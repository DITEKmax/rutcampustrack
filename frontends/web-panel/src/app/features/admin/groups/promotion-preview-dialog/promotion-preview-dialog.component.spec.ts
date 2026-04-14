import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { PromotionPreviewDialogComponent } from './promotion-preview-dialog.component';
import type { PromotionSummary } from '../../shared/types';

function emptySummary(overrides: Partial<PromotionSummary> = {}): PromotionSummary {
  return { toPromote: [], toArchive: [], conflicts: [], dryRun: true, executed: false, ...overrides };
}

describe('PromotionPreviewDialogComponent', () => {
  let component: PromotionPreviewDialogComponent;
  let httpMock: HttpTestingController;
  let closeSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    closeSpy = vi.fn();
    TestBed.configureTestingModule({
      imports: [PromotionPreviewDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: { close: closeSpy } },
      ],
    });
    const fixture = TestBed.createComponent(PromotionPreviewDialogComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('ngOnInit вызывает POST /groups/promote/preview и переводит в фазу preview', () => {
    component.ngOnInit();

    expect(component.phase()).toBe('loading');

    const req = httpMock.expectOne('/api/academic/groups/promote/preview');
    expect(req.request.method).toBe('POST');
    const summary = emptySummary({
      toPromote: [{ id: 1, from: 'УИТ-111', to: 'УИТ-211', action: 'PROMOTE' }],
    });
    req.flush(summary);

    expect(component.phase()).toBe('preview');
    expect(component.summary()).toEqual(summary);
  });

  it('summary с conflicts показывает их и оставляет кнопку подтверждения активной (isEmpty=false)', () => {
    component.ngOnInit();
    httpMock
      .expectOne('/api/academic/groups/promote/preview')
      .flush(
        emptySummary({
          toPromote: [{ id: 1, from: 'УИТ-111', to: 'УИТ-211', action: 'PROMOTE' }],
          conflicts: [{ prefix: 'УВП', reason: 'unknown_type', message: 'Неизвестный тип', groupIds: [9] }],
        }),
      );

    expect(component.phase()).toBe('preview');
    expect(component.isEmpty()).toBe(false);
    expect(component.summary()!.conflicts.length).toBe(1);
  });

  it('isEmpty() = true если toPromote и toArchive пустые → confirm() не работает', () => {
    component.ngOnInit();
    httpMock
      .expectOne('/api/academic/groups/promote/preview')
      .flush(emptySummary({ conflicts: [{ prefix: 'УВП', reason: 'parse_error', message: 'x', groupIds: [] }] }));

    expect(component.isEmpty()).toBe(true);

    component.confirm();

    httpMock.expectNone('/api/academic/groups/promote');
  });

  it('confirm() вызывает POST /groups/promote и переходит в done', () => {
    component.ngOnInit();
    httpMock
      .expectOne('/api/academic/groups/promote/preview')
      .flush(emptySummary({ toArchive: [{ id: 9, from: 'УИТ-411', action: 'ARCHIVE' }] }));

    component.confirm();
    expect(component.phase()).toBe('executing');

    const done = emptySummary({
      toArchive: [{ id: 9, from: 'УИТ-411', action: 'ARCHIVE' }],
      dryRun: false,
      executed: true,
    });
    httpMock.expectOne('/api/academic/groups/promote').flush(done);

    expect(component.phase()).toBe('done');
    expect(component.result()).toEqual(done);
  });

  it('cancel() закрывает диалог без аргумента', () => {
    component.ngOnInit();
    httpMock.expectOne('/api/academic/groups/promote/preview').flush(emptySummary());

    component.cancel();

    expect(closeSpy).toHaveBeenCalledWith();
  });

  it('close() (после done) закрывает диалог с результатом "done"', () => {
    component.ngOnInit();
    httpMock
      .expectOne('/api/academic/groups/promote/preview')
      .flush(emptySummary({ toPromote: [{ id: 1, from: 'УИТ-111', to: 'УИТ-211', action: 'PROMOTE' }] }));

    component.confirm();
    httpMock.expectOne('/api/academic/groups/promote').flush(emptySummary({ executed: true, dryRun: false }));

    component.close();

    expect(closeSpy).toHaveBeenCalledWith('done');
  });

  it('ошибка на preview переводит в фазу error с сообщением', () => {
    component.ngOnInit();
    httpMock
      .expectOne('/api/academic/groups/promote/preview')
      .flush({ detail: 'server down' }, { status: 500, statusText: 'ISE' });

    expect(component.phase()).toBe('error');
    expect(component.errorMessage()).toBe('server down');
  });
});
