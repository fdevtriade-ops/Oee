package br.com.telemetria.api.simulacao;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import br.com.telemetria.api.LeituraService;
import br.com.telemetria.api.operacao.MotorOperacional;
import tools.jackson.databind.ObjectMapper;
import static br.com.telemetria.api.operacao.PoliticaOperacional.*;

@Service
@SuppressWarnings("unchecked")
public class PlacaLocalService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final LeituraService leituras;
    private final MotorOperacional motor;
    public PlacaLocalService(JdbcTemplate jdbc,ObjectMapper json,LeituraService leituras,MotorOperacional motor) {
        this.jdbc=jdbc; this.json=json; this.leituras=leituras; this.motor=motor;
    }
    @Transactional
    public Map<String,Object> criar(String id,Map<String,Object> pedido) {
        campos(pedido,Set.of("fuso","rodando")); schema(id);
        if(!(pedido.get("rodando") instanceof Boolean rodando)) throw new IllegalArgumentException("Informe o estado inicial rodando.");
        String fuso=texto(pedido.get("fuso"),100);
        try { ZoneId.of(fuso); } catch(DateTimeException e) { throw new IllegalArgumentException("Fuso inválido."); }
        var existentes=jdbc.queryForList("SELECT estado FROM simulacao.placa_local WHERE dispositivo_id=?",id);
        if(!existentes.isEmpty()) return consultar(id);
        var estado=new LinkedHashMap<String,Object>();
        estado.put("modo","SIMULADA_LOCAL"); estado.put("ligada",true); estado.put("fuso",fuso);
        estado.put("rodando",rodando);
        estado.put("instante",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
        var contadores=new LinkedHashMap<String,Object>();
        for(int i=1;i<=4;i++) contadores.put("contadorDI"+i,Map.of("habilitado",false,"borda","RISING","valor","-1"));
        estado.put("contadores",contadores);
        jdbc.update("INSERT INTO simulacao.placa_local (dispositivo_id,estado) VALUES (?,?)",id,json.writeValueAsString(estado));
        emitir(id,estado,null,false);
        return consultar(id);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> consultar(String id) { schema(id); return estado(id,false); }

    @Transactional
    public Map<String,Object> comando(String id,Map<String,Object> pedido) {
        campos(pedido,Set.of("acao","execucao")); schema(id);
        String acao=texto(pedido.get("acao"),40);
        if(!Set.of("CONFIGURAR_PELA_REVISAO","DESLIGAR","LIGAR","RECUPERAR").contains(acao)) throw new IllegalArgumentException("Comando local inválido.");
        var estado=estado(id,true);
        var operacao=motor.consultar(id);
        if(!operacao.get("execucao").toString().equals(pedido.get("execucao"))) throw status(409,"A execução mudou.");
        var configuracao=configuracao((UUID)operacao.get("execucao"));
        if(acao.equals("DESLIGAR")) {
            estado.put("ligada",false);
            for(var c:contadores(estado).values()) { c.put("habilitado",false); c.put("valor","-1"); }
        } else if(acao.equals("LIGAR")) estado.put("ligada",true);
        else {
            if(!Boolean.TRUE.equals(estado.get("ligada"))) throw status(409,"Ligue a placa local antes de configurar.");
            for(var entry:contadores(estado).entrySet()) {
                var canal=((List<Map<String,Object>>)configuracao.get("canais")).stream().filter(c->entry.getKey().equals(c.get("codigo"))).findFirst().orElse(null);
                boolean habilitado=canal!=null && Boolean.TRUE.equals(canal.get("habilitado"));
                var c=entry.getValue(); c.put("habilitado",habilitado);
                if(habilitado) {
                    c.put("borda",((Map<String,Object>)canal.get("parametros")).get("borda"));
                    if(acao.equals("RECUPERAR") || Long.parseLong((String)c.get("valor"))<0) c.put("valor","0");
                } else c.put("valor","-1");
            }
            if(acao.equals("RECUPERAR")) motor.novaReferencia(id,Map.of("execucao",pedido.get("execucao"),"motivo","Recuperação confirmada pelo simulador local"));
        }
        UUID comando=UUID.randomUUID();
        var confirmacao=Map.of("comando",comando,"acao",acao,"modo","SIMULADA_LOCAL","confirmado",true);
        estado.put("ultimaConfirmacao",confirmacao);
        jdbc.update("INSERT INTO simulacao.comando_local (comando_id,dispositivo_id,acao,confirmacao) VALUES (?,?,?,?)",comando,id,acao,json.writeValueAsString(confirmacao));
        if(Boolean.TRUE.equals(estado.get("ligada"))) {
            avancarRelogio(estado,1); emitir(id,estado,configuracao,Boolean.TRUE.equals(estado.get("rodando")));
        }
        salvar(id,estado); return estado;
    }
    @Transactional
    public Map<String,Object> passo(String id,Map<String,Object> pedido) {
        campos(pedido,Set.of("execucao","segundos","rodando","pulsosTotal","pulsosQualidade")); schema(id);
        long segundos=inteiro(pedido.get("segundos"));
        if(segundos<1 || segundos>3600) throw new IllegalArgumentException("Informe de 1 a 3600 segundos por passo.");
        if(!(pedido.get("rodando") instanceof Boolean rodando)) throw new IllegalArgumentException("Informe rodando como true/false.");
        long total=inteiro(pedido.get("pulsosTotal")), qualidade=inteiro(pedido.get("pulsosQualidade"));
        var estado=estado(id,true);
        if(!Boolean.TRUE.equals(estado.get("ligada"))) throw status(409,"A placa local está desligada.");
        var operacao=motor.consultar(id);
        if(!operacao.get("execucao").toString().equals(pedido.get("execucao"))) throw status(409,"A execução mudou.");
        var configuracao=configuracao((UUID)operacao.get("execucao"));
        var fontes=(Map<String,Object>)configuracao.get("fontes");
        incrementar(contadores(estado).get(fontes.get("total")),total);
        incrementar(contadores(estado).get(fontes.get("qualidade")),qualidade);
        estado.put("rodando",rodando);
        avancarRelogio(estado,segundos); emitir(id,estado,configuracao,rodando);
        salvar(id,estado); return estado;
    }
    private void incrementar(Map<String,Object> contador,long pulsos) {
        if(Boolean.TRUE.equals(contador.get("habilitado"))) {
            try { contador.put("valor",Long.toString(Math.addExact(Long.parseLong((String)contador.get("valor")),pulsos))); }
            catch(ArithmeticException e) { throw new IllegalArgumentException("O contador local ultrapassou Long.MAX_VALUE."); }
        }
    }
    private void emitir(String id,Map<String,Object> estado,Map<String,Object> configuracao,boolean rodando) {
        var leitura=new LinkedHashMap<String,Object>(); leitura.put("id",id);
        for(int i=1;i<=4;i++) {
            leitura.put("DI"+i,0); leitura.put("DO"+i,0); leitura.put("AI"+i,0);
            leitura.put("contadorDI"+i,Long.parseLong((String)contadores(estado).get("contadorDI"+i).get("valor")));
        }
        if(configuracao!=null) {
            String fonte=(String)((Map<String,Object>)configuracao.get("fontes")).get("estado");
            var canal=((List<Map<String,Object>>)configuracao.get("canais")).stream().filter(c->fonte.equals(c.get("codigo"))).findFirst().orElseThrow();
            int ativo=((Number)((Map<String,Object>)canal.get("parametros")).get("nivelAtivo")).intValue();
            leitura.put(fonte,rodando?ativo:1-ativo);
        }
        leitura.put("timestamp",DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss").withZone(ZoneId.of((String)estado.get("fuso"))).format(Instant.parse((String)estado.get("instante"))));
        var validada=leituras.validar(leitura); motor.processar(validada);
        // Só atualiza a consulta bruta quando a transação do simulador/motor confirmar.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { leituras.armazenar(validada); }
        });
        estado.put("ultimaLeitura",leitura);
    }
    private void avancarRelogio(Map<String,Object> estado,long segundos) { estado.put("instante",Instant.parse((String)estado.get("instante")).plusSeconds(segundos).toString()); }
    private Map<String,Object> configuracao(UUID execucao) { return json.readValue(jdbc.queryForObject("SELECT configuracao FROM configuracao.operacao_execucao WHERE execucao_id=?",String.class,execucao),Map.class); }
    private Map<String,Object> estado(String id,boolean bloquear) {
        var rows=jdbc.queryForList("SELECT estado FROM simulacao.placa_local WHERE dispositivo_id=?"+(bloquear?" FOR UPDATE":""),id);
        if(rows.isEmpty()) throw status(404,"Crie a placa local antes de usar os comandos.");
        return json.readValue((String)rows.get(0).get("estado"),Map.class);
    }
    private Map<String,Map<String,Object>> contadores(Map<String,Object> estado) { return (Map<String,Map<String,Object>>)estado.get("contadores"); }
    private void salvar(String id,Map<String,Object> estado) { jdbc.update("UPDATE simulacao.placa_local SET estado=? WHERE dispositivo_id=?",json.writeValueAsString(estado),id); }
    private void schema(String id) {
        if(!id.equals("TRD_TESTE") && !id.matches("LOCAL_[A-Za-z0-9_-]{1,94}")) throw new IllegalArgumentException("Simulação de placa exige TRD_TESTE ou id iniciado por LOCAL_.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_schema)='simulacao' AND lower(table_name)='placa_local'",Long.class)==0)
            throw status(503,"Execute banco/005_placa_local.sql.");
    }
    private static ResponseStatusException status(int codigo,String mensagem) { return new ResponseStatusException(HttpStatus.valueOf(codigo),mensagem); }
}
