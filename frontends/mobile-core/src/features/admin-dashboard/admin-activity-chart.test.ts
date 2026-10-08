import { describe, expect, it } from 'vitest'
import { adminActivityGeometry, adminActivityTickCandidates, type AdminActivityChartPoint } from './admin-activity-chart'
const point = (date: string, value: number | null): AdminActivityChartPoint => ({ id: date, dateFrom: date, dateTo: date, value })
describe('Admin activity calendar graph', () => {
  it('never places future/invalid dates or percentages on the plot', () => {
    const graph = adminActivityGeometry([point('2026-10-07', 50), point('2026-10-09', 80), point('2026-02-30', 1), point('2026-10-08', 101)], '2026-10-08', true)
    expect(graph.cells.map(cell => cell.point.dateFrom)).toEqual(['2026-10-07'])
    expect(graph.maximum).toBe(100)
  })
  it('preserves real zero values and breaks lines across explicit unknown or omitted periods', () => {
    const graph = adminActivityGeometry([point('2026-10-01', 0), point('2026-10-02', null), point('2026-10-03', 9), point('2026-10-05', 12)], '2026-10-08')
    expect(graph.cells[0]?.y).toBe(144)
    expect(graph.cells[1]?.y).toBeNull()
    expect(graph.paths).toHaveLength(3)
    expect(graph.paths.every(path => !path.includes('NaN'))).toBe(true)
  })
  it('clips a current week label at the server observation date without adding a future bucket', () => {
    const graph = adminActivityGeometry([{ id: 'week', dateFrom: '2026-10-05', dateTo: '2026-10-11', value: 5 }], '2026-10-08')
    expect(graph.cells[0]?.point.dateTo).toBe('2026-10-08')
    expect(graph.cells[0]?.x).toBe(142)
  })
  it('anchors visible date labels at uniform positions across a year boundary', () => {
    const candidates = adminActivityTickCandidates([point('2026-12-30', 1), point('2027-01-02', 2), point('2027-01-05', 3)])
    expect(candidates[0]?.map(tick => tick.position)).toEqual([0, .5, 1])
    expect(candidates[0]?.map(tick => tick.label)).toEqual(['30.12.2026', '02.01.2027', '05.01.2027'])
  })
})
