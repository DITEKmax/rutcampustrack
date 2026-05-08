import { motion } from 'motion/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { useSwipeHandler } from '@/shared/hooks/useSwipeHandler'
import { cn } from '@/lib/utils'

const DAY_LABELS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'] as const

interface WeekDayTabsProps {
  selectedDay: number
  onSelectDay: (day: number) => void
  weekDates: Date[]
  onSwipeWeek: (direction: 'prev' | 'next') => void
  canSwipePrev?: boolean
  canSwipeNext?: boolean
}

export function WeekDayTabs({
  selectedDay,
  onSelectDay,
  weekDates,
  onSwipeWeek,
  canSwipePrev = true,
  canSwipeNext = true,
}: WeekDayTabsProps) {
  const today = new Date()
  const todayYmd = `${today.getFullYear()}-${today.getMonth()}-${today.getDate()}`

  const onDragEnd = useSwipeHandler({
    horizontalThreshold: 80,
    onSwipeLeft: () => {
      if (canSwipeNext) onSwipeWeek('next')
    },
    onSwipeRight: () => {
      if (canSwipePrev) onSwipeWeek('prev')
    },
  })

  const handleSelect = (index: number) => {
    if (index !== selectedDay && hapticFeedback.selectionChanged.isAvailable()) {
      hapticFeedback.selectionChanged()
    }
    onSelectDay(index)
  }

  return (
    <motion.div
      role="tablist"
      aria-label="Дни недели"
      drag="x"
      dragConstraints={{ left: 0, right: 0 }}
      dragElastic={0.2}
      onDragEnd={onDragEnd}
      className={cn(
        'sticky top-12 z-[var(--z-dropdown)]',
        'flex items-stretch gap-1 px-3 py-2',
        'backdrop-blur-md',
      )}
      style={{
        background: 'color-mix(in oklab, var(--bg-primary) 88%, transparent)',
        borderBottom: '1px solid var(--border-subtle)',
      }}
    >
      {DAY_LABELS.map((label, index) => {
        const isActive = index === selectedDay
        const date = weekDates[index]
        const dateNum = date?.getDate() ?? ''
        const isToday =
          date &&
          `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}` === todayYmd

        return (
          <button
            key={label}
            type="button"
            role="tab"
            aria-selected={isActive}
            aria-label={`${label} ${dateNum}`}
            onClick={() => handleSelect(index)}
            className={cn(
              'relative flex min-h-11 flex-1 flex-col items-center justify-center gap-0.5',
              'rounded-xl px-1 py-1.5',
              'transition-colors duration-200 ease-out',
              isActive
                ? 'text-[var(--accent-primary-contrast)]'
                : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]',
            )}
          >
            {isActive && (
              <motion.span
                layoutId="mini-weekday-active-pill"
                aria-hidden="true"
                className="absolute inset-0 -z-10 rounded-xl"
                style={{ background: 'var(--gradient-brand)' }}
                transition={{ type: 'spring', stiffness: 520, damping: 34 }}
              />
            )}

            <span className="text-[10px] font-medium uppercase leading-none tracking-wide">
              {label}
            </span>
            <span className="text-sm font-semibold leading-none tabular-nums">{dateNum}</span>

            {isToday && !isActive && (
              <span
                aria-hidden="true"
                className="absolute bottom-0.5 size-1 rounded-full"
                style={{ background: 'var(--accent-primary)' }}
              />
            )}
          </button>
        )
      })}
    </motion.div>
  )
}
