import { StudentApi } from '../../../mobile-core/src/api/student-client'
import { webSession } from '../auth/session'

export class WebAuthorityChangedError extends Error {
  constructor() {
    super('Сессия или роль изменилась. Повтори действие в текущем разделе.')
    this.name = 'WebAuthorityChangedError'
  }
}

/** One admitted feature instance owns every initial request and token-refresh retry. */
export function createStudentApi(): StudentApi {
  const owner = webSession.identity.value
  const assertOwner = () => {
    if (webSession.identity.value !== owner) throw new WebAuthorityChangedError()
  }
  return new StudentApi({
    accessToken: () => {
      assertOwner()
      return webSession.accessToken()
    },
    onUnauthorized: async () => {
      assertOwner()
      await webSession.refresh()
      assertOwner()
    },
  })
}

/** Shared transport for role adapters; caller owns decoding and mutation reconciliation. */
export async function webFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  const token = webSession.accessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  return fetch(path, { ...init, headers, credentials: 'include' })
}
