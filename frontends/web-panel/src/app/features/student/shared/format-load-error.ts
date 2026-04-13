import { HttpErrorResponse } from '@angular/common/http';

/**
 * BUG-008: общий helper для понятных error-сообщений на student-страницах.
 * Ранее везде возвращалось одно generic-сообщение и невозможно было понять причину.
 * Теперь — добавляем код статуса и хинт по типу ошибки, console.error остаётся
 * за вызывающей стороной (он даёт полный stack для DevTools).
 */
export function formatLoadError(err: unknown, prefix = 'Не удалось загрузить данные.'): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return `${prefix} Нет связи с сервером.`;
    }
    if (err.status === 401 || err.status === 403) {
      return `${prefix} Сессия истекла или нет прав. Войдите снова.`;
    }
    if (err.status >= 500) {
      return `${prefix} Сервер временно недоступен (${err.status}).`;
    }
    return `${prefix} (HTTP ${err.status})`;
  }
  return prefix;
}
