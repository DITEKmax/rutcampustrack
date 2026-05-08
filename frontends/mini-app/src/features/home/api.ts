import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import type {
  GroupResponse,
  OverallStats,
  StatusBreakdown,
  StudentDashboard,
  TopMissedSubject,
  WeeklyStat,
} from '@/api/schema'

export type {
  GroupResponse,
  OverallStats,
  StatusBreakdown,
  StudentDashboard,
  TopMissedSubject,
  WeeklyStat,
}

export interface MeResponse {
  id: number
  login: string
  fullName: string | null
  firstName: string | null
  lastName: string | null
  role: string
  groupId: number | null
  headman: boolean
  avatarId: string | null
}

async function fetchDashboard(): Promise<StudentDashboard> {
  const { data } = await apiClient.get('/attendance/reports/student/dashboard', {
    params: { topLimit: 5 },
  })
  const { _links: _ignored, ...payload } = data ?? {}
  return payload as StudentDashboard
}

async function fetchMe(): Promise<MeResponse> {
  const { data } = await apiClient.get('/academic/users/me')
  return data as MeResponse
}

async function fetchGroup(groupId: number): Promise<GroupResponse> {
  const { data } = await apiClient.get(`/academic/groups/${groupId}`)
  return data as GroupResponse
}

export function useStudentDashboard() {
  return useQuery({
    queryKey: ['student-dashboard'],
    queryFn: fetchDashboard,
    staleTime: 60_000,
  })
}

export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: fetchMe,
    staleTime: 5 * 60_000,
  })
}

export function useGroupInfo(groupId: number | undefined | null) {
  return useQuery({
    queryKey: ['group', groupId],
    queryFn: () => fetchGroup(groupId as number),
    enabled: typeof groupId === 'number',
    staleTime: 10 * 60_000,
  })
}
