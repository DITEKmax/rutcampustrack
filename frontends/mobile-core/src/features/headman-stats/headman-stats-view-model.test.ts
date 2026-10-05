import { describe, expect, it } from 'vitest'
import { headmanStatsSeries } from './headman-stats-view-model'

describe('headman stacked chart counters', () => {
  it('preserves real dates and derives three disjoint bands from shared counters', () => {
    const [point] = headmanStatsSeries([{ key: 'week1', label: 'Неделя 1', from: '2026-09-01', to: '2026-09-06', present: { numerator: 60, denominator: 100, percent: 60 }, presentOrExcused: { numerator: 85, denominator: 100, percent: 85 } }])
    expect(point?.metrics.present.count).toBe(60)
    expect(point?.metrics.excused.count).toBe(25)
    expect(point?.metrics.absent.count).toBe(15)
    expect(point?.dateFrom).toBe('2026-09-01')
    expect(point?.dateTo).toBe('2026-09-06')
  })
  it.each([{ present: 0, combined: 0, first: 0, second: 0 }, { present: 6, combined: 5, first: 10, second: 10 }, { present: 6, combined: 8, first: 10, second: 12 }])('keeps unknown or incompatible denominators as no-data gaps: %j', ({ present, combined, first, second }) => {
    const [point] = headmanStatsSeries([{ key: 'week', label: 'Неделя', from: '2026-09-07', to: '2026-09-13', present: { numerator: present, denominator: first, percent: null }, presentOrExcused: { numerator: combined, denominator: second, percent: null } }])
    expect(point?.state).toBe('NO_DATA')
    expect(point?.metrics.excused.percent).toBeNull()
    expect(point?.metrics.absent.percent).toBeNull()
  })
})
