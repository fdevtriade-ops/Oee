package br.com.telemetria.api.previa;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dispositivos/{id}/previa")
public class PreviaController {
    private final PreviaService service;
    public PreviaController(PreviaService service) { this.service=service; }
    @GetMapping
    public PreviaService.Previa consultar(@PathVariable String id) { return service.consultar(id); }
}
