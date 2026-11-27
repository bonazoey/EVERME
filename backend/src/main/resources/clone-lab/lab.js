/* Same-origin lab client. No provider key or generation-only persona reaches this view. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id), owner = document.body.dataset.view === 'owner';
  const state = { catalog:null, user:'', batch:null, profile:null, domain:'emotion', session:null, busy:false, run:{}, timer:null };
  const esc = value => String(value ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const stages = {CONTEXT:'현재 컨텍스트', MEMORY:'장기 기억', PROFILE:'성향에 따른 추정', INSUFFICIENT:'정보 부족'};
  const number = value => value === null || value === undefined ? '미확인' : Number(value).toFixed(2).replace(/\.00$/,'');
  async function api(path, method='GET', body) {
    const response=await fetch('/api/lab'+path,{method,headers:body===undefined?{}:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
    const data=await response.json().catch(()=>({message:'서버 응답을 읽을 수 없습니다.'}));
    if(!response.ok)throw new Error(data.message || `요청 실패 (${response.status})`);return data;
  }
  const userPath=()=>'/users/'+state.user;
  function notice(text,error=false){$('notice').textContent=text;$('notice').classList.toggle('error',error);}
  function controls(){
    const running=['running','stopping'].includes(state.run.status), unfinished=state.run.mode==='single-session-pipeline-v1'&&['paused','failed'].includes(state.run.status),locked=state.busy||running||unfinished, exists=!!state.user;
    $('user').disabled=state.busy;
    if(owner){
      $('generate').disabled=locked||!exists;$('run').disabled=locked||!exists||['paused','failed'].includes(state.run.status);
      $('append').disabled=locked||!state.batch;
      $('owner-message').disabled=locked||!exists;$('owner-send').disabled=locked||!exists;
      $('analyze').disabled=locked||!state.batch||state.batch.status==='saved';$('save').disabled=locked||state.batch?.status!=='analyzed';
      $('stop').disabled=state.busy||state.run.status!=='running';$('resume').disabled=state.busy||!['paused','failed'].includes(state.run.status);
      $('export').disabled=state.busy||!exists;$('open-create').disabled=state.busy;$('batch').disabled=state.busy;$('topic').disabled=locked;
    } else {
      $('new-session').disabled=state.busy||!exists;$('batch').disabled=state.busy||!exists;$('question').disabled=state.busy||!state.session;$('send').disabled=state.busy||!state.session;
    }
  }
  async function action(label,fn){if(state.busy)return;state.busy=true;controls();notice(label);try{await fn();}catch(error){notice(error.message,true);}finally{state.busy=false;controls();}}
  async function loadUsers(selected){const users=await api('/users');$('user').innerHTML='<option value="">사용자를 선택하세요</option>'+users.map(u=>`<option value="${esc(u.id)}">${esc(u.name)}</option>`).join('');if(selected&&users.some(u=>u.id===selected)){$('user').value=selected;await selectUser(selected);}}
  async function selectUser(id){
    clearTimeout(state.timer);state.user=id;state.batch=null;state.profile=null;state.session=null;state.run={};
    if(owner){$('owner-message').value='';}
    if(!id){if(owner){$('conversation').innerHTML='';renderElements();}else{$('chat').innerHTML='';$('evidence').innerHTML='';}controls();return;}
    localStorage.setItem('everme-lab-user',id);
    if(owner){state.profile=await api(userPath()+'/profile');await loadBatches();await pollRun();renderElements();notice('주제를 입력해 대화를 생성하거나 기존 배치를 선택하세요.');}
    else {await loadBatches();const history=await api(userPath()+'/sessions/current');if(history.sessionId){state.session=history;$('batch').value=history.batchId||'';renderHistory();notice('이전 클론 대화를 불러왔습니다.');}else await newSession();$('clone-title').textContent=$('user').selectedOptions[0].textContent+'의 클론';}
    controls();
  }
  async function loadBatches(preferred){
    const batches=await api(userPath()+'/batches');const available=owner?batches:batches.filter(b=>b.status==='saved');
    const previous=preferred||state.batch?.id||$('batch').value;
    $('batch').innerHTML=(owner?'<option value="">대화 배치를 선택하세요</option>':'<option value="">배치 없이 장기 기억에서 찾기</option>')+available.map(b=>`<option value="${esc(b.id)}">#${b.sequence_no} ${esc((b.topic||'주제 미정 · 10쌍 후 자동 확정').slice(0,32))} · ${{generated:'생성됨',analyzed:'분석됨',saved:'저장됨'}[b.status]}</option>`).join('');
    if(previous&&available.some(b=>b.id===previous))$('batch').value=previous;
    else if(owner&&available.length)$('batch').value=available[0].id;
    if(owner&&$('batch').value)await loadBatch($('batch').value);else if(owner){state.batch=null;$('conversation').innerHTML='<div class="empty"><h3>아직 대화가 없습니다.</h3><p>아래에 직접 이야기를 입력하거나 10개 대화를 생성하세요.</p></div>';$('turn-count').textContent='0';}
  }
  async function loadBatch(id){state.batch=id?await api(userPath()+'/batches/'+id):null;if(state.batch)$('topic').value=state.batch.topic;renderConversation();renderElements();controls();}
  function renderConversation(){
    const turns=state.batch?.turns||[];$('turn-count').textContent=turns.length+'쌍';
    $('conversation').innerHTML=turns.map(t=>`<article class="turn user"><div class="turn-label"><span>사용자 · ${t.ordinal}</span><span>${esc(String(t.id).slice(0,8))}</span></div><div class="bubble owner">${esc(t.user_text)}</div></article><article class="turn assistant"><div class="turn-label">대화 상대</div><div class="bubble">${esc(t.assistant_text)}</div></article>`).join('');
  }
  function renderElements(){
    if(!state.catalog)return;const domain=state.catalog.domains.find(d=>d.code===state.domain),points=state.profile?.elements||{};
    const segments=state.batch?.analysis?.segments||[],draft=segments.find(s=>s.status==='analyzed'),lastSaved=segments.filter(s=>s.status==='saved').at(-1),analysis=draft?.analysis||state.batch?.analysis||{},observations=analysis.observations||[];
    const pending=!!draft||state.batch?.status==='analyzed';
    const storedCount=Object.values(points).filter(p=>p.score!==null).length;
    $('coverage').textContent=pending?`이번 분석 ${observations.length}개 · DB 저장 ${storedCount}/140개`:`DB 저장 ${storedCount}/140개`;
    $('domains').innerHTML=state.catalog.domains.map(d=>`<button type="button" data-domain="${d.code}" aria-pressed="${d.code===state.domain}">${esc(d.label)}</button>`).join('');
    const sources=Object.fromEntries((state.batch?.turns||[]).map(t=>[t.id,t.user_text]));
    $('elements').innerHTML=domain.elements.map(e=>{
      const point=points[e.code],observation=observations.find(o=>o.code===e.code),score=point?.score,reason=observation?.reason||point?.reason||'아직 관찰 근거가 없습니다.';
      const change=draft?null:(lastSaved?.changes||state.batch?.analysis?.changes)?.find(c=>c.code===e.code);
      const sourceIds=observation?.sourceIds||point?.sourceIds||[];
      const initial=score===null||score===undefined;
      const preview=pending&&!!observation,displayScore=initial?50:score;
      const barLabel=initial?'초기 기준값 · 분석 전':'DB 저장값';
      const bars=`<div class="score-comparison"><div class="score-row"><span class="score-row-label">${initial?'초기':'DB'} ${number(displayScore)}</span><div class="score-track" role="img" aria-label="${esc(barLabel)}: ${number(displayScore)}"><div class="score-fill" style="--score:${Number(displayScore)}%"></div></div></div>${preview?`<div class="score-row score-row-preview"><span class="score-row-label">저장 전 ${number(observation.score)}</span><div class="score-track" role="img" aria-label="이번 분석값 · 저장 전: ${number(observation.score)}"><div class="score-fill score-fill-preview" style="--score:${Number(observation.score)}%"></div></div></div>`:''}</div>`;
      return `<details class="element"><summary><div class="element-heading"><span>${esc(e.label)}</span><span class="numbers">${initial?'초기 기준값 50 · 분석 전':`DB 저장값 ${number(score)}`}${observation?` · 이번 분석 ${number(observation.score)}${pending?' (저장 전)':''}`:''}</span></div>${bars}</summary><div class="element-body">${preview?'<p><b>위 초록 막대는 저장값·초기 기준값, 아래 파란 막대는 저장 전 분석값입니다. 저장하면 누적값에 반영됩니다.</b></p>':''}${change?`<p><b>이 배치 저장 결과</b> · 이전 ${number(change.previous)} → 저장 ${number(change.stored)} · 변화 ${change.delta===null?'최초 확인':(change.delta>0?'+':'')+number(change.delta)}</p>`:''}<p>${esc(e.definition)}</p><p>${esc(e.boundary)}</p><div class="anchors">${Object.entries(e.anchors).map(([n,t])=>`<span><b>${n}</b><br>${esc(t)}</span>`).join('')}</div><p><b>판단 근거</b> · ${esc(reason)}</p>${sourceIds.map(id=>`<p>↳ ${esc(sources[id]||'다른 저장 배치의 근거')} <small>${esc(id)}</small></p>`).join('')}<p>반영 속도 ${{10:"빠름",20:"중간",40:"느림"}[e.updatePolicy.maxWeight]} · 상한 ${e.updatePolicy.maxWeight}</p><p>${esc(e.updatePolicy.rationale)}</p><p>저장 가중치 ${point?.weight||0}${observation?' · 이번 근거 강도 '+observation.strength:''}</p><p>제외: ${esc(e.exclude)}</p></div></details>`;
    }).join('');
  }
  async function pollRun(){
    if(!owner||!state.user)return;const user=state.user;const run=await api(userPath()+'/runs/current');if(user!==state.user)return;state.run=run;
    $('run-panel').hidden=!run.status;$('run-state').textContent=({running:'자동 테스트 진행 중',stopping:'현재 생성·분석·저장 단계 완료 후 중단합니다',paused:'자동 테스트 일시 정지',failed:'자동 테스트 실패 · 재개 가능',completed:'1000쌍 처리 완료'})[run.status]||'';
    const pipeline=run.mode==='single-session-pipeline-v1';
    $('run-count').textContent=pipeline?`생성 ${run.generatedPairs||0} · 분석 ${run.analyzedPairs||0} · 저장 ${run.savedPairs||0} / 1000쌍`:(run.completedBatches||0)*10+' / 1000쌍';$('progress').value=pipeline?(run.savedPairs||0):(run.completedBatches||0)*10;
    const phases={generating:'생성 중',waiting:'대기',completed:'완료',analyzing:'분석 중',ready_to_save:'저장 대기',saving:'저장 중'};
    $('run-metrics').textContent=`${pipeline?`생성 작업: ${phases[run.generationPhase]||run.generationPhase} · 분석 작업: ${phases[run.analysisPhase]||run.analysisPhase} · 실제 실행 ${Math.round((run.wallElapsedMs||0)/1000)}초 · `:''}LLM 호출 합계 ${Math.round((run.llmElapsedMs??run.elapsedMs??0)/1000)}초 · 입력 ${(run.inputTokens||0).toLocaleString()} / 출력 ${(run.outputTokens||0).toLocaleString()} 토큰 · 캐시 ${(run.cachedTokens||0).toLocaleString()} 토큰 · 재시도 ${run.retryCount||0}회${run.error?' · '+run.error:''}`;
    const progressKey=[user,run.runId,run.generatedPairs,run.analyzedPairs,run.savedPairs,run.status].join(':');
    if(pipeline&&state.progressKey!==progressKey){state.progressKey=progressKey;state.profile=await api(userPath()+'/profile');await loadBatches(run.batchId);renderElements();}
    controls();if(['running','stopping'].includes(run.status))state.timer=setTimeout(()=>pollRun().catch(e=>{notice(e.message,true);state.timer=setTimeout(()=>pollRun().catch(()=>{}),5000);}),2500);
    else if(run.status==='completed'||run.status==='paused'||run.status==='failed'){state.profile=await api(userPath()+'/profile');await loadBatches(run.batchId);renderElements();}
  }
  function metrics(m){return m?` · ${m.inputTokens||0} 입력 / ${m.outputTokens||0} 출력 토큰 · ${(m.elapsedMs/1000).toFixed(1)}초`:'';}
  async function newSession(){state.session=await api(userPath()+'/sessions','POST',{batchId:$('batch').value||null});renderHistory();notice('새 클론 대화를 시작했습니다.');controls();}
  function bubble(question,response){return `<article class="turn user"><div class="turn-label">정보이용자</div><div class="bubble owner">${esc(question)}</div></article><article class="turn assistant"><div class="turn-label">클론</div><div class="bubble">${esc(response.answer)}</div><p class="answer-stage">${esc(stages[response.stage]||response.stage)}${response.inferred?' · 추정한 답변':''}</p></article>`;}
  function renderHistory(){
    const messages=state.session?.messages||[];$('chat').innerHTML=messages.length?messages.map(m=>bubble(m.question,m.response)).join(''):'<div class="empty"><h3>질문을 보내 대화를 시작하세요.</h3><p>현재 컨텍스트 → 장기 기억 → 분석 프로필 순서로 확인합니다.</p></div>';
    if(messages.length)renderEvidence(messages.at(-1).response);else $('evidence').innerHTML='<p class="muted">첫 답변을 기다리고 있습니다.</p>';$('chat').scrollTop=$('chat').scrollHeight;
  }
  function renderEvidence(response){$('evidence').innerHTML=`<p><strong>${esc(stages[response.stage])}</strong></p><p>${response.inferred?'성향에 따른 추정입니다.':'기록된 근거를 사용하거나 정보 부족을 밝혔습니다.'}</p><p>${esc((response.evidenceIds||[]).join(', ')||'참조 근거 없음')}</p>${(response.trace||[]).map(t=>`<details><summary>${esc(t.tool)}</summary><pre>${esc(JSON.stringify(t,null,2))}</pre></details>`).join('')}<p class="muted">${esc(metrics(response.metrics))}</p>`;}
  $('user').addEventListener('change',()=>action('사용자를 불러오는 중…',()=>selectUser($('user').value)));
  if(owner){
    $('owner-message-form').onsubmit=event=>{event.preventDefault();const content=$('owner-message').value;if(!content.trim())return;action('메시지를 보내고 AI의 답변을 기다리는 중…',async()=>{const b=await api(userPath()+'/messages','POST',{batchId:state.batch?.id||null,topic:$('topic').value||null,content});await loadBatches(b.id);$('owner-message').value='';$('conversation').scrollTop=$('conversation').scrollHeight;notice('메시지와 AI 답변을 저장했습니다. 분석 버튼으로 직접 쓴 발언도 분석할 수 있습니다.'+metrics(b.metrics));});};
    $('owner-message').addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing&&event.keyCode!==229){event.preventDefault();if(!state.busy&&!$('owner-send').disabled)$('owner-message-form').requestSubmit();}});
    $('domains').addEventListener('click',event=>{const button=event.target.closest('[data-domain]');if(button){state.domain=button.dataset.domain;renderElements();}});
    $('batch').addEventListener('change',()=>action('대화를 불러오는 중…',async()=>{await loadBatch($('batch').value);notice('선택한 배치를 불러왔습니다.');}));
    $('open-create').onclick=()=>{$('create-notice').textContent='';$('create-dialog').showModal();};$('close-create').onclick=()=>$('create-dialog').close();
    $('create-form').onsubmit=event=>{event.preventDefault();$('create').disabled=true;$('create-notice').classList.remove('error');$('create-notice').textContent='고정 인물과 140개 정답 요소를 만드는 중입니다…';action('고정 가상 인물과 140개 정답 요소를 만드는 중…',async()=>{try{const user=await api('/users','POST',{name:$('name').value,seed:$('seed').value});$('create-dialog').close();await loadUsers(user.id);notice('가상 인물을 만들었습니다. 대화 주제를 입력하세요.'+metrics(user.metrics));}catch(error){$('create-notice').textContent=error.message;$('create-notice').classList.add('error');throw error;}finally{$('create').disabled=false;}});};
    $('generate').onclick=()=>action('10쌍의 대화를 생성하는 중… 분석은 실행하지 않습니다.',async()=>{const b=await api(userPath()+'/batches','POST',{topic:$('topic').value});await loadBatches(b.id);notice('10쌍을 생성했습니다. 분석 버튼으로 분석을 시작하세요.'+metrics(b.metrics));});
    $('append').onclick=()=>action('같은 주제로 대화 10쌍을 이어서 생성하는 중…',async()=>{const b=await api(userPath()+'/batches/'+state.batch.id+'/turns','POST');await loadBatches(b.id);$('conversation').scrollTop=$('conversation').scrollHeight;notice(`‘${b.topic}’ 주제로 10쌍을 추가했습니다. 총 ${b.turns.length}쌍입니다. 다시 분석해 주세요. 기존 DB 저장값은 유지됩니다.`+metrics(b.metrics));});
    $('analyze').onclick=()=>action('사용자 발언을 근거로 분석하는 중…',async()=>{const b=await api(userPath()+'/batches/'+state.batch.id+'/analysis','POST');await loadBatches(b.id);notice('분석을 완료했습니다. 아직 프로필에는 반영하지 않았습니다.'+metrics(b.analysis?.metrics));});
    $('save').onclick=()=>action('기존 데이터와 관찰값을 서버에서 계산·검증한 뒤 저장하는 중…',async()=>{state.profile=await api(userPath()+'/batches/'+state.batch.id+'/save','POST');await loadBatches(state.batch.id);renderElements();notice('프로필을 갱신하고 기억을 저장했습니다.'+metrics(state.profile.metrics));});
    $('run').onclick=()=>action('1000쌍 자동 테스트를 시작하는 중…',async()=>{state.run=await api(userPath()+'/runs','POST',{topic:$('topic').value});await pollRun();notice('한 대화에 이어서 생성하며 10쌍마다 분석·저장합니다.');});
    $('stop').onclick=()=>action('중단을 요청하는 중…',async()=>{state.run=await api(userPath()+'/runs/current/stop','POST');notice('현재 배치가 완료되면 중단합니다.');});
    $('resume').onclick=()=>action('자동 테스트를 재개하는 중…',async()=>{state.run=await api(userPath()+'/runs/current/resume','POST');await pollRun();notice('마지막 미완료 배치부터 이어갑니다.');});
    $('export').onclick=()=>action('결과를 내보내는 중…',async()=>{const result=await api(userPath()+'/export');const url=URL.createObjectURL(new Blob([JSON.stringify(result,null,2)],{type:'application/json'}));const link=document.createElement('a');link.href=url;link.download='everme-lab-'+state.user+'.json';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);notice('실험 결과와 평가 지표를 내보냈습니다.');});
  }else{
    $('new-session').onclick=()=>action('새 대화를 준비하는 중…',newSession);$('batch').onchange=()=>action('컨텍스트를 변경해 새 대화를 준비하는 중…',newSession);
    $('question-form').onsubmit=event=>{event.preventDefault();const question=$('question').value;action('클론이 기억과 근거를 확인하고 있습니다…',async()=>{await api(userPath()+'/questions','POST',{sessionId:state.session.sessionId,question});state.session=await api(userPath()+'/sessions/current');$('question').value='';renderHistory();notice('답변을 받았습니다. 근거와 추정 여부를 확인하세요.');});};
  }
  action('실험 환경을 불러오는 중…',async()=>{const [catalog,config]=await Promise.all([api('/catalog'),api('/config')]);state.catalog=catalog;$('model-info').textContent=owner?`생성 ${config.generation} · 분석 ${config.analysis}`:`답변 ${config.answer}`;if(owner)renderElements();await loadUsers(localStorage.getItem('everme-lab-user'));if(!config.keyConfigured)notice('OPENAI_API_KEY를 설정한 뒤 서버를 다시 시작해 주세요.',true);else if(!state.user)notice('새 가상 인물을 만들거나 기존 사용자를 선택하세요.');});
})();
