import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from './AuthProvider'
import { useNetworkStatus } from '@/shared/hooks/useNetworkStatus'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { LoadingSpinner } from '@/shared/components/LoadingSpinner'
import { OfflineBanner } from '@/shared/components/OfflineBanner'
import { AxiosError } from 'axios'

export function LoginPage() {
  const { login } = useAuth()
  const { isOnline } = useNetworkStatus()
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!username || !password) return

    setError(null)
    setIsLoading(true)

    try {
      await login({ login: username, password })
      navigate('/home', { replace: true })
    } catch (err) {
      if (err instanceof AxiosError) {
        if (!err.response) {
          setError('Не удалось подключиться к серверу. Проверьте соединение')
        } else if (err.response.status === 401) {
          setError('Неверное имя пользователя или пароль')
        } else {
          setError('Ошибка сервера. Попробуйте ещё раз')
        }
      } else {
        setError('Ошибка сервера. Попробуйте ещё раз')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <OfflineBanner />
      <div className="w-full max-w-sm mx-auto px-4">
        <h1 className="text-3xl font-semibold text-center mb-8 text-foreground">RutTrack</h1>
        <Card className="border border-border rounded-2xl shadow-sm">
          <CardContent className="p-8">
            <form onSubmit={handleSubmit} className="flex flex-col gap-6">
              {error && (
                <Alert variant="destructive">
                  <AlertDescription>{error}</AlertDescription>
                </Alert>
              )}

              <div className="flex flex-col gap-2">
                <Label htmlFor="username">Имя пользователя</Label>
                <Input
                  id="username"
                  type="text"
                  autoComplete="username"
                  inputMode="text"
                  placeholder="student00001"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  disabled={!isOnline || isLoading}
                />
              </div>

              <div className="flex flex-col gap-2">
                <Label htmlFor="password">Пароль</Label>
                <Input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={!isOnline || isLoading}
                />
              </div>

              <Button
                type="submit"
                className="w-full min-h-[44px] active:scale-95 transition-transform duration-[80ms]"
                disabled={!isOnline || isLoading}
              >
                {isLoading ? (
                  <span className="flex items-center gap-2">
                    <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground border-t-transparent" />
                  </span>
                ) : !isOnline ? (
                  'Нет подключения'
                ) : (
                  'Войти в систему'
                )}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
