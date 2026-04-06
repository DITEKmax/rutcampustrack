import { NavLink } from 'react-router'
import { House, Calendar, Fingerprint, User } from '@phosphor-icons/react'

const tabs = [
  { to: '/home', icon: House, label: 'Главная' },
  { to: '/schedule', icon: Calendar, label: 'Расписание' },
  { to: '/checkin', icon: Fingerprint, label: 'Отметка' },
  { to: '/profile', icon: User, label: 'Профиль' },
]

export function BottomNav() {
  return (
    <nav
      aria-label="Основная навигация"
      className="fixed bottom-0 left-0 right-0 h-14 bg-card border-t border-border flex items-center justify-around pb-[env(safe-area-inset-bottom)]"
    >
      {tabs.map(({ to, icon: Icon, label }) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) =>
            `relative flex flex-col items-center justify-center min-h-[44px] min-w-[44px] gap-0.5 text-xs ${
              isActive ? 'text-primary' : 'text-muted-foreground'
            }`
          }
        >
          {({ isActive }) => (
            <>
              <Icon size={24} weight={isActive ? 'fill' : 'bold'} />
              <span>{label}</span>
              {isActive && (
                <span className="absolute bottom-1 h-1 w-1 rounded-full bg-primary" />
              )}
            </>
          )}
        </NavLink>
      ))}
    </nav>
  )
}
