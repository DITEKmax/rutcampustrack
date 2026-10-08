import type { AdminAccessProfile } from '../features/admin-access/admin-access-client'
import { AdminSemesterClient } from '../features/admin-semester/admin-semester-client'
export const adminSemesterStates = ['missing-authority', 'no-delete-permission', 'read-only', 'revoked', 'list', 'create', 'edit', 'start-calendar', 'end-calendar', 'spring', 'overlap', 'activation-confirm', 'activated', 'archive-confirm', 'archive-unknown', 'archiving', 'archived', 'restore-confirm', 'restored', 'delete-confirm', 'delete-password', 'delete-password-extras', 'delete-forbidden', 'delete-poll-forbidden', 'delete-resume-forbidden', 'delete-unknown', 'delete-progress', 'delete-background', 'delete-background-completed', 'delete-background-resume', 'delete-completed', 'delete-stale', 'delete-expired', 'no-active', 'legacy', 'empty', 'error', 'forbidden'] as const
const fullAdminPermissions = ['DASHBOARD_VIEW', 'USERS_VIEW', 'USERS_MANAGE', 'USER_CREDENTIALS_MANAGE', 'USERS_ARCHIVE', 'GROUPS_VIEW', 'GROUPS_MANAGE', 'SEMESTERS_VIEW', 'SEMESTERS_MANAGE', 'CAMPUS_MAP_MANAGE', 'DATA_DELETE', 'ADMIN_ACCESS_MANAGE'] as const
export function adminSemesterAuthority(state: string): AdminAccessProfile | null { return state === 'missing-authority' ? null : { userId: 1, assigned: true, mode: ['read-only', 'no-delete-permission'].includes(state) ? 'LIMITED' : 'FULL', permissions: state === 'no-delete-permission' ? ['SEMESTERS_VIEW', 'SEMESTERS_MANAGE'] : state === 'read-only' ? ['SEMESTERS_VIEW'] : fullAdminPermissions, version: 1 } }
export function createAdminSemesterFixture(state: string): AdminSemesterClient {
  let reads = 0
  let deletionPolls = 0
  document.documentElement.dataset.adminFixtureRequests = '0'
  const semesters = [
    { id: 1, name: 'Осенний 2026/2027', dateFrom: '2026-09-01', dateTo: '2027-01-31', active: state !== 'no-active', createdAt: '2026-08-20T00:00:00Z', archived: false, transition: 'NONE', stateVersion: 1, releasePending: false, writeBlocked: false, semesterType: state === 'legacy' ? null : 'AUTUMN', academicYear: state === 'legacy' ? null : 2026 },
    { id: 2, name: 'Весенний 2026/2027', dateFrom: '2027-02-09', dateTo: '2027-06-30', active: false, createdAt: '2026-08-20T00:00:00Z', archived: false, transition: 'NONE', stateVersion: 1, releasePending: false, writeBlocked: false, semesterType: 'SPRING', academicYear: 2026 },
    { id: 3, name: 'Весенний 2025/2026', dateFrom: '2026-02-09', dateTo: '2026-06-30', active: false, createdAt: '2026-02-01T00:00:00Z', archived: true, transition: 'NONE', stateVersion: 2, releasePending: false, writeBlocked: true, semesterType: 'SPRING', academicYear: 2025 },
    { id: 4, name: 'Осенний 2025/2026', dateFrom: '2025-09-01', dateTo: '2026-01-31', active: false, createdAt: '2025-08-20T00:00:00Z', archived: true, transition: 'NONE', stateVersion: 2, releasePending: false, writeBlocked: true, semesterType: 'AUTUMN', academicYear: 2025 },
  ]
  const counts = { scheduleTemplates: 12, oneOffLessons: 4, lessons: 160, assignments: 8, homeworks: 24, attendanceMarks: 2350, studentRequests: 16 }
  const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
  const scope = 'synthetic-admin-semester-' + state
  window.sessionStorage.removeItem('rct:admin:semester-delete:v1:' + encodeURIComponent(scope))
  let archiveOperation: Record<string, unknown> | null = null
  let deletionOperation: Record<string, unknown> | null = null
  const preview = (id: number, changed = false) => ({ semesterId: id, semesterName: semesters.find(item => item.id === id)?.name ?? 'Учебный период', priorState: id === 1 ? 'ACTIVE' : id >= 3 ? 'ARCHIVED' : 'INACTIVE', stateVersion: changed ? 2 : 1, previewDigest: changed ? 'synthetic-preview-2' : 'synthetic-preview-1', counts: changed ? { ...counts, attendanceMarks: 2352 } : counts })
  if (state === 'delete-resume-forbidden') window.sessionStorage.setItem('rct:admin:semester-delete:v1:' + encodeURIComponent(scope), JSON.stringify({ version: 1, scope, semesterId: 1, preview: preview(1), idempotencyKey: '30100000-0000-4000-8000-000000000001', operationId: '20100000-0000-4000-8000-000000000001' }))
  return new AdminSemesterClient({ accessToken: () => 'synthetic-admin-semester', sessionScopeFor: () => scope, fetcher: async (input, init) => {
    const url = new URL(String(input), window.location.origin), method = init?.method ?? 'GET'
    reads += 1; document.documentElement.dataset.adminFixtureRequests = String(reads)
    if (state === 'revoked' && method === 'GET') { const read = reads; await new Promise<void>(resolve => setTimeout(resolve, 100)); document.documentElement.dataset.adminFixtureReadSettled = String(read) }
    if (state === 'forbidden') return json({ detail: 'У тебя нет доступа к семестрам.' }, 403)
    if (state === 'error') return json({ detail: 'Список семестров недоступен. Попробуй ещё раз.' }, 503)
    const body = init?.body ? JSON.parse(String(init.body)) as Record<string, unknown> : {}
    if (url.pathname.endsWith('/overlap')) return json({ overlaps: state === 'overlap', conflictingName: state === 'overlap' ? 'Осенний 2026/2027' : null })
    if (url.pathname === '/api/academic/semesters') {
      if (method === 'POST') { const created = { ...semesters[1]!, ...body, id: 9, active: false }; semesters.push(created as typeof semesters[number]); return json(created, 201) }
      return json({ _embedded: { semesterResponseList: state === 'empty' ? [] : semesters }, page: { number: 0, totalPages: 1, size: 200, totalElements: semesters.length } })
    }
    if (url.pathname.startsWith('/api/academic/semester-archive-operations/')) return json(archiveOperation)
    if (url.pathname.startsWith('/api/academic/semester-deletions/')) {
      deletionPolls += 1
      if (['delete-poll-forbidden', 'delete-resume-forbidden'].includes(state)) return json({ detail: 'Доступ к операции отозван.' }, 403)
      if (['delete-background-completed', 'delete-background-resume'].includes(state) && deletionPolls >= 2 && deletionOperation) { deletionOperation = { ...deletionOperation, phase: 'COMPLETED' }; const index = semesters.findIndex(item => item.id === deletionOperation?.semesterId); if (index >= 0) semesters.splice(index, 1) }
      return json(deletionOperation)
    }
    const match = url.pathname.match(/\/semesters\/(\d+)(.*)$/), semester = semesters.find(item => item.id === Number(match?.[1]))
    if (!semester) return json({ detail: 'Семестр не найден.' }, 404)
    if (match?.[2] === '/delete-preview') return json(preview(semester.id))
    if (match?.[2] === '/archive/status') return json({ semesterId: semester.id, active: semester.active, archived: semester.archived, transition: semester.transition, stateVersion: semester.stateVersion, releasePending: semester.releasePending, operation: archiveOperation?.semesterId === semester.id ? archiveOperation : null })
    if (match?.[2] === '/activate') { semesters.forEach(item => { item.active = item.id === semester.id }); return json(semester) }
    if (match?.[2] === '/archive' || match?.[2] === '/restore') {
      if (state === 'archive-unknown') throw new TypeError('Synthetic acknowledgement lost')
      const archive = match[2] === '/archive', pending = state === 'archiving'
      Object.assign(semester, { active: false, archived: pending ? semester.archived : archive, transition: pending ? 'ARCHIVING' : 'NONE', stateVersion: semester.stateVersion + 1, writeBlocked: archive || pending })
      archiveOperation = { operationId: 'synthetic-archive-operation', semesterId: semester.id, action: archive ? 'ARCHIVE' : 'RESTORE', operationState: pending ? 'PENDING' : 'COMPLETED', retryable: false, stateVersion: semester.stateVersion, transition: semester.transition, active: false, archived: semester.archived, releasePending: false, academic: pending ? 'PENDING' : archive ? 'READY' : 'RELEASED', schedule: pending ? 'PENDING' : archive ? 'READY' : 'RELEASED', attendance: pending ? 'PENDING' : archive ? 'READY' : 'RELEASED', blockingReason: null }
      return json(archiveOperation, 202)
    }
    if (method === 'DELETE') {
      if (state === 'delete-forbidden') return json({ detail: 'Доступ к удалению отозван.' }, 403)
      if (state === 'delete-password-extras') return json({ extras: { code: 'CURRENT_PASSWORD_INVALID' }, detail: 'Пароль не подошёл. Попробуй ещё раз.' }, 403)
      if (state === 'delete-password') return json({ code: 'CURRENT_PASSWORD_INVALID', detail: 'Пароль не подошёл. Попробуй ещё раз.' }, 403)
      if (state === 'delete-unknown') throw new TypeError('Synthetic deletion acknowledgement lost')
      const cancelled = ['delete-stale', 'delete-expired'].includes(state)
      deletionOperation = { operationId: '20100000-0000-4000-8000-000000000001', semesterId: semester.id, phase: cancelled ? 'CANCELLED' : ['delete-progress', 'delete-poll-forbidden', 'delete-background', 'delete-background-completed', 'delete-background-resume'].includes(state) ? 'PREPARING' : 'COMPLETED', retryable: false, stateVersion: semester.stateVersion + 1, counts, reason: cancelled ? state === 'delete-expired' ? 'PREPARE_EXPIRED' : 'STALE_PREVIEW' : null, blockingReason: null, refreshedPreview: cancelled ? preview(semester.id, true) : null }
      if (!cancelled && !['delete-progress', 'delete-poll-forbidden', 'delete-background', 'delete-background-completed', 'delete-background-resume'].includes(state)) semesters.splice(semesters.indexOf(semester), 1)
      return json(deletionOperation, cancelled ? 409 : 202)
    }
    if (method === 'PUT') { Object.assign(semester, body); return json(semester) }
    if (method === 'GET' && match?.[2] === '') return json(semester)
    throw new Error(`Unsupported synthetic semester request: ${method} ${url.pathname}`)
  } })
}
