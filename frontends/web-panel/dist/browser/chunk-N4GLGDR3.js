import {
  formatLoadError
} from "./chunk-OWQVFP43.js";
import {
  StudentApiService
} from "./chunk-ESVH3VU5.js";
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
  MatProgressBar,
  MatProgressBarModule
} from "./chunk-PWYB6KY4.js";
import "./chunk-B3BDHDAM.js";
import {
  BaseChartDirective
} from "./chunk-XO6TQHL6.js";
import "./chunk-KLKVWNKW.js";
import {
  CommonModule
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  Input,
  forkJoin,
  inject,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵNgOnChangesFeature,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵclassProp,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵnextContext,
  ɵɵproperty,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1,
  ɵɵtextInterpolate2
} from "./chunk-MFRIGFR2.js";

// src/app/features/student/stats/student-subject-chart/student-subject-chart.component.ts
function StudentSubjectChartComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 4);
    \u0275\u0275element(1, "i", 7);
    \u0275\u0275elementStart(2, "span");
    \u0275\u0275text(3);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate1("\u041F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C \u043D\u0438\u0436\u0435 \u043F\u043E\u0440\u043E\u0433\u0430 (", ctx_r0.threshold, "%)");
  }
}
var StudentSubjectChartComponent = class _StudentSubjectChartComponent {
  stat;
  threshold;
  get isRedzone() {
    return this.stat.percentage < this.threshold;
  }
  barChartData = { labels: [], datasets: [] };
  barChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    animation: {
      duration: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 400
    },
    plugins: { legend: { position: "top" } },
    scales: {
      x: { stacked: true },
      y: { stacked: true, title: { display: true, text: "\u0417\u0430\u043D\u044F\u0442\u0438\u0439" } }
    }
  };
  ngOnChanges() {
    const freeAttendance = Math.max(0, this.stat.total - this.stat.attended - this.stat.absent - this.stat.excused);
    this.barChartData = {
      labels: [this.stat.subjectName],
      datasets: [
        { label: "\u041F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B", data: [this.stat.attended], backgroundColor: "rgba(0, 229, 160, 0.85)" },
        { label: "\u0423\u0432\u0430\u0436. \u043F\u0440\u0438\u0447\u0438\u043D\u0430", data: [this.stat.excused], backgroundColor: "rgba(245, 158, 11, 0.85)" },
        { label: "\u0421\u0432\u043E\u0431. \u043F\u043E\u0441\u0435\u0449\u0435\u043D\u0438\u0435", data: [freeAttendance], backgroundColor: "rgba(139, 92, 246, 0.85)" },
        { label: "\u041E\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B", data: [this.stat.absent], backgroundColor: "rgba(239, 68, 68, 0.85)" }
      ]
    };
  }
  static \u0275fac = function StudentSubjectChartComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentSubjectChartComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentSubjectChartComponent, selectors: [["app-student-subject-chart"]], inputs: { stat: "stat", threshold: "threshold" }, features: [\u0275\u0275NgOnChangesFeature], decls: 9, vars: 10, consts: [[1, "chart-card"], [1, "chart-card__header"], [1, "chart-card__title"], [1, "chart-card__ratio"], [1, "chart-card__redzone-badge"], [1, "chart-container"], ["baseChart", "", "role", "img", 3, "data", "options", "type"], [1, "ph-warning", "ph-fill"]], template: function StudentSubjectChartComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "h3", 2);
      \u0275\u0275text(3);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(4, "span", 3);
      \u0275\u0275text(5);
      \u0275\u0275elementEnd()();
      \u0275\u0275template(6, StudentSubjectChartComponent_Conditional_6_Template, 4, 1, "div", 4);
      \u0275\u0275elementStart(7, "div", 5);
      \u0275\u0275element(8, "canvas", 6);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275classProp("is-redzone", ctx.isRedzone);
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.stat.subjectName);
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate2("", ctx.stat.attended, "/", ctx.stat.total, "");
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.isRedzone ? 6 : -1);
      \u0275\u0275advance(2);
      \u0275\u0275property("data", ctx.barChartData)("options", ctx.barChartOptions)("type", "bar");
      \u0275\u0275attribute("aria-label", ctx.stat.subjectName + " \u2014 \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438");
    }
  }, dependencies: [CommonModule, BaseChartDirective], styles: ["\n\n.chart-card[_ngcontent-%COMP%] {\n  background: var(--bg-elevated);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-xl);\n  padding: var(--space-4);\n  height: 320px;\n  display: flex;\n  flex-direction: column;\n}\n.chart-card.is-redzone[_ngcontent-%COMP%] {\n  border-color: var(--accent-warning);\n}\n.chart-card__header[_ngcontent-%COMP%] {\n  display: flex;\n  justify-content: space-between;\n  align-items: baseline;\n  margin-bottom: var(--space-2);\n}\n.chart-card__title[_ngcontent-%COMP%] {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n}\n.chart-card__ratio[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  font-family: var(--font-mono);\n  font-weight: 600;\n  font-variant-numeric: tabular-nums;\n  color: var(--text-muted);\n}\n.chart-card__redzone-badge[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-1);\n  font-size: var(--text-xs);\n  color: var(--accent-warning);\n  background: color-mix(in oklab, var(--accent-warning) 10%, transparent);\n  border-radius: var(--radius-md);\n  padding: 4px var(--space-2);\n  margin-bottom: var(--space-2);\n}\n.chart-container[_ngcontent-%COMP%] {\n  flex: 1;\n  position: relative;\n  height: 240px;\n}\n/*# sourceMappingURL=student-subject-chart.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentSubjectChartComponent, [{
    type: Component,
    args: [{ selector: "app-student-subject-chart", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule, BaseChartDirective], template: `
    <div class="chart-card" [class.is-redzone]="isRedzone">
      <div class="chart-card__header">
        <h3 class="chart-card__title">{{ stat.subjectName }}</h3>
        <span class="chart-card__ratio">{{ stat.attended }}/{{ stat.total }}</span>
      </div>
      @if (isRedzone) {
        <div class="chart-card__redzone-badge">
          <i class="ph-warning ph-fill"></i>
          <span>\u041F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C \u043D\u0438\u0436\u0435 \u043F\u043E\u0440\u043E\u0433\u0430 ({{ threshold }}%)</span>
        </div>
      }
      <div class="chart-container">
        <canvas
          baseChart
          role="img"
          [attr.aria-label]="stat.subjectName + ' \u2014 \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438'"
          [data]="barChartData"
          [options]="barChartOptions"
          [type]="'bar'">
        </canvas>
      </div>
    </div>
  `, styles: ["/* angular:styles/component:css;c2121b9390e9bf9d5b1a65e9e828f4565d7d06f853d3943ce11ea0070cd5a11a;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/student/stats/student-subject-chart/student-subject-chart.component.ts */\n.chart-card {\n  background: var(--bg-elevated);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-xl);\n  padding: var(--space-4);\n  height: 320px;\n  display: flex;\n  flex-direction: column;\n}\n.chart-card.is-redzone {\n  border-color: var(--accent-warning);\n}\n.chart-card__header {\n  display: flex;\n  justify-content: space-between;\n  align-items: baseline;\n  margin-bottom: var(--space-2);\n}\n.chart-card__title {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n}\n.chart-card__ratio {\n  font-size: var(--text-sm);\n  font-family: var(--font-mono);\n  font-weight: 600;\n  font-variant-numeric: tabular-nums;\n  color: var(--text-muted);\n}\n.chart-card__redzone-badge {\n  display: flex;\n  align-items: center;\n  gap: var(--space-1);\n  font-size: var(--text-xs);\n  color: var(--accent-warning);\n  background: color-mix(in oklab, var(--accent-warning) 10%, transparent);\n  border-radius: var(--radius-md);\n  padding: 4px var(--space-2);\n  margin-bottom: var(--space-2);\n}\n.chart-container {\n  flex: 1;\n  position: relative;\n  height: 240px;\n}\n/*# sourceMappingURL=student-subject-chart.component.css.map */\n"] }]
  }], null, { stat: [{
    type: Input,
    args: [{ required: true }]
  }], threshold: [{
    type: Input,
    args: [{ required: true }]
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentSubjectChartComponent, { className: "StudentSubjectChartComponent", filePath: "src/app/features/student/stats/student-subject-chart/student-subject-chart.component.ts", lineNumber: 61 });
})();

// src/app/features/student/stats/student-overall-card/student-overall-card.component.ts
var StudentOverallCardComponent = class _StudentOverallCardComponent {
  stats;
  threshold;
  static \u0275fac = function StudentOverallCardComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentOverallCardComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentOverallCardComponent, selectors: [["app-student-overall-card"]], inputs: { stats: "stats", threshold: "threshold" }, decls: 11, vars: 6, consts: [["aria-live", "polite", 1, "overall-card"], [1, "overall-card__stat"], [1, "overall-card__label"], [1, "overall-card__value", "overall-card__value--mono"], [1, "overall-card__percentage"]], template: function StudentOverallCardComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "span", 2);
      \u0275\u0275text(3, "\u0412\u0441\u0435\u0433\u043E \u0437\u0430\u043D\u044F\u0442\u0438\u0439");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(4, "span", 3);
      \u0275\u0275text(5);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(6, "div", 1)(7, "span", 2);
      \u0275\u0275text(8, "\u041F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(9, "span", 4);
      \u0275\u0275text(10);
      \u0275\u0275elementEnd()()();
    }
    if (rf & 2) {
      \u0275\u0275advance(5);
      \u0275\u0275textInterpolate(ctx.stats.total);
      \u0275\u0275advance(4);
      \u0275\u0275classProp("is-good", ctx.stats.percentage >= ctx.threshold)("is-warning", ctx.stats.percentage < ctx.threshold);
      \u0275\u0275advance();
      \u0275\u0275textInterpolate1(" ", ctx.stats.percentage, "% ");
    }
  }, dependencies: [CommonModule], styles: ["\n\n.overall-card[_ngcontent-%COMP%] {\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-xl);\n  padding: var(--space-5);\n  display: flex;\n  gap: var(--space-6);\n  align-items: center;\n  margin-bottom: var(--space-5);\n}\n.overall-card__stat[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-1);\n}\n.overall-card__label[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  text-transform: uppercase;\n  letter-spacing: 0.05em;\n  font-weight: 600;\n}\n.overall-card__value--mono[_ngcontent-%COMP%] {\n  font-size: var(--text-2xl);\n  font-family: var(--font-mono);\n  font-weight: 600;\n  font-variant-numeric: tabular-nums;\n}\n.overall-card__percentage[_ngcontent-%COMP%] {\n  font-size: var(--text-2xl);\n  font-family: var(--font-mono);\n  font-weight: 600;\n  font-variant-numeric: tabular-nums;\n}\n.overall-card__percentage.is-good[_ngcontent-%COMP%] {\n  color: var(--accent-primary);\n}\n.overall-card__percentage.is-warning[_ngcontent-%COMP%] {\n  color: var(--accent-warning);\n}\n/*# sourceMappingURL=student-overall-card.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentOverallCardComponent, [{
    type: Component,
    args: [{ selector: "app-student-overall-card", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule], template: `
    <div class="overall-card" aria-live="polite">
      <div class="overall-card__stat">
        <span class="overall-card__label">\u0412\u0441\u0435\u0433\u043E \u0437\u0430\u043D\u044F\u0442\u0438\u0439</span>
        <span class="overall-card__value overall-card__value--mono">{{ stats.total }}</span>
      </div>
      <div class="overall-card__stat">
        <span class="overall-card__label">\u041F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C</span>
        <span
          class="overall-card__percentage"
          [class.is-good]="stats.percentage >= threshold"
          [class.is-warning]="stats.percentage < threshold">
          {{ stats.percentage }}%
        </span>
      </div>
    </div>
  `, styles: ["/* angular:styles/component:css;79e05848ced6765e61dcf2d39b8d58a0d408f9540156bcd9833ee158712da6e6;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/student/stats/student-overall-card/student-overall-card.component.ts */\n.overall-card {\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-xl);\n  padding: var(--space-5);\n  display: flex;\n  gap: var(--space-6);\n  align-items: center;\n  margin-bottom: var(--space-5);\n}\n.overall-card__stat {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-1);\n}\n.overall-card__label {\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  text-transform: uppercase;\n  letter-spacing: 0.05em;\n  font-weight: 600;\n}\n.overall-card__value--mono {\n  font-size: var(--text-2xl);\n  font-family: var(--font-mono);\n  font-weight: 600;\n  font-variant-numeric: tabular-nums;\n}\n.overall-card__percentage {\n  font-size: var(--text-2xl);\n  font-family: var(--font-mono);\n  font-weight: 600;\n  font-variant-numeric: tabular-nums;\n}\n.overall-card__percentage.is-good {\n  color: var(--accent-primary);\n}\n.overall-card__percentage.is-warning {\n  color: var(--accent-warning);\n}\n/*# sourceMappingURL=student-overall-card.component.css.map */\n"] }]
  }], null, { stats: [{
    type: Input,
    args: [{ required: true }]
  }], threshold: [{
    type: Input,
    args: [{ required: true }]
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentOverallCardComponent, { className: "StudentOverallCardComponent", filePath: "src/app/features/student/stats/student-overall-card/student-overall-card.component.ts", lineNumber: 46 });
})();

// src/app/features/student/stats/student-stats.component.ts
var _forTrack0 = ($index, $item) => $item.subjectId;
function StudentStatsComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-progress-bar", 5);
  }
}
function StudentStatsComponent_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 6);
    \u0275\u0275element(1, "i", 7);
    \u0275\u0275elementStart(2, "span");
    \u0275\u0275text(3);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate(ctx_r0.error());
  }
}
function StudentStatsComponent_Conditional_10_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "app-student-overall-card", 8);
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275property("stats", ctx_r0.stats().overall)("threshold", ctx_r0.threshold());
  }
}
function StudentStatsComponent_Conditional_10_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 9);
    \u0275\u0275element(1, "i", 11);
    \u0275\u0275elementStart(2, "h2");
    \u0275\u0275text(3, "\u0414\u0430\u043D\u043D\u044B\u0445 \u043F\u043E\u043A\u0430 \u043D\u0435\u0442");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p");
    \u0275\u0275text(5, "\u0414\u0430\u043D\u043D\u044B\u0435 \u043E \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438 \u043F\u043E\u044F\u0432\u044F\u0442\u0441\u044F \u043F\u043E\u0441\u043B\u0435 \u043F\u0435\u0440\u0432\u044B\u0445 \u0437\u0430\u043D\u044F\u0442\u0438\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u0430.");
    \u0275\u0275elementEnd()();
  }
}
function StudentStatsComponent_Conditional_10_Conditional_2_For_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "app-student-subject-chart", 12);
  }
  if (rf & 2) {
    const subject_r2 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275property("stat", subject_r2)("threshold", ctx_r0.threshold());
  }
}
function StudentStatsComponent_Conditional_10_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 10);
    \u0275\u0275repeaterCreate(1, StudentStatsComponent_Conditional_10_Conditional_2_For_2_Template, 1, 2, "app-student-subject-chart", 12, _forTrack0);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275repeater(ctx_r0.stats().subjects);
  }
}
function StudentStatsComponent_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275template(0, StudentStatsComponent_Conditional_10_Conditional_0_Template, 1, 2, "app-student-overall-card", 8)(1, StudentStatsComponent_Conditional_10_Conditional_1_Template, 6, 0, "div", 9)(2, StudentStatsComponent_Conditional_10_Conditional_2_Template, 3, 0, "div", 10);
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275conditional(ctx_r0.stats().overall ? 0 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.stats().subjects.length === 0 ? 1 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.stats().subjects.length > 0 ? 2 : -1);
  }
}
var StudentStatsComponent = class _StudentStatsComponent {
  authService = inject(AuthService);
  apiService = inject(StudentApiService);
  loading = signal(false);
  error = signal(null);
  stats = signal(null);
  threshold = signal(75);
  ngOnInit() {
    const groupId = this.authService.currentUser()?.groupId;
    this.loading.set(true);
    const threshold$ = groupId != null ? this.apiService.resolveGroupThreshold(groupId) : this.apiService.resolveGlobalThreshold();
    forkJoin([this.apiService.getStudentStats(), threshold$]).subscribe({
      next: ([statsResp, thresholdResp]) => {
        this.stats.set(statsResp);
        this.threshold.set(thresholdResp.percentage);
        this.loading.set(false);
      },
      error: (err) => {
        console.error("[student-stats] failed to load", err);
        this.error.set(formatLoadError(err, "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0443."));
        this.loading.set(false);
      }
    });
  }
  static \u0275fac = function StudentStatsComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentStatsComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentStatsComponent, selectors: [["app-student-stats"]], decls: 11, vars: 4, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-header__eyebrow"], [1, "page-header__title"], [1, "page-header__subtitle"], ["mode", "indeterminate"], ["role", "alert", 1, "page-error"], [1, "ph-warning-circle", "ph-fill"], [3, "stats", "threshold"], [1, "page-empty"], [1, "stats-grid"], [1, "ph-chart-line", "ph-duotone"], [3, "stat", "threshold"]], template: function StudentStatsComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "header", 1)(2, "p", 2);
      \u0275\u0275text(3, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(4, "h1", 3);
      \u0275\u0275text(5, "\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(6, "p", 4);
      \u0275\u0275text(7, "\u041F\u0440\u043E\u0446\u0435\u043D\u0442 \u043F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u0438\u044F \u043F\u043E \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u0430\u043C \u0438 \u043E\u0431\u0449\u0438\u0439 \u043F\u043E\u043A\u0430\u0437\u0430\u0442\u0435\u043B\u044C.");
      \u0275\u0275elementEnd()();
      \u0275\u0275template(8, StudentStatsComponent_Conditional_8_Template, 1, 0, "mat-progress-bar", 5)(9, StudentStatsComponent_Conditional_9_Template, 4, 1, "div", 6)(10, StudentStatsComponent_Conditional_10_Template, 3, 3);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(8);
      \u0275\u0275conditional(ctx.loading() ? 8 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(!ctx.loading() && ctx.error() ? 9 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(!ctx.loading() && !ctx.error() && ctx.stats() ? 10 : -1);
    }
  }, dependencies: [CommonModule, MatProgressBarModule, MatProgressBar, StudentSubjectChartComponent, StudentOverallCardComponent], styles: ["\n\n.stats-grid[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: 1fr;\n  gap: var(--space-6, 24px);\n}\n@media (min-width: 1024px) {\n  .stats-grid[_ngcontent-%COMP%] {\n    grid-template-columns: repeat(auto-fill, minmax(480px, 1fr));\n  }\n}\n@media (prefers-reduced-motion: reduce) {\n}\n/*# sourceMappingURL=student-stats.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentStatsComponent, [{
    type: Component,
    args: [{ selector: "app-student-stats", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule, MatProgressBarModule, StudentSubjectChartComponent, StudentOverallCardComponent], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: '<div class="page-stack" [@routeFade]>\n  <header class="page-header">\n    <p class="page-header__eyebrow">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</p>\n    <h1 class="page-header__title">\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438</h1>\n    <p class="page-header__subtitle">\u041F\u0440\u043E\u0446\u0435\u043D\u0442 \u043F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u0438\u044F \u043F\u043E \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u0430\u043C \u0438 \u043E\u0431\u0449\u0438\u0439 \u043F\u043E\u043A\u0430\u0437\u0430\u0442\u0435\u043B\u044C.</p>\n  </header>\n\n  @if (loading()) {\n    <mat-progress-bar mode="indeterminate"></mat-progress-bar>\n  }\n\n  @if (!loading() && error()) {\n    <div class="page-error" role="alert">\n      <i class="ph-warning-circle ph-fill"></i>\n      <span>{{ error() }}</span>\n    </div>\n  }\n\n  @if (!loading() && !error() && stats()) {\n    <!-- Overall summary card -->\n    @if (stats()!.overall) {\n      <app-student-overall-card\n        [stats]="stats()!.overall"\n        [threshold]="threshold()">\n      </app-student-overall-card>\n    }\n\n    <!-- Empty state -->\n    @if (stats()!.subjects.length === 0) {\n      <div class="page-empty">\n        <i class="ph-chart-line ph-duotone"></i>\n        <h2>\u0414\u0430\u043D\u043D\u044B\u0445 \u043F\u043E\u043A\u0430 \u043D\u0435\u0442</h2>\n        <p>\u0414\u0430\u043D\u043D\u044B\u0435 \u043E \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438 \u043F\u043E\u044F\u0432\u044F\u0442\u0441\u044F \u043F\u043E\u0441\u043B\u0435 \u043F\u0435\u0440\u0432\u044B\u0445 \u0437\u0430\u043D\u044F\u0442\u0438\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u0430.</p>\n      </div>\n    }\n\n    <!-- Subject charts grid -->\n    @if (stats()!.subjects.length > 0) {\n      <div class="stats-grid">\n        @for (subject of stats()!.subjects; track subject.subjectId) {\n          <app-student-subject-chart\n            [stat]="subject"\n            [threshold]="threshold()">\n          </app-student-subject-chart>\n        }\n      </div>\n    }\n  }\n</div>\n', styles: ["/* src/app/features/student/stats/student-stats.component.css */\n.stats-grid {\n  display: grid;\n  grid-template-columns: 1fr;\n  gap: var(--space-6, 24px);\n}\n@media (min-width: 1024px) {\n  .stats-grid {\n    grid-template-columns: repeat(auto-fill, minmax(480px, 1fr));\n  }\n}\n@media (prefers-reduced-motion: reduce) {\n}\n/*# sourceMappingURL=student-stats.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentStatsComponent, { className: "StudentStatsComponent", filePath: "src/app/features/student/stats/student-stats.component.ts", lineNumber: 31 });
})();
export {
  StudentStatsComponent
};
//# sourceMappingURL=chunk-N4GLGDR3.js.map
