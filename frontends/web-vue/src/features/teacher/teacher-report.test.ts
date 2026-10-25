import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { WebTransport } from '../../app/api'
import type { ReportDownloadTicketRequest } from '../../../../mobile-core/src/shared/report-download-client'
import { createTeacherReportPort } from './teacher-report'

const session = vi.hoisted(() => ({ identity: { value: 'teacher-session-a' } }))
vi.mock('../../auth/session', () => ({ webSession: session }))
const request: ReportDownloadTicketRequest = {
  kind: 'TEACHER_STATS',
  teacherStats: { semesterId: 2, scope: 'students', groupId: 1, subjectId: 1, lessonTypes: ['lecture'], format: 'pdf' },
}
let stored: Map<string, string>
beforeEach(() => {
  session.identity.value = 'teacher-session-a'
  stored = new Map()
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => stored.get(key) ?? null,
    setItem: (key: string, value: string) => stored.set(key, value),
    removeItem: (key: string) => stored.delete(key),
  })
})
afterEach(() => vi.unstubAllGlobals())

function transport(fetcher: typeof fetch): WebTransport {
  const owner = session.identity.value
  return {
    fetcher,
    accessToken: () => 'synthetic-access-secret',
    onUnauthorized: async () => undefined,
    assertCurrent: () => { if (session.identity.value !== owner) throw new Error('Authority changed') },
  }
}

describe('teacher export reconciliation boundary', () => {
  it('reconciles a lost create response with the original request identity after remount', async () => {
    const sent: { requestId: string; report: ReportDownloadTicketRequest }[] = []
    const fetcher = vi.fn<typeof fetch>(async (_input, init) => {
      sent.push(JSON.parse(String(init?.body)))
      if (sent.length === 1) throw new TypeError('Lost acceptance response')
      return new Response(null, { status: 403 })
    })
    await expect(createTeacherReportPort(transport(fetcher)).download(request, () => true)).rejects.toThrow('Lost acceptance response')
    await expect(createTeacherReportPort(transport(fetcher)).download(request, () => true)).rejects.toThrow()
    expect(sent).toHaveLength(2)
    expect(sent[0]!.requestId).toBe(sent[1]!.requestId)
    expect(sent[1]!.report).toEqual(request)
    expect(JSON.stringify([...stored])).not.toContain('synthetic-access-secret')
    expect(stored.size).toBe(0)
  })

  it('never reuses another admitted session identity and stops before requesting a ticket after authority changes', async () => {
    const sent: string[] = []
    const fetcher = vi.fn<typeof fetch>(async (_input, init) => {
      sent.push((JSON.parse(String(init?.body)) as { requestId: string }).requestId)
      if (sent.length === 1) throw new TypeError('Lost response')
      session.identity.value = 'teacher-session-c'
      return Response.json({ jobId: '00000000-0000-4000-8000-000000000001', state: 'ready', expiresAt: new Date(Date.now() + 60_000).toISOString(), suggestedFilename: 'stats.pdf', errorCode: null })
    })
    await expect(createTeacherReportPort(transport(fetcher)).download(request, () => true)).rejects.toThrow()
    session.identity.value = 'teacher-session-b'
    await expect(createTeacherReportPort(transport(fetcher)).download(request, () => true)).rejects.toThrow('Authority changed')
    expect(sent).toHaveLength(2)
    expect(sent[0]).not.toBe(sent[1])
    expect(fetcher.mock.calls.every(([path]) => path === '/api/auth/export-jobs')).toBe(true)
  })
})
