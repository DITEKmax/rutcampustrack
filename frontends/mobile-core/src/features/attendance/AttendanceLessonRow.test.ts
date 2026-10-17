/// <reference types="vite/client" />
import { afterEach, describe, expect, it } from 'vitest'
import { compile, createRenderer, h, nextTick, shallowReactive, ssrContextKey, type App } from 'vue'
import { compileScript, parse } from '@vue/compiler-sfc'
import AttendanceLessonRow from './AttendanceLessonRow.vue'
import rowSource from './AttendanceLessonRow.vue?raw'
import AttendanceSubjectList from './AttendanceSubjectList.vue'
import subjectsSource from './AttendanceSubjectList.vue?raw'
import type { AttendanceLesson, AttendanceSubject } from './attendance-view-model'

for (const [component, source] of [
  [AttendanceLessonRow, rowSource],
  [AttendanceSubjectList, subjectsSource],
] as const) {
  const descriptor = parse(source).descriptor
  component.render = compile(descriptor.template!.content, {
    bindingMetadata: compileScript(descriptor, { id: 'attendance-explanation-test' }).bindings!,
    prefixIdentifiers: true,
  })
}
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
const explanation = {
  reasonCode: 'OTHER',
  reasonText: 'Другое',
  comment: 'Полное пояснение '.repeat(20),
  cancellationReason: 'Отмена преподавателем',
}
const lesson: AttendanceLesson = {
  id: 'physical:77',
  date: '2026-09-15',
  number: '1',
  subject: { id: '5', name: 'Предмет' },
  type: 'LECTURE',
  schedule: { startsAt: '09:00', endsAt: '10:30', room: null },
  status: 'EXCUSED',
  requestOptions: [{ id: 'EXCUSE', kind: 'EXCUSE', label: 'Подать', enabled: true }],
  explanation,
}
function mount(render: () => ReturnType<typeof h>) {
  const root = node('root')
  const app = renderer.createApp({ render })
  app.provide(ssrContextKey, { modules: new Set() })
  apps.push(app)
  app.mount(root)
  return root
}
const tooltip = (root: Node) => nodes(root).find((item) => item.props.role === 'tooltip')
async function invoke(target: Node, name: string) {
  ;(target.props[name] as (event: { key: string }) => void)({ key: 'Escape' })
  await settle()
}
describe('student attendance status explanation', () => {
  it.each(['EXCUSED', 'CANCELLED'] as const)(
    'reveals %s by hover, focus and tap in read-only mode without enabling a request',
    async (status) => {
      const events: unknown[] = []
      const props = shallowReactive<{
        lesson: AttendanceLesson
        terminal: boolean
        onToggleActions: (value: unknown) => void
      }>({
        lesson: { ...lesson, status },
        terminal: true,
        onToggleActions: (value: unknown) => events.push(value),
      })
      const root = mount(() => h(AttendanceLessonRow, props))
      await settle()
      const trigger = nodes(root).find((item) => item.tag === 'button')!
      const expected =
        status === 'EXCUSED' ? `Другое · ${explanation.comment}` : explanation.cancellationReason
      expect(trigger.props.disabled).not.toBe(true)
      expect(trigger.props['aria-label']).toContain(expected)
      expect(tooltip(root)).toBeUndefined()
      await invoke(trigger, 'onMouseenter')
      expect(text(tooltip(root)!)).toBe(expected)
      await invoke(trigger, 'onMouseleave')
      expect(tooltip(root)).toBeUndefined()
      await invoke(trigger, 'onFocus')
      expect(text(tooltip(root)!)).toBe(expected)
      await invoke(trigger, 'onMouseleave')
      expect(text(tooltip(root)!)).toBe(expected)
      await invoke(trigger, 'onKeydown')
      expect(tooltip(root)).toBeUndefined()
      await invoke(trigger, 'onClick')
      expect(text(tooltip(root)!)).toBe(expected)
      expect(events).toEqual([])
      await invoke(trigger, 'onBlur')
      expect(tooltip(root)).toBeUndefined()
      props.lesson = { ...lesson, status: 'PRESENT' }
      await settle()
      expect(tooltip(root)).toBeUndefined()
      expect(text(root)).not.toContain(explanation.comment)
    },
  )
  it('keeps legacy status without metadata noninteractive and exposes canonical subject-history explanation directly', async () => {
    const legacy = mount(() =>
      h(AttendanceLessonRow, { lesson: { ...lesson, explanation: null, requestOptions: [] } }),
    )
    expect(nodes(legacy).some((item) => item.tag === 'button')).toBe(false)
    const metrics = {
      present: { count: 0, percent: null },
      presentOrExcused: { count: 0, percent: null },
      excused: { count: 0, percent: null },
      absent: { count: 0, percent: null },
      held: 0,
      planned: 0,
    }
    const subjects: readonly AttendanceSubject[] = [
      {
        id: '5',
        name: 'Предмет',
        typeCards: [
          {
            type: 'LECTURE',
            metrics,
            history: [{ id: 'canonical:888', status: 'EXCUSED', explanation }],
          },
        ],
      },
    ]
    const root = mount(() => h(AttendanceSubjectList, { subjects, expandedSubjectId: '5' }))
    await settle()
    const trigger = nodes(root).find(
      (item) => item.tag === 'button' && String(item.props['aria-label']).startsWith('Занятие'),
    )!
    await invoke(trigger, 'onFocus')
    expect(text(tooltip(root)!)).toBe(`Другое · ${explanation.comment}`)
    expect(trigger.props['aria-describedby']).toContain('canonical:888')
    await invoke(trigger, 'onKeydown')
    expect(tooltip(root)).toBeUndefined()
  })
})
