import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import type { ExcuseType } from '@/api/schema'

export type { ExcuseType }

export const EXCUSE_TYPES: { value: ExcuseType; label: string }[] = [
  { value: 'ILLNESS', label: 'Болезнь' },
  { value: 'SUMMONS', label: 'Повестка' },
  { value: 'UNIVERSITY_ORDER', label: 'Приказ вуза' },
  { value: 'EXEMPTION', label: 'Освобождение' },
  { value: 'FREE_ATTENDANCE', label: 'Свободное посещение' },
  { value: 'OTHER', label: 'Другое' },
]

interface CreateExcusePayload {
  lessonIds: number[]
  excuseType: ExcuseType
  comment?: string
  file?: File | null
}

async function postExcuse(payload: CreateExcusePayload) {
  const body = {
    lessonIds: payload.lessonIds,
    excuseType: payload.excuseType,
    comment: payload.comment ?? null,
  }
  if (payload.file) {
    const fd = new FormData()
    fd.append(
      'request',
      new Blob([JSON.stringify(body)], { type: 'application/json' }),
    )
    fd.append('file', payload.file)
    const { data } = await apiClient.post('/attendance/excuses/with-file', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  }
  const { data } = await apiClient.post('/attendance/excuses', body)
  return data
}

export function useCreateExcuse() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: postExcuse,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['student-dashboard'] })
      qc.invalidateQueries({ queryKey: ['schedule'] })
    },
  })
}

export function useRequestLateCheckin() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (lessonId: number) => {
      const { data } = await apiClient.post(
        `/attendance/late-checkin/${lessonId}`,
      )
      return data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['schedule'] })
    },
  })
}

export function useBlockLesson() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (lessonId: number) => {
      const { data } = await apiClient.post(
        `/schedule/lessons/${lessonId}/blockage`,
      )
      return data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['schedule'] })
    },
  })
}

export function useUnblockLesson() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (lessonId: number) => {
      const { data } = await apiClient.delete(
        `/schedule/lessons/${lessonId}/blockage`,
      )
      return data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['schedule'] })
    },
  })
}

export function mapLessonActionError(status: number | undefined): string {
  switch (status) {
    case 403:
      return 'Нет прав на это действие'
    case 404:
      return 'Пара не найдена'
    case 409:
      return 'Уже отправлено'
    case 422:
      return 'Действие недоступно для этой пары'
    case 429:
      return 'Слишком часто, подожди минуту'
    default:
      return 'Ошибка сервера. Попробуй позже'
  }
}
