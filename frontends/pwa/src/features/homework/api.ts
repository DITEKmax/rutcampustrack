import { useQuery, useMutation } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import { queryClient } from '@/shared/lib/queryClient'
import type { HomeworkResponse } from './types'

interface SemesterResponse {
  id: number
  name: string
  startDate: string
  endDate: string
  active: boolean
}

export function useActiveSemester() {
  return useQuery<number | null>({
    queryKey: ['semester', 'active'],
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/semesters', { params: { size: 50 } })
      const list: SemesterResponse[] =
        data._embedded?.semesterResponseList ??
        data._embedded?.[Object.keys(data._embedded ?? {})[0]] ??
        []
      const active = list.find((s) => s.active === true)
      return active?.id ?? null
    },
    staleTime: 24 * 60 * 60 * 1000,
  })
}

export function useHomework(groupId: number | undefined, semesterId: number | null | undefined) {
  return useQuery<HomeworkResponse[]>({
    queryKey: ['homework', groupId, semesterId],
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/homeworks', {
        params: { groupId, semesterId },
      })
      return (
        data._embedded?.homeworkResponseList ??
        data._embedded?.[Object.keys(data._embedded ?? {})[0]] ??
        []
      )
    },
    staleTime: 60 * 60 * 1000,
    refetchOnReconnect: true,
    enabled: !!groupId && !!semesterId,
  })
}

interface ToggleHomeworkVariables {
  id: number
  completed: boolean
  groupId: number | undefined
  semesterId: number | null | undefined
}

export function useToggleHomework() {
  return useMutation<void, Error, ToggleHomeworkVariables, { previous: HomeworkResponse[] | undefined }>({
    mutationFn: async ({ id, completed }: ToggleHomeworkVariables) => {
      if (completed) {
        await apiClient.post(`/academic/homeworks/${id}/complete`)
      } else {
        await apiClient.delete(`/academic/homeworks/${id}/complete`)
      }
    },
    onMutate: async ({ id, completed, groupId, semesterId }) => {
      await queryClient.cancelQueries({ queryKey: ['homework'] })
      const previous = queryClient.getQueryData<HomeworkResponse[]>(['homework', groupId, semesterId])
      queryClient.setQueryData<HomeworkResponse[]>(
        ['homework', groupId, semesterId],
        (old) => old?.map((h) => (h.id === id ? { ...h, completed } : h))
      )
      return { previous }
    },
    onError: (_err, { groupId, semesterId }, context) => {
      queryClient.setQueryData(['homework', groupId, semesterId], context?.previous)
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['homework'] })
    },
  })
}

export { useSubjectName } from '@/features/schedule/api'
