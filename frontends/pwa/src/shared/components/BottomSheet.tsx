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
  /** Close when the backdrop is tapped. Default true. */
  closeOnBackdrop?: boolean
  /** Close on Escape. Default true. */
  closeOnEscape?: boolean
  /** Close on vertical drag/swipe down. Default true. */
  closeOnSwipeDown?: boolean
  /** Show the small drag affordance at the top. Default true. */
  showDragHandle?: boolean
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
  closeOnBackdrop = true,
  closeOnEscape = true,
  closeOnSwipeDown = true,
  showDragHandle = true,
  children,
}: BottomSheetProps) {
  const reduceMotion = useReducedMotion()
  const onDragEnd = useSwipeHandler({
    verticalThreshold: 120,
    onSwipeDown: closeOnSwipeDown ? onClose : undefined,
  })

  useEffect(() => {
    if (!open || !closeOnEscape) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose, closeOnEscape])

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
            onClick={closeOnBackdrop ? onClose : undefined}
            className="fixed inset-0 z-[var(--z-overlay)] bg-black/55 backdrop-blur-sm"
            aria-hidden="true"
            data-bottom-sheet-backdrop="true"
            data-pull-to-refresh-ignore="true"
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
            data-bottom-sheet-panel="true"
            data-pull-to-refresh-ignore="true"
            className={cn(
              'fixed inset-x-0 bottom-0 z-[var(--z-modal)]',
              'rounded-t-[var(--radius-lg)] border-t',
              'flex flex-col overflow-hidden overscroll-contain pb-[env(safe-area-inset-bottom)]',
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
            {showDragHandle && (
              <div className="flex shrink-0 justify-center pt-[var(--space-2)] pb-[var(--space-1)]">
                <span
                  aria-hidden="true"
                  className="block h-1 w-10 rounded-full"
                  style={{ background: 'var(--border-default)' }}
                />
              </div>
            )}

            {heading !== undefined && (
              <div className="flex shrink-0 items-start gap-[var(--space-3)] px-[var(--space-5)] pb-[var(--space-3)] pt-[var(--space-3)]">
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

            <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">{children}</div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )
}
