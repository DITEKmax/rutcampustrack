import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../fixtures/users';
import { loginAs, logout } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';

/**
 * M08 Группа 5 (P2-8/5) — auth flow core.
 *
 * Golden path:
 *  1. student логинится → видит /student/schedule.
 *  2. schedule показан в header'е (первая группа, сегодня — неделя).
 *  3. logout очищает sessionStorage + HTTP-only cookie → redirect на /login.
 *  4. попытка открыть /student/schedule без сессии → снова на /login.
 *
 * @smoke — включается в post-deploy smoke (scripts/smoke-prod.sh).
 */

test.describe('Auth flow @smoke', () => {
  // M13 G25.20 — diagnostic: direct API call to surface real status code
  // before we trust UI-driven login. CI логи компактны, status code из этого
  // теста + текст ответа подскажут где блок (gateway 502/auth 401/timeout).
  test('diagnostic: direct POST /api/auth/login returns expected status', async ({ request }) => {
    const validResponse = await request.post('/api/auth/login', {
      data: { login: 'admin', password: 'password' },
      failOnStatusCode: false,
    });
    const validBody = await validResponse.text();
    console.log(`[DIAG] valid login: status=${validResponse.status()} body=${validBody.slice(0, 500)}`);

    const invalidResponse = await request.post('/api/auth/login', {
      data: { login: 'admin', password: 'WRONG' },
      failOnStatusCode: false,
    });
    const invalidBody = await invalidResponse.text();
    console.log(`[DIAG] invalid login: status=${invalidResponse.status()} body=${invalidBody.slice(0, 500)}`);

    expect(validResponse.status(), `valid creds got ${validResponse.status()}, body: ${validBody}`).toBe(200);
    expect(invalidResponse.status(), `invalid creds got ${invalidResponse.status()}, body: ${invalidBody}`).toBe(401);
  });

  test('student login → dashboard visible → logout clears state', async ({ page }) => {
    const user = TEST_USERS.student;

    await loginAs(page, user);

    // M13 G25.23 — student-headman landing = /headman/dashboard, heading = "Добрый вечер"
    // (level=2 в layout.component, time-of-day greeting). URL уже asserted в loginAs,
    // здесь просто verify что dashboard "чувствуется" rendered (любой heading из
    // sidebar nav links: "Кабинет старосты", "Расписание", etc).
    await expect(page.getByRole('link', { name: /кабинет старосты/i })).toBeVisible();

    // axe baseline
    await assertNoA11yCriticalOrSerious(page);

    await logout(page);

    // Protected route → redirect на /login
    await page.goto(user.expectedLandingPath);
    await expect(page).toHaveURL(/\/login$/);
  });

  test('admin login → dashboard visible', async ({ page }) => {
    await loginAs(page, TEST_USERS.admin);
    // M13 G25.23 — admin dashboard heading = "Добрый вечер, администратор"
    // (level=2, time-of-day greeting). Sidebar nav проверка более стабильна
    // против изменений приветствия — admin layout всегда содержит ссылку
    // "Пользователи" в sidebar.
    await expect(page.getByRole('link', { name: /пользователи/i }).first()).toBeVisible();
    await assertNoA11yCriticalOrSerious(page);
  });

  test('invalid password → error shown, no redirect', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('textbox', { name: /логин/i }).fill(TEST_USERS.student.login);
    await page.getByRole('textbox', { name: /пароль/i }).fill('WRONG_PASSWORD');
    await page.getByRole('button', { name: /войти/i }).click();

    // Должна остаться на /login и показать ошибку (RFC 7807 toast или inline)
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByText(/неверн|неправильн/i)).toBeVisible({ timeout: 10_000 });
  });
});
