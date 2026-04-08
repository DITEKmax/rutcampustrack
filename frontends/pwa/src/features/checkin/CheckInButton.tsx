import { useState } from 'react'
import { motion } from 'motion/react'
import { Fingerprint, CircleNotch } from '@phosphor-icons/react'
import { useCheckin, mapCheckinError } from './api'
import { useNetworkStatus } from '@/shared/hooks/useNetworkStatus'
import { cn } from '@/lib/utils'

/**
 * Primary check-in CTA (brandbook §4.1, §5.4 micro-interactions).
 *
 * Large gradient-brand pill with a pulsing ring when idle, spinner when
 * capturing GPS, and a scale-down whileTap for tactile feedback. All other
 * behavior (geolocation capture, error mapping, aria-busy, disabled-when-
 * offline) is preserved unchanged — 8 tests depend on it.
 */
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
  const isDisabled = Boolean(disabled) || !isOnline || isLoading

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
              const status =
                (error as { response?: { status?: number } })?.response?.status ?? 500
              onError?.(mapCheckinError(status))
            },
          },
        )
      },
      () => {
        setIsCapturing(false)
        onError?.('Нет доступа к GPS. Разрешите доступ в настройках браузера')
      },
      { timeout: 10000, maximumAge: 30000 },
    )
  }

  return (
    <motion.button
      type="button"
      onClick={handleClick}
      disabled={isDisabled}
      aria-busy={isLoading}
      aria-label="Отметиться"
      whileTap={isDisabled ? undefined : { scale: 0.96 }}
      transition={{ type: 'spring', stiffness: 420, damping: 28 }}
      className={cn(
        'group relative isolate',
        'inline-flex items-center justify-center gap-2.5',
        'min-h-[56px] px-8 rounded-full',
        'text-base font-semibold tracking-tight',
        'transition-opacity duration-200 ease-out',
        isDisabled && 'opacity-55 cursor-not-allowed',
      )}
      style={{
        background: 'var(--gradient-brand)',
        color: 'var(--accent-primary-contrast)',
        boxShadow: isDisabled ? 'none' : 'var(--glow-primary)',
      }}
    >
      {/* Pulsing ring — only when idle + enabled */}
      {!isDisabled && (
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 -z-10 rounded-full"
          style={{
            boxShadow: '0 0 0 0 color-mix(in oklab, var(--accent-primary) 60%, transparent)',
            animation: 'ruttrack-checkin-pulse 2s ease-out infinite',
          }}
        />
      )}

      {isLoading ? (
        <>
          <CircleNotch size={22} weight="bold" className="animate-spin" aria-hidden="true" />
          <span>Определяем место…</span>
        </>
      ) : (
        <>
          <Fingerprint size={22} weight="fill" aria-hidden="true" />
          <span>Отметиться</span>
        </>
      )}

      {/* Keyframes — injected once via <style>. Compositor-only (box-shadow
          spreads the pulse; `transform: scale()` would be cheaper but the
          expanding-ring affordance is a brand-level signal, not generic micro. */}
      <style>{`
        @keyframes ruttrack-checkin-pulse {
          0%   { box-shadow: 0 0 0 0   color-mix(in oklab, var(--accent-primary) 55%, transparent); }
          70%  { box-shadow: 0 0 0 16px color-mix(in oklab, var(--accent-primary) 0%, transparent); }
          100% { box-shadow: 0 0 0 0   color-mix(in oklab, var(--accent-primary) 0%, transparent); }
        }
        @media (prefers-reduced-motion: reduce) {
          [aria-label="Отметиться"] span[aria-hidden="true"] { animation: none !important; }
        }
      `}</style>
    </motion.button>
  )
}
