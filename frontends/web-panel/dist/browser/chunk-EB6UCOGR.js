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

// src/app/features/student/shared/student-api.service.ts
var StudentApiService = class _StudentApiService {
  http = inject(HttpClient);
  /**
   * Fetch a group's lessons for an inclusive date range.
   * Backend paginates at 100 per request; Phase 51 reads weeks (≤36 lessons).
   */
  getWeekLessons(groupId, dateFrom, dateTo) {
    const params = new HttpParams().set("dateFrom", dateFrom).set("dateTo", dateTo).set("size", "100");
    return this.http.get(`/api/schedule/groups/${groupId}/lessons`, { params }).pipe(map((resp) => resp._embedded?.["lessonResponseList"] ?? []));
  }
  /** Per-subject + overall attendance stats for the authenticated student. */
  getStudentStats() {
    return this.http.get("/api/attendance/reports/student/stats");
  }
  /** Effective global red-zone threshold (no params). */
  resolveGlobalThreshold() {
    return this.http.get("/api/academic/thresholds/resolve");
  }
  /** Effective group-level red-zone threshold (falls back to global server-side). */
  resolveGroupThreshold(groupId) {
    const params = new HttpParams().set("groupId", String(groupId));
    return this.http.get("/api/academic/thresholds/resolve", {
      params
    });
  }
  /**
   * Submit a geo check-in. Server resolves the target lesson from the
   * authenticated student's group + current time — clients cannot select
   * which lesson to mark (T-51-02 mitigation).
   */
  checkin(coords) {
    return this.http.post("/api/attendance/checkin", coords);
  }
  /** Fetch homeworks for the student's group in the given semester. */
  getHomeworks(groupId, semesterId) {
    const params = new HttpParams().set("groupId", String(groupId)).set("semesterId", String(semesterId)).set("size", "50");
    return this.http.get("/api/academic/homeworks", { params }).pipe(map((resp) => resp._embedded?.["homeworkResponseList"] ?? []));
  }
  /** Mark a homework item as complete for the authenticated student. */
  markHomeworkComplete(id) {
    return this.http.post(`/api/academic/homeworks/${id}/complete`, {});
  }
  /** Remove the completion mark for a homework item. */
  unmarkHomeworkComplete(id) {
    return this.http.delete(`/api/academic/homeworks/${id}/complete`);
  }
  /** Fetch all semesters and return the ID of the currently active one, or null. */
  getActiveSemesterId() {
    const params = new HttpParams().set("size", "100");
    return this.http.get("/api/academic/semesters", { params }).pipe(map((resp) => {
      const list = resp._embedded?.["semesterResponseList"] ?? [];
      const active = list.find((s) => s.active);
      return active?.id ?? null;
    }));
  }
  /**
   * Fetch the student's attendance records (all lessons with status).
   * Backend: GET /api/attendance/reports/student/records
   * HATEOAS embedded key: attendanceRecordEntryList (defensive fallback uses Object.values).
   */
  getStudentRecords(subjectId) {
    let params = new HttpParams();
    if (subjectId != null)
      params = params.set("subjectId", String(subjectId));
    return this.http.get("/api/attendance/reports/student/records", { params }).pipe(map((resp) => {
      const embedded = resp._embedded;
      if (!embedded)
        return [];
      return embedded["attendanceRecordEntryList"] ?? Object.values(embedded)[0] ?? [];
    }));
  }
  /**
   * Fetch excuse tickets for the authenticated student (D-05, D-22).
   * Backend endpoint: GET /api/attendance/excuses/me (live since Phase 59-02).
   * HATEOAS embedded key: excuseTicketResponseList (defensive fallback uses Object.values).
   * HTTP 404 → graceful degradation (empty array) — should not occur in normal operation.
   */
  getExcuseTickets() {
    const params = new HttpParams().set("page", "0").set("size", "20");
    return this.http.get("/api/attendance/excuses/me", { params }).pipe(map((resp) => {
      if (Array.isArray(resp))
        return resp;
      const embedded = resp?._embedded;
      if (!embedded)
        return [];
      return embedded["excuseTicketResponseList"] ?? embedded["excuseTicketList"] ?? Object.values(embedded)[0] ?? [];
    }), catchError((err) => {
      if (err.status === 404)
        return of([]);
      return throwError(() => err);
    }));
  }
  /**
   * Submit an excuse ticket (D-04, D-21).
   * Backend endpoint: POST /api/attendance/excuses — expects JSON body.
   * File attachments are OUT OF SCOPE for Phase 59 (D-03 — Telegram flow later).
   * HTTP 404 → graceful degradation (treated as success); other errors propagate.
   */
  submitExcuse(lessonIds, excuseType, comment) {
    const body = { lessonIds, excuseType, comment };
    return this.http.post("/api/attendance/excuses", body).pipe(catchError((err) => {
      if (err.status === 404)
        return of(void 0);
      return throwError(() => err);
    }));
  }
  /**
   * Request a late check-in for a specific absent lesson.
   * Backend endpoint (POST /api/attendance/late-checkin/{lessonId}) is deferred.
   * HTTP 404 → graceful degradation (treated as success).
   */
  requestLateCheckin(lessonId) {
    return this.http.post(`/api/attendance/late-checkin/${lessonId}`, {}).pipe(catchError((err) => {
      if (err.status === 404)
        return of(void 0);
      return throwError(() => err);
    }));
  }
  static \u0275fac = function StudentApiService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentApiService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _StudentApiService, factory: _StudentApiService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentApiService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], null, null);
})();

export {
  StudentApiService
};
//# sourceMappingURL=chunk-EB6UCOGR.js.map
