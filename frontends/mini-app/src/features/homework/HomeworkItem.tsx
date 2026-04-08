import { Check } from '@phosphor-icons/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { motion } from 'motion/react'
import { cn } from '@/lib/utils'
import type { HomeworkResponse } from './types'

/**
 * Homework row with a custom Transit Grid checkbox.
 *
 * Custom checkbox because shadcn's Checkbox pulls in Radix + extra CSS the
 * mini-app doesn't need at this size. The box uses tokens so it lights up
 * brand-green when checked. Haptic feedback on toggle preserved.
 */
interface HomeworkItemProps {
  homework: HomeworkResponse
  onToggle: (id: number, completed: boolean) => void
}

export function HomeworkItem({ homework, onToggle }: HomeworkItemProps) {
  const handleToggle = () => {
    if (hapticFeedback.impactOccurred.isAvailable()) {
      hapticFeedback.impactOccurred('light')
    }
    onToggle(homework.id, homework.completed)
  }

  return (
    <motion.div
      animate={{ opacity: homework.completed ? 0.55 : 1 }}
      transition={{ duration: 0.18, ease: 'easeOut' }}
      className="flex items-start gap-3 rounded-2xl border px-3 py-3"
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
      }}
    >
      <button
        type="button"
        onClick={handleToggle}
        role="checkbox"
        aria-checked={homework.completed}
        aria-label={
          homework.completed
            ? `Отметить "${homework.title}" как невыполненное`
            : `Отметить "${homework.title}" как выполненное`
        }
        className="flex size-11 shrink-0 items-center justify-center"
      >
        <span
          aria-hidden="true"
          className={cn(
            'grid size-6 place-items-center rounded-md border-2',
            'transition-colors duration-200 ease-out',
          )}
          style={{
            background: homework.completed ? 'var(--accent-primary)' : 'transparent',
            borderColor: homework.completed ? 'var(--accent-primary)' : 'var(--border-default)',
            boxShadow: homework.completed ? 'var(--glow-primary)' : 'none',
          }}
        >
          {homework.completed && (
            <Check size={14} weight="bold" style={{ color: 'var(--accent-primary-contrast)' }} />
          )}
        </span>
      </button>

      <div className="min-w-0 flex-1">
        <p
          className={cn(
            'text-sm font-semibold leading-snug text-balance',
            homework.completed && 'line-through',
          )}
          style={{
            color: homework.completed ? 'var(--text-muted)' : 'var(--text-primary)',
            fontFamily: 'var(--font-heading)',
          }}
        >
          {homework.title}
        </p>
        {homework.description && (
          <p
            className="mt-0.5 text-xs text-pretty line-clamp-2"
            style={{ color: 'var(--text-secondary)' }}
          >
            {homework.description}
          </p>
        )}
        {homework.dueDate && (
          <p
            className="mt-1 inline-flex items-center gap-1 text-[11px] font-medium tabular-nums"
            style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}
          >
            До {homework.dueDate}
          </p>
        )}
      </div>
    </motion.div>
  )
}
