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
  @Input() personalSource: string | null = null;
  @Input() expanded = false;
  @Input() lateCheckinSent = false;
  @Input() lateCheckinPending = false;
  @Input() lateCheckinError: string | null = null;
  @Output() toggle = new EventEmitter<number>();
  @Output() requestExcuse = new EventEmitter<number>();
  @Output() requestLateCheckin = new EventEmitter<number>();

  get isActive(): boolean {
    return this.lesson.status === 'ACTIVE';
  }

  get isCancelled(): boolean {
    return this.lesson.status === 'CANCELLED';
  }

  /**
   * Colour bucket for the rail dot. Covers the 4 states the UX mapping asks
   * for: waiting-to-check-in (active, not yet marked) is blue; present is
   * green; absent is red; excused / free attendance is yellow. Everything
   * else (planned, cancelled, past without own status) is muted.
   */
  get dotStateClass(): string {
    if (this.personalStatus === 'present') return 'lesson-row__dot--present';
    if (this.personalStatus === 'absent') return 'lesson-row__dot--absent';
    if (
      this.personalStatus === 'excused' ||
      this.personalStatus === 'free_attendance'
    ) {
      return 'lesson-row__dot--excused';
    }
    if (this.isActive) return 'lesson-row__dot--active';
    return 'lesson-row__dot--muted';
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
    if (this.isCancelled) return 'status-chip status-chip--cancelled';
    return 'status-chip status-chip--planned';
  }

  get statusLabel(): string {
    if (this.personalStatus === 'present') {
      const src = (this.personalSource ?? '').toLowerCase();
      return src === 'late_checkin' ? '+' : 'б';
    }
    if (this.personalStatus === 'absent') return 'н';
    if (this.personalStatus === 'excused') return 'у';
    if (this.personalStatus === 'free_attendance') return 'сп';
    if (this.isCancelled) return 'Отменена';
    return 'Пара';
  }

  /** Excuse-ticket button is disabled for cancelled lessons. */
  get canRequestExcuse(): boolean {
    return !this.isCancelled;
  }

  /** Late-checkin button is available for any non-cancelled lesson not already marked present. */
  get canRequestLateCheckin(): boolean {
    return !this.isCancelled && this.personalStatus !== 'present';
  }

  onRequestExcuse(event: Event): void {
    event.stopPropagation();
    this.requestExcuse.emit(this.lesson.id);
  }

  onRequestLateCheckin(event: Event): void {
    event.stopPropagation();
    if (this.lateCheckinSent || this.lateCheckinPending) return;
    this.requestLateCheckin.emit(this.lesson.id);
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
