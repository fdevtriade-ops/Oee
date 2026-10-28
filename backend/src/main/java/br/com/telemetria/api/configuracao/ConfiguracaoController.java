package br.com.telemetria.api.configuracao;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import static br.com.telemetria.api.configuracao.ConfiguracaoDados.*;

@RestController
public class ConfiguracaoController {
    private final ConfiguracaoService service;

    public ConfiguracaoController(ConfiguracaoService service) { this.service = service; }

    @GetMapping("/api/equipamentos")
    public List<Maquina> maquinas() { return service.maquinas(); }

    @GetMapping("/api/funcoes-di")
    public List<FuncaoDi> funcoes() { return service.funcoes(); }

    @GetMapping("/api/equipamentos/{id}/configuracoes")
    public Configuracoes consultar(@PathVariable String id) { return service.consultar(id); }

    @PutMapping("/api/equipamentos/{id}/canais/{codigo}")
    public Canal salvar(@PathVariable String id, @PathVariable String codigo,
                        @RequestBody Map<String, Object> dados) {
        return service.salvar(id, codigo, dados);
    }
}
