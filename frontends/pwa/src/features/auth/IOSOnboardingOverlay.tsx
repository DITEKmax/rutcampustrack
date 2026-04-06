import { useState, useEffect } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { ShareNetwork, PlusSquare } from '@phosphor-icons/react'

function isIOSSafari(): boolean {
  const ua = navigator.userAgent
  return /iphone|ipad|ipod/i.test(ua)
}

function isStandalone(): boolean {
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  )
}

export function IOSOnboardingOverlay() {
  const [show, setShow] = useState(false)

  useEffect(() => {
    if (isIOSSafari() && !isStandalone() && !localStorage.getItem('ios_onboarding_shown')) {
      setShow(true)
    }
  }, [])

  const dismiss = () => {
    localStorage.setItem('ios_onboarding_shown', '1')
    setShow(false)
  }

  return (
    <AnimatePresence>
      {show && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2, ease: 'easeOut' }}
          role="dialog"
          aria-modal="true"
          aria-label="Установка приложения"
          className="fixed inset-0 bg-background z-50 flex flex-col items-center justify-center gap-6 p-8"
        >
          <div className="flex flex-col items-center gap-8">
            <div className="flex flex-col items-center gap-2">
              <ShareNetwork size={48} weight="bold" className="text-primary" />
              <p className="text-base text-foreground text-center">
                Нажмите &laquo;Поделиться&raquo;
              </p>
            </div>
            <div className="flex flex-col items-center gap-2">
              <PlusSquare size={48} weight="bold" className="text-primary" />
              <p className="text-base text-foreground text-center">
                Выберите &laquo;На экран Домой&raquo;
              </p>
            </div>
            <div className="flex flex-col items-center gap-2">
              <img src="/icons/icon-192.png" alt="RutTrack" className="h-12 w-12 rounded-xl" />
              <p className="text-base text-foreground text-center">
                Откройте RutTrack как приложение
              </p>
            </div>
          </div>
          <button
            onClick={dismiss}
            className="text-sm text-muted-foreground mt-8 min-h-[44px] min-w-[44px]"
          >
            Понятно
          </button>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
