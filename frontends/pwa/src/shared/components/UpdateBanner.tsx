import { useCallback, useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { ArrowClockwise, ShieldWarning, WifiSlash } from '@phosphor-icons/react'
import { registerSW } from 'virtual:pwa-register'
import { Button } from '@/components/ui/button'
import {
  APP_UPDATE_REQUIRED_EVENT,
  APP_VERSION,
  VERSION_POLICY_URL,
  isUpdateRequiredByPolicy,
  type UpdateRequiredEventDetail,
  type VersionPolicy,
} from '@/shared/lib/appVersion'

const POLICY_CHECK_INTERVAL_MS = 60 * 1000

type UpdateReason = 'service-worker' | 'version-policy' | 'api-version'

interface UpdateGateState {
  reason: UpdateReason
  latest?: string
  minimumSupported?: string
  message?: string
}

function buildVersionPolicyUrl(): string {
  const separator = VERSION_POLICY_URL.includes('?') ? '&' : '?'
  return `${VERSION_POLICY_URL}${separator}t=${Date.now()}`
}

function shouldBlockByPolicy(policy: VersionPolicy): boolean {
  return isUpdateRequiredByPolicy(APP_VERSION, policy)
}

function gateFromPolicy(policy: VersionPolicy): UpdateGateState {
  return {
    reason: 'version-policy',
    latest: policy.latest,
    minimumSupported: policy.minimumSupported,
    message: policy.message,
  }
}

export function UpdateBanner() {
  const [gate, setGate] = useState<UpdateGateState | null>(null)
  const [updating, setUpdating] = useState(false)
  const [offline, setOffline] = useState(false)
  const updateSWRef = useRef<((reload?: boolean) => Promise<void>) | null>(null)
  const swRegistrationRef = useRef<ServiceWorkerRegistration | null>(null)

  const checkPolicy = useCallback(async () => {
    if (typeof window === 'undefined') return
    try {
      const response = await fetch(buildVersionPolicyUrl(), {
        cache: 'no-store',
        headers: { Accept: 'application/json' },
      })
      if (!response.ok) return

      const policy = await response.json() as VersionPolicy
      if (shouldBlockByPolicy(policy)) {
        setGate(gateFromPolicy(policy))
      }
    } catch {
      // Version policy is a guardrail, not a hard dependency. Existing SW
      // update detection still handles deployed app updates offline-first.
    }
  }, [])

  useEffect(() => {
    if (typeof window === 'undefined') return

    let intervalId: number | undefined
    let wasControlled = !!navigator.serviceWorker?.controller

    const updateSW = registerSW({
      immediate: true,
      onNeedRefresh() {
        setGate({ reason: 'service-worker' })
      },
      onRegisteredSW(_swUrl, registration) {
        swRegistrationRef.current = registration ?? null
        void registration?.update()
        intervalId = window.setInterval(() => {
          void registration?.update()
          void checkPolicy()
        }, POLICY_CHECK_INTERVAL_MS)
      },
    })
    updateSWRef.current = updateSW

    const handleControllerChange = () => {
      if (wasControlled) {
        setGate({ reason: 'service-worker' })
      }
      wasControlled = true
    }
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        void swRegistrationRef.current?.update()
        void checkPolicy()
      }
    }
    const handleRequiredUpdate = (event: Event) => {
      const detail = (event as CustomEvent<UpdateRequiredEventDetail>).detail ?? {}
      setGate({
        reason: 'api-version',
        latest: detail.latest,
        minimumSupported: detail.minimumSupported,
        message: detail.message,
      })
    }
    const handleOnline = () => {
      setOffline(false)
      void swRegistrationRef.current?.update()
      void checkPolicy()
    }
    const handleOffline = () => setOffline(true)

    navigator.serviceWorker?.addEventListener('controllerchange', handleControllerChange)
    document.addEventListener('visibilitychange', handleVisibilityChange)
    window.addEventListener(APP_UPDATE_REQUIRED_EVENT, handleRequiredUpdate as EventListener)
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)

    setOffline(!navigator.onLine)
    void checkPolicy()

    return () => {
      if (intervalId) window.clearInterval(intervalId)
      navigator.serviceWorker?.removeEventListener('controllerchange', handleControllerChange)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
      window.removeEventListener(APP_UPDATE_REQUIRED_EVENT, handleRequiredUpdate as EventListener)
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
    }
  }, [checkPolicy])

  const handleUpdate = useCallback(async () => {
    if (offline) return
    setUpdating(true)
    try {
      await swRegistrationRef.current?.update()
      if (updateSWRef.current) {
        await updateSWRef.current(true)
      } else {
        window.location.reload()
      }
      window.setTimeout(() => window.location.reload(), 800)
    } catch {
      window.location.reload()
    }
  }, [offline])

  const title = gate?.reason === 'service-worker'
    ? 'Необходимо обновить RutTrack'
    : 'Версия больше не поддерживается'

  const description = gate?.message
    ?? 'Вышла новая сборка приложения. Обновите PWA, чтобы продолжить работу с актуальными уведомлениями и расписанием.'

  return (
    <AnimatePresence>
      {gate && (
        <motion.div
          className="fixed inset-0 z-[var(--z-overlay)] flex items-end bg-black/55 p-3 backdrop-blur-sm sm:items-center sm:justify-center"
          role="presentation"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.18, ease: 'easeOut' }}
        >
          <motion.div
            role="dialog"
            aria-modal="true"
            aria-labelledby="pwa-update-title"
            initial={{ opacity: 0, y: 24, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 20, scale: 0.98 }}
            transition={{ duration: 0.22, ease: 'easeOut' }}
            className="w-full max-w-md rounded-2xl border bg-background p-5 shadow-2xl"
          >
            <div className="flex items-start gap-3">
              <div className="rounded-xl bg-primary/10 p-2.5 text-primary">
                <ShieldWarning size={24} weight="bold" />
              </div>
              <div className="min-w-0 flex-1">
                <h2 id="pwa-update-title" className="text-lg font-semibold leading-tight">
                  {title}
                </h2>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                  {description}
                </p>
              </div>
            </div>

            <div className="mt-4 grid grid-cols-2 gap-2 rounded-xl border bg-muted/35 p-3 text-xs text-muted-foreground">
              <div>
                <div className="font-medium text-foreground">Текущая</div>
                <div className="mt-1 tabular-nums">{APP_VERSION}</div>
              </div>
              <div>
                <div className="font-medium text-foreground">Требуется</div>
                <div className="mt-1 tabular-nums">
                  {gate.minimumSupported ?? gate.latest ?? 'новая сборка'}
                </div>
              </div>
            </div>

            {offline && (
              <div className="mt-4 flex gap-2 rounded-xl border border-destructive/20 bg-destructive/5 p-3 text-sm text-destructive">
                <WifiSlash size={18} weight="bold" className="mt-0.5 shrink-0" />
                <span>Для обновления нужно подключение к интернету.</span>
              </div>
            )}

            <Button
              type="button"
              size="lg"
              className="mt-5 min-h-[48px] w-full"
              onClick={handleUpdate}
              disabled={updating || offline}
            >
              <ArrowClockwise
                size={19}
                weight="bold"
                className={updating ? 'mr-2 animate-spin' : 'mr-2'}
              />
              {updating ? 'Обновляем...' : 'Обновить приложение'}
            </Button>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
