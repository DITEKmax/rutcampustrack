import { afterEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, h, markRaw, nextTick, type App } from 'vue'
import RoleSwitchDialog from './RoleSwitchDialog.vue'
import AutoGrowTextarea from '../../features/requests/AutoGrowTextarea.vue'
import { DEFAULT_PASSWORD_POLICY, type ProfileSnapshot } from '../../features/profile/profile-types'

let wrapOnPageScrollbar = false

class TestElement {
  children: TestElement[] = []
  parentElement: TestElement | null = null
  props: Record<string, unknown> = {}
  style = { height: '' }
  text = ''
  value = ''
  clientWidth = 350
  contentHeight = 188
  constructor(readonly tag: string) { markRaw(this) }
  get tagName(): string { return this.tag.toUpperCase() }
  get nodeName(): string { return this.tagName }
  get childNodes(): TestElement[] { return this.children }
  get clientHeight(): number { return Math.max(96, parseFloat(this.style.height) || 0) }
  get scrollHeight(): number {
    const extraLine = wrapOnPageScrollbar && this.tag === 'textarea' && this.clientHeight >= 188 ? 24 : 0
    return Math.max(this.contentHeight + extraLine, this.clientHeight)
  }
  getAttributeNames(): string[] { return Object.keys(this.props) }
  getAttribute(name: string): string | null { return this.props[name] === undefined ? null : String(this.props[name]) }
  focus(): void { testDocument.activeElement = this }
  contains(target: unknown): boolean { return this === target || this.children.some((child) => child.contains(target)) }
  querySelectorAll(): TestElement[] {
    return this.children.flatMap((child) => [
      ...(child.tag === 'button' && !child.props.disabled ? [child] : []), ...child.querySelectorAll(),
    ])
  }
}
class TestDocument extends EventTarget {
  activeElement: TestElement | null = null
  body = new TestElement('body')
  fonts = Object.assign(new EventTarget(), { ready: Promise.resolve() })
  listeners = new Map<string, Set<EventListenerOrEventListenerObject>>()
  override addEventListener(name: string, listener: EventListenerOrEventListenerObject | null, options?: boolean | AddEventListenerOptions): void {
    if (listener) {
      const callbacks = this.listeners.get(name) ?? new Set()
      callbacks.add(listener)
      this.listeners.set(name, callbacks)
    }
    super.addEventListener(name, listener, options)
  }
  override removeEventListener(name: string, listener: EventListenerOrEventListenerObject | null, options?: boolean | EventListenerOptions): void {
    if (listener) this.listeners.get(name)?.delete(listener)
    super.removeEventListener(name, listener, options)
  }
}
let testDocument: TestDocument
let observed: TestElement | null = null
let notifyResize: (() => void) | null = null
let disconnected = false
let frameId = 0
const frames = new Map<number, FrameRequestCallback>()
const apps: App[] = []
const renderer = createRenderer<TestElement, TestElement>({
  createElement: (tag) => new TestElement(tag),
  createText: (text) => Object.assign(new TestElement('#text'), { text }),
  createComment: (text) => Object.assign(new TestElement('#comment'), { text }),
  setText: (node, text) => { node.text = text },
  setElementText: (node, text) => { node.text = text; node.children = [] },
  parentNode: (node) => node.parentElement,
  nextSibling: (node) => node.parentElement?.children[node.parentElement.children.indexOf(node) + 1] ?? null,
  querySelector: () => testDocument.body,
  patchProp: (node, key, _old, value) => {
    node.props[key] = value
    if (key === 'value') node.value = String(value)
  },
  remove: (node) => {
    if (node.parentElement) node.parentElement.children = node.parentElement.children.filter((child) => child !== node)
    node.parentElement = null
  },
  insert: (node, parent, anchor) => {
    if (node.parentElement) node.parentElement.children = node.parentElement.children.filter((child) => child !== node)
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
    node.parentElement = parent
  },
})
function setup(): TestElement {
  testDocument = new TestDocument()
  frames.clear()
  wrapOnPageScrollbar = false
  observed = null
  notifyResize = null
  disconnected = false
  vi.stubGlobal('document', testDocument)
  vi.stubGlobal('HTMLElement', TestElement)
  vi.stubGlobal('window', new EventTarget())
  vi.stubGlobal('getComputedStyle', () => ({ minHeight: '96px', borderTopWidth: '0px', borderBottomWidth: '0px' }))
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => { frames.set(++frameId, callback); return frameId })
  vi.stubGlobal('cancelAnimationFrame', (id: number) => { frames.delete(id) })
  vi.stubGlobal('ResizeObserver', class {
    constructor(callback: () => void) { notifyResize = callback }
    observe(element: TestElement): void { observed = element }
    disconnect(): void { disconnected = true }
  })
  vi.stubGlobal('MutationObserver', class { observe(): void {} disconnect(): void {} })
  return new TestElement('root')
}
async function flushFrame(): Promise<void> {
  await nextTick()
  const scheduled = [...frames.values()]
  frames.clear()
  for (const callback of scheduled) callback(0)
  await nextTick()
}
function key(key: string, shiftKey = false): Event {
  return Object.assign(new Event('keydown', { cancelable: true }), { key, shiftKey })
}
function roleSnapshot(): ProfileSnapshot {
  return {
    sessionId: 'session', userId: 'student', displayName: 'Student', sessionVersion: 'v1', rolesVersion: 'v1',
    activeRole: 'STUDENT', readOnly: false, passwordPolicy: DEFAULT_PASSWORD_POLICY,
    roles: [
      { grantId: 'student', role: 'STUDENT', status: 'ACTIVE', selectable: true, readOnly: false },
      { grantId: 'headman', role: 'HEADMAN', status: 'ACTIVE', selectable: true, readOnly: false },
    ],
  }
}
afterEach(() => {
  for (const app of apps.splice(0)) app.unmount()
  vi.unstubAllGlobals()
})

describe('role overlay lifecycle', () => {
  it('does not install listeners or move focus when unmounted before the mounted nextTick', async () => {
    const root = setup()
    const previous = new TestElement('button')
    testDocument.activeElement = previous
    const app = renderer.createApp(RoleSwitchDialog, { snapshot: roleSnapshot(), onSelectRole: vi.fn() })
    app.mount(root)
    app.unmount()
    await nextTick()
    expect(testDocument.listeners.get('keydown')?.size ?? 0).toBe(0)
    expect(testDocument.listeners.get('focusin')?.size ?? 0).toBe(0)
    expect(testDocument.activeElement).toBe(previous)
    const tab = key('Tab')
    testDocument.dispatchEvent(tab)
    expect(tab.defaultPrevented).toBe(false)
  })
  it('retains focus wrapping, Escape and focus restoration during a normal mount', async () => {
    const root = setup()
    const previous = new TestElement('button')
    testDocument.activeElement = previous
    const close = vi.fn()
    const app = renderer.createApp(RoleSwitchDialog, { snapshot: roleSnapshot(), onSelectRole: vi.fn(), onClose: close })
    apps.push(app)
    app.mount(root)
    await nextTick()
    const buttons = testDocument.body.querySelectorAll()
    expect(buttons).toHaveLength(2)
    expect(testDocument.activeElement).toBe(buttons[0])
    testDocument.dispatchEvent(key('Tab', true))
    expect(testDocument.activeElement).toBe(buttons[1])
    testDocument.dispatchEvent(key('Tab'))
    expect(testDocument.activeElement).toBe(buttons[0])
    testDocument.dispatchEvent(key('Escape'))
    expect(close).toHaveBeenCalledOnce()
    app.unmount()
    apps.pop()
    expect(testDocument.activeElement).toBe(previous)
    expect(testDocument.listeners.get('keydown')?.size).toBe(0)
    expect(testDocument.listeners.get('focusin')?.size).toBe(0)
  })
})

describe('comment wrapping measurement', () => {
  it('fits final wrapping when restoring height restores the outer page scrollbar', async () => {
    const root = setup()
    wrapOnPageScrollbar = true
    const app = renderer.createApp(AutoGrowTextarea, { modelValue: 'Retained comment' })
    apps.push(app)
    app.mount(root)
    const control = root.children.find((child) => child.tag === 'textarea')!
    // height:auto measured 188; restoring it caused one extra 24px line.
    expect(control.style.height).toBe('212px')
    expect(control.clientHeight).toBeGreaterThanOrEqual(control.scrollHeight)
    await flushFrame()
    expect(control.style.height).toBe('212px')
  })
  it('remeasures the textarea width after page scrollbars, font completion and deletion', async () => {
    const root = setup()
    const app = renderer.createApp({ render: () => h(AutoGrowTextarea, { modelValue: 'Retained comment' }) })
    apps.push(app)
    app.mount(root)
    const control = root.children.find((child) => child.tag === 'textarea')!
    expect(control.style.height).toBe('188px')
    expect(observed).toBe(control)
    // A page scrollbar narrows the actual control after initial growth.
    control.clientWidth = 335
    control.contentHeight = 212
    notifyResize?.()
    // A hidden browser may not run animation frames: nextTick must suffice.
    await nextTick()
    expect(control.style.height).toBe('212px')
    await flushFrame()
    expect(control.clientHeight).toBeGreaterThanOrEqual(control.scrollHeight)
    expect(control.style.height).toBe('212px')
    // Font completion can wrap more lines while width stays unchanged.
    control.contentHeight = 236
    testDocument.fonts.dispatchEvent(new Event('loadingdone'))
    await flushFrame()
    expect(control.style.height).toBe('236px')
    control.contentHeight = 44
    const input = control.props.onInput as (event: { target: TestElement }) => void
    control.value = ''
    input({ target: control })
    await flushFrame()
    expect(control.style.height).toBe('96px')
    app.unmount()
    apps.pop()
    expect(disconnected).toBe(true)
    const height = control.style.height
    notifyResize?.()
    testDocument.fonts.dispatchEvent(new Event('loadingdone'))
    await flushFrame()
    expect(frames.size).toBe(0)
    expect(control.style.height).toBe(height)
  })
})
