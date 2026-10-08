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
async function input(node: TestElement,value: string): Promise<void> { node.value = value; node.dispatchEvent(new Event('input')); await flush() }
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
