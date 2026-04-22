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
  test('student login → schedule visible → logout clears state', async ({ page }) => {
    const user = TEST_USERS.student;

    await loginAs(page, user);

    // Schedule page должна отрендериться
    await expect(page.getByRole('heading', { name: /расписание/i })).toBeVisible();

    // axe baseline
    await assertNoA11yCriticalOrSerious(page);

    await logout(page);

    // Protected route → redirect на /login
    await page.goto(user.expectedLandingPath);
    await expect(page).toHaveURL(/\/login$/);
  });

  test('admin login → dashboard visible', async ({ page }) => {
    await loginAs(page, TEST_USERS.admin);
    await expect(page.getByRole('heading', { name: /дашборд|обзор|панель/i })).toBeVisible();
    await assertNoA11yCriticalOrSerious(page);
  });

  test('invalid password → error shown, no redirect', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/логин/i).fill(TEST_USERS.student.login);
    await page.getByLabel(/пароль/i).fill('WRONG_PASSWORD');
    await page.getByRole('button', { name: /войти/i }).click();

    // Должна остаться на /login и показать ошибку (RFC 7807 toast или inline)
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByText(/неверн|неправильн/i)).toBeVisible({ timeout: 10_000 });
  });
});
