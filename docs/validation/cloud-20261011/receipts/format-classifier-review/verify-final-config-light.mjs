import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { extname } from 'node:path'
import { createHash } from 'node:crypto'

const wt = '/workspace/rct-worktrees/np01-config'
const own = '/workspace/scratch/rct-validation/np01_common_review/'
const inputs = '/workspace/scratch/rct-validation/np01_config/classifier-correction/'
const commit = 'f09f7b2f2321dc4605b019a4fd1b52a0be0d4f10'
const predecessor = '84d56cb0691b28cbe47c46e8dc84ba03b380ac46'
const file = 'scripts/format-authored.mjs'
// Exact frozen Git blobs copied by a separate read-only Python orchestration step.
// Node subprocess creation is unavailable in this executor; classification stays pure.
const old = readFileSync(own + 'classifier-input-before.mjs', 'utf8')
const current = readFileSync(own + 'classifier-input-after.mjs', 'utf8')
const declarations = current.slice(current.indexOf('// Exact source/evidence copies'), current.indexOf('export function classify'))
const additions = "  if (exactReceiptCopies.has(path))\n    return { path, reason: 'immutable verification evidence/source copy; preserve exact bytes' }\n  if (exactSourceRegistries.has(path))\n    return { path, reason: 'preserved source registry/hash receipt; separate byte owner' }\n"
assert.equal(current.replace(declarations, '').replace(additions, ''), old)
const load = (source) => {
  const start = source.includes('const exactReceiptCopies') ? source.indexOf('const exactReceiptCopies') : source.indexOf('export function classify')
  const body = source.slice(start, source.indexOf('\nconst inventory ='))
  return new Function('extname', body.replace('export function', 'function') + '\nreturn classify')(extname)
}
const before = load(old), after = load(current)
const paths = JSON.parse(readFileSync(inputs + 'root-tracked-paths.json'))
const originals = JSON.parse(readFileSync(inputs + 'exact-source-copy-proofs.json')).filter((e) => e.exact_identity_verified)
const preserved = [
  'docs/design/packets/JS-STUDENT-01/manifest.json', 'docs/presentations/manifest.json',
  'docs/validation/cloud-20261011/receipts/manifest-post-hooks.json', 'docs/validation/cloud-20261011/skills/manifest.json',
]
const expected = new Set([...originals.map((e) => e.path), ...preserved])
const diff = paths.filter((p) => JSON.stringify(before(p)) !== JSON.stringify(after(p)))
assert.equal(expected.size, 37)
assert.deepEqual(new Set(diff), expected)
for (const p of diff) { assert.ok(before(p).tool); assert.equal(after(p).tool, undefined) }
const classified = paths.map(after)
const java = classified.filter((e) => e.tool === 'java').map((e) => e.path)
const spotless = paths.filter((p) => p.endsWith('.java') && ((p.startsWith('services/') && p.includes('/src/')) || p.startsWith('tests/e2e/backend-clock/')) && !p.includes('/generated/') && !p.includes('/snapshots/'))
assert.deepEqual(java, spotless)
assert.equal(java.length, 1581)
assert.equal(classified.filter((e) => e.tool === 'sql').length, 6)
const controls = JSON.parse(readFileSync(inputs + 'verification.safe.json')).maintainedPositiveControls
for (const [path, tool] of controls) { assert.equal(after(path).tool, tool); assert.deepEqual(after(path), before(path)) }
for (const path of [
  'services/academic-service/academic-app/src/main/resources/db/migration/V56__durable_export_jobs.sql',
  'services/academic-service/academic-app/src/main/resources/db/migration/V57__bot_credential_message_lifecycle.sql',
  'services/notification-bot/bot/grpc_client/academic_pb2.py',
]) { assert.equal(after(path).tool, undefined); assert.deepEqual(after(path), before(path)) }
const result = {
  status: 'PASS_INDEPENDENT_LIGHT_CLASSIFIER_CHECK', commit, path: file,
  source_sha256: createHash('sha256').update(current).digest('hex'), source_bytes: Buffer.byteLength(current),
  root_snapshot_paths: paths.length, exact_changed_paths: diff, verified_originals: originals.length,
  precise_registry_paths: preserved, java_targets_match_root_Spotless_policy: java.length,
  maintained_positive_controls: controls, sql_targets: 6,
  all_original_declarations_and_driver_bytes_preserved_after_subtracting_only_exact_exclusions: true,
  formatter_runs: 0, limits: 'Source/inventory classification only; 1581 is this root25668 snapshot, not future post-integration inventory or actual formatting success',
}
writeFileSync(own + 'classifier-independent-check.safe.json', JSON.stringify(result, null, 2) + '\n')
console.log(JSON.stringify({ status: result.status, exact_changes: diff.length, java_targets: java.length, sql_targets: 6, positive_controls: controls.length }))
