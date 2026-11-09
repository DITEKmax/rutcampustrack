import { afterEach, describe, expect, it, vi } from 'vitest'
import { telegramDevicePlatform } from './telegram'
import { createDeviceAccess } from '../../mobile-core/src/device-access/device-access'
import { acquireGeolocation } from '../../mobile-core/src/device-access/geolocation'
type App = NonNullable<NonNullable<Window['Telegram']>['WebApp']>
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})
function app(extra: Partial<App>): App {
  return { initData: '', ready() {}, expand() {}, viewportHeight: 800, version: '8.0', ...extra }
}
describe('Telegram bounded sensor adapters', () => {
  it('lets an explicit probe query the native service again after cached unavailability', async () => {
    const getLocation = vi.fn((callback) => callback({ latitude: 0, longitude: 0 }))
    const platform = telegramDevicePlatform(
      app({
        LocationManager: { isInited: true, isLocationAvailable: false, init() {}, getLocation },
      }),
    )
    await expect(acquireGeolocation(platform.geo)).resolves.toMatchObject({ reason: 'UNSUPPORTED' })
    expect(getLocation).not.toHaveBeenCalled()
    const owner = createDeviceAccess(platform, null)
    owner.bindOwner('a', 's')
    await owner.probe('location')
    expect(getLocation).toHaveBeenCalledOnce()
    expect(owner.state.location.permission).toBe('granted')
  })
  it.each(['init', 'location'] as const)(
    'bounds a native %s that never calls back',
    async (hang) => {
      vi.useFakeTimers()
      const getLocation = vi.fn()
      const platform = telegramDevicePlatform(
        app({ LocationManager: { isInited: hang === 'location', init: vi.fn(), getLocation } }),
      )
      const result = acquireGeolocation(platform.geo)
      await vi.advanceTimersByTimeAsync(28_000)
      await expect(result).resolves.toMatchObject({
        source: 'TMA',
        reason: 'BUDGET_EXPIRED',
      })
      expect(getLocation).toHaveBeenCalledTimes(hang === 'location' ? 1 : 0)
      expect(vi.getTimerCount()).toBe(0)
    },
  )
  it('serializes requests, preserves unknown fix timestamp and fences a late callback after cancel', async () => {
    vi.useFakeTimers()
    let callback!: (
      value: { latitude: number; longitude: number; horizontal_accuracy: number | null } | null,
    ) => void
    const getLocation = vi.fn((value: typeof callback) => {
      callback = value
    })
    const platform = telegramDevicePlatform(
      app({
        LocationManager: { isInited: true, isLocationAvailable: true, init() {}, getLocation },
      }),
    )
    const samples = vi.fn()
    const stop = platform.geo.watch(samples, vi.fn())
    await vi.advanceTimersByTimeAsync(5_000)
    expect(getLocation).toHaveBeenCalledOnce()
    callback({ latitude: 55.7, longitude: 37.6, horizontal_accuracy: null })
    expect(samples).toHaveBeenCalledWith(
      expect.objectContaining({ accuracy: null, timestamp: null }),
    )
    await vi.advanceTimersByTimeAsync(1_500)
    expect(getLocation).toHaveBeenCalledTimes(2)
    stop()
    callback({ latitude: 55.7, longitude: 37.6, horizontal_accuracy: 20 })
    expect(samples).toHaveBeenCalledOnce()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('inspects flags without requesting location and opens settings only on explicit invocation', async () => {
    const getLocation = vi.fn()
    const openSettings = vi.fn()
    const platform = telegramDevicePlatform(
      app({
        LocationManager: {
          isInited: true,
          isLocationAvailable: true,
          isAccessRequested: true,
          isAccessGranted: false,
          init() {},
          getLocation,
          openSettings,
        },
      }),
    )
    await expect(platform.inspect?.('location')).resolves.toBe('unknown')
    expect(getLocation).not.toHaveBeenCalled()
    expect(openSettings).not.toHaveBeenCalled()
    platform.openLocationSettings?.()
    expect(openSettings).toHaveBeenCalledOnce()
  })
  it('retries getLocation after a not-granted response even when native flags remain cached', async () => {
    const getLocation = vi
      .fn()
      .mockImplementationOnce((callback) => callback(null))
      .mockImplementationOnce((callback) =>
        callback({ latitude: 0, longitude: 0, horizontal_accuracy: null }),
      )
    const platform = telegramDevicePlatform(
      app({
        LocationManager: {
          isInited: true,
          isLocationAvailable: true,
          isAccessRequested: true,
          isAccessGranted: false,
          init() {},
          getLocation,
        },
      }),
    )
    const owner = createDeviceAccess(platform, null)
    owner.bindOwner('a', 's')
    await owner.probe('location')
    expect(owner.state.location.permission).toBe('denied')
    await owner.probe('location')
    expect(owner.state.location.permission).toBe('granted')
    expect(getLocation).toHaveBeenCalledTimes(2)
  })
  it('allows another explicit native probe after an unanswered request and ignores the old callback', async () => {
    const callbacks: Array<(value: { latitude: number; longitude: number } | null) => void> = []
    const getLocation = vi.fn((callback) => {
      callbacks.push(callback)
    })
    const platform = telegramDevicePlatform(
      app({
        LocationManager: { isInited: true, isLocationAvailable: true, init() {}, getLocation },
      }),
    )
    const owner = createDeviceAccess(platform, null)
    owner.bindOwner('a', 's')
    const first = owner.probe('location')
    owner.cancel()
    await first
    const second = owner.probe('location')
    callbacks[0]!({ latitude: 0, longitude: 0 })
    await Promise.resolve()
    expect(owner.state.location.permission).toBe('checking')
    callbacks[1]!({ latitude: 0, longitude: 0 })
    await second
    expect(owner.state.location.permission).toBe('granted')
    expect(getLocation).toHaveBeenCalledTimes(2)
  })
  it('uses one native QR popup and removes its listeners before handing off a scan', () => {
    vi.useFakeTimers()
    let callback!: (value: string) => boolean
    const showScanQrPopup = vi.fn((_: unknown, value: typeof callback) => {
      callback = value
    })
    const closeScanQrPopup = vi.fn()
    const onEvent = vi.fn(),
      offEvent = vi.fn(),
      scanned = vi.fn()
    const platform = telegramDevicePlatform(
      app({ showScanQrPopup, closeScanQrPopup, onEvent, offEvent }),
    )
    const stop = platform.scanQr?.(scanned, vi.fn(), vi.fn())
    expect(showScanQrPopup).toHaveBeenCalledOnce()
    expect(callback('capability')).toBe(true)
    callback('late capability')
    stop?.()
    expect(closeScanQrPopup).toHaveBeenCalledOnce()
    expect(scanned).toHaveBeenCalledExactlyOnceWith('capability')
    expect(offEvent).toHaveBeenCalledWith('scanQrPopupClosed', expect.any(Function))
    expect(vi.getTimerCount()).toBe(0)
  })
  it('bounds a native QR popup without a close event and reports honest camera status', async () => {
    vi.useFakeTimers()
    const closeScanQrPopup = vi.fn(),
      failed = vi.fn()
    const platform = telegramDevicePlatform(
      app({ version: '6.4', showScanQrPopup() {}, closeScanQrPopup }),
    )
    await expect(platform.inspect?.('camera')).resolves.toBe('unknown')
    platform.scanQr?.(vi.fn(), vi.fn(), failed)
    await vi.advanceTimersByTimeAsync(60_000)
    expect(closeScanQrPopup).toHaveBeenCalledOnce()
    expect(failed).toHaveBeenCalledWith(expect.objectContaining({ name: 'TimeoutError' }))
  })
})
