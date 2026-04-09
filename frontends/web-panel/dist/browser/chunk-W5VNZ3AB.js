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
      httpParams = httpParams.set("role", params.role);
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
//# sourceMappingURL=chunk-W5VNZ3AB.js.map
