import { afterEach, expect, it, vi } from 'vitest'
import { createBrowserQrDecoder } from './browser-login-scanner'
import { parseBrowserLoginProof } from './browser-login-approval'
import fixture from './browser-login-qr.fixture.json'

afterEach(() => vi.unstubAllGlobals())
it('decodes a standard LOGIN QR matrix through the real bundled jsQR fallback and strict parser', async () => {
  // Synthetic test capability only. The existing WEB encoder qrcode@1.5.4 created the fixture.
  // Canvas receives a deterministic RGBA image; no media/browser/hardware is requested.
  const quietZone = 4
  const scale = 6
  const size = (fixture.matrix.length + quietZone * 2) * scale
  const pixels = new Uint8ClampedArray(size * size * 4).fill(255)
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const row = Math.floor(y / scale) - quietZone
      const column = Math.floor(x / scale) - quietZone
      if (fixture.matrix[row]?.[column]) {
        const offset = (y * size + x) * 4
        pixels[offset] = pixels[offset + 1] = pixels[offset + 2] = 0
      }
    }
  }
  const context = {
    drawImage() {},
    getImageData: () => ({ data: pixels, width: size, height: size }),
    clearRect() {},
  }
  const canvas = { width: 0, height: 0, getContext: () => context }
  vi.stubGlobal('BarcodeDetector', undefined)
  vi.stubGlobal('document', { createElement: () => canvas })
  const decoder = await createBrowserQrDecoder()
  const result = await decoder.detect({
    readyState: 2,
    videoWidth: size,
    videoHeight: size,
  } as HTMLVideoElement)
  expect(result).toBe(fixture.payload)
  expect(parseBrowserLoginProof(result!)).toEqual({
    purpose: 'LOGIN',
    challengeId: '00000000-0000-4000-8000-000000000042',
    approvalToken: 'a'.repeat(43),
  })
  expect(pixels.every((value) => value === 0)).toBe(true)
  decoder.dispose()
  expect(canvas.width).toBe(0)
  expect(canvas.height).toBe(0)
})
