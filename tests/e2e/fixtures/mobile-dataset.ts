import { randomBytes, randomUUID } from 'node:crypto';
import { setTimeout } from 'node:timers/promises';
import { expect, type APIRequestContext, type Page } from '@playwright/test';
import type { CurrentSession } from './pwa-auth';
import { TEST_USERS, type TestUser } from './users';
import { test as base, requirePrivateArtifacts } from './artifact-privacy';
import { datasetHttpFailure } from './safe-reporter';

type Role = Exclude<CurrentSession['activeRole'], null>;
type Model = Record<string, any>;
export interface SyntheticUser extends TestUser {
  id: number;
  displayName: string;
  telegramId: number;
}

/** Status-only diagnostics keep generated initial passwords and bearer tokens out of reports. */
export class Actor {
  constructor(
    readonly request: APIRequestContext,
    readonly token: string,
    readonly session: CurrentSession,
  ) {}
  headers() {
    return { Authorization: `Bearer ${this.token}` };
  }
  async call(
    method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
    path: string,
    data?: unknown,
    statuses = [200],
    extraHeaders: Record<string, string> = {},
  ): Promise<Model> {
    const response = await this.request.fetch(path, {
      method,
      data,
      headers: { ...this.headers(), ...extraHeaders },
    });
    if (!statuses.includes(response.status()))
      throw datasetHttpFailure(method, path, response.status());
    return response.status() === 204 ? {} : response.json();
  }
}

export async function admit(
  request: APIRequestContext,
  user: TestUser,
  role?: Role,
): Promise<Actor> {
  const login = () =>
    request.post('/api/auth/login', {
      data: { login: user.login, password: user.password },
    });
  let response = await login();
  if (response.status() === 429) {
    const retryAfter = response.headers()['retry-after'] ?? '';
    const seconds = /^[0-9]+$/.test(retryAfter) ? Number(retryAfter) : 0;
    if (Number.isSafeInteger(seconds) && seconds > 0 && seconds <= 60) {
      const retryAt = performance.now() + seconds * 1000;
      // Timer wake-up can precede its requested delay on the monotonic clock.
      for (let remaining = seconds * 1000; remaining > 0; remaining = retryAt - performance.now()) {
        await setTimeout(Math.ceil(remaining));
      }
      response = await login();
    }
  }
  if (response.status() !== 200)
    throw new Error(`Synthetic actor login: HTTP ${response.status()}`);
  let token: string = (await response.json()).accessToken;
  const read = await request.get('/api/auth/session', {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (read.status() !== 200) throw new Error(`Synthetic actor session: HTTP ${read.status()}`);
  let session: CurrentSession = await read.json();
  if (role && session.activeRole !== role) {
    const selected = await request.put('/api/auth/session/active-role', {
      headers: { Authorization: `Bearer ${token}` },
      data: { role, expectedSessionVersion: session.sessionVersion },
    });
    if (selected.status() !== 200)
      throw new Error(`Synthetic actor role ${role}: HTTP ${selected.status()}`);
    const body = await selected.json();
    token = body.accessToken;
    session = body.session;
  }
  return new Actor(request, token, session);
}

function items(body: Model, key: string): Model[] {
  return body._embedded?.[key] ?? body.content ?? [];
}
function moscowDate(instant = new Date()) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Moscow',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(instant);
  const value = (name: string) => parts.find((part) => part.type === name)!.value;
  return `${value('year')}-${value('month')}-${value('day')}`;
}
function shifted(date: string, days: number) {
  return new Date(Date.parse(`${date}T12:00:00Z`) + days * 86400000).toISOString().slice(0, 10);
}
function suffix() {
  return [...randomBytes(8)].map((byte) => 'АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЭЮЯ'[byte % 27]).join('');
}

export class MobileDataset {
  readonly marker = `RCT-cloud-20261011-${randomUUID()}`;
  readonly today: string;
  readonly tomorrow: string;
  group!: Model;
  otherGroup!: Model;
  semester!: Model;
  teacher!: SyntheticUser;
  student!: SyntheticUser;
  headman!: SyntheticUser;
  assistant!: SyntheticUser;
  foreignStudent!: SyntheticUser;
  limitedAdmin!: SyntheticUser;
  subject!: Model;
  lesson!: Model;
  homework!: Model;
  assistantGrant!: Model;
  headmanActor!: Actor;
  constructor(
    readonly request: APIRequestContext,
    readonly admin: Actor,
    teachingInstant?: Date,
    readonly minimumWeeklyReportWeeks: 0 | 18 = 0,
  ) {
    this.today = moscowDate(teachingInstant);
    this.tomorrow = shifted(this.today, 1);
  }

  async user(
    role: SyntheticUser['role'],
    groupId?: number,
    limited = false,
  ): Promise<SyntheticUser> {
    const lastName = `Проверка${suffix()}`;
    const telegramId = 8_000_000_000 + randomBytes(4).readUInt32BE(0);
    const body = await this.admin.call(
      'POST',
      '/api/academic/users',
      {
        lastName,
        firstName: 'Синтетика',
        middleName: null,
        role,
        groupId: groupId ?? null,
        employeeNumber: role === 'TEACHER' ? `E2E-${randomUUID().slice(0, 20)}` : null,
        telegramId: role === 'ADMIN' ? null : telegramId,
        adminAccess:
          role === 'ADMIN'
            ? { mode: limited ? 'LIMITED' : 'FULL', permissions: limited ? ['USERS_VIEW'] : [] }
            : null,
      },
      [201],
    );
    if (typeof body.initialPassword !== 'string' || typeof body.login !== 'string')
      throw new Error('Synthetic user creation omitted credentials');
    return {
      id: body.id,
      login: body.login,
      password: body.initialPassword,
      role,
      displayName: `${lastName} Синтетика`,
      telegramId,
      expectedLandingPath: '/app/',
    };
  }

  /** Only the untouched V2 seed in this explicit disposable CI lane may be expanded. */
  private async prepareWeeklyReportSeed(semesters: Model[], page: Model): Promise<Model> {
    const url = process.env.E2E_BASE_URL;
    if (
      this.minimumWeeklyReportWeeks !== 18 ||
      process.env.RCT_E2E_ASYNC_REPORT !== 'true' ||
      process.env.RCT_E2E_REMAINING5 !== 'true' ||
      process.env.RCT_E2E_SYNTHETIC_SEED !== 'true' ||
      process.env.CI !== 'true' ||
      process.env.GITHUB_ACTIONS !== 'true' ||
      process.env.RUNNER_ENVIRONMENT !== 'github-hosted' ||
      !['https://localhost:8443', 'https://127.0.0.1:8443', 'https://[::1]:8443'].includes(
        url ?? '',
      )
    )
      throw new Error('Weekly report seed preparation requires the disposable async CI lane');
    const isFreshSeed = (row: Model) =>
      (row.id === 1 || row.id === '1') &&
      row.name === 'Spring 2026' &&
      row.dateFrom === '2026-02-01' &&
      row.dateTo === '2026-06-30' &&
      row.semesterType === null &&
      row.academicYear === null &&
      row.active === true &&
      row.archived === false &&
      row.transition === 'NONE' &&
      row.releasePending === false &&
      row.activationScheduled === false &&
      (row.stateVersion === 0 || row.stateVersion === '0');
    if (
      semesters.length !== 1 ||
      page?.totalElements !== 1 ||
      page?.number !== 0 ||
      !isFreshSeed(semesters[0]) ||
      !isFreshSeed(await this.admin.call('GET', '/api/academic/semesters/1'))
    )
      throw new Error('Weekly report preparation requires the unchanged single V2 semester seed');

    // Mirror the existing semester lifecycle's real participant barrier, without importing its spec.
    const transition = async (action: 'ARCHIVE' | 'RESTORE') => {
      const operation = await this.admin.call(
        'POST',
        `/api/academic/semesters/1/${action === 'ARCHIVE' ? 'archive' : 'restore'}`,
        undefined,
        [200, 202, 503],
        { 'Idempotency-Key': randomUUID() },
      );
      expect(
        typeof operation.operationId === 'string' &&
          /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(
            operation.operationId,
          ) &&
          String(operation.semesterId) === '1' &&
          operation.action === action &&
          /^[1-9][0-9]{0,18}$/.test(String(operation.stateVersion)),
        'Weekly report seed command retains its own operation identity',
      ).toBe(true);
      await expect
        .poll(
          async () => {
            const status = await this.admin.call('GET', '/api/academic/semesters/1/archive/status');
            const current = status.operation;
            expect(
              String(status.semesterId) === '1' &&
                current?.operationId === operation.operationId &&
                String(current.semesterId) === '1' &&
                current.action === action &&
                String(current.stateVersion) === String(operation.stateVersion) &&
                String(current.stateVersion) === String(status.stateVersion) &&
                (current.operationState !== 'ERROR' || current.retryable === true),
              'Weekly report seed participant operation identity remains stable',
            ).toBe(true);
            const participant = action === 'ARCHIVE' ? 'READY' : 'RELEASED';
            return (
              current.operationState === 'COMPLETED' &&
              ['academic', 'schedule', 'attendance'].every(
                (name) => current[name] === participant,
              ) &&
              status.active === false &&
              status.archived === (action === 'ARCHIVE') &&
              status.transition === 'NONE' &&
              status.releasePending === false &&
              status.writeBlocked === (action === 'ARCHIVE')
            );
          },
          { timeout: 30000, message: 'Weekly report seed real participant barrier completes' },
        )
        .toBe(true);
    };
    await transition('ARCHIVE');
    const dateTo = shifted(this.today, 30);
    const expanded = await this.admin.call('PUT', '/api/academic/semesters/1', {
      name: 'Spring 2026',
      dateFrom: '2026-02-01',
      dateTo,
      semesterType: 'SPRING',
      academicYear: 2025,
    });
    const hasExpandedIdentity = (row: Model) =>
      String(row.id) === '1' &&
      row.dateFrom === '2026-02-01' &&
      row.dateTo === dateTo &&
      row.semesterType === 'SPRING' &&
      row.academicYear === 2025 &&
      row.transition === 'NONE' &&
      row.releasePending === false;
    expect(
      hasExpandedIdentity(expanded) && expanded.archived === true && expanded.active === false,
      'Weekly report seed expansion preserves its original history and settled archive',
    ).toBe(true);
    await transition('RESTORE');
    const activated = await this.admin.call('PATCH', '/api/academic/semesters/1/activate');
    expect(
      hasExpandedIdentity(activated) &&
        activated.active === true &&
        activated.archived === false &&
        activated.activationScheduled === false,
      'Weekly report seed is current only after restore participants release it',
    ).toBe(true);
    return activated;
  }

  async initialize() {
    const currentMonday = shifted(
      this.today,
      -((new Date(`${this.today}T12:00:00Z`).getUTCDay() + 6) % 7),
    );
    const reportStart = shifted(currentMonday, -17 * 7);
    const semesterResponse = await this.admin.call('GET', '/api/academic/semesters?size=100');
    const semesters = items(semesterResponse, 'semesterResponseList');
    this.semester = semesters.find(
      (row) => row.active && row.dateFrom <= this.today && row.dateTo >= this.tomorrow,
    )!;
    if (
      !this.semester &&
      this.minimumWeeklyReportWeeks === 18 &&
      semesters.some((row) => row.dateFrom <= shifted(this.today, 30) && row.dateTo >= reportStart)
    ) {
      this.semester = await this.prepareWeeklyReportSeed(semesters, semesterResponse.page);
    }
    if (!this.semester) {
      const month = Number(this.today.slice(5, 7));
      const teachingYesterday = shifted(this.today, -1);
      const realYesterday = shifted(moscowDate(), -1);
      this.semester = await this.admin.call(
        'POST',
        '/api/academic/semesters',
        {
          name: this.marker,
          // Historical fixtures must precede both educational jobs and real SQL/
          // membership cutoffs. Notification audiences also retain their real date.
          dateFrom:
            this.minimumWeeklyReportWeeks === 18
              ? reportStart
              : teachingYesterday < realYesterday
                ? teachingYesterday
                : realYesterday,
          dateTo: shifted(this.today, 30),
          semesterType: month >= 7 ? 'AUTUMN' : 'SPRING',
          academicYear: Number(this.today.slice(0, 4)) - (month >= 7 ? 0 : 1),
        },
        [201],
      );
      await this.admin.call('PATCH', `/api/academic/semesters/${this.semester.id}/activate`);
    }
    if (this.minimumWeeklyReportWeeks === 18 && this.semester.dateFrom > reportStart) {
      // Explicit serial disposable lane only. Preserve ordinary overlap validation;
      // an incompatible stand fails here rather than bypassing the domain with SQL.
      this.semester = await this.admin.call('PUT', `/api/academic/semesters/${this.semester.id}`, {
        name: this.semester.name,
        dateFrom: reportStart,
        dateTo: this.semester.dateTo,
        semesterType: this.semester.semesterType,
        academicYear: this.semester.academicYear,
      });
    }
    const group = () =>
      this.admin.call(
        'POST',
        '/api/academic/groups/registry',
        {
          alphabeticCode: suffix().slice(0, 4),
          // Course 1, registered programme digit 1–9 and ordinal 0–9; all 90 valid codes remain.
          numericCode: `1${10 + (randomBytes(1)[0] % 90)}`,
          trainingDurationYears: 4,
        },
        [201],
      );
    this.group = await group();
    this.otherGroup = await group();
    this.teacher = await this.user('TEACHER');
    this.student = await this.user('STUDENT', this.group.id);
    this.headman = await this.user('STUDENT', this.group.id);
    this.assistant = await this.user('STUDENT', this.group.id);
    this.foreignStudent = await this.user('STUDENT', this.otherGroup.id);
    this.limitedAdmin = await this.user('ADMIN', undefined, true);
    await this.admin.call('PUT', `/api/academic/groups/${this.group.id}/headman`, {
      studentId: this.headman.id,
      expectedHeadmanId: null,
    });
    this.headmanActor = await admit(this.request, this.headman, 'HEADMAN');
    this.subject = await this.headmanActor.call(
      'POST',
      '/api/academic/subjects',
      {
        name: this.marker,
        type: 'LECTURE',
        lessonTypes: ['LECTURE'],
        initialAssignments: [
          {
            teacherId: this.teacher.id,
            semesterId: this.semester.id,
            lessonType: 'LECTURE',
            validFrom: this.semester.dateFrom,
            validUntilExclusive: null,
          },
        ],
      },
      [201],
    );
    // Publication/adoption can be asynchronous; re-read actual authority instead of bypassing its fences.
    await expect
      .poll(
        async () => {
          this.subject = await this.headmanActor.call(
            'GET',
            `/api/academic/subjects/${this.subject.id}`,
          );
          return this.subject.assignments?.length ?? 0;
        },
        { timeout: 60000, message: 'Synthetic assignment adoption becomes published' },
      )
      .toBeGreaterThan(0);
    this.lesson = await this.headmanActor.call(
      'POST',
      '/api/schedule/one-off-lessons',
      {
        groupId: this.group.id,
        subjectId: this.subject.id,
        assignmentId: this.subject.assignments[0].id,
        date: this.today,
        lessonNumber: 1,
        classroom: 'Синтетическая аудитория',
      },
      [201, 200],
      { 'Idempotency-Key': randomUUID() },
    );
    const payload = {
      title: this.marker,
      description: 'Синтетическое задание без внешних файлов',
      link: null,
      subjectId: this.subject.id,
      groupId: this.group.id,
      semesterId: this.semester.id,
      lessonDate: this.tomorrow,
      lessonNumber: null,
      bindingMode: 'DATE',
      lessonType: 'LECTURE',
      requestKey: randomUUID(),
    };
    await expect
      .poll(
        async () => {
          this.homework = await this.headmanActor.call(
            'POST',
            '/api/academic/homeworks',
            payload,
            [201, 200, 202],
          );
          return this.homework.state !== 'PENDING' && this.homework.id != null;
        },
        { timeout: 60000, message: 'Synthetic homework publication becomes active' },
      )
      .toBe(true);
    this.assistantGrant = await this.headmanActor.call(
      'POST',
      '/api/academic/assistants',
      {
        groupId: this.group.id,
        studentId: this.assistant.id,
        permissions: ['MARK_ATTENDANCE'],
      },
      [201],
    );
  }
}

/** Browser role change goes through the shared profile UI and the real admission command. */
export async function selectPwaRole(page: Page, label: string) {
  if (!(await page.getByRole('heading', { name: 'Сменить роль', exact: true }).isVisible())) {
    await page.getByRole('button', { name: 'Профиль', exact: true }).click();
    await page.getByRole('button', { name: 'Сменить роль', exact: true }).click();
  }
  const changed = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/auth/session/active-role' &&
      response.request().method() === 'PUT',
  );
  await page.getByRole('button', { name: new RegExp(label) }).click();
  if ((await changed).status() !== 200) throw new Error('Browser role admission denied');
  await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible();
}

export const test = base.extend<
  {},
  {
    dataset: MobileDataset;
    activeHeadmanDataset: MobileDataset;
    teachingInstant: Date | undefined;
    minimumWeeklyReportWeeks: 0 | 18;
  }
>({
  teachingInstant: [undefined, { scope: 'worker', option: true }],
  minimumWeeklyReportWeeks: [0, { scope: 'worker', option: true }],
  page: async ({ page, teachingInstant }, use) => {
    // Account archive previews expire against real time, so their browser keeps that clock.
    if (
      process.env.RCT_E2E_REMAINING5 === 'true' &&
      process.env.RCT_E2E_ADMIN_LIFECYCLE !== 'true'
    ) {
      if (!teachingInstant) throw new Error('Remaining five teaching instant is required');
      // Start on the educational date while allowing Vue event timestamps to advance.
      // A frozen Date.now suppresses target handlers after ancestor capture listeners.
      await page.clock.install({ time: teachingInstant });
    }
    await use(page);
  },
  dataset: [
    async ({ playwright, teachingInstant, minimumWeeklyReportWeeks }, use, workerInfo) => {
      requirePrivateArtifacts(workerInfo);
      const baseURL = String(workerInfo.project.use.baseURL ?? 'https://localhost:8443');
      const url = new URL(baseURL);
      if (
        process.env.RCT_E2E_SYNTHETIC_SEED !== 'true' ||
        url.protocol !== 'https:' ||
        !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname) ||
        url.port !== '8443'
      ) {
        throw new Error(
          'Synthetic dataset requires RCT_E2E_SYNTHETIC_SEED=true and disposable HTTPS loopback:8443; private/local stands are forbidden',
        );
      }
      const request = await playwright.request.newContext({ baseURL, ignoreHTTPSErrors: true });
      try {
        const dataset = new MobileDataset(
          request,
          await admit(request, TEST_USERS.admin, 'ADMIN'),
          teachingInstant,
          minimumWeeklyReportWeeks,
        );
        await dataset.initialize();
        await use(dataset);
      } finally {
        await request.dispose();
      }
      // Root owns disposal of this task-labelled stack. Never delete unrelated existing data.
    },
    { scope: 'worker', timeout: 180000 },
  ],
  activeHeadmanDataset: [
    async ({ dataset }, use) => {
      // The real 60s scheduler activates the new PLANNED lesson. Only the HEADMAN
      // geo-block journey needs ACTIVE; keep this wait outside its 60s test body.
      await expect
        .poll(
          async () => {
            const lessons = items(
              await dataset.headmanActor.call(
                'GET',
                `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${dataset.today}&dateTo=${dataset.today}&size=100`,
              ),
              'lessonResponseList',
            );
            const lesson = lessons.find(
              (row) => Number(row.id) === Number(dataset.lesson.physicalLessonId),
            );
            return lesson?.status === 'ACTIVE' && lesson.current === true;
          },
          { timeout: 90000, intervals: [1000], message: 'Own HEADMAN lesson becomes active' },
        )
        .toBe(true);
      await use(dataset);
    },
    { scope: 'worker', timeout: 180000 },
  ],
});
export { expect };
