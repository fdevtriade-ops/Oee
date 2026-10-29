package br.com.telemetria.api;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import br.com.telemetria.api.previa.*;
import br.com.telemetria.api.configuracao.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class PreviaApiTest {
    @LocalServerPort private int porta;
    @Autowired private LeituraService leituras;
    @Autowired private LinhaRascunhoService linhas;
    @Autowired private CanalRascunhoService canais;
    @Autowired private PreviaService previa;
    @Autowired private ObjectMapper json;
    private String preparar() {
        String id="PREVIA-"+UUID.randomUUID();
        Map<String,Object> d=new LinkedHashMap<>();d.put("id",id);d.put("timestamp","29/10/2026 14:30:00");
        for(int i=1;i<=4;i++){d.put("DI"+i,0);d.put("DO"+i,0);d.put("AI"+i,3.5);d.put("contadorDI"+i,-1);}
        d.put("DI3",1);d.put("contadorDI1",100);leituras.receber(d);
        linhas.salvar(id,Map.of("nomeLinha","Linha prévia","produto","Produto","velocidadeIdealPpm",100,"versao",0));
        canais.salvar(id,"DI3",Map.of("nome","Estado","habilitado",true,"funcao","MAQUINA_RODANDO","parametros",Map.of("nivelAtivo",1),"versao",0));
        canais.salvar(id,"contadorDI1",Map.of("nome","Total","habilitado",true,"funcao","TOTAL_PRODUZIDO","parametros",Map.of("unidade","pecas","fator",1,"borda","RISING"),"versao",0));
        canais.salvar(id,"AI1",Map.of("nome","Pressão","habilitado",true,"funcao","MEDIDA_ANALOGICA","parametros",Map.of("grandeza","Pressão","unidade","bar","tensaoMin",0,"tensaoMax",10,"valorMin",0,"valorMax",100),"versao",0));
        return id;
    }
    @Test
    void rotaRetornaConfiguracaoSalvaEAmostraSemAlterarDados() throws Exception {
        String id=preparar(); var antes=canais.listar(id);var bruto=leituras.buscar(id).orElseThrow();
        var r=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+porta+"/api/dispositivos/"+id+"/previa")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,r.statusCode());var data=json.readTree(r.body());assertEquals(16,data.get("canais").size());
        assertEquals("RASCUNHO",data.get("situacao").asString());assertEquals(bruto.get("recebidoEm"),data.get("recebidoEm").asString());
        var porCodigo=new HashMap<String,String>();for(var c:data.get("canais")) porCodigo.put(c.get("codigo").asString(),c.get("valorFormatado").asString());
        assertEquals("Rodando",porCodigo.get("DI3"));assertEquals("100 pecas",porCodigo.get("contadorDI1"));assertEquals("35 bar",porCodigo.get("AI1"));
        assertEquals(antes,canais.listar(id));assertEquals(bruto,leituras.buscar(id).orElseThrow());
    }
    @Test
    void aposReinicioSemAmostraMantemConfiguracaoMasNaoInventaLeitura() {
        String id=preparar();
        var nova=new PreviaService(new LeituraService(),canais,new InterpretadorCanal()).consultar(id);
        assertNull(nova.timestamp());assertNull(nova.recebidoEm());
        assertEquals("SEM_LEITURA",nova.canais().stream().filter(c->c.codigo().equals("AI1")).findFirst().orElseThrow().situacao());
        assertTrue(nova.canais().stream().allMatch(c->c.valorInterpretado()==null));
    }
    @Test
    void novaConfiguracaoEAplicadaSomenteAposSalvar() {
        String id=preparar();
        canais.salvar(id,"DI3",Map.of("nome","Estado","habilitado",true,"funcao","MAQUINA_RODANDO","parametros",Map.of("nivelAtivo",0),"versao",1));
        var r=previa.consultar(id).canais().stream().filter(c->c.codigo().equals("DI3")).findFirst().orElseThrow();
        assertEquals("Parada",r.valorFormatado());assertEquals(2,r.versaoConfiguracao());
    }
    @Test
    void dispositivoSemRascunhoRetorna404() throws Exception {
        var r=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+porta+"/api/dispositivos/SEM-RASCUNHO/previa")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(404,r.statusCode());
    }
}
