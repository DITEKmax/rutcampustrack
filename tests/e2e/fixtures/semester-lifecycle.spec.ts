import { localSpecialLane, localSpecialService, type LocalSpecialLane } from './local-special';
import { diagnosticCase } from './diagnostic-events';
import { terminal as observeTerminal, assertOwnedSemesterUi } from './semester-lifecycle-observers';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { lstatSync, readFileSync, writeFileSync } from 'node:fs';
import { isAbsolute, join, resolve } from 'node:path';
import { isIP } from 'node:net';
import { setTimeout as delay } from 'node:timers/promises';
import {
  expect,
  errors as playwrightErrors,
  type APIRequestContext,
  type APIResponse,
  type Response,
  type TestInfo,
} from '@playwright/test';
import { test, requirePrivateArtifacts } from './artifact-privacy';
import { Actor, MobileDataset, admit, selectPwaRole } from './mobile-dataset';
import { loginPwa } from './pwa-auth';
import { TEST_USERS } from './users';
import {
  retainFirstDatasetHttpFailure,
  retainSemesterPreviewTransport,
  safeNativePrimaryFailures,
  retainNativePrimaryFailure,
  retainLifecycleCleanupFailure,
  type LifecycleCleanupCause,
} from './safe-reporter';

import {
  canonicalObjectOrder,
  mountRecords,
  ownedContainerIdentityAnnotation,
  ownedContainerIdentityChanges,
  type OwnedContainerIdentityFailure,
} from './owned-container-identity';
import { normalizeDockerHealthTimestamp, observeHealth, withoutGatewayAdmissionDiagnostic, writeHealthDiagnostic } from './semester-health-diagnostic';
type Row = Record<string, any>;

// This admission pins the manifest, current phase and every local resource.
const localLane = process.env.RCT_LOCAL_SPECIAL_LANE;
const local = localSpecialLane((localLane ?? 'semester-metadata') as LocalSpecialLane);
if (local && !['semester-metadata', 'subject-deletion', 'semester-reconciliation', 'semester-archive'].includes(localLane ?? ''))
  throw new Error('Local semester lane mismatch');


type SemesterStage =
  | 'SETUP'
  | 'COMMAND'
  | 'PARTICIPANT_BARRIER'
  | 'SETTLED_READBACK'
  | 'REPLAY'
  | 'ARCHIVED_READBACK'
  | 'RESTORED_READBACK'
  | 'PERMITTED_WRITE'
  | 'CLEANUP_STATUS'
  | 'ORIGINAL_AUTHORITY'
  | 'COMPLETED';

function semesterProgress() {
  return {
    stage: 'SETUP' as SemesterStage,
    action: 'NONE' as 'NONE' | 'ARCHIVE' | 'RESTORE',
    operationState: 'UNKNOWN',
    academic: 'UNKNOWN',
    schedule: 'UNKNOWN',
    attendance: 'UNKNOWN',
    retryable: 'UNKNOWN' as boolean | 'UNKNOWN',
    transition: 'UNKNOWN',
    active: 'UNKNOWN' as boolean | 'UNKNOWN',
    archived: 'UNKNOWN' as boolean | 'UNKNOWN',
    releasePending: 'UNKNOWN' as boolean | 'UNKNOWN',
    writeBlocked: 'UNKNOWN' as boolean | 'UNKNOWN',
    reasonCode: 'UNKNOWN',
  };
}

function semesterReasonCode(reason: unknown) {
  for (const event of [
    'lesson.closed',
    'lesson.cancelled',
    'lesson.deleted',
    'lesson.one_off.cancelled',
    'homework.binding.archived',
  ]) {
    if (reason === `Ожидается точное подтверждение эффекта ${event}`)
      return 'SCHEDULE_EFFECT_ACK_PENDING';
    if (reason === `В outbox сохранено старое событие ${event} без точного effect receipt`)
      return 'SCHEDULE_LEGACY_EFFECT_UNPROVEN';
    if (reason === `Событие ${event} не содержит подтверждённую semester scope`)
      return 'SCHEDULE_EFFECT_SCOPE_UNPROVEN';
  }
  switch (reason) {
    case null:
      return 'NONE';
    case 'Ожидается завершение или reconciliation переноса пары в расписании':
      return 'SCHEDULE_TRANSFER_PENDING';
    case 'Ожидается подтверждение принятой публикации домашнего задания в расписании':
      return 'SCHEDULE_PUBLICATION_PENDING';
    case 'Ожидается подтверждение принятого изменения домашнего задания':
      return 'SCHEDULE_EDIT_PENDING';
    case 'Ожидается завершение принятого редактирования домашнего задания':
      return 'ACADEMIC_EDIT_PENDING';
    case 'Ожидается подтверждение принятой публикации домашнего задания в Academic':
      return 'ACADEMIC_PUBLICATION_PENDING';
    case 'Ожидается завершение ранее принятой публикации домашнего задания':
      return 'ACADEMIC_ADMISSION_PENDING';
    case 'Ожидается применение принятого переноса привязки домашнего задания':
      return 'ACADEMIC_TRANSFER_PENDING';
    case 'Ожидается точный ACK ретроспективной посещаемости зачисленного студента':
      return 'ACADEMIC_INITIAL_MEMBERSHIP_ACK_PENDING';
    case 'Ожидается точный ACK применения эффекта Schedule':
      return 'ACADEMIC_EFFECT_ACK_PENDING';
    case 'Ожидается применение отмены и точный ACK Schedule effect':
      return 'SCHEDULE_CANCELLATION_ACK_PENDING';
    case 'ARCHIVE_DRAIN_PENDING':
    case 'ARCHIVE_SEAL_ACK_PENDING':
    case 'RESTORE_RELEASE_PENDING':
    case 'STALE_STATE_VERSION':
    case 'ARCHIVE_TRANSITION_IN_PROGRESS':
    case 'ARCHIVE_PREPARE_VERSION_MISMATCH':
    case 'ATTENDANCE_EFFECT_REQUIRES_RECONCILIATION':
    case 'ATTENDANCE_TRANSFER_REQUIRES_RECONCILIATION':
    case 'RESTORE_REQUIRES_ARCHIVE_PREPARE':
    case 'RESTORE_RELEASE_VERSION_MISMATCH':
      return reason;
    default:
      return 'UNKNOWN';
  }
}

function observeSemester(progress: ReturnType<typeof semesterProgress>, status: Row) {
  const operation = status.operation ?? status;
  const closed = (value: unknown, allowed: string[]) =>
    allowed.find((candidate) => candidate === value) ?? 'UNKNOWN';
  const boolean = (value: unknown): boolean | 'UNKNOWN' =>
    typeof value === 'boolean' ? value : 'UNKNOWN';
  progress.operationState = closed(operation.operationState, ['PENDING', 'COMPLETED', 'ERROR']);
  for (const participant of ['academic', 'schedule', 'attendance'] as const) {
    progress[participant] = closed(operation[participant], [
      'NOT_STARTED',
      'PENDING',
      'READY',
      'PREPARED_RESTORE',
      'RELEASE_PENDING',
      'RELEASED',
      'DELETED',
    ]);
  }
  progress.retryable = boolean(operation.retryable);
  progress.transition = closed(status.transition, ['NONE', 'ARCHIVING', 'RESTORING', 'DELETING']);
  progress.active = boolean(status.active);
  progress.archived = boolean(status.archived);
  progress.releasePending = boolean(status.releasePending);
  progress.writeBlocked = boolean(status.writeBlocked);
  progress.reasonCode = semesterReasonCode(operation.blockingReason);
}

function requireValue(condition: unknown, message: string): asserts condition {
  if (!condition) {
    const error = new Error(message);
    Error.captureStackTrace(error, requireValue);
    throw error;
  }
}

function requireOwnedContainerIdentity(
  current: Row,
  pin: Row,
  fixture: OwnedContainerIdentityFailure['fixture'],
  service: OwnedContainerIdentityFailure['service'],
  message: string,
) {
  const changed = ownedContainerIdentityChanges(current, pin);
  if (Object.values(changed).some(Boolean)) {
    const annotations = test.info().annotations;
    if (!annotations.some((annotation) => annotation.type === ownedContainerIdentityAnnotation))
      annotations.push({
        type: ownedContainerIdentityAnnotation,
        description: JSON.stringify({ fixture, service, changed }),
      });
    requireValue(false, message);
  }
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
  if (JSON.stringify(left) !== JSON.stringify(right)) {
    const error = new Error(message);
    Error.captureStackTrace(error, equal);
    throw error;
  }
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


const terminal = (...args: Parameters<typeof observeTerminal>) => {
  args[7] = (progress, value) => observeSemester(progress as ReturnType<typeof semesterProgress>, value);
  return observeTerminal(...args);
};

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

async function inactiveStudentHistory(request: APIRequestContext, student: Actor, path: string) {
  const response = await request.get(path, { headers: student.headers() });
  requireValue(
    response.status() === 403 &&
      response.headers()['content-type']?.includes('application/problem+json'),
    'Inactive student history must return a typed scope denial',
  );
  const body = await response.json();
  requireValue(
    body.status === 403 && body.code === 'OUT_OF_SCOPE',
    'Inactive student history scope denial changed',
  );
}

// Fixed historical fixture for the two real Vue promotion commands below.
// Durable rows/events are retained in this disposable CI stand; no historical data is purged.
const promotionStartedAt = new WeakMap<object, number>();
test.beforeEach(async ({}, info) => {
  promotionStartedAt.set(info, performance.now());
});

function promotionForeignCohortsSafe(groups: Row[], cutoff: string): boolean {
  const end = Date.parse(`${cutoff}T23:59:59.999+03:00`);
  return groups.every(
    (group) =>
      group.is_active === false ||
      (group.is_active === true &&
        Number.isFinite(Date.parse(group.created_at)) &&
        Date.parse(group.created_at) > end),
  );
}

function promotionCycleCandidate(
  rows: Row[],
  today: string,
  groups: Row[],
): { from: string; to: string; year: number } {
  requireValue(rows.length < 100, 'Promotion semester inventory must be bounded');
  const newestYear = Number(today.slice(0, 4)) - (today.slice(5) <= '06-30' ? 1 : 0);
  for (let offset = 0; offset < 3; offset++) {
    const year = newestYear - offset;
    const from = `${year}-02-01`,
      to = `${year}-06-30`;
    const unoccupied = rows.every((row) => row.date_to < from || row.date_from > to);
    const latest = rows.every(
      (row) =>
        row.semester_type !== 'SPRING' ||
        (row.promotion_cycle_date_to ?? row.date_to) >= today ||
        (row.promotion_cycle_date_to ?? row.date_to) < to,
    );
    if (unoccupied && latest && promotionForeignCohortsSafe(groups, to)) return { from, to, year };
  }
  throw new Error('No unoccupied latest historical spring fixture range');
}

function promotionActionables(
  plan: Row,
  cycle: number,
  group: number | null,
  promote: number[],
  archive: number[],
  dryRun: boolean,
): void {
  requireValue(
    plan.cycleSemesterId === cycle &&
      plan.groupId === group &&
      plan.dryRun === dryRun &&
      plan.executed === !dryRun &&
      typeof plan.previewVersion === 'string' &&
      /^[0-9a-f]{64}$/.test(plan.previewVersion) &&
      Array.isArray(plan.toPromote) &&
      Array.isArray(plan.toArchive) &&
      Array.isArray(plan.skipped) &&
      Array.isArray(plan.conflicts) &&
      plan.conflicts.length === 0,
    'Promotion must confirm the exact owned cycle and plan',
  );
  equal(
    plan.toPromote.map((item: Row) => item.id),
    promote,
    'Promotion contains unowned promote action',
  );
  equal(
    plan.toArchive.map((item: Row) => item.id),
    archive,
    'Promotion contains unowned archive action',
  );
  requireValue(
    [...plan.toPromote, ...plan.toArchive].every((item: Row) => item.studentCount === 0),
    'Promotion fixture groups must remain empty',
  );
}

function promotionWorkBudget(
  caseEnd: number,
  workEnd: number,
  cap: number,
  now = performance.now(),
): number {
  const remaining = Math.floor(Math.min(workEnd, caseEnd - 150000) - now);
  requireValue(remaining >= 100, 'Promotion main work must retain original recovery time');
  return Math.min(cap, remaining);
}

async function ownedHistoricalPromotion(
  page: import('@playwright/test').Page,
  request: APIRequestContext,
  admin: Actor,
  caseEnd: number,
  currentSemesterId: number,
): Promise<void> {
  requireValue(
    caseEnd - performance.now() >= 210000,
    'Promotion requires its full main and cleanup reserve',
  );
  const workEnd = performance.now() + 60000;
  let cleaning = false,
    cleanupEnd = 0;
  const budget = (cap = 5000): number =>
    cleaning
      ? (() => {
          const left = Math.floor(cleanupEnd - performance.now());
          requireValue(left >= 100, 'Owned promotion cleanup deadline exhausted');
          return Math.min(cap, left);
        })()
      : promotionWorkBudget(caseEnd, workEnd, cap);
  const natural = () => {
    const fields = new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Europe/Moscow',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hourCycle: 'h23',
    }).formatToParts(new Date());
    const value = (type: string) => fields.find((part) => part.type === type)?.value ?? '';
    const second =
      Number(value('hour')) * 3600 + Number(value('minute')) * 60 + Number(value('second'));
    const since = (second - 10800 + 86400) % 86400;
    requireValue(
      since > 600 && (86400 - since) * 1000 > caseEnd - performance.now() + 1000,
      'Promotion fixture must not overlap the natural scheduled cycle',
    );
    return `${value('year')}-${value('month')}-${value('day')}`;
  };
  const today = natural();
  const root = resolve(__dirname, '../../..'),
    envFile = (local ? join(local.run, 'synthetic.env') : join(root, 'tests/e2e/.env.ci'));
  const envStat = lstatSync(envFile);
  requireValue(
    envStat.isFile() &&
      !envStat.isSymbolicLink() &&
      envStat.size <= 65536 &&
      (envStat.mode & 0o022) === 0,
    'Promotion requires bounded owned CI configuration',
  );
  let password = '';
  for (const line of readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const match = /^POSTGRES_ACADEMIC_PASSWORD=(.*)$/.exec(line);
    if (match) {
      password = match[1];
      if (
        password.length >= 2 &&
        ['"', "'"].includes(password[0]) &&
        password.at(-1) === password[0]
      )
        password = password.slice(1, -1);
    }
  }
  requireValue(password.length > 0, 'Owned Academic fixture credential is required');
  const native = (args: string[], input?: string, secret = false): string => {
    requireValue(natural() === today, 'Natural promotion date must stay unchanged');
    try {
      return execFileSync('docker', args, {
        cwd: root,
        env: {
          PATH: process.env.PATH ?? '/usr/bin:/bin',
          LANG: 'C.UTF-8',
          RCT_E2E_TEACHING_CLOCK_DIR: process.env.RCT_E2E_TEACHING_CLOCK_DIR!,
          ...(secret ? { PGPASSWORD: password } : {}),
        },
        input,
        timeout: budget(),
        maxBuffer: 262144,
        encoding: 'utf8',
        stdio: ['pipe', 'pipe', 'pipe'],
      });
    } catch {
      throw new Error('Owned Academic promotion command failed');
    }
  };
  const json = (text: string): Row => {
    try {
      return JSON.parse(text);
    } catch {
      throw new Error('Owned promotion JSON is invalid');
    }
  };
  // Successful application recovery leaves the original databases stopped.
  const recoveryProject = process.env.RCT_APP_RECOVERY_PROJECT;
  const project = local?.databaseProject ?? recoveryProject ?? 'rct-e2e';
  if (!local && recoveryProject !== undefined) {
    const runId = process.env.GITHUB_RUN_ID;
    const runAttempt = process.env.GITHUB_RUN_ATTEMPT;
    requireValue(
      process.env.GITHUB_ACTIONS === 'true' &&
        /^rct-recovery-[1-9][0-9]*-[1-9][0-9]*-dst$/.test(recoveryProject) &&
        recoveryProject === `rct-recovery-${runId}-${runAttempt}-dst`,
      'Owned promotion recovery project is invalid',
    );
  }
  const ids = native(
    local ? ['ps', '-q', '--filter', `label=com.docker.compose.project=${project}`, '--filter', 'label=com.docker.compose.service=postgres-academic', '--filter', `label=rct.local.stage17=${local.nonce}`] : recoveryProject === undefined
      ? [
          'compose',
          '--env-file',
          envFile,
          '-p',
          project,
          '-f',
          join(root, 'docker-compose.e2e.yml'),
          '-f',
          join(root, 'tests/e2e/infra/docker-compose.clock-fixture.yml'),
          'ps',
          '-q',
          'postgres-academic',
        ]
      : [
          'ps',
          '-q',
          '--filter',
          `label=com.docker.compose.project=${project}`,
          '--filter',
          'label=com.docker.compose.service=postgres-academic',
          '--filter',
          'label=io.rutcampustrack.recovery=disposable',
        ],
  )
    .trim()
    .split(/\s+/);
  requireValue(
    ids.length === 1 && /^[0-9a-f]{12,64}$/.test(ids[0]),
    'Exactly one owned Academic database is required',
  );
  const inspect = () =>
    json(
      native([
        'inspect',
        '--format',
        '{"id":{{json .Id}},"image":{{json .Image}},"configuredImage":{{json .Config.Image}},"labels":{{json .Config.Labels}},"running":{{json .State.Running}},"health":{{json .State.Health.Status}},"mounts":{{json .Mounts}}}',
        ids[0],
      ]),
    );
  const pin = inspect();
  if (local) requireValue(localSpecialService(local, 'postgres-academic').id === pin.id, 'Local promotion service mismatch');
  requireValue(
    /^[0-9a-f]{64}$/.test(pin.id) &&
      pin.id.startsWith(ids[0]) &&
      pin.labels?.['com.docker.compose.project'] === project &&
      (local ? localSpecialService(local, 'postgres-academic').id === pin.id : (recoveryProject === undefined ||
        pin.labels?.['io.rutcampustrack.recovery'] === 'disposable')) &&
      pin.labels?.['com.docker.compose.service'] === 'postgres-academic' &&
      pin.configuredImage === 'postgres:16' &&
      pin.running === true &&
      pin.health === 'healthy',
    'Exact healthy owned Academic database is required',
  );
  const identity = () =>
    requireOwnedContainerIdentity(
      inspect(),
      pin,
      'promotion',
      'postgres-academic',
      'Owned Academic container identity changed',
    );
  const nonce = randomUUID();
  const marker = `RCT-PROMOTION-${nonce}`;
  const alphabet = 'АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЩЭЮЯ';
  const prefix = (part: string) => [...part].map((digit) => alphabet[parseInt(digit, 16)]).join('');
  const aPrefix = prefix(nonce.slice(0, 4)),
    bPrefix = prefix(nonce.slice(9, 13));
  requireValue(aPrefix !== bPrefix, 'Owned promotion prefixes must be distinct');
  let cycle: ReturnType<typeof promotionCycleCandidate> | undefined;
  let seedAttempted = false,
    own: Row | undefined;
  let before: Row | undefined,
    aCreated = '',
    bCreated = '';
  const quote = (text: string) => `'${text.replace(/'/g, "''")}'`;
  // This fixed operation selector cannot execute caller-supplied SQL.
  const pg = (operation: 'snapshot' | 'seed' | 'owned'): Row => {
    identity();
    let command: string;
    const ownFilter = own ? `id NOT IN (${own.a},${own.b})` : 'true';
    if (operation === 'snapshot')
      command = `
      SELECT json_build_object('date', (clock_timestamp() AT TIME ZONE 'Europe/Moscow')::date,
        'free', NOT EXISTS (SELECT 1 FROM shedlock WHERE name='academic-group-promotion-job' AND lock_until>clock_timestamp()),
        'semesters', (SELECT COALESCE(jsonb_agg(to_jsonb(s) ORDER BY id),'[]') FROM semesters s ${own ? `WHERE id<>${own.cycle}` : ''}),
        'groups', (SELECT COALESCE(jsonb_agg(to_jsonb(g) ORDER BY id),'[]') FROM groups g WHERE ${ownFilter}),
        'coverage', (SELECT COALESCE(jsonb_agg(to_jsonb(h) ORDER BY group_id),'[]') FROM group_history_coverage h ${own ? `WHERE group_id NOT IN (${own.a},${own.b})` : ''}),
        'ledger', (SELECT COALESCE(jsonb_agg(to_jsonb(l) ORDER BY id),'[]') FROM group_promotion_cycle_record l ${own ? `WHERE cycle_semester_id<>${own.cycle}` : ''}));`;
    else if (operation === 'seed') {
      requireValue(cycle && before && !own, 'Owned historical seed prerequisites are required');
      command = `LOCK TABLE semesters, groups, group_history_coverage, group_promotion_cycle_record IN SHARE ROW EXCLUSIVE MODE;
        DO $fixture$ BEGIN
          IF (SELECT COALESCE(jsonb_agg(to_jsonb(s) ORDER BY id),'[]') FROM semesters s) IS DISTINCT FROM ${quote(JSON.stringify(before.semesters))}::jsonb
            OR (SELECT COALESCE(jsonb_agg(to_jsonb(g) ORDER BY id),'[]') FROM groups g) IS DISTINCT FROM ${quote(JSON.stringify(before.groups))}::jsonb
            OR (SELECT COALESCE(jsonb_agg(to_jsonb(l) ORDER BY id),'[]') FROM group_promotion_cycle_record l) IS DISTINCT FROM ${quote(JSON.stringify(before.ledger))}::jsonb
            OR (SELECT COALESCE(jsonb_agg(to_jsonb(h) ORDER BY group_id),'[]') FROM group_history_coverage h) IS DISTINCT FROM ${quote(JSON.stringify(before.coverage))}::jsonb
            OR EXISTS (SELECT 1 FROM groups WHERE is_active AND (created_at AT TIME ZONE 'Europe/Moscow')::date<=${quote(cycle.to)}::date)
            OR EXISTS (SELECT 1 FROM shedlock WHERE name='academic-group-promotion-job' AND lock_until>clock_timestamp())
            OR (clock_timestamp() AT TIME ZONE 'Europe/Moscow')::date<>${quote(today)}::date
          THEN RAISE EXCEPTION 'Owned historical promotion admission changed'; END IF;
        END $fixture$;
        WITH c AS (INSERT INTO semesters(name,date_from,date_to,is_active,created_at,semester_type,academic_year)
          VALUES (${quote(marker)},${quote(cycle.from)},${quote(cycle.to)},false,${quote(`${cycle.year}-01-15T00:00:00+03:00`)},'SPRING',${cycle.year - 1}) RETURNING id),
        a AS (INSERT INTO groups(name,alphabetic_code,numeric_code,current_course,training_duration_years,duration_status,is_active,created_at)
          VALUES (${quote(aPrefix + '-110')},${quote(aPrefix)},'110',1,4,'KNOWN',true,${quote(aCreated)}) RETURNING id),
        b AS (INSERT INTO groups(name,alphabetic_code,numeric_code,current_course,training_duration_years,duration_status,is_active,created_at)
          VALUES (${quote(bPrefix + '-410')},${quote(bPrefix)},'410',4,4,'KNOWN',true,${quote(bCreated)}) RETURNING id),
        coverage AS (INSERT INTO group_history_coverage(group_id,coverage_from,writer_version)
          SELECT id,${quote(aCreated.slice(0, 10))}::date,'managed_v1' FROM a
          UNION ALL SELECT id,${quote(bCreated.slice(0, 10))}::date,'managed_v1' FROM b RETURNING group_id)
        SELECT json_build_object('cycle',(SELECT id FROM c),'a',(SELECT id FROM a),'b',(SELECT id FROM b));`;
    } else {
      requireValue(cycle, 'Owned historical recovery needs its original candidate');
      command = `SELECT json_build_object('cycles',(SELECT COALESCE(jsonb_agg(to_jsonb(s)),'[]') FROM semesters s WHERE name=${quote(marker)}),
        'groups',(SELECT COALESCE(jsonb_agg(to_jsonb(g) ORDER BY id),'[]') FROM groups g
          WHERE (alphabetic_code=${quote(aPrefix)} AND created_at=${quote(aCreated)}::timestamptz)
             OR (alphabetic_code=${quote(bPrefix)} AND created_at=${quote(bCreated)}::timestamptz)),
        'coverage',(SELECT COALESCE(jsonb_agg(to_jsonb(h) ORDER BY group_id),'[]') FROM group_history_coverage h JOIN groups g ON g.id=h.group_id
          WHERE (g.alphabetic_code=${quote(aPrefix)} AND g.created_at=${quote(aCreated)}::timestamptz)
             OR (g.alphabetic_code=${quote(bPrefix)} AND g.created_at=${quote(bCreated)}::timestamptz)),
        'empty', NOT EXISTS (SELECT 1 FROM academic_profiles p
          JOIN academic_role_eligibility r ON r.user_id=p.id JOIN groups g ON g.id=r.group_id
          WHERE (g.alphabetic_code=${quote(aPrefix)} AND g.created_at=${quote(aCreated)}::timestamptz)
             OR (g.alphabetic_code=${quote(bPrefix)} AND g.created_at=${quote(bCreated)}::timestamptz))
          AND NOT EXISTS (SELECT 1 FROM student_group_history h JOIN groups g ON g.id=h.group_id
          WHERE (g.alphabetic_code=${quote(aPrefix)} AND g.created_at=${quote(aCreated)}::timestamptz)
             OR (g.alphabetic_code=${quote(bPrefix)} AND g.created_at=${quote(bCreated)}::timestamptz)),
        'ledger',(SELECT COALESCE(jsonb_agg(to_jsonb(l) ORDER BY group_id),'[]') FROM group_promotion_cycle_record l
          WHERE cycle_semester_id IN (SELECT id FROM semesters WHERE name=${quote(marker)})));`;
    }
    const raw = native(
      [
        'exec',
        '-i',
        '-e',
        'PGPASSWORD',
        pin.id,
        'psql',
        '-X',
        '-qAt',
        '-U',
        'rct_user',
        '-d',
        'academic_db',
        '-v',
        'ON_ERROR_STOP=1',
      ],
      `BEGIN; SET LOCAL statement_timeout='2000ms'; SET LOCAL lock_timeout='1000ms'; ${command} COMMIT;`,
      true,
    );
    const result = json(raw.trim());
    if (operation === 'owned') {
      // Auth owns admission grants after the split. Keep their independent emptiness
      // witness, scoped only to the exact owned groups returned by Academic above.
      const groupIds = result.groups.map((group: Row) => Number(group.id)).join(',');
      const auth = json(
        native(
          [
            'exec',
            '-i',
            '-e',
            'PGPASSWORD',
            pin.id,
            'psql',
            '-X',
            '-qAt',
            '-U',
            'rct_user',
            '-d',
            'auth_db',
            '-v',
            'ON_ERROR_STOP=1',
          ],
          `BEGIN READ ONLY; SET LOCAL statement_timeout='2000ms'; SET LOCAL lock_timeout='1000ms';
          SELECT json_build_object('empty',NOT EXISTS(SELECT 1 FROM user_role_grants
            WHERE group_id=ANY(ARRAY[${groupIds}]::bigint[]))); ROLLBACK;`,
          true,
        ).trim(),
      );
      result.empty = result.empty === true && auth.empty === true;
    }
    return result;
  };
  const call = async (method: 'GET' | 'PUT', path: string, data?: Row): Promise<Row> => {
    const response = await request.fetch(path, {
      method,
      data,
      headers: admin.headers(),
      timeout: budget(5000),
    });
    requireValue(response.status() === 200, 'Owned promotion read or finalization must succeed');
    return response.json();
  };
  const uiResponse = async (path: string, action: () => Promise<void>): Promise<Row> => {
    let observed: Response | undefined;
    const matches = (response: Response) =>
      new URL(response.url()).pathname === path && response.request().method() === 'POST';
    const observe = (response: Response) => {
      if (matches(response)) observed ??= response;
    };
    page.on('response', observe);
    try {
      await action();
      const response =
        observed ?? (await page.waitForResponse(matches, { timeout: budget(10000) }));
      requireValue(response.status() === 200, 'Real Vue promotion command must succeed');
      const body = response.request().postDataJSON();
      let timer: ReturnType<typeof setTimeout> | undefined;
      let value: Row;
      try {
        value = await Promise.race([
          response.json(),
          new Promise<never>((_, reject) => {
            timer = setTimeout(
              () => reject(new Error('Vue promotion response body deadline exhausted')),
              budget(5000),
            );
          }),
        ]);
      } finally {
        if (timer) clearTimeout(timer);
      }
      if (path.endsWith('/preview'))
        equal(
          body,
          value.groupId === null ? {} : { groupId: value.groupId },
          'Vue preview scope changed',
        );
      else
        equal(
          body,
          {
            cycleSemesterId: value.cycleSemesterId,
            previewVersion: value.previewVersion,
            groupId: value.groupId,
          },
          'Vue execute scope changed',
        );
      return value;
    } finally {
      page.off('response', observe);
    }
  };
  let primary: { error: unknown } | undefined;
  try {
    before = pg('snapshot');
    requireValue(
      before.date === today &&
        before.free === true &&
        before.groups.length < 1000 &&
        before.ledger.length < 5000,
      'Owned historical promotion inventory and scheduler must be bounded and stable',
    );
    const currentBefore = before.semesters.filter((row: Row) => row.is_active === true);
    requireValue(
      currentBefore.length === 1 &&
        currentBefore[0].id === currentSemesterId &&
        currentBefore[0].date_from <= today &&
        currentBefore[0].date_to >= today,
      'Historical promotion fixture must preserve the known current semester',
    );
    cycle = promotionCycleCandidate(before.semesters, today, before.groups);
    requireValue(
      before.groups.every(
        (row: Row) =>
          row.alphabetic_code !== aPrefix &&
          row.alphabetic_code !== bPrefix &&
          !String(row.name).startsWith(aPrefix + '-') &&
          !String(row.name).startsWith(bPrefix + '-'),
      ),
      'Owned historical group prefixes must not already exist',
    );
    const seconds = parseInt(nonce.slice(-4), 16) % 86400;
    const time = new Date(Date.UTC(2000, 0, 1, 0, 0, seconds)).toISOString().slice(11, 19);
    const fraction = parseInt(nonce.slice(14, 18), 16).toString().padStart(6, '0');
    aCreated = `${cycle.year - 1}-09-01T${time}.${fraction}+03:00`;
    bCreated = `${cycle.year - 4}-09-01T${time}.${fraction}+03:00`;
    seedAttempted = true;
    own = pg('seed');
    requireValue(
      ['cycle', 'a', 'b'].every((key) => Number.isSafeInteger(own![key]) && own![key] > 0) &&
        own.a !== own.b,
      'Owned historical seed identities must be positive and distinct',
    );
    const seed = pg('owned');
    requireValue(
      seed.cycles.length === 1 &&
        seed.cycles[0].id === own.cycle &&
        seed.cycles[0].is_active === false &&
        seed.cycles[0].semester_type === 'SPRING' &&
        seed.cycles[0].date_from === cycle.from &&
        seed.cycles[0].date_to === cycle.to &&
        seed.cycles[0].academic_year === cycle.year - 1 &&
        seed.groups.length === 2 &&
        seed.coverage.length === 2 &&
        seed.empty === true &&
        seed.coverage.every((row: Row) => row.writer_version === 'managed_v1') &&
        seed.ledger.length === 0,
      'Owned historical cohort must be newly seeded and unprocessed',
    );
    // Native SQL prepares historical state only; both actual mutations use Vue and the real API.
    await expect(page.locator('main.admin-semester-screen')).toHaveAttribute(
      'data-admin-semester-view',
      'list',
      { timeout: budget(10000) },
    );
    await page
      .locator('main.admin-semester-screen')
      .getByRole('button', { name: 'Назад', exact: true })
      .click({ timeout: budget(10000) });
    await expect(page.locator('main.admin-more')).toBeVisible({ timeout: budget(10000) });
    await page
      .getByRole('navigation', { name: 'Основная навигация', exact: true })
      .getByRole('button', { name: 'Группы', exact: true })
      .click({ timeout: budget(10000) });
    await page.getByRole('tab', { name: /^Черновики/ }).click({ timeout: budget(10000) });
    const search = page.getByRole('searchbox', { name: 'Найти группу', exact: true });
    await search.fill(aPrefix, { timeout: budget(10000) });
    await page
      .getByRole('button', { name: 'Найти группу', exact: true })
      .click({ timeout: budget(10000) });
    const row = page.locator(`[data-group-id="${own.a}"]`);
    await row.locator('.admin-groups-item__trigger').click({ timeout: budget(10000) });
    const first = await uiResponse('/api/academic/groups/promote/preview', () =>
      row
        .getByRole('button', { name: 'Перевести на следующий курс', exact: true })
        .click({ timeout: budget(10000) }),
    );
    promotionActionables(first, own.cycle, own.a, [own.a], [], true);
    requireValue(
      first.cycleDateTo === cycle.to &&
        first.toPromote[0].from === aPrefix + '-110' &&
        first.toPromote[0].to === aPrefix + '-210',
      'Row preview must show the real historical course increment',
    );
    await page
      .getByRole('button', { name: 'Подтвердить показанный план', exact: true })
      .click({ timeout: budget(10000) });
    const executed = await uiResponse('/api/academic/groups/promote', () =>
      page
        .getByRole('dialog', { name: 'Подтверди перевод групп', exact: true })
        .getByRole('button', { name: 'Подтвердить перевод', exact: true })
        .click({ timeout: budget(10000) }),
    );
    promotionActionables(executed, own.cycle, own.a, [own.a], [], false);
    requireValue(
      executed.previewVersion === first.previewVersion,
      'Row command must execute the exact shown preview',
    );
    const promotionGroups = page.locator('main.admin-groups-screen');
    await expect(promotionGroups.locator('.admin-groups-promotion__result')).toBeVisible({
      timeout: budget(10000),
    });
    await promotionGroups
      .getByRole('button', { name: 'Назад', exact: true })
      .click({ timeout: budget(10000) });
    await expect(
      promotionGroups.getByRole('region', { name: 'Реестр групп', exact: true }),
    ).toBeVisible({ timeout: budget(10000) });
    await expect(row.getByText(aPrefix + '-210', { exact: true })).toBeVisible({
      timeout: budget(10000),
    });
    const aAfter = await call('GET', `/api/academic/groups/${own.a}`);
    requireValue(
      aAfter.name === aPrefix + '-210' &&
        aAfter.currentCourse === 2 &&
        aAfter.trainingDurationYears === 4 &&
        aAfter.active === true,
      'Single Vue promotion must authoritatively advance only its own course',
    );
    const mass = await uiResponse('/api/academic/groups/promote/preview', () =>
      page
        .getByRole('button', { name: 'Перевести все группы на следующий курс', exact: true })
        .click({ timeout: budget(10000) }),
    );
    promotionActionables(mass, own.cycle, null, [], [own.b], true);
    requireValue(
      mass.cycleDateTo === cycle.to &&
        mass.skipped.some(
          (item: Row) =>
            item.id === own!.a &&
            item.reason === 'ALREADY_PROCESSED' &&
            item.previousAction === 'PROMOTE' &&
            item.previousFrom === aPrefix + '-110' &&
            item.previousTo === aPrefix + '-210',
        ),
      'Mass plan must exclude the row already processed in the same cycle',
    );
    await page
      .getByRole('button', { name: 'Подтвердить показанный план', exact: true })
      .click({ timeout: budget(10000) });
    const completed = await uiResponse('/api/academic/groups/promote', () =>
      page
        .getByRole('dialog', { name: 'Подтверди перевод групп', exact: true })
        .getByRole('button', { name: 'Подтвердить перевод', exact: true })
        .click({ timeout: budget(10000) }),
    );
    promotionActionables(completed, own.cycle, null, [], [own.b], false);
    requireValue(
      completed.previewVersion === mass.previewVersion,
      'Mass command must execute the exact shown preview',
    );
    equal(
      await call('GET', `/api/academic/groups/${own.a}`),
      aAfter,
      'Mass promotion changed the already processed own row',
    );
    const bAfter = await call('GET', `/api/academic/groups/${own.b}`);
    requireValue(
      bAfter.name === `${bPrefix}-410 (выпуск ${cycle.year})` &&
        bAfter.active === false &&
        bAfter.currentCourse === 4 &&
        bAfter.trainingDurationYears === 4,
      'Mass Vue promotion must archive the exact graduating own cohort',
    );
    await expect(
      page
        .locator('.admin-groups-promotion__result')
        .getByText(bPrefix + '-410 — отправлена в архив', { exact: true }),
    ).toBeVisible({ timeout: budget(10000) });
    const after = pg('snapshot');
    equal(
      after.semesters,
      before.semesters,
      'Promotion changed a pre-existing semester or its active context',
    );
    equal(after.groups, before.groups, 'Promotion changed an unowned group');
    equal(after.coverage, before.coverage, 'Promotion changed an unowned history coverage');
    equal(after.ledger, before.ledger, 'Promotion changed an unowned cycle ledger');
    const ownedAfter = pg('owned');
    requireValue(
      ownedAfter.empty === true,
      'Owned historical cohorts must retain their known empty history',
    );
    equal(ownedAfter.coverage, seed.coverage, 'Promotion changed the owned empty history coverage');
    const ledger = ownedAfter.ledger;
    requireValue(
      ledger.length === 2 &&
        ledger.some(
          (item: Row) =>
            item.group_id === own!.a &&
            item.action === 'PROMOTE' &&
            item.from_name === aPrefix + '-110' &&
            item.to_name === aPrefix + '-210' &&
            item.student_count === 0,
        ) &&
        ledger.some(
          (item: Row) =>
            item.group_id === own!.b &&
            item.action === 'ARCHIVE' &&
            item.from_name === bPrefix + '-410' &&
            item.to_name === bAfter.name &&
            item.student_count === 0,
        ),
      'Exactly both own actions must be durably recorded in their historical cycle',
    );
  } catch (error) {
    primary = { error };
  } finally {
    cleaning = true;
    cleanupEnd = Math.min(performance.now() + 30000, caseEnd - 120000);
    let cleanupFailure: { error: unknown } | undefined;
    if (seedAttempted && cycle) {
      try {
        const retained = pg('owned');
        requireValue(
          retained.empty === true &&
            retained.cycles.length <= 1 &&
            retained.groups.length <= 2 &&
            (retained.groups.length === 0 || retained.cycles.length === 1),
          'Owned historical finalization identity is inconsistent',
        );
        for (const group of retained.groups) {
          try {
            requireValue(
              group.training_duration_years === 4 &&
                [aPrefix, bPrefix].includes(group.alphabetic_code),
              'Owned historical finalization group identity changed',
            );
            if (group.is_active) {
              const archived = await call('PUT', `/api/academic/groups/${group.id}`, {
                name: group.name,
                active: false,
              });
              requireValue(
                archived.active === false,
                'Owned historical group must be safely archived',
              );
            }
          } catch (error) {
            cleanupFailure ??= { error };
          }
        }
        const settled = pg('owned');
        requireValue(
          settled.groups.every(
            (group: Row) => group.is_active === false && group.archived_at !== null,
          ),
          'Owned historical groups must finish inactive with archive provenance',
        );
      } catch (error) {
        cleanupFailure ??= { error };
      }
    }
    // Retain the inactive cycle, archived groups, ledger and real events for normal stand teardown.
    expect
      .soft(cleanupFailure === undefined, 'Owned historical cohort finalization must complete')
      .toBe(true);
    if (primary) throw primary.error;
    if (cleanupFailure) throw cleanupFailure.error;
  }
}

async function ownedSemesterMetadataAndDeletion(
  page: import('@playwright/test').Page,
  admin: Actor,
  caseEnd: number,
  currentSemesterId: number,
  teachingInstant: Date,
): Promise<void> {
  // Keep the existing promotion's main work and original recovery reserve untouched.
  requireValue(
    caseEnd - performance.now() >= 330000,
    'Semester UI witness requires its main and owned cleanup reserve',
  );
  const mainEnd = Math.min(performance.now() + 60000, caseEnd - 270000);
  let cleaning = false,
    cleanupEnd = 0;
  const budget = (cap = 10000) => {
    const remaining = Math.floor((cleaning ? cleanupEnd : mainEnd) - performance.now());
    requireValue(remaining > 0, 'Owned semester UI deadline exhausted');
    return Math.min(cap, remaining);
  };
  const call = async (
    method: 'GET' | 'DELETE',
    path: string,
    data?: unknown,
    statuses = [200],
    headers: Record<string, string> = {},
  ): Promise<Row> => {
    const response = await admin.request.fetch(path, {
      method,
      data,
      headers: { ...admin.headers(), ...headers },
      timeout: budget(3000),
    });
    try {
      requireValue(
        statuses.includes(response.status()),
        'Owned semester protocol response rejected',
      );
      return response.status() === 404 ? {} : await response.json();
    } finally {
      await response.dispose();
    }
  };
  const inventory = async (): Promise<Row[]> => {
    const rows: Row[] = [];
    let total: number | undefined;
    for (let number = 0; number < 5; number++) {
      const body = await call('GET', `/api/academic/semesters?page=${number}&size=200&sort=id,asc`);
      const part = body._embedded?.semesterResponseList ?? [];
      requireValue(
        Array.isArray(part) &&
          part.length <= 200 &&
          body.page?.number === number &&
          Number.isSafeInteger(body.page.totalElements) &&
          body.page.totalElements >= 0 &&
          body.page.totalElements <= 1000,
        'Owned semester inventory must remain bounded',
      );
      total ??= body.page.totalElements;
      requireValue(
        total === body.page.totalElements,
        'Owned semester inventory changed while reading',
      );
      rows.push(...part);
      if (rows.length === total) break;
    }
    requireValue(
      rows.length === total &&
        new Set(rows.map((row) => row.id)).size === rows.length &&
        rows.every(
          (row) =>
            Number.isSafeInteger(row.id) &&
            row.id > 0 &&
            /^\d{4}-\d{2}-\d{2}$/.test(row.dateFrom) &&
            /^\d{4}-\d{2}-\d{2}$/.test(row.dateTo) &&
            row.dateFrom <= row.dateTo,
        ),
      'Owned semester inventory identities or dates changed',
    );
    return rows;
  };
  const before = await inventory();
  const previousIds = new Set(before.map((row) => row.id));
  requireValue(
    before.filter((row) => row.active).length === 1 &&
      before.find((row) => row.id === currentSemesterId)?.active === true,
    'Known original semester must remain current',
  );
  const today = (date: Date) => date.toLocaleDateString('sv-SE', { timeZone: 'Europe/Moscow' });
  const browserToday = await page.evaluate(() =>
    new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Moscow' }),
  );
  const laterToday = [today(teachingInstant), browserToday].sort()[1]!;
  let from = shift(laterToday, 1);
  for (const occupied of [...before].sort((a, b) => a.dateFrom.localeCompare(b.dateFrom))) {
    if (occupied.dateTo >= from && occupied.dateFrom <= shift(from, 1))
      from = shift(occupied.dateTo, 1);
  }
  const to = shift(from, 1);
  const type = Number(from.slice(5, 7)) >= 7 ? 'AUTUMN' : 'SPRING';
  const year = Number(from.slice(0, 4)) - (type === 'SPRING' ? 1 : 0);
  const expected = {
    name: `${type === 'AUTUMN' ? 'Осенний' : 'Весенний'} ${year}/${year + 1}`,
    dateFrom: from,
    dateTo: to,
    semesterType: type,
    academicYear: year,
  };
  const monthDistance = (initial: string, target: string) =>
    (Number(target.slice(0, 4)) - Number(initial.slice(0, 4))) * 12 +
    Number(target.slice(5, 7)) -
    Number(initial.slice(5, 7));
  const formYear = Number(browserToday.slice(0, 4));
  requireValue(
    year >= 1 &&
      year <= 9998 &&
      from >= '0001-01-01' &&
      to <= '9999-12-31' &&
      before.every((row) => row.dateTo < from || row.dateFrom > to) &&
      [from, to].every(
        (date) =>
          Math.abs(monthDistance(`${formYear}-09`, date)) <= 24 &&
          Math.abs(monthDistance(`${formYear + 1}-01`, date)) <= 24,
      ),
    'Owned semester needs a bounded non-overlapping calendar window',
  );
  const metadata = Object.keys(expected) as (keyof typeof expected)[];
  const owns = (row: Row) =>
    Number.isSafeInteger(row.id) &&
    row.id > 0 &&
    !previousIds.has(row.id) &&
    metadata.every((key) => row[key] === expected[key]) &&
    row.active === false &&
    row.archived === false &&
    row.activationScheduled === false;
  const countItems = [
    ['scheduleTemplates', 'Шаблоны расписания'],
    ['oneOffLessons', 'Разовые занятия'],
    ['lessons', 'Занятия'],
    ['assignments', 'Задания'],
    ['homeworks', 'Домашние работы'],
    ['attendanceMarks', 'Отметки посещаемости'],
    ['studentRequests', 'Заявки студентов'],
  ] as const;
  const validCounts = (counts: Row) =>
    counts && countItems.every(([key]) => Number.isSafeInteger(counts[key]) && counts[key] >= 0);
  const emptyCounts = (counts: Row) =>
    validCounts(counts) && countItems.every(([key]) => counts[key] === 0);
  const validPreview = (preview: Row, id: number) =>
    preview.semesterId === id &&
    Number.isSafeInteger(preview.stateVersion) &&
    preview.stateVersion >= 0 &&
    /^[0-9a-f]{64}$/.test(preview.previewDigest) &&
    validCounts(preview.counts);
  const screen = page.locator('main.admin-semester-screen');
  let ownId: number | undefined,
    createSent = false,
    createdReply: Response | undefined;
  let ownPreview: Row | undefined,
    deleteKey: string | undefined,
    deletionSubmitted = false;
  let foreignDelete = false,
    deleted = false;
  const observeRequest = (request: import('@playwright/test').Request) => {
    const path = new URL(request.url()).pathname;
    if (request.method() === 'POST' && path === '/api/academic/semesters') createSent = true;
    if (request.method() === 'DELETE' && /^\/api\/academic\/semesters\/[1-9]\d*$/.test(path)) {
      if (ownId === undefined || path !== `/api/academic/semesters/${ownId}`) foreignDelete = true;
      else {
        deletionSubmitted = true;
        deleteKey = request.headers()['idempotency-key'];
      }
    }
  };
  const observeCreate = (response: Response) => {
    if (
      response.request().method() === 'POST' &&
      new URL(response.url()).pathname === '/api/academic/semesters'
    )
      createdReply ??= response;
  };
  const browserBody = async (response: Response): Promise<Row> => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    try {
      return await Promise.race([
        response.json(),
        new Promise<never>((_, reject) => {
          timer = setTimeout(
            () => reject(new Error('Owned semester browser body deadline exhausted')),
            budget(5000),
          );
        }),
      ]);
    } finally {
      if (timer) clearTimeout(timer);
    }
  };
  const uiReply = async (path: string, method: string, action: () => Promise<unknown>) => {
    const response = page.waitForResponse(
      (reply) => reply.request().method() === method && new URL(reply.url()).pathname === path,
      { timeout: budget() },
    );
    const [reply] = await Promise.all([response, action()]);
    return reply;
  };
  const visibleCounts = async (preview: Row) => {
    const form = screen.getByRole('group', { name: 'Проверь последствия удаления', exact: true });
    await expect(form.locator('.admin-semester-deletion-counts > div')).toHaveCount(7, {
      timeout: budget(),
    });
    for (const [key, label] of countItems) {
      const item = form
        .locator('.admin-semester-deletion-counts > div')
        .filter({ has: page.getByText(label, { exact: true }) });
      await expect(item.locator('dd')).toHaveText(String(preview.counts[key]), {
        timeout: budget(),
      });
    }
  };
  const previewInUi = async (id: number) => {
    const reply = await uiReply(`/api/academic/semesters/${id}/delete-preview`, 'GET', () =>
      screen
        .locator(`[data-semester-id="${id}"]`)
        .getByRole('button', { name: 'Удалить', exact: true })
        .click({ timeout: budget() }),
    );
    requireValue(reply.status() === 200, 'Vue numeric deletion preview must succeed');
    const preview = await browserBody(reply);
    requireValue(validPreview(preview, id), 'Vue numeric preview must bind its exact semester');
    await visibleCounts(preview);
    return preview;
  };
  page.on('request', observeRequest);
  page.on('response', observeCreate);
  let primaryFailure: { error: unknown } | undefined;
  let cleanupFailure: { error: unknown } | undefined;
  let createBodyReadAttempted = false;
  try {
    const known = await previewInUi(currentSemesterId);
    requireValue(
      known.priorState === 'ACTIVE' &&
        known.stateVersion === before.find((row) => row.id === currentSemesterId)!.stateVersion &&
        countItems.some(([key]) => known.counts[key] > 0),
      'Known semester preview must show genuine nonzero consequences',
    );
    await screen
      .getByRole('button', { name: 'Закрыть предпросмотр', exact: true })
      .click({ timeout: budget() });
    await expect(screen).toHaveAttribute('data-admin-semester-view', 'list', { timeout: budget() });
    requireValue(!foreignDelete, 'Read-only known preview must never issue deletion');
    // No create request may begin unless its failure cleanup still leaves promotion's reserve.
    requireValue(
      caseEnd - performance.now() >= 270000 && budget() > 0,
      'Owned creation must retain all recovery time',
    );
    await screen
      .getByRole('button', { name: 'Добавить семестр', exact: true })
      .click({ timeout: budget() });
    const form = screen.getByRole('form', { name: 'Параметры семестра', exact: true });
    await form
      .getByRole('radio', { name: type === 'AUTUMN' ? 'Осенний' : 'Весенний', exact: true })
      .check({ timeout: budget() });
    await form.getByLabel('Учебный год', { exact: true }).fill(String(year), { timeout: budget() });
    for (const [label, target] of [
      ['Начало', from],
      ['Окончание', to],
    ] as const) {
      await form
        .getByRole('button', { name: label === 'Начало' ? /^Начало / : /^Окончание / })
        .click({ timeout: budget() });
      const calendar = form.getByRole('region', { name: `${label}: выбор даты`, exact: true });
      const firstDay = await calendar
        .locator('button[data-outside="false"]')
        .first()
        .getAttribute('data-date', { timeout: budget() });
      requireValue(
        firstDay && /^\d{4}-\d{2}-\d{2}$/.test(firstDay),
        'Vue semester calendar must expose its real month',
      );
      const distance = monthDistance(firstDay, target);
      requireValue(
        Number.isSafeInteger(distance) && Math.abs(distance) <= 24,
        'Vue semester calendar travel must be bounded',
      );
      for (let index = 0; index < Math.abs(distance); index++)
        await calendar
          .getByRole('button', {
            name: distance > 0 ? 'Следующий месяц' : 'Предыдущий месяц',
            exact: true,
          })
          .click({ timeout: budget() });
      await calendar
        .locator(`button[data-date="${target}"][data-outside="false"]`)
        .click({ timeout: budget() });
    }
    const created = await uiReply('/api/academic/semesters', 'POST', () =>
      form
        .getByRole('button', { name: 'Добавить семестр', exact: true })
        .click({ timeout: budget() }),
    );
    requireValue(created.status() === 201, 'Vue semester metadata creation must succeed once');
    createBodyReadAttempted = true;
    const saved = await browserBody(created);
    requireValue(
      owns(saved),
      'Only the exact new inactive semester may become the deletion target',
    );
    ownId = saved.id;
    const persisted = await call('GET', `/api/academic/semesters/${ownId}`);
    requireValue(
      owns(persisted) && persisted.id === ownId,
      'Vue-created semester metadata must persist exactly',
    );
    await expect(screen.locator(`[data-semester-id="${ownId}"]`)).toContainText(expected.name, {
      timeout: budget(),
    });
    const emptyPreview: Row = await previewInUi(ownId!);
    ownPreview = emptyPreview;
    requireValue(
      emptyPreview.priorState === 'INACTIVE' && emptyCounts(emptyPreview.counts),
      'Only the newly owned empty semester may be deleted',
    );
    await screen
      .getByLabel('Пароль администратора', { exact: true })
      .fill(TEST_USERS.admin.password, { timeout: budget() });
    const removed = await uiReply(`/api/academic/semesters/${ownId}`, 'DELETE', () =>
      screen
        .getByRole('button', { name: 'Удалить семестр безвозвратно', exact: true })
        .click({ timeout: budget() }),
    );
    requireValue(
      [200, 202].includes(removed.status()) &&
        deleteKey &&
        /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(deleteKey),
      'Vue final deletion must carry its one owned idempotency key',
    );
    const body = removed.request().postDataJSON();
    requireValue(
      body.previewDigest === emptyPreview.previewDigest &&
        body.password === TEST_USERS.admin.password,
      'Vue password confirmation must bind the exact displayed digest',
    );
    const operation = await browserBody(removed);
    requireValue(
      operation.semesterId === ownId &&
        operation.operationId === deleteKey &&
        emptyCounts(operation.counts),
      'Vue deletion operation must remain bound to the empty owned semester',
    );
    const progress = screen.getByRole('region', { name: 'Удаление семестра', exact: true });
    await expect(progress.getByText('Удаление завершено.', { exact: true })).toBeVisible({
      timeout: budget(30000),
    });
    const terminal = await call('GET', `/api/academic/semester-deletions/${deleteKey}`);
    requireValue(
      terminal.phase === 'COMPLETED' &&
        terminal.semesterId === ownId &&
        terminal.operationId === deleteKey &&
        emptyCounts(terminal.counts),
      'Final semester deletion must complete its own operation',
    );
    await call('GET', `/api/academic/semesters/${ownId}`, undefined, [404]);
    deleted = true;
    await progress
      .getByRole('button', { name: 'Закрыть результат', exact: true })
      .click({ timeout: budget() });
    await expect(screen).toHaveAttribute('data-admin-semester-view', 'list', { timeout: budget() });
    await expect(screen.locator(`[data-semester-id="${ownId}"]`)).toHaveCount(0, {
      timeout: budget(),
    });
    const unchangedPreview = await call(
      'GET',
      `/api/academic/semesters/${currentSemesterId}/delete-preview`,
    );
    requireValue(
      unchangedPreview.previewDigest === known.previewDigest &&
        unchangedPreview.stateVersion === known.stateVersion,
      'Owned empty deletion must preserve every original semester consequence',
    );
  } catch (error) {
    primaryFailure = { error };
  } finally {
    cleaning = true;
    cleanupEnd = Math.min(performance.now() + 60000, caseEnd - 210000);
    try {
      if (createSent && ownId === undefined) {
        // A lost response never licenses repeating POST or assuming that nothing committed.
        await expect
          .poll(
            async () => {
              if (createdReply?.status() === 201 && !createBodyReadAttempted) {
                createBodyReadAttempted = true;
                try {
                  const candidate = await browserBody(createdReply);
                  if (owns(candidate)) ownId = candidate.id;
                } catch {
                  // A bounded unavailable body still permits exact inventory reconciliation.
                }
              }
              if (ownId === undefined) {
                const matches = (await inventory()).filter(owns);
                requireValue(
                  matches.length <= 1,
                  'Ambiguous created semester ownership must fail closed',
                );
                ownId = matches[0]?.id;
              }
              return (
                ownId !== undefined || (createdReply !== undefined && createdReply.status() >= 400)
              );
            },
            {
              timeout: budget(10000),
              message: 'Owned create outcome must reconcile before cleanup',
            },
          )
          .toBe(true);
      }
      if (ownId !== undefined && !deleted) {
        requireValue(!previousIds.has(ownId), 'Cleanup must never target a preexisting semester');
        let operation: Row | undefined;
        if (deletionSubmitted) {
          requireValue(
            deleteKey &&
              ownPreview &&
              validPreview(ownPreview, ownId) &&
              emptyCounts(ownPreview.counts),
            'Uncertain deletion cleanup requires its exact original command',
          );
          operation = await call(
            'GET',
            `/api/academic/semester-deletions/${deleteKey}`,
            undefined,
            [200, 404],
          );
        }
        if (!operation?.operationId) {
          const candidate = await call('GET', `/api/academic/semesters/${ownId}`);
          requireValue(
            owns(candidate) && candidate.id === ownId,
            'Cleanup owns only its exact inactive metadata tuple',
          );
          const preview = await call('GET', `/api/academic/semesters/${ownId}/delete-preview`);
          requireValue(
            validPreview(preview, ownId) &&
              preview.priorState === 'INACTIVE' &&
              emptyCounts(preview.counts),
            'Cleanup may delete only its still-empty owned semester',
          );
          if (deletionSubmitted)
            requireValue(
              preview.previewDigest === ownPreview!.previewDigest,
              'Uncertain command cleanup cannot replace the shown digest',
            );
          else {
            ownPreview = preview;
            deleteKey = randomUUID();
          }
          operation = await call(
            'DELETE',
            `/api/academic/semesters/${ownId}`,
            { previewDigest: ownPreview!.previewDigest, password: TEST_USERS.admin.password },
            [200, 202],
            { 'Idempotency-Key': deleteKey! },
          );
        }
        requireValue(
          operation.semesterId === ownId &&
            operation.operationId === deleteKey &&
            emptyCounts(operation.counts),
          'Cleanup deletion must retain its original empty target and key',
        );
        await expect
          .poll(
            async () => {
              const current = await call('GET', `/api/academic/semester-deletions/${deleteKey}`);
              requireValue(
                current.semesterId === ownId &&
                  current.operationId === deleteKey &&
                  emptyCounts(current.counts),
                'Cleanup operation scope changed',
              );
              requireValue(current.phase !== 'CANCELLED', 'Owned empty deletion was cancelled');
              return current.phase;
            },
            { timeout: budget(30000), message: 'Owned empty semester deletion cleanup completes' },
          )
          .toBe('COMPLETED');
        await call('GET', `/api/academic/semesters/${ownId}`, undefined, [404]);
      }
      requireValue(!foreignDelete, 'Semester witness must not issue any foreign deletion');
      const after = await inventory();
      const retained = [
        'id',
        'name',
        'dateFrom',
        'dateTo',
        'semesterType',
        'academicYear',
        'active',
        'archived',
        'activationScheduled',
        'stateVersion',
      ];
      equal(
        after.map((row) => fields(row, retained)),
        before.map((row) => fields(row, retained)),
        'Owned semester witness changed the original semester registry',
      );
    } catch (error) {
      cleanupFailure ??= { error };
    } finally {
      page.off('request', observeRequest);
      page.off('response', observeCreate);
    }
  }
  expect
    .soft(cleanupFailure === undefined, 'Owned empty semester cleanup must complete')
    .toBe(true);
  if (primaryFailure) throw primaryFailure.error;
  if (cleanupFailure) throw cleanupFailure.error;
}

test('FULL admin archives and restores the disposable CI semester through real participants', { annotation: diagnosticCase('SEMESTER_ARCHIVE_RESTORE') }, async ({
  playwright,
  page,
}, info) => {
  localSpecialLane('semester-archive');
  requirePrivateArtifacts(info);
  test.setTimeout(420000);
  const browserLoginCutoff = performance.now() + 270000;
  const run = process.env.RCT_SEMESTER_LIFECYCLE_RUN;
  requireValue(!local || run === local.laneRun, 'Local semester checkpoint mismatch');
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
  let archiveKey: string = randomUUID();
  let restoreKey: string = randomUUID();
  const diagnostic = {
    schema: 'semester-lifecycle.v1',
    primary: semesterProgress(),
    cleanup: semesterProgress(),
    primaryFailure: false,
    cleanupOutcome: 'NOT_REQUIRED',
    clockOutcome: 'PENDING',
    disposeOutcome: 'PENDING',
  };
  let primaryFailure: { error: unknown } | undefined;
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
            initialMark.source === 'auto_scheduler' &&
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
    // This report exposes null status/source/revision for a missing stored mark. The old
    // AUTO_SCHEDULER row above therefore precedes the new initial membership below.
    const presentBeforeLateMember = (await headman.call('GET', reportPath)).entries.find(
      (row: Row) => Number(row.userId) === dataset.student.id,
    );
    requireValue(
      presentBeforeLateMember?.status === 'present' &&
        typeof presentBeforeLateMember.markRevision === 'string' &&
        presentBeforeLateMember.markRevision.length > 0 &&
        presentBeforeLateMember.markRevision !== initialMark.markRevision,
      'Explicit historical presence must precede the late initial member',
    );
    const lateMemberBudget = () => {
      // Keep the original browser admission and archive/restore cutoff; no new case window.
      const remaining = Math.floor(browserLoginCutoff - performance.now() - 95000);
      requireValue(remaining > 0, 'Late initial membership must retain lifecycle admission time');
      return Math.min(20000, remaining);
    };
    lateMemberBudget();
    const lateMember = await dataset.user('STUDENT', Number(dataset.group.id));
    const lateStudent = await admit(request, lateMember, 'STUDENT');
    await expect
      .poll(
        async () => {
          const report = await headman.call('GET', reportPath);
          requireValue(
            Number(report.lessonId) === lessonId &&
              Number(report.groupId) === Number(dataset.group.id) &&
              Number(report.semesterId) === ownSemesterId &&
              Number(report.subjectId) === Number(dataset.subject.id),
            'Late initial member report must retain the exact owned historical scope',
          );
          equal(
            report.entries.find((row: Row) => Number(row.userId) === dataset.student.id),
            presentBeforeLateMember,
            'Late initial membership changed the explicit historical presence',
          );
          const entry = report.entries.find((row: Row) => Number(row.userId) === lateMember.id);
          return (
            entry?.status === 'absent' &&
            entry.source === 'auto_scheduler' &&
            typeof entry.markRevision === 'string' &&
            entry.markRevision.length > 0
          );
        },
        {
          timeout: lateMemberBudget(),
          message: 'Late initial member receives a stored historical automatic absence',
        },
      )
      .toBe(true);
    const lateHistoryLesson = (await lessonRows()).find((row) => Number(row.id) === lessonId);
    requireValue(
      lateHistoryLesson &&
        Number.isSafeInteger(Number(lateHistoryLesson.occurrenceId)) &&
        Number(lateHistoryLesson.occurrenceId) > 0,
      'Late initial membership requires the same canonical historical occurrence',
    );
    const lateHistory = history(
      await lateStudent.call('GET', `/api/v1/student/attendance?semesterId=${ownSemesterId}`),
    );
    requireValue(
      lateHistory.some(
        (subject: Row) =>
          Number(subject.id) === Number(dataset.subject.id) &&
          subject.typeCards.some((card: Row) =>
            card.history.some(
              (segment: Row) =>
                segment.id === String(lateHistoryLesson.occurrenceId) &&
                segment.status === 'ABSENT',
            ),
          ),
      ),
      'Late initial member reads the stored absence in the same historical occurrence',
    );
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
    const remainingLoginMs = Math.floor(browserLoginCutoff - performance.now());
    requireValue(remainingLoginMs > 0, 'Browser activation login must retain recovery time');
    const browserAdmin = await loginPwa(page, TEST_USERS.admin, {
      retryUntilMs: Date.now() + Math.min(65000, remainingLoginMs),
    });
    requireValue(
      browserAdmin.session.userId === admin.session.userId,
      'Browser activation must use the same owned FULL administrator',
    );
    if (browserAdmin.session.activeRole !== 'ADMIN') await selectPwaRole(page, 'Администратор');
    await page.getByRole('button', { name: 'Ещё', exact: true }).click();
    await page.getByRole('button', { name: 'Семестры', exact: true }).click();
    const ownCard = page.locator(`[data-semester-id="${ownSemesterId}"]`);
    await expect(ownCard).toBeVisible();
    const screen = page.locator('main.admin-semester-screen');
    const uiBudget = (cap = 10000): number => {
      const remaining = Math.floor(browserLoginCutoff - performance.now());
      requireValue(remaining > 0, 'Vue semester lifecycle must retain original recovery time');
      return Math.min(cap, remaining);
    };
    const archiveFromUi = async (action: 'ARCHIVE' | 'RESTORE') => {
      const actionPath = `/api/academic/semesters/${ownSemesterId}/${action === 'ARCHIVE' ? 'archive' : 'restore'}`;
      const initialLabel = action === 'ARCHIVE' ? 'Архивировать' : 'Восстановить';
      const confirmationLabel =
        action === 'ARCHIVE' ? 'Подтверди архивацию' : 'Подтверди восстановление';
      const submitLabel =
        action === 'ARCHIVE' ? 'Подтвердить архивацию' : 'Подтвердить восстановление';
      await ownCard
        .getByRole('button', { name: initialLabel, exact: true })
        .click({ timeout: uiBudget() });
      const confirmation = screen.getByRole('group', { name: confirmationLabel, exact: true });
      await expect(confirmation).toBeVisible({ timeout: uiBudget() });
      let commandRequest: import('@playwright/test').Request | undefined;
      let commandResponse: Response | undefined;
      let duplicateCommand = false;
      const observeCommand = (value: import('@playwright/test').Request) => {
        const url = new URL(value.url());
        if (
          url.origin !== origin.origin ||
          url.pathname !== actionPath ||
          value.method() !== 'POST'
        )
          return;
        if (commandRequest && commandRequest !== value) {
          duplicateCommand = true;
          return;
        }
        commandRequest = value;
        const key = value.headers()['idempotency-key'];
        if (
          typeof key === 'string' &&
          /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(key)
        ) {
          if (action === 'ARCHIVE') archiveKey = key;
          else restoreKey = key;
        }
      };
      const matchesResponse = (value: Response) => value.request() === commandRequest;
      const observeResponse = (value: Response) => {
        if (matchesResponse(value)) commandResponse ??= value;
      };
      page.on('request', observeCommand);
      page.on('response', observeResponse);
      let operation: Row;
      try {
        await confirmation
          .getByRole('button', { name: submitLabel, exact: true })
          .click({ timeout: uiBudget() });
        const response =
          commandResponse ?? (await page.waitForResponse(matchesResponse, { timeout: uiBudget() }));
        requireValue(
          commandRequest && !duplicateCommand && [200, 202, 503].includes(response.status()),
          'Vue archive command must retain one bound response',
        );
        const key = commandRequest.headers()['idempotency-key'];
        requireValue(
          key === (action === 'ARCHIVE' ? archiveKey : restoreKey) &&
            /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(key),
          'Vue archive command must retain its actual idempotency key',
        );
        let bodyTimer: ReturnType<typeof setTimeout> | undefined;
        try {
          operation = await Promise.race([
            response.json(),
            new Promise<never>((_, reject) => {
              bodyTimer = setTimeout(
                () => reject(new Error('Vue archive response body deadline')),
                uiBudget(5000),
              );
            }),
          ]);
        } finally {
          if (bodyTimer !== undefined) clearTimeout(bodyTimer);
        }
        requireValue(
          Number(operation.semesterId) === ownSemesterId &&
            operation.action === action &&
            typeof operation.operationId === 'string' &&
            operation.operationId.length > 0 &&
            (response.status() !== 503 ||
              (operation.operationState === 'ERROR' && typeof operation.retryable === 'boolean')),
          'Vue archive response must identify its owned typed operation',
        );
      } finally {
        page.off('response', observeResponse);
        page.off('request', observeCommand);
      }
      const settled = await terminal(
        admin!,
        ownSemesterId!,
        action,
        action === 'ARCHIVE' ? archiveKey : restoreKey,
        diagnostic.primary,
        uiBudget(120000),
        operation,
      );
      await assertOwnedSemesterUi(screen, page, String(previousActive!.name), ownCard, action, uiBudget);
      return settled;
    };
    clock.assertUnchanged();
    archiveStarted = true;
    const archived = await archiveFromUi('ARCHIVE');
    diagnostic.primary.stage = 'ARCHIVED_READBACK';
    requireValue(
      (await semesters(admin)).every((row) => row.active === false),
      'Archive must not choose another active semester',
    );
    await inactiveStudentHistory(request, student, historyPath);
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
    await inactiveStudentHistory(request, student, historyPath);
    const afterRefusals = await admin.call(
      'GET',
      `/api/academic/semesters/${ownSemesterId}/archive/status`,
    );
    requireValue(
      afterRefusals.stateVersion === archived.stateVersion &&
        afterRefusals.operation.operationId === archived.operation.operationId,
      'Refused writers changed the archive authority',
    );
    await archiveFromUi('RESTORE');
    diagnostic.primary.stage = 'RESTORED_READBACK';
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
    await inactiveStudentHistory(request, student, historyPath);
    equal(await markProjection(), beforeMark, 'Restore changed historical attendance');
    equal(await scheduleProjection(), beforeSchedule, 'Restore changed canonical lessons');
    equal(
      fields(await headman.call('GET', homeworkPath), homeworkFields),
      beforeHomework,
      'Restore changed homework contents',
    );
    // The original activation command now comes from the real FULL-admin Vue controls.
    // Keep the educational fixture clock unchanged; the UI uses the browser's Moscow date.
    const browserDate = await page.evaluate(() => {
      const parts = new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Europe/Moscow',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
      }).formatToParts(new Date());
      const part = (type: string) => parts.find((item) => item.type === type)?.value ?? '';
      return `${part('year')}-${part('month')}-${part('day')}`;
    });
    requireValue(
      inactive.dateFrom <= browserDate && inactive.dateTo >= browserDate,
      'Owned activation semester must also cover the real browser Moscow date',
    );
    await expect(ownCard.getByRole('img', { name: 'Неактивный', exact: true })).toBeVisible();
    await ownCard.getByRole('button', { name: 'Сделать текущим', exact: true }).click();
    const activationPath = `/api/academic/semesters/${ownSemesterId}/activate`;
    const activationMatch = (response: Response) =>
      new URL(response.url()).pathname === activationPath &&
      response.request().method() === 'PATCH';
    let observedActivation: Response | undefined;
    const observeActivation = (response: Response) => {
      if (activationMatch(response)) observedActivation ??= response;
    };
    page.on('response', observeActivation);
    let current: Row;
    try {
      await page.getByRole('button', { name: 'Подтвердить активацию', exact: true }).click();
      const response = observedActivation ?? (await page.waitForResponse(activationMatch));
      requireValue(response.status() === 200, 'Vue semester activation must succeed');
      current = await response.json();
    } finally {
      page.off('response', observeActivation);
    }
    requireValue(
      current.active === true && current.archived === false,
      'Explicit activation must restore current semester authority',
    );
    const activeAfterUi = (await semesters(admin)).filter((row) => row.active === true);
    requireValue(
      activeAfterUi.length === 1 && Number(activeAfterUi[0].id) === ownSemesterId,
      'Vue activation must leave exactly its owned semester current',
    );
    await expect(ownCard.getByRole('img', { name: 'Активный', exact: true })).toBeVisible();
    await expect(ownCard.getByRole('button', { name: 'Сделать текущим', exact: true })).toHaveCount(
      0,
    );
    equal(
      history(await student.call('GET', historyPath)),
      beforeHistory,
      'Archive and restore changed student history after explicit activation',
    );
    diagnostic.primary.stage = 'PERMITTED_WRITE';
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
    diagnostic.primary.stage = 'COMPLETED';
  } catch (error) {
    primaryFailure = { error };
    diagnostic.primaryFailure = true;
    retainFirstDatasetHttpFailure(info.annotations, error);
  } finally {
    let cleanupFailure: { error: unknown } | undefined;
    try {
      if (admin && originalAuthorityCaptured && previousActive && ownSemesterId) {
        diagnostic.cleanupOutcome = 'PENDING';
        diagnostic.cleanup.stage = 'CLEANUP_STATUS';
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
          observeSemester(diagnostic.cleanup, status);
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
              await terminal(
                cleanupAdmin,
                ownSemesterId,
                'ARCHIVE',
                archiveKey,
                diagnostic.cleanup,
                30000,
              );
            }
            await terminal(
              cleanupAdmin,
              ownSemesterId,
              'RESTORE',
              restoreKey,
              diagnostic.cleanup,
              60000,
            );
          }
          diagnostic.cleanup.stage = 'ORIGINAL_AUTHORITY';
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
        } catch (error) {
          cleanupFailure = { error };
        } finally {
          try {
            await cleanupRequest.dispose();
          } catch (error) {
            cleanupFailure ??= { error };
          }
        }
        if (cleanupFailure) throw cleanupFailure.error;
        diagnostic.cleanup.stage = 'COMPLETED';
        diagnostic.cleanupOutcome = 'COMPLETED';
      }
    } catch (error) {
      diagnostic.cleanupOutcome = 'FAILED';
      cleanupFailure = { error };
    }
    try {
      clock.assertUnchanged();
      diagnostic.clockOutcome = 'PASSED';
    } catch (error) {
      diagnostic.clockOutcome = 'FAILED';
      cleanupFailure ??= { error };
    }
    try {
      await request.dispose();
      diagnostic.disposeOutcome = 'PASSED';
    } catch (error) {
      diagnostic.disposeOutcome = 'FAILED';
      cleanupFailure ??= { error };
    }
    try {
      const directory = lstatSync(run);
      const markerState = lstatSync(marker);
      requireValue(
        directory.isDirectory() &&
          !directory.isSymbolicLink() &&
          (directory.mode & 0o777) === 0o700 &&
          markerState.isFile() &&
          !markerState.isSymbolicLink() &&
          (markerState.mode & 0o777) === 0o600 &&
          markerState.size <= 64 &&
          readFileSync(marker, 'utf8').trim() === 'RCT_SEMESTER_DISPOSABLE',
        'Semester diagnostic requires the original owned directory',
      );
      const bytes = JSON.stringify(diagnostic);
      requireValue(Buffer.byteLength(bytes) <= 4096, 'Semester diagnostic exceeds its bound');
      // O_EXCL creation also rejects an existing symbolic link; never overwrite private inputs.
      writeFileSync(join(run, 'lifecycle.safe.json'), bytes, { flag: 'wx', mode: 0o600 });
    } catch (error) {
      cleanupFailure ??= { error };
    }
    if (primaryFailure) throw primaryFailure.error;
    if (cleanupFailure) throw cleanupFailure.error;
  }
});

test('FULL admin manages owned semester metadata and historical group promotion through the ready UI', async ({
  playwright,
  page,
}, info) => {
  localSpecialLane('semester-metadata');
  requirePrivateArtifacts(info);
  test.setTimeout(420000);
  const promotionCaseEnd = promotionStartedAt.get(info)! + info.timeout - 10000;
  const setupEnd = Math.min(performance.now() + 80000, promotionCaseEnd - 330000);
  const setupBudget = (cap = 10000): number => {
    const remaining = Math.floor(setupEnd - performance.now());
    requireValue(
      remaining > 0,
      'Independent admin UI admission must retain original helper reserves',
    );
    return Math.min(cap, remaining);
  };
  const run = process.env.RCT_SEMESTER_LIFECYCLE_RUN;
  requireValue(!local || run === local.laneRun, 'Local semester checkpoint mismatch');
  requireValue(run && isAbsolute(run), 'Independent admin UI requires its private run directory');
  for (const path of [run, join(run, 'artifacts')]) {
    const stat = lstatSync(path);
    requireValue(
      stat.isDirectory() && !stat.isSymbolicLink() && (stat.mode & 0o777) === 0o700,
      'Independent admin UI directories must be private',
    );
  }
  const marker = join(run, 'disposable-target');
  const markerInfo = lstatSync(marker);
  const baseURL = String(info.project.use.baseURL);
  requireValue(
    markerInfo.isFile() &&
      !markerInfo.isSymbolicLink() &&
      (markerInfo.mode & 0o777) === 0o600 &&
      markerInfo.size <= 64 &&
      readFileSync(marker, 'utf8').trim() === 'RCT_SEMESTER_DISPOSABLE' &&
      info.project.outputDir === join(run, 'artifacts') &&
      baseURL === 'https://localhost:8443' &&
      process.env.RCT_E2E_SYNTHETIC_SEED === 'true',
    'Independent admin UI requires exact disposable CI ownership',
  );
  const clock = unchangedClock();
  const request = await playwright.request.newContext({
    baseURL,
    ignoreHTTPSErrors: false,
    timeout: 3000,
  });
  let primaryFailure: { error: unknown } | undefined;
  let cleanupFailure: { error: unknown } | undefined;
  const body = async (read: () => Promise<Row>): Promise<Row> => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    try {
      return await Promise.race([
        read(),
        new Promise<never>((_, reject) => {
          timer = setTimeout(
            () => reject(new Error('Independent admin admission body deadline')),
            setupBudget(5000),
          );
        }),
      ]);
    } finally {
      if (timer !== undefined) clearTimeout(timer);
    }
  };
  try {
    const admissionTimeout = setupBudget(80000);
    const admissionWork = (async () => {
      const browser = await loginPwa(page, TEST_USERS.admin, {
        retryUntilMs: Date.now() + setupBudget(65000),
      });
      let token = browser.accessToken;
      let session = browser.session;
      if (session.activeRole !== 'ADMIN') {
        let observedRole: Response | undefined;
        const matchesRole = (response: Response) => {
          const url = new URL(response.url());
          return (
            url.origin === baseURL &&
            url.pathname === '/api/auth/session/active-role' &&
            response.request().method() === 'PUT'
          );
        };
        const observeRole = (response: Response) => {
          if (matchesRole(response)) observedRole ??= response;
        };
        page.on('response', observeRole);
        try {
          if (
            !(await page.getByRole('heading', { name: 'Сменить роль', exact: true }).isVisible())
          ) {
            await page
              .getByRole('button', { name: 'Профиль', exact: true })
              .click({ timeout: setupBudget() });
            await page
              .getByRole('button', { name: 'Сменить роль', exact: true })
              .click({ timeout: setupBudget() });
          }
          await page
            .getByRole('button', { name: /Администратор/ })
            .click({ timeout: setupBudget() });
          const response =
            observedRole ?? (await page.waitForResponse(matchesRole, { timeout: setupBudget() }));
          requireValue(
            response.status() === 200,
            'Independent admin UI role selection must succeed',
          );
          const selected = await body(() => response.json());
          token = selected.accessToken;
          session = selected.session;
          await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible({
            timeout: setupBudget(),
          });
        } finally {
          page.off('response', observeRole);
        }
      }
      requireValue(
        typeof token === 'string' &&
          token.length > 0 &&
          session?.activeRole === 'ADMIN' &&
          session.userId === browser.session.userId &&
          session.sessionId === browser.session.sessionId &&
          typeof session.sessionVersion === 'string' &&
          session.sessionVersion.length > 0,
        'Independent admin UI must retain its same authenticated FULL-admin session',
      );
      return { token, session };
    })();
    let admissionTimer: ReturnType<typeof setTimeout> | undefined;
    let admitted: Awaited<typeof admissionWork>;
    try {
      admitted = await Promise.race([
        admissionWork,
        new Promise<never>((_, reject) => {
          admissionTimer = setTimeout(
            () => {
              reject(new Error('Independent admin UI admission deadline'));
            },
            Math.max(1, Math.min(admissionTimeout, Math.floor(setupEnd - performance.now()))),
          );
        }),
      ]);
    } catch (error) {
      let closeTimer: ReturnType<typeof setTimeout> | undefined;
      try {
        await Promise.race([
          (async () => {
            await page.close({ runBeforeUnload: false });
            await admissionWork.then(
              () => undefined,
              () => undefined,
            );
          })(),
          new Promise<never>((_, reject) => {
            closeTimer = setTimeout(
              () => reject(new Error('Independent admin admission cancellation deadline')),
              5000,
            );
          }),
        ]);
      } catch (cancellationError) {
        cleanupFailure ??= { error: cancellationError };
      } finally {
        if (closeTimer !== undefined) clearTimeout(closeTimer);
      }
      throw error;
    } finally {
      if (admissionTimer !== undefined) clearTimeout(admissionTimer);
    }
    const { token, session } = admitted;
    const admin = new Actor(request, token, session);
    const read = async (path: string): Promise<Row> => {
      const response = await request.get(path, {
        headers: admin.headers(),
        timeout: setupBudget(3000),
      });
      try {
        requireValue(response.status() === 200, 'Independent admin admission read must succeed');
        return await body(() => response.json());
      } finally {
        await response.dispose();
      }
    };
    const access = await read('/api/academic/admin-access/me');
    requireValue(
      access.mode === 'FULL' &&
        access.assigned === true &&
        String(access.userId) === session.userId,
      'Independent admin UI requires current assigned FULL authority',
    );
    const inventory = await read('/api/academic/semesters?size=100');
    const rows: Row[] = inventory._embedded?.semesterResponseList ?? [];
    requireValue(
      Array.isArray(rows) &&
        rows.length <= 100 &&
        inventory.page?.number === 0 &&
        inventory.page.totalElements === rows.length &&
        rows.every(
          (row) =>
            Number.isSafeInteger(Number(row.id)) &&
            Number(row.id) > 0 &&
            row.transition === 'NONE' &&
            row.releasePending === false &&
            row.activationScheduled === false,
        ) &&
        new Set(rows.map((row) => Number(row.id))).size === rows.length,
      'Independent admin UI needs a complete stable semester inventory',
    );
    const active = rows.filter((row) => row.active === true);
    requireValue(active.length === 1, 'Independent admin UI requires exactly one current semester');
    const currentSemesterId = Number(active[0].id);
    const current = await read(`/api/academic/semesters/${currentSemesterId}`);
    requireValue(
      Number(current.id) === currentSemesterId &&
        current.stateVersion === active[0].stateVersion &&
        current.active === true &&
        current.archived === false &&
        current.activationScheduled === false &&
        current.transition === 'NONE' &&
        current.releasePending === false,
      'Independent admin UI current semester authority must remain unchanged',
    );
    await page.getByRole('button', { name: 'Ещё', exact: true }).click({ timeout: setupBudget() });
    await page
      .getByRole('button', { name: 'Семестры', exact: true })
      .click({ timeout: setupBudget() });
    await expect(page.locator('main.admin-semester-screen')).toHaveAttribute(
      'data-admin-semester-view',
      'list',
      { timeout: setupBudget() },
    );
    await expect(
      page
        .locator(`[data-semester-id="${currentSemesterId}"]`)
        .getByRole('img', { name: 'Активный', exact: true }),
    ).toBeVisible({ timeout: setupBudget() });
    setupBudget(1);
    clock.assertUnchanged();
    await ownedSemesterMetadataAndDeletion(
      page,
      admin,
      promotionCaseEnd,
      currentSemesterId,
      clock.instant,
    );
    await ownedHistoricalPromotion(page, request, admin, promotionCaseEnd, currentSemesterId);
  } catch (error) {
    primaryFailure = { error };
    retainFirstDatasetHttpFailure(info.annotations, error);
  } finally {
    try {
      clock.assertUnchanged();
    } catch (error) {
      cleanupFailure ??= { error };
    }
    let disposeTimer: ReturnType<typeof setTimeout> | undefined;
    try {
      await Promise.race([
        request.dispose(),
        new Promise<never>((_, reject) => {
          disposeTimer = setTimeout(
            () => reject(new Error('Independent admin request disposal deadline')),
            5000,
          );
        }),
      ]);
    } catch (error) {
      cleanupFailure ??= { error };
    } finally {
      if (disposeTimer !== undefined) clearTimeout(disposeTimer);
    }
    if (primaryFailure) throw primaryFailure.error;
    if (cleanupFailure) throw cleanupFailure.error;
  }
});

// One explicit synthetic operator action; never an automatic SENT/DLQ redriver.
test('FULL admin reconciles one exact stranded close before archive completion', async ({
  playwright,
}, info) => {
  localSpecialLane('semester-reconciliation');
  requirePrivateArtifacts(info);
  test.setTimeout(420000);
  const end = performance.now() + info.timeout - Math.max(info.duration, 10000);
  const run = process.env.RCT_SEMESTER_LIFECYCLE_RUN;
  requireValue(!local || run === local.laneRun, 'Local semester checkpoint mismatch');
  requireValue(run && isAbsolute(run), 'Reconciliation requires a private run directory');
  for (const path of [run, join(run, 'artifacts')]) {
    const stat = lstatSync(path);
    requireValue(
      stat.isDirectory() && !stat.isSymbolicLink() && (stat.mode & 0o777) === 0o700,
      'Reconciliation directories must be private',
    );
  }
  const marker = join(run, 'disposable-target');
  const markerStat = lstatSync(marker);
  requireValue(
    markerStat.isFile() &&
      !markerStat.isSymbolicLink() &&
      markerStat.size <= 64 &&
      (markerStat.mode & 0o777) === 0o600 &&
      readFileSync(marker, 'utf8').trim() === 'RCT_SEMESTER_DISPOSABLE' &&
      info.project.outputDir === join(run, 'artifacts') &&
      info.project.use.baseURL === 'https://localhost:8443' &&
      process.env.RCT_E2E_SYNTHETIC_SEED === 'true',
    'Exact disposable CI admission is required',
  );
  const clock = unchangedClock();
  const root = resolve(__dirname, '../../..');
  const envFile = (local ? join(local.run, 'synthetic.env') : join(root, 'tests/e2e/.env.ci'));
  const envStat = lstatSync(envFile);
  requireValue(
    envStat.isFile() &&
      !envStat.isSymbolicLink() &&
      envStat.size <= 65536 &&
      (envStat.mode & 0o022) === 0,
    'Synthetic CI configuration must be bounded and private',
  );
  const wanted = [
    'POSTGRES_ACADEMIC_PASSWORD',
    'POSTGRES_SCHEDULE_PASSWORD',
    'MONGO_ROOT_PASSWORD',
    'RABBITMQ_USER',
    'RABBITMQ_PASSWORD',
  ];
  const secret: Record<string, string> = {};
  for (const line of readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const match = /^([A-Z_]+)=(.*)$/.exec(line);
    if (match && wanted.includes(match[1])) {
      let value = match[2];
      if (value.length >= 2 && ['"', "'"].includes(value[0]) && value.at(-1) === value[0])
        value = value.slice(1, -1);
      secret[match[1]] = value;
    }
  }
  requireValue(
    wanted.every((key) => secret[key]),
    'Synthetic CI credentials are required',
  );
  type OwnedServiceState = {
    running: boolean | 'UNKNOWN';
    health: 'HEALTHY' | 'STARTING' | 'UNHEALTHY' | 'UNKNOWN';
    samePinnedId: boolean | 'UNKNOWN';
    sameNetworkAddresses: boolean | 'UNKNOWN';
  };
  const replayStages = ['RECEIPT', 'PAYLOAD', 'PREPARE', 'TRANSACTION'] as const;
  const replayCategories = [
    'ACADEMIC_UNAVAILABLE',
    'SCHEDULE_UNAVAILABLE',
    'RESOURCE_NOT_FOUND',
    'INVALID_ARGUMENT',
    'TRANSACTION_ROLLBACK',
    'MONGO_TRANSACTION',
    'DUPLICATE_KEY',
    'EFFECT_REJECTED',
    'OTHER',
  ] as const;
  const replayMethods = ['NONE', 'ACADEMIC_GROUP_MEMBERS', 'SCHEDULE_LESSON_BY_ID'] as const;
  const replayStatuses = [
    'NONE',
    'OK',
    'CANCELLED',
    'UNKNOWN',
    'INVALID_ARGUMENT',
    'DEADLINE_EXCEEDED',
    'NOT_FOUND',
    'ALREADY_EXISTS',
    'PERMISSION_DENIED',
    'RESOURCE_EXHAUSTED',
    'FAILED_PRECONDITION',
    'ABORTED',
    'OUT_OF_RANGE',
    'UNIMPLEMENTED',
    'INTERNAL',
    'UNAVAILABLE',
    'DATA_LOSS',
    'UNAUTHENTICATED',
  ] as const;
  const replayCauses = [
    'NONE',
    'SEMANTIC_REJECTION',
    'MONGO_TRANSIENT_TRANSACTION',
    'MONGO_UNKNOWN_COMMIT',
    'MONGO_WRITE_CONFLICT',
    'MONGO_DUPLICATE_KEY',
    'MONGO_OTHER',
    'TRANSACTION_ROLLBACK',
    'OTHER',
    'CONNECT',
    'DNS',
    'TLS',
    'CHANNEL_SHUTDOWN',
    'TRANSPORT_IO',
    'SERVER_UNAVAILABLE',
    'UNKNOWN',
  ] as const;
  const ackCategories = [
    'INVALID_ARGUMENT',
    'SQL_PERMISSION',
    'SQL_UNDEFINED_TABLE',
    'SQL_UNDEFINED_COLUMN',
    'SQL_CONSTRAINT',
    'SQL_SERIALIZATION',
    'SQL_DEADLOCK',
    'SQL_CONNECTION',
    'SQL_OTHER',
    'DATA_ACCESS',
    'OTHER',
  ] as const;
  type ReplayWarn = {
    stage: (typeof replayStages)[number];
    category: (typeof replayCategories)[number];
    rpc: (typeof replayMethods)[number];
    status: (typeof replayStatuses)[number];
    cause: (typeof replayCauses)[number];
  };
  type ReplayLog<T> = {
    state: 'REPORTED' | 'UNKNOWN';
    unknownReason:
      | 'NONE'
      | 'NO_SOURCE_EVENT'
      | 'NO_ACK_EVENT'
      | 'LOG_READ'
      | 'LOG_BYTE_LIMIT'
      | 'LOG_TAIL_LIMIT'
      | 'LOG_FORMAT'
      | 'LOG_RECORD_SHAPE'
      | 'TARGET_TEMPLATE'
      | 'TARGET_CORRELATION'
      | 'MATCH_COUNT_LIMIT'
      | 'NO_MATCHING_WARN';
    matchedCount: number;
    last: T | null;
  };
  type ReplayChecks = {
    ledgerApplied: boolean;
    oneClaim: boolean;
    oneReceipt: boolean;
    receiptApplied: boolean;
    receiptSourceEventMatches: boolean;
    receiptTargetMatches: boolean;
    receiptSemesterMatches: boolean;
    receiptTypeMatches: boolean;
    receiptHashMatches: boolean;
    ackEventMatchesLedger: boolean;
    ackSourceMatches: boolean;
    ackTypeMatches: boolean;
    ackVersionMatches: boolean;
    ackSourceEventMatches: boolean;
    ackHashMatches: boolean;
    ackTargetMatches: boolean;
    ackApplied: boolean;
    ackSemesterMatches: boolean;
    ackSourceTypeMatches: boolean;
  };
  type CountClass = 'ZERO' | 'ONE' | 'MANY' | 'INVALID';
  const readinessSources = {
    GATEWAY_ISSUER_CLIENT: {
      logger: 'ru.rutcampustrack.gateway.security.InternalJwtIssuerClient',
      stages: ['REQUEST_BUILD', 'AUTH_TRANSPORT', 'AUTH_HTTP', 'AUTH_RESPONSE'],
    },
    GATEWAY_ISSUER_FILTER: {
      logger: 'ru.rutcampustrack.gateway.security.InternalJwtIssuerFilter',
      stages: ['EXPECTATIONS', 'FORWARDED_IDENTITY'],
    },
    ACADEMIC_CONTEXT: {
      logger: 'ru.rutcampustrack.academic.security.AcademicUserContextFilter',
      stages: ['CURRENT_CONTEXT'],
    },
    SCHEDULE_CLIENT: {
      logger: 'ru.rutcampustrack.academic.grpc.ScheduleGrpcClient',
      stages: ['SCHEDULE_PREVIEW'],
    },
    ACADEMIC_PREVIEW: {
      logger: 'ru.rutcampustrack.academic.semester.SemesterDeletionPreviewService',
      stages: [
        'ATTENDANCE_PREVIEW',
        'ECHO_FENCE',
        'ACADEMIC_DIGEST',
        'SCHEDULE_DIGEST',
        'ATTENDANCE_DIGEST',
        'COUNTS',
      ],
    },
  } as const;
  const readinessCauses = [
    'DNS',
    'CONNECT',
    'TLS',
    'TIMEOUT',
    'TRANSPORT_IO',
    'RPC_STATUS',
    'CREDENTIAL_CONFIGURATION',
    'HTTP_REJECTED',
    'RESPONSE_REJECTED',
    'IDENTITY_REJECTED',
    'INVALID_ARGUMENT',
    'ECHO_OR_FENCE_INVALID',
    'DIGEST_INVALID',
    'NEGATIVE_COUNT',
    'UNEXPECTED',
    'UNKNOWN',
  ] as const;
  const readinessChannelStates = [
    'READY',
    'IDLE',
    'CONNECTING',
    'TRANSIENT_FAILURE',
    'SHUTDOWN',
    'UNKNOWN',
  ] as const;
  const readinessConnectKinds = ['REFUSED', 'TIMEOUT', 'NO_ROUTE', 'OTHER', 'UNKNOWN'] as const;
  type ReadinessWarn = {
    source: keyof typeof readinessSources;
    stage: string;
    status: string;
    cause: (typeof readinessCauses)[number];
    scheduleTransport: {
      channelBefore: (typeof readinessChannelStates)[number];
      channelAtFailure: (typeof readinessChannelStates)[number];
      connectKind: (typeof readinessConnectKinds)[number];
      endpointRelation: 'UNKNOWN';
    } | null;
  };
  type ReadinessLog = {
    state: 'REPORTED' | 'UNKNOWN';
    unknownReason:
      | 'NONE'
      | 'NO_REQUEST'
      | 'LOG_READ'
      | 'LOG_BYTE_LIMIT'
      | 'LOG_TAIL_LIMIT'
      | 'LOG_FORMAT'
      | 'LOG_RECORD_SHAPE'
      | 'TARGET_TEMPLATE'
      | 'TARGET_CORRELATION'
      | 'MATCH_COUNT_LIMIT'
      | 'NO_MATCHING_WARN'
      | 'AMBIGUOUS';
    matchedCount: number;
    last: ReadinessWarn | null;
  };
  const diagnostic = {
    schema: 'semester-reconciliation.v11',
    phase: 'SETUP',
    configuredListenerAttempts: 3, // Per delivery; one delayed redrive permits at most six total.
    measuredListenerAttempts: 'UNKNOWN',
    exactDlqEnvelope: false,
    pendingBeforeReplay: false,
    appliedAfterReplay: false,
    sameArchiveCompleted: false,
    duplicateBusinessUnchanged: false,
    recoveryPublishAttempted: false,
    duplicateAttempted: false,
    retainedDlqCopies: 'UNKNOWN' as number | 'UNKNOWN',
    restorationSucceeded: false,
    clockUnchanged: false,
    cleanupOutcome: 'PENDING',
    firstRestartFailure: null as {
      phase: 'MAIN' | 'CLEANUP';
      service: 'ATTENDANCE' | 'SCHEDULE';
      cause: 'NOT_RUNNING' | 'HEALTH_STARTING' | 'HEALTH_UNHEALTHY' | 'HEALTH_UNKNOWN';
    } | null,
    mainCloseLastRead: null as {
      targetPresent: boolean;
      groupMatches: boolean;
      semesterMatches: boolean;
      status: 'PLANNED' | 'ACTIVE' | 'CLOSED' | 'OTHER_OR_ABSENT';
    } | null,
    firstHttp500OwnedServices: null as {
      ATTENDANCE: OwnedServiceState;
      SCHEDULE: OwnedServiceState;
    } | null,
    strandedLastRead: null as {
      deadReadyOne: boolean;
      deadUnackedZero: boolean;
      mainReadyZero: boolean;
      mainUnackedZero: boolean;
      deadReadyClass: 'ZERO' | 'ONE' | 'MANY' | 'UNKNOWN';
    } | null,
    recoveryHealthLastRead: null as RecoveryHealthRead | null,
    academicRecoveryHealthLastRead: null as {
      channelState: RecoveryHealthRead['channelState'];
      fresh: boolean;
    } | null,
    readinessFailureLog: null as ReadinessLog | null,
    archiveReadinessLastRead: null as {
      httpStatus: number | null;
      transport: NonNullable<
        ReturnType<typeof safeNativePrimaryFailures>[number]['transportHint']
      > | null;
      semesterMatches: boolean | null;
      priorStateActive: boolean | null;
      stateVersionMatches: boolean | null;
    } | null,
    replayLastRead: null as {
      ledgerState: 'PENDING' | 'APPLIED' | 'ERROR' | 'OTHER';
      ledgerReceiptIdPresent: boolean;
      claimCount: CountClass;
      receiptCount: CountClass;
      ackCount: CountClass;
      receiptResult: 'APPLIED' | 'ERROR' | 'OTHER_OR_ABSENT';
      ackOutboxStatus: 'PENDING' | 'SENT' | 'OTHER_OR_ABSENT';
      checks: ReplayChecks;
      firstFailed: keyof ReplayChecks | 'NONE';
    } | null,
    replayFailureLog: null as {
      attendance: ReplayLog<ReplayWarn>;
      schedule: ReplayLog<{ category: (typeof ackCategories)[number] }>;
    } | null,
    setupQuiescenceLastRead: null as {
      firstFailed: string;
      mainReady: number | null;
      mainUnacked: number | null;
      mainConsumers: number | null;
      deadReady: number | null;
      deadUnacked: number | null;
      retryReady: number | null;
      retryUnacked: number | null;
      scheduleUnproven: number | null;
      scheduleLegacy: number | null;
      scheduleDue: number | null;
      initialExpectedApplied: boolean;
      initialNoPendingWork: boolean;
      initialProducerSent: boolean;
    } | null,
  };
  let strandedLastRead: typeof diagnostic.strandedLastRead = null;
  const stopped = new Set<string>();
  let eventAccepted = false,
    applied = false,
    archiveStarted = false,
    archived = false;
  let restored = false,
    authorityDirty = false,
    cleaning = false;
  let cleanupStage: LifecycleCleanupCause = 'CLEANUP_SEMESTER_RESTART';
  let closeDeadline = 0;
  let producerDeadline = 0;
  let lessonClosed = false;
  let effectCaptured = false;
  // Capture recovery shares the original absolute close/producer windows: it cannot reset them.
  // Add30s for exact command/read callbacks, then the existing95s/35s effect settlement.
  // Restart60s each, archive/restore30s each, authority15s and finalqueue/disposal15s remain.
  const reserve = (next: { stop?: string; event?: boolean; archive?: boolean } = {}) => {
    const pending = new Set(stopped);
    if (next.stop) pending.add(next.stop);
    return (
      pending.size * 60000 +
      ((eventAccepted || next.event) && !applied
        ? (diagnostic.recoveryPublishAttempted ? 35000 : 95000) +
          (effectCaptured
            ? 0
            : (lessonClosed
                ? 0
                : Math.max(0, (closeDeadline || performance.now() + 90000) - performance.now())) +
              (producerDeadline ? Math.max(0, producerDeadline - performance.now()) : 10000) +
              30000)
        : 0) +
      ((archiveStarted || next.archive) && !archived ? 30000 : 0) +
      ((archiveStarted || next.archive) && !restored ? 30000 : 0) +
      (authorityDirty || next.archive ? 15000 : 0) +
      15000
    );
  };
  const budget = (cap: number, next = {}, action = false) => {
    const remaining = end - performance.now() - (cleaning ? 0 : reserve(next));
    requireValue(remaining >= (action ? cap : 1000), 'Reconciliation cleanup reserve exhausted');
    if (action) clock.assertUnchanged();
    return Math.max(1, Math.min(cap, Math.floor(remaining)));
  };
  const nativeEnv = {
    PATH: process.env.PATH ?? '/usr/bin:/bin',
    LANG: 'C.UTF-8',
    RCT_E2E_TEACHING_CLOCK_DIR: process.env.RCT_E2E_TEACHING_CLOCK_DIR!,
  };
  let nativeFailure: { error: Error; report: () => void } | undefined;
  const docker = (
    args: string[],
    input?: string,
    extra: Record<string, string> = {},
    cap = 5000,
  ): string => {
    const timeout = budget(cap);
    try {
      return execFileSync('docker', args, {
        cwd: root,
        env: { ...nativeEnv, ...extra },
        input,
        timeout,
        maxBuffer: 262144,
        encoding: 'utf8',
        stdio: ['pipe', 'pipe', 'pipe'],
      });
    } catch (error) {
      const failure = new Error('Owned reconciliation command failed');
      if (!cleaning) {
        let code: unknown;
        let status: unknown;
        if (typeof error === 'object' && error !== null) {
          try {
            code = Object.getOwnPropertyDescriptor(error, 'code')?.value;
            status = Object.getOwnPropertyDescriptor(error, 'status')?.value;
          } catch {
            // Uninspectable errors remain UNKNOWN; no native output or accessor is read.
          }
        }
        nativeFailure = {
          error: failure,
          report: () => {
            const matches = (expected: (string | undefined)[]) =>
              args.length === expected.length &&
              args.every((value, index) => value === expected[index]);
            const inspectFormat =
              '{"id":{{json .Id}},"image":{{json .Image}},"labels":{{json .Config.Labels}},"running":{{json .State.Running}},"health":{{json .State.Health.Status}},"startedAt":{{json .State.StartedAt}},"healthLog":{{json .State.Health.Log}},"mounts":{{json .Mounts}},"networks":{{json .NetworkSettings.Networks}}}';
            if (
              matches([
                'exec',
                '-i',
                '-e',
                'PGPASSWORD',
                pins['postgres-schedule']?.id,
                'psql',
                '-X',
                '-qAt',
                '-U',
                'rct_user',
                '-d',
                'schedule_db',
                '-v',
                'ON_ERROR_STOP=1',
              ])
            ) {
              expect.soft(false, 'D9_NATIVE_SCHEDULE_PSQL').toBe(true);
            } else if (
              matches([
                'exec',
                '-i',
                '-e',
                'RCT_MONGO_PASSWORD',
                pins['mongo-attendance']?.id,
                'mongosh',
                '--quiet',
                '--nodb',
                '--file',
                '/dev/stdin',
              ])
            ) {
              expect.soft(false, 'D9_NATIVE_ATTENDANCE_MONGO').toBe(true);
            } else if (
              matches(['inspect', '--format', inspectFormat, pins['schedule-service']?.id])
            ) {
              expect.soft(false, 'D9_NATIVE_SCHEDULE_INSPECT').toBe(true);
            } else if (
              matches(['inspect', '--format', inspectFormat, pins['attendance-service']?.id])
            ) {
              expect.soft(false, 'D9_NATIVE_ATTENDANCE_INSPECT').toBe(true);
            } else if (matches([...compose, 'stop', '--timeout', '10', 'schedule-service'])) {
              expect.soft(false, 'D9_NATIVE_SCHEDULE_STOP').toBe(true);
            } else if (
              matches([
                ...compose,
                'up',
                '-d',
                '--no-build',
                '--no-recreate',
                '--no-deps',
                '--pull',
                'never',
                '--wait',
                '--wait-timeout',
                '40',
                'attendance-service',
              ])
            ) {
              expect.soft(false, 'D9_NATIVE_ATTENDANCE_RESTART').toBe(true);
            } else {
              expect.soft(false, 'D9_NATIVE_OTHER').toBe(true);
            }
            if (code === 'ETIMEDOUT') {
              expect.soft(false, 'D9_NATIVE_TIMEOUT').toBe(true);
            } else if (code === 'ENOBUFS') {
              expect.soft(false, 'D9_NATIVE_OUTPUT_BOUND').toBe(true);
            } else if (status === 1) {
              expect.soft(false, 'D9_NATIVE_EXIT_1').toBe(true);
            } else if (status === 2) {
              expect.soft(false, 'D9_NATIVE_EXIT_2').toBe(true);
            } else if (status === 3) {
              expect.soft(false, 'D9_NATIVE_EXIT_3').toBe(true);
            } else if (status === 125) {
              expect.soft(false, 'D9_NATIVE_EXIT_125').toBe(true);
            } else if (status === 126) {
              expect.soft(false, 'D9_NATIVE_EXIT_126').toBe(true);
            } else if (status === 127) {
              expect.soft(false, 'D9_NATIVE_EXIT_127').toBe(true);
            } else if (status === 137) {
              expect.soft(false, 'D9_NATIVE_EXIT_137').toBe(true);
            } else if (status === 143) {
              expect.soft(false, 'D9_NATIVE_EXIT_143').toBe(true);
            } else if (
              typeof status === 'number' &&
              Number.isSafeInteger(status) &&
              status > 0 &&
              status <= 255
            ) {
              expect.soft(false, 'D9_NATIVE_OTHER_NONZERO').toBe(true);
            } else {
              expect.soft(false, 'D9_NATIVE_UNKNOWN').toBe(true);
            }
          },
        };
      }
      throw failure;
    }
  };
  const compose = local ? ['compose', '-p', local.project, '-f', local.composeFile] : [
    'compose',
    '--env-file',
    envFile,
    '-p',
    'rct-e2e',
    '-f',
    join(root, 'docker-compose.e2e.yml'),
    '-f',
    join(root, 'tests/e2e/infra/docker-compose.clock-fixture.yml'),
  ];
  const inspect = (id: string, cap = 5000): Row =>
    JSON.parse(
      docker(
        [
          'inspect',
          '--format',
          '{"id":{{json .Id}},"image":{{json .Image}},"labels":{{json .Config.Labels}},"running":{{json .State.Running}},"health":{{json .State.Health.Status}},"startedAt":{{json .State.StartedAt}},"healthLog":{{json .State.Health.Log}},"mounts":{{json .Mounts}},"networks":{{json .NetworkSettings.Networks}}}',
          id,
        ],
        undefined,
        {},
        cap,
      ),
    );
  const pins: Record<string, Row> = {};
  const identity = (service: string, cap = 5000) => {
    const pin = pins[service];
    requireValue(pin, 'Pinned owned service is required');
    const current = inspect(pin.id, cap);
    requireValue(
      current.id === pin.id &&
        current.image === pin.image &&
        current.labels['com.docker.compose.project'] === (local?.project ?? 'rct-e2e') &&
        current.labels['com.docker.compose.service'] === service,
      'Owned container identity changed',
    );
    equal(mountRecords(current.mounts), mountRecords(pin.mounts), 'Owned container mounts changed');
    equal(
      canonicalObjectOrder(current.labels),
      canonicalObjectOrder(pin.labels),
      'Owned container labels changed',
    );
    return current;
  };
  const unknownServiceState = (): OwnedServiceState => ({
    running: 'UNKNOWN',
    health: 'UNKNOWN',
    samePinnedId: 'UNKNOWN',
    sameNetworkAddresses: 'UNKNOWN',
  });
  const networkAddresses = (value: unknown): [string, string, string][] | null => {
    if (value === null || typeof value !== 'object' || Array.isArray(value)) return null;
    const names = Object.keys(value).sort();
    if (names.length === 0 || names.length > 8) return null;
    const addresses: [string, string, string][] = [];
    for (const name of names) {
      if (name.length === 0 || name.length > 128) return null;
      const row: unknown = Object.getOwnPropertyDescriptor(value, name)?.value;
      if (row === null || typeof row !== 'object' || Array.isArray(row)) return null;
      const ipv4: unknown = Object.getOwnPropertyDescriptor(row, 'IPAddress')?.value;
      const ipv6: unknown = Object.getOwnPropertyDescriptor(row, 'GlobalIPv6Address')?.value;
      if (
        typeof ipv4 !== 'string' ||
        typeof ipv6 !== 'string' ||
        ipv4.length > 64 ||
        ipv6.length > 64 ||
        (ipv4 !== '' && isIP(ipv4) !== 4) ||
        (ipv6 !== '' && isIP(ipv6) !== 6) ||
        (ipv4 === '' && ipv6 === '')
      )
        return null;
      addresses.push([name, ipv4, ipv6]);
    }
    return addresses;
  };
  const networkMembership = (value: unknown): [string, string][] | null => {
    const addresses = networkAddresses(value);
    if (addresses === null) return null;
    const membership: [string, string][] = [];
    for (const [name] of addresses) {
      const row = Object.getOwnPropertyDescriptor(value, name)?.value;
      const id: unknown = Object.getOwnPropertyDescriptor(row, 'NetworkID')?.value;
      if (typeof id !== 'string' || id.length !== 64 || !/^[0-9a-f]{64}$/.test(id)) return null;
      membership.push([name, id]);
    }
    return membership;
  };
  const captureHttp500OwnedServices = (error: unknown) => {
    if (cleaning || diagnostic.firstHttp500OwnedServices !== null) return;
    let message: unknown;
    try {
      if (error !== null && typeof error === 'object')
        message = Object.getOwnPropertyDescriptor(error, 'message')?.value;
    } catch {
      return;
    }
    if (
      message !== 'Reported synthetic dataset DATASET_ATTENDANCE_REPORT_HTTP: HTTP 500' &&
      message !== 'Reported synthetic dataset DATASET_LESSON_CREATE_HTTP: HTTP 500'
    )
      return;
    const savedNativeFailure = nativeFailure;
    const snapshot = (service: 'attendance-service' | 'schedule-service'): OwnedServiceState => {
      try {
        const current = identity(service);
        const before = networkAddresses(pins[service].networks);
        const after = networkAddresses(current.networks);
        return {
          running: typeof current.running === 'boolean' ? current.running : 'UNKNOWN',
          health:
            current.health === 'healthy'
              ? 'HEALTHY'
              : current.health === 'starting'
                ? 'STARTING'
                : current.health === 'unhealthy'
                  ? 'UNHEALTHY'
                  : 'UNKNOWN',
          samePinnedId: true,
          sameNetworkAddresses:
            before === null || after === null
              ? 'UNKNOWN'
              : JSON.stringify(before) === JSON.stringify(after),
        };
      } catch {
        return unknownServiceState();
      }
    };
    try {
      diagnostic.firstHttp500OwnedServices = {
        ATTENDANCE: snapshot('attendance-service'),
        SCHEDULE: snapshot('schedule-service'),
      };
    } finally {
      // Diagnostic inspect errors must not replace the original native failure record.
      nativeFailure = savedNativeFailure;
    }
  };
  const pg = (sql: string): any => {
    const raw = docker(
      [
        'exec',
        '-i',
        '-e',
        'PGPASSWORD',
        pins['postgres-schedule'].id,
        'psql',
        '-X',
        '-qAt',
        '-U',
        'rct_user',
        '-d',
        'schedule_db',
        '-v',
        'ON_ERROR_STOP=1',
      ],
      `BEGIN READ ONLY; SET LOCAL statement_timeout='2000ms'; ${sql}; ROLLBACK;`,
      { PGPASSWORD: secret.POSTGRES_SCHEDULE_PASSWORD },
    );
    const lines = raw
      .trim()
      .split('\n')
      .filter((line) => !['BEGIN', 'SET', 'ROLLBACK'].includes(line));
    requireValue(lines.length === 1, 'One bounded SQL projection is required');
    return JSON.parse(lines[0]);
  };
  let scope: Row = {};
  const mongo = (): Row => {
    const js = `const k=${JSON.stringify(scope)};
const d=connect('mongodb://root:'+encodeURIComponent(process.env.RCT_MONGO_PASSWORD)+'@localhost:27017/attendance_db?authSource=admin&replicaSet=rs0');
const receipt=d.semester_archive_effect_receipts.find({_id:(k.event||'')+':ATTENDANCE'}).maxTimeMS(2000).limit(2).toArray();
receipt.forEach(r=>r.semester_id=Number(r.semester_id));
const claims=d.event_consumer_processed.countDocuments({consumer_id:'attendance',event_id:k.event||''},{maxTimeMS:2000});
const acks=d.attendance_outbox.find({event_type:'semester.archive.effect.ack',payload:{$regex:'"source_event_id"\\\\s*:\\\\s*"'+(k.event||'')+'"'}}).maxTimeMS(2000).limit(4).toArray();
const docs=d.attendances.find({semester_id:k.semester,lesson_id:k.lesson}).sort({user_id:1}).maxTimeMS(2000).limit(501).toArray();
const other=d.attendances.find({semester_id:k.semester,lesson_id:{$ne:k.lesson}}).sort({_id:1}).maxTimeMS(2000).limit(501).toArray();
[...docs,...other].forEach(r=>['lesson_id','semester_id','user_id','group_id','subject_id'].forEach(f=>{if(r[f]!=null)r[f]=Number(r[f]);}));
print(JSON.stringify({receipt,claims,acks,docs,other}));`;
    return JSON.parse(
      docker(
        [
          'exec',
          '-i',
          '-e',
          'RCT_MONGO_PASSWORD',
          pins['mongo-attendance'].id,
          'mongosh',
          '--quiet',
          '--nodb',
          '--file',
          '/dev/stdin',
        ],
        js,
        { RCT_MONGO_PASSWORD: secret.MONGO_ROOT_PASSWORD },
      ),
    );
  };
  const broker = async (path: string, data?: unknown): Promise<any> => {
    const current = pins.rabbitmq;
    const ip =
      local ? current.networks[local.networkName]?.IPAddress : current.networks.rct_e2e_private_net?.IPAddress ??
      current.networks['rct-e2e_private_net']?.IPAddress;
    requireValue(
      typeof ip === 'string' && /^(?:10\.|172\.(?:1[6-9]|2\d|3[01])\.|192\.168\.)\d/.test(ip),
      'Owned private broker address required',
    );
    let response: globalThis.Response;
    try {
      response = await fetch(`http://${ip}:15672/api/${path}`, {
        method: data === undefined ? 'GET' : 'POST',
        redirect: 'error',
        signal: AbortSignal.timeout(budget(4000)),
        headers: {
          Authorization: `Basic ${Buffer.from(`${secret.RABBITMQ_USER}:${secret.RABBITMQ_PASSWORD}`).toString('base64')}`,
          'Content-Type': 'application/json',
        },
        body: data === undefined ? undefined : JSON.stringify(data),
      });
    } catch (error) {
      if (cleaning) retainLifecycleCleanupFailure(info.annotations, cleanupStage, error);
      throw new Error('Owned broker control unavailable');
    }
    if (cleaning && !(response.ok && response.body))
      retainLifecycleCleanupFailure(info.annotations, cleanupStage, undefined, response.status);
    requireValue(response.ok && response.body, 'Owned broker control rejected');
    const reader = response.body.getReader();
    const chunks: Uint8Array[] = [];
    let length = 0;
    try {
      for (;;) {
        const part = await reader.read();
        if (part.done) break;
        length += part.value.length;
        requireValue(length <= 262144, 'Broker projection exceeds bound');
        chunks.push(part.value);
      }
    } finally {
      await reader.cancel().catch(() => {});
    }
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  };
  const queues = async () => ({
    main: await broker('queues/%2F/attendance-service.events'),
    dead: await broker('queues/%2F/attendance-service.events.dlq'),
    retry: await broker('queues/%2F/attendance-service.events.retry'),
  });
  const stop = (service: string) => {
    budget(15000, { stop: service }, true);
    identity(service);
    stopped.add(service);
    docker([...compose, 'stop', '--timeout', '10', service], undefined, {}, 15000);
    requireValue(identity(service).running === false, 'Owned service must stop');
  };
  type RecoveryHealthRead = {
    channelState: 'READY' | 'IDLE' | 'CONNECTING' | 'TRANSIENT_FAILURE' | 'SHUTDOWN' | 'UNKNOWN';
    fresh: boolean | null;
  };
  const recoveryHealthRead = (
    rows: unknown,
    since: number,
    observedAt: number,
  ): RecoveryHealthRead => {
    const unknown = (): RecoveryHealthRead => ({ channelState: 'UNKNOWN', fresh: null });
    try {
      if (!Array.isArray(rows) || rows.length === 0 || rows.length > 5) return unknown();
      const row = rows.at(-1);
      if (
        row === null ||
        typeof row !== 'object' ||
        Array.isArray(row) ||
        Object.getPrototypeOf(row) !== Object.prototype ||
        Object.keys(row).sort().join(',') !== 'End,ExitCode,Output,Start' ||
        row.ExitCode !== 0 ||
        typeof row.Start !== 'string' ||
        typeof row.End !== 'string' ||
        typeof row.Output !== 'string' ||
        Buffer.byteLength(row.Output, 'utf8') > 4096 ||
        !Number.isFinite(since) ||
        !Number.isFinite(observedAt)
      )
        return unknown();
      const startWire = normalizeDockerHealthTimestamp(row.Start),
        endWire = normalizeDockerHealthTimestamp(row.End);
      if (startWire === null || endWire === null) return unknown();
      const utc = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/;
      if (
        !utc.test(startWire) ||
        !utc.test(endWire) ||
        startWire.trim() !== startWire ||
        endWire.trim() !== endWire
      )
        return unknown();
      const start = Date.parse(startWire),
        end = Date.parse(endWire);
      if (!Number.isFinite(start) || !Number.isFinite(end) || start > end || end > observedAt)
        return unknown();
      if (
        new Date(start).toISOString().slice(0, 19) !== startWire.slice(0, 19) ||
        new Date(end).toISOString().slice(0, 19) !== endWire.slice(0, 19) ||
        startWire.replace(
          /(?:\.(\d{1,9}))?Z$/,
          (_, fraction = '') => `.${fraction.padEnd(9, '0')}Z`,
        ) >
          endWire.replace(
            /(?:\.(\d{1,9}))?Z$/,
            (_, fraction = '') => `.${fraction.padEnd(9, '0')}Z`,
          )
      )
        return unknown();
      const body = JSON.parse(row.Output);
      if (
        body?.status !== 'UP' ||
        body?.components?.grpcChannel?.status !== 'UP' ||
        body?.components?.grpcChannel?.details === null ||
        typeof body?.components?.grpcChannel?.details !== 'object' ||
        Array.isArray(body.components.grpcChannel.details) ||
        !Object.hasOwn(body.components.grpcChannel.details, 'schedule-service')
      )
        return unknown();
      const state: unknown = body.components.grpcChannel.details['schedule-service'];
      if (
        state !== 'READY' &&
        state !== 'IDLE' &&
        state !== 'CONNECTING' &&
        state !== 'TRANSIENT_FAILURE' &&
        state !== 'SHUTDOWN'
      )
        return unknown();
      return { channelState: state, fresh: start >= since };
    } catch {
      // Only the finite connectivity state leaves this private existing health response.
      return unknown();
    }
  };
  const restart = async (service: string, scheduleReturnedAt?: number) => {
    if (!stopped.has(service)) return;
    const restartEnd = performance.now() + budget(45000);
    const remaining = () => {
      const value = Math.floor(restartEnd - performance.now());
      requireValue(value > 0, 'Owned restart deadline exhausted');
      return value;
    };
    const recordRestartFailure = (current: Row) => {
      if (
        !(current.running && current.health === 'healthy') &&
        diagnostic.firstRestartFailure === null &&
        (service === 'attendance-service' || service === 'schedule-service')
      ) {
        diagnostic.firstRestartFailure = {
          phase: cleaning ? 'CLEANUP' : 'MAIN',
          service: service === 'attendance-service' ? 'ATTENDANCE' : 'SCHEDULE',
          cause:
            current.running === false
              ? 'NOT_RUNNING'
              : current.running === true && current.health === 'starting'
                ? 'HEALTH_STARTING'
                : current.running === true && current.health === 'unhealthy'
                  ? 'HEALTH_UNHEALTHY'
                  : 'HEALTH_UNKNOWN',
        };
      }
    };
    const before = identity(service, Math.min(5000, remaining()));
    const passiveReady =
      service === 'attendance-service' && !cleaning && scheduleReturnedAt !== undefined;
    const recovered = (current: Row) => {
      if (!passiveReady) return current.running && current.health === 'healthy';
      requireValue(
        before.running === true &&
          typeof before.startedAt === 'string' &&
          before.startedAt.length > 0 &&
          current.startedAt === before.startedAt,
        'Same owned Attendance process must recover without restart',
      );
      const initialAddresses = networkAddresses(before.networks);
      const currentAddresses = networkAddresses(current.networks);
      const pinnedAddresses = networkAddresses(pins[service].networks);
      const initialMembership = networkMembership(before.networks);
      const currentMembership = networkMembership(current.networks);
      const pinnedMembership = networkMembership(pins[service].networks);
      requireValue(
        initialAddresses !== null &&
          currentAddresses !== null &&
          pinnedAddresses !== null &&
          initialMembership !== null &&
          currentMembership !== null &&
          pinnedMembership !== null &&
          JSON.stringify(initialMembership) === JSON.stringify(pinnedMembership) &&
          JSON.stringify(initialMembership) === JSON.stringify(currentMembership) &&
          JSON.stringify(initialAddresses) === JSON.stringify(currentAddresses),
        'Owned Attendance network addresses changed',
      );
      const health = observeHealth(diagnostic, 'ATTENDANCE', current.healthLog, scheduleReturnedAt!, Date.now(), recoveryHealthRead, run);
      diagnostic.recoveryHealthLastRead = health;
      // Passive Docker health snapshots do not initiate an Academic business RPC.
      const academic = identity('academic-service', Math.min(5000, remaining()));
      const academicPin = pins['academic-service'];
      requireValue(
        academicPin.running === true &&
          typeof academicPin.startedAt === 'string' &&
          academicPin.startedAt.length > 0 &&
          academic.startedAt === academicPin.startedAt,
        'Same pinned Academic process must recover without restart',
      );
      const academicAddresses = networkAddresses(academic.networks);
      const academicPinnedAddresses = networkAddresses(academicPin.networks);
      const academicMembership = networkMembership(academic.networks);
      const academicPinnedMembership = networkMembership(academicPin.networks);
      requireValue(
        academicAddresses !== null &&
          academicPinnedAddresses !== null &&
          academicMembership !== null &&
          academicPinnedMembership !== null &&
          JSON.stringify(academicAddresses) === JSON.stringify(academicPinnedAddresses) &&
          JSON.stringify(academicMembership) === JSON.stringify(academicPinnedMembership),
        'Pinned Academic network addresses changed',
      );
      const academicHealth = observeHealth(
        diagnostic, 'ACADEMIC', academic.healthLog,
        scheduleReturnedAt!,
        Date.now(), recoveryHealthRead, run,
      );
      diagnostic.academicRecoveryHealthLastRead = {
        channelState: academicHealth.channelState,
        fresh: academicHealth.fresh === true,
      };
      return (
        current.running &&
        current.health === 'healthy' &&
        health.channelState === 'READY' &&
        health.fresh === true &&
        academic.running === true &&
        academic.health === 'healthy' &&
        (academicHealth.channelState === 'READY' || academicHealth.channelState === 'IDLE') &&
        academicHealth.fresh === true
      );
    };
    try {
      docker(
        [
          ...compose,
          'up',
          '-d',
          '--no-build',
          '--no-recreate',
          '--no-deps',
          '--pull',
          'never',
          '--wait',
          '--wait-timeout',
          '40',
          service,
        ],
        undefined,
        {},
        remaining(),
      );
    } catch (error) {
      // A failed Compose wait must not bypass the existing finite health witness.
      // Keep both the original command failure and its native classifier intact.
      const originalNativeFailure = nativeFailure;
      try {
        recordRestartFailure(identity(service, Math.min(5000, remaining())));
      } catch {
        // No readable snapshot within the original deadline: leave the witness unknown.
      } finally {
        nativeFailure = originalNativeFailure;
      }
      throw error;
    }
    let current = identity(service, Math.min(5000, remaining()));
    while (
      current.running &&
      (current.health === 'starting' || (passiveReady && !recovered(current)))
    ) {
      const left = Math.floor(restartEnd - performance.now());
      if (left <= 0) break;
      await new Promise((resolve) => setTimeout(resolve, Math.min(250, left)));
      const afterDelay = Math.floor(restartEnd - performance.now());
      if (afterDelay <= 0) break;
      current = identity(service, Math.min(5000, afterDelay));
    }
    recordRestartFailure(current);
    requireValue(recovered(current), 'Same owned service must recover');
    requireValue(performance.now() <= restartEnd, 'Owned restart deadline exhausted');
    stopped.delete(service);
  };
  const request = await playwright.request.newContext({
    baseURL: 'https://localhost:8443',
    ignoreHTTPSErrors: false,
    timeout: 2000,
  });
  let admin: Actor | undefined, previous: Row | undefined, expected: Row | undefined;
  let envelope: Buffer | undefined, properties: Row | undefined, otherBefore: unknown;
  let peeked = false;
  let closeLookupAttempted = false;
  let closeCommand:
    | {
        actor: Actor;
        key: string;
        body: Row;
        bodySnapshot: string;
        lessonsPath: string;
      }
    | undefined;
  const archiveKey = randomUUID(),
    restoreKey = randomUUID();
  const archiveProgress = semesterProgress(),
    restoreProgress = semesterProgress();
  let originalFailure: { error: unknown } | undefined;
  const hash = (value: unknown): string => {
    const sort = (v: any): any =>
      Array.isArray(v)
        ? v.map(sort)
        : v !== null && typeof v === 'object'
          ? Object.fromEntries(
              Object.keys(v)
                .sort()
                .map((key) => [key, sort(v[key])]),
            )
          : v;
    return createHash('sha256')
      .update(JSON.stringify(sort(value)))
      .digest('hex');
  };
  const effectRows = (): Row[] =>
    pg(`SELECT coalesce(json_agg(t),'[]'::json) FROM (
SELECT e.event_id::text,e.target,e.event_type,e.semester_id,e.scope_id,encode(e.payload_hash,'hex') AS hash,
e.state,e.receipt_event_id::text,e.event_payload,
(SELECT count(*) FROM schedule_outbox o WHERE o.payload->>'event_id'=e.event_id::text) AS producers,
(SELECT json_build_object('status',o.status,'body',encode(convert_to(o.payload::text,'UTF8'),'base64'))
FROM schedule_outbox o WHERE o.payload->>'event_id'=e.event_id::text LIMIT 1) AS producer
FROM schedule_semester_archive_effect_ledger e WHERE e.semester_id=${scope.semester}
AND e.event_type='lesson.closed' AND e.event_payload#>>'{payload,lesson_id}'='${scope.lesson}' LIMIT 2) t`);
  const effect = () => {
    const rows = effectRows();
    requireValue(rows.length === 1, 'One exact owned close is required');
    const row = rows[0];
    requireValue(
      row.target === 'ATTENDANCE' &&
        row.event_type === 'lesson.closed' &&
        Number(row.semester_id) === scope.semester &&
        Number(row.scope_id) === scope.semester &&
        /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(row.event_id) &&
        row.producers === 1 &&
        row.producer?.status === 'sent' &&
        row.event_payload.source === 'schedule-service' &&
        row.event_payload.event_version === 1 &&
        row.event_payload.event_id === row.event_id &&
        typeof row.event_payload.trace_id === 'string' &&
        row.event_payload.trace_id.trim().length > 0 &&
        row.event_payload.payload.lesson_id === scope.lesson &&
        row.event_payload.payload.group_id === scope.group &&
        row.event_payload.payload.semester_id === scope.semester &&
        hash(row.event_payload.payload) === row.hash,
      'Producer ledger payload and scope must match',
    );
    const bytes = Buffer.from(row.producer.body, 'base64');
    requireValue(bytes.length > 0 && bytes.length <= 65536, 'Original envelope must be bounded');
    equal(JSON.parse(bytes.toString('utf8')), row.event_payload, 'Original envelope changed');
    if (expected) {
      const keys = [
        'event_id',
        'target',
        'event_type',
        'semester_id',
        'scope_id',
        'hash',
        'event_payload',
      ];
      equal(fields(row, keys), fields(expected, keys), 'Exact original effect changed');
      requireValue(bytes.equals(envelope!), 'Original producer bytes changed');
    } else {
      expected = row;
      effectCaptured = true;
      envelope = bytes;
      scope.event = row.event_id;
    }
    return row;
  };
  const consumer = () => {
    const value = mongo();
    requireValue(
      value.docs.length <= 500 && value.other.length <= 500 && value.acks.length <= 3,
      'Consumer projection must be bounded',
    );
    if (otherBefore === undefined) otherBefore = value.other;
    else equal(value.other, otherBefore, 'Unrelated attendance changed');
    return value;
  };
  const captureDelayedRetry = async () => {
    await expect
      .poll(
        async () => {
          const q = await queues();
          requireValue(
            q.retry.arguments?.['x-message-ttl'] === 30000 &&
              q.retry.arguments?.['x-dead-letter-exchange'] === '' &&
              q.retry.arguments?.['x-dead-letter-routing-key'] === 'attendance-service.events' &&
              q.retry.consumers === 0,
            'Owned delayed retry must use the configured thirty-second route without consumers',
          );
          return (
            q.retry.messages_ready === 1 &&
            q.retry.messages_unacknowledged === 0 &&
            q.main.messages_ready === 0 &&
            q.main.messages_unacknowledged === 0 &&
            q.dead.messages_ready === 0 &&
            q.dead.messages_unacknowledged === 0
          );
        },
        {
          // Includes the existing bounded application startup plus the first three dependency
          // attempts and confirmed retry publication. Global cleanup reserves still apply.
          timeout: budget(60000),
          message: 'First transient delivery schedules one exact delayed retry',
        },
      )
      .toBe(true);
    // RabbitMQ 3.13 TTL semantics preserve the original expiry on requeue. This same management
    // requeue-only peek used for the terminal DLQ retains the copy without removing or republishing it.
    const delayed = await broker('queues/%2F/attendance-service.events.retry/get', {
      count: 1,
      ackmode: 'ack_requeue_true',
      encoding: 'base64',
    });
    requireValue(
      delayed.length === 1 &&
        delayed[0].payload_encoding === 'base64' &&
        delayed[0].payload_bytes <= 65536 &&
        Buffer.from(delayed[0].payload, 'base64').equals(envelope!),
      'Delayed retry must retain the exact original producer bytes',
    );
    const prop = delayed[0].properties;
    requireValue(
      prop.content_type === 'application/json' &&
        prop.content_encoding === 'UTF-8' &&
        prop.delivery_mode === 2 &&
        prop.message_id === scope.event &&
        prop.headers?.event_type === 'lesson.closed' &&
        prop.headers.rct_redrive_count === 1 &&
        prop.headers.rct_failure_code === 'transient_dependency',
      'One persistent retry must preserve message identity and the finite redrive budget',
    );
    requireValue(effect().state === 'PENDING', 'Delayed original effect must remain pending');
    const value = consumer();
    requireValue(
      value.claims === 0 && value.receipt.length === 0 && value.acks.length === 0,
      'Delayed preparation failure must leave no business claim receipt or ACK',
    );
  };
  const stranded = async () => {
    requireValue(effect().state === 'PENDING', 'Original effect must remain pending');
    const value = consumer();
    requireValue(
      value.claims === 0 && value.receipt.length === 0 && value.acks.length === 0,
      'Preparation failure must leave no claim receipt or ACK',
    );
    const q = await queues();
    requireValue(
      q.dead.messages_ready === 1 &&
        q.dead.messages_unacknowledged === 0 &&
        q.main.messages_ready === 0 &&
        q.main.messages_unacknowledged === 0 &&
        q.retry.messages_ready === 0 &&
        q.retry.messages_unacknowledged === 0,
      'One exact quiescent DLQ delivery required',
    );
    if (!peeked) {
      const owner = identity('rabbitmq');
      requireValue(
        owner.running && owner.health === 'healthy',
        'Owned DLQ broker must remain healthy',
      );
      const dead = await broker('queues/%2F/attendance-service.events.dlq/get', {
        count: 1,
        ackmode: 'ack_requeue_true',
        encoding: 'base64',
      });
      peeked = true;
      requireValue(
        dead.length === 1 &&
          dead[0].payload_encoding === 'base64' &&
          dead[0].payload_bytes <= 65536 &&
          Buffer.from(dead[0].payload, 'base64').equals(envelope!),
        'DLQ must retain exact original bytes',
      );
      const prop = dead[0].properties;
      requireValue(
        prop.content_type === 'application/json' &&
          prop.content_encoding === 'UTF-8' &&
          prop.delivery_mode === 2 &&
          prop.message_id === scope.event &&
          prop.headers?.event_type === 'lesson.closed' &&
          prop.headers.rct_redrive_count === 1 &&
          prop.headers['x-death']?.some(
            (v: Row) =>
              v.queue === 'attendance-service.events' && v.reason === 'rejected' && v.count === 1,
          ),
        'Exact original delivery must have been rejected to its DLQ',
      );
      requireValue(
        prop.headers['x-death']?.some(
          (v: Row) =>
            v.queue === 'attendance-service.events.retry' &&
            v.reason === 'expired' &&
            v.count === 1,
        ),
        'One automatic delayed cycle must expire before terminal rejection',
      );
      properties = {
        content_type: prop.content_type,
        content_encoding: prop.content_encoding,
        delivery_mode: prop.delivery_mode,
        message_id: prop.message_id,
        headers: { event_type: prop.headers.event_type, rct_redrive_count: 1 },
      };
      diagnostic.exactDlqEnvelope = true;
    }
  };
  const publish = async (duplicate = false) => {
    budget(5000, {}, true);
    for (const service of ['attendance-service', 'schedule-service', 'rabbitmq']) {
      const current = identity(service);
      requireValue(
        current.running && current.health === 'healthy',
        'Replay dependencies must remain healthy',
      );
    }
    requireValue(
      effect().state === (duplicate ? 'APPLIED' : 'PENDING'),
      'Exact effect state changed before publish',
    );
    if (!duplicate) {
      requireValue(!diagnostic.recoveryPublishAttempted, 'Only one recovery publish is allowed');
      await stranded();
      diagnostic.recoveryPublishAttempted = true;
    } else {
      requireValue(
        applied && archived && !diagnostic.duplicateAttempted,
        'Only one deliberate terminal duplicate is allowed',
      );
      diagnostic.duplicateAttempted = true;
    }
    if (!duplicate && !cleaning) replaySince = new Date().toISOString();
    const sent = await broker('exchanges/%2F/amq.default/publish', {
      properties,
      routing_key: 'attendance-service.events',
      payload: envelope!.toString('base64'),
      payload_encoding: 'base64',
    });
    requireValue(sent.routed === true, 'Exact targeted copy must route to Attendance');
  };
  let readinessRequest: { id: string; since: string; until: string } | undefined;
  const captureReadinessFailure = () => {
    const unknown = (reason: ReadinessLog['unknownReason']): ReadinessLog => ({
      state: 'UNKNOWN',
      unknownReason: reason,
      matchedCount: 0,
      last: null,
    });
    const previousNativeFailure = nativeFailure;
    try {
      if (!readinessRequest) {
        diagnostic.readinessFailureLog = unknown('NO_REQUEST');
        return;
      }
      let count = 0;
      let last: ReadinessWarn | null = null;
      for (const service of ['api-gateway', 'academic-service']) {
        const current = identity(service, 2000);
        if (!current.running) {
          diagnostic.readinessFailureLog = unknown('LOG_READ');
          return;
        }
        const payload = docker(
          [
            'logs',
            '--since',
            readinessRequest.since,
            '--until',
            readinessRequest.until,
            '--tail',
            '2000',
            pins[service].id,
          ],
          undefined,
          {},
          2000,
        );
        const after = identity(service, 2000);
        if (!after.running || after.startedAt !== current.startedAt) {
          diagnostic.readinessFailureLog = unknown('LOG_READ');
          return;
        }
        if (Buffer.byteLength(payload, 'utf8') >= 262144) {
          diagnostic.readinessFailureLog = unknown('LOG_BYTE_LIMIT');
          return;
        }
        const lines = payload.split(/\r?\n/);
        if (lines.at(-1) === '') lines.pop();
        if (lines.length >= 2000) {
          diagnostic.readinessFailureLog = unknown('LOG_TAIL_LIMIT');
          return;
        }
        for (const line of lines) {
          if (Buffer.byteLength(line, 'utf8') > 131072) {
            diagnostic.readinessFailureLog = unknown('LOG_BYTE_LIMIT');
            return;
          }
          let record: unknown;
          try {
            record = JSON.parse(line);
          } catch {
            diagnostic.readinessFailureLog = unknown('LOG_FORMAT');
            return;
          }
          if (typeof record !== 'object' || record === null || Array.isArray(record)) {
            diagnostic.readinessFailureLog = unknown('LOG_RECORD_SHAPE');
            return;
          }
          const row = record as Record<string, unknown>;
          const sources = Object.keys(readinessSources) as (keyof typeof readinessSources)[];
          const owned = sources.filter(
            (source) =>
              readinessSources[source].logger === row.logger &&
              (service === 'api-gateway') === source.startsWith('GATEWAY_'),
          );
          if (owned.length !== 1 || row.level !== 'WARN') continue;
          if (typeof row.msg !== 'string') {
            diagnostic.readinessFailureLog = unknown('TARGET_TEMPLATE');
            return;
          }
          if (!row.msg.startsWith('semester.preview failure ')) continue;
          if (
            typeof row.requestId !== 'string' ||
            row.requestId.length !== 36 ||
            !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(row.requestId)
          ) {
            diagnostic.readinessFailureLog = unknown('TARGET_CORRELATION');
            return;
          }
          if (row.requestId !== readinessRequest.id) continue;
          const message = withoutGatewayAdmissionDiagnostic(row.msg, owned[0]);
          const match = message === null ? null :
            /^semester\.preview failure source=([A-Z_]+) stage=([A-Z_]+) status=([A-Z_0-9]+) cause=([A-Z_]+)(?: channelBefore=([A-Z_]+) channelAtFailure=([A-Z_]+) connectKind=([A-Z_]+) endpointRelation=(UNKNOWN))?$/.exec(
              message,
            );
          if (
            !match ||
            match[0] !== message ||
            match[1] !== owned[0] ||
            !(readinessSources[owned[0]].stages as readonly string[]).includes(match[2]) ||
            !(readinessCauses as readonly string[]).includes(match[4])
          ) {
            diagnostic.readinessFailureLog = unknown('TARGET_TEMPLATE');
            return;
          }
          const stage = match[2],
            status = match[3];
          const validStatus =
            stage === 'AUTH_HTTP'
              ? /^HTTP_[1-5][0-9]{2}$/.test(status) && status !== 'HTTP_200'
              : stage === 'AUTH_RESPONSE'
                ? status === 'HTTP_200'
                : stage === 'CURRENT_CONTEXT'
                  ? (replayStatuses as readonly string[]).includes(status) && status !== 'NONE'
                  : stage === 'SCHEDULE_PREVIEW' || stage === 'ATTENDANCE_PREVIEW'
                    ? (replayStatuses as readonly string[]).includes(status)
                    : status === 'NONE';
          if (!validStatus) {
            diagnostic.readinessFailureLog = unknown('TARGET_TEMPLATE');
            return;
          }
          let scheduleTransport: ReadinessWarn['scheduleTransport'] = null;
          if (owned[0] === 'SCHEDULE_CLIENT') {
            const invalidArgument = status === 'NONE' && match[4] === 'INVALID_ARGUMENT';
            if (
              invalidArgument
                ? match[5] !== undefined
                : !(readinessChannelStates as readonly string[]).includes(match[5]) ||
                  !(readinessChannelStates as readonly string[]).includes(match[6]) ||
                  !(readinessConnectKinds as readonly string[]).includes(match[7]) ||
                  match[8] !== 'UNKNOWN'
            ) {
              diagnostic.readinessFailureLog = unknown('TARGET_TEMPLATE');
              return;
            }
            if (!invalidArgument) {
              scheduleTransport = {
                channelBefore: match[5] as (typeof readinessChannelStates)[number],
                channelAtFailure: match[6] as (typeof readinessChannelStates)[number],
                connectKind: match[7] as (typeof readinessConnectKinds)[number],
                endpointRelation: 'UNKNOWN',
              };
            }
          } else if (match[5] !== undefined) {
            diagnostic.readinessFailureLog = unknown('TARGET_TEMPLATE');
            return;
          }
          if (++count > 200) {
            diagnostic.readinessFailureLog = unknown('MATCH_COUNT_LIMIT');
            return;
          }
          last = {
            source: owned[0],
            stage,
            status,
            cause: match[4] as ReadinessWarn['cause'],
            scheduleTransport,
          };
        }
      }
      diagnostic.readinessFailureLog =
        count === 1
          ? { state: 'REPORTED', unknownReason: 'NONE', matchedCount: 1, last }
          : count > 1
            ? { state: 'UNKNOWN', unknownReason: 'AMBIGUOUS', matchedCount: count, last: null }
            : unknown('NO_MATCHING_WARN');
    } catch {
      diagnostic.readinessFailureLog = unknown('LOG_READ');
    } finally {
      // Diagnostic read failures cannot replace the original readiness exception/native carrier.
      nativeFailure = previousNativeFailure;
    }
  };
  let replaySince: string | undefined;
  let replayAckEventId: string | undefined;
  const countClass = (value: unknown): CountClass =>
    typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0
      ? 'INVALID'
      : value === 0
        ? 'ZERO'
        : value === 1
          ? 'ONE'
          : 'MANY';
  const captureReplayFailure = () => {
    const unknown = <T>(reason: ReplayLog<T>['unknownReason']): ReplayLog<T> => ({
      state: 'UNKNOWN',
      unknownReason: reason,
      matchedCount: 0,
      last: null,
    });
    const read = <T>(
      service: 'attendance-service' | 'schedule-service',
      eventId: string | undefined,
      decodeWarn: (message: string) => T | null,
    ): ReplayLog<T> => {
      if (!eventId || !replaySince)
        return unknown(service === 'attendance-service' ? 'NO_SOURCE_EVENT' : 'NO_ACK_EVENT');
      const previousNativeFailure = nativeFailure;
      try {
        // Same owned container, bounded existing command primitive; no raw log is retained.
        const current = identity(service);
        if (!current.running) return unknown('LOG_READ');
        const payload = docker(
          ['logs', '--since', replaySince, '--tail', '2000', pins[service].id],
          undefined,
          {},
          2000,
        );
        if (Buffer.byteLength(payload, 'utf8') >= 262144) return unknown('LOG_BYTE_LIMIT');
        const lines = payload.split(/\r?\n/);
        if (lines.at(-1) === '') lines.pop();
        if (lines.length >= 2000) return unknown('LOG_TAIL_LIMIT');
        let count = 0;
        let last: T | null = null;
        for (const line of lines) {
          if (Buffer.byteLength(line, 'utf8') > 131072) return unknown('LOG_BYTE_LIMIT');
          let record: unknown;
          try {
            record = JSON.parse(line);
          } catch {
            return unknown('LOG_FORMAT');
          }
          if (typeof record !== 'object' || record === null || Array.isArray(record))
            return unknown('LOG_RECORD_SHAPE');
          const row = record as Record<string, unknown>;
          const logger =
            service === 'attendance-service'
              ? 'ru.rutcampustrack.attendance.event.EventConsumer'
              : 'ru.rutcampustrack.schedule.event.EventConsumer';
          if (row.logger !== logger || row.level !== 'WARN') continue;
          if (typeof row.msg !== 'string') return unknown('TARGET_TEMPLATE');
          const prefix =
            service === 'attendance-service'
              ? 'lesson.closed failure '
              : 'archive.effect.ack failure ';
          if (!row.msg.startsWith(prefix)) continue;
          if (typeof row.eventId !== 'string') return unknown('TARGET_CORRELATION');
          if (row.eventId !== eventId) continue;
          const decoded = decodeWarn(row.msg);
          if (!decoded) return unknown('TARGET_TEMPLATE');
          if (++count > 200) return unknown('MATCH_COUNT_LIMIT');
          last = decoded;
        }
        return count
          ? { state: 'REPORTED', unknownReason: 'NONE', matchedCount: count, last }
          : unknown('NO_MATCHING_WARN');
      } catch {
        return unknown('LOG_READ');
      } finally {
        nativeFailure = previousNativeFailure;
      }
    };
    const attendance = read<ReplayWarn>('attendance-service', scope.event, (message) => {
      const match =
        /^lesson\.closed failure stage=([A-Z_]+) category=([A-Z_]+) rpc=([A-Z_]+) status=([A-Z_]+) cause=([A-Z_]+)$/.exec(
          message,
        );
      if (
        !match ||
        !(replayStages as readonly string[]).includes(match[1]) ||
        !(replayCategories as readonly string[]).includes(match[2]) ||
        !(replayMethods as readonly string[]).includes(match[3]) ||
        !(replayStatuses as readonly string[]).includes(match[4]) ||
        !(replayCauses as readonly string[]).includes(match[5])
      )
        return null;
      return {
        stage: match[1],
        category: match[2],
        rpc: match[3],
        status: match[4],
        cause: match[5],
      } as ReplayWarn;
    });
    const schedule = read<{ category: (typeof ackCategories)[number] }>(
      'schedule-service',
      replayAckEventId,
      (message) => {
        const match = /^archive\.effect\.ack failure category=([A-Z_]+)$/.exec(message);
        return match && (ackCategories as readonly string[]).includes(match[1])
          ? { category: match[1] as (typeof ackCategories)[number] }
          : null;
      },
    );
    diagnostic.replayFailureLog = { attendance, schedule };
  };
  const awaitApplied = async () => {
    await expect
      .poll(
        () => {
          const row = effect(),
            value = consumer(),
            r = value.receipt[0];
          const ack = value.acks[0] && JSON.parse(value.acks[0].payload);
          if (!cleaning) {
            const checks: ReplayChecks = {
              ledgerApplied: row.state === 'APPLIED',
              oneClaim: value.claims === 1,
              oneReceipt: value.receipt.length === 1,
              receiptApplied: r?.result === 'APPLIED',
              receiptSourceEventMatches: r?.source_event_id === scope.event,
              receiptTargetMatches: r?.target === 'ATTENDANCE',
              receiptSemesterMatches: Number(r?.semester_id) === scope.semester,
              receiptTypeMatches: r?.source_event_type === 'lesson.closed',
              receiptHashMatches: r?.payload_hash === expected!.hash,
              ackEventMatchesLedger: ack?.event_id === row.receipt_event_id,
              ackSourceMatches: ack?.source === 'attendance-service',
              ackTypeMatches: ack?.event_type === 'semester.archive.effect.ack',
              ackVersionMatches: ack?.event_version === 1,
              ackSourceEventMatches: ack?.payload?.source_event_id === scope.event,
              ackHashMatches: ack?.payload?.payload_hash === expected!.hash,
              ackTargetMatches: ack?.payload?.target === 'ATTENDANCE',
              ackApplied: ack?.payload?.result === 'APPLIED',
              ackSemesterMatches: Number(ack?.payload?.semester_id) === scope.semester,
              ackSourceTypeMatches: ack?.payload?.source_event_type === 'lesson.closed',
            };
            diagnostic.replayLastRead = {
              ledgerState: ['PENDING', 'APPLIED', 'ERROR'].includes(row.state)
                ? row.state
                : 'OTHER',
              ledgerReceiptIdPresent:
                typeof row.receipt_event_id === 'string' && row.receipt_event_id.length > 0,
              claimCount: countClass(value.claims),
              receiptCount: countClass(value.receipt.length),
              ackCount: countClass(value.acks.length),
              receiptResult:
                r?.result === 'APPLIED' || r?.result === 'ERROR' ? r.result : 'OTHER_OR_ABSENT',
              ackOutboxStatus:
                value.acks[0]?.status === 'pending'
                  ? 'PENDING'
                  : value.acks[0]?.status === 'sent'
                    ? 'SENT'
                    : 'OTHER_OR_ABSENT',
              checks,
              firstFailed:
                (Object.keys(checks) as (keyof ReplayChecks)[]).find((key) => !checks[key]) ??
                'NONE',
            };
            replayAckEventId =
              value.acks.length === 1 &&
              typeof ack?.event_id === 'string' &&
              /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(ack.event_id)
                ? ack.event_id
                : undefined;
          }
          return (
            row.state === 'APPLIED' &&
            value.claims === 1 &&
            value.receipt.length === 1 &&
            r.result === 'APPLIED' &&
            r.source_event_id === scope.event &&
            r.target === 'ATTENDANCE' &&
            Number(r.semester_id) === scope.semester &&
            r.source_event_type === 'lesson.closed' &&
            r.payload_hash === expected!.hash &&
            ack?.event_id === row.receipt_event_id &&
            ack?.source === 'attendance-service' &&
            ack.event_type === 'semester.archive.effect.ack' &&
            ack.event_version === 1 &&
            ack.payload.source_event_id === scope.event &&
            ack.payload.payload_hash === expected!.hash &&
            ack.payload.target === 'ATTENDANCE' &&
            ack.payload.result === 'APPLIED' &&
            Number(ack.payload.semester_id) === scope.semester &&
            ack.payload.source_event_type === 'lesson.closed'
          );
        },
        { timeout: budget(20000), message: 'Exact original replay commits receipt ACK and ledger' },
      )
      .toBe(true);
    applied = true;
    diagnostic.appliedAfterReplay = diagnostic.recoveryPublishAttempted;
  };
  const recoverOwnClose = async () => {
    requireValue(
      closeCommand && eventAccepted,
      'Exact accepted close command must remain retained',
    );
    equal(
      closeCommand.body,
      JSON.parse(closeCommand.bodySnapshot),
      'Original close command changed',
    );
    if (!Number.isSafeInteger(scope.lesson) || scope.lesson <= 0) {
      requireValue(
        !archiveStarted && !closeLookupAttempted,
        'Only one exact create-command recovery is allowed',
      );
      closeLookupAttempted = true;
      const recovered = await closeCommand.actor.call(
        'POST',
        '/api/schedule/one-off-lessons',
        closeCommand.body,
        [201, 200],
        { 'Idempotency-Key': closeCommand.key },
      );
      scope.lesson = Number(recovered.physicalLessonId);
      requireValue(
        Number.isSafeInteger(scope.lesson) && scope.lesson > 0,
        'Exact create replay must identify its lesson',
      );
    }
    const isClosed = async () => {
      const rows = Object.values(
        (await closeCommand!.actor.call('GET', closeCommand!.lessonsPath))._embedded ?? {},
      ).flat();
      if (!cleaning) {
        const target = (rows as Row[]).find((row) => Number(row.id) === scope.lesson);
        diagnostic.mainCloseLastRead = {
          targetPresent: target !== undefined,
          groupMatches: target !== undefined && Number(target.groupId) === scope.group,
          semesterMatches: target !== undefined && Number(target.semesterId) === scope.semester,
          status:
            target?.status === 'PLANNED' ||
            target?.status === 'ACTIVE' ||
            target?.status === 'CLOSED'
              ? target.status
              : 'OTHER_OR_ABSENT',
        };
      }
      return rows.some(
        (row: any) =>
          Number(row.id) === scope.lesson &&
          Number(row.groupId) === scope.group &&
          Number(row.semesterId) === scope.semester &&
          row.status === 'CLOSED',
      );
    };
    // One exact final read is still useful if the original wait expired just before cleanup.
    if (!(await isClosed())) {
      const remaining = Math.min(budget(90000), Math.floor(closeDeadline - performance.now()));
      requireValue(remaining > 0, 'Original close recovery window exhausted');
      await expect
        .poll(isClosed, {
          timeout: remaining,
          message: 'Exact owned lesson closes in its original window',
        })
        .toBe(true);
    }
    lessonClosed = true;
    if (!producerDeadline) producerDeadline = performance.now() + 10000;
    const isSent = () => effectRows().some((row) => row.producer?.status === 'sent');
    if (!isSent()) {
      const remaining = Math.min(budget(10000), Math.floor(producerDeadline - performance.now()));
      requireValue(remaining > 0, 'Original producer recovery window exhausted');
      await expect
        .poll(isSent, { timeout: remaining, message: 'Exact original close producer reaches SENT' })
        .toBe(true);
    }
    effect();
    consumer();
  };
  try {
    for (const service of [
      'api-gateway',
      'academic-service',
      'schedule-service',
      'attendance-service',
      'postgres-schedule',
      'postgres-academic',
      'mongo-attendance',
      'rabbitmq',
    ]) {
      const ids = docker([...compose, 'ps', '-q', service])
        .trim()
        .split(/\s+/);
      requireValue(
        ids.length === 1 && /^[0-9a-f]{12,64}$/.test(ids[0]),
        'One owned container required',
      );
      pins[service] = inspect(ids[0]);
      if (local) requireValue(localSpecialService(local, service).id === pins[service].id, 'Local reconciliation service mismatch');
      const current = identity(service);
      requireValue(
        current.running && current.health === 'healthy',
        'Fresh owned stack must be healthy',
      );
    }
    admin = await admit(request, TEST_USERS.admin, 'ADMIN');
    const access = await admin.call('GET', '/api/academic/admin-access/me');
    requireValue(
      access.mode === 'FULL' && access.assigned === true,
      'Live FULL administrator required',
    );
    const inventory = await semesters(admin),
      active = inventory.filter((row) => row.active === true);
    requireValue(
      active.length === 1 &&
        inventory.every(
          (row) =>
            row.transition === 'NONE' &&
            row.releasePending === false &&
            row.activationScheduled === false,
        ),
      'Known current CI authority required',
    );
    previous = active[0];
    const noFallback = new (class extends Actor {
      override async call(...args: Parameters<Actor['call']>) {
        requireValue(
          !(args[0] === 'POST' && args[1] === '/api/academic/semesters') &&
            !(args[0] === 'PATCH' && /\/semesters\/[^/]+\/activate$/.test(args[1])),
          'Fallback semester mutation forbidden',
        );
        return super.call(...args);
      }
    })(request, admin.token, admin.session);
    const dataset = new MobileDataset(request, noFallback, clock.instant);
    requireValue(
      previous.archived === false &&
        previous.dateFrom <= shift(dataset.today, -1) &&
        previous.dateTo >= dataset.tomorrow,
      'Known semester must cover own historical close',
    );
    await dataset.initialize();
    requireValue(
      Number(dataset.semester.id) === Number(previous.id),
      'Original semester must remain current',
    );
    scope = { semester: Number(previous.id), group: dataset.group.id };
    requireValue(
      Number.isSafeInteger(scope.semester) &&
        scope.semester > 0 &&
        Number.isSafeInteger(scope.group) &&
        scope.group > 0,
      'Exact positive synthetic scope required',
    );
    const expectedRoster = [dataset.student.id, dataset.headman.id, dataset.assistant.id];
    const assignment = dataset.subject.assignments.find(
      (row: Row) =>
        Number(row.semesterId) === scope.semester &&
        row.validFrom <= shift(dataset.today, -1) &&
        (row.validUntilExclusive == null || row.validUntilExclusive > shift(dataset.today, -1)),
    );
    requireValue(assignment, 'Own assignment must cover the historical close');
    const expectedInitialMemberships = [
      ...expectedRoster.map((studentId) => [Number(studentId), Number(scope.group)]),
      [Number(dataset.foreignStudent.id), Number(dataset.otherGroup.id)],
    ];
    requireValue(
      expectedInitialMemberships.length === 4 &&
        new Set(expectedInitialMemberships.map(([studentId]) => studentId)).size === 4 &&
        expectedInitialMemberships.every((tuple) =>
          tuple.every((value) => Number.isSafeInteger(value) && value > 0),
        ) &&
        Number(dataset.otherGroup.id) !== Number(scope.group),
      'Four exact owned initial memberships are required',
    );
    const initialReadinessEnd = performance.now() + budget(10000);
    const initialMembershipsReady = (): {
      expectedApplied: boolean;
      noPendingWork: boolean;
      producerSent: boolean;
    } => {
      const remaining = () => {
        const value = Math.floor(initialReadinessEnd - performance.now());
        requireValue(value > 0, 'Initial membership readiness deadline exhausted');
        return Math.min(2000, value);
      };
      let stage:
        | 'IDENTITY_BUDGET'
        | 'IDENTITY'
        | 'QUERY_BUDGET'
        | 'QUERY'
        | 'OUTPUT'
        | 'JSON'
        | 'SHAPE' = 'IDENTITY_BUDGET';
      try {
        const identityCap = remaining();
        stage = 'IDENTITY';
        const current = identity('postgres-academic', identityCap);
        const values = expectedInitialMemberships
          .map(([studentId, groupId]) => `(${studentId},${groupId})`)
          .join(',');
        const sql = `SELECT json_build_object(
'expectedApplied',(SELECT bool_and((SELECT count(*)=1 AND
COALESCE(bool_and(w.group_id=e.group_id AND w.state='APPLIED'),false)
FROM academic_initial_membership_backfills w
WHERE w.student_id=e.student_id AND w.semester_id=${scope.semester}))
FROM (VALUES ${values}) AS e(student_id,group_id)),
'noPendingWork',NOT EXISTS(SELECT 1 FROM academic_initial_membership_backfills WHERE state='PENDING'),
'producerSent',NOT EXISTS(SELECT 1 FROM academic_outbox
WHERE event_type='student.initial-membership.recorded' AND status<>'sent'))`;
        stage = 'QUERY_BUDGET';
        const queryCap = remaining();
        stage = 'QUERY';
        const raw = docker(
          [
            'exec',
            '-i',
            '-e',
            'PGPASSWORD',
            current.id,
            'psql',
            '-X',
            '-qAt',
            '-U',
            'rct_user',
            '-d',
            'academic_db',
            '-v',
            'ON_ERROR_STOP=1',
          ],
          `BEGIN READ ONLY; SET LOCAL statement_timeout='2000ms'; ${sql}; ROLLBACK;`,
          { PGPASSWORD: secret.POSTGRES_ACADEMIC_PASSWORD },
          queryCap,
        );
        stage = 'OUTPUT';
        const lines = raw
          .trim()
          .split('\n')
          .filter((line) => !['BEGIN', 'SET', 'ROLLBACK'].includes(line));
        requireValue(
          Buffer.byteLength(raw, 'utf8') <= 1024 && lines.length === 1,
          'One bounded initial membership projection is required',
        );
        stage = 'JSON';
        const projection: unknown = JSON.parse(lines[0]);
        stage = 'SHAPE';
        requireValue(
          projection !== null &&
            typeof projection === 'object' &&
            !Array.isArray(projection) &&
            Object.keys(projection).sort().join(',') ===
              'expectedApplied,noPendingWork,producerSent' &&
            Object.values(projection).every((value) => typeof value === 'boolean'),
          'Initial membership projection must contain exact booleans',
        );
        return projection as {
          expectedApplied: boolean;
          noPendingWork: boolean;
          producerSent: boolean;
        };
      } catch (error) {
        if (nativeFailure !== undefined && nativeFailure.error === error) nativeFailure.report();
        switch (stage) {
          case 'IDENTITY_BUDGET':
          case 'QUERY_BUDGET':
            throw new Error('Owned initial membership readiness deadline failed');
          case 'IDENTITY':
            throw new Error('Owned initial membership readiness identity read failed');
          case 'QUERY':
            throw new Error('Owned initial membership readiness query failed');
          case 'OUTPUT':
            throw new Error('Owned initial membership readiness output shape failed');
          case 'JSON':
            throw new Error('Owned initial membership readiness JSON decode failed');
          case 'SHAPE':
            throw new Error('Owned initial membership readiness boolean shape failed');
        }
      }
    };
    const quiescence = {
      firstFailed: 'UNOBSERVED' as
        | 'UNOBSERVED'
        | 'MAIN_READY'
        | 'MAIN_UNACKED'
        | 'MAIN_CONSUMER'
        | 'DLQ_READY'
        | 'DLQ_UNACKED'
        | 'RETRY_READY'
        | 'RETRY_UNACKED'
        | 'SCHEDULE_UNPROVEN'
        | 'SCHEDULE_LEGACY'
        | 'SCHEDULE_DUE'
        | 'INITIAL_EXPECTED'
        | 'INITIAL_PENDING'
        | 'INITIAL_PRODUCER',
    };
    try {
      await expect
        .poll(
          async () => {
            quiescence.firstFailed = 'UNOBSERVED';
            const q = await queues();
            const state =
              pg(`SELECT json_build_object('unproven',(SELECT count(*) FROM schedule_semester_archive_effect_ledger
WHERE (semester_id=${scope.semester} OR semester_id IS NULL) AND state<>'APPLIED'),
'legacy',(SELECT count(*) FROM schedule_outbox o WHERE o.event_type IN
('lesson.closed','lesson.cancelled','lesson.deleted','lesson.one_off.cancelled','homework.binding.archived')
AND (o.payload#>>'{payload,semester_id}'='${scope.semester}' OR o.payload#>>'{payload,semester_id}' IS NULL)
AND NOT EXISTS(SELECT 1 FROM schedule_semester_archive_effect_ledger e
WHERE e.event_id::text=o.payload->>'event_id' AND e.event_type=o.event_type)),
'due',(SELECT count(*) FROM lessons WHERE status IN ('planned','active') AND date+end_time+interval '5 minutes'
<=('${clock.instant.toISOString()}'::timestamptz AT TIME ZONE 'Europe/Moscow')))`);
            const initial = initialMembershipsReady();
            if (q.main.messages_ready !== 0) quiescence.firstFailed = 'MAIN_READY';
            else if (q.main.messages_unacknowledged !== 0) quiescence.firstFailed = 'MAIN_UNACKED';
            else if (q.main.consumers !== 1) quiescence.firstFailed = 'MAIN_CONSUMER';
            else if (q.dead.messages_ready !== 0) quiescence.firstFailed = 'DLQ_READY';
            else if (q.dead.messages_unacknowledged !== 0) quiescence.firstFailed = 'DLQ_UNACKED';
            else if (q.retry.messages_ready !== 0) quiescence.firstFailed = 'RETRY_READY';
            else if (q.retry.messages_unacknowledged !== 0)
              quiescence.firstFailed = 'RETRY_UNACKED';
            else if (state.unproven !== 0) quiescence.firstFailed = 'SCHEDULE_UNPROVEN';
            else if (state.legacy !== 0) quiescence.firstFailed = 'SCHEDULE_LEGACY';
            else if (state.due !== 0) quiescence.firstFailed = 'SCHEDULE_DUE';
            else if (!initial.expectedApplied) quiescence.firstFailed = 'INITIAL_EXPECTED';
            else if (!initial.noPendingWork) quiescence.firstFailed = 'INITIAL_PENDING';
            else if (!initial.producerSent) quiescence.firstFailed = 'INITIAL_PRODUCER';
            const count = (value: unknown): number | null =>
              typeof value === 'number' &&
              Number.isInteger(value) &&
              value >= 0 &&
              value <= 1_000_000
                ? value
                : null;
            diagnostic.setupQuiescenceLastRead = {
              firstFailed: quiescence.firstFailed,
              mainReady: count(q.main?.messages_ready),
              mainUnacked: count(q.main?.messages_unacknowledged),
              mainConsumers: count(q.main?.consumers),
              deadReady: count(q.dead?.messages_ready),
              deadUnacked: count(q.dead?.messages_unacknowledged),
              retryReady: count(q.retry?.messages_ready),
              retryUnacked: count(q.retry?.messages_unacknowledged),
              scheduleUnproven: count(state?.unproven),
              scheduleLegacy: count(state?.legacy),
              scheduleDue: count(state?.due),
              initialExpectedApplied: initial.expectedApplied,
              initialNoPendingWork: initial.noPendingWork,
              initialProducerSent: initial.producerSent,
            };
            return (
              q.main.messages_ready === 0 &&
              q.main.messages_unacknowledged === 0 &&
              q.main.consumers === 1 &&
              q.dead.messages_ready === 0 &&
              q.dead.messages_unacknowledged === 0 &&
              q.retry.messages_ready === 0 &&
              q.retry.messages_unacknowledged === 0 &&
              state.unproven === 0 &&
              state.legacy === 0 &&
              state.due === 0 &&
              initial.expectedApplied &&
              initial.noPendingWork &&
              initial.producerSent
            );
          },
          {
            timeout: Math.max(1, Math.min(budget(10000), initialReadinessEnd - performance.now())),
            message: 'Fresh synthetic namespace is quiescent',
          },
        )
        .toBe(true);
    } catch (error) {
      switch (quiescence.firstFailed) {
        case 'MAIN_READY':
          expect.soft(false, 'D9_QUIESCENCE_MAIN_READY').toBe(true);
          break;
        case 'MAIN_UNACKED':
          expect.soft(false, 'D9_QUIESCENCE_MAIN_UNACKED').toBe(true);
          break;
        case 'MAIN_CONSUMER':
          expect.soft(false, 'D9_QUIESCENCE_MAIN_CONSUMER').toBe(true);
          break;
        case 'DLQ_READY':
          expect.soft(false, 'D9_QUIESCENCE_DLQ_READY').toBe(true);
          break;
        case 'DLQ_UNACKED':
          expect.soft(false, 'D9_QUIESCENCE_DLQ_UNACKED').toBe(true);
          break;
        case 'RETRY_READY':
          expect.soft(false, 'D9_QUIESCENCE_RETRY_READY').toBe(true);
          break;
        case 'RETRY_UNACKED':
          expect.soft(false, 'D9_QUIESCENCE_RETRY_UNACKED').toBe(true);
          break;
        case 'SCHEDULE_UNPROVEN':
          expect.soft(false, 'D9_QUIESCENCE_SCHEDULE_UNPROVEN').toBe(true);
          break;
        case 'SCHEDULE_LEGACY':
          expect.soft(false, 'D9_QUIESCENCE_SCHEDULE_LEGACY').toBe(true);
          break;
        case 'SCHEDULE_DUE':
          expect.soft(false, 'D9_QUIESCENCE_SCHEDULE_DUE').toBe(true);
          break;
        case 'INITIAL_EXPECTED':
          expect.soft(false, 'D9_QUIESCENCE_INITIAL_EXPECTED').toBe(true);
          break;
        case 'INITIAL_PENDING':
          expect.soft(false, 'D9_QUIESCENCE_INITIAL_PENDING').toBe(true);
          break;
        case 'INITIAL_PRODUCER':
          expect.soft(false, 'D9_QUIESCENCE_INITIAL_PRODUCER').toBe(true);
          break;
      }
      throw error;
    }
    const body = Object.freeze({
      groupId: dataset.group.id,
      subjectId: dataset.subject.id,
      assignmentId: assignment.id,
      date: shift(dataset.today, -1),
      lessonNumber: 1,
      classroom: 'Синтетическое адресное восстановление',
    });
    closeCommand = {
      actor: dataset.headmanActor,
      key: randomUUID(),
      body,
      bodySnapshot: JSON.stringify(body),
      lessonsPath: `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${shift(dataset.today, -1)}&dateTo=${dataset.today}&size=200`,
    };
    diagnostic.phase = 'CONTROLLED_CLOSE';
    stop('attendance-service');
    closeDeadline = performance.now() + 90000;
    budget(10000, { event: true }, true);
    // Keep accepted-or-ambiguous state even when the committed HTTP response is lost.
    eventAccepted = true;
    const past = await closeCommand.actor.call(
      'POST',
      '/api/schedule/one-off-lessons',
      closeCommand.body,
      [201, 200],
      { 'Idempotency-Key': closeCommand.key },
    );
    scope.lesson = Number(past.physicalLessonId);
    requireValue(
      Number.isSafeInteger(scope.lesson) && scope.lesson > 0,
      'Owned physical lesson required',
    );
    await recoverOwnClose();
    stop('schedule-service');
    diagnostic.phase = 'STRANDED';
    // Observe retry admission while the same application starts. The original synchronous
    // restart --wait can otherwise hide the entire thirty-second delayed phase behind health.
    budget(5000, {}, true);
    identity('attendance-service');
    docker(
      [
        ...compose,
        'up',
        '-d',
        '--no-build',
        '--no-recreate',
        '--no-deps',
        '--pull',
        'never',
        'attendance-service',
      ],
      undefined,
      {},
      5000,
    );
    requireValue(
      identity('attendance-service').running === true,
      'Same owned Attendance must run during the controlled dependency outage',
    );
    await captureDelayedRetry();
    // Aggregate health includes the intentionally unavailable Schedule gRPC channel.
    // Keep that outage through final DLQ; require full health after Schedule returns.
    await expect
      .poll(
        async () => {
          const q = await queues();
          // Last completed MAIN read, not a timestamp or a cleanup observation.
          strandedLastRead = {
            deadReadyOne: q.dead.messages_ready === 1,
            deadUnackedZero: q.dead.messages_unacknowledged === 0,
            mainReadyZero: q.main.messages_ready === 0,
            mainUnackedZero: q.main.messages_unacknowledged === 0,
            deadReadyClass:
              Number.isSafeInteger(q.dead.messages_ready) && q.dead.messages_ready >= 0
                ? q.dead.messages_ready === 0
                  ? 'ZERO'
                  : q.dead.messages_ready === 1
                    ? 'ONE'
                    : 'MANY'
                : 'UNKNOWN',
          };
          return (
            q.dead.messages_ready === 1 &&
            q.dead.messages_unacknowledged === 0 &&
            q.main.messages_ready === 0 &&
            q.main.messages_unacknowledged === 0 &&
            q.retry.messages_ready === 0 &&
            q.retry.messages_unacknowledged === 0
          );
        },
        {
          // One thirty-second delay, three bounded three-second dependency attempts, and a
          // finite margin for backoff/broker observations; not a fresh unbounded retry cycle.
          timeout: budget(30000 + 3 * 3000 + 6000),
          message: 'One delayed cycle and the final three attempts retain the original in DLQ',
        },
      )
      .toBe(true);
    await stranded();
    await restart('schedule-service');
    // Observe automatic channel recovery without resetting the running Attendance process.
    await restart('attendance-service', Date.now());
    diagnostic.phase = 'PENDING_ARCHIVE';
    budget(10000, { archive: true }, true);
    for (const service of ['schedule-service', 'attendance-service']) {
      const current = identity(service);
      requireValue(
        current.running && current.health === 'healthy',
        'Archive dependencies must remain healthy',
      );
    }
    const readinessEnd = performance.now() + budget(10000);
    let lastReadinessTransport: { error: unknown } | undefined;
    try {
      await expect
        .poll(
          async () => {
            const remaining = Math.floor(readinessEnd - performance.now());
            requireValue(remaining > 0, 'Directed archive readiness deadline exhausted');
            const requestedAt = new Date().toISOString();
            readinessRequest = undefined;
            let response: APIResponse;
            try {
              response = await admin!.request.get(
                `/api/academic/semesters/${scope.semester}/delete-preview`,
                { headers: admin!.headers(), timeout: Math.min(2000, remaining) },
              );
            } catch (error) {
              const observed: TestInfo['annotations'] = [];
              retainSemesterPreviewTransport(observed, error);
              const hint = safeNativePrimaryFailures(observed).find(
                (failure) => failure.path === 'SEMESTER_PREVIEW',
              )?.transportHint;
              if (!cleaning) {
                diagnostic.archiveReadinessLastRead = {
                  httpStatus: null,
                  transport: hint ?? 'UNKNOWN',
                  semesterMatches: null,
                  priorStateActive: null,
                  stateVersionMatches: null,
                };
              }
              if (hint === 'ECONNRESET' || hint === 'ECONNREFUSED') {
                lastReadinessTransport = { error };
                return false;
              }
              lastReadinessTransport = undefined;
              retainSemesterPreviewTransport(info.annotations, error);
              // Retain only the transport class; never inspect native request details.
              if (error instanceof playwrightErrors.TimeoutError)
                expect.soft(false, 'D9_PREVIEW_REQUEST_TIMEOUT').toBe(true);
              else expect.soft(false, 'D9_PREVIEW_REQUEST_OTHER').toBe(true);
              throw error;
            }
            lastReadinessTransport = undefined;
            try {
              if (!cleaning) {
                const status = response.status();
                diagnostic.archiveReadinessLastRead = {
                  httpStatus:
                    Number.isInteger(status) && status >= 100 && status <= 599 ? status : null,
                  transport: null,
                  semesterMatches: null,
                  priorStateActive: null,
                  stateVersionMatches: null,
                };
              }
              if (response.status() === 503) {
                try {
                  const id: unknown = response.headers()['x-request-id'];
                  if (
                    typeof id === 'string' &&
                    id.length === 36 &&
                    /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(id)
                  )
                    readinessRequest = { id, since: requestedAt, until: new Date().toISOString() };
                } catch {
                  // Missing correlation stays UNKNOWN; never inspect or retain response content.
                }
                return false;
              }
              requireValue(response.status() === 200, 'Directed archive readiness read rejected');
              const preview = await response.json();
              if (!cleaning) {
                const object =
                  typeof preview === 'object' && preview !== null && !Array.isArray(preview);
                diagnostic.archiveReadinessLastRead = {
                  httpStatus: 200,
                  transport: null,
                  semesterMatches: object && preview.semesterId === scope.semester,
                  priorStateActive: object && preview.priorState === 'ACTIVE',
                  stateVersionMatches: object && preview.stateVersion === previous!.stateVersion,
                };
              }
              requireValue(
                preview.semesterId === scope.semester &&
                  preview.priorState === 'ACTIVE' &&
                  preview.stateVersion === previous!.stateVersion,
                'Directed archive readiness semester identity changed',
              );
              return true;
            } finally {
              await response.dispose();
            }
          },
          {
            timeout: Math.max(1, Math.floor(readinessEnd - performance.now())),
            message: 'Academic directed archive dependencies are ready',
          },
        )
        .toBe(true);
    } catch (error) {
      if (lastReadinessTransport) {
        const original = lastReadinessTransport.error;
        retainSemesterPreviewTransport(info.annotations, original);
        expect.soft(false, 'D9_PREVIEW_REQUEST_OTHER').toBe(true);
        throw original;
      }
      throw error;
    }
    budget(10000, { archive: true }, true);
    archiveStarted = true;
    authorityDirty = true;
    const operation = await admin.call(
      'POST',
      `/api/academic/semesters/${scope.semester}/archive`,
      undefined,
      [200, 202, 503],
      { 'Idempotency-Key': archiveKey },
    );
    if (!operation.operationId) {
      requireValue(false, 'Initial archive operation identity missing');
    }
    if (operation.operationState === 'ERROR' && operation.retryable === true) {
      // Classify only the original POST response: a further GET advances the coordinator.
      const reason = operation.blockingReason;
      const reasonCode = semesterReasonCode(reason);
      if (reason === 'Schedule returned a different archive operation identity')
        requireValue(false, 'Initial archive retryable receipt identity mismatch');
      if (reason === 'Schedule returned a receipt for a different archive command phase')
        requireValue(false, 'Initial archive retryable receipt phase mismatch');
      if (reason === 'Schedule returned an unspecified archive barrier state')
        requireValue(false, 'Initial archive retryable receipt state unknown');
      const rpcPrefix =
        'Не удалось установить barrier архивации в schedule-service в schedule-service: ';
      const rpc =
        typeof reason === 'string' && reason.startsWith(rpcPrefix)
          ? reason.slice(rpcPrefix.length)
          : '';
      if (/^Status\{code=UNAVAILABLE(?:,|})/.test(rpc)) {
        if (
          rpc.startsWith(
            'Status{code=UNAVAILABLE, description=Academic archive authority could not be verified, cause=',
          )
        )
          requireValue(
            false,
            'Initial archive retryable Schedule reports unverified Academic archive authority',
          );
        if (
          rpc.includes('javax.net.ssl.SSLHandshakeException:') ||
          rpc.includes(
            'io.netty.handler.ssl.ReferenceCountedOpenSslEngine$OpenSslHandshakeException:',
          )
        )
          requireValue(false, 'Initial archive retryable Schedule RPC TLS handshake marker');
        if (rpc.includes('io.netty.handler.ssl.NotSslRecordException:'))
          requireValue(false, 'Initial archive retryable Schedule RPC TLS record marker');
        if (
          (rpc.includes('java.net.ConnectException:') ||
            rpc.includes('io.netty.channel.AbstractChannel$AnnotatedConnectException:')) &&
          rpc.includes('Connection refused')
        )
          requireValue(false, 'Initial archive retryable Schedule RPC connection refused marker');
        requireValue(false, 'Initial archive retryable Schedule RPC unavailable');
      }
      if (/^Status\{code=DEADLINE_EXCEEDED(?:,|})/.test(rpc))
        requireValue(false, 'Initial archive retryable Schedule RPC deadline');
      if (/^Status\{code=INTERNAL(?:,|})/.test(rpc))
        requireValue(false, 'Initial archive retryable Schedule RPC internal failure');
      if (rpc) requireValue(false, 'Initial archive retryable Schedule RPC other status');
      if (reasonCode === 'SCHEDULE_EFFECT_ACK_PENDING')
        requireValue(false, 'Initial archive retryable error with exact Schedule ACK reason');
      if (reasonCode !== 'UNKNOWN')
        requireValue(false, 'Initial archive retryable error with classified participant reason');
      if (
        operation.academic === 'NOT_STARTED' &&
        operation.schedule === 'NOT_STARTED' &&
        operation.attendance === 'NOT_STARTED'
      )
        requireValue(
          false,
          'Initial archive retryable unknown reason before participant readiness',
        );
      if (operation.academic === 'READY' && operation.schedule === 'NOT_STARTED')
        requireValue(false, 'Initial archive retryable unknown reason after Academic readiness');
      if (operation.schedule === 'PENDING')
        requireValue(false, 'Initial archive retryable unknown reason with Schedule pending');
      requireValue(false, 'Initial archive operation retryable error');
    }
    if (operation.operationState === 'ERROR' && operation.retryable === false) {
      requireValue(false, 'Initial archive operation terminal error');
    }
    if (operation.operationState === 'COMPLETED') {
      requireValue(false, 'Initial archive operation already completed');
    }
    if (operation.operationState !== 'PENDING') {
      requireValue(false, 'Initial archive operation state unknown');
    }
    requireValue(
      operation.operationId && operation.operationState === 'PENDING',
      'One pending archive operation required',
    );
    let pendingReads = 0;
    await expect
      .poll(
        async () => {
          const status = await admin!.call(
            'GET',
            `/api/academic/semesters/${scope.semester}/archive/status`,
          );
          requireValue(
            status.operation?.operationId === operation.operationId &&
              status.operation.stateVersion === operation.stateVersion &&
              status.operation.operationState === 'PENDING' &&
              status.transition === 'ARCHIVING' &&
              status.writeBlocked === true &&
              semesterReasonCode(status.operation.blockingReason) === 'SCHEDULE_EFFECT_ACK_PENDING',
            'Dependency return must not fabricate completion',
          );
          await stranded();
          return ++pendingReads;
        },
        {
          timeout: budget(10000),
          intervals: [3100],
          message: 'Same operation stays pending through coordinator retry',
        },
      )
      .toBe(2);
    diagnostic.pendingBeforeReplay = true;
    diagnostic.phase = 'EXACT_REPLAY';
    await publish();
    await awaitApplied();
    const completed = await terminal(
      admin,
      scope.semester,
      'ARCHIVE',
      archiveKey,
      archiveProgress,
      budget(20000),
    );
    archived = true;
    requireValue(
      completed.operation.operationId === operation.operationId,
      'Same archive operation must complete',
    );
    diagnostic.sameArchiveCompleted = true;
    const first = consumer();
    requireValue(
      first.docs.length === expectedRoster.length &&
        first.docs.every((row: Row) => row.status === 'absent') &&
        new Set(first.docs.map((row: Row) => row.user_id)).size === expectedRoster.length &&
        expectedRoster.every((id) => first.docs.some((row: Row) => row.user_id === id)),
      'Owned historical roster must materialize exactly once',
    );
    // Student history is denied while inactive; the own HEADMAN historical journal remains readable.
    const historyPath = `/api/attendance/reports/lesson/${scope.lesson}`;
    const historyBefore = await dataset.headmanActor.call('GET', historyPath),
      ledgerBefore = effect();
    requireValue(
      Number(historyBefore.lessonId) === scope.lesson &&
        Number(historyBefore.groupId) === scope.group &&
        Number(historyBefore.semesterId) === scope.semester &&
        Number(historyBefore.subjectId) === dataset.subject.id &&
        historyBefore.editable === false &&
        Array.isArray(historyBefore.entries) &&
        historyBefore.entries.length === expectedRoster.length &&
        expectedRoster.every((id) =>
          historyBefore.entries.some((row: Row) => Number(row.userId) === id),
        ),
      'Archived HEADMAN read must retain its exact own lesson semester and roster',
    );
    diagnostic.phase = 'DUPLICATE';
    await publish(true);
    await expect
      .poll(() => consumer().acks.length, {
        timeout: budget(10000),
        message: 'Exact receipt replay emits one additional ACK',
      })
      .toBe(first.acks.length + 1);
    const duplicate = consumer();
    equal(duplicate.docs, first.docs, 'Duplicate close changed marks or revisions');
    equal(duplicate.receipt, first.receipt, 'Duplicate close changed receipt');
    requireValue(
      duplicate.claims === 1 && duplicate.claims === first.claims,
      'Duplicate close must retain one claim',
    );
    equal(effect(), ledgerBefore, 'Duplicate ACK changed applied ledger');
    equal(
      await dataset.headmanActor.call('GET', historyPath),
      historyBefore,
      'Duplicate close changed permitted own historical journal',
    );
    diagnostic.duplicateBusinessUnchanged = true;
  } catch (error) {
    if (nativeFailure !== undefined && nativeFailure.error === error) nativeFailure.report();
    originalFailure = { error };
    if (diagnostic.phase === 'EXACT_REPLAY' && diagnostic.recoveryPublishAttempted)
      captureReplayFailure();
    if (
      diagnostic.phase === 'PENDING_ARCHIVE' &&
      diagnostic.archiveReadinessLastRead?.httpStatus === 503
    )
      captureReadinessFailure();
    diagnostic.strandedLastRead = strandedLastRead;
    captureHttp500OwnedServices(error);
    retainFirstDatasetHttpFailure(info.annotations, error);
  } finally {
    cleaning = true;
    let cleanupFailure: { error: unknown } | undefined;
    try {
      for (const service of ['schedule-service', 'attendance-service']) {
        try {
          await restart(service);
        } catch (error) {
          retainLifecycleCleanupFailure(info.annotations, cleanupStage, error);
          cleanupFailure ??= { error };
        }
      }
      if (cleanupFailure) throw cleanupFailure.error;
      if (eventAccepted && !applied) {
        cleanupStage = 'CLEANUP_SEMESTER_RECOVER';
        await recoverOwnClose();
        if (
          !diagnostic.recoveryPublishAttempted &&
          consumer().receipt.length === 0 &&
          (await queues()).dead.messages_ready === 1
        ) {
          cleanupStage = 'CLEANUP_SEMESTER_PUBLISH';
          await publish();
        }
        cleanupStage = 'CLEANUP_SEMESTER_APPLY';
        await awaitApplied();
      }
      if (admin && previous && archiveStarted) {
        if (!archived) {
          cleanupStage = 'CLEANUP_SEMESTER_ARCHIVE';
          await terminal(
            admin,
            Number(previous.id),
            'ARCHIVE',
            archiveKey,
            archiveProgress,
            budget(20000),
          );
          archived = true;
        }
        if (!restored) {
          cleanupStage = 'CLEANUP_SEMESTER_RESTORE';
          await terminal(
            admin,
            Number(previous.id),
            'RESTORE',
            restoreKey,
            restoreProgress,
            budget(20000),
          );
          restored = true;
        }
        cleanupStage = 'CLEANUP_SEMESTER_ACTIVATE';
        const current = await admin.call(
          'PATCH',
          `/api/academic/semesters/${previous.id}/activate`,
        );
        requireValue(
          current.active === true &&
            current.archived === false &&
            current.activationScheduled === false,
          'Original active authority must return',
        );
        cleanupStage = 'CLEANUP_SEMESTER_VERIFY';
        const active = (await semesters(admin)).filter((row) => row.active === true);
        requireValue(
          active.length === 1 && Number(active[0].id) === Number(previous.id),
          'Original exclusive active authority must return',
        );
        authorityDirty = false;
      }
      if (peeked) {
        cleanupStage = 'CLEANUP_SEMESTER_DLQ';
        const q = await queues();
        requireValue(
          q.dead.messages_ready === 1 && q.dead.messages_unacknowledged === 0,
          'Original DLQ copy must remain retained',
        );
        diagnostic.retainedDlqCopies = 1;
      }
      cleanupStage = 'CLEANUP_SEMESTER_SETTLE';
      requireValue(
        stopped.size === 0 && !authorityDirty && (!eventAccepted || applied),
        'Controlled state must settle safely',
      );
      diagnostic.restorationSucceeded = true;
      diagnostic.cleanupOutcome = 'PASSED';
    } catch (error) {
      retainLifecycleCleanupFailure(info.annotations, cleanupStage, error);
      cleanupFailure = { error };
      diagnostic.cleanupOutcome = 'FAILED';
    }
    try {
      cleanupStage = 'CLEANUP_SEMESTER_CLOCK';
      clock.assertUnchanged();
      diagnostic.clockUnchanged = true;
    } catch (error) {
      retainLifecycleCleanupFailure(info.annotations, cleanupStage, error);
      cleanupFailure ??= { error };
    }
    let disposeTimer: ReturnType<typeof setTimeout> | undefined;
    try {
      cleanupStage = 'CLEANUP_SEMESTER_DISPOSE';
      await Promise.race([
        request.dispose(),
        new Promise<never>((_, reject) => {
          disposeTimer = setTimeout(
            () => reject(new Error('Reconciliation request disposal deadline')),
            budget(5000),
          );
        }),
      ]);
    } catch (error) {
      retainLifecycleCleanupFailure(info.annotations, cleanupStage, error);
      cleanupFailure ??= { error };
    } finally {
      if (disposeTimer !== undefined) clearTimeout(disposeTimer);
    }
    if (cleanupFailure) {
      diagnostic.restorationSucceeded = false;
      diagnostic.cleanupOutcome = 'FAILED';
    }
    try {
      cleanupStage = 'CLEANUP_SEMESTER_WRITE';
      const safeDiagnostic = `${JSON.stringify(diagnostic)}\n`;
      requireValue(
        Buffer.byteLength(safeDiagnostic, 'utf8') <= 4096,
        'Reconciliation diagnostic must remain bounded',
      );
      writeHealthDiagnostic(diagnostic, run, safeDiagnostic, {
        mode: 0o600,
        flag: 'wx',
      });
    } catch (error) {
      retainLifecycleCleanupFailure(info.annotations, cleanupStage, error);
      cleanupFailure ??= { error };
    }
    if (originalFailure) throw originalFailure.error;
    if (cleanupFailure) throw cleanupFailure.error;
  }
});

// Subject deletion has its own test/fixture slot; the two existing semester scenarios are unchanged.
function subjectCascadeOtpReceiver(info: TestInfo) {
  const run = process.env.RCT_SEMESTER_LIFECYCLE_RUN;
  requireValue(!local || run === local.laneRun, 'Local semester checkpoint mismatch');
  requireValue(run && isAbsolute(run), 'OTP receiver requires a private run directory');
  for (const directory of [run, join(run, 'artifacts')]) {
    const stat = lstatSync(directory);
    requireValue(
      stat.isDirectory() && !stat.isSymbolicLink() && (stat.mode & 0o777) === 0o700,
      'OTP receiver directories must be private',
    );
  }
  requireValue(
    (local !== undefined || process.env.CI === 'true') &&
      process.env.RCT_E2E_SYNTHETIC_SEED === 'true' &&
      info.project.outputDir === join(run, 'artifacts') &&
      info.project.use.baseURL === 'https://localhost:8443',
    'OTP receiver requires the exact disposable CI lane',
  );
  const root = resolve(__dirname, '../../..');
  const envFile = (local ? join(local.run, 'synthetic.env') : join(root, 'tests/e2e/.env.ci'));
  const envStat = lstatSync(envFile);
  requireValue(
    envStat.isFile() &&
      !envStat.isSymbolicLink() &&
      envStat.size <= 65536 &&
      (envStat.mode & 0o022) === 0,
    'Synthetic CI configuration must be bounded',
  );
  const secret: Record<string, string> = {};
  for (const line of readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const match = /^(RABBITMQ_USER|RABBITMQ_PASSWORD)=(.*)$/.exec(line);
    if (!match) continue;
    let value = match[2];
    if (value.length >= 2 && ['"', "'"].includes(value[0]) && value.at(-1) === value[0])
      value = value.slice(1, -1);
    requireValue(
      value.length > 0 && value.length <= 1024 && secret[match[1]] === undefined,
      'Synthetic broker credential shape is invalid',
    );
    secret[match[1]] = value;
  }
  requireValue(
    secret.RABBITMQ_USER && secret.RABBITMQ_PASSWORD,
    'Synthetic broker credentials are required',
  );
  const docker = (args: string[], timeout = 4000) => {
    try {
      return execFileSync('docker', args, {
        cwd: root,
        env: { PATH: process.env.PATH ?? '/usr/bin:/bin', LANG: 'C.UTF-8' },
        timeout: Math.max(1, Math.floor(Math.min(4000, timeout))),
        maxBuffer: 65536,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
      });
    } catch (error) {
      if (args[0] === 'inspect')
        retainNativePrimaryFailure(info.annotations, 'SUBJECT_OTP', 'DOCKER_INSPECT', error);
      throw new Error('Owned OTP container lookup failed');
    }
  };
  const ids = docker(local ? ['compose', '-p', local.project, '-f', local.composeFile, 'ps', '-q', 'rabbitmq'] : [
    'compose',
    '--env-file',
    envFile,
    '-p',
    'rct-e2e',
    '-f',
    join(root, 'docker-compose.e2e.yml'),
    'ps',
    '-q',
    'rabbitmq',
  ])
    .trim()
    .split(/\s+/);
  requireValue(
    ids.length === 1 && /^[0-9a-f]{12,64}$/.test(ids[0]),
    'Exactly one owned OTP broker is required',
  );
  const inspect = (timeout = 4000) => {
    let raw: string;
    try {
      raw = docker(
        [
          'inspect',
          '--format',
          '{"id":{{json .Id}},"image":{{json .Image}},"imageName":{{json .Config.Image}},"labels":{{json .Config.Labels}},"running":{{json .State.Running}},"health":{{json .State.Health.Status}},"mounts":{{json .Mounts}},"networks":{{json .NetworkSettings.Networks}}}',
          ids[0],
        ],
        timeout,
      );
    } catch {
      throw new Error('Owned OTP broker inspection failed');
    }
    try {
      return JSON.parse(raw) as Record<string, any>;
    } catch (error) {
      retainNativePrimaryFailure(info.annotations, 'SUBJECT_OTP', 'JSON_PARSE', error);
      throw new Error('Owned OTP broker inspection failed');
    }
  };
  const pin = inspect();
  if (local) requireValue(localSpecialService(local, 'rabbitmq').id === pin.id, 'Local subject broker mismatch');
  requireValue(
    /^[0-9a-f]{64}$/.test(pin.id) &&
      /^sha256:[0-9a-f]{64}$/.test(pin.image) &&
      pin.imageName === 'rabbitmq:3.13-management-alpine' &&
      pin.labels?.['com.docker.compose.project'] === (local?.project ?? 'rct-e2e') &&
      pin.labels?.['com.docker.compose.service'] === 'rabbitmq' &&
      pin.running === true &&
      pin.health === 'healthy',
    'Owned OTP broker identity is invalid',
  );
  const network = local ? pin.networks?.[local.networkName] : pin.networks?.rct_e2e_private_net ?? pin.networks?.['rct-e2e_private_net'];
  const ip = network?.IPAddress;
  requireValue(
    typeof ip === 'string' &&
      isIP(ip) === 4 &&
      /^(?:10\.|172\.(?:1[6-9]|2\d|3[01])\.|192\.168\.)\d/.test(ip),
    'Owned private OTP broker address is required',
  );
  const identity = (timeout = 4000) => {
    const current = inspect(timeout);
    requireValue(
      current.id === pin.id &&
        current.image === pin.image &&
        current.imageName === pin.imageName &&
        current.running === true &&
        current.health === 'healthy' &&
        JSON.stringify(current.labels) === JSON.stringify(pin.labels) &&
        JSON.stringify(mountRecords(current.mounts)) === JSON.stringify(mountRecords(pin.mounts)) &&
        JSON.stringify(current.networks) === JSON.stringify(pin.networks),
      'Owned OTP broker identity changed',
    );
  };
  const queue = `rct-subject-delete-${randomUUID()}`;
  const queuePath = `queues/%2F/${encodeURIComponent(queue)}`;
  let attempted = false;
  let closing = false;
  let inFlight: Promise<{ status: number; body: any }> | null = null;
  let totalBytes = 0;
  let cleanupBytes = 0;
  const request = async (
    method: string,
    path: string,
    statuses: number[],
    data?: unknown,
    timeout = 4000,
    cleaning = false,
  ): Promise<{ status: number; body: any }> => {
    const end = performance.now() + Math.max(1, Math.min(4000, timeout));
    identity(end - performance.now());
    const remaining = Math.floor(end - performance.now());
    requireValue(remaining > 0, 'Owned OTP broker control deadline expired');
    let response: globalThis.Response;
    try {
      response = await fetch(`http://${ip}:15672/api/${path}`, {
        method,
        redirect: 'error',
        signal: AbortSignal.timeout(remaining),
        headers: {
          Authorization: `Basic ${Buffer.from(`${secret.RABBITMQ_USER}:${secret.RABBITMQ_PASSWORD}`).toString('base64')}`,
          'Content-Type': 'application/json',
        },
        ...(data === undefined ? {} : { body: JSON.stringify(data) }),
      });
    } catch {
      throw new Error('Owned OTP broker control unavailable');
    }
    requireValue(statuses.includes(response.status), 'Owned OTP broker control rejected');
    const chunks: Uint8Array[] = [];
    let size = 0;
    const reader = response.body?.getReader();
    if (reader) {
      try {
        for (;;) {
          const part = await reader.read();
          if (part.done) break;
          size += part.value.length;
          if (cleaning) cleanupBytes += part.value.length;
          else totalBytes += part.value.length;
          requireValue(
            size <= 16384 && (cleaning ? cleanupBytes <= 32768 : totalBytes <= 262144),
            'Owned OTP broker response exceeds its bound',
          );
          chunks.push(part.value);
        }
      } finally {
        await reader.cancel().catch(() => {});
      }
    }
    try {
      return {
        status: response.status,
        body: size === 0 ? null : JSON.parse(Buffer.concat(chunks).toString('utf8')),
      };
    } catch {
      throw new Error('Owned OTP broker returned an invalid response');
    }
  };
  const call = (...args: Parameters<typeof request>) => {
    requireValue(args[5] === true || !closing, 'Owned OTP receiver is closing');
    requireValue(inFlight === null, 'Owned OTP broker calls must remain serial');
    const operation = request(...args);
    inFlight = operation;
    const settled = () => {
      if (inFlight === operation) inFlight = null;
    };
    void operation.then(settled, settled);
    return operation;
  };
  return {
    async bind() {
      await call('GET', queuePath, [404]);
      requireValue(!closing, 'Owned OTP receiver is closing');
      attempted = true;
      await call('PUT', queuePath, [201], { durable: false, auto_delete: false, arguments: {} });
      await call('POST', `bindings/%2F/e/rut-uit.events/q/${encodeURIComponent(queue)}`, [201], {
        routing_key: '',
        arguments: {},
      });
    },
    async receive(telegramId: number, challengeId: string, attempts: number, deadline: number) {
      const end = Math.min(performance.now() + 30000, deadline);
      let messages = 0;
      for (let polls = 0; polls < 120 && performance.now() < end; polls += 1) {
        const result = await call(
          'POST',
          `${queuePath}/get`,
          [200],
          { count: 1, ackmode: 'ack_requeue_false', encoding: 'auto', truncate: 8192 },
          end - performance.now(),
        );
        requireValue(!closing, 'Owned OTP receiver is closing');
        requireValue(
          Array.isArray(result.body) && result.body.length <= 1,
          'Owned OTP queue response shape is invalid',
        );
        for (const message of result.body) {
          messages += 1;
          requireValue(
            messages <= 64 &&
              message.payload_encoding === 'string' &&
              typeof message.payload === 'string' &&
              Buffer.byteLength(message.payload) <= 8192,
            'Owned OTP queue delivery exceeds its bound',
          );
          let event: Record<string, any>;
          try {
            event = JSON.parse(message.payload);
          } catch {
            throw new Error('Owned OTP event is malformed');
          }
          requireValue(
            event !== null && typeof event === 'object' && !Array.isArray(event),
            'Owned OTP event shape is invalid',
          );
          if (
            event.event_type !== 'otp.requested' ||
            event.source !== 'auth-service' ||
            event.payload?.purpose !== 'subject_delete' ||
            event.payload?.challenge_id !== challengeId ||
            event.payload?.telegram_id !== telegramId
          )
            continue;
          requireValue(
            Number.isSafeInteger(event.payload.ttl_seconds) &&
              event.payload.ttl_seconds > 0 &&
              event.payload.attempts_remaining === attempts &&
              Number.isSafeInteger(event.payload.attempts_remaining) &&
              event.payload.attempts_remaining > 0 &&
              typeof event.payload.code === 'string' &&
              /^[0-9]{6}$/.test(event.payload.code),
            'Owned subject-deletion OTP contract is invalid',
          );
          return {
            code: event.payload.code as string,
            attemptsRemaining: event.payload.attempts_remaining as number,
          };
        }
        const remaining = end - performance.now();
        if (remaining > 0) await delay(Math.min(250, remaining));
      }
      throw new Error('Own subject-deletion OTP was not delivered within its bound');
    },
    async close() {
      closing = true;
      // Leave teardown overhead inside the existing separately awaited 30s fixture slot.
      const end = performance.now() + 25000;
      // The original caller still owns its result; cleanup independently settles its I/O.
      if (inFlight) await Promise.allSettled([inFlight]);
      if (!attempted) return;
      for (;;) {
        const remaining = Math.floor(end - performance.now());
        requireValue(remaining > 0, 'Owned OTP queue cleanup deadline expired');
        const existing = await call('GET', queuePath, [200, 404], undefined, remaining, true);
        if (existing.status === 404) return;
        requireValue(existing.body?.name === queue, 'Owned OTP queue name must match');
        requireValue(existing.body.durable === false, 'Owned OTP queue must be non-durable');
        requireValue(existing.body.auto_delete === false, 'Owned OTP queue must not auto-delete');
        requireValue(existing.body.exclusive === false, 'Owned OTP queue must not be exclusive');
        requireValue(
          existing.body.arguments !== null &&
            typeof existing.body.arguments === 'object' &&
            !Array.isArray(existing.body.arguments) &&
            Object.keys(existing.body.arguments).length === 0,
          'Owned OTP queue declared arguments must remain empty',
        );
        // Management counts are sampled: wait only for absence, never infer zero.
        if (!Object.hasOwn(existing.body, 'consumers')) {
          const waitRemaining = Math.floor(end - performance.now());
          requireValue(waitRemaining > 0, 'Owned OTP queue consumer count is absent');
          await delay(Math.min(250, waitRemaining));
          continue;
        }
        requireValue(
          Object.hasOwn(existing.body, 'consumers'),
          'Owned OTP queue consumer count is absent',
        );
        requireValue(
          typeof existing.body.consumers === 'number',
          'Owned OTP queue consumer count is not numeric',
        );
        requireValue(
          Number.isSafeInteger(existing.body.consumers) && existing.body.consumers >= 0,
          'Owned OTP queue consumer count is not a nonnegative safe integer',
        );
        requireValue(existing.body.consumers === 0, 'Owned OTP queue must have no consumers');
        const deleteRemaining = Math.floor(end - performance.now());
        requireValue(deleteRemaining > 0, 'Owned OTP queue cleanup deadline expired');
        await call(
          'DELETE',
          `${queuePath}?if-unused=true`,
          [204, 404],
          undefined,
          deleteRemaining,
          true,
        );
        return;
      }
    },
  };
}

const subjectCascadeTest = test.extend<{
  subjectCascade: {
    request: APIRequestContext;
    otp: ReturnType<typeof subjectCascadeOtpReceiver>;
  };
}>({
  subjectCascade: [
    async ({ playwright }, use, info) => {
      requirePrivateArtifacts(info);
      const otp = subjectCascadeOtpReceiver(info);
      const request = await playwright.request.newContext({
        baseURL: 'https://localhost:8443',
        ignoreHTTPSErrors: false,
        timeout: 10000,
      });
      try {
        await use({ request, otp });
      } finally {
        // The separate fixture slot is awaited after a body timeout as well.
        // Independent resources settle together: a slow protocol disposal cannot consume the
        // queue's separately awaited ownership/if-unused cleanup slot.
        const [requestCleanup, queueCleanup] = await Promise.allSettled([
          request.dispose(),
          otp.close(),
        ]);
        expect
          .soft(
            requestCleanup.status === 'fulfilled',
            'Subject deletion request context cleanup succeeds',
          )
          .toBe(true);
        expect
          .soft(queueCleanup.status === 'fulfilled', 'Subject deletion owned OTP cleanup succeeds')
          .toBe(true);
      }
    },
    { timeout: 30000 },
  ],
});

/** Read-only, exact owned-container projections; no SQL/Mongo domain seeding or cleanup. */
function subjectCascadeReadback(deadline: number) {
  const root = resolve(__dirname, '../../..');
  const run = process.env.RCT_SEMESTER_LIFECYCLE_RUN;
  requireValue(!local || run === local.laneRun, 'Local semester checkpoint mismatch');
  requireValue(run && isAbsolute(run), 'Subject deletion requires the private semester lane');
  for (const directory of [run, join(run, 'artifacts')]) {
    const stat = lstatSync(directory);
    requireValue(
      stat.isDirectory() && !stat.isSymbolicLink() && (stat.mode & 0o777) === 0o700,
      'Subject deletion directories must be private',
    );
  }
  const marker = join(run, 'disposable-target');
  requireValue(
    lstatSync(marker).isFile() &&
      !lstatSync(marker).isSymbolicLink() &&
      (lstatSync(marker).mode & 0o777) === 0o600 &&
      lstatSync(marker).size <= 64 &&
      readFileSync(marker, 'utf8').trim() === 'RCT_SEMESTER_DISPOSABLE',
    'Subject deletion stand ownership must match',
  );
  const envFile = (local ? join(local.run, 'synthetic.env') : join(root, 'tests/e2e/.env.ci'));
  const stat = lstatSync(envFile);
  requireValue(
    stat.isFile() && !stat.isSymbolicLink() && stat.size <= 65536 && (stat.mode & 0o022) === 0,
    'Subject deletion CI configuration must be bounded',
  );
  const wanted = [
    'POSTGRES_ACADEMIC_PASSWORD',
    'POSTGRES_SCHEDULE_PASSWORD',
    'MONGO_ROOT_PASSWORD',
  ];
  const secret: Record<string, string> = {};
  for (const line of readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const match = /^([A-Z_]+)=(.*)$/.exec(line);
    if (!match || !wanted.includes(match[1])) continue;
    let value = match[2];
    if (value.length >= 2 && ['"', "'"].includes(value[0]) && value.at(-1) === value[0])
      value = value.slice(1, -1);
    requireValue(
      value.length > 0 && value.length <= 1024 && secret[match[1]] === undefined,
      'Subject deletion CI credential shape must match',
    );
    secret[match[1]] = value;
  }
  requireValue(
    wanted.every((name) => secret[name]),
    'Subject deletion CI credentials are required',
  );
  type MongoFailureFamily =
    | 'AUTH'
    | 'MONGO_SELECTION'
    | 'MONGO_NETWORK'
    | 'MONGO_PARSE'
    | 'MONGO_SERVER'
    | 'JS_REFERENCE'
    | 'JS_TYPE'
    | 'JS_SYNTAX'
    | 'DOCKER_EXEC'
    | 'AMBIGUOUS'
    | 'UNKNOWN';
  type MongoFailureSite =
    | 'CONNECT'
    | 'TICKET'
    | 'EPOCH'
    | 'COUNT'
    | 'CURSOR'
    | 'HISTORY'
    | 'HISTORY_ORIGINAL_COUNT'
    | 'HISTORY_EFFECTIVE_IDS'
    | 'EFFECTIVE_IDS_UNDEFINED'
    | 'EFFECTIVE_IDS_NULL'
    | 'EFFECTIVE_IDS_ARRAY'
    | 'EFFECTIVE_IDS_OBJECT'
    | 'EFFECTIVE_IDS_OTHER'
    | 'HISTORY_ADJUSTMENTS'
    | 'HISTORY_REMOVED'
    | 'HISTORY_REMAINING'
    | 'BUDGET'
    | 'OUTPUT'
    | 'UNKNOWN';
  const mongoFailureFamily = (
    error: unknown,
    site: { value: MongoFailureSite },
  ): MongoFailureFamily => {
    try {
      if (error === null || typeof error !== 'object') return 'UNKNOWN';
      const text: string[] = [];
      let bytes = 0;
      const byteLength = Object.getOwnPropertyDescriptor(
        Object.getPrototypeOf(Uint8Array.prototype),
        'byteLength',
      )?.get;
      for (const name of ['stdout', 'stderr']) {
        const descriptor = Object.getOwnPropertyDescriptor(error, name);
        if (!descriptor || !('value' in descriptor)) return 'UNKNOWN';
        const value: unknown = descriptor.value;
        if (typeof value === 'string') {
          bytes += Buffer.byteLength(value, 'utf8');
          if (bytes > 65536) return 'UNKNOWN';
          text.push(value);
        } else if (Buffer.isBuffer(value) && byteLength) {
          const length: unknown = Reflect.apply(byteLength, value, []);
          if (typeof length !== 'number' || !Number.isSafeInteger(length) || length < 0)
            return 'UNKNOWN';
          bytes += length;
          if (bytes > 65536) return 'UNKNOWN';
          text.push(
            new TextDecoder('utf-8', { fatal: true }).decode(value as Uint8Array<ArrayBuffer>),
          );
        } else return 'UNKNOWN';
      }
      const lines = text.join('\n').split(/\r?\n/);
      if (
        lines.length > 200 ||
        text.some((value) => {
          for (const character of value) {
            const code = character.charCodeAt(0);
            if (
              (code < 32 && code !== 9 && code !== 10 && code !== 13) ||
              code === 127 ||
              code === 65533
            )
              return true;
          }
          return false;
        })
      )
        return 'UNKNOWN';
      const markers = lines.filter((line) => line.trim().startsWith('RCT_SUBJECT_READBACK_SITE:'));
      if (markers.length === 1) {
        const match =
          /^RCT_SUBJECT_READBACK_SITE:(CONNECT|TICKET|EPOCH|COUNT|CURSOR|HISTORY|HISTORY_ORIGINAL_COUNT|HISTORY_EFFECTIVE_IDS|EFFECTIVE_IDS_UNDEFINED|EFFECTIVE_IDS_NULL|EFFECTIVE_IDS_ARRAY|EFFECTIVE_IDS_OBJECT|EFFECTIVE_IDS_OTHER|HISTORY_ADJUSTMENTS|HISTORY_REMOVED|HISTORY_REMAINING|BUDGET|OUTPUT)$/.exec(
            markers[0],
          );
        if (match) site.value = match[1] as MongoFailureSite;
      }
      const families = new Set<MongoFailureFamily>();
      let bareServerParent = false;
      for (const entry of lines) {
        const line = entry.trim();
        if (/^MongoServerError: Authentication failed\.?$/.test(line)) families.add('AUTH');
        else if (/^MongoServerSelectionError:/.test(line)) families.add('MONGO_SELECTION');
        else if (/^MongoNetwork(?:Timeout)?Error:/.test(line)) families.add('MONGO_NETWORK');
        else if (/^MongoParseError:/.test(line)) families.add('MONGO_PARSE');
        else if (/^MongoServerError:?$/.test(line)) bareServerParent = true;
        else if (/^MongoServerError:/.test(line)) families.add('MONGO_SERVER');
        else if (/^ReferenceError:/.test(line)) families.add('JS_REFERENCE');
        else if (/^TypeError:/.test(line)) families.add('JS_TYPE');
        else if (/^SyntaxError:/.test(line)) families.add('JS_SYNTAX');
        else if (/^(?:OCI runtime exec failed:|Error response from daemon:)/.test(line))
          families.add('DOCKER_EXEC');
      }
      if (bareServerParent && !families.has('AUTH')) families.add('MONGO_SERVER');
      if (families.size > 1) return 'AMBIGUOUS';
      return families.values().next().value ?? 'UNKNOWN';
    } catch {
      return 'UNKNOWN';
    }
  };
  let readbackNativeFailure:
    | {
        error: Error;
        reason: 'TIMEOUT' | 'OUTPUT_BOUND' | 'EXIT_NONZERO' | 'UNKNOWN';
        family: MongoFailureFamily;
        site: MongoFailureSite;
      }
    | undefined;
  const docker = (args: string[], input?: string, extra: Record<string, string> = {}) => {
    const remaining = Math.floor(deadline - performance.now());
    requireValue(remaining > 0, 'Subject deletion readback deadline remains');
    try {
      return execFileSync('docker', args, {
        cwd: root,
        env: { PATH: process.env.PATH ?? '/usr/bin:/bin', LANG: 'C.UTF-8', ...extra },
        input,
        timeout: Math.min(4000, remaining),
        maxBuffer: 65536,
        encoding: 'utf8',
        stdio: ['pipe', 'pipe', 'pipe'],
      });
    } catch (error) {
      let code: unknown;
      let status: unknown;
      try {
        if (error !== null && typeof error === 'object') {
          code = Object.getOwnPropertyDescriptor(error, 'code')?.value;
          status = Object.getOwnPropertyDescriptor(error, 'status')?.value;
        }
      } catch {
        // Uninspectable errors stay UNKNOWN; never read native output or accessors.
      }
      const failure = new Error('Subject deletion owned readback failed');
      const reason =
        code === 'ETIMEDOUT'
          ? 'TIMEOUT'
          : code === 'ENOBUFS'
            ? 'OUTPUT_BOUND'
            : typeof status === 'number' &&
                Number.isSafeInteger(status) &&
                status > 0 &&
                status <= 255
              ? 'EXIT_NONZERO'
              : 'UNKNOWN';
      const selectedMongo = [
        'exec',
        '-i',
        '-e',
        'RCT_MONGO_PASSWORD',
        pins['mongo-attendance']?.id,
        'mongosh',
        '--quiet',
        '--nodb',
        '--file',
        '/dev/stdin',
      ];
      const site: { value: MongoFailureSite } = { value: 'UNKNOWN' };
      readbackNativeFailure = {
        error: failure,
        reason,
        family:
          reason === 'EXIT_NONZERO' &&
          args.length === selectedMongo.length &&
          args.every((value, index) => value === selectedMongo[index])
            ? mongoFailureFamily(error, site)
            : 'UNKNOWN',
        site: site.value,
      };
      throw failure;
    }
  };
  const compose = local ? ['compose', '-p', local.project, '-f', local.composeFile] : [
    'compose',
    '--env-file',
    envFile,
    '-p',
    'rct-e2e',
    '-f',
    join(root, 'docker-compose.e2e.yml'),
  ];
  const pins: Record<string, Row> = {};
  const inspect = (id: string): Row => {
    try {
      return JSON.parse(
        docker([
          'inspect',
          '--format',
          '{"id":{{json .Id}},"image":{{json .Image}},"labels":{{json .Config.Labels}},"running":{{json .State.Running}},"health":{{json .State.Health.Status}},"mounts":{{json .Mounts}},"networks":{{json .NetworkSettings.Networks}}}',
          id,
        ]),
      );
    } catch {
      throw new Error('Subject deletion container inspection failed');
    }
  };
  for (const service of ['postgres-academic', 'postgres-schedule', 'mongo-attendance'] as const) {
    const ids = docker([...compose, 'ps', '-q', service])
      .trim()
      .split(/\s+/);
    requireValue(
      ids.length === 1 && /^[0-9a-f]{12,64}$/.test(ids[0]),
      'Subject deletion requires one owned database',
    );
    const pin = inspect(ids[0]);
    if (local) requireValue(localSpecialService(local, service).id === pin.id, 'Local subject database mismatch');
    requireValue(
      /^[0-9a-f]{64}$/.test(pin.id) &&
        /^sha256:[0-9a-f]{64}$/.test(pin.image) &&
        pin.labels?.['com.docker.compose.project'] === (local?.project ?? 'rct-e2e') &&
        pin.labels?.['com.docker.compose.service'] === service &&
        pin.running === true &&
        pin.health === 'healthy',
      'Subject deletion database identity must match',
    );
    pins[service] = pin;
  }
  const identity = (service: OwnedContainerIdentityFailure['service']) => {
    const pin = pins[service];
    requireOwnedContainerIdentity(
      inspect(pin.id),
      pin,
      'subjectdelete',
      service,
      'Subject deletion database identity changed',
    );
    return pin.id as string;
  };
  const pg = (kind: 'academic' | 'schedule', sql: string): Row => {
    const service = `postgres-${kind}` as const;
    const raw = docker(
      [
        'exec',
        '-i',
        '-e',
        'PGPASSWORD',
        identity(service),
        'psql',
        '-X',
        '-qAt',
        '-U',
        'rct_user',
        '-d',
        `${kind}_db`,
        '-v',
        'ON_ERROR_STOP=1',
      ],
      `BEGIN READ ONLY; SET LOCAL statement_timeout='2000ms'; ${sql}; ROLLBACK;`,
      {
        PGPASSWORD:
          secret[kind === 'academic' ? 'POSTGRES_ACADEMIC_PASSWORD' : 'POSTGRES_SCHEDULE_PASSWORD'],
      },
    );
    const lines = raw
      .trim()
      .split('\n')
      .filter((line) => !['BEGIN', 'SET', 'ROLLBACK'].includes(line));
    requireValue(lines.length === 1, 'Subject deletion needs one bounded SQL projection');
    try {
      return JSON.parse(lines[0]);
    } catch {
      throw new Error('Subject deletion SQL projection is invalid');
    }
  };
  return (
    scope: {
      operation: string;
      subject: number;
      control: number;
      semester: number;
      group: number;
      student: number;
      mixed: string;
      sole: string;
      manual: string;
      removed: number[];
      kept: number;
    },
    phase: 'BEFORE' | 'AFTER' | 'REPLAY',
  ) => {
    requireValue(
      /^[0-9a-f-]{36}$/.test(scope.operation) &&
        /^[a-f0-9]{24}$/.test(scope.mixed) &&
        /^[a-f0-9]{24}$/.test(scope.sole) &&
        /^[a-f0-9]{24}$/.test(scope.manual) &&
        [
          scope.subject,
          scope.control,
          scope.semester,
          scope.group,
          scope.student,
          scope.kept,
          ...scope.removed,
        ].every((id) => Number.isSafeInteger(id) && id > 0),
      'Subject deletion readback scope is exact',
    );
    const academic = pg(
      'academic',
      `SELECT json_build_object(
      'operationCount',(SELECT count(*) FROM subject_deletion_operations WHERE subject_id=${scope.subject}),
      'operation',(SELECT json_build_object('phase',phase,'schedule',schedule_state,'attendance',attendance_state) FROM subject_deletion_operations WHERE operation_id='${scope.operation}'::uuid),
      'subjectRows',(SELECT count(*) FROM subjects WHERE id=${scope.subject}),
      'homeworkRows',(SELECT count(*) FROM homeworks WHERE subject_id=${scope.subject}),
      'controlRows',(SELECT count(*) FROM subjects WHERE id=${scope.control} AND group_id=${scope.group} AND semester_id=${scope.semester}),
      'deletedEvents',(SELECT count(*) FROM academic_outbox WHERE event_type='subject.deleted' AND payload->'payload'->>'subject_id'='${scope.subject}'))`,
    );
    const schedule = pg(
      'schedule',
      `SELECT json_build_object(
      'participantCount',(SELECT count(*) FROM schedule_subject_deletion_participants WHERE subject_id=${scope.subject}),
      'state',(SELECT state FROM schedule_subject_deletion_participants WHERE operation_id='${scope.operation}'::uuid),
      'lessonRows',(SELECT count(*) FROM lessons WHERE subject_id=${scope.subject}),
      'controlRows',(SELECT count(*) FROM lessons WHERE id=${scope.kept} AND subject_id=${scope.control} AND semester_id=${scope.semester} AND group_id=${scope.group}),
      'deletedEvents',(SELECT count(*) FROM schedule_outbox WHERE event_type='lesson.deleted' AND payload->'payload'->'lesson_ids' @> '[${scope.removed[0]}]'::jsonb))`,
    );
    const js = `const k=${JSON.stringify(scope)};
let rctSubjectReadbackSite='CONNECT';
try {
const d=connect('mongodb://root:'+encodeURIComponent(process.env.RCT_MONGO_PASSWORD)+'@localhost:27017/attendance_db?authSource=admin&replicaSet=rs0');
const count=(c,q)=>(rctSubjectReadbackSite='COUNT',d.getCollection(c).countDocuments(q,{maxTimeMS:2000}));
rctSubjectReadbackSite='TICKET';
const t=d.excuse_tickets.findOne({_id:new ObjectId(k.mixed)},{data:0});
rctSubjectReadbackSite='EPOCH';
const e=d.subject_deletion_participants.findOne({_id:k.operation},{state:1,subject_id:1,semester_id:1,group_id:1});
rctSubjectReadbackSite='HISTORY';
const h=t?.subject_coverage_adjustments||[];
print(JSON.stringify({epoch:(rctSubjectReadbackSite='EPOCH',e?{state:e.state,subject:Number(e.subject_id),semester:Number(e.semester_id),group:Number(e.group_id)}:null),
epochCount:count('subject_deletion_participants',{subject_id:k.subject}),
marks:count('attendances',{lesson_id:{$in:k.removed}}),
sole:count('excuse_tickets',{_id:new ObjectId(k.sole)}),manual:count('late_checkin_requests',{_id:new ObjectId(k.manual)}),
soleFiles:count('request_attachments',{request_id:k.sole}),mixedFiles:count('request_attachments',{request_id:k.mixed}),
controlMarks:(rctSubjectReadbackSite='CURSOR',d.attendances.find({lesson_id:k.kept,user_id:k.student}).maxTimeMS(2000).limit(2).toArray()),
originalCount:(rctSubjectReadbackSite='HISTORY_ORIGINAL_COUNT',t?.lesson_snapshots?.length),effectiveIds:(rctSubjectReadbackSite='HISTORY_EFFECTIVE_IDS',(()=>{
const value=t===null||t===undefined?undefined:t.effective_original_lesson_ids;
try {
rctSubjectReadbackSite=value===undefined?'EFFECTIVE_IDS_UNDEFINED':value===null?'EFFECTIVE_IDS_NULL':Array.isArray(value)?'EFFECTIVE_IDS_ARRAY':typeof value==='object'?'EFFECTIVE_IDS_OBJECT':'EFFECTIVE_IDS_OTHER';
} catch {}
return value===null||value===undefined?undefined:value.map(Number);
})()),
history:(rctSubjectReadbackSite='HISTORY_ADJUSTMENTS',h.map(x=>({operation:x.operation_id,subject:Number(x.subject_id),actor:Number(x.actor_id),removed:(rctSubjectReadbackSite='HISTORY_REMOVED',x.removed_lesson_ids.map(Number)),remaining:(rctSubjectReadbackSite='HISTORY_REMAINING',x.remaining_lesson_ids.map(Number))}))),
budget:(rctSubjectReadbackSite='BUDGET',d.student_late_checkin_budgets.findOne({student_id:k.student,semester_id:k.semester},{_id:0,limit:1,used:1})),
...(rctSubjectReadbackSite='OUTPUT',{})}));
} catch(error) {
try { print('RCT_SUBJECT_READBACK_SITE:'+rctSubjectReadbackSite); } catch {}
throw error;
}`;
    let attendance: Row;
    let mongoStage: 'IDENTITY' | 'COMMAND' | 'PARSE' = 'IDENTITY';
    try {
      const id = identity('mongo-attendance');
      mongoStage = 'COMMAND';
      const raw = docker(
        [
          'exec',
          '-i',
          '-e',
          'RCT_MONGO_PASSWORD',
          id,
          'mongosh',
          '--quiet',
          '--nodb',
          '--file',
          '/dev/stdin',
        ],
        js,
        { RCT_MONGO_PASSWORD: secret.MONGO_ROOT_PASSWORD },
      );
      mongoStage = 'PARSE';
      attendance = JSON.parse(raw);
    } catch (error) {
      let failure: Error;
      let family: MongoFailureFamily | undefined;
      let site: MongoFailureSite = 'UNKNOWN';
      if (mongoStage === 'IDENTITY') {
        failure = new Error('Subject deletion Mongo projection is invalid');
      } else if (mongoStage === 'PARSE') {
        failure = new Error('Subject deletion Mongo projection is invalid');
      } else if (
        readbackNativeFailure !== undefined &&
        readbackNativeFailure.error === error &&
        readbackNativeFailure.reason === 'TIMEOUT'
      ) {
        failure = new Error('Subject deletion Mongo projection is invalid');
      } else if (
        readbackNativeFailure !== undefined &&
        readbackNativeFailure.error === error &&
        readbackNativeFailure.reason === 'OUTPUT_BOUND'
      ) {
        failure = new Error('Subject deletion Mongo projection is invalid');
      } else if (
        readbackNativeFailure !== undefined &&
        readbackNativeFailure.error === error &&
        readbackNativeFailure.reason === 'EXIT_NONZERO'
      ) {
        family = readbackNativeFailure.family;
        site = readbackNativeFailure.site;
        failure = new Error('Subject deletion Mongo projection is invalid');
      } else {
        failure = new Error('Subject deletion Mongo projection is invalid');
      }
      try {
        if (phase === 'BEFORE') {
          expect.soft(false, 'SUBJECT_MONGO_BEFORE').toBe(true);
        } else if (phase === 'AFTER') {
          expect.soft(false, 'SUBJECT_MONGO_AFTER').toBe(true);
        } else if (phase === 'REPLAY') {
          expect.soft(false, 'SUBJECT_MONGO_REPLAY').toBe(true);
        }
        if (family !== undefined) {
          switch (family) {
            case 'AUTH':
              expect.soft(false, 'SUBJECT_MONGO_AUTH').toBe(true);
              break;
            case 'MONGO_SELECTION':
              expect.soft(false, 'SUBJECT_MONGO_SELECTION').toBe(true);
              break;
            case 'MONGO_NETWORK':
              expect.soft(false, 'SUBJECT_MONGO_NETWORK').toBe(true);
              break;
            case 'MONGO_PARSE':
              expect.soft(false, 'SUBJECT_MONGO_URI_PARSE').toBe(true);
              break;
            case 'MONGO_SERVER':
              expect.soft(false, 'SUBJECT_MONGO_SERVER').toBe(true);
              break;
            case 'JS_REFERENCE':
              expect.soft(false, 'SUBJECT_MONGO_JS_REFERENCE').toBe(true);
              break;
            case 'JS_TYPE':
              expect.soft(false, 'SUBJECT_MONGO_JS_TYPE').toBe(true);
              switch (site) {
                case 'CONNECT':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_CONNECT').toBe(true);
                  break;
                case 'TICKET':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_TICKET').toBe(true);
                  break;
                case 'EPOCH':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_EPOCH').toBe(true);
                  break;
                case 'COUNT':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_COUNT').toBe(true);
                  break;
                case 'CURSOR':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_CURSOR').toBe(true);
                  break;
                case 'HISTORY':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_HISTORY').toBe(true);
                  break;
                case 'HISTORY_ORIGINAL_COUNT':
                  expect
                    .soft(false, 'SUBJECT_MONGO_REPORTED_SITE_HISTORY_ORIGINAL_COUNT')
                    .toBe(true);
                  break;
                case 'HISTORY_EFFECTIVE_IDS':
                  expect
                    .soft(false, 'SUBJECT_MONGO_REPORTED_SITE_HISTORY_EFFECTIVE_IDS')
                    .toBe(true);
                  break;
                case 'EFFECTIVE_IDS_UNDEFINED':
                  expect
                    .soft(false, 'SUBJECT_MONGO_REPORTED_SITE_EFFECTIVE_IDS_UNDEFINED')
                    .toBe(true);
                  break;
                case 'EFFECTIVE_IDS_NULL':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_EFFECTIVE_IDS_NULL').toBe(true);
                  break;
                case 'EFFECTIVE_IDS_ARRAY':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_EFFECTIVE_IDS_ARRAY').toBe(true);
                  break;
                case 'EFFECTIVE_IDS_OBJECT':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_EFFECTIVE_IDS_OBJECT').toBe(true);
                  break;
                case 'EFFECTIVE_IDS_OTHER':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_EFFECTIVE_IDS_OTHER').toBe(true);
                  break;
                case 'HISTORY_ADJUSTMENTS':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_HISTORY_ADJUSTMENTS').toBe(true);
                  break;
                case 'HISTORY_REMOVED':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_HISTORY_REMOVED').toBe(true);
                  break;
                case 'HISTORY_REMAINING':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_HISTORY_REMAINING').toBe(true);
                  break;
                case 'BUDGET':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_BUDGET').toBe(true);
                  break;
                case 'OUTPUT':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_OUTPUT').toBe(true);
                  break;
                case 'UNKNOWN':
                  expect.soft(false, 'SUBJECT_MONGO_REPORTED_SITE_UNKNOWN').toBe(true);
                  break;
              }
              break;
            case 'JS_SYNTAX':
              expect.soft(false, 'SUBJECT_MONGO_JS_SYNTAX').toBe(true);
              break;
            case 'DOCKER_EXEC':
              expect.soft(false, 'SUBJECT_MONGO_DOCKER_EXEC').toBe(true);
              break;
            case 'AMBIGUOUS':
              expect.soft(false, 'SUBJECT_MONGO_FAMILY_AMBIGUOUS').toBe(true);
              break;
            case 'UNKNOWN':
              expect.soft(false, 'SUBJECT_MONGO_FAMILY_UNKNOWN').toBe(true);
              break;
          }
        }
      } catch {
        // Diagnostic failure cannot replace this same fixed primary error.
      }
      throw failure;
    }
    return { academic, schedule, attendance };
  };
}

subjectCascadeTest.describe('full subject deletion through the owned stand', () => {
  const test = subjectCascadeTest;
  // New separate slot; neither existing semester test changes its 420s budget.
  test.setTimeout(420000);
  test('HEADMAN deletes one subject through real proof and participants while mixed EXCUSE keeps the control subject', async ({
    subjectCascade,
  }, info) => {
    localSpecialLane('subject-deletion');
    const { request, otp } = subjectCascade;
    const mainEnd = performance.now() + 330000;
    const remaining = (cap = 10000) => {
      const left = Math.floor(mainEnd - performance.now());
      requireValue(left > 0, 'Subject deletion main-work deadline remains');
      return Math.min(left, cap);
    };
    const clock = unchangedClock();
    let primaryFailed = false;
    try {
      const admin = await admit(request, TEST_USERS.admin, 'ADMIN');
      const access = await admin.call('GET', '/api/academic/admin-access/me');
      requireValue(
        access.mode === 'FULL' &&
          access.assigned === true &&
          String(access.userId) === admin.session.userId,
        'Subject deletion requires actual FULL synthetic fixture admission',
      );
      const originalSemesters = await semesters(admin);
      requireValue(
        originalSemesters.length < 100 &&
          originalSemesters.every(
            (row) =>
              row.transition === 'NONE' &&
              row.releasePending === false &&
              row.activationScheduled === false,
          ),
        'Subject deletion requires quiescent known semester authority',
      );
      const datasetAdmin = new (class extends Actor {
        override async call(...args: Parameters<Actor['call']>) {
          requireValue(
            !(args[0] === 'POST' && args[1] === '/api/academic/semesters') &&
              !(
                args[0] === 'PATCH' && /^\/api\/academic\/semesters\/[^/]+\/activate$/.test(args[1])
              ),
            'Subject deletion cannot create or activate a fallback semester',
          );
          remaining();
          return super.call(...args);
        }
      })(request, admin.token, admin.session);
      const dataset = new MobileDataset(request, datasetAdmin, clock.instant);
      const pastDate = shift(dataset.today, -1);
      const active = originalSemesters.filter((row) => row.active === true);
      requireValue(
        active.length === 1 &&
          active[0].archived === false &&
          active[0].dateFrom <= pastDate &&
          active[0].dateTo >= dataset.tomorrow,
        'Subject deletion current semester covers the owned past and future lessons',
      );
      const readback = subjectCascadeReadback(mainEnd);
      await dataset.initialize();
      requireValue(
        Number(dataset.semester.id) === Number(active[0].id),
        'Subject deletion retains original semester',
      );
      const headman = dataset.headmanActor;
      const student = await admit(request, dataset.student, 'STUDENT');
      const subjectPath = `/api/academic/subjects/${dataset.subject.id}`;
      const control = await headman.call(
        'POST',
        '/api/academic/subjects',
        {
          name: `${dataset.marker} контроль`,
          type: 'LECTURE',
          lessonTypes: ['LECTURE'],
          initialAssignments: [
            {
              teacherId: dataset.teacher.id,
              semesterId: dataset.semester.id,
              lessonType: 'LECTURE',
              validFrom: dataset.semester.dateFrom,
              validUntilExclusive: null,
            },
          ],
        },
        [201],
      );
      let controlSubject: Row = {};
      await expect
        .poll(
          async () => {
            controlSubject = await headman.call('GET', `/api/academic/subjects/${control.id}`);
            return (
              controlSubject.assignments?.some(
                (row: Row) => Number(row.semesterId) === Number(dataset.semester.id),
              ) === true
            );
          },
          { timeout: remaining(60000), message: 'Control subject assignment becomes published' },
        )
        .toBe(true);
      const createLesson = async (subject: Row, date: string, lessonNumber: number) => {
        remaining();
        const row = await headman.call(
          'POST',
          '/api/schedule/one-off-lessons',
          {
            groupId: dataset.group.id,
            subjectId: subject.id,
            assignmentId: subject.assignments[0].id,
            date,
            lessonNumber,
            classroom: 'Синтетическая проверка удаления',
          },
          [200, 201],
          { 'Idempotency-Key': randomUUID() },
        );
        const id = Number(row.physicalLessonId);
        requireValue(
          Number.isSafeInteger(id) && id > 0,
          'Subject deletion needs a genuine physical lesson',
        );
        return id;
      };
      const past = await createLesson(dataset.subject, pastDate, 1);
      const future = await createLesson(dataset.subject, dataset.tomorrow, 1);
      const soleLesson = await createLesson(dataset.subject, dataset.tomorrow, 3);
      const kept = await createLesson(controlSubject, dataset.tomorrow, 2);
      const reportPath = `/api/attendance/reports/lesson/${past}`;
      await expect
        .poll(
          async () => {
            const body = await headman.call('GET', reportPath);
            return (
              body.editable === true &&
              body.entries.some(
                (row: Row) =>
                  Number(row.userId) === dataset.student.id &&
                  row.status === 'absent' &&
                  typeof row.markRevision === 'string',
              )
            );
          },
          {
            timeout: remaining(90000),
            message: 'Own past lesson materializes through real lifecycle and attendance',
          },
        )
        .toBe(true);
      const manual = await student.call(
        'POST',
        '/api/v1/student/requests/late-checkin',
        { lessonId: String(past) },
        [200],
        { 'Idempotency-Key': randomUUID() },
      );
      requireValue(
        manual.summary.kind === 'LATE_CHECKIN' &&
          manual.summary.origin === 'MANUAL' &&
          manual.summary.status === 'PENDING',
        'Own manual request spends a real attempt',
      );
      const approvedManual = await headman.call(
        'POST',
        `/api/attendance/requests/${manual.summary.id}/decision`,
        { decision: 'APPROVED', reason: null },
      );
      requireValue(
        approvedManual.summary.status === 'APPROVED' &&
          approvedManual.summary.decisionBy === dataset.headman.id,
        'Own manual approval creates the accepted present mark',
      );
      const png = Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF5kAAAAASUVORK5CYII=',
        'base64',
      );
      const submit = async (ids: number[]) => {
        const response = await request.post('/api/v1/student/requests/excuse', {
          headers: { ...student.headers(), 'Idempotency-Key': randomUUID() },
          timeout: remaining(),
          multipart: {
            request: {
              name: 'request.json',
              mimeType: 'application/json',
              buffer: Buffer.from(
                JSON.stringify({
                  lessonIds: ids.map(String),
                  reason: 'ILLNESS',
                  comment: dataset.marker,
                }),
              ),
            },
            files: { name: 'subject-proof.png', mimeType: 'image/png', buffer: png },
          },
        });
        requireValue(response.status() === 200, 'Own real multipart EXCUSE is accepted');
        const body = await response.json();
        await response.dispose();
        requireValue(
          /^[a-f0-9]{24}$/.test(body.summary.id) &&
            body.summary.status === 'PENDING' &&
            body.summary.lessons.length === ids.length &&
            Array.isArray(body.attachments) &&
            body.attachments.length === 1,
          'Own EXCUSE retains its exact lessons and attachment',
        );
        return body;
      };
      const mixed = await submit([future, kept]);
      const sole = await submit([soleLesson]);
      const mixedPath = `/api/attendance/requests/${mixed.summary.id}`;
      const mixedBefore = await headman.call('GET', mixedPath);
      requireValue(
        typeof mixedBefore.decisionRevision === 'string' && mixedBefore.decisionRevision.length > 0,
        'Mixed request has a genuine decision revision',
      );
      const decided = await headman.call('POST', `${mixedPath}/decision`, {
        decision: 'APPROVED',
        reason: null,
        expectedRevision: mixedBefore.decisionRevision,
        excludedOccurrenceIds: [],
        requestKey: randomUUID(),
      });
      requireValue(
        decided.summary.status === 'APPROVED' &&
          decided.summary.decisionBy === dataset.headman.id &&
          decided.lessons.length === 2,
        'Mixed EXCUSE has one actual decision for both subjects',
      );
      const beforeBudget = (await student.call('GET', '/api/v1/student/requests/options')).budget;
      requireValue(
        beforeBudget.used === 1 && beforeBudget.limit === 5 && beforeBudget.remaining === 4,
        'Before deletion the manual attempt is durably charged',
      );
      const controlBefore = fields(
        await headman.call('GET', `/api/academic/subjects/${control.id}`),
        ['id', 'name', 'type', 'lessonTypes', 'assignments'],
      );
      const beforeMark = await headman.call('GET', reportPath);
      requireValue(
        beforeMark.entries.some(
          (row: Row) => Number(row.userId) === dataset.student.id && row.status === 'present',
        ),
        'Subject deletion starts with a genuine owned PRESENT mark',
      );
      const operation = randomUUID();
      const scope = {
        operation,
        subject: Number(dataset.subject.id),
        control: Number(control.id),
        semester: Number(dataset.semester.id),
        group: Number(dataset.group.id),
        student: dataset.student.id,
        mixed: mixed.summary.id as string,
        sole: sole.summary.id as string,
        manual: manual.summary.id as string,
        removed: [past, future, soleLesson, Number(dataset.lesson.physicalLessonId)].sort(
          (a, b) => a - b,
        ),
        kept,
      };
      const databaseBefore = readback(scope, 'BEFORE');
      requireValue(
        databaseBefore.academic.operationCount === 0,
        'Owned Academic subject deletion scope is unclaimed before deletion',
      );
      requireValue(
        databaseBefore.schedule.participantCount === 0,
        'Owned Schedule subject deletion scope is unclaimed before deletion',
      );
      requireValue(
        databaseBefore.attendance.epochCount === 0,
        'Owned Attendance subject deletion scope is unclaimed before deletion',
      );
      if (databaseBefore.attendance.controlMarks.length !== 1) {
        if (databaseBefore.attendance.controlMarks.length === 0) {
          const controlDecisionLesson = decided.lessons.find(
            (row: Row) =>
              Number(row.lessonId) === kept &&
              Number(row.subjectId) === Number(control.id) &&
              Number(row.groupId) === Number(dataset.group.id) &&
              Number(row.semesterId) === Number(dataset.semester.id),
          );
          requireValue(
            Number(decided.summary.studentId) === dataset.student.id && controlDecisionLesson,
            'SUBJECT_CONTROL_BEFORE_ZERO_DECISION_PAIR_MISSING',
          );
          requireValue(
            controlDecisionLesson.attendanceStatus === 'EXCUSED',
            'SUBJECT_CONTROL_BEFORE_ZERO_DECISION_STATUS_NOT_EXCUSED',
          );
          requireValue(
            controlDecisionLesson.attendanceSource === 'HEADMAN_EXCUSE',
            'SUBJECT_CONTROL_BEFORE_ZERO_DECISION_SOURCE_NOT_HEADMAN_EXCUSE',
          );
          requireValue(false, 'SUBJECT_CONTROL_BEFORE_ZERO_DESPITE_EXCUSED_DECISION');
        } else if (databaseBefore.attendance.controlMarks.length > 1) {
          requireValue(false, 'SUBJECT_CONTROL_BEFORE_MULTIPLE');
        }
      }
      requireValue(
        databaseBefore.attendance.controlMarks.length === 1,
        'Owned control attendance exists before subject deletion',
      );
      await otp.bind();
      const preview = await headman.call('GET', `${subjectPath}/deletion-preview`);
      requireValue(
        preview.subjectId === String(scope.subject) &&
          preview.semesterId === String(scope.semester) &&
          preview.groupId === String(scope.group) &&
          /^[0-9a-f]{64}$/.test(preview.previewDigest) &&
          preview.academicRows > 0 &&
          preview.scheduleRows > 0 &&
          preview.attendanceRows > 0 &&
          preview.mixedTickets === 1,
        'Real three-service preview resolves the exact subject and mixed ticket',
      );
      const challengeResponse = await request.post(`${subjectPath}/deletion-challenge`, {
        headers: headman.headers(),
        timeout: remaining(),
        data: {
          operationId: operation,
          previewDigest: preview.previewDigest,
          password: dataset.headman.password,
        },
      });
      requireValue(
        challengeResponse.status() === 200 &&
          /(?:^|,)\s*no-store\s*(?:,|$)/i.test(challengeResponse.headers()['cache-control'] ?? ''),
        'Real password challenge is accepted privately',
      );
      const challenge = await challengeResponse.json();
      await challengeResponse.dispose();
      requireValue(
        /^[A-Za-z0-9_-]{32}$/.test(challenge.challengeId) &&
          Number.isSafeInteger(challenge.attemptsRemaining) &&
          challenge.attemptsRemaining > 0 &&
          challenge.attemptsRemaining <= 3 &&
          Date.parse(challenge.expiresAt) > Date.now(),
        'Own subject challenge has a valid bounded lifetime',
      );
      const delivered = await otp.receive(
        dataset.headman.telegramId,
        challenge.challengeId,
        challenge.attemptsRemaining,
        mainEnd,
      );
      const key = randomUUID();
      const confirmation = {
        operationId: operation,
        previewDigest: preview.previewDigest,
        challengeId: challenge.challengeId,
        code: delivered.code,
      };
      const confirm = async () => {
        const response = await request.post(`${subjectPath}/deletion`, {
          headers: { ...headman.headers(), 'Idempotency-Key': key },
          data: confirmation,
          timeout: remaining(),
        });
        requireValue(
          [200, 202].includes(response.status()) &&
            /(?:^|,)\s*no-store\s*(?:,|$)/i.test(response.headers()['cache-control'] ?? ''),
          'Real subject command is accepted privately',
        );
        const body = await response.json();
        await response.dispose();
        requireValue(
          body.operationId === operation &&
            body.subjectId === String(scope.subject) &&
            body.semesterId === String(scope.semester),
          'Accepted operation retains exact subject and semester',
        );
        return body;
      };
      await confirm();
      await expect
        .poll(
          async () => {
            remaining();
            const status = await headman.call(
              'GET',
              `/api/academic/subject-deletion-operations/${operation}`,
            );
            requireValue(
              status.operationId === operation &&
                status.subjectId === String(scope.subject) &&
                status.semesterId === String(scope.semester),
              'Durable status remains in the exact own scope',
            );
            requireValue(
              status.phase !== 'CANCELLED' && status.phase !== 'RELEASING',
              'Accepted delete cannot silently cancel',
            );
            return status.phase;
          },
          {
            timeout: remaining(90000),
            intervals: [1000],
            message: 'Real participants finish the durable subject command',
          },
        )
        .toBe('COMPLETED');
      const absent = async (actor: Actor, path: string) => {
        const response = await request.get(path, {
          headers: actor.headers(),
          timeout: remaining(),
        });
        requireValue(response.status() === 404, 'Deleted own data is authoritatively absent');
        await response.dispose();
      };
      await absent(headman, subjectPath);
      await absent(headman, `/api/academic/homeworks/${dataset.homework.id}`);
      await absent(student, `/api/v1/student/requests/${sole.summary.id}`);
      await absent(student, `/api/v1/student/requests/${manual.summary.id}`);
      const after = await headman.call('GET', mixedPath);
      requireValue(
        after.summary.id === mixed.summary.id &&
          after.summary.status === decided.summary.status &&
          after.summary.decisionBy === decided.summary.decisionBy &&
          after.summary.decisionAt === decided.summary.decisionAt &&
          after.lessons.length === 1 &&
          Number(after.lessons[0].lessonId) === kept &&
          after.attachments.length === 1 &&
          after.attachments[0].id === decided.attachments[0].id,
        'Mixed EXCUSE retains one ticket decision, control coverage and attachment',
      );
      const ownAfter = await student.call('GET', `/api/v1/student/requests/${mixed.summary.id}`);
      requireValue(
        ownAfter.summary.id === mixed.summary.id &&
          ownAfter.summary.status === 'APPROVED' &&
          ownAfter.summary.lessons.length === 1 &&
          String(ownAfter.summary.lessons[0].id) === String(kept),
        'Student authoritative projection retains only control coverage',
      );
      const attachment = await request.get(
        `/api/v1/student/requests/${mixed.summary.id}/attachments/${ownAfter.attachments[0].id}`,
        {
          headers: student.headers(),
          timeout: remaining(),
        },
      );
      requireValue(
        attachment.status() === 200 && (await attachment.body()).equals(png),
        'Mixed attachment bytes remain owned and unchanged',
      );
      await attachment.dispose();
      equal(
        fields(await headman.call('GET', `/api/academic/subjects/${control.id}`), [
          'id',
          'name',
          'type',
          'lessonTypes',
          'assignments',
        ]),
        controlBefore,
        'Control subject and assignment remain unchanged',
      );
      const scheduleBody = await headman.call(
        'GET',
        `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${pastDate}&dateTo=${shift(dataset.tomorrow, 1)}&semesterId=${dataset.semester.id}&size=200`,
      );
      const scheduleRows = Object.values(scheduleBody._embedded ?? {}).flat() as Row[];
      requireValue(
        scheduleRows.some(
          (row) => Number(row.id) === kept && Number(row.subjectId) === scope.control,
        ) && !scheduleRows.some((row) => Number(row.subjectId) === scope.subject),
        'Public schedule keeps B and removes all A lessons',
      );
      equal(
        (await student.call('GET', '/api/v1/student/requests/options')).budget,
        beforeBudget,
        'Full subject deletion does not refund the used manual attempt',
      );
      const databaseAfter = readback(scope, 'AFTER');
      requireValue(
        databaseAfter.academic.operationCount === 1 &&
          databaseAfter.academic.operation?.phase === 'COMPLETED' &&
          databaseAfter.academic.operation.schedule === 'DELETED' &&
          databaseAfter.academic.operation.attendance === 'DELETED' &&
          databaseAfter.academic.subjectRows === 0 &&
          databaseAfter.academic.homeworkRows === 0 &&
          databaseAfter.academic.controlRows === 1 &&
          databaseAfter.academic.deletedEvents === 1 &&
          databaseAfter.schedule.participantCount === 1 &&
          databaseAfter.schedule.state === 'DELETED' &&
          databaseAfter.schedule.lessonRows === 0 &&
          databaseAfter.schedule.controlRows === 1 &&
          databaseAfter.schedule.deletedEvents === 1,
        'Real PG operation, participant and cascade/outbox complete exactly once',
      );
      const mongo = databaseAfter.attendance;
      requireValue(
        mongo.epochCount === 1 &&
          mongo.epoch?.state === 'DELETED' &&
          mongo.epoch.subject === scope.subject &&
          mongo.epoch.semester === scope.semester &&
          mongo.epoch.group === scope.group &&
          mongo.marks === 0 &&
          mongo.sole === 0 &&
          mongo.manual === 0 &&
          mongo.soleFiles === 0 &&
          mongo.mixedFiles === 1 &&
          mongo.originalCount === 2 &&
          mongo.effectiveIds.length === 1 &&
          Number(mongo.effectiveIds[0]) === kept &&
          mongo.history.length === 1 &&
          mongo.history[0].operation === operation &&
          mongo.history[0].subject === scope.subject &&
          mongo.history[0].actor === dataset.headman.id &&
          mongo.history[0].removed.length === 1 &&
          mongo.history[0].removed[0] === future &&
          mongo.history[0].remaining.length === 1 &&
          mongo.history[0].remaining[0] === kept,
        'Real Mongo retains immutable mixed evidence and one exact coverage adjustment',
      );
      equal(
        mongo.controlMarks,
        databaseBefore.attendance.controlMarks,
        'Control attendance is byte-equivalent after A deletion',
      );
      equal(mongo.budget, databaseBefore.attendance.budget, 'Stored used budget is unchanged');
      requireValue(
        (await confirm()).phase === 'COMPLETED',
        'Exact secret-bearing command replay returns the same completed operation',
      );
      equal(
        readback(scope, 'REPLAY'),
        databaseAfter,
        'Exact replay has no second operation, effect, history or outbox delta',
      );
      equal(await semesters(admin), originalSemesters, 'No semester or active context changes');
    } catch (error) {
      primaryFailed = true;
      retainFirstDatasetHttpFailure(info.annotations, error);
      throw error;
    } finally {
      // Domain data and immutable receipts stay until the ordinary owned stand teardown.
      // No force-delete or foreign ledger/clock mutation is used to tidy a failed operation.
      try {
        clock.assertUnchanged();
      } catch (error) {
        if (!primaryFailed) throw error;
        expect.soft(false, 'Subject deletion leaves educational clock unchanged').toBe(true);
      }
    }
  });
});
