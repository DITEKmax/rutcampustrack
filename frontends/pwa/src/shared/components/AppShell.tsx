import { Suspense, useState } from 'react'
import { Outlet, useLocation } from 'react-router'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { AppHeader } from './AppHeader'
import { BottomNav } from './BottomNav'
import { DrawerMenu } from './DrawerMenu'
import { OfflineBanner } from './OfflineBanner'
import { LoadingSpinner } from './LoadingSpinner'

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

  return (
    <div className="flex h-dvh flex-col bg-background text-foreground">
      <OfflineBanner />
      <AppHeader onOpenMenu={() => setDrawerOpen(true)} />
      <DrawerMenu open={drawerOpen} onClose={() => setDrawerOpen(false)} />

      <main
        className="relative flex-1 overflow-x-hidden overflow-y-auto"
        style={{
          // Reserve room for the fixed bottom nav (52px + safe-area)
          paddingBottom: 'calc(64px + env(safe-area-inset-bottom))',
        }}
      >
        {/*
         * Single Suspense boundary above AnimatePresence. When a lazy route
         * chunk suspends, React does not blow away the previous tree —
         * AnimatePresence's exit animation can finish before the new tree
         * commits. Keeping Suspense *inside* the motion.div caused transient
         * black screens (no fallback, no previous content) when switching
         * from a non-lazy route (/checkin) to a still-loading lazy one.
         */}
        <Suspense fallback={<LoadingSpinner />}>
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={location.pathname}
              initial={reduceMotion ? false : { opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={reduceMotion ? undefined : { opacity: 0, y: -6 }}
              transition={{
                duration: reduceMotion ? 0 : 0.2,
                ease: 'easeOut',
              }}
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </Suspense>
      </main>

      <BottomNav />
    </div>
  )
}
