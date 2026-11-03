/** Exercise bounded diagnostic privacy with a synthetic retired-case identity. */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';

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

console.log(JSON.stringify({ state: 'PASS', closedNegatives, diagnosticPrivacyPreserved: true, browserServerNetwork: false }));
