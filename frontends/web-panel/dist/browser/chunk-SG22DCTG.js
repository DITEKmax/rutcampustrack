import {
  StatCardComponent
} from "./chunk-YLIW7ZOJ.js";
import {
  AdminApiService
} from "./chunk-4Y37KGBC.js";
import {
  RouterLink
} from "./chunk-BRINUWSL.js";
import "./chunk-RWV7HGF7.js";
import "./chunk-FLE4DLW4.js";
import "./chunk-CLRYRPPS.js";
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
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-4M4FDLBS.js";

// src/app/features/admin/dashboard/admin-dashboard.component.ts
var _c0 = () => ({ role: "STUDENT" });
var _c1 = () => ({ role: "TEACHER" });
var _c2 = () => ({ status: "active" });
var _c3 = () => ({ action: "create", role: "TEACHER" });
var _c4 = () => ({ action: "create", role: "STUDENT" });
var _c5 = () => ({ action: "create" });
function AdminDashboardComponent_Conditional_26_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 19);
    \u0275\u0275element(1, "i", 36);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.error(), " ");
  }
}
var AdminDashboardComponent = class _AdminDashboardComponent {
  adminApi = inject(AdminApiService);
  destroyRef = inject(DestroyRef);
  stats = signal(null);
  loading = signal(false);
  error = signal(null);
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
    const tick = setInterval(() => this._now.set(/* @__PURE__ */ new Date()), 6e4);
    this.destroyRef.onDestroy(() => clearInterval(tick));
  }
  static \u0275fac = function AdminDashboardComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _AdminDashboardComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _AdminDashboardComponent, selectors: [["app-admin-dashboard"]], decls: 61, vars: 26, consts: [[1, "dashboard"], [1, "dashboard__hero"], [1, "dashboard__greeting"], [1, "dashboard__eyebrow"], ["aria-hidden", "true", 1, "dashboard__pulse"], [1, "dashboard__title"], [1, "dashboard__subtitle"], ["aria-hidden", "true", 1, "dashboard__clock"], [1, "dashboard__clock-value"], [1, "dashboard__clock-label"], [1, "dashboard__grid"], ["routerLink", "/admin/users", "aria-label", "\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u044E \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430\u043C\u0438", 1, "dashboard__stat-link", 3, "queryParams"], ["accent", "primary", "icon", "ph-duotone ph-students", "label", "\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432", 3, "value", "loading"], ["routerLink", "/admin/users", "aria-label", "\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u044E \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044F\u043C\u0438", 1, "dashboard__stat-link", 3, "queryParams"], ["accent", "secondary", "icon", "ph-duotone ph-chalkboard-teacher", "label", "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0435\u0439", 3, "value", "loading"], ["routerLink", "/admin/groups", "aria-label", "\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0433\u0440\u0443\u043F\u043F\u0430\u043C", 1, "dashboard__stat-link"], ["accent", "warning", "icon", "ph-duotone ph-users-three", "label", "\u0413\u0440\u0443\u043F\u043F \u0432\u0441\u0435\u0433\u043E", 3, "value", "loading"], ["routerLink", "/admin/groups", "aria-label", "\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u043C \u0433\u0440\u0443\u043F\u043F\u0430\u043C", 1, "dashboard__stat-link", 3, "queryParams"], ["accent", "info", "icon", "ph-duotone ph-buildings", "label", "\u0410\u043A\u0442\u0438\u0432\u043D\u044B\u0445 \u0433\u0440\u0443\u043F\u043F", 3, "value", "suffix", "loading"], ["role", "alert", 1, "dashboard__error"], [1, "dashboard__section"], [1, "dashboard__section-head"], [1, "dashboard__section-title"], [1, "dashboard__section-sub"], [1, "dashboard__actions"], ["routerLink", "/admin/users", "data-accent", "secondary", 1, "action-card", 3, "queryParams"], ["aria-hidden", "true", 1, "action-card__icon"], [1, "ph-duotone", "ph-chalkboard-teacher"], [1, "action-card__body"], [1, "action-card__title"], [1, "action-card__text"], ["aria-hidden", "true", 1, "ph", "ph-arrow-right", "action-card__arrow"], ["routerLink", "/admin/users", "data-accent", "primary", 1, "action-card", 3, "queryParams"], [1, "ph-duotone", "ph-student"], ["routerLink", "/admin/groups", "data-accent", "warning", 1, "action-card", 3, "queryParams"], [1, "ph-duotone", "ph-users-three"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"]], template: function AdminDashboardComponent_Template(rf, ctx) {
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
      \u0275\u0275template(26, AdminDashboardComponent_Conditional_26_Template, 3, 1, "div", 19);
      \u0275\u0275elementStart(27, "div", 20)(28, "div", 21)(29, "h3", 22);
      \u0275\u0275text(30, "\u0411\u044B\u0441\u0442\u0440\u044B\u0435 \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044F");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(31, "p", 23);
      \u0275\u0275text(32, "\u0427\u0430\u0441\u0442\u043E \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u043C\u044B\u0435 \u0440\u0430\u0437\u0434\u0435\u043B\u044B \u043F\u0430\u043D\u0435\u043B\u0438");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(33, "div", 24)(34, "a", 25)(35, "div", 26);
      \u0275\u0275element(36, "i", 27);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(37, "div", 28)(38, "h4", 29);
      \u0275\u0275text(39, "\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044F");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(40, "p", 30);
      \u0275\u0275text(41, "\u041D\u043E\u0432\u0430\u044F \u0443\u0447\u0451\u0442\u043D\u0430\u044F \u0437\u0430\u043F\u0438\u0441\u044C \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044F");
      \u0275\u0275elementEnd()();
      \u0275\u0275element(42, "i", 31);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(43, "a", 32)(44, "div", 26);
      \u0275\u0275element(45, "i", 33);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(46, "div", 28)(47, "h4", 29);
      \u0275\u0275text(48, "\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(49, "p", 30);
      \u0275\u0275text(50, "\u041D\u043E\u0432\u0430\u044F \u0443\u0447\u0451\u0442\u043D\u0430\u044F \u0437\u0430\u043F\u0438\u0441\u044C \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430 \u0441 \u043F\u0440\u0438\u0432\u044F\u0437\u043A\u043E\u0439 \u043A \u0433\u0440\u0443\u043F\u043F\u0435");
      \u0275\u0275elementEnd()();
      \u0275\u0275element(51, "i", 31);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(52, "a", 34)(53, "div", 26);
      \u0275\u0275element(54, "i", 35);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(55, "div", 28)(56, "h4", 29);
      \u0275\u0275text(57, "\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(58, "p", 30);
      \u0275\u0275text(59, "\u041D\u043E\u0432\u0430\u044F \u0443\u0447\u0435\u0431\u043D\u0430\u044F \u0433\u0440\u0443\u043F\u043F\u0430");
      \u0275\u0275elementEnd()();
      \u0275\u0275element(60, "i", 31);
      \u0275\u0275elementEnd()()()();
    }
    if (rf & 2) {
      let tmp_2_0;
      let tmp_5_0;
      let tmp_8_0;
      let tmp_10_0;
      let tmp_14_0;
      \u0275\u0275advance(7);
      \u0275\u0275textInterpolate1("", ctx.greeting(), ", \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440");
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate1(" ", ctx.dateLabel(), " \xB7 \u0442\u0435\u043A\u0443\u0449\u0438\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440 ");
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate((tmp_2_0 = (tmp_2_0 = ctx.stats()) == null ? null : tmp_2_0.activeSemesterName) !== null && tmp_2_0 !== void 0 ? tmp_2_0 : "\u2014");
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.timeLabel());
      \u0275\u0275advance(4);
      \u0275\u0275property("queryParams", \u0275\u0275pureFunction0(20, _c0));
      \u0275\u0275advance();
      \u0275\u0275property("value", (tmp_5_0 = (tmp_5_0 = ctx.stats()) == null ? null : tmp_5_0.totalStudents) !== null && tmp_5_0 !== void 0 ? tmp_5_0 : 0)("loading", ctx.loading());
      \u0275\u0275advance();
      \u0275\u0275property("queryParams", \u0275\u0275pureFunction0(21, _c1));
      \u0275\u0275advance();
      \u0275\u0275property("value", (tmp_8_0 = (tmp_8_0 = ctx.stats()) == null ? null : tmp_8_0.totalTeachers) !== null && tmp_8_0 !== void 0 ? tmp_8_0 : 0)("loading", ctx.loading());
      \u0275\u0275advance(2);
      \u0275\u0275property("value", (tmp_10_0 = (tmp_10_0 = ctx.stats()) == null ? null : tmp_10_0.totalGroups) !== null && tmp_10_0 !== void 0 ? tmp_10_0 : 0)("loading", ctx.loading());
      \u0275\u0275advance();
      \u0275\u0275property("queryParams", \u0275\u0275pureFunction0(22, _c2));
      \u0275\u0275advance();
      \u0275\u0275propertyInterpolate("suffix", ctx.activeGroupsPct() ? ctx.activeGroupsPct() + "%" : "");
      \u0275\u0275property("value", (tmp_14_0 = (tmp_14_0 = ctx.stats()) == null ? null : tmp_14_0.activeGroups) !== null && tmp_14_0 !== void 0 ? tmp_14_0 : 0)("loading", ctx.loading());
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.error() ? 26 : -1);
      \u0275\u0275advance(8);
      \u0275\u0275property("queryParams", \u0275\u0275pureFunction0(23, _c3));
      \u0275\u0275advance(9);
      \u0275\u0275property("queryParams", \u0275\u0275pureFunction0(24, _c4));
      \u0275\u0275advance(9);
      \u0275\u0275property("queryParams", \u0275\u0275pureFunction0(25, _c5));
    }
  }, dependencies: [StatCardComponent, RouterLink], styles: ['\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.dashboard[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-7);\n  animation: _ngcontent-%COMP%_dashboard-in 0.6s var(--ease-out) both;\n}\n@keyframes _ngcontent-%COMP%_dashboard-in {\n  from {\n    opacity: 0;\n    transform: translateY(12px);\n  }\n  to {\n    opacity: 1;\n    transform: translateY(0);\n  }\n}\n.dashboard__hero[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: flex-start;\n  justify-content: space-between;\n  gap: var(--space-5);\n  padding: var(--space-6);\n  border-radius: var(--radius-xl);\n  background:\n    radial-gradient(\n      ellipse at 15% 20%,\n      color-mix(in oklab, var(--accent-primary) 16%, transparent),\n      transparent 55%),\n    radial-gradient(\n      ellipse at 90% 80%,\n      color-mix(in oklab, var(--accent-secondary) 18%, transparent),\n      transparent 55%),\n    var(--bg-secondary);\n  border: 1px solid var(--border-default);\n  overflow: hidden;\n  position: relative;\n  isolation: isolate;\n}\n.dashboard__hero[_ngcontent-%COMP%]::before {\n  content: "";\n  position: absolute;\n  inset: 0;\n  background-image:\n    linear-gradient(var(--border-subtle) 1px, transparent 1px),\n    linear-gradient(\n      90deg,\n      var(--border-subtle) 1px,\n      transparent 1px);\n  background-size: 48px 48px;\n  -webkit-mask-image:\n    radial-gradient(\n      ellipse 70% 80% at 50% 50%,\n      #000 25%,\n      transparent 75%);\n  mask-image:\n    radial-gradient(\n      ellipse 70% 80% at 50% 50%,\n      #000 25%,\n      transparent 75%);\n  z-index: -1;\n  opacity: 0.6;\n}\n.dashboard__greeting[_ngcontent-%COMP%] {\n  min-width: 0;\n  flex: 1;\n}\n.dashboard__eyebrow[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: 4px 12px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-primary) 30%, transparent);\n  color: var(--accent-primary);\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  font-weight: 500;\n  letter-spacing: 0.06em;\n  text-transform: uppercase;\n  margin: 0 0 var(--space-4);\n}\n.dashboard__pulse[_ngcontent-%COMP%] {\n  width: 6px;\n  height: 6px;\n  border-radius: 50%;\n  background: var(--accent-primary);\n  box-shadow: 0 0 8px var(--accent-primary);\n  animation: _ngcontent-%COMP%_dashboard-pulse 2.2s ease-in-out infinite;\n}\n@keyframes _ngcontent-%COMP%_dashboard-pulse {\n  0%, 100% {\n    opacity: 1;\n    transform: scale(1);\n  }\n  50% {\n    opacity: 0.5;\n    transform: scale(1.4);\n  }\n}\n.dashboard__title[_ngcontent-%COMP%] {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: clamp(1.75rem, 2.4vw + 0.5rem, 2.5rem);\n  line-height: 1.1;\n  letter-spacing: -0.02em;\n  color: var(--text-primary);\n  margin: 0 0 var(--space-3);\n}\n.dashboard__subtitle[_ngcontent-%COMP%] {\n  font-size: 0.9375rem;\n  color: var(--text-secondary);\n  margin: 0;\n  line-height: 1.5;\n}\n.dashboard__subtitle[_ngcontent-%COMP%]   strong[_ngcontent-%COMP%] {\n  color: var(--text-primary);\n  font-weight: 600;\n}\n.dashboard__clock[_ngcontent-%COMP%] {\n  display: none;\n  flex-direction: column;\n  align-items: flex-end;\n  padding: var(--space-4) var(--space-5);\n  border-radius: var(--radius-lg);\n  background: color-mix(in oklab, var(--bg-primary) 60%, transparent);\n  border: 1px solid var(--border-subtle);\n  flex-shrink: 0;\n}\n@media (min-width: 640px) {\n  .dashboard__clock[_ngcontent-%COMP%] {\n    display: flex;\n  }\n}\n.dashboard__clock-value[_ngcontent-%COMP%] {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 2rem;\n  line-height: 1;\n  color: var(--text-primary);\n  letter-spacing: -0.02em;\n  font-variant-numeric: tabular-nums;\n}\n.dashboard__clock-label[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  color: var(--text-muted);\n  letter-spacing: 0.06em;\n  text-transform: uppercase;\n  margin-top: 4px;\n}\n.dashboard__grid[_ngcontent-%COMP%] {\n  display: grid;\n  gap: var(--space-4);\n  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));\n}\n.dashboard__stat-link[_ngcontent-%COMP%] {\n  display: block;\n  text-decoration: none;\n  color: inherit;\n  border-radius: var(--radius-lg, 14px);\n  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);\n  outline: none;\n}\n.dashboard__stat-link[_ngcontent-%COMP%]:hover {\n  transform: translateY(-2px);\n}\n.dashboard__stat-link[_ngcontent-%COMP%]:focus-visible {\n  box-shadow: 0 0 0 2px color-mix(in oklab, var(--accent-primary) 55%, transparent);\n}\n.dashboard__grid[_ngcontent-%COMP%]    > app-stat-card[_ngcontent-%COMP%], \n.dashboard__grid[_ngcontent-%COMP%]    > .dashboard__stat-link[_ngcontent-%COMP%] {\n  animation: _ngcontent-%COMP%_card-in 0.5s var(--ease-out) both;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > [_ngcontent-%COMP%]:nth-child(1) {\n  animation-delay: 0.05s;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > [_ngcontent-%COMP%]:nth-child(2) {\n  animation-delay: 0.12s;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > [_ngcontent-%COMP%]:nth-child(3) {\n  animation-delay: 0.19s;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > [_ngcontent-%COMP%]:nth-child(4) {\n  animation-delay: 0.26s;\n}\n.dashboard__grid[_ngcontent-%COMP%]    > [_ngcontent-%COMP%]:nth-child(5) {\n  animation-delay: 0.33s;\n}\n@keyframes _ngcontent-%COMP%_card-in {\n  from {\n    opacity: 0;\n    transform: translateY(14px);\n  }\n  to {\n    opacity: 1;\n    transform: translateY(0);\n  }\n}\n.dashboard__error[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: var(--space-4) var(--space-5);\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 30%, transparent);\n  color: var(--accent-danger);\n  font-size: 0.875rem;\n}\n.dashboard__error[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 20px;\n}\n.dashboard__section[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-5);\n}\n.dashboard__section-head[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 4px;\n}\n.dashboard__section-title[_ngcontent-%COMP%] {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 1.25rem;\n  color: var(--text-primary);\n  margin: 0;\n  letter-spacing: -0.01em;\n}\n.dashboard__section-sub[_ngcontent-%COMP%] {\n  font-size: 0.875rem;\n  color: var(--text-muted);\n  margin: 0;\n}\n.dashboard__actions[_ngcontent-%COMP%] {\n  display: grid;\n  gap: var(--space-4);\n  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));\n}\n.action-card[_ngcontent-%COMP%] {\n  --stat-color: var(--accent-primary);\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n  padding: var(--space-5);\n  border-radius: var(--radius-lg);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  text-decoration: none;\n  color: inherit;\n  position: relative;\n  overflow: hidden;\n  transition:\n    transform var(--duration-slow) var(--ease-out),\n    border-color var(--duration-slow) var(--ease-out),\n    box-shadow var(--duration-slow) var(--ease-out);\n}\n.action-card[data-accent=warning][_ngcontent-%COMP%] {\n  --stat-color: var(--accent-warning);\n}\n.action-card[data-accent=info][_ngcontent-%COMP%] {\n  --stat-color: var(--accent-info);\n}\n.action-card[_ngcontent-%COMP%]:hover {\n  transform: translateY(-2px);\n  border-color: color-mix(in oklab, var(--stat-color) 40%, transparent);\n  box-shadow: 0 0 24px color-mix(in oklab, var(--stat-color) 14%, transparent), var(--shadow-md);\n}\n.action-card[_ngcontent-%COMP%]:hover   .action-card__arrow[_ngcontent-%COMP%] {\n  transform: translateX(4px);\n  color: var(--stat-color);\n}\n.action-card__icon[_ngcontent-%COMP%] {\n  width: 52px;\n  height: 52px;\n  border-radius: var(--radius-md);\n  display: grid;\n  place-items: center;\n  background: color-mix(in oklab, var(--stat-color) 14%, transparent);\n  border: 1px solid color-mix(in oklab, var(--stat-color) 28%, transparent);\n  color: var(--stat-color);\n  flex-shrink: 0;\n}\n.action-card__icon[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 24px;\n}\n.action-card__body[_ngcontent-%COMP%] {\n  flex: 1;\n  min-width: 0;\n}\n.action-card__title[_ngcontent-%COMP%] {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 1rem;\n  margin: 0 0 4px;\n  color: var(--text-primary);\n}\n.action-card__text[_ngcontent-%COMP%] {\n  font-size: 0.8125rem;\n  color: var(--text-secondary);\n  margin: 0;\n  line-height: 1.4;\n}\n.action-card__arrow[_ngcontent-%COMP%] {\n  color: var(--text-muted);\n  font-size: 20px;\n  transition: transform var(--duration-base) var(--ease-out), color var(--duration-base) var(--ease-out);\n}\n@media (prefers-reduced-motion: reduce) {\n  .dashboard[_ngcontent-%COMP%], \n   .dashboard__grid[_ngcontent-%COMP%]    > app-stat-card[_ngcontent-%COMP%] {\n    animation: none;\n  }\n  .dashboard__pulse[_ngcontent-%COMP%] {\n    animation: none;\n  }\n}\n/*# sourceMappingURL=admin-dashboard.component.css.map */'], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(AdminDashboardComponent, [{
    type: Component,
    args: [{ selector: "app-admin-dashboard", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [StatCardComponent, RouterLink], template: `<section class="dashboard">\r
  <!-- Greeting + live clock -->\r
  <header class="dashboard__hero">\r
    <div class="dashboard__greeting">\r
      <p class="dashboard__eyebrow">\r
        <span class="dashboard__pulse" aria-hidden="true"></span>\r
        \u0421\u0438\u0441\u0442\u0435\u043C\u0430 \u0430\u043A\u0442\u0438\u0432\u043D\u0430\r
      </p>\r
      <h2 class="dashboard__title">{{ greeting() }}, \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440</h2>\r
      <p class="dashboard__subtitle">\r
        {{ dateLabel() }} \xB7 \u0442\u0435\u043A\u0443\u0449\u0438\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440\r
        <strong>{{ stats()?.activeSemesterName ?? '\u2014' }}</strong>\r
      </p>\r
    </div>\r
\r
    <div class="dashboard__clock" aria-hidden="true">\r
      <span class="dashboard__clock-value">{{ timeLabel() }}</span>\r
      <span class="dashboard__clock-label">\u043F\u043E \u041C\u043E\u0441\u043A\u0432\u0435</span>\r
    </div>\r
  </header>\r
\r
  <!-- Stat grid (clickable \u2014 BUG-005) -->\r
  <div class="dashboard__grid">\r
    <a routerLink="/admin/users" [queryParams]="{ role: 'STUDENT' }" class="dashboard__stat-link" aria-label="\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u044E \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430\u043C\u0438">\r
      <app-stat-card\r
        accent="primary"\r
        icon="ph-duotone ph-students"\r
        [value]="stats()?.totalStudents ?? 0"\r
        label="\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432"\r
        [loading]="loading()"\r
      />\r
    </a>\r
    <a routerLink="/admin/users" [queryParams]="{ role: 'TEACHER' }" class="dashboard__stat-link" aria-label="\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u044E \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044F\u043C\u0438">\r
      <app-stat-card\r
        accent="secondary"\r
        icon="ph-duotone ph-chalkboard-teacher"\r
        [value]="stats()?.totalTeachers ?? 0"\r
        label="\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0435\u0439"\r
        [loading]="loading()"\r
      />\r
    </a>\r
    <a routerLink="/admin/groups" class="dashboard__stat-link" aria-label="\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0433\u0440\u0443\u043F\u043F\u0430\u043C">\r
      <app-stat-card\r
        accent="warning"\r
        icon="ph-duotone ph-users-three"\r
        [value]="stats()?.totalGroups ?? 0"\r
        label="\u0413\u0440\u0443\u043F\u043F \u0432\u0441\u0435\u0433\u043E"\r
        [loading]="loading()"\r
      />\r
    </a>\r
    <a routerLink="/admin/groups" [queryParams]="{ status: 'active' }" class="dashboard__stat-link" aria-label="\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043A \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u043C \u0433\u0440\u0443\u043F\u043F\u0430\u043C">\r
      <app-stat-card\r
        accent="info"\r
        icon="ph-duotone ph-buildings"\r
        [value]="stats()?.activeGroups ?? 0"\r
        label="\u0410\u043A\u0442\u0438\u0432\u043D\u044B\u0445 \u0433\u0440\u0443\u043F\u043F"\r
        suffix="{{ activeGroupsPct() ? activeGroupsPct() + '%' : '' }}"\r
        [loading]="loading()"\r
      />\r
    </a>\r
  </div>\r
\r
  @if (error()) {\r
    <div class="dashboard__error" role="alert">\r
      <i class="ph ph-warning-circle" aria-hidden="true"></i>\r
      {{ error() }}\r
    </div>\r
  }\r
\r
  <!-- Quick actions -->\r
  <div class="dashboard__section">\r
    <div class="dashboard__section-head">\r
      <h3 class="dashboard__section-title">\u0411\u044B\u0441\u0442\u0440\u044B\u0435 \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044F</h3>\r
      <p class="dashboard__section-sub">\u0427\u0430\u0441\u0442\u043E \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u043C\u044B\u0435 \u0440\u0430\u0437\u0434\u0435\u043B\u044B \u043F\u0430\u043D\u0435\u043B\u0438</p>\r
    </div>\r
\r
    <div class="dashboard__actions">\r
      <a routerLink="/admin/users" [queryParams]="{ action: 'create', role: 'TEACHER' }" class="action-card" data-accent="secondary">\r
        <div class="action-card__icon" aria-hidden="true">\r
          <i class="ph-duotone ph-chalkboard-teacher"></i>\r
        </div>\r
        <div class="action-card__body">\r
          <h4 class="action-card__title">\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044F</h4>\r
          <p class="action-card__text">\u041D\u043E\u0432\u0430\u044F \u0443\u0447\u0451\u0442\u043D\u0430\u044F \u0437\u0430\u043F\u0438\u0441\u044C \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044F</p>\r
        </div>\r
        <i class="ph ph-arrow-right action-card__arrow" aria-hidden="true"></i>\r
      </a>\r
\r
      <a routerLink="/admin/users" [queryParams]="{ action: 'create', role: 'STUDENT' }" class="action-card" data-accent="primary">\r
        <div class="action-card__icon" aria-hidden="true">\r
          <i class="ph-duotone ph-student"></i>\r
        </div>\r
        <div class="action-card__body">\r
          <h4 class="action-card__title">\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430</h4>\r
          <p class="action-card__text">\u041D\u043E\u0432\u0430\u044F \u0443\u0447\u0451\u0442\u043D\u0430\u044F \u0437\u0430\u043F\u0438\u0441\u044C \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430 \u0441 \u043F\u0440\u0438\u0432\u044F\u0437\u043A\u043E\u0439 \u043A \u0433\u0440\u0443\u043F\u043F\u0435</p>\r
        </div>\r
        <i class="ph ph-arrow-right action-card__arrow" aria-hidden="true"></i>\r
      </a>\r
\r
      <a routerLink="/admin/groups" [queryParams]="{ action: 'create' }" class="action-card" data-accent="warning">\r
        <div class="action-card__icon" aria-hidden="true">\r
          <i class="ph-duotone ph-users-three"></i>\r
        </div>\r
        <div class="action-card__body">\r
          <h4 class="action-card__title">\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443</h4>\r
          <p class="action-card__text">\u041D\u043E\u0432\u0430\u044F \u0443\u0447\u0435\u0431\u043D\u0430\u044F \u0433\u0440\u0443\u043F\u043F\u0430</p>\r
        </div>\r
        <i class="ph ph-arrow-right action-card__arrow" aria-hidden="true"></i>\r
      </a>\r
    </div>\r
  </div>\r
</section>\r
`, styles: ['/* src/app/features/admin/dashboard/admin-dashboard.component.css */\n:host {\n  display: block;\n}\n.dashboard {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-7);\n  animation: dashboard-in 0.6s var(--ease-out) both;\n}\n@keyframes dashboard-in {\n  from {\n    opacity: 0;\n    transform: translateY(12px);\n  }\n  to {\n    opacity: 1;\n    transform: translateY(0);\n  }\n}\n.dashboard__hero {\n  display: flex;\n  align-items: flex-start;\n  justify-content: space-between;\n  gap: var(--space-5);\n  padding: var(--space-6);\n  border-radius: var(--radius-xl);\n  background:\n    radial-gradient(\n      ellipse at 15% 20%,\n      color-mix(in oklab, var(--accent-primary) 16%, transparent),\n      transparent 55%),\n    radial-gradient(\n      ellipse at 90% 80%,\n      color-mix(in oklab, var(--accent-secondary) 18%, transparent),\n      transparent 55%),\n    var(--bg-secondary);\n  border: 1px solid var(--border-default);\n  overflow: hidden;\n  position: relative;\n  isolation: isolate;\n}\n.dashboard__hero::before {\n  content: "";\n  position: absolute;\n  inset: 0;\n  background-image:\n    linear-gradient(var(--border-subtle) 1px, transparent 1px),\n    linear-gradient(\n      90deg,\n      var(--border-subtle) 1px,\n      transparent 1px);\n  background-size: 48px 48px;\n  -webkit-mask-image:\n    radial-gradient(\n      ellipse 70% 80% at 50% 50%,\n      #000 25%,\n      transparent 75%);\n  mask-image:\n    radial-gradient(\n      ellipse 70% 80% at 50% 50%,\n      #000 25%,\n      transparent 75%);\n  z-index: -1;\n  opacity: 0.6;\n}\n.dashboard__greeting {\n  min-width: 0;\n  flex: 1;\n}\n.dashboard__eyebrow {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: 4px 12px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-primary) 30%, transparent);\n  color: var(--accent-primary);\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  font-weight: 500;\n  letter-spacing: 0.06em;\n  text-transform: uppercase;\n  margin: 0 0 var(--space-4);\n}\n.dashboard__pulse {\n  width: 6px;\n  height: 6px;\n  border-radius: 50%;\n  background: var(--accent-primary);\n  box-shadow: 0 0 8px var(--accent-primary);\n  animation: dashboard-pulse 2.2s ease-in-out infinite;\n}\n@keyframes dashboard-pulse {\n  0%, 100% {\n    opacity: 1;\n    transform: scale(1);\n  }\n  50% {\n    opacity: 0.5;\n    transform: scale(1.4);\n  }\n}\n.dashboard__title {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: clamp(1.75rem, 2.4vw + 0.5rem, 2.5rem);\n  line-height: 1.1;\n  letter-spacing: -0.02em;\n  color: var(--text-primary);\n  margin: 0 0 var(--space-3);\n}\n.dashboard__subtitle {\n  font-size: 0.9375rem;\n  color: var(--text-secondary);\n  margin: 0;\n  line-height: 1.5;\n}\n.dashboard__subtitle strong {\n  color: var(--text-primary);\n  font-weight: 600;\n}\n.dashboard__clock {\n  display: none;\n  flex-direction: column;\n  align-items: flex-end;\n  padding: var(--space-4) var(--space-5);\n  border-radius: var(--radius-lg);\n  background: color-mix(in oklab, var(--bg-primary) 60%, transparent);\n  border: 1px solid var(--border-subtle);\n  flex-shrink: 0;\n}\n@media (min-width: 640px) {\n  .dashboard__clock {\n    display: flex;\n  }\n}\n.dashboard__clock-value {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 2rem;\n  line-height: 1;\n  color: var(--text-primary);\n  letter-spacing: -0.02em;\n  font-variant-numeric: tabular-nums;\n}\n.dashboard__clock-label {\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  color: var(--text-muted);\n  letter-spacing: 0.06em;\n  text-transform: uppercase;\n  margin-top: 4px;\n}\n.dashboard__grid {\n  display: grid;\n  gap: var(--space-4);\n  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));\n}\n.dashboard__stat-link {\n  display: block;\n  text-decoration: none;\n  color: inherit;\n  border-radius: var(--radius-lg, 14px);\n  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);\n  outline: none;\n}\n.dashboard__stat-link:hover {\n  transform: translateY(-2px);\n}\n.dashboard__stat-link:focus-visible {\n  box-shadow: 0 0 0 2px color-mix(in oklab, var(--accent-primary) 55%, transparent);\n}\n.dashboard__grid > app-stat-card,\n.dashboard__grid > .dashboard__stat-link {\n  animation: card-in 0.5s var(--ease-out) both;\n}\n.dashboard__grid > :nth-child(1) {\n  animation-delay: 0.05s;\n}\n.dashboard__grid > :nth-child(2) {\n  animation-delay: 0.12s;\n}\n.dashboard__grid > :nth-child(3) {\n  animation-delay: 0.19s;\n}\n.dashboard__grid > :nth-child(4) {\n  animation-delay: 0.26s;\n}\n.dashboard__grid > :nth-child(5) {\n  animation-delay: 0.33s;\n}\n@keyframes card-in {\n  from {\n    opacity: 0;\n    transform: translateY(14px);\n  }\n  to {\n    opacity: 1;\n    transform: translateY(0);\n  }\n}\n.dashboard__error {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: var(--space-4) var(--space-5);\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 30%, transparent);\n  color: var(--accent-danger);\n  font-size: 0.875rem;\n}\n.dashboard__error i {\n  font-size: 20px;\n}\n.dashboard__section {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-5);\n}\n.dashboard__section-head {\n  display: flex;\n  flex-direction: column;\n  gap: 4px;\n}\n.dashboard__section-title {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 1.25rem;\n  color: var(--text-primary);\n  margin: 0;\n  letter-spacing: -0.01em;\n}\n.dashboard__section-sub {\n  font-size: 0.875rem;\n  color: var(--text-muted);\n  margin: 0;\n}\n.dashboard__actions {\n  display: grid;\n  gap: var(--space-4);\n  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));\n}\n.action-card {\n  --stat-color: var(--accent-primary);\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n  padding: var(--space-5);\n  border-radius: var(--radius-lg);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  text-decoration: none;\n  color: inherit;\n  position: relative;\n  overflow: hidden;\n  transition:\n    transform var(--duration-slow) var(--ease-out),\n    border-color var(--duration-slow) var(--ease-out),\n    box-shadow var(--duration-slow) var(--ease-out);\n}\n.action-card[data-accent=warning] {\n  --stat-color: var(--accent-warning);\n}\n.action-card[data-accent=info] {\n  --stat-color: var(--accent-info);\n}\n.action-card:hover {\n  transform: translateY(-2px);\n  border-color: color-mix(in oklab, var(--stat-color) 40%, transparent);\n  box-shadow: 0 0 24px color-mix(in oklab, var(--stat-color) 14%, transparent), var(--shadow-md);\n}\n.action-card:hover .action-card__arrow {\n  transform: translateX(4px);\n  color: var(--stat-color);\n}\n.action-card__icon {\n  width: 52px;\n  height: 52px;\n  border-radius: var(--radius-md);\n  display: grid;\n  place-items: center;\n  background: color-mix(in oklab, var(--stat-color) 14%, transparent);\n  border: 1px solid color-mix(in oklab, var(--stat-color) 28%, transparent);\n  color: var(--stat-color);\n  flex-shrink: 0;\n}\n.action-card__icon i {\n  font-size: 24px;\n}\n.action-card__body {\n  flex: 1;\n  min-width: 0;\n}\n.action-card__title {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 1rem;\n  margin: 0 0 4px;\n  color: var(--text-primary);\n}\n.action-card__text {\n  font-size: 0.8125rem;\n  color: var(--text-secondary);\n  margin: 0;\n  line-height: 1.4;\n}\n.action-card__arrow {\n  color: var(--text-muted);\n  font-size: 20px;\n  transition: transform var(--duration-base) var(--ease-out), color var(--duration-base) var(--ease-out);\n}\n@media (prefers-reduced-motion: reduce) {\n  .dashboard,\n  .dashboard__grid > app-stat-card {\n    animation: none;\n  }\n  .dashboard__pulse {\n    animation: none;\n  }\n}\n/*# sourceMappingURL=admin-dashboard.component.css.map */\n'] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(AdminDashboardComponent, { className: "AdminDashboardComponent", filePath: "src/app/features/admin/dashboard/admin-dashboard.component.ts", lineNumber: 31 });
})();
export {
  AdminDashboardComponent
};
//# sourceMappingURL=chunk-SG22DCTG.js.map
