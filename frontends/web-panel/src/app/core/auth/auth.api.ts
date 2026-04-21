import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import type { TokenResponse, WsTicketResponse } from '../../api/schema';

export type { TokenResponse, WsTicketResponse };

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private http = inject(HttpClient);

  /**
   * POST /auth/login — `withCredentials: true` обязательно, чтобы браузер
   * сохранил HttpOnly cookie `rct_refresh`. Access token — в body.
   */
  login(credentials: { login: string; password: string }): Observable<TokenResponse> {
    return this.http.post<TokenResponse>('/api/auth/login', credentials, {
      withCredentials: true,
    });
  }

  verifyOtpByCode(code: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>('/api/auth/otp/verify-by-code', { code }, {
      withCredentials: true,
    });
  }

  /**
   * POST /auth/refresh — без body (cookie автоматически летит через
   * `withCredentials`). Возвращает новый access + rotated cookie.
   */
  refresh(): Observable<TokenResponse> {
    return this.http.post<TokenResponse>('/api/auth/refresh', null, {
      withCredentials: true,
    });
  }

  /**
   * POST /auth/logout — revoke refresh token + clear cookie. Body опционально
   * для legacy TMA, но web-panel шлёт только cookie.
   */
  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', null, {
      withCredentials: true,
    });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/auth/change-password', { currentPassword, newPassword });
  }

  /**
   * POST /auth/ws-ticket — issue short-lived (30s, single-use) ticket для
   * WebSocket handshake. Защищён access-JWT (Bearer auto через authInterceptor).
   */
  acquireWsTicket(): Observable<WsTicketResponse> {
    return this.http.post<WsTicketResponse>('/api/auth/ws-ticket', null);
  }
}
