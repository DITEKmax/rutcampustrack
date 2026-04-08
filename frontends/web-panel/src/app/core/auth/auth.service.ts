import { Injectable, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from './auth.api';

export interface AuthUser {
  id: number;
  role: 'TEACHER' | 'ADMIN' | 'STUDENT';
  isHeadman: boolean;
  groupId: number | null;
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
        role: (payload.role as string).toUpperCase() as 'TEACHER' | 'ADMIN' | 'STUDENT',
        isHeadman: payload.is_headman === true,
        groupId: payload.group_id ?? null,
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

  /**
   * Single source of truth for post-login redirects (Phase 50, D-09).
   *
   * Used by login.component.ts after successful auth, by guestGuard
   * (when already-logged-in user hits /login), and by roleGuard fallback
   * (so denied access routes the user to *their own* dashboard, not a
   * hard-coded admin route).
   */
  resolveDashboardFor(user: AuthUser | null): string {
    if (!user) return '/login';
    if (user.role === 'ADMIN') return '/admin/dashboard';
    if (user.role === 'TEACHER') return '/teacher/dashboard';
    if (user.isHeadman) return '/headman/dashboard';
    return '/student/dashboard';
  }
}
