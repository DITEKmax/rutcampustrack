import { expectWebPanelRoute } from '../fixtures/web-panel-route';
import { test, expect } from '@playwright/test';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';

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
    // M14 G8 (G26 F03): seed user `student` имеет is_headman=true и
    // landing page /headman/dashboard. TEST_USERS.headman дубликат удалён.
    await loginAs(page, TEST_USERS.student);
  });

  test('can access headman-only paths', async ({ page }) => {
    const paths = [
      '/headman/dashboard',
      '/headman/schedule',
      '/headman/excuses',
      '/headman/late-checkin',
    ];
    for (const path of paths) {
      await expectWebPanelRoute(page, path);
    }
  });

  test('cannot access /admin/* or /teacher/*', async ({ page }) => {
    for (const path of ['/admin/users', '/teacher/journal']) {
      let foreignData = false;
      const observe = (response: import('@playwright/test').Request) => {
        const target = new URL(response.url()).pathname;
        if ((path === '/admin/users' && target === '/api/academic/users') ||
          (path === '/teacher/journal' && target === '/api/academic/assignments/my')) foreignData = true;
      };
      page.on('request', observe);
      try {
        await page.goto(path);
        await expect(page).toHaveURL((url) => url.pathname === '/headman/dashboard', { timeout: 5_000 });
        await expect(page.locator('main app-headman-dashboard .page-hero')).toBeVisible();
        await expect(page.locator('main app-users-page, main app-journal-page')).toHaveCount(0);
        expect(foreignData, 'Denied target never starts its data read').toBe(false);
      } finally { page.off('request', observe); }
    }
  });
});
