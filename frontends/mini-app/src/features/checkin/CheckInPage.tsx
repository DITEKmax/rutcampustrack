import { useState, useCallback, useEffect, useRef, type ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router'
import { motion } from 'motion/react'
import { hapticFeedback } from '@telegram-apps/sdk-react'
import {
  CheckCircle,
  ClockCountdown,
  MapPin,
  Timer,
  WarningCircle,
  CircleNotch,
  Fingerprint,
} from '@phosphor-icons/react'
import { useBackButton } from '@/shared/hooks/useBackButton'
import { useMainButton } from '@/shared/hooks/useMainButton'
import { cn } from '@/lib/utils'
import { useCheckin, mapCheckinError } from './api'
import { getCheckinLocation, isBrowserLocationFallbackEnabled, mapLocationError } from './location'

/**
 * CheckIn flow inside the Telegram Mini App.
 *
 * Layout: centered brand hero (fingerprint) + geolocation status card +
 * result card. The primary CTA lives in Telegram's native MainButton via
 * `useMainButton`, with a dev-mode fallback button for browsers outside
 * Telegram. Back navigation uses Telegram's BackButton via `useBackButton`.
 * All haptic feedback is preserved — the only changes are visual tokens.
 */
type GpsState = 'idle' | 'acquiring' | 'acquired' | 'error'
type ResultState =
  | { kind: 'success' }
  | { kind: 'error'; status: number; message: string }
  | null

export function CheckInPage() {
  const { lessonId } = useParams<{ lessonId: string }>()
  const navigate = useNavigate()
  const checkinMutation = useCheckin()
  const didAutoStart = useRef(false)

  useBackButton()

  const [gpsState, setGpsState] = useState<GpsState>('idle')
  const [locationSource, setLocationSource] = useState<'telegram' | 'browser' | null>(null)
  const [result, setResult] = useState<ResultState>(null)
  const isSubmitting = checkinMutation.isPending

  const handleCheckin = useCallback(async () => {
    setGpsState('acquiring')
    setLocationSource(null)
    setResult(null)

    try {
      const location = await getCheckinLocation()
      setGpsState('acquired')
      setLocationSource(location.source)
      checkinMutation.mutate(
        { lat: location.lat, lng: location.lng },
        {
          onSuccess: () => {
            setResult({ kind: 'success' })
            if (hapticFeedback.notificationOccurred.isAvailable()) {
              hapticFeedback.notificationOccurred('success')
            }
            setTimeout(() => navigate('/schedule'), 1500)
          },
          onError: (error: unknown) => {
            const status =
              (error as { response?: { status?: number } })?.response?.status ?? 500
            setResult({ kind: 'error', status, message: mapCheckinError(status) })
            if (hapticFeedback.notificationOccurred.isAvailable()) {
              hapticFeedback.notificationOccurred('error')
            }
          },
        },
      )
    } catch (error) {
      setGpsState('error')
      setResult({ kind: 'error', status: 0, message: mapLocationError(error) })
      if (hapticFeedback.notificationOccurred.isAvailable()) {
        hapticFeedback.notificationOccurred('error')
      }
    }
  }, [checkinMutation, navigate])

  useEffect(() => {
    if (didAutoStart.current) return
    didAutoStart.current = true
    void handleCheckin()
  }, [handleCheckin])

  const mainButtonText =
    gpsState === 'acquiring'
      ? 'Определяем местоположение...'
      : isSubmitting
        ? 'Отправляем отметку...'
        : 'Отметиться'

  const mainButtonEnabled =
    gpsState !== 'acquiring' && !isSubmitting && result?.kind !== 'success'
  const mainButtonVisible = result?.kind !== 'success'

  useMainButton({
    text: mainButtonText,
    isEnabled: mainButtonEnabled,
    isVisible: mainButtonVisible,
    onClick: handleCheckin,
  })

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2, ease: 'easeOut' }}
      className="flex min-h-screen flex-col px-4 pt-[calc(env(safe-area-inset-top)+16px)]"
    >
      {/* Brand hero */}
      <header className="flex flex-col items-center gap-3 pt-6 text-center">
        <div
          className="grid size-16 place-items-center rounded-2xl"
          style={{
            background: 'var(--gradient-brand)',
            color: 'var(--accent-primary-contrast)',
            boxShadow: 'var(--glow-primary)',
          }}
          aria-hidden="true"
        >
          <Fingerprint size={32} weight="fill" />
        </div>
        <h1
          className="text-xl font-bold leading-tight text-balance"
          style={{
            color: 'var(--text-primary)',
            fontFamily: 'var(--font-display)',
            letterSpacing: '-0.02em',
          }}
        >
          Отметка на паре
        </h1>
        <p
          className="text-xs font-medium tabular-nums"
          style={{
            color: 'var(--text-muted)',
            fontFamily: 'var(--font-mono)',
          }}
        >
          Пара #{lessonId}
        </p>
      </header>

      {/* Status cards */}
      <div className="mt-6 flex flex-col gap-3">
        {gpsState === 'acquiring' && <StatusCard tone="neutral" icon={<CircleNotch size={18} className="animate-spin" />} text="Определяем местоположение..." />}
        {gpsState === 'acquired' && (
          <StatusCard
            tone="success"
            icon={<MapPin size={18} weight="fill" />}
            text={locationSource === 'browser' ? 'Геолокация получена через браузер' : 'Геолокация получена через Telegram'}
          />
        )}
        {gpsState === 'error' && <StatusCard tone="danger" icon={<WarningCircle size={18} weight="bold" />} text="Не удалось получить геолокацию" />}

        {/* Result */}
        {result?.kind === 'success' && (
          <ResultCard tone="success" icon={<CheckCircle size={24} weight="fill" />} text="Отметка принята!" />
        )}
        {result?.kind === 'error' && (
          <ResultCard
            tone={result.status === 409 ? 'warning' : result.status === 403 ? 'danger' : 'neutral'}
            icon={
              result.status === 409 ? (
                <CheckCircle size={24} weight="fill" />
              ) : result.status === 422 ? (
                <ClockCountdown size={24} />
              ) : result.status === 403 ? (
                <WarningCircle size={24} />
              ) : result.status === 429 ? (
                <Timer size={24} />
              ) : (
                <WarningCircle size={24} />
              )
            }
            text={result.message}
          />
        )}
      </div>

      {/* Fallback button for browser location mode where MainButton may be unavailable. */}
      {isBrowserLocationFallbackEnabled() && (
        <button
          type="button"
          onClick={handleCheckin}
          disabled={!mainButtonEnabled}
          className={cn(
            'mt-6 inline-flex w-full items-center justify-center gap-2',
            'min-h-[48px] rounded-full px-6 text-sm font-semibold',
            'transition-opacity duration-200 ease-out',
            !mainButtonEnabled && 'opacity-55',
          )}
          style={{
            background: 'var(--gradient-brand)',
            color: 'var(--accent-primary-contrast)',
            boxShadow: mainButtonEnabled ? 'var(--glow-primary)' : 'none',
          }}
        >
          <Fingerprint size={18} weight="fill" />
          Отметиться
        </button>
      )}
    </motion.div>
  )
}

type Tone = 'neutral' | 'success' | 'warning' | 'danger'

function toneColors(tone: Tone) {
  switch (tone) {
    case 'success':
      return { fg: 'var(--accent-primary)', bg: 'color-mix(in oklab, var(--accent-primary) 12%, transparent)', bd: 'var(--border-accent)' }
    case 'warning':
      return { fg: 'var(--accent-warning)', bg: 'color-mix(in oklab, var(--accent-warning) 12%, transparent)', bd: 'color-mix(in oklab, var(--accent-warning) 28%, transparent)' }
    case 'danger':
      return { fg: 'var(--accent-danger)', bg: 'color-mix(in oklab, var(--accent-danger) 12%, transparent)', bd: 'color-mix(in oklab, var(--accent-danger) 28%, transparent)' }
    default:
      return { fg: 'var(--text-secondary)', bg: 'var(--bg-surface)', bd: 'var(--border-subtle)' }
  }
}

function StatusCard({
  tone,
  icon,
  text,
}: {
  tone: Tone
  icon: ReactNode
  text: string
}) {
  const c = toneColors(tone)
  return (
    <div
      className="flex items-center gap-2 rounded-xl border px-3 py-2 text-xs font-medium"
      style={{ background: c.bg, borderColor: c.bd, color: c.fg }}
    >
      <span aria-hidden="true">{icon}</span>
      <span>{text}</span>
    </div>
  )
}

function ResultCard({
  tone,
  icon,
  text,
}: {
  tone: Tone
  icon: ReactNode
  text: string
}) {
  const c = toneColors(tone)
  return (
    <div
      className="flex items-center gap-3 rounded-2xl border p-4"
      style={{ background: 'var(--bg-secondary)', borderColor: c.bd }}
    >
      <span
        aria-hidden="true"
        className="grid size-11 shrink-0 place-items-center rounded-full"
        style={{ background: c.bg, color: c.fg }}
      >
        {icon}
      </span>
      <span
        className="text-sm font-semibold text-pretty"
        style={{ color: 'var(--text-primary)' }}
      >
        {text}
      </span>
    </div>
  )
}
