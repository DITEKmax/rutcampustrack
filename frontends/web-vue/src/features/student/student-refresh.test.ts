import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createRenderer,
  defineComponent,
  h,
  nextTick,
  type App,
  type Component,
  type FunctionalComponent,
} from 'vue'

const server = vi.hoisted(() => ({
  status: 'ABSENT',
  failAttendance: false,
  requestsVisible: false,
  attendanceReads: 0,
  requestReads: 0,
  subjectTypes: [] as string[][],
  rankingPages: [] as (number | null)[],
  invalidate: () => {},
}))
vi.mock('../../auth/session', async () => {
  const { ref } = await import('vue')
  return {
    webSession: {
      identity: ref('student-unit-owner'),
      generation: ref(0),
      snapshot: ref({
        userId: '15',
        sessionId: 'unit-session',
        activeRole: 'STUDENT',
        readOnly: false,
        roles: [{ role: 'STUDENT', groupId: '4' }],
      }),
    },
  }
})
vi.mock('../../app/assistant', () => ({ webAssistantAccess: { load: async () => [] } }))
vi.mock('../../../../mobile-core/src/features/notifications/notifications-realtime', () => ({
  createGenerationBoundNotificationsRealtime: (options: { onChanged: () => void }) => {
    server.invalidate = options.onChanged
    return { dispose() {} }
  },
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({
    query: { view: 'subjects', subject: '4', type: 'LECTURE', kind: 'EXCUSE', lesson: '64' },
    params: {},
  }),
  useRouter: () => ({ replace: async () => {} }),
  onBeforeRouteLeave() {},
}))
vi.mock('../../app/api', () => ({
  createWebTransport: () => ({
    accessToken: () => null,
    assertCurrent() {},
    onUnauthorized: async () => {},
    fetcher: async (input: RequestInfo | URL) => {
      const url = new URL(String(input), 'https://unit.invalid')
      const metrics = {
        present: {
          count: server.status === 'PRESENT' ? 1 : 0,
          percent: server.status === 'PRESENT' ? 100 : 0,
        },
        presentOrExcused: { count: 0, percent: 0 },
        excused: { count: 0, percent: 0 },
        absent: { count: 1, percent: 100 },
        held: 1,
        planned: 2,
      }
      const lesson = {
        id: '64',
        date: '2026-10-24',
        number: '1',
        subject: { id: '4', name: 'Предмет' },
        type: 'LECTURE',
        schedule: { startsAt: '08:30:00', endsAt: '09:50:00', room: 'Аудитория' },
        status: server.status,
        requestOptions: [],
      }
      const requestLesson = {
        id: '64',
        date: '2026-10-24',
        subjectId: '4',
        subjectName: 'Предмет',
        subjectType: 'lecture',
        status: 'planned',
        blocked: false,
        lessonNumber: 1,
      }
      const summary = {
        id: 'saved-request',
        kind: 'EXCUSE',
        status: 'PENDING',
        origin: 'MANUAL',
        createdAt: '2026-10-24T08:00:00Z',
        updatedAt: '2026-10-24T08:00:00Z',
        lessons: [requestLesson],
      }
      if (url.pathname.endsWith('/session'))
        return Response.json({
          user: { id: '15', displayName: 'Студент' },
          activeRole: 'STUDENT',
          group: { id: '4', name: 'Группа' },
          semester: { id: '2', name: 'Семестр', startsOn: '2026-10-19', endsOn: '2026-12-31' },
          capabilities: ['TODAY', 'GEO_CHECKIN'],
          readOnly: false,
          rolesVersion: '1',
          sessionId: 'unit-session',
          sessionVersion: '1',
          serverNow: '2026-10-24T08:00:00Z',
        })
      if (url.pathname.endsWith('/attendance')) {
        server.attendanceReads++
        if (server.failAttendance)
          return Response.json({ detail: 'Связь потеряна' }, { status: 503 })
        return Response.json({
          semester: { id: '2', name: 'Семестр', startsOn: '2026-10-19', endsOn: '2026-12-31' },
          serverNow: '2026-10-24T08:00:00Z',
          terminalReadOnly: false,
          metrics,
          days: [
            {
              date: '2026-10-24',
              weekday: 'Сб',
              dayNumber: '24',
              state: 'CURRENT',
              lessons: [lesson],
            },
          ],
          subjects: [
            {
              id: '4',
              name: 'Предмет',
              typeCards: ['LECTURE', 'PRACTICE'].map((type) => ({ type, metrics, history: [] })),
            },
          ],
          graph: { days: [], weeks: [] },
        })
      }
      if (url.pathname.includes('/statistics/subjects/')) {
        server.subjectTypes.push(url.searchParams.getAll('types'))
        return Response.json({
          subjectId: '4',
          name: 'Предмет',
          selectedTypes: url.searchParams.getAll('types'),
          availableTypes: ['LECTURE', 'PRACTICE'],
          selectedAggregate: metrics,
          series: [],
          typeCards: [],
        })
      }
      if (url.pathname.endsWith('/statistics/ranking')) {
        const page = url.searchParams.has('page') ? Number(url.searchParams.get('page')) : null
        server.rankingPages.push(page)
        const effective = page ?? 1
        return Response.json({
          available: true,
          page: effective,
          size: 20,
          total: 45,
          ownPosition: 26,
          rows: Array.from({ length: effective === 2 ? 5 : 20 }, (_, offset) => {
            const index = effective * 20 + offset
            return {
              id: index === 25 ? '15' : String(index + 100),
              name: `Участник ${index + 1}`,
              position: index + 1,
              percentage: 50,
              isSelf: index === 25,
            }
          }),
        })
      }
      if (url.pathname.endsWith('/requests/options'))
        return Response.json({
          reasons: [{ code: 'ILLNESS', label: 'Болезнь', commentRequired: false }],
          files: {
            maxFiles: 2,
            maxBytesPerFile: 100000,
            maxBytesTotal: 200000,
            contentTypes: ['application/pdf'],
            extensions: ['.pdf'],
          },
          budget: { semesterId: '2', limit: 5, used: 0, remaining: 5 },
          lessons: [
            {
              lesson: requestLesson,
              excuseEligible: true,
              lateCheckinEligible: false,
              pendingRequests: [],
            },
          ],
        })
      if (url.pathname.endsWith('/requests')) {
        server.requestReads++
        return Response.json({
          content: server.requestsVisible ? [summary] : [],
          page: 0,
          size: 10,
          totalElements: server.requestsVisible ? 1 : 0,
          totalPages: server.requestsVisible ? 1 : 0,
        })
      }
      if (url.pathname.endsWith('/requests/saved-request'))
        return Response.json({
          summary,
          reason: 'ILLNESS',
          comment: 'Новая заявка',
          decision: null,
          attachments: [],
        })
      throw Error(`Unexpected request ${url.pathname}`)
    },
  }),
}))

class TestNode {
  text = ''
  value: unknown = ''
  type = ''
  multiple = false
  checked = false
  selected = false
  open = false
  props: Record<string, unknown> = {}
  children: TestNode[] = []
  parent: TestNode | null = null
  events = new Map<string, (event: unknown) => void>()
  private selection = -1
  constructor(readonly tag: string) {}
  get tagName() {
    return this.tag.toUpperCase()
  }
  get options() {
    return this.children.filter((node) => node.tag === 'option')
  }
  get selectedIndex() {
    return this.selection
  }
  set selectedIndex(value: number) {
    this.selection = value
    this.options.forEach((node, index) => {
      node.selected = index === value
    })
    this.value = this.options[value]?.value ?? ''
  }
  addEventListener(name: string, action: (event: unknown) => void) {
    this.events.set(name, action)
  }
  removeEventListener(name: string) {
    this.events.delete(name)
  }
  getRootNode() {
    return document
  }
  focus() {
    testDocument.activeElement = this
  }
  scrollIntoView() {}
  contains(node: TestNode) {
    return flatten(this).includes(node)
  }
  querySelector(selector: string) {
    return (
      flatten(this).find((node) =>
        selector
          .split(',')
          .some((tag) =>
            tag.startsWith('.')
              ? String(node.props.class).split(' ').includes(tag.slice(1))
              : node.tag === tag,
          ),
      ) ?? null
    )
  }
  getAttribute(key: string) {
    return this.props[key] ?? null
  }
  setAttribute(key: string, value: string) {
    this.props[key] = value
  }
  removeAttribute(key: string) {
    delete this.props[key]
  }
}
const testDocument = { activeElement: null as TestNode | null }
function flatten(node: TestNode): TestNode[] {
  return [node, ...node.children.flatMap(flatten)]
}
function text(node: TestNode): string {
  return node.tag === '#comment' ? '' : node.text + node.children.map(text).join('')
}
function remove(node: TestNode) {
  if (node.parent) node.parent.children = node.parent.children.filter((child) => child !== node)
  node.parent = null
}
const renderer = createRenderer<TestNode, TestNode>({
  createElement: (tag) => new TestNode(tag),
  createText: (value) => Object.assign(new TestNode('#text'), { text: value }),
  createComment: (value) => Object.assign(new TestNode('#comment'), { text: value }),
  patchProp(node, key, _old, value) {
    node.props[key] = value
    if (key === 'value') node.value = value
    if (key === 'type') node.type = String(value)
    if (key === 'checked') node.checked = Boolean(value)
  },
  insert(node, parent, anchor) {
    remove(node)
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, node)
    node.parent = parent
  },
  remove,
  setText(node, value) {
    node.text = value
  },
  setElementText(node, value) {
    node.text = value
    node.children = []
  },
  parentNode: (node) => node.parent,
  nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
})
const apps: App[] = []
async function settle() {
  for (let i = 0; i < 64; i++) {
    await Promise.resolve()
    await nextTick()
  }
}
const RouterLinkStub: FunctionalComponent = (_props, { slots }) => h('a', slots.default?.())
function mount(component: Component) {
  const root = new TestNode('root')
  const app = renderer.createApp(component)
  app.component('RouterLink', RouterLinkStub)
  app.mount(root)
  apps.push(app)
  return root
}
function click(node: TestNode) {
  ;(node.props.onClick as (event: unknown) => void)?.({ currentTarget: node })
  return settle()
}
function button(root: TestNode, label: string) {
  const node = flatten(root).find((node) => node.tag === 'button' && text(node).trim() === label)
  if (!node) throw Error(`Missing action ${label}`)
  return node
}
function mark(root: TestNode) {
  const node = flatten(root).find((node) =>
    String(node.props.class).includes('student-mark-button'),
  )
  if (!node) throw Error('No mark')
  return node
}
function lecture(root: TestNode) {
  const node = flatten(root).find((node) => node.tag === 'input' && node.value === 'LECTURE')
  if (!node) throw Error('No lecture filter')
  return node
}
beforeEach(() => {
  server.status = 'ABSENT'
  server.failAttendance = false
  server.requestsVisible = false
  server.attendanceReads = 0
  server.requestReads = 0
  server.subjectTypes = []
  server.rankingPages = []
  testDocument.activeElement = null
  const events = new EventTarget()
  vi.stubGlobal('window', {
    addEventListener: events.addEventListener.bind(events),
    removeEventListener: events.removeEventListener.bind(events),
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
  })
  vi.stubGlobal('document', testDocument)
  vi.stubGlobal('navigator', { onLine: true })
})
afterEach(() => {
  for (const app of apps.splice(0)) app.unmount()
  vi.unstubAllGlobals()
  vi.resetModules()
})

describe('student SFC live reading and retained selections', () => {
  it('updates the authoritative mark under ordinary hover/focus without replacing its focused cell or selected type', async () => {
    const { default: Page } = await import('./StudentAttendancePage.vue')
    const root = mount(Page)
    await settle()
    const cell = mark(root)
    cell.focus()
    const region = flatten(root).find(
      (node) => node.props['data-screen-id'] === 'WEB-STUDENT-ATTENDANCE',
    )!
    ;(region.props.onPointerenter as (() => void) | undefined)?.()
    ;(region.props.onFocusin as (() => void) | undefined)?.()
    server.status = 'PRESENT'
    server.invalidate()
    await settle()
    expect(text(mark(root))).toBe('+')
    expect(mark(root)).toBe(cell)
    expect(testDocument.activeElement).toBe(cell)
    expect(lecture(root).checked).toBe(true)
    expect(server.subjectTypes.at(-1)).toEqual(['LECTURE'])
  })
  it('keeps the selected types and own rows after a failed refresh and successful explicit retry', async () => {
    const { default: Page } = await import('./StudentAttendancePage.vue')
    const root = mount(Page)
    await settle()
    expect(lecture(root).checked).toBe(true)
    server.failAttendance = true
    server.invalidate()
    await settle()
    expect(lecture(root).checked).toBe(true)
    expect(text(mark(root))).toBe('н')
    server.failAttendance = false
    await click(button(root, 'Повторить'))
    expect(lecture(root).checked).toBe(true)
    expect(server.subjectTypes.at(-1)).toEqual(['LECTURE'])
    expect(text(mark(root))).toBe('н')
  })
  it('defers server replacement while the actual request form has a draft, then publishes the pending update after closing it', async () => {
    const { default: Page } = await import('./StudentRequestsPage.vue')
    const root = mount(Page)
    await settle()
    const textarea = flatten(root).find((node) => node.tag === 'textarea')!
    textarea.value = 'Сохрани мой черновик'
    textarea.events.get('input')?.({ target: textarea })
    await settle()
    const reads = server.requestReads
    server.requestsVisible = true
    server.invalidate()
    await settle()
    expect(server.requestReads).toBe(reads)
    expect(textarea.value).toBe('Сохрани мой черновик')
    expect(text(root)).not.toContain('Заявка saved-request')
    await click(button(root, 'Закрыть форму'))
    expect(text(root)).toContain('Заявка saved-request')
  })
  it('can read both earlier and later cohort pages around the server-selected own page and return to the own position', async () => {
    const { default: Ranking } = await import('./StudentRanking.vue')
    const { useStudentScope } = await import('./student-state')
    const Root = defineComponent({
      setup() {
        const scope = useStudentScope()
        return () => h(Ranking, { scope })
      },
    })
    const root = mount(Root)
    await settle()
    expect(text(root)).toContain('Участник 26')
    expect(text(root)).not.toContain('Участник 1 ')
    await click(button(root, 'Предыдущие участники'))
    expect(text(root)).toContain('Участник 1')
    await click(button(root, 'Следующие участники'))
    expect(text(root)).toContain('Участник 45')
    expect(flatten(root).filter((node) => node.tag === 'li')).toHaveLength(45)
    await click(button(root, 'К моей позиции'))
    expect(flatten(root).filter((node) => node.tag === 'li')).toHaveLength(20)
    expect(server.rankingPages).toEqual([null, 0, 2, null])
  })
})
