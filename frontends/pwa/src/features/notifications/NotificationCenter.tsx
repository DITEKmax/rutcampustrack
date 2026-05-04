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
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/features/auth/AuthProvider'
import { buildWsUrl } from '@/features/auth/wsTicket'
import {
  loadPrefs,
  shouldStoreInHistory,
  shouldSuppressBanner,
} from './notificationPrefs'
import { markAllNotificationsRead } from './notificationsApi'
import { notificationsQueryKeys } from './useNotificationHistory'
import { PWA_ICON_URL } from '@/features/push/pushUtils'

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

// attendance.marked попадает в историю ТОЛЬКО для manual-mark старостой
// (фильтр payload.marked_by === 'headman' в handleFrame). Self-check-in и
// auto-absent — служебные ACK, в список не кладём.
const STORED_TYPES: ReadonlySet<string> = new Set([
  'lesson.started',
  'lesson.reminder',
  'lesson.cancelled',
  'lesson.one_off.created',
  'lesson.one_off.cancelled',
  'homework.published',
  'homework.updated',
  'late_checkin.requested',
  'late_checkin.decided',
  'excuse.requested',
  'excuse.decided',
  'attendance.marked',
  'group.renamed',
  'group.archived',
])

const USER_SCOPED_TYPES: ReadonlySet<string> = new Set([
  'late_checkin.decided',
  'excuse.decided',
  'attendance.marked',
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
    case 'lesson.reminder':
      return 'Не забудьте отметиться'
    case 'attendance.marked':
      return 'Староста изменил статус'
    case 'lesson.cancelled':
      return 'Пара отменена'
    case 'lesson.one_off.created':
      return 'Добавлена пара'
    case 'lesson.one_off.cancelled':
      return 'Пара отменена'
    case 'homework.published':
      return 'Новое домашнее задание'
    case 'homework.updated':
      return 'Изменения в домашнем задании'
    case 'late_checkin.requested':
      return 'Запрос опоздалой отметки'
    case 'late_checkin.decided':
      return payload.status === 'approved'
        ? 'Запрос одобрен'
        : 'Запрос отклонён'
    case 'excuse.requested':
      return 'Новый тикет о пропуске'
    case 'excuse.decided':
      return payload.status === 'approved'
        ? 'Уважительная одобрена'
        : 'Уважительная отклонена'
    case 'group.renamed':
      return 'Группа переименована'
    case 'group.archived':
      return 'Группа архивирована'
    default:
      return 'RutTrack'
  }
}

const EXCUSE_TYPE_RU: Record<string, string> = {
  illness: 'Болезнь',
  summons: 'Повестка',
  university_order: 'Приказ',
  exemption: 'Освобождение',
  free_attendance: 'Свобод. посещение',
  other: 'Другое',
}

const ATTENDANCE_STATUS_RU: Record<string, string> = {
  present: 'Присутствует (+)',
  absent: 'Отсутствует (н)',
  excused: 'Уважительная (у)',
  free_attendance: 'Свобод. посещение (сп)',
}

function str(payload: Record<string, unknown>, key: string): string {
  const v = payload[key]
  return typeof v === 'string' ? v : ''
}

function formatShortDate(iso: string): string {
  if (iso.length >= 10 && iso[4] === '-' && iso[7] === '-') {
    return `${iso.slice(8, 10)}.${iso.slice(5, 7)}`
  }
  return iso
}

function formatLessonRef(payload: Record<string, unknown>): string {
  const parts: string[] = []
  const lessonNumber = payload.lesson_number
  if (typeof lessonNumber === 'number') parts.push(`№${lessonNumber}`)
  const startTime = str(payload, 'start_time')
  const endTime = str(payload, 'end_time')
  if (startTime && endTime) {
    parts.push(`${startTime}–${endTime}`)
  } else {
    const date = str(payload, 'lesson_date') || str(payload, 'date')
    if (date) parts.push(formatShortDate(date))
  }
  return parts.join(', ')
}

function addIfPresent(lines: string[], value: string): void {
  if (value.trim()) lines.push(value.trim())
}

function buildHomeworkBody(payload: Record<string, unknown>, lessonRef: string): string {
  const lines: string[] = []
  addIfPresent(lines, str(payload, 'title'))
  addIfPresent(lines, lessonRef)
  addIfPresent(lines, str(payload, 'description'))
  addIfPresent(lines, str(payload, 'link'))
  return lines.length > 0
    ? lines.join('\n')
    : 'Откройте приложение для подробностей'
}

function buildBody(
  type: string,
  payload: Record<string, unknown>,
): string {
  const student = str(payload, 'student_name')
  const excuseType = (() => {
    const code = str(payload, 'excuse_type')
    if (!code) return ''
    return EXCUSE_TYPE_RU[code] ?? code
  })()
  const lessonIds = Array.isArray(payload.lesson_ids) ? payload.lesson_ids : null
  const comment = str(payload, 'comment')
  const lessonRef = formatLessonRef(payload)

  switch (type) {
    case 'lesson.started':
      return lessonRef || 'Время отметиться'
    case 'lesson.reminder':
      return lessonRef || 'Сейчас идёт пара'
    case 'attendance.marked': {
      const status = str(payload, 'status')
      const statusRu = ATTENDANCE_STATUS_RU[status] ?? status
      const subject = str(payload, 'subject_name')
      const number = payload.lesson_number
      const date = str(payload, 'lesson_date')
      const parts: string[] = []
      if (statusRu) parts.push(statusRu)
      if (typeof number === 'number' && subject) {
        parts.push(`№${number} · ${subject}`)
      } else if (typeof number === 'number') {
        parts.push(`№${number}`)
      } else if (subject) {
        parts.push(subject)
      }
      if (date) parts.push(formatShortDate(date))
      return parts.join(' · ')
    }
    case 'lesson.cancelled': {
      const reason = str(payload, 'cancel_reason')
      const base = lessonRef || 'Пара отменена'
      return reason ? `${base} · ${reason}` : base
    }
    case 'lesson.one_off.created': {
      const classroom = str(payload, 'classroom')
      const base = lessonRef || 'Дополнительная пара'
      return classroom ? `${base} · ауд. ${classroom}` : base
    }
    case 'lesson.one_off.cancelled':
      return lessonRef || 'Староста отменил пару'
    case 'homework.published':
    case 'homework.updated': {
      return buildHomeworkBody(payload, lessonRef)
    }
    case 'excuse.requested': {
      const parts: string[] = []
      if (student) parts.push(student)
      if (excuseType) parts.push(excuseType)
      if (lessonIds && lessonIds.length > 0) {
        parts.push(`${lessonIds.length} пар(ы)`)
      }
      if (comment) parts.push(comment)
      return parts.join(' · ')
    }
    case 'late_checkin.requested': {
      const parts: string[] = []
      if (student) parts.push(student)
      if (lessonRef) parts.push(lessonRef)
      return parts.join(' · ')
    }
    case 'excuse.decided':
    case 'late_checkin.decided': {
      const decisionComment = str(payload, 'decision_comment')
      if (decisionComment) return decisionComment
      return payload.status === 'approved'
        ? 'Староста одобрил ваш запрос'
        : 'Староста отклонил ваш запрос'
    }
    case 'group.renamed': {
      const newName = str(payload, 'new_name')
      return newName
        ? `Новое название: ${newName}`
        : 'Откройте приложение для подробностей'
    }
    case 'group.archived':
      return 'Группа выпустилась. Поздравляем!'
    default:
      return ''
  }
}

export function NotificationCenterProvider({ children }: { children: ReactNode }) {
  const { user, accessToken } = useAuth()
  const groupId = user?.groupId ?? null
  const userId = user?.id ?? null
  const isHeadman = !!user?.isHeadman
  const queryClient = useQueryClient()

  const [items, setItems] = useState<NotificationRecord[]>(() => loadFromStorage())
  const itemsRef = useRef(items)
  useEffect(() => {
    itemsRef.current = items
    persist(items)
  }, [items])

  useEffect(() => {
    if (!groupId || !userId || !accessToken) return

    const client = new Client({
      // M03b Группа 6: pre-connect fetch single-use ticket из /auth/ws-ticket.
      webSocketFactory: async () => new SockJS(await buildWsUrl()),
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

          // attendance.marked: показываем студенту ТОЛЬКО ручную правку
          // старостой. self-check-in и auto-absent — служебные события.
          if (envelope.type === 'attendance.marked') {
            if (envelope.payload?.['marked_by'] !== 'headman') return
          }

          if (!STORED_TYPES.has(envelope.type)) return

          // Читаем prefs прямо из localStorage на каждое событие, чтобы
          // не таскать их в deps effect'а — он пересоздаёт STOMP-клиент.
          // В идеале это 3 JSON.parse/минуту, без ощутимой цены.
          const prefs = loadPrefs()
          if (!shouldStoreInHistory(envelope.type, prefs)) return

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

          // M10 G6: backend consumer после persist делает evict Caffeine
          // unread-count; invalidate TanStack query чтобы badge/список
          // перечитали с сервера без 30s staleTime задержки.
          queryClient.invalidateQueries({ queryKey: notificationsQueryKeys.all })

          if (!shouldSuppressBanner(envelope.type, prefs)) {
            showNativeNotification(record)
          }
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
  }, [groupId, userId, isHeadman, accessToken, queryClient])

  const markAllRead = useCallback(() => {
    setItems((prev) =>
      prev.every((i) => i.read) ? prev : prev.map((i) => ({ ...i, read: true })),
    )
    // M10 G6: best-effort синхронизация с backend. Failure в offline —
    // ignore, локальный state уже помечен read, следующий onlineReconnect
    // подтянет server state.
    markAllNotificationsRead()
      .then(() => {
        queryClient.invalidateQueries({ queryKey: notificationsQueryKeys.all })
      })
      .catch(() => {
        // noop — offline / не залогинен. Локальный mark stays.
      })
  }, [queryClient])

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
    // the unread badge. Web Push from the service worker still shows in the OS
    // shade because it is the reliable installed-PWA delivery path.
    return
  }

  const title = buildTitle(record.type, record.payload)
  const body = buildBody(record.type, record.payload)
  try {
    const n = new Notification(title, {
      body,
      icon: PWA_ICON_URL,
      badge: PWA_ICON_URL,
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
    body: buildBody(record.type, record.payload),
  }
}

export function isHeadmanOnlyType(type: string): boolean {
  return HEADMAN_ONLY_TYPES.has(type)
}
