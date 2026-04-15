import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';

/**
 * Create request — aligns 1:1 with backend CreateHomeworkRequest
 * (services/academic-service/academic-api-contract/.../CreateHomeworkRequest.java).
 */
export interface CreateHomeworkRequest {
  title: string;
  description: string | null;
  link: string | null;
  subjectId: number;
  groupId: number;
  semesterId: number;
  lessonDate: string; // YYYY-MM-DD (ISO LocalDate)
  lessonNumber: number;
}

/** Update request — PUT semantics, lesson binding is immutable post-create (D-01). */
export interface UpdateHomeworkRequest {
  title: string;
  description: string | null;
  link: string | null;
}

/** Response shape returned by POST/PUT/GET /api/academic/homeworks. */
export interface HomeworkResponse {
  id: number;
  title: string;
  description: string | null;
  link: string | null;
  subjectId: number;
  groupId: number;
  semesterId: number;
  publishedBy: number;
  completed: boolean;
  createdAt: string;
  lessonDate: string;
  lessonNumber: number;
}

interface PagedHomeworkResponse {
  _embedded?: { homeworkResponseList?: HomeworkResponse[] };
  page?: { totalElements: number; totalPages: number; size: number; number: number };
}

/**
 * HTTP client for the headman homework CRUD endpoints on academic-service.
 *
 * Phase 61-05: exposes the three write operations plus a `list` helper that
 * unwraps the HATEOAS envelope (matches StudentApiService.getHomeworks
 * pattern). URLs are absolute `/api/...` paths — routed to API Gateway by
 * the dev proxy and prod nginx.
 *
 * Auth: the global `authInterceptor` injects `Authorization: Bearer <jwt>`.
 * Backend guards (D-05, D-06) enforce that only the HEADMAN author of a ДЗ
 * can mutate it — this client does not pre-check roles.
 */
@Injectable({ providedIn: 'root' })
export class HeadmanHomeworkApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/academic/homeworks';

  /** Fetch homeworks for a group + semester (paged, client-filters by date). */
  list(groupId: number, semesterId: number): Observable<HomeworkResponse[]> {
    const params = new HttpParams()
      .set('groupId', String(groupId))
      .set('semesterId', String(semesterId))
      .set('size', '200');
    return this.http
      .get<PagedHomeworkResponse>(this.base, { params })
      .pipe(map(resp => resp._embedded?.homeworkResponseList ?? []));
  }

  create(req: CreateHomeworkRequest): Observable<HomeworkResponse> {
    return this.http.post<HomeworkResponse>(this.base, req);
  }

  update(id: number, req: UpdateHomeworkRequest): Observable<HomeworkResponse> {
    return this.http.put<HomeworkResponse>(`${this.base}/${id}`, req);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
