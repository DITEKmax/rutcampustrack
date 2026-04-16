import { NavLink } from 'react-router'
import { motion } from 'motion/react'
import { cn } from '@/lib/utils'
import { useTabs } from './useTabs'
import { useNotificationCenter } from '@/features/notifications/NotificationCenter'

/**
 * RutCampusTrack — Bottom tab bar (brandbook §4.6, §5.4)
 *
 * Subway-inspired active indicator: the active tab's route pill slides with
 * Motion's shared-layout (layoutId) and carries a glowing station-dot below.
 * Icons swap between `regular` (idle) and `fill` (active) weight for extra
 * contrast, similar to the Phosphor duotone treatment used elsewhere.
 *
 * Backdrop blur + translucent surface so content scrolls *behind* the bar
 * instead of stopping at it. All colors flow from Transit Grid tokens via
 * the shadcn bridge configured in index.css.
 *
 * Tab list is role-aware: headman users (isHeadman=true) see a 5th "Группа"
 * tab before "Профиль". Tab array is managed by useTabs() hook.
 */

export function BottomNav() {
  const tabs = useTabs()
  const { unreadCount } = useNotificationCenter()

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
      <ul className="mx-auto flex max-w-md items-stretch justify-around px-2 pt-1.5 pb-1.5">
        {tabs.map(({ to, icon: Icon, label, badge }) => {
          const badgeValue = badge === 'unread' ? unreadCount : 0
          return (
            <li key={to} className="flex-1">
              <NavLink
                to={to}
                end={to === '/home'}
                className={({ isActive }) =>
                  cn(
                    'relative flex min-h-[52px] flex-col items-center justify-center gap-0.5 rounded-xl',
                    'text-[10px] font-medium leading-tight',
                    'transition-colors duration-200 ease-out',
                    isActive
                      ? 'text-[var(--accent-primary)]'
                      : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]',
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    {isActive && (
                      <motion.span
                        layoutId="tab-active-pill"
                        aria-hidden="true"
                        className={cn(
                          'absolute inset-x-1 inset-y-0.5 -z-10 rounded-xl',
                          'bg-[color-mix(in_oklab,var(--accent-primary)_12%,transparent)]',
                          'border border-[var(--border-accent)]',
                        )}
                        transition={{ type: 'spring', stiffness: 520, damping: 34 }}
                      />
                    )}

                    <span className="relative">
                      <Icon
                        size={22}
                        weight={isActive ? 'fill' : 'regular'}
                        aria-hidden="true"
                      />
                      {badgeValue > 0 && (
                        <span
                          aria-label={`Непрочитанных: ${badgeValue}`}
                          className="absolute -right-1.5 -top-1 min-w-[16px] rounded-full px-1 text-center text-[9px] font-bold leading-[16px] text-white"
                          style={{ background: 'var(--accent-danger, #ef4444)' }}
                        >
                          {badgeValue > 9 ? '9+' : badgeValue}
                        </span>
                      )}
                    </span>
                    <span className="truncate">{label}</span>

                    {isActive && (
                      <motion.span
                        layoutId="tab-active-dot"
                        aria-hidden="true"
                        className="absolute -bottom-0.5 size-1 rounded-full bg-[var(--accent-primary)]"
                        style={{ boxShadow: '0 0 6px var(--accent-primary)' }}
                        transition={{ type: 'spring', stiffness: 520, damping: 34 }}
                      />
                    )}
                  </>
                )}
              </NavLink>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
