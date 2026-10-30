/** Pure exact-online observer and existing private error sink controls. No browser/server. */
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import { resolve, join } from 'node:path';
import { tmpdir } from 'node:os';
import { runInNewContext } from 'node:vm';
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { stripTypeScriptTypes } from 'node:module';
const text = fs.readFileSync(new URL('./safe-reporter.ts', import.meta.url), 'utf8');
const begin = text.indexOf('export function createD002ReconnectObserver()');
const end = text.indexOf('\nexport const safeFailureAnnotation', begin);
const observerSource = stripTypeScriptTypes(text.slice(begin, end), { mode: 'transform' }).replace(/export /g, '');
const events = new Map(), calls = [];
const original = function (...args) {
  let resolve, reject;
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail; });
  calls.push({ receiver: this, args, promise, resolve, reject });
  return promise;
};
const sandbox = { fetch: original, URL, Request, location: { origin: 'https://private.invalid', href: 'https://private.invalid/app/' },
  navigator: { onLine: true }, addEventListener: (kind, callback) => events.set(kind, callback),
  removeEventListener: kind => events.delete(kind) };
const create = () => runInNewContext(`${observerSource}; createD002ReconnectObserver()`, sandbox);
const same = (actual, expected) => assert.deepEqual(JSON.parse(JSON.stringify(actual)), expected);
let observer = create();
const receiver = {}, init = { method: 'POST' };
const pending = sandbox.fetch.call(receiver, '/api/auth/refresh', init);
assert.equal(pending, calls[0].promise); assert.equal(calls[0].receiver, receiver); assert.equal(calls[0].args[1], init);
events.get('offline')(); events.get('online')();
same(observer.read().atOnline, { refresh: 'PENDING', current: 'NOT_CALLED' });
const error = new Error('PRIVATE_D002_ERROR_CANARY');
calls[0].reject(error); assert.equal(await pending.catch(value => value), error);
assert.equal(observer.read().native.preOnline.refresh.phase, 'REJECTED');
same(observer.read().atOnline, { refresh: 'PENDING', current: 'NOT_CALLED' });
events.get('online')(); assert.equal(observer.read().atOnline.refresh, 'PENDING', 'first online snapshot is immutable');
observer.restore(); assert.equal(sandbox.fetch, original);
observer = create();
const settled = sandbox.fetch('/api/auth/refresh', init); calls.at(-1).reject(error); await settled.catch(() => {});
events.get('online')(); assert.equal(observer.read().atOnline.refresh, 'REJECTED'); observer.restore();
observer = create();
const fulfilled = sandbox.fetch('/api/auth/refresh', init); calls.at(-1).resolve(new Response('{}')); await fulfilled;
events.get('online')(); assert.equal(observer.read().atOnline.refresh, 'FULFILLED'); observer.restore();
sandbox.navigator.onLine = false
observer = create();
assert.equal(observer.read().offlineSeen, true)
same(observer.read().atOnline, { refresh: 'UNKNOWN', current: 'UNKNOWN' });
sandbox.fetch('/api/auth/refresh', init); sandbox.fetch('/api/auth/refresh', init);
events.get('online')(); assert.equal(observer.read().atOnline.refresh, 'UNKNOWN'); observer.restore();
assert.equal(runInNewContext(`${observerSource}; createD002ReconnectObserver().read()`, {}), null);

const start = text.indexOf("export const d002ReconnectObservationAnnotation"), stop = text.indexOf('\nconst startupFixtureStages', start);
const closure = stripTypeScriptTypes(text.slice(start, stop), { mode: 'transform' }).replace(/export /g, '');
const ownData = (value, key) => value && typeof value === 'object' ? Object.getOwnPropertyDescriptor(value, key)?.value : undefined;
const closed = new Function('ownData', 'retainDiagnosticObservation', `${closure}; return closedD002ReconnectObservation;`)(ownData, () => {});
const state = { phase: 'NOT_CALLED', multiple: false };
const observation = { offlineSeen: true, onlineSeen: true, online: true, observationUnknown: false,
  atOnline: { refresh: 'PENDING', current: 'NOT_CALLED' },
  native: { preOnline: { refresh: { phase: 'REJECTED', multiple: false }, current: state }, postOnline: { refresh: state, current: state } },
  pageHttp: { refresh: { requested: true, responded: false, failed: true }, current: { requested: false, responded: false, failed: false } },
  priorPending: { refresh: false, current: false }, refreshHttpStatus: 'UNOBSERVED' };
assert.deepEqual(closed(observation), observation);
for (const candidate of [{ ...observation, body: 'PRIVATE_CANARY' },
  { ...observation, atOnline: { ...observation.atOnline, url: 'PRIVATE_CANARY' } },
  { ...observation, atOnline: { refresh: 'PRIVATE_CANARY', current: 'NOT_CALLED' } }]) assert.equal(closed(candidate), undefined);
let getters = 0;
const hostile = { ...observation, atOnline: Object.defineProperty({ current: 'NOT_CALLED' }, 'refresh', { get() { getters++; return 'PENDING'; } }) };
assert.equal(closed(hostile), undefined); assert.equal(getters, 0);
assert.equal(JSON.stringify(closed(observation)).includes('PRIVATE'), false);

const root = fs.mkdtempSync(join(tmpdir(), 'rct-d002-private-')); fs.chmodSync(root, 0o700);
try {
  const privacy = fs.readFileSync(new URL('./artifact-privacy.ts', import.meta.url), 'utf8');
  const a = privacy.indexOf('const failedCiRawCounts = '), b = privacy.indexOf('/** Run before Playwright', a);
  const source = stripTypeScriptTypes(privacy.slice(a,b), { mode: 'transform' }).replace(/export /g, '');
  const nonce = 'abcdef0123456789abcdef01', caseId = 'D002_RECONNECT_ACCOUNT_AUTHORITY';
  const cases = [caseId, 'D002_LATE_ACCOUNT_HOMEWORK']
  const title = 'D002 offline reload rechecks suspended grants before role selection'
  const lateTitle =
    'D002 late prior-account homework cannot replace the new account or offline snapshot'
  const actualRoot = fileURLToPath(new URL('../../../', import.meta.url))
  const produced = spawnSync(
    'python3',
    [
      '-B',
      '-c',
      "import sys; sys.path.insert(0, sys.argv[1] + '/scripts'); import local_e2e_async as lane; print(lane.Runner('client', release_lane='client', client_cases=lane.D002_CASES).env['RCT_LOCAL_CLIENT_CASES'])",
      actualRoot,
    ],
    { encoding: 'utf8' },
  )
  assert.equal(produced.status, 0)
  const serializedPair = produced.stdout.trim()
  assert.deepEqual(JSON.parse(serializedPair), cases)
  assert.equal(
    serializedPair,
    JSON.stringify(cases),
    'Actual Python producer uses canonical Node-compatible JSON',
  )
  const run = resolve(root, '.agent/evidence/local-runs', `rct-local-stage17-${nonce.slice(0,12)}`), directory = resolve(run, 'framework-private/failed-ci');
  fs.mkdirSync(directory, { recursive: true, mode: 0o700 });
  const manifest = resolve(run, 'owner.json'), target = resolve(directory, 'test-errors.private.jsonl');
  const owner = {
    root,
    run,
    nonce,
    uid: process.getuid(),
    source_sha: 'source',
    mode: 'client',
    release_lane: 'client',
    client_cases: cases,
  }
  const writeOwner = value => fs.writeFileSync(manifest, JSON.stringify(value), { mode: 0o600 }); writeOwner(owner);
  const env = {
    RCT_LOCAL_RELEASE_MODE: '',
    RCT_LOCAL_CLIENT_CASES: serializedPair,
    RCT_LOCAL_STAGE17_NONCE: nonce,
    RCT_LOCAL_STAGE17_MANIFEST: manifest,
    RCT_LOCAL_RELEASE_REVISION: 'source',
    RCT_LOCAL_STAGE17_MODE: 'client',
  }
  const proc = { env, platform: 'linux', getuid: () => process.getuid(), stderr: { write: value => assert.equal(value, 'RCT_FAILED_CI_RAW_RETENTION_V1 FAIL\n') } };
  const names = ['closeSync','constants','fstatSync','lstatSync','openSync','readFileSync','realpathSync','writeSync'];
  const factory = new Function(...names, 'resolve', '__dirname', 'failedCaseId', 'process', `${source}; return retainFailedCiRawError;`);
  const sink = () => factory(...names.map(name => fs[name]), resolve, resolve(root, 'tests/e2e/fixtures'), () => undefined, proc);
  assert.equal(sink()({ title }, error), 'OFF'); assert.equal(fs.existsSync(target), false);
  env.RCT_LOCAL_RELEASE_MODE = 'failed-ci'
  delete env.RCT_LOCAL_CLIENT_CASES
  assert.equal(sink()({ title }, error), 'REJECTED'); assert.equal(fs.existsSync(target), false);
  env.RCT_LOCAL_CLIENT_CASES = serializedPair
  for (const change of [
    { client_cases: ['wrong'] },
    { mode: 'ui-batch' },
    { release_lane: null },
    { source_sha: 'wrong' },
  ]) {
    writeOwner({ ...owner, ...change })
    assert.equal(sink()({ title }, error), 'FAILED')
    assert.equal(fs.existsSync(target), false)
  }
  writeOwner(owner);
  env.RCT_LOCAL_CLIENT_CASES = JSON.stringify(cases, null, 1)
  assert.equal(sink()({ title }, error), 'REJECTED')
  env.RCT_LOCAL_CLIENT_CASES = serializedPair
  const retain = sink(); const before = Object.getOwnPropertyDescriptors(error);
  assert.equal(retain({ title }, error), 'RETAINED');
  assert.equal(fs.statSync(target).mode & 0o777, 0o600);
  assert.ok(fs.readFileSync(target, 'utf8').includes('PRIVATE_D002_ERROR_CANARY'));
  assert.deepEqual(Object.getOwnPropertyDescriptors(error), before);
  assert.equal(retain({ title }, error), 'ALREADY_RETAINED');
  assert.equal(retain({ title: lateTitle }, new Error('PRIVATE_LATE_CANARY')), 'RETAINED')
  const rawCases = fs
    .readFileSync(target, 'utf8')
    .trim()
    .split('\n')
    .map((line) => JSON.parse(line).caseId)
  assert.deepEqual(rawCases, cases)
  const producedAuthority = spawnSync('python3', ['-B', '-c',
    "import sys; sys.path.insert(0, sys.argv[1] + '/scripts'); import local_e2e_async as lane; print(lane.Runner('client', release_lane='client', client_cases=lane.D002_AUTHORITY_CASES).env['RCT_LOCAL_CLIENT_CASES'])", actualRoot], { encoding: 'utf8' });
  assert.equal(producedAuthority.status, 0);
  const serializedAuthority = producedAuthority.stdout.trim();
  assert.equal(serializedAuthority, JSON.stringify(cases.slice(0, 1)), 'Actual Python A-only canonical environment');
  env.RCT_LOCAL_CLIENT_CASES = serializedAuthority;
  writeOwner({ ...owner, client_cases: cases.slice(0, 1) });
  const authority = sink();
  assert.equal(authority({ title }, new Error('PRIVATE_AUTHORITY_CANARY')), 'RETAINED');
  const retainedBeforeForeign = fs.readFileSync(target);
  assert.equal(authority({ title: lateTitle }, new Error('PRIVATE_UNSELECTED_B_CANARY')), 'REJECTED');
  assert.deepEqual(fs.readFileSync(target), retainedBeforeForeign);
  writeOwner(owner);
  assert.equal(sink()({ title }, new Error('PRIVATE_UNBOUND_CANARY')), 'FAILED');
  env.RCT_LOCAL_CLIENT_CASES = serializedPair;
  writeOwner({ ...owner, client_cases: cases.slice(0, 1) });
  assert.equal(sink()({ title }, new Error('PRIVATE_UNBOUND_CANARY')), 'FAILED');
  for (const rejected of [[cases[1]], [...cases].reverse(), ['FOREIGN']]) {
    env.RCT_LOCAL_CLIENT_CASES = JSON.stringify(rejected);
    writeOwner({ ...owner, client_cases: rejected });
    assert.equal(sink()({ title }, new Error('PRIVATE_FOREIGN_CANARY')), 'REJECTED');
    assert.equal(sink()({ title: lateTitle }, new Error('PRIVATE_FOREIGN_CANARY')), 'REJECTED');
  }

} finally { fs.rmSync(root, { recursive: true, force: true }); }
// Execute the actual changed finally statements, not a mirrored cleanup model.
const storySource = fs.readFileSync(new URL('../specs/mobile-real-server.spec.ts', import.meta.url), 'utf8');
const cleanupSites = {
  ADMISSION: ['observerRestore', 'observerDispose', 'network'],
  ROLE: ['network', 'role'],
  ACCOUNT: ['gate', 'gateDispose', 'network'],
};
let cleanupInjections = 0;
for (const [kind, sites] of Object.entries(cleanupSites)) {
  const beginMarker = `// D002_${kind}_CLEANUP_BEGIN`, endMarker = `// D002_${kind}_CLEANUP_END`;
  const begin = storySource.indexOf(beginMarker), end = storySource.indexOf(endMarker, begin);
  assert.ok(begin >= 0 && end > begin);
  const actual = stripTypeScriptTypes(storySource.slice(begin + beginMarker.length, end), { mode: 'transform' });
  for (const failedSites of [[], ...sites.map(site => [site]), sites]) {
    for (const withPrimary of [false, true]) {
      const primary = withPrimary ? new Error('PRIVATE_PRIMARY_CANARY') : undefined;
      const failures = new Map(failedSites.map(site => [site, new Error('PRIVATE_CLEANUP_CANARY')]));
      const soft = [];
      const fail = name => { if (failures.has(name)) throw failures.get(name); };
      const environment = {
        primary, offlinePrimary: primary ? { error: primary } : undefined,
        rolePrimary: primary ? { error: primary } : undefined, latePrimary: primary ? { error: primary } : undefined,
        page: { off() {} }, countRole() {}, observeReconnect() {}, detachAuthObservation() {},
        reconnectObserver: { evaluate: async callback => callback({ restore: async () => fail('observerRestore') }), dispose: async () => fail('observerDispose') },
        reconnectTimer: undefined, currentObserved: true, cancelCurrent() {}, onlineRestored: false,
        context: { setOffline: async () => fail('network') }, dataset: { group: { id: 1 }, admin: { call: async () => fail('role') } }, rolePath: '/synthetic-own-role',
        gateDisposed: false, heldFetch: { evaluate: async callback => callback({ cleanup: async () => fail('gate') }), dispose: async () => fail('gateDispose') },
        expect: { soft(value, label) { assert.equal(value, false); assert.match(label, /^D002 (grant|old feed) [A-Z_]+$/); soft.push(label); return { toBe(expected) { assert.equal(expected, true); } }; } },
      };
      const invoke = new Function(...Object.keys(environment), `return (async () => { try { if (primary) throw primary; } finally { ${actual} } })();`);
      let caught;
      try { await invoke(...Object.values(environment)); } catch (error) { caught = error; }
      if (primary) assert.equal(caught, primary, `${kind} keeps original body error identity`);
      else if (failedSites.length) assert.ok(failedSites.some(site => failures.get(site) === caught), `${kind} successful body remains failed on cleanup`);
      else assert.equal(caught, undefined);
      assert.equal(soft.length, failedSites.length, `${kind} exposes every failed cleanup as a finite soft error`);
      cleanupInjections++;
    }
  }
}

console.log(
  JSON.stringify({
    state: 'PASS',
    actualFinallyFailureInjections: cleanupInjections,
    nativeAtOnlinePendingImmutable: true,
    rejectedBeforeOnlineDistinct: true,
    fulfilledBeforeOnlineDistinct: true,
    unknownAndMultipleClosed: true,
    promiseIdentityPreserved: true,
    privateFieldGetterClosure: true,
    exactClientRaw0600: true,
    defaultCiRawOff: true,
    actualPythonPairBothTitlesRetained: true,
    actualPythonAuthorityOnlyRetained: true,
    authorityRejectsUnselectedBAndCrossedBindings: true,
    spacedPairRejected: true,
    native: 'NOT_RUN',
  }),
)

