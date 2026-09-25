import {
  ReportDownloadResponseError,
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
  constructor() {
    super('Telegram не удалось передать файл для скачивания.')
    this.name = 'TelegramReportDownloadHostError'
  }
}

export function createTelegramReportDownloadPort(
  tickets: ReportDownloadTicketIssuer,
  host: TelegramReportDownloadHost,
  trustedOrigin: () => string = () => window.location.origin,
): ReportDownloadPort {
  return {
    async download(request, isCurrent) {
      if (!isCurrent()) return 'stale'
      tickets.assertCurrent()
      if (!host.supportsFileDownload()) return 'unsupported'

      const ticket = await tickets.issueTicket(request)
      tickets.assertCurrent()
      if (!isCurrent()) return 'stale'

      const params = {
        url: buildTrustedHttpsDownloadUrl(trustedOrigin(), ticket.downloadPath),
        file_name: validateSuggestedFilename(ticket.suggestedFilename, request),
      }
      tickets.assertCurrent()
      if (!isCurrent()) return 'stale'

      const result = await new Promise<'accepted' | 'cancelled' | 'unsupported'>((resolve, reject) => {
        try {
          const submitted = host.requestFileDownload(params, (accepted) => {
            resolve(accepted ? 'accepted' : 'cancelled')
          })
          if (!submitted) resolve('unsupported')
        } catch {
          reject(new TelegramReportDownloadHostError())
        }
      })

      tickets.assertCurrent()
      return isCurrent() ? result : 'stale'
    },
  }
}

export function buildTrustedHttpsDownloadUrl(origin: string, downloadPath: string): string {
  if (typeof origin !== 'string' || !/^\/api\/report-download\/[A-Za-z0-9_-]{43}$/.test(downloadPath)) {
    throw new ReportDownloadResponseError()
  }
  let trusted: URL
  try {
    trusted = new URL(origin)
  } catch {
    throw new ReportDownloadResponseError()
  }
  if (trusted.protocol !== 'https:' || trusted.origin !== origin || trusted.username || trusted.password
    || trusted.pathname !== '/' || trusted.search || trusted.hash) {
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
  const format = request.kind === 'TEACHER_JOURNAL' ? request.teacherJournal.format
    : request.kind === 'TEACHER_STATS' ? request.teacherStats.format
      : request.kind === 'HEADMAN_WEEKLY_CURRENT' ? request.headmanWeeklyCurrent.format
        : request.kind === 'HEADMAN_WEEKLY_SELECTED' ? request.headmanWeeklySelected.format
          : request.headmanStats.format
  const extension = format === 'png' ? 'zip' : format
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$/.test(filename) || !filename.endsWith(`.${extension}`)) {
    throw new ReportDownloadResponseError()
  }
  return filename
}
