import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { OneOffDialogComponent, OneOffDialogData } from './one-off-dialog.component';
import { HeadmanApiService } from '../shared/headman-api.service';

const SUBJECTS = [
  { id: 100, name: 'Алгебра' },
  { id: 101, name: 'Физика' },
];

function makeApi(overrides: Partial<HeadmanApiService> = {}): HeadmanApiService {
  return {
    listSubjects: vi.fn(() => of({ _embedded: { subjectResponseList: SUBJECTS } })),
    createOneOffLesson: vi.fn(() => of({ id: 42 })),
    ...overrides,
  } as unknown as HeadmanApiService;
}

function setup(
  apiOverrides: Partial<HeadmanApiService> = {},
  data: OneOffDialogData = { groupId: 5 },
): {
  fixture: ComponentFixture<OneOffDialogComponent>;
  component: OneOffDialogComponent;
  api: HeadmanApiService;
  dialogRef: { close: ReturnType<typeof vi.fn> };
  snackBar: { open: ReturnType<typeof vi.fn> };
} {
  const api = makeApi(apiOverrides);
  const dialogRef = { close: vi.fn() };
  const snackBar = { open: vi.fn() };
  TestBed.configureTestingModule({
    imports: [OneOffDialogComponent],
    providers: [
      provideNoopAnimations(),
      { provide: HeadmanApiService, useValue: api },
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: data },
      { provide: MatSnackBar, useValue: snackBar },
    ],
  });
  const fixture = TestBed.createComponent(OneOffDialogComponent);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, api, dialogRef, snackBar };
}

describe('OneOffDialogComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('creates with date, lessonNumber, subjectId, classroom form fields', () => {
    const { component } = setup();
    expect(component.form.get('date')).toBeTruthy();
    expect(component.form.get('lessonNumber')).toBeTruthy();
    expect(component.form.get('subjectId')).toBeTruthy();
    expect(component.form.get('classroom')).toBeTruthy();
    expect(component.lessonNumbers).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);
    expect(component.subjects.length).toBe(2);
  });

  it('submit calls createOneOffLesson with {groupId, subjectId, date, lessonNumber, classroom}', () => {
    const { component, api, dialogRef } = setup();
    component.form.patchValue({
      date: new Date(2026, 3, 20), // 2026-04-20
      lessonNumber: 3,
      subjectId: 100,
      classroom: '305',
    });
    component.onSubmit();
    expect(api.createOneOffLesson).toHaveBeenCalledWith({
      groupId: 5,
      subjectId: 100,
      date: '2026-04-20',
      lessonNumber: 3,
      classroom: '305',
    });
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('form invalid blocks submit when required fields missing', () => {
    const { component, api } = setup();
    component.form.patchValue({ date: null, lessonNumber: null, subjectId: null });
    expect(component.form.valid).toBe(false);
    component.onSubmit();
    expect(api.createOneOffLesson).not.toHaveBeenCalled();
  });

  it('409 response → "Слот занят" apiError (user-visible)', () => {
    const { component, api } = setup({
      createOneOffLesson: vi.fn(() => ({ subscribe: (o: any) => o.error({ status: 409 }) } as any)),
    });
    component.form.patchValue({
      date: new Date(2026, 3, 20),
      lessonNumber: 3,
      subjectId: 100,
      classroom: '',
    });
    component.onSubmit();
    expect(component.apiError).toContain('Слот занят');
    expect(api.createOneOffLesson).toHaveBeenCalled();
  });

  it('403 response → "Недостаточно прав" apiError', () => {
    const { component } = setup({
      createOneOffLesson: vi.fn(() => ({ subscribe: (o: any) => o.error({ status: 403 }) } as any)),
    });
    component.form.patchValue({
      date: new Date(2026, 3, 20),
      lessonNumber: 3,
      subjectId: 100,
      classroom: '',
    });
    component.onSubmit();
    expect(component.apiError).toContain('Недостаточно прав');
  });
});
