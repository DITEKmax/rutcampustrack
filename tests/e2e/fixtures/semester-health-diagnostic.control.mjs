import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createHealthDiagnostic, normalizeDockerHealthTimestamp, observeHealth, writeHealthDiagnostic, HEALTH_REASONS, GATEWAY_ADMISSION_CHECKS, GATEWAY_ADMISSION_PARSERS } from './semester-health-diagnostic.ts';

const directory = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(directory, 'semester-lifecycle.spec.ts'), 'utf8');
const begin = source.indexOf('  const recoveryHealthRead =');
const end = source.indexOf('  const restart =', begin);
assert.ok(begin > 0 && end > begin);
const temporary = mkdtempSync(join(tmpdir(), 'rct-semester-health-control-'));
try {
  const modulePath = join(temporary, 'original.mts');
  const helper = pathToFileURL(join(directory, 'semester-health-diagnostic.ts')).href;
  writeFileSync(modulePath, `import { normalizeDockerHealthTimestamp } from '${helper}';\nimport type { RecoveryHealthRead } from '${helper}';\nexport ${source.slice(begin, end)}`);
  const { recoveryHealthRead: original } = await import(pathToFileURL(modulePath).href);
  const canary = 'PRIVATE_HEALTH_BODY_MUST_NEVER_ESCAPE';
  const start = '2026-03-02T00:00:00.000Z', finish = '2026-03-02T00:00:00.001Z';
  const since = Date.parse(start), observed = Date.parse(finish) + 1000;
  const body = (state = 'READY') => ({ status: 'UP', components: { grpcChannel: { status: 'UP', details: { 'schedule-service': state } } } });
  const row = (changes = {}) => ({ Start: start, End: finish, ExitCode: 0, Output: JSON.stringify(body()), ...changes });
  const withBody = (value) => [row({ Output: JSON.stringify(value) })];
  const cases = [
    ['ROWS_TYPE', () => null], ['ROWS_EMPTY', () => []], ['ROWS_LIMIT', () => Array(6).fill(row())],
    ['ROW_NULL', () => [null]], ['ROW_TYPE', () => [canary]], ['ROW_ARRAY', () => [[]]],
    ['ROW_PROTOTYPE', () => [Object.assign(Object.create(null), row())]],
    ['ROW_KEYS', () => [{ ...row(), [canary]: canary }]], ['EXIT_TYPE', () => [row({ ExitCode: '0' })]],
    ['EXIT_NOT_ZERO', () => [row({ ExitCode: 1 })]], ['START_TYPE', () => [row({ Start: null })]],
    ['END_TYPE', () => [row({ End: null })]], ['OUTPUT_TYPE', () => [row({ Output: null })]],
    ['OUTPUT_SIZE', () => [row({ Output: canary.repeat(200) })]],
    ['SINCE_NONFINITE', () => [row()], NaN], ['OBSERVED_NONFINITE', () => [row()], since, NaN],
    ['START_NON_Z', () => [row({ Start: start.replace('Z', '+24:00') })]],
    ['START_FORMAT', () => [row({ Start: canary })]],
    ['END_NON_Z', () => [row({ End: finish.replace('Z', '+00:60') })]],
    ['END_FORMAT', () => [row({ End: canary })]],
    ['START_DATE', () => [row({ Start: '2026-99-02T00:00:00Z' })]],
    ['END_DATE', () => [row({ End: '2026-99-02T00:00:00Z' })]],
    ['START_AFTER_END', () => [row({ Start: '2026-03-02T00:00:00.002Z' })]],
    ['END_AFTER_OBSERVED', () => [row()], since, since],
    ['START_NONCANONICAL', () => [row({ Start: '2026-02-30T00:00:00.000Z' })]],
    ['END_NONCANONICAL', () => [row({ End: '2026-02-30T00:00:00.001Z' })]],
    ['SUBMILLI_ORDER', () => [row({ Start: '2026-03-02T00:00:00.000000002Z', End: '2026-03-02T00:00:00.000000001Z' })]],
    ['BODY_JSON', () => [row({ Output: canary })]], ['BODY_STATUS', () => withBody({ status: canary })],
    ['GRPC_STATUS', () => withBody({ status: 'UP', components: { grpcChannel: { status: canary } } })],
    ['DETAILS_NULL', () => withBody({ status: 'UP', components: { grpcChannel: { status: 'UP', details: null } } })],
    ['DETAILS_TYPE', () => withBody({ status: 'UP', components: { grpcChannel: { status: 'UP', details: canary } } })],
    ['DETAILS_ARRAY', () => withBody({ status: 'UP', components: { grpcChannel: { status: 'UP', details: [] } } })],
    ['SCHEDULE_MISSING', () => withBody({ status: 'UP', components: { grpcChannel: { status: 'UP', details: {} } } })],
    ['STATE_NOT_ALLOWED', () => withBody(body(canary))], ['STALE_SNAPSHOT', () => [row()], since + 1],
    ...['READY', 'IDLE', 'CONNECTING', 'TRANSIENT_FAILURE', 'SHUTDOWN'].map(state => ['OBSERVED_' + state, () => withBody(body(state))]),
    ['UNEXPECTED', () => { const value = row(); Object.defineProperty(value, 'Output', { enumerable: true, get() { throw Error(canary); } }); return [value]; }],
  ];
  const reasons = new Set();
  const diagnostic = createHealthDiagnostic();
  for (const [reason, make, sinceValue = since, observedValue = observed] of cases) {
    const input = make();
    const expected = original(input, sinceValue, observedValue);
    const actual = diagnostic.observe('ATTENDANCE', 'MAIN', input, sinceValue, observedValue, original);
    assert.deepEqual(actual, expected, reason);
    assert.equal(diagnostic.snapshot().MAIN.ATTENDANCE.last.reason, reason);
    reasons.add(reason);
  }
  const unchanged = { channelState: 'UNKNOWN', fresh: null };
  assert.equal(diagnostic.observe('ACADEMIC', 'MAIN', [row()], since, observed, () => unchanged), unchanged);
  assert.equal(diagnostic.snapshot().MAIN.ACADEMIC.last.reason, 'RESULT_MISMATCH');
  reasons.add('RESULT_MISMATCH');
  assert.deepEqual([...reasons].sort(), [...HEALTH_REASONS].sort());
  const equivalent = [
    ['2026-03-02T00:00:00Z', '2026-03-02T00:00:00Z'],
    ['2026-03-02T00:00:00+00:00', '2026-03-02T00:00:00Z'],
    ['2026-03-02T03:00:00+03:00', '2026-03-02T00:00:00Z'],
    ['2026-03-01T20:30:00-03:30', '2026-03-02T00:00:00Z'],
    ['2027-01-01T00:30:00+00:30', '2027-01-01T00:00:00Z'],
    ['2027-01-01T00:00:00+00:30', '2026-12-31T23:30:00Z'],
    ['2026-12-31T23:30:00-00:30', '2027-01-01T00:00:00Z'],
    ['2024-03-01T00:00:00+00:30', '2024-02-29T23:30:00Z'],
    ['2024-02-29T00:00:00-00:30', '2024-02-29T00:30:00Z'],
    ['2026-03-02T23:59:00+23:59', '2026-03-02T00:00:00Z'],
    ['2026-03-01T00:01:00-23:59', '2026-03-02T00:00:00Z'],
    ...Array.from({ length: 9 }, (_, n) => [`2026-03-02T03:00:00.${'123456789'.slice(0, n + 1)}+03:00`, `2026-03-02T00:00:00.${'123456789'.slice(0, n + 1)}Z`]),
  ];
  for (const [wire, utc] of equivalent) {
    assert.equal(normalizeDockerHealthTimestamp(wire), utc);
    const rows = [row({ Start: wire, End: wire })];
    const before = JSON.stringify(rows), now = Date.parse(utc);
    for (const [sinceValue, observedValue] of [[now, now + 1], [now + 1, now + 1], [now, now - 1]]) {
      const expected = original([row({ Start: utc, End: utc })], sinceValue, observedValue);
      assert.deepEqual(original(rows, sinceValue, observedValue), expected);
      assert.deepEqual(diagnostic.observe('ACADEMIC', 'CLEANUP', rows, sinceValue, observedValue, original), expected);
      assert.notEqual(diagnostic.snapshot().CLEANUP.ACADEMIC.last.reason, 'RESULT_MISMATCH');
    }
    assert.equal(JSON.stringify(rows), before, 'rows must not be mutated');
  }
  const invalid = ['+24:00', '+00:60', '-24:00', '-00:60', '+0:00', '+00:0', '+0000', '+00:00 ', '+00:00\n', '+00:00Z'];
  for (const offset of invalid) assert.deepEqual(original([row({ Start: `2026-03-02T00:00:00${offset}` })], since, observed), unchanged);
  for (const wire of ['2026-02-29T00:00:00+03:00', '2024-02-30T00:00:00+03:00', '2026-04-31T00:00:00+00:00',
    '2026-13-01T00:00:00+00:00', '2026-03-02T24:00:00+00:00', '2026-03-02T00:60:00+00:00',
    '2026-03-02T00:00:60+00:00', '2026-03-02T00:00:00.1234567890+00:00', '2026-03-02 00:00:00+00:00',
    '0000-01-01T00:00:00+00:01', '9999-12-31T23:59:59-00:01']) {
    assert.equal(normalizeDockerHealthTimestamp(wire), null);
    assert.deepEqual(original([row({ Start: wire })], since, observed), unchanged);
  }
  const nanoRows = (first, last) => [row({ Start: `2026-03-02T03:00:00.00000000${first}+03:00`, End: `2026-03-01T20:30:00.00000000${last}-03:30` })];
  assert.deepEqual(original(nanoRows(1, 1), since, observed), { channelState: 'READY', fresh: true });
  assert.deepEqual(original(nanoRows(1, 2), since, observed), { channelState: 'READY', fresh: true });
  assert.deepEqual(original(nanoRows(2, 1), since, observed), unchanged);
  const beforeCleanup = diagnostic.snapshot().MAIN;
  diagnostic.observe('ATTENDANCE', 'CLEANUP', [row()], since, observed, original);
  assert.deepEqual(diagnostic.snapshot().MAIN, beforeCleanup);
  assert.equal(diagnostic.snapshot().MAIN.ATTENDANCE.firstUnknown.reason, 'ROWS_TYPE');
  assert.ok(!JSON.stringify(diagnostic.snapshot()).includes(canary));
  const bounded = createHealthDiagnostic();
  for (let n = 0; n < 4097; n++) bounded.observe('ATTENDANCE', 'MAIN', [row()], since, observed, original);
  assert.equal(bounded.snapshot().MAIN.ATTENDANCE.observations, 4096);
  assert.equal(bounded.snapshot().MAIN.ATTENDANCE.truncated, true);
  // Execute the actual retained readiness collector with synthetic in-memory logs only.
  const constants = ['readinessSources', 'readinessCauses', 'readinessChannelStates', 'readinessConnectKinds', 'replayStatuses'].map(name => {
    const start = source.indexOf(`  const ${name} =`), end = source.indexOf(' as const;', start) + ' as const;'.length;
    assert.ok(start > 0 && end > start); return source.slice(start, end);
  }).join('\n');
  const captureStart = source.indexOf('  const captureReadinessFailure ='), captureEnd = source.indexOf('  let replaySince:', captureStart);
  assert.ok(captureStart > 0 && captureEnd > captureStart);
  const capturePath = join(temporary, 'readiness.mts');
  writeFileSync(capturePath, `import { withoutGatewayAdmissionDiagnostic } from '${helper}';
    type ReadinessLog = any; type ReadinessWarn = any;
    ${constants}
    export function probe(payloads: Record<string, string>, id: string | undefined) {
      const diagnostic: any = {}; const saved = {}; let nativeFailure = saved;
      const readinessRequest = id === undefined ? undefined : { id, since: '', until: '' };
      const identity = () => ({ running: true, startedAt: 'fixed' });
      const pins = { 'api-gateway': { id: 'api-gateway' }, 'academic-service': { id: 'academic-service' } };
      const docker = (args: string[]) => payloads[args.at(-1)!] ?? '';
      ${source.slice(captureStart, captureEnd)}
      captureReadinessFailure(); return { ...diagnostic.readinessFailureLog, nativePreserved: nativeFailure === saved };
    }`);
  const { probe } = await import(pathToFileURL(capturePath).href);
  const java = readFileSync(join(directory, '../../../services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilter.java'), 'utf8');
  const enumValues = name => { const marker = `private enum ${name} {`; const start = java.indexOf(marker); assert.ok(start > 0); return java.slice(start + marker.length, java.indexOf('}', start)).split(/[,\s]+/).filter(Boolean); };
  assert.deepEqual(GATEWAY_ADMISSION_CHECKS, enumValues('Check'));
  assert.deepEqual(GATEWAY_ADMISSION_PARSERS, enumValues('ParserFailure'));
  assert.ok(java.includes('cause={} check={} parser={} startWallMs={} validationNowMs={} issuedAtMs={} expiresAtMs={} elapsedMs={} wallElapsedMs={} correlation=UNCONFIRMED'));
  const id = '00000000-0000-4000-8000-000000000001';
  const base = 'semester.preview failure source=GATEWAY_ISSUER_FILTER stage=FORWARDED_IDENTITY status=NONE cause=IDENTITY_REJECTED';
  const suffix = ' check=JWT_PARSE parser=SIGNATURE startWallMs=1000 validationNowMs=null issuedAtMs=null expiresAtMs=null elapsedMs=2 wallElapsedMs=-1 correlation=UNCONFIRMED';
  const gateway = msg => ({ 'api-gateway': JSON.stringify({ level: 'WARN', logger: 'ru.rutcampustrack.gateway.security.InternalJwtIssuerFilter', requestId: id, msg }) });
  const accepted = probe(gateway(base + suffix), id);
  assert.equal(accepted.state, 'REPORTED'); assert.equal(accepted.nativePreserved, true);
  assert.deepEqual(accepted, probe(gateway(base), id));
  assert.equal(probe(gateway(base + suffix.replace('validationNowMs=null issuedAtMs=null expiresAtMs=null', 'validationNowMs=1000 issuedAtMs=0 expiresAtMs=2000')), id).state, 'REPORTED');
  for (const check of GATEWAY_ADMISSION_CHECKS) assert.equal(probe(gateway(base + suffix.replace('check=JWT_PARSE', `check=${check}`)), id).state, 'REPORTED');
  for (const parser of GATEWAY_ADMISSION_PARSERS) assert.equal(probe(gateway(base + suffix.replace('parser=SIGNATURE', `parser=${parser}`)), id).state, 'REPORTED');
  for (const bad of [suffix + ' raw=' + canary, suffix + '\n', suffix.replace('JWT_PARSE', canary), suffix.replace('SIGNATURE', canary),
    suffix.replace('elapsedMs=2', 'elapsedMs=-1'), suffix.replace('startWallMs=1000', 'startWallMs=01'),
    suffix.replace('startWallMs=1000', 'startWallMs=9223372036854775808'), suffix.replace('validationNowMs=null', 'validationNowMs=NaN'),
    suffix.replace('correlation=UNCONFIRMED', 'correlation=CONFIRMED')]) {
    const result = probe(gateway(base + bad), id); assert.equal(result.unknownReason, 'TARGET_TEMPLATE');
    assert.equal(result.nativePreserved, true); assert.ok(!JSON.stringify(result).includes(canary));
  }
  assert.equal(probe(gateway(base + suffix), undefined).unknownReason, 'NO_REQUEST');
  assert.equal(probe(gateway(base + suffix), id.replace(/1$/, '2')).unknownReason, 'NO_MATCHING_WARN');
  const missing = gateway(base + suffix); missing['api-gateway'] = missing['api-gateway'].replace(id, 'invalid');
  assert.equal(probe(missing, id).unknownReason, 'TARGET_CORRELATION');
  const schedule = 'semester.preview failure source=SCHEDULE_CLIENT stage=SCHEDULE_PREVIEW status=UNAVAILABLE cause=RPC_STATUS';
  const channel = ' channelBefore=READY channelAtFailure=TRANSIENT_FAILURE connectKind=REFUSED endpointRelation=UNKNOWN';
  const academic = msg => ({ 'academic-service': JSON.stringify({ level: 'WARN', logger: 'ru.rutcampustrack.academic.grpc.ScheduleGrpcClient', requestId: id, msg }) });
  assert.equal(probe(academic(schedule + channel), id).state, 'REPORTED');
  assert.equal(probe(academic('semester.preview failure source=SCHEDULE_CLIENT stage=SCHEDULE_PREVIEW status=NONE cause=INVALID_ARGUMENT'), id).state, 'REPORTED');
  assert.equal(probe(academic(schedule + suffix), id).unknownReason, 'TARGET_TEMPLATE');
  assert.equal(probe(gateway(base + channel), id).unknownReason, 'TARGET_TEMPLATE');
  const owner = { private: canary };
  observeHealth(owner, 'ATTENDANCE', [row({ Output: canary })], since, observed, original, temporary);
  // Durable before any finally/cleanup writer: simulate an abort immediately after observation.
  const checkpoint = JSON.parse(readFileSync(join(temporary, 'health-observation.safe.json'), 'utf8'));
  assert.equal(checkpoint.MAIN.ATTENDANCE.firstUnknown.reason, 'BODY_JSON');
  assert.ok(!JSON.stringify(checkpoint).includes(canary));
  const originalReceipt = '{"schema":"semester-reconciliation.v11"}\n';
  writeHealthDiagnostic(owner, temporary, originalReceipt, { flag: 'wx', mode: 0o600 });
  assert.equal(readFileSync(join(temporary, 'reconciliation.safe.json'), 'utf8'), originalReceipt);
  assert.ok(!readFileSync(join(temporary, 'health-observation.safe.json'), 'utf8').includes(canary));
  const failedWrite = join(temporary, 'failed-primary'); mkdirSync(failedWrite, { mode: 0o700 });
  const second = {};
  observeHealth(second, 'ACADEMIC', [row({ Start: canary })], since, observed, original, failedWrite);
  writeFileSync(join(failedWrite, 'reconciliation.safe.json'), 'preexisting-primary', { flag: 'wx' });
  assert.throws(() => writeHealthDiagnostic(second, failedWrite, originalReceipt, { flag: 'wx', mode: 0o600 }), { code: 'EEXIST' });
  const retained = JSON.parse(readFileSync(join(failedWrite, 'health-observation.safe.json'), 'utf8'));
  assert.equal(retained.MAIN.ACADEMIC.firstUnknown.reason, 'START_FORMAT');
  assert.equal(readFileSync(join(failedWrite, 'reconciliation.safe.json'), 'utf8'), 'preexisting-primary');
  console.log(JSON.stringify({ result: 'PASS', cases: reasons.size, originalParserEquivalent: true,
    mainPreservedAfterCleanup: true, canaryAbsent: true, boundedCounts: true, originalReceiptUnchanged: true,
    checkpointBeforeFinalWriter: true, checkpointSurvivesPrimaryWriteFailure: true,
    strictOffsetEquivalents: equivalent.length, malformedOffsetAndCalendarRejected: true,
    nanosecondOffsetOrderPreserved: true, rowsUnmodified: true, currentGatewaySuffixAndLegacy: true, actualReadinessCorrelationGates: true, sidecar: diagnostic.snapshot() }));
} finally {
  rmSync(temporary, { recursive: true, force: true });
}
