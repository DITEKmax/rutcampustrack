import { failureBoundary, helperRequestState } from '../fixtures/failed-case-diagnostics';


import type { TestInfo } from '@playwright/test';
import {
  test,
  expect,
  http,
  singleCapability,
  selectRole,
  openAssistant,
  deniedMark,
  oneOff,
  registerPrivateSuite,
} from '../fixtures/cloud-accepted-support';
import { loginPwa } from '../fixtures/pwa-auth';


registerPrivateSuite();

// Charge requested fixtures and body setup to this caller's monotonic budget.
// Earlier runner/auto-fixture overhead is unmeasured; the original timeout applies.
const retryStartedAt = new WeakMap<TestInfo, number>();
test.beforeEach(async () => {
  retryStartedAt.set(test.info(), performance.now());
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
    const savedPayload: unknown = response.request().postDataJSON();
    await failureBoundary(info, 'HELPER_REQUEST', async () => {
      expect(savedPayload).toEqual({
        permissions: ['VIEW_STATS'],
        expectedPermissions: ['MANAGE_EXCUSES', 'VIEW_STATS'],
      });
    }, () => helperRequestState(savedPayload, ['MANAGE_EXCUSES', 'VIEW_STATS']));
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
