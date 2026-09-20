import { ref } from 'vue'

interface TokenResponse { accessToken?: string }

export function usePwaAuth() {
  const accessToken = ref<string | null>(null)
  let refreshInFlight: Promise<void> | null = null

  async function refresh(): Promise<void> {
    if (!refreshInFlight) {
      refreshInFlight = fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
        .then(async (response) => {
          if (!response.ok) throw new Error('Сессию не удалось восстановить')
          const token = await response.json() as TokenResponse
          if (!token.accessToken) throw new Error('Сервер не вернул access token')
          accessToken.value = token.accessToken
        })
        .finally(() => { refreshInFlight = null })
    }
    return refreshInFlight
  }

  function clear(): void { accessToken.value = null }

  async function logout(clearSnapshot: () => Promise<void>): Promise<void> {
    try {
      await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' })
    } catch {
      // Local token and partition cleanup is still an explicit logout.
    } finally {
      clear()
      await clearSnapshot()
    }
  }

  return { accessToken, refresh, clear, logout }
}
