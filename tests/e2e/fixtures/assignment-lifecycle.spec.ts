import { randomUUID } from 'node:crypto';
import { existsSync, lstatSync, readFileSync, writeFileSync } from 'node:fs';
import { isAbsolute, join } from 'node:path';
import { type APIRequestContext, expect } from '@playwright/test';
import { test, requirePrivateArtifacts } from './artifact-privacy';
import { Actor, MobileDataset, admit, type SyntheticUser } from './mobile-dataset';
import { readTeachingInstant } from './teaching-clock';
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
async function groupHistory(request: APIRequestContext, teacher: Actor, checkpoint: Checkpoint) {
  const response = await request.get(journalPath(checkpoint), { headers: teacher.headers() });
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
    await groupHistory(request, await admit(request, dataset.teacher, 'TEACHER'), checkpoint);
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
  const headman = await admit(request, checkpoint.headman, 'HEADMAN');
  expect((await close(request, headman, checkpoint)).status()).toBe(204);
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
  await groupHistory(request, adopted, checkpoint);
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
  await groupHistory(request, adopted, checkpoint);
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

test('assignment lifecycle API phase', async ({ playwright }, info) => {
  requirePrivateArtifacts(info);
  requireValue(phase && phases.includes(phase), 'Assignment phase must be explicit');
  test.setTimeout(phase === 'prepare' ? 300000 : 120000);
  const run = process.env.RCT_ASSIGNMENT_LIFECYCLE_RUN;
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
