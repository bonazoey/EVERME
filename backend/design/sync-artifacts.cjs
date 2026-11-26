// Regenerate design contracts and the existing architecture artifact; no app Java/DB writes.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {Parser,ModelExporter}=require('@dbml/core');
const root=path.resolve(__dirname,'../..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const write=(p,s)=>{fs.mkdirSync(path.dirname(path.join(root,p)),{recursive:true});fs.writeFileSync(path.join(root,p),s.trimEnd()+'\n');};
const json=(p,x)=>write(p,JSON.stringify(x,null,2));
const db=Parser.parse(read('backend/design/everme.dbml'),'dbmlv2');
const schema=db.export().schemas[0],tables=schema.tables;
if(tables.length!==30)throw Error('Expected 29 core + 1 group table');
write('output/architecture/.qa/dbml-generated.sql',ModelExporter.export(db,'postgres'));
const javaDir=path.join(root,'backend/src/main/java/com/example/everme/controller');
const runtime=[];
for(const file of fs.readdirSync(javaDir).filter(f=>f.endsWith('.java'))){
  const s=fs.readFileSync(path.join(javaDir,file),'utf8'),base=s.match(/@RequestMapping\(value = "([^"]*)"/)[1];
  for(const m of s.matchAll(/\/\*\* ([^\n]*?) \*\/\r?\n\s+@(Get|Post|Patch|Put|Delete)Mapping\(([^\n]*)\)\r?\n\s+public ResponseEntity<\?> (\w+)\(([^\n]*)\) \{\s+return null;/g)){
    runtime.push({controller:file.replace('.java',''),method:m[2].toUpperCase(),path:base+m[3].match(/"([^"]*)"/)[1],description:m[1],handler:m[4],params:m[5],status:'stub-return-null'});
  }
}
if(runtime.length!==58)throw Error('Existing routes changed unexpectedly: '+runtime.length);
const target=runtime.map(r=>({...r}));
target.find(r=>r.path==='/api/me/analysis'&&r.method==='GET').status='existing-route-proposed-kind-filter';
target.push({controller:'AnalysisController',method:'GET',path:'/api/me/analysis/summary',description:'본인 지식 5종별 항목 수·충돌·분석/임베딩 대기 현황',handler:'summary',params:'',status:'design-only-new-route'});
const kinds=['FACT','MEMORY','VALUE','PREFERENCE','BEHAVIOR'];
const type=t=>{
  t=t.trim();
  if(t.includes('List<'))return {type:'array',items:{$ref:'#/components/schemas/'+t.match(/<([^>]+)>/)[1]}};
  if(t.includes('UUID'))return {type:'string',format:'uuid'};
  if(t.includes('LocalDate'))return {type:'string',format:'date'};
  if(t==='Boolean')return {type:'boolean'};
  if(t==='Integer'||t==='int')return {type:'integer'};
  return {type:'string'};
};
const requestSchemas={};
for(const m of read('backend/src/main/java/com/example/everme/dto/EndpointRequests.java').matchAll(/public record (\w+)\(([^)]*)\)/g)){
  const props={};for(const f of m[2].split(/,\s*/)){const parts=f.trim().split(/\s+/);const name=parts.pop();props[name]=type(parts.join(' '));if(/password|token/i.test(name))props[name].writeOnly=true;if(name==='email')props[name].format='email';if(name==='maxAccessLevel')Object.assign(props[name],{minimum:1,maximum:4});}
  requestSchemas[m[1]]={type:'object',properties:props,additionalProperties:false};
}
const schemas={...requestSchemas,
  KnowledgeKind:{type:'string',enum:kinds},
  AnalysisListItem:{type:'object',required:['informationId','kind','latestVersionId','versionNo','content','sensitivity','recordedAt','hasConflict'],properties:{informationId:type('UUID'),kind:{$ref:'#/components/schemas/KnowledgeKind'},latestVersionId:type('UUID'),versionNo:{type:'integer',minimum:1},content:type('String'),sensitivity:{type:'string',enum:['L1','L2','L3','L4']},recordedAt:{type:'string',format:'date-time'},hasConflict:{type:'boolean'}}},
  AnalysisListResponse:{type:'object',required:['items','page','size','totalElements'],properties:{items:{type:'array',items:{$ref:'#/components/schemas/AnalysisListItem'}},page:{type:'integer',minimum:0},size:{type:'integer',minimum:1,maximum:100},totalElements:{type:'integer',minimum:0}}},
  AnalysisSummaryResponse:{type:'object',required:['totalItems','countsByKind','openConflictCount','analysisPendingCount','embeddingPendingCount','lastAnalyzedAt'],properties:{totalItems:{type:'integer',minimum:0},countsByKind:{type:'object',required:kinds,properties:Object.fromEntries(kinds.map(k=>[k,{type:'integer',minimum:0}])),additionalProperties:false},openConflictCount:{type:'integer',minimum:0},analysisPendingCount:{type:'integer',minimum:0},embeddingPendingCount:{type:'integer',minimum:0},lastAnalyzedAt:{type:['string','null'],format:'date-time'}}},
  AnalysisDetailResponse:{type:'object',required:['informationId','kind','occurrenceCount','changeCount','versions'],properties:{informationId:type('UUID'),kind:{$ref:'#/components/schemas/KnowledgeKind'},occurrenceCount:{type:'integer',minimum:0},changeCount:{type:'integer',minimum:0},versions:{type:'array',items:{$ref:'#/components/schemas/KnowledgeVersionResponse'}}}},
  KnowledgeVersionResponse:{type:'object',required:['versionId','versionNo','content','sensitivity','recordedAt','detail','sources','hasConflict'],properties:{versionId:type('UUID'),versionNo:{type:'integer',minimum:1},content:type('String'),sensitivity:{type:'string',enum:['L1','L2','L3','L4']},occurredFrom:{type:['string','null'],format:'date-time'},occurredUntil:{type:['string','null'],format:'date-time'},recordedAt:{type:'string',format:'date-time'},supersededAt:{type:['string','null'],format:'date-time'},detail:{oneOf:kinds.map(k=>({$ref:'#/components/schemas/'+k+'DetailResponse'}))},sources:{type:'array',items:{type:'object',required:['messageRevisionId','excerpt'],properties:{messageRevisionId:type('UUID'),excerpt:type('String')}}},hasConflict:{type:'boolean'}}},
  ErrorResponse:{type:'object',required:['code','message'],properties:{code:type('String'),message:type('String')},description:'목표 오류 계약. 현재 Java에는 별도 예외/오류 처리가 없음.'}
};
const requiredRequests={SignupRequest:['name','email','password','consents'],ConsentSelection:['consentType','documentVersion','agreed'],LoginRequest:['email','password'],PasswordResetRequest:['email'],PasswordChangeRequest:['token','newPassword'],ViewerConversationRequest:['cloneId'],TitleRequest:['title'],MessageRequest:['content'],InvitationRequest:['email','maxAccessLevel'],AccessRequest:['maxAccessLevel'],GroupRequest:['name'],InvitationResponseRequest:['decision'],MembershipRequest:['groupId']};
for(const [name,required]of Object.entries(requiredRequests))schemas[name].required=required;
schemas.ConsentSelection.properties.agreed.const=true;
schemas.SignupRequest.properties.consents.minItems=1;
schemas.MembershipRequest.properties.groupId.type=['string','null'];
schemas.InvitationResponseRequest.properties.decision.enum=['accepted','declined'];
const camel=s=>s.replace(/_([a-z])/g,(_,c)=>c.toUpperCase());
const mapNames={value_ciphertext:'value',location_ciphertext:'location',participants_ciphertext:'participants',principle_ciphertext:'principle',scope_ciphertext:'scope',target_ciphertext:'target',context_ciphertext:'context',trigger_ciphertext:'trigger'};
for(const k of kinds){const table=tables.find(t=>t.name===k.toLowerCase()+'_details'),properties={},required=[];for(const f of table.fields.filter(f=>!['version_id','kind'].includes(f.name))){const name=mapNames[f.name]??camel(f.name);const integer=/smallint|int/.test(f.type.type_name);properties[name]={type:f.not_null?(integer?'integer':'string'):[integer?'integer':'string','null']};if(f.not_null)required.push(name);}schemas[k+'DetailResponse']={type:'object',required,properties,additionalProperties:false,description:k+' 형식. 상위 kind와 일치하는 분류의 상세만 반환. DB ciphertext는 복호화한 표시값으로 매핑.'};}
const openapi={openapi:'3.1.0',info:{title:'EVERME backend design contract',version:'2026-11-26-knowledge-v2',description:'설계 목표 59개 경로 계약 / 실제 58개 null 골격. 신규 summary와 kind 필터는 DESIGN ONLY. 기존 Java와 EndpointContractTests는 수정하지 않았다. 나머지 응답 객체는 후속 DTO 설계가 필요하다.'},'x-runtime-route-count':58,'x-target-route-count':59,'x-http-domain-count':9,servers:[{url:'http://localhost:8080'}],paths:{},components:{schemas,securitySchemes:{bearerAuth:{type:'http',scheme:'bearer'}}},security:[{bearerAuth:[]}]};
for(const r of target){
  const op={tags:[r.controller],summary:r.description,operationId:r.controller+'_'+r.handler,'x-implementation-status':r.status,parameters:[],responses:{'200':{description:'설계 목표 응답. 현재 null 골격의 빈 응답과 구분; 최종 DTO 미확정.'},'400':{description:'목표 입력 오류',content:{'application/json':{schema:{$ref:'#/components/schemas/ErrorResponse'}}}},'401':{description:'인증 필요'},'403':{description:'권한 없음'}}};
  for(const p of r.path.matchAll(/\{(\w+)\}/g))op.parameters.push({name:p[1],in:'path',required:true,schema:p[1]==='provider'?{type:'string'}:{type:'string',format:'uuid'}});
  for(const p of r.params.matchAll(/@RequestParam(?:\(([^)]*)\))?\s+(int|String|UUID)\s+(\w+)/g)){
    const settings=p[1]??'',schema=type(p[2]),def=settings.match(/defaultValue = "([^"]*)"/)?.[1];
    if(def!==undefined)schema.default=p[2]==='int'?Number(def):def;
    if(p[3]==='page')schema.minimum=0;if(p[3]==='size')Object.assign(schema,{minimum:1,maximum:100});
    op.parameters.push({name:settings.match(/^"([^"]*)"/)?.[1]??p[3],in:'query',required:def===undefined&&!settings.includes('required = false'),schema});
  }
  const body=r.params.match(/@RequestBody (\w+)/)?.[1];
  if(body)op.requestBody={required:true,content:{'application/json':{schema:{$ref:'#/components/schemas/'+body}}}};
  if(r.params.includes('MultipartFile'))op.requestBody={required:true,content:{'multipart/form-data':{schema:{type:'object',required:['file'],properties:{file:{type:'string',format:'binary'}}}}}};
  if(r.path==='/api/me/analysis'){op.parameters.push({name:'kind',in:'query',required:false,schema:{$ref:'#/components/schemas/KnowledgeKind'},description:'新增設計. 미지정 전체; 현 Java에는 아직 바인딩되지 않음.'.replace('新增設計','신규 설계')});op.responses['200']={description:'인증 owner의 항목 목록',content:{'application/json':{schema:{$ref:'#/components/schemas/AnalysisListResponse'}}}};}
  if(r.path==='/api/me/analysis/summary')op.responses['200']={description:'분류별 항목 및 작업 현황',content:{'application/json':{schema:{$ref:'#/components/schemas/AnalysisSummaryResponse'}}}};
  if(r.path==='/api/me/analysis/{informationId}'){op.responses['200']={description:'지식 버전·정확한 상세·근거',content:{'application/json':{schema:{$ref:'#/components/schemas/AnalysisDetailResponse'}}}};op.responses['404']={description:'없거나 다른 소유자의 항목'};}
  if((r.controller==='AuthController'&&r.handler!=='logout')||r.controller==='ConsentDocumentController')op.security=[];
  (openapi.paths[r.path]??={})[r.method.toLowerCase()]=op;
}
json('backend/design/openapi.json',openapi);
json('backend/design/endpoint-contract.json',{runtimeCount:58,targetCount:59,domains:9,knowledgeKinds:kinds,runtime,target});
let api=read('backend/API_ENDPOINTS.md');
const marker='## 지식 5대 분류 변경 설계 (2026-11-26)';
const extra=`${marker}

아래는 **설계 계약 변경**이며 현재 Java/DTO/EndpointContractTests는 변경하지 않았다. 위 목록은 실행 코드 58개 경로의 인벤토리다. 목표는 기존 9개 도메인 그대로 59개 경로다.

| 메서드 | 경로 | 변경 | 현재 코드 |
| --- | --- | --- | --- |
| GET | /api/me/analysis?kind=FACT | 기존 목록에 선택 필터 추가 | kind 미바인딩 |
| GET | /api/me/analysis?kind=MEMORY | 같은 목록의 필터 값 | kind 미바인딩 |
| GET | /api/me/analysis?kind=VALUE | 같은 목록의 필터 값 | kind 미바인딩 |
| GET | /api/me/analysis?kind=PREFERENCE | 같은 목록의 필터 값 | kind 미바인딩 |
| GET | /api/me/analysis?kind=BEHAVIOR | 같은 목록의 필터 값 | kind 미바인딩 |
| GET | /api/me/analysis/summary | 신규 경로 1개 | 전용 핸들러 없음 |

kind는 FACT/MEMORY/VALUE/PREFERENCE/BEHAVIOR 중 하나이며 미지정 전체다. informationId는 knowledge_items.id다. 상세는 해당 버전의 단일 분류 상세 테이블을 JOIN하고 이력·출처·충돌을 제공한다. summary는 버전 수가 아닌 유효한 활성 항목 수를 5종별 집계한다.

요청/응답과 후속 테스트는 [ANALYSIS_API.md](design/ANALYSIS_API.md), 기계 판독 계약은 [openapi.json](design/openapi.json), 호출·TX·병합·권한 설계는 [BACKEND_ARCHITECTURE.md](design/BACKEND_ARCHITECTURE.md)를 따른다. 조회 필터는 분류별 공개 권한 설정이 아니며 지식 수정/제외 API는 추가하지 않는다.
`;
api=api.includes(marker)?api.slice(0,api.indexOf(marker)).trimEnd()+'\n\n'+extra:api.trimEnd()+'\n\n'+extra;
api=api.replace('## 구현 경계','설계 변경: 실행 코드 58개는 유지하며, 아래 지식 5대 분류 목표 계약은 59개다. 상세는 문서 끝의 변경 설계를 참조한다.\n\n## 구현 경계');
api=api.replace(/(설계 변경: 실행 코드 58개는 유지하며,[^\n]*\n\n){2,}/,'$1');
write('backend/API_ENDPOINTS.md',api);

const detailNames=kinds.map(k=>k.toLowerCase()+'_details');
const newNames=new Set(['knowledge_embeddings','embedding_jobs','member_groups',...detailNames]);
const groups={accounts:'회원·동의',social_identities:'회원·동의',auth_sessions:'회원·동의',password_reset_tokens:'회원·동의',consent_documents:'회원·동의',consent_events:'회원·동의',deactivation_requests:'회원·동의',clones:'클론·권한',clone_assessments:'클론·권한',clone_invitations:'클론·권한',clone_permissions:'클론·권한',member_groups:'클론·권한',audit_events:'클론·권한',conversation_sessions:'원문 대화',messages:'원문 대화',message_revisions:'원문 대화',analysis_jobs:'장기 지식',knowledge_items:'장기 지식',knowledge_versions:'장기 지식',knowledge_sources:'장기 지식',knowledge_conflicts:'장기 지식',knowledge_embeddings:'벡터·답변',embedding_jobs:'벡터·답변',response_segments:'벡터·답변',response_evidence:'벡터·답변'};
detailNames.forEach(n=>groups[n]='분류별 상세');
const roles={accounts:'계정·활성 상태',social_identities:'소셜 계정 연결',auth_sessions:'로그인 세션',password_reset_tokens:'비밀번호 재설정 토큰',consent_documents:'동의 문서·버전',consent_events:'가입 동의·탈퇴 철회',deactivation_requests:'논리 비활성화 요청',clones:'클론 식별·소유자',clone_assessments:'정보 충분성 평가',clone_invitations:'초대 상태',clone_permissions:'개인별 현재 허용 등급',member_groups:'그룹과 일괄 등급 변경',audit_events:'접근·권한 변경 감사',conversation_sessions:'personal/spokesperson 세션',messages:'메시지 역할·순서·상태',message_revisions:'원문과 정정 버전',analysis_jobs:'분석 작업·lease 점유',knowledge_items:'지식 정체성·소유자·분류',knowledge_versions:'내용·민감도·시간 이력',knowledge_sources:'지식 버전 ↔ 원문 근거',knowledge_conflicts:'상충하는 버전',knowledge_embeddings:'지식 버전의 검색 벡터',embedding_jobs:'인덱싱 작업·lease 점유',response_segments:'답변의 사실·추론·안내',response_evidence:'답변이 사용한 지식 버전',fact_details:'FACT: 주체·술어·값 형식',memory_details:'MEMORY: 사건·회상 특성',value_details:'VALUE: 원칙·판단 영역',preference_details:'PREFERENCE: 호불호·대상',behavior_details:'BEHAVIOR: 계기·빈도·습관'};
const changes=new Set(['clone_permissions','analysis_jobs','knowledge_items','knowledge_versions','knowledge_sources','knowledge_conflicts','consent_events']);
const T={};
const fieldMeaning={version_id:'소속 지식 버전 PK/FK',kind:'공통 5종 분류 또는 상세의 고정 타입 가드',subject_scope:'자신의 속성 / 관계에 관한 속성',predicate_code:'정규화된 속성·술어 코드',value_type:'복호화 후 검증할 값 형식',value_ciphertext:'형식에 맞게 정규화한 암호화 값',unit_code:'숫자 값의 단위 코드',event_type:'사건·경험의 유형 코드',location_ciphertext:'관련 장소의 암호화 값',participants_ciphertext:'관련 인물의 암호화 값',emotional_tone:'경험에 드러난 감정 방향',recall_precision:'회상 정보의 정밀도',domain_code:'가치 판단 영역 코드',principle_ciphertext:'가치 원칙의 암호화 값',priority_band:'명시적 근거가 있는 중요도 1~5, 불명확하면 NULL',scope_ciphertext:'적용 범위·상황의 암호화 값',target_type:'선호 대상의 유형',target_ciphertext:'선호 대상의 암호화 값',polarity:'LIKE/DISLIKE/MIXED/NEUTRAL',strength:'근거 있는 선호 강도 1~5, 불명확하면 NULL',context_ciphertext:'선호가 성립하는 조건의 암호화 값',action_code:'행동·반응 유형 코드',trigger_ciphertext:'행동 발생 계기의 암호화 값',frequency_unit:'빈도 기간 단위 또는 비정기·미상',frequency_count:'해당 단위의 관찰 빈도, 미상이면 NULL',automaticity:'습관적/의도적/미상 구분',lease_token:'점유 시 새로 발급하는 워커 fencing token',lease_until:'이 점유가 유효한 시각',locked_by:'점유 워커 ID',max_attempts:'프로세스 사망 후 재점유 상한',attempt_count:'점유 횟수',candidate_no:'분석 작업 안에서 원자 추출 후보의 순번',default_level:'그룹 신규 배정과 일괄 변경에 사용할 등급'};
for(const t of tables){
  const related=target.filter(r=>(t.name.startsWith('knowledge_')||detailNames.includes(t.name))?r.controller==='AnalysisController':t.name.startsWith('message')||t.name==='conversation_sessions'?/ConversationController/.test(r.controller):t.name==='member_groups'?r.path.includes('/groups'):t.name==='clones'?r.controller==='CloneController':false);
  T[t.name]={name:t.name,group:groups[t.name],shortRole:roles[t.name],role:t.note||roles[t.name],status:newNames.has(t.name)?'추가 제안':changes.has(t.name)?'기존 수정':'기존',fields:t.fields.map(f=>({name:f.name,type:f.type.type_name+(f.pk?' PK':f.not_null?' NOT NULL':''),meaning:f.note||fieldMeaning[f.name]||(f.name==='id'?'식별자':f.name==='is_active'?'논리 활성 여부':'컬럼 정의·제약은 DBML 참조')})),read:detailNames.includes(t.name)?'KnowledgeDao.loadVersionWithDetail / 분류별 분석 / 허용 정보 조회':t.name==='knowledge_embeddings'?'KnowledgeDao.searchCandidates: owner·등급·활성·모델 범위':'요청 및 Worker 호출 표 참조',write:detailNames.includes(t.name)?'KnowledgeMergeService.apply TX-4에서 해당 버전과 함께 INSERT':t.name==='analysis_jobs'?'TX-1 등록 / TX-3 점유 / TX-4 완료':'아래 단계별 DML·호출 계약 참조',example:t.name==='knowledge_versions'?'버전당 정확히 한 분류 상세. 타입 복합 FK+상수 CHECK+커밋 지연 트리거.':t.name==='member_groups'?'현재 max_level에 그룹 일괄 변경을 물질화. 그룹 해제와 권한 철회는 별도.':t.name==='knowledge_embeddings'?'모델/차원 미확정. 유효한 과거 버전은 검색 유지. 삭제·정정 무효 버전만 제외.':'설계안 · 실행 코드/DB 적용 전',api:related.map(r=>r.method+' '+r.path)};
}
const refs=[];
for(const r of schema.refs){let [a,b]=r.endpoints;if(a.relation==='1'&&b.relation==='1')[a,b]=[b,a];else if(a.relation==='1'&&b.relation==='*')[a,b]=[b,a];for(let i=0;i<a.fieldNames.length;i++)refs.push([a.tableName,a.fieldNames[i],b.tableName,b.fieldNames[i]]);}
const calls={
  sync:[['1 OwnerConversationController.send','인증·personal 세션 확인','TX 밖','OwnerConversationService.send 호출'],['2 OwnerConversationService.saveUserTurn','messages + message_revisions + analysis_jobs','TX-1','원문과 queued 작업을 함께 저장. assistant 답변은 분석 원문 대상 아님'],['3 RagService.retrieve / 최근 맥락 조회','기존 지식 + 사용자 세션 문맥','TX 밖','분석 완료 대기 없음'],['4 LLM 응답 생성','외부 LLM','TX 밖','네트워크 동안 DB 잠금/Connection 보유 안 함'],['5 OwnerConversationService.saveAssistantTurn','messages + message_revisions','TX-2','AI 답변 저장. analysis_jobs 미생성'],['6 HTTP 응답','messageId + 답변·상태','TX 밖','분석 실패와 대화 응답을 분리']],
  etl:[['1 AnalysisWorker.claim → AnalysisDao','analysis_jobs','TX-3','FOR UPDATE SKIP LOCKED, 새 lease_token/lease_until 설정 후 즉시 COMMIT'],['2 KnowledgeExtractor.extract','원문 revision + 최근 사용자 문맥','TX 밖 LLM','원자 후보 추출. assistant 발언을 사용자 사실로 저장하지 않음'],['3 SensitivityClassifier.classify','FACT/MEMORY/VALUE/PREFERENCE/BEHAVIOR + L1~L4','TX 밖','실패 후보는 classification_failed_count만 기록하고 저장·재시도 없음'],['4 KnowledgeAnalyzer.analyze','기존 지식·해당 분류 상세','TX 밖','Fact/Memory/Value/Preference/BehaviorAnalyzer를 kind로 선택'],['5 KnowledgeMergeService.plan','NEW/DUPLICATE/TEMPORAL_CHANGE/CONFLICT/CLASSIFICATION_FAILED','TX 밖','최종 병합 계획'],['6 KnowledgeMergeService.apply','items + versions + 정확한 상세 + sources + conflicts + embedding_jobs + analysis_jobs','TX-4','owner 행 → 작업 행 → 지식 잠금. token/lease/원문 활성/기존 상태 재검증. 결과와 작업 완료를 원자 반영']],
  kinds:kinds.map((k,i)=>[(i+1)+' '+k+' → '+k[0]+k.slice(1).toLowerCase()+'Analyzer.analyze',k.toLowerCase()+'_details','TX 밖 분석 / TX-4 저장',roles[k.toLowerCase()+'_details']+' · version_id 1:1 · 고정 kind 복합 FK · 누락 방지 지연 트리거']),
  merge:[['NEW','items + versions + 상세 + sources + embedding_jobs','TX-4 INSERT','신규 항목과 최초 버전'],['DUPLICATE','sources INSERT / item 카운터 UPDATE','TX-4','기존 버전·벡터 유지. 실제 신규 근거만 카운트'],['TEMPORAL_CHANGE','새 버전·상세·출처·job INSERT / 기존 수명 UPDATE','TX-4','과거 유효 정보/벡터 유지. 사건 경계가 알려진 경우만 occurred_until 종료'],['CONFLICT','새 버전·상세·출처·충돌·job INSERT','TX-4','양쪽 보존. 불확실성 표시'],['CLASSIFICATION_FAILED','analysis_jobs 결과 개수만 UPDATE','TX-4','지식·상세·벡터·job 미생성, 재시도 없음'],['EmbeddingWorker.claim → embed → complete','embedding_jobs → 외부 API → knowledge_embeddings','점유 TX / API 밖 / 저장 TX','lease/fingerprint/활성/출처 재검증 후 UPSERT + completed. failed 자동 재시도 없음']],
  rag:[['1 ViewerConversationService.answer → AccessPolicyService.resolve','accounts/consent_events/clones/clone_permissions/member_groups','조회/질문 저장 TX','질문자는 consumer, 검색 대상은 clone.owner. 그룹 변경은 현재 max_level에 반영'],['2 RagService.retrieve → KnowledgeDao.searchCandidates','knowledge_embeddings','TX 밖 검색','소유자·민감도·활성·모델 필터. 단순 벡터 검색은 Tool Calling 아님'],['3 KnowledgeDao.loadVersionWithDetail','knowledge_versions/items + 해당 *_details + 유효 sources','2차 인가','owner·현재 등급·활성·유효 원문 검사 후 복호화. 과거 버전도 권한 내 검색'],['4 선택적 원문 / ToolQueryService.executeAllowed','sources → revisions 또는 허용 통계 SQL','TX 밖','원문 혼합 민감도 마스킹. 도구별 서버 owner/등급 강제'],['5 LLM → 출력 노출 검사','허용 컨텍스트만 외부 전달','TX 밖','현재 권한으로 기존 대화 맥락도 검증. 미검증 토큰·TTS 선출력 금지'],['6 ViewerConversationService 답변 저장','messages/revisions + segments/evidence + audit','저장 TX','정확한 version 근거 보존. 재조회·근거 열람도 현재 권한 검사']]
};
const views={all:{label:'기존 24 + 상세 5 = 핵심 29 / 그룹 저장 보완 1 = 총 30 · 설계 전용',groups:Object.entries(groups).reduce((acc,[n,g])=>{let x=acc.find(a=>a[0]===g);if(!x){x=[g,[]];acc.push(x);}x[1].push(n);return acc;},[])},sync:{label:'원문 TX-1 → TX 밖 LLM → AI 응답 TX-2 · 분석 완료 대기 없음',groups:[['동기 대화 저장',['conversation_sessions','messages','message_revisions','analysis_jobs']],['검색과 응답',['knowledge_items','knowledge_versions','knowledge_embeddings']]]},etl:{label:'점유 TX-3 → 외부 분석 → 원자 저장 TX-4 · lease fencing',groups:[['작업과 원문',['analysis_jobs','conversation_sessions','messages','message_revisions']],['원자 지식과 상세',['knowledge_items','knowledge_versions',...detailNames]],['출처·충돌·인덱싱 예약',['knowledge_sources','knowledge_conflicts','embedding_jobs']]]},kinds:{label:'5대 분류 · 버전별 정확히 한 상세 · 상수 CHECK + 복합 FK + 지연 트리거',groups:[['공통 항목과 버전',['knowledge_items','knowledge_versions']],['버전별 1:1 상세',detailNames],['추적과 검색',['knowledge_sources','knowledge_conflicts','knowledge_embeddings']]]},merge:{label:'5가지 병합 판정 · 내용 불변 버전 · 임베딩은 외부 호출과 저장 TX 분리',groups:[['지식·상세 저장',['knowledge_items','knowledge_versions',...detailNames]],['근거·충돌·벡터',['knowledge_sources','knowledge_conflicts','embedding_jobs','knowledge_embeddings']]]},rag:{label:'서버 권한 확정 → 검색 필터 → 상세 조회·2차 인가 → 검증된 답변',groups:[['권한과 질문',['clones','clone_permissions','member_groups','conversation_sessions','messages']],['검색·분류 상세·출처',['knowledge_embeddings','knowledge_versions','knowledge_items',...detailNames,'knowledge_sources','message_revisions']],['답변과 감사',['response_segments','response_evidence','audit_events']]]}};
const decisions=[['코드와 계약','실행 58 / 목표 59','9개 도메인 유지. kind는 기존 목록 필터. summary 1개만 설계 추가. Java와 기존 테스트는 그대로.'],['테이블 수','핵심 29 + 그룹 1','기존 24개에 상세 5개. 기존 그룹 API 저장 누락 때문에 member_groups 보완.'],['상세 버전 연결','version_id PK + 복합 FK','item 단위 연결은 과거 상세를 덮어쓰므로 배제. 정확히 1개는 deferred constraint trigger.'],['분류','5개 대문자','FACT/MEMORY/VALUE/PREFERENCE/BEHAVIOR. 이전 personality/linguistic_style 자동 매핑 금지; 별도 전환 검토.'],['작업 점유','짧은 TX와 fencing','lease_token/lease_until. 만료 running만 상한 내 회수. 완료 TX에 전 결과 저장.'],['분류 실패','저장·재시도 없음','실패 후보는 작업의 classification_failed_count만 증가. 유효 후보와 부분 성공 가능.'],['그룹 권한','단일 그룹·일괄 물질화','group default와 구성원 max_level을 같은 TX에서 변경. 개인 수정은 다음 그룹 변경까지 유효.'],['암호화','내용·상세 실제 값 보호','L4 ciphertext·키 관리, 벡터·코드·수치도 민감 파생 데이터. 검색 실행 가능한 저장·백업 암호화는 별도 검토.'],['역사 데이터','과거도 RAG에 포함','시간 변화 버전은 유효하면 벡터 유지. 삭제/정정 무효화와 구분.'],['Tool Calling','필요한 구조화 질의만','일반 벡터 검색은 서비스가 직접 실행. 모델에 임의 SQL/소유자/등급 결정권 없음.']];
const sources=[['PostgreSQL Constraints','https://www.postgresql.org/docs/current/ddl-constraints.html','CHECK는 다른 테이블의 변경 상태를 보장하지 않음','복합 FK와 커밋 시점 상세 존재 검사'],['PostgreSQL SELECT','https://www.postgresql.org/docs/current/sql-select.html','SKIP LOCKED는 잠긴 큐 행을 건너뜀','점유 TX는 짧게, 외부 호출 중 잠금 유지 안 함'],['DBML 공식 문법','https://dbml.dbdiagram.io/docs/','컬럼·테이블 CHECK와 1:1 복합 FK 표현','현행 dbmlv2 파서로 검증. 트리거는 SQL 보완안'],['pgvector','https://github.com/pgvector/pgvector#filtering','ANN 필터 시 후보 부족·정확도/지연 상충','소유자 exact 검색도 측정. 모델/차원 확정 뒤 ANN 인덱스']];
const apiRows=target.map(r=>[r.controller,r.method+' '+r.path,r.status,r.path==='/api/me/analysis'?'kind=FACT|MEMORY|VALUE|PREFERENCE|BEHAVIOR (선택)':r.path==='/api/me/analysis/summary'?'5종 항목 수·충돌·작업 현황':r.description]);
const classRows=[];
for(const line of read('backend/design/BACKEND_ARCHITECTURE.md').split('\n'))if(line.startsWith('| ')&&/controller\.|service\.|dao\.|dto 설계/.test(line)){const cells=line.split('|').slice(1,-1).map(s=>s.trim());if(cells.length===4)classRows.push(cells);}
const model={version:'2026-11-26-knowledge-v2',tables:T,refs,views,opData:calls,apiRows,classRows,sources,decisions,counts:{baseline:24,core:29,total:30,runtimeApis:58,targetApis:59,domains:9},knowledgeKinds:kinds};
json('backend/design/architecture-model.json',model);
write('docs/database/README.md',`# EVERME 데이터베이스 상세 설계

기준일: 2026-11-26. 최신 사용자 첨부의 5대 분류·비동기 작업·API 정책을 기존 설계에 반영했다. 운영 DB migration이나 실제 구현은 아니다.

## 기준 파일

- [everme.dbml](everme.dbml): 전체 30개. 기준본은 [backend/design/everme.dbml](../../backend/design/everme.dbml)이며 내용이 동일하다.
- [백엔드 상세 아키텍처](../../backend/design/BACKEND_ARCHITECTURE.md): 클래스/메서드, TX-1~4, ETL·병합·임베딩·RAG·권한.
- [API 설계](../../backend/design/ANALYSIS_API.md): 실행 58개 유지, 목표 59개. kind 5개 필터와 summary 1개 추가 설계.
- [변경 보고](../../backend/design/CHANGE_REPORT.md), [검증 결과](../../backend/design/validation-results.json).
- docs/output은 기존 gitignore 대상이며 추적 가능한 기준 자료는 backend/design에 있다.

## 개수와 기존 설계 대비

기존 원본 22개 + 벡터 제안 2개 = 24개. 5종 상세를 추가한 핵심 29개에 그룹 API 저장 보완 member_groups 1개를 더해 총 30개다. 기존 테이블 삭제는 없다. 29개를 엄격히 유지하려면 그룹 저장·API 구현을 보류해야 하므로 이번안은 그룹 권한 요구를 반영해 30개를 선택했다.

분류는 FACT/MEMORY/VALUE/PREFERENCE/BEHAVIOR 5개다. 기존 7종 소문자 enum에서 personality/linguistic_style은 자동 변환하지 않는다. 원문과 기존 값을 보존한 별도 전환 계획이 필요하다.

## 테이블 역할

| 테이블 | 역할 | 상태 |
| --- | --- | --- |
${tables.map(t=>'| '+t.name+' | '+roles[t.name]+' | '+T[t.name].status+' |').join('\n')}

## 관계와 무결성

knowledge_items 1:N knowledge_versions, 각 version에는 정확히 한 분류 상세가 1:1 연결된다. 상세 PK는 version_id다. owner/내용/민감도/시점/활성은 공통 테이블을 JOIN한다. 고정 kind CHECK + 복합 FK는 서로 다른 상세 혼합을 차단한다. 상세 누락은 커밋 시 지연 constraint trigger로 보완한다. [SQL 보완 설계](../../backend/design/constraints-design.sql)는 자동 실행하지 않는다.

knowledge_sources는 지식 버전과 원문 revision을 N:M 연결한다. response_evidence는 답변 부분과 사용한 지식 버전을 연결한다. 단순 출처 링크는 원문 전체 공개 권한을 의미하지 않는다. 원문 혼합 민감도는 별도 검사·마스킹한다.

작업의 lease_token/lease_until과 최종 CAS는 이전 워커의 늦은 커밋을 막는다. 새 지식/버전/상세/출처/embedding_jobs/분석 완료는 한 TX-4에서 저장한다. 외부 LLM·임베딩 호출은 TX 밖이다. 분류 실패는 지식을 저장하거나 재시도하지 않는다.

그룹은 한 이용자당 최대 하나다. member_groups.default_level 변경과 소속 clone_permissions.max_level 변경은 같은 TX다. 검색은 현재 max_level을 사용한다. 이동은 새 default, 해제는 기존 개인 등급 유지, 철회는 별도다. 이 그룹 정책은 구현 전 확인할 설계 가정이다.

## 기존 정책 유지

- 사용자/대변인 대화는 공통 테이블, kind=personal/spokesperson. 참여 계정과 클론 소유자는 다르다.
- 사용자 personal/user 발언만 지식 추출 대상. AI 답변·정보이용자 질문은 사용자 사실로 축적하지 않는다.
- 가입 시 전체 필수 동의, 탈퇴 시만 철회. 개별 동의 API 없음.
- 삭제는 논리 비활성화. 지식 내용/민감도 직접 수정·개별 활용 제외 API 없음.
- 클론은 별도 활성화 신청 없이 이용하며 개인정보 비활성화·동의·현재 접근 권한은 검사한다.
- 유효한 과거 정보도 전체 누적 기록의 일부로 검색. 기간별 페르소나 기능 없음.
- 벡터 검색 필터와 LLM 전달 전 2차 인가, 응답·근거·이력·TTS에도 현재 권한 적용.

## 검증 한계

현대 DBML 파서와 격리 PostgreSQL 엔진에서 관계형 제약을 검증한다. 테스트의 vector는 대체 타입이므로 실제 pgvector/ANN·모델·암호화·네트워크·다중 워커 경합은 별도 검증 대상이다. 실제 DB에는 접속하지 않는다. 모델/차원 결정 후 vector(d), 키 관리, HNSW 실행 계획·검색 정확도를 확인해야 한다.
`);
// Preserve existing HTML shell, typography, colors, legend and SVG selection behavior.
let html=read('output/architecture/everme-data-architecture.html');
const start=html.indexOf('    const f='),end=html.indexOf("    let view=",start);
const priorStart=html.indexOf('    // BEGIN GENERATED DESIGN MODEL');
const data='    // BEGIN GENERATED DESIGN MODEL\n    const model='+JSON.stringify(model)+';\n    const T=model.tables, refs=model.refs, views=model.views, opData=model.opData, apiRows=model.apiRows, sources=model.sources, decisions=model.decisions;\n    // END GENERATED DESIGN MODEL\n';
if(start>=0&&end>start)html=html.slice(0,start)+data+html.slice(end);
else if(priorStart>=0){const stop=html.indexOf('    // END GENERATED DESIGN MODEL',priorStart)+'    // END GENERATED DESIGN MODEL'.length;html=html.slice(0,priorStart)+data.trimEnd()+html.slice(stop);}
else throw Error('Architecture model marker missing');
html=html.replace('<button type="button" class="btn" data-view="etl"', '<button type="button" class="btn" data-view="etl"');
if(!html.includes('data-view="sync"'))html=html.replace('      <button type="button" class="btn" data-view="etl"','      <button type="button" class="btn" data-view="sync" aria-pressed="false">동기 대화</button>\n      <button type="button" class="btn" data-view="kinds" aria-pressed="false">5종 분석</button>\n      <button type="button" class="btn" data-view="merge" aria-pressed="false">병합·임베딩</button>\n      <button type="button" class="btn" data-view="classes" aria-pressed="false">클래스</button>\n      <button type="button" class="btn" data-view="etl"');
if(!html.includes('id="ev-classes-panel"'))html=html.replace('    <section id="ev-api-panel"','    <section id="ev-classes-panel" class="stack" hidden></section>\n    <section id="ev-api-panel"');
html=html.replace("const shown=[shortRoles[id],", "const shown=[t.shortRole||shortRoles[id],");
html=html.replace("view==='etl'?'ETL 저장 경계':'RAG 조회·저장 경계'","view==='etl'?'ETL 저장 경계':view==='rag'?'RAG 조회·저장 경계':'클래스 호출·트랜잭션·DML'");
html=html.replace("['단계','접근 테이블','구분','조건·결과']","['단계·실제 메서드 설계','접근 테이블','TX 경계','조건·결과']");
if(!html.includes("$('#ev-classes-panel').hidden=view!=='classes';"))html=html.replace("$('#ev-api-panel').hidden=view!=='api';", "$('#ev-api-panel').hidden=view!=='api';$('#ev-classes-panel').hidden=view!=='classes';");
html=html.replace("['all','etl','rag','api','research'].includes(c.view)","['all','sync','etl','kinds','merge','rag','classes','api','research'].includes(c.view)");
html=html.replace('경로 분리는 설계 제안입니다. 현재 Java의 /api/conversations 경로는 아직 변경하지 않았습니다.','기존 9개 도메인·실행 경로 58 유지. 목표 59: kind 필터 + summary 1개. Java와 기존 테스트는 변경하지 않은 설계안입니다.');
html=html.replace("['기능','경로 제안','테이블','설계 포인트']","['도메인','경로','구현 상태','계약']");
if(!html.includes("$('#ev-classes-panel').innerHTML="))html=html.replace("    $('#ev-api-panel').innerHTML=", "    $('#ev-classes-panel').innerHTML='<h3>계층·메서드 설계</h3><div class=\"text-small\">기존 빈 클래스와 신규 설계를 구분. 실제 Java 구현은 추가하지 않았습니다.</div>'+rowsTable(['클래스','상태','주요 메서드','책임·의존관계'],model.classRows.map(r=>r.map(esc)),'wide-table');\n    $('#ev-api-panel').innerHTML=");
html=html.replace('설계 검토안','5종 지식 상세 설계');
write('output/architecture/everme-data-architecture.html',html);
// Reuse existing dbdiagram metadata and dark/light preference; add new table placements only.
for(const p of ['docs/database/everme.dbdiagram','output/architecture/everme-rag-proposal.dbdiagram']){
  const layout=JSON.parse(read(p)),positions=layout.defaultView.tablePositions,known=new Set(positions.map(p=>p.name));
  let i=0;for(const t of tables)if(!known.has(t.name)){positions.push({name:t.name,schemaName:'public',x:2500+(i%3)*420,y:Math.floor(i/3)*460});i++;}
  json(p,layout);
}
const hashes={};for(const d of ['backend/src/main/java','backend/src/test/java']){const walk=p=>{for(const e of fs.readdirSync(p,{withFileTypes:true})){const f=path.join(p,e.name);if(e.isDirectory())walk(f);else if(e.name.endsWith('.java'))hashes[path.relative(root,f).replaceAll('\\','/')]=crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex');}};walk(path.join(root,d));}
json('output/architecture/.qa/java-snapshot.json',hashes);
console.log('Synced OpenAPI 59 targets / 58 existing, architecture model 30 tables, HTML and layouts');
