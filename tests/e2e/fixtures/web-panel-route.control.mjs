/** Finite public-adapter controls; actual Angular rendering remains an ordinary browser proof. */
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const require = createRequire(import.meta.url);
const { requireOrImport } = require(join(dirname(require.resolve('playwright/package.json')), 'lib/transform/transform.js'));
const { observeWebPanelReads, expectRenderedStat } = await requireOrImport(join(dirname(fileURLToPath(import.meta.url)), 'web-panel-route.ts'));
const paths = ['/api/academic/assignments/my', '/api/academic/groups', '/api/academic/subjects', '/api/academic/semesters'];
function fixture() {
  const page = new EventEmitter();
  page.url = () => 'https://synthetic.invalid/teacher/journal';
  page.waitForResponse = () => { throw new Error('Unfinished required feature read'); };
  return page;
}
function response(path, { status = 200, finished = async () => null, json = async () => ({ _embedded: {} }) } = {}) {
  return { url: () => 'https://synthetic.invalid' + path, request: () => ({ method: () => 'GET' }),
    status: () => status, finished, json };
}
let positive = 0, rejected = 0;
for (const route of ['/teacher/journal', '/teacher/stats']) {
  const page = fixture(), observer = observeWebPanelReads(page, route);
  for (const path of paths) page.emit('response', response(path));
  assert.deepEqual(Object.keys(await observer.initial()), ['assignments', 'groups', 'subjects', 'semesters']);
  observer.dispose(); assert.equal(page.listenerCount('response'), 0); positive++;
  for (const failed of paths.slice(1)) {
    const brokenPage = fixture(), broken = observeWebPanelReads(brokenPage, route);
    for (const path of paths) brokenPage.emit('response', response(path, { status: path === failed ? 500 : 200 }));
    await assert.rejects(broken.initial()); broken.dispose(); rejected++;
  }
}
{
  const page = fixture(), observer = observeWebPanelReads(page, '/teacher/stats');
  page.emit('response', response(paths[0]));
  await assert.rejects(observer.initial()); observer.dispose(); rejected++;
}
{
  const page = fixture(), observer = observeWebPanelReads(page, '/teacher/stats');
  let release;
  const body = new Promise(resolve => { release = resolve; });
  for (const path of paths) page.emit('response', response(path, { finished: path === paths[1] ? () => body : async () => null }));
  let settled = false;
  const result = observer.initial().then(value => { settled = true; return value; });
  // Advance the observer to the pending companion body without wall-clock waits.
  for (let i = 0; i < 12; i++) await Promise.resolve();
  assert.equal(settled, false); release(null); await result; observer.dispose(); positive++;
}
for (const failure of ['body', 'decode', 'foreign-origin']) {
  const page = fixture(), observer = observeWebPanelReads(page, '/teacher/journal');
  for (const path of paths) {
    const current = response(path, path === paths[1] ? {
      finished: async () => failure === 'body' ? 'failed' : null,
      json: async () => { if (failure === 'decode') throw new Error('Invalid feature body'); return {}; },
    } : {});
    if (failure === 'foreign-origin' && path === paths[1]) current.url = () => 'https://other.invalid' + path;
    page.emit('response', current);
  }
  await assert.rejects(observer.initial()); observer.dispose(); rejected++;
}
// Inject only the assertion adapter; the live function still chooses/reads actual public locators.
const assertion = target => ({
  toHaveCount: async count => assert.equal(target.count, count),
  toHaveAttribute: async (name, value) => assert.equal(target.attributes?.[name], value),
  toHaveText: async value => assert.equal(target.text, value),
});
const owner = card => ({ page: () => ({ getByText: () => ({}) }), locator: () => ({ filter: () => card }) });
const card = number => ({ count: 1, locator: () => number });
await expectRenderedStat(owner(card({ attributes: { 'aria-label': '17' }, text: '17' })), 'Synthetic count', 17, assertion); positive++;
for (const candidate of [
  { count: 1, locator: () => ({ attributes: {}, text: '' }) }, // shell/skeleton with no rendered value
  card({ attributes: { 'aria-label': '0' }, text: '0' }), // stale counter
  card({ attributes: { 'aria-label': '17' }, text: '0' }), // unfinished visible animation
]) {
  await assert.rejects(expectRenderedStat(owner(candidate), 'Synthetic count', 17, assertion)); rejected++;
}
console.log(JSON.stringify({ state: 'PASS', requiredCompanionSettlement: true, renderedValueSensitivity: true,
  positive, rejected, angularBrowserProof: false }));
