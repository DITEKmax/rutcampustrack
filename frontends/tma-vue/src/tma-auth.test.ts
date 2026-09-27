import { afterEach, describe, expect, it, vi } from 'vitest'
import { createFixtureTransport, StaleSessionGenerationError, type ReportDownloadTicketRequest } from '@rct/mobile-core'
import { installFixtureTelegramHost, TelegramHost } from './telegram'
import { authenticateTma, TmaAuthError } from './tma-auth'
import { useTmaSession } from './tma-session'

const INIT_DATA = 'query_id=fixture-query&user=%7B%22id%22%3A77%7D&hash=fixture-hash'

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
})
