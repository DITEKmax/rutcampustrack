import { defineConfig, devices } from '@playwright/test';

/**
 * M08 Группа 5 (P2-8/5) — Playwright e2e config.
 *
 * BASE_URL — env override для локальных / CI прогонов. Дефолт —
 * http://localhost:8080 (api-gateway) для back + http://localhost/app
 * для PWA static, http://localhost (root) для web-panel.
 *
 * Chromium + WebKit coverage per OWNER-ANSWERS P2-8/5 acceptance.
 * Firefox omitted — нестабильные downloads в CI, PWA не официально
 * Firefox-target.
 *
 * Специальный `@smoke` grep — для подмножества тестов, которые запускают
 * post-deploy smoke перед прод-release (scripts/smoke-prod.sh wrapper).
 */
export default defineConfig({
  testDir: './specs',
  outputDir: './test-results',

  // Параллельное выполнение внутри файла + 4 worker'а параллельно.
  fullyParallel: true,
  workers: process.env.CI ? 2 : 4,

  // CI: retries=1 от неконтролируемых flakes (WebSocket timing etc);
  // локально — retries=0 (чтобы видеть реальные failures сразу).
  retries: process.env.CI ? 1 : 0,

  reporter: [
    ['list'],
    ['html', { outputFolder: './playwright-report', open: 'never' }],
    process.env.CI ? ['github'] : ['line'],
  ] as any,

  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 10_000,
    navigationTimeout: 30_000,
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
    // Mobile viewport для PWA role-tests (iPhone 13 dimensions).
    {
      name: 'mobile-chrome',
      use: { ...devices['Pixel 7'] },
      testMatch: /.*role-student\.spec\.ts|.*role-headman\.spec\.ts|.*student-excuse\.spec\.ts/,
    },
  ],

  // expect behavior
  expect: {
    timeout: 5_000,
  },

  // Forbid .only'ы в CI — защита от случайно забытого теста.
  forbidOnly: !!process.env.CI,
});
