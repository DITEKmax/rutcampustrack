import { randomBytes, randomUUID } from 'node:crypto';
import { test, expect } from '../fixtures/mobile-dataset';
import { TEST_USERS } from '../fixtures/users';
import { loginAs, logout } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';

// The existing dataset/private wrapper admits only the labelled disposable HTTPS lane.
test.describe('Admin creates user', () => {
  test('owned STUDENT creation exposes the exact receipt and admits that current identity', async ({ page, browser, dataset }) => {
    await loginAs(page, TEST_USERS.admin);
    await page.goto('/admin/users');
    const owner = page.locator('main#main .admin-page');
    await owner.getByRole('button', { name: 'Создать пользователя', exact: true }).click();
    const editor = owner.getByRole('region', { name: 'Создание пользователя', exact: true });
    const lastName = `Проверка${randomUUID().replaceAll('-', '').slice(0, 12)}`;
    const telegramId = 8_000_000_000 + randomBytes(4).readUInt32BE(0);
    await editor.getByRole('combobox', { name: 'Роль', exact: true }).selectOption('STUDENT');
    await editor.getByLabel('Фамилия', { exact: true }).fill(lastName);
    await editor.getByLabel('Имя', { exact: true }).fill('Синтетика');
    await editor.getByLabel('Telegram ID', { exact: true }).fill(String(telegramId));
    const group = editor.getByRole('group', { name: 'Учебная группа', exact: true });
    await group.getByLabel('Поиск группы', { exact: true }).fill(String(dataset.group.name));
    const origin = new URL(page.url()).origin;
    const options = page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.origin === origin && url.pathname === '/api/academic/groups/options' &&
        url.searchParams.get('search') === dataset.group.name && response.request().method() === 'GET';
    });
    await group.getByRole('button', { name: 'Найти', exact: true }).click();
    expect((await options).status()).toBe(200);
    await group.getByRole('combobox', { name: 'Группа', exact: true }).selectOption(String(dataset.group.id));
    const created = page.waitForResponse(response => new URL(response.url()).origin === origin &&
      new URL(response.url()).pathname === '/api/academic/users' && response.request().method() === 'POST');
    await editor.getByRole('button', { name: 'Создать учётную запись', exact: true }).click();
    const response = await created;
    expect(response.status()).toBe(201);
    expect(await response.finished()).toBeNull();
    const identity = await response.json();
    expect(typeof identity.login === 'string' && identity.login.length > 0 &&
      typeof identity.initialPassword === 'string' && identity.initialPassword.length >= 8 &&
      Number.isSafeInteger(identity.id) && identity.id > 0).toBe(true);
    const owned = await dataset.admin.call('GET', `/api/academic/users/${identity.id}`);
    expect(owned.id === identity.id && owned.login === identity.login && owned.lastName === lastName &&
      owned.firstName === 'Синтетика' && Number(owned.telegramId) === telegramId &&
      Array.isArray(owned.roles) && owned.roles.some((grant: { role: string; status: string; groupId: number }) =>
        grant.role === 'STUDENT' && grant.status === 'ACTIVE' && Number(grant.groupId) === Number(dataset.group.id)),
      'The returned user is the exact newly created fixture subject').toBe(true);

    // The creation receipt is ephemeral. Its values must match this exact POST,
    // rather than a plaintext column or an unrelated currently selected account.
    const receipt = owner.getByRole('status').filter({ has: page.getByRole('heading', { name: 'Начальный доступ', exact: true }) });
    await expect(receipt).toHaveCount(1);
    const visibleLogin = receipt.locator('p').filter({ hasText: /^\s*Логин:/ }).locator('output');
    const visiblePassword = receipt.locator('p').filter({ hasText: /^\s*Пароль:/ }).locator('output');
    await expect(visibleLogin).toBeVisible();
    await expect(visiblePassword).toBeVisible();
    expect(await visibleLogin.textContent() === identity.login && await visiblePassword.textContent() === identity.initialPassword,
      'The ephemeral receipt carries exactly the owned created credential').toBe(true);
    await receipt.getByRole('button', { name: 'Данные сохранены', exact: true }).click();
    await expect(receipt).toHaveCount(0);
    await page.reload();
    await expect(owner.locator('output.admin-secret')).toHaveCount(0);
    const toolbar = owner.locator('form.admin-toolbar');
    await toolbar.getByLabel('Поиск', { exact: true }).fill(identity.login);
    const filtered = page.waitForResponse(item => {
      const url = new URL(item.url());
      return url.origin === origin && item.request().method() === 'GET' && url.pathname === '/api/academic/users' &&
        url.searchParams.get('search') === identity.login;
    });
    await toolbar.getByRole('button', { name: 'Найти', exact: true }).click();
    const registry = await filtered;
    expect(registry.status()).toBe(200);
    expect(await registry.finished()).toBeNull();
    const row = owner.getByRole('table', { name: 'Реестр пользователей', exact: true }).locator('tbody tr')
      .filter({ has: page.getByText(identity.login, { exact: true }) });
    await expect(row).toHaveCount(1);
    await expect(row).toContainText(lastName);
    await assertNoA11yCriticalOrSerious(page);

    const context = await browser.newContext({ baseURL: origin, ignoreHTTPSErrors: true });
    try {
      const studentPage = await context.newPage();
      const admitted = studentPage.waitForResponse(item => new URL(item.url()).origin === origin &&
        new URL(item.url()).pathname === '/api/auth/login' && item.request().method() === 'POST');
      await loginAs(studentPage, { login: identity.login, password: identity.initialPassword,
        role: 'STUDENT', expectedLandingPath: '/student/home' });
      const login = await admitted;
      expect(login.status()).toBe(200);
      const { accessToken } = await login.json();
      expect(typeof accessToken === 'string' && accessToken.length > 0).toBe(true);
      const current = await context.request.get('/api/auth/session', { headers: { Authorization: `Bearer ${accessToken}` } });
      expect(current.status()).toBe(200);
      const session = await current.json();
      expect(String(session.userId) === String(identity.id) && session.activeRole === 'STUDENT' &&
        Array.isArray(session.roles) && session.roles.some((grant: { role: string; status: string; groupId: number }) =>
          grant.role === 'STUDENT' && grant.status === 'ACTIVE' && String(grant.groupId) === String(dataset.group.id)),
        'The exact created credential admits its own current user and group grant').toBe(true);
      await logout(studentPage);
    } finally { await context.close(); }
    await logout(page);
  });
});
