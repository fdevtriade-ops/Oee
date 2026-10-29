package br.com.telemetria.api;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfiguradorWebTest {
    @LocalServerPort private int porta;
    @Test
    void serveInterfaceERecursosNaMesmaAplicacaoDaAPI() throws Exception {
        var http=HttpClient.newHttpClient();
        var index=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+porta+"/")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,index.statusCode());
        assertTrue(index.headers().firstValue("Content-Type").orElse("").contains("text/html"));
        assertTrue(index.body().contains("lang=\"pt-BR\""));
        for(String path:new String[]{"/assets/oee-config.js","/assets/oee-config.css","/api/dispositivos"}) {
            var response=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+porta+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,response.statusCode(),path);
            assertFalse(response.body().isBlank());
        }
    }
}
