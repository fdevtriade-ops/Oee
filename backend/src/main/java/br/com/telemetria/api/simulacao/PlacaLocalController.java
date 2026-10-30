package br.com.telemetria.api.simulacao;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulacoes/placas/{id}")
public class PlacaLocalController {
    private final PlacaLocalService service;
    public PlacaLocalController(PlacaLocalService service) { this.service=service; }
    @PostMapping public Map<String,Object> criar(@PathVariable String id,@RequestBody Map<String,Object> pedido) { return service.criar(id,pedido); }
    @GetMapping public Map<String,Object> consultar(@PathVariable String id) { return service.consultar(id); }
    @PostMapping("/comandos") public Map<String,Object> comando(@PathVariable String id,@RequestBody Map<String,Object> pedido) { return service.comando(id,pedido); }
    @PostMapping("/passo") public Map<String,Object> passo(@PathVariable String id,@RequestBody Map<String,Object> pedido) { return service.passo(id,pedido); }
}
