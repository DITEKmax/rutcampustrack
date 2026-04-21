import { useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo } from 'react'
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

async function fetchSubjectName(id: number): Promise<string> {
  const { data } = await apiClient.get<SubjectResponse>(`/academic/subjects/${id}`)
  return data.name
}

export function useSubjectName(subjectId: number | undefined) {
  return useQuery<string>({
    queryKey: ['subject', subjectId],
    queryFn: () => fetchSubjectName(subjectId as number),
    staleTime: 24 * 60 * 60 * 1000,
    enabled: !!subjectId,
  })
}

/**
 * Batch subject-name lookup (M07 G6/6 forkJoin fix).
 *
 * Возвращает `Record<id, name>` по переданным subjectIds. Делит кеш с
 * `useSubjectName` (тот же query key `['subject', id]`), поэтому когда
 * LessonCard тоже вызывает `useSubjectName` — попадает в cache hit.
 *
 * Ранее SchedulePage использовала `usePrefetchSubjects` + N×`useSubjectName`
 * в детях, что давало:
 * - first render: LessonCard → пустой `subjectName` → skeleton (потому
 *   что prefetch effect ещё не успел),
 * - second render: после TanStack onSuccess → real name.
 *
 * Теперь parent ждёт все promises через `useQueries`, собирает map и
 * **передаёт `subjectName` как prop** в LessonCard — один render,
 * никаких in-card skeleton'ов кроме первоначальной пары мс.
 */
export function useSubjectMap(subjectIds: number[]): {
  subjectMap: Record<number, string>
  isLoading: boolean
} {
  const uniqueIds = useMemo(
    () => [...new Set(subjectIds)].filter((id) => id > 0),
    [subjectIds],
  )

  const queries = useQueries({
    queries: uniqueIds.map((id) => ({
      queryKey: ['subject', id] as const,
      queryFn: () => fetchSubjectName(id),
      staleTime: 24 * 60 * 60 * 1000,
    })),
  })

  const subjectMap = useMemo<Record<number, string>>(() => {
    const m: Record<number, string> = {}
    uniqueIds.forEach((id, i) => {
      const q = queries[i]
      if (q?.data) m[id] = q.data
    })
    return m
  }, [queries, uniqueIds])

  const isLoading = queries.some((q) => q.isLoading)

  return { subjectMap, isLoading }
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
