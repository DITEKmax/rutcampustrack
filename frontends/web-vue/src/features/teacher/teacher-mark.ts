import type { TeacherJournalCell, TeacherRosterEntry } from '../../../../mobile-core/src/features/teacher/teacher-client'
import { journalMark } from '../../../../mobile-core/src/features/teacher/teacher-journal-model'

export type TeacherAttendanceValue = TeacherJournalCell | TeacherRosterEntry

/** Missing future marks are distinct from canonical effective absence after closure. */
export function teacherAttendanceKind(cell: TeacherAttendanceValue | null | undefined) {
  if (!cell || (!cell.recordPresent && !cell.status && !cell.symbol && !cell.autoAbsent)) return null
  return journalMark({ ...cell, lessonId: 'lessonId' in cell ? cell.lessonId : 0 })
}

export function teacherAttendanceLabel(cell: TeacherAttendanceValue | null | undefined): string {
  const kind = teacherAttendanceKind(cell)
  const text = { present: 'Присутствовал', excused: 'Уважительная причина', absent: 'Отсутствовал', none: 'Нет отметки' }[kind ?? 'none']
  return `${text}${cell?.autoAbsent ? ', автоматически' : ''}${cell?.pendingTicket ? ', заявка на рассмотрении' : ''}`
}
