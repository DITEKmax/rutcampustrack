import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { isAbsolute, relative, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const distDirectory = resolve(process.argv[2] ?? new URL('../dist', import.meta.url).pathname)
const chromePath = process.env.RCT_CHROME_PATH ?? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const playwrightModule = process.env.RCT_PLAYWRIGHT_MODULE
if (!playwrightModule) throw new Error('RCT_PLAYWRIGHT_MODULE must point to playwright/index.mjs')
const { chromium } = await import(pathToFileURL(playwrightModule).href)
const mimeTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.webmanifest': 'application/manifest+json',
  '.woff2': 'font/woff2',
}

function contentType(fileName) {
  const extension = fileName.slice(fileName.lastIndexOf('.'))
  return mimeTypes[extension] ?? 'application/octet-stream'
}

function serveDist() {
  const server = createServer(async (request, response) => {
    const pathname = decodeURIComponent(new URL(request.url ?? '/', 'http://localhost').pathname)
    const requested = pathname === '/' ? 'index.html' : pathname.slice(1)
    const target = resolve(distDirectory, requested)
    const outsideDist = relative(distDirectory, target)
    if (outsideDist.startsWith('..') || isAbsolute(outsideDist)) {
      response.writeHead(403).end()
      return
    }
    try {
      const contents = await readFile(target)
      response.writeHead(200, { 'Content-Type': contentType(target), 'Cache-Control': 'no-store' })
      response.end(contents)
    } catch (error) {
      const status = error && typeof error === 'object' && 'code' in error && error.code === 'ENOENT' ? 404 : 500
      response.writeHead(status).end()
    }
  })
  return new Promise((resolveServer, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => resolveServer(server))
  })
}

function closeServer(server) {
  return new Promise((resolveClose, reject) => server.close((error) => error ? reject(error) : resolveClose()))
}

function snapshotDiagnostics(page) {
  return page.evaluate(async () => {
    const registration = await navigator.serviceWorker.getRegistration()
    const controller = navigator.serviceWorker.controller
    const cacheNames = await caches.keys()
    const cache = cacheNames.includes('rct-student-pwa-v3') ? await caches.open('rct-student-pwa-v3') : null
    const assetSource = await fetch('/sw-assets.js', { cache: 'no-store' }).then((response) => response.text())
    const serialized = assetSource.match(/=\s*(\[[\s\S]*\]);?\s*$/)?.[1]
    const assets = serialized ? JSON.parse(serialized) : []
    const cached = cache ? await Promise.all(assets.map((asset) => cache.match(asset))) : []
    return {
      registrationState: registration?.active?.state ?? null,
      controllerState: controller?.state ?? null,
      cacheNames,
      precacheCoverage: `${cached.filter(Boolean).length}/${assets.length}`,
    }
  })
}

let server
let browser
let page
let phase = 'startup'
let online = null
let offline = null
let result = null
const consoleErrors = []
const failedRequests = []

try {
  server = await serveDist()
  const address = server.address()
  if (!address || typeof address === 'string') throw new Error('Static server did not bind a TCP port')
  const url = `http://localhost:${address.port}/?fixtureToday=default&fixtureDiagnostics=true`
  const taskOrigin = new URL(url).origin
  browser = await chromium.launch({ executablePath: chromePath, headless: true })
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
  page = await context.newPage()
  page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()) })
  page.on('requestfailed', (request) => {
    if (new URL(request.url()).origin === taskOrigin) failedRequests.push({ url: request.url(), failure: request.failure()?.errorText ?? 'unknown' })
  })

  phase = 'online bootstrap'
  await page.goto(url, { waitUntil: 'networkidle' })
  await page.waitForSelector('.today-list')
  await page.evaluate(() => navigator.serviceWorker.ready)
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null)
  online = await snapshotDiagnostics(page)
  if (online.precacheCoverage !== '7/7') throw new Error(`Unexpected online precache coverage: ${online.precacheCoverage}`)

  phase = 'offline shell reload'
  await closeServer(server)
  await context.setOffline(true)
  await page.reload({ waitUntil: 'domcontentloaded', timeout: 15_000 })
  await page.waitForSelector('.today-list', { timeout: 10_000 })
  offline = await page.evaluate(() => ({
    appChildren: document.querySelector('#app')?.childElementCount ?? 0,
    hasOfflineBadge: document.body.innerText.includes('Офлайн'),
    hasTodayRows: document.body.innerText.includes('Пары на сегодня'),
  }))
  await page.screenshot({ path: resolve(distDirectory, 'offline-shell-check.png'), fullPage: true })
  result = { status: offline.appChildren > 0 && offline.hasOfflineBadge && offline.hasTodayRows ? 'PASS' : 'FAIL', phase, online, offline, consoleErrors, failedRequests }
  if (result.status !== 'PASS') process.exitCode = 1
} catch (error) {
  let bodyText = null
  let screenshot = null
  if (page) {
    bodyText = await page.locator('body').innerText().catch(() => null)
    screenshot = resolve(distDirectory, 'offline-shell-failure.png')
    await page.screenshot({ path: screenshot, fullPage: true }).catch(() => { screenshot = null })
  }
  result = {
    status: 'FAIL',
    phase,
    error: error instanceof Error ? error.message : 'unknown',
    online,
    offline,
    bodyText: bodyText?.slice(0, 4_000) ?? null,
    screenshot,
    consoleErrors,
    failedRequests,
  }
  process.exitCode = 1
} finally {
  if (server?.listening) await closeServer(server)
  await browser?.close()
  if (result) process.stdout.write(`${JSON.stringify(result)}\n`)
}
