import { createRenderer, h, markRaw, nextTick, reactive, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminUsersScreen from './AdminUsersScreen.vue'
import RecoveryLinkPanel from './RecoveryLinkPanel.vue'
import { AdminUsersApiError } from './admin-users-client'
import { AdminAccessApiError, type AdminAccessProfile } from '../admin-access/admin-access-client'
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
  const root = mount(() => h(AdminUsersScreen, { client, accessClient, authority }))
  await flush()
  await click(nodes(root).find((node) => node.props['data-user-id'] === 3)!)
  await click(button(root, 'Редактировать данные'))
  await click(button(root, 'Настроить права администратора'))
  return { root, update }
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
