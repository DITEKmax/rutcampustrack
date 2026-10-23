import { afterEach, describe, expect, it, vi } from 'vitest'

function snapshot(user: '42' | '43') {
  return {
    sessionId:
      user === '42'
        ? '00000000-0000-4000-8000-000000000001'
        : '00000000-0000-4000-8000-000000000002',
    userId: user,
    displayName: user === '42' ? 'Анна' : 'Борис',
    sessionVersion: '1',
    rolesVersion: '1',
    activeRole: 'STUDENT',
    readOnly: false,
    roles: [
      {
        grantId: user,
        role: 'STUDENT',
        status: 'ACTIVE',
        groupId: '1',
        selectable: true,
        readOnly: false,
      },
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

describe('web API request ownership', () => {
  it('never applies an old completion to a newly signed-in account after delayed refresh', async () => {
    let releaseRefresh!: (response: Response) => void
    const refresh = new Promise<Response>((resolve) => {
      releaseRefresh = resolve
    })
    let refreshStarted!: () => void
    const refreshing = new Promise<void>((resolve) => {
      refreshStarted = resolve
    })
    let currentUser: '42' | '43' = '42'
    const ownCompletion = { '42': false, '43': false }
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path.endsWith('/login')) {
        const login = (JSON.parse(String(init?.body)) as { login: string }).login
        currentUser = login === 'anna' ? '42' : '43'
        return Response.json({ accessToken: `token-${currentUser}` })
      }
      if (path.endsWith('/logout')) return new Response(null, { status: 204 })
      if (path.endsWith('/session')) return Response.json(snapshot(currentUser))
      if (path.endsWith('/refresh')) {
        refreshStarted()
        return refresh
      }
      if (path.endsWith('/completion')) {
        const token = new Headers(init?.headers).get('Authorization')
        if (token === 'Bearer token-42')
          return Response.json({ code: 'INVALID_SESSION' }, { status: 401 })
        if (token === 'Bearer token-43') {
          ownCompletion['43'] = true
          return Response.json({ id: '1', completed: true, completedAt: '2026-10-24T10:00:00Z' })
        }
      }
      throw new Error(`Unexpected request: ${path}`)
    })
    const { webSession } = await import('../auth/session')
    const { createStudentApi, WebAuthorityChangedError } = await import('./api')
    await webSession.login('anna', 'password')
    const annaApi = createStudentApi()
    const oldCompletion = annaApi.setHomeworkCompletion('1', { completed: true })
    // Attach the expected rejection immediately so the test never leaves an unhandled request.
    const result = oldCompletion.catch((error) => error as unknown)
    await refreshing
    await webSession.logout()
    await webSession.login('boris', 'password')
    releaseRefresh(Response.json({ accessToken: 'obsolete-anna-token' }))
    expect(await result).toBeInstanceOf(WebAuthorityChangedError)
    expect(webSession.snapshot.value?.userId).toBe('43')
    expect(ownCompletion['43']).toBe(false)
    expect(ownCompletion['42']).toBe(false)
  })

  it('allows the same account and role to finish a completion after normal token refresh', async () => {
    let refreshed = false
    let completed = false
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path.endsWith('/login')) return Response.json({ accessToken: 'expired-token' })
      if (path.endsWith('/session')) return Response.json(snapshot('42'))
      if (path.endsWith('/refresh')) {
        refreshed = true
        return Response.json({ accessToken: 'current-token' })
      }
      if (path.endsWith('/completion')) {
        if (new Headers(init?.headers).get('Authorization') !== 'Bearer current-token')
          return Response.json({ code: 'INVALID_SESSION' }, { status: 401 })
        completed = true
        return Response.json({ id: '1', completed: true, completedAt: '2026-10-24T10:00:00Z' })
      }
      throw new Error(`Unexpected request: ${path}`)
    })
    const { webSession } = await import('../auth/session')
    const { createStudentApi } = await import('./api')
    await webSession.login('anna', 'password')
    const api = createStudentApi()
    await expect(api.setHomeworkCompletion('1', { completed: true })).resolves.toMatchObject({
      completed: true,
    })
    expect(webSession.snapshot.value?.userId).toBe('42')
    expect(refreshed).toBe(true)
    expect(completed).toBe(true)
  })

  it('keeps a late previous-account read out of the current visible state', async () => {
    let releaseRead!: (response: Response) => void
    const heldRead = new Promise<Response>((resolve) => {
      releaseRead = resolve
    })
    let currentUser: '42' | '43' = '42'
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path.endsWith('/login')) {
        const login = (JSON.parse(String(init?.body)) as { login: string }).login
        currentUser = login === 'anna' ? '42' : '43'
        return Response.json({ accessToken: `token-${currentUser}` })
      }
      if (path.endsWith('/logout')) return new Response(null, { status: 204 })
      if (path.endsWith('/session')) return Response.json(snapshot(currentUser))
      if (path === '/api/academic/users/me') return heldRead
      throw new Error(`Unexpected request: ${path}`)
    })
    const { webSession } = await import('../auth/session')
    const { createWebTransport, WebAuthorityChangedError } = await import('./api')
    await webSession.login('anna', 'password')
    const transport = createWebTransport()
    let visibleProfile: unknown = null
    const reading = transport
      .fetcher('/api/academic/users/me')
      .then(async (response) => {
        const profile: unknown = await response.json()
        transport.assertCurrent()
        visibleProfile = profile
      })
      .catch((error) => error as unknown)
    await webSession.logout()
    await webSession.login('boris', 'password')
    releaseRead(Response.json({ userId: '42', displayName: 'Анна' }))
    expect(await reading).toBeInstanceOf(WebAuthorityChangedError)
    expect(webSession.snapshot.value?.userId).toBe('43')
    expect(visibleProfile).toBeNull()
  })
})
