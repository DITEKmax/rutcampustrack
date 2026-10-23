import { computed, onScopeDispose, toValue, watch, type MaybeRefOrGetter } from 'vue'
import {
  createGenerationBoundNotificationsRealtime,
  type NotificationsRealtimeLifecycle,
} from '../../../mobile-core/src/features/notifications/notifications-realtime'
import { createWebTransport, WebAuthorityChangedError } from './api'
import { webSession } from '../auth/session'
import { webAssistantAccess } from './assistant'

interface Listener {
  identity: string | null
  dashboard: boolean
  changed(): void
}
const listeners = new Set<Listener>()
let realtime: NotificationsRealtimeLifecycle | null = null
let signature: string | null = null

function notify(dashboard = false) {
  if (!dashboard && webSession.snapshot.value?.activeRole === 'STUDENT')
    void webAssistantAccess.load(true).catch(() => undefined)
  for (const listener of listeners) {
    if (listener.identity === webSession.identity.value && (!dashboard || listener.dashboard))
      listener.changed()
  }
}

function connect() {
  const session = webSession.snapshot.value
  const dashboard = [...listeners].some(
    (listener) => listener.dashboard && listener.identity === webSession.identity.value,
  )
  const next = session?.activeRole ? `${webSession.identity.value}:${dashboard}` : null
  if (next === signature) return
  realtime?.dispose()
  realtime = null
  signature = next
  if (!session?.activeRole) return
  const transport = createWebTransport()
  const generation = webSession.generation.value
  const role = session.activeRole
  const groupId = session.roles.find((grant) => grant.role === role)?.groupId ?? null
  realtime = createGenerationBoundNotificationsRealtime({
    owner: {
      currentGeneration: () => webSession.generation.value,
      accessTokenFor: (expected) => {
        if (expected !== generation) throw new WebAuthorityChangedError()
        return transport.accessToken()
      },
      refreshFor: async (expected) => {
        if (expected !== generation) throw new WebAuthorityChangedError()
        await transport.onUnauthorized()
      },
    },
    scope: {
      userId: session.userId,
      groupId,
      headman: role === 'HEADMAN',
      adminDashboard: role === 'ADMIN' && dashboard,
    },
    fetcher: transport.fetcher,
    onChanged: () => notify(),
    onAdminDashboardChanged: () => notify(true),
  })
}

// Only real transport and browser reconnect signals invalidate role projections.
watch(webSession.identity, connect, { flush: 'sync', immediate: true })
window.addEventListener('online', () => notify())
window.addEventListener('focus', () => notify())

/** Dirty drafts retain one invalidation until the feature explicitly becomes safe to refresh. */
export function useWebLiveRefresh(
  load: () => void | Promise<void>,
  options: {
    blocked?: MaybeRefOrGetter<boolean>
    /** Only callers with authoritative DASHBOARD_VIEW may request this topic. */
    adminDashboard?: boolean
  } = {},
) {
  const identity = webSession.identity.value
  const blocked = computed(() => Boolean(toValue(options.blocked)))
  let pending = false
  let running = false
  let active = true
  async function flush() {
    if (!active || identity !== webSession.identity.value || blocked.value || running || !pending)
      return
    pending = false
    running = true
    try {
      await load()
    } catch {
      /* The page owns its REST error and retry state. */
    } finally {
      running = false
      if (pending) void flush()
    }
  }
  const listener: Listener = {
    identity,
    dashboard: options.adminDashboard ?? false,
    changed: () => {
      pending = true
      void flush()
    },
  }
  listeners.add(listener)
  connect()
  watch(blocked, () => {
    if (!blocked.value) void flush()
  })
  onScopeDispose(() => {
    active = false
    listeners.delete(listener)
    connect()
  })
}
