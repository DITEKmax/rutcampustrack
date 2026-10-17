import { randomUUID } from 'node:crypto'
import { mkdtempSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const root = dirname(fileURLToPath(import.meta.url))
const output = mkdtempSync(join(tmpdir(), 'rct-artifact-control-'))
const marker = `RCT_PUBLIC_CONTROL_${randomUUID()}`
const red = process.argv.includes('--unprotected')
const lateControl = 'late fixture diagnostics are safe before the producer attachment'
const missingGuardControl = 'privacy guard rejects before an explicit operation fixture'
const emit = (value) => {
  const text = JSON.stringify(value)
  if (process.env.RCT_E2E_CONTROL_RECEIPT_PATH)
    writeFileSync(process.env.RCT_E2E_CONTROL_RECEIPT_PATH, `${text}\n`)
  process.stdout.write(`${text}\n`)
}
const files = (path) =>
  readdirSync(path, { withFileTypes: true }).flatMap((entry) =>
    entry.isDirectory() ? files(join(path, entry.name)) : [join(path, entry.name)],
  )
function run(name, interrupted, missingGuard = false) {
  const artifacts = join(output, name)
  const supervision = join(output, `${name}-supervision`)
  mkdirSync(supervision)
  const child = spawnSync(
    process.execPath,
    [
      '--max-old-space-size=512',
      join(root, 'node_modules/playwright/cli.js'),
      'test',
      '--config',
      'playwright.artifact-control.config.ts',
      ...(missingGuard
        ? ['--grep', missingGuardControl]
        : red || interrupted
          ? ['--grep', lateControl]
          : []),
    ],
    {
      cwd: root,
      encoding: 'utf8',
      env: {
        ...process.env,
        RCT_E2E_CONTROL_OUTPUT_DIR: artifacts,
        RCT_E2E_CONTROL_SUPERVISION_DIR: supervision,
        RCT_E2E_CONTROL_MARKER: marker,
        RCT_E2E_CONTROL_UNPROTECTED: String(red),
        RCT_E2E_CONTROL_INTERRUPT: String(interrupted),
        RCT_E2E_CONTROL_MISSING_GUARD: String(missingGuard),
      },
    },
  )
  const artifactFiles = files(artifacts)
  const leaked = [
    child.stdout ?? '',
    child.stderr ?? '',
    ...artifactFiles.map((path) => readFileSync(path).toString()),
  ].some((value) => value.includes(marker))
  const read = (path) => {
    try {
      return JSON.parse(readFileSync(path, 'utf8'))
    } catch {
      return null
    }
  }
  return {
    childStatus: child.status,
    childSignal: child.signal,
    markerAbsent: !leaked,
    report: read(join(artifacts, 'safe-results.json')),
    producer: read(join(supervision, 'producer-boundary.safe.json')),
    explicitOperationStarted:
      read(join(supervision, 'explicit-operation.safe.json'))?.explicitOperationStarted === true,
    artifactFiles,
  }
}
try {
  const normal = run('normal', false)
  const expectedFailures = normal.report?.results.filter(
    (row) => row.status === 'failed' && row.expectedStatus === 'failed' && row.errorCount > 0,
  ).length
  const producerPassed =
    normal.producer?.stage === 'AUTOMATIC_CONTEXT_BEFORE_ATTACHMENT' &&
    normal.producer.failureRetained === true &&
    normal.producer.attachmentNotYetCreated === true
  if (red) {
    const reproduced =
      normal.childStatus === 0 &&
      expectedFailures === 1 &&
      producerPassed &&
      normal.producer.markerAbsent === false
    // Red fails the producer oracle while the real test remains an expected failure.
    emit({
      state: reproduced ? 'LATE_ERROR_PRODUCER_COUNTEREXAMPLE' : 'RED_CONTROL_NOT_REPRODUCED',
      nativeChildStatus: normal.childStatus,
      failedTestRetained: expectedFailures === 1,
      producerMarkerAbsent: normal.producer?.markerAbsent ?? null,
      reporterMarkerAbsent: normal.markerAbsent,
      browser: 'NOT_STARTED',
    })
    process.exitCode = 1
  } else {
    const successRetained =
      normal.report?.results.filter((row) => row.status === 'passed').length === 3
    const titleControl = normal.artifactFiles
      .filter((path) => path.endsWith('control.json'))
      .some(
        (path) => JSON.parse(readFileSync(path, 'utf8')).originalFillTitleContainsMarker === true,
      )
    const guardControl = normal.artifactFiles
      .filter((path) => path.endsWith('guard-control.json'))
      .map((path) => JSON.parse(readFileSync(path, 'utf8')))[0]
    const guardPassed = guardControl && Object.values(guardControl).every((value) => value === true)
    const interrupted = run('interrupted', true)
    const missingGuard = run('missing-guard', false, true)
    const missingGuardRejectedBeforeOperation =
      missingGuard.childStatus === 0 &&
      missingGuard.markerAbsent &&
      !missingGuard.explicitOperationStarted &&
      missingGuard.report?.results.filter(
        (row) => row.status === 'failed' && row.expectedStatus === 'failed' && row.errorCount > 0,
      ).length === 1
    const interruptionPassed =
      interrupted.producer?.interrupted === true &&
      interrupted.producer.markerAbsent === true &&
      interrupted.producer.failureRetained === true &&
      interrupted.producer.attachmentNotYetCreated === true &&
      interrupted.markerAbsent &&
      interrupted.report?.results.filter((row) => row.status === 'failed' && row.errorCount > 0)
        .length === 1
    const passed =
      normal.childStatus === 0 &&
      normal.markerAbsent &&
      expectedFailures === 3 &&
      successRetained &&
      titleControl &&
      guardPassed &&
      producerPassed &&
      normal.producer.markerAbsent === true &&
      interruptionPassed &&
      missingGuardRejectedBeforeOperation
    // Never forward raw child output, even if this control detects a leak.
    emit({
      state: passed ? 'PRIVACY_BOUNDARIES_PASSED' : 'PRIVACY_CONTROL_FAILED',
      normalChildStatus: normal.childStatus,
      markerAbsent: normal.markerAbsent,
      producerBoundary: normal.producer,
      originalMetadataLeakReproduced: titleControl,
      expectedFailureRetained: expectedFailures === 3,
      successRetained,
      guardControl,
      interruptedChildStatus: interrupted.childStatus,
      interruptedChildSignal: interrupted.childSignal,
      interruptionBoundary: interrupted.producer,
      interruptionPassed,
      missingGuardRejectedBeforeOperation,
      browser: 'NOT_STARTED',
    })
    if (!passed) process.exitCode = 1
  }
} catch {
  emit({ state: 'CONTROL_EXECUTION_ERROR', rawErrorSuppressed: true, browser: 'NOT_STARTED' })
  process.exitCode = 1
} finally {
  // Public control payloads only; interruption privacy depends on the producer boundary.
  rmSync(output, { recursive: true, force: true })
}
