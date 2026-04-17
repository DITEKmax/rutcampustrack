/**
 * DTOs align 1:1 with academic-service contract:
 * services/academic-service/academic-api-contract/.../homework/*.java
 */

export interface HomeworkResponse {
  id: number
  title: string
  description: string | null
  link: string | null
  subjectId: number
  groupId: number
  semesterId: number
  publishedBy: number
  /** Per-student flag — true if the authenticated student has marked it done. */
  completed: boolean
  createdAt: string
  lessonDate: string    // YYYY-MM-DD
  lessonNumber: number
}

export interface CreateHomeworkRequest {
  title: string
  description: string | null
  link: string | null
  subjectId: number
  groupId: number
  semesterId: number
  lessonDate: string
  lessonNumber: number
}

/** PUT semantics: lesson binding is immutable post-create (backend D-01). */
export interface UpdateHomeworkRequest {
  title: string
  description: string | null
  link: string | null
}

export interface SemesterResponse {
  id: number
  active: boolean
}
