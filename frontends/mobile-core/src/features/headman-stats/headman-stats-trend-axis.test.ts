import { describe, expect, it } from 'vitest'
import { headmanStatsDate, headmanStatsTrendAxis, headmanStatsTrendPosition } from './headman-stats-trend-axis'
import type { HeadmanStatsTrendPoint } from './headman-stats-client'
function point(from: string, to = from): HeadmanStatsTrendPoint { return { key: from, label: from, from, to, present: { numerator: 0, denominator: 0, percent: null }, presentOrExcused: { numerator: 0, denominator: 0, percent: null } } }
describe('headman calendar trend axis', () => {
  it('uses UTC month boundaries and elapsed date spacing without creating observations', () => {
    const points = [point('2026-09-01'), point('2026-09-02'), point('2027-01-31')]
    const axis = headmanStatsTrendAxis({ mode: 'SEMESTER', from: '2026-09-01', to: '2027-01-31', points })!
    expect(axis.ticks.map(tick => tick.date)).toEqual(['2026-09-01','2026-10-01','2026-11-01','2026-12-01','2027-01-01'])
    expect(headmanStatsTrendPosition('2026-09-02',axis)).toBeCloseTo(1/152)
    expect(headmanStatsTrendPosition('2027-01-31',axis)).toBe(1)
    expect(points.every(point => point.present.percent === null)).toBe(true)
  })
  it('keeps seven daily ticks across a year boundary even when only two days have observations', () => {
    const axis = headmanStatsTrendAxis({ mode: 'WEEK', weekStart: '2026-12-28', points: [point('2026-12-29'),point('2027-01-02')] })!
    expect(axis.ticks.map(tick => tick.date)).toEqual(['2026-12-28','2026-12-29','2026-12-30','2026-12-31','2027-01-01','2027-01-02','2027-01-03'])
    expect(axis.ticks.map(tick => tick.label)).toEqual(['28','29','30','31','1','2','3'])
    expect(axis.ticks.map(tick => tick.position)).toEqual([0,1/6,2/6,3/6,4/6,5/6,1])
  })
  it('bounds subject ticks, includes both actual endpoints and spaces irregular points by date', () => {
    const axis = headmanStatsTrendAxis({ mode: 'SUBJECT', points: [point('2026-10-20'),point('2026-10-02'),point('2026-10-01')] })!
    expect(axis.ticks).toHaveLength(5)
    expect(axis.ticks[0]?.date).toBe('2026-10-01'); expect(axis.ticks.at(-1)?.date).toBe('2026-10-20')
    expect(headmanStatsTrendPosition('2026-10-02',axis)).toBeCloseTo(1/19)
    expect(headmanStatsTrendPosition('2026-09-30',axis)).toBeNull()
  })
  it('handles leap dates, a single date, invalid ranges and no observations without NaN coordinates', () => {
    expect(headmanStatsDate('2026-02-29')).toBeNull(); expect(headmanStatsDate('2028-02-29')).not.toBeNull()
    const single = headmanStatsTrendAxis({mode:'SUBJECT',points:[point('2028-02-29')]})!
    expect(single.ticks).toHaveLength(1); expect(single.ticks[0]?.position).toBe(0.5)
    expect(headmanStatsTrendAxis({mode:'SEMESTER',from:'2026-10-01',to:'2026-09-01',points:[]})).toBeNull()
    expect(headmanStatsTrendAxis({mode:'SUBJECT',points:[]})).toBeNull()
  })
})
