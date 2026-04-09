import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { LessonResponse } from '../../shared/student-schedule.types';

/**
 * Dashboard "current / next lesson" card.
 *
 * Visual language per Phase 51 UI-SPEC §Component 2 — card with
 * `--border-accent` + `--glow-primary` when the lesson is ACTIVE, plain
 * `--border-subtle` otherwise. The inline "Отметиться" link is rendered
 * only when the lesson is ACTIVE and routes to `/student/checkin`. When
 * no lesson is passed the empty variant renders "Сегодня пар нет".
 */
@Component({
  selector: 'app-next-lesson-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './next-lesson-card.component.html',
  styleUrl: './next-lesson-card.component.css',
})
export class NextLessonCardComponent {
  @Input() lesson: LessonResponse | null = null;
  @Input() subjectName: string = 'Предмет';

  get isActive(): boolean {
    return this.lesson?.status === 'ACTIVE';
  }

  get isPresent(): boolean {
    return this.lesson !== null;
  }

  get heading(): string {
    return this.isActive ? 'Текущая пара' : 'Следующая пара';
  }

  get startLabel(): string {
    return this.lesson ? this.lesson.startTime.slice(0, 5) : '';
  }

  get endLabel(): string {
    return this.lesson ? this.lesson.endTime.slice(0, 5) : '';
  }

  get timeLabel(): string {
    return this.isPresent ? `${this.startLabel}–${this.endLabel}` : '';
  }
}
