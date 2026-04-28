import { useCallback, useEffect, useState } from 'react'

/**
 * Категории уведомлений, по которым пользователь может независимо включать
 * или выключать доставку. Категория выводится из event_type (см. CATEGORY_MAP).
 */
export type NotificationCategory =
  | 'lessons'
  | 'reminders'
  | 'homework'
  | 'tickets'
  | 'schedule'
  | 'group'

export const CATEGORY_LABELS: Record<NotificationCategory, string> = {
  lessons: 'Пары (начало, отмена)',
  reminders: 'Напоминания об отметке',
  homework: 'Домашние задания',
  tickets: 'Тикеты (у.п., опоздания)',
  schedule: 'Изменения расписания',
  group: 'Группа (переименование, архив)',
}

const CATEGORY_MAP: Record<string, NotificationCategory> = {
  'lesson.started': 'lessons',
  'lesson.cancelled': 'lessons',
  'lesson.reminder': 'reminders',
  'lesson.one_off.created': 'schedule',
  'lesson.one_off.cancelled': 'schedule',
  'homework.published': 'homework',
  'homework.updated': 'homework',
  'excuse.requested': 'tickets',
  'excuse.decided': 'tickets',
  'late_checkin.requested': 'tickets',
  'late_checkin.decided': 'tickets',
  'group.renamed': 'group',
  'group.archived': 'group',
}

export function categoryOf(eventType: string): NotificationCategory | null {
  return CATEGORY_MAP[eventType] ?? null
}

export interface QuietHours {
  /** Часы-минуты в формате HH:mm (локальное время устройства). */
  from: string
  to: string
}

export interface NotificationPrefs {
  categories: Record<NotificationCategory, boolean>
  /** Тихий режим: в этом окне подавляем native Notification, но событие
   * по-прежнему попадает в историю с пометкой read=false. */
  quietHours: QuietHours | null
}

export const DEFAULT_PREFS: NotificationPrefs = {
  categories: {
    lessons: true,
    reminders: true,
    homework: true,
    tickets: true,
    schedule: true,
    group: true,
  },
  quietHours: null,
}

const STORAGE_KEY = 'rct.pwa.notif-prefs.v1'

export function loadPrefs(): NotificationPrefs {
  try {
    if (typeof localStorage === 'undefined') return DEFAULT_PREFS
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return DEFAULT_PREFS
    const parsed = JSON.parse(raw) as Partial<NotificationPrefs>
    return {
      categories: { ...DEFAULT_PREFS.categories, ...(parsed.categories ?? {}) },
      quietHours: parsed.quietHours ?? null,
    }
  } catch {
    return DEFAULT_PREFS
  }
}

function savePrefs(prefs: NotificationPrefs): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs))
    // Оповещаем другие вкладки / слушателей внутри того же документа
    // (storage событие выстреливает только для других вкладок).
    window.dispatchEvent(new CustomEvent('rct:notif-prefs-changed'))
  } catch {
    // quota — лучше пропустить, чем упасть
  }
}

/**
 * Проверяет, активно ли окно тихого режима *сейчас*. Поддерживает переход
 * через полночь (например, 22:00 → 07:00).
 */
export function isQuietNow(qh: QuietHours | null, now = new Date()): boolean {
  if (!qh) return false
  const toMinutes = (hhmm: string): number => {
    const [h, m] = hhmm.split(':').map((x) => Number.parseInt(x, 10))
    if (Number.isNaN(h) || Number.isNaN(m)) return -1
    return h * 60 + m
  }
  const from = toMinutes(qh.from)
  const to = toMinutes(qh.to)
  if (from < 0 || to < 0 || from === to) return false
  const current = now.getHours() * 60 + now.getMinutes()
  if (from < to) return current >= from && current < to
  // Переход через полночь: окно [from..24:00) ∪ [00:00..to)
  return current >= from || current < to
}

export function shouldSuppressBanner(
  eventType: string,
  prefs: NotificationPrefs,
  now = new Date(),
): boolean {
  const cat = categoryOf(eventType)
  if (cat && !prefs.categories[cat]) return true
  if (isQuietNow(prefs.quietHours, now)) return true
  return false
}

export function shouldStoreInHistory(
  eventType: string,
  prefs: NotificationPrefs,
): boolean {
  const cat = categoryOf(eventType)
  if (cat && !prefs.categories[cat]) return false
  return true
}

/**
 * React hook над localStorage-настройками. Подписывается на события изменения
 * (и storage-события из других вкладок), чтобы все экраны оставались в синке.
 */
export function useNotificationPrefs(): [
  NotificationPrefs,
  (next: NotificationPrefs) => void,
] {
  const [prefs, setPrefs] = useState<NotificationPrefs>(() => loadPrefs())

  useEffect(() => {
    const reload = () => setPrefs(loadPrefs())
    window.addEventListener('rct:notif-prefs-changed', reload)
    window.addEventListener('storage', reload)
    return () => {
      window.removeEventListener('rct:notif-prefs-changed', reload)
      window.removeEventListener('storage', reload)
    }
  }, [])

  const update = useCallback((next: NotificationPrefs) => {
    setPrefs(next)
    savePrefs(next)
  }, [])

  return [prefs, update]
}
