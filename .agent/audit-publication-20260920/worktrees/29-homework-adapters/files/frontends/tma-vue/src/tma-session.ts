import { ref } from 'vue'
import { createGenerationBoundStudentApi, StaleSessionGenerationError } from '@rct/mobile-core'
import type { StudentApi } from '@rct/mobile-core'
import { authenticateTma } from './tma-auth'

export interface TmaSessionOptions {
  fetcher: typeof fetch
  getInitData: () => string | null
}

/** In-memory Telegram session owner; no initData or bearer token is persisted. */
export function useTmaSession(options: TmaSessionOptions) {
  const accessToken = ref<string | null>(null)
  const resetGeneration = ref(0)
  let authenticateInFlight: { generation: number; promise: Promise<void> } | null = null

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

  async function authenticateFor(generation: number): Promise<void> {
    assertCurrent(generation)
    if (authenticateInFlight?.generation === generation) return authenticateInFlight.promise
    const initData = options.getInitData()
    if (!initData) throw new Error('Открой приложение из Telegram')

    let promise: Promise<void>
    promise = authenticateTma(options.fetcher, initData)
      .then((token) => {
        assertCurrent(generation)
        accessToken.value = token
      })
      .finally(() => {
        if (authenticateInFlight?.promise === promise) authenticateInFlight = null
      })
    authenticateInFlight = { generation, promise }
    return promise
  }

  function authenticate(): Promise<void> {
    return authenticateFor(currentGeneration())
  }

  function createApi(fetcher?: typeof fetch): StudentApi {
    return createGenerationBoundStudentApi({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
    }, fetcher)
  }

  function clear(): void {
    resetGeneration.value += 1
    accessToken.value = null
  }

  return {
    accessToken,
    resetGeneration,
    generation: resetGeneration,
    currentGeneration,
    isCurrent: (generation: number) => generation === currentGeneration(),
    authenticate,
    authenticateFor,
    createApi,
    clear,
  }
}
