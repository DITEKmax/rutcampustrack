import type { AttendanceDay, AttendanceGraphPoint, AttendanceGraphRange, AttendanceMetricSet, AttendanceViewModel } from './attendance-view-model'

const weekdays = ['Вс', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб']
export function civilDate(date: string): Date { return new Date(`${date}T12:00:00Z`) }
export function shiftDate(date: string, amount: number): string {
  const value = civilDate(date)
  value.setUTCDate(value.getUTCDate() + amount)
  return value.toISOString().slice(0, 10)
}
export function monday(date: string): string { return shiftDate(date, -((civilDate(date).getUTCDay() + 6) % 7)) }
export function serverDate(serverNow?: string): string | null {
  if (!serverNow || !Number.isFinite(new Date(serverNow).getTime())) return null
  return new Date(serverNow).toLocaleDateString('sv-SE', { timeZone: 'Europe/Moscow' })
}
export function shortDate(date: string): string {
  return civilDate(date).toLocaleDateString('ru-RU', { timeZone: 'UTC', day: 'numeric', month: 'short' })
}
export function weekday(date: string): string { return weekdays[civilDate(date).getUTCDay()] ?? '' }
export function attendanceBounds(data: AttendanceViewModel): { from: string; to: string } | null {
  if (data.semester) return { from: data.semester.dateFrom, to: data.semester.dateTo }
  const dates = [...data.days.map((day) => day.date), ...data.graph.days.flatMap((point) => [point.dateFrom, point.dateTo]), ...data.graph.weeks.flatMap((point) => [point.dateFrom, point.dateTo])].sort()
  return dates.length ? { from: dates[0]!, to: dates[dates.length - 1]! } : null
}
export function calendarWeek(data: AttendanceViewModel, date: string): readonly AttendanceDay[] {
  const now = serverDate(data.serverNow)
  return Array.from({ length: 7 }, (_, index) => {
    const day = shiftDate(monday(date), index)
    return data.days.find((candidate) => candidate.date === day) ?? {
      date: day, weekday: weekday(day), dayNumber: String(civilDate(day).getUTCDate()),
      state: now && day > now ? 'FUTURE' : now === day ? 'CURRENT' : 'PAST', lessons: [],
    }
  })
}
function unknownMetrics(): AttendanceMetricSet {
  return { present: { count: 0, percent: null }, presentOrExcused: { count: 0, percent: null }, excused: { count: 0, percent: null }, absent: { count: 0, percent: null }, held: 0, planned: 0 }
}
export interface AttendancePeriod extends AttendanceGraphPoint { readonly missing: boolean; readonly outsideSemester: boolean; readonly unfinished: boolean }
/** Calendar slots preserve gaps. Supplied weekly values are never rebuilt from daily percentages. */
export function graphPeriods(data: AttendanceViewModel, range: AttendanceGraphRange): readonly AttendancePeriod[] {
  const bounds = attendanceBounds(data)
  if (!bounds) return []
  const step = range === 'days' ? 1 : 7
  const start = monday(bounds.from)
  const end = shiftDate(monday(bounds.to), 6)
  const now = serverDate(data.serverNow)
  const supplied = new Map(data.graph[range].map((point) => [range === 'days' ? point.dateFrom : monday(point.dateFrom), point]))
  const result: AttendancePeriod[] = []
  for (let from = start; from <= end; from = shiftDate(from, step)) {
    const to = shiftDate(from, step - 1)
    const point = supplied.get(from)
    result.push({
      ...(point ?? { id: `${range}:${from}`, metrics: unknownMetrics(), state: now && from > now ? 'FUTURE' as const : 'NO_DATA' as const }),
      dateFrom: from, dateTo: to, label: range === 'days' ? `${weekday(from)} ${shortDate(from)}` : `${shortDate(from)} – ${shortDate(to)}`,
      missing: !point, outsideSemester: to < bounds.from || from > bounds.to,
      unfinished: Boolean(now && from <= now && point?.state === 'FUTURE'),
    })
  }
  return result
}
export function initialPeriodPage(points: readonly AttendanceGraphPoint[], range: AttendanceGraphRange, serverNow?: string): number {
  const size = range === 'days' ? 7 : 6
  const now = serverDate(serverNow)
  let index = now ? points.findIndex((point) => point.dateFrom <= now && point.dateTo >= now) : -1
  if (index < 0 && now && points.length) index = now > points[points.length - 1]!.dateTo ? points.length - 1 : 0
  if (index < 0) index = Math.max(0, points.findIndex((point) => point.state === 'DATA'))
  return Math.floor(index / size)
}
export function periodState(point: AttendanceGraphPoint & { readonly missing?: boolean; readonly outsideSemester?: boolean; readonly unfinished?: boolean }): string {
  if (point.outsideSemester) return 'Вне семестра'
  if (point.state === 'FUTURE') return point.unfinished ? 'Пары ещё не завершены' : 'Будущий период'
  if (point.missing) return 'Нет учитываемых пар'
  if (point.state === 'NO_DATA') return 'Нет данных об отметках'
  if (!point.metrics.held) return 'Нет закрытых учитываемых пар'
  if (point.metrics.present.percent === null) return 'Нет данных об отметках'
  return 'Данные доступны'
}
