import { lstatSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import {
  request as requestFactory,
  type APIRequestContext,
  type Page,
  type Response,
  type TestInfo,
} from '@playwright/test';
import type { MobileDataset, SyntheticUser } from './mobile-dataset';

export const caseTitles = {
  STUDENT: 'STUDENT completion persists and another group cannot mutate the homework',
  HEADMAN: 'HEADMAN marks the real roster and TEACHER reads without acquiring write permission',
  HELPER: 'STUDENT helper marks with its capability and revocation denies the same effect',
} as const;
type CaseCode = keyof typeof caseTitles;
type Endpoint =
  | 'LOGIN'
  | 'LOGOUT'
  | 'SESSION'
  | 'ROLE_CHANGE'
  | 'HOMEWORK_DEFAULT'
  | 'HOMEWORK_EXPLICIT'
  | 'LESSON_LIST'
  | 'LESSON_ROSTER'
  | 'HELPER_PERMISSIONS'
  | 'MARK_WRITE'
  | 'COMPLETION_WRITE';
type Phase = 'BEGIN' | 'UI_RESPONSE' | 'FAILURE_READ' | 'DOM' | 'END';
type Facts = Record<string, boolean | number | string>;
interface SafeEvent {
  caseCode: CaseCode;
  phase: Phase;
  endpoint?: Endpoint;
  facts: Facts;
}
const httpStatuses = [
  200, 201, 202, 204, 301, 302, 303, 307, 308, 400, 401, 403, 404, 409, 412, 422, 429, 500, 502,
  503, 504,
];
const boolKeys = new Set([
  'bodyParsed',
  'bodyShapeKnown',
  'uiTokenCaptured',
  'actorMatchesTarget',
  'actorMatchesTeacher',
  'requiredRoleSelectable',
  'activeRoleExpected',
  'itemsIsArray',
  'targetPresent',
  'targetTitleEqual',
  'semesterEqual',
  'rangeContainsTarget',
  'serverDateEqual',
  'selectedDateEqual',
  'selectedDateObserved',
  'requestedRangeEqual',
  'groupPathEqual',
  'lessonPathEqual',
  'lessonIdEqual',
  'groupEqual',
  'subjectEqual',
  'dateEqual',
  'transferPending',
  'entriesIsArray',
  'studentPresent',
  'assistantPresent',
  'targetNameEqual',
  'reportEditableKnown',
  'reportEditable',
  'rowEditableKnown',
  'rowEditable',
  'normalizedRowEditable',
  'blockedReasonPresent',
  'markPermission',
  'loginVisible',
  'navigationVisible',
  'rolePickerVisible',
  'targetRowVisible',
  'targetButtonVisible',
  'targetButtonEnabled',
  'clearButtonVisible',
  'clearButtonEnabled',
  'homeworkVisible',
  'homeworkCompleteVisible',
  'homeworkIncompleteVisible',
  'journalBusy',
  'diagnosticCompleted',
]);
const countKeys = new Set([
  'itemCount',
  'entryCount',
  'lessonCount',
  'targetCount',
  'journalCount',
  'rowCount',
  'targetRowCount',
  'targetButtonCount',
  'clearButtonCount',
  'alertCount',
  'homeworkCount',
  'homeworkCompleteCount',
  'homeworkIncompleteCount',
  'droppedEventCount',
]);
const enumValues: Record<string, readonly string[]> = {
  httpStatus: [...httpStatuses.map(String), 'OTHER'],
  activeRole: ['STUDENT', 'HEADMAN', 'TEACHER', 'ADMIN', 'NONE', 'UNKNOWN'],
  targetStatus: ['EMPTY', 'PRESENT', 'ABSENT', 'EXCUSED', 'CANCELLED', 'UNSUPPORTED'],
  domStatus: ['EMPTY', 'PRESENT', 'ABSENT', 'EXCUSED', 'CANCELLED', 'UNSUPPORTED', 'ABSENT_ROW'],
  lessonStatus: ['PLANNED', 'ACTIVE', 'CLOSED', 'CANCELLED', 'TRANSFERRED', 'UNSUPPORTED'],
  testStatus: ['passed', 'failed', 'timedOut', 'skipped', 'interrupted', 'UNKNOWN'],
  diagnosticState: [
    'OBSERVING',
    'NO_UI_TOKEN',
    'WRONG_ACTOR',
    'COMPLETE',
    'READ_UNAVAILABLE',
    'DOM_UNAVAILABLE',
  ],
};

/** Runtime allowlist rejects raw strings/objects, including unexpected new diagnostic fields. */
export function validateFacts(facts: Facts): void {
  for (const [key, value] of Object.entries(facts)) {
    if (boolKeys.has(key) && typeof value === 'boolean') continue;
    if (
      countKeys.has(key) &&
      typeof value === 'number' &&
      Number.isSafeInteger(value) &&
      value >= 0 &&
      value <= 1_000_000
    )
      continue;
    if (
      Object.hasOwn(enumValues, key) &&
      typeof value === 'string' &&
      enumValues[key].includes(value)
    )
      continue;
    throw new Error('Focused diagnostic rejected a non-allowlisted field');
  }
}
function record(value: unknown): Record<string, unknown> | undefined {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined;
}
function unwrap(value: unknown): Record<string, unknown> | undefined {
  const raw = record(value);
  return record(raw?.content) ?? raw;
}
function idEqual(value: unknown, expected: unknown): boolean {
  return (
    (typeof value === 'string' || typeof value === 'number') &&
    (typeof expected === 'string' || typeof expected === 'number') &&
    String(value) === String(expected)
  );
}
function count(value: number): number {
  return Math.min(Math.max(value, 0), 1_000_000);
}
function status(value: number): string {
  return httpStatuses.includes(value) ? String(value) : 'OTHER';
}
function attendanceStatus(value: unknown): string {
  const raw =
    typeof value === 'string' ? value.trim().toUpperCase() : value === null ? '' : 'UNSUPPORTED';
  // Match the actual client: literal wire "EMPTY" is UNSUPPORTED; null/empty string is EMPTY.
  return ['PRESENT', 'ABSENT', 'EXCUSED', 'CANCELLED'].includes(raw)
    ? raw
    : raw === ''
      ? 'EMPTY'
      : 'UNSUPPORTED';
}
function lessonStatus(value: unknown): string {
  const raw = typeof value === 'string' ? value.trim().toUpperCase() : '';
  return enumValues.lessonStatus.includes(raw) ? raw : 'UNSUPPORTED';
}
function arrayItems(value: unknown): unknown[] | undefined {
  if (Array.isArray(value)) return value;
  const body = record(value);
  if (Array.isArray(body?.content)) return body.content;
  const embedded = record(body?._embedded);
  if (Array.isArray(embedded?.lessonResponseList)) return embedded.lessonResponseList;
  return embedded
    ? (Object.values(embedded).find(Array.isArray) as unknown[] | undefined)
    : undefined;
}
/** Passive UI observation plus failed-case GETs using only the UI-issued token in this closure. */
export class FocusObserver {
  private readonly caseCode: CaseCode;
  private readonly actor: SyntheticUser;
  private readonly target: SyntheticUser;
  private readonly file: string;
  private events: SafeEvent[] = [];
  private dropped = 0;
  private pending = new Set<Promise<void>>();
  private active = true;
  private stopped = false;
  private ended = false;
  private cancelled = false;
  private readonly cancellation = new AbortController();
  private diagnosticCreation?: Promise<APIRequestContext>;
  private diagnosticClose?: Promise<void>;
  private token: string | undefined;
  private defaultHomeworkObserved = false;
  private selectedDate: string | undefined;
  private actorIsExpected = false;
  private responseCount = 0;
  private readonly responseListener = (response: Response): void => {
    if (!this.active) return;
    if (this.responseCount >= 96 || this.pending.size >= 24) {
      this.dropped++;
      return;
    }
    this.responseCount++;
    const operation = this.captureResponse(response)
      .catch(() => {
        if (this.active && !this.stopped)
          this.add('UI_RESPONSE', { diagnosticState: 'READ_UNAVAILABLE' });
      })
      .finally(() => this.pending.delete(operation));
    this.pending.add(operation);
  };

  constructor(
    private readonly page: Page,
    private readonly dataset: MobileDataset,
    info: TestInfo,
  ) {
    const key = (Object.keys(caseTitles) as CaseCode[]).find(
      (item) => caseTitles[item] === info.title,
    );
    if (!key) throw new Error('Focused diagnostic refuses an unselected case');
    this.caseCode = key;
    this.actor =
      key === 'STUDENT' ? dataset.student : key === 'HEADMAN' ? dataset.headman : dataset.assistant;
    this.target = key === 'HELPER' ? dataset.assistant : dataset.student;
    const run = process.env.RCT_FOCUS3_RUN;
    const output = process.env.RCT_FOCUS3_OBSERVATIONS;
    if (!run || output !== join(run, 'supervision', 'observations.safe.json')) {
      throw new Error('Focused diagnostic requires its private observation file');
    }
    const directory = lstatSync(dirname(output));
    if (
      !directory.isDirectory() ||
      directory.isSymbolicLink() ||
      (directory.mode & 0o777) !== 0o700
    ) {
      throw new Error('Focused diagnostic observation directory is not private');
    }
    this.file = output.replace('.safe.json', `-${key.toLowerCase()}.safe.json`);
  }

  start(): void {
    this.add('BEGIN', { diagnosticState: 'OBSERVING' });
    this.page.on('response', this.responseListener);
  }
  private add(phase: Phase, facts: Facts, endpoint?: Endpoint): void {
    if (this.stopped || this.ended) return;
    validateFacts(facts);
    if (this.events.length >= 96) {
      this.dropped++;
      return;
    }
    this.events.push({ caseCode: this.caseCode, phase, ...(endpoint ? { endpoint } : {}), facts });
    this.persist();
  }
  private persist(): void {
    if (this.ended) return;
    const output = {
      schema: 'RCT_FOCUS3_CLOSED_V1',
      caseCode: this.caseCode,
      events: this.events,
      droppedEventCount: count(this.dropped),
    };
    // Only closed, already-validated records enter serialization. No body, error or TestInfo spread.
    writeFileSync(`${this.file}.next`, JSON.stringify(output, null, 2) + '\n', { mode: 0o600 });
    renameSync(`${this.file}.next`, this.file);
  }

  private requireRunning(): void {
    if (this.stopped || this.ended) throw new Error('Focused diagnostic observation stopped');
  }
  private stop(cancelled: boolean): void {
    if (this.stopped) return;
    this.stopped = true;
    this.cancelled = cancelled;
    this.active = false;
    this.token = undefined;
    this.actorIsExpected = false;
    this.page.off('response', this.responseListener);
    this.cancellation.abort();
    // Owned context only; never dispose the page's or dataset's shared request context.
    void this.closeDiagnosticContext().catch(() => {});
  }
  private closeDiagnosticContext(): Promise<void> {
    if (!this.diagnosticClose) {
      this.diagnosticClose = (async () => {
        const context = await this.diagnosticCreation;
        if (context) await context.dispose({ reason: 'Focused diagnostic stopped' });
      })();
    }
    return this.diagnosticClose;
  }
  private async diagnosticContext(): Promise<APIRequestContext> {
    this.requireRunning();
    this.diagnosticCreation ??= requestFactory.newContext({
      baseURL: 'https://localhost:8443',
      ignoreHTTPSErrors: true,
      timeout: 3000,
      maxRedirects: 0,
    });
    const context = await this.wait(this.diagnosticCreation, 3000);
    this.requireRunning();
    return context;
  }
  private wait<T>(operation: Promise<T>, milliseconds: number): Promise<T> {
    this.requireRunning();
    return new Promise<T>((resolve, reject) => {
      let settled = false;
      const finish = (action: () => void): void => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        this.cancellation.signal.removeEventListener('abort', aborted);
        action();
      };
      const aborted = (): void =>
        finish(() => reject(new Error('Focused diagnostic observation stopped')));
      const timer = setTimeout(() => this.stop(true), milliseconds);
      this.cancellation.signal.addEventListener('abort', aborted, { once: true });
      operation.then(
        (value) =>
          finish(() => {
            if (this.stopped || this.ended)
              reject(new Error('Focused diagnostic observation stopped'));
            else resolve(value);
          }),
        () => finish(() => reject(new Error('Focused diagnostic read unavailable'))),
      );
    });
  }
  private async domStep<T>(start: () => Promise<T>): Promise<T> {
    this.requireRunning();
    const value = await this.wait(start(), 3000);
    this.requireRunning();
    return value;
  }

  private sessionFacts(value: unknown): Facts {
    const body = record(value);
    const role =
      body?.activeRole == null
        ? 'NONE'
        : typeof body.activeRole === 'string' && enumValues.activeRole.includes(body.activeRole)
          ? body.activeRole
          : 'UNKNOWN';
    this.actorIsExpected = idEqual(body?.userId, this.actor.id);
    const required = this.caseCode === 'HEADMAN' ? 'HEADMAN' : 'STUDENT';
    const grants = Array.isArray(body?.roles) ? body.roles : [];
    return {
      bodyShapeKnown: !!body,
      actorMatchesTarget: this.actorIsExpected,
      actorMatchesTeacher: idEqual(body?.userId, this.dataset.teacher.id),
      activeRole: role,
      activeRoleExpected: role === required,
      requiredRoleSelectable: grants.some(
        (grant) => record(grant)?.role === required && record(grant)?.selectable === true,
      ),
    };
  }
  private homeworkFacts(value: unknown): Facts {
    const body = record(value);
    const items = Array.isArray(body?.items) ? body.items : [];
    const targets = items.filter((item) => idEqual(record(item)?.id, this.dataset.homework.id));
    const target = record(targets[0]);
    const from = typeof body?.from === 'string' ? body.from : undefined;
    const to = typeof body?.to === 'string' ? body.to : undefined;
    let serverDateEqual = false;
    if (typeof body?.serverNow === 'string') {
      const instant = new Date(body.serverNow);
      if (Number.isFinite(instant.getTime())) {
        const parts = new Intl.DateTimeFormat('en-CA', {
          timeZone: 'Europe/Moscow',
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
        }).formatToParts(instant);
        const part = (kind: string) => parts.find((item) => item.type === kind)?.value;
        serverDateEqual = `${part('year')}-${part('month')}-${part('day')}` === this.dataset.today;
      }
    }
    return {
      bodyShapeKnown: !!body,
      itemsIsArray: Array.isArray(body?.items),
      itemCount: count(items.length),
      targetCount: count(targets.length),
      targetPresent: targets.length > 0,
      targetTitleEqual: target?.title === this.dataset.marker,
      semesterEqual: idEqual(record(body?.semester)?.id, this.dataset.semester.id),
      rangeContainsTarget:
        !!from && !!to && from <= this.dataset.tomorrow && to >= this.dataset.tomorrow,
      serverDateEqual,
    };
  }
  private lessonsFacts(value: unknown): Facts {
    const items = arrayItems(value);
    const targets = (items ?? [])
      .map(unwrap)
      .filter((item) => idEqual(item?.id, this.dataset.lesson.physicalLessonId));
    const target = targets[0];
    return {
      itemsIsArray: !!items,
      lessonCount: count(items?.length ?? 0),
      targetCount: count(targets.length),
      targetPresent: targets.length > 0,
      groupEqual: idEqual(target?.groupId, this.dataset.group.id),
      subjectEqual: idEqual(target?.subjectId, this.dataset.subject.id),
      dateEqual: target?.date === this.dataset.today,
      lessonStatus: lessonStatus(target?.status),
      transferPending: target?.transferState === 'PENDING',
    };
  }
  private rosterFacts(value: unknown): Facts {
    const body = unwrap(value);
    const entries = Array.isArray(body?.entries) ? body.entries : [];
    const targets = entries.map(record).filter((entry) => idEqual(entry?.userId, this.target.id));
    const target = targets[0];
    return {
      bodyShapeKnown: !!body,
      entriesIsArray: Array.isArray(body?.entries),
      entryCount: count(entries.length),
      targetCount: count(targets.length),
      targetPresent: targets.length > 0,
      studentPresent: entries.some((entry) =>
        idEqual(record(entry)?.userId, this.dataset.student.id),
      ),
      assistantPresent: entries.some((entry) =>
        idEqual(record(entry)?.userId, this.dataset.assistant.id),
      ),
      targetNameEqual:
        typeof target?.displayName === 'string' &&
        target.displayName.trim() === this.target.displayName,
      reportEditableKnown: typeof body?.editable === 'boolean',
      reportEditable: body?.editable === true,
      rowEditableKnown: typeof target?.editable === 'boolean',
      rowEditable: target?.editable === true,
      normalizedRowEditable: !!target && body?.editable === true && target.editable !== false,
      blockedReasonPresent:
        target?.blockedReason !== undefined &&
        target.blockedReason !== null &&
        target.blockedReason !== '',
      targetStatus: attendanceStatus(target?.status),
      lessonIdEqual: idEqual(body?.lessonId, this.dataset.lesson.physicalLessonId),
      groupEqual: idEqual(body?.groupId, this.dataset.group.id),
      subjectEqual: idEqual(body?.subjectId, this.dataset.subject.id),
      semesterEqual: idEqual(body?.semesterId, this.dataset.semester.id),
      dateEqual: body?.lessonDate === this.dataset.today,
      lessonStatus: lessonStatus(body?.lessonStatus),
    };
  }
  private classify(url: URL, method: string): Endpoint | undefined {
    const path = url.pathname;
    if (path === '/api/auth/login' && method === 'POST') return 'LOGIN';
    if (path === '/api/auth/logout' && method === 'POST') return 'LOGOUT';
    if (path === '/api/auth/session' && method === 'GET') return 'SESSION';
    if (path === '/api/auth/session/active-role' && method === 'PUT') return 'ROLE_CHANGE';
    if (path === '/api/v1/student/homework' && method === 'GET')
      return url.search ? 'HOMEWORK_EXPLICIT' : 'HOMEWORK_DEFAULT';
    if (/^\/api\/schedule\/groups\/\d+\/lessons$/.test(path) && method === 'GET')
      return 'LESSON_LIST';
    if (/^\/api\/attendance\/reports\/lesson\/\d+$/.test(path) && method === 'GET')
      return 'LESSON_ROSTER';
    if (path === '/api/academic/assistants/me/permissions' && method === 'GET')
      return 'HELPER_PERMISSIONS';
    if (
      path ===
        `/api/attendance/lessons/${this.dataset.lesson.physicalLessonId}/students/${this.target.id}` &&
      method === 'PUT'
    )
      return 'MARK_WRITE';
    if (
      path === `/api/v1/student/homework/${this.dataset.homework.id}/completion` &&
      method === 'PUT'
    )
      return 'COMPLETION_WRITE';
    return undefined;
  }
  private async captureResponse(response: Response): Promise<void> {
    const url = new URL(response.url());
    if (url.origin !== 'https://localhost:8443') return;
    const endpoint = this.classify(url, response.request().method());
    if (!endpoint) return;
    const facts: Facts = { httpStatus: status(response.status()) };
    // This is a header from an actual UI request, never a seed/admin actor or an invented JWT.
    const authorization = response.request().headers()['authorization'];
    if (authorization?.startsWith('Bearer ')) this.token = authorization.slice(7);
    if (endpoint === 'LOGOUT') {
      this.token = undefined;
      this.actorIsExpected = false;
    }
    if (endpoint === 'LESSON_LIST') {
      const from = url.searchParams.get('dateFrom');
      const to = url.searchParams.get('dateTo');
      if (from && from === to && /^\d{4}-\d{2}-\d{2}$/.test(from)) this.selectedDate = from;
      facts.selectedDateEqual = from === this.dataset.today && to === this.dataset.today;
      facts.groupPathEqual =
        url.pathname === `/api/schedule/groups/${this.dataset.group.id}/lessons`;
    }
    if (endpoint === 'LESSON_ROSTER')
      facts.lessonPathEqual =
        url.pathname === `/api/attendance/reports/lesson/${this.dataset.lesson.physicalLessonId}`;
    if (endpoint === 'HOMEWORK_EXPLICIT')
      facts.requestedRangeEqual =
        url.searchParams.get('from') === this.dataset.today &&
        url.searchParams.get('to') === this.dataset.tomorrow;
    if (response.status() === 200 && !['MARK_WRITE', 'COMPLETION_WRITE'].includes(endpoint)) {
      let body: unknown;
      try {
        body = await this.wait(response.json() as Promise<unknown>, 1500);
        this.requireRunning();
        facts.bodyParsed = true;
      } catch {
        facts.bodyParsed = false;
      }
      if (this.stopped || this.ended) return;
      if (endpoint === 'LOGIN' || endpoint === 'ROLE_CHANGE') {
        const login = record(body);
        if (typeof login?.accessToken === 'string') this.token = login.accessToken;
        facts.uiTokenCaptured = typeof login?.accessToken === 'string';
        if (endpoint === 'ROLE_CHANGE') Object.assign(facts, this.sessionFacts(login?.session));
      } else if (endpoint === 'SESSION') Object.assign(facts, this.sessionFacts(body));
      else if (endpoint.startsWith('HOMEWORK_')) {
        Object.assign(facts, this.homeworkFacts(body));
        if (endpoint === 'HOMEWORK_DEFAULT' && facts.bodyParsed)
          this.defaultHomeworkObserved = true;
      } else if (endpoint === 'LESSON_LIST') Object.assign(facts, this.lessonsFacts(body));
      else if (endpoint === 'LESSON_ROSTER') Object.assign(facts, this.rosterFacts(body));
      else if (endpoint === 'HELPER_PERMISSIONS') facts.markPermission = hasMarkPermission(body);
    }
    if (this.active) this.add('UI_RESPONSE', facts, endpoint);
  }

  private async failureRead(endpoint: Endpoint, path: string): Promise<void> {
    if (this.stopped || this.ended || !this.token) return;
    const facts: Facts = {};
    try {
      const context = await this.diagnosticContext();
      this.requireRunning();
      const response = await this.wait(
        context.get(path, {
          headers: { Authorization: `Bearer ${this.token}` },
          timeout: 3000,
          maxRetries: 0,
          maxRedirects: 0,
        }),
        4500,
      );
      this.requireRunning();
      facts.httpStatus = status(response.status());
      if (response.status() === 200) {
        const body: unknown = await this.wait(response.json() as Promise<unknown>, 1500);
        this.requireRunning();
        facts.bodyParsed = true;
        if (endpoint === 'SESSION') Object.assign(facts, this.sessionFacts(body));
        else if (endpoint.startsWith('HOMEWORK_')) Object.assign(facts, this.homeworkFacts(body));
        else if (endpoint === 'LESSON_LIST') Object.assign(facts, this.lessonsFacts(body));
        else if (endpoint === 'LESSON_ROSTER') Object.assign(facts, this.rosterFacts(body));
        else if (endpoint === 'HELPER_PERMISSIONS') facts.markPermission = hasMarkPermission(body);
      }
      // All cached response bodies are released by the one owned context's final disposal.
    } catch {
      facts.diagnosticState = 'READ_UNAVAILABLE';
    }
    this.add('FAILURE_READ', facts, endpoint);
  }
  private async failureReads(): Promise<void> {
    if (this.stopped || this.ended) return;
    if (!this.token) {
      this.add('FAILURE_READ', { diagnosticState: 'NO_UI_TOKEN' });
      return;
    }
    this.actorIsExpected = false;
    await this.failureRead('SESSION', '/api/auth/session');
    if (this.stopped || this.ended) return;
    if (!this.actorIsExpected) {
      this.add('FAILURE_READ', { diagnosticState: 'WRONG_ACTOR' });
      return;
    }
    if (this.caseCode === 'STUDENT') {
      const reads = [
        this.failureRead(
          'HOMEWORK_EXPLICIT',
          `/api/v1/student/homework?from=${this.dataset.today}&to=${this.dataset.tomorrow}`,
        ),
      ];
      if (!this.defaultHomeworkObserved)
        reads.push(this.failureRead('HOMEWORK_DEFAULT', '/api/v1/student/homework'));
      await Promise.all(reads);
    } else {
      const date = this.selectedDate ?? this.dataset.today;
      this.add(
        'FAILURE_READ',
        {
          selectedDateObserved: !!this.selectedDate,
          selectedDateEqual: date === this.dataset.today,
        },
        'LESSON_LIST',
      );
      const query = new URLSearchParams({ dateFrom: date, dateTo: date, page: '0', size: '100' });
      for (const value of ['PLANNED', 'ACTIVE', 'CLOSED', 'CANCELLED', 'TRANSFERRED'])
        query.append('status', value);
      const reads = [
        this.failureRead(
          'LESSON_LIST',
          `/api/schedule/groups/${this.dataset.group.id}/lessons?${query}`,
        ),
        this.failureRead(
          'LESSON_ROSTER',
          `/api/attendance/reports/lesson/${this.dataset.lesson.physicalLessonId}`,
        ),
      ];
      if (this.caseCode === 'HELPER')
        reads.push(
          this.failureRead('HELPER_PERMISSIONS', '/api/academic/assistants/me/permissions'),
        );
      await Promise.all(reads);
    }
  }
  private async domFacts(): Promise<Facts> {
    const row = this.page.locator(`li.headman-journal__row[data-user-id="${this.target.id}"]`);
    const mark = this.page.getByRole('button', {
      name: `Был: ${this.target.displayName}`,
      exact: true,
    });
    const clear = this.page.getByRole('button', {
      name: `Был: ${this.target.displayName}. Снять отметку`,
      exact: true,
    });
    const homework = this.page.getByText(this.dataset.marker, { exact: true });
    const complete = this.page.getByRole('slider', {
      name: `Снять отметку: ${this.dataset.marker}`,
      exact: true,
    });
    const incomplete = this.page.getByRole('slider', {
      name: `Выполнить: ${this.dataset.marker}`,
      exact: true,
    });
    const [rowCount, markCount, clearCount, homeworkCount, completeCount, incompleteCount] =
      await Promise.all([
        this.domStep(() => row.count()),
        this.domStep(() => mark.count()),
        this.domStep(() => clear.count()),
        this.domStep(() => homework.count()),
        this.domStep(() => complete.count()),
        this.domStep(() => incomplete.count()),
      ]);
    const facts: Facts = {
      journalCount: count(await this.domStep(() => this.page.locator('.headman-journal').count())),
      rowCount: count(
        await this.domStep(() => this.page.locator('li.headman-journal__row').count()),
      ),
      targetRowCount: count(rowCount),
      targetButtonCount: count(markCount),
      clearButtonCount: count(clearCount),
      homeworkCount: count(homeworkCount),
      homeworkCompleteCount: count(completeCount),
      homeworkIncompleteCount: count(incompleteCount),
      alertCount: count(await this.domStep(() => this.page.getByRole('alert').count())),
      loginVisible: await this.domStep(() =>
        this.page.getByRole('heading', { name: 'Войти в приложение', exact: true }).isVisible(),
      ),
      navigationVisible: await this.domStep(() =>
        this.page.getByRole('navigation', { name: 'Основная навигация', exact: true }).isVisible(),
      ),
      rolePickerVisible: await this.domStep(() =>
        this.page.getByRole('heading', { name: 'Сменить роль', exact: true }).isVisible(),
      ),
      targetRowVisible: rowCount === 1 && (await this.domStep(() => row.isVisible())),
      targetButtonVisible: markCount === 1 && (await this.domStep(() => mark.isVisible())),
      targetButtonEnabled:
        markCount === 1 && (await this.domStep(() => mark.isEnabled({ timeout: 1000 }))),
      clearButtonVisible: clearCount === 1 && (await this.domStep(() => clear.isVisible())),
      clearButtonEnabled:
        clearCount === 1 && (await this.domStep(() => clear.isEnabled({ timeout: 1000 }))),
      homeworkVisible:
        homeworkCount > 0 && (await this.domStep(() => homework.first().isVisible())),
      homeworkCompleteVisible:
        completeCount === 1 && (await this.domStep(() => complete.isVisible())),
      homeworkIncompleteVisible:
        incompleteCount === 1 && (await this.domStep(() => incomplete.isVisible())),
      domStatus: 'ABSENT_ROW',
      targetNameEqual: false,
    };
    if (rowCount === 1) {
      const raw = (
        await this.domStep(() => row.getAttribute('data-status', { timeout: 1000 }))
      )?.toUpperCase();
      facts.domStatus = raw && enumValues.domStatus.includes(raw) ? raw : 'UNSUPPORTED';
      // Return one boolean from the browser; never return the name or surrounding DOM text.
      facts.targetNameEqual = await this.domStep(() =>
        row.locator('.headman-journal__row-name').evaluate(
          (element, expected) =>
            Array.from(element.childNodes)
              .filter((node) => node.nodeType === Node.TEXT_NODE)
              .map((node) => node.textContent ?? '')
              .join('')
              .trim() === expected,
          this.target.displayName,
          { timeout: 1000 },
        ),
      );
    }
    const journal = this.page.locator('.headman-journal');
    facts.journalBusy =
      (await this.domStep(() => journal.count())) === 1 &&
      (await this.domStep(() => journal.getAttribute('aria-busy', { timeout: 1000 }))) === 'true';
    return facts;
  }

  async finish(info: TestInfo): Promise<void> {
    if (this.ended) return;
    // Stop intake. Existing observation waits abort together; late adapter completions are discarded.
    this.page.off('response', this.responseListener);
    let completed = true;
    const deadline = setTimeout(() => this.stop(true), 12_000);
    try {
      await Promise.allSettled([...this.pending]);
      this.active = false;
      if (!this.stopped) {
        try {
          this.add('DOM', await this.domFacts());
        } catch {
          completed = false;
          this.add('DOM', { diagnosticState: 'DOM_UNAVAILABLE' });
        }
      }
      if (!this.stopped && (info.status === 'failed' || info.status === 'timedOut')) {
        try {
          await this.failureReads();
        } catch {
          completed = false;
          this.add('FAILURE_READ', { diagnosticState: 'READ_UNAVAILABLE' });
        }
      }
    } finally {
      clearTimeout(deadline);
      // Synchronous stop/token clear precedes any awaited cleanup, even if disposal is delayed.
      this.stop(false);
      this.token = undefined;
      await Promise.allSettled([...this.pending]);
      try {
        await this.closeDiagnosticContext();
      } catch {
        completed = false;
      }
      // No final END claims settlement before owned cancellation/disposal has returned.
      const testStatus =
        info.status && enumValues.testStatus.includes(info.status) ? info.status : 'UNKNOWN';
      const facts = {
        testStatus,
        diagnosticCompleted: completed && !this.cancelled,
        droppedEventCount: count(this.dropped),
      };
      validateFacts(facts);
      this.events.push({ caseCode: this.caseCode, phase: 'END', facts });
      try {
        this.persist();
      } finally {
        this.ended = true;
        this.token = undefined;
      }
    }
  }
}

function hasMarkPermission(body: unknown): boolean {
  // Match the current fixture's closed shape: an array of objects with string code.
  return (
    Array.isArray(body) &&
    body.every((item) => typeof record(item)?.code === 'string') &&
    body.some((item) => record(item)?.code === 'MARK_ATTENDANCE')
  );
}
