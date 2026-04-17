import { motion } from 'motion/react'
import { CheckCircle, Circle, Link as LinkIcon } from '@phosphor-icons/react'
import { useSubjectName } from '@/features/schedule/api'
import { cn } from '@/lib/utils'
import type { HomeworkResponse } from './types'

interface HomeworkCardProps {
  homework: HomeworkResponse
  onToggleComplete?: (next: boolean) => void
  disabled?: boolean
}

/**
 * Read-only ДЗ card for the student view. Chip-style completion control on
 * the right; title + optional description/link on the left. Link opens in
 * a new tab with `noopener` (user-supplied URL — no referer leak).
 */
export function HomeworkCard({
  homework,
  onToggleComplete,
  disabled,
}: HomeworkCardProps) {
  const { data: subjectName } = useSubjectName(homework.subjectId)
  const completed = homework.completed

  return (
    <motion.article
      layout
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2, ease: 'easeOut' }}
      className={cn(
        'relative rounded-2xl border p-3',
        'flex gap-3',
      )}
      style={{
        background: 'var(--bg-secondary)',
        borderColor: 'var(--border-subtle)',
        opacity: completed ? 0.72 : 1,
      }}
    >
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p
          className="text-[11px] font-medium uppercase tracking-wide"
          style={{ color: 'var(--text-muted)' }}
        >
          {subjectName ?? 'Предмет'} · {homework.lessonNumber} пара
        </p>
        <h3
          className={cn(
            'text-base font-semibold leading-snug text-balance',
            completed && 'line-through',
          )}
          style={{
            color: 'var(--text-primary)',
            fontFamily: 'var(--font-heading)',
          }}
        >
          {homework.title}
        </h3>
        {homework.description && (
          <p
            className="text-sm leading-snug whitespace-pre-wrap"
            style={{ color: 'var(--text-secondary)' }}
          >
            {homework.description}
          </p>
        )}
        {homework.link && (
          <a
            href={homework.link}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex w-fit items-center gap-1 text-xs font-medium"
            style={{ color: 'var(--accent-primary)' }}
          >
            <LinkIcon size={14} weight="bold" aria-hidden="true" />
            Ссылка
          </a>
        )}
      </div>

      {onToggleComplete && (
        <button
          type="button"
          onClick={() => onToggleComplete(!completed)}
          disabled={disabled}
          aria-label={completed ? 'Снять отметку' : 'Отметить выполненным'}
          aria-pressed={completed}
          className={cn(
            'shrink-0 grid size-10 place-items-center rounded-full',
            'transition-colors duration-150',
            'disabled:opacity-50',
          )}
          style={{
            color: completed
              ? 'var(--accent-primary)'
              : 'var(--text-secondary)',
          }}
        >
          {completed ? (
            <CheckCircle size={26} weight="fill" />
          ) : (
            <Circle size={26} weight="regular" />
          )}
        </button>
      )}
    </motion.article>
  )
}
