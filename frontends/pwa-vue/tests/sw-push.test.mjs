import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import { test } from 'node:test'
import assert from 'node:assert/strict'

const source = readFileSync(new URL('../public/sw.js', import.meta.url), 'utf8')
function worker() {
  const handlers = new Map()
  const notifications = []
  const opened = []
  const sent = []
  const scope = 'https://campus.example/app/'
  const windowClient = { id: 'tab', url: scope, focus: async () => {}, postMessage: (value) => sent.push(value) }
  let windows = []
  let stored = null
  const indexedDB = {
    open() {
      const request = {}
      request.result = {
        close() {},
        transaction() {
          const tx = { objectStore: () => ({
            get: () => { const query = { result: stored }; queueMicrotask(() => tx.oncomplete()); return query },
            put: (value) => { stored = value; queueMicrotask(() => tx.oncomplete()); return {} },
            delete: () => { stored = null; queueMicrotask(() => tx.oncomplete()); return {} },
          }) }
          return tx
        },
      }
      queueMicrotask(() => request.onsuccess())
      return request
    },
  }
  runInNewContext(source, {
    indexedDB, URL, importScripts() {},
    self: {
      location: new URL(`${scope}sw.js`), registration: {
        scope,
        showNotification: async (title, options) => { notifications.push({ title, ...options, close() { this.closed = true } }) },
        getNotifications: async () => notifications,
      },
      clients: { get: async () => windowClient, matchAll: async () => windows, openWindow: async (url) => opened.push(url) },
      addEventListener: (type, handler) => handlers.set(type, handler),
    },
  })
  async function dispatch(type, fields) {
    let pending
    handlers.get(type)({ ...fields, waitUntil: (promise) => { pending = promise } })
    await pending
  }
  return {
    notifications, opened, sent,
    setWindows: () => { windows = [windowClient] },
    bind: (binding) => dispatch('message', { source: { id: 'tab' }, data: { type: 'RCT_PUSH_BIND', binding }, ports: [{ postMessage() {} }] }),
    push: (payload) => dispatch('push', { data: { json: () => payload } }),
    click: (notification) => dispatch('notificationclick', { notification }),
  }
}
const fingerprint = 'a'.repeat(64)
const payload = { title: 'Новое ДЗ', body: 'Проверь задание', recipientUserId: '1', subscriptionFingerprint: fingerprint, data: { url: 'https://hostile.example/' } }

test('worker fails closed for malformed, unbound, old-account and retired-endpoint payloads', async () => {
  const w = worker()
  await w.push(payload)
  assert.equal(w.notifications.length, 0)
  await w.bind({ userId: '1', fingerprint })
  await w.push({ title: 'Legacy' })
  await w.push({ ...payload, recipientUserId: '2' })
  await w.push({ ...payload, subscriptionFingerprint: 'b'.repeat(64) })
  assert.equal(w.notifications.length, 0)
  await w.push(payload)
  assert.equal(w.notifications.length, 1)
  assert.equal(w.notifications[0].body, payload.body)
  assert.equal('url' in w.notifications[0].data, false)
})

test('disable closes system notifications and an old click cannot open another account', async () => {
  const w = worker()
  await w.bind({ userId: '1', fingerprint })
  await w.push(payload)
  const old = w.notifications[0]
  await w.bind(null)
  assert.equal(old.closed, true)
  await w.bind({ userId: '2', fingerprint: 'b'.repeat(64) })
  await w.click(old)
  assert.equal(w.opened.length, 0)
  assert.equal(w.sent.length, 0)
})

test('click opens the existing same-origin history entry and ignores arbitrary payload URLs', async () => {
  const w = worker()
  await w.bind({ userId: '1', fingerprint })
  await w.push(payload)
  await w.click(w.notifications[0])
  assert.deepEqual(w.opened, ['https://campus.example/app/?pushNotifications=1&pushOwner=1'])
  w.setWindows()
  await w.click(w.notifications[0])
  assert.equal(w.opened.length, 1)
  assert.equal(w.sent[0].type, 'RCT_PUSH_OPEN')
  assert.equal(w.sent[0].userId, '1')
})
