export interface HomeworkResponse {
  id: number
  subjectId: number
  subjectName: string
  groupId: number
  title: string
  description: string | null
  dueDate: string | null  // 'YYYY-MM-DD'
  completed: boolean
  createdAt: string
}

export interface SemesterResponse {
  id: number
  name: string
  startDate: string
  endDate: string
  active: boolean
}
