import { motion } from 'motion/react'

const DAY_LABELS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'] as const

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
  return (
    <motion.div
      role="tablist"
      drag="x"
      dragConstraints={{ left: 0, right: 0 }}
      onDragEnd={(_e, info) => {
        if (info.offset.x < -80) onSwipeWeek('next')
        else if (info.offset.x > 80) onSwipeWeek('prev')
      }}
      className={`flex items-center gap-1 px-4 h-12 sticky ${hasOfflineBanner ? 'top-8' : 'top-0'} bg-background z-10`}
    >
      {DAY_LABELS.map((label, index) => {
        const isActive = index === selectedDay
        const dateNum = weekDates[index]?.getDate() ?? ''

        return (
          <button
            key={label}
            role="tab"
            aria-selected={isActive}
            onClick={() => onSelectDay(index)}
            className={`flex flex-col items-center justify-center min-h-[44px] min-w-[44px] flex-1 rounded-lg transition-colors ${
              isActive
                ? 'text-primary font-semibold border-b-2 border-primary'
                : 'text-muted-foreground'
            }`}
          >
            <span className="text-xs">{label}</span>
            <span className="text-sm">{dateNum}</span>
          </button>
        )
      })}
    </motion.div>
  )
}
