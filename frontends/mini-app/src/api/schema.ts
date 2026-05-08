/**
 * Mini App type aliases over generated OpenAPI schemas.
 *
 * M19 G2 moves the Mini App away from hand-copied backend DTO interfaces.
 * Feature-local `types.ts` files should re-export aliases from here unless
 * they define a frontend-only view model.
 */

import type { components as AcademicComponents } from './generated/academic.types'
import type { components as AttendanceComponents } from './generated/attendance.types'
import type { components as AuthComponents } from './generated/auth.types'
import type { components as ScheduleComponents } from './generated/schedule.types'

type AcademicSchemas = AcademicComponents['schemas']
type AttendanceSchemas = AttendanceComponents['schemas']
type AuthSchemas = AuthComponents['schemas']
type ScheduleSchemas = ScheduleComponents['schemas']

type Strict<T, K extends keyof T> = Required<Pick<T, K>> & Omit<T, K | '_links'>

// ------------------------- auth-service -----------------------------

export type TokenResponse = Strict<AuthSchemas['TokenResponse'], 'accessToken' | 'expiresIn'>
export type TmaAuthRequest = AuthSchemas['TmaAuthRequest']

/**
 * Frontend-derived claims from JWT access token; not a backend DTO.
 */
export interface AuthUser {
  id: number
  role: string
  groupId?: number
  isHeadman: boolean
}

// ------------------------- schedule-service -------------------------

export type LessonResponse = Strict<
  ScheduleSchemas['EntityModelLessonResponse'],
  | 'id'
  | 'scheduleItemId'
  | 'groupId'
  | 'subjectId'
  | 'date'
  | 'status'
  | 'dayOfWeek'
  | 'lessonNumber'
  | 'startTime'
  | 'endTime'
  | 'weekType'
  | 'room'
  | 'geoBlocked'
  | 'createdAt'
>

export type LessonStatus = NonNullable<LessonResponse['status']>
export type AttendanceStatus = 'present' | 'absent' | 'excused' | 'free_attendance'
export type CheckinRequest = AttendanceSchemas['CheckinRequest']
export type MarkRequest = AttendanceSchemas['MarkRequest']
export type MarkBatchRequest = AttendanceSchemas['MarkBatchRequest']
export type JournalResponse = AttendanceSchemas['EntityModelJournalResponse']
export type JournalStudentRow = AttendanceSchemas['JournalStudentRow']
export type JournalCellDto = AttendanceSchemas['JournalCell']
export type HeadmanWeeklyExportRequest = AttendanceSchemas['HeadmanWeeklyExportRequest']
export type HeadmanWeeklyReportFormat = HeadmanWeeklyExportRequest['format']
export type HeadmanWeeklyWeeksResponse = Omit<
  Strict<AttendanceSchemas['EntityModelHeadmanWeeklyWeeksResponse'], 'weeks'>,
  'semesterId' | 'semesterName' | 'semesterDateFrom' | 'semesterDateTo' | 'weeks'
> & {
  semesterId: number | null
  semesterName: string | null
  semesterDateFrom: string | null
  semesterDateTo: string | null
  weeks: HeadmanWeeklyWeekOption[]
}
export type HeadmanWeeklyWeekOption = Required<
  AttendanceSchemas['HeadmanWeeklyWeekOption']
>
export interface ReportBlobResponse {
  data: Blob
  headers: unknown
}

export type HeadmanAttendanceStatus = AttendanceStatus
export type AttendanceSource =
  | 'student_geo'
  | 'headman'
  | 'auto_scheduler'
  | 'late_checkin'
  | 'headman_excuse'

export type LessonAttendanceEntry = {
  userId: number
  displayName: string
  symbol: string
  status: HeadmanAttendanceStatus
  source: AttendanceSource | null
  excuseReason?: string | null
}

export type LessonAttendance = {
  lessonId: number
  groupId: number
  subjectId: number
  lessonDate: string
  entries: LessonAttendanceEntry[]
}

// ------------------------- academic-service -------------------------

export type SubjectType = NonNullable<AcademicSchemas['CreateSubjectRequest']['type']>
export type CreateSubjectRequest = AcademicSchemas['CreateSubjectRequest']
export type UpdateSubjectRequest = AcademicSchemas['UpdateSubjectRequest']
export type SetThresholdRequest = AcademicSchemas['SetThresholdRequest']
export type ResolvedThreshold = Omit<
  Strict<AcademicSchemas['EntityModelResolvedThresholdResponse'], 'minPercentage'>,
  'level' | 'sourceId'
> & {
  level: string | null
  sourceId: number | null
}
export type ThresholdResponse = Omit<
  Strict<AcademicSchemas['EntityModelThresholdResponse'], 'minPercentage'>,
  'id' | 'groupId' | 'subjectId' | 'updatedAt'
> & {
  id: number | null
  groupId: number | null
  subjectId: number | null
  updatedAt: string | null
}
export type SubjectResponse = Omit<
  Strict<AcademicSchemas['EntityModelSubjectResponse'], 'id' | 'name'>,
  'type' | 'groupId' | 'teacherIds' | 'createdAt'
> & {
  type?: SubjectType
  groupId?: number
  teacherIds?: number[]
  createdAt?: string
}

export type GroupResponse = Strict<
  AcademicSchemas['EntityModelGroupResponse'],
  'id' | 'name'
>

export type UserResponse = Strict<
  AcademicSchemas['EntityModelUserResponse'],
  'id' | 'login' | 'role'
>
export type AssignAssistantRequest = AcademicSchemas['AssignAssistantRequest']
export type AssistantPermission = AssignAssistantRequest['permissions'][number]
export type AssistantResponse = Omit<
  Strict<
    AcademicSchemas['EntityModelAssistantResponse'],
    'id' | 'studentId' | 'groupId' | 'permissions'
  >,
  'studentName' | 'fullName' | 'login' | 'active' | 'assignedAt' | 'revokedAt'
> & {
  studentName: string | null
  fullName: string | null
  login: string | null
  active: boolean
  assignedAt: string | null
  revokedAt: string | null
}

export type SemesterResponse = Strict<
  AcademicSchemas['EntityModelSemesterResponse'],
  'id' | 'name' | 'dateFrom' | 'dateTo' | 'active'
>

export type HomeworkDto = Strict<
  AcademicSchemas['EntityModelHomeworkResponse'],
  | 'id'
  | 'title'
  | 'subjectId'
  | 'groupId'
  | 'semesterId'
  | 'publishedBy'
  | 'completed'
  | 'createdAt'
  | 'lessonDate'
  | 'lessonNumber'
>

/**
 * View model for the current compact Mini App homework page.
 * `subjectName` is resolved client-side from `subjectId`; backend does not
 * return it in HomeworkResponse.
 */
export type HomeworkResponse = Omit<HomeworkDto, 'description' | 'link'> & {
  description: string | null
  link: string | null
  subjectName: string
  dueDate: string | null
}

// ------------------------- attendance-service -----------------------

export type OverallStats = Required<AttendanceSchemas['OverallStats']>
export type SubjectStats = Required<Omit<AttendanceSchemas['SubjectStats'], 'subjectType'>> & {
  subjectType?: string
}
export type StatusBreakdown = Required<AttendanceSchemas['StatusBreakdown']>
export type WeeklyStat = Required<AttendanceSchemas['WeeklyStat']>
export type TopMissedSubject = Required<AttendanceSchemas['TopMissedSubject']>
export type AttendanceRecord = Strict<
  AttendanceSchemas['EntityModelAttendanceRecordEntry'],
  'lessonId' | 'subjectId' | 'lessonDate' | 'lessonNumber' | 'status' | 'symbol' | 'source'
>
export type CreateExcuseRequest = AttendanceSchemas['CreateExcuseRequest']
export type ExcuseType = CreateExcuseRequest['excuseType']
export type ExcuseTicketStatus = 'submitted' | 'approved' | 'rejected' | 'draft'
export type LateCheckinRequestStatus = 'pending' | 'approved' | 'rejected'

export type ExcuseTicket = Omit<
  Strict<
    AttendanceSchemas['EntityModelExcuseTicketResponse'],
    'id' | 'studentId' | 'groupId' | 'studentName' | 'lessonIds' | 'excuseType' | 'createdAt' | 'updatedAt'
  >,
  'comment' | 'decisionBy' | 'decisionComment' | 'decisionAt' | 'status'
> & {
  status: ExcuseTicketStatus
  comment: string | null
  decisionBy: number | null
  decisionComment: string | null
  decisionAt: string | null
}

export type LateCheckinRequest = Omit<
  Strict<
    AttendanceSchemas['EntityModelLateCheckinRequestResponse'],
    'id' | 'studentId' | 'groupId' | 'lessonId' | 'studentName' | 'createdAt' | 'updatedAt'
  >,
  'decisionBy' | 'decisionAt' | 'status'
> & {
  status: LateCheckinRequestStatus
  decisionBy: number | null
  decisionAt: string | null
}

export type StudentStatsResponse = {
  subjects: SubjectStats[]
  overall: OverallStats
}

export type StudentDashboard = {
  overall: OverallStats
  breakdown: StatusBreakdown
  weekly: WeeklyStat[]
  topMissed: TopMissedSubject[]
}
