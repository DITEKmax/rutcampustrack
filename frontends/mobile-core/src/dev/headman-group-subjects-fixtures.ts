import { HeadmanGroupApi } from '../features/headman-group/headman-group-client'
import { HeadmanSubjectsApi, type HeadmanPendingClosure } from '../features/headman-subjects/headman-subjects-client'
import { syntheticAttachmentResponse } from './attachment-preview-fixture'
export const headmanGroupSubjectsStates = ['group-list', 'group-empty', 'group-member', 'group-ordinary', 'group-permissions', 'group-dirty-leave', 'group-revoke-confirm', 'group-permission-confirm', 'group-permission-stale', 'group-permission-unknown', 'group-read-error', 'group-offline', 'group-readonly', 'subjects-list', 'subjects-editor', 'subjects-picker', 'subjects-remove-confirm', 'subjects-new', 'subjects-unknown', 'subjects-partial', 'subjects-removal-pending', 'subjects-adoption-pending', 'subjects-dirty-leave', 'subjects-readonly', 'subjects-historical', 'subjects-historical-detail', 'subjects-read-error', 'subjects-create-rejected', 'subjects-closure-recovery'] as const
export function createGroupSubjectsApis(state: string) {
  const names = ['Анна Иванова', 'Борис Алексеев', 'Мария Васильева', 'Алексей Громов', 'Валерия Ушакова', 'Артём Карпов', 'Илья Соколов', 'Вероника Белова', 'Виктория Павлова', 'Глеб Фёдоров', 'Даниил Морозов', 'Евгения Крылова', 'Егор Никитин', 'Елена Смирнова', 'Олег Волков', 'Антон Орлов', 'Ирина Павлова', 'Павел Белов', 'Лев Фролов', 'Лидия Соколова', 'Сергей Петров', 'Алина Кузнецова', 'Дмитрий Николаев', 'Нина Егорова', 'Роман Зайцев', 'Ольга Морозова', 'Фёдор Мельников', 'Юлия Громова', 'Яна Лебедева', 'Ярослав Романов']
  const members = names.map((fullName, index) => ({ id: index + 1, fullName, login: `student${index + 1}`, status: 'ACTIVE', groupId: 4, roles: [{ role: index === 0 ? 'HEADMAN' : 'STUDENT', status: 'ACTIVE', groupId: 4 }] }))
  const permissions = [{ code: 'MARK_ATTENDANCE', label: 'Отмечать посещаемость' }, { code: 'MANAGE_EXCUSES', label: 'Управлять уважительными причинами' }, { code: 'MANAGE_HOMEWORK', label: 'Управлять домашними заданиями' }, { code: 'CANCEL_LESSONS', label: 'Отменять пары' }, { code: 'VIEW_STATS', label: 'Просматривать статистику' }]
  const assistants = [{ id: 11, studentId: 2, studentName: names[1], login: 'student2', groupId: 4, permissions: ['MARK_ATTENDANCE', 'MANAGE_EXCUSES', 'VIEW_STATS'], active: true, assignedAt: '2026-09-01T08:00:00Z', revokedAt: null }]
  const teachers = [{ id: 101, fullName: 'Кузнецов Михаил Андреевич', employeeNumber: '10482' }, { id: 102, fullName: 'Орлова Елена Викторовна', employeeNumber: '11832' }, { id: 103, fullName: 'Волков Дмитрий Николаевич', employeeNumber: '12745' }, { id: 104, fullName: 'Петрова Анна Сергеевна', employeeNumber: '12896' }]
  const subjects = ['Информационные системы', 'Высшая математика', 'Архитектура ЭВМ', 'Базы данных', 'Программирование'].map((name, index) => ({ id: index + 1, name, type: 'LECTURE', groupId: 4, lessonTypes: ['LECTURE', 'PRACTICE', 'LAB'], teacherIds: [101, 102], pendingClosures: [] as HeadmanPendingClosure[], pendingAdoptions: [] as { targetAssignmentId: number; adoptionOperationId: string; state: string; teacherId: number; subjectId: number; groupId: number; semesterId: number; lessonType: string; validFrom: string; validUntilExclusive: string | null }[], assignments: [{ id: 100 + index * 10, teacherId: 101, semesterId: 1, lessonType: 'LECTURE', validFrom: '2026-09-01', validUntilExclusive: null }, { id: 101 + index * 10, teacherId: 102, semesterId: 1, lessonType: 'PRACTICE', validFrom: '2026-09-01', validUntilExclusive: null }] }))
  if (state === 'subjects-closure-recovery') { const subject = subjects[0]!; subject.assignments = subject.assignments.filter((a) => a.id !== 100); subject.pendingClosures = [{ operationId: '33333333-3333-4333-8333-333333333333', assignmentId: 100, requestKey: '11111111-1111-4111-8111-111111111111', validUntilExclusive: '2026-10-05', teacherId: 101, subjectId: 1, groupId: 4, semesterId: 1, lessonType: 'LECTURE' }] }
  let removalPendingSent = false, metadataLostSent = false, readFailed = false
  const operationId = '22222222-2222-4222-8222-222222222222'
  const visibleSubject = (subject: typeof subjects[number], detail = false) => {
    const assignments = subject.assignments.filter((a) => !subject.pendingAdoptions.some((h) => h.targetAssignmentId === a.id && h.state === 'PREPARED'))
    return { ...subject, assignments, teacherIds: [...new Set(assignments.map((a) => a.teacherId))], pendingAdoptions: detail ? subject.pendingAdoptions : undefined, pendingClosures: detail ? subject.pendingClosures : undefined }
  }
  const requests: { method: string; path: string; body: unknown; key: string | null }[] = []
  const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
  const page = (key: string, items: unknown[]) => json({ _embedded: { [key]: items }, page: { number: 0, size: 100, totalElements: items.length, totalPages: 1 } })
  const fetcher: typeof fetch = async (input, init) => {
    const url = new URL(String(input), 'http://synthetic.invalid'), method = init?.method ?? 'GET'
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) as Record<string, unknown> : null
    requests.push({ method, path: url.pathname + url.search, body, key: new Headers(init?.headers).get('Idempotency-Key') })
    document.documentElement.dataset.groupSubjectsRequests = JSON.stringify(requests)
    if (url.pathname === '/api/academic/groups/my/members') return state === 'group-read-error' ? json({ detail: 'Синтетический сбой чтения состава группы.' }, 503) : page('userResponseList', state === 'group-empty' ? [] : members)
    if (url.pathname === '/api/academic/assistants/permissions') return json(permissions)
    if (url.pathname === '/api/academic/assistants' && method === 'GET') return page('assistantResponseList', assistants)
    if (url.pathname === '/api/academic/assistants' && method === 'POST') { const item = { ...assistants[0]!, id: 12, studentId: Number(body!.studentId), permissions: body!.permissions as string[], studentName: names[Number(body!.studentId) - 1] }; assistants.push(item); return json(item, 201) }
    if (url.pathname.startsWith('/api/academic/assistants/') && method === 'PATCH') { if (state === 'group-permission-unknown') throw new TypeError('Синтетическая потеря ответа на сохранение.'); const item = assistants.find((a) => String(a.id) === url.pathname.split('/')[4])!; item.permissions = body!.permissions as string[]; return json(item) }
    if (url.pathname.startsWith('/api/academic/assistants/') && method === 'DELETE') { const item = assistants.find((a) => String(a.id) === url.pathname.split('/').at(-1))!; item.active = false; return new Response(null, { status: 204 }) }
    if (url.pathname.endsWith('/group-composition/formats')) return json({ formats: [{ code: 'pdf', label: 'PDF', contentType: 'application/pdf', extension: 'pdf' }] })
    if (url.pathname.endsWith('/group-composition/export')) return syntheticAttachmentResponse('pdf')
    if (url.pathname === '/api/academic/subjects' && method === 'GET') {
      if (state === 'subjects-read-error' && !readFailed) { readFailed = true; return json({ detail: 'Синтетический сбой первой загрузки предметов.' }, 503) }
      return page('subjectResponseList', subjects.map((subject) => visibleSubject(subject)))
    }
    if (/^\/api\/academic\/subjects\/\d+$/.test(url.pathname) && method === 'GET') { const subject = subjects.find((s) => s.id === Number(url.pathname.split('/').at(-1)))!; return json(visibleSubject(subject, true)) }
    if (/^\/api\/academic\/subjects\/\d+$/.test(url.pathname) && method === 'PUT') { if (state === 'subjects-unknown' && !metadataLostSent) { metadataLostSent = true; throw new TypeError('Synthetic response lost'); } const subject = subjects.find((s) => s.id === Number(url.pathname.split('/').at(-1)))!; subject.name = String(body!.name); subject.type = String(body!.type); subject.lessonTypes = body!.lessonTypes as string[]; return json(subject) }
    if (url.pathname === '/api/academic/subjects' && method === 'POST') { if (state === 'subjects-create-rejected') return json({ detail: 'Синтетический отказ атомарного создания.' }, 409); const assignments = (body!.initialAssignments as Record<string, unknown>[]).map((a, i) => ({ id: 400 + i, teacherId: Number(a.teacherId), semesterId: Number(a.semesterId), lessonType: String(a.lessonType), validFrom: String(a.validFrom), validUntilExclusive: null })); const item = { id: 6, name: String(body!.name), type: String(body!.type), groupId: 4, lessonTypes: body!.lessonTypes as string[], teacherIds: assignments.map((a) => a.teacherId), pendingAdoptions: [], pendingClosures: [], assignments }; subjects.push(item); return json(item, 201) }
    if (url.pathname.includes('/teachers/') && method === 'POST') { if (state === 'subjects-partial') return json({ detail: 'Синтетический отказ второго шага после сохранения названия.' }, 409); const subject = subjects.find((s) => s.id === Number(url.pathname.split('/')[4]))!; const teacherId = Number(url.pathname.split('/').at(-1)); subject.teacherIds.push(teacherId); subject.assignments.push({ id: 500, teacherId, semesterId: Number(body!.semesterId), lessonType: String(body!.lessonType), validFrom: String(body!.validFrom), validUntilExclusive: null }); if (state === 'subjects-adoption-pending') { subject.pendingAdoptions = [{ targetAssignmentId: 500, adoptionOperationId: operationId, state: 'PREPARED', ...subject.assignments.at(-1)!, subjectId: subject.id, groupId: subject.groupId }]; return json({ type: 'urn:rct:assignment-adoption-pending', detail: 'Синтетическое применение назначения ожидает завершения.', extras: { subjectId: subject.id, pendingAdoptions: [{ targetAssignmentId: 500, adoptionOperationId: operationId }] } }, 503) } return json({ id: 500 }, 201) }
    if (url.pathname.includes('/teachers/') && method === 'DELETE') {
      const subject = subjects.find((s) => s.id === Number(url.pathname.split('/')[4]))!, assignmentId = Number(url.searchParams.get('assignmentId')), requestKey = new Headers(init?.headers).get('Idempotency-Key')
      if (!requestKey || !url.searchParams.get('validUntilExclusive')) return json({ detail: 'Требуются исходные ключ и дата.' }, 400)
      if (state === 'subjects-removal-pending' && !removalPendingSent) { removalPendingSent = true; return json({ type: 'urn:rct:assignment-closure-pending', extras: { assignmentId, requestKey } }, 503) }
      const handle = subject.pendingClosures.find((h) => h.assignmentId === assignmentId)
      if (handle && (handle.requestKey !== requestKey || handle.validUntilExclusive !== url.searchParams.get('validUntilExclusive'))) return json({ extras: { code: 'IDEMPOTENCY_PAYLOAD_MISMATCH' } }, 409)
      subject.pendingClosures = subject.pendingClosures.filter((h) => h.assignmentId !== assignmentId)
      subject.assignments = subject.assignments.filter((a) => a.id !== assignmentId); subject.teacherIds = [...new Set(subject.assignments.map((a) => a.teacherId))]
      return new Response(null, { status: 204 })
    }
    if (/^\/api\/academic\/assignments\/\d+\/resume-adoption$/.test(url.pathname) && method === 'POST') {
      const id = Number(url.pathname.split('/')[4]), subject = subjects.find((s) => s.assignments.some((a) => a.id === id))!, assignment = subject?.assignments.find((a) => a.id === id)
      if (!subject || !assignment) return json({ detail: 'Назначение не найдено.' }, 404)
      subject.pendingAdoptions = subject.pendingAdoptions.filter((h) => h.targetAssignmentId !== id)
      return json({ ...assignment, subjectId: subject.id, groupId: subject.groupId })
    }
    if (url.pathname === '/api/academic/semesters') return page('semesterResponseList', [{ id: 1, name: 'Осенний семестр 2026', dateFrom: '2026-09-01', dateTo: '2027-01-31', active: true }, { id: 2, name: 'Весенний семестр 2026', dateFrom: '2026-02-01', dateTo: '2026-06-30', active: false }])
    if (url.pathname === '/api/academic/users/by-ids') return page('userSummaryResponseList', teachers.filter((teacher) => url.searchParams.get('ids')?.split(',').includes(String(teacher.id))))
    if (url.pathname === '/api/academic/users/teachers/search') return page('teacherLookupResponseList', teachers.filter((teacher) => `${teacher.fullName} ${teacher.employeeNumber}`.toLocaleLowerCase('ru').includes((url.searchParams.get('search') ?? '').toLocaleLowerCase('ru'))))
    return json({ detail: `Synthetic route unavailable: ${method} ${url.pathname}` }, 404)
  }
  return { changeAssistantPermissions: () => { assistants[0]!.permissions = ['MARK_ATTENDANCE', 'MANAGE_HOMEWORK', 'VIEW_STATS'] }, group: new HeadmanGroupApi({ accessToken: () => null, fetcher }), subjects: new HeadmanSubjectsApi({ accessToken: () => null, fetcher }), requests }
}
