// WPAN-13: Headman assistant management is NOT implemented in this phase.
// Reason: AssistantController on the backend requires @RequireRole(STUDENT) — admin users
// receive 403 Forbidden when calling assistant endpoints. The UI flow can be added once
// the backend is updated to allow ADMIN role access to assistant management endpoints.

import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { debounceTime, distinctUntilChanged, forkJoin } from 'rxjs';

import { AdminApiService } from '../shared/admin-api.service';
import {
  fullName,
  type GroupResponse,
  type GroupStatus,
  type UserResponse,
} from '../shared/types';
import { GroupDialogComponent, GroupDialogData } from './group-dialog/group-dialog.component';
import { AssignHeadmanDialogComponent } from './assign-headman-dialog/assign-headman-dialog.component';
import { RevokeHeadmanDialogComponent } from './revoke-headman-dialog/revoke-headman-dialog.component';
import { DeleteGroupDialogComponent } from './delete-group-dialog/delete-group-dialog.component';
import { PromotionPreviewDialogComponent } from './promotion-preview-dialog/promotion-preview-dialog.component';

@Component({
  selector: 'app-groups-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatTabsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './groups-page.component.html',
  styleUrls: ['./groups-page.component.scss'],
})
export class GroupsPageComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** BUG-006-6 / plan 58-08: активный/архивный таб. */
  readonly status = signal<GroupStatus>('ACTIVE');
  readonly searchControl = new FormControl('');

  groups = signal<GroupResponse[]>([]);
  allStudents = signal<UserResponse[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  readonly activeColumns = ['name', 'headman', 'studentCount', 'actions'];
  readonly archivedColumns = ['name', 'archivedAt', 'actions'];

  /** Обратная совместимость со старыми тестами/шаблоном. */
  get displayedColumns(): string[] {
    return this.status() === 'ARCHIVED' ? this.archivedColumns : this.activeColumns;
  }

  ngOnInit(): void {
    this.reload();

    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload());

    // Open create dialog when navigated with ?action=create (BUG-005 dashboard quick action).
    if (this.route.snapshot.queryParamMap.get('action') === 'create') {
      queueMicrotask(() => {
        this.openCreateDialog();
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { action: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        });
      });
    }
  }

  onTabChange(index: number): void {
    this.status.set(index === 1 ? 'ARCHIVED' : 'ACTIVE');
    this.searchControl.setValue('', { emitEvent: false });
    this.reload();
  }

  groupHeadman(groupId: number): UserResponse | undefined {
    return this.allStudents().find(u => u.groupId === groupId && u.headman === true);
  }

  groupHeadmanName(groupId: number): string {
    const h = this.groupHeadman(groupId);
    return h ? fullName(h) : 'Не назначен';
  }

  groupStudentCount(groupId: number): number {
    return this.allStudents().filter(u => u.groupId === groupId).length;
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);

    const search = this.searchControl.value?.trim() || undefined;
    const status = this.status();

    forkJoin({
      groups: this.adminApi.listGroupsByStatus({ status, search, size: 200 }),
      students: this.adminApi.listUsers({ role: 'STUDENT', size: 500 }),
    }).subscribe({
      next: ({ groups, students }) => {
        this.groups.set(groups.items);
        this.allStudents.set(students.items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить данные.');
        this.loading.set(false);
      },
    });
  }

  openCreateDialog(): void {
    const ref = this.dialog.open(GroupDialogComponent, {
      maxWidth: '480px',
      width: '100%',
      data: { mode: 'create' } as GroupDialogData,
    });
    ref.afterClosed().subscribe(result => {
      if (result) {
        this.snackBar.open('Группа создана.', undefined, { duration: 4000 });
        this.reload();
      }
    });
  }

  openEditDialog(group: GroupResponse): void {
    const ref = this.dialog.open(GroupDialogComponent, {
      maxWidth: '480px',
      width: '100%',
      data: { mode: 'edit', group } as GroupDialogData,
    });
    ref.afterClosed().subscribe(result => {
      if (result) {
        this.snackBar.open('Группа обновлена.', undefined, { duration: 4000 });
        this.reload();
      }
    });
  }

  /** BUG-006-6 / plan 58-08: открыть модалку предпросмотра перевода групп. */
  openPromotionDialog(): void {
    const ref = this.dialog.open(PromotionPreviewDialogComponent, {
      maxWidth: '640px',
      width: '100%',
    });
    ref.afterClosed().subscribe(result => {
      if (result === 'done') {
        this.snackBar.open('Группы переведены.', undefined, { duration: 4000 });
        this.reload();
      }
    });
  }

  /** BUG-006-6 / plan 58-08: открыть read-only страницу истории архивной группы. */
  openHistory(group: GroupResponse): void {
    this.router.navigate(['/admin/groups', group.id, 'history']);
  }

  assignHeadman(group: GroupResponse): void {
    this.adminApi.listStudentsByGroup(group.id).subscribe(students => {
      const ref = this.dialog.open(AssignHeadmanDialogComponent, {
        maxWidth: '480px',
        width: '100%',
        data: { group, students },
      });
      ref.afterClosed().subscribe(userId => {
        if (userId) {
          this.adminApi.patchUser(userId, { isHeadman: true }).subscribe({
            next: () => {
              this.snackBar.open('Староста назначен.', undefined, { duration: 4000 });
              this.reload();
            },
            error: () => {
              this.snackBar.open('Не удалось назначить старосту.', undefined, { duration: 6000 });
            },
          });
        }
      });
    });
  }

  deleteGroup(group: GroupResponse): void {
    const studentCount = this.groupStudentCount(group.id);
    const ref = this.dialog.open(DeleteGroupDialogComponent, {
      maxWidth: '480px',
      width: '100%',
      data: { group, studentCount },
    });
    ref.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        this.adminApi.deleteGroup(group.id).subscribe({
          next: () => {
            this.snackBar.open('Группа удалена.', undefined, { duration: 4000 });
            this.reload();
          },
          error: err => {
            const msg = err?.error?.detail ?? 'Не удалось удалить группу.';
            this.snackBar.open(msg, undefined, { duration: 6000 });
          },
        });
      }
    });
  }

  revokeHeadman(group: GroupResponse): void {
    const headman = this.groupHeadman(group.id);
    if (!headman) return;

    const ref = this.dialog.open(RevokeHeadmanDialogComponent, {
      maxWidth: '480px',
      width: '100%',
      data: { group, headman },
    });
    ref.afterClosed().subscribe(confirmed => {
      if (confirmed) {
        this.adminApi.patchUser(headman.id, { isHeadman: false }).subscribe({
          next: () => {
            this.snackBar.open('Доступ старосты снят.', undefined, { duration: 4000 });
            this.reload();
          },
          error: () => {
            this.snackBar.open('Не удалось снять старосту.', undefined, { duration: 6000 });
          },
        });
      }
    });
  }
}
