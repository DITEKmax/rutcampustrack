import { useEffect } from 'react'
import { NavLink } from 'react-router'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { BookOpen, ClockCountdown, FileText, User, X, type Icon } from '@phosphor-icons/react'
import { cn } from '@/lib/utils'

interface DrawerItem {
  to: string
  icon: Icon
  label: string
}

const ITEMS: DrawerItem[] = [
  { to: '/homework', icon: BookOpen, label: 'Домашние задания' },
  { to: '/late-checkin', icon: ClockCountdown, label: 'Запрос отметки' },
  { to: '/excuses', icon: FileText, label: 'Уважительные пропуски' },
  { to: '/profile', icon: User, label: 'Профиль' },
]

interface DrawerMenuProps {
  open: boolean
  onClose: () => void
}

/**
 * Slide-in right drawer with secondary navigation (homework, profile).
 * Primary tabs stay in the BottomNav; less-frequent destinations live here
 * so the bottom bar doesn't overflow on 5-6 tabs for headmen.
 *
 * Closes on: backdrop tap, X button, Escape, route change (NavLink click).
 */
export function DrawerMenu({ open, onClose }: DrawerMenuProps) {
  const reduceMotion = useReducedMotion()

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
            key="drawer-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: reduceMotion ? 0 : 0.18 }}
            onClick={onClose}
            className="fixed inset-0 z-[var(--z-modal,60)] bg-black/40 backdrop-blur-sm"
            aria-hidden="true"
          />
          <motion.aside
            key="drawer-panel"
            role="dialog"
            aria-label="Меню"
            aria-modal="true"
            initial={reduceMotion ? { opacity: 0 } : { x: '100%' }}
            animate={reduceMotion ? { opacity: 1 } : { x: 0 }}
            exit={reduceMotion ? { opacity: 0 } : { x: '100%' }}
            transition={{
              type: 'spring',
              stiffness: 520,
              damping: 42,
              mass: 0.8,
            }}
            className={cn(
              'fixed right-0 top-0 z-[var(--z-modal,60)]',
              'flex h-dvh w-[min(84vw,320px)] flex-col',
              'border-l shadow-2xl',
            )}
            style={{
              background: 'var(--bg-secondary)',
              borderColor: 'var(--border-subtle)',
              paddingTop: 'env(safe-area-inset-top)',
              paddingBottom: 'env(safe-area-inset-bottom)',
            }}
          >
            <header
              className="flex items-center justify-between gap-2 border-b px-4 h-14"
              style={{ borderColor: 'var(--border-subtle)' }}
            >
              <p
                className="text-sm font-semibold tracking-tight"
                style={{
                  color: 'var(--text-primary)',
                  fontFamily: 'var(--font-heading)',
                }}
              >
                Меню
              </p>
              <button
                type="button"
                onClick={onClose}
                aria-label="Закрыть меню"
                className="grid size-9 place-items-center rounded-full transition-colors"
                style={{ color: 'var(--text-secondary)' }}
              >
                <X size={20} weight="bold" />
              </button>
            </header>

            <nav className="flex-1 overflow-y-auto p-2">
              <ul className="flex flex-col gap-1">
                {ITEMS.map(({ to, icon: Icon, label }) => (
                  <li key={to}>
                    <NavLink
                      to={to}
                      onClick={onClose}
                      className={({ isActive }) =>
                        cn(
                          'flex items-center gap-3 rounded-xl px-3 py-3',
                          'text-sm font-medium',
                          'transition-colors duration-150',
                          isActive
                            ? 'text-[var(--accent-primary)]'
                            : 'text-[var(--text-primary)] hover:bg-[var(--bg-tertiary,var(--bg-secondary))]',
                        )
                      }
                      style={({ isActive }) =>
                        isActive
                          ? {
                              background:
                                'color-mix(in oklab, var(--accent-primary) 12%, transparent)',
                            }
                          : undefined
                      }
                    >
                      {({ isActive }) => (
                        <>
                          <Icon
                            size={22}
                            weight={isActive ? 'fill' : 'regular'}
                            aria-hidden="true"
                          />
                          <span>{label}</span>
                        </>
                      )}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </nav>
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  )
}
