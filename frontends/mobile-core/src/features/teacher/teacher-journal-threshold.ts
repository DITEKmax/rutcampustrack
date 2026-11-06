import type { TeacherStatsMetric } from './teacher-client'

export type JournalThreshold =
  | { state: 'empty' | 'invalid'; value: null }
  | { state: 'valid'; value: number }

/** Local display preference only; never changes the authoritative metric or query. */
export function journalThreshold(input: string): JournalThreshold {
  const text = input.trim()
  if (!text) return { state: 'empty', value: null }
  if (!/^(?:\d+(?:[.,]\d*)?|[.,]\d+)$/.test(text)) return { state: 'invalid', value: null }
  const value = Number(text.replace(',', '.'))
  return Number.isFinite(value) && value >= 0 && value <= 100
    ? { state: 'valid', value }
    : { state: 'invalid', value: null }
}

export function journalThresholdResult(
  metric: TeacherStatsMetric | null | undefined,
  threshold: JournalThreshold,
): 'meets' | 'below' | null {
  if (
    threshold.state !== 'valid' ||
    !metric ||
    !Number.isFinite(metric.denominator) ||
    metric.denominator <= 0 ||
    !Number.isFinite(metric.percent) ||
    metric.percent < 0 ||
    metric.percent > 100
  )
    return null
  return metric.percent >= threshold.value ? 'meets' : 'below'
}
