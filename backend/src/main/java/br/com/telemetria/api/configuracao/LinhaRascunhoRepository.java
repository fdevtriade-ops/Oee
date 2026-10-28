package br.com.telemetria.api.configuracao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LinhaRascunhoRepository {
    private final JdbcTemplate jdbc;
    public LinhaRascunhoRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record LinhaRascunho(UUID linhaId, String dispositivoId, String nomeLinha,
                               String produto, double velocidadeIdealPpm, long versao,
                               String situacao, String atualizadoEm) { }

    public boolean schemaDisponivel() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE lower(table_schema) = 'configuracao' AND lower(table_name) = 'linha_rascunho'
                """, Long.class) > 0;
    }

    private List<LinhaRascunho> consultar(String sql, Object... params) {
        return jdbc.query(sql, (rs, row) -> new LinhaRascunho(
                rs.getObject("linha_id", UUID.class), rs.getString("dispositivo_id"),
                rs.getString("nome_linha"), rs.getString("produto"),
                rs.getDouble("velocidade_ideal_ppm"), rs.getLong("versao"), "RASCUNHO",
                rs.getObject("atualizado_em", java.time.OffsetDateTime.class).toInstant().toString()), params);
    }

    public List<LinhaRascunho> listar() {
        return consultar("SELECT * FROM configuracao.linha_rascunho ORDER BY dispositivo_id");
    }

    public Optional<LinhaRascunho> buscar(String dispositivoId) {
        return consultar("SELECT * FROM configuracao.linha_rascunho WHERE dispositivo_id = ?", dispositivoId)
                .stream().findFirst();
    }

    public void criar(String id, String nome, String produto, double velocidade) {
        jdbc.update("""
                INSERT INTO configuracao.linha_rascunho
                (linha_id, dispositivo_id, nome_linha, produto, velocidade_ideal_ppm, versao)
                VALUES (?, ?, ?, ?, ?, 1)
                """, UUID.randomUUID(), id, nome, produto, velocidade);
    }

    public boolean atualizar(String id, String nome, String produto, double velocidade, long versao) {
        return jdbc.update("""
                UPDATE configuracao.linha_rascunho
                SET nome_linha = ?, produto = ?, velocidade_ideal_ppm = ?,
                    versao = versao + 1, atualizado_em = CURRENT_TIMESTAMP
                WHERE dispositivo_id = ? AND versao = ?
                """, nome, produto, velocidade, id, versao) == 1;
    }
}
