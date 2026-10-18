import { afterEach, describe, expect, it, vi } from 'vitest'
import { IdentityOperationError } from '../../shared/identity-operation'
import {
  AdminAccessApiError,
  AdminAccessClient,
  adminHasPermission,
  adminPermissionCodes,
  normalizeAdminAccessProfile,
} from './admin-access-client'
const profile = {
  userId: 42,
  assigned: true,
  mode: 'LIMITED' as const,
  permissions: ['USERS_VIEW'],
  version: 7,
}
const response = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
describe('Admin access authority', () => {
  it('fails closed for missing/revoked/malformed authority and unknown capabilities', () => {
    expect(adminHasPermission(null, 'USERS_VIEW')).toBe(false)
    expect(
      adminHasPermission(
        { ...profile, assigned: false, mode: null, permissions: [] },
        'USERS_VIEW',
      ),
    ).toBe(false)
    expect(adminHasPermission(profile, 'USERS_MANAGE')).toBe(false)
    expect(
      adminHasPermission(
        { ...profile, mode: 'FULL', permissions: [...adminPermissionCodes] },
        'UNKNOWN',
      ),
    ).toBe(false)
    expect(() =>
      normalizeAdminAccessProfile({ ...profile, permissions: ['ADMIN_ACCESS_MANAGE'] }),
    ).toThrow()
    expect(() =>
      normalizeAdminAccessProfile({ ...profile, mode: 'FULL', permissions: [] }),
    ).toThrow()
    expect(() => normalizeAdminAccessProfile({ ...profile, assigned: false })).toThrow()
    expect(() => normalizeAdminAccessProfile({ ...profile, version: 0 })).toThrow()
  })
  it('preserves a concurrent profile and never retries a CAS conflict with a new version', async () => {
    let writes = 0
    const client = new AdminAccessClient({
      accessToken: () => null,
      fetcher: async () => {
        writes++
        return response(
          {
            code: 'ADMIN_ACCESS_CONFLICT',
            detail: 'Конфликт версии',
            currentProfile: { ...profile, version: 8 },
          },
          409,
        )
      },
    })
    try {
      await client.updateUserAccess(42, {
        mode: 'LIMITED',
        permissions: ['USERS_VIEW'],
        expectedVersion: 7,
      })
      throw new Error('Expected conflict')
    } catch (cause) {
      expect(cause).toBeInstanceOf(AdminAccessApiError)
      expect((cause as AdminAccessApiError).currentProfile?.version).toBe(8)
    }
    expect(writes).toBe(1)
  })
  it('keeps the original PUT after an unknown outcome and blocks a different revoke intent', async () => {
    const calls: RequestInit[] = []
    const client = new AdminAccessClient({
      accessToken: () => null,
      fetcher: async (_input, init) => {
        calls.push(init!)
        return response({ detail: 'Недоступно' }, 503)
      },
    })
    await expect(client.updateUserAccess(42, update)).rejects.toBeInstanceOf(AdminAccessApiError)
    await expect(client.revokeUserAccess(42, 7)).rejects.toBeInstanceOf(AdminAccessApiError)
    expect(calls).toHaveLength(1)
    expect(client.hasPendingIdentityOperation).toBe(true)
    expect(calls[0]?.method).toBe('PUT')
  })
  it('rejects late authority from an invalidated session before it can be accepted', async () => {
    let current = true
    let finish: ((value: Response) => void) | undefined
    const client = new AdminAccessClient({
      accessToken: () => null,
      assertCurrent: () => {
        if (!current) throw new Error('stale-session')
      },
      fetcher: () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    })
    const pending = client.getMe()
    current = false
    finish!(response(profile))
    await expect(pending).rejects.toThrow('stale-session')
  })
})

const update = { mode: 'LIMITED' as const, permissions: ['USERS_VIEW'], expectedVersion: 7 }
function operation(id: string, status: string, retryAfterSeconds = 0): Response {
  return new Response(JSON.stringify({ operationId: id, status, retryAfterSeconds }), {
    status: status === 'PENDING' ? 202 : 200,
    headers: { 'X-Operation-Id': id, Location: `/api/academic/identity-operations/${id}` },
  })
}
afterEach(() => vi.useRealTimers())
describe('AdminAccess durable mutations', () => {
  it.each(['PUT', 'DELETE'] as const)(
    'resumes pending %s with its exact original key and success type',
    async (method) => {
      vi.useFakeTimers()
      const calls: { path: string; init: RequestInit }[] = []
      let id = '',
        done = false
      const client = new AdminAccessClient({
        accessToken: () => null,
        fetcher: async (input, init) => {
          const path = String(input)
          calls.push({ path, init: init! })
          if (path.includes('/identity-operations/'))
            return operation(id, done ? 'SUCCEEDED' : 'PENDING', done ? 0 : 30)
          id = new Headers(init?.headers).get('Idempotency-Key')!
          if (!done) return operation(id, 'PENDING', 30)
          return method === 'DELETE'
            ? new Response(null, { status: 204 })
            : response({ ...profile, version: 8 })
        },
      })
      const signal = new AbortController().signal
      const first =
        method === 'PUT'
          ? client.updateUserAccess(42, update, signal)
          : client.revokeUserAccess(42, 7, signal)
      await expect(first).rejects.toMatchObject({ state: 'PENDING' })
      expect(client.hasPendingIdentityOperation).toBe(true)
      expect(calls).toHaveLength(1)
      done = true
      await vi.advanceTimersByTimeAsync(30_000)
      const result = await client.resumeIdentityOperation()
      expect(result).toMatchObject({
        userId: 42,
        revoked: method === 'DELETE',
        profile: method === 'DELETE' ? null : { ...profile, version: 8 },
      })
      expect(calls.map((c) => c.path)).toEqual([
        '/api/academic/users/42/admin-access',
        `/api/academic/identity-operations/${id}`,
        '/api/academic/users/42/admin-access',
      ])
      expect(new Headers(calls[2]!.init.headers).get('Idempotency-Key')).toBe(id)
      expect(calls[2]!.init.body).toBe(calls[0]!.init.body)
      expect(JSON.parse(String(calls[0]!.init.body))).toEqual(
        method === 'DELETE' ? { expectedVersion: 7 } : update,
      )
      expect(client.hasPendingIdentityOperation).toBe(true)
      client.acknowledgeIdentityOperation()
      expect(client.hasPendingIdentityOperation).toBe(false)
    },
  )
  it('uses status first and the SAME PUT key after a lost response and GET404', async () => {
    const calls: { path: string; init: RequestInit }[] = []
    let writes = 0
    const client = new AdminAccessClient({
      accessToken: () => null,
      fetcher: async (input, init) => {
        const path = String(input)
        calls.push({ path, init: init! })
        if (path.includes('/identity-operations/')) return response({}, 404)
        if (++writes === 1) throw new TypeError('lost response')
        return response({ ...profile, version: 8 })
      },
    })
    await expect(
      client.updateUserAccess(42, update, new AbortController().signal),
    ).rejects.toMatchObject({ state: 'UNKNOWN' })
    const result = await client.resumeIdentityOperation()
    expect(result.profile?.version).toBe(8)
    expect(calls[1]!.path).toContain('/identity-operations/')
    expect(calls[2]!.init.body).toBe(calls[0]!.init.body)
    expect(new Headers(calls[2]!.init.headers).get('Idempotency-Key')).toBe(
      new Headers(calls[0]!.init.headers).get('Idempotency-Key'),
    )
  })
  it.each([400, 403, 409])(
    'keeps HTTP%s denial/currentProfile until durable REJECTED, without a second mutation',
    async (status) => {
      let id = '',
        writes = 0,
        rejected = false
      const client = new AdminAccessClient({
        accessToken: () => null,
        fetcher: async (input, init) => {
          if (String(input).includes('/identity-operations/'))
            return operation(id, rejected ? 'REJECTED' : 'PENDING', rejected ? 0 : 30)
          writes++
          id = new Headers(init?.headers).get('Idempotency-Key')!
          return response(
            { detail: 'Проверь права', currentProfile: { ...profile, version: 8 } },
            status,
          )
        },
      })
      await expect(client.updateUserAccess(42, update)).rejects.toMatchObject({
        response: { status },
        currentProfile: { version: 8 },
      })
      expect(client.hasPendingIdentityOperation).toBe(true)
      rejected = true
      await expect(client.resumeIdentityOperation()).rejects.toMatchObject({
        response: { status },
        currentProfile: { version: 8 },
      })
      expect(client.hasPendingIdentityOperation).toBe(false)
      expect(writes).toBe(1)
    },
  )
  it('retains accepted work on forbidden status observation and clears it on session invalidation', async () => {
    vi.useFakeTimers()
    let current = true,
      id = ''
    const client = new AdminAccessClient({
      accessToken: () => null,
      assertCurrent: () => {
        if (!current) throw new Error('stale-session')
      },
      fetcher: async (input, init) => {
        if (String(input).includes('/identity-operations/')) return response({}, 403)
        id = new Headers(init?.headers).get('Idempotency-Key')!
        return operation(id, 'PENDING', 30)
      },
    })
    await expect(client.revokeUserAccess(42, 7)).rejects.toBeInstanceOf(IdentityOperationError)
    await vi.advanceTimersByTimeAsync(30_000)
    await expect(client.resumeIdentityOperation()).rejects.toMatchObject({ state: 'PENDING' })
    expect(client.hasPendingIdentityOperation).toBe(true)
    current = false
    await expect(client.resumeIdentityOperation()).rejects.toThrow('stale-session')
    current = true
    expect(client.hasPendingIdentityOperation).toBe(false)
  })
})

describe('AdminAccess FAILED_SAFE reconciliation', () => {
  it('preserves FAILED_SAFE UUID instead of masking it with an earlier HTTP error, then recovers without a Profile body', async () => {
    let original = '',
      writes = 0,
      saved: unknown = null
    const client = new AdminAccessClient({
      accessToken: () => null,
      fetcher: async (input, init) => {
        const path = String(input),
          key = new Headers(init?.headers).get('Idempotency-Key')!
        if (path.endsWith('/reconcile')) {
          expect(saved).toMatchObject({ reconcileId: key })
          expect(init?.body).toBeUndefined()
          return operation(key, 'SUCCEEDED')
        }
        if (path.includes('/identity-operations/')) return operation(original, 'FAILED_SAFE')
        writes++
        original = key
        return response({ detail: 'Unknown downstream outcome' }, 503)
      },
    })
    client.bindIdentityRecovery('current-actor-session-full', null, (value) => {
      saved = value
    })
    await expect(client.updateUserAccess(42, update)).rejects.toBeInstanceOf(AdminAccessApiError)
    await expect(client.resumeIdentityOperation()).rejects.toMatchObject({
      state: 'FAILED_SAFE',
      operationId: original,
    })
    expect(client.failedIdentityOperation).toMatchObject({
      operationId: original,
      kind: 'ADMIN_ACCESS',
      userId: 42,
    })
    await client.confirmIdentityRecovery()
    await client.reconcileIdentityOperation(new AbortController().signal)
    expect(await client.resumeIdentityOperation()).toMatchObject({
      userId: 42,
      recovered: true,
      profile: null,
    })
    expect(writes).toBe(1)
    client.acknowledgeIdentityOperation()
    expect(saved).toBeNull()
  })
  it('rehydrates a pending empty-body recovery under the same key and rejects stale context responses', async () => {
    const failed = crypto.randomUUID(),
      current = crypto.randomUUID()
    const saved = {
      operationId: failed,
      kind: 'ADMIN_ACCESS',
      userId: 42,
      reconcileId: current,
      archivePreviewDigest: null,
    }
    const calls: { path: string; init: RequestInit | undefined }[] = []
    let finish!: (response: Response) => void
    const client = new AdminAccessClient({
      accessToken: () => null,
      fetcher: async (input, init) => {
        const path = String(input)
        calls.push({ path, init })
        if (path.endsWith('/reconcile'))
          return new Promise((resolve) => {
            finish = resolve
          })
        return operation(current, 'SUCCEEDED')
      },
    })
    client.bindIdentityRecovery('actor-session-full', saved, () => {})
    const pending = client.resumeIdentityOperation()
    for (let n = 0; n < 30 && !finish; n++) await Promise.resolve()
    expect(calls[0]!.path).toBe(`/api/academic/identity-operations/${current}`)
    expect(new Headers(calls[1]!.init?.headers).get('Idempotency-Key')).toBe(current)
    client.bindIdentityRecovery('another-actor-session', null, () => {})
    finish(operation(current, 'SUCCEEDED'))
    await expect(pending).rejects.toThrow('Владелец операции изменился')
    expect(client.failedIdentityOperation).toBeNull()
    expect(client.hasPendingIdentityOperation).toBe(false)
  })
})
