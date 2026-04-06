import { Outlet, useLocation } from 'react-router'
import { AnimatePresence, motion } from 'motion/react'
import { BottomNav } from './BottomNav'
import { OfflineBanner } from './OfflineBanner'

export function AppShell() {
  const location = useLocation()

  return (
    <div className="min-h-screen bg-background text-foreground">
      <OfflineBanner />
      <main className="pb-14 pb-[env(safe-area-inset-bottom)]">
        <AnimatePresence mode="wait">
          <motion.div
            key={location.pathname}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15, ease: 'easeOut' }}
          >
            <Outlet />
          </motion.div>
        </AnimatePresence>
      </main>
      <BottomNav />
    </div>
  )
}
