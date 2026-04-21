import { describe, it, expect } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { parseProblemDetails } from './problem-details';

describe('parseProblemDetails', () => {
  it('нормализует полный RFC 7807 ответ с fieldErrors → invalidParams', () => {
    const err = new HttpErrorResponse({
      status: 400,
      error: {
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
      },
    });

    const p = parseProblemDetails(err);

    expect(p.status).toBe(400);
    expect(p.title).toBe('Неверный запрос');
    expect(p.detail).toBe('lessonId обязателен');
    expect(p.traceId).toBe('abc123def456');
    expect(p.invalidParams).toEqual([
      { field: 'lessonId', rejectedValue: null, message: 'Поле не может быть пустым' },
    ]);
  });

  it('принимает post-M11 invalidParams shape без переименования', () => {
    const err = new HttpErrorResponse({
      status: 400,
      error: {
        title: 'Bad',
        detail: 'x',
        invalidParams: [{ field: 'name', message: 'required' }],
      },
    });

    const p = parseProblemDetails(err);
    expect(p.invalidParams).toEqual([
      { field: 'name', rejectedValue: undefined, message: 'required' },
    ]);
  });

  it('синтезирует fallback для plain-text 502', () => {
    const err = new HttpErrorResponse({
      status: 502,
      error: '<html>Bad Gateway</html>',
    });
    const p = parseProblemDetails(err);
    expect(p.status).toBe(502);
    expect(p.title).toBe('Сервер недоступен');
    expect(p.invalidParams).toEqual([]);
  });

  it('network error (status=0) даёт offline-friendly сообщение', () => {
    const err = new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') });
    const p = parseProblemDetails(err);
    expect(p.status).toBe(0);
    expect(p.title).toBe('Нет соединения');
  });

  it('не-HttpErrorResponse даёт generic shape', () => {
    const p = parseProblemDetails(new Error('oops'));
    expect(p.status).toBe(0);
    expect(p.title).toBe('Ошибка');
    expect(p.detail).toBe('oops');
  });

  it('обрабатывает тело без title/detail/type как non-RFC', () => {
    const err = new HttpErrorResponse({
      status: 500,
      error: { someOtherField: 'x' },
    });
    const p = parseProblemDetails(err);
    expect(p.title).toBe('Ошибка сервера');
    expect(p.invalidParams).toEqual([]);
  });

  it('игнорирует мусорные fieldErrors (null items)', () => {
    const err = new HttpErrorResponse({
      status: 400,
      error: {
        title: 'x',
        detail: 'y',
        fieldErrors: [null, { message: 'm' }, undefined],
      },
    });
    const p = parseProblemDetails(err);
    expect(p.invalidParams.length).toBe(1);
    expect(p.invalidParams[0]).toEqual({ field: '', rejectedValue: undefined, message: 'm' });
  });
});
