import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, useLocation } from 'react-router'
import { QueryClientProvider } from '@tanstack/react-query'
import { motion, AnimatePresence, useReducedMotion } from 'motion/react'
import { queryClient } from '@/shared/lib/queryClient'
import { TelegramThemeProvider } from '@/shared/providers/TelegramThemeProvider'
import { DevModeBanner } from '@/shared/components/DevModeBanner'
import { AppHeader } from '@/shared/components/AppHeader'
import { BottomNav } from '@/shared/components/BottomNav'
import { SkeletonList } from '@/shared/components/Skeleton'
import { AuthProvider } from '@/features/auth/AuthProvider'

// Route-level code splitting (brandbook §5 performance): each feature page
// ships in its own chunk so the initial payload stays small. PWA already
// uses the same pattern in main.tsx.
const SchedulePage = lazy(() =>
  import('@/features/schedule/SchedulePage').then((m) => ({ default: m.SchedulePage })),
)
const CheckInPage = lazy(() =>
  import('@/features/checkin/CheckInPage').then((m) => ({ default: m.CheckInPage })),
)
const StatsPage = lazy(() =>
  import('@/features/stats/StatsPage').then((m) => ({ default: m.StatsPage })),
)
const HomeworkPage = lazy(() =>
  import('@/features/homework/HomeworkPage').then((m) => ({ default: m.HomeworkPage })),
)

function RouteFallback() {
  return (
    <div className="px-3 pt-3 pb-[calc(72px+env(safe-area-inset-bottom))]">
      <SkeletonList count={4} />
    </div>
  )
}

function AppLayout() {
  const location = useLocation()
  const reduceMotion = useReducedMotion()
  // The check-in flow uses Telegram's native BackButton + MainButton and
  // should feel like a full-screen modal — hide shell chrome there.
  const isCheckinPage = location.pathname.startsWith('/checkin')

  return (
    <div className="flex min-h-dvh flex-col bg-background text-foreground">
      {!isCheckinPage && <AppHeader />}

      <main className="flex-1">
        <AnimatePresence mode="wait" initial={false}>
          <motion.div
            key={location.pathname}
            initial={reduceMotion ? false : { opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={reduceMotion ? undefined : { opacity: 0, y: -4 }}
            transition={{ duration: reduceMotion ? 0 : 0.18, ease: 'easeOut' }}
          >
            <Suspense fallback={<RouteFallback />}>
              <Routes location={location}>
                <Route path="/" element={<SchedulePage />} />
                <Route path="/checkin/:lessonId" element={<CheckInPage />} />
                <Route path="/stats" element={<StatsPage />} />
                <Route path="/homework" element={<HomeworkPage />} />
              </Routes>
            </Suspense>
          </motion.div>
        </AnimatePresence>
      </main>

      {!isCheckinPage && <BottomNav />}
    </div>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <TelegramThemeProvider>
        <DevModeBanner />
        <AuthProvider>
          <BrowserRouter>
            <AppLayout />
          </BrowserRouter>
        </AuthProvider>
      </TelegramThemeProvider>
    </QueryClientProvider>
  )
}
