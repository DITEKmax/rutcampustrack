import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  acquireGeolocation,
  browserGeoSource,
  withinGeofence,
  type GeoFailure,
  type GeoSample,
  type GeoSource,
} from './geolocation'
import { createDeviceAccess, inspectBrowserPermission } from './device-access'
import { CheckinBeforeSendCancelled } from '../domain/checkin'
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})
const policy = { latitude: 0, longitude: 0, radiusMeters: 400 }
const at = (meters: number) => (meters * 180) / (Math.PI * 6_371_000)
function fixture(source: GeoSource['source'] = 'BROWSER') {
  const callbacks: Array<(value: GeoSample) => void> = []
  let failed!: (value: GeoFailure) => void
  const stop = vi.fn()
  const watch = vi.fn((sample: (value: GeoSample) => void, error: typeof failed) => {
    callbacks.push(sample)
    failed = error
    return stop
  })
  return {
    source: { source, watch },
    watch,
    stop,
    callbacks,
    emit(meters = 600, accuracy: number | null = 1000) {
      callbacks.at(-1)!({
        latitude: at(meters),
        longitude: 0,
        accuracy,
        timestamp: source === 'TMA' ? null : Date.now(),
        receivedAt: Date.now(),
      })
    },
    fail(reason: GeoFailure) {
      failed(reason)
    },
  }
}
async function settle() {
  for (let i = 0; i < 10; i++) await Promise.resolve()
}
async function next() {
  await settle()
  await vi.advanceTimersByTimeAsync(1)
}
describe('owner-requested automatic geofence acquisition', () => {
  it('lets cancellation win after an inside callback but before coordinator completion', async () => {
    const f = fixture()
    const controller = new AbortController()
    const terminal = vi.fn()
    const run = acquireGeolocation(f.source, {
      geofence: policy,
      signal: controller.signal,
      terminal,
    })
    const rejected = expect(run).rejects.toMatchObject({ name: 'AbortError' })
    f.emit(0)
    controller.abort()
    await rejected
    expect(terminal).toHaveBeenCalledWith(
      expect.objectContaining({ reason: 'CANCELLED', sampleCount: 1 }),
    )
  })
  it.each([1000, null])(
    'finishes the first inside position immediately without an accuracy hurdle (%s)',
    async (accuracy) => {
      vi.useFakeTimers()
      const f = fixture()
      const terminal = vi.fn()
      const run = acquireGeolocation(f.source, { geofence: policy, terminal })
      f.emit(300, accuracy)
      await expect(run).resolves.toMatchObject({
        sampleCount: 1,
        reason: 'WITHIN_GEOFENCE',
        sample: { accuracy },
      })
      expect(f.watch).toHaveBeenCalledOnce()
      expect(f.stop).toHaveBeenCalledOnce()
      expect(terminal).toHaveBeenCalledExactlyOnceWith({
        source: 'BROWSER',
        reason: 'WITHIN_GEOFENCE',
        duration: 0,
        sampleCount: 1,
      })
    },
  )
  it('includes the 400m boundary but not 401m in the server-provided hint', () => {
    const sample = {
      latitude: at(400),
      longitude: 0,
      accuracy: null,
      timestamp: null,
      receivedAt: 0,
    }
    expect(withinGeofence(sample, policy)).toBe(true)
    expect(withinGeofence({ ...sample, latitude: at(401) }, policy)).toBe(false)
  })
  it('automatically reaches an inside position on the second reading and emits one terminal report', async () => {
    vi.useFakeTimers()
    const f = fixture()
    const terminal = vi.fn()
    const run = acquireGeolocation(f.source, { geofence: policy, terminal })
    f.emit(600)
    await next()
    f.emit(300)
    await expect(run).resolves.toMatchObject({
      sampleCount: 2,
      reason: 'WITHIN_GEOFENCE',
      sample: { latitude: at(300) },
    })
    expect(f.watch).toHaveBeenCalledTimes(2)
    expect(terminal).toHaveBeenCalledOnce()
  })
  it('takes at most three outside readings and returns the latest, not nearest or most accurate one', async () => {
    vi.useFakeTimers()
    const f = fixture()
    const terminal = vi.fn()
    const run = acquireGeolocation(f.source, { geofence: policy, terminal })
    f.emit(500, 1)
    await next()
    f.emit(800, 2)
    await next()
    f.emit(1200, 9000)
    await expect(run).resolves.toMatchObject({
      sampleCount: 3,
      reason: 'ATTEMPTS_EXHAUSTED',
      sample: { latitude: at(1200), accuracy: 9000 },
    })
    expect(f.watch).toHaveBeenCalledTimes(3)
    expect(f.stop).toHaveBeenCalledTimes(3)
    expect(terminal).toHaveBeenCalledOnce()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('uses three readings with absent or invalid policy without any hardcoded campus rule', async () => {
    vi.useFakeTimers()
    const f = fixture('TMA')
    const run = acquireGeolocation(f.source, { geofence: { ...policy, radiusMeters: NaN } })
    f.emit(0, null)
    await next()
    f.emit(1, null)
    await next()
    f.emit(2, null)
    await expect(run).resolves.toMatchObject({
      reason: 'ATTEMPTS_EXHAUSTED',
      sampleCount: 3,
      sample: { latitude: at(2), timestamp: null },
    })
  })
  it('retries a transient error within the same three-attempt budget', async () => {
    vi.useFakeTimers()
    const f = fixture()
    const run = acquireGeolocation(f.source, { geofence: policy })
    f.fail('POSITION_UNAVAILABLE')
    await next()
    f.emit(600)
    await next()
    f.emit(200)
    await expect(run).resolves.toMatchObject({ reason: 'WITHIN_GEOFENCE', sampleCount: 2 })
    expect(f.watch).toHaveBeenCalledTimes(3)
  })
  it.each(['PERMISSION_DENIED', 'UNSUPPORTED'] as const)(
    'automatically returns %s without more prompts or manual Continue',
    async (reason) => {
      const f = fixture()
      const run = acquireGeolocation(f.source, { geofence: policy })
      f.fail(reason)
      await expect(run).resolves.toMatchObject({ reason, sample: null, sampleCount: 0 })
      expect(f.watch).toHaveBeenCalledOnce()
    },
  )
  it('bounds a hanging source by the one overall 28s deadline and ignores its late callback', async () => {
    vi.useFakeTimers()
    const f = fixture()
    const terminal = vi.fn()
    const run = acquireGeolocation(f.source, { geofence: policy, terminal })
    await vi.advanceTimersByTimeAsync(28_000)
    await expect(run).resolves.toMatchObject({
      reason: 'BUDGET_EXPIRED',
      sample: null,
      duration: 28_000,
    })
    f.emit(0)
    expect(f.watch).toHaveBeenCalledOnce()
    expect(terminal).toHaveBeenCalledOnce()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('ignores a repeated browser fix timestamp on an automatic fresh retry', async () => {
    vi.useFakeTimers()
    const f = fixture()
    const run = acquireGeolocation(f.source, { geofence: policy })
    f.emit(600)
    await settle()
    f.emit(200)
    await settle()
    expect(f.watch).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(1)
    f.emit(200)
    await expect(run).resolves.toMatchObject({ reason: 'WITHIN_GEOFENCE', sampleCount: 2 })
  })
  it('cancels an automatic retry once, stops resources and sends no later result', async () => {
    vi.useFakeTimers()
    const f = fixture()
    const controller = new AbortController()
    const terminal = vi.fn()
    const run = acquireGeolocation(f.source, {
      geofence: policy,
      signal: controller.signal,
      terminal,
    })
    const rejected = expect(run).rejects.toMatchObject({ name: 'AbortError' })
    f.emit(600)
    await next()
    controller.abort()
    f.emit(100)
    await rejected
    expect(f.watch).toHaveBeenCalledTimes(2)
    expect(f.stop).toHaveBeenCalledTimes(2)
    expect(terminal).toHaveBeenCalledExactlyOnceWith({
      source: 'BROWSER',
      reason: 'CANCELLED',
      duration: 1,
      sampleCount: 1,
    })
    expect(vi.getTimerCount()).toBe(0)
  })
})
describe('permission retries and passive inspection', () => {
  it('clears a real browser watch on cancellation and rejects its late callback', async () => {
    let callback!: PositionCallback
    const clearWatch = vi.fn()
    const source = browserGeoSource({
      watchPosition: vi.fn((sample) => {
        callback = sample
        return 7
      }),
      clearWatch,
    } as unknown as Geolocation)
    const controller = new AbortController()
    const run = acquireGeolocation(source, { signal: controller.signal, geofence: policy })
    const rejected = expect(run).rejects.toMatchObject({ name: 'AbortError' })
    controller.abort()
    callback({
      coords: { latitude: 0, longitude: 0, accuracy: 1 },
      timestamp: Date.now(),
    } as GeolocationPosition)
    await rejected
    expect(clearWatch).toHaveBeenCalledExactlyOnceWith(7)
  })
  it('bounds a hanging browser permission query and permits another fresh inspection', async () => {
    vi.useFakeTimers()
    const query = vi
      .fn()
      .mockReturnValueOnce(new Promise(() => {}))
      .mockResolvedValueOnce({ state: 'granted' })
    vi.stubGlobal('navigator', { geolocation: {}, permissions: { query } })
    const owner = createDeviceAccess({ geo: fixture().source }, null)
    owner.bindOwner('a', 's')
    const pending = owner.inspect('location')
    await vi.advanceTimersByTimeAsync(3_000)
    await pending
    expect(owner.state.location.permission).toBe('unknown')
    await owner.inspect('location')
    expect(owner.state.location.permission).toBe('granted')
  })
  it('keeps device/account preferences separate from OS grant and does not prompt on inspect', async () => {
    const f = fixture()
    const values = new Map<string, string>()
    const owner = createDeviceAccess(
      { geo: f.source, inspect: async () => 'prompt' },
      {
        getItem: (k) => values.get(k) ?? null,
        setItem: (k, v) => {
          values.set(k, v)
        },
      },
    )
    owner.bindOwner('alice', 's1')
    await owner.inspect('location')
    owner.setEnabled('location', false)
    owner.bindOwner('bob', 's2')
    expect(owner.state.location).toMatchObject({ enabled: true, permission: 'unknown' })
    owner.bindOwner('alice', 's3')
    expect(owner.state.location).toMatchObject({ enabled: false, permission: 'unknown' })
    expect(f.watch).not.toHaveBeenCalled()
  })
  it('returns APP_DISABLED automatically, without requesting sensors or another confirmation', async () => {
    const f = fixture()
    const owner = createDeviceAccess({ geo: f.source }, null)
    owner.bindOwner('a', 's')
    owner.setEnabled('location', false)
    const terminal = vi.fn()
    await expect(owner.acquire({ terminal })).resolves.toMatchObject({
      geo: { kind: 'UNAVAILABLE' },
      diagnostics: { completionReason: 'APP_DISABLED' },
    })
    expect(f.watch).not.toHaveBeenCalled()
    expect(terminal).toHaveBeenCalledOnce()
  })
  it('cancels an unanswered probe and permits a fresh explicit probe, fencing its late callback and reporting nothing', async () => {
    vi.useFakeTimers()
    const f = fixture()
    const owner = createDeviceAccess({ geo: f.source }, null)
    owner.bindOwner('a', 's')
    const first = owner.probe('location')
    owner.cancel()
    await first
    const second = owner.probe('location')
    expect(f.watch).toHaveBeenCalledTimes(2)
    f.callbacks[0]!({
      latitude: 0,
      longitude: 0,
      accuracy: 1,
      timestamp: Date.now(),
      receivedAt: Date.now(),
    })
    await settle()
    expect(owner.state.location.permission).toBe('checking')
    f.emit(0, null)
    await second
    expect(owner.state.location.permission).toBe('granted')
    expect(vi.getTimerCount()).toBe(0)
  })
  it('allows a fresh probe after a denied/dismissed probe without caching a permanent block', async () => {
    const f = fixture()
    const owner = createDeviceAccess({ geo: f.source }, null)
    owner.bindOwner('a', 's')
    const first = owner.probe('location')
    f.fail('PERMISSION_DENIED')
    await first
    const second = owner.probe('location')
    f.emit(0)
    await second
    expect(f.watch).toHaveBeenCalledTimes(2)
    expect(owner.state.location.permission).toBe('granted')
  })
  it('aborts check-in before business transport on disable/owner cancellation', async () => {
    const f = fixture()
    const owner = createDeviceAccess({ geo: f.source }, null)
    owner.bindOwner('a', 's')
    const run = owner.acquire()
    const rejected = expect(run).rejects.toBeInstanceOf(CheckinBeforeSendCancelled)
    owner.setEnabled('location', false)
    f.emit(0)
    await rejected
  })
  it('treats unsupported permission descriptors as unknown', async () => {
    vi.stubGlobal('navigator', {
      mediaDevices: { getUserMedia: vi.fn() },
      permissions: { query: vi.fn().mockRejectedValue(new TypeError()) },
    })
    await expect(inspectBrowserPermission('camera')).resolves.toBe('unknown')
  })
  it('bounds settings camera probes and stops their late streams', async () => {
    vi.useFakeTimers()
    let resolve!: (s: MediaStream) => void
    vi.stubGlobal('navigator', {
      mediaDevices: {
        getUserMedia: vi.fn(
          () =>
            new Promise<MediaStream>((done) => {
              resolve = done
            }),
        ),
      },
    })
    const owner = createDeviceAccess({ geo: fixture().source }, null)
    owner.bindOwner('a', 's')
    const probe = owner.probe('camera')
    await vi.advanceTimersByTimeAsync(15_000)
    await probe
    const stop = vi.fn()
    resolve({ getTracks: () => [{ stop }] } as unknown as MediaStream)
    await settle()
    expect(stop).toHaveBeenCalledOnce()
  })
})
