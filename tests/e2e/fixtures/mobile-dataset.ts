import { randomBytes, randomUUID } from 'node:crypto';
import { test as base, expect, type APIRequestContext, type Page } from '@playwright/test';
import type { CurrentSession } from './pwa-auth';
import { TEST_USERS, type TestUser } from './users';
import { requirePrivateArtifacts } from './artifact-privacy';

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
      throw new Error(
        `${method} ${path}: HTTP ${response.status()}, expected ${statuses.join('/')}`,
      );
    return response.status() === 204 ? {} : response.json();
  }
}

export async function admit(
  request: APIRequestContext,
  user: TestUser,
  role?: Role,
): Promise<Actor> {
  const response = await request.post('/api/auth/login', {
    data: { login: user.login, password: user.password },
  });
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
function moscowDate() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Moscow',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts();
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
  readonly today = moscowDate();
  readonly tomorrow = shifted(this.today, 1);
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
  ) {}

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

  async initialize() {
    const semesters = items(
      await this.admin.call('GET', '/api/academic/semesters?size=100'),
      'semesterResponseList',
    );
    this.semester = semesters.find(
      (row) => row.active && row.dateFrom <= this.today && row.dateTo >= this.tomorrow,
    )!;
    if (!this.semester) {
      const month = Number(this.today.slice(5, 7));
      this.semester = await this.admin.call(
        'POST',
        '/api/academic/semesters',
        {
          name: this.marker,
          dateFrom: shifted(this.today, -1),
          dateTo: shifted(this.today, 30),
          semesterType: month >= 7 ? 'AUTUMN' : 'SPRING',
          academicYear: Number(this.today.slice(0, 4)) - (month >= 7 ? 0 : 1),
        },
        [201],
      );
      await this.admin.call('PATCH', `/api/academic/semesters/${this.semester.id}/activate`);
    }
    const group = () =>
      this.admin.call(
        'POST',
        '/api/academic/groups/registry',
        {
          alphabeticCode: suffix().slice(0, 4),
          numericCode: `1${randomBytes(1)[0] % 100}`.padEnd(3, '0'),
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

export const test = base.extend<{}, { dataset: MobileDataset }>({
  dataset: [
    async ({ playwright }, use, workerInfo) => {
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
        const dataset = new MobileDataset(request, await admit(request, TEST_USERS.admin, 'ADMIN'));
        await dataset.initialize();
        await use(dataset);
      } finally {
        await request.dispose();
      }
      // Root owns disposal of this task-labelled stack. Never delete unrelated existing data.
    },
    { scope: 'worker', timeout: 180000 },
  ],
});
export { expect };
