import {
  ReportDownloadClient,
  type ReportDownloadTicketRequest,
} from '../../../../mobile-core/src/shared/report-download-client'
import { createBrowserReportDownloadPort } from '../../../../mobile-core/src/shared/report-download-browser'
import { webSession } from '../../auth/session'
import type { WebTransport } from '../../app/api'

interface PendingIdentity {
  requestId: string
  expiresAt: number
}

/** Preserve the original create identity after a lost acceptance response.
 * Repeating this POST reconciles the existing server job through its idempotency key.
 * Neither a bearer nor a download ticket is retained in storage.
 */
export function createTeacherReportPort(transport: WebTransport) {
  const owner = webSession.identity.value
  const key = `rct:web:teacher-export:v1:${owner}`
  let pending: Record<string, PendingIdentity> = {}
  try {
    const stored: unknown = JSON.parse(sessionStorage.getItem(key) ?? '{}')
    if (stored && typeof stored === 'object' && !Array.isArray(stored)) {
      for (const [report, value] of Object.entries(stored)) {
        const entry = value as Partial<PendingIdentity> | null
        if (
          entry &&
          typeof entry.requestId === 'string' &&
          /^[0-9a-f-]{36}$/.test(entry.requestId) &&
          typeof entry.expiresAt === 'number' &&
          entry.expiresAt > Date.now()
        )
          pending[report] = { requestId: entry.requestId, expiresAt: entry.expiresAt }
      }
    }
  } catch {
    /* In-memory recovery still works when browser storage is unavailable. */
  }
  function persist() {
    transport.assertCurrent()
    try {
      if (Object.keys(pending).length) sessionStorage.setItem(key, JSON.stringify(pending))
      else sessionStorage.removeItem(key)
    } catch {
      /* Do not turn storage restrictions into a failed server operation. */
    }
  }
  const tickets = new ReportDownloadClient({
    ...transport,
    fetcher: async (input, init) => {
      transport.assertCurrent()
      if (
        String(input) === '/api/auth/export-jobs' &&
        init?.method === 'POST' &&
        typeof init.body === 'string'
      ) {
        const body = JSON.parse(init.body) as {
          requestId: string
          report: ReportDownloadTicketRequest
        }
        const report = JSON.stringify(body.report)
        pending = Object.fromEntries(
          Object.entries(pending).filter(([, value]) => value.expiresAt > Date.now()),
        )
        if (!pending[report]) {
          if (Object.keys(pending).length >= 20)
            throw new Error('Сначала заверши ранее запрошенные выгрузки.')
          pending[report] = { requestId: body.requestId, expiresAt: Date.now() + 10 * 60_000 }
          persist()
        }
        const response = await transport.fetcher(input, {
          ...init,
          body: JSON.stringify({ ...body, requestId: pending[report]!.requestId }),
        })
        transport.assertCurrent()
        if ([400, 403, 404, 409, 410, 422].includes(response.status)) {
          delete pending[report]
          persist()
        }
        return response
      }
      return transport.fetcher(input, init)
    },
  })
  const port = createBrowserReportDownloadPort(
    {
      assertCurrent: () => transport.assertCurrent(),
      issueTicket: (...args) => tickets.issueTicket(...args),
      markDelivered: (request) => {
        delete pending[JSON.stringify(request)]
        persist()
      },
    },
    async (input, init) => {
      // A single-use download capability is same-origin and must not carry a bearer.
      transport.assertCurrent()
      const response = await fetch(input, init)
      transport.assertCurrent()
      return response
    },
  )
  return port
}
