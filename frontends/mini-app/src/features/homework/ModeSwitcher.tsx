import { motion } from 'motion/react'
import { cn } from '@/lib/utils'

export type HomeworkMode = 'day' | 'week' | 'month'

const OPTIONS: { value: HomeworkMode; label: string }[] = [
  { value: 'day', label: 'День' },
  { value: 'week', label: 'Неделя' },
  { value: 'month', label: 'Месяц' },
]

interface ModeSwitcherProps {
  value: HomeworkMode
  onChange: (mode: HomeworkMode) => void
}

export function ModeSwitcher({ value, onChange }: ModeSwitcherProps) {
  return (
    <div
      role="tablist"
      aria-label="Режим просмотра"
      className="relative flex w-full rounded-full border p-1"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
      }}
    >
      {OPTIONS.map((option) => {
        const active = option.value === value
        return (
          <button
            key={option.value}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(option.value)}
            className={cn(
              'relative min-h-9 flex-1 rounded-full px-3 text-sm font-semibold',
              'transition-colors duration-150',
              active
                ? 'text-[var(--accent-primary-contrast)]'
                : 'text-[var(--text-secondary)]',
            )}
          >
            {active && (
              <motion.span
                layoutId="mini-homework-mode-pill"
                aria-hidden="true"
                className="absolute inset-0 -z-10 rounded-full"
                style={{
                  background: 'var(--gradient-brand)',
                  boxShadow: 'var(--glow-primary)',
                }}
                transition={{ type: 'spring', stiffness: 520, damping: 34 }}
              />
            )}
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
