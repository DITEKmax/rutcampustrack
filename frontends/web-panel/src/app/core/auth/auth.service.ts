import { Injectable, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from './auth.api';

export interface AuthUser {
  id: number;
  role: 'TEACHER' | 'ADMIN';
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _accessToken = signal<string | null>(null);
  private readonly _refreshToken = signal<string | null>(null);

  readonly accessToken = this._accessToken.asReadonly();
  readonly isAuthenticated = computed(() => this._accessToken() !== null);
  readonly currentUser = computed((): AuthUser | null => {
    const token = this._accessToken();
    if (!token) return null;
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      const payload = JSON.parse(atob(parts[1]));
      return {
        id: Number(payload.sub),
        role: (payload.role as string).toUpperCase() as 'TEACHER' | 'ADMIN',
      };
    } catch {
      return null;
    }
  });

  setTokens(accessToken: string, refreshToken: string): void {
    this._accessToken.set(accessToken);
    this._refreshToken.set(refreshToken);
  }

  getRefreshToken(): string | null {
    return this._refreshToken();
  }

  clearTokens(): void {
    this._accessToken.set(null);
    this._refreshToken.set(null);
  }

  async logout(authApi: AuthApi, router: Router): Promise<void> {
    const rt = this._refreshToken();
    if (rt) {
      try {
        await firstValueFrom(authApi.logout(rt));
      } catch {
        // Ignore logout errors — tokens are cleared regardless
      }
    }
    this.clearTokens();
    router.navigate(['/login']);
  }
}
