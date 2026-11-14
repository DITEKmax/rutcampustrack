import { attendance, previewDate, previewNow, requestDetail, requestOptions, requestPage, schedule, today } from '../../../web-vue/src/dev/visual-review-fixtures'
import { previewPdf } from '../../../web-vue/src/dev/headman-visual-data'
import type { StudentAttendanceLesson, StudentCheckinAck, StudentRequestAttachment, StudentRequestDetail, StudentRequestLesson, StudentRequestPendingRef } from '../../../mobile-core/src/api/types'
import type { PreviewHandler } from '../../../pwa-vue/src/dev/interactive-mock-transport'

const base = '/api/v1/student'
const noContent = () => new Response(null, { status: 204 })
const missing = () => Response.json({ title: 'Демо-запись не найдена' }, { status: 404 })
const invalid = () => Response.json({ title: 'Выбери доступную пару' }, { status: 400 })

export function createStudentDemo(): PreviewHandler {
  const marked = new Set<string>()
  const details = new Map<string, StudentRequestDetail>()
  const attachments = new Map<string, Blob>()
  for (const bucket of ['OPEN', 'ARCHIVE'] as const) {
    for (const { id } of requestPage(bucket, 0, 100).content) details.set(id, structuredClone(requestDetail(id)!))
  }
  let nextId = 8001
  const attendanceStatus = { status: 'PRESENT', source: 'STUDENT_GEO', markedAt: previewNow } as const
  const missed = attendance.days.flatMap(({ lessons }) => lessons).filter(({ status }) => status === 'ABSENT')
  function requestLesson(lesson: StudentAttendanceLesson): StudentRequestLesson {
    return {
      id: lesson.id, lessonNumber: Number(lesson.number), date: lesson.date, status: 'CLOSED',
      blocked: false, semesterId: attendance.semester.id, subjectId: lesson.subject.id,
      subjectName: lesson.subject.name, subjectType: lesson.type,
      startsAt: lesson.schedule.startsAt, endsAt: lesson.schedule.endsAt,
    }
  }
  const options = missed.map(requestLesson)
  const pendingFor = (id: string): StudentRequestPendingRef[] => [...details.values()]
    .filter(({ summary }) => summary.status === 'PENDING' && summary.lessons.some((lesson) => lesson.id === id))
    .map(({ summary }) => ({ id: summary.id, kind: summary.kind, origin: summary.origin }))
  const studyDays = attendance.days.flatMap((day) => day.date === previewDate ? [] : day.lessons.map((lesson) => ({
    ...schedule.lessons[0]!, id: lesson.id, date: lesson.date, subject: lesson.subject,
    lessonNumber: Number(lesson.number), startsAt: lesson.schedule.startsAt, endsAt: lesson.schedule.endsAt,
    status: lesson.status === 'FUTURE' ? 'PLANNED' as const : 'CLOSED' as const,
    room: { current: lesson.schedule.room, previous: null, changeState: 'UNCHANGED' as const },
  })))
  return async (url, method, readBody, role, body) => {
    const path = url.pathname
    if (role !== 'STUDENT' || !path.startsWith(`${base}/`)) return null
    if (path === `${base}/today` && method === 'GET') return Response.json({
      ...today, lessons: today.lessons.map((lesson) => marked.has(lesson.schedule.id)
        ? { ...lesson, attendance: attendanceStatus, checkinEligibility: { ...lesson.checkinEligibility, allowed: false } }
        : lesson),
    })
    if (path === `${base}/schedule` && method === 'GET')
      return Response.json({ ...schedule, lessons: [...studyDays, ...schedule.lessons] })
    if (path === `${base}/attendance` && method === 'GET') return Response.json({
      ...attendance, days: attendance.days.map((day) => ({ ...day, lessons: day.lessons.map((lesson) => {
        const id = today.lessons.find((item) => day.date === previewDate && item.schedule.lessonNumber === Number(lesson.number))?.schedule.id
        return { ...lesson, status: id && marked.has(id) ? 'PRESENT' : lesson.status, pendingRequest: pendingFor(lesson.id).length > 0 }
      }) })),
    })
    const checkin = path.match(/^\/api\/v1\/student\/lessons\/([^/]+)\/checkin$/)
    if (checkin && method === 'POST') {
      const lesson = today.lessons.find(({ schedule }) => schedule.id === checkin[1])
      if (!lesson?.checkinEligibility.allowed) return missing()
      const command = await readBody()
      if (!command.geo) return invalid()
      marked.add(lesson.schedule.id)
      const response: StudentCheckinAck = {
        lessonId: lesson.schedule.id, outcome: 'PRESENT', attendance: attendanceStatus,
        request: null, retryAt: null, serverNow: previewNow, _links: {},
      }
      return Response.json(response)
    }
    if (path === `${base}/checkin/acquisition-reports` && method === 'POST') return noContent()
    const requests = `${base}/requests`
    if (path === `${requests}/options` && method === 'GET') {
      const used = requestOptions.budget.used + [...details.values()].filter(({ summary }) =>
        Number(summary.id) >= 8001 && summary.kind === 'LATE_CHECKIN' && summary.status !== 'CANCELLED').length
      return Response.json({
        ...requestOptions, budget: { ...requestOptions.budget, used, remaining: Math.max(0, requestOptions.budget.limit - used) },
        lessons: options.map(lesson => {
          const pendingRequests = pendingFor(lesson.id)
          return { lesson, excuseEligible: !pendingRequests.length, lateCheckinEligible: !pendingRequests.length, pendingRequests }
        }),
      })
    }
    if (path === requests && method === 'GET') {
      const page = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('size') ?? 10)
      const open = url.searchParams.get('bucket') !== 'ARCHIVE'
      const summaries = [...details.values()].map(({ summary }) => summary)
        .filter(({ status }) => (status === 'PENDING') === open)
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
      return Response.json({ content: summaries.slice(page * size, (page + 1) * size), page, size, totalElements: summaries.length, totalPages: Math.ceil(summaries.length / size) })
    }
    if (method === 'POST' && (path === `${requests}/excuse` || path === `${requests}/late-checkin`)) {
      const excuse = path.endsWith('/excuse')
      const form = body instanceof FormData ? body : null
      const part = form?.get('request')
      const command = part instanceof Blob ? JSON.parse(await part.text()) as Record<string, unknown> : await readBody()
      const ids: unknown[] = Array.isArray(command.lessonIds) ? command.lessonIds : [command.lessonId]
      const lessons = options.filter(lesson => ids.includes(lesson.id))
      if (!lessons.length || lessons.some(({ id }) => pendingFor(id).length)) return invalid()
      const id = String(nextId++)
      const uploaded = (form?.getAll('files') ?? []).filter((file): file is File => file instanceof File)
      const files = await Promise.all(uploaded.map(async (file, index): Promise<StudentRequestAttachment> => {
        const key = `${id}-${index + 1}`
        attachments.set(key, file)
        const hash = new Uint8Array(await crypto.subtle.digest('SHA-256', await file.arrayBuffer()))
        const sha256 = [...hash].map(byte => byte.toString(16).padStart(2, '0')).join('')
        return { id: key, name: file.name, sizeBytes: file.size, contentType: file.type,
          state: 'ACTIVE', expiredAt: null, expiresAt: '2027-01-01T00:00:00Z', uploadedAt: previewNow, sha256 }
      }))
      const detail: StudentRequestDetail = {
        summary: { id, lessons, kind: excuse ? 'EXCUSE' : 'LATE_CHECKIN', origin: 'MANUAL', status: 'PENDING', createdAt: previewNow, updatedAt: previewNow },
        reason: excuse ? command.reason as StudentRequestDetail['reason'] : null,
        comment: typeof command.comment === 'string' ? command.comment : null,
        decision: null, attachments: files,
      }
      details.set(id, detail)
      return Response.json(detail, { status: 201 })
    }
    const detailPath = path.match(/^\/api\/v1\/student\/requests\/([^/]+)(?:\/(cancel|attachments\/([^/]+)))?$/)
    if (detailPath) {
      const detail = details.get(decodeURIComponent(detailPath[1]!))
      if (!detail) return missing()
      if (method === 'GET' && !detailPath[2]) return Response.json(detail)
      if (method === 'GET' && detailPath[3]) {
        const file = attachments.get(decodeURIComponent(detailPath[3]))
        return file ? new Response(file) : missing()
      }
      if (method === 'POST' && detailPath[2] === 'cancel' && detail.summary.status === 'PENDING') {
        detail.summary.status = 'CANCELLED'
        detail.summary.updatedAt = previewNow
        return Response.json(detail)
      }
    }
    if (path.includes('/attendance/lessons/') && path.includes('/attachments/') && method === 'GET')
      return new Response(previewPdf, { headers: { 'Content-Type': 'application/pdf' } })
    return null
  }
}
