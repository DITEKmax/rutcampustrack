import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { resolve, extname } from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const git = (args) => run('git', args, { capture: true }).stdout
const tracked = git(['ls-files', '-z']).split('\0').filter(Boolean)
const modes = new Set(['inventory', 'prettier', 'java', 'python', 'sql', 'all'])
const mode = process.argv[2] ?? 'inventory'
const write = process.argv.includes('--write')
if (!modes.has(mode) || process.argv.slice(3).some((arg) => !['--write', '--check'].includes(arg))) {
  throw new Error('Usage: node scripts/format-authored.mjs inventory|prettier|java|python|sql|all [--check|--write]')
}

export function classify(path) {
  const parts = path.split('/')
  if (parts.some((part) => ['node_modules', 'vendor', 'build', 'target', 'coverage', 'test-results', 'playwright-report'].includes(part))) {
    return { path, reason: 'generated build/vendor/artifact' }
  }
  if (parts.includes('dist') && !path.startsWith('frontends/landing/dist/')) {
    return { path, reason: 'generated dist; landing/dist is canonical author source' }
  }
  if (path.startsWith('docs/archive/') || path.startsWith('docs/research/') || path.startsWith('.agent/')) {
    return { path, reason: 'byte-exact historical/source/evidence copy' }
  }
  if (parts.includes('generated') || /_pb2(?:_grpc)?\.py$/.test(path)) {
    return { path, reason: 'generated API/schema/protobuf' }
  }
  if (parts.some((part) => ['fixtures', 'snapshots', '__snapshots__'].includes(part)) || path.endsWith('.snap')) {
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
  if (['.vue', '.ts', '.js', '.mjs', '.cjs', '.html', '.pcss', '.css', '.yml', '.yaml'].includes(extension)) {
    // Product/source manifests and imported canonical documents have identity
    // checks of their own; a generic formatter cannot change their bytes.
    if (path.startsWith('docs/')) return { path, reason: 'documentation/source registry; separate owner review' }
    return { path, tool: 'prettier' }
  }
  if (extension === '.json' && /(?:^|\/)(?:package|tsconfig[^/]*|angular|manifest[^/]*)\.json$/.test(path)) {
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
  const baselineBytes = new Map(migrations.map(({ path }) => [path, readFileSync(resolve(root, path))]))
  for (const tool of mode === 'all' ? ['prettier', 'java', 'python', 'sql'] : [mode]) {
    const files = inventory.filter((entry) => entry.tool === tool).map((entry) => entry.path)
    if (!files.length) continue
    if (tool === 'prettier') {
      run(process.execPath, [resolve(root, 'frontends/node_modules/prettier/bin/prettier.cjs'), write ? '--write' : '--check', ...files])
    } else if (tool === 'java') {
      run(resolve(root, 'gradlew'), [write ? 'formatJava' : 'formatJavaCheck', '--no-daemon'])
    } else if (tool === 'python') {
      assertVersion('ruff', ['--version'], /\b0\.11\.13\b/)
      run('ruff', ['format', ...(write ? [] : ['--check']), ...files])
    } else if (tool === 'sql') {
      assertVersion('pg_format', ['--version'], /\b5\.6\b/)
      for (const path of files) {
        const formatted = run('pg_format', ['--spaces', '4', path], { capture: true }).stdout
        const original = readFileSync(resolve(root, path), 'utf8')
        if (original === formatted) continue
        if (!write) throw new Error(`SQL format differs: ${path}`)
        run('pg_format', ['--spaces', '4', '--inplace', path])
      }
    }
  }
  for (const [path, original] of baselineBytes) {
    if (!original.equals(readFileSync(resolve(root, path)))) throw new Error(`Historical migration was rewritten: ${path}`)
  }
}

function assertVersion(command, args, expected) {
  const result = run(command, args, { capture: true })
  if (!expected.test(result.stdout + result.stderr)) throw new Error(`Unexpected formatter version: ${command}`)
}

function run(command, args, { capture = false } = {}) {
  const result = spawnSync(command, args, { cwd: root, encoding: 'utf8', stdio: capture ? 'pipe' : 'inherit' })
  if (result.error) throw result.error
  if (result.status !== 0) throw new Error(`Command failed (${result.status}): ${command}`)
  return result
}
