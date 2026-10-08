import { createRenderer, h, markRaw, nextTick, type App } from 'vue'
import { afterEach, describe, expect, it } from 'vitest'
import TeacherAttachmentPreview from './TeacherAttachmentPreview.vue'
import { TeacherApi } from './teacher-client'
class Node { children: Node[]=[]; parent: Node|null=null; props: Record<string,unknown>={}; text=''; constructor(readonly tag:string) {markRaw(this)} }
const renderer=createRenderer<Node,Node>({createElement:tag=>new Node(tag),createText:text=>Object.assign(new Node('#text'),{text}),createComment:()=>new Node('#comment'),setText:(node,text)=>{node.text=text},setElementText:(node,text)=>{node.text=text;node.children=[]},parentNode:node=>node.parent,nextSibling:node=>node.parent?.children[node.parent.children.indexOf(node)+1]??null,patchProp:(node,key,_old,value)=>{node.props[key]=value},insert:(node,parent,anchor=null)=>{if(node.parent)node.parent.children.splice(node.parent.children.indexOf(node),1);const index=anchor?parent.children.indexOf(anchor):-1;parent.children.splice(index<0?parent.children.length:index,0,node);node.parent=parent},remove:node=>{node.parent?.children.splice(node.parent.children.indexOf(node),1);node.parent=null}})
function find(node:Node,predicate:(node:Node)=>boolean):Node|undefined {if(predicate(node))return node;for(const child of node.children){const found=find(child,predicate);if(found)return found};return undefined}
const apps:App[]=[]
afterEach(()=>{for(const app of apps.splice(0))app.unmount()})
async function settle():Promise<void>{for(let i=0;i<12;i++){await new Promise(resolve=>setTimeout(resolve,0));await nextTick()}}

describe('actual teacher attachment retry control',()=>{
 it('retries authenticated bytes through StudentWarningBlock and shows image',async()=>{
  let downloads=0;const errors:unknown[]=[];const api=markRaw(new TeacherApi({accessToken:()=>null,fetcher:async()=>{downloads+=1;return downloads===1?new Response(JSON.stringify({detail:'Временно недоступно'}),{status:503,headers:{'Content-Type':'application/json'}}):new Response(new Uint8Array([137,80,78,71,13,10,26,10,0]),{headers:{'Content-Type':'image/png'}})}}));
  const root=new Node('root');const app=renderer.createApp({render:()=>h(TeacherAttachmentPreview,{api,requestId:'request-a',attachment:{id:'file-a',fileName:'Справка.png',contentType:'image/png',size:9,uploadedAt:null,expiresAt:null}})});app.config.errorHandler=cause=>{errors.push(cause)};apps.push(app);app.mount(root);await settle();expect(downloads).toBe(1);const retry=find(root,node=>node.tag==='button'&&node.text.trim()==='Повторить');expect(retry).toBeDefined();(retry!.props.onClick as ()=>void)();await settle();expect(downloads).toBe(2);expect(errors).toEqual([]);expect(find(root,node=>node.tag==='img')).toBeDefined();
 })
})
