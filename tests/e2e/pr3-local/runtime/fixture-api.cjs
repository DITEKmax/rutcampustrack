// Portable local adaptation; cloud acceptance is not local execution.
// Future explicit Root GO only. Real same-realm APIs; no session/token fabrication.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{spawnSync}=require('node:child_process');
const ROOT=__dirname,PRIVATE=path.join(ROOT,'private'),OWN_ORIGIN='http://127.0.0.1:14250';
const {request}=require(process.env.RCT_PLAYWRIGHT_MODULE||path.join(JSON.parse(fs.readFileSync(path.join(__dirname,'settings.json'))).repo, 'tests/e2e/node_modules/@playwright/test'));
const actors=[];let scope;
function store(p,x,exclusive=false){
 fs.mkdirSync(path.dirname(p),{recursive:true,mode:0o700});if(fs.existsSync(p)&&fs.lstatSync(p).isSymbolicLink())throw Error('Private handle symlink denied');
 const tmp=p+'.'+crypto.randomUUID()+'.tmp',fd=fs.openSync(tmp,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_EXCL|fs.constants.O_NOFOLLOW,0o600);
 try{fs.writeFileSync(fd,JSON.stringify(x,null,2)+'\n');fs.fsyncSync(fd);}finally{fs.closeSync(fd);}
 if(exclusive){fs.linkSync(tmp,p);fs.unlinkSync(tmp);}else fs.renameSync(tmp,p);
 const dir=fs.openSync(path.dirname(p),fs.constants.O_RDONLY);try{fs.fsyncSync(dir);}finally{fs.closeSync(dir);}
}
function audit(entry){const p=path.join(PRIVATE,'fixture-api-attempts.jsonl'),fd=fs.openSync(p,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_APPEND|fs.constants.O_NOFOLLOW,0o600);try{fs.writeSync(fd,JSON.stringify(entry)+'\n');fs.fsyncSync(fd);}finally{fs.closeSync(fd);}}
function admit(phase,go){const r=spawnSync('python3',['-c','import runtime,sys;runtime.admission(sys.argv[1],sys.argv[2])',phase,go],{cwd:ROOT,encoding:'utf8'});if(r.status!==0)throw Error('Root admission failed; no action');}
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
 const owned=ownPath(actor,url),rid=crypto.randomUUID(),headers={Origin:OWN_ORIGIN,'X-Request-Id':rid,...(actor.token?{Authorization:'Bearer '+actor.token}:{})};if(method!=='GET')headers['Idempotency-Key']=crypto.randomUUID();
 actor.lastRequest={at:new Date().toISOString(),method,path:owned,requestId:rid,key:headers['Idempotency-Key']||null,state:'STARTED'};await persistActor(actor,'BEFORE_REQUEST');audit({actorHandle:actor.handle,...actor.lastRequest});
 try{
  const response=await actor.api.fetch(owned,{method,data,headers,maxRedirects:0});actor.lastRequest.status=response.status();actor.lastRequest.state='HEADERS_RECEIVED';await persistActor(actor,'RESPONSE_HEADERS_COOKIES_SAVED');
  if(new URL(response.url()).origin!==OWN_ORIGIN||!expected.includes(response.status()))throw Error('Request response outside expected known result');
  const body=response.status()===204?null:await response.json();
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
  actor.status='HOLD';actor.lastRequest.state='UNKNOWN_OR_UNEXPECTED_HOLD';try{await persistActor(actor,'HOLD_CONTEXT_RETAINED');audit({actorHandle:actor.handle,...actor.lastRequest});}catch{}throw error;
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
  setInterval(()=>{},1000); // Keep live RAM/API contexts; Root can explicitly stop only after read-only recovery.
 }
}
async function main(){
 const go=process.argv[2],configFile=process.argv[3];if(!go||!configFile)throw Error('Explicit GO/private config required');admit('fixture-api',go);
 const cfg=JSON.parse(fs.readFileSync(configFile));bindScope(cfg,go,'fixture-api');const stateFile=path.join(PRIVATE,'fixture-state.json');if(fs.existsSync(stateFile))throw Error('Already attempted; no new-key retry');
 if(!/^ГДЛ-[1-5]\d{2}$/.test(cfg.groupName)||!/^ГДЛ-[1-5]\d{2}$/.test(cfg.controlGroupName)||cfg.groupName===cfg.controlGroupName)throw Error('Two distinct fresh owned groups required');
 const state={caseId:crypto.randomUUID(),groupName:cfg.groupName,controlGroupName:cfg.controlGroupName,step:'intent',accounts:[],sourceCommit:JSON.parse(fs.readFileSync(path.join(ROOT,'pins.json'))).sourceCommit};store(stateFile,state,true);
 try{
  const admin=await login(cfg.baseURL,cfg.admin,'ADMIN','admin','1');if(String(admin.session.userId)!=='1')throw Error('Retained Admin1 required');
  const calendar=JSON.parse(fs.readFileSync(path.join(PRIVATE,'calendar-state.json')));if(calendar.phase!=='activated')throw Error('Approved current calendar must be acknowledged before fixture');const semester=await invoke(admin,'GET','/api/academic/semesters/'+calendar.newSemesterId,undefined,[200]);const today=new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Moscow',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());if(String(semester.id)!==calendar.newSemesterId||semester.active!==true||semester.dateFrom>today||semester.dateTo<today)throw Error('Real current active owned semester required');state.semester=semester;
  const create=async(method,url,data,step)=>{state.step=step;store(stateFile,state);return invoke(admin,method,url,data,[201]);};
  const group=await create('POST','/api/academic/groups',{name:cfg.groupName},'create-target-group');state.groupId=String(group.id);store(stateFile,state);
  const control=await create('POST','/api/academic/groups',{name:cfg.controlGroupName},'create-control-group');state.controlGroupId=String(control.id);if(BigInt(state.groupId)<=1n||BigInt(state.controlGroupId)<=1n||state.groupId===state.controlGroupId)throw Error('Only new own groups');store(stateFile,state);
  async function user(firstName,role,groupId,telegramId,employeeNumber){const u=await create('POST','/api/academic/users',{lastName:'ГруппаУдаление',firstName,middleName:null,role,groupId,telegramId,employeeNumber},'create-'+firstName);if(!u.initialPassword||BigInt(u.id)<=3n)throw Error('Fresh own identity/one-time credentials required');return {id:String(u.id),login:u.login,password:u.initialPassword};}
  const member=await user('Участник','STUDENT',Number(state.groupId),900000199,null);state.accounts.push(member);store(stateFile,state);
  const helper=await user('Помощник','STUDENT',Number(state.groupId),null,null);state.accounts.push(helper);store(stateFile,state);
  state.retainedTeacher=await user('Преподаватель','TEACHER',null,900000198,'GRT-'+state.caseId.slice(0,8));state.retainedTeacherId=state.retainedTeacher.id;store(stateFile,state);
  state.controlStudent=await user('Контроль','STUDENT',Number(state.controlGroupId),null,null);store(stateFile,state);
  state.employeeNumber='GRP-'+state.caseId.slice(0,8);await invoke(admin,'PUT','/api/academic/users/'+member.id+'/roles/TEACHER',{status:'ACTIVE',employeeNumber:state.employeeNumber,groupId:null,telegramId:900000199},[200]);
  for(const [g,u] of [[state.groupId,member.id],[state.controlGroupId,state.controlStudent.id]])await invoke(admin,'PUT','/api/academic/groups/'+g+'/headman',{studentId:Number(u),expectedHeadmanId:null},[200]);
  await invoke(admin,'POST','/api/academic/assistants',{studentId:Number(helper.id),groupId:Number(state.groupId),permissions:['MANAGE_HOMEWORK']},[201]);
  const headman=await login(cfg.baseURL,member,'HEADMAN','headman',member.id);
  const teacher=await login(cfg.baseURL,state.retainedTeacher,'TEACHER','teacher',state.retainedTeacher.id);
  const controlHeadman=await login(cfg.baseURL,state.controlStudent,'HEADMAN','control-headman',state.controlStudent.id);
  const from=semester.dateFrom,until=new Date(Date.parse(semester.dateTo+'T00:00:00Z')+86400000).toISOString().slice(0,10);state.validFrom=from;state.validUntilExclusive=until;
  for(const [actor,key,title] of [[headman,'subjectId','ОкончательноеУдаление '],[controlHeadman,'controlSubjectId','СохранённаяГруппа ']]){state.step='create-'+key;store(stateFile,state);const subject=await invoke(actor,'POST','/api/academic/subjects',{name:title+state.caseId.slice(0,8),type:'LECTURE',lessonTypes:['LECTURE'],initialAssignments:[{teacherId:Number(state.retainedTeacherId),semesterId:Number(semester.id),lessonType:'LECTURE',validFrom:from,validUntilExclusive:until}]},[201]);state[key]=String(subject.id);store(stateFile,state);}
  await closeKnownActor(controlHeadman);state.controlFixtureSessionClosed=true;
  state.authActorHandles={};for(const [name,actor] of [['admin',admin],['headman',headman],['teacher',teacher]]){await handoffActor(name,actor);state[name+'SessionId']=actor.session.sessionId;state[name+'Token']=actor.token;state.authActorHandles[name]={handle:actor.handle,path:actor.handlePath,status:actor.status};store(stateFile,state);}
  state.telegramIds={headman:900000199,teacher:900000198};state.step='api-fixture-complete';store(stateFile,state);console.log('Fresh target/control fixture ready via normal APIs; old identities/credentials/bindings unchanged; no delete');
 }catch(error){throw error;}
}
main().catch(async()=>{await holdActors();console.error('Fixture stopped; durable own actor handles retained, no automatic retry/reset or finally cleanup.');process.exitCode=1});
