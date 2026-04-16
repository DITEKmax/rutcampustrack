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

// src/app/features/admin/shared/admin-api.service.ts
var AdminApiService = class _AdminApiService {
  http = inject(HttpClient);
  listUsers(params) {
    let httpParams = new HttpParams();
    if (params.page != null)
      httpParams = httpParams.set("page", params.page);
    if (params.size != null)
      httpParams = httpParams.set("size", params.size);
    if (params.role)
      httpParams = httpParams.set("role", params.role.toUpperCase());
    if (params.status)
      httpParams = httpParams.set("status", params.status);
    if (params.search)
      httpParams = httpParams.set("search", params.search);
    return this.http.get("/api/academic/users", { params: httpParams }).pipe(map((res) => ({
      items: Object.values(res._embedded ?? {})[0] ?? [],
      total: res.page.totalElements
    })));
  }
  createUser(req) {
    return this.http.post("/api/academic/users", req);
  }
  getUser(id) {
    return this.http.get(`/api/academic/users/${id}`);
  }
  patchUser(id, req) {
    return this.http.patch(`/api/academic/users/${id}`, req);
  }
  deleteUser(id) {
    return this.http.delete(`/api/academic/users/${id}`);
  }
  listGroups() {
    return this.http.get("/api/academic/groups", {
      params: new HttpParams().set("size", "200")
    }).pipe(map((res) => Object.values(res._embedded ?? {})[0] ?? []));
  }
  /**
   * BUG-006-6 / plan 58-08: listGroupsByStatus — GET /groups?status=ACTIVE|ARCHIVED|ALL&search=...
   * Возвращает плоский массив (как listGroups), с поддержкой ILIKE-поиска и lifecycle-фильтра.
   */
  listGroupsByStatus(params) {
    let httpParams = new HttpParams().set("status", params.status);
    if (params.search)
      httpParams = httpParams.set("search", params.search);
    httpParams = httpParams.set("page", params.page ?? 0);
    httpParams = httpParams.set("size", params.size ?? 200);
    return this.http.get("/api/academic/groups", { params: httpParams }).pipe(map((res) => ({
      items: Object.values(res._embedded ?? {})[0] ?? [],
      total: res.page?.totalElements ?? 0
    })));
  }
  /** BUG-006-6 / plan 58-08: POST /groups/promote/preview — сухой прогон, без записи. */
  promotePreview() {
    return this.http.post("/api/academic/groups/promote/preview", {});
  }
  /** BUG-006-6 / plan 58-08: POST /groups/promote — выполнение перевода (ADMIN). */
  promote() {
    return this.http.post("/api/academic/groups/promote", {});
  }
  /** BUG-006-6 / plan 58-08: GET /groups/{id} — одна группа (для страницы истории). */
  getGroup(id) {
    return this.http.get(`/api/academic/groups/${id}`);
  }
  createGroup(req) {
    return this.http.post("/api/academic/groups", req);
  }
  updateGroup(id, req) {
    return this.http.put(`/api/academic/groups/${id}`, req);
  }
  deleteGroup(id) {
    return this.http.delete(`/api/academic/groups/${id}`);
  }
  listSemesters() {
    return this.http.get("/api/academic/semesters", {
      params: new HttpParams().set("size", "200")
    }).pipe(map((res) => Object.values(res._embedded ?? {})[0] ?? []));
  }
  createSemester(req) {
    return this.http.post("/api/academic/semesters", req);
  }
  updateSemester(id, req) {
    return this.http.put(`/api/academic/semesters/${id}`, req);
  }
  deleteSemester(id, confirmation) {
    return this.http.request("DELETE", `/api/academic/semesters/${id}`, {
      body: { confirmation }
    });
  }
  activateSemester(id) {
    return this.http.patch(`/api/academic/semesters/${id}/activate`, null);
  }
  /**
   * BUG-006-7 / plan 58-05: проверка пересечения дат семестра.
   * Используется async-валидатором semester-dialog до submit.
   */
  checkSemesterOverlap(from, to, excludeId) {
    let params = new HttpParams().set("from", from).set("to", to);
    if (excludeId != null)
      params = params.set("excludeId", excludeId);
    return this.http.get("/api/academic/semesters/overlap", { params });
  }
  getDashboardStats() {
    return this.http.get("/api/academic/dashboard/stats");
  }
  listStudentsByGroup(groupId) {
    return this.http.get("/api/academic/users", {
      params: new HttpParams().set("role", "STUDENT").set("size", "500")
    }).pipe(map((res) => Object.values(res._embedded ?? {})[0] ?? []), map((users) => users.filter((u) => u.groupId === groupId)));
  }
  static \u0275fac = function AdminApiService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _AdminApiService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _AdminApiService, factory: _AdminApiService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(AdminApiService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], null, null);
})();

export {
  AdminApiService
};
//# sourceMappingURL=chunk-LPTENPUF.js.map
