import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import type { HomeworkResponse, SemesterResponse } from './types'

export function useActiveSemester() {
  return useQuery<SemesterResponse | null>({
    queryKey: ['semester', 'active'],
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/semesters', {
        params: { size: 20 },
      })
      const semesters: SemesterResponse[] = data._embedded?.semesterResponseList ?? []
      return semesters.find(s => s.active) ?? null
    },
    staleTime: 24 * 60 * 60 * 1000,
  })
}

export function useHomeworkList(groupId: number | undefined, semesterId: number | undefined) {
  return useQuery<HomeworkResponse[]>({
    queryKey: ['homeworks', groupId, semesterId],
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/homeworks', {
        params: { groupId, semesterId, size: 50 },
      })
      return data._embedded?.homeworkResponseList ?? []
    },
    staleTime: 5 * 60 * 1000,
    enabled: !!groupId && !!semesterId,
  })
}

export function useToggleHomework(groupId: number | undefined, semesterId: number | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, completed }: { id: number; completed: boolean }) => {
      if (completed) {
        await apiClient.delete(`/academic/homeworks/${id}/complete`)
      } else {
        await apiClient.post(`/academic/homeworks/${id}/complete`)
      }
    },
    onMutate: async ({ id, completed }) => {
      await queryClient.cancelQueries({ queryKey: ['homeworks', groupId, semesterId] })
      const previous = queryClient.getQueryData<HomeworkResponse[]>(['homeworks', groupId, semesterId])
      queryClient.setQueryData<HomeworkResponse[]>(['homeworks', groupId, semesterId], (old) =>
        old?.map(hw => hw.id === id ? { ...hw, completed: !completed } : hw) ?? []
      )
      return { previous }
    },
    onError: (_err, _vars, context) => {
      queryClient.setQueryData(['homeworks', groupId, semesterId], context?.previous)
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['homeworks', groupId, semesterId] })
    },
  })
}
