import { describe, expect, it } from 'vitest'
import { nestedRoute, rootRoute } from './navigation'
import {
  canRunProfileNetworkAction,
  isProfileStaleRoute,
  profileOwnerStaleMessage,
} from './profile-owner-status'

describe('profile owner offline status', () => {
  it.each([
    rootRoute('profile'),
    nestedRoute('profile', 'profile/history', 'detail'),
    nestedRoute('profile', 'profile/sessions', 'detail'),
  ])('marks an online-loaded %s route stale after going offline', (route) => {
    expect(isProfileStaleRoute(route)).toBe(true)
    expect(profileOwnerStaleMessage(route, false)).toBeNull()
    expect(profileOwnerStaleMessage(route, true)).toContain('могут быть устаревшими')
  })

  it('blocks offline page actions while allowing explicit online actions', () => {
    expect(canRunProfileNetworkAction(false)).toBe(true)
    expect(canRunProfileNetworkAction(true)).toBe(false)
  })
})
