package br.com.telemetria.api;

import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import br.com.telemetria.api.configuracao.*;
import br.com.telemetria.api.operacao.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="spring.sql.init.schema-locations=classpath:schema-test.sql,classpath:linha-rascunho-test.sql,classpath:canal-rascunho-test.sql,classpath:configuracao-concluida-test.sql,classpath:motor-operacional-test.sql,classpath:placa-local-test.sql")
@SuppressWarnings("unchecked")
class MotorOperacionalTest {
    @Autowired MotorOperacional motor;
    @Autowired ConsultaIndicadores consulta;
    @Autowired LinhaRascunhoService linhas;
    @Autowired CanalRascunhoService canais;
    @Autowired ConclusaoConfiguracaoService conclusao;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager tx;
    @Autowired br.com.telemetria.api.simulacao.PlacaLocalService placas;
    @org.springframework.boot.test.web.server.LocalServerPort int porta;
    final Instant base=Instant.parse("2026-10-30T15:00:00Z");
    UUID execucao;

    static Map<String,Object> politica() {
        Map<String,Object> p=new LinkedHashMap<>();
        p.put("fuso","UTC"); p.put("lacunaMaximaSegundos",15); p.put("atrasoMaximoSegundos",60); p.put("adiantamentoMaximoSegundos",60);
        p.put("microparadaLimiteSegundos",10); p.put("microparadaLimiteInclusivo",true);
        p.put("turnos",List.of(Map.of("nome","Teste 24h","inicio","00:00","fim","00:00")));
        p.put("estadoNoIntervalo","AMOSTRA_ANTERIOR"); p.put("foraDeOrdem","IGNORAR");
        p.put("lacunas","DESCARTAR_E_REINICIAR_REFERENCIA"); p.put("fronteiras","DESCARTAR_INTERVALO");
        p.put("programadas","EXCLUIR_DO_PLANEJADO"); p.put("paradaInterrompida","DESCARTAR_PENDENTES");
        return p;
    }
    @BeforeEach void preparar() {
        jdbc.update("DELETE FROM simulacao.comando_local"); jdbc.update("DELETE FROM simulacao.placa_local");
        for(String tabela:List.of("operacao_programada","operacao_intervalo","operacao_estado","operacao_execucao","linha_configuracao","canal_rascunho","linha_rascunho"))
            jdbc.update("DELETE FROM configuracao."+tabela);
        linhas.salvar("TESTE_A",Map.of("nomeLinha","Linha teste","produto","Produto","velocidadeIdealPpm",120,"versao",0));
        canais.salvar("TESTE_A","DI3",Map.of("nome","Estado","habilitado",true,"funcao","MAQUINA_RODANDO","parametros",Map.of("nivelAtivo",1),"versao",0));
        contador("contadorDI1","TOTAL_PRODUZIDO",0);
        contador("contadorDI4","PRODUTOS_BONS",0);
        concluir();
        execucao=(UUID)motor.ativar("TESTE_A",Map.of("revisao",1,"politica",politica())).get("execucao");
    }
    void contador(String codigo,String funcao,long versao) {
        canais.salvar("TESTE_A",codigo,Map.of("nome",codigo,"habilitado",true,"funcao",funcao,
                "parametros",Map.of("unidade","un","fator",1,"borda","RISING"),"versao",versao));
    }
    void concluir() {
        var fontes=Map.of("estado","DI3","total","contadorDI1","qualidade","contadorDI4");
        var c=conclusao.conferir("TESTE_A",Map.of("fontes",fontes));
        conclusao.concluir("TESTE_A",Map.of("fontes",fontes,"versaoLinha",c.versaoLinha(),"versoesCanais",c.versoesCanais(),"revisaoAtual",c.revisaoAtual()));
    }
    Map<String,Object> leitura(int segundo,long total,long boas,boolean rodando) {
        var l=new LinkedHashMap<String,Object>(); l.put("id","TESTE_A");
        for(int i=1;i<=4;i++) { l.put("DI"+i,i==3 && rodando?1:0); l.put("DO"+i,0); l.put("AI"+i,0); l.put("contadorDI"+i,-1L); }
        l.put("contadorDI1",total); l.put("contadorDI4",boas);
        l.put("timestamp",DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss").withZone(ZoneOffset.UTC).format(base.plusSeconds(segundo)));
        l.put("recebidoEm",base.plusSeconds(segundo).toString()); return l;
    }
    void ler(int segundo,long total,long boas,boolean rodando) { motor.processar(leitura(segundo,total,boas,rodando)); }
    Map<String,Object> resumo() { return consulta.consultar("TESTE_A",ConsultaIndicadores.Agrupamento.RESUMO,null,null,null).get(0); }
    Indicadores.Resultado indicadores() { return (Indicadores.Resultado)resumo().get("indicadores"); }
    Map<String,Object> estado() { return (Map<String,Object>)motor.consultar("TESTE_A").get("estado"); }
    void igual(String esperado,BigDecimal atual) { assertNotNull(atual); assertEquals(0,new BigDecimal(esperado).compareTo(atual)); }

    @Test void calculaProducaoPpmEOeeEmLeiturasContinuas() {
        ler(0,1000,900,true); ler(5,1010,909,true); ler(10,1020,919,true);
        var i=indicadores(); igual("20",i.quantidade()); igual("120",i.ppm());
        igual("1",i.disponibilidade()); igual("1",i.performance()); igual("0.95",i.qualidade()); igual("0.95",i.oee());
        assertTrue(conclusao.buscar("TESTE_A").processamentoHabilitado());
        assertEquals(1,consulta.consultar("TESTE_A",ConsultaIndicadores.Agrupamento.MINUTO,null,null,null).size());
    }
    @Test void limitaPerformanceA100ESomaBases() {
        ler(0,0,0,true); ler(5,30,30,true);
        igual("3",indicadores().performanceSemLimite()); igual("1",indicadores().performance()); igual("1",indicadores().oee());
        ler(15,30,30,true);
        igual("120",indicadores().ppm()); igual("1",indicadores().performance());
    }
    @Test void zeroESemBaseSaoDistintos() {
        assertNull(indicadores().quantidade()); assertNull(indicadores().oee());
        ler(0,0,0,true); ler(5,0,0,true);
        igual("0",indicadores().quantidade()); igual("0",indicadores().performance());
        assertNull(indicadores().qualidade()); assertNull(indicadores().oee());
    }
    @Test void microparadaAfetaSoPerformanceEContaTodaDuracao() {
        ler(0,0,0,true); ler(5,10,10,false); ler(10,10,10,false);
        assertEquals(1,motor.consultar("TESTE_A").get("intervalosPendentes"));
        ler(15,10,10,true);
        igual("15",indicadores().planejadoSegundos()); igual("15",indicadores().rodandoSegundos());
        igual("1",indicadores().disponibilidade());
        assertEquals(1,((Number)resumo().get("microparadas")).intValue());
        igual("10",new BigDecimal(resumo().get("microparadaSegundos").toString()));
        assertEquals(0,((Number)resumo().get("paradas")).intValue());
    }
    @Test void paradaMaiorQueLimiarAfetaDisponibilidadeSemDuplaPenalizacao() {
        ler(0,0,0,true); ler(5,10,10,false); ler(10,10,10,false); ler(15,10,10,false); ler(20,10,10,false);
        ler(25,10,10,true); ler(30,20,20,true);
        igual("30",indicadores().planejadoSegundos()); igual("10",indicadores().rodandoSegundos()); igual("1",indicadores().performance());
        assertEquals(1,((Number)resumo().get("paradas")).intValue());
        igual("20",new BigDecimal(resumo().get("paradaSegundos").toString()));
    }
    @Test void ignoraRepetidasEForaDeOrdemSemDuplicar() {
        ler(0,0,0,true); ler(5,10,10,true); ler(5,10,10,true); ler(2,100,100,true); ler(10,20,20,true);
        igual("20",indicadores().quantidade()); assertEquals(2,indicadores().intervalosValidos());
        assertEquals(2,((Number)estado().get("amostrasIgnoradas")).intValue());
    }
    @Test void excluiLacunaERetomaComNovaReferencia() {
        ler(0,0,0,true); ler(5,10,10,true); ler(30,100,100,true); ler(35,110,110,true);
        igual("20",indicadores().quantidade()); igual("10",indicadores().planejadoSegundos());
        assertEquals(1,((Number)resumo().get("intervalosExcluidos")).intValue());
    }
    @Test void desconfiguracaoOuReducaoBloqueiaAteRetomadaExplicita() {
        ler(0,100,100,true); ler(5,110,110,true); ler(10,-1,-1,true); ler(15,120,120,true);
        assertEquals("RECUPERACAO_NECESSARIA",estado().get("diagnostico")); igual("10",indicadores().quantidade());
        motor.novaReferencia("TESTE_A",Map.of("execucao",execucao.toString(),"motivo","Teste de energia"));
        ler(20,0,0,true); ler(25,5,5,true); igual("15",indicadores().quantidade());
        ler(30,1,1,true); assertEquals(true,estado().get("bloqueado"));
    }
    @Test void reinicioDoServicoPreservaReferenciaEAmostraNaoDuplica() {
        ler(0,100,100,true); ler(5,110,110,true);
        var outro=new MotorOperacional(jdbc,json);
        new TransactionTemplate(tx).executeWithoutResult(s->outro.processar(leitura(5,110,110,true)));
        new TransactionTemplate(tx).executeWithoutResult(s->outro.processar(leitura(10,120,120,true)));
        igual("20",indicadores().quantidade());
    }
    @Test void falhaDeEscritaDesfazTambemAvancoDaReferencia() {
        ler(0,0,0,true);
        String antes=jdbc.queryForObject("SELECT estado FROM configuracao.operacao_estado",String.class);
        jdbc.execute("ALTER TABLE configuracao.operacao_intervalo ADD CONSTRAINT falha_teste CHECK (quantidade < 5)");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->ler(5,10,10,true));
            assertEquals(antes,jdbc.queryForObject("SELECT estado FROM configuracao.operacao_estado",String.class));
        } finally { jdbc.execute("ALTER TABLE configuracao.operacao_intervalo DROP CONSTRAINT falha_teste"); }
        ler(5,10,10,true); igual("10",indicadores().quantidade());
    }
    @Test void fronteirasNaoRecebemRateioInventado() {
        ler(58,0,0,true); ler(63,10,10,true); ler(68,20,20,true);
        igual("10",indicadores().quantidade());
        assertEquals(1,((Number)resumo().get("intervalosExcluidos")).intValue());
    }
    @Test void programadaExcluiTempoEDeltaEImpedeEdicaoRetroativa() {
        motor.programar("TESTE_A",Map.of("execucao",execucao.toString(),"inicio",base.plusSeconds(5).toString(),"fim",base.plusSeconds(15).toString(),"motivo","Intervalo teste"));
        ler(0,0,0,true); ler(5,10,10,true); ler(10,20,20,true); ler(15,30,30,true); ler(20,40,40,true);
        igual("20",indicadores().quantidade()); igual("10",indicadores().planejadoSegundos());
        assertThrows(ResponseStatusException.class,()->motor.programar("TESTE_A",Map.of("execucao",execucao.toString(),"inicio",base.toString(),"fim",base.plusSeconds(15).toString(),"motivo","Retroativo")));
    }
    @Test void fonteDeQualidadePodeContarRejeitadas() {
        motor.parar("TESTE_A",Map.of("execucao",execucao.toString()));
        contador("contadorDI4","PRODUTOS_REJEITADOS",1); concluir();
        motor.ativar("TESTE_A",Map.of("revisao",2,"politica",politica()));
        ler(0,0,0,true); ler(5,10,2,true); igual("8",indicadores().boas()); igual("0.8",indicadores().oee());
    }
    @Test void ativaSomenteRevisaoAtualEEdicoesNaoMudamExecucaoAtiva() {
        assertThrows(ResponseStatusException.class,()->motor.ativar("TESTE_A",Map.of("revisao",1,"politica",politica())));
        linhas.salvar("TESTE_A",Map.of("nomeLinha","Editada","produto","Produto","velocidadeIdealPpm",999,"versao",1));
        ler(0,0,0,true); ler(5,10,10,true); igual("1",indicadores().performanceSemLimite());
        motor.parar("TESTE_A",Map.of("execucao",execucao.toString()));
        assertThrows(ResponseStatusException.class,()->motor.ativar("TESTE_A",Map.of("revisao",1,"politica",politica())));
    }
    @Test void contadorGrandeMantemPrecisaoNoDelta() {
        ler(0,Long.MAX_VALUE-20,Long.MAX_VALUE-20,true); ler(5,Long.MAX_VALUE-10,Long.MAX_VALUE-10,true);
        igual("10",indicadores().quantidade());
    }
    @Test void representacaoTextualPreservaProducaoAcimaDaPrecisaoJavascript() {
        ler(0,0,0,true); ler(5,9007199254740993L,9007199254740993L,true);
        igual("9007199254740993",new BigDecimal(indicadores().quantidadeTexto()));
        assertTrue(indicadores().boasTexto().startsWith("9007199254740993"));
    }
    @Test void concorrenciaSerializaDuplicatas() throws Exception {
        ler(0,0,0,true); var pool=Executors.newFixedThreadPool(2);
        try {
            var gate=new CountDownLatch(1);
            Callable<Void> enviar=()->{gate.await();ler(5,10,10,true);return null;};
            var a=pool.submit(enviar); var b=pool.submit(enviar); gate.countDown();
            a.get(10,TimeUnit.SECONDS); b.get(10,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        igual("10",indicadores().quantidade()); assertEquals(1,indicadores().intervalosValidos());
    }
    @Test void periodoNoturnoUsaDataDeInicioETurnosNaoSeSobrepoem() {
        var p=politica(); p.put("fuso","America/Sao_Paulo");
        p.put("turnos",List.of(Map.of("nome","Noite","inicio","22:30","fim","06:30")));
        var politica=PoliticaOperacional.ler(p);
        assertEquals(Instant.parse("2026-10-30T01:30:00Z"),politica.periodo(Instant.parse("2026-10-30T04:00:00Z")).inicio());
        p.put("turnos",List.of(Map.of("nome","A","inicio","00:00","fim","12:00"),Map.of("nome","B","inicio","11:00","fim","18:00")));
        assertThrows(IllegalArgumentException.class,()->PoliticaOperacional.ler(p));
    }
    @Test void horarioAtrasadoOuAmbiguoNaoAvancaReferencia() {
        ler(0,0,0,true); var l=leitura(5,10,10,true); l.put("recebidoEm",base.plusSeconds(600).toString()); motor.processar(l);
        assertEquals("ATRASO_OU_RELOGIO_FORA_DA_POLITICA",estado().get("ultimaAmostraIgnorada"));
        ler(10,20,20,true); igual("20",indicadores().quantidade());
        var p=politica(); p.put("fuso","America/New_York");
        assertThrows(IllegalArgumentException.class,()->PoliticaOperacional.ler(p).horario("01/11/2026 01:30:00"));
    }
    @Test void rotaDeLeituraExecutaMotorEConsultaIndicadores() throws Exception {
        var http=java.net.http.HttpClient.newHttpClient();
        String baseUrl="http://localhost:"+porta;
        var l=leitura(0,0,0,true);
        l.put("timestamp",DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss").withZone(ZoneOffset.UTC).format(Instant.now()));
        var r=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl+"/api/leituras")).header("Content-Type","application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(l))).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(204,r.statusCode(),r.body()); assertEquals("REFERENCIA_ESTABELECIDA",estado().get("diagnostico"));
        var indicadores=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl+"/api/dispositivos/TESTE_A/operacao/indicadores")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200,indicadores.statusCode());
        for(String caminho:List.of("/operacao.html","/assets/oee-operacao.js","/assets/oee-operacao.css")) {
            assertEquals(200,http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl+caminho)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }
    UUID prepararPlacaLocal() {
        String id="LOCAL_PLACA";
        placas.criar(id,Map.of("fuso","UTC","rodando",true));
        linhas.salvar(id,Map.of("nomeLinha","Linha local","produto","Produto","velocidadeIdealPpm",120,"versao",0));
        canais.salvar(id,"DI3",Map.of("nome","Estado","habilitado",true,"funcao","MAQUINA_RODANDO","parametros",Map.of("nivelAtivo",1),"versao",0));
        for(var e:Map.of("contadorDI1","TOTAL_PRODUZIDO","contadorDI4","PRODUTOS_BONS").entrySet()) {
            canais.salvar(id,e.getKey(),Map.of("nome",e.getKey(),"habilitado",true,"funcao",e.getValue(),"parametros",Map.of("unidade","un","fator",1,"borda",e.getKey().equals("contadorDI4")?"FALLING":"RISING"),"versao",0));
        }
        var f=Map.of("estado","DI3","total","contadorDI1","qualidade","contadorDI4");
        var c=conclusao.conferir(id,Map.of("fontes",f));
        conclusao.concluir(id,Map.of("fontes",f,"versaoLinha",c.versaoLinha(),"versoesCanais",c.versoesCanais(),"revisaoAtual",0));
        var p=politica(); p.put("adiantamentoMaximoSegundos",3600);
        return (UUID)motor.ativar(id,Map.of("revisao",1,"politica",p)).get("execucao");
    }
    Indicadores.Resultado indicadoresLocais() {
        return (Indicadores.Resultado)consulta.consultar("LOCAL_PLACA",ConsultaIndicadores.Agrupamento.RESUMO,null,null,null).get(0).get("indicadores");
    }
    @Test void placaLocalConfiguraBordasConfirmaECalculaProducao() {
        UUID exec=prepararPlacaLocal();
        var configurada=placas.comando("LOCAL_PLACA",Map.of("acao","CONFIGURAR_PELA_REVISAO","execucao",exec.toString()));
        assertEquals(true,((Map<?,?>)configurada.get("ultimaConfirmacao")).get("confirmado"));
        assertEquals("FALLING",((Map<String,Map<String,Object>>)configurada.get("contadores")).get("contadorDI4").get("borda"));
        placas.passo("LOCAL_PLACA",Map.of("execucao",exec.toString(),"segundos",5,"rodando",true,"pulsosTotal",10,"pulsosQualidade",9));
        igual("10",indicadoresLocais().quantidade()); igual("120",indicadoresLocais().ppm()); igual("0.9",indicadoresLocais().oee());
    }
    @Test void placaLocalRecuperaAposEnergiaSemPerderAcumulados() {
        UUID exec=prepararPlacaLocal();
        placas.comando("LOCAL_PLACA",Map.of("acao","CONFIGURAR_PELA_REVISAO","execucao",exec.toString()));
        var passo=Map.<String,Object>of("execucao",exec.toString(),"segundos",5,"rodando",true,"pulsosTotal",10,"pulsosQualidade",10);
        placas.passo("LOCAL_PLACA",passo);
        placas.comando("LOCAL_PLACA",Map.of("acao","DESLIGAR","execucao",exec.toString()));
        assertThrows(ResponseStatusException.class,()->placas.passo("LOCAL_PLACA",passo));
        placas.comando("LOCAL_PLACA",Map.of("acao","LIGAR","execucao",exec.toString()));
        assertEquals("RECUPERACAO_NECESSARIA",((Map<?,?>)motor.consultar("LOCAL_PLACA").get("estado")).get("diagnostico"));
        placas.comando("LOCAL_PLACA",Map.of("acao","RECUPERAR","execucao",exec.toString()));
        placas.passo("LOCAL_PLACA",passo); igual("20",indicadoresLocais().quantidade());
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM simulacao.comando_local",Integer.class));
    }
    @Test void simuladorNaoAssumeIdentidadeDaPlacaFisica() {
        assertThrows(IllegalArgumentException.class,()->placas.criar("TRD2001",Map.of("fuso","UTC","rodando",true)));
    }
}
