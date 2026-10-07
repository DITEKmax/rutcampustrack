import type { HeadmanStatsTrendMode, HeadmanStatsTrendPoint } from './headman-stats-client'

const day = 86_400_000
export interface HeadmanStatsTrendAxis { start: number; end: number; ticks: readonly { date: string; label: string; position: number }[] }
export function headmanStatsDate(value: string | null | undefined): number | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const timestamp = Date.parse(value + 'T00:00:00Z')
  return Number.isFinite(timestamp) && new Date(timestamp).toISOString().slice(0, 10) === value ? timestamp : null
}
function iso(timestamp: number): string { return new Date(timestamp).toISOString().slice(0, 10) }
export function headmanStatsTrendPosition(value: string, axis: HeadmanStatsTrendAxis): number | null {
  const timestamp = headmanStatsDate(value)
  if (timestamp === null || timestamp < axis.start || timestamp > axis.end) return null
  return axis.start === axis.end ? 0.5 : (timestamp - axis.start) / (axis.end - axis.start)
}
export function headmanStatsTrendAxis(input: { mode: HeadmanStatsTrendMode; from?: string | null | undefined; to?: string | null | undefined; weekStart?: string | undefined; points: readonly HeadmanStatsTrendPoint[] }): HeadmanStatsTrendAxis | null {
  const dates = input.points.flatMap(point => [headmanStatsDate(point.from), headmanStatsDate(point.to)]).filter((value): value is number => value !== null)
  let start = headmanStatsDate(input.from); let end = headmanStatsDate(input.to)
  if (input.mode === 'WEEK') { start = headmanStatsDate(input.weekStart); end = start === null ? null : start + 6 * day }
  else if (input.mode === 'SUBJECT' && dates.length) { start = Math.min(...dates); end = Math.max(...dates) }
  if (start === null || end === null || end < start) return null
  const ticks: number[] = []
  if (input.mode === 'WEEK') for (let index = 0; index < 7; index += 1) ticks.push(start + index * day)
  else if (input.mode === 'SEMESTER') {
    // Tick dates describe the calendar, never attendance observations.
    ticks.push(start)
    const cursor = new Date(start); cursor.setUTCDate(1); cursor.setUTCMonth(cursor.getUTCMonth() + 1)
    while (cursor.getTime() <= end && ticks.length < 13) { ticks.push(cursor.getTime()); cursor.setUTCMonth(cursor.getUTCMonth() + 1) }
  } else {
    const span = Math.round((end - start) / day); const intervals = Math.min(4, span)
    for (let index = 0; index <= intervals; index += 1) ticks.push(start + (intervals ? Math.round(span * index / intervals) : 0) * day)
  }
  const formatter = new Intl.DateTimeFormat('ru-RU', input.mode === 'SEMESTER' ? { month: 'short', timeZone: 'UTC' } : input.mode === 'WEEK' ? { day: 'numeric', timeZone: 'UTC' } : { day: 'numeric', month: 'short', timeZone: 'UTC' })
  const axis: HeadmanStatsTrendAxis = { start, end, ticks: [] }
  return { ...axis, ticks: [...new Set(ticks)].map(timestamp => ({ date: iso(timestamp), label: formatter.format(timestamp), position: start === end ? 0.5 : (timestamp - start) / (end - start) })) }
}
