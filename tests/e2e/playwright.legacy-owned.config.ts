import { defineConfig } from '@playwright/test';
import existing from './playwright.config';

// Owned API fixtures require the existing private reporter before credentials are created.
process.env.PLAYWRIGHT_NO_COPY_PROMPT = '1';
export default defineConfig({
  ...existing,
  testMatch: /(?:student-excuse|admin-create-user)\.spec\.ts/,
  testIgnore: [],
  projects: existing.projects?.map((project) => ({
    ...project,
    testMatch: project.name === 'mobile-chrome'
      ? /student-excuse\.spec\.ts/
      : /(?:student-excuse|admin-create-user)\.spec\.ts/,
  })),
  fullyParallel: false,
  workers: 1,
  reporter: [['./fixtures/safe-reporter.ts']],
  use: { ...existing.use, trace: 'off', screenshot: 'off', video: 'off' },
});
