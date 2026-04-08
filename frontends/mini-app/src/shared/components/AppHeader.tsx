import { useLocation } from 'react-router'
import { ThemeToggle } from '@/shared/theme/ThemeToggle'
import { cn } from '@/lib/utils'

/**
 * Mini App top header (brandbook §4.6, §7).
 *
 * Compact (48px) sticky header with brand mark + current page title + theme
 * toggle. Sized for Telegram's 400-wide viewport. Safe-area top inset is
 * respected in case Telegram reserves space for its own chrome.
 *
 * The theme toggle placement in §4.8 of the brandbook specifically calls
 * for mini-app: "settings profile + optionally in header". There is no
 * profile page in the mini-app yet, so the header is the primary location.
 * Telegram's native theme still seeds the initial theme via
 * `TelegramThemeProvider`; the toggle lets users override.
 */
const routeLabels: Record<string, string> = {
  '/': 'Расписание',
  '/stats': 'Статистика',
  '/homework': 'Домашние задания',
}

function matchTitle(pathname: string): string {
  if (routeLabels[pathname]) return routeLabels[pathname]
  if (pathname.startsWith('/checkin')) return 'Отметка'
  for (const key of Object.keys(routeLabels)) {
    if (key !== '/' && pathname.startsWith(key + '/')) return routeLabels[key]
  }
  return 'RutTrack'
}

export function AppHeader() {
  const { pathname } = useLocation()
  const title = matchTitle(pathname)

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

      <ThemeToggle compact />
    </header>
  )
}
