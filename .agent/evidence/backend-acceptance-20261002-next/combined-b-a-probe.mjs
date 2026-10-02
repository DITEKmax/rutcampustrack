// PREPARED ONLY: DATE lifecycle first, independent DATE-inclusive semester recovery last.
// Product/shared runner read-only. Requires explicit root freeze + lease before execution.
import fs from 'node:fs';
import https from 'node:https';
import { promisify } from 'node:util';
import { execFile } from 'node:child_process';
import { randomUUID, createHash } from 'node:crypto';
import { deflateSync } from 'node:zlib';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { login, listFrom, FINAL_HEADMAN_PASSWORD } from '../../orchestration-v2/evidence/2026-09-27-delivery/transfer-assistant-ui-fixture.mjs';

const runCommand = promisify(execFile);
const argv = new Map();
for (let i = 2; i < process.argv.length; i += 2) argv.set(process.argv[i], process.argv[i + 1]);
function need(ok, message) { if (!ok) throw new Error(message); }
const origin = argv.get('--origin'), output = argv.get('--out'), revision = argv.get('--revision'), runId = argv.get('--run-id');
const scope = argv.get('--scope');
need(['A-only', 'DATE-homework', 'B-regrant','Geo-valid','Geo-block'].includes(scope), 'Explicit focused scope required; full B/geo lifecycle replay is disabled');
need(origin && output && argv.get('--ca') && argv.get('--fixture') && /^[a-f0-9]{40}$/.test(revision ?? '') && /^[a-z0-9_-]+$/.test(runId ?? ''), 'Required local origin, public CA, owned fixture, output, full revision and run ID');
const ownDirectory=path.dirname(fileURLToPath(import.meta.url));
const insideOwnDirectory=value=>path.resolve(value).startsWith(`${ownDirectory}${path.sep}`);
need(insideOwnDirectory(output)&&insideOwnDirectory(argv.get('--fixture'))&&path.extname(output)==='.json'&&path.extname(argv.get('--fixture'))==='.json','Evidence output and synthetic fixture must be JSON files in own next evidence directory');
need(!path.basename(argv.get('--ca')).toLowerCase().includes('key'),'Public CA certificate required; private key paths refused');
const url = new URL(origin);
need(url.protocol === 'https:' && url.hostname === '127.0.0.1' && !url.username && !url.password && url.pathname === '/' && !url.search && !url.hash, 'Only trusted local HTTPS origin');
const containerIds = Object.fromEntries(['pg-academic', 'pg-schedule', 'mongo', 'rabbit', 'attendance', 'bff'].map(name => [name, argv.get(`--${name}-id`)]));
need(Object.values(containerIds).every(id => /^[a-f0-9]{64}$/.test(id ?? '')), 'Exact complete owned container IDs required');
const fixture = JSON.parse(fs.readFileSync(argv.get('--fixture'), 'utf8'));
const id = value => { need(Number.isSafeInteger(Number(value)) && Number(value) > 0, 'Positive synthetic ID required'); return Number(value); };
for (const field of ['groupId', 'semesterId', 'lessonId', 'subjectId', 'headmanStudentId', 'studentId']) fixture[field] = id(fixture[field]);
need(fixture.scenario === 'geo' && fixture.headmanLogin && fixture.studentLogin, 'Existing geo fixture with separate normal student required');
const agent = new https.Agent({ ca: fs.readFileSync(argv.get('--ca')), rejectUnauthorized: true });
const fixtureMetadata = Object.fromEntries(['groupId','semesterId','lessonId','subjectId','headmanStudentId','studentId','headmanLogin','studentLogin','lessonDate'].map(field=>[field,fixture[field]]));
const evidence = { status: 'RUNNING', scope, revision, runId, startedAt: new Date().toISOString(), fixture: fixtureMetadata, steps: [], checks: [], pauseTimeline: [], limitations: ['Previously passed B/geo criteria remain immutable and are not in the next wrapper scopes.', 'DATE midnight/revocation/foreign-manager boundaries use existing component evidence; no clock control.', 'HTTP absence proves inaccessible file; accepted component IT provides physical orphan-byte coverage.'] };
const paused = new Map();
let outageExpired = false;
function save() { fs.writeFileSync(output, JSON.stringify(evidence, null, 2)); }
function check(ok, name) { need(ok, name); evidence.checks.push(name); }
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
function canonical(value) { if (Array.isArray(value)) return value.map(canonical); if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])); return value; }
const same = (a, b) => JSON.stringify(canonical(a)) === JSON.stringify(canonical(b));
function retainedMetadataView(detail){
  const pick=(value,fields)=>Object.fromEntries(fields.map(field=>[field,value?.[field]??null]));
  const summary=pick(detail?.summary,['id','kind','status','origin','createdAt','updatedAt']);
  summary.lessons=(detail?.summary?.lessons??[]).map(lesson=>pick(lesson,['id','lessonNumber','status','blocked','subjectId','subjectType','semesterId','date','startsAt','endsAt']));
  return {summary,reason:detail?.reason??null,decisionAt:detail?.decision?.decidedAt??null,attachments:(detail?.attachments??[]).map(attachment=>pick(attachment,['id','contentType','sizeBytes','sha256','state','uploadedAt','expiresAt','expiredAt']))};
}
function metadataDiff(before,after,prefix=''){
  if(same(before,after))return [];
  if(before&&after&&typeof before==='object'&&typeof after==='object')return [...new Set([...Object.keys(before),...Object.keys(after)])].flatMap(key=>metadataDiff(before[key],after[key],prefix?`${prefix}.${key}`:key));
  return [{field:prefix,before:before??null,after:after??null}];
}

function http(auth, route, { method = 'GET', body, headers = {}, timeoutMs = 10000 } = {}) {
  const target = new URL(route, origin); need(target.origin === origin, 'Request escaped local origin');
  const encoded = body === undefined ? undefined : Buffer.isBuffer(body) ? body : Buffer.from(JSON.stringify(body));
  const requestHeaders = { Accept: 'application/json', Authorization: `Bearer ${auth.token}`, Cookie: auth.cookie, ...headers };
  if (encoded) { requestHeaders['Content-Length'] = String(encoded.length); if (!Buffer.isBuffer(body)) requestHeaders['Content-Type'] = 'application/json'; }
  return new Promise((resolve, reject) => {
    let timer;
    const request = https.request(target, { method, agent, headers: requestHeaders }, response => {
      let size = 0; const parts = [];
      response.on('data', part => { size += part.length; if (size <= 20 * 1024 * 1024) parts.push(part); });
      response.once('error', reject);
      response.on('end', () => { clearTimeout(timer); resolve({ status: response.statusCode, headers: response.headers, bytes: Buffer.concat(parts), truncated: size > 20 * 1024 * 1024 }); });
    });
    timer = setTimeout(() => request.destroy(new Error('Absolute local API deadline exceeded')), timeoutMs);
    request.once('error', error => { clearTimeout(timer); reject(error); });
    request.end(encoded);
  });
}
async function api(auth, route, name, method = 'GET', body, expected = 200, headers = {}, timeoutMs = 10000) {
  const response = await http(auth, route, { method, body, headers, timeoutMs });
  evidence.steps.push({ name, status: response.status });
  let data = null; try { data = JSON.parse(response.bytes.toString('utf8')); } catch { /* 204 / binary */ }
  if(name==='A.delete.async'&&response.status===409){
    if(response.truncated)data=null;
    const code=value=>typeof value==='string'&&/^[A-Z][A-Z0-9_]{0,63}$/.test(value)?value:null;
    evidence.A.deleteRejected={httpStatus:409,bodyParsed:!!data&&!response.truncated,operationId:/^[a-f0-9-]{36}$/.test(data?.operationId??'')?data.operationId:null,code:code(data?.code??data?.extras?.code),phase:['PREPARING','RELEASING','DELETING','COMPLETED','CANCELLED'].includes(data?.phase)?data.phase:null,cancelReason:code(data?.cancelReason??data?.reason),refreshedPreview:sanitizedPreview(data?.refreshedPreview)};
    save();
    console.log(JSON.stringify({status:'DELETE_NOT_ACCEPTED',httpStatus:409,phase:evidence.A.deleteRejected.phase,cancelReason:evidence.A.deleteRejected.cancelReason,evidence:output}));
    throw new Error('DELETE_NOT_ACCEPTED: HTTP 409; structured sanitized response saved; recovery not polled');
  }
  need(!response.truncated && response.status === expected, `${name}: HTTP ${response.status}; expected ${expected}`);
  return { data, response };
}
function sanitizedPreview(value){
  if(!value||typeof value!=='object')return null;
  const integer=number=>Number.isSafeInteger(number)&&number>=0?number:null;
  return {semesterId:integer(value.semesterId),priorState:['ACTIVE','INACTIVE','ARCHIVED'].includes(value.priorState)?value.priorState:null,stateVersion:integer(value.stateVersion),previewDigest:/^[a-f0-9]{64}$/.test(value.previewDigest??'')?value.previewDigest:null,counts:Object.fromEntries(['scheduleTemplates','oneOffLessons','lessons','assignments','homeworks','attendanceMarks','studentRequests'].map(field=>[field,integer(value.counts?.[field])]))};
}
async function docker(arguments_, purpose, timeout = 2000) {
  try { return (await runCommand('docker', arguments_, { timeout, maxBuffer: 128 * 1024, windowsHide: true })).stdout.trim(); }
  catch (error) { throw new Error(`${purpose}: Docker command failed (${error.code ?? 'unknown'})`); }
}
async function owned(name, requireRunning = true, timeout = 1000) {
  const exactId = containerIds[name];
  // One inspect round trip; no Env, mounts, command or credentials are returned.
  const fields = (await docker(['inspect', '--format', '{{.Id}}|{{.Name}}|{{index .Config.Labels "rct.runtime-owner"}}|{{index .Config.Labels "rct.runtime-run"}}|{{.State.Running}}|{{.State.Paused}}|{{.State.OOMKilled}}|{{with index .State "Health"}}{{.Status}}{{else}}none{{end}}', exactId], `inspect exact ownership/state ${name}`, timeout)).split('|');
  need(fields.length === 8 && fields[0] === exactId && fields[1] === `/rct-requests-${runId}-${name}` && fields[2] === 'student-requests-gate' && fields[3] === runId, `Ownership mismatch ${name}`);
  const state = { Running:fields[4]==='true', Paused:fields[5]==='true', OOMKilled:fields[6]==='true', health:fields[7] };
  if (requireRunning) need(state.Running && !state.OOMKilled && state.health !== 'unhealthy', `Unavailable/OOM owned ${name}`);
  return state;
}
async function pg(sql) {
  // Exact immutable IDs were checked initially; while paused, avoid extra round trips
  // against unchanged read-only stores. All mutations still recheck exact ownership.
  if (!paused.size) await owned('pg-academic');
  return JSON.parse(await docker(['exec', containerIds['pg-academic'], 'psql', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1', '-U', 'rct_user', '-d', 'academic_db', '-c', `BEGIN READ ONLY; SET LOCAL statement_timeout='1000ms'; ${sql}; COMMIT;`], 'read-only exact Academic observation', 2000));
}
async function schedulePg(sql) {
  await owned('pg-schedule');
  return JSON.parse(await docker(['exec',containerIds['pg-schedule'],'psql','-q','-t','-A','-v','ON_ERROR_STOP=1','-U','rct_user','-d','schedule_db','-c',`BEGIN READ ONLY; SET LOCAL statement_timeout='1000ms'; ${sql}; COMMIT;`],'read-only exact Schedule DATE observation',2000));
}
async function receiptCount(operationId) {
  need(/^[a-f0-9-]{36}$/.test(operationId), 'Canonical synthetic operation UUID required');
  if (!paused.size) await owned('mongo');
  return Number(await docker(['exec', containerIds.mongo, 'mongosh', '--quiet', '--eval', `print(db.getSiblingDB('attendance_db').semester_deletion_participant_receipts.countDocuments({operation_id:'${operationId}',command:'PREPARE_DELETE'}))`], 'read-only exact participant receipt count', 2000));
}
async function retainedMarkCounts(target, lesson) {
  target=id(target);lesson=id(lesson);await owned('mongo');
  return JSON.parse(await docker(['exec',containerIds.mongo,'mongosh','--quiet','--eval',`const c=db.getSiblingDB('attendance_db').attendances;print(JSON.stringify({total:c.countDocuments({user_id:NumberLong('${target}')}),pair:c.countDocuments({user_id:NumberLong('${target}'),lesson_id:NumberLong('${lesson}')})}))`],'read-only independently counted retained target marks',3000));
}
async function userCounts(target) {
  target = id(target);
  return pg(`SELECT json_build_object('grants',COALESCE((SELECT json_agg(json_build_object('id',id,'role',role,'status',status,'groupId',group_id) ORDER BY id) FROM user_role_grants WHERE user_id=${target}),'[]'::json),'historyCount',(SELECT count(*) FROM student_group_history WHERE user_id=${target}),'openHistoryCount',(SELECT count(*) FROM student_group_history WHERE user_id=${target} AND left_at IS NULL),'openTodayCount',(SELECT count(*) FROM student_group_history WHERE user_id=${target} AND left_at IS NULL AND joined_at=(CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Moscow')::date),'selectedSessions',(SELECT count(*) FROM auth_sessions WHERE user_id=${target} AND revoked_at IS NULL AND active_role_grant_id IS NOT NULL),'activeHelpers',(SELECT count(*) FROM headman_assistants WHERE group_id=${fixture.groupId} AND is_active))`);
}
async function operationState(operationId) {
  need(/^[a-f0-9-]{36}$/.test(operationId), 'Canonical synthetic operation UUID required');
  return pg(`SELECT json_build_object('exists',EXISTS(SELECT 1 FROM semester_archive_operations WHERE operation_id='${operationId}' AND semester_id=${fixture.semesterId}),'phase',(SELECT delete_phase FROM semester_archive_operations WHERE operation_id='${operationId}'),'attendanceStatus',(SELECT attendance_status FROM semester_archive_operations WHERE operation_id='${operationId}'),'outboxExists',EXISTS(SELECT 1 FROM academic_outbox WHERE event_type='semester.archive.participant.command' AND payload->'payload'->>'operation_id'='${operationId}' AND payload->'payload'->>'command'='PREPARE_DELETE'))`);
}
async function unpause(name, cause) {
  const entry = paused.get(name); if (!entry) return;
  if (entry.unpausing) return entry.unpausing;
  entry.unpausing = (async () => {
    const state = await owned(name, false, 500);
    if (state.Paused) await docker(['unpause', containerIds[name]], `unpause own ${name}`, 800);
    const after = await owned(name, false, 500);
    need(!after.Paused, `Own ${name} remains paused`);
    clearTimeout(entry.watchdog);
    const elapsedMs=Date.now()-entry.started;
    if(elapsedMs>=10000) outageExpired=true;
    evidence.pauseTimeline.push({ name, event: 'unpauseVerified', cause, at: new Date().toISOString(), elapsedMs, withinBound:elapsedMs<10000 });
    paused.delete(name); save();
  })();
  try { await entry.unpausing; } catch (error) { entry.unpausing = null; throw error; }
}
async function cleanupPauses(cause) {
  const failures = [];
  for (const name of [...paused.keys()]) {
    try { await unpause(name, cause); } catch (error) { failures.push({ name, failure: error.message }); }
  }
  if (failures.length) { evidence.unpauseFailures = failures; evidence.remainingOwnPauseIntents=[...paused.keys()].map(name=>({name,containerId:containerIds[name]})); save(); }
  return failures;
}
async function pause(name) {
  const state = await owned(name); need(!state.Paused, `Own ${name} was already paused`);
  const entry = { started: Date.now() }; paused.set(name, entry);
  evidence.pauseTimeline.push({ name, event: 'pauseIntent', containerId: containerIds[name], at: new Date().toISOString(), deadlineMs: 10000 }); save();
  // Starts before mutation. Resume commands get a 2 s reserve inside the 10 s window.
  entry.watchdog = setTimeout(() => { outageExpired = true; unpause(name, '8s-watchdog').catch(error => { evidence.watchdogFailure = error.message; save(); }); }, 8000);
  await docker(['pause', containerIds[name]], `pause own ${name}`, 1500);
  need((await owned(name)).Paused, `Own ${name} pause not observed`);
  evidence.pauseTimeline.push({ name, event: 'pausedVerified', at: new Date().toISOString() }); save();
}
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => {
  evidence.status = 'INTERRUPTED'; evidence.failure = signal; save();
  agent.destroy();
  cleanupPauses(signal).finally(() => { agent.destroy(); save(); process.exit(1); });
});

// Same deterministic white RGBA PNG pattern as the accepted attachment probe; no large padded fixture.
function crc32(bytes) { let crc = 0xffffffff; for (const byte of bytes) { crc ^= byte; for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0); } return (crc ^ 0xffffffff) >>> 0; }
function chunk(type, bytes) { const length = Buffer.alloc(4); length.writeUInt32BE(bytes.length); const name = Buffer.from(type); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([name, bytes]))); return Buffer.concat([length, name, bytes, crc]); }
const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(1, 0); ihdr.writeUInt32BE(1, 4); ihdr[8] = 8; ihdr[9] = 6;
const png = Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(Buffer.from([0,255,255,255,255]))), chunk('IEND', Buffer.alloc(0))]);
async function submitFile(student, lessonId, name) {
  const boundary = `rct-${randomUUID()}`;
  const multipart = Buffer.concat([Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="request"\r\nContent-Type: application/json\r\n\r\n${JSON.stringify({lessonIds:[String(lessonId)],reason:'OTHER',comment:'Owned synthetic semester recovery fixture'})}\r\n--${boundary}\r\nContent-Disposition: form-data; name="files"; filename="synthetic-white.png"\r\nContent-Type: image/png\r\n\r\n`), png, Buffer.from(`\r\n--${boundary}--\r\n`)]);
  const detail = (await api(student, '/api/v1/student/requests/excuse', `${name}.submit`, 'POST', multipart, 200, { 'Content-Type': `multipart/form-data; boundary=${boundary}`, 'Idempotency-Key': randomUUID() })).data;
  need(/^[a-f0-9]{24}$/.test(detail?.summary?.id??'') && detail.attachments?.length === 1 && /^[a-f0-9]{24}$/.test(detail.attachments[0].id??''), `${name} exact request/file IDs missing`);
  const result = { requestId: detail.summary.id, attachmentId: detail.attachments[0].id, metadata: canonical(detail), sha256: hash(png), sizeBytes: png.length };
  // Retention compares the same persisted GET projection before and after deletion.
  if(name==='A.foreign')result.metadata=canonical((await api(student,`/api/v1/student/requests/${result.requestId}`,'A.foreign.detail.before')).data);
  const bytes = (await api(student, `/api/v1/student/requests/${result.requestId}/attachments/${result.attachmentId}`, `${name}.download.before`)).response.bytes;
  check(hash(bytes) === result.sha256 && bytes.length === result.sizeBytes && detail.attachments[0].sha256 === result.sha256, `${name} actual stored file bytes`);
  return result;
}

function assertOldSessionDenied(response) {
  let error; try { error = JSON.parse(response.bytes.toString('utf8')); } catch { /* fail closed below */ }
  const code=error?.code??error?.extras?.code;
  const codes={401:['INVALID_SESSION','SESSION_REVOKED'],403:['WRONG_ROLE','ROLE_NOT_GRANTED','ROLE_NOT_SELECTABLE'],409:['SESSION_STATE_STALE']};
  evidence.oldSessionDenial={status:response.status,code:typeof code==='string'?code:null};
  check(!response.truncated&&error?.status===response.status&&codes[response.status]?.includes(code),'B old selected session denied with source-defined typed error');
}

async function acceptFocusedRegrant(admin, headman) {
  const target=fixture.headmanStudentId, base=`/api/academic/users/${target}`;
  const before=await userCounts(target);
  const preview=(await api(admin,`${base}/archive-preview`,'B.setup.preview')).data;
  await api(admin,`${base}/archive`,'B.setup.archive','POST',{operationId:randomUUID(),previewDigest:preview.previewDigest,password:'password'},204);
  await api(admin,`${base}/restore`,'B.setup.restore','POST',{operationId:randomUUID()},204);
  const restored=await userCounts(target);
  need(restored.openHistoryCount===0&&restored.selectedSessions===0&&restored.activeHelpers===0&&restored.grants.every(grant=>grant.status==='archived')&&same(before.grants.map(grant=>grant.id),restored.grants.map(grant=>grant.id))&&restored.historyCount===before.historyCount,'Focused regrant fixture must be restored with retained archived grants and closed membership');
  evidence.B={setup:'archive/restore are required fresh fixture preconditions; not repeated full B acceptance',before,restored};save();
  await api(admin,`${base}/roles/STUDENT`,'B.explicit.student.regrant','PUT',{status:'ACTIVE',groupId:fixture.groupId});
  const reassigned=await userCounts(target);
  check(same(before.grants.map(grant=>grant.id),reassigned.grants.map(grant=>grant.id))&&reassigned.openHistoryCount===1&&reassigned.openTodayCount===1&&reassigned.historyCount===before.historyCount+1&&reassigned.grants.filter(grant=>grant.status==='active').length===1&&reassigned.grants.filter(grant=>grant.status==='active').every(grant=>grant.role==='student'&&Number(grant.groupId)===fixture.groupId),'B explicit chosen group opens new TODAY membership without teacher/headman revival');
  const student=await login(origin,agent,fixture.headmanLogin,'STUDENT',FINAL_HEADMAN_PASSWORD);
  need(Number(student.session.userId)===target,'Explicit regrant login must match own restored account');
  evidence.B.reassigned=reassigned;evidence.B.status='PASS';save();
}

async function acceptGeo(admin, student){
  const campus=await pg("SELECT json_build_object('latitude',lat,'longitude',lng,'radius',radius_m) FROM campus_settings WHERE id=1");
  need(Number.isFinite(campus.latitude)&&Number.isFinite(campus.longitude)&&campus.radius>0,'Own seeded campus center required');
  const lessonFrom=value=>value?.lessons?.find(item=>String(item.schedule?.id)===String(fixture.lessonId));
  const before=lessonFrom((await api(student,'/api/v1/student/today','Geo.today.before')).data);
  need(before?.checkinEligibility?.allowed===true,'Independent geo fixture must be eligible before this criterion');
  const countsBefore=await retainedMarkCounts(fixture.studentId,fixture.lessonId);
  const command={geo:{kind:'COORDINATES',latitude:campus.latitude,longitude:campus.longitude}};
  if(scope==='Geo-valid'){
    const ack=(await api(student,`/api/v1/student/lessons/${fixture.lessonId}/checkin`,'Geo.valid.coordinates','POST',command,200,{'Idempotency-Key':randomUUID()})).data;
    check(ack?.outcome==='PRESENT'&&ack.attendance?.status==='PRESENT'&&ack.attendance?.source==='STUDENT_GEO','Geo in-campus coordinates create PRESENT/STUDENT_GEO');
    const after=lessonFrom((await api(student,'/api/v1/student/today','Geo.today.present')).data);
    check(after?.attendance?.status==='PRESENT'&&after.attendance?.source==='STUDENT_GEO','Geo fresh Today reflects PRESENT');
    const countsAfter=await retainedMarkCounts(fixture.studentId,fixture.lessonId);
    check(countsAfter.pair===1&&countsAfter.total===countsBefore.total+(countsBefore.pair===0?1:0),'Geo persisted mark matches independent baseline delta');
    evidence.Geo={status:'PASS',scope,lessonId:fixture.lessonId,countsBefore,countsAfter};
  }else{
    await api(admin,`/api/schedule/lessons/${fixture.lessonId}/geo-block`,'Geo.block.fixture','PATCH',{blocked:true});
    const blocked=lessonFrom((await api(student,'/api/v1/student/today','Geo.today.blocked')).data);
    check(blocked?.checkinEligibility?.allowed===false&&blocked.checkinEligibility?.reason==='GEO_BLOCKED','Geo blocked fresh Today reports GEO_BLOCKED');
    const denial=(await api(student,`/api/v1/student/lessons/${fixture.lessonId}/checkin`,'Geo.block.denied','POST',command,409,{'Idempotency-Key':randomUUID()})).data;
    check(denial?.code==='CHECKIN_NOT_ELIGIBLE','Geo blocked mutation denied with source-defined code');
    const countsAfter=await retainedMarkCounts(fixture.studentId,fixture.lessonId);
    check(same(countsBefore,countsAfter),'Geo denied mutation preserves independent retained marks');
    evidence.Geo={status:'PASS',scope,lessonId:fixture.lessonId,denialCode:denial.code,countsBefore,countsAfter};
  }
  save();
}

function shiftDate(day, offset) { const value = new Date(`${day}T00:00:00Z`); value.setUTCDate(value.getUTCDate()+offset); return value.toISOString().slice(0,10); }
const homeworkView=hw=>Object.fromEntries(['id','bindingId','publishedBy','requestKey','subjectId','groupId','semesterId','bindingMode','lessonDate','lessonNumber','revision','archived'].map(field=>[field,hw?.[field]??null]));
const bindingIdentity=binding=>Object.fromEntries(['bindingId','actorId','requestKey','payloadHash','groupId','subjectId','semesterId','originalMode','originalDate','originalNumber','originalOccurrenceId'].map(field=>[field,binding?.[field]??null]));
async function homeworkStore(homeworkId, bindingId) {
  homeworkId=id(homeworkId);bindingId=id(bindingId);
  const academic=await pg(`SELECT json_build_object('homeworkRows',(SELECT count(*) FROM homeworks WHERE id=${homeworkId}),'historyRows',(SELECT count(*) FROM homework_edit_history WHERE homework_id=${homeworkId}),'receiptRows',(SELECT count(*) FROM homework_edit_receipts WHERE homework_id=${homeworkId}),'completion',coalesce((SELECT json_agg(json_build_object('id',id,'studentId',student_id,'completedAt',completed_at) ORDER BY id) FROM homework_completions WHERE homework_id=${homeworkId}),'[]'::json))`);
  const schedule=await schedulePg(`SELECT json_build_object('bindingRows',(SELECT count(*) FROM lesson_homework_bindings WHERE binding_id=${bindingId}),'binding', (SELECT json_build_object('bindingId',binding_id,'homeworkId',homework_id,'actorId',actor_id,'requestKey',request_key,'payloadHash',encode(payload_hash,'hex'),'groupId',group_id,'subjectId',subject_id,'semesterId',semester_id,'originalMode',original_binding_mode,'originalDate',original_date,'originalNumber',original_lesson_number,'originalOccurrenceId',original_occurrence_id,'mode',binding_mode,'occurrenceId',occurrence_id,'lessonId',current_lesson_id,'state',state) FROM lesson_homework_bindings WHERE binding_id=${bindingId}))`);
  return {academic,schedule};
}
async function dateSlots(headman,name) {
  const days=[];
  for(let offset=1;offset<=3&&days.length<2;offset++){
    const day=shiftDate(fixture.lessonDate,offset);
    need(day<=fixture.semesterDateTo,'DATE fixture requires two future days in original semester');
    const rows=listFrom((await api(headman,`/api/schedule/groups/${fixture.groupId}/lessons?dateFrom=${day}&dateTo=${day}&size=200&status=PLANNED&status=ACTIVE&status=CLOSED&status=CANCELLED`,`${name}.absence.${offset}`)).data);
    if(rows.length===0)days.push(day);
  }
  need(days.length===2,'DATE requires two genuinely lesson-free fixture dates');
  return days;
}
async function createDate(headman,name,day) {
  const baseline=await pg(`SELECT json_build_object('homeworks',count(*)) FROM homeworks WHERE semester_id=${fixture.semesterId}`);
  const request={title:'Owned DATE homework',description:'Synthetic DATE acceptance content',link:null,subjectId:fixture.subjectId,groupId:fixture.groupId,semesterId:fixture.semesterId,lessonDate:day,lessonNumber:null,bindingMode:'DATE',requestKey:randomUUID()};
  const created=(await api(headman,'/api/academic/homeworks',`${name}.create`,'POST',request,201)).data;
  const after=await pg(`SELECT json_build_object('homeworks',count(*)) FROM homeworks WHERE semester_id=${fixture.semesterId}`);
  const store=await homeworkStore(created.id,created.bindingId);
  check(created.bindingMode==='DATE'&&created.lessonNumber===null&&created.lessonDate===day&&Number(created.publishedBy)===fixture.headmanStudentId&&created.revision>=1&&!created.archived&&after.homeworks===baseline.homeworks+1&&store.schedule.bindingRows===1&&store.schedule.binding?.mode==='DATE'&&store.schedule.binding.occurrenceId===null&&store.schedule.binding.lessonId===null&&Number(store.schedule.binding.homeworkId)===Number(created.id),`${name} DATE admitted without lesson or occurrence and counted from actual baseline`);
  return {request,created,baseline,after,store};
}
async function acceptDate(headman,student) {
  const [firstDay,secondDay]=await dateSlots(headman,'DATE');
  const publication=await createDate(headman,'DATE',firstDay),hw=publication.created;
  evidence.DATE={created:homeworkView(hw),baseline:publication.baseline,afterCreate:publication.after,store:publication.store};save();
  await api(headman,'/api/academic/assistants','DATE.helper.assign','POST',{studentId:fixture.studentId,groupId:fixture.groupId,permissions:['MANAGE_HOMEWORK']},201);
  // Assignment writes only assistant permissions; it neither selects a new role
  // nor replaces a token. Verify the same current STUDENT session and live grant.
  const helperSession=(await api(student,'/api/auth/session','DATE.helper.current.session')).data;
  const helperPermissions=(await api(student,'/api/academic/assistants/me/permissions','DATE.helper.current.permissions')).data;
  const manageHomework=Array.isArray(helperPermissions)&&helperPermissions.some(item=>item.code==='MANAGE_HOMEWORK');
  check(helperSession?.activeRole==='STUDENT'&&Number(helperSession.userId)===fixture.studentId&&manageHomework,'DATE same STUDENT token retains current role and has live MANAGE_HOMEWORK permission');
  evidence.DATE.helper={userId:fixture.studentId,activeRole:helperSession.activeRole,manageHomework};save();
  await api(student,`/api/v1/student/homework/${hw.id}/completion`,'DATE.complete','PUT',{completed:true});
  const feed=async name=>(await api(student,`/api/v1/student/homework?from=${firstDay}&to=${secondDay}`,name)).data?.items?.find(item=>String(item.id)===String(hw.id));
  const beforeFeed=await feed('DATE.feed.before.edit');
  check(beforeFeed?.bindingMode==='DATE'&&beforeFeed.lessonNumber===null&&beforeFeed.lessonDate===firstDay&&beforeFeed.completed===true&&!!beforeFeed.completedAt&&!beforeFeed.archived,'DATE public student projection has null lesson number and persisted completion');
  const beforeStore=await homeworkStore(hw.id,hw.bindingId);
  const historyRoute=`/api/academic/homeworks/${hw.id}/history?size=100&page=0&sort=revision,asc&sort=id,asc`;
  const historyBefore=listFrom((await api(student,historyRoute,'DATE.history.before')).data);
  const edit={title:'Owned DATE edited by helper',description:'Synthetic changed content',link:null,bindingMode:'DATE',lessonDate:secondDay,lessonNumber:null,requestKey:randomUUID(),expectedRevision:hw.revision};
  const edited=(await api(student,`/api/academic/homeworks/${hw.id}`,'DATE.nonpublisher.edit','PUT',edit)).data;
  check(Number(edited.id)===Number(hw.id)&&Number(edited.bindingId)===Number(hw.bindingId)&&Number(edited.publishedBy)===fixture.headmanStudentId&&edited.requestKey===hw.requestKey&&edited.revision===hw.revision+1&&edited.title===edit.title&&edited.description===edit.description&&edited.lessonDate===secondDay&&edited.bindingMode==='DATE'&&edited.lessonNumber===null,'DATE other authorized manager edits content/date preserving publication identity');
  const afterFeed=await feed('DATE.feed.after.edit'),afterStore=await homeworkStore(hw.id,hw.bindingId);
  check(afterFeed?.completed===true&&afterFeed.completedAt===beforeFeed.completedAt&&afterFeed.lessonDate===secondDay&&afterFeed.title===edit.title&&afterFeed.bindingMode==='DATE'&&afterFeed.lessonNumber===null&&same(beforeStore.academic.completion,afterStore.academic.completion),'DATE edit preserves persisted student completion and public timestamp');
  const historyAfter=listFrom((await api(student,historyRoute,'DATE.history.after')).data),entry=historyAfter.at(-1);
  check(historyAfter.length===historyBefore.length+1&&Number(entry?.actorId)===fixture.studentId&&entry.revision===edited.revision&&entry.before?.lessonDate===firstDay&&entry.after?.lessonDate===secondDay&&entry.before?.title===hw.title&&entry.after?.title===edit.title&&entry.before?.bindingMode==='DATE'&&entry.after?.bindingMode==='DATE','DATE same-group student reads chronological before/after history from nonpublisher edit');
  const replay=(await api(student,`/api/academic/homeworks/${hw.id}`,'DATE.edit.exact.replay','PUT',edit)).data;
  const originalReplay=(await api(headman,'/api/academic/homeworks','DATE.create.original.replay','POST',publication.request,201)).data;
  const historyReplay=listFrom((await api(student,historyRoute,'DATE.history.replay')).data),replayStore=await homeworkStore(hw.id,hw.bindingId);
  check(same(homeworkView(replay),homeworkView(edited))&&same(homeworkView(originalReplay),homeworkView(edited))&&same(historyReplay,historyAfter)&&same(replayStore,afterStore),'DATE exact edit/create replay returns current same identity with no duplicate history/receipt/completion');
  evidence.DATE={...evidence.DATE,edited:homeworkView(edited),history:{beforeCount:historyBefore.length,afterCount:historyAfter.length,actorId:entry.actorId,revision:entry.revision},completionBefore:beforeStore.academic.completion,completionAfter:afterStore.academic.completion,replayStore,status:'PASS'};save();
}
async function acceptA(admin, headman, student) {
  // Independent DATE inventory is a deletion precondition, not coupled to DATE flow PASS.
  const [inventoryDay]=await dateSlots(headman,'A.DATE');
  const dateInventory=await createDate(headman,'A.DATE',inventoryDay);
  const target = await submitFile(student,fixture.lessonId,'A.target');
  const semesters = listFrom((await api(admin,'/api/academic/semesters?size=200&page=0','A.semesters')).data);
  const lastDate = semesters.map(item=>item.dateTo).filter(Boolean).sort().at(-1); need(lastDate,'Semester date boundary missing');
  const dateFrom=shiftDate(lastDate,1), dateTo=shiftDate(dateFrom,35);
  let lessonDate=shiftDate(dateFrom,1);if(new Date(`${lessonDate}T00:00:00Z`).getUTCDay()===0)lessonDate=shiftDate(lessonDate,1);
  const foreignSemester=(await api(admin,'/api/academic/semesters','A.foreign.semester','POST',{name:`Owned retained recovery ${Date.now()}`,dateFrom,dateTo},201)).data;
  await api(admin,`/api/academic/semesters/${foreignSemester.id}/activate`,'A.foreign.activate','PATCH');
  // activateSemester emits archived(previousID) transactionally; publisher tick is
  // asynchronous (5 s). Observe the public budget's active ID before dependent writes.
  const refreshDeadline=Date.now()+20000, refreshObservations=[];
  let activeOptions;
  do{
    activeOptions=(await api(student,'/api/v1/student/requests/options','A.activation.refresh.options','GET',undefined,200,{},5000)).data;
    refreshObservations.push({semesterId:activeOptions?.budget?.semesterId,at:new Date().toISOString()});
    if(String(activeOptions?.budget?.semesterId)===String(foreignSemester.id))break;
    await delay(500);
  }while(Date.now()<refreshDeadline);
  evidence.activeSemesterRefresh={expectedSemesterId:String(foreignSemester.id),observations:refreshObservations};save();
  need(String(activeOptions?.budget?.semesterId)===String(foreignSemester.id),'A activation async refresh did not converge within bounded window');
  const subject=(await api(headman,'/api/academic/subjects','A.foreign.subject','POST',{name:`Owned foreign recovery ${Date.now()}`,type:'LECTURE',lessonTypes:['LECTURE'],teacherIds:null,initialAssignments:[{teacherId:2,semesterId:foreignSemester.id,lessonType:'LECTURE',validFrom:dateFrom,validUntilExclusive:null}]},201)).data;
  const assignmentId=id(subject.assignments?.[0]?.id??subject.createdAssignmentIds?.[0]);
  await api(headman,'/api/schedule/items','A.foreign.schedule','POST',{assignmentId,groupId:fixture.groupId,subjectId:subject.id,semesterId:foreignSemester.id,dayOfWeek:new Date(`${lessonDate}T00:00:00Z`).getUTCDay()||7,lessonNumber:1,startTime:'10:00:00',endTime:'11:30:00',weekType:'ALL',room:'RECOVERY'},201,{'Idempotency-Key':randomUUID()});
  const lessons=listFrom((await api(headman,`/api/schedule/groups/${fixture.groupId}/lessons?dateFrom=${lessonDate}&dateTo=${lessonDate}&size=100&status=PLANNED`,'A.foreign.lessons')).data);
  const lesson=lessons.find(item=>Number(item.subjectId)===Number(subject.id)); need(lesson,'Foreign planned lesson missing');
  const foreign=await submitFile(student,id(lesson.id),'A.foreign');
  const preview=(await api(admin,`/api/academic/semesters/${fixture.semesterId}/delete-preview`,'A.preview')).data;
  const actualInventory=await pg(`SELECT json_build_object('homeworks',count(*),'dateHomeworks',count(*) FILTER (WHERE binding_mode='DATE')) FROM homeworks WHERE semester_id=${fixture.semesterId}`);
  check(preview.counts?.studentRequests>=1&&preview.counts.homeworks===actualInventory.homeworks&&actualInventory.dateHomeworks>=1,'A preview includes target request and actual DATE-inclusive homework inventory');
  const userBefore=(await api(admin,`/api/academic/users/${fixture.studentId}`,'A.student.before')).data;
  const headmanBefore=(await api(admin,`/api/academic/users/${fixture.headmanStudentId}`,'A.headman.before')).data;
  const groupBefore=(await api(admin,`/api/academic/groups/${fixture.groupId}`,'A.group.before')).data;
  const operationId=randomUUID(), body={previewDigest:preview.previewDigest,password:'password'};
  evidence.A={operationId,semesterId:fixture.semesterId,foreignSemesterId:foreignSemester.id,previewCounts:preview.counts,target:{requestId:target.requestId,attachmentId:target.attachmentId,sizeBytes:target.sizeBytes,sha256:target.sha256},foreign:{requestId:foreign.requestId,attachmentId:foreign.attachmentId,sizeBytes:foreign.sizeBytes,sha256:foreign.sha256}}; save();
  evidence.A.foreign.metadataBefore=retainedMetadataView(foreign.metadata);save();
  evidence.A.dateInventory={homework:homeworkView(dateInventory.created),baseline:dateInventory.baseline,afterCreate:dateInventory.after,actualInventory};save();
  evidence.A.originalPreview=sanitizedPreview(preview);save();
  await owned('bff'); for(const name of ['rabbit','attendance']) need(!(await owned(name)).Paused,`Before outage ${name} already paused`);
  await docker(['exec',containerIds.rabbit,'rabbitmq-diagnostics','-q','ping'],'healthy own Rabbit availability',10000);
  await api(student,`/api/v1/student/requests/${foreign.requestId}`,'A.ready.Attendance');
  let confirm, confirmOutcome;
  try {
    await pause('rabbit');
    confirm=api(admin,`/api/academic/semesters/${fixture.semesterId}`,'A.delete.async','DELETE',body,202,{'Idempotency-Key':operationId},10000).then(result=>(confirmOutcome={result}),error=>(confirmOutcome={error}));
    const deadline=Date.now()+5000; let observed;
    do { if(confirmOutcome?.error)throw confirmOutcome.error;observed=await operationState(operationId);if(confirmOutcome?.error)throw confirmOutcome.error;if(observed.exists&&observed.outboxExists) break; await delay(100); } while(Date.now()<deadline&&!outageExpired);
    need(!outageExpired && observed?.exists && observed.outboxExists && observed.phase==='PREPARING' && observed.attendanceStatus==='PENDING','NOT_COVERED: durable PREPARING + command proof before participant pause missing');
    need(await receiptCount(operationId)===0,'NOT_COVERED: participant already processed before pause');
    evidence.A.durableBeforePause={...observed,receiptCount:0,at:new Date().toISOString()}; save();
    await pause('attendance');
    await unpause('rabbit','participant-paused');
    const interrupted=await operationState(operationId), count=await receiptCount(operationId);
    need(!outageExpired&&interrupted.phase==='PREPARING'&&interrupted.attendanceStatus==='PENDING'&&count===0,'NOT_COVERED: unprocessed paused participant not observed');
    evidence.A.interrupted={...interrupted,receiptCount:count,at:new Date().toISOString()};save();
  } finally {
    const failures=await cleanupPauses('outage-finally'); need(failures.length===0,'Owned unpause failed; root checkpoint required');
  }
  need(!outageExpired,'NOT_COVERED: own pause window reached watchdog');
  const confirmation=await confirm; if(confirmation.error) throw confirmation.error;
  check(confirmation.result.data?.operationId===operationId,'A HTTP pending response exact durable operation');
  await api(student,`/api/v1/student/requests/${foreign.requestId}`,'A.resumed.Attendance');
  const until=Date.now()+75000;let completed;
  do { completed=(await api(admin,`/api/academic/semester-deletions/${operationId}`,'A.same.operation.status')).data; if(completed.phase==='COMPLETED') break; need(!['CANCELLED','RELEASING'].includes(completed.phase),'A operation released/cancelled rather than recovered');await delay(500); }while(Date.now()<until);
  check(completed?.phase==='COMPLETED'&&completed.operationId===operationId&&same(completed.counts,preview.counts),'A same operation recovered with exact preview counts');
  evidence.A.completed={operationId,phase:completed.phase,counts:completed.counts};save();
  await api(admin,`/api/academic/semesters/${fixture.semesterId}`,'A.target.semester.deleted','GET',undefined,404);
  await api(student,`/api/v1/student/requests/${target.requestId}`,'A.target.request.deleted','GET',undefined,404);
  await api(student,`/api/v1/student/requests/${target.requestId}/attachments/${target.attachmentId}`,'A.target.file.deleted','GET',undefined,404);
  await api(headman,`/api/academic/homeworks/${dateInventory.created.id}`,'A.DATE.homework.deleted','GET',undefined,404);
  const deletedStore=await homeworkStore(dateInventory.created.id,dateInventory.created.bindingId);
  evidence.A.dateInventory.deletedStore=deletedStore;save();
  const retainedBinding=deletedStore.schedule.binding;
  check(deletedStore.academic.homeworkRows===0&&deletedStore.academic.historyRows===0&&deletedStore.academic.receiptRows===0&&deletedStore.academic.completion.length===0&&deletedStore.schedule.bindingRows===1&&retainedBinding?.state==='ARCHIVED'&&retainedBinding.homeworkId===null&&retainedBinding.mode==='DATE'&&retainedBinding.occurrenceId===null&&retainedBinding.lessonId===null&&same(bindingIdentity(retainedBinding),bindingIdentity(dateInventory.store.schedule.binding)),'A DATE content removed; immutable terminal Schedule publication retained with cleared homework reference');
  const retained=(await api(student,`/api/v1/student/requests/${foreign.requestId}`,'A.foreign.detail.retained')).data;
  const bytes=(await api(student,`/api/v1/student/requests/${foreign.requestId}/attachments/${foreign.attachmentId}`,'A.foreign.file.retained')).response.bytes;
  const metadataMatches=same(retained,foreign.metadata),afterSha256=hash(bytes),afterSizeBytes=bytes.length;
  const shaMatches=afterSha256===foreign.sha256,sizeMatches=afterSizeBytes===foreign.sizeBytes;
  const metadataAfter=retainedMetadataView(retained),differences=metadataDiff(evidence.A.foreign.metadataBefore,metadataAfter);
  evidence.A.foreignRetention={metadataMatches,shaMatches,sizeMatches,afterSha256,afterSizeBytes,metadataAfter,differences,outsideAllowlistDifference:!metadataMatches&&differences.length===0};save();
  check(metadataMatches&&shaMatches&&sizeMatches,'A foreign request metadata and bytes exactly retained');
  check(same((await api(admin,`/api/academic/users/${fixture.studentId}`,'A.student.after')).data,userBefore)&&same((await api(admin,`/api/academic/users/${fixture.headmanStudentId}`,'A.headman.after')).data,headmanBefore)&&same((await api(admin,`/api/academic/groups/${fixture.groupId}`,'A.group.after')).data,groupBefore),'A retained users/group unchanged');
  const replay=(await api(admin,`/api/academic/semesters/${fixture.semesterId}`,'A.same.operation.replay','DELETE',body,200,{'Idempotency-Key':operationId})).data;
  check(replay.operationId===operationId&&same(replay.counts,completed.counts)&&replay.phase==='COMPLETED','A completed receipt replay stable');
  evidence.A.completed={operationId,phase:completed.phase,counts:completed.counts};save();
}

async function main() {
  for(const name of Object.keys(containerIds)) need(!(await owned(name)).Paused,`Initial owned ${name} must not be paused`);
  const admin=await login(origin,agent,'admin','ADMIN');
  const headman=await login(origin,agent,fixture.headmanLogin,'HEADMAN',FINAL_HEADMAN_PASSWORD);
  need(Number(headman.session.userId)===fixture.headmanStudentId,'Fixture headman identity must match live own account');
  if(scope==='B-regrant') await acceptFocusedRegrant(admin,headman);
  else {
    const student=await login(origin,agent,fixture.studentLogin,'STUDENT',FINAL_HEADMAN_PASSWORD);
    need(Number(student.session.userId)===fixture.studentId,'Fixture normal student identity must match live own account');
    if(scope==='A-only')await acceptA(admin,headman,student);
    else if(scope==='DATE-homework')await acceptDate(headman,student);
    else await acceptGeo(admin,student);
  }
  evidence.status='PASS';
}
try { await main(); }
catch(error) { evidence.status=error.message.startsWith('DELETE_NOT_ACCEPTED:')?'DELETE_NOT_ACCEPTED':error.message.startsWith('NOT_COVERED:')?'NOT_COVERED':'FAIL';evidence.failure=error.message;process.exitCode=1; }
finally {
  const failures=await cleanupPauses('probe-finally');if(failures.length){evidence.status='FAIL';process.exitCode=1;}
  evidence.finishedAt=new Date().toISOString();agent.destroy();save();
  console.log(JSON.stringify({status:evidence.status,evidence:output,steps:evidence.steps.length,checks:evidence.checks.length,failure:evidence.failure}));
}
