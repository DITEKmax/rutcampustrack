import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { HomeworkInlineFormComponent } from './homework-inline-form.component';
import {
  HeadmanHomeworkApiService,
  HomeworkResponse,
} from '../headman-homework-api.service';

/**
 * Unit tests for HomeworkInlineFormComponent (Phase 61-05).
 *
 * Covers:
 * - title required validator (D-14 — inline errors under fields)
 * - submit in create mode → api.create called with full lesson context
 * - submit in edit mode (initialHomework != null) → api.update called with id
 */

const HW_EXISTING: HomeworkResponse = {
  id: 42,
  title: 'Старый title',
  description: 'Старое описание',
  link: 'https://old.example/',
  subjectId: 7,
  groupId: 5,
  semesterId: 1,
  publishedBy: 100,
  completed: false,
  createdAt: '2026-04-10T10:00:00Z',
  lessonDate: '2026-04-15',
  lessonNumber: 2,
};

function makeApi(overrides: Partial<HeadmanHomeworkApiService> = {}): HeadmanHomeworkApiService {
  return {
    create: vi.fn(() => of({ ...HW_EXISTING, id: 43 })),
    update: vi.fn(() => of(HW_EXISTING)),
    delete: vi.fn(() => of(void 0)),
    list: vi.fn(() => of([])),
    ...overrides,
  } as unknown as HeadmanHomeworkApiService;
}

function setup(
  options: { initialHomework?: HomeworkResponse | null; apiOverrides?: Partial<HeadmanHomeworkApiService> } = {},
): {
  fixture: ComponentFixture<HomeworkInlineFormComponent>;
  component: HomeworkInlineFormComponent;
  api: HeadmanHomeworkApiService;
} {
  const api = makeApi(options.apiOverrides);
  TestBed.configureTestingModule({
    imports: [HomeworkInlineFormComponent],
    providers: [
      provideNoopAnimations(),
      { provide: HeadmanHomeworkApiService, useValue: api },
    ],
  });
  const fixture = TestBed.createComponent(HomeworkInlineFormComponent);
  const component = fixture.componentInstance;
  component.subjectId = 7;
  component.subjectName = 'Математика';
  component.lessonDate = '2026-04-15';
  component.lessonNumber = 2;
  component.groupId = 5;
  component.semesterId = 1;
  component.initialHomework = options.initialHomework ?? null;
  fixture.detectChanges();
  return { fixture, component, api };
}

describe('HomeworkInlineFormComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('marks title as required — form invalid when empty', () => {
    const { component } = setup();
    expect(component.form.valid).toBe(false);
    expect(component.form.get('title')?.hasError('required')).toBe(true);
    // Description/link have no required — empty is fine
    expect(component.form.get('description')?.valid).toBe(true);
    expect(component.form.get('link')?.valid).toBe(true);
  });

  it('submit in create mode calls api.create with full lesson context', () => {
    const { component, api } = setup();
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    component.form.patchValue({
      title: 'Новое ДЗ',
      description: 'Решить № 1-5',
      link: 'https://example.com/task',
    });
    component.submit();

    expect(api.create).toHaveBeenCalledWith({
      title: 'Новое ДЗ',
      description: 'Решить № 1-5',
      link: 'https://example.com/task',
      subjectId: 7,
      groupId: 5,
      semesterId: 1,
      lessonDate: '2026-04-15',
      lessonNumber: 2,
    });
    expect(api.update).not.toHaveBeenCalled();
    expect(savedSpy).toHaveBeenCalledTimes(1);
  });

  it('submit in edit mode (initialHomework != null) calls api.update with id', () => {
    const { component, api } = setup({ initialHomework: HW_EXISTING });
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    // ngOnInit should prefill the form from initialHomework
    expect(component.form.value.title).toBe('Старый title');

    component.form.patchValue({
      title: 'Новый title',
      description: '',
      link: '',
    });
    component.submit();

    expect(api.update).toHaveBeenCalledWith(42, {
      title: 'Новый title',
      description: null,
      link: null,
    });
    expect(api.create).not.toHaveBeenCalled();
    expect(savedSpy).toHaveBeenCalledTimes(1);
  });

  it('api error surfaces to apiError signal and does not emit saved', () => {
    const { component } = setup({
      apiOverrides: {
        create: vi.fn(() =>
          throwError(() => ({
            status: 400,
            error: { detail: 'На эту пару нельзя назначить ДЗ — пары нет в расписании' },
          })),
        ),
      },
    });
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    component.form.patchValue({ title: 'X', description: '', link: '' });
    component.submit();

    expect(component.apiError()).toContain('пары нет в расписании');
    expect(savedSpy).not.toHaveBeenCalled();
    expect(component.submitting()).toBe(false);
  });

  it('cancel emits cancelled event', () => {
    const { component } = setup();
    const cancelledSpy = vi.fn();
    component.cancelled.subscribe(cancelledSpy);
    component.cancel();
    expect(cancelledSpy).toHaveBeenCalledTimes(1);
  });
});
