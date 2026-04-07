import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private http = inject(HttpClient);

  login(credentials: { login: string; password: string }): Observable<TokenResponse> {
    return this.http.post<TokenResponse>('/api/auth/login', credentials);
  }

  refresh(refreshToken: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>('/api/auth/refresh', { refreshToken });
  }

  logout(refreshToken: string): Observable<void> {
    return this.http.post<void>('/api/auth/logout', { refreshToken });
  }
}
