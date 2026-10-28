package br.com.telemetria.api.configuracao;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static br.com.telemetria.api.configuracao.ConfiguracaoDados.*;

@Service
public class ConfiguracaoService {
    private static final Pattern CODIGO = Pattern.compile("^(DI|DO|AI)([1-9][0-9]*)$");
    private static final Set<String> CAMPOS = Set.of("nome", "funcao", "nivelAtivo", "habilitado");
    private final ConfiguracaoRepository repository;

    public ConfiguracaoService(ConfiguracaoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Maquina> maquinas() { return repository.maquinas(); }

    @Transactional(readOnly = true)
    public List<FuncaoDi> funcoes() { return repository.funcoes(); }

    @Transactional(readOnly = true)
    public Configuracoes consultar(String maquinaId) {
        Maquina maquina = repository.maquina(maquinaId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Máquina não cadastrada."));
        return new Configuracoes(maquina.id(), maquina.nome(), repository.canais(maquina.id()));
    }

    @Transactional
    public Canal salvar(String maquinaId, String codigo, Map<String, Object> dados) {
        var match = CODIGO.matcher(codigo);
        if (!match.matches()) throw new IllegalArgumentException("Canal inválido. Use DI1, DO1, AI1 etc.");
        int numero;
        try {
            numero = Integer.parseInt(match.group(2));
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException("Número do canal fora do limite permitido.");
        }
        if (!dados.keySet().equals(CAMPOS)) {
            throw new IllegalArgumentException("Envie exatamente nome, funcao, nivelAtivo e habilitado. Use null nos campos sem configuração.");
        }
        String nome = textoOpcional(dados.get("nome"), "nome", 150);
        String funcao = textoOpcional(dados.get("funcao"), "funcao", 40);
        if (!(dados.get("habilitado") instanceof Boolean habilitado)) {
            throw new IllegalArgumentException("habilitado deve ser true ou false.");
        }
        Short nivel = null;
        if (dados.get("nivelAtivo") != null) {
            if (!(dados.get("nivelAtivo") instanceof Number n)
                    || (n.doubleValue() != 0 && n.doubleValue() != 1)) {
                throw new IllegalArgumentException("nivelAtivo deve ser 0, 1 ou null.");
            }
            nivel = ((Number) dados.get("nivelAtivo")).shortValue();
        }

        Canal atual = repository.canalParaAlterar(maquinaId, match.group(1), numero).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Canal não cadastrado para esta máquina."));
        if (!atual.tipo().equals("DI") && funcao != null) {
            throw new IllegalArgumentException("As funções do catálogo só podem ser atribuídas a DIs.");
        }
        if (funcao != null && !repository.existeFuncao(funcao)) {
            throw new IllegalArgumentException("Função desconhecida. Consulte /api/funcoes-di.");
        }
        if (atual.tipo().equals("AI") && nivel != null) {
            throw new IllegalArgumentException("Uma AI não possui nivelAtivo digital.");
        }
        if (habilitado && !atual.tipo().equals("AI") && nivel == null) {
            throw new IllegalArgumentException("Um canal digital habilitado precisa de nivelAtivo.");
        }
        if (habilitado && atual.tipo().equals("DI") && funcao == null) {
            throw new IllegalArgumentException("Uma DI habilitada precisa de uma função.");
        }

        repository.salvar(atual.id(), nome, funcao, nivel, habilitado);
        return new Canal(atual.id(), atual.codigo(), atual.tipo(), atual.numero(), nome,
                funcao, nivel, habilitado, atual.tensaoMin(), atual.tensaoMax());
    }

    private String textoOpcional(Object valor, String campo, int limite) {
        if (valor == null) return null;
        if (!(valor instanceof String texto) || texto.isBlank() || texto.length() > limite) {
            throw new IllegalArgumentException(campo + " deve ser texto não vazio de até " + limite + " caracteres, ou null.");
        }
        return (String) valor;
    }
}
