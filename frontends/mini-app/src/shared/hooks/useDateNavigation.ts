import { useCallback, useMemo } from 'react'
import { addDays, getMonday, isSameDay } from '@/shared/lib/dateUtils'

export type DateUnit = 'day' | 'week' | 'month'

interface DateBounds {
  min?: Date | null
  max?: Date | null
}

interface UseDateNavigationOptions {
  unit?: DateUnit
  value: Date
  onChange: (next: Date) => void
  bounds?: DateBounds
}

export function useDateNavigation({
  unit = 'week',
  value,
  onChange,
  bounds,
}: UseDateNavigationOptions) {
  const normalized = useMemo(() => normalize(value, unit), [unit, value])
  const prev = useMemo(() => step(normalized, unit, -1), [normalized, unit])
  const next = useMemo(() => step(normalized, unit, 1), [normalized, unit])

  const canGoPrev = useMemo(() => isInBounds(prev, bounds, unit), [bounds, prev, unit])
  const canGoNext = useMemo(() => isInBounds(next, bounds, unit), [bounds, next, unit])

  const goPrev = useCallback(() => {
    if (canGoPrev) onChange(prev)
  }, [canGoPrev, onChange, prev])

  const goNext = useCallback(() => {
    if (canGoNext) onChange(next)
  }, [canGoNext, next, onChange])

  const goToToday = useCallback(() => {
    onChange(normalize(new Date(), unit))
  }, [onChange, unit])

  const isAtToday = useMemo(
    () => isSameDay(normalized, normalize(new Date(), unit)),
    [normalized, unit],
  )

  return { goPrev, goNext, goToToday, isAtToday, canGoPrev, canGoNext }
}

function normalize(date: Date, unit: DateUnit): Date {
  if (unit === 'week') return getMonday(date)
  const normalized = new Date(date)
  if (unit === 'month') normalized.setDate(1)
  normalized.setHours(0, 0, 0, 0)
  return normalized
}

function step(date: Date, unit: DateUnit, direction: -1 | 1): Date {
  if (unit === 'day') return addDays(date, direction)
  if (unit === 'week') return addDays(date, direction * 7)
  const next = new Date(date)
  next.setMonth(next.getMonth() + direction)
  return next
}

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
  const end = new Date(start)
  end.setMonth(end.getMonth() + 1)
  end.setDate(0)
  return end
}
