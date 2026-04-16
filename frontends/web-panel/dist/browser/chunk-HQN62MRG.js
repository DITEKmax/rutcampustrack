import {
  HttpClient
} from "./chunk-M5DKW26A.js";
import {
  Injectable,
  inject,
  setClassMetadata,
  ɵɵdefineInjectable
} from "./chunk-MFRIGFR2.js";

// src/app/core/auth/auth.api.ts
var AuthApi = class _AuthApi {
  http = inject(HttpClient);
  login(credentials) {
    return this.http.post("/api/auth/login", credentials);
  }
  verifyOtpByCode(code) {
    return this.http.post("/api/auth/otp/verify-by-code", { code });
  }
  refresh(refreshToken) {
    return this.http.post("/api/auth/refresh", { refreshToken });
  }
  logout(refreshToken) {
    return this.http.post("/api/auth/logout", { refreshToken });
  }
  changePassword(currentPassword, newPassword) {
    return this.http.post("/api/auth/change-password", { currentPassword, newPassword });
  }
  static \u0275fac = function AuthApi_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _AuthApi)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _AuthApi, factory: _AuthApi.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(AuthApi, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], null, null);
})();

export {
  AuthApi
};
//# sourceMappingURL=chunk-HQN62MRG.js.map
