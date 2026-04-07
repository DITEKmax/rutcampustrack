import { BrowserRouter, Routes, Route, useLocation } from 'react-router'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from '@/shared/lib/queryClient'
import { TelegramThemeProvider } from '@/shared/providers/TelegramThemeProvider'
import { DevModeBanner } from '@/shared/components/DevModeBanner'
import { BottomNav } from '@/shared/components/BottomNav'
import { AuthProvider } from '@/features/auth/AuthProvider'
import { SchedulePage } from '@/features/schedule/SchedulePage'
import { CheckInPage } from '@/features/checkin/CheckInPage'
import { StatsPage } from '@/features/stats/StatsPage'
import { HomeworkPage } from '@/features/homework/HomeworkPage'
import { motion, AnimatePresence } from 'motion/react'

function AppLayout() {
  const location = useLocation()
  const isCheckinPage = location.pathname.startsWith('/checkin')

  return (
    <>
      <AnimatePresence mode="wait">
        <motion.div
          key={location.pathname}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.1, ease: 'easeOut' }}
        >
          <Routes location={location}>
            <Route path="/" element={<SchedulePage />} />
            <Route path="/checkin/:lessonId" element={<CheckInPage />} />
            <Route path="/stats" element={<StatsPage />} />
            <Route path="/homework" element={<HomeworkPage />} />
          </Routes>
        </motion.div>
      </AnimatePresence>
      {!isCheckinPage && <BottomNav />}
    </>
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
