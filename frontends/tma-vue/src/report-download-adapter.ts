import {
  ReportDownloadResponseError,
  reportFormat,
  type ReportDownloadPort,
  type ReportDownloadTicketIssuer,
  type ReportDownloadTicketRequest,
} from '../../mobile-core/src/shared/report-download-client'

export interface TelegramDownloadFileParams {
  readonly url: string
  readonly file_name: string
}

export interface TelegramReportDownloadHost {
  supportsFileDownload(): boolean
  requestFileDownload(
    params: TelegramDownloadFileParams,
    callback: (accepted: boolean) => void,
  ): boolean
}

export class TelegramReportDownloadHostError extends Error {
  constructor(message = 'Telegram не удалось передать файл для скачивания.') {
    super(message)
    this.name = 'TelegramReportDownloadHostError'
  }
}

export function createTelegramReportDownloadPort(
  tickets: ReportDownloadTicketIssuer,
  host: TelegramReportDownloadHost,
  trustedOrigin: () => string = () => window.location.origin,
): ReportDownloadPort {
  return {
    carrier: 'telegram',
    ...(tickets.pendingRequests ? { pendingRequests: () => tickets.pendingRequests!() } : {}),
    async download(request, isCurrent, onState) {
      if (!isCurrent()) return 'stale'
      tickets.assertCurrent()
      if (!host.supportsFileDownload()) return 'unsupported'

      const ticket = await tickets.issueTicket(request, onState, isCurrent)
      tickets.assertCurrent()
      if (!isCurrent()) return 'stale'

      const params = {
        url: buildTrustedHttpsDownloadUrl(trustedOrigin(), ticket.downloadPath),
        file_name: validateSuggestedFilename(ticket.suggestedFilename, request),
      }
      tickets.assertCurrent()
      if (!isCurrent()) return 'stale'

      const result = await new Promise<'accepted' | 'cancelled' | 'unsupported' | 'stale'>(
        (resolve, reject) => {
          let settled = false
          const finish = (action: () => void): void => {
            if (settled) return
            settled = true
            clearTimeout(timeout)
            action()
          }
          const timeout = setTimeout(
            () =>
              finish(() => {
                try {
                  tickets.assertCurrent()
                  if (!isCurrent()) resolve('stale')
                  else
                    reject(
                      new TelegramReportDownloadHostError(
                        'Telegram не подтвердил скачивание. Попробуй ещё раз.',
                      ),
                    )
                } catch (failure) {
                  reject(failure)
                }
              }),
            60_000,
          )
          try {
            const submitted = host.requestFileDownload(params, (accepted) => {
              finish(() => {
                try {
                  tickets.assertCurrent()
                  resolve(!isCurrent() ? 'stale' : accepted ? 'accepted' : 'cancelled')
                } catch (failure) {
                  reject(failure)
                }
              })
            })
            if (!submitted) finish(() => resolve('unsupported'))
          } catch {
            finish(() => reject(new TelegramReportDownloadHostError()))
          }
        },
      )

      tickets.assertCurrent()
      if (!isCurrent()) return 'stale'
      if (result === 'accepted') tickets.markDelivered?.(request)
      return result
    },
  }
}

export function buildTrustedHttpsDownloadUrl(origin: string, downloadPath: string): string {
  if (
    typeof origin !== 'string' ||
    !/^\/api\/report-download\/[A-Za-z0-9_-]{43}$/.test(downloadPath)
  ) {
    throw new ReportDownloadResponseError()
  }
  let trusted: URL
  try {
    trusted = new URL(origin)
  } catch {
    throw new ReportDownloadResponseError()
  }
  if (
    trusted.protocol !== 'https:' ||
    trusted.origin !== origin ||
    trusted.username ||
    trusted.password ||
    trusted.pathname !== '/' ||
    trusted.search ||
    trusted.hash
  ) {
    throw new ReportDownloadResponseError()
  }
  const url = new URL(downloadPath, trusted.origin)
  if (url.origin !== trusted.origin || url.pathname !== downloadPath || url.search || url.hash) {
    throw new ReportDownloadResponseError()
  }
  return url.href
}

export function validateSuggestedFilename(
  filename: string,
  request: ReportDownloadTicketRequest,
): string {
  const format = reportFormat(request)
  const extension =
    request.kind === 'HEADMAN_STATS_TREND' ? format : format === 'png' ? 'zip' : format
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$/.test(filename) || !filename.endsWith(`.${extension}`)) {
    throw new ReportDownloadResponseError()
  }
  return filename
}
