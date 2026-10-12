import { describe, expect, it } from 'vitest'
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
  it('does not repeat an uncertain permission mutation and sends an explicit expected version on revoke', async () => {
    const calls: RequestInit[] = []
    const client = new AdminAccessClient({
      accessToken: () => null,
      fetcher: async (_input, init) => {
        calls.push(init!)
        return calls.length === 1
          ? response({ detail: 'Недоступно' }, 503)
          : new Response(null, { status: 204 })
      },
    })
    await expect(
      client.updateUserAccess(42, {
        mode: 'LIMITED',
        permissions: ['USERS_VIEW'],
        expectedVersion: 7,
      }),
    ).rejects.toBeInstanceOf(AdminAccessApiError)
    expect(calls).toHaveLength(1)
    await client.revokeUserAccess(42, 7)
    expect(calls[1]?.method).toBe('DELETE')
    expect(JSON.parse(String(calls[1]?.body))).toEqual({ expectedVersion: 7 })
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
