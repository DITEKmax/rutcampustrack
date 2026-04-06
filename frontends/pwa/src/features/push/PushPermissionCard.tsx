import { Bell, BellSlash, Warning } from '@phosphor-icons/react'
import { Button } from '@/components/ui/button'
import { usePushSubscription } from './usePushSubscription'

export function PushPermissionCard() {
  const { state, loading, error, subscribe, unsubscribe } = usePushSubscription()

  if (state === 'unsupported') {
    return (
      <div className="rounded-xl border bg-card p-4 space-y-2">
        <div className="flex items-center gap-2 text-muted-foreground">
          <BellSlash size={20} weight="bold" />
          <span className="text-sm font-medium">Уведомления недоступны</span>
        </div>
        <p className="text-xs text-muted-foreground">
          Ваш браузер не поддерживает push-уведомления.
        </p>
      </div>
    )
  }

  return (
    <div className="rounded-xl border bg-card p-4 space-y-3">
      <div className="flex items-center gap-2">
        <Bell size={20} weight="bold" />
        <span className="text-sm font-medium">Push-уведомления</span>
      </div>

      {state === 'denied' && (
        <p className="text-xs text-destructive">
          Уведомления заблокированы в настройках браузера. Разрешите их вручную.
        </p>
      )}

      {error === 'iOS_NOT_STANDALONE' && (
        <div className="flex items-start gap-2 text-xs text-amber-600">
          <Warning size={16} weight="bold" className="mt-0.5 shrink-0" />
          <span>Установите RutTrack на домашний экран, чтобы получать уведомления.</span>
        </div>
      )}

      {error && error !== 'iOS_NOT_STANDALONE' && (
        <p className="text-xs text-destructive">{error}</p>
      )}

      {state === 'granted' ? (
        <Button
          variant="outline"
          size="sm"
          className="min-h-[44px] w-full"
          onClick={unsubscribe}
          disabled={loading}
        >
          <BellSlash size={18} weight="bold" className="mr-2" />
          Отключить уведомления
        </Button>
      ) : (
        <Button
          variant="default"
          size="sm"
          className="min-h-[44px] w-full"
          onClick={subscribe}
          disabled={loading || state === 'denied'}
        >
          <Bell size={18} weight="bold" className="mr-2" />
          Включить уведомления
        </Button>
      )}
    </div>
  )
}
