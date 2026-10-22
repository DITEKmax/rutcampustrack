const fs = require('node:fs');
const { request } = require('/workspace/rutcampustrack/tests/e2e/node_modules/playwright');
const ts = require('/workspace/rutcampustrack/frontends/node_modules/typescript/lib/typescript.js');
const ROOT = '/workspace/rutcampustrack/';
const usersText = fs.readFileSync(ROOT + 'tests/e2e/fixtures/users.ts', 'utf8');
const moduleBox = { exports: {} };
const emitted = ts.transpileModule(usersText, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
new Function('module', 'exports', emitted)(moduleBox, moduleBox.exports);
const adminUser = moduleBox.exports.TEST_USERS.admin;
const nativeBase = '/workspace/scratch/rct-validation/client_real_acceptance/focused3-next96-date-r2/runs/attempt-01/supervision/';
const nativeLaunch = JSON.parse(fs.readFileSync(nativeBase + 'launch.safe.json', 'utf8'));
const start = Date.parse(nativeLaunch.issued_at);
const finish = fs.statSync(nativeBase + 'terminal.safe.json').mtimeMs;
if (!Number.isFinite(start) || !(finish > start)) throw new Error('CLOSED_INTERVAL_GATE');
let stage = 'START', context, adminToken, actorToken;
const facts = { stage: 'START', status: 'OTHER', boundary: 'OTHER', dependency: 'OTHER', rpc: 'OTHER', actorRecovery: 'NOT_RUN', singleRosterGet: false, noReseedOrDomainWrites: true, clientAcceptance: false };
function need(ok) { if (!ok) throw new Error('CLOSED_GATE'); }
function unwrap(v) { return v && typeof v === 'object' ? v.content ?? v : {}; }
function items(v, key) { return Array.isArray(v) ? v : Array.isArray(v?.content) ? v.content : v?._embedded?.[key] ?? []; }
async function call(method, path, token, data) {
  const response = await context.fetch(path, { method, headers: token ? { Authorization: `Bearer ${token}` } : {}, data, timeout: 3000, maxRedirects: 0, maxRetries: 0 });
  const status = response.status();
  const bytes = await response.body(); need(bytes.length <= 1048576);
  let body = {}; if (bytes.length) { try { body = JSON.parse(bytes.toString('utf8')); } catch {} }
  return { status, body };
}
async function admit(user, role) {
  const login = await call('POST', '/api/auth/login', undefined, { login: user.login, password: user.password }); need(login.status === 200 && typeof login.body.accessToken === 'string');
  let token = login.body.accessToken;
  const session = await call('GET', '/api/auth/session', token); need(session.status === 200);
  let value = session.body;
  if (value.activeRole !== role) {
    const selected = await call('PUT', '/api/auth/session/active-role', token, { role, expectedSessionVersion: value.sessionVersion });
    need(selected.status === 200 && typeof selected.body.accessToken === 'string'); token = selected.body.accessToken; value = selected.body.session;
  }
  need(value.activeRole === role); return { token, session: value };
}
(async () => {
  try {
    stage = 'ADMIN_ADMISSION'; context = await request.newContext({ baseURL: 'https://localhost:8443', ignoreHTTPSErrors: true, timeout: 3000, maxRedirects: 0 });
    adminToken = (await admit(adminUser, 'ADMIN')).token;
    stage = 'EXISTING_ACTOR_LOOKUP';
    const read = await call('GET', '/api/academic/users?role=STUDENT&search=%D0%9F%D1%80%D0%BE%D0%B2%D0%B5%D1%80%D0%BA%D0%B0&page=0&size=100&sort=createdAt,desc', adminToken); need(read.status === 200);
    need(!read.body.page || read.body.page.totalPages <= 1);
    const candidates = items(read.body, 'userResponseList').map(unwrap).filter(v => v.headman === true && v.firstName === 'Синтетика' && typeof v.lastName === 'string' && /^Проверка[А-Я]{8}$/.test(v.lastName) && Date.parse(v.createdAt) >= start && Date.parse(v.createdAt) < finish).sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt));
    need(candidates.length === 2 && Date.parse(candidates[0].createdAt) < Date.parse(candidates[1].createdAt));
    const actor = candidates[0]; need(Number.isSafeInteger(actor.id) && Number.isSafeInteger(actor.groupId));
    stage = 'EXISTING_ACTOR_CREDENTIAL_VIEW';
    const credentialRead = await call('GET', `/api/academic/users/${actor.id}`, adminToken); need(credentialRead.status === 200);
    const credential = unwrap(credentialRead.body); need(String(credential.id) === String(actor.id) && String(credential.groupId) === String(actor.groupId) && credential.headman === true && typeof credential.login === 'string' && typeof credential.initialPassword === 'string');
    facts.actorRecovery = 'SOURCE_QUALIFIED_FIRST_WORKER_CHRONOLOGY';
    stage = 'EXISTING_HEADMAN_ADMISSION'; const admitted = await admit({ login: credential.login, password: credential.initialPassword }, 'HEADMAN'); actorToken = admitted.token; need(String(admitted.session.userId) === String(actor.id));
    stage = 'EXISTING_LESSON_LOOKUP';
    const lessons = await call('GET', `/api/schedule/groups/${actor.groupId}/lessons?dateFrom=2026-10-12&dateTo=2026-10-12&page=0&size=100`, actorToken); need(lessons.status === 200);
    const selected = items(lessons.body, 'lessonResponseList').map(unwrap); need(selected.length === 1); const lesson = selected[0]; need(Number.isSafeInteger(lesson.id) && String(lesson.groupId) === String(actor.groupId) && lesson.date === '2026-10-12');
    stage = 'ONE_ROSTER_GET'; facts.singleRosterGet = true;
    const witness = await call('GET', `/api/attendance/reports/lesson/${lesson.id}`, actorToken);
    facts.status = [200, 401, 403, 404, 409, 422, 500, 502, 503, 504].includes(witness.status) ? String(witness.status) : 'OTHER';
    facts.boundary = witness.body.code === 'DEPENDENCY_UNAVAILABLE' ? 'GATEWAY_ISSUER' : witness.body.type === 'https://api.rutcampustrack.ru/problems/service-unavailable' ? 'ATTENDANCE_DEPENDENCY' : 'OTHER';
    const detail = typeof witness.body.detail === 'string' ? witness.body.detail : '';
    const dep = /^(Academic|Schedule) Service unavailable: Status\{code=(UNAUTHENTICATED|PERMISSION_DENIED|FAILED_PRECONDITION|UNAVAILABLE|DEADLINE_EXCEEDED|INTERNAL|UNKNOWN|INVALID_ARGUMENT),/.exec(detail);
    if (dep) { facts.dependency = dep[1] === 'Academic' ? 'ACADEMIC' : 'SCHEDULE'; facts.rpc = dep[2]; }
    stage = 'CLASSIFIED';
  } catch { facts.failedClosed = true; }
  finally {
    if (context) {
      for (const token of [actorToken, adminToken]) if (token) { try { await call('POST', '/api/auth/logout', token); } catch {} }
      actorToken = adminToken = undefined; await context.dispose().catch(() => {});
    }
    facts.stage = stage;
    const output = process.argv[2]; need(typeof output === 'string' && output.startsWith('/tmp/rct-roster-witness-') && output.endsWith('/witness.safe.json'));
    fs.writeFileSync(output, JSON.stringify(facts, null, 2) + '\n', { flag: 'wx', mode: 0o600 });
    process.stdout.write(JSON.stringify({ stage, classified: stage === 'CLASSIFIED', failedClosed: facts.failedClosed === true }) + '\n');
  }
})().catch(() => process.exitCode = 1);
