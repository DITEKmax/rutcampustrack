/** Actual Chromium ordering controls for the narrow Today request observer. */
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { createServer } from 'node:http'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
const require = createRequire(import.meta.url)
const { chromium } = require('@playwright/test')
const { requireOrImport } = require(
  join(dirname(require.resolve('playwright/package.json')), 'lib/transform/transform.js'),
)
const { todayAfterAction } = await requireOrImport(
  join(dirname(fileURLToPath(import.meta.url)), 'student-today-response.ts'),
)
let old
let sawOld
const server = createServer((req, res) => {
  if (req.url.startsWith('/api/v1/student/today?old')) {
    old = res
    sawOld()
    return
  }
  res.writeHead(200, { 'content-type': 'text/html' })
  res.end('<button onclick="void fetch(\'/api/v1/student/today\')">refresh</button>')
})
await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
const origin = `http://127.0.0.1:${server.address().port}`
const browser = await chromium.launch({ headless: true })
async function disposedToday(page, action, intercepted, timeout) {
  const events = ['request', 'response', 'requestfailed', 'close']
  const before = events.map((event) => page.listenerCount(event))
  const nativeSet = globalThis.setTimeout,
    nativeClear = globalThis.clearTimeout
  const timers = new Set()
  globalThis.setTimeout = (callback, delay, ...args) => {
    let timer
    timer = nativeSet(() => {
      timers.delete(timer)
      callback(...args)
    }, delay)
    if (delay === timeout) timers.add(timer)
    return timer
  }
  globalThis.clearTimeout = (timer) => {
    timers.delete(timer)
    return nativeClear(timer)
  }
  try {
    return await todayAfterAction(page, action, intercepted, timeout)
  } finally {
    globalThis.setTimeout = nativeSet
    globalThis.clearTimeout = nativeClear
    assert.equal(timers.size, 0, 'Every observer deadline is disposed')
    assert.deepEqual(
      events.map((event) => page.listenerCount(event)),
      before,
      'Every observer listener is disposed',
    )
  }
}

const facts = []
try {
  for (const [kind, status] of [
    ['old', 503],
    ['fixed', 503],
    ['fixed-wrong', 200],
  ]) {
    const page = await browser.newPage()
    await page.goto(origin)
    const started = new Promise((resolve) => {
      sawOld = resolve
    })
    await page.evaluate(() => {
      void fetch('/api/v1/student/today?old=1')
    })
    await started
    let intercepted
    await page.route(origin + '/api/v1/student/today', async (route) => {
      intercepted = route.request()
      const delivered = page.waitForResponse((r) => new URL(r.url()).searchParams.has('old'))
      old.writeHead(200, { 'content-type': 'application/json' })
      old.end('{}')
      await delivered
      await new Promise((resolve) => setTimeout(resolve, 80))
      await route.fulfill({ status, contentType: 'application/json', body: '{}' })
    })
    const response =
      kind === 'old'
        ? (
            await Promise.all([
              page.waitForResponse(
                (r) =>
                  new URL(r.url()).pathname === '/api/v1/student/today' &&
                  r.request().method() === 'GET',
              ),
              page.getByRole('button').click(),
            ])
          )[0]
        : await disposedToday(
            page,
            () => page.getByRole('button').click(),
            () => intercepted,
            2000,
          )
    const passed = response.status() === 503
    assert.equal(passed, kind === 'fixed')
    if (kind !== 'old') assert.equal(response.request(), intercepted)
    facts.push({ kind, status: response.status(), passed })
    await page.close()
  }
  for (const kind of [
    'wrong-method',
    'wrong-path',
    'missing-interception',
    'action-failure',
    'request-failure',
    'page-close',
  ]) {
    const page = await browser.newPage()
    await page.goto(origin)
    await page.route('**/api/**', (route) =>
      kind === 'request-failure' ? route.abort() : route.fulfill({ status: 503, body: '{}' }),
    )
    const before = page.listenerCount('request')
    let failed = false
    try {
      await disposedToday(
        page,
        async () => {
          if (kind === 'action-failure') throw new Error('CONTROL_ACTION_FAILURE')
          if (kind === 'page-close') {
            await page.close()
            return
          }
          await page.evaluate((kind) => {
            void fetch(kind === 'wrong-path' ? '/api/other' : '/api/v1/student/today', {
              method: kind === 'wrong-method' ? 'POST' : 'GET',
            })
          }, kind)
        },
        kind === 'missing-interception' ? () => undefined : undefined,
        250,
      )
    } catch {
      failed = true
    }
    assert.equal(failed, true)
    assert.equal(page.listenerCount('request'), before)
    facts.push({ kind, rejected: true })
    await page.close()
  }
  for (const kind of [
    'held-deadline',
    'held-close',
    'response-held-deadline',
    'response-held-close',
  ]) {
    const page = await browser.newPage()
    await page.goto(origin)
    await page.route('**/api/**', (route) => route.fulfill({ status: 503, body: '{}' }))
    let guard
    const external = new Promise((resolve) => {
      guard = setTimeout(() => resolve('HUNG'), 1200)
    })
    const observed = disposedToday(
      page,
      () => {
        if (kind.startsWith('response')) {
          if (kind.endsWith('close'))
            void page
              .waitForResponse((r) => new URL(r.url()).pathname === '/api/v1/student/today')
              .then(() => page.close())
          void page
            .evaluate(() => {
              void fetch('/api/v1/student/today')
            })
            .catch(() => {})
        } else if (kind.endsWith('close'))
          setTimeout(() => {
            void page.close()
          }, 40)
        return new Promise(() => {})
      },
      undefined,
      250,
    ).then(
      () => 'RESOLVED',
      () => 'CANCELLED',
    )
    try {
      assert.equal(await Promise.race([observed, external]), 'CANCELLED')
    } finally {
      clearTimeout(guard)
      await page.close()
    }
    facts.push({ kind, cancelled: true, listenersAndTimerDisposed: true })
  }
  console.log(JSON.stringify({ status: 'PASS', realChromium: true, facts }))
} finally {
  await browser.close()
  await new Promise((resolve) => server.close(resolve))
}
