import { describe, expect, it, vi } from 'vitest'
import {
  PwaAuthError,
  usePwaAuth,
  type PwaAuthInvalidationChannel,
  type PwaAuthInvalidationReason,
  type PwaAuthLogoutMarkerStorage,
} from './auth'
import { AuthRequestError } from './auth-client'
import { StaleSessionGenerationError } from '../../mobile-core/src/shared/session-owner'
import { ProfileState } from '../../mobile-core/src/features/profile/profile-state'
import type { ProfileSnapshot } from '../../mobile-core/src/features/profile/profile-types'

const profile: ProfileSnapshot = {
  sessionId: '00000000-0000-4000-8000-000000000001',
  userId: '42',
  displayName: 'Анна Смирнова',
  groupLabel: 'ИВТ-21',
  sessionVersion: '3',
  rolesVersion: '4',
  activeRole: 'STUDENT',
  roles: [{
    grantId: '17',
    role: 'STUDENT',
    status: 'ACTIVE',
    groupId: '17',
    contextLabel: 'ИВТ-21',
    selectable: true,
    readOnly: false,
  }],
  readOnly: false,
  passwordPolicy: {
    minCodePoints: 12,
    maxUtf8Bytes: 72,
    requiresDecimalDigit: true,
    specialCategories: ['P', 'S'],
    normalization: 'NONE',
  },
}

function response(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

type DurableMutation = 'changePassword' | 'logoutAll'

function invokeDurableMutation(state: ProfileState, mutation: DurableMutation): Promise<void> {
  return mutation === 'changePassword'
    ? state.changePassword({ currentPassword: 'old', newPassword: 'new' })
    : state.logoutAll()
}

function emptyMutationResponse(options: {
  contentLength?: string
  text?: string
  textError?: unknown
} = {}): Response {
  const text = options.textError === undefined
    ? vi.fn(async () => options.text ?? '')
    : vi.fn(async () => { throw options.textError })
  return {
    ok: true,
    status: 204,
    headers: new Headers(options.contentLength === undefined ? undefined : { 'Content-Length': options.contentLength }),
    body: null,
    text,
  } as unknown as Response
}

function readableMutationResponse(payload: Uint8Array): {
  response: Response
  text: ReturnType<typeof vi.fn>
  readCount: () => number
} {
  let readCount = 0
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      if (payload.byteLength > 0) controller.enqueue(payload)
      controller.close()
    },
  })
  const text = vi.fn(async () => {
    const reader = body.getReader()
    let byteCount = 0
    while (true) {
      const result = await reader.read()
      readCount += 1
      if (result.done) break
      byteCount += result.value.byteLength
    }
    return byteCount === 0 ? '' : 'unexpected'
  })
  return {
    response: {
      ok: true,
      status: 204,
      headers: new Headers({ 'Content-Length': '0' }),
      body,
      text,
    } as unknown as Response,
    text,
    readCount: () => readCount,
  }
}

const durableMutations: readonly DurableMutation[] = ['changePassword', 'logoutAll']

class TestInvalidationChannel implements PwaAuthInvalidationChannel {
  private readonly listeners = new Set<(event: MessageEvent<unknown>) => void>()
  peer?: TestInvalidationChannel
  closed = false

  postMessage(message: unknown): void {
    this.peer?.listeners.forEach((listener) => listener({ data: message } as MessageEvent<unknown>))
  }

  addEventListener(_type: 'message', listener: (event: MessageEvent<unknown>) => void): void {
    this.listeners.add(listener)
  }

  removeEventListener(_type: 'message', listener: (event: MessageEvent<unknown>) => void): void {
    this.listeners.delete(listener)
  }

  close(): void {
    this.closed = true
    this.listeners.clear()
  }
}

class TestLogoutMarkerStorage implements PwaAuthLogoutMarkerStorage {
  private readonly values = new Map<string, string>()

  constructor(private readonly failWrites = false) {}

  getItem(key: string): string | null {
    return this.values.get(key) ?? null
  }

  setItem(key: string, value: string): void {
    if (this.failWrites) throw new Error('storage quota exceeded')
    this.values.set(key, value)
  }

  removeItem(key: string): void {
    this.values.delete(key)
  }
}

describe('PWA memory session', () => {
  it('uses one refresh request for concurrent bootstrap callers and retains only the access token in memory', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ accessToken: 'memory-only-token' }), { status: 200 }))
    vi.stubGlobal('fetch', fetcher)
    const auth = usePwaAuth()
    await Promise.all([auth.refresh(), auth.refresh()])
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(auth.accessToken.value).toBe('memory-only-token')
    vi.unstubAllGlobals()
  })

  it.each([
    ['HTTP 503', 503],
    ['network failure', 0],
  ] as const)('keeps explicit sign-out locked across reload after %s', async (_label, status) => {
    const storage = new TestLogoutMarkerStorage()
    const fetcher = vi.fn<typeof fetch>()
    if (status === 503) fetcher.mockResolvedValue(new Response(null, { status: 503 }))
    else fetcher.mockRejectedValue(new TypeError('offline'))
    const clearSnapshot = vi.fn().mockResolvedValue(undefined)
    const auth = usePwaAuth({ fetcher, logoutMarkerStorage: storage })
    auth.setToken('old-admin-token')

    const logout = auth.logout(clearSnapshot)
    expect(auth.accessToken.value).toBeNull()
    expect(auth.canAutoBootstrap()).toBe(false)
    await expect(logout).rejects.toMatchObject({
      name: 'PwaAuthError',
      status,
    } satisfies Partial<PwaAuthError>)
    expect(auth.accessToken.value).toBeNull()
    expect(auth.explicitLogoutState()).toBe('unconfirmed')
    expect(auth.canAutoBootstrap()).toBe(false)
    expect(clearSnapshot).toHaveBeenCalledOnce()

    const reloaded = usePwaAuth({ fetcher, logoutMarkerStorage: storage })
    expect(reloaded.explicitLogoutState()).toBe('unconfirmed')
    expect(reloaded.canAutoBootstrap()).toBe(false)
    expect(fetcher).toHaveBeenCalledOnce()
    auth.dispose()
    reloaded.dispose()
  })

  it('keeps confirmed sign-out locked until a successful manual login', async () => {
    const storage = new TestLogoutMarkerStorage()
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(response({ accessToken: 'new-admin-token', expiresIn: 3600 }))
    const auth = usePwaAuth({ fetcher, logoutMarkerStorage: storage })
    auth.setToken('old-admin-token')

    await expect(auth.logout(async () => undefined)).resolves.toBeUndefined()

    expect(auth.explicitLogoutState()).toBe('confirmed')
    expect(auth.canAutoBootstrap()).toBe(false)
    const reloaded = usePwaAuth({ fetcher, logoutMarkerStorage: storage })
    expect(reloaded.canAutoBootstrap()).toBe(false)
    await reloaded.login({ login: 'admin', password: 'secret' })
    expect(reloaded.explicitLogoutState()).toBeNull()
    expect(reloaded.canAutoBootstrap()).toBe(true)
    auth.dispose()
    reloaded.dispose()
  })

  it('does not let a late logout response overwrite a newer manual login', async () => {
    const storage = new TestLogoutMarkerStorage()
    let resolveLogout!: (response: Response) => void
    const delayedLogout = new Promise<Response>((resolve) => { resolveLogout = resolve })
    const fetcher = vi.fn<typeof fetch>()
      .mockReturnValueOnce(delayedLogout)
      .mockResolvedValueOnce(response({ accessToken: 'new-admin-token', expiresIn: 3600 }))
    const auth = usePwaAuth({ fetcher, logoutMarkerStorage: storage })
    auth.setToken('old-admin-token')

    const logout = auth.logout(async () => undefined)
    expect(auth.accessToken.value).toBeNull()
    expect(auth.canAutoBootstrap()).toBe(false)
    await auth.login({ login: 'admin', password: 'secret' })
    expect(auth.accessToken.value).toBe('new-admin-token')
    expect(auth.explicitLogoutState()).toBeNull()

    resolveLogout(new Response(null, { status: 503 }))
    await expect(logout).rejects.toMatchObject({ status: 503 })
    expect(auth.accessToken.value).toBe('new-admin-token')
    expect(auth.explicitLogoutState()).toBeNull()
    expect(auth.canAutoBootstrap()).toBe(true)
    auth.dispose()
  })

  it('closes local authority immediately and lets old bearer device cleanup finish before remote logout', async () => {
    let finish!: () => void
    const cleanup = new Promise<void>((resolve) => { finish = resolve })
    let captured: string | null = null
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 204 }))
    const auth = usePwaAuth({ fetcher, beforeClear: () => { captured = auth.accessToken.value; return cleanup } })
    auth.setToken('old-token')
    const logout = auth.logout(async () => undefined)
    expect(captured).toBe('old-token')
    expect(auth.accessToken.value).toBeNull()
    expect(fetcher).not.toHaveBeenCalled()
    finish()
    await logout
    expect(fetcher).toHaveBeenCalledOnce()
    auth.dispose()
  })

  it('does not remotely revoke a new manual login that replaced an owner during device cleanup', async () => {
    let finish!: () => void
    const cleanup = new Promise<void>((resolve) => { finish = resolve })
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(response({ accessToken: 'new-token' }))
    const auth = usePwaAuth({ fetcher, beforeClear: () => cleanup })
    auth.setToken('old-token')
    const logout = auth.logout(async () => undefined)
    await auth.login({ login: 'anna', password: 'secret' })
    finish()
    await logout
    expect(auth.accessToken.value).toBe('new-token')
    expect(fetcher.mock.calls.some(([url]) => url === '/api/auth/logout')).toBe(false)
    auth.dispose()
  })

  it.each([
    ['HTTP 503', 503],
    ['network failure', 0],
  ] as const)('keeps the in-memory sign-out latch when marker storage fails after %s', async (_label, status) => {
    const storage = new TestLogoutMarkerStorage(true)
    const fetcher = vi.fn<typeof fetch>()
    if (status === 503) {
      fetcher.mockResolvedValueOnce(new Response(null, { status: 503 }))
    } else {
      fetcher.mockRejectedValueOnce(new TypeError('offline'))
    }
    fetcher.mockResolvedValueOnce(response({ accessToken: 'manual-login-token', expiresIn: 3600 }))
    const auth = usePwaAuth({ fetcher, logoutMarkerStorage: storage })
    auth.setToken('old-admin-token')

    await expect(auth.logout(async () => undefined)).rejects.toMatchObject({ status })
    expect(storage.getItem('rct-pwa-explicit-logout-v1')).toBeNull()
    expect(auth.explicitLogoutState()).toBe('unconfirmed')
    // App's initial, online, and retry bootstrap paths all consult this gate.
    expect(auth.canAutoBootstrap()).toBe(false)
    expect(fetcher).toHaveBeenCalledOnce()

    await auth.login({ login: 'admin', password: 'secret' })
    expect(auth.explicitLogoutState()).toBeNull()
    expect(auth.canAutoBootstrap()).toBe(true)
    auth.dispose()
  })

  it.each([
    ['409 version conflict', 409, 'SESSION_VERSION_CONFLICT'],
    ['503 service failure', 503, 'AUTHORITY_UNAVAILABLE'],
  ] as const)('refreshes the active session version after a %s and retries role selection', async (_label, status, code) => {
    const adminProfile: ProfileSnapshot = {
      ...profile,
      activeRole: 'ADMIN',
      sessionVersion: '4',
      roles: [...profile.roles, {
        grantId: '18',
        role: 'ADMIN',
        status: 'ACTIVE',
        selectable: true,
        readOnly: false,
      }],
    }
    const selectedProfile: ProfileSnapshot = { ...profile, sessionVersion: '5' }
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(response({ status, title: 'Не удалось сменить роль', detail: 'Повтори выбор', extras: { code } }, status))
      .mockResolvedValueOnce(response(adminProfile))
      .mockResolvedValueOnce(response({ accessToken: 'student-role-token', expiresIn: 3600, session: selectedProfile }))
    const auth = usePwaAuth({ fetcher, logoutMarkerStorage: null })
    auth.setToken('admin-token')
    const generation = auth.currentGeneration()

    await expect(auth.selectRoleFor(generation, { role: 'STUDENT', expectedSessionVersion: '3' }))
      .rejects.toMatchObject({ status, code })
    const current = await auth.getSessionFor(generation)
    expect(current.sessionVersion).toBe('4')

    const selection = await auth.selectRoleFor(generation, {
      role: 'STUDENT',
      expectedSessionVersion: current.sessionVersion,
    })

    expect(selection.session.activeRole).toBe('STUDENT')
    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({ role: 'STUDENT', expectedSessionVersion: '3' })
    expect(JSON.parse(String(fetcher.mock.calls[2]?.[1]?.body))).toEqual({ role: 'STUDENT', expectedSessionVersion: '4' })
    expect(auth.accessToken.value).toBe('student-role-token')
    auth.dispose()
  })

  it('observes a fast snapshot cleanup rejection before the remote logout resolves', async () => {
    let resolveRemote!: (response: Response) => void
    const remote = new Promise<Response>((resolve) => { resolveRemote = resolve })
    const fetcher = vi.fn().mockReturnValue(remote)
    vi.stubGlobal('fetch', fetcher)
    const cleanupError = new Error('storage unavailable')
    const clearSnapshot = vi.fn().mockRejectedValue(cleanupError)
    const auth = usePwaAuth()
    const unhandled: unknown[] = []
    const onUnhandled = (reason: unknown): void => { unhandled.push(reason) }
    process.on('unhandledRejection', onUnhandled)

    const logout = auth.logout(clearSnapshot)
    await Promise.resolve()
    expect(fetcher).toHaveBeenCalledWith('/api/auth/logout', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      signal: expect.any(AbortSignal),
    }))
    resolveRemote(new Response(null, { status: 204 }))
    await expect(logout).rejects.toBe(cleanupError)
    await new Promise<void>((resolve) => setImmediate(resolve))

    expect(unhandled).toEqual([])
    process.off('unhandledRejection', onUnhandled)
    vi.unstubAllGlobals()
  })

  it('invalidates another tab without sending a token or looping the signal', () => {
    const firstChannel = new TestInvalidationChannel()
    const secondChannel = new TestInvalidationChannel()
    firstChannel.peer = secondChannel
    secondChannel.peer = firstChannel
    const first = usePwaAuth({ channel: firstChannel })
    const second = usePwaAuth({ channel: secondChannel })
    const invalidated = vi.fn()
    second.subscribeInvalidation(invalidated)
    first.setToken('first-tab-token')

    first.clear()

    expect(first.accessToken.value).toBeNull()
    expect(second.accessToken.value).toBeNull()
    expect(second.currentGeneration()).toBe(1)
    expect(invalidated).toHaveBeenCalledOnce()
    expect(firstChannel.closed).toBe(false)
    expect(secondChannel.closed).toBe(false)
    first.dispose()
    second.dispose()
  })

  it('rejects a login response that arrives after its generation was cleared', async () => {
    let resolveLogin!: (response: Response) => void
    const fetcher = vi.fn<typeof fetch>().mockReturnValue(new Promise<Response>((resolve) => { resolveLogin = resolve }))
    const auth = usePwaAuth({ fetcher })
    const pending = auth.login({ login: 'anna', password: 'secret' })
    auth.clear({ broadcast: false })
    resolveLogin(new Response(JSON.stringify({ accessToken: 'late-token' }), { status: 200 }))

    await expect(pending).rejects.toBeInstanceOf(Error)
    expect(auth.accessToken.value).toBeNull()
  })

  it('generation-binds profile reads so a late owner response cannot restore a cleared session', async () => {
    let resolveProfile!: (response: Response) => void
    const fetcher = vi.fn<typeof fetch>().mockReturnValue(new Promise<Response>((resolve) => { resolveProfile = resolve }))
    const auth = usePwaAuth({ fetcher })
    auth.setToken('owner-token')
    const port = auth.createProfilePort(0)
    const pending = port.getSnapshot()

    auth.clear({ broadcast: false })
    resolveProfile(response(profile))

    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(auth.accessToken.value).toBeNull()
    expect(auth.currentGeneration()).toBe(1)
  })

  it('does not invalidate a newer owner when a delayed profile 401 arrives after the generation changed', async () => {
    let resolveProblem!: (value: unknown) => void
    const unauthorized = new Response(null, { status: 401 })
    Object.defineProperty(unauthorized, 'json', {
      value: vi.fn(() => new Promise<unknown>((resolve) => { resolveProblem = resolve })),
    })
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(response(profile))
      .mockResolvedValueOnce(unauthorized)
    const auth = usePwaAuth({ fetcher })
    auth.setToken('owner-token')
    const onInvalidated = vi.fn()
    const state = new ProfileState(auth.createProfilePort(0, { onInvalidated }))
    await state.loadSnapshot()

    const pending = state.loadSessions()
    await vi.waitFor(() => expect(resolveProblem).toBeTypeOf('function'))
    auth.clear({ broadcast: false })
    resolveProblem({ status: 401, title: 'Сессия больше недействительна' })

    await expect(pending).rejects.toMatchObject({ code: 'NETWORK' })
    expect(onInvalidated).not.toHaveBeenCalled()
    expect(auth.accessToken.value).toBeNull()
    expect(auth.currentGeneration()).toBe(1)
  })

  it('keeps typed Auth conflict details available to the role UI', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      status: 409,
      title: 'Конфликт',
      detail: 'Сессия устарела',
      extras: { code: 'SESSION_VERSION_CONFLICT' },
    }), { status: 409 }))
    const auth = usePwaAuth({ fetcher })
    await expect(auth.refresh()).rejects.toBeInstanceOf(AuthRequestError)
  })

  it.each(durableMutations)('preserves the owner and skips invalidation for non-204 %s responses', async (mutation) => {
    const mutationResponses = [
      ['JSON 200', () => response({}, 200), 200],
      ['empty 205', () => new Response(null, { status: 205 }), 205],
    ] as const

    for (const [, makeResponse, status] of mutationResponses) {
      const fetcher = vi.fn<typeof fetch>()
        .mockResolvedValueOnce(response(profile))
        .mockResolvedValueOnce(makeResponse())
      const auth = usePwaAuth({ fetcher })
      auth.setToken('owner-token')
      const onInvalidated = vi.fn()
      const state = new ProfileState(auth.createProfilePort(0, { onInvalidated }))
      await state.loadSnapshot()
      const snapshotBefore = state.view.snapshot

      await expect(invokeDurableMutation(state, mutation)).rejects.toMatchObject({
        name: 'AuthProtocolError',
        status,
      })
      expect(onInvalidated).not.toHaveBeenCalled()
      expect(state.view.snapshot).toBe(snapshotBefore)
      expect(auth.accessToken.value).toBe('owner-token')
      expect(auth.currentGeneration()).toBe(0)
    }
  })

  it.each(durableMutations)('preserves the owner and skips invalidation for malformed empty 204 %s responses', async (mutation) => {
    const malformedResponses = [
      ['Content-Length: 2', () => emptyMutationResponse({ contentLength: '2' })],
      ['negative Content-Length', () => emptyMutationResponse({ contentLength: '-1' })],
      ['invalid Content-Length', () => emptyMutationResponse({ contentLength: 'not-a-number' })],
      ['combined Content-Length', () => emptyMutationResponse({ contentLength: '0, 0' })],
      ['non-empty response body', () => readableMutationResponse(new Uint8Array([1])).response],
      ['non-empty response text', () => emptyMutationResponse({ text: 'unexpected' })],
      ['body read error', () => emptyMutationResponse({ textError: new Error('body read failed') })],
    ] as const

    for (const [, makeResponse] of malformedResponses) {
      const fetcher = vi.fn<typeof fetch>()
        .mockResolvedValueOnce(response(profile))
        .mockResolvedValueOnce(makeResponse())
      const auth = usePwaAuth({ fetcher })
      auth.setToken('owner-token')
      const onInvalidated = vi.fn()
      const state = new ProfileState(auth.createProfilePort(0, { onInvalidated }))
      await state.loadSnapshot()
      const snapshotBefore = state.view.snapshot

      await expect(invokeDurableMutation(state, mutation)).rejects.toMatchObject({
        name: 'AuthProtocolError',
        status: 204,
      })
      expect(onInvalidated).not.toHaveBeenCalled()
      expect(state.view.snapshot).toBe(snapshotBefore)
      expect(auth.accessToken.value).toBe('owner-token')
      expect(auth.currentGeneration()).toBe(0)
    }
  })

  it.each(durableMutations)('consumes a non-null empty ReadableStream and invalidates exactly once for %s', async (mutation) => {
    const readable = readableMutationResponse(new Uint8Array())
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(response(profile))
      .mockResolvedValueOnce(readable.response)
    const auth = usePwaAuth({ fetcher })
    auth.setToken('owner-token')
    const onInvalidated = vi.fn((reason: 'password-changed' | 'logout-all' | 'account-invalidated') => {
      expect(reason).toBe(mutation === 'changePassword' ? 'password-changed' : 'logout-all')
      auth.clear({ broadcast: false })
    })
    const state = new ProfileState(auth.createProfilePort(0, { onInvalidated }))
    await state.loadSnapshot()

    await expect(invokeDurableMutation(state, mutation)).resolves.toBeUndefined()
    expect(readable.text).toHaveBeenCalledTimes(1)
    expect(readable.readCount()).toBe(1)
    expect(onInvalidated).toHaveBeenCalledTimes(1)
    expect(state.view.snapshot).toBeNull()
    expect(auth.accessToken.value).toBeNull()
    expect(auth.currentGeneration()).toBe(1)
  })

  it.each([
    ['changePassword', 'password-changed'],
    ['logoutAll', 'logout-all'],
  ] as const)('invalidates exactly once after an exact empty 204 %s', async (mutation, reason) => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(response(profile))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const auth = usePwaAuth({ fetcher })
    auth.setToken('owner-token')
    const onInvalidated = vi.fn((actualReason: 'password-changed' | 'logout-all' | 'account-invalidated') => {
      expect(actualReason).toBe(reason)
      auth.clear({ broadcast: false })
    })
    const state = new ProfileState(auth.createProfilePort(0, { onInvalidated }))
    await state.loadSnapshot()

    await expect(invokeDurableMutation(state, mutation)).resolves.toBeUndefined()
    expect(onInvalidated).toHaveBeenCalledTimes(1)
    expect(state.view.snapshot).toBeNull()
    expect(auth.accessToken.value).toBeNull()
    expect(auth.currentGeneration()).toBe(1)
  })

  it('probes the refreshed authority before allowing the old StudentApi one retry', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(response(profile))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(response({ accessToken: 'new-token', expiresIn: 3600 }))
      .mockResolvedValueOnce(response(profile))
      .mockResolvedValueOnce(response({ lessons: [] }))
    const auth = usePwaAuth({ fetcher })
    auth.setToken('old-token')
    await auth.getSessionFor(0)

    const api = auth.createApi(fetcher)
    await expect(api.getToday()).resolves.toEqual({ lessons: [] })

    const todayCalls = fetcher.mock.calls.filter(([input]) => String(input).includes('/today'))
    expect(todayCalls).toHaveLength(2)
    expect(new Headers(todayCalls[1]?.[1]?.headers).get('Authorization')).toBe('Bearer new-token')
    expect(fetcher).toHaveBeenCalledTimes(5)
  })

  it.each([
    ['sid', (value: ProfileSnapshot): ProfileSnapshot => ({
      ...value,
      sessionId: '00000000-0000-4000-8000-000000000002',
    })],
    ['user', (value: ProfileSnapshot): ProfileSnapshot => ({ ...value, userId: '43' })],
    ['role', (value: ProfileSnapshot): ProfileSnapshot => ({ ...value, activeRole: null })],
    ['sessionVersion', (value: ProfileSnapshot): ProfileSnapshot => ({ ...value, sessionVersion: '5' })],
    ['rolesVersion', (value: ProfileSnapshot): ProfileSnapshot => ({ ...value, rolesVersion: '6' })],
    ['readOnly', (value: ProfileSnapshot): ProfileSnapshot => ({
      ...value,
      readOnly: true,
      roles: value.roles.map((grant) => ({ ...grant, readOnly: true })),
    })],
    ['group grant context', (value: ProfileSnapshot): ProfileSnapshot => ({
      ...value,
      groupLabel: 'ПМ-22',
      roles: value.roles.map((grant) => ({ ...grant, groupId: '18', contextLabel: 'ПМ-22' })),
    })],
  ] as const)('invalidates the old generation when refresh changes %s', async (_label, change) => {
    const changedProfile = change(profile)
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(response(profile))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(response({ accessToken: 'changed-token', expiresIn: 3600 }))
      .mockResolvedValueOnce(response(changedProfile))
    const auth = usePwaAuth({ fetcher })
    const reasons: PwaAuthInvalidationReason[] = []
    auth.subscribeInvalidation((reason) => reasons.push(reason))
    auth.setToken('old-token')
    await auth.getSessionFor(0)

    const api = auth.createApi(fetcher)
    await expect(api.getToday()).rejects.toBeInstanceOf(StaleSessionGenerationError)

    const todayCalls = fetcher.mock.calls.filter(([input]) => String(input).includes('/today'))
    expect(todayCalls).toHaveLength(1)
    expect(fetcher).toHaveBeenCalledTimes(4)
    expect(auth.currentGeneration()).toBe(1)
    expect(auth.accessToken.value).toBe('changed-token')
    expect(reasons).toEqual(['authority-changed'])
  })
})
