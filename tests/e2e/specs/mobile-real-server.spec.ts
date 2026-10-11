import { test, expect, admit, selectPwaRole } from '../fixtures/mobile-dataset';
import { loginPwa, logoutPwa } from '../fixtures/pwa-auth';
import { installTelegramHost, syntheticInitData } from '../fixtures/telegram';

// Generated credentials stay in memory; browser traces can capture login POSTs.
// Existing mandatory auth-smoke artifact policy remains unchanged.
test.use({ trace: 'off', video: 'off', screenshot: 'off' });
test.describe.configure({ mode: 'default' });

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
    await page.getByLabel('Причина', { exact: true }).selectOption('ILLNESS');
    await page.getByLabel('Комментарий', { exact: true }).fill('Синтетическая причина');
    await page.locator('.headman-journal__excuse-form input[type="file"]').setInputFiles({
      name: 'синтетическая-справка.png',
      mimeType: 'image/png',
      buffer: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF5kAAAAASUVORK5CYII=',
        'base64',
      ),
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
    await page.getByRole('button', { name: 'Открыть файл', exact: true }).click();
    const file = await opened;
    expect(file.status()).toBe(200);
    expect(file.headers()['content-type']).toContain('image/png');
    expect((await file.body()).subarray(0, 8).toString('hex')).toBe('89504e470d0a1a0a');
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

  test('assistant grant is selectable and fresh authority rejects mutation after revocation', async ({
    page,
    dataset,
  }) => {
    const { accessToken, session } = await loginPwa(page, dataset.assistant);
    expect(session.roles.some((grant) => grant.role === 'HEADMAN' && grant.selectable)).toBe(true);
    await selectPwaRole(page, 'Староста');
    await page.getByRole('button', { name: 'Посещаемость', exact: true }).click();
    await expect(
      page.getByRole('button', { name: `Был: ${dataset.assistant.displayName}`, exact: true }),
    ).toBeEnabled();
    const assistant = await admit(dataset.request, dataset.assistant, 'HEADMAN');
    await dataset.headmanActor.call(
      'DELETE',
      `/api/academic/assistants/${dataset.assistantGrant.id}`,
      undefined,
      [204],
    );
    const denied = await dataset.request.put(
      `/api/attendance/lessons/${dataset.lesson.physicalLessonId}/students/${dataset.assistant.id}`,
      {
        headers: assistant.headers(),
        data: { status: 'PRESENT', expectedStatus: 'EMPTY', expectedRevision: 'EMPTY' },
      },
    );
    expect([401, 403]).toContain(denied.status());
    await page.reload();
    await expect(page.locator('.headman-journal')).toHaveCount(0);
    const stale = await page.request.get('/api/academic/assistants/me/permissions', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect([200, 401, 403]).toContain(stale.status());
    if (stale.status() === 200)
      expect((await stale.json()).permissions ?? []).not.toContain('MARK_ATTENDANCE');
  });

  test('LIMITED ADMIN views accounts while creation is denied by UI and server', async ({
    page,
    dataset,
  }) => {
    await loginPwa(page, dataset.limitedAdmin);
    await selectPwaRole(page, 'Администратор');
    await page.getByRole('button', { name: 'Аккаунты', exact: true }).click();
    await expect(
      page.getByRole('textbox', { name: 'Найти пользователя', exact: true }),
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
    await loginPwa(page, dataset.student);
    // Abort transport once; no fabricated successful API/role/domain response.
    await page.route('**/api/v1/student/homework?**', (route) => route.abort('failed'), {
      times: 1,
    });
    await page.getByRole('button', { name: 'Задания', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Повторить', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Повторить', exact: true }).click();
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

  test('D003 signed TMA reuse admits twice and live teacher suspension denies the same proof', async ({
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
      await page.goto('/mini-app/');
      expect((await admission).status()).toBe(200);
      await expect(page.locator('.teacher-feature-owner')).toBeVisible();
    }
    await dataset.admin.call('PUT', `/api/academic/users/${dataset.teacher.id}/roles/TEACHER`, {
      status: 'SUSPENDED',
    });
    try {
      const rejected = page.waitForResponse(
        (response) =>
          new URL(response.url()).pathname === '/api/auth/tma' &&
          response.request().method() === 'POST',
      );
      await page.reload();
      expect([401, 403]).toContain((await rejected).status());
      await expect(
        page.getByRole('heading', { name: 'Не удалось открыть приложение', exact: true }),
      ).toBeVisible();
      await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
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
});
