import type { components as AttendanceComponents } from '@/api/generated/attendance.types'

/** POST /attendance/checkin body. Re-export generated (M07 G3b, D3). */
export type CheckinRequest = Required<
  AttendanceComponents['schemas']['CheckinRequest']
>

/**
 * STOMP event payload `/topic/attendance/marked`. Snake_case — формат
 * события на RabbitMQ, не отражается в OpenAPI (шина, не REST). Остаётся
 * ручным типом по этой причине.
 */
export interface AttendanceMarkedPayload {
  lesson_id: number
  user_id: number
  group_id: number
  status: string
  marked_by: string
}
