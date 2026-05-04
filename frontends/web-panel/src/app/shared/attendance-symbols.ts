/**
 * Canonical attendance-status UI mapping for web-panel (mirrors PWA).
 *
 * "+"  = present
 * "н"  = absent
 * "у"  = excused — paired with excuseReason from backend
 * "сп" = free_attendance
 * "—"  = cancelled
 *
 * The backend also returns a `symbol` field on each cell, but we override it on
 * the client so a single source of truth controls what the user sees.
 */
export type AttendanceSymbolKey =
  | 'present'
  | 'absent'
  | 'excused'
  | 'free_attendance'
  | 'cancelled';

export const ATTENDANCE_SYMBOLS: Record<AttendanceSymbolKey, string> = {
  present: '+',
  absent: 'н',
  excused: 'у',
  free_attendance: 'сп',
  cancelled: '—',
};

export const ATTENDANCE_ARIA: Record<AttendanceSymbolKey, string> = {
  present: 'Присутствовал',
  absent: 'Отсутствовал',
  excused: 'Уважительная причина',
  free_attendance: 'Свободное посещение',
  cancelled: 'Отменена',
};

export function symbolFor(status: string): string {
  return (ATTENDANCE_SYMBOLS as Record<string, string>)[status] ?? '?';
}
