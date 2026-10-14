import { createHash, randomUUID } from 'node:crypto';
import { chmodSync, existsSync, lstatSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
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
  pendingHeadman: RetainedActorProof;
  historyLessonId: number;
  historyUrl: string;
  attachmentPath: string;
  attachmentSha256: string;
  sourceLessonId: number;
}

interface RetainedActorProof {
  accessToken: string;
  sessionId: string;
}

interface RevocationWitness {
  userId: number;
  revoked: { accessToken: string; refreshToken: string; sessionId: string };
  control: { accessToken: string; sessionId: string };
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

// These are owned synthetic proofs, decoded only in RAM after real server admission.
// Decoding is not signature verification; the unchanged Gateway verifies the live control.
function proofClaims(token: string) {
  requireValue(typeof token === 'string' && token.length <= 16384, 'Invalid recovery proof');
  const parts = token.split('.');
  requireValue(parts.length === 3 && /^[A-Za-z0-9_-]+$/.test(parts[1]!), 'Invalid recovery proof');
  let claims: Record<string, unknown>;
  try {
    claims = JSON.parse(Buffer.from(parts[1]!, 'base64url').toString('utf8'));
  } catch {
    throw new Error('Invalid recovery proof claims');
  }
  requireValue(
    claims !== null &&
      typeof claims === 'object' &&
      typeof claims.iss === 'string' &&
      claims.iss.length > 0 &&
      typeof claims.sub === 'string' &&
      typeof claims.sid === 'string' &&
      typeof claims.iat === 'number' &&
      Number.isSafeInteger(claims.iat) &&
      typeof claims.exp === 'number' &&
      Number.isSafeInteger(claims.exp) &&
      claims.iat < claims.exp,
    'Invalid recovery proof envelope',
  );
  return claims;
}

function requireUnexpiredWitness(witness: RevocationWitness) {
  const access = proofClaims(witness.revoked.accessToken);
  const refresh = proofClaims(witness.revoked.refreshToken);
  const control = proofClaims(witness.control.accessToken);
  requireValue(
    witness.revoked.sessionId !== witness.control.sessionId &&
      access.sid === witness.revoked.sessionId &&
      refresh.sid === witness.revoked.sessionId &&
      control.sid === witness.control.sessionId &&
      [access, refresh, control].every(
        (proof) =>
          proof.iss === control.iss &&
          proof.sub === String(witness.userId) &&
          Number(proof.exp) * 1000 > Date.now(),
      ) &&
      Math.abs(Number(access.iat) - Number(control.iat)) <= 60,
    'Recovery revocation proofs must share an issuer and remain unexpired',
  );
}

async function retainActor(
  request: APIRequestContext,
  proof: RetainedActorProof,
  userId: number,
  role: 'HEADMAN' | 'STUDENT',
) {
  const claims = proofClaims(proof.accessToken);
  requireValue(
    claims.sub === String(userId) &&
      claims.sid === proof.sessionId &&
      Number(claims.exp) * 1000 > Date.now(),
    'Recovery actor proof must match its unexpired session',
  );
  // Decoded claims only check the checkpoint; the Gateway admits the retained proof.
  const response = await request.get('/api/auth/session', {
    headers: { Authorization: `Bearer ${proof.accessToken}` },
  });
  requireValue(response.status() === 200, 'Retained recovery actor was not admitted');
  const session = await response.json();
  requireValue(
    session.userId === String(userId) &&
      session.sessionId === proof.sessionId &&
      session.activeRole === role &&
      Number(claims.exp) * 1000 > Date.now(),
    'Retained recovery actor identity or active role changed',
  );
  return new Actor(request, proof.accessToken, session);
}

async function verifyRevocation(request: APIRequestContext, witness: RevocationWitness) {
  requireUnexpiredWitness(witness);
  const controlHeaders = { Authorization: `Bearer ${witness.control.accessToken}` };
  const controlBefore = await request.get('/api/auth/session', { headers: controlHeaders });
  requireValue(controlBefore.status() === 200, 'Retained recovery control was not admitted');
  const current = await controlBefore.json();
  requireValue(
    current.sessionId === witness.control.sessionId && current.userId === String(witness.userId),
    'Retained recovery control identity changed',
  );
  const deniedAccess = await request.get('/api/auth/session', {
    headers: { Authorization: `Bearer ${witness.revoked.accessToken}` },
  });
  requireValue(deniedAccess.status() === 401, 'Revoked recovery access was admitted');
  const deniedRefresh = await request.post('/api/auth/refresh', {
    headers: {
      Cookie: `rct_refresh=${witness.revoked.refreshToken}`,
      Origin: new URL(controlBefore.url()).origin,
    },
  });
  requireValue(deniedRefresh.status() === 401, 'Revoked recovery refresh was admitted');
  const controlAfter = await request.get('/api/auth/session', { headers: controlHeaders });
  requireValue(controlAfter.status() === 200, 'Retained recovery control stopped being admitted');
  requireUnexpiredWitness(witness);
}

function digest(bytes: Buffer) {
  return createHash('sha256').update(bytes).digest('hex');
}

// Only this closed projection may leave the private checkpoint; never serialize API rows/errors.
function lifecycleProjection(
  clockMatchesExpected: boolean,
  httpStatus: unknown,
  rows: unknown,
  targetId: number,
  expectedDate: string,
  expectedInstant: string,
) {
  const allowedHttp = [200, 400, 401, 403, 404, 409, 410, 422, 423, 429, 500, 502, 503, 504];
  const apiReadHttp =
    typeof httpStatus === 'number' && allowedHttp.includes(httpStatus) ? httpStatus : 0;
  const target =
    apiReadHttp === 200 && Array.isArray(rows)
      ? rows.find((row) => row && typeof row === 'object' && row.id === targetId)
      : undefined;
  const status = target?.status;
  const targetStatus = ['PLANNED', 'ACTIVE', 'CLOSED', 'CANCELLED'].includes(status)
    ? status
    : 'UNKNOWN';
  const end = target?.endTime;
  const validEnd =
    typeof end === 'string' &&
    /^(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9](?:\.[0-9]{1,3})?)?$/.test(end);
  const endMillis =
    validEnd && target.date === expectedDate ? Date.parse(`${expectedDate}T${end}+03:00`) : NaN;
  return {
    clockMatchesExpected: clockMatchesExpected === true,
    apiReadHttp,
    targetPresent: target !== undefined,
    targetStatus,
    current: target?.current === true,
    endPlus5IsDue: Number.isFinite(endMillis) && endMillis + 300000 <= Date.parse(expectedInstant),
  };
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
  const expectedInstant = `${dataset.today}T09:00:00.000Z`;
  const expectedClock = Buffer.from(`${expectedInstant}\n`);
  const temporaryClock = join(clockDirectory, `recovery-${randomUUID()}`);
  writeFileSync(temporaryClock, expectedClock, { flag: 'wx', mode: 0o644 });
  // The CI umask stays private; only this owned public educational clock is readable by service UIDs.
  chmodSync(temporaryClock, 0o644);
  renameSync(temporaryClock, clockFile);
  save(run, 'lifecycle-input.private.json', {
    lessonId: historyLessonId,
    semesterId: Number(dataset.semester.id),
    expectedInstant,
    clockSha256: digest(expectedClock),
  });
  const lessonRows = async (date: string) => {
    const response = await headman.call(
      'GET',
      `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${date}&dateTo=${date}&size=200`,
    );
    return Object.values(response._embedded ?? {}).flat() as Record<string, any>[];
  };
  try {
    await expect
      .poll(
        async () =>
          (await lessonRows(dataset.today)).some(
            (lesson) => Number(lesson.id) === historyLessonId && lesson.status === 'CLOSED',
          ),
        { timeout: 90000, message: 'Recovery historical lesson closes through the lifecycle job' },
      )
      .toBe(true);
  } catch (error) {
    // One bounded diagnostic read after failure; the original assertion remains authoritative.
    let httpStatus = 0;
    let rows: unknown = [];
    let clockMatchesExpected = false;
    try {
      const response = await request.get(
        `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${dataset.today}&dateTo=${dataset.today}&size=200`,
        { headers: headman.headers(), timeout: 1000 },
      );
      httpStatus = response.status();
      if (httpStatus === 200) {
        const body = await response.json();
        rows = Object.values(body._embedded ?? {}).flat();
      }
    } catch {
      // No raw network error or server body enters the diagnostic.
    }
    try {
      const stat = lstatSync(clockFile);
      clockMatchesExpected =
        stat.isFile() &&
        !stat.isSymbolicLink() &&
        stat.size <= 128 &&
        digest(readFileSync(clockFile)) === digest(expectedClock);
    } catch {
      // An unreadable or replaced clock is a closed mismatch.
    }
    try {
      save(
        join(run, 'artifacts', 'seed'),
        'lifecycle.safe.json',
        lifecycleProjection(
          clockMatchesExpected,
          httpStatus,
          rows,
          historyLessonId,
          dataset.today,
          expectedInstant,
        ),
      );
    } catch {
      // Artifact failure must not replace the original check failure.
    }
    throw error;
  }

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
  // Issue near backup, after the slow educational/history setup. Only the selected
  // session is revoked; the second session proves restart did not merely expire
  // all old proofs or replace the signing identity. Security clocks stay real.
  const revoked = await admit(request, dataset.student, 'STUDENT');
  const cookie = (await request.storageState()).cookies.find(
    (value) => value.name === 'rct_refresh',
  );
  requireValue(cookie?.value, 'Recovery revocation requires its own refresh proof');
  const control = await admit(request, dataset.student, 'STUDENT');
  const witness: RevocationWitness = {
    userId: dataset.student.id,
    revoked: {
      accessToken: revoked.token,
      refreshToken: cookie.value,
      sessionId: revoked.session.sessionId,
    },
    control: { accessToken: control.token, sessionId: control.session.sessionId },
  };
  requireUnexpiredWitness(witness);
  await control.call('DELETE', `/api/auth/sessions/${witness.revoked.sessionId}`, undefined, [204]);
  await verifyRevocation(request, witness);
  save(run, 'auth-revocation.private.json', witness);
  // Prepare the pending actors while Rabbit is up. Pending keeps its 60s budget
  // for the genuine outage operation, without repeating login's allowed retry wait.
  const pendingHeadman: RetainedActorProof = {
    accessToken: headman.token,
    sessionId: headman.session.sessionId,
  };
  await retainActor(request, pendingHeadman, dataset.headman.id, 'HEADMAN');
  await retainActor(request, witness.control, dataset.student.id, 'STUDENT');
  save(run, 'seed.json', {
    headman: dataset.headman,
    student: dataset.student,
    pendingHeadman,
    historyLessonId,
    historyUrl: `/api/v1/student/attendance?semesterId=${dataset.semester.id}`,
    attachmentPath,
    attachmentSha256: digest(png),
    sourceLessonId,
  });
  privateFile(join(run, 'seed.json'), 65536);
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
    seed: [
      'seed.json',
      'request.json',
      'lifecycle-input.private.json',
      'auth-revocation.private.json',
    ],
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
    privateFile(join(run, 'seed.json'), 65536);
    const checkpoint = load<Seed>(run, 'seed.json');
    if (phase === 'pending') {
      // CI has stopped and checked the owned Rabbit process before this invocation.
      // The subsequent immutable capture proves all participant receipts absent, not just this API status.
      const headman = await retainActor(
        request,
        checkpoint.pendingHeadman,
        checkpoint.headman.id,
        'HEADMAN',
      );
      privateFile(join(run, 'auth-revocation.private.json'), 65536);
      const witness = load<RevocationWitness>(run, 'auth-revocation.private.json');
      requireUnexpiredWitness(witness);
      requireValue(witness.userId === checkpoint.student.id, 'Recovery student identity changed');
      const student = await retainActor(request, witness.control, checkpoint.student.id, 'STUDENT');
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
      // CI has restarted the same Auth container/signing volume on restored data.
      // Check the exact pre-backup proofs before creating any fresh sessions.
      privateFile(join(run, 'auth-revocation.private.json'), 65536);
      await verifyRevocation(request, load<RevocationWitness>(run, 'auth-revocation.private.json'));
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
