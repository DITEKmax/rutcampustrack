const query = "const d=db.getSiblingDB('attendance_db'),sid=42,rid='abcdef0123456789abcdef01';\nconst reqFilter=rid===null?{}:{_id:new ObjectId(rid),student_id:sid};\nconst attFilter=rid===null?{}:{request_id:rid};\nconst outFilter=rid===null?{event_type:'excuse.requested'}:{event_type:'excuse.requested',payload:{'$regex':rid}};\nfunction exactJsonInteger(value,context){\n  if(typeof value==='number'){\n    if(!Number.isSafeInteger(value))throw new Error(context+' must be an exact safe JSON integer');\n    return value;\n  }\n  if(value===null||typeof value!=='object'||value._bsontype!=='Long'||typeof value.toString!=='function'||typeof value.toNumber!=='function'){\n    throw new Error(context+' must be a native BSON Long or safe JSON integer');\n  }\n  const decimal=value.toString();\n  if(!/^-?\\d+$/.test(decimal))throw new Error(context+' BSON Long has an invalid decimal representation');\n  let exact;\n  try{exact=BigInt(decimal);}catch(e){throw new Error(context+' BSON Long cannot be represented exactly');}\n  const number=value.toNumber();\n  if(!Number.isSafeInteger(number)||BigInt(number)!==exact)throw new Error(context+' BSON Long is outside the safe JSON integer range');\n  return number;\n}\nfunction nonNegativeJsonInteger(value,context){\n  const integer=exactJsonInteger(value,context);\n  if(integer<0)throw new Error(context+' must be non-negative');\n  return integer;\n}\nprint(JSON.stringify({\n  excuseTickets:d.excuse_tickets.countDocuments({student_id:sid}),\n  receipts:d.student_request_receipts.countDocuments({student_id:sid,command_kind:'EXCUSE'}),\n  attachments:d.request_attachments.countDocuments({owner_student_id:sid}),\n  outboxRequested:d.attendance_outbox.countDocuments({event_type:'excuse.requested'}),\n  requestTickets:d.excuse_tickets.countDocuments(reqFilter),\n  requestTicketStatus:rid===null?null:(d.excuse_tickets.find(reqFilter,{_id:1,status:1}).sort({_id:1}).toArray().map(x=>x.status)[0]??null),\n  requestAttachments:d.request_attachments.find(attFilter,{_id:1,name:1,type:1,size:1,sha256:1,state:1,uploaded_at:1,expires_at:1,expired_at:1,request_id:1,position:1}).sort({position:1}).toArray().map(x=>({id:String(x._id),name:x.name,content_type:x.type,size:nonNegativeJsonInteger(x.size,'request_attachments.size'),sha256:x.sha256,state:x.state,uploaded_at:x.uploaded_at==null?null:new Date(x.uploaded_at).toISOString(),expires_at:x.expires_at==null?null:new Date(x.expires_at).toISOString(),expired_at:x.expired_at==null?null:new Date(x.expired_at).toISOString(),request_id:x.request_id})),\n  requestOutbox:d.attendance_outbox.find(outFilter,{_id:1,event_type:1,payload:1,status:1}).toArray().map(x=>{const envelope=JSON.parse(x.payload),payload=envelope.payload||{};return {id:String(x._id),event_type:x.event_type,status:x.status,payloadTicketId:payload.ticket_id==null?null:String(payload.ticket_id),payloadAttachments:(payload.attachments||[]).map(a=>({id:a.id,name:a.name,content_type:a.content_type,size:nonNegativeJsonInteger(a.size,'outbox.payload.attachments[].size'),sha256:a.sha256,state:a.state,uploaded_at:a.uploaded_at,expires_at:a.expires_at,expired_at:a.expired_at==null?null:a.expired_at}))}})\n}));";
const rid='abcdef0123456789abcdef01';
const descriptors=[
 {id:'abcdef0123456789abcdef02',name:'fixture.pdf',content_type:'application/pdf',size:10485760,sha256:'a'.repeat(64),state:'ACTIVE',uploaded_at:'2026-09-20T00:00:00.000Z',expires_at:'2026-09-21T00:00:00.000Z',expired_at:null},
 {id:'abcdef0123456789abcdef03',name:'fixture.png',content_type:'image/png',size:10485760,sha256:'b'.repeat(64),state:'ACTIVE',uploaded_at:'2026-09-20T00:00:01.000Z',expires_at:'2026-09-21T00:00:01.000Z',expired_at:null}
];
const cases=[
 ['nativeLong',()=>NumberLong('10485760'),true],
 ['number',()=>10485760,true],
 ['unsafeLong',()=>NumberLong('9007199254740993'),false],
 ['negativeLong',()=>NumberLong('-1'),false],
 ['fraction',()=>1.5,false],
 ['numericString',()=>'10485760',false],
 ['missing',()=>undefined,false],
 ['null',()=>null,false]
];
const results=[];
for(const [name,value,expectedAccept] of cases){
 const attachments=descriptors.map((x,i)=>({_id:new ObjectId(x.id),name:x.name,type:x.content_type,size:value(),sha256:x.sha256,state:x.state,uploaded_at:new Date(x.uploaded_at),expires_at:new Date(x.expires_at),expired_at:null,request_id:rid,position:i}));
 const outbox=[{_id:new ObjectId('abcdef0123456789abcdef04'),event_type:'excuse.requested',status:'NEW',payload:JSON.stringify({payload:{ticket_id:rid,attachments:descriptors}})}];
 const collections={
  excuse_tickets:{countDocuments(){return 1},find(){const c={sort(){return c},toArray(){return [{_id:new ObjectId(rid),status:'submitted'}]}};return c}},
  student_request_receipts:{countDocuments(){return 1}},
  request_attachments:{countDocuments(){return 2},find(){const c={sort(){return c},toArray(){return attachments}};return c}},
  attendance_outbox:{countDocuments(){return 1},find(){return {toArray(){return outbox}}}}
 };
 const db={getSiblingDB(name){if(name!=='attendance_db')throw Error('Unexpected database');return collections}};
 let snapshot=null;
 try {new Function('db','print',query)(db,text=>{snapshot=JSON.parse(text)});results.push({name,expectedAccept,snapshot,error:null})}
 catch(e){results.push({name,expectedAccept,snapshot:null,error:String(e.message)})}
}
print(JSON.stringify({schema:'rct.h80.native-bson.v1',results,descriptors}));