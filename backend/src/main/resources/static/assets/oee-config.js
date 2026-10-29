'use strict';
(() => {
  const $ = id => document.getElementById(id);
  const lineForm = $('line-form'), channelForm = $('channel-form');
  const input = (form, name) => form.elements.namedItem(name);
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const roles = {
    DI: [['MAQUINA_RODANDO','Máquina rodando'],['MAQUINA_PARADA','Máquina parada'],['MAQUINA_OCIOSA','Máquina ociosa'],['FALHA','Falha']],
    CONTADOR: [['TOTAL_PRODUZIDO','Total produzido'],['PRODUTOS_BONS','Produtos bons'],['PRODUTOS_REJEITADOS','Produtos rejeitados']],
    AI: [['MEDIDA_ANALOGICA','Medida analógica']]
  };
  const types = { DI:'Entrada digital', CONTADOR:'Contador acumulado', AI:'Entrada analógica', DO:'Saída digital' };
  const channels = ['DI','CONTADOR','AI','DO'].flatMap(type => [1,2,3,4].map(n => ({type,code:type==='CONTADOR'?`contadorDI${n}`:`${type}${n}`})));
  const state = { devices:[], readings:new Map(), drafts:new Map(), legacy:new Map(), selected:null, line:null, configs:new Map(), code:'DI1', filter:'all', lineDirty:false, channelDirty:false, busy:false, loading:false, lineReady:false, channelsReady:false, inventoryReady:false, generation:0 };
  class ApiError extends Error { constructor(status,message) { super(message); this.status=status; } }
  async function api(path, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
      const response = await fetch(path, {...options, signal:controller.signal, cache:'no-store', headers:{Accept:'application/json',...(options.body?{'Content-Type':'application/json'}:{}),...options.headers}});
      const text = await response.text();
      let data;
      try { data = text ? JSON.parse(text) : null; } catch { throw new ApiError(response.status,'Resposta inesperada da API. Confira se a aplicação está atualizada.'); }
      if (!response.ok) throw new ApiError(response.status,data?.erro || `A requisição retornou HTTP ${response.status}.`);
      return data;
    } catch (error) {
      if (error.name==='AbortError') throw new ApiError(0,'A API demorou a responder. Consulte o cadastro salvo antes de reenviar uma alteração.');
      if (error instanceof ApiError) throw error;
      throw new ApiError(0,'Não foi possível acessar a API. Confira se a aplicação está em execução.');
    } finally { clearTimeout(timeout); }
  }
  function message(id, text = '', error = false) {
    const element = $(id);
    element.textContent = text;
    element.classList.toggle('error',error);
    element.hidden = !text;
  }
  function time(value) {
    if (!value) return '—';
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString('pt-BR');
  }
  function currentChannel() { return channels.find(c => c.code===state.code); }
  function currentReading(code) { return state.readings.get(state.selected)?.canais.find(c => c.codigo===code); }
  function path() { return `/api/dispositivos/${encodeURIComponent(state.selected)}`; }
  async function allowDiscard(onlyChannel=false) {
    if (!(state.channelDirty || (!onlyChannel && state.lineDirty))) return true;
    const dialog=$('discard-dialog');
    return new Promise(resolve=>{
      const finish=value=>{
        dialog.close();
        $('discard-confirm').removeEventListener('click',confirm);
        $('discard-cancel').removeEventListener('click',cancel);
        dialog.removeEventListener('cancel',escapeDialog);
        resolve(value);
      };
      const confirm=()=>finish(true), cancel=()=>finish(false);
      const escapeDialog=e=>{e.preventDefault();finish(false);};
      $('discard-confirm').addEventListener('click',confirm);
      $('discard-cancel').addEventListener('click',cancel);
      dialog.addEventListener('cancel',escapeDialog);
      dialog.showModal();
    });
  }
  function markDirty(kind) {
    state[`${kind}Dirty`] = true;
    $(`${kind}-state`).textContent = 'Alterações não salvas';
    message(`${kind}-message`);
  }
  function setBusy(value) {
    state.busy=value;
    $('workspace').classList.toggle('loading',value);
    $('refresh').disabled=value;
    $('reload').disabled=value;
    updateLocks();
  }
  function updateLocks() {
    for (const e of lineForm.querySelectorAll('input,button')) e.disabled=state.busy || state.loading || !state.lineReady;
    const c=currentChannel();
    const locked=state.busy || state.loading || !state.line || !state.channelsReady || c.type==='DO';
    $('channel-fields').disabled=locked;
    $('channel-save').disabled=locked;
    $('channel-form').hidden=c.type==='DO';
    let text='';
    if(c.type==='DO') text='As saídas digitais são exibidas para consulta. Seu uso funcional será definido em uma etapa posterior.';
    else if(!state.line) text='Salve primeiro a identificação da linha para configurar este canal.';
    else if(!state.channelsReady) text='O cadastro de canais está indisponível. Confira a mensagem acima e recarregue o cadastro.';
    $('channel-lock').textContent=text; $('channel-lock').hidden=!text;
  }
  function renderDevices() {
    const query=$('search').value.toLocaleLowerCase('pt-BR').trim();
    const list=state.devices.filter(id => `${id} ${state.drafts.get(id)?.nomeLinha || state.legacy.get(id)?.nome || ''}`.toLocaleLowerCase('pt-BR').includes(query));
    $('device-list').innerHTML=list.map(id => `<button type="button" class="device-button" data-device="${escape(id)}" aria-current="${id===state.selected}"><span class="device-glyph" aria-hidden="true">▧</span><span><strong>${escape(id)}</strong><small>${escape(state.drafts.get(id)?.nomeLinha || state.legacy.get(id)?.nome || 'Aguardando cadastro da linha')}</small></span><i class="dot ${state.readings.has(id)?'has-reading':''}" aria-hidden="true"></i></button>`).join('');
    if(!list.length) {
      const p=document.createElement('p'); p.className='note';
      p.textContent=state.devices.length ? 'Nenhum dispositivo encontrado.' : 'Nenhum dispositivo disponível. Aguarde uma leitura da placa e atualize a lista.';
      $('device-list').append(p);
    }
  }
  function renderChannels() {
    $('configured-count').textContent=`${[...state.configs.values()].filter(c=>c.habilitado).length} canais habilitados`;
    const list=channels.filter(c=>state.filter==='all'||c.type===state.filter);
    $('channel-list').innerHTML=list.map(c => {
      const config=state.configs.get(c.code), reading=currentReading(c.code);
      const value=reading ? String(reading.valor) : '—';
      return `<button type="button" class="channel-button" data-channel="${c.code}" aria-current="${c.code===state.code}"><span class="channel-mark ${config?.habilitado?'configured':''}" aria-hidden="true"></span><span><strong>${c.code}</strong><small>${escape(config?.nome || (c.type==='DO'?'Somente leitura':config?'Sem uso':'Sem configuração'))}</small></span><span class="channel-number">${escape(value)}</span></button>`;
    }).join('');
  }
  function updateReadings() {
    if(!state.selected) return;
    const device=state.readings.get(state.selected);
    $('received-at').textContent=time(device?.recebidoEm);
    $('reading-presence').textContent=device ? 'Última amostra recebida · horário deste navegador' : 'Sem leitura nesta execução da API';
    const c=currentReading(state.code);
    $('channel-value').textContent=c ? `${c.valor}${currentChannel().type==='AI'?' V':''}` : '—';
    $('channel-reading-state').textContent=!c ? 'Sem amostra disponível' : c.situacaoLeitura==='DESLIGADO_OU_DESCONFIGURADO' ? 'Contador desligado ou desconfigurado' : `Enviado pela placa: ${device.timestamp}`;
    renderChannels();
  }
  function selectField(name,label,options,value,extra='') {
    return `<label class="${extra}">${label}<select name="${name}" required>${options.map(([v,l])=>`<option value="${v}" ${String(v)===String(value)?'selected':''}>${escape(l)}</option>`).join('')}</select></label>`;
  }
  function field(name,label,value,options={}) {
    return `<label class="${options.wide?'span-all':''}">${label}<input name="${name}" type="${options.number?'number':'text'}" ${options.number?'step="any"':`maxlength="${options.maxlength || 30}"`} ${options.min!==undefined?`min="${options.min}"`:''} ${options.max!==undefined?`max="${options.max}"`:''} value="${escape(value)}" required></label>`;
  }
  function renderEditor() {
    const c=currentChannel(), config=state.configs.get(c.code), p=config?.parametros || {};
    state.channelDirty=false;
    message('channel-message');
    $('channel-type').textContent=types[c.type]; $('channel-title').textContent=c.code;
    $('channel-version').textContent=config?`Versão ${config.versao}`:'Sem cadastro';
    $('channel-state').textContent=config?`Salvo em ${time(config.atualizadoEm)}`:'Configuração ainda não salva';
    input(channelForm,'nome').value=config?.nome || '';
    $('channel-enabled').checked=!!config?.habilitado;
    let html='';
    if(c.type!=='DO') html+=selectField('funcao','Função do canal',[['','Selecione uma função'],...roles[c.type]],config?.funcao || '', 'span-all');
    if(c.type==='DI') html+=selectField('nivelAtivo','Nível que ativa a função',[[1,'1 = ativo · 0 = inativo'],[0,'0 = ativo · 1 = inativo']],p.nivelAtivo ?? 1,'span-all');
    if(c.type==='CONTADOR') {
      html+=field('unidade','Unidade',p.unidade || 'pecas');
      html+=field('fator','Peças por contagem',p.fator ?? 1,{number:true,min:0});
      html+=selectField('borda','Borda de contagem',[['RISING','Subida (RISING)'],['FALLING','Descida (FALLING)']],p.borda || 'RISING','span-all');
      html+='<p class="field-note span-all">A borda será salva no cadastro. A habilitação do contador na placa depende da integração de comandos.</p>';
    }
    if(c.type==='AI') {
      html+=field('grandeza','Grandeza',p.grandeza || '',{maxlength:100});
      html+=field('unidade','Unidade',p.unidade || '');
      html+=field('tensaoMin','Entrada mínima (V)',p.tensaoMin ?? 0,{number:true,min:0,max:10});
      html+=field('tensaoMax','Entrada máxima (V)',p.tensaoMax ?? 10,{number:true,min:0,max:10});
      html+=field('valorMin','Valor na entrada mínima',p.valorMin ?? 0,{number:true});
      html+=field('valorMax','Valor na entrada máxima',p.valorMax ?? 100,{number:true});
      html+='<p class="field-note span-all">A escala fica cadastrada. A leitura bruta acima continua sendo apresentada em volts.</p>';
    }
    $('channel-parameters').innerHTML=html;
    updateEnabled(); updateLocks(); updateReadings();
  }
  function updateEnabled() {
    const enabled=$('channel-enabled').checked;
    input(channelForm,'nome').required=enabled;
    $('channel-parameters').hidden=!enabled;
    for(const e of $('channel-parameters').querySelectorAll('input,select')) { e.disabled=!enabled; e.required=enabled; }
    if(!enabled) $('channel-state').textContent='Ao salvar sem uso, a função e os parâmetros serão removidos deste rascunho.';
  }
  async function loadInventory(initial=false) {
    if(state.busy || state.loading) return;
    $('refresh').disabled=true;
    const results=await Promise.allSettled([api('/api/dispositivos'),api('/api/linhas/rascunhos'),api('/api/equipamentos')]);
    const [readings,drafts,legacy]=results;
    if(readings.status==='fulfilled') state.readings=new Map(readings.value.map(d=>[d.id,d]));
    if(drafts.status==='fulfilled') state.drafts=new Map(drafts.value.map(d=>[d.dispositivoId,d]));
    if(legacy.status==='fulfilled') state.legacy=new Map(legacy.value.map(d=>[d.id,d]));
    state.inventoryReady=drafts.status==='fulfilled';
    state.devices=[...new Set([...state.readings.keys(),...state.drafts.keys(),...state.legacy.keys()])].sort();
    const errors=results.filter(r=>r.status==='rejected').map(r=>r.reason.message);
    message('global-message',errors.length?`Não foi possível carregar todos os dados: ${[...new Set(errors)].join(' ')}`:'',!!errors.length);
    renderDevices(); updateReadings(); $('refresh').disabled=false;
    if(initial && state.devices.length) await selectDevice(state.devices[0]);
  }
  async function selectDevice(id,force=false) {
    if(state.busy || state.loading || (!force && id===state.selected)) return;
    if(!await allowDiscard()) return;
    state.selected=id; state.line=null; state.configs=new Map(); state.lineReady=false; state.channelsReady=false;
    state.lineDirty=false; state.channelDirty=false; state.loading=true;
    const generation=++state.generation;
    $('empty').hidden=true; $('workspace').hidden=false; $('workspace').classList.add('loading');
    $('device-title').textContent=id; message('line-message'); message('channel-message');
    renderDevices(); updateLocks();
    let channelError='';
    try {
      try { state.line=await api(`${path()}/linha-rascunho`); }
      catch(e) { if(e.status!==404 || !state.inventoryReady) throw e; }
      if(generation!==state.generation) return;
      state.lineReady=true;
      if(state.line) {
        try { const data=await api(`${path()}/canais-rascunho`); state.configs=new Map(data.map(c=>[c.codigo,c])); state.channelsReady=true; }
        catch(e) { channelError=e.message; }
      }
    } catch(e) { message('line-message',e.message,true); }
    finally {
      if(generation===state.generation) {
        state.loading=false; $('workspace').classList.remove('loading');
        const l=state.line;
        input(lineForm,'nomeLinha').value=l?.nomeLinha || '';
        input(lineForm,'produto').value=l?.produto || '';
        input(lineForm,'velocidadeIdealPpm').value=l?.velocidadeIdealPpm ?? '';
        $('line-version').textContent=l?`Versão ${l.versao}`:'Sem cadastro';
        $('line-state').textContent=l?`Salvo em ${time(l.atualizadoEm)}`:'Cadastre a linha para configurar seus canais.';
        state.code=[...state.configs.keys()][0] || 'DI1';
        renderEditor();
        // renderEditor limpa apenas mensagens do formulário, preservando falhas de carregamento.
        if(state.line && !state.channelsReady) message('channel-message',`${channelError || 'Não foi possível carregar os canais.'} Use “Recarregar cadastro salvo” para tentar novamente.`,true);
      }
    }
  }
  function numeric(form,name) {
    const el=input(form,name), value=Number(el.value);
    if(el.value.trim()==='' || !Number.isFinite(value)) throw new Error('Preencha os campos numéricos com valores válidos.');
    return value;
  }
  async function saveLine(event) {
    event.preventDefault(); if(state.busy || !state.lineReady) return;
    if(!lineForm.reportValidity()) return;
    const id=state.selected;
    let body;
    try { body={nomeLinha:input(lineForm,'nomeLinha').value.trim(),produto:input(lineForm,'produto').value.trim(),velocidadeIdealPpm:numeric(lineForm,'velocidadeIdealPpm'),versao:state.line?.versao ?? 0}; }
    catch(e) { message('line-message',e.message,true); return; }
    setBusy(true);
    try {
      const saved=await api(`${path()}/linha-rascunho`,{method:'PUT',body:JSON.stringify(body)});
      state.line=saved; state.drafts.set(id,saved); state.lineDirty=false;
      input(lineForm,'nomeLinha').value=saved.nomeLinha; input(lineForm,'produto').value=saved.produto;
      $('line-version').textContent=`Versão ${saved.versao}`; $('line-state').textContent=`Salvo em ${time(saved.atualizadoEm)}`;
      message('line-message','Rascunho da linha salvo.'); renderDevices();
      if(!state.channelsReady) {
        try { const data=await api(`${path()}/canais-rascunho`); state.configs=new Map(data.map(c=>[c.codigo,c])); state.channelsReady=true; renderEditor(); }
        catch(e) { channelError=e.message; }
      }
    } catch(e) { message('line-message',e.status===409?`${e.message} Seu formulário foi mantido. Use “Recarregar cadastro salvo” antes de editar novamente.`:e.message,true); }
    finally { setBusy(false); }
  }
  async function saveChannel(event) {
    event.preventDefault();
    if(state.busy || !state.channelsReady || !state.line || currentChannel().type==='DO') return;
    if(!channelForm.reportValidity()) return;
    const c=currentChannel(), enabled=$('channel-enabled').checked;
    let parametros={};
    try {
      if(enabled && c.type==='DI') parametros={nivelAtivo:numeric(channelForm,'nivelAtivo')};
      if(enabled && c.type==='CONTADOR') parametros={unidade:input(channelForm,'unidade').value.trim(),fator:numeric(channelForm,'fator'),borda:input(channelForm,'borda').value};
      if(enabled && c.type==='AI') {
        parametros={grandeza:input(channelForm,'grandeza').value.trim(),unidade:input(channelForm,'unidade').value.trim()};
        for(const name of ['tensaoMin','tensaoMax','valorMin','valorMax']) parametros[name]=numeric(channelForm,name);
        if(parametros.tensaoMin>=parametros.tensaoMax) throw new Error('A entrada mínima deve ser menor que a entrada máxima.');
        if(parametros.valorMin===parametros.valorMax) throw new Error('Os extremos da escala devem ser diferentes.');
      }
    } catch(e) { message('channel-message',e.message,true); return; }
    const body={nome:input(channelForm,'nome').value.trim() || null,habilitado:enabled,funcao:enabled?input(channelForm,'funcao').value:null,parametros,versao:state.configs.get(c.code)?.versao ?? 0};
    setBusy(true);
    try {
      const saved=await api(`${path()}/canais-rascunho/${c.code}`,{method:'PUT',body:JSON.stringify(body)});
      state.configs.set(c.code,saved); renderEditor(); message('channel-message',`${c.code} salvo em rascunho.`);
    } catch(e) { message('channel-message',e.status===409?`${e.message} Seu formulário foi mantido. Use “Recarregar cadastro salvo” para consultar a versão atual.`:e.message,true); }
    finally { setBusy(false); }
  }
  $('device-list').addEventListener('click',e=>{const b=e.target.closest('[data-device]'); if(b) selectDevice(b.dataset.device);});
  $('channel-list').addEventListener('click',async e=>{
    const b=e.target.closest('[data-channel]');
    if(!b || state.busy || state.loading || b.dataset.channel===state.code) return;
    if(!await allowDiscard(true)) return;
    state.code=b.dataset.channel; renderEditor();
  });
  document.querySelector('.filters').addEventListener('click',e=>{
    const b=e.target.closest('[data-filter]'); if(!b) return;
    state.filter=b.dataset.filter;
    document.querySelectorAll('[data-filter]').forEach(el=>el.setAttribute('aria-pressed',String(el===b)));
    renderChannels();
  });
  $('search').addEventListener('input',renderDevices);
  $('refresh').addEventListener('click',()=>loadInventory());
  $('reload').addEventListener('click',()=>selectDevice(state.selected,true));
  lineForm.addEventListener('input',()=>markDirty('line'));
  channelForm.addEventListener('input',()=>markDirty('channel'));
  $('channel-enabled').addEventListener('change',()=>{updateEnabled();markDirty('channel');});
  lineForm.addEventListener('submit',saveLine); channelForm.addEventListener('submit',saveChannel);
  window.addEventListener('beforeunload',e=>{if(state.lineDirty||state.channelDirty||state.busy){e.preventDefault();e.returnValue='';}});
  let polling=false;
  setInterval(async()=>{
    if(polling || state.busy || state.loading || !state.selected || document.hidden) return;
    polling=true; const id=state.selected, generation=state.generation;
    try {
      const d=await api(`/api/dispositivos/${encodeURIComponent(id)}`);
      if(id===state.selected && generation===state.generation){state.readings.set(id,d);updateReadings();renderDevices();}
    } catch(e) {
      if(id===state.selected && generation===state.generation) {
        if(e.status===404) {state.readings.delete(id);updateReadings();renderDevices();}
        else $('reading-presence').textContent='Não foi possível atualizar a leitura';
      }
    } finally {polling=false;}
  },5000);
  loadInventory(true);
})();
