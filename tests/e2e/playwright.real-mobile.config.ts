import { lstatSync } from 'node:fs';
import { isAbsolute, join } from 'node:path';
import { defineConfig } from '@playwright/test';
import existing from './playwright.config';
import { readTeachingInstantForConfig } from './fixtures/teaching-clock';
import { requireLocalLane } from './fixtures/local-stage17';

// Pinned Playwright checks this before reading a page snapshot, including during teardown.
process.env.PLAYWRIGHT_NO_COPY_PROMPT = '1';

const remaining5 = process.env.RCT_E2E_REMAINING5 === 'true';
const roleLogout = process.env.RCT_E2E_ROLE_LOGOUT === 'true';
const webkitSubset = process.env.RCT_E2E_WEBKIT_SUBSET === 'true';
const workerLifecycle = process.env.RCT_E2E_SW_LIFECYCLE === 'true';
const localStage17 = requireLocalLane(
  process.env.RCT_LESSON_LIFECYCLE_RUN ? 'lesson-lifecycle' : 'client',
);
const startupDiagnostic = process.env.RCT_LOCAL_CLIENT_STARTUP_DIAGNOSTIC === 'true';
if (startupDiagnostic && (!localStage17 || !remaining5 || process.env.RCT_LESSON_LIFECYCLE_RUN)) {
  throw new Error('Startup diagnostics require the admitted local client lane');
}
const botRoleMenu = process.env.RCT_E2E_BOT_ROLE_MENU === 'true';
if ((roleLogout || webkitSubset || workerLifecycle || botRoleMenu) && !remaining5) {
  throw new Error('Extended mobile scenarios require the private serial mobile lane');
}
if ([roleLogout, webkitSubset, workerLifecycle, botRoleMenu].filter(Boolean).length > 1) {
  throw new Error('Select one extended mobile scenario');
}
const run = process.env.RCT_REMAINING5_RUN;
let teachingInstant: Date | undefined;
if (remaining5) {
  if (!run || !isAbsolute(run)) {
    throw new Error('Remaining five require an absolute private run directory');
  }
  for (const path of [run, join(run, 'artifacts')]) {
    const info = lstatSync(path);
    if (!info.isDirectory() || info.isSymbolicLink() || (info.mode & 0o777) !== 0o700) {
      throw new Error('Remaining five require precreated private artifact directories');
    }
  }
  teachingInstant = readTeachingInstantForConfig(process.env.RCT_E2E_TEACHING_CLOCK_DIR);
}

/** One shared active semester and task-owned disposable dataset; never run in parallel with another seed writer. */
export default defineConfig<
  {},
  { teachingInstant: Date | undefined; minimumWeeklyReportWeeks: 0 | 18 }
>({
  ...existing,
  outputDir: remaining5 ? join(run!, 'artifacts') : './test-results/real-mobile',
  reporter: [
    startupDiagnostic
      ? ['./fixtures/safe-reporter.ts', { localStartupDiagnostic: true }]
      : ['./fixtures/safe-reporter.ts'],
  ],
  use: {
    ...existing.use,
    ignoreHTTPSErrors: false,
    trace: 'off',
    video: 'off',
    screenshot: 'off',
    ...(remaining5 ? { timezoneId: 'Europe/Moscow', teachingInstant } : {}),
    minimumWeeklyReportWeeks: 0,
  },
  testMatch: roleLogout
    ? [
        /mobile-real-server\.spec\.ts/,
        /cloud-accepted-role-authority\.spec\.ts/,
        /cloud-accepted-auth-security\.spec\.ts/,
      ]
    : /mobile-real-server\.spec\.ts/,
  testIgnore: [],
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: roleLogout ? 120000 : 60000,
  ...(remaining5
    ? {
        forbidOnly: true,
        grep: botRoleMenu
          ? /actual private bot menu roster and teacher Today use hosted current authority$/
          : workerLifecycle
            ? /(?:production worker update activates through the update gate and retains a warm launch|synthetic installation prompt handles dismissed unavailable and accepted outcomes|synthetic iOS installation instructions dismiss persistently without a native prompt)$/
            : webkitSubset
              ? /@session-revocation(?= |$)/
              : roleLogout
                ? /(?:SUSPENDED STUDENT retains active TEACHER access until UI logout-all revokes both sessions|unconfirmed UI logout clears its owner and rejects a late real feed through reload @cloud-accepted|own profile security changes password and replacement login rejects old authority @cloud-accepted|password recovery consumes its own delivered OTP and admits the replacement password @cloud-accepted)$/
                : /@(?:mobile-access-smoke|d002-reconnect-authority)(?= |$)|(?:empty foreign-group homework and transport failure provide a real retry path|warm offline reload remains read-only; revoked session reconnect clears the cached user|D002 late prior-account homework cannot replace the new account or offline snapshot)$/,
      }
    : {}),
  projects: existing.projects
    ?.filter((project) =>
      remaining5
        ? project.name === (webkitSubset ? 'webkit' : 'chromium')
        : project.name === 'chromium' || project.name === 'webkit',
    )
    .map((project) => ({
      ...project,
      ...(remaining5
        ? { use: { ...project.use, timezoneId: 'Europe/Moscow', teachingInstant } }
        : {}),
    })),
});
