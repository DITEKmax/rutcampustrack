export interface GroupMember {
  id: number
  fullName: string
  login: string
  isHeadman?: boolean
  isAssistant?: boolean
}

export interface Teacher {
  id: number
  fullName: string
  login: string
}

export interface Subject {
  id: number
  name: string
  teacherId?: number
  teacherName?: string
}

export type AttendanceStatus = 'present' | 'absent' | 'excused' | 'free_attendance' | 'cancelled'

export interface JournalCell {
  lessonId: number
  studentId: number
  studentName: string
  date: string
  status: AttendanceStatus
}

export interface ResolvedThreshold {
  subjectId: number
  groupId: number
  minPercentage: number
  source: 'global' | 'group' | 'subject'
}

export interface Assistant {
  id: number
  studentId: number
  fullName: string
  permissions: string[]
}

export type AssistantPermission = 'manage_students' | 'manage_subjects' | 'manage_excuses' | 'manage_stats'

export type ExcuseType =
  | 'illness'
  | 'summons'
  | 'university_order'
  | 'exemption'
  | 'free_attendance'
  | 'other'

export type ExcuseTicketStatus = 'submitted' | 'approved' | 'rejected' | 'draft'

/**
 * Full shape of an ExcuseTicket from attendance-service.
 * Matches ExcuseTicketResponse in the backend contract (Phase 59).
 */
export interface ExcuseTicket {
  id: string
  studentId: number
  groupId: number
  studentName: string
  lessonIds: number[]
  excuseType: ExcuseType
  comment: string | null
  status: ExcuseTicketStatus
  decisionBy: number | null
  decisionComment: string | null
  decisionAt: string | null
  createdAt: string
  updatedAt: string
}

export const EXCUSE_TYPE_LABELS: Record<ExcuseType, string> = {
  illness: 'Болезнь',
  summons: 'Повестка',
  university_order: 'Приказ университета',
  exemption: 'Освобождение',
  free_attendance: 'Свободное посещение',
  other: 'Другое',
}

export const EXCUSE_STATUS_LABELS: Record<ExcuseTicketStatus, string> = {
  submitted: 'На рассмотрении',
  approved: 'Одобрено',
  rejected: 'Отклонено',
  draft: 'Черновик',
}

/** @deprecated — use ExcuseTicket. Kept for backward compatibility of old hooks. */
export interface PendingExcuse {
  id: number
  studentName: string
  reason: string
  createdAt: string
}

/**
 * LateCheckinRequest — matches LateCheckinRequestResponse on the backend.
 * status: 'pending' | 'approved' | 'rejected' (lowercase to match Mongo / events).
 */
export type LateCheckinRequestStatus = 'pending' | 'approved' | 'rejected'

export interface LateCheckinRequest {
  id: string
  studentId: number
  groupId: number
  lessonId: number
  studentName: string
  status: LateCheckinRequestStatus
  decisionBy: number | null
  decisionAt: string | null
  createdAt: string
  updatedAt: string
}

/** @deprecated — use LateCheckinRequest. Kept for backward compatibility. */
export interface PendingLateCheckin {
  id: number
  studentName: string
  lessonId: number
  createdAt: string
}

export interface TodayLesson {
  lessonId: number
  subjectName: string
  startsAt: string
  endsAt: string
  room?: string
}
