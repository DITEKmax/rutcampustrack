import { randomUUID } from 'node:crypto';
import type { Locator, Page, Request, Response, TestInfo } from '@playwright/test';
import {
  Actor,
  MobileDataset,
  admit,
  selectPwaRole,
  test,
  expect,
} from '../fixtures/mobile-dataset';
import { loginPwa, logoutPwa } from '../fixtures/pwa-auth';
import { assistantPermissionCodes } from '../fixtures/assistant-permissions';
import { datasetHttpFailure } from '../fixtures/safe-reporter';
import {
  http,
  openAssistant,
  singleCapability,
  registerPrivateSuite,
  oneOff,
} from '../fixtures/cloud-accepted-support';

registerPrivateSuite();

// Charge requested page/scenario setup and the body to one login budget.
// Earlier runner/auto-fixture overhead is unmeasured; the original timeout still applies.
const retryStartedAt = new WeakMap<TestInfo, number>();
test.beforeEach(async ({}, info) => {
  retryStartedAt.set(info, Date.now());
});

// These mutations must not reuse the original worker-scoped lesson/homework.
const isolated = test.extend<{ scenario: MobileDataset }>({
  scenario: async ({ dataset, teachingInstant }, use) => {
    const own = new MobileDataset(dataset.request, dataset.admin, teachingInstant);
    await own.initialize();
    await use(own);
  },
});

// BOUNDARY-36/38: publisher identity is information, not an author-only edit grant.
// One DATE homework before its Moscow cutoff; no clock writes or archive/transfer claim.
isolated(
  'MANAGE_HOMEWORK non-author edits preserve student completion and revoked authority cannot overwrite it @real-mobile',
  async ({ page, scenario: dataset }, info) => {
    test.setTimeout(120000);
    const retryUntilMs =
      (retryStartedAt.get(info) ?? 0) + Math.max(0, Math.min(info.timeout, 120000) - 30000);
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

    const admittedStudent = await loginPwa(page, dataset.student, {
      retryUntilMs: Math.min(retryUntilMs, Date.now() + 65000),
    });
    const student = new Actor(
      dataset.request,
      admittedStudent.accessToken,
      admittedStudent.session,
    );
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await selectHomeworkDate(page, dataset, dataset.tomorrow);
    const completion = page.getByRole('slider', {
      name: `Выполнить: ${dataset.marker}`,
      exact: true,
    });
    await completion.focus();
    for (let index = 0; index < 4; index++) await completion.press('ArrowRight');
    const completedResponse = http(
      page,
      `/api/v1/student/homework/${original.id}/completion`,
      'PUT',
    );
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

    const reopenedStudent = await loginPwa(page, dataset.student, {
      retryUntilMs: Math.min(retryUntilMs, Date.now() + 65000),
    });
    const readStudent = new Actor(
      dataset.request,
      reopenedStudent.accessToken,
      reopenedStudent.session,
    );
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await selectHomeworkDate(page, dataset, dataset.tomorrow);
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
    await selectHomeworkDate(page, dataset, dataset.tomorrow);
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

    // JS-HEADMAN-17 / JS-SYSTEM-10: this real UI edit must reach its dated audience.
    // The event uses the real clock, independently of the educational browser clock.
    const moscowDay = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Europe/Moscow',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
    expect(
      dataset.semester.dateFrom <= moscowDay.format(new Date(edit.occurredAt)) &&
        dataset.semester.dateFrom <= moscowDay.format(new Date()),
    ).toBe(true);
    type NotificationRow = {
      id: string;
      type: string;
      sentAt: string;
      readAt: string | null;
      revision: string;
      details: { label: string; value: string }[];
      action: { kind: string; role: string; homeworkId: string; lessonDate: string } | null;
    };
    const inboxPath = '/api/notifications/inbox';
    const matchesHomework = (row: NotificationRow, type: string, itemTitle: string) =>
      row.type === type &&
      row.action?.kind === 'homework' &&
      row.action.homeworkId === String(original.id) &&
      row.action.lessonDate === dataset.tomorrow &&
      row.details.some((detail) => detail.label === 'Задание' && detail.value === itemTitle);
    let notificationRows: NotificationRow[] = [];
    await expect
      .poll(
        async () => {
          const inbox = await readStudent.call('GET', `${inboxPath}?limit=100`);
          expect(inbox.role === 'STUDENT' && Array.isArray(inbox.items)).toBe(true);
          notificationRows = inbox.items;
          return (
            notificationRows.filter((row) => matchesHomework(row, 'HOMEWORK_UPDATED', title))
              .length === 1 &&
            notificationRows.filter((row) =>
              matchesHomework(row, 'HOMEWORK_PUBLISHED', original.title),
            ).length === 1
          );
        },
        { timeout: 20000, message: 'Real homework publication and edit reach the student inbox' },
      )
      .toBe(true);
    const updatedNotification = notificationRows.find((row) =>
      matchesHomework(row, 'HOMEWORK_UPDATED', title),
    )!;
    const publishedNotification = notificationRows.find((row) =>
      matchesHomework(row, 'HOMEWORK_PUBLISHED', original.title),
    )!;
    expect(
      updatedNotification.action?.role === 'STUDENT' &&
        updatedNotification.readAt === null &&
        Number.isFinite(Date.parse(updatedNotification.sentAt)),
    ).toBe(true);
    const notificationsDialog = page.getByRole('dialog', { name: 'Уведомления', exact: true });
    const updatedNotificationCard = notificationsDialog
      .locator('.notification-card')
      .filter({ has: page.getByText(title, { exact: true }) });
    const publishedNotificationCard = notificationsDialog
      .locator('.notification-card')
      .filter({ has: page.getByText(original.title, { exact: true }) });
    // The existing notification entry is in the Today header, not the homework list.
    await page.getByRole('button', { name: 'Сегодня', exact: true }).click();
    const [openedInbox] = await Promise.all([
      http(page, inboxPath, 'GET'),
      page.getByRole('button', { name: /^Открыть уведомления/ }).click(),
    ]);
    expect(openedInbox.status()).toBe(200);
    await expect(updatedNotificationCard).toBeVisible();
    await expect(updatedNotificationCard).toHaveAttribute('data-unread', 'true');
    await expect(publishedNotificationCard).toBeVisible();
    const readPath = `${inboxPath}/${updatedNotification.id}/read-state`;
    const notificationAction = updatedNotificationCard.locator('.notification-card__main');
    await expect(notificationAction).toBeEnabled();
    let readRequestObserved = false;
    const observeReadRequest = (request: Request) => {
      if (request.method() === 'PUT' && new URL(request.url()).pathname === readPath)
        readRequestObserved = true;
    };
    page.on('request', observeReadRequest);
    let responses: [
      PromiseSettledResult<Response>,
      PromiseSettledResult<Response>,
      PromiseSettledResult<void>,
    ];
    try {
      // Keep a simultaneous click timeout from being hidden by the response timeout.
      responses = await Promise.allSettled([
        http(page, readPath, 'PUT'),
        page.waitForResponse((response) => {
          const url = new URL(response.url());
          return (
            response.request().method() === 'GET' &&
            url.pathname === '/api/v1/student/homework' &&
            url.searchParams.get('from') === dataset.tomorrow &&
            url.searchParams.get('to') === dataset.tomorrow
          );
        }),
        notificationAction.click(),
      ]);
    } finally {
      page.off('request', observeReadRequest);
    }
    const [readResult, targetResult, clickResult] = responses;
    if (responses.some((response) => response.status === 'rejected')) {
      expect
        .soft(clickResult.status === 'fulfilled', 'Notification action click completed')
        .toBe(true);
      expect.soft(readRequestObserved, 'Notification read-state PUT was emitted').toBe(true);
      expect
        .soft(targetResult.status === 'fulfilled', 'Notification target response arrived')
        .toBe(true);
    }
    if (readResult.status === 'rejected') throw readResult.reason;
    if (targetResult.status === 'rejected') throw targetResult.reason;
    if (clickResult.status === 'rejected') throw clickResult.reason;
    const readResponse = readResult.value;
    const targetResponse = targetResult.value;
    expect(readResponse.status()).toBe(200);
    const readCommand = readResponse.request().postDataJSON();
    expect(
      readCommand.read === true && readCommand.expectedRevision === updatedNotification.revision,
    ).toBe(true);
    const readReceipt = await readResponse.json();
    const readNotification = readReceipt.item as NotificationRow;
    expect(
      readReceipt.role === 'STUDENT' &&
        readNotification.id === updatedNotification.id &&
        readNotification.sentAt === updatedNotification.sentAt &&
        readNotification.revision !== updatedNotification.revision &&
        typeof readNotification.readAt === 'string' &&
        Number.isFinite(Date.parse(readNotification.readAt)),
    ).toBe(true);
    expect(targetResponse.status()).toBe(200);
    const targetFeed = await targetResponse.json();
    const targetItems = targetFeed.items.filter((item: { id: string }) => item.id === completed.id);
    expect(targetItems.length === 1).toBe(true);
    expect(
      targetItems[0].title === title &&
        targetItems[0].description === description &&
        targetItems[0].lessonDate === dataset.tomorrow &&
        targetItems[0].completed === true &&
        targetItems[0].completedAt === completed.completedAt,
    ).toBe(true);
    await expect(notificationsDialog).toHaveCount(0);
    await expect(card).toBeVisible();
    await expect(card).toHaveAttribute('data-completion', 'completed');
    await expect(completedSlider).toBeFocused();

    await page.reload();
    await page.getByRole('button', { name: 'Сегодня', exact: true }).click();
    const [reopenedInbox] = await Promise.all([
      http(page, inboxPath, 'GET'),
      page.getByRole('button', { name: /^Открыть уведомления/ }).click(),
    ]);
    expect(reopenedInbox.status()).toBe(200);
    const durableInbox = await reopenedInbox.json();
    expect(durableInbox.role === 'STUDENT' && Array.isArray(durableInbox.items)).toBe(true);
    const durableRows = durableInbox.items as NotificationRow[];
    expect(
      durableRows.filter((row) => row.id === updatedNotification.id).length === 1 &&
        durableRows.some((row) => JSON.stringify(row) === JSON.stringify(readNotification)) &&
        durableRows.some((row) => JSON.stringify(row) === JSON.stringify(publishedNotification)),
    ).toBe(true);
    await expect(updatedNotificationCard).toBeVisible();
    await expect(updatedNotificationCard).toHaveAttribute('data-unread', 'false');
    await expect(publishedNotificationCard).toBeVisible();

    // A different already-admitted recipient can read its own event, not this student's row.
    const headmanInbox = await dataset.headmanActor.call('GET', `${inboxPath}?limit=100`);
    expect(headmanInbox.role === 'HEADMAN' && Array.isArray(headmanInbox.items)).toBe(true);
    const headmanRows = (headmanInbox.items as NotificationRow[]).filter((row) =>
      matchesHomework(row, 'HOMEWORK_UPDATED', title),
    );
    expect(
      headmanRows.length === 1 &&
        headmanRows[0].action?.role === 'HEADMAN' &&
        headmanRows[0].id !== updatedNotification.id &&
        !headmanInbox.items.some((row: NotificationRow) => row.id === updatedNotification.id),
    ).toBe(true);
    const foreignRead = await dataset.request.put(readPath, {
      headers: dataset.headmanActor.headers(),
      data: { read: false, expectedRevision: readNotification.revision },
    });
    expect(foreignRead.status()).toBe(404);
    await foreignRead.dispose();
    const unchangedInbox = await readStudent.call('GET', `${inboxPath}?limit=100`);
    expect(
      unchangedInbox.items.some(
        (row: NotificationRow) => JSON.stringify(row) === JSON.stringify(readNotification),
      ),
    ).toBe(true);

    // CLIENT-FE-NOTIFY-01: only this isolated scenario's own recipient is changed.
    // Its existing published/read-updated pair distinguishes the real unread filter.
    await notificationsDialog
      .getByRole('button', { name: 'Вернуться к предыдущему экрану', exact: true })
      .click();
    await expect(notificationsDialog).toHaveCount(0);
    const unreadCountPath = `${inboxPath}/unread-count`;
    const initialCount = await readStudent.call('GET', unreadCountPath);
    expect(initialCount.role).toBe('STUDENT');
    expect(Number.isSafeInteger(initialCount.count) && initialCount.count > 0).toBe(true);
    const notificationEntry = page.getByRole('button', { name: /^Открыть уведомления/ });
    await expect(notificationEntry).toHaveAccessibleName(
      `Открыть уведомления, непрочитанных: ${initialCount.count}`,
    );
    await expect(notificationEntry.locator('.notifications-entry__badge')).toHaveText(
      initialCount.count > 99 ? '99+' : String(initialCount.count),
    );
    const [filterInbox] = await Promise.all([
      http(page, inboxPath, 'GET'),
      notificationEntry.click(),
    ]);
    expect(filterInbox.status()).toBe(200);
    const unreadOnlyResponse = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        response.request().method() === 'GET' &&
        url.pathname === inboxPath &&
        url.searchParams.get('unreadOnly') === 'true'
      );
    });
    await notificationsDialog.getByRole('button', { name: 'Непрочитанные', exact: true }).click();
    const unreadOnly = await unreadOnlyResponse;
    expect(unreadOnly.status()).toBe(200);
    const unreadPage = await unreadOnly.json();
    const unreadRows = unreadPage.items as NotificationRow[];
    expect(
      unreadPage.role === 'STUDENT' &&
        unreadRows.every((row) => row.readAt === null) &&
        !unreadRows.some((row) => row.id === updatedNotification.id) &&
        unreadRows.some((row) => row.id === publishedNotification.id),
    ).toBe(true);
    await expect(
      notificationsDialog.getByRole('button', { name: 'Непрочитанные', exact: true }),
    ).toHaveAttribute('aria-pressed', 'true');
    await expect(updatedNotificationCard).toHaveCount(0);
    await expect(publishedNotificationCard).toBeVisible();
    const allReadResponse = http(page, `${inboxPath}/mark-all-read`, 'POST');
    const afterAllReadInbox = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        response.request().method() === 'GET' &&
        url.pathname === inboxPath &&
        url.searchParams.get('unreadOnly') === 'true'
      );
    });
    await notificationsDialog.getByRole('button', { name: 'Прочитать все', exact: true }).click();
    const allRead = await allReadResponse;
    expect(allRead.status()).toBe(200);
    expect(await allRead.json()).toMatchObject({ role: 'STUDENT', unreadCount: 0 });
    const refreshedUnread = await afterAllReadInbox;
    expect(refreshedUnread.status()).toBe(200);
    const emptyUnread = await refreshedUnread.json();
    expect(emptyUnread.role === 'STUDENT' && emptyUnread.items.length === 0).toBe(true);
    await expect(notificationsDialog.locator('.notification-card')).toHaveCount(0);
    const allInboxResponse = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        response.request().method() === 'GET' &&
        url.pathname === inboxPath &&
        url.searchParams.get('unreadOnly') === 'false'
      );
    });
    await notificationsDialog.getByRole('button', { name: 'Все', exact: true }).click();
    const allInbox = await allInboxResponse;
    expect(allInbox.status()).toBe(200);
    const allRows = (await allInbox.json()).items as NotificationRow[];
    for (const ownId of [publishedNotification.id, updatedNotification.id]) {
      const ownRow = allRows.find((row) => row.id === ownId);
      expect(typeof ownRow?.readAt === 'string' && Number.isFinite(Date.parse(ownRow.readAt))).toBe(
        true,
      );
    }
    await expect(updatedNotificationCard).toHaveAttribute('data-unread', 'false');
    await expect(publishedNotificationCard).toHaveAttribute('data-unread', 'false');
    const clearedCount = await readStudent.call('GET', unreadCountPath);
    expect(clearedCount).toMatchObject({ role: 'STUDENT', count: 0 });
    await notificationsDialog
      .getByRole('button', { name: 'Вернуться к предыдущему экрану', exact: true })
      .click();
    await expect(notificationEntry).toHaveAccessibleName('Открыть уведомления');
    await expect(notificationEntry.locator('.notifications-entry__badge')).toHaveCount(0);

    type Preference = { appPush: boolean; telegram: boolean; colorId: string };
    type Preferences = {
      revision: string;
      splitChannels: boolean;
      types: Record<string, Preference>;
      mutedUntil: string | null;
      telegramLinked: boolean;
    };
    const preferencesPath = '/api/notifications/preferences/v2';
    const [catalogResponse, preferencesResponse] = await Promise.all([
      http(page, '/api/notifications/catalog', 'GET'),
      http(page, preferencesPath, 'GET'),
      notificationEntry.click(),
    ]);
    expect(catalogResponse.status()).toBe(200);
    expect(preferencesResponse.status()).toBe(200);
    const catalog = await catalogResponse.json();
    const beforePreferences = (await preferencesResponse.json()) as Preferences;
    const updatedType = (catalog.types as { id: string; label: string }[]).find(
      (type) => type.id === 'HOMEWORK_UPDATED',
    );
    expect(typeof updatedType?.label === 'string' && updatedType.label.length > 0).toBe(true);
    const beforeChoice = beforePreferences.types.HOMEWORK_UPDATED;
    expect(typeof beforeChoice?.appPush === 'boolean').toBe(true);
    const expectedTypes = structuredClone(beforePreferences.types);
    expectedTypes.HOMEWORK_UPDATED.appPush = !beforeChoice.appPush;
    if (!beforePreferences.splitChannels)
      expectedTypes.HOMEWORK_UPDATED.telegram = !beforeChoice.appPush;
    await notificationsDialog
      .getByRole('button', { name: 'Настройки уведомлений', exact: true })
      .click();
    const settings = page.getByRole('dialog', { name: 'Настройки уведомлений', exact: true });
    const choiceName = `${updatedType!.label}: ${beforePreferences.splitChannels ? 'TMA/PWA' : 'TMA/PWA и Telegram'}`;
    const checkbox = settings.getByRole('checkbox', { name: choiceName, exact: true });
    await expect(checkbox).toBeChecked({ checked: beforeChoice.appPush });
    await checkbox.setChecked(!beforeChoice.appPush);
    const savePreferences = http(page, preferencesPath, 'PUT');
    await settings.getByRole('button', { name: 'Сохранить настройки', exact: true }).click();
    const savedResponse = await savePreferences;
    expect(savedResponse.status()).toBe(200);
    const postedPreferences = savedResponse.request().postDataJSON();
    expect(postedPreferences.expectedRevision).toBe(beforePreferences.revision);
    expect(postedPreferences.splitChannels).toBe(beforePreferences.splitChannels);
    expect(postedPreferences.types).toEqual(expectedTypes);
    expect(Object.hasOwn(postedPreferences, 'mutedUntil')).toBe(false);
    const savedPreferences = (await savedResponse.json()) as Preferences;
    expect(savedPreferences.revision !== beforePreferences.revision).toBe(true);
    expect(savedPreferences.splitChannels).toBe(beforePreferences.splitChannels);
    expect(savedPreferences.types).toEqual(expectedTypes);
    expect(savedPreferences.mutedUntil).toBe(beforePreferences.mutedUntil);
    expect(savedPreferences.telegramLinked).toBe(beforePreferences.telegramLinked);
    expect(await readStudent.call('GET', preferencesPath)).toEqual(savedPreferences);
    await page.reload();
    await page.getByRole('button', { name: 'Сегодня', exact: true }).click();
    const reloadedPreferencesResponse = http(page, preferencesPath, 'GET');
    await page.getByRole('button', { name: /^Открыть уведомления/ }).click();
    const reloadedPreferences = await reloadedPreferencesResponse;
    expect(reloadedPreferences.status()).toBe(200);
    expect(await reloadedPreferences.json()).toEqual(savedPreferences);
    await notificationsDialog
      .getByRole('button', { name: 'Настройки уведомлений', exact: true })
      .click();
    await expect(
      page
        .getByRole('dialog', { name: 'Настройки уведомлений', exact: true })
        .getByRole('checkbox', { name: choiceName, exact: true }),
    ).toBeChecked({ checked: !beforeChoice.appPush });

    await readStudent.call('POST', '/api/auth/logout-all', undefined, [204]);
    const revokedInbox = await dataset.request.get(inboxPath, { headers: readStudent.headers() });
    expect(revokedInbox.status()).toBe(401);
    await revokedInbox.dispose();
    const revokedRead = await dataset.request.put(readPath, {
      headers: readStudent.headers(),
      data: { read: false, expectedRevision: readNotification.revision },
    });
    expect(revokedRead.status()).toBe(401);
    await revokedRead.dispose();
    await page.reload();
    await expect(
      page.getByRole('heading', { name: 'Войти в приложение', exact: true }),
    ).toBeVisible();
    await expect(page.locator('.notification-card')).toHaveCount(0);
  },
);

function shifted(date: string, days: number): string {
  return new Date(Date.parse(`${date}T12:00:00Z`) + days * 86400000).toISOString().slice(0, 10);
}

async function selectHomeworkDate(page: Page, dataset: MobileDataset, date: string): Promise<void> {
  expect(date >= dataset.semester.dateFrom && date <= dataset.semester.dateTo).toBe(true);
  const monthTab = page.getByRole('tab', { name: 'Месяц', exact: true });
  const readMonth = async (action: () => Promise<unknown>): Promise<void> => {
    const read = page.waitForResponse((response) => {
      const url = new URL(response.url());
      const from = url.searchParams.get('from'),
        to = url.searchParams.get('to');
      if (
        url.pathname !== '/api/v1/student/homework' ||
        response.request().method() !== 'GET' ||
        !from ||
        !to
      )
        return false;
      const first = from.slice(0, 7) + '-01';
      const next = new Date(`${first}T12:00:00Z`);
      next.setUTCMonth(next.getUTCMonth() + 1);
      const last = shifted(next.toISOString().slice(0, 10), -1);
      return (
        from === (first < dataset.semester.dateFrom ? dataset.semester.dateFrom : first) &&
        to === (last > dataset.semester.dateTo ? dataset.semester.dateTo : last)
      );
    });
    await action();
    const response = await read;
    expect(response.status()).toBe(200);
    const feed = await response.json();
    const selected = await page
      .locator('.homework-calendar button[aria-pressed="true"]')
      .getAttribute('data-homework-date');
    expect(
      typeof selected === 'string' &&
        feed.from <= selected &&
        selected <= feed.to &&
        feed.from >= dataset.semester.dateFrom &&
        feed.to <= dataset.semester.dateTo &&
        feed.semester.id === String(dataset.semester.id),
    ).toBe(true);
    await expect(page.locator('.homework-skeleton')).toHaveCount(0);
  };
  if ((await monthTab.getAttribute('aria-selected')) !== 'true')
    await readMonth(() => monthTab.click());
  await expect(monthTab).toHaveAttribute('aria-selected', 'true');
  const selected = await page
    .locator('.homework-calendar button[aria-pressed="true"]')
    .getAttribute('data-homework-date');
  expect(typeof selected === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(selected)).toBe(true);
  const monthIndex = (value: string): number =>
    Number(value.slice(0, 4)) * 12 + Number(value.slice(5, 7));
  const steps = monthIndex(date) - monthIndex(selected!);
  for (let index = 0; index < Math.abs(steps); index++) {
    await readMonth(() =>
      page
        .getByRole('button', {
          name: steps < 0 ? 'Предыдущий период' : 'Следующий период',
          exact: true,
        })
        .click(),
    );
  }
  const target = page.locator(`.homework-calendar button[data-homework-date="${date}"]`);
  await expect(target).toBeEnabled();
  await target.click();
  await expect(target).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('.homework-group')).toHaveCount(1);
}

function homeworkCard(page: Page, title: string): Locator {
  return page.locator('.homework-card').filter({ has: page.getByText(title, { exact: true }) });
}

async function completeCard(page: Page, card: Locator, id: number): Promise<void> {
  const slider = card.getByRole('slider');
  await slider.focus();
  for (let index = 0; index < 4; index++) await slider.press('ArrowRight');
  const saved = http(page, `/api/v1/student/homework/${id}/completion`, 'PUT');
  await slider.press('Enter');
  expect((await saved).status()).toBe(200);
  await expect(card).toHaveAttribute('data-pending', 'false');
  await expect(card).toHaveAttribute('data-completion', 'completed');
  await expect(card.getByRole('slider')).toBeFocused();
}

async function publishHomework(
  dataset: MobileDataset,
  date: string,
  number: number | null,
  title: string,
  description = 'Синтетическое задание',
  link: string | null = null,
) {
  const input = {
    groupId: dataset.group.id,
    subjectId: dataset.subject.id,
    semesterId: dataset.semester.id,
    lessonType: 'LECTURE',
    lessonDate: date,
    lessonNumber: number,
    bindingMode: number === null ? 'DATE' : 'LESSON',
    requestKey: randomUUID(),
    title,
    description,
    link,
  };
  let result = await dataset.headmanActor.call(
    'POST',
    '/api/academic/homeworks',
    input,
    [200, 201, 202],
  );
  await expect
    .poll(
      async () => {
        if (result.state !== 'PENDING' && Number.isSafeInteger(result.id)) return true;
        result = await dataset.headmanActor.call(
          'POST',
          '/api/academic/homeworks',
          input,
          [200, 201, 202],
        );
        return result.state !== 'PENDING' && Number.isSafeInteger(result.id);
      },
      { timeout: 60000, message: 'Own homework publication completes with the same request key' },
    )
    .toBe(true);
  return result;
}

// JS-HEADMAN-16/17/21/28/29, B36/37/38. Helper next/placement are genuine
// API seams; Assistant Vue supplies publication/content edit. The separate
// HEADMAN carrier reads a different publisher's archived item and history.
// This does not claim that the Assistant carrier has the HEADMAN wizard.
isolated(
  'MANAGE_HOMEWORK next lesson and completed binding survive transfer then open as read-only non-author archive @real-mobile',
  async ({ page, scenario: dataset }, info) => {
    test.setTimeout(120000);
    const retryUntilMs =
      (retryStartedAt.get(info) ?? 0) + Math.max(0, Math.min(info.timeout, 120000) - 30000);
    const firstDate = shifted(dataset.today, 21);
    const editedDate = shifted(firstDate, 1);
    const targetDate = shifted(firstDate, 2);
    expect(targetDate <= dataset.semester.dateTo).toBe(true);
    const first = await oneOff(dataset, firstDate, 2);
    const second = await oneOff(dataset, editedDate, 3);
    const { user } = await singleCapability(dataset, 'MANAGE_HOMEWORK');
    const helper = await admit(dataset.request, user, 'STUDENT');
    expect(
      assistantPermissionCodes(await helper.call('GET', '/api/academic/assistants/me/permissions')),
    ).toEqual(['MANAGE_HOMEWORK']);
    const query = new URLSearchParams({
      groupId: String(dataset.group.id),
      semesterId: String(dataset.semester.id),
      subjectId: String(dataset.subject.id),
      lessonType: 'LECTURE',
      fromDate: dataset.tomorrow,
    });
    const next = await helper.call('GET', `/api/schedule/lessons/next?${query}`);
    expect(
      next.lessonId === first.physicalLessonId &&
        next.date === firstDate &&
        next.lessonNumber === 2,
    ).toBe(true);
    const foreign = await dataset.request.get(
      `/api/schedule/lessons/next?${new URLSearchParams({
        ...Object.fromEntries(query),
        groupId: String(dataset.otherGroup.id),
      })}`,
      { headers: helper.headers() },
    );
    expect(foreign.status()).toBe(403);

    await loginPwa(page, user, {
      retryUntilMs: Math.min(retryUntilMs, Date.now() + 65000),
    });
    await openAssistant(page, 'Домашние задания');
    await page.getByLabel('Дата', { exact: true }).fill(firstDate);
    await page.getByRole('button', { name: /^Пара 2 ·/ }).click();
    await page.getByRole('button', { name: 'Добавить', exact: true }).click();
    const title = `${dataset.marker}-bound`;
    await page.getByLabel('Название', { exact: true }).fill(title);
    await page.getByLabel('Описание', { exact: true }).fill('Привязанное задание помощника');
    const posted = http(page, '/api/academic/homeworks', 'POST');
    await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
    const firstPost = await posted;
    expect([200, 201, 202].includes(firstPost.status())).toBe(true);
    // A 202 is a receipt, not publication success. Retry the same actual UI intent.
    if (firstPost.status() === 202) {
      await expect
        .poll(
          async () => {
            const repeat = http(page, '/api/academic/homeworks', 'POST');
            await page.getByRole('button', { name: 'Повторить запрос', exact: true }).click();
            const response = await repeat;
            expect([200, 201, 202].includes(response.status())).toBe(true);
            return response.status() !== 202;
          },
          {
            timeout: 60000,
            message: 'Assistant publication leaves pending through its original intent',
          },
        )
        .toBe(true);
    }
    await expect(
      page.locator('.assistant-homework__item').getByText(title, { exact: true }),
    ).toBeVisible();
    const list = await helper.call(
      'GET',
      `/api/academic/homeworks?groupId=${dataset.group.id}&semesterId=${dataset.semester.id}&size=100`,
    );
    const matching = (list._embedded?.homeworkResponseList ?? list.content).filter(
      (item: { title: string }) => item.title === title,
    );
    expect(matching.length === 1).toBe(true);
    const original = matching[0];
    const path = `/api/academic/homeworks/${original.id}`;
    const historyPath = `${path}/history?size=100`;
    expect(
      original.publishedBy === user.id &&
        original.bindingMode === 'LESSON' &&
        original.lessonDate === firstDate &&
        original.lessonNumber === 2 &&
        original.archived === false,
    ).toBe(true);
    const historyBefore = await helper.call('GET', historyPath);
    const edited = await helper.call('PUT', path, {
      title,
      description: original.description,
      link: original.link,
      expectedRevision: original.revision,
      requestKey: randomUUID(),
      bindingMode: 'LESSON',
      lessonDate: editedDate,
      lessonNumber: 3,
    });
    expect(
      edited.id === original.id &&
        edited.bindingId === original.bindingId &&
        edited.publishedBy === user.id &&
        edited.requestKey === original.requestKey &&
        edited.lessonDate === editedDate &&
        edited.lessonNumber === 3 &&
        edited.revision === original.revision + 1,
    ).toBe(true);
    const historyEdited = await helper.call('GET', historyPath);
    expect(
      historyEdited.content.length === historyBefore.content.length + 1 &&
        JSON.stringify(historyEdited.content.slice(0, -1)) ===
          JSON.stringify(historyBefore.content),
    ).toBe(true);
    const edit = historyEdited.content.at(-1);
    expect(
      edit.action === 'EDITED' &&
        edit.actorId === user.id &&
        edit.before.lessonDate === firstDate &&
        edit.after.lessonDate === editedDate,
    ).toBe(true);
    const control = await publishHomework(
      dataset,
      editedDate,
      null,
      `${dataset.marker}-date-control`,
    );
    await page.getByRole('button', { name: 'Назад', exact: true }).click();
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible();
    await logoutPwa(page);

    const admitted = await loginPwa(page, dataset.student, {
      retryUntilMs: Math.min(retryUntilMs, Date.now() + 65000),
    });
    const student = new Actor(dataset.request, admitted.accessToken, admitted.session);
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    // Preserve the original real completion; view its actual lesson day explicitly.
    await student.call('PUT', `/api/v1/student/homework/${original.id}/completion`, {
      completed: true,
    });
    await page.reload();
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await selectHomeworkDate(page, dataset, editedDate);
    const completedCard = homeworkCard(page, title);
    await expect(completedCard).toHaveAttribute('data-completion', 'completed');
    const feedPath = `/api/v1/student/homework?from=${firstDate}&to=${targetDate}`;
    const completedFeed = await student.call('GET', feedPath);
    const completed = completedFeed.items.find(
      (item: { id: string }) => item.id === String(original.id),
    );
    expect(completed?.completed === true && typeof completed.completedAt === 'string').toBe(true);
    const schedule = await dataset.headmanActor.call(
      'GET',
      `/api/schedule/groups/${dataset.group.id}/lessons?dateFrom=${editedDate}&dateTo=${editedDate}&size=100`,
    );
    const physical = (schedule._embedded?.lessonResponseList ?? schedule.content).find(
      (item: { id: number }) => item.id === second.physicalLessonId,
    );
    expect(Number.isSafeInteger(physical?.occurrenceRevision)).toBe(true);
    const transfer = await dataset.headmanActor.call(
      'POST',
      `/api/schedule/lessons/${physical.id}/transfer`,
      {
        targetDate,
        targetLessonNumber: 4,
        targetRoom: 'Синтетическая новая аудитория',
        expectedRevision: String(physical.occurrenceRevision),
        requestKey: randomUUID(),
      },
      [200, 202],
    );
    await expect
      .poll(
        async () =>
          (
            await dataset.headmanActor.call(
              'GET',
              `/api/schedule/lesson-transfers/${transfer.operationId}`,
            )
          ).state,
        { timeout: 60000, message: 'Real transfer participants durably complete' },
      )
      .toBe('COMPLETED');
    const moved = await helper.call('GET', path);
    expect(
      moved.id === original.id &&
        moved.bindingId === original.bindingId &&
        moved.requestKey === original.requestKey &&
        moved.publishedBy === user.id &&
        moved.lessonDate === targetDate &&
        moved.lessonNumber === 4 &&
        moved.archived === false,
    ).toBe(true);
    expect(
      JSON.stringify(await helper.call('GET', `/api/academic/homeworks/${control.id}`)) ===
        JSON.stringify(control),
    ).toBe(true);
    const movedHistory = await helper.call('GET', historyPath);
    expect(
      JSON.stringify(movedHistory.content.slice(0, historyEdited.content.length)) ===
        JSON.stringify(historyEdited.content) &&
        movedHistory.content.length === historyEdited.content.length + 1 &&
        movedHistory.content.at(-1).action === 'LESSON_TRANSFERRED',
    ).toBe(true);
    const movedFeed = await student.call('GET', feedPath);
    const retained = movedFeed.items.filter(
      (item: { id: string }) => item.id === String(original.id),
    );
    expect(
      retained.length === 1 &&
        retained[0].completed === true &&
        retained[0].completedAt === completed.completedAt &&
        retained[0].lessonDate === targetDate,
    ).toBe(true);
    await page.reload();
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await selectHomeworkDate(page, dataset, targetDate);
    await expect(completedCard).toHaveCount(1);
    await expect(completedCard).toHaveAttribute('data-completion', 'completed');

    const terminal = await dataset.headmanActor.call(
      'GET',
      `/api/schedule/lesson-transfers/${transfer.operationId}`,
    );
    await dataset.headmanActor.call(
      'PATCH',
      `/api/schedule/lessons/${terminal.targetLessonId}/cancel`,
      { reason: 'Синтетический архив привязанного задания' },
    );
    await expect
      .poll(
        async () => {
          const response = await dataset.request.get(`${path}?archived=true`, {
            headers: helper.headers(),
          });
          try {
            if (response.status() === 404) return false;
            if (response.status() !== 200) throw datasetHttpFailure('GET', path, response.status());
            let body: { id?: unknown; archived?: unknown } | null;
            try {
              body = await response.json();
            } catch {
              throw new Error('Archived homework readback must be valid JSON');
            }
            if (body === null || body.id !== original.id || body.archived !== true)
              throw new Error('Archived homework readback must match the archived publication');
            return true;
          } finally {
            await response.dispose();
          }
        },
        {
          timeout: 60000,
          message: 'Cancelled binding becomes archived',
        },
      )
      .toBe(true);
    const archived = await helper.call('GET', `${path}?archived=true`);
    const archivedHistory = await helper.call('GET', historyPath);
    expect(
      archived.id === moved.id &&
        archived.bindingId === moved.bindingId &&
        archived.publishedBy === user.id &&
        JSON.stringify(archivedHistory) === JSON.stringify(movedHistory),
    ).toBe(true);
    const deniedArchived = await dataset.request.put(path, {
      headers: helper.headers(),
      data: {
        title: `${title}-archive-denied`,
        description: archived.description,
        link: archived.link,
        expectedRevision: archived.revision,
        requestKey: randomUUID(),
      },
    });
    expect(deniedArchived.status()).toBe(409);
    expect(
      JSON.stringify(await helper.call('GET', `${path}?archived=true`)) ===
        JSON.stringify(archived),
    ).toBe(true);
    const archivedFeed = await student.call('GET', feedPath);
    expect(archivedFeed.items.some((item: { id: string }) => item.id === String(original.id))).toBe(
      false,
    );
    const deniedCompletion = await dataset.request.put(
      `/api/v1/student/homework/${original.id}/completion`,
      { headers: student.headers(), data: { completed: false } },
    );
    expect(deniedCompletion.status()).toBe(404);
    await page.reload();
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await selectHomeworkDate(page, dataset, dataset.tomorrow);
    await expect(homeworkCard(page, dataset.homework.title)).toBeVisible();
    await selectHomeworkDate(page, dataset, targetDate);
    await expect(completedCard).toHaveCount(0);
    // Terminal completion storage is separately proved by the existing real-PG
    // HomeworkEditLifecycleIT.dateTerminalEventWinsBeforeAcademicFinalizationWithoutSuccessfulEditOrCompletionLoss.
    // Keep the exact post-transfer timestamp/identity/history assertions above;
    // ordinary students have no archived completion-read API to replace them.
    await logoutPwa(page);

    await loginPwa(page, dataset.headman);
    await selectPwaRole(page, 'Староста');
    await page.getByRole('button', { name: 'Ещё', exact: true }).click();
    await page.getByRole('button', { name: 'Домашнее задание', exact: true }).click();
    // Existing seven-day calendar, three weeks forward; no clock/semester mutation.
    const weekdayOffset = (new Date(`${dataset.today}T12:00:00Z`).getUTCDay() + 6) % 7;
    const weeksForward = Math.floor((23 + weekdayOffset) / 7);
    for (let index = 0; index < weeksForward; index++)
      await page.getByRole('button', { name: 'Следующая неделя', exact: true }).click();
    const dayText = new Intl.DateTimeFormat('ru-RU', {
      timeZone: 'Europe/Moscow',
      day: 'numeric',
      month: 'long',
      weekday: 'long',
    }).format(new Date(`${targetDate}T12:00:00Z`));
    const dayLabel = dayText.charAt(0).toUpperCase() + dayText.slice(1);
    await page.getByRole('button', { name: dayLabel, exact: true }).click();
    await page.locator('.headman-homework__item').filter({ hasText: title }).click();
    await expect(page.getByText('Архив · только просмотр', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Изменить задание', exact: true })).toHaveCount(
      0,
    );
    await expect(page.getByRole('button', { name: /Восстановить|Вернуть из архива/ })).toHaveCount(
      0,
    );
    const readHistory = http(page, `/api/academic/homeworks/${original.id}/history`, 'GET');
    await page.getByRole('button', { name: 'История изменений', exact: true }).click();
    expect((await readHistory).status()).toBe(200);
    await expect(
      page.getByRole('region', { name: 'История изменений', exact: true }),
    ).toContainText('Пара перенесена');
    expect(
      JSON.stringify(await dataset.headmanActor.call('GET', historyPath)) ===
        JSON.stringify(archivedHistory),
    ).toBe(true);
  },
);

// JS-STUDENT-20/21/22: real accepted periods, within-day order/focus and completion rollback.
// All positive content/completion reads use the backend. Only one failed PUT
// and an external material document are controlled at the dependency boundary.
isolated(
  'STUDENT multi-item feed keeps focus and rolls back a failed completion with safe external materials @real-mobile',
  async ({ page, context, scenario: dataset }, info) => {
    test.setTimeout(120000);
    const retryUntilMs =
      (retryStartedAt.get(info) ?? 0) + Math.max(0, Math.min(info.timeout, 120000) - 30000);
    await oneOff(dataset, dataset.today, 3);
    await oneOff(dataset, dataset.today, 5);
    // Publish in reverse lesson order so insertion order cannot satisfy the oracle.
    const later = await publishHomework(dataset, dataset.today, 5, `${dataset.marker}-later`);
    const mainUrl = 'https://materials.example.test/homework/main';
    const inlineUrl = 'http://materials.example.test/homework/inline';
    const description = `Материал ${inlineUrl} и javascript:window.__rctHomeworkUnsafe=1 <script>window.__rctHomeworkUnsafe=1</script>`;
    const earlier = await publishHomework(
      dataset,
      dataset.today,
      3,
      `${dataset.marker}-earlier`,
      description,
      mainUrl,
    );
    const unsafe = await publishHomework(
      dataset,
      dataset.today,
      null,
      `${dataset.marker}-unsafe`,
      description,
      'javascript:window.__rctHomeworkUnsafe=1',
    );
    const admitted = await loginPwa(page, dataset.student, {
      retryUntilMs: Math.min(retryUntilMs, Date.now() + 65000),
    });
    const student = new Actor(dataset.request, admitted.accessToken, admitted.session);
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await expect(page.getByRole('tab', { name: 'День', exact: true })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    await expect(page.getByRole('tab', { selected: true })).toHaveCount(1);
    const earlierCard = homeworkCard(page, earlier.title);
    const laterCard = homeworkCard(page, later.title);
    const unsafeCard = homeworkCard(page, unsafe.title);
    const dayTitles = [earlier.title, later.title, unsafe.title];
    await expect(earlierCard).toBeVisible();
    await expect(laterCard).toBeVisible();
    await expect(unsafeCard).toBeVisible();
    const initialOrder = (await page.locator('.homework-card__title').allTextContents()).filter(
      (title) => dayTitles.includes(title),
    );
    expect(
      initialOrder.length === 3 &&
        initialOrder.indexOf(earlier.title) < initialOrder.indexOf(later.title) &&
        initialOrder.indexOf(later.title) < initialOrder.indexOf(unsafe.title),
    ).toBe(true);

    // HTTP(S) material is a synthetic document at an exact intercepted target;
    // no external provider availability participates in acceptance.
    await context.route(mainUrl, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: '<!doctype html><title>Синтетический материал</title>',
      }),
    );
    const material = earlierCard.getByRole('button', {
      name: `Материалы к заданию: ${dataset.subject.name}`,
      exact: true,
    });
    await expect(material).toBeVisible();
    const materialDomainVisible = (await material.innerText()).includes(new URL(mainUrl).hostname);
    const [external] = await Promise.all([context.waitForEvent('page'), material.click()]);
    await external.waitForLoadState('domcontentloaded');
    expect(
      external.url() === mainUrl && (await external.evaluate(() => window.opener === null)),
    ).toBe(true);
    await external.close();
    await earlierCard.getByRole('button', { name: 'Раскрыть описание', exact: true }).click();
    await expect(earlierCard.locator('.homework-card__description')).toHaveText(description);
    // Observe the actual rendered material controls, including an absent inline
    // control, without replacing the real homework response or inventing a route.
    const inlineControl = earlierCard
      .locator('.homework-card__description')
      .locator('a, button')
      .filter({ hasText: inlineUrl });
    let inlineMaterialOpened = false;
    if ((await inlineControl.count()) === 1) {
      await context.route(inlineUrl, (route) =>
        route.fulfill({
          status: 200,
          contentType: 'text/html',
          body: '<!doctype html><title>Синтетический встроенный материал</title>',
        }),
      );
      const [inlineExternal] = await Promise.all([
        context.waitForEvent('page'),
        inlineControl.click(),
      ]);
      await inlineExternal.waitForLoadState('domcontentloaded');
      inlineMaterialOpened =
        inlineExternal.url() === inlineUrl &&
        (await inlineExternal.evaluate(() => window.opener === null));
      await inlineExternal.close();
    }
    await unsafeCard.getByRole('button', { name: 'Раскрыть описание', exact: true }).click();
    await expect(unsafeCard.locator('.homework-card__description')).toHaveText(description);
    await expect(unsafeCard).toHaveAttribute('data-material', 'unsafe');
    await expect(unsafeCard.getByRole('button', { name: /^Материалы к заданию:/ })).toHaveCount(0);
    await expect(unsafeCard.locator('a')).toHaveCount(1);
    expect(
      await unsafeCard
        .locator('a')
        .evaluateAll((links) => links.map((link) => link.getAttribute('href'))),
    ).toEqual([inlineUrl]);
    await expect(unsafeCard.locator('script, img')).toHaveCount(0);
    expect(
      await page.evaluate(
        () => !Object.prototype.hasOwnProperty.call(window, '__rctHomeworkUnsafe'),
      ),
    ).toBe(true);

    // Observe an optimistic change while the definite failed dependency is held.
    // The route never forwards this PUT; all durable checks use the actual API.
    const failedPath = `/api/v1/student/homework/${later.id}/completion`;
    let releaseFailure!: () => void;
    const heldFailure = new Promise<void>((resolve) => {
      releaseFailure = resolve;
    });
    const routePattern = `**${failedPath}`;
    await page.route(routePattern, async (route) => {
      if (route.request().method() !== 'PUT') return route.continue();
      await heldFailure;
      await route.fulfill({
        status: 503,
        contentType: 'application/problem+json',
        body: JSON.stringify({ title: 'Synthetic completion dependency unavailable', status: 503 }),
      });
    });
    const before = await student.call(
      'GET',
      `/api/v1/student/homework?from=${dataset.today}&to=${dataset.tomorrow}`,
    );
    const beforeLater = before.items.find((item: { id: string }) => item.id === String(later.id));
    expect(beforeLater?.completed === false && beforeLater.completedAt === null).toBe(true);
    let optimisticCompleted = false;
    try {
      const slider = laterCard.getByRole('slider');
      await slider.focus();
      for (let index = 0; index < 4; index++) await slider.press('ArrowRight');
      await slider.press('Enter');
      await expect(laterCard).toHaveAttribute('data-pending', 'true');
      optimisticCompleted = (await laterCard.getAttribute('data-completion')) === 'completed';
      const failedResponse = http(page, failedPath, 'PUT');
      releaseFailure();
      expect((await failedResponse).status()).toBe(503);
      await expect(laterCard).toHaveAttribute('data-pending', 'false');
      await expect(laterCard).toHaveAttribute('data-completion', 'open');
      await expect(laterCard.getByText('Отметка не сохранена', { exact: true })).toBeVisible();
      await expect(laterCard.getByText('Выполнено', { exact: true })).toHaveCount(0);
      const after = await student.call(
        'GET',
        `/api/v1/student/homework?from=${dataset.today}&to=${dataset.tomorrow}`,
      );
      const afterLater = after.items.find((item: { id: string }) => item.id === String(later.id));
      expect(afterLater?.completed === false && afterLater.completedAt === null).toBe(true);
    } finally {
      releaseFailure();
      await page.unroute(routePattern);
    }

    // Positive retry reaches the real backend, settles the old error and keeps
    // focus on the original card after its completion-driven keyed move.
    const retry = http(page, failedPath, 'PUT');
    await laterCard.getByRole('button', { name: 'Повторить', exact: true }).click();
    expect((await retry).status()).toBe(200);
    await expect(laterCard).toHaveAttribute('data-pending', 'false');
    await expect(laterCard).toHaveAttribute('data-completion', 'completed');
    await expect(laterCard.getByRole('slider')).toBeFocused();
    await expect(laterCard.getByText('Отметка не сохранена', { exact: true })).toHaveCount(0);
    await completeCard(page, earlierCard, earlier.id);
    const durable = await student.call(
      'GET',
      `/api/v1/student/homework?from=${dataset.today}&to=${dataset.tomorrow}`,
    );
    expect(
      [earlier.id, later.id].every(
        (id) =>
          durable.items.filter(
            (item: { id: string; completed: boolean; completedAt: string | null }) =>
              item.id === String(id) && item.completed && typeof item.completedAt === 'string',
          ).length === 1,
      ),
    ).toBe(true);
    // JS-STUDENT-21 orders the three items of this one day. The independent
    // tomorrow item is excluded: no global cross-day ordering rule is inferred.
    const completedOrder = (await page.locator('.homework-card__title').allTextContents()).filter(
      (title) => dayTitles.includes(title),
    );
    const completedAtBottom =
      completedOrder.length === 3 &&
      completedOrder.indexOf(unsafe.title) < completedOrder.indexOf(earlier.title) &&
      completedOrder.indexOf(earlier.title) < completedOrder.indexOf(later.title);

    // One real period path: month day panel, both semester edges, week and Today.
    await selectHomeworkDate(page, dataset, dataset.tomorrow);
    await expect(homeworkCard(page, dataset.homework.title)).toBeVisible();
    await expect(earlierCard).toHaveCount(0);
    await selectHomeworkDate(page, dataset, dataset.semester.dateFrom);
    await expect(
      page.getByRole('button', { name: 'Предыдущий период', exact: true }),
    ).toBeDisabled();
    await selectHomeworkDate(page, dataset, dataset.semester.dateTo);
    await expect(
      page.getByRole('button', { name: 'Следующий период', exact: true }),
    ).toBeDisabled();
    await selectHomeworkDate(page, dataset, dataset.today);
    await expect(
      page.locator(`.homework-calendar button[data-homework-date="${dataset.today}"]`),
    ).toHaveAttribute('aria-current', 'date');
    await expect(earlierCard).toHaveAttribute('data-completion', 'completed');
    await expect(page.getByRole('tab', { selected: true })).toHaveCount(1);
    const weekRead = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname === '/api/v1/student/homework' &&
        response.request().method() === 'GET' &&
        Boolean(url.searchParams.get('from') && url.searchParams.get('to'))
      );
    });
    await page.getByRole('tab', { name: 'Неделя', exact: true }).click();
    const weekResponse = await weekRead;
    expect(weekResponse.status()).toBe(200);
    const week = await weekResponse.json();
    expect(
      week.from <= dataset.today &&
        week.to >= dataset.today &&
        week.from >= dataset.semester.dateFrom &&
        week.to <= dataset.semester.dateTo &&
        Date.parse(`${week.to}T12:00:00Z`) - Date.parse(`${week.from}T12:00:00Z`) <= 6 * 86400000,
    ).toBe(true);
    await expect(page.getByRole('tab', { name: 'Неделя', exact: true })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    await expect(page.getByRole('tab', { selected: true })).toHaveCount(1);
    // Preserve the original bounded previous-page response and membership oracle.
    // Yesterday exists even when Today belongs to the first clipped semester week.
    await page.getByRole('tab', { name: 'День', exact: true }).click();
    await expect(page.getByRole('tab', { name: 'День', exact: true })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    const previousRead = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname === '/api/v1/student/homework' &&
        response.request().method() === 'GET' &&
        (url.searchParams.get('to') ?? dataset.today) < dataset.today
      );
    });
    await page.getByRole('button', { name: 'Предыдущий период', exact: true }).click();
    const previousResponse = await previousRead;
    expect(previousResponse.status()).toBe(200);
    const previous = await previousResponse.json();
    expect(
      previous.to < dataset.today &&
        previous.from >= dataset.semester.dateFrom &&
        Date.parse(`${previous.to}T12:00:00Z`) - Date.parse(`${previous.from}T12:00:00Z`) <=
          6 * 86400000,
    ).toBe(true);
    const returnRead = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname === '/api/v1/student/homework' &&
        response.request().method() === 'GET' &&
        !url.searchParams.has('from') &&
        !url.searchParams.has('to')
      );
    });
    await page
      .getByRole('button', { name: 'Вернуться к сегодняшним заданиям', exact: true })
      .click();
    expect((await returnRead).status()).toBe(200);
    await expect(page.getByRole('tab', { name: 'День', exact: true })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    await expect(earlierCard).toHaveAttribute('data-completion', 'completed');
    await expect(laterCard).toHaveAttribute('data-completion', 'completed');
    await page.reload();
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await expect(laterCard).toHaveAttribute('data-completion', 'completed');
    // Accepted relative-order oracle; a real failure is required before any UI
    // defect classification or correction. Do not mirror the grouping function.
    expect.soft(materialDomainVisible).toBe(true);
    expect.soft(inlineMaterialOpened).toBe(true);
    expect.soft(optimisticCompleted).toBe(true);
    expect(completedAtBottom).toBe(true);
  },
);

// Preserve the original author archive/history path and exercise D022 through
// both existing management clients with current own-group authority.
isolated(
  'HEADMAN author manual DELETE retains one read-only archive and edit history after replay @real-mobile',
  async ({ page, scenario: dataset }, info) => {
    test.setTimeout(120000);
    const caseDeadline = Date.now() + 110000;
    const author = dataset.headmanActor;
    expect(author.session.activeRole).toBe('HEADMAN');
    const path = `/api/academic/homeworks/${dataset.homework.id}`;
    const historyPath = `${path}/history`;
    const original = await author.call('GET', path);
    expect(original).toMatchObject({
      id: dataset.homework.id,
      publishedBy: dataset.headman.id,
      archived: false,
      bindingMode: 'DATE',
      lessonDate: dataset.tomorrow,
      lessonNumber: null,
    });
    expect(original.bindingId).toBeGreaterThan(0);
    const originalHistory = await author.call('GET', `${historyPath}?size=100`);
    expect(Array.isArray(originalHistory.content)).toBe(true);

    // D022: reuse the existing own assistant and restore its exact grant before
    // the original author story. Only this one additional UI login is needed.
    const grantPath = `/api/academic/assistants/${dataset.assistantGrant.id}/permissions`;
    const originalPermissions = [...dataset.assistantGrant.permissions];
    const helperRetryDeadline = Math.min(
      caseDeadline,
      (retryStartedAt.get(info) ?? Date.now()) + 90000,
    );
    expect(helperRetryDeadline - Date.now(), 'Own helper login budget remains').toBeGreaterThan(0);
    let helperPublished: Record<string, any> | undefined;
    try {
      await author.call('PATCH', grantPath, { permissions: ['MANAGE_HOMEWORK'] });
      const admittedHelper = await loginPwa(page, dataset.assistant, {
        retryUntilMs: Math.min(helperRetryDeadline, Date.now() + 65000),
      });
      const helper = new Actor(dataset.request, admittedHelper.accessToken, admittedHelper.session);
      expect(admittedHelper.session.activeRole).toBe('STUDENT');
      expect(
        assistantPermissionCodes(
          await helper.call('GET', '/api/academic/assistants/me/permissions'),
        ),
      ).toEqual(['MANAGE_HOMEWORK']);
      const publish = async (creator: Actor, suffix: string) => {
        const payload = {
          title: `${dataset.marker}-${suffix}`,
          description: 'Синтетическое задание для удаления текущим управляющим группы',
          link: null,
          subjectId: dataset.subject.id,
          groupId: dataset.group.id,
          semesterId: dataset.semester.id,
          lessonDate: dataset.tomorrow,
          lessonNumber: null,
          bindingMode: 'DATE',
          lessonType: 'LECTURE',
          requestKey: randomUUID(),
        };
        let published: Record<string, any> | undefined;
        const remaining = Math.min(20000, helperRetryDeadline - Date.now());
        expect(remaining, 'Own publication budget remains').toBeGreaterThan(0);
        await expect
          .poll(
            async () => {
              published = await creator.call(
                'POST',
                '/api/academic/homeworks',
                payload,
                [201, 200, 202],
              );
              return published.state !== 'PENDING' && Number.isSafeInteger(published.id);
            },
            { timeout: remaining, message: 'Own nonauthor deletion fixture becomes active' },
          )
          .toBe(true);
        return creator.call('GET', `/api/academic/homeworks/${published!.id}`);
      };
      helperPublished = await publish(helper, 'helper-created-delete');
      expect(helperPublished.publishedBy).toBe(dataset.assistant.id);
      const headmanPublished = await publish(author, 'headman-created-delete');
      const headmanPath = `/api/academic/homeworks/${headmanPublished.id}`;
      const headmanHistory = await author.call('GET', `${headmanPath}/history?size=100`);
      await openAssistant(page, 'Домашние задания');
      await page.getByLabel('Дата', { exact: true }).fill(dataset.tomorrow);
      await page.getByRole('combobox', { name: /^Привязка/ }).selectOption('DATE');
      const helperCard = page.locator('.assistant-homework__item').filter({
        has: page.getByText(headmanPublished.title, { exact: true }),
      });
      await expect(helperCard).toHaveCount(1);
      const helperDelete = http(page, headmanPath, 'DELETE');
      const helperDialog = page.waitForEvent('dialog');
      const helperClick = helperCard.getByRole('button', { name: 'Удалить', exact: true }).click();
      const confirmation = await helperDialog;
      expect(confirmation.type()).toBe('confirm');
      await confirmation.accept();
      await helperClick;
      expect((await helperDelete).status()).toBe(204);
      await expect(helperCard).toHaveCount(0);
      const helperDeleted = await author.call('GET', `${headmanPath}?archived=true`);
      expect({ ...helperDeleted, _links: null }).toEqual({
        ...headmanPublished,
        _links: null,
        archived: true,
      });
      expect(helperDeleted.publishedBy).toBe(dataset.headman.id);
      expect(await author.call('GET', `${headmanPath}/history?size=100`)).toEqual(headmanHistory);
      const noActive = await dataset.request.get(headmanPath, { headers: author.headers() });
      expect(noActive.status()).toBe(404);
      await noActive.dispose();
      await author.call('PATCH', grantPath, { permissions: originalPermissions });
      const revokedReplay = await dataset.request.delete(headmanPath, {
        headers: helper.headers(),
      });
      expect(revokedReplay.status()).toBe(403);
      await revokedReplay.dispose();
      expect(await author.call('GET', `${headmanPath}?archived=true`)).toEqual(helperDeleted);
      expect(await author.call('GET', `${headmanPath}/history?size=100`)).toEqual(headmanHistory);
      await page.getByRole('button', { name: 'Назад', exact: true }).click();
      await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible();
      await logoutPwa(page);
    } finally {
      try {
        await author.call('PATCH', grantPath, { permissions: originalPermissions });
      } catch {
        expect.soft(false, 'Own assistant permission cleanup succeeds').toBe(true);
      }
    }
    expect(helperPublished !== undefined, 'Own helper-authored homework was published').toBe(true);

    // Give the terminal read a real content change to preserve and display.
    const title = `${dataset.marker}-manual-delete`;
    const description = 'Синтетическая правка перед ручным архивированием';
    await author.call('PUT', path, {
      title,
      description,
      link: original.link,
      expectedRevision: original.revision,
      requestKey: randomUUID(),
    });
    const edited = await author.call('GET', path);
    expect(edited).toMatchObject({
      id: original.id,
      bindingId: original.bindingId,
      requestKey: original.requestKey,
      publishedBy: dataset.headman.id,
      title,
      description,
      archived: false,
      revision: original.revision + 1,
    });
    const history = await author.call('GET', `${historyPath}?size=100`);
    expect(history.content).toHaveLength(originalHistory.content.length + 1);
    expect(history.content.slice(0, -1)).toEqual(originalHistory.content);
    const edit = history.content[history.content.length - 1];
    expect(edit).toMatchObject({
      action: 'EDITED',
      actorId: dataset.headman.id,
      actorDisplayName: dataset.headman.displayName,
      revision: edited.revision,
      before: { title: original.title, description: original.description },
      after: { title, description },
    });
    expect(Number.isFinite(Date.parse(edit.occurredAt))).toBe(true);

    // A personal STUDENT grant cannot delete published content, even by ID.
    const student = await admit(dataset.request, dataset.student, 'STUDENT');
    const denied = await dataset.request.delete(path, { headers: student.headers() });
    expect(denied.status()).toBe(403);
    await denied.dispose();
    expect(await author.call('GET', path)).toEqual(edited);
    expect(await author.call('GET', `${historyPath}?size=100`)).toEqual(history);

    // A fresh ACTIVE durable binding must traverse the real Schedule archive
    // RPC before this synchronous 204; an already-archived shortcut is excluded.
    await author.call('DELETE', path, undefined, [204]);
    const activeRead = await dataset.request.get(path, { headers: author.headers() });
    expect(activeRead.status()).toBe(404);
    await activeRead.dispose();
    const archived = await author.call('GET', `${path}?archived=true`);
    expect(archived).toMatchObject({
      id: edited.id,
      bindingId: edited.bindingId,
      requestKey: edited.requestKey,
      publishedBy: edited.publishedBy,
      groupId: edited.groupId,
      subjectId: edited.subjectId,
      semesterId: edited.semesterId,
      bindingMode: edited.bindingMode,
      lessonDate: edited.lessonDate,
      lessonNumber: edited.lessonNumber,
      title,
      description,
      link: edited.link,
      revision: edited.revision,
      archived: true,
    });
    expect(await author.call('GET', `${historyPath}?size=100`)).toEqual(history);
    const archiveListPath = `/api/academic/homeworks?groupId=${dataset.group.id}&semesterId=${dataset.semester.id}&archived=true&size=100`;
    const archiveList = await author.call('GET', archiveListPath);
    expect(Array.isArray(archiveList._embedded?.homeworkResponseList)).toBe(true);
    expect(
      archiveList._embedded.homeworkResponseList.filter(
        (item: { id: number }) => item.id === original.id,
      ),
    ).toHaveLength(1);

    await author.call('DELETE', path, undefined, [204]);
    expect(await author.call('GET', `${path}?archived=true`)).toEqual(archived);
    expect(await author.call('GET', `${historyPath}?size=100`)).toEqual(history);
    expect(await author.call('GET', archiveListPath)).toEqual(archiveList);
    const terminalEdit = await dataset.request.put(path, {
      headers: author.headers(),
      data: {
        title: `${title}-denied`,
        description,
        link: edited.link,
        expectedRevision: edited.revision,
        requestKey: randomUUID(),
      },
    });
    expect(terminalEdit.status()).toBe(409);
    await terminalEdit.dispose();
    expect(await author.call('GET', `${path}?archived=true`)).toEqual(archived);
    expect(await author.call('GET', `${historyPath}?size=100`)).toEqual(history);

    await loginPwa(page, dataset.headman, {
      retryUntilMs: Math.min(caseDeadline, Date.now() + 65000),
    });
    await selectPwaRole(page, 'Староста');
    await page.getByRole('button', { name: 'Ещё', exact: true }).click();
    await page.getByRole('button', { name: 'Домашнее задание', exact: true }).click();
    const weekdayOffset = (new Date(`${dataset.today}T12:00:00Z`).getUTCDay() + 6) % 7;
    if (weekdayOffset === 6)
      await page.getByRole('button', { name: 'Следующая неделя', exact: true }).click();
    const dayText = new Intl.DateTimeFormat('ru-RU', {
      timeZone: 'Europe/Moscow',
      day: 'numeric',
      month: 'long',
      weekday: 'long',
    }).format(new Date(`${dataset.tomorrow}T12:00:00Z`));
    const dayLabel = dayText.charAt(0).toUpperCase() + dayText.slice(1);
    await page.getByRole('button', { name: dayLabel, exact: true }).click();
    const card = page.locator(`.headman-homework__item[data-homework-id="${original.id}"]`);
    await expect(card).toHaveCount(1);
    await expect(card).toContainText(title);
    await card.click();
    await expect(page.getByText('Архив · только просмотр', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Изменить задание', exact: true })).toHaveCount(
      0,
    );
    await expect(
      page.getByRole('button', { name: /Удалить|Восстановить|Вернуть из архива/ }),
    ).toHaveCount(0);
    const openedHistory = http(page, historyPath, 'GET');
    await page.getByRole('button', { name: 'История изменений', exact: true }).click();
    const historyRead = await openedHistory;
    expect(historyRead.status()).toBe(200);
    expect((await historyRead.json()).content).toEqual(history.content);
    const region = page.getByRole('region', { name: 'История изменений', exact: true });
    await expect(region.locator('li')).toHaveCount(history.content.length);
    await expect(region).toContainText(dataset.headman.displayName);
    await expect(region).toContainText('Задание изменено');
    await expect(region).toContainText('название');
    await expect(region).toContainText('текст');
    expect(await author.call('GET', `${path}?archived=true`)).toEqual(archived);
    expect(await author.call('GET', `${historyPath}?size=100`)).toEqual(history);

    // The same HEADMAN login now deletes another creator's current homework.
    // A dismissed native confirmation cannot issue a DELETE.
    const helperItem = helperPublished!;
    const helperPath = `/api/academic/homeworks/${helperItem.id}`;
    const helperHistory = await author.call('GET', `${helperPath}/history?size=100`);
    await page.getByRole('button', { name: 'Назад', exact: true }).click();
    await page.getByRole('button', { name: 'Назад', exact: true }).click();
    const helperCreatedCard = page.locator(
      `.headman-homework__item[data-homework-id="${helperItem.id}"]`,
    );
    await expect(helperCreatedCard).toHaveCount(1);
    await helperCreatedCard.click();
    let deleteRequests = 0;
    const countDelete = (request: Request) => {
      if (new URL(request.url()).pathname === helperPath && request.method() === 'DELETE')
        deleteRequests += 1;
    };
    page.on('request', countDelete);
    try {
      const deleteButton = page.getByRole('button', { name: 'Удалить задание', exact: true });
      const cancelDialog = page.waitForEvent('dialog');
      const cancelClick = deleteButton.click();
      const cancelled = await cancelDialog;
      expect(cancelled.type()).toBe('confirm');
      await cancelled.dismiss();
      await cancelClick;
      expect(deleteRequests).toBe(0);
      expect(await author.call('GET', helperPath)).toEqual(helperItem);
      expect(await author.call('GET', `${helperPath}/history?size=100`)).toEqual(helperHistory);
      const headmanDelete = http(page, helperPath, 'DELETE');
      const confirmDialog = page.waitForEvent('dialog');
      const confirmClick = deleteButton.click();
      await (await confirmDialog).accept();
      await confirmClick;
      expect((await headmanDelete).status()).toBe(204);
      await expect(page.getByText('Архив · только просмотр', { exact: true })).toBeVisible();
      await expect(deleteButton).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Изменить задание', exact: true })).toHaveCount(
        0,
      );
      expect(deleteRequests).toBe(1);
      const helperArchived = await author.call('GET', `${helperPath}?archived=true`);
      expect({ ...helperArchived, _links: null }).toEqual({
        ...helperItem,
        _links: null,
        archived: true,
      });
      expect(helperArchived.publishedBy).toBe(dataset.assistant.id);
      const activeHelper = await dataset.request.get(helperPath, { headers: author.headers() });
      expect(activeHelper.status()).toBe(404);
      await activeHelper.dispose();
      expect(await author.call('GET', `${helperPath}/history?size=100`)).toEqual(helperHistory);
      const deletedHistory = http(page, `${helperPath}/history`, 'GET');
      await page.getByRole('button', { name: 'История изменений', exact: true }).click();
      expect((await deletedHistory).status()).toBe(200);
      const helperHistoryRegion = page.getByRole('region', {
        name: 'История изменений',
        exact: true,
      });
      await expect(helperHistoryRegion.locator('li')).toHaveCount(helperHistory.content.length);
      expect(await author.call('GET', `${helperPath}/history?size=100`)).toEqual(helperHistory);
    } finally {
      page.off('request', countDelete);
    }
  },
);
