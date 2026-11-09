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

export type DevicePermission =
  | 'prompt'
  | 'granted'
  | 'denied'
  | 'unsupported'
  | 'unknown'
  | 'checking'
  | 'error'
export type DeviceKind = 'location' | 'camera'
export interface DevicePlatform {
  geo: GeoSource
  inspect?(kind: DeviceKind, signal?: AbortSignal): Promise<DevicePermission>
  openLocationSettings?(): void
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
    location: { enabled: true, permission: 'unknown' as DevicePermission },
    camera: { enabled: true, permission: 'unknown' as DevicePermission },
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
    for (const kind of ['camera', 'location'] as const)
      if (state[kind].permission === 'checking') state[kind].permission = 'unknown'
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
    for (const kind of ['location', 'camera'] as const) {
      state[kind].permission = 'unknown'
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
    if (active) return
    const version = generation
    inspections.get(kind)?.abort()
    const inspection = new AbortController()
    inspections.set(kind, inspection)
    state[kind].permission = 'checking'
    const permission = await (
      platform.inspect?.(kind, inspection.signal) ??
      inspectBrowserPermission(kind, inspection.signal)
    ).catch(() => 'error' as const)
    if (version === generation && !inspection.signal.aborted) state[kind].permission = permission
    if (inspections.get(kind) === inspection) inspections.delete(kind)
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
      const result = !state.location.enabled
        ? {
            source: platform.geo.source,
            sample: null,
            sampleCount: 0,
            duration: 0,
            reason: 'APP_DISABLED' as const,
          }
        : await acquireGeolocation(platform.geo, {
            signal: controller.signal,
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
    state[kind].permission = 'checking'
    try {
      if (kind === 'location') {
        const result = await readGeolocationOnce(platform.geo, {
          signal: controller.signal,
          probe: true,
        })
        if (version !== generation || controller.signal.aborted) return
        state.location.permission = result.sample
          ? 'granted'
          : result.reason === 'PERMISSION_DENIED'
            ? 'denied'
            : result.reason === 'UNSUPPORTED'
              ? 'unsupported'
              : 'error'
      } else if (platform.scanQr) {
        // Telegram exposes no universal camera grant or permission request API.
        state.camera.permission = 'unknown'
      } else {
        if (!navigator.mediaDevices?.getUserMedia) {
          state.camera.permission = 'unsupported'
          return
        }
        const streamPromise = requestBrowserCamera()
        const stream = await new Promise<MediaStream>((resolve, reject) => {
          const finish = () => {
            clearTimeout(timer)
            controller.signal.removeEventListener('abort', aborted)
          }
          const aborted = () => {
            finish()
            reject(new DOMException('Cancelled', 'AbortError'))
          }
          const timer = setTimeout(() => {
            finish()
            controller.abort()
            reject(new DOMException('Camera timeout', 'TimeoutError'))
          }, 15_000)
          controller.signal.addEventListener('abort', aborted, { once: true })
          void streamPromise.then(
            (value) => {
              finish()
              if (controller.signal.aborted || version !== generation)
                value.getTracks().forEach((track) => track.stop())
              resolve(value)
            },
            (error: unknown) => {
              finish()
              reject(error)
            },
          )
        })
        stream.getTracks().forEach((track) => track.stop())
        if (version === generation && !controller.signal.aborted)
          state.camera.permission = 'granted'
      }
    } catch (error) {
      if (
        version === generation &&
        (!controller.signal.aborted || (error instanceof Error && error.name === 'TimeoutError'))
      )
        state[kind].permission =
          error instanceof Error && error.name === 'NotAllowedError' ? 'denied' : 'error'
    } finally {
      if (active === controller) active = null
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
    scanQr: platform.scanQr,
    dispose() {
      cancel()
      cleanups.clear()
    },
  }
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
    kind === 'location'
      ? !globalThis.navigator?.geolocation
      : !globalThis.navigator?.mediaDevices?.getUserMedia
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
