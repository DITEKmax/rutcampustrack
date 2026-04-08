import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Teacher dashboard landing.
 *
 * The teacher role is read-only, so the dashboard acts as a welcome screen
 * with quick links into the journal and statistics pages plus a live clock.
 * No data fetching — real numbers live behind the journal / stats routes.
 */
@Component({
  selector: 'app-teacher-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './teacher-dashboard.component.html',
  styleUrl: './teacher-dashboard.component.css',
})
export class TeacherDashboardComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  private readonly _now = signal(new Date());
  readonly timeLabel = computed(() =>
    this._now().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' }),
  );
  readonly dateLabel = computed(() =>
    this._now().toLocaleDateString('ru-RU', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    }),
  );
  readonly greeting = computed(() => {
    const hour = this._now().getHours();
    if (hour < 6) return 'Доброй ночи';
    if (hour < 12) return 'Доброе утро';
    if (hour < 18) return 'Добрый день';
    return 'Добрый вечер';
  });

  ngOnInit(): void {
    const tick = setInterval(() => this._now.set(new Date()), 60_000);
    this.destroyRef.onDestroy(() => clearInterval(tick));
  }
}
