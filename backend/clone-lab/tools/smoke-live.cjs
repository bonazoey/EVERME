// Explicit small paid smoke test: one persona, ten pairs, one analysis/save, one answer.
const fs=require('node:fs'),path=require('node:path');
const base=process.env.LAB_URL||'http://127.0.0.1:8087';
async function api(route,body){const r=await fetch(base+'/api/lab'+route,{method:body===undefined?'GET':'POST',headers:body===undefined?{}:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(600000)});const data=await r.json();if(!r.ok)throw Error(route+': '+(data.message||r.status));return data;}
(async()=>{
 const report={startedAt:new Date().toISOString(),checks:[]};
 const record=(name,data)=>{report.checks.push({name,...data});console.log(name+': '+JSON.stringify(data));};
 record('models',{accessible:await api('/models/check',{})});
 const u=await api('/users',{name:'실제 연결 확인 · 민서',seed:'가상의 34세 제품 디자이너. 조용한 소규모 모임과 자전거 여행을 좋아한다. 여행 전 예산과 안전을 확인하며 주 3회 자전거를 타고, 가족과의 약속을 지키는 것을 중요하게 생각한다. 경주 자전거 여행 경험이 있다.'});
 report.userId=u.id;record('persona',{id:u.id,metrics:u.metrics});
 const prefix='/users/'+u.id;
 const b=await api(prefix+'/batches',{topic:'지난 경주 자전거 여행의 경험과 다음 주말 자전거 여행 계획, 예산·안전·동행자와의 대화'});record('generation',{batchId:b.id,pairs:b.turns.length,metrics:b.metrics});
 const before=await api(prefix+'/profile');if(before.version!==0)throw Error('Generation changed profile');
 const a=await api(prefix+'/batches/'+b.id+'/analysis',{});record('analysis',{observations:a.analysis.observations.length,memories:a.analysis.memories.length,metrics:a.analysis.metrics});
 if((await api(prefix+'/profile')).version!==0)throw Error('Analysis changed profile');
 const saved=await api(prefix+'/batches/'+b.id+'/save',{});record('save',{version:saved.version,observed:Object.values(saved.elements).filter(p=>p.score!==null).length,metrics:saved.metrics});
 if((await api(prefix+'/batches/'+b.id+'/save',{})).version!==saved.version)throw Error('Duplicate save changed version');
 const session=await api(prefix+'/sessions',{batchId:b.id});const answer=await api(prefix+'/questions',{sessionId:session.sessionId,question:'방금 기록한 자전거 여행에서 중요하게 생각한 조건은 무엇인가요?'});record('answer',{stage:answer.stage,evidenceIds:answer.evidenceIds,toolCalls:answer.trace.length,metrics:answer.metrics});
 report.completedAt=new Date().toISOString();const out=path.resolve(__dirname,'../../build/clone-lab-live-smoke.json');fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n');console.log('PASS: '+out);
})().catch(e=>{console.error(e.message);process.exitCode=1;});
