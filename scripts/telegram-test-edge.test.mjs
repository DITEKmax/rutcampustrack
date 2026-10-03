import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import http from 'node:http'
import { safePath, apiAllowed, snapshotDist, readSnapshot, startEdge } from './telegram-test-edge.mjs'

test('public surface blocks alternate path encodings and administrative/auth bypasses', () => {
  for (const value of ['/api/%61uth/login', '/api/v1/student/../admin', '/api/v1/student/%2e%2e/admin', '//api/auth/tma', '/api//auth/tma', '/api/auth/tma#admin', '/api\\auth/tma', 'https://127.0.0.1/api/auth/tma']) assert.equal(safePath(value), null)
  for (const value of ['/api/auth/login', '/api/auth/otp/request', '/api/auth/password-reset/request', '/api/auth/qr-session', '/api/auth/admin/users/1', '/api/academic/users', '/api/internal/foo', '/actuator/health', '/metrics', '/openapi/auth-service', '/api/v1/studentish/foo', '/api/auth/tma/']) assert.equal(apiAllowed('POST', value), false)
  assert.equal(apiAllowed('POST', '/api/auth/tma'), true)
  assert.equal(apiAllowed('GET', '/api/auth/tma'), false)
  assert.equal(apiAllowed('GET', '/api/auth/session'), true)
  assert.equal(apiAllowed('POST', '/api/auth/session'), false)
  assert.equal(apiAllowed('GET', '/api/v1/student/today'), true)
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
    assert.equal((await request('/mini-app/', { host: 'other.test' })).status, 403)
    assert.equal((await request('/mini-app/', { host: 'fixture.test', origin: 'https://other.test' })).status, 403)
    await new Promise(resolve => server.once('close', resolve))
    assert.equal(server.listening, false)
  } finally { stop() }
})
