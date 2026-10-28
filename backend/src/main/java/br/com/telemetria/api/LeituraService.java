package br.com.telemetria.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class LeituraService {
    // Somente a última leitura por equipamento. Reiniciar a API apaga as leituras.
    private final Map<String, Map<String, Object>> ultimasLeituras = new ConcurrentHashMap<>();

    public void receber(Map<String, Object> dados) {
        if (!(dados.get("id") instanceof String id) || id.isBlank()) {
            throw new IllegalArgumentException("O campo id deve ser um texto não vazio.");
        }

        Map<String, Object> leitura = new LinkedHashMap<>();
        leitura.put("id", id);

        for (String prefixo : new String[]{"DI", "DO"}) {
            for (int i = 1; i <= 4; i++) {
                String canal = prefixo + i;
                Object valor = dados.get(canal);
                if (!(valor instanceof Number numero)
                        || (numero.doubleValue() != 0 && numero.doubleValue() != 1)) {
                    throw new IllegalArgumentException(
                            "O campo " + canal + " é obrigatório e deve ser o número 0 ou 1.");
                }
                leitura.put(canal, numero.intValue());
            }
        }

        for (int i = 1; i <= 4; i++) {
            String canal = "AI" + i;
            Object valor = dados.get(canal);
            if (!(valor instanceof Number numero) || !Double.isFinite(numero.doubleValue())) {
                throw new IllegalArgumentException(
                        "O campo " + canal + " é obrigatório e deve ser um número finito.");
            }
            leitura.put(canal, numero);
        }

        // Valida tudo antes de substituir; campos extras nunca são armazenados.
        ultimasLeituras.put(id, Collections.unmodifiableMap(leitura));
    }

    public Optional<Map<String, Object>> buscar(String id) {
        return Optional.ofNullable(ultimasLeituras.get(id));
    }
}
