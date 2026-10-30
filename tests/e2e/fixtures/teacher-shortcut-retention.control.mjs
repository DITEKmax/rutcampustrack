/** Execute the existing guarded private sink on isolated temporary files only. */
import assert from 'node:assert/strict';
import * as fs from 'node:fs';
import { resolve, join } from 'node:path';
import { tmpdir } from 'node:os';
import { stripTypeScriptTypes } from 'node:module';
const root = fs.mkdtempSync(join(tmpdir(), 'rct-click-privacy-'));
fs.chmodSync(root, 0o700);
const text = fs.readFileSync(new URL('./artifact-privacy.ts', import.meta.url), 'utf8');
const start = text.indexOf('const failedCiRawCounts = '), end = text.indexOf('/** Run before Playwright', start);
assert.ok(start > 0 && end > start);
const source = stripTypeScriptTypes(text.slice(start, end), { mode: 'transform' }).replace(/export /g, '');
const title = 'HEADMAN marks the real roster and TEACHER reads without acquiring write permission';
const nonce = '0123456789abcdef01234567';
const run = resolve(root, '.agent/evidence/local-runs', `rct-local-stage17-${nonce.slice(0, 12)}`);
const directory = resolve(run, 'framework-private/failed-ci'), manifest = resolve(run, 'owner.json');
fs.mkdirSync(directory, { recursive: true, mode: 0o700 });
for (const path of [run, resolve(run, 'framework-private'), directory]) fs.chmodSync(path, 0o700);
const owner = { root, run, nonce, uid: process.getuid(), source_sha: 'source-control', mode: 'ui-batch' };
const writeOwner = value => fs.writeFileSync(manifest, JSON.stringify(value), { mode: 0o600 });
writeOwner(owner);
const env = { RCT_LOCAL_RELEASE_MODE: 'failed-ci', RCT_LOCAL_STAGE17_NONCE: nonce,
  RCT_LOCAL_STAGE17_MANIFEST: manifest, RCT_LOCAL_RELEASE_REVISION: owner.source_sha, RCT_LOCAL_STAGE17_MODE: owner.mode };
const markers = [];
const proc = { env, platform: 'linux', getuid: () => process.getuid(), stderr: { write: value => markers.push(value) } };
const names = ['closeSync', 'constants', 'fstatSync', 'lstatSync', 'openSync', 'readFileSync', 'realpathSync', 'writeSync'];
const factory = new Function(...names, 'resolve', '__dirname', 'failedCaseId', 'process', `${source}\nreturn { retainTeacherShortcutClickError, retainFailedCiRawError };`);
const create = (overrides = {}) => factory(...names.map(name => overrides[name] ?? fs[name]), resolve, resolve(root, 'tests/e2e/fixtures'),
  value => value === title ? 'HEADMAN_TEACHER_READ' : value === 'other-allowed' ? 'SEMESTER_METADATA' : undefined, proc);
const sink = create(), info = { title }, target = resolve(directory, 'test-errors.private.jsonl');
const error = Object.assign(new Error('canary-private-secondary\nCall log:\n  - <overlay>canary-private</overlay> intercepts pointer events'), { name: 'TimeoutError' });
const original = Object.getOwnPropertyDescriptors(error);
try {
  env.RCT_LOCAL_RELEASE_MODE = 'ordinary-ci';
  assert.equal(sink.retainTeacherShortcutClickError(info, error), 'OFF');
  assert.equal(fs.existsSync(target), false);
  env.RCT_LOCAL_RELEASE_MODE = 'failed-ci';
  assert.equal(sink.retainTeacherShortcutClickError({ title: 'other-allowed' }, error), 'REJECTED');
  assert.equal(fs.existsSync(target), false);
  assert.equal(sink.retainTeacherShortcutClickError(info, error), 'RETAINED');
  const rows = () => fs.readFileSync(target, 'utf8').trimEnd().split('\n').map(JSON.parse);
  assert.equal(rows()[0].message, original.message.value);
  assert.equal(rows()[0].stack, original.stack.value);
  assert.equal(fs.statSync(target).mode & 0o777, 0o600);
  assert.deepEqual(Object.getOwnPropertyDescriptors(error), original);
  assert.equal(sink.retainFailedCiRawError(info, error), 'ALREADY_RETAINED');
  assert.equal(rows().length, 1, 'Primary scrub reuses object dedup');
  let getters = 0;
  const hostile = Object.defineProperty({}, 'message', { get() { getters++; throw Error('private getter'); } });
  assert.equal(sink.retainTeacherShortcutClickError(info, hostile), 'RETAINED');
  assert.equal(getters, 0);
  const long = { message: 'm'.repeat(10000), stack: 's'.repeat(20000), name: 'n'.repeat(200), code: 'c'.repeat(200) };
  assert.equal(sink.retainTeacherShortcutClickError(info, long), 'RETAINED');
  for (const [key, max] of [['message', 8192], ['stack', 16384], ['name', 128], ['code', 128]]) assert.equal(rows().at(-1)[key].length, max);
  for (let index = 3; index < 16; index++) assert.equal(sink.retainTeacherShortcutClickError(info, new Error('private-canary')), 'RETAINED');
  const before = fs.readFileSync(target);
  assert.equal(sink.retainTeacherShortcutClickError(info, new Error('bound')), 'FAILED');
  assert.deepEqual(fs.readFileSync(target), before);
  for (const [key, bad] of [['root', root + '/wrong'], ['run', run + '/wrong'], ['nonce', 'bad'], ['uid', process.getuid() + 1], ['source_sha', 'wrong'], ['mode', 'unknown']]) {
    writeOwner({ ...owner, [key]: bad });
    assert.equal(create().retainTeacherShortcutClickError(info, new Error('private-canary')), 'FAILED');
  }
  writeOwner(owner);
  fs.chmodSync(manifest, 0o644);
  assert.equal(create().retainTeacherShortcutClickError(info, new Error('private-canary')), 'FAILED');
  fs.chmodSync(manifest, 0o600);
  fs.chmodSync(directory, 0o755);
  assert.equal(create().retainTeacherShortcutClickError(info, new Error('private-canary')), 'FAILED');
  fs.chmodSync(directory, 0o700);
  fs.chmodSync(target, 0o644);
  assert.equal(create().retainTeacherShortcutClickError(info, new Error('private-canary')), 'FAILED');
  fs.chmodSync(target, 0o600);
  assert.equal(create({ writeSync: () => { throw new Error('private-write-canary'); } }).retainTeacherShortcutClickError(info, new Error('private-canary')), 'FAILED');
  const sizeFd = fs.openSync(target, 'r+'); fs.ftruncateSync(sizeFd, 256 * 1024); fs.closeSync(sizeFd);
  assert.equal(create().retainTeacherShortcutClickError(info, new Error('private-canary')), 'FAILED');
  fs.unlinkSync(target);
  const other = resolve(root, 'unowned-file'); fs.writeFileSync(other, 'untouched', { mode: 0o600 }); fs.symlinkSync(other, target);
  assert.equal(create().retainTeacherShortcutClickError(info, new Error('private-canary')), 'FAILED');
  assert.equal(fs.readFileSync(other, 'utf8'), 'untouched');
  assert.ok(markers.every(value => value === 'RCT_FAILED_CI_RAW_RETENTION_V1 FAIL\n'));
  console.log(JSON.stringify({ state: 'PASS', actualSecondaryCallLogRetained0600: true, objectUnmodified: true,
    offModeWrites: false, sameSinkDedup: true, descriptorsOnly: true, sourceModeUidNoncePathGuards: true,
    fieldCountFileBounds: true, privateFailureCannotEscapeWrapper: true, publicCanary: false, native: 'NOT_RUN' }));
} finally {
  fs.rmSync(root, { recursive: true, force: true });
}
