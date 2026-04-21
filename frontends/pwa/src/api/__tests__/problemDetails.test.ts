import { describe, it, expect } from 'vitest'
import { parseProblemDetails, getProblemDetails, attachProblemDetails } from '../problemDetails'
import type { AxiosError } from 'axios'

function makeAxiosError(status: number, data: unknown): AxiosError {
  return {
    isAxiosError: true,
    name: 'AxiosError',
    message: `Request failed with status ${status}`,
    response: {
      status,
      data,
      statusText: 'x',
      headers: {},
      config: {} as never,
    },
    config: {} as never,
    toJSON: () => ({}),
  } as unknown as AxiosError
}

describe('parseProblemDetails', () => {
  it('нормализует полный RFC 7807 ответ c fieldErrors в invalidParams', () => {
    const err = makeAxiosError(400, {
      status: 400,
      type: 'https://api.rutcampustrack.ru/problems/bad-request',
      title: 'Неверный запрос',
      detail: 'lessonId обязателен',
      instance: '/api/attendance/checkins',
      timestamp: '2026-04-21T10:00:00Z',
      traceId: 'abc123def456',
      fieldErrors: [
        { field: 'lessonId', rejectedValue: null, message: 'Поле не может быть пустым' },
      ],
    })

    const p = parseProblemDetails(err)

    expect(p.status).toBe(400)
    expect(p.title).toBe('Неверный запрос')
    expect(p.detail).toBe('lessonId обязателен')
    expect(p.traceId).toBe('abc123def456')
    expect(p.invalidParams).toEqual([
      { field: 'lessonId', rejectedValue: null, message: 'Поле не может быть пустым' },
    ])
  })

  it('принимает post-M11 invalidParams shape без переименования', () => {
    const err = makeAxiosError(400, {
      status: 400,
      type: 'about:blank',
      title: 'Bad',
      detail: 'x',
      invalidParams: [
        { field: 'name', message: 'required' },
      ],
    })

    const p = parseProblemDetails(err)
    expect(p.invalidParams).toEqual([
      { field: 'name', rejectedValue: undefined, message: 'required' },
    ])
  })

  it('синтезирует fallback для не-RFC тел (plain text HTML от 502)', () => {
    const err = makeAxiosError(502, '<html>Bad Gateway</html>')
    const p = parseProblemDetails(err)
    expect(p.status).toBe(502)
    expect(p.title).toBe('Сервер недоступен')
    expect(p.invalidParams).toEqual([])
  })

  it('синтезирует fallback для пустого response body', () => {
    const err = makeAxiosError(500, {})
    const p = parseProblemDetails(err)
    expect(p.status).toBe(500)
    expect(p.title).toBe('Ошибка сервера')
  })

  it('network error (без response) даёт offline-friendly сообщение', () => {
    const networkErr = {
      isAxiosError: true,
      name: 'AxiosError',
      message: 'Network Error',
      response: undefined,
      config: {} as never,
      toJSON: () => ({}),
    } as unknown as AxiosError
    const p = parseProblemDetails(networkErr)
    expect(p.status).toBe(0)
    expect(p.title).toBe('Нет соединения')
  })

  it('не-axios ошибка (raw Error) даёт generic shape', () => {
    const p = parseProblemDetails(new Error('oops'))
    expect(p.status).toBe(0)
    expect(p.title).toBe('Ошибка')
    expect(p.detail).toBe('oops')
  })

  it('игнорирует мусорные fieldErrors (non-array, null items)', () => {
    const err = makeAxiosError(400, {
      title: 'x',
      detail: 'y',
      fieldErrors: [null, { message: 'm' }, undefined],
    })
    const p = parseProblemDetails(err)
    expect(p.invalidParams.length).toBe(1)
    expect(p.invalidParams[0]).toEqual({ field: '', rejectedValue: undefined, message: 'm' })
  })
})

describe('attachProblemDetails / getProblemDetails', () => {
  it('возвращает закешированный ProblemDetails без повторного парсинга', () => {
    const err = makeAxiosError(404, { title: 'Ресурс не найден', detail: 'id=5' })
    const cached = parseProblemDetails(err)
    attachProblemDetails(err, cached)

    const retrieved = getProblemDetails(err)
    expect(retrieved).toBe(cached)
  })

  it('парсит заново если attach не вызывался', () => {
    const err = makeAxiosError(403, { title: 'Нет прав', detail: 'forbidden' })
    const p = getProblemDetails(err)
    expect(p.title).toBe('Нет прав')
  })
})
