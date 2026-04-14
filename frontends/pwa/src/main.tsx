import { StrictMode, Suspense, lazy } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider, Navigate } from 'react-router'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from './shared/lib/queryClient'
import { ThemeProvider } from './shared/theme/ThemeProvider'
import { AuthProvider } from './features/auth/AuthProvider'
import { LoginPage } from './features/auth/LoginPage'
import { ProtectedRoute } from './shared/components/ProtectedRoute'
import { AppShell } from './shared/components/AppShell'
import { IOSOnboardingOverlay } from './features/auth/IOSOnboardingOverlay'
import { LoadingSpinner } from './shared/components/LoadingSpinner'
import { StompProvider } from './features/checkin/StompProvider'
import { UpdateBanner } from './shared/components/UpdateBanner'
import './index.css'

const SchedulePage = lazy(() => import('./features/schedule/SchedulePage').then(m => ({ default: m.SchedulePage })))
const CheckInScreen = lazy(() => import('./features/checkin/CheckInScreen').then(m => ({ default: m.CheckInScreen })))
const ProfilePage = lazy(() => import('./features/profile/ProfilePage'))
const GroupHub = lazy(() => import('./features/headman/group-hub/GroupHub').then(m => ({ default: m.GroupHub })))
const Overview = lazy(() => import('./features/headman/overview/Overview').then(m => ({ default: m.Overview })))
const StudentsList = lazy(() => import('./features/headman/students/StudentsList').then(m => ({ default: m.StudentsList })))
const SubjectsList = lazy(() => import('./features/headman/subjects/SubjectsList').then(m => ({ default: m.SubjectsList })))
const JournalPage = lazy(() => import('./features/headman/journal/JournalPage').then(m => ({ default: m.JournalPage })))
const ExcusesPage = lazy(() => import('./features/headman/excuses/ExcusesPage').then(m => ({ default: m.ExcusesPage })))
const LateCheckinPage = lazy(() => import('./features/headman/late-checkin/LateCheckinPage').then(m => ({ default: m.LateCheckinPage })))
const StatsPage = lazy(() => import('./features/headman/stats/StatsPage').then(m => ({ default: m.StatsPage })))

// BUG-008: PWA proxied под /app/ в проде (см. nginx default.conf).
// basename должен совпадать с base из vite.config.ts, иначе createBrowserRouter
// будет видеть «/app/login» как unmatched route и рендерить пустоту → белый экран.
const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <StompProvider>
          <AppShell />
        </StompProvider>
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <Navigate to="/home" replace /> },
      {
        path: 'home',
        element: (
          <Suspense fallback={<LoadingSpinner />}>
            <SchedulePage />
          </Suspense>
        ),
      },
      {
        path: 'schedule',
        element: (
          <Suspense fallback={<LoadingSpinner />}>
            <SchedulePage />
          </Suspense>
        ),
      },
      {
        path: 'checkin',
        element: (
          <Suspense fallback={<LoadingSpinner />}>
            <CheckInScreen />
          </Suspense>
        ),
      },
      {
        path: 'profile',
        element: (
          <Suspense fallback={<LoadingSpinner />}>
            <ProfilePage />
          </Suspense>
        ),
      },
      { path: 'group', element: <Suspense fallback={<LoadingSpinner />}><GroupHub /></Suspense> },
      { path: 'group/overview', element: <Suspense fallback={<LoadingSpinner />}><Overview /></Suspense> },
      { path: 'group/students', element: <Suspense fallback={<LoadingSpinner />}><StudentsList /></Suspense> },
      { path: 'group/subjects', element: <Suspense fallback={<LoadingSpinner />}><SubjectsList /></Suspense> },
      { path: 'group/journal', element: <Suspense fallback={<LoadingSpinner />}><JournalPage /></Suspense> },
      { path: 'group/excuses', element: <Suspense fallback={<LoadingSpinner />}><ExcusesPage /></Suspense> },
      { path: 'group/late-checkin', element: <Suspense fallback={<LoadingSpinner />}><LateCheckinPage /></Suspense> },
      { path: 'group/stats', element: <Suspense fallback={<LoadingSpinner />}><StatsPage /></Suspense> },
    ],
  },
], { basename: '/app' })

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <IOSOnboardingOverlay />
          <RouterProvider router={router} />
          <UpdateBanner />
        </AuthProvider>
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>
)
