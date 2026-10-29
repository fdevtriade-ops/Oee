package br.com.telemetria.api.configuracao;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class CanalRascunhoRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public CanalRascunhoRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc; this.json = json;
    }
    public record CanalRascunho(String dispositivoId, String codigo, String tipo,
            String nome, boolean habilitado, String funcao, Map<String,Object> parametros,
            long versao, String situacao, String atualizadoEm) { }

    public boolean schemaDisponivel() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE lower(table_schema) = 'configuracao' AND lower(table_name) = 'canal_rascunho'
                """, Long.class) > 0;
    }
    public boolean bloquearLinha(String id) {
        return !jdbc.queryForList("SELECT dispositivo_id FROM configuracao.linha_rascunho WHERE dispositivo_id = ? FOR UPDATE",id).isEmpty();
    }
    public boolean existeLinha(String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM configuracao.linha_rascunho WHERE dispositivo_id = ?",Long.class,id)>0;
    }
    @SuppressWarnings("unchecked")
    private List<CanalRascunho> consultar(String sql, Object... args) {
        return jdbc.query(sql, (rs,row) -> {
            Map<String,Object> c = json.readValue(rs.getString("configuracao"),Map.class);
            String codigo = rs.getString("codigo");
            return new CanalRascunho(rs.getString("dispositivo_id"), codigo,
                    codigo.startsWith("contador") ? "CONTADOR" : codigo.substring(0,2),
                    (String)c.get("nome"), (Boolean)c.get("habilitado"), (String)c.get("funcao"),
                    (Map<String,Object>)c.get("parametros"), rs.getLong("versao"), "RASCUNHO",
                    rs.getObject("atualizado_em",java.time.OffsetDateTime.class).toInstant().toString());
        },args);
    }
    public List<CanalRascunho> listar(String id) {
        return consultar("""
                SELECT * FROM configuracao.canal_rascunho WHERE dispositivo_id = ?
                ORDER BY CASE WHEN codigo LIKE 'DI%' THEN 1 WHEN codigo LIKE 'contador%' THEN 2 ELSE 3 END, codigo
                """,id);
    }
    public Optional<CanalRascunho> buscar(String id, String codigo) {
        return consultar("SELECT * FROM configuracao.canal_rascunho WHERE dispositivo_id = ? AND codigo = ?",id,codigo).stream().findFirst();
    }
    public void criar(String id, String codigo, Map<String,Object> config) {
        jdbc.update("INSERT INTO configuracao.canal_rascunho (dispositivo_id,codigo,configuracao,versao) VALUES (?,?,?,1)",
                id,codigo,json.writeValueAsString(config));
    }
    public boolean atualizar(String id,String codigo,Map<String,Object> config,long versao) {
        return jdbc.update("""
                UPDATE configuracao.canal_rascunho SET configuracao = ?, versao = versao + 1,
                    atualizado_em = CURRENT_TIMESTAMP
                WHERE dispositivo_id = ? AND codigo = ? AND versao = ?
                """,json.writeValueAsString(config),id,codigo,versao)==1;
    }
}
