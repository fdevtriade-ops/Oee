package br.com.telemetria.api.configuracao;

import java.util.List;

public final class ConfiguracaoDados {
    private ConfiguracaoDados() { }

    public record Maquina(String id, String nome) { }
    public record FuncaoDi(String codigo, String descricao) { }
    public record Canal(long id, String codigo, String tipo, int numero,
                        String nome, String funcao, Short nivelAtivo,
                        boolean habilitado, Float tensaoMin, Float tensaoMax) { }
    public record Configuracoes(String id, String nomeMaquina, List<Canal> canais) { }
}
