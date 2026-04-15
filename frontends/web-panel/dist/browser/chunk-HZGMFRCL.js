import {
  HeadmanApiService
} from "./chunk-ZC6IXG25.js";
import {
  MatProgressSpinner,
  MatProgressSpinnerModule
} from "./chunk-VOF6QK53.js";
import {
  MatSnackBar
} from "./chunk-5Q6RVIG4.js";
import {
  addDays,
  formatDate,
  getMonday,
  isSameWeek
} from "./chunk-3SX4S54V.js";
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogActions,
  MatDialogContent,
  MatDialogModule,
  MatDialogRef,
  MatDialogTitle
} from "./chunk-6NLIVYJ4.js";
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
import {
  MatButton,
  MatButtonModule
} from "./chunk-GTDHNJL7.js";
import "./chunk-2WN7CIGJ.js";
import "./chunk-ZEX6QU7O.js";
import "./chunk-G4JX6AWT.js";
import "./chunk-YIJEORIR.js";
import "./chunk-FW2NJ6PM.js";
import "./chunk-FLE4DLW4.js";
import {
  CommonModule
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
  ɵɵattribute,
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
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1,
  ɵɵtextInterpolate2
} from "./chunk-4M4FDLBS.js";

// src/app/features/headman/weekly-journal/bulk-confirm-dialog.component.ts
var BulkConfirmDialogComponent = class _BulkConfirmDialogComponent {
  data = inject(MAT_DIALOG_DATA);
  ref = inject(MatDialogRef);
  static \u0275fac = function BulkConfirmDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _BulkConfirmDialogComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _BulkConfirmDialogComponent, selectors: [["app-bulk-confirm-dialog"]], decls: 10, vars: 2, consts: [["mat-dialog-title", ""], ["align", "end"], ["mat-button", "", 3, "click"], ["mat-flat-button", "", "color", "primary", 3, "click"]], template: function BulkConfirmDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "h2", 0);
      \u0275\u0275text(1);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(2, "mat-dialog-content")(3, "p");
      \u0275\u0275text(4);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(5, "mat-dialog-actions", 1)(6, "button", 2);
      \u0275\u0275listener("click", function BulkConfirmDialogComponent_Template_button_click_6_listener() {
        return ctx.ref.close(false);
      });
      \u0275\u0275text(7, "\u041E\u0442\u043C\u0435\u043D\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(8, "button", 3);
      \u0275\u0275listener("click", function BulkConfirmDialogComponent_Template_button_click_8_listener() {
        return ctx.ref.close(true);
      });
      \u0275\u0275text(9, "\u041F\u0440\u043E\u0441\u0442\u0430\u0432\u0438\u0442\u044C");
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275advance();
      \u0275\u0275textInterpolate(ctx.data.title);
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.data.message);
    }
  }, dependencies: [MatDialogModule, MatDialogTitle, MatDialogActions, MatDialogContent, MatButtonModule, MatButton], encapsulation: 2, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(BulkConfirmDialogComponent, [{
    type: Component,
    args: [{
      selector: "app-bulk-confirm-dialog",
      standalone: true,
      changeDetection: ChangeDetectionStrategy.OnPush,
      imports: [MatDialogModule, MatButtonModule],
      template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="ref.close(false)">\u041E\u0442\u043C\u0435\u043D\u0430</button>
      <button mat-flat-button color="primary" (click)="ref.close(true)">\u041F\u0440\u043E\u0441\u0442\u0430\u0432\u0438\u0442\u044C</button>
    </mat-dialog-actions>
  `
    }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(BulkConfirmDialogComponent, { className: "BulkConfirmDialogComponent", filePath: "src/app/features/headman/weekly-journal/bulk-confirm-dialog.component.ts", lineNumber: 26 });
})();

// src/app/features/headman/weekly-journal/headman-weekly-journal.component.ts
var _forTrack0 = ($index, $item) => $item.date;
var _forTrack1 = ($index, $item) => $item.userId;
var _forTrack2 = ($index, $item) => $item.lessonId;
function HeadmanWeeklyJournalComponent_Conditional_17_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 12);
    \u0275\u0275element(1, "mat-spinner", 16);
    \u0275\u0275elementEnd();
  }
}
function HeadmanWeeklyJournalComponent_Conditional_18_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 13);
    \u0275\u0275element(1, "i", 17);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.error(), "");
  }
}
function HeadmanWeeklyJournalComponent_Conditional_19_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 14);
    \u0275\u0275element(1, "i", 18);
    \u0275\u0275elementStart(2, "h3");
    \u0275\u0275text(3, "\u041D\u0430 \u044D\u0442\u043E\u0439 \u043D\u0435\u0434\u0435\u043B\u0435 \u043D\u0435\u0442 \u0437\u0430\u043D\u044F\u0442\u0438\u0439");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p");
    \u0275\u0275text(5, "\u041F\u0435\u0440\u0435\u0439\u0434\u0438\u0442\u0435 \u043A \u0434\u0440\u0443\u0433\u043E\u0439 \u043D\u0435\u0434\u0435\u043B\u0435, \u0447\u0442\u043E\u0431\u044B \u043F\u0440\u043E\u0434\u043E\u043B\u0436\u0438\u0442\u044C \u0440\u0430\u0431\u043E\u0442\u0443 \u0441 \u0436\u0443\u0440\u043D\u0430\u043B\u043E\u043C.");
    \u0275\u0275elementEnd()();
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_For_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "th", 26)(1, "span", 27);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "span", 28);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const day_r2 = ctx.$implicit;
    const \u0275$index_65_r3 = ctx.$index;
    const \u0275$count_65_r4 = ctx.$count;
    \u0275\u0275classProp("col-day--last", !(\u0275$index_65_r3 === \u0275$count_65_r4 - 1));
    \u0275\u0275attribute("colspan", day_r2.columns.length * 3);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(day_r2.weekday);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(day_r2.displayDate);
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_For_10_For_1_Template(rf, ctx) {
  if (rf & 1) {
    const _r5 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "th", 30)(1, "div", 31)(2, "span", 32);
    \u0275\u0275text(3);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "span", 33);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(6, "div", 34)(7, "button", 35);
    \u0275\u0275listener("click", function HeadmanWeeklyJournalComponent_Conditional_20_For_10_For_1_Template_button_click_7_listener() {
      const col_r6 = \u0275\u0275restoreView(_r5).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(3);
      return \u0275\u0275resetView(ctx_r0.onBulkMark(col_r6, "present"));
    });
    \u0275\u0275text(8, "+");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(9, "button", 36);
    \u0275\u0275listener("click", function HeadmanWeeklyJournalComponent_Conditional_20_For_10_For_1_Template_button_click_9_listener() {
      const col_r6 = \u0275\u0275restoreView(_r5).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(3);
      return \u0275\u0275resetView(ctx_r0.onBulkMark(col_r6, "absent"));
    });
    \u0275\u0275text(10, "\u041D");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(11, "button", 37);
    \u0275\u0275listener("click", function HeadmanWeeklyJournalComponent_Conditional_20_For_10_For_1_Template_button_click_11_listener() {
      const col_r6 = \u0275\u0275restoreView(_r5).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(3);
      return \u0275\u0275resetView(ctx_r0.onBulkMark(col_r6, "excused"));
    });
    \u0275\u0275text(12, "\u0423");
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const col_r6 = ctx.$implicit;
    const \u0275$index_77_r7 = ctx.$index;
    const \u0275$count_77_r8 = ctx.$count;
    const ctx_r8 = \u0275\u0275nextContext();
    const \u0275$index_76_r10 = ctx_r8.$index;
    const \u0275$count_76_r11 = ctx_r8.$count;
    \u0275\u0275classProp("col-subject--day-end", \u0275$index_77_r7 === \u0275$count_77_r8 - 1 && !(\u0275$index_76_r10 === \u0275$count_76_r11 - 1));
    \u0275\u0275advance();
    \u0275\u0275property("title", col_r6.subjectName + " \xA7 " + col_r6.lessonNumber + " " + col_r6.startTime);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(col_r6.subjectName);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate2("\xA7 ", col_r6.lessonNumber, " \xB7 ", col_r6.startTime, "");
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_For_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275repeaterCreate(0, HeadmanWeeklyJournalComponent_Conditional_20_For_10_For_1_Template, 13, 6, "th", 29, _forTrack2);
  }
  if (rf & 2) {
    const day_r12 = ctx.$implicit;
    \u0275\u0275repeater(day_r12.columns);
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_For_13_For_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "th", 38);
    \u0275\u0275text(1, "+");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(2, "th", 39);
    \u0275\u0275text(3, "\u041D");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "th", 39);
    \u0275\u0275text(5, "\u0423");
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const \u0275$index_102_r13 = ctx.$index;
    const \u0275$count_102_r14 = ctx.$count;
    const ctx_r14 = \u0275\u0275nextContext();
    const \u0275$index_101_r16 = ctx_r14.$index;
    const \u0275$count_101_r17 = ctx_r14.$count;
    \u0275\u0275advance(4);
    \u0275\u0275classProp("col-status--day-end", \u0275$index_102_r13 === \u0275$count_102_r14 - 1 && !(\u0275$index_101_r16 === \u0275$count_101_r17 - 1));
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_For_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275repeaterCreate(0, HeadmanWeeklyJournalComponent_Conditional_20_For_13_For_1_Template, 6, 2, null, null, _forTrack2);
  }
  if (rf & 2) {
    const day_r18 = ctx.$implicit;
    \u0275\u0275repeater(day_r18.columns);
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_For_16_For_4_For_1_Template(rf, ctx) {
  if (rf & 1) {
    const _r19 = \u0275\u0275getCurrentView();
    \u0275\u0275declareLet(0)(1)(2);
    \u0275\u0275elementStart(3, "td", 41)(4, "button", 42);
    \u0275\u0275listener("click", function HeadmanWeeklyJournalComponent_Conditional_20_For_16_For_4_For_1_Template_button_click_4_listener() {
      const col_r20 = \u0275\u0275restoreView(_r19).$implicit;
      const student_r21 = \u0275\u0275nextContext(2).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onStatusClick(col_r20.lessonId, student_r21.userId, "present"));
    });
    \u0275\u0275text(5, "+");
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(6, "td", 41)(7, "button", 43);
    \u0275\u0275listener("click", function HeadmanWeeklyJournalComponent_Conditional_20_For_16_For_4_For_1_Template_button_click_7_listener() {
      const col_r20 = \u0275\u0275restoreView(_r19).$implicit;
      const student_r21 = \u0275\u0275nextContext(2).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onStatusClick(col_r20.lessonId, student_r21.userId, "absent"));
    });
    \u0275\u0275text(8, "\u041D");
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(9, "td", 41)(10, "button", 44);
    \u0275\u0275listener("click", function HeadmanWeeklyJournalComponent_Conditional_20_For_16_For_4_For_1_Template_button_click_10_listener() {
      const col_r20 = \u0275\u0275restoreView(_r19).$implicit;
      const student_r21 = \u0275\u0275nextContext(2).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onStatusClick(col_r20.lessonId, student_r21.userId, "excused"));
    });
    \u0275\u0275text(11, "\u0423");
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const col_r20 = ctx.$implicit;
    const \u0275$index_121_r22 = ctx.$index;
    const \u0275$count_121_r23 = ctx.$count;
    const ctx_r23 = \u0275\u0275nextContext();
    const \u0275$index_120_r25 = ctx_r23.$index;
    const \u0275$count_120_r26 = ctx_r23.$count;
    const student_r21 = \u0275\u0275nextContext().$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    const status_r27 = ctx_r0.getStatus(col_r20.lessonId, student_r21.userId);
    const saving_r28 = ctx_r0.isSaving(col_r20.lessonId, student_r21.userId);
    const flashing_r29 = ctx_r0.isFlashing(col_r20.lessonId, student_r21.userId);
    \u0275\u0275advance(3);
    \u0275\u0275classProp("cell--saving", saving_r28)("cell--flash", flashing_r29);
    \u0275\u0275advance();
    \u0275\u0275classProp("dot--active", status_r27 === "present");
    \u0275\u0275attribute("aria-label", "\u041F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B \u2014 " + student_r21.displayName)("aria-pressed", status_r27 === "present");
    \u0275\u0275advance(2);
    \u0275\u0275classProp("cell--saving", saving_r28)("cell--flash", flashing_r29);
    \u0275\u0275advance();
    \u0275\u0275classProp("dot--active", status_r27 === "absent");
    \u0275\u0275attribute("aria-label", "\u041D\u0435 \u0431\u044B\u043B \u2014 " + student_r21.displayName)("aria-pressed", status_r27 === "absent");
    \u0275\u0275advance(2);
    \u0275\u0275classProp("cell--saving", saving_r28)("cell--flash", flashing_r29)("cell--day-end", \u0275$index_121_r22 === \u0275$count_121_r23 - 1 && !(\u0275$index_120_r25 === \u0275$count_120_r26 - 1));
    \u0275\u0275advance();
    \u0275\u0275classProp("dot--active", status_r27 === "excused");
    \u0275\u0275attribute("aria-label", "\u0423\u0432\u0430\u0436\u0438\u0442. \u043F\u0440\u0438\u0447\u0438\u043D\u0430 \u2014 " + student_r21.displayName)("aria-pressed", status_r27 === "excused");
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_For_16_For_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275repeaterCreate(0, HeadmanWeeklyJournalComponent_Conditional_20_For_16_For_4_For_1_Template, 12, 26, null, null, _forTrack2);
  }
  if (rf & 2) {
    const day_r30 = ctx.$implicit;
    \u0275\u0275repeater(day_r30.columns);
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_For_16_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "tr", 25)(1, "td", 40);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275repeaterCreate(3, HeadmanWeeklyJournalComponent_Conditional_20_For_16_For_4_Template, 2, 0, null, null, _forTrack0);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const student_r21 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275property("title", student_r21.displayName);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(student_r21.displayName);
    \u0275\u0275advance();
    \u0275\u0275repeater(ctx_r0.dayGroups());
  }
}
function HeadmanWeeklyJournalComponent_Conditional_20_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 15)(1, "table", 19)(2, "thead")(3, "tr", 20)(4, "th", 21);
    \u0275\u0275text(5, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
    \u0275\u0275elementEnd();
    \u0275\u0275repeaterCreate(6, HeadmanWeeklyJournalComponent_Conditional_20_For_7_Template, 5, 5, "th", 22, _forTrack0);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(8, "tr", 23);
    \u0275\u0275repeaterCreate(9, HeadmanWeeklyJournalComponent_Conditional_20_For_10_Template, 2, 0, null, null, _forTrack0);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(11, "tr", 24);
    \u0275\u0275repeaterCreate(12, HeadmanWeeklyJournalComponent_Conditional_20_For_13_Template, 2, 0, null, null, _forTrack0);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(14, "tbody");
    \u0275\u0275repeaterCreate(15, HeadmanWeeklyJournalComponent_Conditional_20_For_16_Template, 5, 2, "tr", 25, _forTrack1);
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(6);
    \u0275\u0275repeater(ctx_r0.dayGroups());
    \u0275\u0275advance(3);
    \u0275\u0275repeater(ctx_r0.dayGroups());
    \u0275\u0275advance(3);
    \u0275\u0275repeater(ctx_r0.dayGroups());
    \u0275\u0275advance(3);
    \u0275\u0275repeater(ctx_r0.sortedStudents());
  }
}
var WEEKDAY_LABELS = ["\u0412\u0421", "\u041F\u041D", "\u0412\u0422", "\u0421\u0420", "\u0427\u0422", "\u041F\u0422", "\u0421\u0411"];
var HeadmanWeeklyJournalComponent = class _HeadmanWeeklyJournalComponent {
  headmanApi = inject(HeadmanApiService);
  auth = inject(AuthService);
  snackBar = inject(MatSnackBar);
  dialog = inject(MatDialog);
  loading = signal(true);
  error = signal(null);
  monday = signal(getMonday(/* @__PURE__ */ new Date()));
  students = signal([]);
  lessons = signal([]);
  subjects = signal(/* @__PURE__ */ new Map());
  /** key = `${lessonId}_${userId}` -> status */
  statusMap = signal(/* @__PURE__ */ new Map());
  /** cells currently saving (visual feedback) */
  savingCells = signal(/* @__PURE__ */ new Set());
  /** cells that just saved successfully — flash indicator */
  flashCells = signal(/* @__PURE__ */ new Set());
  weekRange = computed(() => {
    const mon = this.monday();
    const sun = addDays(mon, 6);
    const fmt = (d) => `${String(d.getDate()).padStart(2, "0")}.${String(d.getMonth() + 1).padStart(2, "0")}`;
    return `${fmt(mon)} \u2014 ${fmt(sun)}`;
  });
  isCurrentWeek = computed(() => isSameWeek(this.monday(), /* @__PURE__ */ new Date()));
  dayGroups = computed(() => {
    const monday = this.monday();
    const subjects = this.subjects();
    const byDate = /* @__PURE__ */ new Map();
    for (const l of this.lessons()) {
      const arr = byDate.get(l.date) ?? [];
      arr.push(l);
      byDate.set(l.date, arr);
    }
    const groups = [];
    for (let i = 0; i < 7; i++) {
      const d = addDays(monday, i);
      const iso = formatDate(d);
      const dayLessons = byDate.get(iso);
      if (!dayLessons || dayLessons.length === 0)
        continue;
      const sorted = dayLessons.slice().sort((a, b) => {
        if (a.lessonNumber !== b.lessonNumber)
          return a.lessonNumber - b.lessonNumber;
        return a.startTime.localeCompare(b.startTime);
      });
      const columns = sorted.map((l) => ({
        lessonId: l.id,
        subjectId: l.subjectId,
        subjectName: subjects.get(l.subjectId) ?? `\u041F\u0440\u0435\u0434\u043C\u0435\u0442 #${l.subjectId}`,
        lessonNumber: l.lessonNumber,
        startTime: (l.startTime ?? "").slice(0, 5),
        date: l.date,
        header: `${(subjects.get(l.subjectId) ?? `#${l.subjectId}`).toUpperCase()} \xA7 ${l.lessonNumber} ${(l.startTime ?? "").slice(0, 5)}`
      }));
      groups.push({
        date: iso,
        weekday: WEEKDAY_LABELS[d.getDay()],
        displayDate: `${String(d.getDate()).padStart(2, "0")}.${String(d.getMonth() + 1).padStart(2, "0")}`,
        columns
      });
    }
    return groups;
  });
  sortedStudents = computed(() => {
    return this.students().slice().sort((a, b) => a.displayName.localeCompare(b.displayName, "ru"));
  });
  hasLessons = computed(() => this.dayGroups().length > 0);
  ngOnInit() {
    const user = this.auth.currentUser();
    if (!user?.groupId) {
      this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u043F\u0440\u0435\u0434\u0435\u043B\u0438\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443. \u041E\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044C \u043A \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440\u0443.");
      this.loading.set(false);
      return;
    }
    this.loadSubjects();
    this.loadWeek();
  }
  loadSubjects() {
    this.headmanApi.listSubjects(0, 200).pipe(catchError(() => of(null))).subscribe((resp) => {
      if (!resp?._embedded)
        return;
      const list = Object.values(resp._embedded)[0] ?? [];
      const map = /* @__PURE__ */ new Map();
      for (const s of list)
        map.set(s.id, s.name);
      this.subjects.set(map);
    });
  }
  loadWeek() {
    const user = this.auth.currentUser();
    if (!user?.groupId)
      return;
    const groupId = user.groupId;
    this.loading.set(true);
    this.error.set(null);
    const mon = this.monday();
    const sun = addDays(mon, 6);
    const dateFrom = formatDate(mon);
    const dateTo = formatDate(sun);
    forkJoin({
      members: this.headmanApi.getGroupMembers(0, 200).pipe(catchError(() => of(null))),
      lessons: this.headmanApi.getGroupLessons(groupId, dateFrom, dateTo).pipe(catchError(() => of(null)))
    }).subscribe(({ members, lessons }) => {
      if (members?._embedded) {
        const list = Object.values(members._embedded)[0] ?? [];
        this.students.set(list.map((m) => ({
          userId: m.userId ?? m.id,
          displayName: m.fullName ?? m.displayName ?? `#${m.userId ?? m.id}`
        })));
      } else {
        this.students.set([]);
      }
      const embedded = lessons?._embedded;
      const rawLessons = embedded ? Object.values(embedded)[0] ?? [] : [];
      const normalised = rawLessons.map((entry) => entry?.content ?? entry).filter(Boolean);
      this.lessons.set(normalised);
      if (normalised.length === 0) {
        this.statusMap.set(/* @__PURE__ */ new Map());
        this.loading.set(false);
        return;
      }
      forkJoin(normalised.map((l) => this.headmanApi.getLessonAttendance(l.id).pipe(catchError(() => of(null))))).subscribe((results) => {
        const map = /* @__PURE__ */ new Map();
        for (let i = 0; i < normalised.length; i++) {
          const lesson = normalised[i];
          const resp = results[i];
          const payload = resp?.content ?? resp;
          const entries = payload?.entries ?? [];
          for (const e of entries) {
            const status = (e.status ?? "").toLowerCase();
            if (!status)
              continue;
            map.set(`${lesson.id}_${e.userId}`, status);
          }
        }
        this.statusMap.set(map);
        this.loading.set(false);
      });
    });
  }
  prevWeek() {
    this.monday.set(addDays(this.monday(), -7));
    this.loadWeek();
  }
  nextWeek() {
    if (this.isCurrentWeek())
      return;
    const next = addDays(this.monday(), 7);
    const currentMonday = getMonday(/* @__PURE__ */ new Date());
    if (next.getTime() > currentMonday.getTime()) {
      this.monday.set(currentMonday);
    } else {
      this.monday.set(next);
    }
    this.loadWeek();
  }
  getStatus(lessonId, userId) {
    return this.statusMap().get(`${lessonId}_${userId}`);
  }
  isSaving(lessonId, userId) {
    return this.savingCells().has(`${lessonId}_${userId}`);
  }
  isFlashing(lessonId, userId) {
    return this.flashCells().has(`${lessonId}_${userId}`);
  }
  onStatusClick(lessonId, userId, next) {
    const key = `${lessonId}_${userId}`;
    const current = this.statusMap().get(key);
    if (current === next)
      return;
    const prev = current;
    const map = new Map(this.statusMap());
    map.set(key, next);
    this.statusMap.set(map);
    const saving = new Set(this.savingCells());
    saving.add(key);
    this.savingCells.set(saving);
    this.headmanApi.markAttendance(lessonId, userId, next).pipe(catchError(() => {
      const rollback = new Map(this.statusMap());
      if (prev)
        rollback.set(key, prev);
      else
        rollback.delete(key);
      this.statusMap.set(rollback);
      this.snackBar.open("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0441\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C. \u041F\u043E\u0432\u0442\u043E\u0440\u0438\u0442\u0435.", void 0, { duration: 4e3 });
      return of(null);
    })).subscribe((result) => {
      const s = new Set(this.savingCells());
      s.delete(key);
      this.savingCells.set(s);
      if (result !== null) {
        const f = new Set(this.flashCells());
        f.add(key);
        this.flashCells.set(f);
        setTimeout(() => {
          const f2 = new Set(this.flashCells());
          f2.delete(key);
          this.flashCells.set(f2);
        }, 600);
      }
    });
  }
  onBulkMark(col, status) {
    const day = this.dayGroups().find((g) => g.date === col.date);
    const weekday = day?.weekday ?? "";
    const statusLabel = status === "present" ? "\xAB+\xBB" : status === "absent" ? "\xAB\u041D\xBB" : "\xAB\u0423\xBB";
    const data = {
      title: `\u041F\u0440\u043E\u0441\u0442\u0430\u0432\u0438\u0442\u044C ${statusLabel} \u0432\u0441\u0435\u043C?`,
      message: `${col.subjectName} (${weekday} ${day?.displayDate ?? ""}) \u2014 ${col.startTime}`
    };
    const ref = this.dialog.open(BulkConfirmDialogComponent, { data, width: "420px" });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed)
        return;
      this.applyBulk(col.lessonId, status);
    });
  }
  applyBulk(lessonId, status) {
    const studs = this.sortedStudents();
    const targets = [];
    const map = new Map(this.statusMap());
    for (const s of studs) {
      const key = `${lessonId}_${s.userId}`;
      const prev = map.get(key);
      if (prev === "cancelled")
        continue;
      if (prev === status)
        continue;
      targets.push({ userId: s.userId, prev });
      map.set(key, status);
    }
    if (targets.length === 0)
      return;
    this.statusMap.set(map);
    forkJoin(targets.map((t) => this.headmanApi.markAttendance(lessonId, t.userId, status).pipe(catchError(() => of({ failed: true, userId: t.userId, prev: t.prev }))))).subscribe((results) => {
      const failed = results.filter((r) => r?.failed);
      if (failed.length > 0) {
        const rollback = new Map(this.statusMap());
        for (const f of failed) {
          const key = `${lessonId}_${f.userId}`;
          if (f.prev)
            rollback.set(key, f.prev);
          else
            rollback.delete(key);
        }
        this.statusMap.set(rollback);
        this.snackBar.open(`\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u0431\u043D\u043E\u0432\u0438\u0442\u044C ${failed.length} ${failed.length === 1 ? "\u0437\u0430\u043F\u0438\u0441\u044C" : "\u0437\u0430\u043F\u0438\u0441\u0435\u0439"}.`, void 0, { duration: 4e3 });
      } else {
        this.snackBar.open("\u0421\u0442\u0430\u0442\u0443\u0441\u044B \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u044B.", void 0, { duration: 2e3 });
      }
    });
  }
  static \u0275fac = function HeadmanWeeklyJournalComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanWeeklyJournalComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanWeeklyJournalComponent, selectors: [["app-headman-weekly-journal"]], decls: 21, vars: 5, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-eyebrow"], [1, "page-title"], [1, "page-subtitle"], ["role", "navigation", "aria-label", "\u041D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F \u043F\u043E \u043D\u0435\u0434\u0435\u043B\u044F\u043C", 1, "week-nav"], ["type", "button", "aria-label", "\u041F\u0440\u0435\u0434\u044B\u0434\u0443\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F", 1, "week-nav__btn", 3, "click", "disabled"], [1, "ph", "ph-caret-left"], ["aria-live", "polite", 1, "week-nav__range"], ["type", "button", "aria-label", "\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F", 1, "week-nav__btn", 3, "click", "disabled"], [1, "ph", "ph-caret-right"], [1, "page-card", "page-card--flush"], [1, "loader"], [1, "page-error"], [1, "page-empty"], [1, "grid-wrapper"], ["diameter", "32"], [1, "ph", "ph-warning-circle"], [1, "ph-duotone", "ph-calendar-blank"], [1, "journal-table"], [1, "row-day-header"], ["rowspan", "3", 1, "col-student", "col-student--head"], [1, "col-day", 3, "col-day--last"], [1, "row-subject-header"], [1, "row-status-header"], [1, "row-student"], [1, "col-day"], [1, "col-day__weekday"], [1, "col-day__date"], ["colspan", "3", 1, "col-subject", 3, "col-subject--day-end"], ["colspan", "3", 1, "col-subject"], [1, "col-subject__text", 3, "title"], [1, "col-subject__name"], [1, "col-subject__meta"], [1, "bulk-row"], ["type", "button", "title", "\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C \u0432\u0441\u0435\u043C +", 1, "bulk-btn", "bulk-btn--present", 3, "click"], ["type", "button", "title", "\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C \u0432\u0441\u0435\u043C \u041D", 1, "bulk-btn", "bulk-btn--absent", 3, "click"], ["type", "button", "title", "\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C \u0432\u0441\u0435\u043C \u0423", 1, "bulk-btn", "bulk-btn--excused", 3, "click"], [1, "col-status", "col-status--plus"], [1, "col-status"], [1, "col-student", 3, "title"], [1, "cell"], ["type", "button", 1, "dot", "dot--present", 3, "click"], ["type", "button", 1, "dot", "dot--absent", 3, "click"], ["type", "button", 1, "dot", "dot--excused", 3, "click"]], template: function HeadmanWeeklyJournalComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "div")(3, "span", 2);
      \u0275\u0275text(4, "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "h1", 3);
      \u0275\u0275text(6, "\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(7, "p", 4);
      \u0275\u0275text(8, "\u041E\u0442\u043C\u0435\u0442\u044C\u0442\u0435 \u043F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u0438\u0435 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(9, "div", 5)(10, "button", 6);
      \u0275\u0275listener("click", function HeadmanWeeklyJournalComponent_Template_button_click_10_listener() {
        return ctx.prevWeek();
      });
      \u0275\u0275element(11, "i", 7);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(12, "span", 8);
      \u0275\u0275text(13);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(14, "button", 9);
      \u0275\u0275listener("click", function HeadmanWeeklyJournalComponent_Template_button_click_14_listener() {
        return ctx.nextWeek();
      });
      \u0275\u0275element(15, "i", 10);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(16, "div", 11);
      \u0275\u0275template(17, HeadmanWeeklyJournalComponent_Conditional_17_Template, 2, 0, "div", 12)(18, HeadmanWeeklyJournalComponent_Conditional_18_Template, 3, 1, "div", 13)(19, HeadmanWeeklyJournalComponent_Conditional_19_Template, 6, 0, "div", 14)(20, HeadmanWeeklyJournalComponent_Conditional_20_Template, 17, 0, "div", 15);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(10);
      \u0275\u0275property("disabled", ctx.loading());
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.weekRange());
      \u0275\u0275advance();
      \u0275\u0275property("disabled", ctx.loading() || ctx.isCurrentWeek());
      \u0275\u0275advance(3);
      \u0275\u0275conditional(ctx.loading() ? 17 : ctx.error() ? 18 : !ctx.hasLessons() ? 19 : 20);
    }
  }, dependencies: [CommonModule, MatProgressSpinnerModule, MatProgressSpinner, MatButtonModule, MatDialogModule], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.page-subtitle[_ngcontent-%COMP%] {\n  margin: 4px 0 0;\n  color: var(--mat-sys-on-surface-variant);\n  font-size: 14px;\n}\n.week-nav[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  gap: 16px;\n  padding: 8px 12px;\n  background: var(--mat-sys-surface-container);\n  border: 1px solid var(--mat-sys-outline-variant);\n  border-radius: 12px;\n  align-self: center;\n  width: fit-content;\n  margin: 0 auto;\n}\n.week-nav__btn[_ngcontent-%COMP%] {\n  border: 1px solid var(--mat-sys-outline-variant);\n  background: var(--mat-sys-surface);\n  width: 36px;\n  height: 36px;\n  border-radius: 50%;\n  cursor: pointer;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  color: var(--mat-sys-on-surface);\n  transition: background 0.12s ease;\n}\n.week-nav__btn[_ngcontent-%COMP%]:hover:not(:disabled) {\n  background: var(--mat-sys-surface-variant);\n}\n.week-nav__btn[_ngcontent-%COMP%]:disabled {\n  opacity: 0.4;\n  cursor: not-allowed;\n}\n.week-nav__range[_ngcontent-%COMP%] {\n  font-weight: 600;\n  font-size: 15px;\n  min-width: 140px;\n  text-align: center;\n  color: var(--mat-sys-on-surface);\n}\n.loader[_ngcontent-%COMP%] {\n  display: flex;\n  justify-content: center;\n  padding: 48px 16px;\n}\n.grid-wrapper[_ngcontent-%COMP%] {\n  overflow: auto;\n  max-height: calc(100vh - 280px);\n  border-radius: 8px;\n  position: relative;\n}\n.journal-table[_ngcontent-%COMP%] {\n  border-collapse: separate;\n  border-spacing: 0;\n  width: max-content;\n  min-width: 100%;\n  font-size: 13px;\n}\n.col-student[_ngcontent-%COMP%] {\n  position: sticky;\n  left: 0;\n  z-index: 3;\n  width: 200px;\n  min-width: 200px;\n  max-width: 200px;\n  padding: 8px 12px;\n  background: var(--mat-sys-surface);\n  border-right: 2px solid var(--mat-sys-outline-variant);\n  text-align: left;\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  vertical-align: middle;\n}\n.col-student--head[_ngcontent-%COMP%] {\n  background: var(--mat-sys-surface-container);\n  z-index: 6;\n  font-weight: 600;\n}\nthead[_ngcontent-%COMP%]   tr.row-day-header[_ngcontent-%COMP%]   th[_ngcontent-%COMP%], \nthead[_ngcontent-%COMP%]   tr.row-subject-header[_ngcontent-%COMP%]   th[_ngcontent-%COMP%], \nthead[_ngcontent-%COMP%]   tr.row-status-header[_ngcontent-%COMP%]   th[_ngcontent-%COMP%] {\n  position: sticky;\n  background: var(--mat-sys-surface-container);\n  z-index: 2;\n}\nthead[_ngcontent-%COMP%]   tr.row-day-header[_ngcontent-%COMP%]   th[_ngcontent-%COMP%] {\n  top: 0;\n}\nthead[_ngcontent-%COMP%]   tr.row-subject-header[_ngcontent-%COMP%]   th[_ngcontent-%COMP%] {\n  top: 38px;\n}\nthead[_ngcontent-%COMP%]   tr.row-status-header[_ngcontent-%COMP%]   th[_ngcontent-%COMP%] {\n  top: 172px;\n}\nthead[_ngcontent-%COMP%]   tr.row-day-header[_ngcontent-%COMP%]   th.col-student--head[_ngcontent-%COMP%], \nthead[_ngcontent-%COMP%]   tr.row-subject-header[_ngcontent-%COMP%]   th.col-student--head[_ngcontent-%COMP%], \nthead[_ngcontent-%COMP%]   tr.row-status-header[_ngcontent-%COMP%]   th.col-student--head[_ngcontent-%COMP%] {\n  z-index: 7;\n}\n.col-day[_ngcontent-%COMP%] {\n  padding: 8px 6px;\n  font-size: 13px;\n  font-weight: 700;\n  text-align: center;\n  border-bottom: 1px solid var(--mat-sys-outline-variant);\n  border-right: 2px solid var(--mat-sys-outline-variant);\n  color: var(--mat-sys-on-surface);\n  background: var(--mat-sys-surface-container-high);\n}\n.col-day__weekday[_ngcontent-%COMP%] {\n  display: inline-block;\n  margin-right: 6px;\n  opacity: 0.7;\n  font-weight: 600;\n}\n.col-day__date[_ngcontent-%COMP%] {\n  font-weight: 700;\n}\n.col-subject[_ngcontent-%COMP%] {\n  padding: 6px 2px 4px;\n  min-width: 126px;\n  width: 126px;\n  background: var(--mat-sys-surface-container);\n  border-bottom: 1px solid var(--mat-sys-outline-variant);\n  vertical-align: bottom;\n  height: 134px;\n}\n.col-subject--day-end[_ngcontent-%COMP%] {\n  border-right: 2px solid var(--mat-sys-outline-variant);\n}\n.col-subject__text[_ngcontent-%COMP%] {\n  writing-mode: vertical-rl;\n  transform: rotate(180deg);\n  display: flex;\n  flex-direction: column;\n  justify-content: flex-start;\n  align-items: center;\n  font-size: 11.5px;\n  line-height: 1.15;\n  text-align: left;\n  height: 108px;\n  margin: 0 auto;\n  gap: 4px;\n}\n.col-subject__name[_ngcontent-%COMP%] {\n  font-weight: 700;\n  text-transform: uppercase;\n  letter-spacing: 0.3px;\n  max-height: 82px;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n}\n.col-subject__meta[_ngcontent-%COMP%] {\n  font-weight: 500;\n  font-size: 10.5px;\n  opacity: 0.7;\n}\n.bulk-row[_ngcontent-%COMP%] {\n  display: flex;\n  gap: 3px;\n  justify-content: center;\n  margin-top: 4px;\n  padding: 0 6px;\n}\n.bulk-btn[_ngcontent-%COMP%] {\n  border: 1px solid var(--mat-sys-outline-variant);\n  background: var(--mat-sys-surface);\n  width: 22px;\n  height: 20px;\n  padding: 0;\n  border-radius: 4px;\n  font-size: 10px;\n  font-weight: 700;\n  cursor: pointer;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  transition: transform 0.12s ease, background 0.12s ease;\n}\n.bulk-btn[_ngcontent-%COMP%]:hover {\n  transform: scale(1.12);\n}\n.bulk-btn--present[_ngcontent-%COMP%] {\n  color: #2e7d32;\n}\n.bulk-btn--absent[_ngcontent-%COMP%] {\n  color: #c62828;\n}\n.bulk-btn--excused[_ngcontent-%COMP%] {\n  color: #b45309;\n}\n.col-status[_ngcontent-%COMP%] {\n  width: 42px;\n  min-width: 42px;\n  max-width: 42px;\n  padding: 4px 0;\n  text-align: center;\n  font-weight: 700;\n  font-size: 12px;\n  color: var(--mat-sys-on-surface-variant);\n  background: var(--mat-sys-surface-container);\n  border-bottom: 1px solid var(--mat-sys-outline-variant);\n}\n.col-status--day-end[_ngcontent-%COMP%] {\n  border-right: 2px solid var(--mat-sys-outline-variant);\n}\n.row-student[_ngcontent-%COMP%] {\n  transition: background 0.1s ease;\n}\n.row-student[_ngcontent-%COMP%]:hover {\n  background: color-mix(in srgb, var(--mat-sys-primary) 5%, transparent);\n}\n.row-student[_ngcontent-%COMP%]:hover   .col-student[_ngcontent-%COMP%] {\n  background: color-mix(in srgb, var(--mat-sys-primary) 7%, var(--mat-sys-surface));\n}\n.cell[_ngcontent-%COMP%] {\n  width: 42px;\n  min-width: 42px;\n  max-width: 42px;\n  padding: 4px 0;\n  text-align: center;\n  border-bottom: 1px solid var(--mat-sys-outline-variant);\n  vertical-align: middle;\n  transition: box-shadow 0.4s ease;\n}\n.cell--day-end[_ngcontent-%COMP%] {\n  border-right: 2px solid var(--mat-sys-outline-variant);\n}\n.cell--saving[_ngcontent-%COMP%] {\n  opacity: 0.6;\n}\n.cell--flash[_ngcontent-%COMP%] {\n  box-shadow: inset 0 0 0 2px color-mix(in srgb, var(--mat-sys-primary) 60%, transparent);\n}\n.dot[_ngcontent-%COMP%] {\n  width: 26px;\n  height: 26px;\n  border-radius: 50%;\n  border: 1px solid var(--mat-sys-outline-variant);\n  background: transparent;\n  color: var(--mat-sys-on-surface-variant);\n  font-size: 12px;\n  font-weight: 700;\n  cursor: pointer;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  opacity: 0.45;\n  transition:\n    transform 0.1s ease,\n    opacity 0.15s ease,\n    background 0.15s ease,\n    box-shadow 0.15s ease;\n  padding: 0;\n}\n.dot[_ngcontent-%COMP%]:hover {\n  opacity: 1;\n  transform: scale(1.1);\n}\n.dot--active[_ngcontent-%COMP%] {\n  opacity: 1;\n  color: #fff;\n  border-color: transparent;\n  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);\n}\n.dot--present.dot--active[_ngcontent-%COMP%] {\n  background: #2e7d32;\n}\n.dot--absent.dot--active[_ngcontent-%COMP%] {\n  background: #c62828;\n}\n.dot--excused.dot--active[_ngcontent-%COMP%] {\n  background: #f59e0b;\n}\n/*# sourceMappingURL=headman-weekly-journal.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanWeeklyJournalComponent, [{
    type: Component,
    args: [{ selector: "app-headman-weekly-journal", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule, MatProgressSpinnerModule, MatButtonModule, MatDialogModule], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: `<div class="page-stack" [@routeFade]>
  <div class="page-header">
    <div>
      <span class="page-eyebrow">\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442</span>
      <h1 class="page-title">\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438</h1>
      <p class="page-subtitle">\u041E\u0442\u043C\u0435\u0442\u044C\u0442\u0435 \u043F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u0438\u0435 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432</p>
    </div>
  </div>

  <!-- Week navigation -->
  <div class="week-nav" role="navigation" aria-label="\u041D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F \u043F\u043E \u043D\u0435\u0434\u0435\u043B\u044F\u043C">
    <button
      type="button"
      class="week-nav__btn"
      (click)="prevWeek()"
      [disabled]="loading()"
      aria-label="\u041F\u0440\u0435\u0434\u044B\u0434\u0443\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F"
    >
      <i class="ph ph-caret-left"></i>
    </button>
    <span class="week-nav__range" aria-live="polite">{{ weekRange() }}</span>
    <button
      type="button"
      class="week-nav__btn"
      (click)="nextWeek()"
      [disabled]="loading() || isCurrentWeek()"
      aria-label="\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F"
    >
      <i class="ph ph-caret-right"></i>
    </button>
  </div>

  <div class="page-card page-card--flush">
    @if (loading()) {
      <div class="loader">
        <mat-spinner diameter="32"></mat-spinner>
      </div>
    } @else if (error()) {
      <div class="page-error"><i class="ph ph-warning-circle"></i> {{ error() }}</div>
    } @else if (!hasLessons()) {
      <div class="page-empty">
        <i class="ph-duotone ph-calendar-blank"></i>
        <h3>\u041D\u0430 \u044D\u0442\u043E\u0439 \u043D\u0435\u0434\u0435\u043B\u0435 \u043D\u0435\u0442 \u0437\u0430\u043D\u044F\u0442\u0438\u0439</h3>
        <p>\u041F\u0435\u0440\u0435\u0439\u0434\u0438\u0442\u0435 \u043A \u0434\u0440\u0443\u0433\u043E\u0439 \u043D\u0435\u0434\u0435\u043B\u0435, \u0447\u0442\u043E\u0431\u044B \u043F\u0440\u043E\u0434\u043E\u043B\u0436\u0438\u0442\u044C \u0440\u0430\u0431\u043E\u0442\u0443 \u0441 \u0436\u0443\u0440\u043D\u0430\u043B\u043E\u043C.</p>
      </div>
    } @else {
      <div class="grid-wrapper">
        <table class="journal-table">
          <thead>
            <!-- Day headers row -->
            <tr class="row-day-header">
              <th class="col-student col-student--head" rowspan="3">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</th>
              @for (day of dayGroups(); track day.date; let last = $last) {
                <th
                  class="col-day"
                  [class.col-day--last]="!last"
                  [attr.colspan]="day.columns.length * 3"
                >
                  <span class="col-day__weekday">{{ day.weekday }}</span>
                  <span class="col-day__date">{{ day.displayDate }}</span>
                </th>
              }
            </tr>
            <!-- Subject headers row -->
            <tr class="row-subject-header">
              @for (day of dayGroups(); track day.date; let dayLast = $last) {
                @for (col of day.columns; track col.lessonId; let colLast = $last) {
                  <th
                    class="col-subject"
                    [class.col-subject--day-end]="colLast && !dayLast"
                    colspan="3"
                  >
                    <div class="col-subject__text" [title]="col.subjectName + ' \xA7 ' + col.lessonNumber + ' ' + col.startTime">
                      <span class="col-subject__name">{{ col.subjectName }}</span>
                      <span class="col-subject__meta">\xA7 {{ col.lessonNumber }} \xB7 {{ col.startTime }}</span>
                    </div>
                    <div class="bulk-row">
                      <button
                        type="button"
                        class="bulk-btn bulk-btn--present"
                        (click)="onBulkMark(col, 'present')"
                        title="\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C \u0432\u0441\u0435\u043C +"
                      >+</button>
                      <button
                        type="button"
                        class="bulk-btn bulk-btn--absent"
                        (click)="onBulkMark(col, 'absent')"
                        title="\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C \u0432\u0441\u0435\u043C \u041D"
                      >\u041D</button>
                      <button
                        type="button"
                        class="bulk-btn bulk-btn--excused"
                        (click)="onBulkMark(col, 'excused')"
                        title="\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C \u0432\u0441\u0435\u043C \u0423"
                      >\u0423</button>
                    </div>
                  </th>
                }
              }
            </tr>
            <!-- Status column headers row (+/\u041D/\u0423) -->
            <tr class="row-status-header">
              @for (day of dayGroups(); track day.date; let dayLast = $last) {
                @for (col of day.columns; track col.lessonId; let colLast = $last) {
                  <th class="col-status col-status--plus">+</th>
                  <th class="col-status">\u041D</th>
                  <th
                    class="col-status"
                    [class.col-status--day-end]="colLast && !dayLast"
                  >\u0423</th>
                }
              }
            </tr>
          </thead>
          <tbody>
            @for (student of sortedStudents(); track student.userId) {
              <tr class="row-student">
                <td class="col-student" [title]="student.displayName">{{ student.displayName }}</td>
                @for (day of dayGroups(); track day.date; let dayLast = $last) {
                  @for (col of day.columns; track col.lessonId; let colLast = $last) {
                    @let status = getStatus(col.lessonId, student.userId);
                    @let saving = isSaving(col.lessonId, student.userId);
                    @let flashing = isFlashing(col.lessonId, student.userId);
                    <td class="cell" [class.cell--saving]="saving" [class.cell--flash]="flashing">
                      <button
                        type="button"
                        class="dot dot--present"
                        [class.dot--active]="status === 'present'"
                        (click)="onStatusClick(col.lessonId, student.userId, 'present')"
                        [attr.aria-label]="'\u041F\u0440\u0438\u0441\u0443\u0442\u0441\u0442\u0432\u043E\u0432\u0430\u043B \u2014 ' + student.displayName"
                        [attr.aria-pressed]="status === 'present'"
                      >+</button>
                    </td>
                    <td class="cell" [class.cell--saving]="saving" [class.cell--flash]="flashing">
                      <button
                        type="button"
                        class="dot dot--absent"
                        [class.dot--active]="status === 'absent'"
                        (click)="onStatusClick(col.lessonId, student.userId, 'absent')"
                        [attr.aria-label]="'\u041D\u0435 \u0431\u044B\u043B \u2014 ' + student.displayName"
                        [attr.aria-pressed]="status === 'absent'"
                      >\u041D</button>
                    </td>
                    <td
                      class="cell"
                      [class.cell--saving]="saving"
                      [class.cell--flash]="flashing"
                      [class.cell--day-end]="colLast && !dayLast"
                    >
                      <button
                        type="button"
                        class="dot dot--excused"
                        [class.dot--active]="status === 'excused'"
                        (click)="onStatusClick(col.lessonId, student.userId, 'excused')"
                        [attr.aria-label]="'\u0423\u0432\u0430\u0436\u0438\u0442. \u043F\u0440\u0438\u0447\u0438\u043D\u0430 \u2014 ' + student.displayName"
                        [attr.aria-pressed]="status === 'excused'"
                      >\u0423</button>
                    </td>
                  }
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  </div>
</div>
`, styles: ["/* src/app/features/headman/weekly-journal/headman-weekly-journal.component.css */\n:host {\n  display: block;\n}\n.page-subtitle {\n  margin: 4px 0 0;\n  color: var(--mat-sys-on-surface-variant);\n  font-size: 14px;\n}\n.week-nav {\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  gap: 16px;\n  padding: 8px 12px;\n  background: var(--mat-sys-surface-container);\n  border: 1px solid var(--mat-sys-outline-variant);\n  border-radius: 12px;\n  align-self: center;\n  width: fit-content;\n  margin: 0 auto;\n}\n.week-nav__btn {\n  border: 1px solid var(--mat-sys-outline-variant);\n  background: var(--mat-sys-surface);\n  width: 36px;\n  height: 36px;\n  border-radius: 50%;\n  cursor: pointer;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  color: var(--mat-sys-on-surface);\n  transition: background 0.12s ease;\n}\n.week-nav__btn:hover:not(:disabled) {\n  background: var(--mat-sys-surface-variant);\n}\n.week-nav__btn:disabled {\n  opacity: 0.4;\n  cursor: not-allowed;\n}\n.week-nav__range {\n  font-weight: 600;\n  font-size: 15px;\n  min-width: 140px;\n  text-align: center;\n  color: var(--mat-sys-on-surface);\n}\n.loader {\n  display: flex;\n  justify-content: center;\n  padding: 48px 16px;\n}\n.grid-wrapper {\n  overflow: auto;\n  max-height: calc(100vh - 280px);\n  border-radius: 8px;\n  position: relative;\n}\n.journal-table {\n  border-collapse: separate;\n  border-spacing: 0;\n  width: max-content;\n  min-width: 100%;\n  font-size: 13px;\n}\n.col-student {\n  position: sticky;\n  left: 0;\n  z-index: 3;\n  width: 200px;\n  min-width: 200px;\n  max-width: 200px;\n  padding: 8px 12px;\n  background: var(--mat-sys-surface);\n  border-right: 2px solid var(--mat-sys-outline-variant);\n  text-align: left;\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  vertical-align: middle;\n}\n.col-student--head {\n  background: var(--mat-sys-surface-container);\n  z-index: 6;\n  font-weight: 600;\n}\nthead tr.row-day-header th,\nthead tr.row-subject-header th,\nthead tr.row-status-header th {\n  position: sticky;\n  background: var(--mat-sys-surface-container);\n  z-index: 2;\n}\nthead tr.row-day-header th {\n  top: 0;\n}\nthead tr.row-subject-header th {\n  top: 38px;\n}\nthead tr.row-status-header th {\n  top: 172px;\n}\nthead tr.row-day-header th.col-student--head,\nthead tr.row-subject-header th.col-student--head,\nthead tr.row-status-header th.col-student--head {\n  z-index: 7;\n}\n.col-day {\n  padding: 8px 6px;\n  font-size: 13px;\n  font-weight: 700;\n  text-align: center;\n  border-bottom: 1px solid var(--mat-sys-outline-variant);\n  border-right: 2px solid var(--mat-sys-outline-variant);\n  color: var(--mat-sys-on-surface);\n  background: var(--mat-sys-surface-container-high);\n}\n.col-day__weekday {\n  display: inline-block;\n  margin-right: 6px;\n  opacity: 0.7;\n  font-weight: 600;\n}\n.col-day__date {\n  font-weight: 700;\n}\n.col-subject {\n  padding: 6px 2px 4px;\n  min-width: 126px;\n  width: 126px;\n  background: var(--mat-sys-surface-container);\n  border-bottom: 1px solid var(--mat-sys-outline-variant);\n  vertical-align: bottom;\n  height: 134px;\n}\n.col-subject--day-end {\n  border-right: 2px solid var(--mat-sys-outline-variant);\n}\n.col-subject__text {\n  writing-mode: vertical-rl;\n  transform: rotate(180deg);\n  display: flex;\n  flex-direction: column;\n  justify-content: flex-start;\n  align-items: center;\n  font-size: 11.5px;\n  line-height: 1.15;\n  text-align: left;\n  height: 108px;\n  margin: 0 auto;\n  gap: 4px;\n}\n.col-subject__name {\n  font-weight: 700;\n  text-transform: uppercase;\n  letter-spacing: 0.3px;\n  max-height: 82px;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n}\n.col-subject__meta {\n  font-weight: 500;\n  font-size: 10.5px;\n  opacity: 0.7;\n}\n.bulk-row {\n  display: flex;\n  gap: 3px;\n  justify-content: center;\n  margin-top: 4px;\n  padding: 0 6px;\n}\n.bulk-btn {\n  border: 1px solid var(--mat-sys-outline-variant);\n  background: var(--mat-sys-surface);\n  width: 22px;\n  height: 20px;\n  padding: 0;\n  border-radius: 4px;\n  font-size: 10px;\n  font-weight: 700;\n  cursor: pointer;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  transition: transform 0.12s ease, background 0.12s ease;\n}\n.bulk-btn:hover {\n  transform: scale(1.12);\n}\n.bulk-btn--present {\n  color: #2e7d32;\n}\n.bulk-btn--absent {\n  color: #c62828;\n}\n.bulk-btn--excused {\n  color: #b45309;\n}\n.col-status {\n  width: 42px;\n  min-width: 42px;\n  max-width: 42px;\n  padding: 4px 0;\n  text-align: center;\n  font-weight: 700;\n  font-size: 12px;\n  color: var(--mat-sys-on-surface-variant);\n  background: var(--mat-sys-surface-container);\n  border-bottom: 1px solid var(--mat-sys-outline-variant);\n}\n.col-status--day-end {\n  border-right: 2px solid var(--mat-sys-outline-variant);\n}\n.row-student {\n  transition: background 0.1s ease;\n}\n.row-student:hover {\n  background: color-mix(in srgb, var(--mat-sys-primary) 5%, transparent);\n}\n.row-student:hover .col-student {\n  background: color-mix(in srgb, var(--mat-sys-primary) 7%, var(--mat-sys-surface));\n}\n.cell {\n  width: 42px;\n  min-width: 42px;\n  max-width: 42px;\n  padding: 4px 0;\n  text-align: center;\n  border-bottom: 1px solid var(--mat-sys-outline-variant);\n  vertical-align: middle;\n  transition: box-shadow 0.4s ease;\n}\n.cell--day-end {\n  border-right: 2px solid var(--mat-sys-outline-variant);\n}\n.cell--saving {\n  opacity: 0.6;\n}\n.cell--flash {\n  box-shadow: inset 0 0 0 2px color-mix(in srgb, var(--mat-sys-primary) 60%, transparent);\n}\n.dot {\n  width: 26px;\n  height: 26px;\n  border-radius: 50%;\n  border: 1px solid var(--mat-sys-outline-variant);\n  background: transparent;\n  color: var(--mat-sys-on-surface-variant);\n  font-size: 12px;\n  font-weight: 700;\n  cursor: pointer;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  opacity: 0.45;\n  transition:\n    transform 0.1s ease,\n    opacity 0.15s ease,\n    background 0.15s ease,\n    box-shadow 0.15s ease;\n  padding: 0;\n}\n.dot:hover {\n  opacity: 1;\n  transform: scale(1.1);\n}\n.dot--active {\n  opacity: 1;\n  color: #fff;\n  border-color: transparent;\n  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);\n}\n.dot--present.dot--active {\n  background: #2e7d32;\n}\n.dot--absent.dot--active {\n  background: #c62828;\n}\n.dot--excused.dot--active {\n  background: #f59e0b;\n}\n/*# sourceMappingURL=headman-weekly-journal.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanWeeklyJournalComponent, { className: "HeadmanWeeklyJournalComponent", filePath: "src/app/features/headman/weekly-journal/headman-weekly-journal.component.ts", lineNumber: 75 });
})();
export {
  HeadmanWeeklyJournalComponent
};
//# sourceMappingURL=chunk-HZGMFRCL.js.map
