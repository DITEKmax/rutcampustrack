import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'

export type HeadmanAttendanceStatus =
  | 'present'
  | 'absent'
  | 'excused'
  | 'free_attendance'
  | 'cancelled'

export type AttendanceSource =
  | 'student_geo'
  | 'headman'
  | 'auto_scheduler'
  | 'late_checkin'
  | 'headman_excuse'

export interface LessonAttendanceEntry {
  userId: number
  displayName: string
  status: HeadmanAttendanceStatus
  symbol: string
  /** null if no attendance doc yet. */
  source: AttendanceSource | null
}

export interface LessonAttendance {
  lessonId: number
  groupId: number
  subjectId: number
  lessonDate: string
  entries: LessonAttendanceEntry[]
}

async function fetchLessonAttendance(lessonId: number): Promise<LessonAttendance> {
  const { data } = await apiClient.get(`/attendance/reports/lesson/${lessonId}`)
  const { _links: _ignored, ...payload } = data ?? {}
  return payload as LessonAttendance
}

export function useLessonAttendance(lessonId: number | null) {
  return useQuery({
    queryKey: ['lessonAttendance', lessonId],
    queryFn: () => fetchLessonAttendance(lessonId as number),
    enabled: typeof lessonId === 'number' && lessonId > 0,
    staleTime: 15_000,
  })
}

/**
 * PUT /attendance/lessons/{lessonId}/students/{userId}
 * Same endpoint as web-panel headman-journal-grid. Status payload lowercase.
 */
export function useHeadmanMarkAttendance() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({
      lessonId,
      userId,
      status,
    }: {
      lessonId: number
      userId: number
      status: HeadmanAttendanceStatus
    }) => {
      const { data } = await apiClient.put(
        `/attendance/lessons/${lessonId}/students/${userId}`,
        { status },
      )
      return data
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ['lessonAttendance', vars.lessonId] })
      qc.invalidateQueries({ queryKey: ['journal'] })
    },
  })
}
