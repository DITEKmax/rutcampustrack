import { readFileSync } from 'node:fs'
import { parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import { ref, toRaw, type Ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthRequestError } from './auth-client'
import { PwaAuthError, usePwaAuth } from './auth'
import { createPwaFetcher } from './pwa-version'
import { isOfflineBootstrapRecoveryError } from './bootstrap-policy'
import { assertAuthBffCoherence } from './session-coherence'
import { StudentApiError } from '../../mobile-core/src/api/student-client'
import { ProfileRequestError } from '../../mobile-core/src/features/profile/profile-types'
import type { ProfileSnapshot } from '../../mobile-core/src/features/profile/profile-types'
import type { StudentSession } from '../../mobile-core/src/api/types'
import {
  StaleSessionGenerationError,
  studentFeatureScope,
  studentFeatureScopeIdentity,
  studentOfflineScopeKey,
} from '../../mobile-core/src/shared/session-owner'

// Exact complete production handlers, real auth/StudentApi/version transport. Only
// browser/storage boundaries are adapted; this is not a copied bootstrap model.
const source = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')
const script = ts.createSourceFile(
  'App.ts',
  parse(source).descriptor.scriptSetup!.content,
  ts.ScriptTarget.Latest,
  true,
)
const names = [
  'isPasswordRecoveryView',
  'fetchAuthForCurrentGeneration',
  'bootstrap',
  'goOffline',
  'goOnline',
  'handleExternalInvalidation',
  'invalidateOwnerSynchronously',
  'clearOwnerSnapshot',
  'observeCleanupResult',
  'isConfirmedOnlineAuthDenial',
  'authDenialStatus',
  'handleOnlineAuthDenial',
  'fetchStudentCandidate',
  'activateCandidate',
  'assertCandidateCurrent',
  'isPersistedSnapshot',
  'persistedScope',
  'makeSnapshot',
]
const declarations = names.map((name) => {
  const node = script.statements.find((n) => ts.isFunctionDeclaration(n) && n.name?.text === name)
  if (!node) throw new Error(`Missing production handler ${name}`)
  return node.getText(script)
})
const authDeclaration = script.statements
  .filter(ts.isVariableStatement)
  .flatMap((n) => [...n.declarationList.declarations])
  .find((n) => ts.isIdentifier(n.name) && n.name.text === 'auth')!
const authOptions = (authDeclaration.initializer as ts.CallExpression)
  .arguments[0] as ts.ObjectLiteralExpression
const refreshPolicy = authOptions.properties.find(
  (n) => ts.isPropertyAssignment(n) && n.name.getText(script) === 'canRefresh',
) as ts.PropertyAssignment | undefined
const retry = script.statements.find(
  (n) => ts.isFunctionDeclaration(n) && n.name?.text === 'retryConnection',
)
if (retry) declarations.push(retry.getText(script))
const refNames = new Set<string>()
for (const text of declarations) {
  const node = ts.createSourceFile('handler.ts', text, ts.ScriptTarget.Latest, true)
  const visit = (n: ts.Node) => {
    if (
      ts.isPropertyAccessExpression(n) &&
      ts.isIdentifier(n.expression) &&
      n.name.text === 'value'
    )
      refNames.add(n.expression.text)
    ts.forEachChild(n, visit)
  }
  visit(node)
}
const queue = ['pendingOnlineGeneration', 'queuedBootstrapAfterInvalidation'].map((name) => {
  const node = script.statements.find(
    (n) =>
      ts.isVariableStatement(n) &&
      n.declarationList.declarations.some((d) => ts.isIdentifier(d.name) && d.name.text === name),
  )
  if (!node) throw new Error(`Missing production queue ${name}`)
  return node.getText(script)
})
const profile: ProfileSnapshot = {
  sessionId: '00000000-0000-4000-8000-000000000001',
  userId: '42',
  displayName: 'Synthetic',
  sessionVersion: '3',
  rolesVersion: '4',
  activeRole: 'STUDENT',
  readOnly: false,
  roles: [{ grantId: '17', role: 'STUDENT', status: 'ACTIVE', selectable: true, readOnly: false }],
  passwordPolicy: {
    minCodePoints: 12,
    maxUtf8Bytes: 72,
    requiresDecimalDigit: true,
    specialCategories: ['P', 'S'],
    normalization: 'NONE',
  },
}
const session = {
  user: { id: '42', displayName: 'Synthetic' },
  group: null,
  semester: null,
  activeRole: 'STUDENT',
  sessionId: profile.sessionId,
  sessionVersion: '3',
  rolesVersion: '4',
  readOnly: false,
} as StudentSession
const response = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const cleanups: (() => void)[] = []
afterEach(() => {
  cleanups.splice(0).forEach((fn) => fn())
  vi.unstubAllGlobals()
})
async function settle(predicate: () => boolean) {
  for (let i = 0; i < 100 && !predicate(); i++)
    await new Promise((resolve) => setImmediate(resolve))
  expect(predicate()).toBe(true)
}
async function setup() {
  let resolveRefresh: ((value: Response) => void) | undefined
  let releaseCurrent: (() => void) | undefined
  let holdCurrent = false
  let rejectRefresh: ((reason: unknown) => void) | undefined
  let holdRefresh = false
  let denyStudent = false
  let studentStatus = 200
  let currentStatus = 200
  let currentProblemCode: string | undefined
  let currentProblemBody: unknown
  let currentNetwork = false
  let currentProfile = profile
  const calls: string[] = []
  const fetcher: typeof fetch = async (input, init) => {
    const url = String(input)
    calls.push(`${init?.method ?? 'GET'} ${url}`)
    if (url === '/api/auth/session') {
      if (holdCurrent) {
        holdCurrent = false
        const held = response(currentProblemBody ?? currentProfile, currentStatus)
        return new Promise<Response>((resolve) => {
          releaseCurrent = () => resolve(held)
        })
      }
      if (currentNetwork) throw new TypeError('Synthetic network boundary')
      return response(
        currentProblemBody ??
          (currentProblemCode ? { extras: { code: currentProblemCode } } : currentProfile),
        currentStatus,
      )
    }
    if (url === '/api/auth/refresh') {
      if (holdRefresh)
        return new Promise<Response>((resolve, reject) => {
          rejectRefresh = reject
          resolveRefresh = resolve
        })
      return response({ accessToken: 'synthetic-refreshed', expiresIn: 3600 })
    }
    if (url === '/api/v1/student/today') return response({}, 401)
    if (url === '/api/v1/student/session')
      return response(session, denyStudent ? 401 : studentStatus)
    throw new Error(`Unexpected synthetic request ${url}`)
  }
  vi.stubGlobal('navigator', { onLine: true })
  const deps: Record<string, unknown> = {
    PwaAuthError,
    AuthRequestError,
    StudentApiError,
    ProfileRequestError,
    StaleSessionGenerationError,
    isOfflineBootstrapRecoveryError,
    assertAuthBffCoherence,
    studentFeatureScope,
    studentFeatureScopeIdentity,
    studentOfflineScopeKey,
    toRaw,
    currentFetcher: () => createPwaFetcher(fetcher),
    isAssistantPermissionReadForbidden: () => false,
    push: { invalidate: async () => {} },
    themeMirror: null,
    adminAuthorityOwner: { bind() {} },
    snapshotStore: {
      invalidatePendingWrites() {},
      hasUnresolvedCleanup: () => false,
      clearDetailed: async () => ({ retryRequired: false }),
      clearMismatchedCurrent: async () => null,
      read: async () => null,
      switchToDetailed: async () => ({ retryRequired: false }),
    },
    replacePasswordResetRouteWithLogin() {},
  }
  const state: Record<string, Ref> = {}
  for (const name of refNames) state[name] = ref(null)
  Object.assign(deps, state)
  for (const name of [
    'NotificationsApiError',
    'TeacherApiError',
    'HeadmanScheduleApiError',
    'HeadmanJournalApiError',
    'HeadmanRequestsApiError',
    'HeadmanHomeworkApiError',
    'HeadmanStatsApiError',
    'HeadmanGroupApiError',
    'HeadmanSubjectsApiError',
    'AdminAccessApiError',
    'AdminSemesterApiError',
    'AdminDashboardApiError',
    'AdminGroupsApiError',
    'AdminUsersApiError',
  ])
    deps[name] = class UnusedApiError extends Error {}
  const unsupported = () => {
    throw new Error('Unexpected uncovered role/storage branch')
  }
  for (const name of [
    'fetchHeadmanCandidate',
    'activateHeadmanCandidate',
    'fetchTeacherCandidate',
    'activateTeacherCandidate',
    'activateMapRole',
    'loadOfflineSnapshot',
    'handleProfileInvalidated',
  ])
    deps[name] = unsupported
  type RecoveryControl = {
    bootstrap: (options?: { refresh?: boolean; currentOnly?: boolean }) => Promise<void>
    goOnline: () => void
    goOffline: () => void
    handleExternalInvalidation: () => void
    recover: () => Promise<void>
    canRefresh: (generation: number) => boolean
    queued: () => boolean
  }
  const policyOwner: { control?: RecoveryControl } = {}
  const auth = usePwaAuth({
    fetcher: createPwaFetcher(fetcher),
    logoutMarkerStorage: null,
    channel: { postMessage() {}, addEventListener() {}, removeEventListener() {}, close() {} },
    // The real App uses the same generation-bound admission policy.
    canRefresh: (generation: number) => policyOwner.control?.canRefresh(generation) ?? true,
  } as Parameters<typeof usePwaAuth>[0])
  deps.auth = auth
  auth.setToken('synthetic-initial')
  await auth.getSessionFor(0)
  const ownerApi = auth.createApi(createPwaFetcher(fetcher))
  for (const [name, value] of Object.entries({
    bootstrapping: false,
    offline: false,
    readOnly: false,
    sessionReady: true,
    authView: 'student',
    authSnapshot: profile,
    featureVisible: true,
    api: ownerApi,
    snapshot: null,
    session,
    scope: studentFeatureScope(session, 0),
    ownerRevision: 1,
  }))
    state[name]!.value = value
  const code = ts.transpileModule(
    `const { ${Object.keys(deps).join(',')} } = deps;
    let cleanupUiGeneration = 0;
    let currentOnlyGeneration = null;
    ${queue.join('\n')}
    ${declarations.join('\n')}
    return { bootstrap, goOnline, goOffline, handleExternalInvalidation,
      recover: ${retry ? 'retryConnection' : '() => Promise.resolve()'},
      canRefresh: ${refreshPolicy ? refreshPolicy.initializer.getText(script) : '() => true'},
      queued: () => pendingOnlineGeneration !== null };`,
    { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None } },
  ).outputText
  const control = new Function('deps', code)(deps) as RecoveryControl
  policyOwner.control = control
  const unsubscribe = auth.subscribeInvalidation(control.handleExternalInvalidation)
  cleanups.push(() => {
    unsubscribe()
    auth.dispose()
  })
  return {
    auth,
    control,
    state,
    calls,
    ownerApi,
    hold: () => {
      holdRefresh = true
    },
    reject: () => {
      holdRefresh = false
      rejectRefresh!(new TypeError('Synthetic network boundary'))
    },
    ready: () => rejectRefresh !== undefined,
    resolveRefresh: () => {
      holdRefresh = false
      resolveRefresh!(response({ accessToken: 'synthetic-refreshed', expiresIn: 3600 }))
    },
    holdCurrent: () => {
      holdCurrent = true
    },
    currentHeld: () => releaseCurrent !== undefined,
    releaseCurrent: () => releaseCurrent!(),
    denyStudent: () => {
      denyStudent = true
    },
    studentStatus: (status: number) => {
      studentStatus = status
    },
    currentStatus: (status: number, code?: string) => {
      currentStatus = status
      currentProblemCode = code
    },
    currentFailure: (status: number, problem: unknown) => {
      currentStatus = status
      currentProblemBody = problem
    },
    currentNetwork: () => {
      currentNetwork = true
    },
    suspend: () => {
      currentProfile = {
        ...profile,
        activeRole: null,
        readOnly: true,
        rolesVersion: '5',
        roles: profile.roles.map((role) => ({
          ...role,
          status: 'SUSPENDED',
          selectable: false,
          readOnly: true,
        })),
      }
    },
  }
}
async function failedReconnect(c: Awaited<ReturnType<typeof setup>>, onlineWhileBusy = false) {
  c.hold()
  // A real bound StudentApi 401, not bootstrap, starts and owns the old refresh.
  const request = c.ownerApi.getToday().catch((error) => error)
  await settle(c.ready)
  expect(c.state.bootstrapping!.value).toBe(false)
  c.control.goOffline()
  c.control.goOnline()
  if (onlineWhileBusy) c.control.goOnline()
  expect(c.calls.filter((x) => x === 'POST /api/auth/refresh')).toHaveLength(1)
  c.reject()
  await request
  await settle(() => !c.state.bootstrapping!.value)
  expect(c.state.offline!.value).toBe(true)
  expect(c.state.readOnly!.value).toBe(true)
}

describe('retained PWA owner recovery after a pre-online Student 401 refresh fails', () => {
  it('re-admits the same owner on explicit retry without another refresh POST', async () => {
    const c = await setup()
    await failedReconnect(c)
    const previousApi = c.state.api!.value
    const previousRevision = c.state.ownerRevision!.value
    await c.control.recover()
    expect(c.state.offline!.value).toBe(false)
    expect(c.state.readOnly!.value).toBe(false)
    expect(c.state.api!.value).toBe(previousApi)
    expect(c.state.ownerRevision!.value).toBe(previousRevision)
    expect(c.calls.filter((x) => x === 'POST /api/auth/refresh')).toHaveLength(1)
    expect(c.calls.slice(-2)).toEqual(['GET /api/auth/session', 'GET /api/v1/student/session'])
  })
  it('keeps a repeated NETWORK failure closed and retains the committed owner', async () => {
    const c = await setup()
    await failedReconnect(c)
    const api = c.state.api!.value
    c.currentNetwork()
    await c.control.recover()
    expect(c.state.offline!.value).toBe(true)
    expect(c.state.readOnly!.value).toBe(true)
    expect(c.state.api!.value).toBe(api)
    expect(c.calls.filter((x) => x === 'POST /api/auth/refresh')).toHaveLength(1)
  })
  it('rechecks suspended grants and closes the previous generation without replay', async () => {
    const c = await setup()
    await failedReconnect(c)
    c.suspend()
    await c.control.recover()
    await settle(() => !c.state.bootstrapping!.value)
    expect(c.state.authView!.value).toBe('role')
    expect(c.state.authSnapshot!.value.roles[0].selectable).toBe(false)
    expect(c.state.readOnly!.value).toBe(true)
    expect(c.auth.currentGeneration()).toBe(1)
    await expect(c.ownerApi.getSession()).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(c.calls.filter((x) => x === 'POST /api/auth/refresh')).toHaveLength(1)
  })
  it('does not turn a downstream Student 401 into a refresh POST after current succeeds', async () => {
    const c = await setup()
    await failedReconnect(c)
    c.denyStudent()
    await c.control.recover()
    expect(c.state.readOnly!.value).toBe(true)
    expect(c.calls.filter((x) => x === 'POST /api/auth/refresh')).toHaveLength(1)
  })
  it('uses explicit login on current 401, invalidating old commands without remote logout', async () => {
    const c = await setup()
    await failedReconnect(c)
    c.currentStatus(401)
    await c.control.recover()
    expect(c.state.authView!.value).toBe('login')
    expect(c.state.bootstrapError!.value).toContain('Войди снова')
    await expect(c.ownerApi.getSession()).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(c.calls.filter((x) => x.startsWith('POST '))).toEqual(['POST /api/auth/refresh'])
  })
  it('uses explicit login on typed current 409 after suspension without another refresh', async () => {
    const c = await setup()
    await failedReconnect(c)
    c.currentStatus(409, 'SESSION_STATE_STALE')
    await c.control.recover()
    expect(c.state.authView!.value).toBe('login')
    expect(c.state.authSnapshot!.value).toBeNull()
    expect(c.state.api!.value).toBeNull()
    expect(c.state.readOnly!.value).toBe(true)
    expect(c.auth.currentGeneration()).toBe(1)
    await expect(c.ownerApi.getSession()).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(c.calls.filter((x) => x.startsWith('POST '))).toEqual(['POST /api/auth/refresh'])
  })
  it('uses explicit re-entry on exact gateway current 403 WRONG_ROLE without refresh or replay', async () => {
    const c = await setup()
    await failedReconnect(c)
    c.currentFailure(403, { status: 403, code: 'WRONG_ROLE' })
    await c.control.recover()
    expect(c.state.authView!.value).toBe('login')
    expect(c.state.authSnapshot!.value).toBeNull()
    expect(c.state.api!.value).toBeNull()
    expect(c.state.readOnly!.value).toBe(true)
    expect(c.auth.currentGeneration()).toBe(1)
    await expect(c.ownerApi.getSession()).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(c.calls.filter((x) => x.startsWith('POST '))).toEqual(['POST /api/auth/refresh'])
  })
  it.each([
    {},
    { status: 403, code: 'UNKNOWN' },
    { status: 401, code: 'WRONG_ROLE' },
    { code: 'WRONG_ROLE' },
    { status: 403, extras: { code: 'WRONG_ROLE' } },
  ])('retains the owner for an unconfirmed current 403 envelope %#', async (problem) => {
    const c = await setup()
    await failedReconnect(c)
    const owner = c.state.api!.value
    c.currentFailure(403, problem)
    await c.control.recover()
    expect(c.state.authView!.value).toBe('student')
    expect(c.state.api!.value).toBe(owner)
    expect(c.state.readOnly!.value).toBe(true)
    expect(c.auth.currentGeneration()).toBe(0)
    expect(c.calls.filter((x) => x.startsWith('POST '))).toEqual(['POST /api/auth/refresh'])
  })
  it('retains the owner when current admission is followed by a Student context 403', async () => {
    const c = await setup()
    await failedReconnect(c)
    const owner = c.state.api!.value
    c.studentStatus(403)
    await c.control.recover()
    expect(c.state.authView!.value).toBe('student')
    expect(c.state.api!.value).toBe(owner)
    expect(c.state.readOnly!.value).toBe(true)
    expect(c.auth.currentGeneration()).toBe(0)
    expect(c.calls.filter((x) => x.startsWith('POST '))).toEqual(['POST /api/auth/refresh'])
  })
  it('does not invalidate a newer authority when an old current 403 arrives late', async () => {
    const c = await setup()
    await failedReconnect(c)
    c.currentFailure(403, { status: 403, code: 'WRONG_ROLE' })
    c.holdCurrent()
    const recovery = c.control.recover()
    await settle(c.currentHeld)
    const generation = c.auth.clear()
    c.auth.accessToken.value = 'synthetic-new-authority'
    const nextApi = c.auth.createApi()
    c.state.api!.value = nextApi
    c.releaseCurrent()
    await recovery
    expect(c.auth.currentGeneration()).toBe(generation)
    expect(c.auth.accessToken.value).toBe('synthetic-new-authority')
    expect(toRaw(c.state.api!.value)).toBe(nextApi)
    expect(c.calls.filter((x) => x.startsWith('POST '))).toEqual(['POST /api/auth/refresh'])
  })
  it('does not apply a late candidate-current 403 from an old refresh to a newer authority', async () => {
    const c = await setup()
    c.hold()
    c.control.goOffline()
    c.control.goOnline()
    await settle(c.ready)
    c.currentFailure(403, { status: 403, code: 'WRONG_ROLE' })
    c.holdCurrent()
    c.resolveRefresh()
    await settle(c.currentHeld)
    const generation = c.auth.clear()
    c.auth.accessToken.value = 'synthetic-new-authority'
    const nextApi = c.auth.createApi()
    c.state.api!.value = nextApi
    c.releaseCurrent()
    await settle(() => !c.state.bootstrapping!.value)
    expect(c.auth.currentGeneration()).toBe(generation)
    expect(c.auth.accessToken.value).toBe('synthetic-new-authority')
    expect(toRaw(c.state.api!.value)).toBe(nextApi)
    expect(c.calls.filter((x) => x.startsWith('POST '))).toEqual(['POST /api/auth/refresh'])
  })
  it('does not blindly retry refresh when another online event queued during the uncertain refresh', async () => {
    const c = await setup()
    await failedReconnect(c, true)
    expect(c.control.queued()).toBe(false)
    expect(c.calls.filter((x) => x === 'POST /api/auth/refresh')).toHaveLength(1)
    await c.control.recover()
    expect(c.state.offline!.value).toBe(false)
    expect(c.calls.filter((x) => x === 'POST /api/auth/refresh')).toHaveLength(1)
  })
})

describe('PWA recovery ordering and generation isolation', () => {
  it('preserves R2: an old refresh fully rejected before online allows normal reconnect', async () => {
    const c = await setup()
    c.hold()
    const request = c.ownerApi.getToday().catch((error) => error)
    await settle(c.ready)
    c.control.goOffline()
    c.reject()
    await request
    c.control.goOnline()
    await settle(() => !c.state.bootstrapping!.value)
    expect(c.state.offline!.value).toBe(false)
    expect(c.calls.filter((x) => x === 'POST /api/auth/refresh')).toHaveLength(2)
  })
  it('preserves normal reconnect and a busy successful queue', async () => {
    const c = await setup()
    c.hold()
    c.control.goOffline()
    c.control.goOnline()
    await settle(c.ready)
    c.control.goOnline()
    c.resolveRefresh()
    await settle(() => !c.state.bootstrapping!.value)
    expect(c.state.offline!.value).toBe(false)
    expect(c.state.readOnly!.value).toBe(false)
    expect(c.calls.filter((x) => x === 'POST /api/auth/refresh')).toHaveLength(2)
  })
  it('retains the read-only gate while current is pending and rejects its late response after invalidation', async () => {
    const c = await setup()
    await failedReconnect(c)
    c.holdCurrent()
    const recovery = c.control.recover()
    await settle(c.currentHeld)
    expect(c.state.offline!.value).toBe(true)
    expect(c.state.readOnly!.value).toBe(true)
    c.auth.clear()
    c.control.handleExternalInvalidation()
    c.releaseCurrent()
    await recovery
    expect(c.state.authView!.value).toBe('login')
    expect(c.state.api!.value).toBeNull()
    expect(c.auth.accessToken.value).toBeNull()
    expect(c.calls.filter((x) => x === 'POST /api/auth/refresh')).toHaveLength(1)
  })
  it('keeps queued readmission current-only after an online event and downstream 401', async () => {
    const c = await setup()
    await failedReconnect(c)
    c.holdCurrent()
    const recovery = c.control.recover()
    await settle(c.currentHeld)
    c.control.goOnline()
    c.denyStudent()
    c.releaseCurrent()
    await recovery
    await settle(() => !c.state.bootstrapping!.value)
    expect(c.state.offline!.value).toBe(true)
    expect(c.state.readOnly!.value).toBe(true)
    expect(c.calls.filter((x) => x === 'POST /api/auth/refresh')).toHaveLength(1)
  })
})

it('blocks an independent old-owner 401 retry after failed readmission without manufacturing logout', async () => {
  const c = await setup()
  await failedReconnect(c)
  c.currentNetwork()
  await c.control.recover()
  await expect(c.ownerApi.getToday()).rejects.toBeInstanceOf(Error)
  expect(c.calls.filter((x) => x === 'POST /api/auth/refresh')).toHaveLength(1)
  expect(c.state.authView!.value).toBe('student')
  expect(c.state.readOnly!.value).toBe(true)
  expect(c.auth.currentGeneration()).toBe(0)
})
