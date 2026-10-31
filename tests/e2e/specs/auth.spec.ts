import { expect } from '@playwright/test';
import { test } from '../fixtures/artifact-privacy';
import { TEST_USERS } from '../fixtures/users';
import { loginAs, logout } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';
import { expectWebPanelRoute } from '../fixtures/web-panel-route';

/** Current root Vue auth behavior; the existing private config is required before credentials. */
test.describe('Vue WEB auth flow', () => {
  test('student login → owned schedule → logout protects the deep link', async ({ page }) => {
    await loginAs(page, TEST_USERS.student);
    await expectWebPanelRoute(page, '/student/schedule');
    await logout(page);
    const target = '/student/schedule?from=auth-check';
    await page.goto(target);
    await expect(page).toHaveURL(url => url.pathname === '/login' && url.searchParams.get('redirect') === target);
    await expect(page.locator('.web-shell, [data-screen-id="WEB-STUDENT-SCHEDULE"]')).toHaveCount(0);
    await expect(page.getByLabel(/^Пароль\s+\*(?:\s+(?:Показать|Скрыть))?$/)).toHaveValue('');
  });

  test('admin login → authorized dashboard data', async ({ page }) => {
    await loginAs(page, TEST_USERS.admin);
    await expectWebPanelRoute(page, '/admin/dashboard');
  });

  test('invalid password → server rejection and guest error state', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Логин *', { exact: true }).fill(TEST_USERS.student.login);
    const password = page.getByLabel(/^Пароль\s+\*(?:\s+(?:Показать|Скрыть))?$/);
    await password.fill('WRONG_PASSWORD');
    const origin = new URL(page.url()).origin;
    const rejected = page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.origin === origin && url.pathname === '/api/auth/login' && response.request().method() === 'POST';
    });
    await page.getByRole('button', { name: 'Войти', exact: true }).click();
    const response = await rejected;
    expect(response.status()).toBe(401);
    expect(await response.finished()).toBeNull();
    await expect(page).toHaveURL(url => url.pathname === '/login');
    await expect(page.getByRole('alert')).toContainText(/\S/);
    await expect(page.locator('.web-shell')).toHaveCount(0);
    await expect(password).toHaveValue('');
  });
});

test.describe('Auth a11y @a11y', () => {
  test('login page — zero CRITICAL/SERIOUS axe violations', async ({ page }) => {
    await page.goto('/login');
    await assertNoA11yCriticalOrSerious(page);
  });

  test('explicitly granted HEADMAN page — zero CRITICAL/SERIOUS axe violations', async ({ page }) => {
    await loginAs(page, { ...TEST_USERS.student, role: 'HEADMAN', expectedLandingPath: '/headman' });
    await expectWebPanelRoute(page, '/headman');
  });
});
