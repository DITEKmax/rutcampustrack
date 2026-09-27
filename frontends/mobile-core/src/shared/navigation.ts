export const mobileRootRouteIds = [
  'today', 'homework', 'attendance', 'more', 'profile',
  'teacher-home', 'teacher-attendance', 'teacher-stats', 'teacher-map',
  'headman-today', 'headman-attendance', 'headman-requests', 'headman-more', 'headman-map',
] as const

export type MobileRootRouteId = (typeof mobileRootRouteIds)[number]

/**
 * Nested routes are owned by a root feature. Keeping the root in the id makes
 * accidental cross-feature transitions visible to TypeScript callers.
 */
export type MobileNestedRouteId = `${MobileRootRouteId}/${string}`

export type MobileRouteSurface = 'root' | 'overview' | 'task' | 'detail' | 'editor'
export type MobileNestedRouteSurface = Exclude<MobileRouteSurface, 'root'>

export type MobileRootRoute = {
  kind: 'root'
  id: MobileRootRouteId
  root: MobileRootRouteId
  surface: 'root'
}

export type MobileNestedRoute = {
  kind: 'nested'
  id: MobileNestedRouteId
  root: MobileRootRouteId
  surface: MobileNestedRouteSurface
}

export type MobileRoute = MobileRootRoute | MobileNestedRoute

export interface MobileBottomNavItem {
  id: MobileRootRouteId
  label: string
  accessibleLabel?: string
  icon: string
  route: MobileRootRouteId
  badge?: string | number
  disabled?: boolean
  disabledReason?: string
}

export type MobileBottomNavItems =
  | readonly [MobileBottomNavItem, MobileBottomNavItem, MobileBottomNavItem]
  | readonly [MobileBottomNavItem, MobileBottomNavItem, MobileBottomNavItem, MobileBottomNavItem]
  | readonly [MobileBottomNavItem, MobileBottomNavItem, MobileBottomNavItem, MobileBottomNavItem, MobileBottomNavItem]

export function rootRoute(id: MobileRootRouteId): MobileRootRoute {
  return { kind: 'root', id, root: id, surface: 'root' }
}

export function nestedRoute<TRoot extends MobileRootRouteId>(
  root: TRoot,
  id: `${TRoot}/${string}`,
  surface: MobileNestedRouteSurface,
): MobileNestedRoute {
  return { kind: 'nested', id, root, surface }
}

export interface MobileNavigationStack {
  readonly entries: readonly MobileRoute[]
  readonly current: MobileRoute
  subscribe(listener: () => void): () => void
  push(route: MobileRoute): MobileRoute
  replace(route: MobileRoute): MobileRoute
  goRoot(id: MobileRootRouteId): MobileRootRoute
  back(): MobileRoute | null
}

/**
 * Small, framework-independent stack used by both PWA and TMA adapters.
 * Components decide when to expose a route; this object only owns stack
 * semantics and never claims that a route has an implementation.
 */
export function createMobileNavigationStack(initial: MobileRoute = rootRoute('today')): MobileNavigationStack {
  const entries: MobileRoute[] = initial.kind === 'nested'
    ? [rootRoute(initial.root), initial]
    : [initial]
  const listeners = new Set<() => void>()

  function notify(): void {
    for (const listener of [...listeners]) listener()
  }

  function goRoot(id: MobileRootRouteId): MobileRootRoute {
    const route = rootRoute(id)
    if (entries.length === 1 && entries[0]!.id === route.id) return route
    entries.splice(0, entries.length, route)
    notify()
    return route
  }

  return {
    get entries(): readonly MobileRoute[] {
      return entries.slice()
    },
    get current(): MobileRoute {
      return entries[entries.length - 1]!
    },
    subscribe(listener): () => void {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    push(route): MobileRoute {
      if (route.kind === 'root') return goRoot(route.id)
      if (entries[entries.length - 1]!.id === route.id) return entries[entries.length - 1]!

      // A nested deeplink belongs to its own root. Start that root's history
      // explicitly so Back returns to the owning root and stops there.
      if (entries[entries.length - 1]!.root !== route.root) {
        entries.splice(0, entries.length, rootRoute(route.root), route)
      } else {
        entries.push(route)
      }
      notify()
      return entries[entries.length - 1]!
    },
    replace(route): MobileRoute {
      if (route.kind === 'root') return goRoot(route.id)

      const current = entries[entries.length - 1]!
      if (entries.length === 1 || current.root !== route.root) {
        entries.splice(0, entries.length, rootRoute(route.root), route)
      } else {
        entries[entries.length - 1] = route
      }
      notify()
      return route
    },
    goRoot,
    back(): MobileRoute | null {
      if (entries.length <= 1) return null
      entries.pop()
      notify()
      return entries[entries.length - 1]!
    },
  }
}

export function isRootLikeRoute(route: MobileRoute): boolean {
  return route.kind === 'root' || route.surface === 'overview'
}

export function isTaskLikeRoute(route: MobileRoute): boolean {
  return route.kind === 'nested' && (route.surface === 'task' || route.surface === 'detail' || route.surface === 'editor')
}
