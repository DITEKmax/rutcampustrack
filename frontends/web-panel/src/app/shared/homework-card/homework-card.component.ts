import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCheckboxModule } from '@angular/material/checkbox';

/**
 * Minimal HomeworkLike view-model for the shared card component.
 *
 * The headman uses the backend `HomeworkResponse` shape (with `publishedBy`,
 * `lessonDate`, `lessonNumber`), while the student uses `HomeworkItem` from
 * `student-schedule.types.ts` (no lesson binding fields). Both shapes share
 * the five fields the card actually reads — this keeps the card feature-
 * agnostic.
 */
export interface HomeworkCardModel {
  id: number;
  title: string;
  description: string | null;
  link: string | null;
  subjectId: number;
  completed?: boolean;
}

/**
 * Shared homework card used by both `/headman/homework` (Phase 61) and
 * (eventually) `/student/homework` (Phase 61 PLAN-06 migration).
 *
 * Two variants are toggled by `@Input()` flags:
 * - `showEditActions` (headman + own ДЗ): renders edit/delete icon buttons
 * - `showCompleteCheckbox` (student only): renders MatCheckbox wired to
 *   `completeToggled` output
 *
 * Both can be false simultaneously (read-only student view of someone else's
 * ДЗ), but they are never both true — the headman page (D-10) never shows
 * per-student completion state.
 *
 * Subject name is rendered via an optional `@Input() subjectName` — the
 * parent resolves subjectId → name via SubjectCacheService and passes the
 * string in. The card never talks to the backend itself.
 */
@Component({
  selector: 'app-homework-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatCheckboxModule],
  template: `
    <div
      class="hw-card"
      [class.hw-card--completed]="homework.completed"
      role="listitem"
      [attr.aria-label]="'Задание «' + homework.title + '»'">

      @if (showCompleteCheckbox) {
        <mat-checkbox
          class="hw-card__checkbox"
          [checked]="!!homework.completed"
          [disabled]="pending"
          (change)="onToggleComplete()"
          [attr.aria-label]="toggleLabel()">
        </mat-checkbox>
      }

      <div class="hw-card__body">
        <div class="hw-card__header">
          <span class="hw-card__title">{{ homework.title }}</span>
          @if (homework.link) {
            <a
              class="hw-card__link"
              [href]="homework.link"
              target="_blank"
              rel="noopener"
              aria-label="Открыть материалы к заданию (новая вкладка)">
              <i class="ph ph-arrow-square-out" aria-hidden="true"></i>
            </a>
          }
        </div>

        @if (subjectName) {
          <div class="hw-card__subject">
            <i class="ph ph-book-open" aria-hidden="true"></i>
            <span>{{ subjectName }}</span>
          </div>
        }

        @if (homework.description) {
          <p class="hw-card__description">{{ homework.description }}</p>
        }
      </div>

      @if (showEditActions) {
        <div class="hw-card__actions">
          <button
            type="button"
            class="hw-card__icon-btn"
            (click)="edit.emit(homework)"
            [attr.aria-label]="'Редактировать «' + homework.title + '»'">
            <i class="ph ph-pencil" aria-hidden="true"></i>
          </button>
          <button
            type="button"
            class="hw-card__icon-btn hw-card__icon-btn--danger"
            (click)="delete.emit(homework)"
            [attr.aria-label]="'Удалить «' + homework.title + '»'">
            <i class="ph ph-trash" aria-hidden="true"></i>
          </button>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .hw-card {
      display: flex;
      gap: var(--space-2, 8px);
      background: var(--bg-secondary, #fafafa);
      border: 1px solid var(--border-subtle, #e0e0e0);
      border-left: 3px solid var(--accent-secondary, #1976d2);
      border-radius: var(--radius-lg, 12px);
      padding: var(--space-3, 12px) var(--space-4, 16px);
      align-items: flex-start;
      transition: opacity 150ms ease;
    }
    .hw-card--completed {
      opacity: 0.55;
      border-left-color: var(--border-subtle, #d0d0d0);
    }
    .hw-card--completed .hw-card__title { text-decoration: line-through; }
    .hw-card__checkbox { flex-shrink: 0; }
    .hw-card__body { flex: 1; min-width: 0; }
    .hw-card__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    .hw-card__title {
      font-weight: 600;
      font-size: 0.95rem;
      line-height: 1.35;
      color: var(--text-primary);
    }
    .hw-card__link {
      color: var(--accent-secondary, #1976d2);
      min-width: 32px; min-height: 32px;
      display: inline-flex; align-items: center; justify-content: center;
    }
    .hw-card__subject {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 0.78rem;
      color: var(--accent-secondary, #1976d2);
      margin-top: 4px;
    }
    .hw-card__description {
      margin: 6px 0 0;
      font-size: 0.85rem;
      line-height: 1.4;
      color: var(--text-secondary, #555);
      white-space: pre-wrap;
    }
    .hw-card__actions {
      display: flex;
      gap: 4px;
      flex-shrink: 0;
    }
    .hw-card__icon-btn {
      width: 32px; height: 32px;
      display: inline-flex; align-items: center; justify-content: center;
      background: transparent;
      border: 1px solid var(--border-subtle, #d0d0d0);
      border-radius: var(--radius-md, 8px);
      color: var(--text-secondary, #555);
      cursor: pointer;
      transition: background 150ms ease, color 150ms ease, border-color 150ms ease;
    }
    .hw-card__icon-btn:hover {
      background: var(--bg-hover, #eee);
      color: var(--text-primary);
    }
    .hw-card__icon-btn--danger:hover {
      background: #ffebee;
      color: #c62828;
      border-color: #ef9a9a;
    }
  `],
})
export class HomeworkCardComponent {
  @Input({ required: true }) homework!: HomeworkCardModel;
  @Input() subjectName: string | null = null;
  @Input() showEditActions = false;
  @Input() showCompleteCheckbox = false;
  @Input() pending = false;

  @Output() edit = new EventEmitter<HomeworkCardModel>();
  @Output() delete = new EventEmitter<HomeworkCardModel>();
  @Output() completeToggled = new EventEmitter<{
    homework: HomeworkCardModel;
    completed: boolean;
  }>();

  toggleLabel(): string {
    return this.homework.completed
      ? `Снять отметку «выполнено» с задания «${this.homework.title}»`
      : `Отметить задание «${this.homework.title}» выполненным`;
  }

  onToggleComplete(): void {
    this.completeToggled.emit({
      homework: this.homework,
      completed: !this.homework.completed,
    });
  }
}
