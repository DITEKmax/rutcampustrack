import { motion } from 'motion/react'
import { useSubjectName } from './api'
import { StatusBadge } from './StatusBadge'
import type { LessonResponse, AttendanceStatus } from './types'

interface LessonCardProps {
  lesson: LessonResponse
  attendanceCount?: number
  personalStatus?: AttendanceStatus | null
  onCheckin?: () => void
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
        <StatusBadge status={lesson.status} />
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
            <StatusBadge status={personalStatus} />
          ) : (
            <>
              {onCheckin && (
                <button
                  onClick={onCheckin}
                  disabled={isCheckinLoading}
                  className="bg-primary text-primary-foreground rounded-lg px-4 py-2 text-sm font-medium min-h-[44px] disabled:opacity-50"
                >
                  {isCheckinLoading ? (
                    <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
                  ) : (
                    'Отметиться'
                  )}
                </button>
              )}
              {attendanceCount !== undefined && (
                <span className="text-xs text-muted-foreground">
                  {attendanceCount} / ? чел
                </span>
              )}
            </>
          )}
        </div>
      )}
    </motion.div>
  )
}
