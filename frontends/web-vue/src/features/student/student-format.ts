export {
  lessonTypeLabel,
  formatLessonDate,
  formatRequestDate,
  requestTime,
} from '../../../../mobile-core/src/features/requests/state'
export {
  monday,
  shiftDate,
  shortDate,
  serverDate,
} from '../../../../mobile-core/src/features/attendance/attendance-periods'
import type { StudentAttendanceLessonStatus } from '../../../../mobile-core/src/api/types'
export function studentMark(status: StudentAttendanceLessonStatus | null | undefined) {
  switch (status) {
    case 'PRESENT':
      return { symbol: '+', label: 'Присутствовал', tone: 'present' }
    case 'EXCUSED':
      return { symbol: 'у', label: 'Уважительная причина', tone: 'excused' }
    case 'ABSENT':
      return { symbol: 'н', label: 'Отсутствовал', tone: 'absent' }
    case 'CANCELLED':
      return { symbol: '×', label: 'Пара отменена', tone: 'none' }
    case 'ACTIVE':
      return { symbol: '—', label: 'Пара идёт', tone: 'none' }
    case 'FUTURE':
      return { symbol: '—', label: 'Будущая пара', tone: 'none' }
    default:
      return { symbol: '—', label: 'Нет отметки', tone: 'none' }
  }
}
export function percent(value: number | null | undefined): string {
  return value === null || value === undefined
    ? '—'
    : `${new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(value)} %`
}
