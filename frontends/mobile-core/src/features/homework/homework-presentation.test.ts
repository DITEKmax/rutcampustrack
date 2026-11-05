import { createRenderer, h, markRaw, nextTick, reactive, type App, type Component } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import HomeworkCompletion from './HomeworkCompletion.vue'
import HomeworkScreen from './HomeworkScreen.vue'
import type { StudentHomework, StudentHomeworkItem } from '../../api/types'

// Exercise real Vue components; this host deliberately provides no browser layout.
class Node {
  children: Node[] = []
  parent: Node | null = null
  props: Record<string, unknown> = {}
  text = ''
  captures = new Set<number>()
  constructor(readonly tag: string) {
    markRaw(this)
  }
  focus(): void {}
  getBoundingClientRect() {
    return { width: this.tag === 'button' ? 44 : 326 }
  }
  setPointerCapture(id: number) {
    this.captures.add(id)
  }
  hasPointerCapture(id: number) {
    return this.captures.has(id)
  }
  releasePointerCapture(id: number) {
    this.captures.delete(id)
  }
}
const renderer = createRenderer<Node, Node>({
  createElement: (tag) => new Node(tag),
  createText: (text) => Object.assign(new Node('#text'), { text }),
  createComment: () => new Node('#comment'),
  setText: (node, text) => {
    node.text = text
  },
  setElementText: (node, text) => {
    node.text = text
    node.children = []
  },
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _old, value) => {
    node.props[key] = value
  },
  insert: (node, parent, anchor = null) => {
    if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
    node.parent = parent
  },
  remove: (node) => {
    node.parent?.children.splice(node.parent.children.indexOf(node), 1)
    node.parent = null
  },
})
const apps: App[] = []
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  vi.unstubAllGlobals()
})
function nodes(root: Node): Node[] {
  return [root, ...root.children.flatMap(nodes)]
}
function find(root: Node, predicate: (node: Node) => boolean): Node {
  const result = nodes(root).find(predicate)
  if (!result) throw new Error('Expected rendered node missing')
  return result
}
function hasClass(node: Node, name: string): boolean {
  return String(node.props.class ?? '')
    .split(' ')
    .includes(name)
}
function mount(component: Component, props: Record<string, unknown>) {
  vi.stubGlobal('getComputedStyle', () => ({ paddingInlineStart: '4' }))
  const root = new Node('root')
  const app = renderer.createApp({ render: () => h(component, props) })
  apps.push(app)
  app.mount(root)
  return { root, app }
}
function completion(completed = false) {
  const intents: boolean[] = []
  const ready = vi.fn()
  const props = reactive({
    completed,
    pending: false,
    disabled: false,
    resetKey: 'owner-a:task-a',
    label: 'Задание',
    subject: 'Математика',
    onComplete: (desired: boolean) => intents.push(desired),
    onHandleReady: ready,
  })
  const mounted = mount(HomeworkCompletion, props)
  const handle = find(mounted.root, (node) => node.props.role === 'slider')
  const track = find(mounted.root, (node) => hasClass(node, 'homework-completion'))
  return { ...mounted, props, intents, ready, handle, track }
}
async function key(handle: Node, value: string) {
  ;(handle.props.onKeydown as (event: unknown) => void)({ key: value, preventDefault() {} })
  await nextTick()
}
async function pointer(handle: Node, type: string, x: number, y = 0) {
  ;(handle.props[type] as (event: unknown) => void)({
    pointerId: 1,
    clientX: x,
    clientY: y,
    isPrimary: true,
    button: 0,
  })
  await nextTick()
}
function checkVisible(root: Node): boolean {
  return nodes(root).some((node) => hasClass(node, 'homework-completion__check'))
}

describe('homework progress and authoritative completion presentation', () => {
  it('drives fill/leading handle from the same partial gesture and cancels without an intent', async () => {
    const view = completion()
    await pointer(view.handle, 'onPointerdown', 0)
    await pointer(view.handle, 'onPointermove', 137)
    expect(view.handle.props['aria-valuenow']).toBe(50)
    expect(
      find(view.root, (node) => hasClass(node, 'homework-completion__travel')).props.style,
    ).toEqual({ transform: 'translateX(50%)' })
    expect(
      find(view.root, (node) => hasClass(node, 'homework-completion__fill')).props.style,
    ).toEqual({ transform: 'scaleX(0.5)' })
    await pointer(view.handle, 'onPointercancel', 137)
    expect(view.handle.props['aria-valuenow']).toBe(0)
    expect(view.handle.captures.size).toBe(0)
    await pointer(view.handle, 'onPointerup', 274)
    expect(view.intents).toEqual([])
  })

  it('withholds check/settle for optimistic pending, rolls back on failure, then confirms retry ACK', async () => {
    const view = completion()
    for (let i = 0; i < 4; i++) await key(view.handle, 'ArrowRight')
    await key(view.handle, 'Enter')
    expect(view.intents).toEqual([true])
    expect(view.track.props['data-state']).toBe('pending')
    Object.assign(view.props, { completed: true, pending: true, disabled: true })
    await nextTick()
    expect(checkVisible(view.root)).toBe(false)
    expect(view.track.props['data-settled']).toBeNull()
    await key(view.handle, 'Enter')
    expect(view.intents).toEqual([true])
    Object.assign(view.props, { completed: false, pending: false })
    await nextTick()
    expect(view.track.props['data-state']).toBe('open')
    expect(view.track.props['data-settled']).toBeNull()
    expect(
      find(view.root, (node) => hasClass(node, 'homework-completion__travel')).props.style,
    ).toEqual({ transform: 'translateX(0%)' })
    // Retry belongs to the parent's error action, with the same saved intent.
    Object.assign(view.props, { completed: true, pending: true })
    await nextTick()
    expect(checkVisible(view.root)).toBe(false)
    Object.assign(view.props, { pending: false, disabled: false })
    await nextTick()
    expect(checkVisible(view.root)).toBe(true)
    expect(view.track.props['data-settled']).toBe('completed')
  })

  it('supports reversal, while changed task identity and unmount discard staged work', async () => {
    const view = completion(true)
    await pointer(view.handle, 'onPointerdown', 274)
    await pointer(view.handle, 'onPointermove', 0)
    await pointer(view.handle, 'onPointerup', 0)
    expect(view.intents).toEqual([false])
    Object.assign(view.props, { completed: false, pending: true, disabled: true })
    await nextTick()
    expect(checkVisible(view.root)).toBe(false)
    Object.assign(view.props, { pending: false, disabled: false })
    await nextTick()
    expect(view.track.props['data-settled']).toBe('open')
    for (let i = 0; i < 4; i++) await key(view.handle, 'ArrowRight')
    view.props.resetKey = 'owner-b:task-b'
    await nextTick()
    await key(view.handle, 'Enter')
    expect(view.intents).toEqual([false])
    await pointer(view.handle, 'onPointerdown', 0)
    await pointer(view.handle, 'onPointermove', 274)
    view.app.unmount()
    apps.splice(apps.indexOf(view.app), 1)
    expect(view.handle.captures.size).toBe(0)
    expect(view.ready).toHaveBeenLastCalledWith(null)
    expect(view.intents).toEqual([false])
  })
})

function task(id: string, completed: boolean): StudentHomeworkItem {
  return {
    id,
    title: 'Задание',
    description: '',
    lessonDate: '2026-09-07',
    lessonNumber: 1,
    bindingMode: 'LESSON',
    archived: false,
    link: null,
    subject: { id: 'subject', name: 'Математика' },
    completed,
    completedAt: completed ? '2026-09-07T09:00:00Z' : null,
  }
}

describe('homework month completion indicators', () => {
  it('derives mixed/all-complete/reverted dates from the same feed and never marks unknown data complete', async () => {
    const feed: StudentHomework = {
      semester: { id: 'semester', name: 'Осень', dateFrom: '2026-09-01', dateTo: '2026-12-31' },
      from: '2026-09-01',
      to: '2026-09-30',
      serverNow: '2026-09-07T10:00:00Z',
      items: [task('a', true), task('b', false)],
    }
    const props = reactive({
      homework: feed,
      loading: false,
      error: null as string | null,
      navItems: [],
      periodMode: 'month',
      pendingIds: [] as string[],
      selectedDate: '2026-09-07',
      periodRange: { from: feed.from, to: feed.to },
      semesterRange: { from: feed.from, to: feed.to },
    })
    const { root } = mount(HomeworkScreen, props)
    const day = () => find(root, (node) => node.props['data-homework-date'] === '2026-09-07')
    const dot = () => find(day(), (node) => hasClass(node, 'homework-calendar__dot'))
    expect(dot().props['data-completion']).toBe('open')
    expect(day().props['aria-label']).toContain('Заданий: 2')
    expect(
      nodes(find(root, (node) => node.props['data-homework-date'] === '2026-09-08')).some((node) =>
        hasClass(node, 'homework-calendar__dot'),
      ),
    ).toBe(false)
    props.pendingIds = ['b']
    props.homework.items = [task('a', true), task('b', true)]
    await nextTick()
    expect(dot().props['data-completion']).toBe('open')
    expect(day().props['aria-label']).toContain('Сохраняем изменения')
    // Failed forward intent restores the same feed; retry still cannot claim success.
    props.pendingIds = []
    props.homework.items = [task('a', true), task('b', false)]
    await nextTick()
    expect(dot().props['data-completion']).toBe('open')
    props.pendingIds = ['b']
    props.homework.items = [task('a', true), task('b', true)]
    await nextTick()
    expect(dot().props['data-completion']).toBe('open')
    props.pendingIds = []
    await nextTick()
    expect(dot().props['data-completion']).toBe('completed')
    expect(day().props['aria-label']).toContain('Все выполнены')
    // Reverse failure restores completed; a later successful reverse remains open.
    props.pendingIds = ['a']
    props.homework.items = [task('a', false), task('b', true)]
    await nextTick()
    expect(dot().props['data-completion']).toBe('open')
    expect(day().props['aria-label']).toContain('Сохраняем изменения')
    props.pendingIds = []
    props.homework.items = [task('a', true), task('b', true)]
    await nextTick()
    expect(dot().props['data-completion']).toBe('completed')
    props.pendingIds = ['a']
    props.homework.items = [task('a', false), task('b', true)]
    await nextTick()
    expect(dot().props['data-completion']).toBe('open')
    props.pendingIds = []
    await nextTick()
    expect(dot().props['data-completion']).toBe('open')
    for (const state of ['loading', 'error', 'unknown'] as const) {
      props.loading = state === 'loading'
      props.error = state === 'error' ? 'Не загружено' : null
      props.homework.to = state === 'unknown' ? '2026-09-07' : '2026-09-30'
      await nextTick()
      expect(nodes(day()).some((node) => hasClass(node, 'homework-calendar__dot'))).toBe(false)
    }
  })
})
