import { createRenderer, h, markRaw, nextTick, reactive, type App } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SecurityScreen from './SecurityScreen.vue'
import BrowserLoginApprovalForm from './BrowserLoginApprovalForm.vue'
import BrowserLoginScreen from './BrowserLoginScreen.vue'
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
  readyState = 2
  videoWidth = 640
  videoHeight = 480
  srcObject: unknown = null
  play(): Promise<void> {
    return Promise.resolve()
  }
  focus(): void {
    testDocument.activeElement = this
  }
}
class TestDocument extends EventTarget {
  visibilityState = 'visible'
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
  testDocument.visibilityState = 'visible'
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
  onChangePassword?: (() => Promise<void>) | undefined
  onRecover?: () => void
  disabled?: boolean
  busy?: boolean
  offline?: boolean
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
  it('gates submit and Enter by valid required fields and current source availability, keeping recovery independent', async () => {
    const changePassword = vi.fn(async () => {})
    const recover = vi.fn()
    const props = reactive({
      ownerKey: 'synthetic-account',
      error: null as ProfileRequestError | null,
      onChangePassword: changePassword as (() => Promise<void>) | undefined,
      onRecover: recover,
      disabled: false,
      busy: false,
      offline: false,
    })
    const root = mount(props)
    const inputs = () => nodes(root).filter((node) => node.tag === 'input')
    const submitButton = () => nodes(root).find((node) => node.props.type === 'submit')!
    const recovery = () =>
      nodes(root).find(
        (node) => node.props.class === 'profile-secondary-button profile-security__recovery',
      )!
    const blocked = async () => {
      expect(submitButton().props.disabled).toBe(true)
      await submit(root)
      expect(changePassword).not.toHaveBeenCalled()
    }
    expect(recovery().props.type).toBe('button')
    expect(nodes(root).indexOf(recovery())).toBeLessThan(nodes(root).indexOf(submitButton()))
    click(recovery())
    expect(recover).toHaveBeenCalledOnce()
    await blocked()
    await input(inputs()[1]!, 'Abcdefghij1!')
    await input(inputs()[2]!, 'Abcdefghij1!')
    await blocked() // current password is required in the existing settings flow
    await input(inputs()[0]!, 'current-password')
    for (const invalid of [
      'short1!',
      'abcdefghijkl',
      'abcdefghijk1',
      'Abcdefghij1!' + 'a'.repeat(200),
    ]) {
      await input(inputs()[1]!, invalid)
      await input(inputs()[2]!, invalid)
      await blocked()
    }
    await input(inputs()[1]!, 'Abcdefghij1!')
    await input(inputs()[2]!, 'different1!password')
    await blocked()
    await input(inputs()[2]!, 'Abcdefghij1!')
    expect(submitButton().props.disabled).toBe(false)
    for (const flag of ['disabled', 'busy', 'offline'] as const) {
      props[flag] = true
      await nextTick()
      await blocked()
      expect(inputs().map((node) => node.value)).toEqual([
        'current-password',
        'Abcdefghij1!',
        'Abcdefghij1!',
      ])
      props[flag] = false
      await nextTick()
    }
    props.onChangePassword = undefined
    await nextTick()
    await blocked()
    props.onChangePassword = changePassword
    await nextTick()
    expect(submitButton().props.disabled).toBe(false)
    await submit(root)
    expect(changePassword).toHaveBeenCalledWith({
      currentPassword: 'current-password',
      newPassword: 'Abcdefghij1!',
    })
    expect(submitButton().props.disabled).toBe(true)
  })

  it('updates each password rule from the unchanged policy as the new value changes', async () => {
    const root = mount({
      ownerKey: 'synthetic-account',
      error: null,
      onChangePassword: async () => {},
    })
    const rules = () => nodes(root).filter((node) => node.props.class === 'profile-password-policy')
    const states = () => rules().map((node) => node.props['data-valid'])
    expect(states()).toEqual([false, false, false])
    const field = nodes(root).filter((node) => node.tag === 'input')[1]!
    await input(field, 'abcdefghijkl')
    expect(states()).toEqual([true, false, false])
    await input(field, 'abcdefghijk1')
    expect(states()).toEqual([true, true, false])
    await input(field, 'abcdefghijk1!')
    expect(states()).toEqual([true, true, true])
    await input(field, '')
    expect(states()).toEqual([false, false, false])
  })
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
      expect(submitButton().props.disabled).toBe(true)
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
      expect(submitButton().props.disabled).toBe(true)
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
const scanForms = new WeakMap<TestElement, { inspectScan(value: string): Promise<void> }>()
function mountApproval(props: {
  port: BrowserLoginApprovalPort
  ownerKey?: string | null
}): TestElement {
  vi.stubGlobal('document', testDocument)
  vi.stubGlobal('Document', TestDocument)
  const root = new TestElement('root')
  const app = renderer.createApp({
    setup: () => () =>
      h(BrowserLoginApprovalForm, {
        scanOnly: true,
        ...props,
        ref: (value) => {
          if (value)
            scanForms.set(root, value as unknown as { inspectScan(value: string): Promise<void> })
        },
      }),
  })
  apps.push(app)
  app.mount(root)
  return root
}
async function fillProof(root: TestElement, value = JSON.stringify(browserProof)): Promise<void> {
  // The same exposed scan boundary as the camera; no visible input/demo UI.
  void scanForms.get(root)!.inspectScan(value)
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
  it('dismisses scan details by X without confirming or rejecting a login', async () => {
    const port = markRaw({
      isOnline: () => true,
      preview: vi.fn().mockResolvedValue(browserPreview),
      decide: vi.fn(),
    })
    const root = mountApproval({ port })
    await fillProof(root)
    await settleApproval()
    const dismiss = nodes(root).find(
      (node) => node.tag === 'button' && node.props['aria-label'] === 'Закрыть',
    )!
    click(dismiss)
    await settleApproval()
    expect(port.decide).not.toHaveBeenCalled()
    expect(
      nodes(root).some((node) => node.props['aria-label'] === 'Сведения о входе в браузере'),
    ).toBe(false)
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
    await settleApproval()
    expect(port.preview).toHaveBeenCalledOnce()
    expect(nodes(root).some((node) => node.tag === 'input')).toBe(false)
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
    await settleApproval()
    expect(port.preview).not.toHaveBeenCalled()
    await fillProof(root)
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
    props.port = next
    await nextTick()
    expect(nodes(root).some((node) => node.tag === 'input')).toBe(false)
    finish(browserPreview)
    await settleApproval()
    expect(nodes(root).some((node) => approvalText(node) === 'Подтвердить вход')).toBe(false)
    expect(old.decide).not.toHaveBeenCalled()
    expect(next.preview).not.toHaveBeenCalled()
  })
  it('invalidates a late preview when the owner changes on the same port', async () => {
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
    const props = reactive({ port, ownerKey: 'owner-one' })
    const root = mountApproval(props)
    await fillProof(root)
    props.ownerKey = 'owner-two'
    await nextTick()
    finish(browserPreview)
    await settleApproval()
    expect(approvalText(root)).not.toContain('Подтвердить вход')
    expect(port.decide).not.toHaveBeenCalled()
  })
  it('expires a pending preview at the server deadline and removes its action and RAM input', async () => {
    const port = markRaw({
      isOnline: () => true,
      preview: vi.fn().mockResolvedValue(browserPreview),
      decide: vi.fn(),
    })
    const root = mountApproval({ port })
    await fillProof(root)
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
    expect(nodes(root).some((node) => node.tag === 'input')).toBe(false)
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
    vi.setSystemTime(new Date(Date.parse(browserPreview.expiresAt) + 1))
    finish(browserPreview)
    await settleApproval()
    expect(nodes(root).some((node) => approvalText(node) === 'Подтвердить вход')).toBe(false)
    expect(nodes(root).some((node) => node.tag === 'input')).toBe(false)
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
    await settleApproval()
    expect(vi.getTimerCount()).toBe(1)
    props.port = second
    await nextTick()
    expect(vi.getTimerCount()).toBe(0)
    await fillProof(root)
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
      expect(nodes(root).some((node) => node.tag === 'input')).toBe(false)
      expect(vi.getTimerCount()).toBe(0)
      expect(port.preview).toHaveBeenCalledOnce()
    },
  )
})

describe('D024 scanner-only browser login screen', () => {
  it('offers browser fallback only after a native scanner failure and another user click', async () => {
    const { provideDeviceAccess } = await import('../../device-access/device-access')
    vi.useFakeTimers()
    let failed!: (error: unknown) => void
    const scanQr = vi.fn((_scanned: (value: string) => void, _closed: () => void, onFailed: (error: unknown) => void) => { failed = onFailed; return () => {} })
    const acquire = vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] })
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia: acquire } })
    vi.stubGlobal('BarcodeDetector', class { async detect() { return [] } })
    vi.stubGlobal('window', new EventTarget())
    vi.stubGlobal('document', testDocument)
    const port = markRaw({ isOnline: () => true, preview: vi.fn(), decide: vi.fn() })
    const root = new TestElement('root')
    const app = renderer.createApp({ setup() {
      const owner = provideDeviceAccess({ geo: { source: 'TMA', watch: () => () => {} }, scanQr })
      owner.bindOwner('synthetic-owner', 'synthetic-session')
      return () => h(BrowserLoginScreen, { port, offline: false, theme: 'light', ownerKey: 'synthetic-owner' })
    } })
    apps.push(app); app.mount(root)
    click(approvalButton(root, 'Сканировать QR'))
    expect(scanQr).toHaveBeenCalledOnce()
    expect(acquire).not.toHaveBeenCalled()
    failed(new DOMException('native unavailable', 'TimeoutError'))
    await settleApproval()
    expect(acquire).not.toHaveBeenCalled()
    click(approvalButton(root, 'Сканировать камерой браузера'))
    for (let count = 0; count < 8; count++) await settleApproval()
    expect(acquire).toHaveBeenCalledOnce()
    expect(scanQr).toHaveBeenCalledOnce()
    expect(port.preview).not.toHaveBeenCalled()
  })
  it('keeps its own pending permission request through browser blur and releases the resulting video on pagehide', async () => {
    vi.useFakeTimers()
    let finish!: (stream: MediaStream) => void
    const track = { stop: vi.fn() }
    const acquire = vi.fn(
      () =>
        new Promise<MediaStream>((resolve) => {
          finish = resolve
        }),
    )
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia: acquire } })
    vi.stubGlobal(
      'BarcodeDetector',
      class {
        async detect() {
          return []
        }
      },
    )
    const windowEvents = new EventTarget()
    vi.stubGlobal('window', windowEvents)
    vi.stubGlobal('document', testDocument)
    const port = markRaw({ isOnline: () => true, preview: vi.fn(), decide: vi.fn() })
    const root = new TestElement('root')
    const app = renderer.createApp({
      setup: () => () =>
        h(BrowserLoginScreen, {
          port,
          offline: false,
          theme: 'light',
          ownerKey: 'synthetic-owner',
        }),
    })
    apps.push(app)
    app.mount(root)
    click(approvalButton(root, 'Сканировать QR'))
    expect(acquire).toHaveBeenCalledExactlyOnceWith({
      video: { facingMode: { ideal: 'environment' } },
      audio: false,
    })
    windowEvents.dispatchEvent(new Event('blur'))
    finish({ getTracks: () => [track] } as unknown as MediaStream)
    for (let count = 0; count < 8; count++) await settleApproval()
    expect(track.stop).not.toHaveBeenCalled()
    windowEvents.dispatchEvent(new Event('pagehide'))
    await settleApproval()
    expect(track.stop).toHaveBeenCalledOnce()
    expect(port.preview).not.toHaveBeenCalled()
    expect(port.decide).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('scans through the strict parser and waits for explicit rejection without manual or demo fields', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(browserPreview.issuedAt))
    const track = { stop: vi.fn() }
    const acquire = vi.fn().mockResolvedValue({ getTracks: () => [track] })
    const detect = vi.fn().mockResolvedValue([{ rawValue: JSON.stringify(browserProof) }])
    vi.stubGlobal(
      'BarcodeDetector',
      class {
        detect = detect
      },
    )
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia: acquire } })
    vi.stubGlobal('window', new EventTarget())
    const port = markRaw({
      isOnline: () => true,
      preview: vi.fn().mockResolvedValue(browserPreview),
      decide: vi.fn().mockResolvedValue({
        challengeId: browserProof.challengeId,
        status: 'rejected',
        expiresAt: browserPreview.expiresAt,
      }),
    })
    vi.stubGlobal('document', testDocument)
    vi.stubGlobal('Document', TestDocument)
    const root = new TestElement('root')
    const app = renderer.createApp({
      setup: () => () =>
        h(BrowserLoginScreen, {
          port,
          offline: false,
          theme: 'light',
          ownerKey: 'synthetic-account',
        }),
    })
    apps.push(app)
    app.mount(root)
    expect(acquire).not.toHaveBeenCalled()
    expect(nodes(root).some((node) => node.tag === 'input' || node.tag === 'form')).toBe(false)
    click(approvalButton(root, 'Сканировать QR'))
    for (let count = 0; count < 8; count++) await settleApproval()
    expect(port.preview).toHaveBeenCalledWith({
      purpose: 'LOGIN',
      challengeId: browserProof.challengeId,
      approvalToken: browserProof.approvalToken,
    })
    expect(port.decide).not.toHaveBeenCalled()
    expect(track.stop).toHaveBeenCalledOnce()
    expect(approvalText(root)).toContain(browserPreview.browserLabel)
    expect(testDocument.activeElement?.props['aria-label']).toBe('Сведения о входе в браузере')
    click(approvalButton(root, 'Отклонить вход'))
    await settleApproval()
    expect(port.decide).toHaveBeenCalledOnce()
    expect(port.decide.mock.calls[0]?.[1]).toBe('REJECT')
    expect(approvalText(root)).toContain('Вход в браузере отклонён')
    expect(vi.getTimerCount()).toBe(0)
  })
  it.each(['owner', 'offline', 'blur', 'pagehide', 'visibility', 'back', 'unmount'] as const)(
    'cleans camera and prevents resume after %s',
    async (reason) => {
      vi.useFakeTimers()
      const track = { stop: vi.fn() }
      const acquire = vi.fn().mockResolvedValue({ getTracks: () => [track] })
      vi.stubGlobal(
        'BarcodeDetector',
        class {
          async detect() {
            return []
          }
        },
      )
      vi.stubGlobal('navigator', { mediaDevices: { getUserMedia: acquire } })
      const windowEvents = new EventTarget()
      vi.stubGlobal('window', windowEvents)
      vi.stubGlobal('document', testDocument)
      const port = markRaw({ isOnline: () => true, preview: vi.fn(), decide: vi.fn() })
      const props = reactive({
        port,
        offline: false,
        theme: 'light' as const,
        ownerKey: 'owner-one',
      })
      const root = new TestElement('root')
      const app = renderer.createApp({ setup: () => () => h(BrowserLoginScreen, props) })
      apps.push(app)
      app.mount(root)
      click(approvalButton(root, 'Сканировать QR'))
      for (let count = 0; count < 5; count++) await settleApproval()
      expect(acquire).toHaveBeenCalledOnce()
      if (reason === 'owner') props.ownerKey = 'owner-two'
      else if (reason === 'offline') props.offline = true
      else if (reason === 'visibility') {
        testDocument.visibilityState = 'hidden'
        testDocument.dispatchEvent(new Event('visibilitychange'))
      } else if (reason === 'back')
        click(nodes(root).find((node) => node.props['aria-label'] === 'Назад')!)
      else if (reason === 'unmount') {
        apps.pop()!.unmount()
      } else windowEvents.dispatchEvent(new Event(reason))
      await settleApproval()
      expect(track.stop).toHaveBeenCalledOnce()
      expect(vi.getTimerCount()).toBe(0)
      expect(port.preview).not.toHaveBeenCalled()
      testDocument.visibilityState = 'visible'
      windowEvents.dispatchEvent(new Event('focus'))
      testDocument.dispatchEvent(new Event('visibilitychange'))
      await settleApproval()
      expect(acquire).toHaveBeenCalledOnce()
    },
  )
})
