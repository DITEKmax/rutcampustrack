import { createServer } from 'node:https'
import { readFile } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'

const [certificatePath, keyPath, playwrightPath, chromePath] = process.argv.slice(2)
if (!certificatePath || !keyPath || !playwrightPath || !chromePath) {
  throw new Error('Usage: browser-https-transport-probe.mjs <cert> <key> <playwright-index.mjs> <chrome.exe>')
}

const host = '127.0.0.1'
const port = 28445
const origin = `https://${host}:${port}`
const requests = []
let server
let browser
let result

function safeRequest(request, bodyLength) {
  return {
    method: request.method,
    path: new URL(request.url ?? '/', origin).pathname,
    host: request.headers.host ?? null,
    origin: request.headers.origin ?? null,
    contentType: request.headers['content-type'] ?? null,
    bodyLength,
  }
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = []
    request.on('data', (chunk) => chunks.push(chunk))
    request.on('end', () => resolve(Buffer.concat(chunks)))
    request.on('error', reject)
  })
}

function close(serverToClose) {
  return new Promise((resolve, reject) => serverToClose.close((error) => error ? reject(error) : resolve()))
}

try {
  const { chromium } = await import(pathToFileURL(playwrightPath).href)
  const certificate = await readFile(certificatePath)
  const key = await readFile(keyPath)
  server = createServer({ cert: certificate, key }, async (request, response) => {
    const body = await readBody(request)
    requests.push(safeRequest(request, body.length))
    const url = new URL(request.url ?? '/', origin)
    if (request.method === 'GET' && url.pathname === '/health') {
      response.writeHead(200, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' })
      response.end('{"status":"ok"}')
      return
    }
    if (request.method === 'POST' && url.pathname === '/transport' && request.headers.origin === origin) {
      response.writeHead(200, {
        'content-type': 'application/json; charset=utf-8',
        'cache-control': 'no-store',
        'set-cookie': 'rct_refresh=fixture; Path=/api/auth; HttpOnly; Secure; SameSite=Strict',
      })
      response.end('{"status":"accepted"}')
      return
    }
    response.writeHead(404, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' })
    response.end('{"status":"not-found"}')
  })
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(port, host, resolve)
  })

  browser = await chromium.launch({ executablePath: chromePath, headless: true, args: ['--ignore-certificate-errors'] })
  const context = await browser.newContext({ ignoreHTTPSErrors: true })
  const page = await context.newPage()
  const getResponse = await page.goto(`${origin}/health`, { waitUntil: 'domcontentloaded', timeout: 15_000 })
  const post = await page.evaluate(async () => {
    const response = await fetch('/transport', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: '{"probe":"transport"}',
    })
    const payload = await response.json()
    return { status: response.status, contentType: response.headers.get('content-type'), bodyStatus: payload.status }
  })
  const getStatus = getResponse?.status() ?? null
  const observedPost = requests.find((request) => request.method === 'POST' && request.path === '/transport') ?? null
  const rootCookies = await context.cookies(origin)
  const authCookies = await context.cookies(`${origin}/api/auth/refresh`)
  const refreshCookie = authCookies.find((cookie) => cookie.name === 'rct_refresh') ?? null
  const passed = getStatus === 200 && post.status === 200 && post.bodyStatus === 'accepted'
    && observedPost?.origin === origin && observedPost.host === `${host}:${port}`
    && !rootCookies.some((cookie) => cookie.name === 'rct_refresh')
    && refreshCookie?.path === '/api/auth' && refreshCookie.httpOnly && refreshCookie.secure && refreshCookie.sameSite === 'Strict'
  result = {
    status: passed ? 'PASS' : 'FAIL', origin, get: { status: getStatus }, post,
    cookieQueries: {
      originRefreshCount: rootCookies.filter((cookie) => cookie.name === 'rct_refresh').length,
      authRefresh: refreshCookie ? { path: refreshCookie.path, httpOnly: refreshCookie.httpOnly, secure: refreshCookie.secure, sameSite: refreshCookie.sameSite } : null,
    },
    observedRequests: requests,
  }
  if (!passed) process.exitCode = 1
  await context.close()
} catch (error) {
  result = { status: 'FAIL', origin, error: error instanceof Error ? error.message : 'unknown', observedRequests: requests }
  process.exitCode = 1
} finally {
  await browser?.close()
  if (server?.listening) await close(server)
  if (result) process.stdout.write(`${JSON.stringify(result)}\n`)
}
