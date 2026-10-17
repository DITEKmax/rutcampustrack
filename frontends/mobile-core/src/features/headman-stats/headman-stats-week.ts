import { headmanStatsDate } from './headman-stats-trend-axis'
const day = 86_400_000
function monday(value: number): number {
  return value - ((new Date(value).getUTCDay() + 6) % 7) * day
}
function iso(value: number): string {
  return new Date(value).toISOString().slice(0, 10)
}
export function headmanStatsToday(generatedAt: string | null | undefined): string {
  const parsed = generatedAt ? Date.parse(generatedAt) : NaN
  const date = new Date(Number.isFinite(parsed) ? parsed : Date.now())
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Moscow',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date)
  const value = (type: string) => parts.find((part) => part.type === type)!.value
  return `${value('year')}-${value('month')}-${value('day')}`
}
export function headmanStatsWeekBounds(
  from: string | null | undefined,
  to: string | null | undefined,
  today: string,
): { first: string; last: string } | null {
  const start = headmanStatsDate(from)
  const end = headmanStatsDate(to)
  const now = headmanStatsDate(today)
  if (start === null || end === null || now === null || end < start || now < start) return null
  return { first: iso(monday(start)), last: iso(monday(Math.min(end, now))) }
}
export function headmanStatsWeekSelection(
  date: string,
  bounds: ReturnType<typeof headmanStatsWeekBounds>,
): string | null {
  const parsed = headmanStatsDate(date)
  if (parsed === null || bounds === null) return null
  const selected = iso(monday(parsed))
  return selected >= bounds.first && selected <= bounds.last ? selected : null
}
export function headmanStatsStepWeek(
  date: string,
  direction: -1 | 1,
  bounds: ReturnType<typeof headmanStatsWeekBounds>,
): string | null {
  const parsed = headmanStatsDate(date)
  return parsed === null
    ? null
    : headmanStatsWeekSelection(iso(parsed + direction * 7 * day), bounds)
}
