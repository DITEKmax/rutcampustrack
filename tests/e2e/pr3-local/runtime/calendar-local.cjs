'use strict';
// A new local realm uses the real teaching clock. No clock file or existing realm is changed.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');
const root=__dirname,settings=JSON.parse(fs.readFileSync(path.join(root,'settings.json')));
const {request}=require(path.join(settings.repo,'tests/e2e/node_modules/@playwright/test'));
const crypto=require('node:crypto');
function save(file,value,exclusive=false){
 const temp=file+'.'+crypto.randomUUID()+'.tmp';const fd=fs.openSync(temp,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_EXCL|fs.constants.O_NOFOLLOW,0o600);
 try{fs.writeFileSync(fd,JSON.stringify(value,null,2)+'\n');fs.fsyncSync(fd);}finally{fs.closeSync(fd);}
 if(exclusive){fs.linkSync(temp,file);fs.unlinkSync(temp);}else fs.renameSync(temp,file);
 const dir=fs.openSync(path.dirname(file),fs.constants.O_RDONLY);try{fs.fsyncSync(dir);}finally{fs.closeSync(dir);}
}
async function main(){
 const go=process.argv[2],cfg=JSON.parse(fs.readFileSync(process.argv[3]));
 const r=cp.spawnSync('python3',['-c','import runtime,sys;runtime.admission("calendar-setup",sys.argv[1])',go],{cwd:root});if(r.status!==0)throw Error();
 const file=path.join(root,'private/calendar-state.json');save(file,{phase:'intent',at:new Date().toISOString()},true);
 const handle=path.join(root,'private/calendar-auth-handle.json');let api,token,sid;
 const origin=settings.ownOrigin||'http://127.0.0.1:14250';
 async function invoke(method,url,data,status){
  if(new URL(url,origin).origin!==origin||!url.startsWith('/api/'))throw Error();
  save(handle,{state:'REQUEST_INTENT',method,url,sid,token,storage:api?await api.storageState():null});
  const response=await api.fetch(url,{method,data,maxRedirects:0,headers:{Origin:origin,...(token?{Authorization:'Bearer '+token}:{})}});
  save(handle,{state:'HEADERS',method,url,status:response.status(),sid,token,storage:await api.storageState()});
  if(response.status()!==status||new URL(response.url()).origin!==origin)throw Error();
  const body=status===204?null:await response.json();
  if(body?.accessToken){token=body.accessToken;const claim=JSON.parse(Buffer.from(token.split('.')[1],'base64url'));if(String(claim.sub)!=='1')throw Error();sid=claim.sid;}
  save(handle,{state:'ACKNOWLEDGED',method,url,sid,token,storage:await api.storageState()});return body;
 }
 save(handle,{state:'REGISTERED_BEFORE_AUTH'},true);
 api=await request.newContext({baseURL:origin,maxRedirects:0});
 await invoke('POST','/api/auth/login',cfg.admin,200);const session=await invoke('GET','/api/auth/session',undefined,200);
 if(session.sessionId!==sid||String(session.userId)!=='1')throw Error();
 await invoke('PUT','/api/auth/session/active-role',{role:'ADMIN',expectedSessionVersion:session.sessionVersion},200);
 const now=new Date(),year=Number(new Intl.DateTimeFormat('en',{timeZone:'Europe/Moscow',year:'numeric'}).format(now));
 const today=new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Moscow',year:'numeric',month:'2-digit',day:'2-digit'}).format(now);
 // Previous month through current month always includes the fixture's yesterday.
 const month=Number(today.slice(5,7)),from=new Date(Date.UTC(year,month-2,1)).toISOString().slice(0,10),to=new Date(Date.UTC(year,month,0)).toISOString().slice(0,10);
 const overlap=await invoke('GET','/api/academic/semesters/overlap?from='+from+'&to='+to,undefined,200);if(overlap.overlaps!==false)throw Error();
 const created=await invoke('POST','/api/academic/semesters',{name:'PR3 local current semester',dateFrom:from,dateTo:to,semesterType:month>=7?'AUTUMN':'SPRING',academicYear:month>=7?year:year-1},201);
 save(file,{phase:'created',newSemesterId:String(created.id),createdVersion:created.stateVersion});
 const active=await invoke('PATCH','/api/academic/semesters/'+created.id+'/activate',undefined,200);if(!active.active||active.activationScheduled)throw Error();
 save(file,{phase:'activated',newSemesterId:String(created.id),activatedVersion:active.stateVersion,realTeachingClock:true,oldBaselineSemester:1});
 await invoke('POST','/api/auth/logout',undefined,204);await api.dispose();save(handle,{state:'CLOSED_LOGOUT_ACK',sid});
 console.log('Own current semester created through normal API; OS/Auth/teaching clocks unchanged.');
}
main().catch(()=>{console.error('HOLD: local calendar intent/Auth handle retained; no retry, restore, new login or automatic cleanup.');process.exitCode=1;});
