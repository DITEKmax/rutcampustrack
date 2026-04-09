import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

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

  /** Create a new subject with optional teacher assignment. */
  createSubject(body: { name: string; teacherId?: number | null }): Observable<any> {
    return this.http.post('/api/academic/subjects', body);
  }

  /** Full-update a subject (PUT = full replacement). */
  updateSubject(id: number, body: { name: string; teacherId?: number | null }): Observable<any> {
    return this.http.put(`/api/academic/subjects/${id}`, body);
  }

  /** Delete a subject by ID. */
  deleteSubject(id: number): Observable<any> {
    return this.http.delete(`/api/academic/subjects/${id}`);
  }

  // Teacher listing

  /** List all teachers — new endpoint added in Plan 1 (academic-service). */
  listTeachers(): Observable<any> {
    return this.http.get('/api/academic/users/teachers');
  }
}
