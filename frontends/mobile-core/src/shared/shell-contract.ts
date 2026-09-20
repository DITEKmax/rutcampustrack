import type { MobileBackOwner } from './host'
import type { MobileRoute } from './navigation'

function isRootLikeRoute(route: MobileRoute): boolean {
  return route.kind === 'root' || route.surface === 'overview'
}

function isTaskLikeRoute(route: MobileRoute): boolean {
  return route.kind === 'nested' && (route.surface === 'task' || route.surface === 'detail' || route.surface === 'editor')
}

export function shouldShowMobileDock(route: MobileRoute, keyboardVisible: boolean): boolean {
  return isRootLikeRoute(route) && !keyboardVisible
}

export function resolvedBackOwner(route: MobileRoute, hostBackOwner: MobileBackOwner = 'product'): MobileBackOwner {
  if (!isTaskLikeRoute(route)) return 'none'
  return hostBackOwner
}

export function shouldShowProductBack(route: MobileRoute, hostBackOwner: MobileBackOwner = 'product'): boolean {
  return resolvedBackOwner(route, hostBackOwner) === 'product'
}

export function shouldShowHostBack(route: MobileRoute, hostBackOwner: MobileBackOwner): boolean {
  return resolvedBackOwner(route, hostBackOwner) === 'host'
}
