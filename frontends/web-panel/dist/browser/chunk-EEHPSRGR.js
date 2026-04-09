import {
  BaseChartDirective
} from "./chunk-6X4HSO6O.js";
import "./chunk-3KOO4W2I.js";
import {
  MatCard,
  MatCardContent,
  MatCardHeader,
  MatCardModule,
  MatCardTitle
} from "./chunk-GPYV2N2Y.js";
import {
  JournalApiService
} from "./chunk-KZBYJFJR.js";
import {
  MatSelect,
  MatSelectModule
} from "./chunk-C3QW7W42.js";
import "./chunk-GDZ3HOTB.js";
import {
  MatProgressBar,
  MatProgressBarModule
} from "./chunk-WFRMJ3YJ.js";
import {
  MatOption
} from "./chunk-4GAYHM76.js";
import {
  MatButtonModule,
  MatFormField,
  MatLabel
} from "./chunk-SDENFCBC.js";
import {
  ReactiveFormsModule
} from "./chunk-52TXXDGC.js";
import "./chunk-QNELF7JG.js";
import "./chunk-G7PFIUH5.js";
import "./chunk-NITJSJ4E.js";
import "./chunk-T4R75CF5.js";
import {
  CommonModule
} from "./chunk-L2FQVI4C.js";
import {
  Component,
  Input,
  __spreadValues,
  catchError,
  computed,
  forkJoin,
  inject,
  of,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵNgOnChangesFeature,
  ɵɵadvance,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵlistener,
  ɵɵnextContext,
  ɵɵproperty,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-WGFJ2PWY.js";

// src/app/features/teacher/stats/stats-utils.ts
function deriveStudentChartData(journal) {
  return journal.students.map((student) => {
    const counts = { present: 0, excused: 0, freeAttendance: 0, absent: 0 };
    student.records.forEach((cell) => {
      if (cell.status === "present")
        counts.present++;
      else if (cell.status === "excused")
        counts.excused++;
      else if (cell.status === "free_attendance")
        counts.freeAttendance++;
      else if (cell.status === "absent")
        counts.absent++;
    });
    const name = student.displayName.length > 16 ? student.displayName.slice(0, 16) + "..." : student.displayName;
    return __spreadValues({ name }, counts);
  });
}
function deriveOverallStats(journal) {
  if (journal.students.length === 0) {
    return { totalLessons: 0, attendanceRate: 0 };
  }
  const lessonSet = /* @__PURE__ */ new Set();
  let totalPresent = 0;
  let totalRecords = 0;
  journal.students.forEach((student) => {
    student.records.forEach((cell) => {
      if (cell.status !== "cancelled") {
        lessonSet.add(`${cell.date}_${cell.lessonNumber}`);
        totalRecords++;
        if (cell.status === "present")
          totalPresent++;
      }
    });
  });
  return {
    totalLessons: lessonSet.size,
    attendanceRate: totalRecords > 0 ? Math.round(totalPresent / totalRecords * 100) : 0
  };
}

// src/app/features/teacher/stats/subject-chart/subject-chart.component.ts
var SubjectChartComponent = class _SubjectChartComponent {
  chartData;
  subjectName;
  barChartData = { labels: [], datasets: [] };
  barChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: "top" }
    },
    scales: {
      x: { stacked: true },
      y: { stacked: true, title: { display: true, text: "\u0417\u0430\u043D\u044F\u0442\u0438\u0439" } }
    }
  };
  ngOnChanges() {
    this.barChartData = {
      labels: this.chartData.map((s) => s.name),
      datasets: [
        {
          label: "\u041F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B",
          data: this.chartData.map((s) => s.present),
          backgroundColor: "rgba(22, 163, 74, 0.85)"
        },
        {
          label: "\u0423\u0432\u0430\u0436. \u043F\u0440\u0438\u0447\u0438\u043D\u0430",
          data: this.chartData.map((s) => s.excused),
          backgroundColor: "rgba(217, 119, 6, 0.85)"
        },
        {
          label: "\u0421\u0432\u043E\u0431. \u043F\u043E\u0441\u0435\u0449\u0435\u043D\u0438\u0435",
          data: this.chartData.map((s) => s.freeAttendance),
          backgroundColor: "rgba(147, 51, 234, 0.85)"
        },
        {
          label: "\u041E\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B",
          data: this.chartData.map((s) => s.absent),
          backgroundColor: "rgba(220, 38, 38, 0.85)"
        }
      ]
    };
  }
  static \u0275fac = function SubjectChartComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _SubjectChartComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _SubjectChartComponent, selectors: [["app-subject-chart"]], inputs: { chartData: "chartData", subjectName: "subjectName" }, features: [\u0275\u0275NgOnChangesFeature], decls: 7, vars: 4, consts: [[1, "chart-card"], [1, "text-[20px]", "font-semibold"], [1, "chart-container"], ["baseChart", "", 3, "data", "options", "type"]], template: function SubjectChartComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "mat-card", 0)(1, "mat-card-header")(2, "mat-card-title", 1);
      \u0275\u0275text(3);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(4, "mat-card-content")(5, "div", 2);
      \u0275\u0275element(6, "canvas", 3);
      \u0275\u0275elementEnd()()();
    }
    if (rf & 2) {
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.subjectName);
      \u0275\u0275advance(3);
      \u0275\u0275property("data", ctx.barChartData)("options", ctx.barChartOptions)("type", "bar");
    }
  }, dependencies: [CommonModule, MatCardModule, MatCard, MatCardContent, MatCardHeader, MatCardTitle, BaseChartDirective], styles: ["\n\n.chart-card[_ngcontent-%COMP%] {\n  height: 320px;\n}\n.chart-container[_ngcontent-%COMP%] {\n  height: 240px;\n  position: relative;\n}\n/*# sourceMappingURL=subject-chart.component.css.map */"] });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(SubjectChartComponent, [{
    type: Component,
    args: [{ selector: "app-subject-chart", standalone: true, imports: [CommonModule, MatCardModule, BaseChartDirective], template: `
    <mat-card class="chart-card">
      <mat-card-header>
        <mat-card-title class="text-[20px] font-semibold">{{ subjectName }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <div class="chart-container">
          <canvas baseChart
            [data]="barChartData"
            [options]="barChartOptions"
            [type]="'bar'">
          </canvas>
        </div>
      </mat-card-content>
    </mat-card>
  `, styles: ["/* angular:styles/component:css;ec68d14867dcd040a2051198946d02a8b9daadcf687468410d3a1c8870b5de9e;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/teacher/stats/subject-chart/subject-chart.component.ts */\n.chart-card {\n  height: 320px;\n}\n.chart-container {\n  height: 240px;\n  position: relative;\n}\n/*# sourceMappingURL=subject-chart.component.css.map */\n"] }]
  }], null, { chartData: [{
    type: Input,
    args: [{ required: true }]
  }], subjectName: [{
    type: Input,
    args: [{ required: true }]
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(SubjectChartComponent, { className: "SubjectChartComponent", filePath: "src/app/features/teacher/stats/subject-chart/subject-chart.component.ts", lineNumber: 33 });
})();

// src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.ts
var OverallStatCardComponent = class _OverallStatCardComponent {
  totalLessons;
  attendanceRate;
  static \u0275fac = function OverallStatCardComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _OverallStatCardComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _OverallStatCardComponent, selectors: [["app-overall-stat-card"]], inputs: { totalLessons: "totalLessons", attendanceRate: "attendanceRate" }, decls: 12, vars: 2, consts: [[1, "p-6"], [1, "flex", "gap-8"], [1, "text-sm", "text-[var(--mat-sys-on-surface)]/60"], [1, "text-2xl", "font-semibold", "text-[var(--mat-sys-on-surface)]"]], template: function OverallStatCardComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "mat-card", 0)(1, "div", 1)(2, "div")(3, "p", 2);
      \u0275\u0275text(4, "\u0412\u0441\u0435\u0433\u043E \u0437\u0430\u043D\u044F\u0442\u0438\u0439");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "p", 3);
      \u0275\u0275text(6);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(7, "div")(8, "p", 2);
      \u0275\u0275text(9, "\u041F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(10, "p", 3);
      \u0275\u0275text(11);
      \u0275\u0275elementEnd()()()();
    }
    if (rf & 2) {
      \u0275\u0275advance(6);
      \u0275\u0275textInterpolate(ctx.totalLessons);
      \u0275\u0275advance(5);
      \u0275\u0275textInterpolate1("", ctx.attendanceRate, "%");
    }
  }, dependencies: [CommonModule, MatCardModule, MatCard], encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(OverallStatCardComponent, [{
    type: Component,
    args: [{
      selector: "app-overall-stat-card",
      standalone: true,
      imports: [CommonModule, MatCardModule],
      template: `
    <mat-card class="p-6">
      <div class="flex gap-8">
        <div>
          <p class="text-sm text-[var(--mat-sys-on-surface)]/60">\u0412\u0441\u0435\u0433\u043E \u0437\u0430\u043D\u044F\u0442\u0438\u0439</p>
          <p class="text-2xl font-semibold text-[var(--mat-sys-on-surface)]">{{ totalLessons }}</p>
        </div>
        <div>
          <p class="text-sm text-[var(--mat-sys-on-surface)]/60">\u041F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C</p>
          <p class="text-2xl font-semibold text-[var(--mat-sys-on-surface)]">{{ attendanceRate }}%</p>
        </div>
      </div>
    </mat-card>
  `
    }]
  }], null, { totalLessons: [{
    type: Input,
    args: [{ required: true }]
  }], attendanceRate: [{
    type: Input,
    args: [{ required: true }]
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(OverallStatCardComponent, { className: "OverallStatCardComponent", filePath: "src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.ts", lineNumber: 24 });
})();

// src/app/features/teacher/stats/stats-page.component.ts
var _forTrack0 = ($index, $item) => $item.id;
var _forTrack1 = ($index, $item) => $item.name;
function StatsPageComponent_For_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-option", 8);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const g_r1 = ctx.$implicit;
    \u0275\u0275property("value", g_r1.id);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(g_r1.name);
  }
}
function StatsPageComponent_Conditional_15_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-progress-bar", 9);
  }
}
function StatsPageComponent_Conditional_16_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 10);
    \u0275\u0275element(1, "i", 11);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r1.error(), " ");
  }
}
function StatsPageComponent_Conditional_17_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 4)(1, "div", 12)(2, "div", 13);
    \u0275\u0275element(3, "i", 14);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "h3", 15);
    \u0275\u0275text(5, "\u041D\u0435\u0442 \u0434\u0430\u043D\u043D\u044B\u0445");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "p", 16);
    \u0275\u0275text(7, " \u0414\u043B\u044F \u0432\u044B\u0431\u0440\u0430\u043D\u043D\u043E\u0439 \u0433\u0440\u0443\u043F\u043F\u044B \u0435\u0449\u0451 \u043D\u0435\u0442 \u0434\u0430\u043D\u043D\u044B\u0445 \u043E \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438. ");
    \u0275\u0275elementEnd()()();
  }
}
function StatsPageComponent_Conditional_18_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 4)(1, "div", 12)(2, "div", 13);
    \u0275\u0275element(3, "i", 17);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "h3", 15);
    \u0275\u0275text(5, "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0433\u0440\u0443\u043F\u043F\u0443");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "p", 16);
    \u0275\u0275text(7, " \u0427\u0442\u043E\u0431\u044B \u0443\u0432\u0438\u0434\u0435\u0442\u044C \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0443, \u0432\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0433\u0440\u0443\u043F\u043F\u0443 \u0432 \u0444\u0438\u043B\u044C\u0442\u0440\u0435 \u0432\u044B\u0448\u0435. ");
    \u0275\u0275elementEnd()()();
  }
}
function StatsPageComponent_Conditional_19_For_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "app-subject-chart", 20);
  }
  if (rf & 2) {
    const entry_r3 = ctx.$implicit;
    \u0275\u0275property("subjectName", entry_r3.name)("chartData", entry_r3.data);
  }
}
function StatsPageComponent_Conditional_19_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "app-overall-stat-card", 18);
    \u0275\u0275elementStart(1, "div", 19);
    \u0275\u0275repeaterCreate(2, StatsPageComponent_Conditional_19_For_3_Template, 1, 2, "app-subject-chart", 20, _forTrack1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275property("totalLessons", ctx_r1.overallStats().totalLessons)("attendanceRate", ctx_r1.overallStats().attendanceRate);
    \u0275\u0275advance(2);
    \u0275\u0275repeater(ctx_r1.chartEntries());
  }
}
var StatsPageComponent = class _StatsPageComponent {
  journalApi = inject(JournalApiService);
  groups = signal([]);
  subjects = signal([]);
  assignments = signal([]);
  selectedGroupId = signal(null);
  loading = signal(false);
  error = signal(null);
  chartDataMap = signal(/* @__PURE__ */ new Map());
  overallStats = signal({ totalLessons: 0, attendanceRate: 0 });
  chartEntries = computed(() => {
    const entries = [];
    this.chartDataMap().forEach((data, name) => entries.push({ name, data }));
    return entries;
  });
  ngOnInit() {
    this.journalApi.getMyAssignments().subscribe({
      next: (assignments) => {
        this.assignments.set(assignments);
        const groupIds = new Set(assignments.map((a) => a.groupId));
        this.journalApi.getGroups().subscribe({
          next: (groups) => {
            this.groups.set(groups.filter((g) => groupIds.has(g.id)));
          }
        });
      }
    });
  }
  onGroupChange(groupId) {
    this.selectedGroupId.set(groupId);
    this.chartDataMap.set(/* @__PURE__ */ new Map());
    this.overallStats.set({ totalLessons: 0, attendanceRate: 0 });
    this.error.set(null);
    const subjectIds = new Set(this.assignments().filter((a) => a.groupId === groupId).map((a) => a.subjectId));
    this.journalApi.getSubjects().subscribe({
      next: (subjects) => {
        const filtered = subjects.filter((s) => subjectIds.has(s.id));
        this.subjects.set(filtered);
        if (filtered.length === 0) {
          return;
        }
        this.loading.set(true);
        const today = (/* @__PURE__ */ new Date()).toISOString().slice(0, 10);
        const semesterStart = `${(/* @__PURE__ */ new Date()).getFullYear()}-01-01`;
        const requests = filtered.map((subject) => this.journalApi.getJournal(groupId, subject.id, semesterStart, today).pipe(catchError(() => of(null))));
        forkJoin(requests).subscribe({
          next: (responses) => {
            const newMap = /* @__PURE__ */ new Map();
            responses.forEach((response, index) => {
              if (response) {
                const subjectName = filtered[index].name;
                newMap.set(subjectName, deriveStudentChartData(response));
              }
            });
            this.chartDataMap.set(newMap);
            const allRecords = [];
            newMap.forEach((data) => allRecords.push(...data));
            const validResponses = responses.filter((r) => r !== null);
            if (validResponses.length > 0) {
              let totalLessons = 0;
              let totalPresent = 0;
              let totalRecords = 0;
              validResponses.forEach((r) => {
                const stats = deriveOverallStats(r);
                totalLessons += stats.totalLessons;
                r.students.forEach((s) => {
                  s.records.forEach((cell) => {
                    if (cell.status !== "cancelled") {
                      totalRecords++;
                      if (cell.status === "present")
                        totalPresent++;
                    }
                  });
                });
              });
              this.overallStats.set({
                totalLessons,
                attendanceRate: totalRecords > 0 ? Math.round(totalPresent / totalRecords * 100) : 0
              });
            }
            this.loading.set(false);
          },
          error: () => {
            this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0443. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043F\u043E\u0437\u0436\u0435.");
            this.loading.set(false);
          }
        });
      }
    });
  }
  static \u0275fac = function StatsPageComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StatsPageComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StatsPageComponent, selectors: [["app-stats-page"]], decls: 20, vars: 5, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-header__title"], [1, "page-header__subtitle"], [1, "page-card"], [1, "page-filters"], [1, "w-48"], [3, "selectionChange"], [3, "value"], ["mode", "indeterminate"], ["role", "alert", 1, "page-error"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"], [1, "page-empty"], ["aria-hidden", "true", 1, "page-empty__icon"], [1, "ph-duotone", "ph-chart-line"], [1, "page-empty__title"], [1, "page-empty__text"], [1, "ph-duotone", "ph-funnel"], [1, "block", 3, "totalLessons", "attendanceRate"], [1, "grid", "grid-cols-1", "lg:grid-cols-[repeat(auto-fill,minmax(480px,1fr))]", "gap-6"], [3, "subjectName", "chartData"]], template: function StatsPageComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "header", 1)(2, "div", 2)(3, "h1");
      \u0275\u0275text(4, "\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "p", 3);
      \u0275\u0275text(6, " \u041F\u0440\u043E\u0446\u0435\u043D\u0442 \u043F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u0438\u044F \u043F\u043E \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u0430\u043C \u0438 \u043E\u0431\u0449\u0438\u0439 \u043F\u043E\u043A\u0430\u0437\u0430\u0442\u0435\u043B\u044C \u0433\u0440\u0443\u043F\u043F\u044B. ");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(7, "div", 4)(8, "div", 5)(9, "mat-form-field", 6)(10, "mat-label");
      \u0275\u0275text(11, "\u0413\u0440\u0443\u043F\u043F\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(12, "mat-select", 7);
      \u0275\u0275listener("selectionChange", function StatsPageComponent_Template_mat_select_selectionChange_12_listener($event) {
        return ctx.onGroupChange($event.value);
      });
      \u0275\u0275repeaterCreate(13, StatsPageComponent_For_14_Template, 2, 2, "mat-option", 8, _forTrack0);
      \u0275\u0275elementEnd()()()();
      \u0275\u0275template(15, StatsPageComponent_Conditional_15_Template, 1, 0, "mat-progress-bar", 9)(16, StatsPageComponent_Conditional_16_Template, 3, 1, "div", 10)(17, StatsPageComponent_Conditional_17_Template, 8, 0, "div", 4)(18, StatsPageComponent_Conditional_18_Template, 8, 0, "div", 4)(19, StatsPageComponent_Conditional_19_Template, 4, 2);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275advance(13);
      \u0275\u0275repeater(ctx.groups());
      \u0275\u0275advance(2);
      \u0275\u0275conditional(ctx.loading() ? 15 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.error() ? 16 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(!ctx.loading() && !ctx.error() && ctx.chartDataMap().size === 0 && ctx.selectedGroupId() ? 17 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(!ctx.selectedGroupId() && !ctx.loading() && !ctx.error() ? 18 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.chartDataMap().size > 0 ? 19 : -1);
    }
  }, dependencies: [
    CommonModule,
    ReactiveFormsModule,
    MatSelectModule,
    MatFormField,
    MatLabel,
    MatSelect,
    MatOption,
    MatButtonModule,
    MatProgressBarModule,
    MatProgressBar,
    SubjectChartComponent,
    OverallStatCardComponent
  ], encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StatsPageComponent, [{
    type: Component,
    args: [{ selector: "app-stats-page", standalone: true, imports: [
      CommonModule,
      ReactiveFormsModule,
      MatSelectModule,
      MatButtonModule,
      MatProgressBarModule,
      SubjectChartComponent,
      OverallStatCardComponent
    ], template: '<div class="page-stack">\n  <header class="page-header">\n    <div class="page-header__title">\n      <h1>\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438</h1>\n      <p class="page-header__subtitle">\n        \u041F\u0440\u043E\u0446\u0435\u043D\u0442 \u043F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u0438\u044F \u043F\u043E \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u0430\u043C \u0438 \u043E\u0431\u0449\u0438\u0439 \u043F\u043E\u043A\u0430\u0437\u0430\u0442\u0435\u043B\u044C \u0433\u0440\u0443\u043F\u043F\u044B.\n      </p>\n    </div>\n  </header>\n\n  <!-- Filter row -->\n  <div class="page-card">\n    <div class="page-filters">\n      <mat-form-field class="w-48">\n        <mat-label>\u0413\u0440\u0443\u043F\u043F\u0430</mat-label>\n        <mat-select (selectionChange)="onGroupChange($event.value)">\n          @for (g of groups(); track g.id) {\n            <mat-option [value]="g.id">{{ g.name }}</mat-option>\n          }\n        </mat-select>\n      </mat-form-field>\n    </div>\n  </div>\n\n  @if (loading()) {\n    <mat-progress-bar mode="indeterminate" />\n  }\n\n  @if (error()) {\n    <div class="page-error" role="alert">\n      <i class="ph ph-warning-circle" aria-hidden="true"></i>\n      {{ error() }}\n    </div>\n  }\n\n  @if (!loading() && !error() && chartDataMap().size === 0 && selectedGroupId()) {\n    <div class="page-card">\n      <div class="page-empty">\n        <div class="page-empty__icon" aria-hidden="true">\n          <i class="ph-duotone ph-chart-line"></i>\n        </div>\n        <h3 class="page-empty__title">\u041D\u0435\u0442 \u0434\u0430\u043D\u043D\u044B\u0445</h3>\n        <p class="page-empty__text">\n          \u0414\u043B\u044F \u0432\u044B\u0431\u0440\u0430\u043D\u043D\u043E\u0439 \u0433\u0440\u0443\u043F\u043F\u044B \u0435\u0449\u0451 \u043D\u0435\u0442 \u0434\u0430\u043D\u043D\u044B\u0445 \u043E \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438.\n        </p>\n      </div>\n    </div>\n  }\n\n  @if (!selectedGroupId() && !loading() && !error()) {\n    <div class="page-card">\n      <div class="page-empty">\n        <div class="page-empty__icon" aria-hidden="true">\n          <i class="ph-duotone ph-funnel"></i>\n        </div>\n        <h3 class="page-empty__title">\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0433\u0440\u0443\u043F\u043F\u0443</h3>\n        <p class="page-empty__text">\n          \u0427\u0442\u043E\u0431\u044B \u0443\u0432\u0438\u0434\u0435\u0442\u044C \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0443, \u0432\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0433\u0440\u0443\u043F\u043F\u0443 \u0432 \u0444\u0438\u043B\u044C\u0442\u0440\u0435 \u0432\u044B\u0448\u0435.\n        </p>\n      </div>\n    </div>\n  }\n\n  @if (chartDataMap().size > 0) {\n    <app-overall-stat-card\n      [totalLessons]="overallStats().totalLessons"\n      [attendanceRate]="overallStats().attendanceRate"\n      class="block" />\n\n    <div class="grid grid-cols-1 lg:grid-cols-[repeat(auto-fill,minmax(480px,1fr))] gap-6">\n      @for (entry of chartEntries(); track entry.name) {\n        <app-subject-chart\n          [subjectName]="entry.name"\n          [chartData]="entry.data" />\n      }\n    </div>\n  }\n</div>\n' }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StatsPageComponent, { className: "StatsPageComponent", filePath: "src/app/features/teacher/stats/stats-page.component.ts", lineNumber: 30 });
})();
export {
  StatsPageComponent
};
//# sourceMappingURL=chunk-EEHPSRGR.js.map
