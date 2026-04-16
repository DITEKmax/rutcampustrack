/**
 * Canonical attendance-status UI mapping — used by every component that shows
 * a journal cell, roster row, or segmented control. Keeps "+", "н", "у", "сп"
 * consistent across PWA views. A single source of truth also makes future
 * copy-to-mini-app trivial.
 *
 * "+"  = present (was "б" before 2026-04-16)
 * "н"  = absent
 * "у"  = excused — paired with excuseReason text from the backend cascade
 * "сп" = free_attendance
 * "—"  = cancelled
 */
export type AttendanceSymbolKey =
  | 'present'
  | 'absent'
  | 'excused'
  | 'free_attendance'
  | 'cancelled'

export const STATUS_SYMBOLS: Record<AttendanceSymbolKey, string> = {
  present: '+',
  absent: 'н',
  excused: 'у',
  free_attendance: 'сп',
  cancelled: '—',
}

export const STATUS_ARIA_LABELS: Record<AttendanceSymbolKey, string> = {
  present: 'Присутствовал',
  absent: 'Отсутствовал',
  excused: 'Уважительная причина',
  free_attendance: 'Свободное посещение',
  cancelled: 'Отменена',
}
