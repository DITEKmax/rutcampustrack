import { useEffect } from 'react'
import { motion } from 'motion/react'

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

  return (
    <motion.div
      initial={{ y: 64, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      exit={{ y: 64, opacity: 0 }}
      transition={{ duration: isSuccess ? 0.2 : 0.15, ease: isSuccess ? 'easeOut' : 'easeIn' }}
      role={isSuccess ? 'status' : 'alert'}
      className={`fixed bottom-16 left-4 right-4 z-40 rounded-lg p-4 shadow-lg ${
        isSuccess
          ? 'bg-card border border-green-200 text-green-800'
          : 'bg-card border border-destructive/30 text-destructive'
      }`}
    >
      {isSuccess ? (
        <p className="font-medium text-sm">Отметка принята</p>
      ) : (
        <p className="font-medium text-sm">{message}</p>
      )}
    </motion.div>
  )
}
