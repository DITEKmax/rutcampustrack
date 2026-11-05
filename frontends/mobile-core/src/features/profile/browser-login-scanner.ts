/** Camera pixels and decoded capabilities remain in memory for this scan only. */
export interface BrowserQrDecoder {
  detect(video: HTMLVideoElement): Promise<string | null>
  dispose(): void
}
interface NativeQrDetector {
  detect(video: HTMLVideoElement): Promise<readonly { rawValue: string }[]>
}
interface NativeQrConstructor {
  new (options: { formats: string[] }): NativeQrDetector
  getSupportedFormats?: () => Promise<readonly string[]>
}

export async function createBrowserQrDecoder(): Promise<BrowserQrDecoder> {
  const Native = (globalThis as typeof globalThis & { BarcodeDetector?: NativeQrConstructor })
    .BarcodeDetector
  if (Native) {
    try {
      if (!Native.getSupportedFormats || (await Native.getSupportedFormats()).includes('qr_code')) {
        const detector = new Native({ formats: ['qr_code'] })
        return {
          async detect(video) {
            if (video.readyState < 2) return null
            return (await detector.detect(video)).find((item) => item.rawValue)?.rawValue ?? null
          },
          dispose() {},
        }
      }
    } catch {
      // Unsupported native implementation: use the bundled decoder, including on Safari.
    }
  }
  const { default: jsQR } = await import('jsqr')
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d', { willReadFrequently: true })
  if (!context) throw new Error('QR decoder unavailable')
  return {
    async detect(video) {
      if (video.readyState < 2 || !video.videoWidth || !video.videoHeight) return null
      const scale = Math.min(1, 960 / Math.max(video.videoWidth, video.videoHeight))
      canvas.width = Math.max(1, Math.round(video.videoWidth * scale))
      canvas.height = Math.max(1, Math.round(video.videoHeight * scale))
      context.drawImage(video, 0, 0, canvas.width, canvas.height)
      const pixels = context.getImageData(0, 0, canvas.width, canvas.height)
      try {
        return (
          jsQR(pixels.data, pixels.width, pixels.height, { inversionAttempts: 'attemptBoth' })
            ?.data ?? null
        )
      } finally {
        pixels.data.fill(0)
        context.clearRect(0, 0, canvas.width, canvas.height)
      }
    },
    dispose() {
      canvas.width = 0
      canvas.height = 0
    },
  }
}

export type CameraState = 'idle' | 'starting' | 'scanning'
export interface BrowserCameraRuntime {
  allowed(): boolean
  acquire(): Promise<MediaStream>
  decoder(): Promise<BrowserQrDecoder>
  video(): Promise<HTMLVideoElement | null>
  state(value: CameraState): void
  scanned(value: string): Promise<void>
  failed(error: unknown): void
}
/** One acquisition/decoder loop, fenced before every asynchronous continuation. */
export function createBrowserLoginCamera(runtime: BrowserCameraRuntime) {
  let revision = 0
  let stream: MediaStream | null = null
  let decoder: BrowserQrDecoder | null = null
  let video: HTMLVideoElement | null = null
  let timer: ReturnType<typeof setTimeout> | undefined
  let state: CameraState = 'idle'
  function setState(value: CameraState): void {
    state = value
    runtime.state(value)
  }
  function stop(): void {
    revision++
    clearTimeout(timer)
    timer = undefined
    stream?.getTracks().forEach((track) => track.stop())
    stream = null
    decoder?.dispose()
    decoder = null
    if (video) video.srcObject = null
    video = null
    setState('idle')
  }
  function current(request: number): boolean {
    if (request !== revision) return false
    if (!runtime.allowed()) {
      stop()
      return false
    }
    return true
  }
  async function frame(
    request: number,
    activeDecoder: BrowserQrDecoder,
    activeVideo: HTMLVideoElement,
  ): Promise<void> {
    if (!current(request)) return
    try {
      const value = await activeDecoder.detect(activeVideo)
      if (!current(request)) return
      if (value) {
        stop()
        await runtime.scanned(value)
        return
      }
      timer = setTimeout(() => void frame(request, activeDecoder, activeVideo), 120)
    } catch (error) {
      if (!current(request)) return
      stop()
      runtime.failed(error)
    }
  }
  async function start(): Promise<void> {
    if (state !== 'idle' || !runtime.allowed()) return
    stop()
    const request = revision
    setState('starting')
    try {
      const created = await runtime.decoder()
      if (!current(request)) {
        created.dispose()
        return
      }
      decoder = created
      const acquired = await runtime.acquire()
      if (!current(request)) {
        acquired.getTracks().forEach((track) => track.stop())
        return
      }
      stream = acquired
      setState('scanning')
      const target = await runtime.video()
      if (!current(request)) return
      if (!target) throw new Error('Camera view unavailable')
      video = target
      target.srcObject = acquired
      await target.play()
      if (!current(request)) return
      void frame(request, created, target)
    } catch (error) {
      if (!current(request)) return
      stop()
      runtime.failed(error)
    }
  }
  return { start, stop }
}
