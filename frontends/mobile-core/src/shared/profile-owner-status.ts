import type { MobileRoute } from './navigation'

/**
 * Profile pages keep their last accepted state while the browser is offline.
 * This owner-level status makes that state explicit without changing the
 * accepted feature screens or creating a second query owner.
 */
export function profileOwnerStaleMessage(route: MobileRoute, offline: boolean): string | null {
  if (!offline || !isProfileStaleRoute(route)) return null
  return 'Нет соединения. Данные профиля и списки могут быть устаревшими; сетевые действия недоступны.'
}

export function isProfileStaleRoute(route: MobileRoute): boolean {
  return route.root === 'profile'
    && (route.kind === 'root' || route.id === 'profile/sessions' || route.id === 'profile/history')
}

export function canRunProfileNetworkAction(offline: boolean): boolean {
  return !offline
}
