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
export type UnavailableReason = Extract<
  components['schemas']['GeoInput'],
  { kind: 'UNAVAILABLE' }
>['reason']
