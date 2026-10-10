import * as Vue from 'vue'
import { compile as compileDom } from '@vue/compiler-dom'
import ts from 'typescript'
import { ssrContextKey, createRenderer, h, markRaw, nextTick, reactive, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { compileScript, parse } from '@vue/compiler-sfc'
import AdminSemesterScreen from './AdminSemesterScreen.vue'
import screenSource from './AdminSemesterScreen.vue?raw'
import AdminNotice from '../../shared/components/AdminNotice.vue'
import noticeSource from '../../shared/components/AdminNotice.vue?raw'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import iconSource from '../../shared/components/MobileIcon.vue?raw'
import { createAdminSemesterFixture, adminSemesterAuthority } from '../../dev/admin-semester-fixtures'
import type { AdminSemesterClient } from './admin-semester-client'
function clientRender(source: string) {
  const descriptor = parse(source).descriptor
  const code = compileDom(descriptor.template!.content, { mode:'function', bindingMetadata: compileScript(descriptor, { id:'semester-r2-test' }).bindings!, prefixIdentifiers:true, expressionPlugins:['typescript'] }).code
  return new Function('Vue', ts.transpileModule(code, {compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText)(Vue)
}
AdminSemesterScreen.render = clientRender(screenSource)
AdminNotice.render = clientRender(noticeSource)
MobileIcon.render = clientRender(iconSource)

class TestElement extends EventTarget {
  children: TestElement[] = []
  parentElement: TestElement | null = null
  props: Record<string, unknown> = {}
  style: Record<string, string> = {}
  value = ''
  checked = false
  text = ''
  composing = false
  constructor(readonly tag: string) { super(); markRaw(this) }
  get tagName(): string { return this.tag.toUpperCase() }
  get type(): string { return String(this.props.type ?? '') }
  getRootNode(): typeof testDocument { return testDocument }
}
class TestDocument { activeElement: TestElement | null = null; documentElement = {dataset:{}} }
const testDocument = new TestDocument()
const renderer = createRenderer<TestElement, TestElement>({
  createElement: tag => new TestElement(tag), createText: text => Object.assign(new TestElement('#text'), {text}), createComment: () => new TestElement('#comment'),
  setText: (node,text) => {node.text = text}, setElementText: (node,text) => {node.text = text; node.children = []}, parentNode: node => node.parentElement,
  nextSibling: node => node.parentElement?.children[node.parentElement.children.indexOf(node)+1] ?? null,
  patchProp: (node,key,_old,value) => { node.props[key] = value; if (key === 'value') node.value = String(value ?? ''); if (key === 'checked') node.checked = Boolean(value) },
  remove: node => { if (node.parentElement) node.parentElement.children = node.parentElement.children.filter(item => item !== node); node.parentElement = null },
  insert: (node,parent,anchor) => { const index = anchor ? parent.children.indexOf(anchor) : -1; parent.children.splice(index < 0 ? parent.children.length : index,0,node); node.parentElement = parent },
})
const apps: App[] = []
afterEach(() => { apps.splice(0).forEach(app => app.unmount()); vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.useRealTimers() })
function nodes(root: TestElement): TestElement[] { return [root,...root.children.flatMap(nodes)] }
function text(root: TestElement): string { return root.text + root.children.map(text).join('') }
function button(root: TestElement,label: string): TestElement { const found = nodes(root).find(node => node.tag === 'button' && text(node).trim() === label); if (!found) throw new Error('Missing button: '+label); return found }
async function flush(): Promise<void> { for(let i=0;i<15;i++) { await Promise.resolve(); await nextTick() } }
async function click(node: TestElement): Promise<void> { (node.props.onClick as () => unknown)(); await flush() }
async function input(node: TestElement,value: string): Promise<void> { node.value = value; const event = new Event('input'); node.dispatchEvent(event); if (typeof node.props.onInput === 'function') (node.props.onInput as (event: Event) => unknown)(event); await flush() }
async function submit(root: TestElement): Promise<void> { const form = nodes(root).find(node => node.tag === 'form')!; (form.props.onSubmit as (event: Event) => unknown)(new Event('submit',{cancelable:true})); await flush() }
function mount(render: () => ReturnType<typeof h>): {root: TestElement; app: App} { vi.stubGlobal('document',testDocument); vi.stubGlobal('Document',TestDocument); const root = new TestElement('root'); const app = renderer.createApp({setup:() => render}); apps.push(app); app.provide(ssrContextKey,{modules:new Set()}); app.mount(root); return {root,app} }

function storage() {
  const values = new Map<string,string>()
  return { getItem:(key:string) => values.get(key) ?? null, setItem:(key:string,value:string) => {values.set(key,value)}, removeItem:(key:string) => {values.delete(key)}, clear:() => values.clear(), key:(index:number) => [...values.keys()][index] ?? null, get length() {return values.size} }
}
function setup() {
  vi.useFakeTimers()
  const sessionStorage=storage()
  vi.stubGlobal('window',{sessionStorage,location:{origin:'https://synthetic.invalid'},setTimeout:(handler:()=>void,ms:number)=>setTimeout(handler,ms),clearTimeout:(id:ReturnType<typeof setTimeout>)=>clearTimeout(id)})
  vi.stubGlobal('document',{documentElement:{dataset:{}}})
  const client=createAdminSemesterFixture('delete-background')
  const props=reactive({authority:adminSemesterAuthority('delete-background')})
  return {client,props,sessionStorage}
}
async function start(client:AdminSemesterClient,props:ReturnType<typeof setup>['props']) {
  const onBack=vi.fn()
  const mounted=mount(()=>h(AdminSemesterScreen,{client:markRaw(client),authority:props.authority,onBack}))
  await flush();await click(button(mounted.root,'Удалить'))
  await input(nodes(mounted.root).find(node=>node.props.id==='admin-semester-delete-password')!,'Synthetic-only-password')
  await submit(mounted.root)
  return {...mounted,onBack}
}
describe('automatic durable deletion monitor',()=>{
  it('continues past the previous ten polls without overlap or a manual action, and shows success only after completion',async()=>{
    const {client,props}=setup()
    const original=client.getSemesterDeletionOperation.bind(client)
    let polls=0,inFlight=0,maxInFlight=0
    vi.spyOn(client,'getSemesterDeletionOperation').mockImplementation(async(...args)=>{inFlight++;maxInFlight=Math.max(maxInFlight,inFlight);const value=await original(...args);inFlight--;polls++;return polls===13?{...value,phase:'COMPLETED'}:value})
    const {root}=await start(client,props)
    expect(text(root)).not.toContain('Удаление завершено.')
    await vi.advanceTimersByTimeAsync(12*1200);await flush()
    expect(polls).toBe(12);expect(maxInFlight).toBe(1)
    expect(text(root)).not.toContain('Продолжить проверку');expect(text(root)).not.toContain('Удаление завершено.')
    await vi.advanceTimersByTimeAsync(1200);await flush()
    expect(text(root)).toContain('Удаление завершено.')
    expect(nodes(root).some(node=>node.props['data-semester-id']===1)).toBe(false)
  })
  it('retains the admitted command across Back/unmount and resumes from its saved operation on a later mount',async()=>{
    const {client,props,sessionStorage}=setup();const original=client.getSemesterDeletionOperation.bind(client)
    const first=await start(client,props);await click(nodes(first.root).find(node=>node.props['aria-label']==='Назад')!)
    expect(first.onBack).toHaveBeenCalledOnce();first.app.unmount();apps.splice(apps.indexOf(first.app),1)
    expect(sessionStorage.length).toBe(1)
    vi.spyOn(client,'getSemesterDeletionOperation').mockImplementation(async(...args)=>({...await original(...args),phase:'COMPLETED'}))
    const resumed=mount(()=>h(AdminSemesterScreen,{client:markRaw(client),authority:props.authority}));await flush()
    expect(text(resumed.root)).toContain('Удаление завершено.')
    expect(text(resumed.root)).not.toContain('Повторить тот же запрос')
  })
  it('clears current data and persisted preview after authority loss and ignores a late operation acknowledgement',async()=>{
    const {client,props,sessionStorage}=setup();const original=client.getSemesterDeletionOperation.bind(client)
    let release!:()=>void
    vi.spyOn(client,'getSemesterDeletionOperation').mockImplementation(async(...args)=>{const value=await original(...args);return new Promise(resolve=>{release=()=>resolve({...value,phase:'COMPLETED'})})})
    const {root}=await start(client,props);await vi.advanceTimersByTimeAsync(1200);await flush()
    props.authority=null;await flush();release();await flush()
    expect(sessionStorage.length).toBe(0);expect(text(root)).toContain('текущими правами')
    expect(text(root)).not.toContain('Удаление завершено.');expect(nodes(root).some(node=>node.props['data-semester-id'])).toBe(false)
  })
})

describe('semester metadata form and Moscow restoration guards', () => {
  function metadata(state: string) {
    setup(); vi.setSystemTime(new Date('2026-10-09T12:00:00Z'))
    const client = createAdminSemesterFixture(state)
    const mounted = mount(() => h(AdminSemesterScreen, { client: markRaw(client), authority: adminSemesterAuthority(state) }))
    return { ...mounted, client }
  }
  function card(root: TestElement, id: number): TestElement { return nodes(root).find(node => node.props['data-semester-id'] === id)! }
  function field(root: TestElement, id: string): TestElement { return nodes(root).find(node => node.props.id === id)! }
  function save(root: TestElement): TestElement { return nodes(root).find(node => node.tag === 'button' && node.props.type === 'submit')! }
  async function date(root: TestElement, type: 'from' | 'to', target: string, months = 0) {
    await click(nodes(root).find(node => node.tag === 'button' && node.props['aria-labelledby'] === `admin-semester-${type}-label admin-semester-${type}-value`)!)
    for (let i = 0; i < Math.abs(months); i++) await click(nodes(root).find(node => node.props['aria-label'] === (months > 0 ? 'Следующий месяц' : 'Предыдущий месяц'))!)
    await click(nodes(root).find(node => node.props['data-date'] === target)!)
    await click(nodes(root).find(node => node.props['aria-label'] === 'Назад')!)
  }
  it('blocks incomplete legacy classification before overlap or mutation and exposes field errors', async () => {
    const { root, client } = metadata('required-classification'); await flush()
    const update = vi.spyOn(client, 'updateSemester'), overlap = vi.spyOn(client, 'checkOverlap')
    await click(button(card(root, 1), 'Редактировать'))
    expect(save(root).props.disabled).toBe(true)
    expect(text(field(root, 'admin-semester-type-error'))).toContain('Выбери тип')
    expect(text(field(root, 'admin-semester-year-error'))).toContain('Укажи учебный год')
    await submit(root); expect(update).not.toHaveBeenCalled(); expect(overlap).not.toHaveBeenCalled()
    const radio = nodes(root).find(node => node.tag === 'input' && node.props.value === 'AUTUMN')!
    radio.checked = true; radio.dispatchEvent(new Event('change')); await flush()
    await input(nodes(root).find(node => node.tag === 'input' && node.props.type === 'number')!, '2026')
    expect(save(root).props.disabled).toBe(false)
  })
  it('requires explicit archived legacy year after selecting a type and preserves create defaults', async () => {
    const { root, client } = metadata('date-extension'); await flush()
    const original = client.getSemester.bind(client)
    vi.spyOn(client, 'getSemester').mockImplementation(async (...args) => ({ ...await original(...args), semesterType: null, academicYear: null }))
    const update = vi.spyOn(client, 'updateSemester')
    await click(button(card(root, 3), 'Редактировать'))
    const year = nodes(root).find(node => node.tag === 'input' && node.props.type === 'number')!
    expect(year.value).toBe('')
    const radio = nodes(root).find(node => node.tag === 'input' && node.props.value === 'SPRING')!
    radio.checked = true; radio.dispatchEvent(new Event('change')); await flush()
    expect(year.value).toBe('')
    expect(save(root).props.disabled).toBe(true)
    await submit(root); expect(update).not.toHaveBeenCalled()
    await date(root, 'to', '2026-10-31', 4)
    expect(year.value).toBe('')
    expect(save(root).props.disabled).toBe(true)
    await input(nodes(root).find(node => node.tag === 'input' && node.props.type === 'number')!, '2025')
    expect(save(root).props.disabled).toBe(false)
    await submit(root)
    expect(update).toHaveBeenCalledWith(3, expect.objectContaining({ semesterType:'SPRING', academicYear:2025, dateFrom:'2026-02-09', dateTo:'2026-10-31' }))
    await click(button(root, 'Добавить семестр'))
    expect(nodes(root).find(node => node.tag === 'input' && node.props.type === 'number')!.value).toBe('2026')
    expect(save(root).props.disabled).toBe(false)
  })
  it('blocks expired restoration, permits metadata-only extension, and preserves established classification', async () => {
    const { root, client } = metadata('date-extension'); await flush()
    const update = vi.spyOn(client, 'updateSemester')
    expect(button(card(root, 3), 'Восстановить').props.disabled).toBe(true)
    expect(nodes(card(root, 3)).some(node => node.tag === 'button' && text(node) === 'Сделать текущим')).toBe(false)
    await click(button(card(root, 3), 'Редактировать'))
    expect(nodes(root).find(node => node.tag === 'fieldset')?.props.disabled).toBe(true)
    expect(nodes(root).find(node => node.tag === 'input' && node.props.type === 'number')?.props.readonly).toBe(true)
    await date(root, 'from', '2026-02-10')
    expect(save(root).props.disabled).toBe(true); await submit(root); expect(update).not.toHaveBeenCalled()
    await date(root, 'from', '2026-02-09')
    await date(root, 'to', '2026-10-31', 4)
    expect(save(root).props.disabled).toBe(false); await submit(root)
    expect(update).toHaveBeenCalledWith(3, expect.objectContaining({dateFrom:'2026-02-09', dateTo:'2026-10-31', semesterType:'SPRING', academicYear:2025}))
    expect(button(card(root, 3), 'Восстановить').props.disabled).toBe(false)
    expect(nodes(card(root, 3)).some(node => node.props['aria-label'] === 'Архивный')).toBe(true)
  })
  it('keeps inclusive dateTo restorable until the next Moscow calendar day', async () => {
    const { root } = metadata('date-extension'); await flush()
    // 30 June UTC has already become 1 July in Moscow.
    vi.setSystemTime(new Date('2026-06-30T20:59:59Z'))
    await click(button(card(root, 3), 'Редактировать')); await click(nodes(root).find(node => node.props['aria-label'] === 'Назад')!)
    expect(button(card(root, 3), 'Восстановить').props.disabled).toBe(false)
    vi.setSystemTime(new Date('2026-06-30T21:00:00Z'))
    await click(button(card(root, 3), 'Редактировать')); await click(nodes(root).find(node => node.props['aria-label'] === 'Назад')!)
    expect(button(card(root, 3), 'Восстановить').props.disabled).toBe(true)
  })
  it('permits future activation and inclusive Moscow end date but blocks expired activation', async () => {
    setup(); vi.setSystemTime(new Date('2026-10-09T20:59:59Z'))
    const client = createAdminSemesterFixture('date-extension')
    const original = client.listSemesters.bind(client)
    vi.spyOn(client, 'listSemesters').mockImplementation(async (...args) => (await original(...args)).map((item, index) => ({ ...item, active: false, archived: false, isWriteBlocked: false, transition: 'NONE', releasePending: false, dateFrom: index === 0 ? '2026-11-01' : '2026-09-01', dateTo: index === 0 ? '2027-01-31' : index === 1 ? '2026-10-09' : '2026-10-08' })))
    const { root } = mount(() => h(AdminSemesterScreen, { client: markRaw(client), authority: adminSemesterAuthority('date-extension') })); await flush()
    expect(button(card(root, 1), 'Сделать текущим').props.disabled).toBe(false)
    expect(button(card(root, 2), 'Сделать текущим').props.disabled).toBe(false)
    expect(button(card(root, 3), 'Сделать текущим').props.disabled).toBe(true)
    vi.setSystemTime(new Date('2026-10-09T21:00:00Z'))
    await click(button(card(root, 2), 'Редактировать')); await click(nodes(root).find(node => node.props['aria-label'] === 'Назад')!)
    expect(button(card(root, 2), 'Сделать текущим').props.disabled).toBe(true)
  })
  it('rechecks fresh archive state before exposing editable metadata', async () => {
    const { root, client } = metadata('date-extension'); await flush()
    const original = client.getSemester.bind(client)
    vi.spyOn(client, 'getSemester').mockImplementation(async (...args) => ({ ...await original(...args), transition: 'RESTORING' }))
    await click(button(card(root, 3), 'Редактировать'))
    expect(nodes(root).some(node => node.props.class === 'admin-semester-form')).toBe(false)
    expect(text(root)).toContain('Дождись завершения архивации или восстановления')
  })
})
