import { createRenderer, h, markRaw, nextTick, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TeacherStatsScreen from './TeacherStatsScreen.vue'
import {
  TeacherApi,
  createGenerationBoundTeacherApi,
  toTeacherStatsReportSelector,
} from './teacher-client'
import {
  teacherStatsQueryForContext,
  readTeacherStatsContext,
  readRecoverableTeacherStatsContext,
  teacherStatsContextParams,
} from './teacher-stats-route'
import { StaleSessionGenerationError } from '../../shared/session-owner'

class Node {
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

const subjectOptions = [
  {
    groupId: '2',
    subjectId: '3',
    subjectName: 'Программирование',
    lessonTypes: ['lecture', 'PrAcTiCe', 'lab'],
  },
]
const catalog = {
  groups: [{ id: '2', name: 'ИКБО-01-24' }],
  subjectOptions,
  taughtSubjectOptions: [],
  serverNow: '2026-10-08T09:00:00Z',
}
const metric = { numerator: 1, denominator: 2, percent: 50 }
const json = (value: unknown) =>
  new Response(JSON.stringify(value), { headers: { 'Content-Type': 'application/json' } })
async function settle(): Promise<void> {
  for (let i = 0; i < 20; i++) {
    await Promise.resolve()
    await nextTick()
  }
}
function harness(
  options: {
    delayedCatalog?: boolean
    emptyCatalog?: boolean
    route?: string
    assignedOnly?: boolean
    noData?: boolean
    multipleSubjects?: boolean
  } = {},
) {
  const location = {
    href: `https://fixture.test/app${options.route ?? ''}`,
    search: options.route ?? '',
  }
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
  let delayCatalog = options.delayedCatalog ?? false
  let failStats = false
  let releaseCatalog: (() => void) | null = null
  let hold = false
  let release: (() => void) | null = null
  const queries: URLSearchParams[] = []
  const searches: URLSearchParams[] = []
  const exports: URLSearchParams[] = []
  let holdSearch = false
  let releaseSearch: (() => void) | null = null
  let failSearch = false
  const currentCatalog = options.multipleSubjects
    ? {
        ...catalog,
        subjectOptions: [
          ...subjectOptions,
          { ...subjectOptions[0]!, subjectId: '5', subjectName: 'Алгоритмы' },
        ],
      }
    : catalog
  const api = new TeacherApi({
    accessToken: () => null,
    fetcher: async (input) => {
      const url = new URL(String(input), 'http://fixture.test')
      if (url.pathname.endsWith('/catalog')) {
        const reply = json(
          options.emptyCatalog
            ? { ...catalog, groups: [], subjectOptions: [] }
            : options.assignedOnly
              ? { ...catalog, subjectOptions: [], taughtSubjectOptions: subjectOptions }
              : currentCatalog,
        )
        if (delayCatalog) {
          delayCatalog = false
          return new Promise<Response>((resolve) => {
            releaseCatalog = () => resolve(reply)
          })
        }
        return reply
      }
      if (url.pathname.endsWith('/formats'))
        return json({
          formats: [
            [
              'docx',
              'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
              'docx',
            ],
            ['pdf', 'application/pdf', 'pdf'],
            ['png', 'application/zip', 'zip'],
            ['html', 'text/html', 'html'],
            ['xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'xlsx'],
          ].map(([code, contentType, extension]) => ({
            code,
            label: code,
            contentType,
            extension,
          })),
        })
      if (url.pathname.endsWith('/search')) {
        searches.push(url.searchParams)
        if (failSearch) {
          failSearch = false
          return new Response(JSON.stringify({ code: 'DEPENDENCY_UNAVAILABLE' }), { status: 503 })
        }
        const reply = json({
          groups: [{ id: '2', name: holdSearch ? 'Устаревший поиск' : 'ИКБО-01-24' }],
          students: [
            { id: '4', displayName: 'Анна Белова', groupId: '2', groupName: 'ИКБО-01-24' },
          ],
          truncated: false,
        })
        if (holdSearch) {
          holdSearch = false
          return new Promise<Response>((resolve) => {
            releaseSearch = () => resolve(reply)
          })
        }
        return reply
      }
      if (url.pathname.endsWith('/stats/export')) {
        exports.push(url.searchParams)
        return new Response('<!doctype html><title>Teacher no-data report</title>', {
          headers: {
            'Content-Type': 'text/html',
            'Content-Disposition': 'attachment; filename="teacher-stats.html"',
          },
        })
      }
      if (url.pathname.endsWith('/stats')) {
        queries.push(url.searchParams)
        if (failStats) {
          failStats = false
          return new Response(JSON.stringify({ code: 'DEPENDENCY_UNAVAILABLE' }), { status: 503 })
        }
        const students = url.searchParams.get('scope') === 'students'
        const value = options.noData ? { numerator: 0, denominator: 0, percent: 0 } : metric
        const row = { present: value, presentOrExcused: value, excused: value, absent: value }
        const reply = json({
          semesterId: '7',
          scope: students ? 'students' : 'groups',
          lessonsCount: options.noData ? 0 : 2,
          periodFrom: null,
          periodTo: null,
          subjectOptions: options.assignedOnly ? [] : subjectOptions,
          students:
            students && !options.noData
              ? [{ ...row, studentId: '4', displayName: 'Анна Белова' }]
              : [],
          groups: students
            ? []
            : [
                {
                  ...row,
                  groupId: '2',
                  groupName: hold ? 'Устаревшая группа' : 'ИКБО-01-24',
                  lessonsCount: options.noData ? 0 : 2,
                },
              ],
          serverNow: catalog.serverNow,
        })
        if (hold) {
          hold = false
          return new Promise<Response>((resolve) => {
            release = () => resolve(reply)
          })
        }
        return reply
      }
      throw new Error(`Unexpected fixture path ${url.pathname}`)
    },
  })
  const root = new Node('root')
  const app = renderer.createApp({
    render: () => h(TeacherStatsScreen, { api: markRaw(api), semesterId: 7 }),
  })
  apps.push(app)
  app.mount(root)
  return {
    root,
    api,
    queries,
    searches,
    exports,
    holdSearch: () => {
      holdSearch = true
    },
    releaseSearch: () => releaseSearch?.(),
    failNextSearch: () => {
      failSearch = true
    },
    delayCatalog: () => {
      delayCatalog = true
    },
    failNextStats: () => {
      failStats = true
    },
    releaseCatalog: () => releaseCatalog?.(),
    hold: () => {
      hold = true
    },
    release: () => release?.(),
  }
}
async function chooseContext(root: Node, chooseType = true): Promise<void> {
  if (find(root, (item) => item.props['aria-label'] === 'Группа')) {
    click(root, 'Группа')
    await settle()
    click(root, 'ИКБО-01-24')
    await settle()
    click(root, 'Предмет')
    await settle()
    click(root, 'Программирование')
    await settle()
  }
  if (chooseType) {
    click(root, 'Лекция')
    await settle()
  }
}
async function searchDirectory(root: Node, query: string): Promise<void> {
  const input = find(root, (item) => item.props.id === 'teacher-stats-directory')!
  ;(input.props['onUpdate:modelValue'] as (value: string) => void)(query)
  const form = find(root, (item) => item.props.role === 'search')!
  ;(form.props.onSubmit as (event: { preventDefault(): void }) => void)({ preventDefault() {} })
  await settle()
}
describe('teacher statistics selectors and races', () => {
  it('defers lens and sort controls until delayed catalog arrives without discarding its context', async () => {
    const fixture = harness({ delayedCatalog: true })
    await settle()
    expect(
      find(fixture.root, (item) => item.tag === 'button' && text(item).trim() === 'По студентам')
        ?.props.disabled,
    ).toBe(true)
    expect(
      find(fixture.root, (item) => item.props['aria-label'] === 'Сортировка')?.props.disabled,
    ).toBe(true)
    // Custom host events deliberately bypass native disabled handling; the
    // domain handlers must still preserve the pending catalog generation.
    click(fixture.root, 'По студентам')
    click(fixture.root, 'Добавить условие сортировки')
    await settle()
    expect(fixture.queries).toHaveLength(0)
    fixture.releaseCatalog()
    await settle()
    expect(
      find(fixture.root, (item) => item.tag === 'button' && text(item).trim() === 'По студентам')
        ?.props.disabled,
    ).toBe(false)
    click(fixture.root, 'По студентам')
    await settle()
    expect(fixture.queries).toHaveLength(0)
    await chooseContext(fixture.root)
    expect(fixture.queries.at(-1)?.get('groupId')).toBe('2')
    expect(fixture.queries.at(-1)?.get('subjectId')).toBe('3')
    expect(text(fixture.root)).toContain('Анна Белова')
  })
  it('labels lowercase and mixed catalog types distinctly while preserving their wire values', async () => {
    const fixture = harness()
    await settle()
    await chooseContext(fixture.root, false)
    expect(fixture.queries).toHaveLength(0)
    expect(text(fixture.root)).toContain('Лекция')
    expect(text(fixture.root)).toContain('Практика')
    expect(text(fixture.root)).toContain('Лабораторная')
    click(fixture.root, 'Лекция')
    await settle()
    expect(fixture.queries.at(-1)?.getAll('lessonType')).toEqual(['lecture'])
  })
  it('preserves catalog retry with an open filter while all/apply/reset events are attempted', async () => {
    const fixture = harness()
    await settle()
    await chooseContext(fixture.root, false)
    click(fixture.root, 'Фильтры')
    await settle()
    fixture.failNextStats()
    click(fixture.root, 'Лекция')
    await settle()
    expect(text(fixture.root)).toContain('Статистика недоступна')
    fixture.delayCatalog()
    click(fixture.root, 'Повторить')
    await settle()
    const before = fixture.queries.length
    click(fixture.root, 'Лекция')
    const form = find(fixture.root, (item) => item.tag === 'form')!
    ;(form.props.onSubmit as (event: { preventDefault(): void }) => void)({ preventDefault() {} })
    click(fixture.root, 'Сбросить фильтры и сортировку')
    await settle()
    expect(fixture.queries).toHaveLength(before)
    expect(
      find(fixture.root, (item) => item.props['aria-label'] === 'Скачать статистику'),
    ).toBeUndefined()
    fixture.releaseCatalog()
    await settle()
    expect(text(fixture.root)).toContain('ИКБО-01-24')
    expect(text(fixture.root)).not.toContain('Статистика недоступна')
    expect(fixture.queries.at(-1)?.getAll('lessonType')).toEqual(['lecture'])
    expect(
      find(fixture.root, (item) => item.tag === 'button' && text(item).trim() === 'По студентам')
        ?.props.disabled,
    ).toBe(false)
  })
  it('shows a successful empty catalog without requesting an unauthorized groups aggregate', async () => {
    const fixture = harness({ emptyCatalog: true })
    await settle()
    expect(fixture.queries).toHaveLength(0)
    expect(text(fixture.root)).toContain('Нет доступных групп.')
    expect(text(fixture.root)).not.toContain('Статистика недоступна')
    expect(
      find(fixture.root, (item) => item.props['aria-label'] === 'Скачать статистику'),
    ).toBeUndefined()
  })

  it('releases catalog loading before the first aggregate so a newer lens and sort can finish independently', async () => {
    const fixture = harness()
    await settle()
    fixture.hold()
    await chooseContext(fixture.root)
    expect(fixture.queries).toHaveLength(1)
    click(fixture.root, 'По студентам')
    await settle()
    expect(fixture.queries).toHaveLength(1)
    await chooseContext(fixture.root, false)
    click(fixture.root, 'Лекция')
    await settle()
    expect(fixture.queries.at(-1)?.get('scope')).toBe('students')
    expect(text(fixture.root)).toContain('Анна Белова')
    expect(
      find(fixture.root, (item) => item.props['aria-label'] === 'Скачать статистику'),
    ).toBeUndefined()
    click(fixture.root, 'Сортировка')
    await settle()
    click(fixture.root, 'Отсутствия: сначала выше')
    await settle()
    expect(fixture.queries.at(-1)?.getAll('sort')).toEqual(['-absent'])
    expect(text(fixture.root)).toContain('Анна Белова')
    expect(
      find(fixture.root, (item) => item.props['aria-label'] === 'Скачать статистику'),
    ).toBeUndefined()
    fixture.release()
    await settle()
    expect(text(fixture.root)).toContain('Анна Белова')
    expect(text(fixture.root)).not.toContain('Устаревшая группа')
  })
  it('keeps independent lens ordering and ignores the older group response after switching to students', async () => {
    const fixture = harness()
    await settle()
    await chooseContext(fixture.root)
    fixture.hold()
    click(fixture.root, 'Сортировка')
    await settle()
    click(fixture.root, 'Отсутствия: сначала выше')
    await settle()
    click(fixture.root, 'По студентам')
    await settle()
    await chooseContext(fixture.root, false)
    click(fixture.root, 'Лекция')
    await settle()
    expect(fixture.queries.at(-1)?.get('scope')).toBe('students')
    expect(fixture.queries.at(-1)?.getAll('sort')).toEqual(['present'])
    fixture.release()
    await settle()
    expect(text(fixture.root)).toContain('Анна Белова')
    expect(text(fixture.root)).not.toContain('Устаревшая группа')
    click(fixture.root, 'По моим группам')
    await settle()
    expect(fixture.queries.at(-1)?.getAll('sort')).toEqual(['-absent'])
    click(fixture.root, 'Добавить условие сортировки')
    await settle()
    expect(fixture.queries.at(-1)?.getAll('sort')).toEqual(['-absent', 'groupName'])
    click(fixture.root, 'Удалить условие 2')
    await settle()
    expect(fixture.queries.at(-1)?.getAll('sort')).toEqual(['-absent'])
  })
  it('loads the authorized groups hub without selecting a pair and requires explicit subject drilldown', async () => {
    const fixture = harness({ multipleSubjects: true })
    await settle()
    expect(fixture.queries).toHaveLength(0)
    expect(find(fixture.root, (node) => node.props['aria-label'] === 'Группа')).toBeUndefined()
    click(fixture.root, 'Лекция')
    await settle()
    expect(fixture.queries).toHaveLength(1)
    expect(fixture.queries[0]?.get('scope')).toBe('groups')
    expect(fixture.queries[0]?.has('groupId')).toBe(false)
    expect(fixture.queries[0]?.has('subjectId')).toBe(false)
    expect(fixture.queries[0]?.getAll('sort')).toEqual(['present'])
    const group = find(
      fixture.root,
      (node) =>
        node.tag === 'button' && text(node).includes('ИКБО-01-24') && text(node).includes('2 пар'),
    )!
    ;(group.props.onClick as () => void)()
    await settle()
    expect(fixture.queries).toHaveLength(1)
    expect(find(fixture.root, (node) => node.props['aria-label'] === 'Предмет')).toBeDefined()
    expect(find(fixture.root, (node) => node.props.class === 'teacher-stats__card')).toBeUndefined()
    click(fixture.root, 'Предмет')
    await settle()
    expect(
      find(
        fixture.root,
        (node) => node.props.role === 'option' && node.props['aria-label'] === 'Алгоритмы',
      ),
    ).toBeDefined()
    expect(
      find(
        fixture.root,
        (node) => node.props.role === 'option' && node.props['aria-label'] === 'Программирование',
      ),
    ).toBeDefined()
    click(fixture.root, 'Программирование')
    await settle()
    expect(fixture.queries).toHaveLength(1)
    click(fixture.root, 'Лекция')
    await settle()
    expect(fixture.queries.at(-1)?.get('scope')).toBe('students')
    expect(fixture.queries.at(-1)?.get('groupId')).toBe('2')
    expect(fixture.queries.at(-1)?.get('subjectId')).toBe('3')
    const before = fixture.queries.length
    click(fixture.root, 'Группа')
    await settle()
    const option = find(
      fixture.root,
      (node) => node.props.role === 'option' && node.props['aria-label'] === 'ИКБО-01-24',
    )!
    ;(option.props.onClick as () => void)()
    await settle()
    expect(fixture.queries).toHaveLength(before)
    expect(find(fixture.root, (node) => node.props.class === 'teacher-stats__card')).toBeUndefined()
    click(fixture.root, 'По моим группам')
    await settle()
    expect(fixture.queries.at(-1)?.get('scope')).toBe('groups')
    expect(fixture.queries.at(-1)?.getAll('lessonType')).toEqual(['lecture'])
  })
  it('uses the same absent-pair scope for routes and exports while rejecting partial or unscoped students', async () => {
    const fixture = harness()
    await settle()
    const query = { semesterId: 7, scope: 'groups' as const, lessonTypes: ['lecture'] }
    await fixture.api.stats(query)
    await fixture.api.exportStats(query, 'html')
    expect(fixture.exports[0]?.has('groupId')).toBe(false)
    expect(fixture.exports[0]?.has('subjectId')).toBe(false)
    expect(toTeacherStatsReportSelector(query, 'html')).toEqual({ ...query, format: 'html' })
    const context = readTeacherStatsContext(
      new URLSearchParams({
        teacherStats: '1',
        teacherStatsSemester: '7',
        teacherStatsScope: 'groups',
        teacherStatsType: 'lecture',
      }),
    )!
    expect(teacherStatsQueryForContext(context)).toMatchObject(query)
    expect(teacherStatsContextParams(context).has('teacherStatsGroup')).toBe(false)
    for (const malformed of [
      'teacherStatsGroup=0&teacherStatsSubject=0',
      'teacherStatsGroup=NaN',
      'teacherStatsGroup=2',
      'teacherStatsSubject=3',
      'teacherStatsScope=unknown',
    ]) {
      const params = new URLSearchParams(`teacherStats=1&teacherStatsSemester=7&${malformed}`)
      expect(readRecoverableTeacherStatsContext(params).context).toBeNull()
    }
    const sent = fixture.queries.length
    expect(() => fixture.api.stats({ ...query, groupId: 2 })).toThrow(RangeError)
    expect(() => fixture.api.stats({ ...query, subjectId: 3 })).toThrow(RangeError)
    expect(() => fixture.api.stats({ ...query, scope: 'students' })).toThrow(RangeError)
    expect(() => fixture.api.stats({ ...query, lessonTypes: [] })).toThrow(RangeError)
    expect(fixture.queries).toHaveLength(sent)
    await fixture.api.stats({ ...query, groupId: 2, subjectId: 3 })
    expect(fixture.queries.at(-1)?.get('groupId')).toBe('2')
    expect(fixture.queries.at(-1)?.get('subjectId')).toBe('3')
  })
  it('searches independently, discards older replies and selects only a group from a student result', async () => {
    const fixture = harness()
    await settle()
    fixture.holdSearch()
    await searchDirectory(fixture.root, 'БЁЛОВА анна')
    expect(fixture.queries).toHaveLength(0)
    expect(fixture.searches.at(-1)?.get('q')).toBe('БЁЛОВА анна')
    await searchDirectory(fixture.root, 'Анна')
    fixture.releaseSearch()
    await settle()
    expect(text(fixture.root)).not.toContain('Устаревший поиск')
    click(fixture.root, 'Анна Белова ИКБО-01-24')
    await settle()
    expect(fixture.queries).toHaveLength(0)
    expect(find(fixture.root, (node) => node.props['aria-label'] === 'Предмет')).toBeDefined()
    expect(
      find(fixture.root, (node) => node.tag === 'button' && text(node).trim() === 'Лекция'),
    ).toBeUndefined()
    click(fixture.root, 'По студентам')
    await settle()
    expect(fixture.searches).toHaveLength(2)
    expect(fixture.queries).toHaveLength(0)
    click(fixture.root, 'Очистить поиск')
    await settle()
    expect(
      find(fixture.root, (node) => node.props['aria-label'] === 'Результаты поиска'),
    ).toBeUndefined()
    await searchDirectory(fixture.root, 'я')
    await searchDirectory(fixture.root, '   ')
    expect(fixture.searches).toHaveLength(2)
  })
  it('keeps search failure and retry independent of metric filters', async () => {
    const fixture = harness()
    await settle()
    await chooseContext(fixture.root)
    const before = fixture.queries.length
    fixture.failNextSearch()
    await searchDirectory(fixture.root, 'Анна')
    expect(text(fixture.root)).toContain('Поиск недоступен')
    expect(fixture.queries).toHaveLength(before)
    click(fixture.root, 'Повторить поиск')
    await settle()
    expect(fixture.searches).toHaveLength(2)
    expect(fixture.queries).toHaveLength(before)
    expect(fixture.queries.at(-1)?.getAll('filter')).toEqual([])
  })
  it('restores a validated complete group route and retires only its legacy name search', async () => {
    const params = new URLSearchParams({
      teacherStats: '1',
      teacherStatsSemester: '7',
      teacherStatsScope: 'groups',
      teacherStatsGroup: '2',
      teacherStatsSubject: '3',
      teacherStatsType: 'lecture',
      teacherStatsCriteria: '1',
      teacherStatsSearch: 'я'.repeat(200),
      teacherStatsSort: '-absent',
      teacherStatsFilter: JSON.stringify({ column: 'present', minPercent: 25 }),
    })
    const context = readTeacherStatsContext(params)!
    expect(context.search).toBe('')
    expect(teacherStatsContextParams(context).get('teacherStatsGroup')).toBe('2')
    expect(teacherStatsQueryForContext(context)?.filters).toEqual([
      { column: 'present', minPercent: 25 },
    ])
    const fixture = harness({ route: `?${params}` })
    await settle()
    expect(fixture.queries).toHaveLength(1)
    expect(fixture.queries[0]?.has('groupId')).toBe(false)
    expect(fixture.queries[0]?.has('subjectId')).toBe(false)
    expect(fixture.queries[0]?.getAll('filter')).toEqual(['present>=25'])
    expect(fixture.queries[0]?.getAll('sort')).toEqual(['-absent'])
  })
  it('rejects a stale search session and validates canonical string identity without exposing extra fields', async () => {
    let generation = 1
    let release!: (reply: Response) => void
    const api = createGenerationBoundTeacherApi(
      {
        currentGeneration: () => generation,
        accessTokenFor: () => null,
        refreshFor: async () => {},
      },
      async () =>
        new Promise<Response>((resolve) => {
          release = resolve
        }),
    )
    const pending = api.search(7, 'Анна')
    generation = 2
    release(json({ groups: [], students: [], truncated: false }))
    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    let sentInit: RequestInit | undefined
    const valid = new TeacherApi({
      accessToken: () => null,
      fetcher: async (_input, init) => {
        sentInit = init
        return json({
          groups: [{ id: '2', name: 'ИКБО', login: 'hidden' }],
          students: [
            {
              id: '9007199254740993',
              displayName: 'Анна',
              groupId: '2',
              groupName: 'ИКБО',
              phone: 'hidden',
            },
          ],
          truncated: true,
        })
      },
    })
    const result = await valid.search(7, 'Анна')
    expect(sentInit?.cache).toBe('no-store')
    expect(result.students[0]?.id).toBe('9007199254740993')
    expect(result.groups[0]).toEqual({ id: '2', name: 'ИКБО' })
    expect(Object.keys(result.students[0]!)).toEqual(['id', 'displayName', 'groupId', 'groupName'])
    const invalid = new TeacherApi({
      accessToken: () => null,
      fetcher: async () =>
        json({ groups: [{ id: '02', name: 'ИКБО' }], students: [], truncated: false }),
    })
    await expect(invalid.search(7, 'Анна')).rejects.toThrow('идентификатор')
  })
  it.each(['groups', 'students'] as const)(
    'allows assigned subjects without scheduled lessons in %s after its required choice',
    async (scope) => {
      const fixture = harness({ assignedOnly: true, noData: true })
      await settle()
      if (scope === 'students') {
        click(fixture.root, 'По студентам')
        await settle()
      }
      expect(fixture.queries).toHaveLength(0)
      await chooseContext(fixture.root, false)
      expect(fixture.queries).toHaveLength(0)
      expect(
        find(fixture.root, (node) => node.tag === 'button' && text(node).trim() === 'Лекция'),
      ).toBeDefined()
      click(fixture.root, 'Лекция')
      await settle()
      expect(fixture.queries).toHaveLength(1)
      expect(fixture.queries[0]?.get('scope')).toBe(scope)
      expect(fixture.queries[0]?.get('groupId')).toBe(scope === 'students' ? '2' : null)
      expect(fixture.queries[0]?.get('subjectId')).toBe(scope === 'students' ? '3' : null)
      expect(fixture.queries[0]?.getAll('lessonType')).toEqual(['lecture'])
      expect(text(fixture.root)).not.toContain('Статистика недоступна')
      if (scope === 'groups') expect(text(fixture.root)).toContain('0 пар')
      else
        expect(
          find(fixture.root, (node) => node.props.class === 'teacher-stats__card'),
        ).toBeUndefined()
      const report = await fixture.api.exportStats(
        { semesterId: 7, scope, groupId: 2, subjectId: 3, lessonTypes: ['lecture'] },
        'html',
      )
      expect(report.filename).toBe('teacher-stats.html')
      expect(fixture.exports[0]?.get('scope')).toBe(scope)
      expect(fixture.exports[0]?.get('groupId')).toBe('2')
      expect(fixture.exports[0]?.get('subjectId')).toBe('3')
      expect(fixture.exports[0]?.getAll('lessonType')).toEqual(['lecture'])
    },
  )
  it('rejects late catalog data after session generation changes and malformed catalog context', async () => {
    let generation = 1
    let release!: (reply: Response) => void
    const api = createGenerationBoundTeacherApi(
      {
        currentGeneration: () => generation,
        accessTokenFor: () => null,
        refreshFor: async () => {},
      },
      async () =>
        new Promise<Response>((resolve) => {
          release = resolve
        }),
    )
    const pending = api.catalog(7)
    generation = 2
    release(json(catalog))
    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    const invalidApi = new TeacherApi({
      accessToken: () => null,
      fetcher: async () => json({ ...catalog, groups: [] }),
    })
    await expect(invalidApi.catalog(7)).rejects.toThrow('несогласованный каталог')
    const malformedApi = new TeacherApi({
      accessToken: () => null,
      fetcher: async () =>
        json({ ...catalog, subjectOptions: [{ ...subjectOptions[0], lessonTypes: null }] }),
    })
    await expect(malformedApi.catalog(7)).rejects.toThrow('некорректные типы занятий')
    const emptyApi = new TeacherApi({
      accessToken: () => null,
      fetcher: async () =>
        json({
          groups: [],
          subjectOptions: [],
          taughtSubjectOptions: [],
          serverNow: catalog.serverNow,
        }),
    })
    await expect(emptyApi.catalog(7)).resolves.toMatchObject({ groups: [], subjectOptions: [] })
  })
})
