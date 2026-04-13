import { useLocation } from 'react-router'
import { ThemeToggle } from '@/shared/theme/ThemeToggle'
import { useInstallPrompt } from '@/shared/hooks/useInstallPrompt'
import { cn } from '@/lib/utils'

/**
 * RutCampusTrack — PWA Top Header (brandbook §4.6, §7)
 *
 * Slim sticky header showing the brand mark + current page title + theme
 * toggle. Sits above the scrollable main area so the page title stays in
 * view regardless of content length. Backdrop blur lets content scroll
 * behind the bar instead of clipping against it.
 *
 * Page titles are derived from `useLocation().pathname` — keeping a static
 * route→label map here instead of reading from route objects since the
 * router config is a flat list and the titles never depend on data.
 */
const routeLabels: Record<string, string> = {
  '/home': 'Главная',
  '/schedule': 'Расписание',
  '/checkin': 'Отметка',
  '/profile': 'Профиль',
}

function matchTitle(pathname: string): string {
  // Exact match first, then prefix-based fallback for nested paths.
  if (routeLabels[pathname]) return routeLabels[pathname]
  for (const key of Object.keys(routeLabels)) {
    if (pathname.startsWith(key + '/')) return routeLabels[key]
  }
  return 'RutTrack'
}

export function AppHeader() {
  const { pathname } = useLocation()
  const title = matchTitle(pathname)
  const { canInstall, triggerInstall } = useInstallPrompt()

  return (
    <header
      className={cn(
        'sticky top-0 z-[var(--z-sticky)]',
        'flex items-center justify-between gap-3',
        'h-14 px-4',
        'pt-[env(safe-area-inset-top)]',
        'border-b border-border',
        'bg-background/80 backdrop-blur-xl backdrop-saturate-150',
      )}
    >
      {/* Brand — always visible */}
      <div className="flex items-center gap-2.5">
        <span
          aria-hidden="true"
          className={cn(
            'relative grid size-8 place-items-center rounded-lg',
            'text-sm font-bold',
            'text-[var(--accent-primary-contrast)]',
          )}
          style={{
            background: 'var(--gradient-brand)',
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
          className={cn(
            'text-base font-semibold leading-tight',
            'text-[var(--text-primary)] text-balance',
          )}
          style={{ fontFamily: 'var(--font-heading)' }}
        >
          {title}
        </p>
      </div>

      <div className="flex items-center gap-2">
        {canInstall && (
          <button
            type="button"
            onClick={() => { void triggerInstall() }}
            className={cn(
              'inline-flex items-center gap-1 rounded-full px-3 h-8',
              'text-xs font-semibold',
              'bg-[var(--accent-primary)] text-[var(--accent-primary-contrast)]',
              'shadow-[var(--glow-primary)] transition-transform active:scale-95',
            )}
            aria-label="Установить приложение"
          >
            <i className="ph ph-download-simple text-base" aria-hidden="true" />
            Установить
          </button>
        )}
        <ThemeToggle compact />
      </div>
    </header>
  )
}
