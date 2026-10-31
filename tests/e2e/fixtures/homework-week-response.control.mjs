/** Exercise the actual week observer against a late month response; no browser or server. */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';

const source = readFileSync(new URL('../specs/mobile-homework-lifecycle.spec.ts', import.meta.url), 'utf8');
const fixedStart = source.indexOf('    const weekFacts =');
const start = fixedStart >= 0 ? fixedStart : source.indexOf('    const weekRead =');
const end = source.indexOf("\n    await expect(page.getByRole('tab', { name: 'Неделя'", start);
assert.ok(start > 0 && end > start, 'Use the current source week reader');
const reader = stripTypeScriptTypes(source.slice(start, end), { mode: 'transform' });
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
const shifted = (date, days) => new Date(Date.parse(`${date}T12:00:00Z`) + days * 86400000).toISOString().slice(0, 10);

async function run(changes = {}) {
  const dataset = {
    today: changes.today ?? '2026-10-14',
    semester: { id: 17, dateFrom: changes.semesterFrom ?? '2026-10-01', dateTo: changes.semesterTo ?? '2026-11-20' },
  };
  // Expected requests are independent fixed calendar examples, not the product range function.
  const from = changes.requestFrom ?? '2026-10-12', to = changes.requestTo ?? '2026-10-18';
  const feed = { from: changes.from ?? from, to: changes.to ?? to, semester: { id: changes.semesterId ?? '17' } };
  const facts = [];
  let predicate, resolve, reject, selected, clicked = false, ignored = 0;
  const response = (requestFrom, requestTo, body = feed, method = 'GET', path = '/api/v1/student/homework') => ({
    url: () => `https://control.invalid${path}?from=${requestFrom}&to=${requestTo}`,
    request: () => ({ method: () => method }),
    status: () => changes.status ?? 200,
    async json() { if (changes.malformed) throw new SyntaxError('synthetic JSON'); return body; },
  });
  const page = {
    waitForResponse(filter, ...options) {
      assert.equal(options.length, 0, 'Keep the original deadline');
      predicate = filter;
      return new Promise((ok, fail) => { resolve = ok; reject = fail; });
    },
    getByRole(role, options) {
      assert.equal(role, 'tab'); assert.deepEqual(options, { name: 'Неделя', exact: true });
      return { async click() {
        assert.equal(typeof predicate, 'function', 'Observe before the UI action'); clicked = true;
        const candidates = changes.lateMonth ? [
          response('2026-10-01', '2026-10-31', { from: '2026-10-01', to: '2026-10-31', semester: { id: '17' } }),
          response(from, to, feed, 'POST'),
          response(from, to, feed, 'GET', '/api/v1/student/schedule'),
          response(from, to),
        ] : [response(from, to)];
        if (changes.laterGood) candidates.push(response(from, to, { from, to, semester: { id: '17' } }));
        for (const candidate of candidates) {
          if (predicate(candidate)) { selected = candidate; resolve(candidate); return; }
          ignored++;
        }
        // Deterministic equivalent of no matching response before the existing deadline.
        reject(new Error('synthetic observer deadline'));
      } };
    },
  };
  const expect = value => ({ toBe(expected) { assert.equal(value, expected); } });
  try {
    await new AsyncFunction('page', 'dataset', 'expect', 'shifted', 'retainFirstFailure', 'info', reader)(
      page, dataset, expect, shifted, (_info, stage, observed) => facts.push({ stage, facts: { ...observed } }), {},
    );
    return { passed: true, facts, ignored, clicked, selected };
  } catch (error) { return { passed: false, facts, ignored, clicked, error }; }
}

assert.equal((await run()).passed, true);
const delayed = await run({ lateMonth: true });
assert.equal(delayed.passed, true, 'A late month response must not replace the week response');
assert.equal(delayed.ignored, 3);
assert.equal(delayed.clicked, true);
assert.equal((await run({ today: '2026-10-01', requestFrom: '2026-10-01', requestTo: '2026-10-04' })).passed, true);
assert.equal((await run({ today: '2026-11-20', requestFrom: '2026-11-16', requestTo: '2026-11-20' })).passed, true);
const negatives = [
  { from: '2026-10-15' }, { to: '2026-10-13' },
  { from: '2026-09-30' }, { to: '2026-11-21' },
  { from: '2026-10-11', to: '2026-10-18' },
  { from: '2026-10-13', to: '2026-10-17' },
  { from: '2026-10-01', to: '2026-10-31', laterGood: true },
  { semesterId: '18' }, { status: 503 }, { malformed: true },
  { requestFrom: '2026-10-01', requestTo: '2026-10-31' },
  { requestFrom: '2026-10-05', requestTo: '2026-10-11' },
];
for (const changes of negatives) {
  const result = await run(changes);
  assert.equal(result.passed, false, 'A wrong request, body or status must still fail');
  assert.equal(result.facts[0]?.stage, 'HOMEWORK_WEEK');
  assert.equal(Object.values(result.facts[0].facts).every(value => value === null || typeof value === 'boolean' || value === 200 || value === 503), true);
}
console.log(JSON.stringify({ state: 'PASS', lateMonthRejected: true, boundaryWeeksAccepted: 2, negativesRejected: negatives.length, browserServerNetwork: false }));
