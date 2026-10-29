package br.com.telemetria.api.previa;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import org.springframework.stereotype.Component;
import br.com.telemetria.api.configuracao.CanalRascunhoRepository.CanalRascunho;

@Component
public class InterpretadorCanal {
    public record Interpretacao(String codigo, String tipo, String nome, String funcao,
            Long versaoConfiguracao, Object valorBruto, String valorBrutoTexto,
            Object valorInterpretado, String valorFormatado, Boolean ativo,
            String situacao, String detalhe) { }

    public Interpretacao interpretar(String codigo, CanalRascunho config, Object bruto) {
        String tipo=codigo.startsWith("contador")?"CONTADOR":codigo.substring(0,2);
        if (tipo.equals("DO")) return resultado(codigo,tipo,config,bruto,null,"—",null,
                "SOMENTE_LEITURA","Uso funcional da saída ainda não definido.");
        if (config==null) return resultado(codigo,tipo,null,bruto,null,"—",null,
                "SEM_CONFIGURACAO","Salve a função e os parâmetros deste canal.");
        if (!config.habilitado()) return resultado(codigo,tipo,config,bruto,null,"—",null,
                "DESABILITADO","Canal sem uso no rascunho salvo.");
        if (bruto==null) return resultado(codigo,tipo,config,null,null,"—",null,
                "SEM_LEITURA","Aguarde uma leitura da placa nesta execução da API.");
        BigDecimal valor;
        try { valor=decimal(bruto); }
        catch (IllegalArgumentException e) { return resultado(codigo,tipo,config,bruto,null,"—",null,
                "LEITURA_INVALIDA","A leitura não é um número válido."); }
        try {
            Map<String,Object> p=config.parametros();
            if (p==null) throw new IllegalArgumentException();
            if (tipo.equals("DI")) {
                if (!(valor.compareTo(BigDecimal.ZERO)==0 || valor.compareTo(BigDecimal.ONE)==0))
                    return resultado(codigo,tipo,config,bruto,null,"—",null,"LEITURA_INVALIDA","Estado digital deve ser 0 ou 1.");
                BigDecimal nivel=decimal(p.get("nivelAtivo"));
                if (!(nivel.compareTo(BigDecimal.ZERO)==0 || nivel.compareTo(BigDecimal.ONE)==0)) throw new IllegalArgumentException();
                boolean ativo=valor.compareTo(nivel)==0;
                String texto=switch(config.funcao()) {
                    case "MAQUINA_RODANDO" -> ativo?"Rodando":"Parada";
                    case "MAQUINA_PARADA" -> ativo?"Parada":"Sem sinal de parada";
                    case "MAQUINA_OCIOSA" -> ativo?"Ociosa":"Sem sinal de ociosidade";
                    case "FALHA" -> ativo?"Falha ativa":"Sem falha";
                    default -> throw new IllegalArgumentException();
                };
                return resultado(codigo,tipo,config,bruto,ativo,texto,ativo,"VALIDO","Estado da função conforme a polaridade salva.");
            }
            if (tipo.equals("CONTADOR")) {
                if (!java.util.Set.of("TOTAL_PRODUZIDO","PRODUTOS_BONS","PRODUTOS_REJEITADOS").contains(config.funcao())) throw new IllegalArgumentException();
                if (valor.compareTo(BigDecimal.valueOf(-1))==0)
                    return resultado(codigo,tipo,config,bruto,null,"—",null,"CONTADOR_DESCONFIGURADO","Contador desligado ou desconfigurado. -1 não representa produção negativa.");
                if (valor.signum()<0 || valor.stripTrailingZeros().scale()>0 || valor.compareTo(BigDecimal.valueOf(Long.MAX_VALUE))>0)
                    return resultado(codigo,tipo,config,bruto,null,"—",null,"LEITURA_INVALIDA","Contagem acumulada inválida.");
                BigDecimal fator=decimal(p.get("fator"));
                if (fator.signum()<=0 || !java.util.Set.of("RISING","FALLING").contains(texto(p,"borda"))) throw new IllegalArgumentException();
                String unidade=texto(p,"unidade");
                // Exibe o valor desta amostra com o fator. Não é delta, produção do turno ou acumulado entre reinícios.
                BigDecimal convertido=valor.multiply(fator);
                return resultado(codigo,tipo,config,bruto,convertido,formatar(convertido)+" "+unidade,null,"VALIDO",
                        "Contador da amostra × fator. Não representa produção do turno nem PPM; a borda está apenas cadastrada.");
            }
            if (!"MEDIDA_ANALOGICA".equals(config.funcao())) throw new IllegalArgumentException();
            BigDecimal min=decimal(p.get("tensaoMin")), max=decimal(p.get("tensaoMax"));
            BigDecimal vmin=decimal(p.get("valorMin")), vmax=decimal(p.get("valorMax"));
            String unidade=texto(p,"unidade"); texto(p,"grandeza");
            if (min.signum()<0 || max.compareTo(BigDecimal.TEN)>0 || min.compareTo(max)>=0 || vmin.compareTo(vmax)==0) throw new IllegalArgumentException();
            if (valor.compareTo(min)<0 || valor.compareTo(max)>0)
                return resultado(codigo,tipo,config,bruto,null,"—",null,"FORA_DA_FAIXA","Leitura fora da faixa elétrica configurada. A prévia não extrapola a escala.");
            BigDecimal convertido=vmin.add(valor.subtract(min).multiply(vmax.subtract(vmin))
                    .divide(max.subtract(min),MathContext.DECIMAL128));
            return resultado(codigo,tipo,config,bruto,convertido,formatar(convertido)+" "+unidade,null,"VALIDO",
                    "Conversão linear pela escala salva; a tensão original permanece preservada.");
        } catch (IllegalArgumentException | ArithmeticException | NullPointerException e) {
            return resultado(codigo,tipo,config,bruto,null,"—",null,"CONFIGURACAO_INVALIDA","Revise a função e os parâmetros salvos deste canal.");
        }
    }
    private BigDecimal decimal(Object valor) {
        if (!(valor instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException();
        return new BigDecimal(n.toString());
    }
    private String texto(Map<String,Object> p,String campo) {
        if (!(p.get(campo) instanceof String s) || s.isBlank()) throw new IllegalArgumentException();
        return s;
    }
    private String formatar(BigDecimal n) { return n.stripTrailingZeros().toPlainString(); }
    private Interpretacao resultado(String codigo,String tipo,CanalRascunho c,Object bruto,Object valor,
            String formatado,Boolean ativo,String situacao,String detalhe) {
        return new Interpretacao(codigo,tipo,c==null?null:c.nome(),c==null?null:c.funcao(),
                c==null?null:c.versao(),bruto,bruto==null?null:bruto.toString(),valor,formatado,ativo,situacao,detalhe);
    }
}
