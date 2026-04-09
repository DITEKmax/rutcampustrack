import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Student dashboard (Phase 51 — Plan 04 fills this).
 *
 * Empty shell committed in Plan 01 so the route resolves immediately and
 * the other student page plans can run in parallel without colliding on
 * this file. Selector matches the naming convention of other dashboards.
 */
@Component({
  selector: 'app-student-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section class="dashboard page-stack" data-testid="student-dashboard-shell"></section>`,
  styles: [`:host { display: block; }`],
})
export class StudentDashboardComponent {}
