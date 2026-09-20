export type StatisticsTheme = 'dark' | 'light'

export type StatisticsReadState<T> =
  | { readonly status: 'loading' }
  | { readonly status: 'ready'; readonly data: T }
  | { readonly status: 'empty' }
  | { readonly status: 'error'; readonly code: string; readonly message: string; readonly retryable: boolean }
  | { readonly status: 'forbidden'; readonly reason: string }
  | { readonly status: 'offline' }

export type StatisticsGraphRange = 'days' | 'weeks'
export type StatisticsLessonType = 'LECTURE' | 'PRACTICE' | 'LAB'
export const STATISTICS_TYPE_ORDER = ['LECTURE', 'PRACTICE', 'LAB'] as const satisfies readonly StatisticsLessonType[]

export interface StatisticsMetricValue {
  readonly count: number
  readonly percent: number | null
}

export interface StatisticsMetricSet {
  readonly present: StatisticsMetricValue
  readonly presentOrExcused: StatisticsMetricValue
  readonly excused: StatisticsMetricValue
  readonly absent: StatisticsMetricValue
  readonly held: number
  readonly planned: number
}

export interface StatisticsOwnRank {
  readonly position: number | null
  readonly participantCount: number
  readonly available: boolean
}

export interface StatisticsSeriesPoint {
  readonly id: string
  readonly label: string
  readonly dateFrom: string
  readonly dateTo: string
  readonly state: 'DATA' | 'NO_DATA' | 'FUTURE'
  readonly metrics: StatisticsMetricSet
}

export type StatisticsHistoryStatus = 'PRESENT' | 'ABSENT' | 'EXCUSED' | 'FUTURE' | 'NO_DATA'

export interface StatisticsHistorySegment {
  readonly id: string
  readonly status: StatisticsHistoryStatus
}

export interface StatisticsTypeCardData {
  readonly type: StatisticsLessonType
  readonly metrics: StatisticsMetricSet
  readonly history: readonly StatisticsHistorySegment[]
}

export interface StatisticsSubjectSummary {
  readonly id: string
  readonly name: string
  readonly metrics: StatisticsMetricSet
}

export interface StatisticsOverviewData {
  readonly metrics: StatisticsMetricSet
  readonly ownRank: StatisticsOwnRank
  readonly semesterSeries: readonly StatisticsSeriesPoint[]
  readonly subjects: readonly StatisticsSubjectSummary[]
}

export interface StatisticsSubjectDetailData {
  readonly subjectId: string
  readonly name: string
  readonly availableTypes: readonly StatisticsLessonType[]
  readonly selectedTypes: readonly StatisticsLessonType[]
  readonly selectedAggregate: StatisticsMetricSet
  readonly series: readonly StatisticsSeriesPoint[]
  readonly typeCards: readonly StatisticsTypeCardData[]
}

export function displayPercent(percent: number | null): string {
  return percent === null || !Number.isFinite(percent) ? '—' : `${Math.round(percent)}%`
}

export function statisticsTypeLabel(type: StatisticsLessonType): string {
  const labels: Record<StatisticsLessonType, string> = {
    LECTURE: 'Лекция',
    PRACTICE: 'Практика',
    LAB: 'Лабораторная',
  }
  return labels[type]
}

export function historyStatusLabel(status: StatisticsHistoryStatus): string {
  const labels: Record<StatisticsHistoryStatus, string> = {
    PRESENT: '+',
    ABSENT: 'н',
    EXCUSED: 'у',
    FUTURE: 'будет',
    NO_DATA: 'нет данных',
  }
  return labels[status]
}

export function historyStatusAccessibleLabel(status: StatisticsHistoryStatus): string {
  const labels: Record<StatisticsHistoryStatus, string> = {
    PRESENT: 'присутствовал',
    ABSENT: 'отсутствовал',
    EXCUSED: 'уважительная причина',
    FUTURE: 'будет',
    NO_DATA: 'нет данных',
  }
  return labels[status]
}

export function typeSelectionContains(
  selectedTypes: readonly StatisticsLessonType[],
  type: StatisticsLessonType,
): boolean {
  return selectedTypes.includes(type)
}

export function toggleTypeSelection(
  selectedTypes: readonly StatisticsLessonType[],
  type: StatisticsLessonType,
): readonly StatisticsLessonType[] {
  if (selectedTypes.includes(type)) {
    if (selectedTypes.length === 1) return selectedTypes
    return STATISTICS_TYPE_ORDER.filter((candidate) => candidate !== type && selectedTypes.includes(candidate))
  }
  return STATISTICS_TYPE_ORDER.filter((candidate) => candidate === type || selectedTypes.includes(candidate))
}

export function seriesForRange(
  data: StatisticsSubjectDetailData,
  range: StatisticsGraphRange = 'weeks',
): readonly StatisticsSeriesPoint[] {
  // The host supplies an already bucketed projection. IDs are opaque and are
  // never parsed to infer the selected range or drop a valid server point.
  void range
  return data.series
}
