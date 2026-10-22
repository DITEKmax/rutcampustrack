import { randomBytes, randomUUID } from 'node:crypto';
import { test, expect } from '../fixtures/mobile-dataset';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';
import { sanitizeTestErrors } from '../fixtures/artifact-privacy';

test.afterEach(async () => sanitizeTestErrors(test.info()));

test.describe('Admin creates user', () => {
  test('admin creates STUDENT, initial_password visible', async ({ page, browser, dataset }) => {
    await loginAs(page, TEST_USERS.admin);
    await page.goto('/admin/users');
    await page.getByRole('button', { name: /добавить|создать/i }).click();
    const dialog = page.getByRole('dialog', { name: 'Новый пользователь', exact: true });
    const lastName = `Проверка${randomUUID().replaceAll('-', '').slice(0, 12)}`;
    const telegramId = 8_000_000_000 + randomBytes(4).readUInt32BE(0);
    await dialog.getByLabel('Фамилия', { exact: true }).fill(lastName);
    await dialog.getByLabel('Имя', { exact: true }).fill('Синтетика');
    await dialog.getByLabel('Роль', { exact: true }).click();
    await page.getByRole('option', { name: 'Студент', exact: true }).click();
    await dialog.getByLabel('Учебная группа', { exact: true }).click();
    await page.getByRole('option', { name: String(dataset.group.name), exact: true }).click();
    await dialog.getByLabel('Telegram ID', { exact: true }).fill(String(telegramId));
    const created = page.waitForResponse((response) => response.request().method() === 'POST' &&
      new URL(response.url()).pathname === '/api/academic/users');
    await dialog.getByRole('button', { name: 'Создать пользователя', exact: true }).click();
    const response = await created;
    expect(response.status()).toBe(201);
    const identity = await response.json();
    expect(typeof identity.login === 'string' && identity.login.length > 0 &&
      typeof identity.initialPassword === 'string' && identity.initialPassword.length >= 8 &&
      Number.isSafeInteger(identity.id) && identity.id > 0).toBe(true);
    const owned = await dataset.admin.call('GET', `/api/academic/users/${identity.id}`);
    expect(owned.login === identity.login && owned.lastName === lastName &&
      Number(owned.groupId) === Number(dataset.group.id) && Number(owned.telegramId) === telegramId).toBe(true);
    // The supported credential carrier is the owned users-table row, which survives dialog close/reload.
    await page.reload();
    const filtered = page.waitForResponse((item) => {
      const url = new URL(item.url());
      return item.request().method() === 'GET' && url.pathname === '/api/academic/users' &&
        url.searchParams.get('search') === identity.login;
    });
    await page.getByPlaceholder('Поиск по имени или логину…', { exact: true }).fill(identity.login);
    expect((await filtered).status()).toBe(200);
    const row = page.locator('mat-row').filter({ hasText: identity.login });
    await expect(row).toHaveCount(1);
    await expect(row).toContainText(lastName);
    const visiblePassword = row.locator('code.init-password');
    await expect(visiblePassword).toBeVisible();
    expect((await visiblePassword.textContent()) === identity.initialPassword,
      'Visible owned-row credential equals the exact created response before first login').toBe(true);
    await assertNoA11yCriticalOrSerious(page);

    const context = await browser.newContext({ baseURL: new URL(page.url()).origin, ignoreHTTPSErrors: true });
    try {
      const studentPage = await context.newPage();
      const admitted = studentPage.waitForResponse((item) => item.request().method() === 'POST' &&
        new URL(item.url()).pathname === '/api/auth/login');
      await loginAs(studentPage, { login: identity.login, password: identity.initialPassword,
        role: 'STUDENT', expectedLandingPath: '/student/dashboard' });
      const login = await admitted;
      expect(login.status()).toBe(200);
      const { accessToken } = await login.json();
      const current = await context.request.get('/api/auth/session', {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(current.status()).toBe(200);
      expect(String((await current.json()).userId) === String(identity.id)).toBe(true);
    } finally { await context.close(); }
  });
});
