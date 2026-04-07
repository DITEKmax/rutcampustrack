import { motion } from 'motion/react'

export function LoadingScreen() {
  return (
    <motion.div
      className="min-h-screen flex flex-col items-center justify-center gap-3"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.15, ease: 'easeOut' }}
    >
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-muted border-t-primary" />
      <p className="text-sm text-muted-foreground">Вход через Telegram...</p>
    </motion.div>
  )
}
