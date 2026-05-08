import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  JournalCellDto,
  JournalResponse,
  MarkRequest,
  ResolvedThreshold,
  ThresholdResponse,
} from '@/api/schema'
import { apiClient } from '@/shared/lib/axios'
import type { LessonResponse } from '@/features/schedule/types'
import type {
  Assistant,
  AssistantPermission,
  AttendanceStatus,
  AssistantResponse,
  ExcuseTicket,
  ExcuseTicketStatus,
  HeadmanWeeklyExportRequest,
  HeadmanWeeklyReportFormat,
  HeadmanWeeklyWeeksResponse,
  JournalAttendanceStatus,
  JournalCell,
  LateCheckinRequest,
  LateCheckinRequestStatus,
  PendingExcuse,
  PendingLateCheckin,
  ReportBlobResponse,
  Subject,
  SubjectType,
  Teacher,
  TodayLesson,
} from './types'
import { toGroupMember, type GroupMember } from './types'

export const headmanKeys = {
  all: ['headman'] as const,
  members: () => [...headmanKeys.all, 'members'] as const,
  assistants: (groupId: number) => [...headmanKeys.all, 'assistants', groupId] as const,
  subjects: () => [...headmanKeys.all, 'subjects'] as const,
  todayLesson: (groupId: number) => [...headmanKeys.all, 'todayLesson', groupId] as const,
  pendingExcuses: (groupId: number) => [...headmanKeys.all, 'pendingExcuses', groupId] as const,
  pendingLateCheckins: (groupId: number) => [...headmanKeys.all, 'pendingLateCheckins', groupId] as const,
  groupExcuses: (groupId: number, status?: ExcuseTicketStatus) =>
    [...headmanKeys.all, 'groupExcuses', groupId, status ?? 'all'] as const,
  groupLateCheckins: (groupId: number) => [...headmanKeys.all, 'groupLateCheckins', groupId] as const,
  journal: (groupId: number, subjectId: number, dateFrom: string, dateTo: string) =>
    [...headmanKeys.all, 'journal', groupId, subjectId, dateFrom, dateTo] as const,
  threshold: (groupId: number, subjectId: number) =>
    [...headmanKeys.all, 'threshold', groupId, subjectId] as const,
  weeklyWeeks: () => [...headmanKeys.all, 'weeklyWeeks'] as const,
}

function unwrapEmbeddedList<T>(data: unknown, keys: string[]): T[] {
  if (Array.isArray(data)) return data as T[]
  if (!data || typeof data !== 'object') return []
  const embedded = (data as { _embedded?: Record<string, unknown> })._embedded
  if (!embedded) return []
  for (const key of keys) {
    const value = embedded[key]
    if (Array.isArray(value)) return value as T[]
  }
  const firstArray = Object.values(embedded).find(Array.isArray)
  return (firstArray as T[] | undefined) ?? []
}

function todayDateString(): string {
  return new Date().toISOString().slice(0, 10)
}

function getHttpStatus(error: unknown): number {
  return (error as { response?: { status?: number } })?.response?.status ?? 0
}

function normalizeExcuseStatus(value: unknown): ExcuseTicketStatus {
  const status = String(value ?? 'submitted').toLowerCase()
  if (status === 'approved' || status === 'rejected' || status === 'draft') return status
  return 'submitted'
}

function normalizeLateCheckinStatus(value: unknown): LateCheckinRequestStatus {
  const status = String(value ?? 'pending').toLowerCase()
  if (status === 'approved' || status === 'rejected') return status
  return 'pending'
}

function normalizeAttendanceStatus(value: unknown): JournalAttendanceStatus {
  const status = String(value ?? 'absent').toLowerCase().replace('-', '_')
  if (
    status === 'present' ||
    status === 'absent' ||
    status === 'excused' ||
    status === 'free_attendance' ||
    status === 'cancelled'
  ) {
    return status
  }
  return 'absent'
}

function toMarkRequestStatus(status: AttendanceStatus): MarkRequest['status'] {
  return status.toUpperCase() as MarkRequest['status']
}

function flattenJournal(data: JournalResponse): JournalCell[] {
  return (data.students ?? []).flatMap((student) => {
    const studentId = Number(student.userId ?? 0)
    if (!studentId) return []
    return (student.records ?? [])
      .map((record: JournalCellDto): JournalCell | null => {
        const lessonId = Number(record.lessonId ?? 0)
        if (!lessonId) return null
        return {
          lessonId,
          studentId,
          studentName: student.displayName || 'Студент',
          date: record.date ?? '',
          lessonNumber: record.lessonNumber ?? null,
          status: normalizeAttendanceStatus(record.status),
          symbol: record.symbol ?? null,
          excuseReason: record.excuseReason ?? null,
        }
      })
      .filter((record): record is JournalCell => record !== null)
  })
}

function normalizeExcuseTicket(ticket: Partial<ExcuseTicket>): ExcuseTicket {
  return {
    ...ticket,
    id: String(ticket.id ?? ''),
    studentId: Number(ticket.studentId ?? 0),
    groupId: Number(ticket.groupId ?? 0),
    studentName: ticket.studentName || 'Студент',
    lessonIds: ticket.lessonIds ?? [],
    excuseType: ticket.excuseType ?? 'OTHER',
    status: normalizeExcuseStatus(ticket.status),
    comment: ticket.comment ?? null,
    decisionBy: ticket.decisionBy ?? null,
    decisionComment: ticket.decisionComment ?? null,
    decisionAt: ticket.decisionAt ?? null,
    createdAt: ticket.createdAt ?? '',
    updatedAt: ticket.updatedAt ?? '',
  }
}

function normalizeLateCheckinRequest(
  request: Partial<LateCheckinRequest>,
): LateCheckinRequest {
  return {
    ...request,
    id: String(request.id ?? ''),
    studentId: Number(request.studentId ?? 0),
    groupId: Number(request.groupId ?? 0),
    lessonId: Number(request.lessonId ?? 0),
    studentName: request.studentName || 'Студент',
    status: normalizeLateCheckinStatus(request.status),
    decisionBy: request.decisionBy ?? null,
    decisionAt: request.decisionAt ?? null,
    createdAt: request.createdAt ?? '',
    updatedAt: request.updatedAt ?? '',
  }
}

function normalizeAssistant(assistant: Partial<AssistantResponse>): Assistant {
  const fullName = assistant.fullName || assistant.studentName || assistant.login || 'Студент'
  return {
    id: Number(assistant.id ?? 0),
    studentId: Number(assistant.studentId ?? 0),
    groupId: Number(assistant.groupId ?? 0),
    fullName,
    login: assistant.login ?? '',
    permissions: assistant.permissions ?? [],
    active: assistant.active ?? true,
  }
}

function normalizeSubject(subject: Partial<Subject>): Subject {
  return {
    ...subject,
    id: Number(subject.id ?? 0),
    name: subject.name || 'Предмет',
    type: subject.type ?? 'LECTURE',
    groupId: subject.groupId ?? null,
    teacherIds: subject.teacherIds ?? [],
    createdAt: subject.createdAt ?? null,
  }
}

function normalizeResolvedThreshold(threshold: Partial<ResolvedThreshold>): ResolvedThreshold {
  return {
    ...threshold,
    minPercentage: Number(threshold.minPercentage ?? 75),
    level: threshold.level ?? null,
    sourceId: threshold.sourceId ?? null,
  }
}

function normalizeThreshold(threshold: Partial<ThresholdResponse>): ThresholdResponse {
  return {
    ...threshold,
    id: threshold.id ?? null,
    groupId: threshold.groupId ?? null,
    subjectId: threshold.subjectId ?? null,
    minPercentage: Number(threshold.minPercentage ?? 75),
    updatedAt: threshold.updatedAt ?? null,
  }
}

function normalizeWeeklyWeeks(data: Partial<HeadmanWeeklyWeeksResponse>): HeadmanWeeklyWeeksResponse {
  return {
    ...data,
    semesterId: data.semesterId ?? null,
    semesterName: data.semesterName ?? null,
    semesterDateFrom: data.semesterDateFrom ?? null,
    semesterDateTo: data.semesterDateTo ?? null,
    weeks: (data.weeks ?? []).map((week) => ({
      weekOfSemester: Number(week.weekOfSemester ?? 0),
      isoWeek: Number(week.isoWeek ?? 0),
      label: week.label ?? '',
      weekStart: week.weekStart ?? '',
      weekEnd: week.weekEnd ?? '',
      current: !!week.current,
    })),
  }
}

function toTeacher(user: Teacher): Teacher {
  return {
    ...user,
    fullName:
      user.fullName ||
      [user.lastName, user.firstName, user.middleName].filter(Boolean).join(' ').trim() ||
      user.login,
  }
}

export function useGroupMembers(enabled = true) {
  return useQuery<GroupMember[]>({
    queryKey: headmanKeys.members(),
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/groups/my/members', {
        params: { page: 0, size: 100 },
      })
      return unwrapEmbeddedList<Parameters<typeof toGroupMember>[0]>(data, [
        'userResponseList',
      ]).map(toGroupMember)
    },
    staleTime: 5 * 60 * 1000,
    enabled,
  })
}

export function useGroupSubjects(enabled = true) {
  return useQuery<Subject[]>({
    queryKey: headmanKeys.subjects(),
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/subjects', {
        params: { page: 0, size: 100 },
      })
      return unwrapEmbeddedList<Partial<Subject>>(data, ['subjectResponseList']).map(normalizeSubject)
    },
    staleTime: 24 * 60 * 60 * 1000,
    enabled,
  })
}

export function useGroupTeachers(enabled = true) {
  return useQuery<Teacher[]>({
    queryKey: [...headmanKeys.all, 'teachers'] as const,
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/users/teachers')
      return unwrapEmbeddedList<Teacher>(data, ['userResponseList']).map(toTeacher)
    },
    staleTime: 24 * 60 * 60 * 1000,
    enabled,
  })
}

export function useAssistants(groupId: number | undefined) {
  return useQuery<Assistant[]>({
    queryKey: headmanKeys.assistants(groupId ?? 0),
    queryFn: async () => {
      try {
        const { data } = await apiClient.get('/academic/assistants', {
          params: { groupId },
        })
        return unwrapEmbeddedList<Partial<AssistantResponse>>(data, [
          'assistantResponseList',
        ]).map(normalizeAssistant)
      } catch (error) {
        const status = getHttpStatus(error)
        if (status === 403 || status === 404) return []
        throw error
      }
    },
    retry: false,
    staleTime: 5 * 60 * 1000,
    enabled: !!groupId,
  })
}

export function useTodayLesson(groupId: number | undefined) {
  return useQuery<TodayLesson | null>({
    queryKey: headmanKeys.todayLesson(groupId ?? 0),
    queryFn: async () => {
      const today = todayDateString()
      const { data } = await apiClient.get(`/schedule/groups/${groupId}/lessons`, {
        params: { dateFrom: today, dateTo: today, size: 20 },
      })
      const lessons = unwrapEmbeddedList<LessonResponse>(data, ['lessonResponseList'])
      const activeOrNext =
        lessons.find((lesson) => lesson.status === 'ACTIVE') ??
        lessons.find((lesson) => lesson.status === 'PLANNED') ??
        null
      if (!activeOrNext) return null
      return {
        lessonId: activeOrNext.id,
        subjectName: 'Пара',
        startsAt: activeOrNext.startTime,
        endsAt: activeOrNext.endTime,
        room: activeOrNext.room,
      }
    },
    staleTime: 5 * 60 * 1000,
    enabled: !!groupId,
  })
}

export function useJournal(
  groupId: number | undefined,
  subjectId: number | undefined,
  dateFrom: string,
  dateTo: string,
) {
  return useQuery<JournalCell[]>({
    queryKey: headmanKeys.journal(groupId ?? 0, subjectId ?? 0, dateFrom, dateTo),
    queryFn: async () => {
      const { data } = await apiClient.get('/attendance/reports/journal', {
        params: { groupId, subjectId, dateFrom, dateTo },
      })
      return flattenJournal(data as JournalResponse)
    },
    staleTime: 30 * 1000,
    enabled: !!groupId && !!subjectId && !!dateFrom && !!dateTo,
  })
}

export function useMarkAttendance() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      lessonId,
      studentId,
      status,
    }: {
      lessonId: number
      studentId: number
      status: AttendanceStatus
    }) => {
      await apiClient.put(`/attendance/lessons/${lessonId}/students/${studentId}`, {
        status: toMarkRequestStatus(status),
      })
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: headmanKeys.all })
    },
  })
}

export function useResolveThreshold(
  groupId: number | undefined,
  subjectId: number | undefined,
) {
  return useQuery<ResolvedThreshold>({
    queryKey: headmanKeys.threshold(groupId ?? 0, subjectId ?? 0),
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/thresholds/resolve', {
        params: { groupId, subjectId },
      })
      return normalizeResolvedThreshold(data as Partial<ResolvedThreshold>)
    },
    staleTime: 24 * 60 * 60 * 1000,
    enabled: !!groupId && !!subjectId,
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
      return normalizeThreshold(data as Partial<ThresholdResponse>)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: headmanKeys.all })
    },
  })
}

export function useHeadmanWeeklyReportWeeks(enabled = true) {
  return useQuery<HeadmanWeeklyWeeksResponse>({
    queryKey: headmanKeys.weeklyWeeks(),
    queryFn: async () => {
      const { data } = await apiClient.get('/attendance/reports/headman-weekly/weeks')
      return normalizeWeeklyWeeks(data as Partial<HeadmanWeeklyWeeksResponse>)
    },
    staleTime: 5 * 60 * 1000,
    enabled,
  })
}

export function useDownloadHeadmanWeeklyReport() {
  return useMutation({
    mutationFn: async ({
      weekStart,
      format,
    }: {
      weekStart: string
      format: HeadmanWeeklyReportFormat
    }): Promise<ReportBlobResponse> => {
      const { data, headers } = await apiClient.get(
        '/attendance/reports/headman-weekly/current',
        {
          params: { weekStart, format },
          responseType: 'blob',
        },
      )
      return { data, headers }
    },
  })
}

export function useExportHeadmanWeeklyReport() {
  return useMutation({
    mutationFn: async (body: HeadmanWeeklyExportRequest): Promise<ReportBlobResponse> => {
      const { data, headers } = await apiClient.post(
        '/attendance/reports/headman-weekly/export',
        body,
        { responseType: 'blob' },
      )
      return { data, headers }
    },
  })
}

export function usePendingExcuses(groupId: number | undefined) {
  return useQuery<PendingExcuse[]>({
    queryKey: headmanKeys.pendingExcuses(groupId ?? 0),
    queryFn: async () => {
      try {
        const { data } = await apiClient.get('/attendance/excuses/pending', {
          params: { groupId },
        })
        return unwrapEmbeddedList<PendingExcuse>(data, ['pendingExcuseList'])
      } catch (error) {
        const status = getHttpStatus(error)
        if (status === 403 || status === 404) return []
        throw error
      }
    },
    retry: false,
    staleTime: 60 * 1000,
    enabled: !!groupId,
  })
}

export function usePendingLateCheckins(groupId: number | undefined) {
  return useQuery<PendingLateCheckin[]>({
    queryKey: headmanKeys.pendingLateCheckins(groupId ?? 0),
    queryFn: async () => {
      try {
        const { data } = await apiClient.get('/attendance/late-checkin/pending', {
          params: { groupId },
        })
        return unwrapEmbeddedList<PendingLateCheckin>(data, ['pendingLateCheckinList'])
      } catch (error) {
        const status = getHttpStatus(error)
        if (status === 403 || status === 404) return []
        throw error
      }
    },
    retry: false,
    staleTime: 60 * 1000,
    enabled: !!groupId,
  })
}

export function useGroupExcuses(
  groupId: number | undefined,
  status?: ExcuseTicketStatus,
) {
  return useQuery<ExcuseTicket[]>({
    queryKey: headmanKeys.groupExcuses(groupId ?? 0, status),
    queryFn: async () => {
      try {
        const { data } = await apiClient.get(`/attendance/excuses/group/${groupId}`, {
          params: { page: 0, size: 100, status: status?.toUpperCase() },
        })
        return unwrapEmbeddedList<Partial<ExcuseTicket>>(data, [
          'excuseTicketResponseList',
          'excuseTicketList',
        ]).map(normalizeExcuseTicket)
      } catch (error) {
        const statusCode = getHttpStatus(error)
        if (statusCode === 403 || statusCode === 404) return []
        throw error
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
      decisionComment?: string | null
    }) => {
      await apiClient.patch(`/attendance/excuses/${id}/status`, {
        status: approved ? 'APPROVED' : 'REJECTED',
        decisionComment: decisionComment?.trim() || null,
      })
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: headmanKeys.all })
    },
  })
}

export function usePendingLateCheckinRequests(groupId: number | undefined) {
  return useQuery<LateCheckinRequest[]>({
    queryKey: headmanKeys.groupLateCheckins(groupId ?? 0),
    queryFn: async () => {
      try {
        const { data } = groupId
          ? await apiClient.get(`/attendance/late-checkin/group/${groupId}`, {
              params: { page: 0, size: 100 },
            })
          : await apiClient.get('/attendance/late-checkin/pending')
        return unwrapEmbeddedList<Partial<LateCheckinRequest>>(data, [
          'lateCheckinRequestResponseList',
          'pendingLateCheckinList',
        ]).map(normalizeLateCheckinRequest)
      } catch (error) {
        const status = getHttpStatus(error)
        if (status === 403 || status === 404) return []
        throw error
      }
    },
    retry: false,
    staleTime: 60 * 1000,
    enabled: !!groupId,
  })
}

export function useDecideLateCheckin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, approved }: { id: string; approved: boolean }) => {
      await apiClient.post(`/attendance/late-checkin/${id}/decision`, { approved })
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: headmanKeys.all })
    },
  })
}

export function useCreateAssistant() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      studentId,
      groupId,
      permissions,
    }: {
      studentId: number
      groupId: number
      permissions: AssistantPermission[]
    }) => {
      const { data } = await apiClient.post('/academic/assistants', {
        studentId,
        groupId,
        permissions,
      })
      return normalizeAssistant(data as Partial<AssistantResponse>)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: headmanKeys.all })
    },
  })
}

export function useDeleteAssistant() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`/academic/assistants/${id}`)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: headmanKeys.all })
    },
  })
}

export function useCreateSubject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      name,
      type,
      teacherIds,
    }: {
      name: string
      type: SubjectType
      teacherIds: number[]
    }) => {
      const { data } = await apiClient.post('/academic/subjects', {
        name,
        type,
        teacherIds,
      })
      return normalizeSubject(data as Partial<Subject>)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: headmanKeys.all })
    },
  })
}

export function useUpdateSubject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      id,
      name,
      type,
      previousTeacherIds,
      teacherIds,
    }: {
      id: number
      name: string
      type: SubjectType
      previousTeacherIds: number[]
      teacherIds: number[]
    }) => {
      const { data } = await apiClient.put(`/academic/subjects/${id}`, { name, type })
      const previous = new Set(previousTeacherIds)
      const next = new Set(teacherIds)
      const removals = previousTeacherIds.filter((teacherId) => !next.has(teacherId))
      const additions = teacherIds.filter((teacherId) => !previous.has(teacherId))
      await Promise.all([
        ...removals.map((teacherId) =>
          apiClient.delete(`/academic/subjects/${id}/teachers/${teacherId}`),
        ),
        ...additions.map((teacherId) =>
          apiClient.post(`/academic/subjects/${id}/teachers/${teacherId}`),
        ),
      ])
      return normalizeSubject(data as Partial<Subject>)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: headmanKeys.all })
    },
  })
}

export function useDeleteSubject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`/academic/subjects/${id}`)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: headmanKeys.all })
    },
  })
}

export function mapHeadmanApiError(status: number): string {
  switch (status) {
    case 400:
    case 422:
      return 'Проверьте заполненные данные.'
    case 403:
      return 'У вас нет прав на эту операцию.'
    case 404:
      return 'Студент или помощник не найден.'
    case 409:
      return 'Операция конфликтует с текущими данными.'
    case 429:
      return 'Слишком много запросов. Подождите минуту.'
    default:
      return 'Ошибка сервера. Попробуйте еще раз.'
  }
}
