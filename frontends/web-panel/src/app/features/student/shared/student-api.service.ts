import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, of, throwError } from 'rxjs';
import type {
  AttendanceRecord,
  CheckinRequest,
  CheckinResponse,
  ExcuseTicket,
  ExcuseType,
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

  /**
   * Fetch the student's attendance records (all lessons with status).
   * Backend: GET /api/attendance/reports/student/records
   * HATEOAS embedded key: attendanceRecordEntryList (defensive fallback uses Object.values).
   */
  getStudentRecords(subjectId?: number): Observable<AttendanceRecord[]> {
    let params = new HttpParams();
    if (subjectId != null) params = params.set('subjectId', String(subjectId));
    return this.http
      .get<PagedResponse<AttendanceRecord>>(
        '/api/attendance/reports/student/records',
        { params },
      )
      .pipe(
        map(resp => {
          const embedded = resp._embedded;
          if (!embedded) return [];
          // Defensive: try known key first, then first array value
          return (
            embedded['attendanceRecordEntryList'] ??
            (Object.values(embedded)[0] as AttendanceRecord[]) ??
            []
          );
        }),
      );
  }

  /**
   * Fetch excuse tickets for the authenticated student (D-05, D-22).
   * Backend endpoint: GET /api/attendance/excuses/me (live since Phase 59-02).
   * HATEOAS embedded key: excuseTicketResponseList (defensive fallback uses Object.values).
   * HTTP 404 → graceful degradation (empty array) — should not occur in normal operation.
   */
  getExcuseTickets(): Observable<ExcuseTicket[]> {
    const params = new HttpParams().set('page', '0').set('size', '20');
    return this.http
      .get<any>('/api/attendance/excuses/me', { params })
      .pipe(
        map(resp => {
          if (Array.isArray(resp)) return resp as ExcuseTicket[];
          const embedded = resp?._embedded;
          if (!embedded) return [];
          return (
            (embedded['excuseTicketResponseList'] as ExcuseTicket[]) ??
            (embedded['excuseTicketList'] as ExcuseTicket[]) ??
            (Object.values(embedded)[0] as ExcuseTicket[]) ??
            []
          );
        }),
        catchError((err: HttpErrorResponse) => {
          if (err.status === 404) return of([]);
          return throwError(() => err);
        }),
      );
  }

  /**
   * Submit an excuse ticket (D-04, D-21).
   * Backend endpoint: POST /api/attendance/excuses — expects JSON body.
   * File attachments are OUT OF SCOPE for Phase 59 (D-03 — Telegram flow later).
   * HTTP 404 → graceful degradation (treated as success); other errors propagate.
   */
  submitExcuse(
    lessonIds: number[],
    excuseType: ExcuseType,
    comment: string | null,
  ): Observable<void> {
    const body = { lessonIds, excuseType, comment };
    return this.http.post<void>('/api/attendance/excuses', body).pipe(
      catchError((err: HttpErrorResponse) => {
        if (err.status === 404) return of(undefined);
        return throwError(() => err);
      }),
    );
  }

  /**
   * Request a late check-in for a specific absent lesson.
   * Backend endpoint (POST /api/attendance/late-checkin/{lessonId}) is deferred.
   * HTTP 404 → graceful degradation (treated as success).
   */
  requestLateCheckin(lessonId: number): Observable<void> {
    return this.http
      .post<void>(`/api/attendance/late-checkin/${lessonId}`, {})
      .pipe(
        catchError((err: HttpErrorResponse) => {
          if (err.status === 404) return of(undefined);
          return throwError(() => err);
        }),
      );
  }
}
