import { describe, expect, it } from 'vitest'
import { hasStatisticsMarks, statisticsGeometry, statisticsAxisCandidates, statisticsFitTicks } from './statistics-chart-geometry'
import type { StatisticsSeriesPoint } from './statistics-view-model'
const metrics = { present: { count: 2, percent: 50 }, excused: { count: 1, percent: 25 }, absent: { count: 1, percent: 25 }, presentOrExcused: { count: 3, percent: 75 }, held: 4, planned: 6 }
function point(id: string, from: string, to = from, state: StatisticsSeriesPoint['state'] = 'DATA'): StatisticsSeriesPoint { return { id, dateFrom: from, dateTo: to, label: id, state, metrics } }
describe('statistics filled calendar geometry', () => {
  it('connects DATA across omitted no-lesson days without deriving new metrics', () => {
    const result = statisticsGeometry([point('opaque', '2026-09-01'), point('not-a-date', '2026-09-04')])
    expect(result.cells[0]?.left).toBe(0)
    expect(result.cells[1]?.right).toBe(284)
    for (const band of result.bands) {
      expect(band.paths).toHaveLength(1)
      expect(band.paths[0]).toContain('35.50,')
      expect(band.paths[0]).toContain('248.50,')
    }
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
  it('keeps zero-held and invalid dates out of filled regions', () => {
    const zero = { ...point('zero', '2026-09-01'), metrics: { ...metrics, held: 0 } }
    const invalid = point('bad', '2026-02-30')
    expect(statisticsGeometry([zero, invalid]).bands.every((band) => band.paths.length === 0)).toBe(true)

  })
})


describe('statistics adaptive measured calendar axis', () => {
  const points = [point('opaque-start', '2026-12-28'), point('opaque-end', '2027-01-17')]
  it('retains exact date endpoints across years and places every tick at equal domain intervals', () => {
    const candidates = statisticsAxisCandidates(points, 'days')
    const widths = Object.fromEntries(candidates.flatMap((ticks) => ticks.map((tick) => [tick.label, 62])))
    const result = statisticsFitTicks(candidates, 284, widths, 8)
    expect(result.stacked).toBe(false)
    expect(result.ticks[0]?.label).toBe('28.12.2026')
    expect(result.ticks.at(-1)?.label).toBe('17.01.2027')
    expect(result.ticks[0]?.position).toBe(0)
    expect(result.ticks.at(-1)?.position).toBe(1)
    const step = 1 / (result.ticks.length - 1)
    result.ticks.forEach((tick, index) => expect(tick.position).toBeCloseTo(index * step))
  })
  it('reduces count for actual larger font/narrower container and never overlaps or overflows', () => {
    const candidates = statisticsAxisCandidates(points, 'days')
    const measured = (width: number) => Object.fromEntries(candidates.flatMap((ticks) => ticks.map((tick) => [tick.label, width])))
    const normal = statisticsFitTicks(candidates, 284, measured(60), 8)
    const enlarged = statisticsFitTicks(candidates, 180, measured(75), 10)
    expect(enlarged.ticks.length).toBeLessThan(normal.ticks.length)
    expect(enlarged.ticks).toHaveLength(2)
    expect(enlarged.stacked).toBe(false)
    for (const result of [normal, enlarged]) {
      const width = result === normal ? 284 : 180, labelWidth = result === normal ? 60 : 75, gap = result === normal ? 8 : 10
      let previousRight = -gap
      result.ticks.forEach((tick, index) => {
        const left = index === 0 ? 0 : index === result.ticks.length - 1 ? width - labelWidth : width * tick.position - labelWidth / 2
        expect(left).toBeGreaterThanOrEqual(0)
        expect(left + labelWidth).toBeLessThanOrEqual(width)
        expect(left - previousRight).toBeGreaterThanOrEqual(gap)
        previousRight = left + labelWidth
      })
    }
    const cramped = statisticsFitTicks(candidates, 100, measured(75), 10)
    expect(cramped.stacked).toBe(true)
    expect(cramped.ticks.map((tick) => tick.label)).toEqual(['28.12.2026', '17.01.2027'])
  })
  it('numbers actual Monday weeks through omitted buckets independently of opaque identifiers', () => {
    const weeks = statisticsAxisCandidates([point('ID:999', '2026-12-30', '2027-01-03'), point('ID:1', '2027-01-11', '2027-01-17')], 'weeks')
    expect(weeks[0]?.map((tick) => tick.label)).toEqual(['1', '2', '3'])
    expect(weeks[0]?.map((tick) => tick.date)).toEqual(['2026-12-28', '2027-01-04', '2027-01-11'])
    const geometry = statisticsGeometry([point('a', '2026-12-28', '2027-01-03'), point('b', '2027-01-11', '2027-01-17')])
    for (const band of geometry.bands) expect(band.paths).toHaveLength(1)
  })
  it('anchors weeks to partial first semester Monday and retains semester bounds beyond returned lessons', () => {
    const bounds = { semesterStartsOn: '2026-09-02', semesterEndsOn: '2026-09-27' }
    const candidates = statisticsAxisCandidates([point('late', '2026-09-14', '2026-09-20')], 'weeks', bounds)
    expect(candidates[0]?.map((tick) => tick.label)).toEqual(['1', '2', '3', '4'])
    expect(candidates[0]?.[0]?.date).toBe('2026-08-31')
    const days = statisticsAxisCandidates([point('late', '2026-09-14')], 'days', bounds)
    expect(days[0]?.[0]?.label).toBe('02.09')
    expect(days[0]?.at(-1)?.label).toBe('27.09')
    const geometry = statisticsGeometry([point('late', '2026-09-14')], bounds)
    expect(geometry.cells[0]?.left).toBeGreaterThan(0)
    expect(geometry.cells[0]?.right).toBeLessThan(284)
  })
  it('handles single valid period and empty/invalid date domain without fabricated labels', () => {
    expect(statisticsAxisCandidates([], 'days')).toEqual([])
    expect(statisticsAxisCandidates([point('invalid', '2026-02-30')], 'days')).toEqual([])
    const candidates = statisticsAxisCandidates([point('single', '2026-09-01')], 'days')
    expect(statisticsFitTicks(candidates, 180, { '01.09': 36 }, 8)).toEqual({ ticks: [{ position: 0.5, label: '01.09', date: '2026-09-01' }], stacked: false })
  })
})
