package br.com.telemetria.api;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import br.com.telemetria.api.configuracao.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CadastroLinhaApiTest {
    @LocalServerPort private int porta;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void descobreCanaisSemCadastrarMaquinaOuAtribuirFuncoes() throws Exception {
        String id = id();
        assertEquals(404, get("/api/dispositivos/" + id).statusCode());
        enviarLeitura(id);
        var resposta = get("/api/dispositivos/" + id);
        assertEquals(200, resposta.statusCode());
        var dispositivo = json.readTree(resposta.body());
        assertEquals(id, dispositivo.get("id").asString());
        assertEquals(16, dispositivo.get("canais").size());
        var contador = dispositivo.get("canais").get(4);
        assertEquals("contadorDI1", contador.get("codigo").asString());
        assertEquals("DI1", contador.get("entradaAssociada").asString());
        assertEquals(-1, contador.get("valor").asInt());
        assertEquals("DESLIGADO_OU_DESCONFIGURADO", contador.get("situacaoLeitura").asString());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM configuracao.maquina WHERE id = ?", Integer.class, id));
        assertTrue(get("/api/dispositivos").body().contains(id));
        assertEquals(404, get(rascunho(id)).statusCode());
    }

    @Test
    void salvaRascunhoPreservaIdentidadeERecuperaSemLeituraEmMemoria() throws Exception {
        String id = id();
        enviarLeitura(id);
        var criado = salvar(id, corpo(0));
        assertEquals(200, criado.statusCode());
        var primeiro = json.readTree(criado.body());
        assertEquals(1, primeiro.get("versao").asInt());
        assertEquals("RASCUNHO", primeiro.get("situacao").asString());
        String linhaId = primeiro.get("linhaId").asString();
        UUID.fromString(linhaId);
        var atualizado = corpo(1);
        atualizado.put("nomeLinha", "Linha renomeada");
        assertEquals(200, salvar(id, atualizado).statusCode());
        var retorno = json.readTree(get(rascunho(id)).body());
        assertEquals(linhaId, retorno.get("linhaId").asString());
        assertEquals("Linha renomeada", retorno.get("nomeLinha").asString());
        assertEquals(2, retorno.get("versao").asInt());
        // Nova instância, sem leituras: a configuração é recuperada do banco.
        var service = new LinhaRascunhoService(new LinhaRascunhoRepository(jdbc),
                new ConfiguracaoRepository(jdbc), new LeituraService());
        assertEquals("Linha renomeada", service.buscar(id).nomeLinha());
        assertEquals(2, service.buscar(id).versao());
        assertTrue(get("/api/linhas/rascunhos").body().contains(linhaId));
        enviarLeitura(id);
        assertEquals(retorno, json.readTree(get(rascunho(id)).body()));
    }

    @Test
    void naoAlteraConfiguracaoExistenteNemMisturaDispositivos() throws Exception {
        String antes = get("/api/equipamentos/TESTE_B/configuracoes").body();
        assertEquals(200, salvar("TESTE_B", corpo(0)).statusCode());
        assertEquals(antes, get("/api/equipamentos/TESTE_B/configuracoes").body());
        String outro = id();
        enviarLeitura(outro);
        var c = corpo(0);
        c.put("produto", "Outro produto");
        assertEquals(200, salvar(outro,c).statusCode());
        assertEquals("Produto piloto", json.readTree(get(rascunho("TESTE_B")).body()).get("produto").asString());
    }

    @Test
    void rejeitaVersaoAntigaECriacaoDuplicada() throws Exception {
        String id = id();
        enviarLeitura(id);
        assertEquals(200, salvar(id, corpo(0)).statusCode());
        assertEquals(409, salvar(id, corpo(0)).statusCode());
        assertEquals(200, salvar(id, corpo(1)).statusCode());
        assertEquals(409, salvar(id, corpo(1)).statusCode());
        assertEquals(2, json.readTree(get(rascunho(id)).body()).get("versao").asInt());
    }

    @Test
    void atualizacoesConcorrentesNaoSobrescrevemSilenciosamente() throws Exception {
        String id = id();
        enviarLeitura(id);
        assertEquals(200, salvar(id, corpo(0)).statusCode());
        var request = requisicao("PUT", rascunho(id), json.writeValueAsString(corpo(1)));
        var a = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var b = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        CompletableFuture.allOf(a,b).join();
        var statuses = new ArrayList<>(List.of(a.join().statusCode(), b.join().statusCode()));
        Collections.sort(statuses);
        assertEquals(List.of(200,409), statuses);
    }

    @Test
    void rejeitaDispositivoDesconhecido() throws Exception {
        assertEquals(404, salvar(id(), corpo(0)).statusCode());
    }

    @ParameterizedTest
    @MethodSource("invalidos")
    void dadosInvalidosNaoAlteramRascunho(String campo, Object valor) throws Exception {
        String id = id();
        enviarLeitura(id);
        assertEquals(200, salvar(id, corpo(0)).statusCode());
        String antes = get(rascunho(id)).body();
        var dados = corpo(1);
        dados.put(campo, valor);
        assertEquals(400, salvar(id, dados).statusCode());
        assertEquals(antes, get(rascunho(id)).body());
    }

    static Stream<Arguments> invalidos() {
        return Stream.of(Arguments.of("nomeLinha", " "), Arguments.of("produto", null),
                Arguments.of("produto", "x".repeat(151)), Arguments.of("nomeLinha", 2),
                Arguments.of("velocidadeIdealPpm", 0), Arguments.of("velocidadeIdealPpm", -1),
                Arguments.of("velocidadeIdealPpm", "100"), Arguments.of("velocidadeIdealPpm", null),
                Arguments.of("versao", -1), Arguments.of("versao", 1.5),
                Arguments.of("versao", "1"), Arguments.of("versao", Long.MAX_VALUE),
                Arguments.of("ativado", true));
    }

    @Test
    void camposObrigatoriosENumeroNaoFinitoSaoRejeitados() throws Exception {
        String id = id();
        enviarLeitura(id);
        for (String campo : corpo(0).keySet()) {
            var dados = corpo(0);
            dados.remove(campo);
            assertEquals(400, salvar(id, dados).statusCode());
        }
        String dados = json.writeValueAsString(corpo(0)).replace("120.5", "1e999");
        assertEquals(400, http.send(requisicao("PUT",rascunho(id),dados),HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private String id() { return "CAD-" + UUID.randomUUID(); }
    private String rascunho(String id) { return "/api/dispositivos/" + id + "/linha-rascunho"; }
    private Map<String,Object> corpo(long versao) {
        return new LinkedHashMap<>(Map.of("nomeLinha","Linha piloto","produto","Produto piloto",
                "velocidadeIdealPpm",120.5,"versao",versao));
    }
    private void enviarLeitura(String id) throws Exception {
        var dados = new LinkedHashMap<String,Object>();
        dados.put("id",id);
        for (int i=1;i<=4;i++) {
            dados.put("DI"+i,0); dados.put("DO"+i,0); dados.put("AI"+i,0); dados.put("contadorDI"+i,-1);
        }
        dados.put("timestamp","28/10/2026 17:40:07");
        assertEquals(204, http.send(requisicao("POST","/api/leituras",json.writeValueAsString(dados)),HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    private HttpRequest requisicao(String metodo, String path, String corpo) {
        return HttpRequest.newBuilder(URI.create("http://localhost:"+porta+path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type","application/json")
                .method(metodo,corpo==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(corpo)).build();
    }
    private HttpResponse<String> get(String path) throws Exception {
        return http.send(requisicao("GET",path,null),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> salvar(String id,Map<String,Object> dados) throws Exception {
        return http.send(requisicao("PUT",rascunho(id),json.writeValueAsString(dados)),HttpResponse.BodyHandlers.ofString());
    }
}
