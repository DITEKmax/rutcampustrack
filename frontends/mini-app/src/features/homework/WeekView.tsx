import { useMemo } from 'react'
import { CaretLeft, CaretRight } from '@phosphor-icons/react'
import {
  addDays,
  DAY_NAMES_FULL,
  formatDate,
  formatWeekRange,
  MONTH_ABBREV,
} from '@/shared/lib/dateUtils'
import { useDateNavigation } from '@/shared/hooks/useDateNavigation'
import type { HomeworkResponse } from './types'
import { HomeworkItem } from './HomeworkItem'

interface WeekViewProps {
  monday: Date
  onMondayChange: (monday: Date) => void
  homeworks: HomeworkResponse[]
  subjectMap: Record<number, string>
  toggling: Set<number>
  onToggleComplete: (homework: HomeworkResponse) => void
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
  subjectMap,
  toggling,
  onToggleComplete,
}: WeekViewProps) {
  const buckets = useMemo<DayBucket[]>(() => {
    const days: DayBucket[] = []
    for (let index = 0; index < 6; index += 1) {
      const date = addDays(monday, index)
      const iso = formatDate(date)
      days.push({
        date,
        iso,
        homeworks: homeworks
          .filter((homework) => homework.lessonDate === iso)
          .sort((a, b) => a.lessonNumber - b.lessonNumber),
      })
    }
    return days
  }, [homeworks, monday])

  const { goPrev, goNext } = useDateNavigation({
    unit: 'week',
    value: monday,
    onChange: onMondayChange,
  })

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
          aria-label="Предыдущая неделя"
        >
          <CaretLeft size={20} weight="bold" />
        </button>
        <div className="flex flex-col items-center gap-0.5 text-center">
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
          onClick={goNext}
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
            <header className="flex items-baseline justify-between px-1">
              <h3
                className="text-sm font-semibold"
                style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
              >
                {DAY_NAMES_FULL[(bucket.date.getDay() + 6) % 7]}
              </h3>
              <span className="text-xs tabular-nums" style={{ color: 'var(--text-muted)' }}>
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
                {bucket.homeworks.map((homework) => (
                  <HomeworkItem
                    key={homework.id}
                    homework={homework}
                    subjectName={subjectMap[homework.subjectId] ?? homework.subjectName}
                    disabled={toggling.has(homework.id)}
                    onToggle={() => onToggleComplete(homework)}
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
