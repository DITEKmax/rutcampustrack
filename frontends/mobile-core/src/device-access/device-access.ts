import { inject, onBeforeUnmount, onMounted, provide, reactive, type InjectionKey } from 'vue'
import type { CheckinDiagnostics, StudentCheckinCommand } from '../api/types'
import { CheckinBeforeSendCancelled } from '../domain/checkin'
import {
  acquireGeolocation,
  browserGeoSource,
  isFreshBrowserFix,
  notifyGeoTerminal,
  validGeofence,
  type GeoTerminalHook,
  readGeolocationOnce,
  type GeoGeofence,
  type GeoSource,
} from './geolocation'
import { requestBrowserCamera } from './browser-camera'
import { browserDeviceContextAllows } from './browser-policy'

export type DevicePermission = 'prompt' | 'granted' | 'denied' | 'unsupported' | 'unknown'
export type DeviceCheck =
  | 'checking'
  | 'granted'
  | 'denied'
  | 'unsupported'
  | 'cancelled'
  | 'unconfirmed'
  | 'timeout'
  | 'error'
export type DeviceKind = 'location' | 'camera'
export interface DevicePlatform {
  geo: GeoSource
  selectGeo?(): GeoSource
  prepareGeo?(signal?: AbortSignal, budgetMs?: number): Promise<void> | undefined
  inspect?(kind: DeviceKind, signal?: AbortSignal): Promise<DevicePermission>
  openLocationSettings?(): boolean
  openBrowserApp?(): void
  onDeviceReturn?(listener: () => void): () => void
  scanQr?(
    scanned: (value: string) => void,
    closed: () => void,
    failed: (error: unknown) => void,
  ): (() => void) | null
}
export interface AcquisitionOptions {
  signal?: AbortSignal
  progress?: (stage: 'determining' | 'refining') => void
  terminal?: GeoTerminalHook
  geofence?: GeoGeofence | null
}
const key: InjectionKey<DeviceAccess> = Symbol('device-access')
const GEO_INIT_BUDGET_MS = 8_000
export type DeviceAccess = ReturnType<typeof createDeviceAccess>
export function useDeviceAccess(): DeviceAccess | null {
  return inject(key, null)
}
export function provideDeviceAccess(platform: DevicePlatform): DeviceAccess {
  const owner = createDeviceAccess(platform)
  provide(key, owner)
  const hidden = () => {
    if (document.visibilityState === 'hidden') owner.cancel()
  }
  onMounted(() => {
    document.addEventListener('visibilitychange', hidden)
    window.addEventListener('pagehide', owner.cancel)
    window.addEventListener('offline', owner.cancel)
  })
  onBeforeUnmount(() => {
    owner.dispose()
    document.removeEventListener('visibilitychange', hidden)
    window.removeEventListener('pagehide', owner.cancel)
    window.removeEventListener('offline', owner.cancel)
  })
  return owner
}

/** Preferences never stand in for a browser/OS grant. No coordinates are persisted here. */
export function createDeviceAccess(
  platform: DevicePlatform = { geo: browserGeoSource() },
  storage: Pick<Storage, 'getItem' | 'setItem'> | null = safeStorage(),
) {
  const state = reactive({
    location: {
      enabled: false,
      permission: 'unknown' as DevicePermission,
      check: null as DeviceCheck | null,
      source: platform.geo.source,
    },
    camera: {
      enabled: false,
      permission: 'unknown' as DevicePermission,
      check: null as DeviceCheck | null,
    },
    checking: null as DeviceKind | null,
    stage: null as 'determining' | 'refining' | null,
  })
  let account: string | null = null
  let authority: string | null = null
  let generation = 0
  let active: AbortController | null = null
  const inspections = new Map<DeviceKind, AbortController>()
  const cleanups = new Set<() => void>()
  function cancel(): void {
    for (const inspection of inspections.values()) inspection.abort()
    inspections.clear()
    active?.abort()
    active = null
    state.stage = null
    if (state.checking) state[state.checking].check = 'cancelled'
    state.checking = null
    for (const cleanup of [...cleanups]) cleanup()
  }
  function storageKey(kind: DeviceKind): string {
    return `rct.device-access.v1.${encodeURIComponent(account ?? '')}.${kind}`
  }
  function bindOwner(nextAccount: string | null, nextAuthority: string | null): void {
    if (account === nextAccount && authority === nextAuthority) return
    cancel()
    generation++
    account = nextAccount
    authority = nextAuthority
    state.location.source = currentGeo().source
    for (const kind of ['location', 'camera'] as const) {
      state[kind].permission = 'unknown'
      state[kind].check = null
      try {
        state[kind].enabled = !!account && storage?.getItem(storageKey(kind)) !== 'off'
      } catch {
        state[kind].enabled = !!account
      }
    }
  }
  function setEnabled(kind: DeviceKind, enabled: boolean): void {
    state[kind].enabled = enabled
    if (!enabled) cancel()
    if (account)
      try {
        storage?.setItem(storageKey(kind), enabled ? 'on' : 'off')
      } catch {
        /* In-memory preference still applies. */
      }
  }
  async function inspect(kind: DeviceKind): Promise<void> {
    if (!authority || active) return
    const version = generation
    inspections.get(kind)?.abort()
    const inspection = new AbortController()
    inspections.set(kind, inspection)
    try {
      const permission = await (platform.inspect?.(kind, inspection.signal) ??
        inspectBrowserPermission(kind, inspection.signal))
      if (version === generation && !inspection.signal.aborted) {
        state[kind].permission = permission
        if (kind === 'location') state.location.source = currentGeo().source
      }
    } catch {
      if (version === generation && !inspection.signal.aborted) state[kind].permission = 'unknown'
    } finally {
      if (inspections.get(kind) === inspection) inspections.delete(kind)
    }
  }
  function currentGeo(): GeoSource {
    return platform.selectGeo?.() ?? platform.geo
  }
  async function acquire(options: AcquisitionOptions = {}): Promise<StudentCheckinCommand> {
    if (active) throw new CheckinBeforeSendCancelled()
    const controller = new AbortController()
    active = controller
    const abort = () => controller.abort()
    options.signal?.addEventListener('abort', abort, { once: true })
    if (options.signal?.aborted) controller.abort()
    try {
      if (controller.signal.aborted || !authority) throw new CheckinBeforeSendCancelled()
      const geo = currentGeo()
      state.location.source = geo.source
      const result = !state.location.enabled
        ? {
            source: geo.source,
            sample: null,
            sampleCount: 0,
            duration: 0,
            reason: 'APP_DISABLED' as const,
          }
        : await acquireGeolocation(geo, {
            signal: controller.signal,
            ...(platform.prepareGeo
              ? {
                  selectSource() {
                    const preparation = platform.prepareGeo?.(controller.signal, GEO_INIT_BUDGET_MS)
                    if (!preparation) return geo
                    return preparation.then(() => {
                      const selected = currentGeo()
                      if (!controller.signal.aborted) state.location.source = selected.source
                      return selected
                    })
                  },
                }
              : {}),
            ...(options.geofence ? { geofence: options.geofence } : {}),
            ...(options.terminal ? { terminal: options.terminal } : {}),
            progress(stage) {
              state.stage = stage
              options.progress?.(stage)
            },
          })
      if (result.reason === 'APP_DISABLED')
        notifyGeoTerminal(options.terminal, {
          source: result.source,
          reason: result.reason,
          duration: result.duration,
          sampleCount: result.sampleCount,
        })
      if (controller.signal.aborted) throw new CheckinBeforeSendCancelled()
      if (result.reason === 'PERMISSION_DENIED') state.location.permission = 'denied'
      if (result.reason === 'UNSUPPORTED') state.location.permission = 'unsupported'
      if (result.sample) state.location.permission = 'granted'
      return commandFromGeoResult(result, validGeofence(options.geofence))
    } catch (error) {
      if (controller.signal.aborted) throw new CheckinBeforeSendCancelled()
      throw error
    } finally {
      options.signal?.removeEventListener('abort', abort)
      if (active === controller) {
        active = null
        state.stage = null
      }
    }
  }
  async function probe(kind: DeviceKind): Promise<void> {
    if (!authority || !state[kind].enabled || active) return
    const version = generation
    inspections.get(kind)?.abort()
    inspections.delete(kind)
    const controller = new AbortController()
    active = controller
    state.checking = kind
    state[kind].check = 'checking'
    let result: DeviceCheck = 'error'
    try {
      if (kind === 'location') {
        const startedAt = performance.now()
        const preparation = platform.prepareGeo?.(controller.signal, GEO_INIT_BUDGET_MS)
        if (preparation) await preparation
        if (controller.signal.aborted) return
        const geo = currentGeo()
        state.location.source = geo.source
        const reading = await readGeolocationOnce(geo, {
          signal: controller.signal,
          budgetMs: 15_000 - (performance.now() - startedAt),
        })
        if (reading.sample) result = 'granted'
        else if (reading.reason === 'UNSUPPORTED') result = 'unsupported'
        else if (reading.reason === 'TIMEOUT' || reading.reason === 'BUDGET_EXPIRED')
          result = 'timeout'
        else if (reading.reason === 'PERMISSION_DENIED') {
          const permission =
            geo.source === 'BROWSER'
              ? await inspectBrowserPermission(kind, controller.signal)
              : await platform.inspect?.(kind, controller.signal)
          result = permission === 'denied' ? 'denied' : 'unconfirmed'
        }
      } else {
        result = await probeBrowserCamera(controller.signal)
      }
    } catch (error) {
      const name = error instanceof Error ? error.name : ''
      if (name === 'TimeoutError') result = 'timeout'
      else if (name === 'AbortError') result = 'cancelled'
      else if (name === 'SecurityError' || name === 'NotSupportedError' || name === 'NotFoundError')
        result = 'unsupported'
      else if (name === 'NotAllowedError')
        result =
          (await inspectBrowserPermission(kind, controller.signal)) === 'denied'
            ? 'denied'
            : 'unconfirmed'
    } finally {
      if (active === controller) {
        active = null
        state.checking = null
        if (version === generation && !controller.signal.aborted) {
          state[kind].check = result
          if (result === 'granted' || result === 'denied' || result === 'unsupported')
            state[kind].permission = result
          else if (result === 'unconfirmed') state[kind].permission = 'unknown'
        }
      }
    }
  }
  function registerStop(stop: () => void): () => void {
    cleanups.add(stop)
    return () => cleanups.delete(stop)
  }
  return {
    state,
    bindOwner,
    setEnabled,
    inspect,
    probe,
    acquire,
    cancel,
    registerStop,
    openLocationSettings: platform.openLocationSettings,
    openBrowserApp: platform.openBrowserApp,
    onDeviceReturn: platform.onDeviceReturn,
    scanQr: platform.scanQr,
    dispose() {
      cancel()
      cleanups.clear()
    },
  }
}

function probeBrowserCamera(signal: AbortSignal): Promise<DeviceCheck> {
  const stream = requestBrowserCamera()
  return new Promise((resolve, reject) => {
    const finish = () => {
      clearTimeout(timer)
      signal.removeEventListener('abort', abort)
    }
    const abort = () => {
      finish()
      resolve('cancelled')
    }
    const timer = setTimeout(() => {
      finish()
      resolve('timeout')
    }, 15_000)
    signal.addEventListener('abort', abort, { once: true })
    // Browsers may deliver a stream after cancellation or timeout. Stop every track.
    void stream.then(
      (value) => {
        finish()
        const tracks = value.getTracks()
        const live = tracks.some((track) => track.kind === 'video' && track.readyState === 'live')
        for (const track of tracks) track.stop()
        resolve(live ? 'granted' : 'error')
      },
      (error: unknown) => {
        finish()
        reject(error)
      },
    )
  })
}

/** First-send guard only. An already-sent ambiguous command must replay unchanged. */
export function hasFreshCheckinCoordinates(
  command: StudentCheckinCommand,
  now = Date.now(),
): boolean {
  if (command.geo.kind !== 'COORDINATES' || command.diagnostics?.source !== 'BROWSER') return true
  const timestamp = command.diagnostics.fixTimestamp
  return typeof timestamp === 'string' && isFreshBrowserFix(Date.parse(timestamp), now)
}

function safeStorage(): Storage | null {
  try {
    return globalThis.localStorage ?? null
  } catch {
    return null
  }
}
export async function inspectBrowserPermission(
  kind: DeviceKind,
  signal?: AbortSignal,
): Promise<DevicePermission> {
  if (
    !browserDeviceContextAllows(kind === 'location' ? 'geolocation' : 'camera') ||
    (kind === 'location'
      ? !globalThis.navigator?.geolocation
      : !globalThis.navigator?.mediaDevices?.getUserMedia)
  )
    return 'unsupported'
  if (!navigator.permissions?.query) return 'unknown'
  return new Promise((resolve) => {
    let done = false
    const finish = (value: DevicePermission) => {
      if (done) return
      done = true
      clearTimeout(timer)
      signal?.removeEventListener('abort', abort)
      resolve(value)
    }
    const abort = () => finish('unknown')
    const timer = setTimeout(abort, 3_000)
    signal?.addEventListener('abort', abort, { once: true })
    if (signal?.aborted) return abort()
    try {
      void navigator.permissions
        .query({ name: kind === 'location' ? 'geolocation' : ('camera' as PermissionName) })
        .then(
          (value) => finish(value.state),
          () => finish('unknown'),
        )
    } catch {
      finish('unknown')
    }
  })
}
export function commandFromGeoResult(
  result: import('./geolocation').GeoResult,
  allowNewReasons = true,
): StudentCheckinCommand {
  const sample = result.sample
  const diagnostics: CheckinDiagnostics = {
    source: result.source,
    accuracyMeters:
      sample?.accuracy !== null &&
      sample?.accuracy !== undefined &&
      Number.isFinite(sample.accuracy) &&
      sample.accuracy >= 0 &&
      sample.accuracy <= 10_000_000
        ? sample.accuracy
        : null,
    fixTimestamp:
      sample?.timestamp !== null && sample?.timestamp !== undefined
        ? new Date(sample.timestamp).toISOString()
        : null,
    receivedAt: sample ? new Date(sample.receivedAt).toISOString() : null,
    sampleCount: result.sampleCount,
    acquisitionDurationMs: result.duration,
    ...(allowNewReasons ||
    (result.reason !== 'WITHIN_GEOFENCE' && result.reason !== 'ATTEMPTS_EXHAUSTED')
      ? { completionReason: result.reason }
      : {}),
  }
  return {
    geo: sample
      ? { kind: 'COORDINATES' as const, latitude: sample.latitude, longitude: sample.longitude }
      : {
          kind: 'UNAVAILABLE' as const,
          reason:
            result.reason === 'PERMISSION_DENIED'
              ? ('PERMISSION_DENIED' as const)
              : result.reason === 'TIMEOUT'
                ? ('TIMEOUT' as const)
                : ('POSITION_UNAVAILABLE' as const),
        },
    diagnostics,
  }
}
