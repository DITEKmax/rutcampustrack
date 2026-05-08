import type { ReactNode } from 'react'
import { useCallback, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { X } from '@phosphor-icons/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { cn } from '@/lib/utils'
import { useSwipeHandler } from '@/shared/hooks/useSwipeHandler'

interface BottomSheetProps {
  open: boolean
  onClose: () => void
  title: string
  heading?: ReactNode
  subtitle?: ReactNode
  maxHeight?: string
  className?: string
  closeOnBackdrop?: boolean
  closeOnEscape?: boolean
  closeOnSwipeDown?: boolean
  showDragHandle?: boolean
  children: ReactNode
}

export function BottomSheet({
  open,
  onClose,
  title,
  heading,
  subtitle,
  maxHeight = '92vh',
  className,
  closeOnBackdrop = true,
  closeOnEscape = true,
  closeOnSwipeDown = true,
  showDragHandle = true,
  children,
}: BottomSheetProps) {
  const reduceMotion = useReducedMotion()
  const closeWithFeedback = useCallback(() => {
    if (hapticFeedback.selectionChanged.isAvailable()) {
      hapticFeedback.selectionChanged()
    }
    onClose()
  }, [onClose])
  const onDragEnd = useSwipeHandler({
    verticalThreshold: 120,
    onSwipeDown: closeOnSwipeDown ? closeWithFeedback : undefined,
  })

  useEffect(() => {
    if (!open || !closeOnEscape) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeWithFeedback()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [closeOnEscape, closeWithFeedback, open])

  const sheet = (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            key="sheet-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: reduceMotion ? 0 : 0.2 }}
            onClick={closeOnBackdrop ? closeWithFeedback : undefined}
            className="fixed inset-0 z-[var(--z-overlay,50)] bg-black/55 backdrop-blur-sm"
            aria-hidden="true"
          />

          <motion.div
            key="sheet"
            role="dialog"
            aria-modal="true"
            aria-label={title}
            initial={reduceMotion ? { opacity: 0 } : { y: '100%' }}
            animate={reduceMotion ? { opacity: 1 } : { y: 0 }}
            exit={reduceMotion ? { opacity: 0 } : { y: '100%' }}
            transition={{
              duration: reduceMotion ? 0 : 0.3,
              ease: [0.16, 1, 0.3, 1],
            }}
            drag={reduceMotion || !closeOnSwipeDown ? false : 'y'}
            dragConstraints={{ top: 0, bottom: 0 }}
            dragElastic={{ top: 0, bottom: 0.35 }}
            onDragEnd={closeOnSwipeDown ? onDragEnd : undefined}
            className={cn(
              'fixed inset-x-0 bottom-0 z-[var(--z-modal,60)]',
              'flex flex-col overflow-hidden rounded-t-[var(--radius-lg)] border-t',
              'overscroll-contain pb-[env(safe-area-inset-bottom)]',
              className,
            )}
            style={{
              background: 'var(--bg-elevated)',
              borderColor: 'var(--border-default)',
              boxShadow: 'var(--shadow-lg)',
              maxHeight,
            }}
          >
            {showDragHandle && (
              <div className="flex shrink-0 justify-center pb-1 pt-2">
                <span
                  aria-hidden="true"
                  className="block h-1 w-10 rounded-full"
                  style={{ background: 'var(--border-default)' }}
                />
              </div>
            )}

            {heading !== undefined && (
              <div className="flex shrink-0 items-start gap-3 px-5 pb-3 pt-3">
                <div className="min-w-0 flex-1">
                  <h2
                    className="line-clamp-2 text-lg font-semibold leading-snug"
                    style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-heading)' }}
                  >
                    {heading}
                  </h2>
                  {subtitle !== undefined && (
                    <p className="mt-0.5 text-xs" style={{ color: 'var(--text-muted)' }}>
                      {subtitle}
                    </p>
                  )}
                </div>
                <button
                  type="button"
                  onClick={closeWithFeedback}
                  aria-label="Закрыть"
                  className="grid size-9 place-items-center rounded-full"
                  style={{ color: 'var(--text-secondary)' }}
                >
                  <X size={18} weight="bold" />
                </button>
              </div>
            )}

            <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">{children}</div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )

  return typeof document === 'undefined' ? sheet : createPortal(sheet, document.body)
}
