import { test, expect, admit, selectPwaRole } from '../fixtures/mobile-dataset';
import { loginPwa, logoutPwa } from '../fixtures/pwa-auth';
import { installTelegramHost, syntheticInitData } from '../fixtures/telegram';
import { sanitizeTestErrors } from '../fixtures/artifact-privacy';
import { datasetHttpFailure } from '../fixtures/safe-reporter';
import { assistantPermissionCodes } from '../fixtures/assistant-permissions';

// Generated credentials stay in memory; browser traces can capture login POSTs.
// Existing mandatory auth-smoke artifact policy remains unchanged.
test.use({ trace: 'off', video: 'off', screenshot: 'off' });
test.describe.configure({ mode: 'default' });
test.afterEach(async ({}, info) => sanitizeTestErrors(info));

test.describe('Current Vue roles with real synthetic domain @real-mobile', () => {
  test('STUDENT completion persists and another group cannot mutate the homework', async ({
    page,
    dataset,
  }) => {
    const { accessToken } = await loginPwa(page, dataset.student);
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await expect(page.getByText(dataset.marker, { exact: true }).first()).toBeVisible();
    const completion = page.getByRole('slider', {
      name: `Выполнить: ${dataset.marker}`,
      exact: true,
    });
    await completion.focus();
    for (let index = 0; index < 4; index++) await completion.press('ArrowRight');
    const saved = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
          `/api/v1/student/homework/${dataset.homework.id}/completion` &&
        response.request().method() === 'PUT',
    );
    await completion.press('Enter');
    expect((await saved).status()).toBe(200);
    await expect(
      page.getByRole('slider', { name: `Снять отметку: ${dataset.marker}`, exact: true }),
    ).toBeVisible();
    await page.reload();
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await expect(
      page.getByRole('slider', { name: `Снять отметку: ${dataset.marker}`, exact: true }),
    ).toBeVisible();
    const foreign = await admit(dataset.request, dataset.foreignStudent, 'STUDENT');
    const denied = await dataset.request.put(
      `/api/v1/student/homework/${dataset.homework.id}/completion`,
      { headers: foreign.headers(), data: { completed: true } },
    );
    expect([403, 404]).toContain(denied.status());
    const read = await page.request.get(
      `/api/v1/student/homework?from=${dataset.today}&to=${dataset.tomorrow}`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    );
    expect(read.status()).toBe(200);
    expect(
      (await read.json()).items.find(
        (item: { id: string }) => String(item.id) === String(dataset.homework.id),
      ).completed,
    ).toBe(true);
  });

  test('HEADMAN marks the real roster and TEACHER reads without acquiring write permission', async ({
    page,
    dataset,
  }) => {
    await loginPwa(page, dataset.headman);
    await selectPwaRole(page, 'Староста');
    await page.getByRole('button', { name: 'Посещаемость', exact: true }).click();
    const marked = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
          `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.student.id}` &&
        response.request().method() === 'PUT',
    );
    await page
      .getByRole('button', { name: `Был: ${dataset.student.displayName}`, exact: true })
      .click();
    expect((await marked).status()).toBe(200);
    await expect(
      page.getByRole('button', {
        name: `Был: ${dataset.student.displayName}. Снять отметку`,
        exact: true,
      }),
    ).toBeVisible();
    await page
      .getByRole('button', {
        name: `Уважительная причина: ${dataset.headman.displayName}`,
        exact: true,
      })
      .click();
    await page.locator('.headman-journal__excuse-form select').selectOption('ILLNESS');
    await page.getByLabel('Комментарий', { exact: true }).fill('Синтетическая причина');
    const expectedPng = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF5kAAAAASUVORK5CYII=',
      'base64',
    );
    await page.locator('.headman-journal__excuse-form input[type="file"]').setInputFiles({
      name: 'синтетическая-справка.png',
      mimeType: 'image/png',
      buffer: expectedPng,
    });
    const attachmentSaved = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname ===
          `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.headman.id}` &&
        response.request().method() === 'PUT',
    );
    await page.getByRole('button', { name: 'Сохранить', exact: true }).click();
    expect((await attachmentSaved).status()).toBe(200);
    const attachmentPath = `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.headman.id}/attachment`;
    const opened = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === attachmentPath &&
        response.request().method() === 'GET',
    );
    // The client severs popup.opener, so observe the owning context's new page.
    const openedPopup = page.context().waitForEvent('page');
    await page.getByRole('button', { name: 'Открыть файл', exact: true }).click();
    const file = await opened;
    expect(file.status()).toBe(200);
    expect(file.headers()['content-type']).toContain('image/png');
    const popup = await openedPopup;
    try {
      await popup.waitForURL(/^blob:/);
      const openedBytesEqual = await popup.evaluate(async (expected) => {
        const response = await fetch(location.href);
        // Inspect only the fixture-sized Blob already obtained by the real client.
        if (Number(response.headers.get('content-length')) !== expected.length) return false;
        const bytes = new Uint8Array(await response.arrayBuffer());
        return (
          bytes.length === expected.length && bytes.every((byte, index) => byte === expected[index])
        );
      }, Array.from(expectedPng));
      expect(openedBytesEqual).toBe(true);
    } finally {
      await popup.close();
    }
    const foreign = await admit(dataset.request, dataset.foreignStudent, 'STUDENT');
    expect([403, 404]).toContain(
      (await dataset.request.get(attachmentPath, { headers: foreign.headers() })).status(),
    );
    await logoutPwa(page);
    const { accessToken } = await loginPwa(page, dataset.teacher);
    await expect(page.locator('.teacher-feature-owner')).toBeVisible();
    const lesson = await page.request.get(
      `/api/v1/teacher/lessons/${dataset.lesson.physicalLessonId}`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    );
    expect(lesson.status()).toBe(200);
    expect(JSON.stringify(await lesson.json())).toContain(dataset.subject.name);
    const denied = await page.request.put(
      `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.assistant.id}`,
      {
        headers: { Authorization: `Bearer ${accessToken}` },
        data: { status: 'PRESENT', expectedStatus: 'EMPTY', expectedRevision: 'EMPTY' },
      },
    );
    expect(denied.status()).toBe(403);
  });

  test('STUDENT helper marks with its capability and revocation denies the same effect', async ({
    page,
    dataset,
  }) => {
    const { accessToken, session } = await loginPwa(page, dataset.assistant);
    expect(session.activeRole).toBe('STUDENT');
    expect(session.roles.some((grant) => grant.role === 'HEADMAN' && grant.selectable)).toBe(false);
    const before = await page.request.get('/api/academic/assistants/me/permissions', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(before.status()).toBe(200);
    expect(assistantPermissionCodes(await before.json())).toContain('MARK_ATTENDANCE');
    await page.getByRole('button', { name: 'Ещё', exact: true }).click();
    await page.getByRole('button', { name: 'Действия помощника', exact: true }).click();
    await page.getByRole('button', { name: 'Посещаемость и журнал', exact: true }).click();
    await expect(
      page.getByRole('button', { name: `Был: ${dataset.assistant.displayName}`, exact: true }),
    ).toBeEnabled();
    const markPath = `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.assistant.id}`;
    const marked = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === markPath && response.request().method() === 'PUT',
    );
    await page
      .getByRole('button', { name: `Был: ${dataset.assistant.displayName}`, exact: true })
      .click();
    expect((await marked).status()).toBe(200);
    await expect(
      page.getByRole('button', {
        name: `Был: ${dataset.assistant.displayName}. Снять отметку`,
        exact: true,
      }),
    ).toBeVisible();
    const reportPath = `/api/attendance/reports/lesson/${dataset.lesson.physicalLessonId}`;
    const report = await dataset.headmanActor.call('GET', reportPath);
    const entry = report.entries.find(
      (row: { userId: number }) => Number(row.userId) === dataset.assistant.id,
    );
    expect(String(entry.status).toUpperCase()).toBe('PRESENT');
    expect(typeof entry.markRevision).toBe('string');
    await dataset.headmanActor.call(
      'DELETE',
      `/api/academic/assistants/${dataset.assistantGrant.id}`,
      undefined,
      [204],
    );
    const denied = await dataset.request.put(markPath, {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: { status: 'ABSENT', expectedStatus: 'PRESENT', expectedRevision: entry.markRevision },
    });
    expect(denied.status()).toBe(403);
    const after = await dataset.headmanActor.call('GET', reportPath);
    expect(
      String(
        after.entries.find((row: { userId: number }) => Number(row.userId) === dataset.assistant.id)
          .status,
      ).toUpperCase(),
    ).toBe('PRESENT');
    await page.reload();
    await page.getByRole('button', { name: 'Ещё', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Действия помощника', exact: true })).toHaveCount(
      0,
    );
    const stale = await page.request.get('/api/academic/assistants/me/permissions', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(stale.status()).toBe(200);
    expect(assistantPermissionCodes(await stale.json())).toEqual([]);
  });

  test('LIMITED ADMIN views accounts while creation is denied by UI and server', async ({
    page,
    dataset,
  }) => {
    await loginPwa(page, dataset.limitedAdmin);
    await selectPwaRole(page, 'Администратор');
    await page.getByRole('button', { name: 'Аккаунты', exact: true }).click();
    await expect(
      page.getByRole('searchbox', { name: 'Найти пользователя', exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'Создать пользователя', exact: true }),
    ).toHaveCount(0);
    const limited = await admit(dataset.request, dataset.limitedAdmin, 'ADMIN');
    const denied = await dataset.request.post('/api/academic/users', {
      headers: limited.headers(),
      data: {
        lastName: 'Запрещённый',
        firstName: 'Синтетика',
        role: 'STUDENT',
        groupId: dataset.group.id,
        telegramId: 999999999999,
      },
    });
    expect(denied.status()).toBe(403);
  });

  test('empty foreign-group homework and transport failure provide a real retry path', async ({
    page,
    dataset,
  }) => {
    await loginPwa(page, dataset.foreignStudent);
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await expect(
      page.getByText('Новые задания появятся здесь после публикации.', { exact: true }),
    ).toBeVisible();
    await logoutPwa(page);
    // The owner loads homework during login and retries failed reads automatically.
    // Keep the real transport unavailable until its manual-retry state is visible.
    const homeworkRoute = (url: URL) => url.pathname === '/api/v1/student/homework';
    const abortHomework: Parameters<typeof page.route>[1] = (route) =>
      route.request().method() === 'GET' ? route.abort('failed') : route.continue();
    await page.route(homeworkRoute, abortHomework);
    try {
      const failed = page.waitForEvent(
        'requestfailed',
        (request) =>
          new URL(request.url()).pathname === '/api/v1/student/homework' &&
          request.method() === 'GET',
      );
      await Promise.all([failed, loginPwa(page, dataset.student)]);
      await page.getByRole('button', { name: 'Задания', exact: true }).click();
      await expect(page.getByRole('button', { name: 'Повторить', exact: true })).toBeVisible();
    } finally {
      await page.unroute(homeworkRoute, abortHomework);
    }
    const [retried] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/v1/student/homework' &&
          response.request().method() === 'GET',
      ),
      page.getByRole('button', { name: 'Повторить', exact: true }).click(),
    ]);
    expect(retried.status()).toBe(200);
    await expect(page.getByText(dataset.marker, { exact: true }).first()).toBeVisible();
  });

  test('another live session revokes the browser session and recovery exposes login only', async ({
    page,
    dataset,
  }) => {
    const { accessToken, session } = await loginPwa(page, dataset.foreignStudent);
    const other = await admit(dataset.request, dataset.foreignStudent, 'STUDENT');
    await other.call('DELETE', `/api/auth/sessions/${session.sessionId}`, undefined, [204]);
    await page.reload();
    await expect(
      page.getByRole('heading', { name: 'Войти в приложение', exact: true }),
    ).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
    const denied = await page.request.get('/api/auth/session', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(denied.status()).toBe(401);
  });

  test('D003 reuse admits twice; suspended grant yields neutral bootstrap and protected denials', async ({
    page,
    dataset,
  }) => {
    const proof = syntheticInitData(dataset.teacher.telegramId);
    await installTelegramHost(page, proof);
    for (let attempt = 0; attempt < 2; attempt++) {
      const admission = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/tma' &&
          response.request().method() === 'POST',
      );
      const current = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/session' &&
          response.request().method() === 'GET',
      );
      await page.goto('/mini-app/');
      expect((await admission).status()).toBe(200);
      const actual = await current;
      expect(actual.status()).toBe(200);
      const session = await actual.json();
      expect(String(session.userId)).toBe(String(dataset.teacher.id));
      expect(session.activeRole).toBe('TEACHER');
      await expect(page.locator('.teacher-feature-owner')).toBeVisible();
    }
    await dataset.admin.call('PUT', `/api/academic/users/${dataset.teacher.id}/roles/TEACHER`, {
      status: 'SUSPENDED',
    });
    try {
      const neutralAdmission = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/tma' &&
          response.request().method() === 'POST',
      );
      const current = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/session' &&
          response.request().method() === 'GET',
      );
      await page.reload();
      const admitted = await neutralAdmission;
      expect(admitted.status()).toBe(200);
      const { accessToken } = await admitted.json();
      const response = await current;
      expect(response.status()).toBe(200);
      const neutral = await response.json();
      expect(neutral.activeRole ?? null).toBeNull();
      expect(
        neutral.roles.some(
          (grant: { role: string; selectable: boolean; status: string }) =>
            grant.role === 'TEACHER' && grant.status === 'SUSPENDED' && !grant.selectable,
        ),
      ).toBe(true);
      await expect(page.getByRole('heading', { name: 'Сменить роль', exact: true })).toBeVisible();
      await expect(page.locator('.teacher-feature-owner')).toHaveCount(0);
      await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
      const headers = { Authorization: `Bearer ${accessToken}` };
      const deniedRole = await page.request.put('/api/auth/session/active-role', {
        headers,
        data: { role: 'TEACHER', expectedSessionVersion: neutral.sessionVersion },
      });
      expect(deniedRole.status()).toBe(403);
      expect((await deniedRole.json()).extras.code).toBe('ROLE_NOT_SELECTABLE');
      const deniedRead = await page.request.get(
        `/api/v1/teacher/lessons/${dataset.lesson.physicalLessonId}`,
        { headers },
      );
      expect(deniedRead.status()).toBe(403);
      expect((await deniedRead.json()).code).toBe('BOOTSTRAP_SCOPE_DENIED');
      const deniedWrite = await page.request.put(
        `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.assistant.id}`,
        {
          headers,
          data: { status: 'PRESENT', expectedStatus: 'EMPTY', expectedRevision: 'EMPTY' },
        },
      );
      expect(deniedWrite.status()).toBe(403);
      expect((await deniedWrite.json()).code).toBe('BOOTSTRAP_SCOPE_DENIED');
    } finally {
      await dataset.admin.call('PUT', `/api/academic/users/${dataset.teacher.id}/roles/TEACHER`, {
        status: 'ACTIVE',
      });
    }
  });
});

test.describe('Production PWA service worker D002 @real-mobile', () => {
  test.use({ serviceWorkers: 'allow' });
  test('warm offline reload remains read-only; revoked session reconnect clears the cached user', async ({
    page,
    context,
    dataset,
  }) => {
    const { session } = await loginPwa(page, dataset.student);
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await expect(page.getByText(dataset.marker, { exact: true }).first()).toBeVisible();
    await page.evaluate(async () => {
      await navigator.serviceWorker.ready;
    });
    await expect
      .poll(() => page.evaluate(() => navigator.serviceWorker.controller !== null), {
        timeout: 15000,
      })
      .toBe(true);
    const another = await admit(dataset.request, dataset.student, 'STUDENT');
    await another.call('DELETE', `/api/auth/sessions/${session.sessionId}`, undefined, [204]);
    await context.setOffline(true);
    try {
      await page.reload();
      await page.getByRole('button', { name: 'Задания', exact: true }).click();
      await expect(page.getByText('Ты офлайн', { exact: true })).toBeVisible();
      await expect(page.getByText(dataset.marker, { exact: true }).first()).toBeVisible();
      await expect(page.getByRole('slider', { name: new RegExp(dataset.marker) })).toBeDisabled();
    } finally {
      await context.setOffline(false);
    }
    await expect(
      page.getByRole('heading', { name: 'Войти в приложение', exact: true }),
    ).toBeVisible();
    await loginPwa(page, dataset.foreignStudent);
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await expect(page.getByText(dataset.marker, { exact: true })).toHaveCount(0);
    await logoutPwa(page);
    await context.setOffline(true);
    try {
      await page.reload();
      await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
      await expect(page.getByText(dataset.marker, { exact: true })).toHaveCount(0);
    } finally {
      await context.setOffline(false);
    }
  });

  // Q04 / JS-ADMIN-01,04,07: one real account, group authority and retained history.
  test('ADMIN creates a multirole headman and restores the archived account without old authority', async ({
    page,
    dataset,
  }) => {
    const administrator = await dataset.user('ADMIN');
    const assistantPath = `/api/academic/assistants?groupId=${dataset.group.id}`;
    const existing = await dataset.headmanActor.call('GET', assistantPath);
    const active = existing._embedded?.assistantResponseList ?? existing.content ?? [];
    expect(
      active.length <= 1 &&
        active.every(
          (grant: { studentId: number }) => Number(grant.studentId) === dataset.assistant.id,
        ),
    ).toBe(true);
    // The worker's earlier revocation case leaves no active grant; a fresh lane has one.
    if (active.length === 0) {
      await dataset.headmanActor.call(
        'POST',
        '/api/academic/assistants',
        {
          groupId: dataset.group.id,
          studentId: dataset.assistant.id,
          permissions: ['MARK_ATTENDANCE'],
        },
        [201],
      );
    }
    const confirmed = await dataset.headmanActor.call('GET', assistantPath);
    const currentHelpers = confirmed._embedded?.assistantResponseList ?? confirmed.content ?? [];
    expect(currentHelpers.length).toBe(1);
    expect(Number(currentHelpers[0].studentId)).toBe(dataset.assistant.id);
    expect(currentHelpers[0].permissions).toContain('MARK_ATTENDANCE');
    const helper = await admit(dataset.request, dataset.assistant, 'STUDENT');
    const teacher = await admit(dataset.request, dataset.teacher, 'TEACHER');
    const yesterday = new Date(Date.parse(`${dataset.today}T12:00:00Z`) - 86400000)
      .toISOString()
      .slice(0, 10);
    // A past covered date survives the archive's exclusive membership end today.
    expect(yesterday >= dataset.semester.dateFrom).toBe(true);
    await loginPwa(page, administrator);
    await selectPwaRole(page, 'Администратор');
    await page.getByRole('button', { name: 'Аккаунты', exact: true }).click();
    const users = page.locator('.admin-users-screen');
    await users.getByRole('button', { name: 'Создать пользователя', exact: true }).click();
    const lastName = `${dataset.student.displayName.split(' ')[0]}Новый`;
    const telegramId = dataset.student.telegramId + 20_000_000_000;
    await users.getByLabel('Фамилия', { exact: true }).fill(lastName);
    await users.getByLabel('Имя', { exact: true }).fill('Проверка');
    await users.getByLabel('Отчество', { exact: true }).fill('Синтетическая');
    const picker = users.locator('[data-picker="group"]');
    await picker.locator('summary').click();
    await picker
      .getByRole('searchbox', { name: 'Найти группу', exact: true })
      .fill(dataset.group.name);
    await picker.getByRole('button', { name: dataset.group.name, exact: true }).click();
    await users.getByLabel('Telegram ID', { exact: true }).fill(String(telegramId));
    const [createdResponse] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/academic/users' &&
          response.request().method() === 'POST',
      ),
      users.getByRole('button', { name: 'Создать пользователя', exact: true }).click(),
    ]);
    expect(createdResponse.status()).toBe(201);
    const created = await createdResponse.json();
    expect(typeof created.login).toBe('string');
    expect(typeof created.initialPassword).toBe('string');
    const userPath = `/api/academic/users/${created.id}`;
    const original = await dataset.admin.call('GET', userPath);
    expect(typeof original.fullName === 'string' && original.fullName.trim().length > 0).toBe(true);
    expect(
      original.lastName === lastName &&
        original.firstName === 'Проверка' &&
        original.middleName === 'Синтетическая' &&
        Number(original.groupId) === dataset.group.id &&
        Number(original.telegramId) === telegramId,
    ).toBe(true);
    await users.getByRole('button', { name: 'Готово', exact: true }).click();
    await users
      .getByRole('searchbox', { name: 'Найти пользователя', exact: true })
      .fill(created.login);
    await users.getByRole('button', { name: 'Найти', exact: true }).click();
    await users.locator(`[data-user-id="${created.id}"]`).click();
    await users.getByRole('button', { name: 'Редактировать данные', exact: true }).click();
    await users.getByRole('button', { name: 'Добавить роль', exact: true }).click();
    await users.getByRole('button', { name: 'Преподаватель', exact: true }).click();
    const employeeNumber = `E2E-${dataset.group.id}-${created.id}`;
    await users.getByLabel('Табельный номер', { exact: true }).fill(employeeNumber);
    const [roleSaved] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === `${userPath}/roles/TEACHER` &&
          response.request().method() === 'PUT',
      ),
      users.getByRole('button', { name: 'Назначить роль', exact: true }).click(),
    ]);
    expect(roleSaved.status()).toBe(200);
    const multirole = await dataset.admin.call('GET', userPath);
    expect(
      ['id', 'login', 'lastName', 'firstName', 'middleName', 'groupId', 'telegramId'].every(
        (field) => multirole[field] === original[field],
      ),
    ).toBe(true);
    expect(multirole.employeeNumber === employeeNumber).toBe(true);
    expect(
      multirole.roles
        .filter((grant: { status: string }) => grant.status === 'ACTIVE')
        .map((grant: { role: string }) => grant.role)
        .sort(),
    ).toEqual(['STUDENT', 'TEACHER']);

    const historical = await dataset.headmanActor.call(
      'POST',
      '/api/schedule/one-off-lessons',
      {
        groupId: dataset.group.id,
        subjectId: dataset.subject.id,
        assignmentId: dataset.subject.assignments[0].id,
        date: yesterday,
        lessonNumber: 1,
        classroom: 'Синтетическая история',
      },
      [201, 200],
      { 'Idempotency-Key': dataset.marker.slice(-36) },
    );
    const reportPath = `/api/attendance/reports/lesson/${historical.physicalLessonId}`;
    const markPath = `/api/attendance/lessons/${historical.physicalLessonId}/students/${created.id}`;
    const initial = (await teacher.call('GET', reportPath)).entries.find(
      (entry: { userId: number }) => Number(entry.userId) === Number(created.id),
    );
    expect(initial !== undefined).toBe(true);
    // The same helper bearer succeeds before replacement and is refused afterwards.
    await helper.call('PUT', markPath, {
      status: 'PRESENT',
      expectedStatus: String(initial.status ?? 'EMPTY').toUpperCase(),
      expectedRevision: initial.markRevision ?? 'EMPTY',
    });
    const before = (await teacher.call('GET', reportPath)).entries.find(
      (entry: { userId: number }) => Number(entry.userId) === Number(created.id),
    );
    expect(before !== undefined).toBe(true);
    expect(String(before.status).toUpperCase()).toBe('PRESENT');
    expect(typeof before.markRevision).toBe('string');

    await users.getByRole('button', { name: 'Назад', exact: true }).click();
    await page.getByRole('button', { name: 'Группы', exact: true }).click();
    const groups = page.locator('.admin-groups-screen');
    await groups
      .getByRole('searchbox', { name: 'Найти группу', exact: true })
      .fill(dataset.group.name);
    await groups.getByRole('button', { name: 'Найти группу', exact: true }).click();
    const group = groups.locator(`[data-group-id="${dataset.group.id}"]`);
    await group.locator('.admin-groups-item__trigger').click();
    await group.getByRole('button', { name: 'Изменить старосту', exact: true }).click();
    const replacement = groups.locator('.admin-groups-headman');
    const [headmanPreview] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname ===
            `/api/academic/groups/${dataset.group.id}/headman/preview` &&
          new URL(response.url()).searchParams.get('studentId') === String(created.id) &&
          response.request().method() === 'GET',
      ),
      replacement.getByLabel('Выбери старосту', { exact: true }).selectOption(String(created.id)),
    ]);
    expect(headmanPreview.status()).toBe(200);
    const replacementImpact = await headmanPreview.json();
    expect(Number(replacementImpact.currentHeadmanId)).toBe(dataset.headman.id);
    expect(Number(replacementImpact.candidateId)).toBe(Number(created.id));
    expect(replacementImpact.assistantsToRevoke).toBe(1);
    await expect(replacement.getByRole('status')).toContainText('Помощников к отзыву: 1.');
    const [replaced] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === `/api/academic/groups/${dataset.group.id}/headman` &&
          response.request().method() === 'PUT',
      ),
      replacement.getByRole('button', { name: 'Подтвердить назначение', exact: true }).click(),
    ]);
    expect(replaced.status()).toBe(200);
    const replacedBody = await replaced.json();
    expect(Number(replacedBody.headmanId)).toBe(Number(created.id));
    expect(replacedBody.assistantsRevoked).toBe(1);
    const rosterPath = `/api/academic/groups/${dataset.group.id}/headman/roster`;
    const assignedRoster = await dataset.admin.call('GET', rosterPath);
    expect(Number(assignedRoster.currentHeadmanId)).toBe(Number(created.id));
    expect(assignedRoster.activeAssistantCount).toBe(0);
    const oldHeadman = await dataset.admin.call('GET', `/api/academic/users/${dataset.headman.id}`);
    expect(
      oldHeadman.roles.some(
        (grant: { role: string; status: string }) =>
          grant.role === 'HEADMAN' && grant.status === 'ACTIVE',
      ),
    ).toBe(false);
    for (const actor of [helper, dataset.headmanActor]) {
      const denied = await dataset.request.put(markPath, {
        headers: actor.headers(),
        data: {
          status: 'ABSENT',
          expectedStatus: 'PRESENT',
          expectedRevision: before.markRevision,
        },
      });
      if (actor === helper) expect(denied.status()).toBe(403);
      else expect([401, 403]).toContain(denied.status());
    }
    const afterReplacement = (await teacher.call('GET', reportPath)).entries.find(
      (entry: { userId: number }) => Number(entry.userId) === Number(created.id),
    );
    expect(
      afterReplacement.status === before.status &&
        afterReplacement.markRevision === before.markRevision,
    ).toBe(true);
    await page.reload();
    await page.getByRole('button', { name: 'Группы', exact: true }).click();
    await groups
      .getByRole('searchbox', { name: 'Найти группу', exact: true })
      .fill(dataset.group.name);
    await groups.getByRole('button', { name: 'Найти группу', exact: true }).click();
    await group.locator('.admin-groups-item__trigger').click();
    await expect(group.getByText(original.fullName, { exact: true })).toBeVisible();

    const currentHeadman = await admit(
      dataset.request,
      {
        login: created.login,
        password: created.initialPassword,
        role: 'STUDENT',
        expectedLandingPath: '/app/',
      },
      'HEADMAN',
    );
    await page.getByRole('button', { name: 'Аккаунты', exact: true }).click();
    await users
      .getByRole('searchbox', { name: 'Найти пользователя', exact: true })
      .fill(created.login);
    await users.getByRole('button', { name: 'Найти', exact: true }).click();
    await users.locator(`[data-user-id="${created.id}"]`).click();
    await users.getByRole('button', { name: 'Редактировать данные', exact: true }).click();
    const [archivePreview] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === `${userPath}/archive-preview` &&
          response.request().method() === 'GET',
      ),
      users.getByRole('button', { name: 'Архивировать аккаунт', exact: true }).click(),
    ]);
    expect(archivePreview.status()).toBe(200);
    const archiveImpact = await archivePreview.json();
    expect(Number(archiveImpact.userId)).toBe(Number(created.id));
    expect(archiveImpact.attendanceMarksCount).toBe(1);
    expect(archiveImpact.activeHeadmanGroupCount).toBe(1);
    expect(archiveImpact.requiresPassword).toBe(true);
    await expect(users.locator('.admin-users-data')).toContainText('Отметки посещаемости');
    await users.getByLabel('Твой текущий пароль', { exact: true }).fill(administrator.password);
    await users.getByRole('checkbox', { name: 'Подтверждаю', exact: true }).check();
    const [archived] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === `${userPath}/archive` &&
          response.request().method() === 'POST',
      ),
      users.getByRole('button', { name: 'Архивировать аккаунт', exact: true }).click(),
    ]);
    if (archived.status() !== 204) {
      throw datasetHttpFailure('POST', `${userPath}/archive`, archived.status());
    }
    expect(archived.status()).toBe(204);
    await expect(
      users.getByText('Аккаунт архивирован. История сохранена.', { exact: true }),
    ).toBeVisible();
    const archivedAccount = await dataset.admin.call('GET', userPath);
    expect(archivedAccount.status).toBe('ARCHIVED');
    expect(
      archivedAccount.roles.some((grant: { status: string }) => grant.status === 'ACTIVE'),
    ).toBe(false);
    const archiveDenial = await dataset.request.put(markPath, {
      headers: currentHeadman.headers(),
      data: { status: 'ABSENT', expectedStatus: 'PRESENT', expectedRevision: before.markRevision },
    });
    expect([401, 403]).toContain(archiveDenial.status());
    const afterArchive = (await teacher.call('GET', reportPath)).entries.find(
      (entry: { userId: number }) => Number(entry.userId) === Number(created.id),
    );
    expect(
      afterArchive.status === before.status && afterArchive.markRevision === before.markRevision,
    ).toBe(true);
    await page.reload();
    await page.getByRole('button', { name: 'Аккаунты', exact: true }).click();
    await users.getByRole('button', { name: 'Фильтры и сортировка', exact: true }).click();
    const archivedRoleFilter = users.locator('[data-picker="filter-role"]');
    await archivedRoleFilter.locator('summary').click();
    await archivedRoleFilter.getByRole('button', { name: 'Студенты', exact: true }).click();
    const archivedStatusFilter = users.locator('[data-picker="filter-status"]');
    await archivedStatusFilter.locator('summary').click();
    await archivedStatusFilter.getByRole('button', { name: 'Архивная', exact: true }).click();
    await users.getByRole('button', { name: 'Применить', exact: true }).click();
    await users
      .getByRole('searchbox', { name: 'Найти пользователя', exact: true })
      .fill(created.login);
    await users.getByRole('button', { name: 'Найти', exact: true }).click();
    await users.locator(`[data-user-id="${created.id}"]`).click();
    const accountStatus = users
      .locator('.admin-users-data > div')
      .filter({ has: page.locator('dt', { hasText: /^Статус аккаунта$/ }) })
      .locator('dd');
    await expect(accountStatus).toBeVisible();
    await expect(accountStatus).toHaveText(/^\s*●?\s*Аккаунт в архиве\s*$/);
    await users.getByRole('button', { name: 'Редактировать данные', exact: true }).click();
    await users.getByRole('button', { name: 'Восстановить аккаунт', exact: true }).click();
    await expect(users.getByText(/Аккаунт восстановится без активных прав/)).toBeVisible();
    await users.getByRole('checkbox', { name: 'Подтверждаю', exact: true }).check();
    const [restored] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === `${userPath}/restore` &&
          response.request().method() === 'POST',
      ),
      users.getByRole('button', { name: 'Восстановить аккаунт', exact: true }).click(),
    ]);
    expect(restored.status()).toBe(204);
    await expect(
      users.getByText('Аккаунт восстановлен без активных прав. Назначь нужные роли заново.', {
        exact: true,
      }),
    ).toBeVisible();
    const restoredAccount = await dataset.admin.call('GET', userPath);
    expect(restoredAccount.status).toBe('ACTIVE');
    expect(restoredAccount.groupId).toBeNull();
    expect(restoredAccount.headman).toBe(false);
    expect(
      ['id', 'login', 'lastName', 'firstName', 'middleName', 'telegramId', 'employeeNumber'].every(
        (field) => restoredAccount[field] === multirole[field],
      ),
    ).toBe(true);
    expect(
      restoredAccount.roles.some(
        (grant: { status: string; selectable: boolean }) =>
          grant.status === 'ACTIVE' || grant.selectable,
      ),
    ).toBe(false);
    const restoreDenial = await dataset.request.put(markPath, {
      headers: currentHeadman.headers(),
      data: { status: 'ABSENT', expectedStatus: 'PRESENT', expectedRevision: before.markRevision },
    });
    expect([401, 403]).toContain(restoreDenial.status());
    const afterRestore = (await teacher.call('GET', reportPath)).entries.find(
      (entry: { userId: number }) => Number(entry.userId) === Number(created.id),
    );
    expect(
      afterRestore.userId === before.userId &&
        afterRestore.status === before.status &&
        afterRestore.source === before.source &&
        afterRestore.markRevision === before.markRevision,
    ).toBe(true);
    const vacant = await dataset.admin.call('GET', rosterPath);
    expect(vacant.currentHeadmanId).toBeNull();
    expect(vacant.activeAssistantCount).toBe(0);
    expect(vacant.candidates.length).toBe(assignedRoster.candidates.length - 1);
    expect(
      vacant.candidates.some(
        (candidate: { id: number }) => Number(candidate.id) === Number(created.id),
      ),
    ).toBe(false);
    const draftRegistry = await dataset.admin.call(
      'GET',
      `/api/academic/groups/registry?status=DRAFT&search=${encodeURIComponent(dataset.group.name)}&size=100`,
    );
    const draft = draftRegistry.items.find(
      (entry: { id: number }) => entry.id === dataset.group.id,
    );
    expect(draft !== undefined).toBe(true);
    expect(draft.headmanFio).toBeNull();
    expect(draft.studentCount).toBe(vacant.candidates.length);
    expect(typeof draft.draftReason === 'string' && draft.draftReason.trim().length > 0).toBe(true);
    await page.getByRole('button', { name: 'Группы', exact: true }).click();
    await groups.getByRole('tab', { name: /^Черновики/ }).click();
    await groups
      .getByRole('searchbox', { name: 'Найти группу', exact: true })
      .fill(dataset.group.name);
    await groups.getByRole('button', { name: 'Найти группу', exact: true }).click();
    await group.locator('.admin-groups-item__trigger').click();
    const draftHeadman = group
      .locator('dl > div')
      .filter({ has: page.locator('dt', { hasText: /^Староста$/ }) })
      .locator('dd');
    await expect(draftHeadman).toBeVisible();
    await expect(draftHeadman).toHaveText('Не назначен');
    await expect(group).toContainText(draft.draftReason);
    await expect(
      group.locator('dl > div').filter({ hasText: 'Студентов' }).locator('dd'),
    ).toHaveText(String(draft.studentCount));
  });

  // Q03: one live multirole authority transition and the ready client's logout-all.
  test('SUSPENDED STUDENT retains active TEACHER access until UI logout-all revokes both sessions', async ({
    page,
    dataset,
    playwright,
  }) => {
    const studentRole = `/api/academic/users/${dataset.teacher.id}/roles/STUDENT`;
    await dataset.admin.call('PUT', studentRole, {
      status: 'ACTIVE',
      groupId: dataset.group.id,
      telegramId: dataset.teacher.telegramId,
    });
    const logged = await loginPwa(page, dataset.teacher);
    expect(logged.session.activeRole).toBe('STUDENT');
    expect(
      ['STUDENT', 'TEACHER'].every((role) =>
        logged.session.roles.some((grant) => grant.role === role && grant.selectable),
      ),
    ).toBe(true);
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await expect(page.getByText(dataset.marker, { exact: true }).first()).toBeVisible();
    const homeworkPath = '/api/v1/student/homework';
    expect(
      (
        await page.request.get(homeworkPath, {
          headers: { Authorization: `Bearer ${logged.accessToken}` },
        })
      ).status(),
    ).toBe(200);

    await dataset.admin.call('PUT', studentRole, { status: 'SUSPENDED' });
    // Live admission rejects the unselectable role before checking the old JWT version.
    const deniedStudent = await page.request.get(homeworkPath, {
      headers: { Authorization: `Bearer ${logged.accessToken}` },
    });
    expect(deniedStudent.status()).toBe(403);
    expect((await deniedStudent.json()).code).toBe('WRONG_ROLE');
    const [refreshed, current] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/refresh' &&
          response.request().method() === 'POST',
      ),
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/session' &&
          response.request().method() === 'GET',
      ),
      page.reload(),
    ]);
    expect(refreshed.status()).toBe(200);
    expect(current.status()).toBe(200);
    const neutral = await current.json();
    expect(neutral.activeRole ?? null).toBeNull();
    expect(
      neutral.roles.some(
        (grant: { role: string; status: string; selectable: boolean }) =>
          grant.role === 'STUDENT' && grant.status === 'SUSPENDED' && !grant.selectable,
      ),
    ).toBe(true);
    expect(
      neutral.roles.some(
        (grant: { role: string; status: string; selectable: boolean }) =>
          grant.role === 'TEACHER' && grant.status === 'ACTIVE' && grant.selectable,
      ),
    ).toBe(true);
    await expect(page.getByRole('heading', { name: 'Сменить роль', exact: true })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /Студент/ })).toBeDisabled();
    const [selected] = await Promise.all([
      page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/session/active-role' &&
          response.request().method() === 'PUT',
      ),
      selectPwaRole(page, 'Преподаватель'),
    ]);
    expect(selected.status()).toBe(200);
    const teacherAdmission = await selected.json();
    expect(teacherAdmission.session.activeRole).toBe('TEACHER');
    await expect(page.locator('.teacher-feature-owner')).toBeVisible();
    const lessonPath = `/api/v1/teacher/lessons/${dataset.lesson.physicalLessonId}`;
    const teacherHeaders = { Authorization: `Bearer ${teacherAdmission.accessToken}` };
    const assigned = await page.request.get(lessonPath, { headers: teacherHeaders });
    expect(assigned.status()).toBe(200);
    const beforeLogout = await assigned.json();
    expect(JSON.stringify(beforeLogout).includes(dataset.subject.name)).toBe(true);

    // A separate cookie jar keeps this login from replacing the browser's refresh authority.
    const origin = new URL(page.url()).origin;
    const second = await playwright.request.newContext({
      baseURL: origin,
      ignoreHTTPSErrors: true,
    });
    try {
      const other = await admit(second, dataset.teacher, 'TEACHER');
      expect(other.session.sessionId !== teacherAdmission.session.sessionId).toBe(true);
      expect((await second.get(lessonPath, { headers: other.headers() })).status()).toBe(200);
      await page.getByRole('button', { name: 'Профиль', exact: true }).click();
      const profile = page.getByRole('main', { name: 'Профиль', exact: true });
      const [listed] = await Promise.all([
        page.waitForResponse(
          (response) =>
            new URL(response.url()).pathname === '/api/auth/sessions' &&
            response.request().method() === 'GET',
        ),
        profile.getByRole('button', { name: 'Активные сеансы', exact: true }).click(),
      ]);
      expect(listed.status()).toBe(200);
      const sessions = await listed.json();
      expect(
        [teacherAdmission.session.sessionId, other.session.sessionId].every((sessionId) =>
          sessions.items.some((item: { sessionId: string }) => item.sessionId === sessionId),
        ),
      ).toBe(true);
      const screen = page.getByRole('main', { name: 'Активные сеансы', exact: true });
      await expect(screen.getByRole('list', { name: 'Список активных сеансов' })).toBeVisible();
      // Retain both actual current refresh proofs before the UI clears its own cookie.
      const browserCookie = (await page.context().cookies(`${origin}/api/auth/refresh`)).find(
        (cookie) => cookie.name === 'rct_refresh',
      );
      const secondCookie = (await second.storageState()).cookies.find(
        (cookie) => cookie.name === 'rct_refresh',
      );
      expect(Boolean(browserCookie?.value && secondCookie?.value)).toBe(true);
      expect(browserCookie?.value !== secondCookie?.value).toBe(true);
      const [loggedOut] = await Promise.all([
        page.waitForResponse(
          (response) =>
            new URL(response.url()).pathname === '/api/auth/logout-all' &&
            response.request().method() === 'POST',
        ),
        screen.getByRole('button', { name: 'Выйти на всех устройствах', exact: true }).click(),
      ]);
      expect(loggedOut.status()).toBe(204);
      await expect(page.getByRole('heading', { name: 'Войти в приложение' })).toBeVisible();
      await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
      for (const headers of [teacherHeaders, other.headers()]) {
        expect((await second.get(lessonPath, { headers })).status()).toBe(401);
      }
      for (const cookie of [browserCookie, secondCookie]) {
        const deniedRefresh = await second.post('/api/auth/refresh', {
          headers: { Cookie: `rct_refresh=${cookie!.value}`, Origin: origin },
        });
        expect(deniedRefresh.status()).toBe(401);
      }
      // Fresh admission can still read the same assignment; revocation did not alter its data.
      const fresh = await admit(second, dataset.teacher, 'TEACHER');
      const retained = await fresh.call('GET', lessonPath);
      expect(
        JSON.stringify({ lesson: retained.lesson, roster: retained.roster }) ===
          JSON.stringify({ lesson: beforeLogout.lesson, roster: beforeLogout.roster }),
      ).toBe(true);
      await expect(page.getByRole('heading', { name: 'Войти в приложение' })).toBeVisible();
      await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
    } finally {
      await second.dispose();
    }
  });
});
