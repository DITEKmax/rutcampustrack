import { expect, type Request } from '@playwright/test';
import { test } from '../fixtures/artifact-privacy';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { expectWebPanelRoute } from '../fixtures/web-panel-route';

test.describe('Role: TEACHER', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEST_USERS.teacher);
  });

  test('journal and statistics render current assigned group AND subject scope', async ({ page }) => {
    for (const path of ['/teacher/journal', '/teacher/statistics']) await expectWebPanelRoute(page, path);
  });

  test('foreign ADMIN route retains TEACHER and never starts target reads', async ({ page }) => {
    let foreignData = false;
    const origin = new URL(page.url()).origin;
    const observe = (request: Request) => {
      const url = new URL(request.url());
      if (url.origin === origin && /^\/api\/academic\/(?:admin-access|users|groups)/.test(url.pathname)) foreignData = true;
    };
    page.on('request', observe);
    try {
      await page.goto('/admin/users');
      await expect(page).toHaveURL(url => url.pathname === '/teacher/home');
      await expect(page.locator('main#main [data-screen-id="WEB-TEACHER-HOME"]')).toBeVisible();
      await expect(page.locator('main#main .admin-page')).toHaveCount(0);
      expect(foreignData, 'Denied target never starts administrative reads').toBe(false);
    } finally { page.off('request', observe); }
  });
});
