import { createRenderer, h, markRaw, nextTick, shallowReactive, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TeacherFeatureOwner from './TeacherFeatureOwner.vue'
import { TeacherApi } from '../../features/teacher/teacher-client'

// The day feature is independent of this owner catalogue recovery scenario.
vi.mock('../../features/teacher/TeacherHomeScreen.vue', () => ({ default: { render: () => null } }))

class HostNode {
  children: HostNode[] = []; parent: HostNode | null = null; props: Record<string, unknown> = {}; text = ''
  value: unknown = ''
  constructor(readonly tag: string) { markRaw(this) }
  querySelectorAll(): HostNode[] { return [] }
  focus(): void {}
  addEventListener(): void {}
}
const renderer = createRenderer<HostNode, HostNode>({
  createElement: tag => new HostNode(tag), createText: text => Object.assign(new HostNode('#text'), { text }), createComment: () => new HostNode('#comment'),
  setText: (node, text) => { node.text = text }, setElementText: (node, text) => { node.text = text; node.children = [] },
  parentNode: node => node.parent, nextSibling: node => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _old, value) => { node.props[key] = value },
  insert: (node, parent, anchor = null) => { if (node.parent) node.parent.children.splice(node.parent.children.indexOf(node), 1); const index = anchor ? parent.children.indexOf(anchor) : -1; parent.children.splice(index < 0 ? parent.children.length : index, 0, node); node.parent = parent },
  remove: node => { node.parent?.children.splice(node.parent.children.indexOf(node), 1); node.parent = null },
})
function text(root: HostNode): string { return root.text + root.children.map(text).join(' ') }
function find(root: HostNode, predicate: (node: HostNode) => boolean): HostNode | undefined {
  if (predicate(root)) return root
  for (const child of root.children) { const match = find(child, predicate); if (match) return match }
  return undefined
}
function click(root: HostNode, label: string): void {
  const button = find(root, node => node.tag === 'button' && (node.props['aria-label'] === label || text(node).trim() === label))
  expect(button).toBeDefined()
  ;(button!.props.onClick as () => void)()
}
function openGroup(root: HostNode, name: string): void {
  const button = find(root, node => node.tag === 'button' && text(node).includes(name) && node.children.some(child => String(child.props.class).includes('teacher-stats__row-name')))
  expect(button).toBeDefined()
  ;(button!.props.onClick as () => void)()
}
async function settle(): Promise<void> { for (let index = 0; index < 30; index++) { await Promise.resolve(); await nextTick() } }
async function chooseStatsContext(root: HostNode, groupName: string, subjectName: string): Promise<void> {
  click(root, 'Группа'); await settle(); click(root, groupName); await settle()
  click(root, 'Предмет'); await settle(); click(root, subjectName); await settle()
  click(root, 'Лекция'); await settle()
}
const apps: App[] = []
afterEach(() => { for (const app of apps.splice(0)) app.unmount(); vi.unstubAllGlobals() })
const json = (value: unknown): Response => Response.json(value)
const unavailable = (): Response => Response.json({ detail: 'Каталог временно недоступен' }, { status: 503 })
function fixture(options: { semesterId?: number; groupName?: string; subjectName?: string; catalogue?: (attempt: number, success: () => Response) => Promise<Response> | Response } = {}) {
  const requests: string[] = []
  let catalogues = 0
  const semesterId = options.semesterId ?? 7
  const groupName = options.groupName ?? 'ИВТ-241'
  const subjectName = options.subjectName ?? 'Программирование'
  const option = { groupId: '101', subjectId: '201', subjectName, lessonTypes: ['lecture'] }
  const catalog = () => json({ groups: [{ id: '101', name: groupName }], subjectOptions: [option], taughtSubjectOptions: [option], serverNow: '2026-10-09T08:00:00Z' })
  const api = markRaw(new TeacherApi({ accessToken: () => null, fetcher: async input => {
    const path = new URL(String(input), 'https://fixture.test').pathname
    requests.push(path)
    if (path.endsWith('/semester')) return json({ id: String(semesterId), name: 'Осень', dateFrom: '2026-09-01', dateTo: '2027-01-31' })
    if (path.endsWith('/lesson-slots')) return json({ timezone: 'Europe/Moscow', slots: Array.from({ length: 8 }, (_, index) => ({ lessonNumber: index + 1, startTime: `${index + 9}:00:00`.padStart(8, '0'), endTime: `${index + 9}:50:00`.padStart(8, '0') })) })
    if (path.endsWith('/catalog')) { catalogues += 1; return options.catalogue ? options.catalogue(catalogues, catalog) : catalog() }
    if (path.endsWith('/stats')) {
      const metric = { numerator: 0, denominator: 1, percent: 0 }
      return json({ scope: 'groups', semesterId: String(semesterId), periodFrom: '2026-09-01', periodTo: '2026-10-09', lessonsCount: 1, students: [], groups: [{ groupId: '101', groupName, lessonsCount: 1, present: metric, presentOrExcused: metric, excused: metric, absent: { numerator: 1, denominator: 1, percent: 100 } }], subjectOptions: [option], serverNow: '2026-10-09T08:00:00Z' })
    }
    throw new Error(`Unexpected context scenario path ${path}`)
  } }))
  return { api, requests, groupName, subjectName, catalog, catalogues: () => catalogues }
}
function mount(api: TeacherApi) {
  const location = { href: 'https://fixture.test/app', search: '' }
  vi.stubGlobal('window', { location, history: { state: null, replaceState(_state: unknown, _title: string, value: string) { const next = new URL(value, location.href); location.href = next.href; location.search = next.search } } })
  vi.stubGlobal('document', { activeElement: null, addEventListener() {}, removeEventListener() {} })
  const state = shallowReactive({ api, semesterId: 7 })
  const root = new HostNode('root')
  const app = renderer.createApp({ render: () => h(TeacherFeatureOwner, { api: state.api, semesterId: state.semesterId }) })
  apps.push(app); app.mount(root)
  return { root, state }
}

describe('teacher owner catalogue recovery', () => {
  it('recovers a transient failure through real group drilldown and deduplicates retry while preserving slots and semester', async () => {
    let complete!: () => void
    const transport = fixture({ catalogue: (attempt, success) => attempt === 1 || attempt === 3 ? unavailable() : attempt === 4 ? new Promise(resolve => { complete = () => resolve(success()) }) : success() })
    const view = mount(transport.api); await settle()
    click(view.root, 'Итоги'); await settle()
    expect(transport.requests.filter(path => path.endsWith('/stats'))).toHaveLength(0)
    await chooseStatsContext(view.root, transport.groupName, transport.subjectName)
    openGroup(view.root, transport.groupName); await settle()
    expect(text(view.root)).toContain('Предметы недоступны')
    click(view.root, 'Повторить'); click(view.root, 'Повторить'); await settle()
    expect(transport.catalogues()).toBe(4)
    complete(); await settle()
    expect(text(view.root)).toContain(transport.subjectName)
    expect(text(view.root)).not.toContain('Предметы недоступны')
    expect(transport.requests.filter(path => path.endsWith('/semester'))).toHaveLength(1)
    expect(transport.requests.filter(path => path.endsWith('/lesson-slots'))).toHaveLength(1)
    click(view.root, 'Назад'); await settle(); openGroup(view.root, transport.groupName); await settle()
    expect(transport.catalogues()).toBe(4)
    expect(text(view.root)).toContain(transport.subjectName)
  })

  it('ignores an obsolete retry after the authenticated API and semester context change', async () => {
    let complete!: () => void
    const obsolete = fixture({ catalogue: (attempt, success) => attempt === 1 || attempt === 3 ? unavailable() : attempt === 4 ? new Promise(resolve => { complete = () => resolve(success()) }) : success() })
    const view = mount(obsolete.api); await settle(); click(view.root, 'Итоги'); await settle()
    await chooseStatsContext(view.root, obsolete.groupName, obsolete.subjectName)
    openGroup(view.root, obsolete.groupName); await settle()
    click(view.root, 'Повторить'); await settle()
    const current = fixture({ semesterId: 8, groupName: 'Новая группа', subjectName: 'Новые сети' })
    view.state.api = current.api; view.state.semesterId = 8; await settle()
    complete(); await settle()
    expect(current.requests.filter(path => path.endsWith('/stats'))).toHaveLength(0)
    await chooseStatsContext(view.root, current.groupName, current.subjectName)
    openGroup(view.root, current.groupName); await settle()
    expect(text(view.root)).toContain(current.subjectName)
    expect(text(view.root)).not.toContain(obsolete.subjectName)
    expect(text(view.root)).not.toContain('Предметы недоступны')
  })
})
