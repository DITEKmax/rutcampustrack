import { localSpecialLane } from './local-special';
import { diagnosticCase } from './diagnostic-events';
import { randomUUID } from 'node:crypto';
import { existsSync, lstatSync, readFileSync, writeFileSync } from 'node:fs';
import { isAbsolute, join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { type APIRequestContext, type APIResponse, expect } from '@playwright/test';
import { test, requirePrivateArtifacts } from './artifact-privacy';
import { Actor, MobileDataset, admit, type SyntheticUser } from './mobile-dataset';
import { readTeachingInstant } from './teaching-clock';
import { datasetHttpFailure } from './safe-reporter';
import { TEST_USERS } from './users';

// CI owns the exact Schedule stop/start/readiness between these API-only phases.
const phase = process.env.RCT_ASSIGNMENT_LIFECYCLE_PHASE;
const phases = ['prepare', 'pending', 'resume'];
type Row = Record<string, any>;
interface Checkpoint {
  headman: SyntheticUser;
  former: SyntheticUser;
  adopted: SyntheticUser;
  replacement: SyntheticUser;
  foreign: SyntheticUser;
  groupId: number;
  subjectId: number;
  semesterId: number;
  studentId: number;
  sourceAssignmentId: number;
  adoptedEmployeeNumber: string;
  today: string;
  yesterday: string;
  futureDate: string;
  replacementEffectiveFrom: string;
  futureId: number;
  historyIds: number[];
  closeUrl: string;
  closeKey: string;
  history: Row[];
  currentPhysical: Row;
  currentMark: Row;
  marks: Row[];
  homeworks: Row[];
  future: Row;
  teacherStatsBaseline?: Row;
}

function requireValue(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}
function privateFile(path: string, maximum: number) {
  const stat = lstatSync(path);
  requireValue(
    stat.isFile() &&
      !stat.isSymbolicLink() &&
      (stat.mode & 0o777) === 0o600 &&
      stat.size <= maximum,
    'Assignment checkpoint must be a bounded private regular file',
  );
}
function save(run: string, name: string, value: unknown) {
  const bytes = JSON.stringify(value);
  requireValue(Buffer.byteLength(bytes) <= 256 * 1024, 'Assignment checkpoint exceeds its bound');
  writeFileSync(join(run, name), bytes, { flag: 'wx', mode: 0o600 });
}
function load<T>(run: string, name: string): T {
  const path = join(run, name);
  privateFile(path, 256 * 1024);
  return JSON.parse(readFileSync(path, 'utf8')) as T;
}
function rows(body: Row): Row[] {
  return Object.values(body._embedded ?? {}).flat() as Row[];
}
function projection(row: Row, keys: string[]): Row {
  return Object.fromEntries(keys.map((key) => [key, row[key] ?? null]));
}
const physicalFields = [
  'id',
  'occurrenceId',
  'groupId',
  'subjectId',
  'semesterId',
  'date',
  'lessonNumber',
  'status',
  'current',
  'assignmentId',
  'assignedTeacherId',
  'lessonType',
  'generation',
  'revision',
];
const markFields = ['userId', 'status', 'source', 'markRevision'];
const homeworkFields = [
  'id',
  'bindingId',
  'revision',
  'publishedBy',
  'title',
  'description',
  'subjectId',
  'groupId',
  'semesterId',
  'lessonDate',
  'lessonNumber',
  'bindingMode',
  'lessonType',
];
function shift(date: string, days: number) {
  return new Date(Date.parse(`${date}T12:00:00Z`) + days * 86400000).toISOString().slice(0, 10);
}
function studyDay(date: string, direction: number) {
  while ([0, 6].includes(new Date(`${date}T12:00:00Z`).getUTCDay())) date = shift(date, direction);
  return date;
}
function subjectPath(checkpoint: Pick<Checkpoint, 'subjectId'>) {
  return `/api/academic/subjects/${checkpoint.subjectId}`;
}
function journalPath(checkpoint: Checkpoint) {
  const query = new URLSearchParams({
    semesterId: String(checkpoint.semesterId),
    groupId: String(checkpoint.groupId),
    subjectId: String(checkpoint.subjectId),
    lessonType: 'LECTURE',
    dateFrom: checkpoint.yesterday,
    dateTo: checkpoint.today,
    pastOnly: 'true',
  });
  return `/api/v1/teacher/journal?${query}`;
}
async function physicalRows(headman: Actor, groupId: number, from: string, to: string) {
  return rows(
    await headman.call(
      'GET',
      `/api/schedule/groups/${groupId}/lessons?dateFrom=${from}&dateTo=${to}&size=200`,
    ),
  );
}
async function homework(headman: Actor, dataset: MobileDataset, date: string, number: number) {
  const command = {
    title: dataset.marker,
    description: 'Синтетическая история назначения',
    link: null,
    groupId: dataset.group.id,
    subjectId: dataset.subject.id,
    semesterId: dataset.semester.id,
    lessonDate: date,
    lessonNumber: number,
    bindingMode: 'LESSON',
    lessonType: 'LECTURE',
    requestKey: randomUUID(),
  };
  let published: Row = {};
  await expect
    .poll(
      async () => {
        published = await headman.call('POST', '/api/academic/homeworks', command, [200, 201, 202]);
        return published.state !== 'PENDING' && published.id != null && published.bindingId != null;
      },
      { timeout: 60000, message: 'Assignment lesson-bound homework becomes published' },
    )
    .toBe(true);
  return projection(published, homeworkFields);
}
async function retainedHistory(headman: Actor, checkpoint: Checkpoint) {
  const physical = await physicalRows(
    headman,
    checkpoint.groupId,
    checkpoint.yesterday,
    checkpoint.today,
  );
  const retained = [...checkpoint.history, checkpoint.currentPhysical];
  const marks = [...checkpoint.marks, checkpoint.currentMark];
  for (let index = 0; index < retained.length; index++) {
    const id = Number(retained[index].id);
    const lesson = physical.find((row) => Number(row.id) === id);
    requireValue(lesson, 'Original physical lesson is missing');
    expect(
      JSON.stringify(projection(lesson, physicalFields)) === JSON.stringify(retained[index]),
    ).toBe(true);
    const report = await headman.call('GET', `/api/attendance/reports/lesson/${id}`);
    const mark = report.entries.find((row: Row) => Number(row.userId) === checkpoint.studentId);
    requireValue(mark, 'Original attendance row is missing');
    expect(JSON.stringify(projection(mark, markFields)) === JSON.stringify(marks[index])).toBe(
      true,
    );
  }
  for (const original of checkpoint.homeworks) {
    const current = await headman.call('GET', `/api/academic/homeworks/${original.id}`);
    expect(JSON.stringify(projection(current, homeworkFields)) === JSON.stringify(original)).toBe(
      true,
    );
  }
}
async function futureLesson(headman: Actor, checkpoint: Checkpoint) {
  const physical = await physicalRows(
    headman,
    checkpoint.groupId,
    checkpoint.futureDate,
    checkpoint.futureDate,
  );
  const future = physical.find((row) => Number(row.id) === checkpoint.futureId);
  requireValue(future, 'Original future physical lesson is missing');
  expect(future.current === true && future.status === 'PLANNED').toBe(true);
  for (const field of [
    'id',
    'occurrenceId',
    'date',
    'lessonNumber',
    'groupId',
    'subjectId',
    'semesterId',
  ]) {
    expect(future[field] === checkpoint.future[field]).toBe(true);
  }
  return future;
}
async function close(request: APIRequestContext, headman: Actor, checkpoint: Checkpoint) {
  return request.delete(checkpoint.closeUrl, {
    headers: { ...headman.headers(), 'Idempotency-Key': checkpoint.closeKey },
  });
}
type GroupHistoryStage = 'PREPARE' | 'ADOPTED_PUBLISHED' | 'ADOPTED_AFTER_REPLACEMENT';
async function groupHistory(
  request: APIRequestContext,
  teacher: Actor,
  checkpoint: Checkpoint,
  stage: GroupHistoryStage = 'PREPARE',
) {
  const response = await request.get(journalPath(checkpoint), { headers: teacher.headers() });
  const status = response.status();
  if (status !== 200) {
    // Two closed Source frames identify the caller and status without response data.
    if (stage === 'PREPARE') expect.soft(false).toBe(true);
    if (stage === 'ADOPTED_PUBLISHED') expect.soft(false).toBe(true);
    if (stage === 'ADOPTED_AFTER_REPLACEMENT') expect.soft(false).toBe(true);
    if (status === 201) expect.soft(false).toBe(true);
    if (status === 202) expect.soft(false).toBe(true);
    if (status === 204) expect.soft(false).toBe(true);
    if (status === 400) expect.soft(false).toBe(true);
    if (status === 401) expect.soft(false).toBe(true);
    if (status === 403) expect.soft(false).toBe(true);
    if (status === 404) expect.soft(false).toBe(true);
    if (status === 409) expect.soft(false).toBe(true);
    if (status === 410) expect.soft(false).toBe(true);
    if (status === 412) expect.soft(false).toBe(true);
    if (status === 422) expect.soft(false).toBe(true);
    if (status === 423) expect.soft(false).toBe(true);
    if (status === 429) expect.soft(false).toBe(true);
    if (status === 500) expect.soft(false).toBe(true);
    if (status === 502) expect.soft(false).toBe(true);
    if (status === 503) expect.soft(false).toBe(true);
    if (status === 504) expect.soft(false).toBe(true);
    if (
      ![
        201, 202, 204, 400, 401, 403, 404, 409, 410, 412, 422, 423, 429, 500, 502, 503, 504,
      ].includes(status)
    )
      expect.soft(false).toBe(true);
  }
  expect(response.status()).toBe(200);
  const journal = await response.json();
  expect(
    checkpoint.historyIds.length > 0 &&
      checkpoint.history.every(
        (lesson) => lesson.date < checkpoint.today && lesson.status === 'CLOSED',
      ),
  ).toBe(true);
  expect(journal.lessons.length >= checkpoint.historyIds.length).toBe(true);
  expect(
    checkpoint.historyIds.every((id) =>
      journal.lessons.some((lesson: Row) => Number(lesson.id) === id),
    ),
  ).toBe(true);
  const student = journal.students.find(
    (row: Row) => Number(row.studentId) === checkpoint.studentId,
  );
  requireValue(student, 'Group journal must include the marked student');
  expect(
    checkpoint.historyIds.every((id) =>
      student.cells.some((cell: Row) => Number(cell.lessonId) === id && cell.status === 'present'),
    ),
  ).toBe(true);
}
async function deniedGroup(request: APIRequestContext, teacher: Actor, checkpoint: Checkpoint) {
  const response = await request.get(journalPath(checkpoint), { headers: teacher.headers() });
  // TeacherReadFacade.journal checks live group authority and conceals foreign group scope.
  expect(response.status()).toBe(404);
  expect((await response.json()).code).toBe('OUT_OF_SCOPE');
}

function teacherStatsPath(checkpoint: Checkpoint): string {
  return `/api/v1/teacher/stats?${new URLSearchParams({
    semesterId: String(checkpoint.semesterId),
    scope: 'students',
    groupId: String(checkpoint.groupId),
    subjectId: String(checkpoint.subjectId),
    lessonType: 'lecture',
  })}`;
}

function teacherStatsProjection(value: unknown, checkpoint: Checkpoint): Row {
  requireValue(
    typeof value === 'object' && value !== null && !Array.isArray(value),
    'Teacher stats must return a real aggregate',
  );
  const stats = value as Row;
  const id = (value: unknown): number => {
    requireValue(
      typeof value === 'string' &&
        /^[1-9][0-9]*$/.test(value) &&
        Number.isSafeInteger(Number(value)),
      'Teacher stats identity must be a positive canonical decimal',
    );
    return Number(value);
  };
  const metrics = (row: Row): Row =>
    Object.fromEntries(
      ['present', 'presentOrExcused', 'excused', 'absent'].map((key) => {
        const metric = row[key];
        requireValue(
          metric &&
            Number.isSafeInteger(metric.numerator) &&
            metric.numerator >= 0 &&
            Number.isSafeInteger(metric.denominator) &&
            metric.denominator >= metric.numerator &&
            typeof metric.percent === 'number' &&
            Number.isFinite(metric.percent) &&
            metric.percent >= 0 &&
            metric.percent <= 100,
          'Teacher stats must retain finite native metric triples',
        );
        return [key, projection(metric, ['numerator', 'denominator', 'percent'])];
      }),
    );
  requireValue(
    stats.scope === 'students' &&
      id(stats.semesterId) === checkpoint.semesterId &&
      stats.periodFrom === checkpoint.yesterday &&
      stats.periodTo === checkpoint.yesterday &&
      Number.isSafeInteger(stats.lessonsCount) &&
      stats.lessonsCount === checkpoint.historyIds.length &&
      stats.lessonsCount > 0 &&
      Array.isArray(stats.students) &&
      Array.isArray(stats.groups) &&
      Array.isArray(stats.subjectOptions),
    'Teacher stats must include the exact nonempty closed lesson scope',
  );
  const students: Row[] = stats.students
    .map((row: Row) => ({ studentId: id(row.studentId), ...metrics(row) }))
    .sort((left: Row, right: Row) => left.studentId - right.studentId);
  requireValue(
    new Set(students.map((row: Row) => row.studentId)).size === students.length,
    'Teacher stats students must be unique',
  );
  const marked = students.filter((row: Row) => row.studentId === checkpoint.studentId);
  requireValue(
    marked.length === 1 &&
      marked[0].present.numerator === checkpoint.historyIds.length &&
      marked[0].present.denominator === checkpoint.historyIds.length &&
      marked[0].present.percent === 100,
    'Teacher stats must retain the own seeded PRESENT numerator and denominator',
  );
  const groups = stats.groups.map((row: Row) => {
    requireValue(
      id(row.groupId) === checkpoint.groupId && row.lessonsCount === checkpoint.historyIds.length,
      'Teacher stats group must retain its own closed lesson count',
    );
    return { groupId: id(row.groupId), lessonsCount: row.lessonsCount, ...metrics(row) };
  });
  requireValue(groups.length === 1, 'Teacher stats must retain one exact own group');
  const subjectOptions = stats.subjectOptions
    .map((row: Row) => {
      requireValue(
        Array.isArray(row.lessonTypes) &&
          row.lessonTypes.length > 0 &&
          row.lessonTypes.every(
            (type: unknown) =>
              typeof type === 'string' && ['lecture', 'practice', 'lab'].includes(type),
          ) &&
          new Set(row.lessonTypes).size === row.lessonTypes.length,
        'Teacher stats subject types must remain canonical and unique',
      );
      return {
        groupId: id(row.groupId),
        subjectId: id(row.subjectId),
        lessonTypes: [...row.lessonTypes].sort(),
      };
    })
    .sort(
      (left: Row, right: Row) => left.groupId - right.groupId || left.subjectId - right.subjectId,
    );
  requireValue(
    new Set(subjectOptions.map((row: Row) => `${row.groupId}:${row.subjectId}`)).size ===
      subjectOptions.length &&
      subjectOptions.some(
        (row: Row) =>
          row.groupId === checkpoint.groupId &&
          row.subjectId === checkpoint.subjectId &&
          row.lessonTypes.includes('lecture'),
      ),
    'Teacher stats catalogue must retain the own lecture subject',
  );
  return {
    scope: stats.scope,
    semesterId: checkpoint.semesterId,
    periodFrom: stats.periodFrom,
    periodTo: stats.periodTo,
    lessonsCount: stats.lessonsCount,
    students,
    groups,
    subjectOptions,
  };
}

async function teacherStatsUnavailable(response: APIResponse): Promise<void> {
  requireValue(response.status() === 503, 'Unavailable Schedule must return teacher stats HTTP503');
  requireValue(
    response.headers()['content-type']?.split(';')[0].trim().toLowerCase() ===
      'application/problem+json',
    'Teacher stats HTTP503 must be a typed problem',
  );
  const problem = await response.json();
  requireValue(
    problem &&
      typeof problem === 'object' &&
      !Array.isArray(problem) &&
      problem.status === 503 &&
      problem.code === 'DEPENDENCY_UNAVAILABLE' &&
      problem.type === 'urn:rct:problem:dependency-unavailable' &&
      !['lessonsCount', 'students', 'groups'].some((key) => key in problem),
    'Teacher stats outage must not masquerade as successful empty statistics',
  );
}

async function teacherStatsRead(
  request: APIRequestContext,
  teacher: Actor,
  checkpoint: Checkpoint,
  readyUntil?: number,
): Promise<Row> {
  let lastTyped503 = false;
  const remainingBudget = () => {
    const remaining = readyUntil === undefined ? 10000 : readyUntil - Date.now();
    if (remaining <= 0 && lastTyped503)
      throw new Error(
        'Teacher stats dependency HTTP503 persisted through the original restart deadline',
      );
    requireValue(remaining > 0, 'Teacher stats original restart deadline expired');
    return remaining;
  };
  while (true) {
    const response = await request.get(teacherStatsPath(checkpoint), {
      headers: teacher.headers(),
      timeout: Math.min(10000, remainingBudget()),
    });
    try {
      if (response.status() === 200) {
        remainingBudget();
        const snapshot = teacherStatsProjection(await response.json(), checkpoint);
        remainingBudget();
        return snapshot;
      }
      await teacherStatsUnavailable(response);
      lastTyped503 = true;
      requireValue(readyUntil !== undefined, 'Teacher stats baseline must return HTTP200');
    } finally {
      await response.dispose();
    }
    await delay(Math.min(500, remainingBudget()));
  }
}

async function prepare(request: APIRequestContext, run: string) {
  const directory = process.env.RCT_E2E_TEACHING_CLOCK_DIR;
  requireValue(
    directory && isAbsolute(directory),
    'Assignment requires the owned educational clock',
  );
  requireValue(!lstatSync(directory).isSymbolicLink(), 'Assignment clock directory is a link');
  requireValue(
    !lstatSync(join(directory, 'disposable-target')).isSymbolicLink(),
    'Assignment clock marker is a link',
  );
  const initialInstant = readTeachingInstant(directory);
  const clock = join(directory, 'current-instant');
  const clockStat = lstatSync(clock);
  requireValue(
    clockStat.isFile() && !clockStat.isSymbolicLink(),
    'Assignment clock file is not regular',
  );
  const originalClock = readFileSync(clock);
  const dataset = new MobileDataset(
    request,
    await admit(request, TEST_USERS.admin, 'ADMIN'),
    initialInstant,
  );
  await dataset.initialize();
  const headman = dataset.headmanActor;
  const yesterday = studyDay(shift(dataset.today, -1), -1);
  // Keep LESSON publication separate from MobileDataset's existing tomorrow DATE binding.
  const futureDate = studyDay(shift(dataset.tomorrow, 1), 1);
  const replacementEffectiveFrom = shift(dataset.today, 1);
  requireValue(
    yesterday < dataset.today &&
      yesterday >= dataset.semester.dateFrom &&
      replacementEffectiveFrom <= futureDate &&
      futureDate <= dataset.semester.dateTo,
    'Assignment historical and future dates must be covered',
  );
  const adopted = await dataset.user('TEACHER');
  const replacement = await dataset.user('TEACHER');
  const foreign = await dataset.user('TEACHER');
  const adoptedAccount = await dataset.admin.call('GET', `/api/academic/users/${adopted.id}`);
  requireValue(
    typeof adoptedAccount.employeeNumber === 'string',
    'Teacher employee number is missing',
  );
  const oneOff = async (date: string, number: number) =>
    headman.call(
      'POST',
      '/api/schedule/one-off-lessons',
      {
        groupId: dataset.group.id,
        subjectId: dataset.subject.id,
        assignmentId: dataset.subject.assignments[0].id,
        date,
        lessonNumber: number,
        classroom: 'Синтетическая история назначения',
      },
      [200, 201],
      { 'Idempotency-Key': randomUUID() },
    );
  const past = await oneOff(yesterday, 1);
  const future = await oneOff(futureDate, 2);
  const historyIds = [Number(past.physicalLessonId)];
  const todayId = Number(dataset.lesson.physicalLessonId);
  const markedIds = [...historyIds, todayId];
  for (const id of historyIds) {
    await expect
      .poll(
        async () =>
          (await physicalRows(headman, dataset.group.id, yesterday, yesterday)).some(
            (row) => Number(row.id) === id && row.status === 'CLOSED' && row.current === true,
          ),
        { timeout: 90000, message: 'Own past lesson closes before its attendance is marked' },
      )
      .toBe(true);
    let original: Row = {};
    await expect
      .poll(
        async () => {
          const report = await headman.call('GET', `/api/attendance/reports/lesson/${id}`);
          original =
            report.entries.find((entry: Row) => Number(entry.userId) === dataset.student.id) ?? {};
          return (
            report.editable === true &&
            original.status === 'absent' &&
            typeof original.markRevision === 'string' &&
            original.markRevision.trim().length > 0
          );
        },
        { timeout: 60000, message: 'Own closed attendance materializes its absence and revision' },
      )
      .toBe(true);
    await headman.call('PUT', `/api/attendance/lessons/${id}/students/${dataset.student.id}`, {
      status: 'PRESENT',
      expectedStatus: 'ABSENT',
      expectedRevision: original.markRevision,
    });
  }
  for (const id of [todayId]) {
    const report = await headman.call('GET', `/api/attendance/reports/lesson/${id}`);
    const row = report.entries.find((entry: Row) => Number(entry.userId) === dataset.student.id);
    requireValue(row && report.editable === true, 'Original history must admit its real mark');
    await headman.call('PUT', `/api/attendance/lessons/${id}/students/${dataset.student.id}`, {
      status: 'PRESENT',
      expectedStatus: String(row.status ?? 'EMPTY').toUpperCase(),
      expectedRevision: row.markRevision ?? 'EMPTY',
    });
  }
  // Past-date homework creation is forbidden; retain today's real lesson-bound content separately.
  const homeworks = [
    await homework(headman, dataset, dataset.today, 1),
    await homework(headman, dataset, futureDate, 2),
  ];
  requireValue(
    readFileSync(clock).equals(originalClock),
    'Assignment educational clock changed during preparation',
  );
  try {
    await expect
      .poll(
        async () => {
          const physical = await physicalRows(headman, dataset.group.id, yesterday, dataset.today);
          return (
            historyIds.every((id) =>
              physical.some(
                (row) => Number(row.id) === id && row.status === 'CLOSED' && row.current === true,
              ),
            ) &&
            physical.some(
              (row) =>
                Number(row.id) === todayId && row.status === 'ACTIVE' && row.current === true,
            )
          );
        },
        {
          timeout: 90000,
          message: 'Own past lesson closes and current lesson activates under the unchanged clock',
        },
      )
      .toBe(true);
    const physical = await physicalRows(headman, dataset.group.id, yesterday, futureDate);
    const futureRow = physical.find((row) => Number(row.id) === Number(future.physicalLessonId));
    requireValue(
      futureRow?.current === true && futureRow.status === 'PLANNED',
      'Future physical lesson must remain planned',
    );
    const currentPhysical = physical.find((lesson) => Number(lesson.id) === todayId);
    requireValue(currentPhysical?.status === 'ACTIVE', 'Current physical lesson must stay active');
    const history = historyIds.map((id) => {
      const row = physical.find((lesson) => Number(lesson.id) === id);
      requireValue(row, 'Closed physical history is missing');
      return projection(row, physicalFields);
    });
    const marks: Row[] = [];
    for (const id of markedIds) {
      const report = await headman.call('GET', `/api/attendance/reports/lesson/${id}`);
      const row = report.entries.find((entry: Row) => Number(entry.userId) === dataset.student.id);
      requireValue(
        row && row.status === 'present' && typeof row.markRevision === 'string',
        'Original attendance must retain the real mark',
      );
      marks.push(projection(row, markFields));
    }
    const checkpoint: Checkpoint = {
      headman: dataset.headman,
      former: dataset.teacher,
      adopted,
      replacement,
      foreign,
      groupId: dataset.group.id,
      subjectId: dataset.subject.id,
      semesterId: dataset.semester.id,
      studentId: dataset.student.id,
      sourceAssignmentId: Number(dataset.subject.assignments[0].id),
      adoptedEmployeeNumber: adoptedAccount.employeeNumber,
      today: dataset.today,
      yesterday,
      futureDate,
      replacementEffectiveFrom,
      futureId: Number(future.physicalLessonId),
      historyIds,
      closeUrl: `/api/academic/assignments/${dataset.subject.assignments[0].id}?validUntilExclusive=${dataset.today}`,
      closeKey: randomUUID(),
      history,
      marks: marks.slice(0, historyIds.length),
      currentPhysical: projection(currentPhysical, physicalFields),
      currentMark: marks[marks.length - 1],
      homeworks,
      future: projection(futureRow, physicalFields),
    };
    await retainedHistory(headman, checkpoint);
    const statsTeacher = await admit(request, dataset.teacher, 'TEACHER');
    await groupHistory(request, statsTeacher, checkpoint);
    checkpoint.teacherStatsBaseline = await teacherStatsRead(request, statsTeacher, checkpoint);
    save(run, 'prepared.private.json', checkpoint);
  } finally {
    requireValue(
      readFileSync(clock).equals(originalClock),
      'Assignment preparation changed the teaching clock',
    );
  }
}

async function resume(request: APIRequestContext, run: string, checkpoint: Checkpoint) {
  const pending = load<{ closeUrl: string; closeKey: string }>(run, 'pending.private.json');
  expect(pending.closeUrl === checkpoint.closeUrl && pending.closeKey === checkpoint.closeKey).toBe(
    true,
  );
  const restartReadyUntil = assignmentRestartDeadline(
    process.env.RCT_ASSIGNMENT_RESTART_READY_UNTIL_MS,
  );
  const headman = await admit(request, checkpoint.headman, 'HEADMAN');
  const admin = await admit(request, TEST_USERS.admin, 'ADMIN');
  await assignmentRestartReady(request, admin, checkpoint.semesterId, restartReadyUntil);
  await assignmentReportReady(request, headman, checkpoint, restartReadyUntil);
  requireValue(checkpoint.teacherStatsBaseline, 'Teacher stats private baseline must be present');
  const statsTeacher = await admit(request, checkpoint.former, 'TEACHER');
  const recoveredStats = await teacherStatsRead(
    request,
    statsTeacher,
    checkpoint,
    restartReadyUntil,
  );
  requireValue(
    JSON.stringify(recoveredStats) === JSON.stringify(checkpoint.teacherStatsBaseline),
    'Teacher stats recovery must preserve the same numeric baseline',
  );
  requireValue(
    Date.now() < restartReadyUntil,
    'Assignment same-channel readiness deadline expired before resume',
  );
  const closed = await close(request, headman, checkpoint);
  if (closed.status() !== 204)
    throw datasetHttpFailure('DELETE', checkpoint.closeUrl, closed.status());
  expect(closed.status()).toBe(204);
  const unassigned = await futureLesson(headman, checkpoint);
  expect(unassigned.assignmentId == null && unassigned.assignedTeacherId == null).toBe(true);
  await retainedHistory(headman, checkpoint);
  const closedSubject = await headman.call('GET', subjectPath(checkpoint));
  expect(
    closedSubject.assignments.some((row: Row) => Number(row.id) === checkpoint.sourceAssignmentId),
  ).toBe(false);
  expect((await close(request, headman, checkpoint)).status()).toBe(204);
  expect(
    JSON.stringify(projection(await futureLesson(headman, checkpoint), physicalFields)) ===
      JSON.stringify(projection(unassigned, physicalFields)),
  ).toBe(true);
  const former = await admit(request, checkpoint.former, 'TEACHER');
  await deniedGroup(request, former, checkpoint);

  const accepted = await headman.call(
    'POST',
    '/api/academic/assignments',
    {
      employeeNumber: checkpoint.adoptedEmployeeNumber,
      subjectId: checkpoint.subjectId,
      groupId: checkpoint.groupId,
      semesterId: checkpoint.semesterId,
      lessonType: 'LECTURE',
      validFrom: checkpoint.today,
      validUntilExclusive: null,
    },
    [201],
  );
  const adoptedId = Number(accepted.id);
  requireValue(
    Number.isSafeInteger(adoptedId) && adoptedId > 0,
    'Adopted assignment identity is missing',
  );
  await expect
    .poll(
      async () => {
        const subject = await headman.call('GET', subjectPath(checkpoint));
        return subject.assignments.filter(
          (row: Row) =>
            Number(row.id) === adoptedId && Number(row.teacherId) === checkpoint.adopted.id,
        ).length;
      },
      { timeout: 60000, message: 'Adopted assignment becomes authoritatively published' },
    )
    .toBe(1);
  const adoptedFuture = await futureLesson(headman, checkpoint);
  expect(
    Number(adoptedFuture.assignmentId) === adoptedId &&
      Number(adoptedFuture.assignedTeacherId) === checkpoint.adopted.id,
  ).toBe(true);
  const adopted = await admit(request, checkpoint.adopted, 'TEACHER');
  await groupHistory(request, adopted, checkpoint, 'ADOPTED_PUBLISHED');
  await retainedHistory(headman, checkpoint);

  const command = {
    replacementTeacherId: String(checkpoint.replacement.id),
    effectiveFrom: checkpoint.replacementEffectiveFrom,
    requestKey: randomUUID(),
  };
  const replaced = await headman.call(
    'POST',
    `/api/academic/assignments/${adoptedId}/replace`,
    command,
    [201],
  );
  expect(replaced.state === 'COMMITTED' && replaced.scheduleReceiptState === 'COMMITTED').toBe(
    true,
  );
  expect(
    Number(replaced.sourceAssignmentId) === adoptedId &&
      Number(replaced.targetTeacherId) === checkpoint.replacement.id &&
      replaced.effectiveFrom === shift(checkpoint.today, 1) &&
      replaced.sourceValidUntilExclusive === shift(checkpoint.today, 1) &&
      Number(replaced.movedCount) >= 1,
  ).toBe(true);
  expect(
    checkpoint.replacementEffectiveFrom === shift(checkpoint.today, 1) &&
      checkpoint.futureDate >= checkpoint.replacementEffectiveFrom,
  ).toBe(true);
  const moved = await futureLesson(headman, checkpoint);
  expect(
    Number(moved.assignmentId) === Number(replaced.targetAssignmentId) &&
      Number(moved.assignedTeacherId) === checkpoint.replacement.id,
  ).toBe(true);
  const status = await headman.call(
    'GET',
    `/api/academic/assignments/replacements/${replaced.operationId}`,
  );
  expect(
    status.operationId === replaced.operationId &&
      status.state === 'COMMITTED' &&
      status.targetAssignmentId === replaced.targetAssignmentId,
  ).toBe(true);
  const replay = await headman.call(
    'POST',
    `/api/academic/assignments/${adoptedId}/replace`,
    command,
    [201],
  );
  expect(
    replay.operationId === replaced.operationId &&
      replay.targetAssignmentId === replaced.targetAssignmentId &&
      replay.movedCount === replaced.movedCount,
  ).toBe(true);
  const finalSubject = await headman.call('GET', subjectPath(checkpoint));
  expect(
    finalSubject.assignments.filter(
      (row: Row) =>
        Number(row.id) === adoptedId && row.validUntilExclusive === shift(checkpoint.today, 1),
    ).length,
  ).toBe(1);
  expect(
    finalSubject.assignments.filter(
      (row: Row) =>
        Number(row.id) === Number(replaced.targetAssignmentId) &&
        row.validFrom === shift(checkpoint.today, 1),
    ).length,
  ).toBe(1);
  await retainedHistory(headman, checkpoint);
  // B remains active today; C's accepted assignment starts on the later replacement date.
  await groupHistory(request, adopted, checkpoint, 'ADOPTED_AFTER_REPLACEMENT');
  await deniedGroup(request, former, checkpoint);
  await deniedGroup(request, await admit(request, checkpoint.foreign, 'TEACHER'), checkpoint);
  await deniedGroup(request, await admit(request, checkpoint.replacement, 'TEACHER'), checkpoint);
  expect(
    JSON.stringify(projection(await futureLesson(headman, checkpoint), physicalFields)) ===
      JSON.stringify(projection(moved, physicalFields)),
  ).toBe(true);
  save(run, 'verified.private.json', {
    closeKey: checkpoint.closeKey,
    replacementOperationId: replaced.operationId,
    targetAssignmentId: replaced.targetAssignmentId,
  });
}

// CI sets this absolute UTC epoch-millisecond deadline before starting the
// original Schedule container. HTTP health and this same-channel RPC witness
// share the existing 120-second restart budget; no new readiness window starts.
function assignmentRestartDeadline(value: string | undefined): number {
  requireValue(
    typeof value === 'string' && /^[1-9][0-9]{0,15}$/.test(value),
    'Assignment restart deadline must be absolute integer UTC milliseconds',
  );
  const deadline = Number(value);
  const now = Date.now();
  requireValue(
    Number.isSafeInteger(deadline) && deadline > now && deadline <= now + 120000,
    'Assignment restart deadline must retain the original bounded restart window',
  );
  return deadline;
}

function assignmentPreviewMatches(value: unknown, semesterId: number): boolean {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false;
  const preview = value as Record<string, unknown>;
  if (
    preview.semesterId !== semesterId ||
    typeof preview.semesterName !== 'string' ||
    preview.semesterName.length === 0 ||
    typeof preview.priorState !== 'string' ||
    !['ACTIVE', 'INACTIVE', 'ARCHIVED'].includes(preview.priorState) ||
    !Number.isSafeInteger(preview.stateVersion) ||
    Number(preview.stateVersion) < 0 ||
    typeof preview.previewDigest !== 'string' ||
    !/^[a-f0-9]{64}$/i.test(preview.previewDigest) ||
    typeof preview.counts !== 'object' ||
    preview.counts === null ||
    Array.isArray(preview.counts)
  )
    return false;
  const counts = preview.counts as Record<string, unknown>;
  return [
    'scheduleTemplates',
    'oneOffLessons',
    'lessons',
    'assignments',
    'homeworks',
    'attendanceMarks',
    'studentRequests',
  ].every((key) => Number.isSafeInteger(counts[key]) && Number(counts[key]) >= 0);
}

async function assignmentRestartReady(
  request: APIRequestContext,
  admin: Actor,
  semesterId: number,
  readyUntil: number,
): Promise<void> {
  const path = `/api/academic/semesters/${semesterId}/delete-preview`;
  while (true) {
    const remaining = readyUntil - Date.now();
    requireValue(remaining > 0, 'Assignment same-channel readiness deadline expired');
    const response = await request.get(path, {
      headers: admin.headers(),
      timeout: Math.min(remaining, 3000),
    });
    try {
      requireValue(Date.now() < readyUntil, 'Assignment same-channel readiness deadline expired');
      if (response.status() === 200) {
        let preview: unknown;
        try {
          preview = await response.json();
        } catch {
          throw new Error('Assignment readiness preview must be valid JSON');
        }
        requireValue(Date.now() < readyUntil, 'Assignment same-channel readiness deadline expired');
        requireValue(
          assignmentPreviewMatches(preview, semesterId),
          'Assignment readiness preview must match the checkpoint semester and DTO',
        );
        return;
      }
      if (response.status() !== 503) throw datasetHttpFailure('GET', path, response.status());
    } finally {
      await response.dispose();
    }
    const remainingAfterRead = readyUntil - Date.now();
    requireValue(remainingAfterRead > 0, 'Assignment same-channel readiness deadline expired');
    await delay(Math.min(500, remainingAfterRead));
  }
}

function assignmentReportMatches(value: unknown, checkpoint: Checkpoint): boolean {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false;
  const report = value as Record<string, unknown>;
  return (
    report.lessonId === checkpoint.currentPhysical.id &&
    report.groupId === checkpoint.groupId &&
    report.subjectId === checkpoint.subjectId &&
    report.semesterId === checkpoint.semesterId &&
    report.lessonDate === checkpoint.currentPhysical.date &&
    Array.isArray(report.entries) &&
    report.entries.some(
      (entry: unknown) =>
        typeof entry === 'object' &&
        entry !== null &&
        !Array.isArray(entry) &&
        (entry as Record<string, unknown>).userId === checkpoint.studentId,
    )
  );
}

// Read the retained current lesson through Attendance's own Schedule channel
// before resuming mutations. Reuse CI's original absolute restart deadline.
async function assignmentReportReady(
  request: APIRequestContext,
  headman: Actor,
  checkpoint: Checkpoint,
  readyUntil: number,
): Promise<void> {
  const lesson = checkpoint.currentPhysical;
  requireValue(
    [
      lesson.id,
      checkpoint.groupId,
      checkpoint.subjectId,
      checkpoint.semesterId,
      checkpoint.studentId,
    ].every((id) => Number.isSafeInteger(id) && id > 0) &&
      lesson.groupId === checkpoint.groupId &&
      lesson.subjectId === checkpoint.subjectId &&
      lesson.semesterId === checkpoint.semesterId &&
      typeof lesson.date === 'string' &&
      lesson.date === checkpoint.today,
    'Assignment readiness report requires the own checkpoint lesson',
  );
  const path = `/api/attendance/reports/lesson/${lesson.id}`;
  let unavailable = false;
  const remainingBudget = () => {
    const remaining = readyUntil - Date.now();
    if (remaining <= 0 && unavailable) throw datasetHttpFailure('GET', path, 503);
    requireValue(remaining > 0, 'Assignment report readiness deadline expired');
    return remaining;
  };
  while (true) {
    const remaining = remainingBudget();
    const response = await request.get(path, {
      headers: headman.headers(),
      timeout: Math.min(remaining, 3000),
    });
    try {
      if (response.status() === 503) {
        unavailable = true;
      } else if (response.status() === 200) {
        remainingBudget();
        let report: unknown;
        try {
          report = await response.json();
        } catch {
          throw new Error('Assignment readiness report must be valid JSON');
        }
        remainingBudget();
        requireValue(
          assignmentReportMatches(report, checkpoint),
          'Assignment readiness report must match the own checkpoint lesson and student',
        );
        return;
      } else {
        throw datasetHttpFailure('GET', path, response.status());
      }
    } finally {
      await response.dispose();
    }
    await delay(Math.min(500, remainingBudget()));
  }
}

test('assignment lifecycle API phase', { annotation: diagnosticCase('ASSIGNMENT_LIFECYCLE_API') }, async ({ playwright }, info) => {
  const local = localSpecialLane('assignment');
  requirePrivateArtifacts(info);
  requireValue(phase && phases.includes(phase), 'Assignment phase must be explicit');
  test.setTimeout(phase === 'prepare' ? 300000 : 120000);
  const run = process.env.RCT_ASSIGNMENT_LIFECYCLE_RUN;
  requireValue(!local || run === local.laneRun, 'Local assignment checkpoint mismatch');
  requireValue(run && isAbsolute(run), 'Assignment requires an absolute private run directory');
  for (const path of [run, join(run, 'artifacts'), join(run, 'artifacts', phase)]) {
    const stat = lstatSync(path);
    requireValue(
      stat.isDirectory() && !stat.isSymbolicLink() && (stat.mode & 0o777) === 0o700,
      'Assignment directories must be private',
    );
  }
  const marker = join(run, 'disposable-target');
  privateFile(marker, 64);
  requireValue(
    readFileSync(marker, 'utf8').trim() === 'RCT_ASSIGNMENT_LIFECYCLE_DISPOSABLE',
    'Assignment ownership marker mismatch',
  );
  requireValue(
    info.project.outputDir === join(run, 'artifacts', phase),
    'Assignment artifacts must stay in their phase',
  );
  const output =
    phase === 'prepare'
      ? 'prepared.private.json'
      : phase === 'pending'
        ? 'pending.private.json'
        : 'verified.private.json';
  requireValue(!existsSync(join(run, output)), 'Assignment phase output must be new');
  const baseURL = String(info.project.use.baseURL);
  const origin = new URL(baseURL);
  requireValue(
    process.env.RCT_E2E_SYNTHETIC_SEED === 'true' &&
      origin.protocol === 'https:' &&
      origin.hostname === 'localhost' &&
      origin.port === '8443',
    'Assignment requires explicit disposable HTTPS localhost:8443',
  );
  const request = await playwright.request.newContext({
    baseURL,
    ignoreHTTPSErrors: false,
    timeout: 10000,
  });
  try {
    if (phase === 'prepare') return await prepare(request, run);
    const checkpoint = load<Checkpoint>(run, 'prepared.private.json');
    if (phase === 'resume') return await resume(request, run, checkpoint);
    const statsTeacher = await admit(request, checkpoint.former, 'TEACHER');
    const statsUnavailable = await request.get(teacherStatsPath(checkpoint), {
      headers: statsTeacher.headers(),
    });
    try {
      await teacherStatsUnavailable(statsUnavailable);
    } finally {
      await statsUnavailable.dispose();
    }
    const headman = await admit(request, checkpoint.headman, 'HEADMAN');
    const before = await headman.call('GET', subjectPath(checkpoint));
    const response = await close(request, headman, checkpoint);
    expect(response.status()).toBe(503);
    const pending = await response.json();
    expect(
      pending.type.endsWith('/assignment-closure-pending') &&
        Number(pending.extras.assignmentId) === checkpoint.sourceAssignmentId &&
        pending.extras.requestKey === checkpoint.closeKey,
    ).toBe(true);
    // Schedule is genuinely unavailable: no install receipt, so Academic retains the source publication.
    const after = await headman.call('GET', subjectPath(checkpoint));
    expect(JSON.stringify(after.assignments) === JSON.stringify(before.assignments)).toBe(true);
    expect(
      after.assignments.filter((row: Row) => Number(row.id) === checkpoint.sourceAssignmentId)
        .length,
    ).toBe(1);
    save(run, 'pending.private.json', {
      closeUrl: checkpoint.closeUrl,
      closeKey: checkpoint.closeKey,
    });
  } finally {
    await request.dispose();
  }
});
