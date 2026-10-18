const fs = require('node:fs')
const vm = require('node:vm')
const assert = require('node:assert/strict')
const root = __dirname + require('node:path').sep
const results = []
const clone = (x) => JSON.parse(JSON.stringify(x))
function makeFixture(geo) {
  const actors = [1, 2].map((i) => ({
    login: `synthetic-${i}`,
    password: 'not-a-real-secret',
    userId: i,
    groupId: geo ? 10 : i * 10,
    lessonId: i * 100,
    date: '2026-10-18',
    ...(geo ? { latitude: 55, longitude: 37 } : { studentIds: [i * 1000, i * 1000 + 1] }),
  }))
  return {
    runId: 'u24-mock-only',
    exclusive: true,
    actors,
    ...(geo
      ? {
          observer: {
            login: 'synthetic-observer',
            password: 'not-a-real-secret',
            userId: 99,
            groupId: 10,
          },
        }
      : {}),
  }
}
function load(geo, fault = '', modify = () => {}, envOverrides = {}) {
  const fixture = makeFixture(geo)
  modify(fixture)
  const calls = [],
    checks = [],
    metrics = {},
    marks = new Map(),
    pending = new Map()
  const sessions = new Map(),
    delayed = [],
    unreadReports = new Set()
  const uncertain = ['timeoutLate', 'unreadableReadback', 'unreadableResponse'].includes(fault)
  const identities = [fixture.observer, ...fixture.actors].filter(Boolean)
  const key = (lesson, user) => `${lesson}/${user}`
  const response = (body, status = 200) => ({ status, json: () => clone(body) })
  function snapshot(a) {
    const session = sessions.get(a.userId)
    const headman = !geo || a.userId === 99
    const roles = fault === 'wrongRole' ? ['ADMIN'] : headman ? ['STUDENT', 'HEADMAN'] : ['STUDENT']
    return {
      userId: String(a.userId),
      sessionId: `session-${a.userId}`,
      sessionVersion: String(session.version),
      activeRole: session.role,
      readOnly: false,
      roles: roles.map((role) => ({
        role,
        status: 'ACTIVE',
        groupId: String(a.groupId),
        selectable: true,
        readOnly: false,
      })),
    }
  }
  function saveMark(k, row) {
    if (uncertain) delayed.push(() => marks.set(k, row))
    else if (fault !== 'noEffect') marks.set(k, row)
  }
  function writeResponse(body, status = 200) {
    if (fault === 'timeoutLate') return response({}, 0)
    if (fault === 'unreadableResponse')
      return {
        status: 200,
        json: () => {
          throw new Error('unreadable')
        },
      }
    return response(body, status)
  }
  const ctx = vm.createContext({
    __ENV: {
      BASE_URL: 'http://mock.invalid',
      FIXTURES_FILE: '/unused',
      OWNED_RUN_ID: fixture.runId,
      ...envOverrides,
    },
    open: () => JSON.stringify(fixture),
    check: (value, rules) => {
      const ok = Object.values(rules).every((fn) => Boolean(fn(value)))
      checks.push({ label: Object.keys(rules)[0], ok })
      return ok
    },
    exec: { vu: { idInTest: 1 } },
    Counter: class {
      constructor(name) {
        this.name = name
        metrics[name] = []
      }
      add(x) {
        metrics[this.name].push(x)
      }
    },
    Rate: class {
      constructor(name) {
        this.name = name
        metrics[name] = []
      }
      add(x) {
        metrics[this.name].push(x)
      }
    },
  })
  function request(method, raw, payload, params) {
    const url = new URL(raw),
      token = params.headers.Authorization.replace('Bearer ', '')
    const identity = identities.find((a) => sessions.get(a.userId)?.token === token)
    calls.push({ method, path: url.pathname, query: url.searchParams, payload, token })
    assert.equal(params.timeout, '5s')
    assert.equal(params.redirects, 0)
    assert(params.jar)
    if (url.pathname === '/api/auth/login') {
      const a = identities.find((x) => x.login === JSON.parse(payload).login)
      assert(a)
      if (fault === 'login429' && a.userId === 2) return response({}, 429)
      const token = `token-${a.userId}-v1`
      sessions.set(a.userId, { token, version: 1, role: 'STUDENT' }) // Actual ActiveRolePolicy default.
      return response(fault === 'oldToken' ? { access_token: 'obsolete' } : { accessToken: token })
    }
    if (!identity) return response({}, 401)
    if (url.pathname === '/api/auth/logout') {
      assert.equal(
        token,
        sessions.get(identity.userId).token,
        'logout must use newest selected token',
      )
      return response(null, fault === 'logout503' ? 503 : 204)
    }
    if (url.pathname === '/api/auth/session/active-role') {
      assert.equal(method, 'PUT')
      const command = JSON.parse(payload),
        session = sessions.get(identity.userId)
      assert.equal(command.expectedSessionVersion, String(session.version))
      assert.equal(session.role, 'STUDENT')
      assert.equal(command.role, 'HEADMAN')
      if (fault === 'roleCAS409') return response({}, 409)
      session.role = 'HEADMAN'
      session.version++
      session.token = `token-${identity.userId}-v${session.version}`
      const selected = snapshot(identity)
      return response({
        accessToken: session.token,
        expiresIn: 900,
        session: fault === 'malformedSelected' ? { ...selected, userId: '999999' } : selected,
      })
    }
    if (url.pathname === '/api/auth/session') {
      const current = snapshot(identity)
      return response(
        fault === 'freshWrongRole' && sessions.get(identity.userId).version > 1
          ? { ...current, activeRole: 'STUDENT' }
          : current,
      )
    }
    if (url.pathname.startsWith('/api/schedule/groups/')) {
      const groupId = Number(url.pathname.split('/')[4])
      const lessons = fixture.actors
        .filter((a) => a.groupId === groupId)
        .map((a) => ({
          id: a.lessonId,
          groupId,
          date: a.date,
          current: true,
          status: 'ACTIVE',
          geoBlocked: false,
          blockedByHeadman: false,
        }))
      return response(
        fault === 'nonHAL'
          ? lessons
          : {
              _embedded: { lessonResponseList: fault === 'noLesson' ? [] : lessons },
              page: { totalPages: fault === 'incompleteHAL' ? 2 : 1 },
            },
      )
    }
    if (url.pathname.startsWith('/api/attendance/reports/lesson/')) {
      const lessonId = Number(url.pathname.split('/').at(-1))
      const a = fixture.actors.find((a) => a.lessonId === lessonId)
      const studentIds = geo ? [a.userId] : a.studentIds
      if (fault === 'unreadableReadback' && delayed.length && !unreadReports.has(lessonId)) {
        unreadReports.add(lessonId)
        return response({}, 503)
      }
      if (fault === 'preexisting')
        marks.set(key(lessonId, studentIds[0]), {
          status: 'present',
          source: 'headman',
          markRevision: 'prior',
        })
      return response({
        lessonId,
        groupId: a.groupId,
        lessonDate: a.date,
        editable: true,
        entries: studentIds.map((userId) => ({
          userId,
          status: null,
          source: null,
          markRevision: null,
          editable: true,
          ...(marks.get(key(lessonId, userId)) || {}),
        })),
      })
    }
    if (url.pathname === '/api/attendance/marks/batch') {
      const body = JSON.parse(payload)
      assert.deepEqual(Object.keys(body), ['items'])
      const actor = fixture.actors.find((a) => a.userId === identity.userId)
      assert.equal(body.items.length, actor.studentIds.length)
      body.items.forEach((item) => {
        assert.equal(item.lessonId, actor.lessonId)
        assert(actor.studentIds.includes(item.userId))
        assert.equal(item.status, 'PRESENT')
        saveMark(key(item.lessonId, item.userId), {
          status: 'present',
          source: 'headman',
          markRevision: 'created-revision',
        })
      })
      return writeResponse(
        {
          processed: body.items.length + (fault === 'wrongProcessed' ? -1 : 0),
          items: body.items.map((item) => ({
            ...item,
            timestamp: '2026-10-18T00:00:00Z',
            ...(fault === 'wrongItems' ? { userId: 77777 } : {}),
          })),
        },
        fault === 'batch207' ? 207 : 200,
      )
    }
    if (url.pathname.endsWith('/checkin')) {
      const a = fixture.actors.find((a) => a.userId === identity.userId)
      assert.equal(url.pathname, `/api/v1/student/lessons/${a.lessonId}/checkin`)
      assert.deepEqual(JSON.parse(payload), {
        geo: { kind: 'COORDINATES', latitude: a.latitude, longitude: a.longitude },
      })
      assert.equal(
        params.headers['Idempotency-Key'],
        `k6-${fixture.runId}-${a.userId}-${a.lessonId}`,
      )
      assert(
        params.headers['Idempotency-Key'].length >= 16 &&
          params.headers['Idempotency-Key'].length <= 128,
      )
      if (fault === 'pending') {
        pending.set(a.userId, {
          id: '123456789012345678901234',
          kind: 'LATE_CHECKIN',
          origin: 'AUTO_GEO_FAILURE',
          status: 'PENDING',
          lessons: [{ id: String(a.lessonId) }],
        })
        return response({
          lessonId: String(a.lessonId),
          outcome: 'PENDING_CONFIRMATION',
          attendance: null,
          request: {},
          retryAt: '2026-10-18T00:05:00Z',
          serverNow: '2026-10-18T00:00:00Z',
        })
      }
      saveMark(key(a.lessonId, a.userId), {
        status: 'present',
        source: 'student_geo',
        markRevision: 'created-revision',
      })
      return writeResponse({
        lessonId: String(a.lessonId),
        outcome: 'PRESENT',
        attendance: { status: 'PRESENT', source: 'STUDENT_GEO', markedAt: '2026-10-18T00:00:00Z' },
        request: null,
        retryAt: null,
        serverNow: '2026-10-18T00:00:00Z',
      })
    }
    if (url.pathname === '/api/v1/student/requests') {
      const rows =
        url.searchParams.get('bucket') === 'OPEN' && pending.has(identity.userId)
          ? [pending.get(identity.userId)]
          : []
      return response({
        content: rows,
        page: 0,
        totalPages: rows.length ? 1 : 0,
        totalElements: rows.length,
      })
    }
    if (url.pathname.endsWith('/cancel')) {
      const row = pending.get(identity.userId)
      assert(row)
      pending.delete(identity.userId)
      return response({ summary: { ...row, status: 'CANCELLED' } })
    }
    if (method === 'DELETE' && url.pathname.startsWith('/api/attendance/lessons/')) {
      const parts = url.pathname.split('/'),
        k = key(Number(parts[4]), Number(parts[6]))
      assert(marks.has(k))
      assert.equal(url.searchParams.get('expectedStatus'), 'PRESENT')
      assert.equal(url.searchParams.get('expectedRevision'), 'created-revision')
      if (fault === 'casRace') {
        marks.set(k, { status: 'present', source: 'headman', markRevision: 'concurrent-revision' })
        return response({}, 409)
      }
      marks.delete(k)
      return response(null, 204)
    }
    throw new Error(`Unexpected mocked operation ${method} ${url.pathname}`)
  }
  ctx.http = {
    CookieJar: class {},
    get: (url, p) => request('GET', url, null, p),
    post: (url, body, p) => request('POST', url, body, p),
    put: (url, body, p) => request('PUT', url, body, p),
    del: (url, body, p) => request('DELETE', url, body, p),
  }
  let source =
    fs
      .readFileSync(root + (geo ? 'geolocation-flood.js' : 'bulk-mark.js'), 'utf8')
      .replace(/^import .*\n/gm, '')
      .replace('export default function (data)', 'function run(data)')
      .replace(/export /g, '') + '\n;globalThis.api = { options, setup, run, teardown };'
  new vm.Script(source).runInContext(ctx)
  return { fixture, ctx, api: ctx.api, calls, checks, metrics, marks, pending, delayed, uncertain }
}
function runCase(name, geo, fault = '', expectedFailure = false) {
  const h = load(geo, fault)
  assert.equal(h.api.options.scenarios[geo ? 'geo' : 'bulk'].executor, 'per-vu-iterations')
  assert.equal(h.api.options.scenarios[geo ? 'geo' : 'bulk'].iterations, 1)
  assert.equal(h.api.options.scenarios[geo ? 'geo' : 'bulk'].maxDuration, geo ? '1m' : '4m')
  assert.equal(h.api.options.setupTimeout, '3m')
  assert(h.api.options.thresholds.checks.includes('rate==1'))
  assert(!h.api.options.systemTags.includes('url'))
  assert(!h.api.options.systemTags.includes('vu'))
  assert(!h.api.options.systemTags.includes('iter'))
  assert(h.api.options.systemTags.includes('name'))
  let data,
    errors = []
  try {
    data = h.api.setup()
  } catch (e) {
    errors.push(e.message)
  }
  if (data) {
    assert(
      !JSON.stringify(data).includes('not-a-real-secret'),
      'setup data must not retain passwords',
    )
    assert(data.actors.every((a) => !('login' in a) && !('password' in a)))
    for (let i = 0; i < data.actors.length; i++) {
      h.ctx.exec.vu.idInTest = i + 1
      try {
        h.api.run(clone(data))
      } catch (e) {
        errors.push(e.message)
      }
    }
    h.api.teardown(clone(data))
  }
  const failed =
    errors.length > 0 || h.checks.some((c) => !c.ok) || h.metrics.cleanup_success.includes(false)
  assert.equal(
    failed,
    expectedFailure,
    `${name}: expected failure=${expectedFailure}, errors=${errors}`,
  )
  if (fault !== 'oldToken') {
    const issued = h.calls.filter(
      (c) =>
        c.path === '/api/auth/login' &&
        !(fault === 'login429' && JSON.parse(c.payload).login === 'synthetic-2'),
    ).length
    assert.equal(
      h.calls.filter((c) => c.path === '/api/auth/logout').length,
      issued,
      `${name}: all issued sessions logout attempted`,
    )
  }
  if (!data)
    assert.equal(
      h.calls.filter(
        (c) =>
          c.path.endsWith('/checkin') || c.path.endsWith('/marks/batch') || c.method === 'DELETE',
      ).length,
      0,
    )
  if (h.uncertain) {
    assert(
      h.metrics.cleanup_success.includes(false),
      'uncertain outcome must keep cleanup failed/UNKNOWN',
    )
    assert.equal(
      h.calls.filter((c) => c.method === 'DELETE').length,
      0,
      'unknown write never justifies deletion',
    )
    assert.equal(h.marks.size, 0, 'immediate/teardown EMPTY snapshot before delayed commit')
    for (const commit of h.delayed) commit()
    assert(h.marks.size > 0, 'remote work can commit after teardown EMPTY/logout')
  }
  if (h.uncertain || fault === 'casRace' || fault === 'preexisting') assert(h.marks.size > 0)
  else assert.equal(h.marks.size, 0, `${name}: attendance cleaned`)
  assert.equal(h.pending.size, 0, `${name}: pending request cancelled`)
  if (!expectedFailure) {
    assert.equal(
      h.metrics.completed_operations.reduce((a, b) => a + b, 0),
      2,
    )
    assert.equal(
      new Set(
        h.calls
          .filter((c) => c.path.endsWith('/checkin') || c.path.endsWith('/marks/batch'))
          .map((c) => c.token),
      ).size,
      2,
    )
  }
  results.push({ name, status: 'PASS', HTTP: 'in-memory-only', calls: h.calls.length })
}
for (const geo of [false, true]) {
  const type = geo ? 'geo' : 'bulk'
  runCase(`${type}: two distinct actors, effect/readback/CAS cleanup/logout`, geo)
  for (const fault of [
    'oldToken',
    'wrongRole',
    'nonHAL',
    'noLesson',
    'incompleteHAL',
    'preexisting',
    'login429',
    'casRace',
    'logout503',
    'noEffect',
  ]) {
    runCase(`${type}: fail closed ${fault}`, geo, fault, true)
  }
  for (const fault of [
    'roleCAS409',
    'malformedSelected',
    'freshWrongRole',
    'timeoutLate',
    'unreadableReadback',
    'unreadableResponse',
  ]) {
    runCase(`${type}: addressed R1/R2 ${fault}`, geo, fault, true)
  }
  assert.throws(() =>
    load(geo, '', (f) => {
      f.actors[1].userId = f.actors[0].userId
    }),
  )
  assert.throws(() =>
    load(geo, '', (f) => {
      f.exclusive = false
    }),
  )
  assert.throws(() => load(geo, '', () => {}, { BASE_URL: '' }))
  assert.throws(() => load(geo, '', () => {}, { BASE_URL: 'https://user:password@mock.invalid' }))
  assert.throws(() => load(geo, '', () => {}, { OWNED_RUN_ID: 'different-run' }))
  results.push({
    name: `${type}: duplicate actor/unowned fixture/missing target/userinfo/run mismatch rejected before HTTP`,
    status: 'PASS',
  })
}
for (const fault of ['wrongProcessed', 'wrongItems', 'batch207'])
  runCase(`bulk: ${fault} fails, owned marks cleaned`, false, fault, true)
runCase(
  'geo: wrong geofence PENDING fails, owned automatic request cancelled',
  true,
  'pending',
  true,
)
console.log(
  JSON.stringify(
    { runtime: process.version, network: 'NONE', count: results.length, results },
    null,
    2,
  ),
)
