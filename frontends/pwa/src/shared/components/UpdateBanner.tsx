import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowClockwise } from '@phosphor-icons/react'
import { registerSW } from 'virtual:pwa-register'
import { cn } from '@/lib/utils'

export function UpdateBanner() {
  const [needRefresh, setNeedRefresh] = useState(false)
  const [updateSW, setUpdateSW] = useState<((reload?: boolean) => Promise<void>) | null>(null)

  useEffect(() => {
    const update = registerSW({
      immediate: true,
      onNeedRefresh() {
        setNeedRefresh(true)
      },
    })
    setUpdateSW(() => update)
  }, [])

  const handleReload = () => {
    if (updateSW) {
      void updateSW(true)
    } else {
      window.location.reload()
    }
  }

  return (
    <AnimatePresence>
      {needRefresh && (
        <motion.div
          role="status"
          aria-live="polite"
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 16 }}
          transition={{ duration: 0.2, ease: 'easeOut' }}
          className={cn(
            'fixed inset-x-3 z-[var(--z-overlay)]',
            'bottom-[calc(env(safe-area-inset-bottom)+76px)]',
            'flex items-center justify-between gap-3',
            'rounded-2xl px-4 py-3 shadow-lg',
            'text-sm font-medium',
          )}
          style={{
            background: 'var(--bg-elevated)',
            color: 'var(--text-primary)',
            border: '1px solid color-mix(in oklab, var(--accent-primary) 32%, transparent)',
          }}
        >
          <span>Доступна новая версия</span>
          <button
            type="button"
            onClick={handleReload}
            className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold"
            style={{
              background: 'var(--accent-primary)',
              color: 'var(--bg-base)',
            }}
          >
            <ArrowClockwise size={14} weight="bold" aria-hidden="true" />
            Обновить
          </button>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
