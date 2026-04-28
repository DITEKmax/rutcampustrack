import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { EMPTY, Observable, of, throwError } from 'rxjs';
import { catchError, expand, map, reduce } from 'rxjs/operators';
import {
  ExcuseTicket,
  ExcuseTicketStatus,
  PagedExcuseResponse,
} from '../excuses/excuse.types';
import { LateCheckinRequestView } from '../late-checkin/late-checkin.types';

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
  private static readonly SCHEDULE_PAGE_SIZE = 200;

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
      params: new HttpParams().set('status', 'submitted'),
    });
  }

  /**
   * Fetch late-checkin requests for the headman's group.
   * Endpoint: GET /api/attendance/late-checkin/group/{groupId}?size=100
   * Includes pending and decided rows updated during the backend retention window.
   * 403/404 -> empty list (graceful degradation).
   */
  getGroupLateCheckins(groupId: number): Observable<LateCheckinRequestView[]> {
    return this.http
      .get<any>(`/api/attendance/late-checkin/group/${groupId}`, {
        params: new HttpParams().set('size', '100'),
      })
      .pipe(
        map(resp => {
          if (Array.isArray(resp)) return resp as LateCheckinRequestView[];
          const embedded = resp?._embedded;
          if (!embedded) return [];
          return (
            (embedded['lateCheckinRequestResponseList'] as LateCheckinRequestView[]) ??
            (Object.values(embedded)[0] as LateCheckinRequestView[]) ??
            []
          );
        }),
        catchError((err: HttpErrorResponse) => {
          if (err.status === 403 || err.status === 404) {
            return of([] as LateCheckinRequestView[]);
          }
          return throwError(() => err);
        }),
      );
  }

  /**
   * Fetch pending late-checkin requests for dashboard counters.
   */
  getPendingLateCheckins(): Observable<LateCheckinRequestView[]> {
    return this.http
      .get<any>('/api/attendance/late-checkin/pending')
      .pipe(
        map(resp => {
          if (Array.isArray(resp)) return resp as LateCheckinRequestView[];
          const embedded = resp?._embedded;
          if (!embedded) return [];
          return (
            (embedded['lateCheckinRequestResponseList'] as LateCheckinRequestView[]) ??
            (Object.values(embedded)[0] as LateCheckinRequestView[]) ??
            []
          );
        }),
        catchError((err: HttpErrorResponse) => {
          if (err.status === 403 || err.status === 404) {
            return of([] as LateCheckinRequestView[]);
          }
          return throwError(() => err);
        }),
      );
  }

  /**
   * Approve or reject a late-checkin request (web channel).
   * Endpoint: POST /api/attendance/late-checkin/{requestId}/decision
   * Body: { approved: boolean }.
   * Idempotent on the backend — repeated decisions on an already-decided request are no-ops.
   */
  decideLateCheckin(requestId: string, approved: boolean): Observable<LateCheckinRequestView> {
    return this.http.post<LateCheckinRequestView>(
      `/api/attendance/late-checkin/${requestId}/decision`,
      { approved },
    );
  }

  /**
   * Fetch excuse tickets for the headman's group (Phase 59, D-23).
   * Endpoint: GET /api/attendance/excuses/group/{groupId}?status=...&size=50
   * Returns unwrapped ExcuseTicket[]. Spring HATEOAS использует имя DTO для
   * ключа в `_embedded` — без @Relation получаем `excuseTicketResponseList`.
   * Старое имя `excuseTicketList` оставлено как fallback для совместимости,
   * плюс `Object.values(embedded)[0]` — защита от переименования DTO.
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
        map(resp => {
          const embedded = resp?._embedded as Record<string, unknown> | undefined;
          if (!embedded) return [] as ExcuseTicket[];
          return (
            (embedded['excuseTicketResponseList'] as ExcuseTicket[]) ??
            (embedded['excuseTicketList'] as ExcuseTicket[]) ??
            (Object.values(embedded)[0] as ExcuseTicket[]) ??
            []
          );
        }),
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

  /**
   * Delete a subject by ID. Без force — backend вернёт 409 с `extras`
   * (счётчики касающихся lessons), если удаление может потерять посещаемость.
   * С `force=true` pre-check пропускается и каскад идёт до конца.
   */
  deleteSubject(id: number, force = false): Observable<any> {
    let params = new HttpParams();
    if (force) params = params.set('force', 'true');
    return this.http.delete(`/api/academic/subjects/${id}`, { params });
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
   * Fetch the attendance entries for a single lesson (weekly-journal page).
   * Endpoint: GET /api/attendance/reports/lesson/{lessonId}
   * Returns LessonAttendanceResponse wrapped in EntityModel (payload under .content or flat).
   */
  getLessonAttendance(lessonId: number): Observable<any> {
    return this.http.get(`/api/attendance/reports/lesson/${lessonId}`);
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

  // Schedule management (Plan 60-07 — /headman/schedule page)

  /**
   * List all semesters (paged). Headman page uses this to pick the active one.
   * Endpoint: GET /api/academic/semesters?size=200
   */
  listSemesters(): Observable<any> {
    return this.http.get('/api/academic/semesters', {
      params: new HttpParams().set('size', '200'),
    });
  }

  /**
   * Load schedule template (ScheduleItem list) for a given group + semester.
   * Endpoint: GET /api/schedule/items?groupId=X&semesterId=Y
   * Backend contract `ScheduleItemApi` (Plan 60-02) — returns PagedModel.
   */
  getGroupScheduleItems(groupId: number, semesterId: number): Observable<any> {
    return this.http.get('/api/schedule/items', {
      params: new HttpParams()
        .set('groupId', groupId)
        .set('semesterId', semesterId)
        .set('size', '200'),
    });
  }

  /**
   * Create a schedule template slot (HEADMAN only — own group).
   * Endpoint: POST /api/schedule/items
   */
  createScheduleItem(body: {
    groupId: number;
    subjectId: number;
    semesterId: number;
    dayOfWeek: number;
    lessonNumber: number;
    startTime: string;
    endTime: string;
    weekType: string;
    room?: string;
  }): Observable<any> {
    return this.http.post('/api/schedule/items', body);
  }

  /**
   * Full-update a template slot (HEADMAN only — own group).
   * Endpoint: PUT /api/schedule/items/{id}
   */
  updateScheduleItem(id: number, body: {
    subjectId: number;
    dayOfWeek: number;
    lessonNumber: number;
    startTime: string;
    endTime: string;
    weekType: string;
    room?: string;
  }): Observable<any> {
    return this.http.put(`/api/schedule/items/${id}`, body);
  }

  /** Soft-delete (deactivate) a template slot. */
  deleteScheduleItem(id: number): Observable<any> {
    return this.http.delete(`/api/schedule/items/${id}`);
  }

  /**
   * Create a one-off lesson for a specific date (Plan 60-03).
   * Endpoint: POST /api/schedule/one-off-lessons
   * 409 if a template slot is active on that (date, lessonNumber) — see D-09.
   */
  createOneOffLesson(body: {
    groupId: number;
    subjectId: number;
    date: string;
    lessonNumber: number;
    classroom?: string;
  }): Observable<any> {
    return this.http.post('/api/schedule/one-off-lessons', body);
  }

  /** Delete a one-off lesson (D-22 — any date). */
  deleteOneOffLesson(id: number): Observable<any> {
    return this.http.delete(`/api/schedule/one-off-lessons/${id}`);
  }

  /** List one-off lessons for a group within [dateFrom..dateTo]. */
  getOneOffLessons(groupId: number, dateFrom: string, dateTo: string): Observable<any> {
    return this.http.get('/api/schedule/one-off-lessons', {
      params: new HttpParams()
        .set('groupId', groupId)
        .set('dateFrom', dateFrom)
        .set('dateTo', dateTo),
    });
  }

  /**
   * Cancel a concrete lesson on a specific date.
   * Endpoint: PATCH /api/schedule/lessons/{id}/cancel
   * Body: { reason: string } (NotBlank, max 512)
   */
  cancelLesson(lessonId: number, reason: string): Observable<any> {
    return this.http.patch(`/api/schedule/lessons/${lessonId}/cancel`, { reason });
  }

  /**
   * Restore a previously cancelled lesson.
   * Endpoint: PATCH /api/schedule/lessons/{id}/restore
   */
  restoreLesson(lessonId: number): Observable<any> {
    return this.http.patch(`/api/schedule/lessons/${lessonId}/restore`, {});
  }

  /**
   * List concrete lessons for a group within [dateFrom..dateTo] (inclusive).
   * Endpoint: GET /api/schedule/groups/{groupId}/lessons
   * Optional `status` filter: planned|active|closed|cancelled (lowercase per project convention).
   */
  getGroupLessons(
    groupId: number,
    dateFrom: string,
    dateTo: string,
    status?: string,
  ): Observable<any> {
    return this.getGroupLessonsPage(
      groupId,
      dateFrom,
      dateTo,
      status,
      0,
      HeadmanApiService.SCHEDULE_PAGE_SIZE,
    ).pipe(
      expand((pageResp: any) => {
        const currentPage = Number(pageResp?.page?.number ?? 0);
        const totalPages = Number(pageResp?.page?.totalPages ?? 1);
        if (!Number.isFinite(currentPage) || !Number.isFinite(totalPages)) {
          return EMPTY;
        }
        const nextPage = currentPage + 1;
        return nextPage < totalPages
          ? this.getGroupLessonsPage(
              groupId,
              dateFrom,
              dateTo,
              status,
              nextPage,
              HeadmanApiService.SCHEDULE_PAGE_SIZE,
            )
          : EMPTY;
      }),
      reduce((pages: any[], pageResp: any) => [...pages, pageResp], []),
      map((pages: any[]) => this.mergePagedEmbedded(pages, 'lessonResponseList')),
    );
  }

  private getGroupLessonsPage(
    groupId: number,
    dateFrom: string,
    dateTo: string,
    status: string | undefined,
    page: number,
    size: number,
  ): Observable<any> {
    let params = new HttpParams()
      .set('dateFrom', dateFrom)
      .set('dateTo', dateTo)
      .set('page', String(page))
      .set('size', String(size));
    if (status) params = params.set('status', status);
    return this.http.get(`/api/schedule/groups/${groupId}/lessons`, { params });
  }

  private mergePagedEmbedded(pages: any[], fallbackKey: string): any {
    const first = pages[0] ?? {};
    const embeddedKey = this.firstEmbeddedKey(pages) ?? fallbackKey;
    const mergedItems = pages.flatMap(page => {
      const embedded = page?._embedded;
      if (!embedded) return [];
      const value = embedded[embeddedKey] ?? Object.values(embedded)[0];
      return Array.isArray(value) ? value : [];
    });

    return {
      ...first,
      _embedded: {
        ...(first._embedded ?? {}),
        [embeddedKey]: mergedItems,
      },
      page: {
        ...(first.page ?? {}),
        size: mergedItems.length,
        totalElements: mergedItems.length,
        totalPages: 1,
        number: 0,
      },
    };
  }

  private firstEmbeddedKey(pages: any[]): string | null {
    for (const page of pages) {
      const embedded = page?._embedded;
      if (!embedded) continue;
      const [key] = Object.keys(embedded);
      if (key) return key;
    }
    return null;
  }

  /**
   * Batch-резолв display-имён пользователей по ID (DEV-C2 backend endpoint).
   * Endpoint: GET /api/academic/users/by-ids?ids=1,2,3
   * Доступ STUDENT/TEACHER/ADMIN. Cap 100 ids/request на бэкенде.
   * Используется для подстановки имени отменившего на странице /headman/lessons.
   */
  getUsersByIds(ids: number[]): Observable<any> {
    return this.http.get('/api/academic/users/by-ids', {
      params: new HttpParams().set('ids', ids.join(',')),
    });
  }
}
