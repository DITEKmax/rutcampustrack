import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { extname } from 'node:path'
import { createHash } from 'node:crypto'

const directory = '/workspace/scratch/rct-validation/np01_config/classifier-correction/'
const repository = '/workspace/rutcampustrack/'
const beforeSource = readFileSync(directory + 'before-script.mjs', 'utf8')
const afterSource = readFileSync('/workspace/rct-worktrees/np01-config/scripts/format-authored.mjs', 'utf8')

function load(source) {
  const functionStart = source.indexOf('export function classify')
  const declarationsStart = source.indexOf('const exactReceiptCopies')
  const start = declarationsStart === -1 ? functionStart : declarationsStart
  const declaration = source.slice(start, source.indexOf('\nconst inventory ='))
  return new Function('extname', declaration.replace('export function', 'function') + '\nreturn classify;')(extname)
}

const before = load(beforeSource)
const after = load(afterSource)
const paths = JSON.parse(readFileSync(directory + 'root-tracked-paths.json', 'utf8'))
const copies = JSON.parse(readFileSync(directory + 'exact-source-copy-proofs.json', 'utf8'))
const exactCopies = copies.filter((p) => p.exact_identity_verified).map((p) => p.path)
const registries = [
  'docs/design/packets/JS-STUDENT-01/manifest.json',
  'docs/presentations/manifest.json',
  'docs/validation/cloud-20261011/receipts/manifest-post-hooks.json',
  'docs/validation/cloud-20261011/skills/manifest.json',
]
const expected = new Set([...exactCopies, ...registries])
const original = paths.map(before)
const corrected = paths.map(after)
const differences = []
for (let i = 0; i < paths.length; i++) {
  if (JSON.stringify(original[i]) === JSON.stringify(corrected[i])) continue
  assert.ok(original[i].tool)
  assert.equal(corrected[i].tool, undefined)
  assert.ok(expected.has(paths[i]))
  differences.push({ path: paths[i], before: original[i], after: corrected[i] })
}
assert.deepEqual(new Set(differences.map((p) => p.path)), expected)
assert.equal(expected.size, 37)
const counts = (list) => list.reduce((total, item) => {
  const key = item.tool ?? 'excluded'
  total[key] = (total[key] ?? 0) + 1
  return total
}, {})
const javaTargets = corrected.filter((p) => p.tool === 'java').map((p) => p.path)
const spotless = paths.filter((p) => p.endsWith('.java') &&
  ((p.startsWith('services/') && p.includes('/src/')) || p.startsWith('tests/e2e/backend-clock/')) &&
  !p.includes('/generated/') && !p.includes('/snapshots/'))
assert.deepEqual(javaTargets, spotless)
assert.equal(javaTargets.length, 1581)
assert.equal(corrected.filter((p) => p.tool === 'sql').length, 6)

const controls = [
  ['docs/validation/cloud-20261011/receipts/scenario-sources/build-matrix.py', 'python'],
  ['scripts/generate-bot-proto.py', 'python'],
  ['docs/validation/cloud-20261011/receipts/fingerprint-review/maintained-new-helper.py', 'python'],
  ['docs/validation/cloud-20261011/receipts/map-validation-recheck/source/maintained-helper.java', 'java'],
  ['docs/validation/cloud-20261011/receipts/maintained/package.json', 'prettier'],
  ['tests/e2e/fixtures/artifact-privacy.ts', 'prettier'],
  ['tests/e2e/fixtures/mobile-dataset.ts', 'prettier'],
  ['frontends/landing/dist/index.html', 'prettier'],
  ['frontends/mobile-core/src/styles/tokens.pcss', 'prettier'],
  ['frontends/pwa-vue/src/App.vue', 'prettier'],
]
for (const [path, tool] of controls) {
  assert.equal(after(path).tool, tool)
  assert.deepEqual(after(path), before(path))
}
for (const path of [
  'services/academic-service/academic-app/src/main/resources/db/migration/V56__durable_export_jobs.sql',
  'services/academic-service/academic-app/src/main/resources/db/migration/V57__own_bot_credentials.sql',
  'frontends/mobile-core/src/api/generated/auth.ts',
  'services/notification-bot/bot/grpc_client/academic_pb2.py',
  'frontends/mobile-core/fixtures/session.json',
  'services/notification-bot/tests/fixtures/excuse_decided.json',
]) {
  assert.equal(after(path).tool, undefined)
  assert.deepEqual(after(path), before(path))
}
const bytes = new Map(corrected.filter((p) => p.tool).map(({ path }) =>
  [path, createHash('sha256').update(readFileSync(repository + path)).digest('hex')]))
writeFileSync(directory + 'eligible-source-hashes.json', JSON.stringify(Object.fromEntries(bytes), null, 2) + '\n')
writeFileSync(directory + 'before-inventory.json', JSON.stringify(original, null, 2) + '\n')
writeFileSync(directory + 'after-inventory.json', JSON.stringify(corrected, null, 2) + '\n')
writeFileSync(directory + 'changed-dispositions.json', JSON.stringify(differences, null, 2) + '\n')
const proof = {
  status: 'PASS_SOURCE_ONLY_CLASSIFIER',
  rootRevision: '25668e10ffcdcda1848c6fda38429dea7e22a793',
  trackedPaths: paths.length,
  before: counts(original),
  after: counts(corrected),
  exactChangedPaths: differences.length,
  verifiedSourceCopies: exactCopies.length,
  preservedRegistries: registries.length,
  javaExactlyMatchesActualSpotless: true,
  actualCurrentJavaTargets: javaTargets.length,
  historicalC4JavaTargets: 1549,
  sixSqlTargetsPreserved: true,
  maintainedPositiveControls: controls,
  immutableIdentityUnknownAndMaintained: 'docs/validation/cloud-20261011/receipts/scenario-sources/build-matrix.py',
  parserRulesVersionsDriversUnchanged: beforeSource.slice(beforeSource.indexOf('\nconst inventory =')) === afterSource.slice(afterSource.indexOf('\nconst inventory =')),
  massFormatting: 'NOT_RUN',
}
assert.ok(proof.parserRulesVersionsDriversUnchanged)
writeFileSync(directory + 'verification.safe.json', JSON.stringify(proof, null, 2) + '\n')
console.log(JSON.stringify(proof))
