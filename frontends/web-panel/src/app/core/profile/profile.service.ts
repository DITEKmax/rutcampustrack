import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import type { UserResponse } from '../../api/schema';

/**
 * Profile reflects the academic-service `/me` response (BUG-004 / BUG-006).
 * Kept separate from AuthService.currentUser (parsed from JWT) because we need
 * server-side fields like avatarId and full name for the profile dialog.
 *
 * M07 G3b (D3): тип привязан к generated UserResponse + добавляет avatarId,
 * который бекенд отдаёт, но generated types помечают как optional — у /me
 * оно всегда null или строка, поэтому узким Strict здесь не нужен.
 */
export type ProfileResponse = UserResponse & {
  avatarId: string | null;
};

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);

  private readonly _profile = signal<ProfileResponse | null>(null);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);

  readonly profile = this._profile.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  readonly displayName = computed(() => {
    const p = this._profile();
    if (!p) return '';
    return [p.lastName, p.firstName].filter(Boolean).join(' ');
  });

  readonly initials = computed(() => {
    const p = this._profile();
    if (!p) return '';
    const ln = (p.lastName?.[0] ?? '').toUpperCase();
    const fn = (p.firstName?.[0] ?? '').toUpperCase();
    return (ln + fn) || '?';
  });

  load(force = false): void {
    if (this._loading()) return;
    if (this._profile() && !force) return;
    this._loading.set(true);
    this._error.set(null);
    this.http.get<ProfileResponse>('/api/academic/users/me').subscribe({
      next: data => {
        this._profile.set(data);
        this._loading.set(false);
      },
      error: () => {
        this._error.set('Не удалось загрузить профиль.');
        this._loading.set(false);
      },
    });
  }

  updateAvatar(avatarId: string | null): Observable<ProfileResponse> {
    return this.http
      .patch<ProfileResponse>('/api/academic/users/me/avatar', { avatarId })
      .pipe(tap(p => this._profile.set(p)));
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/auth/change-password', {
      currentPassword,
      newPassword,
    });
  }

  /** Wipe cached profile (called from logout). */
  clear(): void {
    this._profile.set(null);
  }
}
