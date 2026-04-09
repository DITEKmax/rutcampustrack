import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * Amber banner surfacing a subject whose attendance is below the resolved
 * red-zone threshold. Visual contract per Phase 51 UI-SPEC §Component 2
 * (Red-zone warnings sub-component).
 *
 * Percentages are rounded to the nearest integer for display — the raw
 * decimal value is kept on the input so the consumer can still compare
 * `percentage < threshold` if needed.
 */
@Component({
  selector: 'app-redzone-warning',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './redzone-warning.component.html',
  styleUrl: './redzone-warning.component.css',
})
export class RedzoneWarningComponent {
  @Input({ required: true }) subjectName!: string;
  @Input({ required: true }) percentage!: number;

  get percentLabel(): string {
    return `${Math.round(this.percentage)}%`;
  }
}
