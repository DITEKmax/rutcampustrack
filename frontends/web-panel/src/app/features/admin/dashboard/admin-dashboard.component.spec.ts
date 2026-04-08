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

    expect(component.stats()).toEqual(mockStats);
    expect(component.loading()).toBe(false);
    expect(component.error()).toBeNull();
  });

  it('on error, error signal is set', () => {
    component.ngOnInit();

    httpMock
      .expectOne('/api/academic/dashboard/stats')
      .flush(null, { status: 500, statusText: 'Server Error' });

    expect(component.error()).toBe('Не удалось загрузить сводку. Попробуйте позже.');
    expect(component.loading()).toBe(false);
  });
});
