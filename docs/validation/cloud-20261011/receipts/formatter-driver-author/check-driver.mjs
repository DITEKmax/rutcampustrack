import assert from 'node:assert/strict'
import childProcess from 'node:child_process'
import { syncBuiltinESMExports } from 'node:module'
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = '/workspace/rct-worktrees/ci-academic-heap'
const output = '/workspace/scratch/rct-validation/formatter_driver_preservation'
const requested = process.argv[2] ?? 'posix-check'
const listing = { status: 0, stdout: readFileSync(`${output}/own-tracked.paths0`, 'utf8') }
const calls = []
childProcess.spawnSync = (command, args, options) => {
  if (command === 'git') {
    assert.deepEqual(args, ['ls-files', '-z'])
    return listing
  }
  calls.push({ command, args, cwd: options.cwd, shell: options.shell ?? false })
  return { status: 0, stdout: '', stderr: '' }
}
syncBuiltinESMExports()
const windows = requested.startsWith('win32-')
if (windows) Object.defineProperty(process, 'platform', { value: 'win32' })
const write = requested.endsWith('-write')
process.argv = [process.execPath, `${root}/scripts/format-authored.mjs`, 'java', write ? '--write' : '--check']
const { classify } = await import(`file://${root}/scripts/format-authored.mjs`)
assert.equal(calls.length, 1)
const task = write ? 'formatJava' : 'formatJavaCheck'
const normalizedCall = { ...calls[0], cwd: resolve(calls[0].cwd) }
if (windows) {
  assert.deepEqual(normalizedCall, {
    command: 'powershell.exe',
    args: ['-NoProfile', '-NonInteractive', '-Command', `& '${root}/gradlew.bat' '${task}' '--no-daemon'; exit $LASTEXITCODE`],
    cwd: root,
    shell: false,
  })
} else {
  assert.deepEqual(normalizedCall, { command: 'bash', args: [`${root}/gradlew`, task, '--no-daemon'], cwd: root, shell: false })
}
const paths = ['current-client-artifacts/manifest.json', 'export-audit-http-author/manifest.safe.json',
  'host-guard-correction-author/manifest.json', 'host-runtime-prepare/manifest.json']
const preserved = paths.map((path) => classify(`docs/validation/cloud-20261011/receipts/${path}`))
assert(preserved.every((entry) => entry.reason === 'preserved source registry/hash receipt; separate byte owner' && !entry.tool))
assert.equal(classify('docs/validation/cloud-20261011/receipts/current-client-artifacts/manifest-authored.json').tool, 'prettier')
assert.equal(classify('docs/validation/cloud-20261011/receipts/current-client-artifacts/maintained.py').tool, 'python')
assert.equal(classify('docs/validation/cloud-20261011/receipts/scenario-sources/build-matrix.py').tool, 'python')
assert.equal(classify('docs/milestones/M05-performance/seed-perf.sql').tool, 'sql')
const rootListing = { status: 0, stdout: readFileSync(`${output}/root-tracked.paths0`, 'utf8') }
const rootInventory = rootListing.stdout.split('\0').filter(Boolean).map(classify)
if (requested === 'posix-check')
  writeFileSync(`${output}/root-candidate-inventory.json`, `${JSON.stringify(rootInventory, null, 2)}\n`)
writeFileSync(`${output}/${requested}.safe.json`, `${JSON.stringify({
  status: 'PURE_ARGV_CLASSIFICATION_PASS_NO_FORMATTER_SPAWN', requested, calls, preserved,
  actualPowerShellRuntime: 'NOT_RUN', actualJavaFormatterRuntime: 'NOT_RUN',
  wrapperMode: (await import('node:fs')).statSync(`${root}/gradlew`).mode & 0o777,
  sourceBytes: readFileSync(`${root}/scripts/format-authored.mjs`).length,
}, null, 2)}\n`)
console.log(`${requested}: pure argv/classification PASS; no formatter or JVM process spawned`)
