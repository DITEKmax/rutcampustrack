// Portable local adaptation; cloud acceptance is not local execution.
// Passive evidence helper for the existing Root browser driver. No fake frames/API/auth/events.
// Never persist URLs (ticket query), raw STOMP headers, tokens, or arbitrary frame bodies.
const assert=require('node:assert/strict');
function frames(raw){
 const s=typeof raw==='string'?raw:Buffer.from(raw).toString('utf8');
 if(s.startsWith('a[')||s.startsWith('[')){try{return JSON.parse(s.startsWith('a[')?s.slice(1):s)}catch{return []}}
 return [s];
}
function observe(page,{userId,groupId}){
 const state={physicalWebSockets:0,connected:false,personalSubscribed:false,groupId:String(groupId),hintCount:0,ticketDenied:false,reads:[],socketClosed:false};let from=Infinity;
 page.on('websocket',ws=>{
  if(!new URL(ws.url()).pathname.startsWith('/api/ws/'))return;
  state.physicalWebSockets++;
  ws.on('framesent',({payload})=>{for(const f of frames(payload))if(f.startsWith('SUBSCRIBE\n')&&f.includes('\ndestination:/topic/user/'+String(userId)+'\n'))state.personalSubscribed=true;});
  ws.on('framereceived',({payload})=>{for(const f of frames(payload)){
   if(f.startsWith('CONNECTED\n'))state.connected=true;
   if(!f.startsWith('MESSAGE\n')||Date.now()<from)continue;
   const split=f.indexOf('\n\n');if(split<0)continue;
   let body;try{body=JSON.parse(f.slice(split+2).replace(/\0+$/,''))}catch{continue}
   if(body.type!=='group.updated')continue;
   assert.deepEqual(Object.keys(body).sort(),['payload','type']);
   assert.deepEqual(Object.keys(body.payload),['group_id']);
   assert.equal(body.payload.group_id,String(groupId));state.hintCount++;state.hintAt=Date.now();
  }});
  ws.on('close',()=>{if(Date.now()>=from)state.socketClosed=true;});
 });
 page.on('response',response=>{
  const p=new URL(response.url()).pathname;
  if(Date.now()<from)return;
  if(p==='/api/auth/ws-ticket'&&[401,403].includes(response.status()))state.ticketDenied=true;
  if(p.startsWith('/api/v1/teacher/')&&response.request().method()==='GET')state.reads.push({path:p,status:response.status(),at:Date.now()});
 });
 return {
  state,
  async ready(){await page.waitForFunction(()=>document.readyState==='complete');const end=Date.now()+30000;while(Date.now()<end&&!(state.connected&&state.personalSubscribed)){await page.waitForTimeout(100)}assert(state.physicalWebSockets>0&&state.connected&&state.personalSubscribed,'Real physical WebSocket and personal subscription required before the single delete');},
  begin(){from=Date.now();},
  async headmanRetired(privateLocator){await privateLocator.waitFor({state:'hidden',timeout:30000});assert(state.socketClosed||state.ticketDenied,'Natural socket admission retirement/reconnect denial evidence required; no forced reconnect/reload');return {...state,privateProjectionRetired:true};},
  async teacherRetained({ownerHandle,draftLocator,draftValue='37'}){
   const end=Date.now()+30000;while(Date.now()<end&&!(state.hintCount>0&&state.reads.some(x=>x.status===200&&x.at>=state.hintAt))){await page.waitForTimeout(100)}
   assert(state.hintCount>0,'Exact hidden personal hint required for retained Teacher');
   assert(state.reads.some(x=>x.status===200&&x.at>=state.hintAt),'Actual active Teacher REST read must refresh after the hint');
   assert(await ownerHandle.evaluate(el=>el.isConnected),'The same mounted owner must survive');if(draftLocator)assert.equal(await draftLocator.inputValue(),draftValue);
   return {...state,sameMountedOwner:true,draftPreserved:!!draftLocator,providerOrExportSuccessClaimed:false};
  }
 };
}
module.exports={observe};
