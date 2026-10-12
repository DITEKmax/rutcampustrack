import { lstatSync } from 'node:fs';
import { isAbsolute, join } from 'node:path';
import { defineConfig } from '@playwright/test';
import existing from './playwright.real-mobile.config';
import { readTeachingInstant } from './fixtures/teaching-clock';

// Explicit real-client lane: the default suite and its auth smoke selection stay unchanged.
process.env.PLAYWRIGHT_NO_COPY_PROMPT = '1';

const run = process.env.RCT_FOCUS3_RUN;
if (!run || !isAbsolute(run)) {
  throw new Error('FIRST3 requires an absolute private run directory');
}
for (const path of [run, join(run, 'artifacts'), join(run, 'supervision')]) {
  const info = lstatSync(path);
  if (!info.isDirectory() || info.isSymbolicLink() || (info.mode & 0o777) !== 0o700) {
    throw new Error('FIRST3 requires precreated private artifact directories');
  }
}
if (process.env.RCT_FOCUS3_OBSERVATIONS !== join(run, 'supervision', 'observations.safe.json')) {
  throw new Error('FIRST3 observations must stay outside Playwright outputDir');
}
const teachingInstant = readTeachingInstant(process.env.RCT_E2E_TEACHING_CLOCK_DIR);

export default defineConfig<{}, { teachingInstant: Date | undefined }>({
  ...existing,
  testDir: './first3-specs',
  testMatch: 'mobile-first3.spec.ts',
  testIgnore: [],
  grep: /(?:STUDENT completion persists and another group cannot mutate the homework|HEADMAN marks the real roster and TEACHER reads without acquiring write permission|STUDENT helper marks with its capability and revocation denies the same effect)$/,
  outputDir: join(run, 'artifacts'),
  reporter: [['./fixtures/safe-reporter.ts']],
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: true,
  use: {
    ...existing.use,
    trace: 'off',
    video: 'off',
    screenshot: 'off',
    timezoneId: 'Europe/Moscow',
    teachingInstant,
  },
  projects: existing.projects
    ?.filter((project) => project.name === 'chromium')
    .map((project) => ({
      ...project,
      use: {
        ...project.use,
        trace: 'off',
        video: 'off',
        screenshot: 'off',
        timezoneId: 'Europe/Moscow',
        teachingInstant,
      },
    })),
});
