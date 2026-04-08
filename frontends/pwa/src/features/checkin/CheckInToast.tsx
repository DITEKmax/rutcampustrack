import { useEffect } from 'react'
import { motion } from 'motion/react'
import { CheckCircle, WarningCircle } from '@phosphor-icons/react'

/**
 * Check-in feedback toast (brandbook §5.4 "micro-interactions: успешный
 * чекин — pulse + confetti-burst").
 *
 * Two tones: success uses --accent-primary (the brand green), error uses
 * --accent-danger. Auto-dismisses after 3s (success) / 5s (error) — timings
 * preserved from the previous implementation so the 2 test cases pass.
 *
 * Only compositor-prop animations (opacity + translateY).
 */
interface CheckInToastProps {
  type: 'success' | 'error'
  message: string
  onDismiss: () => void
}

export function CheckInToast({ type, message, onDismiss }: CheckInToastProps) {
  useEffect(() => {
    const duration = type === 'success' ? 3000 : 5000
    const timer = setTimeout(onDismiss, duration)
    return () => clearTimeout(timer)
  }, [type, onDismiss])

  const isSuccess = type === 'success'
  const displayMessage = isSuccess ? 'Отметка принята' : message

  return (
    <motion.div
      initial={{ opacity: 0, y: 32 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: 32 }}
      transition={{ duration: 0.2, ease: 'easeOut' }}
      role={isSuccess ? 'status' : 'alert'}
      className="fixed inset-x-4 bottom-20 z-[var(--z-toast)] mx-auto flex max-w-sm items-center gap-3 rounded-xl px-4 py-3"
      style={{
        background: 'var(--bg-elevated)',
        border: `1px solid ${
          isSuccess
            ? 'color-mix(in oklab, var(--accent-primary) 32%, transparent)'
            : 'color-mix(in oklab, var(--accent-danger) 32%, transparent)'
        }`,
        boxShadow: 'var(--shadow-lg)',
      }}
    >
      <span
        aria-hidden="true"
        className="grid size-9 place-items-center rounded-full"
        style={{
          background: isSuccess
            ? 'color-mix(in oklab, var(--accent-primary) 18%, transparent)'
            : 'color-mix(in oklab, var(--accent-danger) 18%, transparent)',
          color: isSuccess ? 'var(--accent-primary)' : 'var(--accent-danger)',
        }}
      >
        {isSuccess ? (
          <CheckCircle size={20} weight="fill" />
        ) : (
          <WarningCircle size={20} weight="fill" />
        )}
      </span>
      <p
        className="text-sm font-medium leading-tight text-pretty"
        style={{ color: 'var(--text-primary)' }}
      >
        {displayMessage}
      </p>
    </motion.div>
  )
}
