import { HttpEvent, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, switchMap, throwError } from 'rxjs';
import { AuthApi } from './auth.api';
import { AuthService } from './auth.service';

// Module-level state for the 401 retry queue pattern (adapted from PWA axios.ts)
let isRefreshing = false;
let pendingRequests: Array<{
  resolve: (token: string) => void;
  reject: (err: unknown) => void;
}> = [];

/** Reset interceptor state between tests to avoid cross-test contamination */
export function resetInterceptorState(): void {
  isRefreshing = false;
  pendingRequests = [];
}

const AUTH_ENDPOINTS = ['/api/auth/login', '/api/auth/refresh', '/api/auth/logout'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const authApi = inject(AuthApi);
  const router = inject(Router);

  const isAuthEndpoint = AUTH_ENDPOINTS.some((ep) => req.url.includes(ep));
  const token = authService.accessToken();

  const authedReq =
    token && !isAuthEndpoint
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(authedReq).pipe(
    catchError((error) => {
      // Only handle 401 for non-auth endpoints
      if (error.status !== 401 || isAuthEndpoint) {
        return throwError(() => error);
      }

      if (isRefreshing) {
        // Queue this request until refresh completes
        return new Observable<HttpEvent<unknown>>((observer) => {
          pendingRequests.push({
            resolve: (newToken) => {
              const retried = req.clone({
                setHeaders: { Authorization: `Bearer ${newToken}` },
              });
              next(retried).subscribe(observer);
            },
            reject: (err) => observer.error(err),
          });
        });
      }

      isRefreshing = true;
      const refreshToken = authService.getRefreshToken();

      if (!refreshToken) {
        isRefreshing = false;
        authService.clearTokens();
        router.navigate(['/login']);
        return throwError(() => error);
      }

      return authApi.refresh(refreshToken).pipe(
        switchMap((tokens) => {
          authService.setTokens(tokens.accessToken, tokens.refreshToken);
          isRefreshing = false;
          pendingRequests.forEach((p) => p.resolve(tokens.accessToken));
          pendingRequests = [];
          const retried = req.clone({
            setHeaders: { Authorization: `Bearer ${tokens.accessToken}` },
          });
          return next(retried);
        }),
        catchError((refreshError) => {
          isRefreshing = false;
          pendingRequests.forEach((p) => p.reject(refreshError));
          pendingRequests = [];
          authService.clearTokens();
          router.navigate(['/login']);
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
