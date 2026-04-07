import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import type { LessonResponse, SubjectResponse } from './types'

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

export function useSubjectName(subjectId: number) {
  return useQuery<SubjectResponse>({
    queryKey: ['subject', subjectId],
    queryFn: async () => {
      const { data } = await apiClient.get(`/academic/subjects/${subjectId}`)
      return data
    },
    staleTime: 24 * 60 * 60 * 1000,
  })
}
