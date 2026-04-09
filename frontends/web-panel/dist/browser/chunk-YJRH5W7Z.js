import {
  Injectable,
  __async,
  computed,
  firstValueFrom,
  setClassMetadata,
  signal,
  ɵɵdefineInjectable
} from "./chunk-WGFJ2PWY.js";

// src/app/core/auth/auth.service.ts
var AuthService = class _AuthService {
  _accessToken = signal(null);
  _refreshToken = signal(null);
  accessToken = this._accessToken.asReadonly();
  isAuthenticated = computed(() => this._accessToken() !== null);
  currentUser = computed(() => {
    const token = this._accessToken();
    if (!token)
      return null;
    try {
      const parts = token.split(".");
      if (parts.length !== 3)
        return null;
      const payload = JSON.parse(atob(parts[1]));
      return {
        id: Number(payload.sub),
        role: payload.role.toUpperCase(),
        isHeadman: payload.is_headman === true,
        groupId: payload.group_id ?? null
      };
    } catch {
      return null;
    }
  });
  setTokens(accessToken, refreshToken) {
    this._accessToken.set(accessToken);
    this._refreshToken.set(refreshToken);
  }
  getRefreshToken() {
    return this._refreshToken();
  }
  clearTokens() {
    this._accessToken.set(null);
    this._refreshToken.set(null);
  }
  logout(authApi, router) {
    return __async(this, null, function* () {
      const rt = this._refreshToken();
      if (rt) {
        try {
          yield firstValueFrom(authApi.logout(rt));
        } catch {
        }
      }
      this.clearTokens();
      router.navigate(["/login"]);
    });
  }
  /**
   * Single source of truth for post-login redirects (Phase 50, D-09).
   *
   * Used by login.component.ts after successful auth, by guestGuard
   * (when already-logged-in user hits /login), and by roleGuard fallback
   * (so denied access routes the user to *their own* dashboard, not a
   * hard-coded admin route).
   */
  resolveDashboardFor(user) {
    if (!user)
      return "/login";
    if (user.role === "ADMIN")
      return "/admin/dashboard";
    if (user.role === "TEACHER")
      return "/teacher/dashboard";
    if (user.isHeadman)
      return "/headman/dashboard";
    return "/student/dashboard";
  }
  static \u0275fac = function AuthService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _AuthService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _AuthService, factory: _AuthService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(AuthService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], null, null);
})();

export {
  AuthService
};
//# sourceMappingURL=chunk-YJRH5W7Z.js.map
