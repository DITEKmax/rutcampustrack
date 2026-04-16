import {
  HttpClient,
  HttpParams
} from "./chunk-M5DKW26A.js";
import {
  Injectable,
  inject,
  map,
  setClassMetadata,
  ɵɵdefineInjectable
} from "./chunk-MFRIGFR2.js";

// src/app/features/teacher/journal/journal-api.service.ts
var JournalApiService = class _JournalApiService {
  http = inject(HttpClient);
  getJournal(groupId, subjectId, dateFrom, dateTo) {
    const params = new HttpParams().set("groupId", groupId).set("subjectId", subjectId).set("dateFrom", dateFrom).set("dateTo", dateTo);
    return this.http.get("/api/attendance/reports/journal", { params });
  }
  getMyAssignments() {
    return this.http.get("/api/academic/assignments/my").pipe(map((response) => Object.values(response._embedded ?? {})[0] ?? []));
  }
  getGroups() {
    return this.http.get("/api/academic/groups", {
      params: new HttpParams().set("size", "200")
    }).pipe(map((response) => Object.values(response._embedded ?? {})[0] ?? []));
  }
  getSubjects() {
    return this.http.get("/api/academic/subjects", {
      params: new HttpParams().set("size", "200")
    }).pipe(map((response) => Object.values(response._embedded ?? {})[0] ?? []));
  }
  static \u0275fac = function JournalApiService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _JournalApiService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _JournalApiService, factory: _JournalApiService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(JournalApiService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], null, null);
})();

export {
  JournalApiService
};
//# sourceMappingURL=chunk-T4UZ7SIZ.js.map
