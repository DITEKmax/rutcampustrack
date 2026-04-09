import {
  Injectable,
  setClassMetadata,
  signal,
  ɵɵdefineInjectable
} from "./chunk-WGFJ2PWY.js";

// src/app/features/student/shared/student-notification-badge.service.ts
var StudentNotificationBadgeService = class _StudentNotificationBadgeService {
  _count = signal(0);
  unreadCount = this._count.asReadonly();
  increment() {
    this._count.update((n) => n + 1);
  }
  reset() {
    this._count.set(0);
  }
  static \u0275fac = function StudentNotificationBadgeService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentNotificationBadgeService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _StudentNotificationBadgeService, factory: _StudentNotificationBadgeService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentNotificationBadgeService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], null, null);
})();

export {
  StudentNotificationBadgeService
};
//# sourceMappingURL=chunk-5SWBTVWA.js.map
