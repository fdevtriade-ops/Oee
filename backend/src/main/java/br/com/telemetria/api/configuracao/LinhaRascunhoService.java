package br.com.telemetria.api.configuracao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.telemetria.api.LeituraService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static br.com.telemetria.api.configuracao.LinhaRascunhoRepository.LinhaRascunho;

@Service
public class LinhaRascunhoService {
    private static final Set<String> CAMPOS = Set.of("nomeLinha", "produto", "velocidadeIdealPpm", "versao");
    private final LinhaRascunhoRepository repository;
    private final ConfiguracaoRepository configuracoes;
    private final LeituraService leituras;

    public LinhaRascunhoService(LinhaRascunhoRepository repository,
                               ConfiguracaoRepository configuracoes, LeituraService leituras) {
        this.repository = repository;
        this.configuracoes = configuracoes;
        this.leituras = leituras;
    }

    @Transactional(readOnly = true)
    public List<LinhaRascunho> listar() { conferirSchema(); return repository.listar(); }

    @Transactional(readOnly = true)
    public LinhaRascunho buscar(String id) {
        conferirSchema();
        return repository.buscar(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Não existe rascunho salvo para este dispositivo."));
    }

    @Transactional
    public LinhaRascunho salvar(String id, Map<String, Object> dados) {
        if (id.isBlank() || id.length() > 100)
            throw new IllegalArgumentException("O id deve conter de 1 a 100 caracteres.");
        if (!dados.keySet().equals(CAMPOS))
            throw new IllegalArgumentException("Envie exatamente nomeLinha, produto, velocidadeIdealPpm e versao.");
        String nome = texto(dados.get("nomeLinha"), "nomeLinha");
        String produto = texto(dados.get("produto"), "produto");
        if (!(dados.get("velocidadeIdealPpm") instanceof Number n)
                || !Double.isFinite(n.doubleValue()) || n.doubleValue() <= 0)
            throw new IllegalArgumentException("velocidadeIdealPpm deve ser um número finito maior que zero.");
        long versao = versao(dados.get("versao"));
        conferirSchema();
        if (versao == 0) {
            if (repository.buscar(id).isPresent()) throw conflito();
            if (leituras.buscar(id).isEmpty() && configuracoes.maquina(id).isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Receba uma leitura do dispositivo ou cadastre a máquina antes de criar seu rascunho.");
            repository.criar(id, nome, produto, n.doubleValue());
        } else if (!repository.atualizar(id, nome, produto, n.doubleValue(), versao)) {
            throw conflito();
        }
        return repository.buscar(id).orElseThrow();
    }

    private void conferirSchema() {
        if (!repository.schemaDisponivel())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cadastro de linha indisponível: execute banco/001_linha_rascunho.sql no PostgreSQL.");
    }

    private String texto(Object valor, String campo) {
        if (!(valor instanceof String s) || s.isBlank() || s.length() > 150)
            throw new IllegalArgumentException(campo + " deve ser texto não vazio de até 150 caracteres.");
        return s.trim();
    }

    private long versao(Object valor) {
        if (valor instanceof Number n) {
            try {
                long v = new BigDecimal(n.toString()).longValueExact();
                if (v >= 0 && v < Long.MAX_VALUE) return v;
            } catch (ArithmeticException | NumberFormatException e) { /* validação abaixo */ }
        }
        throw new IllegalArgumentException("versao deve ser um inteiro não negativo; use 0 para criar.");
    }

    private ResponseStatusException conflito() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "O rascunho já existe ou foi alterado. Consulte novamente e envie a versao atual.");
    }
}
