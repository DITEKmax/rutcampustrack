import type { AxiosError } from 'axios'

export interface InvalidParam {
  field: string
  rejectedValue?: unknown
  message: string
}

export interface ProblemDetails {
  status: number
  type: string
  title: string
  detail: string
  instance?: string
  timestamp?: string
  traceId?: string
  invalidParams: InvalidParam[]
  extras?: Record<string, unknown>
  field?: string
}

type RawErrorBody = {
  status?: number
  type?: string
  title?: string
  detail?: string
  instance?: string
  timestamp?: string
  traceId?: string
  fieldErrors?: Array<{ field?: string; rejectedValue?: unknown; message?: string }>
  extras?: Record<string, unknown>
  field?: string
}

const FALLBACK_TITLES: Record<number, string> = {
  400: 'Неверный запрос',
  401: 'Требуется вход',
  403: 'Недостаточно прав',
  404: 'Ресурс не найден',
  409: 'Конфликт',
  422: 'Невалидные данные',
  429: 'Слишком много запросов',
  500: 'Ошибка сервера',
  502: 'Сервер недоступен',
  503: 'Сервис временно недоступен',
  504: 'Превышено время ожидания',
}

function fallbackTitle(status: number): string {
  return FALLBACK_TITLES[status] ?? 'Ошибка'
}

function coerceInvalidParams(raw: RawErrorBody): InvalidParam[] {
  return (raw.fieldErrors ?? []).map((item) => ({
    field: typeof item.field === 'string' ? item.field : '',
    rejectedValue: item.rejectedValue,
    message: typeof item.message === 'string' ? item.message : '',
  }))
}

export function parseProblemDetails(error: unknown): ProblemDetails {
  if (!error || typeof error !== 'object' || !('isAxiosError' in error)) {
    return {
      status: 0,
      type: 'about:blank',
      title: 'Ошибка',
      detail: error instanceof Error ? error.message : 'Неизвестная ошибка',
      invalidParams: [],
    }
  }

  const ax = error as AxiosError<unknown>
  if (!ax.response) {
    return {
      status: 0,
      type: 'about:blank',
      title: 'Нет соединения',
      detail: 'Проверьте интернет и попробуйте ещё раз.',
      invalidParams: [],
    }
  }

  const status = ax.response.status
  const raw = (ax.response.data ?? {}) as RawErrorBody
  const hasProblemShape =
    raw &&
    typeof raw === 'object' &&
    ('title' in raw || 'detail' in raw || 'type' in raw)

  if (!hasProblemShape) {
    return {
      status,
      type: 'about:blank',
      title: fallbackTitle(status),
      detail: typeof raw === 'string' ? raw : `Код ошибки ${status}`,
      invalidParams: [],
    }
  }

  return {
    status: typeof raw.status === 'number' ? raw.status : status,
    type: typeof raw.type === 'string' ? raw.type : 'about:blank',
    title: typeof raw.title === 'string' && raw.title.length > 0 ? raw.title : fallbackTitle(status),
    detail: typeof raw.detail === 'string' ? raw.detail : '',
    instance: typeof raw.instance === 'string' ? raw.instance : undefined,
    timestamp: typeof raw.timestamp === 'string' ? raw.timestamp : undefined,
    traceId: typeof raw.traceId === 'string' ? raw.traceId : undefined,
    invalidParams: coerceInvalidParams(raw),
    extras: raw.extras,
    field: typeof raw.field === 'string' ? raw.field : undefined,
  }
}

const PROBLEM_KEY = Symbol.for('rutcampustrack.problemDetails')

export function attachProblemDetails(error: unknown, problem: ProblemDetails): void {
  if (error && typeof error === 'object') {
    ;(error as Record<symbol, unknown>)[PROBLEM_KEY] = problem
  }
}

export function getProblemDetails(error: unknown): ProblemDetails {
  if (error && typeof error === 'object') {
    const cached = (error as Record<symbol, unknown>)[PROBLEM_KEY]
    if (cached && typeof cached === 'object') return cached as ProblemDetails
  }
  return parseProblemDetails(error)
}
