import { expect, type Page, type Locator, type Response } from '@playwright/test';
import { assertNoA11yCriticalOrSerious } from './axe';

const endpoints = {
  authority: /^\/api\/academic\/admin-access\/me$/,
  stats: /^\/api\/academic\/dashboard\/stats$/,
  activity: /^\/api\/academic\/dashboard\/activity$/,
  users: /^\/api\/academic\/users$/,
  groups: /^\/api\/academic\/groups\/registry$/,
  semesters: /^\/api\/academic\/semesters$/,
  teacherSemester: /^\/api\/v1\/teacher\/semester$/,
  teacherCatalog: /^\/api\/v1\/teacher\/catalog$/,
  journal: /^\/api\/v1\/teacher\/journal$/,
  teacherStats: /^\/api\/v1\/teacher\/stats$/,
};
type Read = keyof typeof endpoints;
type Data = Partial<Record<Read, unknown>>;
type Row = Record<string, unknown>;
const routes: Record<string, { owner: string; reads: Read[]; extra?: Read[]; permission?: string }> = {
  '/admin/dashboard': { owner: '.admin-page', reads: ['authority', 'stats', 'activity'], permission: 'DASHBOARD_VIEW' },
  '/admin/users': { owner: '.admin-page', reads: ['authority', 'users'], permission: 'USERS_VIEW' },
  '/admin/groups': { owner: '.admin-page', reads: ['authority', 'groups'], permission: 'GROUPS_VIEW' },
  '/admin/semesters': { owner: '.admin-page', reads: ['authority', 'semesters'], permission: 'SEMESTERS_VIEW' },
  '/teacher/journal': { owner: '[data-screen-id="WEB-TEACHER-ATTENDANCE"]', reads: ['teacherSemester', 'teacherCatalog'], extra: ['journal'] },
  '/teacher/statistics': { owner: '[data-screen-id="WEB-TEACHER-STATISTICS"]', reads: ['teacherSemester', 'teacherCatalog'], extra: ['teacherStats'] },
};

function intendedRoute(path: string) {
  const route = routes[path];
  if (!route) throw new Error('PENDING_VUE_ROUTE_ORACLE: this scenario needs its current Vue route, role intent and data-to-DOM oracle before browser actions');
  return route;
}

// Attach before navigation. Every required response must finish and decode, including
// paginated semester companions; headers or an unrelated successful request cannot pass.
export function observeWebPanelReads(page: Page, path: string) {
  const route = intendedRoute(path);
  const origin = new URL(page.url()).origin;
  const responses = new Map<Read, Response[]>();
  const consumed = new Map<Read, number>();
  const keys = [...route.reads, ...(route.extra ?? [])];
  const matches = (response: Response, key: Read) => {
    const url = new URL(response.url());
    return url.origin === origin && response.request().method() === 'GET' && endpoints[key].test(url.pathname);
  };
  const listener = (response: Response) => {
    for (const key of keys) if (matches(response, key)) responses.set(key, [...(responses.get(key) ?? []), response]);
  };
  page.on('response', listener);
  const read = async (key: Read) => {
    if (!keys.includes(key)) throw new Error('Read is outside the intended Vue feature');
    const index = consumed.get(key) ?? 0;
    const response = responses.get(key)?.[index] ?? await page.waitForResponse(candidate => matches(candidate, key));
    consumed.set(key, index + 1);
    expect(response.status(), 'Scoped feature read succeeds').toBe(200);
    expect(await response.finished(), 'Scoped feature body finishes').toBe(null);
    const body: unknown = await response.json();
    expect(body !== null && typeof body === 'object', 'Scoped feature data decodes').toBe(true);
    return body;
  };
  return {
    read,
    initial: async () => {
      const bodies: Data = {};
      for (const key of route.reads) bodies[key] = await read(key);
      return bodies;
    },
    dispose: () => page.off('response', listener),
  };
}

function record(value: unknown): Row {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid feature record');
  return value as Row;
}
function array(value: unknown): Row[] {
  if (!Array.isArray(value)) throw new Error('Invalid feature collection');
  return value.map(record);
}
function text(value: unknown): string {
  if (typeof value !== 'string' || !value.trim()) throw new Error('Missing feature identity');
  return value;
}
function collection(value: unknown): Row[] {
  if (Array.isArray(value)) return array(value);
  const body = record(value);
  if (Array.isArray(body.items)) return array(body.items);
  const embedded = body._embedded == null ? {} : record(body._embedded);
  const lists = Object.values(embedded).filter(Array.isArray);
  if (lists.length === 1) return array(lists[0]);
  if (lists.length === 0 && record(body.page).totalElements === 0) return [];
  throw new Error('Unconfirmed collection shape is not an empty feature');
}

export async function expectRenderedStat(owner: Locator, label: string, value: number, assertion = expect) {
  expect(Number.isFinite(value), 'Feature count is finite').toBe(true);
  const card = owner.locator('.admin-metric').filter({ has: owner.page().getByText(label, { exact: true }) });
  await assertion(card).toHaveCount(1);
  await assertion(card.locator('strong')).toHaveText(value.toLocaleString('ru-RU'));
}

async function rowsOrEmpty(owner: Locator, rows: Locator, values: string[], empty: string) {
  await expect(rows).toHaveCount(values.length);
  if (!values.length) await expect(owner.getByText(empty, { exact: true })).toBeVisible();
  for (let i = 0; i < values.length; i++) await expect(rows.nth(i).getByText(values[i]!, { exact: true })).toBeVisible();
}

async function renderedFeature(owner: Locator, path: string, data: Data, read: (key: Read) => Promise<unknown>) {
  if (path.startsWith('/admin/')) {
    const authority = record(data.authority);
    expect(authority.assigned, 'An administrative access profile is assigned').toBe(true);
    expect(Array.isArray(authority.permissions) && authority.permissions.includes(intendedRoute(path).permission),
      'Current server permissions authorize this feature').toBe(true);
  }
  if (path === '/admin/dashboard') {
    const stats = record(data.stats);
    for (const [label, key] of [['Студенты', 'totalStudents'], ['Преподаватели', 'totalTeachers'], ['Группы', 'totalGroups']]) {
      const count = stats[key!];
      if (typeof count !== 'number') throw new Error('Invalid dashboard count');
      await expectRenderedStat(owner, label!, count);
    }
    const activity = record(data.activity);
    record(record(activity.summary).metrics);
    expect(Array.isArray(activity.series), 'Activity series is decoded').toBe(true);
    await expect(owner.getByRole('heading', { name: 'Активность', exact: true })).toBeVisible();
  } else if (path === '/admin/users' || path === '/admin/groups') {
    const users = path === '/admin/users';
    const items = collection(users ? data.users : data.groups);
    const table = owner.getByRole('table', { name: users ? 'Реестр пользователей' : 'Реестр групп', exact: true });
    await rowsOrEmpty(owner, table.locator('tbody tr'), items.map(item => text(users ? item.login : item.name)),
      users ? 'Пользователи не найдены. Измени поиск или фильтры.' : 'Группы не найдены.');
  } else if (path === '/admin/semesters') {
    let body = data.semesters;
    const semesters = collection(body);
    if (!Array.isArray(body) && record(body).page != null) {
      let page = record(record(body).page);
      while (typeof page.number === 'number' && typeof page.totalPages === 'number' && page.number + 1 < page.totalPages) {
        const previous = page.number;
        body = await read('semesters');
        page = record(record(body).page);
        expect(page.number, 'Semester pages are contiguous').toBe(previous + 1);
        semesters.push(...collection(body));
      }
    }
    const table = owner.getByRole('table', { name: 'Учебные семестры', exact: true });
    await rowsOrEmpty(owner, table.locator('tbody tr'), semesters.map(item => text(item.name)), 'В этом разделе нет семестров.');
  } else {
    const catalog = record(data.teacherCatalog);
    const groups = array(catalog.groups);
    const group = owner.getByRole('combobox', { name: 'Группа', exact: true });
    await expect(group.locator('option')).toHaveText(groups.map(item => text(item.name)));
    if (!groups.length) {
      await expect(group).toBeDisabled();
      await expect(owner.getByRole('table')).toHaveCount(0);
      return;
    }
    const subjectOptions = array(catalog.subjectOptions).filter(item => item.groupId === groups[0]!.id);
    await expect(owner.getByRole('combobox', { name: 'Предмет', exact: true }).locator('option'))
      .toHaveText(subjectOptions.map(item => text(item.subjectName)));
    if (!subjectOptions.length || !Array.isArray(subjectOptions[0]!.lessonTypes) || !subjectOptions[0]!.lessonTypes.length) {
      await expect(owner.getByRole('table')).toHaveCount(0);
      return;
    }
    if (path === '/teacher/journal') {
      const journal = record(await read('journal'));
      const students = array(journal.students), lessons = array(journal.lessons);
      const table = owner.getByRole('table', { name: 'Посещаемость, только чтение', exact: true });
      if (!lessons.length) {
        await expect(table).toHaveCount(0);
        await expect(owner.getByText('За этот период пар нет.', { exact: true })).toBeVisible();
      } else {
        await expect(table.locator('tbody tr')).toHaveCount(students.length);
        for (let i = 0; i < students.length; i++)
          await expect(table.locator('tbody tr').nth(i).getByRole('rowheader')).toHaveText(text(students[i]!.displayName));
        for (const lesson of lessons)
          await expect(table.locator(`thead a[href="/teacher/lessons/${lesson.id}"]`)).toHaveCount(1);
      }
    } else {
      const stats = record(await read('teacherStats'));
      expect(stats.scope, 'Default statistics retain the requested student scope').toBe('students');
      const students = array(stats.students);
      const rows = owner.locator('.teacher-stats-table tbody tr');
      await expect(rows).toHaveCount(Math.max(students.length, 1));
      if (!students.length) await expect(rows).toHaveText('За этот период данных пока нет.');
      for (let i = 0; i < students.length; i++) {
        const row = rows.nth(i), student = students[i]!;
        await expect(row.getByRole('rowheader')).toHaveText(text(student.displayName));
        for (const [index, key] of ['present', 'presentOrExcused', 'excused', 'absent'].entries()) {
          const metric = record(student[key]);
          if (typeof metric.percent !== 'number') throw new Error('Invalid attendance metric');
          await expect(row.locator('td').nth(index).locator('span').nth(0)).toHaveText(metric.denominator ?
            metric.percent.toLocaleString('ru', { maximumFractionDigits: 1 }) + ' %' : 'Нет данных');
          await expect(row.locator('td').nth(index).locator('span').nth(1)).toHaveText(`${metric.numerator} / ${metric.denominator}`);
        }
      }
    }
  }
}

export async function expectWebPanelRoute(page: Page, path: string): Promise<void> {
  const route = intendedRoute(path); // Unsupported legacy intent fails before navigation/listeners.
  const reads = observeWebPanelReads(page, path);
  try {
    await page.goto(path);
    await expect(page).toHaveURL(url => url.pathname === path);
    const data = await reads.initial();
    const owner = page.locator('main#main').locator(route.owner);
    await expect(owner).toBeVisible();
    await renderedFeature(owner, path, data, reads.read);
    const navigation = page.getByRole('navigation', { name: 'Разделы', exact: true });
    const link = navigation.locator(`a[href="${path}"]`);
    await expect(link).toHaveCount(1);
    if (!await link.isVisible()) await page.getByRole('button', { name: 'Разделы', exact: true }).click();
    await expect(link).toBeVisible();
    await expect(link).toHaveAttribute('aria-current', 'page');
    await expect(owner).not.toHaveAttribute('aria-busy', 'true');
    await expect(owner.locator('[aria-busy="true"], [role="alert"], .web-error')).toHaveCount(0);
    await expect(page.getByRole('heading', { name: 'Страница не найдена', exact: true })).toHaveCount(0);
    await assertNoA11yCriticalOrSerious(page);
  } finally {
    reads.dispose();
  }
}
