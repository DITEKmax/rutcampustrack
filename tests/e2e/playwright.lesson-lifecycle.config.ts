import { lstatSync } from 'node:fs';
import { isAbsolute, join } from 'node:path';
import { defineConfig } from '@playwright/test';
import existing from './playwright.real-mobile.config';
import { readTeachingInstant } from './fixtures/teaching-clock';
import { requireLocalLane } from './fixtures/local-stage17';

requireLocalLane('lesson-lifecycle');

const teachingInstant = readTeachingInstant(process.env.RCT_E2E_TEACHING_CLOCK_DIR);
const run = process.env.RCT_LESSON_LIFECYCLE_RUN;
if (!run || !isAbsolute(run)) {
  throw new Error('Lesson lifecycle requires an explicit private run directory');
}
for (const path of [run, join(run, 'artifacts')]) {
  const info = lstatSync(path);
  if (!info.isDirectory() || info.isSymbolicLink() || (info.mode & 0o777) !== 0o700) {
    throw new Error('Lesson lifecycle requires private artifact directories');
  }
}
export default defineConfig<{}, { teachingInstant: Date | undefined }>({
  ...existing,
  testMatch: /mobile-(?:lesson-lifecycle|schedule-lifecycle|attendance-requests)\.spec\.ts/,
  outputDir: join(run, 'artifacts'),
  grep: undefined,
  forbidOnly: true,
  timeout: 240000,
  use: { ...existing.use, ignoreHTTPSErrors: false, timezoneId: 'Europe/Moscow', teachingInstant },
  projects: existing.projects
    ?.filter((project) => project.name === 'chromium')
    .map((project) => ({
      ...project,
      use: {
        ...project.use,
        ignoreHTTPSErrors: false,
        timezoneId: 'Europe/Moscow',
        teachingInstant,
      },
    })),
});
