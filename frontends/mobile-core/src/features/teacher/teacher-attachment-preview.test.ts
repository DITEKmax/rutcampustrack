import { afterEach, describe, expect, it, vi } from 'vitest'
import { TeacherApi } from './teacher-client'
import {
  TeacherAttachmentPreview,
  type TeacherAttachmentOwner,
  type TeacherAttachmentState,
} from './teacher-attachment-preview'
const png = () =>
  new Blob([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10, 0])], { type: 'image/png' })
function owner(download = async () => png()): TeacherAttachmentOwner {
  return {
    api: { downloadAttachment: download },
    requestId: 'request-a',
    attachment: {
      id: 'file-a',
      fileName: 'Справка.png',
      contentType: 'image/png',
      size: 9,
      uploadedAt: null,
      expiresAt: null,
    },
  }
}
function harness(initial = owner()) {
  let current = initial
  let state: TeacherAttachmentState = { status: 'idle', url: null, mime: null, error: null }
  const created: string[] = []
  const revoked: string[] = []
  const preview = new TeacherAttachmentPreview(
    () => current,
    (next) => {
      state = next
    },
    {
      createObjectURL: () => {
        const url = 'blob:teacher-' + created.length
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
    created,
    revoked,
    get state() {
      return state
    },
    setOwner(value: TeacherAttachmentOwner) {
      current = value
    },
  }
}
afterEach(() => {
  vi.useRealTimers()
})
describe('teacher authenticated attachment ownership', () => {
  it('uses the exact private GET without caching and preserves a typed attachment denial', async () => {
    const problem = { code: 'ATTACHMENT_NOT_FOUND', detail: 'Документ недоступен.' }
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(png()))
      .mockResolvedValueOnce(Response.json(problem, { status: 404 }))
    const api = new TeacherApi({ accessToken: () => 'teacher-session', fetcher })
    const result = await api.downloadManualExcuseAttachment(11, 22, 'file/a')
    expect(result.type).toBe('image/png')
    expect(result.size).toBe(png().size)
    const [path, init] = fetcher.mock.calls[0]!
    expect(path).toBe('/api/v1/teacher/lessons/11/students/22/excuse/attachments/file%2Fa')
    expect(init?.method ?? 'GET').toBe('GET')
    expect(init?.cache).toBe('no-store')
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer teacher-session')
    await expect(api.downloadManualExcuseAttachment(11, 22, 'missing')).rejects.toMatchObject({
      problem,
      response: { status: 404 },
    })
    await expect(api.downloadManualExcuseAttachment(11.5, 22, 'file/a')).rejects.toThrow()
    expect(fetcher).toHaveBeenCalledTimes(2)
  })
  it('uses the authorized manual route and revokes its blob when lesson or student ownership changes', async () => {
    const downloadAttachment = vi.fn(async () => png())
    const downloadManualExcuseAttachment = vi.fn(async () => png())
    const initial = {
      ...owner(),
      requestId: null,
      attachment: { ...owner().attachment, expiresAt: '2030-01-01T00:00:00Z' },
      lessonId: 11,
      studentId: 22,
      api: { downloadAttachment, downloadManualExcuseAttachment },
    }
    const h = harness(initial)
    await h.preview.load()
    expect(downloadManualExcuseAttachment).toHaveBeenCalledWith(11, 22, 'file-a')
    expect(downloadAttachment).not.toHaveBeenCalled()
    expect(h.preview.openUrl()).toBe('blob:teacher-0')
    h.setOwner({ ...initial, studentId: 23 })
    expect(h.preview.openUrl()).toBeNull()
    expect(h.revoked).toEqual(h.created)
    await h.preview.load()
    h.setOwner({ ...initial, lessonId: 12, studentId: 23 })
    expect(h.preview.openUrl()).toBeNull()
    expect(h.revoked).toEqual(h.created)
  })
  it('rejects late manual bytes after changing their lesson context', async () => {
    let finish!: (value: Blob) => void
    const initial = {
      ...owner(),
      requestId: null,
      attachment: { ...owner().attachment, expiresAt: '2030-01-01T00:00:00Z' },
      lessonId: 11,
      studentId: 22,
      api: {
        downloadAttachment: async () => png(),
        downloadManualExcuseAttachment: () =>
          new Promise<Blob>((resolve) => {
            finish = resolve
          }),
      },
    }
    const h = harness(initial)
    const loading = h.preview.load()
    h.setOwner({ ...initial, lessonId: 12 })
    finish(png())
    await loading
    expect(h.created).toEqual([])
    expect(h.preview.openUrl()).toBeNull()
  })
  it('does not download or invent a lifetime for old manual metadata without its actual expiry', async () => {
    const download = vi.fn(async () => png())
    const h = harness({
      ...owner(),
      requestId: null,
      lessonId: 11,
      studentId: 22,
      api: { downloadAttachment: download, downloadManualExcuseAttachment: download },
    })
    await h.preview.load()
    expect(download).not.toHaveBeenCalled()
    expect(h.state.error).toContain('Сервер не предоставил срок хранения')
    expect(h.created).toEqual([])
  })
  it('discards manual bytes arriving after their deadline and expires an already owned manual blob', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-05T09:00:00Z'))
    let finish!: (value: Blob) => void
    const initial = {
      ...owner(),
      requestId: null,
      lessonId: 11,
      studentId: 22,
      attachment: { ...owner().attachment, expiresAt: '2026-11-05T09:00:01Z' },
      api: {
        downloadAttachment: async () => png(),
        downloadManualExcuseAttachment: () =>
          new Promise<Blob>((resolve) => {
            finish = resolve
          }),
      },
    }
    const h = harness(initial)
    const loading = h.preview.load()
    vi.setSystemTime(new Date('2026-11-05T09:00:02Z'))
    finish(png())
    await loading
    expect(h.created).toEqual([])
    expect(h.state.error).toContain('истёк')
    h.setOwner({
      ...initial,
      attachment: { ...initial.attachment, expiresAt: '2026-11-05T09:00:03Z' },
      api: {
        downloadAttachment: async () => png(),
        downloadManualExcuseAttachment: async () => png(),
      },
    })
    await h.preview.load()
    expect(h.preview.openUrl()).toBe('blob:teacher-0')
    await vi.advanceTimersByTimeAsync(1001)
    expect(h.state.url).toBeNull()
    expect(h.revoked).toEqual(h.created)
  })
  it('opens validated bytes and revokes on request change/unmount', async () => {
    const pdf = new Blob(['%PDF-1.7'], { type: 'application/pdf' })
    const h = harness(owner(async () => pdf))
    await h.preview.load()
    expect(h.preview.openUrl()).toBe('blob:teacher-0')
    expect(h.preview.openBlob('blob:teacher-0')).toBe(pdf)
    expect(h.preview.openBlob('blob:other')).toBeNull()
    h.setOwner({ ...owner(), requestId: 'request-b' })
    expect(h.preview.openBlob('blob:teacher-0')).toBeNull()
    expect(h.revoked).toEqual(h.created)
    await h.preview.load()
    expect(h.preview.openBlob('blob:teacher-0')).toBeNull()
    h.preview.dispose()
    expect(h.preview.openBlob('blob:teacher-1')).toBeNull()
    expect(h.revoked).toEqual(h.created)
  })
  it.each(['session', 'request', 'attachment', 'dispose'] as const)(
    'does not publish late bytes after %s changes',
    async (change) => {
      let finish!: (blob: Blob) => void
      const initial = owner(
        () =>
          new Promise((resolve) => {
            finish = resolve
          }),
      )
      const h = harness(initial)
      const run = h.preview.load()
      if (change === 'dispose') h.preview.dispose()
      else
        h.setOwner({
          ...initial,
          ...(change === 'session'
            ? { api: owner().api }
            : change === 'request'
              ? { requestId: 'request-b' }
              : { attachment: { ...initial.attachment, id: 'file-b' } }),
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
    new Blob([], { type: 'application/pdf' }),
  ])('rejects unsupported/disguised/empty bytes', async (blob) => {
    const h = harness(owner(async () => blob))
    await h.preview.load()
    expect(h.state.status).toBe('error')
    expect(h.created).toEqual([])
  })
  it('does not allocate a URL when expiry happens during download', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-08T10:00:00Z'))
    let finish!: (blob: Blob) => void
    const initial = owner(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const h = harness({
      ...initial,
      attachment: { ...initial.attachment, expiresAt: '2026-10-08T10:00:01Z' },
    })
    const run = h.preview.load()
    vi.setSystemTime(new Date('2026-10-08T10:00:02Z'))
    finish(png())
    await run
    expect(h.state.error).toContain('истёк')
    expect(h.created).toEqual([])
  })
  it('revokes a ready URL on expiry and handles long timer ceiling', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-08T10:00:00Z'))
    const initial = owner()
    const h = harness({
      ...initial,
      attachment: { ...initial.attachment, expiresAt: '2026-12-08T10:00:00Z' },
    })
    await h.preview.load()
    await vi.advanceTimersByTimeAsync(2_147_483_647)
    expect(h.state.status).toBe('ready')
    await vi.advanceTimersByTimeAsync(62 * 86_400_000)
    expect(h.state.status).toBe('error')
    expect(h.revoked).toEqual(h.created)
    expect(h.preview.openUrl()).toBeNull()
    expect(h.state.status).toBe('error')
  })
  it('retains safe error/retry and ignores stale image errors', async () => {
    let fail = true
    const h = harness(
      owner(async () => {
        if (fail) throw new Error('Нет доступа к файлу')
        return png()
      }),
    )
    await h.preview.load()
    expect(h.state.error).toBe('Нет доступа к файлу')
    fail = false
    await h.preview.load()
    h.preview.imageFailed('blob:old')
    expect(h.state.status).toBe('ready')
    h.preview.imageFailed(h.state.url!)
    expect(h.state.status).toBe('error')
    expect(h.revoked).toEqual(h.created)
  })
})
