import { useEffect } from 'react'
import { backButton } from '@telegram-apps/sdk-react'
import { useNavigate } from 'react-router'

export function useBackButton() {
  const navigate = useNavigate()

  useEffect(() => {
    if (!backButton.show.isAvailable()) return
    backButton.show()

    const unsubscribe = backButton.onClick(() => {
      navigate(-1)
    })

    return () => {
      unsubscribe()
      if (backButton.hide.isAvailable()) backButton.hide()
    }
  }, [navigate])
}
