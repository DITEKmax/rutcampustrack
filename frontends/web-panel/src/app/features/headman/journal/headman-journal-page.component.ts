import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { trigger, transition, style, animate } from '@angular/animations';
import { catchError, finalize, of } from 'rxjs';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthService } from '../../../core/auth/auth.service';
import { HeadmanApiService } from '../shared/headman-api.service';
import { HeadmanJournalGridComponent } from './headman-journal-grid/headman-journal-grid.component';
import type { JournalResponse } from '../../teacher/journal/types';

@Component({
  selector: 'app-headman-journal-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressBarModule,
    HeadmanJournalGridComponent,
  ],
  animations: [
    trigger('routeFade', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate(
          '200ms cubic-bezier(0.16, 1, 0.3, 1)',
          style({ opacity: 1, transform: 'translateY(0)' }),
        ),
      ]),
    ]),
  ],
  host: { '[@routeFade]': '' },
  templateUrl: './headman-journal-page.component.html',
})
export class HeadmanJournalPageComponent implements OnInit {
  private readonly headmanApi = inject(HeadmanApiService);
  private readonly authService = inject(AuthService);

  readonly subjects = signal<{ id: number; name: string }[]>([]);
  readonly selectedSubjectId = signal<number | null>(null);

  readonly dateFrom = signal<string>(this.getFirstDayOfMonth());
  readonly dateTo = signal<string>(this.getToday());

  readonly journalData = signal<JournalResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  private getFirstDayOfMonth(): string {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().split('T')[0];
  }

  private getToday(): string {
    return new Date().toISOString().split('T')[0];
  }

  ngOnInit(): void {
    this.headmanApi
      .listSubjects(0, 100)
      .pipe(catchError(() => of(null)))
      .subscribe(res => {
        if (res?._embedded) {
          const key = Object.keys(res._embedded)[0];
          const raw: any[] = res._embedded[key] ?? [];
          this.subjects.set(raw.map((s: any) => ({ id: s.id, name: s.name })));
        }
      });
  }

  onSubjectChange(id: number): void {
    this.selectedSubjectId.set(id);
  }

  loadJournal(): void {
    const subjectId = this.selectedSubjectId();
    const groupId = this.authService.currentUser()?.groupId;
    if (!subjectId || !groupId) return;

    this.loading.set(true);
    this.error.set(null);

    this.headmanApi
      .getJournal(groupId, subjectId, this.dateFrom(), this.dateTo())
      .pipe(
        catchError(() => {
          this.error.set(
            'Не удалось загрузить данные. Обновите страницу или попробуйте позже.',
          );
          return of(null);
        }),
        finalize(() => this.loading.set(false)),
      )
      .subscribe(data => {
        this.journalData.set(data);
      });
  }
}
