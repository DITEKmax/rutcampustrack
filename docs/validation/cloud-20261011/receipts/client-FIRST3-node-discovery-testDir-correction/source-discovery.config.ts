import { defineConfig } from '/workspace/rutcampustrack/tests/e2e/node_modules/@playwright/test';
import first3 from '/workspace/rct-worktrees/client-ci-first3/tests/e2e/playwright.first3.config';

// Dedicated --list ONLY: no fixture/webServer/API/browser/login is executed.
export default defineConfig<{}, { teachingInstant: Date | undefined }>({
  ...first3,
  testDir: '/workspace/rct-worktrees/client-ci-first3/tests/e2e/first3-specs',
  webServer: undefined,
  reporter: [['/workspace/scratch/rct-validation/client_real_acceptance/ci-first3-db177-r1/discovery-reporter.cjs']],
});
