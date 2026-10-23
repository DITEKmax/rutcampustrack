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
    await new Promise<void>(resolve => setImmediate(resolve))
    slowRefresh.resolve(Response.json({ accessToken: 'obsolete-token' }))
    await Promise.all([oldRefresh, changeRole])
    expect(webSession.phase.value).toBe('ready')
    expect(webSession.busy.value).toBe(false)
    expect(webSession.snapshot.value).toMatchObject({ activeRole: 'STUDENT', sessionVersion: '2' })
    expect(webSession.accessToken()).toBe('current-token')
  })
})
