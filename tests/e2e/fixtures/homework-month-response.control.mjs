/** Exercise the actual extracted month reader at its HTTP/UI boundary, without a browser. */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';

const source = readFileSync(new URL('../specs/mobile-homework-lifecycle.spec.ts', import.meta.url), 'utf8');
const start = source.indexOf('const readMonth = async ');
const end = source.indexOf('\n  };\n  if ((await monthTab', start);
assert.ok(start > 0 && end > start, 'Use the current source month reader');
const reader = stripTypeScriptTypes(source.slice(start, end + 5) + '\nreadMonth;', { mode: 'transform' });
const dataset = { semester: { id: 17, dateFrom: '2026-02-05', dateTo: '2026-06-20' } };
const shifted = (date, days) => new Date(Date.parse(`${date}T12:00:00Z`) + days * 86400000).toISOString().slice(0, 10);

async function run(changes = {}) {
  const feed = {
    from: changes.from ?? '2026-02-05', to: changes.to ?? '2026-02-28',
    semester: { id: changes.semesterId ?? '17' },
  };
  const model = { selected: null, calendarReady: false, enabled: false };
  const facts = [];
  const selectedDay = {
    kind: 'selected',
    async getAttribute(name) {
      assert.equal(name, 'data-homework-date');
      return model.selected;
    },
  };
  const calendar = {
    kind: 'calendar',
    locator(selector) { assert.equal(selector, 'button[aria-pressed="true"]'); return selectedDay; },
  };
  const page = {
    waitForResponse(predicate, ...options) {
      assert.equal(options.length, 0, 'Keep the original response deadline');
      const response = {
        status: () => changes.status ?? 200,
        request: () => ({ method: () => 'GET' }),
        url: () => `https://control.invalid/api/v1/student/homework?from=${changes.requestFrom ?? '2026-02-05'}&to=${changes.requestTo ?? '2026-02-28'}`,
        async json() { if (changes.malformed) throw new SyntaxError('synthetic JSON'); return feed; },
      };
      assert.equal(predicate(response), true, 'The response must be the clipped real month request');
      return Promise.resolve(response);
    },
    locator(selector) {
      if (selector === '.homework-calendar') return calendar;
      assert.equal(selector, '.homework-skeleton');
      return { kind: 'skeleton' };
    },
  };
  const expect = value => ({
    toBe(expected) { assert.equal(value, expected); },
    async toHaveAttribute(name, expected) {
      assert.equal(value.kind, 'calendar'); assert.equal(name, 'aria-busy'); assert.equal(expected, 'false');
      if (changes.busy) throw new Error('synthetic calendar remains busy');
      // The mounted UI publishes its new selected date when the accepted feed settles.
      model.calendarReady = true;
      model.enabled = !changes.disabled;
      model.selected = changes.absent ? null : changes.selected ?? '2026-02-10';
    },
    async toBeEnabled() { assert.equal(value.kind, 'selected'); assert.equal(model.enabled, true); },
    async toHaveCount(expected) { assert.equal(value.kind, 'skeleton'); assert.equal(expected, 0); },
  });
  const readMonth = new Function('page', 'dataset', 'expect', 'shifted', 'retainFirstFailure', 'test', `${reader.slice(0, reader.lastIndexOf('readMonth;'))}\nreturn readMonth;`)(
    page, dataset, expect, shifted, (_info, stage, observed) => facts.push({ stage, facts: observed }), { info: () => ({}) },
  );
  try { await readMonth(async () => {}); return { passed: true, facts }; }
  catch (error) { return { passed: false, facts, error }; }
}

assert.equal((await run()).passed, true);
assert.equal((await run({ requestFrom: '2026-06-01', requestTo: '2026-06-20', from: '2026-06-01', to: '2026-06-20', selected: '2026-06-20' })).passed, true);
const negatives = [
  { absent: true }, { from: '2026-02-11' }, { to: '2026-02-09' },
  { from: '2026-02-04' }, { to: '2026-06-21' }, { semesterId: '18' },
  { busy: true }, { disabled: true }, { status: 403 }, { malformed: true },
];
for (const changes of negatives) assert.equal((await run(changes)).passed, false, 'A wrong feed or unsettled calendar must still fail');
const isolated = await run({ semesterId: '18' });
assert.equal(isolated.facts[0].stage, 'HOMEWORK_MONTH');
assert.equal(isolated.facts[0].facts.semesterIdMatches, false);
assert.equal(Object.values(isolated.facts[0].facts).every(value => value === null || typeof value === 'boolean' || value === 200), true);
console.log(JSON.stringify({ state: 'PASS', waitedForCalendarPublication: true, boundaryMonthsAccepted: 2, negativesRejected: negatives.length, strictSemesterIdentityRetained: true, browserServerNetwork: false, historicalCause: 'UNKNOWN' }));
