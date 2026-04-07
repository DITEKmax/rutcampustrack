import { useLocation, useNavigate } from 'react-router'
import { CalendarBlank, ChartBar, BookOpen } from '@phosphor-icons/react'

const tabs = [
  { path: '/', label: 'Расписание', Icon: CalendarBlank },
  { path: '/stats', label: 'Статистика', Icon: ChartBar },
  { path: '/homework', label: 'ДЗ', Icon: BookOpen },
] as const

export function BottomNav() {
  const location = useLocation()
  const navigate = useNavigate()

  return (
    <nav className="fixed bottom-0 left-0 right-0 h-14 bg-background border-t border-border pb-[env(safe-area-inset-bottom)] flex z-50">
      {tabs.map(({ path, label, Icon }) => {
        const isActive = location.pathname === path
        return (
          <button
            key={path}
            onClick={() => navigate(path)}
            className="flex-1 flex flex-col items-center justify-center gap-1 min-h-[56px]"
          >
            <div className="relative">
              <Icon
                size={24}
                weight={isActive ? 'fill' : 'regular'}
                className={isActive ? 'text-primary' : 'text-muted-foreground'}
              />
              {isActive && (
                <span className="absolute -bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-primary" />
              )}
            </div>
            <span
              className={`text-xs ${isActive ? 'font-semibold text-primary' : 'font-normal text-muted-foreground'}`}
            >
              {label}
            </span>
          </button>
        )
      })}
    </nav>
  )
}
