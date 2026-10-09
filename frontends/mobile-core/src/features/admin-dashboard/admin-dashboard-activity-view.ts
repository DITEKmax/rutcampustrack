import { ADMIN_ACTIVITY_METRICS, type AdminActivityMetric, type AdminActivityMetricCode, type AdminDashboardActivity } from './admin-dashboard-activity'
import type { AdminActivityChartPoint } from './admin-activity-chart'

export const adminActivityLabels: Readonly<Record<AdminActivityMetricCode, string>> = {
  positiveMarks: 'Выставлено отметок «+»',
  geoMarks: 'Геоотметки среди выставленных «+»',
  mapOpens: 'Просмотры карты',
  requests: 'Создано заявок и тикетов',
  homeworkPublications: 'Опубликовано домашних заданий',
}
export { ADMIN_ACTIVITY_METRICS }
const integer = new Intl.NumberFormat('ru-RU')
const decimal = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 })
const moscowDay = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Moscow', year: 'numeric', month: '2-digit', day: '2-digit' })
const moscowTime = new Intl.DateTimeFormat('ru-RU', { timeZone: 'Europe/Moscow', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
export function adminActivityMoscowDate(instant: string): string { return moscowDay.format(new Date(instant)) }
export function adminActivityObservedAt(instant: string): string { return moscowTime.format(new Date(instant)) + ' (Москва)' }
export function adminActivityValue(metric: AdminActivityMetric, code: AdminActivityMetricCode): string {
  if (metric.state !== 'AVAILABLE') return '—'
  if (code === 'geoMarks') return metric.percent === null ? '—' : decimal.format(metric.percent) + '%'
  return metric.value === null ? '—' : integer.format(metric.value)
}
export function adminActivityExplanation(metric: AdminActivityMetric): string {
  if (metric.state === 'UNAVAILABLE') return 'Данные временно недоступны.'
  if (metric.state === 'INSUFFICIENT_HISTORY') return 'Для этого периода не хватает сохранённой истории.'
  return ''
}
export function adminActivityDelta(metric: AdminActivityMetric, code: AdminActivityMetricCode): { text: string; tone: 'positive' | 'negative' | 'neutral'; description: string } {
  const previous = metric.comparison
  const measured = metric.state === 'AVAILABLE' && (code === 'geoMarks' ? metric.percent !== null : metric.value !== null)
  const delta = measured && previous?.state === 'AVAILABLE' ? code === 'geoMarks' ? previous.percentPointDelta : previous.absoluteDelta : null
  if (delta === null) return { text: '—', tone: 'neutral', description: 'Сравнение недоступно' }
  const text = `${delta > 0 ? '+' : delta < 0 ? '−' : ''}${decimal.format(Math.abs(delta))}${code === 'geoMarks' ? ' п.п.' : ''}`
  return { text, tone: delta > 0 ? 'positive' : delta < 0 ? 'negative' : 'neutral', description: `${text}, относительно вчера к этому времени` }
}
export function adminActivityChartPoints(activity: AdminDashboardActivity, code: AdminActivityMetricCode): readonly AdminActivityChartPoint[] {
  return activity.series.map((item, index) => {
    const metric = item.metrics[code]
    // Bounds are UTC instants; a completed exclusive midnight belongs to the preceding Moscow day.
    const from = adminActivityMoscowDate(item.range.from)
    const to = adminActivityMoscowDate(new Date(Math.max(Date.parse(item.range.from), Date.parse(item.range.to) - 1)).toISOString())
    const value = metric.state === 'AVAILABLE' ? code === 'geoMarks' ? metric.percent : metric.value : null
    return { id: item.range.from + ':' + index, dateFrom: from, dateTo: to, value, detail: adminActivityExplanation(metric) }
  })
}
