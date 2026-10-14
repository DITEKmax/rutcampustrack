import { randomUUID } from 'node:crypto';
import { Actor, test, expect } from '../fixtures/mobile-dataset';
import { loginPwa, logoutPwa } from '../fixtures/pwa-auth';
import { assistantPermissionCodes } from '../fixtures/assistant-permissions';
import {
  http,
  openAssistant,
  singleCapability,
  registerPrivateSuite,
} from '../fixtures/cloud-accepted-support';

registerPrivateSuite();

// BOUNDARY-36/38: publisher identity is information, not an author-only edit grant.
// One DATE homework before its Moscow cutoff; no clock writes or archive/transfer claim.
test('MANAGE_HOMEWORK non-author edits preserve student completion and revoked authority cannot overwrite it @real-mobile', async ({
  page,
  dataset,
}) => {
  test.setTimeout(120000);
  const path = `/api/academic/homeworks/${dataset.homework.id}`;
  const historyPath = `${path}/history`;
  const feedPath = `/api/v1/student/homework?from=${dataset.today}&to=${dataset.tomorrow}`;
  const original = await dataset.headmanActor.call('GET', path);
  expect(original).toMatchObject({
    publishedBy: dataset.headman.id,
    archived: false,
    bindingMode: 'DATE',
    lessonDate: dataset.tomorrow,
    lessonNumber: null,
  });
  expect(original.bindingId).toBeGreaterThan(0);
  const originalHistory = await dataset.headmanActor.call('GET', `${historyPath}?size=100`);
  expect(Array.isArray(originalHistory.content)).toBe(true);

  const admittedStudent = await loginPwa(page, dataset.student);
  const student = new Actor(dataset.request, admittedStudent.accessToken, admittedStudent.session);
  await page.getByRole('button', { name: 'Задания', exact: true }).click();
  const completion = page.getByRole('slider', {
    name: `Выполнить: ${dataset.marker}`,
    exact: true,
  });
  await completion.focus();
  for (let index = 0; index < 4; index++) await completion.press('ArrowRight');
  const completedResponse = http(page, `/api/v1/student/homework/${original.id}/completion`, 'PUT');
  await completion.press('Enter');
  expect((await completedResponse).status()).toBe(200);
  await expect(
    page.getByRole('slider', { name: `Снять отметку: ${dataset.marker}`, exact: true }),
  ).toBeVisible();
  const completedFeed = await student.call('GET', feedPath);
  const completedItems = completedFeed.items.filter(
    (item: { id: string }) => item.id === String(original.id),
  );
  expect(completedItems).toHaveLength(1);
  const completed = completedItems[0];
  expect(completed).toMatchObject({ completed: true, archived: false });
  expect(completed.completedAt).toEqual(expect.any(String));
  // Completion is a personal STUDENT operation and does not confer content management.
  await expect(page.getByRole('button', { name: 'Изменить', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Удалить', exact: true })).toHaveCount(0);
  const deniedStudent = await dataset.request.put(path, {
    headers: student.headers(),
    data: {
      title: `${dataset.marker}-student-denied`,
      description: original.description,
      link: original.link,
      expectedRevision: original.revision,
      requestKey: randomUUID(),
    },
  });
  expect(deniedStudent.status()).toBe(403);
  expect(await dataset.headmanActor.call('GET', path)).toEqual(original);
  expect(await dataset.headmanActor.call('GET', `${historyPath}?size=100`)).toEqual(
    originalHistory,
  );
  await logoutPwa(page);

  const { user, grant } = await singleCapability(dataset, 'MANAGE_HOMEWORK');
  expect(user.id).not.toBe(original.publishedBy);
  const admittedHelper = await loginPwa(page, user);
  const helper = new Actor(dataset.request, admittedHelper.accessToken, admittedHelper.session);
  expect(admittedHelper.session.activeRole).toBe('STUDENT');
  expect(
    admittedHelper.session.roles.some((role) => role.role === 'HEADMAN' && role.selectable),
  ).toBe(false);
  expect(
    assistantPermissionCodes(await helper.call('GET', '/api/academic/assistants/me/permissions')),
  ).toEqual(['MANAGE_HOMEWORK']);
  await openAssistant(page, 'Домашние задания');
  await page.getByLabel('Дата', { exact: true }).fill(dataset.tomorrow);
  await page.getByRole('combobox', { name: /^Привязка/ }).selectOption('DATE');
  await expect(
    page
      .getByText(original.title, { exact: true })
      .and(page.locator('.assistant-homework__item strong')),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Изменить', exact: true }).click();
  const title = `${dataset.marker}-helper-edit`;
  const description = 'Синтетическая правка помощника без сброса выполнения';
  await page.getByLabel('Название', { exact: true }).fill(title);
  await page.getByLabel('Описание', { exact: true }).fill(description);
  const changedResponse = http(page, path, 'PUT');
  await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
  expect((await changedResponse).status()).toBe(200);
  await expect(
    page.getByText(title, { exact: true }).and(page.locator('.assistant-homework__item strong')),
  ).toBeVisible();
  await expect(
    page.locator('.assistant-homework__item').getByText(description, { exact: true }),
  ).toBeVisible();
  const edited = await dataset.headmanActor.call('GET', path);
  expect(edited).toMatchObject({
    id: original.id,
    title,
    description,
    publishedBy: original.publishedBy,
    bindingId: original.bindingId,
    requestKey: original.requestKey,
    bindingMode: original.bindingMode,
    lessonDate: original.lessonDate,
    lessonNumber: original.lessonNumber,
    archived: false,
  });
  expect(edited.revision).toBe(original.revision + 1);
  const history = await dataset.headmanActor.call('GET', `${historyPath}?size=100`);
  expect(history.content).toHaveLength(originalHistory.content.length + 1);
  const edit = history.content[history.content.length - 1];
  expect(edit).toMatchObject({
    action: 'EDITED',
    actorId: user.id,
    revision: edited.revision,
    before: { title: original.title, description: original.description },
    after: { title, description },
  });
  expect(edit.occurredAt).toEqual(expect.any(String));
  expect(history.content.slice(0, -1)).toEqual(originalHistory.content);
  const openedHistory = http(page, historyPath, 'GET');
  await page.getByRole('button', { name: 'История', exact: true }).click();
  expect((await openedHistory).status()).toBe(200);
  const historyRegion = page.getByRole('region', {
    name: 'История домашнего задания',
    exact: true,
  });
  await expect(historyRegion).toContainText(`Версия ${edited.revision}`);
  await expect(historyRegion).toContainText(`Пользователь #${user.id}`);

  // Keep the real editor open across live grant revocation; the server owns admission.
  await page.getByRole('button', { name: 'Изменить', exact: true }).click();
  await page.getByLabel('Название', { exact: true }).fill(`${title}-revoked-denied`);
  await dataset.headmanActor.call('DELETE', `/api/academic/assistants/${grant.id}`, undefined, [
    204,
  ]);
  const revokedResponse = http(page, path, 'PUT');
  await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
  expect((await revokedResponse).status()).toBe(403);
  expect(await dataset.headmanActor.call('GET', path)).toEqual(edited);
  expect(await dataset.headmanActor.call('GET', `${historyPath}?size=100`)).toEqual(history);
  await page.reload();
  await page.getByRole('button', { name: 'Ещё', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Действия помощника', exact: true })).toHaveCount(
    0,
  );
  await logoutPwa(page);

  const reopenedStudent = await loginPwa(page, dataset.student);
  const readStudent = new Actor(
    dataset.request,
    reopenedStudent.accessToken,
    reopenedStudent.session,
  );
  await page.getByRole('button', { name: 'Задания', exact: true }).click();
  const card = page
    .locator('.homework-card')
    .filter({ has: page.getByText(title, { exact: true }) });
  const completedSlider = card.getByRole('slider', {
    name: `Снять отметку: ${dataset.subject.name}`,
    exact: true,
  });
  await expect(completedSlider).toBeVisible();
  await expect(card).toHaveCount(1);
  await expect(card).toHaveAttribute('data-completion', 'completed');
  await expect(page.getByRole('button', { name: 'Изменить', exact: true })).toHaveCount(0);
  await page.reload();
  await page.getByRole('button', { name: 'Задания', exact: true }).click();
  await expect(completedSlider).toBeVisible();
  const reloadedFeed = await readStudent.call('GET', feedPath);
  const reloadedItems = reloadedFeed.items.filter(
    (item: { id: string }) => item.id === String(original.id),
  );
  expect(reloadedItems).toHaveLength(1);
  expect(reloadedItems[0]).toMatchObject({
    id: completed.id,
    title,
    description,
    completed: true,
    completedAt: completed.completedAt,
    archived: false,
    lessonDate: completed.lessonDate,
    bindingMode: completed.bindingMode,
  });
  expect(await dataset.headmanActor.call('GET', `${historyPath}?size=100`)).toEqual(history);
});
