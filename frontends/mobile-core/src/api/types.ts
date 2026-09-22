import type { components } from './generated/mobile-bff'

export type StudentSession = components['schemas']['StudentSession']
export type StudentToday = components['schemas']['StudentToday']
export type TodayLesson = components['schemas']['TodayLesson']
export type LessonSchedule = components['schemas']['LessonSchedule']
export type StudentSemesterSchedule = components['schemas']['StudentSemesterSchedule']
export type StudentHomework = components['schemas']['StudentHomework']
export type StudentHomeworkItem = components['schemas']['StudentHomeworkItem']
export type StudentHomeworkCompletionCommand = components['schemas']['StudentHomeworkCompletionCommand']
export type StudentHomeworkCompletion = components['schemas']['StudentHomeworkCompletion']
export type StudentCheckinAck = components['schemas']['StudentCheckinAck']
export type StudentCheckinCommand = components['schemas']['StudentCheckinCommand']
export type StudentExcuseRequest = components['schemas']['StudentExcuseRequest']
export type StudentLateCheckinRequest = components['schemas']['StudentLateCheckinRequest']
export type StudentRequestAttachment = components['schemas']['StudentRequestAttachment']
export type StudentRequestBudget = components['schemas']['StudentRequestBudget']
export type StudentRequestDecision = components['schemas']['StudentRequestDecision']
export type StudentRequestDetail = components['schemas']['StudentRequestDetail']
export type StudentRequestFileLimits = components['schemas']['StudentRequestFileLimits']
export type StudentRequestLesson = components['schemas']['StudentRequestLesson']
export type StudentRequestLessonOption = components['schemas']['StudentRequestLessonOption']
export type StudentRequestOptions = components['schemas']['StudentRequestOptions']
export type StudentRequestPage = components['schemas']['StudentRequestPage']
export type StudentRequestPendingRef = components['schemas']['StudentRequestPendingRef']
export type StudentRequestReasonOption = components['schemas']['StudentRequestReasonOption']
export type StudentRequestSummary = components['schemas']['StudentRequestSummary']
export type MobileProblemDetails = components['schemas']['MobileProblemDetails']
export type StudentRequestBucket = 'OPEN' | 'ARCHIVE'

export type MapFormat = 'png' | 'svg'
export type MapFormatState = 'absent' | 'processing' | 'ready' | 'failed'

export interface MapFormatSlot {
  readonly format: MapFormat
  readonly state: MapFormatState
  readonly contentType: 'image/png' | 'image/svg+xml'
  readonly id: string | null
  readonly bytes: number
  readonly sha256: string | null
  readonly width: number | null
  readonly height: number | null
  readonly viewBox: readonly number[] | null
}

export interface MapPlan {
  readonly buildingId: string
  readonly floorId: string
  readonly version: string
  readonly label: string
  readonly png: MapFormatSlot
  readonly svg: MapFormatSlot
}

export interface MapFloor {
  readonly id: string
  readonly label: string
  readonly plan: MapPlan | null
}

export interface MapBuilding {
  readonly id: string
  readonly label: string
  readonly floors: readonly MapFloor[]
}

export interface MapManifest {
  readonly schemaVersion: number
  readonly validationPolicyVersion: number
  readonly revision: string
  readonly buildings: readonly MapBuilding[]
}

export interface AdminMapPlan extends MapPlan {
  readonly catalogRevision: string
}

export interface AdminMapFloorResponse {
  readonly id: string
  readonly buildingId: string
  readonly code: string
  readonly label: string
  readonly currentPlan: AdminMapPlan | null
  readonly openCount: number
  readonly openCountPeriod: 'all_time'
}

export interface AdminMapBuildingResponse {
  readonly id: string
  readonly code: string
  readonly label: string
  readonly floors: readonly AdminMapFloorResponse[]
}

export type UnavailableReason = Extract<
  components['schemas']['GeoInput'],
  { kind: 'UNAVAILABLE' }
>['reason']

/**
 * The student read projections are intentionally hand-written until the
 * additive BFF contract is merged into the generated OpenAPI document.  They
 * mirror the frozen mobile contract and keep generated sources owned by the
 * backend integration writer.
 */
export type StudentLessonType = 'LECTURE' | 'PRACTICE' | 'LAB'
export type StudentAttendanceLessonStatus =
  | 'PRESENT'
  | 'ABSENT'
  | 'EXCUSED'
  | 'ACTIVE'
  | 'FUTURE'
  | 'NO_DATA'
  | 'CANCELLED'
export type StudentAttendanceHistoryStatus = Exclude<StudentAttendanceLessonStatus, 'ACTIVE' | 'CANCELLED'>
export type StudentProjectionState = 'PAST' | 'CURRENT' | 'FUTURE'
export type StudentSeriesState = 'DATA' | 'NO_DATA' | 'FUTURE'

export interface StudentAttendanceMetricValue {
  readonly count: number
  readonly percent: number | null
}

export interface StudentAttendanceMetricSet {
  readonly present: StudentAttendanceMetricValue
  readonly presentOrExcused: StudentAttendanceMetricValue
  readonly excused: StudentAttendanceMetricValue
  readonly absent: StudentAttendanceMetricValue
  readonly held: number
  readonly planned: number
}

export interface StudentAttendanceRequestOption {
  readonly id: string
  readonly kind: 'EXCUSE' | 'LATE_CHECKIN'
  readonly label: string
  readonly enabled: boolean
  readonly reason?: string | null
}

export interface StudentAttendanceLesson {
  readonly id: string
  readonly date: string
  readonly number: string
  readonly subject: { readonly id: string; readonly name: string }
  readonly type: StudentLessonType
  readonly schedule: {
    readonly startsAt: string
    readonly endsAt: string
    readonly room: string | null
  }
  readonly status: StudentAttendanceLessonStatus
  readonly requestOptions: readonly StudentAttendanceRequestOption[]
}

export interface StudentAttendanceDay {
  readonly date: string
  readonly weekday: string
  readonly dayNumber: string
  readonly state: StudentProjectionState
  readonly lessons: readonly StudentAttendanceLesson[]
}

export interface StudentAttendanceHistorySegment {
  readonly id: string
  readonly status: StudentAttendanceHistoryStatus
}

export interface StudentAttendanceTypeCard {
  readonly type: StudentLessonType
  readonly metrics: StudentAttendanceMetricSet
  readonly history: readonly StudentAttendanceHistorySegment[]
}

export interface StudentAttendanceSubject {
  readonly id: string
  readonly name: string
  readonly typeCards: readonly StudentAttendanceTypeCard[]
}

export interface StudentAttendanceSeriesPoint {
  readonly id: string
  readonly label: string
  readonly dateFrom: string
  readonly dateTo: string
  readonly state: StudentSeriesState
  readonly metrics: StudentAttendanceMetricSet
}

export interface StudentAttendanceResponse {
  readonly semester: {
    readonly id: string
    readonly name: string
    readonly dateFrom: string
    readonly dateTo: string
  }
  readonly serverNow: string
  readonly terminalReadOnly: boolean
  readonly metrics: StudentAttendanceMetricSet
  readonly days: readonly StudentAttendanceDay[]
  readonly subjects: readonly StudentAttendanceSubject[]
  readonly graph: {
    readonly days: readonly StudentAttendanceSeriesPoint[]
    readonly weeks: readonly StudentAttendanceSeriesPoint[]
  }
}

export interface StudentStatisticsOwnRank {
  readonly position: number | null
  readonly participantCount: number
  readonly available: boolean
}

export interface StudentStatisticsSeriesPoint {
  readonly id: string
  readonly label: string
  readonly dateFrom: string
  readonly dateTo: string
  readonly state: StudentSeriesState
  readonly metrics: StudentAttendanceMetricSet
}

export interface StudentStatisticsSubjectSummary {
  readonly id: string
  readonly name: string
  readonly metrics: StudentAttendanceMetricSet
}

export interface StudentStatisticsOverviewResponse {
  readonly metrics: StudentAttendanceMetricSet
  readonly ownRank: StudentStatisticsOwnRank
  readonly semesterSeries: readonly StudentStatisticsSeriesPoint[]
  readonly subjects: readonly StudentStatisticsSubjectSummary[]
}

export interface StudentStatisticsTypeCard {
  readonly type: StudentLessonType
  readonly metrics: StudentAttendanceMetricSet
  readonly history: readonly StudentAttendanceHistorySegment[]
}

export interface StudentStatisticsSubjectDetailResponse {
  readonly subjectId: number | string
  readonly name: string
  readonly availableTypes: readonly StudentLessonType[]
  readonly selectedTypes: readonly StudentLessonType[]
  readonly selectedAggregate: StudentAttendanceMetricSet
  readonly series: readonly StudentStatisticsSeriesPoint[]
  readonly typeCards: readonly StudentStatisticsTypeCard[]
}
