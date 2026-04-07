import { useState, useCallback } from 'react'
import { useNavigate, useParams } from 'react-router'
import { motion } from 'motion/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import { CheckCircle, Check, ClockCountdown, MapPin, Timer, WarningCircle } from '@phosphor-icons/react'
import { useBackButton } from '@/shared/hooks/useBackButton'
import { useMainButton } from '@/shared/hooks/useMainButton'
import { useCheckin, mapCheckinError } from './api'

type GpsState = 'idle' | 'acquiring' | 'acquired' | 'error'
type ResultState = { kind: 'success' } | { kind: 'error'; status: number; message: string } | null

export function CheckInPage() {
  const { lessonId } = useParams<{ lessonId: string }>()
  const navigate = useNavigate()
  const checkinMutation = useCheckin()

  useBackButton()

  const [gpsState, setGpsState] = useState<GpsState>('idle')
  const [result, setResult] = useState<ResultState>(null)
  const isSubmitting = checkinMutation.isPending

  const handleCheckin = useCallback(() => {
    setGpsState('acquiring')
    setResult(null)

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setGpsState('acquired')
        checkinMutation.mutate(
          { lat: position.coords.latitude, lng: position.coords.longitude },
          {
            onSuccess: () => {
              setResult({ kind: 'success' })
              if (hapticFeedback.notificationOccurred.isAvailable()) {
                hapticFeedback.notificationOccurred('success')
              }
              setTimeout(() => navigate('/'), 1500)
            },
            onError: (error: unknown) => {
              const status = (error as { response?: { status?: number } })?.response?.status ?? 500
              setResult({ kind: 'error', status, message: mapCheckinError(status) })
              if (hapticFeedback.notificationOccurred.isAvailable()) {
                hapticFeedback.notificationOccurred('error')
              }
            },
          }
        )
      },
      () => {
        setGpsState('error')
        if (hapticFeedback.notificationOccurred.isAvailable()) {
          hapticFeedback.notificationOccurred('error')
        }
      },
      { enableHighAccuracy: true, timeout: 10000 }
    )
  }, [checkinMutation, navigate])

  const mainButtonText =
    gpsState === 'acquiring' ? 'Определяем местоположение...' :
    isSubmitting ? 'Отправляем отметку...' :
    'Отметиться'

  const mainButtonEnabled = gpsState !== 'acquiring' && !isSubmitting && result?.kind !== 'success'
  const mainButtonVisible = result?.kind !== 'success'

  useMainButton({
    text: mainButtonText,
    isEnabled: mainButtonEnabled,
    isVisible: mainButtonVisible,
    onClick: handleCheckin,
  })

  return (
    <motion.div
      className="px-4 pt-4 min-h-screen"
      initial={{ opacity: 0, x: 16 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ duration: 0.15, ease: 'easeOut' }}
    >
      <h1 className="text-base font-semibold">Отметка на паре</h1>
      <p className="text-xs text-muted-foreground mt-1">Пара #{lessonId}</p>

      <div className="mt-6 space-y-4">
        {/* GPS status */}
        {gpsState === 'acquiring' && (
          <div className="flex items-center gap-2 text-muted-foreground">
            <div className="w-5 h-5 border-2 border-muted-foreground border-t-transparent rounded-full animate-spin" />
            <span className="text-sm">Определяем местоположение...</span>
          </div>
        )}
        {gpsState === 'acquired' && (
          <div className="flex items-center gap-2 text-green-600">
            <MapPin size={20} weight="fill" />
            <span className="text-sm">Геолокация получена</span>
          </div>
        )}
        {gpsState === 'error' && (
          <div className="flex items-center gap-2 text-destructive">
            <WarningCircle size={20} weight="bold" />
            <span className="text-sm">Не удалось получить геолокацию</span>
          </div>
        )}

        {/* Result */}
        {result?.kind === 'success' && (
          <div className="bg-card border border-border rounded-xl p-4 flex items-center gap-3">
            <CheckCircle size={24} weight="fill" className="text-green-600" />
            <span className="text-sm font-semibold">Отметка принята!</span>
          </div>
        )}
        {result?.kind === 'error' && (
          <div className="bg-card border border-border rounded-xl p-4 flex items-center gap-3">
            {result.status === 409 && <Check size={24} weight="fill" className="text-green-600" />}
            {result.status === 422 && <ClockCountdown size={24} className="text-muted-foreground" />}
            {result.status === 403 && <WarningCircle size={24} className="text-destructive" />}
            {result.status === 429 && <Timer size={24} className="text-muted-foreground" />}
            {![409, 422, 403, 429].includes(result.status) && <WarningCircle size={24} className="text-destructive" />}
            <span className={`text-sm ${result.status === 403 ? 'text-destructive' : 'text-muted-foreground'}`}>
              {result.message}
            </span>
          </div>
        )}
      </div>

      {/* Dev fallback button */}
      {import.meta.env.VITE_TMA_DEV === 'true' && (
        <button
          onClick={handleCheckin}
          disabled={!mainButtonEnabled}
          className="w-full bg-primary text-primary-foreground rounded-xl py-3 text-sm font-semibold min-h-[48px] mt-6 disabled:opacity-50"
        >
          Отметиться
        </button>
      )}
    </motion.div>
  )
}
