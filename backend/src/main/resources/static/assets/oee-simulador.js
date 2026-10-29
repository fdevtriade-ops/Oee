'use strict';
(() => {
  const $=id=>document.getElementById(id);
  const escape=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const row=(contador,segundos,sessao='A')=>({sessao,leitura:{id:'TRD_TESTE',timestamp:`29/10/2026 15:00:${String(segundos).padStart(2,'0')}`,contadorDI1:contador}});
  const examples={
    normal:[row(1000,0),row(1010,5)],
    media:[row(1000,0),row(1010,5),row(1025,15)],
    sessao:[row(1000,0),row(1005,5),row(-1,10),row(1020,15),row(0,20,'B'),row(10,25,'B')],
    inconsistencia:[row(1000,0),row(1010,5),row(1011,5),row(1015,10),row(100,15),row(105,20),row(0,25,'B'),row(5,30,'B')]
  };
  const labels={REFERENCIA_INICIAL:'Referência inicial',VALIDO:'Válido',NOVA_SESSAO:'Nova sessão',NOVA_REFERENCIA:'Nova referência',TIMESTAMP_INVALIDO:'Data inválida',TIMESTAMP_REPETIDO:'Horário repetido',FORA_DE_ORDEM:'Fora de ordem',CONTAGEM_INVALIDA:'Contagem inválida',CONTADOR_DESCONFIGURADO:'Contador desconfigurado',SESSAO_INCONSISTENTE:'Sessão inconsistente',CONTADOR_DECRESCENTE:'Contador decrescente'};
  let busy=false,changed=false,abortController;
  function dirty(){changed=true;$('scenario-state').textContent='Cenário alterado. Execute novamente para atualizar os resultados.';if(!$('simulation-result').hidden)$('result-status').textContent='Resultado anterior';}
  async function loadExample(){
    if(changed){
      const dialog=$('replace-dialog');
      const confirmed=await new Promise(resolve=>{
        const finish=value=>{dialog.close();$('keep-scenario').removeEventListener('click',keep);$('replace-scenario').removeEventListener('click',replace);dialog.removeEventListener('cancel',cancel);resolve(value);};
        const keep=()=>finish(false),replace=()=>finish(true),cancel=e=>{e.preventDefault();finish(false);};
        $('keep-scenario').addEventListener('click',keep);$('replace-scenario').addEventListener('click',replace);dialog.addEventListener('cancel',cancel);dialog.showModal();
      });
      if(!confirmed)return;
    }
    $('counter').value='contadorDI1';$('factor').value='1';$('samples').value=JSON.stringify(examples[$('example').value],null,2);
    changed=false;$('scenario-state').textContent='Exemplo carregado. Clique em Executar simulação.';$('simulation-error').hidden=true;$('simulation-result').hidden=true;
  }
  function result(data){
    const r=data.resumo;
    $('result-context').textContent=`${data.dispositivoId} · ${data.canal} · Fator ${$('factor').value} · Somente dados desta simulação`;
    $('result-status').textContent=({CALCULADO:'Calculado',PARCIAL:'Resultado parcial',SEM_INTERVALOS_VALIDOS:'Sem intervalos válidos'})[r.situacao] || r.situacao;
    $('total-quantity').textContent=r.quantidadeTexto??'—';$('average-ppm').textContent=r.ppmMedioTexto??'—';$('total-seconds').textContent=`${r.segundosCalculados} s`;
    $('valid-intervals').textContent=`${r.intervalosValidos} de ${r.intervalosPossiveis} intervalos calculados`;
    $('result-rows').innerHTML=data.passos.map(p=>{
      const cls=p.situacao==='VALIDO'?'valid':['REFERENCIA_INICIAL','NOVA_SESSAO'].includes(p.situacao)?'':'problem';
      return `<tr><td>${p.indice}</td><td>${escape(p.sessao)}<small>${escape(p.timestamp??'Sem horário')}</small></td><td class="numeric">${escape(p.contador??'—')}</td><td>${p.intervaloSegundos===null?'—':`${p.intervaloSegundos} s`}</td><td class="numeric">${escape(p.quantidadeTexto??'—')}</td><td class="numeric">${escape(p.ppmTexto??'—')}</td><td><span class="result-state ${cls}">${escape(labels[p.situacao]??p.situacao)}</span><small>${escape(p.detalhe)}</small></td></tr>`;
    }).join('');
    $('simulation-result').hidden=false;changed=false;$('scenario-state').textContent='Simulação concluída. Nenhum dado da linha foi alterado.';
  }
  $('simulation-form').addEventListener('input',e=>{if(e.target.id!=='example')dirty();});
  $('load-example').addEventListener('click',loadExample);
  $('simulation-form').addEventListener('submit',async event=>{
    event.preventDefault();if(busy)return;
    $('simulation-error').hidden=true;
    let body;
    try{
      const fator=Number($('factor').value),text=$('samples').value;
      if(!Number.isFinite(fator)||fator<=0)throw new Error('Informe um fator positivo e finito.');
      const parsed=JSON.parse(text);
      if(!Array.isArray(parsed))throw new Error('As amostras devem formar uma lista JSON.');
      // O texto original segue ao servidor, preservando inteiros além da precisão numérica do JavaScript.
      body=`{"canal":${JSON.stringify($('counter').value)},"fator":${fator},"amostras":${text}}`;
    }catch(e){$('simulation-error').textContent=e instanceof SyntaxError?'O JSON das amostras está inválido. Confira vírgulas, aspas e colchetes.':e.message;$('simulation-error').hidden=false;return;}
    busy=true;for(const el of document.querySelectorAll('#simulation-form input,#simulation-form select,#simulation-form textarea,#simulation-form button'))el.disabled=true;
    $('simulate').textContent='Calculando…';abortController=new AbortController();const timeout=setTimeout(()=>abortController.abort(),15000);
    try{
      const response=await fetch('/api/simulacoes/contadores',{method:'POST',headers:{'Content-Type':'application/json',Accept:'application/json'},body,signal:abortController.signal});
      let data;try{data=await response.json();}catch{throw new Error('Resposta inesperada. Confira se a API foi atualizada.');}
      if(!response.ok)throw new Error(data.erro || `A simulação retornou HTTP ${response.status}.`);
      result(data);
    }catch(e){$('simulation-error').textContent=e.name==='AbortError'?'A simulação demorou a responder. Tente novamente.':e.message;$('simulation-error').hidden=false;if(!$('simulation-result').hidden)$('result-status').textContent='Resultado anterior';}
    finally{clearTimeout(timeout);busy=false;for(const el of document.querySelectorAll('#simulation-form input,#simulation-form select,#simulation-form textarea,#simulation-form button'))el.disabled=false;$('simulate').textContent='Executar simulação';}
  });
  loadExample();
})();
