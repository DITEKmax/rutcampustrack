import { Navigate, useLocation } from 'react-router'
import { useAuth } from '@/features/auth/AuthProvider'

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isBootstrapping } = useAuth()
  const location = useLocation()

  // Cold-start: refresh cookie still being exchanged for an access token in
  // AuthProvider's mount effect. Without this gate the guard would redirect
  // to /login on every relaunch (Android killing the WebView, returning from
  // background after process eviction, etc.) — the user looks logged out even
  // though the 7-day refresh cookie is alive.
  if (isBootstrapping) {
    return null
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <>{children}</>
}
