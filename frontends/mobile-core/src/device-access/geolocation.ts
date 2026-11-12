import type { StudentToday } from '../api/types'
import { browserDeviceContextAllows } from './browser-policy'

/** Acquisition hints never override the server's final attendance decision. */
export const GEO_BUDGET_MS = 28_000
export const GEO_FRESH_MS = 30_000
export const GEO_MAX_ATTEMPTS = 3
export type GeoFailure =
  | 'PERMISSION_DENIED'
  | 'POSITION_UNAVAILABLE'
  | 'TIMEOUT'
  | 'UNSUPPORTED'
  | 'APP_DISABLED'
export interface GeoSample {
  latitude: number
  longitude: number
  accuracy: number | null
  /** Telegram LocationData has no fix timestamp. */
  timestamp: number | null
  receivedAt: number
}
export interface GeoSource {
  readonly source: 'BROWSER' | 'TMA'
  watch(sample: (sample: GeoSample) => void, failed: (reason: GeoFailure) => void): () => void
}
export type GeoGeofence = NonNullable<StudentToday['checkinGeofence']>
export interface GeoResult {
  source: GeoSource['source']
  sample: GeoSample | null
  sampleCount: number
  duration: number
  reason: GeoFailure | 'WITHIN_GEOFENCE' | 'ATTEMPTS_EXHAUSTED' | 'BUDGET_EXPIRED'
}
export type GeoAcquisitionTerminal = Pick<GeoResult, 'source' | 'duration' | 'sampleCount'> & {
  reason: GeoResult['reason'] | 'CANCELLED'
}
export type GeoTerminalHook = (event: GeoAcquisitionTerminal) => void | Promise<void>
export function notifyGeoTerminal(
  hook: GeoTerminalHook | undefined,
  event: GeoAcquisitionTerminal,
): void {
  if (!hook) return
  try {
    void Promise.resolve(hook(Object.freeze({ ...event }))).catch(() => undefined)
  } catch {
    /* Optional only. */
  }
}
export function isFreshBrowserFix(timestamp: number | null, now = Date.now()): boolean {
  return (
    timestamp !== null &&
    Number.isFinite(timestamp) &&
    now - timestamp <= GEO_FRESH_MS &&
    timestamp <= now + 1_000
  )
}
function valid(sample: GeoSample, source: GeoSource['source']): boolean {
  return (
    Number.isFinite(sample.latitude) &&
    Math.abs(sample.latitude) <= 90 &&
    Number.isFinite(sample.longitude) &&
    Math.abs(sample.longitude) <= 180 &&
    (source === 'TMA' || isFreshBrowserFix(sample.timestamp))
  )
}
export function validGeofence(policy: GeoGeofence | null | undefined): policy is GeoGeofence {
  return (
    !!policy &&
    Number.isFinite(policy.latitude) &&
    Math.abs(policy.latitude) <= 90 &&
    Number.isFinite(policy.longitude) &&
    Math.abs(policy.longitude) <= 180 &&
    Number.isFinite(policy.radiusMeters) &&
    policy.radiusMeters > 0
  )
}
export function withinGeofence(sample: GeoSample, policy: GeoGeofence): boolean {
  const rad = Math.PI / 180
  const dlat = (sample.latitude - policy.latitude) * rad
  const dlon = (sample.longitude - policy.longitude) * rad
  const h =
    Math.sin(dlat / 2) ** 2 +
    Math.cos(sample.latitude * rad) * Math.cos(policy.latitude * rad) * Math.sin(dlon / 2) ** 2
  const meters = 6_371_000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0, 1 - h)))
  return (
    meters <= policy.radiusMeters ||
    Math.abs(meters - policy.radiusMeters) <=
      Number.EPSILON * Math.max(meters, policy.radiusMeters) * 8
  )
}
interface Reading {
  sample: GeoSample | null
  reason: GeoFailure | 'BUDGET_EXPIRED' | null
}
/** One fresh reading. Settings uses this directly, without coordinator telemetry or retries. */
export function readGeolocationOnce(
  source: GeoSource,
  options: {
    signal?: AbortSignal
    budgetMs?: number
    afterTimestamp?: number | null
  } = {},
): Promise<Reading> {
  return new Promise((resolve, reject) => {
    const deadline =
      performance.now() + Math.max(0, Math.min(GEO_BUDGET_MS, options.budgetMs ?? GEO_BUDGET_MS))
    let done = false
    let stop = () => {}
    const finish = (reading: Reading) => {
      if (done) return
      done = true
      clearTimeout(timer)
      options.signal?.removeEventListener('abort', abort)
      stop()
      resolve(reading)
    }
    const abort = () => {
      if (done) return
      done = true
      clearTimeout(timer)
      options.signal?.removeEventListener('abort', abort)
      stop()
      reject(new DOMException('Geolocation cancelled', 'AbortError'))
    }
    const timer = setTimeout(
      () => finish({ sample: null, reason: 'BUDGET_EXPIRED' }),
      Math.max(0, Math.min(GEO_BUDGET_MS, options.budgetMs ?? GEO_BUDGET_MS)),
    )
    options.signal?.addEventListener('abort', abort, { once: true })
    if (options.signal?.aborted) return abort()
    try {
      stop = source.watch(
        (sample) => {
          if (!done && performance.now() > deadline) {
            finish({ sample: null, reason: 'BUDGET_EXPIRED' })
            return
          }
          if (done || !valid(sample, source.source)) return
          if (
            source.source === 'BROWSER' &&
            options.afterTimestamp != null &&
            (sample.timestamp ?? -Infinity) <= options.afterTimestamp
          )
            return
          finish({ sample, reason: null })
        },
        (reason) => finish({ sample: null, reason }),
      )
      if (done) stop()
    } catch {
      finish({ sample: null, reason: 'POSITION_UNAVAILABLE' })
    }
  })
}
/** Initial reading plus two automatic retries, one shared deadline and one terminal event. */
export async function acquireGeolocation(
  source: GeoSource,
  options: {
    signal?: AbortSignal
    progress?: (stage: 'determining' | 'refining') => void
    budgetMs?: number
    geofence?: GeoGeofence | null
    terminal?: GeoTerminalHook
    selectSource?: () => GeoSource | Promise<GeoSource>
  } = {},
): Promise<GeoResult> {
  const started = performance.now()
  const elapsed = () => Math.max(0, performance.now() - started)
  const budget = Math.max(0, Math.min(GEO_BUDGET_MS, options.budgetMs ?? GEO_BUDGET_MS))
  let latest: GeoSample | null = null
  let count = 0
  let reason: GeoResult['reason'] = 'POSITION_UNAVAILABLE'
  try {
    for (let attempt = 0; attempt < GEO_MAX_ATTEMPTS; attempt++) {
      if (options.signal?.aborted) throw new DOMException('Geolocation cancelled', 'AbortError')
      let remaining = budget - elapsed()
      if (remaining <= 0) {
        reason = 'BUDGET_EXPIRED'
        break
      }
      try {
        options.progress?.(attempt === 0 ? 'determining' : 'refining')
      } catch {
        /* Presentation only. */
      }
      if (attempt === 0 && options.selectSource) {
        const selected = options.selectSource()
        source = selected instanceof Promise ? await selected : selected
        if (options.signal?.aborted) throw new DOMException('Geolocation cancelled', 'AbortError')
        remaining = budget - elapsed()
        if (remaining <= 0) {
          reason = 'BUDGET_EXPIRED'
          break
        }
      }
      const reading = await readGeolocationOnce(source, {
        ...(options.signal ? { signal: options.signal } : {}),
        budgetMs: remaining,
        afterTimestamp: latest?.timestamp ?? null,
      })
      if (reading.sample) count++
      if (options.signal?.aborted) throw new DOMException('Geolocation cancelled', 'AbortError')
      if (reading.sample) {
        latest = reading.sample
        reason = 'ATTEMPTS_EXHAUSTED'
        if (validGeofence(options.geofence) && withinGeofence(latest, options.geofence)) {
          reason = 'WITHIN_GEOFENCE'
          break
        }
      } else {
        reason = reading.reason ?? 'POSITION_UNAVAILABLE'
        if (
          reason === 'PERMISSION_DENIED' ||
          reason === 'UNSUPPORTED' ||
          reason === 'APP_DISABLED'
        ) {
          latest = null
          break
        }
        if (reason === 'BUDGET_EXPIRED') break
      }
    }
    if (latest && !valid(latest, source.source)) {
      latest = null
      if (reason !== 'BUDGET_EXPIRED') reason = 'POSITION_UNAVAILABLE'
    }
    if (latest && reason !== 'WITHIN_GEOFENCE' && reason !== 'BUDGET_EXPIRED')
      reason = 'ATTEMPTS_EXHAUSTED'
    const duration = Math.min(60_000, Math.floor(elapsed()))
    notifyGeoTerminal(options.terminal, {
      source: source.source,
      reason,
      duration,
      sampleCount: count,
    })
    return { source: source.source, sample: latest, reason, duration, sampleCount: count }
  } catch (error) {
    notifyGeoTerminal(options.terminal, {
      source: source.source,
      reason: 'CANCELLED',
      duration: Math.min(60_000, Math.floor(elapsed())),
      sampleCount: count,
    })
    throw error
  }
}
export function browserGeoSource(
  geo: Geolocation | undefined = globalThis.navigator?.geolocation,
): GeoSource {
  return {
    source: 'BROWSER',
    watch(sample, failed) {
      if (!geo || !browserDeviceContextAllows('geolocation')) {
        failed('UNSUPPORTED')
        return () => {}
      }
      const id = geo.watchPosition(
        (position) =>
          sample({
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            accuracy: position.coords.accuracy,
            timestamp: position.timestamp,
            receivedAt: Date.now(),
          }),
        (error) =>
          failed(
            error.code === 1
              ? 'PERMISSION_DENIED'
              : error.code === 3
                ? 'TIMEOUT'
                : 'POSITION_UNAVAILABLE',
          ),
        { enableHighAccuracy: true, maximumAge: 0, timeout: GEO_BUDGET_MS },
      )
      return () => geo.clearWatch(id)
    },
  }
}
