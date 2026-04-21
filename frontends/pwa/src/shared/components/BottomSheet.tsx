import { useEffect } from 'react'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { X } from '@phosphor-icons/react'
import { cn } from '@/lib/utils'
import { useSwipeHandler } from '@/shared/hooks/useSwipeHandler'

interface BottomSheetProps {
  open: boolean
  onClose: () => void
  /** Aria label for screen readers. */
  title: string
  /** Optional heading shown at the top of the sheet (non-aria label). */
  heading?: React.ReactNode
  /** Optional subtitle below the heading. */
  subtitle?: React.ReactNode
  /**
   * Max height of the sheet as CSS value. Default `92vh`.
   * Use lower values for short content to avoid empty space.
   */
  maxHeight?: string
  /** Extra class names on the sheet panel. */
  className?: string
  children: React.ReactNode
}

/**
 * Unified bottom sheet (M07 G6/7).
 *
 * Замена трём inline-реализациям (LessonActionsSheet, HeadmanLessonSheet,
 * и любой будущий sheet в PWA). Включает:
 * - backdrop с fade + click-to-close,
 * - slide-up с spring easing,
 * - drag-handle + swipe-down-to-close (порог через `useSwipeHandler`),
 * - Escape-to-close,
 * - close-button в header,
 * - `prefers-reduced-motion` — отключает slide + drag, оставляет fade.
 *
 * Children получают готовый scrollable контейнер (overflow-y-auto).
 * Heading/subtitle — опциональный header-шаблон; если нужен custom
 * header, не передавать `heading`/`subtitle` и рендерить свой в children.
 */
export function BottomSheet({
  open,
  onClose,
  title,
  heading,
  subtitle,
  maxHeight = '92vh',
  className,
  children,
}: BottomSheetProps) {
  const reduceMotion = useReducedMotion()
  const onDragEnd = useSwipeHandler({
    verticalThreshold: 120,
    onSwipeDown: onClose,
  })

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            key="sheet-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: reduceMotion ? 0 : 0.2 }}
            onClick={onClose}
            className="fixed inset-0 z-[var(--z-overlay)] bg-black/55 backdrop-blur-sm"
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
            drag={reduceMotion ? false : 'y'}
            dragConstraints={{ top: 0, bottom: 0 }}
            dragElastic={{ top: 0, bottom: 0.35 }}
            onDragEnd={onDragEnd}
            className={cn(
              'fixed inset-x-0 bottom-0 z-[var(--z-modal)]',
              'rounded-t-[var(--radius-lg)] border-t',
              'flex flex-col pb-[env(safe-area-inset-bottom)]',
              className,
            )}
            style={{
              background: 'var(--bg-elevated)',
              borderColor: 'var(--border-default)',
              boxShadow: 'var(--shadow-lg)',
              maxHeight,
            }}
          >
            {/* Drag handle */}
            <div className="flex justify-center pt-[var(--space-2)] pb-[var(--space-1)]">
              <span
                aria-hidden="true"
                className="block h-1 w-10 rounded-full"
                style={{ background: 'var(--border-default)' }}
              />
            </div>

            {heading !== undefined && (
              <div className="flex items-start gap-[var(--space-3)] px-[var(--space-5)] pb-[var(--space-3)]">
                <div className="min-w-0 flex-1">
                  <h2
                    className="line-clamp-2 text-[var(--text-lg)] font-semibold leading-snug"
                    style={{
                      color: 'var(--text-primary)',
                      fontFamily: 'var(--font-heading)',
                    }}
                  >
                    {heading}
                  </h2>
                  {subtitle !== undefined && (
                    <p
                      className="mt-0.5 text-[var(--text-xs)]"
                      style={{ color: 'var(--text-muted)' }}
                    >
                      {subtitle}
                    </p>
                  )}
                </div>
                <button
                  type="button"
                  onClick={onClose}
                  aria-label="Закрыть"
                  className="grid size-9 place-items-center rounded-full"
                  style={{ color: 'var(--text-secondary)' }}
                >
                  <X size={18} weight="bold" />
                </button>
              </div>
            )}

            <div className="flex-1 overflow-y-auto">{children}</div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )
}
