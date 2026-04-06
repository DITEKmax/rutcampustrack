import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import type { StudentStatsResponse, AttendanceRecordEntry } from './types'

export function useStudentStats() {
  return useQuery<StudentStatsResponse>({
    queryKey: ['attendance', 'stats'],
    queryFn: async () => {
      const { data } = await apiClient.get('/attendance/reports/student/stats')
      return data
    },
    staleTime: 60 * 60 * 1000,
    refetchOnReconnect: true,
  })
}

export function useThreshold(groupId: number | undefined) {
  return useQuery<number | null>({
    queryKey: ['threshold', groupId],
    queryFn: async () => {
      try {
        const { data } = await apiClient.get('/academic/thresholds/resolve', {
          params: { groupId },
        })
        return data.minPercentage as number
      } catch (err: unknown) {
        const error = err as { response?: { status?: number } }
        if (error?.response?.status === 404) {
          return null
        }
        throw err
      }
    },
    enabled: !!groupId,
    staleTime: 24 * 60 * 60 * 1000,
  })
}

export function useAttendanceRecords(subjectId: number | undefined) {
  return useQuery<AttendanceRecordEntry[]>({
    queryKey: ['attendance', 'records', subjectId],
    queryFn: async () => {
      const { data } = await apiClient.get('/attendance/reports/student/records', {
        params: { subjectId },
      })
      return (
        data._embedded?.attendanceRecordEntryList ??
        data._embedded?.[Object.keys(data._embedded ?? {})[0]] ??
        []
      )
    },
    enabled: !!subjectId,
    staleTime: 60 * 60 * 1000,
    refetchOnReconnect: true,
  })
}
