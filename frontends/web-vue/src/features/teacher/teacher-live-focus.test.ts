import { createRenderer, markRaw, nextTick, type App } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

const realtime = vi.hoisted(() => ({ changed: (): void => undefined }))
vi.mock('../../../../mobile-core/src/features/notifications/notifications-realtime', () => ({
  createGenerationBoundNotificationsRealtime: (options: { onChanged(): void }) => {
    realtime.changed = options.onChanged
    return { dispose() {} }
  },
}))

class HostDocument extends EventTarget {
  activeElement: HostNode | null = null
  body: HostNode | null = null
}
let document: HostDocument
class HostNode {
  children: HostNode[] = []
  parent: HostNode | null = null
  props: Record<string, unknown> = {}
  events = new Map<string, Array<(event: { target: HostNode }) => void>>()
  text = ''
  value = ''
  _value: unknown
  selected = false
  selectedIndex = -1
  checked = false
  constructor(readonly tag: string) { markRaw(this) }
  get options() { return this.children.filter(n => n.tag === 'option') }
  get type() { return this.props.type ?? 'text' }
  get tagName() { return this.tag.toUpperCase() }
  get isConnected(): boolean { return this.tag === 'root' || !!this.parent?.isConnected }
  getRootNode() { return document }
  focus() {
    document.activeElement = this
    this.focusIn()
  }
  focusIn() {
    ;(this.props.onFocusin as (() => void) | undefined)?.()
    this.parent?.focusIn()
  }
  addEventListener(name: string, fn: (event: { target: HostNode }) => void) {
    this.events.set(name, [...(this.events.get(name) ?? []), fn])
  }
  input(value: string) {
    this.value = value
    this.events.get('input')?.forEach(fn => fn({ target: this }))
    ;(this.props.onInput as ((event: { target: HostNode }) => void) | undefined)?.({ target: this })
  }
}
const nodes = (n: HostNode): HostNode[] => [n, ...n.children.flatMap(nodes)]
const text = (n: HostNode): string => n.text + n.children.map(text).join(' ')
const renderer = createRenderer<HostNode, HostNode>({
  createElement: tag => new HostNode(tag),
  createText: value => Object.assign(new HostNode('#text'), { text: value }),
  createComment: () => new HostNode('#comment'),
  insertStaticContent: (content, parent, anchor) => {
    const node = Object.assign(new HostNode('#static'), { text: content, parent })
    const at = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(at < 0 ? parent.children.length : at, 0, node)
    return [node, node]
  },
  setText: (node, value) => { node.text = value },
  setElementText: (node, value) => { node.text = value; node.children = [] },
  parentNode: node => node.parent,
  nextSibling: node => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _old, value) => {
    node.props[key] = value
    if (key === 'value') { node.value = String(value ?? ''); node._value = value }
  },
  insert: (node, parent, anchor = null) => {
    if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1)
    const at = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(at < 0 ? parent.children.length : at, 0, node)
    node.parent = parent
  },
  remove: node => {
    if (document.activeElement && nodes(node).includes(document.activeElement)) document.activeElement = null
    node.parent?.children.splice(node.parent.children.indexOf(node), 1)
    node.parent = null
  },
})
let app: App | undefined
let invalidate: (() => void) | undefined
afterEach(() => {
  app?.unmount(); app = undefined
  invalidate?.(); invalidate = undefined
  vi.unstubAllGlobals(); vi.resetModules()
})
async function settle() {
  for (let n = 0; n < 8; n++) { await new Promise<void>(resolve => setImmediate(resolve)); await nextTick() }
}
function click(root: HostNode, name: string) {
  const button = nodes(root).find(n => n.tag === 'button' && (n.props['aria-label'] === name || text(n).trim() === name))
  expect(button).toBeDefined()
  ;(button!.props.onClick as (event: { currentTarget: HostNode }) => void)({ currentTarget: button! })
}
const metric = { numerator: 1, denominator: 2, percent: 50 }
const subject = { groupId: '10', subjectId: '100', subjectName: 'Предмет контроля', lessonTypes: ['lecture'] }
const initialCatalog = {
  groups: [{ id: '10', name: 'Контрольная группа' }, { id: '20', name: 'Удаляемая группа' }],
  subjectOptions: [subject, { ...subject, groupId: '20', subjectId: '200' }],
  taughtSubjectOptions: [subject], serverNow: '2026-10-24T09:00:00Z',
}
async function mounted(screen: 'stats' | 'journal' = 'stats') {
  document = new HostDocument()
  vi.stubGlobal('document', document); vi.stubGlobal('Document', HostDocument)
  vi.stubGlobal('ShadowRoot', class {}); vi.stubGlobal('HTMLElement', HostNode)
  vi.stubGlobal('window', new EventTarget())
  const storage = new Map<string, string>()
  vi.stubGlobal('sessionStorage', { getItem: (k: string) => storage.get(k) ?? null, setItem: (k: string, v: string) => storage.set(k, v), removeItem: (k: string) => storage.delete(k) })
  let catalog = initialCatalog
  let catalogFailure = 0, statsFailure = 0
  let catalogHold: Promise<void> | null = null, statsHold: Promise<void> | null = null
  let projection = 'Прежняя студентка контроля'
  const exports: Array<{ requestId: string; report: unknown }> = []
  const journalQueries: URLSearchParams[] = []
  vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), 'http://fixture.test')
    if (url.pathname.endsWith('/login')) return Response.json({ accessToken: 'synthetic-bearer' })
    if (url.pathname.endsWith('/session')) return Response.json({
      sessionId: '00000000-0000-4000-8000-000000000002', userId: '2', displayName: 'Преподаватель', sessionVersion: '1', rolesVersion: '1', activeRole: 'TEACHER', readOnly: false,
      roles: [{ grantId: '2', role: 'TEACHER', status: 'ACTIVE', selectable: true, readOnly: false }],
      passwordPolicy: { minCodePoints: 12, maxUtf8Bytes: 72, requiresDecimalDigit: true, specialCategories: ['P', 'S'], normalization: 'NONE' },
    })
    if (url.pathname.endsWith('/semester')) return Response.json({ id: '1', name: 'Осень', dateFrom: '2026-09-01', dateTo: '2027-01-31' })
    if (url.pathname.endsWith('/catalog')) {
      await catalogHold
      return catalogFailure ? new Response(null, { status: catalogFailure }) : Response.json(catalog)
    }
    if (url.pathname.endsWith('/stats')) {
      await statsHold
      return statsFailure ? new Response(null, { status: statsFailure }) : Response.json({
        scope: 'students', semesterId: '1', periodFrom: null, periodTo: null, lessonsCount: 2,
        students: [{ studentId: '1000', displayName: projection, present: metric, presentOrExcused: metric, excused: metric, absent: metric }], groups: [], subjectOptions: catalog.subjectOptions, serverNow: '2026-10-24T09:00:00Z',
      })
    }
    if (url.pathname.endsWith('/journal')) {
      journalQueries.push(new URLSearchParams(url.search))
      return Response.json({
        lessons: [{ id: '300', groupId: '10', groupName: 'Контрольная группа', subjectId: '100', subjectName: 'Предмет контроля', semesterId: '1', lessonType: 'lecture', date: '2026-10-20', lessonNumber: 1, status: 'CLOSED', oneOff: false, moved: false, cancelled: false }],
        students: [{ studentId: '1000', displayName: projection, cells: [] }],
        serverNow: '2026-10-24T09:00:00Z', page: 0, pageSize: 30, totalLessons: 1, hasMore: false,
      })
    }
    if (url.pathname === '/api/auth/export-jobs') {
      exports.push(JSON.parse(String(init?.body)))
      throw new TypeError('Synthetic lost export acknowledgement')
    }
    throw new Error('Unexpected fixture request')
  })
  const { webSession } = await import('../../auth/session')
  invalidate = webSession.invalidate
  await webSession.login('teacher', 'fixture')
  const { default: Page } = screen === 'stats' ? await import('./TeacherStatsPage.vue') : await import('./TeacherJournalPage.vue')
  const path = screen === 'stats' ? '/teacher/statistics' : '/teacher/journal'
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path, component: Page },
    { path: '/teacher/lessons/:lessonId', name: 'teacher-lesson', component: { render: () => null } },
  ] })
  await router.push(path + '?group=10&subject=100&type=lecture')
  const root = new HostNode('root')
  app = renderer.createApp(Page); app.use(router); app.mount(root)
  await settle()
  expect(text(root)).toContain(projection)
  const group = () => nodes(root).find(n => n.tag === 'select' && n.options.some(o => text(o) === 'Контрольная группа'))!
  const openDraft = async () => {
    click(root, 'Фильтр: Присутствия'); await nextTick()
    const field = nodes(root).find(n => n.tag === 'input' && n.props.type === 'number')!
    field.input('37'); field.focus(); await nextTick()
    return field
  }
  return {
    root, group, openDraft, exports, journalQueries, webSession,
    changed: () => realtime.changed(),
    removeOtherGroup: () => { catalog = { ...initialCatalog, groups: initialCatalog.groups.slice(0, 1), subjectOptions: [subject] } },
    removeCurrentScope: (keepOther = false) => { catalog = { ...initialCatalog, groups: keepOther ? initialCatalog.groups.slice(1) : [], subjectOptions: keepOther ? initialCatalog.subjectOptions.slice(1) : [], taughtSubjectOptions: [] } },
    projection: (name: string) => { projection = name },
    denyCatalog: (status: number) => { catalogFailure = status },
    denyStats: (status: number) => { statsFailure = status },
    holdCatalog: () => { let release!: () => void; catalogHold = new Promise<void>(resolve => { release = resolve }); return () => { catalogHold = null; release() } },
    holdStats: () => { let release!: () => void; statsHold = new Promise<void>(resolve => { release = resolve }); return () => { statsHold = null; release() } },
  }
}

describe('teacher live reads while a mounted filter is focused', () => {
  it('removes the deleted target option and refreshes the authorized control without replacing its draft, focus or unknown export', async () => {
    const f = await mounted()
    click(f.root, 'Экспортировать'); await settle()
    const originalExport = f.exports[0]!
    const field = await f.openDraft(), group = f.group()
    const releaseCatalog = f.holdCatalog(), releaseStats = f.holdStats()
    f.removeOtherGroup(); f.projection('Обновлённая студентка контроля'); f.changed(); await settle()
    expect(group.props.disabled).toBe(false)
    expect(document.activeElement).toBe(field)
    releaseCatalog(); await settle()
    expect(group.options.map(text)).not.toContain('Удаляемая группа')
    expect(group.options[group.selectedIndex]?.value).toBe('10')
    expect(field.isConnected).toBe(true)
    expect(field.value).toBe('37')
    expect(document.activeElement).toBe(field)
    expect(text(f.root)).toContain('Прежняя студентка контроля')
    releaseStats(); await settle()
    expect(text(f.root)).toContain('Обновлённая студентка контроля')
    expect(field.isConnected).toBe(true)
    expect(field.value).toBe('37')
    expect(document.activeElement).toBe(field)
    expect(f.group()).toBe(group)
    expect(f.exports).toEqual([originalExport])
    click(f.root, 'Проверить выгрузку'); await settle()
    expect(f.exports[1]).toEqual(originalExport)
  })

  it('retires the cached roster and focused private draft when the catalog proves its scope is gone', async () => {
    const f = await mounted(), field = await f.openDraft()
    f.removeCurrentScope(); f.changed(); await settle()
    expect(text(f.root)).not.toContain('Прежняя студентка контроля')
    expect(text(f.root)).not.toContain('Удаляемая группа')
    expect(field.isConnected).toBe(false)
    expect(document.activeElement).not.toBe(field)
  })

  it.each(['catalog', 'stats'] as const)('withdraws the private projection after a background %s denial despite focus', async target => {
    const f = await mounted(), field = await f.openDraft()
    if (target === 'catalog') f.denyCatalog(403)
    else f.denyStats(403)
    f.changed(); await settle()
    expect(text(f.root)).not.toContain('Прежняя студентка контроля')
    expect(field.isConnected).toBe(false)
  })

  it('clears a lost current scope before waiting for the alternative authorized roster', async () => {
    const f = await mounted(), field = await f.openDraft(), release = f.holdStats()
    f.removeCurrentScope(true); f.changed(); await settle()
    expect(text(f.root)).not.toContain('Прежняя студентка контроля')
    expect(field.isConnected).toBe(false)
    f.projection('Студентка другой доступной группы'); release(); await settle()
    expect(text(f.root)).toContain('Студентка другой доступной группы')
  })

  it('preserves a focused filter and its original unknown export through a transient read failure', async () => {
    const f = await mounted()
    click(f.root, 'Экспортировать'); await settle()
    const original = f.exports[0]!, field = await f.openDraft()
    f.denyStats(503); f.changed(); await settle()
    expect(text(f.root)).toContain('Прежняя студентка контроля')
    expect(field.isConnected).toBe(true)
    expect(field.value).toBe('37')
    expect(document.activeElement).toBe(field)
    click(f.root, 'Проверить выгрузку'); await settle()
    expect(f.exports[1]).toEqual(original)
    f.denyStats(0); f.projection('Восстановленная студентка контроля'); f.changed(); await settle()
    expect(text(f.root)).toContain('Восстановленная студентка контроля')
    expect(document.activeElement).toBe(field)
  })

  it('refreshes the applied journal query while an unapplied date draft stays dirty and focused', async () => {
    const f = await mounted('journal')
    const field = nodes(f.root).find(n => n.tag === 'input' && n.props.type === 'date')!
    field.input('2026-10-01'); field.focus(); await nextTick()
    f.removeOtherGroup(); f.projection('Обновлённая студентка контроля'); f.changed(); await settle()
    expect(f.group().options.map(text)).not.toContain('Удаляемая группа')
    expect(text(f.root)).toContain('Обновлённая студентка контроля')
    expect(field.isConnected).toBe(true)
    expect(field.value).toBe('2026-10-01')
    expect(document.activeElement).toBe(field)
    expect(f.journalQueries.at(-1)?.get('groupId')).toBe('10')
    expect(f.journalQueries.at(-1)?.has('dateFrom')).toBe(false)
    expect(f.journalQueries.at(-1)?.has('dateTo')).toBe(false)
  })

  it('ignores a late background roster after its admitted session retires', async () => {
    const f = await mounted(), field = await f.openDraft(), release = f.holdStats()
    f.projection('Поздний приватный ответ'); f.changed(); await settle()
    f.webSession.invalidate(); await settle()
    expect(field.isConnected).toBe(false)
    release(); await settle()
    expect(text(f.root)).not.toContain('Поздний приватный ответ')
    expect(text(f.root)).not.toContain('Прежняя студентка контроля')
  })
})
