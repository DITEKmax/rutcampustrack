import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AdminApiService } from '../../shared/admin-api.service';
import { fullName, type GroupResponse, type UserResponse } from '../../shared/types';

/**
 * BUG-006-6 / plan 58-08: read-only страница истории архивной группы.
 *
 * Защита от отображения активной группы (T-58-08-03): на ngOnInit сверяем
 * `active=false`. Если группа активна — редиректим обратно на /admin/groups со snackbar.
 * Если 404 — показываем "Группа не найдена".
 */
@Component({
  selector: 'app-group-history-page',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule],
  templateUrl: './group-history-page.component.html',
  styleUrls: ['./group-history-page.component.scss'],
})
export class GroupHistoryPageComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading = signal(true);
  readonly group = signal<GroupResponse | null>(null);
  readonly students = signal<UserResponse[]>([]);
  readonly notFound = signal(false);

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = idParam ? Number(idParam) : NaN;
    if (!Number.isFinite(id)) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }

    forkJoin({
      group: this.api.getGroup(id).pipe(catchError(() => of(null))),
      students: this.api.listUsers({ role: 'student', size: 500 }).pipe(
        catchError(() => of({ items: [] as UserResponse[], total: 0 })),
      ),
    }).subscribe(({ group, students }) => {
      if (!group) {
        this.notFound.set(true);
        this.loading.set(false);
        return;
      }
      if (group.active) {
        this.snackBar.open(
          'Эта группа активна, история недоступна.',
          undefined,
          { duration: 5000 },
        );
        this.router.navigate(['/admin/groups']);
        return;
      }
      this.group.set(group);
      this.students.set(students.items.filter(u => u.groupId === group.id));
      this.loading.set(false);
    });
  }

  /** Год выпуска извлекается из суффикса "(выпуск YYYY)" или archivedAt. */
  graduationYear(): number | null {
    const g = this.group();
    if (!g) return null;
    const match = /\(выпуск (\d{4})\)/.exec(g.name);
    if (match) return Number(match[1]);
    if (g.archivedAt) return new Date(g.archivedAt).getFullYear();
    return null;
  }

  /** Отображаемое имя без суффикса "(выпуск YYYY)" — для отдельного заголовка. */
  activeName(): string {
    const g = this.group();
    if (!g) return '';
    return g.name.replace(/ \(выпуск \d{4}\)$/, '');
  }

  displayName(u: UserResponse): string {
    return fullName(u);
  }
}
