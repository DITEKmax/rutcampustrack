export interface LessonResponse {
  id: number
  scheduleItemId: number
  groupId: number
  subjectId: number
  teacherId: number
  date: string           // 'YYYY-MM-DD'
  status: 'PLANNED' | 'ACTIVE' | 'CLOSED' | 'CANCELLED'
  dayOfWeek: number      // 1=Mon..7=Sun
  lessonNumber: number
  startTime: string      // 'HH:mm:ss'
  endTime: string        // 'HH:mm:ss'
  weekType: 'NUMERATOR' | 'DENOMINATOR' | 'BOTH'
  room: string
  geoBlocked: boolean
  cancelReason: string | null
  createdAt: string
}

export type LessonStatus = LessonResponse['status']
export type AttendanceStatus = 'present' | 'absent' | 'excused' | 'free_attendance'

export interface SubjectResponse {
  id: number
  name: string
}
