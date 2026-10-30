package br.com.telemetria.api.configuracao;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dispositivos/{id}/configuracao")
public class ConclusaoConfiguracaoController {
    private final ConclusaoConfiguracaoService service;
    public ConclusaoConfiguracaoController(ConclusaoConfiguracaoService service) { this.service=service; }
    @PostMapping("/conferir")
    public ConclusaoConfiguracaoService.Conferencia conferir(@PathVariable String id,@RequestBody Map<String,Object> dados) {
        return service.conferir(id,dados);
    }
    @PostMapping("/concluir")
    public ConclusaoConfiguracaoService.Conclusao concluir(@PathVariable String id,@RequestBody Map<String,Object> dados) {
        return service.concluir(id,dados);
    }
    @GetMapping("/concluida")
    public ConclusaoConfiguracaoService.Conclusao buscar(@PathVariable String id) { return service.buscar(id); }
}
