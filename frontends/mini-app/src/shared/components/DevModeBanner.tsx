export function DevModeBanner() {
  if (import.meta.env.VITE_TMA_DEV !== 'true') return null
  const mockUser = import.meta.env.VITE_TMA_MOCK_USER ?? 'student'
  return (
    <div className="fixed top-0 left-0 right-0 z-50 bg-yellow-400 text-yellow-900 text-xs text-center h-7 flex items-center justify-center">
      DEV MODE — mock user: {mockUser}
    </div>
  )
}
