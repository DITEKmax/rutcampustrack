import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { writeFile, access } from 'node:fs/promises'
import { setTimeout } from 'node:timers/promises'
import { dirname, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const root = resolve(import.meta.dirname, '../..')
const requireFrontend = createRequire(resolve(root, 'frontends/mobile-core/package.json'))
const requireE2e = createRequire(resolve(root, 'tests/e2e/package.json'))
const { chromium } = requireE2e('@playwright/test')
const { createServer } = await import(
  pathToFileURL(resolve(root, 'frontends/node_modules/vite/dist/node/index.js'))
)
const [origin, key, control] = process.argv.slice(2)
assert.equal(process.env.RCT_OBSERVABILITY_APPS_RUNTIME, 'isolated-ci')
assert.equal(process.env.GITHUB_ACTIONS, 'true')
const server = await createServer({
  root: resolve(root, 'frontends'),
  configFile: false,
  logLevel: 'silent',
  resolve: {
    alias: { vue: requireFrontend.resolve('vue/dist/vue.esm-bundler.js'),
      '@sentry/vue': dirname(requireFrontend.resolve('@sentry/vue/package.json')) },
  },
  optimizeDeps: { include: ['vue'] },
  plugins: [
    {
      name: 'observer-fixture',
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (!['/app/observer-fixture', '/mini-app/observer-fixture'].includes(req.url))
            return next()
          const address = server.httpServer.address()
          const dsn = `http://${key}@127.0.0.1:${address.port}/telemetry/errors/1`
          const config = JSON.stringify({ dsn, release: process.env.GITHUB_SHA })
          res.setHeader('Content-Type', 'text/html')
          res.end(
            `<html><body><div id="app"></div><script>window.__RCT_OBSERVER_FIXTURE__=${config}</script><script type="module" src="/@fs/${root}/infra/observability-apps/vue-fixture.ts"></script></body></html>`,
          )
        })
      },
    },
  ],
  server: {
    host: '127.0.0.1',
    port: 0,
    fs: { allow: [root] },
    proxy: { '/telemetry/errors/': { target: origin, changeOrigin: true } },
  },
})
let browser
try {
  await server.listen()
  const fixtureOrigin = `http://127.0.0.1:${server.httpServer.address().port}`
  browser = await chromium.launch({ headless: true })
  const denied = await browser.newContext()
  assert.equal((await denied.request.get(origin + '/')).status(), 401)
  assert.equal((await denied.request.get(origin + '/api/services')).status(), 401)
  await denied.close()
  const operator = await browser.newContext({
    httpCredentials: { username: 'operator', password: 'synthetic-operator-only' },
  })
  const home = await operator.newPage()
  assert.equal((await home.goto(origin)).status(), 200)
  for (const link of ['/glitchtip/', '/pyroscope/', 'https://ruttrack.site/grafana/']) {
    await home.locator(`a[href="${link}"]`).first().waitFor({ state: 'visible', timeout: 30000 })
  }
  assert.equal((await operator.request.get(origin + '/glitchtip/')).status(), 200)
  assert.equal((await operator.request.get(origin + '/pyroscope/')).status(), 200)
  await operator.close()
  const context = await browser.newContext()
  const page = await context.newPage()
  let bootHintWritten = false
  page.on('pageerror', (error) => {
    if (bootHintWritten || error.message === 'synthetic-private@example.invalid') return
    const message = typeof error.message === 'string' ? error.message : ''
    let hint = 'JS_UNKNOWN'
    if (message === 'process is not defined') hint = 'PROCESS_GLOBAL'
    else if (/^__VUE_(?:OPTIONS_API|PROD_DEVTOOLS|PROD_HYDRATION_MISMATCH_DETAILS)__ is not defined$/.test(message)) hint = 'VUE_FEATURE_FLAG'
    else if (message.includes('does not provide an export named')) hint = 'MODULE_EXPORT'
    else if (message.startsWith('Failed to fetch dynamically imported module')) hint = 'DYNAMIC_IMPORT'
    else if (error.name === 'ReferenceError') hint = 'JS_REFERENCE'
    else if (error.name === 'SyntaxError') hint = 'JS_SYNTAX'
    else if (error.name === 'TypeError') hint = 'JS_TYPE'
    else if (error.name === 'Error') hint = 'JS_ERROR'
    bootHintWritten = true
    console.error('RCT_BROWSER_BOOT:' + hint)
  })
  page.on('response', (response) => {
    if (bootHintWritten || response.request().resourceType() !== 'script' ||
        !response.url().startsWith(fixtureOrigin + '/') || response.status() < 400) return
    const status = [403, 404, 500, 504].includes(response.status()) ? response.status() : 'OTHER'
    bootHintWritten = true
    console.error('RCT_BROWSER_BOOT:SCRIPT_' + status)
  })
  const sent = []
  page.on('request', (request) => {
    if (request.url().includes('/telemetry/errors/api/1/envelope/'))
      sent.push(request.postData() ?? '')
  })
  // Both platform bootstrap paths send through the same-origin, finite nginx route.
  for (const path of ['/app/observer-fixture', '/mini-app/observer-fixture']) {
    await page.goto(fixtureOrigin + path)
    await page.locator('#operation').click()
    await page.locator('#operation').click()
    await page.waitForFunction(() => document.querySelector('#completed')?.textContent === '2')
    assert.equal(await page.evaluate(() => window.__RCT_OBSERVER_FLUSH__()), true)
  }
  assert.equal(sent.length, 4)
  assert.ok(sent.every((body) => !body.includes('synthetic-private@example.invalid')))
  assert.ok(
    sent.every((body) => body.includes('TypeError') && body.includes(process.env.GITHUB_SHA)),
  )
  await writeFile(
    resolve(control, 'browser-ready.json'),
    JSON.stringify({ result: 'PASS', browser_events: sent.length }),
  )
  const deadline = Date.now() + 240000
  while (true) {
    try {
      await access(resolve(control, 'outage'))
      break
    } catch {}
    assert.ok(Date.now() < deadline, 'Outage coordination timed out')
    await setTimeout(250)
  }
  await page.locator('#operation').click()
  await page.waitForFunction(() => document.querySelector('#completed')?.textContent === '3')
  await writeFile(
    resolve(control, 'browser.json'),
    JSON.stringify({
      result: 'PASS',
      browser_events: sent.length,
      platforms: ['pwa', 'tma'],
      private_ui_auth: 'PASS',
      useful_links: 'PASS',
      exported_marker_absent: true,
      telemetry_outage_business_operation: 'PASS',
    }),
  )
} finally {
  if (browser) await browser.close()
  await server.close()
}
