import { defineConfig } from '@playwright/test';
import existing from './playwright.config';

/** One shared active semester and task-owned disposable dataset; never run in parallel with another seed writer. */
export default defineConfig({
  ...existing,
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
