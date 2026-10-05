import { describe, expect, it } from 'vitest'
import { hasStatisticsMarks, statisticsGeometry, statisticsTickCells } from './statistics-chart-geometry'
import type { StatisticsSeriesPoint } from './statistics-view-model'
const metrics = { present: { count: 2, percent: 50 }, excused: { count: 1, percent: 25 }, absent: { count: 1, percent: 25 }, presentOrExcused: { count: 3, percent: 75 }, held: 4, planned: 6 }
function point(id: string, from: string, to = from, state: StatisticsSeriesPoint['state'] = 'DATA'): StatisticsSeriesPoint { return { id, dateFrom: from, dateTo: to, label: id, state, metrics } }
describe('statistics filled calendar geometry', () => {
  it('uses opaque server buckets and leaves actual missing dates empty', () => {
    const result = statisticsGeometry([point('opaque', '2026-09-01'), point('not-a-date', '2026-09-04')])
    expect(result.cells[0]?.left).toBe(0)
    expect(result.cells[1]?.right).toBe(284)
    expect(result.calendarGaps).toEqual([{ left: 71, right: 213 }])
    for (const band of result.bands) expect(band.paths).toHaveLength(2)
  })
  it('breaks all stacked bands at unknown, nullable and future periods', () => {
    const unknown = { ...point('nullable', '2026-09-03'), metrics: { ...metrics, excused: { count: 1, percent: null } } }
    const points = [point('a', '2026-09-01'), point('b', '2026-09-02', undefined, 'NO_DATA'), unknown, point('c', '2026-09-04'), point('d', '2026-09-05', undefined, 'FUTURE')]
    expect(hasStatisticsMarks(unknown)).toBe(false)
    for (const band of statisticsGeometry(points).bands) expect(band.paths).toHaveLength(2)
  })
  it('uses authoritative present and combined rounded percentages without denominator recalculation', () => {
    const supplied = { ...point('weighted', '2026-09-01'), metrics: { ...metrics, present: { count: 999, percent: 33.3 }, excused: { count: 777, percent: 16.7 }, absent: { count: 3, percent: 50 }, presentOrExcused: { count: 1776, percent: 50 } } }
    const result = statisticsGeometry([supplied])
    expect(result.bands[0]?.paths[0]).toContain('0.00,96.05')
    expect(result.bands[1]?.paths[0]).toContain('0.00,72.00')
    expect(result.bands[2]?.paths[0]).toContain('0.00,0.00')
  })
  it('uses the server combined boundary when individual thirds sum differently', () => {
    const thirds = { ...point('thirds', '2026-09-01'), metrics: { ...metrics, present: { count: 1, percent: 33.33 }, excused: { count: 1, percent: 33.33 }, absent: { count: 1, percent: 33.33 }, presentOrExcused: { count: 2, percent: 66.67 } } }
    expect(statisticsGeometry([thirds]).bands[1]?.paths[0]).toContain('0.00,48.00')
  })
  it('keeps zero-held and invalid dates out of filled regions; labels stay readable', () => {
    const zero = { ...point('zero', '2026-09-01'), metrics: { ...metrics, held: 0 } }
    const invalid = point('bad', '2026-02-30')
    expect(statisticsGeometry([zero, invalid]).bands.every((band) => band.paths.length === 0)).toBe(true)
    const sparse = statisticsGeometry([point('a', '2026-09-01'), point('b', '2026-09-02'), point('c', '2026-09-03'), point('d', '2026-09-20'), point('e', '2026-10-01')])
    expect(statisticsTickCells(sparse.cells).map((cell) => cell.point.id)).toEqual(['a', 'd', 'e'])
  })
})
