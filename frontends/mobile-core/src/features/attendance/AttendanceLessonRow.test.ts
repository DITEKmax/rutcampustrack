/// <reference types="vite/client" />
import { afterEach, describe, expect, it } from 'vitest'
import { compile, createRenderer, h, nextTick, shallowReactive, ssrContextKey, type App } from 'vue'
import { compileScript, parse } from '@vue/compiler-sfc'
import AttendanceLessonRow from './AttendanceLessonRow.vue'
import rowSource from './AttendanceLessonRow.vue?raw'
import AttendanceSubjectList from './AttendanceSubjectList.vue'
import subjectsSource from './AttendanceSubjectList.vue?raw'
import AttendanceDocumentPreview from './AttendanceDocumentPreview.vue'
import previewSource from './AttendanceDocumentPreview.vue?raw'
import type { AttendanceLesson, AttendanceSubject } from './attendance-view-model'

for (const [component, source] of [
  [AttendanceDocumentPreview, previewSource],
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
      if (status === 'EXCUSED') {
        expect(tooltip(root)).toBeDefined()
        await invoke(
          nodes(root).find((item) => item.props['aria-label'] === 'Закрыть причину')!,
          'onClick',
        )
      }
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
  it('opens each excused lesson document independently and keeps it reachable when focus leaves the mark', async () => {
    const opened: string[] = []
    const documentForLesson = (id: string) => ({ label: 'Документ', open: () => opened.push(id) })
    const root = mount(() => h(AttendanceLessonRow, { lesson, documentForLesson }))
    const mark = nodes(root).find((item) => item.tag === 'button')!
    await invoke(mark, 'onFocus')
    let document = nodes(root).find((item) => item.props.class === 'attendance-status-document')!
    ;(mark.props.onBlur as (event: unknown) => void)({ relatedTarget: { closest: () => document } })
    await settle()
    await invoke(document, 'onClick')
    expect(opened).toEqual([lesson.id])
    expect(tooltip(root)).toBeDefined()
    await invoke(document, 'onBlur')
    expect(tooltip(root)).toBeUndefined()

    const metrics = {
      present: { count: 0, percent: null },
      presentOrExcused: { count: 0, percent: null },
      excused: { count: 2, percent: 100 },
      absent: { count: 0, percent: null },
      held: 2,
      planned: 2,
    }
    const subjects: readonly AttendanceSubject[] = [
      {
        id: '5',
        name: 'Предмет',
        typeCards: [
          {
            type: 'LECTURE',
            metrics,
            history: [
              { id: 'lesson:1', status: 'EXCUSED', explanation },
              { id: 'lesson:2', status: 'EXCUSED', explanation },
            ],
          },
        ],
      },
    ]
    const history = mount(() =>
      h(AttendanceSubjectList, { subjects, expandedSubjectId: '5', documentForLesson }),
    )
    const marks = nodes(history).filter(
      (item) => item.tag === 'button' && String(item.props['aria-label']).startsWith('Занятие'),
    )
    for (const [index, trigger] of marks.entries()) {
      await invoke(trigger, 'onClick')
      expect(trigger.props['aria-describedby']).toContain(`lesson:${index + 1}`)
      document = nodes(history).find((item) => item.props.class === 'attendance-status-document')!
      await invoke(document, 'onClick')
    }
    expect(opened).toEqual([lesson.id, 'lesson:1', 'lesson:2'])
  })
  it('opens an actual excused segment with its immediate preview and returns from full view to the same reason', async () => {
    const opened: string[] = []
    const metrics = {
      present: { count: 0, percent: null },
      presentOrExcused: { count: 1, percent: 100 },
      excused: { count: 1, percent: 100 },
      absent: { count: 0, percent: null },
      held: 1,
      planned: 1,
    }
    const subjects: readonly AttendanceSubject[] = [
      {
        id: '5',
        name: 'Предмет',
        typeCards: [
          {
            type: 'LECTURE',
            metrics,
            history: [{ id: 'actual-lesson:77', status: 'EXCUSED', explanation }],
          },
        ],
      },
    ]
    const root = mount(() =>
      h(AttendanceSubjectList, {
        subjects,
        expandedSubjectId: '5',
        documentForLesson: (id: string) =>
          id === 'actual-lesson:77'
            ? {
                label: 'Учебный образец',
                open: () => opened.push(id),
                preview: {
                  src: '/local-attachment.png',
                  kind: 'image' as const,
                  alt: 'Учебное вложение',
                },
              }
            : null,
      }),
    )
    const segment = nodes(root).find(
      (item) => item.tag === 'button' && String(item.props['aria-label']).startsWith('Занятие'),
    )!
    await invoke(segment, 'onClick')
    expect(text(tooltip(root)!)).toContain(explanation.comment)
    const image = nodes(root).find(
      (item) => item.tag === 'img' && item.props.src === '/local-attachment.png',
    )
    expect(image?.props.alt).toBe('Учебное вложение')
    expect(nodes(root).some((item) => item.props.class === 'attendance-status-document')).toBe(
      false,
    )
    const preview = nodes(root).find((item) => item.props.class === 'attendance-document-preview')!
    await invoke(preview, 'onClick')
    expect(nodes(root).find((item) => item.tag === 'dialog')).toBeDefined()
    const close = nodes(root).find(
      (item) => item.tag === 'button' && item.props.autofocus !== undefined,
    )!
    expect(close.props['aria-label']).toBe('Закрыть')
    expect(nodes(close).some((item) => item.tag === 'svg')).toBe(true)
    expect(text(close)).toBe('')
    await invoke(close, 'onClick')
    expect(nodes(root).find((item) => item.tag === 'dialog')).toBeUndefined()
    expect(text(tooltip(root)!)).toContain(explanation.comment)
    expect(segment.props['aria-expanded']).toBe(true)
    expect(
      nodes(root).find((item) => item.props.class === 'attendance-document-preview'),
    ).toBeDefined()
    expect(opened).toEqual([])
    await invoke(preview, 'onClick')
    const dialog = nodes(root).find((item) => item.tag === 'dialog')!
    ;(dialog.props.onCancel as (event: unknown) => void)({ preventDefault: () => undefined })
    await settle()
    expect(nodes(root).find((item) => item.tag === 'dialog')).toBeUndefined()
    expect(text(tooltip(root)!)).toContain(explanation.comment)
    const dismissReason = nodes(root).find(
      (item) => item.props['aria-label'] === 'Закрыть причину',
    )!
    expect(nodes(dismissReason).some((item) => item.tag === 'svg')).toBe(true)
    expect(text(dismissReason)).toBe('')
    await invoke(
      nodes(root).find((item) => item.props['aria-label'] === 'Закрыть причину')!,
      'onClick',
    )
    expect(tooltip(root)).toBeUndefined()
  })
})
