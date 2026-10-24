import { createRenderer, h, markRaw, nextTick, shallowReactive, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TeacherFeatureOwner from './TeacherFeatureOwner.vue'
import { TeacherApi, type TeacherJournalQuery } from '../../features/teacher/teacher-client'

// The day emitter is a seam; the real owner, journal, query mapping and rendered type remain mounted.
vi.mock('../../features/teacher/TeacherHomeScreen.vue', async () => {
  const { h } = await import('vue')
  return {
    default: {
      emits: ['open-journal'],
      setup(
        _props: unknown,
        { emit }: { emit: (event: 'open-journal', query: TeacherJournalQuery) => void },
      ) {
        return () =>
          h(
            'button',
            {
              onClick: () =>
                emit('open-journal', {
                  semesterId: 7,
                  groupId: 101,
                  subjectId: 201,
                  lessonType: 'lecture',
                }),
            },
            'Открыть журнал назначения',
          )
      },
    },
  }
})

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
  focus(): void {}
  addEventListener(): void {}
  removeEventListener(): void {}
  getRootNode() {
    return document
  }
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
function click(root: HostNode, label: string): void {
  const button = find(
    root,
    (node) =>
      node.tag === 'button' && (node.props['aria-label'] === label || text(node).trim() === label),
  )
  expect(button).toBeDefined()
  ;(button!.props.onClick as () => void)()
}
function openGroup(root: HostNode, name: string): void {
  const button = find(
    root,
    (node) =>
      node.tag === 'button' &&
      text(node).includes(name) &&
      node.children.some((child) => String(child.props.class).includes('teacher-stats__row-name')),
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
  click(root, 'Лекция')
  await settle()
}
const apps: App[] = []
afterEach(() => {
  for (const app of apps.splice(0)) app.unmount()
  vi.unstubAllGlobals()
})
const json = (value: unknown): Response => Response.json(value)
const unavailable = (): Response =>
  Response.json({ detail: 'Каталог временно недоступен' }, { status: 503 })
function fixture(
  options: {
    semesterId?: number
    groupName?: string
    subjectName?: string
    catalogue?: (attempt: number, success: () => Response) => Promise<Response> | Response
    groupIds?: () => readonly string[]
  } = {},
) {
  const requests: string[] = []
  const journalQueries: URLSearchParams[] = []
  let catalogues = 0
  const semesterId = options.semesterId ?? 7
  const groupName = options.groupName ?? 'ИВТ-241'
  const subjectName = options.subjectName ?? 'Программирование'
  const option = { groupId: '101', subjectId: '201', subjectName, lessonTypes: ['lecture'] }
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
        const path = new URL(String(input), 'https://fixture.test').pathname
        requests.push(path)
        if (path.endsWith('/semester'))
          return json({
            id: String(semesterId),
            name: 'Осень',
            dateFrom: '2026-09-01',
            dateTo: '2027-01-31',
          })
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
        if (path.endsWith('/formats'))
          return json({
            formats: [{ code: 'html', label: 'HTML', contentType: 'text/html', extension: 'html' }],
          })
        if (path.endsWith('/stats')) {
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
    journalQueries,
    groupName,
    subjectName,
    catalog,
    catalogues: () => catalogues,
  }
}
function mount(api: TeacherApi) {
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
  vi.stubGlobal('document', {
    activeElement: null,
    addEventListener() {},
    removeEventListener() {},
  })
  const state = shallowReactive({ api, semesterId: 7, realtimeRevision: 0 })
  const root = new HostNode('root')
  const app = renderer.createApp({
    render: () =>
      h(TeacherFeatureOwner, {
        api: state.api,
        semesterId: state.semesterId,
        realtimeRevision: state.realtimeRevision,
      }),
  })
  apps.push(app)
  app.mount(root)
  return { root, state }
}

describe('teacher live projections', () => {
  it('refreshes the mounted journal and catalog without resetting its selected context', async () => {
    const transport = fixture()
    const view = mount(transport.api)
    await settle()
    click(view.root, 'Открыть журнал назначения')
    await settle()
    const first = transport.journalQueries.at(-1)!.toString()
    const before = transport.journalQueries.length
    view.state.realtimeRevision += 1
    await settle()
    expect(transport.journalQueries.length).toBe(before + 1)
    expect(transport.journalQueries.at(-1)!.toString()).toBe(first)
    expect(transport.catalogues()).toBe(2)
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
    click(view.root, 'По студентам')
    await settle()
    click(view.root, 'Выбери группу')
    await settle()
    expect(text(view.root)).toContain('Другая назначенная группа')
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
    click(view.root, 'Открыть журнал назначения')
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
    click(view.root, 'Предмет')
    await settle()
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
