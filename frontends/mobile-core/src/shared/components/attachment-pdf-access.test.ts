import { afterEach, expect, it, vi } from 'vitest'
import { ServerAttachmentPreview, type ServerAttachmentOwner, type ServerAttachmentPreviewState } from '../../features/requests/server-attachment-preview'
afterEach(()=>vi.useRealTimers())
it('refuses PDF opening when the wall clock crosses expiry before the expiry timer runs',async()=>{
  vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-07T00:00:00Z'))
  const owner: ServerAttachmentOwner = {api:{downloadAttachment:async()=>new Blob(['%PDF-1.7'],{type:'application/pdf'})},requestId:'ticket',attachment:{id:'file',name:null,contentType:'application/pdf',size:8,sha256:null,state:'AVAILABLE',uploadedAt:null,expiresAt:'2026-10-07T00:00:01Z',downloadUrl:null},ownerKey:'account/group',allowed:true,offline:false}
  let state:ServerAttachmentPreviewState={status:'idle',url:null,mime:null,error:null}
  const revoke=vi.fn(); const preview=new ServerAttachmentPreview(()=>owner,next=>{state=next},{createObjectURL:()=> 'blob:owned-pdf',revokeObjectURL:revoke})
  await preview.load(); expect(preview.openUrl()).toBe('blob:owned-pdf')
  // Setting the wall clock does not advance timers: the rendered URL can still exist.
  vi.setSystemTime(new Date('2026-10-07T00:00:02Z')); expect((state as ServerAttachmentPreviewState).status).toBe('ready')
  expect(preview.openUrl()).toBeNull(); expect(revoke).toHaveBeenCalledWith('blob:owned-pdf')
})
