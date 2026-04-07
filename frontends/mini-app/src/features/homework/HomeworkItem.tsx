import { CheckSquare, Square } from '@phosphor-icons/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { motion } from 'motion/react'
import type { HomeworkResponse } from './types'

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
      className="bg-card border border-border rounded-xl px-4 py-3 flex items-start gap-3"
      animate={{ opacity: homework.completed ? 0.6 : 1 }}
      transition={{ duration: 0.15 }}
    >
      <button
        onClick={handleToggle}
        className="min-w-[44px] min-h-[44px] flex items-center justify-center -ml-3 -mt-1"
      >
        {homework.completed ? (
          <CheckSquare size={20} weight="fill" className="text-primary" />
        ) : (
          <Square size={20} weight="regular" className="text-muted-foreground" />
        )}
      </button>
      <div className="flex-1 min-w-0">
        <p className={`text-sm font-semibold ${homework.completed ? 'line-through text-muted-foreground' : ''}`}>
          {homework.title}
        </p>
        {homework.description && (
          <p className="text-xs text-muted-foreground mt-0.5">{homework.description}</p>
        )}
        {homework.dueDate && (
          <p className="text-xs text-muted-foreground mt-0.5">До {homework.dueDate}</p>
        )}
      </div>
    </motion.div>
  )
}
