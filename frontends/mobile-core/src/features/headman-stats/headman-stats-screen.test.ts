import { createRenderer, h, markRaw, nextTick, shallowReactive, type App, type SetupContext } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import HeadmanStatsScreen from './HeadmanStatsScreen.vue'
import { HeadmanStatsApi, type HeadmanStatsBlock, type HeadmanStatsQuery, type HeadmanStatsTrendQuery } from './headman-stats-client'
import { headmanStatsFixtureWeekPolicy, headmanStatsFixtureDetail, headmanStatsFixtureResponse, headmanStatsFixtureTrend, headmanStatsFixtureLessons } from '../../dev/headman-stats-fixtures'
import { HeadmanRequestsApi } from '../headman-requests/headman-requests-client'
import type { StatisticsGraphRange } from '../statistics/statistics-view-model'

interface HeadmanStatsScreenTestVm {
  readonly ready: boolean
  openBlock(block: HeadmanStatsBlock): void
  selectSubject(value: string): void
  openTool(tool: 'filters' | 'sort' | 'export' | 'trend' | 'tickets'): void
  openStudentDetail(studentId: number, event: MouseEvent): void
  back(): boolean
  openHistory(): void
  setChartRange(range: StatisticsGraphRange): void
  setMobileTypes(types: readonly string[]): void
}

vi.mock('./HeadmanStatsHistory.vue', () => ({ default: {
  emits: ['refreshed'], setup(_props: unknown, { emit, expose }: SetupContext) { expose({ ready: true, back: () => false }); return () => h('button', { class: 'test-history-ack', onClick: () => emit('refreshed') }, 'Сервер подтвердил запись') },
} }))
vi.mock('./HeadmanStatsDetailPanel.vue', () => ({ default: { render: () => null } }))
vi.mock('./HeadmanStatsTrendChart.vue', () => ({ default: { render: () => null } }))

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
function click(root: Node, className: string): void { const node = find(root, (item) => item.props.class === className); expect(node).toBeDefined(); (node!.props.onClick as () => void)() }
const apps: App[] = []
afterEach(() => { for (const app of apps.splice(0)) app.unmount(); vi.unstubAllGlobals() })
function harness(legacyPersonal = false, semesterSpecificCatalog = false) {
  vi.stubGlobal('HTMLButtonElement', class {})
  vi.stubGlobal('HTMLElement', class {})
  vi.stubGlobal('document', { activeElement: null, addEventListener() {}, removeEventListener() {} })
  const statsQueries: HeadmanStatsQuery[] = []; const trendQueries: HeadmanStatsTrendQuery[] = []
  let personalCalls = 0; let overviewCalls = 0; let todayPresent = 21; let failPersonal = true
  let holdNextPolicy = false; let releasePolicy: (()=>void) | null = null
  let heldOverview: ((reply: Response) => void) | null = null; let holdOverview = false
  const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } })
  const fetcher: typeof fetch = async (input, init) => {
    const url = new URL(String(input), 'http://fixture.test')
    if (url.pathname.endsWith('/academic-week-policy')) { const policy = headmanStatsFixtureWeekPolicy(url.searchParams.get('date')!); if (holdNextPolicy) { holdNextPolicy=false; return new Promise<Response>(resolve => {releasePolicy=()=>resolve(json(policy))}) }; return json(policy) }
    if (url.pathname.endsWith('/semesters')) return json({ groupId: 2, activeSemesterId: 7, semesters: [{ id: 7, name: 'Осенний семестр', dateFrom: '2026-09-01', dateTo: '2027-01-31', active: true, archived: false }, ...(semesterSpecificCatalog ? [{ id: 8, name: 'Весенний семестр 2027', dateFrom: '2027-02-01', dateTo: '2027-06-30', active: false, archived: false }] : [])] })
    if (url.pathname.endsWith('/overview')) { overviewCalls += 1; if (holdOverview) return new Promise<Response>((resolve) => { heldOverview = resolve }); return overviewReply() }
    if (url.pathname.endsWith('/query')) {
      const query = JSON.parse(String(init?.body)) as HeadmanStatsQuery; statsQueries.push(query)
      const result = headmanStatsFixtureResponse('overview-autumn', query)
      if (semesterSpecificCatalog) {
        const spring = query.semesterId === 8
        const subject = { id: spring ? 202 : 101, label: spring ? 'Весенняя дисциплина' : 'Осенняя дисциплина', lessonTypes: [{code:'LECTURE',label:'Лекция'}] }
        if (query.subjectId !== null && query.subjectId !== subject.id) return json({detail:'Предмет не входит в выбранный семестр'},400)
        return json({...result,subjects:[subject]})
      }
      return json(query.subjectId === null ? result : { ...result, rows: result.rows.map((row) => ({ ...row, lateCheckin: { submitted: 0, approved: 0, rejected: 0 }, excuse: { submitted: 0, approved: 0, rejected: 0 } })) })
    }
    if (url.pathname.endsWith('/detail')) { const result = headmanStatsFixtureDetail('overview-autumn', 11, 7); return json(legacyPersonal ? { ...result, sources: undefined, lateCheckin: undefined, excuse: undefined } : result) }
    if (url.pathname.endsWith('/trend')) {
      trendQueries.push(JSON.parse(String(init?.body)) as HeadmanStatsTrendQuery)
      if (url.pathname.includes('/students/')) { personalCalls += 1; if (failPersonal) return json({ detail: 'Личный график временно недоступен' }, 503) }
      return json(headmanStatsFixtureTrend('overview-autumn', JSON.parse(String(init?.body)) as HeadmanStatsTrendQuery, url.pathname.includes('/students/')))
    }
    return json({}, 404)
  }
  function overviewReply() { return json({ groupId: 2, membership: { active: 27, total: 30 }, today: { date: '2026-10-06', present: { numerator: todayPresent, denominator: 27, percent: todayPresent / 27 * 100 }, presentOrExcused: { numerator: 23, denominator: 27, percent: 23 / 27 * 100 } } }) }
  const props = shallowReactive({ api: new HeadmanStatsApi({ accessToken: () => 'synthetic-token', fetcher }), groupId: 2 })
  let screen: HeadmanStatsScreenTestVm | null = null
  const root = new Node('root'); const app = renderer.createApp({ render: () => h(HeadmanStatsScreen, { ...props, ref: (value) => { screen = value as unknown as HeadmanStatsScreenTestVm | null } }) }); apps.push(app); app.mount(root)
  return { holdNextPolicy:()=>{holdNextPolicy=true;releasePolicy=null}, policyHeld:()=>releasePolicy!==null, releasePolicy:()=>releasePolicy?.(), root, props, statsQueries, trendQueries, screen: (): HeadmanStatsScreenTestVm => screen!, personalCalls: () => personalCalls, overviewCalls: () => overviewCalls, recover: () => { failPersonal = false }, today: (value: number) => { todayPresent = value }, holdOverview: () => { holdOverview = true }, releaseOverview: () => { heldOverview?.(overviewReply()) } }
}
describe('headman statistics reviewed regressions', () => {
  it('history403 invalidates an in-flight approved-ticket intent and prevents late private detail publication', async () => {
    const { default: History } = await vi.importActual<typeof import('./HeadmanStatsHistory.vue')>('./HeadmanStatsHistory.vue')
    let denied = false; let releaseTicket: ((response: Response) => void) | null = null
    const lesson = headmanStatsFixtureLessons('history')[1]!
    const fetcher: typeof fetch = async (input) => {
      if (String(input).endsWith('/lessons?page=0&size=50&semesterId=7')) return new Response(JSON.stringify(denied ? { detail: 'Просмотр истории запрещён' } : { page: 0, size: 50, totalElements: 1, totalPages: 1, hasPrevious: false, hasNext: false, items: [lesson] }), { status: denied ? 403 : 200, headers: { 'Content-Type': 'application/json' } })
      return new Promise<Response>((resolve) => { releaseTicket = resolve })
    }
    let history: { controller: { load: () => Promise<boolean> } } | null = null
    const root = new Node('root'); const app = renderer.createApp({ render: () => h(History, { api: new HeadmanStatsApi({ accessToken: () => 'synthetic-token', fetcher }), journalApi: null, requestsApi: new HeadmanRequestsApi({ accessToken: () => 'synthetic-token', fetcher }), groupId: 2, studentId: 11, semesterId: 7, offline: false, allowed: true, markAllowed: true, excuseAllowed: true, ref: (value) => { history = value as unknown as typeof history } }) }); apps.push(app); app.mount(root)
    await vi.waitFor(() => expect(text(root)).toContain('Высшая математика'))
    click(root, 'headman-stats-history__ticket'); await vi.waitFor(() => expect(releaseTicket).not.toBeNull())
    denied = true; await history!.controller.load(); expect(text(root)).toContain('Просмотр истории запрещён')
    const release = releaseTicket as unknown as (response: Response) => void
    release(new Response(JSON.stringify({ summary: { id: lesson.approvedTicket!.id, kind: 'EXCUSE', status: 'APPROVED', studentId: 11, studentName: 'Александрова Мария', reason: 'Приватная причина', hasAttachments: false }, lessons: [], attachments: [] }), { headers: { 'Content-Type': 'application/json' } }))
    await new Promise((resolve) => setTimeout(resolve, 0)); await nextTick()
    expect(text(root)).not.toContain('Приватная причина'); expect(text(root)).not.toContain('Высшая математика'); expect(text(root)).not.toContain('Загружаем…')
  })
  it('changing semester in the subject catalog replaces metadata and permits a subject unique to that semester', async () => {
    const h = harness(false, true); await vi.waitFor(() => expect(h.screen().ready).toBe(true))
    h.screen().openBlock('subject'); await nextTick(); expect(text(h.root)).toContain('Осенняя дисциплина')
    const semesterButton = find(h.root, node => node.tag === 'button' && node.props['aria-label'] === 'Семестр')!
    ;(semesterButton.props.onClick as () => void)(); await nextTick()
    const springOption = find(h.root, node => node.props.role === 'option' && node.props['aria-label'] === 'Весенний семестр 2027')
    expect(springOption).toBeDefined()
    ;(springOption!.props.onClick as () => void)()
    await nextTick(); expect(text(h.root)).not.toContain('Осенняя дисциплина')
    await vi.waitFor(() => expect(h.screen().ready).toBe(true))
    expect(text(h.root)).toContain('Весенний семестр 2027'); expect(text(h.root)).toContain('1 февраля — 30 июня')
    expect(text(h.root)).toContain('Весенняя дисциплина'); expect(text(h.root)).not.toContain('Осенняя дисциплина')
    expect(h.statsQueries.at(-1)).toMatchObject({semesterId:8,subjectId:null,lessonTypes:[]})
    h.screen().selectSubject('202'); await vi.waitFor(() => expect(h.screen().ready).toBe(true))
    expect(h.statsQueries.at(-1)).toMatchObject({semesterId:8,subjectId:202,lessonTypes:['LECTURE']})
    expect(text(h.root)).toContain('Александрова Мария'); expect(text(h.root)).not.toContain('Предмет не входит')
  })
  it('a filter switch closes approved-ticket loading and rejects its delayed private response', async () => {
    const { default: History } = await vi.importActual<typeof import('./HeadmanStatsHistory.vue')>('./HeadmanStatsHistory.vue')
    const lessons = headmanStatsFixtureLessons('history'); const math = lessons[1]!; const systems = lessons[0]!
    const owner = shallowReactive({subjectId:13,lessonTypes:['PRACTICE']})
    let releaseTicket: ((reply: Response) => void) | null = null
    const fetcher: typeof fetch = async (input) => {
      const url = new URL(String(input),'http://fixture.test')
      if (url.pathname.endsWith('/lessons')) return new Response(JSON.stringify({page:0,size:50,totalElements:1,totalPages:1,hasPrevious:false,hasNext:false,items:[url.searchParams.get('subjectId') === '13' ? math : systems]}),{headers:{'Content-Type':'application/json'}})
      return new Promise<Response>(resolve => { releaseTicket = resolve })
    }
    const api = new HeadmanStatsApi({accessToken:()=>'synthetic-token',fetcher})
    const requestsApi = new HeadmanRequestsApi({accessToken:()=>'synthetic-token',fetcher})
    const root = new Node('root'); const app = renderer.createApp({render:()=>h(History,{api,journalApi:null,requestsApi,groupId:2,studentId:11,semesterId:7,offline:false,allowed:true,markAllowed:true,excuseAllowed:true,...owner})}); apps.push(app); app.mount(root)
    await vi.waitFor(()=>expect(text(root)).toContain('Высшая математика'))
    click(root,'headman-stats-history__ticket'); await vi.waitFor(()=>expect(releaseTicket).not.toBeNull())
    expect(text(root)).toContain('Загружаем…')
    owner.subjectId = 12; owner.lessonTypes = ['LECTURE']; await nextTick()
    await vi.waitFor(()=>expect(text(root)).toContain('Информационные системы'))
    expect(text(root)).not.toContain('Загружаем…'); expect(text(root)).not.toContain('Высшая математика')
    const release = releaseTicket as unknown as (reply: Response) => void
    release(new Response(JSON.stringify({summary:{id:math.approvedTicket!.id,kind:'EXCUSE',status:'APPROVED',studentId:11,studentName:'Александрова Мария',reason:'Приватная старая заявка',hasAttachments:false},lessons:[],attachments:[]}),{headers:{'Content-Type':'application/json'}}))
    await new Promise(resolve=>setTimeout(resolve,0)); await nextTick()
    expect(text(root)).not.toContain('Приватная старая заявка'); expect(text(root)).not.toContain('Заявка')
    expect(text(root)).toContain('Информационные системы')
  })
  it.each([false, true])('personal counters use whole-semester detail; legacy missing=%s never borrows the filtered row', async (legacy) => {
    const h = harness(legacy); h.recover(); await vi.waitFor(() => expect(h.screen().ready).toBe(true)); h.screen().openBlock('subject'); h.screen().selectSubject('12'); h.screen().setMobileTypes(['LECTURE']); await vi.waitFor(() => expect(h.screen().ready).toBe(true))
    h.screen().openStudentDetail(11, {} as MouseEvent); await vi.waitFor(() => expect(h.screen().ready).toBe(true))
    const sourceCard = find(h.root, (node) => node.props.class === 'headman-stats-mobile__card headman-stats-mobile__sources')!
    const ticketCard = find(h.root, (node) => node.props.class === 'headman-stats-mobile__ticket-cards')!
    if (legacy) { expect(text(sourceCard)).toContain('Сервер не передал источники'); expect(text(ticketCard)).toContain('—'); expect(text(h.root)).toContain('Сервер не передал счётчики тикетов') }
    else { expect(text(sourceCard)).toMatch(/Гео\s*73%/); expect(text(ticketCard)).toMatch(/Подано\s*1/) }
    expect(text(sourceCard)).not.toMatch(/Гео\s*25%/)
  })
  it('the personal chart retry repeats the personal endpoint and clears its visible error', async () => {
    const h = harness(); await vi.waitFor(() => expect(h.screen().ready).toBe(true)); h.screen().openStudentDetail(11, {} as MouseEvent)
    await vi.waitFor(() => expect(text(h.root)).toContain('Личный график временно недоступен'))
    h.recover(); const retry = find(h.root, node => node.tag === 'button' && text(node) === 'Повторить')!; (retry.props.onClick as () => void)()
    await vi.waitFor(() => expect(h.personalCalls()).toBe(2)); await vi.waitFor(() => expect(h.screen().ready).toBe(true))
    expect(text(h.root)).not.toContain('Личный график временно недоступен')
  })
  it('an acknowledged history write refreshes today overview and cannot publish after the owner changes', async () => {
    const h = harness(); h.recover(); await vi.waitFor(() => expect(h.screen().ready).toBe(true))
    h.screen().openStudentDetail(11, {} as MouseEvent); h.screen().openHistory(); await nextTick()
    h.today(20); click(h.root, 'test-history-ack'); await vi.waitFor(() => expect(h.overviewCalls()).toBe(2))
    h.screen().back(); h.screen().back(); await vi.waitFor(() => expect(text(h.root)).toContain('20 из 27'))
    h.screen().openStudentDetail(11, {} as MouseEvent); h.screen().openHistory(); await nextTick(); h.holdOverview(); click(h.root, 'test-history-ack')
    await vi.waitFor(() => expect(h.overviewCalls()).toBe(3)); h.props.api = null as unknown as HeadmanStatsApi; await nextTick(); h.releaseOverview(); await nextTick(); await nextTick()
    expect(text(h.root)).not.toContain('20 из 27')
  })
  it('NONE hides the whole semester chart card without issuing an all-type fallback', async () => {
    const h = harness(); await vi.waitFor(() => expect(h.screen().ready).toBe(true)); h.screen().openBlock('subject'); h.screen().selectSubject('12'); await vi.waitFor(() => expect(h.screen().ready).toBe(true))
    expect(find(h.root, (node) => node.props['aria-label'] === 'График посещаемости')).toBeDefined()
    h.screen().setMobileTypes([]); await nextTick()
    expect(find(h.root, (node) => node.props['aria-label'] === 'График посещаемости')).toBeUndefined()
    expect(text(h.root)).toContain('Выбери')
  })
  it('week and subject trend controls send their actual date and type scope, then restore semester mode on return', async () => {
    const fixture = harness(); await vi.waitFor(() => expect(fixture.screen().ready).toBe(true)); fixture.screen().openTool('trend'); await nextTick()
    const week = find(fixture.root, node => node.tag === 'button' && text(node) === 'Неделя')!; (week.props.onClick as () => void)(); await vi.waitFor(() => expect(fixture.screen().ready).toBe(true))
    const nextWeek = find(fixture.root,node=>node.props['aria-label'] === 'Следующая неделя')!; expect(nextWeek.props.disabled).toBe(true)
    const previous = find(fixture.root,node=>node.props['aria-label'] === 'Предыдущая неделя')!; (previous.props.onClick as ()=>void)()
    await vi.waitFor(() => expect(fixture.trendQueries.at(-1)).toMatchObject({mode:'WEEK',weekStart:'2026-09-28',bucketUnit:'DAY'}))
    await vi.waitFor(()=>expect(find(fixture.root,node=>node.tag==='small'&&node.props['data-parity-number']===1)).toBeDefined())
    const subject = find(fixture.root, node => node.tag === 'button' && text(node) === 'Предмет')!; (subject.props.onClick as () => void)(); await vi.waitFor(() => expect(fixture.screen().ready).toBe(true))
    const type = find(fixture.root, node => node.tag === 'button' && node.props['data-lesson-type'] === 'LECTURE')!; (type.props.onClick as ()=>void)()
    fixture.screen().setChartRange('days'); await vi.waitFor(() => expect(fixture.trendQueries.at(-1)).toMatchObject({mode:'SUBJECT',subjectId:12,lessonTypes:['LECTURE'],bucketUnit:'DAY'}))
    fixture.screen().back(); await vi.waitFor(() => expect(fixture.trendQueries.at(-1)).toMatchObject({mode:'SEMESTER',semesterId:7,bucketUnit:'DAY'}))
  })

})

describe('R3 server-normalized metric sorting',()=>{
  it.each([null,12])('offers exactly8 server percent choices and independent alphabet reset in subject=%s',async(subjectId)=>{
    const fixture=harness();await vi.waitFor(()=>expect(fixture.screen().ready).toBe(true))
    if(subjectId){fixture.screen().openBlock('subject');fixture.screen().selectSubject(String(subjectId));await vi.waitFor(()=>expect(fixture.screen().ready).toBe(true))}
    const choices=['present','excused','absent','presentOrExcused'].flatMap(kind=>[false,true].map(descending=>({field:kind+'Percent',descending})))
    for(const choice of choices){
      fixture.screen().openTool('sort');await nextTick()
      const trigger=find(fixture.root,node=>node.props['aria-label']==='Порядок'&&node.tag==='button')!;(trigger.props.onClick as ()=>void)();await nextTick()
      const options:Node[]=[];const collect=(node:Node)=>{if(node.props.role==='option')options.push(node);node.children.forEach(collect)};collect(fixture.root)
      expect(options).toHaveLength(8)
      const index=choices.findIndex(item=>item.field===choice.field&&item.descending===choice.descending);(options[index]!.props.onClick as ()=>void)()
      await vi.waitFor(()=>expect(fixture.statsQueries.at(-1)).toMatchObject({subjectId,sorts:[choice]}));await vi.waitFor(()=>expect(fixture.screen().ready).toBe(true))
    }
    fixture.screen().openTool('sort');await nextTick();click(fixture.root,'headman-stats__sort-reset')
    await vi.waitFor(()=>expect(fixture.statsQueries.at(-1)).toMatchObject({subjectId,sorts:[{field:'displayName',descending:false}]}))
  })
})

it('a delayed academic policy cannot replace the current week policy after pager context changes',async()=>{
  const fixture=harness();await vi.waitFor(()=>expect(fixture.screen().ready).toBe(true));fixture.screen().openTool('trend');await nextTick()
  const week=find(fixture.root,node=>node.tag==='button'&&text(node)==='Неделя')!;(week.props.onClick as ()=>void)()
  await vi.waitFor(()=>expect(find(fixture.root,node=>node.tag==='small'&&node.props['data-parity-number']===2)).toBeDefined());fixture.holdNextPolicy()
  const previous=find(fixture.root,node=>node.props['aria-label']==='Предыдущая неделя')!;(previous.props.onClick as ()=>void)()
  await vi.waitFor(()=>expect(fixture.policyHeld()).toBe(true));await vi.waitFor(()=>expect(fixture.screen().ready).toBe(true))
  const next=find(fixture.root,node=>node.props['aria-label']==='Следующая неделя')!;(next.props.onClick as ()=>void)()
  await vi.waitFor(()=>expect(find(fixture.root,node=>node.tag==='small'&&node.props['data-parity-number']===2)).toBeDefined());fixture.releasePolicy();await nextTick();await new Promise(resolve=>setTimeout(resolve,0))
  expect(find(fixture.root,node=>node.tag==='small'&&node.props['data-parity-number']===2)).toBeDefined();expect(find(fixture.root,node=>node.tag==='small'&&node.props['data-parity-number']===1)).toBeUndefined()
  expect(fixture.trendQueries.at(-1)).toMatchObject({mode:'WEEK',weekStart:'2026-10-05'})
})
