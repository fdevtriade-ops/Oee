package br.com.telemetria.api.configuracao;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static br.com.telemetria.api.configuracao.CanalRascunhoRepository.CanalRascunho;

@Service
public class CanalRascunhoService {
    private final CanalRascunhoRepository repository;
    public CanalRascunhoService(CanalRascunhoRepository repository) { this.repository=repository; }

    @Transactional(readOnly=true)
    public List<CanalRascunho> listar(String id) {
        conferirSchema(); conferirLinha(id,false); return repository.listar(id);
    }
    @Transactional(readOnly=true)
    public CanalRascunho buscar(String id,String codigo) {
        tipo(codigo); conferirSchema(); conferirLinha(id,false);
        return repository.buscar(id,codigo).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,"Canal ainda sem rascunho salvo."));
    }
    @Transactional
    public CanalRascunho salvar(String id,String codigo,Map<String,Object> dados) {
        String tipo=tipo(codigo);
        campos(dados,Set.of("nome","habilitado","funcao","parametros","versao"));
        long versao=versao(dados.get("versao"));
        if (!(dados.get("habilitado") instanceof Boolean habilitado)) throw erro("habilitado deve ser true ou false.");
        if (!(dados.get("parametros") instanceof Map<?,?> raw)) throw erro("parametros deve ser um objeto JSON.");
        Map<String,Object> p=new LinkedHashMap<>();
        for (var e:raw.entrySet()) p.put((String)e.getKey(),e.getValue());
        String nome=dados.get("nome")==null ? null : texto(dados.get("nome"),"nome",150);
        String funcao=dados.get("funcao")==null ? null : texto(dados.get("funcao"),"funcao",40);
        // Desabilitado pode ficar totalmente sem função/parâmetros ou preservar uma configuração válida.
        boolean semUso=!habilitado && funcao==null && p.isEmpty();
        if (!semUso) {
            if (nome==null) throw erro("Informe nome para um canal configurado.");
            if (tipo.equals("DI")) {
                if (funcao==null || !Set.of("MAQUINA_RODANDO","MAQUINA_PARADA","MAQUINA_OCIOSA","FALHA").contains(funcao))
                    throw erro("Função DI inválida: use MAQUINA_RODANDO, MAQUINA_PARADA, MAQUINA_OCIOSA ou FALHA.");
                campos(p,Set.of("nivelAtivo"));
                double ativo=numero(p.get("nivelAtivo"),"nivelAtivo");
                if (ativo!=0 && ativo!=1) throw erro("nivelAtivo deve ser 0 ou 1.");
                p.put("nivelAtivo",(int)ativo);
            } else if (tipo.equals("CONTADOR")) {
                if (funcao==null || !Set.of("TOTAL_PRODUZIDO","PRODUTOS_BONS","PRODUTOS_REJEITADOS").contains(funcao))
                    throw erro("Função de contador inválida: use TOTAL_PRODUZIDO, PRODUTOS_BONS ou PRODUTOS_REJEITADOS.");
                campos(p,Set.of("unidade","fator","borda"));
                p.put("unidade",texto(p.get("unidade"),"unidade",30));
                double fator=numero(p.get("fator"),"fator");
                if (fator<=0) throw erro("fator deve ser maior que zero.");
                String borda=texto(p.get("borda"),"borda",10);
                if (!Set.of("RISING","FALLING").contains(borda)) throw erro("borda deve ser RISING ou FALLING.");
                p.put("fator",fator); p.put("borda",borda);
            } else {
                if (!"MEDIDA_ANALOGICA".equals(funcao)) throw erro("Use MEDIDA_ANALOGICA para a AI nesta etapa.");
                campos(p,Set.of("grandeza","unidade","tensaoMin","tensaoMax","valorMin","valorMax"));
                p.put("grandeza",texto(p.get("grandeza"),"grandeza",100));
                p.put("unidade",texto(p.get("unidade"),"unidade",30));
                double min=numero(p.get("tensaoMin"),"tensaoMin");
                double max=numero(p.get("tensaoMax"),"tensaoMax");
                double vmin=numero(p.get("valorMin"),"valorMin");
                double vmax=numero(p.get("valorMax"),"valorMax");
                if (min<0 || max>10 || min>=max) throw erro("A faixa elétrica deve estar entre 0 e 10 V, com tensaoMin menor que tensaoMax.");
                if (vmin==vmax || !Double.isFinite(vmax-vmin)) throw erro("A escala deve ter extremos distintos e diferença finita.");
                p.put("tensaoMin",min); p.put("tensaoMax",max); p.put("valorMin",vmin); p.put("valorMax",vmax);
            }
        }
        Map<String,Object> config=new LinkedHashMap<>();
        config.put("nome",nome); config.put("habilitado",habilitado); config.put("funcao",funcao); config.put("parametros",p);
        conferirSchema();
        // Serializa também a primeira criação, que ainda não possui uma linha de canal para bloquear.
        conferirLinha(id,true);
        if (versao==0) {
            if (repository.buscar(id,codigo).isPresent()) throw conflito();
            repository.criar(id,codigo,config);
        } else if (!repository.atualizar(id,codigo,config,versao)) throw conflito();
        return repository.buscar(id,codigo).orElseThrow();
    }
    private String tipo(String codigo) {
        if (codigo.matches("DI[1-4]")) return "DI";
        if (codigo.matches("contadorDI[1-4]")) return "CONTADOR";
        if (codigo.matches("AI[1-4]")) return "AI";
        throw erro("Use DI1–DI4, contadorDI1–contadorDI4 ou AI1–AI4. DO permanece apenas na telemetria nesta etapa.");
    }
    private void conferirSchema() {
        if (!repository.schemaDisponivel()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Execute banco/001_linha_rascunho.sql e banco/002_canal_rascunho.sql no PostgreSQL.");
    }
    private void conferirLinha(String id,boolean bloquear) {
        if (!(bloquear ? repository.bloquearLinha(id) : repository.existeLinha(id)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cadastre primeiro o rascunho da linha para este dispositivo.");
    }
    private void campos(Map<String,Object> m,Set<String> campos) {
        if (!m.keySet().equals(campos)) throw erro("Envie exatamente os campos: "+new TreeSet<>(campos));
    }
    private String texto(Object v,String campo,int limite) {
        if (!(v instanceof String s) || s.isBlank() || s.length()>limite) throw erro(campo+" deve ser texto não vazio de até "+limite+" caracteres.");
        return s.trim();
    }
    private double numero(Object v,String campo) {
        if (!(v instanceof Number n) || !Double.isFinite(n.doubleValue())) throw erro(campo+" deve ser um número finito.");
        return n.doubleValue();
    }
    private long versao(Object v) {
        if (v instanceof Number n) {
            try { long x=new BigDecimal(n.toString()).longValueExact(); if (x>=0 && x<Long.MAX_VALUE) return x; }
            catch (ArithmeticException | NumberFormatException e) { /* validação abaixo */ }
        }
        throw erro("versao deve ser um inteiro não negativo; use 0 para criar.");
    }
    private IllegalArgumentException erro(String msg) { return new IllegalArgumentException(msg); }
    private ResponseStatusException conflito() { return new ResponseStatusException(HttpStatus.CONFLICT,
            "O canal já existe ou foi alterado. Consulte e envie sua versao atual."); }
}
