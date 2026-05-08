import { useMemo } from 'react'
import { CaretLeft, CaretRight } from '@phosphor-icons/react'
import {
  addDays,
  DAY_NAMES_SHORT,
  formatDate,
  getMonday,
  MONTH_FULL,
} from '@/shared/lib/dateUtils'
import { useDateNavigation } from '@/shared/hooks/useDateNavigation'
import { cn } from '@/lib/utils'
import type { HomeworkResponse } from './types'

interface MonthViewProps {
  month: Date
  onMonthChange: (month: Date) => void
  homeworks: HomeworkResponse[]
  onPickDate: (date: Date) => void
}

interface Cell {
  date: Date
  iso: string
  inCurrentMonth: boolean
  count: number
  uncompletedCount: number
}

function startOfMonth(date: Date): Date {
  const next = new Date(date)
  next.setDate(1)
  next.setHours(0, 0, 0, 0)
  return next
}

export function MonthView({
  month,
  onMonthChange,
  homeworks,
  onPickDate,
}: MonthViewProps) {
  const { goPrev, goNext } = useDateNavigation({
    unit: 'month',
    value: month,
    onChange: onMonthChange,
  })

  const cells = useMemo<Cell[]>(() => {
    const first = startOfMonth(month)
    const monday = getMonday(first)
    const byDate = new Map<string, HomeworkResponse[]>()
    for (const homework of homeworks) {
      const bucket = byDate.get(homework.lessonDate) ?? []
      bucket.push(homework)
      byDate.set(homework.lessonDate, bucket)
    }

    const result: Cell[] = []
    for (let index = 0; index < 42; index += 1) {
      const date = addDays(monday, index)
      const iso = formatDate(date)
      const list = byDate.get(iso) ?? []
      result.push({
        date,
        iso,
        inCurrentMonth: date.getMonth() === first.getMonth(),
        count: list.length,
        uncompletedCount: list.filter((homework) => !homework.completed).length,
      })
    }
    return result
  }, [homeworks, month])

  const today = formatDate(new Date())

  return (
    <div className="flex flex-col gap-3">
      <div
        className="flex items-center justify-between gap-2 rounded-2xl border p-2"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
        }}
      >
        <button
          type="button"
          onClick={goPrev}
          className="grid size-10 place-items-center rounded-full"
          style={{ color: 'var(--text-secondary)' }}
          aria-label="Предыдущий месяц"
        >
          <CaretLeft size={20} weight="bold" />
        </button>
        <span
          className="text-sm font-semibold capitalize tracking-tight"
          style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
        >
          {MONTH_FULL[month.getMonth()]} {month.getFullYear()}
        </span>
        <button
          type="button"
          onClick={goNext}
          className="grid size-10 place-items-center rounded-full"
          style={{ color: 'var(--text-secondary)' }}
          aria-label="Следующий месяц"
        >
          <CaretRight size={20} weight="bold" />
        </button>
      </div>

      <div
        className="rounded-2xl border p-2"
        style={{
          background: 'var(--bg-secondary)',
          borderColor: 'var(--border-subtle)',
        }}
      >
        <div className="grid grid-cols-7 gap-1 pb-1">
          {DAY_NAMES_SHORT.map((day) => (
            <div
              key={day}
              className="text-center text-[10px] font-medium uppercase"
              style={{ color: 'var(--text-muted)' }}
            >
              {day}
            </div>
          ))}
        </div>
        <div className="grid grid-cols-7 gap-1">
          {cells.map((cell) => {
            const isToday = cell.iso === today
            return (
              <button
                key={cell.iso}
                type="button"
                onClick={() => onPickDate(cell.date)}
                disabled={cell.count === 0}
                aria-label={`${cell.date.getDate()}, заданий: ${cell.count}`}
                className={cn(
                  'relative aspect-square rounded-xl',
                  'flex flex-col items-center justify-center gap-0.5',
                  'transition-colors duration-150',
                  cell.count === 0 && 'cursor-default',
                )}
                style={{
                  background: isToday
                    ? 'color-mix(in oklab, var(--accent-primary) 14%, transparent)'
                    : cell.count > 0
                      ? 'color-mix(in oklab, var(--accent-primary) 6%, transparent)'
                      : 'transparent',
                  border: isToday ? '1px solid var(--accent-primary)' : '1px solid transparent',
                  opacity: cell.inCurrentMonth ? 1 : 0.35,
                }}
              >
                <span
                  className="text-sm font-semibold tabular-nums"
                  style={{
                    color: isToday ? 'var(--accent-primary)' : 'var(--text-primary)',
                  }}
                >
                  {cell.date.getDate()}
                </span>
                {cell.count > 0 && (
                  <span
                    className="min-w-[18px] rounded-full px-1 text-[10px] font-bold leading-[16px]"
                    style={{
                      background:
                        cell.uncompletedCount > 0
                          ? 'var(--accent-primary)'
                          : 'color-mix(in oklab, var(--accent-primary) 30%, transparent)',
                      color:
                        cell.uncompletedCount > 0
                          ? 'var(--accent-primary-contrast)'
                          : 'var(--text-primary)',
                    }}
                  >
                    {cell.count}
                  </span>
                )}
              </button>
            )
          })}
        </div>
      </div>
    </div>
  )
}
