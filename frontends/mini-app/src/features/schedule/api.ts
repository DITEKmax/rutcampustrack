import { useMemo } from 'react'
import { useQueries, useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import type { LessonResponse, SubjectResponse } from './types'

export function useWeekSchedule(
  groupId: number | undefined,
  weekStart: string,
  weekEnd: string,
) {
  return useQuery<LessonResponse[]>({
    queryKey: ['schedule', groupId, weekStart],
    queryFn: async () => {
      const { data } = await apiClient.get(`/schedule/groups/${groupId}/lessons`, {
        params: { dateFrom: weekStart, dateTo: weekEnd, size: 100 },
      })
      return (data._embedded?.lessonResponseList ?? []).sort(
        (a: LessonResponse, b: LessonResponse) =>
          a.date.localeCompare(b.date) || a.lessonNumber - b.lessonNumber,
      )
    },
    staleTime: 60 * 60 * 1000,
    enabled: !!groupId,
  })
}

export function useTodaySchedule(groupId: number | undefined) {
  const today = new Date().toISOString().split('T')[0]
  return useQuery<LessonResponse[]>({
    queryKey: ['schedule', groupId, today],
    queryFn: async () => {
      const { data } = await apiClient.get(`/schedule/groups/${groupId}/lessons`, {
        params: { dateFrom: today, dateTo: today, size: 50 },
      })
      return (data._embedded?.lessonResponseList ?? [])
        .sort((a: LessonResponse, b: LessonResponse) => a.lessonNumber - b.lessonNumber)
    },
    staleTime: 10 * 60 * 1000,
    enabled: !!groupId,
  })
}

async function fetchSubject(subjectId: number): Promise<SubjectResponse> {
  const { data } = await apiClient.get(`/academic/subjects/${subjectId}`)
  return data
}

export function useSubjectName(subjectId: number) {
  return useQuery<SubjectResponse>({
    queryKey: ['subject', subjectId],
    queryFn: () => fetchSubject(subjectId),
    staleTime: 24 * 60 * 60 * 1000,
  })
}

export function useSubjectMap(subjectIds: number[]) {
  const uniqueIds = useMemo(
    () => [...new Set(subjectIds)].filter((id) => id > 0),
    [subjectIds],
  )

  const queries = useQueries({
    queries: uniqueIds.map((id) => ({
      queryKey: ['subject', id] as const,
      queryFn: () => fetchSubject(id),
      staleTime: 24 * 60 * 60 * 1000,
    })),
  })

  const subjectMap = useMemo<Record<number, string>>(() => {
    const map: Record<number, string> = {}
    uniqueIds.forEach((id, index) => {
      const subject = queries[index]?.data
      if (subject?.name) map[id] = subject.name
    })
    return map
  }, [queries, uniqueIds])

  return { subjectMap, isLoading: queries.some((query) => query.isLoading) }
}
