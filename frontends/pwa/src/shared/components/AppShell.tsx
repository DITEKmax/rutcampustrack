import { Suspense, useRef, useState } from 'react'
import { Outlet, useLocation } from 'react-router'
import { motion, useReducedMotion } from 'motion/react'
import { AppHeader } from './AppHeader'
import { BottomNav } from './BottomNav'
import { DrawerMenu } from './DrawerMenu'
import { OfflineBanner } from './OfflineBanner'
import { LoadingSpinner } from './LoadingSpinner'
import { useScrollRestoration } from '@/shared/hooks/useScrollRestoration'

/**
 * RutCampusTrack — PWA shell (brandbook §7, §5.4)
 *
 * Mobile-first shell with a sticky top header, scrollable main area, and
 * a fixed bottom tab bar. The main area flexes between the header and the
 * tab bar so page content has a clear scroll boundary instead of scrolling
 * the entire document. Route transitions use a slide+fade via Motion's
 * `AnimatePresence`, honoring `prefers-reduced-motion`.
 */
export function AppShell() {
  const location = useLocation()
  const reduceMotion = useReducedMotion()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const mainRef = useRef<HTMLElement>(null)
  useScrollRestoration(mainRef)

  return (
    <div className="flex h-dvh flex-col bg-background text-foreground">
      <OfflineBanner />
      <AppHeader onOpenMenu={() => setDrawerOpen(true)} />
      <DrawerMenu open={drawerOpen} onClose={() => setDrawerOpen(false)} />

      <main
        ref={mainRef}
        className="relative flex-1 overflow-x-hidden overflow-y-auto"
        style={{
          // Reserve room for the fixed bottom nav (52px + safe-area)
          paddingBottom: 'calc(64px + env(safe-area-inset-bottom))',
        }}
      >
        {/* Mount the next route immediately; exit-wait animations can strand lazy pages at opacity:0. */}
        <Suspense fallback={<LoadingSpinner />}>
          <motion.div
            key={location.pathname}
            initial={reduceMotion ? false : { opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{
              duration: reduceMotion ? 0 : 0.16,
              ease: 'easeOut',
            }}
          >
            <Outlet />
          </motion.div>
        </Suspense>
      </main>

      <BottomNav />
    </div>
  )
}
