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
      className="relative flex rounded-full border p-1"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
      }}
    >
      {OPTIONS.map((opt) => {
        const isActive = value === opt.value
        return (
          <button
            key={opt.value}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onChange(opt.value)}
            className={cn(
              'relative flex-1 rounded-full px-3 py-1.5',
              'text-sm font-medium',
              'transition-colors duration-150',
              isActive
                ? 'text-[var(--accent-primary-contrast)]'
                : 'text-[var(--text-secondary)]',
            )}
          >
            {isActive && (
              <motion.span
                layoutId="homework-mode-pill"
                aria-hidden="true"
                className="absolute inset-0 -z-10 rounded-full"
                style={{
                  background: 'var(--gradient-brand)',
                  boxShadow: 'var(--glow-primary)',
                }}
                transition={{ type: 'spring', stiffness: 520, damping: 34 }}
              />
            )}
            {opt.label}
          </button>
        )
      })}
    </div>
  )
}
