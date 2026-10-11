import { defineConfig } from '@playwright/test';
import existing from './playwright.real-mobile.config';
import { readTeachingInstant } from './fixtures/teaching-clock';

// Explicit opt-in config; never selected by existing mandatory/default smoke config.
const teachingInstant = readTeachingInstant(process.env.RCT_E2E_TEACHING_CLOCK_DIR);
export default defineConfig<{}, { teachingInstant: Date | undefined }>({
  ...existing,
  testDir: './clock-specs',
  testMatch: /teaching-clock-geo\.spec\.ts/,
  outputDir: './test-results/teaching-clock',
  timeout: 180000,
  use: {
    ...existing.use,
    teachingInstant,
    timezoneId: 'Europe/Moscow',
    permissions: ['geolocation'],
    geolocation: { latitude: 55.788204, longitude: 37.606762, accuracy: 1 },
  },
});
