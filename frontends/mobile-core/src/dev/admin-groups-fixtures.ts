import type { AdminAccessProfile } from '../features/admin-access/admin-access-client'
import { AdminGroupsClient, adminGroupProgramTypes, type AdminGroup, type PromotionSummary } from '../features/admin-groups/admin-groups-client'
export const adminGroupsStates = ['missing-authority', 'read-only', 'revoked', 'active', 'expanded', 'actions-expanded', 'filter-course-options', 'filter-program-options', 'filter-sort-options', 'filtered-course', 'filtered-program', 'sorted', 'draft', 'archive', 'filters', 'create', 'created', 'create-twice', 'headman', 'headman-preview', 'headman-unchanged', 'headman-conflict', 'promotion', 'promotion-confirm', 'promotion-result', 'promotion-history', 'promotion-conflicts', 'promotion-stale', 'restored', 'archive-confirm', 'archived', 'empty', 'error', 'forbidden'] as const
const fullAdminPermissions = ['DASHBOARD_VIEW', 'USERS_VIEW', 'USERS_MANAGE', 'USER_CREDENTIALS_MANAGE', 'USERS_ARCHIVE', 'GROUPS_VIEW', 'GROUPS_MANAGE', 'SEMESTERS_VIEW', 'SEMESTERS_MANAGE', 'CAMPUS_MAP_MANAGE', 'DATA_DELETE', 'ADMIN_ACCESS_MANAGE'] as const
export function adminGroupsAuthority(state: string): AdminAccessProfile | null { return state === 'missing-authority' ? null : { userId: 1, assigned: true, mode: ['read-only', 'no-delete-permission'].includes(state) ? 'LIMITED' : 'FULL', permissions: state === 'no-delete-permission' ? ['SEMESTERS_VIEW', 'SEMESTERS_MANAGE'] : state === 'read-only' ? ['GROUPS_VIEW'] : fullAdminPermissions, version: 1 } }
export function createAdminGroupsFixture(state: string): AdminGroupsClient {
  let createdCount = 0
  let reads = 0
  document.documentElement.dataset.adminFixtureRequests = '0'
  const groups: AdminGroup[] = Array.from({ length: 34 }, (_, index) => {
    const status = index < 24 ? 'ACTIVE' : index < 31 ? 'DRAFT' : 'ARCHIVED'
    const currentCourse = index % 9 + 1, program = adminGroupProgramTypes[index % 9]!
    const numericCode = `${currentCourse}${program.digit}${index % 10}`
    const alphabeticCode = index % 3 === 0 ? 'УИТ' : index % 3 === 1 ? 'ЭКН' : 'АСУ'
    return { id: index + 1, name: `${alphabeticCode}-${numericCode}`, alphabeticCode, numericCode, currentCourse, programTypeDigit: program.digit, studyMode: program.studyMode, educationLevel: program.educationLevel, trainingDurationYears: 5, durationStatus: 'KNOWN', status, draftReason: status === 'DRAFT' ? index % 2 ? 'Староста ушёл' : 'Группа только создана' : null, studentCount: index < 24 ? 22 + index % 3 : 0, headmanFio: status === 'ACTIVE' ? 'Анна Иванова' : null, createdAt: '2026-01-01T00:00:00Z' }
  })
  const newGroupTemplate = { ...groups.find(group => group.status === 'DRAFT')! }
  let currentHeadman = 11
  function plan(groupId: number | null, executed = false): PromotionSummary { return { toPromote: [{ id: groupId ?? 1, from: 'УИТ-311', to: 'УИТ-411', action: 'PROMOTE', studentCount: 22 }], toArchive: groupId ? [] : [{ id: 4, from: 'ЭКН-461', to: null, action: 'ARCHIVE', studentCount: 25 }], skipped: [{ id: 25, name: 'АСУ-310', studentCount: 0, reason: 'CREATED_AFTER_CYCLE_END', previousAction: null, previousFrom: null, previousTo: null }], conflicts: state === 'promotion-conflicts' ? [{ prefix: 'АСУ', reason: 'NAME_CONFLICT', message: 'Имя следующего курса уже занято. Группа останется без изменений.', groupIds: [3, 7] }] : [], cycleSemesterId: 2, cycleDateTo: '2026-06-30', groupId, previewVersion: 'synthetic-plan-1', promoteCount: 1, archiveCount: groupId ? 0 : 1, promotedStudentCount: 22, archivedStudentCount: groupId ? 0 : 25, skippedGroupCount: 1, skippedStudentCount: 0, conflictCount: state === 'promotion-conflicts' ? 1 : 0, dryRun: !executed, executed } }
  const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
  return new AdminGroupsClient({ accessToken: () => 'synthetic-admin-groups', fetcher: async (input, init) => {
    const url = new URL(String(input), window.location.origin), method = init?.method ?? 'GET'
    reads += 1; document.documentElement.dataset.adminFixtureRequests = String(reads)
    if (state === 'revoked' && method === 'GET') { const read = reads; await new Promise<void>(resolve => setTimeout(resolve, 100)); document.documentElement.dataset.adminFixtureReadSettled = String(read) }
    if (state === 'forbidden') return json({ detail: 'У тебя нет доступа к группам.' }, 403)
    if (state === 'error') return json({ detail: 'Не удалось загрузить группы. Попробуй ещё раз.' }, 503)
    const body = init?.body ? JSON.parse(String(init.body)) as Record<string, unknown> : {}
    if (url.pathname.endsWith('/registry')) {
      if (method === 'POST') { const numericCode = String(body.numericCode), program = adminGroupProgramTypes.find(item => item.digit === Number(numericCode[1])); const group: AdminGroup = { ...newGroupTemplate, id: 90 + createdCount++, name: `${body.alphabeticCode}-${numericCode}`, alphabeticCode: String(body.alphabeticCode), numericCode, currentCourse: Number(numericCode[0]), programTypeDigit: program?.digit ?? null, studyMode: program?.studyMode ?? null, educationLevel: program?.educationLevel ?? null, trainingDurationYears: Number(body.trainingDurationYears) }; groups.unshift(group); return json(group, 201) }
      document.documentElement.dataset.adminFixtureRegistryQuery = url.search
      const search = (url.searchParams.get('search') ?? '').toLocaleLowerCase('ru-RU')
      const all = state === 'empty' ? [] : groups.filter(group => group.name.toLocaleLowerCase('ru-RU').includes(search)
        && (!url.searchParams.has('course') || group.currentCourse === Number(url.searchParams.get('course')))
        && (!url.searchParams.has('studyMode') || group.studyMode === url.searchParams.get('studyMode'))
        && (!url.searchParams.has('educationLevel') || group.educationLevel === url.searchParams.get('educationLevel')))
      const selected = all.filter(group => group.status === url.searchParams.get('status')), number = Number(url.searchParams.get('page')), size = Number(url.searchParams.get('size'))
      const sorts = url.searchParams.getAll('sort')
      if (sorts.length) selected.sort((left, right) => {
        for (const sort of sorts) {
          const [field, direction] = sort.split(',')
          const a = field === 'programType' ? left.programTypeDigit : field === 'currentCourse' ? left.currentCourse : left.name
          const b = field === 'programType' ? right.programTypeDigit : field === 'currentCourse' ? right.currentCourse : right.name
          if (a === null && b !== null) return 1
          if (b === null && a !== null) return -1
          const comparison = typeof a === 'number' && typeof b === 'number' ? a - b : String(a).localeCompare(String(b), 'ru')
          if (comparison) return direction === 'desc' ? -comparison : comparison
        }
        return left.id - right.id
      })
      return json({ items: selected.slice(number * size, (number + 1) * size), number, size, totalElements: selected.length, totalPages: Math.ceil(selected.length / size), activeCount: all.filter(g => g.status === 'ACTIVE').length, draftCount: all.filter(g => g.status === 'DRAFT').length, archivedCount: all.filter(g => g.status === 'ARCHIVED').length })
    }
    if (url.pathname.endsWith('/promote/preview')) return json(plan(typeof body.groupId === 'number' ? body.groupId : null))
    if (url.pathname.endsWith('/promote')) return state === 'promotion-stale' ? json({ detail: 'План изменился. Проверь обновлённый предпросмотр.' }, 409) : json(plan(typeof body.groupId === 'number' ? body.groupId : null, true))
    const match = url.pathname.match(/\/groups\/(\d+)(.*)$/), group = groups.find(item => item.id === Number(match?.[1]))
    if (!group) return json({ detail: 'Группа не найдена.' }, 404)
    if (match?.[2] === '/headman/roster') return json({ groupId: group.id, currentHeadmanId: currentHeadman, currentHeadmanFio: 'Анна Иванова', activeAssistantCount: 2, candidates: [{ id: 11, fio: 'Анна Иванова', current: true }, { id: 12, fio: 'Илья Соколов', current: false }] })
    if (match?.[2] === '/headman/preview') return json({ groupId: group.id, currentHeadmanId: currentHeadman, currentHeadmanFio: 'Анна Иванова', candidateId: Number(url.searchParams.get('studentId')), candidateFio: url.searchParams.get('studentId') === '11' ? 'Анна Иванова' : 'Илья Соколов', sameHeadman: url.searchParams.get('studentId') === String(currentHeadman), activatesDraft: group.status === 'DRAFT', assistantsToRevoke: 2 })
    if (match?.[2] === '/headman' && method === 'PUT') { if (state === 'headman-conflict') return json({ detail: 'Староста уже изменился. Обнови состав и выбери студента снова.' }, 409); currentHeadman = Number(body.studentId); Object.assign(group, { headmanFio: 'Илья Соколов', status: 'ACTIVE' }); return json({ groupId: group.id, headmanId: currentHeadman, headmanFio: 'Илья Соколов', changed: true, activatesDraft: false, assistantsRevoked: 2 }) }
    if (match?.[2] === '/restore' && method === 'POST') { Object.assign(group, { status: 'DRAFT' }); return new Response(null, { status: 204 }) }
    if (match?.[2] === '' && method === 'PUT') { Object.assign(group, { status: 'ARCHIVED' }); return json(group) }
    throw new Error(`Unsupported synthetic group request: ${method} ${url.pathname}`)
  } })
}
