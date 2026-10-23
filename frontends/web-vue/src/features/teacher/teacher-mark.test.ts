import { describe, expect, it } from 'vitest'
import type { TeacherJournalCell } from '../../../../mobile-core/src/features/teacher/teacher-client'
import { teacherAttendanceKind, teacherAttendanceLabel } from './teacher-mark'

const missing: TeacherJournalCell = { lessonId: 1, status: null, symbol: null, recordPresent: false, autoAbsent: false, pendingTicket: false, source: null, ticketId: null, excuseType: null, excuseReason: null }
describe('teacher attendance read semantics', () => {
  it('keeps an unmarked future lesson distinct from an effective absence without a stored record', () => {
    expect(teacherAttendanceKind(missing)).toBeNull()
    expect(teacherAttendanceKind(undefined)).toBeNull()
    expect(teacherAttendanceKind({ ...missing, status: 'absent', symbol: 'н' })).toBe('absent')
    expect(teacherAttendanceKind({ ...missing, autoAbsent: true })).toBe('absent')
  })
  it('includes the mark and pending request in the accessible action name', () => {
    const cell = { ...missing, recordPresent: true, status: 'excused', symbol: 'у', pendingTicket: true }
    expect(teacherAttendanceKind(cell)).toBe('excused')
    expect(teacherAttendanceLabel(cell)).toContain('Уважительная причина')
    expect(teacherAttendanceLabel(cell)).toContain('заявка на рассмотрении')
  })
})
