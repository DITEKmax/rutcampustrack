/** Shared activity observer/oracle controls. No browser, server, network, or mounted DOM. */
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect as primitiveExpect, errors as playwrightErrors } from '@playwright/test';

const require = createRequire(import.meta.url);
const { requireOrImport } = require(
  join(dirname(require.resolve('playwright/package.json')), 'lib/transform/transform.js'),
);
const { completedActivity, assertActivity } = await requireOrImport(
  join(dirname(fileURLToPath(import.meta.url)), 'admin-activity.ts'),
);
const { safeFirstFailureObservations } = await requireOrImport(
  join(dirname(fileURLToPath(import.meta.url)), 'failed-case-diagnostics.ts'),
);
// Counterfactual header observer retains the old failure mode independently of source spelling.
const headerActivity = (page, period) => page.waitForResponse((response) => {
  const url = new URL(response.url());
  return url.pathname === '/api/academic/dashboard/activity' &&
    url.searchParams.get('period') === period && url.searchParams.get('bucket') === 'day' &&
    response.request().method() === 'GET';
});
function factory(observe) {
  return (expect, page, info) => {
    const first = observe(page, 'today');
    return async () => {
      await assertActivity(page, await first, 'today', expect, info);
      for (const period of ['all_time', 'today']) {
        const next = observe(page, period);
        await page.getByRole('button', { name: period === 'today' ? 'Сегодня' : 'Всё время', exact: true }).click();
        await assertActivity(page, await next, period, expect, info);
      }
    };
  };
}
const fixedFactory = factory(completedActivity);
const baselineFactory = factory(headerActivity);
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
      if (value.kind === 'activity' && name === 'aria-busy') actual = page.ui.busy ? 'true' : 'false';
      else if (value.kind === 'row' && name === 'data-state') actual = page.ui.state;
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
      if (selector === '.admin-dashboard-screen__activity') return locator('activity');
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
    const reply = {
      controlId: request.controlId,
      status: () => changes.status ?? 200,
      url: request.url,
      request: () => request,
      async json() {
        if (changes.aborted) {
          observations.abortedBodyReads++;
          throw new Error('Controlled cancelled response body');
        }
        return JSON.parse(changes.malformed ? '{' : JSON.stringify(changes.payload ?? payload(period)));
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
    if (options.activityBusy) page.ui.busy = true;
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
  const info = {
    title: 'FULL multirole ADMIN assigns updates and revokes LIMITED rights through Vue without foreign-role calls @cloud-accepted',
    annotations: [],
  };
  const check = factory((value, message) => modelExpect(control.page, value, message), control.page, info);
  const completion = check();
  // Attach rejection handling before delivering the deterministic lifecycle.
  const outcome = completion.then(() => ({ passed: true }), (error) => ({ passed: false, error }));
  control.emitInitial();
  if (options.noReplacement) control.expireOriginalWaiter();
  return { ...await outcome, observations: control.observations, failureFacts: safeFirstFailureObservations(info.annotations) };
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
  { activityBusy: true }, { fractionMismatch: true }, { deltaMismatch: true }, { stateMismatch: true }, { buttonMismatch: true },
]) {
  const result = await run(fixedFactory, { supersedePhase: 0, ...changes });
  assert.equal(result.passed, false, 'A completed response must still satisfy the original full activity oracle');
  assert.equal(result.observations.abortedBodyReads, 0);
  assert.equal(result.failureFacts.length, 1, 'Actual failed assertions must retain closed predicates');
  assert.equal(result.failureFacts[0].stage, 'ADMIN_ACTIVITY');
  negativeRejected++;
}
const unavailable = await run(fixedFactory, { supersedePhase: 0, noReplacement: true });
assert.equal(unavailable.passed, false);
assert.ok(unavailable.error instanceof playwrightErrors.TimeoutError);
assert.equal(unavailable.observations.abortedBodyReads, 0);
console.log(JSON.stringify({
  state: 'PASS', baselineStable: 1, baselineRed, fixedGreen, negativeRejected,
  noCompletedReplacementRetainsOriginalDeadline: true,
  sharedObserverAndOracle: true,
  browserServerNetworkOrMountedDom: false, historicalCause: 'UNKNOWN',
}));
