package br.com.telemetria.api;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.sql.init.schema-locations=classpath:schema-test.sql")
class CadastroSemMigracaoTest {
    @LocalServerPort private int porta;

    @Test
    void ausenciaDaTabelaNovaNaoInterrompeAPIExistente() throws Exception {
        var http = HttpClient.newHttpClient();
        for (String path : new String[]{"/api/equipamentos", "/api/dispositivos"}) {
            var r = http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+porta+path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200,r.statusCode());
        }
        var r = http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+porta+"/api/linhas/rascunhos")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(503,r.statusCode());
        assertTrue(r.body().contains("001_linha_rascunho.sql"));
        var canais = http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+porta+"/api/dispositivos/TESTE_A/canais-rascunho")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(503,canais.statusCode());
        assertTrue(canais.body().contains("002_canal_rascunho.sql"));
    }
}
