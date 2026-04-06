import { motion } from 'motion/react'
import { Check } from '@phosphor-icons/react'
import type { HomeworkResponse } from './types'
import { useSubjectName } from './api'

interface HomeworkItemProps {
  homework: HomeworkResponse
  onToggle: (id: number, completed: boolean) => void
  error: string | null
}

export function HomeworkItem({ homework, onToggle, error }: HomeworkItemProps) {
  const { data: subjectName } = useSubjectName(homework.subjectId)

  return (
    <div className={`flex items-start gap-3 min-h-[56px]${homework.completed ? ' opacity-60' : ''}`}>
      {/* Custom checkbox with 44x44 touch target */}
      <motion.button
        role="checkbox"
        aria-checked={homework.completed}
        onClick={() => onToggle(homework.id, !homework.completed)}
        className={`flex-shrink-0 w-6 h-6 mt-1 rounded border-2 flex items-center justify-center min-w-[44px] min-h-[44px] -m-[9px] ${
          homework.completed
            ? 'bg-primary border-primary'
            : 'border-muted-foreground bg-transparent'
        }`}
        whileTap={{ scale: 0.85 }}
        animate={{ scale: 1 }}
        transition={{ type: 'spring', stiffness: 400, damping: 20 }}
      >
        {homework.completed && (
          <Check size={16} weight="bold" className="text-primary-foreground" />
        )}
      </motion.button>

      {/* Content */}
      <div className="flex flex-col min-w-0 pt-1">
        <span
          className={`text-base font-semibold leading-snug${homework.completed ? ' line-through opacity-60' : ''}`}
        >
          {homework.title}
        </span>
        {subjectName && (
          <span className="text-sm text-muted-foreground">{subjectName}</span>
        )}
        {error && (
          <span className="text-destructive text-xs">{error}</span>
        )}
      </div>
    </div>
  )
}
