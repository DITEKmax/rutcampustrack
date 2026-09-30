import { describe, expect, it, vi } from 'vitest'
import { createPasswordRecoveryClient } from './password-recovery-client'
import { consumePasswordResetFragment, isPasswordResetEntryPath, passwordRecoveryLoginPath } from './password-recovery-link'
import { createPasswordRecoveryOperationGate } from './password-recovery-operation'
import { APP_UPDATE_REQUIRED_EVENT, APP_VERSION, createPwaFetcher } from '../../pwa-version'

describe('PWA password recovery contract', () => {
  it('clears bot proof from the fragment before exposing it to the caller', () => {
    const events: string[] = []
    const proof = consumePasswordResetFragment('#challengeId=challenge-1&code=731904', () => events.push('cleared'))

    expect(events).toEqual(['cleared'])
    expect(proof).toEqual({ challengeId: 'challenge-1', code: '731904' })
    expect(isPasswordResetEntryPath('/password-reset', '/')).toBe(true)
    expect(isPasswordResetEntryPath('/pwa/password-reset', '/pwa/')).toBe(true)
    expect(isPasswordResetEntryPath('/password-reset/other', '/')).toBe(false)
    expect(passwordRecoveryLoginPath('/')).toBe('/')
    expect(passwordRecoveryLoginPath('/pwa')).toBe('/pwa/')
    expect(passwordRecoveryLoginPath('/pwa/')).toBe('/pwa/')
  })

  it('rejects duplicate or unrelated fragment parameters after clearing them', () => {
    const replaceUrl = vi.fn()

    expect(consumePasswordResetFragment('#challengeId=a&challengeId=b&code=1', replaceUrl)).toBeNull()
    expect(consumePasswordResetFragment('#challengeId=a&code=1&next=/login', replaceUrl)).toBeNull()
    expect(replaceUrl).toHaveBeenCalledTimes(2)
  })

  it('invalidates late async work when the recovery screen closes', () => {
    const operations = createPasswordRecoveryOperationGate()
    const operation = operations.begin()

    operations.invalidate()

    expect(operation.signal.aborted).toBe(true)
    expect(operation.isCurrent()).toBe(false)
  })

  it('consumes admin bearer tickets only from a strict fragment and clears even rejected links', () => {
    const ticket = 'a'.repeat(43)
    const expiresAt = '2030-10-01T12:00:00.123456Z'
    const clear = vi.fn()
    expect(consumePasswordResetFragment(`#resetTicket=${ticket}&expiresAt=${expiresAt}`, clear))
      .toEqual({ resetTicket: ticket, expiresAt })
    for (const fragment of [
      `#resetTicket=${ticket}&resetTicket=${ticket}&expiresAt=${expiresAt}`,
      `#resetTicket=${ticket}&expiresAt=2030-02-30T12:00:00Z`,
      `#resetTicket=${ticket}&expiresAt=${expiresAt}&code=731904`,
      '#resetTicket=short&expiresAt=2030-10-01T12:00:00Z',
    ]) expect(consumePasswordResetFragment(fragment, clear)).toBeNull()
    expect(clear).toHaveBeenCalledTimes(5)
    expect(() => consumePasswordResetFragment(`#resetTicket=${ticket}&expiresAt=${expiresAt}`, () => {
      throw new Error('history unavailable')
    })).toThrow('history unavailable')
  })

  it('uses server counters for an invalid code', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      extras: { code: 'OTP_INVALID', attemptsRemaining: 2 },
    }), { status: 400, headers: { 'Content-Type': 'application/json' } }))
    const client = createPasswordRecoveryClient(fetcher)

    await expect(client.verify({ challengeId: 'challenge-1', code: '731904' })).rejects.toMatchObject({
      status: 400,
      code: 'OTP_INVALID',
      problem: { attemptsRemaining: 2 },
    })
  })

  it('keeps all recovery credentials in no-store request bodies without the current session', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        challengeId: 'challenge-1',
        ttlSeconds: 300,
      }), { status: 202, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        resetTicket: 'opaque-ticket',
        expiresInSeconds: 180,
        attemptsRemaining: 3,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const client = createPasswordRecoveryClient(fetcher)

    await expect(client.request('student-login')).resolves.toEqual({
      challengeId: 'challenge-1',
      ttlSeconds: 300,
    })
    await expect(client.verify({ challengeId: 'challenge-1', code: '731904' })).resolves.toEqual({
      resetTicket: 'opaque-ticket',
      expiresInSeconds: 180,
      attemptsRemaining: 3,
    })
    await expect(client.complete({ resetTicket: 'opaque-ticket', newPassword: 'long-new-password' })).resolves.toBeUndefined()

    const expectedBodies = [
      { login: 'student-login' },
      { challengeId: 'challenge-1', code: '731904' },
      { resetTicket: 'opaque-ticket', newPassword: 'long-new-password' },
    ]
    const expectedPaths = [
      '/api/auth/password-reset/request',
      '/api/auth/password-reset/verify',
      '/api/auth/password-reset/complete',
    ]
    expect(fetcher).toHaveBeenCalledTimes(3)
    for (const [index, [path, init]] of fetcher.mock.calls.entries()) {
      expect(path).toBe(expectedPaths[index])
      expect(init?.credentials).toBe('omit')
      expect(init?.cache).toBe('no-store')
      expect(new Headers(init?.headers).has('Authorization')).toBe(false)
      expect(JSON.parse(String(init?.body))).toEqual(expectedBodies[index])
    }
  })

  it('routes recovery requests through the configured PWA version fetcher', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 204 }))
    const client = createPasswordRecoveryClient(createPwaFetcher(fetcher))

    await expect(client.complete({ resetTicket: 'ticket', newPassword: 'new-password' })).resolves.toBeUndefined()

    const [, init] = fetcher.mock.calls[0] ?? []
    const headers = new Headers(init?.headers)
    expect(headers.get('X-PWA-Version')).toBe(APP_VERSION)
    expect(init?.credentials).toBe('omit')
    expect(init?.cache).toBe('no-store')
    expect(headers.has('Authorization')).toBe(false)
  })

  it('surfaces the PWA update gate when a recovery endpoint returns 426', async () => {
    const response = new Response(JSON.stringify({ detail: 'Обнови приложение.' }), {
      status: 426,
      headers: { 'Content-Type': 'application/json' },
    })
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(response)
    const dispatchEvent = vi.fn()
    vi.stubGlobal('window', { dispatchEvent })

    try {
      const client = createPasswordRecoveryClient(createPwaFetcher(fetcher))
      await expect(client.request('student-login')).rejects.toMatchObject({ status: 426 })
      await vi.waitFor(() => expect(dispatchEvent).toHaveBeenCalledOnce())

      const event = dispatchEvent.mock.calls[0]?.[0] as CustomEvent<{ reason: string; message: string }>
      expect(event.type).toBe(APP_UPDATE_REQUIRED_EVENT)
      expect(event.detail).toMatchObject({ reason: 'api', message: 'Обнови приложение.' })
    } finally {
      vi.unstubAllGlobals()
    }
  })
})
