import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import { studentRequestsKeys } from '@/features/student/requests/studentRequestsApi'
import type { CheckinRequest } from './types'

export function useCheckin() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (coords: CheckinRequest) => {
      const { data } = await apiClient.post('/attendance/checkin', coords)
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: studentRequestsKeys.records() })
    },
  })
}

export function mapCheckinError(status: number): string {
  switch (status) {
    case 403:
      return 'Вы находитесь слишком далеко от аудитории'
    case 404:
      return 'Активное занятие не найдено'
    case 409:
      return 'Вы уже отметились на этой паре'
    case 422:
      return 'Окно отметки ещё не открыто или уже закрыто'
    case 429:
      return 'Слишком много попыток. Подождите немного.'
    default:
      return 'Ошибка сервера. Попробуйте ещё раз'
  }
}
