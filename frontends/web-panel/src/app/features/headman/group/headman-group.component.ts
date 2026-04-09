import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { trigger, transition, style, animate } from '@angular/animations';
import { forkJoin } from 'rxjs';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';

import { HeadmanApiService } from '../shared/headman-api.service';
import { AuthService } from '../../../core/auth/auth.service';
import { AssignAssistantDialogComponent } from './assign-assistant-dialog.component';
import { DeleteAssistantDialogComponent } from './delete-assistant-dialog.component';

const PERMISSION_CHIP_LABELS: Record<string, string> = {
  excuse_approve: 'Одобрение пропусков',
  late_checkin_approve: 'Поздние отметки',
  subject_manage: 'Управление предметами',
  attendance_mark: 'Проставление посещаемости',
};

@Component({
  selector: 'app-headman-group',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    AssignAssistantDialogComponent,
    DeleteAssistantDialogComponent,
  ],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms var(--ease-out, ease-out)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  template: `
    <div class="page-stack" [@routeFade]>
      <!-- Page header -->
      <div class="page-header">
        <div>
          <span class="page-eyebrow">Старостат</span>
          <h1 class="page-title">Моя группа</h1>
        </div>
        <div class="page-header__actions">
          <button class="btn-brand" (click)="openAssignDialog()">
            <i class="ph ph-plus"></i> Назначить помощника
          </button>
        </div>
      </div>

      <!-- Section 1: Student list -->
      <div class="page-card page-card--flush">
        <div class="page-card__header">
          <h2>Студенты</h2>
        </div>

        @if (loading()) {
          <div class="skeleton-row" *ngFor="let i of [1,2,3,4,5]" aria-hidden="true"></div>
          <span aria-live="polite" class="sr-only">Загрузка...</span>
        }
        @else if (error()) {
          <div class="page-error"><i class="ph ph-warning-circle"></i>{{ error() }}</div>
        }
        @else if (students().length === 0) {
          <div class="page-empty">
            <i class="ph-duotone ph-users"></i>
            <h3>Группа пуста</h3>
            <p>В вашей группе пока нет студентов.</p>
          </div>
        }
        @else {
          <table mat-table [dataSource]="students()">
            <ng-container matColumnDef="student">
              <th mat-header-cell *matHeaderCellDef>Студент</th>
              <td mat-cell *matCellDef="let s">
                <span class="student-name">{{ s.fullName }}</span>
                <span class="student-login">{{ s.login }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Статус</th>
              <td mat-cell *matCellDef="let s">
                @if (assistantIds().has(s.id)) {
                  <span class="role-chip role-chip--headman-assistant">Помощник старосты</span>
                } @else {
                  <span class="role-chip role-chip--student">Студент</span>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="action">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let s"></td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="['student','status','action']"></tr>
            <tr mat-row *matRowDef="let row; columns: ['student','status','action']"></tr>
          </table>
        }
      </div>

      <!-- Section 2: Assistant list -->
      <div class="page-card">
        <div class="page-card__header">
          <h2>Помощники старосты</h2>
        </div>
        @if (assistants().length === 0 && !loading()) {
          <p class="assistant-empty">Помощники не назначены</p>
        }
        @for (a of assistants(); track a.id) {
          <div class="assistant-item">
            <i class="ph ph-user-circle"></i>
            <div class="assistant-info">
              <span class="assistant-name">{{ a.fullName }}</span>
              <span class="assistant-login">{{ a.login }}</span>
              <div class="permission-chips">
                @for (p of a.permissions; track p) {
                  <span class="permission-chip">{{ permissionLabel(p) }}</span>
                }
              </div>
            </div>
            <button class="icon-btn icon-btn--danger"
                    [attr.aria-label]="'Удалить помощника ' + a.fullName"
                    (click)="openDeleteDialog(a)">
              <i class="ph ph-trash"></i>
            </button>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .student-login {
      display: block;
      font-family: var(--font-mono);
      font-size: var(--text-xs);
      color: var(--text-muted);
    }

    .assistant-login {
      display: block;
      font-family: var(--font-mono);
      font-size: var(--text-xs);
      color: var(--text-muted);
    }

    .permission-chip {
      display: inline-flex;
      align-items: center;
      background: var(--bg-elevated);
      font-size: var(--text-xs);
      padding: 2px 8px;
      border-radius: var(--radius-full);
      margin-right: 4px;
      margin-top: 4px;
    }

    .icon-btn--danger:hover {
      color: var(--accent-danger);
    }

    .skeleton-row {
      height: 52px;
      background: var(--bg-elevated);
      border-radius: var(--radius-md);
      margin-bottom: 8px;
      animation: dashboard-shimmer 1.4s ease-in-out infinite;
    }

    @keyframes dashboard-shimmer {
      0%, 100% { opacity: 0.5; }
      50% { opacity: 1; }
    }

    .role-chip--headman-assistant {
      background: var(--accent-info-subtle, rgba(139,92,246,0.12));
      color: var(--accent-info);
      font-size: var(--text-xs);
      font-weight: 600;
      padding: 2px 8px;
      border-radius: var(--radius-full);
    }

    .assistant-item {
      display: flex;
      align-items: flex-start;
      gap: var(--space-4);
      padding: var(--space-4);
      border-bottom: 1px solid var(--border-subtle);
    }

    .assistant-item:last-of-type {
      border-bottom: none;
    }

    .assistant-info {
      flex: 1;
    }

    .assistant-name {
      display: block;
      font-weight: 600;
      font-size: var(--text-sm);
    }

    .permission-chips {
      display: flex;
      flex-wrap: wrap;
      margin-top: var(--space-1);
    }

    .assistant-empty {
      padding: var(--space-4);
      color: var(--text-muted);
      font-size: var(--text-sm);
    }

    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0,0,0,0);
      white-space: nowrap;
      border: 0;
    }
  `],
})
export class HeadmanGroupComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly students = signal<any[]>([]);
  readonly assistants = signal<any[]>([]);

  readonly assistantIds = computed(() => new Set(this.assistants().map((a: any) => a.studentId)));

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.error.set(null);
    const groupId = this.auth.currentUser()?.groupId;
    if (!groupId) {
      this.error.set('Не удалось загрузить список студентов. Попробуйте обновить страницу.');
      this.loading.set(false);
      return;
    }
    forkJoin([
      this.headmanApi.getGroupMembers(),
      this.headmanApi.listAssistants(groupId),
    ]).subscribe({
      next: ([membersResp, assistantsResp]) => {
        this.students.set(Object.values(membersResp?._embedded ?? {})[0] as any[] ?? []);
        this.assistants.set(Object.values(assistantsResp?._embedded ?? {})[0] as any[] ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить список студентов. Попробуйте обновить страницу.');
        this.loading.set(false);
      },
    });
  }

  openAssignDialog(): void {
    const ref = this.dialog.open(AssignAssistantDialogComponent, {
      width: '520px',
      maxWidth: '95vw',
      ariaLabel: 'Назначить помощника старосты',
      data: { students: this.students(), assistantIds: this.assistantIds() },
    });
    ref.afterClosed().subscribe(result => {
      if (result) { this.loadData(); }
    });
  }

  openDeleteDialog(assistant: any): void {
    const ref = this.dialog.open(DeleteAssistantDialogComponent, {
      data: { fullName: assistant.fullName },
      ariaLabel: 'Подтверждение удаления',
    });
    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.headmanApi.revokeAssistant(assistant.id).subscribe({
        next: () => {
          this.snackBar.open('Помощник удалён.', undefined, { duration: 4000 });
          this.loadData();
        },
        error: () => this.snackBar.open('Не удалось удалить помощника.', undefined, { duration: 6000 }),
      });
    });
  }

  permissionLabel(p: string): string {
    return PERMISSION_CHIP_LABELS[p] ?? p;
  }
}
