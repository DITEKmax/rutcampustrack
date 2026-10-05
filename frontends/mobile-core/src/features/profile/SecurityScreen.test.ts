import { createRenderer, h, markRaw, nextTick, reactive, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SecurityScreen from './SecurityScreen.vue'
import { ProfileRequestError } from './profile-types'

// The established custom Vue host executes the real SFC and v-model without a browser dependency.
class TestElement extends EventTarget {
  children: TestElement[] = []
  parentElement: TestElement | null = null
  props: Record<string, unknown> = {}
  value = ''
  text = ''
  composing = false
  constructor(readonly tag: string) { super(); markRaw(this) }
  get tagName(): string { return this.tag.toUpperCase() }
  get type(): string { return String(this.props.type ?? '') }
  getRootNode(): typeof testDocument { return testDocument }
  focus(): void { testDocument.activeElement = this }
}
class TestDocument { activeElement: TestElement | null = null }
const testDocument = new TestDocument()
const renderer = createRenderer<TestElement, TestElement>({
  createElement: (tag) => new TestElement(tag), createText: (text) => Object.assign(new TestElement('#text'), { text }), createComment: () => new TestElement('#comment'),
  setText: (node, text) => { node.text = text }, setElementText: (node, text) => { node.text = text; node.children = [] }, parentNode: (node) => node.parentElement, nextSibling: (node) => node.parentElement?.children[node.parentElement.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _old, value) => { node.props[key] = value; if (key === 'value') node.value = String(value ?? '') },
  remove: (node) => { if (node.parentElement) node.parentElement.children = node.parentElement.children.filter((item) => item !== node); node.parentElement = null },
  insert: (node, parent, anchor) => { const index = anchor ? parent.children.indexOf(anchor) : -1; parent.children.splice(index < 0 ? parent.children.length : index, 0, node); node.parentElement = parent },
})
const apps: App[] = []
afterEach(() => { for (const app of apps.splice(0)) app.unmount(); vi.unstubAllGlobals() })
function nodes(root: TestElement): TestElement[] { return [root, ...root.children.flatMap(nodes)] }
function find(root: TestElement, tag: string): TestElement { const value = nodes(root).find((node) => node.tag === tag); if (!value) throw new Error('Missing ' + tag); return value }
function click(node: TestElement): void { (node.props.onClick as () => void)() }
async function input(node: TestElement, value: string): Promise<void> { node.value = value; node.dispatchEvent(new Event('input')); await nextTick() }
function mount(props: { ownerKey: string; error: ProfileRequestError | null; onChangePassword: () => Promise<void> }): TestElement {
  vi.stubGlobal('document', testDocument)
  vi.stubGlobal('Document', TestDocument)
  const root = new TestElement('root')
  const app = renderer.createApp({ setup: () => () => h(SecurityScreen, props) }); apps.push(app); app.mount(root)
  return root
}
async function fill(root: TestElement): Promise<void> {
  const inputs = nodes(root).filter((node) => node.tag === 'input')
  await input(inputs[0]!, 'current-password')
  await input(inputs[1]!, 'Abcdefghij1!')
  await input(inputs[2]!, 'Abcdefghij1!')
}
async function submit(root: TestElement): Promise<void> { (find(root, 'form').props.onSubmit as (event: Event) => void)(new Event('submit', { cancelable: true })); await nextTick() }

describe('SecurityScreen sensitive owner lifecycle', () => {
  it.each(['never', 'reject', 'resolve'] as const)('releases a new owner immediately and isolates the old %s settlement', async (oldOutcome) => {
    let rejectOld!: (error: unknown) => void
    let resolveOld!: () => void
    let resolveNew!: () => void
    const oldRequest = new Promise<void>((resolve, reject) => { resolveOld = resolve; rejectOld = reject })
    const newRequest = new Promise<void>((resolve) => { resolveNew = resolve })
    const changePassword = vi.fn().mockReturnValueOnce(oldRequest).mockReturnValueOnce(newRequest)
    const props = reactive({ ownerKey: 'account-A', error: null as ProfileRequestError | null, onChangePassword: changePassword })
    const root = mount(props)
    const submitButton = () => nodes(root).find((node) => node.tag === 'button' && node.props.type === 'submit')!
    await fill(root)
    click(nodes(root).find((node) => node.props['aria-label'] === 'Показать текущий пароль')!)
    await nextTick()
    expect(nodes(root).filter((node) => node.tag === 'input')[0]!.props.type).toBe('text')
    await submit(root)
    expect(submitButton().props.disabled).toBe(true)
    props.ownerKey = 'account-B'
    await nextTick()
    expect(nodes(root).filter((node) => node.tag === 'input').map((node) => node.value)).toEqual(['', '', ''])
    expect(nodes(root).filter((node) => node.tag === 'input').map((node) => node.props.type)).toEqual(['password', 'password', 'password'])
    expect(submitButton().props.disabled).toBe(false)
    await fill(root)
    await submit(root)
    expect(changePassword).toHaveBeenCalledTimes(2)
    expect(submitButton().props.disabled).toBe(true)
    if (oldOutcome === 'reject') rejectOld(new ProfileRequestError('CURRENT_PASSWORD_INVALID', 'Old account failure'))
    if (oldOutcome === 'resolve') resolveOld()
    await Promise.resolve(); await nextTick()
    expect(submitButton().props.disabled).toBe(true)
    expect(nodes(root).filter((node) => node.tag === 'input').map((node) => node.value)).toEqual(['current-password', 'Abcdefghij1!', 'Abcdefghij1!'])
    expect(nodes(root).map((node) => node.text).join(' ')).not.toContain('Текущий пароль неверен')
    resolveNew()
    await Promise.resolve(); await nextTick()
    expect(submitButton().props.disabled).toBe(false)
    expect(nodes(root).filter((node) => node.tag === 'input').map((node) => node.value)).toEqual(['', '', ''])
  })
  it('clears every sensitive field when a session is revoked', async () => {
    const props = reactive({ ownerKey: 'account-A', error: null as ProfileRequestError | null, onChangePassword: async () => {} })
    const root = mount(props)
    await fill(root)
    props.error = new ProfileRequestError('SESSION_REVOKED', 'Сессия завершена')
    await nextTick()
    expect(nodes(root).filter((node) => node.tag === 'input').map((node) => node.value)).toEqual(['', '', ''])
    expect(nodes(root).filter((node) => node.tag === 'input').every((node) => node.props.disabled)).toBe(true)
  })
})
