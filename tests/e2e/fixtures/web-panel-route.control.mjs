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
  { route: '/student/schedule', paths: ['/api/v1/student/session', '/api/v1/student/schedule?semesterId=1'], keys: ['studentSession'], extra: ['studentSchedule'] },
  { route: '/student/statistics', paths: ['/api/v1/student/session', '/api/v1/student/statistics?semesterId=1'], keys: ['studentSession'], extra: ['studentStats'] },
  { route: '/student/requests', paths: ['/api/v1/student/session', '/api/v1/student/requests?bucket=OPEN&page=0&size=10', '/api/v1/student/requests/options'], keys: ['studentSession'], extra: ['studentRequests', 'studentOptions'] },
  { route: '/headman', paths: ['/api/academic/semesters', '/api/schedule/groups/1/lessons', '/api/academic/subjects',
    '/api/attendance/requests?bucket=OPEN&page=0&size=1&type=EXCUSE', '/api/attendance/requests?bucket=OPEN&page=0&size=1&type=LATE_CHECKIN'],
    keys: ['semesters', 'headmanLessons', 'subjects', 'excuseCount', 'lateCount'] },
  { route: '/headman/schedule', paths: ['/api/academic/semesters', '/api/schedule/lesson-slots', '/api/schedule/academic-week-policy', '/api/schedule/items', '/api/academic/assignments'],
    keys: ['semesters', 'slots', 'policy'], extra: ['items', 'assignments'] },
  { route: '/headman/requests?type=EXCUSE', paths: ['/api/attendance/requests?bucket=OPEN&page=0&size=20&type=EXCUSE'], keys: ['groupRequests'] },
  { route: '/headman/requests?type=LATE_CHECKIN', paths: ['/api/attendance/requests?bucket=OPEN&page=0&size=20&type=LATE_CHECKIN'], keys: ['groupRequests'] },
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
async function settle(observer, extra = []) {
  const values = await observer.initial();
  for (const key of extra) await observer.read(key);
  return values;
}
for (const { route, paths, keys, extra } of controls) {
  const page = fixture(), observer = observeWebPanelReads(page, route);
  for (const path of paths) page.emit('response', response(path));
  assert.deepEqual(Object.keys(await settle(observer, extra)), keys);
  observer.dispose(); assert.equal(page.listenerCount('response'), 0); positive++;
  for (const failed of paths) {
    const brokenPage = fixture(), broken = observeWebPanelReads(brokenPage, route);
    for (const path of paths) brokenPage.emit('response', response(path, { status: path === failed ? 500 : 200 }));
    await assert.rejects(settle(broken, extra)); broken.dispose(); rejected++;
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
{
  const page = fixture(), observer = observeWebPanelReads(page, '/student/requests');
  page.waitForResponse = predicate => new Promise(resolve => {
    const listener = candidate => { if (predicate(candidate)) { page.off('response', listener); resolve(candidate); } };
    page.on('response', listener);
  });
  const pending = [observer.read('studentDetail'), observer.read('studentDetail')];
  page.emit('response', response('/api/v1/student/requests/owned-first', { json: async () => ({ summary: { id: 'owned-first' } }) }));
  page.emit('response', response('/api/v1/student/requests/owned-next', { json: async () => ({ summary: { id: 'owned-next' } }) }));
  assert.deepEqual((await Promise.all(pending)).map(detail => detail.summary.id), ['owned-first', 'owned-next']);
  observer.dispose(); assert.equal(page.listenerCount('response'), 0); positive++;
}
for (const path of [
  '/api/attendance/requests?bucket=OPEN&page=0&size=20&type=LATE_CHECKIN',
  '/api/attendance/requests?bucket=ARCHIVE&page=0&size=20&type=EXCUSE',
  '/api/attendance/requests?bucket=OPEN&page=0&size=1&type=EXCUSE',
]) {
  const page = fixture(), observer = observeWebPanelReads(page, '/headman/requests?type=EXCUSE');
  page.emit('response', response(path));
  await assert.rejects(observer.initial()); observer.dispose(); rejected++;
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
for (const value of [-1, Infinity, NaN]) {
  await assert.rejects(expectRenderedStat(owner(card(String(value))), 'Synthetic count', value, assertion)); rejected++;
}
for (const [expectedLandingPath, role, intended] of [
  ['/student/home', 'STUDENT', 'STUDENT'], ['/headman', 'STUDENT', 'HEADMAN'],
  ['/headman', 'HEADMAN', 'HEADMAN'],
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
