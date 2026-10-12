import { describe, expect, it } from 'vitest'
import {
  adminActivityGeometry,
  adminActivityTickCandidates,
  fitAdminActivityTicks,
  type AdminActivityChartPoint,
} from './admin-activity-chart'
const point = (date: string, value: number | null): AdminActivityChartPoint => ({
  id: date,
  dateFrom: date,
  dateTo: date,
  value,
})
describe('Admin activity calendar graph', () => {
  it('never places future/invalid dates or percentages on the plot', () => {
    const graph = adminActivityGeometry(
      [
        point('2026-10-07', 50),
        point('2026-10-09', 80),
        point('2026-02-30', 1),
        point('2026-10-08', 101),
      ],
      '2026-10-08',
      true,
    )
    expect(graph.cells.map((cell) => cell.point.dateFrom)).toEqual(['2026-10-07'])
    expect(graph.maximum).toBe(100)
  })
  it('preserves real zero values and breaks lines across explicit unknown or omitted periods', () => {
    const graph = adminActivityGeometry(
      [
        point('2026-10-01', 0),
        point('2026-10-02', null),
        point('2026-10-03', 9),
        point('2026-10-05', 12),
      ],
      '2026-10-08',
    )
    expect(graph.cells[0]?.y).toBe(144)
    expect(graph.cells[1]?.y).toBeNull()
    expect(graph.paths).toHaveLength(3)
    expect(graph.paths.every((path) => !path.includes('NaN'))).toBe(true)
  })
  it('clips a current week label at the server observation date without adding a future bucket', () => {
    const graph = adminActivityGeometry(
      [{ id: 'week', dateFrom: '2026-10-05', dateTo: '2026-10-11', value: 5 }],
      '2026-10-08',
    )
    expect(graph.cells[0]?.point.dateTo).toBe('2026-10-08')
    expect(graph.cells[0]?.x).toBe(142)
  })
  it('prefers whole calendar intervals across a year boundary', () => {
    const candidates = adminActivityTickCandidates([
      point('2026-12-30', 1),
      point('2027-01-02', 2),
      point('2027-01-05', 3),
    ])
    const axis = fitAdminActivityTicks(
      candidates,
      330,
      Object.fromEntries(candidates.flat().map((tick) => [tick.label, 70])),
      12,
    )
    expect(axis.ticks.map((tick) => tick.position)).toEqual([0, 1 / 3, 2 / 3, 1])
    expect(axis.ticks.map((tick) => tick.label)).toEqual([
      '30.12.2026',
      '01.01.2027',
      '03.01.2027',
      '05.01.2027',
    ])
    expect(axis.ticks.every((tick) => Number.isInteger(tick.calendarDay))).toBe(true)
  })
  it('uses exact fractional time anchors with intermediate labels on prime-day spans and sparse data', () => {
    const candidates = adminActivityTickCandidates([point('2026-09-25', 1), point('2026-10-08', 3)])
    const axis = fitAdminActivityTicks(
      candidates,
      162,
      Object.fromEntries(candidates.flat().map((tick) => [tick.label, 36])),
      12,
    )
    expect(axis.ticks.map((tick) => tick.position)).toEqual([0, 0.5, 1])
    expect(axis.ticks[1]).toMatchObject({
      date: '2026-10-01T12:00:00.000Z',
      description: '01.10.2026, 12:00 (Москва), временная опора',
    })
    expect(axis.ticks[2]!.calendarDay - axis.ticks[1]!.calendarDay).toBe(
      axis.ticks[1]!.calendarDay - axis.ticks[0]!.calendarDay,
    )
    const graph = adminActivityGeometry(
      [point('2026-09-25', 1), point('2026-10-08', 3)],
      '2026-10-08',
    )
    expect(graph.cells.map((cell) => cell.x)).toEqual([0, 284])
    expect(graph.paths).toHaveLength(2)
  })
  it('keeps equally spaced label centres inside measured edges with larger font and varying label widths', () => {
    const candidates = adminActivityTickCandidates([point('2026-09-25', 1), point('2026-10-08', 3)])
    for (const width of [162, 235, 275]) {
      const widths = Object.fromEntries(
        candidates.flat().map((tick) => [tick.label, tick.label === '08.10' ? 42 : 38]),
      )
      const axis = fitAdminActivityTicks(candidates, width, widths, 15)
      expect(axis.stacked).toBe(false)
      expect(axis.ticks.length).toBeGreaterThanOrEqual(3)
      const centres = axis.ticks.map((tick) => axis.gutter + tick.position * axis.plotWidth)
      const intervals = centres.slice(1).map((centre, index) => centre - centres[index]!)
      for (const interval of intervals) expect(interval).toBeCloseTo(intervals[0]!, 6)
      axis.ticks.forEach((tick, index) => {
        expect(centres[index]! - widths[tick.label]! / 2).toBeGreaterThanOrEqual(0)
        expect(centres[index]! + widths[tick.label]! / 2).toBeLessThanOrEqual(width)
      })
    }
  })
})
