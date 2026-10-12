import overviewIcon from '../assets/admin/overview.svg'
import usersIcon from '../assets/admin/users.svg'
import groupsIcon from '../assets/admin/groups.svg'
import moreIcon from '../assets/admin/more.svg'
import profileIcon from '../assets/admin/profile.svg'
import type { AdminAccessProfile } from '../features/admin-access/admin-access-client'
import type { MobileBottomNavItems, MobileRootRouteId } from './navigation'

export type AdminRoleRoute =
  | 'home'
  | 'map'
  | 'map-statistics'
  | 'semesters'
  | 'users'
  | 'groups'
  | 'more'
  | 'profile'
export function adminRootId(active: AdminRoleRoute): MobileRootRouteId {
  if (active === 'profile') return 'profile'
  if (
    active === 'map' ||
    active === 'map-statistics' ||
    active === 'semesters' ||
    active === 'more'
  )
    return 'admin-more'
  return `admin-${active}`
}
export function adminDestination(route: MobileRootRouteId): AdminRoleRoute | null {
  const routes: Partial<Record<MobileRootRouteId, AdminRoleRoute>> = {
    'admin-home': 'home',
    'admin-users': 'users',
    'admin-groups': 'groups',
    'admin-more': 'more',
    profile: 'profile',
  }
  return routes[route] ?? null
}
export function adminCanNavigate(
  profile: AdminAccessProfile | null | undefined,
  route: AdminRoleRoute,
): boolean {
  if (route === 'profile') return true
  if (!profile?.assigned || !profile.mode) return false
  const has = (code: string): boolean => profile.permissions.includes(code)
  if (route === 'home') return has('DASHBOARD_VIEW')
  if (route === 'users') return has('USERS_VIEW')
  if (route === 'groups') return has('GROUPS_VIEW')
  if (route === 'semesters') return has('SEMESTERS_VIEW')
  if (route === 'map' || route === 'map-statistics') return has('CAMPUS_MAP_MANAGE')
  return has('SEMESTERS_VIEW') || has('CAMPUS_MAP_MANAGE')
}
export function firstAdminRoute(profile: AdminAccessProfile | null): AdminRoleRoute {
  return (
    (['home', 'users', 'groups', 'semesters', 'map'] as const).find((route) =>
      adminCanNavigate(profile, route),
    ) ?? 'profile'
  )
}
export function createAdminNavigationItems(
  authority: AdminAccessProfile | null = null,
): MobileBottomNavItems {
  const items: MobileBottomNavItems = [
    { id: 'admin-home', route: 'admin-home', label: 'Обзор', icon: overviewIcon },
    { id: 'admin-users', route: 'admin-users', label: 'Аккаунты', icon: usersIcon },
    { id: 'admin-groups', route: 'admin-groups', label: 'Группы', icon: groupsIcon },
    { id: 'admin-more', route: 'admin-more', label: 'Ещё', icon: moreIcon },
    { id: 'profile', route: 'profile', label: 'Профиль', icon: profileIcon },
  ]
  const gated = (item: MobileBottomNavItems[number]): MobileBottomNavItems[number] => {
    const destination = adminDestination(item.route)
    return destination && adminCanNavigate(authority, destination)
      ? item
      : { ...item, disabled: true, disabledReason: 'Нет доступа к разделу' }
  }
  return [gated(items[0]), gated(items[1]), gated(items[2]), gated(items[3]!), gated(items[4]!)]
}
