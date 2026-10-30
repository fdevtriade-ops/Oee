'use strict';
(() => {
  const $ = id => document.getElementById(id);
  let conferencia=null, busy=false, generation=0;
  const fontes=()=>Object.fromEntries(['estado','total','qualidade'].map(k=>[k,$(k).value]));
  const path=()=>`/api/dispositivos/${encodeURIComponent($('dispositivo').value)}/configuracao`;
  async function api(url,body) {
    const response=await fetch(url,{method:body?'POST':'GET',cache:'no-store',
      headers:body?{'Content-Type':'application/json',Accept:'application/json'}:{Accept:'application/json'},
      ...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(10000)});
    const data=await response.json();
    if(!response.ok) throw Object.assign(new Error(data.erro || 'Não foi possível consultar a configuração.'),{status:response.status});
    return data;
  }
  function show(text,error=false) {
    $('resultado').textContent=text;
    $('resultado').hidden=!text;
    $('resultado').classList.toggle('error',error);
  }
  function lock(value) {
    busy=value; $('conclusao-fields').disabled=value;
    $('concluir').disabled=value || !conferencia?.podeConcluir;
  }
  function invalidate() { conferencia=null; $('concluir').disabled=true; show(''); }
  function options(id,lista) {
    $(id).replaceChildren(new Option('Selecione um canal',''),...lista.map(c=>
      new Option(`${c.codigo} · ${c.nome} · ${c.funcao}${c.parametros.unidade?' · '+c.parametros.unidade:''}`,c.codigo)));
  }
  async function load() {
    invalidate(); const g=++generation;
    $('identificacao').textContent=''; $('revisao').textContent='';
    for(const id of ['estado','total','qualidade']) options(id,[]);
    if(!$('dispositivo').value) return;
    lock(true);
    try {
      const id=$('dispositivo').value;
      const canais=await api(`/api/dispositivos/${encodeURIComponent(id)}/canais-rascunho`);
      if(g!==generation) return;
      const habilitados=canais.filter(c=>c.habilitado);
      options('estado',habilitados.filter(c=>c.tipo==='DI' && c.funcao==='MAQUINA_RODANDO'));
      options('total',habilitados.filter(c=>c.tipo==='CONTADOR' && c.funcao==='TOTAL_PRODUZIDO'));
      options('qualidade',habilitados.filter(c=>c.tipo==='CONTADOR' && ['PRODUTOS_BONS','PRODUTOS_REJEITADOS'].includes(c.funcao)));
      try {
        const atual=await api(`${path()}/concluida`);
        if(g!==generation) return;
        $('revisao').textContent=`Revisão ${atual.revisao} · ${atual.situacao==='RASCUNHO_ALTERADO'?'O rascunho mudou após a conclusão':'Configuração concluída'} · ${new Date(atual.concluidoEm).toLocaleString('pt-BR')}`;
      } catch(e) { if(e.status!==404) throw e; }
    } catch(e) { show(e.message,true); }
    finally { if(g===generation) lock(false); }
  }
  $('dispositivo').addEventListener('change',load);
  for(const id of ['estado','total','qualidade']) $(id).addEventListener('change',invalidate);
  $('conclusao-form').addEventListener('submit',async e=>{
    e.preventDefault(); if(busy || !$('conclusao-form').reportValidity()) return;
    invalidate(); lock(true);
    try {
      conferencia=await api(`${path()}/conferir`,{fontes:fontes()});
      show(conferencia.podeConcluir?`Configuração válida. Linha versão ${conferencia.versaoLinha}. Confira os canais escolhidos e clique em Concluir configuração.`:conferencia.pendencias.join(' '),!conferencia.podeConcluir);
    } catch(e) { show(e.message,true); }
    finally { lock(false); }
  });
  $('concluir').addEventListener('click',async()=>{
    if(busy || !conferencia?.podeConcluir) return;
    const c=conferencia; lock(true);
    try {
      const atual=await api(`${path()}/concluir`,{fontes:c.fontes,versaoLinha:c.versaoLinha,versoesCanais:c.versoesCanais,revisaoAtual:c.revisaoAtual});
      conferencia=null;
      show(`Configuração concluída. Revisão ${atual.revisao} salva.`);
      $('revisao').textContent=`Revisão ${atual.revisao} · ${new Date(atual.concluidoEm).toLocaleString('pt-BR')}`;
    } catch(e) { conferencia=null; show(`${e.message} Confira novamente antes de concluir.`,true); }
    finally { lock(false); }
  });
  (async()=>{
    lock(true);
    try {
      const linhas=await api('/api/linhas/rascunhos');
      for(const linha of linhas) $('dispositivo').add(new Option(`${linha.nomeLinha} · ${linha.dispositivoId}`,linha.dispositivoId));
      if(!linhas.length) show('Cadastre uma linha no configurador antes de concluir.');
    } catch(e) { show(e.message,true); }
    finally { lock(false); }
  })();
})();
