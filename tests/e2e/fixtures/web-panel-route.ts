import { expect, type Page, type Locator, type Response } from '@playwright/test';
import { assertNoA11yCriticalOrSerious } from './axe';

const endpoints = {
  stats: /^\/api\/academic\/dashboard\/stats$/,
  groups: /^\/api\/academic\/groups$/,
  users: /^\/api\/academic\/users$/,
  semesters: /^\/api\/academic\/semesters$/,
  subjects: /^\/api\/academic\/subjects$/,
  assignments: /^\/api\/academic\/assignments\/my$/,
  members: /^\/api\/academic\/groups\/my\/members$/,
  lessons: /^\/api\/schedule\/groups\/[1-9][0-9]*\/lessons$/,
  excuses: /^\/api\/attendance\/excuses\/group\/[1-9][0-9]*$/,
  pendingLate: /^\/api\/attendance\/late-checkin\/pending$/,
  late: /^\/api\/attendance\/late-checkin\/group\/[1-9][0-9]*$/,
  policy: /^\/api\/schedule\/academic-week-policy$/,
  items: /^\/api\/schedule\/items$/,
  records: /^\/api\/attendance\/reports\/student\/records$/,
  studentStats: /^\/api\/attendance\/reports\/student\/stats$/,
  threshold: /^\/api\/academic\/thresholds\/resolve$/,
  ownExcuses: /^\/api\/attendance\/excuses\/me$/,
};
type Read = keyof typeof endpoints;
const routes: Record<string, { owner: string; reads: Read[] }> = {
  '/admin/dashboard': { owner: 'app-admin-dashboard', reads: ['stats', 'groups'] },
  '/admin/users': { owner: 'app-users-page', reads: ['users', 'groups'] },
  '/admin/groups': { owner: 'app-groups-page', reads: ['groups', 'users'] },
  '/admin/semesters': { owner: 'app-semesters-page', reads: ['semesters'] },
  '/headman/dashboard': { owner: 'app-headman-dashboard', reads: ['members', 'lessons', 'excuses', 'pendingLate'] },
  '/headman/schedule': { owner: 'app-headman-schedule', reads: ['semesters', 'subjects', 'policy'] },
  '/headman/excuses': { owner: 'app-headman-excuses', reads: ['excuses'] },
  '/headman/late-checkin': { owner: 'app-headman-late-checkin', reads: ['late'] },
  '/student/schedule': { owner: 'app-student-schedule', reads: ['lessons', 'records'] },
  '/student/stats': { owner: 'app-student-stats', reads: ['studentStats', 'threshold'] },
  '/student/excuses': { owner: 'app-student-excuses', reads: ['ownExcuses'] },
  '/teacher/journal': { owner: 'app-journal-page', reads: ['assignments', 'groups', 'subjects', 'semesters'] },
  '/teacher/stats': { owner: 'app-stats-page', reads: ['assignments', 'groups', 'subjects', 'semesters'] },
};

// Attach before navigation: forkJoin companions may finish before the first body is read.
// Success headers alone are insufficient; each response must finish and decode.
export function observeWebPanelReads(page: Page, path: string) {
  const route = routes[path];
  if (!route) throw new Error('Unregistered intended web-panel route');
  const origin = new URL(page.url()).origin;
  const responses = new Map<Read, Response>();
  const matches = (response: Response, key: Read) => {
    const url = new URL(response.url());
    return url.origin === origin && response.request().method() === 'GET' && endpoints[key].test(url.pathname);
  };
  const listener = (response: Response) => {
    for (const key of [...route.reads, ...(path === '/headman/schedule' ? ['items' as const] : [])]) {
      if (!responses.has(key) && matches(response, key)) responses.set(key, response);
    }
  };
  page.on('response', listener);
  const read = async (key: Read) => {
    const response = responses.get(key) ?? await page.waitForResponse((candidate) => matches(candidate, key));
    expect(response.status() === 200, 'Scoped feature read succeeds').toBe(true);
    expect(await response.finished() === null, 'Scoped feature body finishes').toBe(true);
    const body = await response.json();
    expect(body !== null && typeof body === 'object', 'Scoped feature data decodes').toBe(true);
    return body;
  };
  return {
    read,
    initial: async () => {
      const bodies: Partial<Record<Read, any>> = {};
      for (const key of route.reads) bodies[key] = await read(key);
      return bodies;
    },
    dispose: () => page.off('response', listener),
  };
}

function list(body: any): any[] {
  if (Array.isArray(body)) return body;
  const embedded = body?._embedded;
  const value = embedded && Object.values(embedded)[0];
  if (Array.isArray(value)) return value;
  // Empty HAL collections may omit _embedded entirely.
  expect(!embedded || Object.keys(embedded).length === 0, 'Feature collection shape').toBe(true);
  return [];
}

export async function expectRenderedStat(owner: Locator, label: string, value: number, assertion = expect) {
  expect(Number.isFinite(value), 'Feature count is finite').toBe(true);
  const card = owner.locator('app-stat-card').filter({ has: owner.page().getByText(label, { exact: true }) });
  await assertion(card).toHaveCount(1);
  // aria-label carries the final value while the visible counter is animating.
  const number = card.locator('.stat-card__number');
  await assertion(number).toHaveAttribute('aria-label', String(value));
  await assertion(number).toHaveText(String(value));
}

async function rowsOrEmpty(owner: Locator, rows: Locator, values: string[], empty: string) {
  await expect(rows).toHaveCount(values.length);
  if (!values.length) await expect(owner.getByText(empty, { exact: true })).toBeVisible();
  for (let i = 0; i < values.length; i++) {
    expect(typeof values[i] === 'string' && values[i].length > 0, 'Rendered feature identity exists').toBe(true);
    await expect(rows.nth(i).getByText(values[i], { exact: true })).toBeVisible();
  }
}

async function renderedFeature(page: Page, owner: Locator, path: string, data: Partial<Record<Read, any>>,
  read: (key: Read) => Promise<any>) {
  if (path === '/admin/dashboard') {
    for (const [label, key] of [['Студентов', 'totalStudents'], ['Преподавателей', 'totalTeachers'],
      ['Групп всего', 'totalGroups'], ['Активных групп', 'activeGroups']]) {
      await expectRenderedStat(owner, label, data.stats[key]);
    }
    const groups = list(data.groups).sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? '')).slice(0, 8);
    const region = owner.getByRole('region', { name: 'Недавние группы', exact: true });
    await expect(region.locator('tbody a')).toHaveCount(groups.length);
    for (let i = 0; i < groups.length; i++) await expect(region.locator('tbody a').nth(i)).toHaveText(groups[i].name);
    if (!groups.length) await expect(region.getByText('Нет данных о группах.', { exact: true })).toBeVisible();
  } else if (path.startsWith('/admin/')) {
    const [key, field, empty] = path === '/admin/users' ? ['users', 'login', 'Пользователи не найдены'] :
      path === '/admin/groups' ? ['groups', 'name', 'Групп нет'] : ['semesters', 'name', 'Семестры не созданы'];
    await rowsOrEmpty(owner, owner.locator('mat-row:visible'), list(data[key as Read]).map(item => item[field]), empty);
  } else if (path === '/headman/dashboard') {
    await expectRenderedStat(owner, 'Студентов в группе', data.members.page?.totalElements || list(data.members).length);
    await expectRenderedStat(owner, 'Тикеты о пропуске', list(data.excuses).length);
    await expectRenderedStat(owner, 'Запросы поздней отметки', list(data.pendingLate).length);
    const lessons = list(data.lessons);
    if (lessons.length) await expect(owner.locator('.lesson-name')).toHaveText(lessons[0].subjectName);
    else await expect(owner.getByText('Нет пары сегодня', { exact: true })).toBeVisible();
  } else if (path === '/headman/schedule') {
    if (!list(data.semesters).some(item => item.active === true)) {
      await expect(owner.getByRole('heading', { name: 'Нет активного семестра', exact: true })).toBeVisible();
    } else {
      await expect(owner.locator('.current-week-banner strong')).toHaveText(String(data.policy.label) + '-я');
      const items = list(await read('items')).map(item => item.content ?? item).filter(item => item.active !== false);
      await expect(owner.locator('.schedule-matrix')).toBeVisible();
      await expect(owner.locator('.cell-entry')).toHaveCount(items.length);
      for (const item of items) {
        const subject = list(data.subjects).find(subject => subject.id === item.subjectId);
        expect(!!subject, 'Schedule subject is owned loaded data').toBe(true);
        await expect(owner.getByRole('button', { name: 'Редактировать ' + subject.name, exact: true }).first()).toBeVisible();
      }
    }
  } else if (path === '/headman/excuses') {
    const tickets = list(data.excuses);
    const pending = tickets.filter(item => item.status === 'submitted');
    await rowsOrEmpty(owner, owner.getByTestId('excuse-card'), pending.map(item => item.studentName), 'Нет заявок на рассмотрении');
    await expect(owner.locator('.excuse-card--resolved')).toHaveCount(tickets.length - pending.length);
  } else if (path === '/headman/late-checkin') {
    await rowsOrEmpty(owner, owner.locator('.lcr-card'), list(data.late).map(item => item.studentName), 'Нет активных запросов');
  } else if (path === '/student/schedule') {
    await owner.getByRole('tab', { name: 'Пн', exact: true }).click();
    const lessons = list(data.lessons).filter(item => item.dayOfWeek === 1).sort((a, b) => a.lessonNumber - b.lessonNumber);
    await expect(owner.locator('app-lesson-row')).toHaveCount(lessons.length);
    for (const lesson of lessons) await expect(owner.locator(`[data-lesson-id="${lesson.id}"]`)).toBeVisible();
    if (!lessons.length) await expect(owner.getByRole('heading', { name: 'Занятий нет', exact: true })).toBeVisible();
  } else if (path === '/student/stats') {
    const stats = data.studentStats;
    if (stats.overall) {
      const card = owner.locator('app-student-overall-card');
      await expect(card.locator('.overall-card__stat').filter({ hasText: 'Всего занятий' }).locator('.overall-card__value')).toHaveText(String(stats.overall.total));
      await expect(card.locator('.overall-card__percentage')).toHaveText(String(stats.overall.percentage) + '%');
    }
    expect(Array.isArray(stats.subjects), 'Subject statistics shape').toBe(true);
    await expect(owner.locator('app-student-subject-chart')).toHaveCount(stats.subjects.length);
    for (const subject of stats.subjects) await expect(owner.getByRole('img', { name: subject.subjectName + ' — статистика посещаемости', exact: true })).toBeVisible();
    if (!stats.subjects.length) await expect(owner.getByRole('heading', { name: 'Данных пока нет', exact: true })).toBeVisible();
  } else if (path === '/student/excuses') {
    const tickets = list(data.ownExcuses).filter(item => ['draft', 'submitted'].includes(item.status));
    const rows = owner.locator('button.excuse-table__row');
    await expect(rows).toHaveCount(tickets.length);
    if (!tickets.length) await expect(owner.getByRole('heading', { name: 'Нет активных тикетов', exact: true })).toBeVisible();
    for (let i = 0; i < tickets.length; i++) {
      await rows.nth(i).click();
      await expect(owner.getByRole('region', { name: 'Детали тикета ' + tickets[i].id, exact: true })).toBeVisible();
      await rows.nth(i).click();
    }
  } else {
    const assigned = new Set(list(data.assignments).map(item => item.groupId));
    const groups = list(data.groups).filter(item => assigned.has(item.id));
    const select = owner.getByRole('combobox', { name: 'Группа', exact: true });
    await select.click();
    const options = page.getByRole('option');
    await expect(options).toHaveCount(groups.length);
    for (let i = 0; i < groups.length; i++) await expect(options.nth(i)).toHaveText(groups[i].name);
    await page.keyboard.press('Escape');
  }
}

export async function expectWebPanelRoute(page: Page, path: string): Promise<void> {
  const route = routes[path];
  const reads = observeWebPanelReads(page, path);
  try {
    await page.goto(path);
    await expect(page).toHaveURL((url) => url.pathname === path);
    const data = await reads.initial();
    const owner = page.locator('main').locator(route.owner);
    await expect(owner).toBeVisible();
    await renderedFeature(page, owner, path, data, reads.read);
    await expect(owner.locator('mat-spinner, mat-progress-bar, .stat-card__skeleton, .table-card__skeleton, .skeleton-row, .skeleton-grid, .skeleton-card, .schedule-skeleton')).toHaveCount(0);
    await expect(page.getByRole('heading', { name: 'Страница не найдена', exact: true })).toHaveCount(0);
    await expect(owner.locator('[role="alert"], .page-error, .dashboard-error, .dashboard__error')).toHaveCount(0);
    await assertNoA11yCriticalOrSerious(page);
  } finally {
    reads.dispose();
  }
}
