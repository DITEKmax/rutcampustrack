import {
  effectScope,
  onScopeDispose,
  readonly,
  ref,
  shallowRef,
  watch,
  type EffectScope,
} from 'vue'
import { createGenerationBoundNotificationsV2Api } from '../../../../mobile-core/src/features/notifications/notifications-v2-client'
import type {
  NotificationItem,
  NotificationPreferencesV2,
} from '../../../../mobile-core/src/features/notifications/notifications-v2-models'
import { NotificationsUnreadState } from '../../../../mobile-core/src/features/notifications/notifications-unread'
import { NotificationsMutationQueue } from '../../../../mobile-core/src/features/notifications/notifications-mutations'
import { NotificationsApiError } from '../../../../mobile-core/src/features/notifications/notifications-client'
import { createWebTransport, WebAuthorityChangedError } from '../../app/api'
import { useWebLiveRefresh } from '../../app/live'
import { webSession, message } from '../../auth/session'
export type WebToast = { item: NotificationItem; expiresAt: number }
export function createWebNotifications() {
  const identity = webSession.identity.value!
  const transport = createWebTransport()
  const generation = webSession.generation.value
  const api = createGenerationBoundNotificationsV2Api(
    {
      currentGeneration: () => webSession.generation.value,
      currentRole: () => webSession.snapshot.value?.activeRole ?? null,
      currentContext: () => webSession.identity.value ?? '',
      accessTokenFor: (expected) => {
        if (expected !== generation) throw new WebAuthorityChangedError()
        return transport.accessToken()
      },
      refreshFor: async (expected) => {
        if (expected !== generation) throw new WebAuthorityChangedError()
        await transport.onUnauthorized()
      },
    },
    transport.fetcher,
  )
  const unread = new NotificationsUnreadState()
  const count = ref<number | null>(null)
  const stopUnread = unread.subscribe(() => {
    count.value = unread.view.count
  })
  unread.bind(identity, api)
  const items = shallowRef<readonly NotificationItem[]>([])
  const nextCursor = ref<string | null>(null)
  const loading = ref(false)
  const loadingMore = ref(false)
  const error = ref('')
  const notice = ref('')
  const pending = ref(false)
  const uncertain = ref(false)
  const reconciling = ref(false)
  const toasts = shallowRef<readonly WebToast[]>([])
  let preferences: NotificationPreferencesV2 | null = null
  let uncertainIntent:
    | { kind: 'item'; item: NotificationItem; read: boolean }
    | { kind: 'all' }
    | null = null
  let initialized = false
  let readRevision = 0
  let writeRevision = 0
  let active = true
  let inboxReads: Promise<void> = Promise.resolve()
  function serialRead(task: () => Promise<void>) {
    const result = inboxReads.then(task, task)
    inboxReads = result.catch(() => undefined)
    return result
  }
  const seen = new Set<string>()
  const queue = new NotificationsMutationQueue()
  function assertCurrent() {
    if (!active) throw new WebAuthorityChangedError()
    transport.assertCurrent()
  }
  const clock = setInterval(() => {
    if (toasts.value.some((value) => value.expiresAt <= Date.now()))
      toasts.value = toasts.value.filter((value) => value.expiresAt > Date.now())
  }, 250)
  function toastAllowed(item: NotificationItem) {
    return (
      preferences !== null &&
      (preferences.mutedUntil === null || Date.parse(preferences.mutedUntil) <= Date.now()) &&
      preferences.types[item.type]?.appPush === true
    )
  }
  function acceptInbox(page: readonly NotificationItem[], live: boolean) {
    const fresh = page.filter((item) => !seen.has(item.id))
    for (const item of page) seen.add(item.id)
    if (live && initialized) {
      const additions = fresh
        .filter(toastAllowed)
        .map((item) => ({ item, expiresAt: Date.now() + 10_000 }))
      toasts.value = [
        ...toasts.value.filter((value) => value.expiresAt > Date.now()),
        ...additions,
      ].slice(-3)
    }
    items.value = [
      ...new Map([...items.value, ...page].map((item) => [item.id, item])).values(),
    ].sort((a, b) => b.orderAt.localeCompare(a.orderAt) || b.id.localeCompare(a.id))
    initialized = true
  }
  function load(live = false) {
    return serialRead(() => loadHead(live))
  }
  async function loadHead(live: boolean) {
    if (!active || identity !== webSession.identity.value) return
    const revision = ++readRevision
    const writesAtStart = writeRevision
    if (!initialized) loading.value = true
    try {
      const [page, prefs] = await Promise.all([api.inbox(), api.getPreferences().catch(() => null)])
      assertCurrent()
      if (revision !== readRevision || writesAtStart !== writeRevision) return
      preferences = prefs
      if (!initialized) {
        items.value = []
      }
      // Retained cards are deduplicated, but pagination must follow the latest head.
      nextCursor.value = page.nextCursor
      acceptInbox(page.items, live)
      error.value = ''
      loading.value = false
      void unread.invalidate()
    } catch (cause) {
      if (
        active &&
        revision === readRevision &&
        writesAtStart === writeRevision &&
        !(cause instanceof WebAuthorityChangedError)
      )
        error.value = message(cause)
    } finally {
      if (revision === readRevision) loading.value = false
    }
  }
  async function more() {
    if (!nextCursor.value || loadingMore.value || pending.value || reconciling.value) return
    loadingMore.value = true
    try {
      await serialRead(appendPage)
    } finally {
      loadingMore.value = false
    }
  }
  async function appendPage() {
    if (!active || identity !== webSession.identity.value) return
    if (!nextCursor.value || pending.value || reconciling.value) return
    const revision = readRevision
    const writesAtStart = writeRevision
    try {
      const page = await api.inbox({ cursor: nextCursor.value })
      assertCurrent()
      if (revision !== readRevision || writesAtStart !== writeRevision) return
      acceptInbox(page.items, false)
      nextCursor.value = page.nextCursor
    } catch (cause) {
      if (
        active &&
        revision === readRevision &&
        writesAtStart === writeRevision &&
        !(cause instanceof WebAuthorityChangedError)
      )
        error.value = message(cause)
    }
  }
  // null proves absence at the end of the chain; undefined means the bounded read is incomplete.
  async function readback(item: NotificationItem): Promise<NotificationItem | null | undefined> {
    let cursor: string | undefined
    const cursors = new Set<string>()
    for (let index = 0; index < 100; index++) {
      const page = await api.inbox(cursor ? { cursor } : {})
      assertCurrent()
      const actual = page.items.find((value) => value.id === item.id)
      if (actual) {
        items.value = items.value.map((value) => (value.id === item.id ? actual : value))
        return actual
      }
      if (page.nextCursor === null) return null
      if (cursors.has(page.nextCursor)) return undefined
      cursor = page.nextCursor
      cursors.add(cursor)
    }
    return undefined
  }
  function mark(item: NotificationItem, read: boolean) {
    return queue.run(async () => {
      if (uncertain.value) return
      assertCurrent()
      pending.value = true
      writeRevision++
      readRevision++
      notice.value = ''
      try {
        const current = items.value.find((value) => value.id === item.id) ?? item
        const ack = await api.setReadState(current, read)
        assertCurrent()
        items.value = items.value.map((value) => (value.id === item.id ? ack.item : value))
        unread.acknowledge(ack.unreadCount)
      } catch (cause) {
        if (cause instanceof WebAuthorityChangedError) return
        if (
          cause instanceof NotificationsApiError &&
          cause.status !== null &&
          cause.status >= 400 &&
          cause.status < 500
        ) {
          await load()
          notice.value =
            cause.status === 403
              ? 'Изменение уведомления недоступно для текущего контекста.'
              : 'Уведомление изменилось или недоступно. Показано текущее состояние.'
          return
        }
        uncertainIntent = { kind: 'item', item, read }
        try {
          const actual = await readback(item)
          if (actual && (actual.readAt !== null) === read)
            notice.value = 'Состояние уведомления подтверждено.'
          else if (cause instanceof NotificationsApiError && cause.status === 409)
            notice.value = 'Уведомление уже изменилось. Показано текущее состояние.'
          else {
            uncertain.value = true
            notice.value = 'Результат неизвестен. Проверь уведомление перед повторным изменением.'
          }
        } catch {
          uncertain.value = true
          notice.value = 'Результат неизвестен. Не повторяй действие до проверки состояния.'
        }
      } finally {
        writeRevision++
        pending.value = false
        void unread.invalidate()
      }
    })
  }
  function markAll() {
    return queue.run(async () => {
      if (uncertain.value) return
      assertCurrent()
      pending.value = true
      writeRevision++
      readRevision++
      notice.value = ''
      try {
        const ack = await api.markAllRead()
        assertCurrent()
        unread.acknowledge(ack.unreadCount)
        items.value = []
        initialized = false
        await load()
      } catch (cause) {
        if (cause instanceof WebAuthorityChangedError) return
        if (
          cause instanceof NotificationsApiError &&
          cause.status !== null &&
          cause.status >= 400 &&
          cause.status < 500
        ) {
          notice.value = 'Не удалось отметить уведомления. Проверь текущий доступ.'
          return
        }
        uncertainIntent = { kind: 'all' }
        try {
          const actual = await api.unreadCount()
          assertCurrent()
          unread.acknowledge(actual)
          if (actual === 0) {
            items.value = []
            initialized = false
            await load()
            notice.value = 'Все уведомления прочитаны.'
          } else {
            uncertain.value = true
            notice.value = 'Результат неизвестен. Проверь историю перед повторным действием.'
          }
        } catch {
          uncertain.value = true
          notice.value = 'Не удалось проверить результат. Повторное действие заблокировано.'
        }
      } finally {
        writeRevision++
        pending.value = false
      }
    })
  }
  async function reconcile() {
    if (pending.value || reconciling.value || !uncertain.value) return
    if (!active || identity !== webSession.identity.value) return
    reconciling.value = true
    writeRevision++
    readRevision++
    try {
      const intent = uncertainIntent
      let confirmed = false
      if (intent?.kind === 'item') {
        const actual = await readback(intent.item)
        if (actual === undefined) {
          notice.value =
            'Проверка истории не завершена. Запись не повторялась; попробуй проверить позже.'
          return
        }
        confirmed = actual !== null && (actual.readAt !== null) === intent.read
      } else if (intent?.kind === 'all') {
        const actual = await api.unreadCount()
        assertCurrent()
        unread.acknowledge(actual)
        confirmed = actual === 0
      }
      assertCurrent()
      await load()
      assertCurrent()
      uncertain.value = false
      uncertainIntent = null
      notice.value = confirmed
        ? 'Состояние уведомлений подтверждено.'
        : 'Показано текущее состояние. Предыдущее изменение не подтверждено. Если нужно, выбери новое действие.'
    } catch {
      try {
        assertCurrent()
        notice.value = 'Не удалось проверить состояние. Запись не повторялась.'
      } catch {
        /* Old owner has no recovery continuation. */
      }
    } finally {
      reconciling.value = false
    }
  }
  function dismiss(id: string) {
    toasts.value = toasts.value.filter((value) => value.item.id !== id)
  }
  useWebLiveRefresh(() => load(true), { blocked: () => pending.value || reconciling.value })
  onScopeDispose(() => {
    active = false
    ++readRevision
    clearInterval(clock)
    stopUnread()
    unread.bind(null, null)
    items.value = []
    toasts.value = []
  })
  void load()
  return {
    items: readonly(items),
    count: readonly(count),
    nextCursor: readonly(nextCursor),
    loading: readonly(loading),
    loadingMore: readonly(loadingMore),
    pending: readonly(pending),
    uncertain: readonly(uncertain),
    reconciling: readonly(reconciling),
    error: readonly(error),
    notice: readonly(notice),
    toasts: readonly(toasts),
    reconcile,
    load,
    more,
    mark,
    markAll,
    dismiss,
  }
}
export const webNotifications = shallowRef<ReturnType<typeof createWebNotifications> | null>(null)
let scope: EffectScope | undefined
watch(
  webSession.identity,
  () => {
    scope?.stop()
    scope = undefined
    webNotifications.value = null
    if (webSession.snapshot.value?.activeRole) {
      scope = effectScope(true)
      webNotifications.value = scope.run(createWebNotifications) ?? null
    }
  },
  { immediate: true, flush: 'sync' },
)
