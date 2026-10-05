import type { StatisticsGraphRange, StatisticsSeriesPoint } from './statistics-view-model'

const DAY = 86_400_000
export const STATISTICS_PLOT_WIDTH = 284
export const STATISTICS_PLOT_HEIGHT = 144
export type StatisticsBand = 'present' | 'excused' | 'absent'
export const STATISTICS_BANDS = ['present', 'excused', 'absent'] as const

/** Date bounds come only from server buckets. Opaque IDs and the wall clock are never used. */
export function statisticsDay(value: string): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const date = Date.parse(`${value}T00:00:00Z`)
  return Number.isFinite(date) && new Date(date).toISOString().slice(0, 10) === value ? date / DAY : null
}
export function hasStatisticsMarks(point: StatisticsSeriesPoint): boolean {
  return point.state === 'DATA' && point.metrics.held > 0
    && point.metrics.presentOrExcused.percent !== null && Number.isFinite(point.metrics.presentOrExcused.percent)
    && STATISTICS_BANDS.every((band) => point.metrics[band].percent !== null && Number.isFinite(point.metrics[band].percent))
}
export function statisticsPointState(point: StatisticsSeriesPoint): string {
  if (point.state === 'FUTURE') return 'Будущий период'
  if (point.state === 'NO_DATA') return 'Нет данных'
  return hasStatisticsMarks(point) ? '' : 'Нет данных об отметках'
}
export interface StatisticsSemesterBounds {
  readonly range?: StatisticsGraphRange
  readonly semesterStartsOn?: string
  readonly semesterEndsOn?: string
}
function calendarDomain(dates: readonly { from: number; to: number }[], bounds: StatisticsSemesterBounds) {
  const first = dates.length ? Math.min(...dates.map((date) => date.from)) : 0
  const last = dates.length ? Math.max(...dates.map((date) => date.to)) : first
  const start = statisticsDay(bounds.semesterStartsOn ?? '') ?? first
  const end = statisticsDay(bounds.semesterEndsOn ?? '') ?? last
  return end >= start ? { start, end } : { start: first, end: last }
}
export function statisticsGeometry(points: readonly StatisticsSeriesPoint[], bounds: StatisticsSemesterBounds = {}) {
  const dates = points.map((point) => ({ from: statisticsDay(point.dateFrom), to: statisticsDay(point.dateTo) }))
  const validDates = dates.filter((date): date is { from: number; to: number } => date.from !== null && date.to !== null && date.to >= date.from)
  const domain = calendarDomain(validDates, bounds)
  const monday = (value: number) => value - (new Date(value * DAY).getUTCDay() + 6) % 7
  const start = bounds.range === 'weeks' ? monday(domain.start) : domain.start
  const end = bounds.range === 'weeks' ? monday(domain.end) + 7 : domain.end + 1
  const width = STATISTICS_PLOT_WIDTH
  const height = STATISTICS_PLOT_HEIGHT
  const x = (value: number) => (value - start) / (end - start) * width
  const cells = points.map((point, index) => {
    const date = dates[index]!
    const valid = date.from !== null && date.to !== null && date.to >= date.from
    const left = valid ? x(date.from!) : index / Math.max(1, points.length) * width
    const right = valid ? x(date.to! + 1) : (index + 1) / Math.max(1, points.length) * width
    return { point, index, left, right, center: (left + right) / 2, validDate: valid }
  })
  const runs: (typeof cells)[] = []
  for (const cell of cells) {
    if (!hasStatisticsMarks(cell.point) || !cell.validDate) continue
    const previous = cells[cell.index - 1]
    const currentRun = runs.at(-1)
    // Omitted no-lesson buckets connect DATA. Explicit unknown/future/null buckets still break the run.
    if (previous && currentRun?.at(-1)?.index === previous.index) currentRun.push(cell)
    else runs.push([cell])
  }
  function boundary(point: StatisticsSeriesPoint, level: number): number {
    const percent = level === 0 ? 0 : level === 1 ? point.metrics.present.percent! : level === 2 ? point.metrics.presentOrExcused.percent! : 100
    return height * (1 - Math.max(0, Math.min(100, percent)) / 100)
  }
  const bands = STATISTICS_BANDS.map((band, index) => ({
    band,
    paths: runs.map((run) => {
      const first = run[0]!
      const last = run.at(-1)!
      const coords = (level: number) => [
        [first.left, boundary(first.point, level)],
        ...run.map((cell) => [cell.center, boundary(cell.point, level)]),
        [last.right, boundary(last.point, level)],
      ]
      const top = coords(index + 1)
      const bottom = coords(index).reverse()
      return `M ${[...top, ...bottom].map((coord) => coord.map((value) => value!.toFixed(2)).join(',')).join(' L ')} Z`
    }),
  }))
  return { cells, bands }
}

export interface StatisticsAxisTick {
  readonly position: number
  readonly label: string
  readonly date: string
}

/** Calendar domain includes omitted buckets; weekly numbering uses Monday weeks, never opaque IDs. */
export function statisticsAxisCandidates(points: readonly StatisticsSeriesPoint[], range: StatisticsGraphRange, bounds: StatisticsSemesterBounds = {}): readonly (readonly StatisticsAxisTick[])[] {
  const dates = points.flatMap((point) => {
    const from = statisticsDay(point.dateFrom), to = statisticsDay(point.dateTo)
    return from !== null && to !== null && to >= from ? [{ from, to }] : []
  })
  if (!dates.length) return []
  const { start, end } = calendarDomain(dates, bounds)
  const monday = (value: number) => value - (new Date(value * DAY).getUTCDay() + 6) % 7
  const from = range === 'weeks' ? monday(start) : start
  const to = range === 'weeks' ? monday(end) : end
  const periods = Math.round((to - from) / (range === 'weeks' ? 7 : 1)) + 1
  const withYear = new Date(start * DAY).getUTCFullYear() !== new Date(end * DAY).getUTCFullYear()
  const iso = (value: number) => new Date(value * DAY).toISOString().slice(0, 10)
  // Bound candidate generation, not the rendered tick count. Mobile content width is already constrained.
  return Array.from({ length: Math.min(periods, 128) - (periods > 1 ? 1 : 0) }, (_, offset) => {
    const count = periods === 1 ? 1 : Math.min(periods, 128) - offset
    return Array.from({ length: count }, (_, index) => {
      const position = count === 1 ? 0.5 : index / (count - 1)
      const period = Math.round(position * (periods - 1))
      const date = iso(from + period * (range === 'weeks' ? 7 : 1))
      const label = range === 'weeks' ? String(period + 1) : `${date.slice(8, 10)}.${date.slice(5, 7)}${withYear ? '.' + date.slice(0, 4) : ''}`
      return { position, label, date }
    })
  })
}

/** Measured DOM widths include actual font/root sizing. Endpoints anchor inward; interiors are equally spaced. */
export function statisticsFitTicks(candidates: readonly (readonly StatisticsAxisTick[])[], width: number, labelWidths: Readonly<Record<string, number>>, gap: number) {
  for (const ticks of candidates) {
    let previousRight = -gap
    const fits = ticks.every((tick, index) => {
      const measured = labelWidths[tick.label]
      if (measured === undefined || !Number.isFinite(measured)) return false
      const left = ticks.length === 1 ? (width - measured) / 2 : index === 0 ? 0 : index === ticks.length - 1 ? width - measured : tick.position * width - measured / 2
      const right = left + measured
      const fits = left >= 0 && right <= width && left - previousRight >= gap - 0.01
      previousRight = right
      return fits
    })
    if (fits) return { ticks, stacked: false }
  }
  // Extremely narrow surfaces keep both endpoint labels on separate rows rather than dropping one.
  return { ticks: candidates.at(-1) ?? [], stacked: true }
}
