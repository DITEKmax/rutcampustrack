import {
  HeadmanApiService
} from "./chunk-BU5FJGI6.js";
import "./chunk-33KX5TJL.js";
import {
  animate,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import {
  AuthService
} from "./chunk-URGWZVMC.js";
import {
  StatCardComponent
} from "./chunk-6SXDTYWQ.js";
import {
  RouterLink,
  RouterModule
} from "./chunk-643P6YYN.js";
import "./chunk-7WMFDRFR.js";
import {
  DatePipe
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  catchError,
  computed,
  forkJoin,
  inject,
  of,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵclassProp,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵnextContext,
  ɵɵpipe,
  ɵɵpipeBind2,
  ɵɵproperty,
  ɵɵpureFunction0,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵrepeaterTrackByIdentity,
  ɵɵsyntheticHostProperty,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1,
  ɵɵtextInterpolate2
} from "./chunk-MFRIGFR2.js";

// src/app/features/headman/dashboard/headman-dashboard.component.ts
var _c0 = () => [1, 2, 3, 4];
function HeadmanDashboardComponent_Conditional_0_For_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 3);
  }
}
function HeadmanDashboardComponent_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 1)(1, "div", 2);
    \u0275\u0275repeaterCreate(2, HeadmanDashboardComponent_Conditional_0_For_3_Template, 1, 0, "div", 3, \u0275\u0275repeaterTrackByIdentity);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(4, "span", 4);
    \u0275\u0275text(5, "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...");
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    \u0275\u0275advance(2);
    \u0275\u0275repeater(\u0275\u0275pureFunction0(0, _c0));
  }
}
function HeadmanDashboardComponent_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 0);
    \u0275\u0275element(1, "i", 5);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.error(), " ");
  }
}
function HeadmanDashboardComponent_Conditional_2_Conditional_21_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 23);
    \u0275\u0275element(1, "span", 24);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275classProp("pill--success", ctx_r0.todayLesson().status === "ACTIVE")("pill--info", ctx_r0.todayLesson().status !== "ACTIVE");
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.todayLesson().status === "ACTIVE" ? "\u0418\u0434\u0451\u0442" : "\u0417\u0430\u043F\u043B\u0430\u043D\u0438\u0440\u043E\u0432\u0430\u043D\u0430", " ");
  }
}
function HeadmanDashboardComponent_Conditional_2_Conditional_23_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275text(0);
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275textInterpolate1(" \xB7 \u0430\u0443\u0434. ", ctx_r0.todayLesson().room, " ");
  }
}
function HeadmanDashboardComponent_Conditional_2_Conditional_23_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 25);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(2, "p", 26);
    \u0275\u0275text(3);
    \u0275\u0275pipe(4, "date");
    \u0275\u0275pipe(5, "date");
    \u0275\u0275template(6, HeadmanDashboardComponent_Conditional_2_Conditional_23_Conditional_6_Template, 1, 1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.todayLesson().subjectName);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate2(" ", \u0275\u0275pipeBind2(4, 4, ctx_r0.todayLesson().startTime, "HH:mm"), " \u2013 ", \u0275\u0275pipeBind2(5, 7, ctx_r0.todayLesson().endTime, "HH:mm"), " ");
    \u0275\u0275advance(3);
    \u0275\u0275conditional(ctx_r0.todayLesson().room ? 6 : -1);
  }
}
function HeadmanDashboardComponent_Conditional_2_Conditional_24_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 21);
    \u0275\u0275element(1, "i", 27);
    \u0275\u0275text(2, " \u041D\u0435\u0442 \u043F\u0430\u0440\u044B \u0441\u0435\u0433\u043E\u0434\u043D\u044F ");
    \u0275\u0275elementEnd();
  }
}
function HeadmanDashboardComponent_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 1)(1, "header", 6)(2, "div", 7)(3, "p", 8);
    \u0275\u0275element(4, "span", 9);
    \u0275\u0275text(5, " \u041A\u0430\u0431\u0438\u043D\u0435\u0442 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B ");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "h2", 10);
    \u0275\u0275text(7);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(8, "p", 11);
    \u0275\u0275text(9);
    \u0275\u0275elementEnd()()();
    \u0275\u0275elementStart(10, "div", 12);
    \u0275\u0275element(11, "app-stat-card", 13)(12, "app-stat-card", 14);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(13, "div", 12)(14, "section", 15)(15, "header", 16)(16, "div")(17, "p", 17);
    \u0275\u0275text(18, "\u0421\u0435\u0439\u0447\u0430\u0441 / \u0441\u043A\u043E\u0440\u043E");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(19, "h3", 18);
    \u0275\u0275text(20, "\u041F\u0430\u0440\u0430 \u0441\u0435\u0433\u043E\u0434\u043D\u044F");
    \u0275\u0275elementEnd()();
    \u0275\u0275template(21, HeadmanDashboardComponent_Conditional_2_Conditional_21_Template, 3, 5, "span", 19);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(22, "div", 20);
    \u0275\u0275template(23, HeadmanDashboardComponent_Conditional_2_Conditional_23_Template, 7, 10)(24, HeadmanDashboardComponent_Conditional_2_Conditional_24_Template, 3, 0, "p", 21);
    \u0275\u0275elementEnd()();
    \u0275\u0275element(25, "app-stat-card", 22);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(7);
    \u0275\u0275textInterpolate(ctx_r0.greeting());
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.dateLabel());
    \u0275\u0275advance(2);
    \u0275\u0275property("value", ctx_r0.memberCount());
    \u0275\u0275advance();
    \u0275\u0275property("value", ctx_r0.pendingExcuses());
    \u0275\u0275advance(9);
    \u0275\u0275conditional(ctx_r0.todayLesson() ? 21 : -1);
    \u0275\u0275advance(2);
    \u0275\u0275conditional(ctx_r0.todayLesson() ? 23 : 24);
    \u0275\u0275advance(2);
    \u0275\u0275property("value", ctx_r0.pendingLateCheckins());
  }
}
var HeadmanDashboardComponent = class _HeadmanDashboardComponent {
  auth = inject(AuthService);
  headmanApi = inject(HeadmanApiService);
  loading = signal(false);
  error = signal(null);
  memberCount = signal(0);
  todayLesson = signal(null);
  pendingExcuses = signal(0);
  pendingLateCheckins = signal(0);
  _now = signal(/* @__PURE__ */ new Date());
  greeting = computed(() => {
    const hour = this._now().getHours();
    if (hour < 12)
      return "\u0414\u043E\u0431\u0440\u043E\u0435 \u0443\u0442\u0440\u043E";
    if (hour < 17)
      return "\u0414\u043E\u0431\u0440\u044B\u0439 \u0434\u0435\u043D\u044C";
    return "\u0414\u043E\u0431\u0440\u044B\u0439 \u0432\u0435\u0447\u0435\u0440";
  });
  dateLabel = computed(() => this._now().toLocaleDateString("ru-RU", {
    weekday: "long",
    day: "numeric",
    month: "long"
  }));
  ngOnInit() {
    this.loading.set(true);
    const groupId = this.auth.currentUser()?.groupId;
    if (!groupId) {
      this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0434\u0430\u043D\u043D\u044B\u0435 \u0433\u0440\u0443\u043F\u043F\u044B. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043E\u0431\u043D\u043E\u0432\u0438\u0442\u044C \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0443.");
      this.loading.set(false);
      return;
    }
    forkJoin([
      this.headmanApi.getGroupMembers(0, 1),
      this.headmanApi.getTodayLessons(groupId).pipe(catchError(() => of(null))),
      this.headmanApi.getGroupExcuses(groupId, "submitted").pipe(catchError(() => of([])))
    ]).subscribe({
      next: ([members, lessons, excuses]) => {
        this.memberCount.set(members?.page?.totalElements ?? 0);
        const embedded = lessons?._embedded;
        const lessonList = embedded ? Object.values(embedded)[0] : [];
        this.todayLesson.set(lessonList[0] ?? null);
        this.pendingExcuses.set(Array.isArray(excuses) ? excuses.length : 0);
        this.pendingLateCheckins.set(0);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0434\u0430\u043D\u043D\u044B\u0435 \u0433\u0440\u0443\u043F\u043F\u044B. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043E\u0431\u043D\u043E\u0432\u0438\u0442\u044C \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0443.");
        this.loading.set(false);
      }
    });
  }
  static \u0275fac = function HeadmanDashboardComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanDashboardComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanDashboardComponent, selectors: [["app-headman-dashboard"]], hostVars: 1, hostBindings: function HeadmanDashboardComponent_HostBindings(rf, ctx) {
    if (rf & 2) {
      \u0275\u0275syntheticHostProperty("@routeFade", void 0);
    }
  }, decls: 3, vars: 1, consts: [["role", "alert", 1, "dashboard-error"], [1, "headman-dash"], ["aria-hidden", "true", 1, "stat-grid", "skeleton-grid"], [1, "skeleton-card"], ["aria-live", "polite", 1, "sr-only"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"], [1, "page-hero"], [1, "page-hero__body"], [1, "page-hero__eyebrow"], ["aria-hidden", "true", 1, "page-hero__pulse"], [1, "page-hero__title"], [1, "page-hero__subtitle"], [1, "stat-grid"], ["label", "\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432 \u0432 \u0433\u0440\u0443\u043F\u043F\u0435", "icon", "ph-duotone ph-users", "accent", "primary", "routerLink", "/headman/group", 1, "stat-card-link", 3, "value"], ["label", "\u0422\u0438\u043A\u0435\u0442\u044B \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0435", "icon", "ph-duotone ph-file-text", "accent", "warning", "routerLink", "/headman/excuses", 1, "stat-card-link", 3, "value"], ["aria-labelledby", "today-lesson-title", 1, "chart-card", "today-lesson-card"], [1, "chart-card__head"], [1, "chart-card__eyebrow"], ["id", "today-lesson-title", 1, "chart-card__title"], [1, "pill", 3, "pill--success", "pill--info"], [1, "today-lesson-card__body"], [1, "no-lesson"], ["label", "\u0417\u0430\u043F\u0440\u043E\u0441\u044B \u043F\u043E\u0437\u0434\u043D\u0435\u0439 \u043E\u0442\u043C\u0435\u0442\u043A\u0438", "icon", "ph-duotone ph-clock-countdown", "accent", "info", 3, "value"], [1, "pill"], ["aria-hidden", "true", 1, "pill__dot"], [1, "lesson-name"], [1, "lesson-time"], ["aria-hidden", "true", 1, "ph", "ph-calendar-x"]], template: function HeadmanDashboardComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275template(0, HeadmanDashboardComponent_Conditional_0_Template, 6, 1)(1, HeadmanDashboardComponent_Conditional_1_Template, 3, 1, "div", 0)(2, HeadmanDashboardComponent_Conditional_2_Template, 26, 7, "div", 1);
    }
    if (rf & 2) {
      \u0275\u0275conditional(ctx.loading() ? 0 : ctx.error() ? 1 : 2);
    }
  }, dependencies: [RouterModule, RouterLink, StatCardComponent, DatePipe], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.headman-dash[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-6);\n}\n.stat-grid[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: repeat(2, 1fr);\n  gap: var(--space-5);\n}\n@media (max-width: 600px) {\n  .stat-grid[_ngcontent-%COMP%] {\n    grid-template-columns: 1fr;\n  }\n}\n.stat-card-link[_ngcontent-%COMP%] {\n  display: block;\n  cursor: pointer;\n}\n.today-lesson-card__body[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2);\n  min-height: 80px;\n}\n.lesson-name[_ngcontent-%COMP%] {\n  margin: 0;\n  font: 600 var(--text-xl)/1.2 var(--font-heading);\n  color: var(--text-primary);\n}\n.lesson-time[_ngcontent-%COMP%] {\n  margin: 0;\n  font-family: var(--font-mono);\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n  font-variant-numeric: tabular-nums;\n}\n.no-lesson[_ngcontent-%COMP%] {\n  margin: 0;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  font: 500 var(--text-base)/1.4 var(--font-heading);\n  color: var(--text-secondary);\n}\n.no-lesson[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 20px;\n  color: var(--text-muted);\n}\n.dashboard-error[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-4);\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 28%, transparent);\n  color: var(--accent-danger);\n  font-size: var(--text-sm);\n}\n.skeleton-grid[_ngcontent-%COMP%] {\n  margin-bottom: var(--space-5);\n}\n.skeleton-card[_ngcontent-%COMP%] {\n  min-height: 168px;\n  border-radius: var(--radius-xl);\n  background:\n    linear-gradient(\n      90deg,\n      var(--bg-elevated) 25%,\n      color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,\n      var(--bg-elevated) 75%);\n  background-size: 200% 100%;\n  animation: _ngcontent-%COMP%_dashboard-shimmer 1.5s linear infinite;\n}\n@keyframes _ngcontent-%COMP%_dashboard-shimmer {\n  0% {\n    background-position: 200% 0;\n  }\n  100% {\n    background-position: -200% 0;\n  }\n}\n.sr-only[_ngcontent-%COMP%] {\n  position: absolute;\n  width: 1px;\n  height: 1px;\n  padding: 0;\n  margin: -1px;\n  overflow: hidden;\n  clip: rect(0, 0, 0, 0);\n  white-space: nowrap;\n  border: 0;\n}\n@media (prefers-reduced-motion: reduce) {\n  .skeleton-card[_ngcontent-%COMP%] {\n    animation: none;\n  }\n}\n/*# sourceMappingURL=headman-dashboard.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanDashboardComponent, [{
    type: Component,
    args: [{ selector: "app-headman-dashboard", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [RouterModule, StatCardComponent, DatePipe], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], host: { "[@routeFade]": "" }, template: `
    @if (loading()) {
      <div class="headman-dash">
        <div class="stat-grid skeleton-grid" aria-hidden="true">
          @for (i of [1,2,3,4]; track i) {
            <div class="skeleton-card"></div>
          }
        </div>
      </div>
      <span aria-live="polite" class="sr-only">\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...</span>
    } @else if (error()) {
      <div class="dashboard-error" role="alert">
        <i class="ph ph-warning-circle" aria-hidden="true"></i>
        {{ error() }}
      </div>
    } @else {
      <div class="headman-dash">
        <header class="page-hero">
          <div class="page-hero__body">
            <p class="page-hero__eyebrow">
              <span class="page-hero__pulse" aria-hidden="true"></span>
              \u041A\u0430\u0431\u0438\u043D\u0435\u0442 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B
            </p>
            <h2 class="page-hero__title">{{ greeting() }}</h2>
            <p class="page-hero__subtitle">{{ dateLabel() }}</p>
          </div>
        </header>

        <div class="stat-grid">
          <app-stat-card
            [value]="memberCount()"
            label="\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432 \u0432 \u0433\u0440\u0443\u043F\u043F\u0435"
            icon="ph-duotone ph-users"
            accent="primary"
            routerLink="/headman/group"
            class="stat-card-link" />
          <app-stat-card
            [value]="pendingExcuses()"
            label="\u0422\u0438\u043A\u0435\u0442\u044B \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0435"
            icon="ph-duotone ph-file-text"
            accent="warning"
            routerLink="/headman/excuses"
            class="stat-card-link" />
        </div>

        <div class="stat-grid">
          <section class="chart-card today-lesson-card" aria-labelledby="today-lesson-title">
            <header class="chart-card__head">
              <div>
                <p class="chart-card__eyebrow">\u0421\u0435\u0439\u0447\u0430\u0441 / \u0441\u043A\u043E\u0440\u043E</p>
                <h3 class="chart-card__title" id="today-lesson-title">\u041F\u0430\u0440\u0430 \u0441\u0435\u0433\u043E\u0434\u043D\u044F</h3>
              </div>
              @if (todayLesson()) {
                <span class="pill" [class.pill--success]="todayLesson()!.status === 'ACTIVE'" [class.pill--info]="todayLesson()!.status !== 'ACTIVE'">
                  <span class="pill__dot" aria-hidden="true"></span>
                  {{ todayLesson()!.status === 'ACTIVE' ? '\u0418\u0434\u0451\u0442' : '\u0417\u0430\u043F\u043B\u0430\u043D\u0438\u0440\u043E\u0432\u0430\u043D\u0430' }}
                </span>
              }
            </header>
            <div class="today-lesson-card__body">
              @if (todayLesson()) {
                <p class="lesson-name">{{ todayLesson()!.subjectName }}</p>
                <p class="lesson-time">
                  {{ todayLesson()!.startTime | date:'HH:mm' }} \u2013
                  {{ todayLesson()!.endTime | date:'HH:mm' }}
                  @if (todayLesson()!.room) {
                    \xB7 \u0430\u0443\u0434. {{ todayLesson()!.room }}
                  }
                </p>
              } @else {
                <p class="no-lesson">
                  <i class="ph ph-calendar-x" aria-hidden="true"></i>
                  \u041D\u0435\u0442 \u043F\u0430\u0440\u044B \u0441\u0435\u0433\u043E\u0434\u043D\u044F
                </p>
              }
            </div>
          </section>

          <app-stat-card
            [value]="pendingLateCheckins()"
            label="\u0417\u0430\u043F\u0440\u043E\u0441\u044B \u043F\u043E\u0437\u0434\u043D\u0435\u0439 \u043E\u0442\u043C\u0435\u0442\u043A\u0438"
            icon="ph-duotone ph-clock-countdown"
            accent="info" />
        </div>
      </div>
    }
  `, styles: ["/* angular:styles/component:css;1554b9c1eefcc712cc2c0b961766869f2d6c866aabe580c1968dfb3cf86849fc;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/dashboard/headman-dashboard.component.ts */\n:host {\n  display: block;\n}\n.headman-dash {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-6);\n}\n.stat-grid {\n  display: grid;\n  grid-template-columns: repeat(2, 1fr);\n  gap: var(--space-5);\n}\n@media (max-width: 600px) {\n  .stat-grid {\n    grid-template-columns: 1fr;\n  }\n}\n.stat-card-link {\n  display: block;\n  cursor: pointer;\n}\n.today-lesson-card__body {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2);\n  min-height: 80px;\n}\n.lesson-name {\n  margin: 0;\n  font: 600 var(--text-xl)/1.2 var(--font-heading);\n  color: var(--text-primary);\n}\n.lesson-time {\n  margin: 0;\n  font-family: var(--font-mono);\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n  font-variant-numeric: tabular-nums;\n}\n.no-lesson {\n  margin: 0;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  font: 500 var(--text-base)/1.4 var(--font-heading);\n  color: var(--text-secondary);\n}\n.no-lesson i {\n  font-size: 20px;\n  color: var(--text-muted);\n}\n.dashboard-error {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-4);\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 28%, transparent);\n  color: var(--accent-danger);\n  font-size: var(--text-sm);\n}\n.skeleton-grid {\n  margin-bottom: var(--space-5);\n}\n.skeleton-card {\n  min-height: 168px;\n  border-radius: var(--radius-xl);\n  background:\n    linear-gradient(\n      90deg,\n      var(--bg-elevated) 25%,\n      color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,\n      var(--bg-elevated) 75%);\n  background-size: 200% 100%;\n  animation: dashboard-shimmer 1.5s linear infinite;\n}\n@keyframes dashboard-shimmer {\n  0% {\n    background-position: 200% 0;\n  }\n  100% {\n    background-position: -200% 0;\n  }\n}\n.sr-only {\n  position: absolute;\n  width: 1px;\n  height: 1px;\n  padding: 0;\n  margin: -1px;\n  overflow: hidden;\n  clip: rect(0, 0, 0, 0);\n  white-space: nowrap;\n  border: 0;\n}\n@media (prefers-reduced-motion: reduce) {\n  .skeleton-card {\n    animation: none;\n  }\n}\n/*# sourceMappingURL=headman-dashboard.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanDashboardComponent, { className: "HeadmanDashboardComponent", filePath: "src/app/features/headman/dashboard/headman-dashboard.component.ts", lineNumber: 206 });
})();
export {
  HeadmanDashboardComponent
};
//# sourceMappingURL=chunk-6EU5CYLO.js.map
