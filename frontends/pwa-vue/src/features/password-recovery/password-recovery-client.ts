export type PasswordRecoveryChallenge = {
  challengeId: string
  ttlSeconds: number
}

export type PasswordRecoveryVerification = {
  resetTicket: string
  expiresInSeconds: number
  attemptsRemaining: number
}

export interface PasswordRecoveryClient {
  request(login: string, signal?: AbortSignal): Promise<PasswordRecoveryChallenge>
  verify(input: { challengeId: string; code: string }, signal?: AbortSignal): Promise<PasswordRecoveryVerification>
  complete(input: { resetTicket: string; newPassword: string }, signal?: AbortSignal): Promise<void>
}

export type PasswordRecoveryProblem = {
  attemptsRemaining: number | null
  retryAfterSeconds: number | null
  detail: string | null
}

export class PasswordRecoveryApiError extends Error {
  readonly status: number
  readonly code: string | null
  readonly problem: PasswordRecoveryProblem

  constructor(
    status: number,
    code: string | null,
    problem: PasswordRecoveryProblem = { attemptsRemaining: null, retryAfterSeconds: null, detail: null },
  ) {
    super('Password recovery request failed')
    this.name = 'PasswordRecoveryApiError'
    this.status = status
    this.code = code
    this.problem = problem
  }
}

type UnknownRecord = Record<string, unknown>

export function createPasswordRecoveryClient(fetcher: typeof fetch = globalThis.fetch): PasswordRecoveryClient {
  return {
    request: async (login, signal) => {
      const response = await send(fetcher, '/api/auth/password-reset/request', { login }, signal)
      if (response.status !== 202) {
        if (!response.ok) throw await responseError(response)
        throw new PasswordRecoveryApiError(response.status, 'PROTOCOL_ERROR')
      }
      const value = await readJson(response)
      if (!isRecord(value) || !isNonEmptyString(value.challengeId) || !isPositiveInteger(value.ttlSeconds)) {
        throw new PasswordRecoveryApiError(502, 'PROTOCOL_ERROR')
      }
      return { challengeId: value.challengeId, ttlSeconds: value.ttlSeconds }
    },
    verify: async (input, signal) => {
      const response = await send(fetcher, '/api/auth/password-reset/verify', {
        challengeId: input.challengeId,
        code: input.code,
      }, signal)
      if (!response.ok) throw await responseError(response)
      const value = await readJson(response)
      if (!isRecord(value)
        || !isNonEmptyString(value.resetTicket)
        || !isPositiveInteger(value.expiresInSeconds)
        || !isNonNegativeInteger(value.attemptsRemaining)) {
        throw new PasswordRecoveryApiError(502, 'PROTOCOL_ERROR')
      }
      return {
        resetTicket: value.resetTicket,
        expiresInSeconds: value.expiresInSeconds,
        attemptsRemaining: value.attemptsRemaining,
      }
    },
    complete: async (input, signal) => {
      const response = await send(fetcher, '/api/auth/password-reset/complete', {
        resetTicket: input.resetTicket,
        newPassword: input.newPassword,
      }, signal)
      if (!response.ok) throw await responseError(response)
      if (response.status !== 204) throw new PasswordRecoveryApiError(response.status, 'PROTOCOL_ERROR')
    },
  }
}

async function send(
  fetcher: typeof fetch,
  path: string,
  body: unknown,
  signal?: AbortSignal,
): Promise<Response> {
  let response: Response
  try {
    response = await fetcher(path, {
      method: 'POST',
      credentials: 'omit',
      cache: 'no-store',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      ...(signal ? { signal } : {}),
    })
  } catch (error) {
    if (signal?.aborted || isAbortError(error)) throw error
    throw new PasswordRecoveryApiError(0, 'NETWORK_ERROR')
  }
  return response
}

async function responseError(response: Response): Promise<PasswordRecoveryApiError> {
  let value: unknown = null
  try {
    value = await response.json() as unknown
  } catch {
    // An empty or malformed problem response still maps to a generic UI state.
  }

  const root = isRecord(value) ? value : null
  const extras = root && isRecord(root.extras) ? root.extras : null
  const codeValue = extras?.code ?? root?.code
  const code = isNonEmptyString(codeValue) ? codeValue : null
  const attemptsRemaining = nonNegativeIntegerOrNull(extras?.attemptsRemaining ?? root?.attemptsRemaining)
  const retryAfterSeconds = nonNegativeIntegerOrNull(extras?.retryAfterSeconds ?? root?.retryAfterSeconds)
  const detail = root && typeof root.detail === 'string' ? root.detail : null
  return new PasswordRecoveryApiError(response.status, code, {
    attemptsRemaining,
    retryAfterSeconds,
    detail,
  })
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json() as unknown
  } catch {
    throw new PasswordRecoveryApiError(502, 'PROTOCOL_ERROR')
  }
}

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

function isPositiveInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

function isNonNegativeInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
}

function nonNegativeIntegerOrNull(value: unknown): number | null {
  return isNonNegativeInteger(value) ? value : null
}

function isAbortError(value: unknown): boolean {
  return isRecord(value) && value.name === 'AbortError'
}