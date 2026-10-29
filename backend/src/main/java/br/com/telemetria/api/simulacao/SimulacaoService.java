package br.com.telemetria.api.simulacao;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.*;
import java.time.format.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Cálculo isolado: sem banco, relógio do servidor, telemetria compartilhada ou comandos. */
@Service
public class SimulacaoService {
    private static final DateTimeFormatter FORMATO=DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss",Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    public record Passo(int indice,String sessao,String timestamp,String contador,Long intervaloSegundos,
            Long variacaoContador,BigDecimal quantidade,String quantidadeTexto,BigDecimal ppm,String ppmTexto,
            String situacao,String detalhe) { }
    public record Resumo(int intervalosPossiveis,int intervalosValidos,long segundosCalculados,
            BigDecimal quantidadeCalculada,String quantidadeTexto,BigDecimal ppmMedio,String ppmMedioTexto,
            String situacao) { }
    public record Simulacao(String modo,String dispositivoId,String canal,BigDecimal fator,
            String premissaTemporal,Resumo resumo,List<Passo> passos) { }
    private record Amostra(String sessao,LocalDateTime horario,Long contador,boolean referenciaUtilizavel) { }

    public Simulacao simular(Map<String,Object> dados) {
        if (!dados.keySet().equals(Set.of("canal","fator","amostras")))
            throw erro("Envie exatamente canal, fator e amostras.");
        if (!(dados.get("canal") instanceof String canal) || !canal.matches("contadorDI[1-4]"))
            throw erro("canal deve ser contadorDI1, contadorDI2, contadorDI3 ou contadorDI4.");
        BigDecimal fator=decimal(dados.get("fator"));
        if (fator.signum()<=0) throw erro("fator deve ser maior que zero.");
        if (!(dados.get("amostras") instanceof List<?> amostras) || amostras.size()<2 || amostras.size()>500)
            throw erro("Envie de 2 a 500 amostras, na ordem que deseja conferir.");
        String dispositivo=null;
        List<Passo> passos=new ArrayList<>();
        Map<String,LocalDateTime> maiorHorario=new HashMap<>();
        Map<String,Long> ultimoContadorValido=new HashMap<>();
        Set<String> sessoesBloqueadas=new HashSet<>();
        Amostra anterior=null;
        BigDecimal total=BigDecimal.ZERO;
        long segundos=0;
        int validos=0;
        for (int i=0;i<amostras.size();i++) {
            if (!(amostras.get(i) instanceof Map<?,?> a) || !a.keySet().equals(Set.of("sessao","leitura")))
                throw erro("Amostra "+(i+1)+": envie sessao e leitura.");
            String sessao=texto(a.get("sessao"),"sessao",100);
            if (!(a.get("leitura") instanceof Map<?,?> leitura)) throw erro("leitura deve ser um objeto JSON.");
            String id=texto(leitura.get("id"),"id",100);
            if (dispositivo==null) dispositivo=id;
            else if (!dispositivo.equals(id)) throw erro("Todas as amostras devem pertencer ao mesmo dispositivo.");
            String timestamp=leitura.get("timestamp") instanceof String s?s:null;
            LocalDateTime horario=horario(timestamp);
            Long contador=contador(leitura.get(canal));
            String status,detalhe;
            boolean utilizavel=false;
            Long intervalo=null,variacao=null;
            BigDecimal quantidade=null,ppm=null;
            LocalDateTime maior=maiorHorario.get(sessao);
            if (horario==null) {
                status="TIMESTAMP_INVALIDO";detalhe="Data ausente ou inválida; use dd/MM/yyyy HH:mm:ss.";
            } else if (maior!=null && !horario.isAfter(maior)) {
                status=horario.equals(maior)?"TIMESTAMP_REPETIDO":"FORA_DE_ORDEM";
                detalhe="Intervalo descartado. A próxima amostra cronológica válida será apenas nova referência.";
            } else if (contador==null) {
                status="CONTAGEM_INVALIDA";detalhe="O contador deve ser -1 ou um inteiro não negativo até "+Long.MAX_VALUE+".";
            } else if (contador==-1) {
                status="CONTADOR_DESCONFIGURADO";detalhe="Sem cálculo. Declare uma nova sessão ao simular a retomada do contador.";
                sessoesBloqueadas.add(sessao);
            } else if (sessoesBloqueadas.contains(sessao)) {
                status="SESSAO_INCONSISTENTE";detalhe="Esta sessão teve contador -1 ou decrescente. A retomada requer uma nova sessão declarada.";
            } else if (ultimoContadorValido.containsKey(sessao) && contador<ultimoContadorValido.get(sessao)) {
                status="CONTADOR_DECRESCENTE";detalhe="Possível reset ou sequência inconsistente. Não cria produção negativa; declare uma nova sessão.";
                sessoesBloqueadas.add(sessao);
            } else if (anterior==null) {
                status="REFERENCIA_INICIAL";detalhe="Primeira leitura válida; ainda não há intervalo para calcular.";utilizavel=true;
            } else if (!sessao.equals(anterior.sessao())) {
                status="NOVA_SESSAO";detalhe="Nova referência. Não há cálculo entre sessões diferentes.";utilizavel=true;
            } else if (!anterior.referenciaUtilizavel()) {
                status="NOVA_REFERENCIA";detalhe="Referência restabelecida; o intervalo que atravessa a amostra inválida foi descartado.";utilizavel=true;
            } else {
                intervalo=Duration.between(anterior.horario(),horario).getSeconds();
                variacao=contador-anterior.contador();
                quantidade=BigDecimal.valueOf(variacao).multiply(fator);
                ppm=quantidade.multiply(BigDecimal.valueOf(60)).divide(BigDecimal.valueOf(intervalo),MathContext.DECIMAL128);
                status="VALIDO";detalhe="Diferença de contagem × fator; PPM = quantidade × 60 / segundos.";
                total=total.add(quantidade);segundos+=intervalo;validos++;utilizavel=true;
            }
            if (horario!=null && (maior==null || horario.isAfter(maior))) maiorHorario.put(sessao,horario);
            if (utilizavel) ultimoContadorValido.put(sessao,contador);
            passos.add(new Passo(i+1,sessao,timestamp,contador==null?null:contador.toString(),intervalo,variacao,
                    quantidade,formatar(quantidade),ppm,formatar(ppm),status,detalhe));
            anterior=new Amostra(sessao,horario,contador,utilizavel);
        }
        BigDecimal media=segundos>0?total.multiply(BigDecimal.valueOf(60)).divide(BigDecimal.valueOf(segundos),MathContext.DECIMAL128):null;
        Resumo resumo=new Resumo(amostras.size()-1,validos,segundos,validos==0?null:total,validos==0?null:formatar(total),media,formatar(media),
                validos==0?"SEM_INTERVALOS_VALIDOS":validos<amostras.size()-1?"PARCIAL":"CALCULADO");
        return new Simulacao("SIMULACAO",dispositivo,canal,fator,
                "Horários simulados em uma mesma referência de relógio por sessão; não são convertidos para UTC nem usados na coleta real.",
                resumo,List.copyOf(passos));
    }
    private LocalDateTime horario(String valor) {
        if (valor==null || !valor.matches("[0-9]{2}/[0-9]{2}/[0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2}")) return null;
        try {var t=LocalDateTime.parse(valor,FORMATO);return t.getYear()>0?t:null;} catch(DateTimeParseException e){return null;}
    }
    private Long contador(Object valor) {
        try {long c=decimal(valor).longValueExact();return c>=-1?c:null;}
        catch(IllegalArgumentException|ArithmeticException e){return null;}
    }
    private BigDecimal decimal(Object valor) {
        if (!(valor instanceof Number n) || !Double.isFinite(n.doubleValue())) throw erro("fator e contagens devem ser números finitos.");
        try{return new BigDecimal(n.toString());}catch(NumberFormatException e){throw erro("Número inválido.");}
    }
    private String texto(Object valor,String campo,int limite) {
        if (!(valor instanceof String s) || s.isBlank() || s.length()>limite) throw erro(campo+" deve conter de 1 a "+limite+" caracteres.");
        return s;
    }
    private String formatar(BigDecimal valor) {return valor==null?null:valor.stripTrailingZeros().toPlainString();}
    private IllegalArgumentException erro(String texto){return new IllegalArgumentException(texto);}
}
