import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { HeadmanRequestsApi, HeadmanRequestAttachment } from '../headman-requests/headman-requests-client'
export interface ServerAttachmentOwner {
  readonly api: Pick<HeadmanRequestsApi, 'downloadAttachment'> | null
  readonly requestId: string
  readonly attachment: HeadmanRequestAttachment
  readonly ownerKey: string
  readonly allowed: boolean
  readonly offline: boolean
}
export type ServerAttachmentPreviewState =
  | { readonly status: 'idle' | 'loading'; readonly url: null; readonly mime: null; readonly error: null }
  | { readonly status: 'error'; readonly url: null; readonly mime: null; readonly error: string }
  | { readonly status: 'ready'; readonly url: string; readonly mime: 'image/png' | 'image/jpeg' | 'application/pdf'; readonly error: null }
type ObjectUrlApi = Pick<typeof URL, 'createObjectURL' | 'revokeObjectURL'>
const idle = (): ServerAttachmentPreviewState => ({ status: 'idle', url: null, mime: null, error: null })
function expired(owner: ServerAttachmentOwner): boolean { return owner.attachment.expiresAt !== null && (!Number.isFinite(Date.parse(owner.attachment.expiresAt)) || Date.parse(owner.attachment.expiresAt) <= Date.now()) }
async function supportedBlob(blob: Blob): Promise<ServerAttachmentPreviewState['mime']> {
  if (!blob.size || !['image/jpeg','image/png','application/pdf'].includes(blob.type)) return null
  const header = new Uint8Array(await blob.slice(0, 8).arrayBuffer())
  const matches = (signature: number[]) => signature.every((byte, index) => header[index] === byte)
  if (blob.type === 'image/png' && matches([137,80,78,71,13,10,26,10])) return 'image/png'
  if (blob.type === 'image/jpeg' && matches([255,216,255])) return 'image/jpeg'
  if (blob.type === 'application/pdf' && matches([37,80,68,70,45])) return 'application/pdf'
  return null
}
/** Owns authenticated resources; late responses never publish into a different owner. */
export class ServerAttachmentPreview {
  private generation = 0
  private disposed = false
  private ownedUrl: string | null = null
  private readyOwner: ServerAttachmentOwner | null = null
  private timer: ReturnType<typeof setTimeout> | undefined
  private state: ServerAttachmentPreviewState = idle()
  constructor(private readonly owner: () => ServerAttachmentOwner, private readonly publish: (state: ServerAttachmentPreviewState) => void, private readonly urls: ObjectUrlApi = URL) {}
  private set(state: ServerAttachmentPreviewState): void { this.state = state; this.publish(state) }
  private release(): void { clearTimeout(this.timer); this.timer = undefined; if (this.ownedUrl) this.urls.revokeObjectURL(this.ownedUrl); this.ownedUrl = null; this.readyOwner = null }
  private fail(message: string): void { this.release(); this.set({ status: 'error', url: null, mime: null, error: message }) }
  private owns(run: number, scope: ServerAttachmentOwner): boolean {
    const next = this.owner()
    return !this.disposed && run === this.generation && scope.api === next.api && scope.requestId === next.requestId
      && scope.ownerKey === next.ownerKey && scope.attachment.id === next.attachment.id && scope.attachment.expiresAt === next.attachment.expiresAt
      && scope.attachment.state === next.attachment.state && next.allowed && !next.offline
  }
  private armExpiry(run: number, scope: ServerAttachmentOwner): void {
    if (scope.attachment.expiresAt === null) return
    const delay = Date.parse(scope.attachment.expiresAt) - Date.now()
    if (delay <= 0) { if (this.owns(run, scope)) this.fail('Срок хранения файла истёк.'); return }
    this.timer = setTimeout(() => { if (!this.owns(run, scope)) return; if (expired(scope)) this.fail('Срок хранения файла истёк.'); else this.armExpiry(run, scope) }, Math.min(2_147_483_647, delay))
  }
  async load(): Promise<void> {
    const scope = this.owner(); const run = ++this.generation
    this.release(); this.set(idle())
    if (this.disposed || !scope.api || !scope.allowed || scope.offline) return
    if (expired(scope)) { this.fail('Срок хранения файла истёк.'); return }
    if (scope.attachment.state !== 'AVAILABLE') { this.fail('Файл сейчас недоступен.'); return }
    this.set({ status: 'loading', url: null, mime: null, error: null })
    try {
      const blob = await scope.api.downloadAttachment(scope.requestId, scope.attachment.id)
      if (!this.owns(run, scope)) return
      if (expired(scope)) { this.fail('Срок хранения файла истёк.'); return }
      const mime = await supportedBlob(blob)
      if (!this.owns(run, scope)) return
      if (expired(scope)) { this.fail('Срок хранения файла истёк.'); return }
      if (!mime) { this.fail('Файл повреждён или имеет неподдерживаемый тип. Доступны JPEG, PNG и PDF.'); return }
      const url = this.urls.createObjectURL(blob)
      if (!this.owns(run, scope)) { this.urls.revokeObjectURL(url); return }
      this.ownedUrl = url; this.readyOwner = scope
      this.set({ status: 'ready', url, mime, error: null })
      this.armExpiry(run, scope)
    } catch (cause) {
      if (!this.owns(run, scope)) return
      if (expired(scope)) { this.fail('Срок хранения файла истёк.'); return }
      if (cause instanceof StaleSessionGenerationError) { this.set(idle()); return }
      this.fail(cause instanceof Error ? cause.message : 'Не удалось открыть вложение.')
    }
  }
  imageFailed(url: string): void { if (this.state.status === 'ready' && this.state.url === url) this.fail('Изображение повреждено. Не удалось показать вложение.') }
  openUrl(): string | null {
    if (this.state.status !== 'ready') return null
    if (!this.readyOwner || !this.owns(this.generation, this.readyOwner)) { this.release(); this.set(idle()); return null }
    if (!this.owner().allowed || this.owner().offline) { this.release(); this.set(idle()); return null }
    if (expired(this.owner())) { this.fail('Срок хранения файла истёк.'); return null }
    return this.state.url
  }
  dispose(): void { this.disposed = true; this.generation += 1; this.release(); this.set(idle()) }
}
