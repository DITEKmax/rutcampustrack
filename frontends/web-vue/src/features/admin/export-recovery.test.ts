import { describe, expect, it, vi } from 'vitest'
import {
  ReportDownloadClient,
  type ReportDownloadTicketRequest,
} from '../../../../mobile-core/src/shared/report-download-client'
const jobId = '00000000-0000-4000-8000-000000000091'
const requests: ReportDownloadTicketRequest[] = [
  { kind: 'ADMIN_USERS', adminUsers: { groupId: 3, role: 'STUDENT', format: 'html' } },
  { kind: 'ADMIN_GROUPS', adminGroups: { status: 'ARCHIVED', format: 'html' } },
  {
    kind: 'ADMIN_GROUP_ATTENDANCE',
    adminGroupAttendance: {
      semesterId: '2',
      search: 'ПРТ',
      sort: 'GROUP_NAME',
      direction: 'ASC',
      format: 'html',
    },
  },
]
function memory() {
  const data = new Map<string, string>()
  return {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => {
      data.set(key, value)
    },
    removeItem: (key: string) => {
      data.delete(key)
    },
  }
}
const ready = () =>
  Response.json({
    jobId,
    state: 'ready',
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    suggestedFilename: 'report.html',
    errorCode: null,
  })
const ticket = () =>
  Response.json({
    downloadPath: '/api/report-download/' + 'a'.repeat(43),
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    suggestedFilename: 'report.html',
  })
function client(
  fetcher: typeof fetch,
  storage = memory(),
  scope: () => string | null = () => 'actor-session-admin',
  optIn = true,
) {
  return new ReportDownloadClient({
    fetcher,
    accessToken: () => null,
    assertCurrent: () => {},
    recoveryScope: scope,
    recoveryStorage: storage,
    recoverAdminExports: optIn,
  })
}
describe('ADMIN export original command recovery', () => {
  it.each(requests)(
    'recovers lost acceptance for $kind with its frozen selector and original key',
    async (request) => {
      const storage = memory(),
        posts: { requestId: string; report: ReportDownloadTicketRequest }[] = []
      let lost = true
      const fetcher = vi.fn<typeof fetch>(async (_path, init) => {
        if (String(_path).endsWith('/ticket')) return ticket()
        posts.push(JSON.parse(String(init?.body)))
        if (lost) {
          lost = false
          throw new TypeError('lost acceptance')
        }
        return ready()
      })
      const original = structuredClone(request),
        first = client(fetcher, storage)
      await expect(first.issueTicket(request)).rejects.toThrow('lost acceptance')
      expect(first.pendingRequests()).toEqual([original])
      const resumed = client(fetcher, storage)
      await resumed.issueTicket(resumed.pendingRequests()[0]!)
      expect(posts).toHaveLength(2)
      expect(posts[1]).toEqual(posts[0])
      expect(posts[1]?.report).toEqual(original)
      resumed.markDelivered(original)
      expect(resumed.pendingRequests()).toEqual([])
    },
  )
  it('reads an already known job after ticket failure without another enqueue', async () => {
    let failed = false,
      enqueues = 0,
      reads = 0
    const fetcher = vi.fn<typeof fetch>(async (path, init) => {
      if (String(path).endsWith('/ticket')) {
        if (!failed) {
          failed = true
          return new Response('', { status: 503 })
        }
        return ticket()
      }
      if (init?.method === 'POST') enqueues++
      else reads++
      return ready()
    })
    const own = client(fetcher)
    await expect(own.issueTicket(requests[0]!)).rejects.toMatchObject({ status: 503 })
    await own.issueTicket(own.pendingRequests()[0]!)
    expect({ enqueues, reads }).toEqual({ enqueues: 1, reads: 1 })
  })
  it('does not disclose or reuse pending commands under another captured scope', async () => {
    const storage = memory(),
      fetcher = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('lost'))
    await expect(client(fetcher, storage).issueTicket(requests[0]!)).rejects.toThrow()
    const other = client(fetcher, storage, () => 'other-session-admin')
    expect(other.pendingRequests()).toEqual([])
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
  it.each([false, null])('requires both opt-in and nonempty recovery scope (%s)', async (flag) => {
    const fetcher = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('lost'))
    const own = client(
      fetcher,
      memory(),
      () => (flag === null ? null : 'same-owner'),
      flag !== false,
    )
    await expect(own.issueTicket(requests[0]!)).rejects.toThrow()
    expect(own.pendingRequests()).toEqual([])
  })
  it('keeps weekly default recovery and removes an explicitly expired ADMIN intent', async () => {
    const fetcher = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('lost'))
    const weekly: ReportDownloadTicketRequest = {
      kind: 'HEADMAN_WEEKLY_CURRENT',
      headmanWeeklyCurrent: { weekStart: '2026-10-19', format: 'html' },
    }
    const own = client(fetcher, memory(), () => 'weekly-owner', false)
    await expect(own.issueTicket(weekly)).rejects.toThrow()
    expect(own.pendingRequests()).toEqual([weekly])
    const expired = client(async () => new Response('', { status: 410 }))
    await expect(expired.issueTicket(requests[0]!)).rejects.toMatchObject({ status: 410 })
    expect(expired.pendingRequests()).toEqual([])
  })
})
