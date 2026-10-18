/**
 * Каноническая геоотметка: один отдельный synthetic STUDENT на VU, одна попытка.
 * Старый /api/attendance/checkin retired (410); случайные координаты не используются.
 * Только после согласования target/эксклюзивного владения данными:
 *   BASE_URL=http://approved-host:8080 OWNED_RUN_ID=unique-run \
 *   FIXTURES_FILE=/absolute/private/geo.json k6 run tests/load/geolocation-flood.js
 * Private JSON (пароли не коммитить): { runId: "unique-run", exclusive: true,
 *   observer: { login, password, userId, groupId }, actors: [
 *     { login, password, userId, groupId, lessonId, date: "YYYY-MM-DD", latitude, longitude }
 *   ] }. ID — positive safe integers; 1..4 отдельных STUDENT и один HEADMAN observer.
 * Все actors в группе observer, координаты внутри реально настроенной геозоны,
 * активная пара в разрешённом сервером окне, без отметок/заявок. Fixtures одноразовые.
 * Расписание выбранной даты должно помещаться в одну HAL page (size=100).
 * Общий observer token используется только для report/cleanup, checkin — своим student token.
 * CAS cleanup снимает только наблюдённую PRESENT/student_geo revision, отменяет только
 * созданные для своей пары AUTO_GEO_FAILURE requests и отзывает свои sessions.
 * Receipts/pair-state/audit/history НЕ удаляются; cooldown 300s, TTL receipts не заявлен.
 * При kill/непрочитанном результате write данные могут остаться: cleanup FAIL, без DB reset.
 * HEADMAN выбирается штатным session active-role CAS после проверки собственного grant.
 * Не использовать --no-setup/--no-teardown/переопределение executor.
 * Это ограниченный сценарий, не 50 независимых пользователей и не доказательство VPS SLO.
 * k6 не pinned/runtime UNKNOWN; minimal API сверены с official v0.49.x:
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
    fixture.runId !== __ENV.OWNED_RUN_ID || fixture.exclusive !== true || !fixture.observer ||
    !Array.isArray(fixture.actors) || fixture.actors.length < 1 || fixture.actors.length > 4) {
  throw new Error('Require matching OWNED_RUN_ID, exclusive fixtures, observer and 1..4 students')
}
const actors = fixture.actors
const positiveId = (id) => Number.isSafeInteger(id) && id > 0
for (const a of [fixture.observer, ...actors]) {
  if (![a.userId, a.groupId].every(positiveId) ||
      typeof a.login !== 'string' || !a.login || typeof a.password !== 'string' || !a.password ||
      a.groupId !== fixture.observer.groupId) throw new Error('Invalid explicit actor fixture')
}
for (const a of actors) {
  if (!positiveId(a.lessonId) || !/^\d{4}-\d{2}-\d{2}$/.test(a.date || '') ||
      !Number.isFinite(a.latitude) || Math.abs(a.latitude) > 90 ||
      !Number.isFinite(a.longitude) || Math.abs(a.longitude) > 180) {
    throw new Error('Explicit owned lesson/date and in-fence coordinates required')
  }
}
for (const key of ['userId', 'login']) {
  if (new Set([fixture.observer, ...actors].map((a) => a[key])).size !== actors.length + 1) {
    throw new Error('Students and observer must be distinct owned actors')
  }
}

const completed = new Counter('completed_operations')
const cleanup = new Rate('cleanup_success')
export const options = {
  scenarios: { geo: { executor: 'per-vu-iterations', vus: actors.length,
    iterations: 1, maxDuration: '1m', gracefulStop: '10s' } },
  setupTimeout: '3m', teardownTimeout: '2m', maxRedirects: 0,
  // name фиксирован в params; url/vu/iter с идентификаторами не экспортируются.
  systemTags: ['status', 'method', 'name', 'check', 'scenario', 'expected_response', 'error_code'],
  thresholds: {
    checks: ['rate==1'], http_req_failed: ['rate==0'],
    'http_req_duration{endpoint:checkin}': ['p(95)<1000'],
    completed_operations: [`count==${actors.length}`], cleanup_success: ['rate==1'],
  },
  tags: { test: 'geolocation-flood', scope: 'owned-fixtures' },
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
function login(a, sessions, role) {
  const res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ login: a.login, password: a.password }), params('', 'login'))
  const body = json200(res, 'login')
  requireCheck(typeof body.accessToken === 'string' && body.accessToken.length > 0, 'accessToken present')
  const session = { accessToken: body.accessToken }
  sessions.push(session) // Cleanup и при последующем setup failure.
  const desiredRole = role
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
    params(a.observerToken, 'attendance-read')), 'attendance report')
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
  const body = json200(http.get(url, params(a.observerToken, 'schedule')), 'schedule')
  const collections = Object.values(body._embedded || {})
  requireCheck(body.page && body.page.totalPages === 1 && collections.length === 1 &&
    Array.isArray(collections[0]), 'schedule is one complete HAL page')
  const matches = collections[0].filter((l) => l.id === a.lessonId && l.groupId === a.groupId &&
    l.date === a.date && l.current === true && l.status === 'ACTIVE' && l.geoBlocked === false && l.blockedByHeadman === false)
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
function requests(a, bucket = 'OPEN') {
  const body = json200(http.get(`${BASE_URL}/api/v1/student/requests?bucket=${bucket}&page=0&size=50`,
    params(a.accessToken, 'student-requests')), 'student requests')
  requireCheck(Array.isArray(body.content) && body.page === 0 &&
    Number.isInteger(body.totalPages) && body.totalPages >= 0 && body.totalPages <= 1 &&
    body.totalElements === body.content.length, 'complete bounded request page')
  return body.content
}
export function setup() {
  const sessions = []
  try {
    const observer = login(fixture.observer, sessions, 'HEADMAN')
    const ready = actors.map((a) => ({ ...login(a, sessions, 'STUDENT'),
      observerToken: observer.accessToken, studentIds: [a.userId] }))
    requireCheck(new Set(sessions.map((s) => s.accessToken)).size === sessions.length, 'distinct actor tokens')
    for (const a of ready) {
      schedule(a)
      requireCheck(report(a).every(empty), 'all target cells initially empty')
      requireCheck(requests(a).length === 0 && requests(a, 'ARCHIVE').length === 0,
        'one-use student has no existing requests')
    }
    return { actors: ready, sessions }
  } catch (error) {
    logout(sessions) // При setup failure k6 не вызывает teardown.
    throw error
  }
}
export default function (data) {
  const a = data && data.actors[exec.vu.idInTest - 1]
  requireCheck(Boolean(a), 'one explicit student per VU')
  const requestParams = params(a.accessToken, 'checkin')
  requestParams.headers['Idempotency-Key'] = `k6-${fixture.runId}-${a.userId}-${a.lessonId}`
  let observed = []
  let settled = false
  let readable = false
  let readBack = false
  try {
    const res = http.post(`${BASE_URL}/api/v1/student/lessons/${a.lessonId}/checkin`,
      JSON.stringify({ geo: { kind: 'COORDINATES', latitude: a.latitude, longitude: a.longitude } }),
      requestParams)
    settled = res.status >= 200 && res.status < 500 && res.status !== 202
    let body
    try { body = res.json(); readable = true } catch (_) { /* Неизвестный исход сохраняется. */ }
    observed = report(a) // Только этот postwrite snapshot может дать revision для CAS.
    readBack = true
    requireCheck(res.status === 200, 'checkin: HTTP 200')
    requireCheck(readable, 'checkin: valid JSON')
    requireCheck(body.lessonId === String(a.lessonId) && body.outcome === 'PRESENT' &&
      body.attendance && body.attendance.status === 'PRESENT' && body.attendance.source === 'STUDENT_GEO' &&
      typeof body.attendance.markedAt === 'string' && Number.isFinite(Date.parse(body.attendance.markedAt)) &&
      body.request === null && body.retryAt === null && typeof body.serverNow === 'string' &&
      Number.isFinite(Date.parse(body.serverNow)), 'canonical checkin PRESENT effect')
    requireCheck(observed.every((row) => row.status === 'present' && row.source === 'student_geo' &&
      typeof row.markRevision === 'string' && row.markRevision.length > 0), 'student geo effect read back')
    completed.add(1)
  } finally {
    // EMPTY не доказывает завершение запроса: transport/5xx/202 или непрочитанный
    // outcome/readback остаются UNKNOWN даже после пустого teardown snapshot.
    const cleanupKnown = settled && readable && readBack
    if (!cleanupKnown) cleanup.add(false)
    for (const row of cleanupKnown ? observed : []) {
      if (empty(row)) continue
      try {
        requireCheck(row.status === 'present' && row.source === 'student_geo' &&
          /^[A-Za-z0-9_-]{1,128}$/.test(row.markRevision || '') && row.markRevision !== 'EMPTY',
        'cleanup refuses changed or foreign mark')
        const res = http.del(`${BASE_URL}/api/attendance/lessons/${a.lessonId}/students/${row.userId}` +
          `?expectedStatus=PRESENT&expectedRevision=${encodeURIComponent(row.markRevision)}`,
        null, params(a.observerToken, 'cleanup-mark'))
        requireCheck(res.status === 204, 'owned revision cleared')
        cleanup.add(true)
      } catch (_) { cleanup.add(false) }
    }
    // Неверная геозона может дать PENDING_CONFIRMATION: FAIL остаётся, request отменяется.
    if (cleanupKnown) try {
      for (const request of requests(a)) {
        requireCheck(request.kind === 'LATE_CHECKIN' && request.origin === 'AUTO_GEO_FAILURE' &&
          request.status === 'PENDING' && /^[0-9a-f]{24}$/.test(request.id || '') &&
          Array.isArray(request.lessons) && request.lessons.length === 1 &&
          request.lessons[0].id === String(a.lessonId), 'cleanup only owned automatic request')
        const body = json200(http.post(`${BASE_URL}/api/v1/student/requests/${request.id}/cancel`,
          null, params(a.accessToken, 'cleanup-request')), 'cancel request')
        requireCheck(body.summary && body.summary.id === request.id &&
          body.summary.status === 'CANCELLED', 'owned automatic request cancelled')
      }
      cleanup.add(true)
    } catch (_) { cleanup.add(false) }
  }
}
export function teardown(data) {
  try {
    for (const a of data.actors) {
      try {
        requireCheck(report(a).every(empty) && requests(a).length === 0, 'cleanup effect read back')
        cleanup.add(true)
      } catch (_) { cleanup.add(false) }
    }
  } finally { logout(data.sessions) }
}
