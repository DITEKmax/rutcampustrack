import { useState } from 'react'
import { useCheckin, mapCheckinError } from './api'
import { useNetworkStatus } from '@/shared/hooks/useNetworkStatus'

interface CheckInButtonProps {
  onSuccess: () => void
  onError?: (message: string) => void
  disabled?: boolean
}

export function CheckInButton({ onSuccess, onError, disabled }: CheckInButtonProps) {
  const checkinMutation = useCheckin()
  const { isOnline } = useNetworkStatus()
  const [isCapturing, setIsCapturing] = useState(false)

  const isLoading = isCapturing || checkinMutation.isPending
  const isDisabled = disabled || !isOnline || isLoading

  const handleClick = () => {
    setIsCapturing(true)

    navigator.geolocation.getCurrentPosition(
      (position) => {
        checkinMutation.mutate(
          { lat: position.coords.latitude, lng: position.coords.longitude },
          {
            onSuccess: () => {
              setIsCapturing(false)
              onSuccess()
            },
            onError: (error: unknown) => {
              setIsCapturing(false)
              const status = (error as { response?: { status?: number } })?.response?.status ?? 500
              onError?.(mapCheckinError(status))
            },
          }
        )
      },
      () => {
        setIsCapturing(false)
        onError?.('Нет доступа к GPS. Разрешите доступ в настройках браузера')
      },
      { timeout: 10000, maximumAge: 30000 }
    )
  }

  return (
    <button
      onClick={handleClick}
      disabled={isDisabled}
      aria-busy={isLoading}
      className="bg-primary text-primary-foreground rounded-lg px-4 min-h-[44px] font-medium text-sm disabled:opacity-50"
    >
      {isLoading ? (
        <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground border-t-transparent" />
      ) : (
        'Отметиться'
      )}
    </button>
  )
}
