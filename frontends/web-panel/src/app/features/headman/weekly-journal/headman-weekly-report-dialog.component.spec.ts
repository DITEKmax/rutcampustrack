import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { HeadmanWeeklyReportDialogComponent } from './headman-weekly-report-dialog.component';
import { HeadmanApiService } from '../shared/headman-api.service';

const WEEKS = [
  {
    weekOfSemester: 1,
    isoWeek: 18,
    label: 'Н1',
    weekStart: '2026-04-27',
    weekEnd: '2026-05-03',
    current: false,
  },
  {
    weekOfSemester: 2,
    isoWeek: 19,
    label: 'Н2',
    weekStart: '2026-05-04',
    weekEnd: '2026-05-10',
    current: true,
  },
  {
    weekOfSemester: 3,
    isoWeek: 20,
    label: 'Н3',
    weekStart: '2026-05-11',
    weekEnd: '2026-05-17',
    current: false,
  },
];

function setup(): {
  fixture: ComponentFixture<HeadmanWeeklyReportDialogComponent>;
  component: HeadmanWeeklyReportDialogComponent;
  api: HeadmanApiService;
  dialogRef: { close: ReturnType<typeof vi.fn> };
} {
  const api = {
    listHeadmanWeeklyReportWeeks: vi.fn(() => of({
      semesterId: 1,
      semesterName: 'Spring',
      semesterDateFrom: '2026-04-29',
      semesterDateTo: '2026-05-17',
      weeks: WEEKS,
    })),
    exportHeadmanWeeklyReport: vi.fn(() => of(new HttpResponse({ body: new Blob(['docx']) }))),
  } as unknown as HeadmanApiService;
  const dialogRef = { close: vi.fn() };

  TestBed.configureTestingModule({
    imports: [HeadmanWeeklyReportDialogComponent],
    providers: [
      provideNoopAnimations(),
      { provide: HeadmanApiService, useValue: api },
      { provide: MatDialogRef, useValue: dialogRef },
    ],
  });

  const fixture = TestBed.createComponent(HeadmanWeeklyReportDialogComponent);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, api, dialogRef };
}

describe('HeadmanWeeklyReportDialogComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('loads active-semester weeks and preselects current week', () => {
    const { component, api } = setup();
    expect(api.listHeadmanWeeklyReportWeeks).toHaveBeenCalled();
    expect(component.weeks().length).toBe(3);
    expect(component.selectedWeekStarts()).toEqual(['2026-05-04']);
  });

  it('exports non-consecutive selected weeks with one format and closes with blob response', () => {
    const { component, api, dialogRef } = setup();
    component.selectedWeekStarts.set(['2026-04-27', '2026-05-11']);
    component.format.set('png');

    component.exportSelected();

    expect(api.exportHeadmanWeeklyReport).toHaveBeenCalledWith({
      weekStarts: ['2026-04-27', '2026-05-11'],
      format: 'png',
    });
    expect((api.exportHeadmanWeeklyReport as any).mock.calls[0][0].groupId).toBeUndefined();
    expect(dialogRef.close).toHaveBeenCalledWith(expect.any(HttpResponse));
  });

  it('shows validation message when no week is selected', () => {
    const { component, api } = setup();
    component.selectedWeekStarts.set([]);

    component.exportSelected();

    expect(api.exportHeadmanWeeklyReport).not.toHaveBeenCalled();
    expect(component.selectionError()).toContain('Выберите');
  });
});
