import { describe, expect, it, vi } from 'vitest'
import { AuthPayloadError, AuthProtocolError, AuthRequestError, createAuthClient } from './auth-client'

const sessionId = '00000000-0000-4000-8000-000000000001'

function authSession(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    sessionId,
    userId: '42',
    displayName: 'Анна Смирнова',
    groupLabel: 'ИВТ-21',
    sessionVersion: '1',
    rolesVersion: '2',
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
    ...overrides,
  }
}

function response(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('generated Auth client adapter', () => {
  it('uses cookie and bearer routes and adapts a validated current session', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(response({ accessToken: 'login-token', expiresIn: 3600 }))
      .mockResolvedValueOnce(response(authSession()))
    const client = createAuthClient({ fetcher, accessToken: () => 'login-token' })

    await expect(client.login({ login: 'anna', password: 'pass' })).resolves.toEqual({
      accessToken: 'login-token',
      expiresIn: 3600,
    })
    await expect(client.getSnapshot()).resolves.toMatchObject({
      sessionId,
      userId: '42',
      activeRole: 'STUDENT',
      roles: [{ grantId: '17', role: 'STUDENT', selectable: true }],
    })

    const [, loginInit] = fetcher.mock.calls[0] ?? []
    expect(fetcher.mock.calls[0]?.[0]).toBe('/api/auth/login')
    expect(loginInit?.credentials).toBe('include')
    expect(JSON.parse(String(loginInit?.body))).toEqual({ login: 'anna', password: 'pass' })
    const [, sessionInit] = fetcher.mock.calls[1] ?? []
    expect(new Headers(sessionInit?.headers).get('Authorization')).toBe('Bearer login-token')
  })

  it('accepts an explicit null active role without selecting an alternative', async () => {
    const client = createAuthClient({ fetcher: vi.fn<typeof fetch>().mockResolvedValue(response(authSession({ activeRole: null }))) })
    await expect(client.getSnapshot()).resolves.toMatchObject({ activeRole: null, roles: [{ role: 'STUDENT' }] })
  })

  it.each([
    ['session id', { sessionId: 'not-a-uuid' }],
    ['user id', { userId: '0' }],
  ])('rejects a malformed canonical %s', async (_label, overrides) => {
    const client = createAuthClient({ fetcher: vi.fn<typeof fetch>().mockResolvedValue(response(authSession(overrides))) })
    await expect(client.getSnapshot()).rejects.toBeInstanceOf(AuthPayloadError)
  })

  it('rejects repeated grant ids instead of collapsing authority', async () => {
    const grants = [
      { grantId: '17', role: 'STUDENT', status: 'ACTIVE', selectable: true, readOnly: false },
      { grantId: '17', role: 'HEADMAN', status: 'ACTIVE', selectable: true, readOnly: false },
    ]
    const client = createAuthClient({ fetcher: vi.fn<typeof fetch>().mockResolvedValue(response(authSession({ roles: grants }))) })
    await expect(client.getSnapshot()).rejects.toMatchObject({ name: 'AuthPayloadError', code: 'UNKNOWN' })
  })

  it('rejects repeated roles even when grant and group contexts differ', async () => {
    const grants = [
      { grantId: '17', role: 'STUDENT', status: 'ACTIVE', groupId: '17', selectable: true, readOnly: false },
      { grantId: '18', role: 'STUDENT', status: 'ACTIVE', groupId: '18', selectable: true, readOnly: false },
    ]
    const client = createAuthClient({ fetcher: vi.fn<typeof fetch>().mockResolvedValue(response(authSession({ roles: grants }))) })
    await expect(client.getSnapshot()).rejects.toMatchObject({ name: 'AuthPayloadError', code: 'UNKNOWN' })
  })

  it('preserves the machine code from ErrorResponse extras', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(response({
      status: 409,
      title: 'Конфликт',
      detail: 'Версия сессии устарела',
      extras: { code: 'SESSION_VERSION_CONFLICT' },
    }, 409))
    const client = createAuthClient({ fetcher })
    await expect(client.selectRole({ role: 'STUDENT', expectedSessionVersion: '1' })).rejects.toMatchObject({
      name: 'AuthRequestError',
      code: 'SESSION_VERSION_CONFLICT',
      serverCode: 'SESSION_VERSION_CONFLICT',
      status: 409,
    } satisfies Partial<AuthRequestError>)
  })

  it('requires the selected role and a fresh token in the role response', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(response({
      accessToken: 'student-role-token',
      expiresIn: 3600,
      session: authSession({ activeRole: 'STUDENT' }),
    }))
    const client = createAuthClient({ fetcher, accessToken: () => 'old-token' })
    await expect(client.selectRole({ role: 'STUDENT', expectedSessionVersion: '1' })).resolves.toMatchObject({
      accessToken: 'student-role-token',
      session: { activeRole: 'STUDENT' },
    })
    const [, init] = fetcher.mock.calls[0] ?? []
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer old-token')
  })

  it('uses generated cursor query shapes and keeps security mutations typed', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(response({
        items: [{
          sessionId,
          authMethod: 'PASSWORD',
          clientLabel: 'Chrome',
          locationLabel: null,
          createdAt: '2026-09-13T08:00:00Z',
          lastSeenAt: '2026-09-13T09:00:00Z',
          current: true,
        }],
        nextCursor: 'history-page-2',
      }))
      .mockResolvedValueOnce(response({
        items: [{ id: 'event-1', type: 'LOGIN', occurredAt: '2026-09-13T08:00:00Z', authMethod: 'PASSWORD' }],
        nextCursor: null,
      }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const client = createAuthClient({ fetcher, accessToken: () => 'profile-token' })

    await expect(client.listSessions({ cursor: 'cursor/1', limit: 20 })).resolves.toMatchObject({
      nextCursor: 'history-page-2',
      items: [{ sessionId, authMethod: 'PASSWORD', current: true }],
    })
    await expect(client.listHistory({ limit: 20 })).resolves.toMatchObject({
      nextCursor: null,
      items: [{ id: 'event-1', type: 'LOGIN', authMethod: 'PASSWORD' }],
    })
    await expect(client.changePassword({ currentPassword: 'old', newPassword: 'new' })).resolves.toBeUndefined()
    await expect(client.logoutAll()).resolves.toBeUndefined()

    expect(fetcher.mock.calls[0]?.[0]).toBe('/api/auth/sessions?cursor=cursor%2F1&limit=20')
    expect(fetcher.mock.calls[1]?.[0]).toBe('/api/auth/account-history?limit=20')
    expect(fetcher.mock.calls[2]?.[0]).toBe('/api/auth/change-password')
    expect(JSON.parse(String(fetcher.mock.calls[2]?.[1]?.body))).toEqual({ currentPassword: 'old', newPassword: 'new' })
    expect(fetcher.mock.calls[3]?.[0]).toBe('/api/auth/logout-all')
    expect(fetcher.mock.calls[3]?.[1]?.body).toBeUndefined()
  })

  it('keeps typed server cursor errors available for the profile retry action', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(response({
      title: 'Курсор устарел',
      detail: 'Запроси первую страницу',
      extras: { code: 'INVALID_CURSOR' },
    }, 400))
    const client = createAuthClient({ fetcher })

    await expect(client.listHistory({ cursor: 'stale' })).rejects.toMatchObject({
      code: 'INVALID_CURSOR',
      serverCode: 'INVALID_CURSOR',
      status: 400,
    } satisfies Partial<AuthRequestError>)
  })

  it.each([
    ['change-password', (client: ReturnType<typeof createAuthClient>) => client.changePassword({ currentPassword: 'old', newPassword: 'new' })],
    ['logout-all', (client: ReturnType<typeof createAuthClient>) => client.logoutAll()],
    ['terminate-session', (client: ReturnType<typeof createAuthClient>) => client.terminateSession(sessionId)],
  ] as const)('rejects a non-204 %s response as an Auth protocol error', async (_operation, invoke) => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(response({}, 200))
    const client = createAuthClient({ fetcher, accessToken: () => 'profile-token' })

    await expect(invoke(client)).rejects.toMatchObject({
      name: 'AuthProtocolError',
      status: 200,
    } satisfies Partial<AuthProtocolError>)
  })

  it.each([
    ['change-password', (client: ReturnType<typeof createAuthClient>) => client.changePassword({ currentPassword: 'old', newPassword: 'new' })],
    ['logout-all', (client: ReturnType<typeof createAuthClient>) => client.logoutAll()],
    ['terminate-session', (client: ReturnType<typeof createAuthClient>) => client.terminateSession(sessionId)],
  ] as const)('rejects an empty 205 %s response', async (_operation, invoke) => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 205 }))
    const client = createAuthClient({ fetcher, accessToken: () => 'profile-token' })

    await expect(invoke(client)).rejects.toMatchObject({
      name: 'AuthProtocolError',
      status: 205,
    } satisfies Partial<AuthProtocolError>)
  })

  it('accepts exact empty 204 responses for both durable mutations', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 204, headers: { 'Content-Length': '0' } }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const client = createAuthClient({ fetcher, accessToken: () => 'profile-token' })

    await expect(client.changePassword({ currentPassword: 'old', newPassword: 'new' })).resolves.toBeUndefined()
    await expect(client.logoutAll()).resolves.toBeUndefined()
    expect(fetcher).toHaveBeenCalledTimes(2)
  })
  it('uses the selected own session exact DELETE contract and keeps not-found typed', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(new Response(null, { status: 204 })).mockResolvedValueOnce(response({ extras: { code: 'SESSION_NOT_FOUND' }, detail: 'Не найдена' }, 404))
    const client = createAuthClient({ fetcher, accessToken: () => 'actor-token' })
    await expect(client.terminateSession(sessionId)).resolves.toBeUndefined()
    expect(fetcher.mock.calls[0]?.[0]).toBe(`/api/auth/sessions/${sessionId}`)
    expect(fetcher.mock.calls[0]?.[1]?.method).toBe('DELETE')
    expect(fetcher.mock.calls[0]?.[1]?.body).toBeUndefined()
    await expect(client.terminateSession(sessionId)).rejects.toMatchObject({ status: 404, code: 'SESSION_NOT_FOUND' })
    await expect(client.terminateSession('../other')).rejects.toBeInstanceOf(AuthPayloadError)
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

})
