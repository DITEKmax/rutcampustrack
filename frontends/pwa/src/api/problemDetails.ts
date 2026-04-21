/**
 * RFC 9457 (ex-RFC 7807) Problem Details parser.
 *
 * M07 G4 (QC3): единая нормализация ошибок от backend'а в типизированный
 * shape. Backend сейчас отдаёт `fieldErrors` (pre-M11 shape) либо
 * `invalidParams` (shared-web shape, post-M11) — adapter унифицирует в
 * `invalidParams` как того требует RFC 9457.
 *
 * Парсер терпим к тому, что backend может вернуть не-JSON (network error,
 * CORS, HTML 502), либо JSON без обязательных полей. В таких случаях
 * синтезируется generic ProblemDetails на базе HTTP status / axios message.
 */

import type { AxiosError } from 'axios'

export interface InvalidParam {
  /** Имя DTO-поля, не прошедшего валидацию. */
  field: string
  /** Отклонённое значение (может быть `null`). */
  rejectedValue?: unknown
  /** Сообщение валидатора (RU). */
  message: string
}

/**
 * Нормализованный ProblemDetails для frontend consumers. Имя поля
 * `invalidParams` соответствует RFC 9457; если backend отдаёт legacy
 * `fieldErrors` — adapter их переименовывает.
 */
export interface ProblemDetails {
  status: number
  type: string
  title: string
  detail: string
  instance?: string
  timestamp?: string
  /** Correlation id из MDC (M04 tracing) — отображается в toast для support. */
  traceId?: string
  /** Validation errors (400 Bad Request). Пусто для не-validation ошибок. */
  invalidParams: InvalidParam[]
  /** Сервисные extras (BUG-006-2 cascade deletion counts и т.п.). */
  extras?: Record<string, unknown>
  /** Имя DTO-поля, вызвавшего конфликт (BUG-006-2, сейчас только academic). */
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
  /** Pre-M11 name — shape: { field, rejectedValue?, message } (attendance, schedule, academic). */
  fieldErrors?: Array<{ field?: string; rejectedValue?: unknown; message?: string }>
  /** Post-M11 name — shape: { field, rejectedValue?, message } (shared-web ErrorResponse). */
  invalidParams?: Array<{ field?: string; rejectedValue?: unknown; message?: string }>
  field?: string
  extras?: Record<string, unknown>
}

/** Fallback titles по HTTP status — когда backend не отдал RFC 7807. */
const FALLBACK_TITLES: Record<number, string> = {
  400: 'Неверный запрос',
  401: 'Требуется вход',
  403: 'Недостаточно прав',
  404: 'Ресурс не найден',
  409: 'Конфликт',
  410: 'Ресурс больше не доступен',
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
  const source = raw.invalidParams ?? raw.fieldErrors ?? []
  return source
    .filter((item): item is { field?: string; rejectedValue?: unknown; message?: string } => !!item)
    .map((item) => ({
      field: typeof item.field === 'string' ? item.field : '',
      rejectedValue: item.rejectedValue,
      message: typeof item.message === 'string' ? item.message : '',
    }))
}

/**
 * Парсит любой axios error в нормализованный ProblemDetails.
 *
 * Возвращает `ProblemDetails` всегда — если backend молчит/не-JSON/нет
 * response (network error), синтезирует на базе статуса или запасного
 * сообщения про network. Это упрощает call-site: `catch (err) { show(parse(err)) }`.
 */
export function parseProblemDetails(error: unknown): ProblemDetails {
  // Не-axios ошибка (напр. JS TypeError) — generic fallback.
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

  // Нет ответа — network error (offline, CORS, DNS).
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
  const hasRfc7807Shape = typeof raw === 'object' && raw !== null && ('title' in raw || 'detail' in raw || 'type' in raw)

  if (!hasRfc7807Shape) {
    // Backend вернул HTML / plain text / пустое тело.
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
    traceId: typeof raw.traceId === 'string' && raw.traceId.length > 0 ? raw.traceId : undefined,
    invalidParams: coerceInvalidParams(raw),
    extras: raw.extras,
    field: typeof raw.field === 'string' ? raw.field : undefined,
  }
}

/**
 * Symbol-key на axios error, в который response interceptor кладёт
 * распарсенный ProblemDetails. Consumer читает через {@link getProblemDetails}
 * без повторного парсинга.
 */
const PROBLEM_KEY = Symbol.for('rutcampustrack.problemDetails')

/** Attach (response interceptor). */
export function attachProblemDetails(error: unknown, problem: ProblemDetails): void {
  if (error && typeof error === 'object') {
    ;(error as Record<symbol, unknown>)[PROBLEM_KEY] = problem
  }
}

/**
 * Извлечь уже распарсенный ProblemDetails; fallback — распарсить заново.
 * Вызывай в onError/catch для получения типизированной ошибки.
 */
export function getProblemDetails(error: unknown): ProblemDetails {
  if (error && typeof error === 'object') {
    const cached = (error as Record<symbol, unknown>)[PROBLEM_KEY]
    if (cached && typeof cached === 'object') return cached as ProblemDetails
  }
  return parseProblemDetails(error)
}
