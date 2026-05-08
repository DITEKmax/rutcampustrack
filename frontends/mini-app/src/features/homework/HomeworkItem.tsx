import { Check, Link as LinkIcon } from '@phosphor-icons/react'
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
  subjectName?: string
  disabled?: boolean
  onToggle: () => void
}

export function HomeworkItem({
  homework,
  subjectName,
  disabled,
  onToggle,
}: HomeworkItemProps) {
  const handleToggle = () => {
    if (disabled) return
    if (hapticFeedback.impactOccurred.isAvailable()) {
      hapticFeedback.impactOccurred('light')
    }
    onToggle()
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
        disabled={disabled}
        role="checkbox"
        aria-checked={homework.completed}
        aria-label={
          homework.completed
            ? `Отметить "${homework.title}" как невыполненное`
            : `Отметить "${homework.title}" как выполненное`
        }
        className="flex size-11 shrink-0 items-center justify-center disabled:opacity-50"
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
          className="mb-0.5 text-[11px] font-medium uppercase tracking-wide"
          style={{ color: 'var(--text-muted)' }}
        >
          {subjectName ?? homework.subjectName ?? 'Предмет'} · {homework.lessonNumber} пара
        </p>
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
        <div className="mt-1 flex flex-wrap items-center gap-2">
          {homework.dueDate && (
            <p
              className="inline-flex items-center gap-1 text-[11px] font-medium tabular-nums"
              style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}
            >
              До {homework.dueDate}
            </p>
          )}
          {homework.link && (
            <a
              href={homework.link}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 text-[11px] font-semibold"
              style={{ color: 'var(--accent-primary)' }}
            >
              <LinkIcon size={12} weight="bold" aria-hidden="true" />
              Ссылка
            </a>
          )}
        </div>
      </div>
    </motion.div>
  )
}
