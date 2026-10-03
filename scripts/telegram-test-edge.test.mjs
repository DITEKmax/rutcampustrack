import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import http from 'node:http'
import { safePath, apiAllowed, upstreamRequest, snapshotDist, readSnapshot, startEdge } from './telegram-test-edge.mjs'

test('public surface blocks alternate path encodings and administrative/auth bypasses', () => {
  for (const value of ['/api/%61uth/login', '/api/v1/student/../admin', '/api/v1/student/%2e%2e/admin', '//api/auth/tma', '/api//auth/tma', '/api/auth/tma#admin', '/api\\auth/tma', 'https://127.0.0.1/api/auth/tma']) assert.equal(safePath(value), null)
  for (const value of ['/api/auth/login', '/api/auth/otp/request', '/api/auth/password-reset/request', '/api/auth/qr-session', '/api/auth/admin/users/1', '/api/academic/users', '/api/internal/foo', '/actuator/health', '/metrics', '/openapi/auth-service', '/api/v1/studentish/foo', '/api/auth/tma/']) assert.equal(apiAllowed('POST', value), false)
  assert.equal(apiAllowed('POST', '/api/auth/tma'), true)
  assert.equal(apiAllowed('GET', '/api/auth/tma'), false)
  assert.equal(apiAllowed('GET', '/api/auth/session'), true)
  assert.equal(apiAllowed('POST', '/api/auth/session'), false)
  assert.equal(apiAllowed('GET', '/api/v1/student/today'), true)
})

test('student bootstrap permissions and role selection use only their exact protected contracts', () => {
  assert.equal(apiAllowed('GET', '/api/academic/assistants/me/permissions'), true)
  for (const method of ['POST', 'PUT', 'PATCH', 'DELETE']) assert.equal(apiAllowed(method, '/api/academic/assistants/me/permissions'), false)
  for (const pathname of ['/api/academic/assistants/me/permissions/', '/api/academic/assistants/other/permissions', '/api/academic/assistants', '/api/academic/users', '/api/auth/admin/users', '/api/auth/login']) assert.equal(apiAllowed('GET', pathname), false)
  assert.equal(apiAllowed('PUT', '/api/auth/session/active-role'), true)
  for (const method of ['GET', 'POST', 'PATCH', 'DELETE']) assert.equal(apiAllowed(method, '/api/auth/session/active-role'), false)
})

test('verified snapshot stays immutable; modified files and fixture builds fail closed', () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'rct-telegram-edge-'))
  try {
    const source = path.join(temp, 'source')
    fs.mkdirSync(path.join(source, 'assets'), { recursive: true })
    fs.writeFileSync(path.join(source, 'index.html'), '<script src="https://telegram.org/js/telegram-web-app.js"></script>')
    fs.writeFileSync(path.join(source, 'assets', 'main.js'), 'real()')
    const snapshot = path.join(temp, 'snapshot')
    const digest = snapshotDist(source, snapshot)
    const files = readSnapshot(snapshot, digest)
    fs.writeFileSync(path.join(snapshot, 'assets', 'main.js'), 'modified')
    assert.equal(files.get('/assets/main.js').toString(), 'real()')
    assert.throws(() => readSnapshot(snapshot, digest), /hash differs/)
    assert.throws(() => readSnapshot(snapshot, '0'.repeat(64)), /manifest hash differs/)
    fs.writeFileSync(path.join(source, 'assets', 'main.js'), 'fixture-signed-init-data')
    assert.throws(() => snapshotDist(source, path.join(temp, 'fixture')), /Fixture/)
  } finally { fs.rmSync(temp, { recursive: true, force: true }) }
})

test('SockJS discovery and bounded websocket use a canonical ticket header; upstream URL and privacy headers omit credentials', () => {
  const ticket = '12345678-1234-1234-1234-123456789abc'
  const incoming = { referer: 'https://fixture.test/?ticket=' + ticket, 'user-agent': 'private-value' }
  const info = upstreamRequest('/api/ws/info?ticket=' + ticket + '&t=123456', incoming, 'fixture.test')
  assert.equal(apiAllowed('GET', '/api/ws/info'), true)
  assert.equal(apiAllowed('POST', '/api/ws/info'), false)
  assert.equal(info.info, true)
  assert.equal(info.path, '/api/ws/info?t=123456')
  assert.equal(info.headers['x-rct-ws-ticket'], ticket)
  assert.equal(info.headers.referer, undefined)
  assert.equal(info.headers['user-agent'], 'RutCampusTrack-TestEdge/1')
  const websocket = upstreamRequest('/api/ws/123/abc_123/websocket?ticket=' + ticket, incoming, 'fixture.test')
  assert.equal(websocket.websocket, true)
  assert.equal(websocket.path, '/api/ws/123/abc_123/websocket')
  assert.equal(websocket.headers['x-rct-ws-ticket'], ticket)
  for (const pathname of ['/api/ws/admin/abc/websocket', '/api/ws/123/abc/xhr', '/api/ws/websocket', '/api/ws/123/abc/websocket/extra']) {
    assert.equal(upstreamRequest(pathname + '?ticket=' + ticket, {}, 'fixture.test'), null)
  }
  for (const query of ['ticket=' + ticket + '&ticket=' + ticket, 'ticket=' + ticket + '&%74icket=' + ticket, 'ticket=' + ticket.toUpperCase(), 'ticket=bad', '', 'ticket=' + ticket + '&access_token=secret']) {
    assert.equal(upstreamRequest('/api/ws/info' + (query ? '?' + query : ''), {}, 'fixture.test'), null)
  }
  assert.equal(upstreamRequest('/api/ws/info?ticket=' + ticket, { 'x-rct-ws-ticket': ticket }, 'fixture.test'), null)
})

test('public auth rejects query credentials; ordinary mobile filters retain their values', () => {
  for (const pathname of ['/api/auth/tma', '/api/auth/refresh', '/api/auth/logout']) {
    assert.equal(upstreamRequest(pathname + '?initData=secret', {}, 'fixture.test'), null)
    assert.equal(upstreamRequest(pathname, {}, 'fixture.test').path, pathname)
  }
  const prepared = upstreamRequest('/api/v1/student/statistics?semesterId=42&range=weeks&types=LECTURE&types=PRACTICE', { authorization: 'Bearer opaque' }, 'fixture.test')
  assert.equal(prepared.path, '/api/v1/student/statistics?semesterId=42&range=weeks&types=LECTURE&types=PRACTICE')
  assert.equal(prepared.headers.authorization, 'Bearer opaque')
  for (const query of ['token=secret', 'access_token=secret', 'otp=123456', 'password=secret', 'ticket=secret', 'authorization=secret']) {
    assert.equal(upstreamRequest('/api/v1/student/today?' + query, {}, 'fixture.test'), null)
  }
})

test('loopback listener serves only pinned assets, refuses unauthenticated protected APIs, and expires', async () => {
  const files = new Map([['/index.html', Buffer.from('tma')], ['/assets/main.js', Buffer.from('real()')]])
  const { server, stop } = startEdge(files, 'unused-test-ca', 1, 'https://fixture.test')
  const request = (pathname, headers = { host: 'fixture.test' }) => new Promise((resolve, reject) => {
    http.get({ host: '127.0.0.1', port: 18530, path: pathname, headers }, response => {
      const chunks = []; response.on('data', chunk => chunks.push(chunk))
      response.on('end', () => resolve({ status: response.statusCode, text: Buffer.concat(chunks).toString() }))
    }).on('error', reject)
  })
  try {
    if (!server.listening) await new Promise(resolve => server.once('listening', resolve))
    assert.equal(server.address().address, '127.0.0.1')
    assert.equal((await request('/mini-app/')).text, 'tma')
    assert.equal((await request('/assets/main.js')).text, 'real()')
    for (const pathname of ['/', '/assets/missing.js', '/metrics', '/api/auth/login', '/assets/%2e%2e/index.html']) assert.equal((await request(pathname)).status, 404)
    assert.equal((await request('/api/v1/student/today')).status, 401)
    assert.equal((await request('/api/academic/assistants/me/permissions')).status, 401)
    assert.equal((await request('/mini-app/', { host: 'other.test' })).status, 403)
    assert.equal((await request('/mini-app/', { host: 'fixture.test', origin: 'https://other.test' })).status, 403)
    await new Promise(resolve => server.once('close', resolve))
    assert.equal(server.listening, false)
  } finally { stop() }
})
