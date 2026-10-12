import { test as base, type TestInfo, type WorkerInfo } from '@playwright/test';

/** Automatic fixtures start before explicit page/dataset fixtures and finish after their teardown. */
export const test = base.extend<{ privateArtifacts: void }>({
  privateArtifacts: [
    async ({}, use, info) => {
      requirePrivateArtifacts(info);
      try {
        await use();
      } finally {
        // afterEach cannot cover errors added by page/context/request fixture teardown.
        sanitizeTestErrors(info);
      }
    },
    { auto: true, box: true },
  ],
});

/** Validate before creating credentials. Extra reporters/debug logging can disclose API arguments. */
export function requirePrivateArtifacts(info: WorkerInfo): void {
  const reporters = info.config.reporter;
  if (
    reporters.length !== 1 ||
    reporters[0][0] !== require.resolve('./safe-reporter.ts') ||
    process.env.PW_TEST_REPORTER ||
    process.env.DEBUG ||
    process.env.DEBUG_FILE ||
    process.env.PWDEBUG ||
    process.env.PLAYWRIGHT_NO_COPY_PROMPT !== '1'
  ) {
    throw new Error(
      'Real-mobile requires the dedicated safe reporter, no debug/extra reporter and snapshot capture disabled',
    );
  }
  const use = info.project.use;
  if (use.trace !== 'off' || use.video !== 'off' || use.screenshot !== 'off') {
    throw new Error('Real-mobile credentials require trace/video/screenshots off');
  }
}

/** Run before Playwright writes automatic error-context.md, which otherwise includes raw call logs. */
export function sanitizeTestErrors(info: TestInfo): void {
  for (const error of info.errors) {
    const record = error as Record<string, unknown>;
    const location = record.location;
    for (const key of Object.keys(error)) delete record[key];
    error.message = 'Check failed; source location and outcome are recorded in safe-results.json';
    error.stack = `Error: check failed\n    at (${info.file}:${info.line}:1)`;
    if (location) record.location = location;
  }
}
