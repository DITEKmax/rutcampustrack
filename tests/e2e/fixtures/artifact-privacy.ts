import { test as base, type TestInfo, type WorkerInfo } from '@playwright/test';
import {
  classifySafeFailure,
  discardReportedFailureHint,
  reportedFailureHints,
  safeFailureAnnotation,
  safeFailureDiagnostics,
} from './safe-reporter';

/** Automatic fixtures start before explicit page/dataset fixtures and finish after their teardown. */
export const test = base.extend<{ privateArtifacts: void }>({
  privateArtifacts: [
    async ({}, use, info) => {
      try {
        requirePrivateArtifacts(info);
        await use();
      } catch (error) {
        recordSafeFailure(info, error);
        recordReportedHints(info, error);
        throw error;
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

function recordReportedHints(info: TestInfo, error: unknown): void {
  for (const diagnostic of reportedFailureHints(error)) {
    const previous = safeFailureDiagnostics(info.annotations);
    const prefix = diagnostic.cause.startsWith('REPORTED_CLASS_')
      ? 'REPORTED_CLASS_'
      : 'REPORTED_FRAME_';
    if (previous.length >= 8 || previous.some((item) => item.cause.startsWith(prefix))) continue;
    info.annotations.push({
      type: safeFailureAnnotation,
      description: `${diagnostic.cause}:0`,
    });
  }
}

function recordSafeFailure(info: TestInfo, error: unknown): void {
  const diagnostic = classifySafeFailure(error);
  const previous = safeFailureDiagnostics(info.annotations);
  const coreCount = previous.filter((item) => !item.cause.startsWith('REPORTED_')).length;
  if (
    coreCount >= 8 ||
    previous.some(
      (item) => item.cause === diagnostic.cause && item.httpStatus === diagnostic.httpStatus,
    )
  )
    return;
  if (previous.length >= 8 && !discardReportedFailureHint(info.annotations)) return;
  info.annotations.push({
    type: safeFailureAnnotation,
    description: `${diagnostic.cause}:${diagnostic.httpStatus}`,
  });
}

/** Run before Playwright writes automatic error-context.md, which otherwise includes raw call logs. */
export function sanitizeTestErrors(info: TestInfo): void {
  for (const error of info.errors) {
    // The generic rewritten message adds no new cause on repeated sanitizer passes.
    const message = Object.getOwnPropertyDescriptor(error, 'message');
    if (
      message?.value !==
      'Check failed; source location and outcome are recorded in safe-results.json'
    ) {
      recordSafeFailure(info, error);
      recordReportedHints(info, error);
    }
    const record = error as Record<string, unknown>;
    const location = record.location;
    for (const key of Object.keys(error)) delete record[key];
    error.message = 'Check failed; source location and outcome are recorded in safe-results.json';
    error.stack = `Error: check failed\n    at (${info.file}:${info.line}:1)`;
    if (location) record.location = location;
  }
}
