import { motion } from 'motion/react'
import { cn } from '@/lib/utils'

const DAY_LABELS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'] as const

/**
 * Weekday tab strip (brandbook §4.6 navigation tabs).
 *
 * Swipe horizontally to switch weeks. The active tab is highlighted by a
 * Motion shared-layout pill that slides between selections. Sticky just
 * below the header so the user always knows which day they are viewing.
 */
interface WeekDayTabsProps {
  selectedDay: number // 0-5 (0=Mon, 5=Sat)
  onSelectDay: (day: number) => void
  weekDates: Date[] // array of 6 Date objects for Mon-Sat
  onSwipeWeek: (direction: 'prev' | 'next') => void
  hasOfflineBanner?: boolean
}

export function WeekDayTabs({
  selectedDay,
  onSelectDay,
  weekDates,
  onSwipeWeek,
  hasOfflineBanner,
}: WeekDayTabsProps) {
  const today = new Date()
  const todayYmd = `${today.getFullYear()}-${today.getMonth()}-${today.getDate()}`

  return (
    <motion.div
      role="tablist"
      aria-label="Дни недели"
      drag="x"
      dragConstraints={{ left: 0, right: 0 }}
      dragElastic={0.2}
      onDragEnd={(_e, info) => {
        if (info.offset.x < -80) onSwipeWeek('next')
        else if (info.offset.x > 80) onSwipeWeek('prev')
      }}
      className={cn(
        'sticky z-[var(--z-dropdown)]',
        'flex items-stretch gap-1 px-3 py-2',
        'backdrop-blur-md',
        hasOfflineBanner ? 'top-8' : 'top-0',
      )}
      style={{
        background: 'color-mix(in oklab, var(--bg-primary) 85%, transparent)',
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
            role="tab"
            aria-selected={isActive}
            aria-label={`${label} ${dateNum}`}
            onClick={() => onSelectDay(index)}
            className={cn(
              'relative flex flex-1 flex-col items-center justify-center gap-0.5',
              'min-h-[44px] rounded-xl px-1 py-1.5',
              'transition-colors duration-200 ease-out',
              isActive
                ? 'text-[var(--accent-primary-contrast)]'
                : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]',
            )}
          >
            {/* Active pill — shared layoutId for slide */}
            {isActive && (
              <motion.span
                layoutId="weekday-active-pill"
                aria-hidden="true"
                className="absolute inset-0 -z-10 rounded-xl"
                style={{ background: 'var(--gradient-brand)' }}
                transition={{ type: 'spring', stiffness: 520, damping: 34 }}
              />
            )}

            <span className="text-[10px] font-medium uppercase leading-none tracking-wide">
              {label}
            </span>
            <span className="text-sm font-semibold tabular-nums leading-none">
              {dateNum}
            </span>

            {/* Today marker dot — only when not active */}
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
