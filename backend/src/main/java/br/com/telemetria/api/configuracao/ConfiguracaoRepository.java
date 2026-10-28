package br.com.telemetria.api.configuracao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import static br.com.telemetria.api.configuracao.ConfiguracaoDados.*;

@Repository
public class ConfiguracaoRepository {
    private final JdbcTemplate jdbc;

    public ConfiguracaoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Maquina> maquinas() {
        return jdbc.query("SELECT id, nome FROM configuracao.maquina ORDER BY id",
                (rs, linha) -> new Maquina(rs.getString("id"), rs.getString("nome")));
    }

    public Optional<Maquina> maquina(String id) {
        return jdbc.query("SELECT id, nome FROM configuracao.maquina WHERE id = ?",
                (rs, linha) -> new Maquina(rs.getString("id"), rs.getString("nome")), id)
                .stream().findFirst();
    }

    public List<FuncaoDi> funcoes() {
        return jdbc.query("SELECT codigo, descricao FROM configuracao.funcao_di ORDER BY descricao",
                (rs, linha) -> new FuncaoDi(rs.getString("codigo"), rs.getString("descricao")));
    }

    public boolean existeFuncao(String codigo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM configuracao.funcao_di WHERE codigo = ?",
                Long.class, codigo) > 0;
    }

    public List<Canal> canais(String maquinaId) {
        return jdbc.query("""
                SELECT * FROM configuracao.canal WHERE maquina_id = ?
                ORDER BY CASE tipo WHEN 'DI' THEN 1 WHEN 'DO' THEN 2 ELSE 3 END, numero
                """, (rs, linha) -> canal(rs), maquinaId);
    }

    public Optional<Canal> canalParaAlterar(String maquinaId, String tipo, int numero) {
        return jdbc.query("""
                SELECT * FROM configuracao.canal
                WHERE maquina_id = ? AND tipo = ? AND numero = ? FOR UPDATE
                """, (rs, linha) -> canal(rs), maquinaId, tipo, numero).stream().findFirst();
    }

    public void salvar(long canalId, String nome, String funcao, Short nivelAtivo, boolean habilitado) {
        jdbc.update("""
                UPDATE configuracao.canal
                SET nome = ?, funcao_di_codigo = ?, nivel_ativo = ?, habilitado = ?
                WHERE id = ?
                """, nome, funcao, nivelAtivo, habilitado, canalId);
    }

    public void conferirSchema() {
        jdbc.queryForList("SELECT id, nome FROM configuracao.maquina WHERE FALSE");
        jdbc.queryForList("SELECT codigo, descricao FROM configuracao.funcao_di WHERE FALSE");
        jdbc.queryForList("""
                SELECT id, maquina_id, tipo, numero, nome, funcao_di_codigo,
                       nivel_ativo, habilitado, tensao_min, tensao_max
                FROM configuracao.canal WHERE FALSE
                """);
    }

    private Canal canal(ResultSet rs) throws SQLException {
        String tipo = rs.getString("tipo");
        int numero = rs.getInt("numero");
        return new Canal(rs.getLong("id"), tipo + numero, tipo, numero,
                rs.getString("nome"), rs.getString("funcao_di_codigo"),
                rs.getObject("nivel_ativo", Short.class), rs.getBoolean("habilitado"),
                rs.getObject("tensao_min", Float.class), rs.getObject("tensao_max", Float.class));
    }
}
