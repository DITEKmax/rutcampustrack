import { afterEach, describe, expect, it, vi } from 'vitest'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

function session(version: string) {
  return {
    sessionId: '00000000-0000-4000-8000-000000000001',
    userId: '42',
    displayName: 'Анна Смирнова',
    sessionVersion: version,
    rolesVersion: '1',
    activeRole: 'STUDENT',
    readOnly: false,
    roles: [
      { grantId: '1', role: 'STUDENT', status: 'ACTIVE', selectable: true, readOnly: false },
      { grantId: '2', role: 'HEADMAN', status: 'ACTIVE', selectable: true, readOnly: false },
    ],
    passwordPolicy: {
      minCodePoints: 12,
      maxUtf8Bytes: 72,
      requiresDecimalDigit: true,
      specialCategories: ['P', 'S'],
      normalization: 'NONE',
    },
  }
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.resetModules()
})

describe('authoritative web session concurrency', () => {
  it('recovers a role conflict after an earlier token refresh becomes obsolete', async () => {
    const slowRefresh = deferred<Response>()
    const conflictReceived = deferred<void>()
    let holdRefresh = true
    let version = '1'
    vi.stubGlobal('fetch', async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path.endsWith('/login')) return Response.json({ accessToken: 'login-token' })
      if (path.endsWith('/active-role')) {
        version = '2'
        conflictReceived.resolve()
        return Response.json({ title: 'Сессия изменилась' }, { status: 409 })
      }
      if (path.endsWith('/refresh')) {
        if (holdRefresh) {
          holdRefresh = false
          return slowRefresh.promise
        }
        return Response.json({ accessToken: 'current-token' })
      }
      if (path.endsWith('/session')) return Response.json(session(version))
      throw new Error(`Unexpected request: ${path}`)
    })
    const { webSession } = await import('./session')
    await webSession.login('anna', 'password')
    const oldRefresh = webSession.refresh()
    const changeRole = webSession.selectRole('HEADMAN')
    await conflictReceived.promise
    // Drain the conflict response so its reconciliation is waiting on the held refresh.
    await new Promise<void>((resolve) => setImmediate(resolve))
    slowRefresh.resolve(Response.json({ accessToken: 'obsolete-token' }))
    await Promise.all([oldRefresh, changeRole])
    expect(webSession.phase.value).toBe('ready')
    expect(webSession.busy.value).toBe(false)
    expect(webSession.snapshot.value).toMatchObject({ activeRole: 'STUDENT', sessionVersion: '2' })
    expect(webSession.accessToken()).toBe('current-token')
  })
})

describe('guest authentication lifetime', () => {
  it('does not open an abandoned QR session after its delayed exchange returns', async () => {
    const exchange = deferred<{ accessToken: string }>()
    vi.stubGlobal('fetch', async () => Response.json(session('1')))
    const { webSession } = await import('./session')
    webSession.invalidate()
    let mounted = true
    const authentication = webSession.authenticate(
      () => exchange.promise,
      () => mounted,
    )
    mounted = false
    webSession.cancelAuthentication()
    exchange.resolve({ accessToken: 'abandoned-token' })
    await authentication
    expect(webSession.phase.value).toBe('guest')
    expect(webSession.snapshot.value).toBeNull()
    expect(webSession.accessToken()).toBeNull()
    expect(webSession.busy.value).toBe(false)
  })

  it('publishes OTP login only with the authoritative role/session snapshot', async () => {
    const authority = deferred<Response>()
    vi.stubGlobal('fetch', async (input: RequestInfo | URL) => {
      if (String(input).endsWith('/verify-by-code'))
        return Response.json({ accessToken: 'otp-token' })
      return authority.promise
    })
    const { webSession } = await import('./session')
    webSession.invalidate()
    const authentication = webSession.loginOtp('123456')
    await new Promise<void>((resolve) => setImmediate(resolve))
    expect(webSession.snapshot.value).toBeNull()
    expect(webSession.phase.value).toBe('guest')
    authority.resolve(Response.json(session('1')))
    await authentication
    expect(webSession.phase.value).toBe('ready')
    expect(webSession.snapshot.value?.roles).toHaveLength(2)
    webSession.cancelAuthentication()
    expect(webSession.phase.value).toBe('ready')
  })
})

describe('unknown login recovery ownership', () => {
  it.each(['resolve', 'reject'] as const)(
    'keeps successor login intact when obsolete recovery %ss',
    async (outcome) => {
      let resolveRefresh!: (response: Response) => void
      let rejectRefresh!: (cause: Error) => void
      const slowRefresh = new Promise<Response>((resolve, reject) => {
        resolveRefresh = resolve
        rejectRefresh = reject
      })
      const recoveryStarted = deferred<void>()
      const successorAuthorityStarted = deferred<void>()
      const successorAuthority = deferred<Response>()
      vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input)
        if (path.endsWith('/login')) {
          if (JSON.parse(String(init?.body)).login === 'anna')
            throw new TypeError('A response lost')
          return Response.json({ accessToken: 'B-token' })
        }
        if (path.endsWith('/refresh')) {
          recoveryStarted.resolve()
          return slowRefresh
        }
        if (path.endsWith('/session')) {
          successorAuthorityStarted.resolve()
          return successorAuthority.promise
        }
        throw new Error(path)
      })
      const { webSession } = await import('./session')
      const obsolete = webSession.login('anna', 'password')
      await recoveryStarted.promise
      webSession.cancelAuthentication()
      const successor = webSession.login('boris', 'password')
      await successorAuthorityStarted.promise
      if (outcome === 'resolve') resolveRefresh(Response.json({ accessToken: 'obsolete-token' }))
      else rejectRefresh(new TypeError('Obsolete recovery failed'))
      await obsolete
      expect(webSession.phase.value).toBe('guest')
      expect(webSession.accessToken()).toBe('B-token')
      expect(webSession.error.value).toBeNull()
      successorAuthority.resolve(Response.json({ ...session('1'), userId: '43' }))
      await successor
      expect(webSession.phase.value).toBe('ready')
      expect(webSession.snapshot.value?.userId).toBe('43')
      expect(webSession.error.value).toBeNull()
    },
  )
})
