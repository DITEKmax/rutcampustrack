import { randomUUID } from 'node:crypto'
import { mkdtempSync, readdirSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const root = dirname(fileURLToPath(import.meta.url))
const output = mkdtempSync(join(tmpdir(), 'rct-artifact-control-'))
const marker = `RCT_PUBLIC_CONTROL_${randomUUID()}`
const files = (path) =>
  readdirSync(path, { withFileTypes: true }).flatMap((entry) =>
    entry.isDirectory() ? files(join(path, entry.name)) : [join(path, entry.name)],
  )
try {
  const child = spawnSync(
    process.execPath,
    [
      join(root, 'node_modules/playwright/cli.js'),
      'test',
      '--config',
      'playwright.artifact-control.config.ts',
    ],
    {
      cwd: root,
      encoding: 'utf8',
      env: { ...process.env, RCT_E2E_CONTROL_OUTPUT_DIR: output, RCT_E2E_CONTROL_MARKER: marker },
    },
  )
  const leaked = [
    child.stdout ?? '',
    child.stderr ?? '',
    ...files(output).map((path) => readFileSync(path).toString()),
  ].some((value) => value.includes(marker))
  const reportPath = join(output, 'safe-results.json')
  const report = child.status === 0 ? JSON.parse(readFileSync(reportPath, 'utf8')) : null
  const expectedFailureRetained =
    report?.results.filter(
      (row) => row.status === 'failed' && row.expectedStatus === 'failed' && row.errorCount > 0,
    ).length === 2
  const successRetained = report?.results.filter((row) => row.status === 'passed').length === 2
  const titleControl = files(output)
    .filter((path) => path.endsWith('control.json'))
    .some((path) => JSON.parse(readFileSync(path, 'utf8')).originalFillTitleContainsMarker === true)
  // Never forward raw child output, even if this control detects a leak.
  process.stdout.write(
    `${JSON.stringify({
      status: child.status,
      markerAbsent: !leaked,
      originalMetadataLeakReproduced: titleControl,
      expectedFailureRetained,
      successRetained,
      emittedArtifactCount: files(output).length,
      browser: 'NOT_STARTED',
    })}\n`,
  )
  if (child.status !== 0 || leaked || !titleControl || !expectedFailureRetained || !successRetained)
    process.exitCode = 1
} finally {
  rmSync(output, { recursive: true, force: true })
}
