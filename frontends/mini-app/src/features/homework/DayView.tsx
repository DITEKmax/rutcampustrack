import { CaretLeft, CaretRight, Books } from '@phosphor-icons/react'
import { motion } from 'motion/react'
import { DAY_NAMES_FULL, formatLongDate } from '@/shared/lib/dateUtils'
import { useDateNavigation } from '@/shared/hooks/useDateNavigation'
import { cn } from '@/lib/utils'
import type { HomeworkResponse } from './types'
import { HomeworkItem } from './HomeworkItem'

interface DayViewProps {
  date: Date
  onDateChange: (date: Date) => void
  homeworks: HomeworkResponse[]
  subjectMap: Record<number, string>
  toggling: Set<number>
  onToggleComplete: (homework: HomeworkResponse) => void
}

function dayLabel(date: Date): string {
  return DAY_NAMES_FULL[(date.getDay() + 6) % 7]
}

export function DayView({
  date,
  onDateChange,
  homeworks,
  subjectMap,
  toggling,
  onToggleComplete,
}: DayViewProps) {
  const { goPrev, goNext } = useDateNavigation({
    unit: 'day',
    value: date,
    onChange: onDateChange,
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
          aria-label="Предыдущий день"
        >
          <CaretLeft size={20} weight="bold" />
        </button>
        <div className="flex flex-col items-center gap-0.5 text-center">
          <span
            className="text-[10px] font-medium uppercase tracking-wide"
            style={{ color: 'var(--text-muted)' }}
          >
            {dayLabel(date)}
          </span>
          <span
            className="text-sm font-semibold tabular-nums tracking-tight"
            style={{ color: 'var(--text-primary)' }}
          >
            {formatLongDate(date)}
          </span>
        </div>
        <button
          type="button"
          onClick={goNext}
          className="grid size-10 place-items-center rounded-full"
          style={{ color: 'var(--text-secondary)' }}
          aria-label="Следующий день"
        >
          <CaretRight size={20} weight="bold" />
        </button>
      </div>

      {homeworks.length === 0 ? (
        <EmptyDay />
      ) : (
        <motion.div
          className="flex flex-col gap-2"
          initial="hidden"
          animate="visible"
          variants={{
            visible: { transition: { staggerChildren: 0.04 } },
            hidden: {},
          }}
        >
          {homeworks.map((homework) => (
            <motion.div
              key={homework.id}
              variants={{
                hidden: { opacity: 0, y: 6 },
                visible: { opacity: 1, y: 0 },
              }}
            >
              <HomeworkItem
                homework={homework}
                subjectName={subjectMap[homework.subjectId] ?? homework.subjectName}
                disabled={toggling.has(homework.id)}
                onToggle={() => onToggleComplete(homework)}
              />
            </motion.div>
          ))}
        </motion.div>
      )}
    </div>
  )
}

function EmptyDay() {
  return (
    <div
      className={cn(
        'flex flex-col items-center gap-3 rounded-2xl border border-dashed p-8 text-center',
      )}
      style={{
        borderColor: 'var(--border-default)',
        background: 'color-mix(in oklab, var(--bg-secondary) 50%, transparent)',
      }}
    >
      <div
        className="grid size-14 place-items-center rounded-full"
        style={{
          background: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
          border: '1px solid var(--border-accent)',
          color: 'var(--accent-primary)',
        }}
      >
        <Books size={24} weight="duotone" />
      </div>
      <h2
        className="text-lg font-semibold text-balance"
        style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
      >
        Заданий нет
      </h2>
      <p className="max-w-xs text-sm text-pretty" style={{ color: 'var(--text-secondary)' }}>
        На выбранный день староста не оставлял домашних заданий.
      </p>
    </div>
  )
}
