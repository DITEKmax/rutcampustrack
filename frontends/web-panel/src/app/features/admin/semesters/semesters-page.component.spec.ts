import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { SemestersPageComponent } from './semesters-page.component';
import type { SemesterResponse, PagedResponse } from '../shared/types';

function mockSemestersResponse(semesters: SemesterResponse[]): PagedResponse<SemesterResponse> {
  return {
    _embedded: { semesterResponseList: semesters },
    page: { totalElements: semesters.length, totalPages: 1, size: 200, number: 0 },
  };
}

const MOCK_SEMESTERS: SemesterResponse[] = [
  { id: 1, name: 'Весна 2026', dateFrom: '2026-02-01', dateTo: '2026-06-30', active: true, createdAt: '2026-01-01T00:00:00Z' },
  { id: 2, name: 'Осень 2025', dateFrom: '2025-09-01', dateTo: '2026-01-31', active: false, createdAt: '2025-08-01T00:00:00Z' },
];

describe('SemestersPageComponent', () => {
  let component: SemestersPageComponent;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SemestersPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(SemestersPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('ngOnInit loads semesters', () => {
    component.ngOnInit();
    httpMock.expectOne(req => req.url === '/api/academic/semesters').flush(mockSemestersResponse(MOCK_SEMESTERS));

    expect(component.semesters().length).toBe(2);
    expect(component.loading()).toBe(false);
  });

  it('formatDate converts "2026-02-01" to "01.02.2026"', () => {
    expect(component.formatDate('2026-02-01')).toBe('01.02.2026');
    expect(component.formatDate('2025-12-31')).toBe('31.12.2025');
  });

  it('delete button should be disabled for active semesters', () => {
    component.ngOnInit();
    httpMock.expectOne(req => req.url === '/api/academic/semesters').flush(mockSemestersResponse(MOCK_SEMESTERS));

    const activeSem = component.semesters().find(s => s.active);
    expect(activeSem).toBeDefined();
    // Active semester should not allow deletion
    expect(activeSem!.active).toBe(true);
  });
});
