'use strict';
// Distinct common-edge acceptance: native WEB login, exact own session/catalog and physical SockJS.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const [source,output]=process.argv.slice(2),privateDir=path.join(source,'.pr3-local/runtime/private');
const {chromium,request}=require(path.join(source,'tests/e2e/node_modules/@playwright/test'));
const fixture=JSON.parse(fs.readFileSync(path.join(privateDir,'fixture-state.json')));
assert.equal(fixture.step,'owned-fixture-ready');
const UUID=/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i;
const safe={schema:1,result:'FAIL',http:null,https:null,externalProviders:'NOT_APPLICABLE'};
function save(name,value,exclusive=false){
 const file=path.join(output,name),tmp=file+'.tmp';
 const fd=fs.openSync(tmp,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_EXCL|fs.constants.O_NOFOLLOW,0o600);
 try{fs.writeSync(fd,JSON.stringify(value,null,2)+'\n');fs.fsyncSync(fd);}finally{fs.closeSync(fd);}
 if(exclusive){fs.linkSync(tmp,file);fs.unlinkSync(tmp);}else fs.renameSync(tmp,file);
}
function claim(token){
 assert.equal(typeof token,'string');const body=JSON.parse(Buffer.from(token.split('.')[1],'base64url').toString());
 assert(UUID.test(body.sid));assert.equal(String(body.sub),String(fixture.retainedTeacherId));return body;
}
function frames(payload){
 const raw=typeof payload==='string'?payload:Buffer.from(payload).toString('utf8');
 if(raw.startsWith('a['))return JSON.parse(raw.slice(1));
 if(raw.startsWith('['))return JSON.parse(raw);
 return [raw];
}
async function until(predicate,ms=15000){const end=Date.now()+ms;while(Date.now()<end){if(predicate())return;await new Promise(r=>setTimeout(r,100));}throw Error('Bounded common-edge observation expired');}
async function one(browser,scheme,port){
 const base=scheme+'://127.0.0.1:'+port,qualified={login:false,session:false,catalog:false,physicalSocket:false,connected:false,subscribed:false,logout:false,oldBearerRefused:false,tlsVerified:scheme==='https'};
 let token=null,sid=null,qualifiedSid=false,loginSent=false,logoutSent=false,context,primary=null;
 const handle={state:'REGISTERED_BEFORE_LOGIN',scheme,sid:null,token:null};save(scheme+'.handle.private.json',handle,true);
 try{
  context=await browser.newContext({viewport:{width:1440,height:1080},timezoneId:'Europe/Moscow'});
  const page=await context.newPage();page.setDefaultTimeout(12000);
  const socket={count:0,connected:0,subscribe:0,errors:0,foreign:0};let catalog=null,snapshot=null,foreign=0,pageErrors=0;
  page.on('pageerror',()=>pageErrors++);
  page.on('websocket',ws=>{
   const u=new URL(ws.url());if(!u.pathname.startsWith('/api/ws/'))return;
   socket.count++;if(u.host!==new URL(base).host||u.protocol!==(scheme==='https'?'wss:':'ws:'))socket.foreign++;
   ws.on('socketerror',()=>socket.errors++);
   ws.on('framereceived',event=>{try{for(const frame of frames(event.payload))if(typeof frame==='string'&&frame.startsWith('CONNECTED\n'))socket.connected++;}catch{socket.errors++;}});
   ws.on('framesent',event=>{try{for(const frame of frames(event.payload))if(typeof frame==='string'&&frame.startsWith('SUBSCRIBE\n'))socket.subscribe++;}catch{socket.errors++;}});
  });
  page.on('response',async response=>{try{if(new URL(response.url()).pathname==='/api/v1/teacher/catalog'&&response.status()===200)catalog=await response.json();if(new URL(response.url()).pathname==='/api/auth/session'&&response.status()===200)snapshot=await response.json();}catch{}});
  await page.route('**/*',async route=>{
   const req=route.request(),u=new URL(req.url());
   if(u.origin!==base){foreign++;return route.abort('blockedbyclient');}
   if(req.method()==='POST'&&u.pathname==='/api/auth/login'){
    assert(!loginSent,'No login retry');loginSent=true;handle.state='LOGIN_FORWARD_UNKNOWN';save(scheme+'.handle.private.json',handle);
    const reply=await route.fetch({maxRedirects:0});assert.equal(reply.status(),200);
    const body=await reply.json();token=body.accessToken;const c=claim(token);sid=c.sid;
    handle.state='LOGIN_HANDLE_CAPTURED';handle.sid=sid;handle.token=token;save(scheme+'.handle.private.json',handle);
    qualified.login=true;return route.fulfill({response:reply});
   }
   if(req.method()==='PUT'&&u.pathname==='/api/auth/session/active-role'){
    assert(token&&sid);const reply=await route.fetch({maxRedirects:0});assert.equal(reply.status(),200);
    const body=await reply.json();const c=claim(body.accessToken);assert.equal(c.sid,sid);token=body.accessToken;
    handle.token=token;handle.state='ROLE_REPLY_HANDLE_CAPTURED';save(scheme+'.handle.private.json',handle);
    return route.fulfill({response:reply});
   }
   if(req.method()==='POST'&&u.pathname==='/api/auth/logout'){
    assert(qualifiedSid&&!logoutSent);logoutSent=true;handle.state='LOGOUT_FORWARD_UNKNOWN';save(scheme+'.handle.private.json',handle);
   }
   // Native role selection uses the same acknowledged SID. No fixture/API mock is installed.
   return route.continue();
  });
  await page.goto(base+'/login',{waitUntil:'domcontentloaded'});
  assert.equal(new URL(page.url()).origin,base);
  await page.locator('#login').fill(fixture.retainedTeacher.login);await page.locator('#password').fill(fixture.retainedTeacher.password);
  await page.getByRole('button',{name:'Войти',exact:true}).click();
  await until(()=>qualified.login);
  await until(()=>snapshot!==null);
  if(snapshot.activeRole!=='TEACHER'){const choice=page.locator('.web-header-role').getByRole('button',{name:'Преподаватель',exact:true});await choice.waitFor({state:'visible'});await choice.click();}
  await page.waitForURL(base+'/teacher/home');
  const canonical=await context.request.get(base+'/api/auth/session',{headers:{Authorization:'Bearer '+token}});assert.equal(canonical.status(),200);
  const current=await canonical.json();assert.equal(String(current.userId),String(fixture.retainedTeacherId));assert.equal(current.sessionId,sid);assert.equal(current.activeRole,'TEACHER');
  qualifiedSid=true;qualified.session=true;handle.state='CANONICAL_EXACT_OWN_SID';save(scheme+'.handle.private.json',handle);
  await page.locator('a[href="/teacher/statistics"]').first().click();await page.waitForURL(base+'/teacher/statistics');
  await until(()=>catalog?.groups?.some(g=>String(g.id)===String(fixture.groupId))&&catalog?.groups?.some(g=>String(g.id)===String(fixture.controlGroupId)));
  qualified.catalog=true;
  await until(()=>socket.connected>=1&&socket.subscribe>=1);
  assert.equal(socket.count,1);assert.equal(socket.connected,1);assert(socket.subscribe>=1);assert.equal(socket.errors,0);assert.equal(socket.foreign,0);assert.equal(foreign,0);assert.equal(pageErrors,0);
  qualified.physicalSocket=true;qualified.connected=true;qualified.subscribed=true;
  const profile=page.getByRole('button',{name:/^Профиль:/});if(await profile.getAttribute('aria-expanded')!=='true')await profile.click();
  const reply=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/auth/logout'&&r.request().method()==='POST');
  await page.locator('#web-profile-menu').getByRole('button',{name:'Выйти',exact:true}).click();assert.equal((await reply).status(),204);
  qualified.logout=true;handle.state='LOGOUT_ACK';save(scheme+'.handle.private.json',handle);
  const anonymous=await request.newContext();try{assert.equal((await anonymous.get(base+'/api/auth/session',{headers:{Authorization:'Bearer '+token}})).status(),401);}finally{await anonymous.dispose();}
  qualified.oldBearerRefused=true;await page.waitForURL(base+'/login');return qualified;
 }catch(error){primary=error;handle.state='HOLD_OR_KNOWN_FAILURE';save(scheme+'.handle.private.json',handle);throw error;
 }finally{
  // Never retry an uncertain login/logout. A qualified still-live known SID has one exact self-revoke.
  if(primary&&qualifiedSid&&!logoutSent&&context){
   handle.state='EXACT_SELF_REVOKE_FORWARD_UNKNOWN';save(scheme+'.handle.private.json',handle);
   try{const result=await context.request.delete(base+'/api/auth/sessions/'+sid,{headers:{Authorization:'Bearer '+token}});if(result.status()===204){handle.state='EXACT_SELF_REVOKE_ACK';save(scheme+'.handle.private.json',handle);}}catch{}
  }
  if(context)await context.close();safe[scheme]={...qualified,result:primary?'FAIL':'PASS'};save('auth.safe.json',safe);
 }
}
async function main(){
 const executable=chromium.executablePath();assert(path.isAbsolute(executable)&&fs.statSync(executable).isFile());
 const browser=await chromium.launch({executablePath:executable,headless:true,env:{...process.env,HOME:process.env.RCT_COMMON_BROWSER_HOME}});
 try{safe.http=await one(browser,'http',15440);safe.https=await one(browser,'https',15443);safe.result='PASS';save('auth.safe.json',safe);}finally{await browser.close();}
 console.log(JSON.stringify({result:safe.result,http:!!safe.http,https:!!safe.https}));
}
main().catch(()=>{save('auth.safe.json',safe);process.exitCode=1;});
