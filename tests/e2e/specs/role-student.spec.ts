import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';

/**
 * M08 Группа 5 (P2-8/5) — role-student golden paths.
 *
 * Student-only пути в web-panel (Angular). Для PWA-версии — отдельный
 * spec будет в v0.1 когда PWA routes стабилизируются.
 */

test.describe('Role: STUDENT', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEST_USERS.student);
  });

  test('can access student-only paths', async ({ page }) => {
    const paths = ['/student/schedule', '/student/stats', '/student/excuses'];
    for (const path of paths) {
      await page.goto(path);
      await expect(page).toHaveURL(new RegExp(path));
      await assertNoA11yCriticalOrSerious(page);
    }
  });

  test('cannot access /headman/* without is_headman=true', async ({ page }) => {
    await page.goto('/headman/dashboard');
    // Role-guard redirect — STUDENT без is_headman не видит headman-панель
    await expect(page).toHaveURL(/\/student\//, { timeout: 5_000 });
  });

  test('cannot access /admin/* or /teacher/*', async ({ page }) => {
    await page.goto('/admin/users');
    await expect(page).toHaveURL(/\/student\//, { timeout: 5_000 });

    await page.goto('/teacher/stats');
    await expect(page).toHaveURL(/\/student\//, { timeout: 5_000 });
  });
});
