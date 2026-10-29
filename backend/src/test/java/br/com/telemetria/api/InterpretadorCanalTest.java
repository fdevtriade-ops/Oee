package br.com.telemetria.api;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Stream;
import br.com.telemetria.api.previa.InterpretadorCanal;
import br.com.telemetria.api.configuracao.CanalRascunhoRepository.CanalRascunho;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class InterpretadorCanalTest {
    private final InterpretadorCanal motor=new InterpretadorCanal();
    private CanalRascunho config(String codigo,String funcao,Map<String,Object> p,boolean habilitado) {
        return new CanalRascunho("TESTE",codigo,codigo.startsWith("contador")?"CONTADOR":codigo.substring(0,2),"Nome",habilitado,funcao,p,3,"RASCUNHO","2026-10-29T17:00:00Z");
    }
    @ParameterizedTest
    @CsvSource({"1,1,true,Rodando","0,1,false,Parada","0,0,true,Rodando","1,0,false,Parada"})
    void interpretaPolaridadeSemImporNivelFixo(int bruto,int nivel,boolean ativo,String texto) {
        var r=motor.interpretar("DI3",config("DI3","MAQUINA_RODANDO",Map.of("nivelAtivo",nivel),true),bruto);
        assertEquals(ativo,r.ativo());assertEquals(ativo,r.valorInterpretado());assertEquals(texto,r.valorFormatado());
        assertEquals("VALIDO",r.situacao());assertEquals(3,r.versaoConfiguracao());
    }
    @ParameterizedTest
    @CsvSource({"MAQUINA_PARADA,0,Sem sinal de parada","MAQUINA_PARADA,1,Parada","MAQUINA_OCIOSA,0,Sem sinal de ociosidade","MAQUINA_OCIOSA,1,Ociosa","FALHA,0,Sem falha","FALHA,1,Falha ativa"})
    void descreveCadaFuncaoSemInventarRodando(String funcao,int bruto,String texto) {
        var r=motor.interpretar("DI1",config("DI1",funcao,Map.of("nivelAtivo",1),true),bruto);
        assertEquals(texto,r.valorFormatado());
    }
    private CanalRascunho analogico(double a,double b,double c,double d) {
        return config("AI1","MEDIDA_ANALOGICA",Map.of("grandeza","Pressão","unidade","bar","tensaoMin",a,"tensaoMax",b,"valorMin",c,"valorMax",d),true);
    }
    @ParameterizedTest
    @CsvSource({"0,0","3.5,35","10,100"})
    void converteTensaoComExtremosInclusivos(double bruto,String esperado) {
        var r=motor.interpretar("AI1",analogico(0,10,0,100),bruto);
        assertEquals(0,new BigDecimal(esperado).compareTo((BigDecimal)r.valorInterpretado()));
        assertEquals(esperado+" bar",r.valorFormatado());assertEquals(bruto,r.valorBruto());
    }
    @Test
    void escalaDecrescenteELeituraForaDaFaixa() {
        var r=motor.interpretar("AI1",analogico(2,8,100,-20),3.5);
        assertEquals("70 bar",r.valorFormatado());
        for(double v:new double[]{0,1.9,8.1,10}) {
            var fora=motor.interpretar("AI1",analogico(2,8,100,-20),v);
            assertEquals("FORA_DA_FAIXA",fora.situacao());assertNull(fora.valorInterpretado());
        }
    }
    @Test
    void contadorNaoEProducaoDoTurnoEPreservaPrecisao() {
        var c=config("contadorDI1","TOTAL_PRODUZIDO",Map.of("unidade","pecas","fator",0.5,"borda","RISING"),true);
        assertEquals("50 pecas",motor.interpretar("contadorDI1",c,100).valorFormatado());
        assertEquals("CONTADOR_DESCONFIGURADO",motor.interpretar("contadorDI1",c,-1).situacao());
        assertNull(motor.interpretar("contadorDI1",c,-1).valorInterpretado());
        assertEquals("0 pecas",motor.interpretar("contadorDI1",c,0).valorFormatado());
        var max=motor.interpretar("contadorDI1",c,Long.MAX_VALUE);
        assertEquals("4611686018427387903.5 pecas",max.valorFormatado());
        assertEquals("9223372036854775807",max.valorBrutoTexto());
        assertTrue(max.detalhe().contains("Não representa produção do turno"));
    }
    @Test
    void ausenciasDesabilitacaoESaidasNaoSeTornamZero() {
        var c=analogico(0,10,0,100);
        assertEquals("SEM_LEITURA",motor.interpretar("AI1",c,null).situacao());
        assertNull(motor.interpretar("AI1",c,null).valorInterpretado());
        assertEquals("SEM_CONFIGURACAO",motor.interpretar("DI1",null,0).situacao());
        assertEquals("DESABILITADO",motor.interpretar("DI1",config("DI1",null,Map.of(),false),1).situacao());
        assertEquals("SOMENTE_LEITURA",motor.interpretar("DO1",null,1).situacao());
    }
    @Test
    void configuracaoDanificadaOuNumeroInvalidoNaoDerrubaPrevia() {
        assertEquals("CONFIGURACAO_INVALIDA",motor.interpretar("AI1",analogico(2,2,0,100),3).situacao());
        assertEquals("CONFIGURACAO_INVALIDA",motor.interpretar("DI1",config("DI1","INVALIDA",Map.of(),true),0).situacao());
        assertEquals("LEITURA_INVALIDA",motor.interpretar("AI1",analogico(0,10,0,100),Double.NaN).situacao());
        assertEquals("LEITURA_INVALIDA",motor.interpretar("DI1",config("DI1","FALHA",Map.of("nivelAtivo",1),true),2).situacao());
        var c=config("contadorDI1","TOTAL_PRODUZIDO",Map.of("unidade","pecas","fator",1,"borda","FALLING"),true);
        for(Object bruto:new Object[]{-2,1.5,"10"}) assertEquals("LEITURA_INVALIDA",motor.interpretar("contadorDI1",c,bruto).situacao());
    }
}
