import {
  HttpClient
} from "./chunk-M5DKW26A.js";
import {
  Injectable,
  catchError,
  inject,
  map,
  of,
  setClassMetadata,
  shareReplay,
  ɵɵdefineInjectable
} from "./chunk-MFRIGFR2.js";

// src/app/features/student/shared/subject-cache.service.ts
var SubjectCacheService = class _SubjectCacheService {
  http = inject(HttpClient);
  cache = /* @__PURE__ */ new Map();
  /**
   * Resolve a subject name for the given id.
   * Returns a shared Observable; re-subscribes hit the cached emission.
   */
  getName(subjectId) {
    if (!subjectId) {
      return of("\u041F\u0440\u0435\u0434\u043C\u0435\u0442");
    }
    const cached = this.cache.get(subjectId);
    if (cached) {
      return cached;
    }
    const request$ = this.http.get(`/api/academic/subjects/${subjectId}`).pipe(map((response) => response.name), catchError(() => of("\u041F\u0440\u0435\u0434\u043C\u0435\u0442")), shareReplay(1));
    this.cache.set(subjectId, request$);
    return request$;
  }
  static \u0275fac = function SubjectCacheService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _SubjectCacheService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _SubjectCacheService, factory: _SubjectCacheService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(SubjectCacheService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], null, null);
})();

export {
  SubjectCacheService
};
//# sourceMappingURL=chunk-QY7VTECN.js.map
