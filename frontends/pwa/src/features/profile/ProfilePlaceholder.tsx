import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { useAuth } from '@/features/auth/AuthProvider'

export default function ProfilePlaceholder() {
  const { logout, user } = useAuth()
  const [showConfirm, setShowConfirm] = useState(false)
  const [isLoggingOut, setIsLoggingOut] = useState(false)

  const handleLogout = async () => {
    setIsLoggingOut(true)
    await logout()
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] gap-6 px-8">
      <p className="text-sm text-muted-foreground">{user?.role}</p>
      {!showConfirm ? (
        <Button
          variant="destructive"
          className="min-h-[44px]"
          onClick={() => setShowConfirm(true)}
        >
          Выйти
        </Button>
      ) : (
        <Alert>
          <AlertDescription className="flex flex-col gap-4">
            <p className="text-sm">Вы уверены, что хотите выйти?</p>
            <div className="flex gap-3">
              <Button
                variant="destructive"
                className="min-h-[44px]"
                onClick={handleLogout}
                disabled={isLoggingOut}
              >
                Выйти
              </Button>
              <Button
                variant="ghost"
                className="min-h-[44px]"
                onClick={() => setShowConfirm(false)}
              >
                Остаться в системе
              </Button>
            </div>
          </AlertDescription>
        </Alert>
      )}
    </div>
  )
}
