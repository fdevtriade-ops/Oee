package br.com.telemetria.api.configuracao;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import static br.com.telemetria.api.configuracao.LinhaRascunhoRepository.LinhaRascunho;

@RestController
public class LinhaRascunhoController {
    private final LinhaRascunhoService service;
    public LinhaRascunhoController(LinhaRascunhoService service) { this.service = service; }

    @GetMapping("/api/linhas/rascunhos")
    public List<LinhaRascunho> listar() { return service.listar(); }

    @GetMapping("/api/dispositivos/{id}/linha-rascunho")
    public LinhaRascunho buscar(@PathVariable String id) { return service.buscar(id); }

    @PutMapping("/api/dispositivos/{id}/linha-rascunho")
    public LinhaRascunho salvar(@PathVariable String id, @RequestBody Map<String, Object> dados) {
        return service.salvar(id, dados);
    }
}
