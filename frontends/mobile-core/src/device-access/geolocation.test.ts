import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  acquireGeolocation,
  browserGeoSource,
  type GeoFailure,
  type GeoSample,
  type GeoSource,
} from './geolocation'
import { createDeviceAccess, commandFromGeoResult, inspectBrowserPermission } from './device-access'
import { CheckinBeforeSendCancelled } from '../domain/checkin'

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})
function fixture(source: GeoSource['source'] = 'BROWSER') {
  let sample!: (value: GeoSample) => void
  let failed!: (value: GeoFailure) => void
  const stop = vi.fn()
  const watch = vi.fn((onSample: typeof sample, onFailed: typeof failed) => {
    sample = onSample
    failed = onFailed
    return stop
  })
  return {
    source: { source, watch },
    watch,
    stop,
    emit: (value: Partial<GeoSample> = {}) =>
      sample({
        latitude: 55.7,
        longitude: 37.6,
        accuracy: 150,
        timestamp: Date.now(),
        receivedAt: Date.now(),
        ...value,
      }),
    fail: (value: GeoFailure) => failed(value),
  }
}
async function settle() {
  for (let i = 0; i < 8; i++) await Promise.resolve()
}

describe('bounded shared geolocation policy', () => {
  it('retains a fresh alternative when the initially more accurate fix ages out before deadline', async () => {
    vi.useFakeTimers()
    const f = fixture()
    const result = acquireGeolocation(f.source)
    f.emit({ accuracy: 100, timestamp: Date.now() - 5_000 })
    await vi.advanceTimersByTimeAsync(15_000)
    f.emit({ accuracy: 200 })
    await vi.advanceTimersByTimeAsync(13_000)
    await expect(result).resolves.toMatchObject({ sample: { accuracy: 200 }, needsReview: true })
  })
  it('refines poor fixes, picks quality rather than proximity and stops one source at a good fix', async () => {
    vi.useFakeTimers()
    const f = fixture()
    const progress = vi.fn()
    const result = acquireGeolocation(f.source, { progress })
    f.emit({ accuracy: 400 })
    f.emit({ accuracy: 120 })
    f.emit({ accuracy: 30 })
    await expect(result).resolves.toMatchObject({
      sample: { accuracy: 30 },
      sampleCount: 3,
      reason: 'QUALITY_REACHED',
      needsReview: false,
    })
    expect(f.watch).toHaveBeenCalledOnce()
    expect(f.stop).toHaveBeenCalledOnce()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('does not silently accept contradictory samples and retains the best quality at deadline', async () => {
    vi.useFakeTimers()
    const f = fixture()
    const result = acquireGeolocation(f.source)
    f.emit({ accuracy: 80 })
    f.emit({ latitude: 56.7, accuracy: 15 })
    await vi.advanceTimersByTimeAsync(28_000)
    await expect(result).resolves.toMatchObject({
      sample: { latitude: 56.7, accuracy: 15 },
      conflicting: true,
      needsReview: true,
      reason: 'BUDGET_EXPIRED',
    })
    expect(f.stop).toHaveBeenCalledOnce()
  })
  it('rejects stale fixes and does not turn native callback time into fix time', async () => {
    vi.useFakeTimers()
    const stale = fixture()
    const result = acquireGeolocation(stale.source)
    stale.emit({ timestamp: Date.now() - 31_000, accuracy: 1 })
    await vi.advanceTimersByTimeAsync(28_000)
    await expect(result).resolves.toMatchObject({ sample: null, sampleCount: 0, needsReview: true })
    const native = fixture('TMA')
    const acquired = acquireGeolocation(native.source)
    native.emit({ timestamp: null, accuracy: 20 })
    const command = commandFromGeoResult(await acquired)
    expect(command.diagnostics).toMatchObject({
      source: 'TMA',
      fixTimestamp: null,
      accuracyMeters: 20,
      sampleCount: 1,
    })
    expect(command.diagnostics?.receivedAt).toEqual(expect.any(String))
  })
  it.each(['PERMISSION_DENIED', 'UNSUPPORTED'] as const)(
    'finishes %s immediately without consuming GPS budget',
    async (reason) => {
      vi.useFakeTimers()
      const f = fixture()
      const result = acquireGeolocation(f.source)
      f.fail(reason)
      await expect(result).resolves.toMatchObject({ reason, duration: 0, sample: null })
      expect(vi.getTimerCount()).toBe(0)
    },
  )
  it('stops the browser watch on abort and ignores callbacks that arrive afterwards', async () => {
    vi.useFakeTimers()
    let sample!: PositionCallback
    const clearWatch = vi.fn()
    const source = browserGeoSource({
      watchPosition: vi.fn((callback) => {
        sample = callback
        return 7
      }),
      clearWatch,
    } as unknown as Geolocation)
    const controller = new AbortController()
    const result = acquireGeolocation(source, { signal: controller.signal })
    const assertion = expect(result).rejects.toMatchObject({ name: 'AbortError' })
    controller.abort()
    sample({
      coords: { latitude: 55, longitude: 37, accuracy: 1 },
      timestamp: Date.now(),
    } as GeolocationPosition)
    await assertion
    expect(clearWatch).toHaveBeenCalledExactlyOnceWith(7)
    expect(vi.getTimerCount()).toBe(0)
  })
})

describe('permission owner and explicit choice before transport', () => {
  it('reacquires instead of returning a browser fix after a delayed Continue', async () => {
    vi.useFakeTimers()
    const f = fixture()
    const owner = createDeviceAccess({ geo: f.source }, null)
    owner.bindOwner('alice', 's1')
    const attempt = owner.acquire()
    let completed = false
    void attempt.then(() => {
      completed = true
    })
    f.emit({ accuracy: 500 })
    await vi.advanceTimersByTimeAsync(28_000)
    expect(owner.state.stage).toBe('review')
    await vi.advanceTimersByTimeAsync(60_000)
    owner.choose('continue')
    await settle()
    expect(completed).toBe(false)
    expect(f.watch).toHaveBeenCalledTimes(2)
    f.emit({ accuracy: 20 })
    await expect(attempt).resolves.toMatchObject({
      diagnostics: { fixTimestamp: new Date(Date.now()).toISOString(), accuracyMeters: 20 },
    })
  })
  it('does not prompt during inspection or restore a system grant from account preferences', async () => {
    const f = fixture()
    const values = new Map<string, string>()
    const storage = {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => {
        values.set(key, value)
      },
    }
    const owner = createDeviceAccess({ geo: f.source, inspect: async () => 'prompt' }, storage)
    owner.bindOwner('alice', 's1')
    await owner.inspect('location')
    expect(f.watch).not.toHaveBeenCalled()
    owner.setEnabled('location', false)
    owner.bindOwner('bob', 's2')
    expect(owner.state.location).toMatchObject({ enabled: true, permission: 'unknown' })
    owner.bindOwner('alice', 's3')
    expect(owner.state.location).toMatchObject({ enabled: false, permission: 'unknown' })
  })
  it('requires a user choice for poor quality and cancels a pending choice on owner change', async () => {
    vi.useFakeTimers()
    const f = fixture()
    const owner = createDeviceAccess({ geo: f.source }, null)
    owner.bindOwner('alice', 's1')
    let settled = false
    const attempt = owner.acquire().then((value) => {
      settled = true
      return value
    })
    const assertion = expect(attempt).rejects.toBeInstanceOf(CheckinBeforeSendCancelled)
    f.emit({ accuracy: 500 })
    await vi.advanceTimersByTimeAsync(28_000)
    expect(owner.state.stage).toBe('review')
    expect(settled).toBe(false)
    owner.bindOwner('bob', 's2')
    owner.choose('continue')
    await assertion
    expect(owner.state.review).toBeNull()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('retries without a POST, then returns the chosen result only on explicit continue', async () => {
    vi.useFakeTimers()
    const f = fixture('TMA')
    const owner = createDeviceAccess({ geo: f.source }, null)
    owner.bindOwner('alice', 's1')
    const attempt = owner.acquire()
    f.emit({ timestamp: null, accuracy: null })
    await vi.advanceTimersByTimeAsync(28_000)
    owner.choose('retry')
    await settle()
    expect(f.watch).toHaveBeenCalledTimes(2)
    f.fail('PERMISSION_DENIED')
    await settle()
    owner.choose('continue')
    await expect(attempt).resolves.toMatchObject({
      geo: { kind: 'UNAVAILABLE', reason: 'PERMISSION_DENIED' },
      diagnostics: {
        source: 'TMA',
        sampleCount: 0,
        completionReason: 'PERMISSION_DENIED',
        fixTimestamp: null,
      },
    })
    expect(owner.state.stage).toBeNull()
  })
  it('turns off active acquisition immediately and never accepts a late fix', async () => {
    vi.useFakeTimers()
    const f = fixture()
    const owner = createDeviceAccess({ geo: f.source }, null)
    owner.bindOwner('alice', 's1')
    const attempt = owner.acquire()
    const assertion = expect(attempt).rejects.toBeInstanceOf(CheckinBeforeSendCancelled)
    owner.setEnabled('location', false)
    f.emit({ accuracy: 1 })
    await assertion
    expect(f.stop).toHaveBeenCalledOnce()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('treats an unsupported permission descriptor as unknown, not denied or granted', async () => {
    vi.stubGlobal('navigator', {
      mediaDevices: { getUserMedia: vi.fn() },
      permissions: { query: vi.fn().mockRejectedValue(new TypeError('camera unsupported')) },
    })
    await expect(inspectBrowserPermission('camera')).resolves.toBe('unknown')
  })
  it('bounds a settings camera probe and stops a late stream without an approval', async () => {
    vi.useFakeTimers()
    let resolve!: (value: MediaStream) => void
    const getUserMedia = vi.fn(
      () =>
        new Promise<MediaStream>((done) => {
          resolve = done
        }),
    )
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } })
    const owner = createDeviceAccess({ geo: fixture().source }, null)
    owner.bindOwner('alice', 's1')
    const probe = owner.probe('camera')
    expect(getUserMedia).toHaveBeenCalledExactlyOnceWith({ video: true, audio: false })
    await vi.advanceTimersByTimeAsync(15_000)
    await probe
    expect(owner.state.camera.permission).toBe('error')
    const stop = vi.fn()
    resolve({ getTracks: () => [{ stop }] } as unknown as MediaStream)
    await settle()
    expect(stop).toHaveBeenCalledOnce()
  })
})
