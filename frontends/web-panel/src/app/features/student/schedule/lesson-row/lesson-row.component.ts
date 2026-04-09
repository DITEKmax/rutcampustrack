import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { NgClass } from '@angular/common';
import type {
  AttendanceStatus,
  LessonResponse,
} from '../../shared/student-schedule.types';
import { formatLessonTime } from '../week-utils';

/**
 * Presentational lesson row — subway-rail layout per UI-SPEC §Component 3.
 *
 * Visual structure:
 *   [ start time ]          subject name                 [status chip]
 *   [    dot    ]           i Ауд. {room}
 *   [  end time ]
 *
 * Left rail (time + dot + time) mirrors the PWA `LessonCard` subway-rail
 * language. Right column holds subject name and meta row. A status chip
 * sits top-right of the body.
 *
 * Pure presentational: subject names are resolved by the parent via
 * `SubjectCacheService` and passed through `subjectName`. Click emits
 * `toggle(lesson.id)`; the parent decides what to do with expansion.
 * Cancelled lessons are visually muted and non-interactive (no toggle).
 */
@Component({
  selector: 'app-lesson-row',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgClass],
  templateUrl: './lesson-row.component.html',
  styleUrl: './lesson-row.component.css',
})
export class LessonRowComponent {
  @Input({ required: true }) lesson!: LessonResponse;
  @Input() subjectName = 'Предмет';
  @Input() personalStatus: AttendanceStatus | null = null;
  @Input() expanded = false;
  @Output() toggle = new EventEmitter<number>();

  get isActive(): boolean {
    return this.lesson.status === 'ACTIVE';
  }

  get isCancelled(): boolean {
    return this.lesson.status === 'CANCELLED';
  }

  get startLabel(): string {
    return formatLessonTime(this.lesson.startTime);
  }

  get endLabel(): string {
    return formatLessonTime(this.lesson.endTime);
  }

  get statusChipClass(): string {
    if (this.personalStatus) {
      return `status-chip status-chip--${this.personalStatus}`;
    }
    if (this.isActive) return 'status-chip status-chip--active';
    if (this.isCancelled) return 'status-chip status-chip--cancelled';
    return 'status-chip status-chip--planned';
  }

  get statusLabel(): string {
    if (this.personalStatus === 'present') return 'б';
    if (this.personalStatus === 'absent') return 'н';
    if (this.personalStatus === 'excused') return 'у';
    if (this.personalStatus === 'free_attendance') return 'сп';
    if (this.isActive) return 'Идёт';
    if (this.isCancelled) return 'Отменена';
    return 'Пара';
  }

  /**
   * Simple deterministic label — backend LessonResponse currently does not
   * expose a lesson-type field. Phase 52 may extend this to real type text
   * (лекция/практика/лаб. работа). For Phase 51 fall back to the lesson
   * number to give students a stable anchor in the detail panel.
   */
  get lessonTypeLabel(): string {
    return `Пара №${this.lesson.lessonNumber}`;
  }

  onClick(): void {
    if (this.isCancelled) return;
    this.toggle.emit(this.lesson.id);
  }
}
