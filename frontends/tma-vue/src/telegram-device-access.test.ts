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
  it('reports the unavailable native service and rechecks after Telegram restores availability', async () => {
    const getLocation = vi.fn((callback) => callback({ latitude: 0, longitude: 0 }))
    const manager = {
      isInited: true,
      isLocationAvailable: false,
      isAccessGranted: true,
      init() {},
      getLocation,
    }
    const platform = telegramDevicePlatform(app({ LocationManager: manager }))
    await expect(acquireGeolocation(platform.geo)).resolves.toMatchObject({ reason: 'UNSUPPORTED' })
    expect(getLocation).not.toHaveBeenCalled()
    const owner = createDeviceAccess(platform, null)
    owner.bindOwner('a', 's')
    await owner.probe('location')
    expect(owner.state.location.check).toBe('unsupported')
    expect(getLocation).not.toHaveBeenCalled()
    manager.isLocationAvailable = true
    await owner.probe('location')
    expect(getLocation).toHaveBeenCalledOnce()
    expect(owner.state.location.check).toBe('granted')
  })
  it('uses real browser semantics when the initialized native client reports unavailable without access flags', async () => {
    const clearWatch = vi.fn()
    const watchPosition = vi.fn((callback: PositionCallback) => {
      callback({
        coords: { latitude: 0, longitude: 0, accuracy: 20 },
        timestamp: Date.now(),
      } as GeolocationPosition)
      return 1
    })
    vi.stubGlobal('navigator', { geolocation: { watchPosition, clearWatch } })
    const getLocation = vi.fn()
    const platform = telegramDevicePlatform(
      app({
        LocationManager: { isInited: true, isLocationAvailable: false, init() {}, getLocation },
      }),
    )
    const owner = createDeviceAccess(platform, null)
    owner.bindOwner('a', 's')
    const probe = owner.probe('location')
    expect(watchPosition).toHaveBeenCalledOnce()
    await probe
    expect(owner.state.location).toMatchObject({ source: 'BROWSER', check: 'granted' })
    const command = await owner.acquire({
      geofence: { latitude: 0, longitude: 0, radiusMeters: 100 },
    })
    expect(command).toMatchObject({
      diagnostics: { source: 'BROWSER', fixTimestamp: expect.any(String) },
    })
    expect(clearWatch).toHaveBeenCalledTimes(2)
    expect(getLocation).not.toHaveBeenCalled()
  })
  it('keeps the native route after a denial even if the unavailable client omits its flags', async () => {
    const watchPosition = vi.fn()
    vi.stubGlobal('navigator', { geolocation: { watchPosition } })
    const manager: NonNullable<App['LocationManager']> = {
      isInited: true,
      isLocationAvailable: true,
      isAccessRequested: true,
      isAccessGranted: false,
      init() {},
      getLocation(callback) {
        callback(null)
      },
    }
    const owner = createDeviceAccess(
      telegramDevicePlatform(app({ LocationManager: manager })),
      null,
    )
    owner.bindOwner('a', 's')
    await owner.probe('location')
    expect(owner.state.location.check).toBe('denied')
    manager.isLocationAvailable = false
    delete manager.isAccessRequested
    delete manager.isAccessGranted
    await owner.probe('location')
    expect(owner.state.location).toMatchObject({ source: 'TMA', check: 'denied' })
    expect(watchPosition).not.toHaveBeenCalled()
  })
  it('keeps the native route after null even when the client never updates the access flags', async () => {
    const watchPosition = vi.fn()
    vi.stubGlobal('navigator', { geolocation: { watchPosition } })
    const manager = {
      isInited: true,
      isLocationAvailable: true,
      isAccessRequested: false,
      isAccessGranted: false,
      init() {},
      getLocation(callback: (location: null) => void) {
        callback(null)
      },
    }
    const owner = createDeviceAccess(
      telegramDevicePlatform(app({ LocationManager: manager })),
      null,
    )
    owner.bindOwner('a', 's')
    await owner.probe('location')
    expect(owner.state.location.check).toBe('unconfirmed')
    manager.isLocationAvailable = false
    await owner.probe('location')
    expect(owner.state.location).toMatchObject({ source: 'TMA', check: 'unsupported' })
    expect(watchPosition).not.toHaveBeenCalled()
  })
  it('latches a late null after cancellation and a change to unavailable', async () => {
    const watchPosition = vi.fn()
    vi.stubGlobal('navigator', { geolocation: { watchPosition } })
    let respond!: (location: null) => void
    const manager: NonNullable<App['LocationManager']> = {
      isInited: true,
      isLocationAvailable: true,
      init() {},
      getLocation(callback) {
        respond = callback
      },
    }
    const owner = createDeviceAccess(
      telegramDevicePlatform(app({ LocationManager: manager })),
      null,
    )
    owner.bindOwner('a', 's')
    const cancelled = owner.probe('location')
    owner.cancel()
    manager.isLocationAvailable = false
    respond(null)
    await cancelled
    expect(owner.state.location.check).toBe('cancelled')
    await owner.probe('location')
    expect(owner.state.location).toMatchObject({ source: 'TMA', check: 'unsupported' })
    expect(watchPosition).not.toHaveBeenCalled()
  })
  it.each(['probe', 'acquire'] as const)(
    'chooses browser for the first %s after delayed native initialization reports unavailable',
    async (action) => {
      vi.useFakeTimers()
      const watchPosition = vi.fn((callback: PositionCallback) => {
        callback({
          coords: { latitude: 0, longitude: 0, accuracy: 10 },
          timestamp: Date.now(),
        } as GeolocationPosition)
        return 1
      })
      vi.stubGlobal('navigator', { geolocation: { watchPosition, clearWatch: vi.fn() } })
      const manager: NonNullable<App['LocationManager']> = {
        isInited: false,
        init(callback) {
          setTimeout(() => {
            manager.isInited = true
            manager.isLocationAvailable = false
            callback()
          }, 3_500)
        },
        getLocation: vi.fn(),
      }
      const owner = createDeviceAccess(
        telegramDevicePlatform(app({ LocationManager: manager })),
        null,
      )
      owner.bindOwner('a', 's')
      const operation =
        action === 'probe'
          ? owner.probe('location')
          : owner.acquire({ geofence: { latitude: 0, longitude: 0, radiusMeters: 100 } })
      await vi.advanceTimersByTimeAsync(3_100)
      expect(watchPosition).not.toHaveBeenCalled()
      await vi.advanceTimersByTimeAsync(400)
      const result = await operation
      expect(owner.state.location.source).toBe('BROWSER')
      expect(watchPosition).toHaveBeenCalledOnce()
      expect(manager.getLocation).not.toHaveBeenCalled()
      if (action === 'acquire') expect(result).toMatchObject({ diagnostics: { source: 'BROWSER' } })
      else expect(owner.state.location.check).toBe('granted')
    },
  )
  it('reports a timed-out active initialization without posting another init or a sensor request', async () => {
    vi.useFakeTimers()
    const init = vi.fn()
    const getLocation = vi.fn()
    const watchPosition = vi.fn()
    vi.stubGlobal('navigator', { geolocation: { watchPosition } })
    const owner = createDeviceAccess(
      telegramDevicePlatform(
        app({
          LocationManager: { isInited: false, init, getLocation },
        }),
      ),
      null,
    )
    owner.bindOwner('a', 's')
    const terminal = vi.fn()
    const command = owner.acquire({ terminal })
    await vi.advanceTimersByTimeAsync(8_000)
    await expect(command).resolves.toMatchObject({
      diagnostics: { source: 'TMA', completionReason: 'TIMEOUT' },
    })
    expect(terminal).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({
        source: 'TMA',
        reason: 'TIMEOUT',
        sampleCount: 0,
      }),
    )
    expect(init).toHaveBeenCalledOnce()
    expect(getLocation).not.toHaveBeenCalled()
    expect(watchPosition).not.toHaveBeenCalled()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('bounds a native location request that never calls back', async () => {
    vi.useFakeTimers()
    const getLocation = vi.fn()
    const platform = telegramDevicePlatform(
      app({ LocationManager: { isInited: true, init: vi.fn(), getLocation } }),
    )
    const result = acquireGeolocation(platform.geo)
    await vi.advanceTimersByTimeAsync(28_000)
    await expect(result).resolves.toMatchObject({ source: 'TMA', reason: 'BUDGET_EXPIRED' })
    expect(getLocation).toHaveBeenCalledOnce()
    expect(vi.getTimerCount()).toBe(0)
  })
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
    await expect(platform.inspect?.('location')).resolves.toBe('denied')
    expect(getLocation).not.toHaveBeenCalled()
    expect(openSettings).not.toHaveBeenCalled()
    expect(platform.openLocationSettings?.()).toBe(true)
    expect(openSettings).toHaveBeenCalledOnce()
  })
  it.each([
    { isInited: false, isLocationAvailable: true, isAccessRequested: true },
    { isInited: true, isLocationAvailable: false, isAccessRequested: true },
    { isInited: true, isLocationAvailable: true, isAccessRequested: false },
    { isInited: true, isLocationAvailable: true, isAccessRequested: true, isAccessGranted: true },
  ])('does not call native settings when the bridge would reject or ignore it (%j)', (flags) => {
    const openSettings = vi.fn()
    const platform = telegramDevicePlatform(
      app({
        LocationManager: { init() {}, getLocation() {}, openSettings, ...flags },
      }),
    )
    expect(platform.openLocationSettings?.()).toBe(false)
    expect(openSettings).not.toHaveBeenCalled()
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
    expect(owner.state.location.check).toBe('denied')
    await owner.probe('location')
    expect(owner.state.location.check).toBe('granted')
    expect(getLocation).toHaveBeenCalledTimes(2)
  })
  it('waits for a cancelled native request to settle before registering another SDK callback', async () => {
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
    await owner.probe('location')
    expect(owner.state.location.check).toBe('error')
    expect(getLocation).toHaveBeenCalledOnce()
    callbacks[0]!({ latitude: 0, longitude: 0 })
    expect(owner.state.location.check).toBe('error')
    const second = owner.probe('location')
    expect(owner.state.location.check).toBe('checking')
    callbacks[1]!({ latitude: 0, longitude: 0 })
    await second
    expect(owner.state.location.check).toBe('granted')
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
  it('bounds a native QR popup without a close event', async () => {
    vi.useFakeTimers()
    const closeScanQrPopup = vi.fn(),
      failed = vi.fn()
    const platform = telegramDevicePlatform(
      app({ version: '6.4', showScanQrPopup() {}, closeScanQrPopup }),
    )
    platform.scanQr?.(vi.fn(), vi.fn(), failed)
    await vi.advanceTimersByTimeAsync(60_000)
    expect(closeScanQrPopup).toHaveBeenCalledOnce()
    expect(failed).toHaveBeenCalledWith(expect.objectContaining({ name: 'TimeoutError' }))
  })
  it('uses the browser scanner path in Telegram Desktop even when the SDK exposes native QR methods', () => {
    const showScanQrPopup = vi.fn()
    const platform = telegramDevicePlatform(
      app({
        platform: 'tdesktop',
        showScanQrPopup,
        closeScanQrPopup: vi.fn(),
      }),
    )
    expect(platform.scanQr).toBeUndefined()
    expect(showScanQrPopup).not.toHaveBeenCalled()
  })
})
