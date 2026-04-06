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
import { registerSW } from 'virtual:pwa-register'
import './index.css'

// Auto-update SW when new version available
registerSW({ immediate: true })

const HomePlaceholder = lazy(() => import('./features/home/HomePlaceholder'))
const ProfilePlaceholder = lazy(() => import('./features/profile/ProfilePlaceholder'))

const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <Navigate to="/home" replace /> },
      {
        path: 'home',
        element: (
          <Suspense fallback={<LoadingSpinner />}>
            <HomePlaceholder />
          </Suspense>
        ),
      },
      {
        path: 'schedule',
        element: (
          <Suspense fallback={<LoadingSpinner />}>
            <HomePlaceholder />
          </Suspense>
        ),
      },
      {
        path: 'checkin',
        element: (
          <Suspense fallback={<LoadingSpinner />}>
            <HomePlaceholder />
          </Suspense>
        ),
      },
      {
        path: 'profile',
        element: (
          <Suspense fallback={<LoadingSpinner />}>
            <ProfilePlaceholder />
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
