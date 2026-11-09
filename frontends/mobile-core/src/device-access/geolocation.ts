/** Provisional device policy; server geofence and eligibility remain authoritative. */
export const GEO_BUDGET_MS = 28_000
export const GEO_FRESH_MS = 30_000
export const GEO_GOOD_METERS = 50
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
  /** Native Telegram LocationData has no fix timestamp. */
  timestamp: number | null
  receivedAt: number
}
export interface GeoSource {
  readonly source: 'BROWSER' | 'TMA'
  watch(sample: (sample: GeoSample) => void, failed: (reason: GeoFailure) => void): () => void
}
export interface GeoResult {
  source: GeoSource['source']
  sample: GeoSample | null
  sampleCount: number
  duration: number
  reason: GeoFailure | 'QUALITY_REACHED' | 'BUDGET_EXPIRED'
  needsReview: boolean
  conflicting: boolean
}
export type GeoAcquisitionTerminal = Pick<GeoResult, 'source' | 'duration' | 'sampleCount'> & {
  reason: GeoResult['reason'] | 'CANCELLED'
}
export type GeoTerminalHook = (event: GeoAcquisitionTerminal) => void | Promise<void>

/** Only bounded, non-identifying terminal values leave the sensor owner. Never await telemetry. */
export function notifyGeoTerminal(
  hook: GeoTerminalHook | undefined,
  event: GeoAcquisitionTerminal,
): void {
  if (!hook) return
  try {
    void Promise.resolve(hook(Object.freeze({ ...event }))).catch(() => undefined)
  } catch {
    /* Optional reporting must not affect acquisition or session state. */
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

function valid(sample: GeoSample, source: GeoSource['source'], now: number): boolean {
  return (
    Number.isFinite(sample.latitude) &&
    Math.abs(sample.latitude) <= 90 &&
    Number.isFinite(sample.longitude) &&
    Math.abs(sample.longitude) <= 180 &&
    (source === 'TMA' || isFreshBrowserFix(sample.timestamp, now))
  )
}
function accuracy(sample: GeoSample): number {
  return sample.accuracy !== null &&
    Number.isFinite(sample.accuracy) &&
    sample.accuracy >= 0 &&
    sample.accuracy <= 10_000_000
    ? sample.accuracy
    : Infinity
}
function distance(a: GeoSample, b: GeoSample): number {
  const rad = Math.PI / 180
  const dlat = (b.latitude - a.latitude) * rad
  const dlon = (b.longitude - a.longitude) * rad
  const h =
    Math.sin(dlat / 2) ** 2 +
    Math.cos(a.latitude * rad) * Math.cos(b.latitude * rad) * Math.sin(dlon / 2) ** 2
  return 6_371_000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0, 1 - h)))
}

/** One bounded source, deterministic deadline, no campus-distance selection or averaging. */
export function acquireGeolocation(
  source: GeoSource,
  options: {
    signal?: AbortSignal
    progress?: (stage: 'determining' | 'refining') => void
    budgetMs?: number
    terminal?: GeoTerminalHook
  } = {},
): Promise<GeoResult> {
  return new Promise((resolve, reject) => {
    const started = Date.now()
    let done = false
    let stop = () => {}
    let best: GeoSample | null = null
    const samples: GeoSample[] = []
    let count = 0
    let conflicting = false
    const finish = (reason: GeoResult['reason']) => {
      if (done) return
      done = true
      clearTimeout(timer)
      options.signal?.removeEventListener('abort', abort)
      stop()
      best = null
      if (reason !== 'PERMISSION_DENIED' && reason !== 'UNSUPPORTED' && reason !== 'APP_DISABLED')
        for (const candidate of samples) {
          if (!valid(candidate, source.source, Date.now())) continue
          if (
            !best ||
            accuracy(candidate) < accuracy(best) ||
            (accuracy(candidate) === accuracy(best) &&
              (candidate.timestamp ?? candidate.receivedAt) > (best.timestamp ?? best.receivedAt))
          )
            best = candidate
        }
      const duration = Math.min(60_000, Math.max(0, Date.now() - started))
      notifyGeoTerminal(options.terminal, {
        source: source.source,
        reason,
        duration,
        sampleCount: count,
      })
      resolve({
        source: source.source,
        sample: best,
        sampleCount: count,
        duration,
        reason,
        needsReview: conflicting || !best || accuracy(best) > GEO_GOOD_METERS,
        conflicting,
      })
    }
    const abort = () => {
      if (done) return
      done = true
      clearTimeout(timer)
      stop()
      options.signal?.removeEventListener('abort', abort)
      notifyGeoTerminal(options.terminal, {
        source: source.source,
        reason: 'CANCELLED',
        duration: Math.min(60_000, Math.max(0, Date.now() - started)),
        sampleCount: count,
      })
      reject(new DOMException('Определение геолокации отменено', 'AbortError'))
    }
    const timer = setTimeout(
      () => finish('BUDGET_EXPIRED'),
      Math.min(GEO_BUDGET_MS, options.budgetMs ?? GEO_BUDGET_MS),
    )
    options.signal?.addEventListener('abort', abort, { once: true })
    if (options.signal?.aborted) return abort()
    options.progress?.('determining')
    try {
      stop = source.watch(
        (sample) => {
          if (done || !valid(sample, source.source, Date.now())) return
          count = Math.min(64, count + 1)
          samples.push(sample)
          if (samples.length > 64) samples.shift()
          if (best && !valid(best, source.source, Date.now())) best = null
          if (
            best &&
            Number.isFinite(accuracy(best)) &&
            Number.isFinite(accuracy(sample)) &&
            distance(best, sample) > Math.max(100, accuracy(best) + accuracy(sample))
          )
            conflicting = true
          if (
            !best ||
            accuracy(sample) < accuracy(best) ||
            (accuracy(sample) === accuracy(best) &&
              (sample.timestamp ?? sample.receivedAt) > (best.timestamp ?? best.receivedAt))
          )
            best = sample
          options.progress?.('refining')
          if (accuracy(best) <= GEO_GOOD_METERS && !conflicting) finish('QUALITY_REACHED')
        },
        (reason) => finish(reason),
      )
      // Some native/fixture implementations invoke the callback synchronously.
      if (done) stop()
    } catch {
      finish('POSITION_UNAVAILABLE')
    }
  })
}

export function browserGeoSource(
  geo: Geolocation | undefined = globalThis.navigator?.geolocation,
): GeoSource {
  return {
    source: 'BROWSER',
    watch(sample, failed) {
      if (!geo) {
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
