import { afterEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, h, markRaw, reactive, nextTick, type App, type Component } from 'vue'
import HeadmanJournalScreen from './HeadmanJournalScreen.vue'
import HeadmanHomeScreen from '../headman-home/HeadmanHomeScreen.vue'
import { moscowDate } from '../../domain/homework'
import {
  HeadmanJournalApiError,
  type HeadmanJournalApi,
  type HeadmanJournalMarkCommand,
  type HeadmanJournalLesson,
  type HeadmanJournalReport,
  type HeadmanJournalRosterEntry,
} from './headman-journal-client'
import { journalScopeCurrent, type JournalMutationScope } from './headman-journal-operation'
import type {
  ReportDownloadPort,
  ReportDownloadTicketRequest,
} from '../../shared/report-download-client'
import { ReportDownloadClient, ReportDownloadTicketError } from '../../shared/report-download-client'

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
    const original = journalFixture()
    Object.assign(original.api, {
      getWeeklyExportOptions: async () => ({ semesterId: 2, weeks: [], formats: [] }),
    })
    let finish!: () => void
    original.clear.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve
        }),
    )
    const next = journalFixture(false, 'ABSENT')
    Object.assign(next.api, {
      getWeeklyExportOptions: async () => ({ semesterId: 2, weeks: [], formats: [] }),
    })
    const props = reactive({
      api: markRaw(original.api),
      groupId: 4,
      initialDate: original.lesson.date,
    })
    const errors = vi.fn()
    const root = mountJournalScreen(HeadmanJournalScreen, Object.assign(props, { onError: errors }))
    await vi.waitFor(() =>
      expect(journalButton(root, 'Был: Синтетический студент')?.props['aria-pressed']).toBe(true),
    )
    journalClick(journalButton(root, 'Был: Синтетический студент'))
    expect(original.clear).toHaveBeenCalledWith(30, 7, {
      expectedStatus: 'PRESENT',
      expectedRevision: 'revision-a',
    })
    props.api = markRaw(next.api)
    await vi.waitFor(() =>
      expect(journalButton(root, 'Не был: Синтетический студент')?.props['aria-pressed']).toBe(
        true,
      ),
    )
    const readsBefore = vi.mocked(next.api.getLessonAttendance).mock.calls.length
    finish()
    await nextTick()
    await Promise.resolve()
    await nextTick()
    expect(journalButton(root, 'Не был: Синтетический студент')?.props['aria-pressed']).toBe(true)
    expect(journalButton(root, 'Был: Синтетический студент')?.props.disabled).not.toBe(true)
    expect(vi.mocked(next.api.getLessonAttendance).mock.calls.length).toBe(readsBefore)
    expect(next.clear).not.toHaveBeenCalled()
    expect(errors).not.toHaveBeenCalled()
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
  querySelectorAll(selector: string): JournalNode[] {
    return selector === '[role="option"]'
      ? journalNodes(this).filter((node) => node.props.role === 'option')
      : []
  }
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
    (node) =>
      node.tag === 'button' &&
      (node.props['aria-label'] === label ||
        String(node.props['aria-label']).startsWith(label + '. ')),
  )
}
function journalFormat(root: JournalNode): string | undefined {
  const button = journalButton(root, 'Формат файла')
  return button && journalNodes(button).map((node) => node.text).join('').trim()
}
function journalClick(node: JournalNode | undefined): void {
  expect(node).toBeDefined()
  ;(node!.props.onClick as (event: { currentTarget: JournalNode }) => void)({
    currentTarget: node!,
  })
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
  date = future ? '2099-09-08' : '2026-09-07',
) {
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
    entries: [
      { ...entry },
      { ...entry, userId: 8, displayName: 'Иванов Иван', status: 'EMPTY', markRevision: null },
    ],
  })
  const clear = vi.fn(async () => {
    entry = { ...entry, status: 'EMPTY', symbol: null, markRevision: null, source: null }
  })
  const mark = vi.fn(
    async (lessonId: number, userId: number, command: HeadmanJournalMarkCommand) => {
      entry = {
        ...entry,
        status: command.status,
        markRevision: 'revision-b',
        excuseType: command.status === 'EXCUSED' ? command.excuseType : null,
      }
      return { status: command.status, lessonId, userId, timestamp: '2026-09-07T09:00:00Z' }
    },
  )
  const api = {
    writable: true,
    getWeeklyExportOptions: async () => ({ semesterId: 2, weeks: [], formats: [] }),
    listLessons: vi.fn(async () => [lesson]),
    getLessonAttendance: vi.fn(async () => report()),
    clear,
    mark,
    setHeadmanBlock: vi.fn(async (_id: number, blocked: boolean) => ({
      ...lesson,
      blockedByHeadman: blocked,
    })),
  } as unknown as HeadmanJournalApi
  return { api, lesson, clear, mark, report }
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
    const onError = vi.fn()
    const events = new Map<string, () => void>()
    vi.stubGlobal('window', {
      addEventListener: (event: string, listener: () => void) => events.set(event, listener),
      removeEventListener: (event: string) => events.delete(event),
    })
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: { ...fixture.api, getWeeklyExportOptions: optionsRead },
      groupId: 4,
      initialDate: fixture.lesson.date,
      onError,
    })
    await vi.waitFor(() =>
      expect(journalNodes(root).filter((node) => node.props.type === 'checkbox')).toHaveLength(2),
    )
    const checkboxes = () => journalNodes(root).filter((node) => node.props.type === 'checkbox')
    ;(checkboxes()[0]!.props.onChange as (event: unknown) => void)({ target: { checked: false } })
    ;(checkboxes()[1]!.props.onChange as (event: unknown) => void)({ target: { checked: true } })
    journalClick(journalButton(root, 'Формат файла'))
    await nextTick()
    journalClick(journalButton(root, 'pdf'))
    await nextTick()
    expect(checkboxes().map((node) => node.props.checked)).toEqual([false, true])
    expect(journalFormat(root)).toBe('pdf')
    optionsRead.mockRejectedValueOnce(new Error('catalogue refresh failed'))
    events.get('online')?.()
    await vi.waitFor(() => expect(optionsRead).toHaveBeenCalledTimes(2))
    await nextTick()
    expect(checkboxes().map((node) => node.props.checked)).toEqual([false, true])
    expect(journalFormat(root)).toBe('pdf')
    expect(onError).not.toHaveBeenCalled()
  })

  it('retries only the weekly catalogue even when a durable download is still pending', async () => {
    const fixture = journalFixture()
    const pending: ReportDownloadTicketRequest = {
      kind: 'HEADMAN_WEEKLY_CURRENT',
      headmanWeeklyCurrent: { weekStart: '2026-09-07', format: 'pdf' },
    }
    const getWeeklyExportOptions = vi.fn(async () => ({
      semesterId: 2,
      weeks: [],
      formats: [{ code: 'pdf', label: 'PDF', extension: 'pdf', contentType: 'application/pdf' }],
    })).mockRejectedValueOnce(new Error('Каталог временно недоступен'))
    const pendingRequests = vi.fn(() => [pending])
    const download = vi.fn<ReportDownloadPort['download']>().mockResolvedValue('cancelled')
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: { ...fixture.api, getWeeklyExportOptions },
      groupId: 4,
      initialDate: fixture.lesson.date,
      reportDownload: { pendingRequests, download },
    })
    await vi.waitFor(() => expect(download).toHaveBeenCalledTimes(1))
    expect(download.mock.calls[0]?.[0]).toEqual(pending)
    journalClick(journalNodes(root).find(
      (node) => node.tag === 'button' && node.text.trim() === 'Скачать журнал',
    ))
    const catalogueRetry = () => journalNodes(root).find(
      (node) => node.tag === 'button' && node.text.trim() === 'Повторить загрузку',
    )
    await vi.waitFor(() => expect(catalogueRetry()?.props.disabled).toBe(false))
    journalClick(catalogueRetry())
    await vi.waitFor(() => expect(journalFormat(root)).toBe('PDF'))
    expect(getWeeklyExportOptions).toHaveBeenCalledTimes(2)
    expect(download).toHaveBeenCalledTimes(1)
    expect(pendingRequests).toHaveBeenCalledTimes(1)
  })

  it.each([503, 401, 403])(
    'keeps weekly export failure %i in its panel and forwards only access failures',
    async (status) => {
      const fixture = journalFixture()
      const request = {
        kind: 'HEADMAN_WEEKLY_SELECTED',
        headmanWeeklySelected: { weekStarts: ['2026-09-07'], format: 'pdf' },
      }
      const onError = vi.fn()
      const failure = new ReportDownloadTicketError(status)
      const download = vi.fn<ReportDownloadPort['download']>()
        .mockRejectedValueOnce(failure)
        .mockResolvedValueOnce('accepted')
      const api = {
        ...fixture.api,
        getWeeklyExportOptions: async () => ({
          semesterId: 2,
          weeks: [{
            weekOfSemester: 1,
            isoWeek: 37,
            label: 'Неделя 1',
            weekStart: '2026-09-07',
            weekEnd: '2026-09-13',
            current: false,
          }],
          formats: [{ code: 'pdf', label: 'PDF', extension: 'pdf', contentType: 'application/pdf' }],
        }),
      }
      const root = mountJournalScreen(HeadmanJournalScreen, {
        api,
        groupId: 4,
        initialDate: fixture.lesson.date,
        reportDownload: { download },
        onError,
      })
      const downloadButton = () => journalNodes(root).find(
        (node) => node.props.class === 'headman-journal__weekly-download' && node.text.trim() === 'Скачать',
      )
      await vi.waitFor(() => expect(downloadButton()?.props.disabled).toBe(false))
      journalClick(journalNodes(root).find(
        (node) => node.tag === 'button' && node.text.trim() === 'Скачать журнал',
      ))
      await nextTick()
      journalClick(downloadButton())
      await vi.waitFor(() => expect(journalNodes(root).filter(
        (node) => node.props.role === 'alert' && journalNodes(node).some((child) => child.text.includes(failure.message)),
      )).toHaveLength(1))
      expect(download.mock.calls[0]?.[0]).toEqual(request)
      if (status === 503) {
        expect(onError).not.toHaveBeenCalled()
        expect(downloadButton()?.props.disabled).toBe(false)
        journalClick(downloadButton())
        await vi.waitFor(() => expect(download).toHaveBeenCalledTimes(2))
        expect(download.mock.calls[1]?.[0]).toEqual(request)
      } else {
        expect(onError).toHaveBeenCalledOnce()
        expect(onError.mock.calls[0]?.[0]).toBeInstanceOf(HeadmanJournalApiError)
        expect(onError.mock.calls[0]?.[0].response.status).toBe(status)
      }
    },
  )

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
        expect(journalFormat(root)).toBe('Word')
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
            node.props.role === 'alert' &&
            journalNodes(node).some((child) => child.text.includes('Не удалось связаться с сервером')),
        ),
      ).toBe(true),
    )
    await nextTick()
    events.get('online')?.()
    await vi.waitFor(() => expect(download).toHaveBeenCalledTimes(3))
    expect(download.mock.calls[2]?.[0]).toEqual(request)
    finish('accepted')
  })

  it('clears the active choice directly using its current revision', async () => {
    const fixture = journalFixture()
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: fixture.api,
      groupId: 4,
      initialDate: fixture.lesson.date,
    })
    await vi.waitFor(() =>
      expect(journalButton(root, 'Был: Синтетический студент')?.props['aria-pressed']).toBe(true),
    )
    expect(journalButton(root, 'Не был: Синтетический студент')).toBeDefined()
    journalClick(journalButton(root, 'Был: Синтетический студент'))
    await vi.waitFor(() =>
      expect(journalButton(root, 'Был: Синтетический студент')?.props['aria-pressed']).toBe(false),
    )
    expect(fixture.clear).toHaveBeenCalledExactlyOnceWith(30, 7, {
      expectedStatus: 'PRESENT',
      expectedRevision: 'revision-a',
    })
  })

  it('clears the repeated EXCUSED choice immediately without opening an attachment dialog', async () => {
    const fixture = journalFixture(false, 'EXCUSED')
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: fixture.api,
      groupId: 4,
      initialDate: fixture.lesson.date,
    })
    await vi.waitFor(() =>
      expect(
        journalButton(root, 'Уважительная причина: Синтетический студент')?.props['aria-pressed'],
      ).toBe(true),
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
    const findChoice = () => journalButton(root, 'Уважительная причина: Синтетический студент')
    await vi.waitFor(() => expect(findChoice()?.props.disabled).toBe(true))
    journalClick(findChoice())
    await nextTick()
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
  it.each([HeadmanJournalScreen, HeadmanHomeScreen])(
    'validates an empty reason before writing and accepts a reason after one command',
    async (screen) => {
      const fixture = journalFixture()
      const root = mountJournalScreen(screen, {
        api: fixture.api,
        groupId: 4,
        initialDate: fixture.lesson.date,
        selectedDate: fixture.lesson.date,
      })
      const choice = () => journalButton(root, 'Уважительная причина: Синтетический студент')
      await vi.waitFor(() => expect(choice()).toBeDefined())
      journalClick(choice())
      await nextTick()
      const form = () => journalNodes(root).find((node) => node.tag === 'form')
      const select = () => journalButton(root, 'Причина уважительной отметки')!
      const submit = () =>
        (form()!.props.onSubmit as (event: unknown) => void)({ preventDefault() {} })
      submit()
      await nextTick()
      expect(fixture.mark).not.toHaveBeenCalled()
      expect(select().props['aria-invalid']).toBe(true)
      expect(
        journalNodes(form()!).some(
          (node) =>
            String(select().props['aria-describedby']).split(' ').includes(String(node.props.id)) &&
            node.text.trim() === 'Выбери причину.',
        ),
      ).toBe(true)
      journalClick(select())
      await nextTick()
      journalClick(journalButton(root, 'Болезнь'))
      await nextTick()
      submit()
      await vi.waitFor(() => expect(choice()?.props['aria-pressed']).toBe(true))
      expect(form()).toBeUndefined()
      expect(fixture.mark).toHaveBeenCalledExactlyOnceWith(
        30,
        7,
        {
          status: 'EXCUSED',
          excuseType: 'ILLNESS',
        },
        { expectedStatus: 'PRESENT', expectedRevision: 'revision-a' },
      )
    },
  )

  it('shows a confirmed row after the next GET fails and only reads on retry', async () => {
    const fixture = journalFixture()
    const getAttendance = vi.mocked(fixture.api.getLessonAttendance)
    const siblingBefore = () => journalButton(root, 'Был: Иванов Иван')
    const onError = vi.fn()
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: fixture.api,
      groupId: 4,
      initialDate: fixture.lesson.date,
      onError,
    })
    const absent = () => journalButton(root, 'Не был: Синтетический студент')
    await vi.waitFor(() => expect(absent()).toBeDefined())
    const sibling = siblingBefore()
    getAttendance.mockRejectedValueOnce(new Error('read unavailable'))
    journalClick(absent())
    const retry = () => journalButton(root, 'Обновить отметку: Синтетический студент')
    await vi.waitFor(() => expect(retry()?.props.disabled).toBe(false))
    expect(absent()?.props['aria-pressed']).toBe(true)
    expect(absent()?.props['aria-disabled']).toBe(true)
    expect(siblingBefore()).toBe(sibling)
    expect(sibling?.props['aria-pressed']).toBe(false)
    expect(sibling?.props['aria-disabled']).toBeUndefined()
    expect(journalNodes(root).some((node) => node.text.includes('Отметка сохранена.'))).toBe(true)
    journalClick(absent())
    expect(fixture.mark).toHaveBeenCalledTimes(1)
    expect(fixture.clear).not.toHaveBeenCalled()
    journalClick(retry())
    await vi.waitFor(() => expect(retry()).toBeUndefined())
    expect(getAttendance).toHaveBeenCalledTimes(3)
    expect(fixture.mark).toHaveBeenCalledTimes(1)
    expect(fixture.clear).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()
    journalClick(absent())
    await vi.waitFor(() =>
      expect(fixture.clear).toHaveBeenCalledExactlyOnceWith(30, 7, {
        expectedStatus: 'ABSENT',
        expectedRevision: 'revision-b',
      }),
    )
  })

  it.each([409, 503])(
    'distinguishes a conflict from an ordinary rejected write (%s)',
    async (status) => {
      const fixture = journalFixture()
      const reads = vi.mocked(fixture.api.getLessonAttendance)
      const root = mountJournalScreen(HeadmanJournalScreen, {
        api: fixture.api,
        groupId: 4,
        initialDate: fixture.lesson.date,
      })
      const absent = () => journalButton(root, 'Не был: Синтетический студент')
      await vi.waitFor(() => expect(absent()).toBeDefined())
      fixture.mark.mockRejectedValueOnce(
        new HeadmanJournalApiError(new Response(null, { status }), {
          detail: 'Операция отклонена',
        }),
      )
      if (status === 409) reads.mockResolvedValueOnce(journalFixture(false, 'EXCUSED').report())
      journalClick(absent())
      await vi.waitFor(() =>
        expect(
          journalNodes(root).some((node) =>
            node.text.includes(status === 409 ? 'Отметка изменилась.' : 'Операция отклонена'),
          ),
        ).toBe(true),
      )
      expect(reads).toHaveBeenCalledTimes(status === 409 ? 2 : 1)
      expect(fixture.mark).toHaveBeenCalledTimes(1)
      expect(absent()?.props['aria-pressed']).toBe(false)
      expect(
        journalButton(
          root,
          status === 409
            ? 'Уважительная причина: Синтетический студент'
            : 'Был: Синтетический студент',
        )?.props['aria-pressed'],
      ).toBe(true)
    },
  )

  it('keeps a rejected reason draft open, and purges it and the roster on authorization denial', async () => {
    const fixture = journalFixture()
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: fixture.api,
      groupId: 4,
      initialDate: fixture.lesson.date,
    })
    const choice = () => journalButton(root, 'Уважительная причина: Синтетический студент')
    await vi.waitFor(() => expect(choice()).toBeDefined())
    journalClick(choice())
    await nextTick()
    const findForm = () => journalNodes(root).find((node) => node.tag === 'form')
    const form = findForm()!
    journalClick(journalButton(root, 'Причина уважительной отметки'))
    await nextTick()
    journalClick(journalButton(root, 'Болезнь'))
    await nextTick()
    const submit = () => (form.props.onSubmit as (event: unknown) => void)({ preventDefault() {} })
    fixture.mark.mockRejectedValueOnce(
      new HeadmanJournalApiError(new Response(null, { status: 503 }), {
        detail: 'Причина не принята',
      }),
    )
    submit()
    await vi.waitFor(() =>
      expect(journalNodes(form).some((node) => node.text === 'Причина не принята')).toBe(true),
    )
    expect(findForm()).toBe(form)
    fixture.mark.mockRejectedValueOnce(
      new HeadmanJournalApiError(new Response(null, { status: 403 }), { detail: 'Нет доступа' }),
    )
    submit()
    await vi.waitFor(() => expect(findForm()).toBeUndefined())
    expect(journalNodes(root).some((node) => node.props['data-user-id'] === 7)).toBe(false)
    expect(fixture.mark).toHaveBeenCalledTimes(2)
  })

  it('holds a conflicting reason draft until the refreshed mark has been reviewed', async () => {
    const fixture = journalFixture()
    const reads = vi.mocked(fixture.api.getLessonAttendance)
    const root = mountJournalScreen(HeadmanJournalScreen, {
      api: fixture.api,
      groupId: 4,
      initialDate: fixture.lesson.date,
    })
    const choice = () => journalButton(root, 'Уважительная причина: Синтетический студент')
    await vi.waitFor(() => expect(choice()).toBeDefined())
    journalClick(choice())
    await nextTick()
    const form = journalNodes(root).find((node) => node.tag === 'form')!
    journalClick(journalButton(root, 'Причина уважительной отметки'))
    await nextTick()
    journalClick(journalButton(root, 'Болезнь'))
    await nextTick()
    const submit = () => (form.props.onSubmit as (event: unknown) => void)({ preventDefault() {} })
    const button = (label: string) =>
      journalNodes(form).find((node) => node.tag === 'button' && node.text.trim() === label)
    const formText = () =>
      journalNodes(form)
        .map((node) => node.text)
        .join(' ')
    fixture.mark.mockRejectedValueOnce(
      new HeadmanJournalApiError(new Response(null, { status: 409 }), {}),
    )
    reads.mockRejectedValueOnce(new Error('read unavailable'))
    submit()
    await vi.waitFor(() => expect(button('Обновить отметку')?.props.disabled).toBe(false))
    submit()
    expect(fixture.mark).toHaveBeenCalledTimes(1)
    const refreshed = journalFixture(false, 'EXCUSED').report()
    reads.mockResolvedValueOnce({
      ...refreshed,
      entries: refreshed.entries.map((row) =>
        row.userId === 7
          ? {
              ...row,
              markRevision: 'actual-revision',
              excuseType: 'MEDICAL_EXAMINATION',
              comment: 'Комментарий с сервера',
            }
          : row,
      ),
    })
    journalClick(button('Обновить отметку'))
    await vi.waitFor(() => expect(button('Продолжить с этой отметкой')?.props.disabled).toBe(false))
    expect(reads).toHaveBeenCalledTimes(3)
    expect(formText()).toContain('Медицинское обследование')
    expect(formText()).toContain('Комментарий с сервера')
    expect(formText()).not.toContain('Обнови состав')
    expect(formText()).toContain('Болезнь')
    expect(button('Сохранить')?.props['aria-disabled']).toBe(true)
    submit()
    expect(fixture.mark).toHaveBeenCalledTimes(1)
    journalClick(button('Продолжить с этой отметкой'))
    await nextTick()
    expect(fixture.mark).toHaveBeenCalledTimes(1)
    submit()
    await vi.waitFor(() => expect(choice()?.props['aria-pressed']).toBe(true))
    expect(fixture.mark).toHaveBeenCalledTimes(2)
    expect(fixture.mark).toHaveBeenLastCalledWith(
      30,
      7,
      { status: 'EXCUSED', excuseType: 'ILLNESS' },
      {
        expectedStatus: 'EXCUSED',
        expectedRevision: 'actual-revision',
      },
    )
  })

  it('does not grant Today reason or geo controls to an assistant with only MARK_ATTENDANCE', async () => {
    const fixture = journalFixture(false, 'PRESENT', moscowDate(new Date().toISOString())!)
    const root = mountJournalScreen(HeadmanHomeScreen, {
      api: fixture.api,
      groupId: 4,
      selectedDate: fixture.lesson.date,
      assistantPermissions: ['MARK_ATTENDANCE'],
    })
    const reason = () => journalButton(root, 'Уважительная причина: Синтетический студент')
    await vi.waitFor(() => expect(reason()?.props.disabled).toBe(true))
    journalClick(reason())
    await nextTick()
    expect(journalNodes(root).some((node) => node.tag === 'form')).toBe(false)
    expect(journalButton(root, 'Заблокировать геоотметки')).toBeUndefined()
    expect(fixture.mark).not.toHaveBeenCalled()
  })

  it('keeps the pending row owner when a geo ACK replaces the same lesson', async () => {
    const fixture = journalFixture(false, 'PRESENT', moscowDate(new Date().toISOString())!)
    const mark = fixture.mark.getMockImplementation()!
    let finishMark!: () => void
    fixture.mark.mockImplementationOnce(async (...args) => {
      await new Promise<void>((resolve) => {
        finishMark = resolve
      })
      return mark(...args)
    })
    const root = mountJournalScreen(HeadmanHomeScreen, {
      api: fixture.api,
      groupId: 4,
      selectedDate: fixture.lesson.date,
    })
    const absent = () => journalButton(root, 'Не был: Синтетический студент')
    await vi.waitFor(() => expect(absent()).toBeDefined())
    journalClick(absent())
    journalClick(journalButton(root, 'Заблокировать геоотметки'))
    await nextTick()
    journalClick(journalButton(root, 'Подтвердить блокировку геоотметок'))
    await vi.waitFor(() => expect(journalButton(root, 'Разблокировать геоотметки')).toBeDefined())
    expect(absent()?.props['aria-disabled']).toBe(true)
    expect(absent()?.props['aria-pressed']).toBe(false)
    expect(fixture.api.getLessonAttendance).toHaveBeenCalledTimes(1)
    finishMark()
    await vi.waitFor(() => expect(absent()?.props['aria-pressed']).toBe(true))
    expect(fixture.mark).toHaveBeenCalledTimes(1)
    expect(fixture.api.getLessonAttendance).toHaveBeenCalledTimes(2)
  })

  it('offers geo blocking on the loaded active main lesson', async () => {
    const fixture = journalFixture(false, 'PRESENT', moscowDate(new Date().toISOString())!)
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
    expect(journalButton(root, 'Заблокировать геоотметки')).toBeUndefined()
    const cancel = journalNodes(root).find(
      (node) => node.tag === 'button' && node.text.trim() === 'Отмена',
    )
    expect(cancel?.props.focused).toBe(true)
    const group = journalNodes(root).find(
      (node) => node.props.class === 'headman-home__block-confirm',
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
    const fixture = journalFixture(false, 'PRESENT', moscowDate(new Date().toISOString())!)
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
    const fixture = journalFixture(false, 'PRESENT', moscowDate(new Date().toISOString())!)
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
