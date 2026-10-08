import { statisticsDay, statisticsFitTicks, type StatisticsAxisTick } from '../statistics/statistics-chart-geometry'

/** View data only; this is not an API DTO. A live adapter supplies server observation bounds. */
export interface AdminActivityChartPoint {
  readonly id: string
  readonly dateFrom: string
  readonly dateTo: string
  readonly value: number | null
  readonly detail?: string
}
export type AdminActivityChartRange = 'days' | 'weeks'
export const ADMIN_ACTIVITY_PLOT_WIDTH = 284
export const ADMIN_ACTIVITY_PLOT_HEIGHT = 144
const DAY = 86_400_000

export function adminActivityGeometry(source: readonly AdminActivityChartPoint[], asOfDate: string, percent = false) {
  const end = statisticsDay(asOfDate)
  const points = source.flatMap(point => {
    const from = statisticsDay(point.dateFrom), to = statisticsDay(point.dateTo)
    if (end === null || from === null || to === null || from > end || to < from) return []
    if (point.value !== null && (!Number.isFinite(point.value) || point.value < 0 || percent && point.value > 100)) return []
    return [{ ...point, dateTo: new Date(Math.min(to, end) * DAY).toISOString().slice(0, 10), from }]
  }).sort((a, b) => a.from - b.from)
  const first = points[0]?.from ?? 0, last = points.at(-1)?.from ?? first
  const values = points.flatMap(point => point.value === null ? [] : [point.value])
  const largest = Math.max(1, ...values)
  const step = 10 ** Math.floor(Math.log10(largest))
  const maximum = percent ? 100 : Math.ceil(largest / step) * step
  const cells = points.map((point, index) => ({
    point, index,
    x: points.length === 1 ? ADMIN_ACTIVITY_PLOT_WIDTH / 2 : (point.from - first) / Math.max(1, last - first) * ADMIN_ACTIVITY_PLOT_WIDTH,
    y: point.value === null ? null : ADMIN_ACTIVITY_PLOT_HEIGHT * (1 - point.value / maximum),
  }))
  const runs: (typeof cells)[] = []
  for (const cell of cells) {
    if (cell.y === null) continue
    const previous = cells[cell.index - 1]
    const run = runs.at(-1)
    if (previous && previous.y !== null && (statisticsDay(previous.point.dateTo) ?? -Infinity) + 1 >= cell.point.from && run?.at(-1)?.index === previous.index) run.push(cell)
    else runs.push([cell])
  }
  return { cells, maximum, paths: runs.map(run => run.map((cell, index) => `${index === 0 ? 'M' : 'L'}${cell.x.toFixed(2)},${cell.y!.toFixed(2)}`).join(' ')) }
}

export function adminActivityTickCandidates(points: readonly AdminActivityChartPoint[]): readonly (readonly StatisticsAxisTick[])[] {
  const first = statisticsDay(points[0]?.dateFrom ?? '')
  const last = statisticsDay(points.at(-1)?.dateFrom ?? '')
  if (first === null || last === null || last < first) return []
  const days = last - first
  const maximum = Math.min(points.length, 8)
  const withYear = points[0]?.dateFrom.slice(0, 4) !== points.at(-1)?.dateFrom.slice(0, 4)
  if (days === 0) return [[{ position: .5, label: shortDate(points[0]!.dateFrom, withYear), date: points[0]!.dateFrom }]]
  return Array.from({ length: Math.max(1, maximum - 1) }, (_, offset) => {
    const count = maximum - offset
    return Array.from({ length: count }, (_, index) => {
      const position = index / (count - 1)
      const date = new Date((first + Math.round(days * position)) * DAY).toISOString().slice(0, 10)
      return { position, label: shortDate(date, withYear), date }
    })
  })
}
export function shortDate(date: string, withYear = false): string {
  return `${date.slice(8, 10)}.${date.slice(5, 7)}${withYear ? '.' + date.slice(0, 4) : ''}`
}
export function adminActivityPointDate(point: AdminActivityChartPoint): string {
  const withYear = point.dateFrom.slice(0, 4) !== point.dateTo.slice(0, 4)
  return point.dateFrom === point.dateTo ? shortDate(point.dateFrom, withYear)
    : `${shortDate(point.dateFrom, withYear)} — ${shortDate(point.dateTo, withYear)}`
}
export { statisticsFitTicks as fitAdminActivityTicks }
