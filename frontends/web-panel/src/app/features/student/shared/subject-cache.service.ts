import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';
import type { SubjectResponse } from './student-schedule.types';

/**
 * In-memory cache for subject names keyed by subjectId.
 *
 * Rationale:
 * - Schedule rows carry `subjectId: number` but render the human subject
 *   name. A week's worth of lessons can repeat the same subject 6+ times;
 *   hitting `/api/academic/subjects/{id}` per row is wasteful.
 * - `shareReplay(1)` ensures multiple concurrent subscribers for the same
 *   id share exactly one HTTP call and all get the cached emission on
 *   subsequent subscribes.
 * - Errors (404, 5xx) fall back to the generic string "Предмет" so the
 *   schedule UI never shows a broken row; the academic service is the
 *   source of truth, and a missing lookup is non-fatal.
 */
@Injectable({ providedIn: 'root' })
export class SubjectCacheService {
  private readonly http = inject(HttpClient);
  private readonly cache = new Map<number, Observable<string>>();

  /**
   * Resolve a subject name for the given id.
   * Returns a shared Observable; re-subscribes hit the cached emission.
   */
  getName(subjectId: number | null | undefined): Observable<string> {
    if (!subjectId) {
      return of('Предмет');
    }

    const cached = this.cache.get(subjectId);
    if (cached) {
      return cached;
    }

    const request$ = this.http
      .get<SubjectResponse>(`/api/academic/subjects/${subjectId}`)
      .pipe(
        map(response => response.name),
        catchError(() => of('Предмет')),
        shareReplay(1),
      );
    this.cache.set(subjectId, request$);
    return request$;
  }
}
