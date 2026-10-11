import { createRenderer, h, markRaw, nextTick, shallowReactive, type App } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TeacherJournalScreen from './TeacherJournalScreen.vue'
import type { TeacherApi, TeacherJournalQuery, TeacherJournalResponse } from './teacher-client'
import type { ReportDownloadPort, ReportDownloadTicketRequest } from '../../shared/report-download-client'
vi.mock('../headman-stats/HeadmanStatsSelect.vue',()=>({default:{render:()=>null}}))
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


async function settle():Promise<void>{for(let i=0;i<16;i++){await Promise.resolve();await nextTick()}}
const subjects=[{groupId:2,subjectId:3,subjectName:'Базы данных',lessonTypes:['lecture','practice']}]
function response(subjectId=3,name='Анна'):TeacherJournalResponse{return{page:0,pageSize:100,totalLessons:1,hasMore:false,serverNow:'2026-10-09T12:00:00Z',lessons:[{id:30,assignmentId:1,groupId:2,groupName:'ИКБО',subjectId,subjectName:'Базы данных',semesterId:7,lessonType:'lecture',date:'2026-10-01',lessonNumber:1,startsAt:null,endsAt:null,room:null,status:'CLOSED',cancelled:false,cancellationReason:null,oneOff:false,moved:false}],students:[{studentId:4,displayName:name,cells:[{lessonId:30,status:'excused',symbol:'у',source:'NO_TICKET',recordPresent:true,pendingTicket:false,autoAbsent:false,ticketId:null,excuseType:'illness',excuseReason:'Болезнь'}]}]}}
function mount(options:{initialTypes?:string[];journal?:(query:TeacherJournalQuery)=>Promise<TeacherJournalResponse>;report?:ReportDownloadPort}={}){
 const calls:TeacherJournalQuery[]=[];const manual:[number,number][]=[]
 const api=markRaw({journal:async(query:TeacherJournalQuery)=>{calls.push(query);return options.journal?options.journal(query):response()},journalExportFormats:async()=>[{code:'docx',label:'DOCX',extension:'docx',contentType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document'}]}) as unknown as TeacherApi
 const state=shallowReactive<{query:TeacherJournalQuery;active:boolean}>({query:{semesterId:7,groupId:2,subjectId:3,lessonTypes:options.initialTypes??[],pastOnly:true},active:true})
 const root=new Node('root')
 const app=renderer.createApp({render:()=>h(TeacherJournalScreen,{api,query:state.query,active:state.active,subjectOptions:subjects,reportDownload:options.report??null,'onUpdate-query':(query:TeacherJournalQuery)=>{state.query=query},'onOpen-manual-excuse':(lessonId:number,studentId:number)=>{manual.push([lessonId,studentId])}})})
 apps.push(app);app.mount(root);return{root,state,calls,manual}
}
describe('teacher journal context and navigation',()=>{
 it('invalidates an in-flight report after navigating away from a retained journal',async()=>{
  let release!:(value:'accepted')=>void;let active!:()=>boolean
  const report:ReportDownloadPort={download:(_value,guard)=>{active=guard;return new Promise(resolve=>{release=resolve})}}
  const view=mount({initialTypes:['lecture'],report});await settle();click(view.root,'Скачать журнал за выбранный период');await settle()
  expect(active()).toBe(true)
  view.state.active=false;await settle();expect(active()).toBe(false)
  release('accepted');await settle();expect(text(view.root)).not.toContain('Telegram принял')
  view.state.active=true;await settle();expect(text(view.root)).toContain('Анна')
 })
 it('does not request a journal until explicit type selection and supports a real multiselect',async()=>{
  const view=mount();await settle();expect(view.calls).toHaveLength(0);expect(text(view.root)).toContain('Выбери один или несколько типов занятий.')
  click(view.root,'Лекция');await settle();click(view.root,'Практика');await settle()
  expect(view.state.query.lessonTypes).toEqual(['lecture','practice']);expect(view.calls.at(-1)?.lessonTypes).toEqual(['lecture','practice']);expect(view.calls.every(query=>query.pastOnly&&query.page===0)).toBe(true)
  click(view.root,'Лекция');click(view.root,'Практика');await settle();expect(view.state.query.lessonTypes).toEqual([]);expect(text(view.root)).not.toContain('Анна')
 })
 it('discards an older subject response after context changes',async()=>{
  let release!:(value:TeacherJournalResponse)=>void
  const view=mount({initialTypes:['lecture'],journal:query=>query.subjectId===3?new Promise(resolve=>{release=resolve}):Promise.resolve(response(8,'Новый контекст'))})
  view.state.query={...view.state.query,subjectId:8};await settle();release(response(3,'Устаревшая строка'));await settle()
  expect(text(view.root)).toContain('Новый контекст');expect(text(view.root)).not.toContain('Устаревшая строка')
 })
 it('opens a manual excuse with exact historical lesson/student context',async()=>{
  const view=mount({initialTypes:['lecture']});await settle()
  const button=find(view.root,item=>item.tag==='button'&&String(item.props['aria-label']).startsWith('Анна: Уважительная причина'))
  expect(button).toBeDefined();(button!.props.onClick as ()=>void)();expect(view.manual).toEqual([[30,4]])
 })
 it('invalidates an in-flight report on subject switch and never displays accepted as downloaded',async()=>{
  let release!:(value:'accepted')=>void;let active!:()=>boolean;let request!:ReportDownloadTicketRequest
  const report:ReportDownloadPort={download:(value,guard)=>{request=value;active=guard;return new Promise(resolve=>{release=resolve})}}
  const view=mount({initialTypes:['lecture'],report});await settle();click(view.root,'Скачать журнал за выбранный период');await settle()
  expect(text(view.root)).toContain('Готовим выгрузку…');expect(request.teacherJournal?.groupId).toBe(2)
  view.state.query={semesterId:7,groupId:2,subjectId:8,lessonTypes:[],pastOnly:true};await settle();expect(active()).toBe(false);release('accepted');await settle();expect(text(view.root)).not.toContain('Telegram принял')
 })
})
