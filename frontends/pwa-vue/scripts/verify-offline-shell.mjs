import { createServer as createHttpServer } from 'node:http'
import { createServer as createHttpsServer } from 'node:https'
import { readFile } from 'node:fs/promises'
import { isAbsolute, relative, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const distDirectory = resolve(process.argv[2] ?? new URL('../dist', import.meta.url).pathname)
const chromePath = process.env.RCT_CHROME_PATH ?? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const playwrightModule = process.env.RCT_PLAYWRIGHT_MODULE
if (!playwrightModule) throw new Error('RCT_PLAYWRIGHT_MODULE must point to playwright/index.mjs')
const { chromium } = await import(pathToFileURL(playwrightModule).href)
const verifyHost = process.env.RCT_PWA_VERIFY_HOST ?? '127.0.0.1'
const verifyProtocol = process.env.RCT_PWA_VERIFY_PROTOCOL ?? 'http'
const verifyBase = process.env.RCT_PWA_VERIFY_BASE ?? '/'
const screenshotPath = process.env.RCT_PWA_VERIFY_SCREENSHOT ?? resolve(distDirectory, 'offline-shell-check.png')
const certificatePath = process.env.RCT_PWA_VERIFY_CERT
const privateKeyPath = process.env.RCT_PWA_VERIFY_KEY
const verbose = process.env.RCT_PWA_VERIFY_VERBOSE === 'true'
function log(message) { if (verbose) process.stderr.write(`[pwa-verify] ${message}\n`) }
if (verifyProtocol !== 'http' && verifyProtocol !== 'https') throw new Error(`Unsupported verification protocol: ${verifyProtocol}`)
if (!verifyBase.startsWith('/') || !verifyBase.endsWith('/')) throw new Error('RCT_PWA_VERIFY_BASE must begin and end with /')
if (verifyProtocol === 'https' && (!certificatePath || !privateKeyPath)) throw new Error('RCT_PWA_VERIFY_CERT and RCT_PWA_VERIFY_KEY are required for HTTPS verification')
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

async function serveDist() {
  const requestHandler = async (request, response) => {
    const pathname = decodeURIComponent(new URL(request.url ?? '/', `${verifyProtocol}://${verifyHost}`).pathname)
    const pathWithinBase = pathname === verifyBase
      ? ''
      : pathname.startsWith(verifyBase)
        ? pathname.slice(verifyBase.length)
        : null
    if (pathWithinBase === null) {
      response.writeHead(404).end()
      return
    }
    const requested = pathWithinBase === '' ? 'index.html' : pathWithinBase
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
  }
  const server = verifyProtocol === 'https'
    ? createHttpsServer({ key: await readFile(privateKeyPath), cert: await readFile(certificatePath) }, requestHandler)
    : createHttpServer(requestHandler)
  return new Promise((resolveServer, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => resolveServer(server))
  })
}

function closeServer(server) {
  return new Promise((resolveClose, reject) => server.close((error) => error ? reject(error) : resolveClose()))
}

function snapshotDiagnostics(page, basePath) {
  return page.evaluate(async (base) => {
    const registration = await navigator.serviceWorker.getRegistration()
    const controller = navigator.serviceWorker.controller
    const cacheNames = await caches.keys()
    const cache = cacheNames.includes('rct-student-pwa-v3') ? await caches.open('rct-student-pwa-v3') : null
    const baseUrl = new URL(base, window.location.href)
    const assetSource = await fetch(new URL('sw-assets.js', baseUrl), { cache: 'no-store' }).then((response) => response.text())
    const serialized = assetSource.match(/=\s*(\[[\s\S]*\]);?\s*$/)?.[1]
    const assets = serialized ? JSON.parse(serialized) : []
    const cached = cache ? await Promise.all(assets.map((asset) => cache.match(new URL(asset, baseUrl).href))) : []
    return {
      origin: window.location.origin,
      isSecureContext: window.isSecureContext,
      baseScope: baseUrl.href,
      registrationState: registration?.active?.state ?? null,
      controllerState: controller?.state ?? null,
      cacheNames,
      precacheAssets: assets.length,
      precacheCoverage: `${cached.filter(Boolean).length}/${assets.length}`,
    }
  }, basePath)
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
  log(`serve ${verifyProtocol}://${verifyHost} base ${verifyBase}`)
  server = await serveDist()
  const address = server.address()
  if (!address || typeof address === 'string') throw new Error('Static server did not bind a TCP port')
  const url = `${verifyProtocol}://${verifyHost}:${address.port}${verifyBase}?fixtureToday=default&fixtureDiagnostics=true`
  const taskOrigin = new URL(url).origin
  log(`listen ${url}`)
  browser = await chromium.launch({ executablePath: chromePath, headless: true, args: verifyProtocol === 'https' ? ['--ignore-certificate-errors'] : [] })
  log('browser launched')
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, ignoreHTTPSErrors: verifyProtocol === 'https' })
  page = await context.newPage()
  page.setDefaultTimeout(10_000)
  page.setDefaultNavigationTimeout(15_000)
  page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text()) })
  page.on('requestfailed', (request) => {
    if (new URL(request.url()).origin === taskOrigin) failedRequests.push({ url: request.url(), failure: request.failure()?.errorText ?? 'unknown' })
  })

  phase = 'online bootstrap'
  log('goto')
  await page.goto(url, { waitUntil: 'networkidle', timeout: 15_000 })
  log('selector')
  await page.waitForSelector('.today-list', { timeout: 10_000 })
  log('registration')
  await page.waitForFunction(() => navigator.serviceWorker.getRegistration().then((registration) => registration !== undefined), undefined, { timeout: 10_000 })
  log('active')
  await page.waitForFunction(() => navigator.serviceWorker.getRegistration().then((registration) => Boolean(registration?.active)), undefined, { timeout: 10_000 })
  log('controller')
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null, undefined, { timeout: 15_000 })
  log('diagnostics')
  online = await snapshotDiagnostics(page, verifyBase)
  if (!online.isSecureContext) throw new Error(`Verification origin is not a secure context: ${online.origin}`)
  if (online.baseScope !== new URL(verifyBase, url).href) throw new Error(`Unexpected service-worker base scope: ${online.baseScope}`)
  if (online.precacheAssets === 0 || online.precacheCoverage !== `${online.precacheAssets}/${online.precacheAssets}`) throw new Error(`Unexpected online precache coverage: ${online.precacheCoverage}`)

  phase = 'offline shell reload'
  log('stop server and reload offline')
  await closeServer(server)
  await context.setOffline(true)
  await page.reload({ waitUntil: 'domcontentloaded', timeout: 15_000 })
  await page.waitForSelector('.today-list', { timeout: 10_000 })
  offline = await page.evaluate(() => ({
    appChildren: document.querySelector('#app')?.childElementCount ?? 0,
    hasOfflineBadge: document.body.innerText.includes('Офлайн'),
    hasTodayRows: document.body.innerText.includes('Пары на сегодня'),
  }))
  await page.screenshot({ path: screenshotPath, fullPage: true })
  result = { status: offline.appChildren > 0 && offline.hasOfflineBadge && offline.hasTodayRows ? 'PASS' : 'FAIL', phase, origin: taskOrigin, protocol: verifyProtocol, host: verifyHost, base: verifyBase, screenshot: screenshotPath, online, offline, consoleErrors, failedRequests }
  if (result.status !== 'PASS') process.exitCode = 1
} catch (error) {
  let bodyText = null
  let screenshot = null
  if (page && phase !== 'online bootstrap') {
    bodyText = await page.locator('body').innerText().catch(() => null)
    screenshot = process.env.RCT_PWA_VERIFY_FAILURE_SCREENSHOT ?? resolve(distDirectory, 'offline-shell-failure.png')
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
