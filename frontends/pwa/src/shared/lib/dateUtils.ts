/**
 * Date helpers shared by schedule & homework views.
 * All functions treat dates as local-calendar (no UTC conversion) — matches
 * backend LocalDate semantics.
 */

export const MONTH_ABBREV = [
  'янв', 'фев', 'мар', 'апр', 'мая', 'июн',
  'июл', 'авг', 'сен', 'окт', 'ноя', 'дек',
]

export const MONTH_FULL = [
  'января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
  'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря',
]

export const DAY_NAMES_SHORT = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']
export const DAY_NAMES_FULL = [
  'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота', 'Воскресенье',
]

/** Monday of the ISO week containing `date` (midnight local). */
export function getMonday(date: Date): Date {
  const d = new Date(date)
  const day = d.getDay()
  const diff = day === 0 ? -6 : 1 - day
  d.setDate(d.getDate() + diff)
  d.setHours(0, 0, 0, 0)
  return d
}

export function addDays(date: Date, days: number): Date {
  const d = new Date(date)
  d.setDate(d.getDate() + days)
  return d
}

/** YYYY-MM-DD in the local calendar — matches backend LocalDate serialization. */
export function formatDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** Parse a YYYY-MM-DD string into a local Date at midnight (no timezone drift). */
export function parseLocalDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, (m ?? 1) - 1, d ?? 1)
}

export function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
}

/** `5 апр` */
export function formatShortDate(date: Date): string {
  return `${date.getDate()} ${MONTH_ABBREV[date.getMonth()]}`
}

/** `5 апреля 2026` */
export function formatLongDate(date: Date): string {
  return `${date.getDate()} ${MONTH_FULL[date.getMonth()]} ${date.getFullYear()}`
}

/** Monday..Saturday as `5-10 апр` / `30 мар - 4 апр`. */
export function formatWeekRange(monday: Date): string {
  const saturday = addDays(monday, 5)
  const startDay = monday.getDate()
  const endDay = saturday.getDate()
  if (monday.getMonth() === saturday.getMonth()) {
    return `${startDay}-${endDay} ${MONTH_ABBREV[saturday.getMonth()]}`
  }
  return `${startDay} ${MONTH_ABBREV[monday.getMonth()]} - ${endDay} ${MONTH_ABBREV[saturday.getMonth()]}`
}
