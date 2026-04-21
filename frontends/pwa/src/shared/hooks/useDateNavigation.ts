import { useCallback, useMemo } from 'react'
import { addDays, getMonday, isSameDay } from '@/shared/lib/dateUtils'

export type DateUnit = 'day' | 'week' | 'month'

export interface DateBounds {
  /** Inclusive lower bound. If null/undefined — no lower bound. */
  min?: Date | null
  /** Inclusive upper bound. If null/undefined — no upper bound. */
  max?: Date | null
}

export interface UseDateNavigationOptions {
  unit: DateUnit
  value: Date
  onChange: (next: Date) => void
  bounds?: DateBounds
}

export interface DateNavigationApi {
  goPrev: () => void
  goNext: () => void
  goToToday: () => void
  /** True if the current `value` is (today | monday-of-today | first-of-this-month). */
  isAtToday: boolean
  /** `goPrev` would go below `bounds.min`. */
  canGoPrev: boolean
  /** `goNext` would go above `bounds.max`. */
  canGoNext: boolean
}

/**
 * Single source of truth для prev/next навигации по дате в трёх режимах:
 * `day` (±1 день), `week` (±7 дней, snapped to Monday), `month`
 * (±1 месяц, snapped to 1-е число).
 *
 * Почему хук, а не `addDays(d, direction === 'next' ? X : -X)` inline:
 * (а) bounds-проверка повторяется 3 раза в schedule/homework — выносим
 *     (см. G6/4 Schedule navigation bounds);
 * (б) `goToToday` в текущем DayView/WeekView отсутствует, а SchedulePage
 *     имеет собственный — унификация позволяет добавить ко всем страницам
 *     одинаковый «Сегодня»-жест;
 * (в) тестируемость: bounds логика покрывается одним набором unit'ов.
 *
 * Snap-семантика: `week` всегда нормализует `value` на понедельник до
 * расчёта границ (чтобы «next» от среды давал следующий понедельник, а
 * не среду +7). `month` нормализует на 1-е.
 */
export function useDateNavigation({
  unit,
  value,
  onChange,
  bounds,
}: UseDateNavigationOptions): DateNavigationApi {
  const normalized = useMemo(() => normalize(value, unit), [value, unit])

  const prev = useMemo(() => step(normalized, unit, -1), [normalized, unit])
  const next = useMemo(() => step(normalized, unit, 1), [normalized, unit])

  const canGoPrev = useMemo(
    () => isInBounds(prev, bounds, unit),
    [prev, bounds, unit],
  )
  const canGoNext = useMemo(
    () => isInBounds(next, bounds, unit),
    [next, bounds, unit],
  )

  const goPrev = useCallback(() => {
    if (canGoPrev) onChange(prev)
  }, [canGoPrev, onChange, prev])

  const goNext = useCallback(() => {
    if (canGoNext) onChange(next)
  }, [canGoNext, onChange, next])

  const goToToday = useCallback(() => {
    const today = normalize(new Date(), unit)
    onChange(today)
  }, [unit, onChange])

  const isAtToday = useMemo(() => {
    const today = normalize(new Date(), unit)
    return isSameDay(normalized, today)
  }, [normalized, unit])

  return { goPrev, goNext, goToToday, isAtToday, canGoPrev, canGoNext }
}

function normalize(date: Date, unit: DateUnit): Date {
  if (unit === 'week') return getMonday(date)
  if (unit === 'month') {
    const d = new Date(date)
    d.setDate(1)
    d.setHours(0, 0, 0, 0)
    return d
  }
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  return d
}

function step(date: Date, unit: DateUnit, direction: -1 | 1): Date {
  if (unit === 'day') return addDays(date, direction)
  if (unit === 'week') return addDays(date, direction * 7)
  const d = new Date(date)
  d.setMonth(d.getMonth() + direction)
  return d
}

/**
 * Для week/month bounds проверяем не саму границу нормализованной даты,
 * а то что хотя бы часть периода попадает в диапазон (например, если
 * `bounds.max = 2026-06-15`, навигация на неделю `2026-06-15..21`
 * разрешена — последний день семестра попадает внутрь недели).
 */
function isInBounds(date: Date, bounds: DateBounds | undefined, unit: DateUnit): boolean {
  if (!bounds) return true
  const periodStart = date
  const periodEnd = periodEndOf(date, unit)

  if (bounds.min && periodEnd < normalize(bounds.min, 'day')) return false
  if (bounds.max && periodStart > normalize(bounds.max, 'day')) return false
  return true
}

function periodEndOf(start: Date, unit: DateUnit): Date {
  if (unit === 'day') return start
  if (unit === 'week') return addDays(start, 5)
  const d = new Date(start)
  d.setMonth(d.getMonth() + 1)
  d.setDate(0)
  return d
}
