// One bounded real Rabbit scenario; no participant/ACK/mark writes.
import fs from 'node:fs';
import https from 'node:https';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {randomUUID} from 'node:crypto';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
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
const ids=Object.fromEntries(['pg-academic','pg-schedule','mongo','rabbit'].map(n=>[n,args.get(`--${n}-id`)]));
need(Object.values(ids).every(v=>/^[a-f0-9]{64}$/.test(v??'')),'Exact owned container IDs required');
const agent=new https.Agent({ca:fs.readFileSync(args.get('--ca')),rejectUnauthorized:true});
const run=promisify(execFile);const wait=ms=>new Promise(r=>setTimeout(r,ms));
const e={status:'RUNNING',revision,runId,startedAt:new Date().toISOString(),steps:[],checks:[],fixture:{groupId:f.groupId,semesterId:f.semesterId,studentId:f.studentId},limitations:['Future manual marks are prohibited; no marks seeded. Nonempty marks preservation is separately accepted realMongo component proof.','No fake ACK/participant writes; all DB access is read-only.']};
const save=()=>fs.writeFileSync(output,JSON.stringify(e,null,2));
const same=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
const check=(ok,name)=>{need(ok,name);e.checks.push(name);save();};
async function docker(argv){try{return(await run('docker',argv,{timeout:6000,maxBuffer:256*1024,windowsHide:true})).stdout.trim();}catch(err){throw new Error(`Owned read-only Docker failed: ${err.code??'unknown'}`);}}
async function owned(name){const data=(await docker(['inspect','--format','{{.Id}}|{{index .Config.Labels "rct.runtime-owner"}}|{{index .Config.Labels "rct.runtime-run"}}|{{.State.Running}}|{{.State.Paused}}|{{.State.OOMKilled}}',ids[name]])).split('|');need(data[0]===ids[name]&&data[1]==='student-requests-gate'&&data[2]===runId&&data[3]==='true'&&data[4]==='false'&&data[5]==='false',`Owned resource mismatch ${name}`);}
async function pg(name,sql){await owned(name);return JSON.parse(await docker(['exec',ids[name],'psql','-q','-t','-A','-v','ON_ERROR_STOP=1','-U','rct_user','-d',name==='pg-academic'?'academic_db':'schedule_db','-c',`BEGIN READ ONLY;SET LOCAL statement_timeout='2000ms';${sql};COMMIT;`]));}
async function mongo(source,target,op){await owned('mongo');return JSON.parse(await docker(['exec',ids.mongo,'mongosh','--quiet','--eval',`const d=db.getSiblingDB('attendance_db');const r=d.lesson_transfer_receipts.findOne({_id:'${uuid(op)}'});print(JSON.stringify({marks:d.attendances.countDocuments({user_id:NumberLong('${f.studentId}'),lesson_id:{$in:[NumberLong('${id(source)}'),NumberLong('${id(target)}')]}}),receipt:r?{id:r._id,eventVersion:r.event_version,result:r.result,occurrenceId:String(r.occurrence_id),sourceLessonId:String(r.source_snapshot.lesson_id),targetLessonId:String(r.target_snapshot.lesson_id),sourceOrigin:String(r.source_snapshot.one_off_lesson_id),targetOrigin:String(r.target_snapshot.one_off_lesson_id)}:null}));`]));}
async function api(auth,route,name,method='GET',body,expected=200,headers={}){
 const u=new URL(route,origin);need(u.origin===origin,'HTTP escaped local stand');
 const data=body===undefined?undefined:Buffer.from(JSON.stringify(body));
 const response=await new Promise((resolve,reject)=>{const req=https.request(u,{agent,method,headers:{Accept:'application/json',Authorization:`Bearer ${auth.token}`,Cookie:auth.cookie,...(data?{'Content-Type':'application/json','Content-Length':String(data.length)}:{}),...headers}},res=>{const chunks=[];let size=0;res.on('data',b=>{size+=b.length;if(size<2*1024*1024)chunks.push(b);});res.on('end',()=>resolve({status:res.statusCode,body:Buffer.concat(chunks).toString(),size}));});req.setTimeout(10000,()=>req.destroy(new Error('Local API deadline')));req.on('error',reject);req.end(data);});
 let parsed=null;try{parsed=JSON.parse(response.body);}catch{}
 e.steps.push({name,status:response.status});if(response.status!==expected)e.rejection={name,status:response.status,type:typeof parsed?.type==='string'?parsed.type:null,code:parsed?.code??parsed?.extras?.code??null,traceId:/^[a-f0-9]{32}$/.test(parsed?.traceId??'')?parsed.traceId:null};save();
 need(response.size<2*1024*1024&&response.status===expected,`${name}: HTTP${response.status} expected${expected}`);return parsed;
}
async function physical(auth,date,exact,name){const rows=listFrom(await api(auth,`/api/schedule/groups/${f.groupId}/lessons?dateFrom=${date}&dateTo=${date}&size=100&status=PLANNED&status=ACTIVE&status=CLOSED&status=CANCELLED&status=TRANSFERRED`,name));const row=rows.find(x=>id(x.id)===id(exact));need(row,`${name}: exact physical ID absent`);(e.physicalSnapshots??={})[name]=row;save();return row;}
const shift=(day,n)=>{const d=new Date(`${day}T00:00:00Z`);d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10);};
const nextStudy=day=>new Date(`${day}T00:00:00Z`).getUTCDay()===0?shift(day,1):day;
async function academicStore(hw,op){return pg('pg-academic',`SELECT json_build_object('homework',(SELECT json_build_object('id',id,'bindingId',binding_id,'publisher',published_by,'requestKey',request_key,'date',lesson_date,'number',lesson_number,'mode',binding_mode) FROM homeworks WHERE id=${id(hw.id)}),'completion',coalesce((SELECT json_agg(json_build_object('id',id,'studentId',student_id,'completedAt',completed_at) ORDER BY id) FROM homework_completions WHERE homework_id=${id(hw.id)}),'[]'::json),'receipts',coalesce((SELECT json_agg(json_build_object('batch',batch_index,'result',result,'source',source_lesson_id,'target',target_lesson_id) ORDER BY batch_index) FROM lesson_transfer_receipts WHERE operation_id='${uuid(op)}'),'[]'::json),'history',coalesce((SELECT json_agg(json_build_object('bindingId',binding_id,'homeworkId',homework_id,'occurrenceId',occurrence_id,'source',source_lesson_id,'target',target_lesson_id,'state',result_state) ORDER BY binding_id) FROM homework_binding_transfer_history WHERE operation_id='${op}'),'[]'::json))`);}
async function scheduleStore(hw,op,originId){return pg('pg-schedule',`SELECT json_build_object('origin',(SELECT json_build_object('id',id,'date',date,'number',lesson_number,'physicalId',physical_lesson_id) FROM schedule_one_off_lessons WHERE id=${id(originId)}),'binding',(SELECT json_build_object('id',binding_id,'actor',actor_id,'key',request_key,'hash',encode(payload_hash,'hex'),'originalOccurrence',original_occurrence_id,'originalMode',original_binding_mode,'originalDate',original_date,'originalNumber',original_lesson_number,'group',group_id,'subject',subject_id,'semester',semester_id,'occurrence',occurrence_id,'lesson',current_lesson_id,'homework',homework_id,'state',state) FROM lesson_homework_bindings WHERE binding_id=${id(hw.bindingId)}),'physical',(SELECT json_agg(json_build_object('id',id,'occurrence',occurrence_id,'generation',generation,'date',date,'status',status) ORDER BY generation) FROM lessons WHERE one_off_lesson_id=${originId}),'ack',coalesce((SELECT json_agg(json_build_object('participant',participant,'batch',batch_index,'result',result) ORDER BY participant,batch_index) FROM lesson_transfer_participant_receipts WHERE operation_id='${uuid(op)}'),'[]'::json),'event',(SELECT json_build_object('version',(payload->>'event_version')::int,'sourceOrigin',payload->'payload'->'source'->>'one_off_lesson_id','targetOrigin',payload->'payload'->'target'->>'one_off_lesson_id') FROM schedule_outbox WHERE event_type='lesson.transfer.requested' AND payload->'payload'->>'operation_id'='${op}' LIMIT1))`.replace('LIMIT1','LIMIT 1'));}
try{
 for(const n of Object.keys(ids))await owned(n);save();
 const h=await login(origin,agent,f.headmanLogin,'HEADMAN',FINAL_HEADMAN_PASSWORD),s=await login(origin,agent,f.studentLogin,'STUDENT',FINAL_HEADMAN_PASSWORD);
 const sourceDate=nextStudy(shift(f.lessonDate,1)),targetDate=nextStudy(shift(sourceDate,1));need(targetDate<=f.semesterDateTo,'Future source/target within fixture semester');
 const created=await api(h,'/api/schedule/one-off-lessons','oneoff.public.create','POST',{groupId:f.groupId,subjectId:f.subjectId,assignmentId:f.assignmentId,date:sourceDate,lessonNumber:2,startTime:'12:00:00',endTime:'13:30:00',classroom:'BUS-ONEOFF'},201,{'Idempotency-Key':randomUUID()});
 e.created=created;save();const sourceId=id(created.physicalLessonId),originId=id(created.id);
 const source=await physical(h,sourceDate,sourceId,'source.snapshot');need(id(source.occurrenceId)>0&&Number(source.occurrenceRevision)>0,'Exact current occurrence revision');
 const hw=await api(h,'/api/academic/homeworks','homework.public.publish','POST',{title:'Owned ONE_OFF bus homework',description:'Synthetic actual Rabbit acceptance',link:null,groupId:f.groupId,subjectId:f.subjectId,semesterId:f.semesterId,lessonDate:sourceDate,lessonNumber:2,bindingMode:'LESSON',requestKey:randomUUID()},201);
 await api(s,`/api/v1/student/homework/${id(hw.id)}/completion`,'completion.public','PUT',{completed:true});
 const provisional=randomUUID(),before=await academicStore(hw,provisional),bindingBefore=(await scheduleStore(hw,provisional,originId)).binding;e.before={academic:before,binding:bindingBefore};save();need(before.completion.length===1&&id(before.completion[0].studentId)===f.studentId,'Own completed publication before transfer');
 const command={targetDate,targetLessonNumber:3,targetStartTime:'14:00:00',targetEndTime:'15:30:00',targetRoom:'BUS-MOVED',expectedRevision:String(source.occurrenceRevision),requestKey:randomUUID()};
 // Transfer API returns accepted202; canonical operation state is polled below.
 const accepted=await api(h,`/api/schedule/lessons/${sourceId}/transfer`,'transfer.accept','POST',command,202);uuid(accepted.operationId);const targetId=id(accepted.targetLessonId);
 const deadline=Date.now()+90000;let operation=accepted;e.operation=accepted;save();
 while(operation.state==='PENDING'&&Date.now()<deadline){await wait(500);operation=await api(h,`/api/schedule/lesson-transfers/${accepted.operationId}`,'transfer.wait.actualACK');}
 e.operation=operation;save();
 check(operation.state==='COMPLETED'&&operation.operationId===accepted.operationId,'Actual bus ACK completes exact durable operation');
 const replay=await api(h,`/api/schedule/lessons/${sourceId}/transfer`,'transfer.exact.retry','POST',command,200);
 check(replay.operationId===operation.operationId&&replay.targetLessonId===operation.targetLessonId&&replay.state==='COMPLETED','One exact retry preserves operation and target');
 const [after,placement,marks]=await Promise.all([academicStore(hw,operation.operationId),scheduleStore(hw,operation.operationId,originId),mongo(sourceId,targetId,operation.operationId)]);e.stores={before,after,placement,marks};save();
 const current=await physical(h,targetDate,targetId,'target.fresh'),old=await physical(h,sourceDate,sourceId,'source.history.fresh');
 check(id(current.occurrenceId)===id(source.occurrenceId)&&current.current===true&&current.date===targetDate&&current.generation===source.generation+1&&old.status==='TRANSFERRED'&&old.current===false,'Fresh physical API retains source history and current moved generation');
 const fresh=await api(h,`/api/academic/homeworks/${id(hw.id)}`,'homework.fresh');const feed=(await api(s,`/api/v1/student/homework?from=${sourceDate}&to=${targetDate}`,'completion.feed.fresh')).items?.find(x=>id(x.id)===id(hw.id));
 check(id(fresh.id)===id(hw.id)&&id(fresh.bindingId)===id(hw.bindingId)&&fresh.requestKey===hw.requestKey&&id(fresh.publishedBy)===id(hw.publishedBy)&&fresh.lessonDate===targetDate&&fresh.lessonNumber===3&&feed?.completed===true&&same(before.completion,after.completion),'Fresh homework identity and original completion retained on target');
 check(placement.physical.length===2&&placement.origin.date===sourceDate&&placement.origin.number===2&&id(placement.origin.physicalId)===targetId&&id(placement.binding.homework)===id(hw.id)&&id(placement.binding.lesson)===targetId&&id(placement.binding.originalOccurrence)===id(source.occurrenceId),'Exact origin creation intent and binding point to one new target');
 const identity=x=>['id','actor','key','hash','originalOccurrence','originalMode','originalDate','originalNumber','group','subject','semester'].map(k=>x[k]);check(same(identity(bindingBefore),identity(placement.binding)),'Exact immutable binding create identity retained across actual bus transfer');
 check(after.receipts.length===1&&after.receipts[0].result==='APPLIED'&&after.history.length===1&&after.history[0].state==='MOVED'&&id(after.history[0].homeworkId)===id(hw.id),'Actual Academic durable receipt/history applied once');
 check(placement.ack.length===2&&placement.ack.every(x=>x.result==='APPLIED')&&['ACADEMIC','ATTENDANCE'].every(x=>placement.ack.some(a=>a.participant===x)),'Schedule durable ACKs from both actual participants');
 check(marks.marks===0&&marks.receipt?.result==='APPLIED'&&marks.receipt.eventVersion===2&&id(marks.receipt.sourceLessonId)===sourceId&&id(marks.receipt.targetLessonId)===targetId&&id(marks.receipt.sourceOrigin)===originId&&id(marks.receipt.targetOrigin)===originId,'Actual Attendance v2 receipt with no fabricated future marks');
 const report=await api(h,`/api/attendance/reports/lesson/${targetId}`,'attendance.fresh.report');const entry=report.entries?.find(x=>id(x.userId)===f.studentId);check(id(report.lessonId)===targetId&&report.lessonDate===targetDate&&report.editable===false&&entry!==undefined&&entry.source==null,'Fresh Attendance API shows moved future lesson without fabricated manual mark');
 const list=listFrom(await api(h,`/api/schedule/one-off-lessons?groupId=${f.groupId}&dateFrom=${targetDate}&dateTo=${targetDate}`,'oneoff.current.list'));check(list.some(x=>id(x.id)===originId&&id(x.physicalLessonId)===targetId&&x.date===targetDate&&x.lessonNumber===3),'Public oneoff projection follows moved physical date');
 e.operation=operation;e.status='PASS';
}catch(err){e.failure=String(err.message).slice(0,1000);e.status='FAIL';}finally{e.finishedAt=new Date().toISOString();save();agent.destroy();console.log(JSON.stringify({status:e.status,steps:e.steps.length,checks:e.checks.length,failure:e.failure??null,evidence:output}));}
process.exitCode=e.status==='PASS'?0:1;
