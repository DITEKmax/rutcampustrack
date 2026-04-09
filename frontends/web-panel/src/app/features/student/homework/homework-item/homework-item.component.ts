import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCheckboxModule } from '@angular/material/checkbox';
import type { HomeworkItem } from '../../shared/student-schedule.types';

/**
 * Individual homework assignment card with optimistic completion toggle.
 *
 * Renders a single HomeworkItem with:
 * - MatCheckbox for completion toggle (disabled while API call in flight)
 * - Subject chip (subjectId display)
 * - Optional description (clamped to 2 lines)
 * - Optional external link with rel="noopener"
 * - Completed visual state: opacity 0.55 + line-through title
 *
 * Accessibility:
 * - role="listitem" on host container
 * - aria-busy="true" on host while pending=true
 * - checkbox aria-label varies by completed state
 * - external link aria-label in Russian
 */
@Component({
  selector: 'app-homework-item',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatCheckboxModule],
  template: `
    <div
      class="homework-item"
      [class.is-completed]="item.completed"
      [class.is-incomplete]="!item.completed"
      role="listitem"
      [attr.aria-busy]="pending || null">

      <!-- Checkbox -->
      <mat-checkbox
        [checked]="item.completed"
        [disabled]="pending"
        (change)="onToggle()"
        [attr.aria-label]="toggleAriaLabel">
      </mat-checkbox>

      <!-- Content -->
      <div class="homework-item__content">
        <div class="homework-item__header">
          <span class="homework-item__title">{{ item.title }}</span>
          @if (item.link) {
            <a
              [href]="item.link"
              target="_blank"
              rel="noopener"
              class="homework-item__link"
              aria-label="Открыть материалы к заданию (новая вкладка)">
              <i class="ph-arrow-square-out ph-fill"></i>
            </a>
          }
        </div>
        <div class="homework-item__subject">
          <i class="ph-book-open ph-fill"></i>
          <span>{{ item.subjectId }}</span>
        </div>
        @if (item.description) {
          <p class="homework-item__description">{{ item.description }}</p>
        }
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .homework-item {
      display: flex;
      gap: var(--space-2);
      background: var(--bg-secondary);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-lg);
      padding: var(--space-4);
      border-left: 3px solid var(--border-default);
      transition: opacity 150ms var(--ease-out);
    }
    .homework-item.is-completed {
      opacity: 0.55;
      border-left-color: var(--border-subtle);
    }
    .homework-item.is-completed .homework-item__title {
      text-decoration: line-through;
    }
    .homework-item__content { flex: 1; min-width: 0; }
    .homework-item__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-2);
    }
    .homework-item__title {
      font-size: var(--text-base);
      font-family: var(--font-heading);
      font-weight: 600;
      line-height: var(--leading-heading);
    }
    .homework-item__link {
      color: var(--accent-secondary);
      flex-shrink: 0;
      min-width: 44px;
      min-height: 44px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .homework-item__subject {
      display: flex;
      align-items: center;
      gap: var(--space-1);
      margin-top: var(--space-1);
      font-size: var(--text-xs);
      color: var(--accent-secondary);
      background: color-mix(in oklab, var(--accent-secondary) 10%, transparent);
      border: 1px solid color-mix(in oklab, var(--accent-secondary) 20%, transparent);
      border-radius: var(--radius-full);
      padding: 2px 8px;
      width: fit-content;
    }
    .homework-item__description {
      font-size: var(--text-sm);
      line-height: var(--leading-body);
      color: var(--text-secondary);
      margin-top: var(--space-1);
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
  `],
})
export class HomeworkItemComponent {
  @Input({ required: true }) item!: HomeworkItem;
  @Input() pending = false;
  @Output() toggleComplete = new EventEmitter<number>();

  get toggleAriaLabel(): string {
    return this.item.completed
      ? `Снять отметку с задания "${this.item.title}"`
      : `Отметить задание "${this.item.title}" выполненным`;
  }

  onToggle(): void {
    this.toggleComplete.emit(this.item.id);
  }
}
