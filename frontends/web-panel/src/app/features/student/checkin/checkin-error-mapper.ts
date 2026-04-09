/**
 * Map HTTP status code → user-facing Russian error message per UI-SPEC §Copy.
 *
 * Copy is taken verbatim from
 * .planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-UI-SPEC.md
 * (Copywriting Contract → /student/checkin table).
 *
 * Used by StudentCheckinComponent when a POST to /api/attendance/checkin
 * fails — the error status is passed through this mapper and the resulting
 * string is rendered inline under the primary CTA.
 */
export function mapCheckinError(status: number): string {
  switch (status) {
    case 403:
      return 'Геоотметка заблокирована преподавателем.';
    case 404:
      return 'Активное занятие не найдено.';
    case 409:
      return 'Вы уже отмечены на этом занятии.';
    case 422:
      return 'Вы находитесь слишком далеко от кампуса. Геоотметка недоступна.';
    case 429:
      return 'Слишком много попыток. Подождите минуту и попробуйте снова.';
    default:
      return 'Не удалось отправить отметку. Попробуйте ещё раз.';
  }
}

/**
 * Shown when navigator.geolocation.getCurrentPosition rejects (permission
 * denied, unavailable, timeout). Same string for every error code — the
 * user-visible remediation is identical.
 */
export const GPS_DENIED_MESSAGE =
  'Нет доступа к геолокации. Разрешите доступ в настройках браузера и попробуйте снова.';
