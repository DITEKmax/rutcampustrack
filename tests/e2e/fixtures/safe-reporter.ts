import { mkdirSync, writeFileSync } from 'node:fs';
import { basename, isAbsolute, relative, resolve } from 'node:path';
import type {
  FullConfig,
  FullResult,
  Reporter,
  TestCase,
  TestError,
  TestResult,
} from '@playwright/test/reporter';

export const safeFailureAnnotation = 'rct-safe-failure-v1';

const failureStages = {
  UNKNOWN: 'UNKNOWN',
  ARTIFACT_REPORTER_GUARD: 'ARTIFACT_PRIVACY',
  ARTIFACT_CAPTURE_GUARD: 'ARTIFACT_PRIVACY',
  DATASET_TARGET_GUARD: 'DATASET_TARGET',
  DATASET_CREDENTIALS_MISSING: 'DATASET_USER',
  ACTOR_LOGIN_HTTP: 'ACTOR_LOGIN',
  ACTOR_SESSION_HTTP: 'ACTOR_SESSION',
  ACTOR_ROLE_HTTP: 'ACTOR_ROLE',
  BROWSER_ROLE_DENIED: 'BROWSER_ROLE',
  CLOCK_TARGET_GUARD: 'TEACHING_CLOCK',
  CLOCK_DISPOSABLE_GUARD: 'TEACHING_CLOCK',
  CLOCK_FORMAT_GUARD: 'TEACHING_CLOCK',
  CLOCK_INSTANT_GUARD: 'TEACHING_CLOCK',
  CLOCK_WINDOW_GUARD: 'TEACHING_CLOCK',
  INVALID_TIME_VALUE: 'UNKNOWN',
} as const;
type FailureCause = keyof typeof failureStages;
const failureHttpStatuses = [0, 400, 401, 403, 404, 409, 410, 422, 423, 429, 500, 502, 503, 504] as const;
type FailureHttpStatus = (typeof failureHttpStatuses)[number];
export interface SafeFailureDiagnostic {
  stage: (typeof failureStages)[FailureCause];
  cause: FailureCause;
  httpStatus: FailureHttpStatus;
}
const knownFailureMessages: Record<string, FailureCause> = {
  'Real-mobile requires the dedicated safe reporter, no debug/extra reporter and snapshot capture disabled': 'ARTIFACT_REPORTER_GUARD',
  'Real-mobile credentials require trace/video/screenshots off': 'ARTIFACT_CAPTURE_GUARD',
  'Synthetic dataset requires RCT_E2E_SYNTHETIC_SEED=true and disposable HTTPS loopback:8443; private/local stands are forbidden': 'DATASET_TARGET_GUARD',
  'Synthetic user creation omitted credentials': 'DATASET_CREDENTIALS_MISSING',
  'Browser role admission denied': 'BROWSER_ROLE_DENIED',
  'Teaching-clock lane requires explicit synthetic seed and absolute fixture directory': 'CLOCK_TARGET_GUARD',
  'Teaching-clock lane requires a bounded disposable fixture': 'CLOCK_DISPOSABLE_GUARD',
  'Teaching-clock fixture must contain an ISO UTC instant': 'CLOCK_FORMAT_GUARD',
  'Teaching-clock instant is invalid': 'CLOCK_INSTANT_GUARD',
  'Positive-geo lane requires an ordinary weekday at 09:00 Europe/Moscow': 'CLOCK_WINDOW_GUARD',
  // An intrinsic public error hint; it does not identify a clock or a failing operation.
  'Invalid time value': 'INVALID_TIME_VALUE',
};
function failureDiagnostic(cause: FailureCause = 'UNKNOWN', httpStatus: FailureHttpStatus = 0): SafeFailureDiagnostic {
  return { stage: failureStages[cause], cause, httpStatus };
}
function ownData(value: unknown, key: string): unknown {
  if (typeof value !== 'object' || value === null) return undefined;
  try {
    return Object.getOwnPropertyDescriptor(value, key)?.value;
  } catch {
    return undefined;
  }
}

/** Only bounded own message data is read in RAM; no name/stack/getter/toString or dynamic path is inspected. */
export function classifySafeFailure(error: unknown): SafeFailureDiagnostic {
  const rawMessage = ownData(error, 'message');
  if (typeof rawMessage !== 'string' || rawMessage.length > 512 || /[\r\n]/.test(rawMessage))
    return failureDiagnostic();
  // Pinned Playwright serializeError adds this exact frame; no dynamic error name is read.
  if (rawMessage === 'RangeError: Invalid time value')
    return failureDiagnostic('INVALID_TIME_VALUE');
  const message = rawMessage.startsWith('Error: ') ? rawMessage.slice('Error: '.length) : rawMessage;
  if (Object.prototype.hasOwnProperty.call(knownFailureMessages, message))
    return failureDiagnostic(knownFailureMessages[message]);
  const status = /^Synthetic actor (login|session|role (?:STUDENT|HEADMAN|TEACHER|ADMIN)): HTTP (400|401|403|404|409|410|422|423|429|500|502|503|504)$/.exec(message);
  if (!status) return failureDiagnostic();
  const cause = status[1] === 'login' ? 'ACTOR_LOGIN_HTTP' : status[1] === 'session' ? 'ACTOR_SESSION_HTTP' : 'ACTOR_ROLE_HTTP';
  return failureDiagnostic(cause, Number(status[2]) as FailureHttpStatus);
}

/** Validate the worker's public annotation channel; arbitrary annotation descriptions never enter artifacts. */
export function safeFailureDiagnostics(annotations: unknown, hasErrors = false): SafeFailureDiagnostic[] {
  const result: SafeFailureDiagnostic[] = [];
  if (Array.isArray(annotations)) {
    for (let index = 0; index < Math.min(annotations.length, 32) && result.length < 8; index++) {
      const annotation = ownData(annotations, String(index));
      if (ownData(annotation, 'type') !== safeFailureAnnotation) continue;
      const description = ownData(annotation, 'description');
      const match = typeof description === 'string' && description.length <= 96
        ? /^([A-Z_]+):(0|400|401|403|404|409|410|422|423|429|500|502|503|504)$/.exec(description)
        : null;
      const valid = match && Object.prototype.hasOwnProperty.call(failureStages, match[1]);
      const diagnostic = valid
        ? failureDiagnostic(match[1] as FailureCause, Number(match[2]) as FailureHttpStatus)
        : failureDiagnostic();
      // Only the three source HTTP checks may carry a nonzero public status.
      const httpCause = ['ACTOR_LOGIN_HTTP', 'ACTOR_SESSION_HTTP', 'ACTOR_ROLE_HTTP'].includes(diagnostic.cause);
      const value = (httpCause && diagnostic.httpStatus === 0) || (!httpCause && diagnostic.httpStatus !== 0)
        ? failureDiagnostic()
        : diagnostic;
      if (!result.some((item) => item.cause === value.cause && item.httpStatus === value.httpStatus))
        result.push(value);
    }
  }
  return result.length || !hasErrors ? result : [failureDiagnostic()];
}

interface SafeResult {
  file: string;
  line: number;
  project: string;
  status: TestResult['status'];
  expectedStatus: TestCase['expectedStatus'];
  durationMs: number;
  errorCount: number;
  diagnostics: SafeFailureDiagnostic[];
  errorLocations: { file: string; line: number }[];
}

/** Allowlist output only. Raw step titles, errors, attachments and worker output are never serialized. */
export default class SafeReporter implements Reporter {
  private outputDir = '';
  private results: SafeResult[] = [];
  private runnerErrors = 0;
  private suppressedStdoutBytes = 0;
  private suppressedStderrBytes = 0;
  private unsafeArtifact = false;

  printsToStdio() {
    return true;
  }
  onBegin(config: FullConfig) {
    this.outputDir = config.projects[0].outputDir;
    mkdirSync(this.outputDir, { recursive: true });
  }
  onStdOut(chunk: string | Buffer) {
    this.suppressedStdoutBytes += Buffer.byteLength(chunk);
  }
  onStdErr(chunk: string | Buffer) {
    this.suppressedStderrBytes += Buffer.byteLength(chunk);
  }
  onError(_error: TestError) {
    this.runnerErrors++;
  }
  onTestEnd(test: TestCase, result: TestResult) {
    const row: SafeResult = {
      file: basename(test.location.file),
      line: test.location.line,
      project: test.parent.project()?.name ?? 'unknown',
      status: result.status,
      expectedStatus: test.expectedStatus,
      durationMs: result.duration,
      errorCount: result.errors.length,
      diagnostics: safeFailureDiagnostics(result.annotations, result.errors.length > 0),
      errorLocations: result.errors.flatMap((error) =>
        error.location ? [{ file: basename(error.location.file), line: error.location.line }] : [],
      ),
    };
    this.results.push(row);
    // Automatic error contexts are created during worker teardown, after afterEach.
    // Replace attached artifact contents with safe metadata as a second boundary.
    for (const attachment of result.attachments) {
      if (!attachment.path) continue;
      const path = resolve(attachment.path);
      const child = relative(this.outputDir, path);
      if (
        isAbsolute(child) ||
        child === '..' ||
        child.startsWith(`..${process.platform === 'win32' ? '\\' : '/'}`)
      ) {
        this.unsafeArtifact = true;
        continue;
      }
      writeFileSync(path, JSON.stringify(row, null, 2));
    }
    process.stdout.write(`${row.project} ${row.file}:${row.line} ${row.status}\n`);
  }
  async onEnd(result: FullResult) {
    if (!this.outputDir) return { status: 'failed' as const };
    const status = this.unsafeArtifact ? 'failed' : result.status;
    writeFileSync(
      resolve(this.outputDir, 'safe-results.json'),
      JSON.stringify(
        {
          status,
          runnerErrors: this.runnerErrors,
          unsafeArtifact: this.unsafeArtifact,
          suppressedStdoutBytes: this.suppressedStdoutBytes,
          suppressedStderrBytes: this.suppressedStderrBytes,
          results: this.results,
        },
        null,
        2,
      ),
    );
    process.stdout.write(
      `real-mobile ${status}; ${this.results.length} results; ${this.runnerErrors} runner errors\n`,
    );
    return { status };
  }
}
