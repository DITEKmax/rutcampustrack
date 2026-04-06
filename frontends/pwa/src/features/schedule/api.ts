import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { apiClient } from '@/shared/lib/axios'
import type { LessonResponse, SubjectResponse } from './types'

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
