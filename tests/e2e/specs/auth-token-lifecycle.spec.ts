import { expect, test } from '@playwright/test';

import { loginAs, logout } from '../fixtures/auth';
import { TEST_USERS } from '../fixtures/users';

/**
 * M13 G22 — auth-token lifecycle e2e (5 spec'ов).
 *
 * Дополняет существующий `auth.spec.ts` (M08 G7), который покрывает
 * happy-path login/logout/admin. Здесь — security guarantees:
 *  T1. login → HttpOnly + SameSite=Strict refresh-cookie.
 *  T2. admin → /admin/* доступ, role-guard работает.
 *  T3. access-expiry → /auth/refresh issues new tokens via cookie.
 *  T4. logout → cookie cleared (Max-Age=0, empty value).
 *  T5. offline → online → STOMP reconnect (M13 G18 cross-ref).
 *
 * Cookie name `rct_refresh`, path `/api/auth`, HttpOnly+Secure+SameSite=Strict.
 * Source: services/auth-service/.../security/AuthCookies.java.
 */

const REFRESH_COOKIE = 'rct_refresh';
const COOKIE_PATH = '/api/auth';

test.describe('Auth token lifecycle @smoke', () => {
  test('T1: student login → HttpOnly + SameSite=Strict refresh cookie', async ({
    page,
    context,
  }) => {
    await loginAs(page, TEST_USERS.student);

    // После успешного login auth-service выставил Set-Cookie rct_refresh.
    // Playwright API возвращает sameSite в форме 'Strict' | 'Lax' | 'None'.
    const cookies = await context.cookies();
    const refresh = cookies.find((c) => c.name === REFRESH_COOKIE);

    expect(refresh, 'refresh cookie должна быть установлена после login').toBeDefined();
    expect(refresh!.httpOnly, 'rct_refresh должна быть HttpOnly (защита от XSS)').toBe(true);
    expect(refresh!.sameSite, 'rct_refresh должна быть SameSite=Strict (CSRF)').toBe('Strict');
    expect(refresh!.path, `rct_refresh должна быть scoped на ${COOKIE_PATH}`).toBe(COOKIE_PATH);
    expect(refresh!.value, 'rct_refresh должна иметь непустое value').not.toBe('');
  });

  test('T2: admin login → /admin/* accessible', async ({ page }) => {
    await loginAs(page, TEST_USERS.admin);

    // expectedLandingPath для admin — /admin/dashboard либо /admin/*.
    // loginAs в fixture уже ждёт redirect на этот path. Дополнительно
    // assert что URL содержит /admin/ (role-guard сработал).
    expect(page.url()).toMatch(/\/admin\//);
  });

  test('T3: refresh endpoint выдаёт новый cookie по существующей сессии', async ({
    page,
  }) => {
    await loginAs(page, TEST_USERS.student);

    // Original cookie value
    const before = (await page.context().cookies()).find((c) => c.name === REFRESH_COOKIE);
    expect(before, 'pre-refresh cookie должна быть').toBeDefined();

    // M13 G25.23 — page.request shares cookies с browser context, в отличие от
    // top-level `request` fixture (отдельный API context, без cookies от login).
    // Без этого refresh получает 401 (нет rct_refresh cookie в request).
    const response = await page.request.post('/api/auth/refresh');
    expect(response.status(), 'refresh должен вернуть 200').toBe(200);

    const body = await response.json();
    // Backend возвращает accessToken (camelCase, см. TokenResponse DTO).
    expect(body.accessToken, 'response должен содержать новый accessToken').toBeTruthy();

    // Новый refresh cookie выставлен (rotation на каждый refresh —
    // anti-replay guarantee).
    const after = (await page.context().cookies()).find((c) => c.name === REFRESH_COOKIE);
    expect(after, 'post-refresh cookie должна быть').toBeDefined();
    expect(after!.value, 'rct_refresh value должен ротейться (anti-replay)').not.toBe(
      before!.value,
    );
    expect(after!.httpOnly, 'новая cookie тоже HttpOnly').toBe(true);
  });

  test('T4: logout → refresh cookie cleared', async ({ page }) => {
    await loginAs(page, TEST_USERS.student);

    const beforeLogout = (await page.context().cookies()).find(
      (c) => c.name === REFRESH_COOKIE,
    );
    expect(beforeLogout?.value, 'pre-logout cookie value есть').toBeTruthy();

    await logout(page);

    // После logout backend выставляет Set-Cookie rct_refresh с Max-Age=0
    // и empty value → браузер удаляет cookie. Playwright cookies()
    // отражает текущее состояние jar.
    const afterLogout = (await page.context().cookies()).find(
      (c) => c.name === REFRESH_COOKIE,
    );
    // Либо cookie отсутствует совсем (browser удалил), либо value пустой
    // (зависит от того, что browser jar успел сделать к моменту запроса).
    if (afterLogout) {
      expect(afterLogout.value, 'logout должен очистить refresh cookie value').toBe('');
    }

    // Protected route → redirect /login (cookie + sessionStorage cleared).
    await page.goto('/student/schedule');
    await expect(page).toHaveURL(/\/login/);
  });

  test('T5: offline → online → STOMP reconnect (M13 G18 cross-ref)', async ({
    page,
    context,
  }) => {
    await loginAs(page, TEST_USERS.student);

    // Trigger initial WS connection. PWA / web-panel поднимает STOMP
    // через @stomp/stompjs к /api/ws/. Connection устанавливается
    // в notification-center.service / useStompCheckin.
    await page.waitForLoadState('networkidle');

    // Симулируем offline на 5 секунд. @stomp/stompjs detect'ит
    // disconnect, schedule'ит reconnect через reconnectDelay 1s.
    await context.setOffline(true);
    await page.waitForTimeout(5_000);
    await context.setOffline(false);

    // После online — Stomp клиент должен переподключиться. Точная
    // verification (open WS frame, subscribe replay) — frontend-internal
    // detail; здесь проверяем что страница не падает после offline+online
    // cycle и role-guard всё ещё пускает.
    //
    // M13 G25.23 — student-headman пользователь (seed: is_headman=true)
    // landing = /headman/dashboard, не /student/. Cookie сохранилась через
    // offline cycle → role-guard пропускает на /headman/*.
    await page.reload();
    await expect(page).toHaveURL(/\/headman\//, { timeout: 15_000 });

    // Sidebar nav должен снова отрендериться (cookie + sessionStorage
    // сохранились через offline cycle).
    await expect(page.getByRole('link', { name: /кабинет старосты/i })).toBeVisible({
      timeout: 10_000,
    });
  });
});
