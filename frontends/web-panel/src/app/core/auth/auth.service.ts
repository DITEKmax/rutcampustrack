import { Injectable, Injector, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from './auth.api';
import { ProfileService } from '../profile/profile.service';

export interface AuthUser {
  id: number;
  role: 'TEACHER' | 'ADMIN' | 'STUDENT';
  isHeadman: boolean;
  groupId: number | null;
}

const STORAGE_KEY = 'rct.auth.v1';

interface PersistedTokens {
  accessToken: string;
  refreshToken: string;
}

function readPersisted(): PersistedTokens | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<PersistedTokens>;
    if (
      parsed &&
      typeof parsed.accessToken === 'string' &&
      typeof parsed.refreshToken === 'string'
    ) {
      return { accessToken: parsed.accessToken, refreshToken: parsed.refreshToken };
    }
    return null;
  } catch {
    return null;
  }
}

function writePersisted(tokens: PersistedTokens | null): void {
  try {
    if (tokens === null) {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
    }
  } catch {
    // Storage disabled (private mode / quota) — degrade to in-memory only.
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly injector = inject(Injector);
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

  constructor() {
    const persisted = readPersisted();
    if (persisted) {
      this._accessToken.set(persisted.accessToken);
      this._refreshToken.set(persisted.refreshToken);
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('storage', (event) => {
        if (event.key !== STORAGE_KEY) return;
        // Another tab wrote or cleared tokens — mirror into this tab's state
        // without re-persisting (would echo back and potentially race).
        if (!event.newValue) {
          this._accessToken.set(null);
          this._refreshToken.set(null);
          try {
            this.injector.get(ProfileService).clear();
          } catch {
            // ProfileService not available in this context
          }
          return;
        }
        try {
          const parsed = JSON.parse(event.newValue) as Partial<PersistedTokens>;
          if (
            typeof parsed.accessToken === 'string' &&
            typeof parsed.refreshToken === 'string'
          ) {
            this._accessToken.set(parsed.accessToken);
            this._refreshToken.set(parsed.refreshToken);
          }
        } catch {
          // Malformed write from another tab — ignore.
        }
      });
    }
  }

  setTokens(accessToken: string, refreshToken: string): void {
    this._accessToken.set(accessToken);
    this._refreshToken.set(refreshToken);
    writePersisted({ accessToken, refreshToken });
  }

  getRefreshToken(): string | null {
    return this._refreshToken();
  }

  clearTokens(): void {
    this._accessToken.set(null);
    this._refreshToken.set(null);
    writePersisted(null);
    // Lazy-resolve ProfileService so unit tests for AuthService/guards don't
    // need to provide HttpClient just to instantiate the auth graph.
    try {
      this.injector.get(ProfileService).clear();
    } catch {
      // ProfileService not provided in this test context — safe to ignore.
    }
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
