package br.com.telemetria.api;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import br.com.telemetria.api.configuracao.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimulacaoApiTest {
    @LocalServerPort private int porta;
    @Autowired private LeituraService leituras;
    @Autowired private LinhaRascunhoService linhas;
    @Autowired private ObjectMapper json;
    @Test void simulaSemMudarTelemetriaOuCadastros()throws Exception{
        String id="SIM-"+UUID.randomUUID();
        Map<String,Object> d=new LinkedHashMap<>();d.put("id",id);d.put("timestamp","29/10/2026 10:00:00");for(int i=1;i<=4;i++){d.put("DI"+i,0);d.put("DO"+i,0);d.put("AI"+i,0);d.put("contadorDI"+i,-1);}leituras.receber(d);
        linhas.salvar(id,Map.of("nomeLinha","Linha real","produto","Produto","velocidadeIdealPpm",100,"versao",0));
        var cadastro=linhas.buscar(id);var leitura=leituras.buscar(id).orElseThrow();
        String corpo="""
                {"canal":"contadorDI1","fator":1,"amostras":[
                  {"sessao":"A","leitura":{"id":"SIMULADO","timestamp":"29/10/2026 15:00:00","contadorDI1":1000}},
                  {"sessao":"A","leitura":{"id":"SIMULADO","timestamp":"29/10/2026 15:00:05","contadorDI1":1010}}
                ]}
                """;
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+porta+"/api/simulacoes/contadores")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(corpo)).build();
        var resposta=HttpClient.newHttpClient().send(req,HttpResponse.BodyHandlers.ofString());
        assertEquals(200,resposta.statusCode());var r=json.readTree(resposta.body());assertEquals("SIMULACAO",r.get("modo").asString());assertEquals("120",r.get("resumo").get("ppmMedioTexto").asString());
        assertEquals(cadastro,linhas.buscar(id));assertEquals(leitura,leituras.buscar(id).orElseThrow());assertTrue(leituras.buscar("SIMULADO").isEmpty());
    }
    @Test void corpoInvalidoRetorna400ETelaEhServida()throws Exception{
        var http=HttpClient.newHttpClient();
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+porta+"/api/simulacoes/contadores")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertEquals(400,http.send(req,HttpResponse.BodyHandlers.ofString()).statusCode());
        for(String path:new String[]{"/simulador.html","/assets/oee-simulador.js","/assets/oee-simulador.css"})assertEquals(200,http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+porta+path)).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
