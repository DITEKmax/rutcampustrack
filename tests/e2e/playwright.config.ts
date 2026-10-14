import { defineConfig, devices } from '@playwright/test';

/**
 * M08 Группа 5 (P2-8/5) — Playwright e2e config.
 *
 * E2E_BASE_URL overrides the isolated HTTPS edge on localhost:8443.
 * Current smoke uses /app/ (PWA Vue) and /mini-app/ (TMA Vue).
 *
 * Chromium + WebKit coverage per OWNER-ANSWERS P2-8/5 acceptance.
 * Firefox omitted — нестабильные downloads в CI, PWA не официально
 * Firefox-target.
 *
 * CI selects the current Vue auth specs explicitly and uses @smoke within them.
 */
export default defineConfig({
  testDir: './specs',
  // API-owned synthetic dataset has a separate explicit, serial acceptance config.
  testIgnore: /mobile-(?:real-server|lesson-lifecycle)\.spec\.ts|cloud-accepted-.*\.spec\.ts/,
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
    // M13 G25.3 — e2e compose терминирует HTTPS через self-signed cert
    // (нужно потому что AuthCookies выставляет `Secure` flag — на HTTP
    // browser cookie отбросит). ignoreHTTPSErrors=true принимает self-signed.
    baseURL: process.env.E2E_BASE_URL || 'https://localhost:8443',
    ignoreHTTPSErrors: true,
    serviceWorkers: 'block', // deterministic online auth; offline/SW has its own accepted checks
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
