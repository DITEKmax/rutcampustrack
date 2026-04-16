import {
  HeadmanApiService
} from "./chunk-BU5FJGI6.js";
import "./chunk-F5IUR55I.js";
import {
  MatSnackBar
} from "./chunk-53YESMZZ.js";
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
import "./chunk-OIBNGD5S.js";
import {
  MatFormField,
  MatFormFieldModule,
  MatLabel,
  MatSelect,
  MatSelectModule
} from "./chunk-Z3VV4J2X.js";
import "./chunk-PAS4QIDL.js";
import {
  MatOption
} from "./chunk-4RJPSRCU.js";
import "./chunk-6JM2QIGP.js";
import "./chunk-75YPR2VK.js";
import {
  MatProgressBar,
  MatProgressBarModule
} from "./chunk-PWYB6KY4.js";
import "./chunk-2UA5NBNP.js";
import "./chunk-YIJEORIR.js";
import "./chunk-B3BDHDAM.js";
import {
  FormsModule,
  NgControlStatus,
  NgModel
} from "./chunk-XAMIXVT7.js";
import "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  Input,
  __spreadValues,
  catchError,
  computed,
  effect,
  finalize,
  inject,
  of,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵNgOnChangesFeature,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵclassMap,
  ɵɵclassMapInterpolate1,
  ɵɵclassProp,
  ɵɵconditional,
  ɵɵdeclareLet,
  ɵɵdefineComponent,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵgetCurrentView,
  ɵɵlistener,
  ɵɵnextContext,
  ɵɵproperty,
  ɵɵreadContextLet,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵrepeaterTrackByIdentity,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵstoreLet,
  ɵɵstyleProp,
  ɵɵsyntheticHostProperty,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-MFRIGFR2.js";

// src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts
var _forTrack0 = ($index, $item) => $item.id;
var _forTrack1 = ($index, $item) => $item.userId;
function HeadmanJournalGridComponent_For_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "th", 6)(1, "span", 7);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "span", 8);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const col_r1 = ctx.$implicit;
    \u0275\u0275classProp("today-column", col_r1.isToday);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(col_r1.weekday);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(col_r1.displayDate);
  }
}
function HeadmanJournalGridComponent_For_12_For_4_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 12);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const col_r2 = \u0275\u0275nextContext().$implicit;
    const cell_r3 = \u0275\u0275readContextLet(1);
    const row_r4 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275attribute("aria-label", "\u041E\u0442\u043C\u0435\u043D\u0435\u043D\u0430, " + row_r4.displayName + ", " + col_r2.displayDate);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", cell_r3.symbol, " ");
  }
}
function HeadmanJournalGridComponent_For_12_For_4_Conditional_3_For_2_Template(rf, ctx) {
  if (rf & 1) {
    const _r5 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 17);
    \u0275\u0275listener("click", function HeadmanJournalGridComponent_For_12_For_4_Conditional_3_For_2_Template_button_click_0_listener() {
      const s_r6 = \u0275\u0275restoreView(_r5).$implicit;
      \u0275\u0275nextContext(2);
      const cell_r3 = \u0275\u0275readContextLet(1);
      const row_r4 = \u0275\u0275nextContext().$implicit;
      const ctx_r6 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r6.setStatus(cell_r3, row_r4, s_r6));
    });
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const s_r6 = ctx.$implicit;
    const col_r2 = \u0275\u0275nextContext(2).$implicit;
    const cell_r3 = \u0275\u0275readContextLet(1);
    const row_r4 = \u0275\u0275nextContext().$implicit;
    const ctx_r6 = \u0275\u0275nextContext();
    \u0275\u0275classMapInterpolate1("dot dot--", s_r6, "");
    \u0275\u0275classProp("dot--active", cell_r3.status === s_r6);
    \u0275\u0275attribute("aria-label", ctx_r6.statusAriaLabel(s_r6) + ", " + row_r4.displayName + ", " + col_r2.displayDate)("aria-pressed", cell_r3.status === s_r6)("title", s_r6 === "excused" && cell_r3.excuseReason ? cell_r3.excuseReason : null);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r6.statusSymbol(s_r6));
  }
}
function HeadmanJournalGridComponent_For_12_For_4_Conditional_3_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 16);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    \u0275\u0275nextContext(2);
    const cell_r3 = \u0275\u0275readContextLet(1);
    \u0275\u0275attribute("title", cell_r3.excuseReason);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(cell_r3.excuseReason);
  }
}
function HeadmanJournalGridComponent_For_12_For_4_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 14);
    \u0275\u0275repeaterCreate(1, HeadmanJournalGridComponent_For_12_For_4_Conditional_3_For_2_Template, 2, 9, "button", 15, \u0275\u0275repeaterTrackByIdentity);
    \u0275\u0275elementEnd();
    \u0275\u0275template(3, HeadmanJournalGridComponent_For_12_For_4_Conditional_3_Conditional_3_Template, 2, 2, "span", 16);
  }
  if (rf & 2) {
    \u0275\u0275nextContext();
    const cell_r3 = \u0275\u0275readContextLet(1);
    const ctx_r6 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275repeater(ctx_r6.quickStatuses);
    \u0275\u0275advance(2);
    \u0275\u0275conditional((cell_r3.status === "excused" || cell_r3.status === "free_attendance") && cell_r3.excuseReason ? 3 : -1);
  }
}
function HeadmanJournalGridComponent_For_12_For_4_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 13);
    \u0275\u0275text(1, "\u2014");
    \u0275\u0275elementEnd();
  }
}
function HeadmanJournalGridComponent_For_12_For_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "td", 10);
    \u0275\u0275declareLet(1);
    \u0275\u0275template(2, HeadmanJournalGridComponent_For_12_For_4_Conditional_2_Template, 2, 2, "span", 12)(3, HeadmanJournalGridComponent_For_12_For_4_Conditional_3_Template, 4, 1)(4, HeadmanJournalGridComponent_For_12_For_4_Conditional_4_Template, 2, 0, "span", 13);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const col_r2 = ctx.$implicit;
    const row_r4 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275advance();
    const cell_r8 = \u0275\u0275storeLet(\u0275\u0275nextContext().getCell(row_r4.userId, col_r2.id));
    \u0275\u0275advance();
    \u0275\u0275conditional(cell_r8 && cell_r8.status === "cancelled" ? 2 : cell_r8 ? 3 : 4);
  }
}
function HeadmanJournalGridComponent_For_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "tr", 5)(1, "td", 9);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275repeaterCreate(3, HeadmanJournalGridComponent_For_12_For_4_Template, 5, 2, "td", 10, _forTrack0);
    \u0275\u0275elementStart(5, "td", 11);
    \u0275\u0275text(6);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const row_r4 = ctx.$implicit;
    const ctx_r6 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275property("title", row_r4.displayName);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(row_r4.displayName);
    \u0275\u0275advance();
    \u0275\u0275repeater(ctx_r6.columns());
    \u0275\u0275advance(2);
    \u0275\u0275classMap(ctx_r6.percentClass(ctx_r6.getPercent(row_r4.userId)));
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r6.getPercent(row_r4.userId), "% ");
  }
}
var STATUS_SYMBOLS = {
  present: "+",
  absent: "\u043D",
  excused: "\u0443",
  free_attendance: "\u0441\u043F",
  cancelled: "\u2014"
};
var WEEKDAY_LABELS = ["\u0412\u0421", "\u041F\u041D", "\u0412\u0422", "\u0421\u0420", "\u0427\u0422", "\u041F\u0422", "\u0421\u0411"];
var QUICK_STATUSES = ["present", "absent", "excused"];
var HeadmanJournalGridComponent = class _HeadmanJournalGridComponent {
  journalData;
  loading = false;
  headmanApi = inject(HeadmanApiService);
  snackBar = inject(MatSnackBar);
  today = (/* @__PURE__ */ new Date()).toISOString().slice(0, 10);
  quickStatuses = QUICK_STATUSES;
  cellMap = signal(/* @__PURE__ */ new Map());
  columns = computed(() => {
    void this.cellMap();
    if (!this.journalData)
      return [];
    return this.buildColumns(this.journalData);
  });
  dataSource = computed(() => {
    if (!this.journalData)
      return [];
    return [...this.journalData.students].sort((a, b) => a.displayName.localeCompare(b.displayName, "ru"));
  });
  ngOnChanges(changes) {
    if (changes["journalData"] && this.journalData) {
      this.cellMap.set(this.buildCellMap(this.journalData));
    }
  }
  statusSymbol(s) {
    return STATUS_SYMBOLS[s];
  }
  statusAriaLabel(s) {
    switch (s) {
      case "present":
        return "\u041F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B";
      case "absent":
        return "\u041D\u0435 \u0431\u044B\u043B";
      case "excused":
        return "\u0423\u0432\u0430\u0436\u0438\u0442\u0435\u043B\u044C\u043D\u0430\u044F \u043F\u0440\u0438\u0447\u0438\u043D\u0430";
      case "free_attendance":
        return "\u0421\u0432\u043E\u0431\u043E\u0434\u043D\u043E\u0435 \u043F\u043E\u0441\u0435\u0449\u0435\u043D\u0438\u0435";
      case "cancelled":
        return "\u041E\u0442\u043C\u0435\u043D\u0435\u043D\u0430";
    }
  }
  percentClass(p) {
    if (p >= 80)
      return "col-percent--good";
    if (p >= 50)
      return "col-percent--warn";
    return "col-percent--bad";
  }
  buildColumns(journal) {
    const colSet = /* @__PURE__ */ new Map();
    journal.students.forEach((row) => row.records.forEach((cell) => {
      if (cell.date > this.today)
        return;
      const id = `${cell.date}_lesson${cell.lessonNumber}`;
      if (!colSet.has(id)) {
        const parts = cell.date.split("-");
        const dayIdx = (/* @__PURE__ */ new Date(cell.date + "T00:00:00")).getDay();
        colSet.set(id, {
          id,
          date: cell.date,
          lessonNumber: cell.lessonNumber,
          displayDate: `${parts[2]}.${parts[1]}`,
          isToday: cell.date === this.today,
          weekday: WEEKDAY_LABELS[dayIdx]
        });
      }
    }));
    return Array.from(colSet.values()).sort((a, b) => a.date.localeCompare(b.date) || a.lessonNumber - b.lessonNumber);
  }
  buildCellMap(journal) {
    const map = /* @__PURE__ */ new Map();
    journal.students.forEach((row) => row.records.forEach((cell) => {
      const colId = `${cell.date}_lesson${cell.lessonNumber}`;
      map.set(`${row.userId}_${colId}`, __spreadValues({}, cell));
    }));
    return map;
  }
  getCell(userId, colId) {
    return this.cellMap().get(`${userId}_${colId}`);
  }
  getPercent(userId) {
    const cols = this.columns();
    let counted = 0;
    let attended = 0;
    for (const col of cols) {
      const cell = this.cellMap().get(`${userId}_${col.id}`);
      if (!cell || cell.status === "cancelled")
        continue;
      counted++;
      if (cell.status === "present" || cell.status === "excused" || cell.status === "free_attendance") {
        attended++;
      }
    }
    if (counted === 0)
      return 0;
    return Math.round(attended / counted * 100);
  }
  setStatus(cell, row, next) {
    if (!cell.lessonId || cell.status === next)
      return;
    const prev = cell.status;
    cell.status = next;
    cell.symbol = STATUS_SYMBOLS[next];
    this.cellMap.set(new Map(this.cellMap()));
    this.headmanApi.markAttendance(cell.lessonId, row.userId, next).pipe(catchError(() => {
      cell.status = prev;
      cell.symbol = STATUS_SYMBOLS[prev];
      this.cellMap.set(new Map(this.cellMap()));
      this.snackBar.open("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0438\u0437\u043C\u0435\u043D\u0438\u0442\u044C \u0441\u0442\u0430\u0442\u0443\u0441. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0441\u043D\u043E\u0432\u0430.", void 0, {
        duration: 4e3
      });
      return of(null);
    })).subscribe();
  }
  static \u0275fac = function HeadmanJournalGridComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanJournalGridComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanJournalGridComponent, selectors: [["app-headman-journal-grid"]], inputs: { journalData: "journalData", loading: "loading" }, features: [\u0275\u0275NgOnChangesFeature], decls: 13, vars: 3, consts: [[1, "grid-wrapper"], [1, "journal-table"], [1, "col-student", "col-student--head"], [1, "col-date", 3, "today-column"], [1, "col-percent", "col-percent--head"], [1, "row-student"], [1, "col-date"], [1, "col-date__weekday"], [1, "col-date__date"], [1, "col-student", 3, "title"], [1, "cell"], [1, "col-percent"], [1, "cell-cancelled"], ["aria-label", "\u041D\u0435\u0442 \u0437\u0430\u043D\u044F\u0442\u0438\u044F", 1, "cell-cancelled"], [1, "cell-buttons"], ["type", "button", 3, "class", "dot--active"], [1, "excuse-reason"], ["type", "button", 3, "click"]], template: function HeadmanJournalGridComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "table", 1)(2, "thead")(3, "tr")(4, "th", 2);
      \u0275\u0275text(5, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275repeaterCreate(6, HeadmanJournalGridComponent_For_7_Template, 5, 4, "th", 3, _forTrack0);
      \u0275\u0275elementStart(8, "th", 4);
      \u0275\u0275text(9, "%");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(10, "tbody");
      \u0275\u0275repeaterCreate(11, HeadmanJournalGridComponent_For_12_Template, 7, 5, "tr", 5, _forTrack1);
      \u0275\u0275elementEnd()()();
    }
    if (rf & 2) {
      \u0275\u0275styleProp("pointer-events", ctx.loading ? "none" : "");
      \u0275\u0275attribute("aria-busy", ctx.loading);
      \u0275\u0275advance(6);
      \u0275\u0275repeater(ctx.columns());
      \u0275\u0275advance(5);
      \u0275\u0275repeater(ctx.dataSource());
    }
  }, styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.grid-wrapper[_ngcontent-%COMP%] {\n  overflow-x: auto;\n  overflow-y: visible;\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-xl);\n  background: var(--bg-secondary);\n  position: relative;\n}\n.journal-table[_ngcontent-%COMP%] {\n  border-collapse: separate;\n  border-spacing: 0;\n  width: max-content;\n  min-width: 100%;\n  font-size: 13px;\n}\n.col-student[_ngcontent-%COMP%] {\n  position: sticky;\n  left: 0;\n  z-index: 3;\n  width: 220px;\n  min-width: 220px;\n  max-width: 220px;\n  padding: var(--space-3) var(--space-4);\n  background: var(--bg-secondary);\n  border-right: 1px solid var(--border-subtle);\n  text-align: left;\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  vertical-align: middle;\n}\n.col-student--head[_ngcontent-%COMP%] {\n  z-index: 6;\n  background: var(--bg-surface);\n  font: 500 var(--text-xs)/1 var(--font-mono);\n  letter-spacing: var(--tracking-wide);\n  text-transform: uppercase;\n  color: var(--text-muted);\n  border-bottom: 1px solid var(--border-subtle);\n}\n.col-date[_ngcontent-%COMP%] {\n  min-width: 72px;\n  width: 72px;\n  padding: var(--space-3) 4px;\n  background: var(--bg-surface);\n  border-bottom: 1px solid var(--border-subtle);\n  border-right: 1px solid var(--border-subtle);\n  text-align: center;\n  font: 500 var(--text-xs)/1.2 var(--font-mono);\n  letter-spacing: var(--tracking-wide);\n  text-transform: uppercase;\n  color: var(--text-muted);\n  vertical-align: middle;\n  white-space: nowrap;\n}\n.col-date.today-column[_ngcontent-%COMP%] {\n  color: var(--accent-primary);\n  background: color-mix(in oklab, var(--accent-primary) 8%, var(--bg-surface));\n}\n.col-date__weekday[_ngcontent-%COMP%] {\n  display: block;\n  opacity: 0.8;\n  margin-bottom: 2px;\n}\n.col-date__date[_ngcontent-%COMP%] {\n  display: block;\n  color: var(--text-secondary);\n}\n.col-percent[_ngcontent-%COMP%] {\n  position: sticky;\n  right: 0;\n  z-index: 3;\n  width: 72px;\n  min-width: 72px;\n  max-width: 72px;\n  padding: var(--space-3) var(--space-3);\n  background: var(--bg-secondary);\n  border-left: 1px solid var(--border-subtle);\n  text-align: center;\n  vertical-align: middle;\n  font-family: var(--font-mono);\n  font-variant-numeric: tabular-nums;\n  font-weight: 600;\n}\n.col-percent--head[_ngcontent-%COMP%] {\n  z-index: 6;\n  background: var(--bg-surface);\n  font: 500 var(--text-xs)/1 var(--font-mono);\n  letter-spacing: var(--tracking-wide);\n  text-transform: uppercase;\n  color: var(--text-muted);\n  font-weight: 500;\n  border-bottom: 1px solid var(--border-subtle);\n}\n.col-percent--good[_ngcontent-%COMP%] {\n  color: var(--accent-primary);\n}\n.col-percent--warn[_ngcontent-%COMP%] {\n  color: var(--accent-warning);\n}\n.col-percent--bad[_ngcontent-%COMP%] {\n  color: var(--accent-danger);\n}\n.row-student[_ngcontent-%COMP%] {\n  transition: background var(--duration-base) var(--ease-out);\n}\n.row-student[_ngcontent-%COMP%]   td[_ngcontent-%COMP%] {\n  border-bottom: 1px solid var(--border-subtle);\n  vertical-align: middle;\n}\n.row-student[_ngcontent-%COMP%]:hover {\n  background: var(--bg-elevated);\n}\n.row-student[_ngcontent-%COMP%]:hover   .col-student[_ngcontent-%COMP%], \n.row-student[_ngcontent-%COMP%]:hover   .col-percent[_ngcontent-%COMP%] {\n  background: var(--bg-elevated);\n}\n.cell[_ngcontent-%COMP%] {\n  min-width: 72px;\n  width: 72px;\n  padding: 4px;\n  text-align: center;\n  border-right: 1px solid var(--border-subtle);\n  vertical-align: middle;\n}\n.cell-buttons[_ngcontent-%COMP%] {\n  display: inline-flex;\n  gap: 3px;\n  align-items: center;\n  justify-content: center;\n}\n.dot[_ngcontent-%COMP%] {\n  width: 20px;\n  height: 20px;\n  border-radius: 50%;\n  border: 1px solid var(--border-subtle);\n  background: transparent;\n  color: var(--text-muted);\n  font-size: 10px;\n  font-weight: 700;\n  cursor: pointer;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  opacity: 0.45;\n  transition:\n    transform 0.1s ease,\n    opacity 0.15s ease,\n    background 0.15s ease;\n  padding: 0;\n}\n.dot[_ngcontent-%COMP%]:hover {\n  opacity: 1;\n  transform: scale(1.1);\n}\n.dot--active[_ngcontent-%COMP%] {\n  opacity: 1;\n  color: var(--accent-primary-contrast, #fff);\n  border-color: transparent;\n}\n.dot--present.dot--active[_ngcontent-%COMP%] {\n  background: var(--status-present);\n}\n.dot--absent.dot--active[_ngcontent-%COMP%] {\n  background: var(--status-absent);\n  color: #fff;\n}\n.dot--excused.dot--active[_ngcontent-%COMP%] {\n  background: var(--status-excused);\n  color: #0A0E17;\n}\n.cell-cancelled[_ngcontent-%COMP%] {\n  color: var(--text-muted);\n  opacity: 0.5;\n  font-size: 12px;\n}\n.excuse-reason[_ngcontent-%COMP%] {\n  display: block;\n  margin-top: 2px;\n  font-size: 10px;\n  line-height: 1.2;\n  color: var(--status-excused, var(--accent-warning));\n  max-width: 72px;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n}\n/*# sourceMappingURL=headman-journal-grid.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanJournalGridComponent, [{
    type: Component,
    args: [{ selector: "app-headman-journal-grid", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [], template: `
    <div class="grid-wrapper"
         [attr.aria-busy]="loading"
         [style.pointer-events]="loading ? 'none' : ''">
      <table class="journal-table">
        <thead>
          <tr>
            <th class="col-student col-student--head">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</th>
            @for (col of columns(); track col.id) {
              <th class="col-date" [class.today-column]="col.isToday">
                <span class="col-date__weekday">{{ col.weekday }}</span>
                <span class="col-date__date">{{ col.displayDate }}</span>
              </th>
            }
            <th class="col-percent col-percent--head">%</th>
          </tr>
        </thead>
        <tbody>
          @for (row of dataSource(); track row.userId) {
            <tr class="row-student">
              <td class="col-student" [title]="row.displayName">{{ row.displayName }}</td>
              @for (col of columns(); track col.id) {
                <td class="cell">
                  @let cell = getCell(row.userId, col.id);
                  @if (cell && cell.status === 'cancelled') {
                    <span class="cell-cancelled"
                          [attr.aria-label]="'\u041E\u0442\u043C\u0435\u043D\u0435\u043D\u0430, ' + row.displayName + ', ' + col.displayDate">
                      {{ cell.symbol }}
                    </span>
                  } @else if (cell) {
                    <span class="cell-buttons">
                      @for (s of quickStatuses; track s) {
                        <button
                          type="button"
                          class="dot dot--{{ s }}"
                          [class.dot--active]="cell.status === s"
                          [attr.aria-label]="statusAriaLabel(s) + ', ' + row.displayName + ', ' + col.displayDate"
                          [attr.aria-pressed]="cell.status === s"
                          [attr.title]="s === 'excused' && cell.excuseReason ? cell.excuseReason : null"
                          (click)="setStatus(cell, row, s)"
                        >{{ statusSymbol(s) }}</button>
                      }
                    </span>
                    @if ((cell.status === 'excused' || cell.status === 'free_attendance') && cell.excuseReason) {
                      <span class="excuse-reason" [attr.title]="cell.excuseReason">{{ cell.excuseReason }}</span>
                    }
                  } @else {
                    <span class="cell-cancelled" aria-label="\u041D\u0435\u0442 \u0437\u0430\u043D\u044F\u0442\u0438\u044F">\u2014</span>
                  }
                </td>
              }
              <td class="col-percent" [class]="percentClass(getPercent(row.userId))">
                {{ getPercent(row.userId) }}%
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `, styles: ["/* angular:styles/component:css;ae578355db3ee91afe8c5f150e3ab1f252ea026ab632567c51b0624dbe747497;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts */\n:host {\n  display: block;\n}\n.grid-wrapper {\n  overflow-x: auto;\n  overflow-y: visible;\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-xl);\n  background: var(--bg-secondary);\n  position: relative;\n}\n.journal-table {\n  border-collapse: separate;\n  border-spacing: 0;\n  width: max-content;\n  min-width: 100%;\n  font-size: 13px;\n}\n.col-student {\n  position: sticky;\n  left: 0;\n  z-index: 3;\n  width: 220px;\n  min-width: 220px;\n  max-width: 220px;\n  padding: var(--space-3) var(--space-4);\n  background: var(--bg-secondary);\n  border-right: 1px solid var(--border-subtle);\n  text-align: left;\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  vertical-align: middle;\n}\n.col-student--head {\n  z-index: 6;\n  background: var(--bg-surface);\n  font: 500 var(--text-xs)/1 var(--font-mono);\n  letter-spacing: var(--tracking-wide);\n  text-transform: uppercase;\n  color: var(--text-muted);\n  border-bottom: 1px solid var(--border-subtle);\n}\n.col-date {\n  min-width: 72px;\n  width: 72px;\n  padding: var(--space-3) 4px;\n  background: var(--bg-surface);\n  border-bottom: 1px solid var(--border-subtle);\n  border-right: 1px solid var(--border-subtle);\n  text-align: center;\n  font: 500 var(--text-xs)/1.2 var(--font-mono);\n  letter-spacing: var(--tracking-wide);\n  text-transform: uppercase;\n  color: var(--text-muted);\n  vertical-align: middle;\n  white-space: nowrap;\n}\n.col-date.today-column {\n  color: var(--accent-primary);\n  background: color-mix(in oklab, var(--accent-primary) 8%, var(--bg-surface));\n}\n.col-date__weekday {\n  display: block;\n  opacity: 0.8;\n  margin-bottom: 2px;\n}\n.col-date__date {\n  display: block;\n  color: var(--text-secondary);\n}\n.col-percent {\n  position: sticky;\n  right: 0;\n  z-index: 3;\n  width: 72px;\n  min-width: 72px;\n  max-width: 72px;\n  padding: var(--space-3) var(--space-3);\n  background: var(--bg-secondary);\n  border-left: 1px solid var(--border-subtle);\n  text-align: center;\n  vertical-align: middle;\n  font-family: var(--font-mono);\n  font-variant-numeric: tabular-nums;\n  font-weight: 600;\n}\n.col-percent--head {\n  z-index: 6;\n  background: var(--bg-surface);\n  font: 500 var(--text-xs)/1 var(--font-mono);\n  letter-spacing: var(--tracking-wide);\n  text-transform: uppercase;\n  color: var(--text-muted);\n  font-weight: 500;\n  border-bottom: 1px solid var(--border-subtle);\n}\n.col-percent--good {\n  color: var(--accent-primary);\n}\n.col-percent--warn {\n  color: var(--accent-warning);\n}\n.col-percent--bad {\n  color: var(--accent-danger);\n}\n.row-student {\n  transition: background var(--duration-base) var(--ease-out);\n}\n.row-student td {\n  border-bottom: 1px solid var(--border-subtle);\n  vertical-align: middle;\n}\n.row-student:hover {\n  background: var(--bg-elevated);\n}\n.row-student:hover .col-student,\n.row-student:hover .col-percent {\n  background: var(--bg-elevated);\n}\n.cell {\n  min-width: 72px;\n  width: 72px;\n  padding: 4px;\n  text-align: center;\n  border-right: 1px solid var(--border-subtle);\n  vertical-align: middle;\n}\n.cell-buttons {\n  display: inline-flex;\n  gap: 3px;\n  align-items: center;\n  justify-content: center;\n}\n.dot {\n  width: 20px;\n  height: 20px;\n  border-radius: 50%;\n  border: 1px solid var(--border-subtle);\n  background: transparent;\n  color: var(--text-muted);\n  font-size: 10px;\n  font-weight: 700;\n  cursor: pointer;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  opacity: 0.45;\n  transition:\n    transform 0.1s ease,\n    opacity 0.15s ease,\n    background 0.15s ease;\n  padding: 0;\n}\n.dot:hover {\n  opacity: 1;\n  transform: scale(1.1);\n}\n.dot--active {\n  opacity: 1;\n  color: var(--accent-primary-contrast, #fff);\n  border-color: transparent;\n}\n.dot--present.dot--active {\n  background: var(--status-present);\n}\n.dot--absent.dot--active {\n  background: var(--status-absent);\n  color: #fff;\n}\n.dot--excused.dot--active {\n  background: var(--status-excused);\n  color: #0A0E17;\n}\n.cell-cancelled {\n  color: var(--text-muted);\n  opacity: 0.5;\n  font-size: 12px;\n}\n.excuse-reason {\n  display: block;\n  margin-top: 2px;\n  font-size: 10px;\n  line-height: 1.2;\n  color: var(--status-excused, var(--accent-warning));\n  max-width: 72px;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n}\n/*# sourceMappingURL=headman-journal-grid.component.css.map */\n"] }]
  }], null, { journalData: [{
    type: Input,
    args: [{ required: true }]
  }], loading: [{
    type: Input
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanJournalGridComponent, { className: "HeadmanJournalGridComponent", filePath: "src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts", lineNumber: 256 });
})();

// src/app/features/headman/journal/headman-journal-page.component.ts
var _forTrack02 = ($index, $item) => $item.id;
function HeadmanJournalPageComponent_For_12_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const s_r1 = \u0275\u0275nextContext().$implicit;
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" (", ctx_r1.typeLabel(s_r1.type), ")");
  }
}
function HeadmanJournalPageComponent_For_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-option", 6);
    \u0275\u0275text(1);
    \u0275\u0275template(2, HeadmanJournalPageComponent_For_12_Conditional_2_Template, 2, 1, "span");
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const s_r1 = ctx.$implicit;
    \u0275\u0275property("value", s_r1.id);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", s_r1.name, "");
    \u0275\u0275advance();
    \u0275\u0275conditional(s_r1.type ? 2 : -1);
  }
}
function HeadmanJournalPageComponent_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 7);
    \u0275\u0275text(1, "\u041D\u0430\u0436\u043C\u0438\u0442\u0435 \u043D\u0430 \u044F\u0447\u0435\u0439\u043A\u0443 \u0434\u043B\u044F \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F \u0441\u0442\u0430\u0442\u0443\u0441\u0430.");
    \u0275\u0275elementEnd();
  }
}
function HeadmanJournalPageComponent_Conditional_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-progress-bar", 8);
  }
}
function HeadmanJournalPageComponent_Conditional_15_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 9);
    \u0275\u0275element(1, "i", 12);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r1.error(), " ");
  }
}
function HeadmanJournalPageComponent_Conditional_16_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 10)(1, "div", 13);
    \u0275\u0275element(2, "i", 14);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "p", 15);
    \u0275\u0275text(4, "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "p", 16);
    \u0275\u0275text(6, " \u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442 \u0438\u0437 \u0432\u044B\u043F\u0430\u0434\u0430\u044E\u0449\u0435\u0433\u043E \u0441\u043F\u0438\u0441\u043A\u0430, \u0447\u0442\u043E\u0431\u044B \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438 \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0436\u0443\u0440\u043D\u0430\u043B. ");
    \u0275\u0275elementEnd()();
  }
}
function HeadmanJournalPageComponent_Conditional_17_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "app-headman-journal-grid", 11);
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275property("journalData", ctx_r1.journalData())("loading", ctx_r1.loading());
  }
}
var HeadmanJournalPageComponent = class _HeadmanJournalPageComponent {
  headmanApi = inject(HeadmanApiService);
  authService = inject(AuthService);
  subjects = signal([]);
  typeLabel = (t) => {
    switch (t) {
      case "LECTURE":
        return "\u041B\u0435\u043A\u0446\u0438\u044F";
      case "PRACTICE":
        return "\u041F\u0440\u0430\u043A\u0442\u0438\u0447\u0435\u0441\u043A\u043E\u0435 \u0437\u0430\u043D\u044F\u0442\u0438\u0435";
      case "LAB":
        return "\u041B\u0430\u0431\u043E\u0440\u0430\u0442\u043E\u0440\u043D\u0430\u044F \u0440\u0430\u0431\u043E\u0442\u0430";
      default:
        return "";
    }
  };
  selectedSubjectId = signal(null);
  journalData = signal(null);
  loading = signal(false);
  error = signal(null);
  constructor() {
    effect(() => {
      const id = this.selectedSubjectId();
      if (id != null) {
        this.loadJournal();
      }
    });
  }
  getToday() {
    return (/* @__PURE__ */ new Date()).toISOString().split("T")[0];
  }
  /** Backend expects an explicit range — use a wide past window; future lessons are excluded by dateTo=today. */
  getRangeStart() {
    const d = /* @__PURE__ */ new Date();
    d.setFullYear(d.getFullYear() - 1);
    return d.toISOString().split("T")[0];
  }
  ngOnInit() {
    this.headmanApi.listSubjects(0, 100).pipe(catchError(() => of(null))).subscribe((res) => {
      if (res?._embedded) {
        const key = Object.keys(res._embedded)[0];
        const raw = res._embedded[key] ?? [];
        this.subjects.set(raw.map((s) => ({ id: s.id, name: s.name, type: s.type ?? null })));
      }
    });
  }
  onSubjectChange(id) {
    this.selectedSubjectId.set(id);
  }
  loadJournal() {
    const subjectId = this.selectedSubjectId();
    const groupId = this.authService.currentUser()?.groupId;
    if (!subjectId || !groupId)
      return;
    this.loading.set(true);
    this.error.set(null);
    this.journalData.set(null);
    this.headmanApi.getJournal(groupId, subjectId, this.getRangeStart(), this.getToday()).pipe(catchError(() => {
      this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0434\u0430\u043D\u043D\u044B\u0435. \u041E\u0431\u043D\u043E\u0432\u0438\u0442\u0435 \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0443 \u0438\u043B\u0438 \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043F\u043E\u0437\u0436\u0435.");
      return of(null);
    }), finalize(() => this.loading.set(false))).subscribe((data) => {
      this.journalData.set(data);
    });
  }
  static \u0275fac = function HeadmanJournalPageComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanJournalPageComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanJournalPageComponent, selectors: [["app-headman-journal-page"]], hostVars: 1, hostBindings: function HeadmanJournalPageComponent_HostBindings(rf, ctx) {
    if (rf & 2) {
      \u0275\u0275syntheticHostProperty("@routeFade", void 0);
    }
  }, decls: 18, vars: 4, consts: [[1, "page-header"], [1, "page-header__eyebrow"], [1, "page-card"], [1, "page-filters"], ["appearance", "outline"], ["placeholder", "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442", 3, "ngModelChange", "ngModel"], [3, "value"], [1, "journal-hint"], ["mode", "indeterminate", "aria-label", "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430 \u0436\u0443\u0440\u043D\u0430\u043B\u0430"], ["role", "alert", 1, "page-error"], ["role", "status", "aria-live", "polite", 1, "page-empty"], [3, "journalData", "loading"], [1, "ph", "ph-warning-circle"], [1, "page-empty__icon"], [1, "ph", "ph-table"], [1, "page-empty__title"], [1, "page-empty__text"]], template: function HeadmanJournalPageComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "h1");
      \u0275\u0275text(2, "\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(3, "span", 1);
      \u0275\u0275text(4, "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(5, "div", 2)(6, "div", 3)(7, "mat-form-field", 4)(8, "mat-label");
      \u0275\u0275text(9, "\u041F\u0440\u0435\u0434\u043C\u0435\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(10, "mat-select", 5);
      \u0275\u0275listener("ngModelChange", function HeadmanJournalPageComponent_Template_mat_select_ngModelChange_10_listener($event) {
        return ctx.onSubjectChange($event);
      });
      \u0275\u0275repeaterCreate(11, HeadmanJournalPageComponent_For_12_Template, 3, 3, "mat-option", 6, _forTrack02);
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(13, HeadmanJournalPageComponent_Conditional_13_Template, 2, 0, "p", 7)(14, HeadmanJournalPageComponent_Conditional_14_Template, 1, 0, "mat-progress-bar", 8)(15, HeadmanJournalPageComponent_Conditional_15_Template, 3, 1, "div", 9)(16, HeadmanJournalPageComponent_Conditional_16_Template, 7, 0, "div", 10)(17, HeadmanJournalPageComponent_Conditional_17_Template, 1, 2, "app-headman-journal-grid", 11);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275advance(10);
      \u0275\u0275property("ngModel", ctx.selectedSubjectId());
      \u0275\u0275advance();
      \u0275\u0275repeater(ctx.subjects());
      \u0275\u0275advance(2);
      \u0275\u0275conditional(ctx.selectedSubjectId() && !ctx.loading() && !ctx.error() ? 13 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.loading() ? 14 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.error() ? 15 : !ctx.journalData() ? 16 : 17);
    }
  }, dependencies: [
    FormsModule,
    NgControlStatus,
    NgModel,
    MatSelectModule,
    MatFormField,
    MatLabel,
    MatSelect,
    MatOption,
    MatFormFieldModule,
    MatProgressBarModule,
    MatProgressBar,
    HeadmanJournalGridComponent
  ], encapsulation: 2, data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanJournalPageComponent, [{
    type: Component,
    args: [{ selector: "app-headman-journal-page", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [
      FormsModule,
      MatSelectModule,
      MatFormFieldModule,
      MatProgressBarModule,
      HeadmanJournalGridComponent
    ], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], host: { "[@routeFade]": "" }, template: '<div class="page-header">\n  <h1>\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438</h1>\n  <span class="page-header__eyebrow">\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430</span>\n</div>\n\n<div class="page-card">\n  <div class="page-filters">\n    <mat-form-field appearance="outline">\n      <mat-label>\u041F\u0440\u0435\u0434\u043C\u0435\u0442</mat-label>\n      <mat-select\n        [ngModel]="selectedSubjectId()"\n        (ngModelChange)="onSubjectChange($event)"\n        placeholder="\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442"\n      >\n        @for (s of subjects(); track s.id) {\n          <mat-option [value]="s.id">\n            {{ s.name }}@if (s.type) { <span> ({{ typeLabel(s.type) }})</span> }\n          </mat-option>\n        }\n      </mat-select>\n    </mat-form-field>\n  </div>\n\n  @if (selectedSubjectId() && !loading() && !error()) {\n    <p class="journal-hint">\u041D\u0430\u0436\u043C\u0438\u0442\u0435 \u043D\u0430 \u044F\u0447\u0435\u0439\u043A\u0443 \u0434\u043B\u044F \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F \u0441\u0442\u0430\u0442\u0443\u0441\u0430.</p>\n  }\n\n  @if (loading()) {\n    <mat-progress-bar mode="indeterminate" aria-label="\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430 \u0436\u0443\u0440\u043D\u0430\u043B\u0430"></mat-progress-bar>\n  }\n\n  @if (error()) {\n    <div class="page-error" role="alert">\n      <i class="ph ph-warning-circle"></i>\n      {{ error() }}\n    </div>\n  } @else if (!journalData()) {\n    <div class="page-empty" role="status" aria-live="polite">\n      <div class="page-empty__icon"><i class="ph ph-table"></i></div>\n      <p class="page-empty__title">\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442</p>\n      <p class="page-empty__text">\n        \u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442 \u0438\u0437 \u0432\u044B\u043F\u0430\u0434\u0430\u044E\u0449\u0435\u0433\u043E \u0441\u043F\u0438\u0441\u043A\u0430, \u0447\u0442\u043E\u0431\u044B \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438 \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0436\u0443\u0440\u043D\u0430\u043B.\n      </p>\n    </div>\n  } @else {\n    <app-headman-journal-grid\n      [journalData]="journalData()!"\n      [loading]="loading()"\n    ></app-headman-journal-grid>\n  }\n</div>\n' }]
  }], () => [], null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanJournalPageComponent, { className: "HeadmanJournalPageComponent", filePath: "src/app/features/headman/journal/headman-journal-page.component.ts", lineNumber: 45 });
})();
export {
  HeadmanJournalPageComponent
};
//# sourceMappingURL=chunk-KWLY5T5A.js.map
