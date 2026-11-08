import { createRenderer, h, markRaw, nextTick, shallowReactive, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import HeadmanHomeScreen from './HeadmanHomeScreen.vue'
import type { HeadmanHomeworkLessonContext } from '../homework/headman-homework-state'
import type {
  HeadmanHomeworkApi,
  HeadmanManagedHomework,
} from '../homework/headman-homework-client'
import type {
  HeadmanJournalApi,
  HeadmanJournalLesson,
  HeadmanJournalReport,
} from '../headman-journal/headman-journal-client'

class Element {
  children: Element[] = []
  parent: Element | null = null
  props: Record<string, unknown> = {}
  text = ''
  constructor(readonly tag: string) {
    markRaw(this)
  }
}
const renderer = createRenderer<Element, Element>({
  createElement: (tag) => new Element(tag),
  createText: (text) => Object.assign(new Element('#text'), { text }),
  createComment: () => new Element('#comment'),
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
  remove: (node) => {
    if (node.parent) node.parent.children = node.parent.children.filter((item) => item !== node)
    node.parent = null
  },
  insert: (node, parent, anchor) => {
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
    node.parent = parent
  },
})
const apps: App[] = []
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  vi.useRealTimers()
  vi.unstubAllGlobals()
})
function all(root: Element, match: (node: Element) => boolean): Element[] {
  return [...(match(root) ? [root] : []), ...root.children.flatMap((child) => all(child, match))]
}
function byClass(root: Element, name: string): Element | undefined {
  return all(root, (node) =>
    String(node.props.class ?? '')
      .split(/\s+/)
      .includes(name),
  )[0]
}
function text(root: Element | undefined): string {
  return root ? root.text + root.children.map(text).join('') : ''
}
async function flush(): Promise<void> {
  for (let index = 0; index < 30; index++) {
    await Promise.resolve()
    await nextTick()
  }
}
const today = '2026-10-15'
function lesson(
  id: number,
  status: HeadmanJournalLesson['status'],
  date = today,
  groupId = 7,
): HeadmanJournalLesson {
  return {
    id,
    groupId,
    subjectId: 11,
    date,
    status,
    lessonNumber: id,
    startTime: '08:30:00',
    endTime: '10:00:00',
    room: '101',
    lessonType: 'LECTURE',
    blockedByHeadman: false,
    occurrenceRevision: '1',
    current: true,
    transferOperationId: null,
    transferState: null,
  }
}
function report(id: number, marked: boolean, groupId = 7, date = today): HeadmanJournalReport {
  return {
    lessonId: id,
    groupId,
    subjectId: 11,
    lessonDate: date,
    semesterId: 1,
    lessonStatus: id === 1 ? 'ACTIVE' : 'PLANNED',
    editable: false,
    entries: [1, 2, 3, 4].map((userId) => ({
      userId,
      displayName: `Синтетика ${userId}`,
      status: marked && userId === 1 ? 'PRESENT' : marked && userId === 2 ? 'EXCUSED' : 'EMPTY',
      symbol: null,
      markRevision: null,
      approvedTicket: null,
      source: 'headman',
      excuseReason: null,
      excuseType: marked && userId === 2 ? 'ILLNESS' : null,
      comment: null,
      attachmentId: null,
      attachmentName: null,
      attachmentContentType: null,
      attachmentSize: null,
      editable: false,
    })),
  }
}
function mount(
  api: HeadmanJournalApi,
  extra: {
    homeworkApi?: HeadmanHomeworkApi
    homeworkEnabled?: boolean
    onOpenHomework?: (context: HeadmanHomeworkLessonContext) => void
  } = {},
) {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-15T06:00:00Z'))
  const props = shallowReactive({ api, groupId: 7, selectedDate: today, offline: false, ...extra })
  const root = new Element('root')
  const app = renderer.createApp({ render: () => h(HeadmanHomeScreen, props) })
  app.mount(root)
  apps.push(app)
  return { root, props }
}

describe('Today uses the selected lesson report', () => {
  it('loads one initial roster, and moves the card, marks and counts with the selection', async () => {
    const nextDate = '2026-10-16'
    const listLessons = vi.fn(async (_group: number, date: string) =>
      date === today
        ? [lesson(1, 'ACTIVE'), lesson(2, 'PLANNED')]
        : [lesson(3, 'CLOSED', nextDate)],
    )
    const getLessonAttendance = vi.fn(async (id: number) =>
      report(id, id === 1, 7, id === 3 ? nextDate : today),
    )
    const api = {
      listLessons,
      getLessonAttendance,
      writable: false,
    } as unknown as HeadmanJournalApi
    const { root, props } = mount(api)
    await flush()
    expect(getLessonAttendance.mock.calls).toEqual([[1]])
    expect(listLessons.mock.calls).toEqual([[7, today, today]])
    expect(byClass(root, 'headman-home__requests')).toBeUndefined()
    expect(byClass(root, 'headman-home__ongoing')).toBeUndefined()
    expect(byClass(root, 'headman-manual-roster')).toBeDefined()
    const present = () => all(root, (node) => node.props['data-mark'] === 'PRESENT')
    const dayButton = (date: string) =>
      all(root, (node) => node.tag === 'button' && node.props['aria-label'] === date)[0]!
    expect(dayButton(today).props['aria-current']).toBe('date')
    expect(dayButton(today).props['aria-pressed']).toBe(true)
    expect(present()).toHaveLength(1)
    const reason = byClass(root, 'headman-home__excused')!
    expect(reason.tag).toBe('section')
    expect(text(reason)).toContain('Болезнь')

    props.selectedDate = nextDate
    await flush()
    expect(dayButton(today).props['aria-current']).toBe('date')
    expect(dayButton(today).props['aria-pressed']).toBe(false)
    expect(dayButton(nextDate).props['aria-current']).toBeUndefined()
    expect(dayButton(nextDate).props['aria-pressed']).toBe(true)
    expect(byClass(root, 'headman-home')?.props['data-lesson-id']).toBe(3)
    expect(present()).toHaveLength(0)
    props.selectedDate = today
    await flush()
    expect(byClass(root, 'headman-home')?.props['data-lesson-id']).toBe(1)
    expect(present()).toHaveLength(1)
    expect(getLessonAttendance.mock.calls).toEqual([[1], [3], [1]])
  })

  it('ignores late old-date responses and rejects a report with the wrong group/date', async () => {
    const nextDate = '2026-10-16'
    let finish!: (value: HeadmanJournalReport) => void
    const api = {
      listLessons: vi.fn(async (_group: number, date: string) => [
        lesson(date === today ? 1 : 3, 'ACTIVE', date),
      ]),
      getLessonAttendance: vi.fn((id: number) =>
        id === 1
          ? new Promise<HeadmanJournalReport>((resolve) => {
              finish = resolve
            })
          : Promise.resolve(report(3, true, 8, nextDate)),
      ),
      writable: true,
    } as unknown as HeadmanJournalApi
    const { root, props } = mount(api)
    await flush()
    props.selectedDate = nextDate
    await flush()
    finish(report(1, true))
    await flush()
    expect(byClass(root, 'headman-home')?.props['data-lesson-id']).toBe(3)
    expect(byClass(root, 'headman-manual-roster')).toBeUndefined()
    expect(text(root)).toContain('Сервер вернул состав другой пары.')
  })

  it('hands the selected same-day pair and its semester to homework creation', async () => {
    const first = { ...lesson(1, 'ACTIVE'), occurrenceId: '71' }
    const selected = { ...lesson(2, 'PLANNED'), occurrenceId: '72', occurrenceRevision: '4' }
    const api = {
      listLessons: async () => [first, selected],
      getLessonAttendance: async (id: number) => report(id, false),
      writable: false,
    } as unknown as HeadmanJournalApi
    const onOpenHomework = vi.fn()
    const { root } = mount(api, { homeworkEnabled: true, onOpenHomework })
    await flush()
    const next = all(root, (node) => node.props['aria-label'] === 'Следующая пара')[0]!
    ;(next.props.onClick as () => void)()
    await flush()
    const homework = all(root, (node) => node.tag === 'button' && text(node).trim() === 'Выдать ДЗ')[0]!
    ;(homework.props.onClick as () => void)()
    expect(onOpenHomework).toHaveBeenCalledExactlyOnceWith({ lesson: selected, semesterId: 1 })
  })

  it('keeps selected lesson homework read-only and filters out other dates', async () => {
    const homework: HeadmanManagedHomework = {
      id: 17,
      bindingId: null,
      requestKey: null,
      title: 'Синтетическое ДЗ на сегодня',
      description: 'Описание принятого задания',
      link: null,
      subjectId: 11,
      groupId: 7,
      semesterId: 1,
      publishedBy: 9,
      lessonDate: today,
      lessonNumber: null,
      bindingMode: 'DATE',
      revision: 1,
      archived: false,
    }
    const homeworkApi = {
      listHomeworks: vi.fn(async (_group: number, _semester: number, archived: boolean) =>
        archived
          ? []
          : [homework, { ...homework, id: 18, lessonDate: '2026-10-16', title: 'Завтра' }],
      ),
    } as unknown as HeadmanHomeworkApi
    const api = {
      listLessons: vi.fn(async () => [lesson(1, 'ACTIVE')]),
      getLessonAttendance: vi.fn(async () => report(1, true)),
      writable: false,
    } as unknown as HeadmanJournalApi
    const { root } = mount(api, { homeworkApi })
    await flush()
    const section = byClass(root, 'headman-home__homework')!
    expect(text(section)).toContain(homework.title)
    expect(text(section)).not.toContain('Завтра')
    ;(all(section, (node) => node.tag === 'button')[0]!.props.onClick as () => void)()
    await flush()
    expect(text(section)).toContain(homework.description)
    expect(
      all(section, (node) => node.tag === 'input' || node.props.role === 'slider'),
    ).toHaveLength(0)
  })
})
