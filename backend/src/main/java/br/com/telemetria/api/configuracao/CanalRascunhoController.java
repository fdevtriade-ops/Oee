package br.com.telemetria.api.configuracao;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import static br.com.telemetria.api.configuracao.CanalRascunhoRepository.CanalRascunho;

@RestController
@RequestMapping("/api/dispositivos/{id}/canais-rascunho")
public class CanalRascunhoController {
    private final CanalRascunhoService service;
    public CanalRascunhoController(CanalRascunhoService service) { this.service=service; }
    @GetMapping
    public List<CanalRascunho> listar(@PathVariable String id) { return service.listar(id); }
    @GetMapping("/{codigo}")
    public CanalRascunho buscar(@PathVariable String id,@PathVariable String codigo) { return service.buscar(id,codigo); }
    @PutMapping("/{codigo}")
    public CanalRascunho salvar(@PathVariable String id,@PathVariable String codigo,@RequestBody Map<String,Object> dados) {
        return service.salvar(id,codigo,dados);
    }
}
