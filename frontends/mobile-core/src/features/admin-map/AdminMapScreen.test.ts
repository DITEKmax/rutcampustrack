import { compile, ssrContextKey, createRenderer, h, markRaw, nextTick, reactive, ref, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminMapScreen from './AdminMapScreen.vue'
import mapSource from './AdminMapScreen.vue?raw'
import { compileScript, parse } from '@vue/compiler-sfc'
const descriptor = parse(mapSource).descriptor
AdminMapScreen.render = compile(descriptor.template!.content, { bindingMetadata: compileScript(descriptor, { id: 'admin-map-authority-test' }).bindings!, prefixIdentifiers: true })
import { AdminMapClient } from '../../api/map-client'
import type { AdminMapPlan } from '../../api/types'
import type { AdminAccessProfile } from '../admin-access/admin-access-client'
import { createAdminAuthorityOwner, createAdminGenerationReader } from '../../shared/admin-authority'
class TestElement extends EventTarget {
  children: TestElement[] = []
  parentElement: TestElement | null = null
  props: Record<string, unknown> = {}
  selected = false
  selectedIndex = -1
  get options(): TestElement[] { return this.children.filter(node => node.tag === 'option') }
  get multiple(): boolean { return Boolean(this.props.multiple) }
  value = ''
  text = ''
  composing = false
  constructor(readonly tag: string) { super(); markRaw(this) }
  get tagName(): string { return this.tag.toUpperCase() }
  get type(): string { return String(this.props.type ?? '') }
  getRootNode(): typeof testDocument { return testDocument }
  focus(): void { testDocument.activeElement = this }
}
class TestDocument { activeElement: TestElement | null = null }
const testDocument = new TestDocument()
const renderer = createRenderer<TestElement, TestElement>({
  createElement: (tag) => new TestElement(tag), createText: (text) => Object.assign(new TestElement('#text'), { text }), createComment: () => new TestElement('#comment'),
  setText: (node, text) => { node.text = text }, setElementText: (node, text) => { node.text = text; node.children = [] }, parentNode: (node) => node.parentElement, nextSibling: (node) => node.parentElement?.children[node.parentElement.children.indexOf(node) + 1] ?? null,
  patchProp: (node, key, _old, value) => { node.props[key] = value; if (key === 'value') node.value = String(value ?? '') },
  remove: (node) => { if (node.parentElement) node.parentElement.children = node.parentElement.children.filter((item) => item !== node); node.parentElement = null },
  insert: (node, parent, anchor) => { const index = anchor ? parent.children.indexOf(anchor) : -1; parent.children.splice(index < 0 ? parent.children.length : index, 0, node); node.parentElement = parent },
})

const apps: App[] = []
afterEach(() => { for (const app of apps.splice(0)) app.unmount(); vi.unstubAllGlobals() })
function nodes(root: TestElement): TestElement[] { return [root, ...root.children.flatMap(nodes)] }
function text(root: TestElement): string { return nodes(root).map(node => node.text).join(' ') }
function button(root: TestElement, label: string): TestElement { const node = nodes(root).find(node => node.tag === 'button' && text(node).trim() === label); if (!node) throw new Error('Missing '+label); return node }
function click(node: TestElement): void { (node.props.onClick as () => void)() }
async function input(node: TestElement,value: string): Promise<void> { node.value=value;node.dispatchEvent(new Event('input'));await nextTick() }
async function settle(): Promise<void> { for (let i=0;i<12;i++) { await Promise.resolve();await nextTick() } }
function access(deletion=false): AdminAccessProfile { return {userId:1,assigned:true,mode:'LIMITED',permissions:['CAMPUS_MAP_MANAGE',...(deletion?['DATA_DELETE']:[])],version:1} }
const inventory=[{id:'20',code:'1',label:'Корпус1',floors:[{id:'200',buildingId:'20',code:'1',label:'Этаж1',currentPlan:null,openCount:0,openCountPeriod:'all_time'}]}]
function mount(client: AdminMapClient,authority: AdminAccessProfile|null) {
  vi.stubGlobal('document',testDocument);vi.stubGlobal('Document',TestDocument)
  const props=reactive({client:markRaw(client),authority:authority?markRaw(authority):null})
  const root=new TestElement('root');const app=renderer.createApp({setup:()=>()=>h(AdminMapScreen,props)});app.provide(ssrContextKey,{modules:new Set()});apps.push(app);app.mount(root)
  return {root,props}
}
const slot = (format: 'png' | 'svg') => ({format,state:'ready' as const,contentType:format==='png'?'image/png' as const:'image/svg+xml' as const,id:'asset-'+format,bytes:3,sha256:'a'.repeat(64),width:1,height:1,viewBox:null})
const plan: AdminMapPlan = {buildingId:'20',floorId:'200',version:'2',catalogRevision:'r2',label:'plan',png:slot('png'),svg:slot('svg')}
function chooseFile(root: TestElement): File {
  const file = new File(['map'], 'plan.png', {type:'image/png'})
  const node = nodes(root).find(node=>node.tag==='input'&&String(node.props.accept).includes('png'))!
  ;(node.props.onChange as (event: unknown)=>void)({target:{files:[file]}})
  return file
}
async function draft(root: TestElement,building='1',floor='1'): Promise<File> {
  click(nodes(root).find(node=>node.props['aria-label']==='Добавить схему')!);await nextTick()
  await input(nodes(root).find(node=>node.props['data-field']==='building-code')!,building)
  await input(nodes(root).find(node=>node.props['data-field']==='floor-code')!,floor)
  const file=chooseFile(root);await nextTick();return file
}
describe('mounted admin map authority and session boundary',()=>{
  it('does not load or expose mutation controls before authority is confirmed',async()=>{
    const fetcher=vi.fn<typeof fetch>(async()=>Response.json(inventory))
    const {root}=mount(new AdminMapClient({accessToken:()=>null,currentGeneration:()=>1,fetcher}),null);await settle()
    expect(fetcher).not.toHaveBeenCalled();expect(text(root)).toContain('Управление картой недоступно')
    expect(nodes(root).some(node=>node.tag==='input')).toBe(false)
  })
  it('allows cross-tab generation cleanup to finish while the map sync watcher runs',async()=>{
    const generation=ref(1);const owner=createAdminAuthorityOwner();owner.bind({getMe:async()=>access()},'owner','1');await owner.refresh()
    const revision=owner.snapshot.revision
    const fetcher=vi.fn<typeof fetch>(async()=>Response.json(inventory))
    const client=new AdminMapClient({accessToken:()=>null,currentGeneration:createAdminGenerationReader(()=>generation.value,()=>owner.isCurrent(revision),1),fetcher})
    mount(client,access());await settle()
    let cleanupCompleted=false
    expect(()=>{generation.value=2;cleanupCompleted=true}).not.toThrow()
    expect(cleanupCompleted).toBe(true);expect(client.isCurrentGeneration(1)).toBe(false)
    await settle();expect(fetcher).toHaveBeenCalledTimes(1);owner.dispose()
  })
  it('drops late map JSON and clears sensitive proof when current authority disappears',async()=>{
    const owner=createAdminAuthorityOwner();owner.bind({getMe:async()=>access(true)},'owner','1');await owner.refresh()
    const revision=owner.snapshot.revision
    let release!:(value:unknown)=>void
    const response=new Response('{}');response.json=()=>new Promise(resolve=>{release=resolve})
    const client=new AdminMapClient({accessToken:()=>null,currentGeneration:createAdminGenerationReader(()=>1,()=>owner.isCurrent(revision),1),fetcher:async()=>response})
    const {root,props}=mount(client,access(true));await settle()
    owner.clear();props.authority=null;release(inventory);await settle()
    expect(text(root)).toContain('Управление картой недоступно');expect(text(root)).not.toContain('Корпус1')
    expect(client.isCurrentGeneration(1)).toBe(false);owner.dispose()
  })
  it('uploads one exact target/files command without DATA_DELETE and accepts an existing floor',async()=>{
    const fetcher=vi.fn<typeof fetch>(async(_url,init)=>Response.json(init?.method==='POST'?{status:'created',buildingCreated:false,floorCreated:false,plan}:inventory,{status:init?.method==='POST'?201:200}))
    const {root}=mount(new AdminMapClient({accessToken:()=>null,currentGeneration:()=>1,fetcher}),access());await settle()
    expect(nodes(root).filter(node=>node.props['data-action']==='delete')).toHaveLength(0)
    const file=await draft(root);click(button(root,'Сохранить схему'));await settle()
    const posts=fetcher.mock.calls.filter(([,init])=>init?.method==='POST');expect(posts).toHaveLength(1)
    expect(posts[0]![0]).toBe('/api/academic/map/plans')
    const body=posts[0]![1]!.body as FormData;expect(body.get('buildingCode')).toBe('1');expect(body.get('floorCode')).toBe('1');expect((body.get('png') as File).name).toBe(file.name)
    expect(text(root)).toContain('Схема добавлена');expect(fetcher.mock.calls.some(([url])=>String(url).includes('deletion'))).toBe(false)
  })
  it('allows the protected DATA_DELETE flow and clears its dialog when deletion rights are revoked',async()=>{
    const fetcher=vi.fn<typeof fetch>(async(url)=>{
      if(String(url).endsWith('/deletion-preview'))return Response.json({targetType:'FLOOR',targetId:'200',buildingId:'20',label:'Этаж1',versions:0,assets:0,bytes:0,openCount:0,remainingFloors:1,previewDigest:'a'.repeat(64)})
      if(String(url).endsWith('/deletion'))return Response.json({code:'CURRENT_PASSWORD_INVALID'},{status:403})
      return Response.json(inventory)
    })
    const {root,props}=mount(new AdminMapClient({accessToken:()=>null,currentGeneration:()=>1,fetcher}),access(true));await settle()
    click(button(root,'Удалить этаж со всеми версиями…'));await settle()
    await input(nodes(root).find(node=>node.tag==='input'&&node.props.type==='password')!,'synthetic-proof')
    click(button(root,'Подтвердить окончательное удаление'));await settle()
    expect(text(root)).toContain('Пароль не подошёл');expect(nodes(root).some(node=>node.props.role==='dialog')).toBe(true)
    props.authority=markRaw({...access(),version:2});await settle()
    expect(nodes(root).some(node=>node.props.role==='dialog')).toBe(false)
    expect(nodes(root).filter(node=>node.tag==='input'&&node.props.type==='password')).toHaveLength(0)
    expect(fetcher.mock.calls.filter(([url])=>String(url).endsWith('/deletion'))).toHaveLength(1)
  })
  it('preserves target and selected files after rejected publication',async()=>{
    const fetcher=vi.fn<typeof fetch>(async(_url,init)=>init?.method==='POST'?Response.json({detail:'Файл отклонён'},{status:400}):Response.json(inventory))
    const {root}=mount(new AdminMapClient({accessToken:()=>null,currentGeneration:()=>1,fetcher}),access());await settle()
    await draft(root,'2','3');click(button(root,'Сохранить схему'));await settle()
    expect(text(root)).toContain('Файл отклонён');expect(text(root)).toContain('plan.png')
    expect(nodes(root).find(node=>node.props['data-field']==='building-code')!.value).toBe('2')
    click(button(root,'Сохранить схему'));await settle();expect(fetcher.mock.calls.filter(([,init])=>init?.method==='POST')).toHaveLength(2)
  })
  it('reconciles an unknown publication without another POST or fabricated success',async()=>{
    let published=false
    const fetcher=vi.fn<typeof fetch>(async(_url,init)=>{
      if(init?.method==='POST'){published=true;throw new TypeError('connection lost')}
      return Response.json(published?[{...inventory[0],floors:[{...inventory[0]!.floors[0],currentPlan:plan}]}]:inventory)
    })
    const {root}=mount(new AdminMapClient({accessToken:()=>null,currentGeneration:()=>1,fetcher}),access());await settle()
    await draft(root);click(button(root,'Сохранить схему'));await settle()
    expect(text(root)).toContain('Исход загрузки неизвестен');expect(text(root)).toContain('plan.png')
    click(button(root,'Сохранить схему'));await settle();expect(fetcher.mock.calls.filter(([,init])=>init?.method==='POST')).toHaveLength(1)
    click(button(root,'Проверить список с сервера'));await settle()
    expect(text(root)).toContain('На сервере появилась новая версия');expect(text(root)).not.toContain('Изменение сохранено')
    click(button(root,'Завершить проверку, очистить форму'));await settle();expect(text(root)).not.toContain('plan.png')
    expect(fetcher.mock.calls.filter(([,init])=>init?.method==='POST')).toHaveLength(1)
  })
  it('drops late upload ACK and files when the authenticated client changes',async()=>{
    let release!:(response:Response)=>void
    const fetcher=vi.fn<typeof fetch>(async(_url,init)=>init?.method==='POST'?new Promise(resolve=>{release=resolve}):Response.json(inventory))
    const nextFetcher=vi.fn<typeof fetch>(async()=>Response.json(inventory))
    const {root,props}=mount(new AdminMapClient({accessToken:()=>null,currentGeneration:()=>1,fetcher}),access());await settle()
    await draft(root,'2');click(button(root,'Сохранить схему'));await settle()
    props.client=markRaw(new AdminMapClient({accessToken:()=>null,currentGeneration:()=>2,fetcher:nextFetcher}));await settle()
    release(Response.json({status:'created',buildingCreated:true,floorCreated:true,plan},{status:201}));await settle()
    expect(fetcher.mock.calls.filter(([,init])=>init?.method==='POST')).toHaveLength(1)
    expect(nextFetcher.mock.calls.filter(([,init])=>init?.method==='POST')).toHaveLength(0)
    expect(text(root)).not.toContain('Изменение сохранено');expect(text(root)).not.toContain('plan.png')
  })
  it('never displays late asset bytes for a changed floor',async()=>{
    let release!:(response:Response)=>void
    const data=[{...inventory[0],floors:[{...inventory[0]!.floors[0],currentPlan:plan},{...inventory[0]!.floors[0],id:'201',code:'2'}]}]
    const fetcher=vi.fn<typeof fetch>(async(url)=>String(url).includes('/assets/')?new Promise(resolve=>{release=resolve}):Response.json(data))
    const {root}=mount(new AdminMapClient({accessToken:()=>null,currentGeneration:()=>1,fetcher}),access());await settle()
    click(button(root,'Открыть схему'));await settle()
    const floor=nodes(root).filter(node=>node.tag==='select')[1]!;floor.options[0]!.selected=false;floor.options[1]!.selected=true;floor.dispatchEvent(new Event('change'));await settle()
    release(new Response('map',{headers:{'Content-Type':'image/png'}}));await settle()
    expect(nodes(root).some(node=>node.tag==='img')).toBe(false)
    expect(nodes(root).some(node=>node.tag==='a'&&node.props.download)).toBe(false)
  })
  it('removes loaded v1 preview/download when inventory refresh returns v2 with identical IDs',async()=>{
    let version='1'
    const fetcher=vi.fn<typeof fetch>(async(url)=>String(url).includes('/assets/')
      ? new Response('map',{headers:{'Content-Type':String(url).includes('/png/')?'image/png':'image/svg+xml'}})
      : Response.json([{...inventory[0],floors:[{...inventory[0]!.floors[0],currentPlan:{...plan,version}}]}]))
    const {root}=mount(new AdminMapClient({accessToken:()=>null,currentGeneration:()=>1,fetcher}),access());await settle()
    click(button(root,'Открыть схему'));await settle()
    const image=nodes(root).find(node=>node.tag==='img')!;expect(image).toBeDefined()
    expect(nodes(root).some(node=>node.tag==='a'&&node.props.download)).toBe(true)
    const oldURL=image.props.src
    version='2';click(button(root,'Обновить список с сервера'));await settle()
    expect(nodes(root).some(node=>node.tag==='img')).toBe(false)
    expect(nodes(root).some(node=>node.tag==='a'&&node.props.download)).toBe(false)
    click(button(root,'Открыть схему'));await settle()
    expect(nodes(root).find(node=>node.tag==='img')!.props.src).not.toBe(oldURL)
    expect(fetcher.mock.calls.filter(([url])=>String(url).includes('/versions/2/assets/'))).toHaveLength(2)
  })
  it('drops late v1 assets after inventory refresh to v2 with identical IDs',async()=>{
    let version='1';const releases: ((response:Response)=>void)[]=[]
    const fetcher=vi.fn<typeof fetch>(async(url)=>String(url).includes('/assets/')
      ? new Promise(resolve=>{releases.push(resolve)})
      : Response.json([{...inventory[0],floors:[{...inventory[0]!.floors[0],currentPlan:{...plan,version}}]}]))
    const {root}=mount(new AdminMapClient({accessToken:()=>null,currentGeneration:()=>1,fetcher}),access());await settle()
    click(button(root,'Открыть схему'));await settle();expect(releases).toHaveLength(2)
    version='2';click(button(root,'Обновить список с сервера'));await settle()
    releases[0]!(new Response('map',{headers:{'Content-Type':'image/svg+xml'}}))
    releases[1]!(new Response('map',{headers:{'Content-Type':'image/png'}}));await settle()
    expect(nodes(root).some(node=>node.tag==='img')).toBe(false)
    expect(nodes(root).some(node=>node.tag==='a'&&node.props.download)).toBe(false)
    expect(button(root,'Открыть схему').props.disabled).toBe(false)
  })
  it('preserves existing labels on the server when only a number is edited',async()=>{
    const fetcher=vi.fn<typeof fetch>(async(_url,init)=>Response.json(init?.method==='PUT'?inventory[0]:inventory))
    const {root}=mount(new AdminMapClient({accessToken:()=>null,currentGeneration:()=>1,fetcher}),access());await settle()
    const fields=nodes(root).filter(node=>node.tag==='input'&&node.props.type==='text')
    await input(fields[0]!,'7');click(button(root,'Сохранить корпус'));await settle()
    const mutation=fetcher.mock.calls.find(([,init])=>init?.method==='PUT')!
    expect(JSON.parse(String(mutation[1]!.body))).toEqual({code:'7',label:'Корпус1'})
  })
})
