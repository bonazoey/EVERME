// In-memory PostgreSQL relational design checks only; never connects to project DB.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const {Parser,ModelExporter}=require('@dbml/core');
const {PGlite}=require('@electric-sql/pglite');
const root=path.resolve(__dirname,'../..'),read=p=>fs.readFileSync(path.join(root,p),'utf8');
const id=()=>crypto.randomUUID();
(async()=>{
 const canonical=read('backend/design/everme.dbml');
 assert.equal(canonical,read('docs/database/everme.dbml'));
 assert.equal(canonical,read('output/architecture/everme-rag-proposal.dbml'));
 const schema=Parser.parse(canonical,'dbmlv2'),tables=schema.export().schemas[0].tables;
 assert.equal(tables.length,28);
 assert(!tables.some(t=>['consent_documents','consent_events'].includes(t.name)));
 const kinds=['FACT','MEMORY','VALUE','PREFERENCE','BEHAVIOR'];
 const contract=JSON.parse(read('backend/design/endpoint-contract.json'));
 assert.equal(contract.runtime.length,57);assert.equal(contract.target.length,58);
 assert.deepEqual(contract.knowledgeKinds,kinds);
 const api=JSON.parse(read('backend/design/openapi.json'));
 assert.equal(Object.values(api.paths).reduce((n,p)=>n+Object.keys(p).length,0),58);
 assert(!api.paths['/api/consent-documents']);
 assert(!api.components.schemas.ConsentSelection);
 assert.equal(api.components.schemas.SignupRequest.properties.agreedToTerms.const,true);
 assert.equal(api.paths['/api/me/analysis/summary'].get['x-implementation-status'],'design-only-new-route');
 const model=JSON.parse(read('backend/design/architecture-model.json'));
 assert.deepEqual(Object.keys(model.tables).sort(),tables.map(t=>t.name).sort());
 for(const [a,f,b,g]of model.refs){assert(tables.find(t=>t.name===a).fields.some(x=>x.name===f));assert(tables.find(t=>t.name===b).fields.some(x=>x.name===g));}
 const snapshots=JSON.parse(read('output/architecture/.qa/java-snapshot.json'));
 for(const [file,hash]of Object.entries(snapshots))assert.equal(crypto.createHash('sha256').update(read(file)).digest('hex'),hash,file+' changed');
 const db=new PGlite();const tests=[];
 // vector here is a stand-in type for relational DDL tests; pgvector search is NOT tested.
 await db.exec('CREATE DOMAIN vector AS real[];');
 await db.exec(ModelExporter.export(schema,'postgres'));
 await db.exec(read('backend/design/constraints-design.sql'));
 const owner=id(),other=id(),clone=id(),clone2=id(),session=id(),message=id(),revision=id(),job=id();
 await db.query('INSERT INTO accounts(id,email,display_name) VALUES ($1,$2,$3),($4,$5,$6)',[owner,'owner@example.test','owner',other,'other@example.test','other']);
 await db.query('INSERT INTO clones(id,owner_id) VALUES($1,$2),($3,$4)',[clone,owner,clone2,other]);
 await db.query("INSERT INTO conversation_sessions(id,participant_id,kind,encryption_key_id) VALUES($1,$2,'personal','test-key')",[session,owner]);
 await db.query("INSERT INTO messages(id,session_id,sequence_no,role,input_mode,generation_status) VALUES($1,$2,1,'user','text','completed')",[message,session]);
 await db.query("INSERT INTO message_revisions(id,message_id,revision_no,content_ciphertext,encryption_key_id) VALUES($1,$2,1,'encrypted','test-key')",[revision,message]);
 await db.query("INSERT INTO analysis_jobs(id,source_revision_id,owner_id,analyzer_revision) VALUES($1,$2,$3,'v2')",[job,revision,owner]);
 const attrs={FACT:{subject_scope:'SELF',predicate_code:'occupation',value_type:'TEXT',value_ciphertext:'cipher'},MEMORY:{event_type:'TRAVEL'},VALUE:{domain_code:'FAMILY',principle_ciphertext:'cipher'},PREFERENCE:{target_type:'ACTIVITY',target_ciphertext:'cipher',polarity:'LIKE'},BEHAVIOR:{action_code:'WALK'}};
 async function itemVersion(kind){const item=id(),version=id();await db.query('INSERT INTO knowledge_items(id,owner_id,kind,semantic_key) VALUES($1,$2,$3,$4)',[item,owner,kind,crypto.randomBytes(32).toString('hex')]);await db.query("INSERT INTO knowledge_versions(id,item_id,kind,version_no,content_ciphertext,encryption_key_id,sensitivity) VALUES($1,$2,$3,1,'cipher','test-key','L1')",[version,item,kind]);return {item,version};}
 async function detail(kind,version,overrides={}){const props={version_id:version,...attrs[kind],...overrides},keys=Object.keys(props);await db.query('INSERT INTO '+kind.toLowerCase()+'_details('+keys.join(',')+') VALUES('+keys.map((_,i)=>'$'+(i+1)).join(',')+')',Object.values(props));}
 const saved={};for(const kind of kinds){await db.exec('BEGIN');saved[kind]=await itemVersion(kind);await detail(kind,saved[kind].version);await db.exec('COMMIT');tests.push('valid '+kind+' version + matching detail');}
 async function rejected(name,run){await db.exec('BEGIN');let failed=false;try{await run();await db.exec('COMMIT');}catch(e){failed=true;try{await db.exec('ROLLBACK');}catch{}tests.push(name+' rejected ('+e.code+')');}assert(failed,name+' unexpectedly accepted');}
 await rejected('missing required detail at COMMIT',()=>itemVersion('FACT'));
 await rejected('mixed detail kind',async()=>{const x=await itemVersion('FACT');await detail('PREFERENCE',x.version);});
 await rejected('forged detail type guard',async()=>{const x=await itemVersion('FACT');await detail('FACT',x.version,{kind:'MEMORY'});});
 await rejected('duplicate detail per version',()=>detail('FACT',saved.FACT.version));
 await rejected('detail strength outside CHECK',async()=>{const x=await itemVersion('PREFERENCE');await detail('PREFERENCE',x.version,{strength:6});});
 await rejected('knowledge detail rewrite',()=>db.query("UPDATE fact_details SET predicate_code='changed' WHERE version_id=$1",[saved.FACT.version]));
 await rejected('knowledge content rewrite',()=>db.query("UPDATE knowledge_versions SET content_ciphertext='changed' WHERE id=$1",[saved.FACT.version]));
 await rejected('knowledge kind rewrite',()=>db.query("UPDATE knowledge_items SET kind='MEMORY' WHERE id=$1",[saved.FACT.item]));
 await rejected('source from another owner job',async()=>{await db.query('INSERT INTO knowledge_sources(version_id,message_revision_id,analysis_job_id,candidate_no) VALUES($1,$2,$3,1)',[saved.FACT.version,revision,job]);await db.query('UPDATE analysis_jobs SET owner_id=$1 WHERE id=$2',[other,job]);});
 const group=id();await db.query("INSERT INTO member_groups(id,clone_id,name,default_level) VALUES($1,$2,'family','L2')",[group,clone]);
 await rejected('cross-clone group FK',()=>db.query("INSERT INTO clone_permissions(id,clone_id,consumer_id,group_id,max_level) VALUES($1,$2,$3,$4,'L2')",[id(),clone2,owner,group]));
 await rejected('running job without lease',()=>db.query("UPDATE analysis_jobs SET status='running' WHERE id=$1",[job]));
 await rejected('conflict across different knowledge items',()=>{const ids=[saved.FACT.version,saved.MEMORY.version].sort();return db.query('INSERT INTO knowledge_conflicts(id,existing_version_id,incoming_version_id) VALUES($1,$2,$3)',[id(),...ids]);});
 const tokenA=id(),tokenB=id();await db.query("UPDATE analysis_jobs SET status='running',lease_token=$2,lease_until=clock_timestamp()-interval '1 second',locked_by='A',attempt_count=1 WHERE id=$1",[job,tokenA]);
 await db.query("UPDATE analysis_jobs SET lease_token=$2,lease_until=clock_timestamp()+interval '1 minute',locked_by='B',attempt_count=2 WHERE id=$1 AND lease_until<clock_timestamp()",[job,tokenB]);
 const stale=await db.query("UPDATE analysis_jobs SET classification_failed_count=1 WHERE id=$1 AND status='running' AND lease_token=$2 AND lease_until>clock_timestamp() RETURNING id",[job,tokenA]);assert.equal(stale.rows.length,0);tests.push('stale worker token update blocked');
 await db.query("UPDATE analysis_jobs SET status='completed',classification_failed_count=1,completed_at=clock_timestamp(),lease_token=NULL,lease_until=NULL,locked_by=NULL WHERE id=$1 AND lease_token=$2",[job,tokenB]);
 const done=await db.query('SELECT status,classification_failed_count FROM analysis_jobs WHERE id=$1',[job]);assert.equal(done.rows[0].status,'completed');tests.push('classification failure recorded as completed without knowledge/job creation');
 const itemCount=await db.query('SELECT count(*)::int AS count FROM knowledge_items');
 const embeddingCount=await db.query('SELECT count(*)::int AS count FROM embedding_jobs');
 assert.equal(itemCount.rows[0].count,5);assert.equal(embeddingCount.rows[0].count,0);
 const report={date:'2026-11-26',tables:28,coreTables:27,runtimeEndpoints:57,designEndpoints:58,javaUnchanged:false,tests,limitations:['in-memory PostgreSQL relational checks only','vector is stand-in domain; no pgvector ANN/search benchmark','single connection: SKIP LOCKED multi-worker races require implementation integration tests','no production DB connection']};
 writeReport(report);await db.close();console.log(JSON.stringify(report,null,2));
})().catch(e=>{console.error(e.message);process.exitCode=1;});
function writeReport(report){fs.writeFileSync(path.join(root,'backend/design/validation-results.json'),JSON.stringify(report,null,2)+'\n');}
