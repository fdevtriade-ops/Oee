package br.com.telemetria.api.operacao;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Toda decisão temporal é explícita na ativação. Não contém calendário padrão. */
public record PoliticaOperacional(ZoneId fuso,long lacunaMaximaSegundos,long atrasoMaximoSegundos,
        long adiantamentoMaximoSegundos,long microparadaLimiteSegundos,boolean microparadaLimiteInclusivo,
        List<Turno> turnos) {
    public record Turno(String nome, LocalTime inicio, LocalTime fim) { }
    public record Periodo(String nome, Instant inicio) { }
    public static PoliticaOperacional ler(Map<String,Object> m) {
        campos(m,Set.of("fuso","lacunaMaximaSegundos","atrasoMaximoSegundos","adiantamentoMaximoSegundos",
                "microparadaLimiteSegundos","microparadaLimiteInclusivo","turnos","estadoNoIntervalo",
                "foraDeOrdem","lacunas","fronteiras","programadas","paradaInterrompida"));
        igual(m,"estadoNoIntervalo","AMOSTRA_ANTERIOR");
        igual(m,"foraDeOrdem","IGNORAR"); igual(m,"lacunas","DESCARTAR_E_REINICIAR_REFERENCIA");
        igual(m,"fronteiras","DESCARTAR_INTERVALO"); igual(m,"programadas","EXCLUIR_DO_PLANEJADO");
        igual(m,"paradaInterrompida","DESCARTAR_PENDENTES");
        ZoneId zona;
        try { zona=ZoneId.of(texto(m.get("fuso"),100)); }
        catch(DateTimeException e) { throw erro("Fuso inválido."); }
        long gap=inteiro(m.get("lacunaMaximaSegundos"));
        if(gap==0 || gap>86400) throw erro("lacunaMaximaSegundos deve estar entre 1 e 86400.");
        if(!(m.get("microparadaLimiteInclusivo") instanceof Boolean inclusivo)) throw erro("Informe microparadaLimiteInclusivo.");
        if(inteiro(m.get("microparadaLimiteSegundos"))>3600) throw erro("O limite de microparada suportado é de até 3600 segundos.");
        if(!(m.get("turnos") instanceof List<?> lista) || lista.isEmpty() || lista.size()>24) throw erro("Informe de 1 a 24 turnos.");
        List<Turno> turnos=new ArrayList<>();
        boolean[] minutos=new boolean[1440];
        Set<String> nomes=new HashSet<>();
        for(Object valor:lista) {
            if(!(valor instanceof Map<?,?> t) || !t.keySet().equals(Set.of("nome","inicio","fim"))) throw erro("Turno exige nome, inicio e fim.");
            String nome=texto(t.get("nome"),100);
            if(!nomes.add(nome)) throw erro("Os nomes dos turnos devem ser distintos.");
            LocalTime inicio=hora(t.get("inicio")), fim=hora(t.get("fim"));
            int a=inicio.getHour()*60+inicio.getMinute(), b=fim.getHour()*60+fim.getMinute();
            int duracao=Math.floorMod(b-a,1440); if(duracao==0) duracao=1440;
            for(int j=0;j<duracao;j++) {
                int minuto=(a+j)%1440;
                if(minutos[minuto]) throw erro("Os turnos não podem se sobrepor.");
                minutos[minuto]=true;
            }
            turnos.add(new Turno(nome,inicio,fim));
        }
        return new PoliticaOperacional(zona,gap,inteiro(m.get("atrasoMaximoSegundos")),
                inteiro(m.get("adiantamentoMaximoSegundos")),inteiro(m.get("microparadaLimiteSegundos")),inclusivo,List.copyOf(turnos));
    }
    public Periodo periodo(Instant instante) {
        var local=instante.atZone(fuso);
        for(var t:turnos) {
            for(int dia=0;dia>=-1;dia--) {
                var data=local.toLocalDate().plusDays(dia);
                var inicio=data.atTime(t.inicio()).atZone(fuso).toInstant();
                var fim=(t.fim().isAfter(t.inicio())?data:data.plusDays(1)).atTime(t.fim()).atZone(fuso).toInstant();
                if(!instante.isBefore(inicio) && instante.isBefore(fim)) return new Periodo(t.nome(),inicio);
            }
        }
        return null;
    }
    public Instant horario(String texto) {
        var local=LocalDateTime.parse(texto,java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss")
                .withResolverStyle(java.time.format.ResolverStyle.STRICT));
        var offsets=fuso.getRules().getValidOffsets(local);
        if(offsets.size()!=1) throw erro("Horário da placa inexistente ou ambíguo no fuso informado.");
        return local.toInstant(offsets.get(0));
    }
    public boolean micro(long segundos) {
        return microparadaLimiteInclusivo ? segundos<=microparadaLimiteSegundos : segundos<microparadaLimiteSegundos;
    }
    public static long inteiro(Object valor) {
        if(valor instanceof Number n) try {
            long x=new BigDecimal(n.toString()).longValueExact();
            if(x>=0 && x<Long.MAX_VALUE) return x;
        } catch(ArithmeticException | NumberFormatException ignored) { }
        throw erro("Informe um inteiro não negativo dentro do limite Long.");
    }
    public static String texto(Object valor,int max) {
        if(!(valor instanceof String s) || s.isBlank() || s.length()>max) throw erro("Informe texto de 1 a "+max+" caracteres.");
        return s.trim();
    }
    private static LocalTime hora(Object v) {
        String s=texto(v,5);
        if(!s.matches("[0-2][0-9]:[0-5][0-9]")) throw erro("Informe os horários como HH:mm.");
        try { return LocalTime.parse(s); } catch(DateTimeException e) { throw erro("Horário de turno inválido."); }
    }
    private static void igual(Map<String,Object> m,String chave,String valor) {
        if(!valor.equals(m.get(chave))) throw erro(chave+": esta versão suporta "+valor+"; informe a escolha explicitamente.");
    }
    public static void campos(Map<String,Object> m,Set<String> campos) {
        if(!m.keySet().equals(campos)) throw erro("Envie exatamente "+new TreeSet<>(campos));
    }
    private static IllegalArgumentException erro(String texto) { return new IllegalArgumentException(texto); }
}
