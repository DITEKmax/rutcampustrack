// Root-only localhost acceptance after review and runtime update. No lifecycle operations.
// node scripts/local-geo-retry-acceptance.mjs prepare --lesson-number 7 --start-time 18:30 --end-time 19:50
// node scripts/local-geo-retry-acceptance.mjs start
// node scripts/local-geo-retry-acceptance.mjs finish --geofence-confirmed
// The root supplies ONE approved server slot; this runner owns no timetable.
import https from 'node:https';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { isDeepStrictEqual } from 'node:util';

const ORIGIN = 'https://localhost:18514';
const SCHEMA = 'rct.geo-retry-acceptance.v1';
const UNAVAILABLE = Object.freeze({ geo: { kind: 'UNAVAILABLE', reason: 'TIMEOUT' } });
const COORDINATES = Object.freeze({ geo: { kind: 'COORDINATES', latitude: 55.788204, longitude: 37.606762 } });
const PHASES = ['prepare', 'start', 'finish'];
let criterion = 'input';
let httpStatus = 0;
const checks = [];

function requireThat(condition) { if (!condition) throw new Error('criterion-failed'); }
function complete(id) { checks.push({ criterion: id, status: 'PASS' }); }
export function objectId(value) {
  requireThat(/^[1-9][0-9]*$/.test(String(value)) && Number.isSafeInteger(Number(value)));
  return String(value);
}
function instant(value) {
  requireThat(typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T.*Z$/.test(value)
    && Number.isFinite(Date.parse(value)));
  return Date.parse(value);
}
function requestId(value) { requireThat(typeof value === 'string' && /^[\x21-\x7e]{1,128}$/.test(value)); return value; }
function key(value) { requireThat(typeof value === 'string' && /^[\x21-\x7e]{16,128}$/.test(value)); return value; }
function moscowDate(now) { return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Moscow' }).format(now); }
function normalizedTime(value) {
  requireThat(typeof value === 'string' && /^(?:[01]\d|2[0-3]):[0-5]\d(?::00)?$/.test(value));
  return value.slice(0, 5);
}
export function publicArgs(args) {
  const phase = args[0];
  requireThat(PHASES.includes(phase));
  if (phase === 'prepare') {
    requireThat(args.length === 7 && args[1] === '--lesson-number' && /^[1-8]$/.test(args[2])
      && args[3] === '--start-time' && args[5] === '--end-time');
    return { phase, geoRetry: { slotApproved: true, slot: {
      lessonNumber: Number(args[2]), startTime: normalizedTime(args[4]), endTime: normalizedTime(args[6]),
    } } };
  }
  requireThat((phase === 'start' && args.length === 1)
    || (phase === 'finish' && args.length === 2 && args[1] === '--geofence-confirmed'));
  return { phase, geoRetry: { slotApproved: true, geofenceConfirmed: phase === 'finish' } };
}
export function approvedSlot(input) {
  const approval = input?.geoRetry;
  requireThat(approval?.slotApproved === true);
  const slot = approval.slot;
  requireThat(Number.isInteger(slot?.lessonNumber) && slot.lessonNumber >= 1 && slot.lessonNumber <= 8);
  const startTime = normalizedTime(slot.startTime);
  const endTime = normalizedTime(slot.endTime);
  requireThat(startTime < endTime);
  return { lessonNumber: slot.lessonNumber, startTime, endTime };
}
export function inWindow(date, slot, now, minimumRemainingMs = 0) {
  requireThat(/^\d{4}-\d{2}-\d{2}$/.test(date));
  const opens = Date.parse(`${date}T${slot.startTime}:00+03:00`) - 300_000;
  const closes = Date.parse(`${date}T${slot.endTime}:00+03:00`) + 300_000;
  return now >= opens && closes - now >= minimumRemainingMs;
}
export function rows(body) {
  if (Array.isArray(body)) return body;
  if (Array.isArray(body?.items)) return body.items;
  if (Array.isArray(body?.content)) return body.content;
  const arrays = Object.values(body?._embedded ?? {}).filter(Array.isArray);
  // Empty HAL pages can omit _embedded entirely, but must advertise zero rows.
  if (arrays.length === 0 && body?.page?.totalElements === 0) return [];
  requireThat(arrays.length === 1);
  return arrays[0];
}
function fixtureIdentity(fixture) {
  requireThat(fixture?.schema === 'rct.local-stand-acceptance.v1'
    && /^[a-f0-9-]{36}$/.test(fixture.marker)
    && ['prepared', 'checked-before', 'checked-after'].includes(fixture.phase));
  const identity = { marker: fixture.marker, groupId: objectId(fixture.group?.id),
    semesterId: objectId(fixture.semester?.id), subjectId: objectId(fixture.subject?.id),
    assignmentId: objectId(fixture.subject?.assignmentId), assistantId: objectId(fixture.accounts?.Помощник?.id) };
  for (const name of ['Староста', 'Помощник']) {
    const account = fixture.accounts[name];
    requireThat(account.role === 'STUDENT' && objectId(account.groupId) === identity.groupId
      && typeof account.login === 'string' && account.login.length > 0 && account.login.length <= 256
      && typeof account.password === 'string' && account.password.length > 0 && account.password.length <= 512);
  }
  return identity;
}
export function todayLesson(body, journal) {
  requireThat(body?.timeZone === 'Europe/Moscow' && body.date === journal.date && Array.isArray(body.lessons));
  instant(body.serverNow);
  const matching = body.lessons.filter((row) => String(row.schedule?.id) === journal.lessonId);
  requireThat(matching.length === 1);
  const lesson = matching[0];
  requireThat(lesson.schedule.date === journal.date && lesson.schedule.lessonNumber === journal.slot.lessonNumber
    && normalizedTime(lesson.schedule.startsAt) === journal.slot.startTime
    && normalizedTime(lesson.schedule.endsAt) === journal.slot.endTime && ['PLANNED', 'ACTIVE'].includes(lesson.schedule.status));
  return lesson;
}
export function pendingAck(ack, journal) {
  requireThat(ack?.lessonId === journal.lessonId && ack.outcome === 'PENDING_CONFIRMATION' && ack.attendance == null
    && ack.request?.status === 'PENDING' && ack.request.origin === 'AUTO_GEO_FAILURE'
    && ack.request.resolutionReason == null);
  requestId(ack.request.id);
  requireThat(instant(ack.retryAt) - instant(ack.serverNow) === 300_000);
}
function pendingToday(body, journal, reason) {
  const lesson = todayLesson(body, journal);
  requireThat(lesson.attendance == null && lesson.request?.id === journal.ack.request.id
    && lesson.request.status === 'PENDING' && lesson.request.origin === 'AUTO_GEO_FAILURE'
    && lesson.request.resolutionReason == null && lesson.checkinEligibility?.reason === reason
    && lesson.checkinEligibility.allowed === (reason === 'ELIGIBLE'));
  if (reason === 'COOLDOWN') requireThat(lesson.checkinEligibility.retryAt === journal.ack.retryAt);
}
function presentAck(ack, journal) {
  requireThat(ack?.lessonId === journal.lessonId && ack.outcome === 'PRESENT'
    && ack.attendance?.status === 'PRESENT' && ack.attendance.source === 'STUDENT_GEO'
    && ack.request == null && ack.retryAt == null);
  instant(ack.serverNow); instant(ack.attendance.markedAt);
}
function finalToday(body, journal) {
  const lesson = todayLesson(body, journal);
  requireThat(lesson.attendance?.status === 'PRESENT' && lesson.attendance.source === 'STUDENT_GEO'
    && lesson.request?.id === journal.ack.request.id && lesson.request.status === 'CANCELLED'
    && lesson.request.origin === 'AUTO_GEO_FAILURE' && lesson.request.resolutionReason === 'GEO_CONFIRMED'
    && lesson.checkinEligibility?.allowed === false && lesson.checkinEligibility.reason === 'ALREADY_PRESENT');
}

// Injectable bounded I/O permits source checks with synthetic data and zero HTTP/private access.
export async function run({ phase, input, fixture, journal, save, send, now = () => Date.now() }) {
  requireThat(PHASES.includes(phase));
  const identity = fixtureIdentity(fixture);
  const slot = approvedSlot(input);
  const date = moscowDate(new Date(now()));
  const result = (status, seconds) => ({ schema: SCHEMA, phase, status, checks,
    ...(seconds === undefined ? {} : { waitPending: true, retryRemainingSeconds: seconds }) });
  const checkpoint = (value) => { journal.checkpoint = value; save(journal); };
  const api = async (id, method, url, auth, body, statuses = [200], idempotencyKey) => {
    criterion = id; httpStatus = 0;
    if (idempotencyKey !== undefined) key(idempotencyKey);
    const response = await send({ method, url, auth, body, statuses, idempotencyKey });
    httpStatus = response.status;
    requireThat(statuses.includes(response.status));
    return response.body;
  };
  const login = async (name, selected) => {
    const account = fixture.accounts[name];
    const response = await api('auth.login', 'POST', '/api/auth/login', null,
      { login: account.login, password: account.password });
    requireThat(typeof response?.accessToken === 'string' && response.accessToken.length > 0);
    let auth = { token: response.accessToken };
    const session = await api('auth.session', 'GET', '/api/auth/session', auth);
    requireThat(typeof session?.sessionVersion === 'string');
    const selectedSession = await api('auth.active-role', 'PUT', '/api/auth/session/active-role', auth,
      { role: selected, expectedSessionVersion: session.sessionVersion });
    requireThat(typeof selectedSession?.accessToken === 'string' && selectedSession.accessToken.length > 0
      && selectedSession.session?.activeRole === selected);
    auth = { token: selectedSession.accessToken };
    const canonical = await api('auth.canonical', 'GET', '/api/auth/session', auth);
    requireThat(canonical?.activeRole === selected && canonical.sessionVersion === selectedSession.session.sessionVersion);
    return auth;
  };
  const schedule = (auth) => api('fixture.schedule', 'GET',
    `/api/schedule/groups/${identity.groupId}/lessons?dateFrom=${date}&dateTo=${date}&page=0&size=100&status=PLANNED,ACTIVE,CLOSED,CANCELLED,TRANSFERRED`, auth);
  if (phase === 'prepare') {
    criterion = 'prepare.fresh-journal'; requireThat(journal === undefined);
    if (!inWindow(date, slot, now(), 360_000)) return result('BLOCKED');
    const headman = await login('Староста', 'HEADMAN');
    const response = await schedule(headman);
    const lessons = rows(response);
    requireThat(lessons.length <= 100 && (response.page === undefined
      || (Number.isInteger(response.page.totalElements) && response.page.totalElements <= 100
        && response.page.totalPages <= 1)));
    for (const lesson of lessons) {
      requireThat(String(lesson.groupId) === identity.groupId && lesson.date === date
        && Number.isInteger(lesson.lessonNumber) && lesson.lessonNumber >= 1 && lesson.lessonNumber <= 8);
    }
    if (lessons.some((lesson) => lesson.lessonNumber === slot.lessonNumber)
      || !inWindow(date, slot, now(), 360_000)) return result('BLOCKED');
    journal = { schema: SCHEMA, identity, date, slot, keys: { create: randomUUID(), k1: randomUUID(),
      k2: randomUUID(), k3: randomUUID() }, checkpoint: 'create-inflight' };
    save(journal); // Persist BEFORE the only non-resumable mutation. Never automatically retry it.
    const lesson = await api('fixture.create', 'POST', '/api/schedule/one-off-lessons', headman, {
      groupId: Number(identity.groupId), subjectId: Number(identity.subjectId), assignmentId: Number(identity.assignmentId),
      date, lessonNumber: slot.lessonNumber, startTime: slot.startTime, endTime: slot.endTime, classroom: 'geo-retry',
    }, [201], journal.keys.create);
    journal.lessonId = objectId(lesson?.physicalLessonId);
    // Save returned physical ID before verification: failure cannot trigger a second create.
    checkpoint('created');
    requireThat(String(lesson.groupId) === identity.groupId && String(lesson.subjectId) === identity.subjectId
      && String(lesson.semesterId) === identity.semesterId && lesson.date === date
      && lesson.lessonNumber === slot.lessonNumber && lesson.classroom === 'geo-retry');
    const reread = rows(await schedule(headman)).filter((row) => String(row.id) === journal.lessonId);
    requireThat(reread.length === 1 && reread[0].lessonNumber === slot.lessonNumber
      && normalizedTime(reread[0].startTime) === slot.startTime && normalizedTime(reread[0].endTime) === slot.endTime
      && ['PLANNED', 'ACTIVE'].includes(reread[0].status) && !reread[0].geoBlocked && !reread[0].blockedByHeadman);
    checkpoint('prepared'); complete('fixture.fixed-slot'); return result('PASS');
  }
  criterion = 'journal.scope';
  requireThat(journal?.schema === SCHEMA && isDeepStrictEqual(journal.identity, identity)
    && journal.date === date && isDeepStrictEqual(journal.slot, slot));
  objectId(journal.lessonId);
  requireThat(journal.keys && Object.values(journal.keys).length === 4
    && new Set(Object.values(journal.keys)).size === 4);
  Object.values(journal.keys).forEach(key);
  requireThat(['prepared', 'k1-inflight', 'k1-accepted', 'k2-inflight', 'cooldown-checked',
    'waiting', 'k3-inflight', 'present', 'replayed', 'finished'].includes(journal.checkpoint));
  const student = await login('Помощник', 'STUDENT');
  const session = await api('student.session', 'GET', '/api/v1/student/session', student);
  requireThat(String(session.group?.id) === identity.groupId && String(session.semester?.id) === identity.semesterId);
  const getToday = (id) => api(id, 'GET', '/api/v1/student/today', student);
  const checkin = (id, action, body, statuses) => api(id, 'POST',
    `/api/v1/student/lessons/${journal.lessonId}/checkin`, student, body, statuses, journal.keys[action]);
  if (phase === 'start') {
    requireThat(['prepared', 'k1-inflight', 'k1-accepted', 'k2-inflight', 'cooldown-checked', 'waiting'].includes(journal.checkpoint));
    if (journal.checkpoint === 'prepared') {
      const baseline = await getToday('student.baseline');
      const lesson = todayLesson(baseline, journal);
      requireThat(lesson.attendance == null && lesson.request == null && lesson.checkinEligibility?.allowed === true
        && lesson.checkinEligibility.reason === 'ELIGIBLE');
      if (!inWindow(date, slot, instant(baseline.serverNow), 360_000)) return result('BLOCKED');
      complete('student.baseline'); checkpoint('k1-inflight');
    }
    if (journal.checkpoint === 'k1-inflight') {
      // An explicit rerun may resend this SAME action/body/key; no internal transport retry.
      journal.ack = await checkin('student.unavailable', 'k1', UNAVAILABLE, [200]);
      pendingAck(journal.ack, journal); checkpoint('k1-accepted'); complete('student.pending-ack');
    }
    pendingAck(journal.ack, journal);
    if (['k1-accepted', 'k2-inflight'].includes(journal.checkpoint)) {
      const before = await getToday('student.before-cooldown');
      pendingToday(before, journal, 'COOLDOWN');
      requireThat(instant(before.serverNow) < instant(journal.ack.retryAt));
      checkpoint('k2-inflight');
      const problem = await checkin('student.cooldown', 'k2', UNAVAILABLE, [429]);
      requireThat(problem?.status === 429 && problem.code === 'CHECKIN_COOLDOWN' && problem.retryAt === journal.ack.retryAt);
      checkpoint('cooldown-checked'); complete('student.cooldown-429');
    }
    const after = await getToday('student.pending-today');
    pendingToday(after, journal, 'COOLDOWN');
    checkpoint('waiting'); complete('student.pending-today');
    return result('PASS', Math.max(0, Math.ceil((instant(journal.ack.retryAt) - instant(after.serverNow)) / 1000)));
  }
  criterion = 'finish.geofence-approved';
  requireThat(input.geoRetry.geofenceConfirmed === true);
  requireThat(['waiting', 'k3-inflight', 'present', 'replayed', 'finished'].includes(journal.checkpoint));
  pendingAck(journal.ack, journal);
  if (journal.checkpoint === 'waiting') {
    const before = await getToday('student.retry-window');
    todayLesson(before, journal);
    const remaining = Math.ceil((instant(journal.ack.retryAt) - instant(before.serverNow)) / 1000);
    if (remaining > 0) { pendingToday(before, journal, 'COOLDOWN'); return result('WAIT_PENDING', remaining); }
    pendingToday(before, journal, 'ELIGIBLE');
    requireThat(inWindow(date, slot, instant(before.serverNow)));
    checkpoint('k3-inflight');
  }
  if (journal.checkpoint === 'k3-inflight') {
    journal.presentAck = await checkin('student.coordinates', 'k3', COORDINATES, [200]);
    presentAck(journal.presentAck, journal); checkpoint('present'); complete('student.geo-present');
  }
  presentAck(journal.presentAck, journal);
  if (journal.checkpoint === 'present') {
    const replay = await checkin('student.original-replay', 'k1', UNAVAILABLE, [200]);
    requireThat(isDeepStrictEqual(replay, journal.ack));
    checkpoint('replayed'); complete('student.original-ack-unchanged');
  }
  finalToday(await getToday('student.final-today'), journal);
  checkpoint('finished'); complete('student.geo-confirmed-today'); return result('PASS');
}

function canonical(filename, directory = false) {
  requireThat(path.isAbsolute(filename) && path.resolve(filename) === filename);
  for (let current = filename; ; current = path.dirname(current)) {
    requireThat(!fs.lstatSync(current).isSymbolicLink() && fs.realpathSync(current) === current);
    if (path.dirname(current) === current) break;
  }
  requireThat(directory ? fs.lstatSync(filename).isDirectory() : fs.lstatSync(filename).isFile());
  return filename;
}
export function assertPrivateAcl(directory) {
  // Validate metadata only. Never print PowerShell/native errors or file contents.
  const previousCriterion = criterion;
  criterion = 'private-acl';
  // Windows PowerShell cannot autoload its Security module from an inherited Core PSModulePath.
  const command = "$ErrorActionPreference='Stop'; try { Import-Module ([IO.Path]::Combine($PSHOME,'Modules','Microsoft.PowerShell.Security','Microsoft.PowerShell.Security.psd1')) -ErrorAction Stop; $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value; $d=$env:RCT_GEO_PRIVATE_DIRECTORY; foreach($p in @($d,(Join-Path $d 'acceptance-state.json'),(Join-Path $d 'acceptance-input.json'),(Join-Path $d 'geo-retry-journal.json'),(Join-Path $d 'geo-retry-journal.tmp'),(Join-Path $d 'geo-retry.lock'))) { if(Test-Path -LiteralPath $p) { $a=Get-Acl -LiteralPath $p; if($a.GetOwner([Security.Principal.SecurityIdentifier]).Value -cne $sid -or ($p -ceq $d -and -not $a.AreAccessRulesProtected) -or @($a.Access | Where-Object { $_.AccessControlType -eq 'Allow' -and $_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -cne $sid }).Count) { exit 1 } } }; exit 0 } catch { exit 1 }";
  const result = spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', command], {
    env: { ...process.env, RCT_GEO_PRIVATE_DIRECTORY: directory }, stdio: 'ignore', timeout: 10_000, windowsHide: true,
  });
  requireThat(result.status === 0);
  criterion = previousCriterion;
}
async function main() {
  const approval = publicArgs(process.argv.slice(2));
  requireThat(process.platform === 'win32'
    && typeof process.env.LOCALAPPDATA === 'string');
  const directory = path.resolve(process.env.LOCALAPPDATA, 'RutCampusTrack', 'local-stand');
  const repository = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const relative = path.relative(repository, directory);
  requireThat(relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative));
  canonical(directory, true); assertPrivateAcl(directory);
  const privateFile = (name, existing = true) => {
    const filename = path.join(directory, name);
    if (existing || fs.existsSync(filename)) canonical(filename);
    return filename;
  };
  const input = JSON.parse(fs.readFileSync(privateFile('acceptance-input.json'), 'utf8'));
  requireThat(typeof input.caPath === 'string' && input.caPath === path.resolve(process.env.LOCALAPPDATA, 'mkcert', 'rootCA.pem'));
  const ca = fs.readFileSync(canonical(input.caPath));
  const fixture = JSON.parse(fs.readFileSync(privateFile('acceptance-state.json'), 'utf8'));
  const journalPath = privateFile('geo-retry-journal.json', false);
  const temporary = privateFile('geo-retry-journal.tmp', false);
  const lockPath = privateFile('geo-retry.lock', false);
  const lock = fs.openSync(lockPath, 'wx', 0o600);
  let agent;
  let outcome;
  try {
    assertPrivateAcl(directory);
    const journal = fs.existsSync(journalPath) ? JSON.parse(fs.readFileSync(journalPath, 'utf8')) : undefined;
    if (approval.phase !== 'prepare') approval.geoRetry.slot = journal?.slot;
    const save = (value) => {
      privateFile('geo-retry-journal.json', false); privateFile('geo-retry-journal.tmp', false);
      assertPrivateAcl(directory);
      fs.writeFileSync(temporary, JSON.stringify(value), { flag: 'wx', mode: 0o600 });
      assertPrivateAcl(directory); fs.renameSync(temporary, journalPath);
    };
    agent = new https.Agent({ ca, rejectUnauthorized: true, keepAlive: true });
    let lastLoginAt = 0;
    const send = async ({ method, url, auth, body, statuses, idempotencyKey }) => {
      requireThat(url.startsWith('/api/') && !url.startsWith('//'));
      const target = new URL(url, ORIGIN); requireThat(target.origin === ORIGIN);
      if (url === '/api/auth/login') {
        const wait = 13_000 - (Date.now() - lastLoginAt);
        if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
        lastLoginAt = Date.now();
      }
      const payload = body === undefined ? undefined : Buffer.from(JSON.stringify(body));
      const headers = { Accept: 'application/json' };
      if (auth) headers.Authorization = `Bearer ${auth.token}`;
      if (idempotencyKey) headers['Idempotency-Key'] = key(idempotencyKey);
      if (payload) { headers['Content-Type'] = 'application/json'; headers['Content-Length'] = payload.length; }
      const response = await new Promise((resolve, reject) => {
        const req = https.request(target, { method, headers, agent }, (res) => {
          const parts = []; let size = 0;
          res.on('error', () => reject(new Error('transport-failed')));
          res.on('data', (part) => { size += part.length; if (size > 2 * 1024 * 1024) res.destroy(new Error('bounded-response-exceeded')); else parts.push(part); });
          res.on('end', () => resolve({ status: res.statusCode, data: Buffer.concat(parts) }));
        });
        req.once('error', () => reject(new Error('transport-failed')));
        req.setTimeout(30_000, () => req.destroy(new Error('timeout'))); req.end(payload);
      });
      httpStatus = response.status;
      requireThat(statuses.includes(response.status)); // Never parse unexpected error bodies or follow redirects.
      const parsed = JSON.parse(response.data.toString('utf8'));
      return { status: response.status, body: response.status === 429
        ? { status: parsed.status, code: parsed.code, retryAt: parsed.retryAt } : parsed };
    };
    outcome = await run({ phase: approval.phase, input: approval, fixture, journal, save, send });
  } finally {
    agent?.destroy(); fs.closeSync(lock); fs.unlinkSync(lockPath);
  }
  process.stdout.write(`${JSON.stringify(outcome)}\n`);
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(() => {
    // Only fixed criterion names and HTTP status; never errors, IDs, credentials, URLs or raw bodies.
    process.stdout.write(`${JSON.stringify({ schema: SCHEMA, status: 'FAIL', criterion, httpStatus, checks })}\n`);
    process.exitCode = 1;
  });
}
