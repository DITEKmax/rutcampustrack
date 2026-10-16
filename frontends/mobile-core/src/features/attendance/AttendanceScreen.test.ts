/// <reference types="vite/client" />
import { afterEach, describe, expect, it } from 'vitest'
import { createRenderer, h, nextTick, shallowReactive, ssrContextKey, type App } from 'vue'
import AttendanceScreen from './AttendanceScreen.vue'
import { toAttendanceViewModel } from './use-attendance'
import type { StudentAttendanceResponse } from '../../api/types'
import type { AttendanceReadState, AttendanceViewModel } from './attendance-view-model'

interface Node {
  tag: string
  tagName: string
  text: string
  value: unknown
  selected: boolean
  props: Record<string, unknown>
  children: Node[]
  parent: Node | null
  options: Node[]
  addEventListener: () => void
  getRootNode: () => { activeElement: null }
}
function node(tag = ''): Node {
  return {
    tag,
    tagName: tag.toUpperCase(),
    text: '',
    value: '',
    selected: false,
    props: {},
    children: [],
    parent: null,
    get options() {
      return this.children.filter((child) => child.tag === 'option')
    },
    addEventListener: () => undefined,
    getRootNode: () => ({ activeElement: null }),
  }
}
function remove(node: Node): void {
  if (node.parent) node.parent.children = node.parent.children.filter((child) => child !== node)
  node.parent = null
}
const renderer = createRenderer<Node, Node>({
  patchProp: (node, key, _old, value) => {
    node.props[key] = value
  },
  insert: (node, parent, anchor) => {
    remove(node)
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
    node.parent = parent
  },
  remove,
  createElement: node,
  createText: (text) => ({ ...node('#text'), text }),
  createComment: (text) => ({ ...node('#comment'), text }),
  setText: (node, text) => {
    node.text = text
  },
  setElementText: (node, text) => {
    node.text = text
    node.children = []
  },
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
})
const nodes = (node: Node): Node[] => [node, ...node.children.flatMap(nodes)]
const text = (node: Node): string =>
  node.tag === '#comment' ? '' : node.text + node.children.map(text).join('')
async function settle(): Promise<void> {
  for (let index = 0; index < 32; index += 1) await Promise.resolve()
  await nextTick()
}

const apps: App[] = []
afterEach(() => {
  for (const app of apps.splice(0)) app.unmount()
})
function mount(render: () => ReturnType<typeof h>) {
  const root = node('root')
  const app = renderer.createApp({ render })
  app.provide(ssrContextKey, { modules: new Set() })
  apps.push(app)
  app.mount(root)
  return root
}

function response(): StudentAttendanceResponse {
  return {
    semester: { id: '9', name: 'Осень', dateFrom: '2020-09-02', dateTo: '2021-01-12' },
    serverNow: '2020-12-30T10:00:00Z',
    terminalReadOnly: false,
    metrics: {
      present: { count: 0, percent: null },
      presentOrExcused: { count: 0, percent: null },
      excused: { count: 0, percent: null },
      absent: { count: 0, percent: null },
      held: 0,
      planned: 0,
    },
    days: [],
    subjects: [],
    graph: { days: [], weeks: [] },
    weeks: [
      {
        dateFrom: '2020-12-28',
        dateTo: '2021-01-03',
        weekOfSemester: 18,
        parityNumber: 2,
        parityLabel: '2',
      },
      {
        dateFrom: '2021-01-04',
        dateTo: '2021-01-10',
        weekOfSemester: 19,
        parityNumber: 2,
        parityLabel: '2',
      },
    ],
  }
}
function screen(value = response()) {
  const props = shallowReactive<{
    state: AttendanceReadState<AttendanceViewModel>
    selectedDate: string
    onSelectDate: (date: string) => void
  }>({
    state: { status: 'ready', data: toAttendanceViewModel(value) },
    selectedDate: '2020-12-30',
    onSelectDate: (date) => {
      props.selectedDate = date
    },
  })
  const root = mount(() => h(AttendanceScreen, props))
  return { root, props }
}
function metadata(root: Node): string | undefined {
  const value = nodes(root).find((item) => item.props.class === 'attendance-week-pager__metadata')
  return value ? text(value) : undefined
}
async function page(root: Node, label: string): Promise<void> {
  const button = nodes(root).find(
    (item) => item.tag === 'button' && item.props['aria-label'] === label,
  )!
  ;(button.props.onClick as () => void)()
  await settle()
}

describe('student server week metadata in the existing attendance pager', () => {
  it('retains supplied semester ordinal and same parity across ISO53→1 while paging and returning', async () => {
    const { root, props } = screen()
    await settle()
    expect(metadata(root)).toBe('Неделя 18 · Чётность 2')
    expect(text(root)).toContain('28 дек.')
    await page(root, 'Следующая неделя')
    expect(props.selectedDate).toBe('2021-01-04')
    expect(metadata(root)).toBe('Неделя 19 · Чётность 2')
    expect(text(root)).toContain('4 янв.')
    await page(root, 'Предыдущая неделя')
    expect(props.selectedDate).toBe('2020-12-28')
    expect(metadata(root)).toBe('Неделя 18 · Чётность 2')
  })

  it('keeps legacy absent metadata unknown rather than calculating labels on the client', async () => {
    const value = response()
    delete (value as { weeks?: unknown }).weeks
    const { root } = screen(value)
    await settle()
    expect(metadata(root)).toBeUndefined()
    await page(root, 'Следующая неделя')
    expect(metadata(root)).toBeUndefined()
    expect(text(root)).not.toContain('Чётность')
  })

  it('clears previous metadata in every nonready owner or online state', async () => {
    const { root, props } = screen()
    await settle()
    expect(metadata(root)).toBeDefined()
    for (const state of [
      { status: 'loading' },
      { status: 'offline' },
      { status: 'no-semester' },
      { status: 'empty' },
      { status: 'forbidden', reason: 'Нет доступа' },
      { status: 'error', code: 'NETWORK', message: 'Повтори запрос', retryable: true },
    ] as const) {
      props.state = state
      await settle()
      expect(metadata(root)).toBeUndefined()
      expect(text(root)).not.toContain('Чётность')
    }
  })

  it('rejects malformed, foreign-period and duplicate metadata without inventing a calendar value', () => {
    const good = response().weeks![0]!
    for (const weeks of [
      [{ ...good, parityNumber: 3 }],
      [{ ...good, weekOfSemester: 0 }],
      [{ ...good, parityLabel: 'неизвестно' }],
      [{ ...good, dateTo: '2021-01-04' }],
      [{ ...good, dateFrom: '2019-12-30', dateTo: '2020-01-05' }],
      [good, good],
    ]) {
      expect(() =>
        toAttendanceViewModel({ ...response(), weeks } as StudentAttendanceResponse),
      ).toThrow('Не удалось получить данные недели.')
    }
  })
})
