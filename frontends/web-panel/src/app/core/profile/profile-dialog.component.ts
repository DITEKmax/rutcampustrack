import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';

import { AvatarComponent } from './avatar.component';
import { AVATAR_PRESETS } from './preset-avatars';
import { ProfileService } from './profile.service';

/**
 * BUG-004: модалка профиля.
 * Слева — большой аватар + ФИО + роль; справа (или ниже) — выбор пресета и
 * форма смены пароля. OTP-flow отложен до проверки PWA (см. план фазы B).
 */
@Component({
  selector: 'app-profile-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, MatDialogModule, AvatarComponent],
  templateUrl: './profile-dialog.component.html',
  styleUrl: './profile-dialog.component.css',
})
export class ProfileDialogComponent {
  private readonly profileService = inject(ProfileService);
  private readonly dialogRef = inject(MatDialogRef<ProfileDialogComponent>);
  private readonly fb = inject(FormBuilder);

  readonly presets = AVATAR_PRESETS;
  readonly profile = this.profileService.profile;
  readonly initials = this.profileService.initials;
  readonly displayName = this.profileService.displayName;

  readonly selectedAvatarId = signal<string | null>(this.profile()?.avatarId ?? null);
  readonly avatarSaving = signal(false);
  readonly avatarError = signal<string | null>(null);
  readonly avatarSaved = signal(false);

  readonly showCurrent = signal(false);
  readonly showNew = signal(false);
  readonly passwordSaving = signal(false);
  readonly passwordError = signal<string | null>(null);
  readonly passwordSaved = signal(false);

  readonly form = this.fb.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: [
      '',
      [
        Validators.required,
        Validators.minLength(8),
        Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/),
      ],
    ],
  });

  readonly passwordValue = signal('');

  readonly hintMin = computed(() => this.passwordValue().length >= 8);
  readonly hintUpper = computed(() => /[A-Z]/.test(this.passwordValue()));
  readonly hintDigit = computed(() => /\d/.test(this.passwordValue()));

  constructor() {
    this.profileService.load();
    this.form.controls.newPassword.valueChanges.subscribe(v => this.passwordValue.set(v ?? ''));
  }

  selectPreset(id: string | null): void {
    if (this.avatarSaving()) return;
    this.selectedAvatarId.set(id);
    this.avatarSaved.set(false);
    this.avatarError.set(null);
  }

  saveAvatar(): void {
    if (this.avatarSaving()) return;
    this.avatarSaving.set(true);
    this.avatarError.set(null);
    this.profileService.updateAvatar(this.selectedAvatarId()).subscribe({
      next: () => {
        this.avatarSaving.set(false);
        this.avatarSaved.set(true);
      },
      error: () => {
        this.avatarSaving.set(false);
        this.avatarError.set('Не удалось сохранить аватар.');
      },
    });
  }

  toggleCurrent(): void { this.showCurrent.update(v => !v); }
  toggleNew(): void { this.showNew.update(v => !v); }

  changePassword(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.passwordSaving()) return;
    this.passwordSaving.set(true);
    this.passwordError.set(null);
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.profileService.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.passwordSaving.set(false);
        this.passwordSaved.set(true);
        this.form.reset();
        this.passwordValue.set('');
      },
      error: err => {
        this.passwordSaving.set(false);
        this.passwordError.set(err?.status === 400 || err?.status === 401
          ? 'Неверный текущий пароль.'
          : 'Не удалось сменить пароль. Попробуйте позже.');
      },
    });
  }

  close(): void {
    this.dialogRef.close();
  }
}
