import { statisticsDay, type StatisticsAxisTick } from '../statistics/statistics-chart-geometry'

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

export function adminActivityGeometry(
  source: readonly AdminActivityChartPoint[],
  asOfDate: string,
  percent = false,
) {
  const end = statisticsDay(asOfDate)
  const points = source
    .flatMap((point) => {
      const from = statisticsDay(point.dateFrom),
        to = statisticsDay(point.dateTo)
      if (end === null || from === null || to === null || from > end || to < from) return []
      if (
        point.value !== null &&
        (!Number.isFinite(point.value) || point.value < 0 || (percent && point.value > 100))
      )
        return []
      return [
        { ...point, dateTo: new Date(Math.min(to, end) * DAY).toISOString().slice(0, 10), from },
      ]
    })
    .sort((a, b) => a.from - b.from)
  const first = points[0]?.from ?? 0,
    last = points.at(-1)?.from ?? first
  const values = points.flatMap((point) => (point.value === null ? [] : [point.value]))
  const largest = Math.max(1, ...values)
  const step = 10 ** Math.floor(Math.log10(largest))
  const maximum = percent ? 100 : Math.ceil(largest / step) * step
  const cells = points.map((point, index) => ({
    point,
    index,
    x:
      points.length === 1
        ? ADMIN_ACTIVITY_PLOT_WIDTH / 2
        : ((point.from - first) / Math.max(1, last - first)) * ADMIN_ACTIVITY_PLOT_WIDTH,
    y: point.value === null ? null : ADMIN_ACTIVITY_PLOT_HEIGHT * (1 - point.value / maximum),
  }))
  const runs: (typeof cells)[] = []
  for (const cell of cells) {
    if (cell.y === null) continue
    const previous = cells[cell.index - 1]
    const run = runs.at(-1)
    if (
      previous &&
      previous.y !== null &&
      (statisticsDay(previous.point.dateTo) ?? -Infinity) + 1 >= cell.point.from &&
      run?.at(-1)?.index === previous.index
    )
      run.push(cell)
    else runs.push([cell])
  }
  return {
    cells,
    maximum,
    paths: runs.map((run) =>
      run
        .map(
          (cell, index) => `${index === 0 ? 'M' : 'L'}${cell.x.toFixed(2)},${cell.y!.toFixed(2)}`,
        )
        .join(' '),
    ),
  }
}

export interface AdminActivityAxisTick extends StatisticsAxisTick {
  /** Exact civil-time anchor on the Moscow calendar, independent of data buckets. */
  readonly calendarDay: number
  readonly description: string
}
export function adminActivityTickCandidates(
  points: readonly AdminActivityChartPoint[],
): readonly (readonly AdminActivityAxisTick[])[] {
  const first = statisticsDay(points[0]?.dateFrom ?? '')
  const last = statisticsDay(points.at(-1)?.dateFrom ?? '')
  if (first === null || last === null || last < first) return []
  const days = last - first
  const withYear = points[0]?.dateFrom.slice(0, 4) !== points.at(-1)?.dateFrom.slice(0, 4)
  function ticks(count: number): readonly AdminActivityAxisTick[] {
    return Array.from({ length: count }, (_, index) => {
      const position = count === 1 ? 0.5 : index / (count - 1)
      const calendarDay = first! + days * (count === 1 ? 0 : position)
      const instant = new Date(Math.round(calendarDay * DAY)).toISOString()
      const date = instant.slice(0, 10)
      const time = instant
        .slice(11, 23)
        .replace(/:00\.000$/, '')
        .replace(/\.000$/, '')
      const fractional = !Number.isInteger(calendarDay)
      return {
        position,
        calendarDay,
        date: instant,
        label: shortDate(date, withYear),
        description: `${shortDate(date, true)}${fractional ? ', ' + time : ''} (Москва)${fractional ? ', временная опора' : ''}`,
      }
    })
  }
  if (days === 0) return [ticks(1)]
  const counts = Array.from(
    { length: Math.min(days + 1, 8) - 2 },
    (_, index) => Math.min(days + 1, 8) - index,
  )
  // Prefer exact whole-day intervals. If none with interiors fit, use exact fractional
  // anchors before falling back to endpoints; never round a date into a different x.
  return [
    ...counts.filter((count) => days % (count - 1) === 0),
    ...counts.filter((count) => days % (count - 1) !== 0),
    2,
  ].map(ticks)
}

/** Labels and the plot share equal centres, with measured inward gutters at both edges. */
export function fitAdminActivityTicks(
  candidates: readonly (readonly AdminActivityAxisTick[])[],
  width: number,
  labelWidths: Readonly<Record<string, number>>,
  gap: number,
) {
  const endpoints = candidates.at(-1) ?? []
  const gutter = Math.max(0, ...endpoints.map((tick) => (labelWidths[tick.label] ?? 0) / 2))
  const plotWidth = Math.max(0, width - 2 * gutter)
  for (const ticks of candidates) {
    let previousRight = -gap
    const fits = ticks.every((tick) => {
      const measured = labelWidths[tick.label]
      if (measured === undefined || !Number.isFinite(measured)) return false
      const left = gutter + tick.position * plotWidth - measured / 2
      const right = left + measured
      const fits = left >= -0.01 && right <= width + 0.01 && left - previousRight >= gap - 0.01
      previousRight = right
      return fits
    })
    if (fits) return { ticks, gutter, plotWidth, stacked: false }
  }
  return { ticks: endpoints, gutter, plotWidth, stacked: true }
}
export function shortDate(date: string, withYear = false): string {
  return `${date.slice(8, 10)}.${date.slice(5, 7)}${withYear ? '.' + date.slice(0, 4) : ''}`
}
export function adminActivityPointDate(point: AdminActivityChartPoint): string {
  const withYear = point.dateFrom.slice(0, 4) !== point.dateTo.slice(0, 4)
  return point.dateFrom === point.dateTo
    ? shortDate(point.dateFrom, withYear)
    : `${shortDate(point.dateFrom, withYear)} — ${shortDate(point.dateTo, withYear)}`
}
