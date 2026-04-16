import {
  StatCardComponent
} from "./chunk-6SXDTYWQ.js";
import {
  AdminApiService
} from "./chunk-LPTENPUF.js";
import {
  RouterLink
} from "./chunk-643P6YYN.js";
import "./chunk-7WMFDRFR.js";
import {
  BaseChartDirective
} from "./chunk-XO6TQHL6.js";
import "./chunk-KLKVWNKW.js";
import "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵnextContext,
  ɵɵproperty,
  ɵɵpropertyInterpolate,
  ɵɵpureFunction0,
  ɵɵpureFunction1,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵrepeaterTrackByIdentity,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-MFRIGFR2.js";

// src/app/features/admin/dashboard/admin-dashboard.component.ts
var _c0 = () => ({ role: "STUDENT" });
var _c1 = () => ({ role: "TEACHER" });
var _c2 = () => ({ status: "active" });
var _c3 = () => ({ action: "create", role: "TEACHER" });
var _c4 = () => ({ action: "create", role: "STUDENT" });
var _c5 = () => ({ action: "create" });
var _c6 = () => [1, 2, 3, 4];
var _c7 = () => ["/admin/groups"];
var _c8 = (a0) => ({ id: a0 });
var _forTrack0 = ($index, $item) => $item.id;
function AdminDashboardComponent_Conditional_38_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 27);
    \u0275\u0275element(1, "i", 52);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.error(), " ");
  }
}
function AdminDashboardComponent_Conditional_97_For_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "tr", 53)(1, "td");
    \u0275\u0275element(2, "span", 54);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "td");
    \u0275\u0275element(4, "span", 55);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "td");
    \u0275\u0275element(6, "span", 55);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(7, "td");
    \u0275\u0275element(8, "span", 55);
    \u0275\u0275elementEnd()();
  }
}
function AdminDashboardComponent_Conditional_97_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275repeaterCreate(0, AdminDashboardComponent_Conditional_97_For_1_Template, 9, 0, "tr", 53, \u0275\u0275repeaterTrackByIdentity);
  }
  if (rf & 2) {
    \u0275\u0275repeater(\u0275\u0275pureFunction0(0, _c6));
  }
}
function AdminDashboardComponent_Conditional_98_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "tr")(1, "td", 56);
    \u0275\u0275text(2, "\u041D\u0435\u0442 \u0434\u0430\u043D\u043D\u044B\u0445 \u043E \u0433\u0440\u0443\u043F\u043F\u0430\u0445.");
    \u0275\u0275elementEnd()();
  }
}
function AdminDashboardComponent_Conditional_99_For_1_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 59);
    \u0275\u0275element(1, "span", 62);
    \u0275\u0275text(2, " \u0410\u043A\u0442\u0438\u0432\u043D\u0430 ");
    \u0275\u0275elementEnd();
  }
}
function AdminDashboardComponent_Conditional_99_For_1_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 60);
    \u0275\u0275element(1, "i", 63);
    \u0275\u0275text(2, " \u0410\u0440\u0445\u0438\u0432 ");
    \u0275\u0275elementEnd();
  }
}
function AdminDashboardComponent_Conditional_99_For_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "tr", 53)(1, "td")(2, "a", 57);
    \u0275\u0275element(3, "span", 58);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(5, "td");
    \u0275\u0275template(6, AdminDashboardComponent_Conditional_99_For_1_Conditional_6_Template, 3, 0, "span", 59)(7, AdminDashboardComponent_Conditional_99_For_1_Conditional_7_Template, 3, 0, "span", 60);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(8, "td", 61);
    \u0275\u0275text(9);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(10, "td", 61);
    \u0275\u0275text(11);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const g_r2 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(2);
    \u0275\u0275property("routerLink", \u0275\u0275pureFunction0(6, _c7))("queryParams", \u0275\u0275pureFunction1(7, _c8, g_r2.id));
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", g_r2.name, " ");
    \u0275\u0275advance(2);
    \u0275\u0275conditional(g_r2.active ? 6 : 7);
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate(ctx_r0.formatGroupDate(g_r2.createdAt));
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.formatGroupDate(g_r2.archivedAt));
  }
}
function AdminDashboardComponent_Conditional_99_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275repeaterCreate(0, AdminDashboardComponent_Conditional_99_For_1_Template, 12, 9, "tr", 53, _forTrack0);
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275repeater(ctx_r0.recentGroups());
  }
}
var AdminDashboardComponent = class _AdminDashboardComponent {
  adminApi = inject(AdminApiService);
  destroyRef = inject(DestroyRef);
  stats = signal(null);
  loading = signal(false);
  error = signal(null);
  /** Recently touched groups — used for the §4.4 table (capped at 8). */
  recentGroups = signal([]);
  groupsLoading = signal(false);
  /** Live clock — refreshed once a minute. */
  _now = signal(/* @__PURE__ */ new Date());
  timeLabel = computed(() => this._now().toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" }));
  dateLabel = computed(() => this._now().toLocaleDateString("ru-RU", {
    weekday: "long",
    day: "numeric",
    month: "long"
  }));
  greeting = computed(() => {
    const hour = this._now().getHours();
    if (hour < 6)
      return "\u0414\u043E\u0431\u0440\u043E\u0439 \u043D\u043E\u0447\u0438";
    if (hour < 12)
      return "\u0414\u043E\u0431\u0440\u043E\u0435 \u0443\u0442\u0440\u043E";
    if (hour < 18)
      return "\u0414\u043E\u0431\u0440\u044B\u0439 \u0434\u0435\u043D\u044C";
    return "\u0414\u043E\u0431\u0440\u044B\u0439 \u0432\u0435\u0447\u0435\u0440";
  });
  /** Share of groups that are active (0-100). */
  activeGroupsPct = computed(() => {
    const s = this.stats();
    if (!s || s.totalGroups === 0)
      return 0;
    return Math.round(s.activeGroups / s.totalGroups * 100);
  });
  /** Illustrative sparklines (§4.3). Derived deterministically from the headline
   * number so the trace always ends on the real total. Kept client-side until a
   * real time-series endpoint lands. */
  studentsSpark = computed(() => buildSpark(this.stats()?.totalStudents ?? 0, 7, 0.88));
  teachersSpark = computed(() => buildSpark(this.stats()?.totalTeachers ?? 0, 7, 0.92));
  groupsSpark = computed(() => buildSpark(this.stats()?.totalGroups ?? 0, 7, 0.85));
  activeGroupsSpark = computed(() => buildSpark(this.stats()?.activeGroups ?? 0, 7, 0.9));
  /** Line chart for the «посещаемость за 7 дней» card (§5.3 draw-in). */
  chartData = computed(() => {
    const points = buildSpark(this.stats()?.activeGroups ?? 0, 7, 0.78).map((v, i, arr) => {
      const min = Math.min(...arr);
      const max = Math.max(...arr) || 1;
      const norm = max === min ? 0.85 : (v - min) / (max - min);
      return Math.round((78 + norm * 16) * 10) / 10;
    });
    return {
      labels: ["\u041F\u043D", "\u0412\u0442", "\u0421\u0440", "\u0427\u0442", "\u041F\u0442", "\u0421\u0431", "\u0412\u0441"],
      datasets: [
        {
          data: points,
          borderColor: "rgba(0, 229, 160, 1)",
          backgroundColor: (ctx) => {
            const { ctx: canvas, chartArea } = ctx.chart;
            if (!chartArea)
              return "rgba(0, 229, 160, 0.12)";
            const g = canvas.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
            g.addColorStop(0, "rgba(0, 229, 160, 0.28)");
            g.addColorStop(1, "rgba(0, 229, 160, 0)");
            return g;
          },
          fill: true,
          borderWidth: 2,
          tension: 0.35,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: "rgba(0, 229, 160, 1)",
          pointHoverBorderColor: "#0A0E17",
          pointHoverBorderWidth: 2
        }
      ]
    };
  });
  chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {
      duration: typeof window !== "undefined" && window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ? 0 : 900,
      easing: "easeOutQuart"
    },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: "rgba(26, 34, 54, 0.96)",
        borderColor: "rgba(0, 229, 160, 0.3)",
        borderWidth: 1,
        titleColor: "#F0F2F5",
        bodyColor: "#F0F2F5",
        padding: 12,
        displayColors: false,
        callbacks: { label: (ctx) => `${ctx.parsed.y}% \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438` }
      }
    },
    scales: {
      x: {
        grid: { display: false },
        ticks: { color: "rgba(139, 149, 168, 0.9)", font: { family: "JetBrains Mono", size: 11 } },
        border: { display: false }
      },
      y: {
        min: 70,
        max: 100,
        grid: { color: "rgba(139, 149, 168, 0.12)" },
        ticks: {
          color: "rgba(139, 149, 168, 0.9)",
          font: { family: "JetBrains Mono", size: 11 },
          callback: (v) => `${v}%`
        },
        border: { display: false }
      }
    }
  };
  ngOnInit() {
    this.loading.set(true);
    this.adminApi.getDashboardStats().subscribe({
      next: (data) => {
        this.stats.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0441\u0432\u043E\u0434\u043A\u0443. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043F\u043E\u0437\u0436\u0435.");
        this.loading.set(false);
      }
    });
    this.groupsLoading.set(true);
    this.adminApi.listGroups().subscribe({
      next: (groups) => {
        const sorted = [...groups].sort((a, b) => (b.createdAt ?? "").localeCompare(a.createdAt ?? ""));
        this.recentGroups.set(sorted.slice(0, 8));
        this.groupsLoading.set(false);
      },
      error: () => this.groupsLoading.set(false)
    });
    const tick = setInterval(() => this._now.set(/* @__PURE__ */ new Date()), 6e4);
    this.destroyRef.onDestroy(() => clearInterval(tick));
  }
  formatGroupDate(iso) {
    if (!iso)
      return "\u2014";
    const d = new Date(iso);
    if (Number.isNaN(d.getTime()))
      return "\u2014";
    return d.toLocaleDateString("ru-RU", { day: "2-digit", month: "short", year: "numeric" });
  }
  static \u0275fac = function AdminDashboardComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _AdminDashboardComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _AdminDashboardComponent, selectors: [["app-admin-dashboard"]], decls: 100, vars: 34, consts: [[1, "dashboard"], [1, "page-hero"], [1, "page-hero__body"], [1, "page-hero__eyebrow"], ["aria-hidden", "true", 1, "page-hero__pulse"], [1, "page-hero__title"], [1, "page-hero__subtitle"], ["aria-hidden", "true", 1, "page-hero__aside"], [1, "page-hero__aside-value"], [1, "page-hero__aside-label"], [1, "dashboard__grid"], ["routerLink", "/admin/users", "aria-label", "\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u044E \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430\u043C\u0438", 1, "dashboard__stat-link", 3, "queryParams"], ["accent", "primary", "icon", "ph-duotone ph-students", "label", "\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432", 3, "value", "sparkData", "loading"], ["routerLink", "/admin/users", "aria-label", "\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u044E \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044F\u043C\u0438", 1, "dashboard__stat-link", 3, "queryParams"], ["accent", "secondary", "icon", "ph-duotone ph-chalkboard-teacher", "label", "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0435\u0439", 3, "value", "sparkData", "loading"], ["routerLink", "/admin/groups", "aria-label", "\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0433\u0440\u0443\u043F\u043F\u0430\u043C", 1, "dashboard__stat-link"], ["accent", "warning", "icon", "ph-duotone ph-users-three", "label", "\u0413\u0440\u0443\u043F\u043F \u0432\u0441\u0435\u0433\u043E", 3, "value", "sparkData", "loading"], ["routerLink", "/admin/groups", "aria-label", "\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u043C \u0433\u0440\u0443\u043F\u043F\u0430\u043C", 1, "dashboard__stat-link", 3, "queryParams"], ["accent", "info", "icon", "ph-duotone ph-buildings", "label", "\u0410\u043A\u0442\u0438\u0432\u043D\u044B\u0445 \u0433\u0440\u0443\u043F\u043F", 3, "value", "suffix", "sparkData", "loading"], ["aria-labelledby", "dashboard-chart-title", 1, "chart-card", "data-card-in"], [1, "chart-card__head"], [1, "chart-card__eyebrow"], ["id", "dashboard-chart-title", 1, "chart-card__title"], ["aria-hidden", "true", 1, "chart-card__badge"], [1, "ph", "ph-trend-up"], [1, "chart-card__canvas"], ["baseChart", "", "role", "img", "aria-label", "\u0413\u0440\u0430\u0444\u0438\u043A \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438 \u0437\u0430 \u043F\u043E\u0441\u043B\u0435\u0434\u043D\u0438\u0435 7 \u0434\u043D\u0435\u0439", 3, "data", "options", "type"], ["role", "alert", 1, "dashboard__error"], [1, "dashboard__section"], [1, "section-head"], [1, "section-head__title"], [1, "section-head__sub"], [1, "dashboard__actions"], ["routerLink", "/admin/users", "data-accent", "secondary", 1, "action-card", 3, "queryParams"], ["aria-hidden", "true", 1, "action-card__icon"], [1, "ph-duotone", "ph-chalkboard-teacher"], [1, "action-card__body"], [1, "action-card__title"], [1, "action-card__text"], ["aria-hidden", "true", 1, "ph", "ph-arrow-right", "action-card__arrow"], ["routerLink", "/admin/users", "data-accent", "primary", 1, "action-card", 3, "queryParams"], [1, "ph-duotone", "ph-student"], ["routerLink", "/admin/groups", "data-accent", "warning", 1, "action-card", 3, "queryParams"], [1, "ph-duotone", "ph-users-three"], [1, "section-head", "section-head--row"], ["routerLink", "/admin/groups", 1, "section-link"], ["aria-hidden", "true", 1, "ph", "ph-arrow-right"], ["role", "region", "aria-label", "\u041D\u0435\u0434\u0430\u0432\u043D\u0438\u0435 \u0433\u0440\u0443\u043F\u043F\u044B", 1, "table-card", "data-card-in"], [1, "table-card__scroll"], [1, "table-card__table"], ["scope", "col"], ["scope", "col", 1, "table-card__col-date"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"], [1, "table-card__row"], [1, "table-card__skeleton", "table-card__skeleton--wide"], [1, "table-card__skeleton"], ["colspan", "4", 1, "table-card__empty"], [1, "table-card__name", 3, "routerLink", "queryParams"], ["aria-hidden", "true", 1, "table-card__dot"], [1, "pill", "pill--success"], [1, "pill", "pill--muted"], [1, "table-card__mono"], ["aria-hidden", "true", 1, "pill__dot"], ["aria-hidden", "true", 1, "ph", "ph-archive"]], template: function AdminDashboardComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "section", 0)(1, "header", 1)(2, "div", 2)(3, "p", 3);
      \u0275\u0275element(4, "span", 4);
      \u0275\u0275text(5, " \u0421\u0438\u0441\u0442\u0435\u043C\u0430 \u0430\u043A\u0442\u0438\u0432\u043D\u0430 ");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(6, "h2", 5);
      \u0275\u0275text(7);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(8, "p", 6);
      \u0275\u0275text(9);
      \u0275\u0275elementStart(10, "strong");
      \u0275\u0275text(11);
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(12, "div", 7)(13, "span", 8);
      \u0275\u0275text(14);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(15, "span", 9);
      \u0275\u0275text(16, "\u043F\u043E \u041C\u043E\u0441\u043A\u0432\u0435");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(17, "div", 10)(18, "a", 11);
      \u0275\u0275element(19, "app-stat-card", 12);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(20, "a", 13);
      \u0275\u0275element(21, "app-stat-card", 14);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(22, "a", 15);
      \u0275\u0275element(23, "app-stat-card", 16);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(24, "a", 17);
      \u0275\u0275element(25, "app-stat-card", 18);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(26, "section", 19)(27, "header", 20)(28, "div")(29, "p", 21);
      \u0275\u0275text(30, "\u041F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C \xB7 7 \u0434\u043D\u0435\u0439");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(31, "h3", 22);
      \u0275\u0275text(32, "\u0414\u0438\u043D\u0430\u043C\u0438\u043A\u0430 \u0437\u0430 \u043D\u0435\u0434\u0435\u043B\u044E");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(33, "span", 23);
      \u0275\u0275element(34, "i", 24);
      \u0275\u0275text(35, " live ");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(36, "div", 25);
      \u0275\u0275element(37, "canvas", 26);
      \u0275\u0275elementEnd()();
      \u0275\u0275template(38, AdminDashboardComponent_Conditional_38_Template, 3, 1, "div", 27);
      \u0275\u0275elementStart(39, "div", 28)(40, "div", 29)(41, "h3", 30);
      \u0275\u0275text(42, "\u0411\u044B\u0441\u0442\u0440\u044B\u0435 \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044F");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(43, "p", 31);
      \u0275\u0275text(44, "\u0427\u0430\u0441\u0442\u043E \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u043C\u044B\u0435 \u0440\u0430\u0437\u0434\u0435\u043B\u044B \u043F\u0430\u043D\u0435\u043B\u0438");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(45, "div", 32)(46, "a", 33)(47, "div", 34);
      \u0275\u0275element(48, "i", 35);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(49, "div", 36)(50, "h4", 37);
      \u0275\u0275text(51, "\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044F");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(52, "p", 38);
      \u0275\u0275text(53, "\u041D\u043E\u0432\u0430\u044F \u0443\u0447\u0451\u0442\u043D\u0430\u044F \u0437\u0430\u043F\u0438\u0441\u044C \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044F");
      \u0275\u0275elementEnd()();
      \u0275\u0275element(54, "i", 39);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(55, "a", 40)(56, "div", 34);
      \u0275\u0275element(57, "i", 41);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(58, "div", 36)(59, "h4", 37);
      \u0275\u0275text(60, "\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(61, "p", 38);
      \u0275\u0275text(62, "\u041D\u043E\u0432\u0430\u044F \u0443\u0447\u0451\u0442\u043D\u0430\u044F \u0437\u0430\u043F\u0438\u0441\u044C \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430 \u0441 \u043F\u0440\u0438\u0432\u044F\u0437\u043A\u043E\u0439 \u043A \u0433\u0440\u0443\u043F\u043F\u0435");
      \u0275\u0275elementEnd()();
      \u0275\u0275element(63, "i", 39);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(64, "a", 42)(65, "div", 34);
      \u0275\u0275element(66, "i", 43);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(67, "div", 36)(68, "h4", 37);
      \u0275\u0275text(69, "\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(70, "p", 38);
      \u0275\u0275text(71, "\u041D\u043E\u0432\u0430\u044F \u0443\u0447\u0435\u0431\u043D\u0430\u044F \u0433\u0440\u0443\u043F\u043F\u0430");
      \u0275\u0275elementEnd()();
      \u0275\u0275element(72, "i", 39);
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(73, "div", 28)(74, "div", 44)(75, "div")(76, "h3", 30);
      \u0275\u0275text(77, "\u041D\u0435\u0434\u0430\u0432\u043D\u0438\u0435 \u0433\u0440\u0443\u043F\u043F\u044B");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(78, "p", 31);
      \u0275\u0275text(79, "\u041F\u043E\u0441\u043B\u0435\u0434\u043D\u0438\u0435 \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F \u0432 \u0443\u0447\u0435\u0431\u043D\u044B\u0445 \u0433\u0440\u0443\u043F\u043F\u0430\u0445");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(80, "a", 45);
      \u0275\u0275text(81, " \u0412\u0441\u0435 \u0433\u0440\u0443\u043F\u043F\u044B ");
      \u0275\u0275element(82, "i", 46);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(83, "div", 47)(84, "div", 48)(85, "table", 49)(86, "thead")(87, "tr")(88, "th", 50);
      \u0275\u0275text(89, "\u0413\u0440\u0443\u043F\u043F\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(90, "th", 50);
      \u0275\u0275text(91, "\u0421\u0442\u0430\u0442\u0443\u0441");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(92, "th", 51);
      \u0275\u0275text(93, "\u0421\u043E\u0437\u0434\u0430\u043D\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(94, "th", 51);
      \u0275\u0275text(95, "\u0410\u0440\u0445\u0438\u0432");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(96, "tbody");
      \u0275\u0275template(97, AdminDashboardComponent_Conditional_97_Template, 2, 1)(98, AdminDashboardComponent_Conditional_98_Template, 3, 0, "tr")(99, AdminDashboardComponent_Conditional_99_Template, 2, 0);
      \u0275\u0275elementEnd()()()()()();
    }
    if (rf & 2) {
      let tmp_2_0;
      let tmp_5_0;
      let tmp_9_0;
      let tmp_12_0;
      let tmp_17_0;
      \u0275\u0275advance(7);
      \u0275\u0275textInterpolate1("", ctx.greeting(), ", \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440");
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate1(" ", ctx.dateLabel(), " \xB7 \u0442\u0435\u043A\u0443\u0449\u0438\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440 ");
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate((tmp_2_0 = (tmp_2_0 = ctx.stats()) == null ? null : tmp_2_0.activeSemesterName) !== null && tmp_2_0 !== void 0 ? tmp_2_0 : "\u2014");
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.timeLabel());
      \u0275\u0275advance(4);
      \u0275\u0275property("queryParams", \u0275\u0275pureFunction0(28, _c0));
      \u0275\u0275advance();
      \u0275\u0275property("value", (tmp_5_0 = (tmp_5_0 = ctx.stats()) == null ? null : tmp_5_0.totalStudents) !== null && tmp_5_0 !== void 0 ? tmp_5_0 : 0)("sparkData", ctx.studentsSpark())("loading", ctx.loading());
      \u0275\u0275advance();
      \u0275\u0275property("queryParams", \u0275\u0275pureFunction0(29, _c1));
      \u0275\u0275advance();
      \u0275\u0275property("value", (tmp_9_0 = (tmp_9_0 = ctx.stats()) == null ? null : tmp_9_0.totalTeachers) !== null && tmp_9_0 !== void 0 ? tmp_9_0 : 0)("sparkData", ctx.teachersSpark())("loading", ctx.loading());
      \u0275\u0275advance(2);
      \u0275\u0275property("value", (tmp_12_0 = (tmp_12_0 = ctx.stats()) == null ? null : tmp_12_0.totalGroups) !== null && tmp_12_0 !== void 0 ? tmp_12_0 : 0)("sparkData", ctx.groupsSpark())("loading", ctx.loading());
      \u0275\u0275advance();
      \u0275\u0275property("queryParams", \u0275\u0275pureFunction0(30, _c2));
      \u0275\u0275advance();
      \u0275\u0275propertyInterpolate("suffix", ctx.activeGroupsPct() ? ctx.activeGroupsPct() + "%" : "");
      \u0275\u0275property("value", (tmp_17_0 = (tmp_17_0 = ctx.stats()) == null ? null : tmp_17_0.activeGroups) !== null && tmp_17_0 !== void 0 ? tmp_17_0 : 0)("sparkData", ctx.activeGroupsSpark())("loading", ctx.loading());
      \u0275\u0275advance(12);
      \u0275\u0275property("data", ctx.chartData())("options", ctx.chartOptions)("type", "line");
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.error() ? 38 : -1);
      \u0275\u0275advance(8);
      \u0275\u0275property("queryParams", \u0275\u0275pureFunction0(31, _c3));
      \u0275\u0275advance(9);
      \u0275\u0275property("queryParams", \u0275\u0275pureFunction0(32, _c4));
      \u0275\u0275advance(9);
      \u0275\u0275property("queryParams", \u0275\u0275pureFunction0(33, _c5));
      \u0275\u0275advance(33);
      \u0275\u0275conditional(ctx.groupsLoading() ? 97 : ctx.recentGroups().length === 0 ? 98 : 99);
    }
  }, dependencies: [StatCardComponent, RouterLink, BaseChartDirective], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.dashboard[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-7);\n  animation: _ngcontent-%COMP%_dashboard-in 0.6s var(--ease-out) both;\n}\n@keyframes _ngcontent-%COMP%_dashboard-in {\n  from {\n    opacity: 0;\n    transform: translateY(12px);\n  }\n  to {\n    opacity: 1;\n    transform: translateY(0);\n  }\n}\n.dashboard__grid[_ngcontent-%COMP%] {\n  display: grid;\n  gap: var(--space-4);\n  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));\n}\n.dashboard__stat-link[_ngcontent-%COMP%] {\n  display: block;\n  text-decoration: none;\n  color: inherit;\n  border-radius: var(--radius-lg);\n  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);\n  outline: none;\n}\n.dashboard__stat-link[_ngcontent-%COMP%]:hover {\n  transform: translateY(-2px);\n}\n.dashboard__stat-link[_ngcontent-%COMP%]:focus-visible {\n  box-shadow: 0 0 0 2px color-mix(in oklab, var(--accent-primary) 55%, transparent);\n}\n.dashboard__grid[_ngcontent-%COMP%]    > app-stat-card[_ngcontent-%COMP%], \n.dashboard__grid[_ngcontent-%COMP%]    > .dashboard__stat-link[_ngcontent-%COMP%] {\n  animation: data-card-in 0.5s var(--ease-out) both;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > [_ngcontent-%COMP%]:nth-child(1) {\n  animation-delay: 0.05s;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > [_ngcontent-%COMP%]:nth-child(2) {\n  animation-delay: 0.12s;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > [_ngcontent-%COMP%]:nth-child(3) {\n  animation-delay: 0.19s;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > [_ngcontent-%COMP%]:nth-child(4) {\n  animation-delay: 0.26s;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > [_ngcontent-%COMP%]:nth-child(5) {\n  animation-delay: 0.33s;\n}\n.dashboard__error[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: var(--space-4) var(--space-5);\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 30%, transparent);\n  color: var(--accent-danger);\n  font-size: 0.875rem;\n}\n.dashboard__error[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 20px;\n}\n.dashboard__section[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-5);\n}\n.dashboard__actions[_ngcontent-%COMP%] {\n  display: grid;\n  gap: var(--space-4);\n  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));\n}\n.action-card[_ngcontent-%COMP%] {\n  --stat-color: var(--accent-primary);\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n  padding: var(--space-5);\n  border-radius: var(--radius-lg);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  text-decoration: none;\n  color: inherit;\n  position: relative;\n  overflow: hidden;\n  transition:\n    transform var(--duration-slow) var(--ease-out),\n    border-color var(--duration-slow) var(--ease-out),\n    box-shadow var(--duration-slow) var(--ease-out);\n}\n.action-card[data-accent=warning][_ngcontent-%COMP%] {\n  --stat-color: var(--accent-warning);\n}\n.action-card[data-accent=info][_ngcontent-%COMP%] {\n  --stat-color: var(--accent-info);\n}\n.action-card[_ngcontent-%COMP%]:hover {\n  transform: translateY(-2px);\n  border-color: color-mix(in oklab, var(--stat-color) 40%, transparent);\n  box-shadow: 0 0 24px color-mix(in oklab, var(--stat-color) 14%, transparent), var(--shadow-md);\n}\n.action-card[_ngcontent-%COMP%]:hover   .action-card__arrow[_ngcontent-%COMP%] {\n  transform: translateX(4px);\n  color: var(--stat-color);\n}\n.action-card__icon[_ngcontent-%COMP%] {\n  width: 52px;\n  height: 52px;\n  border-radius: var(--radius-md);\n  display: grid;\n  place-items: center;\n  background: color-mix(in oklab, var(--stat-color) 14%, transparent);\n  border: 1px solid color-mix(in oklab, var(--stat-color) 28%, transparent);\n  color: var(--stat-color);\n  flex-shrink: 0;\n}\n.action-card__icon[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 24px;\n}\n.action-card__body[_ngcontent-%COMP%] {\n  flex: 1;\n  min-width: 0;\n}\n.action-card__title[_ngcontent-%COMP%] {\n  margin: 0 0 4px;\n  font: 600 1rem/1.2 var(--font-heading);\n  color: var(--text-primary);\n}\n.action-card__text[_ngcontent-%COMP%] {\n  margin: 0;\n  font-size: 0.8125rem;\n  line-height: 1.4;\n  color: var(--text-secondary);\n}\n.action-card__arrow[_ngcontent-%COMP%] {\n  color: var(--text-muted);\n  font-size: 20px;\n  transition: transform var(--duration-base) var(--ease-out), color var(--duration-base) var(--ease-out);\n}\n.dashboard[_ngcontent-%COMP%]    > .chart-card[_ngcontent-%COMP%] {\n  animation-delay: 0.38s;\n}\n.dashboard[_ngcontent-%COMP%]    > .dashboard__section[_ngcontent-%COMP%]   .table-card[_ngcontent-%COMP%] {\n  animation-delay: 0.46s;\n}\n@media (prefers-reduced-motion: reduce) {\n  .dashboard[_ngcontent-%COMP%], \n   .dashboard__grid[_ngcontent-%COMP%]    > app-stat-card[_ngcontent-%COMP%] {\n    animation: none;\n  }\n}\n/*# sourceMappingURL=admin-dashboard.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(AdminDashboardComponent, [{
    type: Component,
    args: [{ selector: "app-admin-dashboard", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [StatCardComponent, RouterLink, BaseChartDirective], template: `<section class="dashboard">
  <!-- Greeting + live clock -->
  <header class="page-hero">
    <div class="page-hero__body">
      <p class="page-hero__eyebrow">
        <span class="page-hero__pulse" aria-hidden="true"></span>
        \u0421\u0438\u0441\u0442\u0435\u043C\u0430 \u0430\u043A\u0442\u0438\u0432\u043D\u0430
      </p>
      <h2 class="page-hero__title">{{ greeting() }}, \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440</h2>
      <p class="page-hero__subtitle">
        {{ dateLabel() }} \xB7 \u0442\u0435\u043A\u0443\u0449\u0438\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440
        <strong>{{ stats()?.activeSemesterName ?? '\u2014' }}</strong>
      </p>
    </div>

    <div class="page-hero__aside" aria-hidden="true">
      <span class="page-hero__aside-value">{{ timeLabel() }}</span>
      <span class="page-hero__aside-label">\u043F\u043E \u041C\u043E\u0441\u043A\u0432\u0435</span>
    </div>
  </header>

  <!-- Stat grid (clickable \u2014 BUG-005) -->
  <div class="dashboard__grid">
    <a routerLink="/admin/users" [queryParams]="{ role: 'STUDENT' }" class="dashboard__stat-link" aria-label="\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u044E \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430\u043C\u0438">
      <app-stat-card
        accent="primary"
        icon="ph-duotone ph-students"
        [value]="stats()?.totalStudents ?? 0"
        label="\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432"
        [sparkData]="studentsSpark()"
        [loading]="loading()"
      />
    </a>
    <a routerLink="/admin/users" [queryParams]="{ role: 'TEACHER' }" class="dashboard__stat-link" aria-label="\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u044E \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044F\u043C\u0438">
      <app-stat-card
        accent="secondary"
        icon="ph-duotone ph-chalkboard-teacher"
        [value]="stats()?.totalTeachers ?? 0"
        label="\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0435\u0439"
        [sparkData]="teachersSpark()"
        [loading]="loading()"
      />
    </a>
    <a routerLink="/admin/groups" class="dashboard__stat-link" aria-label="\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0433\u0440\u0443\u043F\u043F\u0430\u043C">
      <app-stat-card
        accent="warning"
        icon="ph-duotone ph-users-three"
        [value]="stats()?.totalGroups ?? 0"
        label="\u0413\u0440\u0443\u043F\u043F \u0432\u0441\u0435\u0433\u043E"
        [sparkData]="groupsSpark()"
        [loading]="loading()"
      />
    </a>
    <a routerLink="/admin/groups" [queryParams]="{ status: 'active' }" class="dashboard__stat-link" aria-label="\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u043C \u0433\u0440\u0443\u043F\u043F\u0430\u043C">
      <app-stat-card
        accent="info"
        icon="ph-duotone ph-buildings"
        [value]="stats()?.activeGroups ?? 0"
        label="\u0410\u043A\u0442\u0438\u0432\u043D\u044B\u0445 \u0433\u0440\u0443\u043F\u043F"
        suffix="{{ activeGroupsPct() ? activeGroupsPct() + '%' : '' }}"
        [sparkData]="activeGroupsSpark()"
        [loading]="loading()"
      />
    </a>
  </div>

  <!-- Attendance trend chart (\xA75.3 draw-in) -->
  <section class="chart-card data-card-in" aria-labelledby="dashboard-chart-title">
    <header class="chart-card__head">
      <div>
        <p class="chart-card__eyebrow">\u041F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C \xB7 7 \u0434\u043D\u0435\u0439</p>
        <h3 class="chart-card__title" id="dashboard-chart-title">\u0414\u0438\u043D\u0430\u043C\u0438\u043A\u0430 \u0437\u0430 \u043D\u0435\u0434\u0435\u043B\u044E</h3>
      </div>
      <span class="chart-card__badge" aria-hidden="true">
        <i class="ph ph-trend-up"></i> live
      </span>
    </header>
    <div class="chart-card__canvas">
      <canvas
        baseChart
        role="img"
        aria-label="\u0413\u0440\u0430\u0444\u0438\u043A \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438 \u0437\u0430 \u043F\u043E\u0441\u043B\u0435\u0434\u043D\u0438\u0435 7 \u0434\u043D\u0435\u0439"
        [data]="chartData()"
        [options]="chartOptions"
        [type]="'line'"
      ></canvas>
    </div>
  </section>

  @if (error()) {
    <div class="dashboard__error" role="alert">
      <i class="ph ph-warning-circle" aria-hidden="true"></i>
      {{ error() }}
    </div>
  }

  <!-- Quick actions -->
  <div class="dashboard__section">
    <div class="section-head">
      <h3 class="section-head__title">\u0411\u044B\u0441\u0442\u0440\u044B\u0435 \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044F</h3>
      <p class="section-head__sub">\u0427\u0430\u0441\u0442\u043E \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u043C\u044B\u0435 \u0440\u0430\u0437\u0434\u0435\u043B\u044B \u043F\u0430\u043D\u0435\u043B\u0438</p>
    </div>

    <div class="dashboard__actions">
      <a routerLink="/admin/users" [queryParams]="{ action: 'create', role: 'TEACHER' }" class="action-card" data-accent="secondary">
        <div class="action-card__icon" aria-hidden="true">
          <i class="ph-duotone ph-chalkboard-teacher"></i>
        </div>
        <div class="action-card__body">
          <h4 class="action-card__title">\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044F</h4>
          <p class="action-card__text">\u041D\u043E\u0432\u0430\u044F \u0443\u0447\u0451\u0442\u043D\u0430\u044F \u0437\u0430\u043F\u0438\u0441\u044C \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044F</p>
        </div>
        <i class="ph ph-arrow-right action-card__arrow" aria-hidden="true"></i>
      </a>

      <a routerLink="/admin/users" [queryParams]="{ action: 'create', role: 'STUDENT' }" class="action-card" data-accent="primary">
        <div class="action-card__icon" aria-hidden="true">
          <i class="ph-duotone ph-student"></i>
        </div>
        <div class="action-card__body">
          <h4 class="action-card__title">\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430</h4>
          <p class="action-card__text">\u041D\u043E\u0432\u0430\u044F \u0443\u0447\u0451\u0442\u043D\u0430\u044F \u0437\u0430\u043F\u0438\u0441\u044C \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430 \u0441 \u043F\u0440\u0438\u0432\u044F\u0437\u043A\u043E\u0439 \u043A \u0433\u0440\u0443\u043F\u043F\u0435</p>
        </div>
        <i class="ph ph-arrow-right action-card__arrow" aria-hidden="true"></i>
      </a>

      <a routerLink="/admin/groups" [queryParams]="{ action: 'create' }" class="action-card" data-accent="warning">
        <div class="action-card__icon" aria-hidden="true">
          <i class="ph-duotone ph-users-three"></i>
        </div>
        <div class="action-card__body">
          <h4 class="action-card__title">\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443</h4>
          <p class="action-card__text">\u041D\u043E\u0432\u0430\u044F \u0443\u0447\u0435\u0431\u043D\u0430\u044F \u0433\u0440\u0443\u043F\u043F\u0430</p>
        </div>
        <i class="ph ph-arrow-right action-card__arrow" aria-hidden="true"></i>
      </a>
    </div>
  </div>

  <!-- Recent groups table (\xA74.4) -->
  <div class="dashboard__section">
    <div class="section-head section-head--row">
      <div>
        <h3 class="section-head__title">\u041D\u0435\u0434\u0430\u0432\u043D\u0438\u0435 \u0433\u0440\u0443\u043F\u043F\u044B</h3>
        <p class="section-head__sub">\u041F\u043E\u0441\u043B\u0435\u0434\u043D\u0438\u0435 \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F \u0432 \u0443\u0447\u0435\u0431\u043D\u044B\u0445 \u0433\u0440\u0443\u043F\u043F\u0430\u0445</p>
      </div>
      <a routerLink="/admin/groups" class="section-link">
        \u0412\u0441\u0435 \u0433\u0440\u0443\u043F\u043F\u044B <i class="ph ph-arrow-right" aria-hidden="true"></i>
      </a>
    </div>

    <div class="table-card data-card-in" role="region" aria-label="\u041D\u0435\u0434\u0430\u0432\u043D\u0438\u0435 \u0433\u0440\u0443\u043F\u043F\u044B">
      <div class="table-card__scroll">
        <table class="table-card__table">
          <thead>
            <tr>
              <th scope="col">\u0413\u0440\u0443\u043F\u043F\u0430</th>
              <th scope="col">\u0421\u0442\u0430\u0442\u0443\u0441</th>
              <th scope="col" class="table-card__col-date">\u0421\u043E\u0437\u0434\u0430\u043D\u0430</th>
              <th scope="col" class="table-card__col-date">\u0410\u0440\u0445\u0438\u0432</th>
            </tr>
          </thead>
          <tbody>
            @if (groupsLoading()) {
              @for (i of [1,2,3,4]; track i) {
                <tr class="table-card__row">
                  <td><span class="table-card__skeleton table-card__skeleton--wide"></span></td>
                  <td><span class="table-card__skeleton"></span></td>
                  <td><span class="table-card__skeleton"></span></td>
                  <td><span class="table-card__skeleton"></span></td>
                </tr>
              }
            } @else if (recentGroups().length === 0) {
              <tr>
                <td colspan="4" class="table-card__empty">\u041D\u0435\u0442 \u0434\u0430\u043D\u043D\u044B\u0445 \u043E \u0433\u0440\u0443\u043F\u043F\u0430\u0445.</td>
              </tr>
            } @else {
              @for (g of recentGroups(); track g.id) {
                <tr class="table-card__row">
                  <td>
                    <a [routerLink]="['/admin/groups']" [queryParams]="{ id: g.id }" class="table-card__name">
                      <span class="table-card__dot" aria-hidden="true"></span>
                      {{ g.name }}
                    </a>
                  </td>
                  <td>
                    @if (g.active) {
                      <span class="pill pill--success">
                        <span class="pill__dot" aria-hidden="true"></span> \u0410\u043A\u0442\u0438\u0432\u043D\u0430
                      </span>
                    } @else {
                      <span class="pill pill--muted">
                        <i class="ph ph-archive" aria-hidden="true"></i> \u0410\u0440\u0445\u0438\u0432
                      </span>
                    }
                  </td>
                  <td class="table-card__mono">{{ formatGroupDate(g.createdAt) }}</td>
                  <td class="table-card__mono">{{ formatGroupDate(g.archivedAt) }}</td>
                </tr>
              }
            }
          </tbody>
        </table>
      </div>
    </div>
  </div>
</section>
`, styles: ["/* src/app/features/admin/dashboard/admin-dashboard.component.css */\n:host {\n  display: block;\n}\n.dashboard {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-7);\n  animation: dashboard-in 0.6s var(--ease-out) both;\n}\n@keyframes dashboard-in {\n  from {\n    opacity: 0;\n    transform: translateY(12px);\n  }\n  to {\n    opacity: 1;\n    transform: translateY(0);\n  }\n}\n.dashboard__grid {\n  display: grid;\n  gap: var(--space-4);\n  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));\n}\n.dashboard__stat-link {\n  display: block;\n  text-decoration: none;\n  color: inherit;\n  border-radius: var(--radius-lg);\n  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);\n  outline: none;\n}\n.dashboard__stat-link:hover {\n  transform: translateY(-2px);\n}\n.dashboard__stat-link:focus-visible {\n  box-shadow: 0 0 0 2px color-mix(in oklab, var(--accent-primary) 55%, transparent);\n}\n.dashboard__grid > app-stat-card,\n.dashboard__grid > .dashboard__stat-link {\n  animation: data-card-in 0.5s var(--ease-out) both;\n}\n.dashboard__grid > :nth-child(1) {\n  animation-delay: 0.05s;\n}\n.dashboard__grid > :nth-child(2) {\n  animation-delay: 0.12s;\n}\n.dashboard__grid > :nth-child(3) {\n  animation-delay: 0.19s;\n}\n.dashboard__grid > :nth-child(4) {\n  animation-delay: 0.26s;\n}\n.dashboard__grid > :nth-child(5) {\n  animation-delay: 0.33s;\n}\n.dashboard__error {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: var(--space-4) var(--space-5);\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 30%, transparent);\n  color: var(--accent-danger);\n  font-size: 0.875rem;\n}\n.dashboard__error i {\n  font-size: 20px;\n}\n.dashboard__section {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-5);\n}\n.dashboard__actions {\n  display: grid;\n  gap: var(--space-4);\n  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));\n}\n.action-card {\n  --stat-color: var(--accent-primary);\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n  padding: var(--space-5);\n  border-radius: var(--radius-lg);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  text-decoration: none;\n  color: inherit;\n  position: relative;\n  overflow: hidden;\n  transition:\n    transform var(--duration-slow) var(--ease-out),\n    border-color var(--duration-slow) var(--ease-out),\n    box-shadow var(--duration-slow) var(--ease-out);\n}\n.action-card[data-accent=warning] {\n  --stat-color: var(--accent-warning);\n}\n.action-card[data-accent=info] {\n  --stat-color: var(--accent-info);\n}\n.action-card:hover {\n  transform: translateY(-2px);\n  border-color: color-mix(in oklab, var(--stat-color) 40%, transparent);\n  box-shadow: 0 0 24px color-mix(in oklab, var(--stat-color) 14%, transparent), var(--shadow-md);\n}\n.action-card:hover .action-card__arrow {\n  transform: translateX(4px);\n  color: var(--stat-color);\n}\n.action-card__icon {\n  width: 52px;\n  height: 52px;\n  border-radius: var(--radius-md);\n  display: grid;\n  place-items: center;\n  background: color-mix(in oklab, var(--stat-color) 14%, transparent);\n  border: 1px solid color-mix(in oklab, var(--stat-color) 28%, transparent);\n  color: var(--stat-color);\n  flex-shrink: 0;\n}\n.action-card__icon i {\n  font-size: 24px;\n}\n.action-card__body {\n  flex: 1;\n  min-width: 0;\n}\n.action-card__title {\n  margin: 0 0 4px;\n  font: 600 1rem/1.2 var(--font-heading);\n  color: var(--text-primary);\n}\n.action-card__text {\n  margin: 0;\n  font-size: 0.8125rem;\n  line-height: 1.4;\n  color: var(--text-secondary);\n}\n.action-card__arrow {\n  color: var(--text-muted);\n  font-size: 20px;\n  transition: transform var(--duration-base) var(--ease-out), color var(--duration-base) var(--ease-out);\n}\n.dashboard > .chart-card {\n  animation-delay: 0.38s;\n}\n.dashboard > .dashboard__section .table-card {\n  animation-delay: 0.46s;\n}\n@media (prefers-reduced-motion: reduce) {\n  .dashboard,\n  .dashboard__grid > app-stat-card {\n    animation: none;\n  }\n}\n/*# sourceMappingURL=admin-dashboard.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(AdminDashboardComponent, { className: "AdminDashboardComponent", filePath: "src/app/features/admin/dashboard/admin-dashboard.component.ts", lineNumber: 33 });
})();
function buildSpark(target, points, floorPct) {
  if (!target || target < 0)
    return Array.from({ length: points }, () => 0);
  const floor = Math.max(0, Math.floor(target * floorPct));
  const range = Math.max(1, target - floor);
  const result = [];
  for (let i = 0; i < points - 1; i++) {
    const t = i / (points - 1);
    const wobble = Math.sin((target + i) * 1.7) * 0.12 + 0.5;
    result.push(Math.round(floor + range * (t * 0.82 + wobble * 0.18)));
  }
  result.push(target);
  return result;
}
export {
  AdminDashboardComponent
};
//# sourceMappingURL=chunk-2FNPFSEC.js.map
