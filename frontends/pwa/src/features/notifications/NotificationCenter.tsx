import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useAuth } from '@/features/auth/AuthProvider'

/**
 * Global notification center for the PWA.
 *
 * Mirrors the web-panel's NotificationCenterService: one persistent STOMP
 * client per session, subscribed to /topic/group/{groupId} and (for headmen)
 * /topic/group/{groupId}/headman. Incoming envelopes are archived in
 * sessionStorage so the notifications tab survives route changes, and each
 * incoming event is also surfaced as a native Notification with sound when
 * the app is in background — matching the "уведомление должно появиться в
 * системе телефона со звуком" requirement.
 */

const STORED_TYPES: ReadonlySet<string> = new Set([
  'lesson.started',
  'lesson.cancelled',
  'homework.published',
  'homework.updated',
  'attendance.marked',
  'late_checkin.requested',
  'late_checkin.decided',
  'excuse.requested',
  'excuse.decided',
])

const USER_SCOPED_TYPES: ReadonlySet<string> = new Set([
  'late_checkin.decided',
  'excuse.decided',
])

const HEADMAN_ONLY_TYPES: ReadonlySet<string> = new Set([
  'late_checkin.requested',
  'excuse.requested',
])

const STORAGE_KEY = 'rct.pwa.notifications.v1'
const MAX_ITEMS = 200

export interface StompEnvelope {
  type: string
  payload: Record<string, unknown>
}

export interface NotificationRecord {
  id: string
  type: string
  payload: Record<string, unknown>
  receivedAt: string
  read: boolean
  archived: boolean
}

interface NotificationCenterValue {
  items: NotificationRecord[]
  unreadCount: number
  markAllRead: () => void
  archive: (id: string) => void
  clearAll: () => void
}

const Context = createContext<NotificationCenterValue | null>(null)

function loadFromStorage(): NotificationRecord[] {
  try {
    if (typeof sessionStorage === 'undefined') return []
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as NotificationRecord[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function persist(items: NotificationRecord[]): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(items))
  } catch {
    // storage quota / disabled — best-effort only
  }
}

function buildTitle(type: string, payload: Record<string, unknown>): string {
  switch (type) {
    case 'lesson.started':
      return 'Пара началась'
    case 'lesson.cancelled':
      return 'Пара отменена'
    case 'homework.published':
      return 'Новое домашнее задание'
    case 'homework.updated':
      return 'Изменения в домашнем задании'
    case 'attendance.marked':
      return 'Отметка поставлена'
    case 'late_checkin.requested':
      return 'Запрос опоздалой отметки'
    case 'late_checkin.decided':
      return payload.approved === true
        ? 'Запрос одобрен'
        : 'Запрос отклонён'
    case 'excuse.requested':
      return 'Новый тикет о пропуске'
    case 'excuse.decided':
      return payload.approved === true
        ? 'Уважительная одобрена'
        : 'Уважительная отклонена'
    default:
      return 'RutTrack'
  }
}

function buildBody(payload: Record<string, unknown>): string {
  const subject =
    typeof payload.subject_name === 'string' ? payload.subject_name : null
  const student =
    typeof payload.student_name === 'string'
      ? payload.student_name
      : typeof payload.studentName === 'string'
      ? payload.studentName
      : null
  if (subject && student) return `${student} · ${subject}`
  return subject ?? student ?? ''
}

export function NotificationCenterProvider({ children }: { children: ReactNode }) {
  const { user, accessToken } = useAuth()
  const groupId = user?.groupId ?? null
  const userId = user?.id ?? null
  const isHeadman = !!user?.isHeadman

  const [items, setItems] = useState<NotificationRecord[]>(() => loadFromStorage())
  const itemsRef = useRef(items)
  useEffect(() => {
    itemsRef.current = items
    persist(items)
  }, [items])

  const tokenRef = useRef(accessToken)
  useEffect(() => {
    tokenRef.current = accessToken
  }, [accessToken])

  useEffect(() => {
    if (!groupId || !userId || !accessToken) return

    const client = new Client({
      webSocketFactory: () =>
        new SockJS(`/api/ws?token=${tokenRef.current ?? ''}`),
      reconnectDelay: 2000,
      onConnect: () => {
        const handle = (body: string) => {
          let envelope: StompEnvelope
          try {
            envelope = JSON.parse(body) as StompEnvelope
          } catch {
            return
          }
          if (!envelope.type) return

          if (USER_SCOPED_TYPES.has(envelope.type)) {
            const payloadUserId = envelope.payload?.['user_id']
            if (
              typeof payloadUserId !== 'number' ||
              payloadUserId !== userId
            ) {
              return
            }
          }

          if (!STORED_TYPES.has(envelope.type)) return

          const record: NotificationRecord = {
            id:
              typeof crypto !== 'undefined' && 'randomUUID' in crypto
                ? crypto.randomUUID()
                : `${Date.now()}-${Math.random()}`,
            type: envelope.type,
            payload: envelope.payload ?? {},
            receivedAt: new Date().toISOString(),
            read: false,
            archived: false,
          }

          setItems((prev) => {
            const next = [record, ...prev]
            return next.length > MAX_ITEMS ? next.slice(0, MAX_ITEMS) : next
          })

          showNativeNotification(record)
        }

        client.subscribe(`/topic/group/${groupId}`, (message) =>
          handle(message.body),
        )
        if (isHeadman) {
          client.subscribe(`/topic/group/${groupId}/headman`, (message) =>
            handle(message.body),
          )
        }
      },
      onStompError: (frame) => {
        // eslint-disable-next-line no-console
        console.error('[notifications] STOMP error:', frame.headers['message'])
      },
    })
    client.activate()

    return () => {
      client.deactivate()
    }
  }, [groupId, userId, isHeadman, accessToken])

  const markAllRead = useCallback(() => {
    setItems((prev) =>
      prev.every((i) => i.read) ? prev : prev.map((i) => ({ ...i, read: true })),
    )
  }, [])

  const archive = useCallback((id: string) => {
    setItems((prev) =>
      prev.map((i) => (i.id === id ? { ...i, archived: true, read: true } : i)),
    )
  }, [])

  const clearAll = useCallback(() => setItems([]), [])

  const unreadCount = useMemo(
    () => items.filter((i) => !i.read && !i.archived).length,
    [items],
  )

  const value = useMemo<NotificationCenterValue>(
    () => ({ items, unreadCount, markAllRead, archive, clearAll }),
    [items, unreadCount, markAllRead, archive, clearAll],
  )

  return <Context.Provider value={value}>{children}</Context.Provider>
}

export function useNotificationCenter(): NotificationCenterValue {
  const ctx = useContext(Context)
  if (!ctx) {
    throw new Error(
      'useNotificationCenter must be used within NotificationCenterProvider',
    )
  }
  return ctx
}

function showNativeNotification(record: NotificationRecord): void {
  if (typeof window === 'undefined') return
  if (!('Notification' in window)) return
  if (Notification.permission !== 'granted') return
  if (typeof document !== 'undefined' && document.visibilityState === 'visible') {
    // Focused PWA — no need to raise a system banner; the tab already shows
    // the unread badge. The service worker suppresses foreground pushes too.
    return
  }

  const title = buildTitle(record.type, record.payload)
  const body = buildBody(record.payload)
  try {
    const n = new Notification(title, {
      body,
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-192.png',
      tag: `${record.type}-${record.id}`,
      silent: false,
    })
    // Best-effort haptic feedback — not all browsers support vibrate.
    if ('vibrate' in navigator) {
      try {
        navigator.vibrate([120, 60, 120])
      } catch {
        // ignore
      }
    }
    // Auto-close after 8s so the user's notification shade doesn't fill up.
    setTimeout(() => n.close(), 8000)
  } catch {
    // createNotification throws on some browsers when called from page
    // context (e.g., Safari). Service worker handles those via push.
  }
}

export function describeNotification(record: NotificationRecord): {
  title: string
  body: string
} {
  return {
    title: buildTitle(record.type, record.payload),
    body: buildBody(record.payload),
  }
}

export function isHeadmanOnlyType(type: string): boolean {
  return HEADMAN_ONLY_TYPES.has(type)
}
