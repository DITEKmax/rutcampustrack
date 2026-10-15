import { createRenderer, h, markRaw, nextTick, reactive, type App } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SecurityScreen from './SecurityScreen.vue'
import BrowserLoginApprovalForm from './BrowserLoginApprovalForm.vue'
import type { BrowserLoginApprovalPort, BrowserLoginPreview } from './browser-login-approval'
import { ProfileRequestError } from './profile-types'

// The established custom Vue host executes the real SFC and v-model without a browser dependency.
class TestElement extends EventTarget {
  children: TestElement[] = []
  parentElement: TestElement | null = null
  props: Record<string, unknown> = {}
  value = ''
  text = ''
  composing = false
  constructor(readonly tag: string) {
    super()
    markRaw(this)
  }
  get tagName(): string {
    return this.tag.toUpperCase()
  }
  get type(): string {
    return String(this.props.type ?? '')
  }
  getRootNode(): typeof testDocument {
    return testDocument
  }
  focus(): void {
    testDocument.activeElement = this
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
  for (const app of apps.splice(0)) app.unmount()
  vi.unstubAllGlobals()
  vi.useRealTimers()
})
function nodes(root: TestElement): TestElement[] {
  return [root, ...root.children.flatMap(nodes)]
}
function find(root: TestElement, tag: string): TestElement {
  const value = nodes(root).find((node) => node.tag === tag)
  if (!value) throw new Error('Missing ' + tag)
  return value
}
function click(node: TestElement): void {
  ;(node.props.onClick as () => void)()
}
async function input(node: TestElement, value: string): Promise<void> {
  node.value = value
  node.dispatchEvent(new Event('input'))
  await nextTick()
}
function mount(props: {
  ownerKey: string
  error: ProfileRequestError | null
  onChangePassword: () => Promise<void>
}): TestElement {
  vi.stubGlobal('document', testDocument)
  vi.stubGlobal('Document', TestDocument)
  const root = new TestElement('root')
  const app = renderer.createApp({ setup: () => () => h(SecurityScreen, props) })
  apps.push(app)
  app.mount(root)
  return root
}
async function fill(root: TestElement): Promise<void> {
  const inputs = nodes(root).filter((node) => node.tag === 'input')
  await input(inputs[0]!, 'current-password')
  await input(inputs[1]!, 'Abcdefghij1!')
  await input(inputs[2]!, 'Abcdefghij1!')
}
async function submit(root: TestElement): Promise<void> {
  ;(find(root, 'form').props.onSubmit as (event: Event) => void)(
    new Event('submit', { cancelable: true }),
  )
  await nextTick()
}

describe('SecurityScreen sensitive owner lifecycle', () => {
  it.each(['never', 'reject', 'resolve'] as const)(
    'releases a new owner immediately and isolates the old %s settlement',
    async (oldOutcome) => {
      let rejectOld!: (error: unknown) => void
      let resolveOld!: () => void
      let resolveNew!: () => void
      const oldRequest = new Promise<void>((resolve, reject) => {
        resolveOld = resolve
        rejectOld = reject
      })
      const newRequest = new Promise<void>((resolve) => {
        resolveNew = resolve
      })
      const changePassword = vi.fn().mockReturnValueOnce(oldRequest).mockReturnValueOnce(newRequest)
      const props = reactive({
        ownerKey: 'account-A',
        error: null as ProfileRequestError | null,
        onChangePassword: changePassword,
      })
      const root = mount(props)
      const submitButton = () =>
        nodes(root).find((node) => node.tag === 'button' && node.props.type === 'submit')!
      await fill(root)
      click(nodes(root).find((node) => node.props['aria-label'] === 'Показать текущий пароль')!)
      await nextTick()
      expect(nodes(root).filter((node) => node.tag === 'input')[0]!.props.type).toBe('text')
      await submit(root)
      expect(submitButton().props.disabled).toBe(true)
      props.ownerKey = 'account-B'
      await nextTick()
      expect(
        nodes(root)
          .filter((node) => node.tag === 'input')
          .map((node) => node.value),
      ).toEqual(['', '', ''])
      expect(
        nodes(root)
          .filter((node) => node.tag === 'input')
          .map((node) => node.props.type),
      ).toEqual(['password', 'password', 'password'])
      expect(submitButton().props.disabled).toBe(false)
      await fill(root)
      await submit(root)
      expect(changePassword).toHaveBeenCalledTimes(2)
      expect(submitButton().props.disabled).toBe(true)
      if (oldOutcome === 'reject')
        rejectOld(new ProfileRequestError('CURRENT_PASSWORD_INVALID', 'Old account failure'))
      if (oldOutcome === 'resolve') resolveOld()
      await Promise.resolve()
      await nextTick()
      expect(submitButton().props.disabled).toBe(true)
      expect(
        nodes(root)
          .filter((node) => node.tag === 'input')
          .map((node) => node.value),
      ).toEqual(['current-password', 'Abcdefghij1!', 'Abcdefghij1!'])
      expect(
        nodes(root)
          .map((node) => node.text)
          .join(' '),
      ).not.toContain('Текущий пароль неверен')
      resolveNew()
      await Promise.resolve()
      await nextTick()
      expect(submitButton().props.disabled).toBe(false)
      expect(
        nodes(root)
          .filter((node) => node.tag === 'input')
          .map((node) => node.value),
      ).toEqual(['', '', ''])
    },
  )
  it('clears every sensitive field when a session is revoked', async () => {
    const props = reactive({
      ownerKey: 'account-A',
      error: null as ProfileRequestError | null,
      onChangePassword: async () => {},
    })
    const root = mount(props)
    await fill(root)
    props.error = new ProfileRequestError('SESSION_REVOKED', 'Сессия завершена')
    await nextTick()
    expect(
      nodes(root)
        .filter((node) => node.tag === 'input')
        .map((node) => node.value),
    ).toEqual(['', '', ''])
    expect(
      nodes(root)
        .filter((node) => node.tag === 'input')
        .every((node) => node.props.disabled),
    ).toBe(true)
  })
})

const browserProof = {
  version: 1,
  purpose: 'LOGIN',
  challengeId: '00000000-0000-4000-8000-000000000042',
  approvalToken: 'a'.repeat(43),
}
const browserPreview: BrowserLoginPreview = {
  challengeId: browserProof.challengeId,
  purpose: 'LOGIN',
  status: 'pending',
  browserLabel: '<synthetic browser>',
  issuedAt: '2026-10-15T00:00:00Z',
  expiresAt: '2026-10-15T00:02:00Z',
  warning: 'Подтверждай только свой вход',
}
function mountApproval(props: { port: BrowserLoginApprovalPort }): TestElement {
  vi.stubGlobal('document', testDocument)
  vi.stubGlobal('Document', TestDocument)
  const root = new TestElement('root')
  const app = renderer.createApp({ setup: () => () => h(BrowserLoginApprovalForm, props) })
  apps.push(app)
  app.mount(root)
  return root
}
async function fillProof(root: TestElement, value = JSON.stringify(browserProof)): Promise<void> {
  const field = find(root, 'input')
  await input(field, value)
  ;(field.props.onInput as (() => void) | undefined)?.()
  await nextTick()
}
function approvalText(node: TestElement): string {
  return (node.text + node.children.map(approvalText).join('')).trim()
}
function approvalButton(root: TestElement, text: string): TestElement {
  const node = nodes(root).find((item) => item.tag === 'button' && approvalText(item) === text)
  if (!node) throw new Error('Missing synthetic approval action')
  return node
}
async function settleApproval(): Promise<void> {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}
describe('D024 incoming browser approval form', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(browserPreview.issuedAt))
  })
  it('performs no background action and requires real preview followed by an explicit single decision', async () => {
    let finish!: (value: { challengeId: string; status: 'confirmed'; expiresAt: string }) => void
    const port = markRaw({
      isOnline: () => true,
      preview: vi.fn().mockResolvedValue(browserPreview),
      decide: vi.fn().mockReturnValue(
        new Promise((resolve) => {
          finish = resolve
        }),
      ),
    })
    const root = mountApproval({ port })
    expect(port.preview).not.toHaveBeenCalled()
    await fillProof(root)
    expect(port.decide).not.toHaveBeenCalled()
    await submit(root)
    await settleApproval()
    expect(port.preview).toHaveBeenCalledOnce()
    expect(find(root, 'input').value).toBe('')
    expect(port.decide).not.toHaveBeenCalled()
    const confirm = approvalButton(root, 'Подтвердить вход')
    click(confirm)
    click(confirm)
    expect(port.decide).toHaveBeenCalledOnce()
    expect(port.decide.mock.calls[0]?.[1]).toBe('CONFIRM')
    finish({
      challengeId: browserProof.challengeId,
      status: 'confirmed',
      expiresAt: browserPreview.expiresAt,
    })
    await settleApproval()
    expect(nodes(root).some((node) => approvalText(node) === 'Вход в браузере подтверждён')).toBe(
      true,
    )
    expect(nodes(root).some((node) => approvalText(node) === 'Подтвердить вход')).toBe(false)
  })
  it('rejects malformed purposes locally and never turns an ambiguous decision into success', async () => {
    const port = markRaw({
      isOnline: () => true,
      preview: vi.fn().mockResolvedValue(browserPreview),
      decide: vi.fn().mockRejectedValue(new Error('synthetic unavailable')),
    })
    const root = mountApproval({ port })
    await fillProof(root, JSON.stringify({ ...browserProof, purpose: 'DEVICE_LINK' }))
    await submit(root)
    await settleApproval()
    expect(port.preview).not.toHaveBeenCalled()
    await fillProof(root)
    await submit(root)
    await settleApproval()
    click(approvalButton(root, 'Отклонить вход'))
    await settleApproval()
    expect(
      nodes(root).some(
        (node) => approvalText(node) === 'Результат не подтверждён. Проверь вход заново',
      ),
    ).toBe(true)
    expect(nodes(root).some((node) => approvalText(node) === 'Вход в браузере отклонён')).toBe(
      false,
    )
    expect(nodes(root).some((node) => approvalText(node) === 'Подтвердить вход')).toBe(false)
  })
  it('clears proof and ignores a late preview when the owner port changes', async () => {
    let finish!: (value: BrowserLoginPreview) => void
    const old = markRaw({
      isOnline: () => true,
      preview: vi.fn().mockReturnValue(
        new Promise((resolve) => {
          finish = resolve
        }),
      ),
      decide: vi.fn(),
    })
    const next = markRaw({ isOnline: () => true, preview: vi.fn(), decide: vi.fn() })
    const props = reactive({ port: old as BrowserLoginApprovalPort })
    const root = mountApproval(props)
    await fillProof(root)
    await submit(root)
    props.port = next
    await nextTick()
    expect(find(root, 'input').value).toBe('')
    finish(browserPreview)
    await settleApproval()
    expect(nodes(root).some((node) => approvalText(node) === 'Подтвердить вход')).toBe(false)
    expect(old.decide).not.toHaveBeenCalled()
    expect(next.preview).not.toHaveBeenCalled()
  })
  it('expires a pending preview at the server deadline and removes its action and RAM input', async () => {
    const port = markRaw({
      isOnline: () => true,
      preview: vi.fn().mockResolvedValue(browserPreview),
      decide: vi.fn(),
    })
    const root = mountApproval({ port })
    await fillProof(root)
    await submit(root)
    await settleApproval()
    expect(approvalButton(root, 'Подтвердить вход')).toBeDefined()
    expect(vi.getTimerCount()).toBe(1)
    vi.advanceTimersByTime(120000)
    await nextTick()
    expect(nodes(root).some((node) => approvalText(node) === 'Подтвердить вход')).toBe(false)
    expect(
      nodes(root).some(
        (node) => approvalText(node) === 'Срок подтверждения истёк. Проверь вход заново',
      ),
    ).toBe(true)
    expect(find(root, 'input').value).toBe('')
    expect(port.decide).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('discards a pending preview that arrives after its provided expiry', async () => {
    let finish!: (value: BrowserLoginPreview) => void
    const port = markRaw({
      isOnline: () => true,
      preview: vi.fn().mockReturnValue(
        new Promise((resolve) => {
          finish = resolve
        }),
      ),
      decide: vi.fn(),
    })
    const root = mountApproval({ port })
    await fillProof(root)
    await submit(root)
    vi.setSystemTime(new Date(Date.parse(browserPreview.expiresAt) + 1))
    finish(browserPreview)
    await settleApproval()
    expect(nodes(root).some((node) => approvalText(node) === 'Подтвердить вход')).toBe(false)
    expect(find(root, 'input').value).toBe('')
    expect(port.decide).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('checks the absolute deadline before decision even when its timer has not resumed', async () => {
    const port = markRaw({
      isOnline: () => true,
      preview: vi.fn().mockResolvedValue(browserPreview),
      decide: vi.fn(),
    })
    const root = mountApproval({ port })
    await fillProof(root)
    await submit(root)
    await settleApproval()
    const confirm = approvalButton(root, 'Подтвердить вход')
    vi.setSystemTime(new Date(browserPreview.expiresAt))
    click(confirm)
    await nextTick()
    expect(port.decide).not.toHaveBeenCalled()
    expect(nodes(root).some((node) => approvalText(node) === 'Подтвердить вход')).toBe(false)
    expect(vi.getTimerCount()).toBe(0)
  })
  it('cleans the deadline when the owner changes and when the form unmounts', async () => {
    const first = markRaw({
      isOnline: () => true,
      preview: vi.fn().mockResolvedValue(browserPreview),
      decide: vi.fn(),
    })
    const second = markRaw({
      isOnline: () => true,
      preview: vi.fn().mockResolvedValue(browserPreview),
      decide: vi.fn(),
    })
    const props = reactive({ port: first as BrowserLoginApprovalPort })
    const root = mountApproval(props)
    await fillProof(root)
    await submit(root)
    await settleApproval()
    expect(vi.getTimerCount()).toBe(1)
    props.port = second
    await nextTick()
    expect(vi.getTimerCount()).toBe(0)
    await fillProof(root)
    await submit(root)
    await settleApproval()
    expect(vi.getTimerCount()).toBe(1)
    apps.pop()!.unmount()
    expect(vi.getTimerCount()).toBe(0)
    expect(first.decide).not.toHaveBeenCalled()
    expect(second.decide).not.toHaveBeenCalled()
  })
  it.each(['confirmed', 'rejected', 'error'] as const)(
    'discards late %s decision settlement when timers have not resumed',
    async (outcome) => {
      type Decision = { challengeId: string; status: 'confirmed' | 'rejected'; expiresAt: string }
      let finish!: (value: Decision) => void
      let fail!: (error: Error) => void
      const pending = new Promise<Decision>((resolve, reject) => {
        finish = resolve
        fail = reject
      })
      const port = markRaw({
        isOnline: () => true,
        preview: vi.fn().mockResolvedValue(browserPreview),
        decide: vi.fn().mockReturnValue(pending),
      })
      const root = mountApproval({ port })
      await fillProof(root)
      await submit(root)
      await settleApproval()
      click(approvalButton(root, 'Подтвердить вход'))
      expect(port.decide).toHaveBeenCalledOnce()
      vi.setSystemTime(new Date(browserPreview.expiresAt))
      if (outcome === 'error') fail(new Error('synthetic late decision error'))
      else
        finish({
          challengeId: browserProof.challengeId,
          status: outcome,
          expiresAt: browserPreview.expiresAt,
        })
      await settleApproval()
      expect(
        nodes(root).some(
          (node) => approvalText(node) === 'Срок подтверждения истёк. Проверь вход заново',
        ),
      ).toBe(true)
      expect(
        nodes(root).some((node) =>
          ['Вход в браузере подтверждён', 'Вход в браузере отклонён', 'Подтвердить вход'].includes(
            approvalText(node),
          ),
        ),
      ).toBe(false)
      expect(find(root, 'input').value).toBe('')
      expect(vi.getTimerCount()).toBe(0)
      expect(port.preview).toHaveBeenCalledOnce()
    },
  )
})
