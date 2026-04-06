import { AnimatePresence, motion } from 'motion/react'
import { useNetworkStatus } from '@/shared/hooks/useNetworkStatus'

export function OfflineBanner() {
  const { isOnline } = useNetworkStatus()

  return (
    <AnimatePresence>
      {!isOnline && (
        <motion.div
          initial={{ y: -32 }}
          animate={{ y: 0 }}
          exit={{ y: -32 }}
          transition={{ duration: 0.2, ease: 'easeOut' }}
          className="fixed top-0 left-0 right-0 z-50 h-8 bg-slate-100 text-slate-600 text-sm flex items-center justify-center"
        >
          Нет подключения к интернету
        </motion.div>
      )}
    </AnimatePresence>
  )
}
