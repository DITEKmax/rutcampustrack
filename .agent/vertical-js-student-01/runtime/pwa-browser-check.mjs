import { createHash, X509Certificate } from 'node:crypto'
import { createServer as createHttpsServer } from 'node:https'
import { appendFile, readFile, stat } from 'node:fs/promises'
import { isAbsolute, relative, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const distDirectory = resolve(process.env.RCT_PWA_DIST ?? process.argv[2] ?? new URL('../../../..', import.meta.url).pathname)
const gatewayUrl = new URL(process.env.RCT_PWA_GATEWAY_URL ?? 'http://127.0.0.1:28080')
const host = process.env.RCT_PWA_RUNTIME_HOST ?? '127.0.0.1'
const login = process.env.RCT_PWA_LOGIN ?? 'student'
const password = process.env.RCT_PWA_PASSWORD ?? 'password'
const certificatePath = process.env.RCT_PWA_CERT
const privateKeyPath = process.env.RCT_PWA_KEY
const requestedPort = Number.parseInt(process.env.RCT_PWA_PORT ?? '0', 10)
const screenshotPath = process.env.RCT_PWA_SCREENSHOT ?? resolve(distDirectory, 'runtime-pwa-offline.png')
const chromePath = process.env.RCT_CHROME_PATH ?? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const playwrightModule = process.env.RCT_PLAYWRIGHT_MODULE
const progressPath = process.env.RCT_PWA_PROGRESS

if (!certificatePath || !privateKeyPath) throw new Error('RCT_PWA_CERT and RCT_PWA_KEY are required')
if (!Number.isInteger(requestedPort) || requestedPort < 0 || requestedPort > 65535) throw new Error('RCT_PWA_PORT must be an integer between 0 and 65535')
if (!playwrightModule) throw new Error('RCT_PLAYWRIGHT_MODULE must point to playwright/index.mjs')
await stat(resolve(distDirectory, 'index.html'))
await stat(resolve(distDirectory, 'sw.js'))
await stat(resolve(distDirectory, 'sw-assets.js'))

const { chromium } = await import(pathToFileURL(playwrightModule).href)
const mimeTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.webmanifest': 'application/manifest+json',
  '.woff2': 'font/woff2',
}
const hopByHopHeaders = new Set([
  'connection',
  'content-length',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
])

function contentType(fileName) {
  const extension = fileName.slice(fileName.lastIndexOf('.'))
  return mimeTypes[extension] ?? 'application/octet-stream'
}

function readBody(request) {
  return new Promise((resolveBody, rejectBody) => {
    const chunks = []
    request.on('data', (chunk) => chunks.push(chunk))
    request.on('end', () => resolveBody(Buffer.concat(chunks)))
    request.on('error', rejectBody)
  })
}

function requestHeaders(request) {
  const headers = new Headers()
  for (const [name, rawValue] of Object.entries(request.headers)) {
    if (rawValue === undefined || hopByHopHeaders.has(name.toLowerCase()) || name === 'host') continue
    headers.set(name, Array.isArray(rawValue) ? rawValue.join(', ') : rawValue)
  }
  return headers
}

function safeSetCookieProjection(setCookie) {
  const segments = setCookie.split(';').map((segment) => segment.trim())
  const attributeValues = new Map(segments.slice(1).map((segment) => {
    const separator = segment.indexOf('=')
    return [separator < 0 ? segment.toLowerCase() : segment.slice(0, separator).toLowerCase(), separator < 0 ? true : segment.slice(separator + 1)]
  }))
  return {
    name: segments[0]?.slice(0, segments[0].indexOf('=')) ?? null,
    path: attributeValues.get('path') ?? null,
    httpOnly: attributeValues.has('httponly'),
    secure: attributeValues.has('secure'),
    sameSite: attributeValues.get('samesite') ?? null,
  }
}

function withinTimeout(promise, timeoutMs, label) {
  let timeout
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      timeout = setTimeout(() => reject(new Error(`${label} timed out after ${timeoutMs}ms`)), timeoutMs)
    }),
  ]).finally(() => clearTimeout(timeout))
}

function closeServer(server) {
  server.closeAllConnections?.()
  return withinTimeout(new Promise((resolveClose, rejectClose) => {
    server.close((error) => error ? rejectClose(error) : resolveClose())
  }), 10_000, 'PWA HTTPS server close')
}

async function recordProgress(value) {
  if (!progressPath) return
  await appendFile(progressPath, `${new Date().toISOString()} ${value}\n`).catch(() => {})
}

const apiRequests = []
const apiResponses = []

async function proxyApi(request, response) {
  const target = new URL(request.url ?? '/', gatewayUrl)
  const body = await readBody(request)
  const headers = requestHeaders(request)
  const init = { method: request.method, headers, redirect: 'manual' }
  if (body.length > 0 && request.method !== 'GET' && request.method !== 'HEAD') {
    init.body = body
  }
  const requestRecord = { method: request.method, path: `${target.pathname}${target.search}` }
  apiRequests.push(requestRecord)
  try {
    const upstream = await fetch(target, init)
    requestRecord.status = upstream.status
    const responseBody = Buffer.from(await upstream.arrayBuffer())
    let problem = null
    try {
      const parsed = JSON.parse(responseBody.toString('utf8'))
      problem = Object.fromEntries(['status', 'code', 'title', 'type']
        .filter((field) => parsed[field] !== undefined)
        .map((field) => [field, parsed[field]]))
    } catch {
      problem = null
    }
    const setCookies = typeof upstream.headers.getSetCookie === 'function'
      ? upstream.headers.getSetCookie()
      : (upstream.headers.get('set-cookie') ? [upstream.headers.get('set-cookie')] : [])
    apiResponses.push({
      method: request.method,
      path: requestRecord.path,
      status: upstream.status,
      contentType: upstream.headers.get('content-type'),
      server: upstream.headers.get('server'),
      via: upstream.headers.get('via'),
      bodyLength: responseBody.length,
      problem,
      setCookies: setCookies.map(safeSetCookieProjection),
    })
    for (const [name, value] of upstream.headers) {
      if (name === 'set-cookie' || hopByHopHeaders.has(name.toLowerCase())) continue
      response.setHeader(name, value)
    }
    if (setCookies.length > 0) response.setHeader('set-cookie', setCookies)
    response.writeHead(upstream.status)
    response.end(responseBody)
  } catch {
    requestRecord.status = null
    response.writeHead(502, { 'Content-Type': 'application/problem+json' })
    response.end('{"status":502,"title":"Gateway unavailable"}')
  }
}

async function serveStatic(request, response) {
  const requestUrl = new URL(request.url ?? '/', `https://${host}`)
  if (requestUrl.pathname === '/__runtime_seed') {
    response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' })
    response.end('<!doctype html><html><body></body></html>')
    return
  }
  const pathname = decodeURIComponent(requestUrl.pathname)
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
    const missing = error && typeof error === 'object' && 'code' in error && error.code === 'ENOENT'
    response.writeHead(missing ? 404 : 500).end()
  }
}

function snapshotPage(page) {
  return page.evaluate(async () => {
    const registration = await navigator.serviceWorker.getRegistration()
    const controller = navigator.serviceWorker.controller
    const cacheNames = await caches.keys()
    const cache = cacheNames.includes('rct-student-pwa-v3') ? await caches.open('rct-student-pwa-v3') : null
    let assets = []
    try {
      const source = await fetch('./sw-assets.js', { cache: 'no-store' }).then((response) => response.text())
      const serialized = source.match(/=\s*(\[[\s\S]*\]);?\s*$/)?.[1]
      assets = serialized ? JSON.parse(serialized) : []
    } catch {
      assets = []
    }
    const cached = cache ? await Promise.all(assets.map((asset) => cache.match(asset))) : []
    const cacheKeys = cache ? await cache.keys() : []
    const cacheUrls = cacheKeys.map((request) => request.url)
    const storageValues = [
      ...Object.values(localStorage),
      ...Object.values(sessionStorage),
    ]
    const jwtPattern = /[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g
    const decodeJwtJson = (segment) => {
      const normalized = segment.replace(/-/g, '+').replace(/_/g, '/')
      const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
      return JSON.parse(atob(padded))
    }
    const isJwt = (value) => {
      if (typeof value !== 'string') return false
      for (const match of value.matchAll(jwtPattern)) {
        try {
          const [encodedHeader, encodedPayload] = match[0].split('.')
          const header = decodeJwtJson(encodedHeader)
          const payload = decodeJwtJson(encodedPayload)
          if (typeof header?.alg === 'string' && header.alg.length > 0
              && payload && typeof payload === 'object'
              && (typeof payload.iss === 'string' || typeof payload.sub === 'string')) return true
        } catch {
          // Ordinary dotted text, including dates, is not an auth-shaped JWT.
        }
      }
      return false
    }
    const containsJwt = (value, seen = new Set()) => {
      if (typeof value === 'string') return isJwt(value)
      if (value === null || typeof value !== 'object' || seen.has(value)) return false
      seen.add(value)
      return Object.values(value).some((nested) => containsJwt(nested, seen))
    }
    const jwtLike = storageValues.some((value) => containsJwt(value))
    let indexedDbJwtLike = false
    let indexedDbDatabases = 0
    if (typeof indexedDB.databases === 'function') {
      const databases = await indexedDB.databases()
      indexedDbDatabases = databases.length
      for (const databaseInfo of databases) {
        if (!databaseInfo.name) continue
        const database = await new Promise((resolveDatabase, rejectDatabase) => {
          const request = indexedDB.open(databaseInfo.name)
          request.onsuccess = () => resolveDatabase(request.result)
          request.onerror = () => rejectDatabase(request.error)
        }).catch(() => null)
        if (!database) continue
        const names = [...database.objectStoreNames]
        const values = await Promise.all(names.map((name) => new Promise((resolveValues) => {
          const transaction = database.transaction(name, 'readonly')
          const request = transaction.objectStore(name).getAll()
          request.onsuccess = () => resolveValues(request.result)
          request.onerror = () => resolveValues([])
        })))
        database.close()
        if (values.some((value) => containsJwt(value))) indexedDbJwtLike = true
      }
    }
    const body = document.body.innerText
    return {
      protocol: location.protocol,
      hostname: location.hostname,
      isSecureContext: window.isSecureContext,
      registrationState: registration?.active?.state ?? null,
      controllerState: controller?.state ?? null,
      cacheNames,
      precacheCoverage: `${cached.filter(Boolean).length}/${assets.length}`,
      cacheEntryCount: cacheUrls.length,
      cacheApiEntries: cacheUrls.filter((url) => new URL(url).pathname.includes('/api/')).length,
      jwtLikeInStorage: jwtLike,
      indexedDbDatabases,
      indexedDbJwtLike,
      todayRowCount: document.querySelectorAll('.today-list .today-row').length,
      hasTodayHeading: body.includes('Пары на сегодня'),
      hasRuntimeSubject: body.includes('Runtime геопроверка'),
      hasFixtureSubject: body.includes('Дискретная математика'),
      hasOfflineBadge: body.includes('Офлайн'),
      mutationDisabled: document.querySelector('.today-hero__action')?.hasAttribute('disabled') ?? false,
    }
  })
}

function cookieProjection(cookie) {
  if (!cookie) return null
  return {
    name: cookie.name,
    path: cookie.path,
    httpOnly: cookie.httpOnly,
    secure: cookie.secure,
    sameSite: cookie.sameSite,
  }
}

const certificate = new X509Certificate(await readFile(certificatePath, 'utf8'))
const spkiHash = createHash('sha256')
  .update(certificate.publicKey.export({ type: 'spki', format: 'der' }))
  .digest('base64')
const tlsOptions = {
  key: await readFile(privateKeyPath),
  cert: await readFile(certificatePath),
}
const server = createHttpsServer(tlsOptions, async (request, response) => {
  if ((request.url ?? '').startsWith('/api/')) {
    await proxyApi(request, response)
    return
  }
  await serveStatic(request, response)
})
await new Promise((resolveServer, rejectServer) => {
  server.once('error', rejectServer)
  server.listen(requestedPort, '127.0.0.1', () => resolveServer())
})
const address = server.address()
if (!address || typeof address === 'string') throw new Error('PWA HTTPS proxy did not bind a TCP port')
const origin = `https://${host}:${address.port}`
const appUrl = `${origin}/`
const seedUrl = `${origin}/__runtime_seed`

let browser
let page
let phase = 'startup'
let seedStatus = null
let online = null
let offline = null
let result = null
let offlinePhase = false
const consoleErrors = []
const failedRequests = []
const offlineApiRequests = []
const offlineApiResponses = []

async function enterPhase(nextPhase) {
  phase = nextPhase
  await recordProgress(phase)
}

try {
  await enterPhase('browser launch')
  browser = await chromium.launch({
    executablePath: chromePath,
    headless: true,
    args: [`--ignore-certificate-errors-spki-list=${spkiHash}`],
  })
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
  page = await context.newPage()
  page.on('console', (message) => { if (message.type() === 'error') consoleErrors.push(message.text().slice(0, 500)) })
  page.on('requestfailed', (request) => {
    const requestUrl = new URL(request.url())
    if (requestUrl.origin !== origin) return
    const record = { method: request.method(), path: `${requestUrl.pathname}${requestUrl.search}`, failure: request.failure()?.errorText ?? 'unknown' }
    failedRequests.push(record)
    if (requestUrl.pathname.startsWith('/api/')) offlineApiRequests.push(record)
  })
  page.on('response', (response) => {
    const responseUrl = new URL(response.url())
    if (responseUrl.origin === origin && responseUrl.pathname.startsWith('/api/')) {
      const record = { method: response.request().method(), path: `${responseUrl.pathname}${responseUrl.search}`, status: response.status() }
      if (offlinePhase) offlineApiResponses.push(record)
    }
  })

  await enterPhase('gateway login seed')
  const seedResponse = await page.goto(seedUrl, { waitUntil: 'domcontentloaded' })
  seedStatus = seedResponse?.status() ?? null
  if (seedStatus !== 200) throw new Error(`PWA HTTPS seed returned ${seedStatus}`)
  const loginResult = await page.evaluate(async ({ loginValue, passwordValue }) => {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ login: loginValue, password: passwordValue }),
    })
    const responseBody = await response.text()
    return { status: response.status, ok: response.ok, problem: response.ok ? null : responseBody.slice(0, 1_000) }
  }, { loginValue: login, passwordValue: password })
  if (!loginResult.ok || loginResult.status !== 200) throw new Error(`Gateway login returned ${loginResult.status}: ${loginResult.problem ?? 'no problem details'}`)
  const loginCookie = (await context.cookies(`${origin}/api/auth/refresh`)).find((cookie) => cookie.name === 'rct_refresh')
  if (!loginCookie?.httpOnly || !loginCookie.secure || loginCookie.sameSite !== 'Strict' || loginCookie.path !== '/api/auth') {
    throw new Error('Gateway login did not set the required refresh cookie attributes')
  }
  const initialCookieValue = loginCookie.value
  await enterPhase('production PWA online bootstrap')
  await page.goto(appUrl, { waitUntil: 'networkidle' })
  await page.waitForSelector('.today-list', { timeout: 20_000 })
  await page.waitForFunction(() => navigator.serviceWorker.getRegistration()
    .then((registration) => registration?.active?.state === 'activated'), null, { timeout: 20_000 })
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null, null, { timeout: 20_000 })
  online = await snapshotPage(page)
  const refreshedCookie = (await context.cookies(`${origin}/api/auth/refresh`)).find((cookie) => cookie.name === 'rct_refresh')
  if (!refreshedCookie || refreshedCookie.value === initialCookieValue) throw new Error('PWA bootstrap did not rotate the refresh cookie')
  if (!online.isSecureContext || online.protocol !== 'https:' || online.hostname !== host) throw new Error('PWA did not run at the task HTTPS origin')
  if (online.registrationState !== 'activated' || online.controllerState !== 'activated') throw new Error('PWA service worker is not active and controlling')
  if (online.precacheCoverage !== '7/7' && !/^\d+\/\d+$/.test(online.precacheCoverage)) throw new Error(`Invalid PWA precache coverage: ${online.precacheCoverage}`)
  const [cached, total] = online.precacheCoverage.split('/').map(Number)
  if (total < 1 || cached !== total || online.cacheApiEntries !== 0 || online.jwtLikeInStorage || online.indexedDbJwtLike) throw new Error('PWA cache/token boundary is not satisfied online')
  if (online.todayRowCount !== 2 || !online.hasTodayHeading || !online.hasRuntimeSubject || online.hasFixtureSubject) throw new Error('Production PWA did not render the real Today projection')

  await enterPhase('production PWA offline reload')
  offlinePhase = true
  await context.setOffline(true)
  await closeServer(server)
  await page.reload({ waitUntil: 'domcontentloaded', timeout: 15_000 })
  await page.waitForSelector('.today-list', { timeout: 10_000 })
  offline = await snapshotPage(page)
  await page.screenshot({ path: screenshotPath, fullPage: true })
  if (offline.todayRowCount !== 2 || !offline.hasTodayHeading || !offline.hasOfflineBadge || !offline.mutationDisabled) throw new Error('Offline PWA did not render cached Today with mutations disabled')
  if (offline.cacheApiEntries !== 0 || offline.jwtLikeInStorage || offline.indexedDbJwtLike) throw new Error('Offline PWA exposed an API response or token in persistent storage')
  if (offlineApiResponses.length !== 0) throw new Error('Offline API request received a response instead of failing outside the service-worker cache')

  result = {
    status: 'PASS',
    phase,
    origin,
    seedStatus,
    secureContext: online.isSecureContext,
    cookie: cookieProjection(refreshedCookie),
    online,
    offline,
    apiRequests,
    apiResponses,
    offlineApiRequests,
    offlineApiResponses,
    consoleErrors,
    failedRequests,
    screenshot: screenshotPath,
  }
} catch (error) {
  let bodyText = null
  if (page) bodyText = await page.locator('body').innerText().catch(() => null)
  result = {
    status: 'FAIL',
    phase,
    origin,
    seedStatus,
    error: error instanceof Error ? error.message : 'unknown',
    online,
    offline,
    apiRequests,
    apiResponses,
    offlineApiRequests,
    offlineApiResponses,
    bodyText: bodyText?.slice(0, 2_000) ?? null,
    consoleErrors,
    failedRequests,
    screenshot: screenshotPath,
  }
  process.exitCode = 1
} finally {
  await recordProgress('cleanup')
  if (browser) await withinTimeout(browser.close(), 10_000, 'PWA browser close')
  if (server.listening) await closeServer(server)
  if (result) process.stdout.write(`${JSON.stringify(result)}\n`)
}
