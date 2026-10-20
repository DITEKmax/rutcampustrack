import { describe, expect, it, vi } from 'vitest'
import { PDF_PREVIEW_MAX_BYTES, readPdfPreviewBytes } from './attachment-pdf-bytes'
describe('bounded PDF preview bytes', () => {
  it('rejects a declared oversized response without reading its stream or full buffer', async () => {
    const stream = new ReadableStream<Uint8Array>()
    const getReader = vi.spyOn(stream, 'getReader')
    const cancel = vi.spyOn(stream, 'cancel')
    const response = new Response(stream, {
      headers: { 'Content-Length': String(PDF_PREVIEW_MAX_BYTES + 1) },
    })
    const arrayBuffer = vi.spyOn(response, 'arrayBuffer')
    await expect(readPdfPreviewBytes(response)).rejects.toThrow(Error)
    expect(getReader).not.toHaveBeenCalled()
    expect(arrayBuffer).not.toHaveBeenCalled()
    expect(cancel).toHaveBeenCalled()
  })
  it('bounds an unknown-length stream and cancels on its first excess chunk', async () => {
    const cancel = vi.fn()
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new Uint8Array(PDF_PREVIEW_MAX_BYTES))
        controller.enqueue(new Uint8Array([1]))
      },
      cancel,
    })
    const response = new Response(stream)
    const arrayBuffer = vi.spyOn(response, 'arrayBuffer')
    await expect(readPdfPreviewBytes(response)).rejects.toThrow(Error)
    expect(cancel).toHaveBeenCalled()
    expect(arrayBuffer).not.toHaveBeenCalled()
  })
  it('returns exact small first-page source bytes without a full-buffer response read', async () => {
    const input = new TextEncoder().encode('%PDF-1.7 source')
    const response = new Response(input)
    const arrayBuffer = vi.spyOn(response, 'arrayBuffer')
    expect(await readPdfPreviewBytes(response)).toEqual(input)
    expect(arrayBuffer).not.toHaveBeenCalled()
  })
  it('cancels before reading when the owning preview is already aborted', async () => {
    const abort = new AbortController()
    abort.abort()
    const response = new Response(new Uint8Array([1]))
    await expect(readPdfPreviewBytes(response, abort.signal)).rejects.toThrow()
  })
})
