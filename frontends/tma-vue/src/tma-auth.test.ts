import { afterEach, describe, expect, it, vi } from 'vitest'
import { createFixtureTransport, StaleSessionGenerationError, type ReportDownloadTicketRequest } from '@rct/mobile-core'
import { installFixtureTelegramHost, TelegramHost } from './telegram'
import { authenticateTma, TmaAuthError } from './tma-auth'
import { useTmaSession } from './tma-session'
import { ProfileState } from '../../mobile-core/src/features/profile/profile-state'

const INIT_DATA = 'query_id=fixture-query&user=%7B%22id%22%3A77%7D&hash=fixture-hash'

function currentSession(sessionVersion: string, activeRole: 'ADMIN' | 'STUDENT' = 'ADMIN') {
  return {
    sessionId: '00000000-0000-4000-8000-000000000077',
    userId: '77',
    displayName: 'Администратор',
    activeRole,
    sessionVersion,
    rolesVersion: '4',
    roles: [
      { grantId: '77', role: 'ADMIN', status: 'ACTIVE', selectable: true, readOnly: false },
      { grantId: '78', role: 'STUDENT', status: 'ACTIVE', selectable: true, readOnly: false },
    ],
    readOnly: false,
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
})

describe('TMA authentication wire contract', () => {
  it('sends the canonical JSON DTO and returns the in-memory access token', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ accessToken: 'memory-only-token' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(authenticateTma(fetcher, INIT_DATA)).resolves.toBe('memory-only-token')

    const [input, init] = fetcher.mock.calls[0] ?? []
    expect(fetcher).toHaveBeenCalledOnce()
    expect(input).toBe('/api/auth/tma')
    expect(init?.method).toBe('POST')
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json')
    expect(new Headers(init?.headers).get('Accept')).toBe('application/json')
    expect(JSON.parse(String(init?.body))).toEqual({ initData: INIT_DATA })
    expect(init?.credentials).toBe('include')
  })

  it('passes the same canonical request to the synthetic fixture transport', async () => {
    const fixture = createFixtureTransport()
    let capturedInit: RequestInit | undefined
    const fetcher = vi.fn<typeof fetch>((input, init) => {
      capturedInit = init
      return fixture(input, init)
    })

    await expect(authenticateTma(fetcher, INIT_DATA)).resolves.toBe('fixture-access-token')
    expect(JSON.parse(String(capturedInit?.body))).toEqual({ initData: INIT_DATA })
    expect(new Headers(capturedInit?.headers).get('Content-Type')).toBe('application/json')
  })

  it('surfaces an unauthorized response as a clean authentication error', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 401 }))

    await expect(authenticateTma(fetcher, INIT_DATA)).rejects.toMatchObject({
      name: 'TmaAuthError',
      status: 401,
      message: 'Telegram не подтвердил сессию',
    } satisfies Partial<TmaAuthError>)
  })
})

describe('generation-bound report ticket session', () => {
  it.each([
    ['409 version conflict', 409],
    ['503 service failure', 503],
  ] as const)('refreshes current sessionVersion after a %s and retries role selection', async (_label, status) => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ accessToken: 'admin-role-token' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status, detail: 'Повтори выбор' }), { status }))
      .mockResolvedValueOnce(new Response(JSON.stringify(currentSession('4')), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        accessToken: 'student-role-token',
        expiresIn: 3600,
        session: currentSession('5', 'STUDENT'),
      }), { status: 200 }))
    const session = useTmaSession({ fetcher, getInitData: () => INIT_DATA })
    await session.authenticate()
    const generation = session.currentGeneration()

    await expect(session.selectRoleFor(generation, { role: 'STUDENT', expectedSessionVersion: '3' }))
      .rejects.toMatchObject({ status })
    const current = await session.getProfileFor(generation)
    expect(current.sessionVersion).toBe('4')

    const selection = await session.selectRoleFor(generation, {
      role: 'STUDENT',
      expectedSessionVersion: current.sessionVersion,
    })

    expect(selection.session.activeRole).toBe('STUDENT')
    expect(JSON.parse(String(fetcher.mock.calls[1]?.[1]?.body)))
      .toEqual({ role: 'STUDENT', expectedSessionVersion: '3' })
    expect(JSON.parse(String(fetcher.mock.calls[3]?.[1]?.body)))
      .toEqual({ role: 'STUDENT', expectedSessionVersion: '4' })
    expect(session.accessToken.value).toBe('student-role-token')
  })

  it('clears the local owner immediately and reports an unconfirmed current-session revocation', async () => {
    let resolveLogout!: (value: Response) => void
    const delayedLogout = new Promise<Response>((resolve) => { resolveLogout = resolve })
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ accessToken: 'admin-session-token' }), { status: 200 }))
      .mockReturnValueOnce(delayedLogout)
    const session = useTmaSession({ fetcher, getInitData: () => INIT_DATA })
    await session.authenticate()
    const generation = session.currentGeneration()

    const pending = session.logoutCurrent()
    expect(session.accessToken.value).toBeNull()
    expect(session.currentGeneration()).toBe(generation + 1)
    expect(fetcher.mock.calls[1]?.[0]).toBe('/api/auth/logout')
    expect(fetcher.mock.calls[1]?.[1]?.method).toBe('POST')
    expect(new Headers(fetcher.mock.calls[1]?.[1]?.headers).get('Authorization'))
      .toBe('Bearer admin-session-token')
    expect(fetcher.mock.calls[1]?.[1]?.credentials).toBe('include')

    resolveLogout(new Response(null, { status: 503 }))
    await expect(pending).rejects.toMatchObject({
      name: 'TmaAuthError',
      status: 503,
      message: 'Не удалось подтвердить отзыв текущей сессии',
    })
    expect(session.accessToken.value).toBeNull()
  })

  it('keeps local sign-out final and reports a network failure while Auth revocation is pending', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ accessToken: 'offline-admin-session-token' }), { status: 200 }))
      .mockRejectedValueOnce(new TypeError('network unavailable'))
    const session = useTmaSession({ fetcher, getInitData: () => INIT_DATA })
    await session.authenticate()

    await expect(session.logoutCurrent()).rejects.toMatchObject({
      name: 'TmaAuthError',
      status: 0,
      message: 'Не удалось связаться с Auth и подтвердить отзыв текущей сессии',
    })
    expect(session.accessToken.value).toBeNull()
    expect(session.currentGeneration()).toBe(1)
  })

  it('refreshes the TMA auth session once after a ticket 401 and retries with the fresh bearer', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ accessToken: 'expired-synthetic-bearer' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ accessToken: 'fresh-synthetic-bearer' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        downloadPath: `/api/report-download/${'a'.repeat(43)}`,
        expiresAt: new Date(Date.now() + 60_000).toISOString(),
        suggestedFilename: 'journal.pdf',
      }), { status: 200 }))
    const session = useTmaSession({ fetcher, getInitData: () => INIT_DATA })
    const request: ReportDownloadTicketRequest = {
      kind: 'TEACHER_JOURNAL',
      teacherJournal: {
        semesterId: 24,
        groupId: 8,
        subjectId: 3,
        lessonTypes: ['LECTURE'],
        format: 'pdf',
      },
    }

    await session.authenticate()
    const client = session.createReportDownloadClient(fetcher)
    await expect(client.issueTicket(request)).resolves.toMatchObject({ suggestedFilename: 'journal.pdf' })

    expect(fetcher).toHaveBeenCalledTimes(4)
    expect(fetcher.mock.calls[0]?.[0]).toBe('/api/auth/tma')
    expect(fetcher.mock.calls[2]?.[0]).toBe('/api/auth/tma')
    expect(new Headers(fetcher.mock.calls[1]?.[1]?.headers).get('Authorization'))
      .toBe('Bearer expired-synthetic-bearer')
    expect(new Headers(fetcher.mock.calls[3]?.[1]?.headers).get('Authorization'))
      .toBe('Bearer fresh-synthetic-bearer')
  })

  it('rejects a profile page body that completes after the TMA session generation is cleared', async () => {
    let resolvePage!: (value: unknown) => void
    const delayedResponse = {
      ok: true,
      status: 200,
      json: () => new Promise<unknown>((resolve) => { resolvePage = resolve }),
    } as unknown as Response
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ accessToken: 'profile-session-token' }), { status: 200 }))
      .mockResolvedValueOnce(delayedResponse)
    const session = useTmaSession({ fetcher, getInitData: () => INIT_DATA })
    await session.authenticate()
    const port = session.createProfilePort()

    const pending = port.listSessions()
    await vi.waitFor(() => expect(resolvePage).toBeTypeOf('function'))
    session.clear()
    resolvePage({ items: [] })

    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
  })

  it('rejects a profile error body that completes after the TMA session generation is cleared', async () => {
    let resolveProblem!: (value: unknown) => void
    const delayedUnauthorized = {
      ok: false,
      status: 403,
      json: () => new Promise<unknown>((resolve) => { resolveProblem = resolve }),
    } as unknown as Response
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ accessToken: 'late-error-session-token' }), { status: 200 }))
      .mockResolvedValueOnce(delayedUnauthorized)
    const session = useTmaSession({ fetcher, getInitData: () => INIT_DATA })
    await session.authenticate()
    const port = session.createProfilePort()

    const pending = port.listSessions()
    await vi.waitFor(() => expect(resolveProblem).toBeTypeOf('function'))
    session.clear()
    resolveProblem({ extras: { code: 'INVALID_SESSION' }, detail: 'Сессия отозвана' })

    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
  })

  it.each([
    ['changePassword', 'password-changed', '/api/auth/change-password'],
    ['logoutAll', 'logout-all', '/api/auth/logout-all'],
  ] as const)('ends the current TMA owner after confirmed %s and preserves the invalidation reason', async (mutation, reason, path) => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ accessToken: 'current-owner-token' }))
      .mockResolvedValueOnce(Response.json(currentSession('3', 'STUDENT')))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const session = useTmaSession({ fetcher, getInitData: () => INIT_DATA })
    await session.authenticate()
    let signedOutReason: string | null = null
    const state = new ProfileState(session.createProfilePort(session.currentGeneration(), {
      onInvalidated: (value) => { signedOutReason = value; session.clear() },
    }))
    await state.loadSnapshot()
    if (mutation === 'changePassword') await state.changePassword({ currentPassword: 'old', newPassword: 'NewPassword1!' })
    else await state.logoutAll()
    expect(signedOutReason).toBe(reason)
    expect(state.view.snapshot).toBeNull()
    expect(session.accessToken.value).toBeNull()
    expect(fetcher.mock.calls[2]?.[0]).toBe(path)
    expect(new Headers(fetcher.mock.calls[2]?.[1]?.headers).get('Authorization')).toBe('Bearer current-owner-token')
    if (mutation === 'changePassword') {
      expect(JSON.parse(String(fetcher.mock.calls[2]?.[1]?.body))).toEqual({ currentPassword: 'old', newPassword: 'NewPassword1!' })
    }
  })

  it.each([
    [400, 'CURRENT_PASSWORD_INVALID'],
    [422, 'PASSWORD_POLICY_VIOLATION'],
    [503, 'AUTHORITY_UNAVAILABLE'],
  ] as const)('keeps the TMA form owner after password rejection %s/%s', async (status, code) => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ accessToken: 'retained-owner-token' }))
      .mockResolvedValueOnce(Response.json(currentSession('3', 'STUDENT')))
      .mockResolvedValueOnce(Response.json({ extras: { code } }, { status }))
    const session = useTmaSession({ fetcher, getInitData: () => INIT_DATA })
    await session.authenticate()
    const state = new ProfileState(session.createProfilePort())
    await state.loadSnapshot()
    await expect(state.changePassword({ currentPassword: 'wrong', newPassword: 'NewPassword1!' })).rejects.toMatchObject({ code })
    expect(state.view.error?.code).toBe(code)
    expect(state.view.snapshot?.userId).toBe('77')
    expect(session.accessToken.value).toBe('retained-owner-token')
  })

  it('ignores an old TMA password result after a new account authenticates', async () => {
    let resolveMutation!: (response: Response) => void
    const mutation = new Promise<Response>((resolve) => { resolveMutation = resolve })
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ accessToken: 'old-account-token' }))
      .mockResolvedValueOnce(Response.json(currentSession('3', 'STUDENT')))
      .mockReturnValueOnce(mutation)
      .mockResolvedValueOnce(Response.json({ accessToken: 'new-account-token' }))
      .mockResolvedValueOnce(Response.json({ ...currentSession('1', 'STUDENT'), userId: '88' }))
    const session = useTmaSession({ fetcher, getInitData: () => INIT_DATA })
    await session.authenticate()
    const state = new ProfileState(session.createProfilePort(session.currentGeneration(), {
      onInvalidated: () => { session.clear() },
    }))
    await state.loadSnapshot()
    const pending = state.changePassword({ currentPassword: 'old', newPassword: 'NewPassword1!' })
    session.clear()
    await session.authenticate()
    const current = await session.getProfileFor(session.currentGeneration())
    resolveMutation(new Response(null, { status: 204 }))
    await expect(pending).rejects.toThrow()
    expect(session.accessToken.value).toBe('new-account-token')
    expect(current.userId).toBe('88')
  })

  it('validates a same-authority refreshed bearer before retrying a profile request', async () => {
    const original = currentSession('3', 'STUDENT')
    const refreshed = { ...original, roles: [...original.roles].reverse() }
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ accessToken: 'old-token' }))
      .mockResolvedValueOnce(Response.json(original))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(Response.json({ accessToken: 'refreshed-token' }))
      .mockResolvedValueOnce(Response.json(refreshed))
      .mockResolvedValueOnce(Response.json({ items: [] }))
    const session = useTmaSession({ fetcher, getInitData: () => INIT_DATA })
    await session.authenticate()
    const generation = session.currentGeneration()
    await session.getProfileFor(generation)
    const port = session.createProfilePort(generation)

    await expect(port.listSessions()).resolves.toEqual({ items: [], nextCursor: null })
    expect(session.currentGeneration()).toBe(generation)
    expect(fetcher.mock.calls[4]?.[0]).toBe('/api/auth/session')
    expect(new Headers(fetcher.mock.calls[4]?.[1]?.headers).get('Authorization')).toBe('Bearer refreshed-token')
    expect(fetcher.mock.calls[5]?.[0]).toBe('/api/auth/sessions')
    expect(new Headers(fetcher.mock.calls[5]?.[1]?.headers).get('Authorization')).toBe('Bearer refreshed-token')
  })

  it.each(['account', 'session', 'role', 'grant'] as const)('fences old clients before publishing a refreshed %s authority', async (change) => {
    const original = currentSession('3', 'STUDENT')
    const changed = {
      ...original,
      ...(change === 'account' ? { userId: '88' } : {}),
      ...(change === 'session' ? { sessionVersion: '4' } : {}),
      ...(change === 'role' ? { activeRole: 'ADMIN' } : {}),
      ...(change === 'grant' ? { roles: original.roles.map((grant) => ({ ...grant, groupId: '99' })) } : {}),
    }
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ accessToken: 'old-token' }))
      .mockResolvedValueOnce(Response.json(original))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(Response.json({ accessToken: 'candidate-token' }))
      .mockResolvedValueOnce(Response.json(changed))
    const session = useTmaSession({ fetcher, getInitData: () => INIT_DATA })
    await session.authenticate()
    const generation = session.currentGeneration()
    await session.getProfileFor(generation)
    const port = session.createProfilePort(generation)
    const client = session.createReportDownloadClient(fetcher)
    let notifiedGeneration: number | null = null
    const stop = session.subscribeInvalidation(() => { notifiedGeneration = session.currentGeneration() })

    await expect(port.listSessions()).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(notifiedGeneration).toBe(generation + 1)
    expect(session.accessToken.value).toBe('candidate-token')
    await expect(port.listSessions()).rejects.toBeInstanceOf(StaleSessionGenerationError)
    await expect(client.issueTicket({
      kind: 'TEACHER_JOURNAL',
      teacherJournal: { semesterId: 24, groupId: 8, subjectId: 3, lessonTypes: ['LECTURE'], format: 'pdf' },
    }))
      .rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(fetcher.mock.calls).toHaveLength(5)
    expect(fetcher.mock.calls[4]?.[0]).toBe('/api/auth/session')
    stop()
  })

  it.each(['owner', 'profile-port'] as const)('rejects changed authority returned directly through %s', async (path) => {
    const original = currentSession('3', 'STUDENT')
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ accessToken: 'current-token' }))
      .mockResolvedValueOnce(Response.json(original))
      .mockResolvedValueOnce(Response.json({ ...original, rolesVersion: '5' }))
    const session = useTmaSession({ fetcher, getInitData: () => INIT_DATA })
    await session.authenticate()
    const generation = session.currentGeneration()
    await session.getProfileFor(generation)
    const pending = path === 'owner'
      ? session.getProfileFor(generation)
      : session.createProfilePort(generation).getSnapshot()

    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(session.currentGeneration()).toBe(generation + 1)
    expect(session.accessToken.value).toBe('current-token')
  })

  it.each(
    (['authenticate', 'candidate-profile', 'owner-profile', 'role-selection', 'profile-port'] as const)
      .flatMap((operation) => (['401', '403', 'network', 'json'] as const).map((failure) => ({ operation, failure }))),
  )('fences a late $failure rejection from $operation after a new account enters', async ({ operation, failure }) => {
    let resolveResponse!: (response: Response) => void
    let rejectResponse!: (cause: Error) => void
    let rejectBody!: (cause: Error) => void
    let bodyRequested = false
    const delayedResponse = new Promise<Response>((resolve, reject) => { resolveResponse = resolve; rejectResponse = reject })
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ accessToken: 'old-token' }))
      .mockResolvedValueOnce(Response.json(currentSession('3', 'STUDENT')))
    if (operation === 'candidate-profile') fetcher.mockResolvedValueOnce(Response.json({ accessToken: 'candidate-token' }))
    if (failure === 'json') {
      const response = Response.json({})
      response.json = () => {
        bodyRequested = true
        return new Promise<unknown>((_resolve, reject) => { rejectBody = reject })
      }
      fetcher.mockResolvedValueOnce(response)
    } else fetcher.mockReturnValueOnce(delayedResponse)
    fetcher
      .mockResolvedValueOnce(Response.json({ accessToken: 'new-token' }))
      .mockResolvedValueOnce(Response.json({ ...currentSession('1', 'STUDENT'), userId: '88' }))
    const session = useTmaSession({ fetcher, getInitData: () => INIT_DATA })
    await session.authenticate()
    const generation = session.currentGeneration()
    await session.getProfileFor(generation)
    const pending = operation === 'authenticate' || operation === 'candidate-profile'
      ? session.authenticateFor(generation)
      : operation === 'role-selection'
        ? session.selectRoleFor(generation, { role: 'ADMIN', expectedSessionVersion: '3' })
        : operation === 'profile-port'
          ? session.createProfilePort(generation).getSnapshot()
          : session.getProfileFor(generation)
    const rejected = expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    if (failure === 'json') await vi.waitFor(() => expect(bodyRequested).toBe(true))
    else if (operation === 'candidate-profile') await vi.waitFor(() => expect(fetcher.mock.calls).toHaveLength(4))

    session.clear()
    await session.authenticate()
    const newGeneration = session.currentGeneration()
    const newProfile = await session.getProfileFor(newGeneration)
    if (failure === 'network') rejectResponse(new TypeError('late network rejection'))
    else if (failure === 'json') rejectBody(new SyntaxError('late malformed response'))
    else resolveResponse(new Response(null, { status: Number(failure) }))

    await rejected
    expect(session.currentGeneration()).toBe(newGeneration)
    expect(session.accessToken.value).toBe('new-token')
    expect(newProfile.userId).toBe('88')
  })
})

describe('Telegram host boundary', () => {
  it('preserves a real host that already provides init data', () => {
    const app = { initData: INIT_DATA }
    const windowStub = { Telegram: { WebApp: app }, innerHeight: 720 } as unknown as Window & typeof globalThis
    vi.stubGlobal('window', windowStub)

    installFixtureTelegramHost()

    expect(windowStub.Telegram?.WebApp).toBe(app)
  })

  it('replaces an empty SDK shell only for the explicit fixture host', () => {
    const windowStub = { Telegram: { WebApp: { initData: '' } }, innerHeight: 720 } as unknown as Window & typeof globalThis
    vi.stubGlobal('window', windowStub)

    installFixtureTelegramHost()

    expect(windowStub.Telegram?.WebApp.initData).toBe('fixture-signed-init-data')
  })

  it('returns no init data when the app is opened outside Telegram', () => {
    vi.stubGlobal('window', { Telegram: undefined, innerHeight: 720 })
    expect(new TelegramHost().start()).toBeNull()
  })
  it('terminates selected own sessions through the generation port, rejects non204 and hides late old-owner completion', async () => {
    let resolve!: (response: Response) => void
    const pending = new Promise<Response>(accept => { resolve = accept })
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(new Response(JSON.stringify({ accessToken: 'actor-token' }), { status: 200 })).mockResolvedValueOnce(new Response(null, { status: 204 })).mockResolvedValueOnce(new Response(null, { status: 200 })).mockReturnValueOnce(pending)
    const owner = useTmaSession({ fetcher, getInitData: () => INIT_DATA })
    await owner.authenticate()
    const port = owner.createProfilePort()
    const target = '00000000-0000-4000-8000-000000000078'
    await expect(port.terminateSession!(target)).resolves.toBeUndefined()
    expect(fetcher.mock.calls[1]?.[0]).toBe(`/api/auth/sessions/${target}`)
    expect(fetcher.mock.calls[1]?.[1]?.method).toBe('DELETE')
    expect(fetcher.mock.calls[1]?.[1]?.body).toBeUndefined()
    expect(owner.accessToken.value).toBe('actor-token')
    await expect(port.terminateSession!(target)).rejects.toMatchObject({ status: 200 })
    const old = port.terminateSession!(target)
    owner.clear()
    resolve(new Response(null, { status: 204 }))
    await expect(old).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(owner.accessToken.value).toBeNull()
  })

})
