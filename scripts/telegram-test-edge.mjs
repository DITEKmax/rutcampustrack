// Temporary, loopback-only edge for the owner-approved Telegram acceptance.
// No credentials, request bodies or URLs are logged. Production is not targeted.
import http from 'node:http'
import https from 'node:https'
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { fileURLToPath } from 'node:url'

const publicAuth = new Set(['/api/auth/tma', '/api/auth/refresh', '/api/auth/logout'])
const protectedAuth = new Map([
  ['/api/auth/session', ['GET']], ['/api/auth/session/active-role', ['PUT']],
  ['/api/auth/sessions', ['GET']], ['/api/auth/account-history', ['GET']],
  ['/api/auth/logout-all', ['POST']], ['/api/auth/ws-ticket', ['POST']],
  ['/api/academic/assistants/me/permissions', ['GET']],
])
const mobileRoutes = [
  /^\/api\/v1\/student\/(session|today|schedule|attendance|statistics|homework|requests)$/,
  /^\/api\/v1\/student\/statistics\/subjects\/[0-9]{1,19}$/,
  /^\/api\/v1\/student\/homework\/[0-9]{1,19}\/completion$/,
  /^\/api\/v1\/student\/lessons\/[0-9]{1,19}\/checkin$/,
  /^\/api\/v1\/student\/requests\/(options|excuse|late-checkin)$/,
  /^\/api\/v1\/student\/requests\/[A-Za-z0-9_-]{1,64}(\/cancel|\/attachments\/[A-Za-z0-9_-]{1,64})?$/,
  /^\/api\/v1\/map\/manifest$/,
  /^\/api\/v1\/map\/buildings\/[A-Za-z0-9_-]{1,64}\/floors\/[A-Za-z0-9_-]{1,64}\/(plan|opens)$/,
  /^\/api\/v1\/map\/buildings\/[A-Za-z0-9_-]{1,64}\/floors\/[A-Za-z0-9_-]{1,64}\/plans\/[A-Za-z0-9_-]{1,64}\/assets\/(svg|png)\/[A-Za-z0-9_-]{1,64}$/,
  /^\/api\/notifications(\/(unread-count|mark-all-read|preferences)|\/[A-Za-z0-9_-]{1,64}\/read)?$/,
  /^\/api\/push\/(vapid-public-key|subscribe)$/,
]
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const sockJsWebsocket = /^\/api\/ws\/[0-9]{3}\/[A-Za-z0-9_-]{1,64}\/websocket$/
const mobileQueryKeys = new Set(['semesterId', 'range', 'types', 'from', 'to', 'bucket', 'page', 'size', 'sort', 'unreadOnly', 'cursor', 'limit', 'version', 'format'])
const sha256 = bytes => crypto.createHash('sha256').update(bytes).digest('hex')

export function safePath(rawUrl) {
  if (typeof rawUrl !== 'string' || !rawUrl.startsWith('/') || rawUrl.startsWith('//')) return null
  const pathname = rawUrl.split('?')[0]
  // Reject alternate encodings before any URL parser or downstream normalization.
  if (/[\\%#\x00-\x20\x7f]/.test(pathname) || pathname.includes('//') || pathname.split('/').some(part => part === '.' || part === '..')) return null
  return pathname
}

export function apiAllowed(method, pathname) {
  if (publicAuth.has(pathname)) return method === 'POST'
  if (protectedAuth.has(pathname)) return protectedAuth.get(pathname).includes(method)
  if (pathname === '/api/ws/info') return method === 'GET'
  if (mobileRoutes.some(route => route.test(pathname))) return ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].includes(method)
  return false
}

export function upstreamRequest(rawUrl, incomingHeaders, publicHost) {
  const pathname = safePath(rawUrl)
  if (!pathname || pathname.length > 512) return null
  if (Object.keys(incomingHeaders).some(key => key.toLowerCase() === 'x-rct-ws-ticket')) return null
  const rawQuery = rawUrl.includes('?') ? rawUrl.slice(rawUrl.indexOf('?') + 1) : ''
  if (rawQuery.length > 2048 || /%(?![0-9a-fA-F]{2})/.test(rawQuery)) return null
  const query = new URLSearchParams(rawQuery)
  const info = pathname === '/api/ws/info'
  const websocket = sockJsWebsocket.test(pathname)
  const headers = { ...proxyHeaders(incomingHeaders), host: publicHost, 'user-agent': 'RutCampusTrack-TestEdge/1' }
  if (info || websocket) {
    const tickets = query.getAll('ticket')
    if (tickets.length !== 1 || !uuidPattern.test(tickets[0])) return null
    for (const [key, value] of query) {
      if (key !== 'ticket' && (key !== 't' || !/^[0-9]{1,20}$/.test(value))) return null
    }
    headers['x-rct-ws-ticket'] = tickets[0]
    query.delete('ticket')
  } else {
    if (publicAuth.has(pathname) && rawQuery !== '') return null
    for (const [key, value] of query) {
      if (!mobileQueryKeys.has(key) || value.length > 512 || /[\x00-\x1f\x7f]/.test(value)) return null
    }
  }
  const suffix = query.toString()
  return { path: pathname + (suffix ? '?' + suffix : ''), headers, info, websocket }
}

export function snapshotDist(source, destination) {
  if (fs.existsSync(destination)) throw new Error('Snapshot destination must be new')
  const sourceRoot = path.resolve(source)
  const rootStat = fs.lstatSync(sourceRoot)
  if (!rootStat.isDirectory() || rootStat.isSymbolicLink()) throw new Error('Dist must be a regular directory')
  const entries = []
  const visit = relative => {
    for (const name of fs.readdirSync(path.join(sourceRoot, relative)).sort()) {
      const file = path.join(relative, name)
      const stat = fs.lstatSync(path.join(sourceRoot, file))
      if (stat.isSymbolicLink()) throw new Error('Dist symlinks are forbidden')
      if (stat.isDirectory()) visit(file)
      else if (stat.isFile()) {
        const url = '/' + file.split(path.sep).join('/')
        if (url !== '/index.html' && !url.startsWith('/assets/')) continue
        const bytes = fs.readFileSync(path.join(sourceRoot, file))
        entries.push({ url, bytes: bytes.length, sha256: sha256(bytes), contents: bytes })
      } else throw new Error('Dist must contain only regular files')
    }
  }
  visit('')
  const index = entries.find(entry => entry.url === '/index.html')
  if (!index || !index.contents.toString('utf8').includes('telegram.org/js/telegram-web-app.js')) throw new Error('Vue TMA index with real Telegram SDK is required')
  if (entries.some(entry => entry.url.endsWith('.js') && (entry.contents.includes('fixture-signed-init-data') || entry.contents.includes('fixture-access-token')))) throw new Error('Fixture TMA build must not be exposed')
  fs.mkdirSync(destination, { recursive: true })
  for (const entry of entries) {
    const output = path.join(destination, entry.url.slice(1))
    fs.mkdirSync(path.dirname(output), { recursive: true })
    fs.writeFileSync(output, entry.contents, { flag: 'wx' })
  }
  const manifest = JSON.stringify(entries.map(({ contents, ...entry }) => entry))
  fs.writeFileSync(path.join(destination, 'manifest.json'), manifest, { flag: 'wx' })
  return sha256(manifest)
}

export function readSnapshot(directory, expectedSha256) {
  const root = path.resolve(directory)
  if (fs.lstatSync(root).isSymbolicLink()) throw new Error('Snapshot symlinks are forbidden')
  const manifestPath = path.join(root, 'manifest.json')
  if (fs.lstatSync(manifestPath).isSymbolicLink()) throw new Error('Snapshot manifest symlinks are forbidden')
  const manifest = fs.readFileSync(manifestPath)
  if (sha256(manifest) !== expectedSha256) throw new Error('Snapshot manifest hash differs')
  const files = new Map()
  for (const entry of JSON.parse(manifest)) {
    if (!safePath(entry.url) || (entry.url !== '/index.html' && !entry.url.startsWith('/assets/')) || files.has(entry.url)) throw new Error('Snapshot entry is outside the static allowlist')
    const filename = path.join(root, entry.url.slice(1))
    let current = root
    for (const part of entry.url.slice(1).split('/')) {
      current = path.join(current, part)
      if (fs.lstatSync(current).isSymbolicLink()) throw new Error('Snapshot symlinks are forbidden')
    }
    const bytes = fs.readFileSync(filename)
    if (bytes.length !== entry.bytes || sha256(bytes) !== entry.sha256) throw new Error('Snapshot asset hash differs')
    files.set(entry.url, bytes)
  }
  if (!files.has('/index.html')) throw new Error('Snapshot index missing')
  // Serve immutable verified bytes, never mutable paths on subsequent requests.
  return files
}

const contentTypes = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.woff2': 'font/woff2', '.svg': 'image/svg+xml', '.png': 'image/png' }
const hopHeaders = new Set(['connection', 'keep-alive', 'proxy-authenticate', 'proxy-authorization', 'te', 'trailer', 'transfer-encoding', 'upgrade', 'forwarded', 'x-forwarded-for', 'x-forwarded-host', 'x-forwarded-proto', 'x-forwarded-port', 'referer', 'user-agent', 'x-rct-ws-ticket'])
function proxyHeaders(incoming) {
  const connectionTokens = new Set(String(incoming.connection ?? '').toLowerCase().split(',').map(value => value.trim()))
  return Object.fromEntries(Object.entries(incoming).filter(([key]) => !hopHeaders.has(key) && !connectionTokens.has(key) && key !== 'host'))
}

export function startEdge(files, ca, ttlSeconds, publicOrigin) {
  if (!Number.isInteger(ttlSeconds) || ttlSeconds < 1 || ttlSeconds > 1800) throw new Error('Deadline must be 1..1800 seconds')
  const origin = new URL(publicOrigin)
  if (origin.protocol !== 'https:' || origin.username || origin.password || origin.port || origin.pathname !== '/' || origin.search || origin.hash || origin.hostname === 'localhost' || origin.hostname === '127.0.0.1') throw new Error('Exact public HTTPS origin required')
  const admittedOrigin = origin.origin
  // Pin TLS identity independently of the public HTTP Host forwarded to Gateway.
  const agent = new https.Agent({ ca, rejectUnauthorized: true, servername: 'localhost' })
  const sockets = new Set()
  const respond = (res, status, bytes = '') => {
    res.writeHead(status, { 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer', 'X-Content-Type-Options': 'nosniff' })
    res.end(bytes)
  }
  const server = http.createServer({ maxHeaderSize: 16384 }, (req, res) => {
    if (req.headers.host !== origin.host || (req.headers.origin && req.headers.origin !== admittedOrigin)) return respond(res, 403)
    const pathname = safePath(req.url)
    if (!pathname) return respond(res, 404)
    let staticPath = pathname
    if (pathname === '/mini-app/' || pathname.startsWith('/mini-app/')) staticPath = '/index.html'
    if ((req.method === 'GET' || req.method === 'HEAD') && (pathname.startsWith('/mini-app/') || pathname.startsWith('/assets/')) && files.has(staticPath)) {
      res.writeHead(200, { 'Content-Type': contentTypes[path.extname(staticPath)] ?? 'application/octet-stream', 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer', 'X-Content-Type-Options': 'nosniff' })
      return res.end(req.method === 'HEAD' ? undefined : files.get(staticPath))
    }
    if (!apiAllowed(req.method, pathname)) return respond(res, 404)
    const prepared = upstreamRequest(req.url, req.headers, origin.host)
    if (!prepared) return respond(res, 401)
    const headers = prepared.headers
    // All public auth uses signed Telegram data or an existing refresh cookie.
    // Protected routes additionally fail closed here, before Gateway admission.
    if (!publicAuth.has(pathname) && !prepared.info && !/^Bearer [A-Za-z0-9._~-]+$/.test(String(headers.authorization ?? ''))) return respond(res, 401)
    if (Number(headers['content-length'] ?? 0) > 24 * 1024 * 1024) return respond(res, 413)
    const upstream = https.request({ hostname: '127.0.0.1', port: 18514, path: prepared.path, method: req.method, headers, agent }, reply => {
      const responseHeaders = proxyHeaders(reply.headers)
      res.writeHead(reply.statusCode, { ...responseHeaders, 'Referrer-Policy': 'no-referrer' })
      reply.pipe(res)
    })
    let received = 0
    req.on('data', bytes => { received += bytes.length; if (received > 24 * 1024 * 1024) { upstream.destroy(); req.destroy() } })
    upstream.setTimeout(30000, () => upstream.destroy())
    upstream.on('error', () => { if (!res.headersSent) respond(res, 502); else res.destroy() })
    req.on('aborted', () => upstream.destroy())
    req.pipe(upstream)
  })
  server.headersTimeout = 15000
  server.requestTimeout = 30000
  server.on('connection', socket => { sockets.add(socket); socket.on('close', () => sockets.delete(socket)) })
  server.on('upgrade', (req, socket, head) => {
    const pathname = safePath(req.url)
    if (req.headers.host !== origin.host || req.headers.origin !== admittedOrigin) return socket.end('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n')
    if (req.method !== 'GET' || !sockJsWebsocket.test(pathname ?? '') || req.headers.upgrade?.toLowerCase() !== 'websocket') return socket.end('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n')
    const prepared = upstreamRequest(req.url, req.headers, origin.host)
    if (!prepared) return socket.end('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n')
    // Existing Gateway/Notification ticket authentication remains the authority.
    const headers = { ...prepared.headers, connection: 'Upgrade', upgrade: 'websocket' }
    const upstream = https.request({ hostname: '127.0.0.1', port: 18514, path: prepared.path, method: 'GET', headers, agent })
    upstream.on('upgrade', (reply, remote, remoteHead) => {
      sockets.add(remote); remote.on('close', () => sockets.delete(remote))
      socket.write(`HTTP/1.1 ${reply.statusCode} Switching Protocols\r\n`)
      for (const [name, value] of Object.entries(reply.headers)) socket.write(`${name}: ${value}\r\n`)
      socket.write('\r\n')
      if (remoteHead.length) socket.write(remoteHead)
      if (head.length) remote.write(head)
      socket.pipe(remote).pipe(socket)
      socket.on('error', () => remote.destroy()); remote.on('error', () => socket.destroy())
      socket.on('close', () => remote.destroy())
    })
    upstream.on('response', reply => { socket.end(`HTTP/1.1 ${reply.statusCode} Rejected\r\nConnection: close\r\n\r\n`); reply.resume() })
    upstream.setTimeout(30000, () => upstream.destroy())
    upstream.on('error', () => socket.destroy())
    socket.on('error', () => upstream.destroy())
    upstream.end()
  })
  const stop = () => { server.close(); for (const socket of sockets) socket.destroy(); agent.destroy() }
  const timer = setTimeout(stop, ttlSeconds * 1000)
  server.on('close', () => clearTimeout(timer))
  server.listen(18530, '127.0.0.1')
  return { server, stop }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  try {
    const args = process.argv.slice(2)
    if (args[0] === '--snapshot' && args.length === 3) {
      console.log('TMA_SNAPSHOT_SHA256=' + snapshotDist(args[1], args[2]))
    } else if (args.length === 5) {
      const [directory, manifestSha256, caPath, ttl, publicOrigin] = args
      const { server, stop } = startEdge(readSnapshot(directory, manifestSha256), fs.readFileSync(caPath), Number(ttl), publicOrigin)
      process.once('SIGINT', stop); process.once('SIGTERM', stop)
      server.on('error', () => { stop(); console.error('EDGE_START_FAILED'); process.exitCode = 1 })
      server.on('listening', () => console.log(`TMA_EDGE_READY pid=${process.pid} address=127.0.0.1:18530 deadlineSeconds=${ttl}`))
    } else throw new Error('Expected --snapshot source destination, or snapshot manifestSha256 caPem ttlSeconds publicOrigin')
  } catch { console.error('EDGE_PREFLIGHT_FAILED'); process.exitCode = 1 }
}
