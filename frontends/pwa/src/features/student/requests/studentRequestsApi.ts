import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import type { AttendanceRecord, ExcuseTicket, ExcuseType } from '@/api/schema'

export type { AttendanceRecord, ExcuseTicket, ExcuseType }

export const studentRequestsKeys = {
  all: ['studentRequests'] as const,
  records: () => [...studentRequestsKeys.all, 'records'] as const,
  excuseTickets: () => [...studentRequestsKeys.all, 'excuseTickets'] as const,
}

export const EXCUSE_TYPE_LABELS: Record<ExcuseType, string> = {
  ILLNESS: 'Болезнь',
  SUMMONS: 'Повестка',
  UNIVERSITY_ORDER: 'Приказ университета',
  EXEMPTION: 'Освобождение',
  FREE_ATTENDANCE: 'Свободное посещение',
  OTHER: 'Другое',
}

export const EXCUSE_STATUS_LABELS: Record<ExcuseTicket['status'], string> = {
  draft: 'Черновик',
  submitted: 'На рассмотрении',
  approved: 'Одобрено',
  rejected: 'Отклонено',
}

export const EXCUSE_TYPE_OPTIONS = Object.keys(EXCUSE_TYPE_LABELS) as ExcuseType[]

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

async function fetchStudentAttendanceRecords(): Promise<AttendanceRecord[]> {
  const { data } = await apiClient.get('/attendance/reports/student/records')
  return unwrapEmbeddedList<AttendanceRecord>(data, ['attendanceRecordEntryList'])
}

async function fetchStudentExcuseTickets(): Promise<ExcuseTicket[]> {
  try {
    const { data } = await apiClient.get('/attendance/excuses/me', {
      params: { page: 0, size: 50 },
    })
    return unwrapEmbeddedList<ExcuseTicket>(data, [
      'excuseTicketResponseList',
      'excuseTicketList',
    ])
  } catch (err: unknown) {
    const status = (err as { response?: { status?: number } })?.response?.status
    if (status === 404) return []
    throw err
  }
}

export function useStudentAttendanceRecords() {
  return useQuery<AttendanceRecord[]>({
    queryKey: studentRequestsKeys.records(),
    queryFn: fetchStudentAttendanceRecords,
    staleTime: 60 * 1000,
  })
}

export function useStudentExcuseTickets() {
  return useQuery<ExcuseTicket[]>({
    queryKey: studentRequestsKeys.excuseTickets(),
    queryFn: fetchStudentExcuseTickets,
    staleTime: 60 * 1000,
  })
}

export function useRequestLateCheckin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (lessonId: number) => {
      await apiClient.post(`/attendance/late-checkin/${lessonId}`, {})
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: studentRequestsKeys.records() })
    },
  })
}

export interface SubmitExcuseInput {
  lessonIds: number[]
  excuseType: ExcuseType
  comment: string | null
  file?: File | null
}

export function useSubmitStudentExcuse() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ lessonIds, excuseType, comment, file }: SubmitExcuseInput) => {
      if (file) {
        const form = new FormData()
        const requestJson = JSON.stringify({ lessonIds, excuseType, comment })
        form.append('request', new Blob([requestJson], { type: 'application/json' }))
        form.append('file', file, file.name)
        await apiClient.post('/attendance/excuses/with-file', form)
        return
      }
      await apiClient.post('/attendance/excuses', { lessonIds, excuseType, comment })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: studentRequestsKeys.excuseTickets() })
      queryClient.invalidateQueries({ queryKey: studentRequestsKeys.records() })
    },
  })
}

export function getHttpStatus(error: unknown): number {
  return (error as { response?: { status?: number } })?.response?.status ?? 0
}
