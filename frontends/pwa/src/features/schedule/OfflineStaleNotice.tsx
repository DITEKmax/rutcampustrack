import { useState, useEffect } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { useNetworkStatus } from '@/shared/hooks/useNetworkStatus'

interface OfflineStaleNoticeProps {
  dataUpdatedAt?: number
}

function formatElapsed(ms: number): string {
  const minutes = Math.round(ms / 60000)
  if (minutes < 1) return 'только что'
  return `${minutes} мин назад`
}

export function OfflineStaleNotice({ dataUpdatedAt }: OfflineStaleNoticeProps) {
  const { isOnline } = useNetworkStatus()
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    if (isOnline) return

    const interval = setInterval(() => {
      setNow(Date.now())
    }, 60000)

    return () => clearInterval(interval)
  }, [isOnline])

  if (isOnline) return null

  const elapsed = dataUpdatedAt ? now - dataUpdatedAt : null

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0, height: 0 }}
        animate={{ opacity: 1, height: 'auto' }}
        exit={{ opacity: 0, height: 0 }}
        transition={{ duration: 0.2 }}
        role="status"
        className="bg-slate-50 text-slate-500 text-xs text-center py-1.5 px-4"
      >
        {elapsed !== null
          ? `Офлайн \u00B7 обновлено ${formatElapsed(elapsed)}`
          : 'Нет данных'}
      </motion.div>
    </AnimatePresence>
  )
}
