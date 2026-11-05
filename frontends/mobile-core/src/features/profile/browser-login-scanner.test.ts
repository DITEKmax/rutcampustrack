import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createBrowserLoginCamera,
  createBrowserQrDecoder,
  type BrowserCameraRuntime,
} from './browser-login-scanner'
import { parseBrowserLoginProof } from './browser-login-approval'
const decodePixels = vi.hoisted(() => vi.fn())
vi.mock('jsqr', () => ({ default: decodePixels }))
afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
  vi.clearAllMocks()
})
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
function fixture() {
  let allowed = true
  const track = { stop: vi.fn() }
  const stream = { getTracks: () => [track] } as unknown as MediaStream
  const video = {
    srcObject: null,
    play: vi.fn().mockResolvedValue(undefined),
  } as unknown as HTMLVideoElement
  const decoder = {
    detect: vi.fn<() => Promise<string | null>>().mockResolvedValue(null),
    dispose: vi.fn(),
  }
  const runtime = {
    allowed: () => allowed,
    acquire: vi.fn().mockResolvedValue(stream),
    decoder: vi.fn().mockResolvedValue(decoder),
    video: vi.fn().mockResolvedValue(video),
    state: vi.fn(),
    scanned: vi.fn().mockResolvedValue(undefined),
    failed: vi.fn(),
  } satisfies BrowserCameraRuntime
  return {
    runtime,
    track,
    stream,
    video,
    decoder,
    revoke: () => {
      allowed = false
    },
    camera: createBrowserLoginCamera(runtime),
  }
}
async function settle() {
  for (let i = 0; i < 8; i++) await Promise.resolve()
}
describe('camera lifecycle without camera hardware', () => {
  it('starts only on demand and prevents parallel starts; closes tracks before handing off a scan', async () => {
    const f = fixture()
    expect(f.runtime.acquire).not.toHaveBeenCalled()
    f.decoder.detect.mockResolvedValueOnce('scanned LOGIN proof')
    const first = f.camera.start()
    await f.camera.start()
    await first
    await settle()
    expect(f.runtime.acquire).toHaveBeenCalledOnce()
    expect(f.runtime.scanned).toHaveBeenCalledWith('scanned LOGIN proof')
    expect(f.track.stop).toHaveBeenCalledOnce()
    expect(f.decoder.dispose).toHaveBeenCalledOnce()
    expect(f.video.srcObject).toBeNull()
  })
  it('disposes a late decoder without asking for media after navigation', async () => {
    const f = fixture()
    const pending = deferred<typeof f.decoder>()
    f.runtime.decoder.mockReturnValue(pending.promise)
    const start = f.camera.start()
    f.camera.stop()
    pending.resolve(f.decoder)
    await start
    expect(f.decoder.dispose).toHaveBeenCalledOnce()
    expect(f.runtime.acquire).not.toHaveBeenCalled()
  })
  it.each(['stop', 'scope'] as const)(
    'stops late acquisition after %s without attaching an old stream',
    async (reason) => {
      const f = fixture()
      const pending = deferred<MediaStream>()
      f.runtime.acquire.mockReturnValue(pending.promise)
      const start = f.camera.start()
      await settle()
      if (reason === 'stop') f.camera.stop()
      else f.revoke()
      pending.resolve(f.stream)
      await start
      expect(f.track.stop).toHaveBeenCalledOnce()
      expect(f.decoder.dispose).toHaveBeenCalledOnce()
      expect(f.runtime.video).not.toHaveBeenCalled()
      expect(f.runtime.scanned).not.toHaveBeenCalled()
    },
  )
  it('drops a late decoded capability and clears the loop after stop', async () => {
    vi.useFakeTimers()
    const f = fixture()
    const pending = deferred<string | null>()
    f.decoder.detect.mockReturnValue(pending.promise)
    await f.camera.start()
    f.camera.stop()
    pending.resolve('old LOGIN proof')
    await settle()
    expect(f.runtime.scanned).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('stops on lost online/owner scope before accepting a decoded value', async () => {
    const f = fixture()
    const pending = deferred<string | null>()
    f.decoder.detect.mockReturnValue(pending.promise)
    await f.camera.start()
    f.revoke()
    pending.resolve('old LOGIN proof')
    await settle()
    expect(f.track.stop).toHaveBeenCalledOnce()
    expect(f.runtime.scanned).not.toHaveBeenCalled()
  })
  it('handles denied media honestly and permits a user retry', async () => {
    const f = fixture()
    const denied = new Error('denied')
    denied.name = 'NotAllowedError'
    f.runtime.acquire.mockRejectedValueOnce(denied)
    await f.camera.start()
    expect(f.runtime.failed).toHaveBeenCalledWith(denied)
    expect(f.decoder.dispose).toHaveBeenCalledOnce()
    await f.camera.start()
    expect(f.runtime.acquire).toHaveBeenCalledTimes(2)
    f.camera.stop()
  })
  it('releases the stream when decoding fails', async () => {
    const f = fixture()
    f.decoder.detect.mockRejectedValue(new Error('decoder unavailable'))
    await f.camera.start()
    await settle()
    expect(f.track.stop).toHaveBeenCalledOnce()
    expect(f.runtime.failed).toHaveBeenCalledOnce()
  })
})
describe('decoder selection and pixel privacy', () => {
  it.each(['absent', 'unsupported', 'throws'] as const)(
    'uses bundled jsQR when native support is %s',
    async (native) => {
      if (native === 'absent') vi.stubGlobal('BarcodeDetector', undefined)
      else
        vi.stubGlobal(
          'BarcodeDetector',
          class {
            static async getSupportedFormats() {
              if (native === 'throws') throw new Error('unsupported')
              return ['aztec']
            }
          },
        )
      const pixels = new Uint8ClampedArray(16).fill(255)
      const context = {
        drawImage: vi.fn(),
        getImageData: () => ({ data: pixels, width: 2, height: 2 }),
        clearRect: vi.fn(),
      }
      const canvas = { width: 0, height: 0, getContext: () => context }
      vi.stubGlobal('document', { createElement: () => canvas })
      decodePixels.mockReturnValue({ data: 'decoder result' })
      const decoder = await createBrowserQrDecoder()
      expect(
        await decoder.detect({ readyState: 2, videoWidth: 2, videoHeight: 2 } as HTMLVideoElement),
      ).toBe('decoder result')
      expect(decodePixels).toHaveBeenCalledWith(pixels, 2, 2, { inversionAttempts: 'attemptBoth' })
      expect([...pixels]).toEqual(Array(16).fill(0))
      decoder.dispose()
      expect(canvas.width).toBe(0)
      expect(canvas.height).toBe(0)
    },
  )
})
describe('LOGIN proof boundary', () => {
  const proof = {
    version: 1,
    purpose: 'LOGIN',
    challengeId: '00000000-0000-4000-8000-000000000042',
    approvalToken: 'a'.repeat(43),
  }
  it.each([
    '123456',
    JSON.stringify({ ...proof, purpose: 'DEVICE_LINK' }),
    JSON.stringify({ ...proof, extra: true }),
    JSON.stringify({ ...proof, approvalToken: '123456' }),
    'x'.repeat(1025),
  ])('rejects raw OTP, foreign, or malformed scan', (input) => {
    expect(() => parseBrowserLoginProof(input)).toThrow()
  })
})
