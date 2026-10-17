import { afterEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, h, markRaw, reactive, nextTick, type App, type Component } from 'vue'
import HeadmanJournalScreen from './HeadmanJournalScreen.vue'
import HeadmanHomeScreen from '../headman-home/HeadmanHomeScreen.vue'
import type {
  HeadmanJournalApi,
  HeadmanJournalLesson,
  HeadmanJournalReport,
  HeadmanJournalRosterEntry,
} from './headman-journal-client'
import { journalScopeCurrent, type JournalMutationScope } from './headman-journal-operation'
import type {
  ReportDownloadPort,
  ReportDownloadTicketRequest,
} from '../../shared/report-download-client'
import { ReportDownloadClient } from '../../shared/report-download-client'

describe('journal destructive confirmation and late ACK ownership', () => {
  const scope: JournalMutationScope = { api: {}, groupId: 4, lessonId: 30, generation: 1 }
  it('allows the unchanged captured account/group/lesson generation', () => {
    expect(journalScopeCurrent(scope, { ...scope })).toBe(true)
  })
  it.each([
    ['account', { ...scope, api: {} }],
    ['group', { ...scope, groupId: 5 }],
    ['lesson', { ...scope, lessonId: 31 }],
    ['rights or offline cycle', { ...scope, generation: 2 }],
    ['disconnected owner', null],
  ])('rejects an old confirmation and ACK after changing %s', (_label, next) => {
    expect(journalScopeCurrent(scope, next)).toBe(false)
  })
  it('does not publish a late destructive ACK into the newly selected lesson', async () => {
    let current: JournalMutationScope | null = { ...scope }
    let finish!: () => void
    const ack = new Promise<void>((resolve) => {
      finish = resolve
    })
    let visibleMark = 'EXCUSED'
    const run = async () => {
      await ack
      if (journalScopeCurrent(scope, current)) visibleMark = 'EMPTY'
    }
    const pending = run()
    current = { ...scope, lessonId: 31, generation: 2 }
    finish()
    await pending
    expect(visibleMark).toBe('EXCUSED')
  })
})

// Same browser-free Vue host seam as the existing headman screen tests; the SFCs are real.
class JournalNode {
  children: JournalNode[] = []
  parent: JournalNode | null = null
  props: Record<string, unknown> = {}
  text = ''
  value: unknown = ''
  selected = false
  selectedIndex = -1
  checked = false
  constructor(readonly tag: string) {
    markRaw(this)
  }
  focus(): void {
    focusJournalNode(this)
  }
  close(): void {
    this.props.open = false
  }
  showModal(): void {
    this.props.open = true
  }
  addEventListener(): void {}
  getRootNode() {
    return { activeElement: null }
  }
  get tagName() {
    return this.tag.toUpperCase()
  }
  get options() {
    return this.children.filter((node) => node.tag === 'option')
  }
}
let journalFocusedNode: JournalNode | null = null
function focusJournalNode(node: JournalNode): void {
  if (journalFocusedNode) journalFocusedNode.props.focused = false
  journalFocusedNode = node
  node.props.focused = true
}
const journalRenderer = createRenderer<JournalNode, JournalNode>({
  createElement: (tag) => new JournalNode(tag),
  createText: (text) => Object.assign(new JournalNode('#text'), { text }),
  createComment: () => new JournalNode('#comment'),
  setText: (node, text) => {
    node.text = text
  },
  setElementText: (node, text) => {
    node.text = text
    node.children = []
  },
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _before, value) => {
    node.props[key] = value
    if (key === 'value') node.value = value
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
function journalNodes(root: JournalNode): JournalNode[] {
  return [root, ...root.children.flatMap(journalNodes)]
}
function journalButton(root: JournalNode, label: string): JournalNode | undefined {
  return journalNodes(root).find(
    (node) => node.tag === 'button' && node.props['aria-label'] === label,
  )
}
function journalClick(node: JournalNode | undefined): void {
  expect(node).toBeDefined()
  ;(node!.props.onClick as () => void)()
}
const journalApps: App[] = []
afterEach(() => {
  journalApps.splice(0).forEach((app) => app.unmount())
  vi.unstubAllGlobals()
})
function mountJournalScreen(component: Component, props: Record<string, unknown>): JournalNode {
  vi.stubGlobal('HTMLElement', class {})
  vi.stubGlobal('HTMLButtonElement', class {})
  vi.stubGlobal('document', {
    activeElement: null,
    addEventListener() {},
    removeEventListener() {},
  })
  const root = new JournalNode('root')
  const app = journalRenderer.createApp({ render: () => h(component, props) })
  app.mount(root)
  journalApps.push(app)
  return root
}
function journalFixture(
  future = false,
  initialStatus: 'PRESENT' | 'ABSENT' | 'EXCUSED' = 'PRESENT',
) {
  const date = future ? '2099-09-08' : '2026-09-07'
  const lesson: HeadmanJournalLesson = {
    id: 30,
    groupId: 4,
    subjectId: 5,
    date,
    status: future ? 'PLANNED' : 'ACTIVE',
    lessonNumber: 1,
    startTime: '08:30',
    endTime: '09:50',
    room: null,
    lessonType: 'LECTURE',
    blockedByHeadman: false,
    occurrenceRevision: '1',
    current: true,
    transferOperationId: null,
    transferState: null,
  }
  let entry: HeadmanJournalRosterEntry = {
    userId: 7,
    displayName: 'Синтетический студент',
    status: future ? 'EMPTY' : initialStatus,
    symbol: future ? null : '+',
    markRevision: future ? null : 'revision-a',
    approvedTicket: null,
    source: future ? null : 'headman',
    excuseReason: null,
    excuseType: null,
    comment: null,
    attachmentId: null,
    attachmentName: null,
    attachmentContentType: null,
    attachmentSize: null,
    editable: !future,
  }
  const report = (): HeadmanJournalReport => ({
    lessonId: 30,
    groupId: 4,
    subjectId: 5,
    lessonDate: date,
    semesterId: 2,
    lessonStatus: lesson.status,
    editable: !future,
    entries: [{ ...entry }],
  })
  const clear = vi.fn(async () => {
    entry = { ...entry, status: 'EMPTY', symbol: null, markRevision: null, source: null }
  })
  const api = {
    writable: true,
    listLessons: vi.fn(async () => [lesson]),
    getLessonAttendance: vi.fn(async () => report()),
    clear,
    mark: vi.fn(),
    setHeadmanBlock: vi.fn(async (_id: number, blocked: boolean) => ({
      ...lesson,
      blockedByHeadman: blocked,
    })),
  } as unknown as HeadmanJournalApi
  return { api, lesson, clear, report }
}
describe('accepted journal production component controls', () => {
  it('preserves the current week/format selection across same-owner reconnect even if the catalogue refresh fails', async () => {
    const fixture = journalFixture()
    const options = {
      semesterId: 2,
      semesterName: 'Семестр',
      semesterDateFrom: '2026-09-01',
      semesterDateTo: '2026-12-31',
      weeks: ['2026-09-07', '2026-09-14'].map((weekStart, index) => ({
        weekStart,
        weekEnd: index ? '2026-09-20' : '2026-09-13',
        weekOfSemester: index + 1,
        isoWeek: 37 + index,
        label: `Неделя ${index + 1}`,
        current: false,
      })),
      formats: ['docx', 'pdf'].map((code) => ({
        code,
        label: code,
        extension: code,
        contentType: `application/${code}`,
      })),
    }
    const optionsRead = vi.fn(async () => options)
    const events = new Map<string, () => void>()
    vi.stubGlobal('window', {
      addEventListener: (event: string, listener: () => void) => events.set(event, listener),
      removeEventListener: (event: string) => events.delete(event),
    })
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: { ...fixture.api, getWeeklyExportOptions: optionsRead },
      groupId: 4,
      initialDate: fixture.lesson.date,
    })
    await vi.waitFor(() =>
      expect(journalNodes(root).filter((node) => node.props.type === 'checkbox')).toHaveLength(2),
    )
    const checkboxes = () => journalNodes(root).filter((node) => node.props.type === 'checkbox')
    ;(checkboxes()[0]!.props.onChange as (event: unknown) => void)({ target: { checked: false } })
    ;(checkboxes()[1]!.props.onChange as (event: unknown) => void)({ target: { checked: true } })
    const select = journalNodes(root).find((node) => node.tag === 'select')!
    ;(select.props['onUpdate:modelValue'] as (value: string) => void)('pdf')
    await nextTick()
    expect(checkboxes().map((node) => node.props.checked)).toEqual([false, true])
    expect(select.selectedIndex).toBe(1)
    optionsRead.mockRejectedValueOnce(new Error('catalogue refresh failed'))
    events.get('online')?.()
    await vi.waitFor(() => expect(optionsRead).toHaveBeenCalledTimes(2))
    await nextTick()
    expect(checkboxes().map((node) => node.props.checked)).toEqual([false, true])
    expect(journalNodes(root).find((node) => node.tag === 'select')?.selectedIndex).toBe(1)
  })

  it.each(['known-job', 'unknown-acceptance', 'catalogue-error'])(
    'resumes the exact accepted CURRENT intent after week rollover (%s), without replacing the visible selection',
    async (mode) => {
      const fixture = journalFixture()
      const request: ReportDownloadTicketRequest = {
        kind: 'HEADMAN_WEEKLY_CURRENT',
        headmanWeeklyCurrent: { weekStart: '2026-09-07', format: 'pdf' },
      }
      const values = new Map<string, string>()
      const recoveryStorage = {
        getItem: (key: string) => values.get(key) ?? null,
        setItem: (key: string, value: string) => {
          values.set(key, value)
        },
        removeItem: (key: string) => {
          values.delete(key)
        },
      }
      const jobId = '00000000-0000-4000-8000-000000000001'
      const job = {
        jobId,
        state: 'ready',
        suggestedFilename: 'weekly.pdf',
        errorCode: null,
        expiresAt: new Date(Date.now() + 60_000).toISOString(),
      }
      const firstFetch = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('response lost'))
      if (mode !== 'unknown-acceptance') firstFetch.mockResolvedValueOnce(Response.json(job))
      const create = (fetcher: typeof fetch) =>
        new ReportDownloadClient({
          fetcher,
          assertCurrent: () => undefined,
          accessToken: () => null,
          recoveryScope: () => 'same-user/session/HEADMAN',
          recoveryStorage,
        })
      await expect(create(firstFetch).issueTicket(request)).rejects.toThrow('response lost')
      const resumedFetch = vi
        .fn<typeof fetch>()
        .mockResolvedValueOnce(Response.json(job))
        .mockResolvedValueOnce(
          Response.json({
            downloadPath: `/api/report-download/${'a'.repeat(43)}`,
            expiresAt: job.expiresAt,
            suggestedFilename: 'weekly.pdf',
          }),
        )
      const client = create(resumedFetch)
      const download = vi.fn<ReportDownloadPort['download']>(async (saved, current, onState) => {
        await client.issueTicket(saved, onState, current)
        return 'cancelled'
      })
      const options = {
        semesterId: 2,
        semesterName: 'Семестр',
        semesterDateFrom: '2026-09-01',
        semesterDateTo: '2026-12-31',
        weeks: [
          {
            weekOfSemester: 1,
            isoWeek: 37,
            label: 'Прошлая неделя',
            weekStart: '2026-09-07',
            weekEnd: '2026-09-13',
            current: false,
          },
        ],
        // The current visible catalogue need not include the accepted format.
        formats: [
          { code: 'docx', label: 'Word', extension: 'docx', contentType: 'application/docx' },
        ],
      }
      const optionsRead = vi.fn(async () => options)
      if (mode === 'catalogue-error')
        optionsRead.mockRejectedValueOnce(new Error('catalogue unavailable'))
      const root = mountJournalScreen(HeadmanJournalScreen, {
        api: { ...fixture.api, getWeeklyExportOptions: optionsRead },
        reportDownload: { pendingRequests: () => client.pendingRequests(), download },
        groupId: 4,
        initialDate: fixture.lesson.date,
      })
      await vi.waitFor(() => expect(resumedFetch).toHaveBeenCalledTimes(2))
      expect(download.mock.calls[0]?.[0]).toEqual(request)
      if (mode === 'unknown-acceptance') {
        expect(resumedFetch.mock.calls[0]?.[0]).toBe('/api/auth/export-jobs')
        expect(JSON.parse(String(resumedFetch.mock.calls[0]?.[1]?.body))).toEqual(
          JSON.parse(String(firstFetch.mock.calls[0]?.[1]?.body)),
        )
      } else {
        expect(resumedFetch.mock.calls[0]?.[0]).toBe(`/api/auth/export-jobs/${jobId}`)
        expect(resumedFetch.mock.calls.some(([path]) => path === '/api/auth/export-jobs')).toBe(
          false,
        )
      }
      expect(client.pendingRequests()).toEqual([request])
      await nextTick()
      if (mode !== 'catalogue-error') {
        const select = journalNodes(root).find((node) => node.tag === 'select')
        expect(select?.props['onUpdate:modelValue']).toBeDefined()
        expect(select?.selectedIndex).toBe(0)
      }
    },
  )

  it('restores a weekly request on return and reconnect while the journal stays usable, and ignores its old view result', async () => {
    const fixture = journalFixture()
    const request: ReportDownloadTicketRequest = {
      kind: 'HEADMAN_WEEKLY_SELECTED',
      headmanWeeklySelected: { weekStarts: ['2026-09-07', '2026-09-14'], format: 'docx' },
    }
    const options = {
      semesterId: 2,
      semesterName: 'Семестр',
      semesterDateFrom: '2026-09-01',
      semesterDateTo: '2026-12-31',
      weeks: ['2026-09-07', '2026-09-14'].map((weekStart, index) => ({
        weekStart,
        weekEnd: `2026-09-${index === 0 ? '13' : '20'}`,
        weekOfSemester: index + 1,
        isoWeek: 37 + index,
        label: `Неделя ${index + 1}`,
        current: false,
      })),
      formats: [
        {
          code: 'docx',
          label: 'Word',
          extension: 'docx',
          contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        },
      ],
    }
    const api = { ...fixture.api, getWeeklyExportOptions: vi.fn(async () => options) }
    const events = new Map<string, () => void>()
    vi.stubGlobal('window', {
      addEventListener: (event: string, listener: () => void) => events.set(event, listener),
      removeEventListener: (event: string) => events.delete(event),
    })
    let finish!: (result: 'accepted') => void
    const download = vi.fn<ReportDownloadPort['download']>((_request, _current, onState) => {
      onState?.('preparing')
      return new Promise((resolve) => {
        finish = resolve
      })
    })
    const port: ReportDownloadPort = { pendingRequests: () => [request], download }
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api,
      reportDownload: port,
      groupId: 4,
      initialDate: fixture.lesson.date,
    })
    await vi.waitFor(() => expect(download).toHaveBeenCalledTimes(1))
    expect(download.mock.calls[0]?.[0]).toEqual(request)
    await nextTick()
    const journal = journalNodes(root).find((node) => node.props.class === 'headman-journal')
    await vi.waitFor(() => expect(journal?.props['aria-busy']).toBe(false))
    expect(
      journalNodes(root).some(
        (node) => node.props.role === 'status' && node.text.includes('Файл готовится'),
      ),
    ).toBe(true)
    expect(journalButton(root, 'Следующая пара или день')?.props.disabled).not.toBe(true)
    // The recovered job does not open a modal or lock host navigation.
    expect(
      journalNodes(root)
        .filter((node) => node.tag === 'dialog')
        .some((node) => node.props.open === true),
    ).toBe(false)
    journalApps.pop()!.unmount()
    expect(download.mock.calls[0]?.[1]()).toBe(false)
    finish('accepted')
    await nextTick()
    expect(events.has('online')).toBe(false)

    // A new mounted view rediscovers the same durable request; an online signal
    // also rechecks it after a transport failure, without needing a socket event.
    download.mockRejectedValueOnce(new DOMException('request timed out', 'AbortError'))
    const returnedRoot = mountJournalScreen(HeadmanJournalScreen, {
      api,
      reportDownload: port,
      groupId: 4,
      initialDate: fixture.lesson.date,
    })
    await vi.waitFor(() => expect(download).toHaveBeenCalledTimes(2))
    await vi.waitFor(() =>
      expect(
        journalNodes(returnedRoot).some(
          (node) =>
            node.props.role === 'alert' && node.text.includes('Не удалось связаться с сервером'),
        ),
      ).toBe(true),
    )
    await nextTick()
    events.get('online')?.()
    await vi.waitFor(() => expect(download).toHaveBeenCalledTimes(3))
    expect(download.mock.calls[2]?.[0]).toEqual(request)
    finish('accepted')
  })

  it('opens the filled chooser without clearing, then clears only the selected choice', async () => {
    const fixture = journalFixture()
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: fixture.api,
      groupId: 4,
      initialDate: fixture.lesson.date,
    })
    await vi.waitFor(() =>
      expect(
        journalButton(root, 'Был: Синтетический студент. Открыть выбор отметки'),
      ).toBeDefined(),
    )
    const edit = journalButton(root, 'Изменить отметку: Синтетический студент')
    expect(edit, 'filled mark exposes the accepted edit action').toBeDefined()
    journalClick(edit)
    await vi.waitFor(() =>
      expect(journalButton(root, 'Не был: Синтетический студент')).toBeDefined(),
    )
    expect(journalButton(root, 'Был: Синтетический студент')?.props.focused).toBe(true)
    expect(fixture.clear).not.toHaveBeenCalled()
    expect(fixture.report().entries[0]?.status).toBe('PRESENT')
    journalClick(journalButton(root, 'Был: Синтетический студент'))
    await vi.waitFor(() => expect(fixture.report().entries[0]?.status).toBe('EMPTY'))
    expect(fixture.clear).toHaveBeenCalledTimes(1)
    expect(fixture.clear).toHaveBeenCalledWith(30, 7, {
      expectedStatus: 'PRESENT',
      expectedRevision: 'revision-a',
    })
    await vi.waitFor(() => expect(journalButton(root, 'Был: Синтетический студент')).toBeDefined())
  })
  it('clears the repeated EXCUSED choice immediately without opening an attachment dialog', async () => {
    const fixture = journalFixture(false, 'EXCUSED')
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: fixture.api,
      groupId: 4,
      initialDate: fixture.lesson.date,
    })
    await vi.waitFor(() =>
      expect(journalButton(root, 'Изменить отметку: Синтетический студент')).toBeDefined(),
    )
    journalClick(journalButton(root, 'Изменить отметку: Синтетический студент'))
    await vi.waitFor(() =>
      expect(journalButton(root, 'Уважительная причина: Синтетический студент')).toBeDefined(),
    )
    expect(fixture.clear).not.toHaveBeenCalled()
    journalClick(journalButton(root, 'Уважительная причина: Синтетический студент'))
    await vi.waitFor(() => expect(fixture.report().entries[0]?.status).toBe('EMPTY'))
    expect(fixture.clear).toHaveBeenCalledTimes(1)
    expect(fixture.clear).toHaveBeenCalledWith(30, 7, {
      expectedStatus: 'EXCUSED',
      expectedRevision: 'revision-a',
    })
    expect(journalNodes(root).some((node) => node.tag === 'dialog' && node.props.open)).toBe(false)
  })
  it('retains the MANAGE_EXCUSES admission before an EXCUSED choice can clear', async () => {
    const fixture = journalFixture(false, 'EXCUSED')
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: fixture.api,
      groupId: 4,
      initialDate: fixture.lesson.date,
      assistantPermissions: ['MARK_ATTENDANCE'],
    })
    await vi.waitFor(() =>
      expect(journalButton(root, 'Изменить отметку: Синтетический студент')).toBeDefined(),
    )
    const edit = journalButton(root, 'Изменить отметку: Синтетический студент')
    expect(edit?.props.disabled).toBe(true)
    journalClick(edit)
    await nextTick()
    expect(journalButton(root, 'Уважительная причина: Синтетический студент') === undefined).toBe(
      true,
    )
    expect(fixture.clear).not.toHaveBeenCalled()
    expect(fixture.report().entries[0]?.status).toBe('EXCUSED')
  })
  it('does not render mark choices before the lesson starts', async () => {
    const fixture = journalFixture(true)
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: fixture.api,
      groupId: 4,
      initialDate: fixture.lesson.date,
    })
    await vi.waitFor(() =>
      expect(journalNodes(root).some((node) => node.props['data-user-id'] === 7)).toBe(true),
    )
    expect(
      journalButton(root, 'Был: Синтетический студент') === undefined,
      'future mark choices are absent, not disabled',
    ).toBe(true)
    expect(journalNodes(root).some((node) => node.text.includes('Пара ещё не началась'))).toBe(true)
    expect(fixture.clear).not.toHaveBeenCalled()
  })
  it('offers geo blocking on the loaded active main lesson', async () => {
    const fixture = journalFixture()
    const root = mountJournalScreen(HeadmanHomeScreen, {
      api: fixture.api,
      groupId: 4,
      selectedDate: fixture.lesson.date,
    })
    await vi.waitFor(() => expect(journalButton(root, 'Открыть учёт выбранной пары')).toBeDefined())
    expect(
      journalButton(root, 'Заблокировать геоотметки'),
      'active main lesson exposes its block action',
    ).toBeDefined()
    journalClick(journalButton(root, 'Заблокировать геоотметки'))
    await vi.waitFor(() =>
      expect(journalButton(root, 'Подтвердить блокировку геоотметок')).toBeDefined(),
    )
    expect(fixture.api.setHeadmanBlock).not.toHaveBeenCalled()
    const cancel = journalNodes(root).find(
      (node) => node.tag === 'button' && node.text.trim() === 'Отмена',
    )
    expect(cancel?.props.focused).toBe(true)
    const group = journalNodes(root).find(
      (node) =>
        node.props.role === 'group' &&
        node.props['aria-label'] === 'Подтвердить блокировку геоотметок',
    )
    expect(group).toBeDefined()
    const preventDefault = vi.fn()
    const stopPropagation = vi.fn()
    ;(group!.props.onKeydown as (event: unknown) => void)({
      key: 'Escape',
      preventDefault,
      stopPropagation,
    })
    await nextTick()
    expect(preventDefault).toHaveBeenCalledOnce()
    expect(stopPropagation).toHaveBeenCalledOnce()
    expect(journalButton(root, 'Подтвердить блокировку геоотметок') === undefined).toBe(true)
    expect(journalButton(root, 'Заблокировать геоотметки')?.props.focused).toBe(true)
    expect(fixture.api.setHeadmanBlock).not.toHaveBeenCalled()
    journalClick(journalButton(root, 'Заблокировать геоотметки'))
    await nextTick()
    journalClick(
      journalNodes(root).find((node) => node.tag === 'button' && node.text.trim() === 'Отмена'),
    )
    await nextTick()
    expect(journalButton(root, 'Заблокировать геоотметки')?.props.focused).toBe(true)
    journalClick(journalButton(root, 'Заблокировать геоотметки'))
    await nextTick()
    journalClick(journalButton(root, 'Подтвердить блокировку геоотметок'))
    await vi.waitFor(() =>
      expect(journalButton(root, 'Разблокировать геоотметки')?.props.focused).toBe(true),
    )
    expect(fixture.api.setHeadmanBlock).toHaveBeenCalledWith(30, true)
    journalClick(journalButton(root, 'Разблокировать геоотметки'))
    await vi.waitFor(() => expect(journalButton(root, 'Заблокировать геоотметки')).toBeDefined())
    expect(fixture.api.setHeadmanBlock).toHaveBeenLastCalledWith(30, false)
  })
  it('keeps a writable transport read-only in an archived owner', async () => {
    const fixture = journalFixture()
    const root = mountJournalScreen(HeadmanHomeScreen, {
      api: fixture.api,
      groupId: 4,
      selectedDate: fixture.lesson.date,
      readOnly: true,
    })
    await vi.waitFor(() => expect(journalButton(root, 'Открыть учёт выбранной пары')).toBeDefined())
    expect(journalButton(root, 'Заблокировать геоотметки') === undefined).toBe(true)
    expect(fixture.api.setHeadmanBlock).not.toHaveBeenCalled()
  })
  it('drops the old block confirmation and late ACK when the owner goes offline', async () => {
    const fixture = journalFixture()
    let finish!: (lesson: HeadmanJournalLesson) => void
    const pending = new Promise<HeadmanJournalLesson>((resolve) => {
      finish = resolve
    })
    vi.mocked(fixture.api.setHeadmanBlock).mockReturnValue(pending)
    const props = reactive({
      api: fixture.api,
      groupId: 4,
      selectedDate: fixture.lesson.date,
      offline: false,
    })
    const root = mountJournalScreen(HeadmanHomeScreen, props)
    await vi.waitFor(() => expect(journalButton(root, 'Заблокировать геоотметки')).toBeDefined())
    journalClick(journalButton(root, 'Заблокировать геоотметки'))
    await nextTick()
    journalClick(journalButton(root, 'Подтвердить блокировку геоотметок'))
    props.offline = true
    await nextTick()
    finish({ ...fixture.lesson, blockedByHeadman: true })
    await nextTick()
    await nextTick()
    expect(journalButton(root, 'Разблокировать геоотметки') === undefined).toBe(true)
    expect(journalButton(root, 'Подтвердить блокировку геоотметок') === undefined).toBe(true)
    expect(fixture.api.setHeadmanBlock).toHaveBeenCalledTimes(1)
  })
})
