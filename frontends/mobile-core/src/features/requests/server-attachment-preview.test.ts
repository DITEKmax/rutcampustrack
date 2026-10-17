import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ServerAttachmentPreview,
  type ServerAttachmentOwner,
  type ServerAttachmentPreviewState,
} from './server-attachment-preview'
const png = () =>
  new Blob([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10, 0])], { type: 'image/png' })
function owner(download = async () => png()): ServerAttachmentOwner {
  return {
    api: { downloadAttachment: download },
    requestId: 'ticket-a',
    attachment: {
      id: 'file-a',
      name: 'sample.png',
      contentType: 'image/png',
      size: 9,
      sha256: null,
      state: 'AVAILABLE',
      uploadedAt: null,
      expiresAt: null,
      downloadUrl: null,
    },
    ownerKey: 'account-1/group-2',
    allowed: true,
    offline: false,
  }
}
function harness(initial = owner()) {
  let current = initial
  let state: ServerAttachmentPreviewState = { status: 'idle', url: null, mime: null, error: null }
  const created: string[] = []
  const revoked: string[] = []
  const preview = new ServerAttachmentPreview(
    () => current,
    (next) => {
      state = next
    },
    {
      createObjectURL: () => {
        const url = 'blob:owned-' + created.length
        created.push(url)
        return url
      },
      revokeObjectURL: (url) => {
        revoked.push(url)
      },
    },
  )
  return {
    preview,
    get state() {
      return state
    },
    created,
    revoked,
    setOwner: (next: ServerAttachmentOwner) => {
      current = next
    },
  }
}
afterEach(() => {
  vi.useRealTimers()
})
describe('authenticated server attachment lifecycle', () => {
  it('publishes validated PNG and revokes on context change and teardown', async () => {
    const h = harness()
    await h.preview.load()
    expect(h.state.status).toBe('ready')
    h.setOwner({ ...owner(), requestId: 'ticket-b' })
    expect(h.preview.openUrl()).toBeNull()
    expect(h.revoked).toEqual(h.created)
    await h.preview.load()
    h.preview.dispose()
    expect(h.revoked).toEqual(h.created)
  })
  it.each(['owner', 'permission', 'offline', 'dispose'] as const)(
    'never creates or opens a late URL after %s changes',
    async (change) => {
      let finish!: (value: Blob) => void
      const pending = new Promise<Blob>((resolve) => {
        finish = resolve
      })
      const initial = owner(async () => pending)
      const h = harness(initial)
      const run = h.preview.load()
      if (change === 'dispose') h.preview.dispose()
      else
        h.setOwner({
          ...initial,
          ...(change === 'owner'
            ? { ownerKey: 'other-account' }
            : change === 'permission'
              ? { allowed: false }
              : { offline: true }),
        })
      finish(png())
      await run
      expect(h.created).toEqual([])
      expect(h.preview.openUrl()).toBeNull()
    },
  )
  it.each([
    new Blob(['<html>unsafe</html>'], { type: 'text/html' }),
    new Blob(['<html>disguised</html>'], { type: 'image/png' }),
    new Blob([''], { type: 'application/pdf' }),
  ])('rejects unsupported, disguised, or empty content before allocating URL', async (blob) => {
    const h = harness(owner(async () => blob))
    await h.preview.load()
    expect(h.state.status).toBe('error')
    expect(h.created).toEqual([])
  })
  it('turns a browser decode failure into an owned error and ignores old-image failures', async () => {
    const h = harness()
    await h.preview.load()
    const url = h.state.url!
    h.preview.imageFailed('blob:old-context')
    expect(h.state.status).toBe('ready')
    h.preview.imageFailed(url)
    expect(h.state.status).toBe('error')
    expect(h.revoked).toEqual([url])
  })
  it('reports expiry while an authenticated download is in flight', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-06T00:00:00Z'))
    let finish!: (value: Blob) => void
    const pending = new Promise<Blob>((resolve) => {
      finish = resolve
    })
    const initial = owner(async () => pending)
    const h = harness({
      ...initial,
      attachment: { ...initial.attachment, expiresAt: '2026-10-06T00:00:01Z' },
    })
    const run = h.preview.load()
    vi.setSystemTime(new Date('2026-10-06T00:00:02Z'))
    finish(png())
    await run
    expect(h.state.status).toBe('error')
    expect(h.state.error).toContain('истёк')
    expect(h.created).toEqual([])
  })
  it('reschedules long expiry without revoking a still-valid preview at the timer ceiling', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-06T00:00:00Z'))
    const initial = owner()
    const h = harness({
      ...initial,
      attachment: { ...initial.attachment, expiresAt: '2026-12-06T00:00:00Z' },
    })
    await h.preview.load()
    await vi.advanceTimersByTimeAsync(2_147_483_647)
    expect(h.state.status).toBe('ready')
    expect(h.revoked).toEqual([])
    await vi.advanceTimersByTimeAsync(62 * 86_400_000)
    expect(h.state.status).toBe('error')
    expect(h.revoked).toEqual(h.created)
  })
})
