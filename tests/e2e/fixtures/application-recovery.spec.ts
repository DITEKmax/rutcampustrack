import { createHash, randomUUID } from 'node:crypto';
import { existsSync, lstatSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { isAbsolute, join } from 'node:path';
import { type APIRequestContext, expect } from '@playwright/test';
import { test, requirePrivateArtifacts } from './artifact-privacy';
import { Actor, MobileDataset, admit, type SyntheticUser } from './mobile-dataset';
import { readTeachingInstant } from './teaching-clock';
import { TEST_USERS } from './users';

// Docker lifecycle and native backup/capture/restore/verify remain explicit CI operations.
// Each invocation performs exactly one API phase, with no browser or automatic retry.
const phase = process.env.RCT_APP_RECOVERY_PHASE;
const phases = ['seed', 'pending', 'admit', 'attachment'];
const png = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF5kAAAAASUVORK5CYII=',
  'base64',
);

interface Seed {
  headman: SyntheticUser;
  student: SyntheticUser;
  historyLessonId: number;
  historyUrl: string;
  attachmentPath: string;
  attachmentSha256: string;
  sourceLessonId: number;
}

function requireValue(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function privateFile(path: string, maxBytes: number) {
  const info = lstatSync(path);
  requireValue(
    info.isFile() &&
      !info.isSymbolicLink() &&
      (info.mode & 0o777) === 0o600 &&
      info.size <= maxBytes,
    'Recovery input must be a bounded private regular file',
  );
}

function load<T>(run: string, name: string): T {
  const path = join(run, name);
  privateFile(path, 3 * 1024 * 1024);
  return JSON.parse(readFileSync(path, 'utf8')) as T;
}

function save(run: string, name: string, value: unknown) {
  writeFileSync(join(run, name), JSON.stringify(value), { flag: 'wx', mode: 0o600 });
}

function saveToken(run: string, name: string, actor: Actor) {
  writeFileSync(join(run, name), actor.token, { flag: 'wx', mode: 0o600 });
}

function digest(bytes: Buffer) {
  return createHash('sha256').update(bytes).digest('hex');
}

function shift(date: string, days: number) {
  return new Date(Date.parse(`${date}T12:00:00Z`) + days * 86400000).toISOString().slice(0, 10);
}

function studyDay(date: string) {
  return new Date(`${date}T12:00:00Z`).getUTCDay() === 0 ? shift(date, 1) : date;
}

async function download(request: APIRequestContext, actor: Actor, path: string, sha256: string) {
  const response = await request.get(path, { headers: actor.headers() });
  requireValue(response.status() === 200, 'Recovery attachment download failed');
  requireValue(
    response.headers()['content-type']?.includes('image/png'),
    'Recovery attachment content type changed',
  );
  const bytes = await response.body();
  requireValue(
    bytes.length === png.length && digest(bytes) === sha256,
    'Recovery attachment bytes changed',
  );
}

async function seed(request: APIRequestContext, run: string) {
  const clockDirectory = process.env.RCT_E2E_TEACHING_CLOCK_DIR;
  requireValue(clockDirectory, 'Recovery requires an owned educational clock');
  requireValue(!lstatSync(clockDirectory).isSymbolicLink(), 'Recovery clock directory is a link');
  const clockFile = join(clockDirectory, 'current-instant');
  requireValue(!lstatSync(clockFile).isSymbolicLink(), 'Recovery clock file is a link');
  requireValue(
    !lstatSync(join(clockDirectory, 'disposable-target')).isSymbolicLink(),
    'Recovery clock marker is a link',
  );
  const instant = readTeachingInstant(clockDirectory);
  const dataset = new MobileDataset(
    request,
    await admit(request, TEST_USERS.admin, 'ADMIN'),
    instant,
  );
  await dataset.initialize();
  const headman = dataset.headmanActor;
  const historyLessonId = Number(dataset.lesson.physicalLessonId);
  requireValue(
    Number.isSafeInteger(historyLessonId) && historyLessonId > 0,
    'Missing physical lesson',
  );
  const reportPath = `/api/attendance/reports/lesson/${historyLessonId}`;
  const report = await headman.call('GET', reportPath);
  const row = report.entries.find(
    (entry: { userId: number }) => entry.userId === dataset.student.id,
  );
  requireValue(
    report.editable === true && row && row.markRevision == null,
    'Historical lesson must be writable and unmarked before closing',
  );
  const markPath = `/api/attendance/lessons/${historyLessonId}/students/${dataset.student.id}`;
  const marked = await request.put(markPath, {
    headers: headman.headers(),
    multipart: {
      request: {
        name: 'request.json',
        mimeType: 'application/json',
        buffer: Buffer.from(
          JSON.stringify({
            status: 'EXCUSED',
            excuseType: 'ILLNESS',
            comment: 'Синтетическая проверка восстановления',
            expectedStatus: 'EMPTY',
            expectedRevision: 'EMPTY',
          }),
        ),
      },
      file: { name: 'синтетическая-справка.png', mimeType: 'image/png', buffer: png },
    },
  });
  requireValue(marked.status() === 200, 'Historical attachment mark failed');
  const attachmentPath = `${markPath}/attachment`;
  await download(request, headman, attachmentPath, digest(png));

  // The existing 60s lifecycle job closes the genuine marked lesson. Security clocks stay real.
  const temporaryClock = join(clockDirectory, `recovery-${randomUUID()}`);
  writeFileSync(temporaryClock, `${dataset.today}T09:00:00.000Z\n`, { flag: 'wx', mode: 0o644 });
  renameSync(temporaryClock, clockFile);
  const lessonRows = async (date: string) => {
    const response = await headman.call(
      'GET',
      `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${date}&dateTo=${date}&size=200`,
    );
    return Object.values(response._embedded ?? {}).flat() as Record<string, any>[];
  };
  await expect
    .poll(
      async () =>
        (await lessonRows(dataset.today)).some(
          (lesson) => Number(lesson.id) === historyLessonId && lesson.status === 'CLOSED',
        ),
      { timeout: 90000, message: 'Recovery historical lesson closes through the lifecycle job' },
    )
    .toBe(true);

  // Keep this binding separate from MobileDataset's already published DATE-mode homework.
  const sourceDate = studyDay(shift(dataset.tomorrow, 1));
  const targetDate = studyDay(shift(sourceDate, 1));
  requireValue(
    targetDate <= dataset.semester.dateTo,
    'Recovery future slots must stay inside the semester',
  );
  const source = await headman.call(
    'POST',
    '/api/schedule/one-off-lessons',
    {
      groupId: dataset.group.id,
      subjectId: dataset.subject.id,
      assignmentId: dataset.subject.assignments[0].id,
      date: sourceDate,
      lessonNumber: 2,
      classroom: 'Синтетическая проверка восстановления',
    },
    [201, 200],
    { 'Idempotency-Key': randomUUID() },
  );
  const sourceLessonId = Number(source.physicalLessonId);
  const physical = (await lessonRows(sourceDate)).find(
    (lesson) => Number(lesson.id) === sourceLessonId,
  );
  requireValue(
    physical?.current === true && physical.status === 'PLANNED',
    'Future physical source is missing',
  );
  const homeworkRequest = {
    title: dataset.marker,
    description: 'Синтетическое ДЗ для переноса после восстановления',
    link: null,
    groupId: dataset.group.id,
    subjectId: dataset.subject.id,
    semesterId: dataset.semester.id,
    lessonDate: sourceDate,
    lessonNumber: 2,
    bindingMode: 'LESSON',
    lessonType: 'LECTURE',
    requestKey: randomUUID(),
  };
  await expect
    .poll(
      async () => {
        const homework = await headman.call(
          'POST',
          '/api/academic/homeworks',
          homeworkRequest,
          [201, 200, 202],
        );
        return homework.state !== 'PENDING' && homework.id != null && homework.bindingId != null;
      },
      { timeout: 60000, message: 'Recovery real lesson-bound homework becomes active' },
    )
    .toBe(true);
  save(run, 'request.json', {
    requestKey: randomUUID(),
    expectedRevision: String(physical.occurrenceRevision),
    targetDate,
    targetLessonNumber: 3,
    targetRoom: 'Синтетическая новая аудитория',
  });
  save(run, 'seed.json', {
    headman: dataset.headman,
    student: dataset.student,
    historyLessonId,
    historyUrl: `/api/v1/student/attendance?semesterId=${dataset.semester.id}`,
    attachmentPath,
    attachmentSha256: digest(png),
    sourceLessonId,
  });
}

test('application recovery API phase', async ({ playwright }, info) => {
  requirePrivateArtifacts(info);
  requireValue(phase && phases.includes(phase), 'Recovery phase must be explicit');
  test.setTimeout(phase === 'seed' ? 300000 : 60000);
  const run = process.env.RCT_APP_RECOVERY_RUN;
  requireValue(run && isAbsolute(run), 'Recovery requires an absolute private run directory');
  for (const path of [run, join(run, 'artifacts'), join(run, 'artifacts', phase)]) {
    const stat = lstatSync(path);
    requireValue(
      stat.isDirectory() && !stat.isSymbolicLink() && (stat.mode & 0o777) === 0o700,
      'Recovery directory must be private',
    );
  }
  const marker = join(run, 'disposable-target');
  privateFile(marker, 64);
  requireValue(
    readFileSync(marker, 'utf8').trim() === 'RCT_APP_RECOVERY_DISPOSABLE',
    'Recovery ownership marker mismatch',
  );
  requireValue(
    info.project.outputDir === join(run, 'artifacts', phase),
    'Recovery artifacts must stay in the phase directory',
  );
  const phaseOutputs: Record<string, string[]> = {
    seed: ['seed.json', 'request.json'],
    pending: ['pending.json', 'history-before.json'],
    admit: ['actor-token.private', 'history-token.private'],
    attachment: [],
  };
  requireValue(
    phaseOutputs[phase].every((name) => !existsSync(join(run, name))),
    'Recovery phase outputs must be new',
  );
  const baseURL = String(info.project.use.baseURL);
  const origin = new URL(baseURL);
  requireValue(
    process.env.RCT_E2E_SYNTHETIC_SEED === 'true' &&
      origin.protocol === 'https:' &&
      origin.hostname === 'localhost' &&
      origin.port === '8443',
    'Recovery requires explicit disposable HTTPS localhost:8443',
  );
  // Root supplies the generated public CA through NODE_EXTRA_CA_CERTS; never ignore TLS errors.
  const request = await playwright.request.newContext({
    baseURL,
    ignoreHTTPSErrors: false,
    timeout: 10000,
  });
  try {
    if (phase === 'seed') {
      await seed(request, run);
      return;
    }
    const checkpoint = load<Seed>(run, 'seed.json');
    if (phase === 'pending') {
      // CI has stopped and checked the owned Rabbit process before this invocation.
      // The subsequent immutable capture proves all participant receipts absent, not just this API status.
      const headman = await admit(request, checkpoint.headman, 'HEADMAN');
      const student = await admit(request, checkpoint.student, 'STUDENT');
      const history = await student.call('GET', checkpoint.historyUrl);
      const subjectIndex = history.subjects.findIndex(
        (subject: { typeCards: { history: unknown[] }[] }) =>
          subject.typeCards.some((card) => card.history.length > 0),
      );
      requireValue(subjectIndex >= 0, 'Recovery requires real nonempty student history');
      const cardIndex = history.subjects[subjectIndex].typeCards.findIndex(
        (card: { history: unknown[] }) => card.history.length > 0,
      );
      save(run, 'history-before.json', history);
      const accepted = await headman.call(
        'POST',
        `/api/schedule/lessons/${checkpoint.sourceLessonId}/transfer`,
        load(run, 'request.json'),
        [202],
      );
      requireValue(accepted.state === 'PENDING', 'Transfer must be pending before checkpoint');
      save(run, 'pending.json', {
        operationId: accepted.operationId,
        historyLessonId: checkpoint.historyLessonId,
        historyPointer: `/subjects/${subjectIndex}/typeCards/${cardIndex}/history`,
        historyUrl: checkpoint.historyUrl,
      });
      return;
    }
    if (phase === 'admit') {
      const headman = await admit(request, checkpoint.headman, 'HEADMAN');
      const student = await admit(request, checkpoint.student, 'STUDENT');
      saveToken(run, 'actor-token.private', headman);
      saveToken(run, 'history-token.private', student);
      await download(request, headman, checkpoint.attachmentPath, checkpoint.attachmentSha256);
      return;
    }
    const tokenFile = join(run, 'actor-token.private');
    privateFile(tokenFile, 16384);
    const token = readFileSync(tokenFile, 'utf8');
    // Use the same admitted actor after the existing Python verifier's one exact replay.
    const response = await request.get(checkpoint.attachmentPath, {
      headers: { Authorization: `Bearer ${token}` },
    });
    requireValue(
      response.status() === 200 && response.headers()['content-type']?.includes('image/png'),
      'Restored attachment unavailable after exact replay',
    );
    const bytes = await response.body();
    requireValue(
      bytes.length === png.length && digest(bytes) === checkpoint.attachmentSha256,
      'Replay changed restored attachment bytes',
    );
  } finally {
    await request.dispose();
  }
});
