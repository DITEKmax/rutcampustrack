import { groupId, headmanDate, headmanNow, homeworkHistory, homeworks, lessons, plus, previewMarkOverrides, report, requests, scheduleItems, semester, slots, subjects, teachers } from '../../../web-vue/src/dev/headman-visual-data'
import { headmanPreview, weekPolicy } from '../../../web-vue/src/dev/headman-visual-preview'
import type { HeadmanManagedHomework } from '../../../mobile-core/src/features/homework/headman-homework-client'
import type { HeadmanRequestDetail } from '../../../mobile-core/src/features/headman-requests/headman-requests-client'
import type { HeadmanJournalLesson, HeadmanLessonTransferRequest, HeadmanLessonTransferResponse } from '../../../mobile-core/src/features/headman-journal/headman-journal-client'
import type { PreviewHandler } from '../../../pwa-vue/src/dev/interactive-mock-transport'
import type { HeadmanOneOffCreateInput, HeadmanOneOffLesson, HeadmanScheduleItem } from '../../../mobile-core/src/features/schedule/headman-schedule-client'
import type { AssignmentReplacementResponse, HeadmanSubjectAssignment } from '../../../mobile-core/src/features/headman-subjects/headman-subjects-client'

const json = Response.json
const missing = () => json({ detail: 'Демо-запись не найдена.' }, { status: 404 })
const noContent = () => new Response(null, { status: 204 })

function page(url: URL, items: readonly unknown[]) {
  const number = Number(url.searchParams.get('page') ?? 0)
  const size = Number(url.searchParams.get('size') ?? 100)
  return { rows: items.slice(number * size, (number + 1) * size), number, size, totalElements: items.length, totalPages: Math.ceil(items.length / size) }
}

export function createHeadmanDemo(): PreviewHandler {
  const homework = new Map(homeworks.map(item => [item.id, structuredClone(item)]))
  const inbox = new Map(requests.map(item => [item.summary.id, structuredClone(item)]))
  const schedule = new Map(lessons.map(item => [item.id, structuredClone(item)]))
  const subjectRows = new Map(subjects.map(item => [item.id, structuredClone(item)]))
  const transfers = new Map<string, HeadmanLessonTransferResponse>()
  const replacements = new Map<string, AssignmentReplacementResponse>()
  const series = new Map(scheduleItems.filter(item => item.id !== undefined).map(item => [item.id!, structuredClone(item)]))
  const occurrences = new Map<number, number[]>()
  const oneOff = new Map<number, HeadmanOneOffLesson>()
  const homeworkChanges = new Map<number, ReturnType<typeof homeworkHistory>>()
  let nextHomework = 10000
  let nextLesson = 29000
  let nextSubject = 120
  let nextItem = 39000
  let nextAssignment = 1000
  function generate(item: HeadmanScheduleItem): string[] {
    const dates: string[] = []
    const type = subjectRows.get(item.subjectId ?? -1)?.assignments.find(row => row.id === item.assignmentId)?.lessonType ?? 'LECTURE'
    for (let date = headmanDate; date <= semester.dateTo; date = plus(date, 1)) {
      const weekday = new Date(`${date}T12:00:00Z`).getUTCDay() || 7
      if (weekday !== item.dayOfWeek || (item.weekType !== 'ALL' && item.weekType !== weekPolicy(date).weekType)) continue
      if ([...schedule.values()].some(row => row.date === date && row.lessonNumber === item.lessonNumber && row.current)) continue
      const id = nextLesson++
      schedule.set(id, { id, groupId, subjectId: item.subjectId ?? null, date,
        lessonNumber: item.lessonNumber ?? null, startTime: item.startTime ?? null, endTime: item.endTime ?? null,
        room: item.room ?? null, lessonType: type, status: 'PLANNED', blockedByHeadman: false,
        current: true, occurrenceId: String(id + 100_000), occurrenceRevision: '1', transferOperationId: null, transferState: null })
      const ids = occurrences.get(item.id!) ?? []
      ids.push(id)
      occurrences.set(item.id!, ids)
      dates.push(date)
    }
    return dates
  }
  function removeGenerated(id: number) {
    for (const lessonId of occurrences.get(id) ?? []) schedule.delete(lessonId)
    occurrences.delete(id)
  }
  return async (url, method, readBody, role, source) => {
    if (role !== 'HEADMAN') return null
    const path = url.pathname
    const formPart = source instanceof FormData ? source.get('request') : null
    const body = async () => formPart instanceof Blob ? JSON.parse(await formPart.text()) as Record<string, unknown> : readBody()
    if (path === '/api/schedule/items' && method === 'GET') {
      const { rows, ...metadata } = page(url, [...series.values()])
      return json({ _embedded: { scheduleItemResponseList: rows }, page: metadata })
    }
    if (path === '/api/schedule/items' && method === 'POST') {
      const command = await body() as HeadmanScheduleItem
      const record = { ...command, id: nextItem++, active: true, transfers: [] }
      series.set(record.id, record)
      const dates = generate(record)
      return json({ ...record, generatedCount: dates.length, generatedFrom: dates[0] ?? null, generatedUntil: dates.at(-1) ?? null }, { status: 201 })
    }
    const itemPath = path.match(/^\/api\/schedule\/items\/(\d+)(\/lifecycle-preview)?$/)
    if (itemPath) {
      const id = Number(itemPath[1])
      const item = series.get(id)
      if (!item) return missing()
      const count = occurrences.get(id)?.length ?? 0
      if (method === 'GET' && !itemPath[2]) return json(item)
      if (method === 'POST' && itemPath[2]) {
        const deletion = url.searchParams.get('delete') === 'true'
        const protectedCount = id < 39000 ? 1 : 0
        return json({ revision: 'a'.repeat(64), updatedCount: deletion ? 0 : count, removedCount: deletion ? count : 0, restoredCount: 0, createdCount: 0,
          ...(deletion ? { operationKind: 'DELETE_UNUSED_SERIES', deletable: !protectedCount, protectedCount,
            removableCount: count, protectedReasons: protectedCount ? [{ code: 'MANUAL_ATTENDANCE', count: 1 }] : [] } : {}) })
      }
      if (method === 'PUT') {
        const record = { ...item, ...await body(), id, transfers: [] }
        if (id >= 39000) {
          removeGenerated(id)
          generate(record)
        } else {
          for (const [lessonId, lesson] of schedule) {
            const weekday = new Date(`${lesson.date}T12:00:00Z`).getUTCDay() || 7
            if (lesson.status !== 'PLANNED' || weekday !== item.dayOfWeek || lesson.lessonNumber !== item.lessonNumber || lesson.subjectId !== item.subjectId) continue
            schedule.set(lessonId, { ...lesson, room: record.room ?? null })
          }
        }
        series.set(id, record)
        return json(record)
      }
      if (method === 'DELETE' && id >= 39000) {
        series.delete(id)
        removeGenerated(id)
        return noContent()
      }
    }
    if (path === '/api/schedule/one-off-lessons' && method === 'GET') {
      const { rows, ...metadata } = page(url, [...oneOff.values()].filter(item => item.date >= (url.searchParams.get('dateFrom') ?? '') && item.date <= (url.searchParams.get('dateTo') ?? '9999-01-01')))
      return json({ _embedded: { oneOffLessonResponseList: rows }, page: metadata })
    }
    if (path === '/api/schedule/one-off-lessons' && method === 'POST') {
      const command = await body() as unknown as HeadmanOneOffCreateInput
      const id = nextLesson++
      const row = { ...command, id, physicalLessonId: id, semesterId: semester.id, classroom: command.classroom ?? null }
      oneOff.set(id, row)
      const lessonType = subjectRows.get(command.subjectId)?.assignments.find(item => item.id === command.assignmentId)?.lessonType ?? 'LECTURE'
      schedule.set(id, { id, groupId, subjectId: command.subjectId, lessonType, date: command.date,
        lessonNumber: command.lessonNumber, startTime: command.startTime, endTime: command.endTime,
        room: command.classroom ?? null, status: 'PLANNED', current: true, occurrenceId: String(id + 100_000),
        occurrenceRevision: '1', transferState: null, transferOperationId: null })
      return json(row, { status: 201 })
    }
    const hw = '/api/academic/homeworks'
    if (path === hw) {
      if (method === 'POST') {
        const command = await body() as unknown as Partial<HeadmanManagedHomework>
        const existing = [...homework.values()].find(({ requestKey }) => requestKey === command.requestKey)
        if (existing) return json(existing)
        const id = nextHomework++
        const item = { ...command, id, bindingId: id + 1000, archived: false, publishedBy: 42, revision: 1 } as HeadmanManagedHomework
        homework.set(id, item)
        homeworkChanges.set(id, [{ id: id * 10, revision: 1, actorId: 42, occurredAt: headmanNow, action: 'CREATED', before: null, after: item }])
        return json(item, { status: 201 })
      }
      if (method === 'GET') {
        const archived = url.searchParams.get('archived') === 'true'
        const result = page(url, [...homework.values()].filter(item => item.archived === archived))
        const { rows, ...metadata } = result
        return json({ _embedded: { homeworkResponseList: rows }, page: metadata })
      }
    }
    const homeworkPath = path.match(/^\/api\/academic\/homeworks\/(\d+)(\/history)?$/)
    if (homeworkPath) {
      const id = Number(homeworkPath[1])
      const item = homework.get(id)
      if (!item) return missing()
      const history = homeworkChanges.get(id) ?? homeworkHistory(item)
      if (method === 'GET') return homeworkPath[2]
        ? json({ content: history, page: { number: 0, size: 100, totalElements: history.length, totalPages: 1 } })
        : json(item)
      if (method === 'DELETE') {
        homework.delete(id)
        return noContent()
      }
      if (method === 'PUT') {
        const command = await body() as Partial<HeadmanManagedHomework>
        const revision = (item.revision ?? 0) + 1
        const updated = { ...item, ...command, id, revision }
        homework.set(id, updated)
        homeworkChanges.set(id, [{ id: id * 10 + revision, revision, actorId: 42,
          occurredAt: headmanNow, action: 'UPDATED', before: item, after: updated }, ...history])
        return json(updated)
      }
    }
    const subjectPath = '/api/academic/subjects'
    if (path === '/api/academic/assignments' && method === 'GET') {
      const assignments = [...subjectRows.values()].flatMap(subject => subject.assignments.map(item => ({ ...item, groupId,
        subjectId: subject.id, subjectName: subject.name, teacherName: teachers.find(teacher => teacher.id === item.teacherId)?.fullName,
      })))
      const { rows, ...metadata } = page(url, assignments.sort((left, right) => Number(left.id === 302) - Number(right.id === 302)))
      return json({ _embedded: { assignmentResponseList: rows }, page: metadata })
    }
    if (path === subjectPath && method === 'GET') {
      const { rows, ...metadata } = page(url, [...subjectRows.values()])
      return json({ _embedded: { subjectResponseList: rows }, page: metadata })
    }
    if (path === subjectPath && method === 'POST') {
      const command = await body() as unknown as typeof subjects[number] & { initialAssignments?: typeof subjects[number]['assignments'] }
      const assignments = (command.initialAssignments ?? []).map(row => ({ ...row, id: nextAssignment++, validUntilExclusive: null }))
      const row = { ...command, id: nextSubject++, groupId, teacherIds: assignments.map(row => row.teacherId), assignments }
      subjectRows.set(row.id, row)
      return json(row, { status: 201 })
    }
    const teacher = path.match(/^\/api\/academic\/subjects\/(\d+)\/teachers\/(\d+)$/)
    if (teacher) {
      const subject = subjectRows.get(Number(teacher[1]))
      const teacherId = Number(teacher[2])
      if (!subject || !teachers.some(row => row.id === teacherId)) return missing()
      if (method === 'POST') {
        const command = await body() as unknown as Omit<HeadmanSubjectAssignment, 'id' | 'teacherId'>
        const assignment = { ...command, id: nextAssignment++, teacherId, validUntilExclusive: null }
        subjectRows.set(subject.id, { ...subject, assignments: [...subject.assignments, assignment],
          teacherIds: [...new Set([...subject.teacherIds, teacherId])] })
        return json(assignment, { status: 201 })
      }
      if (method === 'DELETE') {
        const id = Number(url.searchParams.get('assignmentId'))
        const validUntilExclusive = url.searchParams.get('validUntilExclusive')
        subjectRows.set(subject.id, { ...subject, assignments: subject.assignments.map(row => row.id === id ? { ...row, validUntilExclusive } : row) })
        return noContent()
      }
    }
    const replacement = path.match(/^\/api\/academic\/assignments\/(\d+)\/replace$/)
    if (replacement && method === 'POST') {
      const id = Number(replacement[1])
      const subject = [...subjectRows.values()].find(row => row.assignments.some(item => item.id === id))
      const original = subject?.assignments.find(item => item.id === id)
      if (!subject || !original) return missing()
      const command = await body()
      const teacherId = Number(command.replacementTeacherId)
      const date = String(command.effectiveFrom)
      const operationId = String(command.requestKey)
      const prior = replacements.get(operationId)
      if (prior) return json(prior)
      const next = { ...original, id: nextAssignment++, validFrom: date, teacherId }
      subjectRows.set(subject.id, { ...subject, teacherIds: [...new Set([...subject.teacherIds, teacherId])],
        assignments: [...subject.assignments.map(row => row.id === id ? { ...row, validUntilExclusive: date } : row), next] })
      const outcome: AssignmentReplacementResponse = { operationId, sourceAssignmentId: id, targetAssignmentId: next.id,
        sourceTeacherId: original.teacherId, targetTeacherId: teacherId, subjectId: subject.id, groupId,
        semesterId: next.semesterId, lessonType: next.lessonType, effectiveFrom: date, sourceValidUntilExclusive: date,
        targetValidUntilExclusive: next.validUntilExclusive, state: 'COMPLETED', scheduleReceiptState: 'COMPLETED', movedCount: 0, skippedCount: 0 }
      replacements.set(operationId, outcome)
      return json(outcome)
    }
    const replacementStatus = path.match(/^\/api\/academic\/assignments\/replacements\/([^/]+)$/)
    if (replacementStatus && method === 'GET') {
      const outcome = replacements.get(replacementStatus[1]!)
      return outcome ? json(outcome) : missing()
    }
    const subject = path.match(/^\/api\/academic\/subjects\/(\d+)$/)
    if (subject) {
      const id = Number(subject[1])
      const existing = subjectRows.get(id)
      if (!existing) return missing()
      if (method === 'GET') return json(existing)
      if (method === 'PUT') {
        const updated = { ...existing, ...await body(), id }
        subjectRows.set(id, updated)
        return json(updated)
      }
    }
    const requestPath = '/api/attendance/requests'
    if (path === requestPath && method === 'GET') {
      const query = (key: string) => url.searchParams.get(key) ?? ''
      const archived = query('bucket') === 'ARCHIVE'
      const selected = [...inbox.values()].map(({ summary }) => summary).filter(item =>
        (item.status !== 'PENDING') === archived &&
        (!query('type') || item.kind === query('type')) &&
        (item.studentName ?? '').toLocaleLowerCase('ru').includes(query('studentName').toLocaleLowerCase('ru')) &&
        (!query('coverageDateFrom') || (item.coverageEnd ?? '') >= query('coverageDateFrom')) &&
        (!query('coverageDateTo') || (item.coverageStart ?? '') <= query('coverageDateTo')))
        .sort((a, b) => (archived ? b.decisionAt ?? '' : b.createdAt ?? '').localeCompare(archived ? a.decisionAt ?? '' : a.createdAt ?? ''))
      const { rows, number, ...metadata } = page(url, selected)
      return json({ content: rows, page: number, ...metadata })
    }
    const request = path.match(/^\/api\/attendance\/requests\/([^/]+)(\/decision)?$/)
    if (request) {
      const item = inbox.get(decodeURIComponent(request[1]!))
      if (!item) return missing()
      if (method === 'GET' && !request[2]) return json(item)
      if (method === 'POST' && request[2] && item.summary.status === 'PENDING') {
        const { decision, reason, excludedOccurrenceIds, requestKey, expectedRevision } = await body()
        if (decision !== 'APPROVED' && decision !== 'REJECTED') return missing()
        const affected = item.currentAffectedLessons ?? item.lessons
        const excluded = new Set(Array.isArray(excludedOccurrenceIds) ? excludedOccurrenceIds : [])
        const includedLessons = affected.filter(row => !excluded.has(row.occurrenceId))
        if (decision === 'APPROVED') for (const row of includedLessons) {
          const lesson = schedule.get(row.lessonId ?? -1)
          const student = item.summary.studentId
          const current = lesson && report(lesson).entries.find(entry => entry.userId === student)
          if (!lesson || student === null || !current) continue
          const key = `${lesson.id}/${student}`
          const revision = (previewMarkOverrides.get(key)?.revision ?? 1) + 1
          const status = item.summary.kind === 'EXCUSE' ? 'EXCUSED' : 'PRESENT'
          previewMarkOverrides.set(key, { revision, entry: { ...current, status, editable: false,
            approvedTicket: { id: item.summary.id, kind: item.summary.kind, status: 'APPROVED' },
            timestamp: headmanNow, markRevision: `preview-${key}-${revision}` } })
        }
        const next: HeadmanRequestDetail = { ...item, summary: { ...item.summary, status: decision,
          updatedAt: headmanNow, decisionAt: headmanNow, decisionBy: 42, decisionComment: typeof reason === 'string' ? reason : null },
          ...(typeof requestKey === 'string' && typeof expectedRevision === 'string' ? {
            decisionSelection: { requestKey, expectedRevision, includedLessons, excludedLessons: affected.filter(row => excluded.has(row.occurrenceId)), beforeLessons: affected },
          } : {}),
        }
        inbox.set(item.summary.id, next)
        return json(next)
      }
    }
    const actions = path.match(/^\/api\/schedule\/lessons\/(\d+)\/(blockage|cancel|restore|room|transfer)$/)
    if (actions && method !== 'GET') {
      const id = Number(actions[1]), item = schedule.get(id)
      if (!item) return missing()
      const action = actions[2]
      const revision = String(Number(item.occurrenceRevision ?? '1') + 1)
      const outcome: HeadmanJournalLesson = { ...item, occurrenceRevision: revision }
      if (action === 'transfer') {
        const command = await body() as unknown as HeadmanLessonTransferRequest
        const slot = slots.find(slot => slot.lessonNumber === command.targetLessonNumber)!
        const operationId = command.requestKey
        const prior = transfers.get(operationId)
        if (prior) return json(prior)
        const target = { ...outcome, id: nextLesson++, date: command.targetDate,
          lessonNumber: slot.lessonNumber, startTime: command.targetStartTime ?? slot.startTime,
          endTime: command.targetEndTime ?? slot.endTime, room: command.targetRoom ?? item.room,
          status: 'PLANNED' as const, transferState: 'COMPLETED' as const, transferOperationId: operationId }
        schedule.set(id, { ...outcome, current: false, status: 'TRANSFERRED', transferState: target.transferState, transferOperationId: operationId })
        schedule.set(target.id, target)
        const result: HeadmanLessonTransferResponse = { operationId, state: 'COMPLETED', occurrenceId: item.occurrenceId!,
          sourceLessonId: String(id), targetLessonId: String(target.id), targetDate: target.date, revision, retryable: false, errorCode: null }
        transfers.set(operationId, result)
        return json(result)
      }
      if (action === 'room') {
        const command = await body()
        const room = typeof command.room === 'string' ? command.room : null
        schedule.set(id, { ...outcome, room })
        return json({ state: 'COMPLETED', lessonId: String(id), occurrenceId: item.occurrenceId, operationId: crypto.randomUUID(), revision, room })
      }
      const updated = action === 'blockage' ? { ...outcome, blockedByHeadman: method === 'POST' }
        : { ...outcome, status: action === 'cancel' ? 'CANCELLED' as const : 'PLANNED' as const }
      schedule.set(id, updated)
      return json(updated)
    }
    const transfer = path.match(/^\/api\/schedule\/lesson-transfers\/([^/]+)$/)
    if (transfer && method === 'GET') return transfers.has(transfer[1]!) ? json(transfers.get(transfer[1]!)) : missing()
    if (path === `/api/schedule/groups/${groupId}/lessons` && method === 'GET') {
      const baseline = await headmanPreview(url, method, body)
      const from = url.searchParams.get('dateFrom') ?? '', to = url.searchParams.get('dateTo') ?? '9999-12-31'
      const { rows, ...metadata } = page(url, [...schedule.values()].filter(item => item.date >= from && item.date <= to)
        .sort((a, b) => a.date.localeCompare(b.date) || (a.lessonNumber ?? 0) - (b.lessonNumber ?? 0)))
      return json({ ...(await baseline?.json()), _embedded: { lessonResponseList: rows }, page: metadata })
    }
    const roster = path.match(/^\/api\/attendance\/reports\/lesson\/(\d+)$/)
    if (roster && method === 'GET') {
      const item = schedule.get(Number(roster[1]))
      if (!item) return missing()
      const data = report(item)
      return json({ ...data, entries: data.entries.map(entry => ({ ...entry, status: entry.status === 'EMPTY' ? null : entry.status })) })
    }
    if (formPart && /^\/api\/attendance\/lessons\/\d+\/students\/\d+$/.test(path))
      return headmanPreview(url, method, body)
    return null
  }
}
