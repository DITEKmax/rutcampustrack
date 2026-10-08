import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { TeacherApi, TeacherExcuseAttachment } from './teacher-client'
export interface TeacherAttachmentOwner { readonly api: Pick<TeacherApi,'downloadAttachment'> | null; readonly requestId: string; readonly attachment: TeacherExcuseAttachment }
export interface TeacherAttachmentState { readonly status: 'idle' | 'loading' | 'ready' | 'error'; readonly url: string | null; readonly mime: 'image/png' | 'image/jpeg' | 'application/pdf' | null; readonly error: string | null }
type ObjectUrls = Pick<typeof URL,'createObjectURL' | 'revokeObjectURL'>
const idle = (): TeacherAttachmentState => ({status:'idle',url:null,mime:null,error:null})
function expired(owner: TeacherAttachmentOwner): boolean { const expiry = owner.attachment.expiresAt; return expiry !== null && (!Number.isFinite(Date.parse(expiry)) || Date.parse(expiry) <= Date.now()) }
async function supported(blob: Blob): Promise<TeacherAttachmentState['mime']> {
  if (!blob.size || !['image/png','image/jpeg','application/pdf'].includes(blob.type)) return null
  const bytes = new Uint8Array(await blob.slice(0,8).arrayBuffer()); const matches = (signature: number[]) => signature.every((byte,index) => bytes[index] === byte)
  return blob.type === 'image/png' && matches([137,80,78,71,13,10,26,10]) ? 'image/png' : blob.type === 'image/jpeg' && matches([255,216,255]) ? 'image/jpeg' : blob.type === 'application/pdf' && matches([37,80,68,70,45]) ? 'application/pdf' : null
}
/** Only the teacher API supplies bytes. Each URL belongs to one current request/session. */
export class TeacherAttachmentPreview {
  private revision = 0; private disposed = false; private ownedUrl: string | null = null; private readyOwner: TeacherAttachmentOwner | null = null
  private timer: ReturnType<typeof setTimeout> | undefined
  private state = idle()
  constructor(private readonly owner: () => TeacherAttachmentOwner, private readonly publish: (state: TeacherAttachmentState) => void, private readonly urls: ObjectUrls = URL) {}
  private set(state: TeacherAttachmentState): void { this.state = state; this.publish(state) }
  private release(): void { clearTimeout(this.timer); this.timer = undefined; if (this.ownedUrl) this.urls.revokeObjectURL(this.ownedUrl); this.ownedUrl = null; this.readyOwner = null }
  private fail(message: string): void { this.release(); this.set({status:'error',url:null,mime:null,error:message}) }
  private current(run: number, owner: TeacherAttachmentOwner): boolean { const next = this.owner(); return !this.disposed && run === this.revision && owner.api === next.api && owner.requestId === next.requestId && owner.attachment.id === next.attachment.id && owner.attachment.expiresAt === next.attachment.expiresAt && next.api !== null }
  private expireLater(run: number, owner: TeacherAttachmentOwner): void {
    if (owner.attachment.expiresAt === null) return
    const remaining = Date.parse(owner.attachment.expiresAt) - Date.now()
    if (remaining <= 0) { if (this.current(run,owner)) this.fail('Срок хранения файла истёк.'); return }
    this.timer = setTimeout(() => { if (!this.current(run,owner)) return; if (expired(owner)) this.fail('Срок хранения файла истёк.'); else this.expireLater(run,owner) },Math.min(remaining,2_147_483_647))
  }
  async load(): Promise<void> {
    const owner = this.owner(); const run = ++this.revision; this.release(); this.set(idle())
    if (this.disposed || !owner.api || !owner.requestId) return
    if (expired(owner)) { this.fail('Срок хранения файла истёк.'); return }
    this.set({status:'loading',url:null,mime:null,error:null})
    try {
      const blob = await owner.api.downloadAttachment(owner.requestId,owner.attachment.id)
      if (!this.current(run,owner)) return
      if (expired(owner)) { this.fail('Срок хранения файла истёк.'); return }
      const mime = await supported(blob)
      if (!this.current(run,owner)) return
      if (expired(owner)) { this.fail('Срок хранения файла истёк.'); return }
      if (!mime) { this.fail('Файл повреждён или имеет неподдерживаемый тип. Доступны JPEG, PNG и PDF.'); return }
      const url = this.urls.createObjectURL(blob)
      if (!this.current(run,owner)) { this.urls.revokeObjectURL(url); return }
      this.ownedUrl = url; this.readyOwner = owner; this.set({status:'ready',url,mime,error:null}); this.expireLater(run,owner)
    } catch (cause) {
      if (!this.current(run,owner)) return
      if (cause instanceof StaleSessionGenerationError) { this.set(idle()); return }
      this.fail(cause instanceof Error ? cause.message : 'Не удалось получить файл.')
    }
  }
  openUrl(): string | null { if (this.state.status !== 'ready') return null; if (!this.readyOwner || !this.current(this.revision,this.readyOwner)) { this.release(); this.set(idle()); return null }; if (expired(this.readyOwner)) { this.fail('Срок хранения файла истёк.'); return null }; return this.state.url }
  imageFailed(url: string): void { if (url === this.ownedUrl) this.fail('Изображение повреждено. Не удалось показать файл.') }
  dispose(): void { this.disposed = true; this.revision += 1; this.release(); this.set(idle()) }
}
