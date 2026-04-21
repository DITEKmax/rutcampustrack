import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { parseProblemDetails } from './problem-details';
import { ErrorToastService } from './error-toast.service';

/**
 * Response interceptor, который нормализует backend error в ProblemDetails
 * и показывает snackbar через ErrorToastService.
 *
 * Порядок: **после** `authInterceptor` — 401 обрабатывается auth'ом
 * (silent refresh), сюда попадают только «настоящие» error'ы.
 *
 * Suppress:
 * - 401 — auth-flow.
 * - Запросы с заголовком `X-Skip-Global-Error-Toast: 1` — feature явно
 *   обрабатывает ошибку локально (graceful degradation 403/404).
 */
export const problemDetailsInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ErrorToastService);
  const skipGlobal = req.headers.get('X-Skip-Global-Error-Toast') === '1';
  // Убираем служебный header до отправки на backend.
  const outgoing = skipGlobal
    ? req.clone({ headers: req.headers.delete('X-Skip-Global-Error-Toast') })
    : req;

  return next(outgoing).pipe(
    catchError((error) => {
      if (skipGlobal) return throwError(() => error);
      if (error?.status === 401) return throwError(() => error);

      const problem = parseProblemDetails(error);
      toast.show(problem);
      return throwError(() => error);
    }),
  );
};
