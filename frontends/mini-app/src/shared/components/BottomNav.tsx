import { useLocation, useNavigate } from 'react-router'
import { motion } from 'motion/react'
import { CalendarBlank, ChartBar, BookOpen, type Icon } from '@phosphor-icons/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { cn } from '@/lib/utils'

/**
 * Telegram Mini App bottom tab bar (brandbook §4.6, §5.4).
 *
 * Subway-inspired active indicator: the active tab pill slides via Motion's
 * shared-layout (`layoutId`) with a station-dot underneath. Telegram haptic
 * feedback fires on tap so users feel the navigation.
 *
 * Matches the PWA BottomNav visual language so someone opening the mini-app
 * after using the PWA (or vice versa) feels at home.
 */
interface Tab {
  path: string
  label: string
  Icon: Icon
}

const tabs: Tab[] = [
  { path: '/', label: 'Расписание', Icon: CalendarBlank },
  { path: '/stats', label: 'Статистика', Icon: ChartBar },
  { path: '/homework', label: 'ДЗ', Icon: BookOpen },
]

export function BottomNav() {
  const location = useLocation()
  const navigate = useNavigate()

  const handleTap = (path: string) => {
    if (location.pathname === path) return
    if (hapticFeedback.selectionChanged.isAvailable()) {
      hapticFeedback.selectionChanged()
    }
    navigate(path)
  }

  return (
    <nav
      aria-label="Основная навигация"
      className={cn(
        'fixed inset-x-0 bottom-0 z-[var(--z-sticky)]',
        'pb-[env(safe-area-inset-bottom)]',
        'border-t border-border',
        'bg-card/80 backdrop-blur-xl backdrop-saturate-150',
      )}
    >
      <ul className="mx-auto flex items-stretch justify-around px-2 py-1.5">
        {tabs.map(({ path, label, Icon }) => {
          const isActive = location.pathname === path
          return (
            <li key={path} className="flex-1">
              <button
                type="button"
                onClick={() => handleTap(path)}
                aria-current={isActive ? 'page' : undefined}
                className={cn(
                  'relative flex min-h-[52px] w-full flex-col items-center justify-center gap-0.5 rounded-xl',
                  'text-[10px] font-medium leading-tight',
                  'transition-colors duration-200 ease-out',
                  isActive
                    ? 'text-[var(--accent-primary)]'
                    : 'text-[var(--text-secondary)]',
                )}
              >
                {isActive && (
                  <motion.span
                    layoutId="mini-tab-active-pill"
                    aria-hidden="true"
                    className={cn(
                      'absolute inset-x-1 inset-y-0.5 -z-10 rounded-xl',
                      'bg-[color-mix(in_oklab,var(--accent-primary)_12%,transparent)]',
                      'border border-[var(--border-accent)]',
                    )}
                    transition={{ type: 'spring', stiffness: 520, damping: 34 }}
                  />
                )}

                <Icon size={22} weight={isActive ? 'fill' : 'regular'} aria-hidden="true" />
                <span className="truncate">{label}</span>

                {isActive && (
                  <motion.span
                    layoutId="mini-tab-active-dot"
                    aria-hidden="true"
                    className="absolute -bottom-0.5 size-1 rounded-full bg-[var(--accent-primary)]"
                    style={{ boxShadow: '0 0 6px var(--accent-primary)' }}
                    transition={{ type: 'spring', stiffness: 520, damping: 34 }}
                  />
                )}
              </button>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
