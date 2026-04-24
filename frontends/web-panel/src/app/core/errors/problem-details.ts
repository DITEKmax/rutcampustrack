import { HttpErrorResponse } from '@angular/common/http';

/**
 * RFC 9457 (ex-RFC 7807) Problem Details parser — Angular side.
 *
 * M07 G4 (QC3) + M13 G5: единая нормализация ошибок от backend'а.
 * Backend canonical (M11 G0) = `fieldErrors: FieldError[]`. Adapter
 * переименовывает на TS-уровне в `invalidParams` как того требует
 * RFC 9457 Extension Members.
 */

export interface InvalidParam {
  /** Имя DTO-поля, не прошедшего валидацию. */
  field: string;
  /** Отклонённое значение (может быть `null`). */
  rejectedValue?: unknown;
  /** Сообщение валидатора (RU). */
  message: string;
}

export interface ProblemDetails {
  status: number;
  type: string;
  title: string;
  detail: string;
  instance?: string;
  timestamp?: string;
  /** Correlation id из MDC (M04 tracing) — отображается в snackbar для support. */
  traceId?: string;
  /** Validation errors (400). Пусто для не-validation ошибок. */
  invalidParams: InvalidParam[];
  /** Сервисные extras (BUG-006-2 cascade deletion counts и т.п.). */
  extras?: Record<string, unknown>;
  /** Имя DTO-поля, вызвавшего конфликт (BUG-006-2). */
  field?: string;
}

interface RawErrorBody {
  status?: number;
  type?: string;
  title?: string;
  detail?: string;
  instance?: string;
  timestamp?: string;
  traceId?: string;
  /** M11 G0 canonical. Backend shape: { field, rejectedValue?, message }. */
  fieldErrors?: Array<{ field?: string; rejectedValue?: unknown; message?: string }>;
  field?: string;
  extras?: Record<string, unknown>;
}

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
};

function fallbackTitle(status: number): string {
  return FALLBACK_TITLES[status] ?? 'Ошибка';
}

function coerceInvalidParams(raw: RawErrorBody): InvalidParam[] {
  const source = raw.fieldErrors ?? [];
  return source
    .filter((item): item is { field?: string; rejectedValue?: unknown; message?: string } => !!item)
    .map((item) => ({
      field: typeof item.field === 'string' ? item.field : '',
      rejectedValue: item.rejectedValue,
      message: typeof item.message === 'string' ? item.message : '',
    }));
}

/**
 * Парсит `HttpErrorResponse` в нормализованный ProblemDetails.
 * Всегда возвращает валидный объект (fallback-синтез для не-RFC тел /
 * network errors).
 */
export function parseProblemDetails(error: unknown): ProblemDetails {
  if (!(error instanceof HttpErrorResponse)) {
    return {
      status: 0,
      type: 'about:blank',
      title: 'Ошибка',
      detail: error instanceof Error ? error.message : 'Неизвестная ошибка',
      invalidParams: [],
    };
  }

  // Network-error / CORS — HttpErrorResponse.status = 0.
  if (error.status === 0) {
    return {
      status: 0,
      type: 'about:blank',
      title: 'Нет соединения',
      detail: 'Проверьте интернет и попробуйте ещё раз.',
      invalidParams: [],
    };
  }

  const status = error.status;
  const raw = (error.error ?? {}) as RawErrorBody | string;

  // HTML / plain text body.
  if (typeof raw !== 'object' || raw === null) {
    return {
      status,
      type: 'about:blank',
      title: fallbackTitle(status),
      detail: typeof raw === 'string' ? raw : `Код ошибки ${status}`,
      invalidParams: [],
    };
  }

  const hasRfc7807Shape = 'title' in raw || 'detail' in raw || 'type' in raw;
  if (!hasRfc7807Shape) {
    return {
      status,
      type: 'about:blank',
      title: fallbackTitle(status),
      detail: `Код ошибки ${status}`,
      invalidParams: [],
    };
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
  };
}
