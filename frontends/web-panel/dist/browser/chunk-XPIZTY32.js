import {
  HeadmanApiService
} from "./chunk-ZC6IXG25.js";
import "./chunk-OYYF72SB.js";
import {
  animate,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import {
  AuthService
} from "./chunk-ID5GKTOO.js";
import {
  StatCardComponent
} from "./chunk-YLIW7ZOJ.js";
import {
  RouterLink,
  RouterModule
} from "./chunk-BRINUWSL.js";
import "./chunk-RWV7HGF7.js";
import "./chunk-FLE4DLW4.js";
import {
  DatePipe
} from "./chunk-CLRYRPPS.js";
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
} from "./chunk-4M4FDLBS.js";

// src/app/features/headman/dashboard/headman-dashboard.component.ts
var _c0 = () => [1, 2, 3, 4];
function HeadmanDashboardComponent_Conditional_0_For_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 3);
  }
}
function HeadmanDashboardComponent_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 2);
    \u0275\u0275repeaterCreate(1, HeadmanDashboardComponent_Conditional_0_For_2_Template, 1, 0, "div", 3, \u0275\u0275repeaterTrackByIdentity);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "span", 4);
    \u0275\u0275text(4, "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...");
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    \u0275\u0275advance();
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
function HeadmanDashboardComponent_Conditional_2_Conditional_14_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 18);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.todayLesson().room);
  }
}
function HeadmanDashboardComponent_Conditional_2_Conditional_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "i", 15);
    \u0275\u0275elementStart(1, "p", 16);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "p", 17);
    \u0275\u0275text(4);
    \u0275\u0275pipe(5, "date");
    \u0275\u0275pipe(6, "date");
    \u0275\u0275elementEnd();
    \u0275\u0275template(7, HeadmanDashboardComponent_Conditional_2_Conditional_14_Conditional_7_Template, 2, 1, "p", 18);
    \u0275\u0275elementStart(8, "span", 19);
    \u0275\u0275text(9);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.todayLesson().subjectName);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate2(" ", \u0275\u0275pipeBind2(5, 7, ctx_r0.todayLesson().startTime, "HH:mm"), " \u2013 ", \u0275\u0275pipeBind2(6, 10, ctx_r0.todayLesson().endTime, "HH:mm"), " ");
    \u0275\u0275advance(3);
    \u0275\u0275conditional(ctx_r0.todayLesson().room ? 7 : -1);
    \u0275\u0275advance();
    \u0275\u0275classProp("active", ctx_r0.todayLesson().status === "ACTIVE");
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r0.todayLesson().status === "ACTIVE" ? "\u0418\u0434\u0451\u0442" : "\u0417\u0430\u043F\u043B\u0430\u043D\u0438\u0440\u043E\u0432\u0430\u043D\u0430", " ");
  }
}
function HeadmanDashboardComponent_Conditional_2_Conditional_15_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "i", 20);
    \u0275\u0275elementStart(1, "p", 21);
    \u0275\u0275text(2, "\u041D\u0435\u0442 \u043F\u0430\u0440\u044B \u0441\u0435\u0433\u043E\u0434\u043D\u044F");
    \u0275\u0275elementEnd();
  }
}
function HeadmanDashboardComponent_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 1)(1, "div", 6)(2, "div")(3, "span", 7);
    \u0275\u0275text(4, "\u041A\u0430\u0431\u0438\u043D\u0435\u0442 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "h1", 8);
    \u0275\u0275text(6);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(7, "p", 9);
    \u0275\u0275text(8);
    \u0275\u0275elementEnd()()();
    \u0275\u0275elementStart(9, "div", 10);
    \u0275\u0275element(10, "app-stat-card", 11)(11, "app-stat-card", 12);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(12, "div", 10)(13, "div", 13);
    \u0275\u0275template(14, HeadmanDashboardComponent_Conditional_2_Conditional_14_Template, 10, 13)(15, HeadmanDashboardComponent_Conditional_2_Conditional_15_Template, 3, 0);
    \u0275\u0275elementEnd();
    \u0275\u0275element(16, "app-stat-card", 14);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(6);
    \u0275\u0275textInterpolate(ctx_r0.greeting());
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.dateLabel());
    \u0275\u0275advance(2);
    \u0275\u0275property("value", ctx_r0.memberCount());
    \u0275\u0275advance();
    \u0275\u0275property("value", ctx_r0.pendingExcuses());
    \u0275\u0275advance(2);
    \u0275\u0275classProp("has-lesson", ctx_r0.todayLesson());
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.todayLesson() ? 14 : 15);
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
  }, decls: 3, vars: 1, consts: [[1, "page-error"], [1, "page-stack"], ["aria-hidden", "true", 1, "stat-grid", "skeleton-grid"], [1, "skeleton-card"], ["aria-live", "polite", 1, "sr-only"], [1, "ph", "ph-warning-circle"], [1, "page-header"], [1, "page-eyebrow"], [1, "page-title"], [1, "page-date"], [1, "stat-grid"], ["label", "\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432 \u0432 \u0433\u0440\u0443\u043F\u043F\u0435", "icon", "ph ph-users", "accent", "primary", "routerLink", "/headman/group", 2, "cursor", "pointer", 3, "value"], ["label", "\u0422\u0438\u043A\u0435\u0442\u044B \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0435", "icon", "ph ph-file-text", "accent", "warning", 3, "value"], [1, "page-card", "today-lesson-card"], ["label", "\u0417\u0430\u043F\u0440\u043E\u0441\u044B \u043F\u043E\u0437\u0434\u043D\u0435\u0439 \u043E\u0442\u043C\u0435\u0442\u043A\u0438", "icon", "ph ph-clock-countdown", "accent", "info", 3, "value"], [1, "ph", "ph-chalkboard-teacher"], [1, "lesson-name"], [1, "lesson-time"], [1, "lesson-room"], [1, "status-chip"], [1, "ph", "ph-calendar-x"], [1, "no-lesson"]], template: function HeadmanDashboardComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275template(0, HeadmanDashboardComponent_Conditional_0_Template, 5, 1)(1, HeadmanDashboardComponent_Conditional_1_Template, 3, 1, "div", 0)(2, HeadmanDashboardComponent_Conditional_2_Template, 17, 8, "div", 1);
    }
    if (rf & 2) {
      \u0275\u0275conditional(ctx.loading() ? 0 : ctx.error() ? 1 : 2);
    }
  }, dependencies: [RouterModule, RouterLink, StatCardComponent, DatePipe], styles: ["\n\n.stat-grid[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: repeat(2, 1fr);\n  gap: var(--space-5);\n  margin-bottom: var(--space-5);\n}\n@media (max-width: 600px) {\n  .stat-grid[_ngcontent-%COMP%] {\n    grid-template-columns: 1fr;\n  }\n}\n.today-lesson-card[_ngcontent-%COMP%] {\n  min-height: 80px;\n  border-left: 4px solid var(--border-subtle);\n}\n.today-lesson-card.has-lesson[_ngcontent-%COMP%] {\n  border-left-color: var(--accent-warning);\n}\n.lesson-name[_ngcontent-%COMP%] {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: var(--text-base);\n}\n.lesson-time[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: var(--text-xs);\n  color: var(--text-secondary);\n  font-variant-numeric: tabular-nums;\n}\n.lesson-room[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n}\n.no-lesson[_ngcontent-%COMP%] {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: var(--text-base);\n  color: var(--text-secondary);\n}\n.skeleton-card[_ngcontent-%COMP%] {\n  height: 100px;\n  background: var(--bg-elevated);\n  border-radius: var(--radius-md);\n  animation: dashboard-shimmer 1.5s ease-in-out infinite;\n}\n.sr-only[_ngcontent-%COMP%] {\n  position: absolute;\n  width: 1px;\n  height: 1px;\n  padding: 0;\n  margin: -1px;\n  overflow: hidden;\n  clip: rect(0, 0, 0, 0);\n  white-space: nowrap;\n  border: 0;\n}\n.page-date[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n  margin-top: var(--space-1);\n}\n/*# sourceMappingURL=headman-dashboard.component.css.map */"], data: { animation: [
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
      <div class="stat-grid skeleton-grid" aria-hidden="true">
        @for (i of [1,2,3,4]; track i) {
          <div class="skeleton-card"></div>
        }
      </div>
      <span aria-live="polite" class="sr-only">\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...</span>
    } @else if (error()) {
      <div class="page-error">
        <i class="ph ph-warning-circle"></i>
        {{ error() }}
      </div>
    } @else {
      <div class="page-stack">
        <!-- Page header -->
        <div class="page-header">
          <div>
            <span class="page-eyebrow">\u041A\u0430\u0431\u0438\u043D\u0435\u0442 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B</span>
            <h1 class="page-title">{{ greeting() }}</h1>
            <p class="page-date">{{ dateLabel() }}</p>
          </div>
        </div>

        <!-- Stat grid row 1: member count + excuse tickets -->
        <div class="stat-grid">
          <app-stat-card
            [value]="memberCount()"
            label="\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432 \u0432 \u0433\u0440\u0443\u043F\u043F\u0435"
            icon="ph ph-users"
            accent="primary"
            routerLink="/headman/group"
            style="cursor:pointer" />
          <app-stat-card
            [value]="pendingExcuses()"
            label="\u0422\u0438\u043A\u0435\u0442\u044B \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0435"
            icon="ph ph-file-text"
            accent="warning" />
        </div>

        <!-- Stat grid row 2: today's lesson card + late check-ins -->
        <div class="stat-grid">
          <!-- Today's lesson card (custom, not StatCardComponent) -->
          <div class="page-card today-lesson-card"
               [class.has-lesson]="todayLesson()">
            @if (todayLesson()) {
              <i class="ph ph-chalkboard-teacher"></i>
              <p class="lesson-name">{{ todayLesson()!.subjectName }}</p>
              <p class="lesson-time">
                {{ todayLesson()!.startTime | date:'HH:mm' }} \u2013
                {{ todayLesson()!.endTime | date:'HH:mm' }}
              </p>
              @if (todayLesson()!.room) {
                <p class="lesson-room">{{ todayLesson()!.room }}</p>
              }
              <span class="status-chip" [class.active]="todayLesson()!.status === 'ACTIVE'">
                {{ todayLesson()!.status === 'ACTIVE' ? '\u0418\u0434\u0451\u0442' : '\u0417\u0430\u043F\u043B\u0430\u043D\u0438\u0440\u043E\u0432\u0430\u043D\u0430' }}
              </span>
            } @else {
              <i class="ph ph-calendar-x"></i>
              <p class="no-lesson">\u041D\u0435\u0442 \u043F\u0430\u0440\u044B \u0441\u0435\u0433\u043E\u0434\u043D\u044F</p>
            }
          </div>

          <app-stat-card
            [value]="pendingLateCheckins()"
            label="\u0417\u0430\u043F\u0440\u043E\u0441\u044B \u043F\u043E\u0437\u0434\u043D\u0435\u0439 \u043E\u0442\u043C\u0435\u0442\u043A\u0438"
            icon="ph ph-clock-countdown"
            accent="info" />
        </div>
      </div>
    }
  `, styles: ["/* angular:styles/component:css;b1b0dae1de75d49bb74ca550f511847b72ef00175b1750b494a382eb5c2f944e;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/dashboard/headman-dashboard.component.ts */\n.stat-grid {\n  display: grid;\n  grid-template-columns: repeat(2, 1fr);\n  gap: var(--space-5);\n  margin-bottom: var(--space-5);\n}\n@media (max-width: 600px) {\n  .stat-grid {\n    grid-template-columns: 1fr;\n  }\n}\n.today-lesson-card {\n  min-height: 80px;\n  border-left: 4px solid var(--border-subtle);\n}\n.today-lesson-card.has-lesson {\n  border-left-color: var(--accent-warning);\n}\n.lesson-name {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: var(--text-base);\n}\n.lesson-time {\n  font-family: var(--font-mono);\n  font-size: var(--text-xs);\n  color: var(--text-secondary);\n  font-variant-numeric: tabular-nums;\n}\n.lesson-room {\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n}\n.no-lesson {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: var(--text-base);\n  color: var(--text-secondary);\n}\n.skeleton-card {\n  height: 100px;\n  background: var(--bg-elevated);\n  border-radius: var(--radius-md);\n  animation: dashboard-shimmer 1.5s ease-in-out infinite;\n}\n.sr-only {\n  position: absolute;\n  width: 1px;\n  height: 1px;\n  padding: 0;\n  margin: -1px;\n  overflow: hidden;\n  clip: rect(0, 0, 0, 0);\n  white-space: nowrap;\n  border: 0;\n}\n.page-date {\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n  margin-top: var(--space-1);\n}\n/*# sourceMappingURL=headman-dashboard.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanDashboardComponent, { className: "HeadmanDashboardComponent", filePath: "src/app/features/headman/dashboard/headman-dashboard.component.ts", lineNumber: 181 });
})();
export {
  HeadmanDashboardComponent
};
//# sourceMappingURL=chunk-XPIZTY32.js.map
