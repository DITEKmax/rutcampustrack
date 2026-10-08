import { createRenderer, h, markRaw, nextTick, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TeacherStatsScreen from './TeacherStatsScreen.vue'
import { TeacherApi, createGenerationBoundTeacherApi } from './teacher-client'
import { StaleSessionGenerationError } from '../../shared/session-owner'

class Node {
  children: Node[] = []; parent: Node | null = null; props: Record<string, unknown> = {}; text = ''
  value: unknown = ''; selected = false; checked = false
  constructor(readonly tag: string) { markRaw(this) }
  querySelectorAll(selector: string): Node[] { const result: Node[] = []; const walk = (node: Node) => { if (selector === '[role="option"]' && node.props.role === 'option') result.push(node); node.children.forEach(walk) }; this.children.forEach(walk); return result }
  focus(): void {}
  close(): void {}
  showModal(): void {}
  addEventListener(): void {}
  getRootNode(): { activeElement: null } { return { activeElement: null } }
  get tagName(): string { return this.tag.toUpperCase() }
  get options(): Node[] { return this.children.filter((node) => node.tag === 'option') }
}
const renderer = createRenderer<Node, Node>({
  createElement: (tag) => new Node(tag), createText: (text) => Object.assign(new Node('#text'), { text }), createComment: () => new Node('#comment'),
  setText: (node, text) => { node.text = text }, setElementText: (node, text) => { node.text = text; node.children = [] },
  parentNode: (node) => node.parent, nextSibling: (node) => node.parent?.children[node.parent.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _before, value) => { node.props[key] = value },
  insert: (node, parent, anchor = null) => { const old = node.parent; if (old) old.children.splice(old.children.indexOf(node), 1); const index = anchor ? parent.children.indexOf(anchor) : -1; parent.children.splice(index < 0 ? parent.children.length : index, 0, node); node.parent = parent },
  remove: (node) => { node.parent?.children.splice(node.parent.children.indexOf(node), 1); node.parent = null },
})
function find(root: Node, predicate: (node: Node) => boolean): Node | undefined { if (predicate(root)) return root; for (const child of root.children) { const found = find(child, predicate); if (found) return found }; return undefined }
function text(root: Node): string { return root.text + root.children.map(text).join(' ') }
function click(root: Node, label: string): void { const node = find(root, item => item.tag === 'button' && (item.props['aria-label'] === label || text(item).trim() === label)); expect(node).toBeDefined(); (node!.props.onClick as () => void)() }
const apps: App[] = []
afterEach(() => { for (const app of apps.splice(0)) app.unmount(); vi.unstubAllGlobals() })

const subjectOptions = [{ groupId: '2', subjectId: '3', subjectName: 'Программирование', lessonTypes: ['lecture', 'PrAcTiCe', 'lab'] }]
const catalog = { groups: [{ id: '2', name: 'ИКБО-01-24' }], subjectOptions, serverNow: '2026-10-08T09:00:00Z' }
const metric = { numerator: 1, denominator: 2, percent: 50 }
const json = (value: unknown) => new Response(JSON.stringify(value), { headers: { 'Content-Type': 'application/json' } })
async function settle(): Promise<void> { for (let i = 0; i < 20; i++) { await Promise.resolve(); await nextTick() } }
function harness(options: { delayedCatalog?: boolean; emptyCatalog?: boolean } = {}) {
  const location = { href: 'https://fixture.test/app', search: '' }
  vi.stubGlobal('window', { location, history: { state: null, replaceState(_state: unknown, _title: string, value: string) { const next = new URL(value, location.href); location.href = next.href; location.search = next.search } } })
  vi.stubGlobal('document', { activeElement: null, addEventListener() {}, removeEventListener() {} })
  let delayCatalog = options.delayedCatalog ?? false
  let failStats = false
  let releaseCatalog: (() => void) | null = null
  let hold = false
  let release: (() => void) | null = null
  const queries: URLSearchParams[] = []
  const api = new TeacherApi({ accessToken: () => null, fetcher: async input => {
    const url = new URL(String(input), 'http://fixture.test')
    if (url.pathname.endsWith('/catalog')) {
      const reply = json(options.emptyCatalog ? { ...catalog, groups: [], subjectOptions: [] } : catalog)
      if (delayCatalog) { delayCatalog = false; return new Promise<Response>(resolve => { releaseCatalog = () => resolve(reply) }) }
      return reply
    }
    if (url.pathname.endsWith('/formats')) return json({ formats: [
      ['docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'docx'],
      ['pdf', 'application/pdf', 'pdf'], ['png', 'application/zip', 'zip'], ['html', 'text/html', 'html'],
      ['xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'xlsx'],
    ].map(([code, contentType, extension]) => ({ code, label: code, contentType, extension })) })
    if (url.pathname.endsWith('/stats')) {
      queries.push(url.searchParams)
      if (failStats) { failStats = false; return new Response(JSON.stringify({ code: 'DEPENDENCY_UNAVAILABLE' }), { status: 503 }) }
      const students = url.searchParams.get('scope') === 'students'
      const row = { present: metric, presentOrExcused: metric, excused: metric, absent: metric }
      const reply = json({ semesterId: '7', scope: students ? 'students' : 'groups', lessonsCount: 2, periodFrom: null, periodTo: null, subjectOptions,
        students: students ? [{ ...row, studentId: '4', displayName: 'Анна Белова' }] : [],
        groups: students ? [] : [{ ...row, groupId: '2', groupName: hold ? 'Устаревшая группа' : 'ИКБО-01-24', lessonsCount: 2 }], serverNow: catalog.serverNow })
      if (hold) { hold = false; return new Promise<Response>(resolve => { release = () => resolve(reply) }) }
      return reply
    }
    throw new Error(`Unexpected fixture path ${url.pathname}`)
  } })
  const root = new Node('root')
  const app = renderer.createApp({ render: () => h(TeacherStatsScreen, { api: markRaw(api), semesterId: 7 }) })
  apps.push(app); app.mount(root)
  return { root, queries, delayCatalog: () => { delayCatalog = true }, failNextStats: () => { failStats = true }, releaseCatalog: () => releaseCatalog?.(), hold: () => { hold = true }, release: () => release?.() }
}
describe('teacher statistics selectors and races', () => {
  it('defers lens and sort controls until delayed catalog arrives without discarding its context', async () => {
    const fixture = harness({ delayedCatalog: true }); await settle()
    expect(find(fixture.root, item => item.tag === 'button' && text(item).trim() === 'По студентам')?.props.disabled).toBe(true)
    expect(find(fixture.root, item => item.props['aria-label'] === 'Сортировка')?.props.disabled).toBe(true)
    // Custom host events deliberately bypass native disabled handling; the
    // domain handlers must still preserve the pending catalog generation.
    click(fixture.root, 'По студентам'); click(fixture.root, 'Добавить условие'); await settle()
    expect(fixture.queries).toHaveLength(0)
    fixture.releaseCatalog(); await settle()
    expect(text(fixture.root)).toContain('ИКБО-01-24')
    click(fixture.root, 'По студентам'); await settle()
    expect(fixture.queries.at(-1)?.get('groupId')).toBe('2')
    expect(fixture.queries.at(-1)?.get('subjectId')).toBe('3')
    expect(text(fixture.root)).toContain('Анна Белова')
  })
  it('labels lowercase and mixed catalog types distinctly while preserving their wire values', async () => {
    const fixture = harness(); await settle()
    click(fixture.root, 'Фильтры'); await settle()
    expect(text(fixture.root)).toContain('Лекция')
    expect(text(fixture.root)).toContain('Практика')
    expect(text(fixture.root)).toContain('Лабораторная')
    click(fixture.root, 'Лекция'); await settle()
    expect(fixture.queries.at(-1)?.getAll('lessonType')).toEqual(['lecture'])
  })
  it('preserves catalog retry with an open filter while all/apply/reset events are attempted', async () => {
    const fixture = harness(); await settle()
    click(fixture.root, 'Фильтры'); await settle()
    fixture.failNextStats(); click(fixture.root, 'Лекция'); await settle()
    expect(text(fixture.root)).toContain('Статистика недоступна')
    fixture.delayCatalog(); click(fixture.root, 'Повторить'); await settle()
    const before = fixture.queries.length
    click(fixture.root, 'Все')
    const form = find(fixture.root, item => item.tag === 'form')!
    ;(form.props.onSubmit as (event: { preventDefault(): void }) => void)({ preventDefault() {} })
    click(fixture.root, 'Сбросить фильтры и сортировку'); await settle()
    expect(fixture.queries).toHaveLength(before)
    expect(find(fixture.root, item => item.props['aria-label'] === 'Скачать статистику')?.props.disabled).toBe(true)
    fixture.releaseCatalog(); await settle()
    expect(text(fixture.root)).toContain('ИКБО-01-24')
    expect(text(fixture.root)).not.toContain('Статистика недоступна')
    expect(fixture.queries.at(-1)?.getAll('lessonType')).toEqual(['lecture'])
    expect(find(fixture.root, item => item.tag === 'button' && text(item).trim() === 'По студентам')?.props.disabled).toBe(false)
  })
  it('shows a successful empty catalog without requesting an unauthorized groups aggregate', async () => {
    const fixture = harness({ emptyCatalog: true }); await settle()
    expect(fixture.queries).toHaveLength(0)
    expect(text(fixture.root)).toContain('Нет доступных групп.')
    expect(text(fixture.root)).not.toContain('Статистика недоступна')
    expect(find(fixture.root, item => item.props['aria-label'] === 'Скачать статистику')?.props.disabled).toBe(true)
  })

  it('releases catalog loading before the first aggregate so a newer lens and sort can finish independently', async () => {
    const fixture = harness(); fixture.hold(); await settle()
    expect(fixture.queries).toHaveLength(1)
    click(fixture.root, 'По студентам'); await settle()
    expect(fixture.queries.at(-1)?.get('scope')).toBe('students')
    expect(text(fixture.root)).toContain('Анна Белова')
    expect(find(fixture.root, item => item.props['aria-label'] === 'Скачать статистику')?.props.disabled).toBe(false)
    click(fixture.root, 'Сортировка'); await settle()
    click(fixture.root, 'Отсутствия: сначала выше'); await settle()
    expect(fixture.queries.at(-1)?.getAll('sort')).toEqual(['-absent'])
    expect(text(fixture.root)).toContain('Анна Белова')
    expect(find(fixture.root, item => item.props['aria-label'] === 'Скачать статистику')?.props.disabled).toBe(false)
    fixture.release(); await settle()
    expect(text(fixture.root)).toContain('Анна Белова')
    expect(text(fixture.root)).not.toContain('Устаревшая группа')
  })
  it('keeps independent lens ordering and ignores the older group response after switching to students', async () => {
    const fixture = harness(); await settle()
    fixture.hold()
    click(fixture.root, 'Сортировка'); await settle()
    click(fixture.root, 'Отсутствия: сначала выше'); await settle()
    click(fixture.root, 'По студентам'); await settle()
    expect(fixture.queries.at(-1)?.get('scope')).toBe('students')
    expect(fixture.queries.at(-1)?.getAll('sort')).toEqual(['present'])
    fixture.release(); await settle()
    expect(text(fixture.root)).toContain('Анна Белова')
    expect(text(fixture.root)).not.toContain('Устаревшая группа')
    click(fixture.root, 'По моим группам'); await settle()
    expect(fixture.queries.at(-1)?.getAll('sort')).toEqual(['-absent'])
    click(fixture.root, 'Добавить условие'); await settle()
    expect(fixture.queries.at(-1)?.getAll('sort')).toEqual(['-absent', 'groupName'])
    click(fixture.root, 'Удалить условие 2'); await settle()
    expect(fixture.queries.at(-1)?.getAll('sort')).toEqual(['-absent'])
  })
  it('rejects late catalog data after session generation changes and malformed catalog context', async () => {
    let generation = 1
    let release!: (reply: Response) => void
    const api = createGenerationBoundTeacherApi({ currentGeneration: () => generation, accessTokenFor: () => null, refreshFor: async () => {} },
      async () => new Promise<Response>(resolve => { release = resolve }))
    const pending = api.catalog(7)
    generation = 2; release(json(catalog))
    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
    const invalidApi = new TeacherApi({ accessToken: () => null, fetcher: async () => json({ ...catalog, groups: [] }) })
    await expect(invalidApi.catalog(7)).rejects.toThrow('несогласованный каталог')
    const malformedApi = new TeacherApi({ accessToken: () => null, fetcher: async () => json({ ...catalog, subjectOptions: [{ ...subjectOptions[0], lessonTypes: null }] }) })
    await expect(malformedApi.catalog(7)).rejects.toThrow('некорректные типы занятий')
    const emptyApi = new TeacherApi({ accessToken: () => null, fetcher: async () => json({ groups: [], subjectOptions: [], serverNow: catalog.serverNow }) })
    await expect(emptyApi.catalog(7)).resolves.toMatchObject({ groups: [], subjectOptions: [] })
  })
})
