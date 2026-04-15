import {
  HeadmanApiService
} from "./chunk-ZC6IXG25.js";
import {
  MatSnackBar,
  MatSnackBarModule
} from "./chunk-5Q6RVIG4.js";
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
import "./chunk-OIBNGD5S.js";
import "./chunk-GTDHNJL7.js";
import "./chunk-2WN7CIGJ.js";
import "./chunk-ZEX6QU7O.js";
import "./chunk-G4JX6AWT.js";
import "./chunk-YIJEORIR.js";
import "./chunk-FW2NJ6PM.js";
import "./chunk-FLE4DLW4.js";
import "./chunk-CLRYRPPS.js";
import {
  ChangeDetectionStrategy,
  Component,
  catchError,
  finalize,
  forkJoin,
  inject,
  map,
  of,
  setClassMetadata,
  signal,
  switchMap,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵgetCurrentView,
  ɵɵlistener,
  ɵɵnextContext,
  ɵɵproperty,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵstyleProp,
  ɵɵsyntheticHostProperty,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-4M4FDLBS.js";

// src/app/features/headman/stats/headman-stats.component.ts
var _forTrack0 = ($index, $item) => $item.subjectId;
function HeadmanStatsComponent_Conditional_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 2)(1, "span", 4);
    \u0275\u0275text(2, "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430 \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0438...");
    \u0275\u0275elementEnd()();
  }
}
function HeadmanStatsComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 3);
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
function HeadmanStatsComponent_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 2)(1, "div", 6)(2, "div", 7);
    \u0275\u0275element(3, "i", 8);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p", 9);
    \u0275\u0275text(5, "\u041D\u0435\u0442 \u0434\u0430\u043D\u043D\u044B\u0445");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "p", 10);
    \u0275\u0275text(7, "\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430 \u043F\u043E\u044F\u0432\u0438\u0442\u0441\u044F \u043F\u043E\u0441\u043B\u0435 \u0434\u043E\u0431\u0430\u0432\u043B\u0435\u043D\u0438\u044F \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u043E\u0432 \u0438 \u043F\u0440\u043E\u0432\u0435\u0434\u0435\u043D\u0438\u044F \u0437\u0430\u043D\u044F\u0442\u0438\u0439.");
    \u0275\u0275elementEnd()()();
  }
}
function HeadmanStatsComponent_Conditional_8_For_12_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "tr")(1, "td");
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "td");
    \u0275\u0275text(4);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "td")(6, "input", 12);
    \u0275\u0275listener("blur", function HeadmanStatsComponent_Conditional_8_For_12_Template_input_blur_6_listener($event) {
      const row_r3 = \u0275\u0275restoreView(_r2).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onThresholdBlur(row_r3, $event));
    })("keydown.enter", function HeadmanStatsComponent_Conditional_8_For_12_Template_input_keydown_enter_6_listener($event) {
      const row_r3 = \u0275\u0275restoreView(_r2).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onThresholdEnter(row_r3, $event));
    })("keydown.escape", function HeadmanStatsComponent_Conditional_8_For_12_Template_input_keydown_escape_6_listener($event) {
      const row_r3 = \u0275\u0275restoreView(_r2).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onThresholdEscape(row_r3, $event));
    });
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const row_r3 = ctx.$implicit;
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(row_r3.subjectName);
    \u0275\u0275advance();
    \u0275\u0275styleProp("color", row_r3.isRedZone ? "var(--accent-danger)" : "var(--status-present)")("font-family", "var(--font-mono)")("font-size", "var(--text-sm)");
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", row_r3.groupAveragePercent, "% ");
    \u0275\u0275advance(2);
    \u0275\u0275property("value", row_r3.threshold);
    \u0275\u0275attribute("aria-label", "\u041F\u043E\u0440\u043E\u0433 \u0434\u043B\u044F \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u0430 " + row_r3.subjectName + ", \u043F\u0440\u043E\u0446\u0435\u043D\u0442");
  }
}
function HeadmanStatsComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 2)(1, "table", 11)(2, "thead")(3, "tr")(4, "th");
    \u0275\u0275text(5, "\u041F\u0440\u0435\u0434\u043C\u0435\u0442");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "th");
    \u0275\u0275text(7, "\u041F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u044B");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(8, "th");
    \u0275\u0275text(9, "\u041F\u043E\u0440\u043E\u0433 (%)");
    \u0275\u0275elementEnd()()();
    \u0275\u0275elementStart(10, "tbody");
    \u0275\u0275repeaterCreate(11, HeadmanStatsComponent_Conditional_8_For_12_Template, 7, 10, "tr", null, _forTrack0);
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(11);
    \u0275\u0275repeater(ctx_r0.statsRows());
  }
}
function computeAttendanceRate(journal) {
  const allCells = [];
  (journal?.students ?? []).forEach((row) => (row.records ?? []).forEach((cell) => {
    if (cell.status !== "cancelled")
      allCells.push(cell);
  }));
  if (allCells.length === 0)
    return 0;
  const attended = allCells.filter((c) => c.status === "present" || c.status === "excused" || c.status === "free_attendance").length;
  return Math.round(attended / allCells.length * 100);
}
var HeadmanStatsComponent = class _HeadmanStatsComponent {
  headmanApi = inject(HeadmanApiService);
  authService = inject(AuthService);
  snackBar = inject(MatSnackBar);
  statsRows = signal([]);
  loading = signal(true);
  error = signal(null);
  ngOnInit() {
    const groupId = this.authService.currentUser()?.groupId;
    if (!groupId) {
      this.error.set("\u041E\u0448\u0438\u0431\u043A\u0430: \u043D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u043F\u0440\u0435\u0434\u0435\u043B\u0438\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443.");
      this.loading.set(false);
      return;
    }
    const today = (/* @__PURE__ */ new Date()).toISOString().split("T")[0];
    const ninetyDaysAgo = new Date(Date.now() - 90 * 24 * 60 * 60 * 1e3).toISOString().split("T")[0];
    this.headmanApi.listSubjects(0, 100).pipe(catchError(() => of(null)), switchMap((subjectsPage) => {
      if (!subjectsPage)
        return of([]);
      const embedded = subjectsPage._embedded ?? {};
      const subjects = embedded[Object.keys(embedded)[0]] ?? [];
      if (subjects.length === 0)
        return of([]);
      const requests = subjects.map((s) => forkJoin({
        journal: this.headmanApi.getJournal(groupId, s.id, ninetyDaysAgo, today).pipe(catchError(() => of(null))),
        threshold: this.headmanApi.resolveThreshold(groupId, s.id).pipe(catchError(() => of({ minPercentage: 75 })))
      }).pipe(map(({ journal, threshold }) => {
        const rate = journal ? computeAttendanceRate(journal) : 0;
        const thresh = threshold?.minPercentage ?? 75;
        return {
          subjectId: s.id,
          subjectName: s.name,
          groupAveragePercent: rate,
          threshold: thresh,
          isRedZone: rate < thresh
        };
      })));
      return forkJoin(requests);
    }), catchError(() => {
      this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0434\u0430\u043D\u043D\u044B\u0435. \u041E\u0431\u043D\u043E\u0432\u0438\u0442\u0435 \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0443 \u0438\u043B\u0438 \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043F\u043E\u0437\u0436\u0435.");
      return of([]);
    }), finalize(() => this.loading.set(false))).subscribe((rows) => this.statsRows.set(rows));
  }
  onThresholdBlur(row, event) {
    const input = event.target;
    const value = parseInt(input.value, 10);
    if (isNaN(value) || value < 0 || value > 100) {
      input.value = String(row.threshold);
      return;
    }
    if (value === row.threshold)
      return;
    this.saveThreshold(row, value, input);
  }
  onThresholdEnter(row, event) {
    event.target.blur();
  }
  onThresholdEscape(row, event) {
    const input = event.target;
    input.value = String(row.threshold);
    input.blur();
  }
  saveThreshold(row, newValue, input) {
    const prevValue = row.threshold;
    row.threshold = newValue;
    row.isRedZone = row.groupAveragePercent < newValue;
    this.statsRows.set([...this.statsRows()]);
    this.headmanApi.setSubjectThreshold(row.subjectId, newValue).pipe(catchError(() => {
      row.threshold = prevValue;
      row.isRedZone = row.groupAveragePercent < prevValue;
      input.value = String(prevValue);
      this.statsRows.set([...this.statsRows()]);
      this.snackBar.open("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0441\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u043F\u043E\u0440\u043E\u0433. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0441\u043D\u043E\u0432\u0430.", void 0, { duration: 4e3 });
      return of(null);
    })).subscribe();
  }
  static \u0275fac = function HeadmanStatsComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanStatsComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanStatsComponent, selectors: [["app-headman-stats"]], hostVars: 1, hostBindings: function HeadmanStatsComponent_HostBindings(rf, ctx) {
    if (rf & 2) {
      \u0275\u0275syntheticHostProperty("@routeFade", void 0);
    }
  }, decls: 9, vars: 1, consts: [[1, "page-header"], [1, "page-header__eyebrow"], [1, "page-card"], ["role", "alert", 1, "page-error"], ["aria-live", "polite", 1, "sr-only"], [1, "ph", "ph-warning-circle"], ["role", "status", "aria-live", "polite", 1, "page-empty"], [1, "page-empty__icon"], [1, "ph", "ph-chart-bar"], [1, "page-empty__title"], [1, "page-empty__text"], [1, "stats-table"], ["type", "number", "min", "0", "max", "100", 2, "min-width", "64px", "font-family", "var(--font-mono)", "font-size", "var(--text-sm)", 3, "blur", "keydown.enter", "keydown.escape", "value"]], template: function HeadmanStatsComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "h1");
      \u0275\u0275text(2, "\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430 \u0433\u0440\u0443\u043F\u043F\u044B");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(3, "span", 1);
      \u0275\u0275text(4, "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430");
      \u0275\u0275elementEnd()();
      \u0275\u0275template(5, HeadmanStatsComponent_Conditional_5_Template, 3, 0, "div", 2)(6, HeadmanStatsComponent_Conditional_6_Template, 3, 1, "div", 3)(7, HeadmanStatsComponent_Conditional_7_Template, 8, 0, "div", 2)(8, HeadmanStatsComponent_Conditional_8_Template, 13, 0, "div", 2);
    }
    if (rf & 2) {
      \u0275\u0275advance(5);
      \u0275\u0275conditional(ctx.loading() ? 5 : ctx.error() ? 6 : ctx.statsRows().length === 0 ? 7 : 8);
    }
  }, dependencies: [MatSnackBarModule], encapsulation: 2, data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanStatsComponent, [{
    type: Component,
    args: [{
      selector: "app-headman-stats",
      standalone: true,
      changeDetection: ChangeDetectionStrategy.OnPush,
      imports: [MatSnackBarModule],
      animations: [
        trigger("routeFade", [
          transition(":enter", [
            style({ opacity: 0, transform: "translateY(8px)" }),
            animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
          ])
        ])
      ],
      host: { "[@routeFade]": "" },
      template: `
    <div class="page-header">
      <h1>\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430 \u0433\u0440\u0443\u043F\u043F\u044B</h1>
      <span class="page-header__eyebrow">\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430</span>
    </div>

    @if (loading()) {
      <div class="page-card">
        <span aria-live="polite" class="sr-only">\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430 \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0438...</span>
      </div>
    } @else if (error()) {
      <div class="page-error" role="alert">
        <i class="ph ph-warning-circle"></i>
        {{ error() }}
      </div>
    } @else if (statsRows().length === 0) {
      <div class="page-card">
        <div class="page-empty" role="status" aria-live="polite">
          <div class="page-empty__icon"><i class="ph ph-chart-bar"></i></div>
          <p class="page-empty__title">\u041D\u0435\u0442 \u0434\u0430\u043D\u043D\u044B\u0445</p>
          <p class="page-empty__text">\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430 \u043F\u043E\u044F\u0432\u0438\u0442\u0441\u044F \u043F\u043E\u0441\u043B\u0435 \u0434\u043E\u0431\u0430\u0432\u043B\u0435\u043D\u0438\u044F \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u043E\u0432 \u0438 \u043F\u0440\u043E\u0432\u0435\u0434\u0435\u043D\u0438\u044F \u0437\u0430\u043D\u044F\u0442\u0438\u0439.</p>
        </div>
      </div>
    } @else {
      <div class="page-card">
        <table class="stats-table">
          <thead>
            <tr>
              <th>\u041F\u0440\u0435\u0434\u043C\u0435\u0442</th>
              <th>\u041F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u044B</th>
              <th>\u041F\u043E\u0440\u043E\u0433 (%)</th>
            </tr>
          </thead>
          <tbody>
            @for (row of statsRows(); track row.subjectId) {
              <tr>
                <td>{{ row.subjectName }}</td>
                <td
                  [style.color]="row.isRedZone ? 'var(--accent-danger)' : 'var(--status-present)'"
                  [style.font-family]="'var(--font-mono)'"
                  [style.font-size]="'var(--text-sm)'">
                  {{ row.groupAveragePercent }}%
                </td>
                <td>
                  <input
                    type="number"
                    [value]="row.threshold"
                    min="0" max="100"
                    [attr.aria-label]="'\u041F\u043E\u0440\u043E\u0433 \u0434\u043B\u044F \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u0430 ' + row.subjectName + ', \u043F\u0440\u043E\u0446\u0435\u043D\u0442'"
                    style="min-width: 64px; font-family: var(--font-mono); font-size: var(--text-sm);"
                    (blur)="onThresholdBlur(row, $event)"
                    (keydown.enter)="onThresholdEnter(row, $event)"
                    (keydown.escape)="onThresholdEscape(row, $event)"
                  />
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `
    }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanStatsComponent, { className: "HeadmanStatsComponent", filePath: "src/app/features/headman/stats/headman-stats.component.ts", lineNumber: 115 });
})();
export {
  HeadmanStatsComponent
};
//# sourceMappingURL=chunk-QQXAGGTM.js.map
