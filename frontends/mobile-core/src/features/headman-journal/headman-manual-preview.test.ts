import { describe, expect, it, vi } from 'vitest'
import { PDF_PREVIEW_MAX_BYTES } from '../../shared/components/attachment-pdf-bytes'
import { HeadmanManualPreview, type ManualPreviewOwner, type ManualPreviewState } from './headman-manual-preview'
const pdf = () => new Blob(['%PDF-1.7 sample'],{type:'application/pdf'})
function fixture(download: () => Promise<Blob> = async()=>pdf()) {
  let scope: ManualPreviewOwner = {api:{downloadAttachment:download},lessonId:1,userId:2,attachmentId:'a',file:null,ownerKey:'group1',allowed:true}
  let state: ManualPreviewState | null = null
  const released: string[] = []; let created = 0
  const preview = new HeadmanManualPreview(()=>scope,next=>{state=next},{createObjectURL:()=>`blob:fixture-${++created}`,revokeObjectURL:url=>{released.push(url)}})
  return {preview,get state(){return state},get created(){return created},released,change:(delta:Partial<ManualPreviewOwner>)=>{scope={...scope,...delta}}}
}
describe('manual journal preview ownership',()=>{
  it('discards late authenticated bytes after student/context change',async()=>{
    let resolve!: (blob:Blob)=>void
    const f=fixture(()=>new Promise<Blob>(next=>{resolve=next}))
    const pending=f.preview.load(); f.change({userId:3,ownerKey:'group2'}); resolve(pdf()); await pending
    expect(f.created).toBe(0); expect(f.state?.url).toBeNull()
  })
  it('revokes the visible URL immediately on loss of permission and disposal',async()=>{
    const f=fixture(); await f.preview.load(); expect(f.state?.url).toBe('blob:fixture-1')
    f.change({allowed:false}); await f.preview.load(); expect(f.released).toEqual(['blob:fixture-1']); expect(f.state?.url).toBeNull()
    f.change({allowed:true}); await f.preview.load(); f.preview.dispose(); expect(f.released).toEqual(['blob:fixture-1','blob:fixture-2'])
  })
  it.each([{userId:3},{ownerKey:'other-account'},{allowed:false}])('synchronously refuses an already visible URL after owner/context changes',async(delta)=>{
    const f=fixture(); await f.preview.load(); expect(f.preview.openUrl()).toBe('blob:fixture-1')
    f.change(delta); expect(f.preview.openUrl()).toBeNull(); expect(f.released).toEqual(['blob:fixture-1']); expect(f.state?.url).toBeNull()
  })
  it('rejects an oversized selected file before any signature/full read or URL allocation',async()=>{
    const file=new File(['%PDF-1.7'],'large.pdf',{type:'application/pdf'})
    vi.spyOn(file,'size','get').mockReturnValue(PDF_PREVIEW_MAX_BYTES+1)
    const slice=vi.spyOn(file,'slice'); const arrayBuffer=vi.spyOn(file,'arrayBuffer'); const download=vi.fn(async()=>pdf())
    const f=fixture(download); f.change({file}); await f.preview.load()
    expect(f.state?.error).toContain('слишком большой'); expect(slice).not.toHaveBeenCalled(); expect(arrayBuffer).not.toHaveBeenCalled(); expect(download).not.toHaveBeenCalled(); expect(f.created).toBe(0)
  })
  it('rejects spoofed content and does not create a URL after file removal',async()=>{
    const f=fixture(async()=>new Blob(['<html>'],{type:'application/pdf'})); await f.preview.load()
    expect(f.state?.error).toContain('повреждён'); expect(f.created).toBe(0)
    f.change({attachmentId:null}); await f.preview.load(); expect(f.state?.error).toBeNull()
  })
})
