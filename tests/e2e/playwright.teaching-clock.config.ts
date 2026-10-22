import { lstatSync } from 'node:fs';
import { isAbsolute, join } from 'node:path';
import { defineConfig } from '@playwright/test';
import existing from './playwright.real-mobile.config';
import { readTeachingInstant } from './fixtures/teaching-clock';

// Explicit opt-in config; never selected by existing mandatory/default smoke config.
const teachingInstant = readTeachingInstant(process.env.RCT_E2E_TEACHING_CLOCK_DIR);
const run = process.env.RCT_E2E_GEO_RUN;
if (!run || !isAbsolute(run)) {
  throw new Error('Geolocation acceptance requires an explicit private run directory');
}
for (const path of [run, join(run, 'artifacts')]) {
  const info = lstatSync(path);
  if (!info.isDirectory() || info.isSymbolicLink() || (info.mode & 0o777) !== 0o700) {
    throw new Error('Geolocation acceptance requires private artifact directories');
  }
}
export default defineConfig<{}, { teachingInstant: Date | undefined }>({
  ...existing,
  testDir: './clock-specs',
  testMatch: /teaching-clock-geo\.spec\.ts/,
  outputDir: join(run, 'artifacts'),
  grep: undefined,
  forbidOnly: true,
  timeout: 180000,
  use: {
    ...existing.use,
    ignoreHTTPSErrors: false,
    teachingInstant,
    timezoneId: 'Europe/Moscow',
    permissions: ['geolocation'],
    geolocation: { latitude: 55.788204, longitude: 37.606762, accuracy: 1 },
  },
  projects: existing.projects
    ?.filter((project) => project.name === 'chromium')
    .map((project) => ({ ...project, use: { ...project.use, ignoreHTTPSErrors: false } })),
});
