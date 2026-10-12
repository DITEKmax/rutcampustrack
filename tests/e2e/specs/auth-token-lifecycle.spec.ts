import { expect, test } from '@playwright/test';
import { loginPwa, logoutPwa } from '../fixtures/pwa-auth';
import { TEST_USERS } from '../fixtures/users';

const REFRESH_COOKIE = 'rct_refresh';

/** Current Vue PWA + real gateway/auth/DB. Security expectations come from
 * AuthCookies, ActiveRolePolicy and the durable session authority. */
test.describe('Vue PWA auth lifecycle @smoke', () => {
  test('wrong password does not establish a session', async ({ page, context }) => {
    await page.goto('/app/');
    await page.getByLabel('Логин', { exact: true }).fill(TEST_USERS.student.login);
    await page.getByLabel('Пароль', { exact: true }).fill('WRONG_PASSWORD');
    const response = page.waitForResponse(
      (response) => new URL(response.url()).pathname === '/api/auth/login',
    );
    await page.getByRole('button', { name: 'Войти', exact: true }).click();
    expect((await response).status()).toBe(401);
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Войти в приложение' })).toBeVisible();
    expect((await context.cookies()).some((cookie) => cookie.name === REFRESH_COOKIE)).toBe(false);
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
  });

  test('student login issues a protected cookie and uses the student grant', async ({
    page,
    context,
  }) => {
    const { accessToken, session } = await loginPwa(page, TEST_USERS.student);
    expect(session.activeRole).toBe('STUDENT');
    const refresh = (await context.cookies()).find((cookie) => cookie.name === REFRESH_COOKIE);
    expect(refresh).toBeDefined();
    expect(refresh!.httpOnly).toBe(true);
    expect(refresh!.secure).toBe(true);
    expect(refresh!.sameSite).toBe('Strict');
    expect(refresh!.path).toBe('/api/auth');
    expect(refresh!.value.length).toBeGreaterThan(0);
    const stores = await page.evaluate(() =>
      JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]),
    );
    expect(stores.includes(accessToken)).toBe(false);
  });

  test('student cannot select admin; rejected selection preserves the current role', async ({
    page,
  }) => {
    const { accessToken, session } = await loginPwa(page, TEST_USERS.student);
    const headers = { Authorization: `Bearer ${accessToken}` };
    const denied = await page.request.put('/api/auth/session/active-role', {
      headers,
      data: { role: 'ADMIN', expectedSessionVersion: session.sessionVersion },
    });
    expect(denied.status()).toBe(403);
    expect((await denied.json()).extras.code).toBe('ROLE_NOT_GRANTED');
    const current = await page.request.get('/api/auth/session', { headers });
    expect(current.status()).toBe(200);
    expect((await current.json()).activeRole).toBe('STUDENT');
  });

  test('admin bootstrap requires explicit role selection in the Vue client', async ({ page }) => {
    const { session } = await loginPwa(page, TEST_USERS.admin);
    expect(session.activeRole).toBeNull();
    const selected = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/auth/session/active-role' &&
        response.request().method() === 'PUT',
    );
    await page.getByRole('button', { name: /Администратор/ }).click();
    const response = await selected;
    expect(response.status()).toBe(200);
    expect((await response.json()).session.activeRole).toBe('ADMIN');
    await expect(page.getByRole('heading', { name: 'Профиль', exact: true })).toBeVisible();
  });

  test('refresh rotates the cookie and reload recovers the same user session', async ({
    page,
    context,
  }) => {
    const { session } = await loginPwa(page, TEST_USERS.student);
    const before = (await context.cookies()).find((cookie) => cookie.name === REFRESH_COOKIE)!;
    const refreshed = await page.evaluate(async () => {
      const response = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' });
      const body = await response.json();
      return {
        status: response.status,
        hasAccessToken: typeof body.accessToken === 'string' && body.accessToken.length > 0,
      };
    });
    expect(refreshed).toEqual({ status: 200, hasAccessToken: true });
    const after = (await context.cookies()).find((cookie) => cookie.name === REFRESH_COOKIE)!;
    expect(after.value === before.value).toBe(false);
    expect(after.httpOnly).toBe(true);
    const recovered = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/auth/session' &&
        response.request().method() === 'GET',
    );
    await page.reload();
    const current = await recovered;
    expect(current.status()).toBe(200);
    const body = await current.json();
    expect(body.userId).toBe(session.userId);
    expect(body.sessionId).toBe(session.sessionId);
    expect(body.activeRole).toBe('STUDENT');
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible();
  });

  test('logout clears the cookie, revokes the bearer and prevents recovery on reload', async ({
    page,
    context,
  }) => {
    const { accessToken } = await loginPwa(page, TEST_USERS.student);
    await logoutPwa(page);
    expect((await context.cookies()).some((cookie) => cookie.name === REFRESH_COOKIE)).toBe(false);
    const revoked = await page.request.get('/api/auth/session', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(revoked.status()).toBe(401);
    const refresh = await page.request.post('/api/auth/refresh');
    expect(refresh.status()).toBe(401);
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Войти в приложение' })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
  });

  test('offline then online reload recovers authenticated navigation', async ({
    page,
    context,
  }) => {
    const { session } = await loginPwa(page, TEST_USERS.student);
    await context.setOffline(true);
    await expect.poll(() => page.evaluate(() => navigator.onLine)).toBe(false);
    const recovered = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/auth/session' &&
        response.request().method() === 'GET',
    );
    await context.setOffline(false);
    expect((await (await recovered).json()).sessionId).toBe(session.sessionId);
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible();
  });
});
