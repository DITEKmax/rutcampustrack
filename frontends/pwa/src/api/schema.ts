/**
 * Shared type aliases over generated OpenAPI schemas.
 *
 * M07 G3b (D3): pragmatic types-only migration — axios runtime остаётся,
 * frontend импортирует `components['schemas'][...]` как type вместо
 * ручных interface-копий. Здесь сконцентрированы узкие aliases, которые
 * переопределяют HATEOAS optionality (openapi-typescript помечает все
 * поля `?:` из-за отсутствия `@NotNull` на Spring response DTO), чтобы
 * consumer-код не заставлять писать `lesson.id!` на каждом обращении.
 *
 * Инварианты:
 * - `_links` HATEOAS игнорируется (stripped в fetcher через `{ _links, ...rest }`).
 * - Типы описывают **runtime-контракт**, который backend действительно
 *   возвращает (все поля кроме помеченных `?:` в этом файле — всегда
 *   присутствуют).
 * - При добавлении новых endpoint'ов — расширяй этот файл, не создавай
 *   ручные interfaces в feature-папках.
 */

import type { components as AcademicComponents } from './generated/academic.types'
import type { components as AttendanceComponents } from './generated/attendance.types'
import type { components as AuthComponents } from './generated/auth.types'
import type { components as ScheduleComponents } from './generated/schedule.types'

type AcademicSchemas = AcademicComponents['schemas']
type AttendanceSchemas = AttendanceComponents['schemas']
type AuthSchemas = AuthComponents['schemas']
type ScheduleSchemas = ScheduleComponents['schemas']

/**
 * Make all listed keys required, drop `_links`. Используется для
 * сужения HATEOAS-optionality до runtime-контракта.
 */
type Strict<T, K extends keyof T> = Required<Pick<T, K>> & Omit<T, K | '_links'>

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
export type WeekType = NonNullable<LessonResponse['weekType']>

export type OneOffLessonResponse = Strict<
  ScheduleSchemas['EntityModelOneOffLessonResponse'],
  'id' | 'groupId' | 'subjectId' | 'date' | 'lessonNumber'
>

// ------------------------- academic-service -------------------------

export type SubjectResponse = Strict<
  AcademicSchemas['EntityModelSubjectResponse'],
  'id' | 'name' | 'type' | 'groupId'
>

export type GroupResponse = Strict<
  AcademicSchemas['EntityModelGroupResponse'],
  'id' | 'name'
>

export type UserResponse = Strict<
  AcademicSchemas['EntityModelUserResponse'],
  'id' | 'login' | 'role'
>

export type SemesterResponse = Strict<
  AcademicSchemas['EntityModelSemesterResponse'],
  'id' | 'name' | 'dateFrom' | 'dateTo' | 'active'
>

// ------------------------- attendance-service -----------------------

/**
 * Runtime-значения AttendanceStatus как они приходят от backend'а
 * (lowercase). Backend формально имеет 4 записываемых статуса, но
 * journal/segmented-control оперируют также агрегатом 'cancelled'
 * (пара отменена → всем cancelled в журнале). Union держит все 5,
 * чтобы UI-код не разветвлялся на два типа.
 */
export type AttendanceStatus =
  | 'present'
  | 'absent'
  | 'excused'
  | 'free_attendance'
  | 'cancelled'

/** Alias для обратной совместимости — до G3b это был отдельный union. */
export type HeadmanAttendanceStatus = AttendanceStatus

export type AttendanceSource =
  | 'student_geo'
  | 'headman'
  | 'auto_scheduler'
  | 'late_checkin'
  | 'headman_excuse'

/** Один StudentAttendanceEntry для headman-отметки в LessonSheet. */
export type LessonAttendanceEntry = {
  userId: number
  displayName: string
  symbol: string
  /** Narrow status из backend string → union (backend lowercase). */
  status: HeadmanAttendanceStatus
  /** null если нет документа attendance пока. */
  source: AttendanceSource | null
  /** Только для excused / free_attendance из approved excuse ticket. */
  excuseReason?: string | null
}

export type LessonAttendance = {
  lessonId: number
  groupId: number
  subjectId: number
  lessonDate: string
  entries: LessonAttendanceEntry[]
}

export type AttendanceRecord = Strict<
  AttendanceSchemas['EntityModelAttendanceRecordEntry'],
  'lessonId' | 'subjectId' | 'lessonDate' | 'lessonNumber' | 'status' | 'symbol' | 'source'
>

export type CreateExcuseRequest = AttendanceSchemas['CreateExcuseRequest']
export type ExcuseType = CreateExcuseRequest['excuseType']

export type OverallStats = Required<AttendanceSchemas['OverallStats']>
export type StatusBreakdown = Required<AttendanceSchemas['StatusBreakdown']>
export type WeeklyStat = Required<AttendanceSchemas['WeeklyStat']>
export type TopMissedSubject = Required<AttendanceSchemas['TopMissedSubject']>

/**
 * Strip HATEOAS-обёртку для dashboard — backend возвращает
 * StudentDashboardResponse, fetcher выкидывает `_links` в runtime.
 * Required-вложенные типы используют собственные Required-aliases выше.
 */
export type StudentDashboard = {
  overall: OverallStats
  breakdown: StatusBreakdown
  weekly: WeeklyStat[]
  topMissed: TopMissedSubject[]
}

/**
 * ExcuseTicket: backend отдаёт excuseType UPPERCASE (Java enum). Ручные
 * types.ts в PWA раньше держали lowercase — это runtime рассогласование
 * (EXCUSE_TYPE_LABELS lookup никогда не срабатывал). G3b фиксит тип,
 * fix лежит на G4/G6 потребителях (tsc покажет breakage).
 *
 * Nullable поля (decisionBy, decisionComment, decisionAt, comment)
 * приходят из backend как `null` до decision; generated types
 * помечают их `string | undefined` из-за отсутствия `nullable: true`
 * в @Schema. Override'им явно.
 */
export type ExcuseTicketStatus = 'submitted' | 'approved' | 'rejected' | 'draft'

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

export type LateCheckinRequestStatus = 'pending' | 'approved' | 'rejected'

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

// ------------------------- academic: homework -----------------------

/**
 * Backend @Schema пока не помечает `description`/`link` как nullable, но
 * runtime отдаёт их как `null` для homework без описания. Override'им
 * типы явно до OpenAPI polish (M11).
 */
export type HomeworkResponse = Omit<
  Strict<
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
  >,
  'description' | 'link'
> & {
  description: string | null
  link: string | null
}

export type CreateHomeworkRequest = Omit<
  AcademicSchemas['CreateHomeworkRequest'],
  'description' | 'link'
> & {
  description: string | null
  link: string | null
}

export type UpdateHomeworkRequest = Omit<
  AcademicSchemas['UpdateHomeworkRequest'],
  'description' | 'link'
> & {
  description: string | null
  link: string | null
}

// ------------------------- auth-service -----------------------------

export type LoginRequest = Required<AuthSchemas['LoginRequest']>
export type TokenResponse = Strict<
  AuthSchemas['TokenResponse'],
  'accessToken' | 'expiresIn'
>
export type WsTicketResponse = Required<AuthSchemas['WsTicketResponse']>

// ------------------------- headman DTOs -----------------------------

export type AssistantPermission =
  | 'manage_students'
  | 'manage_subjects'
  | 'manage_excuses'
  | 'manage_stats'
