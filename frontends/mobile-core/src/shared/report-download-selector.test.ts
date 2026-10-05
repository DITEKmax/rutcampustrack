import { describe, expect, it } from 'vitest'
import { ReportDownloadClient, validateTicketRequest, type ReportDownloadTicketRequest } from './report-download-client'

describe('headman selected semester export boundary', () => {
  it.each(['HEADMAN_STATS', 'HEADMAN_STATS_TREND'] as const)('sends the exact selected semester for %s', async (kind) => {
    const request: ReportDownloadTicketRequest = kind === 'HEADMAN_STATS'
      ? { kind, headmanStats: { semesterId: 12, format: 'xlsx' } }
      : { kind, headmanStatsTrend: { semesterId: 12, bucketUnit: 'WEEK', mode: 'SEMESTER', format: 'png' } }
    let sent: unknown
    const client = new ReportDownloadClient({ accessToken: () => null, assertCurrent: () => undefined,
      fetcher: async (_input, init) => {
        sent = JSON.parse(String(init?.body))
        return new Response(JSON.stringify({ downloadPath: `/api/report-download/${'a'.repeat(43)}`, expiresAt: new Date(Date.now() + 60000).toISOString(), suggestedFilename: kind === 'HEADMAN_STATS' ? 'headman-stats.xlsx' : 'headman-stats-trend.png' }), { headers: { 'Content-Type': 'application/json' } })
      },
    })
    await client.issueTicket(request)
    expect(sent).toEqual(request)
  })
  it.each([0, -1, 1.5, '12', null])('rejects invalid selected semester %s rather than dropping it', (semesterId) => {
    for (const request of [
      { kind: 'HEADMAN_STATS', headmanStats: { semesterId, format: 'xlsx' } },
      { kind: 'HEADMAN_STATS_TREND', headmanStatsTrend: { semesterId, mode: 'SEMESTER', format: 'png' } },
    ]) expect(() => validateTicketRequest(request as ReportDownloadTicketRequest)).toThrow(RangeError)
  })
  it('accepts DAY and WEEK, rejects an unsupported bucket and preserves legacy optional defaults', () => {
    for (const bucketUnit of ['DAY', 'WEEK', undefined]) {
      expect(() => validateTicketRequest({ kind: 'HEADMAN_STATS_TREND', headmanStatsTrend: { mode: 'SEMESTER', format: 'png', ...(bucketUnit ? { bucketUnit } : {}) } } as ReportDownloadTicketRequest)).not.toThrow()
    }
    expect(() => validateTicketRequest({ kind: 'HEADMAN_STATS_TREND', headmanStatsTrend: { mode: 'SEMESTER', format: 'png', bucketUnit: 'MONTH' } } as unknown as ReportDownloadTicketRequest)).toThrow(RangeError)
    expect(() => validateTicketRequest({ kind: 'HEADMAN_STATS', headmanStats: { format: 'pdf' } })).not.toThrow()
  })
})
