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
export * from './api/student-client'
export * from './api/types'
export * from './domain/checkin'
export * from './domain/offline-today'
export * from './domain/recoverable-read'
export * from './offline/semester-snapshot'
export * from './test-adapter/fixture-transport'
export * from './features/today/use-today'
export { default as TodayScreen } from './features/today/TodayScreen.vue'
