import { motion } from 'motion/react'
import {
  BookOpen,
  CheckCircle,
  DotsThreeVertical,
  Lock,
  LockOpen,
  MapPin,
} from '@phosphor-icons/react'
import { useSubjectName } from './api'
import { StatusBadge } from './StatusBadge'
import { CheckInButton } from '@/features/checkin/CheckInButton'
import { Skeleton } from '@/shared/components/Skeleton'
import { cn } from '@/lib/utils'
import type { LessonResponse, AttendanceStatus } from './types'

/**
 * Lesson card — subway-station layout per brandbook §1.
 *
 * Left rail holds the time + a station-dot marker (colored by status).
 * Right side holds subject, room, and — when the lesson is ACTIVE — the
 * check-in button with live attendee count. Cancelled lessons dim to 55%
 * and strike through the subject name. Data-lesson-id preserved for the
 * schedule page auto-scroll (D-03).
 */
interface LessonCardProps {
  lesson: LessonResponse
  attendanceCount?: number
  personalStatus?: AttendanceStatus | null
  onCheckin?: () => void
  onCheckinError?: (msg: string) => void
  isCheckinLoading?: boolean
  /** Open the actions sheet (excuse / request check-in). */
  onOpenActions?: () => void
  /** Headman-only: toggle lesson blockage. Shown on future PLANNED lessons in window. */
  onToggleBlock?: () => void
  isHeadman?: boolean
  /** Is lesson a future PLANNED one within the 14-day block window? */
  isBlockable?: boolean
  /** Homework preview: title of first ДЗ + total count. Hidden when count === 0. */
  homeworkPreview?: { title: string; count: number }
}

function formatTime(time: string): string {
  return time.slice(0, 5) // HH:mm:ss -> HH:mm
}

function dotColor(lesson: LessonResponse, personal: AttendanceStatus | null | undefined): string {
  if (personal === 'present') return 'var(--status-present)'
  if (personal === 'absent') return 'var(--status-absent)'
  if (personal === 'excused') return 'var(--status-excused)'
  if (personal === 'free_attendance') return 'var(--status-free-attendance)'
  if (lesson.status === 'ACTIVE') return 'var(--accent-primary)'
  if (lesson.status === 'CANCELLED') return 'var(--text-muted)'
  return 'var(--text-muted)'
}

export function LessonCard({
  lesson,
  attendanceCount,
  personalStatus,
  onCheckin,
  onCheckinError,
  isCheckinLoading,
  onOpenActions,
  onToggleBlock,
  isHeadman,
  isBlockable,
  homeworkPreview,
}: LessonCardProps) {
  const { data: subjectName, isLoading: subjectLoading } = useSubjectName(lesson.subjectId)
  const isCancelled = lesson.status === 'CANCELLED'
  const isActive = lesson.status === 'ACTIVE'
  const isBlockedByHeadman = !!lesson.blockedByHeadman
  const dot = dotColor(lesson, personalStatus)
  const showActions = !!onOpenActions && !isCancelled

  return (
    <motion.article
      layout
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: isCancelled ? 0.55 : 1, y: 0 }}
      transition={{ duration: 0.2, ease: 'easeOut' }}
      data-lesson-id={lesson.id}
      className={cn(
        'relative overflow-hidden rounded-2xl border p-4',
        'flex gap-4',
      )}
      style={{
        background: 'var(--bg-secondary)',
        borderColor: isBlockedByHeadman
          ? 'color-mix(in oklab, var(--accent-warning) 40%, var(--border-subtle))'
          : isActive
          ? 'var(--border-accent)'
          : 'var(--border-subtle)',
        boxShadow: isActive ? 'var(--glow-primary)' : 'none',
      }}
    >
      {/* Headman-lock stripe */}
      {isBlockedByHeadman && (
        <span
          aria-hidden="true"
          className="absolute inset-x-0 top-0 h-1"
          style={{
            background:
              'linear-gradient(90deg, var(--accent-warning) 0%, color-mix(in oklab, var(--accent-warning) 40%, transparent) 100%)',
          }}
        />
      )}

      {/* Left rail: time + station dot */}
      <div className="flex flex-col items-center gap-2 pt-0.5 shrink-0">
        <span
          className="text-xs font-semibold tabular-nums tracking-tight"
          style={{ color: 'var(--text-primary)' }}
        >
          {formatTime(lesson.startTime)}
        </span>

        <span
          aria-hidden="true"
          className="relative my-0.5 block size-3 shrink-0 rounded-full"
          style={{
            background: dot,
            boxShadow: isActive ? `0 0 12px ${dot}` : 'none',
          }}
        />

        <span
          className="text-[10px] tabular-nums"
          style={{ color: 'var(--text-muted)' }}
        >
          {formatTime(lesson.endTime)}
        </span>
      </div>

      {/* Right: subject + room + actions */}
      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <div className="flex items-start justify-between gap-2">
          {subjectLoading ? (
            <Skeleton className="h-5 w-3/4" />
          ) : (
            <h3
              className={cn(
                'line-clamp-2 text-base font-semibold leading-snug text-balance',
                isCancelled && 'line-through',
              )}
              style={{
                color: 'var(--text-primary)',
                fontFamily: 'var(--font-heading)',
              }}
            >
              {subjectName ?? 'Предмет'}
            </h3>
          )}
          <StatusBadge status={personalStatus ?? lesson.status} />
        </div>

        <p
          className="flex items-center gap-1 text-xs"
          style={{ color: 'var(--text-secondary)' }}
        >
          <MapPin size={12} weight="fill" aria-hidden="true" />
          Ауд. {lesson.room}
        </p>

        {homeworkPreview && homeworkPreview.count > 0 && !isCancelled && (
          <p
            className="flex items-center gap-1 text-xs"
            style={{ color: 'var(--accent-primary)' }}
          >
            <BookOpen
              size={12}
              weight="duotone"
              aria-hidden="true"
              style={{ flexShrink: 0 }}
            />
            <span className="truncate">{homeworkPreview.title}</span>
            {homeworkPreview.count > 1 && (
              <span
                className="shrink-0 rounded-full px-1.5 text-[10px] font-semibold leading-4"
                style={{
                  background:
                    'color-mix(in oklab, var(--accent-primary) 18%, transparent)',
                }}
              >
                +{homeworkPreview.count - 1}
              </span>
            )}
          </p>
        )}

        {/* Headman-lock notice (student sees why geo-checkin is unavailable) */}
        {isBlockedByHeadman && (
          <p
            className="mt-1 inline-flex items-center gap-1 text-xs"
            style={{ color: 'var(--accent-warning)' }}
          >
            <Lock size={12} weight="fill" aria-hidden="true" />
            {isHeadman
              ? 'Пара заблокирована. Посещаемость ставишь вручную.'
              : 'Пара заблокирована старостой. Отмечает староста.'}
          </p>
        )}

        {isActive && !isCancelled && (
          <div className="mt-3 flex items-center justify-between gap-3">
            {personalStatus ? (
              <div
                className="flex items-center gap-2 text-sm font-medium"
                style={{ color: 'var(--accent-primary)' }}
              >
                <CheckCircle size={18} weight="fill" aria-hidden="true" />
                <span>Отмечено</span>
              </div>
            ) : (
              onCheckin &&
              !isBlockedByHeadman && (
                <CheckInButton
                  onSuccess={onCheckin}
                  onError={onCheckinError}
                  disabled={isCheckinLoading}
                />
              )
            )}

            <motion.span
              key={attendanceCount}
              initial={{ y: -6, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{ type: 'spring', stiffness: 360, damping: 26 }}
              className="text-xs tabular-nums"
              style={{ color: 'var(--text-muted)' }}
            >
              {attendanceCount ?? 0} чел
            </motion.span>
          </div>
        )}

        {/* Footer actions row */}
        {(showActions || (isHeadman && isBlockable)) && (
          <div
            className="mt-2 flex items-center gap-2 border-t pt-2"
            style={{ borderColor: 'var(--border-subtle)' }}
          >
            {isHeadman && isBlockable && onToggleBlock && (
              <button
                type="button"
                onClick={onToggleBlock}
                className="inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-[11px] font-medium"
                style={{
                  background: isBlockedByHeadman
                    ? 'color-mix(in oklab, var(--accent-warning) 18%, transparent)'
                    : 'var(--bg-secondary)',
                  borderColor: isBlockedByHeadman
                    ? 'var(--accent-warning)'
                    : 'var(--border-subtle)',
                  color: isBlockedByHeadman
                    ? 'var(--accent-warning)'
                    : 'var(--text-secondary)',
                }}
              >
                {isBlockedByHeadman ? (
                  <>
                    <LockOpen size={12} weight="bold" /> Разблокировать
                  </>
                ) : (
                  <>
                    <Lock size={12} weight="bold" /> Заблокировать
                  </>
                )}
              </button>
            )}
            {showActions && (
              <button
                type="button"
                onClick={onOpenActions}
                aria-label="Действия с парой"
                className="ml-auto inline-flex items-center gap-1 rounded-full border px-3 py-1 text-[11px] font-medium"
                style={{
                  background: 'var(--bg-secondary)',
                  borderColor: 'var(--border-subtle)',
                  color: 'var(--text-secondary)',
                }}
              >
                <DotsThreeVertical size={14} weight="bold" />
                Действия
              </button>
            )}
          </div>
        )}
      </div>
    </motion.article>
  )
}
