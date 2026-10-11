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

interface SafeResult {
  file: string;
  line: number;
  project: string;
  status: TestResult['status'];
  expectedStatus: TestCase['expectedStatus'];
  durationMs: number;
  errorCount: number;
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
