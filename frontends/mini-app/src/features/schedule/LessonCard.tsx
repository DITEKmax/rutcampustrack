import { motion } from 'motion/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { StatusBadge } from './StatusBadge'
import type { LessonResponse } from './types'

interface LessonCardProps {
  lesson: LessonResponse
  subjectName: string
  onCheckin: (lessonId: number) => void
}

export function LessonCard({ lesson, subjectName, onCheckin }: LessonCardProps) {
  const isActive = lesson.status === 'ACTIVE'
  const isCancelled = lesson.status === 'CANCELLED'

  const handleCheckin = () => {
    if (hapticFeedback.impactOccurred.isAvailable()) {
      hapticFeedback.impactOccurred('light')
    }
    onCheckin(lesson.id)
  }

  return (
    <motion.div
      className={[
        'bg-card border border-border rounded-xl p-4',
        isActive ? 'border-l-4 border-l-primary' : '',
        isCancelled ? 'opacity-60' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      onClick={isActive ? handleCheckin : undefined}
      style={isActive ? { cursor: 'pointer', minHeight: 44 } : undefined}
    >
      {/* Row 1: time + status badge */}
      <div className="flex items-center justify-between">
        <span className="text-base font-semibold">
          {lesson.startTime.slice(0, 5)} — {lesson.endTime.slice(0, 5)}
        </span>
        <StatusBadge status={lesson.status} />
      </div>

      {/* Row 2: subject name */}
      <p className="text-base font-semibold line-clamp-2 mt-1">{subjectName}</p>

      {/* Row 3: room */}
      <p className="text-sm text-muted-foreground mt-1">Ауд. {lesson.room}</p>

      {/* Row 4: CTA for ACTIVE only */}
      {isActive && (
        <p className="text-sm font-semibold text-primary mt-2">Войти на пару →</p>
      )}
    </motion.div>
  )
}
