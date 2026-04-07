import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import type { StudentStatsResponse } from './types'

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

// TODO: use backend threshold when available
export const RED_ZONE_THRESHOLD = 60
