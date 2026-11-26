const fs=require('node:fs');
const path=require('node:path');
const {spawn}=require('node:child_process');
const {pathToFileURL}=require('node:url');
const assert=require('node:assert/strict');

(async()=>{
  const dir=__dirname,qa=path.join(dir,'.qa');fs.mkdirSync(qa,{recursive:true});
  const fragment=fs.readFileSync(path.join(dir,'everme-data-architecture.html'),'utf8');
  assert(!/<(?:!doctype|html|head|body)[\s>]/i.test(fragment));
  const scripts=[...fragment.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m=>m[1]);
  scripts.forEach(s=>new (require('node:vm').Script)(s));
  const style=`:root{--font-size-base:14px;--background:#fff;--foreground:#18212b;--card:#fff;--card-foreground:#18212b;--muted:#f0f3f5;--muted-foreground:#526273;--accent:#e1eef5;--border:#b8c5ce;--primary:#18212b;--primary-foreground:#fff;}*{box-sizing:border-box}body{font:14px/1.5 system-ui,sans-serif;margin:0;padding:16px;background:var(--background);color:var(--foreground)}h2,h3,p{margin:0;font-size:inherit;font-weight:500}button{font:inherit}.btn{border:1px solid var(--border);border-radius:5px;background:var(--background);color:var(--foreground);padding:8px 12px;cursor:pointer}.btn[aria-pressed=true]{background:var(--primary);color:var(--primary-foreground)}.table{width:100%;border-collapse:collapse}.table td,.table th{text-align:left;padding:8px;border-bottom:1px solid var(--border);font-weight:400}.table-responsive{overflow-x:auto;max-width:100%}.text-small{font-size:12px}.viz-row{display:flex;gap:16px;flex-wrap:wrap}.viz-badge{font-size:12px}a{color:inherit}pre{padding:12px}`;
  const preview=path.join(qa,'preview.html');fs.writeFileSync(preview,'<!doctype html><meta charset="utf-8"><style>'+style+'</style>'+fragment);
  const chrome=spawn('C:/Program Files/Google/Chrome/Application/chrome.exe',['--headless=new','--no-first-run','--no-default-browser-check','--disable-gpu','--remote-debugging-port=0','--user-data-dir='+path.join(qa,'chrome-profile'),'about:blank'],{windowsHide:true,stdio:['ignore','ignore','pipe']});
  let ws;
  try{
    const endpoint=await new Promise((resolve,reject)=>{let s='';const timer=setTimeout(()=>reject(new Error('Chrome startup timeout')),20000);chrome.stderr.on('data',d=>{s+=d;const m=s.match(/DevTools listening on (ws:\/\/[^\s]+)/);if(m){clearTimeout(timer);resolve(m[1]);}});chrome.on('error',reject);});
    const port=new URL(endpoint).port;
    const targets=await(await fetch('http://127.0.0.1:'+port+'/json/list')).json();
    ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);
    await new Promise((r,j)=>{ws.addEventListener('open',r,{once:true});ws.addEventListener('error',j,{once:true});});
    let id=0;const pending=new Map(),errors=[];
    ws.addEventListener('message',e=>{const m=JSON.parse(e.data);if(m.id){const p=pending.get(m.id);if(p){pending.delete(m.id);m.error?p.reject(m.error):p.resolve(m.result);}}else if(m.method==='Runtime.exceptionThrown')errors.push(m.params.exceptionDetails.text+' '+(m.params.exceptionDetails.exception?.description||''));});
    const call=(method,params={})=>new Promise((resolve,reject)=>{const n=++id;pending.set(n,{resolve,reject});ws.send(JSON.stringify({id:n,method,params}));});
    await call('Runtime.enable');await call('Page.enable');
    const evaluate=async expression=>{const r=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;};
    await call('Page.navigate',{url:pathToFileURL(preview).href});
    for(let i=0;i<30;i++){if(await evaluate('!!document.querySelector("#ev-diagram a[data-table]")'))break;await new Promise(r=>setTimeout(r,100));}
    const report=[];
    for(const width of [1024,320]){
      await call('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:false});
      await evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))');
      for(const view of ['all','sync','etl','kinds','merge','rag','classes','api','research']){
        await evaluate(`document.querySelector('[data-view="${view}"]').click();new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))`);
        const state=await evaluate(`({overflow:document.documentElement.scrollWidth>innerWidth,nodes:document.querySelectorAll('#ev-diagram a[data-table]').length,api:!document.querySelector('#ev-api-panel').hidden,research:!document.querySelector('#ev-research-panel').hidden,pressed:document.querySelector('[data-view="${view}"]').getAttribute('aria-pressed')})`);
        assert.equal(state.overflow,false,view+' overflow at '+width);assert.equal(state.pressed,'true');
        if(view==='api')assert(state.api);else if(view==='research')assert(state.research);else if(view==='classes')assert((await evaluate('document.querySelector("#ev-classes-panel").textContent')).includes('KnowledgeMergeService'));else assert(state.nodes>0);
        report.push({width,view,...state});
      }
      await evaluate(`document.querySelector('[data-view="rag"]').click();document.querySelector('[data-table="knowledge_embeddings"]').dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))`);
      assert((await evaluate('document.querySelector("#ev-detail").textContent')).includes('embedding_model'));
      assert((await evaluate('document.querySelector("#ev-operations").textContent')).includes('JOIN knowledge_versions'));
      if(width===1024){const metrics=await call('Page.getLayoutMetrics');const shot=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:true,clip:{x:0,y:0,width:width,height:Math.min(metrics.cssContentSize.height,6000),scale:1}});fs.writeFileSync(path.join(qa,'desktop.png'),Buffer.from(shot.data,'base64'));}
    }
    await evaluate(`document.documentElement.style.setProperty('--background','#13191f');document.documentElement.style.setProperty('--foreground','#eef2f5');document.documentElement.style.setProperty('--muted-foreground','#b2c3d0');document.documentElement.style.setProperty('--border','#53616c');document.documentElement.style.setProperty('--accent','#2c4250');document.querySelector('[data-view="etl"]').click();new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))`);
    const shot=await call('Page.captureScreenshot',{format:'png'});fs.writeFileSync(path.join(qa,'mobile-dark.png'),Buffer.from(shot.data,'base64'));
    assert.deepEqual(errors,[]);fs.writeFileSync(path.join(qa,'checks.json'),JSON.stringify({report,errors},null,2));
    console.log('PASS: 9 views × 2 widths, class contracts, node details, SQL, no page overflow, no JS exceptions.');
  }finally{if(ws)ws.close();chrome.kill();}
})().catch(e=>{console.error(e);process.exitCode=1;});
