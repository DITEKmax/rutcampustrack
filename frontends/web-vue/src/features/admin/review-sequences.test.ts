import { createSSRApp, unref, type Component, type Ref, type VNodeChild } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProfileSnapshot } from '../../auth/session'
import UsersPage from './UsersPage.vue'
import GroupsPage from './GroupsPage.vue'
import GroupHistoryPage from './GroupHistoryPage.vue'
import {
  AdminUsersClient,
  type AdminUser,
} from '../../../../mobile-core/src/features/admin-users/admin-users-client'
import {
  AdminGroupsClient,
  type AdminGroup,
} from '../../../../mobile-core/src/features/admin-groups/admin-groups-client'
const owner = vi.hoisted(() => ({
  retired: false,
  mode: 'users',
  returnGroupId: undefined as string | undefined,
  failedAdmission: false,
}))
vi.mock('../../app/live', () => ({ useWebLiveRefresh: () => {} }))
vi.mock('../../auth/session', async () => {
  const { ref, shallowRef } = await import('vue')
  return {
    webSession: {
      snapshot: shallowRef<ProfileSnapshot>({
        sessionId: '00000000-0000-4000-8000-000000000002',
        userId: '2',
        displayName: 'Синтетический администратор',
        sessionVersion: '1',
        rolesVersion: '1',
        activeRole: 'ADMIN',
        readOnly: false,
        roles: [
          { grantId: '1', role: 'ADMIN', status: 'ACTIVE', selectable: true, readOnly: false },
        ],
        passwordPolicy: {
          minCodePoints: 12,
          maxUtf8Bytes: 72,
          requiresDecimalDigit: true,
          specialCategories: ['P', 'S'],
          normalization: 'NONE',
        },
      }),
      identity: ref('test-owner'),
      phase: ref('ready'),
      busy: ref(false),
    },
  }
})
vi.mock('vue-router', () => ({
  useRoute: () => ({
    query: owner.returnGroupId ? { groupId: owner.returnGroupId, status: 'ARCHIVED' } : {},
    params: owner.mode === 'history' ? { id: '22' } : {},
    fullPath: '/admin/users',
  }),
  useRouter: () => ({ replace: async () => undefined }),
  onBeforeRouteLeave: () => {},
  onBeforeRouteUpdate: () => {},
  RouterLink: 'a',
}))
vi.mock('./use-admin', async () => {
  const { ref } = await import('vue')
  return {
    bindAdminRecovery: () => {},
    roleLabels: { STUDENT: 'Студент', TEACHER: 'Преподаватель' },
    statusLabels: { ACTIVE: 'Активна' },
    useAdmin: () => {
      const assertCurrent = () => {
        if (owner.retired) throw new Error('retired owner')
      }
      return {
        options: { accessToken: () => null, assertCurrent, sessionScopeFor: () => 'test-owner' },
        signal: new AbortController().signal,
        assertCurrent,
        loading: ref(false),
        busy: ref(false),
        error: ref(''),
        notice: ref(''),
        full: ref(false),
        can: (code: string) =>
          (owner.mode === 'users'
            ? ['USERS_VIEW', 'USERS_MANAGE']
            : owner.mode === 'history'
              ? ['GROUPS_VIEW', 'USERS_VIEW']
              : ['GROUPS_VIEW', 'GROUPS_MANAGE']
          ).includes(code),
        access: {},
        guardUnfinished: () => {},
        run: async (task: () => Promise<void>) => task(),
        refresh: async (task: () => Promise<void>) => task(),
        init: async () => {},
        requirePermission: async () => {
          if (owner.failedAdmission) throw new Error('Не удалось подтвердить владельца прав.')
        },
      }
    },
  }
})
type PageState = Record<string, unknown> & {
  initialReceipt: Ref<{ login: string; password: string } | null>
  selected: Ref<unknown>
  transferGroupId: Ref<number | undefined>
  transferReason: Ref<string>
  telegramId: Ref<string>
  firstName: Ref<string>
  roleToUpdate: Ref<'STUDENT' | 'TEACHER' | 'ADMIN'>
  roleEmployeeNumber: Ref<string>
  reportMatches: Ref<boolean>
  name: Ref<string>
  duration: Ref<number>
  resume(): Promise<void>
  open(user: AdminUser): Promise<void>
  chooseRole(): void
  load(): Promise<void>
  loadRegistry(): Promise<void>
  retry(): Promise<void>
  apply(): Promise<void>
  refresh(): Promise<void>
  transfer(): Promise<void>
  updateRole(): Promise<void>
  save(): Promise<void>
  archiveOrRestore(): Promise<void>
}
type TestPage = {
  setup(props: object, context: { expose(): void }): PageState
  render: () => VNodeChild
}
// Script-setup bindings are private in the public SFC type; this harness runs the actual compiled setup and render together.
function compiledPage(component: Component): TestPage {
  return component as unknown as TestPage
}
const user: AdminUser = {
  id: 22,
  login: 'test-created',
  initialPassword: 'ephemeral-regression-password',
  lastName: 'Тест',
  firstName: 'Синтетика',
  middleName: null,
  fullName: 'Тест Синтетика',
  role: 'STUDENT',
  status: 'ACTIVE',
  groupId: 1,
  employeeNumber: null,
  telegramId: null,
  createdAt: null,
  roles: [
    {
      role: 'STUDENT',
      status: 'ACTIVE',
      groupId: 1,
      groupName: 'ТСТ-119',
      selectable: true,
      readOnly: false,
      applicableStatuses: ['ACTIVE'],
      canUpdate: true,
      blockedReason: null,
    },
  ],
}
const registryGroup: AdminGroup = {
  id: 22,
  name: 'УИТ-311',
  trainingDurationYears: 4,
  status: 'ACTIVE',
  studentCount: 0,
  headmanFio: null,
  alphabeticCode: 'УИТ',
  numericCode: '311',
  currentCourse: 3,
  programTypeDigit: 1,
  studyMode: 'FULL_TIME',
  educationLevel: 'BACHELOR',
  durationStatus: 'KNOWN',
  draftReason: null,
  createdAt: null,
}
const groupsPage = {
  items: [registryGroup],
  number: 0,
  size: 30,
  totalElements: 1,
  totalPages: 1,
  activeCount: 1,
  draftCount: 0,
  archivedCount: 0,
}
async function runPage(page: TestPage, run: (state: PageState) => Promise<void>) {
  let state!: PageState
  const app = createSSRApp({
    async setup() {
      state = page.setup({}, { expose() {} })
      await run(state)
      return state
    },
    render: page.render,
  })
  return { html: await renderToString(app), state }
}
beforeEach(() => {
  owner.retired = false
  owner.mode = 'users'
  owner.returnGroupId = undefined
  owner.failedAdmission = false
  const storage = new Map<string, string>()
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
  })
})
afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})
describe('reviewed ADMIN causal sequences', () => {
  it('renders a resumed create receipt for USERS_MANAGE without credential-disclosure authority, before acknowledgement', async () => {
    vi.spyOn(AdminUsersClient.prototype, 'resumeIdentityOperation').mockResolvedValue({
      created: true,
      user,
    })
    vi.spyOn(AdminUsersClient.prototype, 'listUsers').mockResolvedValue({
      items: [],
      number: 0,
      size: 30,
      totalElements: 0,
      totalPages: 0,
    })
    let atAck: unknown
    const ack = vi
      .spyOn(AdminUsersClient.prototype, 'acknowledgeIdentityOperation')
      .mockImplementation(() => {
        atAck = unref(current.initialReceipt)
      })
    let current!: PageState
    const { html } = await runPage(compiledPage(UsersPage), async (state) => {
      current = state
      await state.resume()
    })
    expect(atAck).toEqual({ login: user.login, password: user.initialPassword })
    expect(ack).toHaveBeenCalledTimes(1)
    expect(html).toContain('Начальный доступ')
    expect(html).toContain(user.initialPassword)
    expect(html).not.toContain('Пароль и восстановление')
  })
  it('does not publish or acknowledge a recovered credential after its source owner retires', async () => {
    vi.spyOn(AdminUsersClient.prototype, 'resumeIdentityOperation').mockImplementation(async () => {
      owner.retired = true
      return { created: true, user }
    })
    const ack = vi.spyOn(AdminUsersClient.prototype, 'acknowledgeIdentityOperation')
    let current!: PageState
    await runPage(compiledPage(UsersPage), async (state) => {
      current = state
      await expect(state.resume()).rejects.toThrow('retired owner')
      owner.retired = false
    })
    expect(unref(current.initialReceipt)).toBeNull()
    expect(ack).not.toHaveBeenCalled()
  })
  it('does not carry a created account receipt or transfer draft into another user card', async () => {
    const { initialPassword, ...profile } = user
    const other: AdminUser = { ...profile, id: 23, login: 'another-student', fullName: 'Другой студент' }
    vi.spyOn(AdminUsersClient.prototype, 'resumeIdentityOperation').mockResolvedValue({ created: true, user })
    vi.spyOn(AdminUsersClient.prototype, 'listUsers').mockResolvedValue({
      items: [other], number: 0, size: 30, totalElements: 1, totalPages: 1,
    })
    vi.spyOn(AdminUsersClient.prototype, 'getUser').mockResolvedValue(other)
    const transfer = vi.spyOn(AdminUsersClient.prototype, 'transferStudent')
    const { html } = await runPage(compiledPage(UsersPage), async (state) => {
      await state.resume()
      expect(unref(state.initialReceipt)?.password).toBe(initialPassword)
      state.transferGroupId.value = 77
      state.transferReason.value = 'Причина для первой записи'
      await state.open(other)
      await state.transfer()
    })
    expect(html).toContain(other.fullName)
    expect(html).not.toContain(initialPassword)
    expect(html).not.toContain('Причина для первой записи')
    expect(transfer).not.toHaveBeenCalled()
  })
  it('does not submit an unsaved profile binding when updating an existing role', async () => {
    const existing = { ...user, telegramId: 12345 }
    vi.spyOn(AdminUsersClient.prototype, 'getUser').mockResolvedValue(existing)
    vi.spyOn(AdminUsersClient.prototype, 'listUsers').mockResolvedValue({
      items: [existing], number: 0, size: 30, totalElements: 1, totalPages: 1,
    })
    const role = vi.spyOn(AdminUsersClient.prototype, 'updateRole').mockResolvedValue(existing)
    await runPage(compiledPage(UsersPage), async (state) => {
      await state.open(existing)
      state.telegramId.value = '98765'
      await state.updateRole()
    })
    expect(role.mock.calls[0]?.slice(0, 3)).toEqual([existing.id, 'STUDENT', { status: 'ACTIVE', groupId: 1 }])
  })
  it('uses the number assigned with the teacher role when saving an independent profile edit', async () => {
    const teacher: AdminUser = {
      ...user,
      employeeNumber: 'TN-42',
      roles: [...user.roles, { ...user.roles[0]!, role: 'TEACHER', groupId: null, groupName: null }],
    }
    vi.spyOn(AdminUsersClient.prototype, 'getUser').mockResolvedValue(user)
    vi.spyOn(AdminUsersClient.prototype, 'listUsers').mockResolvedValue({
      items: [teacher], number: 0, size: 30, totalElements: 1, totalPages: 1,
    })
    const role = vi.spyOn(AdminUsersClient.prototype, 'updateRole').mockResolvedValue(teacher)
    const profile = vi.spyOn(AdminUsersClient.prototype, 'updateProfile').mockResolvedValue({
      ...teacher, firstName: 'Елена',
    })
    await runPage(compiledPage(UsersPage), async (state) => {
      await state.open(user)
      state.firstName.value = 'Елена'
      state.roleToUpdate.value = 'TEACHER'
      state.chooseRole()
      state.roleEmployeeNumber.value = 'TN-42'
      await state.updateRole()
      await state.save()
    })
    expect(role.mock.calls[0]?.slice(0, 3)).toEqual([
      user.id, 'TEACHER', { status: 'ACTIVE', employeeNumber: 'TN-42' },
    ])
    expect(profile.mock.calls[0]?.slice(0, 2)).toEqual([
      user.id, { firstName: 'Елена', lastName: user.lastName, middleName: '', employeeNumber: 'TN-42' },
    ])
  })
  it.each(['list', 'target'])('retries the exact history return after a failed %s read', async (failure) => {
    owner.mode = 'groups'
    owner.returnGroupId = String(registryGroup.id)
    const list = vi.spyOn(AdminGroupsClient.prototype, 'listGroups').mockResolvedValue(groupsPage)
    const target = vi.spyOn(AdminGroupsClient.prototype, 'resolveNotificationGroup').mockResolvedValue(registryGroup)
    if (failure === 'list') list.mockRejectedValueOnce(new Error('list unavailable'))
    else target.mockRejectedValueOnce(new Error('target unavailable'))
    await runPage(compiledPage(GroupsPage), async (state) => {
      await expect(state.loadRegistry()).rejects.toThrow('unavailable')
      expect(unref(state.selected)).toBeNull()
      await state.retry()
      expect(unref(state.selected)).toEqual(registryGroup)
    })
    expect(target.mock.calls.map(([id]) => id)).toEqual(
      failure === 'list' ? [registryGroup.id] : [registryGroup.id, registryGroup.id],
    )
    expect(list.mock.lastCall?.[0]?.status).toBe('ACTIVE')
  })
  it('does not reopen the history target after the administrator applies a different group view', async () => {
    owner.mode = 'groups'
    owner.returnGroupId = String(registryGroup.id)
    vi.spyOn(AdminGroupsClient.prototype, 'listGroups').mockResolvedValue(groupsPage)
    const target = vi.spyOn(AdminGroupsClient.prototype, 'resolveNotificationGroup')
      .mockRejectedValueOnce(new Error('target unavailable')).mockResolvedValue(registryGroup)
    await runPage(compiledPage(GroupsPage), async (state) => {
      await expect(state.loadRegistry()).rejects.toThrow('unavailable')
      await state.apply()
      await state.retry()
      expect(unref(state.selected)).toBeNull()
    })
    expect(target).toHaveBeenCalledTimes(1)
  })
  it('invalidates the history export when a permission refresh cannot confirm its owner', async () => {
    owner.mode = 'history'
    vi.spyOn(AdminGroupsClient.prototype, 'resolveNotificationGroup').mockResolvedValue(registryGroup)
    const rows = vi.spyOn(AdminUsersClient.prototype, 'listUsers').mockResolvedValue({
      items: [user], number: 0, size: 30, totalElements: 1, totalPages: 1,
    })
    await runPage(compiledPage(GroupHistoryPage), async (state) => {
      await state.load()
      expect(unref(state.reportMatches)).toBe(true)
      owner.failedAdmission = true
      await expect(state.refresh()).rejects.toThrow('владельца')
      expect(unref(state.reportMatches)).toBe(false)
    })
    expect(rows).toHaveBeenCalledTimes(1)
  })
  it('archives under the authoritative renamed group object instead of the former code', async () => {
    owner.mode = 'groups'
    const old = registryGroup
    const confirmed = { ...old, name: 'УИТ-312' }
    vi.spyOn(AdminGroupsClient.prototype, 'renameGroup').mockResolvedValue()
    vi.spyOn(AdminGroupsClient.prototype, 'resolveNotificationGroup').mockResolvedValue(confirmed)
    vi.spyOn(AdminGroupsClient.prototype, 'listGroups').mockResolvedValue({ ...groupsPage, items: [confirmed] })
    const archive = vi.spyOn(AdminGroupsClient.prototype, 'archiveGroup').mockResolvedValue()
    await runPage(compiledPage(GroupsPage), async (state) => {
      state.selected.value = old
      state.name.value = confirmed.name
      state.duration.value = 4
      await state.save()
      expect(unref(state.selected)).toEqual(confirmed)
      await state.archiveOrRestore()
    })
    expect(archive.mock.calls[0]?.slice(0, 2)).toEqual([22, 'УИТ-312'])
  })
})
