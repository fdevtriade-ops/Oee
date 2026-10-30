package br.com.telemetria.api.operacao;

import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dispositivos/{id}/operacao")
public class OperacaoController {
    private final MotorOperacional motor;
    private final ConsultaIndicadores indicadores;
    public OperacaoController(MotorOperacional motor,ConsultaIndicadores indicadores) { this.motor=motor; this.indicadores=indicadores; }
    @PostMapping("/ativar") public Map<String,Object> ativar(@PathVariable String id,@RequestBody Map<String,Object> pedido) { return motor.ativar(id,pedido); }
    @GetMapping public Map<String,Object> consultar(@PathVariable String id) { return motor.consultar(id); }
    @PostMapping("/parar") public Map<String,Object> parar(@PathVariable String id,@RequestBody Map<String,Object> pedido) { return motor.parar(id,pedido); }
    @PostMapping("/nova-referencia") public Map<String,Object> retomar(@PathVariable String id,@RequestBody Map<String,Object> pedido) { return motor.novaReferencia(id,pedido); }
    @PostMapping("/programadas") public Map<String,Object> programar(@PathVariable String id,@RequestBody Map<String,Object> pedido) { return motor.programar(id,pedido); }
    @GetMapping("/indicadores") public List<Map<String,Object>> indicadores(@PathVariable String id,
            @RequestParam(defaultValue="RESUMO") ConsultaIndicadores.Agrupamento agrupamento,
            @RequestParam(required=false) UUID execucao,@RequestParam(required=false) Instant inicio,@RequestParam(required=false) Instant fim) {
        return indicadores.consultar(id,agrupamento,execucao,inicio,fim);
    }
}
