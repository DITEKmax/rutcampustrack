import { lstatSync } from 'node:fs';
import { isAbsolute, join } from 'node:path';
import { defineConfig } from '@playwright/test';
import existing from './playwright.real-mobile.config';

process.env.PLAYWRIGHT_NO_COPY_PROMPT = '1';

const run = process.env.RCT_SEMESTER_LIFECYCLE_RUN;
if (!run || !isAbsolute(run)) {
  throw new Error('Semester lifecycle requires an explicit private run directory');
}
for (const path of [run, join(run, 'artifacts')]) {
  const info = lstatSync(path);
  if (!info.isDirectory() || info.isSymbolicLink() || (info.mode & 0o777) !== 0o700) {
    throw new Error('Semester lifecycle requires private artifact directories');
  }
}

// One API-only user journey against the same real services; no browser is launched.
export default defineConfig({
  ...existing,
  testDir: './fixtures',
  testMatch: /semester-lifecycle\.spec\.ts/,
  testIgnore: [],
  grep: undefined,
  outputDir: join(run, 'artifacts'),
  reporter: [['./fixtures/safe-reporter.ts']],
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: true,
  use: { ...existing.use, ignoreHTTPSErrors: false, trace: 'off', video: 'off', screenshot: 'off' },
  projects: existing.projects
    ?.filter((project) => project.name === 'chromium')
    .map((project) => ({ ...project, use: { ...project.use, ignoreHTTPSErrors: false } })),
});
