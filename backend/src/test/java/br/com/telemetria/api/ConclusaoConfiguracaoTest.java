package br.com.telemetria.api;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import br.com.telemetria.api.configuracao.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="spring.sql.init.schema-locations=classpath:schema-test.sql,classpath:linha-rascunho-test.sql,classpath:canal-rascunho-test.sql,classpath:configuracao-concluida-test.sql")
class ConclusaoConfiguracaoTest {
    @Autowired ConclusaoConfiguracaoService service;
    @Autowired LinhaRascunhoService linhas;
    @Autowired CanalRascunhoService canais;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.boot.test.web.server.LocalServerPort int porta;
    @Autowired tools.jackson.databind.ObjectMapper json;
    final Map<String,String> fontes=Map.of("estado","DI3","total","contadorDI1","qualidade","contadorDI4");

    @BeforeEach void preparar() {
        jdbc.update("DELETE FROM configuracao.linha_configuracao");
        jdbc.update("DELETE FROM configuracao.canal_rascunho");
        jdbc.update("DELETE FROM configuracao.linha_rascunho");
        linhas.salvar("TESTE_A",Map.of("nomeLinha","Linha","produto","Produto","velocidadeIdealPpm",120,"versao",0));
        canais.salvar("TESTE_A","DI3",Map.of("nome","Estado","habilitado",true,"funcao","MAQUINA_RODANDO","parametros",Map.of("nivelAtivo",1),"versao",0));
        contador("contadorDI1","TOTAL_PRODUZIDO","un",0);
        contador("contadorDI4","PRODUTOS_BONS","un",0);
    }
    void contador(String codigo,String funcao,String unidade,long versao) {
        canais.salvar("TESTE_A",codigo,Map.of("nome",codigo,"habilitado",true,"funcao",funcao,
                "parametros",Map.of("unidade",unidade,"fator",1,"borda","RISING"),"versao",versao));
    }
    Map<String,Object> pedido() {
        var c=service.conferir("TESTE_A",Map.of("fontes",fontes));
        return Map.of("fontes",fontes,"versaoLinha",c.versaoLinha(),"versoesCanais",c.versoesCanais(),"revisaoAtual",c.revisaoAtual());
    }
    @Test void concluiComFontesEscolhidasSemDependerDeLeituraEmMemoria() {
        var c=service.conferir("TESTE_A",Map.of("fontes",fontes));
        assertTrue(c.podeConcluir());
        var resultado=service.concluir("TESTE_A",pedido());
        assertEquals(1,resultado.revisao());
        assertEquals("CONFIGURACAO_CONCLUIDA",resultado.situacao());
        assertFalse(resultado.processamentoHabilitado());
        assertEquals(fontes,resultado.documento().get("fontes"));
        assertEquals(resultado,service.buscar("TESTE_A"));
    }
    @Test void rascunhoEditadoNaoAlteraSnapshotAnterior() {
        service.concluir("TESTE_A",pedido());
        String anterior=jdbc.queryForObject("SELECT documento FROM configuracao.linha_configuracao",String.class);
        contador("contadorDI1","TOTAL_PRODUZIDO","un",1);
        assertEquals("RASCUNHO_ALTERADO",service.buscar("TESTE_A").situacao());
        assertEquals(anterior,jdbc.queryForObject("SELECT documento FROM configuracao.linha_configuracao",String.class));
        assertEquals(2,service.concluir("TESTE_A",pedido()).revisao());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM configuracao.linha_configuracao",Integer.class));
    }
    @Test void recusaConcluirComVersoesAntigasDeCanaisELinha() {
        var velho=pedido();
        contador("contadorDI1","TOTAL_PRODUZIDO","un",1);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.concluir("TESTE_A",velho)).getStatusCode().value());
        var antesDaLinha=pedido();
        linhas.salvar("TESTE_A",Map.of("nomeLinha","Novo nome","produto","Produto","velocidadeIdealPpm",120,"versao",1));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.concluir("TESTE_A",antesDaLinha)).getStatusCode().value());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM configuracao.linha_configuracao",Integer.class));
    }
    @Test void recusaFonteInexistenteDesabilitadaOuFuncaoIncorreta() {
        for (String codigo : List.of("DI1","contadorDI1","DO1")) {
            var c=service.conferir("TESTE_A",Map.of("fontes",Map.of("estado",codigo,"total","contadorDI1","qualidade","contadorDI4")));
            assertFalse(c.podeConcluir());
            assertFalse(c.pendencias().isEmpty());
        }
        canais.salvar("TESTE_A","DI3",Map.of("nome","Estado","habilitado",false,"funcao","MAQUINA_RODANDO","parametros",Map.of("nivelAtivo",1),"versao",1));
        assertEquals(422,assertThrows(ResponseStatusException.class,()->service.concluir("TESTE_A",pedido())).getStatusCode().value());
    }
    @Test void recusaUnidadesDiferentesEAceitaQualidadePorRejeitadas() {
        contador("contadorDI4","PRODUTOS_REJEITADOS","kg",1);
        assertFalse(service.conferir("TESTE_A",Map.of("fontes",fontes)).podeConcluir());
        contador("contadorDI4","PRODUTOS_REJEITADOS","un",2);
        assertTrue(service.conferir("TESTE_A",Map.of("fontes",fontes)).podeConcluir());
    }
    @Test void exigeEscolhaExplicitaSemInferirCanalPeloNumero() {
        assertThrows(IllegalArgumentException.class,()->service.conferir("TESTE_A",Map.of()));
        assertThrows(IllegalArgumentException.class,()->service.conferir("TESTE_A",Map.of("fontes",Map.of("total","contadorDI1"))));
        assertEquals("DI3",service.concluir("TESTE_A",pedido()).documento().get("fontes") instanceof Map<?,?> f ? f.get("estado") : null);
    }
    @Test void conclusoesConcorrentesNaoSobrescrevemRevisao() throws Exception {
        var p=pedido();
        var inicio=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> salvar=()->{
                inicio.await();
                try { service.concluir("TESTE_A",p); return 200; }
                catch(ResponseStatusException e) { return e.getStatusCode().value(); }
            };
            var a=pool.submit(salvar); var b=pool.submit(salvar); inicio.countDown();
            assertEquals(Set.of(200,409),Set.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)));
        } finally { pool.shutdownNow(); }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM configuracao.linha_configuracao",Integer.class));
    }
    @Test void rotasHttpEInterfaceEstaoDisponiveis() throws Exception {
        var http=java.net.http.HttpClient.newHttpClient();
        String base="http://localhost:"+porta;
        var conferir=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/dispositivos/TESTE_A/configuracao/conferir"))
                .header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("fontes",fontes)))).build();
        assertEquals(200,http.send(conferir,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
        var concluir=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/dispositivos/TESTE_A/configuracao/concluir"))
                .header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(pedido()))).build();
        assertEquals(200,http.send(concluir,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
        for(String path:List.of("/conclusao.html","/assets/oee-conclusao.js","/api/dispositivos/TESTE_A/configuracao/concluida")) {
            var r=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+path)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200,r.statusCode(),path);
        }
    }
}
