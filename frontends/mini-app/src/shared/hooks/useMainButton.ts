import { useEffect } from 'react'
import { mainButton } from '@telegram-apps/sdk-react'

interface UseMainButtonOptions {
  text: string
  isEnabled: boolean
  isVisible: boolean
  onClick: () => void
}

export function useMainButton({ text, isEnabled, isVisible, onClick }: UseMainButtonOptions) {
  useEffect(() => {
    if (!mainButton.setParams.isAvailable()) return

    mainButton.setParams({ text, isEnabled, isVisible })
    const unsubscribe = mainButton.onClick(onClick)

    return () => {
      unsubscribe()
      mainButton.setParams({ isVisible: false })
    }
  }, [text, isEnabled, isVisible, onClick])
}
