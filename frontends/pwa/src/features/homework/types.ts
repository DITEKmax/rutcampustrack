export interface HomeworkResponse {
  id: number
  title: string
  description: string | null
  link: string | null
  subjectId: number
  groupId: number
  semesterId: number
  publishedBy: number
  completed: boolean
  createdAt: string
}
