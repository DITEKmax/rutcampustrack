import { StrictMode, Suspense, lazy } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider, Navigate } from 'react-router'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from './shared/lib/queryClient'
import { AuthProvider } from './features/auth/AuthProvider'
import { LoginPage } from './features/auth/LoginPage'
import { ProtectedRoute } from './shared/components/ProtectedRoute'
import { AppShell } from './shared/components/AppShell'
import { IOSOnboardingOverlay } from './features/auth/IOSOnboardingOverlay'
import { LoadingSpinner } from './shared/components/LoadingSpinner'
import { StompProvider } from './features/checkin/StompProvider'
import { registerSW } from 'virtual:pwa-register'
import './index.css'

// Auto-update SW when new version available
registerSW({ immediate: true })

const SchedulePage = lazy(() => import('./features/schedule/SchedulePage').then(m => ({ default: m.SchedulePage })))
const CheckInScreen = lazy(() => import('./features/checkin/CheckInScreen').then(m => ({ default: m.CheckInScreen })))
const ProfilePage = lazy(() => import('./features/profile/ProfilePage'))
const AttendanceStatsPage = lazy(() => import('./features/attendance/AttendanceStatsPage').then(m => ({ default: m.AttendanceStatsPage })))
const AttendanceRecordsPage = lazy(() => import('./features/attendance/AttendanceRecordsPage').then(m => ({ default: m.AttendanceRecordsPage })))
const HomeworkPage = lazy(() => import('./features/homework/HomeworkPage').then(m => ({ default: m.HomeworkPage })))

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
      { index: true, element: <Navigate to="/schedule" replace /> },
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
      {
        path: 'stats',
        element: (
          <Suspense fallback={<LoadingSpinner />}>
            <AttendanceStatsPage />
          </Suspense>
        ),
      },
      {
        path: 'stats/:subjectId',
        element: (
          <Suspense fallback={<LoadingSpinner />}>
            <AttendanceRecordsPage />
          </Suspense>
        ),
      },
      {
        path: 'homework',
        element: (
          <Suspense fallback={<LoadingSpinner />}>
            <HomeworkPage />
          </Suspense>
        ),
      },
    ],
  },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <IOSOnboardingOverlay />
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>
)
