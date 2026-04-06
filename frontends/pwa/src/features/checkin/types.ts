export interface CheckinRequest {
  lat: number
  lng: number
}

export interface AttendanceMarkedPayload {
  lesson_id: number
  user_id: number
  group_id: number
  status: string
  marked_by: string
}
