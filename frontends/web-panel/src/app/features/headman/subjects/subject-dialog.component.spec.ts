import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { SubjectDialogComponent, SubjectDialogData } from './subject-dialog.component';
import { HeadmanApiService } from '../shared/headman-api.service';

const TEACHERS = [
  { id: 1, fullName: 'Иванов И.И.', lastName: 'Иванов' },
  { id: 2, fullName: 'Петров П.П.', lastName: 'Петров' },
  { id: 3, fullName: 'Сидоров С.С.', lastName: 'Сидоров' },
];

function makeApi(overrides: Partial<HeadmanApiService> = {}): HeadmanApiService {
  return {
    listTeachers: vi.fn(() => of({ _embedded: { userResponseList: TEACHERS } })),
    createSubject: vi.fn(() => of({ id: 99, name: 'Новый', type: 'LECTURE', teacherIds: [] })),
    updateSubject: vi.fn(() => of({ id: 10, name: 'Обновлён', type: 'PRACTICE', teacherIds: [] })),
    ...overrides,
  } as unknown as HeadmanApiService;
}

function setup(
  data: SubjectDialogData,
  apiOverrides: Partial<HeadmanApiService> = {},
): {
  fixture: ComponentFixture<SubjectDialogComponent>;
  component: SubjectDialogComponent;
  api: HeadmanApiService;
  dialogRef: { close: ReturnType<typeof vi.fn> };
} {
  const api = makeApi(apiOverrides);
  const dialogRef = { close: vi.fn() };
  const snackBar = { open: vi.fn() };
  TestBed.configureTestingModule({
    imports: [SubjectDialogComponent],
    providers: [
      provideNoopAnimations(),
      { provide: HeadmanApiService, useValue: api },
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: data },
      { provide: MatSnackBar, useValue: snackBar },
    ],
  });
  const fixture = TestBed.createComponent(SubjectDialogComponent);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, api, dialogRef };
}

describe('SubjectDialogComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  describe('create mode', () => {
    it('submits with name, type=LECTURE and teacherIds=[1,2]', () => {
      const { component, api } = setup({ mode: 'create' });

      component.form.patchValue({
        name: 'Алгебра',
        type: 'LECTURE',
        teacherIds: [1, 2],
      });

      expect(component.form.valid).toBe(true);
      component.onSubmit();

      expect(api.createSubject).toHaveBeenCalledWith({
        name: 'Алгебра',
        type: 'LECTURE',
        teacherIds: [1, 2],
      });
    });

    it('empty teacherIds=[] is valid (teachers optional)', () => {
      const { component, api } = setup({ mode: 'create' });

      component.form.patchValue({
        name: 'Философия',
        type: 'LECTURE',
        teacherIds: [],
      });

      expect(component.form.valid).toBe(true);
      component.onSubmit();

      expect(api.createSubject).toHaveBeenCalledWith({
        name: 'Философия',
        type: 'LECTURE',
        teacherIds: [],
      });
    });

    it('missing type field makes the form invalid and blocks submit', () => {
      const { component, api } = setup({ mode: 'create' });

      component.form.patchValue({
        name: 'Геометрия',
        type: '',
        teacherIds: [1],
      });

      expect(component.form.valid).toBe(false);
      component.onSubmit();

      expect(api.createSubject).not.toHaveBeenCalled();
    });

    it('missing name makes the form invalid and blocks submit', () => {
      const { component, api } = setup({ mode: 'create' });

      component.form.patchValue({
        name: '',
        type: 'LECTURE',
        teacherIds: [],
      });

      expect(component.form.valid).toBe(false);
      component.onSubmit();

      expect(api.createSubject).not.toHaveBeenCalled();
    });
  });

  describe('edit mode', () => {
    it('prefills teacherIds from data.subject', () => {
      const { component } = setup({
        mode: 'edit',
        subject: { id: 10, name: 'Физика', type: 'PRACTICE', teacherIds: [3, 4] },
      });

      expect(component.form.value.name).toBe('Физика');
      expect(component.form.value.type).toBe('PRACTICE');
      expect(component.form.value.teacherIds).toEqual([3, 4]);
    });

    it('submits via updateSubject with new teacherIds', () => {
      const { component, api } = setup({
        mode: 'edit',
        subject: { id: 10, name: 'Физика', type: 'PRACTICE', teacherIds: [3] },
      });

      component.form.patchValue({ teacherIds: [1, 2, 3] });
      component.onSubmit();

      expect(api.updateSubject).toHaveBeenCalledWith(10, {
        name: 'Физика',
        type: 'PRACTICE',
        teacherIds: [1, 2, 3],
      });
    });

    it('missing subject.teacherIds defaults to empty array', () => {
      const { component } = setup({
        mode: 'edit',
        subject: { id: 11, name: 'Химия', type: 'LAB' },
      });

      expect(component.form.value.teacherIds).toEqual([]);
    });
  });

  describe('teacher loading', () => {
    it('loads and sorts teachers on init', () => {
      const { component, api } = setup({ mode: 'create' });

      expect(api.listTeachers).toHaveBeenCalled();
      expect(component.teachersLoading).toBe(false);
      expect(component.teachers.length).toBe(3);
      // Sorted by lastName with ru-collator (Иванов, Петров, Сидоров)
      expect(component.teachers[0].id).toBe(1);
      expect(component.teachers[2].id).toBe(3);
    });
  });
});
