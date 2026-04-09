import {
  HttpClient,
  HttpParams
} from "./chunk-T4R75CF5.js";
import {
  Injectable,
  inject,
  map,
  setClassMetadata,
  ɵɵdefineInjectable
} from "./chunk-WGFJ2PWY.js";

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
//# sourceMappingURL=chunk-NZEYOUMN.js.map
