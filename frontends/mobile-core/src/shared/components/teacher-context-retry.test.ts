import { createRenderer, h, markRaw, nextTick, shallowReactive, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TeacherFeatureOwner from './TeacherFeatureOwner.vue'
import { ReportDownloadClient, type ReportDownloadPort } from '../report-download-client'
import { TeacherApi } from '../../features/teacher/teacher-client'

class HostNode {
  children: HostNode[] = []
  parent: HostNode | null = null
  props: Record<string, unknown> = {}
  text = ''
  value: unknown = ''
  style: Record<string, string> = {}
  constructor(readonly tag: string) {
    markRaw(this)
  }
  querySelectorAll(): HostNode[] {
    return []
  }
  querySelector(): null {
    return null
  }
  focus(): void {}
  addEventListener(): void {}
  removeEventListener(): void {}
  getRootNode() {
    return document
  }
  get options() { return this.children.filter(node => node.tag === 'option') }
  get tagName() { return this.tag.toUpperCase() }
}
const renderer = createRenderer<HostNode, HostNode>({
  createElement: (tag) => new HostNode(tag),
  createText: (text) => Object.assign(new HostNode('#text'), { text }),
  createComment: () => new HostNode('#comment'),
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
function text(root: HostNode): string {
  return root.text + root.children.map(text).join(' ')
}
function find(root: HostNode, predicate: (node: HostNode) => boolean): HostNode | undefined {
  if (predicate(root)) return root
  for (const child of root.children) {
    const match = find(child, predicate)
    if (match) return match
  }
  return undefined
}
function button(root: HostNode, label: string): HostNode {
  const node = find(
    root,
    (node) =>
      node.tag === 'button' && (node.props['aria-label'] === label || text(node).trim() === label),
  )
  expect(node).toBeDefined()
  return node!
}
function click(root: HostNode, label: string): void {
  const node = button(root, label)
  ;(node.props.onClick as (event: { currentTarget: HostNode }) => void)({ currentTarget: node })
}
function clickAssignment(root: HostNode): void {
  const assignment = find(root, (node) => node.props['data-teacher-action'] === 'journal-open')
  expect(assignment).toBeDefined()
  ;(assignment!.props.onClick as () => void)()
}
function openGroup(root: HostNode, name: string): void {
  const button = find(
    root,
    (node) =>
      node.tag === 'button' &&
      (node.props['aria-label'] === `Открыть группу ${name}` ||
        (text(node).includes(name) &&
          node.children.some((child) => String(child.props.class).includes('teacher-stats__row-name')))),
  )
  expect(button).toBeDefined()
  ;(button!.props.onClick as () => void)()
}
async function settle(): Promise<void> {
  for (let index = 0; index < 30; index++) {
    await Promise.resolve()
    await nextTick()
  }
}
async function chooseStatsContext(root: HostNode): Promise<void> {
  click(root, 'По моим группам')
  await settle()
  click(root, 'Лекция')
  await settle()
}
const apps: App[] = []
afterEach(() => {
  for (const app of apps.splice(0)) app.unmount()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})
const json = (value: unknown): Response => Response.json(value)
const unavailable = (): Response =>
  Response.json({ detail: 'Каталог временно недоступен' }, { status: 503 })
function fixture(
  options: {
    semesterId?: number
    dateFrom?: string
    dateTo?: string
    groupName?: string
    subjectName?: string
    semester?: (attempt: number, success: () => Response) => Promise<Response> | Response
    day?: (date: string, success: () => Response) => Promise<Response> | Response
    assignments?: (success: () => Response) => Promise<Response> | Response
    catalogue?: (attempt: number, success: () => Response) => Promise<Response> | Response
    groupIds?: () => readonly string[]
    failStats?: () => boolean
  } = {},
) {
  const requests: string[] = []
  const dates: string[] = []
  const daySemesterIds: Array<string | null> = []
  const assignmentRanges: Array<[string | null, string | null]> = []
  const assignmentSemesterIds: Array<string | null> = []
  const catalogueSemesterIds: Array<string | null> = []
  const journalQueries: URLSearchParams[] = []
  let catalogues = 0
  let semesters = 0
  const semesterId = options.semesterId ?? 7
  const dateFrom = options.dateFrom ?? '2026-09-01'
  const dateTo = options.dateTo ?? '2027-01-31'
  const groupName = options.groupName ?? 'ИВТ-241'
  const subjectName = options.subjectName ?? 'Программирование'
  const option = { groupId: '101', subjectId: '201', subjectName, lessonTypes: ['lecture'] }
  const assignment = {
    id: '43', teacherId: '71', semesterId, groupId: '101', groupName, subjectId: '201',
    subjectName, lessonType: 'lecture', validFrom: dateFrom, validUntilExclusive: null,
  }
  const catalog = () =>
    json({
      groups: [
        { id: '101', name: groupName },
        { id: '102', name: 'Другая назначенная группа' },
      ].filter(group => !options.groupIds || options.groupIds().includes(group.id)),
      subjectOptions: [option].filter(subject => !options.groupIds || options.groupIds().includes(subject.groupId)),
      taughtSubjectOptions: [option, { ...option, groupId: '102', subjectId: '202' }].filter(subject => !options.groupIds || options.groupIds().includes(subject.groupId)),
      serverNow: '2026-10-09T08:00:00Z',
    })
  const api = markRaw(
    new TeacherApi({
      accessToken: () => null,
      fetcher: async (input) => {
        const { pathname: path, searchParams } = new URL(String(input), 'https://fixture.test')
        requests.push(path)
        if (path.endsWith('/semester')) {
          semesters += 1
          const success = () => json({
            id: String(semesterId),
            name: 'Осень',
            dateFrom,
            dateTo,
          })
          return options.semester ? options.semester(semesters, success) : success()
        }
        if (path.endsWith('/day')) {
          const date = searchParams.get('date')!
          dates.push(date)
          daySemesterIds.push(searchParams.get('semesterId'))
          const success = () => json({
            semesterId: searchParams.get('semesterId'),
            date,
            lessons: date === dateFrom || date === '2026-09-16' ? [{
              ...assignment,
              id: '73',
              assignmentId: assignment.id,
              date,
              lessonNumber: 1,
              status: 'PLANNED',
              oneOff: false,
              moved: false,
              cancelled: false,
            }] : [],
            serverNow: '2026-10-09T08:00:00Z',
          })
          return options.day ? options.day(date, success) : success()
        }
        if (path.endsWith('/assignments')) {
          assignmentRanges.push([searchParams.get('dateFrom'), searchParams.get('dateTo')])
          assignmentSemesterIds.push(searchParams.get('semesterId'))
          const success = () => json([
            { ...assignment, semesterId: searchParams.get('semesterId') },
            {
              ...assignment, semesterId: searchParams.get('semesterId'),
              id: '44', groupId: '102', groupName: 'ИВТ-242',
              subjectId: '202', subjectName: 'Сети', validFrom: dateTo,
            },
          ])
          return options.assignments ? options.assignments(success) : success()
        }
        if (path.endsWith('/lesson-slots'))
          return json({
            timezone: 'Europe/Moscow',
            slots: Array.from({ length: 8 }, (_, index) => ({
              lessonNumber: index + 1,
              startTime: `${index + 9}:00:00`.padStart(8, '0'),
              endTime: `${index + 9}:50:00`.padStart(8, '0'),
            })),
          })
        if (path.endsWith('/catalog')) {
          catalogues += 1
          catalogueSemesterIds.push(searchParams.get('semesterId'))
          return options.catalogue ? options.catalogue(catalogues, catalog) : catalog()
        }
        if (path.endsWith('/journal')) {
          journalQueries.push(new URL(String(input), 'https://fixture.test').searchParams)
          return json({
            lessons: [],
            students: [],
            page: 0,
            pageSize: 100,
            totalLessons: 0,
            hasMore: false,
            serverNow: '2026-10-09T08:00:00Z',
          })
        }
        if (path.endsWith('/stats/export/formats')) return json({ formats: [
          ['docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'docx'],
          ['pdf', 'application/pdf', 'pdf'], ['png', 'application/zip', 'zip'],
          ['html', 'text/html', 'html'], ['xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'xlsx'],
        ].map(([code, contentType, extension]) => ({ code, label: code, contentType, extension })) })
        if (path.endsWith('/formats'))
          return json({
            formats: [{ code: 'html', label: 'HTML', contentType: 'text/html', extension: 'html' }],
          })
        if (path.endsWith('/stats')) {
          if (options.failStats?.()) return unavailable()
          const metric = { numerator: 0, denominator: 1, percent: 0 }
          return json({
            scope: 'groups',
            semesterId: String(semesterId),
            periodFrom: '2026-09-01',
            periodTo: '2026-10-09',
            lessonsCount: 1,
            students: [],
            groups: (options.groupIds?.() ?? ['101']).map(groupId => ({
                groupId,
                groupName: groupId === '101' ? groupName : 'Другая назначенная группа',
                lessonsCount: 1,
                present: metric,
                presentOrExcused: metric,
                excused: metric,
                absent: { numerator: 1, denominator: 1, percent: 100 },
              })),
            subjectOptions: [option],
            serverNow: '2026-10-09T08:00:00Z',
          })
        }
        throw new Error(`Unexpected context scenario path ${path}`)
      },
    }),
  )
  return {
    api,
    requests,
    dates,
    daySemesterIds,
    assignmentRanges,
    assignmentSemesterIds,
    catalogueSemesterIds,
    journalQueries,
    groupName,
    subjectName,
    catalog,
    catalogues: () => catalogues,
  }
}
function mount(api: TeacherApi, reportDownload?: ReportDownloadPort, selectedDate = '2026-10-08') {
  const location = { href: 'https://fixture.test/app', search: '' }
  vi.stubGlobal('window', {
    location,
    history: {
      state: null,
      replaceState(_state: unknown, _title: string, value: string) {
        const next = new URL(value, location.href)
        location.href = next.href
        location.search = next.search
      },
    },
  })
  vi.stubGlobal('Document', class {})
  vi.stubGlobal('ShadowRoot', class {})
  const page = Object.assign(new EventTarget(), { activeElement: null, visibilityState: 'visible' })
  vi.stubGlobal('document', page)
  const state = shallowReactive({ api, semesterId: 7, realtimeRevision: 0 })
  const root = new HostNode('root')
  const app = renderer.createApp({
    render: () =>
      h(TeacherFeatureOwner, {
        api: state.api,
        reportDownload,
        selectedDate,
        semesterId: state.semesterId,
        realtimeRevision: state.realtimeRevision,
      }),
  })
  apps.push(app)
  app.mount(root)
  return { root, state, page }
}

describe('teacher home semester', () => {
  it('keeps all assignments visible while day requests change or complete out of order', async () => {
    let finishOlderDay!: () => void
    const transport = fixture({
      dateFrom: '2026-09-15',
      dateTo: '2027-01-18',
      day: (date, success) => date === '2026-09-16'
        ? new Promise((resolve) => { finishOlderDay = () => resolve(success()) })
        : success(),
    })
    const { root, state } = mount(transport.api, undefined, '2026-08-15')
    await settle()
    expect(button(root, 'Предыдущий день').props.disabled).toBe(true)
    const lesson = () => find(root, (node) => node.props['data-teacher-action'] === 'lesson-open')
    expect(lesson()).toBeDefined()
    const assignmentRows = () => {
      const section = find(root, (node) => node.props['aria-labelledby'] === 'teacher-assignments-title')
      expect(section).toBeDefined()
      expect(text(section!)).toContain('Программирование')
      expect(text(section!)).toContain('Сети')
    }
    assignmentRows()
    click(root, 'Следующий день')
    await settle()
    expect(text(root)).toContain('Загружаем расписание')
    assignmentRows()
    click(root, 'Следующий день')
    await settle()
    expect(lesson()).toBeUndefined()
    assignmentRows()
    finishOlderDay()
    await settle()
    expect(lesson()).toBeUndefined()
    expect(transport.dates).toEqual(['2026-09-15', '2026-09-16', '2026-09-17'])
    expect(transport.assignmentRanges).toEqual([['2026-09-15', '2027-01-18']])

    state.realtimeRevision += 1
    await settle()
    assignmentRows()
    expect(transport.assignmentRanges).toEqual([
      ['2026-09-15', '2027-01-18'],
      ['2026-09-15', '2027-01-18'],
    ])
  })

  it('ignores the former owner’s day and assignments after changing the authenticated semester', async () => {
    let finishDay!: () => void
    let finishAssignments!: () => void
    const obsolete = fixture({
      dateFrom: '2026-10-08',
      groupName: 'Прежняя группа',
      day: (_date, success) =>
        new Promise((resolve) => { finishDay = () => resolve(success()) }),
      assignments: (success) =>
        new Promise((resolve) => { finishAssignments = () => resolve(success()) }),
    })
    const current = fixture({
      semesterId: 8,
      dateFrom: '2026-09-08',
      dateTo: '2026-09-24',
      groupName: 'Текущая группа',
    })
    const { root, state } = mount(obsolete.api)
    await settle()
    state.api = current.api
    state.semesterId = 8
    await settle()
    expect(current.dates).toEqual(['2026-09-24'])
    expect(button(root, 'Следующий день').props.disabled).toBe(true)
    finishDay()
    finishAssignments()
    await settle()
    expect(find(root, (node) => node.props['data-teacher-action'] === 'lesson-open')).toBeUndefined()
    expect(text(root)).toContain('Текущая группа')
    expect(text(root)).not.toContain('Прежняя группа')
  })

  it('waits for the real semester bounds and recovers with the context retry', async () => {
    const transport = fixture({
      semester: (attempt, success) => attempt === 1 ? unavailable() : success(),
    })
    const { root } = mount(transport.api)
    await settle()
    expect(button(root, 'Предыдущий день').props.disabled).toBe(true)
    expect(button(root, 'Следующий день').props.disabled).toBe(true)
    expect(transport.dates).toEqual([])
    expect(transport.assignmentRanges).toEqual([])

    click(root, 'Повторить')
    await settle()
    expect(transport.dates).toEqual(['2026-10-08'])
    expect(transport.assignmentRanges).toEqual([['2026-09-01', '2027-01-31']])
    expect(text(root)).toContain('Мои назначения')
  })

  it('uses the latest active semester during coalesced realtime invalidations and leaves the former journal', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-07T12:00:00Z'))
    let finishObsolete!: () => void
    const transport = fixture({
      semester: (attempt, success) => {
        if (attempt === 1) return success()
        if (attempt === 2) {
          return new Promise((resolve) => {
            finishObsolete = () => resolve(json({
              id: '8', name: 'Следующий', dateFrom: '2098-02-01', dateTo: '2098-06-01',
            }))
          })
        }
        return json({
          id: '9', name: 'Активный', dateFrom: '2026-10-02', dateTo: '2026-11-01',
        })
      },
    })
    const { root, state } = mount(transport.api)
    await settle()
    clickAssignment(root)
    await settle()
    expect(transport.journalQueries.at(-1)?.get('semesterId')).toBe('7')
    state.realtimeRevision += 1
    await settle()
    state.realtimeRevision += 1
    await settle()
    expect(transport.requests.filter((path) => path.endsWith('/semester'))).toHaveLength(2)
    finishObsolete()
    await settle()
    expect(state.semesterId).toBe(7)
    expect(transport.catalogueSemesterIds).toEqual(['7', '9'])
    expect(transport.daySemesterIds).toEqual(['7', '9'])
    expect(transport.assignmentSemesterIds).toEqual(['7', '9'])
    expect(transport.dates.at(-1)).toBe('2026-11-01')
    expect(transport.assignmentRanges.at(-1)).toEqual(['2026-10-02', '2026-11-01'])
    expect(button(root, 'Следующий день').props.disabled).toBe(true)
    clickAssignment(root)
    await settle()
    expect(transport.journalQueries.map((query) => query.get('semesterId'))).toEqual(['7', '9'])
  })

  it('retries a failed semester refresh and clamps the selected day to the changed bounds', async () => {
    let failForegroundCheck!: () => void
    const transport = fixture({
      semester: (attempt, success) => {
        if (attempt === 1) return success()
        if (attempt === 2) return new Promise((resolve) => {
          failForegroundCheck = () => resolve(unavailable())
        })
        return json({
          id: '7', name: 'Осень', dateFrom: '2026-10-09', dateTo: '2026-10-20',
        })
      },
    })
    const { root, page } = mount(transport.api)
    await settle()
    expect(button(root, 'Предыдущий день').props.disabled).toBe(false)
    expect(button(root, 'Следующий день').props.disabled).toBe(false)
    page.visibilityState = 'hidden'
    page.dispatchEvent(new Event('visibilitychange'))
    await settle()
    expect(transport.requests.filter((path) => path.endsWith('/semester'))).toHaveLength(1)
    page.visibilityState = 'visible'
    page.dispatchEvent(new Event('visibilitychange'))
    await settle()
    expect(button(root, 'Предыдущий день').props.disabled).toBe(true)
    expect(button(root, 'Следующий день').props.disabled).toBe(true)
    click(root, 'Предыдущий день')
    await settle()
    expect(transport.dates).toEqual(['2026-10-08'])
    failForegroundCheck()
    await settle()
    expect(text(root)).toContain('Программирование')
    expect(find(root, (node) => node.props['data-teacher-action'] === 'journal-open')).toBeUndefined()
    expect(button(root, 'Предыдущий день').props.disabled).toBe(true)
    expect(button(root, 'Следующий день').props.disabled).toBe(true)
    click(root, 'Следующий день')
    await settle()
    expect(transport.dates).toEqual(['2026-10-08'])
    click(root, 'Повторить')
    await settle()
    expect(button(root, 'Предыдущий день').props.disabled).toBe(true)
    expect(button(root, 'Следующий день').props.disabled).toBe(false)
    expect(transport.dates).toEqual(['2026-10-08', '2026-10-09'])
    expect(transport.assignmentRanges).toEqual([
      ['2026-09-01', '2027-01-31'],
      ['2026-10-09', '2026-10-20'],
    ])
    expect(transport.catalogueSemesterIds).toEqual(['7', '7'])
    expect(find(root, (node) => node.props['data-teacher-action'] === 'journal-open')).toBeDefined()
  })

  it('verifies the semester before choosing a calendar day and can retry inside an open dialog', async () => {
    let failCalendarCheck!: () => void
    const transport = fixture({
      semester: (attempt, success) => {
        if (attempt === 1) return success()
        if (attempt === 2) return new Promise((resolve) => {
          failCalendarCheck = () => resolve(unavailable())
        })
        return json({
          id: '7', name: 'Осень', dateFrom: '2026-10-09', dateTo: '2026-10-20',
        })
      },
    })
    const { root } = mount(transport.api)
    await settle()
    const trigger = find(root, (node) => node.props['aria-controls'] === 'teacher-date-choice')!
    ;(trigger.props.onClick as () => void)()
    await settle()
    const dialog = () => find(root, (node) => node.props.role === 'dialog')!
    const date = (value: string) => find(dialog(), (node) => node.props['data-date'] === value)!
    expect(date('2026-10-09').props.disabled).toBe(true)
    expect(button(dialog(), 'Закрыть календарь')).toBeDefined()
    failCalendarCheck()
    await settle()
    expect(date('2026-10-09').props.disabled).toBe(true)
    click(dialog(), 'Повторить')
    await settle()
    expect(date('2026-10-08').props.disabled).toBe(true)
    expect(date('2026-10-09').props.disabled).toBe(false)
    expect(date('2026-10-09').props['aria-pressed']).toBe(true)
    ;(date('2026-10-10').props.onClick as () => void)()
    await settle()
    expect(dialog()).toBeUndefined()
    expect(transport.dates).toEqual(['2026-10-08', '2026-10-09', '2026-10-10'])
  })

  it('lists all assignments while the catalogue is pending or failed and opens only current group and subject pairs', async () => {
    let finishCatalogue!: () => void
    const transport = fixture({
      catalogue: (attempt, success) => {
        if (attempt === 1) return new Promise((resolve) => {
          finishCatalogue = () => resolve(unavailable())
        })
        return success().json().then((data) => json({
          ...data,
          subjectOptions: [...data.subjectOptions, {
            groupId: '102', subjectId: '202', subjectName: 'Сети', lessonTypes: ['lecture'],
          }],
          taughtSubjectOptions: [
            { ...data.taughtSubjectOptions[0], lessonTypes: ['practice'] },
            { ...data.taughtSubjectOptions[1], subjectId: '201' },
            { ...data.taughtSubjectOptions[1], groupId: '101' },
          ],
        }))
      },
    })
    const { root } = mount(transport.api)
    const row = (name: string) => find(root, (node) =>
      node.props.class === 'teacher-screen__assignment' && text(node).includes(name),
    )
    await settle()
    expect(row('Программирование')?.tag).toBe('div')
    expect(row('Сети')?.tag).toBe('div')
    finishCatalogue()
    await settle()
    expect(row('Программирование')?.tag).toBe('div')
    expect(row('Сети')?.tag).toBe('div')
    click(root, 'Повторить')
    await settle()
    expect(row('Программирование')?.tag).toBe('button')
    expect(row('Сети')?.tag).toBe('div')
    expect(row('Сети')?.props.onClick).toBeUndefined()
    expect(transport.journalQueries).toHaveLength(0)
    clickAssignment(root)
    await settle()
    expect(transport.journalQueries.at(-1)?.getAll('lessonType')).toEqual(['lecture'])
  })
})

describe('teacher live projections', () => {
  it('refreshes the mounted journal and catalog without resetting its selected context', async () => {
    const transport = fixture()
    const view = mount(transport.api)
    await settle()
    clickAssignment(view.root)
    await settle()
    const first = transport.journalQueries.at(-1)!.toString()
    const before = transport.journalQueries.length
    view.state.realtimeRevision += 1
    await settle()
    expect(transport.journalQueries.length).toBe(before + 1)
    expect(transport.journalQueries.at(-1)!.toString()).toBe(first)
    expect(transport.catalogues()).toBe(2)
  })
  it('keeps the original pending statistics export and draft through catalog and stats read failures', async () => {
    let failCatalog = false, failStats = false
    const transport = fixture({ catalogue: (_attempt, success) => failCatalog ? unavailable() : success(), failStats: () => failStats })
    const issued: Array<{ requestId: string; report: unknown }> = []
    let accept!: () => void
    const tickets: string[] = []
    const jobId = '00000000-0000-4000-8000-000000000042'
    const expiresAt = new Date(Date.now() + 60000).toISOString()
    const client = new ReportDownloadClient({ accessToken: () => 'current', assertCurrent: () => undefined,
      fetcher: async (input, init) => {
        const path = String(input)
        if (path === '/api/auth/export-jobs') {
          issued.push(JSON.parse(String(init?.body)))
          return new Promise<Response>(resolve => { accept = () => resolve(Response.json({
            jobId, state: 'ready', expiresAt, suggestedFilename: 'stats.docx', errorCode: null,
          })) })
        }
        tickets.push(path)
        return Response.json({ downloadPath: `/api/report-download/${'a'.repeat(43)}`, expiresAt, suggestedFilename: 'stats.docx' })
      },
    })
    const port: ReportDownloadPort = { carrier: 'browser', download: async (request, current, state) => {
      await client.issueTicket(request, state, current)
      return 'accepted'
    } }
    const view = mount(transport.api, markRaw(port))
    await settle()
    click(view.root, 'Итоги')
    await settle()
    await chooseStatsContext(view.root)
    click(view.root, 'Скачать')
    await settle()
    expect(issued).toHaveLength(1)
    const originalId = issued[0]!.requestId
    expect(originalId).toMatch(/^[0-9a-f-]{36}$/)
    const control = find(view.root, node => node.tag === 'section' && String(node.props.class).includes('rct-export--inline'))!
    expect(control.props['aria-busy']).toBe(true)
    const filter = find(view.root, node => node.props['data-teacher-action'] === 'stats-filters')!
    ;(filter.props.onClick as () => void)()
    await settle()
    const draft = find(view.root, node => node.props.id === 'teacher-stats-present-min')!
    ;(draft.props['onUpdate:modelValue'] as (value: string) => void)('37')
    for (const failure of ['catalog', 'stats']) {
      failCatalog = failure === 'catalog'; failStats = failure === 'stats'
      view.state.realtimeRevision += 1
      await settle()
      expect(find(view.root, node => node === control)).toBe(control)
      expect(control.props['aria-busy']).toBe(true)
      expect(draft.value).toBe('37')
      click(view.root, 'Скачать')
      expect(issued.map(command => command.requestId)).toEqual([originalId])
    }
    accept()
    await settle()
    expect(tickets).toEqual([`/api/auth/export-jobs/${jobId}/ticket`])
    expect(control.props['aria-busy']).toBe(false)
    expect(text(control)).toContain('Файл готов')
    expect(issued.map(command => command.requestId)).toEqual([originalId])
  })

  it('withdraws the deleted group from the active statistics projection while retaining the other group', async () => {
    let removed = false
    const transport = fixture({ groupIds: () => removed ? ['102'] : ['101', '102'] })
    const view = mount(transport.api)
    await settle()
    click(view.root, 'Итоги')
    await settle()
    await chooseStatsContext(view.root)
    expect(text(view.root)).toContain(transport.groupName)
    expect(text(view.root)).toContain('Другая назначенная группа')
    removed = true
    view.state.realtimeRevision += 1
    await settle()
    expect(text(view.root)).not.toContain(transport.groupName)
    expect(text(view.root)).toContain('Другая назначенная группа')
    click(view.root, 'По студентам')
    await settle()
    expect(text(view.root)).toContain('Найди студента по имени')
    expect(text(view.root)).not.toContain('Другая назначенная группа')
    expect(view.state.api).toBe(transport.api)
  })

  it('refreshes statistics beside a real filter draft without replacing its input', async () => {
    const transport = fixture()
    const view = mount(transport.api)
    await settle()
    click(view.root, 'Итоги')
    await settle()
    await chooseStatsContext(view.root)
    const filter = find(
      view.root,
      (node) => node.tag === 'button' && node.props['data-teacher-action'] === 'stats-filters',
    )
    expect(filter).toBeDefined()
    ;(filter!.props.onClick as () => void)()
    await settle()
    const draft = find(
      view.root,
      (node) => node.tag === 'input' && node.props.id === 'teacher-stats-present-min',
    )
    expect(draft).toBeDefined()
    ;(draft!.props['onUpdate:modelValue'] as (value: string) => void)('37')
    const before = transport.requests.filter((path) => path.endsWith('/stats')).length
    view.state.realtimeRevision += 1
    await settle()
    view.state.realtimeRevision += 1
    await settle()
    expect(transport.requests.filter((path) => path.endsWith('/stats'))).toHaveLength(before + 2)
    expect(find(view.root, (node) => node === draft)).toBe(draft)
    expect(draft!.value).toBe('37')
  })
})

describe('teacher owner catalogue recovery', () => {
  it('forwards a home assignment type into the real journal query and selected type', async () => {
    const transport = fixture()
    const view = mount(transport.api)
    await settle()
    clickAssignment(view.root)
    await settle()
    const lecture = find(
      view.root,
      (node) => node.tag === 'button' && text(node).trim() === 'Лекция',
    )
    expect(lecture?.props['aria-pressed']).toBe(true)
    const query = transport.journalQueries.at(-1)
    expect(query?.get('semesterId')).toBe('7')
    expect(query?.get('groupId')).toBe('101')
    expect(query?.get('subjectId')).toBe('201')
    expect(query?.getAll('lessonType')).toEqual(['lecture'])
    expect(query?.get('pastOnly')).toBe('true')
    expect(
      find(view.root, (node) => node.props.role === 'status' && text(node).trim().length > 0),
    ).toBeDefined()
  })

  it('recovers the current groups catalogue once while preserving slots and semester', async () => {
    let complete!: () => void
    const transport = fixture({
      catalogue: (attempt, success) =>
        attempt === 2
          ? unavailable()
          : attempt === 3
            ? new Promise((resolve) => {
                complete = () => resolve(success())
              })
            : success(),
    })
    const view = mount(transport.api)
    await settle()
    click(view.root, 'Итоги')
    await settle()
    expect(
      find(view.root, (node) => node.tag === 'button' && text(node).trim() === 'Повторить'),
    ).toBeDefined()
    expect(transport.requests.filter((path) => path.endsWith('/stats'))).toHaveLength(0)
    click(view.root, 'Повторить')
    await settle()
    expect(
      find(view.root, (node) => node.tag === 'button' && text(node).trim() === 'Повторить'),
    ).toBeUndefined()
    expect(transport.catalogues()).toBe(3)
    expect(transport.requests.filter((path) => path.endsWith('/stats'))).toHaveLength(0)
    complete()
    await settle()
    await chooseStatsContext(view.root)
    expect(text(view.root)).toContain(transport.groupName)
    expect(
      find(view.root, (node) => node.tag === 'button' && text(node).trim() === 'Повторить'),
    ).toBeUndefined()
    expect(transport.requests.filter((path) => path.endsWith('/stats'))).toHaveLength(1)
    expect(transport.requests.filter((path) => path.endsWith('/semester'))).toHaveLength(1)
    expect(transport.requests.filter((path) => path.endsWith('/lesson-slots'))).toHaveLength(1)
    click(view.root, 'Сегодня')
    await settle()
    click(view.root, 'Итоги')
    await settle()
    expect(transport.catalogues()).toBe(3)
    expect(text(view.root)).toContain(transport.groupName)
  })

  it('ignores an obsolete catalogue retry after authenticated API and semester context change', async () => {
    let complete!: () => void
    const obsolete = fixture({
      catalogue: (attempt, success) =>
        attempt === 2
          ? unavailable()
          : attempt === 3
            ? new Promise((resolve) => {
                complete = () => resolve(success())
              })
            : success(),
    })
    const view = mount(obsolete.api)
    await settle()
    click(view.root, 'Итоги')
    await settle()
    expect(
      find(view.root, (node) => node.tag === 'button' && text(node).trim() === 'Повторить'),
    ).toBeDefined()
    click(view.root, 'Повторить')
    await settle()
    expect(obsolete.catalogues()).toBe(3)
    const current = fixture({ semesterId: 8, groupName: 'Новая группа', subjectName: 'Новые сети' })
    view.state.api = current.api
    view.state.semesterId = 8
    await settle()
    complete()
    await settle()
    expect(current.requests.filter((path) => path.endsWith('/stats'))).toHaveLength(0)
    click(view.root, 'Итоги')
    await settle()
    await chooseStatsContext(view.root)
    expect(text(view.root)).toContain(current.groupName)
    expect(text(view.root)).not.toContain(obsolete.groupName)
    openGroup(view.root, current.groupName)
    await settle()
    expect(text(view.root)).toContain('Мои предметы')
    expect(text(view.root)).toContain(current.subjectName)
    expect(text(view.root)).not.toContain(obsolete.subjectName)
    click(view.root, current.subjectName)
    await settle()
    expect(text(view.root)).toContain(current.subjectName)
    expect(
      find(view.root, (node) => node.tag === 'button' && text(node).trim() === 'Повторить'),
    ).toBeUndefined()
  })
})
