import { CaretLeft, CaretRight, BookOpen } from '@phosphor-icons/react'
import { motion } from 'motion/react'
import { addDays, formatLongDate, DAY_NAMES_FULL } from '@/shared/lib/dateUtils'
import { cn } from '@/lib/utils'
import type { HomeworkResponse } from './types'
import { HomeworkCard } from './HomeworkCard'

interface DayViewProps {
  date: Date
  onDateChange: (date: Date) => void
  homeworks: HomeworkResponse[]
  onToggleComplete: (hw: HomeworkResponse, next: boolean) => void
  toggling: Set<number>
}

const dayLabel = (d: Date): string => {
  const idx = (d.getDay() + 6) % 7
  return DAY_NAMES_FULL[idx]
}

export function DayView({
  date,
  onDateChange,
  homeworks,
  onToggleComplete,
  toggling,
}: DayViewProps) {
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
          onClick={() => onDateChange(addDays(date, -1))}
          className="grid size-10 place-items-center rounded-full"
          style={{ color: 'var(--text-secondary)' }}
          aria-label="Предыдущий день"
        >
          <CaretLeft size={20} weight="bold" />
        </button>
        <div className="flex flex-col items-center gap-0.5">
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
          onClick={() => onDateChange(addDays(date, 1))}
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
          {homeworks.map((hw) => (
            <motion.div
              key={hw.id}
              variants={{
                hidden: { opacity: 0, y: 6 },
                visible: { opacity: 1, y: 0 },
              }}
            >
              <HomeworkCard
                homework={hw}
                onToggleComplete={(next) => onToggleComplete(hw, next)}
                disabled={toggling.has(hw.id)}
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
        <BookOpen size={24} weight="duotone" />
      </div>
      <h2
        className="text-lg font-semibold text-balance"
        style={{
          color: 'var(--text-primary)',
          fontFamily: 'var(--font-heading)',
        }}
      >
        Заданий нет
      </h2>
      <p
        className="max-w-xs text-sm text-pretty"
        style={{ color: 'var(--text-secondary)' }}
      >
        На выбранный день староста не оставлял домашних заданий.
      </p>
    </div>
  )
}
