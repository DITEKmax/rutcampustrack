import { describe, expect, it } from 'vitest'
import {
  headmanStatsDate,
  headmanStatsTrendAxis,
  headmanStatsTrendPosition,
} from './headman-stats-trend-axis'
import {
  headmanStatsToday,
  headmanStatsWeekBounds,
  headmanStatsWeekSelection,
  headmanStatsStepWeek,
} from './headman-stats-week'
import type { HeadmanStatsTrendPoint } from './headman-stats-client'
function point(from: string, to = from): HeadmanStatsTrendPoint {
  return {
    key: from,
    label: from,
    from,
    to,
    present: { numerator: 0, denominator: 0, percent: null },
    presentOrExcused: { numerator: 0, denominator: 0, percent: null },
  }
}
describe('headman calendar trend axis', () => {
  it('uses UTC month boundaries and elapsed date spacing without creating observations', () => {
    const points = [point('2026-09-01'), point('2026-09-02'), point('2027-01-31')]
    const axis = headmanStatsTrendAxis({
      mode: 'SEMESTER',
      from: '2026-09-01',
      to: '2027-01-31',
      points,
    })!
    expect(axis.ticks.map((tick) => tick.date)).toEqual([
      '2026-09-01',
      '2026-10-01',
      '2026-11-01',
      '2026-12-01',
      '2027-01-01',
      '2027-01-31',
    ])
    expect(axis.months.map((month) => month.label)).toEqual(['сент', 'окт', 'нояб', 'дек', 'янв'])
    expect(headmanStatsTrendPosition('2026-09-02', axis)).toBeCloseTo(1 / 152)
    expect(headmanStatsTrendPosition('2027-01-31', axis)).toBe(1)
    expect(points.every((point) => point.present.percent === null)).toBe(true)
  })
  it('keeps seven daily ticks across a year boundary even when only two days have observations', () => {
    const axis = headmanStatsTrendAxis({
      mode: 'WEEK',
      weekStart: '2026-12-28',
      points: [point('2026-12-29'), point('2027-01-02')],
    })!
    expect(axis.ticks.map((tick) => tick.date)).toEqual([
      '2026-12-28',
      '2026-12-29',
      '2026-12-30',
      '2026-12-31',
      '2027-01-01',
      '2027-01-02',
      '2027-01-03',
    ])
    expect(axis.ticks.map((tick) => tick.label)).toEqual(['28', '29', '30', '31', '1', '2', '3'])
    expect(axis.ticks.map((tick) => tick.position)).toEqual([
      0,
      1 / 6,
      2 / 6,
      3 / 6,
      4 / 6,
      5 / 6,
      1,
    ])
  })
  it('bounds subject ticks, includes both actual endpoints and spaces irregular points by date', () => {
    const axis = headmanStatsTrendAxis({
      mode: 'SUBJECT',
      points: [point('2026-10-20'), point('2026-10-02'), point('2026-10-01')],
    })!
    expect(axis.ticks).toHaveLength(2)
    expect(axis.ticks[0]?.date).toBe('2026-10-01')
    expect(axis.ticks.at(-1)?.date).toBe('2026-10-20')
    expect(headmanStatsTrendPosition('2026-10-02', axis)).toBeCloseTo(1 / 19)
    expect(headmanStatsTrendPosition('2026-09-30', axis)).toBeNull()
  })
  it('handles leap dates, a single date, invalid ranges and no observations without NaN coordinates', () => {
    expect(headmanStatsDate('2026-02-29')).toBeNull()
    expect(headmanStatsDate('2028-02-29')).not.toBeNull()
    const single = headmanStatsTrendAxis({ mode: 'SUBJECT', points: [point('2028-02-29')] })!
    expect(single.ticks).toHaveLength(1)
    expect(single.ticks[0]?.position).toBe(0.5)
    expect(
      headmanStatsTrendAxis({ mode: 'SEMESTER', from: '2026-10-01', to: '2026-09-01', points: [] }),
    ).toBeNull()
    expect(headmanStatsTrendAxis({ mode: 'SUBJECT', points: [] })).toBeNull()
  })
})

describe('measured axis and elapsed academic weeks', () => {
  it('uses a measured regular step, keeps endpoints and gets sparser when labels or root font grow', () => {
    const input = { mode: 'SUBJECT' as const, points: [point('2026-10-01'), point('2026-10-25')] }
    const wide = headmanStatsTrendAxis({ ...input, plotWidth: 300, labelWidth: 32 })!
    const narrow = headmanStatsTrendAxis({ ...input, plotWidth: 180, labelWidth: 52 })!
    expect(wide.ticks.map((tick) => tick.date)).toEqual([
      '2026-10-01',
      '2026-10-07',
      '2026-10-13',
      '2026-10-19',
      '2026-10-25',
    ])
    expect(narrow.ticks.map((tick) => tick.date)).toEqual([
      '2026-10-01',
      '2026-10-13',
      '2026-10-25',
    ])
    expect(wide.ticks.length).toBeGreaterThan(narrow.ticks.length)
    expect(narrow.ticks[0]?.position).toBe(0)
    expect(narrow.ticks.at(-1)?.position).toBe(1)
  })
  it('adapts seven day ticks to available measured width while retaining week endpoints', () => {
    const axis = headmanStatsTrendAxis({
      mode: 'WEEK',
      weekStart: '2026-09-28',
      points: [],
      plotWidth: 100,
      labelWidth: 24,
    })!
    expect(axis.ticks.map((tick) => tick.date)).toEqual(['2026-09-28', '2026-10-01', '2026-10-04'])
    expect(axis.ticks.map((tick) => tick.label)).toEqual(['28', '1', '4'])
  })
  it('bounds partial first weeks, current Moscow week and past semesters without future navigation', () => {
    const today = headmanStatsToday('2026-10-04T22:30:00Z')
    expect(today).toBe('2026-10-05')
    const bounds = headmanStatsWeekBounds('2026-09-01', '2027-01-31', today)!
    expect(bounds).toEqual({ first: '2026-08-31', last: '2026-10-05' })
    expect(headmanStatsStepWeek(bounds.first, -1, bounds)).toBeNull()
    expect(headmanStatsStepWeek(bounds.last, 1, bounds)).toBeNull()
    expect(headmanStatsWeekSelection('2026-10-01', bounds)).toBe('2026-09-28')
    expect(headmanStatsWeekSelection('2026-10-12', bounds)).toBeNull()
    expect(headmanStatsWeekBounds('2027-02-01', '2027-06-30', today)).toBeNull()
    expect(headmanStatsWeekBounds('2026-02-01', '2026-06-30', today)?.last).toBe('2026-06-29')
  })
})

it('keeps every partial semester month including a final first day with distinct calendar boundaries', () => {
  const axis = headmanStatsTrendAxis({
    mode: 'SEMESTER',
    from: '2026-09-13',
    to: '2027-01-01',
    points: [],
  })!
  expect(axis.months.map((month) => month.label)).toEqual(['сент', 'окт', 'нояб', 'дек', 'янв'])
  expect(axis.ticks.map((tick) => tick.date)).toEqual([
    '2026-09-13',
    '2026-10-01',
    '2026-11-01',
    '2026-12-01',
    '2027-01-01',
  ])
  expect(axis.ticks[0]?.position).toBe(0)
  expect(axis.ticks.at(-1)?.position).toBe(1)
})

it('divides the whole span evenly instead of creating a wider final interval', () => {
  const axis = headmanStatsTrendAxis({
    mode: 'SUBJECT',
    points: [point('2026-10-01'), point('2026-10-15')],
    plotWidth: 300,
    labelWidth: 50,
  })!
  expect(axis.ticks.map((tick) => tick.date)).toEqual(['2026-10-01', '2026-10-08', '2026-10-15'])
  for (let index = 1; index < axis.ticks.length; index++)
    expect(
      (axis.ticks[index]!.position - axis.ticks[index - 1]!.position) * 300,
    ).toBeGreaterThanOrEqual(62)
})

it('uses only fixed endpoints for a prime span and preserves every observation position', () => {
  const axis = headmanStatsTrendAxis({
    mode: 'SUBJECT',
    points: [point('2026-10-01'), point('2026-10-02'), point('2026-10-20')],
    plotWidth: 300,
    labelWidth: 50,
  })!
  expect(axis.ticks.map((tick) => tick.date)).toEqual(['2026-10-01', '2026-10-20'])
  expect(headmanStatsTrendPosition('2026-10-02', axis)).toBeCloseTo(1 / 19)
})
it('keeps equal integral calendar and screen intervals within the measured budget for composite and prime spans', () => {
  for (let span = 1; span <= 120; span++)
    for (const width of [120, 180, 300])
      for (const labelWidth of [18, 50]) {
        const from = '2026-01-01'
        const to = new Date(Date.parse(from + 'T00:00:00Z') + span * 86400000)
          .toISOString()
          .slice(0, 10)
        const axis = headmanStatsTrendAxis({
          mode: 'SUBJECT',
          points: [point(from), point(to)],
          plotWidth: width,
          labelWidth,
        })!
        expect(axis.ticks[0]?.date).toBe(from)
        expect(axis.ticks.at(-1)?.date).toBe(to)
        const step = (axis.end - axis.start) / (axis.ticks.length - 1)
        expect(step % 86400000).toBe(0)
        for (let index = 1; index < axis.ticks.length; index++) {
          expect(
            Date.parse(axis.ticks[index]!.date) - Date.parse(axis.ticks[index - 1]!.date),
          ).toBe(step)
          const gap = (axis.ticks[index]!.position - axis.ticks[index - 1]!.position) * width
          expect(gap).toBeCloseTo(width / (axis.ticks.length - 1))
          expect(gap + 1e-9).toBeGreaterThanOrEqual(1.5 * labelWidth + 12)
        }
      }
})

it('fits endpoint-only date ticks to the explicit narrow axis budget', () => {
  const axis = headmanStatsTrendAxis({
    mode: 'SUBJECT',
    points: [point('2026-09-01'), point('2027-01-31')],
    plotWidth: 166,
    labelWidth: 64,
  })!
  expect(axis.ticks.map((tick) => tick.date)).toEqual(['2026-09-01', '2027-01-31'])
  expect(axis.ticks.map(tick => tick.position)).toEqual([0, 1])
  expect(headmanStatsTrendPosition('2026-11-16', axis)).toBeCloseTo(76 / 152)
})
