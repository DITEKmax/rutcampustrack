import { motion } from 'motion/react'
import { CheckCircle, MapPin } from '@phosphor-icons/react'
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
}: LessonCardProps) {
  const { data: subjectName, isLoading: subjectLoading } = useSubjectName(lesson.subjectId)
  const isCancelled = lesson.status === 'CANCELLED'
  const isActive = lesson.status === 'ACTIVE'
  const dot = dotColor(lesson, personalStatus)

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
        borderColor: isActive ? 'var(--border-accent)' : 'var(--border-subtle)',
        boxShadow: isActive ? 'var(--glow-primary)' : 'none',
      }}
    >
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
              onCheckin && (
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
      </div>
    </motion.article>
  )
}
