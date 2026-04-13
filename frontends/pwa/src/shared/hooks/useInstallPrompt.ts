import { useEffect, useState, useCallback } from 'react'

/**
 * BUG-008: useInstallPrompt
 * - На Android Chrome ловит beforeinstallprompt; canInstall становится true → UI показывает кнопку.
 * - На iOS Safari события нет (см. IOSOnboardingOverlay для отдельного flow).
 * - Если приложение уже установлено (display-mode: standalone), не показываем повторно.
 *
 * Возвращает state-флаг (а не ref-функцию), иначе React не перерендерит компонент.
 */
export function useInstallPrompt() {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null)
  const [installed, setInstalled] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false
    return window.matchMedia?.('(display-mode: standalone)').matches === true
  })

  useEffect(() => {
    const onPrompt = (e: Event) => {
      e.preventDefault()
      setDeferredPrompt(e as BeforeInstallPromptEvent)
    }
    const onInstalled = () => {
      setInstalled(true)
      setDeferredPrompt(null)
    }
    window.addEventListener('beforeinstallprompt', onPrompt)
    window.addEventListener('appinstalled', onInstalled)
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt)
      window.removeEventListener('appinstalled', onInstalled)
    }
  }, [])

  const triggerInstall = useCallback(async () => {
    if (!deferredPrompt) return false
    await deferredPrompt.prompt()
    const { outcome } = await deferredPrompt.userChoice
    setDeferredPrompt(null)
    return outcome === 'accepted'
  }, [deferredPrompt])

  return {
    /** True when the browser has fired beforeinstallprompt and the PWA isn't already installed. */
    canInstall: !installed && deferredPrompt !== null,
    /** True if the app already runs as a standalone PWA. */
    installed,
    triggerInstall,
  }
}
