import { randomUUID } from 'node:crypto';
import { lstatSync, readFileSync } from 'node:fs';
import { isAbsolute, join } from 'node:path';
import { expect, type APIRequestContext } from '@playwright/test';
import { test, requirePrivateArtifacts } from './artifact-privacy';
import { Actor, MobileDataset, admit } from './mobile-dataset';
import { TEST_USERS } from './users';
import { retainFirstDatasetHttpFailure } from './safe-reporter';

type Row = Record<string, any>;

function requireValue(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function validDate(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    Number.isFinite(Date.parse(`${value}T12:00:00Z`)) &&
    new Date(`${value}T12:00:00Z`).toISOString().slice(0, 10) === value
  );
}

function shift(date: string, days: number) {
  return new Date(Date.parse(`${date}T12:00:00Z`) + days * 86400000).toISOString().slice(0, 10);
}

function weekday(date: string) {
  let result = date;
  while ([0, 6].includes(new Date(`${result}T12:00:00Z`).getUTCDay())) result = shift(result, 1);
  return result;
}

function unchangedClock() {
  const directory = process.env.RCT_E2E_TEACHING_CLOCK_DIR;
  requireValue(
    directory && isAbsolute(directory),
    'Semester lifecycle requires an owned educational clock',
  );
  requireValue(
    !lstatSync(directory).isSymbolicLink(),
    'Semester clock directory must not be a link',
  );
  const marker = join(directory, 'disposable-target');
  const file = join(directory, 'current-instant');
  requireValue(
    !lstatSync(marker).isSymbolicLink() && lstatSync(marker).size <= 64,
    'Semester clock marker must be bounded',
  );
  requireValue(
    readFileSync(marker, 'utf8').trim() === 'RCT_E2E_DISPOSABLE',
    'Semester clock ownership mismatch',
  );
  requireValue(
    !lstatSync(file).isSymbolicLink() && lstatSync(file).size <= 128,
    'Semester clock input must be bounded',
  );
  const original = readFileSync(file);
  const originalInstant = original.toString('utf8');
  requireValue(
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?Z\s*$/.test(originalInstant) &&
      Number.isFinite(Date.parse(originalInstant.trim())),
    'Semester clock instant is invalid',
  );
  return {
    instant: new Date(originalInstant.trim()),
    assertUnchanged: () =>
      requireValue(readFileSync(file).equals(original), 'Shared educational clock changed'),
  };
}

async function semesters(admin: Actor): Promise<Row[]> {
  const result: Row[] = [];
  let total: number | undefined;
  for (let page = 0; page < 5; page++) {
    const response = await admin.call(
      'GET',
      `/api/academic/semesters?page=${page}&size=200&sort=id,asc`,
    );
    const rows = response._embedded?.semesterResponseList ?? [];
    requireValue(
      Array.isArray(rows) && rows.length <= 200 && response.page?.number === page,
      'Semester inventory page is invalid',
    );
    requireValue(
      Number.isSafeInteger(response.page?.totalElements) &&
        response.page.totalElements >= 0 &&
        response.page.totalElements <= 1000,
      'Semester inventory exceeds the bounded fixture scope',
    );
    if (total === undefined) total = response.page.totalElements;
    requireValue(
      response.page.totalElements === total,
      'Semester inventory changed during preparation',
    );
    requireValue(
      rows.every(
        (row: Row) =>
          Number.isSafeInteger(Number(row.id)) &&
          Number(row.id) > 0 &&
          validDate(row.dateFrom) &&
          validDate(row.dateTo) &&
          row.dateFrom <= row.dateTo,
      ),
      'Semester inventory identity or range is invalid',
    );
    result.push(...rows);
    requireValue(
      new Set(result.map((row) => String(row.id))).size === result.length,
      'Semester inventory contains duplicate identities',
    );
    if (result.length === total) return result;
    requireValue(rows.length > 0 && result.length < total!, 'Semester inventory is incomplete');
  }
  throw new Error('Semester inventory is incomplete');
}

function fields(row: Row, names: string[]) {
  return Object.fromEntries(names.map((name) => [name, row[name]]));
}

function equal(left: unknown, right: unknown, message: string) {
  requireValue(JSON.stringify(left) === JSON.stringify(right), message);
}

function history(response: Row) {
  requireValue(Array.isArray(response.subjects), 'Student history must contain subjects');
  const rows = response.subjects.map((subject: Row) => ({
    id: subject.id,
    typeCards: subject.typeCards.map((card: Row) => ({ type: card.type, history: card.history })),
  }));
  requireValue(
    rows.some((row: Row) =>
      row.typeCards.some((card: Row) => Array.isArray(card.history) && card.history.length > 0),
    ),
    'Student history must be nonempty',
  );
  return rows;
}

async function terminal(
  admin: Actor,
  semesterId: number,
  action: 'ARCHIVE' | 'RESTORE',
  key: string,
  timeout = 120000,
) {
  const path = `/api/academic/semesters/${semesterId}/${action === 'ARCHIVE' ? 'archive' : 'restore'}`;
  const operation = await admin.call('POST', path, undefined, [200, 202, 503], {
    'Idempotency-Key': key,
  });
  requireValue(
    typeof operation.operationId === 'string',
    'Semester command must retain its operation identity',
  );
  let status: Row = {};
  await expect
    .poll(
      async () => {
        status = await admin.call('GET', `/api/academic/semesters/${semesterId}/archive/status`);
        requireValue(
          status.operation?.operationId === operation.operationId &&
            status.operation.action === action,
          'Semester participant operation identity changed',
        );
        requireValue(
          status.operation.operationState !== 'ERROR' || status.operation.retryable === true,
          'Semester participant operation failed terminally',
        );
        return status.operation.operationState === 'COMPLETED';
      },
      { timeout, message: 'Real semester participant barrier completes' },
    )
    .toBe(true);
  const participant = action === 'ARCHIVE' ? 'READY' : 'RELEASED';
  requireValue(
    ['academic', 'schedule', 'attendance'].every((name) => status.operation[name] === participant),
    'All three real semester participant receipts are required',
  );
  requireValue(
    status.active === false && status.transition === 'NONE' && status.releasePending === false,
    'Semester transition must settle without activation',
  );
  requireValue(
    status.archived === (action === 'ARCHIVE') && status.writeBlocked === (action === 'ARCHIVE'),
    'Semester archive write gate differs from the accepted state',
  );
  const semester = await admin.call('GET', `/api/academic/semesters/${semesterId}`);
  requireValue(
    Number(status.semesterId) === semesterId &&
      Number(status.operation.semesterId) === semesterId &&
      Number(semester.id) === semesterId &&
      semester.active === false &&
      semester.activationScheduled === false &&
      semester.archived === (action === 'ARCHIVE'),
    'Settled semester identity and inactive authority must agree',
  );
  const replay = await admin.call('POST', path, undefined, [200], { 'Idempotency-Key': key });
  requireValue(
    replay.operationId === operation.operationId &&
      replay.operationState === 'COMPLETED' &&
      replay.stateVersion === status.operation.stateVersion,
    'Semester command replay must retain the completed operation',
  );
  return status;
}

async function conflict(
  request: APIRequestContext,
  actor: Actor,
  method: 'PUT' | 'DELETE',
  path: string,
  data?: unknown,
) {
  const response = await request.fetch(path, { method, data, headers: actor.headers() });
  requireValue(
    response.status() === 409 &&
      response.headers()['content-type']?.includes('application/problem+json'),
    'Archived writer must return a typed conflict',
  );
  const body = await response.json();
  requireValue(
    body.status === 409 && body.type === 'https://api.rutcampustrack.ru/problems/conflict',
    'Archived writer problem type changed',
  );
}

test('FULL admin archives and restores the disposable CI semester through real participants', async ({
  playwright,
}, info) => {
  requirePrivateArtifacts(info);
  test.setTimeout(420000);
  const run = process.env.RCT_SEMESTER_LIFECYCLE_RUN;
  requireValue(
    run && isAbsolute(run),
    'Semester lifecycle requires an absolute private run directory',
  );
  for (const path of [run, join(run, 'artifacts')]) {
    const stat = lstatSync(path);
    requireValue(
      stat.isDirectory() && !stat.isSymbolicLink() && (stat.mode & 0o777) === 0o700,
      'Semester lifecycle artifacts must be private',
    );
  }
  const marker = join(run, 'disposable-target');
  const markerInfo = lstatSync(marker);
  requireValue(
    markerInfo.isFile() &&
      !markerInfo.isSymbolicLink() &&
      (markerInfo.mode & 0o777) === 0o600 &&
      markerInfo.size <= 64,
    'Semester lifecycle marker must be private',
  );
  requireValue(
    readFileSync(marker, 'utf8').trim() === 'RCT_SEMESTER_DISPOSABLE',
    'Semester lifecycle ownership mismatch',
  );
  requireValue(
    info.project.outputDir === join(run, 'artifacts'),
    'Semester lifecycle outputs must stay in the private directory',
  );
  const baseURL = String(info.project.use.baseURL);
  const origin = new URL(baseURL);
  requireValue(
    process.env.RCT_E2E_SYNTHETIC_SEED === 'true' &&
      origin.protocol === 'https:' &&
      origin.hostname === 'localhost' &&
      origin.port === '8443',
    'Semester lifecycle requires disposable HTTPS localhost:8443',
  );
  // The same four educational services read this file; Auth and JWT security clocks are untouched.
  const clock = unchangedClock();
  const request = await playwright.request.newContext({
    baseURL,
    ignoreHTTPSErrors: false,
    timeout: 10000,
  });
  let admin: Actor | undefined;
  let previousActive: Row | undefined;
  let ownSemesterId: number | undefined;
  let originalAuthorityCaptured = false;
  let archiveStarted = false;
  const archiveKey = randomUUID();
  const restoreKey = randomUUID();
  try {
    admin = await admit(request, TEST_USERS.admin, 'ADMIN');
    const adminAccess = await admin.call('GET', '/api/academic/admin-access/me');
    requireValue(
      adminAccess.mode === 'FULL' &&
        adminAccess.assigned === true &&
        String(adminAccess.userId) === admin.session.userId,
      'Semester lifecycle requires actual FULL administrator admission',
    );
    const original = await semesters(admin);
    // All prior lane data and this semester are disposable CI-owned synthetic state.
    // Pin its identity to the successful application-recovery checkpoint before any write.
    const recovery = process.env.RCT_APP_RECOVERY_RUN;
    requireValue(recovery && isAbsolute(recovery), 'Prior application recovery is required');
    const recoveryInfo = lstatSync(recovery);
    requireValue(
      recoveryInfo.isDirectory() &&
        !recoveryInfo.isSymbolicLink() &&
        (recoveryInfo.mode & 0o777) === 0o700,
      'Recovery checkpoint directory must be private',
    );
    for (const name of ['disposable-target', 'seed.json']) {
      const value = lstatSync(join(recovery, name));
      requireValue(
        value.isFile() &&
          !value.isSymbolicLink() &&
          (value.mode & 0o777) === 0o600 &&
          value.size <= (name === 'seed.json' ? 65536 : 64),
        'Recovery checkpoint input must be bounded and private',
      );
    }
    requireValue(
      readFileSync(join(recovery, 'disposable-target'), 'utf8').trim() ===
        'RCT_APP_RECOVERY_DISPOSABLE',
      'Recovery checkpoint ownership mismatch',
    );
    const checkpoint = JSON.parse(readFileSync(join(recovery, 'seed.json'), 'utf8'));
    requireValue(typeof checkpoint.historyUrl === 'string', 'Recovery semester path is required');
    const identity = /^\/api\/v1\/student\/attendance\?semesterId=([1-9]\d*)$/.exec(
      checkpoint.historyUrl,
    );
    requireValue(identity, 'Recovery checkpoint must identify its semester');
    const expectedSemesterId = Number(identity[1]);
    requireValue(Number.isSafeInteger(expectedSemesterId), 'Recovery semester identity is invalid');
    // Fail before MobileDataset can create or activate a fallback semester on a changed inventory.
    const datasetAdmin = new (class extends Actor {
      override async call(...args: Parameters<Actor['call']>) {
        requireValue(
          !(args[0] === 'POST' && args[1] === '/api/academic/semesters') &&
            !(args[0] === 'PATCH' && /^\/api\/academic\/semesters\/[^/]+\/activate$/.test(args[1])),
          'Semester fallback creation or activation is forbidden',
        );
        return super.call(...args);
      }
    })(request, admin.token, admin.session);
    const dataset = new MobileDataset(request, datasetAdmin, clock.instant);
    const date = dataset.today;
    // One-off slots support yesterday, including Sunday, within the known semester coverage.
    const pastDate = shift(date, -1);
    const futureDate = weekday(shift(date, 2));
    requireValue(original.length < 100, 'Existing dataset semester inventory must be complete');
    requireValue(
      original.every(
        (row) =>
          row.activationScheduled === false &&
          row.transition === 'NONE' &&
          row.releasePending === false,
      ),
      'Scheduled or transitioning semesters cannot share this archive fixture',
    );
    const active = original.filter((row) => row.active === true);
    requireValue(
      active.length === 1 &&
        Number(active[0].id) === expectedSemesterId &&
        active[0].archived === false &&
        active[0].dateFrom <= pastDate &&
        active[0].dateTo >= futureDate &&
        active[0].dateTo >= dataset.tomorrow,
      'Known active CI semester must cover the past lesson and future homework/cancellation',
    );
    previousActive = active[0];
    ownSemesterId = expectedSemesterId;
    originalAuthorityCaptured = true;
    await dataset.initialize();
    requireValue(
      Number(dataset.semester.id) === ownSemesterId,
      'Dataset must retain the known original CI semester',
    );
    const headman = dataset.headmanActor;
    const student = await admit(request, dataset.student, 'STUDENT');
    requireValue(
      dataset.subject.assignments.some(
        (assignment: Row) =>
          Number(assignment.semesterId) === ownSemesterId &&
          assignment.validFrom <= pastDate &&
          (assignment.validUntilExclusive == null || assignment.validUntilExclusive > pastDate),
      ),
      'Owned assignment must cover the past lesson',
    );
    const past = await headman.call(
      'POST',
      '/api/schedule/one-off-lessons',
      {
        groupId: dataset.group.id,
        subjectId: dataset.subject.id,
        assignmentId: dataset.subject.assignments[0].id,
        date: pastDate,
        lessonNumber: 1,
        classroom: 'Синтетическая историческая аудитория',
      },
      [201, 200],
      { 'Idempotency-Key': randomUUID() },
    );
    const lessonId = Number(past.physicalLessonId);
    requireValue(
      Number.isSafeInteger(lessonId) && lessonId > 0,
      'Past physical lesson is required',
    );
    const reportPath = `/api/attendance/reports/lesson/${lessonId}`;
    const markPath = `/api/attendance/lessons/${lessonId}/students/${dataset.student.id}`;
    const lessonsPath = `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${pastDate}&dateTo=${shift(futureDate, 1)}&size=200`;
    const lessonRows = async () =>
      Object.values((await headman.call('GET', lessonsPath))._embedded ?? {}).flat() as Row[];
    await expect
      .poll(
        async () =>
          (await lessonRows()).some(
            (row) => Number(row.id) === lessonId && row.status === 'CLOSED',
          ),
        { timeout: 90000, message: 'Owned historical lesson closes through the normal lifecycle' },
      )
      .toBe(true);
    // Schedule CLOSED precedes the asynchronous Attendance consumer. Wait for its durable mark
    // before taking a CAS snapshot, so AUTO ABSENT cannot race an EMPTY-to-PRESENT command.
    let initialMark: Row = {};
    await expect
      .poll(
        async () => {
          const report = await headman.call('GET', reportPath);
          const entry = report.entries.find(
            (row: Row) => Number(row.userId) === dataset.student.id,
          );
          requireValue(
            report.editable === true && entry,
            'Past owned student must be covered and writable',
          );
          initialMark = entry;
          return (
            initialMark.status === 'absent' &&
            typeof initialMark.markRevision === 'string' &&
            initialMark.markRevision.length > 0
          );
        },
        { timeout: 60000, message: 'Owned historical absence materializes through Attendance' },
      )
      .toBe(true);
    await headman.call('PUT', markPath, {
      status: 'PRESENT',
      expectedStatus: initialMark.status.toUpperCase(),
      expectedRevision: initialMark.markRevision,
    });
    const future = await headman.call(
      'POST',
      '/api/schedule/one-off-lessons',
      {
        groupId: dataset.group.id,
        subjectId: dataset.subject.id,
        assignmentId: dataset.subject.assignments[0].id,
        date: futureDate,
        lessonNumber: 2,
        classroom: 'Синтетическая сохранённая аудитория',
      },
      [201, 200],
      { 'Idempotency-Key': randomUUID() },
    );
    const historyPath = `/api/v1/student/attendance?semesterId=${ownSemesterId}`;
    const beforeHistory = history(await student.call('GET', historyPath));
    const canonicalHistory = (await lessonRows()).find((row) => Number(row.id) === lessonId);
    requireValue(
      canonicalHistory &&
        Number.isSafeInteger(Number(canonicalHistory.occurrenceId)) &&
        Number(canonicalHistory.occurrenceId) > 0,
      'Owned history requires a canonical occurrence',
    );
    requireValue(
      beforeHistory.some(
        (subject: Row) =>
          Number(subject.id) === Number(dataset.subject.id) &&
          subject.typeCards.some((card: Row) =>
            card.history.some(
              (segment: Row) =>
                segment.id === String(canonicalHistory.occurrenceId) &&
                segment.status === 'PRESENT',
            ),
          ),
      ),
      'Student history must retain the owned present occurrence identity',
    );
    const futureRow = (await lessonRows()).find(
      (row) => Number(row.id) === Number(future.physicalLessonId),
    );
    requireValue(
      Number.isSafeInteger(Number(future.id)) &&
        Number(future.id) > 0 &&
        futureRow &&
        futureRow.status === 'PLANNED' &&
        futureRow.current === true &&
        Number(futureRow.semesterId) === ownSemesterId &&
        Number(futureRow.groupId) === Number(dataset.group.id) &&
        Number(futureRow.subjectId) === Number(dataset.subject.id),
      'Denied cancellation must target the owned planned lesson',
    );
    const homeworkPath = `/api/academic/homeworks/${dataset.homework.id}`;
    const homeworkFields = [
      'id',
      'title',
      'description',
      'link',
      'revision',
      'bindingId',
      'publishedBy',
      'lessonDate',
      'lessonNumber',
      'subjectId',
      'groupId',
      'semesterId',
      'bindingMode',
      'lessonType',
    ];
    const beforeHomework = fields(await headman.call('GET', homeworkPath), homeworkFields);
    requireValue(
      Number.isSafeInteger(beforeHomework.revision) &&
        beforeHomework.revision > 0 &&
        Number(beforeHomework.semesterId) === ownSemesterId &&
        Number(beforeHomework.subjectId) === Number(dataset.subject.id) &&
        Number(beforeHomework.groupId) === Number(dataset.group.id) &&
        Number.isSafeInteger(Number(beforeHomework.bindingId)) &&
        Number(beforeHomework.bindingId) > 0,
      'Owned homework requires actual placement and revision',
    );
    const scheduleFields = [
      'id',
      'status',
      'current',
      'generation',
      'occurrenceId',
      'occurrenceRevision',
      'room',
      'revision',
      'date',
      'startTime',
      'endTime',
      'lessonNumber',
      'semesterId',
      'groupId',
      'subjectId',
    ];
    const scheduleProjection = async () =>
      (await lessonRows())
        .map((row) => fields(row, scheduleFields))
        .sort((a, b) => Number(a.id) - Number(b.id));
    const beforeSchedule = await scheduleProjection();
    const markFields = [
      'userId',
      'status',
      'markRevision',
      'excuseType',
      'comment',
      'attachmentId',
    ];
    const markProjection = async () => {
      const value = await headman.call('GET', reportPath);
      const entry = value.entries.find((row: Row) => Number(row.userId) === dataset.student.id);
      requireValue(
        entry && entry.status === 'present',
        'Owned historical mark must remain readable',
      );
      return fields(entry, markFields);
    };
    const beforeMark = await markProjection();
    requireValue(
      typeof beforeMark.markRevision === 'string' && beforeMark.markRevision.length > 0,
      'Owned mark requires an actual revision for the denied writer',
    );
    clock.assertUnchanged();
    archiveStarted = true;
    const archived = await terminal(admin, ownSemesterId, 'ARCHIVE', archiveKey);
    requireValue(
      (await semesters(admin)).every((row) => row.active === false),
      'Archive must not choose another active semester',
    );
    equal(
      history(await student.call('GET', historyPath)),
      beforeHistory,
      'Archive changed student history',
    );
    equal(await markProjection(), beforeMark, 'Archive changed the historical mark');
    equal(
      fields(await headman.call('GET', homeworkPath), homeworkFields),
      beforeHomework,
      'Archive changed homework contents',
    );
    const frozenReport = await headman.call('GET', reportPath);
    requireValue(frozenReport.editable === false, 'Archived headman journal must be read-only');
    const deniedHomework = {
      title: 'Запрещённое изменение синтетического задания',
      description: beforeHomework.description,
      link: beforeHomework.link,
      requestKey: randomUUID(),
      expectedRevision: beforeHomework.revision,
    };
    await conflict(request, headman, 'DELETE', `/api/schedule/one-off-lessons/${future.id}`);
    equal(
      await scheduleProjection(),
      beforeSchedule,
      'Archived Schedule cancellation had an effect',
    );
    await conflict(request, headman, 'PUT', markPath, {
      status: 'ABSENT',
      expectedStatus: 'PRESENT',
      expectedRevision: beforeMark.markRevision,
    });
    equal(await markProjection(), beforeMark, 'Archived Attendance writer had an effect');
    await conflict(request, headman, 'PUT', homeworkPath, deniedHomework);
    equal(
      fields(await headman.call('GET', homeworkPath), homeworkFields),
      beforeHomework,
      'Archived Homework writer had an effect',
    );
    equal(
      history(await student.call('GET', historyPath)),
      beforeHistory,
      'Archived refusals changed student history',
    );
    const afterRefusals = await admin.call(
      'GET',
      `/api/academic/semesters/${ownSemesterId}/archive/status`,
    );
    requireValue(
      afterRefusals.stateVersion === archived.stateVersion &&
        afterRefusals.operation.operationId === archived.operation.operationId,
      'Refused writers changed the archive authority',
    );
    await terminal(admin, ownSemesterId, 'RESTORE', restoreKey);
    const inactive = await admin.call('GET', `/api/academic/semesters/${ownSemesterId}`);
    requireValue(
      inactive.active === false &&
        inactive.activationScheduled === false &&
        inactive.archived === false,
      'Restore must leave the semester inactive and unscheduled',
    );
    requireValue(
      (await semesters(admin)).every((row) => row.active === false),
      'Restore must not activate another semester',
    );
    equal(
      history(await student.call('GET', historyPath)),
      beforeHistory,
      'Restore changed student history',
    );
    equal(await markProjection(), beforeMark, 'Restore changed historical attendance');
    equal(await scheduleProjection(), beforeSchedule, 'Restore changed canonical lessons');
    equal(
      fields(await headman.call('GET', homeworkPath), homeworkFields),
      beforeHomework,
      'Restore changed homework contents',
    );
    const current = await admin.call('PATCH', `/api/academic/semesters/${ownSemesterId}/activate`);
    requireValue(
      current.active === true && current.archived === false,
      'Explicit activation must restore current semester authority',
    );
    const changed = await headman.call('PUT', homeworkPath, {
      ...deniedHomework,
      title: 'Разрешённое изменение после восстановления',
      requestKey: randomUUID(),
    });
    requireValue(
      changed.id === beforeHomework.id &&
        changed.title === 'Разрешённое изменение после восстановления' &&
        changed.revision === beforeHomework.revision + 1,
      'Permitted homework mutation must advance exactly once',
    );
    equal(
      fields(await headman.call('GET', homeworkPath), homeworkFields),
      { ...beforeHomework, title: changed.title, revision: beforeHomework.revision + 1 },
      'Permitted homework mutation was not durably read back with unchanged placement',
    );
    equal(
      history(await student.call('GET', historyPath)),
      beforeHistory,
      'Permitted homework edit changed attendance history',
    );
  } catch (error) {
    retainFirstDatasetHttpFailure(info.annotations, error);
    throw error;
  } finally {
    try {
      if (admin && originalAuthorityCaptured && previousActive && ownSemesterId) {
        // This final CI case has a separate outer timeout; hard termination cannot run finally.
        // Short request deadlines bound ordinary failure recovery inside its cleanup reserve.
        const cleanupRequest = await playwright.request.newContext({
          baseURL,
          ignoreHTTPSErrors: false,
          timeout: 2000,
        });
        try {
          const cleanupAdmin = new Actor(cleanupRequest, admin.token, admin.session);
          const status = await cleanupAdmin.call(
            'GET',
            `/api/academic/semesters/${ownSemesterId}/archive/status`,
          );
          if (
            archiveStarted &&
            (status.archived === true ||
              status.transition !== 'NONE' ||
              status.releasePending === true)
          ) {
            if (
              status.operation?.action === 'ARCHIVE' &&
              status.operation.operationState !== 'COMPLETED'
            ) {
              await terminal(cleanupAdmin, ownSemesterId, 'ARCHIVE', archiveKey, 30000);
            }
            await terminal(cleanupAdmin, ownSemesterId, 'RESTORE', restoreKey, 60000);
          }
          const restored = await cleanupAdmin.call(
            'PATCH',
            `/api/academic/semesters/${previousActive.id}/activate`,
          );
          requireValue(
            restored.active === true &&
              restored.archived === false &&
              restored.activationScheduled === false,
            'Original active semester authority was not restored',
          );
          const active = (await semesters(cleanupAdmin)).filter((row) => row.active === true);
          requireValue(
            active.length === 1 && Number(active[0].id) === Number(previousActive.id),
            'Original active semester identity changed during cleanup',
          );
        } finally {
          await cleanupRequest.dispose();
        }
      }
    } finally {
      try {
        clock.assertUnchanged();
      } finally {
        await request.dispose();
      }
    }
  }
});
