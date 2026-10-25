// Portable local adaptation; cloud acceptance is not local execution.
'use strict';
// PREP ONLY. No imports with runtime effects, credential reads or browser launch before matching Root GO.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const assert = require('node:assert/strict'), cp = require('node:child_process');
const { performance } = require('node:perf_hooks');
const ROOT = __dirname, REPO = JSON.parse(fs.readFileSync(path.join(ROOT, 'settings.json'))).repo;
const V8 = ROOT;
const PRIVATE = path.join(ROOT, 'private');
const V8_SHA = fs.readFileSync(path.join(ROOT, 'PACKAGE-SHA256'), 'utf8').trim();
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const labels = { ADMIN: 'Администратор', HEADMAN: 'Староста', TEACHER: 'Преподаватель' };
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
function durableWrite(file, value, exclusive = false) {
  assert(!fs.existsSync(file) || !fs.lstatSync(file).isSymbolicLink());
  const temp = file + '.' + crypto.randomUUID() + '.tmp';
  const fd = fs.openSync(temp, fs.constants.O_WRONLY | fs.constants.O_CREAT | fs.constants.O_EXCL | fs.constants.O_NOFOLLOW, 0o600);
  try { fs.writeFileSync(fd, JSON.stringify(value, null, 2) + '\n'); fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
  if (exclusive) { fs.linkSync(temp, file); fs.unlinkSync(temp); } else fs.renameSync(temp, file);
  const dir = fs.openSync(path.dirname(file), fs.constants.O_RDONLY);
  try { fs.fsyncSync(dir); } finally { fs.closeSync(dir); }
}
const save = (name, value, exclusive = false) => durableWrite(path.join(PRIVATE, name), value, exclusive);
function registerSid(actor, claim, bearer, source) {
  assert(UUID.test(claim.sid || ''), 'Normal Auth SID required');
  actor.observations.push({ sid: claim.sid, userId: String(claim.sub), source });
  if (String(claim.sub) !== actor.userId) { actor.foreignActor = true; throw Error('Normal Auth bearer returned unexpected actor'); }
  actor.sessions.set(claim.sid, { ...(actor.sessions.get(claim.sid) || {}), bearer, qualified: actor.sessions.get(claim.sid)?.qualified || false });
  actor.sids.add(claim.sid);
  if (!actor.initialSid) actor.initialSid = claim.sid;
  else if (actor.initialSid !== claim.sid && actor.successorSid !== claim.sid) {
    if (actor.client === 'TMA' && actor.role === 'HEADMAN' && actor.phase === 'observe'
      && source === '/api/auth/tma' && actor.readmissionForwarded && !actor.successorSid && actor.successorCleanupApproved) actor.successorSid = claim.sid;
    else { actor.unexpectedSuccessor = true; throw Error('Unadmitted successor SID; retain every observed handle'); }
  }
  actor.currentSid = claim.sid; actor.candidateSid = claim.sid; actor.bearer = bearer;
}
function causallyAfter(started, boundary) { return Number.isFinite(started) && Number.isFinite(boundary) && started > boundary; }
async function captureGate(actor) {
  if (actor.forwardBarrier) await actor.forwardBarrier;
  if (actor.captureBarrier) await actor.captureBarrier;
  return !actor.captureError && !actor.foreignActor && !actor.unexpectedSuccessor;
}
function restoreReceipt(result, actors, closed, post, fixture, packageSha) {
  assert.equal(result.phase, 'COMPLETED'); assert.equal(result.confirmRequests, 1); assert(UUID.test(result.operationId || ''));
  for (const key of ['numericPreview', 'originalOperationCompleted', 'sixNaturalViews', 'independentSqlMongoPreservation']) assert.equal(result.checks[key], true);
  assert(!result.runtimeEvidenceFailure && !result.error && result.blockedWrites.length === 0);
  assert(post.allPassed && closed.allPassed); assert.equal(String(post.snapshot.groupId), fixture.groupId);
  const operation = closed.academic.operation, admin = actors.find(a => a.role === 'ADMIN');
  assert.equal(operation.id, result.operationId); assert.equal(operation.phase, 'COMPLETED');
  assert.equal(String(operation.groupId), fixture.groupId); assert.equal(String(operation.actorId), '1'); assert.equal(operation.sessionId, admin.initialSid);
  const observed = actors.flatMap(a => [...a.sids]); assert.equal(new Set(observed).size, observed.length);
  assert.equal(closed.auth.sessions.length, observed.length);
  for (const actor of actors) {
    assert.equal(actor.record.cleanup.state, 'CONFIRMED_204_GUEST_401'); assert(actor.contextClosed);
    for (const sid of actor.sids) assert(closed.auth.sessions.some(s => s.sid === sid && s.userId === actor.userId && s.revoked));
  }
  return { allPassed: true, phase: 'COMPLETED', cleanupComplete: true, groupId: fixture.groupId,
    operationId: result.operationId, sourceCommit: result.sourceCommit, rootHead: result.rootHead,
    packageSha256: V8_SHA, driverPackageSha256: packageSha,
    checks: { ...result.checks }, ownSidCount: observed.length,
    ownSidHandles: actors.map(a => ({ actor: a.name, userId: a.userId, originalSid: a.initialSid, observedSids: [...a.sids], successorSid: a.successorSid || null })),
    cleanup: actors.map(a => ({ actor: a.name, ...a.record.cleanup })),
    evidence: { postproof: path.join(V8, 'private/native-after.json'), cohortClosed: path.join(PRIVATE, 'cohort-closed.json'), receipt: path.join(PRIVATE, 'receipt.json') },
    endpointCleanup: true, productLogoutButtonUiClaim: false, nativeTelegramProviderClaim: false };
}
function python(args, cwd = V8) {
  const r = cp.spawnSync('python3', args, { cwd, encoding: 'utf8', timeout: 60000 });
  assert.equal(r.status, 0, 'Scoped admission/proof failed; private output withheld');
}
function frozen() {
  const manifest = fs.readFileSync(path.join(ROOT, 'SOURCE.SHA256SUMS'), 'utf8');
  assert.equal(hash(manifest), fs.readFileSync(path.join(ROOT, 'PACKAGE-SHA256'), 'utf8').trim());
  for (const line of manifest.trim().split('\n')) {
    const [expected, file] = line.split('  ');
    assert(file && !path.isAbsolute(file) && !file.split('/').includes('..'));
    assert.equal(hash(fs.readFileSync(path.join(ROOT, file))), expected, 'Driver freeze changed');
  }
  assert.equal(fs.readFileSync(path.join(V8, 'PACKAGE-SHA256'), 'utf8').trim().toLowerCase(), V8_SHA);
  return hash(manifest);
}
function resources() {
  const r = cp.spawnSync('python3', ['-c', 'import runtime,json;print(json.dumps(runtime.resources()))'], { cwd: ROOT, encoding: 'utf8' });
  assert.equal(r.status, 0, 'Local resource observation unavailable'); return JSON.parse(r.stdout);
}
async function waitUntil(condition, ms = 30000, interval = 100) {
  const end = Date.now() + ms;
  while (Date.now() < end) { if (await condition()) return; await new Promise(r => setTimeout(r, interval)); }
  assert.fail('Bounded observation expired; no automatic retry');
}

async function main() {
  assert.equal(process.argv[2], 'execute-once', 'PREP only; explicit execute-once + Root GO required');
  const [goFile, configFile] = process.argv.slice(3);
  assert(goFile && configFile);
  const packageSha = frozen(), go = JSON.parse(fs.readFileSync(goFile, 'utf8'));
  assert.equal(go.driverPackageSha256, packageSha);
  assert.equal(go.cohort, 'ONE_DELETE_SIX_LIVE_VIEWS');
  assert.equal(go.telegramContext, 'CONTROLLED_OWN_ISOLATED_SIGNER');
  assert.equal(go.ownOrigin, 'http://127.0.0.1:14250');
  assert.equal(go.allowObservedTmaHeadmanSuccessorCleanup, true, 'Exact one natural TMA successor cleanup scope required');
  const bindings = JSON.parse(fs.readFileSync(path.join(ROOT, 'bindings.json'), 'utf8'));
  assert.equal(bindings.state, 'SOURCE_READY_FOR_INDEPENDENT_REVIEW', 'Accepted frontend correction/source binding remains pending');
  assert.equal(bindings.frontendRootHead, go.rootHead);
  const frontendUnchanged = () => {
    const inputManifest = fs.readFileSync(path.join(ROOT, 'frontend-inputs.SHA256SUMS'), 'utf8');
    assert.equal(hash(inputManifest), bindings.frontendInputsManifestSha256);
    for (const line of inputManifest.trim().split('\n')) {
      const [expected, name] = line.split('  '); assert(name.startsWith('frontends/') && !name.split('/').includes('..'));
      assert.equal(hash(fs.readFileSync(path.join(REPO, name))), expected, 'Prepared frontend input changed');
    }
  };
  frontendUnchanged();
  const rootUnchanged = () => assert.equal(cp.execFileSync('git', ['-C', REPO, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim(), go.rootHead, 'Root source revision changed');
  rootUnchanged();
  // This admits the frozen v8 package, runtime/source/JAR closure and own realm before any credential read.
  python(['-c', 'import runtime,sys;runtime.admission("native-case",sys.argv[1],continuation=True)', goFile]);
  const pre = resources();
  assert(pre.memoryMaximum >= 12884901888 && pre.memoryAvailable >= 2147483648, 'Actual cgroup budget/headroom required');
  assert(pre.diskAvailable >= 2684354560 && go.capacityQENV010Applied === true, 'Root capacity gate remains required');
  fs.mkdirSync(PRIVATE, { mode: 0o700 }); fs.chmodSync(PRIVATE, 0o700);
  save('intent.json', { state: 'RESERVED_NOT_SENT', packageSha, rootHead: go.rootHead, at: new Date().toISOString() }, true);
  assert.equal(fs.realpathSync(configFile), path.join(V8, 'private/native-config.json'), 'Only exact owned fixture credential config is admitted');
  assert(!fs.lstatSync(configFile).isSymbolicLink());
  const cfg = JSON.parse(fs.readFileSync(configFile, 'utf8'));
  for (const file of ['native-case-result.json', 'native-before.json', 'native-after.json']) assert(!fs.existsSync(path.join(V8, 'private', file)), 'Prior native attempt/proof exists; preserve it, no new cohort');
  const fixture = JSON.parse(fs.readFileSync(path.join(V8, 'private/fixture-state.json'), 'utf8'));
  assert.equal(fixture.step, 'owned-fixture-ready');
  assert.equal(fixture.sourceCommit, go.sourceCommit);
  assert.equal(fixture.telegramIds.headman, 900000199); assert.equal(fixture.telegramIds.teacher, 900000198);
  const clients = [ ['WEB', 'http://127.0.0.1:14250'], ['PWA', 'http://127.0.0.1:14251'], ['TMA', 'http://127.0.0.1:14252'] ];
  assert.equal(cfg.baseURL, clients[0][1]); assert.equal(cfg.groupName, fixture.groupName);
  assert.equal(cfg.controlGroupName, fixture.controlGroupName);
  assert(BigInt(fixture.groupId) > 1n && BigInt(fixture.controlGroupId) > 1n && fixture.groupId !== fixture.controlGroupId);
  const { chromium, request } = require(process.env.RCT_PLAYWRIGHT_MODULE || path.join(JSON.parse(fs.readFileSync(path.join(__dirname,'settings.json'))).repo, 'tests/e2e/node_modules/@playwright/test'));
  const { observe } = require(path.join(V8, 'idle-observer.cjs'));
  const { installOwnedTelegramHost } = require(path.join(V8, 'telegram-owned-adapter.cjs'));
  const result = { state: 'RUNNING', packageSha, rootHead: go.rootHead, sourceCommit: go.sourceCommit,
    startedAt: new Date().toISOString(), resourcesBefore: pre, clients: [], confirmRequests: 0,
    operationId: null, phase: null, checks: {}, cleanup: [], nativeTelegramProviderClaim: false,
    providerOrExportCommand: false, blockedWrites: [], samples: [], error: null };
  const actors = [], jobs = new Set(); let browser, monitor, guardStopped = false, deleteArmed = false, complete = false;
  const persist = () => { save('receipt.json', result); save('actors.json', actors.map(a => ({ name: a.name, userId: a.userId, sid: a.initialSid, role: a.role, client: a.client,
    observedSids: [...a.sids], successorSid: a.successorSid || null, currentSid: a.currentSid,
    qualifiedSids: [...a.sessions].filter(([,s]) => s.qualified).map(([sid]) => sid) }))); };
  const job = p => { jobs.add(p); p.finally(() => jobs.delete(p)); };
  const settle = async () => { while (jobs.size) await Promise.all([...jobs]); for (const a of actors) assert(!a.captureError, 'Auth/projection evidence capture failed'); };
  const safeStop = async () => { guardStopped = true; result.state = 'GUARD_STOP_UNKNOWN_HOLD'; persist(); if (browser) await browser.close().catch(() => undefined); };
  process.once('SIGTERM', () => { void safeStop().finally(() => process.exit(2)); });
  process.once('SIGINT', () => { void safeStop().finally(() => process.exit(2)); });
  const retainEvidenceFailure = () => { result.runtimeEvidenceFailure = true; deleteArmed = false; result.state = 'UNKNOWN_HOLD'; try { persist(); } catch {} };
  process.on('uncaughtException', retainEvidenceFailure);
  process.on('unhandledRejection', retainEvidenceFailure);
  async function authCheckpoint(actor, state, extra = {}) {
    // New owned browser context only; never copy the fixture/config/signer/private environment.
    save(actor.name + '-storage.json', await actor.context.storageState());
    save(actor.name + '-handle.json', { state, contextId: actor.contextId, userId: actor.userId,
      initialSid: actor.initialSid, candidateSid: actor.candidateSid || null, observedSids: [...actor.sids],
      currentSid: actor.currentSid, successorSid: actor.successorSid || null, observations: actor.observations,
      sessions: Object.fromEntries(actor.sessions), lastAuthIntent: actor.lastAuthIntent || null,
      ownOrigin: actor.base, bearer: actor.bearer, storage: actor.name + '-storage.json', ...extra });
  }
  function authWriteBarrier(actor) {
    assert(!actor.releaseForwardBarrier, 'Another Auth write is unresolved');
    actor.forwardBarrier = new Promise(resolve => { actor.releaseForwardBarrier = resolve; });
  }
  async function ownCookie(actor) {
    // Playwright's cookies(http://127.0.0.1/...) URL filter omits Secure cookies; inspect metadata of this own context instead.
    const cookies = (await actor.context.cookies()).filter(c => c.name === 'rct_refresh'
      && c.domain === new URL(actor.base).hostname && c.path === '/api/auth' && c.secure && c.httpOnly && c.sameSite === 'Strict');
    assert.equal(cookies.length, 1, 'Exact owned refresh-cookie pointer required; no fabricated header');
    return cookies[0];
  }
  const cookieFingerprint = c => hash(JSON.stringify([c.name, c.domain, c.path, c.value]));
  async function canonical(actor) {
    assert(actor.bearer && actor.initialSid && !actor.foreignActor, 'Exact owned bearer/SID required');
    const response = await actor.context.request.get(actor.base + '/api/auth/session', {
      headers: { Authorization: 'Bearer ' + actor.bearer, Origin: actor.base }, timeout: 7000, maxRedirects: 0 });
    assert.equal(response.status(), 200, 'Canonical own session unavailable; no refresh/re-auth retry');
    const body = await response.json(), snap = body.session || body;
    assert.equal(String(snap.userId), actor.userId); assert.equal(snap.sessionId, actor.currentSid, 'Canonical current SID must match its distinct accepted handle');
    assert(!actor.unexpectedSuccessor && !actor.foreignActor);
    if (actor.currentSid !== actor.initialSid) assert.equal(actor.currentSid, actor.successorSid);
    actor.sessions.get(snap.sessionId).qualified = true;
    await authCheckpoint(actor, 'CANONICAL_EXACT_SID', { activeRole: snap.activeRole });
    return snap;
  }
  async function createActor(client, base, role) {
    const credentials = role === 'ADMIN' ? cfg.admin : role === 'HEADMAN' ? fixture.accounts[0] : fixture.retainedTeacher;
    const userId = role === 'ADMIN' ? '1' : String(credentials.id);
    const actor = { name: client + '-' + role, client, base, role, userId, initialSid: null, sids: new Set(), bearer: null,
      foreignActor: false, unexpectedSuccessor: false, sessions: new Map(), observations: [], successorCleanupApproved: go.allowObservedTmaHeadmanSuccessorCleanup,
      authWrites: {}, authPending: new Set(), api: [], catalogs: [], stats: [], requestStarts: new WeakMap(), phase: 'login', cookieHash: null,
      contextId: crypto.randomUUID() };
    actor.context = await browser.newContext({ viewport: client === 'WEB' ? { width: 1440, height: 1080 } : { width: 390, height: 844 }, timezoneId: 'Europe/Moscow', colorScheme: 'light' });
    actor.page = await actor.context.newPage(); actor.page.setDefaultTimeout(12000); actors.push(actor);
    const page = actor.page, record = { name: actor.name, loginAcknowledged: false, sidCaptured: false, actualOwnerMatches: false, cleanup: null };
    actor.record = record; result.clients.push(record);
    actor.observer = observe(page, { userId, groupId: fixture.groupId });
    page.on('pageerror', () => { actor.pageError = true; });
    page.on('requestfailed', req => {
      const pathname = new URL(req.url()).pathname;
      if (!actor.authPending.has(pathname)) return;
      actor.captureError = true;
      job((async () => { try { await authCheckpoint(actor, 'AUTH_TRANSPORT_UNKNOWN_HOLD', { path: pathname }); }
        finally { actor.releaseForwardBarrier?.(); actor.releaseForwardBarrier = null; } })());
    });
    page.on('request', req => { actor.requestStarts.set(req, performance.now()); });
    page.on('websocket', socket => {
      if (!new URL(socket.url()).pathname.startsWith('/api/ws/')) return;
      socket.on('framereceived', ({ payload }) => {
        const raw = typeof payload === 'string' ? payload : Buffer.from(payload).toString('utf8');
        let frames = [raw];
        if (raw.startsWith('a[') || raw.startsWith('[')) { try { frames = JSON.parse(raw.startsWith('a[') ? raw.slice(1) : raw); } catch { return; } }
        for (const frame of frames) {
          if (typeof frame !== 'string' || !frame.startsWith('MESSAGE\n')) continue;
          try {
            const body = JSON.parse(frame.slice(frame.indexOf('\n\n') + 2).replace(/\0+$/, ''));
            if (actor.phase === 'observe' && body.type === 'group.updated' && body.payload?.group_id === String(fixture.groupId)) actor.hintBoundary = performance.now();
          } catch {}
        }
      });
    });
    page.on('response', response => {
      const pathname = new URL(response.url()).pathname, method = response.request().method();
      if (!pathname.startsWith('/api/')) return;
      actor.api.push({ path: pathname, method, status: response.status(), at: Date.now() });
      const authResponse = ['/api/auth/login', '/api/auth/tma', '/api/auth/refresh', '/api/auth/session', '/api/auth/session/active-role'].includes(pathname);
      const previousBarrier = actor.captureBarrier || Promise.resolve();
      const capture = (async () => {
        if (authResponse) await previousBarrier;
        try {
          if (authResponse) {
            await authCheckpoint(actor, 'AUTH_HEADERS_COOKIE_BODY_UNREAD', { path: pathname, status: response.status() });
            if (actor.lastAuthIntent?.path === pathname) actor.lastAuthIntent.state = 'HEADERS_RECEIVED';
            if (!response.ok()) {
              const startupGuest = pathname === '/api/auth/refresh' && actor.phase === 'login' && [401,403].includes(response.status());
              if (!startupGuest) { actor.authRejected = true; throw Error('Auth write/read rejected; hold'); }
              if (actor.lastAuthIntent?.path === pathname) actor.lastAuthIntent.state = 'KNOWN_STARTUP_GUEST_REJECTED';
              await authCheckpoint(actor, 'KNOWN_STARTUP_GUEST_REFRESH_REJECTED', { path: pathname, status: response.status() });
              actor.authPending.delete(pathname); return;
            }
            const body = await response.json();
            if (body.accessToken) {
              const claim = JSON.parse(Buffer.from(body.accessToken.split('.')[1], 'base64url').toString('utf8'));
              registerSid(actor, claim, body.accessToken, pathname);
              await authCheckpoint(actor, 'ACCEPTED_ISSUED_SID_TOKEN_BEFORE_DEPENDENT_API', { path: pathname });
            }
            const snap = body.session || body;
            if (typeof snap.sessionId === 'string') {
              if (String(snap.userId) !== userId) actor.foreignActor = true;
              assert(!actor.foreignActor && snap.sessionId === actor.currentSid && actor.sessions.has(snap.sessionId), 'Accepted snapshot must qualify its own captured SID');
              actor.sessions.get(snap.sessionId).qualified = true;
              actor.lastSnapshot = snap;
              record.sidCaptured = true; record.actualOwnerMatches = !actor.foreignActor;
            }
            if (pathname === '/api/auth/session/active-role') {
              assert.equal(snap.activeRole, role); assert.equal(snap.sessionId, actor.initialSid);
            }
            if (['/api/auth/login', '/api/auth/tma', '/api/auth/refresh', '/api/auth/session/active-role'].includes(pathname)) {
              record.loginAcknowledged = true;
              if (client === 'PWA') actor.cookieHash = cookieFingerprint(await ownCookie(actor));
            }
            if (actor.lastAuthIntent?.path === pathname) actor.lastAuthIntent.state = 'ACKNOWLEDGED_HANDLES_SAVED';
            await authCheckpoint(actor, 'ACCEPTED_AUTH_RESPONSE_BEFORE_DEPENDENT_API', { path: pathname, status: response.status() });
            actor.authPending.delete(pathname);
            persist();
          } else if (response.ok() && pathname === '/api/v1/teacher/catalog') actor.catalogs.push({ at: Date.now(), startedAt: actor.requestStarts.get(response.request()), body: await response.json() });
          else if (response.ok() && pathname === '/api/v1/teacher/stats') actor.stats.push({ at: Date.now(), startedAt: actor.requestStarts.get(response.request()), body: await response.json(), query: new URL(response.url()).searchParams });
        } catch {
          actor.captureError = true;
          if (actor.lastAuthIntent?.path === pathname) actor.lastAuthIntent.state = 'UNKNOWN_OR_CAPTURE_HOLD';
          try { await authCheckpoint(actor, 'AUTH_CAPTURE_HOLD_NO_DEPENDENT_FORWARD', { path: pathname, status: response.status() }); } catch {}
        }
        finally { if (authResponse && (actor.authPending.has(pathname) || actor.lastAuthIntent?.path === pathname)) { actor.releaseForwardBarrier?.(); actor.releaseForwardBarrier = null; } }
      })();
      if (authResponse) actor.captureBarrier = capture;
      job(capture);
    });
    await page.route('**/*', async route => {
      const req = route.request(), pathname = new URL(req.url()).pathname, method = req.method();
      if (new URL(req.url()).origin !== base) { result.blockedWrites.push({ actor: actor.name, reason: 'FOREIGN_ORIGIN_BLOCKED' }); persist(); return route.abort('blockedbyclient'); }
      if (!pathname.startsWith('/api/')) return route.continue();
      // Accepted Auth headers/body/latest handles are durable before every dependent API forward.
      if (!await captureGate(actor)) return route.abort('blockedbyclient');
      if (['GET', 'HEAD'].includes(method)) return route.continue();
      if (method === 'POST' && pathname === '/api/auth/ws-ticket') return route.continue();
      if (result.runtimeEvidenceFailure) return route.abort('blockedbyclient');
      if (method === 'DELETE' && actor.retireAdmission && !actor.initialRevokeForwarded
        && pathname === '/api/auth/sessions/' + actor.initialSid) {
        const headers = await req.allHeaders(), admission = actor.retireAdmission;
        assert(client === 'TMA' && role === 'HEADMAN' && actor.phase === 'cleanup');
        assert.equal(actor.successorSid, admission.actorSid); assert.equal(actor.currentSid, admission.actorSid);
        assert.equal(actor.initialSid, admission.targetSid);
        assert(headers.authorization === 'Bearer ' + admission.bearer, 'Exact own successor bearer required');
        actor.initialRevokeForwarded = true; persist(); return route.continue();
      }
      if (method === 'POST' && pathname === '/api/auth/logout' && actor.logoutAdmission && !actor.logoutForwarded) {
        const admission = actor.logoutAdmission, headers = await req.allHeaders();
        assert.equal(actor.currentSid, admission.sid); assert(!actor.foreignActor && !actor.unexpectedSuccessor);
        if (client === 'PWA') {
          assert(!headers.authorization, 'Native PWA logout must be cookie-only');
          const cookie = headers.cookie || '';
          assert(cookie.split(/;\s*/).some(pair => pair === 'rct_refresh=' + admission.cookieValue), 'Actual native cookie header must match exact owned pointer');
          actor.record.cleanup.actualNativeCookieMatched = true;
        } else assert(headers.authorization === 'Bearer ' + admission.bearer, 'Native bearer must match canonical owned SID');
        actor.logoutForwarded = true; persist(); return route.continue();
      }
      if (method === 'POST' && ['/api/auth/login', '/api/auth/tma', '/api/auth/refresh'].includes(pathname)) {
        const count = actor.authWrites[pathname] = (actor.authWrites[pathname] || 0) + 1;
        const expected = client === 'TMA' ? '/api/auth/tma' : '/api/auth/login';
        const permitted = pathname === expected || (client !== 'TMA' && pathname === '/api/auth/refresh');
        const normalReadmission = actor.phase === 'observe' && role === 'HEADMAN'
          && pathname === (client === 'TMA' ? '/api/auth/tma' : '/api/auth/refresh') && !actor.readmissionForwarded;
        const initial = actor.phase === 'login' && count === 1;
        // Forward only the product owner's bounded normal readmission, never a harness retry.
        if (permitted && (initial || normalReadmission) && !actor.authPending.has(pathname) && !actor.authRejected && !actor.foreignActor && !actor.unexpectedSuccessor) {
          if (normalReadmission) actor.readmissionForwarded = true;
          actor.authPending.add(pathname);
          authWriteBarrier(actor);
          actor.lastAuthIntent = { method, path: pathname, ordinal: count, requestId: crypto.randomUUID(), state: 'FORWARD_UNKNOWN' };
          await authCheckpoint(actor, 'AUTH_FORWARD_UNKNOWN', { method, path: pathname, ordinal: count });
          persist(); return route.continue();
        }
      } else if (method === 'PUT' && pathname === '/api/auth/session/active-role' && actor.phase === 'login') {
        const body = req.postDataJSON();
        if (body.role === role && !actor.roleForwarded && body.expectedSessionVersion === actor.lastSnapshot.sessionVersion) {
          actor.roleForwarded = true; actor.authPending.add(pathname);
          authWriteBarrier(actor);
          actor.lastAuthIntent = { method, path: pathname, sid: actor.initialSid, role, expectedSessionVersion: body.expectedSessionVersion, requestId: crypto.randomUUID(), state: 'FORWARD_UNKNOWN' };
          await authCheckpoint(actor, 'ROLE_PUT_INTENT_FSYNCED_BEFORE_FORWARD'); persist(); return route.continue();
        }
      } else if (role === 'ADMIN' && method === 'POST' && pathname === '/api/academic/groups/' + fixture.groupId + '/deletions' && deleteArmed && result.confirmRequests === 0) {
        const key = req.headers()['idempotency-key']; assert(UUID.test(key || ''), 'Original UUID is required before forwarding');
        const body = req.postDataJSON(); assert.equal(body.previewDigest, result.previewDigest); assert(body.password === cfg.admin.password, 'Current-password body must match RAM selector');
        assert(!actor.foreignActor && !actor.unexpectedSuccessor && actor.initialSid);
        result.confirmRequests++; result.operationId = key;
        save('original-operation.json', { operationId: key, groupId: fixture.groupId, adminSid: actor.initialSid, state: 'FORWARD_UNKNOWN', requests: 1 }, true);
        persist(); return route.continue();
      }
      result.blockedWrites.push({ actor: actor.name, path: pathname, method }); persist();
      return route.abort('blockedbyclient');
    });
    if (client === 'TMA') await installOwnedTelegramHost(page, role === 'HEADMAN' ? 900000199 : 900000198, goFile);
    const loginPath = client === 'TMA' ? '/api/auth/tma' : '/api/auth/login';
    const reply = page.waitForResponse(r => new URL(r.url()).pathname === loginPath && r.request().method() === 'POST');
    await page.goto(base + (client === 'WEB' ? '/login' : '/'), { waitUntil: 'domcontentloaded' });
    if (client !== 'TMA') {
      assert.equal(new URL(page.url()).origin, base, 'Credentials must only reach exact own origin');
      if (client === 'WEB') { await page.locator('#login').fill(credentials.login); await page.locator('#password').fill(credentials.password); }
      else { await page.getByLabel('Логин', { exact: true }).fill(credentials.login); await page.locator('input[name="password"]').fill(credentials.password); }
      await page.getByRole('button', { name: 'Войти', exact: true }).click();
    }
    assert.equal((await reply).status(), 200, 'Single native login must acknowledge; no retry');
    await waitUntil(() => !!actor.lastSnapshot); await settle();
    if (actor.lastSnapshot.activeRole !== role) {
      const choice = client === 'WEB' ? page.locator('.web-header-role').getByRole('button', { name: labels[role], exact: true }) : page.locator('.profile-role-list').getByRole('button').filter({ hasText: labels[role] });
      await choice.click();
      await waitUntil(() => actor.lastSnapshot?.activeRole === role); await settle();
    }
    const canonicalSnapshot = await canonical(actor); assert.equal(canonicalSnapshot.activeRole, role);
    if (role === 'HEADMAN') assert(canonicalSnapshot.roles.some(g => g.role === role && String(g.groupId) === fixture.groupId), 'Only the owned target Headman grant may be selected');
    if (role !== 'ADMIN') await actor.observer.ready();
    actor.phase = 'mounted'; persist(); return actor;
  }
  async function mount(actor) {
    const page = actor.page;
    if (actor.role === 'HEADMAN') {
      if (actor.client === 'WEB') { await page.locator('a[href="/headman/subjects"]').first().click(); await page.waitForURL(actor.base + '/headman/subjects'); }
      else { await page.getByRole('navigation', { name: 'Основная навигация' }).getByRole('button', { name: 'Ещё', exact: true }).click(); await page.getByRole('button', { name: 'Предметы', exact: true }).click(); }
      actor.privateView = page.getByText('ОкончательноеУдаление ' + fixture.caseId.slice(0, 8), { exact: true }).first();
      await actor.privateView.waitFor({ state: 'visible' });
      actor.record.targetPrivateVisibleBefore = true;
    } else {
      if (actor.client === 'WEB') { await page.locator('a[href="/teacher/statistics"]').first().click(); await page.waitForURL(actor.base + '/teacher/statistics'); }
      else await page.getByRole('navigation', { name: 'Основная навигация' }).getByRole('button', { name: 'Итоги', exact: true }).click();
      actor.ownerLocator = page.locator(actor.client === 'WEB' ? '[data-screen-id="WEB-TEACHER-STATISTICS"]' : '.teacher-stats');
      await actor.ownerLocator.waitFor();
      await waitUntil(() => actor.catalogs.some(x => x.body.groups?.some(g => String(g.id) === fixture.groupId) && x.body.groups?.some(g => String(g.id) === fixture.controlGroupId)));
      if (actor.client === 'WEB') {
        await page.getByRole('button', { name: 'По студентам группы', exact: true }).click();
        actor.groupSelect = page.getByLabel('Группа', { exact: true });
        assert.equal(await actor.groupSelect.locator('option').filter({ hasText: fixture.groupName }).count(), 1);
        await actor.groupSelect.selectOption(fixture.controlGroupId);
        await page.getByLabel('Предмет', { exact: true }).selectOption(fixture.controlSubjectId);
        await page.getByRole('checkbox', { name: 'Лекция', exact: true }).check();
        actor.rows = page.locator('.teacher-stats-table tbody th[scope="row"]');
      } else {
        await page.getByRole('button', { name: 'По студентам', exact: true }).click();
        const group = page.getByRole('button', { name: 'Группа', exact: true }); await group.click();
        await page.getByRole('option', { name: fixture.groupName, exact: true }).waitFor({ state: 'visible' });
        await page.getByRole('option', { name: fixture.controlGroupName, exact: true }).click();
        await page.getByRole('button', { name: 'Предмет', exact: true }).click();
        await page.getByRole('option').filter({ hasText: 'СохранённаяГруппа ' + fixture.caseId.slice(0, 8) }).click();
        const lecture = page.getByRole('button', { name: 'Лекция', exact: true });
        if (await lecture.getAttribute('aria-pressed') !== 'true') await lecture.click();
        actor.rows = page.locator('.teacher-stats__row-name');
      }
      await waitUntil(() => actor.stats.some(x => String(x.query.get('groupId')) === fixture.controlGroupId && x.body.students?.some(s => String(s.studentId) === fixture.controlStudent.id)));
      actor.controlQuery = actor.stats.filter(x => x.query.get('groupId') === fixture.controlGroupId && x.query.get('subjectId') === fixture.controlSubjectId).at(-1)?.query.toString();
      assert(actor.controlQuery, 'Exact control query must be captured before the hint');
      await actor.rows.first().waitFor();
      actor.controlRows = await actor.rows.allTextContents(); assert(actor.controlRows.length > 0);
      if (actor.client === 'WEB') {
        await page.getByRole('button', { name: 'Фильтр: Присутствия', exact: true }).click();
        actor.draft = page.locator('.teacher-column-filter input[type="number"]').first();
      } else {
        await page.getByRole('button', { name: 'Фильтры', exact: true }).click();
        actor.draft = page.locator('#teacher-stats-present-min');
      }
      await actor.draft.fill('37');
      actor.owner = await actor.ownerLocator.elementHandle();
      actor.draftHandle = await actor.draft.elementHandle();
      assert(await actor.draft.evaluate(el => document.activeElement === el));
      actor.record.controlSelectedBefore = true; actor.record.targetOptionVisibleBefore = true;
    }
    await actor.observer.ready(); await settle();
  }
  async function cohortShot(actor, stage) {
    for (const input of await actor.page.locator('input[type="password"]').all()) assert(!await input.isVisible(), 'No credential screen may be captured');
    const file = actor.name + '-' + stage + '.png';
    await actor.page.screenshot({ path: path.join(PRIVATE, file), fullPage: true });
    fs.chmodSync(path.join(PRIVATE, file), 0o600);
    (actor.record.screenshots ||= []).push({ file, sha256: hash(fs.readFileSync(path.join(PRIVATE, file))), credentialFree: true });
  }
  async function cleanup(actor) {
    await settle(); assert(!actor.unexpectedSuccessor && !actor.foreignActor && actor.authPending.size === 0);
    await canonical(actor);
    actor.phase = 'cleanup';
    if (actor.successorSid) {
      assert(actor.client === 'TMA' && actor.role === 'HEADMAN' && actor.currentSid === actor.successorSid);
      const proof = JSON.parse(fs.readFileSync(path.join(PRIVATE, 'cohort-after.json'), 'utf8'));
      const original = proof.auth.sessions.find(s => s.sid === actor.initialSid);
      assert(original && original.userId === actor.userId && !original.selected && !original.revoked);
      actor.retireAdmission = { actorSid: actor.successorSid, targetSid: actor.initialSid, bearer: actor.bearer };
      actor.lastAuthIntent = { method: 'DELETE', path: '/api/auth/sessions/' + actor.initialSid,
        actorSid: actor.successorSid, targetSid: actor.initialSid, requestId: crypto.randomUUID(), state: 'FORWARD_UNKNOWN' };
      await authCheckpoint(actor, 'EXACT_INITIAL_SID_REVOKE_INTENT_FSYNCED');
      const status = await actor.page.evaluate(async init => {
        const response = await fetch(init.path, { method: 'DELETE', credentials: 'include', redirect: 'error',
          headers: { Authorization: 'Bearer ' + init.bearer }, signal: AbortSignal.timeout(7000) });
        return response.status;
      }, { path: actor.lastAuthIntent.path, bearer: actor.bearer });
      assert(actor.initialRevokeForwarded && status === 204, 'Original own SID revocation unconfirmed; no retry/fallback');
      actor.lastAuthIntent.state = 'ACKNOWLEDGED_204';
      await authCheckpoint(actor, 'EXACT_INITIAL_SID_REVOKE_ACKNOWLEDGED');
      actor.record.initialSidEndpointRevocation204 = true;
      await canonical(actor);
    }
    let cookie;
    if (actor.client === 'PWA') {
      cookie = await ownCookie(actor);
      assert.equal(cookieFingerprint(cookie), actor.cookieHash, 'Cookie pointer changed outside correlated owner response; hold');
    }
    const headers = {};
    if (actor.client !== 'PWA') headers.Authorization = 'Bearer ' + actor.bearer;
    // Exact native contracts: PWA cookie-only; TMA/web current owned Bearer. No refresh before logout.
    actor.record.cleanup = { state: 'FORWARD_UNKNOWN', cookieOnly: actor.client === 'PWA', exactSid: true }; persist();
    await authCheckpoint(actor, 'NATIVE_LOGOUT_FORWARD_UNKNOWN');
    actor.logoutAdmission = { sid: actor.currentSid, bearer: actor.bearer, cookieValue: cookie?.value };
    // Browser-native cookie handling is essential for Secure cookies on this HTTP loopback UI origin.
    const status = await actor.page.evaluate(async init => {
      const response = await fetch('/api/auth/logout', { method: 'POST', credentials: 'include', redirect: 'error', headers: init, signal: AbortSignal.timeout(7000) });
      return response.status;
    }, headers);
    assert(actor.logoutForwarded && status === 204, 'Logout unconfirmed; no retry/fallback');
    // The withdrawn Headman role-picker has no universal logout button. Close this acknowledged owned
    // browser context immediately, so endpoint cleanup cannot leave a live old owner to re-authenticate.
    await actor.context.close(); actor.contextClosed = true;
    const readOnly = await request.newContext();
    try {
      const guest = await readOnly.get(actor.base + '/api/auth/session', { headers: { Authorization: 'Bearer ' + actor.bearer, Origin: actor.base }, timeout: 7000, maxRedirects: 0 });
      assert.equal(guest.status(), 401);
    } finally { await readOnly.dispose(); }
    actor.record.cleanup = { ...actor.record.cleanup, state: 'CONFIRMED_204_GUEST_401' }; persist();
    save(actor.name + '-handle.json', { state: 'NATIVE_LOGOUT_CONFIRMED_204_GUEST_401', contextId: actor.contextId, userId: actor.userId, initialSid: actor.initialSid, observedSids: [...actor.sids], successorSid: actor.successorSid || null, contextClosed: true });
  }
  try {
    browser = await chromium.launch({ headless: true, ...(process.env.RCT_CHROMIUM_EXECUTABLE ? { executablePath: process.env.RCT_CHROMIUM_EXECUTABLE } : {}) });
    monitor = setInterval(() => { if (guardStopped) return; const r = resources(); result.samples.push({ at: Date.now(), ...r }); if (r.diskAvailable < 2684354560 || r.memoryAvailable < 536870912) void safeStop(); }, 250);
    const admin = await createActor('WEB', clients[0][1], 'ADMIN');
    for (const [client, base] of clients) for (const role of ['HEADMAN', 'TEACHER']) await mount(await createActor(client, base, role));
    for (const a of actors.filter(a => a.role !== 'ADMIN')) await cohortShot(a, 'before');
    assert.equal(new Set(actors.map(a => a.initialSid)).size, 7); await settle();
    assert(!result.runtimeEvidenceFailure);
    assert(actors.every(a => !a.foreignActor && !a.unexpectedSuccessor && !a.authRejected && !a.authPending.size));
    // Own queues drain after every fixture/login-induced event and before SQL notification counts.
    python([path.join(ROOT, 'cohort-proof.py'), 'quiesce', '--go', goFile]);
    python(['postproof.py', 'before', '--go', goFile]);
    persist(); python([path.join(ROOT, 'cohort-proof.py'), 'before', '--go', goFile]);
    const page = admin.page; await page.locator('a[href="/admin/groups"]').first().click(); await page.waitForURL(admin.base + '/admin/groups');
    await page.getByPlaceholder('Код группы').fill(fixture.groupName);
    await page.getByRole('button', { name: 'Открыть группу ' + fixture.groupName, exact: true }).click();
    const response = page.waitForResponse(r => r.request().method() === 'GET' && new URL(r.url()).pathname === '/api/academic/groups/' + fixture.groupId + '/deletion-preview');
    await page.getByRole('button', { name: 'Показать последствия удаления', exact: true }).click();
    const previewResponse = await response; assert.equal(previewResponse.status(), 200); const preview = await previewResponse.json();
    assert.equal(String(preview.groupId), fixture.groupId); assert.equal(preview.groupName, fixture.groupName);
    const keys = ['accountsAffected', 'memberships', 'headmanAssignments', 'assistantAssignments', 'academicRows', 'scheduleRows', 'attendanceRows', 'authGrants'];
    assert(keys.every(k => Number.isSafeInteger(preview.counts[k]) && preview.counts[k] >= 0));
    assert(preview.counts.memberships === 2 && preview.counts.scheduleRows > 0 && preview.counts.attendanceRows > 0);
    result.checks.numericPreview = true; result.previewDigest = preview.previewDigest; result.previewCounts = preview.counts;
    const dialog = page.locator('[role="dialog"],[role="alertdialog"]').filter({ hasText: 'Удалить ' + fixture.groupName + ' окончательно?' });
    const password = dialog.getByLabel('Твой текущий пароль'); assert.equal(await password.getAttribute('autocomplete'), 'current-password');
    // Credential-free screenshot only, before filling the current password.
    await page.screenshot({ path: path.join(PRIVATE, 'numeric-preview.png'), fullPage: true });
    fs.chmodSync(path.join(PRIVATE, 'numeric-preview.png'), 0o600);
    for (const a of actors.filter(a => a.role !== 'ADMIN')) {
      if (a.role === 'TEACHER') assert(await a.draftHandle.evaluate(el => el.isConnected && document.activeElement === el && el.value === '37'));
      await a.observer.ready(); a.observer.begin(); a.phase = 'observe'; a.started = Date.now();
    }
    result.resourcesBeforeSingleConfirm = resources();
    assert(result.resourcesBeforeSingleConfirm.diskAvailable >= 2684354560 && result.resourcesBeforeSingleConfirm.memoryAvailable >= 1073741824, 'Seven-context actual resource headroom insufficient; no confirmation');
    assert.equal(frozen(), packageSha); rootUnchanged(); frontendUnchanged(); await canonical(admin); deleteArmed = true;
    await password.fill(cfg.admin.password);
    await dialog.getByRole('button', { name: 'Удалить окончательно', exact: true }).click();
    await waitUntil(() => !!result.operationId, 10000); assert.equal(result.confirmRequests, 1);
    await waitUntil(async () => {
      const r = await admin.context.request.get(admin.base + '/api/academic/group-deletions/' + result.operationId,
        { headers: { Authorization: 'Bearer ' + admin.bearer, Origin: admin.base }, timeout: 7000, maxRedirects: 0 });
      if (r.status() === 404) return false;
      assert.equal(r.status(), 200, 'Original read-only status unavailable; hold');
      const operation = await r.json(); result.phase = operation.phase; persist();
      assert(operation.phase !== 'CANCELLED', 'Authoritative cancellation; no new confirmation');
      return operation.phase === 'COMPLETED';
    }, 60000, 500);
    result.checks.originalOperationCompleted = true;
    // No cohort navigation/reload/focus/click from confirmation through these passive observations.
    await Promise.all(actors.filter(a => a.role !== 'ADMIN').map(async a => {
      if (a.role === 'HEADMAN') {
        a.record.after = await a.observer.headmanRetired(a.privateView);
        await new Promise(r => setTimeout(r, 1500)); assert.equal(await a.privateView.isVisible(), false);
      } else {
        a.record.after = await a.observer.teacherRetained({ ownerHandle: a.owner, draftLocator: a.draft, draftValue: '37' });
        await waitUntil(() => a.catalogs.some(x => causallyAfter(x.startedAt, a.hintBoundary) && !x.body.groups?.some(g => String(g.id) === fixture.groupId) && x.body.groups?.some(g => String(g.id) === fixture.controlGroupId))
          && a.stats.some(x => causallyAfter(x.startedAt, a.hintBoundary) && x.query.toString() === a.controlQuery && x.body.students?.some(s => String(s.studentId) === fixture.controlStudent.id)));
        assert.deepEqual(await a.rows.allTextContents(), a.controlRows);
        assert(await a.draftHandle.evaluate(el => el.isConnected && document.activeElement === el && el.value === '37'));
        assert.equal((await canonical(a)).activeRole, 'TEACHER');
        a.record.after.controlRowsPreserved = true; a.record.after.focusAndDraft37Preserved = true;
        a.record.after.targetAbsentFromRefreshedCatalog = true;
        a.record.after.catalogAndStatsRequestsStartedAfterExactHint = true;
      }
    }));
    await waitUntil(() => actors.every(a => !a.authPending.size && [...a.sids].every(sid => a.sessions.get(sid)?.qualified)));
    await settle(); assert.equal(result.blockedWrites.length, 0); assert(!actors.some(a => a.pageError || a.foreignActor || a.unexpectedSuccessor || a.authPending.size));
    python([path.join(ROOT, 'cohort-proof.py'), 'quiesce', '--go', goFile]);
    python(['postproof.py', 'after', '--go', goFile]);
    python([path.join(ROOT, 'cohort-proof.py'), 'after', '--go', goFile]);
    result.checks.sixNaturalViews = true; result.checks.independentSqlMongoPreservation = true;
    for (const a of actors.filter(a => a.role === 'HEADMAN')) a.record.after.targetSelectionClearedSameLiveSidSql = true;
    for (const a of actors.filter(a => a.role !== 'ADMIN')) await cohortShot(a, 'after-passive');
    // Evidence inspection only AFTER passive refresh/focus proof. Opening a menu is not a data refresh.
    for (const a of actors.filter(a => a.role === 'TEACHER')) {
      if (a.client === 'WEB') {
        assert.equal(await a.groupSelect.locator('option').filter({ hasText: fixture.groupName }).count(), 0);
        assert.equal(await a.groupSelect.inputValue(), fixture.controlGroupId);
      } else {
        await a.page.getByRole('button', { name: 'Группа', exact: true }).click();
        assert.equal(await a.page.getByRole('option', { name: fixture.groupName, exact: true }).count(), 0);
        await a.page.getByRole('option', { name: fixture.controlGroupName, exact: true }).waitFor();
      }
      a.record.after.targetAbsentFromActualOptions = true;
    }
    await page.screenshot({ path: path.join(PRIVATE, 'delete-completed.png'), fullPage: true });
    fs.chmodSync(path.join(PRIVATE, 'delete-completed.png'), 0o600);
    for (const a of [...actors.filter(a => a.role !== 'ADMIN'), admin]) await cleanup(a);
    python([path.join(ROOT, 'cohort-proof.py'), 'closed', '--go', goFile]);
    assert.equal(frozen(), packageSha); rootUnchanged(); frontendUnchanged();
    const bridged = restoreReceipt(result, actors,
      JSON.parse(fs.readFileSync(path.join(PRIVATE, 'cohort-closed.json'), 'utf8')),
      JSON.parse(fs.readFileSync(path.join(V8, 'private/native-after.json'), 'utf8')), fixture, packageSha);
    // Mandatory existing restore input is created exactly once, only after real correlated proof/cleanup.
    durableWrite(path.join(V8, 'private/native-case-result.json'), bridged, true);
    result.allPassed = bridged.allPassed; result.cleanupComplete = bridged.cleanupComplete;
    complete = true; result.state = 'COMPLETE';
  } catch (e) {
    result.state = 'UNKNOWN_HOLD'; result.error = { name: ['AssertionError', 'TimeoutError', 'Error', 'TypeError'].includes(e?.name) ? e.name : 'Error', stage: result.operationId ? 'original-operation-or-post-observation' : 'cohort-or-preview' };
  } finally {
    result.finishedAt = new Date().toISOString(); result.resourcesAfter = resources(); persist();
    if (complete || guardStopped) {
      if (monitor) clearInterval(monitor);
      if (browser) await browser.close().catch(() => undefined);
      result.browserClosed = true; persist();
    }
  }
  if (!complete && !guardStopped) {
    // Keep all exact handles and browser owners alive for Root read-only resolution; do not blind-cleanup.
    console.error('UNKNOWN_HOLD: original UUID/SIDs retained privately; no re-confirmation/auth retry/logout/calendar restore.');
    await new Promise(() => {});
  }
  process.exitCode = complete ? 0 : 2;
  console.log(complete ? 'Six live views and one original UI deletion completed; private receipt retained.' : 'Owned browser guard stopped; original handles preserved.');
}
if (require.main === module) main().catch(() => { console.error('Cohort stopped before admission/initialization; no automatic retry.'); process.exitCode = 1; });
module.exports = { durableWrite, captureGate, registerSid, causallyAfter, restoreReceipt };
