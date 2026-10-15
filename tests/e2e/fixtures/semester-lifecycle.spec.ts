import { createHash, randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { lstatSync, readFileSync, writeFileSync } from 'node:fs';
import { isAbsolute, join, resolve } from 'node:path';
import { expect, type APIRequestContext, type Response } from '@playwright/test';
import { test, requirePrivateArtifacts } from './artifact-privacy';
import { Actor, MobileDataset, admit, selectPwaRole } from './mobile-dataset';
import { loginPwa } from './pwa-auth';
import { TEST_USERS } from './users';
import { retainFirstDatasetHttpFailure } from './safe-reporter';

type Row = Record<string, any>;

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

async function terminal(
  admin: Actor,
  semesterId: number,
  action: 'ARCHIVE' | 'RESTORE',
  key: string,
  progress: ReturnType<typeof semesterProgress>,
  timeout = 120000,
) {
  progress.action = action;
  progress.stage = 'COMMAND';
  const path = `/api/academic/semesters/${semesterId}/${action === 'ARCHIVE' ? 'archive' : 'restore'}`;
  const operation = await admin.call('POST', path, undefined, [200, 202, 503], {
    'Idempotency-Key': key,
  });
  observeSemester(progress, operation);
  requireValue(
    typeof operation.operationId === 'string',
    'Semester command must retain its operation identity',
  );
  let status: Row = {};
  progress.stage = 'PARTICIPANT_BARRIER';
  await expect
    .poll(
      async () => {
        status = await admin.call('GET', `/api/academic/semesters/${semesterId}/archive/status`);
        observeSemester(progress, status);
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
  progress.stage = 'SETTLED_READBACK';
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
  progress.stage = 'REPLAY';
  const replay = await admin.call('POST', path, undefined, [200], { 'Idempotency-Key': key });
  requireValue(
    replay.operationId === operation.operationId &&
      replay.operationState === 'COMPLETED' &&
      replay.stateVersion === status.operation.stateVersion,
    'Semester command replay must retain the completed operation',
  );
  progress.stage = 'COMPLETED';
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
    envFile = join(root, 'tests/e2e/.env.ci');
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
  const ids = native([
    'compose',
    '--env-file',
    envFile,
    '-p',
    'rct-e2e',
    '-f',
    join(root, 'docker-compose.e2e.yml'),
    '-f',
    join(root, 'tests/e2e/infra/docker-compose.clock-fixture.yml'),
    'ps',
    '-q',
    'postgres-academic',
  ])
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
  requireValue(
    pin.labels?.['com.docker.compose.project'] === 'rct-e2e' &&
      pin.labels?.['com.docker.compose.service'] === 'postgres-academic' &&
      pin.configuredImage === 'postgres:16' &&
      pin.running === true &&
      pin.health === 'healthy',
    'Exact healthy owned Academic database is required',
  );
  const identity = () => equal(inspect(), pin, 'Owned Academic container identity changed');
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
        'empty', NOT EXISTS (SELECT 1 FROM users u JOIN groups g ON g.id=u.group_id
          WHERE (g.alphabetic_code=${quote(aPrefix)} AND g.created_at=${quote(aCreated)}::timestamptz)
             OR (g.alphabetic_code=${quote(bPrefix)} AND g.created_at=${quote(bCreated)}::timestamptz))
          AND NOT EXISTS (SELECT 1 FROM user_role_grants r JOIN groups g ON g.id=r.group_id
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
    return json(raw.trim());
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
    await page
      .getByRole('button', { name: 'Назад', exact: true })
      .first()
      .click({ timeout: budget(10000) });
    await page
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

test('FULL admin archives and restores the disposable CI semester through real participants', async ({
  playwright,
  page,
}, info) => {
  requirePrivateArtifacts(info);
  test.setTimeout(420000);
  const browserLoginCutoff = performance.now() + 270000;
  const promotionCaseEnd = promotionStartedAt.get(info)! + info.timeout - 10000;
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
    const archived = await terminal(
      admin,
      ownSemesterId,
      'ARCHIVE',
      archiveKey,
      diagnostic.primary,
    );
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
    await terminal(admin, ownSemesterId, 'RESTORE', restoreKey, diagnostic.primary);
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
    await ownedHistoricalPromotion(page, request, admin, promotionCaseEnd, ownSemesterId);
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

// One explicit synthetic operator action; never an automatic SENT/DLQ redriver.
test('FULL admin reconciles one exact stranded close before archive completion', async ({
  playwright,
}, info) => {
  requirePrivateArtifacts(info);
  test.setTimeout(420000);
  const end = performance.now() + info.timeout - Math.max(info.duration, 10000);
  const run = process.env.RCT_SEMESTER_LIFECYCLE_RUN;
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
  const envFile = join(root, 'tests/e2e/.env.ci');
  const envStat = lstatSync(envFile);
  requireValue(
    envStat.isFile() &&
      !envStat.isSymbolicLink() &&
      envStat.size <= 65536 &&
      (envStat.mode & 0o022) === 0,
    'Synthetic CI configuration must be bounded and private',
  );
  const wanted = [
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
  const diagnostic = {
    schema: 'semester-reconciliation.v1',
    phase: 'SETUP',
    configuredListenerAttempts: 3,
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
  };
  const stopped = new Set<string>();
  let eventAccepted = false,
    applied = false,
    archiveStarted = false,
    archived = false;
  let restored = false,
    authorityDirty = false,
    cleaning = false;
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
  const docker = (
    args: string[],
    input?: string,
    extra: Record<string, string> = {},
    cap = 5000,
  ): string => {
    try {
      return execFileSync('docker', args, {
        cwd: root,
        env: { ...nativeEnv, ...extra },
        input,
        timeout: budget(cap),
        maxBuffer: 262144,
        encoding: 'utf8',
        stdio: ['pipe', 'pipe', 'pipe'],
      });
    } catch {
      throw new Error('Owned reconciliation command failed');
    }
  };
  const compose = [
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
  const inspect = (id: string): Row =>
    JSON.parse(
      docker([
        'inspect',
        '--format',
        '{"id":{{json .Id}},"image":{{json .Image}},"labels":{{json .Config.Labels}},"running":{{json .State.Running}},"health":{{json .State.Health.Status}},"mounts":{{json .Mounts}},"networks":{{json .NetworkSettings.Networks}}}',
        id,
      ]),
    );
  const pins: Record<string, Row> = {};
  const canonicalObjectOrder = (value: unknown): unknown => {
    if (Array.isArray(value)) return value.map(canonicalObjectOrder);
    if (value === null || typeof value !== 'object') return value;
    return Object.fromEntries(
      Object.entries(value)
        .sort(([left], [right]) => (left < right ? -1 : left > right ? 1 : 0))
        .map(([key, item]) => [key, canonicalObjectOrder(item)]),
    );
  };
  const mountRecords = (value: unknown): unknown =>
    Array.isArray(value)
      ? value.map((record) => JSON.stringify(canonicalObjectOrder(record))).sort()
      : canonicalObjectOrder(value);
  const identity = (service: string) => {
    const pin = pins[service];
    requireValue(pin, 'Pinned owned service is required');
    const current = inspect(pin.id);
    requireValue(
      current.id === pin.id &&
        current.image === pin.image &&
        current.labels['com.docker.compose.project'] === 'rct-e2e' &&
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
      current.networks.rct_e2e_private_net?.IPAddress ??
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
    } catch {
      throw new Error('Owned broker control unavailable');
    }
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
  });
  const stop = (service: string) => {
    budget(15000, { stop: service }, true);
    identity(service);
    stopped.add(service);
    docker([...compose, 'stop', '--timeout', '10', service], undefined, {}, 15000);
    requireValue(identity(service).running === false, 'Owned service must stop');
  };
  const restart = (service: string) => {
    if (!stopped.has(service)) return;
    identity(service);
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
      45000,
    );
    const current = identity(service);
    requireValue(
      current.running && current.health === 'healthy',
      'Same owned service must recover',
    );
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
        q.main.messages_unacknowledged === 0,
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
          prop.headers?.event_type === 'lesson.closed' &&
          prop.headers['x-death']?.some(
            (v: Row) =>
              v.queue === 'attendance-service.events' && v.reason === 'rejected' && v.count === 1,
          ),
        'Exact original delivery must have been rejected to its DLQ',
      );
      properties = {
        content_type: prop.content_type,
        content_encoding: prop.content_encoding,
        delivery_mode: prop.delivery_mode,
        headers: { event_type: prop.headers.event_type },
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
    const sent = await broker('exchanges/%2F/amq.default/publish', {
      properties,
      routing_key: 'attendance-service.events',
      payload: envelope!.toString('base64'),
      payload_encoding: 'base64',
    });
    requireValue(sent.routed === true, 'Exact targeted copy must route to Attendance');
  };
  const awaitApplied = async () => {
    await expect
      .poll(
        () => {
          const row = effect(),
            value = consumer(),
            r = value.receipt[0];
          const ack = value.acks[0] && JSON.parse(value.acks[0].payload);
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
    const isClosed = async () =>
      Object.values(
        (await closeCommand!.actor.call('GET', closeCommand!.lessonsPath))._embedded ?? {},
      )
        .flat()
        .some(
          (row: any) =>
            Number(row.id) === scope.lesson &&
            Number(row.groupId) === scope.group &&
            Number(row.semesterId) === scope.semester &&
            row.status === 'CLOSED',
        );
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
      'schedule-service',
      'attendance-service',
      'postgres-schedule',
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
    await expect
      .poll(
        async () => {
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
          return (
            q.main.messages_ready === 0 &&
            q.main.messages_unacknowledged === 0 &&
            q.main.consumers === 1 &&
            q.dead.messages_ready === 0 &&
            q.dead.messages_unacknowledged === 0 &&
            state.unproven === 0 &&
            state.legacy === 0 &&
            state.due === 0
          );
        },
        { timeout: budget(10000), message: 'Fresh synthetic namespace is quiescent' },
      )
      .toBe(true);
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
    restart('attendance-service');
    diagnostic.phase = 'STRANDED';
    await expect
      .poll(async () => (await queues()).dead.messages_ready, {
        timeout: budget(15000),
        message: 'Configured three-attempt policy rejects own close',
      })
      .toBe(1);
    await stranded();
    restart('schedule-service');
    diagnostic.phase = 'PENDING_ARCHIVE';
    budget(10000, { archive: true }, true);
    for (const service of ['schedule-service', 'attendance-service']) {
      const current = identity(service);
      requireValue(
        current.running && current.health === 'healthy',
        'Archive dependencies must remain healthy',
      );
    }
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
    originalFailure = { error };
    retainFirstDatasetHttpFailure(info.annotations, error);
  } finally {
    cleaning = true;
    let cleanupFailure: { error: unknown } | undefined;
    try {
      for (const service of ['schedule-service', 'attendance-service']) {
        try {
          restart(service);
        } catch (error) {
          cleanupFailure ??= { error };
        }
      }
      if (cleanupFailure) throw cleanupFailure.error;
      if (eventAccepted && !applied) {
        await recoverOwnClose();
        if (
          !diagnostic.recoveryPublishAttempted &&
          consumer().receipt.length === 0 &&
          (await queues()).dead.messages_ready === 1
        )
          await publish();
        await awaitApplied();
      }
      if (admin && previous && archiveStarted) {
        if (!archived) {
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
        const active = (await semesters(admin)).filter((row) => row.active === true);
        requireValue(
          active.length === 1 && Number(active[0].id) === Number(previous.id),
          'Original exclusive active authority must return',
        );
        authorityDirty = false;
      }
      if (peeked) {
        const q = await queues();
        requireValue(
          q.dead.messages_ready === 1 && q.dead.messages_unacknowledged === 0,
          'Original DLQ copy must remain retained',
        );
        diagnostic.retainedDlqCopies = 1;
      }
      requireValue(
        stopped.size === 0 && !authorityDirty && (!eventAccepted || applied),
        'Controlled state must settle safely',
      );
      diagnostic.restorationSucceeded = true;
      diagnostic.cleanupOutcome = 'PASSED';
    } catch (error) {
      cleanupFailure = { error };
      diagnostic.cleanupOutcome = 'FAILED';
    }
    try {
      clock.assertUnchanged();
      diagnostic.clockUnchanged = true;
    } catch (error) {
      cleanupFailure ??= { error };
    }
    let disposeTimer: ReturnType<typeof setTimeout> | undefined;
    try {
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
      cleanupFailure ??= { error };
    } finally {
      if (disposeTimer !== undefined) clearTimeout(disposeTimer);
    }
    if (cleanupFailure) {
      diagnostic.restorationSucceeded = false;
      diagnostic.cleanupOutcome = 'FAILED';
    }
    try {
      writeFileSync(join(run, 'reconciliation.safe.json'), `${JSON.stringify(diagnostic)}\n`, {
        mode: 0o600,
        flag: 'wx',
      });
    } catch (error) {
      cleanupFailure ??= { error };
    }
    if (originalFailure) throw originalFailure.error;
    if (cleanupFailure) throw cleanupFailure.error;
  }
});
