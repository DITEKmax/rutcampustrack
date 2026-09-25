import { describe, expect, it, vi } from 'vitest'
import { validateTicketRequest } from '../../shared/report-download-client'
import {
  HeadmanStatsApi,
  toHeadmanStatsTrendReportRequest,
  type HeadmanStatsTrendFormat,
} from './headman-stats-client'

const pngFormat: HeadmanStatsTrendFormat = {
  code: 'png',
  label: 'PNG',
  contentType: 'image/png',
  extension: 'png',
}

describe('headman trend downloads', () => {
  it('uses the direct PWA export route and verifies an actual PNG response', async () => {
    const fetcher = vi.fn<typeof fetch>(async () => new Response(
      new Uint8Array([0x89, 0x50, 0x4e, 0x47]),
      {
        status: 200,
        headers: {
          'Content-Type': 'image/png',
          'Content-Disposition': 'attachment; filename="headman-stats-trend.png"',
        },
      },
    ))
    const api = new HeadmanStatsApi({ accessToken: () => 'session', fetcher })

    const result = await api.downloadTrendExport(
      { mode: 'WEEK', weekStart: '2026-09-21' },
      pngFormat,
    )

    expect(result.filename).toBe('headman-stats-trend.png')
    expect(result.contentType).toBe('image/png')
    expect((await result.blob.arrayBuffer()).byteLength).toBe(4)
    const [path, init] = fetcher.mock.calls[0]!
    expect(path).toBe('/api/attendance/reports/headman/stats/trend/export')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({
      query: { mode: 'WEEK', weekStart: '2026-09-21' },
      format: 'png',
    })
  })

  it('maps the selected trend to a closed native ticket selector without group or recipient overrides', () => {
    const request = toHeadmanStatsTrendReportRequest({
      mode: 'SUBJECT',
      subjectId: 12,
      lessonTypes: ['LECTURE', 'LAB'],
    }, 'html')

    expect(request).toEqual({
      kind: 'HEADMAN_STATS_TREND',
      headmanStatsTrend: {
        mode: 'SUBJECT',
        subjectId: 12,
        lessonTypes: ['LECTURE', 'LAB'],
        format: 'html',
      },
    })
    validateTicketRequest(request)
    expect(Object.keys(request.headmanStatsTrend)).not.toContain('groupId')
    expect(Object.keys(request.headmanStatsTrend)).not.toContain('recipientId')
  })
})
