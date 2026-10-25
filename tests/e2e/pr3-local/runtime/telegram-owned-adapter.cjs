// Portable local adaptation; cloud acceptance is not local execution.
// Controlled Telegram host only. Real same-realm Auth/domain HTTP and physical WS remain mandatory.
// Root permits ONLY this owned realm selector, consumed in RAM, never printed/persisted/returned.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict'),{spawnSync}=require('node:child_process');
const SOURCE=path.join(__dirname,'private','local.env');
function selectedSigner(){
 assert.equal(fs.realpathSync(SOURCE),SOURCE);assert(!fs.lstatSync(SOURCE).isSymbolicLink());
 let found,name='',value='',isValue=false,selected=false;const byte=Buffer.alloc(1),fd=fs.openSync(SOURCE,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
 try{while(fs.readSync(fd,byte,0,1,null)){
  const ch=String.fromCharCode(byte[0]);
  if(ch==='\n'){if(selected){assert.equal(found,undefined);found=value;}name='';value='';isValue=false;selected=false;continue;}
  if(!isValue){if(ch==='='){isValue=true;selected=name==='TMA_BOT_TOKEN';}else if(name.length<256)name+=ch;}
  else if(selected)value+=ch; // All other selector values are discarded byte-wise, never collected.
 }if(selected){assert.equal(found,undefined);found=value;}}
 finally{byte.fill(0);fs.closeSync(fd);}
 assert(found&&/^[^\s'"`]+$/.test(found),'Explicit unquoted configured test signer required; no discovery/alternate guesses');return found;
}
async function installOwnedTelegramHost(page,telegramId,goFile){
 const r=spawnSync('python3',['-c','import runtime,sys;runtime.admission("native-case",sys.argv[1],continuation=True)',goFile],{cwd:__dirname,encoding:'utf8'});assert.equal(r.status,0,'Matching reviewed native-case RootGO required');
 const go=JSON.parse(fs.readFileSync(goFile));assert.equal(go.telegramContext,'CONTROLLED_OWN_ISOLATED_SIGNER');assert.equal(go.telegramSignerSource,SOURCE);assert.equal(go.telegramSignerSelector,'TMA_BOT_TOKEN');assert([900000198,900000199].includes(telegramId));
 const state=JSON.parse(fs.readFileSync(path.join(__dirname,'private/fixture-state.json')));assert.equal(state.telegramIds[telegramId===900000198?'teacher':'headman'],telegramId);assert.equal(state.step,'owned-fixture-ready');
 const data=new URLSearchParams({auth_date:String(Math.floor(Date.now()/1000)),query_id:crypto.randomUUID(),user:JSON.stringify({id:telegramId,first_name:'Owned group fixture'})});
 const check=[...data.entries()].sort(([a],[b])=>a.localeCompare(b)).map(([k,v])=>k+'='+v).join('\n');let signer=selectedSigner();const key=crypto.createHmac('sha256','WebAppData').update(signer).digest();signer=undefined;data.set('hash',crypto.createHmac('sha256',key).update(check).digest('hex'));key.fill(0);
 await page.route('https://telegram.org/js/telegram-web-app.js',route=>route.fulfill({body:'',contentType:'application/javascript'}));
 // Same synthetic host surface as the existing public CI helper, with no provider download/bot API.
 await page.addInitScript(initData=>{const backCallbacks=new Set();Object.assign(window,{Telegram:{WebApp:{initData,viewportHeight:844,viewportStableHeight:844,safeAreaInset:{top:0,right:0,bottom:0,left:0},ready(){},expand(){},onEvent(){},offEvent(){},BackButton:{show(){},hide(){},onClick(cb){backCallbacks.add(cb)},offClick(cb){backCallbacks.delete(cb)}}}}});},data.toString());
 // The caller must observe actual /api/auth/tma200, expected fresh Auth user/role, and real WS.
 // No token/initData/signer output, local files, screenshots of credentials, or provider success.
}
module.exports={installOwnedTelegramHost};
