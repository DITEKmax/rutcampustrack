import { afterEach, describe, expect, it, vi } from 'vitest'
import { createFixtureTransport } from '@rct/mobile-core'
import { installFixtureTelegramHost, TelegramHost } from './telegram'
import { authenticateTma } from './tma-auth'

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

    await expect(authenticateTma(fetcher, INIT_DATA)).rejects.toThrow('Telegram не подтвердил сессию')
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
