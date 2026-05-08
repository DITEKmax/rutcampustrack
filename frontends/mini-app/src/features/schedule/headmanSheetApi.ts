import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  AttendanceSource,
  HeadmanAttendanceStatus,
  LessonAttendance,
  LessonAttendanceEntry,
  MarkBatchRequest,
} from '@/api/schema'
import { apiClient } from '@/shared/lib/axios'

type BackendMarkStatus = MarkBatchRequest['items'][number]['status']

export type {
  AttendanceSource,
  HeadmanAttendanceStatus,
  LessonAttendance,
  LessonAttendanceEntry,
}

export const headmanSheetKeys = {
  all: ['lessonAttendance'] as const,
  lesson: (lessonId: number) => [...headmanSheetKeys.all, lessonId] as const,
}

function normalizeStatus(value: unknown): HeadmanAttendanceStatus {
  const status = String(value ?? 'absent').toLowerCase().replace('-', '_')
  if (
    status === 'present' ||
    status === 'absent' ||
    status === 'excused' ||
    status === 'free_attendance'
  ) {
    return status
  }
  return 'absent'
}

function normalizeSource(value: unknown): AttendanceSource | null {
  const source = String(value ?? '').toLowerCase()
  if (
    source === 'student_geo' ||
    source === 'headman' ||
    source === 'auto_scheduler' ||
    source === 'late_checkin' ||
    source === 'headman_excuse'
  ) {
    return source
  }
  return null
}

function toBackendStatus(status: HeadmanAttendanceStatus): BackendMarkStatus {
  return status.toUpperCase() as BackendMarkStatus
}

function normalizeEntry(entry: Partial<LessonAttendanceEntry>): LessonAttendanceEntry | null {
  const userId = Number(entry.userId ?? 0)
  if (!userId) return null
  const status = normalizeStatus(entry.status)
  return {
    userId,
    displayName: entry.displayName || 'Студент',
    status,
    symbol: entry.symbol || statusSymbol(status),
    source: normalizeSource(entry.source),
    excuseReason: entry.excuseReason ?? null,
  }
}

function statusSymbol(status: HeadmanAttendanceStatus): string {
  switch (status) {
    case 'present':
      return '+'
    case 'excused':
      return 'у'
    case 'free_attendance':
      return 'сп'
    case 'absent':
    default:
      return 'н'
  }
}

async function fetchLessonAttendance(lessonId: number): Promise<LessonAttendance> {
  const { data } = await apiClient.get(`/attendance/reports/lesson/${lessonId}`)
  const entries = ((data?.entries ?? []) as Partial<LessonAttendanceEntry>[])
    .map(normalizeEntry)
    .filter((entry): entry is LessonAttendanceEntry => entry !== null)
  return {
    lessonId: Number(data?.lessonId ?? lessonId),
    groupId: Number(data?.groupId ?? 0),
    subjectId: Number(data?.subjectId ?? 0),
    lessonDate: data?.lessonDate ?? '',
    entries,
  }
}

export function useLessonAttendance(lessonId: number | null) {
  return useQuery<LessonAttendance>({
    queryKey: headmanSheetKeys.lesson(lessonId ?? 0),
    queryFn: () => fetchLessonAttendance(lessonId as number),
    enabled: typeof lessonId === 'number' && lessonId > 0,
    staleTime: 15_000,
  })
}

export function useHeadmanMarkAttendance() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      lessonId,
      userId,
      status,
    }: {
      lessonId: number
      userId: number
      status: HeadmanAttendanceStatus
    }) => {
      const { data } = await apiClient.put(`/attendance/lessons/${lessonId}/students/${userId}`, {
        status: toBackendStatus(status),
      })
      return data
    },
    onSuccess: (_data, vars) => {
      invalidateAttendance(queryClient, vars.lessonId)
    },
  })
}

export function useHeadmanMarkBatch() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      lessonId,
      items,
    }: {
      lessonId: number
      items: Array<{ userId: number; status: HeadmanAttendanceStatus }>
    }) => {
      const payload: MarkBatchRequest = {
        items: items.map((item) => ({
          lessonId,
          userId: item.userId,
          status: toBackendStatus(item.status),
        })),
      }
      const { data } = await apiClient.post('/attendance/marks/batch', payload)
      return data
    },
    onSuccess: (_data, vars) => {
      invalidateAttendance(queryClient, vars.lessonId)
    },
  })
}

export function useCancelLesson() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ lessonId, reason }: { lessonId: number; reason: string }) => {
      const { data } = await apiClient.patch(`/schedule/lessons/${lessonId}/cancel`, {
        reason,
      })
      return data
    },
    onSuccess: (_data, vars) => {
      invalidateAttendance(queryClient, vars.lessonId)
      queryClient.invalidateQueries({ queryKey: ['schedule'] })
    },
  })
}

function invalidateAttendance(queryClient: ReturnType<typeof useQueryClient>, lessonId: number) {
  queryClient.invalidateQueries({ queryKey: headmanSheetKeys.lesson(lessonId) })
  queryClient.invalidateQueries({ queryKey: ['headman'] })
  queryClient.invalidateQueries({ queryKey: ['studentRequests'] })
  queryClient.invalidateQueries({ queryKey: ['schedule'] })
  queryClient.invalidateQueries({ queryKey: ['student-dashboard'] })
}

export function mapLessonActionError(status: number | undefined): string {
  switch (status) {
    case 403:
      return 'Нет прав на это действие.'
    case 404:
      return 'Пара не найдена.'
    case 409:
      return 'Операция конфликтует с текущими данными.'
    case 422:
      return 'Действие недоступно для этой пары.'
    case 429:
      return 'Слишком много запросов. Подождите минуту.'
    default:
      return 'Ошибка сервера. Попробуйте еще раз.'
  }
}
