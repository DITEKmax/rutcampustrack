import {
  StudentApiService
} from "./chunk-EB6UCOGR.js";
import "./chunk-OYYF72SB.js";
import {
  animate,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import "./chunk-FLE4DLW4.js";
import {
  CommonModule,
  NgForOf
} from "./chunk-CLRYRPPS.js";
import {
  ChangeDetectionStrategy,
  Component,
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
} from "./chunk-4M4FDLBS.js";

// src/app/features/student/late-checkin/student-late-checkin.component.ts
var _c0 = () => [1, 2, 3];
var _forTrack0 = ($index, $item) => $item.lessonId;
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
function StudentLateCheckinComponent_Conditional_10_For_12_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 22);
    \u0275\u0275element(1, "i", 23);
    \u0275\u0275text(2, " \u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D ");
    \u0275\u0275elementEnd();
  }
}
function StudentLateCheckinComponent_Conditional_10_For_12_Conditional_9_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "span", 25);
  }
}
function StudentLateCheckinComponent_Conditional_10_For_12_Conditional_9_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "i", 26);
  }
}
function StudentLateCheckinComponent_Conditional_10_For_12_Conditional_9_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 27);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const record_r3 = \u0275\u0275nextContext(2).$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.getRowError(record_r3.lessonId));
  }
}
function StudentLateCheckinComponent_Conditional_10_For_12_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 24);
    \u0275\u0275listener("click", function StudentLateCheckinComponent_Conditional_10_For_12_Conditional_9_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r2);
      const record_r3 = \u0275\u0275nextContext().$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.requestLateCheckin(record_r3.lessonId));
    });
    \u0275\u0275template(1, StudentLateCheckinComponent_Conditional_10_For_12_Conditional_9_Conditional_1_Template, 1, 0, "span", 25)(2, StudentLateCheckinComponent_Conditional_10_For_12_Conditional_9_Conditional_2_Template, 1, 0, "i", 26);
    \u0275\u0275text(3, " \u0417\u0430\u043F\u0440\u043E\u0441\u0438\u0442\u044C \u043E\u0442\u043C\u0435\u0442\u043A\u0443 ");
    \u0275\u0275elementEnd();
    \u0275\u0275template(4, StudentLateCheckinComponent_Conditional_10_For_12_Conditional_9_Conditional_4_Template, 2, 1, "p", 27);
  }
  if (rf & 2) {
    const record_r3 = \u0275\u0275nextContext().$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275property("disabled", ctx_r0.isPending(record_r3.lessonId));
    \u0275\u0275attribute("aria-busy", ctx_r0.isPending(record_r3.lessonId));
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.isPending(record_r3.lessonId) ? 1 : 2);
    \u0275\u0275advance(3);
    \u0275\u0275conditional(ctx_r0.getRowError(record_r3.lessonId) ? 4 : -1);
  }
}
function StudentLateCheckinComponent_Conditional_10_For_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 17)(1, "span", 18);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "span", 19);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "span", 20);
    \u0275\u0275text(6, "\u043D");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(7, "div", 21);
    \u0275\u0275template(8, StudentLateCheckinComponent_Conditional_10_For_12_Conditional_8_Template, 3, 0, "span", 22)(9, StudentLateCheckinComponent_Conditional_10_For_12_Conditional_9_Template, 5, 4);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const record_r3 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(record_r3.lessonDate);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1("\u0417\u0430\u043D\u044F\u0442\u0438\u0435 ", record_r3.lessonNumber, "");
    \u0275\u0275advance();
    \u0275\u0275attribute("aria-label", "\u0421\u0442\u0430\u0442\u0443\u0441: \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043E");
    \u0275\u0275advance(3);
    \u0275\u0275conditional(ctx_r0.isSent(record_r3.lessonId) ? 8 : 9);
  }
}
function StudentLateCheckinComponent_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 7)(1, "div", 14)(2, "div", 15)(3, "span", 16);
    \u0275\u0275text(4, "\u0414\u0430\u0442\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "span", 16);
    \u0275\u0275text(6, "\u0417\u0430\u043D\u044F\u0442\u0438\u0435");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(7, "span", 16);
    \u0275\u0275text(8, "\u0421\u0442\u0430\u0442\u0443\u0441");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(9, "span", 16);
    \u0275\u0275text(10, "\u0414\u0435\u0439\u0441\u0442\u0432\u0438\u0435");
    \u0275\u0275elementEnd()();
    \u0275\u0275repeaterCreate(11, StudentLateCheckinComponent_Conditional_10_For_12_Template, 10, 4, "div", 17, _forTrack0);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(11);
    \u0275\u0275repeater(ctx_r0.absentRecords());
  }
}
var StudentLateCheckinComponent = class _StudentLateCheckinComponent {
  apiService = inject(StudentApiService);
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
  ngOnInit() {
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
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentLateCheckinComponent, selectors: [["app-student-late-checkin"]], decls: 11, vars: 2, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-header__eyebrow"], [1, "page-header__title"], [1, "page-card"], ["role", "alert", 1, "page-error"], [1, "page-empty"], [1, "page-card", "page-card--flush"], ["class", "skeleton-row", 4, "ngFor", "ngForOf"], [1, "skeleton-row"], [1, "ph", "ph-warning-circle"], [1, "ph-duotone", "ph-check-circle", "page-empty__icon"], [1, "page-empty__heading"], [1, "page-empty__body"], ["role", "table", "aria-label", "\u0421\u043F\u0438\u0441\u043E\u043A \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043D\u044B\u0445 \u0437\u0430\u043D\u044F\u0442\u0438\u0439", "aria-live", "polite", 1, "checkin-table"], ["role", "row", 1, "checkin-table__header"], ["role", "columnheader"], ["role", "row", 1, "checkin-table__row"], ["role", "cell", 1, "cell-date"], ["role", "cell", 1, "cell-lesson"], ["role", "cell", 1, "status-chip", "status-chip--absent"], ["role", "cell", 1, "cell-action"], ["role", "status", "aria-label", "\u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D", 1, "sent-pill"], [1, "ph-fill", "ph-check-circle", "sent-pill__icon"], ["type", "button", 1, "btn-request", 3, "click", "disabled"], [1, "btn-spinner-sm"], [1, "ph", "ph-clock-countdown", "btn-request__icon"], ["role", "alert", 1, "row-error"]], template: function StudentLateCheckinComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "div")(3, "p", 2);
      \u0275\u0275text(4, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "h1", 3);
      \u0275\u0275text(6, "\u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043C\u0435\u0442\u043A\u0438");
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(7, StudentLateCheckinComponent_Conditional_7_Template, 2, 2, "div", 4)(8, StudentLateCheckinComponent_Conditional_8_Template, 3, 1, "div", 5)(9, StudentLateCheckinComponent_Conditional_9_Template, 6, 0, "div", 6)(10, StudentLateCheckinComponent_Conditional_10_Template, 13, 0, "div", 7);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(7);
      \u0275\u0275conditional(ctx.loading() ? 7 : ctx.error() ? 8 : ctx.absentRecords().length === 0 ? 9 : 10);
    }
  }, dependencies: [CommonModule, NgForOf], styles: ["\n\n.checkin-table[_ngcontent-%COMP%] {\n  width: 100%;\n}\n.checkin-table__header[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: 100px 1fr 60px 180px;\n  padding: var(--space-2) var(--space-4);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  color: var(--text-muted);\n  border-bottom: 1px solid var(--border-subtle);\n  text-transform: uppercase;\n  letter-spacing: 0.05em;\n}\n.checkin-table__row[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: 100px 1fr 60px 180px;\n  padding: var(--space-4);\n  min-height: 52px;\n  align-items: center;\n  gap: var(--space-4);\n  border-bottom: 1px solid var(--border-subtle);\n}\n.checkin-table__row[_ngcontent-%COMP%]:last-child {\n  border-bottom: none;\n}\n.cell-date[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  font-variant-numeric: tabular-nums;\n}\n.cell-lesson[_ngcontent-%COMP%] {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.cell-action[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  align-items: flex-start;\n  gap: var(--space-1);\n}\n.btn-request[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  min-height: 36px;\n  padding: 0 var(--space-4);\n  background: transparent;\n  border: 1px solid var(--accent-primary);\n  border-radius: var(--radius-pill, 9999px);\n  color: var(--accent-primary);\n  font-size: var(--text-sm);\n  cursor: pointer;\n  transition: background 150ms ease;\n  white-space: nowrap;\n}\n.btn-request[_ngcontent-%COMP%]:hover:not(:disabled) {\n  background: color-mix(in srgb, var(--accent-primary) 8%, transparent);\n}\n.btn-request[_ngcontent-%COMP%]:disabled {\n  opacity: 0.55;\n  cursor: not-allowed;\n}\n.btn-request__icon[_ngcontent-%COMP%] {\n  font-size: 16px;\n}\n.sent-pill[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  font-size: var(--text-sm);\n  color: var(--accent-primary);\n}\n.sent-pill__icon[_ngcontent-%COMP%] {\n  font-size: 16px;\n  color: var(--accent-primary);\n}\n.row-error[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  color: var(--accent-danger);\n  margin: 0;\n}\n.skeleton-row[_ngcontent-%COMP%] {\n  height: 52px;\n  border-radius: var(--radius-md);\n  background: var(--bg-elevated);\n  margin-bottom: var(--space-2);\n  animation: dashboard-shimmer 1.5s infinite;\n}\n.btn-spinner-sm[_ngcontent-%COMP%] {\n  display: inline-block;\n  width: 12px;\n  height: 12px;\n  border: 2px solid color-mix(in srgb, var(--accent-primary) 30%, transparent);\n  border-top-color: var(--accent-primary);\n  border-radius: 50%;\n  animation: _ngcontent-%COMP%_spin 0.6s linear infinite;\n}\n@keyframes _ngcontent-%COMP%_spin {\n  to {\n    transform: rotate(360deg);\n  }\n}\n@media (max-width: 600px) {\n  .checkin-table__header[_ngcontent-%COMP%], \n   .checkin-table__row[_ngcontent-%COMP%] {\n    grid-template-columns: 1fr 60px auto;\n  }\n  .cell-date[_ngcontent-%COMP%] {\n    display: none;\n  }\n}\n/*# sourceMappingURL=student-late-checkin.component.css.map */"], data: { animation: [
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
    ], template: `<div class="page-stack" @routeFade>\r
  <div class="page-header">\r
    <div>\r
      <p class="page-header__eyebrow">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</p>\r
      <h1 class="page-header__title">\u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043C\u0435\u0442\u043A\u0438</h1>\r
    </div>\r
  </div>\r
\r
  @if (loading()) {\r
    <div class="page-card">\r
      <div class="skeleton-row" *ngFor="let i of [1,2,3]"></div>\r
    </div>\r
  } @else if (error()) {\r
    <div class="page-error" role="alert">\r
      <i class="ph ph-warning-circle"></i>\r
      {{ error() }}\r
    </div>\r
  } @else if (absentRecords().length === 0) {\r
    <div class="page-empty">\r
      <i class="ph-duotone ph-check-circle page-empty__icon"></i>\r
      <h2 class="page-empty__heading">\u041D\u0435\u0442 \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043D\u044B\u0445 \u0437\u0430\u043D\u044F\u0442\u0438\u0439</h2>\r
      <p class="page-empty__body">\u0412\u0441\u0435 \u0432\u0430\u0448\u0438 \u0437\u0430\u043D\u044F\u0442\u0438\u044F \u043E\u0442\u043C\u0435\u0447\u0435\u043D\u044B \u2014 \u0437\u0430\u043F\u0440\u0430\u0448\u0438\u0432\u0430\u0442\u044C \u043F\u043E\u0437\u0434\u043D\u044E\u044E \u043E\u0442\u043C\u0435\u0442\u043A\u0443 \u043D\u0435 \u043D\u0443\u0436\u043D\u043E.</p>\r
    </div>\r
  } @else {\r
    <div class="page-card page-card--flush">\r
      <div\r
        class="checkin-table"\r
        role="table"\r
        aria-label="\u0421\u043F\u0438\u0441\u043E\u043A \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043D\u044B\u0445 \u0437\u0430\u043D\u044F\u0442\u0438\u0439"\r
        aria-live="polite"\r
      >\r
        <div class="checkin-table__header" role="row">\r
          <span role="columnheader">\u0414\u0430\u0442\u0430</span>\r
          <span role="columnheader">\u0417\u0430\u043D\u044F\u0442\u0438\u0435</span>\r
          <span role="columnheader">\u0421\u0442\u0430\u0442\u0443\u0441</span>\r
          <span role="columnheader">\u0414\u0435\u0439\u0441\u0442\u0432\u0438\u0435</span>\r
        </div>\r
\r
        @for (record of absentRecords(); track record.lessonId) {\r
          <div class="checkin-table__row" role="row">\r
            <span class="cell-date" role="cell">{{ record.lessonDate }}</span>\r
            <span class="cell-lesson" role="cell">\u0417\u0430\u043D\u044F\u0442\u0438\u0435 {{ record.lessonNumber }}</span>\r
            <span\r
              class="status-chip status-chip--absent"\r
              [attr.aria-label]="'\u0421\u0442\u0430\u0442\u0443\u0441: \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043E'"\r
              role="cell"\r
            >\u043D</span>\r
            <div class="cell-action" role="cell">\r
              @if (isSent(record.lessonId)) {\r
                <span class="sent-pill" role="status" aria-label="\u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D">\r
                  <i class="ph-fill ph-check-circle sent-pill__icon"></i>\r
                  \u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D\r
                </span>\r
              } @else {\r
                <button\r
                  class="btn-request"\r
                  type="button"\r
                  (click)="requestLateCheckin(record.lessonId)"\r
                  [disabled]="isPending(record.lessonId)"\r
                  [attr.aria-busy]="isPending(record.lessonId)"\r
                >\r
                  @if (isPending(record.lessonId)) {\r
                    <span class="btn-spinner-sm"></span>\r
                  } @else {\r
                    <i class="ph ph-clock-countdown btn-request__icon"></i>\r
                  }\r
                  \u0417\u0430\u043F\u0440\u043E\u0441\u0438\u0442\u044C \u043E\u0442\u043C\u0435\u0442\u043A\u0443\r
                </button>\r
                @if (getRowError(record.lessonId)) {\r
                  <p class="row-error" role="alert">{{ getRowError(record.lessonId) }}</p>\r
                }\r
              }\r
            </div>\r
          </div>\r
        }\r
      </div>\r
    </div>\r
  }\r
</div>\r
`, styles: ["/* src/app/features/student/late-checkin/student-late-checkin.component.css */\n.checkin-table {\n  width: 100%;\n}\n.checkin-table__header {\n  display: grid;\n  grid-template-columns: 100px 1fr 60px 180px;\n  padding: var(--space-2) var(--space-4);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  color: var(--text-muted);\n  border-bottom: 1px solid var(--border-subtle);\n  text-transform: uppercase;\n  letter-spacing: 0.05em;\n}\n.checkin-table__row {\n  display: grid;\n  grid-template-columns: 100px 1fr 60px 180px;\n  padding: var(--space-4);\n  min-height: 52px;\n  align-items: center;\n  gap: var(--space-4);\n  border-bottom: 1px solid var(--border-subtle);\n}\n.checkin-table__row:last-child {\n  border-bottom: none;\n}\n.cell-date {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  font-variant-numeric: tabular-nums;\n}\n.cell-lesson {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.cell-action {\n  display: flex;\n  flex-direction: column;\n  align-items: flex-start;\n  gap: var(--space-1);\n}\n.btn-request {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  min-height: 36px;\n  padding: 0 var(--space-4);\n  background: transparent;\n  border: 1px solid var(--accent-primary);\n  border-radius: var(--radius-pill, 9999px);\n  color: var(--accent-primary);\n  font-size: var(--text-sm);\n  cursor: pointer;\n  transition: background 150ms ease;\n  white-space: nowrap;\n}\n.btn-request:hover:not(:disabled) {\n  background: color-mix(in srgb, var(--accent-primary) 8%, transparent);\n}\n.btn-request:disabled {\n  opacity: 0.55;\n  cursor: not-allowed;\n}\n.btn-request__icon {\n  font-size: 16px;\n}\n.sent-pill {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  font-size: var(--text-sm);\n  color: var(--accent-primary);\n}\n.sent-pill__icon {\n  font-size: 16px;\n  color: var(--accent-primary);\n}\n.row-error {\n  font-size: var(--text-xs);\n  color: var(--accent-danger);\n  margin: 0;\n}\n.skeleton-row {\n  height: 52px;\n  border-radius: var(--radius-md);\n  background: var(--bg-elevated);\n  margin-bottom: var(--space-2);\n  animation: dashboard-shimmer 1.5s infinite;\n}\n.btn-spinner-sm {\n  display: inline-block;\n  width: 12px;\n  height: 12px;\n  border: 2px solid color-mix(in srgb, var(--accent-primary) 30%, transparent);\n  border-top-color: var(--accent-primary);\n  border-radius: 50%;\n  animation: spin 0.6s linear infinite;\n}\n@keyframes spin {\n  to {\n    transform: rotate(360deg);\n  }\n}\n@media (max-width: 600px) {\n  .checkin-table__header,\n  .checkin-table__row {\n    grid-template-columns: 1fr 60px auto;\n  }\n  .cell-date {\n    display: none;\n  }\n}\n/*# sourceMappingURL=student-late-checkin.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentLateCheckinComponent, { className: "StudentLateCheckinComponent", filePath: "src/app/features/student/late-checkin/student-late-checkin.component.ts", lineNumber: 25 });
})();
export {
  StudentLateCheckinComponent
};
//# sourceMappingURL=chunk-63H455IG.js.map
