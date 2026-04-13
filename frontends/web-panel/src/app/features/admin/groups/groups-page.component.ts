// WPAN-13: Headman assistant management is NOT implemented in this phase.
// Reason: AssistantController on the backend requires @RequireRole(STUDENT) — admin users
// receive 403 Forbidden when calling assistant endpoints. The UI flow can be added once
// the backend is updated to allow ADMIN role access to assistant management endpoints.

import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { forkJoin } from 'rxjs';

import { AdminApiService } from '../shared/admin-api.service';
import { fullName, type GroupResponse, type UserResponse } from '../shared/types';
import { GroupDialogComponent, GroupDialogData } from './group-dialog/group-dialog.component';
import { AssignHeadmanDialogComponent } from './assign-headman-dialog/assign-headman-dialog.component';
import { RevokeHeadmanDialogComponent } from './revoke-headman-dialog/revoke-headman-dialog.component';

@Component({
  selector: 'app-groups-page',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './groups-page.component.html',
})
export class GroupsPageComponent implements OnInit {
  private readonly adminApi = inject(AdminApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  groups = signal<GroupResponse[]>([]);
  allStudents = signal<UserResponse[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  displayedColumns = ['name', 'headman', 'studentCount', 'actions'];

  ngOnInit(): void {
    this.reload();

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

    forkJoin({
      groups: this.adminApi.listGroups(),
      students: this.adminApi.listUsers({ role: 'student', size: 500 }),
    }).subscribe({
      next: ({ groups, students }) => {
        this.groups.set(groups);
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
