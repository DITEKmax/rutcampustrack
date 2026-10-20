/** Source-bound activity observer/oracle controls. No browser, server, network, or mounted DOM. */
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createRequire, stripTypeScriptTypes } from 'node:module';
import { dirname, join } from 'node:path';
import { expect as primitiveExpect, errors as playwrightErrors } from '@playwright/test';

const require = createRequire(import.meta.url);
const { Response: ObservedResponse } = require(
  join(dirname(require.resolve('playwright-core/package.json')), 'lib/client/network.js'),
);
const source = readFileSync(new URL('../specs/cloud-accepted-admin-grants.spec.ts', import.meta.url), 'utf8');
const sha256 = (text) => createHash('sha256').update(text).digest('hex');
function between(text, start, end) {
  assert.equal(text.split(start).length, 2, 'Control source boundary must be unique');
  const offset = text.indexOf(start) + start.length;
  const stop = text.indexOf(end, offset);
  assert.ok(stop > offset, 'Control source end must follow its start');
  return text.slice(offset, stop);
}
const initialStart = '    // ADMIN22: arm the genuine initial activity read before entering the dashboard.\n';
const initialEnd = '    // End ADMIN22 initial read.\n';
const oracleStart = '    const assertActivity = async ';
const sequenceStart = "    await assertActivity(await activityReply, 'today');\n";
const sequenceEnd = '    // End ADMIN22 activity witness.\n';
const initial = between(source, initialStart, initialEnd);
const oracle = oracleStart + between(source, oracleStart, sequenceStart);
const sequence = sequenceStart + between(source, sequenceStart, sequenceEnd);
assert.equal(sha256(oracle), '73b7efb17f8bd5c901cf788494ed815c900ed7200ab479311212d1a19c10dc36');

// Only reverse the observer correction in memory. The full digest authenticates
// these two fragments as the exact 08fd baseline, including the unchanged oracle.
const baselineInitial = `    const activityReply = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname === '/api/academic/dashboard/activity' &&
        url.searchParams.get('period') === 'today' &&
        url.searchParams.get('bucket') === 'day' &&
        response.request().method() === 'GET'
      );
    });
`;
const fixedNext = '      const next = completedActivity(period);\n';
const baselineNext = `      const next = page.waitForResponse((response) => {
        const url = new URL(response.url());
        return (
          url.pathname === '/api/academic/dashboard/activity' &&
          url.searchParams.get('period') === period &&
          url.searchParams.get('bucket') === 'day' &&
          response.request().method() === 'GET'
        );
      });
`;
assert.equal(source.split(fixedNext).length, 2);
const baseline = source.replace(initial, baselineInitial).replace(fixedNext, baselineNext);
assert.equal(sha256(baseline), '102e6cf080f9c6d932e76d92e836b4c604bf0ca29c119ca46b023c82d1c2d642');
function factory(text) {
  const actualInitial = between(text, initialStart, initialEnd);
  const actualOracle = oracleStart + between(text, oracleStart, sequenceStart);
  const actualSequence = sequenceStart + between(text, sequenceStart, sequenceEnd);
  const compiled = stripTypeScriptTypes(`function create(expect, page) {
${actualInitial}
${actualOracle}
return async function check() {
${actualSequence}
};
}`);
  return new Function(`return ${compiled};`)();
}
const fixedFactory = factory(source);
const baselineFactory = factory(baseline);
const periods = ['today', 'all_time', 'today'];
const metricCodes = ['positiveMarks', 'geoMarks', 'mapOpens', 'requests', 'homeworkPublications'];
function payload(period) {
  const values = [11, 3, 7, 0, 4];
  const deltas = [2, null, -1, null, 1];
  return {
    period, bucket: 'day',
    summary: {
      metrics: Object.fromEntries(metricCodes.map((code, index) => [code, {
        state: 'AVAILABLE', value: values[index],
        percent: code === 'geoMarks' ? 50 : null,
        numerator: code === 'geoMarks' ? 3 : null,
        denominator: code === 'geoMarks' ? 6 : null,
        comparison: code === 'requests' ? null : {
          state: 'AVAILABLE', absoluteDelta: deltas[index],
          percentPointDelta: code === 'geoMarks' ? 5 : null,
        },
      }])),
    },
  };
}
function ui(period) {
  return {
    period,
    values: { positiveMarks: '11', geoMarks: '50%', mapOpens: '7', requests: '0', homeworkPublications: '4' },
    deltas: period === 'today'
      ? { positiveMarks: '+2', geoMarks: '+5%', mapOpens: '−1', requests: '—', homeworkPublications: '+1' }
      : Object.fromEntries(metricCodes.map((code) => [code, '—'])),
    fraction: '3 из 6', state: 'AVAILABLE', selected: true,
  };
}
function locator(kind, code, name) {
  return {
    controlLocator: true, kind, code, name,
    locator(selector) {
      assert.equal(kind, 'row');
      const selectors = { 'dd > span': 'value', 'dd > small': 'fraction', '.admin-dashboard-screen__delta': 'delta' };
      assert.ok(Object.hasOwn(selectors, selector), 'Unmodelled activity locator');
      return locator(selectors[selector], code);
    },
    filter(options) {
      assert.equal(kind, 'fraction');
      assert.deepEqual(options, { hasText: ' из ' });
      return this;
    },
  };
}
function modelExpect(page, value, message) {
  if (!value?.controlLocator) return primitiveExpect(value, message);
  return {
    async toHaveAttribute(name, expected) {
      let actual;
      if (value.kind === 'row' && name === 'data-state') actual = page.ui.state;
      else if (value.kind === 'delta' && name === 'aria-label') actual = 'относительно вчера';
      else if (value.kind === 'button' && name === 'aria-pressed') {
        actual = page.ui.selected && value.name === (page.ui.period === 'today' ? 'Сегодня' : 'Всё время') ? 'true' : 'false';
      } else assert.fail('Unmodelled activity attribute');
      if (expected instanceof RegExp) primitiveExpect(actual).toMatch(expected);
      else primitiveExpect(actual).toBe(expected);
    },
    async toHaveText(expected) {
      const actual = value.kind === 'value' ? page.ui.values[value.code]
        : value.kind === 'delta' ? page.ui.deltas[value.code]
          : value.kind === 'fraction' ? page.ui.fraction : undefined;
      assert.notEqual(actual, undefined, 'Unmodelled activity text');
      primitiveExpect(actual).toBe(expected);
    },
    async toHaveCount(expected) {
      assert.equal(value.kind, 'fraction');
      primitiveExpect(value.code === 'geoMarks' ? 1 : 0).toBe(expected);
    },
  };
}
function controlPage(options) {
  const waiters = [];
  const observations = { abortedBodyReads: 0, selectedCompleted: [], selectedHeaders: [], waitsWithoutOptions: true };
  const page = {
    ui: null,
    waitForResponse(predicate, ...extra) {
      assert.equal(extra.length, 0, 'Keep the original response deadline');
      return wait('response', predicate);
    },
    waitForEvent(event, predicate, ...extra) {
      assert.equal(event, 'requestfinished');
      assert.equal(extra.length, 0, 'Keep the original event deadline');
      return wait(event, predicate);
    },
    locator(selector) {
      const match = /^\[data-admin-activity-metric="([A-Za-z]+)"\]$/.exec(selector);
      assert.ok(match && metricCodes.includes(match[1]), 'Unmodelled metric row');
      return locator('row', match[1]);
    },
    getByRole(role, options) {
      assert.equal(role, 'button');
      assert.equal(options.exact, true);
      assert.ok(['Сегодня', 'Всё время'].includes(options.name));
      return {
        ...locator('button', undefined, options.name),
        async click() {
          const phase = page.ui.period === 'today' ? 1 : 2;
          assert.equal(options.name, phase === 1 ? 'Всё время' : 'Сегодня');
          emitPhase(phase);
        },
      };
    },
  };
  function wait(event, predicate) {
    return new Promise((resolve, reject) => waiters.push({ event, predicate, resolve, reject }));
  }
  function emit(event, candidate) {
    for (const waiter of [...waiters]) {
      if (waiter.event !== event || !waiter.predicate(candidate)) continue;
      waiters.splice(waiters.indexOf(waiter), 1);
      observations[event === 'response' ? 'selectedHeaders' : 'selectedCompleted'].push(candidate.controlId);
      waiter.resolve(candidate);
    }
  }
  function response(phase, label, changes = {}) {
    const period = periods[phase];
    const request = {
      controlId: `${phase}-${label}`,
      url: () => `https://control.invalid/api/academic/dashboard/activity?period=${period}&bucket=day`,
      method: () => 'GET',
      response: async () => reply,
    };
    const reply = Object.create(ObservedResponse.prototype);
    reply.controlId = request.controlId;
    reply._initializer = { status: changes.status ?? 200, url: request.url() };
    reply._request = request;
    reply._channel = {
      async body() {
        if (changes.aborted) {
          observations.abortedBodyReads++;
          throw new Error('Controlled cancelled response body');
        }
        return { binary: Buffer.from(changes.malformed ? '{' : JSON.stringify(changes.payload ?? payload(period))) };
      },
    };
    return { request, reply };
  }
  function emitPhase(phase) {
    const period = periods[phase];
    page.ui = ui(period);
    if (options.metricMismatch) page.ui.values[options.metricMismatch] = 'wrong';
    if (options.fractionMismatch) page.ui.fraction = '2 из 6';
    if (options.deltaMismatch) page.ui.deltas.positiveMarks = '—';
    if (options.stateMismatch) page.ui.state = 'UNAVAILABLE';
    if (options.buttonMismatch) page.ui.selected = false;
    if (options.supersedePhase === phase) {
      const old = response(phase, 'aborted', { aborted: true });
      emit('response', old.reply);
      emit('requestfailed', old.request);
    }
    if (options.noReplacement) return;
    const body = payload(period);
    if (options.wrongPeriod) body.period = period === 'today' ? 'all_time' : 'today';
    if (options.wrongBucket) body.bucket = 'week';
    const current = response(phase, 'completed', { status: options.status, malformed: options.malformed, payload: body });
    emit('response', current.reply);
    emit('requestfinished', current.request);
  }
  return {
    page, observations, emitInitial: () => emitPhase(0),
    expireOriginalWaiter() {
      assert.ok(waiters.length > 0, 'No completed replacement must leave the original waiter pending');
      for (const waiter of waiters.splice(0)) waiter.reject(new playwrightErrors.TimeoutError('Controlled original waiter deadline'));
    },
  };
}
async function run(factory, options = {}) {
  const control = controlPage(options);
  const check = factory((value, message) => modelExpect(control.page, value, message), control.page);
  const completion = check();
  // Attach rejection handling before delivering the deterministic lifecycle.
  const outcome = completion.then(() => ({ passed: true }), (error) => ({ passed: false, error }));
  control.emitInitial();
  if (options.noReplacement) control.expireOriginalWaiter();
  return { ...await outcome, observations: control.observations };
}

assert.equal((await run(baselineFactory)).passed, true, 'Baseline must pass without supersession');
let baselineRed = 0;
let fixedGreen = 0;
for (const supersedePhase of [0, 1, 2]) {
  const red = await run(baselineFactory, { supersedePhase });
  assert.equal(red.passed, false);
  assert.equal(red.observations.abortedBodyReads, 1);
  baselineRed++;
  const green = await run(fixedFactory, { supersedePhase });
  assert.equal(green.passed, true);
  assert.equal(green.observations.abortedBodyReads, 0);
  assert.deepEqual(green.observations.selectedCompleted, ['0-completed', '1-completed', '2-completed']);
  fixedGreen++;
}
let negativeRejected = 0;
for (const changes of [
  { malformed: true }, { status: 403 }, { wrongPeriod: true }, { wrongBucket: true },
  ...metricCodes.map((metricMismatch) => ({ metricMismatch })),
  { fractionMismatch: true }, { deltaMismatch: true }, { stateMismatch: true }, { buttonMismatch: true },
]) {
  const result = await run(fixedFactory, { supersedePhase: 0, ...changes });
  assert.equal(result.passed, false, 'A completed response must still satisfy the original full activity oracle');
  assert.equal(result.observations.abortedBodyReads, 0);
  negativeRejected++;
}
const unavailable = await run(fixedFactory, { supersedePhase: 0, noReplacement: true });
assert.equal(unavailable.passed, false);
assert.ok(unavailable.error instanceof playwrightErrors.TimeoutError);
assert.equal(unavailable.observations.abortedBodyReads, 0);
console.log(JSON.stringify({
  state: 'PASS', baselineStable: 1, baselineRed, fixedGreen, negativeRejected,
  noCompletedReplacementRetainsOriginalDeadline: true,
  exactBaselineSha256: sha256(baseline), activityOracleSha256: sha256(oracle),
  sourceSha256: sha256(source), sourceBoundObserverAndOracle: true,
  browserServerNetworkOrMountedDom: false, historicalCause: 'UNKNOWN',
}));
