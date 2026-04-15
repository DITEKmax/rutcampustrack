import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { animate, style, transition, trigger } from '@angular/animations';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  CreateHomeworkRequest,
  HeadmanHomeworkApiService,
  HomeworkResponse,
  UpdateHomeworkRequest,
} from '../headman-homework-api.service';

/**
 * Inline (non-modal) create/edit form for a single homework assignment.
 *
 * D-08: the headman homework page MUST NOT use MatDialog — the form is a
 * collapsible panel rendered directly below the target lesson, so the
 * headman can quickly add several assignments in a row without context
 * switches.
 *
 * D-11: `subjectId` / `lessonDate` / `lessonNumber` / `groupId` / `semesterId`
 * come from the lesson context (parent page) and are rendered as a readonly
 * header — the headman never chooses them. Only title / description / link
 * are editable.
 *
 * Modes:
 * - create: `initialHomework` is null → POST /api/academic/homeworks
 * - edit:   `initialHomework` is the existing ДЗ → PUT /.../{id} with only
 *           title/description/link (the lesson binding is immutable, D-01).
 */
@Component({
  selector: 'app-homework-inline-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  animations: [
    trigger('expand', [
      transition(':enter', [
        style({ height: 0, opacity: 0, overflow: 'hidden' }),
        animate(
          '200ms cubic-bezier(0.16, 1, 0.3, 1)',
          style({ height: '*', opacity: 1 }),
        ),
      ]),
      transition(':leave', [
        style({ overflow: 'hidden' }),
        animate(
          '150ms ease-in',
          style({ height: 0, opacity: 0 }),
        ),
      ]),
    ]),
  ],
  template: `
    <div class="hw-form" [@expand] role="form" [attr.aria-label]="ariaLabel()">
      <div class="hw-form__context">
        <i class="ph ph-book-open" aria-hidden="true"></i>
        <span class="hw-form__context-label">Предмет:</span>
        <span class="hw-form__context-value">{{ subjectName }}</span>
        <span class="hw-form__context-sep">·</span>
        <span>{{ formattedDate() }}</span>
        <span class="hw-form__context-sep">·</span>
        <span>Пара № {{ lessonNumber }}</span>
      </div>

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <mat-form-field appearance="outline" class="hw-form__field">
          <mat-label>Название задания</mat-label>
          <input
            matInput
            formControlName="title"
            maxlength="255"
            placeholder="Например: Решить №12-18 из учебника"
            required />
          @if (form.get('title')?.hasError('required') && form.get('title')?.touched) {
            <mat-error>Название обязательно</mat-error>
          }
          @if (form.get('title')?.hasError('maxlength')) {
            <mat-error>Не более 255 символов</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="hw-form__field">
          <mat-label>Описание (необязательно)</mat-label>
          <textarea
            matInput
            formControlName="description"
            rows="3"
            maxlength="4000"
            placeholder="Что именно нужно сделать, какие страницы/задачи и т.д."></textarea>
          @if (form.get('description')?.hasError('maxlength')) {
            <mat-error>Не более 4000 символов</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="hw-form__field">
          <mat-label>Ссылка на материалы (необязательно)</mat-label>
          <input
            matInput
            formControlName="link"
            type="url"
            maxlength="2048"
            placeholder="https://..." />
          @if (form.get('link')?.hasError('maxlength')) {
            <mat-error>Не более 2048 символов</mat-error>
          }
        </mat-form-field>

        @if (apiError()) {
          <div class="hw-form__error" role="alert">
            <i class="ph ph-warning-circle" aria-hidden="true"></i>
            {{ apiError() }}
          </div>
        }

        <div class="hw-form__actions">
          <button
            type="button"
            mat-stroked-button
            (click)="cancel()"
            [disabled]="submitting()">
            Отмена
          </button>
          <button
            type="submit"
            mat-flat-button
            color="primary"
            class="btn-brand"
            [disabled]="form.invalid || submitting()">
            @if (submitting()) {
              <mat-spinner diameter="16" aria-hidden="true"></mat-spinner>
            }
            {{ isEdit() ? 'Сохранить' : 'Создать' }}
          </button>
        </div>
      </form>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .hw-form {
      margin-top: 8px;
      padding: var(--space-4, 16px);
      background: var(--bg-elevated, #fff);
      border: 1px solid var(--accent-secondary, #1976d2);
      border-radius: var(--radius-lg, 12px);
    }
    .hw-form__context {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px;
      font-size: 0.8rem;
      color: var(--text-secondary, #555);
      margin-bottom: 12px;
      padding-bottom: 8px;
      border-bottom: 1px dashed var(--border-subtle, #e0e0e0);
    }
    .hw-form__context-label { font-weight: 500; }
    .hw-form__context-value {
      font-weight: 600;
      color: var(--text-primary);
    }
    .hw-form__context-sep { color: var(--text-muted, #999); }
    .hw-form__field { width: 100%; display: block; margin-bottom: 4px; }
    .hw-form__error {
      margin: var(--space-2) 0;
      padding: var(--space-2) var(--space-3);
      background: color-mix(in oklab, var(--accent-danger) 12%, transparent);
      border: 1px solid color-mix(in oklab, var(--accent-danger) 28%, transparent);
      color: var(--accent-danger);
      border-radius: var(--radius-md);
      font-size: 0.8125rem;
      display: inline-flex; align-items: center; gap: 6px;
    }
    .hw-form__actions {
      display: flex;
      gap: 8px;
      justify-content: flex-end;
      margin-top: 8px;
    }
  `],
})
export class HomeworkInlineFormComponent implements OnInit {
  private readonly api = inject(HeadmanHomeworkApiService);

  @Input({ required: true }) subjectId!: number;
  @Input({ required: true }) subjectName!: string;
  @Input({ required: true }) lessonDate!: string; // YYYY-MM-DD
  @Input({ required: true }) lessonNumber!: number;
  @Input({ required: true }) groupId!: number;
  @Input({ required: true }) semesterId!: number;
  @Input() initialHomework: HomeworkResponse | null = null;

  @Output() saved = new EventEmitter<HomeworkResponse>();
  @Output() cancelled = new EventEmitter<void>();

  readonly form = new FormGroup({
    title: new FormControl<string>('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(255)],
    }),
    description: new FormControl<string>('', {
      nonNullable: true,
      validators: [Validators.maxLength(4000)],
    }),
    link: new FormControl<string>('', {
      nonNullable: true,
      validators: [Validators.maxLength(2048)],
    }),
  });

  readonly submitting = signal(false);
  readonly apiError = signal<string | null>(null);

  ngOnInit(): void {
    if (this.initialHomework) {
      this.form.patchValue({
        title: this.initialHomework.title ?? '',
        description: this.initialHomework.description ?? '',
        link: this.initialHomework.link ?? '',
      });
    }
  }

  isEdit(): boolean {
    return this.initialHomework !== null;
  }

  ariaLabel(): string {
    return this.isEdit() ? 'Редактировать домашнее задание' : 'Создать домашнее задание';
  }

  formattedDate(): string {
    // D-14: Russian labels. Accept YYYY-MM-DD, output e.g. "15 апр".
    const [y, m, d] = this.lessonDate.split('-');
    const months = [
      'янв', 'фев', 'мар', 'апр', 'мая', 'июн',
      'июл', 'авг', 'сен', 'окт', 'ноя', 'дек',
    ];
    const mi = Math.max(0, Math.min(11, parseInt(m, 10) - 1));
    return `${parseInt(d, 10)} ${months[mi]} ${y}`;
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const title = v.title.trim();
    const description = v.description.trim() || null;
    const link = v.link.trim() || null;

    this.submitting.set(true);
    this.apiError.set(null);

    const call$ = this.isEdit()
      ? this.api.update(this.initialHomework!.id, {
          title,
          description,
          link,
        } as UpdateHomeworkRequest)
      : this.api.create({
          title,
          description,
          link,
          subjectId: this.subjectId,
          groupId: this.groupId,
          semesterId: this.semesterId,
          lessonDate: this.lessonDate,
          lessonNumber: this.lessonNumber,
        } as CreateHomeworkRequest);

    call$.subscribe({
      next: hw => {
        this.submitting.set(false);
        this.saved.emit(hw);
      },
      error: err => {
        this.submitting.set(false);
        this.apiError.set(this.extractError(err));
      },
    });
  }

  cancel(): void {
    if (this.submitting()) return;
    this.cancelled.emit();
  }

  /**
   * Best-effort extraction of a human-readable error from an HttpErrorResponse.
   * Backend emits RFC 7807 `ErrorResponse` with `detail` (and optional `field`);
   * falls back to a generic message if the shape differs.
   */
  private extractError(err: unknown): string {
    const e = err as { status?: number; error?: { detail?: string; message?: string } };
    const detail = e?.error?.detail ?? e?.error?.message;
    if (detail) return detail;
    if (e?.status === 403) return 'Недостаточно прав для этого действия.';
    if (e?.status === 400) return 'Проверьте введённые данные.';
    return 'Не удалось сохранить задание. Попробуйте ещё раз.';
  }
}
