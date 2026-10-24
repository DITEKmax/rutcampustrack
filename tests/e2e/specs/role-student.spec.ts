import { expect, type Request } from '@playwright/test';
import { test } from '../fixtures/artifact-privacy';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { expectWebPanelRoute } from '../fixtures/web-panel-route';

test.describe('Role: STUDENT', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEST_USERS.student);
  });

  test('can access current student paths with owned data', async ({ page }) => {
    for (const path of ['/student/schedule', '/student/statistics', '/student/requests'])
      await expectWebPanelRoute(page, path);
  });

  test('inactive HEADMAN and foreign ADMIN/TEACHER routes never start their reads', async ({ page }) => {
    for (const [path, foreign] of [
      ['/headman', /^\/api\/(?:schedule\/groups\/|academic\/(?:subjects|homeworks)|attendance\/requests)/],
      ['/admin/users', /^\/api\/academic\/(?:admin-access|users|groups)/],
      ['/teacher/journal', /^\/api\/v1\/teacher\//],
    ] as const) {
      let foreignData = false;
      const origin = new URL(page.url()).origin;
      const observe = (request: Request) => {
        const url = new URL(request.url());
        if (url.origin === origin && foreign.test(url.pathname)) foreignData = true;
      };
      page.on('request', observe);
      try {
        await page.goto(path);
        await expect(page).toHaveURL(url => url.pathname === '/student/home');
        await expect(page.locator('main#main [data-screen-id="WEB-STUDENT-HOME"]')).toBeVisible();
        await expect(page.locator('main#main .headman-page, main#main .admin-page, main#main .teacher-page')).toHaveCount(0);
        expect(foreignData, 'Denied role navigation never starts foreign target data').toBe(false);
      } finally { page.off('request', observe); }
    }
  });
});
