import { test as base, type TestInfo, type WorkerInfo } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
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

const publicSpecs = [
  resolve(__dirname, '../specs/mobile-real-server.spec.ts'),
  resolve(__dirname, 'assignment-lifecycle.spec.ts'),
  resolve(__dirname, '../specs/mobile-homework-lifecycle.spec.ts'),
  resolve(__dirname, '../specs/mobile-map-lifecycle.spec.ts'),
  resolve(__dirname, '../specs/cloud-accepted-registry-export.spec.ts'),
  resolve(__dirname, '../specs/cloud-accepted-helper-capabilities.spec.ts'),
];

/** An untrusted reported frame, never proof of a cause, operation or successful assertion. */
function reportedSpecLocation(error: unknown, info: Pick<TestInfo, 'file' | 'line'>) {
  const fallback = { file: info.file, line: info.line, column: 1 };
  if (!publicSpecs.includes(info.file) || typeof error !== 'object' || error === null)
    return fallback;
  let stack: unknown;
  try {
    stack = Object.getOwnPropertyDescriptor(error, 'stack')?.value;
  } catch {
    return fallback;
  }
  if (typeof stack !== 'string' || stack.length > 8192) return fallback;
  const lines = stack.split('\n');
  if (lines.length > 64) return fallback;
  let sourceLines: string[];
  try {
    sourceLines = readFileSync(info.file, 'utf8').split('\n');
    if (sourceLines[sourceLines.length - 1] === '') sourceLines.pop();
  } catch {
    return fallback;
  }
  for (const line of lines) {
    // Assertion messages may span lines; only exact stack frames are interpreted.
    if (!line.startsWith('    at ')) continue;
    if (line.length > 1024 || /[\x00-\x1f\x7f-\x9f\u2028\u2029]/.test(line)) return fallback;
    const frame =
      /^    at (?:[^()\r\n]{1,128} )?\(([^()\r\n]+):([1-9][0-9]{0,5}):([1-9][0-9]{0,5})\)$/.exec(
        line,
      ) ?? /^    at ([^()\r\n]+):([1-9][0-9]{0,5}):([1-9][0-9]{0,5})$/.exec(line);
    if (!frame) return fallback;
    if (frame[1] !== info.file) continue;
    const sourceLine = Number(frame[2]);
    const column = Number(frame[3]);
    if (sourceLine > sourceLines.length || column > sourceLines[sourceLine - 1].length + 1)
      return fallback;
    return { file: info.file, line: sourceLine, column };
  }
  return fallback;
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
    const location = reportedSpecLocation(error, info);
    for (const key of Object.keys(error)) delete record[key];
    // Own data properties also prevent inherited accessors from observing the safe replacement.
    Object.defineProperties(error, {
      message: {
        value: 'Check failed; source location and outcome are recorded in safe-results.json',
        writable: true,
        enumerable: true,
        configurable: true,
      },
      stack: {
        value: `Error: check failed\n    at (${location.file}:${location.line}:${location.column})`,
        writable: true,
        enumerable: true,
        configurable: true,
      },
      location: { value: location, writable: true, enumerable: true, configurable: true },
    });
  }
}
