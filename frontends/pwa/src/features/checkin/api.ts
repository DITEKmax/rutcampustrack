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

/**
 * Маппинг браузерного GeolocationPositionError на понятные сообщения.
 * Коды: 1 PERMISSION_DENIED, 2 POSITION_UNAVAILABLE, 3 TIMEOUT.
 */
export function mapGeolocationError(error: GeolocationPositionError): string {
  switch (error.code) {
    case 1:
      return 'Нет доступа к GPS. Разрешите доступ в настройках браузера'
    case 2:
      return 'Не удалось определить местоположение. Проверь GPS и попробуй ещё раз'
    case 3:
      return 'Слишком долго ищем GPS. Попробуй ещё раз в открытом пространстве'
    default:
      return 'Не удалось получить координаты'
  }
}
