import assert from 'node:assert/strict'
import childProcess from 'node:child_process'
import { registerHooks, syncBuiltinESMExports } from 'node:module'
import { readFileSync, writeFileSync } from 'node:fs'

const output = '/workspace/scratch/rct-validation/formatter_driver_preservation'
const target = 'file:///workspace/rct-worktrees/ci-academic-heap/scripts/format-authored.mjs'
const before = readFileSync(`${output}/format-authored.before.mjs.txt`, 'utf8')
registerHooks({
  load(url, context, next) {
    if (url === target) return { format: 'module', source: before, shortCircuit: true }
    return next(url, context)
  },
})
childProcess.spawnSync = (command, args) => {
  assert.equal(command, 'git')
  assert.deepEqual(args, ['ls-files', '-z'])
  return { status: 0, stdout: readFileSync(`${output}/root-tracked.paths0`, 'utf8'), stderr: '' }
}
syncBuiltinESMExports()
process.argv = [process.execPath, target, 'inventory']
let inventory = ''
const originalWrite = process.stdout.write
process.stdout.write = (chunk) => { inventory += chunk; return true }
await import(target)
process.stdout.write = originalWrite
JSON.parse(inventory)
writeFileSync(`${output}/root-before-inventory.json`, inventory)
console.log('Actual original Node classifier inventory captured from native git snapshot; no formatter child')
