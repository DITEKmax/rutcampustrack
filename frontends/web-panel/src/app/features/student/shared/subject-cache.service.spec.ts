import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { SubjectCacheService } from './subject-cache.service';

describe('SubjectCacheService', () => {
  let service: SubjectCacheService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SubjectCacheService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(SubjectCacheService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('issues GET /api/academic/subjects/{id} on first call and emits the subject name', () => {
    let emitted: string | null = null;
    service.getName(42).subscribe(name => (emitted = name));

    const req = httpMock.expectOne('/api/academic/subjects/42');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 42, name: 'Математика' });

    expect(emitted).toBe('Математика');
  });

  it('shares the same HTTP call across multiple subscribers (shareReplay)', () => {
    let firstEmission: string | null = null;
    let secondEmission: string | null = null;

    service.getName(42).subscribe(n => (firstEmission = n));
    // Second subscribe must NOT trigger a new request — shareReplay(1) replays.
    service.getName(42).subscribe(n => (secondEmission = n));

    // Exactly one outstanding request for subject 42.
    const req = httpMock.expectOne('/api/academic/subjects/42');
    req.flush({ id: 42, name: 'Математика' });

    expect(firstEmission).toBe('Математика');
    expect(secondEmission).toBe('Математика');
  });

  it('returns "Предмет" fallback for a null/zero/undefined id without any network call', () => {
    let forZero: string | null = null;
    let forUndefined: string | null = null;
    let forNull: string | null = null;

    service.getName(0).subscribe(n => (forZero = n));
    service.getName(undefined).subscribe(n => (forUndefined = n));
    service.getName(null).subscribe(n => (forNull = n));

    httpMock.expectNone('/api/academic/subjects/0');
    httpMock.expectNone('/api/academic/subjects/undefined');

    expect(forZero).toBe('Предмет');
    expect(forUndefined).toBe('Предмет');
    expect(forNull).toBe('Предмет');
  });

  it('emits "Предмет" fallback when the HTTP request errors', () => {
    let emitted: string | null = null;
    service.getName(99).subscribe(n => (emitted = n));

    const req = httpMock.expectOne('/api/academic/subjects/99');
    req.flush('not found', { status: 404, statusText: 'Not Found' });

    expect(emitted).toBe('Предмет');
  });
});
