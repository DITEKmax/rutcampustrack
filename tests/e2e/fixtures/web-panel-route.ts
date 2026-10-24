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
  studentSession: /^\/api\/v1\/student\/session$/,
  studentSchedule: /^\/api\/v1\/student\/schedule$/,
  studentStats: /^\/api\/v1\/student\/statistics$/,
  studentRequests: /^\/api\/v1\/student\/requests$/,
  studentOptions: /^\/api\/v1\/student\/requests\/options$/,
  studentDetail: /^\/api\/v1\/student\/requests\/(?!options$)[A-Za-z0-9_-]+$/,
  headmanLessons: /^\/api\/schedule\/groups\/[1-9][0-9]*\/lessons$/,
  subjects: /^\/api\/academic\/subjects$/,
  excuseCount: /^\/api\/attendance\/requests$/,
  lateCount: /^\/api\/attendance\/requests$/,
  groupRequests: /^\/api\/attendance\/requests$/,
  homeworks: /^\/api\/academic\/homeworks$/,
  lessonAttendance: /^\/api\/attendance\/reports\/lesson\/[1-9][0-9]*$/,
  lessonActions: /^\/api\/attendance\/reports\/lesson\/[1-9][0-9]*\/actions$/,
  policy: /^\/api\/schedule\/academic-week-policy$/,
  slots: /^\/api\/schedule\/lesson-slots$/,
  items: /^\/api\/schedule\/items$/,
  assignments: /^\/api\/academic\/assignments$/,
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
  '/student/schedule': { owner: '[data-screen-id="WEB-STUDENT-SCHEDULE"]', reads: ['studentSession'], extra: ['studentSchedule'] },
  '/student/statistics': { owner: '[data-screen-id="WEB-STUDENT-STATISTICS"]', reads: ['studentSession'], extra: ['studentStats'] },
  '/student/requests': { owner: '[data-screen-id="WEB-STUDENT-REQUESTS"]', reads: ['studentSession'], extra: ['studentRequests', 'studentOptions', 'studentDetail'] },
  '/headman': { owner: '.headman-page', reads: ['semesters', 'headmanLessons', 'subjects', 'excuseCount', 'lateCount'], extra: ['homeworks', 'lessonAttendance', 'lessonActions'] },
  '/headman/schedule': { owner: '.headman-page', reads: ['semesters', 'slots', 'policy'], extra: ['items', 'assignments'] },
  '/headman/requests': { owner: '.headman-page', reads: ['groupRequests'] },
};

function intendedRoute(path: string) {
  const url = new URL(path, 'https://web-fixture.invalid');
  const route = routes[url.pathname];
  const kind = url.searchParams.get('type');
  if (!route || url.origin !== 'https://web-fixture.invalid' || url.hash ||
    [...url.searchParams.keys()].some(key => key !== 'type') ||
    (url.search && (url.pathname !== '/headman/requests' || !['EXCUSE', 'LATE_CHECKIN'].includes(kind ?? ''))) ||
    url.searchParams.getAll('type').length > 1)
    throw new Error('PENDING_VUE_ROUTE_ORACLE: this scenario needs its current Vue route, role intent and data-to-DOM oracle before browser actions');
  return { ...route, pathname: url.pathname, kind };
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
    if (url.origin !== origin || response.request().method() !== 'GET' || !endpoints[key].test(url.pathname)) return false;
    if (key === 'excuseCount' || key === 'lateCount')
      return url.searchParams.get('bucket') === 'OPEN' && url.searchParams.get('size') === '1' &&
        url.searchParams.get('type') === (key === 'excuseCount' ? 'EXCUSE' : 'LATE_CHECKIN');
    if (key === 'groupRequests')
      return url.searchParams.get('bucket') === 'OPEN' && url.searchParams.get('size') === '20' &&
        url.searchParams.get('page') === '0' && url.searchParams.get('type') === route.kind;
    if (key === 'studentRequests') return url.searchParams.get('bucket') === 'OPEN' &&
      url.searchParams.get('size') === '10' && url.searchParams.get('page') === '0';
    return true;
  };
  const listener = (response: Response) => {
    for (const key of keys) if (matches(response, key)) responses.set(key, [...(responses.get(key) ?? []), response]);
  };
  page.on('response', listener);
  const read = async (key: Read) => {
    if (!keys.includes(key)) throw new Error('Read is outside the intended Vue feature');
    const index = consumed.get(key) ?? 0;
    consumed.set(key, index + 1);
    const response = responses.get(key)?.[index] ?? await page.waitForResponse(candidate =>
      matches(candidate, key) && responses.get(key)?.[index] === candidate);
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
  if (Array.isArray(body.content)) return array(body.content);
  const embedded = body._embedded == null ? {} : record(body._embedded);
  const lists = Object.values(embedded).filter(Array.isArray);
  if (lists.length === 1) return array(lists[0]);
  if (lists.length === 0 && record(body.page).totalElements === 0) return [];
  throw new Error('Unconfirmed collection shape is not an empty feature');
}

async function allPages(value: unknown, key: Read, read: (key: Read) => Promise<unknown>) {
  const items = collection(value);
  for (let count = 0; !Array.isArray(value) && record(value).page != null; count++) {
    if (count >= 100) throw new Error('Feature pagination exceeds the client bound');
    const page = record(record(value).page);
    if (typeof page.number !== 'number' || !Number.isSafeInteger(page.number) || page.number < 0 ||
      typeof page.totalPages !== 'number' || !Number.isSafeInteger(page.totalPages) || page.totalPages < 0)
      throw new Error('Invalid feature page boundary');
    if (page.number + 1 >= page.totalPages) break;
    value = await read(key);
    expect(record(record(value).page).number, 'Feature pages are contiguous').toBe(page.number + 1);
    items.push(...collection(value));
  }
  return items.map(item => item.content && !Array.isArray(item.content) && typeof item.content === 'object' ? record(item.content) : item);
}

export async function expectRenderedStat(owner: Locator, label: string, value: number, assertion = expect) {
  expect(Number.isSafeInteger(value) && value >= 0, 'Feature count is a non-negative integer').toBe(true);
  const card = owner.locator('.admin-metric').filter({ has: owner.page().getByText(label, { exact: true }) });
  await assertion(card).toHaveCount(1);
  await assertion(card.locator('strong')).toHaveText(value.toLocaleString('ru-RU'));
}

async function rowsOrEmpty(owner: Locator, rows: Locator, values: string[], empty: string) {
  await expect(rows).toHaveCount(values.length);
  if (!values.length) await expect(owner.getByText(empty, { exact: true })).toBeVisible();
  for (let i = 0; i < values.length; i++) await expect(rows.nth(i).getByText(values[i]!, { exact: true })).toBeVisible();
}

async function renderedStudent(owner: Locator, path: string, data: Data, read: (key: Read) => Promise<unknown>) {
  const session = record(data.studentSession);
  expect(session.activeRole, 'Student data belongs to the explicitly active role').toBe('STUDENT');
  if (!session.semester) {
    if (path === '/student/requests') throw new Error('Open-request scenario requires an active-semester fixture; archive is a separate intent');
    await expect(owner.getByText('Активного семестра пока нет.', { exact: true })).toBeVisible();
    await expect(owner.locator('.student-lesson, .student-subjects-table')).toHaveCount(0);
    return;
  }
  if (path === '/student/schedule') {
    const schedule = record(await read('studentSchedule'));
    expect(record(schedule.semester).id, 'Schedule belongs to the current semester').toBe(record(session.semester).id);
    const date = owner.getByLabel('Дата', { exact: true });
    await expect(date).toHaveValue(/^\d{4}-\d{2}-\d{2}$/);
    const start = new Date((await date.inputValue()) + 'T12:00:00Z');
    start.setUTCDate(start.getUTCDate() - (start.getUTCDay() + 6) % 7);
    const first = start.toISOString().slice(0, 10);
    start.setUTCDate(start.getUTCDate() + 6);
    const last = start.toISOString().slice(0, 10);
    const lessons = array(schedule.lessons).filter(lesson => text(lesson.date) >= first && text(lesson.date) <= last)
      .sort((a, b) => text(a.date).localeCompare(text(b.date)));
    const cards = owner.locator('.student-lesson');
    await expect(cards).toHaveCount(lessons.length);
    for (let i = 0; i < lessons.length; i++) {
      const lesson = lessons[i]!, card = cards.nth(i);
      await expect(card.getByRole('heading', { level: 3 })).toHaveText(text(record(lesson.subject).name));
      await expect(card.locator('.student-lesson-time')).toHaveText(`${text(lesson.startsAt).slice(0, 5)}–${text(lesson.endsAt).slice(0, 5)}`);
      const room = record(lesson.room).current;
      if (room) await expect(card.locator('.student-lesson-content')).toContainText(text(room));
      if (lesson.status === 'CANCELLED') await expect(card.locator('.student-lesson-content')).toContainText('Пара отменена');
    }
    await expect(owner.getByText('Пар нет.', { exact: true })).toHaveCount(7 - new Set(lessons.map(lesson => lesson.date)).size);
  } else if (path === '/student/statistics') {
    const stats = record(await read('studentStats')), metrics = record(stats.metrics);
    const format = (value: unknown) => {
      if (value == null) return '—';
      if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 100)
        throw new Error('Invalid student metric');
      return value.toLocaleString('ru-RU', { maximumFractionDigits: 1 }) + ' %';
    };
    expect(typeof metrics.held === 'number' && Number.isSafeInteger(metrics.held) && metrics.held >= 0).toBe(true);
    for (const [key, label] of [['present', 'Посещено'], ['excused', 'Уважительная причина пропуска'],
      ['presentOrExcused', 'Посещено и уважительная причина пропуска'], ['absent', 'Пропущено']]) {
      const value = record(metrics[key!]);
      expect(typeof value.count === 'number' && Number.isSafeInteger(value.count) && value.count >= 0 && value.count <= Number(metrics.held)).toBe(true);
      const tile = owner.locator('.student-metrics > div').filter({ has: owner.getByText(label!, { exact: true }) });
      await expect(tile.locator('dd').nth(0)).toHaveText(format(value.percent));
      await expect(tile.locator('dd').nth(1)).toHaveText(`${value.count} / ${metrics.held}`);
    }
    const subjects = array(stats.subjects).sort((a, b) => text(a.name).localeCompare(text(b.name), 'ru'));
    const rows = owner.locator('.student-subjects-table tbody tr');
    await expect(rows).toHaveCount(Math.max(subjects.length, 1));
    if (!subjects.length) await expect(rows).toHaveText('Предметов с такими условиями нет.');
    for (let i = 0; i < subjects.length; i++) {
      const subject = subjects[i]!, row = rows.nth(i), values = record(subject.metrics);
      await expect(row.getByRole('rowheader')).toHaveText(text(subject.name));
      for (const [column, key] of ['present', 'presentOrExcused', 'excused', 'absent'].entries()) {
        const metric = record(values[key]), cell = row.locator('td').nth(column);
        await expect(cell).toContainText(format(metric.percent));
        await expect(cell.locator('small')).toHaveText(`${metric.count} / ${values.held}`);
      }
    }
  } else {
    const [page, options] = await Promise.all([read('studentRequests').then(record), read('studentOptions').then(record)]);
    const summaries = array(page.content);
    expect(summaries.length <= 10 && new Set(summaries.map(summary => summary.id)).size === summaries.length,
      'The owned first request page respects its size/identity bounds').toBe(true);
    const details = await Promise.all(summaries.map(() => read('studentDetail').then(record)));
    expect(details.map(detail => text(record(detail.summary).id)).sort()).toEqual(summaries.map(summary => text(summary.id)).sort());
    const cards = owner.locator('.student-request-card');
    await expect(cards).toHaveCount(summaries.length);
    if (!summaries.length) await expect(owner.getByText('Нет заявок на рассмотрении.', { exact: true })).toBeVisible();
    for (const summary of summaries) {
      const card = cards.filter({ has: owner.getByRole('heading', { name: 'Заявка ' + text(summary.id), exact: true }) });
      const detail = details.find(value => record(value.summary).id === summary.id)!;
      expect(record(detail.summary).kind).toBe(summary.kind);
      expect(record(detail.summary).status).toBe(summary.status);
      await expect(card).toHaveCount(1);
      await expect(card.locator('.student-request-status')).toHaveText('На рассмотрении');
      expect(summary.status, 'OPEN response cannot render a resolved request as pending').toBe('PENDING');
      await expect(card.getByText(summary.kind === 'EXCUSE' ? 'Уважительная причина' : 'Забыл отметиться', { exact: true })).toBeVisible();
      await card.getByRole('button', { name: 'Раскрыть заявку ' + text(summary.id), exact: true }).click();
      await expect(card.locator('.student-request-lesson')).toHaveCount(array(record(detail.summary).lessons).length);
      await expect(card.locator('[role="alert"]')).toHaveCount(0);
      await card.getByRole('button', { name: 'Свернуть заявку ' + text(summary.id), exact: true }).click();
    }
    await expect(owner.getByRole('button', { name: `На рассмотрении (${page.totalElements})`, exact: true })).toHaveAttribute('aria-pressed', 'true');
    if (session.readOnly) await expect(owner.getByRole('button', { name: 'Подать на «у»', exact: true })).toBeDisabled();
    if (session.readOnly || options.budget == null || Number(record(options.budget).remaining) <= 0)
      await expect(owner.getByRole('button', { name: 'Оспорить «н»', exact: true })).toBeDisabled();
  }
}

async function renderedHeadman(owner: Locator, path: string, kind: string | null, data: Data, read: (key: Read) => Promise<unknown>) {
  if (path === '/headman/requests') {
    const page = record(data.groupRequests), requests = array(page.content);
    await expect(owner.getByRole('combobox', { name: 'Очередь', exact: true })).toHaveValue('OPEN');
    await expect(owner.getByRole('combobox', { name: 'Тип', exact: true })).toHaveValue(kind ?? '');
    const rows = owner.locator('table tbody tr');
    await expect(rows).toHaveCount(requests.length);
    if (!requests.length) await expect(owner.getByText('Заявок по этим условиям нет.', { exact: true })).toBeVisible();
    for (let i = 0; i < requests.length; i++) {
      const request = requests[i]!, row = rows.nth(i);
      if (kind) expect(request.kind, 'Request kind remains independently scoped').toBe(kind);
      expect(request.status, 'Open queue contains pending requests').toBe('PENDING');
      await expect(row.getByRole('rowheader')).toHaveText(text(request.studentName));
      await expect(row.locator('td').nth(0)).toContainText(request.kind === 'EXCUSE' ? 'Уважительная причина' : 'Апелляция отметки');
      await expect(row.locator('td').nth(2)).toContainText(String(request.lessonCount));
      await expect(row.locator('td').nth(3)).toHaveText('На рассмотрении');
    }
    return;
  }
  const semesters = await allPages(data.semesters, 'semesters', read);
  if (path === '/headman/schedule') {
    const selected = semesters.find(semester => semester.active) ?? semesters[0];
    if (!selected) throw new Error('Schedule scenario requires an available-semester fixture');
    const slots = array(record(data.slots).slots), policy = record(data.policy);
    const [items, assignments] = await Promise.all([read('items').then(value => allPages(value, 'items', read)),
      read('assignments').then(value => allPages(value, 'assignments', read))]);
    await expect(owner.getByRole('combobox', { name: 'Семестр', exact: true })).toHaveValue(String(selected.id));
    await expect(owner.getByRole('combobox', { name: 'Семестр', exact: true }).locator('option'))
      .toHaveText(semesters.map(semester => `${text(semester.name)}${semester.active ? '' : ' · архив'}`));
    await expect(owner.getByText(`Текущая неделя · ${policy.label} · ${policy.weekStart} — ${policy.weekEnd}`, { exact: true })).toBeVisible();
    const rows = owner.locator('.headman-schedule tbody tr');
    await expect(rows).toHaveCount(slots.length);
    for (let i = 0; i < slots.length; i++) {
      const slot = slots[i]!, row = rows.nth(i);
      await expect(row.getByRole('rowheader')).toContainText(`${slot.lessonNumber} пара`);
      await expect(row.getByRole('rowheader')).toContainText(`${text(slot.startTime).slice(0, 5)} — ${text(slot.endTime).slice(0, 5)}`);
      await expect(row.locator('td')).toHaveCount(6);
      for (let day = 1; day <= 6; day++) {
        const entries = items.filter(item => item.active && item.dayOfWeek === day && item.lessonNumber === slot.lessonNumber);
        const buttons = row.locator('td').nth(day - 1).locator('button.headman-slot:not(.headman-slot-free)');
        await expect(buttons).toHaveCount(entries.length);
        for (let index = 0; index < entries.length; index++) {
          const entry = entries[index]!, assignment = assignments.find(value => value.id === entry.assignmentId);
          await expect(buttons.nth(index)).toContainText(assignment ? text(assignment.subjectName) : `Предмет №${entry.subjectId}`);
          if (assignment?.teacherName) await expect(buttons.nth(index)).toContainText(text(assignment.teacherName));
          await expect(buttons.nth(index)).toContainText(entry.room ? text(entry.room) : '—');
        }
      }
    }
    return;
  }
  const [lessons, subjects] = await Promise.all([allPages(data.headmanLessons, 'headmanLessons', read), allPages(data.subjects, 'subjects', read)]);
  for (const [key, label] of [['excuseCount', 'Заявки на уважительную причину'], ['lateCount', 'Апелляции отметок']] as const) {
    const count = record(data[key]).totalElements;
    expect(typeof count === 'number' && Number.isSafeInteger(count) && count >= 0, 'Queue count is authoritative').toBe(true);
    const panel = owner.locator('article.headman-panel').filter({ has: owner.getByRole('heading', { name: label, exact: true }) });
    await expect(panel.locator('.headman-metric')).toHaveText(String(count));
  }
  const table = owner.getByRole('table').filter({ has: owner.getByRole('columnheader', { name: 'Пара', exact: true }) });
  await expect(table.locator('tbody tr')).toHaveCount(lessons.length);
  if (!lessons.length) await expect(owner.getByText('Пар на эту дату нет.', { exact: true })).toBeVisible();
  for (let i = 0; i < lessons.length; i++) {
    const lesson = lessons[i]!, row = table.locator('tbody tr').nth(i);
    await expect(row.getByRole('rowheader')).toHaveText(String(lesson.lessonNumber));
    const subject = subjects.find(subject => subject.id === lesson.subjectId);
    await expect(row.locator('td').nth(0)).toContainText(subject ? text(subject.name) : 'Предмет недоступен');
    const link = row.getByRole('link', { name: 'Журнал и действия', exact: true });
    const url = new URL((await link.getAttribute('href'))!, 'https://web-fixture.invalid');
    expect(url.pathname).toBe('/headman/attendance');
    expect(url.searchParams.get('lesson')).toBe(String(lesson.id));
    expect(url.searchParams.get('date')).toBe(lesson.date);
  }
  const active = lessons.find(lesson => lesson.status === 'ACTIVE');
  if (active) {
    const [report, actions] = await Promise.all([read('lessonAttendance').then(record), read('lessonActions').then(record)]);
    expect(report.lessonId).toBe(active.id);
    const entries = array(report.entries).sort((a, b) => text(a.displayName).localeCompare(text(b.displayName), 'ru'));
    const roster = owner.getByRole('table').filter({ has: owner.getByRole('columnheader', { name: 'Студент', exact: true }) });
    await expect(roster.locator('tbody tr th')).toHaveText(entries.map(entry => text(entry.displayName)));
    for (const [status, label] of [['PRESENT', 'Присутствуют «+»'], ['EXCUSED', 'Уважительная причина «у»']]) {
      const count = entries.filter(entry => entry.status === status).length;
      const metric = owner.locator('section.headman-panel').filter({ has: owner.getByRole('heading', { name: label, exact: true }) }).locator('.headman-metric');
      await expect(metric).toHaveAttribute('aria-label', `${count} из ${entries.length}`);
      await expect(metric).toHaveText(new RegExp(`^${count}\\s*/\\s*${entries.length}$`));
    }
    await expect(owner.locator('.headman-list li')).toHaveCount(array(actions.items).length);
  } else await expect(owner.getByText('Сейчас пары нет.', { exact: true })).toBeVisible();
  if (semesters.some(semester => semester.active)) {
    const homeworks = await allPages(await read('homeworks'), 'homeworks', read);
    const date = await owner.getByLabel('Дата', { exact: true }).inputValue();
    const titles = homeworks.filter(homework => homework.lessonDate === date).map(homework => text(homework.title));
    const panel = owner.locator('section.headman-panel').filter({ has: owner.getByRole('heading', { name: 'Домашнее задание на сегодня', exact: true }) });
    await expect(panel.getByRole('heading', { level: 3 })).toHaveText(titles);
    if (!titles.length) await expect(panel.getByText('Заданий на сегодня нет.', { exact: true })).toBeVisible();
  }
}

async function renderedFeature(owner: Locator, path: string, data: Data, read: (key: Read) => Promise<unknown>, kind: string | null) {
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
    const semesters = await allPages(data.semesters, 'semesters', read);
    const table = owner.getByRole('table', { name: 'Учебные семестры', exact: true });
    await rowsOrEmpty(owner, table.locator('tbody tr'), semesters.map(item => text(item.name)), 'В этом разделе нет семестров.');
  } else if (path.startsWith('/student/')) {
    await renderedStudent(owner, path, data, read);
  } else if (path.startsWith('/headman')) {
    await renderedHeadman(owner, path, kind, data, read);
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
    await expect(page).toHaveURL(url => url.pathname === route.pathname && url.searchParams.get('type') === route.kind);
    const data = await reads.initial();
    const owner = page.locator('main#main').locator(route.owner);
    await expect(owner).toBeVisible();
    await renderedFeature(owner, route.pathname, data, reads.read, route.kind);
    const navigation = page.getByRole('navigation', { name: 'Разделы', exact: true });
    const link = navigation.locator(`a[href="${route.pathname}"]`);
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
