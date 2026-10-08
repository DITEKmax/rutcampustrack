import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import { test } from 'node:test'
import assert from 'node:assert/strict'
const source = readFileSync(new URL('../public/sw.js', import.meta.url), 'utf8')
function worker(scope = 'https://campus.example/app/') {
  const handlers = new Map()
  const keys = new Set([
    'rct-student-pwa-v2-old', 'rct-student-pwa-v3-__RCT_PWA_RELEASE__',
    'headman-api-cache-v1', `workbox-precache-v2-${scope}`,
    'workbox-precache-v2-https://campus.example/other/', 'unrelated-app-cache',
  ])
  const retired = []
  let finishDeletion
  const pendingDeletion = new Promise(resolve => { finishDeletion = resolve })
  let claimed = false
  runInNewContext(source, {
    URL, importScripts() {}, caches: {
      keys: async () => [...keys],
      delete: async key => { await pendingDeletion; keys.delete(key); retired.push(key); return true },
    },
    self: {
      location: new URL(`${scope}sw.js`), registration: { scope },
      clients: { claim: async () => { claimed = true } },
      addEventListener: (type, handler) => handlers.set(type, handler),
    },
  })
  return { handlers, keys, retired, finishDeletion, claimed: () => claimed }
}
test('legacy PWA update clears only its retired caches before claiming existing clients', async () => {
  const w = worker()
  let pending
  w.handlers.get('activate')({ waitUntil(value) { pending = value } })
  await Promise.resolve()
  assert.equal(w.claimed(), false)
  w.finishDeletion()
  await pending
  assert.equal(w.claimed(), true)
  assert.deepEqual(w.retired.sort(), ['headman-api-cache-v1', 'rct-student-pwa-v2-old', 'workbox-precache-v2-https://campus.example/app/'].sort())
  assert.ok(w.keys.has('rct-student-pwa-v3-__RCT_PWA_RELEASE__'))
  assert.ok(w.keys.has('workbox-precache-v2-https://campus.example/other/'))
  assert.ok(w.keys.has('unrelated-app-cache'))
})
test('isolated preview activation preserves legacy production-scope API and Workbox caches', async () => {
  const w = worker('https://campus.example/preview/')
  let pending
  w.handlers.get('activate')({ waitUntil(value) { pending = value } })
  w.finishDeletion(); await pending
  assert.ok(w.keys.has('headman-api-cache-v1'))
  assert.ok(w.keys.has('workbox-precache-v2-https://campus.example/preview/'))
})
test('new worker never answers authenticated API requests from any legacy cache', () => {
  const w = worker()
  let responded = false
  w.handlers.get('fetch')({ request: { url: 'https://campus.example/api/v1/teacher/journal', method: 'GET', mode: 'cors' }, respondWith() { responded = true } })
  assert.equal(responded, false)
})
