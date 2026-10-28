package br.com.telemetria.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/dispositivos")
public class DispositivoController {
    private final LeituraService leituras;

    public DispositivoController(LeituraService leituras) { this.leituras = leituras; }

    public record CanalRecebido(String codigo, String tipo, String entradaAssociada,
                               Object valor, String situacaoLeitura) { }
    public record DispositivoRecebido(String id, String timestamp, String recebidoEm,
                                      List<CanalRecebido> canais) { }

    @GetMapping
    public List<DispositivoRecebido> listar() {
        return leituras.listar().stream().map(this::dispositivo).toList();
    }

    @GetMapping("/{id}")
    public DispositivoRecebido buscar(@PathVariable String id) {
        return dispositivo(leituras.buscar(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Nenhuma leitura recebida para este dispositivo nesta execução da API.")));
    }

    private DispositivoRecebido dispositivo(Map<String, Object> leitura) {
        List<CanalRecebido> canais = new ArrayList<>();
        // Mostra apenas canais presentes no contrato validado, sem atribuir funções.
        for (String tipo : new String[]{"DI", "CONTADOR", "DO", "AI"}) {
            for (int i = 1; i <= 4; i++) {
                String codigo = tipo.equals("CONTADOR") ? "contadorDI" + i : tipo + i;
                if (!leitura.containsKey(codigo)) continue;
                Object valor = leitura.get(codigo);
                String situacao = tipo.equals("CONTADOR") && ((Number) valor).longValue() == -1
                        ? "DESLIGADO_OU_DESCONFIGURADO" : "LEITURA_RECEBIDA";
                canais.add(new CanalRecebido(codigo, tipo,
                        tipo.equals("CONTADOR") ? "DI" + i : null, valor, situacao));
            }
        }
        return new DispositivoRecebido((String) leitura.get("id"),
                (String) leitura.get("timestamp"), (String) leitura.get("recebidoEm"), List.copyOf(canais));
    }
}
