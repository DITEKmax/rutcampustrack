/** Preserve exact shortcut target/authority query predicates without product or browser execution. */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';

const source = readFileSync(new URL('../specs/mobile-real-server.spec.ts', import.meta.url), 'utf8');
const start = source.indexOf('const shortcutQueryMatches = ');
const end = source.indexOf('\n      };', start);
assert.ok(start > 0 && end > start, 'Use the current exact query predicate');
const declaration = stripTypeScriptTypes(source.slice(start, end + 9), { mode: 'transform' });
const facts = {};
const match = new Function('dataset', 'shortcutSubjectId', 'shortcutFacts', `${declaration}\nreturn shortcutQueryMatches;`)(
  { semester: { id: 17 }, group: { id: 23 } }, 31, facts,
);
const query = { semesterId: '17', groupId: '23', subjectId: '31', lessonType: 'LECTURE', pastOnly: 'true', page: '0', pageSize: '100' };
const makeUrl = values => new URL(`https://control.invalid/api/v1/teacher/journal?${new URLSearchParams(values)}`);
assert.equal(match(makeUrl(query)), true);
assert.equal(match(makeUrl({ ...query, lessonType: 'lecture' })), true, 'Existing case-insensitive lesson type contract');
let negatives = 0;
for (const [key, field] of Object.entries({
  semesterId: 'semesterMatches', groupId: 'groupMatches', subjectId: 'subjectMatches',
  lessonType: 'lessonTypeMatches', pastOnly: 'pastOnlyMatches', page: 'pageMatches', pageSize: 'pageSizeMatches',
})) {
  assert.equal(match(makeUrl({ ...query, [key]: 'wrong' })), false);
  assert.equal(facts[field], false);
  const absent = { ...query }; delete absent[key];
  assert.equal(match(makeUrl(absent)), false);
  assert.equal(facts[field], false);
  negatives += 2;
}
const duplicate = makeUrl(query);
duplicate.searchParams.append('lessonType', 'LECTURE');
assert.equal(match(duplicate), false);
assert.equal(Object.values(facts).every(value => typeof value === 'boolean'), true);
console.log(JSON.stringify({ state: 'PASS', equivalentValidQueries: 2, negativesRejected: negatives + 1, exactTargetAndQueryRetained: true, browserServerNetwork: false, historicalCause: 'UNKNOWN' }));

// Execute the current bounded diagnostic implementation with no browser or product.
const diagnosticsSource = readFileSync(new URL('./failed-case-diagnostics.ts', import.meta.url), 'utf8');
const diagnostics = await import(`data:text/javascript;base64,${Buffer.from(stripTypeScriptTypes(diagnosticsSource, { mode: 'transform' })).toString('base64')}`);
const { failedCases, handledWaiter, retainFirstFailure, safeFirstFailureObservations,
  teacherShortcutClickErrorClass, teacherShortcutClickLogReason, teacherShortcutState } = diagnostics;
const makeFacts = () => ({
  requestObserved: false, requestFailed: false, httpStatus: null,
  semesterMatches: null, groupMatches: null, subjectMatches: null, lessonTypeMatches: null,
  pastOnlyMatches: null, pageMatches: null, pageSizeMatches: null, responseObserved: false,
  journalVisible: null, shortcutVisible: null, clickStarted: false, clickCompleted: false,
  clickPending: false, clickErrorClass: 'NONE', clickLogReason: 'NONE', clickRawRetention: 'NOT_ATTEMPTED', clickSettledAfterFailure: null, clickCompletedAfterFailure: null,
  beforeCount: 1, beforeVisible: true, beforeEnabled: true, beforeReceivesEvents: true,
  afterCount: null, afterVisible: null, afterEnabled: null, afterReceivesEvents: null,
  homeVisible: null, navigationDetail: null, activeTeacherRole: null,
});
const makeInfo = () => ({ title: failedCases.HEADMAN_TEACHER_READ.title, annotations: [] });
let closedNegatives = 0;
for (const key of Object.keys(makeFacts())) {
  const info = makeInfo();
  retainFirstFailure(info, 'TEACHER_SHORTCUT', { ...makeFacts(), [key]: 'canary-private-url-identity-exception' });
  assert.deepEqual(info.annotations, [], key);
  closedNegatives++;
}
for (const bad of [{ rawURL: 'canary-private-url' }, { rawHTML: '<b>canary-private</b>' }, { exception: new Error('canary-private') }]) {
  const info = makeInfo();
  retainFirstFailure(info, 'TEACHER_SHORTCUT', { ...makeFacts(), ...bad });
  assert.deepEqual(info.annotations, []);
  closedNegatives++;
}
const allClasses = ['NONE', 'TIMEOUT', 'ERROR', 'TYPE_ERROR', 'UNKNOWN'];
for (const clickErrorClass of allClasses) {
  const info = makeInfo();
  retainFirstFailure(info, 'TEACHER_SHORTCUT', { ...makeFacts(), clickErrorClass });
  assert.equal(safeFirstFailureObservations(info.annotations)[0]?.facts.clickErrorClass, clickErrorClass);
}
for (const count of [null, 0, 10000]) {
  const info = makeInfo();
  retainFirstFailure(info, 'TEACHER_SHORTCUT', { ...makeFacts(), beforeCount: count, afterCount: count });
  assert.equal(safeFirstFailureObservations(info.annotations).length, 1);
}
for (const count of [-1, 10001, 1.1, Infinity]) {
  const info = makeInfo();
  retainFirstFailure(info, 'TEACHER_SHORTCUT', { ...makeFacts(), beforeCount: count });
  assert.deepEqual(info.annotations, []);
  closedNegatives++;
}
const timeout = Object.assign(new Error('canary-private-exception\nCall log:\n  - <div>canary-private-overlay</div> intercepts pointer events'), { name: 'TimeoutError' });
assert.equal(teacherShortcutClickErrorClass(timeout), 'TIMEOUT');
assert.equal(teacherShortcutClickErrorClass(new TypeError('canary-private-exception')), 'TYPE_ERROR');
assert.equal(teacherShortcutClickErrorClass(new Error('canary-private-exception')), 'ERROR');
assert.equal(teacherShortcutClickErrorClass(Object.defineProperty({}, 'name', { get() { throw new Error('must not read'); } })), 'UNKNOWN');

const blockStart = source.indexOf('      let shortcutResponse: Response;');
const blockEnd = source.indexOf('      expect(shortcutResponse.status()).toBe(200);', blockStart);
assert.ok(blockStart > 0 && blockEnd > blockStart);
const actionSource = stripTypeScriptTypes(source.slice(blockStart, blockEnd), { mode: 'transform' });
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
const attempt = new AsyncFunction('page', 'shortcut', 'shortcutFacts', 'shortcutQueryMatches', 'handledWaiter',
  'teacherShortcutClickErrorClass', 'teacherShortcutClickLogReason', 'retainTeacherShortcutClickError', 'teacherShortcutState', 'retainFirstFailure', 'info',
  'observeShortcutRequest', 'observeShortcutFailure', `${actionSource}\nreturn shortcutResponse;`);
const deferred = () => { let resolve, reject; const promise = new Promise((ok, fail) => { resolve = ok; reject = fail; }); return { promise, resolve, reject }; };
const after = { count: 1, visible: true, enabled: true, receivesEvents: false,
  journalVisible: false, shortcutVisible: true, homeVisible: true, navigationDetail: false, activeTeacherRole: true };
const captured = [];
let retention = () => 'OFF';
const capture = (info, error) => { captured.push(error); return retention(info, error); };
const runAttempt = (response, click, info, facts, observe = async () => after) => {
  const order = [];
  const page = { waitForResponse() { order.push('wait'); return response; }, off() {} };
  const shortcut = { click(options) { assert.deepEqual(options, { timeout: 10_000 }); order.push('click'); return click; } };
  const result = attempt(page, shortcut, facts, () => true, handledWaiter, teacherShortcutClickErrorClass,
    teacherShortcutClickLogReason, capture, observe, retainFirstFailure, info, () => {}, () => {});
  assert.deepEqual(order, ['wait', 'click']);
  return result;
};
const unhandled = [];
const onUnhandled = error => unhandled.push(error);
process.on('unhandledRejection', onUnhandled);
try {
  // Response failure first: capture pending at boundary, settle only that original click before returning.
  const click = deferred(), response = deferred(), primary = new Error('canary-private-primary');
  const facts = makeFacts(), info = makeInfo();
  let returned = false;
  const result = runAttempt(response.promise, click.promise, info, facts);
  result.finally(() => { returned = true; }).catch(() => {});
  response.reject(primary);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(returned, false);
  click.resolve();
  await assert.rejects(result, error => error === primary);
  const observed = safeFirstFailureObservations(info.annotations)[0].facts;
  assert.equal(observed.clickPending, true);
  assert.equal(observed.clickCompleted, false);
  assert.equal(observed.clickSettledAfterFailure, true);
  assert.equal(observed.clickCompletedAfterFailure, true);
  assert.equal(observed.afterReceivesEvents, false);
  assert.equal(observed.homeVisible, true);
  assert.equal(observed.activeTeacherRole, true);
  assert.equal(JSON.stringify(info.annotations).includes('canary-private'), false);

  // Click rejection first retains that primary; the later response rejection is already handled.
  const laterResponse = deferred(), clickFailure = new TypeError('canary-private-click');
  const clickInfo = makeInfo();
  await assert.rejects(runAttempt(laterResponse.promise, Promise.reject(clickFailure), clickInfo, makeFacts()), error => error === clickFailure);
  laterResponse.reject(new Error('canary-private-late-response'));
  const clickObserved = safeFirstFailureObservations(clickInfo.annotations)[0].facts;
  assert.equal(clickObserved.clickPending, false);
  assert.equal(clickObserved.clickErrorClass, 'TYPE_ERROR');
  assert.equal(clickObserved.clickCompletedAfterFailure, false);

  // A response-first failure followed by click timeout retains both closed outcomes and original response identity.
  const timedClick = deferred(), timedResponse = deferred(), responsePrimary = new Error('canary-private-response');
  const timedInfo = makeInfo();
  const timedResult = runAttempt(timedResponse.promise, timedClick.promise, timedInfo, makeFacts());
  timedResponse.reject(responsePrimary);
  await new Promise(resolve => setImmediate(resolve));
  timedClick.reject(timeout);
  await assert.rejects(timedResult, error => error === responsePrimary);
  const timedObserved = safeFirstFailureObservations(timedInfo.annotations)[0].facts;
  assert.equal(timedObserved.clickPending, true);
  assert.equal(timedObserved.clickErrorClass, 'TIMEOUT');
  assert.equal(timedObserved.clickLogReason, 'POINTER_INTERCEPTION_SEEN');
  assert.equal(timedObserved.clickRawRetention, 'OFF');
  assert.equal(captured.at(-1), timeout, 'The actual secondary Error reaches the existing private sink');
  assert.equal(JSON.stringify(timedInfo.annotations).includes('canary-private'), false);
  assert.equal(timedObserved.clickCompletedAfterFailure, false);

  retention = () => 'FAILED';
  const failedRetentionResponse = new Error('canary-private-retention-primary');
  const failedRetentionInfo = makeInfo();
  await assert.rejects(runAttempt(Promise.reject(failedRetentionResponse), Promise.reject(timeout), failedRetentionInfo, makeFacts()), error => error === failedRetentionResponse);
  assert.equal(safeFirstFailureObservations(failedRetentionInfo.annotations)[0].facts.clickRawRetention, 'FAILED');
  retention = () => 'OFF';

  // Primary success still returns the exact response and publishes no failure observation.
  const successInfo = makeInfo(), successResponse = { status: () => 200 };
  assert.equal(await runAttempt(Promise.resolve(successResponse), Promise.resolve(), successInfo, makeFacts()), successResponse);
  assert.deepEqual(successInfo.annotations, []);

  // Real read-only diagnostic helper returns unavailable after its single 300ms budget.
  const diagnostic = deferred();
  const started = performance.now();
  const unavailable = await teacherShortcutState({ locator: () => ({ isVisible: () => diagnostic.promise }) },
    { evaluateAll: () => diagnostic.promise, isVisible: () => diagnostic.promise });
  assert.equal(unavailable.count, null);
  assert.equal(unavailable.navigationDetail, null);
  assert.ok(performance.now() - started >= 250 && performance.now() - started < 1500);
  diagnostic.reject(new Error('canary-private-late-diagnostic'));
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(unhandled, []);
} finally { process.off('unhandledRejection', onUnhandled); }
console.log(JSON.stringify({ state: 'PASS', closedNegatives, finiteErrorClasses: allClasses.length,
  primaryIdentityPreserved: true, pendingClickSettledBeforeReturn: true, pendingAtPrimaryRetained: true,
  responseRejectionHandled: true, diagnosticBudgetMs: 300, actionBudgetMs: 10000,
  rawSensitiveFactsPublished: false, browserServerNetwork: false }));

const reasons = ['POINTER_INTERCEPTION_SEEN', 'NOT_VISIBLE_SEEN', 'NOT_ENABLED_SEEN', 'NOT_STABLE_SEEN', 'DETACHED_SEEN', 'MULTIPLE_REASONS_SEEN', 'UNKNOWN'];
for (const [text, expected] of [
  ['<div>canary-private</div> intercepts pointer events', 'POINTER_INTERCEPTION_SEEN'],
  ['element is not visible', 'NOT_VISIBLE_SEEN'], ['element is not enabled', 'NOT_ENABLED_SEEN'],
  ['element is not stable', 'NOT_STABLE_SEEN'], ['element is not attached to the DOM', 'DETACHED_SEEN'],
  ['element was detached from the DOM', 'DETACHED_SEEN'],
  ['element is not stable\n  - element is not visible', 'MULTIPLE_REASONS_SEEN'], ['waiting for locator', 'UNKNOWN'],
]) assert.equal(teacherShortcutClickLogReason(new Error(`canary-private\nCall log:\n  - ${text}`)), expected);
for (const error of [new Error('intercepts pointer events'), new Error('x'.repeat(8193)),
  Object.defineProperty({}, 'message', { get() { throw new Error('must not read'); } }),
  Object.create({ message: '\nCall log:\n  - element is not visible' }),
  new Error('\nCall log:\ncanary-private intercepts pointer events')])
  assert.equal(teacherShortcutClickLogReason(error), 'UNKNOWN');
for (const clickLogReason of ['NONE', ...reasons]) {
  const info = makeInfo();
  retainFirstFailure(info, 'TEACHER_SHORTCUT', { ...makeFacts(), clickLogReason });
  assert.equal(safeFirstFailureObservations(info.annotations)[0]?.facts.clickLogReason, clickLogReason);
}
for (const clickRawRetention of ['NOT_ATTEMPTED', 'OFF', 'RETAINED', 'ALREADY_RETAINED', 'REJECTED', 'FAILED']) {
  const info = makeInfo();
  retainFirstFailure(info, 'TEACHER_SHORTCUT', { ...makeFacts(), clickRawRetention });
  assert.equal(safeFirstFailureObservations(info.annotations)[0]?.facts.clickRawRetention, clickRawRetention);
}
console.log(JSON.stringify({ state: 'PASS', secondaryActualErrorCaptured: true, retentionFailurePreservesPrimary: true,
  callLogReasonOnly: true, closedReasons: reasons.length + 1, closedRetentionStatuses: 6, rawSensitiveFactsPublished: false }));
