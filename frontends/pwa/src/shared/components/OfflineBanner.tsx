import { AnimatePresence, motion } from 'motion/react'
import { WifiSlash } from '@phosphor-icons/react'
import { useNetworkStatus } from '@/shared/hooks/useNetworkStatus'
import { cn } from '@/lib/utils'

/**
 * Offline banner — slides down from the top when the network drops.
 *
 * Uses the Transit Grid warning accent (yellow) so it reads as a status
 * notification rather than an error. Sits above the header via the sticky
 * z-scale so it is always visible.
 */
export function OfflineBanner() {
  const { isOnline } = useNetworkStatus()

  return (
    <AnimatePresence>
      {!isOnline && (
        <motion.div
          role="status"
          aria-live="polite"
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -8 }}
          transition={{ duration: 0.18, ease: 'easeOut' }}
          className={cn(
            'fixed inset-x-0 top-0 z-[var(--z-overlay)]',
            'flex items-center justify-center gap-2',
            'min-h-8 px-4 py-1.5',
            'pt-[calc(env(safe-area-inset-top)+6px)]',
            'text-xs font-medium',
          )}
          style={{
            background: 'color-mix(in oklab, var(--accent-warning) 18%, var(--bg-elevated))',
            color: 'var(--accent-warning)',
            borderBottom: '1px solid color-mix(in oklab, var(--accent-warning) 32%, transparent)',
          }}
        >
          <WifiSlash size={14} weight="bold" aria-hidden="true" />
          <span>Нет подключения к интернету</span>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
