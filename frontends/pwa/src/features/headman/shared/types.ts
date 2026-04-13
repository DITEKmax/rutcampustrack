export interface GroupMember {
  id: number
  fullName: string
  login: string
  isHeadman?: boolean
  isAssistant?: boolean
}

export interface Teacher {
  id: number
  fullName: string
  login: string
}

export interface Subject {
  id: number
  name: string
  teacherId?: number
  teacherName?: string
}

export type AttendanceStatus = 'present' | 'absent' | 'excused' | 'free_attendance' | 'cancelled'

export interface JournalCell {
  lessonId: number
  studentId: number
  studentName: string
  date: string
  status: AttendanceStatus
}

export interface ResolvedThreshold {
  subjectId: number
  groupId: number
  minPercentage: number
  source: 'global' | 'group' | 'subject'
}

export interface Assistant {
  id: number
  studentId: number
  fullName: string
  permissions: string[]
}

export type AssistantPermission = 'manage_students' | 'manage_subjects' | 'manage_excuses' | 'manage_stats'

export interface PendingExcuse {
  id: number
  studentName: string
  reason: string
  createdAt: string
}

export interface PendingLateCheckin {
  id: number
  studentName: string
  lessonId: number
  createdAt: string
}

export interface TodayLesson {
  lessonId: number
  subjectName: string
  startsAt: string
  endsAt: string
  room?: string
}
