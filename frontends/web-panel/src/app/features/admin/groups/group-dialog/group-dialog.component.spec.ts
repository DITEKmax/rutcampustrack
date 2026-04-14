import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';

import { GroupDialogComponent } from './group-dialog.component';

function setup(data: { mode: 'create' | 'edit'; group?: any }) {
  const dialogRef = { close: (_: any) => {} };
  TestBed.configureTestingModule({
    imports: [GroupDialogComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideAnimations(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: data },
    ],
  });
  const fixture = TestBed.createComponent(GroupDialogComponent);
  const component = fixture.componentInstance;
  const httpMock = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  return { component, httpMock, dialogRef };
}

describe('GroupDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => {
    httpMock?.verify();
  });

  it('create mode: форма имеет только поле name (без code) и slider скрыт', () => {
    const setupRes = setup({ mode: 'create' });
    httpMock = setupRes.httpMock;
    expect(setupRes.component.form.controls.name).toBeDefined();
    // поле code удалено из DTO (BUG-006-5)
    expect((setupRes.component.form.controls as any).code).toBeUndefined();
    expect(setupRes.component.isEdit).toBe(false);
  });

  it('pattern: УИТ-311 валидно, УВПв-511 валидно, abc/уит-311/УИТ-31/ИВТ11-001 невалидно', () => {
    const setupRes = setup({ mode: 'create' });
    httpMock = setupRes.httpMock;
    const nameCtl = setupRes.component.form.controls.name;

    nameCtl.setValue('УИТ-311');
    expect(nameCtl.valid).toBe(true);

    nameCtl.setValue('УВПв-511');
    expect(nameCtl.valid).toBe(true);

    nameCtl.setValue('abc');
    expect(nameCtl.hasError('pattern')).toBe(true);

    nameCtl.setValue('уит-311');
    expect(nameCtl.hasError('pattern')).toBe(true);

    nameCtl.setValue('УИТ-31');
    expect(nameCtl.hasError('pattern')).toBe(true);

    nameCtl.setValue('ИВТ11-001');
    expect(nameCtl.hasError('pattern')).toBe(true);

    nameCtl.setValue('УИТ-411 (выпуск 2026)');
    // archived format exceeds maxLength(8) AND pattern — must be invalid in CreateRequest
    expect(nameCtl.valid).toBe(false);
  });

  it('save(create) шлёт POST /api/academic/groups с body { name }', () => {
    const setupRes = setup({ mode: 'create' });
    httpMock = setupRes.httpMock;
    setupRes.component.form.controls.name.setValue('УИТ-311');
    setupRes.component.save();

    const req = httpMock.expectOne('/api/academic/groups');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'УИТ-311' });
    // body не содержит code
    expect((req.request.body as any).code).toBeUndefined();
    req.flush({ id: 1, name: 'УИТ-311', active: true, createdAt: '2026-04-14T00:00:00Z' });
  });

  it('edit mode: патчит форму именем и active (без code)', () => {
    const setupRes = setup({
      mode: 'edit',
      group: { id: 7, name: 'УИТ-311', active: true, createdAt: '2026-04-14T00:00:00Z' },
    });
    httpMock = setupRes.httpMock;
    expect(setupRes.component.form.value.name).toBe('УИТ-311');
    expect(setupRes.component.form.value.active).toBe(true);
  });

  it('409 с field=name → помечает control ошибкой conflict и показывает сообщение', () => {
    const setupRes = setup({ mode: 'create' });
    httpMock = setupRes.httpMock;
    setupRes.component.form.controls.name.setValue('УИТ-311');
    setupRes.component.save();

    const req = httpMock.expectOne('/api/academic/groups');
    req.flush(
      { status: 409, title: 'Конфликт данных', detail: 'Группа с таким названием уже существует', field: 'name' },
      { status: 409, statusText: 'Conflict' },
    );

    const nameCtl = setupRes.component.form.controls.name;
    expect(nameCtl.errors?.['conflict']).toBe('Группа с таким названием уже существует');
    expect(setupRes.component.submitError()).toBe('Группа с таким названием уже существует');
    expect(setupRes.component.saving).toBe(false);
  });
});
