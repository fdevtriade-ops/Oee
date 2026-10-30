'use strict';
(() => {
  const $=id=>document.getElementById(id);
  const state={busy:false,operation:null,conclusion:null,group:'MINUTO',generation:0};
  const escape=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const number=n=>{
    if(n===null || n===undefined)return '—';
    const s=String(n),parts=s.split('.');
    if(/^-?\d+(?:\.\d+)?$/.test(s) && parts[0].replace('-','').length>15) {
      const decimal=(parts[1] || '').slice(0,2).replace(/0+$/,'');
      return BigInt(parts[0]).toLocaleString('pt-BR')+(decimal?','+decimal:'');
    }
    return Number(n).toLocaleString('pt-BR',{maximumFractionDigits:2});
  };
  const percent=n=>n===null || n===undefined?'—':`${number(Number(n)*100)}%`;
  const path=()=>`/api/dispositivos/${encodeURIComponent($('device').value)}/operacao`;
  const board=()=>`/api/simulacoes/placas/${encodeURIComponent($('device').value)}`;
  const local=()=>/^LOCAL_/.test($('device').value) || $('device').value==='TRD_TESTE';
  async function api(url,body,method=body?'POST':'GET') {
    const response=await fetch(url,{method,cache:'no-store',signal:AbortSignal.timeout(15000),
      headers:body?{'Content-Type':'application/json',Accept:'application/json'}:{Accept:'application/json'},...(body?{body:JSON.stringify(body)}:{})});
    const data=await response.json();
    if(!response.ok) throw Object.assign(new Error(data.erro || `Falha HTTP ${response.status}`),{status:response.status});
    return data;
  }
  function message(text,error=false) { $('message').textContent=text; $('message').hidden=!text; $('message').classList.toggle('error',error); }
  function locks() {
    for(const e of document.querySelectorAll('button,input,select')) e.disabled=state.busy;
    $('activate').disabled=state.busy || !state.conclusion || !!state.operation?.ativo;
    for(const id of ['stop','resume']) $(id).disabled=state.busy || !state.operation?.ativo;
    $('policy-fields').disabled=state.busy || !!state.operation?.ativo;
    for(const e of $('local-board').querySelectorAll('button,input')) e.disabled=state.busy || !state.operation?.ativo;
  }
  async function action(work) {
    if(state.busy) return; state.busy=true; locks(); message('');
    try { await work(); } catch(e) { message(e.message,true); }
    finally { state.busy=false; locks(); }
  }
  function shift(nome='Teste 24h',inicio='00:00',fim='00:00') {
    const row=document.createElement('div'); row.className='shift-row';
    row.innerHTML=`<label>Nome do turno<input data-shift="nome" maxlength="100" required value="${escape(nome)}"></label><label>Início<input data-shift="inicio" type="time" required value="${escape(inicio)}"></label><label>Fim<input data-shift="fim" type="time" required value="${escape(fim)}"></label><button type="button" class="secondary">Remover</button>`;
    row.querySelector('button').addEventListener('click',()=>{if(!state.operation?.ativo)row.remove();});
    $('shifts').append(row);
  }
  function policy() {
    const integer=id=>{const n=Number($(id).value);if($(id).value==='' || !Number.isSafeInteger(n)||n<0)throw new Error('Preencha os valores inteiros das regras.');return n;};
    return {fuso:$('zone').value.trim(),lacunaMaximaSegundos:integer('gap'),atrasoMaximoSegundos:integer('delay'),adiantamentoMaximoSegundos:integer('future'),
      microparadaLimiteSegundos:integer('micro'),microparadaLimiteInclusivo:$('inclusive').checked,
      turnos:[...$('shifts').children].map(row=>Object.fromEntries([...row.querySelectorAll('[data-shift]')].map(e=>[e.dataset.shift,e.value]))),
      estadoNoIntervalo:'AMOSTRA_ANTERIOR',foraDeOrdem:'IGNORAR',lacunas:'DESCARTAR_E_REINICIAR_REFERENCIA',fronteiras:'DESCARTAR_INTERVALO',programadas:'EXCLUIR_DO_PLANEJADO',paradaInterrompida:'DESCARTAR_PENDENTES'};
  }
  function fillPolicy(p) {
    for(const [key,id] of Object.entries({fuso:'zone',lacunaMaximaSegundos:'gap',atrasoMaximoSegundos:'delay',adiantamentoMaximoSegundos:'future',microparadaLimiteSegundos:'micro'})) $(id).value=p[key];
    $('inclusive').checked=p.microparadaLimiteInclusivo; $('shifts').replaceChildren();
    p.turnos.forEach(t=>shift(t.nome,t.inicio,t.fim));
  }
  async function inventory(selected='') {
    const linhas=await api('/api/linhas/rascunhos');
    $('device').replaceChildren(new Option('Selecione uma linha',''),...linhas.map(l=>new Option(`${l.nomeLinha} · ${l.dispositivoId}`,l.dispositivoId)));
    $('device').value=selected;
  }
  const diagnostics={AGUARDANDO_REFERENCIA:'Aguardando a primeira leitura',REFERENCIA_ESTABELECIDA:'Referência estabelecida; aguardando o próximo intervalo',PROCESSANDO:'Processando produção',RECUPERACAO_NECESSARIA:'Contador desconfigurado ou reduzido; recuperação necessária',AGUARDANDO_FECHAMENTO_PARADA:'Parada em aberto; classificação pendente',PARADA_DE_DISPONIBILIDADE:'Parada de disponibilidade',ENCERRADA:'Execução encerrada',LACUNA:'Lacuna descartada; referência restabelecida',FRONTEIRA_DE_PERIODO:'Intervalo atravessou minuto/turno e foi excluído',SINAIS_INCONSISTENTES:'Sinais inconsistentes; intervalo excluído',PARADA_PROGRAMADA:'Período programado excluído do planejado',FRONTEIRA_PROGRAMADA:'Fronteira de parada programada excluída',FORA_DO_TURNO:'Amostra fora do turno'};
  async function refresh(fill=false) {
    const id=$('device').value,g=state.generation;
    if(!id) { state.operation=null;state.conclusion=null;$('status').textContent='Selecione uma linha ou prepare uma demonstração.';locks();return; }
    const results=await Promise.allSettled([api(`${path()}`),api(`/api/dispositivos/${encodeURIComponent(id)}/configuracao/concluida`)]);
    if(id!==$('device').value || g!==state.generation)return;
    for(const r of results) if(r.status==='rejected' && r.reason.status!==404)throw r.reason;
    state.operation=results[0].status==='fulfilled'?results[0].value:null;
    state.conclusion=results[1].status==='fulfilled'?results[1].value:null;
    $('local-board').hidden=!local();
    $('revision').textContent=state.conclusion?`Revisão concluída ${state.conclusion.revisao}${state.conclusion.situacao==='RASCUNHO_ALTERADO'?' · O rascunho foi alterado; conclua-o novamente antes da próxima ativação.':''}`:'Conclua o cadastro da linha antes de ativar.';
    const op=state.operation;
    $('status').textContent=op?`${diagnostics[op.estado.diagnostico] || op.estado.diagnostico} · Revisão em execução ${op.revisao}${op.semComunicacao?' · Sem mensagem recente':''}${op.intervalosPendentes?` · ${op.intervalosPendentes} intervalo(s) aguardando fechamento`:''}`:'Processamento ainda não ativado.';
    if(fill && op)fillPolicy(op.politica);
    if(op) {
      const [resumo,periodos]=await Promise.all([api(`${path()}/indicadores`),api(`${path()}/indicadores?agrupamento=${state.group}`)]);
      if(id!==$('device').value || g!==state.generation)return;
      const bases=resumo[0],i=bases.indicadores;
      $('metrics').innerHTML=[['Disponibilidade',percent(i.disponibilidade)],['Performance',percent(i.performance)],['Qualidade',percent(i.qualidade)],['OEE',percent(i.oee)],['Produção',number(i.quantidadeTexto ?? i.quantidade)],['Produtos bons',number(i.boasTexto ?? i.boas)],['PPM no tempo válido',number(i.ppmTexto ?? i.ppm)],['Tempo válido',`${number(i.planejadoSegundos)} s`]].map(([label,value])=>`<div class="metric"><span>${escape(label)}</span><strong>${escape(value)}</strong></div>`).join('');
      $('coverage').textContent=`${i.intervalosValidos} intervalos válidos · ${bases.intervalosExcluidos} excluídos (${number(bases.segundosExcluidos)} s) · ${op.intervalosPendentes} pendentes · ${op.estado.amostrasIgnoradas || 0} amostras ignoradas${op.estado.ultimaAmostraIgnorada?' ('+op.estado.ultimaAmostraIgnorada+')':''}. Performance antes do limite: ${percent(i.performanceSemLimite)}.`;
      $('events').textContent=`Microparadas: ${bases.microparadas} / ${number(bases.microparadaSegundos)} s · Paradas de disponibilidade: ${bases.paradas} / ${number(bases.paradaSegundos)} s.`;
      $('periods').innerHTML=periodos.map(r=>{const x=r.indicadores;return `<tr><td>${escape(new Date(r.minuto || r.turnoInicio).toLocaleString('pt-BR',{timeZone:op.politica.fuso}))}</td><td>${escape(r.turno)}</td><td>${escape(number(x.quantidadeTexto ?? x.quantidade))}</td><td>${escape(number(x.ppmTexto ?? x.ppm))}</td><td>${escape(percent(x.disponibilidade))}</td><td>${escape(percent(x.performance))}</td><td>${escape(percent(x.qualidade))}</td><td>${escape(percent(x.oee))}</td></tr>`;}).join('');
      $('metrics-time').textContent=`Atualizado ${new Date().toLocaleTimeString('pt-BR')}`;
    } else { $('metrics').replaceChildren();$('periods').replaceChildren();$('coverage').textContent='';$('events').textContent=''; }
    if(local()) {
      try { const b=await api(board()); if(id===$('device').value)$('board-status').textContent=`Placa ${b.ligada?'ligada':'desligada'} · ${Object.entries(b.contadores).map(([c,v])=>`${c}: ${v.valor} (${v.borda})`).join(' · ')}${b.ultimaConfirmacao?' · Último comando confirmado localmente: '+b.ultimaConfirmacao.acao:''}`; }
      catch(e) { if(e.status!==404)throw e;$('board-status').textContent='Placa local ainda não criada. Use Preparar demonstração local.'; }
    }
    locks();
  }
  $('device').addEventListener('change',()=>{state.generation++;action(()=>refresh(true));});
  $('refresh').addEventListener('click',()=>action(async()=>{const id=$('device').value;await inventory(id);await refresh();}));
  $('add-shift').addEventListener('click',()=>shift('Novo turno','06:00','14:00'));
  $('policy-form').addEventListener('submit',e=>{e.preventDefault();if(!$('policy-form').reportValidity())return;action(async()=>{
    if(!state.conclusion)throw new Error('Conclua a configuração antes de ativar.');
    await api(`${path()}/ativar`,{revisao:state.conclusion.revisao,politica:policy()});await refresh();message('Processamento ativado. A primeira amostra estabelece a referência.');
  });});
  $('stop').addEventListener('click',()=>action(async()=>{await api(`${path()}/parar`,{execucao:state.operation.execucao});await refresh();message('Execução encerrada. Resultados preservados.');}));
  $('resume').addEventListener('click',()=>action(async()=>{if(!$('resume-reason').value.trim())throw new Error('Informe o motivo da retomada.');await api(`${path()}/nova-referencia`,{execucao:state.operation.execucao,motivo:$('resume-reason').value.trim()});await refresh();message('Nova sessão de referência criada; acumulados anteriores preservados.');}));
  document.querySelectorAll('[data-group]').forEach(b=>b.addEventListener('click',()=>action(async()=>{state.group=b.dataset.group;await refresh();})));
  document.querySelectorAll('[data-command]').forEach(b=>b.addEventListener('click',()=>action(async()=>{await api(`${board()}/comandos`,{acao:b.dataset.command,execucao:state.operation.execucao});await refresh();message('Comando confirmado pelo simulador local.');})));
  $('sample-form').addEventListener('submit',e=>{e.preventDefault();if(!$('sample-form').reportValidity())return;action(async()=>{
    const n=id=>{const v=Number($(id).value);if(!Number.isSafeInteger(v))throw new Error('Informe um inteiro com precisão segura.');return v;};
    await api(`${board()}/passo`,{execucao:state.operation.execucao,segundos:n('step'),rodando:$('running').checked,pulsosTotal:n('total-pulses'),pulsosQualidade:n('quality-pulses')});await refresh();
  });});
  $('planned-form').addEventListener('submit',e=>{e.preventDefault();if(!$('planned-form').reportValidity())return;action(async()=>{
    if(!state.operation?.ativo)throw new Error('Ative a execução antes de programar.');
    await api(`${path()}/programadas`,{execucao:state.operation.execucao,inicio:new Date($('planned-start').value).toISOString(),fim:new Date($('planned-end').value).toISOString(),motivo:$('planned-reason').value.trim()});message('Período programado. Seus intervalos ficarão fora do planejado.');
  });});
  $('demo').addEventListener('click',()=>action(async()=>{
    const id=`LOCAL_DEMO_${Date.now()}`,base=`/api/dispositivos/${id}`;
    await api(`/api/simulacoes/placas/${id}`,{fuso:'UTC',rodando:true});
    await api(`${base}/linha-rascunho`,{nomeLinha:'Demonstração local',produto:'Produto teste',velocidadeIdealPpm:120,versao:0},'PUT');
    const put=(url,body)=>api(url,body,'PUT');
    // Requisições explícitas do mesmo cadastro usado pelo configurador.
    await put(`${base}/canais-rascunho/DI3`,{nome:'Estado local',habilitado:true,funcao:'MAQUINA_RODANDO',parametros:{nivelAtivo:1},versao:0});
    for(const [codigo,funcao,borda] of [['contadorDI1','TOTAL_PRODUZIDO','RISING'],['contadorDI4','PRODUTOS_BONS','FALLING']])await put(`${base}/canais-rascunho/${codigo}`,{nome:codigo,habilitado:true,funcao,parametros:{unidade:'un',fator:1,borda},versao:0});
    const c=await api(`${base}/configuracao/conferir`,{fontes:{estado:'DI3',total:'contadorDI1',qualidade:'contadorDI4'}});
    await api(`${base}/configuracao/concluir`,{fontes:c.fontes,versaoLinha:c.versaoLinha,versoesCanais:c.versoesCanais,revisaoAtual:c.revisaoAtual});
    await inventory(id);state.generation++;await refresh();message('Linha de demonstração preparada. Confira as regras, ative o processamento e habilite os contadores locais.');
  }));
  shift(); action(async()=>{await inventory();await refresh();});
  let polling=false;
  setInterval(async()=>{if(polling || state.busy || document.hidden || !$('device').value)return;polling=true;try{await refresh();}catch(e){message(e.message,true);}finally{polling=false;}},5000);
})();

