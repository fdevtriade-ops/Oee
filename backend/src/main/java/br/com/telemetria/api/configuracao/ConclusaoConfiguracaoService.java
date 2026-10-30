package br.com.telemetria.api.configuracao;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static br.com.telemetria.api.configuracao.CanalRascunhoRepository.CanalRascunho;

@Service
public class ConclusaoConfiguracaoService {
    private final LinhaRascunhoService linhas;
    private final CanalRascunhoService canais;
    private final CanalRascunhoRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ConclusaoConfiguracaoService(LinhaRascunhoService linhas, CanalRascunhoService canais,
            CanalRascunhoRepository repository, JdbcTemplate jdbc, ObjectMapper json) {
        this.linhas=linhas; this.canais=canais; this.repository=repository; this.jdbc=jdbc; this.json=json;
    }
    public record Conferencia(long versaoLinha, Map<String,Long> versoesCanais,
            long revisaoAtual, Map<String,String> fontes, List<String> pendencias, boolean podeConcluir) { }
    public record Conclusao(long revisao, String concluidoEm, String situacao,
            boolean processamentoHabilitado, Map<String,Object> documento) { }

    @Transactional(readOnly=true)
    public Conferencia conferir(String id, Map<String,Object> pedido) {
        campos(pedido, Set.of("fontes"));
        return conferirFontes(id, fontes(pedido.get("fontes")));
    }

    private Conferencia conferirFontes(String id, Map<String,String> fontes) {
        var linha=linhas.buscar(id);
        var lista=canais.listar(id);
        Map<String,Long> versoes=new TreeMap<>();
        lista.forEach(c -> versoes.put(c.codigo(),c.versao()));
        var problemas=new ArrayList<String>();
        var estado=selecionar(lista,fontes.get("estado"),Set.of("MAQUINA_RODANDO"),"estado",problemas);
        var total=selecionar(lista,fontes.get("total"),Set.of("TOTAL_PRODUZIDO"),"total produzido",problemas);
        var qualidade=selecionar(lista,fontes.get("qualidade"),Set.of("PRODUTOS_BONS","PRODUTOS_REJEITADOS"),"qualidade",problemas);
        if (estado!=null && !estado.tipo().equals("DI")) problemas.add("O estado precisa de uma entrada DI.");
        if (total!=null && qualidade!=null) {
            if (!total.tipo().equals("CONTADOR") || !qualidade.tipo().equals("CONTADOR"))
                problemas.add("Total e qualidade precisam de contadores.");
            if (!Objects.equals(total.parametros().get("unidade"),qualidade.parametros().get("unidade")))
                problemas.add("Total e qualidade precisam usar a mesma unidade de produto.");
        }
        return new Conferencia(linha.versao(),versoes,revisaoAtual(id),fontes,List.copyOf(problemas),problemas.isEmpty());
    }

    private CanalRascunho selecionar(List<CanalRascunho> lista, String codigo, Set<String> funcoes,
            String uso,List<String> problemas) {
        var canal=lista.stream().filter(c -> c.codigo().equals(codigo)).findFirst().orElse(null);
        if (canal==null || !canal.habilitado() || !funcoes.contains(canal.funcao())) {
            problemas.add("Selecione um canal salvo e habilitado com função de "+uso+".");
            return null;
        }
        return canal;
    }

    @Transactional
    public Conclusao concluir(String id, Map<String,Object> pedido) {
        campos(pedido,Set.of("fontes","versaoLinha","versoesCanais","revisaoAtual"));
        var fontes=fontes(pedido.get("fontes"));
        long versao=inteiro(pedido.get("versaoLinha"));
        long revisao=inteiro(pedido.get("revisaoAtual"));
        if (!(pedido.get("versoesCanais") instanceof Map<?,?> raw))
            throw new IllegalArgumentException("versoesCanais deve ser um objeto com as versões conferidas.");
        Map<String,Long> versoes=new TreeMap<>();
        raw.forEach((k,v) -> versoes.put(k.toString(),inteiro(v)));
        linhas.buscar(id);
        // Mesmo bloqueio usado ao editar os canais e ao atualizar a identificação.
        if (!repository.bloquearLinha(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        schema();
        var conferencia=conferirFontes(id,fontes);
        if (conferencia.versaoLinha()!=versao || !conferencia.versoesCanais().equals(versoes)
                || conferencia.revisaoAtual()!=revisao)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"O cadastro mudou. Confira novamente antes de concluir.");
        if (!conferencia.podeConcluir()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                String.join(" ",conferencia.pendencias()));
        var linha=linhas.buscar(id);
        Map<String,Object> documento=new LinkedHashMap<>();
        documento.put("linha",linha); documento.put("canais",canais.listar(id));
        documento.put("fontes",fontes); documento.put("versaoLinha",versao); documento.put("versoesCanais",versoes);
        jdbc.update("INSERT INTO configuracao.linha_configuracao (dispositivo_id,revisao,documento) VALUES (?,?,?)",
                id,Math.addExact(revisao,1),json.writeValueAsString(documento));
        return buscar(id);
    }

    @Transactional(readOnly=true)
    @SuppressWarnings("unchecked")
    public Conclusao buscar(String id) {
        linhas.buscar(id); schema();
        var documentos=jdbc.query("SELECT * FROM configuracao.linha_configuracao WHERE dispositivo_id=? ORDER BY revisao DESC LIMIT 1",
                (rs,row) -> new Conclusao(rs.getLong("revisao"),
                    rs.getObject("concluido_em",java.time.OffsetDateTime.class).toInstant().toString(),
                    "CONFIGURACAO_CONCLUIDA",false,json.readValue(rs.getString("documento"),Map.class)),id);
        if (documentos.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Nenhuma configuração concluída.");
        var atual=documentos.get(0);
        var versoes=new TreeMap<String,Long>();
        canais.listar(id).forEach(c -> versoes.put(c.codigo(),c.versao()));
        var versoesConcluidas=new TreeMap<String,Long>();
        ((Map<String,Object>)atual.documento().get("versoesCanais")).forEach((k,v)->versoesConcluidas.put(k,inteiro(v)));
        boolean coincide=versoes.equals(versoesConcluidas)
                && inteiro(atual.documento().get("versaoLinha"))==linhas.buscar(id).versao();
        return new Conclusao(atual.revisao(),atual.concluidoEm(),
                coincide?"CONFIGURACAO_CONCLUIDA":"RASCUNHO_ALTERADO",processando(id,atual.revisao()),atual.documento());
    }

    private boolean processando(String id,long revisao) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_schema)='configuracao' AND lower(table_name)='operacao_estado'",Long.class)==0) return false;
        return jdbc.queryForObject("SELECT COUNT(*) FROM configuracao.operacao_estado s JOIN configuracao.operacao_execucao e ON e.execucao_id=s.execucao_id WHERE s.dispositivo_id=? AND s.ativo=TRUE AND e.revisao=?",Long.class,id,revisao)>0;
    }

    private long revisaoAtual(String id) {
        schema();
        return jdbc.queryForObject("SELECT COALESCE(MAX(revisao),0) FROM configuracao.linha_configuracao WHERE dispositivo_id=?",Long.class,id);
    }
    private void schema() {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_schema)='configuracao' AND lower(table_name)='linha_configuracao'",Long.class)==0)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Execute banco/003_configuracao_concluida.sql no PostgreSQL.");
    }
    private Map<String,String> fontes(Object valor) {
        if (!(valor instanceof Map<?,?> m) || !m.keySet().equals(Set.of("estado","total","qualidade")))
            throw new IllegalArgumentException("fontes deve conter estado, total e qualidade, escolhidos explicitamente.");
        Map<String,String> resultado=new TreeMap<>();
        m.forEach((k,v)->{
            if (!(v instanceof String s) || s.isBlank()) throw new IllegalArgumentException("Informe o código de cada fonte.");
            resultado.put(k.toString(),s);
        });
        return resultado;
    }
    private long inteiro(Object valor) {
        if (valor instanceof Number n) try {
            long v=new BigDecimal(n.toString()).longValueExact();
            if(v>=0 && v<Long.MAX_VALUE) return v;
        } catch (ArithmeticException | NumberFormatException ignored) { }
        throw new IllegalArgumentException("Versões e revisão devem ser inteiros não negativos.");
    }
    private void campos(Map<String,Object> dados, Set<String> esperados) {
        if (!dados.keySet().equals(esperados)) throw new IllegalArgumentException("Envie exatamente "+new TreeSet<>(esperados));
    }
}
