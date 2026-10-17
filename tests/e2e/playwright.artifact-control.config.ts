import { defineConfig } from '@playwright/test';

process.env.PLAYWRIGHT_NO_COPY_PROMPT = '1';
// Public-marker negative control: the protected auto fixture must reject before an explicit fixture.
if (process.env.RCT_E2E_CONTROL_MISSING_GUARD === 'true')
  delete process.env.PLAYWRIGHT_NO_COPY_PROMPT;

/** Browser-free controls test only the reporter/permission assertion boundaries. */
export default defineConfig({
  testDir: './artifact-controls',
  outputDir: process.env.RCT_E2E_CONTROL_OUTPUT_DIR,
  workers: 1,
  retries: 0,
  reporter: [['./fixtures/safe-reporter.ts']],
  use: { trace: 'off', video: 'off', screenshot: 'off' },
  forbidOnly: true,
});
