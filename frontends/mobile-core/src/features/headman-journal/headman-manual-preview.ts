import { PDF_PREVIEW_MAX_BYTES } from '../../shared/components/attachment-pdf-bytes'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { HeadmanJournalApi } from './headman-journal-client'
export interface ManualPreviewOwner {
  api: Pick<HeadmanJournalApi, 'downloadAttachment'> | null
  lessonId: number | null
  userId: number | null
  attachmentId: string | null
  file: File | null
  ownerKey: string
  allowed: boolean
}
export interface ManualPreviewState { url: string | null; mime: string | null; loading: boolean; error: string | null }
const empty = (): ManualPreviewState => ({url:null,mime:null,loading:false,error:null})
/** Releases selected and authenticated files when the journal form changes owner. */
export class HeadmanManualPreview {
  private revision = 0
  private disposed = false
  private url: string | null = null
  private readyOwner: ManualPreviewOwner | null = null
  constructor(private readonly owner: () => ManualPreviewOwner, private readonly publish: (state: ManualPreviewState) => void, private readonly urls: Pick<typeof URL,'createObjectURL'|'revokeObjectURL'> = URL) {}
  private release(): void { if (this.url) this.urls.revokeObjectURL(this.url); this.url = null; this.readyOwner = null }
  private current(run: number, scope: ManualPreviewOwner): boolean {
    const next = this.owner()
    return !this.disposed && run === this.revision && next.allowed && next.api === scope.api
      && next.lessonId === scope.lessonId && next.userId === scope.userId && next.attachmentId === scope.attachmentId
      && next.file === scope.file && next.ownerKey === scope.ownerKey
  }
  async load(): Promise<void> {
    const run = ++this.revision; const scope = this.owner()
    this.release(); this.publish(empty())
    if (this.disposed || !scope.allowed || !scope.api || scope.lessonId === null || scope.userId === null || (!scope.file && !scope.attachmentId)) return
    this.publish({...empty(),loading:true})
    try {
      const blob = scope.file ?? await scope.api.downloadAttachment(scope.lessonId, scope.userId)
      if (!this.current(run,scope)) return
      if (blob.size > PDF_PREVIEW_MAX_BYTES) throw new Error('Файл слишком большой для предпросмотра.')
      const header = new Uint8Array(await blob.slice(0,8).arrayBuffer())
      const signatures: Record<string, readonly number[]> = {'image/png':[137,80,78,71,13,10,26,10],'image/jpeg':[255,216,255],'application/pdf':[37,80,68,70,45]}
      if (!this.current(run,scope)) return
      if (!signatures[blob.type]?.every((byte,index)=>header[index]===byte)) throw new Error('Файл повреждён или имеет неподдерживаемый тип. Доступны JPEG, PNG и PDF.')
      const url = this.urls.createObjectURL(blob)
      if (!this.current(run,scope)) { this.urls.revokeObjectURL(url); return }
      this.url = url; this.readyOwner = scope; this.publish({url,mime:blob.type,loading:false,error:null})
    } catch (cause) {
      if (!this.current(run,scope)) return
      this.publish({...empty(),error:cause instanceof StaleSessionGenerationError ? null : cause instanceof Error ? cause.message : 'Не удалось показать вложение.'})
    }
  }
  openUrl(): string | null {
    if (!this.url || !this.readyOwner) return null
    if (!this.current(this.revision,this.readyOwner)) { this.release(); this.publish(empty()); return null }
    return this.url
  }
  dispose(): void { this.disposed = true; this.revision += 1; this.release() }
}
