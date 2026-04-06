import { motion } from 'motion/react'
import { Check } from '@phosphor-icons/react'
import { useSubjectName } from './api'
import { StatusBadge } from './StatusBadge'
import { CheckInButton } from '@/features/checkin/CheckInButton'
import type { LessonResponse, AttendanceStatus } from './types'

interface LessonCardProps {
  lesson: LessonResponse
  attendanceCount?: number
  personalStatus?: AttendanceStatus | null
  onCheckin?: () => void
  onCheckinError?: (msg: string) => void
  isCheckinLoading?: boolean
}

function formatTime(time: string): string {
  // Strip seconds: HH:mm:ss -> HH:mm
  return time.slice(0, 5)
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

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      data-lesson-id={lesson.id}
      className={`bg-card border border-border rounded-xl p-4 ${isCancelled ? 'opacity-60' : ''}`}
    >
      {/* Row 1: Time + Status */}
      <div className="flex items-center justify-between">
        <span className="text-base font-semibold">
          {formatTime(lesson.startTime)} - {formatTime(lesson.endTime)}
        </span>
        {personalStatus ? (
          <StatusBadge status={personalStatus} />
        ) : (
          <StatusBadge status={lesson.status} />
        )}
      </div>

      {/* Row 2: Subject name */}
      <div className="mt-1">
        {subjectLoading ? (
          <div className="h-6 w-48 bg-muted rounded animate-pulse" />
        ) : (
          <h3
            className={`text-xl font-semibold line-clamp-2 leading-snug ${isCancelled ? 'line-through' : ''}`}
          >
            {subjectName ?? 'Предмет'}
          </h3>
        )}
      </div>

      {/* Row 3: Room */}
      <p className="text-sm text-muted-foreground mt-1">Ауд. {lesson.room}</p>

      {/* Row 4: Check-in (only for ACTIVE lessons) */}
      {isActive && !isCancelled && (
        <div className="flex items-center justify-between mt-3">
          {personalStatus ? (
            <div className="flex items-center gap-2">
              <Check size={20} weight="bold" className="text-green-600" />
              <StatusBadge status={personalStatus} />
            </div>
          ) : (
            <>
              {onCheckin && (
                <CheckInButton
                  onSuccess={onCheckin}
                  onError={onCheckinError}
                  disabled={isCheckinLoading}
                />
              )}
              <motion.span
                key={attendanceCount}
                initial={{ y: -8, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                className="text-xs text-muted-foreground"
              >
                {attendanceCount ?? 0} чел
              </motion.span>
            </>
          )}
        </div>
      )}
    </motion.div>
  )
}
