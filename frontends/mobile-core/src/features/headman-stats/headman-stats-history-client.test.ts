import { describe, expect, it, vi } from 'vitest'
import {
  HeadmanStatsApi,
  toHeadmanStatsReportRequest,
  toHeadmanStatsTrendReportRequest,
} from './headman-stats-client'
import {
  headmanStatsFixtureLessons,
  headmanStatsFixtureTrend,
  headmanStatsFixtureDetail,
} from '../../dev/headman-stats-fixtures'

function page(items: unknown[]) {
  return {
    page: 0,
    size: 50,
    totalElements: items.length,
    totalPages: 1,
    hasPrevious: false,
    hasNext: false,
    items,
  }
}
function response(value: unknown): Response {
  return new Response(JSON.stringify(value), { headers: { 'Content-Type': 'application/json' } })
}
describe('headman selected period and lesson contracts', () => {
  it('normalizes authoritative selected-semester personal sources and ticket counters, with truthful legacy absence', async () => {
    const fixture = headmanStatsFixtureDetail('overview-autumn', 11, 7)
    const api = new HeadmanStatsApi({
      accessToken: () => 'synthetic-token',
      fetcher: async () => response(fixture),
    })
    const result = await api.studentDetail(11, {
      latePage: 0,
      excusePage: 0,
      size: 20,
      semesterId: 7,
    })
    expect(result.sources).toEqual(fixture.sources)
    expect(result.lateCheckin).toEqual(fixture.lateCheckin)
    expect(result.excuse).toEqual(fixture.excuse)
    const legacy = new HeadmanStatsApi({
      accessToken: () => 'synthetic-token',
      fetcher: async () =>
        response({ ...fixture, sources: undefined, lateCheckin: undefined, excuse: undefined }),
    })
    const missing = await legacy.studentDetail(11, {
      latePage: 0,
      excusePage: 0,
      size: 20,
      semesterId: 7,
    })
    expect(missing.sources).toBeNull()
    expect(missing.lateCheckin).toBeNull()
    expect(missing.excuse).toBeNull()
  })
  it.each([
    { sources: { studentGeo: -1 } },
    { lateCheckin: { submitted: '1', approved: 0, rejected: 0 } },
    { excuse: { submitted: 2, approved: -1, rejected: 0 } },
  ])(
    'rejects malformed present personal aggregates rather than silently falling back: %j',
    async (bad) => {
      const api = new HeadmanStatsApi({
        accessToken: () => 'synthetic-token',
        fetcher: async () =>
          response({ ...headmanStatsFixtureDetail('overview-autumn', 11, 7), ...bad }),
      })
      await expect(api.studentDetail(11, { latePage: 0, excusePage: 0, size: 20 })).rejects.toThrow(
        'некорректные подробности',
      )
    },
  )
  it('keeps selected semester and whole-period DAY scope through personal reads and exports', async () => {
    const query = { mode: 'SEMESTER', semesterId: 8, bucketUnit: 'DAY' } as const
    const fetcher = vi.fn<typeof fetch>(async () =>
      response(headmanStatsFixtureTrend('overview-spring', query)),
    )
    const api = new HeadmanStatsApi({ accessToken: () => 'synthetic-token', fetcher })
    await api.studentTrend(11, query)
    expect(JSON.parse(String(fetcher.mock.calls[0]![1]?.body))).toEqual(query)
    expect(toHeadmanStatsTrendReportRequest(query, 'html').headmanStatsTrend).toMatchObject(query)
    expect(
      toHeadmanStatsReportRequest(
        { subjectId: null, lessonTypes: [], sorts: [], filters: [], semesterId: 8 },
        'xlsx',
      ).headmanStats,
    ).toMatchObject({ semesterId: 8 })
  })
  it('retains a cleared nullable mark and the independently approved ticket', async () => {
    const lesson = {
      ...headmanStatsFixtureLessons('history')[1]!,
      status: null,
      source: null,
      markRevision: null,
    }
    const api = new HeadmanStatsApi({
      accessToken: () => 'synthetic-token',
      fetcher: async () => response(page([lesson])),
    })
    const result = await api.studentLessons(11, { semesterId: 7, page: 0, size: 50 })
    expect(result.items[0]?.status).toBeNull()
    expect(result.items[0]?.approvedTicket?.status).toBe('APPROVED')
    expect(result.items[0]?.startsAt).toBe(lesson.startsAt)
  })
  it.each([
    { status: 'UNKNOWN' },
    { markRevision: null },
    { editable: 'true' },
    { endsAt: 'invalid' },
  ])('rejects unsafe history rows before any write control is shown: %j', async (bad) => {
    const api = new HeadmanStatsApi({
      accessToken: () => 'synthetic-token',
      fetcher: async () =>
        response(page([{ ...headmanStatsFixtureLessons('history')[1]!, ...bad }])),
    })
    await expect(api.studentLessons(11, { page: 0, size: 50 })).rejects.toThrow()
  })
  it('rejects an unknown active semester rather than allowing a fabricated selection', async () => {
    const api = new HeadmanStatsApi({
      accessToken: () => 'synthetic-token',
      fetcher: async () => response({ groupId: 2, activeSemesterId: 9, semesters: [] }),
    })
    await expect(api.semesters()).rejects.toThrow('каталог')
  })
})
