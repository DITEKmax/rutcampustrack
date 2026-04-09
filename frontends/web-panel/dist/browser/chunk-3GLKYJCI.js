import {
  StudentNotificationBadgeService
} from "./chunk-5SWBTVWA.js";
import "./chunk-3OOB2NIX.js";
import {
  animate,
  state,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import {
  AuthApi
} from "./chunk-2QATIFWL.js";
import {
  AuthService
} from "./chunk-YJRH5W7Z.js";
import {
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet
} from "./chunk-LV56B2AI.js";
import "./chunk-LGL42SQS.js";
import "./chunk-T4R75CF5.js";
import "./chunk-L2FQVI4C.js";
import {
  ChangeDetectionStrategy,
  Component,
  Injectable,
  Input,
  __async,
  computed,
  filter,
  inject,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵclassMap,
  ɵɵclassProp,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵdefineInjectable,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵlistener,
  ɵɵnamespaceSVG,
  ɵɵnextContext,
  ɵɵproperty,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-WGFJ2PWY.js";

// src/app/core/theme/theme.service.ts
var ThemeService = class _ThemeService {
  STORAGE_KEY = "ruttrack.theme";
  /** Legacy key from the pre-brandbook build — still read on first load. */
  LEGACY_STORAGE_KEY = "web-panel.theme";
  _theme = signal("dark");
  theme = this._theme.asReadonly();
  isDark = computed(() => this._theme() === "dark");
  userOverrode = false;
  constructor() {
    const stored = this.readStored();
    if (stored) {
      this._theme.set(stored);
      this.userOverrode = true;
    } else {
      this._theme.set(window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark");
    }
    this.applyTheme();
    this.watchSystemPreference();
  }
  toggle() {
    this.setTheme(this._theme() === "dark" ? "light" : "dark");
  }
  setTheme(next) {
    const root = document.documentElement;
    root.classList.add("theme-transitioning");
    window.setTimeout(() => root.classList.remove("theme-transitioning"), 320);
    this.userOverrode = true;
    try {
      localStorage.setItem(this.STORAGE_KEY, next);
    } catch {
    }
    this._theme.set(next);
    this.applyTheme();
  }
  readStored() {
    try {
      const raw = localStorage.getItem(this.STORAGE_KEY);
      if (raw === "dark" || raw === "light")
        return raw;
      const legacy = localStorage.getItem(this.LEGACY_STORAGE_KEY);
      if (legacy === "dark" || legacy === "light") {
        localStorage.setItem(this.STORAGE_KEY, legacy);
        localStorage.removeItem(this.LEGACY_STORAGE_KEY);
        return legacy;
      }
      return null;
    } catch {
      return null;
    }
  }
  watchSystemPreference() {
    if (!window.matchMedia)
      return;
    const mql = window.matchMedia("(prefers-color-scheme: light)");
    mql.addEventListener("change", (e) => {
      if (this.userOverrode)
        return;
      this._theme.set(e.matches ? "light" : "dark");
      this.applyTheme();
    });
  }
  applyTheme() {
    const root = document.documentElement;
    const current = this._theme();
    root.setAttribute("data-theme", current);
    root.classList.toggle("dark", current === "dark");
  }
  static \u0275fac = function ThemeService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _ThemeService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _ThemeService, factory: _ThemeService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(ThemeService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], () => [], null);
})();

// src/app/layout/sidebar/sidebar.component.ts
var _forTrack0 = ($index, $item) => $item.route;
function SidebarComponent_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 3);
    \u0275\u0275text(1, "RutTrack");
    \u0275\u0275elementEnd();
  }
}
function SidebarComponent_Conditional_5_For_2_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 10);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const item_r1 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(item_r1.label);
  }
}
function SidebarComponent_Conditional_5_For_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "li")(1, "a", 13);
    \u0275\u0275element(2, "i", 14);
    \u0275\u0275template(3, SidebarComponent_Conditional_5_For_2_Conditional_3_Template, 2, 1, "span", 10);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const item_r1 = ctx.$implicit;
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275property("routerLink", item_r1.route)("title", item_r1.label);
    \u0275\u0275attribute("aria-label", ctx_r1.collapsed() ? item_r1.label : null);
    \u0275\u0275advance();
    \u0275\u0275classMap(item_r1.icon);
    \u0275\u0275advance();
    \u0275\u0275conditional(!ctx_r1.collapsed() ? 3 : -1);
  }
}
function SidebarComponent_Conditional_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "ul", 4);
    \u0275\u0275repeaterCreate(1, SidebarComponent_Conditional_5_For_2_Template, 4, 6, "li", null, _forTrack0);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275repeater(ctx_r1.filteredPrimaryItems());
  }
}
function SidebarComponent_Conditional_6_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 15);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r1.sectionLabel());
  }
}
function SidebarComponent_Conditional_6_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 16);
  }
}
function SidebarComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275template(0, SidebarComponent_Conditional_6_Conditional_0_Template, 2, 1, "p", 15)(1, SidebarComponent_Conditional_6_Conditional_1_Template, 1, 0, "div", 16);
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275conditional(!ctx_r1.collapsed() ? 0 : 1);
  }
}
function SidebarComponent_For_9_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 18);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275attribute("aria-label", "\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F, " + ctx_r1.unreadCount() + " \u043D\u0435\u043F\u0440\u043E\u0447\u0438\u0442\u0430\u043D\u043D\u044B\u0445");
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r1.unreadCount() > 9 ? "9+" : ctx_r1.unreadCount(), " ");
  }
}
function SidebarComponent_For_9_Conditional_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 10);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const item_r3 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(item_r3.label);
  }
}
function SidebarComponent_For_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "li")(1, "a", 13)(2, "span", 17);
    \u0275\u0275element(3, "i", 14);
    \u0275\u0275template(4, SidebarComponent_For_9_Conditional_4_Template, 2, 2, "span", 18);
    \u0275\u0275elementEnd();
    \u0275\u0275template(5, SidebarComponent_For_9_Conditional_5_Template, 2, 1, "span", 10);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const item_r3 = ctx.$implicit;
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275property("routerLink", item_r3.route)("title", item_r3.label);
    \u0275\u0275advance(2);
    \u0275\u0275classMap(item_r3.icon);
    \u0275\u0275advance();
    \u0275\u0275conditional(item_r3.icon === "ph-bell" && ctx_r1.unreadCount() > 0 ? 4 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(!ctx_r1.collapsed() ? 5 : -1);
  }
}
function SidebarComponent_Conditional_12_Conditional_0_Case_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275text(0, " \u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440 ");
  }
}
function SidebarComponent_Conditional_12_Conditional_0_Case_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275text(0, " \u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C ");
  }
}
function SidebarComponent_Conditional_12_Conditional_0_Case_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275text(0, " \u0421\u0442\u0443\u0434\u0435\u043D\u0442 ");
  }
}
function SidebarComponent_Conditional_12_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 19)(1, "span", 21);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "div", 22)(4, "span", 23);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "span", 24);
    \u0275\u0275template(7, SidebarComponent_Conditional_12_Conditional_0_Case_7_Template, 1, 0)(8, SidebarComponent_Conditional_12_Conditional_0_Case_8_Template, 1, 0)(9, SidebarComponent_Conditional_12_Conditional_0_Case_9_Template, 1, 0);
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    let tmp_5_0;
    const user_r4 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", (user_r4.id + "").slice(0, 2).toUpperCase(), " ");
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate1("ID ", user_r4.id, "");
    \u0275\u0275advance(2);
    \u0275\u0275conditional((tmp_5_0 = user_r4.role) === "ADMIN" ? 7 : tmp_5_0 === "TEACHER" ? 8 : tmp_5_0 === "STUDENT" ? 9 : -1);
  }
}
function SidebarComponent_Conditional_12_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 20)(1, "span", 21);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const user_r4 = \u0275\u0275nextContext();
    \u0275\u0275property("title", "ID " + user_r4.id);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", (user_r4.id + "").slice(0, 2).toUpperCase(), " ");
  }
}
function SidebarComponent_Conditional_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275template(0, SidebarComponent_Conditional_12_Conditional_0_Template, 10, 3, "div", 19)(1, SidebarComponent_Conditional_12_Conditional_1_Template, 3, 2, "div", 20);
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275conditional(!ctx_r1.collapsed() ? 0 : 1);
  }
}
function SidebarComponent_Conditional_15_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 10);
    \u0275\u0275text(1, "\u0412\u044B\u0439\u0442\u0438");
    \u0275\u0275elementEnd();
  }
}
function SidebarComponent_Conditional_18_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 10);
    \u0275\u0275text(1, "\u0421\u0432\u0435\u0440\u043D\u0443\u0442\u044C");
    \u0275\u0275elementEnd();
  }
}
var SidebarComponent = class _SidebarComponent {
  authService = inject(AuthService);
  authApi = inject(AuthApi);
  router = inject(Router);
  themeService = inject(ThemeService);
  SIDEBAR_KEY = "web-panel.sidebar.collapsed";
  collapsed = signal(false);
  currentUser = this.authService.currentUser;
  notificationBadge = inject(StudentNotificationBadgeService);
  unreadCount = this.notificationBadge.unreadCount;
  /** Primary nav (dashboards) — always shown first when role matches. */
  primaryItems = [
    {
      label: "\u0414\u0430\u0448\u0431\u043E\u0440\u0434",
      icon: "ph-squares-four",
      route: "/teacher/dashboard",
      roles: ["TEACHER"]
    },
    {
      label: "\u0414\u0430\u0448\u0431\u043E\u0440\u0434",
      icon: "ph-squares-four",
      route: "/admin/dashboard",
      roles: ["ADMIN"]
    },
    {
      label: "\u0413\u043B\u0430\u0432\u043D\u0430\u044F",
      icon: "ph-squares-four",
      route: "/student/dashboard",
      roles: ["STUDENT"]
    }
  ];
  /** Secondary nav — work pages under each section. */
  allNavItems = [
    // Teacher items
    {
      label: "\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438",
      icon: "ph-book-open",
      route: "/teacher/journal",
      roles: ["TEACHER"]
    },
    {
      label: "\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430",
      icon: "ph-chart-bar",
      route: "/teacher/stats",
      roles: ["TEACHER"]
    },
    // Admin items
    {
      label: "\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u0438",
      icon: "ph-users",
      route: "/admin/users",
      roles: ["ADMIN"]
    },
    {
      label: "\u0413\u0440\u0443\u043F\u043F\u044B",
      icon: "ph-users-three",
      route: "/admin/groups",
      roles: ["ADMIN"]
    },
    {
      label: "\u0421\u0435\u043C\u0435\u0441\u0442\u0440\u044B",
      icon: "ph-calendar",
      route: "/admin/semesters",
      roles: ["ADMIN"]
    },
    // Student items
    {
      label: "\u0420\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435",
      icon: "ph-calendar-dots",
      route: "/student/schedule",
      roles: ["STUDENT"]
    },
    {
      label: "\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C\u0441\u044F",
      icon: "ph-map-pin",
      route: "/student/checkin",
      roles: ["STUDENT"]
    },
    {
      label: "\u0414\u043E\u043C\u0430\u0448\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F",
      icon: "ph-notebook",
      route: "/student/homework",
      roles: ["STUDENT"]
    },
    {
      label: "\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430",
      icon: "ph-chart-bar",
      route: "/student/stats",
      roles: ["STUDENT"]
    },
    {
      label: "\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F",
      icon: "ph-bell",
      route: "/student/notifications",
      roles: ["STUDENT"]
    },
    {
      label: "\u041F\u0440\u043E\u0444\u0438\u043B\u044C",
      icon: "ph-user-circle",
      route: "/student/profile",
      roles: ["STUDENT"]
    }
  ];
  filteredPrimaryItems = computed(() => {
    const user = this.currentUser();
    if (!user)
      return [];
    return this.primaryItems.filter((item) => item.roles.includes(user.role));
  });
  filteredNavItems = computed(() => {
    const user = this.currentUser();
    if (!user)
      return [];
    return this.allNavItems.filter((item) => item.roles.includes(user.role));
  });
  sectionLabel = computed(() => {
    const user = this.currentUser();
    if (!user)
      return "";
    if (user.role === "ADMIN")
      return "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0438\u0440\u043E\u0432\u0430\u043D\u0438\u0435";
    if (user.role === "STUDENT")
      return "\u0423\u0447\u0451\u0431\u0430";
    return "\u0420\u0430\u0431\u043E\u0442\u0430";
  });
  ngOnInit() {
    const stored = localStorage.getItem(this.SIDEBAR_KEY);
    if (stored === "true")
      this.collapsed.set(true);
    if (typeof window !== "undefined" && window.innerWidth < 1024) {
      this.collapsed.set(true);
    }
  }
  toggleCollapse() {
    this.collapsed.update((v) => !v);
    localStorage.setItem(this.SIDEBAR_KEY, String(this.collapsed()));
  }
  logout() {
    return __async(this, null, function* () {
      yield this.authService.logout(this.authApi, this.router);
    });
  }
  toggleTheme() {
    this.themeService.toggle();
  }
  static \u0275fac = function SidebarComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _SidebarComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _SidebarComponent, selectors: [["app-sidebar"]], decls: 19, vars: 13, consts: [["aria-label", "\u041E\u0441\u043D\u043E\u0432\u043D\u0430\u044F \u043D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F", 1, "sidebar"], ["routerLink", "/", "aria-label", "RutCampusTrack \u2014 \u0433\u043B\u0430\u0432\u043D\u0430\u044F", 1, "sidebar__brand"], ["aria-hidden", "true", 1, "sidebar__brand-mark"], [1, "sidebar__brand-name"], [1, "sidebar__nav", "sidebar__nav--primary"], [1, "sidebar__nav"], [1, "sidebar__spacer"], [1, "sidebar__footer"], ["type", "button", "title", "\u0412\u044B\u0439\u0442\u0438", 1, "sidebar__footer-btn", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-sign-out", "sidebar__icon"], [1, "sidebar__label"], ["type", "button", 1, "sidebar__footer-btn", "sidebar__collapse-btn", 3, "click", "title"], ["aria-hidden", "true", 1, "ph", "ph-caret-left", "sidebar__icon"], ["routerLinkActive", "sidebar__link--active", 1, "sidebar__link", 3, "routerLink", "title"], ["aria-hidden", "true", 1, "ph", "sidebar__icon"], [1, "sidebar__section"], ["aria-hidden", "true", 1, "sidebar__section-divider"], [1, "sidebar__icon-wrap", 2, "position", "relative", "display", "inline-flex"], [1, "notification-badge"], [1, "sidebar__user"], [1, "sidebar__user", "sidebar__user--collapsed", 3, "title"], ["aria-hidden", "true", 1, "sidebar__user-avatar"], [1, "sidebar__user-meta"], [1, "sidebar__user-id"], [1, "sidebar__user-role"]], template: function SidebarComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "nav", 0)(1, "a", 1)(2, "span", 2);
      \u0275\u0275text(3, "R");
      \u0275\u0275elementEnd();
      \u0275\u0275template(4, SidebarComponent_Conditional_4_Template, 2, 0, "span", 3);
      \u0275\u0275elementEnd();
      \u0275\u0275template(5, SidebarComponent_Conditional_5_Template, 3, 0, "ul", 4)(6, SidebarComponent_Conditional_6_Template, 2, 1);
      \u0275\u0275elementStart(7, "ul", 5);
      \u0275\u0275repeaterCreate(8, SidebarComponent_For_9_Template, 6, 6, "li", null, _forTrack0);
      \u0275\u0275elementEnd();
      \u0275\u0275element(10, "div", 6);
      \u0275\u0275elementStart(11, "div", 7);
      \u0275\u0275template(12, SidebarComponent_Conditional_12_Template, 2, 1);
      \u0275\u0275elementStart(13, "button", 8);
      \u0275\u0275listener("click", function SidebarComponent_Template_button_click_13_listener() {
        return ctx.logout();
      });
      \u0275\u0275element(14, "i", 9);
      \u0275\u0275template(15, SidebarComponent_Conditional_15_Template, 2, 0, "span", 10);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(16, "button", 11);
      \u0275\u0275listener("click", function SidebarComponent_Template_button_click_16_listener() {
        return ctx.toggleCollapse();
      });
      \u0275\u0275element(17, "i", 12);
      \u0275\u0275template(18, SidebarComponent_Conditional_18_Template, 2, 0, "span", 10);
      \u0275\u0275elementEnd()()();
    }
    if (rf & 2) {
      let tmp_6_0;
      \u0275\u0275classProp("sidebar--collapsed", ctx.collapsed());
      \u0275\u0275property("@collapse", ctx.collapsed() ? "collapsed" : "expanded");
      \u0275\u0275advance(4);
      \u0275\u0275conditional(!ctx.collapsed() ? 4 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.filteredPrimaryItems().length > 0 ? 5 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.filteredNavItems().length > 0 ? 6 : -1);
      \u0275\u0275advance(2);
      \u0275\u0275repeater(ctx.filteredNavItems());
      \u0275\u0275advance(4);
      \u0275\u0275conditional((tmp_6_0 = ctx.currentUser()) ? 12 : -1, tmp_6_0);
      \u0275\u0275advance();
      \u0275\u0275attribute("aria-label", ctx.collapsed() ? "\u0412\u044B\u0439\u0442\u0438" : null);
      \u0275\u0275advance(2);
      \u0275\u0275conditional(!ctx.collapsed() ? 15 : -1);
      \u0275\u0275advance();
      \u0275\u0275property("title", ctx.collapsed() ? "\u0420\u0430\u0437\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E" : "\u0421\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E");
      \u0275\u0275attribute("aria-label", ctx.collapsed() ? "\u0420\u0430\u0437\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E" : "\u0421\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E");
      \u0275\u0275advance();
      \u0275\u0275property("@rotateChevron", ctx.collapsed() ? "collapsed" : "expanded");
      \u0275\u0275advance();
      \u0275\u0275conditional(!ctx.collapsed() ? 18 : -1);
    }
  }, dependencies: [RouterLink, RouterLinkActive], styles: ['\n\n[_nghost-%COMP%] {\n  display: block;\n  flex-shrink: 0;\n  height: 100vh;\n  position: sticky;\n  top: 0;\n}\n.sidebar[_ngcontent-%COMP%] {\n  height: 100%;\n  display: flex;\n  flex-direction: column;\n  background: var(--bg-primary);\n  border-right: 1px solid var(--border-subtle);\n  overflow: hidden;\n  position: relative;\n  background-image:\n    linear-gradient(var(--border-subtle) 1px, transparent 1px),\n    linear-gradient(\n      90deg,\n      var(--border-subtle) 1px,\n      transparent 1px);\n  background-size: 64px 64px;\n  transition: background-color var(--duration-slow) var(--ease-out), border-color var(--duration-slow) var(--ease-out);\n}\n.sidebar__brand[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 0 var(--space-5);\n  height: var(--header-height, 64px);\n  flex-shrink: 0;\n  border-bottom: 1px solid var(--border-subtle);\n  text-decoration: none;\n  color: var(--text-primary);\n}\n.sidebar--collapsed[_ngcontent-%COMP%]   .sidebar__brand[_ngcontent-%COMP%] {\n  padding: 0;\n  justify-content: center;\n}\n.sidebar__brand-mark[_ngcontent-%COMP%] {\n  width: 38px;\n  height: 38px;\n  border-radius: 10px;\n  background: var(--gradient-brand);\n  color: var(--accent-primary-contrast);\n  display: grid;\n  place-items: center;\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 1rem;\n  flex-shrink: 0;\n  box-shadow: var(--glow-primary);\n  position: relative;\n}\n.sidebar__brand-mark[_ngcontent-%COMP%]::after {\n  content: "";\n  position: absolute;\n  top: 6px;\n  right: 6px;\n  width: 6px;\n  height: 6px;\n  border-radius: 50%;\n  background: #fff;\n  box-shadow: 0 0 8px rgba(255, 255, 255, 0.8);\n}\n.sidebar__brand-name[_ngcontent-%COMP%] {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 1.125rem;\n  color: var(--text-primary);\n  letter-spacing: -0.01em;\n  white-space: nowrap;\n}\n.sidebar__nav[_ngcontent-%COMP%] {\n  list-style: none;\n  margin: 0;\n  padding: var(--space-3);\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n}\n.sidebar__nav--primary[_ngcontent-%COMP%] {\n  padding-bottom: var(--space-2);\n}\n.sidebar__section[_ngcontent-%COMP%] {\n  margin: var(--space-4) var(--space-5) var(--space-2);\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  font-weight: 500;\n  color: var(--text-muted);\n  letter-spacing: 0.05em;\n  text-transform: uppercase;\n}\n.sidebar__section-divider[_ngcontent-%COMP%] {\n  height: 1px;\n  margin: var(--space-4) var(--space-3);\n  background: var(--border-subtle);\n}\n.sidebar__link[_ngcontent-%COMP%] {\n  position: relative;\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 10px 12px 10px 13px;\n  border-radius: var(--radius-md);\n  font-size: 0.875rem;\n  color: var(--text-secondary);\n  text-decoration: none;\n  transition: background-color var(--duration-base) var(--ease-out), color var(--duration-base) var(--ease-out);\n}\n.sidebar--collapsed[_ngcontent-%COMP%]   .sidebar__link[_ngcontent-%COMP%] {\n  justify-content: center;\n  padding: 10px 0;\n}\n.sidebar__link[_ngcontent-%COMP%]:hover {\n  background: color-mix(in oklab, var(--text-primary) 5%, transparent);\n  color: var(--text-primary);\n}\n.sidebar__link--active[_ngcontent-%COMP%] {\n  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);\n  color: var(--accent-primary);\n  font-weight: 500;\n}\n.sidebar__link--active[_ngcontent-%COMP%]::before {\n  content: "";\n  position: absolute;\n  left: 0;\n  top: 8px;\n  bottom: 8px;\n  width: 3px;\n  border-radius: 0 3px 3px 0;\n  background: var(--accent-primary);\n  box-shadow: var(--glow-primary);\n}\n.sidebar--collapsed[_ngcontent-%COMP%]   .sidebar__link--active[_ngcontent-%COMP%]::before {\n  left: 6px;\n  top: 10px;\n  bottom: 10px;\n}\n.sidebar__icon[_ngcontent-%COMP%] {\n  font-size: 22px;\n  flex-shrink: 0;\n  line-height: 1;\n}\n.sidebar__label[_ngcontent-%COMP%] {\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.sidebar__spacer[_ngcontent-%COMP%] {\n  flex: 1;\n  min-height: var(--space-4);\n}\n.sidebar__footer[_ngcontent-%COMP%] {\n  flex-shrink: 0;\n  padding: var(--space-3);\n  border-top: 1px solid var(--border-subtle);\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n}\n.sidebar__user[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: var(--space-3);\n  margin-bottom: var(--space-2);\n  border-radius: var(--radius-md);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n}\n.sidebar__user--collapsed[_ngcontent-%COMP%] {\n  justify-content: center;\n  padding: var(--space-2);\n}\n.sidebar__user-avatar[_ngcontent-%COMP%] {\n  width: 34px;\n  height: 34px;\n  border-radius: 50%;\n  background: var(--gradient-brand);\n  color: var(--accent-primary-contrast);\n  display: grid;\n  place-items: center;\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 0.75rem;\n  flex-shrink: 0;\n  box-shadow: var(--glow-primary);\n}\n.sidebar__user-meta[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  min-width: 0;\n  line-height: 1.2;\n}\n.sidebar__user-id[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  color: var(--text-muted);\n}\n.sidebar__user-role[_ngcontent-%COMP%] {\n  font-family: var(--font-heading);\n  font-size: 0.8125rem;\n  font-weight: 500;\n  color: var(--text-primary);\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.sidebar__footer-btn[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 10px 12px;\n  border-radius: var(--radius-md);\n  background: transparent;\n  border: 0;\n  color: var(--text-secondary);\n  font-size: 0.875rem;\n  font-family: inherit;\n  cursor: pointer;\n  text-align: left;\n  transition: background-color var(--duration-base) var(--ease-out), color var(--duration-base) var(--ease-out);\n}\n.sidebar--collapsed[_ngcontent-%COMP%]   .sidebar__footer-btn[_ngcontent-%COMP%] {\n  justify-content: center;\n  padding: 10px 0;\n}\n.sidebar__footer-btn[_ngcontent-%COMP%]:hover {\n  background: color-mix(in oklab, var(--text-primary) 5%, transparent);\n  color: var(--text-primary);\n}\n.sidebar__collapse-btn[_ngcontent-%COMP%]:hover   .ph-caret-left[_ngcontent-%COMP%] {\n  color: var(--accent-primary);\n}\n.notification-badge[_ngcontent-%COMP%] {\n  position: absolute;\n  top: -4px;\n  right: -4px;\n  min-width: 16px;\n  height: 16px;\n  background: var(--accent-primary);\n  color: var(--accent-primary-contrast, #0A0E17);\n  border-radius: var(--radius-full, 9999px);\n  font-size: 10px;\n  font-family: var(--font-mono);\n  font-weight: 600;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  padding: 0 3px;\n  pointer-events: none;\n}\n/*# sourceMappingURL=sidebar.component.css.map */'], data: { animation: [
    trigger("collapse", [
      state("expanded", style({ width: "260px" })),
      state("collapsed", style({ width: "72px" })),
      transition("expanded <=> collapsed", animate("240ms cubic-bezier(0.16, 1, 0.3, 1)"))
    ]),
    trigger("rotateChevron", [
      state("expanded", style({ transform: "rotate(0deg)" })),
      state("collapsed", style({ transform: "rotate(180deg)" })),
      transition("expanded <=> collapsed", animate("200ms cubic-bezier(0.16, 1, 0.3, 1)"))
    ])
  ] } });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(SidebarComponent, [{
    type: Component,
    args: [{ selector: "app-sidebar", standalone: true, imports: [RouterLink, RouterLinkActive], animations: [
      trigger("collapse", [
        state("expanded", style({ width: "260px" })),
        state("collapsed", style({ width: "72px" })),
        transition("expanded <=> collapsed", animate("240ms cubic-bezier(0.16, 1, 0.3, 1)"))
      ]),
      trigger("rotateChevron", [
        state("expanded", style({ transform: "rotate(0deg)" })),
        state("collapsed", style({ transform: "rotate(180deg)" })),
        transition("expanded <=> collapsed", animate("200ms cubic-bezier(0.16, 1, 0.3, 1)"))
      ])
    ], template: `<nav\r
  [@collapse]="collapsed() ? 'collapsed' : 'expanded'"\r
  class="sidebar"\r
  [class.sidebar--collapsed]="collapsed()"\r
  aria-label="\u041E\u0441\u043D\u043E\u0432\u043D\u0430\u044F \u043D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F"\r
>\r
  <!-- Brand -->\r
  <a routerLink="/" class="sidebar__brand" aria-label="RutCampusTrack \u2014 \u0433\u043B\u0430\u0432\u043D\u0430\u044F">\r
    <span class="sidebar__brand-mark" aria-hidden="true">R</span>\r
    @if (!collapsed()) {\r
      <span class="sidebar__brand-name">RutTrack</span>\r
    }\r
  </a>\r
\r
  <!-- Primary nav (dashboard) -->\r
  @if (filteredPrimaryItems().length > 0) {\r
    <ul class="sidebar__nav sidebar__nav--primary">\r
      @for (item of filteredPrimaryItems(); track item.route) {\r
        <li>\r
          <a\r
            [routerLink]="item.route"\r
            routerLinkActive="sidebar__link--active"\r
            class="sidebar__link"\r
            [title]="item.label"\r
            [attr.aria-label]="collapsed() ? item.label : null"\r
          >\r
            <i [class]="item.icon" class="ph sidebar__icon" aria-hidden="true"></i>\r
            @if (!collapsed()) {\r
              <span class="sidebar__label">{{ item.label }}</span>\r
            }\r
          </a>\r
        </li>\r
      }\r
    </ul>\r
  }\r
\r
  <!-- Section header -->\r
  @if (filteredNavItems().length > 0) {\r
    @if (!collapsed()) {\r
      <p class="sidebar__section">{{ sectionLabel() }}</p>\r
    } @else {\r
      <div class="sidebar__section-divider" aria-hidden="true"></div>\r
    }\r
  }\r
\r
  <!-- Secondary nav -->\r
  <ul class="sidebar__nav">\r
    @for (item of filteredNavItems(); track item.route) {\r
      <li>\r
        <a\r
          [routerLink]="item.route"\r
          routerLinkActive="sidebar__link--active"\r
          class="sidebar__link"\r
          [title]="item.label"\r
        >\r
          <span class="sidebar__icon-wrap" style="position: relative; display: inline-flex;">\r
            <i [class]="item.icon" class="ph sidebar__icon" aria-hidden="true"></i>\r
            @if (item.icon === 'ph-bell' && unreadCount() > 0) {\r
              <span class="notification-badge" [attr.aria-label]="'\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F, ' + unreadCount() + ' \u043D\u0435\u043F\u0440\u043E\u0447\u0438\u0442\u0430\u043D\u043D\u044B\u0445'">\r
                {{ unreadCount() > 9 ? '9+' : unreadCount() }}\r
              </span>\r
            }\r
          </span>\r
          @if (!collapsed()) {\r
            <span class="sidebar__label">{{ item.label }}</span>\r
          }\r
        </a>\r
      </li>\r
    }\r
  </ul>\r
\r
  <!-- Spacer -->\r
  <div class="sidebar__spacer"></div>\r
\r
  <!-- Footer: user block + logout + collapse -->\r
  <div class="sidebar__footer">\r
    @if (currentUser(); as user) {\r
      @if (!collapsed()) {\r
        <div class="sidebar__user">\r
          <span class="sidebar__user-avatar" aria-hidden="true">\r
            {{ (user.id + '').slice(0, 2).toUpperCase() }}\r
          </span>\r
          <div class="sidebar__user-meta">\r
            <span class="sidebar__user-id">ID {{ user.id }}</span>\r
            <span class="sidebar__user-role">\r
              @switch (user.role) {\r
                @case ('ADMIN') { \u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440 }\r
                @case ('TEACHER') { \u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C }\r
                @case ('STUDENT') { \u0421\u0442\u0443\u0434\u0435\u043D\u0442 }\r
              }\r
            </span>\r
          </div>\r
        </div>\r
      } @else {\r
        <div class="sidebar__user sidebar__user--collapsed" [title]="'ID ' + user.id">\r
          <span class="sidebar__user-avatar" aria-hidden="true">\r
            {{ (user.id + '').slice(0, 2).toUpperCase() }}\r
          </span>\r
        </div>\r
      }\r
    }\r
\r
    <button\r
      type="button"\r
      (click)="logout()"\r
      class="sidebar__footer-btn"\r
      title="\u0412\u044B\u0439\u0442\u0438"\r
      [attr.aria-label]="collapsed() ? '\u0412\u044B\u0439\u0442\u0438' : null"\r
    >\r
      <i class="ph ph-sign-out sidebar__icon" aria-hidden="true"></i>\r
      @if (!collapsed()) {\r
        <span class="sidebar__label">\u0412\u044B\u0439\u0442\u0438</span>\r
      }\r
    </button>\r
\r
    <button\r
      type="button"\r
      (click)="toggleCollapse()"\r
      class="sidebar__footer-btn sidebar__collapse-btn"\r
      [attr.aria-label]="collapsed() ? '\u0420\u0430\u0437\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E' : '\u0421\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E'"\r
      [title]="collapsed() ? '\u0420\u0430\u0437\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E' : '\u0421\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E'"\r
    >\r
      <i\r
        [@rotateChevron]="collapsed() ? 'collapsed' : 'expanded'"\r
        class="ph ph-caret-left sidebar__icon"\r
        aria-hidden="true"\r
      ></i>\r
      @if (!collapsed()) {\r
        <span class="sidebar__label">\u0421\u0432\u0435\u0440\u043D\u0443\u0442\u044C</span>\r
      }\r
    </button>\r
  </div>\r
</nav>\r
`, styles: ['/* src/app/layout/sidebar/sidebar.component.css */\n:host {\n  display: block;\n  flex-shrink: 0;\n  height: 100vh;\n  position: sticky;\n  top: 0;\n}\n.sidebar {\n  height: 100%;\n  display: flex;\n  flex-direction: column;\n  background: var(--bg-primary);\n  border-right: 1px solid var(--border-subtle);\n  overflow: hidden;\n  position: relative;\n  background-image:\n    linear-gradient(var(--border-subtle) 1px, transparent 1px),\n    linear-gradient(\n      90deg,\n      var(--border-subtle) 1px,\n      transparent 1px);\n  background-size: 64px 64px;\n  transition: background-color var(--duration-slow) var(--ease-out), border-color var(--duration-slow) var(--ease-out);\n}\n.sidebar__brand {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 0 var(--space-5);\n  height: var(--header-height, 64px);\n  flex-shrink: 0;\n  border-bottom: 1px solid var(--border-subtle);\n  text-decoration: none;\n  color: var(--text-primary);\n}\n.sidebar--collapsed .sidebar__brand {\n  padding: 0;\n  justify-content: center;\n}\n.sidebar__brand-mark {\n  width: 38px;\n  height: 38px;\n  border-radius: 10px;\n  background: var(--gradient-brand);\n  color: var(--accent-primary-contrast);\n  display: grid;\n  place-items: center;\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 1rem;\n  flex-shrink: 0;\n  box-shadow: var(--glow-primary);\n  position: relative;\n}\n.sidebar__brand-mark::after {\n  content: "";\n  position: absolute;\n  top: 6px;\n  right: 6px;\n  width: 6px;\n  height: 6px;\n  border-radius: 50%;\n  background: #fff;\n  box-shadow: 0 0 8px rgba(255, 255, 255, 0.8);\n}\n.sidebar__brand-name {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 1.125rem;\n  color: var(--text-primary);\n  letter-spacing: -0.01em;\n  white-space: nowrap;\n}\n.sidebar__nav {\n  list-style: none;\n  margin: 0;\n  padding: var(--space-3);\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n}\n.sidebar__nav--primary {\n  padding-bottom: var(--space-2);\n}\n.sidebar__section {\n  margin: var(--space-4) var(--space-5) var(--space-2);\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  font-weight: 500;\n  color: var(--text-muted);\n  letter-spacing: 0.05em;\n  text-transform: uppercase;\n}\n.sidebar__section-divider {\n  height: 1px;\n  margin: var(--space-4) var(--space-3);\n  background: var(--border-subtle);\n}\n.sidebar__link {\n  position: relative;\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 10px 12px 10px 13px;\n  border-radius: var(--radius-md);\n  font-size: 0.875rem;\n  color: var(--text-secondary);\n  text-decoration: none;\n  transition: background-color var(--duration-base) var(--ease-out), color var(--duration-base) var(--ease-out);\n}\n.sidebar--collapsed .sidebar__link {\n  justify-content: center;\n  padding: 10px 0;\n}\n.sidebar__link:hover {\n  background: color-mix(in oklab, var(--text-primary) 5%, transparent);\n  color: var(--text-primary);\n}\n.sidebar__link--active {\n  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);\n  color: var(--accent-primary);\n  font-weight: 500;\n}\n.sidebar__link--active::before {\n  content: "";\n  position: absolute;\n  left: 0;\n  top: 8px;\n  bottom: 8px;\n  width: 3px;\n  border-radius: 0 3px 3px 0;\n  background: var(--accent-primary);\n  box-shadow: var(--glow-primary);\n}\n.sidebar--collapsed .sidebar__link--active::before {\n  left: 6px;\n  top: 10px;\n  bottom: 10px;\n}\n.sidebar__icon {\n  font-size: 22px;\n  flex-shrink: 0;\n  line-height: 1;\n}\n.sidebar__label {\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.sidebar__spacer {\n  flex: 1;\n  min-height: var(--space-4);\n}\n.sidebar__footer {\n  flex-shrink: 0;\n  padding: var(--space-3);\n  border-top: 1px solid var(--border-subtle);\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n}\n.sidebar__user {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: var(--space-3);\n  margin-bottom: var(--space-2);\n  border-radius: var(--radius-md);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n}\n.sidebar__user--collapsed {\n  justify-content: center;\n  padding: var(--space-2);\n}\n.sidebar__user-avatar {\n  width: 34px;\n  height: 34px;\n  border-radius: 50%;\n  background: var(--gradient-brand);\n  color: var(--accent-primary-contrast);\n  display: grid;\n  place-items: center;\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 0.75rem;\n  flex-shrink: 0;\n  box-shadow: var(--glow-primary);\n}\n.sidebar__user-meta {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  min-width: 0;\n  line-height: 1.2;\n}\n.sidebar__user-id {\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  color: var(--text-muted);\n}\n.sidebar__user-role {\n  font-family: var(--font-heading);\n  font-size: 0.8125rem;\n  font-weight: 500;\n  color: var(--text-primary);\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.sidebar__footer-btn {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 10px 12px;\n  border-radius: var(--radius-md);\n  background: transparent;\n  border: 0;\n  color: var(--text-secondary);\n  font-size: 0.875rem;\n  font-family: inherit;\n  cursor: pointer;\n  text-align: left;\n  transition: background-color var(--duration-base) var(--ease-out), color var(--duration-base) var(--ease-out);\n}\n.sidebar--collapsed .sidebar__footer-btn {\n  justify-content: center;\n  padding: 10px 0;\n}\n.sidebar__footer-btn:hover {\n  background: color-mix(in oklab, var(--text-primary) 5%, transparent);\n  color: var(--text-primary);\n}\n.sidebar__collapse-btn:hover .ph-caret-left {\n  color: var(--accent-primary);\n}\n.notification-badge {\n  position: absolute;\n  top: -4px;\n  right: -4px;\n  min-width: 16px;\n  height: 16px;\n  background: var(--accent-primary);\n  color: var(--accent-primary-contrast, #0A0E17);\n  border-radius: var(--radius-full, 9999px);\n  font-size: 10px;\n  font-family: var(--font-mono);\n  font-weight: 600;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  padding: 0 3px;\n  pointer-events: none;\n}\n/*# sourceMappingURL=sidebar.component.css.map */\n'] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(SidebarComponent, { className: "SidebarComponent", filePath: "src/app/layout/sidebar/sidebar.component.ts", lineNumber: 50 });
})();

// src/app/core/theme/theme-toggle.component.ts
function ThemeToggleComponent_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275namespaceSVG();
    \u0275\u0275elementStart(0, "svg", 1);
    \u0275\u0275element(1, "path", 2);
    \u0275\u0275elementEnd();
  }
}
function ThemeToggleComponent_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275namespaceSVG();
    \u0275\u0275elementStart(0, "svg", 1);
    \u0275\u0275element(1, "circle", 3)(2, "path", 4)(3, "path", 5)(4, "path", 6)(5, "path", 7)(6, "path", 8)(7, "path", 9)(8, "path", 10)(9, "path", 11);
    \u0275\u0275elementEnd();
  }
}
var ThemeToggleComponent = class _ThemeToggleComponent {
  themeService = inject(ThemeService);
  compact = false;
  static \u0275fac = function ThemeToggleComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _ThemeToggleComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _ThemeToggleComponent, selectors: [["app-theme-toggle"]], inputs: { compact: "compact" }, decls: 3, vars: 6, consts: [["type", "button", "role", "switch", 1, "theme-toggle", 3, "click", "title"], ["viewBox", "0 0 24 24", "fill", "none", "stroke", "currentColor", "stroke-width", "1.8", "stroke-linecap", "round", "stroke-linejoin", "round", "aria-hidden", "true"], ["d", "M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"], ["cx", "12", "cy", "12", "r", "4"], ["d", "M12 2v2"], ["d", "M12 20v2"], ["d", "m4.93 4.93 1.41 1.41"], ["d", "m17.66 17.66 1.41 1.41"], ["d", "M2 12h2"], ["d", "M20 12h2"], ["d", "m6.34 17.66-1.41 1.41"], ["d", "m19.07 4.93-1.41 1.41"]], template: function ThemeToggleComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "button", 0);
      \u0275\u0275listener("click", function ThemeToggleComponent_Template_button_click_0_listener() {
        return ctx.themeService.toggle();
      });
      \u0275\u0275template(1, ThemeToggleComponent_Conditional_1_Template, 2, 0, ":svg:svg", 1)(2, ThemeToggleComponent_Conditional_2_Template, 10, 0, ":svg:svg", 1);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275classProp("compact", ctx.compact);
      \u0275\u0275property("title", ctx.themeService.isDark() ? "\u0421\u0432\u0435\u0442\u043B\u0430\u044F \u0442\u0435\u043C\u0430" : "\u0422\u0451\u043C\u043D\u0430\u044F \u0442\u0435\u043C\u0430");
      \u0275\u0275attribute("aria-checked", ctx.themeService.isDark())("aria-label", ctx.themeService.isDark() ? "\u0412\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u0441\u0432\u0435\u0442\u043B\u0443\u044E \u0442\u0435\u043C\u0443" : "\u0412\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u0442\u0451\u043C\u043D\u0443\u044E \u0442\u0435\u043C\u0443");
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.themeService.isDark() ? 1 : 2);
    }
  }, styles: ["\n\n.theme-toggle[_ngcontent-%COMP%] {\n  width: 40px;\n  height: 40px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  border-radius: var(--radius-full);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  color: var(--text-primary);\n  cursor: pointer;\n  padding: 0;\n  transition:\n    background-color var(--duration-base) var(--ease-out),\n    border-color var(--duration-base) var(--ease-out),\n    transform var(--duration-fast) var(--ease-out),\n    box-shadow var(--duration-base) var(--ease-out);\n}\n.theme-toggle.compact[_ngcontent-%COMP%] {\n  width: 32px;\n  height: 32px;\n}\n.theme-toggle[_ngcontent-%COMP%]   svg[_ngcontent-%COMP%] {\n  width: 20px;\n  height: 20px;\n}\n.theme-toggle.compact[_ngcontent-%COMP%]   svg[_ngcontent-%COMP%] {\n  width: 16px;\n  height: 16px;\n}\n.theme-toggle[_ngcontent-%COMP%]:hover {\n  border-color: var(--border-accent);\n  box-shadow: var(--glow-primary);\n}\n.theme-toggle[_ngcontent-%COMP%]:active {\n  transform: scale(0.94);\n}\n.theme-toggle[_ngcontent-%COMP%]:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n/*# sourceMappingURL=theme-toggle.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(ThemeToggleComponent, [{
    type: Component,
    args: [{ selector: "app-theme-toggle", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, template: `
    <button
      type="button"
      role="switch"
      [attr.aria-checked]="themeService.isDark()"
      [attr.aria-label]="themeService.isDark() ? '\u0412\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u0441\u0432\u0435\u0442\u043B\u0443\u044E \u0442\u0435\u043C\u0443' : '\u0412\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u0442\u0451\u043C\u043D\u0443\u044E \u0442\u0435\u043C\u0443'"
      [title]="themeService.isDark() ? '\u0421\u0432\u0435\u0442\u043B\u0430\u044F \u0442\u0435\u043C\u0430' : '\u0422\u0451\u043C\u043D\u0430\u044F \u0442\u0435\u043C\u0430'"
      [class.compact]="compact"
      class="theme-toggle"
      (click)="themeService.toggle()"
    >
      @if (themeService.isDark()) {
        <!-- Moon -->
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
             stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
        </svg>
      } @else {
        <!-- Sun -->
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
             stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2" />
          <path d="M12 20v2" />
          <path d="m4.93 4.93 1.41 1.41" />
          <path d="m17.66 17.66 1.41 1.41" />
          <path d="M2 12h2" />
          <path d="M20 12h2" />
          <path d="m6.34 17.66-1.41 1.41" />
          <path d="m19.07 4.93-1.41 1.41" />
        </svg>
      }
    </button>
  `, styles: ["/* angular:styles/component:css;b34f7a7eadec0b2a1ae6a317841e571b642dc75cc96a951a3bff13ebba479c5b;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/core/theme/theme-toggle.component.ts */\n.theme-toggle {\n  width: 40px;\n  height: 40px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  border-radius: var(--radius-full);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  color: var(--text-primary);\n  cursor: pointer;\n  padding: 0;\n  transition:\n    background-color var(--duration-base) var(--ease-out),\n    border-color var(--duration-base) var(--ease-out),\n    transform var(--duration-fast) var(--ease-out),\n    box-shadow var(--duration-base) var(--ease-out);\n}\n.theme-toggle.compact {\n  width: 32px;\n  height: 32px;\n}\n.theme-toggle svg {\n  width: 20px;\n  height: 20px;\n}\n.theme-toggle.compact svg {\n  width: 16px;\n  height: 16px;\n}\n.theme-toggle:hover {\n  border-color: var(--border-accent);\n  box-shadow: var(--glow-primary);\n}\n.theme-toggle:active {\n  transform: scale(0.94);\n}\n.theme-toggle:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n/*# sourceMappingURL=theme-toggle.component.css.map */\n"] }]
  }], null, { compact: [{
    type: Input
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(ThemeToggleComponent, { className: "ThemeToggleComponent", filePath: "src/app/core/theme/theme-toggle.component.ts", lineNumber: 95 });
})();

// src/app/layout/header/header.component.ts
function HeaderComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 5)(1, "span", 6);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "div", 7)(4, "span", 8);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "span");
    \u0275\u0275text(7);
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.userInitial());
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate1("ID ", ctx.id, "");
    \u0275\u0275advance();
    \u0275\u0275classMap(ctx_r0.roleChipClass());
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.roleLabel());
  }
}
var HeaderComponent = class _HeaderComponent {
  router = inject(Router);
  auth = inject(AuthService);
  currentUser = this.auth.currentUser;
  /** Page title + eyebrow derived from the currently activated route's `data`. */
  routeState = signal({
    title: "\u041F\u0430\u043D\u0435\u043B\u044C \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u044F",
    eyebrow: "RutCampusTrack"
  });
  pageTitle = computed(() => this.routeState().title);
  pageEyebrow = computed(() => this.routeState().eyebrow);
  roleLabel = computed(() => {
    const user = this.currentUser();
    if (!user)
      return "";
    return user.role === "ADMIN" ? "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440" : "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C";
  });
  roleChipClass = computed(() => {
    const user = this.currentUser();
    if (!user)
      return "role-chip";
    return user.role === "ADMIN" ? "role-chip role-chip--admin" : "role-chip role-chip--teacher";
  });
  userInitial = computed(() => {
    const user = this.currentUser();
    if (!user)
      return "?";
    return String(user.id).slice(0, 2).toUpperCase();
  });
  constructor() {
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.updateTitleFromRoute());
    this.updateTitleFromRoute();
  }
  updateTitleFromRoute() {
    let route = this.router.routerState.snapshot.root;
    while (route.firstChild)
      route = route.firstChild;
    const data = route.data;
    this.routeState.set({
      title: data.title ?? "\u041F\u0430\u043D\u0435\u043B\u044C \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u044F",
      eyebrow: data.eyebrow ?? "RutCampusTrack"
    });
  }
  static \u0275fac = function HeaderComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeaderComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeaderComponent, selectors: [["app-header"]], decls: 9, vars: 3, consts: [[1, "shell-header"], [1, "shell-header__title"], [1, "shell-header__eyebrow"], [1, "shell-header__heading"], [1, "shell-header__actions"], ["aria-label", "\u0422\u0435\u043A\u0443\u0449\u0438\u0439 \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C", 1, "user-chip"], ["aria-hidden", "true", 1, "user-chip__avatar"], [1, "user-chip__meta"], [1, "user-chip__name"]], template: function HeaderComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "header", 0)(1, "div", 1)(2, "span", 2);
      \u0275\u0275text(3);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(4, "p", 3);
      \u0275\u0275text(5);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(6, "div", 4);
      \u0275\u0275element(7, "app-theme-toggle");
      \u0275\u0275template(8, HeaderComponent_Conditional_8_Template, 8, 5, "div", 5);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      let tmp_2_0;
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.pageEyebrow());
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate(ctx.pageTitle());
      \u0275\u0275advance(3);
      \u0275\u0275conditional((tmp_2_0 = ctx.currentUser()) ? 8 : -1, tmp_2_0);
    }
  }, dependencies: [ThemeToggleComponent], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n  position: sticky;\n  top: 0;\n  z-index: var(--z-sticky, 200);\n}\n.shell-header[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: var(--space-5);\n  height: var(--header-height, 64px);\n  padding: 0 var(--space-6);\n  background: color-mix(in oklab, var(--bg-primary) 82%, transparent);\n  backdrop-filter: blur(14px) saturate(140%);\n  -webkit-backdrop-filter: blur(14px) saturate(140%);\n  border-bottom: 1px solid var(--border-subtle);\n  transition: background-color var(--duration-slow) var(--ease-out), border-color var(--duration-slow) var(--ease-out);\n}\n.shell-header__title[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  min-width: 0;\n}\n.shell-header__eyebrow[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: var(--text-xs);\n  font-weight: 500;\n  color: var(--text-muted);\n  letter-spacing: var(--tracking-wide);\n  text-transform: uppercase;\n}\n.shell-header__heading[_ngcontent-%COMP%] {\n  margin: 0;\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: var(--text-xl);\n  color: var(--text-primary);\n  line-height: 1.2;\n  letter-spacing: -0.01em;\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.shell-header__actions[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n  flex-shrink: 0;\n}\n.user-chip[_ngcontent-%COMP%] {\n  display: none;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 6px 14px 6px 6px;\n  border-radius: var(--radius-full);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  transition: border-color var(--duration-base) var(--ease-out), background-color var(--duration-slow) var(--ease-out);\n}\n.user-chip[_ngcontent-%COMP%]:hover {\n  border-color: var(--border-accent);\n}\n@media (min-width: 768px) {\n  .user-chip[_ngcontent-%COMP%] {\n    display: inline-flex;\n  }\n}\n.user-chip__avatar[_ngcontent-%COMP%] {\n  width: 32px;\n  height: 32px;\n  border-radius: 50%;\n  display: grid;\n  place-items: center;\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 0.75rem;\n  color: var(--accent-primary-contrast);\n  background: var(--gradient-brand);\n  box-shadow: var(--glow-primary);\n}\n.user-chip__meta[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  line-height: 1;\n}\n.user-chip__name[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: var(--text-xs);\n  color: var(--text-secondary);\n  letter-spacing: 0.02em;\n}\n.user-chip[_ngcontent-%COMP%]   .role-chip[_ngcontent-%COMP%] {\n  padding: 1px 0;\n  background: transparent !important;\n}\n@media (max-width: 640px) {\n  .shell-header[_ngcontent-%COMP%] {\n    padding: 0 var(--space-4);\n    gap: var(--space-3);\n  }\n  .shell-header__heading[_ngcontent-%COMP%] {\n    font-size: var(--text-lg);\n  }\n}\n/*# sourceMappingURL=header.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeaderComponent, [{
    type: Component,
    args: [{ selector: "app-header", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [ThemeToggleComponent], template: '<header class="shell-header">\n  <div class="shell-header__title">\n    <span class="shell-header__eyebrow">{{ pageEyebrow() }}</span>\n    <p class="shell-header__heading">{{ pageTitle() }}</p>\n  </div>\n\n  <div class="shell-header__actions">\n    <app-theme-toggle />\n\n    @if (currentUser(); as user) {\n      <div class="user-chip" aria-label="\u0422\u0435\u043A\u0443\u0449\u0438\u0439 \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C">\n        <span class="user-chip__avatar" aria-hidden="true">{{ userInitial() }}</span>\n        <div class="user-chip__meta">\n          <span class="user-chip__name">ID {{ user.id }}</span>\n          <span [class]="roleChipClass()">{{ roleLabel() }}</span>\n        </div>\n      </div>\n    }\n  </div>\n</header>\n', styles: ["/* src/app/layout/header/header.component.css */\n:host {\n  display: block;\n  position: sticky;\n  top: 0;\n  z-index: var(--z-sticky, 200);\n}\n.shell-header {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: var(--space-5);\n  height: var(--header-height, 64px);\n  padding: 0 var(--space-6);\n  background: color-mix(in oklab, var(--bg-primary) 82%, transparent);\n  backdrop-filter: blur(14px) saturate(140%);\n  -webkit-backdrop-filter: blur(14px) saturate(140%);\n  border-bottom: 1px solid var(--border-subtle);\n  transition: background-color var(--duration-slow) var(--ease-out), border-color var(--duration-slow) var(--ease-out);\n}\n.shell-header__title {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  min-width: 0;\n}\n.shell-header__eyebrow {\n  font-family: var(--font-mono);\n  font-size: var(--text-xs);\n  font-weight: 500;\n  color: var(--text-muted);\n  letter-spacing: var(--tracking-wide);\n  text-transform: uppercase;\n}\n.shell-header__heading {\n  margin: 0;\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: var(--text-xl);\n  color: var(--text-primary);\n  line-height: 1.2;\n  letter-spacing: -0.01em;\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.shell-header__actions {\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n  flex-shrink: 0;\n}\n.user-chip {\n  display: none;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 6px 14px 6px 6px;\n  border-radius: var(--radius-full);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  transition: border-color var(--duration-base) var(--ease-out), background-color var(--duration-slow) var(--ease-out);\n}\n.user-chip:hover {\n  border-color: var(--border-accent);\n}\n@media (min-width: 768px) {\n  .user-chip {\n    display: inline-flex;\n  }\n}\n.user-chip__avatar {\n  width: 32px;\n  height: 32px;\n  border-radius: 50%;\n  display: grid;\n  place-items: center;\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 0.75rem;\n  color: var(--accent-primary-contrast);\n  background: var(--gradient-brand);\n  box-shadow: var(--glow-primary);\n}\n.user-chip__meta {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  line-height: 1;\n}\n.user-chip__name {\n  font-family: var(--font-mono);\n  font-size: var(--text-xs);\n  color: var(--text-secondary);\n  letter-spacing: 0.02em;\n}\n.user-chip .role-chip {\n  padding: 1px 0;\n  background: transparent !important;\n}\n@media (max-width: 640px) {\n  .shell-header {\n    padding: 0 var(--space-4);\n    gap: var(--space-3);\n  }\n  .shell-header__heading {\n    font-size: var(--text-lg);\n  }\n}\n/*# sourceMappingURL=header.component.css.map */\n"] }]
  }], () => [], null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeaderComponent, { className: "HeaderComponent", filePath: "src/app/layout/header/header.component.ts", lineNumber: 23 });
})();

// src/app/layout/shell/shell.component.ts
var ShellComponent = class _ShellComponent {
  static \u0275fac = function ShellComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _ShellComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _ShellComponent, selectors: [["app-shell"]], decls: 7, vars: 0, consts: [[1, "shell"], [1, "shell__sidebar"], [1, "shell__column"], ["tabindex", "-1", 1, "shell__main"], [1, "shell__content"]], template: function ShellComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0);
      \u0275\u0275element(1, "app-sidebar", 1);
      \u0275\u0275elementStart(2, "div", 2);
      \u0275\u0275element(3, "app-header");
      \u0275\u0275elementStart(4, "main", 3)(5, "div", 4);
      \u0275\u0275element(6, "router-outlet");
      \u0275\u0275elementEnd()()()();
    }
  }, dependencies: [RouterOutlet, SidebarComponent, HeaderComponent], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n  height: 100vh;\n  background: var(--bg-primary);\n  color: var(--text-primary);\n}\n.shell[_ngcontent-%COMP%] {\n  display: flex;\n  height: 100%;\n  overflow: hidden;\n}\n.shell__sidebar[_ngcontent-%COMP%] {\n  flex-shrink: 0;\n}\n.shell__column[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  flex: 1;\n  min-width: 0;\n  min-height: 0;\n  overflow: hidden;\n  background:\n    radial-gradient(\n      ellipse 70% 50% at 50% 0%,\n      color-mix(in oklab, var(--accent-primary) 4%, transparent),\n      transparent 60%),\n    var(--bg-primary);\n}\n.shell__main[_ngcontent-%COMP%] {\n  flex: 1;\n  overflow-y: auto;\n  overflow-x: hidden;\n  outline: none;\n  scrollbar-gutter: stable;\n}\n.shell__content[_ngcontent-%COMP%] {\n  max-width: var(--content-max-width, 1280px);\n  margin: 0 auto;\n  padding: var(--space-6) var(--space-6) var(--space-9);\n}\n@media (max-width: 768px) {\n  .shell__content[_ngcontent-%COMP%] {\n    padding: var(--space-4) var(--space-4) var(--space-8);\n  }\n}\n.shell__main[_ngcontent-%COMP%]::-webkit-scrollbar {\n  width: 12px;\n  height: 12px;\n}\n.shell__main[_ngcontent-%COMP%]::-webkit-scrollbar-track {\n  background: transparent;\n}\n.shell__main[_ngcontent-%COMP%]::-webkit-scrollbar-thumb {\n  background: var(--border-default);\n  border-radius: var(--radius-full);\n  border: 3px solid var(--bg-primary);\n}\n.shell__main[_ngcontent-%COMP%]::-webkit-scrollbar-thumb:hover {\n  background: var(--border-accent);\n}\n/*# sourceMappingURL=shell.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(ShellComponent, [{
    type: Component,
    args: [{ selector: "app-shell", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [RouterOutlet, SidebarComponent, HeaderComponent], template: '<div class="shell">\n  <app-sidebar class="shell__sidebar" />\n\n  <div class="shell__column">\n    <app-header />\n    <main class="shell__main" tabindex="-1">\n      <div class="shell__content">\n        <router-outlet />\n      </div>\n    </main>\n  </div>\n</div>\n', styles: ["/* src/app/layout/shell/shell.component.css */\n:host {\n  display: block;\n  height: 100vh;\n  background: var(--bg-primary);\n  color: var(--text-primary);\n}\n.shell {\n  display: flex;\n  height: 100%;\n  overflow: hidden;\n}\n.shell__sidebar {\n  flex-shrink: 0;\n}\n.shell__column {\n  display: flex;\n  flex-direction: column;\n  flex: 1;\n  min-width: 0;\n  min-height: 0;\n  overflow: hidden;\n  background:\n    radial-gradient(\n      ellipse 70% 50% at 50% 0%,\n      color-mix(in oklab, var(--accent-primary) 4%, transparent),\n      transparent 60%),\n    var(--bg-primary);\n}\n.shell__main {\n  flex: 1;\n  overflow-y: auto;\n  overflow-x: hidden;\n  outline: none;\n  scrollbar-gutter: stable;\n}\n.shell__content {\n  max-width: var(--content-max-width, 1280px);\n  margin: 0 auto;\n  padding: var(--space-6) var(--space-6) var(--space-9);\n}\n@media (max-width: 768px) {\n  .shell__content {\n    padding: var(--space-4) var(--space-4) var(--space-8);\n  }\n}\n.shell__main::-webkit-scrollbar {\n  width: 12px;\n  height: 12px;\n}\n.shell__main::-webkit-scrollbar-track {\n  background: transparent;\n}\n.shell__main::-webkit-scrollbar-thumb {\n  background: var(--border-default);\n  border-radius: var(--radius-full);\n  border: 3px solid var(--bg-primary);\n}\n.shell__main::-webkit-scrollbar-thumb:hover {\n  background: var(--border-accent);\n}\n/*# sourceMappingURL=shell.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(ShellComponent, { className: "ShellComponent", filePath: "src/app/layout/shell/shell.component.ts", lineNumber: 22 });
})();
export {
  ShellComponent
};
//# sourceMappingURL=chunk-3GLKYJCI.js.map
