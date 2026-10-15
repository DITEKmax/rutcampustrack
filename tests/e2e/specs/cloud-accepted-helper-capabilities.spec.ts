import { randomUUID } from 'node:crypto';
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
import { loginPwa } from '../fixtures/pwa-auth';
import { assistantPermissionCodes } from '../fixtures/assistant-permissions';

registerPrivateSuite();

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
  expect(assistantPermissionCodes(await permissions.json())).toEqual(['VIEW_STATS']);
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
}) => {
  const { user, grant } = await singleCapability(dataset, 'CANCEL_LESSONS');
  const lesson = await oneOff(dataset, dataset.today, 8);
  const { accessToken } = await loginPwa(page, user);
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
}) => {
  const { user, grant } = await singleCapability(dataset, 'MANAGE_EXCUSES');
  const requester = await dataset.user('STUDENT', dataset.group.id);
  const actor = await admit(dataset.request, requester, 'STUDENT');
  const lessons = [
    await oneOff(dataset, dataset.tomorrow, 1),
    await oneOff(dataset, dataset.tomorrow, 2),
  ];
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
  const { accessToken } = await loginPwa(page, user);
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
    expect((await stats.json()).context.groupId).toBe(dataset.group.id);
  }
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
