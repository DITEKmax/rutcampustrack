import { describe, expect, it } from 'vitest'
import { AdminDashboardClient } from './admin-dashboard-client'
import { normalizeAdminDashboardActivity } from './admin-dashboard-activity'
import {
  adminActivityChartPoints,
  adminActivityDelta,
  adminActivityMoscowDate,
  adminActivityValue,
} from './admin-dashboard-activity-view'
import { createAdminActivityFixture } from '../../dev/admin-dashboard-fixtures'

describe('Admin activity frozen wire and observation semantics', () => {
  it('requests the exact period/bucket and preserves known zero versus missing retained history', async () => {
    const seen: string[] = []
    const cacheModes: (RequestCache | undefined)[] = []
    const client = new AdminDashboardClient({
      accessToken: () => null,
      fetcher: async (input, init) => {
        seen.push(String(input))
        cacheModes.push(init?.cache)
        return Response.json(createAdminActivityFixture('all_time', 'week', 'coverage-empty'))
      },
    })
    const result = await client.getActivity('all_time', 'week')
    expect(seen).toEqual(['/api/academic/dashboard/activity?period=all_time&bucket=week'])
    expect(cacheModes).toEqual(['no-store'])
    expect(result.summary.range.from).toBeNull()
    expect(result.series).toHaveLength(12)
    expect(result.summary.metrics.mapOpens.value).toBeNull()
    expect(result.summary.metrics.mapOpens.state).toBe('INSUFFICIENT_HISTORY')
    const empty = normalizeAdminDashboardActivity(
      createAdminActivityFixture('today', 'day', 'empty'),
      'today',
      'day',
    )
    expect(adminActivityValue(empty.summary.metrics.positiveMarks, 'positiveMarks')).toBe('0')
    expect(adminActivityValue(result.summary.metrics.mapOpens, 'mapOpens')).toBe('—')
  })
  it('keeps a zero denominator as an unknown percent and a real zero numerator', () => {
    const activity = normalizeAdminDashboardActivity(
      createAdminActivityFixture('today', 'day', 'geo-zero'),
      'today',
      'day',
    )
    expect(activity.summary.metrics.geoMarks).toMatchObject({
      state: 'AVAILABLE',
      value: 0,
      numerator: 0,
      denominator: 0,
      percent: null,
    })
    expect(
      adminActivityChartPoints(activity, 'geoMarks').every((point) => point.value === null),
    ).toBe(true)
    const invalid = structuredClone(activity)
    Object.assign(invalid.summary.metrics.geoMarks, { percent: 0 })
    expect(() => normalizeAdminDashboardActivity(invalid, 'today', 'day')).toThrow()
  })
  it('does not render rounded unsafe JSON int64 or missing required nullable fields', () => {
    for (const mutation of [
      (value: ReturnType<typeof createAdminActivityFixture>) =>
        Object.assign(value.summary.metrics.requests, { value: Number.MAX_SAFE_INTEGER + 1 }),
      (value: ReturnType<typeof createAdminActivityFixture>) => {
        delete (value.summary.metrics.requests as unknown as Record<string, unknown>).denominator
      },
    ]) {
      const value = createAdminActivityFixture()
      mutation(value)
      expect(() => normalizeAdminDashboardActivity(value, 'today', 'day')).toThrow()
    }
  })
  it('rejects future/overlapping windows and incompatible echoed query/comparison', () => {
    const future = createAdminActivityFixture()
    Object.assign(future.series.at(-1)!.range, { to: '2026-10-09T09:30:00Z' })
    expect(() => normalizeAdminDashboardActivity(future, 'today', 'day')).toThrow()
    const overlap = createAdminActivityFixture()
    Object.assign(overlap.series[1]!.range, { from: overlap.series[0]!.range.from })
    expect(() => normalizeAdminDashboardActivity(overlap, 'today', 'day')).toThrow()
    expect(() =>
      normalizeAdminDashboardActivity(createAdminActivityFixture(), 'all_time', 'day'),
    ).toThrow()
    const allTime = createAdminActivityFixture('all_time')
    Object.assign(allTime.summary.metrics.requests, {
      comparison: createAdminActivityFixture().summary.metrics.requests.comparison,
    })
    expect(() => normalizeAdminDashboardActivity(allTime, 'all_time', 'day')).toThrow()
  })
  it('uses Moscow calendar bounds and geo percentage-point comparison rather than action-count delta', () => {
    const activity = createAdminActivityFixture()
    const points = adminActivityChartPoints(activity, 'positiveMarks')
    expect(adminActivityMoscowDate('2026-10-07T21:15:00Z')).toBe('2026-10-08')
    expect(points.at(-2)?.dateTo).toBe('2026-10-07')
    expect(points.at(-1)?.dateTo).toBe('2026-10-08')
    const geo = structuredClone(activity.summary.metrics.geoMarks)
    Object.assign(geo.comparison!, { absoluteDelta: 999, percentPointDelta: 4.5 })
    expect(adminActivityDelta(geo, 'geoMarks')).toMatchObject({ text: '+4,5%', tone: 'positive' })
    expect(adminActivityDelta(geo, 'geoMarks').text).not.toContain('999')
  })
  it('keeps unavailable comparisons gray, renders measured zero, and signs negative count deltas', () => {
    const activity = createAdminActivityFixture()
    const count = structuredClone(activity.summary.metrics.mapOpens)
    Object.assign(count.comparison!, { absoluteDelta: -7 })
    expect(adminActivityDelta(count, 'mapOpens')).toMatchObject({ text: '−7', tone: 'negative' })
    Object.assign(count.comparison!, { absoluteDelta: 0 })
    expect(adminActivityDelta(count, 'mapOpens')).toMatchObject({ text: '0', tone: 'neutral' })
    Object.assign(count.comparison!, { state: 'INSUFFICIENT_HISTORY', absoluteDelta: 7 })
    expect(adminActivityDelta(count, 'mapOpens')).toMatchObject({ text: '—', tone: 'neutral' })
    expect(
      adminActivityDelta(
        createAdminActivityFixture('today', 'day', 'geo-zero').summary.metrics.geoMarks,
        'geoMarks',
      ).text,
    ).toBe('—')
    expect(
      adminActivityDelta(
        createAdminActivityFixture('all_time').summary.metrics.mapOpens,
        'mapOpens',
      ).text,
    ).toBe('—')
  })
  it('rejects a late JSON activity body after the captured authority/session is invalidated', async () => {
    let current = true
    let finish!: (value: unknown) => void
    let started!: () => void
    const reading = new Promise<void>((resolve) => {
      started = resolve
    })
    const response = new Response('{}', { status: 200 })
    Object.defineProperty(response, 'json', {
      value: () => {
        started()
        return new Promise((resolve) => {
          finish = resolve
        })
      },
    })
    const client = new AdminDashboardClient({
      accessToken: () => null,
      assertCurrent: () => {
        if (!current) throw new Error('stale-session')
      },
      fetcher: async () => response,
    })
    const pending = client.getActivity('today', 'day')
    await reading
    current = false
    finish(createAdminActivityFixture())
    await expect(pending).rejects.toThrow('stale-session')
  })
})
