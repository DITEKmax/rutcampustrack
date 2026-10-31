import { closeSync, constants, existsSync, fstatSync, fsyncSync, lstatSync, openSync, readSync, realpathSync, writeFileSync, ftruncateSync, writeSync } from 'node:fs';
import { join } from 'node:path';
import { test, requirePrivateArtifacts } from './fixtures/artifact-privacy';
import { MobileDataset, admit, expect } from './fixtures/mobile-dataset';
import { requireLocalSpecialLane } from './fixtures/local-special';
import { readTeachingInstant } from './fixtures/teaching-clock';
import { TEST_USERS } from './fixtures/users';
import { closedStartupFailure, classifySafeFailure } from './fixtures/safe-reporter';
import { observePreparationTransport } from './fixtures/preparation-transport';
import type { APIRequestContext } from '@playwright/test';

function requireValue(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function ownedDirectory(path: string, mode = 0o700) {
  const info = lstatSync(path);
  requireValue(info.isDirectory() && !info.isSymbolicLink() && info.uid === process.getuid?.() &&
    (info.mode & 0o777) === mode && realpathSync(path) === path,
  'Lifecycle preparation directory ownership mismatch');
  return [info.dev, info.ino, info.mode, info.uid];
}

function ownedFile(path: string, maximum: number, mode = 0o600) {
  const before = lstatSync(path);
  const descriptor = openSync(path, constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK);
  try {
    const info = fstatSync(descriptor);
    const identity = (item: typeof info) => [item.dev, item.ino, item.mode, item.uid, item.size, item.mtimeMs, item.ctimeMs];
    requireValue(info.isFile() && !before.isSymbolicLink() && info.nlink === 1 &&
      info.uid === process.getuid?.() && (info.mode & 0o777) === mode &&
      info.size > 0 && info.size <= maximum && JSON.stringify(identity(before)) === JSON.stringify(identity(info)),
    'Lifecycle preparation file ownership mismatch');
    const buffer = Buffer.alloc(maximum + 1);
    const count = readSync(descriptor, buffer, 0, buffer.length, 0);
    requireValue(count === info.size && JSON.stringify(identity(info)) === JSON.stringify(identity(fstatSync(descriptor))) &&
      JSON.stringify(identity(info)) === JSON.stringify(identity(lstatSync(path))),
    'Lifecycle preparation file changed while reading');
    return { bytes: buffer.subarray(0, count), identity: identity(info) };
  } finally {
    closeSync(descriptor);
  }
}


type PreparationPhase = 'CONTEXT' | 'HTTP_READINESS' | 'AUTH_INTENT' | 'ADMIN_ADMISSION' | 'FRESH_BASELINE' | 'CANONICAL_INITIALIZATION' | 'ORIGINAL_VERIFICATION' | 'COMPLETED';
type Progress = (phase: PreparationPhase, status: 'RUNNING' | 'SUCCEEDED', httpStatus?: number) => void;

// Exactly one nonmutating full-response check in the context subsequently used by canonical admit.
async function sameContextAdmission<T>(request: Pick<APIRequestContext, 'get'>, progress: Progress,
  recordAttempt: () => void, canonicalAdmit: () => Promise<T>): Promise<T> {
  progress('HTTP_READINESS', 'RUNNING');
  const response = await request.get('/api/auth/session', { maxRedirects: 0 });
  const status = response.status();
  progress('HTTP_READINESS', 'RUNNING', status);
  await response.body();
  requireValue(status === 401, 'Anonymous same-client session readiness must return401');
  progress('HTTP_READINESS', 'SUCCEEDED', status);
  progress('AUTH_INTENT', 'RUNNING');
  recordAttempt();
  progress('AUTH_INTENT', 'SUCCEEDED');
  progress('ADMIN_ADMISSION', 'RUNNING'); // Must be durably recorded before a mutating admit.
  const admin = await canonicalAdmit();
  progress('ADMIN_ADMISSION', 'SUCCEEDED');
  return admin;
}

test('fresh local lifecycle prerequisites use the canonical FIRST3 dataset', async ({ playwright }, info) => {
  requirePrivateArtifacts(info);
  requireValue(process.platform === 'linux' && process.env.RCT_LOCAL_SPECIAL_PREPARATION === 'true' &&
    process.env.RCT_LOCAL_SPECIAL_COHORT === 'lifecycle' && process.env.RCT_LOCAL_SPECIAL_PHASE === 'main' &&
    !process.env.RCT_LOCAL_SPECIAL_DISCOVERY && process.env.RCT_E2E_SYNTHETIC_SEED === 'true' &&
    process.env.RCT_E2E_ASYNC_REPORT === 'false', 'Lifecycle prerequisites require a fresh explicit synthetic preparation');
  const context = requireLocalSpecialLane('semester-metadata');
  const preparation = join(context.run, 'prerequisites');
  const artifacts = join(preparation, 'artifacts');
  ownedDirectory(context.run);
  ownedDirectory(preparation);
  ownedDirectory(artifacts);
  requireValue(info.project.outputDir === artifacts && info.project.use.baseURL === 'https://localhost:8443',
    'Lifecycle preparation output and strict loopback origin must match admission');
  const owner = JSON.parse(ownedFile(join(context.run, 'owner.json'), 8 * 1024 ** 2).bytes.toString('utf8'));
  requireValue(owner.root === context.root && owner.run === context.run && owner.project === context.project &&
    owner.nonce === context.nonce && owner.uid === process.getuid?.() && owner.mode === 'special13' &&
    owner.special_cohort === 'lifecycle' && typeof owner.source_sha === 'string' && /^[a-f0-9]{40}$/.test(owner.source_sha),
  'Lifecycle preparation source and run must remain admitted');
  const clockDirectory = join(context.run, 'clock');
  requireValue(process.env.RCT_E2E_TEACHING_CLOCK_DIR === clockDirectory,
    'Lifecycle preparation uses only the original educational clock');
  const snapshot = () => ({
    directory: ownedDirectory(clockDirectory, 0o755),
    marker: ownedFile(join(clockDirectory, 'disposable-target'), 64, 0o644),
    instant: ownedFile(join(clockDirectory, 'current-instant'), 128, 0o644),
  });
  const clock = snapshot();
  const instant = readTeachingInstant(clockDirectory);
  const unchanged = () => requireValue(JSON.stringify(snapshot()) === JSON.stringify(clock),
    'Lifecycle prerequisites must leave the educational clock unchanged');
  const attempt = join(preparation, 'preparation-attempt.private.json');
  requireValue(!existsSync(attempt) && !existsSync(join(preparation, 'preparation.safe.json')),
    'Lifecycle preparation must not retry a previously attempted run');
  const progressPath = join(preparation, 'http-progress.safe.json');
  const progressDescriptor = openSync(progressPath, constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600);
  const started = performance.now();
  const diagnostic = { schema: 'RCT_LIFECYCLE_HTTP_PROGRESS_V1', sourceRevision: owner.source_sha as string,
    phase: 'CONTEXT' as PreparationPhase, status: 'RUNNING' as 'RUNNING' | 'PASSED' | 'FAILED',
    elapsedMs: 0, httpStatus: null as number | null, errorClass: 'UNKNOWN', transportCode: 'UNKNOWN',
    transport: { schema: 'RCT_PREPARATION_TRANSPORT_V1', status: 'UNAVAILABLE', rows: [], truncated: false, observerFailures: 0,
      qualification: 'EVENTS_ARE_OBSERVATIONS_NOT_SERVER_RECEIPT_OR_AUTH_COMMIT; ABSENT_EVENT_IS_UNKNOWN' } as ReturnType<ReturnType<typeof observePreparationTransport>['stop']>,
    steps: [] as { phase: PreparationPhase; status: 'RUNNING' | 'SUCCEEDED'; elapsedMs: number; httpStatus: number | null }[] };
  const persist = () => {
    diagnostic.elapsedMs = Math.max(0, Math.min(180000, Math.trunc(performance.now() - started)));
    const data = Buffer.from(`${JSON.stringify(diagnostic)}\n`);
    requireValue(data.length <= 16384, 'Preparation HTTP diagnostic must remain bounded');
    ftruncateSync(progressDescriptor, 0);
    requireValue(writeSync(progressDescriptor, data, 0, data.length, 0) === data.length, 'Preparation HTTP diagnostic write must complete');
    fsyncSync(progressDescriptor);
  };
  const progress: Progress = (phase, status, httpStatus) => {
    if (phase !== diagnostic.phase) diagnostic.httpStatus = null;
    diagnostic.phase = phase;
    diagnostic.httpStatus = httpStatus ?? diagnostic.httpStatus;
    diagnostic.steps.push({ phase, status, elapsedMs: Math.max(0, Math.min(180000, Math.trunc(performance.now() - started))), httpStatus: httpStatus ?? null });
    persist();
  };
  let request: APIRequestContext | undefined;
  let primaryFailed = false;
  try {
    progress('CONTEXT', 'RUNNING');
    request = await playwright.request.newContext({
      baseURL: 'https://localhost:8443', ignoreHTTPSErrors: false, timeout: 10000,
    });
    progress('CONTEXT', 'SUCCEEDED');
    let transport: ReturnType<typeof observePreparationTransport> | undefined;
    try { transport = observePreparationTransport(); } catch { /* Optional observation cannot replace an HTTP outcome. */ }
    const observedProgress: Progress = (phase, status, code) => {
      if (phase === 'HTTP_READINESS') transport?.enter('READINESS');
      else if (phase === 'ADMIN_ADMISSION') transport?.enter('ADMIN_ADMISSION');
      progress(phase, status, code);
    };
    let admin: Awaited<ReturnType<typeof admit>>;
    try { admin = await sameContextAdmission(request, observedProgress, () => {
      const attemptDescriptor = openSync(attempt, constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600);
      try {
        writeFileSync(attemptDescriptor, `${JSON.stringify({ schema: 'RCT_LOCAL_LIFECYCLE_PREPARATION_ATTEMPT_V1', sourceRevision: owner.source_sha })}\n`);
        fsyncSync(attemptDescriptor);
      } finally { closeSync(attemptDescriptor); }
    }, () => admit(request!, TEST_USERS.admin, 'ADMIN')); }
    finally { if (transport) diagnostic.transport = transport.stop(); }
    progress('FRESH_BASELINE', 'RUNNING');
    const before = await admin.call('GET', '/api/academic/semesters?size=100');
    const baseline = before._embedded?.semesterResponseList;
    requireValue(Array.isArray(baseline) && baseline.length === 1 && before.page?.totalElements === 1 && before.page.number === 0 &&
      Number(baseline[0].id) === 1 && baseline[0].name === 'Spring 2026' && baseline[0].dateFrom === '2026-02-01' &&
      baseline[0].dateTo === '2026-06-30' && baseline[0].semesterType === null && baseline[0].academicYear === null &&
      baseline[0].active === true && baseline[0].archived === false && baseline[0].transition === 'NONE' &&
      baseline[0].releasePending === false && baseline[0].activationScheduled === false &&
      (baseline[0].stateVersion === 0 || baseline[0].stateVersion === '0'),
    'Lifecycle prerequisites require the unchanged fresh Academic baseline');
    progress('FRESH_BASELINE', 'SUCCEEDED');
    progress('CANONICAL_INITIALIZATION', 'RUNNING');
    // The canonical FIRST3 initializer is the sole writer of its dataset and accepted API protocol.
    const dataset = new MobileDataset(request, admin, instant);
    await dataset.initialize();
    progress('CANONICAL_INITIALIZATION', 'SUCCEEDED');
    progress('ORIGINAL_VERIFICATION', 'RUNNING');
    // Match activeHeadmanDataset: the canonical scheduler must settle the owned lesson first.
    await expect
      .poll(
        async () => {
          const body = await dataset.headmanActor.call(
            'GET',
            `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${dataset.today}&dateTo=${dataset.today}&size=100`,
          );
          const lessons = body._embedded?.lessonResponseList ?? body.content ?? [];
          const lesson = lessons.find(
            (row: Record<string, unknown>) => Number(row.id) === Number(dataset.lesson.physicalLessonId),
          );
          return lesson?.status === 'ACTIVE' && lesson.current === true;
        },
        { timeout: 90000, intervals: [1000], message: 'Own HEADMAN lesson becomes active' },
      )
      .toBe(true);
    const inventory = await admin.call('GET', '/api/academic/semesters?size=100');
    const semesters = inventory._embedded?.semesterResponseList;
    requireValue(Array.isArray(semesters) && semesters.length <= 100 && inventory.page?.number === 0 &&
      inventory.page.totalElements === semesters.length, 'Prepared semester inventory must be complete');
    const active = semesters.filter((row: Record<string, unknown>) => row.active === true);
    const yesterday = new Date(Date.parse(`${dataset.today}T12:00:00Z`) - 86400000).toISOString().slice(0, 10);
    requireValue(active.length === 1 && Number(active[0].id) === Number(dataset.semester.id) &&
      active[0].archived === false && active[0].transition === 'NONE' && active[0].releasePending === false &&
      active[0].activationScheduled === false && active[0].dateFrom <= yesterday && active[0].dateTo >= dataset.tomorrow,
    'Prepared active semester must cover the actual stories past and future dates');
    const subject = await dataset.headmanActor.call('GET', `/api/academic/subjects/${dataset.subject.id}`);
    requireValue(Array.isArray(subject.assignments) && subject.assignments.some((row: Record<string, unknown>) =>
      Number(row.semesterId) === Number(dataset.semester.id) && Number(row.teacherId) === dataset.teacher.id),
    'Canonical prerequisite assignment must be authoritatively published');
    const preview = await admin.call('GET', `/api/academic/semesters/${dataset.semester.id}/delete-preview`);
    const keys = ['scheduleTemplates', 'oneOffLessons', 'lessons', 'assignments', 'homeworks', 'attendanceMarks', 'studentRequests'];
    requireValue(Number(preview.semesterId) === Number(dataset.semester.id) && preview.priorState === 'ACTIVE' &&
      preview.stateVersion === active[0].stateVersion && preview.counts &&
      keys.every(key => Number.isSafeInteger(preview.counts[key]) && preview.counts[key] >= 0) &&
      keys.some(key => preview.counts[key] > 0), 'Prepared semester must have genuine numeric deletion consequences');
    unchanged();
    requireLocalSpecialLane('semester-metadata');
    const receipt = {
      schema: 'RCT_LOCAL_LIFECYCLE_PREREQUISITES_V1', sourceRevision: owner.source_sha,
      calendar: { active: true, dateFromYesterday: true, dateToTomorrow: true },
      preview: { nonzero: true }, clockUnchanged: true, status: 'PREPARED', datasetInitialized: true,
    };
    const descriptor = openSync(join(preparation, 'preparation.safe.json'),
      constants.O_WRONLY | constants.O_CREAT | constants.O_EXCL | constants.O_NOFOLLOW, 0o600);
    try {
      writeFileSync(descriptor, `${JSON.stringify(receipt)}\n`);
      fsyncSync(descriptor);
    } finally {
      closeSync(descriptor);
    }
    progress('ORIGINAL_VERIFICATION', 'SUCCEEDED');
    progress('COMPLETED', 'SUCCEEDED');
    diagnostic.status = 'PASSED';
    persist();
  } catch (error) {
    primaryFailed = true;
    diagnostic.status = 'FAILED';
    const failure = closedStartupFailure({ error });
    diagnostic.errorClass = failure.reportedErrorClass;
    diagnostic.transportCode = failure.reportedTransportCode;
    const status = classifySafeFailure(error).httpStatus;
    if (typeof status === 'number' && status >= 100 && status <= 599) diagnostic.httpStatus = status;
    try { persist(); } catch { /* Keep the original failure; the exclusive attempt remains. */ }
    throw error;
  } finally {
    let cleanupFailed = false;
    let cleanupError: unknown;
    try { unchanged(); } catch (error) { cleanupFailed = true; cleanupError = error; }
    try { await request?.dispose(); } catch (error) { cleanupFailed = true; cleanupError ??= error; }
    if (!primaryFailed && cleanupFailed) {
      diagnostic.status = 'FAILED';
      const failure = closedStartupFailure({ error: cleanupError });
      diagnostic.errorClass = failure.reportedErrorClass;
      diagnostic.transportCode = failure.reportedTransportCode;
      try { persist(); } catch { /* Preserve the original cleanup failure. */ }
    }
    try { closeSync(progressDescriptor); } catch (error) { cleanupFailed = true; cleanupError ??= error; }
    if (!primaryFailed && cleanupFailed) throw cleanupError;
  }
});
