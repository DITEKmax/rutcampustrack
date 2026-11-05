/// <reference types="vite/client" />
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, h, nextTick, ref, shallowReactive, ssrContextKey, type App } from 'vue'
import HeadmanLessonManagementScreen from './HeadmanLessonManagementScreen.vue'
import {
  HeadmanJournalApiError,
  type HeadmanJournalApi,
  type HeadmanJournalLesson,
} from '../headman-journal/headman-journal-client'
import {
  HeadmanScheduleApiError,
  type HeadmanOneOffCreateInput,
  type HeadmanOneOffLesson,
  type HeadmanScheduleApi,
} from '../schedule/headman-schedule-client'
import type { HeadmanAssistantPermission } from '../headman-group/headman-group-client'
import { managementScope, type ManagementIntent } from './lesson-management-state'

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
  focus: () => void
  querySelectorAll: (selector: string) => Node[]
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
    focus: () => undefined,
    querySelectorAll(selector) {
      return selector === '[role="option"]'
        ? nodes(this).filter((child) => child.props.role === 'option')
        : []
    },
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

function fixture(
  lessons: readonly HeadmanJournalLesson[] = [],
  oneOffs: readonly HeadmanOneOffLesson[] = [],
  options: {
    activeSemester?: boolean
    catalogError?: Error
    sessionId?: string
    assistantPermissions?: readonly HeadmanAssistantPermission[]
  } = {},
) {
  const owner = shallowReactive({ userId: '1', sessionId: options.sessionId ?? 'session-a' })
  const onError = vi.fn()
  const childView = ref('agenda')
  let pending: Promise<readonly HeadmanJournalLesson[]> | null = null
  const listLessons = vi.fn(async (_groupId: number, _dateFrom: string, _dateTo: string) => {
    const reply = pending
    pending = null
    return reply ? await reply : lessons
  })
  const listOneOffLessons = vi.fn(
    async (_groupId: number, _dateFrom: string, _dateTo: string) => oneOffs,
  )
  const createOneOffLesson = vi.fn(
    async (_input: HeadmanOneOffCreateInput, _key: string): Promise<HeadmanOneOffLesson> => {
      throw new Error('not used')
    },
  )
  const journal = { listLessons } as unknown as HeadmanJournalApi
  const lessonSlots = vi.fn(async () => ({
    timezone: 'Europe/Moscow',
    slots: [
      { lessonNumber: 2, startTime: '10:40:00', endTime: '12:10:00' },
      { lessonNumber: 3, startTime: '12:20:00', endTime: '13:50:00' },
    ],
  }))
  if (options.catalogError) lessonSlots.mockRejectedValueOnce(options.catalogError)
  const schedule = {
    lessonSlots,
    academicWeekPolicy: async () => ({
      weekStart: '2026-10-12',
      weekEnd: '2026-10-18',
      parityNumber: 2,
    }),
    listSemesters: async () =>
      options.activeSemester === false
        ? []
        : [{ id: 9, active: true, dateFrom: '2026-10-01', dateTo: '2026-12-31' }],
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
    listOneOffLessons,
    createOneOffLesson,
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
          assistantPermissions: options.assistantPermissions ?? null,
          initialDate: '2026-10-16',
          onError,
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
    onError,
    listLessons,
    listOneOffLessons,
    createOneOffLesson,
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
const alternateDate = '2026-10-17'
const exactLesson: HeadmanJournalLesson = {
  id: 72,
  groupId: 4,
  subjectId: 14,
  date: alternateDate,
  lessonNumber: 2,
  status: 'PLANNED',
  startTime: '10:40:00',
  endTime: '12:10:00',
  room: null,
  lessonType: 'LECTURE',
  occurrenceRevision: '1',
  current: true,
  transferOperationId: null,
  transferState: null,
}
const acceptedOneOff: HeadmanOneOffLesson = {
  id: 26,
  physicalLessonId: exactLesson.id,
  groupId: 4,
  subjectId: 14,
  semesterId: 9,
  date: alternateDate,
  lessonNumber: 2,
}
const card = (root: Node, id: number) =>
  nodes(root).find((item) => item.props['data-lesson-id'] === id)
const recoverButton = (item: Node) =>
  item.tag === 'button' && text(item).trim() === 'Проверить исходное действие'
const savedOneOff = {
  owner: { userId: '1', sessionId: 'session-a', groupId: 4 },
  command: {
    kind: 'ONE_OFF',
    key: '11111111-2222-4333-8444-555555555555',
    input: {
      groupId: 4,
      subjectId: 14,
      assignmentId: 13,
      date: '2026-10-16',
      lessonNumber: 3,
      startTime: '12:20:00',
      endTime: '13:50:00',
    },
  },
  sent: true,
} as const satisfies ManagementIntent
const saveOneOff = () =>
  sessionStorage.setItem(managementScope(savedOneOff.owner), JSON.stringify(savedOneOff))

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

  it.each(['agenda', 'origins'] as const)(
    'opens the exact accepted date and retries failed %s read without resending creation',
    async (failedRead) => {
      const f = fixture()
      const sameSlot = { ...exactLesson, id: 71 }
      let rows: readonly HeadmanJournalLesson[] = [{ ...sameSlot, lessonNumber: 3 }]
      f.listLessons.mockImplementation(async (_groupId, from) =>
        from === alternateDate ? rows : [],
      )
      f.createOneOffLesson.mockImplementationOnce(async () => {
        rows = [sameSlot, exactLesson]
        const read = failedRead === 'agenda' ? f.listLessons : f.listOneOffLessons
        read.mockRejectedValueOnce(new TypeError('temporary read failure'))
        return acceptedOneOff
      })
      await settle()
      await openOneOff(f.root)
      await click(f.root, (item) => item.props.class === 'lesson-management__select')
      await click(
        f.root,
        (item) => 'data-outside' in item.props && text(item).trim() === '17',
      )
      await click(f.root, (item) => item.props['aria-label'] === 'Номер пары')
      const options = nodes(f.root).filter((item) => item.props.role === 'option')
      expect(options.map((item) => text(item))).toEqual(['2 пара · 10:40–12:10'])
      expect(options[0]?.props['aria-selected']).toBe(true)
      await click(f.root, (item) => item === options[0])
      await click(f.root, (item) => text(item).includes('Создать разовую пару'))

      expect(f.createOneOffLesson).toHaveBeenCalledOnce()
      expect(f.createOneOffLesson.mock.calls[0]?.[0]).toMatchObject({
        date: alternateDate,
        lessonNumber: 2,
        assignmentId: 13,
      })
      expect(main(f.root).props['data-management-view']).toBe('agenda')
      expect(text(f.root)).toContain('суббота, 17 октября')
      expect(nodes(f.root).filter((item) => item.props['data-tone'] === 'success')).toHaveLength(1)
      expect(f.onError).not.toHaveBeenCalled()
      if (failedRead === 'agenda') expect(card(f.root, exactLesson.id)).toBeUndefined()
      else expect(text(card(f.root, exactLesson.id)!)).toContain('Разовая')
      f.listOneOffLessons.mockResolvedValue([acceptedOneOff])
      await click(f.root, (item) => text(item).trim() === 'Повторить')
      expect(f.listLessons).toHaveBeenLastCalledWith(4, alternateDate, alternateDate)
      expect(f.listOneOffLessons).toHaveBeenLastCalledWith(4, alternateDate, alternateDate)
      expect(f.createOneOffLesson).toHaveBeenCalledOnce()
      expect(text(card(f.root, exactLesson.id)!)).toContain('Разовая')
      expect(text(card(f.root, sameSlot.id)!)).not.toContain('Разовая')
      await click(f.root, (item) => item.props['aria-label'] === 'Закрыть уведомление')
      expect(nodes(f.root).some((item) => item.props['data-tone'] === 'success')).toBe(false)
    },
  )

  it('keeps the saved agenda during a one-off readback failure and hides the accepted slot', async () => {
    const date = '2026-10-16'
    const created = { ...exactLesson, date }
    const existing = { ...created, id: 71, lessonNumber: 3 }
    const f = fixture([existing])
    const readFailure = new HeadmanJournalApiError(
      new Response(null, { status: 503 }),
      { detail: 'Временный сбой чтения' },
    )
    f.createOneOffLesson.mockImplementationOnce(async () => {
      f.listLessons.mockRejectedValueOnce(readFailure)
      return { ...acceptedOneOff, date }
    })
    await settle()
    await openOneOff(f.root)
    await click(f.root, (item) => text(item).includes('Создать разовую пару'))

    expect(card(f.root, existing.id)).toBeDefined()
    expect(card(f.root, created.id)).toBeUndefined()
    expect(nodes(f.root).some((item) => item.props['data-slot'] === 2)).toBe(false)
    expect(text(f.root)).toContain('Временный сбой чтения')
    expect(f.onError).not.toHaveBeenCalled()
    f.listLessons.mockResolvedValueOnce([existing, created])
    await click(f.root, (item) => text(item).trim() === 'Повторить')
    expect(f.createOneOffLesson).toHaveBeenCalledOnce()
    expect(text(card(f.root, created.id)!)).toContain('Разовая')
    expect(text(f.root)).not.toContain('Временный сбой чтения')
    expect(f.onError).not.toHaveBeenCalled()
  })

  it('holds both accepted slots until each physical row appears on its date', async () => {
    const date = '2026-10-16'
    const first = { ...exactLesson, date }
    const second = { ...first, id: 73, lessonNumber: 3 }
    const firstReceipt = { ...acceptedOneOff, date }
    const secondReceipt = {
      ...firstReceipt,
      id: 27,
      physicalLessonId: second.id,
      lessonNumber: second.lessonNumber,
    }
    let visible: readonly HeadmanJournalLesson[] = []
    const f = fixture()
    f.listLessons.mockImplementation(async (_group, from) => (from === date ? visible : []))
    f.createOneOffLesson.mockResolvedValueOnce(firstReceipt)
    f.createOneOffLesson.mockImplementationOnce(async () => {
      visible = [second]
      f.listOneOffLessons.mockResolvedValue([secondReceipt])
      return secondReceipt
    })
    const submit = () => click(f.root, (item) => text(item).includes('Создать разовую пару'))
    const free = (number: number) => nodes(f.root).find((item) => item.props['data-slot'] === number)
    await settle()
    await openOneOff(f.root)
    await submit()
    expect(free(2)).toBeUndefined()
    await click(f.root, (item) => item === free(3))
    await submit()
    expect(f.createOneOffLesson.mock.calls.map(([input]) => input.lessonNumber)).toEqual([2, 3])
    expect(text(card(f.root, second.id)!)).toContain('Разовая')
    expect(card(f.root, first.id)).toBeUndefined()
    expect(free(2)).toBeUndefined()

    await click(f.root, (item) => item.props['aria-label'] === 'суббота, 17 октября')
    expect(free(2)).toBeDefined()
    await click(f.root, (item) => item.props['aria-label'] === 'пятница, 16 октября')
    expect(free(2)).toBeUndefined()
    f.listLessons.mockRejectedValueOnce(new TypeError('Временный сбой чтения'))
    await click(f.root, (item) => text(item).trim() === 'Повторить')
    expect(f.onError).not.toHaveBeenCalled()
    expect(free(2)).toBeUndefined()

    visible = [first, second]
    await click(f.root, (item) => text(item).trim() === 'Повторить')
    expect(text(card(f.root, first.id)!)).toContain('Разовая')
    expect(text(card(f.root, second.id)!)).toContain('Разовая')
    expect(text(f.root)).not.toContain('Расписание ещё не отобразило')
    expect(f.createOneOffLesson).toHaveBeenCalledTimes(2)
  })

  it('uses exact one-off metadata after reopening to hold a missing row in agenda and transfer', async () => {
    const existing = { ...exactLesson, id: 71, lessonNumber: 3, occurrenceId: '141' }
    const f = fixture([existing], [acceptedOneOff])
    await settle()
    await click(f.root, (item) => item.props['aria-label'] === 'суббота, 17 октября')
    await click(
      f.root,
      (item) => item.props.role === 'switch' && text(item).includes('Добавление пары'),
    )
    expect(card(f.root, exactLesson.id)).toBeUndefined()
    expect(nodes(f.root).some((item) => item.props['data-slot'] === 2)).toBe(false)
    expect(text(f.root)).toContain('Расписание ещё не отобразило')

    await click(f.root, (item) => item === card(f.root, existing.id))
    await click(f.root, (item) => text(item).includes('Перенести пару'))
    const slot = nodes(f.root).find((item) => item.tag === 'option' && item.props.value === 2)
    expect(slot?.props.disabled).toBe(true)
    expect(f.createOneOffLesson).not.toHaveBeenCalled()
  })

  it('verifies one-off occupancy for a date reached only through the form calendar', async () => {
    const f = fixture()
    let targetReads = 0
    f.listOneOffLessons.mockImplementation(async (_group, from) => {
      if (from !== alternateDate) return []
      if (++targetReads === 1) throw new TypeError('Не удалось прочитать разовые пары')
      return [acceptedOneOff]
    })
    const create = () =>
      nodes(f.root).find((item) => item.tag === 'button' && text(item).includes('Создать разовую пару'))
    await settle()
    await openOneOff(f.root)
    expect(targetReads).toBe(0)
    await click(f.root, (item) => item.props.class === 'lesson-management__select')
    await click(f.root, (item) => 'data-outside' in item.props && text(item).trim() === '17')
    expect(main(f.root).props['data-management-view']).toBe('oneoff')
    expect(create()?.props.disabled).toBe(true)
    expect(text(f.root)).toContain('Занятость не проверена')
    expect(f.onError).not.toHaveBeenCalled()

    await click(f.root, (item) => text(item).trim() === 'Повторить')
    await click(f.root, (item) => item.props['aria-label'] === 'Номер пары')
    const options = nodes(f.root).filter((item) => item.props.role === 'option')
    expect(options.map(text)).toEqual(['3 пара · 12:20–13:50'])
    expect(create()?.props.disabled).toBe(true)
    expect(f.listLessons).toHaveBeenLastCalledWith(4, alternateDate, alternateDate)
    expect(f.listOneOffLessons).toHaveBeenLastCalledWith(4, alternateDate, alternateDate)
    expect(f.createOneOffLesson).not.toHaveBeenCalled()
  })

  it('reopens the agenda with the server physical badge and no old success notice', async () => {
    const f = fixture([exactLesson], [acceptedOneOff])
    await settle()
    await click(f.root, (item) => item.props['aria-label'] === 'суббота, 17 октября')
    expect(text(card(f.root, exactLesson.id)!)).toContain('Разовая')
    expect(nodes(f.root).some((item) => item.props['data-tone'] === 'success')).toBe(false)
    expect(f.createOneOffLesson).not.toHaveBeenCalled()
  })

  it.each(['semester closed', 'catalog unavailable'] as const)(
    'allows only explicit replay of the stored one-off command with %s',
    async (scenario) => {
      saveOneOff()
      const f = fixture(
        [exactLesson],
        [acceptedOneOff],
        scenario === 'semester closed'
          ? { activeSemester: false }
          : { catalogError: new TypeError('catalog unavailable') },
      )
      f.createOneOffLesson.mockResolvedValueOnce(acceptedOneOff)
      expect(nodes(f.root).find(recoverButton)?.props.disabled).toBe(true)
      await settle()
      expect(f.createOneOffLesson).not.toHaveBeenCalled()
      expect(f.onError).toHaveBeenCalledTimes(scenario === 'catalog unavailable' ? 1 : 0)
      expect(text(f.root)).not.toContain('Создать разовую пару')
      await click(f.root, recoverButton)

      expect(f.createOneOffLesson.mock.calls).toEqual([
        [savedOneOff.command.input, savedOneOff.command.key],
      ])
      expect(f.listLessons).toHaveBeenLastCalledWith(4, alternateDate, alternateDate)
      expect(text(card(f.root, exactLesson.id)!)).toContain('Разовая')
      expect(sessionStorage.getItem(managementScope(savedOneOff.owner))).toBeNull()
    },
  )

  it.each(['assistant', 'foreign session', 'denied headman'] as const)(
    'does not replay a stored one-off as %s',
    async (actor) => {
      saveOneOff()
      const f = fixture(
        [],
        [],
        actor === 'assistant'
          ? { assistantPermissions: ['CANCEL_LESSONS'] }
          : actor === 'foreign session'
            ? { sessionId: 'session-b' }
            : { catalogError: new HeadmanScheduleApiError(new Response(null, { status: 403 }), null) },
      )
      await settle()
      const button = nodes(f.root).find(recoverButton)
      if (actor === 'assistant') {
        expect(button?.props.disabled).toBe(true)
        // Also exercise the handler guard independent of the disabled control.
        ;(button!.props.onClick as () => void)()
        await settle()
      } else {
        expect(button).toBeUndefined()
      }
      expect(f.createOneOffLesson).not.toHaveBeenCalled()
      expect(sessionStorage.getItem(managementScope(savedOneOff.owner))).not.toBeNull()
    },
  )

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
      const slot = () =>
        nodes(f.root).find((item) => item.props['aria-label'] === 'Номер пары')!
      expect(slot().props.disabled).toBe(false)
      expect(text(slot())).toContain('2 пара')
      resolveOld([{ ...exactLesson, date: '2026-10-16' }])
      await settle()
      expect(main(f.root).props['data-management-view']).toBe('oneoff')
      expect(slot().props.disabled).toBe(false)
      expect(text(slot())).toContain('2 пара')
      expect(text(f.root)).not.toContain(' · занято')
    },
  )
})
