package br.com.telemetria.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/leituras")
public class LeituraController {
    private final LeituraService service;
    private final br.com.telemetria.api.operacao.MotorOperacional motor;

    public LeituraController(LeituraService service,br.com.telemetria.api.operacao.MotorOperacional motor) {
        this.service = service;
        this.motor = motor;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receber(@RequestBody Map<String, Object> dados) {
        var leitura=service.validar(dados);
        motor.processar(leitura);
        service.armazenar(leitura);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> buscar(@PathVariable String id) {
        return service.buscar(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Nenhuma leitura recebida para este equipamento."));
    }
}
