import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import type {
  GroupMember,
  Subject,
  Teacher,
  JournalCell,
  ResolvedThreshold,
  TodayLesson,
  PendingExcuse,
  PendingLateCheckin,
  ExcuseTicket,
  ExcuseTicketStatus,
  LateCheckinRequest,
} from './types'
import type { AttendanceStatus } from './types'

// ─── GET queries ─────────────────────────────────────────────────────────────

export function useGroupMembers(page = 0, size = 50) {
  return useQuery<GroupMember[]>({
    queryKey: ['groupMembers', page, size],
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/groups/my/members', {
        params: { page, size },
      })
      return data._embedded?.userResponseList ?? []
    },
    staleTime: 5 * 60 * 1000, // 5 min
  })
}

export function useGroupSubjects(page = 0, size = 100) {
  return useQuery<Subject[]>({
    queryKey: ['groupSubjects', page, size],
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/subjects', {
        params: { page, size },
      })
      return data._embedded?.subjectResponseList ?? []
    },
    staleTime: 24 * 60 * 60 * 1000, // 24 hours
  })
}

export function useGroupTeachers(groupId: number) {
  return useQuery<Teacher[]>({
    queryKey: ['groupTeachers', groupId],
    queryFn: async () => {
      const { data } = await apiClient.get(`/academic/groups/${groupId}/teachers`)
      return data ?? []
    },
    staleTime: 24 * 60 * 60 * 1000, // 24 hours
    enabled: !!groupId,
  })
}

/**
 * Flatten the backend journal response ({groupId, subjectId, dates, students:
 * [{userId, displayName, records: JournalCell[]}]}) into a flat list of
 * {studentId, studentName, lessonId, date, status} cells that the PWA
 * journal/stats UI expects.
 *
 * Previously this hook read `_embedded.journalCellList` which never exists —
 * the endpoint returns the tree shape directly — so every caller saw an empty
 * list and the stats page rendered 100% for every subject and student.
 */
export function useJournal(
  groupId: number,
  subjectId: number,
  dateFrom: string,
  dateTo: string,
) {
  return useQuery<JournalCell[]>({
    queryKey: ['journal', groupId, subjectId, dateFrom, dateTo],
    queryFn: async () => {
      const { data } = await apiClient.get('/attendance/reports/journal', {
        params: { groupId, subjectId, dateFrom, dateTo },
      })
      const students: Array<{
        userId: number
        displayName: string
        records: Array<{
          lessonId: number
          date: string
          status: AttendanceStatus
        }>
      }> = data?.students ?? []
      const cells: JournalCell[] = []
      for (const s of students) {
        for (const r of s.records ?? []) {
          cells.push({
            lessonId: r.lessonId,
            studentId: s.userId,
            studentName: s.displayName,
            date: r.date,
            status: r.status,
          })
        }
      }
      return cells
    },
    staleTime: 5 * 60 * 1000, // 5 min
    enabled: !!groupId && !!subjectId,
  })
}

export function useResolveThreshold(groupId: number, subjectId: number) {
  return useQuery<ResolvedThreshold>({
    queryKey: ['threshold', groupId, subjectId],
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/thresholds/resolve', {
        params: { groupId, subjectId },
      })
      return data
    },
    staleTime: 24 * 60 * 60 * 1000, // 24 hours
    enabled: !!groupId && !!subjectId,
  })
}

export function useTodayLesson(groupId: number) {
  return useQuery<TodayLesson | null>({
    queryKey: ['todayLesson', groupId],
    queryFn: async () => {
      const today = new Date().toISOString().split('T')[0] // YYYY-MM-DD
      const { data } = await apiClient.get(`/schedule/groups/${groupId}/lessons`, {
        params: { dateFrom: today, dateTo: today, size: 1 },
      })
      const lessons = data._embedded?.lessonResponseList ?? []
      if (lessons.length === 0) return null
      const first = lessons[0]
      return {
        lessonId: first.id,
        subjectName: first.subjectName ?? '',
        startsAt: first.startsAt ?? '',
        endsAt: first.endsAt ?? '',
        room: first.room,
      } as TodayLesson
    },
    staleTime: 5 * 60 * 1000,
    enabled: !!groupId,
  })
}

export function usePendingExcuses(groupId: number) {
  return useQuery<PendingExcuse[]>({
    queryKey: ['pendingExcuses', groupId],
    queryFn: async () => {
      try {
        const { data } = await apiClient.get('/attendance/excuses/pending', {
          params: { groupId },
        })
        return data._embedded?.pendingExcuseList ?? data ?? []
      } catch (err: unknown) {
        // Graceful degradation per D-10: 404 means endpoint not yet implemented
        const status = (err as { response?: { status?: number } })?.response?.status
        if (status === 404) return []
        throw err
      }
    },
    retry: false,
    staleTime: 5 * 60 * 1000,
    enabled: !!groupId,
  })
}

export function usePendingLateCheckins(groupId: number) {
  return useQuery<PendingLateCheckin[]>({
    queryKey: ['pendingLateCheckins', groupId],
    queryFn: async () => {
      try {
        const { data } = await apiClient.get('/attendance/late-checkins/pending', {
          params: { groupId },
        })
        return data._embedded?.pendingLateCheckinList ?? data ?? []
      } catch (err: unknown) {
        // Graceful degradation per D-10: 404 means endpoint not yet implemented
        const status = (err as { response?: { status?: number } })?.response?.status
        if (status === 404) return []
        throw err
      }
    },
    retry: false,
    staleTime: 5 * 60 * 1000,
    enabled: !!groupId,
  })
}

/**
 * Phase 59 endpoint: excuse tickets for the headman's group.
 * GET /api/attendance/excuses/group/{groupId}?status=...
 * 403/404 → empty list (graceful degradation while backend rolls out).
 */
export function useGroupExcuses(
  groupId: number,
  status?: ExcuseTicketStatus | 'submitted',
) {
  return useQuery<ExcuseTicket[]>({
    queryKey: ['groupExcuses', groupId, status ?? 'all'],
    queryFn: async () => {
      try {
        const params: Record<string, unknown> = { size: 50 }
        if (status) params.status = status
        const { data } = await apiClient.get(
          `/attendance/excuses/group/${groupId}`,
          { params },
        )
        return (
          data._embedded?.excuseTicketList ??
          data._embedded?.excuseTicketResponseList ??
          []
        )
      } catch (err: unknown) {
        const s = (err as { response?: { status?: number } })?.response?.status
        if (s === 403 || s === 404) return []
        throw err
      }
    },
    retry: false,
    staleTime: 60 * 1000,
    enabled: !!groupId,
  })
}

export function useDecideExcuse() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      id,
      approved,
      decisionComment,
    }: {
      id: string
      approved: boolean
      decisionComment: string | null
    }) => {
      await apiClient.patch(`/attendance/excuses/${id}/status`, {
        status: approved ? 'approved' : 'rejected',
        decisionComment: decisionComment ?? null,
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groupExcuses'] })
    },
  })
}

/**
 * Late-checkin requests for the headman's own group.
 * GET /api/attendance/late-checkin/pending (from Phase 61 — web channel).
 * 403/404 → empty list (graceful degradation).
 */
export function usePendingLateCheckinRequests() {
  return useQuery<LateCheckinRequest[]>({
    queryKey: ['pendingLateCheckinRequests'],
    queryFn: async () => {
      try {
        const { data } = await apiClient.get('/attendance/late-checkin/pending')
        if (Array.isArray(data)) return data as LateCheckinRequest[]
        const embedded = data?._embedded
        if (!embedded) return []
        return (
          (embedded.lateCheckinRequestResponseList as LateCheckinRequest[]) ??
          (Object.values(embedded)[0] as LateCheckinRequest[]) ??
          []
        )
      } catch (err: unknown) {
        const s = (err as { response?: { status?: number } })?.response?.status
        if (s === 403 || s === 404) return []
        throw err
      }
    },
    retry: false,
    staleTime: 30 * 1000,
  })
}

export function useDecideLateCheckin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      requestId,
      approved,
    }: {
      requestId: string
      approved: boolean
    }) => {
      await apiClient.post(`/attendance/late-checkin/${requestId}/decision`, {
        approved,
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pendingLateCheckinRequests'] })
    },
  })
}

// ─── Mutations ───────────────────────────────────────────────────────────────

export function useMarkAttendance() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      lessonId,
      userId,
      status,
    }: {
      lessonId: number
      userId: number
      status: AttendanceStatus
    }) => {
      const { data } = await apiClient.put(
        `/attendance/lessons/${lessonId}/students/${userId}`,
        { status },
      )
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['journal'] })
    },
  })
}

export function useSetSubjectThreshold() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      subjectId,
      minPercentage,
    }: {
      subjectId: number
      minPercentage: number
    }) => {
      const { data } = await apiClient.put(
        '/academic/thresholds/subject',
        { minPercentage },
        { params: { subjectId } },
      )
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['threshold'] })
    },
  })
}

export function useCreateSubject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (body: { name: string; teacherId?: number | null }) => {
      const { data } = await apiClient.post('/academic/subjects', body)
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groupSubjects'] })
    },
  })
}

export function useUpdateSubject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      id,
      body,
    }: {
      id: number
      body: { name: string; teacherId?: number | null }
    }) => {
      const { data } = await apiClient.put(`/academic/subjects/${id}`, body)
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groupSubjects'] })
    },
  })
}

export function useDeleteSubject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => {
      const { data } = await apiClient.delete(`/academic/subjects/${id}`)
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groupSubjects'] })
    },
  })
}

export function useCreateAssistant() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (body: { studentId: number; permissions: string[] }) => {
      const { data } = await apiClient.post('/academic/assistants', body)
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groupMembers'] })
    },
  })
}

export function useDeleteAssistant() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => {
      const { data } = await apiClient.delete(`/academic/assistants/${id}`)
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groupMembers'] })
    },
  })
}

// ─── Error mapping ───────────────────────────────────────────────────────────

export function mapHeadmanApiError(status: number): string {
  switch (status) {
    case 403:
      return 'У вас нет прав на эту операцию'
    case 404:
      return 'Ресурс не найден (функция может быть в разработке)'
    case 422:
      return 'Некорректные данные'
    case 429:
      return 'Слишком много запросов. Подождите'
    default:
      return 'Ошибка сервера. Попробуйте ещё раз'
  }
}
