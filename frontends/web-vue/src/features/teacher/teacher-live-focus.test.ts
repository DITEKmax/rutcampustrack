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
  get selectedIndex() { return this.options.findIndex(n => n.props['aria-selected'] === true) }
  checked = false
  constructor(readonly tag: string) { markRaw(this) }
  get options() { return this.props['aria-haspopup'] === 'listbox' && this.parent ? nodes(this.parent).filter(n => n.props.role === 'option') : this.children.filter(n => n.tag === 'option') }
  get type() { return this.props.type ?? 'text' }
  get tagName() { return this.tag.toUpperCase() }
  get isConnected(): boolean { return this.tag === 'root' || !!this.parent?.isConnected }
  getRootNode() { return document }
  matches() { return false }
  setCustomValidity(message: string) { this.props.validationMessage = message }
  querySelector(selector: string) {
    const group = /^\[data-teacher-group="(\d+)"\]$/.exec(selector)
    return nodes(this).find(n => group ? String(n.props['data-teacher-group']) === group[1] : n.tag === selector) ?? null
  }
  focus() {
    const previous = document.activeElement
    if (previous && previous !== this) (previous.props.onBlur as (() => void) | undefined)?.()
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
async function mounted(screen: 'stats' | 'journal' = 'stats', journalPages = 1) {
  document = new HostDocument()
  vi.stubGlobal('document', document); vi.stubGlobal('Document', HostDocument)
  vi.stubGlobal('ShadowRoot', class {}); vi.stubGlobal('HTMLElement', HostNode)
  vi.stubGlobal('window', new EventTarget())
  const storage = new Map<string, string>()
  vi.stubGlobal('sessionStorage', { getItem: (k: string) => storage.get(k) ?? null, setItem: (k: string, v: string) => storage.set(k, v), removeItem: (k: string) => storage.delete(k) })
  let catalog = initialCatalog
  let catalogFailure = 0, statsFailure = 0
  let catalogHold: Promise<void> | null = null, statsHold: Promise<void> | null = null
  let exportHold: Promise<void> | null = null
  let projection = 'Прежняя студентка контроля'
  const exports: Array<{ requestId: string; report: unknown }> = []
  const journalQueries: URLSearchParams[] = []
  const statsQueries: URLSearchParams[] = []
  const journalHolds = new Map<number, Promise<void>>()
  const journalPageMetadata = new Map<number, { pageSize: number; totalLessons: number }>()
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
      statsQueries.push(new URLSearchParams(url.search))
      await statsHold
      const scope = url.searchParams.get('scope')
      const noMatches = url.searchParams.getAll('filter').some(filter => filter.endsWith('~несуществующий'))
      return statsFailure ? new Response(null, { status: statsFailure }) : Response.json({
        scope, semesterId: '1', periodFrom: null, periodTo: null, lessonsCount: 2,
        students: scope === 'students' && !noMatches ? [{ studentId: '1000', displayName: projection, present: metric, presentOrExcused: metric, excused: metric, absent: metric }] : [],
        groups: scope === 'groups' && !noMatches ? [{ groupId: '20', groupName: 'Удаляемая группа', lessonsCount: 2, present: metric, presentOrExcused: metric, excused: metric, absent: metric }] : [],
        subjectOptions: catalog.subjectOptions, serverNow: '2026-10-24T09:00:00Z',
      })
    }
    if (url.pathname.endsWith('/journal')) {
      journalQueries.push(new URLSearchParams(url.search))
      const page = Number(url.searchParams.get('page') ?? 0), size = journalPages > 1 ? 30 : 1
      await journalHolds.get(page)
      return Response.json({
        lessons: Array.from({ length: size }, (_, index) => ({ id: String(300 + page * size + index), groupId: '10', groupName: 'Контрольная группа', subjectId: '100', subjectName: 'Предмет контроля', semesterId: '1', lessonType: 'lecture', date: '2026-10-20', lessonNumber: index + 1, status: 'CLOSED', oneOff: false, moved: false, cancelled: false })),
        students: [{ studentId: '1000', displayName: projection, cells: [] }],
        serverNow: '2026-10-24T09:00:00Z', page, pageSize: 30, totalLessons: size * journalPages, hasMore: page + 1 < journalPages,
        ...journalPageMetadata.get(page),
      })
    }
    if (url.pathname === '/api/auth/export-jobs') {
      exports.push(JSON.parse(String(init?.body)))
      await exportHold
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
  const group = () => nodes(root).find(n => n.props['aria-haspopup'] === 'listbox' && n.options.some(o => text(o).trim() === 'Контрольная группа'))!
  const openDraft = async () => {
    const field = nodes(root).find(n => n.tag === 'input' && n.props.type === 'search')!
    field.input('37'); field.focus(); await nextTick()
    await new Promise(resolve => setTimeout(resolve, 270)); await settle()
    return field
  }
  return {
    root, group, openDraft, exports, journalQueries, statsQueries, webSession,
    changed: () => realtime.changed(),
    removeOtherGroup: () => { catalog = { ...initialCatalog, groups: initialCatalog.groups.slice(0, 1), subjectOptions: [subject] } },
    removeCurrentScope: (keepOther = false) => { catalog = { ...initialCatalog, groups: keepOther ? initialCatalog.groups.slice(1) : [], subjectOptions: keepOther ? initialCatalog.subjectOptions.slice(1) : [], taughtSubjectOptions: [] } },
    projection: (name: string) => { projection = name },
    denyCatalog: (status: number) => { catalogFailure = status },
    denyStats: (status: number) => { statsFailure = status },
    holdCatalog: () => { let release!: () => void; catalogHold = new Promise<void>(resolve => { release = resolve }); return () => { catalogHold = null; release() } },
    holdStats: () => { let release!: () => void; statsHold = new Promise<void>(resolve => { release = resolve }); return () => { statsHold = null; release() } },
    holdExport: () => { let release!: () => void; exportHold = new Promise<void>(resolve => { release = resolve }); return () => { exportHold = null; release() } },
    holdJournalPage: (page: number) => { let release!: () => void; journalHolds.set(page, new Promise<void>(resolve => { release = resolve })); return () => { journalHolds.delete(page); release() } },
    driftJournalPage: (page: number, metadata: { pageSize: number; totalLessons: number }) => {
      journalPageMetadata.set(page, metadata)
      return () => journalPageMetadata.delete(page)
    },
  }
}

describe('teacher live reads while a mounted filter is focused', () => {
  it('removes the deleted target option and refreshes the authorized control without replacing its draft, focus or unknown export', async () => {
    const f = await mounted()
    const field = await f.openDraft(), group = f.group()
    click(f.root, 'Экспортировать'); await settle()
    const originalExport = f.exports[0]!
    const releaseCatalog = f.holdCatalog(), releaseStats = f.holdStats()
    f.removeOtherGroup(); f.projection('Обновлённая студентка контроля'); f.changed(); await settle()
    expect(group.props.disabled).toBe(false)
    expect(document.activeElement).toBe(field)
    releaseCatalog(); await settle()
    expect(group.options.map(n => text(n).trim())).not.toContain('Удаляемая группа')
    expect(text(group.options[group.selectedIndex]!).trim()).toBe('Контрольная группа')
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

  it('retires the cached roster and group options when the catalog proves its scope is gone', async () => {
    const f = await mounted(), field = await f.openDraft()
    f.removeCurrentScope(); f.changed(); await settle()
    expect(text(f.root)).not.toContain('Прежняя студентка контроля')
    expect(text(f.root)).not.toContain('Удаляемая группа')
    expect(field.isConnected).toBe(true)
    expect(f.group()).toBeUndefined()
  })

  it.each(['catalog', 'stats'] as const)('withdraws the private projection after a background %s denial despite focus', async target => {
    const f = await mounted(), field = await f.openDraft()
    if (target === 'catalog') f.denyCatalog(403)
    else f.denyStats(403)
    f.changed(); await settle()
    expect(text(f.root)).not.toContain('Прежняя студентка контроля')
    expect(field.isConnected).toBe(target === 'stats')
  })

  it('clears a lost current scope before waiting for the alternative authorized roster', async () => {
    const f = await mounted(), field = await f.openDraft(), release = f.holdStats()
    f.removeCurrentScope(true); f.changed(); await settle()
    expect(text(f.root)).not.toContain('Прежняя студентка контроля')
    expect(field.isConnected).toBe(true)
    expect(f.group()).toBeUndefined()
    f.projection('Студентка другой доступной группы'); release(); await settle()
    expect(text(f.root)).toContain('Студентка другой доступной группы')
  })

  it('preserves a focused filter and its original unknown export through a transient read failure', async () => {
    const f = await mounted()
    const field = await f.openDraft()
    const releaseExport = f.holdExport()
    click(f.root, 'Экспортировать'); await settle()
    const original = f.exports[0]!
    f.denyStats(503); f.changed(); await settle()
    expect(text(f.root)).toContain('Прежняя студентка контроля')
    expect(field.isConnected).toBe(true)
    expect(field.value).toBe('37')
    expect(document.activeElement).toBe(field)
    expect(f.exports).toEqual([original])
    releaseExport(); await settle()
    click(f.root, 'Проверить выгрузку'); await settle()
    expect(f.exports[1]).toEqual(original)
    f.denyStats(0); f.projection('Восстановленная студентка контроля'); f.changed(); await settle()
    expect(text(f.root)).toContain('Восстановленная студентка контроля')
    expect(document.activeElement).toBe(field)
  })

  it('refreshes the applied journal query while an unapplied date draft stays dirty and focused', async () => {
    const f = await mounted('journal')
    const field = nodes(f.root).find(n => n.tag === 'input' && n.props.placeholder === 'ДД.ММ.ГГГГ')!
    field.input('01.10.2026'); field.focus(); await nextTick()
    f.removeOtherGroup(); f.projection('Обновлённая студентка контроля'); f.changed(); await settle()
    expect(f.group().options.map(text)).not.toContain('Удаляемая группа')
    expect(text(f.root)).toContain('Обновлённая студентка контроля')
    expect(field.isConnected).toBe(true)
    expect(field.value).toBe('01.10.2026')
    expect(document.activeElement).toBe(field)
    expect(f.journalQueries.at(-1)?.get('groupId')).toBe('10')
    expect(f.journalQueries.at(-1)?.has('dateFrom')).toBe(false)
    expect(f.journalQueries.at(-1)?.has('dateTo')).toBe(false)
  })

  it('keeps the applied period in the journal and its export when a date draft is invalid', async () => {
    const f = await mounted('journal')
    const [from, to] = nodes(f.root).filter(n => n.tag === 'input' && n.props.placeholder === 'ДД.ММ.ГГГГ')
    const form = nodes(f.root).find(n => n.tag === 'form')!
    const apply = () => (form.props.onSubmit as (event: { preventDefault(): void }) => void)({ preventDefault() {} })
    from!.input('01.10.2026'); to!.input('20.10.2026'); await nextTick()
    apply(); await settle()
    expect(f.journalQueries.at(-1)?.get('dateFrom')).toBe('2026-10-01')
    expect(f.journalQueries.at(-1)?.get('dateTo')).toBe('2026-10-20')
    const count = f.journalQueries.length
    from!.input('ошибка'); await nextTick()
    apply(); await settle()
    expect(f.journalQueries).toHaveLength(count)
    expect(text(f.root)).toContain('Прежняя студентка контроля')
    expect(from!.value).toBe('ошибка')
    click(f.root, 'Лекция'); await settle()
    click(f.root, 'Лекция'); await settle()
    expect(f.journalQueries.at(-1)?.get('dateFrom')).toBe('2026-10-01')
    click(f.root, 'Экспортировать'); await settle()
    expect(f.exports[0]?.report).toMatchObject({
      teacherJournal: { dateFrom: '2026-10-01', dateTo: '2026-10-20' },
    })
  })

  it('keeps student and group searches and sort separate from the group drill and reset', async () => {
    const f = await mounted()
    await f.openDraft()
    click(f.root, 'Отсутствия: сортировка не задана'); await settle()
    click(f.root, 'По моим группам'); await settle()
    expect(f.statsQueries.at(-1)?.getAll('filter')).toEqual([])
    const field = () => nodes(f.root).find(n => n.tag === 'input' && n.props.type === 'search')!
    const search = async (value: string) => {
      field().input(value)
      await new Promise(resolve => setTimeout(resolve, 270)); await settle()
    }
    await search('несуществующий')
    expect(text(f.root)).toContain('По заданным условиям ничего не найдено.')
    click(f.root, 'Сбросить фильтры и сортировку'); await settle()
    expect(f.statsQueries.at(-1)?.getAll('filter')).toEqual([])
    await search('Удаляемая')
    click(f.root, 'Открыть студентов группы Удаляемая группа'); await settle()
    expect(f.statsQueries.at(-1)?.get('groupId')).toBe('20')
    expect(f.statsQueries.at(-1)?.getAll('filter')).toEqual([])
    click(f.root, 'Вернуться к моим группам'); await settle()
    expect(f.statsQueries.at(-1)?.getAll('filter')).toEqual(['groupName~Удаляемая'])
    click(f.root, 'По студентам группы'); await settle()
    expect(f.statsQueries.at(-1)?.get('groupId')).toBe('10')
    expect(f.statsQueries.at(-1)?.getAll('filter')).toEqual(['displayName~37'])
    expect(f.statsQueries.at(-1)?.getAll('sort')).toEqual(['absent'])
  })

  it('moves focus during a slow group drill and leaves it with the user after returning', async () => {
    const f = await mounted()
    click(f.root, 'По моим группам'); await settle()
    const finishDrill = f.holdStats()
    click(f.root, 'Открыть студентов группы Удаляемая группа'); await settle()
    expect(document.activeElement?.tag).toBe('h2')
    expect(text(document.activeElement!)).toContain('Студенты группы')
    finishDrill(); await settle()
    const finishReturn = f.holdStats()
    click(f.root, 'Вернуться к моим группам'); await settle()
    const groupTab = document.activeElement!
    expect(text(groupTab).trim()).toBe('По моим группам')
    nodes(f.root).find(n => n.tag === 'input' && n.props.type === 'search')!.focus()
    groupTab.focus()
    finishReturn(); await settle()
    expect(document.activeElement).toBe(groupTab)
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

  it('refreshes the loaded journal window without removing a focused second-page lesson', async () => {
    const f = await mounted('journal', 3)
    click(f.root, 'Загрузить ещё пары'); await settle()
    const lessonLinks = () => nodes(f.root).filter(n => n.tag === 'a' && /^\/teacher\/lessons\/\d+$/.test(String(n.props.href)))
    const link = lessonLinks().find(n => n.props.href === '/teacher/lessons/330')!
    expect(lessonLinks()).toHaveLength(60)
    link.focus(); await nextTick()
    const release = f.holdJournalPage(1)
    f.removeOtherGroup(); f.projection('Обновлённая студентка контроля'); f.changed(); await settle()
    expect(f.group().options.map(text)).not.toContain('Удаляемая группа')
    expect(link.isConnected).toBe(true)
    expect(document.activeElement).toBe(link)
    expect(lessonLinks()).toHaveLength(60)
    release(); await settle()
    expect(text(f.root)).toContain('Обновлённая студентка контроля')
    expect(lessonLinks()).toHaveLength(60)
    expect(lessonLinks().some(node => node === link)).toBe(true)
    expect(document.activeElement).toBe(link)
    click(f.root, 'Загрузить ещё пары'); await settle()
    expect(lessonLinks()).toHaveLength(90)
  })

  it('does not merge a drifting next page or start a fresh export before the journal reloads', async () => {
    const f = await mounted('journal', 3)
    const clearDrift = f.driftJournalPage(1, { pageSize: 30, totalLessons: 91 })
    click(f.root, 'Загрузить ещё пары'); await settle()
    expect(text(f.root)).toContain('Список пар изменился.')
    expect(nodes(f.root).filter(n => n.tag === 'a' && /^\/teacher\/lessons\/\d+$/.test(String(n.props.href)))).toHaveLength(30)
    const exportButton = () => nodes(f.root).find(n => n.tag === 'button' && text(n).trim() === 'Экспортировать')!
    expect(exportButton().props.disabled).toBe(true)
    const releaseCatalog = f.holdCatalog()
    click(f.root, 'Повторить'); await settle()
    expect(exportButton().props.disabled).toBe(true)
    clearDrift(); releaseCatalog(); await settle()
    expect(exportButton().props.disabled).toBe(false)
  })
})
