import { useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { SegmentedControl } from '@/shared/components/SegmentedControl'
import { useMarkAttendance } from '@/features/headman/shared/headmanApi'
import type { AttendanceStatus } from '@/features/headman/shared/types'

export interface JournalStudentRowProps {
  studentId: number
  studentName: string
  lessonId: number
  initialStatus: AttendanceStatus
}

export function JournalStudentRow({
  studentId,
  studentName,
  lessonId,
  initialStatus,
}: JournalStudentRowProps) {
  const [status, setStatus] = useState<AttendanceStatus>(initialStatus)
  const [showError, setShowError] = useState(false)
  const { mutate } = useMarkAttendance()

  const handleChange = (newStatus: AttendanceStatus) => {
    const previous = status
    setStatus(newStatus) // optimistic update
    setShowError(false)
    mutate(
      { lessonId, userId: studentId, status: newStatus },
      {
        onError: () => {
          setStatus(previous) // revert
          setShowError(true)
          setTimeout(() => setShowError(false), 2000)
        },
      },
    )
  }

  return (
    <div
      className="relative flex items-center justify-between p-3 rounded-lg mb-2 min-h-[56px]"
      style={{ background: 'var(--bg-secondary)' }}
    >
      <span className="text-sm font-semibold">{studentName}</span>
      <SegmentedControl
        value={status}
        onValueChange={handleChange}
        ariaLabel={`Посещение — ${studentName}`}
      />
      <AnimatePresence>
        {showError && (
          <motion.div
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.15 }}
            role="alert"
            className="absolute -top-6 right-0 text-xs font-semibold"
            style={{ color: 'var(--accent-danger)' }}
          >
            Ошибка. Попробуйте ещё раз.
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
