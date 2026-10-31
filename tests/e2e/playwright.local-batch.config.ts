import { failedCases, type FailedCaseId } from './fixtures/failed-case-diagnostics';
import { defineConfig, type PlaywrightTestConfig } from '@playwright/test';
import { join } from 'node:path';
import { requireLocalLane } from './fixtures/local-stage17';

// Only the Python nonce/manifest admission may select a private batch lane.
const lane = process.env.RCT_LOCAL_UI_BATCH_LANE;
const configurations: Record<string, string> = {
  'auth-smoke': './playwright.config',
  first3: './playwright.first3.config',
  admin: './playwright.real-mobile.config',
  'role-security-export': './playwright.real-mobile.config',
  installation: './playwright.real-mobile.config',
  geo: './playwright.teaching-clock.config',
  homework: './playwright.homework-lifecycle.config',
  map: './playwright.map-lifecycle.config',
  'registry-html': './playwright.registry-export.config',
  helper: './playwright.helper-capabilities.config',
};
if (!lane || !Object.hasOwn(configurations, lane) || process.env.RCT_LOCAL_STAGE17_MODE !== 'ui-batch')
  throw new Error('Local UI batch requires its explicit manifest-bound lane');
requireLocalLane('ui-batch');
// Import only the selected existing config: other lanes' private directories do not exist yet.
const existing = require(configurations[lane]).default as PlaywrightTestConfig;
const run = process.env.RCT_REMAINING5_RUN!;
let testMatch = existing.testMatch;
let grep = existing.grep;
let grepInvert = existing.grepInvert;
if (lane === 'auth-smoke') {
  testMatch = ['auth-token-lifecycle.spec.ts', 'mini-app.spec.ts'];
  grep = /@smoke/;
} else if (lane === 'role-security-export') {
  // This one scenario still needs a nonce-owned OTP broker adapter.
  grepInvert = /password recovery consumes its own delivered OTP and admits the replacement password @cloud-accepted$/;
} else if (lane === 'installation') {
  // Synthetic prompt logic is portable; production worker replacement is hosted-container owned.
  grepInvert = /production worker update activates through the update gate and retains a warm launch$/;
} else if (lane === 'geo') {
  grep = /real geolocation confirms fresh student, then D005 manual absence rejects the blocked attempt$/;
} else if (lane === 'registry-html') {
  grep = /(?:FULL ordinary user export downloads filtered HTML with exact safe fields|LIMITED ordinary user export downloads filtered HTML with exact safe fields|FULL admin applies group filters sorting and reset before downloading filtered HTML) @cloud-accepted$/;
}

// The Python admission above binds this exact enum list to the current manifest.
if (process.env.RCT_LOCAL_UI_BATCH_CASES) {
  const selected: unknown = JSON.parse(process.env.RCT_LOCAL_UI_BATCH_CASES);
  if (!Array.isArray(selected) || selected.length === 0 || selected.length > Object.keys(failedCases).length || new Set(selected).size !== selected.length
    || selected.some(id => typeof id !== 'string' || !Object.hasOwn(failedCases, id)))
    throw new Error('Local UI batch failed-case selection is invalid');
  const titles = (selected as FailedCaseId[]).filter(id => failedCases[id].lane === lane).map(id => failedCases[id].title);
  if (titles.length === 0) throw new Error('Local UI batch selected lane has no selected cases');
  const escape = (title: string) => title.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  grep = new RegExp(`(?:${titles.map(escape).join('|')})$`);
  grepInvert = undefined;
}

export default defineConfig({
  ...existing,
  testMatch,
  grep,
  grepInvert,
  outputDir: join(run, 'artifacts'),
  reporter: [['./fixtures/safe-reporter.ts']],
  fullyParallel: false,
  workers: 1,
  retries: 0,
  maxFailures: 0,
  forbidOnly: true,
  use: { ...existing.use, ignoreHTTPSErrors: false, trace: 'off', video: 'off', screenshot: 'off' },
  projects: existing.projects
    ?.filter(project => project.name === 'chromium')
    .map(project => ({
      ...project,
      use: { ...project.use, ignoreHTTPSErrors: false, trace: 'off', video: 'off', screenshot: 'off' },
    })),
});
