import {
  SubjectCacheService
} from "./chunk-QY7VTECN.js";
import {
  StudentApiService
} from "./chunk-ESVH3VU5.js";
import {
  NotificationCenterService
} from "./chunk-PPDDTI5J.js";
import "./chunk-VAU65FPZ.js";
import {
  takeUntilDestroyed
} from "./chunk-JGPT6EAX.js";
import "./chunk-33KX5TJL.js";
import {
  animate,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import "./chunk-URGWZVMC.js";
import {
  AsyncPipe,
  CommonModule,
  NgForOf
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  __spreadProps,
  __spreadValues,
  computed,
  inject,
  setClassMetadata,
  signal,
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
  ɵɵpipe,
  ɵɵpipeBind1,
  ɵɵproperty,
  ɵɵpureFunction0,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-MFRIGFR2.js";

// src/app/features/student/late-checkin/student-late-checkin.component.ts
var _c0 = () => [1, 2, 3];
var _forTrack0 = ($index, $item) => $item.date;
var _forTrack1 = ($index, $item) => $item.lessonId;
function StudentLateCheckinComponent_Conditional_7_div_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 9);
  }
}
function StudentLateCheckinComponent_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 4);
    \u0275\u0275template(1, StudentLateCheckinComponent_Conditional_7_div_1_Template, 1, 0, "div", 8);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    \u0275\u0275advance();
    \u0275\u0275property("ngForOf", \u0275\u0275pureFunction0(1, _c0));
  }
}
function StudentLateCheckinComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 5);
    \u0275\u0275element(1, "i", 10);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.error(), " ");
  }
}
function StudentLateCheckinComponent_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 6);
    \u0275\u0275element(1, "i", 11);
    \u0275\u0275elementStart(2, "h2", 12);
    \u0275\u0275text(3, "\u041D\u0435\u0442 \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043D\u044B\u0445 \u0437\u0430\u043D\u044F\u0442\u0438\u0439");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p", 13);
    \u0275\u0275text(5, "\u0412\u0441\u0435 \u0432\u0430\u0448\u0438 \u0437\u0430\u043D\u044F\u0442\u0438\u044F \u043E\u0442\u043C\u0435\u0447\u0435\u043D\u044B \u2014 \u0437\u0430\u043F\u0440\u0430\u0448\u0438\u0432\u0430\u0442\u044C \u043F\u043E\u0437\u0434\u043D\u044E\u044E \u043E\u0442\u043C\u0435\u0442\u043A\u0443 \u043D\u0435 \u043D\u0443\u0436\u043D\u043E.");
    \u0275\u0275elementEnd()();
  }
}
function StudentLateCheckinComponent_Conditional_10_For_2_For_16_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 28);
    \u0275\u0275element(1, "i", 29);
    \u0275\u0275text(2, " \u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D ");
    \u0275\u0275elementEnd();
  }
}
function StudentLateCheckinComponent_Conditional_10_For_2_For_16_Conditional_11_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "span", 31);
  }
}
function StudentLateCheckinComponent_Conditional_10_For_2_For_16_Conditional_11_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "i", 32);
  }
}
function StudentLateCheckinComponent_Conditional_10_For_2_For_16_Conditional_11_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 33);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const record_r3 = \u0275\u0275nextContext(2).$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.getRowError(record_r3.lessonId));
  }
}
function StudentLateCheckinComponent_Conditional_10_For_2_For_16_Conditional_11_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 30);
    \u0275\u0275listener("click", function StudentLateCheckinComponent_Conditional_10_For_2_For_16_Conditional_11_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r2);
      const record_r3 = \u0275\u0275nextContext().$implicit;
      const ctx_r0 = \u0275\u0275nextContext(3);
      return \u0275\u0275resetView(ctx_r0.requestLateCheckin(record_r3.lessonId));
    });
    \u0275\u0275template(1, StudentLateCheckinComponent_Conditional_10_For_2_For_16_Conditional_11_Conditional_1_Template, 1, 0, "span", 31)(2, StudentLateCheckinComponent_Conditional_10_For_2_For_16_Conditional_11_Conditional_2_Template, 1, 0, "i", 32);
    \u0275\u0275text(3, " \u0417\u0430\u043F\u0440\u043E\u0441\u0438\u0442\u044C \u043E\u0442\u043C\u0435\u0442\u043A\u0443 ");
    \u0275\u0275elementEnd();
    \u0275\u0275template(4, StudentLateCheckinComponent_Conditional_10_For_2_For_16_Conditional_11_Conditional_4_Template, 2, 1, "p", 33);
  }
  if (rf & 2) {
    const record_r3 = \u0275\u0275nextContext().$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275property("disabled", ctx_r0.isPending(record_r3.lessonId));
    \u0275\u0275attribute("aria-busy", ctx_r0.isPending(record_r3.lessonId));
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.isPending(record_r3.lessonId) ? 1 : 2);
    \u0275\u0275advance(3);
    \u0275\u0275conditional(ctx_r0.getRowError(record_r3.lessonId) ? 4 : -1);
  }
}
function StudentLateCheckinComponent_Conditional_10_For_2_For_16_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 22)(1, "div", 23)(2, "span", 24);
    \u0275\u0275text(3);
    \u0275\u0275pipe(4, "async");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "span", 25);
    \u0275\u0275text(6);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(7, "span", 26);
    \u0275\u0275text(8, "\u043D");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(9, "div", 27);
    \u0275\u0275template(10, StudentLateCheckinComponent_Conditional_10_For_2_For_16_Conditional_10_Template, 3, 0, "span", 28)(11, StudentLateCheckinComponent_Conditional_10_For_2_For_16_Conditional_11_Template, 5, 4);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    let tmp_21_0;
    const record_r3 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate((tmp_21_0 = \u0275\u0275pipeBind1(4, 4, ctx_r0.getSubjectName$(record_r3.subjectId))) !== null && tmp_21_0 !== void 0 ? tmp_21_0 : "\u041F\u0440\u0435\u0434\u043C\u0435\u0442");
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate1("\u041F\u0430\u0440\u0430 \u2116", record_r3.lessonNumber, "");
    \u0275\u0275advance();
    \u0275\u0275attribute("aria-label", "\u0421\u0442\u0430\u0442\u0443\u0441: \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043E");
    \u0275\u0275advance(3);
    \u0275\u0275conditional(ctx_r0.isSent(record_r3.lessonId) ? 10 : 11);
  }
}
function StudentLateCheckinComponent_Conditional_10_For_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "section", 14)(1, "header", 15)(2, "h2", 16);
    \u0275\u0275text(3);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "span", 17);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(6, "div", 18)(7, "div", 19)(8, "div", 20)(9, "span", 21);
    \u0275\u0275text(10, "\u0417\u0430\u043D\u044F\u0442\u0438\u0435");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(11, "span", 21);
    \u0275\u0275text(12, "\u0421\u0442\u0430\u0442\u0443\u0441");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(13, "span", 21);
    \u0275\u0275text(14, "\u0414\u0435\u0439\u0441\u0442\u0432\u0438\u0435");
    \u0275\u0275elementEnd()();
    \u0275\u0275repeaterCreate(15, StudentLateCheckinComponent_Conditional_10_For_2_For_16_Template, 12, 6, "div", 22, _forTrack1);
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    const group_r4 = ctx.$implicit;
    \u0275\u0275attribute("aria-label", "\u0417\u0430\u043D\u044F\u0442\u0438\u044F \u0437\u0430 " + group_r4.label);
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate(group_r4.label);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(group_r4.records.length);
    \u0275\u0275advance(10);
    \u0275\u0275repeater(group_r4.records);
  }
}
function StudentLateCheckinComponent_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 7);
    \u0275\u0275repeaterCreate(1, StudentLateCheckinComponent_Conditional_10_For_2_Template, 17, 3, "section", 14, _forTrack0);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275repeater(ctx_r0.groupedByDay());
  }
}
var DAY_NAMES = ["\u0432\u0441", "\u043F\u043D", "\u0432\u0442", "\u0441\u0440", "\u0447\u0442", "\u043F\u0442", "\u0441\u0431"];
var MONTH_NAMES = [
  "\u044F\u043D\u0432",
  "\u0444\u0435\u0432",
  "\u043C\u0430\u0440",
  "\u0430\u043F\u0440",
  "\u043C\u0430\u0439",
  "\u0438\u044E\u043D",
  "\u0438\u044E\u043B",
  "\u0430\u0432\u0433",
  "\u0441\u0435\u043D",
  "\u043E\u043A\u0442",
  "\u043D\u043E\u044F",
  "\u0434\u0435\u043A"
];
function formatDayLabel(isoDate) {
  const d = /* @__PURE__ */ new Date(isoDate + "T00:00:00");
  if (Number.isNaN(d.getTime()))
    return isoDate;
  const dow = DAY_NAMES[d.getDay()];
  const month = MONTH_NAMES[d.getMonth()];
  return `${dow}, ${d.getDate()} ${month}`;
}
var StudentLateCheckinComponent = class _StudentLateCheckinComponent {
  apiService = inject(StudentApiService);
  subjectCache = inject(SubjectCacheService);
  center = inject(NotificationCenterService);
  destroyRef = inject(DestroyRef);
  /** Observable subject-name for the async pipe. */
  getSubjectName$(subjectId) {
    return this.subjectCache.getName(subjectId);
  }
  loading = signal(false);
  error = signal(null);
  allRecords = signal([]);
  /** IDs of rows where request has been sent successfully (or graceful 404) */
  sentRows = signal(/* @__PURE__ */ new Set());
  /** IDs of rows currently being submitted */
  pendingRows = signal(/* @__PURE__ */ new Set());
  /** Per-row error messages */
  rowErrors = signal({});
  absentRecords = computed(() => this.allRecords().filter((r) => r.status === "absent"));
  groupedByDay = computed(() => {
    const byDate = /* @__PURE__ */ new Map();
    for (const r of this.absentRecords()) {
      const bucket = byDate.get(r.lessonDate);
      if (bucket)
        bucket.push(r);
      else
        byDate.set(r.lessonDate, [r]);
    }
    return Array.from(byDate.entries()).sort(([a], [b]) => a.localeCompare(b)).map(([date, records]) => ({
      date,
      label: formatDayLabel(date),
      records: records.slice().sort((a, b) => a.lessonNumber - b.lessonNumber)
    }));
  });
  ngOnInit() {
    this.fetchRecords();
    this.center.onEvent$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((envelope) => {
      if (envelope.type === "late_checkin.decided" || envelope.type === "attendance.marked") {
        this.fetchRecords();
      }
    });
  }
  fetchRecords() {
    this.loading.set(true);
    this.apiService.getStudentRecords().subscribe({
      next: (records) => {
        this.allRecords.set(records);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0441\u043F\u0438\u0441\u043E\u043A \u0437\u0430\u043D\u044F\u0442\u0438\u0439. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043E\u0431\u043D\u043E\u0432\u0438\u0442\u044C \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0443.");
        this.loading.set(false);
      }
    });
  }
  requestLateCheckin(lessonId) {
    if (this.sentRows().has(lessonId) || this.pendingRows().has(lessonId))
      return;
    this.rowErrors.update((e) => {
      const next = __spreadValues({}, e);
      delete next[lessonId];
      return next;
    });
    this.pendingRows.update((set) => {
      const next = new Set(set);
      next.add(lessonId);
      return next;
    });
    this.apiService.requestLateCheckin(lessonId).subscribe({
      next: () => {
        this.pendingRows.update((set) => {
          const next = new Set(set);
          next.delete(lessonId);
          return next;
        });
        this.sentRows.update((set) => {
          const next = new Set(set);
          next.add(lessonId);
          return next;
        });
      },
      error: () => {
        this.pendingRows.update((set) => {
          const next = new Set(set);
          next.delete(lessonId);
          return next;
        });
        this.rowErrors.update((e) => __spreadProps(__spreadValues({}, e), {
          [lessonId]: "\u041E\u0448\u0438\u0431\u043A\u0430. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437."
        }));
      }
    });
  }
  isSent(lessonId) {
    return this.sentRows().has(lessonId);
  }
  isPending(lessonId) {
    return this.pendingRows().has(lessonId);
  }
  getRowError(lessonId) {
    return this.rowErrors()[lessonId] ?? null;
  }
  static \u0275fac = function StudentLateCheckinComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentLateCheckinComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentLateCheckinComponent, selectors: [["app-student-late-checkin"]], decls: 11, vars: 2, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-header__eyebrow"], [1, "page-header__title"], [1, "page-card"], ["role", "alert", 1, "page-error"], [1, "page-empty"], ["aria-live", "polite", 1, "checkin-groups"], ["class", "skeleton-row", 4, "ngFor", "ngForOf"], [1, "skeleton-row"], [1, "ph", "ph-warning-circle"], [1, "ph-duotone", "ph-check-circle", "page-empty__icon"], [1, "page-empty__heading"], [1, "page-empty__body"], [1, "checkin-group"], [1, "checkin-group__header"], [1, "checkin-group__title"], [1, "checkin-group__count"], [1, "page-card", "page-card--flush"], ["role", "table", "aria-label", "\u0421\u043F\u0438\u0441\u043E\u043A \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043D\u044B\u0445 \u0437\u0430\u043D\u044F\u0442\u0438\u0439", 1, "checkin-table"], ["role", "row", 1, "checkin-table__header"], ["role", "columnheader"], ["role", "row", 1, "checkin-table__row"], ["role", "cell", 1, "cell-lesson"], [1, "cell-lesson__subject"], [1, "cell-lesson__meta"], ["role", "cell", 1, "status-chip", "status-chip--absent"], ["role", "cell", 1, "cell-action"], ["role", "status", "aria-label", "\u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D", 1, "sent-pill"], [1, "ph-fill", "ph-check-circle", "sent-pill__icon"], ["type", "button", 1, "btn-request", 3, "click", "disabled"], [1, "btn-spinner-sm"], [1, "ph", "ph-clock-countdown", "btn-request__icon"], ["role", "alert", 1, "row-error"]], template: function StudentLateCheckinComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "div")(3, "p", 2);
      \u0275\u0275text(4, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "h1", 3);
      \u0275\u0275text(6, "\u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043C\u0435\u0442\u043A\u0438");
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(7, StudentLateCheckinComponent_Conditional_7_Template, 2, 2, "div", 4)(8, StudentLateCheckinComponent_Conditional_8_Template, 3, 1, "div", 5)(9, StudentLateCheckinComponent_Conditional_9_Template, 6, 0, "div", 6)(10, StudentLateCheckinComponent_Conditional_10_Template, 3, 0, "div", 7);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(7);
      \u0275\u0275conditional(ctx.loading() ? 7 : ctx.error() ? 8 : ctx.absentRecords().length === 0 ? 9 : 10);
    }
  }, dependencies: [CommonModule, NgForOf, AsyncPipe], styles: ["\n\n.checkin-groups[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-5);\n}\n.checkin-group[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2);\n}\n.checkin-group__header[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: baseline;\n  gap: var(--space-2);\n  padding: 0 var(--space-1);\n}\n.checkin-group__title[_ngcontent-%COMP%] {\n  margin: 0;\n  font-size: var(--text-sm);\n  font-weight: 600;\n  color: var(--text-primary);\n  text-transform: lowercase;\n}\n.checkin-group__count[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  font-variant-numeric: tabular-nums;\n}\n.checkin-table[_ngcontent-%COMP%] {\n  width: 100%;\n}\n.checkin-table__header[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: 1fr 60px 180px;\n  padding: var(--space-2) var(--space-4);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  color: var(--text-muted);\n  border-bottom: 1px solid var(--border-subtle);\n  text-transform: uppercase;\n  letter-spacing: 0.05em;\n}\n.checkin-table__row[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: 1fr 60px 180px;\n  padding: var(--space-4);\n  min-height: 52px;\n  align-items: center;\n  gap: var(--space-4);\n  border-bottom: 1px solid var(--border-subtle);\n}\n.checkin-table__row[_ngcontent-%COMP%]:last-child {\n  border-bottom: none;\n}\n.cell-lesson[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  min-width: 0;\n}\n.cell-lesson__subject[_ngcontent-%COMP%] {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  color: var(--text-primary);\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n}\n.cell-lesson__meta[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  font-variant-numeric: tabular-nums;\n}\n.cell-action[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  align-items: flex-start;\n  gap: var(--space-1);\n}\n.btn-request[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  min-height: 36px;\n  padding: 0 var(--space-4);\n  background: transparent;\n  border: 1px solid var(--accent-primary);\n  border-radius: var(--radius-pill, 9999px);\n  color: var(--accent-primary);\n  font-size: var(--text-sm);\n  cursor: pointer;\n  transition: background 150ms ease;\n  white-space: nowrap;\n}\n.btn-request[_ngcontent-%COMP%]:hover:not(:disabled) {\n  background: color-mix(in srgb, var(--accent-primary) 8%, transparent);\n}\n.btn-request[_ngcontent-%COMP%]:disabled {\n  opacity: 0.55;\n  cursor: not-allowed;\n}\n.btn-request__icon[_ngcontent-%COMP%] {\n  font-size: 16px;\n}\n.sent-pill[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  font-size: var(--text-sm);\n  color: var(--accent-primary);\n}\n.sent-pill__icon[_ngcontent-%COMP%] {\n  font-size: 16px;\n  color: var(--accent-primary);\n}\n.row-error[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  color: var(--accent-danger);\n  margin: 0;\n}\n.skeleton-row[_ngcontent-%COMP%] {\n  height: 52px;\n  border-radius: var(--radius-md);\n  background: var(--bg-elevated);\n  margin-bottom: var(--space-2);\n  animation: dashboard-shimmer 1.5s infinite;\n}\n.btn-spinner-sm[_ngcontent-%COMP%] {\n  display: inline-block;\n  width: 12px;\n  height: 12px;\n  border: 2px solid color-mix(in srgb, var(--accent-primary) 30%, transparent);\n  border-top-color: var(--accent-primary);\n  border-radius: 50%;\n  animation: _ngcontent-%COMP%_spin 0.6s linear infinite;\n}\n@keyframes _ngcontent-%COMP%_spin {\n  to {\n    transform: rotate(360deg);\n  }\n}\n@media (max-width: 600px) {\n  .checkin-table__header[_ngcontent-%COMP%], \n   .checkin-table__row[_ngcontent-%COMP%] {\n    grid-template-columns: 1fr 60px auto;\n  }\n}\n/*# sourceMappingURL=student-late-checkin.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentLateCheckinComponent, [{
    type: Component,
    args: [{ selector: "app-student-late-checkin", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: `<div class="page-stack" @routeFade>
  <div class="page-header">
    <div>
      <p class="page-header__eyebrow">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</p>
      <h1 class="page-header__title">\u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043C\u0435\u0442\u043A\u0438</h1>
    </div>
  </div>

  @if (loading()) {
    <div class="page-card">
      <div class="skeleton-row" *ngFor="let i of [1,2,3]"></div>
    </div>
  } @else if (error()) {
    <div class="page-error" role="alert">
      <i class="ph ph-warning-circle"></i>
      {{ error() }}
    </div>
  } @else if (absentRecords().length === 0) {
    <div class="page-empty">
      <i class="ph-duotone ph-check-circle page-empty__icon"></i>
      <h2 class="page-empty__heading">\u041D\u0435\u0442 \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043D\u044B\u0445 \u0437\u0430\u043D\u044F\u0442\u0438\u0439</h2>
      <p class="page-empty__body">\u0412\u0441\u0435 \u0432\u0430\u0448\u0438 \u0437\u0430\u043D\u044F\u0442\u0438\u044F \u043E\u0442\u043C\u0435\u0447\u0435\u043D\u044B \u2014 \u0437\u0430\u043F\u0440\u0430\u0448\u0438\u0432\u0430\u0442\u044C \u043F\u043E\u0437\u0434\u043D\u044E\u044E \u043E\u0442\u043C\u0435\u0442\u043A\u0443 \u043D\u0435 \u043D\u0443\u0436\u043D\u043E.</p>
    </div>
  } @else {
    <div class="checkin-groups" aria-live="polite">
      @for (group of groupedByDay(); track group.date) {
        <section class="checkin-group" [attr.aria-label]="'\u0417\u0430\u043D\u044F\u0442\u0438\u044F \u0437\u0430 ' + group.label">
          <header class="checkin-group__header">
            <h2 class="checkin-group__title">{{ group.label }}</h2>
            <span class="checkin-group__count">{{ group.records.length }}</span>
          </header>
          <div class="page-card page-card--flush">
            <div class="checkin-table" role="table" aria-label="\u0421\u043F\u0438\u0441\u043E\u043A \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043D\u044B\u0445 \u0437\u0430\u043D\u044F\u0442\u0438\u0439">
              <div class="checkin-table__header" role="row">
                <span role="columnheader">\u0417\u0430\u043D\u044F\u0442\u0438\u0435</span>
                <span role="columnheader">\u0421\u0442\u0430\u0442\u0443\u0441</span>
                <span role="columnheader">\u0414\u0435\u0439\u0441\u0442\u0432\u0438\u0435</span>
              </div>

              @for (record of group.records; track record.lessonId) {
                <div class="checkin-table__row" role="row">
                  <div class="cell-lesson" role="cell">
                    <span class="cell-lesson__subject">{{ (getSubjectName$(record.subjectId) | async) ?? '\u041F\u0440\u0435\u0434\u043C\u0435\u0442' }}</span>
                    <span class="cell-lesson__meta">\u041F\u0430\u0440\u0430 \u2116{{ record.lessonNumber }}</span>
                  </div>
                  <span
                    class="status-chip status-chip--absent"
                    [attr.aria-label]="'\u0421\u0442\u0430\u0442\u0443\u0441: \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043E'"
                    role="cell"
                  >\u043D</span>
                  <div class="cell-action" role="cell">
                    @if (isSent(record.lessonId)) {
                      <span class="sent-pill" role="status" aria-label="\u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D">
                        <i class="ph-fill ph-check-circle sent-pill__icon"></i>
                        \u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D
                      </span>
                    } @else {
                      <button
                        class="btn-request"
                        type="button"
                        (click)="requestLateCheckin(record.lessonId)"
                        [disabled]="isPending(record.lessonId)"
                        [attr.aria-busy]="isPending(record.lessonId)"
                      >
                        @if (isPending(record.lessonId)) {
                          <span class="btn-spinner-sm"></span>
                        } @else {
                          <i class="ph ph-clock-countdown btn-request__icon"></i>
                        }
                        \u0417\u0430\u043F\u0440\u043E\u0441\u0438\u0442\u044C \u043E\u0442\u043C\u0435\u0442\u043A\u0443
                      </button>
                      @if (getRowError(record.lessonId)) {
                        <p class="row-error" role="alert">{{ getRowError(record.lessonId) }}</p>
                      }
                    }
                  </div>
                </div>
              }
            </div>
          </div>
        </section>
      }
    </div>
  }
</div>
`, styles: ["/* src/app/features/student/late-checkin/student-late-checkin.component.css */\n.checkin-groups {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-5);\n}\n.checkin-group {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2);\n}\n.checkin-group__header {\n  display: flex;\n  align-items: baseline;\n  gap: var(--space-2);\n  padding: 0 var(--space-1);\n}\n.checkin-group__title {\n  margin: 0;\n  font-size: var(--text-sm);\n  font-weight: 600;\n  color: var(--text-primary);\n  text-transform: lowercase;\n}\n.checkin-group__count {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  font-variant-numeric: tabular-nums;\n}\n.checkin-table {\n  width: 100%;\n}\n.checkin-table__header {\n  display: grid;\n  grid-template-columns: 1fr 60px 180px;\n  padding: var(--space-2) var(--space-4);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  color: var(--text-muted);\n  border-bottom: 1px solid var(--border-subtle);\n  text-transform: uppercase;\n  letter-spacing: 0.05em;\n}\n.checkin-table__row {\n  display: grid;\n  grid-template-columns: 1fr 60px 180px;\n  padding: var(--space-4);\n  min-height: 52px;\n  align-items: center;\n  gap: var(--space-4);\n  border-bottom: 1px solid var(--border-subtle);\n}\n.checkin-table__row:last-child {\n  border-bottom: none;\n}\n.cell-lesson {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  min-width: 0;\n}\n.cell-lesson__subject {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  color: var(--text-primary);\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n}\n.cell-lesson__meta {\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  font-variant-numeric: tabular-nums;\n}\n.cell-action {\n  display: flex;\n  flex-direction: column;\n  align-items: flex-start;\n  gap: var(--space-1);\n}\n.btn-request {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  min-height: 36px;\n  padding: 0 var(--space-4);\n  background: transparent;\n  border: 1px solid var(--accent-primary);\n  border-radius: var(--radius-pill, 9999px);\n  color: var(--accent-primary);\n  font-size: var(--text-sm);\n  cursor: pointer;\n  transition: background 150ms ease;\n  white-space: nowrap;\n}\n.btn-request:hover:not(:disabled) {\n  background: color-mix(in srgb, var(--accent-primary) 8%, transparent);\n}\n.btn-request:disabled {\n  opacity: 0.55;\n  cursor: not-allowed;\n}\n.btn-request__icon {\n  font-size: 16px;\n}\n.sent-pill {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  font-size: var(--text-sm);\n  color: var(--accent-primary);\n}\n.sent-pill__icon {\n  font-size: 16px;\n  color: var(--accent-primary);\n}\n.row-error {\n  font-size: var(--text-xs);\n  color: var(--accent-danger);\n  margin: 0;\n}\n.skeleton-row {\n  height: 52px;\n  border-radius: var(--radius-md);\n  background: var(--bg-elevated);\n  margin-bottom: var(--space-2);\n  animation: dashboard-shimmer 1.5s infinite;\n}\n.btn-spinner-sm {\n  display: inline-block;\n  width: 12px;\n  height: 12px;\n  border: 2px solid color-mix(in srgb, var(--accent-primary) 30%, transparent);\n  border-top-color: var(--accent-primary);\n  border-radius: 50%;\n  animation: spin 0.6s linear infinite;\n}\n@keyframes spin {\n  to {\n    transform: rotate(360deg);\n  }\n}\n@media (max-width: 600px) {\n  .checkin-table__header,\n  .checkin-table__row {\n    grid-template-columns: 1fr 60px auto;\n  }\n}\n/*# sourceMappingURL=student-late-checkin.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentLateCheckinComponent, { className: "StudentLateCheckinComponent", filePath: "src/app/features/student/late-checkin/student-late-checkin.component.ts", lineNumber: 48 });
})();
export {
  StudentLateCheckinComponent
};
//# sourceMappingURL=chunk-TVUE6ERH.js.map
