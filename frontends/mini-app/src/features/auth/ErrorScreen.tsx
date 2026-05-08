import { WarningCircle } from '@phosphor-icons/react'
import { Button } from '@/components/ui/button'

interface ErrorScreenProps {
  onRetry: () => void
  title?: string
  detail?: string
  retryLabel?: string
}

export function ErrorScreen({
  onRetry,
  title = 'Не удалось войти',
  detail = 'Произошла ошибка при авторизации. Попробуйте перезапустить приложение.',
  retryLabel = 'Попробовать снова',
}: ErrorScreenProps) {
  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="bg-card border border-border rounded-xl p-6 max-w-sm w-full flex flex-col items-center gap-4 text-center">
        <WarningCircle size={32} weight="bold" className="text-destructive" />
        <h1 className="text-xl font-semibold">{title}</h1>
        <p className="text-sm text-muted-foreground">
          {detail}
        </p>
        <Button onClick={onRetry} className="w-full min-h-[48px]">
          {retryLabel}
        </Button>
      </div>
    </div>
  )
}
