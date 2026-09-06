import type { components, operations, paths } from './api/generated/mobile-bff'

export type { components, operations, paths }

export type StudentSession = components['schemas']['StudentSession']
export type StudentToday = components['schemas']['StudentToday']
export type StudentSemesterSchedule =
  components['schemas']['StudentSemesterSchedule']
export type StudentCheckinCommand =
  components['schemas']['StudentCheckinCommand']
export type StudentCheckinAck = components['schemas']['StudentCheckinAck']
export type MobileProblemDetails =
  components['schemas']['MobileProblemDetails']
