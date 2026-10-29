package br.com.telemetria.api.simulacao;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulacoes/contadores")
public class SimulacaoController {
    private final SimulacaoService service;
    public SimulacaoController(SimulacaoService service){this.service=service;}
    @PostMapping
    public SimulacaoService.Simulacao simular(@RequestBody Map<String,Object> dados){return service.simular(dados);}
}
