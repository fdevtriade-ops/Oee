package br.com.telemetria.api.operacao;

import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import static br.com.telemetria.api.operacao.PoliticaOperacional.*;

@Service
@SuppressWarnings("unchecked")
public class MotorOperacional {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public MotorOperacional(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    private record Execucao(UUID id,long revisao,Map<String,Object> configuracao,Map<String,Object> regras,PoliticaOperacional politica) { }

    @Transactional
    public Map<String,Object> ativar(String dispositivo,Map<String,Object> pedido) {
        campos(pedido,Set.of("revisao","politica")); schema();
        long revisao=inteiro(pedido.get("revisao"));
        Map<String,Object> regras=objeto(pedido.get("politica"));
        PoliticaOperacional.ler(regras);
        if(jdbc.queryForList("SELECT dispositivo_id FROM configuracao.linha_rascunho WHERE dispositivo_id=? FOR UPDATE",dispositivo).isEmpty())
            throw status(404,"Cadastre a linha antes de ativar.");
        var conclusoes=jdbc.queryForList("SELECT revisao,documento FROM configuracao.linha_configuracao WHERE dispositivo_id=? ORDER BY revisao DESC LIMIT 1",dispositivo);
        if(conclusoes.isEmpty()) throw status(409,"Conclua a configuração antes de ativar.");
        var concluida=conclusoes.get(0);
        if(((Number)concluida.get("revisao")).longValue()!=revisao) throw status(409,"A revisão concluída mudou.");
        var documento=ler((String)concluida.get("documento"));
        long versao=jdbc.queryForObject("SELECT versao FROM configuracao.linha_rascunho WHERE dispositivo_id=?",Long.class,dispositivo);
        Map<String,Long> atuais=new TreeMap<>();
        jdbc.queryForList("SELECT codigo,versao FROM configuracao.canal_rascunho WHERE dispositivo_id=?",dispositivo)
                .forEach(c -> atuais.put((String)c.get("codigo"),((Number)c.get("versao")).longValue()));
        Map<String,Long> salvas=new TreeMap<>();
        objeto(documento.get("versoesCanais")).forEach((k,v)->salvas.put(k,inteiro(v)));
        if(versao!=inteiro(documento.get("versaoLinha")) || !atuais.equals(salvas)) throw status(409,"O rascunho mudou. Conclua a configuração novamente.");
        var runtime=runtime(dispositivo,true);
        if(!runtime.isEmpty() && Boolean.TRUE.equals(runtime.get("ativo"))) throw status(409,"Pare a execução atual antes de ativar outra.");
        UUID execucao=UUID.randomUUID();
        jdbc.update("INSERT INTO configuracao.operacao_execucao (execucao_id,dispositivo_id,revisao,configuracao,politica) VALUES (?,?,?,?,?)",
                execucao,dispositivo,revisao,json.writeValueAsString(documento),json.writeValueAsString(regras));
        String estado=json.writeValueAsString(novoEstado("AGUARDANDO_REFERENCIA"));
        if(runtime.isEmpty()) jdbc.update("INSERT INTO configuracao.operacao_estado (dispositivo_id,execucao_id,ativo,estado) VALUES (?,?,TRUE,?)",dispositivo,execucao,estado);
        else jdbc.update("UPDATE configuracao.operacao_estado SET execucao_id=?,ativo=TRUE,estado=?,atualizado_em=CURRENT_TIMESTAMP WHERE dispositivo_id=?",execucao,estado,dispositivo);
        return consultar(dispositivo);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> consultar(String dispositivo) {
        schema(); var runtime=runtime(dispositivo,false);
        if(runtime.isEmpty()) throw status(404,"A linha ainda não possui execução operacional.");
        var exec=execucao((UUID)runtime.get("execucao_id"));
        var estado=ler((String)runtime.get("estado"));
        Map<String,Object> resposta=new LinkedHashMap<>();
        resposta.put("execucao",exec.id()); resposta.put("ativo",runtime.get("ativo"));
        resposta.put("revisao",exec.revisao()); resposta.put("politica",exec.regras());
        resposta.put("estado",estado); resposta.put("fontes",exec.configuracao().get("fontes"));
        resposta.put("comandoPlaca","PENDENTE_CONTRATO_MQTT");
        resposta.put("intervalosPendentes",pendentes(estado).size());
        resposta.put("semComunicacao",estado.get("ultimoRecebidoEm")==null?null:
                Duration.between(Instant.parse((String)estado.get("ultimoRecebidoEm")),Instant.now()).getSeconds()>exec.politica().lacunaMaximaSegundos());
        return resposta;
    }

    @Transactional
    public Map<String,Object> parar(String dispositivo,Map<String,Object> pedido) {
        campos(pedido,Set.of("execucao")); schema();
        var runtime=exigirRuntime(dispositivo,pedido);
        var exec=execucao((UUID)runtime.get("execucao_id"));
        var estado=ler((String)runtime.get("estado"));
        interromper(exec,estado,"PARADA_INTERROMPIDA");
        estado.put("diagnostico","ENCERRADA"); estado.remove("referencia");
        salvar(dispositivo,estado);
        jdbc.update("UPDATE configuracao.operacao_estado SET ativo=FALSE WHERE dispositivo_id=?",dispositivo);
        jdbc.update("UPDATE configuracao.operacao_execucao SET encerrado_em=CURRENT_TIMESTAMP WHERE execucao_id=? AND encerrado_em IS NULL",exec.id());
        return consultar(dispositivo);
    }

    @Transactional
    public Map<String,Object> novaReferencia(String dispositivo,Map<String,Object> pedido) {
        campos(pedido,Set.of("execucao","motivo")); texto(pedido.get("motivo"),200); schema();
        var runtime=exigirRuntime(dispositivo,pedido);
        if(!Boolean.TRUE.equals(runtime.get("ativo"))) throw status(409,"Ative uma execução antes de retomar.");
        var exec=execucao((UUID)runtime.get("execucao_id"));
        var anterior=ler((String)runtime.get("estado"));
        interromper(exec,anterior,"PARADA_INTERROMPIDA");
        var estado=novoEstado("AGUARDANDO_REFERENCIA");
        estado.put("motivoRetomada",pedido.get("motivo"));
        salvar(dispositivo,estado);
        return consultar(dispositivo);
    }

    @Transactional
    public Map<String,Object> programar(String dispositivo,Map<String,Object> pedido) {
        campos(pedido,Set.of("execucao","inicio","fim","motivo")); schema();
        var runtime=exigirRuntime(dispositivo,pedido);
        if(!Boolean.TRUE.equals(runtime.get("ativo"))) throw status(409,"A execução está encerrada.");
        Instant inicio=instant(pedido.get("inicio")),fim=instant(pedido.get("fim"));
        if(!fim.isAfter(inicio)) throw new IllegalArgumentException("O fim deve ser posterior ao início.");
        var estado=ler((String)runtime.get("estado"));
        if(estado.get("maiorHorario")!=null && !inicio.isAfter(Instant.parse((String)estado.get("maiorHorario"))))
            throw status(409,"Programe apenas períodos posteriores às amostras já recebidas; correção retroativa não está habilitada.");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO configuracao.operacao_programada (id,execucao_id,inicio,fim,motivo) VALUES (?,?,?,?,?)",
                id,runtime.get("execucao_id"),offset(inicio),offset(fim),texto(pedido.get("motivo"),200));
        return Map.of("id",id,"inicio",inicio.toString(),"fim",fim.toString(),"tratamento","EXCLUIR_DO_PLANEJADO");
    }

    /** O intervalo, seus indicadores-base e a referência avançam na mesma transação. */
    @Transactional
    public void processar(Map<String,Object> leitura) {
        if(!disponivel()) return; // Migração opcional preserva a coleta antiga.
        String dispositivo=(String)leitura.get("id");
        var runtime=runtime(dispositivo,true);
        if(runtime.isEmpty() || !Boolean.TRUE.equals(runtime.get("ativo"))) return;
        var exec=execucao((UUID)runtime.get("execucao_id"));
        var estado=ler((String)runtime.get("estado"));
        estado.put("ultimoRecebidoEm",leitura.get("recebidoEm"));
        try { amostra(exec,estado,leitura); }
        catch(DateTimeException e) { ignorar(estado,"HORARIO_INVALIDO_NO_FUSO"); }
        salvar(dispositivo,estado);
    }

    private void amostra(Execucao exec,Map<String,Object> estado,Map<String,Object> leitura) {
        var p=exec.politica();
        Instant horario;
        try { horario=p.horario((String)leitura.get("timestamp")); }
        catch(IllegalArgumentException e) { ignorar(estado,"HORARIO_INVALIDO_NO_FUSO"); return; }
        Instant recebido=Instant.parse((String)leitura.get("recebidoEm"));
        long atraso=Duration.between(horario,recebido).getSeconds();
        if(atraso>p.atrasoMaximoSegundos() || atraso < -p.adiantamentoMaximoSegundos()) {
            ignorar(estado,"ATRASO_OU_RELOGIO_FORA_DA_POLITICA"); return;
        }
        if(estado.get("maiorHorario")!=null && !horario.isAfter(Instant.parse((String)estado.get("maiorHorario")))) {
            ignorar(estado,"REPETIDA_OU_FORA_DE_ORDEM"); return;
        }
        estado.put("maiorHorario",horario.toString());
        if(Boolean.TRUE.equals(estado.get("bloqueado"))) { estado.put("diagnostico","RECUPERACAO_NECESSARIA"); return; }
        var fontes=objeto(exec.configuracao().get("fontes"));
        String totalCodigo=(String)fontes.get("total"),qualidadeCodigo=(String)fontes.get("qualidade");
        long total=((Number)leitura.get(totalCodigo)).longValue(), qualidade=((Number)leitura.get(qualidadeCodigo)).longValue();
        var canalEstado=canal(exec,(String)fontes.get("estado"));
        boolean rodando=((Number)leitura.get(fontes.get("estado"))).intValue()==((Number)objeto(canalEstado.get("parametros")).get("nivelAtivo")).intValue();
        var referencia=estado.get("referencia")==null?null:objeto(estado.get("referencia"));
        if(total<0 || qualidade<0 || (referencia!=null && (total<Long.parseLong((String)referencia.get("total")) || qualidade<Long.parseLong((String)referencia.get("qualidade"))))) {
            interromper(exec,estado,"PARADA_INTERROMPIDA");
            estado.put("bloqueado",true); estado.remove("referencia");
            estado.put("diagnostico","RECUPERACAO_NECESSARIA"); return;
        }
        Map<String,Object> proxima=new LinkedHashMap<>();
        proxima.put("horario",horario.toString()); proxima.put("total",Long.toString(total));
        proxima.put("qualidade",Long.toString(qualidade)); proxima.put("rodando",rodando);
        estado.put("referencia",proxima);
        if(referencia==null) { estado.put("diagnostico","REFERENCIA_ESTABELECIDA"); return; }
        Instant inicio=Instant.parse((String)referencia.get("horario"));
        long segundos=Duration.between(inicio,horario).getSeconds();
        var periodo=p.periodo(inicio);
        var intervalo=intervalo(estado,inicio,horario,periodo);
        String exclusao=null;
        if(segundos>p.lacunaMaximaSegundos()) exclusao="LACUNA";
        else if(periodo==null || p.periodo(horario.minusNanos(1))==null) exclusao="FORA_DO_TURNO";
        else if(!periodo.equals(p.periodo(horario.minusNanos(1))) || !inicio.truncatedTo(ChronoUnit.MINUTES).equals(horario.minusNanos(1).truncatedTo(ChronoUnit.MINUTES))) exclusao="FRONTEIRA_DE_PERIODO";
        if(exclusao==null) exclusao=planejamento(exec.id(),inicio,horario);
        BigDecimal n=BigDecimal.valueOf(total-Long.parseLong((String)referencia.get("total"))).multiply(fator(exec,totalCodigo));
        BigDecimal q=BigDecimal.valueOf(qualidade-Long.parseLong((String)referencia.get("qualidade"))).multiply(fator(exec,qualidadeCodigo));
        boolean boas="PRODUTOS_BONS".equals(canal(exec,qualidadeCodigo).get("funcao"));
        BigDecimal g=boas?q:n.subtract(q);
        boolean estavaRodando=Boolean.TRUE.equals(referencia.get("rodando"));
        if(exclusao==null && (g.signum()<0 || g.compareTo(n)>0 || (!estavaRodando && (n.signum()>0 || q.signum()>0)))) exclusao="SINAIS_INCONSISTENTES";
        if(exclusao!=null) {
            interromper(exec,estado,"PARADA_INTERROMPIDA"); gravar(exec,intervalo,exclusao,BigDecimal.ZERO,BigDecimal.ZERO,false,false);
            estado.put("diagnostico",exclusao); return;
        }
        intervalo.put("quantidade",n.toPlainString()); intervalo.put("boas",g.toPlainString());
        if(estavaRodando) {
            fecharParada(exec,estado);
            gravar(exec,intervalo,"VALIDO",BigDecimal.valueOf(segundos),BigDecimal.valueOf(segundos),false,false);
            estado.put("diagnostico","PROCESSANDO");
        } else {
            long duracao=numeroEstado(estado,"duracaoParada")+segundos;
            estado.put("duracaoParada",duracao);
            if(Boolean.TRUE.equals(estado.get("paradaMacro"))) gravar(exec,intervalo,"VALIDO",BigDecimal.valueOf(segundos),BigDecimal.ZERO,false,false);
            else {
                var pendentes=pendentes(estado); pendentes.add(intervalo); estado.put("pendentes",pendentes);
                if(!p.micro(duracao)) {
                    boolean primeiro=true;
                    for(var item:pendentes) { gravar(exec,item,"VALIDO",duracao(item),BigDecimal.ZERO,false,primeiro); primeiro=false; }
                    estado.put("pendentes",new ArrayList<>()); estado.put("paradaMacro",true);
                }
            }
            estado.put("diagnostico",Boolean.TRUE.equals(estado.get("paradaMacro"))?"PARADA_DE_DISPONIBILIDADE":"AGUARDANDO_FECHAMENTO_PARADA");
            if(rodando) { fecharParada(exec,estado); estado.put("diagnostico","PROCESSANDO"); }
        }
    }

    private void fecharParada(Execucao exec,Map<String,Object> estado) {
        boolean primeiro=true;
        for(var item:pendentes(estado)) { item.put("micro",true); gravar(exec,item,"VALIDO",duracao(item),duracao(item),primeiro,false); primeiro=false; }
        estado.put("pendentes",new ArrayList<>()); estado.put("paradaMacro",false); estado.put("duracaoParada",0);
    }
    private void interromper(Execucao exec,Map<String,Object> estado,String motivo) {
        for(var item:pendentes(estado)) gravar(exec,item,motivo,BigDecimal.ZERO,BigDecimal.ZERO,false,false);
        estado.put("pendentes",new ArrayList<>()); estado.put("paradaMacro",false); estado.put("duracaoParada",0);
    }
    private Map<String,Object> intervalo(Map<String,Object> estado,Instant inicio,Instant fim,PoliticaOperacional.Periodo periodo) {
        var item=new LinkedHashMap<String,Object>();
        item.put("sessao",estado.get("sessao")); item.put("inicio",inicio.toString()); item.put("fim",fim.toString());
        item.put("minuto",inicio.truncatedTo(ChronoUnit.MINUTES).toString());
        item.put("turno",periodo==null?"FORA_DO_TURNO":periodo.nome());
        item.put("turnoInicio",periodo==null?inicio.truncatedTo(ChronoUnit.DAYS).toString():periodo.inicio().toString());
        item.put("quantidade","0"); item.put("boas","0"); return item;
    }
    private void gravar(Execucao exec,Map<String,Object> item,String situacao,BigDecimal t,BigDecimal r,boolean micro,boolean parada) {
        boolean valido="VALIDO".equals(situacao);
        BigDecimal n=valido?new BigDecimal((String)item.get("quantidade")):BigDecimal.ZERO;
        BigDecimal g=valido?new BigDecimal((String)item.get("boas")):BigDecimal.ZERO;
        BigDecimal velocidade=decimal(objeto(exec.configuracao().get("linha")).get("velocidadeIdealPpm"));
        BigDecimal capacidade=velocidade.multiply(r).divide(BigDecimal.valueOf(60),Indicadores.PRECISAO);
        boolean parado=valido && n.signum()==0 && (micro || parada || r.signum()==0);
        // Microparadas pendentes possuem marcador no primeiro item; todos os seus itens têm R=T e produção zero.
        BigDecimal microTempo=valido && Boolean.TRUE.equals(item.get("micro"))?duracao(item):BigDecimal.ZERO;
        if(micro) microTempo=duracao(item);
        jdbc.update("""
            INSERT INTO configuracao.operacao_intervalo
            (execucao_id,sessao_id,inicio,fim,minuto,turno,turno_inicio,situacao,observado_segundos,planejado_segundos,rodando_segundos,
             quantidade,boas,capacidade_ideal,microparada_segundos,parada_segundos,microparadas,paradas)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,exec.id(),UUID.fromString((String)item.get("sessao")),offset(Instant.parse((String)item.get("inicio"))),
            offset(Instant.parse((String)item.get("fim"))),offset(Instant.parse((String)item.get("minuto"))),item.get("turno"),
            offset(Instant.parse((String)item.get("turnoInicio"))),situacao,duracao(item),t,r,n,g,capacidade,microTempo,
            parado && r.signum()==0?duracao(item):BigDecimal.ZERO,micro?1:0,parada?1:0);
    }
    private String planejamento(UUID execucao,Instant inicio,Instant fim) {
        var periodos=jdbc.queryForList("SELECT inicio,fim FROM configuracao.operacao_programada WHERE execucao_id=? AND inicio < ? AND fim > ?",execucao,offset(fim),offset(inicio));
        if(periodos.isEmpty()) return null;
        for(var periodo:periodos) {
            Instant a=((OffsetDateTime)periodo.get("inicio")).toInstant(),b=((OffsetDateTime)periodo.get("fim")).toInstant();
            if(!inicio.isBefore(a) && !fim.isAfter(b)) return "PARADA_PROGRAMADA";
        }
        return "FRONTEIRA_PROGRAMADA";
    }
    private Execucao execucao(UUID id) {
        var row=jdbc.queryForMap("SELECT revisao,configuracao,politica FROM configuracao.operacao_execucao WHERE execucao_id=?",id);
        var regras=ler((String)row.get("politica"));
        return new Execucao(id,((Number)row.get("revisao")).longValue(),ler((String)row.get("configuracao")),regras,PoliticaOperacional.ler(regras));
    }
    private Map<String,Object> canal(Execucao exec,String codigo) {
        return ((List<Map<String,Object>>)exec.configuracao().get("canais")).stream().filter(c->codigo.equals(c.get("codigo"))).findFirst().orElseThrow();
    }
    private BigDecimal fator(Execucao exec,String codigo) { return decimal(objeto(canal(exec,codigo).get("parametros")).get("fator")); }
    private Map<String,Object> runtime(String id,boolean bloquear) {
        var rows=jdbc.queryForList("SELECT * FROM configuracao.operacao_estado WHERE dispositivo_id=?"+(bloquear?" FOR UPDATE":""),id);
        return rows.isEmpty()?Map.of():rows.get(0);
    }
    private Map<String,Object> exigirRuntime(String id,Map<String,Object> pedido) {
        var runtime=runtime(id,true);
        if(runtime.isEmpty()) throw status(404,"Execução não encontrada.");
        if(!runtime.get("execucao_id").toString().equals(pedido.get("execucao"))) throw status(409,"A execução mudou. Consulte a operação atual.");
        return runtime;
    }
    private Map<String,Object> novoEstado(String diagnostico) {
        var estado=new LinkedHashMap<String,Object>();
        estado.put("sessao",UUID.randomUUID().toString()); estado.put("diagnostico",diagnostico); return estado;
    }
    private void ignorar(Map<String,Object> estado,String diagnostico) {
        estado.put("ultimaAmostraIgnorada",diagnostico);
        estado.put("amostrasIgnoradas",numeroEstado(estado,"amostrasIgnoradas")+1);
    }
    private void salvar(String id,Map<String,Object> estado) {
        jdbc.update("UPDATE configuracao.operacao_estado SET estado=?,atualizado_em=CURRENT_TIMESTAMP WHERE dispositivo_id=?",json.writeValueAsString(estado),id);
    }
    private boolean disponivel() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_schema)='configuracao' AND lower(table_name)='operacao_estado'",Long.class)>0;
    }
    private void schema() { if(!disponivel()) throw status(503,"Execute banco/004_motor_operacional.sql no PostgreSQL."); }
    private Map<String,Object> ler(String texto) { return json.readValue(texto,Map.class); }
    private static Map<String,Object> objeto(Object valor) {
        if(!(valor instanceof Map<?,?>)) throw new IllegalArgumentException("Informe um objeto JSON.");
        return (Map<String,Object>)valor;
    }
    private List<Map<String,Object>> pendentes(Map<String,Object> estado) {
        return new ArrayList<>((List<Map<String,Object>>)estado.getOrDefault("pendentes",List.of()));
    }
    private static long numeroEstado(Map<String,Object> estado,String chave) { return ((Number)estado.getOrDefault(chave,0)).longValue(); }
    private static BigDecimal decimal(Object valor) { return new BigDecimal(valor.toString()); }
    private static BigDecimal duracao(Map<String,Object> intervalo) {
        return BigDecimal.valueOf(Duration.between(Instant.parse((String)intervalo.get("inicio")),Instant.parse((String)intervalo.get("fim"))).getSeconds());
    }
    private static OffsetDateTime offset(Instant instante) { return instante.atOffset(ZoneOffset.UTC); }
    private static Instant instant(Object valor) {
        try { return Instant.parse(texto(valor,40)); } catch(DateTimeException e) { throw new IllegalArgumentException("Informe início/fim ISO 8601 com UTC (Z)."); }
    }
    private static ResponseStatusException status(int codigo,String mensagem) { return new ResponseStatusException(HttpStatus.valueOf(codigo),mensagem); }
}
