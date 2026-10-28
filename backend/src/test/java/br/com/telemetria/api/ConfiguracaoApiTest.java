package br.com.telemetria.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfiguracaoApiTest {
    @LocalServerPort int porta;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void limparConfiguracoesDosEquipamentosDeTeste() {
        jdbc.update("""
                UPDATE configuracao.canal SET nome=NULL, funcao_di_codigo=NULL,
                nivel_ativo=NULL, habilitado=FALSE WHERE maquina_id IN ('TESTE_A','TESTE_B')
                """);
    }

    @Test
    void consultaMaquinasCatalogoECanaisIncluindoAnalogicosVazios() throws Exception {
        var maquinas = get("/api/equipamentos");
        assertEquals(200, maquinas.statusCode());
        assertEquals(2, json.readValue(maquinas.body(), List.class).size());
        var funcoes = get("/api/funcoes-di");
        assertEquals(8, json.readValue(funcoes.body(), List.class).size());
        var resposta = get("/api/equipamentos/TESTE_A/configuracoes");
        assertEquals(200, resposta.statusCode());
        var dados = json.readTree(resposta.body());
        assertEquals("Máquina A", dados.get("nomeMaquina").asString());
        assertEquals(12, dados.get("canais").size());
        var ai = dados.get("canais").get(8);
        assertEquals("AI1", ai.get("codigo").asString());
        assertTrue(ai.get("funcao").isNull());
        assertTrue(ai.get("nivelAtivo").isNull());
        assertFalse(ai.get("habilitado").asBoolean());
        assertEquals(-0.5, ai.get("tensaoMin").asDouble());
        assertEquals(10, ai.get("tensaoMax").asDouble());
    }

    @Test
    void salvaNoBancoInverteNivelEPreservaOutraMaquina() throws Exception {
        var resposta = put("TESTE_A", "DI1", corpo("MAQUINA_PARADA", "1", "true"));
        assertEquals(200, resposta.statusCode(), resposta.body());
        assertEquals(1, jdbc.queryForObject("""
                SELECT nivel_ativo FROM configuracao.canal
                WHERE maquina_id='TESTE_A' AND tipo='DI' AND numero=1
                """, Integer.class));
        assertEquals(200, put("TESTE_A", "DI1", corpo("MAQUINA_PARADA", "0", "true")).statusCode());
        var leitura = json.readTree(get("/api/equipamentos/TESTE_A/configuracoes").body());
        assertEquals(0, leitura.get("canais").get(0).get("nivelAtivo").asInt());
        assertEquals("MAQUINA_PARADA", leitura.get("canais").get(0).get("funcao").asString());
        assertNull(jdbc.queryForObject("""
                SELECT nivel_ativo FROM configuracao.canal
                WHERE maquina_id='TESTE_B' AND tipo='DI' AND numero=1
                """, Integer.class));
    }

    @ParameterizedTest
    @MethodSource("invalidos")
    void rejeitaConfiguracoesInvalidasSemModificarBanco(String canal, String corpo) throws Exception {
        var resposta = put("TESTE_A", canal, corpo);
        assertEquals(400, resposta.statusCode(), resposta.body());
        assertTrue(resposta.body().contains("erro"));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM configuracao.canal
                WHERE maquina_id='TESTE_A' AND habilitado=TRUE
                """, Integer.class));
    }

    static Stream<Arguments> invalidos() {
        return Stream.of(
                Arguments.of("DI1", corpo("INEXISTENTE", "1", "true")),
                Arguments.of("DI1", corpo("FALHA", "2", "true")),
                Arguments.of("DI1", corpo("FALHA", "0.5", "true")),
                Arguments.of("DI1", corpo("FALHA", "\"1\"", "true")),
                Arguments.of("DI1", corpo("FALHA", "1", "null")),
                Arguments.of("DI1", corpo("FALHA", "null", "true")),
                Arguments.of("DI1", "{\"nome\":null,\"funcao\":null,\"nivelAtivo\":1,\"habilitado\":true}"),
                Arguments.of("DO1", corpo("FALHA", "1", "true")),
                Arguments.of("AI1", corpo("FALHA", "null", "false")),
                Arguments.of("AI1", "{\"nome\":null,\"funcao\":null,\"nivelAtivo\":1,\"habilitado\":false}"),
                Arguments.of("DI1", "{}"),
                Arguments.of("DI1", corpo("FALHA", "1", "true").replace("Falha teste", "  ")),
                Arguments.of("DI01", corpo("FALHA", "1", "true")),
                Arguments.of("DI9999999999999999999", corpo("FALHA", "1", "true")));
    }

    @Test
    void limpaConfiguracaoSemPreencherAnalogicos() throws Exception {
        assertEquals(200, put("TESTE_A", "DI1", corpo("FALHA", "1", "true")).statusCode());
        String vazio = "{\"nome\":null,\"funcao\":null,\"nivelAtivo\":null,\"habilitado\":false}";
        assertEquals(200, put("TESTE_A", "DI1", vazio).statusCode());
        assertEquals(200, put("TESTE_A", "AI1", vazio).statusCode());
        assertNull(jdbc.queryForObject("SELECT nome FROM configuracao.canal WHERE maquina_id='TESTE_A' AND tipo='DI' AND numero=1", String.class));
    }

    @Test
    void aceitaCanalAdicionalJaCadastradoSemMudarEstrutura() throws Exception {
        jdbc.update("INSERT INTO configuracao.canal(maquina_id,tipo,numero) VALUES ('TESTE_A','DI',5)");
        try {
            assertEquals(200, put("TESTE_A", "DI5", corpo("FALHA", "0", "true")).statusCode());
        } finally {
            jdbc.update("DELETE FROM configuracao.canal WHERE maquina_id='TESTE_A' AND tipo='DI' AND numero=5");
        }
    }

    @Test
    void maquinaOuCanalDesconhecidoRetorna404() throws Exception {
        assertEquals(404, get("/api/equipamentos/NAO_EXISTE/configuracoes").statusCode());
        assertEquals(404, put("NAO_EXISTE", "DI1", corpo("FALHA", "1", "true")).statusCode());
        assertEquals(404, put("TESTE_A", "DI16", corpo("FALHA", "1", "true")).statusCode());
    }

    private static String corpo(String funcao, String nivel, String habilitado) {
        return "{\"nome\":\"Falha teste\",\"funcao\":\"" + funcao + "\",\"nivelAtivo\":" + nivel + ",\"habilitado\":" + habilitado + "}";
    }

    private HttpResponse<String> get(String caminho) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(caminho)).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String maquina, String canal, String corpo) throws Exception {
        return http.send(HttpRequest.newBuilder(uri("/api/equipamentos/" + maquina + "/canais/" + canal))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(corpo)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String caminho) { return URI.create("http://127.0.0.1:" + porta + caminho); }
}
