const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const ts = require('/workspace/rutcampustrack/frontends/node_modules/typescript/lib/typescript.js');
const source = '/workspace/rct-worktrees/client-ci-first3/tests/e2e/fixtures/first3-observer.ts';
const parsed = ts.transpileModule(fs.readFileSync(source, 'utf8'), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
  reportDiagnostics: true,
});
const output = process.env.RCT_CLASSIFIER_CONTROL_OUTPUT;
const root = process.env.RCT_FOCUS3_RUN;
if (!output || !root || path.dirname(output) !== path.join(root, 'supervision')) process.exit(2);
const compiled = new Module(source, module);
compiled.filename = source;
compiled.paths = Module._nodeModulePaths(path.dirname(source));
compiled._compile(parsed.outputText, source);
const { classifyRosterProblem, validateFacts, FocusObserver, caseTitles } = compiled.exports;
const publicMarker = 'PUBLIC_SYNTHETIC_CLASSIFIER_ONLY_8F21';
const bytes = (v) => Buffer.from(JSON.stringify(v), 'utf8');
const attendance = (detail) => ({ status: 503, type: 'https://api.rutcampustrack.ru/problems/service-unavailable', detail });
const results = [];
function need(v) { if (!v) throw new Error('CONTROL_REJECTED'); }
function noLeak(v) { validateFacts(v); need(!JSON.stringify(v).includes(publicMarker)); }
async function test(code, action) {
  let passed = false;
  try { await action(); passed = true; } catch {}
  results.push({ code, passed });
}
function equal(v, expected) { for (const [k, value] of Object.entries(expected)) need(v[k] === value); noLeak(v); }
function observer() {
  const user = { id: 1, displayName: 'PUBLIC CONTROL', login: 'PUBLIC CONTROL', password: publicMarker };
  const dataset = { student: user, headman: user, assistant: user, teacher: { ...user, id: 2 }, lesson: { physicalLessonId: 1 }, group: { id: 1 } };
  const page = { on() {}, off() {} };
  return new FocusObserver(page, dataset, { title: caseTitles.HEADMAN });
}
(async () => {
  await test('GATEWAY_STATIC', () => equal(classifyRosterProblem(503, bytes({ status: 503, code: 'DEPENDENCY_UNAVAILABLE', detail: 'Dependency unavailable' })), { failureBoundary: 'GATEWAY_ISSUER', dependency: 'UNKNOWN', operationHint: 'UNKNOWN' }));
  await test('SEMESTER_SHAPE', () => equal(classifyRosterProblem(503, bytes(attendance('Mismatched semester state'))), { dependency: 'ACADEMIC', operationHint: 'GET_SEMESTER_STATE_SHAPE' }));
  await test('ROSTER_SHAPE', () => equal(classifyRosterProblem(503, bytes(attendance('Academic returned a missing or mismatched historical roster echo'))), { dependency: 'ACADEMIC', operationHint: 'GET_GROUP_MEMBERS_SHAPE' }));
  await test('SCHEDULE_SHAPE', () => equal(classifyRosterProblem(503, bytes(attendance('Schedule returned no lesson snapshot'))), { dependency: 'SCHEDULE', operationHint: 'GET_LESSON_BY_ID_SHAPE' }));
  await test('ACADEMIC_RPC_OPERATION_UNKNOWN', () => equal(classifyRosterProblem(503, bytes(attendance(`Academic Service unavailable: Status{code=UNAUTHENTICATED, description=${publicMarker}, cause=null}`))), { dependency: 'ACADEMIC', reportedCode: 'UNAUTHENTICATED', operationHint: 'UNKNOWN' }));
  await test('SCHEDULE_RPC_OPERATION_UNKNOWN', () => equal(classifyRosterProblem(503, bytes(attendance('Schedule Service unavailable: Status{code=DEADLINE_EXCEEDED, description=null, cause=null}'))), { dependency: 'SCHEDULE', reportedCode: 'DEADLINE_EXCEEDED', operationHint: 'UNKNOWN' }));
  await test('UNKNOWN_SECRET_OBJECT', () => equal(classifyRosterProblem(503, bytes({ status: 503, detail: publicMarker, token: publicMarker, login: publicMarker })), { failureBoundary: 'UNKNOWN', dependency: 'UNKNOWN', operationHint: 'UNKNOWN' }));
  await test('NO_SUBSTRING_HINT', () => equal(classifyRosterProblem(503, bytes(attendance(`prefix Mismatched semester state ${publicMarker}`))), { operationHint: 'UNKNOWN' }));
  await test('BAD_STATUS_GRAMMAR', () => equal(classifyRosterProblem(503, bytes(attendance('Academic Service unavailable: Status{code=UNAUTHENTICATED,'))), { dependency: 'UNKNOWN', reportedCode: 'UNKNOWN', operationHint: 'UNKNOWN' }));
  await test('UNKNOWN_RPC_CODE', () => equal(classifyRosterProblem(503, bytes(attendance('Academic Service unavailable: Status{code=PRIVATE_NEW_CODE, description=null, cause=null}'))), { dependency: 'UNKNOWN', reportedCode: 'UNKNOWN' }));
  await test('FOREIGN_PROBLEM_TYPE', () => equal(classifyRosterProblem(503, bytes({ ...attendance('Mismatched semester state'), type: 'https://foreign.invalid/problems/service-unavailable' })), { failureBoundary: 'UNKNOWN', operationHint: 'UNKNOWN' }));
  await test('ONLY_HTTP503', () => equal(classifyRosterProblem(200, bytes(attendance('Mismatched semester state'))), { failureBoundary: 'UNKNOWN', problemBodyState: 'NOT_503' }));
  await test('BODY_STATUS_REQUIRED', () => equal(classifyRosterProblem(503, bytes({ type: attendance('').type, detail: 'Mismatched semester state' })), { failureBoundary: 'UNKNOWN' }));
  await test('MALFORMED_JSON', () => equal(classifyRosterProblem(503, Buffer.from('{')), { failureBoundary: 'UNKNOWN', problemBodyState: 'MALFORMED' }));
  await test('MALFORMED_UTF8', () => equal(classifyRosterProblem(503, Buffer.from([0xff])), { failureBoundary: 'UNKNOWN', problemBodyState: 'MALFORMED' }));
  await test('BYTE_BOUNDARY_AT8192', () => { const body = bytes(attendance('Mismatched semester state')); const padded = Buffer.concat([body, Buffer.alloc(8192 - body.length, 32)]); equal(classifyRosterProblem(503, padded), { operationHint: 'GET_SEMESTER_STATE_SHAPE' }); });
  await test('BYTE_BOUNDARY_OVER8192', () => equal(classifyRosterProblem(503, Buffer.alloc(8193, 32)), { failureBoundary: 'UNKNOWN', problemBodyState: 'OVERSIZE' }));
  await test('UTF8_BYTES_NOT_CHARACTERS', () => equal(classifyRosterProblem(503, Buffer.from(JSON.stringify({ status: 503, detail: 'Я'.repeat(5000) }))), { problemBodyState: 'OVERSIZE' }));
  await test('DECLARED_LENGTH_REJECTS_BEFORE_BODY', async () => { const o = observer(); let reads = 0; const facts = await o.rosterUnavailableFacts({ headers: () => ({ 'content-length': '8193' }), body: async () => { reads++; return bytes(attendance('Mismatched semester state')); } }); need(reads === 0); equal(facts, { problemBodyState: 'OVERSIZE' }); o.stop(false); await o.closeDiagnosticContext(); });
  await test('MALFORMED_LENGTH_REJECTS_BEFORE_BODY', async () => { const o = observer(); let reads = 0; const facts = await o.rosterUnavailableFacts({ headers: () => ({ 'content-length': publicMarker }), body: async () => { reads++; return Buffer.alloc(0); } }); need(reads === 0); equal(facts, { problemBodyState: 'INVALID_LENGTH' }); o.stop(false); await o.closeDiagnosticContext(); });
  await test('ABSENT_LENGTH_VALIDATED_AFTER_BODY', async () => { const o = observer(); const facts = await o.rosterUnavailableFacts({ headers: () => ({}), body: async () => bytes(attendance('Mismatched semester state')) }); equal(facts, { operationHint: 'GET_SEMESTER_STATE_SHAPE' }); o.stop(false); await o.closeDiagnosticContext(); });
  await test('LATE_BODY_AFTER_STOP_NO_EVENT_OR_TOKEN', async () => {
    const o = observer(); o.start(); const file = path.join(root, 'supervision', 'observations-headman.safe.json'); const before = fs.readFileSync(file);
    let resolve; const deferred = new Promise(r => { resolve = r; });
    const operation = o.captureResponse({ url: () => 'https://localhost:8443/api/attendance/reports/lesson/1', status: () => 503, headers: () => ({}), body: () => deferred, request: () => ({ method: () => 'GET', headers: () => ({ authorization: `Bearer ${publicMarker}` }) }) });
    o.stop(false); o.ended = true; resolve(bytes(attendance('Mismatched semester state'))); await Promise.allSettled([operation]); await o.closeDiagnosticContext();
    need(o.events.length === 1 && o.token === undefined && fs.readFileSync(file).equals(before));
  });
  await test('NON_TARGET_ROSTER_BODY_NOT_READ', async () => {
    const o = observer(); o.start(); let reads = 0;
    await o.captureResponse({ url: () => 'https://localhost:8443/api/attendance/reports/lesson/2', status: () => 503, headers: () => ({}), body: async () => { reads++; return bytes(attendance(publicMarker)); }, request: () => ({ method: () => 'GET', headers: () => ({}) }) });
    need(reads === 0 && o.events.at(-1).facts.failureBoundary === undefined); o.stop(false); await o.closeDiagnosticContext();
  });
  await test('FACT_ALLOWLIST_REJECTS_RAW_DETAIL', () => { let rejected = false; try { validateFacts({ detail: publicMarker }); } catch { rejected = true; } need(rejected); });
  const pass = results.every(r => r.passed) && (parsed.diagnostics ?? []).length === 0;
  fs.writeFileSync(output, JSON.stringify({ schema: 'RCT_CLASSIFIER_PUBLIC_SOURCE_ADAPTER_V1', pass, count: results.length, failed: results.filter(r => !r.passed).length, controls: results, parserDiagnosticCount: (parsed.diagnostics ?? []).length, qualification: 'Actual transpiled observer functions; public response/page adapters. No browser/API/real transport; not full worker ordering proof.' }, null, 2) + '\n', { mode: 0o600, flag: 'wx' });
  process.stdout.write(JSON.stringify({ phase: 'CLASSIFIER_SOURCE_ADAPTER', pass, count: results.length }) + '\n');
  if (!pass) process.exitCode = 1;
})().catch(() => { process.stdout.write('{"phase":"CLASSIFIER_CONTROL_INFRA","pass":false}\n'); process.exitCode = 2; });
