import { StaleSessionGenerationError } from './session-owner'
import { reportFormat, ReportDownloadResponseError, type ReportDownloadPort, type ReportDownloadTicketIssuer } from './report-download-client'

/** Existing browser save action, after durable generation and a fresh capability. */
export function createBrowserReportDownloadPort(tickets: ReportDownloadTicketIssuer, fetcher: typeof fetch = globalThis.fetch.bind(globalThis)): ReportDownloadPort {
  return {
    carrier: 'browser',
    async download(request, isCurrent, onState) {
      const current = (): void => { tickets.assertCurrent(); if (!isCurrent()) throw new StaleSessionGenerationError() }
      if (!isCurrent()) return 'stale'
      current()
      const ticket = await tickets.issueTicket(request, onState, isCurrent)
      current()
      const response = await fetcher(ticket.downloadPath, { method: 'GET', credentials: 'same-origin', headers: { Accept: '*/*' } })
      current()
      if (!response.ok) throw new Error(`Не удалось скачать файл (HTTP ${response.status}).`)
      const format = reportFormat(request)
      const mime = request.kind === 'HEADMAN_STATS_TREND' && format === 'png' ? 'image/png' : {
        docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', pdf: 'application/pdf',
        png: 'application/zip', html: 'text/html', xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      }[format]
      if (response.headers.get('Content-Type')?.split(';')[0]?.trim() !== mime) throw new ReportDownloadResponseError()
      const reader = response.body?.getReader()
      if (!reader) throw new ReportDownloadResponseError()
      const chunks: Uint8Array<ArrayBuffer>[] = []
      let size = 0
      try {
        while (true) {
          current()
          const chunk = await reader.read()
          current()
          if (chunk.done) break
          size += chunk.value.length
          if (size > 20 * 1024 * 1024) throw new Error('Файл превышает лимит скачивания.')
          chunks.push(new Uint8Array(chunk.value))
        }
      } finally { await reader.cancel().catch(() => undefined) }
      current()
      if (size === 0) throw new ReportDownloadResponseError()
      const url = URL.createObjectURL(new Blob(chunks, { type: mime }))
      try {
        current()
        const link = document.createElement('a')
        link.href = url; link.download = ticket.suggestedFilename; link.rel = 'noopener'
        document.body.appendChild(link); link.click(); link.remove()
      } catch (failure) { URL.revokeObjectURL(url); throw failure }
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
      return 'accepted'
    },
  }
}
