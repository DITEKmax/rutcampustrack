import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Generic segmented control (pill-style switcher) for Angular web-panel.
 *
 * Phase 61-06 / D-09: First shared/ component of its kind in the web-panel.
 * Used by StudentHomeworkComponent to switch between День / Неделя / Месяц.
 *
 * Accessibility:
 * - role="radiogroup" on the container
 * - role="radio" + aria-checked on each button
 *
 * Usage:
 * ```html
 * <app-segmented-control
 *   [options]="modeOptions"
 *   [value]="mode()"
 *   (valueChange)="mode.set($event)">
 * </app-segmented-control>
 * ```
 */
@Component({
  selector: 'app-segmented-control',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  templateUrl: './segmented-control.component.html',
  styleUrls: ['./segmented-control.component.css'],
})
export class SegmentedControlComponent<T> {
  @Input({ required: true }) options!: { value: T; label: string; icon?: string }[];
  @Input({ required: true }) value!: T;
  @Output() valueChange = new EventEmitter<T>();

  select(v: T): void {
    if (v !== this.value) {
      this.valueChange.emit(v);
    }
  }
}
