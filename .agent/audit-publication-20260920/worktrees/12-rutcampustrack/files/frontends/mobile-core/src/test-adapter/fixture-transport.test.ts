import { afterEach, describe, expect, it, vi } from 'vitest'
import { StudentApi } from '../api/student-client'
import { createFixtureTransport } from './fixture-transport'

afterEach(() => { vi.unstubAllGlobals() })

describe('Today fixture transport', () => {
  it('keeps the canonical fixture untouched while exposing the three-row default screenshot state', async () => {
    vi.stubGlobal('location', { href: 'https://fixture.invalid/?fixtureToday=default' })
    const response = await createFixtureTransport()('/api/v1/student/today')
    const today = await response.json() as { lessons: Array<{ schedule: { id: string; status: string } }> }

    expect(today.lessons.map((lesson) => lesson.schedule.id)).toEqual(['fixture-math', '77', 'fixture-networks'])
    expect(today.lessons.map((lesson) => lesson.schedule.status)).toEqual(['CLOSED', 'ACTIVE', 'PLANNED'])
  })

  it('makes the acknowledged fixture state visible to the refetch that follows a check-in', async () => {
    vi.stubGlobal('location', { href: 'https://fixture.invalid/?fixtureToday=default' })
    const transport = createFixtureTransport()
    await transport('/api/v1/student/lessons/77/checkin', {
      method: 'POST',
      body: JSON.stringify({ geo: { kind: 'UNAVAILABLE', reason: 'TIMEOUT' } }),
    })
    const pending = await (await transport('/api/v1/student/today')).json() as { lessons: Array<{ schedule: { id: string }; request: { status: string } | null }> }
    expect(pending.lessons.find((lesson) => lesson.schedule.id === '77')?.request?.status).toBe('PENDING')

    await transport('/api/v1/student/lessons/77/checkin', {
      method: 'POST',
      body: JSON.stringify({ geo: { kind: 'COORDINATES', latitude: 55.75, longitude: 37.62 } }),
    })
    const confirmed = await (await transport('/api/v1/student/today')).json() as { lessons: Array<{ schedule: { id: string }; attendance: { status: string } | null }> }
    expect(confirmed.lessons.find((lesson) => lesson.schedule.id === '77')?.attendance?.status).toBe('PRESENT')
  })

  it('provides a second date and explicitly simulates transport and expired-session failures', async () => {
    vi.stubGlobal('location', { href: 'https://fixture.invalid/?fixtureToday=default' })
    const schedule = await (await createFixtureTransport()('/api/v1/student/schedule?semesterId=9')).json() as { lessons: Array<{ date: string; subject: { name: string } }> }
    expect([...new Set(schedule.lessons.map((lesson) => lesson.date))]).toEqual(['2026-09-06', '2026-09-07'])
    expect(schedule.lessons.filter((lesson) => lesson.date === '2026-09-06').map((lesson) => lesson.subject.name)).toEqual([
      'Математический анализ',
      'Основы программирования',
      'Компьютерные сети',
    ])

    vi.stubGlobal('location', { href: 'https://fixture.invalid/?fixtureApi=network' })
    await expect(createFixtureTransport()('/api/v1/student/session')).rejects.toThrow('Fixture network unavailable')

    vi.stubGlobal('location', { href: 'https://fixture.invalid/?fixtureApi=unauthorized' })
    expect((await createFixtureTransport()('/api/v1/student/session')).status).toBe(401)

    vi.stubGlobal('location', { href: 'https://fixture.invalid/?fixtureApi=forbidden' })
    expect((await createFixtureTransport()('/api/v1/student/session')).status).toBe(403)

    vi.stubGlobal('navigator', { onLine: false })
    await expect(createFixtureTransport()('/api/v1/student/session')).rejects.toThrow('Fixture network unavailable')
  })

  it('drives StudentApi through the transport and 401 retry failures used by PWA bootstrap', async () => {
    vi.stubGlobal('location', { href: 'https://fixture.invalid/?fixtureApi=network' })
    const networkApi = new StudentApi({ accessToken: () => 'fixture-token', fetcher: createFixtureTransport() })
    await expect(networkApi.getSession()).rejects.toThrow('Fixture network unavailable')

    vi.stubGlobal('location', { href: 'https://fixture.invalid/?fixtureApi=unauthorized' })
    const expiredSessionApi = new StudentApi({
      accessToken: () => 'fixture-token',
      onUnauthorized: async () => undefined,
      fetcher: createFixtureTransport(),
    })
    await expect(expiredSessionApi.getSession()).rejects.toMatchObject({
      name: 'StudentApiError',
      response: expect.objectContaining({ status: 401 }),
    })

    vi.stubGlobal('location', { href: 'https://fixture.invalid/?fixtureApi=forbidden' })
    const forbiddenApi = new StudentApi({ accessToken: () => 'fixture-token', fetcher: createFixtureTransport() })
    await expect(forbiddenApi.getSession()).rejects.toMatchObject({
      name: 'StudentApiError',
      response: expect.objectContaining({ status: 403 }),
    })
  })

  it('calls default browser fetch with the global receiver and leaves an injected fetch unchanged', async () => {
    const defaultFetch = vi.fn(function (this: unknown) {
      if (this !== globalThis) throw new TypeError('Illegal invocation')
      return Promise.resolve(new Response(JSON.stringify({ id: 'session' })))
    })
    vi.stubGlobal('fetch', defaultFetch)
    const defaultApi = new StudentApi({ accessToken: () => null })
    await expect(defaultApi.getSession()).resolves.toEqual({ id: 'session' })
    expect(defaultFetch).toHaveBeenCalledOnce()

    const injectedFetch = vi.fn(() => {
      return Promise.resolve(new Response(JSON.stringify({ id: 'injected' })))
    })
    const injectedApi = new StudentApi({ accessToken: () => null, fetcher: injectedFetch })
    await expect(injectedApi.getSession()).resolves.toEqual({ id: 'injected' })
    expect(injectedFetch).toHaveBeenCalledOnce()
    expect(injectedFetch.mock.contexts[0]).toBe(injectedApi)
  })
})
