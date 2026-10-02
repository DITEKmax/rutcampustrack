// Execute only previously unpassed fresh API suffix; transfer is setup, accepted PG/Mongo criteria are not repeated.
import fs from 'node:fs';
import https from 'node:https';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {randomUUID} from 'node:crypto';
const helper='C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/2026-09-27-delivery/transfer-assistant-ui-fixture.mjs';
const {login,listFrom,FINAL_HEADMAN_PASSWORD}=await import(pathToFileURL(helper).href);
const args=new Map();for(let i=2;i<process.argv.length;i+=2)args.set(process.argv[i],process.argv[i+1]);
const need=(ok,message)=>{if(!ok)throw new Error(message);};
const own=path.dirname(fileURLToPath(import.meta.url));
const output=args.get('--out'),fixturePath=args.get('--fixture'),origin=args.get('--origin'),revision=args.get('--revision'),runId=args.get('--run-id');
for(const file of [output,fixturePath])need(file&&path.dirname(path.resolve(file))===own&&path.extname(file)==='.json','Only own evidence JSON paths');
need(/^https:\/\/127\.0\.0\.1:\d+$/.test(origin??'')&&/^[a-f0-9]{40}$/.test(revision??'')&&/^[a-z0-9_-]+$/.test(runId??''),'Exact local origin/source/run required');
need(!fs.existsSync(output),'Never overwrite prior raw evidence');
const id=v=>{need(Number.isSafeInteger(Number(v))&&Number(v)>0,'Positive exact ID');return Number(v);};
const uuid=v=>{need(/^[a-f0-9-]{36}$/.test(v??''),'Exact operation UUID');return v;};
const f=JSON.parse(fs.readFileSync(fixturePath,'utf8'));
for(const k of ['groupId','semesterId','subjectId','assignmentId','headmanStudentId','studentId'])f[k]=id(f[k]);
need(f.scenario==='geo'&&f.headmanLogin&&f.studentLogin,'Existing fixture needs distinct normal student');
const agent=new https.Agent({ca:fs.readFileSync(args.get('--ca')),rejectUnauthorized:true});
const wait=ms=>new Promise(r=>setTimeout(r,ms));
const e={status:'RUNNING',revision,runId,startedAt:new Date().toISOString(),steps:[],checks:[],fixture:{groupId:f.groupId,semesterId:f.semesterId,studentId:f.studentId},limitations:['Future manual marks are prohibited; no marks seeded. Nonempty marks preservation is separately accepted realMongo component proof.','No fake ACK/participant writes; all DB access is read-only.']};
const save=()=>fs.writeFileSync(output,JSON.stringify(e,null,2));
const same=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
const check=(ok,name)=>{(e.criteria??=[]).push({name,passed:!!ok});if(ok)e.checks.push(name);else(e.failures??=[]).push(name);save();};
async function api(auth,route,name,method='GET',body,expected=200,headers={}){
 const u=new URL(route,origin);need(u.origin===origin,'HTTP escaped local stand');
 const data=body===undefined?undefined:Buffer.from(JSON.stringify(body));
 const response=await new Promise((resolve,reject)=>{const req=https.request(u,{agent,method,headers:{Accept:'application/json',Authorization:`Bearer ${auth.token}`,Cookie:auth.cookie,...(data?{'Content-Type':'application/json','Content-Length':String(data.length)}:{}),...headers}},res=>{const chunks=[];let size=0;res.on('data',b=>{size+=b.length;if(size<2*1024*1024)chunks.push(b);});res.on('end',()=>resolve({status:res.statusCode,body:Buffer.concat(chunks).toString(),size}));});req.setTimeout(10000,()=>req.destroy(new Error('Local API deadline')));req.on('error',reject);req.end(data);});
 let parsed=null;try{parsed=JSON.parse(response.body);}catch{}
 e.steps.push({name,status:response.status,body:parsed});if(response.status!==expected)e.rejection={name,status:response.status,type:typeof parsed?.type==='string'?parsed.type:null,code:parsed?.code??parsed?.extras?.code??null,traceId:/^[a-f0-9]{32}$/.test(parsed?.traceId??'')?parsed.traceId:null};save();
 need(response.size<2*1024*1024&&response.status===expected,`${name}: HTTP${response.status} expected${expected}`);return parsed;
}
async function physical(auth,date,exact,name){const rows=listFrom(await api(auth,`/api/schedule/groups/${f.groupId}/lessons?dateFrom=${date}&dateTo=${date}&size=100&status=PLANNED&status=ACTIVE&status=CLOSED&status=CANCELLED&status=TRANSFERRED`,name));const row=rows.find(x=>id(x.id)===id(exact));need(row,`${name}: exact physical ID absent`);(e.physicalSnapshots??={})[name]=row;save();return row;}
const shift=(day,n)=>{const d=new Date(`${day}T00:00:00Z`);d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10);};
const nextStudy=day=>new Date(`${day}T00:00:00Z`).getUTCDay()===0?shift(day,1):day;
try{
 save();
 const h=await login(origin,agent,f.headmanLogin,'HEADMAN',FINAL_HEADMAN_PASSWORD),s=await login(origin,agent,f.studentLogin,'STUDENT',FINAL_HEADMAN_PASSWORD);
 const sourceDate=nextStudy(shift(f.lessonDate,1)),targetDate=nextStudy(shift(sourceDate,1));need(targetDate<=f.semesterDateTo,'Future source/target within fixture semester');
 const created=await api(h,'/api/schedule/one-off-lessons','oneoff.public.create','POST',{groupId:f.groupId,subjectId:f.subjectId,assignmentId:f.assignmentId,date:sourceDate,lessonNumber:2,startTime:'12:00:00',endTime:'13:30:00',classroom:'BUS-ONEOFF'},201,{'Idempotency-Key':randomUUID()});
 e.created=created;save();const sourceId=id(created.physicalLessonId),originId=id(created.id);
 const source=await physical(h,sourceDate,sourceId,'source.snapshot');need(id(source.occurrenceId)>0&&Number(source.occurrenceRevision)>0,'Exact current occurrence revision');
 const hw=await api(h,'/api/academic/homeworks','homework.public.publish','POST',{title:'Owned ONE_OFF bus homework',description:'Synthetic actual Rabbit acceptance',link:null,groupId:f.groupId,subjectId:f.subjectId,semesterId:f.semesterId,lessonDate:sourceDate,lessonNumber:2,bindingMode:'LESSON',requestKey:randomUUID()},201);
 const completed=await api(s,`/api/v1/student/homework/${id(hw.id)}/completion`,'completion.public','PUT',{completed:true});
 const command={targetDate,targetLessonNumber:3,targetStartTime:'14:00:00',targetEndTime:'15:30:00',targetRoom:'BUS-MOVED',expectedRevision:String(source.occurrenceRevision),requestKey:randomUUID()};
 // Transfer API returns accepted202; canonical operation state is polled below.
 const accepted=await api(h,`/api/schedule/lessons/${sourceId}/transfer`,'transfer.accept','POST',command,202);uuid(accepted.operationId);const targetId=id(accepted.targetLessonId);
 const deadline=Date.now()+90000;let operation=accepted;e.operation=accepted;save();
 while(operation.state==='PENDING'&&Date.now()<deadline){await wait(500);operation=await api(h,`/api/schedule/lesson-transfers/${accepted.operationId}`,'transfer.wait.actualACK');}
 e.operation=operation;save();
 need(operation.state==='COMPLETED'&&operation.operationId===accepted.operationId,'Prerequisite transfer did not complete');
 const current=await physical(h,targetDate,targetId,'target.fresh'),old=await physical(h,sourceDate,sourceId,'source.history.fresh');
 const fresh=await api(h,`/api/academic/homeworks/${id(hw.id)}`,'homework.fresh');
 const feedResponse=await api(s,`/api/v1/student/homework?from=${sourceDate}&to=${targetDate}`,'completion.feed.fresh');
 const matching=(feedResponse?.items??[]).filter(x=>String(x.id)===String(hw.id)),feed=matching[0];
 const report=await api(h,`/api/attendance/reports/lesson/${targetId}`,'attendance.fresh.report');
 const entry=report?.entries?.find(x=>String(x.userId)===String(f.studentId));
 const list=listFrom(await api(h,`/api/schedule/one-off-lessons?groupId=${f.groupId}&dateFrom=${sourceDate}&dateTo=${targetDate}`,'oneoff.current.list'));
 const oneoffs=list.filter(x=>String(x.id)===String(originId)),oneoff=oneoffs[0];
 const exact=(a,b)=>a!==undefined&&b!==undefined&&String(a)===String(b);
 check(exact(fresh?.id,hw.id)&&exact(fresh?.bindingId,hw.bindingId)&&exact(fresh?.publishedBy,hw.publishedBy)&&fresh?.requestKey===hw.requestKey&&fresh?.lessonDate===targetDate&&fresh?.lessonNumber===3&&fresh?.bindingMode==='LESSON'&&fresh?.archived===false,'Fresh Academic GET retains homework identity at target date');
 check(matching.length===1&&feed?.completed===true&&feed?.lessonDate===targetDate&&feed?.lessonNumber===3&&feed?.bindingMode==='LESSON'&&feed?.archived===false&&completed?.completed===true&&completed?.completedAt!==null&&feed?.completedAt===completed?.completedAt,'Fresh BFF feed retains exact completion at target without historical duplicate');
 check(exact(report?.lessonId,targetId)&&exact(report?.groupId,f.groupId)&&report?.lessonDate===targetDate&&report?.editable===false&&entry!==undefined&&entry.source==null,'Fresh Attendance GET resolves moved future physical lesson without fabricated mark');
 check(oneoffs.length===1&&exact(oneoff?.physicalLessonId,targetId)&&oneoff?.date===targetDate&&oneoff?.lessonNumber===3,'Fresh ONE_OFF list returns exactly one current origin at target date');
 e.operation=operation;e.status=e.failures?.length?'FAIL':'PASS';if(e.failures?.length)e.failure=e.failures.join('; ');
}catch(err){e.failure=String(err.message).slice(0,1000);e.status='FAIL';}finally{e.finishedAt=new Date().toISOString();save();agent.destroy();console.log(JSON.stringify({status:e.status,steps:e.steps.length,checks:e.checks.length,failure:e.failure??null,evidence:output}));}
process.exitCode=e.status==='PASS'?0:1;
