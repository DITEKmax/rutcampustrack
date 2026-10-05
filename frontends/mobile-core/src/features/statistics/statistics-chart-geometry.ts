import type { StatisticsSeriesPoint } from './statistics-view-model'

const DAY = 86_400_000
export const STATISTICS_PLOT_WIDTH = 284
export const STATISTICS_PLOT_HEIGHT = 144
export type StatisticsBand = 'present' | 'excused' | 'absent'
export const STATISTICS_BANDS = ['present', 'excused', 'absent'] as const

/** Date bounds come only from server buckets. Opaque IDs and the wall clock are never used. */
function day(value: string): number | null {
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
export function statisticsGeometry(points: readonly StatisticsSeriesPoint[]) {
  const dates = points.map((point) => ({ from: day(point.dateFrom), to: day(point.dateTo) }))
  const validDates = dates.filter((date): date is { from: number; to: number } => date.from !== null && date.to !== null && date.to >= date.from)
  const start = validDates.length ? Math.min(...validDates.map((date) => date.from)) : 0
  const end = validDates.length ? Math.max(...validDates.map((date) => date.to + 1)) : Math.max(1, points.length)
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
    // A gap in the calendar is a break even if both surrounding buckets have data.
    if (previous && currentRun?.at(-1)?.index === previous.index && Math.abs(previous.right - cell.left) < 0.001) currentRun.push(cell)
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
  const calendarGaps = cells.slice(1).flatMap((cell, index) => cell.left > cells[index]!.right + 0.001 ? [{ left: cells[index]!.right, right: cell.left }] : [])
  return { cells, bands, calendarGaps }
}

/** Decimate by calendar position, leaving all periods accessible via the plot slider. */
export function statisticsTickCells(cells: ReturnType<typeof statisticsGeometry>['cells'], capacity = 4) {
  if (!cells.length) return []
  const first = cells[0]!.center
  const last = cells[cells.length - 1]!.center
  const candidates = cells.length <= capacity ? cells : Array.from({ length: capacity }, (_, index) => {
    const target = first + (last - first) * index / (capacity - 1)
    return cells.reduce((closest, cell) => Math.abs(cell.center - target) < Math.abs(closest.center - target) ? cell : closest)
  })
  const displayed: typeof cells = []
  const anchored = (center: number) => Math.max(STATISTICS_PLOT_WIDTH / 8, Math.min(STATISTICS_PLOT_WIDTH * 7 / 8, center))
  for (const cell of candidates) {
    const previous = displayed.at(-1)
    if (!previous || anchored(cell.center) - anchored(previous.center) >= STATISTICS_PLOT_WIDTH * 0.22) displayed.push(cell)
  }
  return displayed
}
