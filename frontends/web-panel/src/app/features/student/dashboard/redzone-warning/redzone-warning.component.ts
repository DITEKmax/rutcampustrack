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
  @Input() subjectType: string | null | undefined;
  @Input({ required: true }) percentage!: number;

  get percentLabel(): string {
    return `${Math.round(this.percentage)}%`;
  }

  get subjectTypeLabel(): string {
    switch ((this.subjectType ?? '').trim().toLowerCase()) {
      case 'lecture':
        return '\u041b\u041a';
      case 'practice':
        return '\u041f\u0417';
      case 'lab':
        return '\u041b\u0417';
      default:
        return '';
    }
  }
}
