import type {
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
