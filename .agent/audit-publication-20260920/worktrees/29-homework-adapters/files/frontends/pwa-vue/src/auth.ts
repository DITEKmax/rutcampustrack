import { ref } from 'vue'
import { createGenerationBoundStudentApi, StaleSessionGenerationError } from '@rct/mobile-core'
import type { StudentApi } from '@rct/mobile-core'

interface TokenResponse { accessToken?: string }

export interface PwaAuthOptions {
  fetcher?: typeof fetch
}

export class PwaAuthError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
    this.name = 'PwaAuthError'
  }
}

export function usePwaAuth(options: PwaAuthOptions = {}) {
  const accessToken = ref<string | null>(null)
  const resetGeneration = ref(0)
  const request = options.fetcher ?? ((input: RequestInfo | URL, init?: RequestInit) => globalThis.fetch(input, init))
  let refreshInFlight: { generation: number; promise: Promise<void> } | null = null

  function currentGeneration(): number {
    return resetGeneration.value
  }

  function assertCurrent(generation: number): void {
    if (generation !== currentGeneration()) throw new StaleSessionGenerationError()
  }

  function accessTokenFor(generation: number): string | null {
    assertCurrent(generation)
    return accessToken.value
  }

  async function refreshFor(generation: number): Promise<void> {
    assertCurrent(generation)
    if (refreshInFlight?.generation === generation) return refreshInFlight.promise

    let promise: Promise<void>
    promise = request('/api/auth/refresh', { method: 'POST', credentials: 'include' })
      .then(async (response) => {
        if (!response.ok) throw new PwaAuthError(response.status, 'Сессию не удалось восстановить')
        const token = await response.json() as TokenResponse
        if (!token.accessToken) throw new PwaAuthError(response.status, 'Сервер не вернул access token')
        assertCurrent(generation)
        accessToken.value = token.accessToken
      })
      .finally(() => {
        if (refreshInFlight?.promise === promise) refreshInFlight = null
      })
    refreshInFlight = { generation, promise }
    return promise
  }

  function refresh(): Promise<void> {
    return refreshFor(currentGeneration())
  }

  function createApi(fetcher?: typeof fetch): StudentApi {
    return createGenerationBoundStudentApi({
      currentGeneration,
      accessTokenFor,
      refreshFor,
    }, fetcher)
  }

  function setToken(token: string, generation = currentGeneration()): void {
    assertCurrent(generation)
    accessToken.value = token
  }

  /** Invalidates every API client captured before this call. */
  function clear(): void {
    resetGeneration.value += 1
    accessToken.value = null
  }

  async function logout(clearSnapshot: () => Promise<void>): Promise<void> {
    // Invalidate before the remote request so an in-flight 401 cannot retry
    // with a replacement token while logout is still waiting on the network.
    clear()
    try {
      await request('/api/auth/logout', { method: 'POST', credentials: 'include' })
    } catch {
      // Local token and partition cleanup is still an explicit logout.
    } finally {
      await clearSnapshot()
    }
  }

  return {
    accessToken,
    resetGeneration,
    generation: resetGeneration,
    currentGeneration,
    isCurrent: (generation: number) => generation === currentGeneration(),
    refresh,
    refreshFor,
    createApi,
    setToken,
    clear,
    logout,
  }
}
