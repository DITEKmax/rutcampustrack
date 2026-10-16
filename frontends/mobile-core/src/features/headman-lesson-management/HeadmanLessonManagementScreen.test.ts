/// <reference types="vite/client" />
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, h, nextTick, ref, shallowReactive, ssrContextKey, type App } from 'vue'
import HeadmanLessonManagementScreen from './HeadmanLessonManagementScreen.vue'
import type {
  HeadmanJournalApi,
  HeadmanJournalLesson,
} from '../headman-journal/headman-journal-client'
import type { HeadmanScheduleApi } from '../schedule/headman-schedule-client'

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
beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-10-16T09:00:00Z'))
  const stored = new Map<string, string>()
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => stored.get(key) ?? null,
    setItem: (key: string, value: string) => stored.set(key, value),
    removeItem: (key: string) => stored.delete(key),
  })
})
afterEach(() => {
  for (const app of apps.splice(0)) app.unmount()
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

function fixture(lessons: readonly HeadmanJournalLesson[] = []) {
  const owner = shallowReactive({ userId: '1', sessionId: 'session-a' })
  const childView = ref('agenda')
  let pending: Promise<readonly HeadmanJournalLesson[]> | null = null
  const listLessons = vi.fn(async () => {
    const reply = pending
    pending = null
    return reply ? await reply : lessons
  })
  const journal = { listLessons } as unknown as HeadmanJournalApi
  const schedule = {
    lessonSlots: async () => ({
      timezone: 'Europe/Moscow',
      slots: [{ lessonNumber: 2, startTime: '10:40:00', endTime: '12:10:00' }],
    }),
    academicWeekPolicy: async () => ({
      weekStart: '2026-10-12',
      weekEnd: '2026-10-18',
      parityNumber: 2,
    }),
    listSemesters: async () => [
      { id: 9, active: true, dateFrom: '2026-10-01', dateTo: '2026-12-31' },
    ],
    listAssignments: async () => [
      {
        id: 13,
        subjectId: 14,
        subjectName: 'Предмет',
        teacherId: 15,
        teacherName: 'Преподаватель',
        lessonType: 'LECTURE',
      },
    ],
  } as unknown as HeadmanScheduleApi
  const root = node('root')
  // Mirror the real parent's fresh owner object and render dependency on view-change.
  const app = renderer.createApp({
    render: () =>
      h('section', { 'data-child-view': childView.value }, [
        h(HeadmanLessonManagementScreen, {
          journalApi: journal,
          scheduleApi: schedule,
          groupId: 4,
          owner: { userId: owner.userId, sessionId: owner.sessionId },
          initialDate: '2026-10-16',
          onViewChange: (view: string) => {
            childView.value = view
          },
        }),
      ]),
  })
  app.provide(ssrContextKey, { modules: new Set() })
  apps.push(app)
  app.mount(root)
  return {
    root,
    owner,
    listLessons,
    deferTarget: (reply: Promise<readonly HeadmanJournalLesson[]>) => {
      pending = reply
    },
  }
}
function main(root: Node): Node {
  return nodes(root).find((item) => item.props.class === 'lesson-management')!
}
async function click(root: Node, predicate: (item: Node) => boolean): Promise<void> {
  const button = nodes(root).find((item) => item.tag === 'button' && predicate(item))!
  expect(button).toBeDefined()
  expect(button.props.disabled).not.toBe(true)
  ;(button.props.onClick as () => void)()
  await settle()
}
async function openOneOff(root: Node): Promise<void> {
  if (!nodes(root).some((item) => item.props.class === 'lesson-management__free'))
    await click(
      root,
      (item) => item.props.role === 'switch' && text(item).includes('Добавление пары'),
    )
  await click(
    root,
    (item) => item.props.class === 'lesson-management__free' && item.props['data-slot'] === 2,
  )
  expect(main(root).props['data-management-view']).toBe('oneoff')
}

describe('lesson management owner identity in the actual SFC', () => {
  it.each([
    ['lecture', 'Лекция'],
    ['practice', 'Практика'],
    ['lab', 'Лабораторная'],
    ['LECTURE', 'Лекция'],
    ['PRACTICE', 'Практика'],
    ['LAB', 'Лабораторная'],
    [null, 'Тип не указан'],
    ['seminar', 'Тип не указан'],
  ])('renders physical type %s in its real card and detail', async (lessonType, label) => {
    const f = fixture([
      {
        id: 61,
        groupId: 4,
        subjectId: 14,
        date: '2026-10-16',
        status: 'PLANNED',
        lessonNumber: 2,
        startTime: '10:40:00',
        endTime: '12:10:00',
        room: 'Аудитория',
        lessonType,
        occurrenceRevision: '1',
        current: true,
        transferOperationId: null,
        transferState: null,
      },
    ])
    await settle()
    const card = nodes(f.root).find((item) => item.props['data-lesson-id'] === 61)!
    expect(card).toBeDefined()
    expect(text(card)).toContain(label)
    await click(f.root, (item) => item === card)
    expect(main(f.root).props['data-management-view']).toBe('detail')
    expect(
      text(
        nodes(f.root).find(
          (item) => item.props.class === 'lesson-management__card lesson-management__summary',
        )!,
      ),
    ).toContain(label)
  })

  it('keeps the one-off form when its parent publishes the view with an equivalent owner', async () => {
    const f = fixture()
    await settle()
    await openOneOff(f.root)
    expect(text(f.root)).toContain('Создать разовую пару')
    expect(f.listLessons).toHaveBeenCalledTimes(2)
  })

  it.each(['userId', 'sessionId'] as const)(
    'resets for a real %s change and ignores the old pending target response',
    async (field) => {
      const f = fixture()
      await settle()
      let resolveOld!: (lessons: readonly HeadmanJournalLesson[]) => void
      f.deferTarget(
        new Promise((resolve) => {
          resolveOld = resolve
        }),
      )
      await openOneOff(f.root)
      expect(text(f.root)).toContain('Проверяем свободные слоты')
      const previousReads = f.listLessons.mock.calls.length
      f.owner[field] = field === 'userId' ? '2' : 'session-b'
      await settle()
      expect(main(f.root).props['data-management-view']).toBe('agenda')
      expect(f.listLessons.mock.calls.length).toBeGreaterThan(previousReads)
      await openOneOff(f.root)
      const option = () =>
        nodes(f.root).find((item) => item.tag === 'option' && item.props.value === 2)!
      expect(option().props.disabled).toBe(false)
      resolveOld([{ current: true, lessonNumber: 2, status: 'PLANNED' } as HeadmanJournalLesson])
      await settle()
      expect(main(f.root).props['data-management-view']).toBe('oneoff')
      expect(option().props.disabled).toBe(false)
      expect(text(f.root)).not.toContain(' · занято')
    },
  )
})
