/**
 * Один batch на явно заданного synthetic старосту, без autoseed/default target.
 * Только после согласования target и эксклюзивного владения fixtures:
 *   BASE_URL=http://approved-host:8080 OWNED_RUN_ID=unique-run \
 *   FIXTURES_FILE=/absolute/private/bulk.json k6 run tests/load/bulk-mark.js
 * Private JSON (пароли не коммитить): { runId: "unique-run", exclusive: true, actors: [
 *   { login, password, userId, groupId, lessonId, date: "YYYY-MM-DD", studentIds: [id] }
 * ] }. ID — positive safe integers; 1..5 HEADMAN, всего 1..30 клеток.
 * Группа/пара отдельная на actor, клетки изначально пустые и редактируемые;
 * расписание выбранной даты должно помещаться в одну HAL page (size=100).
 * Cleanup: CAS DELETE только собственных PRESENT/headman, readback, logout своих sessions.
 * Audit/events сохраняются. Fixtures одноразовые; при kill/непрочитанном результате write
 * cleanup не гарантирован: teardown сообщает FAIL, не удаляет неизвестную revision.
 * HEADMAN выбирается штатным session active-role CAS после проверки собственного grant.
 * Не использовать --no-setup/--no-teardown/переопределение executor.
 * p95 — инженерный порог; малый run не доказывает ёмкость или VPS SLO.
 * k6 в проекте не pinned/runtime UNKNOWN; minimal API сверены с official v0.49.x:
 * https://grafana.com/docs/k6/v0.49.x/using-k6/test-lifecycle/
 * https://grafana.com/docs/k6/v0.49.x/using-k6/scenarios/executors/per-vu-iterations/
 * https://grafana.com/docs/k6/v0.49.x/using-k6/thresholds/
 */
import http from 'k6/http'
import { check } from 'k6'
import exec from 'k6/execution'
import { Counter, Rate } from 'k6/metrics'

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '')
if (!/^https?:\/\/[^/?#@]+$/.test(BASE_URL) || !__ENV.FIXTURES_FILE) {
  throw new Error('Explicit BASE_URL origin and private FIXTURES_FILE required')
}
const fixture = JSON.parse(open(__ENV.FIXTURES_FILE))
if (!/^[A-Za-z0-9_-]{8,48}$/.test(fixture.runId || '') ||
    fixture.runId !== __ENV.OWNED_RUN_ID || fixture.exclusive !== true ||
    !Array.isArray(fixture.actors) || fixture.actors.length < 1 || fixture.actors.length > 5) {
  throw new Error('Require matching OWNED_RUN_ID, exclusive synthetic fixtures and 1..5 actors')
}
const actors = fixture.actors
const positiveId = (id) => Number.isSafeInteger(id) && id > 0
for (const a of actors) {
  if (![a.userId, a.groupId, a.lessonId].every(positiveId) ||
      typeof a.login !== 'string' || !a.login || typeof a.password !== 'string' || !a.password ||
      !/^\d{4}-\d{2}-\d{2}$/.test(a.date || '') || !Array.isArray(a.studentIds) ||
      a.studentIds.length < 1 || !a.studentIds.every(positiveId) ||
      new Set(a.studentIds).size !== a.studentIds.length) {
    throw new Error('Invalid explicit actor/lesson/student fixture')
  }
}
if (['userId', 'login', 'groupId', 'lessonId'].some((key) =>
  new Set(actors.map((a) => a[key])).size !== actors.length) ||
  actors.reduce((n, a) => n + a.studentIds.length, 0) > 30) {
  throw new Error('Distinct actors/groups required; maximum 30 owned cells in total')
}

const completed = new Counter('completed_operations')
const cleanup = new Rate('cleanup_success')
export const options = {
  scenarios: { bulk: { executor: 'per-vu-iterations', vus: actors.length,
    iterations: 1, maxDuration: '4m', gracefulStop: '10s' } },
  setupTimeout: '3m', teardownTimeout: '2m', maxRedirects: 0,
  // name фиксирован в params; url/vu/iter с идентификаторами не экспортируются.
  systemTags: ['status', 'method', 'name', 'check', 'scenario', 'expected_response', 'error_code'],
  thresholds: {
    checks: ['rate==1'], http_req_failed: ['rate==0'],
    'http_req_duration{endpoint:bulk-mark}': ['p(95)<500'],
    completed_operations: [`count==${actors.length}`], cleanup_success: ['rate==1'],
  },
  tags: { test: 'bulk-mark', scope: 'owned-fixtures' },
}

function requireCheck(ok, label) {
  if (!check(ok, { [label]: (value) => value === true })) throw new Error(label)
}
function params(token, endpoint) {
  return { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    timeout: '5s', redirects: 0, tags: { endpoint, name: endpoint }, jar: new http.CookieJar() }
}
function json200(res, label) {
  requireCheck(res.status === 200, `${label}: HTTP 200`)
  try { return res.json() } catch (_) { requireCheck(false, `${label}: valid JSON`) }
}
function login(a, sessions) {
  const res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ login: a.login, password: a.password }), params('', 'login'))
  const body = json200(res, 'login')
  requireCheck(typeof body.accessToken === 'string' && body.accessToken.length > 0, 'accessToken present')
  const session = { accessToken: body.accessToken }
  sessions.push(session) // Cleanup и при последующем setup failure.
  const desiredRole = 'HEADMAN'
  let current = json200(http.get(`${BASE_URL}/api/auth/session`,
    params(session.accessToken, 'session')), 'session')
  function ownedGrant(snapshot) {
    return snapshot && snapshot.userId === String(a.userId) && snapshot.readOnly === false &&
      typeof snapshot.sessionId === 'string' && snapshot.sessionId.length > 0 &&
      /^[1-9][0-9]*$/.test(snapshot.sessionVersion || '') && Array.isArray(snapshot.roles) &&
      snapshot.roles.some((r) => r.role === desiredRole && r.status === 'ACTIVE' &&
        r.groupId === String(a.groupId) && r.selectable === true && r.readOnly === false)
  }
  requireCheck(ownedGrant(current), 'owned selectable role/group grant')
  if (current.activeRole !== desiredRole) {
    const previous = current
    const selected = json200(http.put(`${BASE_URL}/api/auth/session/active-role`,
      JSON.stringify({ role: desiredRole, expectedSessionVersion: previous.sessionVersion }),
      params(session.accessToken, 'active-role')), 'select active role')
    requireCheck(typeof selected.accessToken === 'string' && selected.accessToken.length > 0,
      'selected accessToken present')
    session.accessToken = selected.accessToken // Logout должен использовать новый sessionVersion.
    requireCheck(ownedGrant(selected.session) && selected.session.activeRole === desiredRole &&
      selected.session.sessionId === previous.sessionId &&
      selected.session.sessionVersion !== previous.sessionVersion &&
      Number.isFinite(selected.expiresIn) && selected.expiresIn > 0, 'selected owned role/session')
    current = json200(http.get(`${BASE_URL}/api/auth/session`,
      params(session.accessToken, 'session')), 'selected fresh session')
    requireCheck(ownedGrant(current) && current.sessionId === selected.session.sessionId &&
      current.sessionVersion === selected.session.sessionVersion, 'selected fresh session matches')
  }
  requireCheck(current.activeRole === desiredRole, 'owned active role/group scope')
  const owned = { ...a }
  delete owned.login
  delete owned.password
  return { ...owned, accessToken: session.accessToken }
}
function report(a) {
  const body = json200(http.get(`${BASE_URL}/api/attendance/reports/lesson/${a.lessonId}`,
    params(a.accessToken, 'attendance-read')), 'attendance report')
  requireCheck(body.lessonId === a.lessonId && body.groupId === a.groupId &&
    body.lessonDate === a.date && body.editable === true && Array.isArray(body.entries),
  'report matches owned editable lesson')
  return a.studentIds.map((id) => {
    const rows = body.entries.filter((row) => row.userId === id)
    requireCheck(rows.length === 1 && rows[0].editable === true, 'owned student in editable roster')
    return rows[0]
  })
}
function empty(row) {
  return row.status === null && row.source === null && row.markRevision === null &&
    row.approvedTicket == null && row.attachmentId == null
}
function schedule(a) {
  const url = `${BASE_URL}/api/schedule/groups/${a.groupId}/lessons` +
    `?dateFrom=${a.date}&dateTo=${a.date}&page=0&size=100`
  const body = json200(http.get(url, params(a.accessToken, 'schedule')), 'schedule')
  const collections = Object.values(body._embedded || {})
  requireCheck(body.page && body.page.totalPages === 1 && collections.length === 1 &&
    Array.isArray(collections[0]), 'schedule is one complete HAL page')
  const matches = collections[0].filter((l) => l.id === a.lessonId && l.groupId === a.groupId &&
    l.date === a.date && l.current === true && ['PLANNED', 'ACTIVE', 'CLOSED'].includes(l.status))
  requireCheck(matches.length === 1, 'explicit current lesson present in schedule HAL')
}
function logout(sessions) {
  for (const s of sessions) {
    const res = http.post(`${BASE_URL}/api/auth/logout`, null, params(s.accessToken, 'logout'))
    const ok = res.status === 204
    cleanup.add(ok)
    check(ok, { 'issued session revoked': (value) => value })
  }
}
export function setup() {
  const sessions = []
  try {
    const ready = actors.map((a) => login(a, sessions))
    requireCheck(new Set(ready.map((a) => a.accessToken)).size === ready.length, 'distinct actor tokens')
    for (const a of ready) {
      schedule(a)
      requireCheck(report(a).every(empty), 'all target cells initially empty')
    }
    return { actors: ready, sessions }
  } catch (error) {
    logout(sessions) // k6 не вызывает teardown, если setup бросил исключение.
    throw error
  }
}
export default function (data) {
  const a = data && data.actors[exec.vu.idInTest - 1]
  requireCheck(Boolean(a), 'one explicit actor per VU')
  const items = a.studentIds.map((userId) => ({ lessonId: a.lessonId, userId, status: 'PRESENT' }))
  let observed = []
  let settled = false
  let readable = false
  let readBack = false
  try {
    const res = http.post(`${BASE_URL}/api/attendance/marks/batch`,
      JSON.stringify({ items }), params(a.accessToken, 'bulk-mark'))
    settled = res.status >= 200 && res.status < 500 && res.status !== 202
    let body
    try { body = res.json(); readable = true } catch (_) { /* Неизвестный исход сохраняется. */ }
    observed = report(a) // Только этот postwrite snapshot может дать revision для CAS.
    readBack = true
    requireCheck(res.status === 200, 'batch: HTTP 200')
    requireCheck(readable, 'batch: valid JSON')
    requireCheck(body.processed === items.length && Array.isArray(body.items) &&
      body.items.length === items.length && body.items.every((row, i) =>
        row.lessonId === a.lessonId && row.userId === items[i].userId && row.status === 'PRESENT' &&
        typeof row.timestamp === 'string' && Number.isFinite(Date.parse(row.timestamp))),
    'batch processed exact persisted items')
    requireCheck(observed.every((row) => row.status === 'present' && row.source === 'headman' &&
      typeof row.markRevision === 'string' && row.markRevision.length > 0), 'batch effect read back')
    completed.add(1)
  } finally {
    // EMPTY не доказывает завершение запроса: transport/5xx/202 или непрочитанный
    // outcome/readback остаются UNKNOWN даже после пустого teardown snapshot.
    const cleanupKnown = settled && readable && readBack
    if (!cleanupKnown) cleanup.add(false)
    for (const row of cleanupKnown ? observed : []) {
      if (empty(row)) continue
      try {
        requireCheck(row.status === 'present' && row.source === 'headman' &&
          /^[A-Za-z0-9_-]{1,128}$/.test(row.markRevision || '') && row.markRevision !== 'EMPTY',
        'cleanup refuses changed or foreign mark')
        const res = http.del(`${BASE_URL}/api/attendance/lessons/${a.lessonId}/students/${row.userId}` +
          `?expectedStatus=PRESENT&expectedRevision=${encodeURIComponent(row.markRevision)}`,
        null, params(a.accessToken, 'cleanup-mark'))
        requireCheck(res.status === 204, 'owned revision cleared')
        cleanup.add(true)
      } catch (_) { cleanup.add(false) }
    }
  }
}
export function teardown(data) {
  try {
    for (const a of data.actors) {
      try {
        requireCheck(report(a).every(empty), 'cleanup effect read back')
        cleanup.add(true)
      } catch (_) { cleanup.add(false) }
    }
  } finally { logout(data.sessions) }
}
