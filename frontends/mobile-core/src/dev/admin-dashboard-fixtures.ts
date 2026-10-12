import {
  ADMIN_ACTIVITY_METRICS,
  type AdminActivityBucket,
  type AdminActivityMetric,
  type AdminActivityMetricCode,
  type AdminActivityMetrics,
  type AdminActivityPeriod,
  type AdminDashboardActivity,
} from '../features/admin-dashboard/admin-dashboard-activity'

export type AdminActivityFixtureVariant =
  | 'ready'
  | 'empty'
  | 'partial'
  | 'unavailable'
  | 'coverage-empty'
  | 'geo-zero'
const observedAt = '2026-10-08T09:30:00Z'
const midnight = Date.parse('2026-10-07T21:00:00Z')
const DAY = 86_400_000
const iso = (value: number) => new Date(value).toISOString()
function metric(
  code: AdminActivityMetricCode,
  value: number,
  denominator: number,
  variant: AdminActivityFixtureVariant,
  index: number,
  summary: boolean,
  period: AdminActivityPeriod,
): AdminActivityMetric {
  const unavailable =
    variant === 'unavailable' ||
    (variant === 'partial' && code === 'requests' && (summary || index === 6))
  const insufficient =
    code === 'mapOpens' &&
    (variant === 'coverage-empty' || (variant === 'partial' && index < 5 && !summary))
  const state = unavailable ? 'UNAVAILABLE' : insufficient ? 'INSUFFICIENT_HISTORY' : 'AVAILABLE'
  const zero =
    variant === 'empty' ||
    (variant === 'geo-zero' && (code === 'positiveMarks' || code === 'geoMarks'))
  const count = zero ? 0 : value
  const divisor = zero ? 0 : denominator
  const geo = code === 'geoMarks'
  const percent = geo && divisor > 0 ? Math.round((count / divisor) * 10000) / 100 : null
  const previousValue = zero
    ? 0
    : code === 'mapOpens'
      ? count + 7
      : code === 'homeworkPublications'
        ? count
        : Math.max(0, count - 7)
  const previousPercent = geo && !zero ? Math.max(0, percent! - 4.5) : null
  return {
    state,
    value: state === 'AVAILABLE' ? count : null,
    numerator: state === 'AVAILABLE' && geo ? count : null,
    denominator: state === 'AVAILABLE' && geo ? divisor : null,
    percent: state === 'AVAILABLE' && geo ? percent : null,
    coverage: {
      kind: code === 'mapOpens' ? 'BOUNDED_CAPTURE' : 'RETAINED_DOMAIN_HISTORY',
      from: code === 'mapOpens' ? '2026-10-06T21:00:00Z' : null,
    },
    reason: unavailable ? 'DEPENDENCY_UNAVAILABLE' : insufficient ? 'INSUFFICIENT_HISTORY' : null,
    comparison:
      summary && period === 'today'
        ? {
            state,
            range: { from: iso(midnight - DAY), to: iso(Date.parse(observedAt) - DAY) },
            previousValue: state === 'AVAILABLE' ? previousValue : null,
            absoluteDelta: state === 'AVAILABLE' && !geo ? count - previousValue : null,
            relativeDeltaPercent:
              state === 'AVAILABLE' && !geo && previousValue > 0
                ? Math.round(((count - previousValue) / previousValue) * 10000) / 100
                : null,
            previousPercent: state === 'AVAILABLE' ? previousPercent : null,
            percentPointDelta:
              state === 'AVAILABLE' && geo && percent !== null && previousPercent !== null
                ? percent - previousPercent
                : null,
          }
        : null,
  }
}
function metrics(
  variant: AdminActivityFixtureVariant,
  index: number,
  summary: boolean,
  period: AdminActivityPeriod,
): AdminActivityMetrics {
  const total = summary
    ? 247
    : [85, 110, 94, 135, 142, 167, 151, 178, 160, 196, 211, 190, 232, 247][index % 14]!
  const values: Record<AdminActivityMetricCode, number> = {
    positiveMarks: total,
    geoMarks: Math.floor(total * 0.67),
    mapOpens: 82 + index * 6,
    requests: 9 + (index % 4),
    homeworkPublications: 14 + (index % 7),
  }
  return Object.fromEntries(
    ADMIN_ACTIVITY_METRICS.map((code) => [
      code,
      metric(code, values[code], total, variant, index, summary, period),
    ]),
  ) as unknown as AdminActivityMetrics
}
/** Explicit synthetic values for real component/client review; never a product fallback. */
export function createAdminActivityFixture(
  period: AdminActivityPeriod = 'today',
  bucket: AdminActivityBucket = 'day',
  variant: AdminActivityFixtureVariant = 'ready',
): AdminDashboardActivity {
  const length = bucket === 'day' ? 14 : 12
  const step = bucket === 'day' ? DAY : 7 * DAY
  const lastStart = bucket === 'day' ? midnight : Date.parse('2026-10-04T21:00:00Z')
  const series = Array.from({ length }, (_, index) => {
    const from = lastStart - (length - index - 1) * step
    const to = Math.min(from + step, Date.parse(observedAt))
    return {
      range: { from: iso(from), to: iso(to) },
      metrics: metrics(variant, index, false, period),
    }
  })
  return {
    observedAt,
    timeZone: 'Europe/Moscow',
    period,
    bucket,
    historyScope: 'RETAINED_DOMAIN_HISTORY',
    consistency: 'EVENTUAL_ATTENDANCE_PROJECTION',
    summary: {
      range: { from: period === 'today' ? iso(midnight) : null, to: observedAt },
      metrics: metrics(variant, 13, true, period),
    },
    series,
  }
}
