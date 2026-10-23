import { StudentApi } from '../../../mobile-core/src/api/student-client'
import { webSession } from '../auth/session'

export class WebAuthorityChangedError extends Error {
  constructor() {
    super('Сессия или роль изменилась. Повтори действие в текущем разделе.')
    this.name = 'WebAuthorityChangedError'
  }
}

export interface WebTransport {
  fetcher: typeof fetch
  accessToken(): string | null
  onUnauthorized(): Promise<void>
  assertCurrent(): void
}

/** One feature instance owns its requests, explicit recovery, and async continuations. */
export function createWebTransport(): WebTransport {
  const owner = webSession.identity.value
  const assertCurrent = () => {
    if (webSession.identity.value !== owner) throw new WebAuthorityChangedError()
  }
  const accessToken = () => {
    assertCurrent()
    return webSession.accessToken()
  }
  return {
    accessToken,
    assertCurrent,
    fetcher: async (input, init) => {
      assertCurrent()
      const headers = new Headers(input instanceof Request ? input.headers : undefined)
      for (const [name, value] of new Headers(init?.headers)) headers.set(name, value)
      if (!headers.has('Accept')) headers.set('Accept', 'application/json')
      const token = accessToken()
      if (token) headers.set('Authorization', `Bearer ${token}`)
      else headers.delete('Authorization')
      const response = await fetch(input, { ...init, headers, credentials: 'include' })
      assertCurrent()
      return response
    },
    // Refresh is explicit. Callers must reconcile uncertain mutations rather than replay them.
    onUnauthorized: async () => {
      assertCurrent()
      await webSession.refresh()
      assertCurrent()
    },
  }
}

/** The existing adapter retries only a rejected HTTP 401, under the same admitted owner. */
export function createStudentApi(): StudentApi {
  return new StudentApi(createWebTransport())
}
