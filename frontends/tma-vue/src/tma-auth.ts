interface TmaAuthResponse { accessToken?: string }

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
  if (!response.ok) throw new Error('Telegram не подтвердил сессию')
  const token = await response.json() as TmaAuthResponse
  if (!token.accessToken) throw new Error('Сервер не вернул access token')
  return token.accessToken
}
