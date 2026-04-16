import {
  HttpClient
} from "./chunk-M5DKW26A.js";
import {
  Injectable,
  Injector,
  __async,
  computed,
  firstValueFrom,
  inject,
  setClassMetadata,
  signal,
  tap,
  ɵɵdefineInjectable
} from "./chunk-MFRIGFR2.js";

// src/app/core/profile/profile.service.ts
var ProfileService = class _ProfileService {
  http = inject(HttpClient);
  _profile = signal(null);
  _loading = signal(false);
  _error = signal(null);
  profile = this._profile.asReadonly();
  loading = this._loading.asReadonly();
  error = this._error.asReadonly();
  displayName = computed(() => {
    const p = this._profile();
    if (!p)
      return "";
    return [p.lastName, p.firstName].filter(Boolean).join(" ");
  });
  initials = computed(() => {
    const p = this._profile();
    if (!p)
      return "";
    const ln = (p.lastName?.[0] ?? "").toUpperCase();
    const fn = (p.firstName?.[0] ?? "").toUpperCase();
    return ln + fn || "?";
  });
  load(force = false) {
    if (this._loading())
      return;
    if (this._profile() && !force)
      return;
    this._loading.set(true);
    this._error.set(null);
    this.http.get("/api/academic/users/me").subscribe({
      next: (data) => {
        this._profile.set(data);
        this._loading.set(false);
      },
      error: () => {
        this._error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u043F\u0440\u043E\u0444\u0438\u043B\u044C.");
        this._loading.set(false);
      }
    });
  }
  updateAvatar(avatarId) {
    return this.http.patch("/api/academic/users/me/avatar", { avatarId }).pipe(tap((p) => this._profile.set(p)));
  }
  changePassword(currentPassword, newPassword) {
    return this.http.post("/api/auth/change-password", {
      currentPassword,
      newPassword
    });
  }
  /** Wipe cached profile (called from logout). */
  clear() {
    this._profile.set(null);
  }
  static \u0275fac = function ProfileService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _ProfileService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _ProfileService, factory: _ProfileService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(ProfileService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], null, null);
})();

// src/app/core/auth/auth.service.ts
var STORAGE_KEY = "rct.auth.v1";
function readPersisted() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw)
      return null;
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed.accessToken === "string" && typeof parsed.refreshToken === "string") {
      return { accessToken: parsed.accessToken, refreshToken: parsed.refreshToken };
    }
    return null;
  } catch {
    return null;
  }
}
function writePersisted(tokens) {
  try {
    if (tokens === null) {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
    }
  } catch {
  }
}
var AuthService = class _AuthService {
  injector = inject(Injector);
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
  constructor() {
    const persisted = readPersisted();
    if (persisted) {
      this._accessToken.set(persisted.accessToken);
      this._refreshToken.set(persisted.refreshToken);
    }
    if (typeof window !== "undefined") {
      window.addEventListener("storage", (event) => {
        if (event.key !== STORAGE_KEY)
          return;
        if (!event.newValue) {
          this._accessToken.set(null);
          this._refreshToken.set(null);
          try {
            this.injector.get(ProfileService).clear();
          } catch {
          }
          return;
        }
        try {
          const parsed = JSON.parse(event.newValue);
          if (typeof parsed.accessToken === "string" && typeof parsed.refreshToken === "string") {
            this._accessToken.set(parsed.accessToken);
            this._refreshToken.set(parsed.refreshToken);
          }
        } catch {
        }
      });
    }
  }
  setTokens(accessToken, refreshToken) {
    this._accessToken.set(accessToken);
    this._refreshToken.set(refreshToken);
    writePersisted({ accessToken, refreshToken });
  }
  getRefreshToken() {
    return this._refreshToken();
  }
  clearTokens() {
    this._accessToken.set(null);
    this._refreshToken.set(null);
    writePersisted(null);
    try {
      this.injector.get(ProfileService).clear();
    } catch {
    }
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
  }], () => [], null);
})();

export {
  ProfileService,
  AuthService
};
//# sourceMappingURL=chunk-URGWZVMC.js.map
