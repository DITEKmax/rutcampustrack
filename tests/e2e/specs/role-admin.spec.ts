import { expect, type Request } from '@playwright/test';
import { test } from '../fixtures/artifact-privacy';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { expectWebPanelRoute } from '../fixtures/web-panel-route';

test.describe('Role: ADMIN', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEST_USERS.admin);
  });

  test('can access actually permitted dashboard and registries with loaded identities', async ({ page }) => {
    for (const path of ['/admin/dashboard', '/admin/users', '/admin/groups', '/admin/semesters'])
      await expectWebPanelRoute(page, path);
  });

  test('foreign STUDENT route retains permitted ADMIN home and never starts target reads', async ({ page }) => {
    let foreignData = false;
    const origin = new URL(page.url()).origin;
    const observe = (request: Request) => {
      const url = new URL(request.url());
      if (url.origin === origin && /^\/api\/v1\/student\//.test(url.pathname)) foreignData = true;
    };
    page.on('request', observe);
    try {
      await page.goto('/student/schedule');
      await expect(page).toHaveURL(url => url.pathname === TEST_USERS.admin.expectedLandingPath);
      await expect(page.locator('main#main .admin-page')).toBeVisible();
      await expect(page.locator('main#main .student-page')).toHaveCount(0);
      expect(foreignData, 'Denied target never starts student data').toBe(false);
    } finally { page.off('request', observe); }
  });
});
