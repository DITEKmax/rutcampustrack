import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { ProfileRequestError } from '../../mobile-core/src/features/profile/profile-types'
import { AuthProtocolError, AuthRequestError } from './auth-client'
import { isOfflineBootstrapRecoveryError } from './bootstrap-policy'

describe('PWA cold bootstrap recovery policy', () => {
  it('allows a rejected fetch-shaped Auth NETWORK failure to consult the committed snapshot', () => {
    const error = new AuthRequestError(
      'refresh',
      undefined,
      { code: 'NETWORK', title: 'Нет соединения с сервером' },
      new TypeError('fetch failed'),
    )

    expect(isOfflineBootstrapRecoveryError(error)).toBe(true)
  })

  it.each(['INVALID_SESSION', 'SESSION_REVOKED', 'ACCOUNT_INVALIDATED', 'REFRESH_REJECTED'] as const)(
    'keeps %s fail closed',
    (code) => {
      const error = new AuthRequestError('refresh', undefined, { code })

      expect(isOfflineBootstrapRecoveryError(error)).toBe(false)
    },
  )

  it.each([401, 403, 409, 503] as const)(
    'keeps HTTP %s with a NETWORK problem code fail closed',
    (status) => {
      expect(isOfflineBootstrapRecoveryError(new AuthRequestError('session', status, { code: 'NETWORK' }))).toBe(false)
    },
  )

  it('keeps stale-session and protocol errors fail closed', () => {
    expect(isOfflineBootstrapRecoveryError(new ProfileRequestError('SESSION_STATE_STALE', 'stale'))).toBe(false)
    expect(isOfflineBootstrapRecoveryError(new AuthProtocolError('refresh', 502, 'malformed response'))).toBe(false)
  })

  it('does not treat an untyped profile NETWORK error as auth transport loss', () => {
    expect(isOfflineBootstrapRecoveryError(new ProfileRequestError('NETWORK', 'network'))).toBe(false)
  })

  it('keeps StudentApi-like and unknown bootstrap failures outside recovery', () => {
    const studentApiLike = Object.assign(new Error('BFF HTTP 403'), { status: 403, code: 'UNKNOWN' })

    expect(isOfflineBootstrapRecoveryError(studentApiLike)).toBe(false)
    expect(isOfflineBootstrapRecoveryError(new Error('malformed bootstrap response'))).toBe(false)
  })

  it('keeps the sole bootstrap snapshot recovery call behind the narrow guard', () => {
    const source = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')
    const bootstrapStart = source.indexOf('async function bootstrap(')
    const catchStart = source.indexOf('  } catch (error) {', bootstrapStart)
    const finallyStart = source.indexOf('  } finally {', catchStart)
    const catchBody = source.slice(catchStart, finallyStart)
    const recoveryCall = 'const recovered = await loadOfflineSnapshot()'
    const recoveryCallCount = catchBody.split(recoveryCall).length - 1
    const guardIndex = catchBody.indexOf('!featureVisible.value && isOfflineBootstrapRecoveryError(error)')
    const recoveryIndex = catchBody.indexOf(recoveryCall)

    expect(recoveryCallCount).toBe(1)
    expect(guardIndex).toBeGreaterThanOrEqual(0)
    expect(recoveryIndex).toBeGreaterThan(guardIndex)
  })
})
