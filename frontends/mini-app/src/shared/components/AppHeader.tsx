import { useLocation } from 'react-router'
import { List } from '@phosphor-icons/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { ThemeToggle } from '@/shared/theme/ThemeToggle'
import { cn } from '@/lib/utils'

/**
 * Mini App top header (brandbook §4.6, §7).
 *
 * Compact sticky header with brand mark, current page title and theme toggle.
 * Telegram's native theme seeds the initial theme via TelegramThemeProvider;
 * the toggle lets users override it.
 */
const routeLabels: Record<string, string> = {
  '/': 'Главная',
  '/home': 'Главная',
  '/schedule': 'Расписание',
  '/stats': 'Статистика',
  '/homework': 'Домашние задания',
  '/late-checkin': 'Запрос отметки',
  '/excuses': 'Уважительные пропуски',
  '/profile': 'Профиль',
  '/group': 'Группа',
  '/group/overview': 'Обзор группы',
  '/group/students': 'Студенты',
  '/group/subjects': 'Предметы',
  '/group/journal': 'Журнал',
  '/group/stats': 'Статистика',
  '/group/excuses': 'Уважительные пропуски',
  '/group/late-checkin': 'Запросы отметки',
}

function matchTitle(pathname: string): string {
  if (routeLabels[pathname]) return routeLabels[pathname]
  if (pathname.startsWith('/checkin')) return 'Отметка'
  for (const key of Object.keys(routeLabels)) {
    if (key !== '/' && pathname.startsWith(key + '/')) return routeLabels[key]
  }
  return 'RutTrack'
}

interface AppHeaderProps {
  onOpenMenu: () => void
}

export function AppHeader({ onOpenMenu }: AppHeaderProps) {
  const { pathname } = useLocation()
  const title = matchTitle(pathname)

  const handleOpenMenu = () => {
    if (hapticFeedback.selectionChanged.isAvailable()) {
      hapticFeedback.selectionChanged()
    }
    onOpenMenu()
  }

  return (
    <header
      className={cn(
        'sticky top-0 z-[var(--z-sticky)]',
        'flex items-center justify-between gap-3',
        'h-12 px-3',
        'pt-[env(safe-area-inset-top)]',
        'border-b border-border',
        'bg-background/85 backdrop-blur-xl backdrop-saturate-150',
      )}
    >
      <div className="flex min-w-0 items-center gap-2">
        <span
          aria-hidden="true"
          className="relative grid size-7 shrink-0 place-items-center rounded-lg text-xs font-bold"
          style={{
            background: 'var(--gradient-brand)',
            color: 'var(--accent-primary-contrast)',
            boxShadow: 'var(--glow-primary)',
            fontFamily: 'var(--font-display)',
          }}
        >
          R
          <span
            aria-hidden="true"
            className="absolute right-1 top-1 size-1 rounded-full bg-white"
          />
        </span>

        <p
          className="truncate text-sm font-semibold leading-tight text-balance"
          style={{
            color: 'var(--text-primary)',
            fontFamily: 'var(--font-heading)',
          }}
        >
          {title}
        </p>
      </div>

      <div className="flex items-center gap-1.5">
        <ThemeToggle compact />
        <button
          type="button"
          onClick={handleOpenMenu}
          aria-label="Открыть меню"
          className={cn(
            'grid size-9 place-items-center rounded-full',
            'transition-colors duration-150',
            'text-[var(--text-secondary)] hover:text-[var(--text-primary)]',
          )}
        >
          <List size={21} weight="bold" aria-hidden="true" />
        </button>
      </div>
    </header>
  )
}
