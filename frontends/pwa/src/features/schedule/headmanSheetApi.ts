import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import type {
  AttendanceSource,
  HeadmanAttendanceStatus,
  LessonAttendance,
  LessonAttendanceEntry,
} from '@/api/schema'

export type {
  AttendanceSource,
  HeadmanAttendanceStatus,
  LessonAttendance,
  LessonAttendanceEntry,
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

/**
 * POST /attendance/marks/batch — M05 D7 / P2-10/4.
 * Pseudo-atomic: либо все отметки сохранены, либо ни одна.
 * Один HTTP-запрос вместо N serial PUT'ов (30 студентов: ~6000ms → ~500ms).
 */
export function useHeadmanMarkBatch() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({
      lessonId,
      items,
    }: {
      lessonId: number
      items: Array<{ userId: number; status: HeadmanAttendanceStatus }>
    }) => {
      const payload = {
        items: items.map((i) => ({
          lessonId,
          userId: i.userId,
          status: i.status,
        })),
      }
      const { data } = await apiClient.post('/attendance/marks/batch', payload)
      return data
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ['lessonAttendance', vars.lessonId] })
      qc.invalidateQueries({ queryKey: ['journal'] })
    },
  })
}
