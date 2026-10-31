import { defineConfig } from '@playwright/test';
import { lstatSync, realpathSync } from 'node:fs';
import { join } from 'node:path';
import { requireLocalSpecialLane } from './fixtures/local-special';
import existing from './playwright.config';

if (
  process.env.RCT_LOCAL_SPECIAL_PREPARATION !== 'true' ||
  process.env.RCT_LOCAL_SPECIAL_COHORT !== 'lifecycle' ||
  process.env.RCT_LOCAL_SPECIAL_PHASE !== 'main' ||
  process.env.RCT_LOCAL_SPECIAL_DISCOVERY ||
  process.argv.includes('--list')
) throw new Error('Lifecycle prerequisites require their explicit execution context');
const context = requireLocalSpecialLane('semester-metadata');
const outputDir = join(context.run, 'prerequisites', 'artifacts');
for (const path of [join(context.run, 'prerequisites'), outputDir]) {
  const info = lstatSync(path);
  if (!info.isDirectory() || info.isSymbolicLink() || info.uid !== process.getuid?.() ||
      (info.mode & 0o777) !== 0o700 || realpathSync(path) !== path)
    throw new Error('Lifecycle prerequisites require owned private output directories');
}

export default defineConfig({
  ...existing,
  testDir: '.',
  testMatch: 'local-lifecycle-prerequisites.spec.ts',
  testIgnore: [],
  grep: /fresh local lifecycle prerequisites use the canonical FIRST3 dataset$/,
  grepInvert: undefined,
  outputDir,
  reporter: [['./fixtures/safe-reporter.ts', {
    localFrameworkDiagnostic: true,
    localPreparationDiagnostic: true,
    localDiagnosticOutputDir: outputDir,
  }]],
  timeout: 180000,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  maxFailures: 0,
  forbidOnly: true,
  use: { ...existing.use, ignoreHTTPSErrors: false, trace: 'off', video: 'off', screenshot: 'off' },
  projects: existing.projects?.filter(project => project.name === 'chromium').map(project => ({
    ...project,
    use: { ...project.use, ignoreHTTPSErrors: false, trace: 'off', video: 'off', screenshot: 'off' },
  })),
});
