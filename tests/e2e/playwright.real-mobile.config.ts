import { defineConfig } from '@playwright/test';
import existing from './playwright.config';

// Pinned Playwright checks this before reading a page snapshot, including during teardown.
process.env.PLAYWRIGHT_NO_COPY_PROMPT = '1';

/** One shared active semester and task-owned disposable dataset; never run in parallel with another seed writer. */
export default defineConfig({
  ...existing,
  outputDir: './test-results/real-mobile',
  reporter: [['./fixtures/safe-reporter.ts']],
  use: { ...existing.use, trace: 'off', video: 'off', screenshot: 'off' },
  testMatch: /mobile-real-server\.spec\.ts/,
  testIgnore: [],
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60000,
  projects: existing.projects?.filter(
    (project) => project.name === 'chromium' || project.name === 'webkit',
  ),
});
