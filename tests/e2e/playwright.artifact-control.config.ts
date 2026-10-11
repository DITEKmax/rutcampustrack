import { defineConfig } from '@playwright/test';

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
