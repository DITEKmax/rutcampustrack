interface TmaAuthResponse { accessToken?: string }

export class TmaAuthError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
    this.name = 'TmaAuthError'
  }
}

export function createTmaAuthRequest(initData: string): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ initData }),
    credentials: 'include',
  }
}

export async function authenticateTma(fetcher: typeof fetch, initData: string): Promise<string> {
  const response = await fetcher('/api/auth/tma', createTmaAuthRequest(initData))
  if (!response.ok) throw new TmaAuthError(response.status, 'Telegram не подтвердил сессию')
  const token = await response.json() as TmaAuthResponse
  if (!token.accessToken) throw new TmaAuthError(response.status, 'Сервер не вернул access token')
  return token.accessToken
}