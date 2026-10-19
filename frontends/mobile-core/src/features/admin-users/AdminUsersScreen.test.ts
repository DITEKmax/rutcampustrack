import { createRenderer, h, markRaw, nextTick, reactive, shallowRef, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminUsersScreen from './AdminUsersScreen.vue'
import AdminGroupsScreen from '../admin-groups/AdminGroupsScreen.vue'
import { adminGroupsAuthority, createAdminGroupsFixture } from '../../dev/admin-groups-fixtures'
import RecoveryLinkPanel from './RecoveryLinkPanel.vue'
import { IdentityOperationError } from '../../shared/identity-operation'
import {
  AdminUsersApiError,
  AdminUsersClient,
  type IdentityRecoveryReference,
} from './admin-users-client'
import {
  AdminAccessApiError,
  AdminAccessClient,
  type AdminAccessOperationResult,
  type AdminAccessProfile,
} from '../admin-access/admin-access-client'
import { createAdminUsersReviewApis } from '../../dev/admin-users-fixtures'
import type { ReportDownloadPort } from '../../shared/report-download-client'

// Runs the actual SFC handlers and v-model in memory; no browser/live credentials.
class TestElement extends EventTarget {
  children: TestElement[] = []
  parentElement: TestElement | null = null
  props: Record<string, unknown> = {}
  value = ''
  checked = false
  selected = false
  selectedIndex = -1
  text = ''
  composing = false
  constructor(readonly tag: string) {
    super()
    markRaw(this)
  }
  get tagName(): string {
    return this.tag.toUpperCase()
  }
  get options(): TestElement[] {
    return this.children.filter((node) => node.tag === 'option')
  }
  get type(): string {
    return String(this.props.type ?? '')
  }
  getRootNode(): typeof testDocument {
    return testDocument
  }
  get open(): boolean {
    return Boolean(this.props.open)
  }
  showModal(): void {
    this.props.open = true
  }
  close(): void {
    this.props.open = false
  }
  focus(): void {}
  get isConnected(): boolean {
    return this.parentElement !== null
  }
}
class TestDocument {
  documentElement = { dataset: {} as Record<string, string> }
  activeElement: TestElement | null = null
}
const testDocument = new TestDocument()
const renderer = createRenderer<TestElement, TestElement>({
  createElement: (tag) => new TestElement(tag),
  createText: (text) => Object.assign(new TestElement('#text'), { text }),
  createComment: () => new TestElement('#comment'),
  setText: (node, text) => {
    node.text = text
  },
  setElementText: (node, text) => {
    node.text = text
    node.children = []
  },
  parentNode: (node) => node.parentElement,
  nextSibling: (node) =>
    node.parentElement?.children[node.parentElement.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _old, value) => {
    node.props[key] = value
    if (key === 'value') node.value = String(value ?? '')
    if (key === 'checked') node.checked = Boolean(value)
  },
  remove: (node) => {
    if (node.parentElement)
      node.parentElement.children = node.parentElement.children.filter((item) => item !== node)
    node.parentElement = null
  },
  insert: (node, parent, anchor) => {
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
    node.parentElement = parent
  },
})
const apps: App[] = []
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.useRealTimers()
})
function nodes(root: TestElement): TestElement[] {
  return [root, ...root.children.flatMap(nodes)]
}
function text(root: TestElement): string {
  return root.text + root.children.map(text).join('')
}
function button(root: TestElement, label: string): TestElement {
  const found = nodes(root).find((node) => node.tag === 'button' && text(node).trim() === label)
  if (!found) throw new Error('Missing button: ' + label)
  return found
}
async function flush(): Promise<void> {
  for (let i = 0; i < 15; i++) {
    await Promise.resolve()
    await nextTick()
  }
}
async function click(node: TestElement): Promise<void> {
  ;(node.props.onClick as () => unknown)()
  await flush()
}
async function input(node: TestElement, value: string): Promise<void> {
  node.value = value
  node.dispatchEvent(new Event('input'))
  await flush()
}
async function check(node: TestElement, checked = true): Promise<void> {
  node.checked = checked
  const event = new Event('change')
  node.dispatchEvent(event)
  ;(node.props.onChange as ((event: Event) => void) | undefined)?.(event)
  await flush()
}
async function submit(root: TestElement): Promise<void> {
  const form = nodes(root).find((node) => node.tag === 'form')!
  ;(form.props.onSubmit as (event: Event) => unknown)(new Event('submit', { cancelable: true }))
  await flush()
}
function mount(render: () => ReturnType<typeof h>): TestElement {
  vi.stubGlobal('document', testDocument)
  vi.stubGlobal('Document', TestDocument)
  vi.stubGlobal('HTMLElement', TestElement)
  const root = new TestElement('root')
  const app = renderer.createApp({ setup: () => render })
  apps.push(app)
  app.mount(root)
  return root
}
function failure(status: number, code = ''): AdminUsersApiError {
  return new AdminUsersApiError(new Response(null, { status }), {
    code,
    detail: 'Synthetic rejected action',
  } as never)
}
describe('role editor and current profile load', () => {
  it.each(['recovery', 'limited-manage'])(
    'binds an existing teacher through the current profile editor with %s authority',
    async (state) => {
      const { client, accessClient, authority } = createAdminUsersReviewApis(state)
      const original = { ...(await client.getUser(3)), telegramId: null, initialPassword: null }
      let current: Awaited<ReturnType<typeof client.getUser>> = original
      vi.spyOn(client, 'getUser').mockImplementation(async () => current)
      const update = vi.spyOn(client, 'updateProfile').mockImplementation(async (_id, input) => {
        current = { ...current, ...input }
        return current
      })
      const root = mount(() => h(AdminUsersScreen, { client, accessClient, authority }))
      await flush()
      await click(nodes(root).find((node) => node.props['data-user-id'] === 3)!)
      await click(button(root, 'Редактировать данные'))
      const field = () =>
        nodes(root).find((node) => node.tag === 'input' && node.props.inputmode === 'numeric')!
      expect(field().value).toBe('')
      await input(field(), '0')
      await submit(root)
      expect(update).not.toHaveBeenCalled()
      await input(field(), '987654321')
      await submit(root)
      expect(update.mock.calls[0]!.slice(0, 2)).toEqual([3, { telegramId: 987654321 }])
      expect(current.roles).toEqual(original.roles)
      expect(current.employeeNumber).toBe(original.employeeNumber)
      expect(current.initialPassword).toBeNull()
      expect(text(root)).toContain('987654321')
      await click(button(root, 'Редактировать данные'))
      expect(field().value).toBe('987654321')
      await input(field(), '')
      await submit(root)
      expect(update).toHaveBeenCalledTimes(1)
      expect(text(root)).toContain('Отвязка через эту форму недоступна')
    },
  )

  it('ignores a late identity save and clears transient fields when the authenticated client changes first', async () => {
    const previous = createAdminUsersReviewApis('recovery')
    const replacement = createAdminUsersReviewApis('limited-manage')
    const original = await previous.client.getUser(3)
    let finishSave!: (user: typeof original) => void
    const update = vi.spyOn(previous.client, 'updateProfile').mockImplementation(
      () =>
        new Promise((resolve) => {
          finishSave = resolve
        }),
    )
    const acknowledge = vi.spyOn(previous.client, 'acknowledgeIdentityOperation')
    const currentClient = shallowRef(previous.client)
    const root = mount(() =>
      h(AdminUsersScreen, {
        client: currentClient.value,
        accessClient: previous.accessClient,
        authority: previous.authority,
      }),
    )
    await flush()
    await click(nodes(root).find((node) => node.props['data-user-id'] === 3)!)
    await click(button(root, 'Редактировать данные'))
    const field = nodes(root).find(
      (node) => node.tag === 'input' && node.props.inputmode === 'numeric',
    )!
    await input(field, '987654321')
    await submit(root)
    expect(update).toHaveBeenCalledOnce()
    currentClient.value = replacement.client
    await flush()
    expect(nodes(root).find((node) => node.props['data-view'])?.props['data-view']).toBe('list')
    expect(nodes(root).some((node) => node.value === '987654321')).toBe(false)
    finishSave({
      ...original,
      login: 'previous-actor-result',
      initialPassword: 'previous-actor-proof',
    })
    await flush()
    expect(text(root)).not.toContain('previous-actor-result')
    expect(text(root)).not.toContain('previous-actor-proof')
    expect(text(root)).not.toContain('Данные пользователя сохранены.')
    expect(acknowledge).not.toHaveBeenCalled()
  })

  it('never loses a typed teacher number when the pending profile read finishes', async () => {
    const { client, accessClient, authority } = createAdminUsersReviewApis('recovery')
    const original = await client.getUser(1)
    const fresh = { ...original, firstName: 'Обновлённая', fullName: 'Обновлённая Синтетика' }
    let finishProfile!: (user: typeof original) => void
    vi.spyOn(client, 'getUser')
      .mockResolvedValue(fresh)
      .mockResolvedValueOnce(original)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishProfile = resolve
          }),
      )
    const update = vi.spyOn(client, 'updateRole').mockResolvedValue(fresh)
    const root = mount(() => h(AdminUsersScreen, { client, accessClient, authority }))
    await flush()
    await click(nodes(root).find((node) => node.props['data-user-id'] === 1)!)
    await click(button(root, 'Редактировать данные'))
    const employeeNumber = 'E2E-SYNTHETIC-TEACHER'
    const employeeInput = () =>
      nodes(root).find((node) => node.tag === 'input' && node.props.maxlength === '32')!
    let entered: TestElement | null = null
    // Model a real click: the browser never dispatches a disabled button.
    // If navigation is admitted, a late response must preserve the entered data.
    if (!button(root, 'Добавить роль').props.disabled) {
      await click(button(root, 'Добавить роль'))
      await click(button(root, 'Преподаватель'))
      entered = employeeInput()
      await input(entered, employeeNumber)
    }
    finishProfile(fresh)
    await flush()
    expect(update).not.toHaveBeenCalled()
    if (entered) expect(entered.value).toBe(employeeNumber)
    else {
      expect(text(root)).toContain(fresh.fullName)
      await click(button(root, 'Добавить роль'))
      await click(button(root, 'Преподаватель'))
      await input(employeeInput(), employeeNumber)
    }
    await submit(root)
    expect(update).toHaveBeenCalledOnce()
    expect(update.mock.calls[0]!.slice(0, 3)).toEqual([
      1,
      'TEACHER',
      { status: 'ACTIVE', employeeNumber },
    ])
    expect(text(root)).toContain('Роль сохранена.')
  })
})
async function archive(outcomes: unknown[]): Promise<{
  root: TestElement
  archiveCall: ReturnType<typeof vi.fn>
  ownerError: ReturnType<typeof vi.fn>
}> {
  const { client, accessClient, authority } = createAdminUsersReviewApis('archive-password')
  const archiveCall = vi.fn(async () => {
    const outcome = outcomes.shift()
    if (outcome) throw outcome
  })
  vi.spyOn(client, 'archiveUser').mockImplementation(archiveCall)
  const ownerError = vi.fn()
  const root = mount(() =>
    h(AdminUsersScreen, { client, accessClient, authority, onOwnerError: ownerError }),
  )
  await flush()
  await click(nodes(root).find((node) => node.props['data-user-id'] === 3)!)
  await click(button(root, 'Редактировать данные'))
  await click(button(root, 'Архивировать аккаунт'))
  await input(
    nodes(root).find((node) => node.props.autocomplete === 'current-password')!,
    'synthetic-old-proof',
  )
  await check(nodes(root).find((node) => node.tag === 'input' && node.type === 'checkbox')!)
  await submit(root)
  return { root, archiveCall, ownerError }
}
describe('protected archive correction', () => {
  it.each([failure(403, 'CURRENT_PASSWORD_INVALID'), failure(400), failure(429)])(
    'keeps target and operation while accepting a new proof after definite denial %#',
    async (denial) => {
      const { root, archiveCall, ownerError } = await archive([denial])
      const first = { ...(archiveCall.mock.calls[0]![1] as object) }
      expect(ownerError).not.toHaveBeenCalled()
      const password = nodes(root).find((node) => node.props.autocomplete === 'current-password')!
      expect(password.value).toBe('')
      await input(password, 'synthetic-new-proof')
      await submit(root)
      expect(archiveCall).toHaveBeenCalledTimes(2)
      expect(archiveCall.mock.calls[1]![1]).toEqual({ ...first, password: 'synthetic-new-proof' })
      expect(text(root)).toContain('Аккаунт архивирован')
    },
  )
  it('replays an unknown result with exactly the original proof and operation', async () => {
    const { root, archiveCall } = await archive([new TypeError('Synthetic response lost')])
    const first = archiveCall.mock.calls[0]![1]
    expect(
      nodes(root).find((node) => node.props.autocomplete === 'current-password')!.props.disabled,
    ).toBe(true)
    await submit(root)
    expect(archiveCall.mock.calls[1]![1]).toEqual(first)
  })
  it.each(['', 'ADMIN_PERMISSION_DENIED'])(
    'fails closed for opaque/capability403 %s',
    async (code) => {
      const { root, ownerError } = await archive([failure(403, code)])
      expect(ownerError).toHaveBeenCalledOnce()
      expect(nodes(root).some((node) => node.props.autocomplete === 'current-password')).toBe(false)
    },
  )
})
describe('non-idempotent recovery correction', () => {
  it.each([new TypeError('Synthetic lost response'), failure(503)])(
    'requires a separate conscious issue after unknown result %#',
    async (error) => {
      const { client } = createAdminUsersReviewApis('recovery')
      const issue = vi.spyOn(client, 'issueRecoveryLink').mockRejectedValue(error)
      const ownerError = vi.fn()
      const root = mount(() =>
        h(RecoveryLinkPanel, { client, userId: 3, onOwnerError: ownerError }),
      )
      await click(button(root, 'Создать ссылку восстановления'))
      expect(ownerError).not.toHaveBeenCalled()
      expect(text(root)).toContain('Предыдущая ссылка могла быть создана')
      const newIssue = button(root, 'Создать новую ссылку')
      expect(newIssue.props.disabled).toBe(false)
      await flush()
      expect(issue).toHaveBeenCalledTimes(1)
      expect(nodes(root).some((node) => Object.hasOwn(node.props, 'readonly'))).toBe(false)
      await click(newIssue)
      expect(issue).toHaveBeenCalledTimes(2)
      expect(nodes(root).some((node) => Object.hasOwn(node.props, 'readonly'))).toBe(false)
    },
  )
  it('allows a deliberate retry after a definite400', async () => {
    const { client } = createAdminUsersReviewApis('recovery')
    const issue = vi.spyOn(client, 'issueRecoveryLink').mockRejectedValue(failure(400))
    const root = mount(() => h(RecoveryLinkPanel, { client, userId: 3 }))
    await click(button(root, 'Создать ссылку восстановления'))
    expect(button(root, 'Создать ссылку восстановления').props.disabled).toBe(false)
    expect(text(root)).not.toContain('Результат выдачи неизвестен')
    await click(button(root, 'Создать ссылку восстановления'))
    expect(issue).toHaveBeenCalledTimes(2)
  })
  it('ignores a late old-target outcome after context changes', async () => {
    const { client } = createAdminUsersReviewApis('recovery')
    let rejectOld!: (error: unknown) => void
    const issue = vi.spyOn(client, 'issueRecoveryLink').mockImplementation(
      () =>
        new Promise((_resolve, reject) => {
          rejectOld = reject
        }),
    )
    const props = reactive({ client: markRaw(client), userId: 3 })
    const root = mount(() => h(RecoveryLinkPanel, props))
    await click(button(root, 'Создать ссылку восстановления'))
    props.userId = 42
    await flush()
    rejectOld(new TypeError('Synthetic late old target'))
    await flush()
    expect(text(root)).not.toContain('Результат выдачи неизвестен')
    expect(button(root, 'Создать ссылку восстановления').props.disabled).toBe(false)
    expect(issue).toHaveBeenCalledOnce()
  })
})

describe('R2 recovery issued task lifecycle', () => {
  it('clears the result on Done and issues a fresh link only on the next explicit request', async () => {
    const { client } = createAdminUsersReviewApis('recovery')
    const issue = vi.spyOn(client, 'issueRecoveryLink')
    const done = vi.fn()
    const root = mount(() => h(RecoveryLinkPanel, { client, userId: 3, onDone: done }))
    expect(issue).not.toHaveBeenCalled()
    await click(button(root, 'Создать ссылку восстановления'))
    const previous = nodes(root).find((node) => Object.hasOwn(node.props, 'readonly'))!.value
    expect(nodes(root).some((node) => node.props['data-recovery-view'] === 'issued')).toBe(true)
    expect(
      nodes(root).filter(
        (node) => node.tag === 'button' && node.props['aria-label'] === 'Скопировать ссылку',
      ),
    ).toHaveLength(1)
    expect(
      nodes(root).some(
        (node) => node.tag === 'button' && text(node).trim() === 'Создать ссылку восстановления',
      ),
    ).toBe(false)
    await click(button(root, 'Готово'))
    expect(done).toHaveBeenCalledOnce()
    expect(nodes(root).some((node) => Object.hasOwn(node.props, 'readonly'))).toBe(false)
    expect(issue).toHaveBeenCalledTimes(1)
    await click(button(root, 'Создать ссылку восстановления'))
    expect(issue).toHaveBeenCalledTimes(2)
    expect(nodes(root).find((node) => Object.hasOwn(node.props, 'readonly'))!.value).not.toBe(
      previous,
    )
  })
  it('uses the server expiry and clears the issued material after the one-hour deadline', async () => {
    vi.useFakeTimers()
    const { client } = createAdminUsersReviewApis('recovery')
    const root = mount(() => h(RecoveryLinkPanel, { client, userId: 3 }))
    await click(button(root, 'Создать ссылку восстановления'))
    expect(nodes(root).some((node) => Object.hasOwn(node.props, 'readonly'))).toBe(true)
    vi.setSystemTime(Date.now() + 3600001)
    vi.advanceTimersByTime(1000)
    await flush()
    expect(nodes(root).some((node) => Object.hasOwn(node.props, 'readonly'))).toBe(false)
    expect(text(root)).toContain('Срок ссылки истёк')
    expect(button(root, 'Создать новую ссылку').props.disabled).toBe(false)
  })
  it('Back clears the issued child before re-opening a new recovery task', async () => {
    const { client, accessClient, authority } = createAdminUsersReviewApis('recovery')
    const issue = vi.spyOn(client, 'issueRecoveryLink')
    const root = mount(() => h(AdminUsersScreen, { client, accessClient, authority }))
    await flush()
    await click(nodes(root).find((node) => node.props['data-user-id'] === 3)!)
    await click(button(root, 'Редактировать данные'))
    await click(button(root, 'Восстановить доступ'))
    await click(button(root, 'Создать ссылку восстановления'))
    expect(nodes(root).some((node) => node.props['data-view'] === 'recovery-issued')).toBe(true)
    await click(
      nodes(root).find((node) => node.tag === 'button' && node.props['aria-label'] === 'Назад')!,
    )
    expect(
      nodes(root).some(
        (node) => Object.hasOwn(node.props, 'readonly') && node.value.includes('password-reset'),
      ),
    ).toBe(false)
    await click(button(root, 'Редактировать данные'))
    await click(button(root, 'Восстановить доступ'))
    expect(nodes(root).some((node) => node.props['data-view'] === 'recovery')).toBe(true)
    expect(issue).toHaveBeenCalledTimes(1)
    expect(button(root, 'Создать ссылку восстановления').props.disabled).toBe(false)
  })
})
async function accessEditor(baseline: AdminAccessProfile) {
  const { client, accessClient, authority } = createAdminUsersReviewApis('permissions-mixed')
  vi.spyOn(accessClient, 'getUserAccess').mockResolvedValue(baseline)
  const update = vi
    .spyOn(accessClient, 'updateUserAccess')
    .mockResolvedValue({ ...baseline, permissions: ['USERS_VIEW', 'USERS_MANAGE'], version: 5 })
  const currentAuthority = shallowRef(authority)
  const currentAccessClient = shallowRef(accessClient)
  const root = mount(() =>
    h(AdminUsersScreen, {
      client,
      accessClient: currentAccessClient.value,
      authority: currentAuthority.value,
    }),
  )
  await flush()
  const otherTarget = nodes(root).find(
    (node) => node.props['data-user-id'] && node.props['data-user-id'] !== 3,
  )!
  await click(nodes(root).find((node) => node.props['data-user-id'] === 3)!)
  await click(button(root, 'Редактировать данные'))
  await click(button(root, 'Настроить права администратора'))
  return { root, update, client, accessClient, currentAuthority, currentAccessClient, otherTarget }
}
const accessBaseline: AdminAccessProfile = {
  userId: 3,
  assigned: true,
  mode: 'LIMITED',
  permissions: ['USERS_VIEW', 'GROUPS_VIEW'],
  version: 4,
}
describe('user export follows the displayed registry', () => {
  it('keeps an unapplied search draft out of the export, then exports the successfully applied search', async () => {
    const { client, accessClient, authority } = createAdminUsersReviewApis('recovery')
    const list = vi.spyOn(client, 'listUsers')
    vi.spyOn(client, 'exportFormats').mockResolvedValue([{ code: 'html', label: 'HTML' }])
    const download = vi.fn<ReportDownloadPort['download']>(async () => 'accepted')
    const root = mount(() =>
      h(AdminUsersScreen, {
        client,
        accessClient,
        authority,
        reportDownload: { carrier: 'browser', download },
      }),
    )
    await flush()
    const applied = list.mock.calls.at(-1)![0]!
    expect(applied.search).toBe('')
    await input(
      nodes(root).find((node) => node.props['aria-label'] === 'Найти пользователя')!,
      'Новый поиск',
    )
    expect(list).toHaveBeenCalledTimes(1)
    await click(button(root, 'Выгрузка'))
    await click(button(root, 'Скачать'))
    expect(download.mock.calls[0]![0]).toMatchObject({
      kind: 'ADMIN_USERS',
      adminUsers: { format: 'html' },
    })
    expect(
      (download.mock.calls[0]![0] as { adminUsers: { search?: string } }).adminUsers.search,
    ).toBeUndefined()
    await submit(root)
    expect(list).toHaveBeenCalledTimes(2)
    const nextApplied = list.mock.calls.at(-1)![0]!
    expect(nextApplied.search).toBe('Новый поиск')
    await click(button(root, 'Выгрузка'))
    await click(button(root, 'Скачать'))
    expect(
      (download.mock.calls[1]![0] as { adminUsers: { search: string } }).adminUsers.search,
    ).toBe(nextApplied.search)
  })
})
describe('R2 actual screen permission confirmations', () => {
  it('sends one immutable CAS only after both revoke and grant steps', async () => {
    const { root, update } = await accessEditor(accessBaseline)
    await check(nodes(root).find((node) => node.props['data-permission'] === 'GROUPS_VIEW')!, false)
    await check(nodes(root).find((node) => node.props['data-permission'] === 'USERS_MANAGE')!)
    await click(button(root, 'Проверить изменения'))
    const confirm = () => nodes(root).find((node) => node.tag === 'dialog' && node.open)!
    await check(nodes(confirm()).find((node) => node.tag === 'input' && node.type === 'checkbox')!)
    await click(button(root, 'Подтвердить отзыв'))
    expect(update).not.toHaveBeenCalled()
    expect(text(confirm())).toContain('Применить права?')
    expect(nodes(confirm()).find((node) => node.tag === 'input')!.checked).toBe(false)
    await check(nodes(confirm()).find((node) => node.tag === 'input' && node.type === 'checkbox')!)
    await click(button(root, 'Применить права'))
    expect(update).toHaveBeenCalledTimes(1)
    expect(update.mock.calls[0]![1]).toEqual({
      mode: 'LIMITED',
      permissions: ['USERS_VIEW', 'USERS_MANAGE'],
      expectedVersion: 4,
    })
  })
  it('checks fresh server rights after CAS409 and never retries the mutation automatically', async () => {
    const { root, update } = await accessEditor({ ...accessBaseline, permissions: ['USERS_VIEW'] })
    update.mockRejectedValue(
      new AdminAccessApiError(
        new Response(null, { status: 409 }),
        { detail: 'Synthetic conflict' } as never,
        { ...accessBaseline, permissions: ['USERS_VIEW', 'GROUPS_VIEW'], version: 5 },
      ),
    )
    await check(nodes(root).find((node) => node.props['data-permission'] === 'USERS_MANAGE')!)
    await click(button(root, 'Проверить изменения'))
    await check(
      nodes(root)
        .find((node) => node.tag === 'dialog' && node.open)!
        .children.flatMap(nodes)
        .find((node) => node.tag === 'input' && node.type === 'checkbox')!,
    )
    await click(button(root, 'Применить права'))
    expect(update).toHaveBeenCalledTimes(1)
    expect(
      nodes(root).find((node) => node.props['data-permission'] === 'GROUPS_VIEW')!.checked,
    ).toBe(true)
    expect(
      nodes(root).find((node) => node.props['data-permission'] === 'USERS_MANAGE')!.checked,
    ).toBe(false)
    expect(text(root)).toContain('Права изменились')
    expect(nodes(root).some((node) => node.props['data-tone'] === 'danger')).toBe(true)
  })
})

describe('group identity observation ownership', () => {
  it.each(['open-another-group', 'authority-change'])(
    'ignores a late resumed assignment after %s',
    async (change) => {
      vi.stubGlobal('document', testDocument)
      vi.stubGlobal('window', { location: { origin: 'https://synthetic.invalid' } })
      const client = createAdminGroupsFixture('active')
      const authority = shallowRef(adminGroupsAuthority('active'))
      vi.spyOn(client, 'hasPendingIdentityOperation', 'get').mockReturnValue(true)
      let finishResume!: (
        result: Awaited<ReturnType<typeof client.resumeIdentityOperation>>,
      ) => void
      vi.spyOn(client, 'resumeIdentityOperation').mockImplementation(
        () =>
          new Promise((resolve) => {
            finishResume = resolve
          }),
      )
      const acknowledge = vi.spyOn(client, 'acknowledgeIdentityOperation')
      const list = vi.spyOn(client, 'listGroups')
      const root = mount(() => h(AdminGroupsScreen, { client, authority: authority.value }))
      await flush()
      const openGroup = async (id: number): Promise<void> => {
        const article = nodes(root).find((node) => node.props['data-group-id'] === id)!
        await click(nodes(article).find((node) => node.tag === 'button')!)
        await click(button(article, 'Изменить старосту'))
      }
      await openGroup(1)
      await click(button(root, 'Проверить результат'))
      let title = ''
      if (change === 'open-another-group') {
        await click(button(root, 'Закрыть'))
        await openGroup(2)
        title = text(nodes(root).find((node) => node.props.id === 'admin-groups-headman-title')!)
      } else {
        authority.value = { ...authority.value!, version: authority.value!.version + 1 }
        await flush()
      }
      const readsBeforeCompletion = list.mock.calls.length
      finishResume({
        kind: 'HEADMAN',
        assignment: {
          groupId: 1,
          headmanId: 12,
          headmanFio: 'Результат предыдущего контекста',
          changed: true,
          activatesDraft: false,
          assistantsRevoked: 2,
        },
      })
      await flush()
      expect(acknowledge).not.toHaveBeenCalled()
      expect(list).toHaveBeenCalledTimes(readsBeforeCompletion)
      expect(text(root)).not.toContain('Результат предыдущего контекста')
      if (change === 'open-another-group') {
        expect(
          text(nodes(root).find((node) => node.props.id === 'admin-groups-headman-title')!),
        ).toBe(title)
        expect(
          nodes(root).find((node) => node.props['data-admin-groups-view'])?.props[
            'data-admin-groups-view'
          ],
        ).toBe('headman')
      } else {
        expect(
          nodes(root).find((node) => node.props['data-admin-groups-view'])?.props[
            'data-admin-groups-view'
          ],
        ).toBe('list')
      }
      expect(button(root, 'Проверить результат').props.disabled).toBe(false)
    },
  )
})

describe('durable group lifecycle feedback', () => {
  it.each(['ARCHIVE', 'RESTORE', 'PROMOTION'] as const)(
    'keeps %s pending and acknowledges only the resumed final result',
    async (kind) => {
      vi.stubGlobal('document', testDocument)
      vi.stubGlobal('window', { location: { origin: 'https://synthetic.invalid' } })
      const client = createAdminGroupsFixture('active')
      const preview = await client.previewPromotion()
      let pending = false
      vi.spyOn(client, 'hasPendingIdentityOperation', 'get').mockImplementation(() => pending)
      const failPending = async () => {
        pending = true
        throw new IdentityOperationError('PENDING')
      }
      if (kind === 'ARCHIVE') vi.spyOn(client, 'archiveGroup').mockImplementation(failPending)
      else if (kind === 'RESTORE') vi.spyOn(client, 'restoreGroup').mockImplementation(failPending)
      else vi.spyOn(client, 'executePromotion').mockImplementation(failPending)
      const acknowledge = vi.spyOn(client, 'acknowledgeIdentityOperation')
      let groupId = 1
      vi.spyOn(client, 'resumeIdentityOperation').mockImplementation(async () => {
        pending = false
        return kind === 'PROMOTION'
          ? { kind, summary: { ...preview, executed: true, dryRun: false } }
          : { kind, groupId }
      })
      const root = mount(() =>
        h(AdminGroupsScreen, { client, authority: adminGroupsAuthority('active') }),
      )
      await flush()
      if (kind === 'PROMOTION') {
        await click(button(root, 'Перевести все группы на следующий курс'))
        await click(button(root, 'Подтвердить показанный план'))
        await click(button(root, 'Подтвердить перевод'))
      } else {
        if (kind === 'RESTORE') {
          await click(
            nodes(root).find(
              (node) => node.props.role === 'tab' && text(node).startsWith('Архив'),
            )!,
          )
        }
        const article = nodes(root).find((node) => node.props['data-group-id'])!
        groupId = Number(article.props['data-group-id'])
        await click(nodes(article).find((node) => node.tag === 'button')!)
        await click(button(article, kind === 'ARCHIVE' ? 'Архивировать' : 'Восстановить группу'))
        if (kind === 'ARCHIVE') await click(button(root, 'Подтвердить архивацию'))
      }
      expect(text(root)).toContain('Сервер выполняет действие')
      expect(nodes(root).some((node) => node.props['data-tone'] === 'success')).toBe(false)
      expect(acknowledge).not.toHaveBeenCalled()
      await click(button(root, 'Проверить результат'))
      expect(acknowledge).toHaveBeenCalledOnce()
      expect(text(root)).not.toContain('Сервер выполняет действие')
      expect(nodes(root).some((node) => node.props['data-tone'] === 'success')).toBe(true)
    },
  )

  it('offers explicit recovery only after current GET confirmation and ignores a late recovery after authority changes', async () => {
    vi.stubGlobal('document', testDocument)
    vi.stubGlobal('window', { location: { origin: 'https://synthetic.invalid' } })
    const client = createAdminGroupsFixture('active')
    const failedId = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'
    vi.spyOn(client, 'failedGroupOperationId', 'get').mockReturnValue(failedId)
    const confirm = vi.spyOn(client, 'confirmGroupRecovery').mockResolvedValue(failedId)
    let finishRecovery!: () => void
    const recover = vi.spyOn(client, 'reconcileGroupOperation').mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          finishRecovery = resolve
        }),
    )
    const acknowledge = vi.spyOn(client, 'acknowledgeIdentityOperation')
    const authority = shallowRef(adminGroupsAuthority('active'))
    const root = mount(() => h(AdminGroupsScreen, { client, authority: authority.value }))
    await flush()
    expect(
      nodes(root).some(
        (node) => node.tag === 'button' && text(node).trim() === 'Восстановить операцию',
      ),
    ).toBe(false)
    await click(button(root, 'Проверить результат'))
    expect(confirm).toHaveBeenCalledOnce()
    await click(button(root, 'Восстановить операцию'))
    expect(recover).toHaveBeenCalledWith(failedId, expect.any(AbortSignal))
    authority.value = { ...authority.value!, version: authority.value!.version + 1 }
    await flush()
    finishRecovery()
    await flush()
    expect(acknowledge).not.toHaveBeenCalled()
    expect(text(root)).not.toContain('Операция восстановлена.')
  })
})

describe('durable group lifecycle late observers', () => {
  it.each(['ARCHIVE', 'RESTORE', 'PROMOTION'] as const)(
    'does not apply a late resumed %s result after leaving its context',
    async (kind) => {
      vi.stubGlobal('document', testDocument)
      vi.stubGlobal('window', { location: { origin: 'https://synthetic.invalid' } })
      const client = createAdminGroupsFixture('active')
      const preview = await client.previewPromotion()
      let pending = false
      vi.spyOn(client, 'hasPendingIdentityOperation', 'get').mockImplementation(() => pending)
      const failPending = async () => {
        pending = true
        throw new IdentityOperationError('PENDING')
      }
      if (kind === 'ARCHIVE') vi.spyOn(client, 'archiveGroup').mockImplementation(failPending)
      else if (kind === 'RESTORE') vi.spyOn(client, 'restoreGroup').mockImplementation(failPending)
      else vi.spyOn(client, 'executePromotion').mockImplementation(failPending)
      const acknowledge = vi.spyOn(client, 'acknowledgeIdentityOperation')
      let finishResume!: () => void
      vi.spyOn(client, 'resumeIdentityOperation').mockImplementation(
        () =>
          new Promise((resolve) => {
            finishResume = () =>
              resolve(
                kind === 'PROMOTION'
                  ? { kind, summary: { ...preview, executed: true, dryRun: false } }
                  : { kind, groupId: kind === 'RESTORE' ? 32 : 1 },
              )
          }),
      )
      const root = mount(() =>
        h(AdminGroupsScreen, { client, authority: adminGroupsAuthority('active') }),
      )
      await flush()
      if (kind === 'PROMOTION') {
        await click(button(root, 'Перевести все группы на следующий курс'))
        await click(button(root, 'Подтвердить показанный план'))
        await click(button(root, 'Подтвердить перевод'))
      } else {
        if (kind === 'RESTORE')
          await click(
            nodes(root).find(
              (node) => node.props.role === 'tab' && text(node).startsWith('Архив'),
            )!,
          )
        const article = nodes(root).find((node) => node.props['data-group-id'])!
        await click(nodes(article).find((node) => node.tag === 'button')!)
        await click(button(article, kind === 'ARCHIVE' ? 'Архивировать' : 'Восстановить группу'))
        if (kind === 'ARCHIVE') await click(button(root, 'Подтвердить архивацию'))
      }
      await click(button(root, 'Проверить результат'))
      if (kind === 'ARCHIVE') {
        const other = nodes(root).find((node) => node.props['data-group-id'] === 2)!
        await click(nodes(other).find((node) => node.tag === 'button')!)
        await click(button(other, 'Изменить старосту'))
      } else if (kind === 'RESTORE') {
        await click(
          nodes(root).find(
            (node) => node.props.role === 'tab' && text(node).startsWith('Активные'),
          )!,
        )
      } else {
        await click(button(root, 'Закрыть'))
      }
      const view = nodes(root).find((node) => node.props['data-admin-groups-view'])?.props[
        'data-admin-groups-view'
      ]
      finishResume()
      await flush()
      expect(acknowledge).not.toHaveBeenCalled()
      expect(nodes(root).some((node) => node.props['data-tone'] === 'success')).toBe(false)
      expect(
        nodes(root).find((node) => node.props['data-admin-groups-view'])?.props[
          'data-admin-groups-view'
        ],
      ).toBe(view)
      expect(button(root, 'Проверить результат').props.disabled).toBe(false)
    },
  )

  it('refreshes the existing registry after an explicitly confirmed recovery succeeds', async () => {
    vi.stubGlobal('document', testDocument)
    vi.stubGlobal('window', { location: { origin: 'https://synthetic.invalid' } })
    const client = createAdminGroupsFixture('active')
    const failedId = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee'
    vi.spyOn(client, 'failedGroupOperationId', 'get').mockReturnValue(failedId)
    vi.spyOn(client, 'confirmGroupRecovery').mockResolvedValue(failedId)
    const recover = vi.spyOn(client, 'reconcileGroupOperation').mockResolvedValue()
    const acknowledge = vi.spyOn(client, 'acknowledgeIdentityOperation')
    const reads = vi.spyOn(client, 'listGroups')
    const root = mount(() =>
      h(AdminGroupsScreen, { client, authority: adminGroupsAuthority('active') }),
    )
    await flush()
    const before = reads.mock.calls.length
    await click(button(root, 'Проверить результат'))
    await click(button(root, 'Восстановить операцию'))
    expect(recover).toHaveBeenCalledWith(failedId, expect.any(AbortSignal))
    expect(acknowledge).toHaveBeenCalledOnce()
    expect(reads).toHaveBeenCalledTimes(before + 1)
    expect(text(root)).toContain('Операция восстановлена. Данные обновлены.')
    expect(
      nodes(root).some(
        (node) => node.tag === 'button' && text(node).trim() === 'Восстановить операцию',
      ),
    ).toBe(false)
  })
})

describe('recovery read feedback', () => {
  it.each([
    ['explicit', 'registry'],
    ['resumed', 'registry'],
    ['explicit', 'preview'],
    ['resumed', 'preview'],
  ] as const)(
    'keeps %s recovery confirmed when the fresh %s read fails and retries only reads',
    async (mode, failedRead) => {
      vi.stubGlobal('document', testDocument)
      vi.stubGlobal('window', { location: { origin: 'https://synthetic.invalid' } })
      const client = createAdminGroupsFixture('active')
      const failedId = 'ffffffff-ffff-4fff-8fff-ffffffffffff'
      let pending = mode === 'resumed'
      vi.spyOn(client, 'hasPendingIdentityOperation', 'get').mockImplementation(() => pending)
      vi.spyOn(client, 'failedGroupOperationId', 'get').mockReturnValue(failedId)
      vi.spyOn(client, 'confirmGroupRecovery').mockResolvedValue(failedId)
      const recover = vi.spyOn(client, 'reconcileGroupOperation').mockResolvedValue()
      const resume = vi.spyOn(client, 'resumeIdentityOperation').mockImplementation(async () => {
        pending = false
        return { kind: 'RECOVERY' }
      })
      const acknowledge = vi.spyOn(client, 'acknowledgeIdentityOperation')
      const reads = vi.spyOn(client, 'listGroups')
      const previews = vi.spyOn(client, 'previewPromotion')
      const root = mount(() =>
        h(AdminGroupsScreen, { client, authority: adminGroupsAuthority('active') }),
      )
      await flush()
      if (failedRead === 'preview')
        await click(button(root, 'Перевести все группы на следующий курс'))
      if (failedRead === 'registry')
        reads.mockRejectedValueOnce(new Error('synthetic registry failure'))
      else previews.mockRejectedValueOnce(new Error('synthetic preview failure'))
      await click(button(root, 'Проверить результат'))
      if (mode === 'explicit') await click(button(root, 'Восстановить операцию'))
      expect(acknowledge).toHaveBeenCalledOnce()
      expect(text(root)).toContain(
        'Операция восстановлена, но реестр или предпросмотр не удалось обновить.',
      )
      expect(text(root)).not.toContain('Данные обновлены.')
      expect(nodes(root).some((node) => node.props['data-tone'] === 'warning')).toBe(true)
      const mutationCalls = recover.mock.calls.length + resume.mock.calls.length
      const readCalls = reads.mock.calls.length
      await click(button(root, 'Обновить данные'))
      expect(reads).toHaveBeenCalledTimes(readCalls + 1)
      expect(recover.mock.calls.length + resume.mock.calls.length).toBe(mutationCalls)
      expect(acknowledge).toHaveBeenCalledOnce()
      expect(text(root)).toContain('Операция восстановлена. Данные обновлены.')
      expect(
        nodes(root).some(
          (node) => node.tag === 'button' && text(node).trim() === 'Обновить данные',
        ),
      ).toBe(false)
    },
  )

  it.each(['explicit', 'resumed'] as const)(
    'ignores a late failed read after confirmed %s recovery loses its authority context',
    async (mode) => {
      vi.stubGlobal('document', testDocument)
      vi.stubGlobal('window', { location: { origin: 'https://synthetic.invalid' } })
      const client = createAdminGroupsFixture('active')
      const failedId = 'abababab-abab-4bab-8bab-abababababab'
      vi.spyOn(client, 'hasPendingIdentityOperation', 'get').mockReturnValue(mode === 'resumed')
      vi.spyOn(client, 'failedGroupOperationId', 'get').mockReturnValue(failedId)
      vi.spyOn(client, 'confirmGroupRecovery').mockResolvedValue(failedId)
      const recover = vi.spyOn(client, 'reconcileGroupOperation').mockResolvedValue()
      vi.spyOn(client, 'resumeIdentityOperation').mockResolvedValue({ kind: 'RECOVERY' })
      const acknowledge = vi.spyOn(client, 'acknowledgeIdentityOperation')
      const reads = vi.spyOn(client, 'listGroups')
      const authority = shallowRef(adminGroupsAuthority('active'))
      const root = mount(() => h(AdminGroupsScreen, { client, authority: authority.value }))
      await flush()
      let rejectRead!: (reason: Error) => void
      reads.mockImplementationOnce(
        () =>
          new Promise((_resolve, reject) => {
            rejectRead = reject
          }),
      )
      await click(button(root, 'Проверить результат'))
      if (mode === 'explicit') await click(button(root, 'Восстановить операцию'))
      expect(acknowledge).toHaveBeenCalledOnce()
      authority.value = { ...authority.value!, version: authority.value!.version + 1 }
      await flush()
      rejectRead(new Error('late previous-context failure'))
      await flush()
      expect(acknowledge).toHaveBeenCalledOnce()
      expect(recover).toHaveBeenCalledTimes(mode === 'explicit' ? 1 : 0)
      expect(text(root)).not.toContain('Данные обновлены.')
      expect(text(root)).not.toContain('Повтори загрузку данных.')
      expect(
        nodes(root).some(
          (node) => node.tag === 'button' && text(node).trim() === 'Обновить данные',
        ),
      ).toBe(false)
    },
  )
})

describe('recovery read observer ownership', () => {
  it('keeps a new pending restore resumable when an earlier recovery read fails late in the same registry scope', async () => {
    vi.stubGlobal('document', testDocument)
    vi.stubGlobal('window', { location: { origin: 'https://synthetic.invalid' } })
    const client = createAdminGroupsFixture('active')
    const failedId = 'bcbcbcbc-bcbc-4cbc-8cbc-bcbcbcbcbcbc'
    let pending = false
    vi.spyOn(client, 'hasPendingIdentityOperation', 'get').mockImplementation(() => pending)
    vi.spyOn(client, 'failedGroupOperationId', 'get').mockReturnValue(failedId)
    vi.spyOn(client, 'confirmGroupRecovery').mockResolvedValue(failedId)
    const recover = vi.spyOn(client, 'reconcileGroupOperation').mockResolvedValue()
    const restore = vi.spyOn(client, 'restoreGroup').mockImplementation(async () => {
      pending = true
      throw new IdentityOperationError('PENDING')
    })
    const resume = vi.spyOn(client, 'resumeIdentityOperation').mockImplementation(async () => {
      pending = false
      return { kind: 'RESTORE', groupId: 32 }
    })
    const acknowledge = vi.spyOn(client, 'acknowledgeIdentityOperation')
    const reads = vi.spyOn(client, 'listGroups')
    const root = mount(() =>
      h(AdminGroupsScreen, { client, authority: adminGroupsAuthority('active') }),
    )
    await flush()
    await click(
      nodes(root).find((node) => node.props.role === 'tab' && text(node).startsWith('Архив'))!,
    )
    const article = nodes(root).find((node) => node.props['data-group-id'] === 32)!
    await click(nodes(article).find((node) => node.tag === 'button')!)
    // Keep the already queued restore handler while the older recovery starts its read.
    const queuedRestore = button(article, 'Восстановить группу')
    let rejectOldRead!: (reason: Error) => void
    reads.mockImplementationOnce(
      () =>
        new Promise((_resolve, reject) => {
          rejectOldRead = reject
        }),
    )
    await click(button(root, 'Проверить результат'))
    await click(button(root, 'Восстановить операцию'))
    expect(acknowledge).toHaveBeenCalledOnce()
    await click(queuedRestore)
    expect(restore).toHaveBeenCalledOnce()
    expect(text(root)).toContain('Сервер выполняет действие')
    rejectOldRead(new Error('late old recovery read failure'))
    await flush()
    expect(text(root)).not.toContain('Повтори загрузку данных.')
    expect(
      nodes(root).some((node) => node.tag === 'button' && text(node).trim() === 'Обновить данные'),
    ).toBe(false)
    expect(button(root, 'Проверить результат').props.disabled).toBe(false)
    await click(button(root, 'Проверить результат'))
    expect(resume).toHaveBeenCalledOnce()
    expect(recover).toHaveBeenCalledOnce()
    expect(restore).toHaveBeenCalledOnce()
    expect(acknowledge).toHaveBeenCalledTimes(2)
    expect(text(root)).toContain('Группа восстановлена.')
  })
})

async function confirmAccessAction(root: TestElement, action: 'update' | 'revoke' = 'update') {
  if (action === 'update') {
    await check(nodes(root).find((node) => node.props['data-permission'] === 'USERS_MANAGE')!)
    await click(button(root, 'Проверить изменения'))
  } else await click(button(root, 'Отозвать права администратора'))
  const dialog = nodes(root).find((node) => node.tag === 'dialog' && node.open)!
  await check(nodes(dialog).find((node) => node.tag === 'input' && node.type === 'checkbox')!)
  await click(button(root, action === 'update' ? 'Применить права' : 'Отозвать права'))
}
describe('AdminAccess durable screen observer', () => {
  it('keeps Back blocked until a saved grant refreshes the target roles, then reopens persisted rights', async () => {
    const { client, accessClient, authority } = createAdminUsersReviewApis('recovery')
    const assignedUser = await createAdminUsersReviewApis('permissions-mixed').client.getUser(3)
    const assignedProfile: AdminAccessProfile = {
      ...accessBaseline,
      permissions: ['USERS_VIEW', 'USERS_MANAGE'],
      version: 1,
    }
    const root = mount(() => h(AdminUsersScreen, { client, accessClient, authority }))
    await flush()
    await click(nodes(root).find((node) => node.props['data-user-id'] === 3)!)
    await click(button(root, 'Редактировать данные'))
    await click(button(root, 'Назначить права администратора'))
    const update = vi.spyOn(accessClient, 'updateUserAccess').mockResolvedValue(assignedProfile)
    vi.spyOn(accessClient, 'getUserAccess').mockResolvedValue(assignedProfile)
    let finishUserRead!: (user: typeof assignedUser) => void
    vi.spyOn(client, 'getUser').mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finishUserRead = resolve
        }),
    )
    await confirmAccessAction(root)
    expect(update).toHaveBeenCalledOnce()
    expect(text(root)).toContain('Права администратора сохранены.')
    const back = () =>
      nodes(root).find((node) => node.tag === 'button' && node.props['aria-label'] === 'Назад')!
    expect(back().props.disabled).toBe(true)
    await click(back())
    expect(nodes(root).some((node) => node.props['data-view'] === 'permissions')).toBe(true)
    finishUserRead(assignedUser)
    await flush()
    expect(back().props.disabled).toBe(false)
    await click(back())
    expect(nodes(root).some((node) => node.props['data-view'] === 'edit')).toBe(true)
    await click(button(root, 'Настроить права администратора'))
    expect(
      nodes(root).find((node) => node.props['data-permission'] === 'USERS_VIEW')!.checked,
    ).toBe(true)
    expect(
      nodes(root).find((node) => node.props['data-permission'] === 'USERS_MANAGE')!.checked,
    ).toBe(true)
    expect(update).toHaveBeenCalledOnce()
  })
  it.each(['update', 'revoke'] as const)(
    'keeps %s pending, then retries only fresh reads after confirmed completion',
    async (action) => {
      const context = await accessEditor({ ...accessBaseline, permissions: ['USERS_VIEW'] })
      const { root, update, accessClient } = context
      let pending = false
      vi.spyOn(accessClient, 'hasPendingIdentityOperation', 'get').mockImplementation(() => pending)
      const write = action === 'update' ? update : vi.spyOn(accessClient, 'revokeUserAccess')
      write.mockImplementation(async () => {
        pending = true
        throw new IdentityOperationError('PENDING')
      })
      const resume = vi
        .spyOn(accessClient, 'resumeIdentityOperation')
        .mockResolvedValue({ userId: 3, revoked: action === 'revoke', profile: null })
      const acknowledge = vi
        .spyOn(accessClient, 'acknowledgeIdentityOperation')
        .mockImplementation(() => {
          pending = false
        })
      await confirmAccessAction(root, action)
      expect(write).toHaveBeenCalledTimes(1)
      expect(text(root)).not.toContain(
        action === 'update' ? 'Права администратора сохранены.' : 'Права администратора отозваны.',
      )
      expect(button(root, 'Проверить результат').props.disabled).toBe(false)
      expect(button(root, 'Проверить изменения').props.disabled).toBe(true)
      vi.mocked(accessClient.getUserAccess).mockRejectedValueOnce(
        new Error('fresh profile read unavailable'),
      )
      await click(button(root, 'Проверить результат'))
      expect(acknowledge).toHaveBeenCalledTimes(1)
      expect(text(root)).toContain('Данные не обновлены')
      expect(text(root)).toContain(
        action === 'update' ? 'Права администратора сохранены.' : 'Права администратора отозваны.',
      )
      expect(text(root)).not.toContain('Действие не выполнено')
      await click(button(root, 'Обновить данные'))
      expect(text(root)).not.toContain('Данные не обновлены')
      expect(write).toHaveBeenCalledTimes(1)
      expect(resume).toHaveBeenCalledTimes(1)
      expect(acknowledge).toHaveBeenCalledTimes(1)
    },
  )
  it('keeps a conflicting currentProfile visible but blocks a new write until terminal status', async () => {
    const { root, update, accessClient } = await accessEditor({
      ...accessBaseline,
      permissions: ['USERS_VIEW'],
    })
    let pending = false
    vi.spyOn(accessClient, 'hasPendingIdentityOperation', 'get').mockImplementation(() => pending)
    const conflict = new AdminAccessApiError(
      new Response(null, { status: 409 }),
      { detail: 'conflict' } as never,
      { ...accessBaseline, version: 5 },
    )
    update.mockImplementation(async () => {
      pending = true
      throw conflict
    })
    const resume = vi
      .spyOn(accessClient, 'resumeIdentityOperation')
      .mockImplementation(async () => {
        pending = false
        throw conflict
      })
    await confirmAccessAction(root)
    expect(button(root, 'Проверить изменения').props.disabled).toBe(true)
    expect(
      nodes(root).find((node) => node.props['data-permission'] === 'GROUPS_VIEW')!.checked,
    ).toBe(true)
    await click(button(root, 'Проверить результат'))
    expect(text(root)).toContain('Права изменились')
    expect(button(root, 'Проверить изменения').props.disabled).toBe(false)
    expect(update).toHaveBeenCalledTimes(1)
    expect(resume).toHaveBeenCalledTimes(1)
  })
  it.each(['authority', 'target', 'access-client'] as const)(
    'ignores late resumed completion after %s change',
    async (change) => {
      const context = await accessEditor({ ...accessBaseline, permissions: ['USERS_VIEW'] })
      const { root, update, accessClient, currentAuthority, currentAccessClient, otherTarget } =
        context
      let pending = false
      vi.spyOn(accessClient, 'hasPendingIdentityOperation', 'get').mockImplementation(() => pending)
      update.mockImplementation(async () => {
        pending = true
        throw new IdentityOperationError('PENDING')
      })
      let finish!: (result: AdminAccessOperationResult) => void
      vi.spyOn(accessClient, 'resumeIdentityOperation').mockImplementation(
        () =>
          new Promise((resolve) => {
            finish = resolve
          }),
      )
      const acknowledge = vi.spyOn(accessClient, 'acknowledgeIdentityOperation')
      const read = vi.mocked(accessClient.getUserAccess)
      await confirmAccessAction(root)
      await click(button(root, 'Проверить результат'))
      if (change === 'authority')
        currentAuthority.value = {
          ...currentAuthority.value,
          mode: 'LIMITED',
          permissions: ['USERS_VIEW'],
          version: 6,
        }
      else if (change === 'target') await click(otherTarget)
      else currentAccessClient.value = createAdminUsersReviewApis('permissions-mixed').accessClient
      await flush()
      const reads = read.mock.calls.length
      finish({ userId: 3, revoked: false, profile: { ...accessBaseline, version: 8 } })
      await flush()
      expect(acknowledge).not.toHaveBeenCalled()
      expect(read).toHaveBeenCalledTimes(reads)
      expect(text(root)).not.toContain('Права администратора сохранены.')
      if (change === 'authority')
        expect(button(root, 'Проверить результат').props.disabled).toBe(true)
    },
  )
  it('keeps another target profile when an explicitly resumed old operation is rejected', async () => {
    const { root, update, accessClient, otherTarget } = await accessEditor({
      ...accessBaseline,
      permissions: ['USERS_VIEW'],
    })
    let pending = false
    vi.spyOn(accessClient, 'hasPendingIdentityOperation', 'get').mockImplementation(() => pending)
    vi.spyOn(accessClient, 'identityOperationUserId', 'get').mockReturnValue(3)
    update.mockImplementation(async () => {
      pending = true
      throw new IdentityOperationError('UNKNOWN')
    })
    vi.spyOn(accessClient, 'resumeIdentityOperation').mockImplementation(async () => {
      pending = false
      throw new AdminAccessApiError(
        new Response(null, { status: 409 }),
        { detail: 'old conflict' } as never,
        accessBaseline,
      )
    })
    await confirmAccessAction(root)
    await click(otherTarget)
    const targetId = Number(otherTarget.props['data-user-id'])
    vi.mocked(accessClient.getUserAccess).mockResolvedValue({
      ...accessBaseline,
      userId: targetId,
      permissions: ['SEMESTERS_VIEW'],
      version: 9,
    })
    await click(button(root, 'Редактировать данные'))
    await click(button(root, 'Назначить права администратора'))
    await click(button(root, 'Проверить результат'))
    expect(text(root)).toContain('Предыдущее изменение прав не выполнено')
    expect(
      nodes(root).find((node) => node.props['data-permission'] === 'SEMESTERS_VIEW')!.checked,
    ).toBe(true)
    expect(
      nodes(root).find((node) => node.props['data-permission'] === 'GROUPS_VIEW')!.checked,
    ).toBe(false)
    expect(update).toHaveBeenCalledTimes(1)
  })
  it('does not apply a late fresh-read failure to a new permission context', async () => {
    const { root, update, accessClient, currentAuthority } = await accessEditor({
      ...accessBaseline,
      permissions: ['USERS_VIEW'],
    })
    const acknowledge = vi.spyOn(accessClient, 'acknowledgeIdentityOperation')
    let failRead!: (reason: Error) => void
    vi.mocked(accessClient.getUserAccess).mockImplementationOnce(
      () =>
        new Promise((_resolve, reject) => {
          failRead = reject
        }),
    )
    await confirmAccessAction(root)
    expect(update).toHaveBeenCalledTimes(1)
    expect(acknowledge).toHaveBeenCalledTimes(1)
    currentAuthority.value = { ...currentAuthority.value, version: 6 }
    await flush()
    failRead(new Error('late old-context read failure'))
    await flush()
    expect(text(root)).not.toContain('Данные не обновлены')
    expect(
      nodes(root).some((node) => node.tag === 'button' && text(node).trim() === 'Обновить данные'),
    ).toBe(false)
  })
})

async function failedRecoveryScreen(kind: IdentityRecoveryReference['kind'] = 'USER') {
  const { client, accessClient, authority } = createAdminUsersReviewApis('permissions-mixed')
  const currentAuthority = shallowRef<AdminAccessProfile | null>(authority)
  const scope = 'same-actor-session-ADMIN'
  vi.spyOn(client, 'getIdentityRecoverySessionScope').mockReturnValue(scope)
  vi.spyOn(accessClient, 'getIdentityRecoverySessionScope').mockReturnValue(scope)
  const reference: IdentityRecoveryReference = {
    operationId: crypto.randomUUID(),
    kind,
    userId: 3,
    reconcileId: null,
    archivePreviewDigest: null,
  }
  const owner = kind === 'ADMIN_ACCESS' ? 'ADMIN_ACCESS' : 'USER'
  const key = `rct:identity-recovery:v1:${owner}:${scope}:${JSON.stringify(authority)}`
  const storage = new Map<string, string>([[key, JSON.stringify(reference)]])
  vi.stubGlobal('sessionStorage', {
    getItem: (k: string) => storage.get(k) ?? null,
    setItem: (k: string, v: string) => storage.set(k, v),
    removeItem: (k: string) => storage.delete(k),
  })
  const confirm =
    kind === 'ADMIN_ACCESS'
      ? vi.spyOn(accessClient, 'confirmIdentityRecovery').mockResolvedValue(reference)
      : vi.spyOn(client, 'confirmIdentityRecovery').mockResolvedValue(reference)
  const userReconcile = vi.spyOn(client, 'reconcileIdentityOperation').mockResolvedValue()
  const accessReconcile = vi.spyOn(accessClient, 'reconcileIdentityOperation').mockResolvedValue()
  const update = vi.spyOn(client, 'updateProfile')
  const archive = vi.spyOn(client, 'archiveUser')
  const permissionWrite = vi.spyOn(accessClient, 'updateUserAccess')
  const root = mount(() =>
    h(AdminUsersScreen, { client, accessClient, authority: currentAuthority.value }),
  )
  await flush()
  return {
    root,
    client,
    accessClient,
    currentAuthority,
    reference,
    confirm,
    userReconcile,
    accessReconcile,
    update,
    archive,
    permissionWrite,
    storage,
    key,
  }
}
describe('explicit FAILED_SAFE screen recovery', () => {
  it('rehydrates the scoped selector, confirms status, and retries only GET after a committed recovery/read failure', async () => {
    const { root, client, confirm, userReconcile, update, archive, storage, key } =
      await failedRecoveryScreen()
    expect(text(root)).toContain('Операция остановлена')
    expect(text(root)).not.toContain('Действие не выполнено')
    await click(button(root, 'Проверить результат'))
    expect(confirm).toHaveBeenCalledTimes(1)
    const list = vi
      .spyOn(client, 'listUsers')
      .mockRejectedValueOnce(new Error('fresh registry unavailable'))
    await click(button(root, 'Восстановить операцию'))
    expect(userReconcile).toHaveBeenCalledTimes(1)
    expect(text(root)).toContain('Операция восстановлена.')
    expect(text(root)).toContain('Данные не обновлены')
    expect(storage.has(key)).toBe(false)
    await click(button(root, 'Обновить данные'))
    expect(text(root)).not.toContain('Данные не обновлены')
    expect(userReconcile).toHaveBeenCalledTimes(1)
    expect(list).toHaveBeenCalledTimes(2)
    expect(update).not.toHaveBeenCalled()
    expect(archive).not.toHaveBeenCalled()
  })
  it('requires a directed fresh archive preview and current password, using the existing archive confirmation form', async () => {
    const { root, client, reference, userReconcile, archive, storage } =
      await failedRecoveryScreen('ARCHIVE')
    const preview = {
      userId: 3,
      linkedGroupCount: 1,
      attendanceMarksCount: 2,
      activeHeadmanGroupCount: 1,
      soleTeacherAssignmentCount: 0,
      requiresPassword: true,
      previewDigest: 'c'.repeat(64),
      expiresAt: '2099-01-01T00:00:00Z',
    }
    const directed = vi.spyOn(client, 'previewIdentityRecovery').mockResolvedValue(preview)
    const ordinary = vi.spyOn(client, 'previewArchive')
    await click(button(root, 'Проверить результат'))
    await click(button(root, 'Восстановить операцию'))
    expect(directed).toHaveBeenCalledTimes(1)
    expect(ordinary).not.toHaveBeenCalled()
    expect(client.failedIdentityOperation?.operationId).toBe(reference.operationId)
    await check(nodes(root).find((node) => node.tag === 'input' && node.type === 'checkbox')!)
    await submit(root)
    expect(userReconcile).not.toHaveBeenCalled()
    expect(text(root)).toContain('Введи свой текущий пароль')
    await input(
      nodes(root).find((node) => node.tag === 'input' && node.type === 'password')!,
      'fresh-memory-only-proof',
    )
    await submit(root)
    expect(userReconcile.mock.calls[0]![0]).toEqual({
      archivePreviewDigest: preview.previewDigest,
      password: 'fresh-memory-only-proof',
    })
    expect(archive).not.toHaveBeenCalled()
    expect([...storage.values()].join()).not.toContain('proof')
    expect(
      nodes(root).find((node) => node.tag === 'input' && node.type === 'password'),
    ).toBeUndefined()
  })
  it('clears scoped records on actual authority change and ignores a late archive preview', async () => {
    const { root, client, currentAuthority, key, storage, userReconcile } =
      await failedRecoveryScreen('ARCHIVE')
    let finish!: (value: Awaited<ReturnType<typeof client.previewIdentityRecovery>>) => void
    vi.spyOn(client, 'previewIdentityRecovery').mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    await click(button(root, 'Проверить результат'))
    await click(button(root, 'Восстановить операцию'))
    currentAuthority.value = {
      ...currentAuthority.value!,
      version: currentAuthority.value!.version + 1,
    }
    await flush()
    finish({
      userId: 3,
      linkedGroupCount: 9,
      attendanceMarksCount: 0,
      activeHeadmanGroupCount: 0,
      soleTeacherAssignmentCount: 0,
      requiresPassword: true,
      previewDigest: 'd'.repeat(64),
      expiresAt: '2099-01-01T00:00:00Z',
    })
    await flush()
    expect(storage.has(key)).toBe(false)
    expect(text(root)).not.toContain('Восстановить операцию')
    expect(userReconcile).not.toHaveBeenCalled()
  })
  it('hides recovery during transient authority loading and rehydrates the SAME scoped reference when authority is reconfirmed', async () => {
    const { root, currentAuthority, reference, client, key, storage } = await failedRecoveryScreen()
    const authority = currentAuthority.value
    currentAuthority.value = null
    await flush()
    expect(storage.has(key)).toBe(true)
    expect(text(root)).not.toContain('Операция остановлена')
    expect(client.failedIdentityOperation?.operationId).toBe(reference.operationId)
    currentAuthority.value = authority
    await flush()
    expect(client.failedIdentityOperation?.operationId).toBe(reference.operationId)
    expect(button(root, 'Проверить результат').props.disabled).toBe(false)
  })
  it.each(['authority', 'target'] as const)(
    'ignores late recovery completion after %s changes without ACK or freshness claims',
    async (change) => {
      const { root, client, currentAuthority, userReconcile } = await failedRecoveryScreen()
      const otherTarget = nodes(root).find((node) => node.props['data-user-id'] === 3)!
      let finish!: () => void
      userReconcile.mockImplementation(
        () =>
          new Promise((resolve) => {
            finish = resolve
          }),
      )
      const acknowledge = vi.spyOn(client, 'acknowledgeIdentityOperation')
      await click(button(root, 'Проверить результат'))
      await click(button(root, 'Восстановить операцию'))
      if (change === 'authority')
        currentAuthority.value = {
          ...currentAuthority.value!,
          version: currentAuthority.value!.version + 1,
        }
      else await click(otherTarget)
      await flush()
      finish()
      await flush()
      expect(acknowledge).not.toHaveBeenCalled()
      expect(text(root)).not.toContain('Операция восстановлена.')
      expect(
        nodes(root).find((node) => node.props['aria-label'] === 'Назад')?.props.disabled,
      ).not.toBe(true)
    },
  )
  it('uses empty-body AdminAccess recovery and then current profile reads, without resending permission changes', async () => {
    const { root, accessClient, confirm, accessReconcile, permissionWrite, userReconcile } =
      await failedRecoveryScreen('ADMIN_ACCESS')
    const read = vi.spyOn(accessClient, 'getUserAccess')
    await click(nodes(root).find((node) => node.props['data-user-id'] === 3)!)
    await click(button(root, 'Проверить результат'))
    await click(button(root, 'Восстановить операцию'))
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(accessReconcile).toHaveBeenCalledTimes(1)
    expect(accessReconcile.mock.calls[0]).toHaveLength(1)
    expect(read).toHaveBeenCalledWith(3, expect.any(AbortSignal))
    expect(permissionWrite).not.toHaveBeenCalled()
    expect(userReconcile).not.toHaveBeenCalled()
    expect(text(root)).toContain('Операция восстановлена.')
  })
})

describe('ordinary identity intent during transient authority loading', () => {
  it.each(['CREATE_UNKNOWN', 'ACCESS_PENDING'] as const)(
    'preserves %s UUID and original body when the same authority returns',
    async (kind) => {
      vi.useFakeTimers()
      const fixture = createAdminUsersReviewApis('permissions-mixed')
      const currentAuthority = shallowRef<AdminAccessProfile | null>(fixture.authority)
      const scope = 'same-actor-session-ADMIN'
      const writes: { key: string | null; body: string }[] = []
      const reads: string[] = []
      let complete = false
      const receipt = (status: string, retryAfterSeconds = 0) =>
        new Response(JSON.stringify({ operationId: writes[0]!.key, status, retryAfterSeconds }), {
          status: status === 'PENDING' ? 202 : 200,
          headers: {
            'X-Operation-Id': writes[0]!.key!,
            Location: `/academic/identity-operations/${writes[0]!.key}`,
          },
        })
      const fetcher: typeof fetch = async (input, init) => {
        const path = String(input)
        if (path.includes('/identity-operations/')) {
          reads.push(path)
          return receipt('SUCCEEDED')
        }
        writes.push({
          key: new Headers(init?.headers).get('Idempotency-Key'),
          body: String(init?.body),
        })
        if (!complete) {
          if (kind === 'CREATE_UNKNOWN') throw new TypeError('lost original CREATE response')
          return receipt('PENDING', 30)
        }
        return new Response(
          JSON.stringify(
            kind === 'ACCESS_PENDING'
              ? {
                  userId: 42,
                  assigned: true,
                  mode: 'LIMITED',
                  permissions: ['USERS_VIEW'],
                  version: 8,
                }
              : {
                  id: 42,
                  login: 'created.42',
                  lastName: 'Иванов',
                  firstName: 'Иван',
                  middleName: null,
                  fullName: 'Иванов Иван',
                  role: 'TEACHER',
                  status: 'ACTIVE',
                  groupId: null,
                  employeeNumber: 'Т-42',
                  telegramId: null,
                  createdAt: null,
                  roles: [],
                },
          ),
          { status: kind === 'ACCESS_PENDING' ? 200 : 201 },
        )
      }
      const usersClient =
        kind === 'CREATE_UNKNOWN'
          ? new AdminUsersClient({ accessToken: () => null, sessionScopeFor: () => scope, fetcher })
          : fixture.client
      const accessClient =
        kind === 'ACCESS_PENDING'
          ? new AdminAccessClient({
              accessToken: () => null,
              sessionScopeFor: () => scope,
              fetcher,
            })
          : fixture.accessClient
      const listUsers = fixture.client.listUsers.bind(fixture.client)
      vi.spyOn(usersClient, 'listUsers').mockImplementation(listUsers)
      const root = mount(() =>
        h(AdminUsersScreen, {
          client: usersClient,
          accessClient,
          authority: currentAuthority.value,
        }),
      )
      await flush()
      const activeClient = kind === 'CREATE_UNKNOWN' ? usersClient : accessClient
      const first =
        kind === 'CREATE_UNKNOWN'
          ? usersClient.createUser(
              { lastName: 'Иванов', firstName: 'Иван', role: 'TEACHER', employeeNumber: 'Т-42' },
              new AbortController().signal,
            )
          : accessClient.updateUserAccess(
              42,
              { expectedVersion: 7, mode: 'LIMITED', permissions: ['USERS_VIEW'] },
              new AbortController().signal,
            )
      await expect(first).rejects.toMatchObject({
        state: kind === 'CREATE_UNKNOWN' ? 'UNKNOWN' : 'PENDING',
      })
      const originalKey = writes[0]!.key
      expect(activeClient.hasPendingIdentityOperation).toBe(true)
      currentAuthority.value = null
      await flush()
      expect(activeClient.hasPendingIdentityOperation).toBe(true)
      expect(
        nodes(root).some(
          (node) => node.tag === 'button' && text(node).trim() === 'Проверить результат',
        ),
      ).toBe(false)
      currentAuthority.value = fixture.authority
      await flush()
      expect(activeClient.hasPendingIdentityOperation).toBe(true)
      expect(button(root, 'Проверить результат').props.disabled).toBe(false)
      expect(writes).toHaveLength(1)
      complete = true
      await vi.advanceTimersByTimeAsync(30_000)
      await activeClient.resumeIdentityOperation()
      expect(reads).toEqual([`/api/academic/identity-operations/${originalKey}`])
      expect(writes).toHaveLength(2)
      expect(writes[1]).toEqual(writes[0])
      activeClient.acknowledgeIdentityOperation()
      expect(activeClient.hasPendingIdentityOperation).toBe(false)
    },
  )
  it('persists an ordinary terminal FAILED_SAFE receipt that arrives during authority loading only after the same authority returns', async () => {
    const fixture = createAdminUsersReviewApis('permissions-mixed')
    const currentAuthority = shallowRef<AdminAccessProfile | null>(fixture.authority)
    const scope = 'same-actor-session-ADMIN'
    const storage = new Map<string, string>()
    vi.stubGlobal('sessionStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
    })
    let id = '',
      writes = 0
    let finish!: (response: Response) => void
    const receipt = (status: string) =>
      new Response(JSON.stringify({ operationId: id, status, retryAfterSeconds: 0 }), {
        status: status === 'PENDING' ? 202 : 200,
        headers: { 'X-Operation-Id': id },
      })
    const client = new AdminUsersClient({
      accessToken: () => null,
      sessionScopeFor: () => scope,
      fetcher: async (input, init) => {
        if (String(input).includes('/identity-operations/'))
          return new Promise((resolve) => {
            finish = resolve
          })
        writes++
        id = new Headers(init?.headers).get('Idempotency-Key')!
        return receipt('PENDING')
      },
    })
    vi.spyOn(client, 'listUsers').mockImplementation(fixture.client.listUsers.bind(fixture.client))
    const root = mount(() =>
      h(AdminUsersScreen, {
        client,
        accessClient: fixture.accessClient,
        authority: currentAuthority.value,
      }),
    )
    await flush()
    const pending = client.createUser(
      { lastName: 'Иванов', firstName: 'Иван', role: 'TEACHER', employeeNumber: 'Т-42' },
      new AbortController().signal,
    )
    await vi.waitFor(() => expect(finish).toBeTypeOf('function'))
    currentAuthority.value = null
    await flush()
    finish(receipt('FAILED_SAFE'))
    await expect(pending).rejects.toMatchObject({ state: 'FAILED_SAFE', operationId: id })
    expect(client.failedIdentityOperation?.operationId).toBe(id)
    expect(storage.size).toBe(0)
    expect(text(root)).not.toContain('Операция остановлена')
    currentAuthority.value = fixture.authority
    await flush()
    expect(storage.size).toBe(1)
    expect(JSON.parse([...storage.values()][0]!)).toEqual({
      operationId: id,
      kind: 'USER',
      userId: null,
      reconcileId: null,
      archivePreviewDigest: null,
    })
    expect(text(root)).toContain('Операция остановлена')
    expect(writes).toBe(1)
  })
})
