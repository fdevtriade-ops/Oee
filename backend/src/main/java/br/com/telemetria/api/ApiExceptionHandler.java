package br.com.telemetria.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> validacao(IllegalArgumentException erro) {
        return Map.of("erro", erro.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> jsonInvalido() {
        return Map.of("erro", "Envie um objeto JSON válido no corpo da requisição.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> integridade() {
        return Map.of("erro", "A configuração viola uma regra do banco. Confira a função e os valores do canal.");
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> bancoIndisponivel() {
        return Map.of("erro", "O banco de configurações está indisponível.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException erro) {
        return ResponseEntity.status(erro.getStatusCode())
                .body(Map.of("erro", erro.getReason() == null ? "Requisição inválida." : erro.getReason()));
    }
}
