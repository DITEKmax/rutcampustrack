import { createAdminUsersReviewApis } from '../../../mobile-core/src/dev/admin-users-fixtures'
import { createAdminGroupsFixture } from '../../../mobile-core/src/dev/admin-groups-fixtures'
import { createAdminSemesterFixture } from '../../../mobile-core/src/dev/admin-semester-fixtures'
import { createAdminMapFixtures } from '../../../mobile-core/src/dev/admin-map-fixtures'
import { createAdminActivityFixture } from '../../../mobile-core/src/dev/admin-dashboard-fixtures'
import type { AdminGroup, AdminGroupSort } from '../../../mobile-core/src/features/admin-groups/admin-groups-client'
import type { AdminUser } from '../../../mobile-core/src/features/admin-users/admin-users-client'

const json = (value: unknown, status = 200) => Response.json(value, { status })
const missing = () => json({ title: 'Синтетическая запись не найдена' }, 404)
const number = (url: URL, key: string, fallback = 0) => Number(url.searchParams.get(key) ?? fallback)
export const adminPreviewFormats = [
  { code: 'docx', label: 'Word', extension: 'docx', mime: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' },
  { code: 'pdf', label: 'PDF', extension: 'pdf', mime: 'application/pdf' },
  { code: 'png', label: 'PNG', extension: 'png', mime: 'image/png' },
  { code: 'html', label: 'HTML', extension: 'html', mime: 'text/html' },
  { code: 'xlsx', label: 'Excel', extension: 'xlsx', mime: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
]
// These factories have isolated in-memory transports. Only their public reads are used.
let sources: ReturnType<typeof createSources> | null = null
function createSources() {
  return { users: createAdminUsersReviewApis('detail-multirole'), groups: createAdminGroupsFixture('list'),
    semesters: createAdminSemesterFixture('list'), map: createAdminMapFixtures('full').client }
}
function source() { return sources ??= createSources() }
let registry: Promise<readonly AdminGroup[]> | null = null
function groups() {
  return registry ??= Promise.all((['ACTIVE', 'DRAFT', 'ARCHIVED'] as const).map(status => source().groups.listGroups({ status, size: 100 })))
    .then(pages => pages.flatMap(page => page.items).map(group => group.id === 17 ? { ...group, name: 'ИВТ-21', headmanFio: 'Анна Смирнова' } : group))
}
let people: Promise<readonly AdminUser[]> | null = null
function users() {
  return people ??= Promise.all([source().users.client.getUser(1), source().users.client.getUser(2), source().users.client.getUser(3), groups()])
    .then(([first, second, teacher, registry]) => {
      const name = (id: number | null) => registry.find(group => group.id === id)?.name ?? null
      const studentRole = first.roles[0]!
      const headmanRole = second.roles.find(role => role.role === 'HEADMAN')!
      const adminRole = teacher.roles.find(role => role.role === 'ADMIN')!
      const self: AdminUser = { ...teacher, id: 42, login: 'anna.smirnova', firstName: 'Анна', lastName: 'Смирнова', fullName: 'Смирнова Анна', groupId: 17, initialPassword: null,
        roles: [{ ...studentRole, groupId: 17, groupName: 'ИВТ-21' }, { ...headmanRole, groupId: 17, groupName: 'ИВТ-21', status: 'ACTIVE', selectable: true }, teacher.roles[0]!, adminRole] }
      return [first, second, teacher, self,
        { ...first, id: 11, firstName: 'Анна', lastName: 'Иванова', fullName: 'Иванова Анна', login: 'anna.ivanova', groupId: 1, roles: [{ ...studentRole, groupId: 1, groupName: name(1) }] },
        { ...first, id: 12, firstName: 'Илья', lastName: 'Соколов', fullName: 'Соколов Илья', login: 'ilya.sokolov', groupId: 1, roles: [{ ...studentRole, groupId: 1, groupName: name(1) }] },
        { ...first, id: 81, firstName: 'Ева', lastName: 'Данилова', fullName: 'Данилова Ева', login: 'eva.danilova', groupId: 32, status: 'ARCHIVED' as const, roles: [{ ...studentRole, groupId: 32, status: 'GRADUATED' as const, selectable: false, readOnly: true }] },
      ].map(user => ({ ...user, roles: user.roles.map(role => ({ ...role, groupName: role.groupId === 17 ? 'ИВТ-21' : name(role.groupId) })) }))
    })
}
function page<T>(url: URL, rows: readonly T[]) {
  const number = Number(url.searchParams.get('page') ?? 0), size = Number(url.searchParams.get('size') ?? 20)
  if (!Number.isSafeInteger(number) || number < 0 || !Number.isSafeInteger(size) || size < 1 || size > 100)
    throw new Error('Некорректная страница preview')
  return { items: rows.slice(number * size, (number + 1) * size), number, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) }
}
const includes = (text: string, query: string | null) => !query || text.toLocaleLowerCase('ru').includes(query.toLocaleLowerCase('ru'))

export async function adminPreview(url: URL, method: string): Promise<Response | null> {
  if (method !== 'GET') return null
  const path = url.pathname, api = source()
  if (path === '/api/academic/admin-access/me') return json({ ...api.users.authority, userId: 42 })
  if (path === '/api/academic/admin-access/catalog') return json(await api.users.accessClient.getCatalog())
  const access = path.match(/^\/api\/academic\/users\/(\d+)\/admin-access$/)
  if (access) return json(Number(access[1]) === 42 ? { ...api.users.authority, userId: 42 } : { userId: Number(access[1]), assigned: Number(access[1]) === 3, mode: Number(access[1]) === 3 ? 'LIMITED' : null, permissions: Number(access[1]) === 3 ? ['USERS_VIEW'] : [], version: Number(access[1]) === 3 ? 1 : 0 })
  if (path === '/api/academic/dashboard/stats') {
    const registry = await groups(), active = registry.filter(group => group.status === 'ACTIVE')
    return json({ totalStudents: active.reduce((sum, group) => sum + group.studentCount, 0), totalTeachers: 18, totalGroups: active.length, activeGroups: active.length, activeSemesterName: 'Осенний 2026/2027',
      dailyGrowth: { status: 'AVAILABLE', baselineDate: '2026-11-01', studentsDelta: 6, teachersDelta: 1, groupsDelta: 0 } })
  }
  if (path === '/api/academic/dashboard/activity') return json(createAdminActivityFixture(url.searchParams.get('period') === 'all_time' ? 'all_time' : 'today', url.searchParams.get('bucket') === 'week' ? 'week' : 'day', 'partial'))
  if (path === '/api/academic/groups/registry') {
    const all = (await groups()).filter(group => includes(group.name, url.searchParams.get('search')) && (!url.searchParams.has('course') || group.currentCourse === number(url, 'course')) && (!url.searchParams.has('studyMode') || group.studyMode === url.searchParams.get('studyMode')) && (!url.searchParams.has('educationLevel') || group.educationLevel === url.searchParams.get('educationLevel')))
    const selected = all.filter(group => group.status === (url.searchParams.get('status') ?? 'ACTIVE'))
    const sorts = url.searchParams.getAll('sort') as AdminGroupSort[]
    selected.sort((left, right) => {
      for (const sort of sorts) {
        const [field, direction] = sort.split(',')
        const a = field === 'programType' ? left.programTypeDigit : field === 'currentCourse' ? left.currentCourse : left.name
        const b = field === 'programType' ? right.programTypeDigit : field === 'currentCourse' ? right.currentCourse : right.name
        const compare = typeof a === 'number' && typeof b === 'number' ? a - b : String(a).localeCompare(String(b), 'ru')
        if (compare) return direction === 'desc' ? -compare : compare
      }
      return left.id - right.id
    })
    return json({ ...page(url, selected), activeCount: all.filter(group => group.status === 'ACTIVE').length, draftCount: all.filter(group => group.status === 'DRAFT').length, archivedCount: all.filter(group => group.status === 'ARCHIVED').length })
  }
  if (path === '/api/academic/groups/options') return json(page(url, (await groups()).filter(group => (url.searchParams.get('includeArchived') === 'true' || group.status !== 'ARCHIVED') && includes(group.name, url.searchParams.get('search'))).map(group => ({ id: group.id, name: group.name, archived: group.status === 'ARCHIVED' }))))
  const group = path.match(/^\/api\/academic\/groups\/(\d+)$/)
  if (group) {
    const row = (await groups()).find(item => item.id === Number(group[1]))
    return row ? json({ ...row, active: row.status === 'ACTIVE' }) : missing()
  }
  const roster = path.match(/^\/api\/academic\/groups\/(\d+)\/headman\/roster$/)
  if (roster) return json(Number(roster[1]) === 17 ? { groupId: 17, currentHeadmanId: 42, currentHeadmanFio: 'Анна Смирнова', candidates: [{ id: 42, fio: 'Анна Смирнова', current: true }], activeAssistantCount: 2 } : await api.groups.getHeadmanRoster(Number(roster[1])))
  const headman = path.match(/^\/api\/academic\/groups\/(\d+)\/headman\/preview$/)
  if (headman) return json(Number(headman[1]) === 17 && number(url, 'studentId') === 42 ? { groupId: 17, currentHeadmanId: 42, currentHeadmanFio: 'Анна Смирнова', candidateId: 42, candidateFio: 'Анна Смирнова', sameHeadman: true, activatesDraft: false, assistantsToRevoke: 0 } : await api.groups.previewHeadman(Number(headman[1]), number(url, 'studentId')))
  const groupDeletion = path.match(/^\/api\/academic\/groups\/(\d+)\/deletion-preview$/)
  if (groupDeletion) {
    const group = (await groups()).find(item => item.id === Number(groupDeletion[1]))
    return group ? json({ groupId: String(group.id), groupName: group.name, previewDigest: 'a'.repeat(64), counts: { accountsAffected: group.studentCount, memberships: group.studentCount, headmanAssignments: group.headmanFio ? 1 : 0, assistantAssignments: 2, academicRows: 15, scheduleRows: 90, attendanceRows: 420, authGrants: group.studentCount } }) : missing()
  }
  if (path === '/api/academic/users') {
    const all = (await users()).filter(user => includes(`${user.fullName} ${user.login}`, url.searchParams.get('search')) && (!url.searchParams.get('status') || user.status === url.searchParams.get('status')) && (!url.searchParams.get('role') || user.roles.some(role => role.role === url.searchParams.get('role') && (!url.searchParams.get('roleStatus') || role.status === url.searchParams.get('roleStatus')))) && (!url.searchParams.has('groupId') || user.roles.some(role => role.groupId === number(url, 'groupId'))))
      .sort((a, b) => a.lastName.localeCompare(b.lastName, 'ru') || a.id - b.id)
    if (url.searchParams.getAll('sort').some(sort => sort.endsWith(',desc'))) all.reverse()
    const result = page(url, all)
    return json({ _embedded: { users: result.items.map(user => { const publicUser = { ...user }; delete publicUser.initialPassword; return publicUser }) }, page: { ...result, items: undefined } })
  }
  const user = path.match(/^\/api\/academic\/users\/(\d+)$/)
  if (user) return (await users()).some(item => item.id === Number(user[1])) ? json((await users()).find(item => item.id === Number(user[1]))) : missing()
  const archive = path.match(/^\/api\/academic\/users\/(\d+)\/archive-preview$/)
  if (archive) return json({ userId: Number(archive[1]), linkedGroupCount: 1, attendanceMarksCount: 128, activeHeadmanGroupCount: Number(archive[1]) === 42 ? 1 : 0, soleTeacherAssignmentCount: 2, requiresPassword: true, previewDigest: 'a'.repeat(64), expiresAt: new Date(Date.now() + 600000).toISOString() })
  const credentials = path.match(/^\/api\/auth\/admin\/users\/(\d+)\/initial-password$/)
  if (credentials) return json({ userId: credentials[1], credentialsVersion: '1', state: 'USER_CHANGED', origin: null, password: null })
  if (path === '/api/academic/semesters') return json((await api.semesters.listSemesters()).map(item => ({ ...item, writeBlocked: item.isWriteBlocked })))
  if (path === '/api/academic/semesters/overlap') return json(await api.semesters.checkOverlap(url.searchParams.get('from')!, url.searchParams.get('to')!, url.searchParams.has('excludeId') ? number(url, 'excludeId') : undefined))
  const semester = path.match(/^\/api\/academic\/semesters\/(\d+)$/)
  if (semester) { const item = await api.semesters.getSemester(Number(semester[1])); return json({ ...item, writeBlocked: item.isWriteBlocked }) }
  const archiveStatus = path.match(/^\/api\/academic\/semesters\/(\d+)\/archive\/status$/)
  if (archiveStatus) return json(await api.semesters.getArchiveStatus(Number(archiveStatus[1])))
  const semesterDeletion = path.match(/^\/api\/academic\/semesters\/(\d+)\/delete-preview$/)
  if (semesterDeletion) return json({ ...await api.semesters.getSemesterDeletionPreview(Number(semesterDeletion[1])), previewDigest: 'a'.repeat(64) })
  if (path === '/api/academic/map/buildings') return json(await api.map.listBuildings())
  const floors = path.match(/^\/api\/academic\/map\/buildings\/([^/]+)\/floors$/)
  if (floors) return json((await api.map.listBuildings()).find(item => item.id === floors[1])?.floors ?? [])
  const asset = path.match(/^\/api\/academic\/map\/floors\/([^/]+)\/versions\/([^/]+)\/assets\/(png|svg)\/([^/]+)$/)
  if (asset) return new Response(await api.map.downloadAsset(asset[1]!, asset[2]!, asset[3] as 'png' | 'svg', asset[4]!))
  const version = path.match(/^\/api\/academic\/map\/floors\/([^/]+)\/versions\/([^/]+)$/)
  if (version) {
    const plan = (await api.map.listBuildings()).flatMap(item => item.floors).find(item => item.id === version[1])?.currentPlan
    return plan && plan.version === version[2] ? json(plan) : missing()
  }
  if (path === '/api/academic/map/plans/target') return json(await api.map.resolvePlanTarget(url.searchParams.get('buildingCode')!, url.searchParams.get('floorCode')!))
  if (path === '/api/academic/map/plans/target/deletion-preview') {
    const format = url.searchParams.get('format')
    if (format !== null && format !== 'png' && format !== 'svg') return missing()
    return json({ ...await api.map.planDeletionPreview(url.searchParams.get('buildingCode')!, url.searchParams.get('floorCode')!, format ?? undefined), deletionScope: format ? 'CURRENT_FORMAT' : 'ALL_VERSIONS' })
  }
  if (path === '/api/academic/admin/attendance/semesters') return json({ items: (await api.semesters.listSemesters()).map(item => ({ ...item, id: String(item.id) })) })
  if (path === '/api/academic/admin/attendance') {
    const selected = (await api.semesters.listSemesters()).find(item => String(item.id) === url.searchParams.get('semesterId'))
    if (!selected) return missing()
    const rows = (await groups()).filter(group => group.status === 'ACTIVE' && includes(group.name, url.searchParams.get('search'))).map((group, index) => {
      const opportunities = index % 7 === 0 ? 0 : 100, presentCount = opportunities ? 60 + index % 20 : 0, excusedCount = opportunities ? 10 : 0
      return { groupId: String(group.id), groupName: group.name, countedLessons: opportunities ? 8 : 0, presentCount, excusedCount, absentCount: opportunities - presentCount - excusedCount, opportunities, presentPercent: opportunities ? presentCount : null, presentOrExcusedPercent: opportunities ? presentCount + excusedCount : null, dataState: opportunities ? 'AVAILABLE' : 'NO_DATA' }
    })
    const keys: Record<string, keyof typeof rows[number]> = { GROUP_NAME: 'groupName', PRESENT_PERCENT: 'presentPercent', PRESENT_OR_EXCUSED_PERCENT: 'presentOrExcusedPercent', COUNTED_LESSONS: 'countedLessons', PRESENT: 'presentCount', ABSENT: 'absentCount', EXCUSED: 'excusedCount' }
    const key = keys[url.searchParams.get('sort') ?? 'GROUP_NAME'] ?? 'groupName'
    rows.sort((a, b) => { const first = a[key], second = b[key]; return (typeof first === 'number' && typeof second === 'number' ? first - second : String(first).localeCompare(String(second), 'ru')) * (url.searchParams.get('direction') === 'DESC' ? -1 : 1) })
    const result = page(url, rows)
    return json({ semester: { ...selected, id: String(selected.id) }, search: url.searchParams.get('search') ?? '', sort: url.searchParams.get('sort') ?? 'GROUP_NAME', direction: url.searchParams.get('direction') ?? 'ASC', page: result.number, size: result.size, rows: result.items, totalElements: result.totalElements, totalPages: result.totalPages, observedAt: new Date().toISOString() })
  }
  if (['/api/academic/registry-exports/users/formats', '/api/academic/registry-exports/groups/formats', '/api/academic/admin/attendance/formats'].includes(path)) return json(adminPreviewFormats)
  return null
}

export async function campusPreview(url: URL, method: string): Promise<Response | null> {
  if (method !== 'GET' || !url.pathname.startsWith('/api/v1/map/')) return null
  const map = source().map
  const buildings = await map.listBuildings()
  if (url.pathname === '/api/v1/map/manifest') return json({ schemaVersion: 1, validationPolicyVersion: 1, revision: 'visual-review-map-v1', buildings: buildings.map(building => ({ id: building.id, label: `Корпус ${building.code}`, floors: building.floors.map(floor => ({ id: floor.id, label: `${floor.code} этаж`, plan: floor.currentPlan })) })) })
  const selected = url.pathname.match(/^\/api\/v1\/map\/buildings\/([^/]+)\/floors\/([^/]+)\/plan$/)
  if (selected) return json({ plan: buildings.find(building => building.id === selected[1])?.floors.find(floor => floor.id === selected[2])?.currentPlan ?? null })
  const asset = url.pathname.match(/^\/api\/v1\/map\/buildings\/([^/]+)\/floors\/([^/]+)\/plans\/([^/]+)\/assets\/(png|svg)\/([^/]+)$/)
  if (asset) {
    if (!buildings.find(building => building.id === asset[1])?.floors.some(floor => floor.id === asset[2])) return missing()
    return new Response(await map.downloadAsset(asset[2]!, asset[3]!, asset[4] as 'png' | 'svg', asset[5]!))
  }
  return null
}
