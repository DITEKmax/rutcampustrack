import { useMemo, useState } from 'react'
import {
  Bell,
  BellSlash,
  CheckCircle,
  GearSix,
  Info,
  ShieldCheck,
  Warning,
  X,
} from '@phosphor-icons/react'
import { Button } from '@/components/ui/button'
import { usePushSubscription, type PushState } from './usePushSubscription'

type Platform = 'android' | 'ios' | 'desktop'

function detectPlatform(): Platform {
  if (typeof navigator === 'undefined') return 'desktop'
  const ua = navigator.userAgent
  if (/iphone|ipad|ipod/i.test(ua)) return 'ios'
  if (/android/i.test(ua)) return 'android'
  return 'desktop'
}

function getGuide(platform: Platform): { title: string; steps: string[] } {
  if (platform === 'ios') {
    return {
      title: 'iPhone или iPad',
      steps: [
        'Откройте RutTrack именно с иконки на домашнем экране.',
        'Нажмите «Включить уведомления» в приложении.',
        'Если уведомления не появляются: Настройки -> Уведомления -> RutTrack или Safari -> Разрешить уведомления.',
        'Проверьте, что режим Фокусирования не скрывает уведомления RutTrack.',
      ],
    }
  }

  if (platform === 'android') {
    return {
      title: 'Android',
      steps: [
        'Нажмите «Включить уведомления» и подтвердите запрос браузера.',
        'Если было заблокировано: удерживайте иконку RutTrack -> О приложении -> Уведомления -> Разрешить.',
        'В Chrome проверьте: Настройки -> Настройки сайтов -> Уведомления -> RutTrack -> Разрешить.',
        'Если уведомления задерживаются, отключите экономию батареи для RutTrack или Chrome.',
      ],
    }
  }

  return {
    title: 'Браузер на компьютере',
    steps: [
      'Нажмите «Включить уведомления» и разрешите запрос браузера.',
      'Если запрос был заблокирован, откройте настройки сайта через значок замка в адресной строке.',
      'Проверьте системные настройки уведомлений для браузера.',
    ],
  }
}

function errorText(error: string | null): string | null {
  if (!error) return null
  if (error === 'iOS_NOT_STANDALONE') {
    return 'Установите RutTrack на домашний экран и откройте его с иконки, чтобы получать push-уведомления на iOS.'
  }
  if (error === 'PERMISSION_NOT_GRANTED') {
    return 'Сначала разрешите уведомления для RutTrack.'
  }
  return error
}

function statusCopy(
  state: PushState,
  subscribed: boolean,
  checking: boolean,
): { title: string; body: string; icon: typeof Bell; tone: string } {
  if (state === 'unsupported') {
    return {
      title: 'Уведомления недоступны',
      body: 'Нужны HTTPS, Service Worker и браузер с Web Push. На iOS приложение должно быть установлено на домашний экран.',
      icon: BellSlash,
      tone: 'text-muted-foreground',
    }
  }

  if (state === 'denied') {
    return {
      title: 'Уведомления заблокированы',
      body: 'RutTrack не может показать системный запрос повторно. Разрешение нужно вернуть в настройках телефона или браузера.',
      icon: Warning,
      tone: 'text-destructive',
    }
  }

  if (state === 'granted' && subscribed) {
    return {
      title: 'Уведомления включены',
      body: 'Подписка активна на этом устройстве. Проверьте шторку тестовым уведомлением.',
      icon: CheckCircle,
      tone: 'text-emerald-600 dark:text-emerald-400',
    }
  }

  if (state === 'granted') {
    return {
      title: checking ? 'Проверяем подписку' : 'Разрешение есть, подписки нет',
      body: checking
        ? 'Проверяем локальную push-подписку и синхронизацию с сервером.'
        : 'Разрешение браузера уже выдано, но подписку нужно восстановить на сервере.',
      icon: ShieldCheck,
      tone: 'text-amber-600 dark:text-amber-300',
    }
  }

  return {
    title: 'Push-уведомления',
    body: 'Включите push, затем отправьте тест, чтобы убедиться, что уведомление попадает в шторку телефона.',
    icon: Bell,
    tone: 'text-foreground',
  }
}

export function PushPermissionCard() {
  const {
    state,
    subscribed,
    checking,
    loading,
    error,
    subscribe,
    unsubscribe,
    showTestNotification,
  } = usePushSubscription()
  const [guideOpen, setGuideOpen] = useState(false)
  const message = errorText(error)
  const copy = statusCopy(state, subscribed, checking)
  const StatusIcon = copy.icon

  return (
    <div className="rounded-xl border bg-card p-4 space-y-3">
      <div className="flex items-start gap-3">
        <div className={`mt-0.5 ${copy.tone}`}>
          <StatusIcon size={21} weight="bold" />
        </div>
        <div className="min-w-0 flex-1 space-y-1">
          <div className="text-sm font-medium">{copy.title}</div>
          <p className="text-xs leading-relaxed text-muted-foreground">{copy.body}</p>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label="Как настроить уведомления"
          onClick={() => setGuideOpen(true)}
        >
          <GearSix size={17} weight="bold" />
        </Button>
      </div>

      {message && (
        <div
          className="flex items-start gap-2 rounded-lg border border-destructive/20 bg-destructive/5 p-3 text-xs leading-relaxed text-destructive"
          role="alert"
        >
          <Warning size={16} weight="bold" className="mt-0.5 shrink-0" />
          <span>{message}</span>
        </div>
      )}

      {state === 'unsupported' && (
        <Button
          variant="outline"
          size="sm"
          className="min-h-[44px] w-full"
          onClick={() => setGuideOpen(true)}
        >
          <Info size={18} weight="bold" className="mr-2" />
          Что проверить
        </Button>
      )}

      {state === 'denied' && (
        <div className="grid gap-2 sm:grid-cols-2">
          <Button
            variant="default"
            size="sm"
            className="min-h-[44px] w-full"
            disabled
          >
            <Bell size={18} weight="bold" className="mr-2" />
            Включить уведомления
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="min-h-[44px] w-full"
            onClick={() => setGuideOpen(true)}
          >
            <GearSix size={18} weight="bold" className="mr-2" />
            Открыть инструкцию
          </Button>
        </div>
      )}

      {state === 'granted' && subscribed && (
        <div className="grid gap-2 sm:grid-cols-2">
          <Button
            variant="default"
            size="sm"
            className="min-h-[44px] w-full"
            onClick={showTestNotification}
            disabled={loading || checking}
          >
            <Bell size={18} weight="bold" className="mr-2" />
            Тест в шторку
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="min-h-[44px] w-full"
            onClick={unsubscribe}
            disabled={loading || checking}
          >
            <BellSlash size={18} weight="bold" className="mr-2" />
            Отключить уведомления
          </Button>
        </div>
      )}

      {state === 'granted' && !subscribed && (
        <div className="grid gap-2 sm:grid-cols-2">
          <Button
            variant="default"
            size="sm"
            className="min-h-[44px] w-full"
            onClick={subscribe}
            disabled={loading || checking}
          >
            <Bell size={18} weight="bold" className="mr-2" />
            Восстановить push
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="min-h-[44px] w-full"
            onClick={() => setGuideOpen(true)}
          >
            <GearSix size={18} weight="bold" className="mr-2" />
            Настройки
          </Button>
        </div>
      )}

      {state === 'default' && (
        <div className="grid gap-2 sm:grid-cols-2">
          <Button
            variant="default"
            size="sm"
            className="min-h-[44px] w-full"
            onClick={subscribe}
            disabled={loading || checking}
          >
            <Bell size={18} weight="bold" className="mr-2" />
            Включить уведомления
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="min-h-[44px] w-full"
            onClick={() => setGuideOpen(true)}
          >
            <GearSix size={18} weight="bold" className="mr-2" />
            Как настроить
          </Button>
        </div>
      )}

      <NotificationSettingsDialog
        open={guideOpen}
        state={state}
        loading={loading}
        onClose={() => setGuideOpen(false)}
        onSubscribe={subscribe}
        onTest={showTestNotification}
      />
    </div>
  )
}

interface NotificationSettingsDialogProps {
  open: boolean
  state: PushState
  loading: boolean
  onClose: () => void
  onSubscribe: () => void
  onTest: () => void
}

function NotificationSettingsDialog({
  open,
  state,
  loading,
  onClose,
  onSubscribe,
  onTest,
}: NotificationSettingsDialogProps) {
  const guide = useMemo(() => getGuide(detectPlatform()), [])
  if (!open) return null

  const primaryAction = () => {
    if (state === 'default') {
      onSubscribe()
      return
    }
    if (state === 'granted') {
      onTest()
      return
    }
    onClose()
  }

  const primaryLabel = state === 'default'
    ? 'Включить уведомления'
    : state === 'granted'
      ? 'Тест в шторку'
      : 'Понятно'

  return (
    <div
      className="fixed inset-0 z-[var(--z-modal)] flex items-end bg-black/45 p-3 pb-[calc(env(safe-area-inset-bottom)+0.75rem)] sm:items-center sm:justify-center sm:p-3"
      role="presentation"
      onClick={onClose}
    >
      <div
        className="max-h-[90vh] w-full max-w-md overflow-y-auto rounded-xl border bg-background p-4 shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="push-settings-title"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start gap-3">
          <div className="mt-0.5 rounded-lg bg-primary/10 p-2 text-primary">
            <GearSix size={19} weight="bold" />
          </div>
          <div className="min-w-0 flex-1">
            <h2 id="push-settings-title" className="text-base font-semibold">
              Настройка уведомлений
            </h2>
            <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
              {guide.title}
            </p>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="Закрыть"
            onClick={onClose}
          >
            <X size={16} weight="bold" />
          </Button>
        </div>

        <ol className="mt-4 space-y-3">
          {guide.steps.map((step, index) => (
            <li key={step} className="flex gap-3 text-sm leading-relaxed">
              <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold text-muted-foreground">
                {index + 1}
              </span>
              <span>{step}</span>
            </li>
          ))}
        </ol>

        <div className="mt-4 flex gap-2 rounded-lg border bg-muted/35 p-3 text-xs leading-relaxed text-muted-foreground">
          <Info size={16} weight="bold" className="mt-0.5 shrink-0" />
          <span>
            Открыть эти системные настройки из PWA напрямую нельзя: Android и iOS
            не дают веб-странице такого API. RutTrack может только показать путь и
            проверить результат тестовым уведомлением.
          </span>
        </div>

        <div className="mt-4 grid gap-2 sm:grid-cols-2">
          <Button
            type="button"
            variant="default"
            size="sm"
            className="min-h-[44px] w-full"
            onClick={primaryAction}
            disabled={loading}
          >
            {state === 'granted' ? (
              <Bell size={18} weight="bold" className="mr-2" />
            ) : (
              <ShieldCheck size={18} weight="bold" className="mr-2" />
            )}
            {primaryLabel}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="min-h-[44px] w-full"
            onClick={onClose}
          >
            Закрыть
          </Button>
        </div>
      </div>
    </div>
  )
}
