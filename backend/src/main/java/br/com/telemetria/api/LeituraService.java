package br.com.telemetria.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class LeituraService {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("dd/MM/uuuu HH:mm:ss", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    // Somente a última leitura por equipamento. Reiniciar a API apaga as leituras.
    private final Map<String, Map<String, Object>> ultimasLeituras = new ConcurrentHashMap<>();

    public void receber(Map<String, Object> dados) {
        Instant recebidoEm = Instant.now();
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
            String canal = "contadorDI" + i;
            // -1 significa desligado/desconfigurado, nunca produção negativa.
            leitura.put(canal, validarContador(canal, dados.get(canal)));
        }

        for (int i = 1; i <= 4; i++) {
            String canal = "AI" + i;
            Object valor = dados.get(canal);
            if (!(valor instanceof Number numero) || !Double.isFinite(numero.doubleValue())
                    || numero.doubleValue() < 0 || numero.doubleValue() > 10) {
                throw new IllegalArgumentException(
                        "O campo " + canal + " é obrigatório e deve ser um número entre 0 e 10 V.");
            }
            leitura.put(canal, numero);
        }

        Object timestamp = dados.get("timestamp");
        if (!(timestamp instanceof String texto) || !texto.matches("[0-9]{2}/[0-9]{2}/[0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2}")) {
            throw timestampInvalido();
        }
        try {
            if (LocalDateTime.parse(texto, TIMESTAMP).getYear() < 1) throw timestampInvalido();
        } catch (DateTimeParseException erro) {
            throw timestampInvalido();
        }
        // O timestamp da placa não contém fuso; não presumir conversão nem política de atraso.
        leitura.put("timestamp", texto);
        leitura.put("recebidoEm", recebidoEm.toString());

        // Valida tudo antes de substituir; metadados enviados pelo cliente não são confiados.
        ultimasLeituras.put(id, Collections.unmodifiableMap(leitura));
    }

    private long validarContador(String canal, Object valor) {
        if (valor instanceof Number numero) {
            try {
                long contador = new BigDecimal(numero.toString()).longValueExact();
                if (contador >= -1) return contador;
            } catch (ArithmeticException | NumberFormatException erro) {
                // Rejeita frações, valores não finitos e estouro sem truncar a contagem.
            }
        }
        throw new IllegalArgumentException("O campo " + canal
                + " é obrigatório e deve ser -1 ou um inteiro de 0 a " + Long.MAX_VALUE + ".");
    }

    private IllegalArgumentException timestampInvalido() {
        return new IllegalArgumentException(
                "O campo timestamp é obrigatório e deve conter uma data válida no formato dd/MM/yyyy HH:mm:ss.");
    }

    public List<Map<String, Object>> listar() {
        return ultimasLeituras.values().stream()
                .sorted(Comparator.comparing(leitura -> (String) leitura.get("id")))
                .toList();
    }

    public Optional<Map<String, Object>> buscar(String id) {
        return Optional.ofNullable(ultimasLeituras.get(id));
    }
}
