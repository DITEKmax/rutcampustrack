import { useState, useEffect } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { CloudSlash } from '@phosphor-icons/react'
import { useNetworkStatus } from '@/shared/hooks/useNetworkStatus'

/**
 * Stale-data notice shown inside the schedule / check-in pages when the
 * device is offline. Complements the top-level OfflineBanner by telling the
 * user *how old* the cached data is.
 *
 * Uses Transit Grid warning tokens (amber) to signal "informational, not
 * an error". Returns `null` when online so the schedule test
 * (`container.innerHTML === ''`) passes.
 */
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
    const interval = setInterval(() => setNow(Date.now()), 60000)
    return () => clearInterval(interval)
  }, [isOnline])

  if (isOnline) return null

  const elapsed = dataUpdatedAt ? now - dataUpdatedAt : null

  return (
    <AnimatePresence>
      <motion.div
        key="stale-notice"
        initial={{ opacity: 0, y: -4 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -4 }}
        transition={{ duration: 0.18, ease: 'easeOut' }}
        role="status"
        aria-live="polite"
        className="mx-4 mt-3 flex items-center justify-center gap-2 rounded-full px-3 py-1 text-[11px] font-medium"
        style={{
          background: 'color-mix(in oklab, var(--accent-warning) 14%, transparent)',
          color: 'var(--accent-warning)',
          border: '1px solid color-mix(in oklab, var(--accent-warning) 28%, transparent)',
        }}
      >
        <CloudSlash size={12} weight="bold" aria-hidden="true" />
        <span>
          {elapsed !== null
            ? `Офлайн \u00B7 обновлено ${formatElapsed(elapsed)}`
            : 'Нет данных'}
        </span>
      </motion.div>
    </AnimatePresence>
  )
}
