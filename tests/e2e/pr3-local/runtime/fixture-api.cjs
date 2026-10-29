// Portable local adaptation; cloud acceptance is not local execution.
// Future explicit Root GO only. Real same-realm APIs; no session/token fabrication.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{spawnSync}=require('node:child_process');
const ROOT=__dirname,PRIVATE=path.join(ROOT,'private'),OWN_ORIGIN=JSON.parse(fs.readFileSync(path.join(ROOT,'settings.json'))).ownOrigin||'http://127.0.0.1:14250';
const {request}=require(process.env.RCT_PLAYWRIGHT_MODULE||path.join(JSON.parse(fs.readFileSync(path.join(__dirname,'settings.json'))).repo, 'tests/e2e/node_modules/@playwright/test'));
const actors=[];let scope;
const FIXTURE_TELEGRAM_IDS=Object.freeze({
 headman:900000199,
 teacher:900000198,
 helper:900000197,
 controlStudent:900000196,
});
let fixtureStage='ADMISSION';
function stopped(code){
 const error=new Error('Fixture stopped; values withheld');
 error.fixtureCode=code;return error;
}
function requestStage(method,url,data){
 if(url.startsWith('/api/auth/'))return 'AUTH';
 if(method==='POST'&&url==='/api/academic/groups/registry')return 'CREATE_GROUP';
 if(method==='POST'&&url==='/api/academic/users')return data?.role==='STUDENT'?'CREATE_STUDENT':'CREATE_TEACHER';
 if(method==='PUT'&&url.endsWith('/roles/TEACHER'))return 'GRANT_TEACHER';
 if(method==='PUT'&&url.endsWith('/headman'))return 'ASSIGN_HEADMAN';
 if(url==='/api/academic/assistants')return 'ASSIGN_ASSISTANT';
 if(url==='/api/academic/subjects')return 'CREATE_SUBJECT';
 return 'READ_FIXTURE_CONTEXT';
}
function closedFailure(error){
 const codes=['NAMESPACE_NOT_FRESH','PENDING_IDENTITY_OPERATION','HTTP_VALIDATION_REJECTED','HTTP_NOT_ACKNOWLEDGED','OWN_ORIGIN_MISMATCH','REPLY_OR_TRANSPORT_UNCERTAIN'];
 return {schema:'rct.local-fixture.failure.v1',status:'HOLD_NO_RETRY',stage:fixtureStage,
  code:codes.includes(error?.fixtureCode)?error.fixtureCode:'CONTRACT_OR_EVIDENCE_FAILED'};
}
function store(p,x,exclusive=false){
 fs.mkdirSync(path.dirname(p),{recursive:true,mode:0o700});if(fs.existsSync(p)&&fs.lstatSync(p).isSymbolicLink())throw Error('Private handle symlink denied');
 const tmp=p+'.'+crypto.randomUUID()+'.tmp',fd=fs.openSync(tmp,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_EXCL|fs.constants.O_NOFOLLOW,0o600);
 try{fs.writeFileSync(fd,JSON.stringify(x,null,2)+'\n');fs.fsyncSync(fd);}finally{fs.closeSync(fd);}
 if(exclusive){fs.linkSync(tmp,p);fs.unlinkSync(tmp);}else fs.renameSync(tmp,p);
 const dir=fs.openSync(path.dirname(p),fs.constants.O_RDONLY);try{fs.fsyncSync(dir);}finally{fs.closeSync(dir);}
}
function audit(entry){const p=path.join(PRIVATE,'fixture-api-attempts.jsonl'),fd=fs.openSync(p,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_APPEND|fs.constants.O_NOFOLLOW,0o600);try{fs.writeSync(fd,JSON.stringify(entry)+'\n');fs.fsyncSync(fd);}finally{fs.closeSync(fd);}}
function admit(phase,go){const r=spawnSync('python3',['-c','import runtime,sys;runtime.admission(sys.argv[1],sys.argv[2])',phase,go],{cwd:ROOT,encoding:'utf8'});if(r.status!==0)throw Error('Root admission failed; no action');}
function freshNamespace(cfg){
 fixtureStage='FRESH_NAMESPACE';
 const ids=Object.values(FIXTURE_TELEGRAM_IDS);
 if(ids.length!==4||new Set(ids).size!==4||!ids.every(id=>Number.isSafeInteger(id)&&id>0))throw stopped('NAMESPACE_NOT_FRESH');
 const program=`import json,re,sys,runtime
data=json.load(sys.stdin)
groups=data['groups']; ids=data['telegramIds']
assert len(groups)==2 and groups[0]!=groups[1] and all(re.fullmatch(r'ГДЛ-[1-5]\\d{2}',g) for g in groups)
assert ids==[900000199,900000198,900000197,900000196]
names=','.join("'"+g+"'" for g in groups)
numbers=','.join(str(i) for i in ids)
sql="BEGIN READ ONLY; SELECT json_build_object('groups',(SELECT count(*) FROM groups WHERE name IN("+names+")),'profiles',(SELECT count(*) FROM academic_profiles WHERE last_name='ГруппаУдаление'),'bindings',(SELECT count(*) FROM academic_identity_read_model WHERE telegram_id IN("+numbers+"))); ROLLBACK;"
counts=json.loads(runtime.pg(sql,'academic_db').stdout)
assert set(counts)=={'groups','profiles','bindings'} and all(type(v) is int and v==0 for v in counts.values())
`;
 const result=spawnSync('python3',['-B','-c',program],{
  cwd:ROOT,encoding:'utf8',input:JSON.stringify({groups:[cfg.groupName,cfg.controlGroupName],telegramIds:ids}),timeout:60000,
 });
 if(result.status!==0)throw stopped('NAMESPACE_NOT_FRESH');
}
function bindScope(cfg,goFile,phase){
 const g=JSON.parse(fs.readFileSync(goFile)),u=new URL(cfg.baseURL);
 if(cfg.baseURL!==OWN_ORIGIN||u.origin!==OWN_ORIGIN||u.username||u.password||u.pathname!=='/'||u.search||u.hash||g.ownOrigin!==OWN_ORIGIN||g.phase!==phase)throw Error('Exact frozen owned origin/Root phase required before clock or Auth');
 scope={phase,base:OWN_ORIGIN};return scope;
}
async function persistActor(a,stage){
 if(a.api)store(a.storagePath,await a.api.storageState());
 store(a.handlePath,{handle:a.handle,pid:process.pid,phase:a.phase,label:a.label,origin:a.base,expectedUserId:a.expectedUserId,expectedRole:a.expectedRole,status:a.status,stage,storageStatePath:a.storagePath,accessToken:a.token||null,refreshToken:a.refreshToken||null,correlationSid:a.correlationSid||null,session:a.session||null,lastRequest:a.lastRequest||null});
}
function ownPath(a,url){const u=new URL(url,a.base);if(a.base!==OWN_ORIGIN||u.origin!==OWN_ORIGIN||u.username||u.password||!url.startsWith('/api/')||url.startsWith('//')||u.hash)throw Error('Request outside exact owned API origin denied');return u.pathname+u.search;}
async function invoke(actor,method,url,data,expected){
 fixtureStage=requestStage(method,url,data);
 const owned=ownPath(actor,url),rid=crypto.randomUUID(),headers={Origin:OWN_ORIGIN,'X-Request-Id':rid,...(actor.token?{Authorization:'Bearer '+actor.token}:{})};if(method!=='GET')headers['Idempotency-Key']=crypto.randomUUID();
 actor.lastRequest={at:new Date().toISOString(),method,path:owned,requestId:rid,key:headers['Idempotency-Key']||null,state:'STARTED'};await persistActor(actor,'BEFORE_REQUEST');audit({actorHandle:actor.handle,...actor.lastRequest});
 try{
  const response=await actor.api.fetch(owned,{method,data,headers,maxRedirects:0});actor.lastRequest.status=response.status();actor.lastRequest.state='HEADERS_RECEIVED';await persistActor(actor,'RESPONSE_HEADERS_COOKIES_SAVED');
  if(response.status()===202){
   // This is a durable identity operation, never a new-key retry or an invented result.
   actor.lastRequest.location=response.headers()['location']||null;
   actor.lastRequest.operationId=response.headers()['x-operation-id']||null;
   actor.lastRequest.state='ACKNOWLEDGED_PENDING_IDENTITY_OPERATION';
   await persistActor(actor,'PENDING_OPERATION_LOCATION_RETAINED');audit({actorHandle:actor.handle,...actor.lastRequest});
   throw stopped('PENDING_IDENTITY_OPERATION');
  }
  if(new URL(response.url()).origin!==OWN_ORIGIN)throw stopped('OWN_ORIGIN_MISMATCH');
  if(!expected.includes(response.status()))throw stopped(response.status()===400?'HTTP_VALIDATION_REJECTED':'HTTP_NOT_ACKNOWLEDGED');
  const body=response.status()===204?null:await response.json();
  if(method!=='GET'&&owned.startsWith('/api/academic/')){
   // Persist each acknowledged mutation reply before callbacks/state updates, including one-time credentials.
   store(path.join(PRIVATE,'fixture-api-replies',rid+'.json'),{actorHandle:actor.handle,request:actor.lastRequest,reply:body},true);
  }
  if(owned==='/api/auth/login'||owned==='/api/auth/session/active-role'){
   if(typeof body?.accessToken!=='string')throw Error('Auth token reply incomplete');actor.token=body.accessToken;if(typeof body.refreshToken==='string')actor.refreshToken=body.refreshToken;
   // Correlate only our normal Auth response. Canonical session GET/PUT still qualifies identity.
   const claims=JSON.parse(Buffer.from(actor.token.split('.')[1],'base64url').toString());actor.correlationSid=claims.sid;if(String(claims.sub)!==actor.expectedUserId||!/[0-9a-f-]{36}/i.test(actor.correlationSid||''))throw Error('Unexpected own Auth actor/SID');
  }
  if(owned==='/api/auth/session'){actor.session=body;if(String(body.userId)!==actor.expectedUserId||body.sessionId!==actor.correlationSid)throw Error('Canonical actor/SID mismatch');}
  if(owned==='/api/auth/session/active-role'){
   const previous=actor.session;actor.session=body.session;if(!previous||!actor.session||actor.session.sessionId!==previous.sessionId||String(actor.session.userId)!==actor.expectedUserId||actor.session.activeRole!==actor.expectedRole)throw Error('Selected actor/SID/role mismatch');
  }
  actor.lastRequest.state='ACKNOWLEDGED';await persistActor(actor,'REPLY_HANDLES_SAVED_BEFORE_NEXT_REQUEST');audit({actorHandle:actor.handle,...actor.lastRequest});return body;
 }catch(error){
  actor.status='HOLD';if(actor.lastRequest.state!=='ACKNOWLEDGED_PENDING_IDENTITY_OPERATION')actor.lastRequest.state='UNKNOWN_OR_UNEXPECTED_HOLD';try{await persistActor(actor,'HOLD_CONTEXT_RETAINED');audit({actorHandle:actor.handle,...actor.lastRequest});}catch{}
  if(!error.fixtureCode)error.fixtureCode='REPLY_OR_TRANSPORT_UNCERTAIN';throw error;
 }
}
async function login(base,credentials,role,label,expectedUserId){
 if(!scope||base!==OWN_ORIGIN||!/^\d+$/.test(String(expectedUserId)))throw Error('Bound exact own origin/expected actor required');
 const handle=scope.phase+'-'+label+'-'+crypto.randomUUID(),a={handle,label,phase:scope.phase,base:OWN_ORIGIN,expectedUserId:String(expectedUserId),expectedRole:role,status:'REGISTERED_BEFORE_AUTH',handlePath:path.join(PRIVATE,'auth-actors',handle+'.json'),storagePath:path.join(PRIVATE,'auth-actors',handle+'-storage.json')};
 actors.push(a);await persistActor(a,'REGISTERED_BEFORE_CONTEXT_AND_LOGIN');
 a.api=await request.newContext({baseURL:OWN_ORIGIN,extraHTTPHeaders:{Origin:OWN_ORIGIN},maxRedirects:0});await persistActor(a,'CONTEXT_REGISTERED_BEFORE_LOGIN_POST');
 await invoke(a,'POST','/api/auth/login',{login:credentials.login,password:credentials.password},[200]);
 const session=await invoke(a,'GET','/api/auth/session',undefined,[200]);
 await invoke(a,'PUT','/api/auth/session/active-role',{role,expectedSessionVersion:session.sessionVersion},[200]);a.status='QUALIFIED_OWN_ACTOR';await persistActor(a,'READY_FOR_BUSINESS');return a;
}
async function closeKnownActor(a){if(a.status!=='QUALIFIED_OWN_ACTOR'||!a.session||a.session.sessionId!==a.correlationSid)throw Error('Exact own SID qualification required before logout');await invoke(a,'POST','/api/auth/logout',undefined,[204]);a.status='CLOSED_LOGOUT_ACK';await persistActor(a,'KNOWN_OWN_LOGOUT_ACK');await a.api.dispose();a.api=null;}
async function handoffActor(name,a){
 if(a.status!=='QUALIFIED_OWN_ACTOR'||!a.session||a.session.sessionId!==a.correlationSid)throw Error('Exact own SID required for explicit handoff');
 store(path.join(PRIVATE,name+'-storage.json'),await a.api.storageState());a.status='HANDED_OFF_TO_NATIVE';await persistActor(a,'SUCCESSFUL_PRIVATE_SESSION_HANDOFF');await a.api.dispose();a.api=null;
}
async function holdActors(){
 for(const a of actors){if(['CLOSED_LOGOUT_ACK','HANDED_OFF_TO_NATIVE'].includes(a.status))continue;a.status='HOLD';try{await persistActor(a,'PHASE_STOPPED_NO_FINALLY_CLEANUP');}catch{}}
 if(actors.some(a=>a.status==='HOLD'&&a.api)){
  console.error('HOLD: registered own contexts/handles retained; no new login, refresh, logout or compensation. Inspect private handles/original requests.');
  // Handles/cookies stay private; do not turn a known rejection into a five-minute idle timeout.
 }
}

async function createFixtureUser(admin,state,stateFile,firstName,role,groupId,telegramId,employeeNumber){
 state.step='create-'+firstName;
 store(stateFile,state);
 const response=await invoke(admin,'POST','/api/academic/users',{
  lastName:'ГруппаУдаление',firstName,middleName:null,
  role,groupId,telegramId,employeeNumber,
 },[201]);
 if(!response.initialPassword||BigInt(response.id)<=3n)throw stopped('REPLY_OR_TRANSPORT_UNCERTAIN');
 return {id:String(response.id),login:response.login,password:response.initialPassword};
}

// Both a fresh fixture and the explicitly admitted member checkpoint use this exact tail.
async function finishFixture(admin,state,cfg,stateFile){
 if(state.accounts?.length!==1||!state.groupId||!state.controlGroupId
   ||state.retainedTeacher||state.controlStudent)throw Error('Unexpected post-member checkpoint');
 const member=state.accounts[0],semester=state.semester;
 state.telegramIds={...FIXTURE_TELEGRAM_IDS};
 store(stateFile,state);
  const helper=await createFixtureUser(admin,state,stateFile,'Помощник','STUDENT',Number(state.groupId),FIXTURE_TELEGRAM_IDS.helper,null);
  state.accounts.push(helper);store(stateFile,state);
  state.retainedTeacher=await createFixtureUser(admin,state,stateFile,'Преподаватель','TEACHER',null,FIXTURE_TELEGRAM_IDS.teacher,'GRT-'+state.caseId.slice(0,8));
  state.retainedTeacherId=state.retainedTeacher.id;store(stateFile,state);
  state.controlStudent=await createFixtureUser(admin,state,stateFile,'Контроль','STUDENT',Number(state.controlGroupId),FIXTURE_TELEGRAM_IDS.controlStudent,null);
  store(stateFile,state);
  state.employeeNumber='GRP-'+state.caseId.slice(0,8);
  await invoke(admin,'PUT','/api/academic/users/'+member.id+'/roles/TEACHER',{
   status:'ACTIVE',employeeNumber:state.employeeNumber,groupId:null,telegramId:null,
  },[200]);
  for(const [g,u] of [[state.groupId,member.id],[state.controlGroupId,state.controlStudent.id]])await invoke(admin,'PUT','/api/academic/groups/'+g+'/headman',{studentId:Number(u),expectedHeadmanId:null},[200]);
  const headman=await login(cfg.baseURL,member,'HEADMAN','headman',member.id);
  await invoke(headman,'POST','/api/academic/assistants',{
   studentId:Number(helper.id),groupId:Number(state.groupId),permissions:['MANAGE_HOMEWORK'],
  },[201]);
  const teacher=await login(cfg.baseURL,state.retainedTeacher,'TEACHER','teacher',state.retainedTeacher.id);
  const controlHeadman=await login(cfg.baseURL,state.controlStudent,'HEADMAN','control-headman',state.controlStudent.id);
  const from=semester.dateFrom,until=new Date(Date.parse(semester.dateTo+'T00:00:00Z')+86400000).toISOString().slice(0,10);state.validFrom=from;state.validUntilExclusive=until;
  for(const [actor,key,title] of [[headman,'subjectId','ОкончательноеУдаление '],[controlHeadman,'controlSubjectId','СохранённаяГруппа ']]){state.step='create-'+key;store(stateFile,state);const subject=await invoke(actor,'POST','/api/academic/subjects',{name:title+state.caseId.slice(0,8),type:'LECTURE',lessonTypes:['LECTURE'],initialAssignments:[{teacherId:Number(state.retainedTeacherId),semesterId:Number(semester.id),lessonType:'LECTURE',validFrom:from,validUntilExclusive:until}]},[201]);state[key]=String(subject.id);store(stateFile,state);}
  await closeKnownActor(controlHeadman);state.controlFixtureSessionClosed=true;
  state.authActorHandles={};for(const [name,actor] of [['admin',admin],['headman',headman],['teacher',teacher]]){await handoffActor(name,actor);state[name+'SessionId']=actor.session.sessionId;state[name+'Token']=actor.token;state.authActorHandles[name]={handle:actor.handle,path:actor.handlePath,status:actor.status};store(stateFile,state);}
  state.step='api-fixture-complete';store(stateFile,state);console.log('Fresh target/control fixture ready via normal APIs; old identities/credentials/bindings unchanged; no delete');
}

async function main(){
 const go=process.argv[2],configFile=process.argv[3];if(!go||!configFile)throw Error('Explicit GO/private config required');admit('fixture-api',go);
 const cfg=JSON.parse(fs.readFileSync(configFile));bindScope(cfg,go,'fixture-api');const stateFile=path.join(PRIVATE,'fixture-state.json');if(fs.existsSync(stateFile))throw Error('Already attempted; no new-key retry');
 if(!/^ГДЛ-[1-5]\d{2}$/.test(cfg.groupName)||!/^ГДЛ-[1-5]\d{2}$/.test(cfg.controlGroupName)||cfg.groupName===cfg.controlGroupName)throw Error('Two distinct fresh owned groups required');
 freshNamespace(cfg);
 const state={caseId:crypto.randomUUID(),groupName:cfg.groupName,controlGroupName:cfg.controlGroupName,step:'intent',accounts:[],telegramIds:{...FIXTURE_TELEGRAM_IDS},sourceCommit:JSON.parse(fs.readFileSync(path.join(ROOT,'pins.json'))).sourceCommit};store(stateFile,state,true);
 try{
  const admin=await login(cfg.baseURL,cfg.admin,'ADMIN','admin','1');if(String(admin.session.userId)!=='1')throw Error('Retained Admin1 required');
  const calendar=JSON.parse(fs.readFileSync(path.join(PRIVATE,'calendar-state.json')));if(calendar.phase!=='activated')throw Error('Approved current calendar must be acknowledged before fixture');const semester=await invoke(admin,'GET','/api/academic/semesters/'+calendar.newSemesterId,undefined,[200]);const today=new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Moscow',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());if(String(semester.id)!==calendar.newSemesterId||semester.active!==true||semester.dateFrom>today||semester.dateTo<today)throw Error('Real current active owned semester required');state.semester=semester;
  const create=async(method,url,data,step)=>{state.step=step;store(stateFile,state);return invoke(admin,method,url,data,[201]);};
  const group=await create('POST','/api/academic/groups/registry',{alphabeticCode:'ГДЛ',numericCode:'199',trainingDurationYears:2},'create-target-group');state.groupId=String(group.id);store(stateFile,state);
  const control=await create('POST','/api/academic/groups/registry',{alphabeticCode:'ГДЛ',numericCode:'198',trainingDurationYears:2},'create-control-group');state.controlGroupId=String(control.id);if(BigInt(state.groupId)<=1n||BigInt(state.controlGroupId)<=1n||state.groupId===state.controlGroupId)throw Error('Only new own groups');store(stateFile,state);
  const member=await createFixtureUser(admin,state,stateFile,'Участник','STUDENT',Number(state.groupId),FIXTURE_TELEGRAM_IDS.headman,null);
  state.accounts.push(member);store(stateFile,state);
  await finishFixture(admin,state,cfg,stateFile);
 }catch(error){throw error;}
}
async function handleFailure(error){
 await holdActors();
 // Dispose only local API clients. No server logout, compensation or token retry.
 for(const actor of actors)if(actor.api){try{await actor.api.dispose();}catch{}}
 console.error(JSON.stringify(closedFailure(error)));process.exitCode=1;
}

module.exports={request,actors,store,audit,bindScope,persistActor,invoke,
 holdActors,closedFailure,handleFailure,FIXTURE_TELEGRAM_IDS,finishFixture};
if(require.main===module)main().catch(handleFailure);
