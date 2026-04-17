import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import type {
  CreateHomeworkRequest,
  HomeworkResponse,
  SemesterResponse,
  UpdateHomeworkRequest,
} from './types'

const BASE = '/academic/homeworks'

export const homeworkKeys = {
  all: ['homeworks'] as const,
  list: (groupId: number, semesterId: number) =>
    ['homeworks', groupId, semesterId] as const,
  activeSemester: ['homeworks', 'active-semester'] as const,
}

/**
 * Active semester — needed because the homeworks endpoint requires a
 * semesterId param. Cached for 24h; semesters rotate at most twice a year.
 */
export function useActiveSemesterId() {
  return useQuery<number | null>({
    queryKey: homeworkKeys.activeSemester,
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/semesters', {
        params: { size: 100 },
      })
      const list: SemesterResponse[] =
        data?._embedded?.semesterResponseList ?? []
      return list.find((s) => s.active)?.id ?? null
    },
    staleTime: 24 * 60 * 60 * 1000,
  })
}

export function useHomeworksForGroup(
  groupId: number | undefined,
  semesterId: number | null | undefined,
) {
  return useQuery<HomeworkResponse[]>({
    queryKey: homeworkKeys.list(groupId ?? 0, semesterId ?? 0),
    queryFn: async () => {
      const { data } = await apiClient.get(BASE, {
        params: { groupId, semesterId, size: 200 },
      })
      return data?._embedded?.homeworkResponseList ?? []
    },
    enabled: !!groupId && !!semesterId,
    staleTime: 2 * 60 * 1000,
  })
}

function invalidateLists(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: homeworkKeys.all })
}

export function useCreateHomework() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (req: CreateHomeworkRequest) => {
      const { data } = await apiClient.post<HomeworkResponse>(BASE, req)
      return data
    },
    onSuccess: () => invalidateLists(qc),
  })
}

export function useUpdateHomework() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({
      id,
      req,
    }: {
      id: number
      req: UpdateHomeworkRequest
    }) => {
      const { data } = await apiClient.put<HomeworkResponse>(
        `${BASE}/${id}`,
        req,
      )
      return data
    },
    onSuccess: () => invalidateLists(qc),
  })
}

export function useDeleteHomework() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`${BASE}/${id}`)
    },
    onSuccess: () => invalidateLists(qc),
  })
}

export function useMarkHomeworkComplete() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => {
      await apiClient.post(`${BASE}/${id}/complete`, {})
    },
    onSuccess: () => invalidateLists(qc),
  })
}

export function useUnmarkHomeworkComplete() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`${BASE}/${id}/complete`)
    },
    onSuccess: () => invalidateLists(qc),
  })
}
