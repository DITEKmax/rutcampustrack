import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import {
  ExcuseTicket,
  ExcuseTicketStatus,
  PagedExcuseResponse,
} from '../excuses/excuse.types';

/**
 * Shared HttpClient wrapper for all headman-cabinet REST calls.
 *
 * All URLs are absolute `/api/...` paths so the deployed nginx reverse proxy
 * and the Angular dev proxy both route them to the API Gateway. The Bearer
 * token is injected by the global authInterceptor (see app.config.ts) — no
 * manual header handling needed here. The gateway derives X-Is-Headman from
 * the JWT claim, so no additional headers are injected by Angular.
 *
 * Method-to-endpoint map:
 * - getGroupMembers         → academic-service  (members of the headman's group)
 * - getTodayLessons         → schedule-service  (lessons for a group on today's date)
 * - getPendingExcuses       → academic-service  (deferred — graceful 404 degradation)
 * - getPendingLateCheckins  → academic-service  (deferred — graceful 404 degradation)
 * - listAssistants          → academic-service  (assistants for a group)
 * - assignAssistant         → academic-service  (assign a student as assistant)
 * - updateAssistantPermissions → academic-service (patch assistant permissions)
 * - revokeAssistant         → academic-service  (remove an assistant)
 * - listSubjects            → academic-service  (institution-wide subjects list)
 * - createSubject           → academic-service  (create a new subject)
 * - updateSubject           → academic-service  (full-update a subject)
 * - deleteSubject           → academic-service  (delete a subject)
 * - listTeachers            → academic-service  (all teachers — new endpoint from Plan 1)
 */
@Injectable({ providedIn: 'root' })
export class HeadmanApiService {
  private readonly http = inject(HttpClient);

  // Dashboard data (forkJoin sources — no dedicated headman dashboard endpoint exists)

  /**
   * Fetch members of the headman's group (paginated).
   * Endpoint derives group from JWT — no groupId param needed.
   */
  getGroupMembers(page = 0, size = 50): Observable<any> {
    return this.http.get('/api/academic/groups/my/members', {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  /** Fetch today's lessons for a specific group. */
  getTodayLessons(groupId: number): Observable<any> {
    const today = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
    return this.http.get(`/api/schedule/groups/${groupId}/lessons`, {
      params: new HttpParams().set('dateFrom', today).set('dateTo', today),
    });
  }

  /**
   * Fetch pending excuse tickets awaiting headman review.
   * Backend endpoint is deferred from v5.0 — HTTP 404 returns graceful degradation.
   */
  getPendingExcuses(): Observable<any> {
    return this.http.get('/api/academic/headman/excuses', {
      params: new HttpParams().set('status', 'pending'),
    });
  }

  /**
   * Fetch excuse tickets for the headman's group (Phase 59, D-23).
   * Endpoint: GET /api/attendance/excuses/group/{groupId}?status=...&size=50
   * Returns unwrapped ExcuseTicket[] (extracts _embedded.excuseTicketList).
   * 403/404 → empty list (graceful degradation while backend rolls out).
   * Other errors propagate.
   */
  getGroupExcuses(groupId: number, status?: ExcuseTicketStatus | string): Observable<ExcuseTicket[]> {
    let params = new HttpParams().set('size', '50');
    if (status) {
      params = params.set('status', status);
    }
    return this.http
      .get<PagedExcuseResponse>(`/api/attendance/excuses/group/${groupId}`, { params })
      .pipe(
        map(resp => resp?._embedded?.excuseTicketList ?? []),
        catchError((err: HttpErrorResponse) => {
          if (err.status === 403 || err.status === 404) {
            return of([] as ExcuseTicket[]);
          }
          return throwError(() => err);
        }),
      );
  }

  /**
   * Approve an excuse ticket (Phase 59, D-23).
   * Endpoint: PATCH /api/attendance/excuses/{id}/status
   * Body: { status: 'approved', decisionComment?: string | null }
   * Backend cascades approval onto attendance records (D-16).
   */
  approveExcuse(id: string, decisionComment?: string | null): Observable<void> {
    return this.http.patch<void>(`/api/attendance/excuses/${id}/status`, {
      status: 'approved',
      decisionComment: decisionComment ?? null,
    });
  }

  /**
   * Reject an excuse ticket (Phase 59, D-24).
   * Endpoint: PATCH /api/attendance/excuses/{id}/status
   * Body: { status: 'rejected', decisionComment: string }
   * decisionComment is required on rejection (enforced in caller).
   */
  rejectExcuse(id: string, decisionComment: string): Observable<void> {
    return this.http.patch<void>(`/api/attendance/excuses/${id}/status`, {
      status: 'rejected',
      decisionComment,
    });
  }

  /**
   * Fetch pending late check-in requests awaiting headman review.
   * Backend endpoint is deferred from v5.0 — HTTP 404 returns graceful degradation.
   */
  getPendingLateCheckins(): Observable<any> {
    return this.http.get('/api/academic/headman/late-checkins', {
      params: new HttpParams().set('status', 'pending'),
    });
  }

  // Group management

  /** List assistants for a given group. */
  listAssistants(groupId: number): Observable<any> {
    return this.http.get('/api/academic/assistants', {
      params: new HttpParams().set('groupId', groupId),
    });
  }

  /** Assign a student as headman assistant with given permissions. */
  assignAssistant(body: { studentId: number; permissions: string[] }): Observable<any> {
    return this.http.post('/api/academic/assistants', body);
  }

  /** Update permissions for an existing assistant. */
  updateAssistantPermissions(id: number, body: { permissions: string[] }): Observable<any> {
    return this.http.patch(`/api/academic/assistants/${id}/permissions`, body);
  }

  /** Revoke assistant role from a student. */
  revokeAssistant(id: number): Observable<any> {
    return this.http.delete(`/api/academic/assistants/${id}`);
  }

  // Subjects

  /**
   * List all subjects (institution-wide, no groupId filter).
   * NOTE: SubjectApi.listSubjects() has NO groupId filter per research Key Finding B.
   * Subjects are institution-wide.
   */
  listSubjects(page = 0, size = 50): Observable<any> {
    return this.http.get('/api/academic/subjects', {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  /**
   * Create a new subject with a list of teachers.
   * Backend (Plan 60-01) atomically creates 1 `subjects` row + N `teacher_subject_groups`.
   * `teacherIds` may be empty (subject without teachers is allowed).
   * `type` is required: LECTURE / PRACTICE / LAB.
   */
  createSubject(body: { name: string; type: string; teacherIds: number[] }): Observable<any> {
    return this.http.post('/api/academic/subjects', body);
  }

  /**
   * Full-update a subject (PUT = full replacement).
   * `teacherIds[]` replaces the current teacher assignments for the current semester.
   */
  updateSubject(id: number, body: { name: string; type: string; teacherIds: number[] }): Observable<any> {
    return this.http.put(`/api/academic/subjects/${id}`, body);
  }

  /** Delete a subject by ID. */
  deleteSubject(id: number): Observable<any> {
    return this.http.delete(`/api/academic/subjects/${id}`);
  }

  /**
   * Add a single teacher to an existing subject (Plan 60-01 endpoint).
   * Endpoint: POST /api/academic/subjects/{subjectId}/teachers/{teacherId}
   * 201 on success, 409 if already assigned.
   */
  addTeacherToSubject(subjectId: number, teacherId: number): Observable<void> {
    return this.http.post<void>(`/api/academic/subjects/${subjectId}/teachers/${teacherId}`, {});
  }

  /**
   * Remove a teacher assignment from an existing subject (Plan 60-01 endpoint).
   * Endpoint: DELETE /api/academic/subjects/{subjectId}/teachers/{teacherId}
   * 204 on success, 404 if no assignment exists.
   */
  removeTeacherFromSubject(subjectId: number, teacherId: number): Observable<void> {
    return this.http.delete<void>(`/api/academic/subjects/${subjectId}/teachers/${teacherId}`);
  }

  // Teacher listing

  /** List all teachers — new endpoint added in Plan 1 (academic-service). */
  listTeachers(): Observable<any> {
    return this.http.get('/api/academic/users/teachers');
  }

  // Attendance & Stats

  /**
   * Fetch the attendance journal for the headman's group and a specific subject.
   * Endpoint: GET /api/attendance/reports/journal?groupId=X&subjectId=Y&dateFrom=Z&dateTo=W
   * Per D-05: same endpoint as teacher journal (JournalApiService.getJournal).
   */
  getJournal(groupId: number, subjectId: number, dateFrom: string, dateTo: string): Observable<any> {
    return this.http.get('/api/attendance/reports/journal', {
      params: new HttpParams()
        .set('groupId', groupId)
        .set('subjectId', subjectId)
        .set('dateFrom', dateFrom)
        .set('dateTo', dateTo),
    });
  }

  /**
   * Mark or update attendance status for a student on a specific lesson.
   * Endpoint: PUT /api/attendance/lessons/{lessonId}/students/{userId}
   * Body: { status: AttendanceStatus string }
   * Per D-05: status must be one of: present, absent, excused, free_attendance
   * (MarkingService.ALLOWED_STATUSES excludes cancelled — do not send cancelled).
   */
  markAttendance(lessonId: number, userId: number, status: string): Observable<any> {
    return this.http.put(`/api/attendance/lessons/${lessonId}/students/${userId}`, { status });
  }

  /**
   * Resolve the effective red-zone threshold for a group+subject combination.
   * Endpoint: GET /api/academic/thresholds/resolve?groupId=X&subjectId=Y
   * Returns: { minPercentage: number, level: string, sourceId: number }
   * Per D-11.
   */
  resolveThreshold(groupId: number, subjectId: number): Observable<any> {
    return this.http.get('/api/academic/thresholds/resolve', {
      params: new HttpParams().set('groupId', groupId).set('subjectId', subjectId),
    });
  }

  /**
   * Set the per-subject red-zone threshold for the headman's group.
   * Endpoint: PUT /api/academic/thresholds/subject?subjectId=Y
   * Body: { minPercentage: number } (integer 0-100)
   * Per D-11.
   */
  setSubjectThreshold(subjectId: number, minPercentage: number): Observable<any> {
    return this.http.put('/api/academic/thresholds/subject', { minPercentage }, {
      params: new HttpParams().set('subjectId', subjectId),
    });
  }
}
