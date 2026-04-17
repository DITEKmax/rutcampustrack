import { useMemo } from 'react'
import { CaretLeft, CaretRight } from '@phosphor-icons/react'
import {
  addDays,
  DAY_NAMES_FULL,
  formatDate,
  formatWeekRange,
  MONTH_ABBREV,
} from '@/shared/lib/dateUtils'
import type { HomeworkResponse } from './types'
import { HomeworkCard } from './HomeworkCard'

interface WeekViewProps {
  monday: Date
  onMondayChange: (monday: Date) => void
  homeworks: HomeworkResponse[]
  onToggleComplete: (hw: HomeworkResponse, next: boolean) => void
  toggling: Set<number>
}

interface DayBucket {
  date: Date
  iso: string
  homeworks: HomeworkResponse[]
}

export function WeekView({
  monday,
  onMondayChange,
  homeworks,
  onToggleComplete,
  toggling,
}: WeekViewProps) {
  const buckets = useMemo<DayBucket[]>(() => {
    const days: DayBucket[] = []
    for (let i = 0; i < 6; i++) {
      const d = addDays(monday, i)
      const iso = formatDate(d)
      days.push({
        date: d,
        iso,
        homeworks: homeworks
          .filter((hw) => hw.lessonDate === iso)
          .sort((a, b) => a.lessonNumber - b.lessonNumber),
      })
    }
    return days
  }, [monday, homeworks])

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
          onClick={() => onMondayChange(addDays(monday, -7))}
          className="grid size-10 place-items-center rounded-full"
          style={{ color: 'var(--text-secondary)' }}
          aria-label="Предыдущая неделя"
        >
          <CaretLeft size={20} weight="bold" />
        </button>
        <div className="flex flex-col items-center gap-0.5">
          <span
            className="text-[10px] font-medium uppercase tracking-wide"
            style={{ color: 'var(--text-muted)' }}
          >
            Неделя
          </span>
          <span
            className="text-sm font-semibold tabular-nums tracking-tight"
            style={{ color: 'var(--text-primary)' }}
          >
            {formatWeekRange(monday)}
          </span>
        </div>
        <button
          type="button"
          onClick={() => onMondayChange(addDays(monday, 7))}
          className="grid size-10 place-items-center rounded-full"
          style={{ color: 'var(--text-secondary)' }}
          aria-label="Следующая неделя"
        >
          <CaretRight size={20} weight="bold" />
        </button>
      </div>

      <div className="flex flex-col gap-4">
        {buckets.map((bucket) => (
          <section key={bucket.iso} className="flex flex-col gap-2">
            <header className="flex items-baseline justify-between">
              <h3
                className="text-sm font-semibold"
                style={{
                  color: 'var(--text-primary)',
                  fontFamily: 'var(--font-heading)',
                }}
              >
                {DAY_NAMES_FULL[(bucket.date.getDay() + 6) % 7]}
              </h3>
              <span
                className="text-xs tabular-nums"
                style={{ color: 'var(--text-muted)' }}
              >
                {bucket.date.getDate()} {MONTH_ABBREV[bucket.date.getMonth()]}
              </span>
            </header>
            {bucket.homeworks.length === 0 ? (
              <p
                className="rounded-xl border border-dashed px-3 py-2 text-xs"
                style={{
                  borderColor: 'var(--border-subtle)',
                  color: 'var(--text-muted)',
                }}
              >
                Нет заданий
              </p>
            ) : (
              <div className="flex flex-col gap-2">
                {bucket.homeworks.map((hw) => (
                  <HomeworkCard
                    key={hw.id}
                    homework={hw}
                    onToggleComplete={(next) => onToggleComplete(hw, next)}
                    disabled={toggling.has(hw.id)}
                  />
                ))}
              </div>
            )}
          </section>
        ))}
      </div>
    </div>
  )
}
