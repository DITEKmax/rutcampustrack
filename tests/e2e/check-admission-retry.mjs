import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { createRequire } from 'node:module'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(fileURLToPath(import.meta.url))
const require = createRequire(import.meta.url)
const { requireOrImport } = require(
  join(dirname(require.resolve('playwright/package.json')), 'lib/transform/transform.js'),
)

try {
  const { admit } = await requireOrImport(join(root, 'fixtures/mobile-dataset.ts'))
  const marker = `RCT_ADMISSION_CONTROL_${randomUUID()}`
  const user = { login: marker, password: marker }
  let cases = 0

  async function check(status, header, secondStatus = 200, sessionStatus = 200, roleStatus = 200) {
    const posts = []
    let reads = 0
    let selections = 0
    let elapsed = 0
    const session = { activeRole: 'STUDENT', sessionVersion: 1 }
    const response = (code) => ({
      status: () => code,
      headers: () => (header === undefined ? {} : { 'retry-after': header }),
      json: async () => {
        assert.equal(code, 200)
        return { accessToken: marker }
      },
    })
    const request = {
      post: async (path, options) => {
        assert.equal(path, '/api/auth/login')
        assert.equal(options.data.login === marker && options.data.password === marker, true)
        posts.push(performance.now())
        if (posts.length === 2) elapsed = posts[1] - posts[0]
        assert.equal(posts.length <= 2, true)
        return response(posts.length === 1 ? status : secondStatus)
      },
      get: async (path, options) => {
        reads++
        assert.equal(path, '/api/auth/session')
        assert.equal(options.headers.Authorization === `Bearer ${marker}`, true)
        return { status: () => sessionStatus, json: async () => session }
      },
      put: async (path, options) => {
        selections++
        assert.equal(path, '/api/auth/session/active-role')
        assert.equal(options.headers.Authorization === `Bearer ${marker}`, true)
        assert.deepEqual(options.data, { role: 'TEACHER', expectedSessionVersion: 1 })
        return {
          status: () => roleStatus,
          json: async () => ({
            accessToken: marker,
            session: { ...session, activeRole: 'TEACHER' },
          }),
        }
      },
    }
    const retry = status === 429 && header === '1'
    const loginStatus = retry ? secondStatus : status
    const failureStatus =
      loginStatus !== 200 ? loginStatus : sessionStatus !== 200 ? sessionStatus : roleStatus
    if (failureStatus !== 200) {
      const stage =
        loginStatus !== 200 ? 'login' : sessionStatus !== 200 ? 'session' : 'role TEACHER'
      await assert.rejects(admit(request, user, 'TEACHER'), (error) => {
        assert.equal(error.message, `Synthetic actor ${stage}: HTTP ${failureStatus}`)
        assert.equal(error.message.includes(marker), false)
        return true
      })
    } else {
      const actor = await admit(request, user, 'TEACHER')
      assert.equal(actor.token === marker && actor.session.activeRole === 'TEACHER', true)
    }
    assert.equal(posts.length, retry ? 2 : 1)
    assert.equal(reads, loginStatus === 200 ? 1 : 0)
    assert.equal(selections, loginStatus === 200 && sessionStatus === 200 ? 1 : 0)
    if (retry) assert.equal(elapsed >= 1000, true)
    cases++
  }

  await check(200, undefined)
  await check(429, '1')
  for (const header of [
    undefined,
    '',
    '0',
    '61',
    '-1',
    '1.5',
    '1e0',
    ' 1',
    '1 ',
    'Wed, 21 Oct 2015 07:28:00 GMT',
  ])
    await check(429, header)
  await check(401, '1')
  await check(503, '1')
  await check(429, '1', 429)
  await check(429, '1', 401)
  await check(200, undefined, 200, 401)
  await check(200, undefined, 200, 200, 403)
  process.stdout.write(`${JSON.stringify({ state: 'ADMISSION_RETRY_CONTROLS_PASS', cases })}\n`)
} catch {
  process.stderr.write('ADMISSION_RETRY_CONTROLS_FAIL\n')
  process.exitCode = 1
}
