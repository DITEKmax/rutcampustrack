import {
  ExcuseFormDialogComponent
} from "./chunk-WZUMQHNK.js";
import "./chunk-F5IUR55I.js";
import "./chunk-53YESMZZ.js";
import {
  SubjectCacheService
} from "./chunk-QY7VTECN.js";
import {
  addDays,
  formatDate,
  formatLessonTime,
  formatWeekRange,
  getMonday,
  getTodayDayIndex,
  isSameWeek
} from "./chunk-7F5XKO6G.js";
import "./chunk-MQ7WFICG.js";
import {
  StudentApiService
} from "./chunk-ESVH3VU5.js";
import {
  MatDialog
} from "./chunk-M24SPP55.js";
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
import {
  AuthService
} from "./chunk-URGWZVMC.js";
import "./chunk-WQN3TC62.js";
import "./chunk-POR7KUSL.js";
import "./chunk-OIBNGD5S.js";
import "./chunk-Z3VV4J2X.js";
import "./chunk-PAS4QIDL.js";
import "./chunk-4RJPSRCU.js";
import "./chunk-6JM2QIGP.js";
import "./chunk-75YPR2VK.js";
import "./chunk-2UA5NBNP.js";
import "./chunk-YIJEORIR.js";
import "./chunk-B3BDHDAM.js";
import "./chunk-XAMIXVT7.js";
import {
  AsyncPipe,
  NgClass
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  EventEmitter,
  Input,
  Output,
  __spreadProps,
  __spreadValues,
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
  ɵɵclassMap,
  ɵɵclassProp,
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
  ɵɵpureFunction3,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵrepeaterTrackByIdentity,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1,
  ɵɵtextInterpolate2
} from "./chunk-MFRIGFR2.js";

// src/app/features/student/schedule/lesson-row/lesson-row.component.ts
var _c0 = (a0, a1, a2) => ({ "lesson-row--active": a0, "lesson-row--cancelled": a1, "lesson-row--expanded": a2 });
function LessonRowComponent_Conditional_17_Conditional_22_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div")(1, "dt");
    \u0275\u0275text(2, "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "dd");
    \u0275\u0275text(4);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate1("ID ", ctx_r1.lesson.teacherId, "");
  }
}
function LessonRowComponent_Conditional_17_Conditional_23_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div")(1, "dt");
    \u0275\u0275text(2, "\u041F\u0440\u0438\u0447\u0438\u043D\u0430 \u043E\u0442\u043C\u0435\u043D\u044B");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "dd");
    \u0275\u0275text(4);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate(ctx_r1.lesson.cancelReason);
  }
}
function LessonRowComponent_Conditional_17_Conditional_28_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 15);
    \u0275\u0275element(1, "i", 18);
    \u0275\u0275text(2, " \u0417\u0430\u043F\u0440\u043E\u0441 \u043D\u0430 \u043E\u0442\u043C\u0435\u0442\u043A\u0443 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D ");
    \u0275\u0275elementEnd();
  }
}
function LessonRowComponent_Conditional_17_Conditional_29_Template(rf, ctx) {
  if (rf & 1) {
    const _r3 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 19);
    \u0275\u0275listener("click", function LessonRowComponent_Conditional_17_Conditional_29_Template_button_click_0_listener($event) {
      \u0275\u0275restoreView(_r3);
      const ctx_r1 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r1.onRequestLateCheckin($event));
    });
    \u0275\u0275element(1, "i", 20);
    \u0275\u0275text(2, " \u0417\u0430\u044F\u0432\u0438\u0442\u044C \u043E \u043F\u043E\u0441\u0435\u0449\u0435\u043D\u0438\u0438 ");
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275property("disabled", !ctx_r1.canRequestLateCheckin || ctx_r1.lateCheckinPending);
    \u0275\u0275attribute("aria-busy", ctx_r1.lateCheckinPending);
  }
}
function LessonRowComponent_Conditional_17_Conditional_30_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 17);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r1.lateCheckinError);
  }
}
function LessonRowComponent_Conditional_17_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "div", 10)(1, "dl", 11)(2, "div")(3, "dt");
    \u0275\u0275text(4, "\u041D\u043E\u043C\u0435\u0440 \u043F\u0430\u0440\u044B");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "dd");
    \u0275\u0275text(6);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(7, "div")(8, "dt");
    \u0275\u0275text(9, "\u0422\u0438\u043F");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(10, "dd");
    \u0275\u0275text(11);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(12, "div")(13, "dt");
    \u0275\u0275text(14, "\u0410\u0443\u0434\u0438\u0442\u043E\u0440\u0438\u044F");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(15, "dd");
    \u0275\u0275text(16);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(17, "div")(18, "dt");
    \u0275\u0275text(19, "\u0414\u043B\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0441\u0442\u044C");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(20, "dd");
    \u0275\u0275text(21);
    \u0275\u0275elementEnd()();
    \u0275\u0275template(22, LessonRowComponent_Conditional_17_Conditional_22_Template, 5, 1, "div")(23, LessonRowComponent_Conditional_17_Conditional_23_Template, 5, 1, "div");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(24, "div", 12)(25, "button", 13);
    \u0275\u0275listener("click", function LessonRowComponent_Conditional_17_Template_button_click_25_listener($event) {
      \u0275\u0275restoreView(_r1);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1.onRequestExcuse($event));
    });
    \u0275\u0275element(26, "i", 14);
    \u0275\u0275text(27, " \u0417\u0430\u044F\u0432\u0438\u0442\u044C \u043E\u0431 \u0443\u0432\u0430\u0436\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0439 \u043F\u0440\u0438\u0447\u0438\u043D\u0435 ");
    \u0275\u0275elementEnd();
    \u0275\u0275template(28, LessonRowComponent_Conditional_17_Conditional_28_Template, 3, 0, "span", 15)(29, LessonRowComponent_Conditional_17_Conditional_29_Template, 3, 2, "button", 16);
    \u0275\u0275elementEnd();
    \u0275\u0275template(30, LessonRowComponent_Conditional_17_Conditional_30_Template, 2, 1, "p", 17);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275attribute("id", "lesson-detail-" + ctx_r1.lesson.id);
    \u0275\u0275advance(6);
    \u0275\u0275textInterpolate(ctx_r1.lesson.lessonNumber);
    \u0275\u0275advance(5);
    \u0275\u0275textInterpolate(ctx_r1.lessonTypeLabel);
    \u0275\u0275advance(5);
    \u0275\u0275textInterpolate(ctx_r1.lesson.room);
    \u0275\u0275advance(5);
    \u0275\u0275textInterpolate2("", ctx_r1.startLabel, " \u2014 ", ctx_r1.endLabel, "");
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r1.lesson.teacherId ? 22 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r1.lesson.cancelReason ? 23 : -1);
    \u0275\u0275advance(2);
    \u0275\u0275property("disabled", !ctx_r1.canRequestExcuse);
    \u0275\u0275advance(3);
    \u0275\u0275conditional(ctx_r1.lateCheckinSent ? 28 : 29);
    \u0275\u0275advance(2);
    \u0275\u0275conditional(ctx_r1.lateCheckinError ? 30 : -1);
  }
}
var LessonRowComponent = class _LessonRowComponent {
  lesson;
  subjectName = "\u041F\u0440\u0435\u0434\u043C\u0435\u0442";
  personalStatus = null;
  personalSource = null;
  expanded = false;
  lateCheckinSent = false;
  lateCheckinPending = false;
  lateCheckinError = null;
  toggle = new EventEmitter();
  requestExcuse = new EventEmitter();
  requestLateCheckin = new EventEmitter();
  get isActive() {
    return this.lesson.status === "ACTIVE";
  }
  get isCancelled() {
    return this.lesson.status === "CANCELLED";
  }
  /**
   * Colour bucket for the rail dot. Covers the 4 states the UX mapping asks
   * for: waiting-to-check-in (active, not yet marked) is blue; present is
   * green; absent is red; excused / free attendance is yellow. Everything
   * else (planned, cancelled, past without own status) is muted.
   */
  get dotStateClass() {
    if (this.personalStatus === "present")
      return "lesson-row__dot--present";
    if (this.personalStatus === "absent")
      return "lesson-row__dot--absent";
    if (this.personalStatus === "excused" || this.personalStatus === "free_attendance") {
      return "lesson-row__dot--excused";
    }
    if (this.isActive)
      return "lesson-row__dot--active";
    return "lesson-row__dot--muted";
  }
  get startLabel() {
    return formatLessonTime(this.lesson.startTime);
  }
  get endLabel() {
    return formatLessonTime(this.lesson.endTime);
  }
  get statusChipClass() {
    if (this.personalStatus) {
      return `status-chip status-chip--${this.personalStatus}`;
    }
    if (this.isCancelled)
      return "status-chip status-chip--cancelled";
    return "status-chip status-chip--planned";
  }
  get statusLabel() {
    if (this.personalStatus === "present")
      return "+";
    if (this.personalStatus === "absent")
      return "\u043D";
    if (this.personalStatus === "excused")
      return "\u0443";
    if (this.personalStatus === "free_attendance")
      return "\u0441\u043F";
    if (this.isCancelled)
      return "\u041E\u0442\u043C\u0435\u043D\u0435\u043D\u0430";
    return "\u041F\u0430\u0440\u0430";
  }
  /** Excuse-ticket button is disabled for cancelled lessons. */
  get canRequestExcuse() {
    return !this.isCancelled;
  }
  /** Late-checkin button is available for any non-cancelled lesson not already marked present. */
  get canRequestLateCheckin() {
    return !this.isCancelled && this.personalStatus !== "present";
  }
  onRequestExcuse(event) {
    event.stopPropagation();
    this.requestExcuse.emit(this.lesson.id);
  }
  onRequestLateCheckin(event) {
    event.stopPropagation();
    if (this.lateCheckinSent || this.lateCheckinPending)
      return;
    this.requestLateCheckin.emit(this.lesson.id);
  }
  /**
   * Simple deterministic label — backend LessonResponse currently does not
   * expose a lesson-type field. Phase 52 may extend this to real type text
   * (лекция/практика/лаб. работа). For Phase 51 fall back to the lesson
   * number to give students a stable anchor in the detail panel.
   */
  get lessonTypeLabel() {
    return `\u041F\u0430\u0440\u0430 \u2116${this.lesson.lessonNumber}`;
  }
  onClick() {
    if (this.isCancelled)
      return;
    this.toggle.emit(this.lesson.id);
  }
  static \u0275fac = function LessonRowComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _LessonRowComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _LessonRowComponent, selectors: [["app-lesson-row"]], inputs: { lesson: "lesson", subjectName: "subjectName", personalStatus: "personalStatus", personalSource: "personalSource", expanded: "expanded", lateCheckinSent: "lateCheckinSent", lateCheckinPending: "lateCheckinPending", lateCheckinError: "lateCheckinError" }, outputs: { toggle: "toggle", requestExcuse: "requestExcuse", requestLateCheckin: "requestLateCheckin" }, decls: 18, vars: 17, consts: [["type", "button", 1, "lesson-row", 3, "click", "ngClass"], ["aria-hidden", "true", 1, "lesson-row__rail"], [1, "lesson-row__time", "lesson-row__time--start"], [1, "lesson-row__dot", 3, "ngClass"], [1, "lesson-row__time", "lesson-row__time--end"], [1, "lesson-row__body"], [1, "lesson-row__head"], [1, "lesson-row__subject"], [1, "lesson-row__meta"], ["aria-hidden", "true", 1, "ph", "ph-map-pin"], ["role", "region", 1, "lesson-row__detail"], [1, "lesson-row__detail-list"], [1, "lesson-row__detail-actions"], ["type", "button", 1, "lesson-row__detail-btn", "lesson-row__detail-btn--primary", 3, "click", "disabled"], ["aria-hidden", "true", 1, "ph", "ph-note-pencil"], ["role", "status", "aria-label", "\u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D", 1, "lesson-row__detail-sent"], ["type", "button", 1, "lesson-row__detail-btn", "lesson-row__detail-btn--secondary", 3, "disabled"], ["role", "alert", 1, "lesson-row__detail-error"], ["aria-hidden", "true", 1, "ph-fill", "ph-check-circle"], ["type", "button", 1, "lesson-row__detail-btn", "lesson-row__detail-btn--secondary", 3, "click", "disabled"], ["aria-hidden", "true", 1, "ph", "ph-clock-countdown"]], template: function LessonRowComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "button", 0);
      \u0275\u0275listener("click", function LessonRowComponent_Template_button_click_0_listener() {
        return ctx.onClick();
      });
      \u0275\u0275elementStart(1, "div", 1)(2, "span", 2);
      \u0275\u0275text(3);
      \u0275\u0275elementEnd();
      \u0275\u0275element(4, "span", 3);
      \u0275\u0275elementStart(5, "span", 4);
      \u0275\u0275text(6);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(7, "div", 5)(8, "div", 6)(9, "h3", 7);
      \u0275\u0275text(10);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(11, "span");
      \u0275\u0275text(12);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(13, "p", 8);
      \u0275\u0275element(14, "i", 9);
      \u0275\u0275elementStart(15, "span");
      \u0275\u0275text(16);
      \u0275\u0275elementEnd()()()();
      \u0275\u0275template(17, LessonRowComponent_Conditional_17_Template, 31, 11, "div", 10);
    }
    if (rf & 2) {
      \u0275\u0275property("ngClass", \u0275\u0275pureFunction3(13, _c0, ctx.isActive, ctx.isCancelled, ctx.expanded));
      \u0275\u0275attribute("aria-expanded", ctx.expanded)("aria-controls", ctx.expanded ? "lesson-detail-" + ctx.lesson.id : null)("data-lesson-id", ctx.lesson.id);
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.startLabel);
      \u0275\u0275advance();
      \u0275\u0275property("ngClass", ctx.dotStateClass);
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate(ctx.endLabel);
      \u0275\u0275advance(4);
      \u0275\u0275textInterpolate(ctx.subjectName);
      \u0275\u0275advance();
      \u0275\u0275classMap(ctx.statusChipClass);
      \u0275\u0275advance();
      \u0275\u0275textInterpolate(ctx.statusLabel);
      \u0275\u0275advance(4);
      \u0275\u0275textInterpolate1("\u0410\u0443\u0434. ", ctx.lesson.room, "");
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.expanded ? 17 : -1);
    }
  }, dependencies: [NgClass], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.lesson-row[_ngcontent-%COMP%] {\n  display: flex;\n  gap: var(--space-4);\n  width: 100%;\n  padding: var(--space-4);\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-lg);\n  background: var(--bg-secondary);\n  color: var(--text-primary);\n  text-align: left;\n  cursor: pointer;\n  transition:\n    border-color var(--duration-base) var(--ease-out),\n    box-shadow var(--duration-base) var(--ease-out),\n    transform var(--duration-base) var(--ease-out);\n}\n.lesson-row[_ngcontent-%COMP%]:hover:not(.lesson-row--cancelled) {\n  border-color: var(--border-default);\n}\n.lesson-row[_ngcontent-%COMP%]:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.lesson-row--active[_ngcontent-%COMP%] {\n  border-color: var(--accent-secondary);\n  box-shadow: var(--glow-blue);\n}\n.lesson-row--cancelled[_ngcontent-%COMP%] {\n  opacity: 0.55;\n  cursor: default;\n}\n.lesson-row--cancelled[_ngcontent-%COMP%]   .lesson-row__subject[_ngcontent-%COMP%] {\n  text-decoration: line-through;\n}\n.lesson-row__rail[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-2);\n  flex-shrink: 0;\n  min-width: 48px;\n}\n.lesson-row__time[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: var(--text-sm);\n  font-weight: 600;\n  font-variant-numeric: tabular-nums;\n  color: var(--text-primary);\n}\n.lesson-row__time--end[_ngcontent-%COMP%] {\n  color: var(--text-muted);\n  font-weight: 400;\n}\n.lesson-row__dot[_ngcontent-%COMP%] {\n  width: 12px;\n  height: 12px;\n  border-radius: 50%;\n  background: var(--text-muted);\n  transition: background var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);\n}\n.lesson-row__dot--muted[_ngcontent-%COMP%] {\n  background: var(--text-muted);\n}\n.lesson-row__dot--active[_ngcontent-%COMP%] {\n  background: var(--accent-secondary);\n  box-shadow: 0 0 12px var(--accent-secondary);\n}\n.lesson-row__dot--present[_ngcontent-%COMP%] {\n  background: var(--status-present);\n  box-shadow: 0 0 12px color-mix(in srgb, var(--status-present) 60%, transparent);\n}\n.lesson-row__dot--absent[_ngcontent-%COMP%] {\n  background: var(--status-absent);\n  box-shadow: 0 0 12px color-mix(in srgb, var(--status-absent) 60%, transparent);\n}\n.lesson-row__dot--excused[_ngcontent-%COMP%] {\n  background: var(--status-excused);\n  box-shadow: 0 0 12px color-mix(in srgb, var(--status-excused) 60%, transparent);\n}\n.lesson-row__body[_ngcontent-%COMP%] {\n  flex: 1;\n  min-width: 0;\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-1);\n}\n.lesson-row__head[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: flex-start;\n  justify-content: space-between;\n  gap: var(--space-3);\n}\n.lesson-row__subject[_ngcontent-%COMP%] {\n  margin: 0;\n  font-family: var(--font-heading);\n  font-size: var(--text-base);\n  font-weight: 600;\n  line-height: var(--leading-heading);\n  color: var(--text-primary);\n}\n.lesson-row__meta[_ngcontent-%COMP%] {\n  margin: 0;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  font-size: var(--text-xs);\n  color: var(--text-secondary);\n}\n.lesson-row__meta[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 14px;\n}\n.lesson-row__detail[_ngcontent-%COMP%] {\n  margin-top: var(--space-2);\n  padding: var(--space-4);\n  border-radius: var(--radius-md);\n  background: var(--bg-elevated);\n  border: 1px solid var(--border-subtle);\n}\n.lesson-row__detail-list[_ngcontent-%COMP%] {\n  margin: 0;\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2);\n}\n.lesson-row__detail-list[_ngcontent-%COMP%]   div[_ngcontent-%COMP%] {\n  display: flex;\n  justify-content: space-between;\n  gap: var(--space-3);\n  font-size: var(--text-xs);\n}\n.lesson-row__detail-list[_ngcontent-%COMP%]   dt[_ngcontent-%COMP%] {\n  color: var(--text-muted);\n  text-transform: uppercase;\n  letter-spacing: var(--tracking-wide);\n}\n.lesson-row__detail-list[_ngcontent-%COMP%]   dd[_ngcontent-%COMP%] {\n  margin: 0;\n  color: var(--text-primary);\n  font-variant-numeric: tabular-nums;\n  text-align: right;\n}\n.status-chip--active[_ngcontent-%COMP%] {\n  background: color-mix(in srgb, var(--accent-primary) 14%, transparent);\n  color: var(--accent-primary);\n  padding: 2px 8px;\n  border-radius: var(--radius-full);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  line-height: 1.4;\n  width: auto;\n  height: auto;\n}\n.status-chip--cancelled[_ngcontent-%COMP%] {\n  background: color-mix(in srgb, var(--text-muted) 14%, transparent);\n  color: var(--text-muted);\n  padding: 2px 8px;\n  border-radius: var(--radius-full);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  line-height: 1.4;\n  width: auto;\n  height: auto;\n}\n.status-chip--planned[_ngcontent-%COMP%] {\n  background: color-mix(in srgb, var(--text-secondary) 12%, transparent);\n  color: var(--text-secondary);\n  padding: 2px 8px;\n  border-radius: var(--radius-full);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  line-height: 1.4;\n  width: auto;\n  height: auto;\n}\n.status-chip--present[_ngcontent-%COMP%], \n.status-chip--absent[_ngcontent-%COMP%], \n.status-chip--excused[_ngcontent-%COMP%], \n.status-chip--free_attendance[_ngcontent-%COMP%] {\n  padding: 2px 8px;\n  border-radius: var(--radius-full);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  line-height: 1.4;\n  width: auto;\n  height: auto;\n}\n.status-chip--present[_ngcontent-%COMP%] {\n  background: color-mix(in srgb, var(--accent-success, var(--accent-primary)) 16%, transparent);\n  color: var(--accent-success, var(--accent-primary));\n}\n.status-chip--absent[_ngcontent-%COMP%] {\n  background: color-mix(in srgb, var(--accent-danger) 16%, transparent);\n  color: var(--accent-danger);\n}\n.status-chip--excused[_ngcontent-%COMP%], \n.status-chip--free_attendance[_ngcontent-%COMP%] {\n  background: color-mix(in srgb, var(--accent-warning) 16%, transparent);\n  color: var(--accent-warning);\n}\n.lesson-row__detail-actions[_ngcontent-%COMP%] {\n  margin-top: var(--space-3);\n  display: flex;\n  flex-wrap: wrap;\n  gap: var(--space-2);\n}\n.lesson-row__detail-btn[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  min-height: 36px;\n  padding: 0 var(--space-4);\n  border-radius: var(--radius-pill, 9999px);\n  font-size: var(--text-sm);\n  font-weight: 500;\n  cursor: pointer;\n  transition: background 150ms ease, border-color 150ms ease;\n  white-space: nowrap;\n}\n.lesson-row__detail-btn--primary[_ngcontent-%COMP%] {\n  background: var(--accent-primary);\n  border: 1px solid var(--accent-primary);\n  color: var(--bg-primary, #fff);\n}\n.lesson-row__detail-btn--primary[_ngcontent-%COMP%]:hover:not(:disabled) {\n  background: color-mix(in srgb, var(--accent-primary) 85%, black);\n}\n.lesson-row__detail-btn--secondary[_ngcontent-%COMP%] {\n  background: transparent;\n  border: 1px solid var(--accent-primary);\n  color: var(--accent-primary);\n}\n.lesson-row__detail-btn--secondary[_ngcontent-%COMP%]:hover:not(:disabled) {\n  background: color-mix(in srgb, var(--accent-primary) 8%, transparent);\n}\n.lesson-row__detail-btn[_ngcontent-%COMP%]:disabled {\n  opacity: 0.55;\n  cursor: not-allowed;\n}\n.lesson-row__detail-sent[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  font-size: var(--text-sm);\n  color: var(--accent-primary);\n}\n.lesson-row__detail-error[_ngcontent-%COMP%] {\n  margin: var(--space-1) 0 0;\n  font-size: var(--text-xs);\n  color: var(--accent-danger);\n}\n@media (prefers-reduced-motion: reduce) {\n  .lesson-row[_ngcontent-%COMP%] {\n    transition: none;\n  }\n}\n/*# sourceMappingURL=lesson-row.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(LessonRowComponent, [{
    type: Component,
    args: [{ selector: "app-lesson-row", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [NgClass], template: `<button\r
  type="button"\r
  class="lesson-row"\r
  [ngClass]="{\r
    'lesson-row--active': isActive,\r
    'lesson-row--cancelled': isCancelled,\r
    'lesson-row--expanded': expanded\r
  }"\r
  [attr.aria-expanded]="expanded"\r
  [attr.aria-controls]="expanded ? 'lesson-detail-' + lesson.id : null"\r
  [attr.data-lesson-id]="lesson.id"\r
  (click)="onClick()"\r
>\r
  <div class="lesson-row__rail" aria-hidden="true">\r
    <span class="lesson-row__time lesson-row__time--start">{{ startLabel }}</span>\r
    <span class="lesson-row__dot" [ngClass]="dotStateClass"></span>\r
    <span class="lesson-row__time lesson-row__time--end">{{ endLabel }}</span>\r
  </div>\r
\r
  <div class="lesson-row__body">\r
    <div class="lesson-row__head">\r
      <h3 class="lesson-row__subject">{{ subjectName }}</h3>\r
      <span [class]="statusChipClass">{{ statusLabel }}</span>\r
    </div>\r
    <p class="lesson-row__meta">\r
      <i class="ph ph-map-pin" aria-hidden="true"></i>\r
      <span>\u0410\u0443\u0434. {{ lesson.room }}</span>\r
    </p>\r
  </div>\r
</button>\r
\r
@if (expanded) {\r
  <div\r
    class="lesson-row__detail"\r
    [attr.id]="'lesson-detail-' + lesson.id"\r
    role="region"\r
  >\r
    <dl class="lesson-row__detail-list">\r
      <div>\r
        <dt>\u041D\u043E\u043C\u0435\u0440 \u043F\u0430\u0440\u044B</dt>\r
        <dd>{{ lesson.lessonNumber }}</dd>\r
      </div>\r
      <div>\r
        <dt>\u0422\u0438\u043F</dt>\r
        <dd>{{ lessonTypeLabel }}</dd>\r
      </div>\r
      <div>\r
        <dt>\u0410\u0443\u0434\u0438\u0442\u043E\u0440\u0438\u044F</dt>\r
        <dd>{{ lesson.room }}</dd>\r
      </div>\r
      <div>\r
        <dt>\u0414\u043B\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0441\u0442\u044C</dt>\r
        <dd>{{ startLabel }} \u2014 {{ endLabel }}</dd>\r
      </div>\r
      @if (lesson.teacherId) {\r
        <div>\r
          <dt>\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C</dt>\r
          <dd>ID {{ lesson.teacherId }}</dd>\r
        </div>\r
      }\r
      @if (lesson.cancelReason) {\r
        <div>\r
          <dt>\u041F\u0440\u0438\u0447\u0438\u043D\u0430 \u043E\u0442\u043C\u0435\u043D\u044B</dt>\r
          <dd>{{ lesson.cancelReason }}</dd>\r
        </div>\r
      }\r
    </dl>\r
\r
    <div class="lesson-row__detail-actions">\r
      <button\r
        type="button"\r
        class="lesson-row__detail-btn lesson-row__detail-btn--primary"\r
        [disabled]="!canRequestExcuse"\r
        (click)="onRequestExcuse($event)"\r
      >\r
        <i class="ph ph-note-pencil" aria-hidden="true"></i>\r
        \u0417\u0430\u044F\u0432\u0438\u0442\u044C \u043E\u0431 \u0443\u0432\u0430\u0436\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0439 \u043F\u0440\u0438\u0447\u0438\u043D\u0435\r
      </button>\r
\r
      @if (lateCheckinSent) {\r
        <span class="lesson-row__detail-sent" role="status" aria-label="\u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D">\r
          <i class="ph-fill ph-check-circle" aria-hidden="true"></i>\r
          \u0417\u0430\u043F\u0440\u043E\u0441 \u043D\u0430 \u043E\u0442\u043C\u0435\u0442\u043A\u0443 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D\r
        </span>\r
      } @else {\r
        <button\r
          type="button"\r
          class="lesson-row__detail-btn lesson-row__detail-btn--secondary"\r
          [disabled]="!canRequestLateCheckin || lateCheckinPending"\r
          [attr.aria-busy]="lateCheckinPending"\r
          (click)="onRequestLateCheckin($event)"\r
        >\r
          <i class="ph ph-clock-countdown" aria-hidden="true"></i>\r
          \u0417\u0430\u044F\u0432\u0438\u0442\u044C \u043E \u043F\u043E\u0441\u0435\u0449\u0435\u043D\u0438\u0438\r
        </button>\r
      }\r
    </div>\r
\r
    @if (lateCheckinError) {\r
      <p class="lesson-row__detail-error" role="alert">{{ lateCheckinError }}</p>\r
    }\r
  </div>\r
}\r
`, styles: ["/* src/app/features/student/schedule/lesson-row/lesson-row.component.css */\n:host {\n  display: block;\n}\n.lesson-row {\n  display: flex;\n  gap: var(--space-4);\n  width: 100%;\n  padding: var(--space-4);\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-lg);\n  background: var(--bg-secondary);\n  color: var(--text-primary);\n  text-align: left;\n  cursor: pointer;\n  transition:\n    border-color var(--duration-base) var(--ease-out),\n    box-shadow var(--duration-base) var(--ease-out),\n    transform var(--duration-base) var(--ease-out);\n}\n.lesson-row:hover:not(.lesson-row--cancelled) {\n  border-color: var(--border-default);\n}\n.lesson-row:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.lesson-row--active {\n  border-color: var(--accent-secondary);\n  box-shadow: var(--glow-blue);\n}\n.lesson-row--cancelled {\n  opacity: 0.55;\n  cursor: default;\n}\n.lesson-row--cancelled .lesson-row__subject {\n  text-decoration: line-through;\n}\n.lesson-row__rail {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-2);\n  flex-shrink: 0;\n  min-width: 48px;\n}\n.lesson-row__time {\n  font-family: var(--font-mono);\n  font-size: var(--text-sm);\n  font-weight: 600;\n  font-variant-numeric: tabular-nums;\n  color: var(--text-primary);\n}\n.lesson-row__time--end {\n  color: var(--text-muted);\n  font-weight: 400;\n}\n.lesson-row__dot {\n  width: 12px;\n  height: 12px;\n  border-radius: 50%;\n  background: var(--text-muted);\n  transition: background var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);\n}\n.lesson-row__dot--muted {\n  background: var(--text-muted);\n}\n.lesson-row__dot--active {\n  background: var(--accent-secondary);\n  box-shadow: 0 0 12px var(--accent-secondary);\n}\n.lesson-row__dot--present {\n  background: var(--status-present);\n  box-shadow: 0 0 12px color-mix(in srgb, var(--status-present) 60%, transparent);\n}\n.lesson-row__dot--absent {\n  background: var(--status-absent);\n  box-shadow: 0 0 12px color-mix(in srgb, var(--status-absent) 60%, transparent);\n}\n.lesson-row__dot--excused {\n  background: var(--status-excused);\n  box-shadow: 0 0 12px color-mix(in srgb, var(--status-excused) 60%, transparent);\n}\n.lesson-row__body {\n  flex: 1;\n  min-width: 0;\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-1);\n}\n.lesson-row__head {\n  display: flex;\n  align-items: flex-start;\n  justify-content: space-between;\n  gap: var(--space-3);\n}\n.lesson-row__subject {\n  margin: 0;\n  font-family: var(--font-heading);\n  font-size: var(--text-base);\n  font-weight: 600;\n  line-height: var(--leading-heading);\n  color: var(--text-primary);\n}\n.lesson-row__meta {\n  margin: 0;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  font-size: var(--text-xs);\n  color: var(--text-secondary);\n}\n.lesson-row__meta i {\n  font-size: 14px;\n}\n.lesson-row__detail {\n  margin-top: var(--space-2);\n  padding: var(--space-4);\n  border-radius: var(--radius-md);\n  background: var(--bg-elevated);\n  border: 1px solid var(--border-subtle);\n}\n.lesson-row__detail-list {\n  margin: 0;\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2);\n}\n.lesson-row__detail-list div {\n  display: flex;\n  justify-content: space-between;\n  gap: var(--space-3);\n  font-size: var(--text-xs);\n}\n.lesson-row__detail-list dt {\n  color: var(--text-muted);\n  text-transform: uppercase;\n  letter-spacing: var(--tracking-wide);\n}\n.lesson-row__detail-list dd {\n  margin: 0;\n  color: var(--text-primary);\n  font-variant-numeric: tabular-nums;\n  text-align: right;\n}\n.status-chip--active {\n  background: color-mix(in srgb, var(--accent-primary) 14%, transparent);\n  color: var(--accent-primary);\n  padding: 2px 8px;\n  border-radius: var(--radius-full);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  line-height: 1.4;\n  width: auto;\n  height: auto;\n}\n.status-chip--cancelled {\n  background: color-mix(in srgb, var(--text-muted) 14%, transparent);\n  color: var(--text-muted);\n  padding: 2px 8px;\n  border-radius: var(--radius-full);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  line-height: 1.4;\n  width: auto;\n  height: auto;\n}\n.status-chip--planned {\n  background: color-mix(in srgb, var(--text-secondary) 12%, transparent);\n  color: var(--text-secondary);\n  padding: 2px 8px;\n  border-radius: var(--radius-full);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  line-height: 1.4;\n  width: auto;\n  height: auto;\n}\n.status-chip--present,\n.status-chip--absent,\n.status-chip--excused,\n.status-chip--free_attendance {\n  padding: 2px 8px;\n  border-radius: var(--radius-full);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  line-height: 1.4;\n  width: auto;\n  height: auto;\n}\n.status-chip--present {\n  background: color-mix(in srgb, var(--accent-success, var(--accent-primary)) 16%, transparent);\n  color: var(--accent-success, var(--accent-primary));\n}\n.status-chip--absent {\n  background: color-mix(in srgb, var(--accent-danger) 16%, transparent);\n  color: var(--accent-danger);\n}\n.status-chip--excused,\n.status-chip--free_attendance {\n  background: color-mix(in srgb, var(--accent-warning) 16%, transparent);\n  color: var(--accent-warning);\n}\n.lesson-row__detail-actions {\n  margin-top: var(--space-3);\n  display: flex;\n  flex-wrap: wrap;\n  gap: var(--space-2);\n}\n.lesson-row__detail-btn {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  min-height: 36px;\n  padding: 0 var(--space-4);\n  border-radius: var(--radius-pill, 9999px);\n  font-size: var(--text-sm);\n  font-weight: 500;\n  cursor: pointer;\n  transition: background 150ms ease, border-color 150ms ease;\n  white-space: nowrap;\n}\n.lesson-row__detail-btn--primary {\n  background: var(--accent-primary);\n  border: 1px solid var(--accent-primary);\n  color: var(--bg-primary, #fff);\n}\n.lesson-row__detail-btn--primary:hover:not(:disabled) {\n  background: color-mix(in srgb, var(--accent-primary) 85%, black);\n}\n.lesson-row__detail-btn--secondary {\n  background: transparent;\n  border: 1px solid var(--accent-primary);\n  color: var(--accent-primary);\n}\n.lesson-row__detail-btn--secondary:hover:not(:disabled) {\n  background: color-mix(in srgb, var(--accent-primary) 8%, transparent);\n}\n.lesson-row__detail-btn:disabled {\n  opacity: 0.55;\n  cursor: not-allowed;\n}\n.lesson-row__detail-sent {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  font-size: var(--text-sm);\n  color: var(--accent-primary);\n}\n.lesson-row__detail-error {\n  margin: var(--space-1) 0 0;\n  font-size: var(--text-xs);\n  color: var(--accent-danger);\n}\n@media (prefers-reduced-motion: reduce) {\n  .lesson-row {\n    transition: none;\n  }\n}\n/*# sourceMappingURL=lesson-row.component.css.map */\n"] }]
  }], null, { lesson: [{
    type: Input,
    args: [{ required: true }]
  }], subjectName: [{
    type: Input
  }], personalStatus: [{
    type: Input
  }], personalSource: [{
    type: Input
  }], expanded: [{
    type: Input
  }], lateCheckinSent: [{
    type: Input
  }], lateCheckinPending: [{
    type: Input
  }], lateCheckinError: [{
    type: Input
  }], toggle: [{
    type: Output
  }], requestExcuse: [{
    type: Output
  }], requestLateCheckin: [{
    type: Output
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(LessonRowComponent, { className: "LessonRowComponent", filePath: "src/app/features/student/schedule/lesson-row/lesson-row.component.ts", lineNumber: 40 });
})();

// src/app/features/student/schedule/student-schedule.component.ts
var _forTrack0 = ($index, $item) => $item.id;
function StudentScheduleComponent_For_19_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 19);
    \u0275\u0275listener("click", function StudentScheduleComponent_For_19_Template_button_click_0_listener() {
      const \u0275$index_33_r2 = \u0275\u0275restoreView(_r1).$index;
      const ctx_r2 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r2.selectDay(\u0275$index_33_r2));
    });
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const label_r4 = ctx.$implicit;
    const \u0275$index_33_r2 = ctx.$index;
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275classProp("schedule__day--selected", ctx_r2.selectedDayIndex() === \u0275$index_33_r2);
    \u0275\u0275attribute("aria-selected", ctx_r2.selectedDayIndex() === \u0275$index_33_r2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", label_r4, " ");
  }
}
function StudentScheduleComponent_Conditional_21_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 20)(1, "div", 20)(2, "div", 20)(3, "div", 20);
  }
}
function StudentScheduleComponent_Conditional_22_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 15);
    \u0275\u0275element(1, "i", 21);
    \u0275\u0275elementStart(2, "span");
    \u0275\u0275text(3);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate(ctx_r2.error());
  }
}
function StudentScheduleComponent_Conditional_23_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 16)(1, "div", 22);
    \u0275\u0275element(2, "i", 23);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "h3", 24);
    \u0275\u0275text(4, "\u0417\u0430\u043D\u044F\u0442\u0438\u0439 \u043D\u0435\u0442");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "p", 25);
    \u0275\u0275text(6, "\u0412 \u044D\u0442\u043E\u0442 \u0434\u0435\u043D\u044C \u043F\u0430\u0440 \u043D\u0435 \u0437\u0430\u043F\u043B\u0430\u043D\u0438\u0440\u043E\u0432\u0430\u043D\u043E.");
    \u0275\u0275elementEnd()();
  }
}
function StudentScheduleComponent_Conditional_24_For_2_Template(rf, ctx) {
  if (rf & 1) {
    const _r5 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "app-lesson-row", 27);
    \u0275\u0275pipe(1, "async");
    \u0275\u0275listener("toggle", function StudentScheduleComponent_Conditional_24_For_2_Template_app_lesson_row_toggle_0_listener($event) {
      \u0275\u0275restoreView(_r5);
      const ctx_r2 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r2.toggleLesson($event));
    })("requestExcuse", function StudentScheduleComponent_Conditional_24_For_2_Template_app_lesson_row_requestExcuse_0_listener($event) {
      \u0275\u0275restoreView(_r5);
      const ctx_r2 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r2.openExcuseDialog($event));
    })("requestLateCheckin", function StudentScheduleComponent_Conditional_24_For_2_Template_app_lesson_row_requestLateCheckin_0_listener($event) {
      \u0275\u0275restoreView(_r5);
      const ctx_r2 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r2.requestLateCheckin($event));
    });
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    let tmp_12_0;
    const lesson_r6 = ctx.$implicit;
    const ctx_r2 = \u0275\u0275nextContext(2);
    \u0275\u0275property("lesson", lesson_r6)("subjectName", (tmp_12_0 = \u0275\u0275pipeBind1(1, 8, ctx_r2.getSubjectName$(lesson_r6.subjectId))) !== null && tmp_12_0 !== void 0 ? tmp_12_0 : "\u041F\u0440\u0435\u0434\u043C\u0435\u0442")("expanded", ctx_r2.expandedLessonId() === lesson_r6.id)("personalStatus", ctx_r2.personalStatusFor(lesson_r6.id))("personalSource", ctx_r2.personalSourceFor(lesson_r6.id))("lateCheckinSent", ctx_r2.isLateCheckinSent(lesson_r6.id))("lateCheckinPending", ctx_r2.isLateCheckinPending(lesson_r6.id))("lateCheckinError", ctx_r2.getLateCheckinError(lesson_r6.id));
  }
}
function StudentScheduleComponent_Conditional_24_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 17);
    \u0275\u0275repeaterCreate(1, StudentScheduleComponent_Conditional_24_For_2_Template, 2, 10, "app-lesson-row", 26, _forTrack0);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275property("@daySlide", ctx_r2.selectedDayIndex());
    \u0275\u0275advance();
    \u0275\u0275repeater(ctx_r2.dayLessons());
  }
}
function StudentScheduleComponent_Conditional_25_Template(rf, ctx) {
  if (rf & 1) {
    const _r7 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 28);
    \u0275\u0275listener("click", function StudentScheduleComponent_Conditional_25_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r7);
      const ctx_r2 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r2.jumpToToday());
    });
    \u0275\u0275element(1, "i", 29);
    \u0275\u0275text(2, " \u0421\u0435\u0433\u043E\u0434\u043D\u044F ");
    \u0275\u0275elementEnd();
  }
}
var DAY_LABELS = ["\u041F\u043D", "\u0412\u0442", "\u0421\u0440", "\u0427\u0442", "\u041F\u0442", "\u0421\u0431"];
var StudentScheduleComponent = class _StudentScheduleComponent {
  studentApi = inject(StudentApiService);
  subjectCache = inject(SubjectCacheService);
  auth = inject(AuthService);
  destroyRef = inject(DestroyRef);
  dialog = inject(MatDialog);
  dayLabels = DAY_LABELS;
  currentWeekStart = signal(getMonday(/* @__PURE__ */ new Date()));
  selectedDayIndex = signal(getTodayDayIndex());
  lessons = signal([]);
  personalStateByLessonId = signal(/* @__PURE__ */ new Map());
  loading = signal(false);
  error = signal(null);
  expandedLessonId = signal(null);
  lateCheckinSent = signal(/* @__PURE__ */ new Set());
  lateCheckinPending = signal(/* @__PURE__ */ new Set());
  lateCheckinErrors = signal({});
  weekLabel = computed(() => formatWeekRange(this.currentWeekStart()));
  isCurrentWeek = computed(() => isSameWeek(this.currentWeekStart(), /* @__PURE__ */ new Date()));
  dayLessons = computed(() => {
    const backendDow = this.selectedDayIndex() + 1;
    return this.lessons().filter((l) => l.dayOfWeek === backendDow).sort((a, b) => a.lessonNumber - b.lessonNumber);
  });
  ngOnInit() {
    this.loadWeek();
  }
  loadWeek() {
    const user = this.auth.currentUser();
    const groupId = user?.groupId;
    if (!groupId) {
      this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u043F\u0440\u0435\u0434\u0435\u043B\u0438\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443 \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F.");
      this.lessons.set([]);
      this.loading.set(false);
      return;
    }
    const monday = this.currentWeekStart();
    const saturday = addDays(monday, 5);
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      lessons: this.studentApi.getWeekLessons(groupId, formatDate(monday), formatDate(saturday)),
      records: this.studentApi.getStudentRecords().pipe(catchError(() => of([])))
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ lessons, records }) => {
        this.lessons.set(lessons);
        const map = /* @__PURE__ */ new Map();
        for (const r of records) {
          if (r.status === "present" || r.status === "absent" || r.status === "excused" || r.status === "free_attendance") {
            map.set(r.lessonId, { status: r.status, source: r.source });
          }
        }
        this.personalStateByLessonId.set(map);
        this.loading.set(false);
      },
      error: () => {
        this.lessons.set([]);
        this.personalStateByLessonId.set(/* @__PURE__ */ new Map());
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0440\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043F\u043E\u0437\u0436\u0435.");
        this.loading.set(false);
      }
    });
  }
  personalStatusFor(lessonId) {
    return this.personalStateByLessonId().get(lessonId)?.status ?? null;
  }
  personalSourceFor(lessonId) {
    return this.personalStateByLessonId().get(lessonId)?.source ?? null;
  }
  isLateCheckinSent(lessonId) {
    return this.lateCheckinSent().has(lessonId);
  }
  isLateCheckinPending(lessonId) {
    return this.lateCheckinPending().has(lessonId);
  }
  getLateCheckinError(lessonId) {
    return this.lateCheckinErrors()[lessonId] ?? null;
  }
  /** Open excuse-ticket dialog preselected with this lesson. */
  openExcuseDialog(lessonId) {
    const lesson = this.lessons().find((l) => l.id === lessonId);
    if (!lesson)
      return;
    this.studentApi.getStudentRecords().pipe(catchError(() => of([]))).subscribe((records) => {
      const alreadyThere = records.some((r) => r.lessonId === lessonId);
      const seed = alreadyThere ? records : [
        {
          lessonId: lesson.id,
          subjectId: lesson.subjectId,
          lessonDate: lesson.date,
          lessonNumber: lesson.lessonNumber,
          status: "absent",
          symbol: "\u043D",
          source: "manual"
        },
        ...records
      ];
      this.dialog.open(ExcuseFormDialogComponent, {
        width: "560px",
        maxWidth: "100vw",
        maxHeight: "80vh",
        ariaLabel: "\u041D\u043E\u0432\u044B\u0439 \u0442\u0438\u043A\u0435\u0442 \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0435",
        data: { lessons: seed, preselectedLessonIds: [lessonId] }
      });
    });
  }
  requestLateCheckin(lessonId) {
    if (this.isLateCheckinSent(lessonId) || this.isLateCheckinPending(lessonId))
      return;
    this.lateCheckinErrors.update((e) => {
      const next = __spreadValues({}, e);
      delete next[lessonId];
      return next;
    });
    this.lateCheckinPending.update((set) => {
      const next = new Set(set);
      next.add(lessonId);
      return next;
    });
    this.studentApi.requestLateCheckin(lessonId).subscribe({
      next: () => {
        this.lateCheckinPending.update((set) => {
          const next = new Set(set);
          next.delete(lessonId);
          return next;
        });
        this.lateCheckinSent.update((set) => {
          const next = new Set(set);
          next.add(lessonId);
          return next;
        });
      },
      error: () => {
        this.lateCheckinPending.update((set) => {
          const next = new Set(set);
          next.delete(lessonId);
          return next;
        });
        this.lateCheckinErrors.update((e) => __spreadProps(__spreadValues({}, e), {
          [lessonId]: "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u0442\u043F\u0440\u0430\u0432\u0438\u0442\u044C \u0437\u0430\u043F\u0440\u043E\u0441. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437."
        }));
      }
    });
  }
  prevWeek() {
    this.currentWeekStart.update((d) => addDays(d, -7));
    this.expandedLessonId.set(null);
    this.loadWeek();
  }
  nextWeek() {
    this.currentWeekStart.update((d) => addDays(d, 7));
    this.expandedLessonId.set(null);
    this.loadWeek();
  }
  jumpToToday() {
    this.currentWeekStart.set(getMonday(/* @__PURE__ */ new Date()));
    this.selectedDayIndex.set(getTodayDayIndex());
    this.expandedLessonId.set(null);
    this.loadWeek();
  }
  selectDay(index) {
    this.selectedDayIndex.set(index);
    this.expandedLessonId.set(null);
  }
  toggleLesson(lessonId) {
    this.expandedLessonId.update((cur) => cur === lessonId ? null : lessonId);
  }
  /** Subject-name observable for the async pipe inside the template. */
  getSubjectName$(subjectId) {
    return this.subjectCache.getName(subjectId);
  }
  static \u0275fac = function StudentScheduleComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentScheduleComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentScheduleComponent, selectors: [["app-student-schedule"]], decls: 26, vars: 5, consts: [[1, "page-stack", "schedule"], [1, "page-header"], [1, "page-header__title"], [1, "page-header__subtitle"], ["aria-label", "\u041D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F \u043F\u043E \u043D\u0435\u0434\u0435\u043B\u044F\u043C", 1, "schedule__weeknav"], ["type", "button", "aria-label", "\u041F\u0440\u0435\u0434\u044B\u0434\u0443\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F", 1, "schedule__weeknav-btn", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-caret-left"], [1, "schedule__weeknav-label"], [1, "schedule__weeknav-eyebrow"], [1, "schedule__weeknav-range"], ["type", "button", "aria-label", "\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F", 1, "schedule__weeknav-btn", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-caret-right"], ["role", "tablist", "aria-label", "\u0414\u0435\u043D\u044C \u043D\u0435\u0434\u0435\u043B\u0438", 1, "schedule__days"], ["type", "button", "role", "tab", 1, "schedule__day", 3, "schedule__day--selected"], ["role", "tabpanel", 1, "schedule__list"], ["role", "alert", 1, "schedule__error"], [1, "schedule__empty"], [1, "schedule__day-list"], ["type", "button", 1, "schedule__today-pill"], ["type", "button", "role", "tab", 1, "schedule__day", 3, "click"], ["aria-hidden", "true", 1, "schedule-skeleton"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"], ["aria-hidden", "true", 1, "schedule__empty-icon"], [1, "ph", "ph-calendar-blank"], [1, "schedule__empty-title"], [1, "schedule__empty-text"], [3, "lesson", "subjectName", "expanded", "personalStatus", "personalSource", "lateCheckinSent", "lateCheckinPending", "lateCheckinError"], [3, "toggle", "requestExcuse", "requestLateCheckin", "lesson", "subjectName", "expanded", "personalStatus", "personalSource", "lateCheckinSent", "lateCheckinPending", "lateCheckinError"], ["type", "button", 1, "schedule__today-pill", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-calendar-blank"]], template: function StudentScheduleComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "section", 0)(1, "header", 1)(2, "div", 2)(3, "h2");
      \u0275\u0275text(4, "\u0420\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "p", 3);
      \u0275\u0275text(6, "\u041D\u0435\u0434\u0435\u043B\u044F \u0437\u0430\u043D\u044F\u0442\u0438\u0439 \u0432\u0430\u0448\u0435\u0439 \u0433\u0440\u0443\u043F\u043F\u044B");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(7, "nav", 4)(8, "button", 5);
      \u0275\u0275listener("click", function StudentScheduleComponent_Template_button_click_8_listener() {
        return ctx.prevWeek();
      });
      \u0275\u0275element(9, "i", 6);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(10, "div", 7)(11, "span", 8);
      \u0275\u0275text(12, "\u041D\u0435\u0434\u0435\u043B\u044F");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(13, "span", 9);
      \u0275\u0275text(14);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(15, "button", 10);
      \u0275\u0275listener("click", function StudentScheduleComponent_Template_button_click_15_listener() {
        return ctx.nextWeek();
      });
      \u0275\u0275element(16, "i", 11);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(17, "div", 12);
      \u0275\u0275repeaterCreate(18, StudentScheduleComponent_For_19_Template, 2, 4, "button", 13, \u0275\u0275repeaterTrackByIdentity);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(20, "div", 14);
      \u0275\u0275template(21, StudentScheduleComponent_Conditional_21_Template, 4, 0)(22, StudentScheduleComponent_Conditional_22_Template, 4, 1, "div", 15)(23, StudentScheduleComponent_Conditional_23_Template, 7, 0, "div", 16)(24, StudentScheduleComponent_Conditional_24_Template, 3, 1, "div", 17);
      \u0275\u0275elementEnd();
      \u0275\u0275template(25, StudentScheduleComponent_Conditional_25_Template, 3, 0, "button", 18);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(14);
      \u0275\u0275textInterpolate(ctx.weekLabel());
      \u0275\u0275advance(4);
      \u0275\u0275repeater(ctx.dayLabels);
      \u0275\u0275advance(2);
      \u0275\u0275attribute("aria-busy", ctx.loading() ? "true" : null);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.loading() ? 21 : ctx.error() ? 22 : ctx.dayLessons().length === 0 ? 23 : 24);
      \u0275\u0275advance(4);
      \u0275\u0275conditional(!ctx.isCurrentWeek() ? 25 : -1);
    }
  }, dependencies: [AsyncPipe, LessonRowComponent], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.schedule[_ngcontent-%COMP%] {\n  position: relative;\n}\n.schedule__weeknav[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  padding: var(--space-2) var(--space-4);\n  background: var(--bg-surface);\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-lg);\n}\n.schedule__weeknav-btn[_ngcontent-%COMP%] {\n  width: 44px;\n  height: 44px;\n  border-radius: var(--radius-full);\n  border: 0;\n  background: transparent;\n  color: var(--text-secondary);\n  cursor: pointer;\n  display: grid;\n  place-items: center;\n  transition: background var(--duration-fast) var(--ease-out), color var(--duration-fast) var(--ease-out);\n}\n.schedule__weeknav-btn[_ngcontent-%COMP%]:hover {\n  background: var(--bg-elevated);\n  color: var(--text-primary);\n}\n.schedule__weeknav-btn[_ngcontent-%COMP%]:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.schedule__weeknav-btn[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 20px;\n}\n.schedule__weeknav-label[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: 2px;\n}\n.schedule__weeknav-eyebrow[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  font-weight: 600;\n  color: var(--text-muted);\n  text-transform: uppercase;\n  letter-spacing: var(--tracking-wide);\n}\n.schedule__weeknav-range[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  font-weight: 600;\n  color: var(--text-primary);\n  font-variant-numeric: tabular-nums;\n}\n.schedule__days[_ngcontent-%COMP%] {\n  display: flex;\n  gap: var(--space-2);\n  border-bottom: 1px solid var(--border-subtle);\n  padding-bottom: var(--space-2);\n}\n.schedule__day[_ngcontent-%COMP%] {\n  flex: 1;\n  min-height: 44px;\n  padding: var(--space-2) var(--space-3);\n  border: 0;\n  background: transparent;\n  cursor: pointer;\n  font-family: var(--font-sans);\n  font-size: var(--text-sm);\n  font-weight: 400;\n  color: var(--text-muted);\n  border-radius: var(--radius-md) var(--radius-md) 0 0;\n  border-bottom: 2px solid transparent;\n  transition:\n    color var(--duration-fast) var(--ease-out),\n    background var(--duration-fast) var(--ease-out),\n    border-color var(--duration-fast) var(--ease-out);\n}\n.schedule__day[_ngcontent-%COMP%]:hover {\n  background: var(--bg-secondary);\n  color: var(--text-secondary);\n}\n.schedule__day--selected[_ngcontent-%COMP%] {\n  color: var(--text-primary);\n  font-weight: 600;\n  border-bottom-color: var(--accent-primary);\n}\n.schedule__day[_ngcontent-%COMP%]:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.schedule__list[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3);\n  min-height: 200px;\n}\n.schedule__day-list[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3);\n}\n.schedule-skeleton[_ngcontent-%COMP%] {\n  height: 80px;\n  border-radius: var(--radius-lg);\n  background:\n    linear-gradient(\n      90deg,\n      var(--bg-elevated) 25%,\n      color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,\n      var(--bg-elevated) 75%);\n  background-size: 200% 100%;\n  animation: _ngcontent-%COMP%_schedule-shimmer 1.5s linear infinite;\n}\n@keyframes _ngcontent-%COMP%_schedule-shimmer {\n  0% {\n    background-position: 200% 0;\n  }\n  100% {\n    background-position: -200% 0;\n  }\n}\n.schedule__empty[_ngcontent-%COMP%] {\n  padding: var(--space-7) var(--space-5);\n  border: 1px dashed var(--border-default);\n  border-radius: var(--radius-lg);\n  background: color-mix(in oklab, var(--bg-secondary) 50%, transparent);\n  text-align: center;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-3);\n}\n.schedule__empty-icon[_ngcontent-%COMP%] {\n  width: 64px;\n  height: 64px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);\n  border: 1px solid var(--border-accent);\n  color: var(--accent-primary);\n  display: grid;\n  place-items: center;\n}\n.schedule__empty-icon[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 32px;\n}\n.schedule__empty-title[_ngcontent-%COMP%] {\n  margin: 0;\n  font-family: var(--font-display);\n  font-size: var(--text-2xl);\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.schedule__empty-text[_ngcontent-%COMP%] {\n  margin: 0;\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n  max-width: 44ch;\n}\n.schedule__error[_ngcontent-%COMP%] {\n  padding: var(--space-4);\n  border-radius: var(--radius-md);\n  background: color-mix(in srgb, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in srgb, var(--accent-danger) 28%, transparent);\n  color: var(--accent-danger);\n  font-size: var(--text-sm);\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n}\n.schedule__today-pill[_ngcontent-%COMP%] {\n  position: fixed;\n  left: 50%;\n  bottom: var(--space-6);\n  transform: translateX(-50%);\n  padding: 10px 20px;\n  border-radius: var(--radius-full);\n  border: 0;\n  background: var(--gradient-brand);\n  color: var(--accent-primary-contrast);\n  font-family: var(--font-heading);\n  font-size: var(--text-sm);\n  font-weight: 600;\n  box-shadow: var(--glow-primary);\n  cursor: pointer;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  z-index: var(--z-sticky);\n  transition: filter var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out);\n}\n.schedule__today-pill[_ngcontent-%COMP%]:hover {\n  filter: brightness(1.08);\n}\n.schedule__today-pill[_ngcontent-%COMP%]:active {\n  transform: translateX(-50%) scale(0.97);\n}\n.schedule__today-pill[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 16px;\n}\n@media (prefers-reduced-motion: reduce) {\n  .schedule-skeleton[_ngcontent-%COMP%] {\n    animation: none;\n  }\n  .schedule__weeknav-btn[_ngcontent-%COMP%], \n   .schedule__day[_ngcontent-%COMP%], \n   .schedule__today-pill[_ngcontent-%COMP%] {\n    transition: none;\n  }\n}\n/*# sourceMappingURL=student-schedule.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ]),
    trigger("daySlide", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateX(8px)" }),
        animate("150ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateX(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentScheduleComponent, [{
    type: Component,
    args: [{ selector: "app-student-schedule", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [AsyncPipe, LessonRowComponent], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ]),
      trigger("daySlide", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateX(8px)" }),
          animate("150ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateX(0)" }))
        ])
      ])
    ], template: `<section class="page-stack schedule" [@routeFade]>\r
  <header class="page-header">\r
    <div class="page-header__title">\r
      <h2>\u0420\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435</h2>\r
      <p class="page-header__subtitle">\u041D\u0435\u0434\u0435\u043B\u044F \u0437\u0430\u043D\u044F\u0442\u0438\u0439 \u0432\u0430\u0448\u0435\u0439 \u0433\u0440\u0443\u043F\u043F\u044B</p>\r
    </div>\r
  </header>\r
\r
  <!-- Week navigation strip -->\r
  <nav class="schedule__weeknav" aria-label="\u041D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F \u043F\u043E \u043D\u0435\u0434\u0435\u043B\u044F\u043C">\r
    <button\r
      type="button"\r
      class="schedule__weeknav-btn"\r
      (click)="prevWeek()"\r
      aria-label="\u041F\u0440\u0435\u0434\u044B\u0434\u0443\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F"\r
    >\r
      <i class="ph ph-caret-left" aria-hidden="true"></i>\r
    </button>\r
    <div class="schedule__weeknav-label">\r
      <span class="schedule__weeknav-eyebrow">\u041D\u0435\u0434\u0435\u043B\u044F</span>\r
      <span class="schedule__weeknav-range">{{ weekLabel() }}</span>\r
    </div>\r
    <button\r
      type="button"\r
      class="schedule__weeknav-btn"\r
      (click)="nextWeek()"\r
      aria-label="\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F"\r
    >\r
      <i class="ph ph-caret-right" aria-hidden="true"></i>\r
    </button>\r
  </nav>\r
\r
  <!-- Day tabs -->\r
  <div class="schedule__days" role="tablist" aria-label="\u0414\u0435\u043D\u044C \u043D\u0435\u0434\u0435\u043B\u0438">\r
    @for (label of dayLabels; track label; let i = $index) {\r
      <button\r
        type="button"\r
        role="tab"\r
        class="schedule__day"\r
        [class.schedule__day--selected]="selectedDayIndex() === i"\r
        [attr.aria-selected]="selectedDayIndex() === i"\r
        (click)="selectDay(i)"\r
      >\r
        {{ label }}\r
      </button>\r
    }\r
  </div>\r
\r
  <!-- Lesson list -->\r
  <div\r
    class="schedule__list"\r
    role="tabpanel"\r
    [attr.aria-busy]="loading() ? 'true' : null"\r
  >\r
    @if (loading()) {\r
      <div class="schedule-skeleton" aria-hidden="true"></div>\r
      <div class="schedule-skeleton" aria-hidden="true"></div>\r
      <div class="schedule-skeleton" aria-hidden="true"></div>\r
      <div class="schedule-skeleton" aria-hidden="true"></div>\r
    } @else if (error()) {\r
      <div class="schedule__error" role="alert">\r
        <i class="ph ph-warning-circle" aria-hidden="true"></i>\r
        <span>{{ error() }}</span>\r
      </div>\r
    } @else if (dayLessons().length === 0) {\r
      <div class="schedule__empty">\r
        <div class="schedule__empty-icon" aria-hidden="true">\r
          <i class="ph ph-calendar-blank"></i>\r
        </div>\r
        <h3 class="schedule__empty-title">\u0417\u0430\u043D\u044F\u0442\u0438\u0439 \u043D\u0435\u0442</h3>\r
        <p class="schedule__empty-text">\u0412 \u044D\u0442\u043E\u0442 \u0434\u0435\u043D\u044C \u043F\u0430\u0440 \u043D\u0435 \u0437\u0430\u043F\u043B\u0430\u043D\u0438\u0440\u043E\u0432\u0430\u043D\u043E.</p>\r
      </div>\r
    } @else {\r
      <div [@daySlide]="selectedDayIndex()" class="schedule__day-list">\r
        @for (lesson of dayLessons(); track lesson.id) {\r
          <app-lesson-row\r
            [lesson]="lesson"\r
            [subjectName]="(getSubjectName$(lesson.subjectId) | async) ?? '\u041F\u0440\u0435\u0434\u043C\u0435\u0442'"\r
            [expanded]="expandedLessonId() === lesson.id"\r
            [personalStatus]="personalStatusFor(lesson.id)"\r
            [personalSource]="personalSourceFor(lesson.id)"\r
            [lateCheckinSent]="isLateCheckinSent(lesson.id)"\r
            [lateCheckinPending]="isLateCheckinPending(lesson.id)"\r
            [lateCheckinError]="getLateCheckinError(lesson.id)"\r
            (toggle)="toggleLesson($event)"\r
            (requestExcuse)="openExcuseDialog($event)"\r
            (requestLateCheckin)="requestLateCheckin($event)"\r
          />\r
        }\r
      </div>\r
    }\r
  </div>\r
\r
  <!-- "\u0421\u0435\u0433\u043E\u0434\u043D\u044F" floating pill -->\r
  @if (!isCurrentWeek()) {\r
    <button\r
      type="button"\r
      class="schedule__today-pill"\r
      (click)="jumpToToday()"\r
    >\r
      <i class="ph ph-calendar-blank" aria-hidden="true"></i>\r
      \u0421\u0435\u0433\u043E\u0434\u043D\u044F\r
    </button>\r
  }\r
</section>\r
`, styles: ["/* src/app/features/student/schedule/student-schedule.component.css */\n:host {\n  display: block;\n}\n.schedule {\n  position: relative;\n}\n.schedule__weeknav {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  padding: var(--space-2) var(--space-4);\n  background: var(--bg-surface);\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-lg);\n}\n.schedule__weeknav-btn {\n  width: 44px;\n  height: 44px;\n  border-radius: var(--radius-full);\n  border: 0;\n  background: transparent;\n  color: var(--text-secondary);\n  cursor: pointer;\n  display: grid;\n  place-items: center;\n  transition: background var(--duration-fast) var(--ease-out), color var(--duration-fast) var(--ease-out);\n}\n.schedule__weeknav-btn:hover {\n  background: var(--bg-elevated);\n  color: var(--text-primary);\n}\n.schedule__weeknav-btn:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.schedule__weeknav-btn i {\n  font-size: 20px;\n}\n.schedule__weeknav-label {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: 2px;\n}\n.schedule__weeknav-eyebrow {\n  font-size: var(--text-xs);\n  font-weight: 600;\n  color: var(--text-muted);\n  text-transform: uppercase;\n  letter-spacing: var(--tracking-wide);\n}\n.schedule__weeknav-range {\n  font-size: var(--text-sm);\n  font-weight: 600;\n  color: var(--text-primary);\n  font-variant-numeric: tabular-nums;\n}\n.schedule__days {\n  display: flex;\n  gap: var(--space-2);\n  border-bottom: 1px solid var(--border-subtle);\n  padding-bottom: var(--space-2);\n}\n.schedule__day {\n  flex: 1;\n  min-height: 44px;\n  padding: var(--space-2) var(--space-3);\n  border: 0;\n  background: transparent;\n  cursor: pointer;\n  font-family: var(--font-sans);\n  font-size: var(--text-sm);\n  font-weight: 400;\n  color: var(--text-muted);\n  border-radius: var(--radius-md) var(--radius-md) 0 0;\n  border-bottom: 2px solid transparent;\n  transition:\n    color var(--duration-fast) var(--ease-out),\n    background var(--duration-fast) var(--ease-out),\n    border-color var(--duration-fast) var(--ease-out);\n}\n.schedule__day:hover {\n  background: var(--bg-secondary);\n  color: var(--text-secondary);\n}\n.schedule__day--selected {\n  color: var(--text-primary);\n  font-weight: 600;\n  border-bottom-color: var(--accent-primary);\n}\n.schedule__day:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.schedule__list {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3);\n  min-height: 200px;\n}\n.schedule__day-list {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3);\n}\n.schedule-skeleton {\n  height: 80px;\n  border-radius: var(--radius-lg);\n  background:\n    linear-gradient(\n      90deg,\n      var(--bg-elevated) 25%,\n      color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,\n      var(--bg-elevated) 75%);\n  background-size: 200% 100%;\n  animation: schedule-shimmer 1.5s linear infinite;\n}\n@keyframes schedule-shimmer {\n  0% {\n    background-position: 200% 0;\n  }\n  100% {\n    background-position: -200% 0;\n  }\n}\n.schedule__empty {\n  padding: var(--space-7) var(--space-5);\n  border: 1px dashed var(--border-default);\n  border-radius: var(--radius-lg);\n  background: color-mix(in oklab, var(--bg-secondary) 50%, transparent);\n  text-align: center;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-3);\n}\n.schedule__empty-icon {\n  width: 64px;\n  height: 64px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);\n  border: 1px solid var(--border-accent);\n  color: var(--accent-primary);\n  display: grid;\n  place-items: center;\n}\n.schedule__empty-icon i {\n  font-size: 32px;\n}\n.schedule__empty-title {\n  margin: 0;\n  font-family: var(--font-display);\n  font-size: var(--text-2xl);\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.schedule__empty-text {\n  margin: 0;\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n  max-width: 44ch;\n}\n.schedule__error {\n  padding: var(--space-4);\n  border-radius: var(--radius-md);\n  background: color-mix(in srgb, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in srgb, var(--accent-danger) 28%, transparent);\n  color: var(--accent-danger);\n  font-size: var(--text-sm);\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n}\n.schedule__today-pill {\n  position: fixed;\n  left: 50%;\n  bottom: var(--space-6);\n  transform: translateX(-50%);\n  padding: 10px 20px;\n  border-radius: var(--radius-full);\n  border: 0;\n  background: var(--gradient-brand);\n  color: var(--accent-primary-contrast);\n  font-family: var(--font-heading);\n  font-size: var(--text-sm);\n  font-weight: 600;\n  box-shadow: var(--glow-primary);\n  cursor: pointer;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  z-index: var(--z-sticky);\n  transition: filter var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out);\n}\n.schedule__today-pill:hover {\n  filter: brightness(1.08);\n}\n.schedule__today-pill:active {\n  transform: translateX(-50%) scale(0.97);\n}\n.schedule__today-pill i {\n  font-size: 16px;\n}\n@media (prefers-reduced-motion: reduce) {\n  .schedule-skeleton {\n    animation: none;\n  }\n  .schedule__weeknav-btn,\n  .schedule__day,\n  .schedule__today-pill {\n    transition: none;\n  }\n}\n/*# sourceMappingURL=student-schedule.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentScheduleComponent, { className: "StudentScheduleComponent", filePath: "src/app/features/student/schedule/student-schedule.component.ts", lineNumber: 103 });
})();
export {
  StudentScheduleComponent
};
//# sourceMappingURL=chunk-Z3SSJLAT.js.map
