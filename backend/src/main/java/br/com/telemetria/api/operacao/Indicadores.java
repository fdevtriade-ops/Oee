package br.com.telemetria.api.operacao;

import java.math.*;

/** Razões entre 0 e 1; null representa divisor sem base, nunca zero inventado. */
public final class Indicadores {
    private Indicadores() { }
    public static final MathContext PRECISAO=MathContext.DECIMAL128;
    public record Resultado(long intervalosValidos,BigDecimal planejadoSegundos,BigDecimal rodandoSegundos,
            BigDecimal quantidade,BigDecimal boas,BigDecimal ppm,BigDecimal disponibilidade,
            BigDecimal performanceSemLimite,BigDecimal performance,BigDecimal qualidade,BigDecimal oee,
            String quantidadeTexto,String boasTexto,String ppmTexto) { }
    public static Resultado calcular(long validos,BigDecimal t,BigDecimal r,BigDecimal n,BigDecimal g,BigDecimal capacidade) {
        if(validos==0) return new Resultado(0,t,r,null,null,null,null,null,null,null,null,null,null,null);
        BigDecimal d=dividir(r,t), pBruta=dividir(n,capacidade), q=dividir(g,n);
        BigDecimal p=pBruta==null?null:pBruta.min(BigDecimal.ONE);
        BigDecimal oee=d==null || p==null || q==null?null:d.multiply(p,PRECISAO).multiply(q,PRECISAO);
        BigDecimal ppm=dividir(n.multiply(BigDecimal.valueOf(60)),t);
        return new Resultado(validos,t,r,n,g,ppm,d,pBruta,p,q,oee,n.toPlainString(),g.toPlainString(),ppm==null?null:ppm.toPlainString());
    }
    private static BigDecimal dividir(BigDecimal a,BigDecimal b) {
        return b.signum()==0?null:a.divide(b,PRECISAO);
    }
}
