package br.com.telemetria.api.previa;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.telemetria.api.LeituraService;
import br.com.telemetria.api.configuracao.CanalRascunhoService;
import br.com.telemetria.api.configuracao.CanalRascunhoRepository.CanalRascunho;
import static br.com.telemetria.api.previa.InterpretadorCanal.Interpretacao;

@Service
public class PreviaService {
    private final LeituraService leituras;
    private final CanalRascunhoService canais;
    private final InterpretadorCanal interpretador;
    public PreviaService(LeituraService leituras,CanalRascunhoService canais,InterpretadorCanal interpretador) {
        this.leituras=leituras; this.canais=canais; this.interpretador=interpretador;
    }
    public record Previa(String dispositivoId,String situacao,String timestamp,String recebidoEm,List<Interpretacao> canais) { }

    @Transactional(readOnly=true)
    public Previa consultar(String id) {
        Map<String,CanalRascunho> configs=new HashMap<>();
        for (CanalRascunho c:canais.listar(id)) configs.put(c.codigo(),c);
        Map<String,Object> leitura=leituras.buscar(id).orElse(Map.of());
        List<Interpretacao> resultado=new ArrayList<>();
        for (String prefixo:List.of("DI","contadorDI","AI","DO")) {
            for (int i=1;i<=4;i++) {
                String codigo=prefixo+i;
                resultado.add(interpretador.interpretar(codigo,configs.get(codigo),leitura.get(codigo)));
            }
        }
        return new Previa(id,"RASCUNHO",(String)leitura.get("timestamp"),(String)leitura.get("recebidoEm"),List.copyOf(resultado));
    }
}
