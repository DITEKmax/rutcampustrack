import { createSSRApp, unref, type Component, type Ref, type VNodeChild } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UsersPage from './UsersPage.vue'
import GroupsPage from './GroupsPage.vue'
import {
  AdminUsersClient,
  type AdminUser,
} from '../../../../mobile-core/src/features/admin-users/admin-users-client'
import {
  AdminGroupsClient,
  type AdminGroup,
} from '../../../../mobile-core/src/features/admin-groups/admin-groups-client'
const owner = vi.hoisted(() => ({ retired: false, mode: 'users' }))
vi.mock('../../app/live', () => ({ useWebLiveRefresh: () => {} }))
vi.mock('../../auth/session', () => ({
  webSession: {
    snapshot: { value: null },
    identity: { value: 'test-owner' },
    phase: { value: 'ready' },
  },
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {}, params: {}, fullPath: '/admin/users' }),
  useRouter: () => ({ replace: async () => undefined }),
  onBeforeRouteLeave: () => {},
  onBeforeRouteUpdate: () => {},
  RouterLink: 'a',
}))
vi.mock('./use-admin', async () => {
  const { ref } = await import('vue')
  return {
    bindAdminRecovery: () => {},
    roleLabels: { STUDENT: 'Студент' },
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
            : ['GROUPS_VIEW', 'GROUPS_MANAGE']
          ).includes(code),
        access: {},
        guardUnfinished: () => {},
        run: async (task: () => Promise<void>) => task(),
        refresh: async (task: () => Promise<void>) => task(),
        init: async () => {},
        requirePermission: async () => {},
      }
    },
  }
})
type PageState = Record<string, unknown> & {
  initialReceipt: Ref<{ login: string; password: string } | null>
  selected: Ref<unknown>
  name: Ref<string>
  duration: Ref<number>
  resume(): Promise<void>
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
  vi.restoreAllMocks()
  owner.retired = false
  owner.mode = 'users'
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
  it('archives under the authoritative renamed group object instead of the former code', async () => {
    owner.mode = 'groups'
    const old: AdminGroup = {
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
    const confirmed = { ...old, name: 'УИТ-312' }
    vi.spyOn(AdminGroupsClient.prototype, 'renameGroup').mockResolvedValue()
    vi.spyOn(AdminGroupsClient.prototype, 'resolveNotificationGroup').mockResolvedValue(confirmed)
    vi.spyOn(AdminGroupsClient.prototype, 'listGroups').mockResolvedValue({
      items: [confirmed],
      number: 0,
      size: 30,
      totalElements: 1,
      totalPages: 1,
      activeCount: 1,
      draftCount: 0,
      archivedCount: 0,
    })
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
