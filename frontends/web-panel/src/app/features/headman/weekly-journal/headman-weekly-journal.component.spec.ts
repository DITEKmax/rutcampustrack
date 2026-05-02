import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  HeadmanWeeklyJournalComponent,
  filenameFromContentDisposition,
} from './headman-weekly-journal.component';
import { HeadmanWeeklyReportDialogComponent } from './headman-weekly-report-dialog.component';
import { HeadmanApiService } from '../shared/headman-api.service';
import { AuthService } from '../../../core/auth/auth.service';

function makeBlobResponse(filename = 'report.docx'): HttpResponse<Blob> {
  return new HttpResponse({
    body: new Blob(['report']),
    headers: new HttpHeaders({
      'content-disposition': `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
    }),
  });
}

function makeApi(overrides: Partial<HeadmanApiService> = {}): HeadmanApiService {
  return {
    listSubjects: vi.fn(() => of({ _embedded: { subjectResponseList: [] } })),
    getGroupMembers: vi.fn(() => of({ _embedded: { userResponseList: [] } })),
    getGroupLessons: vi.fn(() => of({ _embedded: { lessonResponseList: [] } })),
    getLessonAttendance: vi.fn(() => of({ entries: [] })),
    markAttendance: vi.fn(() => of(null)),
    downloadHeadmanWeeklyReport: vi.fn(() => of(makeBlobResponse('УВПВ511_27.04.2026_03.05.2026.pdf'))),
    ...overrides,
  } as unknown as HeadmanApiService;
}

function makeAuth(isHeadman = true): AuthService {
  return {
    currentUser: () => ({ id: 1, role: 'STUDENT', isHeadman, groupId: 5 }),
  } as unknown as AuthService;
}

function setup(options: {
  isHeadman?: boolean;
  api?: Partial<HeadmanApiService>;
  dialogResult?: HttpResponse<Blob> | null;
} = {}): {
  fixture: ComponentFixture<HeadmanWeeklyJournalComponent>;
  component: HeadmanWeeklyJournalComponent;
  api: HeadmanApiService;
  dialog: { open: ReturnType<typeof vi.fn> };
} {
  const api = makeApi(options.api);
  const dialog = {
    open: vi.fn(() => ({ afterClosed: () => of(options.dialogResult ?? null) })),
  };

  TestBed.configureTestingModule({
    imports: [HeadmanWeeklyJournalComponent],
    providers: [
      provideNoopAnimations(),
      { provide: HeadmanApiService, useValue: api },
      { provide: AuthService, useValue: makeAuth(options.isHeadman ?? true) },
      { provide: MatDialog, useValue: dialog },
      { provide: MatSnackBar, useValue: { open: vi.fn() } },
    ],
  });

  const fixture = TestBed.createComponent(HeadmanWeeklyJournalComponent);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance, api, dialog };
}

describe('HeadmanWeeklyJournalComponent reports', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    Object.defineProperty(URL, 'createObjectURL', {
      value: vi.fn(() => 'blob:report'),
      configurable: true,
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      value: vi.fn(),
      configurable: true,
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  });

  it('renders report export controls for headman', () => {
    const { fixture } = setup();
    expect(fixture.nativeElement.textContent).toContain('Скачать отчет за текущую неделю');
    expect(fixture.nativeElement.textContent).toContain('Скачать несколько недель');
  });

  it('hides report export controls for non-headman users', () => {
    const { fixture } = setup({ isHeadman: false });
    expect(fixture.nativeElement.textContent).not.toContain('Скачать отчет за текущую неделю');
    expect(fixture.nativeElement.textContent).not.toContain('Скачать несколько недель');
  });

  it('downloads the currently opened week with selected format', () => {
    const { component, api } = setup();
    component.monday.set(new Date(2026, 3, 27));
    component.selectCurrentReportFormat('pdf');

    component.downloadOpenedWeekReport();

    expect(api.downloadHeadmanWeeklyReport).toHaveBeenCalledWith('2026-04-27', 'pdf');
    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:report');
  });

  it('opens multi-week report dialog and downloads returned blob response', () => {
    const response = makeBlobResponse('УВПВ511_27.04.2026_17.05.2026_С_ПРОПУСКАМИ.docx');
    const { component, dialog } = setup({ dialogResult: response });

    component.openMultiWeekReportDialog();

    expect(dialog.open).toHaveBeenCalledWith(
      HeadmanWeeklyReportDialogComponent,
      expect.objectContaining({ ariaLabel: 'Скачать отчет за несколько недель' }),
    );
    expect(URL.createObjectURL).toHaveBeenCalled();
  });

  it('parses UTF-8 filename from content-disposition', () => {
    expect(
      filenameFromContentDisposition(
        "attachment; filename*=UTF-8''%D0%A3%D0%92%D0%9F%D0%92511.docx",
      ),
    ).toBe('УВПВ511.docx');
  });
});
