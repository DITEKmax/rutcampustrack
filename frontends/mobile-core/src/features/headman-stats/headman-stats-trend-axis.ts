import type { HeadmanStatsTrendMode, HeadmanStatsTrendPoint } from './headman-stats-client'

const day = 86_400_000
export interface HeadmanStatsTrendAxis {
  start: number
  end: number
  ticks: readonly { date: string; label: string; position: number }[]
  months: readonly { label: string; position: number }[]
}
export function headmanStatsDate(value: string | null | undefined): number | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const timestamp = Date.parse(value + 'T00:00:00Z')
  return Number.isFinite(timestamp) && new Date(timestamp).toISOString().slice(0, 10) === value
    ? timestamp
    : null
}
function iso(timestamp: number): string {
  return new Date(timestamp).toISOString().slice(0, 10)
}
export function headmanStatsTrendPosition(
  value: string,
  axis: HeadmanStatsTrendAxis,
): number | null {
  const timestamp = headmanStatsDate(value)
  if (timestamp === null || timestamp < axis.start || timestamp > axis.end) return null
  return axis.start === axis.end ? 0.5 : (timestamp - axis.start) / (axis.end - axis.start)
}
export function headmanStatsTrendAxis(input: {
  mode: HeadmanStatsTrendMode
  from?: string | null | undefined
  to?: string | null | undefined
  weekStart?: string | undefined
  points: readonly HeadmanStatsTrendPoint[]
  plotWidth?: number
  labelWidth?: number
}): HeadmanStatsTrendAxis | null {
  const dates = input.points
    .flatMap((point) => [headmanStatsDate(point.from), headmanStatsDate(point.to)])
    .filter((value): value is number => value !== null)
  let start = headmanStatsDate(input.from)
  let end = headmanStatsDate(input.to)
  if (input.mode === 'WEEK') {
    start = headmanStatsDate(input.weekStart)
    end = start === null ? null : start + 6 * day
  } else if (input.mode === 'SUBJECT' && dates.length) {
    start = Math.min(...dates)
    end = Math.max(...dates)
  }
  if (start === null || end === null || end < start) return null
  const ticks: number[] = []
  const months: { label: string; position: number }[] = []
  const position = (date: number) => (start === end ? 0.5 : (date - start!) / (end! - start!))
  if (input.mode === 'SEMESTER') {
    // Calendar divisions describe the fixed range, never attendance observations.
    ticks.push(start)
    let segment = start
    const cursor = new Date(start)
    cursor.setUTCDate(1)
    cursor.setUTCMonth(cursor.getUTCMonth() + 1)
    const monthFormat = new Intl.DateTimeFormat('ru-RU', { month: 'short', timeZone: 'UTC' })
    while (segment <= end) {
      const boundary = Math.min(cursor.getTime(), end + day)
      months.push({
        label: monthFormat.format(new Date(segment)).replace(/\.$/, ''),
        position: position(Math.min(end, (segment + boundary) / 2)),
      })
      ticks.push(Math.min(end, boundary))
      if (boundary > end) break
      segment = boundary
      cursor.setUTCMonth(cursor.getUTCMonth() + 1)
    }
  } else {
    const span = Math.round((end - start) / day)
    const width = Number.isFinite(input.plotWidth) && input.plotWidth! > 0 ? input.plotWidth! : 296
    const label =
      Number.isFinite(input.labelWidth) && input.labelWidth! > 0
        ? input.labelWidth!
        : input.mode === 'WEEK'
          ? 18
          : 50
    // A calendar step must divide the whole range: no shortened/merged tail.
    // Start/end labels occupy a full width toward their neighbour, interior labels half.
    // Prime spans intentionally retain only their two fixed endpoints.
    let intervals = Math.max(1, Math.min(span, Math.floor(width / (1.5 * label + 12))))
    while (intervals > 1 && span % intervals !== 0) intervals -= 1
    const step = span === 0 ? 1 : span / intervals
    for (let offset = 0; offset <= span; offset += step) ticks.push(start + offset * day)
  }
  const formatter = new Intl.DateTimeFormat(
    'ru-RU',
    input.mode === 'WEEK'
      ? { day: 'numeric', timeZone: 'UTC' }
      : { day: 'numeric', month: 'short', timeZone: 'UTC' },
  )
  return {
    start,
    end,
    months,
    ticks: [...new Set(ticks)].map((timestamp) => ({
      date: iso(timestamp),
      label: input.mode === 'SEMESTER' ? '' : formatter.format(timestamp),
      position: position(timestamp),
    })),
  }
}
