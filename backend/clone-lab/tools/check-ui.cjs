// Browser UI contract check with mocked lab JSON only; never triggers paid model calls.
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict'), { spawn } = require('node:child_process');
const base = process.env.LAB_URL || 'http://127.0.0.1:8087', out = path.resolve(__dirname, '../../build/clone-lab-ui'); fs.mkdirSync(out, { recursive: true });
const catalog = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../src/main/resources/clone-lab/catalog.json'), 'utf8'));
const user = '00000000-0000-0000-0000-000000000001', batch = '00000000-0000-0000-0000-000000000002', session = '00000000-0000-0000-0000-000000000003';
let mockRun={},pipelineSegments=null;
let generated = false, analyzed = false, saved = false, profileStored = false, pairCount = 10, questions = [], version = 0;
const profile = () => ({ userId: user, version, name: '민서', elements: Object.fromEntries(catalog.domains.flatMap(d => d.elements).map(e => [e.code, { score: profileStored && e.code === 'relations.social_orientation' ? 20 : null, weight: profileStored ? 2 : 0, sourceIds: [], reason: profileStored ? '소규모 모임을 선호한다고 직접 설명했습니다.' : '' }])) });
const observation = { code: 'relations.social_orientation', score: 20, strength: 2, sourceIds: ['source-1'], reason: '낯선 큰 모임보다 조용한 소규모 모임에서 편안함을 느낍니다.' };
const data = () => ({ id: batch, topic: '주말 자전거 여행', sequence_no: 1, status: saved ? 'saved' : analyzed ? 'analyzed' : 'generated', turns: Array.from({ length: pairCount }, (_, i) => ({ id: 'source-' + (i + 1), ordinal: i + 1, user_text: i === 0 ? '큰 모임보다 가까운 친구 둘과 조용히 여행하는 게 좋아요. 낯선 사람에게 먼저 말을 걸지는 않는 편이에요.' : '여행 전에 자전거 상태와 예산을 확인했어요. 주말에는 강변을 따라 달릴 생각이에요.', assistant_text: '어떤 코스로 떠나고 싶으세요?' })), analysis: pipelineSegments?{segments:pipelineSegments}:analyzed ? { observations: [observation], memories: [] } : null });
const answer = { answer: '기록을 보면 가까운 친구들과 조용한 여행을 더 편안하게 느끼는 것 같아요. 이번 선택도 소규모 여행 쪽으로 기울 수 있지만, 실제 의사는 확인해야 해요.', stage: 'PROFILE', inferred: true, evidenceIds: ['relations.social_orientation'], trace: [{ tool: 'searchLongTermMemory', arguments: { query: '여행 동행' }, result: [] }, { tool: 'getAnalysisProfile', arguments: { codes: ['relations.social_orientation'] }, result: { 'relations.social_orientation': { score: 20 } } }], metrics: { inputTokens: 250, outputTokens: 100, elapsedMs: 1200 } };
function mock(route, method, body) {
    if (route === '/catalog') return catalog; if (route === '/config') return { generation: 'gpt-6.1-sol', analysis: 'gpt-6.1-sol', answer: 'gpt-6.1-terra', keyConfigured: true }; if (route === '/users') return [{ id: user, name: '민서' }];
    if (route.endsWith('/profile')) return profile(); if (route.endsWith('/runs/current')) return mockRun;
    if (route.endsWith('/sessions/current')) return { sessionId: session, batchId: null, messages: questions, version };
    if (route.endsWith('/sessions')) { questions = []; return { sessionId: session, messages: [], version: ++version }; }
    if (route.endsWith('/questions')) { questions.push({ question: JSON.parse(body).question, response: answer }); return answer; }
    if (route.endsWith('/messages')) { const request = JSON.parse(body); pairCount = request.batchId ? pairCount + 1 : 1; generated = true; analyzed = false; saved = false; return data(); }
    if (route.endsWith('/turns')) { pairCount += 10; analyzed = false; saved = false; return data(); }
    if (route.endsWith('/analysis')) { analyzed = true; return data(); } if (route.endsWith('/save')) { saved = true; profileStored = true; return profile(); }
    if (route.endsWith('/batches')) { if (method === 'POST') { generated = true; return data(); } return generated ? [data()] : []; }
    if (route.endsWith('/batches/' + batch)) return data(); throw Error('Unhandled mocked route ' + route);
}
(async () => {
    const chrome = spawn('C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', '--no-first-run', '--disable-gpu', '--remote-debugging-port=0', '--user-data-dir=' + path.join(out, 'chrome-profile'), 'about:blank'], { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] }); let ws;
    try {
        const endpoint = await new Promise((resolve, reject) => { let data = ''; const timer = setTimeout(() => reject(Error('Chrome timeout')), 20000); chrome.stderr.on('data', d => { data += d; const m = data.match(/DevTools listening on (ws:\/\/[^\s]+)/); if (m) { clearTimeout(timer); resolve(m[1]); } }); chrome.on('error', reject); });
        const targets = await (await fetch('http://127.0.0.1:' + new URL(endpoint).port + '/json/list')).json(); ws = new WebSocket(targets.find(t => t.type === 'page').webSocketDebuggerUrl); await new Promise(r => ws.addEventListener('open', r, { once: true }));
        let id = 0; const pending = new Map(), errors = [];
        const call = (method, params = {}) => new Promise((resolve, reject) => { const n = ++id; pending.set(n, { resolve, reject }); ws.send(JSON.stringify({ id: n, method, params })); });
        ws.addEventListener('message', async event => {
            const m = JSON.parse(event.data); if (m.id) { const p = pending.get(m.id); if (p) { pending.delete(m.id); m.error ? p.reject(m.error) : p.resolve(m.result); } } else if (m.method === 'Runtime.exceptionThrown') errors.push(m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text); else if (m.method === 'Fetch.requestPaused') {
                try { const req = m.params.request, route = new URL(req.url).pathname.replace('/api/lab', ''); const result = mock(route, req.method, req.postData); await call('Fetch.fulfillRequest', { requestId: m.params.requestId, responseCode: 200, responseHeaders: [{ name: 'Content-Type', value: 'application/json' }], body: Buffer.from(JSON.stringify(result)).toString('base64') }); } catch (e) { errors.push(e.message); await call('Fetch.failRequest', { requestId: m.params.requestId, errorReason: 'Failed' }); }
            }
        });
        await call('Runtime.enable'); await call('Page.enable'); await call('Fetch.enable', { patterns: [{ urlPattern: '*/api/lab/*' }] });
        const evaluate = async expression => (await call('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })).result.value;
        async function wait(expression) { for (let i = 0; i < 150; i++) { if (await evaluate(expression)) return; await new Promise(r => setTimeout(r, 50)); } throw Error('UI timeout: ' + expression); }
        async function viewport(width) { await call('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: width < 760 }); }
        async function shot(name) { const png = await call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true }); fs.writeFileSync(path.join(out, name + '.png'), Buffer.from(png.data, 'base64')); }
        await viewport(1440); await call('Page.navigate', { url: base + '/lab' }); await wait('document.querySelectorAll("#user option").length===2 && !document.querySelector("#user").disabled');
        await evaluate(`document.getElementById('user').value='${user}';document.getElementById('user').dispatchEvent(new Event('change'))`); await wait('!document.querySelector("#generate").disabled');
        assert.equal(await evaluate('document.querySelectorAll(".element").length'), 20);
        assert(await evaluate('document.querySelector(".element .numbers").textContent.includes("초기 기준값 50 · 분석 전")'));
        assert.equal(await evaluate('document.querySelector(".element .score-fill").style.getPropertyValue("--score")'),'50%');
        await evaluate('document.getElementById("topic").value="주말 자전거 여행";document.getElementById("generate").click()'); await wait('!document.getElementById("analyze").disabled'); assert.equal(analyzed, false); assert.equal(saved, false);
        await evaluate('document.getElementById("append").click()'); await wait('document.getElementById("turn-count").textContent==="20쌍" && !document.getElementById("append").disabled');
        assert.equal(await evaluate('document.querySelectorAll("#conversation .turn").length'), 40); assert.equal(analyzed, false); assert.equal(saved, false);
        await evaluate('document.getElementById("analyze").click()'); await wait('!document.getElementById("save").disabled'); assert.equal(saved, false);
        assert(await evaluate('document.getElementById("batch").selectedOptions[0].textContent.includes("분석됨")'));
        await evaluate('document.querySelector("[data-domain=relations]").click()');
        assert(await evaluate('document.getElementById("coverage").textContent.includes("이번 분석 1개") && document.getElementById("coverage").textContent.includes("DB 저장 0/140개")'));
        assert(await evaluate('document.querySelector(".element .numbers").textContent.includes("초기 기준값 50 · 분석 전") && document.querySelector(".element .numbers").textContent.includes("20 (저장 전)")'));
        assert.equal(await evaluate('document.querySelector(".element .score-fill").style.getPropertyValue("--score")'), '50%');
        assert.equal(await evaluate('document.querySelector(".element .score-fill-preview").style.getPropertyValue("--score")'), '20%');
        assert.equal(await evaluate('document.querySelectorAll(".element:first-child .score-row").length'),2);
        assert.equal(await evaluate('getComputedStyle(document.querySelector(".element .score-fill-preview")).backgroundColor'),'rgb(57, 117, 184)');
        await shot('owner-analysis-preview');
        for (const width of [390, 320]) { await viewport(width); assert(await evaluate('document.documentElement.scrollWidth<=innerWidth+1'), 'Preview overflow ' + width); }
        await viewport(1440);
        await evaluate('document.getElementById("save").click()'); await wait('document.getElementById("coverage").textContent==="DB 저장 1/140개" && !document.getElementById("generate").disabled');
        assert(await evaluate('!document.querySelector(".element .numbers").textContent.includes("저장 전")'));
        assert(await evaluate('!document.querySelector(".element .score-fill").classList.contains("score-fill-preview")'));
        assert.equal(await evaluate('document.querySelectorAll(".element:first-child .score-row").length'),1);
        await evaluate('document.getElementById("append").click()'); await wait('document.getElementById("turn-count").textContent==="30쌍" && !document.getElementById("append").disabled');
        assert(await evaluate('document.getElementById("save").disabled')); assert.equal(profileStored, true);
        await evaluate('document.getElementById("analyze").click()'); await wait('!document.getElementById("save").disabled');
        await evaluate('document.getElementById("save").click()'); await wait('!document.getElementById("append").disabled');
        await evaluate('document.getElementById("owner-message").value="직접 입력한 대화입니다.";document.getElementById("owner-message-form").requestSubmit()');
        await wait('document.querySelectorAll("#conversation .turn").length===62 && !document.getElementById("owner-send").disabled');
        assert.equal(await evaluate('document.getElementById("owner-message").value'), '');
        assert.equal(analyzed, false); assert.equal(saved, false); assert.equal(profileStored, true);
        assert(await evaluate('document.getElementById("save").disabled'));
        assert(await evaluate('const event=new KeyboardEvent("keydown",{key:"Enter",shiftKey:true,bubbles:true,cancelable:true});document.getElementById("owner-message").dispatchEvent(event);!event.defaultPrevented'));
        await evaluate('document.getElementById("owner-message").value="추가 메시지입니다.";document.getElementById("owner-message").dispatchEvent(new KeyboardEvent("keydown",{key:"Enter",bubbles:true,cancelable:true}))');
        await wait('document.querySelectorAll("#conversation .turn").length===64 && !document.getElementById("owner-send").disabled');
        await evaluate('document.getElementById("analyze").click()'); await wait('!document.getElementById("save").disabled');
        await evaluate('document.getElementById("save").click()'); await wait('!document.getElementById("append").disabled');
        await evaluate('document.querySelector("[data-domain=relations]").click();document.querySelector(".element").open=true');
        assert(await evaluate('document.querySelector(".element .element-body").textContent.includes("반영 속도 느림 · 상한 40")'));
        assert(await evaluate('document.querySelector(".element .element-body").textContent.includes("저장 가중치")'));
        assert.equal(saved,true);assert.equal(profileStored,true);
        await shot('owner-desktop');
        for (const width of [390, 320]) { await viewport(width); assert(await evaluate('document.documentElement.scrollWidth<=innerWidth+1'), 'Owner overflow ' + width); await shot('owner-' + width); }
        mockRun={mode:'single-session-pipeline-v1',status:'paused',runId:'test-run',batchId:batch,generatedPairs:20,analyzedPairs:10,savedPairs:0,generationPhase:'waiting',analysisPhase:'ready_to_save',wallElapsedMs:30000,llmElapsedMs:45000,cachedTokens:100,retryCount:2};pipelineSegments=[{number:1,from:1,to:10,status:'analyzed',analysis:{observations:[observation],memories:[]}}];
        await evaluate('document.getElementById("user").dispatchEvent(new Event("change"))');await wait('!document.getElementById("resume").disabled');
        assert(await evaluate('document.getElementById("run-count").textContent.includes("생성 20 · 분석 10 · 저장 0")'));
        assert.equal(await evaluate('document.getElementById("progress").value'),0);
        assert(await evaluate('document.getElementById("run-metrics").textContent.includes("실제 실행 30초") && document.getElementById("run-metrics").textContent.includes("LLM 호출 합계 45초")'));
        await evaluate('document.querySelector("[data-domain=relations]").click()');assert(await evaluate('!!document.querySelector(".score-fill-preview") && document.getElementById("save").disabled && document.getElementById("append").disabled'));
        for(const width of [1440,320]){await viewport(width);assert(await evaluate('document.documentElement.scrollWidth<=innerWidth+1'));await shot('pipeline-'+width);}
        mockRun={};pipelineSegments=null;
        await viewport(1440); await call('Page.navigate', { url: base + '/lab/viewer' }); await wait('document.querySelector("#send") && !document.getElementById("send").disabled');
        await evaluate('document.getElementById("question").value="다음 여행도 소규모로 갈 것 같나요?";document.getElementById("question-form").requestSubmit()'); await wait('document.querySelector("#chat .answer-stage") && !document.getElementById("send").disabled');
        assert(await evaluate('document.getElementById("chat").textContent.includes("성향에 따른 추정")')); assert.equal(await evaluate('document.querySelectorAll("#evidence details").length'), 2); await shot('viewer-desktop');
        for (const width of [390, 320]) { await viewport(width); assert(await evaluate('document.documentElement.scrollWidth<=innerWidth+1'), 'Viewer overflow ' + width); await shot('viewer-' + width); }
        await evaluate('document.getElementById("new-session").click()'); await wait('!document.getElementById("new-session").disabled && !document.querySelector("#chat .answer-stage")');
        assert.deepEqual(errors, []); console.log('PASS: manual generate/analyze/save separation, 140-element view, viewer question/new session, desktop/390/320px, no JS errors.');
    } finally { if (ws) ws.close(); chrome.kill(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
