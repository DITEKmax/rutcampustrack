import type {
  TeacherApi,
  TeacherExcuseResponse,
  TeacherRosterEntry,
} from '../../../../mobile-core/src/features/teacher/teacher-client'
import type { TeacherPreviewAttachment } from '../../../../mobile-core/src/features/teacher/teacher-attachment-preview'
import { isTeacherManualExcuse } from '../../../../mobile-core/src/features/teacher/teacher-manual-excuse'

export interface TeacherRequestSource {
  lessonId: number
  studentId: number
  ticketId: string
}

export interface TeacherManualSource {
  lessonId: number
  studentId: number
}

export type TeacherEvidenceSource = TeacherRequestSource | TeacherManualSource

export function matchesTeacherRequest(source: TeacherRequestSource, request: TeacherExcuseResponse) {
  return request.id === source.ticketId && request.studentId === source.studentId &&
    request.lessons.some(lesson => lesson.id === source.lessonId)
}

export function isManualEvidence(entry: TeacherRosterEntry) {
  return !entry.pendingTicket && isTeacherManualExcuse(entry)
}

export function manualAttachment(entry: TeacherRosterEntry): TeacherPreviewAttachment | null {
  if (!isManualEvidence(entry) || !entry.attachmentId) return null
  return {
    id: entry.attachmentId,
    fileName: entry.attachmentName ?? 'Документ',
    contentType: entry.attachmentContentType ?? 'application/octet-stream',
    size: entry.attachmentSize,
    uploadedAt: null,
    expiresAt: entry.attachmentExpiresAt ?? null,
  }
}

export function teacherAttachmentKey(attachment: TeacherPreviewAttachment) {
  return JSON.stringify([
    attachment.id, attachment.fileName, attachment.contentType, attachment.size,
    attachment.uploadedAt, attachment.expiresAt,
  ])
}

/** The ticket download route can also be reached from other lessons on the same ticket. */
export async function verifyTeacherAttachment(
  api: Pick<TeacherApi, 'lesson' | 'excuse'>,
  source: TeacherEvidenceSource,
  attachment: TeacherPreviewAttachment,
  signal: AbortSignal,
) {
  const options = { cache: 'no-store', signal } as const
  const [lesson, request] = await Promise.all([
    api.lesson(source.lessonId, options),
    'ticketId' in source ? api.excuse(source.ticketId, options) : null,
  ])
  const row = lesson.roster.find(entry => entry.studentId === source.studentId)
  let file: TeacherPreviewAttachment | null = null
  if (lesson.lesson.id === source.lessonId && row) {
    if ('ticketId' in source) {
      if (row.ticketId === source.ticketId && request && matchesTeacherRequest(source, request)) {
        file = request.attachments.find(entry => entry.id === attachment.id) ?? null
      }
    } else if (!lesson.lesson.cancelled) {
      file = manualAttachment(row)
    }
  }
  if (!file || teacherAttachmentKey(file) !== teacherAttachmentKey(attachment))
    throw new Error('Документ больше недоступен для этой отметки.')
}
