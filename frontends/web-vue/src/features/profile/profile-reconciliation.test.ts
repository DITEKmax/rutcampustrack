import { effectScope, type EffectScope } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
vi.mock('../../app/live', () => ({ useWebLiveRefresh() {} }))
let scope: EffectScope | undefined
const target = '00000000-0000-4000-8000-000000000002'
function snapshot(user = '42') {
  return {
    sessionId: '00000000-0000-4000-8000-000000000001',
    userId: user,
    displayName: user,
    sessionVersion: '1',
    rolesVersion: '1',
    activeRole: 'HEADMAN',
    readOnly: false,
    roles: [
      {
        grantId: user,
        role: 'HEADMAN',
        status: 'ACTIVE',
        selectable: true,
        readOnly: false,
        groupId: '1',
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
function summary() {
  return {
    sessionId: target,
    authMethod: 'PASSWORD',
    createdAt: '2026-10-24T09:00:00Z',
    lastSeenAt: '2026-10-24T09:00:00Z',
    current: false,
  }
}
afterEach(() => {
  scope?.stop()
  scope = undefined
  vi.unstubAllGlobals()
  vi.resetModules()
})
describe('profile uncertain session revocation', () => {
  it('confirms a lost revoke acknowledgement through complete session readback', async () => {
    let sessions = [summary()]
    vi.stubGlobal('navigator', { onLine: true })
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path.endsWith('/login')) return Response.json({ accessToken: 'A' })
      if (path.endsWith('/session')) return Response.json(snapshot())
      if (path.endsWith('/sessions/' + target) && init?.method === 'DELETE') {
        sessions = []
        throw new TypeError('Response lost after commit')
      }
      if (path.includes('/sessions')) return Response.json({ items: sessions, nextCursor: null })
      if (path.includes('/account-history')) return Response.json({ items: [], nextCursor: null })
      if (path.endsWith('/users/me'))
        return Response.json({
          id: 42,
          firstName: 'Анна',
          lastName: 'Тест',
          telegramId: null,
          avatarId: null,
        })
      throw new Error(path)
    })
    const { webSession } = await import('../../auth/session')
    await webSession.login('A', 'password')
    const { useWebProfile } = await import('./use-profile')
    scope = effectScope()
    const profile = scope.run(() => useWebProfile(() => false))!
    await profile.load()
    await profile.state.terminateSession(target)
    expect(profile.state.view.sessions).toEqual([])
    expect(profile.uncertain.value).toBe(false)
    expect(webSession.snapshot.value?.userId).toBe('42')
  })

  it('keeps an unresolved session visible and blocks another mutation while readback still contains it', async () => {
    vi.stubGlobal('navigator', { onLine: true })
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input)
      if (path.endsWith('/login')) return Response.json({ accessToken: 'A' })
      if (path.endsWith('/session')) return Response.json(snapshot())
      if (path.endsWith('/sessions/' + target) && init?.method === 'DELETE')
        throw new TypeError('Network unavailable')
      if (path.includes('/sessions')) return Response.json({ items: [summary()], nextCursor: null })
      if (path.includes('/account-history')) return Response.json({ items: [], nextCursor: null })
      if (path.endsWith('/users/me'))
        return Response.json({
          id: 42,
          firstName: 'Анна',
          lastName: 'Тест',
          telegramId: null,
          avatarId: null,
        })
      throw new Error(path)
    })
    const { webSession } = await import('../../auth/session')
    await webSession.login('A', 'password')
    const { useWebProfile } = await import('./use-profile')
    scope = effectScope()
    const profile = scope.run(() => useWebProfile(() => false))!
    await profile.load()
    await expect(profile.state.terminateSession(target)).rejects.toThrow(
      'Результат операции неизвестен',
    )
    expect(profile.state.view.sessions[0]?.sessionId).toBe(target)
    expect(profile.uncertain.value).toBe(true)
    expect(webSession.snapshot.value?.userId).toBe('42')
  })
})

describe('profile history cursor chain', () => {
  it('keeps the boundary event reachable when a head refresh overlaps an older page', async () => {
    let newerHead = false
    let resolveMore!: (value: Response) => void
    let moreStarted!: () => void
    const started = new Promise<void>((resolve) => {
      moreStarted = resolve
    })
    const held = new Promise<Response>((resolve) => {
      resolveMore = resolve
    })
    const event = (id: string) => ({ id, type: 'LOGIN', occurredAt: '2026-10-24T09:00:00Z' })
    vi.stubGlobal('navigator', { onLine: true })
    vi.stubGlobal('fetch', async (input: RequestInfo | URL) => {
      const path = String(input)
      if (path.endsWith('/login')) return Response.json({ accessToken: 'A' })
      if (path.endsWith('/session')) return Response.json(snapshot())
      if (path.includes('/sessions')) return Response.json({ items: [summary()], nextCursor: null })
      if (path.includes('/account-history')) {
        const cursor = new URL(path, 'http://fixture.invalid').searchParams.get('cursor')
        if (cursor === 'e28') {
          moreStarted()
          return held
        }
        if (cursor === 'e29')
          return Response.json({ items: ['e28', 'e27', 'e26', 'e25'].map(event), nextCursor: null })
        return Response.json({
          items: (newerHead ? ['e31', 'e30', 'e29'] : ['e30', 'e29', 'e28']).map(event),
          nextCursor: newerHead ? 'e29' : 'e28',
        })
      }
      if (path.endsWith('/users/me'))
        return Response.json({
          id: 42,
          firstName: 'Анна',
          lastName: 'Тест',
          telegramId: null,
          avatarId: null,
        })
      throw new Error(path)
    })
    const { webSession } = await import('../../auth/session')
    await webSession.login('A', 'password')
    const { useWebProfile } = await import('./use-profile')
    scope = effectScope()
    const profile = scope.run(() => useWebProfile(() => false))!
    await profile.load()
    const more = profile.loadHistory(true)
    await started
    newerHead = true
    const refresh = profile.load()
    resolveMore(Response.json({ items: ['e27', 'e26', 'e25'].map(event), nextCursor: 'e25' }))
    await Promise.all([more, refresh])
    await profile.loadHistory(true)
    expect(profile.state.view.history.map((item) => item.id)).toEqual([
      'e31',
      'e30',
      'e29',
      'e28',
      'e27',
      'e26',
      'e25',
    ])
    expect(profile.state.view.historyNextCursor).toBeNull()
    expect(profile.historyBusy.value).toBe(false)
  })
})
