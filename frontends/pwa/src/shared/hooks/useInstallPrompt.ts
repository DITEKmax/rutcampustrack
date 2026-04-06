import { useEffect, useRef, useCallback } from 'react'

export function useInstallPrompt() {
  const deferredPrompt = useRef<BeforeInstallPromptEvent | null>(null)

  useEffect(() => {
    const handler = (e: BeforeInstallPromptEvent) => {
      e.preventDefault()
      deferredPrompt.current = e
    }
    window.addEventListener('beforeinstallprompt', handler)
    return () => window.removeEventListener('beforeinstallprompt', handler)
  }, [])

  const triggerInstall = useCallback(async () => {
    if (!deferredPrompt.current) return false
    deferredPrompt.current.prompt()
    const { outcome } = await deferredPrompt.current.userChoice
    deferredPrompt.current = null
    return outcome === 'accepted'
  }, [])

  const canInstall = useCallback(() => deferredPrompt.current !== null, [])

  return { triggerInstall, canInstall }
}
