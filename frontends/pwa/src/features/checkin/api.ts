import { useMutation } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import type { CheckinRequest } from './types'

export function useCheckin() {
  return useMutation({
    mutationFn: async (coords: CheckinRequest) => {
      const { data } = await apiClient.post('/attendance/checkin', coords)
      return data
    },
  })
}

export function mapCheckinError(status: number): string {
  switch (status) {
    case 403:
      return 'Геоотметка заблокирована преподавателем'
    case 404:
      return 'Активное занятие не найдено'
    case 409:
      return 'Вы уже отмечены на этом занятии'
    case 422:
      return 'Вы находитесь вне зоны отметки'
    case 429:
      return 'Слишком много попыток. Подождите минуту'
    default:
      return 'Ошибка сервера. Попробуйте ещё раз'
  }
}
