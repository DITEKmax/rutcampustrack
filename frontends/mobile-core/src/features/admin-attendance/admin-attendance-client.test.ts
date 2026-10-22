import { describe, expect, it } from 'vitest'
import { AdminAttendanceClient } from './admin-attendance-client'
import { StaleSessionGenerationError } from '../../shared/session-owner'
const semester = {
  id: '31',
  name: 'Весна',
  dateFrom: '2026-02-01',
  dateTo: '2026-06-30',
  active: false,
  archived: true,
}
const query = {
  semesterId: '31',
  search: 'АБ',
  sort: 'GROUP_NAME' as const,
  direction: 'ASC' as const,
  page: 0,
  size: 25,
}
function report() {
  return {
    semester,
    ...query,
    observedAt: '2026-10-15T10:00:00Z',
    totalElements: 2,
    totalPages: 1,
    rows: [
      {
        groupId: '7',
        groupName: 'АБ-1',
        countedLessons: 2,
        presentCount: 0,
        absentCount: 4,
        excusedCount: 0,
        opportunities: 4,
        presentPercent: 0,
        presentOrExcusedPercent: 0,
        dataState: 'AVAILABLE',
      },
      {
        groupId: '8',
        groupName: 'АБ-2',
        countedLessons: 0,
        presentCount: 0,
        absentCount: 0,
        excusedCount: 0,
        opportunities: 0,
        presentPercent: null,
        presentOrExcusedPercent: null,
        dataState: 'NO_DATA',
      },
    ],
  }
}
describe('admin attendance report transport', () => {
  it('uses the report-only catalogue and exact server selection without registry grants or metric recomputation', async () => {
    const seen: { url: string; cache?: RequestCache; authorization: string | null }[] = []
    const client = new AdminAttendanceClient({
      accessToken: () => 'synthetic',
      fetcher: async (url, init) => {
        seen.push({
          url: String(url),
          ...(init?.cache ? { cache: init.cache } : {}),
          authorization: new Headers(init?.headers).get('Authorization'),
        })
        return Response.json(String(url).endsWith('/semesters') ? { items: [semester] } : report())
      },
    })
    expect(await client.getSemesters()).toEqual([semester])
    const result = await client.getReport(query)
    expect(result.rows.map((row) => [row.dataState, row.presentPercent])).toEqual([
      ['AVAILABLE', 0],
      ['NO_DATA', null],
    ])
    expect(result.rows.map((row) => row.opportunities)).toEqual([4, 0])
    expect(seen.map((row) => row.url)).toEqual([
      '/api/academic/admin/attendance/semesters',
      '/api/academic/admin/attendance?semesterId=31&search=%D0%90%D0%91&sort=GROUP_NAME&direction=ASC&page=0&size=25',
    ])
    expect(
      seen.every((row) => row.cache === 'no-store' && row.authorization === 'Bearer synthetic'),
    ).toBe(true)
  })
  it('rejects foreign selection, missing metrics, unsafe counts and missing-data coercion', async () => {
    const invalid = [
      () => ({ ...report(), semester: { ...semester, id: '32' } }),
      () => ({ ...report(), sort: 'UNKNOWN' }),
      () => ({
        ...report(),
        rows: [{ ...report().rows[0], presentCount: Number.MAX_SAFE_INTEGER + 1 }],
      }),
      () => ({ ...report(), rows: [{ ...report().rows[1], presentPercent: 0 }] }),
      () => ({ ...report(), rows: [{ ...report().rows[0], presentPercent: undefined }] }),
    ]
    for (const value of invalid) {
      const client = new AdminAttendanceClient({
        accessToken: () => null,
        fetcher: async () => Response.json(value()),
      })
      await expect(client.getReport(query)).rejects.toThrow()
    }
  })
  it('checks current owner again after the body resolves and refreshes an expired admission at most once', async () => {
    let current = true,
      reads = 0,
      refreshes = 0
    const client = new AdminAttendanceClient({
      accessToken: () => null,
      assertCurrent: () => {
        if (!current) throw new StaleSessionGenerationError()
      },
      onUnauthorized: async () => {
        refreshes++
      },
      fetcher: async () => {
        reads++
        return new Response('', { status: 401 })
      },
    })
    await expect(client.getReport(query)).rejects.toMatchObject({ response: { status: 401 } })
    expect([reads, refreshes]).toEqual([2, 1])
    const late = new AdminAttendanceClient({
      accessToken: () => null,
      assertCurrent: () => {
        if (!current) throw new StaleSessionGenerationError()
      },
      fetcher: async () =>
        ({
          ok: true,
          status: 200,
          json: async () => {
            current = false
            return report()
          },
        }) as Response,
    })
    await expect(late.getReport(query)).rejects.toBeInstanceOf(StaleSessionGenerationError)
  })
})
