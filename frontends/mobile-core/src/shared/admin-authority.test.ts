import { describe, expect, it } from 'vitest'
import type { AdminAccessProfile } from '../features/admin-access/admin-access-client'
import { createAdminAuthorityOwner, shouldRefreshAdminAuthority } from './admin-authority'
import { adminCanNavigate, firstAdminRoute, createAdminNavigationItems } from './admin-navigation'
import {
  createGenerationBoundAdminDashboardClient,
  StaleSessionGenerationError,
} from './session-owner'

function authority(userId = 1, permissions = ['USERS_VIEW'], version = 1): AdminAccessProfile {
  return { userId, assigned: true, mode: 'LIMITED', permissions, version }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
describe('current admin authority isolation', () => {
  it('fails closed for absent authority and uses the first effective permitted destination', () => {
    expect(firstAdminRoute(null)).toBe('profile')
    expect(adminCanNavigate(null, 'home')).toBe(false)
    expect(
      createAdminNavigationItems(null)
        .filter((item) => !item.disabled)
        .map((item) => item.id),
    ).toEqual(['profile'])
    expect(firstAdminRoute(authority(1, ['SEMESTERS_VIEW']))).toBe('semesters')
    expect(adminCanNavigate(authority(1, ['DATA_DELETE']), 'map')).toBe(false)
    expect(adminCanNavigate(authority(1, ['SEMESTERS_VIEW']), 'more')).toBe(true)
  })
  it('drops a late old-owner FULL response after the current session binds another user', async () => {
    const old = deferred<AdminAccessProfile>()
    const owner = createAdminAuthorityOwner()
    owner.bind({ getMe: () => old.promise }, 'old', '1')
    const late = owner.refresh()
    owner.bind({ getMe: async () => authority(2, ['GROUPS_VIEW']) }, 'new', '2')
    await owner.refresh()
    old.resolve({
      ...authority(),
      mode: 'FULL',
      permissions: ['DASHBOARD_VIEW', 'ADMIN_ACCESS_MANAGE'],
    })
    await late
    expect(owner.snapshot.profile?.userId).toBe(2)
    expect(firstAdminRoute(owner.snapshot.profile)).toBe('groups')
    owner.dispose()
  })
  it('clears authority synchronously on refresh and rejects all requests captured before revocation', async () => {
    const response = deferred<AdminAccessProfile>()
    let calls = 0
    const owner = createAdminAuthorityOwner()
    owner.bind({ getMe: async () => authority() }, 'owner', '1')
    await owner.refresh()
    const captured = owner.snapshot.revision
    const api = createGenerationBoundAdminDashboardClient(
      { currentGeneration: () => 1, accessTokenFor: () => null, refreshFor: async () => undefined },
      async () => {
        calls++
        return Response.json({})
      },
      () => owner.assertCurrent(captured),
    )
    owner.bind({ getMe: () => response.promise }, 'owner', '1')
    const refresh = owner.refresh()
    expect(owner.snapshot.profile).toBeNull()
    await expect(api.getStats()).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(calls).toBe(0)
    response.resolve({ userId: 1, assigned: false, mode: null, permissions: [], version: 2 })
    await refresh
    expect(firstAdminRoute(owner.snapshot.profile)).toBe('profile')
    owner.dispose()
  })
  it('does not publish a payload whose JSON finishes after permissions were invalidated', async () => {
    const body = deferred<unknown>()
    const started = deferred<void>()
    const owner = createAdminAuthorityOwner()
    owner.bind({ getMe: async () => authority(1, ['DASHBOARD_VIEW']) }, 'owner', '1')
    await owner.refresh()
    const revision = owner.snapshot.revision
    const response = new Response('{}')
    response.json = () => {
      started.resolve()
      return body.promise
    }
    const api = createGenerationBoundAdminDashboardClient(
      { currentGeneration: () => 1, accessTokenFor: () => null, refreshFor: async () => undefined },
      async () => response,
      () => owner.assertCurrent(revision),
    )
    const request = api.getStats()
    await started.promise
    owner.clear()
    body.resolve({
      totalStudents: 1,
      totalTeachers: 1,
      totalGroups: 1,
      activeGroups: 1,
      activeSemesterName: null,
    })
    await expect(request).rejects.toBeInstanceOf(StaleSessionGenerationError)
    owner.dispose()
  })
  it('does not install a profile belonging to another user or fall back to FULL on failure', async () => {
    const owner = createAdminAuthorityOwner()
    owner.bind({ getMe: async () => authority(2) }, 'owner', '1')
    await owner.refresh()
    expect(owner.snapshot.status).toBe('error')
    expect(owner.snapshot.profile).toBeNull()
    expect(firstAdminRoute(owner.snapshot.profile)).toBe('profile')
    owner.bind(
      {
        getMe: async () => {
          throw new Error('Authority unavailable')
        },
      },
      'owner',
      '1',
    )
    await owner.refresh()
    expect(owner.snapshot.status).toBe('error')
    expect(adminCanNavigate(owner.snapshot.profile, 'users')).toBe(false)
    owner.dispose()
  })
})

it('preserves exact wrong-password proof codes and invalidates capability or opaque 403', async () => {
  for (const payload of [
    { code: 'CURRENT_PASSWORD_INVALID' },
    { extras: { code: 'CURRENT_PASSWORD_INVALID' } },
  ]) {
    const response = Response.json(payload, { status: 403 })
    expect(await shouldRefreshAdminAuthority(response)).toBe(false)
    expect(await response.json()).toEqual(payload)
  }
  expect(
    await shouldRefreshAdminAuthority(
      Response.json({ code: 'ADMIN_PERMISSION_DENIED' }, { status: 403 }),
    ),
  ).toBe(true)
  expect(await shouldRefreshAdminAuthority(new Response('', { status: 403 }))).toBe(true)
  expect(await shouldRefreshAdminAuthority(Response.json({}, { status: 400 }))).toBe(false)
})
