// Bounded localhost acceptance only. Run after the root grants the runtime lease.
// node scripts/local-stand-acceptance.mjs prepare|resume-prepare|check-before|check-after
// The stand wrapper must first secure LOCALAPPDATA/RutCampusTrack/local-stand
// with a user-only ACL. Root supplies acceptance-input.json: { seed:{login,password},caPath }.
// API credentials and fixture identities stay in that directory; stdout is sanitized.
import https from 'node:https';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID, randomBytes } from 'node:crypto';

const ORIGIN = 'https://localhost:18514';
const SCHEMA = 'rct.local-stand-acceptance.v1';
const ACCOUNT_KEYS = ['Админ', 'Преподаватель', 'Староста', 'Помощник'];
const checks = [];
let criterion = 'input';
let httpStatus = 0;
let agent;
let lastLoginAt = 0;
let state;
let statePath;

function requireThat(condition) {
  if (!condition) throw new Error('criterion-failed');
}
function objectId(value) {
  requireThat(/^[1-9][0-9]*$/.test(String(value)) && Number.isSafeInteger(Number(value)));
  return String(value);
}
function rows(body) {
  if (Array.isArray(body)) return body;
  if (Array.isArray(body?.items)) return body.items;
  if (Array.isArray(body?.content)) return body.content;
  return Object.values(body?._embedded ?? {}).find(Array.isArray) ?? [];
}
function complete(id) { checks.push({ criterion: id, status: 'PASS' }); }
function privateFile(filename) {
  const resolved = path.join(path.dirname(statePath), filename);
  if (fs.existsSync(resolved)) {
    requireThat(fs.lstatSync(resolved).isFile() && !fs.lstatSync(resolved).isSymbolicLink());
    requireThat(fs.realpathSync(resolved) === resolved);
  }
  return resolved;
}
function saveState() {
  const temporary = privateFile('acceptance-state.tmp');
  fs.writeFileSync(temporary, JSON.stringify(state), { flag: 'wx', mode: 0o600 });
  fs.renameSync(temporary, statePath);
}
async function request(id, method, apiPath, auth, body, statuses = [200], json = true) {
  criterion = id;
  httpStatus = 0;
  // Never follow redirects or load a URL taken from a server response.
  requireThat(apiPath.startsWith('/') && !apiPath.startsWith('//'));
  const target = new URL(apiPath, ORIGIN);
  requireThat(target.origin === ORIGIN);
  const payload = body === undefined ? undefined : Buffer.from(JSON.stringify(body));
  const headers = { Accept: json ? 'application/json' : '*/*' };
  if (auth) headers.Authorization = `Bearer ${auth.token}`;
  if (payload) {
    headers['Content-Type'] = 'application/json';
    headers['Content-Length'] = payload.length;
  }
  const response = await new Promise((resolve, reject) => {
    const req = https.request(target, { method, headers, agent }, (res) => {
      const parts = [];
      let size = 0;
      res.on('error', () => reject(new Error('transport-failed')));
      res.on('data', (part) => {
        size += part.length;
        if (size > 2 * 1024 * 1024) res.destroy(new Error('bounded-response-exceeded'));
        else parts.push(part);
      });
      res.on('end', () => resolve({ status: res.statusCode, data: Buffer.concat(parts) }));
    });
    req.once('error', () => reject(new Error('transport-failed')));
    req.setTimeout(30_000, () => req.destroy(new Error('timeout')));
    req.end(payload);
  });
  httpStatus = response.status;
  requireThat(statuses.includes(response.status));
  // Error bodies are deliberately never parsed or printed.
  const result = json && response.data.length && response.status < 300
    ? JSON.parse(response.data.toString('utf8')) : response.data;
  complete(id);
  return result;
}
async function login(account, label) {
  const wait = 13_000 - (Date.now() - lastLoginAt);
  if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
  lastLoginAt = Date.now();
  const result = await request(`${label}.login`, 'POST', '/api/auth/login', null,
    { login: account.login, password: account.password });
  requireThat(typeof result?.accessToken === 'string' && result.accessToken.length > 0);
  const auth = { token: result.accessToken };
  auth.session = await request(`${label}.session`, 'GET', '/api/auth/session', auth);
  requireThat(typeof auth.session?.sessionVersion === 'string');
  return auth;
}
async function role(auth, selected, label) {
  const response = await request(`${label}.role.${selected}`, 'PUT', '/api/auth/session/active-role', auth,
    { role: selected, expectedSessionVersion: auth.session.sessionVersion });
  requireThat(typeof response?.accessToken === 'string' && response.accessToken.length > 0);
  requireThat(response.session?.activeRole === selected);
  const fresh = { token: response.accessToken, session: response.session };
  const canonical = await request(`${label}.canonical.${selected}`, 'GET', '/api/auth/session', fresh);
  requireThat(canonical.activeRole === selected && canonical.sessionVersion === fresh.session.sessionVersion);
  fresh.session = canonical;
  return fresh;
}
function today() {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Moscow' }).format(new Date());
}
function offsetDate(date, days) {
  const value = new Date(`${date}T12:00:00Z`);
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}
async function createUser(admin, key, roleName, telegramId) {
  const user = await request(`prepare.user.${key}`, 'POST', '/api/academic/users', admin, {
    lastName: 'Локальный', firstName: key, middleName: null, role: roleName,
    groupId: roleName === 'STUDENT' ? Number(state.group.id) : null,
    employeeNumber: roleName === 'TEACHER' ? `LOCAL-${state.marker.replaceAll('-', '').slice(0, 24)}` : null,
    telegramId: telegramId ?? null,
  }, [201]);
  requireThat(typeof user.login === 'string' && typeof user.initialPassword === 'string');
  state.accounts[key] = {
    id: objectId(user.id), login: user.login, password: user.initialPassword,
    nextPassword: `${randomBytes(24).toString('base64url')}!Aa9`, groupId: user.groupId,
    lastName: user.lastName, firstName: user.firstName, role: roleName,
  };
  // Journal returned one-time credentials before changing any account password.
  saveState();
  await changeInitialPassword(key, roleName);
}
async function changeInitialPassword(key, roleName) {
  const account = state.accounts[key];
  const auth = await role(await login(account, `prepare.${key}`), roleName, `prepare.${key}`);
  await request(`prepare.${key}.password`, 'POST', '/api/auth/change-password', auth,
    { currentPassword: account.password, newPassword: account.nextPassword }, [204]);
  account.password = account.nextPassword;
  delete account.nextPassword;
  saveState();
}
async function prepare(input) {
  criterion = 'prepare.new-plan';
  requireThat(!fs.existsSync(statePath));
  requireThat(typeof input.seed?.login === 'string' && typeof input.seed?.password === 'string');
  state = { schema: SCHEMA, marker: randomUUID(), phase: 'preparing', accounts: {}, today: today() };
  saveState();
  const admin = await role(await login(input.seed, 'seed'), 'ADMIN', 'seed');
  const semesters = rows(await request('prepare.semesters', 'GET', '/api/academic/semesters?size=200&page=0', admin));
  let semester = semesters.find((item) => item.active === true && item.dateFrom <= state.today && item.dateTo >= state.today);
  if (!semester) {
    semester = await request('prepare.semester.create', 'POST', '/api/academic/semesters', admin,
      { name: `Local ${state.marker}`, dateFrom: offsetDate(state.today, -14), dateTo: offsetDate(state.today, 56) }, [201]);
    state.semester = { id: objectId(semester.id), dateFrom: semester.dateFrom, dateTo: semester.dateTo };
    saveState();
    await request('prepare.semester.activate', 'PATCH', `/api/academic/semesters/${state.semester.id}/activate`, admin);
  }
  state.semester = { id: objectId(semester.id), dateFrom: semester.dateFrom, dateTo: semester.dateTo };
  saveState();
  const group = await request('prepare.group.create', 'POST', '/api/academic/groups/registry', admin,
    { alphabeticCode: 'РТ', numericCode: '319', trainingDurationYears: 3 }, [201]);
  state.group = { id: objectId(group.id), name: group.name };
  saveState();
  await createUser(admin, 'Админ', 'ADMIN');
  await finishPrepare(admin);
}
async function finishPrepare(admin) {
  await createUser(admin, 'Преподаватель', 'TEACHER');
  const syntheticId = 800_000_000_000 + randomBytes(4).readUInt32BE();
  await createUser(admin, 'Староста', 'STUDENT', syntheticId);
  await createUser(admin, 'Помощник', 'STUDENT', syntheticId + 1);
  await request('prepare.headman.bind', 'PUT', `/api/academic/groups/${state.group.id}/headman`, admin,
    { studentId: Number(state.accounts.Староста.id), expectedHeadmanId: null });
  const headman = await role(await login(state.accounts.Староста, 'prepare.headman'), 'HEADMAN', 'prepare.headman');
  const subject = await request('prepare.subject.assignment', 'POST', '/api/academic/subjects', headman, {
    name: `Локальный предмет ${state.marker}`, type: 'LECTURE', lessonTypes: ['LECTURE'], teacherIds: null,
    initialAssignments: [{ teacherId: Number(state.accounts.Преподаватель.id), semesterId: Number(state.semester.id),
      lessonType: 'LECTURE', validFrom: state.semester.dateFrom, validUntilExclusive: null }],
  }, [201]);
  state.subject = { id: objectId(subject.id), assignmentId: objectId(subject.createdAssignmentIds?.[0] ?? subject.assignments?.[0]?.id) };
  saveState();
  await request('prepare.assistant.bind', 'POST', '/api/academic/assistants', headman,
    { studentId: Number(state.accounts.Помощник.id), groupId: Number(state.group.id), permissions: ['VIEW_STATS'] }, [201]);
  state.phase = 'prepared';
  saveState();
}
async function resumePrepare(input) {
  criterion = 'resume.known-checkpoint';
  // Only the observed bootstrap-denied ADMIN password checkpoint is resumable.
  // Any later partial effect or authority mismatch requires a new root decision.
  requireThat(state.phase === 'preparing' && state.subject === undefined);
  requireThat(Object.keys(state.accounts ?? {}).length === 1 && state.accounts.Админ);
  const account = state.accounts.Админ;
  objectId(account.id);
  requireThat(account.role === 'ADMIN' && account.groupId == null);
  requireThat(typeof account.password === 'string' && account.password.length > 0
    && typeof account.nextPassword === 'string' && account.nextPassword.length > 0);
  requireThat(typeof state.group.name === 'string'
    && /^\d{4}-\d{2}-\d{2}$/.test(state.semester.dateFrom)
    && /^\d{4}-\d{2}-\d{2}$/.test(state.semester.dateTo));
  const admin = await role(await login(input.seed, 'resume.seed'), 'ADMIN', 'resume.seed');
  const group = await request('resume.group.authority', 'GET', `/api/academic/groups/${state.group.id}`, admin);
  requireThat(String(group.id) === state.group.id && group.name === state.group.name);
  const semester = await request('resume.semester.authority', 'GET', `/api/academic/semesters/${state.semester.id}`, admin);
  requireThat(String(semester.id) === state.semester.id && semester.active === true
    && semester.dateFrom === state.semester.dateFrom && semester.dateTo === state.semester.dateTo
    && semester.dateFrom <= today() && semester.dateTo >= today());
  const user = await request('resume.admin.authority', 'GET', `/api/academic/users/${objectId(account.id)}`, admin);
  requireThat(String(user.id) === account.id && user.login === account.login
    && user.lastName === account.lastName && user.firstName === account.firstName
    && user.role === 'ADMIN' && user.groupId == null);
  complete('resume.known-checkpoint.authority');
  await changeInitialPassword('Админ', 'ADMIN');
  await finishPrepare(admin);
}
async function persistence(admin, label) {
  const group = await request(`${label}.group.read`, 'GET', `/api/academic/groups/${state.group.id}`, admin);
  requireThat(objectId(group.id) === state.group.id && group.name === state.group.name);
  for (const [index, key] of ACCOUNT_KEYS.entries()) {
    const account = state.accounts[key];
    const user = await request(`${label}.user.${index + 1}`,
      'GET', `/api/academic/users/${objectId(account.id)}`, admin);
    requireThat(String(user.id) === account.id && user.login === account.login
      && user.lastName === account.lastName && user.firstName === account.firstName
      && String(user.groupId) === String(account.groupId));
  }
  const roster = await request(`${label}.headman.read`, 'GET', `/api/academic/groups/${state.group.id}/headman/roster`, admin);
  requireThat(String(roster.currentHeadmanId) === state.accounts.Староста.id);
  complete(`${label}.identical-records`);
}
async function checkBefore() {
  const admin = await role(await login(state.accounts.Админ, 'admin'), 'ADMIN', 'admin');
  await persistence(admin, 'before');
  await request('admin.home', 'GET', '/api/academic/dashboard/stats', admin);
  let headman = await role(await login(state.accounts.Староста, 'headman'), 'STUDENT', 'headman');
  const student = await request('student.session', 'GET', '/api/v1/student/session', headman);
  requireThat(String(student.group?.id) === state.group.id && String(student.semester?.id) === state.semester.id);
  await request('student.permissions', 'GET', '/api/academic/assistants/me/permissions', headman);
  await request('student.today', 'GET', '/api/v1/student/today', headman);
  await request('student.homework', 'GET', '/api/v1/student/homework', headman);
  await request('boundary.no-auth', 'GET', '/api/academic/dashboard/stats', null, undefined, [401, 403]);
  await request('boundary.student-admin', 'GET', '/api/academic/dashboard/stats', headman, undefined, [403]);
  headman = await role(headman, 'HEADMAN', 'sequence');
  const q = `groupId=${state.group.id}&semesterId=${state.semester.id}`;
  await request('headman.lessons', 'GET', `/api/schedule/groups/${state.group.id}/lessons?dateFrom=${state.today}&dateTo=${state.today}&page=0&size=20&status=PLANNED`, headman);
  await request('headman.semesters', 'GET', '/api/academic/semesters?page=0&size=20', headman);
  const assignments = rows(await request('headman.assignments', 'GET', `/api/academic/assignments?${q}&page=0&size=20`, headman));
  requireThat(assignments.some((item) => String(item.id) === state.subject.assignmentId));
  await request('headman.items', 'GET', `/api/schedule/items?${q}&page=0&size=20`, headman);
  await request('headman.one-off', 'GET', `/api/schedule/one-off-lessons?${q}&dateFrom=${state.today}&dateTo=${state.today}`, headman);
  await request('headman.fresh-ticket', 'POST', '/api/auth/ws-ticket', headman);
  headman = await role(headman, 'STUDENT', 'sequence');
  await request('student.fresh-ticket', 'POST', '/api/auth/ws-ticket', headman);
  for (const suffix of ['', '/unread-count', '/preferences']) {
    await request(`notifications.${suffix ? suffix.slice(1) : 'list'}`, 'GET', `/api/notifications${suffix}`, headman);
  }
  const assistant = await role(await login(state.accounts.Помощник, 'assistant'), 'STUDENT', 'assistant');
  const assistantSession = await request('assistant.session', 'GET', '/api/v1/student/session', assistant);
  requireThat(String(assistantSession.group?.id) === state.group.id);
  const permissions = await request('assistant.permissions', 'GET', '/api/academic/assistants/me/permissions', assistant);
  requireThat(Array.isArray(permissions) && permissions.some((permission) => permission.code === 'VIEW_STATS'));
  const teacher = await role(await login(state.accounts.Преподаватель, 'teacher'), 'TEACHER', 'teacher');
  const semester = await request('teacher.semester', 'GET', '/api/v1/teacher/semester', teacher);
  requireThat(String(semester.id) === state.semester.id);
  const teacherAssignments = await request('teacher.assignments', 'GET', `/api/v1/teacher/assignments?semesterId=${semester.id}&dateFrom=${state.today}&dateTo=${state.today}`, teacher);
  requireThat(rows(teacherAssignments).some((item) => String(item.id) === state.subject.assignmentId));
  await request('teacher.day', 'GET', `/api/v1/teacher/day?semesterId=${semester.id}&date=${state.today}`, teacher);
  for (const base of ['/app/', '/mini-app/']) {
    const html = await request(base === '/app/' ? 'pwa.mount' : 'tma.mount', 'GET', base, null, undefined, [200], false);
    const asset = /<script\b[^>]*\bsrc="([^"]+)"/.exec(html.toString('utf8'))?.[1];
    requireThat(typeof asset === 'string' && asset.startsWith(`${base}assets/`));
    await request(base === '/app/' ? 'pwa.asset' : 'tma.asset', 'GET', asset, null, undefined, [200], false);
  }
  await request('tma.malformed-auth', 'POST', '/api/auth/tma', null, {}, [400, 401, 403]);
  state.phase = 'checked-before';
  saveState();
}
async function main() {
  const phase = process.argv[2];
  requireThat(process.argv.length === 3 && ['prepare', 'resume-prepare', 'check-before', 'check-after'].includes(phase));
  requireThat(typeof process.env.LOCALAPPDATA === 'string');
  const directory = path.resolve(process.env.LOCALAPPDATA, 'RutCampusTrack', 'local-stand');
  const repository = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const relative = path.relative(repository, directory);
  requireThat(relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative));
  requireThat(fs.realpathSync(directory) === directory && !fs.lstatSync(directory).isSymbolicLink());
  statePath = path.join(directory, 'acceptance-state.json');
  privateFile('acceptance-state.json');
  const input = JSON.parse(fs.readFileSync(privateFile('acceptance-input.json'), 'utf8'));
  requireThat(typeof input.caPath === 'string'
    && path.resolve(input.caPath) === path.resolve(process.env.LOCALAPPDATA, 'mkcert', 'rootCA.pem'));
  agent = new https.Agent({ ca: fs.readFileSync(input.caPath), rejectUnauthorized: true, keepAlive: true });
  try {
    if (phase === 'prepare') await prepare(input);
    else {
      state = JSON.parse(fs.readFileSync(statePath, 'utf8'));
      requireThat(state.schema === SCHEMA && /^[a-f0-9-]{36}$/.test(state.marker));
      objectId(state.group?.id);
      objectId(state.semester?.id);
      requireThat(/^\d{4}-\d{2}-\d{2}$/.test(state.today));
      if (phase === 'resume-prepare') await resumePrepare(input);
      else {
        objectId(state.subject?.assignmentId);
        requireThat(Object.keys(state.accounts ?? {}).length === 4 && ACCOUNT_KEYS.every((key) => state.accounts[key]));
        requireThat(state.phase === (phase === 'check-before' ? 'prepared' : 'checked-before'));
        if (phase === 'check-before') await checkBefore();
        else {
          const admin = await role(await login(state.accounts.Админ, 'after.admin'), 'ADMIN', 'after.admin');
          await persistence(admin, 'after');
          state.phase = 'checked-after';
          saveState();
        }
      }
    }
    process.stdout.write(`${JSON.stringify({ schema: SCHEMA, phase, status: 'PASS', checks, accountCount: 4 })}\n`);
  } finally { agent.destroy(); }
}
main().catch(() => {
  // Do not print exception messages, stack traces, server bodies or private state.
  process.stdout.write(`${JSON.stringify({ schema: SCHEMA, status: 'FAIL', criterion, httpStatus, checks })}\n`);
  process.exitCode = 1;
});
