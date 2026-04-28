/**
 * Headman feature shared types.
 *
 * M07 G3b (D3): DTO-типы (ExcuseTicket, LateCheckinRequest, ExcuseType,
 * ExcuseTicketStatus, LateCheckinRequestStatus, AssistantPermission) —
 * re-export из `@/api/schema` поверх generated OpenAPI types. Frontend-
 * aggregate типы (GroupMember, Subject, Teacher, JournalCell, ...) —
 * остаются ручными, т.к. это UI-shape'ы, не прямые backend DTO.
 */

export type {
  AttendanceStatus,
  HeadmanAttendanceStatus,
  ExcuseTicket,
  ExcuseTicketStatus,
  ExcuseType,
  LateCheckinRequest,
  LateCheckinRequestStatus,
  AssistantPermission,
} from '@/api/schema'

import type { ExcuseType, ExcuseTicketStatus } from '@/api/schema'

// ─── Frontend-aggregate UI shapes ────────────────────────────────────────────

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

export type SubjectType = 'LECTURE' | 'PRACTICE' | 'LAB'

export interface Subject {
  id: number
  name: string
  type?: SubjectType
  teacherId?: number
  teacherName?: string
}

export const SUBJECT_TYPE_LABELS: Record<SubjectType, string> = {
  LECTURE: 'Лекция',
  PRACTICE: 'Практика',
  LAB: 'Лабораторная',
}

export interface JournalCell {
  lessonId: number
  studentId: number
  studentName: string
  date: string
  status: import('@/api/schema').AttendanceStatus
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

export const EXCUSE_TYPE_LABELS: Record<ExcuseType, string> = {
  ILLNESS: 'Болезнь',
  SUMMONS: 'Повестка',
  UNIVERSITY_ORDER: 'Приказ университета',
  EXEMPTION: 'Освобождение',
  FREE_ATTENDANCE: 'Свободное посещение',
  OTHER: 'Другое',
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
