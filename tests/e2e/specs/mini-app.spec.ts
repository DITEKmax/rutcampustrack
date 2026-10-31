import { failureBoundary, handledWaiter } from '../fixtures/failed-case-diagnostics';
import { expect, test } from '@playwright/test';
import { installTelegramHost, teacherInitData } from '../fixtures/telegram';

/** Real HMAC validation and DB-backed TMA session with a synthetic Telegram host.
 * This verifies the application seam, not an external Telegram provider launch. */
test.describe('Vue Telegram entry @smoke', () => {
  test('tampered Telegram proof cannot expose role data or navigation', async ({
    page,
    context,
  }) => {
    await installTelegramHost(page, teacherInitData(true));
    const authentication = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/auth/tma' &&
        response.request().method() === 'POST',
    );
    await page.goto('/mini-app/');
    expect((await authentication).status()).toBe(401);
    await expect(
      page.getByRole('heading', { name: 'Не удалось открыть приложение' }),
    ).toBeVisible();
    await expect(page.locator('.teacher-feature-owner')).toHaveCount(0);
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toHaveCount(0);
    expect((await context.cookies()).some((cookie) => cookie.name === 'rct_refresh')).toBe(false);
  });

  test('signed teacher entry loads the real session and shared profile with host Back', async ({
    page,
  }) => {
    const initData = teacherInitData();
    await installTelegramHost(page, initData);
    const authentication = handledWaiter(page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/auth/tma' &&
        response.request().method() === 'POST',
    ));
    const currentSession = handledWaiter(page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/auth/session' &&
        response.request().method() === 'GET',
    ));
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/mini-app/');
    const authenticated = await authentication;
    await failureBoundary(test.info(), 'TMA_AUTH', async () => {
      expect(authenticated.status()).toBe(200);
    }, () => ({ httpStatus: authenticated.status(), sessionStatus: null }));
    const { accessToken } = (await authenticated.json()) as { accessToken: string };
    const current = await currentSession;
    await failureBoundary(test.info(), 'TMA_SESSION', async () => {
      expect(current.status()).toBe(200);
    }, () => ({ httpStatus: current.status(), loginStatus: authenticated.status() }));
    const session = await current.json();
    expect(session.userId).toBe('2');
    expect(session.sessionId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
    expect(session.sessionId).not.toBe('00000000-0000-0000-0000-000000000000');
    expect(session.activeRole).toBe('TEACHER');
    expect(session.roles.some((grant: { role: string }) => grant.role === 'TEACHER')).toBe(true);
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible();
    await expect(page.locator('.teacher-feature-owner')).toBeVisible();
    await page.getByRole('button', { name: 'Профиль', exact: true }).click();
    await page.getByRole('button', { name: 'Безопасность', exact: true }).click();
    await expect(page.locator('.mobile-bottom-nav')).toHaveCount(0);
    await expect(page.locator('.profile-back')).toBeHidden();
    await expect
      .poll(() =>
        page.evaluate(
          () =>
            (window as unknown as { __tmaEntryState: { backVisible: boolean } }).__tmaEntryState
              .backVisible,
        ),
      )
      .toBe(true);
    await page.evaluate(() => {
      for (const callback of (
        window as unknown as { __tmaEntryState: { callbacks: Set<() => void> } }
      ).__tmaEntryState.callbacks)
        callback();
    });
    await expect(page.getByRole('navigation', { name: 'Основная навигация' })).toBeVisible();
    const stores = await page.evaluate(() =>
      JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]),
    );
    expect(stores.includes(accessToken)).toBe(false);
    expect(stores.includes(initData)).toBe(false);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(
      true,
    );
    await page.setViewportSize({ width: 430, height: 932 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(
      true,
    );
  });
});
