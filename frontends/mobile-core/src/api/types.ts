import type { components } from './generated/mobile-bff'

export type StudentSession = components['schemas']['StudentSession']
export type StudentToday = components['schemas']['StudentToday']
export type TodayLesson = components['schemas']['TodayLesson']
export type LessonSchedule = components['schemas']['LessonSchedule']
export type StudentSemesterSchedule = components['schemas']['StudentSemesterSchedule']
export type StudentCheckinAck = components['schemas']['StudentCheckinAck']
export type StudentCheckinCommand = components['schemas']['StudentCheckinCommand']
export type MobileProblemDetails = components['schemas']['MobileProblemDetails']
export type UnavailableReason = Extract<
  components['schemas']['GeoInput'],
  { kind: 'UNAVAILABLE' }
>['reason']
