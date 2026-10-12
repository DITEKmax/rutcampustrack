import { afterEach, describe, expect, it, vi } from 'vitest'
import { ReportDownloadClient, notifyExportJobHint, type ExportJobState, type ReportDownloadTicketRequest } from './report-download-client'
import { StaleSessionGenerationError } from './session-owner'
import { createBrowserReportDownloadPort } from './report-download-browser'
import { createGenerationBoundNotificationsRealtime } from '../features/notifications/notifications-realtime'
import type { Client, IMessage, StompConfig } from '@stomp/stompjs'

const id = '00000000-0000-4000-8000-000000000001'
const request: ReportDownloadTicketRequest = { kind: 'ADMIN_GROUPS', adminGroups: { status: 'DRAFT', search: 'ЭК', sorts: [{ field: 'name', descending: true }], format: 'html' } }
const job = (state: ExportJobState) => ({ jobId: id, state, expiresAt: new Date(Date.now() + 60_000).toISOString(), suggestedFilename: 'groups.html', errorCode: null })
const ticket = () => ({ downloadPath: `/api/report-download/${'a'.repeat(43)}`, expiresAt: new Date(Date.now() + 60_000).toISOString(), suggestedFilename: 'groups.html' })
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); vi.restoreAllMocks() })

describe('durable export client', () => {
  it('observes queued and preparing, treats readiness as a hint and requests a capability only after fresh ready status', async () => {
    vi.useFakeTimers()
    const states: ExportJobState[] = []
    const capability = ticket()
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json(job('queued'), { status: 202 }))
      .mockResolvedValueOnce(Response.json(job('preparing')))
      .mockResolvedValueOnce(Response.json(job('ready')))
      .mockResolvedValueOnce(Response.json(capability))
    const client = new ReportDownloadClient({ fetcher, assertCurrent: () => undefined, accessToken: () => 'synthetic-current' })
    const pending = client.issueTicket(request, state => states.push(state))
    await vi.waitFor(() => expect(states).toEqual(['queued']))
    expect(fetcher).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1500)
    expect(states).toEqual(['queued', 'preparing'])
    notifyExportJobHint(id)
    await expect(pending).resolves.toEqual(capability)
    expect(states).toEqual(['queued', 'preparing', 'ready'])
    expect(fetcher.mock.calls.map(call => call[0])).toEqual(['/api/auth/export-jobs', `/api/auth/export-jobs/${id}`, `/api/auth/export-jobs/${id}`, `/api/auth/export-jobs/${id}/ticket`])
    const sent = JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body)) as { requestId: string; report: unknown }
    expect(sent.report).toEqual(request)
    expect(JSON.stringify(sent)).not.toContain('synthetic-current')
  })

  it('ignores foreign hints and refuses a ready-looking hint when the authoritative status denies access', async () => {
    vi.useFakeTimers()
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(Response.json(job('queued'))).mockResolvedValueOnce(new Response(null, { status: 403 }))
    const client = new ReportDownloadClient({ fetcher, assertCurrent: () => undefined, accessToken: () => null })
    const pending = client.issueTicket(request)
    const denied = expect(pending).rejects.toMatchObject({ status: 403 })
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1))
    await Promise.resolve(); await Promise.resolve()
    notifyExportJobHint('00000000-0000-4000-8000-000000000002')
    await Promise.resolve()
    expect(fetcher).toHaveBeenCalledTimes(1)
    notifyExportJobHint(id)
    await vi.advanceTimersByTimeAsync(1500)
    await denied
    expect(fetcher).toHaveBeenCalledTimes(2)
    expect(fetcher.mock.calls.some(call => String(call[0]).endsWith('/ticket'))).toBe(false)
  })

  it('stops without a capability when the selected scope changes while the job is preparing', async () => {
    vi.useFakeTimers()
    let current = true
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(Response.json(job('preparing')))
    const client = new ReportDownloadClient({ fetcher, assertCurrent: () => undefined, accessToken: () => null })
    const pending = client.issueTicket(request, undefined, () => current)
    const stale = expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1))
    current = false
    await vi.advanceTimersByTimeAsync(1500)
    await stale
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it.each(['READY', '4', 'unknown'])('rejects unknown state %s before issuing any capability', async state => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(Response.json({ ...job('ready'), state }))
    const client = new ReportDownloadClient({ fetcher, assertCurrent: () => undefined, accessToken: () => null })
    await expect(client.issueTicket(request)).rejects.toThrow()
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('parses the private socket hint, ignores malformed states and drops frames from an invalidated generation', async () => {
    vi.useFakeTimers()
    let generation = 1
    const callbacks = new Map<string, (message: IMessage) => void>()
    const capture: { config?: StompConfig } = {}
    const client: Pick<Client, 'activate' | 'deactivate' | 'subscribe'> = {
      activate: vi.fn(), deactivate: vi.fn(async () => undefined),
      subscribe(destination, callback) {
        callbacks.set(destination, callback)
        return { id: destination, unsubscribe: vi.fn() }
      },
    }
    const lifecycle = createGenerationBoundNotificationsRealtime({
      owner: { currentGeneration: () => generation, accessTokenFor: () => null, refreshFor: async () => undefined },
      scope: { userId: '7', groupId: null, headman: false },
      onChanged: () => undefined,
      createClient(config) { capture.config = config; return client },
    })
    capture.config?.onConnect?.({} as never)
    const frame = (state: string): void => callbacks.get('/topic/user/7')?.({ body: JSON.stringify({ type: 'export.state.changed', jobId: id, state }) } as IMessage)
    const capability = ticket()
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json(job('queued')))
      .mockResolvedValueOnce(Response.json(job('ready')))
      .mockResolvedValueOnce(Response.json(capability))
    const tickets = new ReportDownloadClient({ fetcher, assertCurrent: () => undefined, accessToken: () => null })
    const pending = tickets.issueTicket(request)
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1))
    await Promise.resolve(); await Promise.resolve()
    frame('READY')
    await vi.advanceTimersByTimeAsync(0)
    expect(fetcher).toHaveBeenCalledTimes(1)
    frame('ready')
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
    await vi.advanceTimersByTimeAsync(1500)
    await stale
    lifecycle.dispose()
    expect(client.deactivate).toHaveBeenCalledWith({ force: true })
  })
})

describe('browser save boundary', () => {
  it('saves exactly the completed file using its bounded safe filename and releases the object URL', async () => {
    vi.useFakeTimers()
    const link = { href: '', download: '', rel: '', click: vi.fn(), remove: vi.fn() }
    const appendChild = vi.fn()
    vi.stubGlobal('document', { createElement: () => link, body: { appendChild } })
    const create = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:synthetic')
    const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(new Response('<html>safe registry</html>', { headers: { 'Content-Type': 'text/html;charset=UTF-8' } }))
    const port = createBrowserReportDownloadPort({ assertCurrent: () => undefined, issueTicket: async () => ticket() }, fetcher)
    await expect(port.download(request, () => true)).resolves.toBe('accepted')
    expect(link.download).toBe('groups.html'); expect(link.click).toHaveBeenCalledTimes(1)
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
    const wrong = createBrowserReportDownloadPort(issuer, async () => new Response('wrong', { headers: { 'Content-Type': 'application/pdf' } }))
    await expect(wrong.download(request, () => true)).rejects.toThrow()
    let current = true
    const stale = createBrowserReportDownloadPort(issuer, async () => { current = false; return new Response('safe', { headers: { 'Content-Type': 'text/html' } }) })
    await expect(stale.download(request, () => current)).rejects.toBeInstanceOf(StaleSessionGenerationError)
    expect(click).not.toHaveBeenCalled()
  })
})
