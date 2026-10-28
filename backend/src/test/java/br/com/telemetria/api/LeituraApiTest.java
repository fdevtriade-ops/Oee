package br.com.telemetria.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LeituraApiTest {
    @LocalServerPort
    private int porta;

    @Autowired
    private ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void recebeObjetoInteiroIgnoraExtrasEPermiteConsultarUltimaLeitura() throws Exception {
        Map<String, Object> leitura = exemplo();
        Map<String, Object> enviado = new LinkedHashMap<>(leitura);
        enviado.put("topic", "My_Home_1/BM/JSON");
        enviado.put("_msgid", "id-interno-node-red");

        HttpResponse<String> resposta = postar(json.writeValueAsString(enviado));
        assertEquals(204, resposta.statusCode());
        assertTrue(resposta.body().isEmpty());

        HttpResponse<String> consulta = consultar((String) leitura.get("id"));
        assertEquals(200, consulta.statusCode());
        assertLeitura(leitura, consulta);
        assertEquals(19, json.readValue(consulta.body(), Map.class).size());

        leitura.put("DO1", 1);
        leitura.put("AI1", 2.5);
        assertEquals(204, postar(json.writeValueAsString(leitura)).statusCode());
        assertLeitura(leitura, consultar((String) leitura.get("id")));
    }

    @Test
    void separaLeiturasPorEquipamento() throws Exception {
        Map<String, Object> primeiro = exemplo();
        Map<String, Object> segundo = exemplo();
        segundo.put("DI1", 1);
        assertEquals(204, postar(json.writeValueAsString(primeiro)).statusCode());
        assertEquals(204, postar(json.writeValueAsString(segundo)).statusCode());
        assertLeitura(primeiro, consultar((String) primeiro.get("id")));
        assertLeitura(segundo, consultar((String) segundo.get("id")));
    }

    @Test
    void aceitaDigitaisNumericamenteInteirosEAnalogicoInteiro() throws Exception {
        Map<String, Object> leitura = exemplo();
        leitura.put("DI1", 0.0);
        leitura.put("DO1", 1.0);
        leitura.put("AI1", 7);
        assertEquals(204, postar(json.writeValueAsString(leitura)).statusCode());
        leitura.put("DI1", 0);
        leitura.put("DO1", 1);
        assertLeitura(leitura, consultar((String) leitura.get("id")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "DI1", "DI2", "DI3", "DI4", "DO1", "DO2", "DO3", "DO4", "AI1", "AI2", "AI3", "AI4", "contadorDI1", "contadorDI2", "contadorDI3", "contadorDI4", "timestamp"})
    void rejeitaCadaCampoAusenteOuNulo(String campo) throws Exception {
        Map<String, Object> leitura = exemplo();
        leitura.remove(campo);
        assertErro400(postar(json.writeValueAsString(leitura)), campo);
        leitura.put(campo, null);
        assertErro400(postar(json.writeValueAsString(leitura)), campo);
    }

    @ParameterizedTest
    @MethodSource("valoresInvalidos")
    void rejeitaTiposErradosSemSubstituirUltimaLeitura(String campo, Object invalido) throws Exception {
        Map<String, Object> original = exemplo();
        assertEquals(204, postar(json.writeValueAsString(original)).statusCode());
        Map<String, Object> alterada = new LinkedHashMap<>(original);
        alterada.put(campo, invalido);
        assertErro400(postar(json.writeValueAsString(alterada)), campo);
        assertLeitura(original, consultar((String) original.get("id")));
    }

    static Stream<Arguments> valoresInvalidos() {
        return Stream.of(
                Arguments.of("id", "  "), Arguments.of("id", 123),
                Arguments.of("DI1", "1"), Arguments.of("DI1", true),
                Arguments.of("DO1", 0.5), Arguments.of("DO1", 2),
                Arguments.of("DO1", -1), Arguments.of("AI1", "3.17"),
                Arguments.of("AI1", -0.01), Arguments.of("AI1", 10.01),
                Arguments.of("contadorDI1", -2), Arguments.of("contadorDI2", 0.5),
                Arguments.of("contadorDI3", "12"), Arguments.of("contadorDI4", true),
                Arguments.of("contadorDI1", new java.math.BigInteger("9223372036854775808")),
                Arguments.of("timestamp", "31/02/2026 10:21:13"),
                Arguments.of("timestamp", "29/02/2025 10:21:13"),
                Arguments.of("timestamp", "28/10/2026 24:00:00"),
                Arguments.of("timestamp", "28/10/2026 10:21:60"),
                Arguments.of("timestamp", "2026-10-28T10:21:13Z"),
                Arguments.of("timestamp", "1/1/2026 10:21:13"),
                Arguments.of("timestamp", ""), Arguments.of("timestamp", 123),
                Arguments.of("AI1", false), Arguments.of("AI1", Map.of("valor", 3.17)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "[{}]", "{", "\"texto\"", "123"})
    void rejeitaCorpoQueNaoEObjetoJson(String corpo) throws Exception {
        assertErro400(postar(corpo), "JSON");
    }

    @Test
    void rejeitaNumeroAnalogicoNaoFinito() throws Exception {
        String corpo = json.writeValueAsString(exemplo()).replace("3.17", "1e999");
        assertEquals(400, postar(corpo).statusCode());
    }

    @Test
    void equipamentoSemLeituraRetorna404() throws Exception {
        HttpResponse<String> resposta = consultar("inexistente-" + UUID.randomUUID());
        assertEquals(404, resposta.statusCode());
        assertTrue(resposta.body().contains("Nenhuma leitura"));
    }

    @Test
    void aceitaJsonRealMk2ERegistraRecebimentoDoServidor() throws Exception {
        Map<String, Object> leitura = exemplo();
        leitura.put("id", "TRD2001");
        for (int i = 1; i <= 4; i++) leitura.put("AI" + i, 0);
        Map<String, Object> enviado = new LinkedHashMap<>(leitura);
        enviado.put("recebidoEm", "2000-01-01T00:00:00Z");
        Instant antes = Instant.now();
        assertEquals(204, postar(json.writeValueAsString(enviado)).statusCode());
        HttpResponse<String> resposta = consultar("TRD2001");
        assertLeitura(leitura, resposta);
        Instant recebido = Instant.parse(json.readTree(resposta.body()).get("recebidoEm").asString());
        assertFalse(recebido.isBefore(antes));
        assertFalse(recebido.isAfter(Instant.now()));
    }

    @Test
    void aceitaContadoresGrandesLimitesAnalogicosEAnoBissexto() throws Exception {
        Map<String, Object> leitura = exemplo();
        leitura.put("contadorDI1", Long.MAX_VALUE);
        leitura.put("contadorDI2", 9007199254740993L);
        leitura.put("contadorDI3", 0);
        leitura.put("contadorDI4", 42);
        leitura.put("AI1", -0.0);
        leitura.put("AI2", 10);
        leitura.put("timestamp", "29/02/2024 23:59:59");
        assertEquals(204, postar(json.writeValueAsString(leitura)).statusCode());
        assertLeitura(leitura, consultar((String) leitura.get("id")));
    }

    @Test
    void rejeicaoPreservaTambemHorarioDeRecebimentoAnterior() throws Exception {
        Map<String, Object> leitura = exemplo();
        String id = (String) leitura.get("id");
        assertEquals(204, postar(json.writeValueAsString(leitura)).statusCode());
        String anterior = consultar(id).body();
        leitura.put("contadorDI4", -2);
        assertErro400(postar(json.writeValueAsString(leitura)), "contadorDI4");
        assertEquals(anterior, consultar(id).body());
    }

    private void assertLeitura(Map<String, Object> esperada, HttpResponse<String> resposta) {
        assertEquals(200, resposta.statusCode());
        Map<String, Object> atual = new LinkedHashMap<>(json.readValue(resposta.body(), Map.class));
        Object recebido = atual.remove("recebidoEm");
        assertTrue(recebido instanceof String);
        Instant.parse((String) recebido);
        assertEquals(esperada, atual);
    }

    private Map<String, Object> exemplo() {
        Map<String, Object> leitura = new LinkedHashMap<>();
        leitura.put("id", "TRD2001-" + UUID.randomUUID());
        for (String prefixo : new String[]{"DI", "DO"}) {
            for (int i = 1; i <= 4; i++) leitura.put(prefixo + i, 0);
        }
        for (int i = 1; i <= 4; i++) leitura.put("contadorDI" + i, -1);
        leitura.put("timestamp", "28/10/2026 10:21:13");
        leitura.put("AI1", 3.17);
        leitura.put("AI2", 5.17);
        leitura.put("AI3", 6.99);
        leitura.put("AI4", 2.24);
        return leitura;
    }

    private HttpResponse<String> postar(String corpo) throws Exception {
        return http.send(HttpRequest.newBuilder(uri("/api/leituras"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(corpo)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> consultar(String id) throws Exception {
        return http.send(HttpRequest.newBuilder(uri("/api/leituras/" + id))
                        .timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String caminho) {
        return URI.create("http://127.0.0.1:" + porta + caminho);
    }

    private void assertErro400(HttpResponse<String> resposta, String trecho) {
        assertEquals(400, resposta.statusCode(), resposta.body());
        assertTrue(resposta.body().contains(trecho), resposta.body());
        assertFalse(resposta.body().contains("stackTrace"));
    }
}
