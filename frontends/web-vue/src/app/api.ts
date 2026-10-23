import { StudentApi } from '../../../mobile-core/src/api/student-client'
import { webSession } from '../auth/session'

export const studentApi = new StudentApi({
  accessToken: webSession.accessToken,
  onUnauthorized: webSession.refresh,
})

/** Shared transport for role adapters; caller owns decoding and mutation reconciliation. */
export async function webFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  const token = webSession.accessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  return fetch(path, { ...init, headers, credentials: 'include' })
}
