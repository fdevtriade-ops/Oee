package br.com.telemetria.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
        assertEquals(leitura, json.readValue(consulta.body(), Map.class));
        assertEquals(13, json.readValue(consulta.body(), Map.class).size());

        leitura.put("DO1", 1);
        leitura.put("AI1", -2.5);
        assertEquals(204, postar(json.writeValueAsString(leitura)).statusCode());
        assertEquals(leitura, json.readValue(consultar((String) leitura.get("id")).body(), Map.class));
    }

    @Test
    void separaLeiturasPorEquipamento() throws Exception {
        Map<String, Object> primeiro = exemplo();
        Map<String, Object> segundo = exemplo();
        segundo.put("DI1", 1);
        assertEquals(204, postar(json.writeValueAsString(primeiro)).statusCode());
        assertEquals(204, postar(json.writeValueAsString(segundo)).statusCode());
        assertEquals(primeiro, json.readValue(consultar((String) primeiro.get("id")).body(), Map.class));
        assertEquals(segundo, json.readValue(consultar((String) segundo.get("id")).body(), Map.class));
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
        assertEquals(leitura, json.readValue(consultar((String) leitura.get("id")).body(), Map.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "DI1", "DI2", "DI3", "DI4", "DO1", "DO2", "DO3", "DO4", "AI1", "AI2", "AI3", "AI4"})
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
        assertEquals(original, json.readValue(consultar((String) original.get("id")).body(), Map.class));
    }

    static Stream<Arguments> valoresInvalidos() {
        return Stream.of(
                Arguments.of("id", "  "), Arguments.of("id", 123),
                Arguments.of("DI1", "1"), Arguments.of("DI1", true),
                Arguments.of("DO1", 0.5), Arguments.of("DO1", 2),
                Arguments.of("DO1", -1), Arguments.of("AI1", "3.17"),
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

    private Map<String, Object> exemplo() {
        Map<String, Object> leitura = new LinkedHashMap<>();
        leitura.put("id", "TRD2001-" + UUID.randomUUID());
        for (String prefixo : new String[]{"DI", "DO"}) {
            for (int i = 1; i <= 4; i++) leitura.put(prefixo + i, 0);
        }
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
