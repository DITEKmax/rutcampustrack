import { randomUUID } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';
import type { Request, TestInfo } from '@playwright/test';
import {
  test,
  expect,
  admit,
  http,
  singleCapability,
  selectRole,
  openAssistant,
  deniedMark,
  oneOff,
  registerPrivateSuite,
} from '../fixtures/cloud-accepted-support';
import { loginPwa, logoutPwa } from '../fixtures/pwa-auth';
import { assistantPermissionCodes } from '../fixtures/assistant-permissions';

registerPrivateSuite();

// Charge requested fixtures and body setup to this caller's monotonic budget.
// Earlier runner/auto-fixture overhead is unmeasured; the original timeout applies.
const retryStartedAt = new WeakMap<TestInfo, number>();
test.beforeEach(async () => {
  retryStartedAt.set(test.info(), performance.now());
});

// CLIENT-HEADMAN / CLIENT-FE-HEADMAN-06 / JS-HEADMAN-14,15 / BOUNDARY-49.
// MARK_ATTENDANCE is already covered in mobile-real-server; these actors each
// receive exactly one of the remaining four accepted capabilities.
test('VIEW_STATS renders own group but grants neither marking, roster export nor credentials @cloud-accepted', async ({
  page,
  dataset,
}, info) => {
  // Share the existing registry fixture's real Retry-After handling, within
  // this case's original budget and with 30 seconds reserved for assertions.
  const retryUntilMs = Date.now() + Math.max(0, Math.min(info.timeout, 120000) - 30000);
  const { user, grant } = await singleCapability(dataset, 'VIEW_STATS');
  const { accessToken, session } = await loginPwa(page, user, { retryUntilMs });
  expect(session.activeRole).toBe('STUDENT');
  const permissions = await dataset.request.get('/api/academic/assistants/me/permissions', {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(permissions.status()).toBe(200);
  const ownPermissionOptions = await permissions.json();
  expect(assistantPermissionCodes(ownPermissionOptions)).toEqual(['VIEW_STATS']);
  // Same logged-in helper: profile is a readonly view of exact own capabilities.
  let groupWideAssistantRead = false;
  const observePermissionScope = (request: Request) => {
    if (
      request.method() === 'GET' &&
      new URL(request.url()).pathname === '/api/academic/assistants'
    )
      groupWideAssistantRead = true;
  };
  page.on('request', observePermissionScope);
  try {
    await page.getByRole('button', { name: 'Профиль', exact: true }).click();
    const rights = page.getByRole('region', { name: 'Дополнительные права', exact: true });
    await expect(rights).toBeVisible();
    await expect(rights.getByRole('listitem')).toHaveText(
      ownPermissionOptions.map((option: { label: string }) => option.label),
    );
    await expect(rights.locator('input, button, [role="switch"]')).toHaveCount(0);
    expect(groupWideAssistantRead).toBe(false);
  } finally {
    page.off('request', observePermissionScope);
  }
  const loaded = http(page, '/api/attendance/reports/headman/stats/query', 'POST');
  await openAssistant(page, 'Статистика группы');
  const response = await loaded;
  expect(response.status()).toBe(200);
  expect((await response.json()).context.groupId).toBe(dataset.group.id);
  await expect(page.locator('.headman-stats')).toBeVisible();
  await deniedMark(dataset, accessToken);
  for (const path of [
    '/api/attendance/reports/headman/group-composition/formats',
    '/api/attendance/reports/headman/group-composition/export?format=html',
  ]) {
    expect(
      (
        await dataset.request.get(path, { headers: { Authorization: `Bearer ${accessToken}` } })
      ).status(),
    ).toBe(403);
  }
  const targetBefore = await dataset.admin.call('GET', `/api/academic/users/${dataset.student.id}`);
  const deniedRecovery = await dataset.request.post(
    `/api/auth/admin/users/${dataset.student.id}/password-reset-link`,
    { headers: { Authorization: `Bearer ${accessToken}` } },
  );
  expect(deniedRecovery.status()).toBe(403);
  const targetAfter = await dataset.admin.call('GET', `/api/academic/users/${dataset.student.id}`);
  // Compare a boolean, never put a generated credential into an assertion value.
  expect(targetAfter.initialPassword === targetBefore.initialPassword).toBe(true);

  // JS-HEADMAN-32: the current headman downloads the real own-group roster file.
  expect(dataset.headmanActor.session.activeRole).toBe('HEADMAN');
  const realMoscowDate = new Intl.DateTimeFormat('ru-RU', {
    timeZone: 'Europe/Moscow',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
  const exportDateBefore = realMoscowDate.format(new Date());
  const rosterFile = await dataset.request.get(
    '/api/attendance/reports/headman/group-composition/export?format=html',
    { headers: dataset.headmanActor.headers() },
  );
  const exportDateAfter = realMoscowDate.format(new Date());
  expect(rosterFile.status()).toBe(200);
  const rosterHeaders = rosterFile.headers();
  expect(rosterHeaders['content-type']?.split(';')[0] === 'text/html').toBe(true);
  expect(/^attachment(?:;|$)/i.test(rosterHeaders['content-disposition'] ?? '')).toBe(true);
  expect(
    rosterHeaders['cache-control']?.split(',').some((value) => value.trim() === 'no-store'),
  ).toBe(true);
  const rosterHtml = (await rosterFile.body()).toString('utf8');
  const downloadedRoster = await page.evaluate((markup) => {
    const document = new DOMParser().parseFromString(markup, 'text/html');
    return {
      heading: document.querySelector('h1')?.textContent,
      context: Array.from(document.querySelectorAll('body > p'), (row) => row.textContent),
      headers: Array.from(document.querySelectorAll('thead th'), (cell) => cell.textContent),
      rows: Array.from(document.querySelectorAll('tbody tr'), (row) =>
        Array.from(row.querySelectorAll('td'), (cell) => cell.textContent),
      ),
    };
  }, rosterHtml);
  const expectedRoster = [
    [dataset.student, 'Студент'],
    [dataset.headman, 'Староста'],
    [dataset.assistant, 'Помощник'],
    [user, 'Помощник'],
  ] as const;
  const expectedRows = [...expectedRoster]
    .sort(([left], [right]) => left.displayName.localeCompare(right.displayName, 'ru'))
    .map(([member, role], index) => [String(index + 1), member.displayName, member.login, role]);
  // Assert booleans only: downloaded data and private fixture values never enter failure output.
  expect(
    downloadedRoster.heading === 'Состав группы',
    'Own roster file has its accepted title',
  ).toBe(true);
  expect(
    [exportDateBefore, exportDateAfter].some(
      (date) =>
        JSON.stringify(downloadedRoster.context) ===
        JSON.stringify([`Группа: ${dataset.group.name}`, `Дата выгрузки: ${date}`]),
    ),
    'Own roster context identifies exactly this group and a real Moscow date during its request',
  ).toBe(true);
  expect(
    JSON.stringify(downloadedRoster.headers) ===
      JSON.stringify(['№', 'ФИО', 'Логин', 'Роль в группе']),
    'Own roster exposes exactly the four accepted minimal columns',
  ).toBe(true);
  expect(
    JSON.stringify(downloadedRoster.rows) === JSON.stringify(expectedRows),
    'Own roster contains exactly four alphabetically numbered members with their current group roles',
  ).toBe(true);
  expect(
    expectedRoster.every(
      ([member]) =>
        !rosterHtml.includes(member.password) && !rosterHtml.includes(String(member.telegramId)),
    ) &&
      !rosterHtml.includes(dataset.foreignStudent.login) &&
      !/telegram|password|recovery/i.test(rosterHtml),
    'Own roster excludes credentials, Telegram identifiers, recovery fields and foreign members',
  ).toBe(true);

  await dataset.headmanActor.call('DELETE', `/api/academic/assistants/${grant.id}`, undefined, [
    204,
  ]);
  expect(
    (
      await dataset.request.post('/api/attendance/reports/headman/stats/query', {
        headers: { Authorization: `Bearer ${accessToken}` },
        data: { subjectId: null, lessonTypes: [], page: 0, size: 20, sorts: [], filters: [] },
      })
    ).status(),
  ).toBe(403);
  await page.reload();
  await page.getByRole('button', { name: 'Ещё', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Действия помощника', exact: true })).toHaveCount(
    0,
  );
  const revokedPermissions = await dataset.request.get('/api/academic/assistants/me/permissions', {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(revokedPermissions.status()).toBe(200);
  expect(await revokedPermissions.json()).toEqual([]);
  await revokedPermissions.dispose();
  await page.getByRole('button', { name: 'Профиль', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Дополнительные права', exact: true })).toHaveCount(
    0,
  );

  // CLIENT-FE-HEADMAN-09: the real own-roster button completes the existing file proof.
  await logoutPwa(page);
  await loginPwa(page, dataset.headman, { retryUntilMs });
  const headmanRole = await selectRole(page, 'Староста');
  expect(headmanRole.session.activeRole).toBe('HEADMAN');
  await page.getByRole('button', { name: 'Ещё', exact: true }).click();
  const [formatsReply] = await Promise.all([
    http(page, '/api/attendance/reports/headman/group-composition/formats', 'GET'),
    page.getByRole('button', { name: 'Группа', exact: true }).click(),
  ]);
  expect(formatsReply.status()).toBe(200);
  const htmlFormat = (await formatsReply.json()).formats.find(
    (format: { code: string }) => format.code === 'html',
  );
  expect(typeof htmlFormat?.label === 'string' && htmlFormat.label.length > 0).toBe(true);
  await page.getByRole('button', { name: 'Скачать состав группы', exact: true }).click();
  const rosterSheet = page.getByRole('dialog', { name: 'Состав группы', exact: true });
  await rosterSheet.getByLabel(/^Формат файла/).selectOption('html');
  const uiDateBefore = realMoscowDate.format(new Date());
  const created = http(page, '/api/auth/export-jobs', 'POST');
  const issued = page.waitForResponse(
    (reply) =>
      reply.request().method() === 'POST' &&
      /^\/api\/auth\/export-jobs\/[0-9a-f-]+\/ticket$/.test(new URL(reply.url()).pathname),
  );
  const saved = page.waitForEvent('download');
  await rosterSheet.getByRole('button', { name: 'Скачать', exact: true }).click();
  const job = await created;
  expect(job.status()).toBe(202);
  const selector = job.request().postDataJSON().report;
  expect(
    selector.kind === 'HEADMAN_GROUP_COMPOSITION' &&
      JSON.stringify(selector.headmanGroupComposition) === JSON.stringify({ format: 'html' }),
    'The own-roster UI button uses the existing complete roster selector',
  ).toBe(true);
  const jobId = (await job.json()).jobId;
  const ticket = await issued;
  expect(ticket.status()).toBe(200);
  expect(new URL(ticket.url()).pathname === `/api/auth/export-jobs/${jobId}/ticket`).toBe(true);
  const download = await saved;
  expect(await download.failure()).toBeNull();
  expect(download.suggestedFilename().endsWith('.html')).toBe(true);
  const stream = await download.createReadStream();
  expect(stream !== null).toBe(true);
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const part of stream!) {
    const bytes = Buffer.from(part);
    size += bytes.length;
    expect(size <= 20 * 1024 * 1024).toBe(true);
    chunks.push(bytes);
  }
  expect(size > 0).toBe(true);
  const uiDateAfter = realMoscowDate.format(new Date());
  const uiHtml = Buffer.concat(chunks).toString('utf8');
  const uiRoster = await page.evaluate((markup) => {
    const document = new DOMParser().parseFromString(markup, 'text/html');
    return {
      heading: document.querySelector('h1')?.textContent,
      context: Array.from(document.querySelectorAll('body > p'), (row) => row.textContent),
      headers: Array.from(document.querySelectorAll('thead th'), (cell) => cell.textContent),
      rows: Array.from(document.querySelectorAll('tbody tr'), (row) =>
        Array.from(row.querySelectorAll('td'), (cell) => cell.textContent),
      ),
    };
  }, uiHtml);
  // The revoked helper is now an ordinary student; every other member and column stays exact.
  const currentRows = [...expectedRoster]
    .sort(([left], [right]) => left.displayName.localeCompare(right.displayName, 'ru'))
    .map(([member, role], index) => [
      String(index + 1),
      member.displayName,
      member.login,
      member.id === user.id ? 'Студент' : role,
    ]);
  expect(
    uiRoster.heading === downloadedRoster.heading &&
      [uiDateBefore, uiDateAfter].some(
        (date) =>
          JSON.stringify(uiRoster.context) ===
          JSON.stringify([`Группа: ${dataset.group.name}`, `Дата выгрузки: ${date}`]),
      ) &&
      JSON.stringify(uiRoster.headers) === JSON.stringify(downloadedRoster.headers) &&
      JSON.stringify(uiRoster.rows) === JSON.stringify(currentRows),
    'The real UI download preserves exact current own members, order, roles and minimal columns',
  ).toBe(true);
  expect(
    expectedRoster.every(
      ([member]) =>
        !uiHtml.includes(member.password) && !uiHtml.includes(String(member.telegramId)),
    ) &&
      !uiHtml.includes(dataset.foreignStudent.login) &&
      !/telegram|password|recovery/i.test(uiHtml),
    'The UI-downloaded roster retains the original private-field and foreign-member exclusions',
  ).toBe(true);
  await rosterSheet.getByRole('button', { name: 'Закрыть настройки', exact: true }).click();
  // End one roster UI witness; all prior helper denial/revocation assertions remain unchanged.
});

// CLIENT-HOMEWORK / CLIENT-FE-HEADMAN-07 / BOUNDARY-36:
// a non-author may edit by MANAGE_HOMEWORK; revocation preserves title/revision.
test('MANAGE_HOMEWORK edits another author through Vue and revoked edit has no effect @cloud-accepted', async ({
  page,
  dataset,
}) => {
  const { user, grant } = await singleCapability(dataset, 'MANAGE_HOMEWORK');
  const { accessToken } = await loginPwa(page, user);
  await openAssistant(page, 'Домашние задания');
  await page.getByLabel('Дата', { exact: true }).fill(dataset.tomorrow);
  await page.getByLabel('Привязка', { exact: true }).selectOption('DATE');
  await expect(page.getByText(dataset.marker, { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Изменить', exact: true }).click();
  const title = `${dataset.marker}-helper-edit`;
  await page.getByLabel('Название', { exact: true }).fill(title);
  const path = `/api/academic/homeworks/${dataset.homework.id}`;
  const changed = http(page, path, 'PUT');
  await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
  expect((await changed).status()).toBe(200);
  await expect(page.getByText(title, { exact: true })).toBeVisible();
  const before = await dataset.headmanActor.call('GET', path);
  expect(before.title).toBe(title);
  const history = await dataset.headmanActor.call('GET', `${path}/history?size=100`);
  const events = history._embedded?.homeworkHistoryResponseList ?? history.content;
  expect(events.some((event: { actorId: number }) => event.actorId === user.id)).toBe(true);
  await deniedMark(dataset, accessToken);
  // Retain the rendered editor when the live server authority changes.
  await page.getByRole('button', { name: 'Изменить', exact: true }).click();
  await page.getByLabel('Название', { exact: true }).fill(`${title}-denied`);
  await dataset.headmanActor.call('DELETE', `/api/academic/assistants/${grant.id}`, undefined, [
    204,
  ]);
  const denied = http(page, path, 'PUT');
  await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
  expect((await denied).status()).toBe(403);
  const after = await dataset.headmanActor.call('GET', path);
  expect(after.title).toBe(before.title);
  expect(after.revision).toBe(before.revision);
  await page.reload();
  await page.getByRole('button', { name: 'Ещё', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Действия помощника', exact: true })).toHaveCount(
    0,
  );
});

// CLIENT-HEADMAN / CLIENT-FE-HEADMAN-04 / BOUNDARY-23: CANCEL_LESSONS is separate from
// schedule construction, homework management and attendance marking.
test('CANCEL_LESSONS confirms a real cancellation and revoked restore cannot resurrect it @cloud-accepted', async ({
  page,
  dataset,
}, info) => {
  const cutoff =
    (retryStartedAt.get(info) ?? performance.now()) + Math.min(info.timeout, 120000) - 30000;
  const retryUntilMs = Date.now() + Math.max(0, Math.floor(cutoff - performance.now()));
  const { user, grant } = await singleCapability(dataset, 'CANCEL_LESSONS');
  const lesson = await oneOff(dataset, dataset.today, 8);
  const { accessToken } = await loginPwa(page, user, { retryUntilMs });
  await openAssistant(page, 'Посещаемость и журнал');
  // An early next click during loadLessons() advances the date while lessons is empty.
  await expect(page.locator('.headman-journal__date-nav output')).toContainText('1 пара');
  await page.getByRole('button', { name: 'Следующая пара или день', exact: true }).click();
  await expect(page.locator('.headman-journal__date-nav output')).toContainText('8 пара');
  await expect(page.getByRole('button', { name: 'Управление парой', exact: true })).toBeVisible();
  await deniedMark(dataset, accessToken);
  await page.getByRole('button', { name: 'Управление парой', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Перенести пару', exact: true })).toHaveCount(0);
  await page.getByLabel('Причина отмены', { exact: true }).fill('Синтетическая отмена');
  await page.getByRole('button', { name: 'Отменить пару', exact: true }).click();
  const cancelled = http(page, `/api/schedule/lessons/${lesson.physicalLessonId}/cancel`, 'PATCH');
  await page
    .getByRole('dialog', { name: 'Отменить пару?', exact: true })
    .getByRole('button', { name: 'Отменить пару', exact: true })
    .click();
  expect((await cancelled).status()).toBe(200);
  await expect(page.locator('.headman-journal__date-nav output')).toContainText('1 пара');
  await page.getByRole('button', { name: 'Следующая пара или день', exact: true }).click();
  await expect(page.locator('.headman-journal__date-nav output')).toContainText('8 пара');
  await page.getByRole('button', { name: 'Управление парой', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Восстановить пару', exact: true })).toBeVisible();
  const listPath = `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${dataset.today}&dateTo=${dataset.today}&status=CANCELLED&size=100`;
  const before = await dataset.headmanActor.call('GET', listPath);
  const lessons = before._embedded?.lessonResponseList ?? before.content;
  expect(lessons.some((row: { id: number }) => row.id === lesson.physicalLessonId)).toBe(true);
  await dataset.headmanActor.call('DELETE', `/api/academic/assistants/${grant.id}`, undefined, [
    204,
  ]);
  const denied = await dataset.request.patch(
    `/api/schedule/lessons/${lesson.physicalLessonId}/restore`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
    },
  );
  expect(denied.status()).toBe(403);
  const after = await dataset.headmanActor.call('GET', listPath);
  const actual = after._embedded?.lessonResponseList ?? after.content;
  expect(actual.some((row: { id: number }) => row.id === lesson.physicalLessonId)).toBe(true);
  await page.reload();
  await page.getByRole('button', { name: 'Ещё', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Действия помощника', exact: true })).toHaveCount(
    0,
  );
});

// CLIENT-HEADMAN / CLIENT-FE-HEADMAN-02 / BOUNDARY-30:
// prepare a genuine own student request; only this helper decides another user.
test('MANAGE_EXCUSES decides a real request and revoked decision preserves the pending request @cloud-accepted', async ({
  page,
  dataset,
}, info) => {
  const retryCutoffMs =
    (retryStartedAt.get(info) ?? Number.NEGATIVE_INFINITY) +
    Math.max(0, Math.min(info.timeout, 120000) - 30000);
  const { user, grant } = await singleCapability(dataset, 'MANAGE_EXCUSES');
  const requester = await dataset.user('STUDENT', dataset.group.id);
  const actor = await admit(dataset.request, requester, 'STUDENT');
  const lessons = [
    await oneOff(dataset, dataset.tomorrow, 1),
    await oneOff(dataset, dataset.tomorrow, 2),
  ];
  // JS-HEADMAN-18: count submitted identities, including the later rejected and pending tickets.
  const reasonBaseline = await dataset.headmanActor.call(
    'POST',
    '/api/attendance/reports/headman/stats/query',
    {
      subjectId: null,
      lessonTypes: [],
      page: 0,
      size: 20,
      sorts: [],
      filters: [],
    },
  );
  expect(
    reasonBaseline.context.groupId === dataset.group.id &&
      reasonBaseline.context.semesterId === dataset.semester.id &&
      Array.isArray(reasonBaseline.reasonCounts),
    'Own current-semester submitted-reason baseline is available',
  ).toBe(true);
  const illnessBefore =
    reasonBaseline.reasonCounts.find(
      (row: { code: string; count: number }) => row.code === 'ILLNESS',
    )?.count ?? 0;
  expect(Number.isSafeInteger(illnessBefore) && illnessBefore >= 0).toBe(true);
  const requests: string[] = [];
  for (const lesson of lessons) {
    const created = await dataset.request.post('/api/v1/student/requests/excuse', {
      headers: { ...actor.headers(), 'Idempotency-Key': randomUUID() },
      multipart: {
        request: {
          name: 'request.json',
          mimeType: 'application/json',
          buffer: Buffer.from(
            JSON.stringify({
              lessonIds: [String(lesson.physicalLessonId)],
              reason: 'ILLNESS',
              comment: dataset.marker,
            }),
          ),
        },
      },
    });
    expect(created.status()).toBe(200);
    requests.push((await created.json()).summary.id);
  }
  const retryRemainingMs = Math.floor(retryCutoffMs - performance.now());
  expect(retryRemainingMs, 'Synthetic helper login retry budget remains').toBeGreaterThan(0);
  const { accessToken } = await loginPwa(page, user, {
    retryUntilMs: Date.now() + Math.min(65000, retryRemainingMs),
  });
  await openAssistant(page, 'Уважительные причины и заявки');
  await page.locator(`[data-request-id="${requests[0]}"]`).click();
  await page.getByRole('button', { name: 'Отклонить', exact: true }).click();
  await page
    .getByRole('textbox', { name: /^Причина отклонения/ })
    .fill('Синтетическая причина отказа');
  const decisionPath = `/api/attendance/requests/${requests[0]}/decision`;
  const decided = http(page, decisionPath, 'POST');
  await page.getByRole('button', { name: 'Отклонить заявку', exact: true }).click();
  expect((await decided).status()).toBe(200);
  const actual = await dataset.headmanActor.call('GET', `/api/attendance/requests/${requests[0]}`);
  expect(actual.summary.status).toBe('REJECTED');
  expect(actual.summary.decisionBy).toBe(user.id);
  await deniedMark(dataset, accessToken);
  const pendingPath = `/api/attendance/requests/${requests[1]}`;
  const pending = await dataset.headmanActor.call('GET', pendingPath);
  expect(pending.summary.status).toBe('PENDING');
  // JS-HEADMAN-15: removing one capability must retain the other on this session.
  for (const permissions of [['MANAGE_EXCUSES', 'VIEW_STATS'], ['VIEW_STATS']]) {
    await dataset.headmanActor.call(
      'PATCH',
      `/api/academic/assistants/${grant.id}/permissions`,
      { permissions },
      [200],
    );
    const catalog = await dataset.request.get('/api/academic/assistants/me/permissions', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(catalog.status()).toBe(200);
    expect(assistantPermissionCodes(await catalog.json()).sort()).toEqual([...permissions].sort());
    const stats = await dataset.request.post('/api/attendance/reports/headman/stats/query', {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: { subjectId: null, lessonTypes: [], page: 0, size: 20, sorts: [], filters: [] },
    });
    expect(stats.status()).toBe(200);
    const currentStats = await stats.json();
    expect(currentStats.context.groupId).toBe(dataset.group.id);
    expect(
      currentStats.context.semesterId === dataset.semester.id &&
        Array.isArray(currentStats.reasonCounts),
      'Same current-semester reason counts survive partial capability removal',
    ).toBe(true);
    expect(
      currentStats.reasonCounts.find(
        (row: { code: string; count: number }) => row.code === 'ILLNESS',
      )?.count,
    ).toBe(illnessBefore + 2);
  }
  const reasonView = http(page, '/api/attendance/reports/headman/stats/query', 'POST');
  await page.reload();
  await openAssistant(page, 'Статистика группы');
  const reasonResponse = await reasonView;
  expect(reasonResponse.status()).toBe(200);
  const reasonBody = await reasonResponse.json();
  expect(
    reasonBody.context.groupId === dataset.group.id &&
      reasonBody.context.semesterId === dataset.semester.id &&
      Array.isArray(reasonBody.reasonCounts),
    'Reloaded current helper receives the same reason scope',
  ).toBe(true);
  expect(
    reasonBody.reasonCounts.find((row: { code: string; count: number }) => row.code === 'ILLNESS')
      ?.count,
  ).toBe(illnessBefore + 2);
  await expect(
    page
      .getByRole('region', { name: 'Поданные уважительные причины', exact: true })
      .getByRole('listitem'),
  ).toHaveText(
    reasonBody.reasonCounts.map(
      (row: { label: string; count: number }) => `${row.label} · ${row.count}`,
    ),
  );
  const partiallyDenied = await dataset.request.post(`${pendingPath}/decision`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: {
      decision: 'REJECTED',
      reason: 'Запрещённое решение после частичного отзыва',
      expectedRevision: pending.decisionRevision,
      excludedOccurrenceIds: [],
      requestKey: randomUUID(),
    },
  });
  expect(partiallyDenied.status()).toBe(403);
  const afterPartialRevoke = await dataset.headmanActor.call('GET', pendingPath);
  expect(afterPartialRevoke.summary.status).toBe('PENDING');
  expect(afterPartialRevoke.summary.decisionBy).toBeNull();
  expect(afterPartialRevoke.decisionRevision).toBe(pending.decisionRevision);
  await dataset.headmanActor.call('DELETE', `/api/academic/assistants/${grant.id}`, undefined, [
    204,
  ]);
  const denied = await dataset.request.post(`${pendingPath}/decision`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: {
      decision: 'REJECTED',
      reason: 'Запрещённое решение',
      expectedRevision: pending.decisionRevision,
      excludedOccurrenceIds: [],
      requestKey: randomUUID(),
    },
  });
  expect(denied.status()).toBe(403);
  const retained = await dataset.headmanActor.call('GET', pendingPath);
  expect(retained.summary.status).toBe('PENDING');
  expect(retained.summary.decisionBy).toBeNull();
  expect(retained.decisionRevision).toBe(pending.decisionRevision);
});

// CLIENT-FE-HEADMAN-06 / JS-HEADMAN-14,15: the HEADMAN chooses a readable
// subset through the member row; the existing helper cases prove its effects.
test('HEADMAN edits helper capabilities through member row and retains VIEW_STATS @cloud-accepted', async ({
  page,
  dataset,
}, info) => {
  const retryUntilMs = Date.now() + Math.max(0, Math.min(info.timeout, 120000) - 30000);
  const user = await dataset.user('STUDENT', dataset.group.id);
  const grant = await dataset.headmanActor.call(
    'POST',
    '/api/academic/assistants',
    {
      groupId: dataset.group.id,
      studentId: user.id,
      permissions: ['MANAGE_EXCUSES', 'VIEW_STATS'],
    },
    [201],
  );
  try {
    await loginPwa(page, dataset.headman, { retryUntilMs });
    const headman = await selectRole(page, 'Староста');
    expect(headman.session.activeRole).toBe('HEADMAN');
    await page.getByRole('button', { name: 'Ещё', exact: true }).click();
    await page.getByRole('button', { name: 'Группа', exact: true }).click();
    const group = page.locator('.headman-group');
    await group.locator(`[data-member-id="${user.id}"]`).click();
    await expect(group.getByRole('heading', { name: user.displayName, exact: true })).toBeVisible();
    await group.getByRole('button', { name: 'Настроить права', exact: true }).click();
    const choices = [
      ['Отмечать посещаемость', false],
      ['Обрабатывать уважительные причины', true],
      ['Управлять домашними заданиями', false],
      ['Отменять занятия', false],
      ['Просматривать статистику', true],
    ] as const;
    await expect(group.getByRole('switch')).toHaveCount(choices.length);
    for (const [label, enabled] of choices) {
      await expect(group.getByRole('switch', { name: label, exact: true })).toHaveAttribute(
        'aria-checked',
        String(enabled),
      );
    }
    await group
      .getByRole('switch', { name: 'Обрабатывать уважительные причины', exact: true })
      .click();
    await group.getByRole('button', { name: 'Сохранить изменения: 1', exact: true }).click();
    const confirmation = page.getByRole('dialog', { name: 'Отозвать права?', exact: true });
    await expect(
      confirmation.getByText('Обрабатывать уважительные причины', { exact: true }),
    ).toBeVisible();
    await expect(confirmation.getByText('Разрешено → Запрещено', { exact: true })).toBeVisible();
    const saved = http(page, `/api/academic/assistants/${grant.id}/permissions`, 'PATCH');
    await confirmation.getByRole('button', { name: 'Отозвать права', exact: true }).click();
    const response = await saved;
    expect(response.status()).toBe(200);
    expect(response.request().postDataJSON()).toEqual({ permissions: ['VIEW_STATS'] });
    await expect(
      group.getByText('Изменения подтверждены сервером.', { exact: true }),
    ).toBeVisible();
    const catalog = await dataset.headmanActor.call(
      'GET',
      `/api/academic/assistants?groupId=${dataset.group.id}&size=100`,
    );
    const assistants = catalog._embedded?.assistantResponseList ?? catalog.content ?? [];
    const actual = assistants.find((row: { id: number }) => row.id === grant.id);
    expect(actual).toMatchObject({
      id: grant.id,
      studentId: user.id,
      groupId: dataset.group.id,
      active: true,
    });
    expect(actual.permissions).toEqual(['VIEW_STATS']);
    await group.getByRole('button', { name: 'Назад', exact: true }).click();
    await group.getByRole('button', { name: 'Настроить права', exact: true }).click();
    for (const [label] of choices) {
      await expect(group.getByRole('switch', { name: label, exact: true })).toHaveAttribute(
        'aria-checked',
        String(label === 'Просматривать статистику'),
      );
    }
  } finally {
    await dataset.headmanActor.call('DELETE', `/api/academic/assistants/${grant.id}`, undefined, [
      204,
    ]);
  }
});

// CLIENT-FE-HEADMAN-01 / JS-HEADMAN-25,26: one genuine ongoing-summary and
// scoped action-log read. No-current, live refresh and an independent history
// pager remain separate criteria; VIEW_STATS selects the existing CI lane.
test('HEADMAN reads ongoing VIEW_STATS counters and scoped action history @cloud-accepted', async ({
  page,
  activeHeadmanDataset: dataset,
  teachingInstant,
}, info) => {
  const retryUntilMs = Date.now() + Math.max(0, Math.min(info.timeout, 120000) - 30000);
  const lessonId = dataset.lesson.physicalLessonId;
  const reportPath = `/api/attendance/reports/lesson/${lessonId}`;
  const schedulePath = `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${dataset.today}&dateTo=${dataset.today}&size=100`;
  expect(teachingInstant).toBeInstanceOf(Date);
  const teachingNow = teachingInstant!.getTime();
  const lessonTime = (
    lesson: { date: string; startTime: string; endTime: string },
    edge: 'startTime' | 'endTime',
  ) => Date.parse(`${lesson.date}T${lesson[edge]}+03:00`);
  type Entry = { userId: number; status: string | null; markRevision: string | null };
  type Action = {
    id: string;
    action: string;
    outcome: string;
    occurredAt: string;
    actor: { userId: number; displayName: string };
    student: { userId: number; displayName: string };
    after: { status: string; source: string };
  };
  const seeded: { userId: number; status: string; revision: string }[] = [];
  let otherLessonId: number | undefined;
  let homeRequestId: string | undefined;
  let homeHomeworkId: number | undefined;
  let homeStudent: Awaited<ReturnType<typeof admit>> | undefined;
  const homeHomeworkTitle = `${dataset.marker} Home Today`;
  let homeExpectedCounts: readonly number[];
  try {
    const before = await dataset.headmanActor.call('GET', reportPath);
    for (const [user, status] of [
      [dataset.student, 'PRESENT'],
      [dataset.assistant, 'EXCUSED'],
    ] as const) {
      const row = (before.entries as Entry[]).find((entry) => entry.userId === user.id);
      expect(row).toMatchObject({ status: null, markRevision: null });
      // These existing mark commands only prepare a distinguishable read fixture.
      await dataset.headmanActor.call(
        'PUT',
        `/api/attendance/lessons/${lessonId}/students/${user.id}`,
        {
          status,
          ...(status === 'EXCUSED' ? { excuseType: 'ILLNESS', comment: dataset.marker } : {}),
          expectedStatus: 'EMPTY',
          expectedRevision: 'EMPTY',
        },
      );
      const marked = await dataset.headmanActor.call('GET', reportPath);
      const actual = (marked.entries as Entry[]).find((entry) => entry.userId === user.id)!;
      expect(typeof actual.markRevision).toBe('string');
      seeded.push({ userId: user.id, status, revision: actual.markRevision! });
    }
    const other = await oneOff(dataset, dataset.today, 2);
    otherLessonId = Number(other.physicalLessonId);
    const schedule = await dataset.headmanActor.call('GET', schedulePath);
    const lessons = schedule._embedded?.lessonResponseList ?? schedule.content ?? [];
    const ongoingLesson = lessons.find((row: { id: number }) => row.id === lessonId);
    const otherLesson = lessons.find((row: { id: number }) => row.id === otherLessonId);
    expect(ongoingLesson).toMatchObject({
      status: 'ACTIVE',
      current: true,
    });
    expect(lessonTime(ongoingLesson, 'startTime')).toBeLessThanOrEqual(teachingNow);
    expect(lessonTime(ongoingLesson, 'endTime')).toBeGreaterThan(teachingNow);
    // current identifies the physical occurrence pointer, including future lessons.
    // Status and Moscow slot times distinguish the ongoing lesson from the next one.
    expect(otherLesson).toMatchObject({
      status: 'PLANNED',
      current: true,
    });
    expect(lessonTime(otherLesson, 'startTime')).toBeGreaterThan(teachingNow);
    // WEB02: own new fixtures, with existing worker tickets retained in the baseline.
    const countOpen = async (type: 'EXCUSE' | 'LATE_CHECKIN') => {
      const reply = await dataset.headmanActor.call(
        'GET',
        `/api/attendance/requests?bucket=OPEN&type=${type}&page=0&size=1`,
      );
      expect(Number.isSafeInteger(reply.totalElements) && reply.totalElements >= 0).toBe(true);
      return reply.totalElements as number;
    };
    const baseline = await Promise.all([countOpen('EXCUSE'), countOpen('LATE_CHECKIN')]);
    homeStudent = await admit(dataset.request, dataset.student, 'STUDENT');
    const submission = await dataset.request.post('/api/v1/student/requests/excuse', {
      headers: { ...homeStudent.headers(), 'Idempotency-Key': randomUUID() },
      multipart: {
        request: {
          name: 'request.json',
          mimeType: 'application/json',
          buffer: Buffer.from(
            JSON.stringify({
              lessonIds: [String(otherLessonId)],
              reason: 'ILLNESS',
              comment: dataset.marker,
            }),
          ),
        },
      },
    });
    expect(submission.status()).toBe(200);
    const ownTicket = await submission.json();
    homeRequestId = ownTicket.summary.id;
    expect(
      ownTicket.summary.status === 'PENDING' &&
        ownTicket.summary.kind === 'EXCUSE' &&
        ownTicket.summary.lessons.length === 1 &&
        String(ownTicket.summary.lessons[0].id) === String(otherLessonId),
    ).toBe(true);
    homeExpectedCounts = [baseline[0]! + 1, baseline[1]!];
    expect(await Promise.all([countOpen('EXCUSE'), countOpen('LATE_CHECKIN')])).toEqual(
      homeExpectedCounts,
    );
    const homeworkInput = {
      title: homeHomeworkTitle,
      description: 'Синтетическое ДЗ главной на сегодня',
      link: null,
      groupId: dataset.group.id,
      subjectId: dataset.subject.id,
      semesterId: dataset.semester.id,
      lessonDate: dataset.today,
      lessonNumber: null,
      bindingMode: 'DATE',
      lessonType: 'LECTURE',
      requestKey: randomUUID(),
    };
    await expect
      .poll(
        async () => {
          const created = await dataset.headmanActor.call(
            'POST',
            '/api/academic/homeworks',
            homeworkInput,
            [200, 201, 202],
          );
          const id = Number(created.id ?? created.homeworkId);
          if (Number.isSafeInteger(id) && id > 0) homeHomeworkId = id;
          return created.state !== 'PENDING' && Number.isSafeInteger(created.id);
        },
        {
          timeout: 15000,
          message: 'Owned Today homework becomes published with the same request key',
        },
      )
      .toBe(true);
    // End WEB02 fixture preparation.

    const members = await dataset.headmanActor.call(
      'GET',
      '/api/academic/groups/my/members?size=100',
    );
    const roster = members._embedded?.userResponseList ?? members.content ?? [];
    const memberIds = roster
      .map((row: { id: number }) => row.id)
      .sort((a: number, b: number) => a - b);
    const current = await dataset.headmanActor.call('GET', reportPath);
    const entries = current.entries as Entry[];
    expect(entries.map((row) => row.userId).sort((a, b) => a - b)).toEqual(memberIds);
    const denominator = memberIds.length;
    expect(new Set(memberIds).size).toBe(denominator);
    expect(denominator).toBeGreaterThanOrEqual(3);
    const counts = (rows: Entry[]) => [
      rows.filter((row) => row.status?.toUpperCase() === 'PRESENT').length,
      rows.filter((row) => row.status?.toUpperCase() === 'EXCUSED').length,
    ];
    expect(counts(entries)).toEqual([1, 1]);
    const actionsPath = `/api/attendance/reports/lesson/${lessonId}/actions`;
    const stored = await dataset.headmanActor.call('GET', `${actionsPath}?limit=50`);
    const events = (stored.items as Action[]).filter(
      (event) =>
        event.action === 'ATTENDANCE_SET' &&
        event.outcome === 'APPLIED' &&
        event.actor?.userId === dataset.headman.id &&
        seeded.some((mark) => mark.userId === event.student?.userId),
    );
    expect(events).toHaveLength(2);
    await loginPwa(page, dataset.headman, { retryUntilMs });
    expect((await selectRole(page, 'Староста')).session.activeRole).toBe('HEADMAN');
    const home = page.locator('.headman-home');
    await expect(home).toHaveAttribute('data-lesson-id', String(lessonId));
    // WEB02: a genuine Home read, separate from notification unread counts.
    const todayHomework = home.getByRole('region', { name: 'Домашнее задание пары', exact: true });
    await expect(todayHomework).toContainText(homeHomeworkTitle);
    await todayHomework.getByRole('button').click();
    await expect(todayHomework).toContainText('Синтетическое ДЗ главной на сегодня');
    await expect(todayHomework.locator('input, [role="slider"]')).toHaveCount(0);
    const pendingCounts = home.getByRole('region', { name: 'Открытые заявки', exact: true });
    await expect(pendingCounts).toHaveAttribute('data-state', 'ready');
    for (const [index, kind] of ['EXCUSE', 'LATE_CHECKIN'].entries())
      await expect(pendingCounts.locator(`[data-request-kind="${kind}"] strong`)).toHaveText(
        String(homeExpectedCounts[index]),
      );
    const queueRead = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        response.request().method() === 'GET' &&
        url.pathname === '/api/attendance/requests' &&
        url.searchParams.get('bucket') === 'OPEN' &&
        url.searchParams.get('type') === 'EXCUSE' &&
        url.searchParams.get('size') === '20'
      );
    });
    await pendingCounts.locator('[data-request-kind="EXCUSE"]').click();
    expect((await queueRead).status()).toBe(200);
    const queue = page.locator('.headman-requests');
    await expect(queue.getByRole('button', { name: /Уважительные причины/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await expect(queue.locator(`[data-request-id="${homeRequestId}"]`)).toBeVisible();
    await page.getByRole('button', { name: 'Сегодня', exact: true }).click();
    await expect(home).toHaveAttribute('data-lesson-id', String(lessonId));
    // End WEB02 Home witness; retain every existing history and live-counter oracle.

    const historyRead = http(page, actionsPath, 'GET');
    await home.getByRole('button', { name: 'Журнал', exact: true }).click();
    const history = await historyRead;
    expect(history.status()).toBe(200);
    const visibleEvents = (await history.json()).items as Action[];
    const log = page.locator('.headman-actions');
    await expect(log).toHaveAttribute('data-lesson-id', String(lessonId));
    for (const event of events) {
      expect(visibleEvents.find((row) => row.id === event.id)).toEqual(event);
      expect(event.after.source).toBe('HEADMAN');
      const row = log.locator(`[data-event-id="${event.id}"]`);
      await expect(row).toContainText(event.student.displayName);
      await expect(row).toContainText(event.actor.displayName);
      await expect(row.locator('time')).toHaveAttribute('datetime', event.occurredAt);
      await expect(row).toHaveAttribute('data-mark', event.after.status.toLowerCase());
      const [afterLabel, afterSymbol] =
        event.after.status === 'PRESENT' ? ['Присутствует', '+'] : ['Уважительная причина', 'у'];
      const transition = row.locator('.headman-actions__transition');
      await expect(transition).toBeVisible();
      await expect(transition).toHaveAttribute('aria-label', `Без отметки → ${afterLabel}`);
      await expect(transition.getByText('—', { exact: true })).toBeVisible();
      await expect(transition.getByText('→', { exact: true })).toBeVisible();
      await expect(transition.getByText(afterSymbol, { exact: true })).toBeVisible();
    }
    // Browse another history through existing Home controls, without a manual mark.
    await page.getByRole('button', { name: 'Назад', exact: true }).click();
    await expect(home).toHaveAttribute('data-lesson-id', String(lessonId));
    await home.getByRole('button', { name: 'Следующая пара', exact: true }).click();
    await expect(home).toHaveAttribute('data-lesson-id', String(otherLessonId));
    const otherHistoryRead = http(
      page,
      `/api/attendance/reports/lesson/${otherLessonId}/actions`,
      'GET',
    );
    await home.getByRole('button', { name: 'Журнал', exact: true }).click();
    expect((await otherHistoryRead).status()).toBe(200);
    await expect(log).toHaveAttribute('data-lesson-id', String(otherLessonId));
    for (const event of events) {
      await expect(log.locator(`[data-event-id="${event.id}"]`)).toHaveCount(0);
    }
    await page.getByRole('button', { name: 'Назад', exact: true }).click();
    await expect(home).toHaveAttribute('data-lesson-id', String(lessonId));
    const selectedReportRead = http(page, `/api/attendance/reports/lesson/${otherLessonId}`, 'GET');
    await home.getByRole('button', { name: 'Следующая пара', exact: true }).click();
    const selectedReport = await selectedReportRead;
    expect(selectedReport.status()).toBe(200);
    expect(counts((await selectedReport.json()).entries)).toEqual([0, 0]);
    await expect(home).toHaveAttribute('data-lesson-id', String(otherLessonId));
    const stillCurrent = await dataset.headmanActor.call('GET', schedulePath);
    const stillLessons = stillCurrent._embedded?.lessonResponseList ?? stillCurrent.content ?? [];
    expect(stillLessons.find((row: { id: number }) => row.id === lessonId)).toMatchObject({
      status: 'ACTIVE',
      current: true,
    });
    const readback = await dataset.headmanActor.call('GET', reportPath);
    expect(counts(readback.entries)).toEqual([1, 1]);
    expect(readback.entries).toHaveLength(denominator);
    // The accepted ongoing summary must retain both full-group fractions even
    // though the selected panel now reads the other lesson's empty report.
    await expect(
      home.locator(
        '.headman-home__ongoing[aria-label="Отметки текущей пары"] .headman-home__ongoing-present',
      ),
    ).toContainText(`1 из ${denominator}`);
    await expect(
      home.locator(
        '.headman-home__ongoing[aria-label="Отметки текущей пары"] .headman-home__ongoing-excused',
      ),
    ).toContainText(`1 из ${denominator}`);
    // One owned, revisioned fixture change must become visible through the normal
    // foreground poll, without reloading or moving the selected other lesson.
    const liveMark = seeded.find((mark) => mark.userId === dataset.student.id)!;
    await dataset.headmanActor.call(
      'PUT',
      `/api/attendance/lessons/${lessonId}/students/${liveMark.userId}`,
      { status: 'ABSENT', expectedStatus: liveMark.status, expectedRevision: liveMark.revision },
    );
    liveMark.status = 'ABSENT';
    const changed = await dataset.headmanActor.call('GET', reportPath);
    const changedEntry = (changed.entries as Entry[]).find(
      (entry) => entry.userId === liveMark.userId,
    )!;
    expect(changedEntry.status).toBe('absent');
    expect(typeof changedEntry.markRevision).toBe('string');
    liveMark.revision = changedEntry.markRevision!;
    expect(counts(changed.entries)).toEqual([0, 1]);
    await expect(
      home.locator(
        '.headman-home__ongoing[aria-label="Отметки текущей пары"] .headman-home__ongoing-present',
      ),
    ).toContainText(`0 из ${denominator}`, { timeout: 40000 });
    await expect(
      home.locator(
        '.headman-home__ongoing[aria-label="Отметки текущей пары"] .headman-home__ongoing-excused',
      ),
    ).toContainText(`1 из ${denominator}`);
    await expect(home).toHaveAttribute('data-lesson-id', String(otherLessonId));
    await expect(home.locator('.headman-home__metrics > div').first().locator('strong')).toHaveText(
      '0',
    );
  } finally {
    let cleanupFailed = false;
    if (homeRequestId !== undefined && homeStudent) {
      try {
        const cancelled = await homeStudent.call(
          'POST',
          `/api/v1/student/requests/${homeRequestId}/cancel`,
        );
        if (cancelled.summary.status !== 'CANCELLED') {
          cleanupFailed = true;
          expect.soft(false, 'Owned Home request cleanup failed').toBe(true);
        }
      } catch {
        cleanupFailed = true;
        expect.soft(false, 'Owned Home request cleanup failed').toBe(true);
      }
    }
    if (homeHomeworkId !== undefined) {
      try {
        await dataset.headmanActor.call(
          'DELETE',
          `/api/academic/homeworks/${homeHomeworkId}`,
          undefined,
          [204],
        );
      } catch {
        cleanupFailed = true;
        expect.soft(false, 'Owned Home homework cleanup failed').toBe(true);
      }
    }
    for (const mark of seeded) {
      try {
        const query = new URLSearchParams({
          expectedStatus: mark.status,
          expectedRevision: mark.revision,
        });
        await dataset.headmanActor.call(
          'DELETE',
          `/api/attendance/lessons/${lessonId}/students/${mark.userId}?${query}`,
          undefined,
          [204],
        );
      } catch {
        cleanupFailed = true;
        if (mark.userId === dataset.student.id)
          expect.soft(false, 'Owned student mark cleanup failed').toBe(true);
        else expect.soft(false, 'Owned assistant mark cleanup failed').toBe(true);
      }
    }
    if (otherLessonId !== undefined) {
      try {
        await dataset.headmanActor.call('PATCH', `/api/schedule/lessons/${otherLessonId}/cancel`, {
          reason: 'Очистка синтетической проверки чтения',
        });
      } catch {
        cleanupFailed = true;
        expect.soft(false, 'Owned other lesson cancellation cleanup failed').toBe(true);
      }
    }
    expect
      .soft(cleanupFailed, 'Only this read fixture is cleaned through guarded server commands')
      .toBe(false);
  }
});

// JS-HEADMAN-23/34: one own member, real generated reset and existing public self-set.
test('HEADMAN resets own member access and issues one-use recovery link @cloud-accepted', async ({
  page,
  dataset,
}, info) => {
  test.setTimeout(120000);
  const cutoff = (retryStartedAt.get(info) ?? performance.now()) + 90000;
  // Recovery-link deadlines use Auth's real clock, before mounting any UI.
  await page.clock.setSystemTime(new Date());
  const remaining = () => {
    const value = Math.floor(cutoff - performance.now());
    expect(value > 0, 'Credential sequence retains its main-work budget').toBe(true);
    return value;
  };
  const loginOptions = () => ({ retryUntilMs: Date.now() + Math.min(65000, remaining()) });
  const user = await dataset.user('STUDENT', dataset.group.id);
  const targetPath = `/api/auth/headman/students/${user.id}`;
  // One existing genuine target session remains live until the HEADMAN reset.
  // Only an actual finite Retry-After can defer this ordinary positive admission.
  const initialLogin = () =>
    dataset.request.post('/api/auth/login', {
      data: { login: user.login, password: user.password },
      timeout: Math.min(10000, remaining()),
    });
  let oldLogin = await initialLogin();
  if (oldLogin.status() === 429) {
    const header = oldLogin.headers()['retry-after'] ?? '';
    const seconds = /^[0-9]+$/.test(header) ? Number(header) : 0;
    expect(
      Number.isSafeInteger(seconds) &&
        seconds > 0 &&
        seconds <= 60 &&
        seconds * 1000 + 10000 < remaining(),
      'Owned target admission honors a bounded server Retry-After',
    ).toBe(true);
    await delay(seconds * 1000);
    oldLogin = await initialLogin();
  }
  expect(oldLogin.status(), 'Own original target session is genuinely admitted').toBe(200);
  const oldBody = await oldLogin.json();
  expect(typeof oldBody.accessToken === 'string' && oldBody.accessToken.length > 0).toBe(true);
  const oldHeaders = { Authorization: `Bearer ${oldBody.accessToken}` };
  expect((await dataset.request.get('/api/auth/session', { headers: oldHeaders })).status()).toBe(
    200,
  );
  let generatedPassword = '';
  const recovery = { url: '' };
  let commands = 0;
  const observe = (request: Request) => {
    if (
      request.method() === 'POST' &&
      new URL(request.url()).pathname === `${targetPath}/generated-password`
    )
      commands++;
  };
  page.on('request', observe);
  try {
    const openMember = async () => {
      await page.getByRole('button', { name: 'Ещё', exact: true }).click();
      await page.getByRole('button', { name: 'Группа', exact: true }).click();
      const group = page.locator('.headman-group');
      await group.locator(`[data-member-id="${user.id}"]`).click();
      await expect(
        group.getByRole('heading', { name: 'Доступ студента', exact: true }),
      ).toBeVisible();
      await expect(
        group.getByRole('button', { name: 'Сбросить пароль', exact: true }),
      ).toBeEnabled();
      return group;
    };
    await loginPwa(page, dataset.headman, loginOptions());
    const selected = await selectRole(page, 'Староста');
    expect(selected.session.activeRole).toBe('HEADMAN');
    let group = await openMember();
    await group.getByRole('button', { name: 'Сбросить пароль', exact: true }).click();
    let confirmation = page.getByRole('dialog', { name: 'Сбросить пароль?', exact: true });
    await confirmation.getByRole('button', { name: 'Отмена', exact: true }).click();
    expect(commands, 'Cancel issues no credential mutation').toBe(0);
    await group.getByRole('button', { name: 'Сбросить пароль', exact: true }).click();
    const reset = http(page, `${targetPath}/generated-password`, 'POST');
    await confirmation.getByRole('button', { name: 'Сбросить пароль', exact: true }).click();
    const resetResponse = await reset;
    expect(resetResponse.status()).toBe(200);
    const resetBody = await resetResponse.json();
    expect(
      resetBody.studentId === String(user.id) &&
        resetBody.state === 'GENERATED' &&
        resetBody.origin === 'headman_reset' &&
        typeof resetBody.password === 'string' &&
        resetBody.password.length > 0,
      'Server returns only the owned generated state',
    ).toBe(true);
    generatedPassword = resetBody.password;
    expect(
      await group
        .textContent()
        .then((text) => text?.includes(generatedPassword) && text.includes('Сброшен старостой')),
      'Current generated value and origin render privately',
    ).toBe(true);
    expect((await dataset.request.get('/api/auth/session', { headers: oldHeaders })).status()).toBe(
      401,
    );
    // Return through genuine Group navigation; the list itself retains the accepted generated value.
    await group.getByRole('button', { name: 'Назад', exact: true }).click();
    await expect
      .poll(
        async () =>
          group
            .locator(`[data-member-id="${user.id}"]`)
            .textContent()
            .then((text) =>
              Boolean(text?.includes(generatedPassword) && text.includes('Сброшен старостой')),
            ),
        { message: 'Own group list displays current generated credential privately' },
      )
      .toBe(true);
    await group.getByRole('button', { name: 'Назад', exact: true }).click();
    // JS-HEADMAN-37: the existing HEADMAN observes server semester totals, never page-length totals.
    const progressRead = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        response.request().method() === 'GET' &&
        url.pathname === `/api/schedule/groups/${dataset.group.id}/lessons` &&
        url.searchParams.get('semesterId') === String(dataset.semester.id) &&
        url.searchParams.get('size') === '1' &&
        url.searchParams.get('dateFrom') === dataset.semester.dateFrom &&
        url.searchParams.get('dateTo') === dataset.semester.dateTo
      );
    });
    await page.getByRole('button', { name: 'Предметы', exact: true }).click();
    const progressResponse = await progressRead;
    expect(progressResponse.status()).toBe(200);
    const progress = (await progressResponse.json()).semesterProgress;
    expect(
      progress?.semesterId === dataset.semester.id && Array.isArray(progress?.items),
      'Own current semester progress is genuine metadata',
    ).toBe(true);
    const lecture = progress.items.find(
      (row: { subjectId: number; lessonType: string }) =>
        row.subjectId === dataset.subject.id && row.lessonType === 'LECTURE',
    );
    expect(
      Boolean(
        lecture &&
          Number.isSafeInteger(lecture.done) &&
          Number.isSafeInteger(lecture.total) &&
          lecture.done >= 0 &&
          lecture.total > 0 &&
          lecture.done <= lecture.total,
      ),
      'Existing own lecture has authoritative bounded progress',
    ).toBe(true);
    const subjects = page.locator('.headman-subjects');
    await expect(
      subjects
        .locator(`[data-subject-id="${dataset.subject.id}"]`)
        .getByText(`Лекция · ${lecture.done} из ${lecture.total} пар пройдено`, { exact: true }),
    ).toBeVisible();
    await subjects.getByRole('button', { name: 'Назад', exact: true }).click();
    await logoutPwa(page);
    await loginPwa(page, { ...user, password: generatedPassword }, loginOptions());
    await page.getByRole('button', { name: 'Профиль', exact: true }).click();
    await page.getByRole('button', { name: 'Безопасность', exact: true }).click();
    const chosenPassword = `Student-${randomUUID()}!1`;
    await page.getByLabel('Текущий пароль *', { exact: true }).fill(generatedPassword);
    await page.getByLabel('Новый пароль *', { exact: true }).fill(chosenPassword);
    await page.getByLabel('Повтори новый пароль *', { exact: true }).fill(chosenPassword);
    const changed = http(page, '/api/auth/change-password', 'POST');
    await page.getByRole('button', { name: 'Сменить пароль', exact: true }).click();
    expect((await changed).status()).toBe(204);
    await expect(
      page.getByRole('heading', { name: 'Войти в приложение', exact: true }),
    ).toBeVisible();
    await loginPwa(page, dataset.headman, loginOptions());
    await selectRole(page, 'Староста');
    group = await openMember();
    await expect(group.getByText('Пароль сменён студентом', { exact: true })).toBeVisible();
    expect(
      await group
        .textContent()
        .then((text) =>
          Boolean(text?.includes(chosenPassword) || text?.includes(generatedPassword)),
        ),
      'Changed credential is not disclosed',
    ).toBe(false);
    await group.getByRole('button', { name: 'Выдать ссылку сброса', exact: true }).click();
    confirmation = page.getByRole('dialog', { name: 'Выдать ссылку сброса?', exact: true });
    const issued = http(page, `${targetPath}/password-reset-link`, 'POST');
    await confirmation.getByRole('button', { name: 'Выдать ссылку', exact: true }).click();
    const issuedResponse = await issued;
    expect(issuedResponse.status()).toBe(200);
    const link = await issuedResponse.json();
    expect(
      typeof link.url === 'string' &&
        link.expiresInSeconds === 3600 &&
        Number.isFinite(Date.parse(link.expiresAt)),
      'Existing trusted link contract is genuine',
    ).toBe(true);
    recovery.url = link.url;
    expect(
      (await group.getByLabel('Ссылка для студента', { exact: true }).inputValue()) ===
        recovery.url,
      'Owned recovery link stays in RAM',
    ).toBe(true);
    await group.getByRole('button', { name: 'Назад', exact: true }).click();
    await group.getByRole('button', { name: 'Назад', exact: true }).click();
    await logoutPwa(page);
    await page.goto(recovery.url);
    await expect(
      page.getByRole('heading', { name: 'Задай новый пароль', exact: true }),
    ).toBeVisible();
    const finalPassword = `Recovered-${randomUUID()}!1`;
    await page.getByLabel('Новый пароль', { exact: true }).fill(finalPassword);
    await page.getByLabel('Повтори новый пароль', { exact: true }).fill(finalPassword);
    const completed = http(page, '/api/auth/password-reset/complete', 'POST');
    await page.getByRole('button', { name: 'Сохранить пароль', exact: true }).click();
    expect((await completed).status()).toBe(204);
    await expect(
      page.getByRole('heading', { name: 'Войти в приложение', exact: true }),
    ).toBeVisible();
    const replacement = await loginPwa(page, { ...user, password: finalPassword }, loginOptions());
    expect(
      replacement.session.userId === String(user.id) &&
        replacement.session.activeRole === 'STUDENT',
      'Public self-set admits only the same owned student',
    ).toBe(true);
    await logoutPwa(page);
  } finally {
    page.off('request', observe);
    generatedPassword = '';
    recovery.url = '';
    // All synthetic rows/history remain in the already-owned fixture stand until its normal teardown.
  }
});
