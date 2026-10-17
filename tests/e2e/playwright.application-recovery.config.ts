import { lstatSync } from 'node:fs';
import { isAbsolute, join } from 'node:path';
import { defineConfig } from '@playwright/test';
import existing from './playwright.real-mobile.config';

process.env.PLAYWRIGHT_NO_COPY_PROMPT = '1';

const run = process.env.RCT_APP_RECOVERY_RUN;
const phase = process.env.RCT_APP_RECOVERY_PHASE;
if (
  !run ||
  !isAbsolute(run) ||
  !phase ||
  !['seed', 'pending', 'admit', 'attachment'].includes(phase)
) {
  throw new Error('Application recovery requires an explicit private run and API phase');
}
for (const path of [run, join(run, 'artifacts'), join(run, 'artifacts', phase)]) {
  const info = lstatSync(path);
  if (!info.isDirectory() || info.isSymbolicLink() || (info.mode & 0o777) !== 0o700) {
    throw new Error('Application recovery requires private artifact directories');
  }
}

// API-only phases reuse the current client fixture and closed reporter without a browser.
export default defineConfig({
  ...existing,
  testDir: './fixtures',
  testMatch: /application-recovery\.spec\.ts/,
  testIgnore: [],
  grep: undefined,
  outputDir: join(run, 'artifacts', phase),
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
