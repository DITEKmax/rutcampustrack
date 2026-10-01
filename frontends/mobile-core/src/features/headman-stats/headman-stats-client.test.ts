import { describe, expect, it, vi } from 'vitest'
import { validateTicketRequest } from '../../shared/report-download-client'
import {
  HeadmanStatsApi,
  HeadmanStatsApiError,
  toHeadmanStatsReportRequest,
  toHeadmanStatsTrendReportRequest,
  type HeadmanStatsExportQuery,
  type HeadmanStatsFormat,
  type HeadmanStatsTrendFormat,
} from './headman-stats-client'

const pngFormat: HeadmanStatsTrendFormat = {
  code: 'png',
  label: 'PNG',
  contentType: 'image/png',
  extension: 'png',
}

const statsPdf: HeadmanStatsFormat = {
  code: 'pdf', label: 'PDF', contentType: 'application/pdf', extension: 'pdf',
}
const statsQuery: HeadmanStatsExportQuery = {
  subjectId: 12,
  lessonTypes: ['LECTURE', 'LAB'],
  sorts: [{ field: 'displayName', descending: false }],
  filters: [{ field: 'displayName', contains: 'Иван' }],
}

describe('headman statistics downloads', () => {
  it('preserves the selected block in both transports and returns the actual server filename', async () => {
    const filename = 'headman-stats-group2-20260901-20261001.pdf'
    const fetcher = vi.fn<typeof fetch>(async () => new Response('%PDF-fixture', {
      headers: {
        'Content-Type': 'application/pdf',
        'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
      },
    }))
    const api = new HeadmanStatsApi({ accessToken: () => 'session', fetcher })
    const downloaded = await api.downloadExport(statsQuery, statsPdf)
    expect(downloaded.filename).toBe(filename)
    expect(downloaded.contentType).toBe(statsPdf.contentType)
    expect(await downloaded.blob.text()).toBe('%PDF-fixture')
    const [path, init] = fetcher.mock.calls[0]!
    expect(path).toBe('/api/attendance/reports/headman/stats/export')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({ ...statsQuery, format: 'pdf' })

    const ticket = toHeadmanStatsReportRequest(statsQuery, 'pdf')
    expect(ticket).toEqual({ kind: 'HEADMAN_STATS', headmanStats: { ...statsQuery, format: 'pdf' } })
    validateTicketRequest(ticket)
  })

  it.each([
    { body: '', type: 'application/pdf', filename: 'stats.pdf' },
    { body: 'unexpected HTML', type: 'text/html', filename: 'stats.pdf' },
    { body: '%PDF-fixture', type: 'application/pdf', filename: 'stats.xlsx' },
  ])('rejects an empty or mismatched file instead of offering it for download: $type $filename', async (file) => {
    const api = new HeadmanStatsApi({ accessToken: () => 'session', fetcher: vi.fn<typeof fetch>(async () =>
      new Response(file.body, { headers: {
        'Content-Type': file.type,
        'Content-Disposition': `attachment; filename="${file.filename}"`,
      } }),
    ) })
    await expect(api.downloadExport(statsQuery, statsPdf)).rejects.toThrow(Error)
  })

  it('preserves an HTTP error and its public explanation instead of downloading the problem JSON', async () => {
    const detail = 'Сервис отчётов временно недоступен.'
    const api = new HeadmanStatsApi({ accessToken: () => 'session', fetcher: vi.fn<typeof fetch>(async () =>
      new Response(JSON.stringify({ detail }), { status: 503,
        headers: { 'Content-Type': 'application/problem+json' } }),
    ) })
    const failure = await api.downloadExport(statsQuery, statsPdf).catch((cause: unknown) => cause)
    expect(failure).toBeInstanceOf(HeadmanStatsApiError)
    expect((failure as HeadmanStatsApiError).response.status).toBe(503)
    expect((failure as HeadmanStatsApiError).message).toBe(detail)
  })
})

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
