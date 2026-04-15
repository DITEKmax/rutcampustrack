import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { AdminDashboardComponent } from './admin-dashboard.component';

describe('AdminDashboardComponent', () => {
  let component: AdminDashboardComponent;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AdminDashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(AdminDashboardComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  function flushGroups(): void {
    // Dashboard also lists groups for the §4.4 table; answer with an empty page.
    const groupReqs = httpMock.match((r) => r.url === '/api/academic/groups');
    for (const r of groupReqs) {
      r.flush({ _embedded: { groupResponseList: [] }, page: { totalElements: 0, totalPages: 0, size: 100, number: 0 } });
    }
  }

  it('ngOnInit calls getDashboardStats', () => {
    component.ngOnInit();

    const req = httpMock.expectOne('/api/academic/dashboard/stats');
    expect(req.request.method).toBe('GET');
    req.flush({
      totalStudents: 100,
      totalTeachers: 10,
      totalGroups: 5,
      activeGroups: 3,
      activeSemesterName: 'Весна 2026',
    });
    flushGroups();
  });

  it('on success, stats signal is set with response data', () => {
    component.ngOnInit();

    const mockStats = {
      totalStudents: 50,
      totalTeachers: 5,
      totalGroups: 3,
      activeGroups: 2,
      activeSemesterName: 'Осень 2025',
    };

    httpMock.expectOne('/api/academic/dashboard/stats').flush(mockStats);
    flushGroups();

    expect(component.stats()).toEqual(mockStats);
    expect(component.loading()).toBe(false);
    expect(component.error()).toBeNull();
  });

  it('on error, error signal is set', () => {
    component.ngOnInit();

    httpMock
      .expectOne('/api/academic/dashboard/stats')
      .flush(null, { status: 500, statusText: 'Server Error' });
    flushGroups();

    expect(component.error()).toBe('Не удалось загрузить сводку. Попробуйте позже.');
    expect(component.loading()).toBe(false);
  });

  it('on success, recentGroups table is populated and sorted newest-first', () => {
    component.ngOnInit();
    httpMock.expectOne('/api/academic/dashboard/stats').flush({
      totalStudents: 1, totalTeachers: 1, totalGroups: 2, activeGroups: 1, activeSemesterName: null,
    });
    const groupReqs = httpMock.match((r) => r.url === '/api/academic/groups');
    expect(groupReqs.length).toBeGreaterThan(0);
    groupReqs[0].flush({
      _embedded: { groupResponseList: [
        { id: 1, name: 'ИСТ-201', active: true, createdAt: '2026-01-10T00:00:00Z' },
        { id: 2, name: 'ИСТ-202', active: false, createdAt: '2026-04-10T00:00:00Z', archivedAt: '2026-04-12T00:00:00Z' },
      ]},
      page: { totalElements: 2, totalPages: 1, size: 100, number: 0 },
    });
    const rows = component.recentGroups();
    expect(rows.length).toBe(2);
    // Newest first
    expect(rows[0].id).toBe(2);
    expect(rows[1].id).toBe(1);
    expect(component.groupsLoading()).toBe(false);
  });
});
