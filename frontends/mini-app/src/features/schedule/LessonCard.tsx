import { motion } from 'motion/react'
import { MapPin, CaretRight } from '@phosphor-icons/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { StatusBadge } from './StatusBadge'
import { cn } from '@/lib/utils'
import type { LessonResponse } from './types'

/**
 * Mini-app lesson card — compact subway-station layout (brandbook §1, §4.2).
 *
 * Same visual language as the PWA LessonCard but denser, since the Telegram
 * Mini App viewport is narrower. Tapping an ACTIVE lesson triggers Telegram
 * haptic feedback and navigates to the check-in flow.
 */
interface LessonCardProps {
  lesson: LessonResponse
  subjectName: string
  onCheckin: (lessonId: number) => void
}

function formatTime(time: string): string {
  return time.slice(0, 5)
}

export function LessonCard({ lesson, subjectName, onCheckin }: LessonCardProps) {
  const isActive = lesson.status === 'ACTIVE'
  const isCancelled = lesson.status === 'CANCELLED'

  const handleClick = () => {
    if (!isActive) return
    if (hapticFeedback.impactOccurred.isAvailable()) {
      hapticFeedback.impactOccurred('light')
    }
    onCheckin(lesson.id)
  }

  const dotColor = isActive
    ? 'var(--accent-primary)'
    : isCancelled
      ? 'var(--text-muted)'
      : 'var(--text-muted)'

  return (
    <motion.article
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: isCancelled ? 0.55 : 1, y: 0 }}
      transition={{ duration: 0.18, ease: 'easeOut' }}
      onClick={handleClick}
      className={cn(
        'relative flex gap-3 rounded-2xl border p-3',
        isActive && 'cursor-pointer',
      )}
      style={{
        background: 'var(--bg-secondary)',
        borderColor: isActive ? 'var(--border-accent)' : 'var(--border-subtle)',
        boxShadow: isActive ? 'var(--glow-primary)' : 'none',
        minHeight: isActive ? 44 : undefined,
      }}
      role={isActive ? 'button' : undefined}
      aria-label={isActive ? `${subjectName} — отметиться на паре` : undefined}
    >
      {/* Left rail — time + station dot */}
      <div className="flex shrink-0 flex-col items-center gap-1 pt-0.5">
        <span
          className="text-[11px] font-semibold tabular-nums"
          style={{ color: 'var(--text-primary)' }}
        >
          {formatTime(lesson.startTime)}
        </span>
        <span
          aria-hidden="true"
          className="my-0.5 size-2.5 shrink-0 rounded-full"
          style={{
            background: dotColor,
            boxShadow: isActive ? `0 0 10px ${dotColor}` : 'none',
          }}
        />
        <span
          className="text-[10px] tabular-nums"
          style={{ color: 'var(--text-muted)' }}
        >
          {formatTime(lesson.endTime)}
        </span>
      </div>

      {/* Right — subject + room + active CTA */}
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex items-start justify-between gap-2">
          <h3
            className={cn(
              'line-clamp-2 text-sm font-semibold leading-snug text-balance',
              isCancelled && 'line-through',
            )}
            style={{
              color: 'var(--text-primary)',
              fontFamily: 'var(--font-heading)',
            }}
          >
            {subjectName}
          </h3>
          <StatusBadge status={lesson.status} />
        </div>

        <p
          className="flex items-center gap-1 text-[11px]"
          style={{ color: 'var(--text-secondary)' }}
        >
          <MapPin size={10} weight="fill" aria-hidden="true" />
          Ауд. {lesson.room}
        </p>

        {isActive && (
          <p
            className="mt-1 inline-flex items-center gap-1 text-[11px] font-semibold"
            style={{ color: 'var(--accent-primary)' }}
          >
            Войти на пару
            <CaretRight size={10} weight="bold" aria-hidden="true" />
          </p>
        )}
      </div>
    </motion.article>
  )
}
