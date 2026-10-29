package br.com.telemetria.api;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import br.com.telemetria.api.configuracao.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class CanalRascunhoApiTest {
    @LocalServerPort private int porta;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    private final HttpClient http=HttpClient.newHttpClient();

    @Test
    void salvaDIContadorEAIConsultaERecuperaDoBanco() throws Exception {
        String id=preparar();
        assertEquals("[]",get(path(id)).body());
        for (String codigo:List.of("DI3","contadorDI1","AI1")) {
            var r=put(id,codigo,config(codigo,0));
            assertEquals(200,r.statusCode(),r.body());
            var c=json.readTree(r.body());
            assertEquals("RASCUNHO",c.get("situacao").asString());
            assertEquals(1,c.get("versao").asInt());
            assertEquals(c,json.readTree(get(path(id)+"/"+codigo).body()));
        }
        var canais=json.readTree(get(path(id)).body());
        assertEquals(3,canais.size());
        assertEquals("DI3",canais.get(0).get("codigo").asString());
        var repo=new CanalRascunhoRepository(jdbc,json);
        assertEquals("RISING",repo.buscar(id,"contadorDI1").orElseThrow().parametros().get("borda"));
        assertEquals(3,repo.listar(id).size());
    }

    @Test
    void editarPolaridadeBordaEscalaEDesabilitarComControleDeVersao() throws Exception {
        String id=preparar();
        for (String codigo:List.of("DI3","contadorDI1","AI1")) assertEquals(200,put(id,codigo,config(codigo,0)).statusCode());
        var di=config("DI3",1); parametros(di).put("nivelAtivo",0);
        assertEquals(200,put(id,"DI3",di).statusCode());
        assertEquals(0,json.readTree(get(path(id)+"/DI3").body()).get("parametros").get("nivelAtivo").asInt());
        var count=config("contadorDI1",1); parametros(count).put("borda","FALLING"); parametros(count).put("fator",0.5);
        assertEquals(200,put(id,"contadorDI1",count).statusCode());
        var ai=config("AI1",1); parametros(ai).put("valorMin",100); parametros(ai).put("valorMax",-50);
        assertEquals(200,put(id,"AI1",ai).statusCode());
        assertEquals(409,put(id,"DI3",config("DI3",0)).statusCode());
        assertEquals(409,put(id,"DI3",config("DI3",1)).statusCode());
        var disabled=new LinkedHashMap<String,Object>();
        disabled.put("nome",null); disabled.put("habilitado",false); disabled.put("funcao",null);
        disabled.put("parametros",Map.of()); disabled.put("versao",2);
        assertEquals(200,put(id,"DI3",disabled).statusCode());
        var resultado=json.readTree(get(path(id)+"/DI3").body());
        assertFalse(resultado.get("habilitado").asBoolean());
        assertEquals(3,resultado.get("versao").asInt());
        assertTrue(resultado.get("funcao").isNull());
    }

    @Test
    void leiturasNaoSobrescrevemConfiguracaoENaoAlteramCadastroAnterior() throws Exception {
        String id=preparar();
        var velha=get("/api/equipamentos/TESTE_A/configuracoes").body();
        assertEquals(200,put(id,"contadorDI1",config("contadorDI1",0)).statusCode());
        String antes=get(path(id)).body();
        leitura(id);
        assertEquals(antes,get(path(id)).body());
        assertEquals(velha,get("/api/equipamentos/TESTE_A/configuracoes").body());
        String outro=preparar();
        assertEquals("[]",get(path(outro)).body());
        assertEquals(404,get(path(outro)+"/contadorDI1").statusCode());
    }

    @Test
    void criacoesConcorrentesEAtualizacoesConflitantesNaoDuplicamCanal() throws Exception {
        String id=preparar();
        for (int versao:List.of(0,1)) {
            var req=request("PUT",path(id)+"/contadorDI1",config("contadorDI1",versao));
            var a=http.sendAsync(req,HttpResponse.BodyHandlers.ofString());
            var b=http.sendAsync(req,HttpResponse.BodyHandlers.ofString());
            CompletableFuture.allOf(a,b).join();
            var status=new ArrayList<>(List.of(a.join().statusCode(),b.join().statusCode()));
            Collections.sort(status); assertEquals(List.of(200,409),status);
        }
        assertEquals(1,json.readTree(get(path(id)).body()).size());
        assertEquals(2,json.readTree(get(path(id)+"/contadorDI1").body()).get("versao").asInt());
    }

    @Test
    void requerRascunhoDeLinhaERecusaDOOuCanaisForaDoContrato() throws Exception {
        assertEquals(404,put("SEM-LINHA","DI1",config("DI1",0)).statusCode());
        String id=preparar();
        for (String c:List.of("DO1","DI5","contadorDI0","AI5","di1"))
            assertEquals(400,put(id,c,config("DI1",0)).statusCode());
    }

    @ParameterizedTest
    @MethodSource("invalidos")
    void rejeitaParametrosInvalidosSemAlterarValorSalvo(String codigo,String campo,Object valor,boolean parametro) throws Exception {
        String id=preparar();
        assertEquals(200,put(id,codigo,config(codigo,0)).statusCode());
        String antes=get(path(id)+"/"+codigo).body();
        var alterada=config(codigo,1);
        (parametro?parametros(alterada):alterada).put(campo,valor);
        assertEquals(400,put(id,codigo,alterada).statusCode());
        assertEquals(antes,get(path(id)+"/"+codigo).body());
    }
    static Stream<Arguments> invalidos() {
        return Stream.of(
            Arguments.of("DI1","nivelAtivo",2,true), Arguments.of("DI1","nivelAtivo","1",true),
            Arguments.of("DI1","nivelAtivo",null,true), Arguments.of("DI1","funcao","TOTAL_PRODUZIDO",false),
            Arguments.of("DI1","nome"," ",false), Arguments.of("DI1","habilitado","true",false),
            Arguments.of("DI1","versao",0.5,false), Arguments.of("DI1","versao",Long.MAX_VALUE,false),
            Arguments.of("DI1","extra",1,false), Arguments.of("DI1","parametros",null,false),
            Arguments.of("contadorDI1","fator",0,true), Arguments.of("contadorDI1","fator",-1,true),
            Arguments.of("contadorDI1","fator","1",true), Arguments.of("contadorDI1","borda","SUBIDA",true),
            Arguments.of("contadorDI1","unidade","",true), Arguments.of("contadorDI1","funcao","FALHA",false),
            Arguments.of("AI1","tensaoMin",-0.1,true), Arguments.of("AI1","tensaoMax",10.1,true),
            Arguments.of("AI1","tensaoMax",0,true), Arguments.of("AI1","valorMax",0,true),
            Arguments.of("AI1","grandeza",null,true), Arguments.of("AI1","funcao","TOTAL_PRODUZIDO",false));
    }

    @Test
    void rejeitaCamposAusentesETotalNaoFinito() throws Exception {
        String id=preparar();
        for (String codigo:List.of("DI1","contadorDI1","AI1")) {
            for (String k:config(codigo,0).keySet()) {
                var c=config(codigo,0); c.remove(k); assertEquals(400,put(id,codigo,c).statusCode());
            }
            for (String k:parametros(config(codigo,0)).keySet()) {
                var c=config(codigo,0); parametros(c).remove(k); assertEquals(400,put(id,codigo,c).statusCode());
            }
        }
        String raw=json.writeValueAsString(config("contadorDI1",0)).replace("1.0","1e999");
        var req=HttpRequest.newBuilder(URI.create(url(path(id)+"/contadorDI1"))).header("Content-Type","application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(raw)).build();
        assertEquals(400,http.send(req,HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private Map<String,Object> config(String codigo,long versao) {
        var p=new LinkedHashMap<String,Object>(); String funcao;
        if (codigo.startsWith("contador")) {
            funcao="TOTAL_PRODUZIDO"; p.put("unidade","pecas"); p.put("fator",1.0); p.put("borda","RISING");
        } else if (codigo.startsWith("AI")) {
            funcao="MEDIDA_ANALOGICA"; p.put("grandeza","Pressão"); p.put("unidade","bar");
            p.put("tensaoMin",0); p.put("tensaoMax",10); p.put("valorMin",0); p.put("valorMax",100);
        } else { funcao="MAQUINA_RODANDO"; p.put("nivelAtivo",1); }
        return new LinkedHashMap<>(Map.of("nome","Canal teste","habilitado",true,"funcao",funcao,"parametros",p,"versao",versao));
    }
    @SuppressWarnings("unchecked") private Map<String,Object> parametros(Map<String,Object> c) { return (Map<String,Object>)c.get("parametros"); }
    private String preparar() throws Exception {
        String id="CAN-"+UUID.randomUUID(); leitura(id);
        assertEquals(200,enviar("PUT","/api/dispositivos/"+id+"/linha-rascunho",
                Map.of("nomeLinha","Linha canais","produto","Produto","velocidadeIdealPpm",100,"versao",0)).statusCode());
        return id;
    }
    private void leitura(String id) throws Exception {
        var d=new LinkedHashMap<String,Object>(); d.put("id",id); d.put("timestamp","29/10/2026 12:00:00");
        for (int i=1;i<=4;i++) { d.put("DI"+i,0); d.put("DO"+i,0); d.put("AI"+i,0); d.put("contadorDI"+i,-1); }
        assertEquals(204,enviar("POST","/api/leituras",d).statusCode());
    }
    private String path(String id) { return "/api/dispositivos/"+id+"/canais-rascunho"; }
    private String url(String p) { return "http://localhost:"+porta+p; }
    private HttpRequest request(String method,String p,Object body) {
        return HttpRequest.newBuilder(URI.create(url(p))).header("Content-Type","application/json")
                .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
    }
    private HttpResponse<String> enviar(String m,String p,Object b) throws Exception { return http.send(request(m,p,b),HttpResponse.BodyHandlers.ofString()); }
    private HttpResponse<String> get(String p) throws Exception { return enviar("GET",p,null); }
    private HttpResponse<String> put(String id,String codigo,Object b) throws Exception { return enviar("PUT",path(id)+"/"+codigo,b); }
}
