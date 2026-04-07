import { BrowserRouter, Routes, Route } from 'react-router'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from '@/shared/lib/queryClient'
import { TelegramThemeProvider } from '@/shared/providers/TelegramThemeProvider'
import { DevModeBanner } from '@/shared/components/DevModeBanner'
import { AuthProvider } from '@/features/auth/AuthProvider'
import { motion } from 'motion/react'

function HomePage() {
  return (
    <motion.main
      className="min-h-screen flex flex-col items-center justify-center p-4"
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.15, ease: 'easeOut' }}
    >
      <h1 className="text-xl font-semibold">Добро пожаловать в RutTrack</h1>
      <p className="text-base text-muted-foreground mt-2">Функции появятся в следующей версии</p>
    </motion.main>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <TelegramThemeProvider>
        <DevModeBanner />
        <AuthProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/" element={<HomePage />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </TelegramThemeProvider>
    </QueryClientProvider>
  )
}
