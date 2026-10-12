import { spawnSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { resolve, extname } from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const git = (args) => run('git', args, { capture: true }).stdout
const tracked = git(['ls-files', '-z']).split('\0').filter(Boolean)
const modes = new Set(['inventory', 'prettier', 'java', 'python', 'sql', 'all'])
const mode = process.argv[2] ?? 'inventory'
const write = process.argv.includes('--write')
if (
  !modes.has(mode) ||
  process.argv.slice(3).some((arg) => !['--write', '--check'].includes(arg))
) {
  throw new Error(
    'Usage: node scripts/format-authored.mjs inventory|prettier|java|python|sql|all [--check|--write]',
  )
}

// Exact source/evidence copies verified against their immutable originals and hash manifests.
// Maintained author files in the same directories remain eligible for formatting.
const exactReceiptCopies = new Set(
  [
    'ci-artifact-reconcile-636/assess.py',
    'ci-artifact-reconcile-636/reconcile.py',
    'ci-artifact-reconcile-636/source-SubjectListDataIT-actual-checkout.java',
    'ci-generator-recheck/audit.py',
    'ci-generator-recheck/clock_audit.py',
    'clock-runtime-recheck/audit.py',
    'export-ebsr-recheck/check-runtime-receipts.py',
    'export-ebsr-recheck/final-check.py',
    'export-ebsr-recheck/recheck.py',
    'export-merged-repository-review/check.py',
    'fingerprint-review/audit.py',
    'fingerprint-review/author-originals/sensitivity.py',
    'fingerprint-review/author-originals/verify.py',
    'format-freeze-prep/inventory.py',
    'map-validation-recheck/audit_evidence.py',
    'map-validation-recheck/original-evidence/run-map-check.py',
    'map-validation-recheck/source/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java',
    'map-validation-recheck/source/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapAdminServiceTest.java',
    'map-validation-recheck/source/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapFileBoundaryIT.java',
    'map-validation-recheck/source/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapFileFixture.java',
    'map-validation-recheck/source/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadRepositoryIT.java',
    'map-validation-review/audit_evidence.py',
    'matrix-evidence-reconciliation/dispositions.py',
    'matrix-evidence-reconciliation/queue.py',
    'matrix-evidence-reconciliation/reconcile.py',
    'notification-recovery-review/collect-review-inputs.py',
    'np01-config-author/verify-config.py',
    'np01-source-resolution/source-ledger.py',
    'ntr-seal-review/audit.py',
    'scenario-sources/build-matrix.py',
    'security-fingerprint-author/sensitivity.py',
    'security-fingerprint-author/verify.py',
    'source-manifest-recovery/check-dependencies.py',
    'source-manifest-recovery/propose-restoration.py',
  ].map((path) => `docs/validation/cloud-20261011/receipts/${path}`),
)

// Imported source registries and machine hash receipts have separate byte owners.
const exactSourceRegistries = new Set([
  'docs/design/packets/JS-STUDENT-01/manifest.json',
  'docs/presentations/manifest.json',
  'docs/validation/cloud-20261011/receipts/manifest-post-hooks.json',
  'docs/validation/cloud-20261011/receipts/current-client-artifacts/manifest.json',
  'docs/validation/cloud-20261011/receipts/export-audit-http-author/manifest.safe.json',
  'docs/validation/cloud-20261011/receipts/host-guard-correction-author/manifest.json',
  'docs/validation/cloud-20261011/receipts/host-runtime-prepare/manifest.json',
  'docs/validation/cloud-20261011/skills/manifest.json',
  'docs/validation/cloud-20261011/receipts/capacity-premutation-aliases/manifest.json',
  'docs/validation/cloud-20261011/receipts/ci731-native-reconciliation/manifest.json',
  'docs/validation/cloud-20261011/receipts/current-artifact-correction-author/manifest.json',
  'docs/validation/cloud-20261011/receipts/current-artifact-correction-review/manifest.json',
  'docs/validation/cloud-20261011/receipts/current-artifact-correction-v2-author/manifest.json',
  'docs/validation/cloud-20261011/receipts/current-artifact-correction-v2-review/manifest.json',
  'docs/validation/cloud-20261011/receipts/current-artifact-independent-review/manifest.json',
  'docs/validation/cloud-20261011/receipts/host-capacity-correction-author/manifest.json',
  'docs/validation/cloud-20261011/receipts/host-capacity-correction-review/manifest.json',
  'docs/validation/cloud-20261011/receipts/host-capacity-independent-review/manifest.json',
  'docs/validation/cloud-20261011/receipts/host-capacity-plan/manifest.json',
])

export function classify(path) {
  // Owner decision D010 preserves the entire Angular frontend unchanged.
  if (path.startsWith('frontends/web-panel/'))
    return { path, reason: 'Angular frontend excluded by owner decision D010; preserve bytes' }
  if (exactReceiptCopies.has(path))
    return { path, reason: 'immutable verification evidence/source copy; preserve exact bytes' }
  if (exactSourceRegistries.has(path))
    return { path, reason: 'preserved source registry/hash receipt; separate byte owner' }
  const parts = path.split('/')
  if (
    parts.some((part) =>
      [
        'node_modules',
        'vendor',
        'build',
        'target',
        'coverage',
        'test-results',
        'playwright-report',
      ].includes(part),
    )
  ) {
    return { path, reason: 'generated build/vendor/artifact' }
  }
  if (parts.includes('dist') && !path.startsWith('frontends/landing/dist/')) {
    return { path, reason: 'generated dist; landing/dist is canonical author source' }
  }
  if (
    path.startsWith('docs/archive/') ||
    path.startsWith('docs/research/') ||
    path.startsWith('.agent/')
  ) {
    return { path, reason: 'byte-exact historical/source/evidence copy' }
  }
  if (parts.includes('generated') || /_pb2(?:_grpc)?\.py$/.test(path)) {
    return { path, reason: 'generated API/schema/protobuf' }
  }
  const byteExactFixtures = [
    'frontends/mobile-core/fixtures/',
    'services/attendance-service/attendance-app/src/test/resources/fixtures/',
    'services/notification-bot/tests/fixtures/',
  ]
  if (
    byteExactFixtures.some((prefix) => path.startsWith(prefix)) ||
    parts.some((part) => ['snapshots', '__snapshots__'].includes(part)) ||
    path.endsWith('.snap')
  ) {
    return { path, reason: 'intentional fixture/snapshot bytes; explicit review required' }
  }
  if (/\/db\/migration\/V[^/]+\.sql$/.test(path)) {
    return { path, reason: 'historical Flyway versioned SQL; checksum must remain stable' }
  }
  if (/package-lock\.json$|gradle\.lockfile$/.test(path)) {
    return { path, reason: 'dependency lockfile; managed by native dependency tool' }
  }
  const extension = extname(path)
  if (extension === '.java') return { path, tool: 'java' }
  if (extension === '.py') return { path, tool: 'python' }
  if (extension === '.sql') return { path, tool: 'sql' }
  if (
    ['.vue', '.ts', '.js', '.mjs', '.cjs', '.html', '.pcss', '.css', '.yml', '.yaml'].includes(
      extension,
    )
  ) {
    // Product/source manifests and imported canonical documents have identity
    // checks of their own; a generic formatter cannot change their bytes.
    if (path.startsWith('docs/'))
      return { path, reason: 'documentation/source registry; separate owner review' }
    return { path, tool: 'prettier' }
  }
  if (
    extension === '.json' &&
    /(?:^|\/)(?:package|tsconfig[^/]*|angular|manifest[^/]*)\.json$/.test(path)
  ) {
    return { path, tool: 'prettier' }
  }
  return { path, reason: 'binary/machine data or no agreed formatter for this language' }
}

const inventory = tracked.map(classify)
if (mode === 'inventory') {
  process.stdout.write(`${JSON.stringify(inventory, null, 2)}\n`)
} else {
  // Check before and after every formatter, so accidental target widening can
  // never silently rewrite published versioned migrations.
  const migrations = inventory.filter((entry) => entry.reason?.startsWith('historical Flyway'))
  const baselineBytes = new Map(
    migrations.map(({ path }) => [path, readFileSync(resolve(root, path))]),
  )
  for (const tool of mode === 'all' ? ['prettier', 'java', 'python', 'sql'] : [mode]) {
    const files = inventory.filter((entry) => entry.tool === tool).map((entry) => entry.path)
    if (!files.length) continue
    if (tool === 'prettier') {
      const vueFiles = files.filter((path) => path.endsWith('.vue'))
      const otherFiles = files.filter((path) => !path.endsWith('.vue'))
      if (otherFiles.length) {
        run(process.execPath, [
          resolve(root, 'frontends/node_modules/prettier/bin/prettier.cjs'),
          write ? '--write' : '--check',
          ...otherFiles,
        ])
      }
      await formatVueFiles(vueFiles)
    } else if (tool === 'java') {
      const task = write ? 'formatJava' : 'formatJavaCheck'
      if (process.platform === 'win32') {
        const wrapper = resolve(root, 'gradlew.bat').replaceAll("'", "''")
        run('powershell.exe', [
          '-NoProfile',
          '-NonInteractive',
          '-Command',
          `& '${wrapper}' '${task}' '--no-daemon'; exit $LASTEXITCODE`,
        ])
      } else {
        run('bash', [resolve(root, 'gradlew'), task, '--no-daemon'])
      }
    } else if (tool === 'python') {
      assertVersion('ruff', ['--version'], /\b0\.17\.0\b/)
      run('ruff', ['format', ...(write ? [] : ['--check']), ...files])
    } else if (tool === 'sql') {
      assertVersion('pg_format', ['--version'], /\b5\.6\b/)
      for (const path of files) {
        const formatted = run('pg_format', ['--spaces', '4', '--no-extra-line', path], {
          capture: true,
        }).stdout
        const original = readFileSync(resolve(root, path), 'utf8')
        if (original === formatted) continue
        if (!write) throw new Error(`SQL format differs: ${path}`)
        run('pg_format', ['--spaces', '4', '--no-extra-line', '--inplace', path])
      }
    }
  }
  for (const [path, original] of baselineBytes) {
    if (!original.equals(readFileSync(resolve(root, path))))
      throw new Error(`Historical migration was rewritten: ${path}`)
  }
}

async function formatVueFiles(files) {
  if (!files.length) return
  const moduleUrl = (path) => pathToFileURL(resolve(root, 'frontends/node_modules', path)).href
  const prettier = await import(moduleUrl('prettier/index.mjs'))
  const { parse } = await import(moduleUrl('@vue/compiler-sfc/dist/compiler-sfc.cjs.js'))
  const { ESLint } = await import(moduleUrl('eslint/lib/api.js'))
  const eslint = new ESLint({ cwd: resolve(root, 'frontends'), fix: true, cache: false })
  for (const path of files) {
    const filename = resolve(root, path)
    const original = readFileSync(filename, 'utf8')
    const parsed = parse(original, { filename })
    if (parsed.errors.length) throw new Error(`Vue parse failed: ${path}`)
    const { script, scriptSetup, styles } = parsed.descriptor
    const options = await prettier.resolveConfig(filename)
    const blocks = [script, scriptSetup, ...styles].filter((block) => block?.content.trim())
    let formatted = original
    // Vue template layout follows the existing ESLint rules. Prettier formats
    // the script/style blocks without introducing whitespace into template text.
    for (const block of blocks.sort((a, b) => b.loc.start.offset - a.loc.start.offset)) {
      const parser = block.type === 'style' ? 'css' : block.lang === 'ts' ? 'typescript' : 'babel'
      const content = `\n${await prettier.format(block.content, { ...options, parser, filepath: filename })}`
      formatted =
        formatted.slice(0, block.loc.start.offset) + content + formatted.slice(block.loc.end.offset)
    }
    const [result] = await eslint.lintText(formatted, { filePath: filename })
    if (result.errorCount || result.warningCount) {
      throw new Error(`Vue lint failed: ${path}`)
    }
    formatted = result.output ?? formatted
    if (formatted === original) continue
    if (!write) throw new Error(`Vue format differs: ${path}`)
    writeFileSync(filename, formatted)
  }
}

function assertVersion(command, args, expected) {
  const result = run(command, args, { capture: true })
  if (!expected.test(result.stdout + result.stderr))
    throw new Error(`Unexpected formatter version: ${command}`)
}

function run(command, args, { capture = false } = {}) {
  const result = spawnSync(command, args, {
    cwd: root,
    encoding: 'utf8',
    stdio: capture ? 'pipe' : 'inherit',
  })
  if (result.error) throw result.error
  if (result.status !== 0) throw new Error(`Command failed (${result.status}): ${command}`)
  return result
}
