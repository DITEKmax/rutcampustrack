import type { StatisticsSeriesPoint } from '../statistics/statistics-view-model'
import type { HeadmanStatsTrendPoint, HeadmanStatsStudentRow } from './headman-stats-client'

/** The two server counters share a denominator; never interpolate dates or marks. */
export function headmanStatsSeries(
  points: readonly HeadmanStatsTrendPoint[],
): readonly StatisticsSeriesPoint[] {
  return points.map((point) => {
    const held = point.present.denominator
    const present = point.present.numerator
    const combined = point.presentOrExcused.numerator
    const valid =
      held > 0 &&
      held === point.presentOrExcused.denominator &&
      present >= 0 &&
      combined >= present &&
      combined <= held
    const metric = (count: number) => ({
      count: valid ? count : 0,
      percent: valid ? (count / held) * 100 : null,
    })
    return {
      id: point.key,
      label: point.label,
      dateFrom: point.from,
      dateTo: point.to,
      state: valid ? 'DATA' : 'NO_DATA',
      metrics: {
        present: metric(present),
        presentOrExcused: metric(combined),
        excused: metric(combined - present),
        absent: metric(held - combined),
        held: valid ? held : 0,
        planned: valid ? held : 0,
      },
    }
  })
}

export function headmanStatsSources(rows: readonly Pick<HeadmanStatsStudentRow, 'sources'>[]) {
  const sources = rows.reduce(
    (result, row) => ({
      geo: result.geo + row.sources.studentGeo,
      manual: result.manual + row.sources.manualRequest,
      failure: result.failure + row.sources.autoAfterGeoFailure,
      headman: result.headman + row.sources.headmanManual,
    }),
    { geo: 0, manual: 0, failure: 0, headman: 0 },
  )
  const total = Object.values(sources).reduce((sum, count) => sum + count, 0)
  return [
    { label: 'Гео', count: sources.geo },
    { label: 'Тикет', count: sources.manual },
    { label: 'Сбой', count: sources.failure },
    { label: 'Староста', count: sources.headman },
  ].map((item) => ({ ...item, percent: total > 0 ? (item.count / total) * 100 : null }))
}

export function headmanStatsPercent(metric: {
  denominator: number
  percent: number | null
}): string {
  return metric.denominator > 0 && metric.percent !== null && Number.isFinite(metric.percent)
    ? `${Math.round(metric.percent)}%`
    : '—'
}

export function headmanStatsSemesterDates(from: string | null, to: string | null): string {
  if (!from || !to) return 'Даты семестра не указаны'
  const format = (value: string) =>
    new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', timeZone: 'UTC' }).format(
      new Date(value + 'T12:00:00Z'),
    )
  return `${format(from)} — ${format(to)}`
}
