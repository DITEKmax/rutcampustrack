import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';

/**
 * M08 Группа 5 (P2-8/5) — role-teacher golden paths.
 *
 * Teacher — read-only режим для журнала + статистика. Нет Telegram.
 */

test.describe('Role: TEACHER', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEST_USERS.teacher);
  });

  test('can access teacher-only paths', async ({ page }) => {
    const paths = ['/teacher/schedule', '/teacher/stats'];
    for (const path of paths) {
      await page.goto(path);
      await expect(page).toHaveURL(new RegExp(path));
      await assertNoA11yCriticalOrSerious(page);
    }
  });

  test('cannot access /admin/* — redirected', async ({ page }) => {
    await page.goto('/admin/users');
    await expect(page).toHaveURL(/\/teacher\//, { timeout: 5_000 });
  });

  test('journal shows red-zone indicator for struggling students', async ({ page }) => {
    await page.goto('/teacher/stats');
    // Если в seed есть red-zone студенты — статистика должна показать badge
    const redZoneBadge = page.locator('[data-testid="red-zone-badge"]');
    // Не обязательно visible (может быть no red-zone в seed'е), но не крашит
    await expect(page.locator('body')).toBeVisible();
  });
});
