interface TmaAuthResponse {
  accessToken?: string
}

const proofMessages = {
  TMA_INIT_DATA_INVALID: 'Telegram не подтвердил данные запуска. Открой приложение заново.',
  TMA_INIT_DATA_EXPIRED: 'Данные запуска устарели. Открой приложение заново из Telegram.',
  TMA_INIT_DATA_NOT_YET_VALID: 'Время запуска не подтверждено. Открой приложение заново.',
  TMA_TELEGRAM_NOT_LINKED: 'Этот Telegram ещё не привязан к учётной записи.',
  AUTHORITY_UNAVAILABLE: 'Сервис входа временно недоступен. Попробуй ещё раз чуть позже.',
} as const

export type TmaProofCode = keyof typeof proofMessages

export class TmaAuthError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly code?: TmaProofCode,
  ) {
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
  if (!response.ok) {
    let code: TmaProofCode | undefined
    if (response.status === 401 || response.status === 503) {
      try {
        const problem: unknown = await response.json()
        if (problem && typeof problem === 'object' && 'extras' in problem) {
          const extras = problem.extras
          if (extras && typeof extras === 'object' && 'code' in extras &&
              typeof extras.code === 'string' && Object.hasOwn(proofMessages, extras.code) &&
              (response.status === 503 ? extras.code === 'AUTHORITY_UNAVAILABLE' : extras.code !== 'AUTHORITY_UNAVAILABLE')) {
            code = extras.code as TmaProofCode
          }
        }
      } catch { /* Unknown or malformed responses keep the generic local message. */ }
    }
    throw new TmaAuthError(response.status, code ? proofMessages[code] : 'Telegram не подтвердил сессию', code)
  }
  const token = (await response.json()) as TmaAuthResponse
  if (!token.accessToken) throw new TmaAuthError(response.status, 'Сервер не вернул access token')
  return token.accessToken
}
