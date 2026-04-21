import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { apiClient } from '@/shared/lib/axios'
import type {
  AttendanceRecord,
  LessonResponse,
  SubjectResponse,
} from '@/api/schema'

export type { AttendanceRecord }

export function useWeekSchedule(groupId: number, weekStart: string, weekEnd: string) {
  return useQuery<LessonResponse[]>({
    queryKey: ['schedule', groupId, weekStart],
    queryFn: async () => {
      const { data } = await apiClient.get(`/schedule/groups/${groupId}/lessons`, {
        params: { dateFrom: weekStart, dateTo: weekEnd, size: 100 },
      })
      return data._embedded?.lessonResponseList ?? []
    },
    staleTime: 60 * 60 * 1000,
    refetchOnReconnect: true,
    enabled: !!groupId,
  })
}

export function useSubjectName(subjectId: number | undefined) {
  return useQuery<string>({
    queryKey: ['subject', subjectId],
    queryFn: async () => {
      const { data } = await apiClient.get<SubjectResponse>(`/academic/subjects/${subjectId}`)
      return data.name
    },
    staleTime: 24 * 60 * 60 * 1000,
    enabled: !!subjectId,
  })
}

/**
 * Fetch the authenticated student's attendance records. Used to seed the
 * schedule page with statuses ("+", "н", "у", "сп") that were recorded
 * outside the current session — without this, refreshed or past lessons
 * only show the neutral grey dot because STOMP only delivers live events.
 */
export function useStudentRecords() {
  return useQuery<AttendanceRecord[]>({
    queryKey: ['studentRecords'],
    queryFn: async () => {
      try {
        const { data } = await apiClient.get('/attendance/reports/student/records')
        const embedded = data?._embedded
        if (!embedded) return []
        return (
          embedded.attendanceRecordEntryList ??
          (Object.values(embedded)[0] as AttendanceRecord[]) ??
          []
        )
      } catch {
        return []
      }
    },
    staleTime: 60 * 1000,
  })
}

export function usePrefetchSubjects(subjectIds: number[]) {
  const queryClient = useQueryClient()

  useEffect(() => {
    const uniqueIds = [...new Set(subjectIds)]
    uniqueIds.forEach((id) => {
      queryClient.prefetchQuery({
        queryKey: ['subject', id],
        queryFn: async () => {
          const { data } = await apiClient.get<SubjectResponse>(`/academic/subjects/${id}`)
          return data.name
        },
        staleTime: 24 * 60 * 60 * 1000,
      })
    })
  }, [subjectIds, queryClient])
}
