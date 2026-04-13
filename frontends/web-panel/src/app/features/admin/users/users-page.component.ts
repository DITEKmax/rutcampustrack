import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { AdminApiService } from '../shared/admin-api.service';
import type { GroupResponse, UserResponse } from '../shared/types';
import { RoleChipComponent } from '../shared/role-chip/role-chip.component';
import { StatusChipComponent } from '../shared/status-chip/status-chip.component';
import { UserDialogComponent } from './user-dialog/user-dialog.component';
import { ArchiveUserDialogComponent } from './archive-user-dialog/archive-user-dialog.component';

@Component({
  selector: 'app-users-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressBarModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatDialogModule,
    RoleChipComponent,
    StatusChipComponent,
  ],
  templateUrl: './users-page.component.html',
})
export class UsersPageComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly users = signal<UserResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly totalElements = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly groups = signal<GroupResponse[]>([]);

  readonly searchControl = new FormControl('');
  readonly roleFilter = signal<string>('');
  readonly statusFilter = signal<string>('');

  readonly displayedColumns = ['login', 'displayName', 'role', 'group', 'status', 'actions'];

  ngOnInit(): void {
    this.adminApi.listGroups().subscribe({
      next: groups => this.groups.set(groups),
    });

    // Pre-apply filters and trigger the create dialog from query params (BUG-005 dashboard links).
    const snapshot = this.route.snapshot.queryParamMap;
    const initialRole = snapshot.get('role');
    const initialStatus = snapshot.get('status');
    const initialAction = snapshot.get('action');
    if (initialRole) this.roleFilter.set(initialRole);
    if (initialStatus) this.statusFilter.set(initialStatus);

    this.loadUsers();

    if (initialAction === 'create') {
      // Defer to next microtask so the dialog opens after the page settles.
      queueMicrotask(() => {
        this.openCreateDialog(initialRole ?? undefined);
        // Strip the action param so refresh/back doesn't reopen the dialog.
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { action: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        });
      });
    }

    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.pageIndex.set(0);
        this.loadUsers();
      });
  }

  loadUsers(): void {
    this.loading.set(true);
    this.error.set(null);

    this.adminApi
      .listUsers({
        page: this.pageIndex(),
        size: this.pageSize(),
        role: this.roleFilter() || undefined,
        status: this.statusFilter() || undefined,
        search: this.searchControl.value || undefined,
      })
      .subscribe({
        next: result => {
          this.users.set(result.items);
          this.totalElements.set(result.total);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Не удалось загрузить пользователей.');
          this.loading.set(false);
        },
      });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadUsers();
  }

  onRoleFilterChange(role: string): void {
    this.roleFilter.set(role);
    this.pageIndex.set(0);
    this.loadUsers();
  }

  onStatusFilterChange(status: string): void {
    this.statusFilter.set(status);
    this.pageIndex.set(0);
    this.loadUsers();
  }

  getGroupName(groupId: number | null): string {
    if (groupId === null) return '—';
    return this.groups().find(g => g.id === groupId)?.name ?? '—';
  }

  openCreateDialog(presetRole?: string): void {
    const dialogRef = this.dialog.open(UserDialogComponent, {
      width: '520px',
      data: { mode: 'create' as const, groups: this.groups(), presetRole },
    });

    dialogRef.afterClosed().subscribe(result => {
      // result — это UserCreatedResponse (диалог закрывается через mat-dialog-close без аргумента
      // после показа экрана с паролем), поэтому перезагружаем всегда при любом закрытии кроме отмены
      this.loadUsers();
      if (result?.login) {
        this.snackBar.open(`Пользователь создан: ${result.login}`, undefined, {
          duration: 4000,
        });
      }
    });
  }

  openEditDialog(user: UserResponse): void {
    const dialogRef = this.dialog.open(UserDialogComponent, {
      width: '520px',
      data: { mode: 'edit' as const, user, groups: this.groups() },
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.snackBar.open('Данные пользователя обновлены.', undefined, {
          duration: 4000,
        });
        this.loadUsers();
      }
    });
  }

  archiveUser(user: UserResponse): void {
    const dialogRef = this.dialog.open(ArchiveUserDialogComponent, {
      width: '480px',
      data: { user },
    });

    dialogRef.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        this.adminApi.patchUser(user.id, { status: 'archived' }).subscribe({
          next: () => {
            this.snackBar.open('Пользователь архивирован.', undefined, {
              duration: 4000,
            });
            this.loadUsers();
          },
        });
      }
    });
  }

  restoreUser(user: UserResponse): void {
    this.adminApi.patchUser(user.id, { status: 'active' }).subscribe({
      next: () => {
        this.snackBar.open('Пользователь восстановлен.', undefined, {
          duration: 4000,
        });
        this.loadUsers();
      },
    });
  }
}
