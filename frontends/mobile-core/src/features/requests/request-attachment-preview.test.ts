import { describe, expect, it, vi } from 'vitest'
import { RequestAttachmentPreviewUrls } from './request-attachment-preview'
import type { RequestFileRef } from './types'

function requestFile(id: string, name: string, type: string, file: File): RequestFileRef {
  return { id, name, size: file.size, type, file }
}

describe('request attachment preview URL lifecycle', () => {
  it('reuses selected URLs and releases removed, replaced, and unmounted files', () => {
    const createObjectURL = vi.fn((file: Blob) => `blob:${file.size}:${createObjectURL.mock.calls.length}`)
    const revokeObjectURL = vi.fn()
    const urls = new RequestAttachmentPreviewUrls({ createObjectURL, revokeObjectURL })
    const first = new File(['first'], 'proof.png', { type: 'image/png' })
    const replacement = new File(['replacement'], 'proof.png', { type: 'image/png' })
    const kept = new File(['pdf'], 'receipt.pdf', { type: 'application/pdf' })

    const initial = urls.sync([
      requestFile('image', 'proof.png', 'image/png', first),
      requestFile('pdf', 'receipt.pdf', 'application/pdf', kept),
    ])
    expect(initial.urls.size).toBe(2)
    expect(createObjectURL).toHaveBeenCalledTimes(2)

    const afterReplacement = urls.sync([
      requestFile('image', 'proof.png', 'image/png', replacement),
      requestFile('pdf', 'receipt.pdf', 'application/pdf', kept),
    ])
    expect(revokeObjectURL).toHaveBeenCalledWith(initial.urls.get('image'))
    expect(afterReplacement.urls.get('pdf')).toBe(initial.urls.get('pdf'))
    expect(afterReplacement.urls.get('image')).not.toBe(initial.urls.get('image'))
    expect(createObjectURL).toHaveBeenCalledTimes(3)

    urls.sync([requestFile('image', 'proof.png', 'image/png', replacement)])
    expect(revokeObjectURL).toHaveBeenCalledWith(initial.urls.get('pdf'))

    const currentImageUrl = afterReplacement.urls.get('image')
    urls.clear()
    expect(revokeObjectURL).toHaveBeenCalledWith(currentImageUrl)
    expect(revokeObjectURL).toHaveBeenCalledTimes(3)
  })

  it('does not create executable previews for unsupported MIME types and reports URL failures', () => {
    const createObjectURL = vi.fn(() => {
      throw new Error('object URLs unavailable')
    })
    const revokeObjectURL = vi.fn()
    const urls = new RequestAttachmentPreviewUrls({ createObjectURL, revokeObjectURL })
    const svg = new File(['<svg/>'], 'image.svg', { type: 'image/svg+xml' })
    const image = new File(['png'], 'image.png', { type: 'image/png' })

    const snapshot = urls.sync([
      requestFile('svg', 'image.svg', 'image/svg+xml', svg),
      requestFile('image', 'image.png', 'image/png', image),
    ])

    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(snapshot.urls.size).toBe(0)
    expect(snapshot.unavailableIds).toEqual(new Set(['image']))
  })
})
