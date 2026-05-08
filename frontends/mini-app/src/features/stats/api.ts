import { useMemo } from 'react'
import { useQueries, useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import type { ResolvedThreshold } from '@/api/schema'
import type { StudentStatsResponse, SubjectStats } from './types'

export const DEFAULT_RED_ZONE_THRESHOLD = 60

export function useStudentStats() {
  return useQuery<StudentStatsResponse>({
    queryKey: ['student', 'stats'],
    queryFn: async () => {
      const { data } = await apiClient.get('/attendance/reports/student/stats')
      return data
    },
    staleTime: 5 * 60 * 1000,
  })
}

export function useStudentThresholds(
  groupId: number | undefined,
  subjects: SubjectStats[] | undefined,
) {
  const subjectIds = useMemo(
    () => Array.from(new Set((subjects ?? []).map((subject) => subject.subjectId))).sort((a, b) => a - b),
    [subjects],
  )

  const queries = useQueries({
    queries: subjectIds.map((subjectId) => ({
      queryKey: ['student', 'threshold', groupId, subjectId],
      enabled: Boolean(groupId),
      queryFn: async () => {
        const { data } = await apiClient.get<ResolvedThreshold>('/academic/thresholds/resolve', {
          params: { groupId, subjectId },
        })
        return Number(data.minPercentage ?? DEFAULT_RED_ZONE_THRESHOLD)
      },
      staleTime: 5 * 60 * 1000,
    })),
  })

  return useMemo(() => {
    const thresholds: Record<number, number> = {}
    subjectIds.forEach((subjectId, index) => {
      thresholds[subjectId] = queries[index]?.data ?? DEFAULT_RED_ZONE_THRESHOLD
    })
    return thresholds
  }, [queries, subjectIds])
}
