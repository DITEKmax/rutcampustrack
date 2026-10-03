import { describe, expect, it } from 'vitest'
import { createMobileNavigationStack, nestedRoute, rootRoute } from './navigation'

describe('Requests navigation stack', () => {
  it.each(['today', 'headman-today'] as const)('returns role selection to its %s entry and retains Back guards', (home) => {
    const navigation = createMobileNavigationStack(rootRoute(home))
    const roleTask = nestedRoute('profile', 'profile/role-switch', 'detail')
    navigation.push(roleTask, { preserveHistory: true })

    expect(navigation.entries.map((route) => route.id)).toEqual([home, 'profile/role-switch'])
    expect(navigation.current.root).toBe('profile')
    let canLeave = false
    const stopGuard = navigation.beforeBack(() => canLeave)
    expect(navigation.back()).toBe(roleTask)
    canLeave = true
    expect(navigation.back()?.id).toBe(home)
    expect(navigation.back()).toBeNull()
    stopGuard()
  })

  it('keeps the owning root for a cross-feature deep link without an explicit entry', () => {
    const navigation = createMobileNavigationStack(rootRoute('today'))
    navigation.push(nestedRoute('profile', 'profile/role-switch', 'detail'))

    expect(navigation.entries.map((route) => route.id)).toEqual(['profile', 'profile/role-switch'])
    expect(navigation.back()?.id).toBe('profile')
    expect(navigation.back()).toBeNull()
  })

  it('keeps More as the root and returns form → type → inbox → More', () => {
    const navigation = createMobileNavigationStack(rootRoute('more'))
    navigation.push(nestedRoute('more', 'more/requests', 'overview'))
    navigation.push(nestedRoute('more', 'more/requests/type', 'detail'))
    navigation.push(nestedRoute('more', 'more/requests/excuse', 'editor'))

    expect(navigation.current.id).toBe('more/requests/excuse')
    expect(navigation.back()?.id).toBe('more/requests/type')
    expect(navigation.back()?.id).toBe('more/requests')
    expect(navigation.back()?.id).toBe('more')
    expect(navigation.back()).toBeNull()
  })

  it('opens the headman map as its own root and keeps it outside task Back history', () => {
    const navigation = createMobileNavigationStack(rootRoute('headman-more'))
    navigation.goRoot('headman-map')

    expect(navigation.current).toMatchObject({ id: 'headman-map', root: 'headman-map', surface: 'root' })
    expect(navigation.back()).toBeNull()
  })
})
