import { createRenderer, h, markRaw, nextTick, shallowReactive, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import HeadmanHomeScreen from './HeadmanHomeScreen.vue'
import type { HeadmanRequestsApi } from '../headman-requests/headman-requests-client'
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
function report(id: number, marked: boolean, groupId = 7): HeadmanJournalReport {
  return {
    lessonId: id,
    groupId,
    subjectId: 11,
    lessonDate: today,
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
      excuseType: null,
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
  live = false,
  extra: Partial<InstanceType<typeof HeadmanHomeScreen>['$props']> = {},
) {
  vi.useFakeTimers({ toFake: live ? ['Date', 'setInterval', 'clearInterval'] : ['Date'] })
  vi.setSystemTime(new Date('2026-10-15T06:00:00Z'))
  const props = shallowReactive({ api, groupId: 7, selectedDate: today, offline: false, ...extra })
  const root = new Element('root')
  const app = renderer.createApp({ render: () => h(HeadmanHomeScreen, props) })
  app.mount(root)
  apps.push(app)
  return { root, props }
}

describe('Headman ongoing summary authority', () => {
  it('keeps full current-lesson fractions while another lesson and a past date are selected', async () => {
    const listLessons = vi.fn(async (_group: number, date: string) =>
      date === today ? [lesson(1, 'ACTIVE'), lesson(2, 'PLANNED')] : [lesson(3, 'CLOSED', date)],
    )
    const api = {
      listLessons,
      getLessonAttendance: vi.fn(async (id: number) => report(id, id === 1)),
      writable: false,
    } as unknown as HeadmanJournalApi
    const { root, props } = mount(api)
    await flush()
    expect(byClass(root, 'headman-home__ongoing')).toBeDefined()
    expect(text(byClass(root, 'headman-home__ongoing-present'))).toContain('1 из 4')
    expect(text(byClass(root, 'headman-home__ongoing-excused'))).toContain('1 из 4')
    const next = all(root, (node) => node.props['aria-label'] === 'Следующая пара')[0]!
    ;(next.props.onClick as () => void)()
    await flush()
    expect(
      all(byClass(root, 'headman-home__metrics')!, (node) => node.tag === 'strong')[0]?.text,
    ).toBe('0')
    expect(text(byClass(root, 'headman-home__excused'))).toContain('Уважительных причин нет')
    expect(text(byClass(root, 'headman-home__ongoing-present'))).toContain('1 из 4')
    expect(text(byClass(root, 'headman-home__ongoing-excused'))).toContain('1 из 4')
    props.selectedDate = '2026-10-14'
    await flush()
    expect(text(byClass(root, 'headman-home__ongoing-present'))).toContain('1 из 4')
    expect(byClass(root, 'headman-home')?.props['data-lesson-id']).toBe(3)
    expect(listLessons.mock.calls.some(([, date]) => date === '2026-10-14')).toBe(true)
  })

  it('keeps an unknown current status unavailable until an authoritative empty day is read', async () => {
    let uncertain = true
    const api = {
      listLessons: vi.fn(async () => (uncertain ? [lesson(2, 'UNSUPPORTED')] : [])),
      getLessonAttendance: vi.fn(async (id: number) => report(id, false)),
      writable: false,
    } as unknown as HeadmanJournalApi
    const { root } = mount(api)
    await flush()
    expect(text(byClass(root, 'headman-home__ongoing'))).toContain('Сводка недоступна')
    expect(text(byClass(root, 'headman-home__ongoing'))).not.toContain('Сейчас пары нет')
    uncertain = false
    const retry = all(
      root,
      (node) => node.tag === 'button' && text(node).includes('Повторить сводку'),
    )[0]!
    ;(retry.props.onClick as () => void)()
    await flush()
    expect(text(byClass(root, 'headman-home__ongoing'))).toContain('Сейчас пары нет')
    expect(text(byClass(root, 'headman-home__ongoing'))).not.toContain('0 из')
  })

  it('distinguishes authoritative no-current from failure and offline without a zero fraction', async () => {
    let unavailable = false
    const api = {
      listLessons: vi.fn(async () => {
        if (unavailable) throw new Error('Synthetic schedule unavailable')
        return [lesson(2, 'PLANNED')]
      }),
      getLessonAttendance: vi.fn(async (id: number) => report(id, false)),
      writable: false,
    } as unknown as HeadmanJournalApi
    const { root, props } = mount(api)
    await flush()
    expect(text(byClass(root, 'headman-home__ongoing'))).toContain('Сейчас пары нет')
    expect(text(byClass(root, 'headman-home__ongoing'))).not.toContain('0 из')
    props.offline = true
    await flush()
    expect(text(byClass(root, 'headman-home__ongoing'))).toContain('Нет подключения')
    expect(text(byClass(root, 'headman-home__ongoing'))).not.toContain('Сейчас пары нет')
    unavailable = true
    props.offline = false
    await flush()
    expect(text(byClass(root, 'headman-home__ongoing'))).toContain('Сводка недоступна')
    expect(text(byClass(root, 'headman-home__ongoing'))).not.toContain('0 из')
  })

  it('discards a late old-group report and rejects a report with foreign identity', async () => {
    let resolveOld!: (value: HeadmanJournalReport) => void
    const delayed = new Promise<HeadmanJournalReport>((resolve) => {
      resolveOld = resolve
    })
    const api = {
      listLessons: vi.fn(async (group: number) => [lesson(1, 'ACTIVE', today, group)]),
      getLessonAttendance: vi.fn(() => delayed),
      writable: false,
    } as unknown as HeadmanJournalApi
    const { root, props } = mount(api)
    await flush()
    expect(text(byClass(root, 'headman-home__ongoing'))).not.toContain('0 из')
    props.groupId = 8
    const nextApi = {
      listLessons: vi.fn(async () => [lesson(1, 'ACTIVE', today, 8)]),
      getLessonAttendance: vi.fn(async () => report(1, true, 99)),
      writable: false,
    } as unknown as HeadmanJournalApi
    props.api = nextApi
    await flush()
    expect(text(byClass(root, 'headman-home__ongoing'))).not.toContain('1 из 4')
    resolveOld(report(1, true))
    await flush()
    expect(text(byClass(root, 'headman-home__ongoing'))).toContain('Сводка недоступна')
    expect(text(byClass(root, 'headman-home__ongoing'))).not.toContain('1 из 4')
  })

  it('refreshes changed counts in the foreground, coalesces slow reads and pauses while hidden', async () => {
    const document = Object.assign(new EventTarget(), { visibilityState: 'visible' })
    vi.stubGlobal('document', document)
    vi.stubGlobal('window', new EventTarget())
    vi.stubGlobal('navigator', { onLine: true })
    let present = true
    let resolveSchedule: ((value: HeadmanJournalLesson[]) => void) | undefined
    let delaySchedule = false
    const listLessons = vi.fn(async () => {
      if (delaySchedule)
        return new Promise<HeadmanJournalLesson[]>((resolve) => {
          resolveSchedule = resolve
        })
      return [lesson(1, 'ACTIVE'), lesson(2, 'PLANNED')]
    })
    const api = {
      listLessons,
      getLessonAttendance: vi.fn(async (id: number) => {
        const value = report(id, id === 1)
        return present || id !== 1
          ? value
          : {
              ...value,
              entries: value.entries.map((entry) =>
                entry.userId === 1 ? { ...entry, status: 'ABSENT' as const } : entry,
              ),
            }
      }),
      writable: false,
    } as unknown as HeadmanJournalApi
    const { root } = mount(api, true)
    await flush()
    const next = all(root, (node) => node.props['aria-label'] === 'Следующая пара')[0]!
    ;(next.props.onClick as () => void)()
    await flush()
    present = false
    await vi.advanceTimersByTimeAsync(30000)
    await flush()
    expect(text(byClass(root, 'headman-home__ongoing-present'))).toContain('0 из 4')
    expect(text(byClass(root, 'headman-home__ongoing-excused'))).toContain('1 из 4')
    expect(byClass(root, 'headman-home')?.props['data-lesson-id']).toBe(2)
    delaySchedule = true
    const before = listLessons.mock.calls.length
    await vi.advanceTimersByTimeAsync(60000)
    expect(listLessons).toHaveBeenCalledTimes(before + 1)
    delaySchedule = false
    resolveSchedule!([lesson(1, 'ACTIVE'), lesson(2, 'PLANNED')])
    await flush()
    expect(listLessons).toHaveBeenCalledTimes(before + 2)
    document.visibilityState = 'hidden'
    document.dispatchEvent(new Event('visibilitychange'))
    const hiddenCalls = listLessons.mock.calls.length
    await vi.advanceTimersByTimeAsync(60000)
    expect(listLessons).toHaveBeenCalledTimes(hiddenCalls)
    expect(text(byClass(root, 'headman-home__ongoing-present'))).not.toContain('0 из 4')
    present = true
    document.visibilityState = 'visible'
    document.dispatchEvent(new Event('visibilitychange'))
    await flush()
    expect(text(byClass(root, 'headman-home__ongoing-present'))).toContain('1 из 4')
  })
})

function homeApi(): HeadmanJournalApi {
  return {
    listLessons: vi.fn(async () => [lesson(1, 'ACTIVE')]),
    getLessonAttendance: vi.fn(async () => report(1, true)),
    writable: false,
  } as unknown as HeadmanJournalApi
}
function requestPage(totalElements: number) {
  return { content: [], totalElements, totalPages: totalElements ? 1 : 0, page: 0, size: 1 }
}

describe('Home request bindings', () => {
  it('renders authoritative typed totals with Today homework read-only and emits each existing filter', async () => {
    const list = vi.fn(async (query: { type: string }) =>
      requestPage(query.type === 'EXCUSE' ? 7 : 0),
    )
    const requestsApi = { list } as unknown as HeadmanRequestsApi
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
    const opened = vi.fn()
    const { root } = mount(homeApi(), false, { requestsApi, homeworkApi, onOpenRequests: opened })
    await flush()
    const region = byClass(root, 'headman-home__requests')!
    expect(region).toBeDefined()
    expect(region.props['data-state']).toBe('ready')
    for (const [kind, count] of [
      ['EXCUSE', '7'],
      ['LATE_CHECKIN', '0'],
    ] as const) {
      const button = all(region, (node) => node.props['data-request-kind'] === kind)[0]!
      expect(text(button)).toContain(count)
      ;(button.props.onClick as () => void)()
      expect(opened).toHaveBeenCalledWith(kind)
      expect(list).toHaveBeenCalledWith({ bucket: 'OPEN', type: kind, page: 0, size: 1 })
    }
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

  it('never queries without MANAGE_EXCUSES and clears known counts offline or after failure', async () => {
    const list = vi.fn(async () => requestPage(3))
    const { root, props } = mount(homeApi(), false, {
      requestsApi: { list } as unknown as HeadmanRequestsApi,
      assistantPermissions: ['VIEW_STATS'],
    })
    await flush()
    expect(byClass(root, 'headman-home__requests')).toBeUndefined()
    expect(list).not.toHaveBeenCalled()
    props.assistantPermissions = ['MANAGE_EXCUSES']
    await flush()
    expect(byClass(root, 'headman-home__requests')?.props['data-state']).toBe('ready')
    props.offline = true
    await flush()
    const offline = byClass(root, 'headman-home__requests')!
    expect(text(offline)).toContain('Число заявок неизвестно')
    expect(all(offline, (node) => node.tag === 'strong').map(text)).toEqual(['—', '—'])
    list.mockRejectedValue(new Error('Synthetic denied read'))
    props.offline = false
    await flush()
    expect(byClass(root, 'headman-home__requests')?.props['data-state']).toBe('unavailable')
    expect(
      all(byClass(root, 'headman-home__requests')!, (node) => node.tag === 'strong').map(text),
    ).toEqual(['—', '—'])
  })

  it('discards a slow old-owner result and refreshes only the current visible online owner', async () => {
    const document = Object.assign(new EventTarget(), { visibilityState: 'visible' })
    vi.stubGlobal('document', document)
    vi.stubGlobal('window', new EventTarget())
    vi.stubGlobal('navigator', { onLine: true })
    let resolve!: (value: ReturnType<typeof requestPage>) => void
    const pending = new Promise<ReturnType<typeof requestPage>>((done) => {
      resolve = done
    })
    const oldList = vi.fn(() => pending)
    const { root, props } = mount(homeApi(), true, {
      requestsApi: { list: oldList } as unknown as HeadmanRequestsApi,
    })
    await flush()
    let total = 4
    const list = vi.fn(async () => requestPage(total))
    props.requestsApi = { list } as unknown as HeadmanRequestsApi
    props.groupId = 8
    resolve(requestPage(99))
    await flush()
    expect(text(byClass(root, 'headman-home__requests'))).not.toContain('99')
    expect(list).toHaveBeenCalledTimes(2)
    expect(
      all(byClass(root, 'headman-home__requests')!, (node) => node.tag === 'strong').map(text),
    ).toEqual(['4', '4'])
    total = 5
    await vi.advanceTimersByTimeAsync(30000)
    await flush()
    expect(
      all(byClass(root, 'headman-home__requests')!, (node) => node.tag === 'strong').map(text),
    ).toEqual(['5', '5'])
    document.visibilityState = 'hidden'
    document.dispatchEvent(new Event('visibilitychange'))
    const before = list.mock.calls.length
    await vi.advanceTimersByTimeAsync(60000)
    expect(list).toHaveBeenCalledTimes(before)
    expect(
      all(byClass(root, 'headman-home__requests')!, (node) => node.tag === 'strong').map(text),
    ).toEqual(['—', '—'])
    total = 6
    document.visibilityState = 'visible'
    document.dispatchEvent(new Event('visibilitychange'))
    await flush()
    expect(
      all(byClass(root, 'headman-home__requests')!, (node) => node.tag === 'strong').map(text),
    ).toEqual(['6', '6'])
  })
})
