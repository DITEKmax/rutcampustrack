import type {
  AssistantPermission,
  AssistantResponse,
  AttendanceStatus,
  ExcuseTicket,
  ExcuseTicketStatus,
  ExcuseType,
  HeadmanWeeklyExportRequest,
  HeadmanWeeklyReportFormat,
  HeadmanWeeklyWeekOption,
  HeadmanWeeklyWeeksResponse,
  LateCheckinRequest,
  LateCheckinRequestStatus,
  ReportBlobResponse,
  SubjectResponse,
  SubjectType,
  UserResponse,
} from '@/api/schema'

export interface GroupMember {
  id: number
  fullName: string
  login: string
  isHeadman: boolean
  isAssistant: boolean
}

export type Teacher = UserResponse
export type Subject = Omit<SubjectResponse, 'type' | 'groupId' | 'teacherIds' | 'createdAt'> & {
  type: SubjectType
  groupId: number | null
  teacherIds: number[]
  createdAt: string | null
}
export type {
  AssistantPermission,
  AssistantResponse,
  AttendanceStatus,
  ExcuseTicket,
  ExcuseTicketStatus,
  ExcuseType,
  HeadmanWeeklyExportRequest,
  HeadmanWeeklyReportFormat,
  HeadmanWeeklyWeekOption,
  HeadmanWeeklyWeeksResponse,
  LateCheckinRequest,
  LateCheckinRequestStatus,
  ReportBlobResponse,
  SubjectType,
}

export interface Assistant {
  id: number
  studentId: number
  groupId: number
  fullName: string
  login: string
  permissions: AssistantPermission[]
  active: boolean
}

export interface TodayLesson {
  lessonId: number
  subjectName: string
  startsAt: string
  endsAt: string
  room?: string
}

export interface PendingExcuse {
  id: string | number
  studentName: string
}

export interface PendingLateCheckin {
  id: string | number
  studentName: string
  lessonId?: number
}

export type JournalAttendanceStatus = AttendanceStatus | 'cancelled'

export interface JournalCell {
  lessonId: number
  studentId: number
  studentName: string
  date: string
  lessonNumber: number | null
  status: JournalAttendanceStatus
  symbol: string | null
  excuseReason: string | null
}

export const EXCUSE_TYPE_LABELS: Record<ExcuseType, string> = {
  ILLNESS: 'Болезнь',
  SUMMONS: 'Повестка',
  UNIVERSITY_ORDER: 'Приказ вуза',
  EXEMPTION: 'Освобождение',
  FREE_ATTENDANCE: 'Свободное посещение',
  OTHER: 'Другое',
}

export const EXCUSE_STATUS_LABELS: Record<ExcuseTicketStatus, string> = {
  draft: 'Черновик',
  submitted: 'На проверке',
  approved: 'Одобрено',
  rejected: 'Отклонено',
}

export const LATE_CHECKIN_STATUS_LABELS: Record<LateCheckinRequestStatus, string> = {
  pending: 'На проверке',
  approved: 'Одобрено',
  rejected: 'Отклонено',
}

export const ASSISTANT_PERMISSION_LABELS: Record<AssistantPermission, string> = {
  MARK_ATTENDANCE: 'Отмечать посещаемость',
  MANAGE_EXCUSES: 'Решать пропуски',
  MANAGE_HOMEWORK: 'Управлять ДЗ',
  CANCEL_LESSONS: 'Отменять пары',
  VIEW_STATS: 'Смотреть статистику',
}

export const ASSISTANT_PERMISSION_OPTIONS = Object.keys(
  ASSISTANT_PERMISSION_LABELS,
) as AssistantPermission[]

export const SUBJECT_TYPE_LABELS: Record<SubjectType, string> = {
  LECTURE: 'Лекция',
  PRACTICE: 'Практика',
  LAB: 'Лабораторная',
}

export const SUBJECT_TYPE_OPTIONS = Object.keys(SUBJECT_TYPE_LABELS) as SubjectType[]

export const ATTENDANCE_STATUS_LABELS: Record<AttendanceStatus, string> = {
  present: 'Присутствует',
  absent: 'Отсутствует',
  excused: 'Уважительная',
  free_attendance: 'Свободное',
}

export const ATTENDANCE_STATUS_SYMBOLS: Record<AttendanceStatus, string> = {
  present: '+',
  absent: 'н',
  excused: 'у',
  free_attendance: 'сп',
}

export const JOURNAL_STATUS_SYMBOLS: Record<JournalAttendanceStatus, string> = {
  ...ATTENDANCE_STATUS_SYMBOLS,
  cancelled: 'отм',
}

export const ATTENDANCE_STATUS_OPTIONS = Object.keys(
  ATTENDANCE_STATUS_LABELS,
) as AttendanceStatus[]

export function toGroupMember(user: UserResponse): GroupMember {
  const fallbackName = [user.lastName, user.firstName, user.middleName]
    .filter(Boolean)
    .join(' ')
    .trim()

  return {
    id: user.id,
    fullName: user.fullName || fallbackName || user.login,
    login: user.login,
    isHeadman: !!user.headman,
    isAssistant: !!(user as { assistant?: boolean }).assistant,
  }
}
