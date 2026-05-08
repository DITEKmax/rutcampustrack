import { lazy, Suspense, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router'
import { QueryClientProvider } from '@tanstack/react-query'
import { motion, AnimatePresence, useReducedMotion } from 'motion/react'
import { retrieveLaunchParams } from '@telegram-apps/sdk-react'
import { queryClient } from '@/shared/lib/queryClient'
import {
  getStartParamFromLaunchParams,
  getStartParamFromSearch,
  resolveStartParamRoute,
} from '@/shared/lib/launchRoutes'
import { TelegramThemeProvider } from '@/shared/providers/TelegramThemeProvider'
import { DevModeBanner } from '@/shared/components/DevModeBanner'
import { AppHeader } from '@/shared/components/AppHeader'
import { BottomNav } from '@/shared/components/BottomNav'
import { DrawerMenu } from '@/shared/components/DrawerMenu'
import { SkeletonList } from '@/shared/components/Skeleton'
import { AuthProvider } from '@/features/auth/AuthProvider'

// Route-level code splitting (brandbook §5 performance): each feature page
// ships in its own chunk so the initial payload stays small. PWA already
// uses the same pattern in main.tsx.
const SchedulePage = lazy(() =>
  import('@/features/schedule/SchedulePage').then((m) => ({ default: m.SchedulePage })),
)
const HomeDashboard = lazy(() =>
  import('@/features/home/HomeDashboard').then((m) => ({ default: m.HomeDashboard })),
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
const ProfilePage = lazy(() =>
  import('@/features/profile/ProfilePage').then((m) => ({ default: m.ProfilePage })),
)
const StudentLateCheckinPage = lazy(() =>
  import('@/features/student/late-checkin/StudentLateCheckinPage').then((m) => ({
    default: m.StudentLateCheckinPage,
  })),
)
const StudentExcusesPage = lazy(() =>
  import('@/features/student/excuses/StudentExcusesPage').then((m) => ({
    default: m.StudentExcusesPage,
  })),
)
const GroupHub = lazy(() =>
  import('@/features/headman/group-hub/GroupHub').then((m) => ({ default: m.GroupHub })),
)
const HeadmanOverviewPage = lazy(() =>
  import('@/features/headman/overview/OverviewPage').then((m) => ({
    default: m.OverviewPage,
  })),
)
const HeadmanStudentsPage = lazy(() =>
  import('@/features/headman/students/HeadmanStudentsPage').then((m) => ({
    default: m.HeadmanStudentsPage,
  })),
)
const HeadmanSubjectsPage = lazy(() =>
  import('@/features/headman/subjects/HeadmanSubjectsPage').then((m) => ({
    default: m.HeadmanSubjectsPage,
  })),
)
const HeadmanJournalPage = lazy(() =>
  import('@/features/headman/journal/HeadmanJournalPage').then((m) => ({
    default: m.HeadmanJournalPage,
  })),
)
const HeadmanStatsPage = lazy(() =>
  import('@/features/headman/stats/HeadmanStatsPage').then((m) => ({
    default: m.HeadmanStatsPage,
  })),
)
const HeadmanExcusesPage = lazy(() =>
  import('@/features/headman/excuses/HeadmanExcusesPage').then((m) => ({
    default: m.HeadmanExcusesPage,
  })),
)
const HeadmanLateCheckinPage = lazy(() =>
  import('@/features/headman/late-checkin/HeadmanLateCheckinPage').then((m) => ({
    default: m.HeadmanLateCheckinPage,
  })),
)

const routerBasename =
  import.meta.env.BASE_URL && import.meta.env.BASE_URL !== '/'
    ? import.meta.env.BASE_URL.replace(/\/$/, '')
    : undefined

function RouteFallback() {
  return (
    <div className="px-3 pt-3 pb-[calc(72px+env(safe-area-inset-bottom))]">
      <SkeletonList count={4} />
    </div>
  )
}

function InitialRouteRedirect() {
  const location = useLocation()
  let startParam = getStartParamFromSearch(location.search)
  if (!startParam) {
    try {
      startParam = getStartParamFromLaunchParams(retrieveLaunchParams(true))
    } catch {
      startParam = null
    }
  }
  return <Navigate to={resolveStartParamRoute(startParam) ?? '/home'} replace />
}

function AppLayout() {
  const location = useLocation()
  const reduceMotion = useReducedMotion()
  const [drawerOpen, setDrawerOpen] = useState(false)
  // The check-in flow uses Telegram's native BackButton + MainButton and
  // should feel like a full-screen modal — hide shell chrome there.
  const isCheckinPage = location.pathname.startsWith('/checkin')

  return (
    <div className="flex min-h-dvh flex-col bg-background text-foreground">
      {!isCheckinPage && <AppHeader onOpenMenu={() => setDrawerOpen(true)} />}
      <DrawerMenu open={!isCheckinPage && drawerOpen} onClose={() => setDrawerOpen(false)} />

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
                <Route path="/" element={<InitialRouteRedirect />} />
                <Route path="/home" element={<HomeDashboard />} />
                <Route path="/schedule" element={<SchedulePage />} />
                <Route path="/checkin/:lessonId" element={<CheckInPage />} />
                <Route path="/stats" element={<StatsPage />} />
                <Route path="/homework" element={<HomeworkPage />} />
                <Route path="/late-checkin" element={<StudentLateCheckinPage />} />
                <Route path="/excuses" element={<StudentExcusesPage />} />
                <Route path="/profile" element={<ProfilePage />} />
                <Route path="/group" element={<GroupHub />} />
                <Route path="/group/overview" element={<HeadmanOverviewPage />} />
                <Route path="/group/students" element={<HeadmanStudentsPage />} />
                <Route path="/group/subjects" element={<HeadmanSubjectsPage />} />
                <Route path="/group/journal" element={<HeadmanJournalPage />} />
                <Route path="/group/stats" element={<HeadmanStatsPage />} />
                <Route path="/group/excuses" element={<HeadmanExcusesPage />} />
                <Route path="/group/late-checkin" element={<HeadmanLateCheckinPage />} />
                <Route path="*" element={<Navigate to="/home" replace />} />
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
          <BrowserRouter basename={routerBasename}>
            <AppLayout />
          </BrowserRouter>
        </AuthProvider>
      </TelegramThemeProvider>
    </QueryClientProvider>
  )
}
