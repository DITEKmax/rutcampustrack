import { lstatSync } from 'node:fs';
import { isAbsolute, join } from 'node:path';
import { defineConfig } from '@playwright/test';
import existing from './playwright.config';
import { readTeachingInstant } from './fixtures/teaching-clock';

// Pinned Playwright checks this before reading a page snapshot, including during teardown.
process.env.PLAYWRIGHT_NO_COPY_PROMPT = '1';

const remaining5 = process.env.RCT_E2E_REMAINING5 === 'true';
const adminLifecycle = process.env.RCT_E2E_ADMIN_LIFECYCLE === 'true';
const roleLogout = process.env.RCT_E2E_ROLE_LOGOUT === 'true';
const webkitSubset = process.env.RCT_E2E_WEBKIT_SUBSET === 'true';
const workerLifecycle = process.env.RCT_E2E_SW_LIFECYCLE === 'true';
if ((adminLifecycle || roleLogout || webkitSubset || workerLifecycle) && !remaining5) {
  throw new Error('Extended mobile scenarios require the private serial mobile lane');
}
if ([adminLifecycle, roleLogout, webkitSubset, workerLifecycle].filter(Boolean).length > 1) {
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
  teachingInstant = readTeachingInstant(process.env.RCT_E2E_TEACHING_CLOCK_DIR);
}

/** One shared active semester and task-owned disposable dataset; never run in parallel with another seed writer. */
export default defineConfig<{}, { teachingInstant: Date | undefined }>({
  ...existing,
  outputDir: remaining5 ? join(run!, 'artifacts') : './test-results/real-mobile',
  reporter: [['./fixtures/safe-reporter.ts']],
  use: {
    ...existing.use,
    trace: 'off',
    video: 'off',
    screenshot: 'off',
    ...(remaining5 ? { timezoneId: 'Europe/Moscow', teachingInstant } : {}),
  },
  testMatch: roleLogout
    ? [
        /mobile-real-server\.spec\.ts/,
        /cloud-accepted-role-authority\.spec\.ts/,
        /cloud-accepted-admin-grants\.spec\.ts/,
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
        grep: workerLifecycle
          ? /(?:production worker update activates through the update gate and retains a warm launch|synthetic installation prompt handles dismissed unavailable and accepted outcomes|synthetic iOS installation instructions dismiss persistently without a native prompt)$/
          : webkitSubset
            ? /(?:another live session revokes the browser session and recovery exposes login only|warm offline reload remains read-only; revoked session reconnect clears the cached user|D002 reconnect rechecks changed grants and a late old feed cannot replace the new account)$/
            : roleLogout
              ? /(?:SUSPENDED STUDENT retains active TEACHER access until UI logout-all revokes both sessions|four durable grants default to STUDENT; explicit role survives reload and stale version cannot switch @cloud-accepted|unconfirmed UI logout clears its owner and rejects a late real feed through reload @cloud-accepted|FULL multirole ADMIN assigns updates and revokes LIMITED rights through Vue without foreign-role calls @cloud-accepted)$/
              : adminLifecycle
                ? /ADMIN creates a multirole headman and restores the archived account without old authority$/
                : /(?:LIMITED ADMIN views accounts while creation is denied by UI and server|empty foreign-group homework and transport failure provide a real retry path|another live session revokes the browser session and recovery exposes login only|D003 reuse admits twice; suspended grant yields neutral bootstrap and protected denials|D003 signed HEADMAN TMA marks an isolated own-group roster row|warm offline reload remains read-only; revoked session reconnect clears the cached user|D002 reconnect rechecks changed grants and a late old feed cannot replace the new account|D002 TMA stays online-only beside a warm PWA snapshot)$/,
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
