import { Injectable, Injector, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from './auth.api';
import { ProfileService } from '../profile/profile.service';
import { clearAllClientState } from './clear-all-client-state';

export interface AuthUser {
  id: number;
  role: 'TEACHER' | 'ADMIN' | 'STUDENT';
  isHeadman: boolean;
  groupId: number | null;
}

/**
 * M03b Группа 7: legacy localStorage key. Refresh token теперь в HttpOnly
 * cookie `rct_refresh`. Этот ключ удаляется migration helper'ом на старте.
 */
const LEGACY_STORAGE_KEY = 'rct.auth.v1';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly injector = inject(Injector);
  private readonly _accessToken = signal<string | null>(null);
  private readonly _isBootstrapping = signal<boolean>(true);

  readonly accessToken = this._accessToken.asReadonly();
  readonly isBootstrapping = this._isBootstrapping.asReadonly();
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

  constructor() {
    // Migration: удаляем legacy persisted tokens (refresh теперь в cookie).
    try {
      if (typeof localStorage !== 'undefined' && localStorage.getItem(LEGACY_STORAGE_KEY)) {
        localStorage.removeItem(LEGACY_STORAGE_KEY);
      }
    } catch {
      /* storage disabled */
    }

    if (typeof window !== 'undefined') {
      // Кросс-таб выход: если в другой вкладке localStorage.clear() вызвали
      // (например clearAllClientState), дублируем in-memory state здесь.
      window.addEventListener('storage', (event) => {
        if (event.key === null || event.key === LEGACY_STORAGE_KEY) {
          // Ранее здесь было восстановление токенов — убрано (refresh в cookie).
          // При full-clear в другой вкладке — логинаутимся и у себя.
          if (event.newValue === null && event.key === LEGACY_STORAGE_KEY) {
            this._accessToken.set(null);
            try {
              this.injector.get(ProfileService).clear();
            } catch {
              /* ProfileService not available */
            }
          }
        }
      });
    }
  }

  /**
   * Bootstrap-попытка восстановить сессию через cookie-based refresh.
   * Вызывается из APP_INITIALIZER — до того, как guards решают на какой
   * dashboard вести. Возвращает void.
   */
  async bootstrap(): Promise<void> {
    const authApi = this.injector.get(AuthApi);
    try {
      const tokens = await firstValueFrom(authApi.refresh());
      this._accessToken.set(tokens.accessToken);
    } catch {
      // 401 → no valid refresh cookie — остаёмся unauth'ed.
    } finally {
      this._isBootstrapping.set(false);
    }
  }

  setAccessToken(accessToken: string): void {
    this._accessToken.set(accessToken);
  }

  /** @deprecated M03b Группа 7 — use setAccessToken. refresh-token больше
   *  не отдаётся фронту (HttpOnly cookie), и второй аргумент игнорируется. */
  setTokens(accessToken: string, _refreshToken?: string): void {
    this._accessToken.set(accessToken);
  }

  clearTokens(): void {
    this._accessToken.set(null);
    try {
      this.injector.get(ProfileService).clear();
    } catch {
      // ProfileService not provided in this test context — safe to ignore.
    }
  }

  async logout(authApi: AuthApi, router: Router): Promise<void> {
    try {
      await firstValueFrom(authApi.logout());
    } catch {
      // Ignore logout errors — tokens are cleared regardless.
    }
    this.clearTokens();
    await clearAllClientState();
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
