import { beginDiagnosticCase, captureDiagnosticFailure } from './diagnostic-events';
import { test as base, type TestInfo, type WorkerInfo } from '@playwright/test';
import { closeSync, constants, fstatSync, lstatSync, openSync, readFileSync, realpathSync, writeSync } from 'node:fs';
import { failedCaseId } from './failed-case-diagnostics';
import { resolve } from 'node:path';
import {
  classifySafeFailure,
  discardReportedFailureHint,
  reportedFailureHints,
  retainReportedFrameworkError,
  safeFailureAnnotation,
  safeFailureDiagnostics,
} from './safe-reporter';

/** Automatic fixtures start before explicit page/dataset fixtures and finish after their teardown. */
export const test = base.extend<{ privateArtifacts: void }>({
  privateArtifacts: [
    async ({}, use, info) => {
      try {
        beginDiagnosticCase(info);
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
  resolve(__dirname, '../specs/mobile-schedule-lifecycle.spec.ts'),
  resolve(__dirname, '../specs/mobile-lesson-lifecycle.spec.ts'),
  resolve(__dirname, '../specs/mobile-attendance-requests.spec.ts'),
  resolve(__dirname, 'assignment-lifecycle.spec.ts'),
  resolve(__dirname, 'semester-lifecycle.spec.ts'),
  resolve(__dirname, '../specs/mobile-homework-lifecycle.spec.ts'),
  resolve(__dirname, '../specs/mobile-map-lifecycle.spec.ts'),
  resolve(__dirname, '../specs/cloud-accepted-registry-export.spec.ts'),
  resolve(__dirname, '../specs/cloud-accepted-helper-capabilities.spec.ts'),
  resolve(__dirname, '../specs/cloud-accepted-auth-security.spec.ts'),
  resolve(__dirname, '../specs/cloud-accepted-role-authority.spec.ts'),
  resolve(__dirname, '../specs/cloud-accepted-admin-grants.spec.ts'),
  resolve(__dirname, '../clock-specs/teaching-clock-geo.spec.ts'),
  resolve(__dirname, '../local-lifecycle-prerequisites.spec.ts'),
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
    const location = { file: info.file, line: sourceLine, column };
    if (
      info.file === publicSpecs[12] &&
      info.line === 111 &&
      sourceLine === 430 &&
      sourceLines[420]?.trim() === 'const openEditor = async (assigned: boolean) => {' &&
      sourceLines[429]?.trim() === '.click();'
    ) {
      if (lines.some((reportedLine) => /[\x00-\x1f\x7f-\x9f\u2028\u2029]/.test(reportedLine)))
        return location;
      let caller: typeof location | undefined;
      for (const callerLine of lines.slice(lines.indexOf(line) + 1)) {
        if (!callerLine.startsWith('    at ')) {
          if (callerLine !== '') return location;
          continue;
        }
        if (callerLine.length > 1024 || /[\x00-\x1f\x7f-\x9f\u2028\u2029]/.test(callerLine))
          return location;
        const callerFrame =
          /^    at (?:[^()\r\n]{1,128} )?\(([^()\r\n]+):([1-9][0-9]{0,5}):([1-9][0-9]{0,5})\)$/.exec(
            callerLine,
          ) ?? /^    at ([^()\r\n]+):([1-9][0-9]{0,5}):([1-9][0-9]{0,5})$/.exec(callerLine);
        if (!callerFrame) return location;
        if (callerFrame[1] !== info.file) continue;
        const callerSourceLine = Number(callerFrame[2]);
        const callerColumn = Number(callerFrame[3]);
        const expectedCaller =
          callerSourceLine === 452
            ? 'await openEditor(false);'
            : callerSourceLine === 470 || callerSourceLine === 488
              ? 'await openEditor(true);'
              : null;
        if (
          caller ||
          expectedCaller === null ||
          sourceLines[callerSourceLine - 1]?.trim() !== expectedCaller ||
          callerColumn > sourceLines[callerSourceLine - 1].length + 1
        )
          return location;
        caller = { file: info.file, line: callerSourceLine, column: callerColumn };
      }
      return caller ?? location;
    }
    return location;
  }
  return fallback;
}

/** Only the explicitly selected owner replay keeps raw errors before the ordinary scrub. */
const failedCiRawCounts = new Map<string, number>();
const failedCiRawObjects = new WeakSet<object>();
function retainFailedCiRawError(info: TestInfo, error: unknown): void {
  if (process.env.RCT_LOCAL_RELEASE_MODE !== 'failed-ci') return;
  const caseId = failedCaseId(info.title);
  if (!caseId || !['HEADMAN_TEACHER_READ', 'FULL_ADMIN_RIGHTS', 'HOMEWORK_PERIOD', 'SEMESTER_METADATA'].includes(caseId)
      || !error || typeof error !== 'object' || failedCiRawObjects.has(error)) return;
  let fd: number | undefined;
  try {
    const root = resolve(__dirname, '../../..'), nonce = process.env.RCT_LOCAL_STAGE17_NONCE;
    if (process.platform !== 'linux' || !nonce || !/^[a-f0-9]{24}$/.test(nonce)) throw new Error('Private identity');
    const run = resolve(root, '.agent/evidence/local-runs', `rct-local-stage17-${nonce.slice(0, 12)}`);
    const directory = resolve(run, 'framework-private/failed-ci');
    for (const path of [run, resolve(run, 'framework-private'), directory]) {
      const metadata = lstatSync(path);
      if (!metadata.isDirectory() || metadata.isSymbolicLink() || metadata.uid !== process.getuid?.()
          || (metadata.mode & 0o777) !== 0o700 || realpathSync(path) !== path) throw new Error('Private directory');
    }
    const manifest = resolve(run, 'owner.json'), metadata = lstatSync(manifest);
    if (process.env.RCT_LOCAL_STAGE17_MANIFEST !== manifest || !metadata.isFile() || metadata.isSymbolicLink()
        || metadata.nlink !== 1 || metadata.uid !== process.getuid?.() || (metadata.mode & 0o777) !== 0o600
        || metadata.size <= 0 || metadata.size > 8 * 1024 ** 2) throw new Error('Private manifest');
    const owner = JSON.parse(readFileSync(manifest, 'utf8'));
    if (owner.root !== root || owner.run !== run || owner.nonce !== nonce || owner.uid !== process.getuid?.()
        || owner.source_sha !== process.env.RCT_LOCAL_RELEASE_REVISION || owner.mode !== process.env.RCT_LOCAL_STAGE17_MODE
        || !['ui-batch', 'special13'].includes(owner.mode)) throw new Error('Private source binding');
    const count = failedCiRawCounts.get(caseId) ?? 0;
    if (count >= 16) throw new Error('Private test bound');
    const fields: Record<string, string> = {};
    for (const [name, limit] of [['message', 8192], ['stack', 16384], ['name', 128], ['code', 128]] as const) {
      const value = Object.getOwnPropertyDescriptor(error, name)?.value;
      if (typeof value === 'string') fields[name] = value.slice(0, limit);
    }
    const raw = Buffer.from(JSON.stringify({ caseId, ordinal: count + 1, ...fields }) + '\n');
    if (raw.length > 64 * 1024) throw new Error('Private error bound');
    fd = openSync(resolve(directory, 'test-errors.private.jsonl'), constants.O_WRONLY | constants.O_CREAT | constants.O_APPEND | constants.O_NOFOLLOW, 0o600);
    const target = fstatSync(fd);
    if (!target.isFile() || target.nlink !== 1 || target.uid !== process.getuid?.() || (target.mode & 0o777) !== 0o600
        || target.size + raw.length > 256 * 1024) throw new Error('Private final bound');
    let written = 0;
    while (written < raw.length) {
      const size = writeSync(fd, raw, written, raw.length - written);
      if (size <= 0) throw new Error('Private write');
      written += size;
    }
    failedCiRawCounts.set(caseId, count + 1);
    failedCiRawObjects.add(error);
  } catch {
    // A finite marker exposes a retention failure; the original test error survives.
    process.stderr.write('RCT_FAILED_CI_RAW_RETENTION_V1 FAIL\n');
  } finally {
    if (fd !== undefined) {
      try { closeSync(fd); } catch { process.stderr.write('RCT_FAILED_CI_RAW_RETENTION_V1 FAIL\n'); }
    }
  }
}

/** Run before Playwright writes automatic error-context.md, which otherwise includes raw call logs. */
export function sanitizeTestErrors(info: TestInfo): void {
  for (const error of info.errors) {
    retainFailedCiRawError(info, error);
    captureDiagnosticFailure(info, error, () => {
    // The generic rewritten message adds no new cause on repeated sanitizer passes.
    const message = Object.getOwnPropertyDescriptor(error, 'message');
    if (
      message?.value !==
      'Check failed; source location and outcome are recorded in safe-results.json'
    ) {
      retainReportedFrameworkError(info.annotations, error);
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
    });
  }
}
