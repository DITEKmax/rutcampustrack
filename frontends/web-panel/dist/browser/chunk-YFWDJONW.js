import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerModule,
  MatDatepickerToggle
} from "./chunk-ZAMWUHYQ.js";
import {
  CdkCell,
  CdkCellDef,
  CdkColumnDef,
  CdkHeaderCell,
  CdkHeaderCellDef,
  CdkHeaderRow,
  CdkHeaderRowDef,
  CdkRow,
  CdkRowDef,
  CdkTable,
  CdkTableModule
} from "./chunk-SEQXD3QE.js";
import {
  MatInput,
  MatInputModule
} from "./chunk-WQN3TC62.js";
import {
  MatNativeDateModule
} from "./chunk-2CP43WR6.js";
import "./chunk-6V3IFZC4.js";
import "./chunk-POR7KUSL.js";
import "./chunk-OIBNGD5S.js";
import {
  JournalApiService
} from "./chunk-T4UZ7SIZ.js";
import {
  MatFormField,
  MatLabel,
  MatSelect,
  MatSelectModule,
  MatSuffix
} from "./chunk-Z3VV4J2X.js";
import {
  MatButtonModule
} from "./chunk-PAS4QIDL.js";
import {
  MatOption
} from "./chunk-4RJPSRCU.js";
import "./chunk-6JM2QIGP.js";
import "./chunk-75YPR2VK.js";
import {
  MatProgressBarModule
} from "./chunk-PWYB6KY4.js";
import {
  CdkFixedSizeVirtualScroll,
  CdkVirtualScrollViewport,
  ScrollingModule
} from "./chunk-2UA5NBNP.js";
import "./chunk-YIJEORIR.js";
import "./chunk-B3BDHDAM.js";
import {
  ReactiveFormsModule
} from "./chunk-XAMIXVT7.js";
import "./chunk-M5DKW26A.js";
import {
  Component,
  Input,
  computed,
  inject,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵNgOnChangesFeature,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵclassMapInterpolate1,
  ɵɵclassProp,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵelement,
  ɵɵelementContainerEnd,
  ɵɵelementContainerStart,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵgetCurrentView,
  ɵɵlistener,
  ɵɵnextContext,
  ɵɵproperty,
  ɵɵreference,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-MFRIGFR2.js";

// src/app/shared/attendance-symbols.ts
var ATTENDANCE_SYMBOLS = {
  present: "+",
  absent: "\u043D",
  excused: "\u0443",
  free_attendance: "\u0441\u043F",
  cancelled: "\u2014"
};
function symbolFor(status) {
  return ATTENDANCE_SYMBOLS[status] ?? "?";
}

// src/app/features/teacher/journal/journal-cell/journal-cell.component.ts
function JournalCellComponent_Conditional_0_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 1);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275attribute("title", ctx_r0.cell.excuseReason);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.cell.excuseReason);
  }
}
function JournalCellComponent_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
    \u0275\u0275template(2, JournalCellComponent_Conditional_0_Conditional_2_Template, 2, 2, "span", 1);
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275classMapInterpolate1("status-chip status-chip--", ctx_r0.cell.status, "");
    \u0275\u0275attribute("title", ctx_r0.reasonTitle());
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.displaySymbol());
    \u0275\u0275advance();
    \u0275\u0275conditional((ctx_r0.cell.status === "excused" || ctx_r0.cell.status === "free_attendance") && ctx_r0.cell.excuseReason ? 2 : -1);
  }
}
function JournalCellComponent_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 0);
    \u0275\u0275text(1, "\xB7");
    \u0275\u0275elementEnd();
  }
}
var JournalCellComponent = class _JournalCellComponent {
  cell;
  displaySymbol() {
    return this.cell ? symbolFor(this.cell.status) : "";
  }
  reasonTitle() {
    if (!this.cell)
      return null;
    if (this.cell.status !== "excused" && this.cell.status !== "free_attendance")
      return null;
    return this.cell.excuseReason ?? null;
  }
  static \u0275fac = function JournalCellComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _JournalCellComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _JournalCellComponent, selectors: [["app-journal-cell"]], inputs: { cell: "cell" }, decls: 2, vars: 1, consts: [[1, "text-[var(--color-pending)]"], [1, "excuse-reason"]], template: function JournalCellComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275template(0, JournalCellComponent_Conditional_0_Template, 3, 6)(1, JournalCellComponent_Conditional_1_Template, 2, 0, "span", 0);
    }
    if (rf & 2) {
      \u0275\u0275conditional(ctx.cell ? 0 : 1);
    }
  }, styles: ["\n\n.excuse-reason[_ngcontent-%COMP%] {\n  display: block;\n  margin-top: 2px;\n  font-size: 10px;\n  line-height: 1.2;\n  color: var(--status-excused, var(--accent-warning));\n  max-width: 72px;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n}\n/*# sourceMappingURL=journal-cell.component.css.map */"] });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(JournalCellComponent, [{
    type: Component,
    args: [{ selector: "app-journal-cell", standalone: true, imports: [], template: `
    @if (cell) {
      <span
        class="status-chip status-chip--{{ cell.status }}"
        [attr.title]="reasonTitle()"
      >{{ displaySymbol() }}</span>
      @if ((cell.status === 'excused' || cell.status === 'free_attendance') && cell.excuseReason) {
        <span class="excuse-reason" [attr.title]="cell.excuseReason">{{ cell.excuseReason }}</span>
      }
    } @else {
      <span class="text-[var(--color-pending)]">\xB7</span>
    }
  `, styles: ["/* angular:styles/component:css;749413b720d8c904d54352f1a09e5268417fa553ddd5251bbfd4f2252c986480;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/teacher/journal/journal-cell/journal-cell.component.ts */\n.excuse-reason {\n  display: block;\n  margin-top: 2px;\n  font-size: 10px;\n  line-height: 1.2;\n  color: var(--status-excused, var(--accent-warning));\n  max-width: 72px;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n}\n/*# sourceMappingURL=journal-cell.component.css.map */\n"] }]
  }], null, { cell: [{
    type: Input
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(JournalCellComponent, { className: "JournalCellComponent", filePath: "src/app/features/teacher/journal/journal-cell/journal-cell.component.ts", lineNumber: 36 });
})();

// src/app/features/teacher/journal/journal-grid/journal-grid.component.ts
var _forTrack0 = ($index, $item) => $item.id;
function JournalGridComponent_th_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "th", 9);
    \u0275\u0275text(1, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
    \u0275\u0275elementEnd();
  }
}
function JournalGridComponent_td_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "td", 10);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r1 = ctx.$implicit;
    \u0275\u0275property("title", row_r1.displayName);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", row_r1.displayName, " ");
  }
}
function JournalGridComponent_For_7_th_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "th", 13)(1, "div", 14);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "div", 15);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const col_r2 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275classProp("today-column", col_r2.isToday);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(col_r2.displayDate);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1("\u041F\u0430\u0440\u0430 ", col_r2.lessonNumber, "");
  }
}
function JournalGridComponent_For_7_td_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "td", 16);
    \u0275\u0275element(1, "app-journal-cell", 17);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const row_r3 = ctx.$implicit;
    const col_r2 = \u0275\u0275nextContext().$implicit;
    const ctx_r3 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275property("cell", ctx_r3.getCellFor(row_r3, col_r2.date, col_r2.lessonNumber));
  }
}
function JournalGridComponent_For_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementContainerStart(0, 6);
    \u0275\u0275template(1, JournalGridComponent_For_7_th_1_Template, 5, 4, "th", 11)(2, JournalGridComponent_For_7_td_2_Template, 2, 1, "td", 12);
    \u0275\u0275elementContainerEnd();
  }
  if (rf & 2) {
    const col_r2 = ctx.$implicit;
    \u0275\u0275property("cdkColumnDef", col_r2.id);
  }
}
function JournalGridComponent_tr_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "tr", 18);
  }
}
function JournalGridComponent_tr_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "tr", 19);
  }
  if (rf & 2) {
    const row_r5 = ctx.$implicit;
    const ctx_r3 = \u0275\u0275nextContext();
    \u0275\u0275classProp("even-row", ctx_r3.isEvenRow(row_r5));
  }
}
var JournalGridComponent = class _JournalGridComponent {
  journalData;
  today = (/* @__PURE__ */ new Date()).toISOString().slice(0, 10);
  /** Internal signal holding current journalData for computed signals */
  _journalData = signal(null);
  /** Pre-computed lookup: userId -> (columnId -> JournalCell) */
  cellMap = /* @__PURE__ */ new Map();
  ngOnChanges(changes) {
    if (changes["journalData"] && this.journalData) {
      this._journalData.set(this.journalData);
      this.buildCellMap();
    }
  }
  buildCellMap() {
    this.cellMap = /* @__PURE__ */ new Map();
    for (const student of this.journalData.students) {
      const colMap = /* @__PURE__ */ new Map();
      for (const record of student.records) {
        const key = `${record.date}_lesson${record.lessonNumber}`;
        colMap.set(key, record);
      }
      this.cellMap.set(student.userId, colMap);
    }
  }
  columns = computed(() => {
    const data = this._journalData();
    if (!data)
      return [];
    const seen = /* @__PURE__ */ new Set();
    const pairs = [];
    for (const student of data.students) {
      for (const record of student.records) {
        const key = `${record.date}_lesson${record.lessonNumber}`;
        if (!seen.has(key)) {
          seen.add(key);
          pairs.push({ date: record.date, lessonNumber: record.lessonNumber });
        }
      }
    }
    pairs.sort((a, b) => {
      const dateCmp = a.date.localeCompare(b.date);
      return dateCmp !== 0 ? dateCmp : a.lessonNumber - b.lessonNumber;
    });
    return pairs.map(({ date, lessonNumber }) => {
      const [year, month, day] = date.split("-");
      const displayDate = `${day}.${month}`;
      return {
        id: `${date}_lesson${lessonNumber}`,
        date,
        lessonNumber,
        displayDate,
        isToday: date === this.today
      };
    });
  });
  displayedColumns = computed(() => [
    "student",
    ...this.columns().map((c) => c.id)
  ]);
  dataSource = computed(() => {
    const data = this._journalData();
    return data ? data.students : [];
  });
  getCellFor(row, date, lessonNumber) {
    const colId = `${date}_lesson${lessonNumber}`;
    return this.cellMap.get(row.userId)?.get(colId) ?? null;
  }
  isEvenRow(row) {
    const data = this._journalData();
    if (!data)
      return false;
    const index = data.students.indexOf(row);
    return index % 2 === 0;
  }
  static \u0275fac = function JournalGridComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _JournalGridComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _JournalGridComponent, selectors: [["app-journal-grid"]], inputs: { journalData: "journalData" }, features: [\u0275\u0275NgOnChangesFeature], decls: 10, vars: 7, consts: [[1, "journal-grid-wrapper"], [1, "journal-viewport", 3, "itemSize", "minBufferPx", "maxBufferPx"], ["cdk-table", "", 3, "dataSource"], ["cdkColumnDef", "student", "sticky", ""], ["cdk-header-cell", "", "class", "student-header", 4, "cdkHeaderCellDef"], ["cdk-cell", "", "class", "student-cell", 3, "title", 4, "cdkCellDef"], [3, "cdkColumnDef"], ["cdk-header-row", "", 4, "cdkHeaderRowDef", "cdkHeaderRowDefSticky"], ["cdk-row", "", 3, "even-row", 4, "cdkRowDef", "cdkRowDefColumns"], ["cdk-header-cell", "", 1, "student-header"], ["cdk-cell", "", 1, "student-cell", 3, "title"], ["cdk-header-cell", "", "class", "date-header", 3, "today-column", 4, "cdkHeaderCellDef"], ["cdk-cell", "", "class", "status-cell", 4, "cdkCellDef"], ["cdk-header-cell", "", 1, "date-header"], [1, "date-label"], [1, "lesson-label"], ["cdk-cell", "", 1, "status-cell"], [3, "cell"], ["cdk-header-row", ""], ["cdk-row", ""]], template: function JournalGridComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "cdk-virtual-scroll-viewport", 1)(2, "table", 2);
      \u0275\u0275elementContainerStart(3, 3);
      \u0275\u0275template(4, JournalGridComponent_th_4_Template, 2, 0, "th", 4)(5, JournalGridComponent_td_5_Template, 2, 2, "td", 5);
      \u0275\u0275elementContainerEnd();
      \u0275\u0275repeaterCreate(6, JournalGridComponent_For_7_Template, 3, 1, "ng-container", 6, _forTrack0);
      \u0275\u0275template(8, JournalGridComponent_tr_8_Template, 1, 0, "tr", 7)(9, JournalGridComponent_tr_9_Template, 1, 2, "tr", 8);
      \u0275\u0275elementEnd()()();
    }
    if (rf & 2) {
      \u0275\u0275advance();
      \u0275\u0275property("itemSize", 40)("minBufferPx", 400)("maxBufferPx", 800);
      \u0275\u0275advance();
      \u0275\u0275property("dataSource", ctx.dataSource());
      \u0275\u0275advance(4);
      \u0275\u0275repeater(ctx.columns());
      \u0275\u0275advance(2);
      \u0275\u0275property("cdkHeaderRowDef", ctx.displayedColumns())("cdkHeaderRowDefSticky", true);
      \u0275\u0275advance();
      \u0275\u0275property("cdkRowDefColumns", ctx.displayedColumns());
    }
  }, dependencies: [CdkTableModule, CdkTable, CdkRowDef, CdkCellDef, CdkHeaderCellDef, CdkColumnDef, CdkCell, CdkRow, CdkHeaderCell, CdkHeaderRow, CdkHeaderRowDef, ScrollingModule, CdkFixedSizeVirtualScroll, CdkVirtualScrollViewport, JournalCellComponent], styles: ["\n\n.journal-grid-wrapper[_ngcontent-%COMP%] {\n  overflow-x: auto;\n  min-width: 800px;\n}\n.journal-viewport[_ngcontent-%COMP%] {\n  height: calc(100vh - 220px);\n}\n[_nghost-%COMP%]     cdk-row, \n[_nghost-%COMP%]     cdk-header-row {\n  display: flex;\n  align-items: center;\n  height: 40px;\n}\n.student-header[_ngcontent-%COMP%], \n.student-cell[_ngcontent-%COMP%] {\n  position: sticky;\n  left: 0;\n  z-index: 2;\n  width: 200px;\n  min-width: 200px;\n  max-width: 200px;\n  padding: 0 8px;\n  background: var(--mat-sys-surface);\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  font-size: 14px;\n}\n.date-header[_ngcontent-%COMP%] {\n  width: 48px;\n  min-width: 48px;\n  text-align: center;\n  height: 48px;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  justify-content: center;\n  font-size: 12px;\n  font-weight: 600;\n  background: var(--mat-sys-surface-container);\n}\n.today-column[_ngcontent-%COMP%] {\n  border-bottom: 2px solid var(--mat-sys-primary);\n}\n.date-label[_ngcontent-%COMP%] {\n  line-height: 1.2;\n}\n.lesson-label[_ngcontent-%COMP%] {\n  line-height: 1.2;\n  color: var(--mat-sys-on-surface);\n  opacity: 0.6;\n}\n.status-cell[_ngcontent-%COMP%] {\n  width: 48px;\n  min-width: 48px;\n  text-align: center;\n  padding: 8px 0;\n}\n.even-row[_ngcontent-%COMP%] {\n  background: var(--mat-sys-surface-container);\n}\n[_nghost-%COMP%]     cdk-header-row {\n  position: sticky;\n  top: 0;\n  z-index: 3;\n  background: var(--mat-sys-surface-container);\n}\n/*# sourceMappingURL=journal-grid.component.css.map */"] });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(JournalGridComponent, [{
    type: Component,
    args: [{ selector: "app-journal-grid", standalone: true, imports: [CdkTableModule, ScrollingModule, JournalCellComponent], template: '<div class="journal-grid-wrapper">\n  <cdk-virtual-scroll-viewport [itemSize]="40" [minBufferPx]="400" [maxBufferPx]="800"\n    class="journal-viewport">\n    <table cdk-table [dataSource]="dataSource()">\n      <!-- Sticky student name column -->\n      <ng-container cdkColumnDef="student" sticky>\n        <th cdk-header-cell *cdkHeaderCellDef class="student-header">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</th>\n        <td cdk-cell *cdkCellDef="let row" class="student-cell"\n            [title]="row.displayName">\n          {{ row.displayName }}\n        </td>\n      </ng-container>\n\n      <!-- Dynamic date/lesson columns -->\n      @for (col of columns(); track col.id) {\n        <ng-container [cdkColumnDef]="col.id">\n          <th cdk-header-cell *cdkHeaderCellDef class="date-header"\n              [class.today-column]="col.isToday">\n            <div class="date-label">{{ col.displayDate }}</div>\n            <div class="lesson-label">\u041F\u0430\u0440\u0430 {{ col.lessonNumber }}</div>\n          </th>\n          <td cdk-cell *cdkCellDef="let row" class="status-cell">\n            <app-journal-cell [cell]="getCellFor(row, col.date, col.lessonNumber)" />\n          </td>\n        </ng-container>\n      }\n\n      <tr cdk-header-row *cdkHeaderRowDef="displayedColumns(); sticky: true"></tr>\n      <tr cdk-row *cdkRowDef="let row; columns: displayedColumns();"\n          [class.even-row]="isEvenRow(row)"></tr>\n    </table>\n  </cdk-virtual-scroll-viewport>\n</div>\n', styles: ["/* angular:styles/component:css;c781191f9733dd5c74a61c9a136bf787926bb802a0e4752ccdc70ecc97e69766;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/teacher/journal/journal-grid/journal-grid.component.ts */\n.journal-grid-wrapper {\n  overflow-x: auto;\n  min-width: 800px;\n}\n.journal-viewport {\n  height: calc(100vh - 220px);\n}\n:host ::ng-deep cdk-row,\n:host ::ng-deep cdk-header-row {\n  display: flex;\n  align-items: center;\n  height: 40px;\n}\n.student-header,\n.student-cell {\n  position: sticky;\n  left: 0;\n  z-index: 2;\n  width: 200px;\n  min-width: 200px;\n  max-width: 200px;\n  padding: 0 8px;\n  background: var(--mat-sys-surface);\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  font-size: 14px;\n}\n.date-header {\n  width: 48px;\n  min-width: 48px;\n  text-align: center;\n  height: 48px;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  justify-content: center;\n  font-size: 12px;\n  font-weight: 600;\n  background: var(--mat-sys-surface-container);\n}\n.today-column {\n  border-bottom: 2px solid var(--mat-sys-primary);\n}\n.date-label {\n  line-height: 1.2;\n}\n.lesson-label {\n  line-height: 1.2;\n  color: var(--mat-sys-on-surface);\n  opacity: 0.6;\n}\n.status-cell {\n  width: 48px;\n  min-width: 48px;\n  text-align: center;\n  padding: 8px 0;\n}\n.even-row {\n  background: var(--mat-sys-surface-container);\n}\n:host ::ng-deep cdk-header-row {\n  position: sticky;\n  top: 0;\n  z-index: 3;\n  background: var(--mat-sys-surface-container);\n}\n/*# sourceMappingURL=journal-grid.component.css.map */\n"] }]
  }], null, { journalData: [{
    type: Input,
    args: [{ required: true }]
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(JournalGridComponent, { className: "JournalGridComponent", filePath: "src/app/features/teacher/journal/journal-grid/journal-grid.component.ts", lineNumber: 44 });
})();

// src/app/features/teacher/journal/status-legend/status-legend.component.ts
var _forTrack02 = ($index, $item) => $item.status;
function StatusLegendComponent_For_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 1)(1, "span");
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "span", 2);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const entry_r1 = ctx.$implicit;
    \u0275\u0275advance();
    \u0275\u0275classMapInterpolate1("status-chip status-chip--", entry_r1.status, "");
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(entry_r1.symbol);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(entry_r1.label);
  }
}
var StatusLegendComponent = class _StatusLegendComponent {
  entries = [
    { symbol: "+", label: "\u043F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B", status: "present" },
    { symbol: "\u043D", label: "\u043E\u0442\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B", status: "absent" },
    { symbol: "\u0443", label: "\u0443\u0432\u0430\u0436\u0438\u0442\u0435\u043B\u044C\u043D\u0430\u044F \u043F\u0440\u0438\u0447\u0438\u043D\u0430", status: "excused" },
    { symbol: "\u0441\u043F", label: "\u0441\u0432\u043E\u0431\u043E\u0434\u043D\u043E\u0435 \u043F\u043E\u0441\u0435\u0449\u0435\u043D\u0438\u0435", status: "free_attendance" },
    { symbol: "\u2014", label: "\u043E\u0442\u043C\u0435\u043D\u0435\u043D\u0430", status: "cancelled" }
  ];
  static \u0275fac = function StatusLegendComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StatusLegendComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StatusLegendComponent, selectors: [["app-status-legend"]], decls: 3, vars: 0, consts: [[1, "flex", "flex-wrap", "gap-x-6", "gap-y-2", "items-center"], [1, "flex", "items-center", "gap-2"], [1, "text-[12px]", "opacity-60"]], template: function StatusLegendComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0);
      \u0275\u0275repeaterCreate(1, StatusLegendComponent_For_2_Template, 5, 5, "div", 1, _forTrack02);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275advance();
      \u0275\u0275repeater(ctx.entries);
    }
  }, encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StatusLegendComponent, [{
    type: Component,
    args: [{
      selector: "app-status-legend",
      standalone: true,
      template: `
    <div class="flex flex-wrap gap-x-6 gap-y-2 items-center">
      @for (entry of entries; track entry.status) {
        <div class="flex items-center gap-2">
          <span class="status-chip status-chip--{{ entry.status }}">{{ entry.symbol }}</span>
          <span class="text-[12px] opacity-60">{{ entry.label }}</span>
        </div>
      }
    </div>
  `
    }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StatusLegendComponent, { className: "StatusLegendComponent", filePath: "src/app/features/teacher/journal/status-legend/status-legend.component.ts", lineNumber: 23 });
})();

// src/app/features/teacher/journal/journal-page.component.ts
var _forTrack03 = ($index, $item) => $item.id;
function JournalPageComponent_For_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-option", 10);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const g_r2 = ctx.$implicit;
    \u0275\u0275property("value", g_r2.id);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(g_r2.name);
  }
}
function JournalPageComponent_For_20_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-option", 10);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const s_r3 = ctx.$implicit;
    \u0275\u0275property("value", s_r3.id);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(s_r3.name);
  }
}
function JournalPageComponent_Conditional_38_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 16);
    \u0275\u0275text(1, "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430 \u0436\u0443\u0440\u043D\u0430\u043B\u0430\u2026");
    \u0275\u0275elementEnd();
  }
}
function JournalPageComponent_Conditional_39_Template(rf, ctx) {
  if (rf & 1) {
    const _r4 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "div", 17);
    \u0275\u0275element(1, "i", 18);
    \u0275\u0275text(2);
    \u0275\u0275elementStart(3, "button", 19);
    \u0275\u0275listener("click", function JournalPageComponent_Conditional_39_Template_button_click_3_listener() {
      \u0275\u0275restoreView(_r4);
      const ctx_r4 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r4.loadJournal());
    });
    \u0275\u0275text(4, " \u041F\u043E\u0432\u0442\u043E\u0440\u0438\u0442\u044C ");
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r4 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r4.error(), " ");
  }
}
function JournalPageComponent_Conditional_40_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 6)(1, "div", 20)(2, "div", 21);
    \u0275\u0275element(3, "i", 22);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "h3", 23);
    \u0275\u0275text(5, "\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u0443\u0441\u0442");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "p", 24);
    \u0275\u0275text(7, " \u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0433\u0440\u0443\u043F\u043F\u0443 \u0438 \u043F\u0440\u0435\u0434\u043C\u0435\u0442, \u0447\u0442\u043E\u0431\u044B \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0436\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438. ");
    \u0275\u0275elementEnd()()();
  }
}
function JournalPageComponent_Conditional_41_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 25);
    \u0275\u0275element(1, "app-journal-grid", 26);
    \u0275\u0275elementEnd();
    \u0275\u0275element(2, "app-status-legend");
  }
  if (rf & 2) {
    const ctx_r4 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275property("journalData", ctx_r4.journalData());
  }
}
var JournalPageComponent = class _JournalPageComponent {
  journalApi = inject(JournalApiService);
  groups = signal([]);
  subjects = signal([]);
  journalData = signal(null);
  loading = signal(false);
  error = signal(null);
  selectedGroupId = signal(null);
  selectedSubjectId = signal(null);
  dateFrom = signal("");
  dateTo = signal("");
  canApply = computed(() => this.selectedGroupId() !== null && this.selectedSubjectId() !== null);
  /** Cached assignments for group-subject filtering */
  assignments = [];
  ngOnInit() {
    const now = /* @__PURE__ */ new Date();
    this.dateFrom.set(new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10));
    this.dateTo.set(now.toISOString().slice(0, 10));
    this.journalApi.getMyAssignments().subscribe({
      next: (assignments) => {
        this.assignments = assignments;
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
    this.selectedSubjectId.set(null);
    this.journalData.set(null);
    const subjectIds = new Set(this.assignments.filter((a) => a.groupId === groupId).map((a) => a.subjectId));
    this.journalApi.getSubjects().subscribe({
      next: (subjects) => {
        this.subjects.set(subjects.filter((s) => subjectIds.has(s.id)));
      }
    });
  }
  loadJournal() {
    const groupId = this.selectedGroupId();
    const subjectId = this.selectedSubjectId();
    if (groupId === null || subjectId === null)
      return;
    this.loading.set(true);
    this.error.set(null);
    this.journalApi.getJournal(groupId, subjectId, this.dateFrom(), this.dateTo()).subscribe({
      next: (response) => {
        this.journalData.set(response);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0436\u0443\u0440\u043D\u0430\u043B. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0441\u043E\u0435\u0434\u0438\u043D\u0435\u043D\u0438\u0435 \u0438 \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0441\u043D\u043E\u0432\u0430.");
        this.loading.set(false);
      }
    });
  }
  static \u0275fac = function JournalPageComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _JournalPageComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _JournalPageComponent, selectors: [["app-journal-page"]], decls: 42, vars: 11, consts: [["pickerFrom", ""], ["pickerTo", ""], [1, "page-stack"], [1, "page-header"], [1, "page-header__title"], [1, "page-header__subtitle"], [1, "page-card"], [1, "page-filters"], [1, "w-48"], [3, "selectionChange"], [3, "value"], [1, "w-40"], ["matInput", "", 3, "dateChange", "matDatepicker", "value"], ["matIconSuffix", "", 3, "for"], ["type", "button", 1, "btn-brand", 3, "click", "disabled"], ["aria-hidden", "true", 1, "ph", "ph-magnifying-glass"], [1, "text-sm", 2, "color", "var(--text-secondary)"], ["role", "alert", 1, "page-error"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"], ["type", "button", 1, "btn-brand", 2, "margin-left", "var(--space-4)", 3, "click"], [1, "page-empty"], ["aria-hidden", "true", 1, "page-empty__icon"], [1, "ph-duotone", "ph-book-open"], [1, "page-empty__title"], [1, "page-empty__text"], [1, "page-card", "page-card--flush"], [3, "journalData"]], template: function JournalPageComponent_Template(rf, ctx) {
    if (rf & 1) {
      const _r1 = \u0275\u0275getCurrentView();
      \u0275\u0275elementStart(0, "div", 2)(1, "header", 3)(2, "div", 4)(3, "h1");
      \u0275\u0275text(4, "\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "p", 5);
      \u0275\u0275text(6, " \u041E\u0442\u043C\u0435\u0442\u043A\u0438 \u0433\u0440\u0443\u043F\u043F\u044B \u0437\u0430 \u0432\u044B\u0431\u0440\u0430\u043D\u043D\u044B\u0439 \u043F\u0435\u0440\u0438\u043E\u0434. Read-only. ");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(7, "div", 6)(8, "div", 7)(9, "mat-form-field", 8)(10, "mat-label");
      \u0275\u0275text(11, "\u0413\u0440\u0443\u043F\u043F\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(12, "mat-select", 9);
      \u0275\u0275listener("selectionChange", function JournalPageComponent_Template_mat_select_selectionChange_12_listener($event) {
        \u0275\u0275restoreView(_r1);
        return \u0275\u0275resetView(ctx.onGroupChange($event.value));
      });
      \u0275\u0275repeaterCreate(13, JournalPageComponent_For_14_Template, 2, 2, "mat-option", 10, _forTrack03);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(15, "mat-form-field", 8)(16, "mat-label");
      \u0275\u0275text(17, "\u041F\u0440\u0435\u0434\u043C\u0435\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(18, "mat-select", 9);
      \u0275\u0275listener("selectionChange", function JournalPageComponent_Template_mat_select_selectionChange_18_listener($event) {
        \u0275\u0275restoreView(_r1);
        return \u0275\u0275resetView(ctx.selectedSubjectId.set($event.value));
      });
      \u0275\u0275repeaterCreate(19, JournalPageComponent_For_20_Template, 2, 2, "mat-option", 10, _forTrack03);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(21, "mat-form-field", 11)(22, "mat-label");
      \u0275\u0275text(23, "\u0421");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(24, "input", 12);
      \u0275\u0275listener("dateChange", function JournalPageComponent_Template_input_dateChange_24_listener($event) {
        let tmp_3_0;
        \u0275\u0275restoreView(_r1);
        return \u0275\u0275resetView(ctx.dateFrom.set((tmp_3_0 = $event.value == null ? null : $event.value.toISOString().slice(0, 10)) !== null && tmp_3_0 !== void 0 ? tmp_3_0 : ""));
      });
      \u0275\u0275elementEnd();
      \u0275\u0275element(25, "mat-datepicker-toggle", 13)(26, "mat-datepicker", null, 0);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(28, "mat-form-field", 11)(29, "mat-label");
      \u0275\u0275text(30, "\u041F\u043E");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(31, "input", 12);
      \u0275\u0275listener("dateChange", function JournalPageComponent_Template_input_dateChange_31_listener($event) {
        let tmp_3_0;
        \u0275\u0275restoreView(_r1);
        return \u0275\u0275resetView(ctx.dateTo.set((tmp_3_0 = $event.value == null ? null : $event.value.toISOString().slice(0, 10)) !== null && tmp_3_0 !== void 0 ? tmp_3_0 : ""));
      });
      \u0275\u0275elementEnd();
      \u0275\u0275element(32, "mat-datepicker-toggle", 13)(33, "mat-datepicker", null, 1);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(35, "button", 14);
      \u0275\u0275listener("click", function JournalPageComponent_Template_button_click_35_listener() {
        \u0275\u0275restoreView(_r1);
        return \u0275\u0275resetView(ctx.loadJournal());
      });
      \u0275\u0275element(36, "i", 15);
      \u0275\u0275text(37, " \u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u0436\u0443\u0440\u043D\u0430\u043B ");
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(38, JournalPageComponent_Conditional_38_Template, 2, 0, "p", 16)(39, JournalPageComponent_Conditional_39_Template, 5, 1, "div", 17)(40, JournalPageComponent_Conditional_40_Template, 8, 0, "div", 6)(41, JournalPageComponent_Conditional_41_Template, 3, 1);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      const pickerFrom_r6 = \u0275\u0275reference(27);
      const pickerTo_r7 = \u0275\u0275reference(34);
      \u0275\u0275advance(13);
      \u0275\u0275repeater(ctx.groups());
      \u0275\u0275advance(6);
      \u0275\u0275repeater(ctx.subjects());
      \u0275\u0275advance(5);
      \u0275\u0275property("matDatepicker", pickerFrom_r6)("value", ctx.dateFrom());
      \u0275\u0275advance();
      \u0275\u0275property("for", pickerFrom_r6);
      \u0275\u0275advance(6);
      \u0275\u0275property("matDatepicker", pickerTo_r7)("value", ctx.dateTo());
      \u0275\u0275advance();
      \u0275\u0275property("for", pickerTo_r7);
      \u0275\u0275advance(3);
      \u0275\u0275property("disabled", !ctx.canApply() || ctx.loading());
      \u0275\u0275advance(3);
      \u0275\u0275conditional(ctx.loading() ? 38 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.error() ? 39 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(!ctx.journalData() && !ctx.loading() && !ctx.error() ? 40 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.journalData() ? 41 : -1);
    }
  }, dependencies: [
    ReactiveFormsModule,
    MatSelectModule,
    MatFormField,
    MatLabel,
    MatSuffix,
    MatSelect,
    MatOption,
    MatDatepickerModule,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle,
    MatNativeDateModule,
    MatButtonModule,
    MatProgressBarModule,
    MatInputModule,
    MatInput,
    JournalGridComponent,
    StatusLegendComponent
  ], encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(JournalPageComponent, [{
    type: Component,
    args: [{ selector: "app-journal-page", standalone: true, imports: [
      ReactiveFormsModule,
      MatSelectModule,
      MatDatepickerModule,
      MatNativeDateModule,
      MatButtonModule,
      MatProgressBarModule,
      MatInputModule,
      JournalGridComponent,
      StatusLegendComponent
    ], template: `<div class="page-stack">
  <header class="page-header">
    <div class="page-header__title">
      <h1>\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438</h1>
      <p class="page-header__subtitle">
        \u041E\u0442\u043C\u0435\u0442\u043A\u0438 \u0433\u0440\u0443\u043F\u043F\u044B \u0437\u0430 \u0432\u044B\u0431\u0440\u0430\u043D\u043D\u044B\u0439 \u043F\u0435\u0440\u0438\u043E\u0434. Read-only.
      </p>
    </div>
  </header>

  <!-- Filters -->
  <div class="page-card">
    <div class="page-filters">
      <mat-form-field class="w-48">
        <mat-label>\u0413\u0440\u0443\u043F\u043F\u0430</mat-label>
        <mat-select (selectionChange)="onGroupChange($event.value)">
          @for (g of groups(); track g.id) {
            <mat-option [value]="g.id">{{ g.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <mat-form-field class="w-48">
        <mat-label>\u041F\u0440\u0435\u0434\u043C\u0435\u0442</mat-label>
        <mat-select (selectionChange)="selectedSubjectId.set($event.value)">
          @for (s of subjects(); track s.id) {
            <mat-option [value]="s.id">{{ s.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <mat-form-field class="w-40">
        <mat-label>\u0421</mat-label>
        <input matInput [matDatepicker]="pickerFrom" [value]="dateFrom()"
               (dateChange)="dateFrom.set($event.value?.toISOString().slice(0,10) ?? '')">
        <mat-datepicker-toggle matIconSuffix [for]="pickerFrom" />
        <mat-datepicker #pickerFrom />
      </mat-form-field>

      <mat-form-field class="w-40">
        <mat-label>\u041F\u043E</mat-label>
        <input matInput [matDatepicker]="pickerTo" [value]="dateTo()"
               (dateChange)="dateTo.set($event.value?.toISOString().slice(0,10) ?? '')">
        <mat-datepicker-toggle matIconSuffix [for]="pickerTo" />
        <mat-datepicker #pickerTo />
      </mat-form-field>

      <button type="button"
              class="btn-brand"
              [disabled]="!canApply() || loading()"
              (click)="loadJournal()">
        <i class="ph ph-magnifying-glass" aria-hidden="true"></i>
        \u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u0436\u0443\u0440\u043D\u0430\u043B
      </button>
    </div>
  </div>

  <!-- Loading -->
  @if (loading()) {
    <p class="text-sm" style="color: var(--text-secondary);">\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430 \u0436\u0443\u0440\u043D\u0430\u043B\u0430\u2026</p>
  }

  <!-- Error -->
  @if (error()) {
    <div class="page-error" role="alert">
      <i class="ph ph-warning-circle" aria-hidden="true"></i>
      {{ error() }}
      <button type="button" class="btn-brand" style="margin-left: var(--space-4);" (click)="loadJournal()">
        \u041F\u043E\u0432\u0442\u043E\u0440\u0438\u0442\u044C
      </button>
    </div>
  }

  <!-- Empty state -->
  @if (!journalData() && !loading() && !error()) {
    <div class="page-card">
      <div class="page-empty">
        <div class="page-empty__icon" aria-hidden="true">
          <i class="ph-duotone ph-book-open"></i>
        </div>
        <h3 class="page-empty__title">\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u0443\u0441\u0442</h3>
        <p class="page-empty__text">
          \u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0433\u0440\u0443\u043F\u043F\u0443 \u0438 \u043F\u0440\u0435\u0434\u043C\u0435\u0442, \u0447\u0442\u043E\u0431\u044B \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0436\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438.
        </p>
      </div>
    </div>
  }

  <!-- Grid -->
  @if (journalData()) {
    <div class="page-card page-card--flush">
      <app-journal-grid [journalData]="journalData()!" />
    </div>
    <app-status-legend />
  }
</div>
` }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(JournalPageComponent, { className: "JournalPageComponent", filePath: "src/app/features/teacher/journal/journal-page.component.ts", lineNumber: 35 });
})();
export {
  JournalPageComponent
};
//# sourceMappingURL=chunk-YFWDJONW.js.map
