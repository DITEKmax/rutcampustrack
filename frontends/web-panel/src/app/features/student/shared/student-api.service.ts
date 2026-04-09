import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import type {
  CheckinRequest,
  CheckinResponse,
  HomeworkItem,
  LessonResponse,
  PagedResponse,
  ResolvedThresholdResponse,
  StudentStatsResponse,
} from './student-schedule.types';

/**
 * Shared HttpClient wrapper for all student-cabinet REST calls.
 *
 * All URLs are absolute `/api/...` paths so the deployed nginx reverse proxy
 * and the Angular dev proxy both route them to the API Gateway. The Bearer
 * token is injected by the global authInterceptor (see app.config.ts:16) — no
 * manual header handling needed here.
 *
 * Method-to-endpoint map:
 * - getWeekLessons      → schedule-service   (lessons for a group in a date range)
 * - getStudentStats     → attendance-service (per-subject + overall stats for the JWT user)
 * - resolveGlobalThreshold / resolveGroupThreshold → academic-service (effective red-zone %)
 * - checkin             → attendance-service (geo check-in — server resolves the lesson)
 *
 * Shape note: schedule/lessons returns a HATEOAS PagedResponse with
 * `_embedded.lessonResponseList`; we unwrap the array here so callers receive
 * a plain `LessonResponse[]`. Stats/threshold/checkin return unwrapped bodies.
 */
@Injectable({ providedIn: 'root' })
export class StudentApiService {
  private readonly http = inject(HttpClient);

  /**
   * Fetch a group's lessons for an inclusive date range.
   * Backend paginates at 100 per request; Phase 51 reads weeks (≤36 lessons).
   */
  getWeekLessons(
    groupId: number,
    dateFrom: string,
    dateTo: string,
  ): Observable<LessonResponse[]> {
    const params = new HttpParams()
      .set('dateFrom', dateFrom)
      .set('dateTo', dateTo)
      .set('size', '100');
    return this.http
      .get<PagedResponse<LessonResponse>>(`/api/schedule/groups/${groupId}/lessons`, { params })
      .pipe(map(resp => resp._embedded?.['lessonResponseList'] ?? []));
  }

  /** Per-subject + overall attendance stats for the authenticated student. */
  getStudentStats(): Observable<StudentStatsResponse> {
    return this.http.get<StudentStatsResponse>('/api/attendance/reports/student/stats');
  }

  /** Effective global red-zone threshold (no params). */
  resolveGlobalThreshold(): Observable<ResolvedThresholdResponse> {
    return this.http.get<ResolvedThresholdResponse>('/api/academic/thresholds/resolve');
  }

  /** Effective group-level red-zone threshold (falls back to global server-side). */
  resolveGroupThreshold(groupId: number): Observable<ResolvedThresholdResponse> {
    const params = new HttpParams().set('groupId', String(groupId));
    return this.http.get<ResolvedThresholdResponse>('/api/academic/thresholds/resolve', {
      params,
    });
  }

  /**
   * Submit a geo check-in. Server resolves the target lesson from the
   * authenticated student's group + current time — clients cannot select
   * which lesson to mark (T-51-02 mitigation).
   */
  checkin(coords: CheckinRequest): Observable<CheckinResponse> {
    return this.http.post<CheckinResponse>('/api/attendance/checkin', coords);
  }

  /** Fetch homeworks for the student's group in the given semester. */
  getHomeworks(groupId: number, semesterId: number): Observable<HomeworkItem[]> {
    const params = new HttpParams()
      .set('groupId', String(groupId))
      .set('semesterId', String(semesterId))
      .set('size', '50');
    return this.http
      .get<PagedResponse<HomeworkItem>>('/api/academic/homeworks', { params })
      .pipe(map(resp => resp._embedded?.['homeworkResponseList'] ?? []));
  }

  /** Mark a homework item as complete for the authenticated student. */
  markHomeworkComplete(id: number): Observable<void> {
    return this.http.post<void>(`/api/academic/homeworks/${id}/complete`, {});
  }

  /** Remove the completion mark for a homework item. */
  unmarkHomeworkComplete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/academic/homeworks/${id}/complete`);
  }

  /** Fetch all semesters and return the ID of the currently active one, or null. */
  getActiveSemesterId(): Observable<number | null> {
    const params = new HttpParams().set('size', '100');
    return this.http
      .get<PagedResponse<{ id: number; active: boolean }>>('/api/academic/semesters', { params })
      .pipe(
        map(resp => {
          const list = resp._embedded?.['semesterResponseList'] ?? [];
          const active = list.find(s => s.active);
          return active?.id ?? null;
        }),
      );
  }
}
