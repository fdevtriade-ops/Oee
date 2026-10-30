package br.com.telemetria.api.operacao;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ConsultaIndicadores {
    private final JdbcTemplate jdbc;
    private final MotorOperacional motor;
    public ConsultaIndicadores(JdbcTemplate jdbc,MotorOperacional motor) { this.jdbc=jdbc; this.motor=motor; }
    public enum Agrupamento { RESUMO, MINUTO, TURNO }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> consultar(String dispositivo,Agrupamento agrupamento,UUID execucao,Instant inicio,Instant fim) {
        if(inicio!=null && fim!=null && !fim.isAfter(inicio)) throw new IllegalArgumentException("O fim deve ser posterior ao início.");
        if(execucao==null) execucao=(UUID)motor.consultar(dispositivo).get("execucao");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM configuracao.operacao_execucao WHERE execucao_id=? AND dispositivo_id=?",Long.class,execucao,dispositivo)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Execução não encontrada para este dispositivo.");
        String grupo=switch(agrupamento) { case RESUMO -> ""; case MINUTO -> "minuto,turno,turno_inicio"; case TURNO -> "turno,turno_inicio"; };
        String sql="SELECT "+(grupo.isEmpty()?"":grupo+",")+"""
            COUNT(CASE WHEN situacao='VALIDO' THEN 1 END) AS validos,
            COUNT(CASE WHEN situacao<>'VALIDO' THEN 1 END) AS excluidos,
            COALESCE(SUM(planejado_segundos),0) AS planejado,
            COALESCE(SUM(rodando_segundos),0) AS rodando,
            COALESCE(SUM(quantidade),0) AS quantidade,
            COALESCE(SUM(boas),0) AS boas,
            COALESCE(SUM(capacidade_ideal),0) AS capacidade,
            COALESCE(SUM(CASE WHEN situacao<>'VALIDO' THEN observado_segundos ELSE 0 END),0) AS excluido_segundos,
            COALESCE(SUM(microparada_segundos),0) AS micro_segundos,
            COALESCE(SUM(parada_segundos),0) AS parada_segundos,
            COALESCE(SUM(microparadas),0) AS microparadas,
            COALESCE(SUM(paradas),0) AS paradas
            FROM configuracao.operacao_intervalo WHERE execucao_id=?
            """;
        List<Object> argumentos=new ArrayList<>(); argumentos.add(execucao);
        if(inicio!=null) { sql+=" AND inicio >= ?"; argumentos.add(inicio.atOffset(ZoneOffset.UTC)); }
        if(fim!=null) { sql+=" AND fim <= ?"; argumentos.add(fim.atOffset(ZoneOffset.UTC)); }
        if(!grupo.isEmpty()) sql+=" GROUP BY "+grupo+" ORDER BY "+(agrupamento==Agrupamento.MINUTO?"minuto":"turno_inicio")+" DESC LIMIT 500";
        var resultado=new ArrayList<Map<String,Object>>();
        for(var row:jdbc.queryForList(sql,argumentos.toArray())) {
            long validos=((Number)row.get("validos")).longValue();
            var item=new LinkedHashMap<String,Object>();
            if(agrupamento==Agrupamento.MINUTO) item.put("minuto",row.get("minuto"));
            if(agrupamento!=Agrupamento.RESUMO) { item.put("turno",row.get("turno")); item.put("turnoInicio",row.get("turno_inicio")); }
            item.put("indicadores",Indicadores.calcular(validos,decimal(row,"planejado"),decimal(row,"rodando"),decimal(row,"quantidade"),decimal(row,"boas"),decimal(row,"capacidade")));
            item.put("intervalosExcluidos",row.get("excluidos")); item.put("segundosExcluidos",row.get("excluido_segundos"));
            item.put("microparadas",row.get("microparadas")); item.put("microparadaSegundos",row.get("micro_segundos"));
            item.put("paradas",row.get("paradas")); item.put("paradaSegundos",row.get("parada_segundos"));
            resultado.add(item);
        }
        return resultado;
    }
    private BigDecimal decimal(Map<String,Object> row,String chave) { return new BigDecimal(row.get(chave).toString()); }
}
