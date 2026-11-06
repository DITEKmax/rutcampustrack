import {
  createRenderer,
  defineComponent,
  h,
  markRaw,
  nextTick,
  shallowReactive,
  type App,
} from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TeacherJournalScreen from './TeacherJournalScreen.vue'
import TeacherJournalTable from './TeacherJournalTable.vue'
import {
  TeacherApiError,
  TeacherApi,
  type TeacherJournalQuery,
  type TeacherJournalResponse,
} from './teacher-client'
import { AuthRequestError } from '../../../../pwa-vue/src/auth-client'
import { TmaAuthError } from '../../../../tma-vue/src/tma-auth'
import type {
  ReportDownloadPort,
  ReportDownloadTicketRequest,
} from '../../shared/report-download-client'
vi.mock('../headman-stats/HeadmanStatsSelect.vue', () => ({
  default: defineComponent({
    props: {
      label: { type: String, default: '' },
      modelValue: { type: String, default: '' },
      options: { type: Array, default: () => [] },
    },
    emits: ['update:modelValue'],
    setup(props, { emit }) {
      return () =>
        h(
          'button',
          {
            'aria-label': props.label,
            onClick: () => {
              const values = props.options as { value: string }[]
              const current = values.findIndex((value) => value.value === props.modelValue)
              emit('update:modelValue', values[(current + 1) % values.length]?.value)
            },
          },
          props.label,
        )
    },
  }),
}))
class Node {
  scrollLeft = 0
  readonly style = {
    properties: new Map<string, string>(),
    setProperty: (name: string, value: string) => this.style.properties.set(name, value),
    removeProperty: (name: string) => this.style.properties.delete(name),
  }
  readonly ownerDocument = { defaultView: { innerHeight: 720, scrollBy: vi.fn() } }
  children: Node[] = []
  parent: Node | null = null
  props: Record<string, unknown> = {}
  text = ''
  value: unknown = ''
  selected = false
  checked = false
  constructor(readonly tag: string) {
    markRaw(this)
  }
  querySelectorAll(selector: string): Node[] {
    const result: Node[] = []
    const walk = (node: Node) => {
      if (selector === '[role="option"]' && node.props.role === 'option') result.push(node)
      node.children.forEach(walk)
    }
    this.children.forEach(walk)
    return result
  }
  getBoundingClientRect() {
    return { width: 360, left: 0, right: 360, top: 0, bottom: 720 }
  }
  contains(target: Node): boolean {
    return target === this || this.children.some((child) => child.contains(target))
  }
  focus(): void {}
  close(): void {}
  showModal(): void {}
  addEventListener(): void {}
  getRootNode(): { activeElement: null } {
    return { activeElement: null }
  }
  get tagName(): string {
    return this.tag.toUpperCase()
  }
  get options(): Node[] {
    return this.children.filter((node) => node.tag === 'option')
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
  patchProp: (node, key, _before, value) => {
    node.props[key] = value
  },
  insert: (node, parent, anchor = null) => {
    const old = node.parent
    if (old) old.children.splice(old.children.indexOf(node), 1)
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
    node.parent = parent
  },
  remove: (node) => {
    node.parent?.children.splice(node.parent.children.indexOf(node), 1)
    node.parent = null
  },
})
function find(root: Node, predicate: (node: Node) => boolean): Node | undefined {
  if (predicate(root)) return root
  for (const child of root.children) {
    const found = find(child, predicate)
    if (found) return found
  }
  return undefined
}
function text(root: Node): string {
  return root.text + root.children.map(text).join(' ')
}
function click(root: Node, label: string): void {
  const node = find(
    root,
    (item) =>
      item.tag === 'button' && (item.props['aria-label'] === label || text(item).trim() === label),
  )
  expect(node).toBeDefined()
  ;(node!.props.onClick as () => void)()
}
const apps: App[] = []
afterEach(() => {
  for (const app of apps.splice(0)) app.unmount()
  vi.unstubAllGlobals()
})

async function settle(): Promise<void> {
  for (let i = 0; i < 16; i++) {
    await Promise.resolve()
    await nextTick()
  }
}
const subjects = [
  { groupId: 2, subjectId: 3, subjectName: 'Базы данных', lessonTypes: ['lecture', 'practice'] },
]
function response(subjectId = 3, name = 'Анна'): TeacherJournalResponse {
  return {
    page: 0,
    pageSize: 100,
    totalLessons: 1,
    hasMore: false,
    serverNow: '2026-10-09T12:00:00Z',
    metrics: {
      students: [
        {
          studentId: 4,
          displayName: name,
          present: { numerator: 0, denominator: 1, percent: 0 },
          presentOrExcused: { numerator: 1, denominator: 1, percent: 100 },
          excused: { numerator: 1, denominator: 1, percent: 100 },
          absent: { numerator: 0, denominator: 1, percent: 0 },
        },
      ],
      series: [],
      selectedStudentId: null,
      serverNow: '2026-10-09T12:00:00Z',
    },
    lessons: [
      {
        id: 30,
        assignmentId: 1,
        groupId: 2,
        groupName: 'ИКБО',
        subjectId,
        subjectName: 'Базы данных',
        semesterId: 7,
        lessonType: 'lecture',
        date: '2026-10-01',
        lessonNumber: 1,
        startsAt: null,
        endsAt: null,
        room: null,
        status: 'CLOSED',
        cancelled: false,
        cancellationReason: null,
        oneOff: false,
        moved: false,
      },
    ],
    students: [
      {
        studentId: 4,
        displayName: name,
        cells: [
          {
            lessonId: 30,
            status: 'excused',
            symbol: 'у',
            source: 'NO_TICKET',
            recordPresent: true,
            pendingTicket: false,
            autoAbsent: false,
            ticketId: null,
            excuseType: 'illness',
            excuseReason: 'Болезнь',
          },
        ],
      },
    ],
  }
}
function mount(
  options: {
    initialTypes?: string[]
    initialRange?: Pick<TeacherJournalQuery, 'dateFrom' | 'dateTo'>
    api?: TeacherApi
    showExport?: boolean
    journal?: (query: TeacherJournalQuery) => Promise<TeacherJournalResponse>
    report?: ReportDownloadPort
  } = {},
) {
  const calls: TeacherJournalQuery[] = []
  const manual: [number, number][] = []
  const openedLessons: [number, number][] = []
  const ownerErrors: unknown[] = []
  const api =
    options.api ??
    (markRaw({
      journal: async (query: TeacherJournalQuery) => {
        calls.push(query)
        return options.journal ? options.journal(query) : response()
      },
      journalExportFormats: async () => [
        {
          code: 'docx',
          label: 'DOCX',
          extension: 'docx',
          contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        },
      ],
    }) as unknown as TeacherApi)
  const state = shallowReactive<{ query: TeacherJournalQuery; active: boolean }>({
    query: {
      semesterId: 7,
      groupId: 2,
      subjectId: 3,
      lessonTypes: options.initialTypes ?? [],
      ...options.initialRange,
      pastOnly: true,
    },
    active: true,
  })
  const root = new Node('root')
  const app = renderer.createApp({
    render: () =>
      h(TeacherJournalScreen, {
        api,
        showExport: options.showExport ?? true,
        onError: (cause: unknown) => ownerErrors.push(cause),
        query: state.query,
        active: state.active,
        subjectOptions: subjects,
        semester: { id: 7, name: 'Осень', dateFrom: '2026-09-01', dateTo: '2027-01-31' },
        reportDownload: options.report ?? null,
        'onUpdate-query': (query: TeacherJournalQuery) => {
          state.query = query
        },
        'onOpen-lesson': (id: number, page: number) => openedLessons.push([id, page]),
        'onOpen-manual-excuse': (lessonId: number, studentId: number) => {
          manual.push([lessonId, studentId])
        },
      }),
  })
  apps.push(app)
  app.mount(root)
  return { root, state, calls, manual, openedLessons, ownerErrors }
}
describe('teacher journal context and navigation', () => {
  it('opens the lesson from its journal heading with the loaded page context', async () => {
    const view = mount({ initialTypes: ['lecture'] })
    await settle()
    click(view.root, 'Открыть 1-ю пару, 01.10, Лекция')
    expect(view.openedLessons).toEqual([[30, 0]])
    expect(view.calls).toHaveLength(1)
  })
  it('selects a name and date independently without navigation, toggles them, and clears a new query context', async () => {
    const view = mount({ initialTypes: ['lecture'] })
    await settle()
    const name = () =>
      find(view.root, (node) => node.tag === 'button' && node.props['aria-label'] === 'Анна')!
    const date = () =>
      find(view.root, (node) => node.props['aria-label'] === 'Выделить 1-ю пару, 01.10, Лекция')!
    click(view.root, 'Анна')
    click(view.root, 'Выделить 1-ю пару, 01.10, Лекция')
    await settle()
    expect(name().props['aria-pressed']).toBe(true)
    expect(date().props['aria-pressed']).toBe(true)
    expect(view.openedLessons).toEqual([])
    expect(view.calls).toHaveLength(1)
    expect(
      find(view.root, (node) => node.tag === 'tr' && node.props['data-selected-row'] === true),
    ).toBeDefined()
    expect(
      find(view.root, (node) => node.tag === 'td' && node.props['data-selected-column'] === true),
    ).toBeDefined()
    click(view.root, 'Анна')
    await settle()
    expect(name().props['aria-pressed']).toBe(false)
    expect(date().props['aria-pressed']).toBe(true)
    click(view.root, 'Выделить 1-ю пару, 01.10, Лекция')
    await settle()
    expect(date().props['aria-pressed']).toBe(false)
    click(view.root, 'Анна')
    click(view.root, 'Выделить 1-ю пару, 01.10, Лекция')
    view.state.query = { ...view.state.query, subjectId: 9 }
    await settle()
    expect(name().props['aria-pressed']).toBe(false)
    expect(date().props['aria-pressed']).toBe(false)
    click(view.root, 'Открыть 1-ю пару, 01.10, Лекция')
    expect(view.openedLessons).toEqual([[30, 0]])
  })
  it.each([403, 503])(
    'keeps journal HTTP %i local and recovers the same query without a stale host error',
    async (status) => {
      let attempts = 0
      const cause = new TeacherApiError(new Response(null, { status }), {
        detail: 'Чтение недоступно',
      })
      const view = mount({
        initialTypes: ['lecture'],
        journal: async () => {
          if (++attempts === 1) throw cause
          return response()
        },
      })
      await settle()
      expect(attempts).toBe(1)
      expect(view.ownerErrors).toEqual([])
      click(view.root, 'Повторить')
      await settle()
      expect(attempts).toBe(2)
      expect(view.calls[1]).toEqual(view.calls[0])
      expect(view.ownerErrors).toEqual([])
      expect(find(view.root, (node) => node.props.role === 'alert')).toBeUndefined()
    },
  )
  it.each([
    new AuthRequestError('refresh', 401, { detail: 'Session expired' }),
    new TmaAuthError(401, 'Re-auth denied'),
    new TmaAuthError(403, 'Candidate session denied'),
  ])(
    'forwards the current owner denial after a real journal401/onUnauthorized rejection (%s)',
    async (cause) => {
      const refresh = vi.fn(async () => {
        throw cause
      })
      let journalReads = 0
      const api = markRaw(
        new TeacherApi({
          accessToken: () => 'unit-test-bearer',
          onUnauthorized: refresh,
          fetcher: async (input) => {
            if (String(input).includes('/journal?')) {
              journalReads += 1
              return new Response(null, { status: 401 })
            }
            throw new Error('Unexpected journal boundary request')
          },
        }),
      )
      const view = mount({ initialTypes: ['lecture'], api, showExport: false })
      await settle()
      expect(journalReads).toBe(1)
      expect(refresh).toHaveBeenCalledTimes(1)
      expect(view.ownerErrors).toEqual([cause])
    },
  )
  it('ignores a late owner refresh denial after the real journal query has changed', async () => {
    let reject!: (cause: unknown) => void
    const refresh = vi.fn(
      () =>
        new Promise<void>((_resolve, fail) => {
          reject = fail
        }),
    )
    const queries: string[] = []
    const api = markRaw(
      new TeacherApi({
        accessToken: () => 'unit-test-bearer',
        onUnauthorized: refresh,
        fetcher: async (input) => {
          const url = new URL(String(input), 'https://fixture.test')
          if (url.pathname.endsWith('/journal')) {
            queries.push(url.searchParams.get('subjectId')!)
            return url.searchParams.get('subjectId') === '3'
              ? new Response(null, { status: 401 })
              : Response.json(response(9))
          }
          throw new Error('Unexpected journal boundary request')
        },
      }),
    )
    const view = mount({ initialTypes: ['lecture'], api, showExport: false })
    await settle()
    expect(refresh).toHaveBeenCalledTimes(1)
    view.state.query = { ...view.state.query, subjectId: 9 }
    await settle()
    reject(new AuthRequestError('refresh', 401, { detail: 'Old session denied' }))
    await settle()
    expect(queries).toEqual(['3', '9'])
    expect(view.ownerErrors).toEqual([])
  })

  it('renders server Table and Chart metrics, with selected-student reads fenced against late results', async () => {
    vi.stubGlobal('document', { createElement: () => ({ getContext: () => null }) })
    const range = { dateFrom: '2026-10-01', dateTo: '2026-10-01' }
    const exports: ReportDownloadTicketRequest[] = []
    const present = { numerator: 1, denominator: 4, percent: 25 }
    const combined = { numerator: 3, denominator: 4, percent: 75 }
    const excused = { numerator: 2, denominator: 4, percent: 50 }
    const absent = { numerator: 1, denominator: 4, percent: 25 }
    const row = {
      studentId: 4,
      displayName: 'Анна',
      present,
      presentOrExcused: combined,
      excused,
      absent,
    }
    const envelope = (selectedStudentId: number | null, percent = 25) => ({
      students: [row],
      series: [
        {
          lessonId: 30,
          present: { ...present, percent },
          presentOrExcused: combined,
          excused,
          absent,
        },
      ],
      selectedStudentId,
      serverNow: '2026-10-09T12:00:00Z',
    })
    let release!: (value: TeacherJournalResponse) => void
    const view = mount({
      initialTypes: ['lecture'],
      initialRange: range,
      report: {
        async download(request) {
          exports.push(request)
          return 'accepted'
        },
      },
      journal: async (query) => {
        if (query.metricsStudentId === 4)
          return new Promise((resolve) => {
            release = resolve
          })
        return { ...response(), metrics: envelope(null) }
      },
    })
    await settle()
    expect(view.calls[0]).toMatchObject({ ...range, pastOnly: true })
    click(view.root, 'Скачать журнал за выбранный период')
    await settle()
    expect(exports).toMatchObject([
      { kind: 'TEACHER_JOURNAL', teacherJournal: { ...range, pastOnly: true } },
    ])
    // The only cell is EXCUSED: these values cannot come from counting that cell.
    expect(text(view.root)).toContain('25 %')
    expect(
      find(
        view.root,
        (node) =>
          node.props['aria-label'] === 'Присутствия и уважительные причины: 3 из 4' &&
          text(node) === '75 %',
      ),
    ).toBeDefined()
    click(view.root, 'Показать график')
    await settle()
    const axis = find(
      view.root,
      (node) => node.props.class === 'teacher-journal__chart-axis',
    )
    expect(
      axis?.children
        .filter((node) => node.tag === 'text')
        .map((node) => [text(node).trim(), node.props['text-anchor']]),
    ).toEqual([['1 окт.', 'middle']])
    const bar = find(
      view.root,
      (node) =>
        node.tag === 'g' &&
        node.props.role === 'img' &&
        String(node.props['aria-label']).includes('присутствия 25 %'),
    )!
    expect(bar).toBeDefined()
    ;(bar.props.onClick as () => void)()
    await settle()
    const readout = find(view.root, (node) => node.tag === 'dl')
    expect(readout?.children.filter((node) => node.tag === 'dd').map(text)).toEqual([
      '25 %', '75 %', '50 %', '25 %', '4',
    ])
    click(view.root, 'Студент')
    await settle()
    expect(view.calls.at(-1)).toMatchObject({
      ...range,
      metricsStudentId: 4,
      includeMetrics: true,
      page: 0,
      pageSize: 1,
    })
    expect(text(view.root)).toContain('Загружаем метрики графика')
    click(view.root, 'Студент')
    await settle()
    release({ ...response(), metrics: envelope(4, 90) })
    await settle()
    expect(
      find(
        view.root,
        (node) =>
          node.props.role === 'img' &&
          String(node.props['aria-label']).includes('присутствия 90 %'),
      ),
    ).toBeUndefined()
    expect(
      find(
        view.root,
        (node) =>
          node.props.role === 'img' &&
          String(node.props['aria-label']).includes('присутствия 25 %'),
      ),
    ).toBeDefined()
    view.state.active = false
    await settle()
    expect(find(view.root, (node) => node.tag === 'g' && node.props.role === 'img')).toBeUndefined()
    view.state.active = true
    await settle()
    expect(
      find(
        view.root,
        (node) =>
          node.props.role === 'img' &&
          String(node.props['aria-label']).includes('присутствия 25 %'),
      ),
    ).toBeDefined()
    click(view.root, 'Показать таблицу')
    await settle()
    expect(
      find(
        view.root,
        (node) =>
          node.props['aria-label'] === 'Присутствия и уважительные причины: 3 из 4' &&
          text(node) === '75 %',
      ),
    ).toBeDefined()
  })
  it('keeps missing server metrics unavailable instead of recomputing from cells', async () => {
    const view = mount({
      initialTypes: ['lecture'],
      journal: async () => ({ ...response(), metrics: null }),
    })
    await settle()
    expect(
      find(view.root, (node) => node.props['aria-label'] === 'Присутствия: данные недоступны'),
    ).toBeDefined()
    expect(text(view.root)).not.toContain('100 %')
  })
  it('invalidates an in-flight report after navigating away from a retained journal', async () => {
    let release!: (value: 'accepted') => void
    let active!: () => boolean
    const report: ReportDownloadPort = {
      download: (_value, guard) => {
        active = guard
        return new Promise((resolve) => {
          release = resolve
        })
      },
    }
    const view = mount({ initialTypes: ['lecture'], report })
    await settle()
    click(view.root, 'Скачать журнал за выбранный период')
    await settle()
    expect(active()).toBe(true)
    view.state.active = false
    await settle()
    expect(active()).toBe(false)
    release('accepted')
    await settle()
    expect(text(view.root)).not.toContain('Telegram принял')
    view.state.active = true
    await settle()
    expect(text(view.root)).toContain('Анна')
  })
  it('does not request a journal until explicit type selection and supports a real multiselect', async () => {
    const view = mount()
    await settle()
    expect(view.calls).toHaveLength(0)
    expect(text(view.root)).toContain('Выбери один или несколько типов занятий.')
    click(view.root, 'Лекция')
    await settle()
    const lessonHeading = () =>
      find(
        view.root,
        (node) => node.tag === 'button' && String(node.props['aria-label']).startsWith('Открыть '),
      )
    expect(lessonHeading()).toBeDefined()
    expect(text(lessonHeading()!)).toContain('ЛК')
    expect(lessonHeading()!.props['aria-label']).toContain('Лекция')
    click(view.root, 'Практика')
    await settle()
    // The response still contains only lecture lessons: selected types own this label.
    expect(text(lessonHeading()!)).toContain('ЛК')
    expect(lessonHeading()!.props['aria-label']).toContain('Лекция')
    expect(view.state.query.lessonTypes).toEqual(['lecture', 'practice'])
    expect(view.calls.at(-1)?.lessonTypes).toEqual(['lecture', 'practice'])
    expect(view.calls.every((query) => query.pastOnly && query.page === 0)).toBe(true)
    click(view.root, 'Лекция')
    click(view.root, 'Практика')
    await settle()
    expect(view.state.query.lessonTypes).toEqual([])
    expect(text(view.root)).not.toContain('Анна')
  })
  it('discards an older subject response after context changes', async () => {
    let release!: (value: TeacherJournalResponse) => void
    const view = mount({
      initialTypes: ['lecture'],
      journal: (query) =>
        query.subjectId === 3
          ? new Promise((resolve) => {
              release = resolve
            })
          : Promise.resolve(response(8, 'Новый контекст')),
    })
    view.state.query = { ...view.state.query, subjectId: 8 }
    await settle()
    release(response(3, 'Устаревшая строка'))
    await settle()
    expect(text(view.root)).toContain('Новый контекст')
    expect(text(view.root)).not.toContain('Устаревшая строка')
  })
  it('distinguishes automatic absence and a pending statement without adding mark actions', async () => {
    const original = response()
    const absent = {
      ...original.students[0]!.cells[0]!,
      status: 'absent',
      symbol: 'н',
      ticketId: null,
      excuseType: null,
    }
    const view = mount({
      initialTypes: ['lecture'],
      journal: async () => ({
        ...original,
        students: [
          { studentId: 4, displayName: 'Автоматически', cells: [{ ...absent, autoAbsent: true }] },
          { studentId: 5, displayName: 'Вручную', cells: [{ ...absent, autoAbsent: false }] },
          {
            studentId: 6,
            displayName: 'На рассмотрении',
            cells: [{ ...absent, autoAbsent: true, pendingTicket: true }],
          },
        ],
      }),
    })
    await settle()
    const automatic = find(
      view.root,
      (item) =>
        item.props['aria-label'] === 'Отсутствовал' && item.props['data-auto-absent'] === true,
    )
    const manual = find(
      view.root,
      (item) =>
        item.props['aria-label'] === 'Отсутствовал' && item.props['data-auto-absent'] === false,
    )
    const pending = find(
      view.root,
      (item) => item.props['aria-label'] === 'Отсутствовал; заявление ожидает решения',
    )
    expect(automatic?.props['aria-description']).toBe(
      'Отсутствие отмечено автоматически при закрытии пары',
    )
    expect(manual?.props['aria-description']).toBeUndefined()
    expect(pending?.props['aria-description']).toBe(automatic?.props['aria-description'])
    expect(text(pending!).trim()).toBe('Н')
    expect(pending?.props['data-pending-ticket']).toBe(true)
    expect(automatic?.props['data-pending-ticket']).toBe(false)
    for (const status of [automatic, manual, pending]) {
      expect(status?.tag).toBe('span')
      expect(status?.props.onClick).toBeUndefined()
    }
    expect(view.manual).toEqual([])
  })
  it('shows an inert cancelled column without counting its marks or future lessons', async () => {
    const original = response()
    const lesson = original.lessons[0]!
    const cell = original.students[0]!.cells[0]!
    const view = mount({
      initialTypes: ['lecture'],
      journal: async () => ({
        ...original,
        lessons: [
          lesson,
          { ...lesson, id: 31, status: 'CANCELLED', cancelled: true, cancellationReason: 'Отмена' },
          { ...lesson, id: 32, status: 'PLANNED' },
        ],
        students: [
          {
            ...original.students[0]!,
            cells: [
              cell,
              { ...cell, lessonId: 31, ticketId: 'cancelled-ticket' },
              { ...cell, lessonId: 32, status: 'absent' },
            ],
          },
        ],
      }),
    })
    await settle()
    expect(text(view.root)).toContain('Отменена')
    const cancelled = find(view.root, (item) => item.props['aria-label'] === 'Отменена')
    expect(cancelled?.tag).toBe('span')
    expect(cancelled?.props.onClick).toBeUndefined()
    expect(text(cancelled!)).toBe('—')
    expect(
      find(view.root, (item) => item.props['aria-label'] === 'Уважительные причины: 1 из 1'),
    ).toBeDefined()
    expect(
      find(view.root, (item) => item.props['aria-label'] === 'Отсутствия: 0 из 1'),
    ).toBeDefined()
    expect(find(view.root, (item) => item.props['aria-label'] === 'Отсутствовал')).toBeUndefined()
    expect(view.manual).toEqual([])
  })
  it('opens a manual excuse with exact historical lesson/student context', async () => {
    const view = mount({ initialTypes: ['lecture'] })
    await settle()
    const button = find(
      view.root,
      (item) =>
        item.tag === 'button' &&
        String(item.props['aria-label']).startsWith('Анна: Уважительная причина'),
    )
    expect(button).toBeDefined()
    ;(button!.props.onClick as () => void)()
    expect(view.manual).toEqual([[30, 4]])
  })
  it('invalidates an in-flight report on subject switch and never displays accepted as downloaded', async () => {
    let release!: (value: 'accepted') => void
    let active!: () => boolean
    let request!: ReportDownloadTicketRequest
    const report: ReportDownloadPort = {
      download: (value, guard) => {
        request = value
        active = guard
        return new Promise((resolve) => {
          release = resolve
        })
      },
    }
    const view = mount({ initialTypes: ['lecture'], report })
    await settle()
    click(view.root, 'Скачать журнал за выбранный период')
    await settle()
    expect(text(view.root)).toContain('Готовим выгрузку…')
    expect(request.teacherJournal?.groupId).toBe(2)
    view.state.query = { semesterId: 7, groupId: 2, subjectId: 8, lessonTypes: [], pastOnly: true }
    await settle()
    expect(active()).toBe(false)
    release('accepted')
    await settle()
    expect(text(view.root)).not.toContain('Telegram принял')
  })
})

describe('journal local threshold and viewport preferences', () => {
  function mountTable() {
    const state = shallowReactive({ journal: response(), contextKey: 'semester:group:subject' })
    const root = new Node('root')
    const app = renderer.createApp({
      render: () => h(TeacherJournalTable, { ...state }),
    })
    apps.push(app)
    app.mount(root)
    const all = (node: Node, predicate: (node: Node) => boolean): Node[] => [
      ...(predicate(node) ? [node] : []),
      ...node.children.flatMap((child) => all(child, predicate)),
    ]
    const inputs = () => all(root, (node) => node.tag === 'input' && node.props.type === 'text')
    const cells = () =>
      all(root, (node) => node.tag === 'td' && node.props.class === 'teacher-journal__percent')
    const input = (index: number, value: string) => {
      ;(inputs()[index]!.props.onInput as (event: unknown) => void)({ target: { value } })
    }
    return { root, state, inputs, cells, input }
  }

  it('compares each authoritative metric independently, handles equality, and resets only with dataset context', async () => {
    const view = mountTable()
    const original = JSON.stringify(view.state.journal)
    view.input(0, '0')
    view.input(1, '100')
    view.input(2, '100,0')
    view.input(3, '0,1')
    await settle()
    expect(view.cells().map((cell) => cell.props['data-threshold'])).toEqual([
      'meets',
      'meets',
      'meets',
      'below',
    ])
    expect(JSON.stringify(view.state.journal)).toBe(original)
    expect(view.cells()[3]!.props['aria-label']).toContain('ниже порога')
    view.state.journal = {
      ...view.state.journal,
      page: 1,
      lessons: [{ ...view.state.journal.lessons[0]!, id: 31 }, ...view.state.journal.lessons],
      students: [...view.state.journal.students].reverse(),
    }
    await settle()
    expect(view.inputs().map((input) => input.props.value)).toEqual(['0', '100', '100,0', '0,1'])
    view.input(0, '101')
    view.input(1, ' ')
    await settle()
    expect(view.inputs()[0]!.props['aria-invalid']).toBe(true)
    expect(view.cells().map((cell) => cell.props['data-threshold'])).toEqual([
      null,
      null,
      'meets',
      'below',
    ])
    const metric = view.state.journal.metrics!
    view.state.journal = {
      ...view.state.journal,
      metrics: {
        ...metric,
        students: [
          { ...metric.students[0]!, excused: { numerator: 0, denominator: 0, percent: 0 } },
        ],
      },
    }
    await settle()
    expect(view.cells()[2]!.props['data-threshold']).toBeNull()
    view.state.contextKey = 'other:group:subject'
    await settle()
    expect(view.inputs().map((input) => input.props.value)).toEqual(['', '', '', ''])
    expect(view.cells().every((cell) => cell.props['data-threshold'] === null)).toBe(true)
  })

  it('keeps a two-finger zoom under its center, suppresses its tap and releases the next touch', async () => {
    const view = mountTable()
    const viewport = find(
      view.root,
      (node) => node.props.class === 'teacher-journal__table-scroll',
    )!
    const table = find(viewport, (node) => node.tag === 'table')!
    const name = find(table, (node) => node.tag === 'th')!
    const lesson = find(table, (node) => String(node.props.class).includes('__type-heading'))!
    const property = '--teacher-journal-scale'
    const scale = () => Number(viewport.style.properties.get(property) ?? 1)
    let pageScroll = 0
    viewport.ownerDocument.defaultView.scrollBy.mockImplementation(({ top }) => (pageScroll += top))
    viewport.scrollLeft = 80
    table.getBoundingClientRect = () => ({
      width: 600 * scale(),
      left: 20 - viewport.scrollLeft,
      right: 620,
      top: 100 - pageScroll,
      bottom: 720,
    })
    // At larger scales, mobile CSS limits the visible sticky name column.
    name.getBoundingClientRect = () => ({
      width: Math.min(100 * scale(), 120),
      left: 20,
      right: 20 + Math.min(100 * scale(), 120),
      top: 100 - pageScroll,
      bottom: 720,
    })
    const finger = (identifier: number, target: Node, clientX: number) => ({
      identifier, target, clientX, clientY: 180,
    })
    const first = finger(11, name, 100)
    const second = finger(22, lesson, 200)
    const dispatch = (handler: string, touches: (typeof first)[], type = 'touchmove') => {
      const event = { touches, type, cancelable: true, timeStamp: 100, preventDefault: vi.fn() }
      ;(viewport.props[handler] as (value: typeof event) => void)(event)
      return event.preventDefault
    }
    expect(dispatch('onTouchstart', [first])).not.toHaveBeenCalled()
    expect(dispatch('onTouchmove', [first])).not.toHaveBeenCalled()
    expect(dispatch('onTouchstart', [first, finger(22, view.root, 200)])).not.toHaveBeenCalled()
    expect(dispatch('onTouchstart', [first, second])).toHaveBeenCalledOnce()
    expect(dispatch('onTouchmove', [
      { ...first, clientX: 75 },
      { ...second, clientX: 225 },
    ])).toHaveBeenCalledOnce()
    expect(scale()).toBe(1.5)
    expect(viewport.scrollLeft).toBe(155)
    expect(pageScroll).toBe(40)
    expect(dispatch('onTouchend', [first], 'touchend')).toHaveBeenCalledOnce()
    expect(dispatch('onTouchmove', [first])).toHaveBeenCalledOnce()
    expect(dispatch('onTouchend', [], 'touchend')).toHaveBeenCalledOnce()
    const tap = () => {
      const event = {
        pointerType: 'touch',
        detail: 1,
        timeStamp: 101,
        preventDefault: vi.fn(),
        stopPropagation: vi.fn(),
      }
      ;(viewport.props.onClickCapture as (value: typeof event) => void)(event)
      return event
    }
    expect(tap().stopPropagation).toHaveBeenCalledOnce()
    expect(dispatch('onTouchstart', [second])).not.toHaveBeenCalled()
    expect(tap().stopPropagation).not.toHaveBeenCalled()
    expect(find(view.root, (node) => node.tag === 'input' && node.props.type === 'range')).toBeUndefined()
    view.state.contextKey = 'new-context'
    await settle()
    expect(viewport.scrollLeft).toBe(0)
    expect(scale()).toBe(1)
  })
})
