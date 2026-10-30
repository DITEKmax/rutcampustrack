/** Closed failure predicates only: no browser, product, network, or private payload. */
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const { requireOrImport } = require(
  join(dirname(require.resolve('playwright/package.json')), 'lib/transform/transform.js'),
);
const { failedCases, retainFirstFailure, safeFirstFailureObservations, failureBoundary } =
  await requireOrImport(join(dirname(fileURLToPath(import.meta.url)), 'failed-case-diagnostics.ts'));

const cases = [
  ['FULL_ADMIN_RIGHTS', 'ADMIN_ACTIVITY', {
    httpStatus: 200, jsonDecoded: true, activityReady: true, periodMatches: true,
    bucketMatches: true, metricStateKnown: true, geoCountsKnown: true,
    metricStateMatches: true, metricValueMatches: true, metricFractionMatches: true,
    metricDeltaMatches: false, selectedPeriodMatches: true,
  }],
  ['HOMEWORK_PERIOD', 'HOMEWORK_MONTH', {
    httpStatus: 200, selectedDatePresent: true, fromContainsSelected: false,
    toContainsSelected: true, fromInsideSemester: true, toInsideSemester: true,
    semesterIdMatches: true, calendarReady: true, selectedDateEnabled: true,
  }],
  ['HEADMAN_TEACHER_READ', 'TEACHER_SHORTCUT', {
    requestObserved: true, requestFailed: false, httpStatus: null,
    semesterMatches: true, groupMatches: true, subjectMatches: false,
    lessonTypeMatches: true, pastOnlyMatches: true, pageMatches: true, pageSizeMatches: true,
    responseObserved: false, journalVisible: true, shortcutVisible: false,
    clickStarted: true, clickCompleted: false, clickPending: false, clickErrorClass: 'TIMEOUT', clickLogReason: 'UNKNOWN', clickRawRetention: 'OFF',
    clickSettledAfterFailure: true, clickCompletedAfterFailure: false,
    beforeCount: 1, beforeVisible: true, beforeEnabled: true, beforeReceivesEvents: false,
    afterCount: null, afterVisible: null, afterEnabled: null, afterReceivesEvents: null,
    homeVisible: false, navigationDetail: true, activeTeacherRole: true,
  }],
  ['SEMESTER_METADATA', 'SEMESTER_METADATA_ISOLATION', {
    semesterIdEqual: true, digestEqual: false, stateVersionEqual: true, priorStateEqual: true,
    countsEqual: true, scheduleTemplatesEqual: true, oneOffLessonsEqual: true,
    lessonsEqual: true, assignmentsEqual: true, homeworksEqual: true,
    attendanceMarksEqual: true, studentRequestsEqual: true,
  }],
];
let accepted = 0;
let rejected = 0;
for (const [caseId, stage, facts] of cases) {
  const info = { title: failedCases[caseId].title, annotations: [] };
  retainFirstFailure(info, stage, facts);
  assert.deepEqual(safeFirstFailureObservations(info.annotations), [{ caseId, stage, facts }]);
  retainFirstFailure(info, stage, { ...facts, arbitraryPayload: 'synthetic-sensitive-value' });
  assert.equal(info.annotations.length, 1, 'Only the first closed observation is retained');
  accepted++;
  const firstKey = Object.keys(facts).find(key => key !== 'httpStatus');
  const invalid = [
    { ...facts, [firstKey]: 'synthetic-sensitive-value' },
    { ...facts, arbitraryPayload: 'synthetic-sensitive-value' },
    { ...facts, [firstKey]: ['synthetic-sensitive-value'] },
    Object.fromEntries(Object.entries(facts).filter(([key]) => key !== firstKey)),
    Object.defineProperty({ ...facts }, firstKey, { enumerable: true, get() { throw new Error('must not read'); } }),
  ];
  for (const values of invalid) {
    const badInfo = { title: info.title, annotations: [] };
    retainFirstFailure(badInfo, stage, values);
    assert.deepEqual(badInfo.annotations, []);
    rejected++;
  }
}
for (const annotations of [
  [{ type: 'rct-first-failure-observation-v1', description: 'synthetic-sensitive-value' }],
  [{ type: 'rct-first-failure-observation-v1', description: JSON.stringify({ caseId: 'HOMEWORK_PERIOD', stage: 'UNKNOWN', facts: {} }) }],
  [{ type: 'rct-first-failure-observation-v1', description: 'x'.repeat(2049) }],
  Object.defineProperty([], '0', { get() { throw new Error('must not read'); } }),
]) {
  assert.deepEqual(safeFirstFailureObservations(annotations), []);
  rejected++;
}
const [caseId, stage, facts] = cases[1];
const primary = new Error('synthetic primary identity');
const info = { title: failedCases[caseId].title, annotations: [] };
await assert.rejects(
  failureBoundary(info, stage, () => Promise.reject(primary), () => facts),
  error => error === primary,
);
assert.equal(safeFirstFailureObservations(info.annotations)[0].facts.fromContainsSelected, false);
assert.equal(JSON.stringify(info.annotations).includes('synthetic primary identity'), false);
console.log(JSON.stringify({ state: 'PASS', accepted, rejected, primaryErrorIdentityRetained: true, privateDataPublished: false, browserServerNetwork: false }));
