import { expect, type Request } from '@playwright/test';
import { test } from '../fixtures/artifact-privacy';
import { TEST_USERS } from '../fixtures/users';
import { loginAs } from '../fixtures/auth';
import { expectWebPanelRoute } from '../fixtures/web-panel-route';

test.describe('Role: HEADMAN', () => {
  test.beforeEach(async ({ page }) => {
    // Same seed identity, separate explicit server-granted role intent.
    await loginAs(page, { ...TEST_USERS.student, role: 'HEADMAN', expectedLandingPath: '/headman' });
  });

  test('can access home, schedule and both independently scoped request kinds', async ({ page }) => {
    for (const path of ['/headman', '/headman/schedule', '/headman/requests?type=EXCUSE', '/headman/requests?type=LATE_CHECKIN'])
      await expectWebPanelRoute(page, path);
  });

  test('foreign ADMIN/TEACHER routes retain HEADMAN and never start target reads', async ({ page }) => {
    for (const [path, foreign] of [
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
        await expect(page).toHaveURL(url => url.pathname === '/headman');
        await expect(page.locator('main#main .headman-page')).toBeVisible();
        await expect(page.locator('main#main .admin-page, main#main .teacher-page')).toHaveCount(0);
        expect(foreignData, 'Denied target never starts its data read').toBe(false);
      } finally { page.off('request', observe); }
    }
  });
});
