import { expect, test, type Page } from '@playwright/test';

function base64Url(input: string): string {
  return Buffer.from(input)
    .toString('base64')
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

function unsignedJwt(payload: Record<string, unknown>): string {
  return [
    base64Url(JSON.stringify({ alg: 'none', typ: 'JWT' })),
    base64Url(JSON.stringify(payload)),
    'signature',
  ].join('.');
}

async function mockTmaAuth(page: Page): Promise<void> {
  await page.route('**/api/auth/tma', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: unsignedJwt({
          sub: '1001',
          role: 'STUDENT',
          group_id: 5,
          is_headman: true,
        }),
        refreshToken: 'e2e-refresh-token',
        expiresIn: 900,
      }),
    });
  });
}

function miniAppUrl(startParam: string): string {
  const tgWebAppData = new URLSearchParams({
    auth_date: '1777777777',
    hash: 'e2e-hash',
    signature: 'e2e-signature',
    user: JSON.stringify({ id: 1001, first_name: 'Mini', username: 'student1001' }),
  }).toString();
  const params = new URLSearchParams({
    tgWebAppData,
    tgWebAppThemeParams: JSON.stringify({
      bg_color: '#ffffff',
      text_color: '#000000',
      button_color: '#00b87a',
      button_text_color: '#ffffff',
      secondary_bg_color: '#f8fafb',
    }),
    tgWebAppVersion: '8.0',
    tgWebAppPlatform: 'tdesktop',
    tgWebAppStartParam: startParam,
  });
  return `/mini-app/?${params.toString()}`;
}

test.describe('Telegram Mini App @smoke', () => {
  test('opens a lesson check-in route from Telegram launch params', async ({ page }) => {
    await mockTmaAuth(page);

    await page.goto(miniAppUrl('checkin:77'));

    await expect(page).toHaveURL(/\/mini-app\/checkin\/77(?:\?|$)/);
    await expect(page.locator('h1')).toBeVisible();
    await expect(page.getByText(/#77/)).toBeVisible();
  });

  for (const viewport of [
    { width: 390, height: 844 },
    { width: 430, height: 932 },
  ]) {
    test(`renders launch route without horizontal overflow at ${viewport.width}x${viewport.height}`, async ({ page }) => {
      await mockTmaAuth(page);
      await page.setViewportSize(viewport);

      await page.goto(miniAppUrl('checkin:77'));
      await expect(page).toHaveURL(/\/mini-app\/checkin\/77(?:\?|$)/);

      const hasHorizontalOverflow = await page.evaluate(
        () => document.documentElement.scrollWidth > window.innerWidth + 1,
      );
      expect(hasHorizontalOverflow).toBe(false);
    });
  }
});
