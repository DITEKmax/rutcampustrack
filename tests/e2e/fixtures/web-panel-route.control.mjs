/** Finite public-adapter controls; actual Vue rendering still requires a separate browser proof. */
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const require = createRequire(import.meta.url);
const { requireOrImport } = require(join(dirname(require.resolve('playwright/package.json')), 'lib/transform/transform.js'));
const directory = dirname(fileURLToPath(import.meta.url));
const { observeWebPanelReads, expectRenderedStat, expectWebPanelRoute } = await requireOrImport(join(directory, 'web-panel-route.ts'));
const { webLoginIntent } = await requireOrImport(join(directory, 'auth.ts'));
const controls = [
  { route: '/admin/dashboard', paths: ['/api/academic/admin-access/me', '/api/academic/dashboard/stats', '/api/academic/dashboard/activity'], keys: ['authority', 'stats', 'activity'] },
  { route: '/teacher/journal', paths: ['/api/v1/teacher/semester', '/api/v1/teacher/catalog'], keys: ['teacherSemester', 'teacherCatalog'] },
  { route: '/teacher/statistics', paths: ['/api/v1/teacher/semester', '/api/v1/teacher/catalog'], keys: ['teacherSemester', 'teacherCatalog'] },
];
function fixture() {
  const page = new EventEmitter();
  page.url = () => 'https://synthetic.invalid/';
  page.waitForResponse = () => { throw new Error('Unfinished required feature read'); };
  return page;
}
function response(path, { status = 200, finished = async () => null, json = async () => ({ owned: true }), method = 'GET' } = {}) {
  return { url: () => 'https://synthetic.invalid' + path, request: () => ({ method: () => method }),
    status: () => status, finished, json };
}
let positive = 0, rejected = 0;
for (const { route, paths, keys } of controls) {
  const page = fixture(), observer = observeWebPanelReads(page, route);
  for (const path of paths) page.emit('response', response(path));
  assert.deepEqual(Object.keys(await observer.initial()), keys);
  observer.dispose(); assert.equal(page.listenerCount('response'), 0); positive++;
  for (const failed of paths) {
    const brokenPage = fixture(), broken = observeWebPanelReads(brokenPage, route);
    for (const path of paths) brokenPage.emit('response', response(path, { status: path === failed ? 500 : 200 }));
    await assert.rejects(broken.initial()); broken.dispose(); rejected++;
  }
}
const { route, paths } = controls[0];
{
  const page = fixture(), observer = observeWebPanelReads(page, route);
  page.emit('response', response(paths[0]));
  await assert.rejects(observer.initial()); observer.dispose(); rejected++;
}
{
  const page = fixture(), observer = observeWebPanelReads(page, route);
  let release, entered;
  const body = new Promise(resolve => { release = resolve; });
  const pendingBody = new Promise(resolve => { entered = resolve; });
  for (const path of paths) page.emit('response', response(path, { finished: path === paths[2] ? () => { entered(); return body; } : async () => null }));
  let settled = false;
  const result = observer.initial().then(value => { settled = true; return value; });
  await pendingBody;
  assert.equal(settled, false); release(null); await result; observer.dispose(); positive++;
}
for (const failure of ['body', 'decode', 'scalar', 'foreign-origin', 'wrong-method']) {
  const page = fixture(), observer = observeWebPanelReads(page, route);
  for (const path of paths) {
    const current = response(path, path === paths[1] ? {
      finished: async () => failure === 'body' ? 'failed' : null,
      json: async () => { if (failure === 'decode') throw new Error('Invalid feature body'); return failure === 'scalar' ? 42 : {}; },
      method: failure === 'wrong-method' ? 'POST' : 'GET',
    } : {});
    if (failure === 'foreign-origin' && path === paths[1]) current.url = () => 'https://other.invalid' + path;
    page.emit('response', current);
  }
  await assert.rejects(observer.initial()); observer.dispose(); rejected++;
}
{
  const page = fixture(), observer = observeWebPanelReads(page, '/admin/semesters');
  page.emit('response', response('/api/academic/admin-access/me'));
  page.emit('response', response('/api/academic/semesters?size=100', { json: async () => ({ items: [{ name: 'First semester' }] }) }));
  page.emit('response', response('/api/academic/semesters?page=1&size=100', { json: async () => ({ items: [{ name: 'Next semester' }] }) }));
  const first = await observer.initial(), next = await observer.read('semesters');
  assert.deepEqual([...first.semesters.items, ...next.items].map(item => item.name), ['First semester', 'Next semester']);
  observer.dispose(); positive++;
}
// Inject only the assertion adapter; the live function chooses the public rendered-value locators.
const assertion = target => ({
  toHaveCount: async count => assert.equal(target.count, count),
  toHaveText: async value => assert.equal(target.text, value),
});
const owner = card => ({ page: () => ({ getByText: () => ({}) }), locator: () => ({ filter: () => card }) });
const card = text => ({ count: 1, locator: () => ({ text }) });
await expectRenderedStat(owner(card('17')), 'Synthetic count', 17, assertion); positive++;
for (const candidate of [card(''), card('0'), { count: 0, locator: () => ({ text: '17' }) }]) {
  await assert.rejects(expectRenderedStat(owner(candidate), 'Synthetic count', 17, assertion)); rejected++;
}
for (const [expectedLandingPath, role, intended] of [
  ['/student/home', 'STUDENT', 'STUDENT'], ['/headman', 'STUDENT', 'HEADMAN'],
  ['/teacher/home', 'TEACHER', 'TEACHER'], ['/admin/dashboard', 'ADMIN', 'ADMIN'],
]) {
  assert.equal(webLoginIntent({ expectedLandingPath, role }).role, intended); positive++;
}
for (const path of ['/headman/dashboard', '/student/stats', '/student/excuses', '/teacher/stats']) {
  assert.throws(() => observeWebPanelReads(fixture(), path), /PENDING_VUE_ROUTE_ORACLE/);
  await assert.rejects(expectWebPanelRoute(fixture(), path), /PENDING_VUE_ROUTE_ORACLE/); rejected++;
}
assert.throws(() => webLoginIntent({ expectedLandingPath: '/headman/dashboard', role: 'STUDENT', isHeadman: true }), /PENDING_VUE_AUTH_FIXTURE/); rejected++;
assert.throws(() => webLoginIntent({ expectedLandingPath: '/teacher/home', role: 'ADMIN' }), /Conflicting fixture role/); rejected++;
console.log(JSON.stringify({ state: 'PASS', requiredCompanionSettlement: true, renderedValueSensitivity: true,
  positive, rejected, pendingLegacyIntent: true, vueBrowserProof: false }));
