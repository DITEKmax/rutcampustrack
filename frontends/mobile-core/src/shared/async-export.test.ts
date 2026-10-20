import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ReportDownloadClient,
  ReportDownloadResponseError,
  notifyExportJobHint,
  type ExportJobState,
  type ReportDownloadTicketRequest,
} from './report-download-client'
import { StaleSessionGenerationError } from './session-owner'
import { createBrowserReportDownloadPort } from './report-download-browser'
import { createGenerationBoundNotificationsRealtime } from '../features/notifications/notifications-realtime'
import type { Client, IMessage, StompConfig } from '@stomp/stompjs'
import { TelegramReportDownloadHostError, createTelegramReportDownloadPort } from '../../../tma-vue/src/report-download-adapter'

const id = '00000000-0000-4000-8000-000000000001'
const request: ReportDownloadTicketRequest = {
  kind: 'ADMIN_GROUPS',
  adminGroups: {
    status: 'DRAFT',
    search: 'ЭК',
    sorts: [{ field: 'name', descending: true }],
    format: 'html',
  },
}
const job = (state: ExportJobState) => ({
  jobId: id,
  state,
  expiresAt: new Date(Date.now() + 60_000).toISOString(),
  suggestedFilename: 'groups.html',
  errorCode: null,
})
const ticket = () => ({
  downloadPath: `/api/report-download/${'a'.repeat(43)}`,
  expiresAt: new Date(Date.now() + 60_000).toISOString(),
  suggestedFilename: 'groups.html',
})
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('durable export client', () => {
  it('observes queued and preparing, treats readiness as a hint and requests a capability only after fresh ready status', async () => {
    vi.useFakeTimers()
    const states: ExportJobState[] = []
    const capability = ticket()
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json(job('queued'), { status: 202 }))
      .mockResolvedValueOnce(Response.json(job('preparing')))
      .mockResolvedValueOnce(Response.json(job('ready')))
      .mockResolvedValueOnce(Response.json(capability))
    const client = new ReportDownloadClient({
      fetcher,
      assertCurrent: () => undefined,
      accessToken: () => 'synthetic-current',
    })
    const pending = client.issueTicket(request, (state) => states.push(state))
    await vi.waitFor(() => expect(states).toEqual(['queued']))
    expect(fetcher).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(2000)
    expect(states).toEqual(['queued', 'preparing'])
    notifyExportJobHint(id)
    await vi.advanceTimersByTimeAsync(2000)
    await expect(pending).resolves.toEqual(capability)
    expect(states).toEqual(['queued', 'preparing', 'ready'])
    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      '/api/auth/export-jobs',
      `/api/auth/export-jobs/${id}`,
      `/api/auth/export-jobs/${id}`,
      `/api/auth/export-jobs/${id}/ticket`,
    ])
    const sent = JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body)) as {
      requestId: string
      report: unknown
    }
    expect(sent.report).toEqual(request)
    expect(JSON.stringify(sent)).not.toContain('synthetic-current')
  })

  it('ignores foreign hints and refuses a ready-looking hint when the authoritative status denies access', async () => {
    vi.useFakeTimers()
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json(job('queued')))
      .mockResolvedValueOnce(new Response(null, { status: 403 }))
    const client = new ReportDownloadClient({
      fetcher,
      assertCurrent: () => undefined,
      accessToken: () => null,
    })
    const pending = client.issueTicket(request)
    const denied = expect(pending).rejects.toMatchObject({ status: 403 })
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1))
    await Promise.resolve()
    await Promise.resolve()
    notifyExportJobHint('00000000-0000-4000-8000-000000000002')
    await Promise.resolve()
    expect(fetcher).toHaveBeenCalledTimes(1)
    notifyExportJobHint(id)
    await vi.advanceTimersByTimeAsync(2000)
    await denied
    expect(fetcher).toHaveBeenCalledTimes(2)
    expect(fetcher.mock.calls.some((call) => String(call[0]).endsWith('/ticket'))).toBe(false)
  })

  it('stops without a capability when the selected scope changes while the job is preparing', async () => {
    vi.useFakeTimers()
    let current = true
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(Response.json(job('preparing')))
    const client = new ReportDownloadClient({
      fetcher,
      assertCurrent: () => undefined,
      accessToken: () => null,
    })
    const pending = client.issueTicket(request, undefined, () => current)
    const stale = expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1))
    current = false
    await vi.advanceTimersByTimeAsync(2000)
    await stale
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it.each(['READY', '4', 'unknown'])(
    'rejects unknown state %s before issuing any capability',
    async (state) => {
      const fetcher = vi
        .fn<typeof fetch>()
        .mockResolvedValueOnce(Response.json({ ...job('ready'), state }))
      const client = new ReportDownloadClient({
        fetcher,
        assertCurrent: () => undefined,
        accessToken: () => null,
      })
      await expect(client.issueTicket(request)).rejects.toThrow()
      expect(fetcher).toHaveBeenCalledTimes(1)
    },
  )

  it('parses the private socket hint, ignores malformed states and drops frames from an invalidated generation', async () => {
    vi.useFakeTimers()
    let generation = 1
    const callbacks = new Map<string, (message: IMessage) => void>()
    const capture: { config?: StompConfig } = {}
    const client: Pick<Client, 'activate' | 'deactivate' | 'subscribe'> = {
      activate: vi.fn(),
      deactivate: vi.fn(async () => undefined),
      subscribe(destination, callback) {
        callbacks.set(destination, callback)
        return { id: destination, unsubscribe: vi.fn() }
      },
    }
    const lifecycle = createGenerationBoundNotificationsRealtime({
      owner: {
        currentGeneration: () => generation,
        accessTokenFor: () => null,
        refreshFor: async () => undefined,
      },
      scope: { userId: '7', groupId: null, headman: false },
      onChanged: () => undefined,
      createClient(config) {
        capture.config = config
        return client
      },
    })
    capture.config?.onConnect?.({} as never)
    const frame = (state: string): void =>
      callbacks.get('/topic/user/7')?.({
        body: JSON.stringify({ type: 'export.state.changed', jobId: id, state }),
      } as IMessage)
    const capability = ticket()
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json(job('queued')))
      .mockResolvedValueOnce(Response.json(job('ready')))
      .mockResolvedValueOnce(Response.json(capability))
    const tickets = new ReportDownloadClient({
      fetcher,
      assertCurrent: () => undefined,
      accessToken: () => null,
    })
    const pending = tickets.issueTicket(request)
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1))
    await Promise.resolve()
    await Promise.resolve()
    frame('READY')
    await vi.advanceTimersByTimeAsync(0)
    expect(fetcher).toHaveBeenCalledTimes(1)
    frame('ready')
    await vi.advanceTimersByTimeAsync(2000)
    await expect(pending).resolves.toEqual(capability)
    expect(fetcher).toHaveBeenCalledTimes(3)

    fetcher.mockResolvedValueOnce(Response.json(job('queued')))
    const stalePending = tickets.issueTicket(request, undefined, () => generation === 1)
    const stale = expect(stalePending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(4))
    generation = 2
    frame('ready')
    await vi.advanceTimersByTimeAsync(0)
    expect(fetcher).toHaveBeenCalledTimes(4)
    await vi.advanceTimersByTimeAsync(2000)
    await stale
    lifecycle.dispose()
    expect(client.deactivate).toHaveBeenCalledWith({ force: true })
  })
})

describe('weekly export recovery', () => {
  const weekly: ReportDownloadTicketRequest = {
    kind: 'HEADMAN_WEEKLY_SELECTED',
    headmanWeeklySelected: {
      weekStarts: Array.from({ length: 18 }, (_, index) =>
        new Date(Date.UTC(2026, 8, 7 + index * 7)).toISOString().slice(0, 10),
      ),
      format: 'docx',
    },
  }
  const weeklyJob = (state: ExportJobState) => ({ ...job(state), suggestedFilename: 'weekly.docx' })
  const weeklyTicket = () => ({ ...ticket(), suggestedFilename: 'weekly.docx' })
  const storage = () => {
    const values = new Map<string, string>()
    return {
      values,
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => {
        values.set(key, value)
      },
      removeItem: (key: string) => {
        values.delete(key)
      },
    }
  }

  it('persists before an unknown POST outcome and replays the same request identity after reload', async () => {
    const recoveryStorage = storage()
    const create = (fetcher: typeof fetch) =>
      new ReportDownloadClient({
        fetcher,
        assertCurrent: () => undefined,
        accessToken: () => 'synthetic-bearer',
        recoveryScope: () => 'user-7/session-1/HEADMAN',
        recoveryStorage,
      })
    const lost = vi.fn<typeof fetch>(async () => {
      expect(recoveryStorage.values.size).toBe(1)
      throw new TypeError('network lost after acceptance')
    })
    await expect(create(lost).issueTicket(weekly)).rejects.toThrow('network lost')
    const retry = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json(weeklyJob('ready')))
      .mockResolvedValueOnce(Response.json(weeklyTicket()))
    const restored = create(retry)
    expect(restored.pendingRequests()).toEqual([weekly])
    await expect(restored.issueTicket(weekly)).resolves.toMatchObject({
      suggestedFilename: 'weekly.docx',
    })
    expect(JSON.parse(String(retry.mock.calls[0]?.[1]?.body))).toEqual(
      JSON.parse(String(lost.mock.calls[0]?.[1]?.body)),
    )
    const persisted = [...recoveryStorage.values.values()].join('')
    expect(persisted).not.toContain('synthetic-bearer')
    expect(persisted).not.toContain('/api/report-download/')
    expect(persisted).not.toContain('suggestedFilename')
    // Issuing a ticket is not delivery: cancellation or a failed file read can resume.
    expect(restored.pendingRequests()).toEqual([weekly])
    restored.markDelivered(weekly)
    expect(restored.pendingRequests()).toEqual([])
  })

  it('recovers an accepted job by fresh status after navigation and never creates another job', async () => {
    vi.useFakeTimers()
    const recoveryStorage = storage()
    let mounted = true
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(Response.json(weeklyJob('queued')))
    const create = () =>
      new ReportDownloadClient({
        fetcher,
        assertCurrent: () => undefined,
        accessToken: () => null,
        recoveryScope: () => 'same-session',
        recoveryStorage,
      })
    const states: ExportJobState[] = []
    const old = create().issueTicket(
      weekly,
      (state) => states.push(state),
      () => mounted,
    )
    const stale = expect(old).rejects.toBeInstanceOf(StaleSessionGenerationError)
    await vi.waitFor(() => expect(states).toEqual(['queued']))
    mounted = false
    await vi.advanceTimersByTimeAsync(2000)
    await stale
    fetcher
      .mockResolvedValueOnce(Response.json(weeklyJob('ready')))
      .mockResolvedValueOnce(Response.json(weeklyTicket()))
    await create().issueTicket(weekly)
    expect(fetcher.mock.calls.map(([path]) => path)).toEqual([
      '/api/auth/export-jobs',
      `/api/auth/export-jobs/${id}`,
      `/api/auth/export-jobs/${id}/ticket`,
    ])
    expect(states).toEqual(['queued'])
  })

  it('keeps simultaneous retries on one idempotency identity and isolates another session', async () => {
    const recoveryStorage = storage()
    const fetcher = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('offline'))
    const create = (scope: string) =>
      new ReportDownloadClient({
        fetcher,
        assertCurrent: () => undefined,
        accessToken: () => null,
        recoveryScope: () => scope,
        recoveryStorage,
      })
    const client = create('session-A')
    await Promise.allSettled([client.issueTicket(weekly), client.issueTicket(weekly)])
    const first = JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body)) as { requestId: string }
    const second = JSON.parse(String(fetcher.mock.calls[1]?.[1]?.body)) as { requestId: string }
    expect(first.requestId).toBe(second.requestId)
    expect(create('session-B').pendingRequests()).toEqual([])
    expect(client.pendingRequests()).toEqual([weekly])
  })

  it('bounds an unknown acceptance timeout and retains its identity for reconnect', async () => {
    vi.useFakeTimers()
    const recoveryStorage = storage()
    const fetcher = vi.fn<typeof fetch>(
      (_input, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () =>
            reject(new DOMException('Timed out', 'AbortError')),
          )
        }),
    )
    const client = new ReportDownloadClient({
      fetcher,
      assertCurrent: () => undefined,
      accessToken: () => null,
      recoveryScope: () => 'same-session',
      recoveryStorage,
    })
    const pending = client.issueTicket(weekly)
    const failed = expect(pending).rejects.toMatchObject({ name: 'AbortError' })
    await vi.advanceTimersByTimeAsync(15_000)
    await failed
    expect(client.pendingRequests()).toEqual([weekly])
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('explains expiry and never silently resubmits an expired accepted job', async () => {
    vi.useFakeTimers()
    const recoveryStorage = storage()
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json(weeklyJob('ready')))
      .mockRejectedValueOnce(new TypeError('ticket response lost'))
    const client = new ReportDownloadClient({
      fetcher,
      assertCurrent: () => undefined,
      accessToken: () => null,
      recoveryScope: () => 'same-session',
      recoveryStorage,
    })
    await expect(client.issueTicket(weekly)).rejects.toThrow('ticket response lost')
    await vi.advanceTimersByTimeAsync(60_001)
    await expect(client.issueTicket(weekly)).rejects.toMatchObject({ status: 410 })
    expect(fetcher).toHaveBeenCalledTimes(2)
    expect(client.pendingRequests()).toEqual([])
  })

  it('also bounds a status response whose headers arrived but JSON body stalled', async () => {
    vi.useFakeTimers()
    const recoveryStorage = storage()
    const client = new ReportDownloadClient({
      fetcher: async (_input, init) =>
        new Response(
          new ReadableStream({
            start(controller) {
              init?.signal?.addEventListener('abort', () =>
                controller.error(new Error('body timeout')),
              )
            },
          }),
        ),
      assertCurrent: () => undefined,
      accessToken: () => null,
      recoveryScope: () => 'same-session',
      recoveryStorage,
    })
    const pending = client.issueTicket(weekly)
    const failed = expect(pending).rejects.toBeInstanceOf(ReportDownloadResponseError)
    await vi.advanceTimersByTimeAsync(15_000)
    await failed
    expect(client.pendingRequests()).toEqual([weekly])
  })

  it('does not publish or retain a late ready response under a replacement generation', async () => {
    const recoveryStorage = storage()
    let current = true
    const states: ExportJobState[] = []
    const fetcher = vi.fn<typeof fetch>(async () => {
      current = false
      return Response.json(weeklyJob('ready'))
    })
    const client = new ReportDownloadClient({
      fetcher,
      assertCurrent: () => {
        if (!current) throw new StaleSessionGenerationError()
      },
      accessToken: () => null,
      recoveryScope: () => 'old-session',
      recoveryStorage,
    })
    await expect(client.issueTicket(weekly, (state) => states.push(state))).rejects.toBeInstanceOf(
      StaleSessionGenerationError,
    )
    expect(states).toEqual([])
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect([...recoveryStorage.values.values()].join('')).toContain('"jobId":null')
  })

  it.each([403, 404])(
    'reports definitive status %s without a new job or capability',
    async (status) => {
      const recoveryStorage = storage()
      const first = new ReportDownloadClient({
        fetcher: async () => Response.json(weeklyJob('ready')),
        assertCurrent: () => undefined,
        accessToken: () => null,
        recoveryScope: () => 'same-session',
        recoveryStorage,
      })
      // Simulate a file handoff failure after acceptance by retaining the job record.
      await expect(first.issueTicket(weekly)).rejects.toThrow()
      const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status }))
      const restored = new ReportDownloadClient({
        fetcher,
        assertCurrent: () => undefined,
        accessToken: () => null,
        recoveryScope: () => 'same-session',
        recoveryStorage,
      })
      await expect(restored.issueTicket(weekly)).rejects.toMatchObject({ status })
      expect(fetcher.mock.calls.map(([path]) => path)).toEqual([`/api/auth/export-jobs/${id}`])
      expect(restored.pendingRequests()).toEqual([])
    },
  )
})

describe('bounded Telegram handoff', () => {
  it('settles a missing host acknowledgment, retains the locator, and ignores a late accepted callback', async () => {
    vi.useFakeTimers()
    let acknowledge: ((accepted: boolean) => void) | undefined
    const markDelivered = vi.fn()
    const issuer = {
      assertCurrent: vi.fn(),
      issueTicket: async () => ticket(),
      pendingRequests: () => [request],
      markDelivered,
    }
    const port = createTelegramReportDownloadPort(
      issuer,
      {
        supportsFileDownload: () => true,
        requestFileDownload: (_params, callback) => {
          acknowledge = callback
          return true
        },
      },
      () => 'https://ruttrack.example',
    )
    const pending = port.download(request, () => true)
    const failed = expect(pending).rejects.toBeInstanceOf(TelegramReportDownloadHostError)
    await vi.advanceTimersByTimeAsync(60_000)
    await failed
    expect(markDelivered).not.toHaveBeenCalled()
    expect(port.pendingRequests?.()).toEqual([request])
    const currentChecks = issuer.assertCurrent.mock.calls.length
    acknowledge?.(true)
    await Promise.resolve()
    expect(issuer.assertCurrent).toHaveBeenCalledTimes(currentChecks)
    expect(markDelivered).not.toHaveBeenCalled()
    expect(port.pendingRequests?.()).toEqual([request])
    expect(vi.getTimerCount()).toBe(0)
  })

  it.each(['view', 'generation'])(
    'drops an accepted acknowledgment after the %s changes',
    async (scope) => {
      vi.useFakeTimers()
      let current = true
      let acknowledge: ((accepted: boolean) => void) | undefined
      const markDelivered = vi.fn()
      const port = createTelegramReportDownloadPort(
        {
          assertCurrent: () => {
            if (scope === 'generation' && !current) throw new StaleSessionGenerationError()
          },
          issueTicket: async () => ticket(),
          markDelivered,
        },
        {
          supportsFileDownload: () => true,
          requestFileDownload: (_params, callback) => {
            acknowledge = callback
            return true
          },
        },
        () => 'https://ruttrack.example',
      )
      const pending = port.download(request, () => current)
      const result =
        scope === 'view'
          ? expect(pending).resolves.toBe('stale')
          : expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
      await vi.waitFor(() => expect(acknowledge).toBeDefined())
      current = false
      acknowledge?.(true)
      await result
      await vi.advanceTimersByTimeAsync(60_000)
      expect(markDelivered).not.toHaveBeenCalled()
      expect(vi.getTimerCount()).toBe(0)
    },
  )
})

describe('browser save boundary', () => {
  it('bounds a stalled file stream without marking the durable result delivered', async () => {
    vi.useFakeTimers()
    const markDelivered = vi.fn()
    const fetcher: typeof fetch = async (_input, init) =>
      new Response(
        new ReadableStream({
          start(controller) {
            init?.signal?.addEventListener('abort', () =>
              controller.error(new DOMException('file timeout', 'AbortError')),
            )
          },
        }),
        { headers: { 'Content-Type': 'text/html' } },
      )
    const port = createBrowserReportDownloadPort(
      {
        assertCurrent: () => undefined,
        issueTicket: async () => ticket(),
        markDelivered,
      },
      fetcher,
    )
    const pending = port.download(request, () => true)
    const failed = expect(pending).rejects.toMatchObject({ name: 'AbortError' })
    await vi.advanceTimersByTimeAsync(60_000)
    await failed
    expect(markDelivered).not.toHaveBeenCalled()
  })

  it('saves exactly the completed file using its bounded safe filename and releases the object URL', async () => {
    vi.useFakeTimers()
    const link = { href: '', download: '', rel: '', click: vi.fn(), remove: vi.fn() }
    const appendChild = vi.fn()
    vi.stubGlobal('document', { createElement: () => link, body: { appendChild } })
    const create = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:synthetic')
    const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    const markDelivered = vi.fn()
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(
      new Response('<html>safe registry</html>', {
        headers: { 'Content-Type': 'text/html;charset=UTF-8' },
      }),
    )
    const port = createBrowserReportDownloadPort(
      { assertCurrent: () => undefined, issueTicket: async () => ticket(), markDelivered },
      fetcher,
    )
    await expect(port.download(request, () => true)).resolves.toBe('accepted')
    expect(link.download).toBe('groups.html')
    expect(link.click).toHaveBeenCalledTimes(1)
    expect(markDelivered).toHaveBeenCalledWith(request)
    expect(create.mock.calls[0]?.[0]).toMatchObject({ type: 'text/html', size: 26 })
    expect(await (create.mock.calls[0]![0] as Blob).text()).toBe('<html>safe registry</html>')
    expect(new Headers(fetcher.mock.calls[0]?.[1]?.headers).has('Authorization')).toBe(false)
    await vi.advanceTimersByTimeAsync(60_000)
    expect(revoke).toHaveBeenCalledWith('blob:synthetic')
  })
  it('never saves mismatched file types or a response arriving after session invalidation', async () => {
    const click = vi.fn()
    vi.stubGlobal('document', { createElement: () => ({ click }) })
    const issuer = { assertCurrent: () => undefined, issueTicket: async () => ticket() }
    const wrong = createBrowserReportDownloadPort(
      issuer,
      async () => new Response('wrong', { headers: { 'Content-Type': 'application/pdf' } }),
    )
    await expect(wrong.download(request, () => true)).rejects.toThrow()
    let current = true
    const stale = createBrowserReportDownloadPort(issuer, async () => {
      current = false
      return new Response('safe', { headers: { 'Content-Type': 'text/html' } })
    })
    await expect(stale.download(request, () => current)).rejects.toBeInstanceOf(
      StaleSessionGenerationError,
    )
    expect(click).not.toHaveBeenCalled()
  })
})
