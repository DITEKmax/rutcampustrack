import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Student check-in page (Phase 51 — Plan 03 fills this).
 *
 * Empty shell committed in Plan 01 so the route resolves immediately and
 * the other student page plans can run in parallel without colliding on
 * this file.
 */
@Component({
  selector: 'app-student-checkin',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section class="page-stack" data-testid="student-checkin-shell"></section>`,
  styles: [`:host { display: block; }`],
})
export class StudentCheckinComponent {}
