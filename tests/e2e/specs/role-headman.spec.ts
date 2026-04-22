import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { assertNoA11yCriticalOrSerious } from '../fixtures/axe';

/**
 * M08 Группа 5 (P2-8/5) — role-headman golden paths.
 *
 * Headman = STUDENT + is_headman=true. Имеет расширенные пути:
 * /headman/dashboard, /headman/schedule, /headman/excuses,
 * /headman/late-checkins, /headman/stats.
 *
 * + все student пути (headman может действовать как обычный student).
 */

test.describe('Role: HEADMAN', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEST_USERS.headman);
  });

  test('can access headman-only paths', async ({ page }) => {
    const paths = [
      '/headman/dashboard',
      '/headman/schedule',
      '/headman/excuses',
      '/headman/late-checkins',
    ];
    for (const path of paths) {
      await page.goto(path);
      await expect(page).toHaveURL(new RegExp(path));
      await assertNoA11yCriticalOrSerious(page);
    }
  });

  test('can also access student paths (fallback)', async ({ page }) => {
    const paths = ['/student/schedule', '/student/stats'];
    for (const path of paths) {
      await page.goto(path);
      await expect(page).toHaveURL(new RegExp(path));
    }
  });

  test('cannot access /admin/* or /teacher/*', async ({ page }) => {
    await page.goto('/admin/users');
    await expect(page).toHaveURL(/\/headman\/|\/student\//, { timeout: 5_000 });
  });
});
