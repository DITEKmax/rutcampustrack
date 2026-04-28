import { Suspense, useRef, useState } from 'react'
import { Outlet, useLocation } from 'react-router'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
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
        {/*
         * Two Suspense boundaries:
         * - Outer: cold-start safety net so SOMETHING renders if no previous
         *   tree exists (first navigation after refresh).
         * - Inner (under motion.div): keeps the suspended chunk from delaying
         *   AnimatePresence's mode="wait" exit. Without it, the new motion.div
         *   only mounts AFTER the old one finishes exit AND the lazy chunk
         *   resolves — and by then framer-motion has already skipped the
         *   `animate` pass, leaving the page at opacity:0. Symptom: blank
         *   screen on /homework or /schedule until something (e.g. opening the
         *   drawer) forces a re-render. Inner Suspense lets motion.div mount
         *   immediately with a spinner, then swap in real content when ready.
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
              <Suspense fallback={<LoadingSpinner />}>
                <Outlet />
              </Suspense>
            </motion.div>
          </AnimatePresence>
        </Suspense>
      </main>

      <BottomNav />
    </div>
  )
}
