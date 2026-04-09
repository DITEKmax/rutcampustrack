import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { trigger, transition, style, animate } from '@angular/animations';
import { switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { StudentApiService } from '../shared/student-api.service';
import type { HomeworkItem } from '../shared/student-schedule.types';
import { HomeworkItemComponent } from './homework-item/homework-item.component';

/**
 * Student homework page — `/student/homework`.
 *
 * Delivers STU-WEB-04: Students can view all homework for their group and mark
 * items complete/incomplete with instant feedback and server-side persistence.
 *
 * Loading flow:
 * 1. getActiveSemesterId() → then getHomeworks(groupId, semesterId)
 * 2. While in flight shows 4 skeleton rows (80px each)
 * 3. Incomplete items sorted by createdAt desc appear first; completed last
 *
 * Optimistic toggle:
 * - Immediately flips item.completed in the local signal
 * - Calls markHomeworkComplete or unmarkHomeworkComplete
 * - On error: reverts local state + sets itemErrors[id] for inline toast
 */
@Component({
  selector: 'app-student-homework',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, HomeworkItemComponent],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('200ms var(--ease-out, ease-out)', style({ opacity: 1, transform: 'translateY(0)' })),
      ]),
    ]),
  ],
  templateUrl: './student-homework.component.html',
  styleUrl: './student-homework.component.css',
})
export class StudentHomeworkComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly apiService = inject(StudentApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly items = signal<HomeworkItem[]>([]);
  /** Map of itemId → boolean: true while API call in flight for that item */
  readonly pendingItems = signal<Record<number, boolean>>({});
  /** Map of itemId → error message when optimistic toggle failed */
  readonly itemErrors = signal<Record<number, string>>({});

  readonly incompleteItems = computed(() =>
    this.items()
      .filter(i => !i.completed)
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()),
  );
  readonly completeItems = computed(() =>
    this.items()
      .filter(i => i.completed)
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()),
  );

  ngOnInit(): void {
    const groupId = this.authService.currentUser()?.groupId;
    if (groupId == null) {
      this.error.set('Не удалось загрузить задания. Проверьте подключение и попробуйте ещё раз.');
      return;
    }
    this.loading.set(true);
    this.apiService
      .getActiveSemesterId()
      .pipe(
        switchMap(semesterId => {
          if (semesterId == null) return of([]);
          return this.apiService.getHomeworks(groupId, semesterId);
        }),
      )
      .subscribe({
        next: homeworks => {
          this.items.set(homeworks);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Не удалось загрузить задания. Проверьте подключение и попробуйте ещё раз.');
          this.loading.set(false);
        },
      });
  }

  onToggleComplete(itemId: number): void {
    const current = this.items().find(i => i.id === itemId);
    if (!current) return;

    // Clear previous error for this item
    this.itemErrors.update(e => ({ ...e, [itemId]: '' }));
    // Set pending
    this.pendingItems.update(p => ({ ...p, [itemId]: true }));
    // Optimistic flip
    this.items.update(list =>
      list.map(i => (i.id === itemId ? { ...i, completed: !i.completed } : i)),
    );

    const call$ = current.completed
      ? this.apiService.unmarkHomeworkComplete(itemId)
      : this.apiService.markHomeworkComplete(itemId);

    call$.subscribe({
      next: () => {
        this.pendingItems.update(p => ({ ...p, [itemId]: false }));
      },
      error: () => {
        // Revert optimistic change
        this.items.update(list =>
          list.map(i => (i.id === itemId ? { ...i, completed: current.completed } : i)),
        );
        this.pendingItems.update(p => ({ ...p, [itemId]: false }));
        this.itemErrors.update(e => ({
          ...e,
          [itemId]: 'Не удалось обновить статус. Попробуйте ещё раз.',
        }));
      },
    });
  }
}
