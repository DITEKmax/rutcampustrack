import fs from 'node:fs/promises'
import path from 'node:path'
import crypto from 'node:crypto'
import { fileURLToPath } from 'node:url'

const ownRoot = path.dirname(fileURLToPath(import.meta.url))
const worktreeRoot = path.resolve(ownRoot, '../../..')
const snapshotRoot = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/homework-combined-review-source'
const manifestPath = path.join(snapshotRoot, 'manifest.json')
const ownedPaths = new Set([
  'frontends/mobile-core/src/features/homework/HomeworkScreen.vue',
  'frontends/mobile-core/src/features/homework/homework-screen.pcss',
])

async function sha256(file) {
  const content = await fs.readFile(file)
  return crypto.createHash('sha256').update(content).digest('hex').toUpperCase()
}

const manifest = JSON.parse(await fs.readFile(manifestPath, 'utf8'))
const paths = await Promise.all(manifest.map(async (entry) => {
  const currentPath = path.join(worktreeRoot, entry.path)
  const currentSha256 = await sha256(currentPath)
  const owned = ownedPaths.has(entry.path)
  return {
    path: entry.path,
    owned,
    snapshotSha256: entry.snapshotSha256,
    currentSha256,
    status: owned
      ? currentSha256 === entry.snapshotSha256 ? 'UNEXPECTED_UNCHANGED' : 'CHANGED'
      : currentSha256 === entry.snapshotSha256 ? 'UNCHANGED' : 'UNEXPECTED_CHANGE',
  }
}))

const changed = paths.filter((entry) => entry.status === 'CHANGED')
const unchanged = paths.filter((entry) => entry.status === 'UNCHANGED')
const unexpected = paths.filter((entry) => entry.status.startsWith('UNEXPECTED'))
if (changed.length !== 2 || unchanged.length !== 23 || unexpected.length > 0) {
  throw new Error(`Frozen25 scope mismatch: changed=${changed.length}, unchanged=${unchanged.length}, unexpected=${unexpected.length}`)
}

const evidence = {
  generatedAt: new Date().toISOString(),
  baselineRevision: 'd3c31acb8cce53791a4981e5858a37d44fdc9a0e',
  manifestPath,
  manifestEntries: paths.length,
  ownedPaths: [...ownedPaths],
  changedCount: changed.length,
  unchangedCount: unchanged.length,
  unexpectedCount: unexpected.length,
  paths,
}
await fs.writeFile(path.join(ownRoot, 'scope-evidence.json'), `${JSON.stringify(evidence, null, 2)}\n`, 'utf8')
console.log(JSON.stringify({ changed: changed.map((entry) => entry.path), unchangedCount: unchanged.length, unexpectedCount: unexpected.length }, null, 2))
