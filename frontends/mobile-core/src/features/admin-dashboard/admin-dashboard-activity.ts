export const ADMIN_ACTIVITY_METRICS = [
  'positiveMarks',
  'geoMarks',
  'mapOpens',
  'requests',
  'homeworkPublications',
] as const
export type AdminActivityMetricCode = (typeof ADMIN_ACTIVITY_METRICS)[number]
export type AdminActivityPeriod = 'today' | 'all_time'
export type AdminActivityBucket = 'day' | 'week'
export type AdminActivityAvailability = 'AVAILABLE' | 'UNAVAILABLE' | 'INSUFFICIENT_HISTORY'
export interface AdminActivityRange {
  readonly from: string | null
  readonly to: string
}
export interface AdminActivityComparison {
  readonly state: AdminActivityAvailability
  readonly range: { readonly from: string; readonly to: string }
  readonly previousValue: number | null
  readonly absoluteDelta: number | null
  readonly relativeDeltaPercent: number | null
  readonly previousPercent: number | null
  readonly percentPointDelta: number | null
}
export interface AdminActivityMetric {
  readonly state: AdminActivityAvailability
  readonly value: number | null
  readonly numerator: number | null
  readonly denominator: number | null
  readonly percent: number | null
  readonly coverage: {
    readonly kind: 'RETAINED_DOMAIN_HISTORY' | 'RAW_RETENTION' | 'BOUNDED_CAPTURE'
    readonly from: string | null
  }
  readonly reason: null | 'DEPENDENCY_UNAVAILABLE' | 'INSUFFICIENT_HISTORY'
  readonly comparison: AdminActivityComparison | null
}
export type AdminActivityMetrics = Readonly<Record<AdminActivityMetricCode, AdminActivityMetric>>
export interface AdminDashboardActivity {
  readonly observedAt: string
  readonly timeZone: 'Europe/Moscow'
  readonly period: AdminActivityPeriod
  readonly bucket: AdminActivityBucket
  readonly historyScope: 'RETAINED_DOMAIN_HISTORY'
  readonly consistency: 'EVENTUAL_ATTENDANCE_PROJECTION'
  readonly summary: { readonly range: AdminActivityRange; readonly metrics: AdminActivityMetrics }
  readonly series: readonly {
    readonly range: { readonly from: string; readonly to: string }
    readonly metrics: AdminActivityMetrics
  }[]
}

function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw invalid()
  return value as Record<string, unknown>
}
function invalid(): Error {
  return new Error('Сервер вернул некорректные данные активности.')
}
function oneOf<T extends string>(value: unknown, values: readonly T[]): T {
  if (typeof value !== 'string' || !values.includes(value as T)) throw invalid()
  return value as T
}
function instant(value: unknown): string {
  if (
    typeof value !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/.test(value) ||
    !Number.isFinite(Date.parse(value))
  )
    throw invalid()
  if (new Date(value).toISOString().slice(0, 19) !== value.slice(0, 19)) throw invalid()
  return value
}
function nullableInstant(value: unknown): string | null {
  return value === null ? null : instant(value)
}
function nullableInteger(value: unknown, signed = false): number | null {
  if (value === null) return null
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || (!signed && value < 0))
    throw invalid()
  return value
}
function nullableNumber(value: unknown, percent = false): number | null {
  if (value === null) return null
  if (
    typeof value !== 'number' ||
    !Number.isFinite(value) ||
    (percent && (value < 0 || value > 100))
  )
    throw invalid()
  return value
}
function range(value: unknown, observedAt: string, nullableFrom: boolean): AdminActivityRange {
  const source = record(value)
  const from = nullableFrom ? nullableInstant(source.from) : instant(source.from)
  const to = instant(source.to)
  if (
    Date.parse(to) > Date.parse(observedAt) ||
    (from !== null && Date.parse(from) > Date.parse(to))
  )
    throw invalid()
  return { from, to }
}
function comparison(
  value: unknown,
  observedAt: string,
  geo: boolean,
): AdminActivityComparison | null {
  if (value === null) return null
  const source = record(value)
  const state = oneOf(source.state, ['AVAILABLE', 'UNAVAILABLE', 'INSUFFICIENT_HISTORY'] as const)
  const period = range(source.range, observedAt, false)
  const previousValue = nullableInteger(source.previousValue)
  const absoluteDelta = nullableInteger(source.absoluteDelta, true)
  const relativeDeltaPercent = nullableNumber(source.relativeDeltaPercent)
  const previousPercent = nullableNumber(source.previousPercent, true)
  const percentPointDelta = nullableNumber(source.percentPointDelta)
  if (
    (state === 'AVAILABLE' && previousValue === null) ||
    (previousValue === 0 && relativeDeltaPercent !== null) ||
    (!geo && (previousPercent !== null || percentPointDelta !== null))
  )
    throw invalid()
  return {
    state,
    range: { from: period.from!, to: period.to },
    previousValue,
    absoluteDelta,
    relativeDeltaPercent,
    previousPercent,
    percentPointDelta,
  }
}
function metrics(
  value: unknown,
  observedAt: string,
  allowComparison: boolean,
): AdminActivityMetrics {
  const source = record(value)
  return Object.fromEntries(
    ADMIN_ACTIVITY_METRICS.map((code) => {
      const item = record(source[code])
      const state = oneOf(item.state, ['AVAILABLE', 'UNAVAILABLE', 'INSUFFICIENT_HISTORY'] as const)
      const count = nullableInteger(item.value)
      const numerator = nullableInteger(item.numerator)
      const denominator = nullableInteger(item.denominator)
      const percent = nullableNumber(item.percent, true)
      const cover = record(item.coverage)
      const kind = oneOf(cover.kind, [
        'RETAINED_DOMAIN_HISTORY',
        'RAW_RETENTION',
        'BOUNDED_CAPTURE',
      ] as const)
      const from = nullableInstant(cover.from)
      const reason =
        item.reason === null
          ? null
          : oneOf(item.reason, ['DEPENDENCY_UNAVAILABLE', 'INSUFFICIENT_HISTORY'] as const)
      const previous = comparison(item.comparison, observedAt, code === 'geoMarks')
      if (
        (!allowComparison && previous !== null) ||
        (from !== null && Date.parse(from) > Date.parse(observedAt))
      )
        throw invalid()
      if (
        (state === 'AVAILABLE' && (count === null || reason !== null)) ||
        (state !== 'AVAILABLE' &&
          (count !== null ||
            numerator !== null ||
            denominator !== null ||
            percent !== null ||
            reason === null))
      )
        throw invalid()
      if (state === 'AVAILABLE' && code === 'geoMarks') {
        if (
          numerator === null ||
          denominator === null ||
          count !== numerator ||
          numerator > denominator ||
          (denominator === 0 && percent !== null) ||
          (denominator > 0 && percent === null)
        )
          throw invalid()
      } else if (
        code !== 'geoMarks' &&
        (numerator !== null || denominator !== null || percent !== null)
      )
        throw invalid()
      return [
        code,
        {
          state,
          value: count,
          numerator,
          denominator,
          percent,
          coverage: { kind, from },
          reason,
          comparison: previous,
        },
      ]
    }),
  ) as unknown as AdminActivityMetrics
}

/** Preserve every unknown/null and reject unsafe JSON int64 before it can become a rounded product number. */
export function normalizeAdminDashboardActivity(
  value: unknown,
  expectedPeriod: AdminActivityPeriod,
  expectedBucket: AdminActivityBucket,
): AdminDashboardActivity {
  const source = record(value)
  const observedAt = instant(source.observedAt)
  const timeZone = oneOf(source.timeZone, ['Europe/Moscow'] as const)
  const period = oneOf(source.period, ['today', 'all_time'] as const)
  const bucket = oneOf(source.bucket, ['day', 'week'] as const)
  const historyScope = oneOf(source.historyScope, ['RETAINED_DOMAIN_HISTORY'] as const)
  const consistency = oneOf(source.consistency, ['EVENTUAL_ATTENDANCE_PROJECTION'] as const)
  if (period !== expectedPeriod || bucket !== expectedBucket) throw invalid()
  const summary = record(source.summary)
  const summaryRange = range(summary.range, observedAt, period === 'all_time')
  if (period === 'all_time' && summaryRange.from !== null) throw invalid()
  if (!Array.isArray(source.series) || source.series.length > (bucket === 'day' ? 14 : 12))
    throw invalid()
  let previousEnd = -Infinity
  let previousStart = -Infinity
  const series = source.series.map((item) => {
    const point = record(item)
    const pointRange = range(point.range, observedAt, false)
    if (Date.parse(pointRange.from!) < previousEnd || Date.parse(pointRange.from!) <= previousStart)
      throw invalid()
    previousStart = Date.parse(pointRange.from!)
    previousEnd = Date.parse(pointRange.to)
    return {
      range: { from: pointRange.from!, to: pointRange.to },
      metrics: metrics(point.metrics, observedAt, false),
    }
  })
  return {
    observedAt,
    timeZone,
    period,
    bucket,
    historyScope,
    consistency,
    summary: {
      range: summaryRange,
      metrics: metrics(summary.metrics, observedAt, period === 'today'),
    },
    series,
  }
}
