import { previewPdf } from '../../../web-vue/src/dev/headman-visual-data'
import type { PreviewHandler, PreviewRole } from '../../../pwa-vue/src/dev/interactive-mock-transport'
import { syntheticReportFile } from './pwa-report-files'

const jobsPath = '/api/auth/export-jobs'
const downloadsPath = '/api/report-download/'
const missing = () => Response.json({ detail: 'Файл демо не найден.' }, { status: 404 })

export function createReportDemo(): PreviewHandler {
  const jobs = new Map<string, {
    jobId: string
    state: 'ready'
    expiresAt: string
    suggestedFilename: string
    role: PreviewRole
    extension: string
    report: object
  }>()
  const downloads = new Map<string, string>()
  return async (url, method, body, role) => {
    const path = url.pathname
    if (method === 'POST' && path === jobsPath) {
      const command = await body()
      const report = command.report as { kind: string; [key: string]: unknown }
      const selector = Object.values(report).find(value => typeof value === 'object' && value !== null && 'format' in value) as { format: string } | undefined
      if (!selector || !report.kind.startsWith(role === 'HEADMAN' ? 'HEADMAN_' : role === 'TEACHER' ? 'TEACHER_' : 'ADMIN_'))
        return missing()
      const jobId = crypto.randomUUID()
      const format = selector.format
      const extension = format === 'png' && report.kind !== 'HEADMAN_STATS_TREND' ? 'zip' : format
      const job = { jobId, state: 'ready' as const, expiresAt: new Date(Date.now() + 600_000).toISOString(),
        suggestedFilename: `rct-demo-report.${extension}`, role, extension, report }
      jobs.set(jobId, job)
      return Response.json({ jobId, state: job.state, expiresAt: job.expiresAt, suggestedFilename: job.suggestedFilename })
    }
    if (path.startsWith(`${jobsPath}/`)) {
      const [id, route] = path.slice(jobsPath.length + 1).split('/')
      const job = jobs.get(id ?? '')
      if (!job || job.role !== role) return missing()
      const { jobId, state, expiresAt, suggestedFilename } = job
      if (method === 'GET' && !route) return Response.json({ jobId, state, expiresAt, suggestedFilename })
      if (method === 'POST' && route === 'ticket') {
        const key = btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32))))
          .replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '')
        downloads.set(key, jobId)
        return Response.json({ downloadPath: downloadsPath + key, expiresAt, suggestedFilename })
      }
    }
    if (path.startsWith(downloadsPath) && method === 'GET') {
      const key = path.slice(downloadsPath.length)
      const job = jobs.get(downloads.get(key) ?? '')
      if (!job || job.role !== role) return missing()
      downloads.delete(key)
      return syntheticReportFile(job.extension, job.report, previewPdf)
    }
    return null
  }
}
