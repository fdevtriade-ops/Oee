package br.com.telemetria.api;

import java.math.BigDecimal;
import java.util.*;
import br.com.telemetria.api.simulacao.SimulacaoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SimulacaoServiceTest {
    private final SimulacaoService service=new SimulacaoService();
    private Map<String,Object> sample(Object contador,int segundo,String sessao){
        var leitura=new LinkedHashMap<String,Object>();leitura.put("id","TRD_TESTE");leitura.put("timestamp",String.format("29/10/2026 15:00:%02d",segundo));leitura.put("contadorDI1",contador);
        return new LinkedHashMap<>(Map.of("sessao",sessao,"leitura",leitura));
    }
    private Map<String,Object> sample(Object contador,int segundo){return sample(contador,segundo,"A");}
    private Map<String,Object> request(Object fator,List<?> samples){return new LinkedHashMap<>(Map.of("canal","contadorDI1","fator",fator,"amostras",samples));}
    private SimulacaoService.Simulacao run(Map<String,Object>... samples){return service.simular(request(1,List.of(samples)));}
    private void numero(String esperado,BigDecimal atual){assertNotNull(atual);assertEquals(0,new BigDecimal(esperado).compareTo(atual));}
    @SuppressWarnings("unchecked") private Map<String,Object> reading(Map<String,Object> s){return (Map<String,Object>)s.get("leitura");}

    @Test void calculaDezPecasEmCincoSegundos(){
        var s=run(sample(1000,0),sample(1010,5));
        assertEquals("REFERENCIA_INICIAL",s.passos().get(0).situacao());assertNull(s.passos().get(0).ppm());
        numero("10",s.resumo().quantidadeCalculada());numero("120",s.passos().get(1).ppm());
        assertEquals(5,s.resumo().segundosCalculados());assertEquals("CALCULADO",s.resumo().situacao());
    }
    @Test void mediaUsaTempoAgregadoNaoMediaDePercentuaisOuPPM(){
        var s=run(sample(1000,0),sample(1010,5),sample(1025,15));
        numero("120",s.passos().get(1).ppm());numero("90",s.passos().get(2).ppm());numero("100",s.resumo().ppmMedio());numero("25",s.resumo().quantidadeCalculada());
    }
    @Test void aplicaFatorSemSomarFotosAcumuladas(){
        var s=service.simular(request(0.5,List.of(sample(1000,0),sample(1010,5))));
        numero("5",s.resumo().quantidadeCalculada());numero("60",s.resumo().ppmMedio());
    }
    @Test void distingueProducaoZeroDeAusenciaDeIntervalos(){
        var zero=run(sample(10,0),sample(10,5));numero("0",zero.resumo().quantidadeCalculada());numero("0",zero.resumo().ppmMedio());
        var sem=run(sample(-1,0),sample(-1,5));assertNull(sem.resumo().quantidadeCalculada());assertNull(sem.resumo().ppmMedio());assertEquals("SEM_INTERVALOS_VALIDOS",sem.resumo().situacao());
    }
    @Test void naoLigaSessaoAnteriorANova(){
        var s=run(sample(1000,0),sample(1010,5),sample(0,10,"B"),sample(5,15,"B"));
        assertEquals("NOVA_SESSAO",s.passos().get(2).situacao());assertNull(s.passos().get(2).quantidade());numero("15",s.resumo().quantidadeCalculada());assertEquals(10,s.resumo().segundosCalculados());assertEquals("PARCIAL",s.resumo().situacao());
    }
    @Test void contadorMenosUmExigeNovaSessaoEJamaisEntraNaConta(){
        var s=run(sample(1000,0),sample(-1,5),sample(1100,10),sample(0,15,"B"),sample(10,20,"B"));
        assertEquals("CONTADOR_DESCONFIGURADO",s.passos().get(1).situacao());assertEquals("SESSAO_INCONSISTENTE",s.passos().get(2).situacao());numero("10",s.resumo().quantidadeCalculada());
    }
    @Test void quedaNaoCriaDeltaNegativoNemInventaRecuperacao(){
        var s=run(sample(1000,0),sample(10,5),sample(20,10),sample(0,15,"B"),sample(3,20,"B"));
        assertEquals("CONTADOR_DECRESCENTE",s.passos().get(1).situacao());assertEquals("SESSAO_INCONSISTENTE",s.passos().get(2).situacao());numero("3",s.resumo().quantidadeCalculada());
    }
    @Test void duplicataNaoDuplicaProducaoNemCriaVelocidadeEntreReferenciasIncertas(){
        var s=run(sample(100,0),sample(110,5),sample(120,5),sample(130,10),sample(140,15));
        assertEquals("TIMESTAMP_REPETIDO",s.passos().get(2).situacao());assertEquals("NOVA_REFERENCIA",s.passos().get(3).situacao());numero("20",s.resumo().quantidadeCalculada());
    }
    @Test void foraDeOrdemNaoRetrocedeRelogioDeReferencia(){
        var s=run(sample(100,10),sample(110,5),sample(115,8),sample(120,15),sample(125,20));
        assertEquals("FORA_DE_ORDEM",s.passos().get(1).situacao());assertEquals("FORA_DE_ORDEM",s.passos().get(2).situacao());assertEquals("NOVA_REFERENCIA",s.passos().get(3).situacao());numero("5",s.resumo().quantidadeCalculada());
    }
    @Test void quedaAposAmostraInvalidaTambemInvalidaSessao(){
        var s=run(sample(1000,0),sample(1005,0),sample(10,5),sample(20,10));
        assertEquals("TIMESTAMP_REPETIDO",s.passos().get(1).situacao());
        assertEquals("CONTADOR_DECRESCENTE",s.passos().get(2).situacao());assertNull(s.resumo().ppmMedio());
    }
    @Test void valoresGrandesNaoPerdemUmPulso(){
        var s=run(sample(9223372036854775806L,0),sample(Long.MAX_VALUE,5));
        numero("1",s.resumo().quantidadeCalculada());assertEquals("9223372036854775807",s.passos().get(1).contador());
    }
    @Test void datasInvalidasNaoGeramIndicadores(){
        var invalida=sample(1000,5);reading(invalida).put("timestamp","31/02/2026 15:00:05");
        var s=run(sample(100,0),invalida,sample(1010,10),sample(1020,15));
        assertEquals("TIMESTAMP_INVALIDO",s.passos().get(1).situacao());assertEquals("NOVA_REFERENCIA",s.passos().get(2).situacao());numero("10",s.resumo().quantidadeCalculada());
    }
    @Test void suportaViradaDeDia(){
        var a=sample(100,0);reading(a).put("timestamp","29/10/2026 23:59:58");
        var b=sample(110,0);reading(b).put("timestamp","30/10/2026 00:00:03");numero("120",run(a,b).resumo().ppmMedio());
    }
    @Test void contagensInvalidasDescartamParAdjacente(){
        for(Object n:new Object[]{-2,1.5,"10",new java.math.BigInteger("9223372036854775808")}){
            var s=run(sample(100,0),sample(n,5),sample(110,10));assertEquals("CONTAGEM_INVALIDA",s.passos().get(1).situacao());assertNull(s.resumo().ppmMedio());
        }
    }
    @ParameterizedTest @ValueSource(doubles={0,-1,Double.NaN,Double.POSITIVE_INFINITY})
    void fatorInvalidoRejeitaCenario(double fator){assertThrows(IllegalArgumentException.class,()->service.simular(request(fator,List.of(sample(1,0),sample(2,5)))));}
    @Test void contratoRejeitaMisturaDeDispositivosOuSessaoAusente(){
        var b=sample(2,5);reading(b).put("id","OUTRA");assertThrows(IllegalArgumentException.class,()->run(sample(1,0),b));
        var a=sample(1,0);a.remove("sessao");assertThrows(IllegalArgumentException.class,()->run(a,sample(2,5)));
        assertThrows(IllegalArgumentException.class,()->service.simular(request(1,List.of(sample(1,0)))));
        assertThrows(IllegalArgumentException.class,()->service.simular(request(1,Collections.nCopies(501,sample(1,0)))));
        var d=request(1,List.of(sample(1,0),sample(2,5)));d.put("canal","DI1");assertThrows(IllegalArgumentException.class,()->service.simular(d));
    }
}
