import { describe, expect, it, vi } from 'vitest'
import type {
  TeacherDayLesson,
  TeacherExcuseResponse,
  TeacherLessonResponse,
  TeacherRosterEntry,
} from '../../../../mobile-core/src/features/teacher/teacher-client'
import { verifyTeacherAttachment } from './teacher-evidence'

const source = { lessonId: 11, studentId: 22, ticketId: 'request-a' }
const file = {
  id: 'proof-a',
  fileName: 'Справка.pdf',
  contentType: 'application/pdf',
  size: 12,
  uploadedAt: null,
  expiresAt: '2030-01-01T00:00:00Z',
}
const lesson: TeacherDayLesson = {
  id: 11, assignmentId: 1, groupId: 3, groupName: 'Группа', subjectId: 4,
  subjectName: 'Предмет', semesterId: 5, lessonType: 'lecture', date: '2026-10-24',
  lessonNumber: 1, startsAt: null, endsAt: null, room: null, status: 'CLOSED',
  oneOff: false, moved: false, cancelled: false, cancellationReason: null,
}
const row: TeacherRosterEntry = {
  studentId: 22, displayName: 'Студент', status: 'absent', symbol: 'н',
  source: null, recordPresent: true, pendingTicket: true, autoAbsent: false,
  ticketId: 'request-a', excuseType: null, excuseReason: null, excuseComment: null,
  attachmentId: null, attachmentName: null, attachmentContentType: null, attachmentSize: null,
}
const request: TeacherExcuseResponse = {
  id: 'request-a', kind: 'EXCUSE', status: 'SUBMITTED', studentId: 22, studentName: 'Студент',
  groupId: 3, groupName: 'Группа', excuseType: null, reason: null, comment: null,
  createdAt: null, decisionBy: null, decisionByName: null, decisionAt: null,
  decisionComment: null, lessons: [lesson], attachments: [file], serverNow: '2026-10-24T09:00:00Z',
}
function api(
  projection: TeacherExcuseResponse = request,
  roster: readonly TeacherRosterEntry[] = [row],
  dayLesson: TeacherDayLesson = lesson,
) {
  const data: TeacherLessonResponse = { lesson: dayLesson, roster, serverNow: request.serverNow }
  return { lesson: vi.fn(async () => data), excuse: vi.fn(async () => projection) }
}

describe('exact Web teacher proof authorization', () => {
  it('revalidates both private projections without caching even for a pending ticket', async () => {
    const access = api()
    const { signal } = new AbortController()
    await verifyTeacherAttachment(access, source, file, signal)
    expect(access.lesson).toHaveBeenCalledWith(source.lessonId, { cache: 'no-store', signal })
    expect(access.excuse).toHaveBeenCalledWith(source.ticketId, { cache: 'no-store', signal })
  })

  it.each([
    api({ ...request, lessons: [{ ...lesson, id: 12 }] }),
    api({ ...request, attachments: [{ ...file, expiresAt: '2029-01-01T00:00:00Z' }] }),
    api(request, [{ ...row, studentId: 23 }]),
    api(request, [{ ...row, ticketId: 'request-b' }]),
    api(request, [row], { ...lesson, id: 12 }),
  ])('rejects a still-readable ticket when the exact proof no longer belongs to the selected mark', async access => {
    await expect(verifyTeacherAttachment(access, source, file, new AbortController().signal))
      .rejects.toThrow('Документ больше недоступен для этой отметки.')
  })

  it('rejects a manual proof when a fresh roster no longer exposes the selected mark’s descriptor', async () => {
    const manualSource = { lessonId: source.lessonId, studentId: source.studentId }
    const manualRow = {
      ...row, status: 'excused', symbol: 'у', source: 'NO_TICKET', pendingTicket: false,
      ticketId: null, attachmentId: file.id, attachmentName: file.fileName,
      attachmentContentType: file.contentType, attachmentSize: file.size,
      attachmentExpiresAt: file.expiresAt,
    }
    const access = api(request, [manualRow])
    const { signal } = new AbortController()
    await verifyTeacherAttachment(access, manualSource, file, signal)
    access.lesson.mockResolvedValueOnce({
      lesson, roster: [{ ...manualRow, attachmentId: null }], serverNow: request.serverNow,
    })
    await expect(verifyTeacherAttachment(access, manualSource, file, signal))
      .rejects.toThrow('Документ больше недоступен для этой отметки.')
    expect(access.lesson.mock.calls).toEqual([
      [manualSource.lessonId, { cache: 'no-store', signal }],
      [manualSource.lessonId, { cache: 'no-store', signal }],
    ])
    expect(access.excuse).not.toHaveBeenCalled()
  })
})
