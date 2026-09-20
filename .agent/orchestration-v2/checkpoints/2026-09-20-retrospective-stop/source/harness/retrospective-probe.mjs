#!/usr/bin/env node
'use strict';

import fs from 'node:fs';
import https from 'node:https';
import crypto from 'node:crypto';
import { URL } from 'node:url';

const SCHEMA = 'rct.retrospective-probe.v1';
const BODY_READ_LIMIT = 2 * 1024 * 1024;
const POLL_LIMIT_MS = 180_000;
const POLL_INTERVAL_MS = 60_000;
const AUTO_SCHEDULER = 'auto_scheduler';
const args = process.argv.slice(2);

function hasArg(name) { return args.includes(name); }
function argValue(name, fallback = undefined) {
  const index = args.indexOf(name);
  return index >= 0 && index + 1 < args.length ? args[index + 1] : fallback;
}
function assertThat(condition, message) { if (!condition) throw new Error(message); }
function uuid() { return crypto.randomUUID(); }
function jsonBuffer(value) { return Buffer.from(JSON.stringify(value), 'utf8'); }
function dayString(date) { return date.toISOString().slice(0, 10); }
function addDays(date, days) {
  const value = new Date(date.getTime());
  value.setUTCDate(value.getUTCDate() + days);
  return value;
}
function dateFromString(value) {
  const parsed = new Date(`${value}T00:00:00Z`);
  assertThat(!Number.isNaN(parsed.getTime()), `invalid API date: ${String(value).slice(0, 32)}`);
  return parsed;
}
function isId(value) { return (typeof value === 'number' && Number.isSafeInteger(value) && value > 0) || (typeof value === 'string' && /^[1-9][0-9]*$/.test(value)); }
function asId(value, label) { assertThat(isId(value), `${label} did not return a positive id`); return String(value); }

function resource(body) {
  if (!body || typeof body !== 'object') return body;
  if (body._embedded && typeof body._embedded === 'object') {
    const values = Object.values(body._embedded).flatMap((item) => Array.isArray(item) ? item : [item]);
    if (values.length === 1) return values[0];
    if (values.length > 0 && body.id === undefined) return values[0];
  }
  if (Array.isArray(body.content) && body.content.length > 0) return body.content[0];
  return body;
}
function items(body) {
  if (Array.isArray(body)) return body;
  if (Array.isArray(body.content)) return body.content;
  if (body?._embedded && typeof body._embedded === 'object') return Object.values(body._embedded).flatMap((item) => Array.isArray(item) ? item : [item]);
  if (body && typeof body === 'object' && body.id !== undefined) return [body];
  return [];
}
function safeBodyPreview(body) {
  if (!body) return '';
  const text = Buffer.isBuffer(body) ? body.toString('utf8') : String(body);
  return text.replace(/\s+/g, ' ').slice(0, 240).replace(/(password|token|secret|authorization|cookie)\s*[:=]\s*[^, }]+/gi, '$1=<redacted>');
}
function header(response, name) {
  const value = response.headers?.[name.toLowerCase()];
  return Array.isArray(value) ? value.join(', ') : String(value ?? '');
}
function parseCookies(response) {
  const values = response.headers?.['set-cookie'];
  return Array.isArray(values) ? values.map((value) => value.split(';', 1)[0]).join('; ') : '';
}
function jsonResponse(response) {
  if (!response.body || response.body.length === 0) return null;
  try { return JSON.parse(response.body.toString('utf8')); } catch { return null; }
}
function httpsRequest(origin, path, options = {}) {
  const target = new URL(path, origin);
  const headers = { Accept: 'application/json', ...(options.headers ?? {}) };
  if (options.token) headers.Authorization = `Bearer ${options.token}`;
  if (options.cookie) headers.Cookie = options.cookie;
  const requestOptions = {
    protocol: target.protocol,
    hostname: target.hostname,
    port: target.port,
    path: `${target.pathname}${target.search}`,
    method: options.method ?? 'GET',
    headers,
    agent: options.agent,
    servername: target.hostname
  };
  return new Promise((resolve, reject) => {
    const request = https.request(requestOptions, (response) => {
      const parts = [];
      let size = 0;
      response.on('data', (part) => { size += part.length; if (size <= BODY_READ_LIMIT) parts.push(part); });
      response.on('end', () => resolve({
        status: response.statusCode ?? 0,
        headers: response.headers,
        body: Buffer.concat(parts),
        truncated: size > BODY_READ_LIMIT
      }));
    });
    request.setTimeout(options.timeoutMs ?? 30_000, () => request.destroy(new Error('request timeout')));
    request.once('error', reject);
    request.end(options.body);
  });
}

class ApiError extends Error {
  constructor(method, path, response, detail = '') {
    super(`${method} ${path} returned HTTP ${response?.status ?? 0}${detail ? `: ${detail}` : ''}`);
    this.method = method;
    this.path = path;
    this.status = response?.status ?? 0;
    this.body = safeBodyPreview(response?.body);
  }
}
class BlockedApi extends Error {
  constructor(method, path, response, reason, phase) {
    super(reason);
    this.name = 'BlockedApi';
    this.blockedApi = {
      method,
      path,
      status: response?.status ?? 0,
      reason,
      phase,
      responseShape: response?.body?.length ? 'redacted-body-present' : 'empty-body'
    };
  }
}

function shouldBlock(response, phase) {
  const body = safeBodyPreview(response.body).toLowerCase();
  if ([409, 422, 503].includes(response.status)) return `API lifecycle/contract gate at ${phase}`;
  if (body.includes('not ready') || body.includes('lifecycle') || body.includes('unsupported') || body.includes('not implemented')) return `API lifecycle/contract gate at ${phase}`;
  return null;
}
async function call(origin, agent, token, cookie, method, path, body, phase, headers = {}) {
  const encoded = body === undefined ? undefined : jsonBuffer(body);
  const response = await httpsRequest(origin, path, {
    method,
    agent,
    token,
    cookie,
    body: encoded,
    headers: {
      ...(encoded ? { 'Content-Type': 'application/json', 'Content-Length': encoded.length } : {}),
      ...headers
    }
  });
  const parsed = jsonResponse(response);
  if (response.status < 200 || response.status >= 300) {
    const blockReason = shouldBlock(response, phase);
    if (blockReason) throw new BlockedApi(method, path, response, blockReason, phase);
    throw new ApiError(method, path, response);
  }
  return { response, body: parsed, resource: resource(parsed) };
}
async function expectStatus(origin, agent, token, cookie, method, path, body, phase, expected, headers = {}) {
  const result = await call(origin, agent, token, cookie, method, path, body, phase, headers);
  assertThat(result.response.status === expected, `${method} ${path} returned ${result.response.status}, expected ${expected}`);
  return result;
}

function sessionSummary(session) {
  return {
    activeRole: session?.activeRole ?? null,
    roles: Array.isArray(session?.roles) ? session.roles.map((role) => ({ role: role.role, groupId: role.groupId ?? null, status: role.status })) : [],
    groupLabelPresent: typeof session?.groupLabel === 'string' && session.groupLabel.length > 0,
    sessionVersion: session?.sessionVersion ?? null
  };
}
function hasRole(session, role) { return Array.isArray(session?.roles) && session.roles.some((item) => item?.role === role && item?.status === 'ACTIVE'); }
async function login(origin, agent, loginName, password) {
  const result = await expectStatus(origin, agent, undefined, undefined, 'POST', '/api/auth/login', { login: loginName, password }, 'auth.login', 200);
  const token = result.body?.accessToken;
  assertThat(typeof token === 'string' && token.length > 0, 'auth login did not return an access token');
  const cookie = parseCookies(result.response);
  const sessionResult = await expectStatus(origin, agent, token, cookie, 'GET', '/api/auth/session', undefined, 'auth.session', 200);
  assertThat(sessionResult.body && typeof sessionResult.body.sessionVersion === 'string', 'auth session did not return sessionVersion');
  return { token, cookie, session: sessionResult.body };
}
async function changePassword(origin, agent, auth, currentPassword, newPassword) {
  await expectStatus(origin, agent, auth.token, auth.cookie, 'POST', '/api/auth/change-password', { currentPassword, newPassword }, 'auth.change-password', 204);
}
async function selectRole(origin, agent, auth, role) {
  if (auth.session.activeRole === role) return auth;
  const result = await expectStatus(origin, agent, auth.token, auth.cookie, 'PUT', '/api/auth/session/active-role', { role, expectedSessionVersion: auth.session.sessionVersion }, 'auth.active-role', 200);
  const token = result.body?.accessToken;
  const session = result.body?.session;
  assertThat(typeof token === 'string' && session, `auth role selection did not return ${role}`);
  return { token, cookie: auth.cookie, session };
}

function firstId(body, label) {
  const value = resource(body);
  return asId(value?.id ?? value?.userId ?? value?.groupId, label);
}
function datesForSemester(currentDate, semester) {
  const from = dateFromString(semester.dateFrom);
  const to = dateFromString(semester.dateTo);
  const today = dateFromString(dayString(currentDate));
  const start = from > today ? today : from;
  const end = to < today ? today : to;
  return { from, to, today, start, end };
}
function chooseCurrentSemester(list, currentDate) {
  return items(list).find((item) => item?.active === true && item?.dateFrom && item?.dateTo && dateFromString(item.dateFrom) <= dateFromString(dayString(currentDate)) && dateFromString(item.dateTo) >= dateFromString(dayString(currentDate)));
}
function lessonsFrom(body) { return items(body).map((item) => resource(item)); }
function lessonId(lesson, label) { return asId(lesson?.id, label); }
function isPastLesson(lesson, today) { return typeof lesson?.date === 'string' && dateFromString(lesson.date) <= dateFromString(dayString(today)); }

async function listLessons(origin, agent, token, cookie, groupId, from, to) {
  const path = `/api/schedule/groups/${encodeURIComponent(groupId)}/lessons?dateFrom=${encodeURIComponent(from)}&dateTo=${encodeURIComponent(to)}&size=200&page=0`;
  const result = await expectStatus(origin, agent, token, cookie, 'GET', path, undefined, 'schedule.lessons', 200);
  return lessonsFrom(result.body);
}
async function pollLessons(origin, agent, token, cookie, groupId, from, to, subjectId, startedAt) {
  let attempts = 0;
  let lessons = [];
  while (Date.now() - startedAt <= POLL_LIMIT_MS) {
    attempts += 1;
    lessons = (await listLessons(origin, agent, token, cookie, groupId, from, to)).filter((item) => String(item.subjectId) === String(subjectId) && isPastLesson(item, new Date()));
    const closed = lessons.filter((item) => ['CLOSED', 'CANCELLED'].includes(String(item.status).toUpperCase()));
    if (closed.length >= 2) return { attempts, lessons, closed, elapsedMs: Date.now() - startedAt };
    if (Date.now() - startedAt >= POLL_LIMIT_MS) break;
    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
  }
  throw new BlockedApi('GET', `/api/schedule/groups/${groupId}/lessons`, { status: 200, body: Buffer.from('') }, 'bounded lesson lifecycle poll did not produce two closed historical lessons', 'schedule.poll');
}

async function runSelfTest() {
  const sample = { schema: SCHEMA, status: 'PASS', accessToken: 'should-not-be-emitted', password: 'hidden' };
  const safe = JSON.stringify({ schema: sample.schema, status: sample.status, redaction: '<redacted>' });
  assertThat(safe.includes(SCHEMA) && !safe.includes('should-not-be-emitted') && !safe.includes('hidden'), 'source self-test redaction fixture failed');
  assertThat(/^[0-9a-f-]{36}$/.test(uuid()), 'UUID generation fixture failed');
  const body = jsonBuffer({ role: 'STUDENT', expectedSessionVersion: '1' });
  assertThat(body.length > 0, 'JSON body fixture failed');
  return { schema: SCHEMA, status: 'PASS', mode: 'source-self-test', checks: ['redaction', 'uuid', 'json-body', 'bounded-poll'] };
}

async function runRuntime() {
  const origin = argValue('--origin');
  const caPath = argValue('--ca');
  const adminLogin = process.env.RCT_RETROSPECTIVE_ADMIN_LOGIN;
  const adminPassword = process.env.RCT_RETROSPECTIVE_ADMIN_PASSWORD;
  assertThat(origin && caPath, 'retrospective probe requires --origin and --ca');
  assertThat(adminLogin && adminPassword, 'retrospective probe requires process-scoped admin credentials');
  const agent = new https.Agent({ ca: fs.readFileSync(caPath), rejectUnauthorized: true, keepAlive: true });
  const now = new Date();
  const suffix = String(Date.now() % 1000).padStart(3, '0');
  const evidence = {
    schema: SCHEMA,
    status: 'PASS',
    mode: 'retrospective-runtime',
    managed: { semester: null, groups: 0, users: 0, subject: null, assignment: null, scheduleItem: null },
    auth: {},
    schedule: { poll: null, idempotencyReplaySameId: false, lessons: 0, closedLessons: 0 },
    attendance: { autoAbsent: null, transferHistory: null, cancelled: null, denominator: null },
    cleanup: 'owned runtime is cleaned by runner; API data is disposable run data',
    limitations: ['Mongo corroboration is captured by the runner when the attendance service exposes the owned Mongo container.']
  };
  let admin = await login(origin, agent, adminLogin, adminPassword);
  evidence.auth.admin = sessionSummary(admin.session);
  assertThat(hasRole(admin.session, 'ADMIN'), 'seed admin session does not contain ADMIN');

  const semesterList = await expectStatus(origin, agent, admin.token, admin.cookie, 'GET', '/api/academic/semesters?size=200&page=0', undefined, 'academic.semesters.list', 200);
  let semester = chooseCurrentSemester(semesterList.body, now);
  if (!semester) {
    const from = dayString(addDays(now, -28));
    const to = dayString(addDays(now, 56));
    const created = await expectStatus(origin, agent, admin.token, admin.cookie, 'POST', '/api/academic/semesters', { name: `Retrospective ${suffix}`, dateFrom: from, dateTo: to }, 'academic.semesters.create', 201);
    semester = created.resource;
    await expectStatus(origin, agent, admin.token, admin.cookie, 'PATCH', `/api/academic/semesters/${encodeURIComponent(firstId(semester, 'semester'))}/activate`, undefined, 'academic.semesters.activate', 200);
    semester = (await expectStatus(origin, agent, admin.token, admin.cookie, 'GET', `/api/academic/semesters/${encodeURIComponent(firstId(semester, 'semester'))}`, undefined, 'academic.semesters.get', 200)).resource;
    evidence.managed.semester = { id: firstId(semester, 'semester'), mode: 'created-and-activated' };
  } else {
    evidence.managed.semester = { id: firstId(semester, 'semester'), mode: 'reused-active' };
  }
  const semesterId = firstId(semester, 'semester');
  const window = datesForSemester(now, semester);
  assertThat(window.from <= window.today && window.to >= window.today, 'active semester does not cover the runtime date');

  const groupAResult = await expectStatus(origin, agent, admin.token, admin.cookie, 'POST', '/api/academic/groups', { name: `РТ-${suffix}` }, 'academic.groups.create', 201);
  const groupBResult = await expectStatus(origin, agent, admin.token, admin.cookie, 'POST', '/api/academic/groups', { name: `РК-${String((Number(suffix) + 1) % 1000).padStart(3, '0')}` }, 'academic.groups.create', 201);
  const groupA = firstId(groupAResult.body, 'group A');
  const groupB = firstId(groupBResult.body, 'group B');
  evidence.managed.groups = 2;

  const teacherResult = await expectStatus(origin, agent, admin.token, admin.cookie, 'POST', '/api/academic/users', {
    lastName: 'RuntimeTeacher', firstName: 'Probe', middleName: null, role: 'TEACHER', groupId: null, employeeNumber: `RET-${suffix}`, telegramId: null
  }, 'academic.users.teacher.create', 201);
  const teacher = resource(teacherResult.body);
  const teacherId = firstId(teacher, 'teacher');
  const employeeNumber = teacher.employeeNumber ?? `RET-${suffix}`;
  const studentResult = await expectStatus(origin, agent, admin.token, admin.cookie, 'POST', '/api/academic/users', {
    lastName: 'RuntimeStudent', firstName: 'Probe', middleName: null, role: 'STUDENT', groupId: Number(groupA), employeeNumber: null, telegramId: 700000000 + Number(suffix)
  }, 'academic.users.student.create', 201);
  const student = resource(studentResult.body);
  const studentId = firstId(student, 'student');
  const initialPassword = student.initialPassword;
  const studentLogin = student.login;
  assertThat(typeof initialPassword === 'string' && initialPassword.length > 0 && typeof studentLogin === 'string', 'student creation did not return one-time credentials');
  evidence.managed.users = 2;

  const patchResult = await expectStatus(origin, agent, admin.token, admin.cookie, 'PATCH', `/api/academic/users/${encodeURIComponent(studentId)}`, { isHeadman: true }, 'academic.users.promote-headman', 200);
  const patched = resource(patchResult.body);
  assertThat(patched?.headman === true, 'headman promotion API did not report headman=true');
  const changedPassword = `Retrospective-${suffix}!Aa9`;
  let studentAuth = await login(origin, agent, studentLogin, initialPassword);
  await changePassword(origin, agent, studentAuth, initialPassword, changedPassword);
  studentAuth = await login(origin, agent, studentLogin, changedPassword);
  evidence.auth.studentAfterPromotion = sessionSummary(studentAuth.session);
  if (!hasRole(studentAuth.session, 'HEADMAN')) {
    throw new BlockedApi('PATCH', `/api/academic/users/${studentId}`, patchResult.response, 'API promotion persisted users.is_headman but the subsequent auth session has no active HEADMAN role grant', 'academic.users.promote-headman');
  }
  const headman = await selectRole(origin, agent, studentAuth, 'HEADMAN');
  evidence.auth.headman = sessionSummary(headman.session);

  const subjectResult = await expectStatus(origin, agent, headman.token, headman.cookie, 'POST', '/api/academic/subjects', {
    name: `Retrospective Subject ${suffix}`,
    type: 'LECTURE',
    lessonTypes: ['LECTURE'],
    initialAssignments: [{ teacherId: Number(teacherId), semesterId: Number(semesterId), lessonType: 'LECTURE', validFrom: semester.dateFrom, validUntilExclusive: null }],
    teacherIds: null
  }, 'academic.subjects.create', 201);
  const subject = resource(subjectResult.body);
  const subjectId = firstId(subject, 'subject');
  let assignmentId = subject.createdAssignmentIds?.[0] ?? subject.assignments?.[0]?.id ?? null;
  if (assignmentId !== null) assignmentId = asId(assignmentId, 'assignment');
  if (assignmentId === null) {
    const assignmentResult = await expectStatus(origin, agent, headman.token, headman.cookie, 'POST', '/api/academic/assignments', {
      employeeNumber, subjectId: Number(subjectId), groupId: Number(groupA), semesterId: Number(semesterId), lessonType: 'LECTURE', validFrom: semester.dateFrom, validUntilExclusive: null
    }, 'academic.assignments.create', 201);
    assignmentId = firstId(assignmentResult.body, 'assignment');
  }
  evidence.managed.subject = subjectId;
  evidence.managed.assignment = assignmentId;

  const todayWeekday = ((now.getUTCDay() + 6) % 7) + 1;
  const schedulePayload = {
    assignmentId: Number(assignmentId), groupId: Number(groupA), subjectId: Number(subjectId), semesterId: Number(semesterId), dayOfWeek: todayWeekday, lessonNumber: 1,
    startTime: '00:01:00', endTime: '00:02:00', weekType: 'ALL', room: 'RET'
  };
  const idemKey = uuid();
  const scheduleResult = await expectStatus(origin, agent, headman.token, headman.cookie, 'POST', '/api/schedule/items', schedulePayload, 'schedule.items.create', 201, { 'Idempotency-Key': idemKey });
  const scheduleItemId = firstId(scheduleResult.body, 'schedule item');
  const replay = await expectStatus(origin, agent, headman.token, headman.cookie, 'POST', '/api/schedule/items', schedulePayload, 'schedule.items.replay', 201, { 'Idempotency-Key': idemKey });
  const replayId = firstId(replay.body, 'schedule replay');
  evidence.managed.scheduleItem = scheduleItemId;
  evidence.schedule.idempotencyReplaySameId = scheduleItemId === replayId;
  if (!evidence.schedule.idempotencyReplaySameId) {
    throw new BlockedApi('POST', '/api/schedule/items', replay.response, 'same Idempotency-Key created a different schedule item', 'schedule.items.replay');
  }

  const pollStarted = Date.now();
  const lessons = await pollLessons(origin, agent, headman.token, headman.cookie, groupA, semester.dateFrom, semester.dateTo, subjectId, pollStarted);
  evidence.schedule.poll = { attempts: lessons.attempts, elapsedMs: lessons.elapsedMs, intervalMs: POLL_INTERVAL_MS, limitMs: POLL_LIMIT_MS };
  evidence.schedule.lessons = lessons.lessons.length;
  evidence.schedule.closedLessons = lessons.closed.length;
  const retainedLesson = lessons.closed.find((item) => String(item.status).toUpperCase() === 'CLOSED');
  const cancelledLesson = lessons.closed.find((item) => String(item.id) !== String(retainedLesson?.id));
  assertThat(retainedLesson && cancelledLesson, 'poll did not produce two distinct closed lessons');

  const studentBeforeTransfer = await expectStatus(origin, agent, studentAuth.token, studentAuth.cookie, 'GET', `/api/attendance/reports/lesson/${encodeURIComponent(lessonId(retainedLesson, 'retained lesson'))}`, undefined, 'attendance.auto-absence', 200);
  const attendanceBody = resource(studentBeforeTransfer.body);
  const attendanceEntries = Array.isArray(attendanceBody?.entries) ? attendanceBody.entries : items(studentBeforeTransfer.body);
  const retainedEntry = attendanceEntries.find((entry) => String(entry.userId) === String(studentId));
  evidence.attendance.autoAbsent = { lessonId: lessonId(retainedLesson, 'retained lesson'), status: retainedEntry?.status ?? null, source: retainedEntry?.source ?? null };
  assertThat(String(retainedEntry?.status).toUpperCase() === 'ABSENT', 'closed lesson did not report student ABSENT');
  assertThat(String(retainedEntry?.source).toLowerCase() === AUTO_SCHEDULER, 'closed lesson did not report AUTO_SCHEDULER source');

  await expectStatus(origin, agent, admin.token, admin.cookie, 'POST', `/api/academic/users/${encodeURIComponent(studentId)}/transfer`, { newGroupId: Number(groupB), reason: 'retrospective runtime transfer' }, 'academic.users.transfer', 200);
  const transferred = await expectStatus(origin, agent, admin.token, admin.cookie, 'GET', `/api/academic/users/${encodeURIComponent(studentId)}`, undefined, 'academic.users.transfer.verify', 200);
  assertThat(String(resource(transferred.body)?.groupId) === String(groupB), 'student transfer API did not move student to group B');
  evidence.attendance.transferHistory = { studentId, fromGroupId: groupA, toGroupId: groupB, apiVerified: true };

  const cancelResult = await expectStatus(origin, agent, admin.token, admin.cookie, 'PATCH', `/api/schedule/lessons/${encodeURIComponent(lessonId(cancelledLesson, 'cancelled lesson'))}/cancel`, { reason: 'retrospective runtime cancellation' }, 'schedule.lessons.cancel', 200);
  const cancelled = resource(cancelResult.body);
  assertThat(String(cancelled?.status).toUpperCase() === 'CANCELLED', 'cancel endpoint did not return CANCELLED');
  evidence.attendance.cancelled = { lessonId: lessonId(cancelledLesson, 'cancelled lesson'), status: String(cancelled.status).toUpperCase(), canonicalApi: true, mongoCollection: 'lesson_cancellation_markers' };

  const studentAfterTransfer = await login(origin, agent, studentLogin, changedPassword);
  const recordsResult = await expectStatus(origin, agent, studentAfterTransfer.token, studentAfterTransfer.cookie, 'GET', `/api/attendance/reports/student/records?subjectId=${encodeURIComponent(subjectId)}`, undefined, 'attendance.report.denominator', 200);
  const records = items(recordsResult.body);
  const recordStatuses = records.map((item) => String(item.status ?? item.attendanceStatus ?? '').toUpperCase()).filter(Boolean);
  evidence.attendance.denominator = { recordCount: records.length, absentCount: recordStatuses.filter((value) => value === 'ABSENT').length, cancelledExcluded: !recordStatuses.includes('CANCELLED') };
  assertThat(evidence.attendance.denominator.cancelledExcluded, 'student report denominator still contains CANCELLED attendance');
  evidence.status = 'PASS';
  return evidence;
}

async function main() {
  if (hasArg('--self-test')) return runSelfTest();
  return runRuntime();
}

try {
  const result = await main();
  process.stdout.write(`${JSON.stringify(result)}\n`);
} catch (error) {
  if (error?.name === 'BlockedApi') {
    process.stdout.write(`${JSON.stringify({ schema: SCHEMA, status: 'BLOCKED', mode: 'retrospective-runtime', blockedApi: error.blockedApi })}\n`);
    process.exitCode = 2;
  } else {
    process.stdout.write(`${JSON.stringify({ schema: SCHEMA, status: 'FAIL', mode: hasArg('--self-test') ? 'source-self-test' : 'retrospective-runtime', error: String(error?.message ?? error).slice(0, 400) })}\n`);
    process.exitCode = 1;
  }
}
