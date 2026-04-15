import {
  HttpClient,
  HttpParams
} from "./chunk-FLE4DLW4.js";
import {
  Injectable,
  catchError,
  inject,
  map,
  of,
  setClassMetadata,
  throwError,
  ɵɵdefineInjectable
} from "./chunk-4M4FDLBS.js";

// src/app/features/headman/shared/headman-api.service.ts
var HeadmanApiService = class _HeadmanApiService {
  http = inject(HttpClient);
  // Dashboard data (forkJoin sources — no dedicated headman dashboard endpoint exists)
  /**
   * Fetch members of the headman's group (paginated).
   * Endpoint derives group from JWT — no groupId param needed.
   */
  getGroupMembers(page = 0, size = 50) {
    return this.http.get("/api/academic/groups/my/members", {
      params: new HttpParams().set("page", page).set("size", size)
    });
  }
  /** Fetch today's lessons for a specific group. */
  getTodayLessons(groupId) {
    const today = (/* @__PURE__ */ new Date()).toISOString().split("T")[0];
    return this.http.get(`/api/schedule/groups/${groupId}/lessons`, {
      params: new HttpParams().set("dateFrom", today).set("dateTo", today)
    });
  }
  /**
   * Fetch pending excuse tickets awaiting headman review.
   * Backend endpoint is deferred from v5.0 — HTTP 404 returns graceful degradation.
   */
  getPendingExcuses() {
    return this.http.get("/api/academic/headman/excuses", {
      params: new HttpParams().set("status", "submitted")
    });
  }
  /**
   * Fetch excuse tickets for the headman's group (Phase 59, D-23).
   * Endpoint: GET /api/attendance/excuses/group/{groupId}?status=...&size=50
   * Returns unwrapped ExcuseTicket[] (extracts _embedded.excuseTicketList).
   * 403/404 → empty list (graceful degradation while backend rolls out).
   * Other errors propagate.
   */
  getGroupExcuses(groupId, status) {
    let params = new HttpParams().set("size", "50");
    if (status) {
      params = params.set("status", status);
    }
    return this.http.get(`/api/attendance/excuses/group/${groupId}`, { params }).pipe(map((resp) => resp?._embedded?.excuseTicketList ?? []), catchError((err) => {
      if (err.status === 403 || err.status === 404) {
        return of([]);
      }
      return throwError(() => err);
    }));
  }
  /**
   * Approve an excuse ticket (Phase 59, D-23).
   * Endpoint: PATCH /api/attendance/excuses/{id}/status
   * Body: { status: 'approved', decisionComment?: string | null }
   * Backend cascades approval onto attendance records (D-16).
   */
  approveExcuse(id, decisionComment) {
    return this.http.patch(`/api/attendance/excuses/${id}/status`, {
      status: "approved",
      decisionComment: decisionComment ?? null
    });
  }
  /**
   * Reject an excuse ticket (Phase 59, D-24).
   * Endpoint: PATCH /api/attendance/excuses/{id}/status
   * Body: { status: 'rejected', decisionComment: string }
   * decisionComment is required on rejection (enforced in caller).
   */
  rejectExcuse(id, decisionComment) {
    return this.http.patch(`/api/attendance/excuses/${id}/status`, {
      status: "rejected",
      decisionComment
    });
  }
  // Group management
  /** List assistants for a given group. */
  listAssistants(groupId) {
    return this.http.get("/api/academic/assistants", {
      params: new HttpParams().set("groupId", groupId)
    });
  }
  /** Assign a student as headman assistant with given permissions. */
  assignAssistant(body) {
    return this.http.post("/api/academic/assistants", body);
  }
  /** Update permissions for an existing assistant. */
  updateAssistantPermissions(id, body) {
    return this.http.patch(`/api/academic/assistants/${id}/permissions`, body);
  }
  /** Revoke assistant role from a student. */
  revokeAssistant(id) {
    return this.http.delete(`/api/academic/assistants/${id}`);
  }
  // Subjects
  /**
   * List all subjects (institution-wide, no groupId filter).
   * NOTE: SubjectApi.listSubjects() has NO groupId filter per research Key Finding B.
   * Subjects are institution-wide.
   */
  listSubjects(page = 0, size = 50) {
    return this.http.get("/api/academic/subjects", {
      params: new HttpParams().set("page", page).set("size", size)
    });
  }
  /**
   * Create a new subject with a list of teachers.
   * Backend (Plan 60-01) atomically creates 1 `subjects` row + N `teacher_subject_groups`.
   * `teacherIds` may be empty (subject without teachers is allowed).
   * `type` is required: LECTURE / PRACTICE / LAB.
   */
  createSubject(body) {
    return this.http.post("/api/academic/subjects", body);
  }
  /**
   * Full-update a subject (PUT = full replacement).
   * `teacherIds[]` replaces the current teacher assignments for the current semester.
   */
  updateSubject(id, body) {
    return this.http.put(`/api/academic/subjects/${id}`, body);
  }
  /** Delete a subject by ID. */
  deleteSubject(id) {
    return this.http.delete(`/api/academic/subjects/${id}`);
  }
  /**
   * Add a single teacher to an existing subject (Plan 60-01 endpoint).
   * Endpoint: POST /api/academic/subjects/{subjectId}/teachers/{teacherId}
   * 201 on success, 409 if already assigned.
   */
  addTeacherToSubject(subjectId, teacherId) {
    return this.http.post(`/api/academic/subjects/${subjectId}/teachers/${teacherId}`, {});
  }
  /**
   * Remove a teacher assignment from an existing subject (Plan 60-01 endpoint).
   * Endpoint: DELETE /api/academic/subjects/{subjectId}/teachers/{teacherId}
   * 204 on success, 404 if no assignment exists.
   */
  removeTeacherFromSubject(subjectId, teacherId) {
    return this.http.delete(`/api/academic/subjects/${subjectId}/teachers/${teacherId}`);
  }
  // Teacher listing
  /** List all teachers — new endpoint added in Plan 1 (academic-service). */
  listTeachers() {
    return this.http.get("/api/academic/users/teachers");
  }
  // Attendance & Stats
  /**
   * Fetch the attendance journal for the headman's group and a specific subject.
   * Endpoint: GET /api/attendance/reports/journal?groupId=X&subjectId=Y&dateFrom=Z&dateTo=W
   * Per D-05: same endpoint as teacher journal (JournalApiService.getJournal).
   */
  getJournal(groupId, subjectId, dateFrom, dateTo) {
    return this.http.get("/api/attendance/reports/journal", {
      params: new HttpParams().set("groupId", groupId).set("subjectId", subjectId).set("dateFrom", dateFrom).set("dateTo", dateTo)
    });
  }
  /**
   * Fetch the attendance entries for a single lesson (weekly-journal page).
   * Endpoint: GET /api/attendance/reports/lesson/{lessonId}
   * Returns LessonAttendanceResponse wrapped in EntityModel (payload under .content or flat).
   */
  getLessonAttendance(lessonId) {
    return this.http.get(`/api/attendance/reports/lesson/${lessonId}`);
  }
  /**
   * Mark or update attendance status for a student on a specific lesson.
   * Endpoint: PUT /api/attendance/lessons/{lessonId}/students/{userId}
   * Body: { status: AttendanceStatus string }
   * Per D-05: status must be one of: present, absent, excused, free_attendance
   * (MarkingService.ALLOWED_STATUSES excludes cancelled — do not send cancelled).
   */
  markAttendance(lessonId, userId, status) {
    return this.http.put(`/api/attendance/lessons/${lessonId}/students/${userId}`, { status });
  }
  /**
   * Resolve the effective red-zone threshold for a group+subject combination.
   * Endpoint: GET /api/academic/thresholds/resolve?groupId=X&subjectId=Y
   * Returns: { minPercentage: number, level: string, sourceId: number }
   * Per D-11.
   */
  resolveThreshold(groupId, subjectId) {
    return this.http.get("/api/academic/thresholds/resolve", {
      params: new HttpParams().set("groupId", groupId).set("subjectId", subjectId)
    });
  }
  /**
   * Set the per-subject red-zone threshold for the headman's group.
   * Endpoint: PUT /api/academic/thresholds/subject?subjectId=Y
   * Body: { minPercentage: number } (integer 0-100)
   * Per D-11.
   */
  setSubjectThreshold(subjectId, minPercentage) {
    return this.http.put("/api/academic/thresholds/subject", { minPercentage }, {
      params: new HttpParams().set("subjectId", subjectId)
    });
  }
  // Schedule management (Plan 60-07 — /headman/schedule page)
  /**
   * List all semesters (paged). Headman page uses this to pick the active one.
   * Endpoint: GET /api/academic/semesters?size=200
   */
  listSemesters() {
    return this.http.get("/api/academic/semesters", {
      params: new HttpParams().set("size", "200")
    });
  }
  /**
   * Load schedule template (ScheduleItem list) for a given group + semester.
   * Endpoint: GET /api/schedule/items?groupId=X&semesterId=Y
   * Backend contract `ScheduleItemApi` (Plan 60-02) — returns PagedModel.
   */
  getGroupScheduleItems(groupId, semesterId) {
    return this.http.get("/api/schedule/items", {
      params: new HttpParams().set("groupId", groupId).set("semesterId", semesterId).set("size", "200")
    });
  }
  /**
   * Create a schedule template slot (HEADMAN only — own group).
   * Endpoint: POST /api/schedule/items
   */
  createScheduleItem(body) {
    return this.http.post("/api/schedule/items", body);
  }
  /**
   * Full-update a template slot (HEADMAN only — own group).
   * Endpoint: PUT /api/schedule/items/{id}
   */
  updateScheduleItem(id, body) {
    return this.http.put(`/api/schedule/items/${id}`, body);
  }
  /** Soft-delete (deactivate) a template slot. */
  deleteScheduleItem(id) {
    return this.http.delete(`/api/schedule/items/${id}`);
  }
  /**
   * Create a one-off lesson for a specific date (Plan 60-03).
   * Endpoint: POST /api/schedule/one-off-lessons
   * 409 if a template slot is active on that (date, lessonNumber) — see D-09.
   */
  createOneOffLesson(body) {
    return this.http.post("/api/schedule/one-off-lessons", body);
  }
  /** Delete a one-off lesson (D-22 — any date). */
  deleteOneOffLesson(id) {
    return this.http.delete(`/api/schedule/one-off-lessons/${id}`);
  }
  /** List one-off lessons for a group within [dateFrom..dateTo]. */
  getOneOffLessons(groupId, dateFrom, dateTo) {
    return this.http.get("/api/schedule/one-off-lessons", {
      params: new HttpParams().set("groupId", groupId).set("dateFrom", dateFrom).set("dateTo", dateTo)
    });
  }
  /**
   * Cancel a concrete lesson on a specific date.
   * Endpoint: PATCH /api/schedule/lessons/{id}/cancel
   * Body: { reason: string } (NotBlank, max 512)
   */
  cancelLesson(lessonId, reason) {
    return this.http.patch(`/api/schedule/lessons/${lessonId}/cancel`, { reason });
  }
  /**
   * Restore a previously cancelled lesson.
   * Endpoint: PATCH /api/schedule/lessons/{id}/restore
   */
  restoreLesson(lessonId) {
    return this.http.patch(`/api/schedule/lessons/${lessonId}/restore`, {});
  }
  /**
   * List concrete lessons for a group within [dateFrom..dateTo] (inclusive).
   * Endpoint: GET /api/schedule/groups/{groupId}/lessons
   * Optional `status` filter: planned|active|closed|cancelled (lowercase per project convention).
   */
  getGroupLessons(groupId, dateFrom, dateTo, status) {
    let params = new HttpParams().set("dateFrom", dateFrom).set("dateTo", dateTo).set("size", "500");
    if (status)
      params = params.set("status", status);
    return this.http.get(`/api/schedule/groups/${groupId}/lessons`, { params });
  }
  static \u0275fac = function HeadmanApiService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanApiService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _HeadmanApiService, factory: _HeadmanApiService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanApiService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], null, null);
})();

export {
  HeadmanApiService
};
//# sourceMappingURL=chunk-ZC6IXG25.js.map
