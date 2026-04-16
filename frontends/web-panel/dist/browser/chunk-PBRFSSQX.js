import {
  HomeworkCardComponent
} from "./chunk-EI5Q5OC5.js";
import {
  MatSlideToggle,
  MatSlideToggleModule
} from "./chunk-CW5F4M6Y.js";
import {
  MONTH_ABBREV,
  addDays,
  formatDate,
  getMonday
} from "./chunk-7F5XKO6G.js";
import "./chunk-MQ7WFICG.js";
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
import "./chunk-POR7KUSL.js";
import "./chunk-75YPR2VK.js";
import "./chunk-YIJEORIR.js";
import "./chunk-B3BDHDAM.js";
import {
  FormsModule
} from "./chunk-XAMIXVT7.js";
import {
  CommonModule
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
  computed,
  inject,
  of,
  setClassMetadata,
  signal,
  switchMap,
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
  ɵɵproperty,
  ɵɵpureFunction0,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵrepeaterTrackByIdentity,
  ɵɵrepeaterTrackByIndex,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-MFRIGFR2.js";

// src/app/shared/segmented-control/segmented-control.component.ts
var _forTrack0 = ($index, $item) => $item.value;
function SegmentedControlComponent_For_2_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "i", 4);
  }
  if (rf & 2) {
    const opt_r2 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275classMap("ph " + opt_r2.icon);
  }
}
function SegmentedControlComponent_For_2_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 2);
    \u0275\u0275listener("click", function SegmentedControlComponent_For_2_Template_button_click_0_listener() {
      const opt_r2 = \u0275\u0275restoreView(_r1).$implicit;
      const ctx_r2 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r2.select(opt_r2.value));
    });
    \u0275\u0275template(1, SegmentedControlComponent_For_2_Conditional_1_Template, 1, 2, "i", 3);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const opt_r2 = ctx.$implicit;
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275classProp("seg-ctrl__btn--active", opt_r2.value === ctx_r2.value);
    \u0275\u0275attribute("aria-checked", opt_r2.value === ctx_r2.value ? "true" : "false");
    \u0275\u0275advance();
    \u0275\u0275conditional(opt_r2.icon ? 1 : -1);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", opt_r2.label, " ");
  }
}
var SegmentedControlComponent = class _SegmentedControlComponent {
  options;
  value;
  valueChange = new EventEmitter();
  select(v) {
    if (v !== this.value) {
      this.valueChange.emit(v);
    }
  }
  static \u0275fac = function SegmentedControlComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _SegmentedControlComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _SegmentedControlComponent, selectors: [["app-segmented-control"]], inputs: { options: "options", value: "value" }, outputs: { valueChange: "valueChange" }, decls: 3, vars: 0, consts: [["role", "radiogroup", "aria-label", "\u041F\u0435\u0440\u0435\u043A\u043B\u044E\u0447\u0430\u0442\u0435\u043B\u044C \u0440\u0435\u0436\u0438\u043C\u0430", 1, "seg-ctrl"], ["type", "button", "role", "radio", 1, "seg-ctrl__btn", 3, "seg-ctrl__btn--active"], ["type", "button", "role", "radio", 1, "seg-ctrl__btn", 3, "click"], ["aria-hidden", "true", 3, "class"], ["aria-hidden", "true"]], template: function SegmentedControlComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0);
      \u0275\u0275repeaterCreate(1, SegmentedControlComponent_For_2_Template, 3, 5, "button", 1, _forTrack0);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275advance();
      \u0275\u0275repeater(ctx.options);
    }
  }, dependencies: [CommonModule], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.seg-ctrl[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: 2px;\n  background: var(--bg-secondary, #f5f5f5);\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-radius: var(--radius-full, 999px);\n  padding: 3px;\n}\n.seg-ctrl__btn[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: 4px;\n  padding: 6px 14px;\n  border: none;\n  border-radius: var(--radius-full, 999px);\n  background: transparent;\n  color: var(--text-secondary, #666);\n  font-size: 0.875rem;\n  font-weight: 500;\n  cursor: pointer;\n  transition:\n    background 150ms ease,\n    color 150ms ease,\n    box-shadow 150ms ease;\n  white-space: nowrap;\n  min-height: 36px;\n}\n.seg-ctrl__btn[_ngcontent-%COMP%]:hover:not(.seg-ctrl__btn--active) {\n  background: var(--bg-hover, #eee);\n  color: var(--text-primary);\n}\n.seg-ctrl__btn--active[_ngcontent-%COMP%] {\n  background: var(--accent-primary, #1565c0);\n  color: #ffffff;\n  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);\n}\n/*# sourceMappingURL=segmented-control.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(SegmentedControlComponent, [{
    type: Component,
    args: [{ selector: "app-segmented-control", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule], template: `<div
  class="seg-ctrl"
  role="radiogroup"
  aria-label="\u041F\u0435\u0440\u0435\u043A\u043B\u044E\u0447\u0430\u0442\u0435\u043B\u044C \u0440\u0435\u0436\u0438\u043C\u0430">

  @for (opt of options; track opt.value) {
    <button
      type="button"
      role="radio"
      class="seg-ctrl__btn"
      [class.seg-ctrl__btn--active]="opt.value === value"
      [attr.aria-checked]="opt.value === value ? 'true' : 'false'"
      (click)="select(opt.value)">
      @if (opt.icon) {
        <i [class]="'ph ' + opt.icon" aria-hidden="true"></i>
      }
      {{ opt.label }}
    </button>
  }

</div>
`, styles: ["/* src/app/shared/segmented-control/segmented-control.component.css */\n:host {\n  display: block;\n}\n.seg-ctrl {\n  display: inline-flex;\n  align-items: center;\n  gap: 2px;\n  background: var(--bg-secondary, #f5f5f5);\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-radius: var(--radius-full, 999px);\n  padding: 3px;\n}\n.seg-ctrl__btn {\n  display: inline-flex;\n  align-items: center;\n  gap: 4px;\n  padding: 6px 14px;\n  border: none;\n  border-radius: var(--radius-full, 999px);\n  background: transparent;\n  color: var(--text-secondary, #666);\n  font-size: 0.875rem;\n  font-weight: 500;\n  cursor: pointer;\n  transition:\n    background 150ms ease,\n    color 150ms ease,\n    box-shadow 150ms ease;\n  white-space: nowrap;\n  min-height: 36px;\n}\n.seg-ctrl__btn:hover:not(.seg-ctrl__btn--active) {\n  background: var(--bg-hover, #eee);\n  color: var(--text-primary);\n}\n.seg-ctrl__btn--active {\n  background: var(--accent-primary, #1565c0);\n  color: #ffffff;\n  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);\n}\n/*# sourceMappingURL=segmented-control.component.css.map */\n"] }]
  }], null, { options: [{
    type: Input,
    args: [{ required: true }]
  }], value: [{
    type: Input,
    args: [{ required: true }]
  }], valueChange: [{
    type: Output
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(SegmentedControlComponent, { className: "SegmentedControlComponent", filePath: "src/app/shared/segmented-control/segmented-control.component.ts", lineNumber: 37 });
})();

// src/app/features/student/homework/day-view/student-homework-day-view.component.ts
var _forTrack02 = ($index, $item) => $item.id;
function StudentHomeworkDayViewComponent_Conditional_8_For_2_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "app-homework-card", 10);
    \u0275\u0275listener("completeToggled", function StudentHomeworkDayViewComponent_Conditional_8_For_2_Template_app_homework_card_completeToggled_0_listener($event) {
      \u0275\u0275restoreView(_r1);
      const ctx_r1 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r1.onCompleteToggled($event));
    });
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const item_r3 = ctx.$implicit;
    \u0275\u0275property("homework", item_r3)("showCompleteCheckbox", true);
  }
}
function StudentHomeworkDayViewComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 7);
    \u0275\u0275repeaterCreate(1, StudentHomeworkDayViewComponent_Conditional_8_For_2_Template, 1, 2, "app-homework-card", 9, _forTrack02);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275repeater(ctx_r1.itemsForDay);
  }
}
function StudentHomeworkDayViewComponent_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 8);
    \u0275\u0275element(1, "i", 11);
    \u0275\u0275elementStart(2, "p");
    \u0275\u0275text(3, "\u041D\u0430 \u044D\u0442\u043E\u0442 \u0434\u0435\u043D\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u0439 \u043D\u0435\u0442");
    \u0275\u0275elementEnd()();
  }
}
var RU_DAYS = ["\u0432\u0441", "\u043F\u043D", "\u0432\u0442", "\u0441\u0440", "\u0447\u0442", "\u043F\u0442", "\u0441\u0431"];
var StudentHomeworkDayViewComponent = class _StudentHomeworkDayViewComponent {
  date;
  items;
  dateChanged = new EventEmitter();
  completeToggled = new EventEmitter();
  get dateKey() {
    return formatDate(this.date);
  }
  get itemsForDay() {
    const key = formatDate(this.date);
    return this.items.filter((h) => h.lessonDate === key);
  }
  get dateLabel() {
    const d = this.date;
    const dayName = RU_DAYS[d.getDay()];
    const dayNum = d.getDate();
    const monthName = MONTH_ABBREV[d.getMonth()];
    const year = d.getFullYear();
    const today = /* @__PURE__ */ new Date();
    const isToday = formatDate(d) === formatDate(today);
    const tomorrow = addDays(today, 1);
    const isTomorrow = formatDate(d) === formatDate(tomorrow);
    let prefix = "";
    if (isToday)
      prefix = "\u0421\u0435\u0433\u043E\u0434\u043D\u044F, ";
    else if (isTomorrow)
      prefix = "\u0417\u0430\u0432\u0442\u0440\u0430, ";
    return `${prefix}${dayName} ${dayNum} ${monthName} ${year}`;
  }
  previous() {
    this.dateChanged.emit(addDays(this.date, -1));
  }
  next() {
    this.dateChanged.emit(addDays(this.date, 1));
  }
  onCompleteToggled(event) {
    this.completeToggled.emit(event);
  }
  static \u0275fac = function StudentHomeworkDayViewComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentHomeworkDayViewComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentHomeworkDayViewComponent, selectors: [["app-student-homework-day-view"]], inputs: { date: "date", items: "items" }, outputs: { dateChanged: "dateChanged", completeToggled: "completeToggled" }, decls: 10, vars: 2, consts: [[1, "day-view"], ["aria-label", "\u041D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F \u043F\u043E \u0434\u043D\u044F\u043C", 1, "day-nav"], ["type", "button", "aria-label", "\u041F\u0440\u0435\u0434\u044B\u0434\u0443\u0449\u0438\u0439 \u0434\u0435\u043D\u044C", 1, "day-nav__btn", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-caret-left"], [1, "day-nav__label"], ["type", "button", "aria-label", "\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0438\u0439 \u0434\u0435\u043D\u044C", 1, "day-nav__btn", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-caret-right"], ["role", "list", "aria-label", "\u0417\u0430\u0434\u0430\u043D\u0438\u044F \u043D\u0430 \u0434\u0435\u043D\u044C", 1, "day-view__list"], [1, "day-view__empty"], [3, "homework", "showCompleteCheckbox"], [3, "completeToggled", "homework", "showCompleteCheckbox"], ["aria-hidden", "true", 1, "ph", "ph-check-circle"]], template: function StudentHomeworkDayViewComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "nav", 1)(2, "button", 2);
      \u0275\u0275listener("click", function StudentHomeworkDayViewComponent_Template_button_click_2_listener() {
        return ctx.previous();
      });
      \u0275\u0275element(3, "i", 3);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(4, "span", 4);
      \u0275\u0275text(5);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(6, "button", 5);
      \u0275\u0275listener("click", function StudentHomeworkDayViewComponent_Template_button_click_6_listener() {
        return ctx.next();
      });
      \u0275\u0275element(7, "i", 6);
      \u0275\u0275elementEnd()();
      \u0275\u0275template(8, StudentHomeworkDayViewComponent_Conditional_8_Template, 3, 0, "div", 7)(9, StudentHomeworkDayViewComponent_Conditional_9_Template, 4, 0, "div", 8);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275advance(5);
      \u0275\u0275textInterpolate(ctx.dateLabel);
      \u0275\u0275advance(3);
      \u0275\u0275conditional(ctx.itemsForDay.length > 0 ? 8 : 9);
    }
  }, dependencies: [CommonModule, HomeworkCardComponent], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.day-view[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3, 12px);\n}\n.day-nav[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  padding: var(--space-2, 8px) var(--space-3, 12px);\n  background: var(--bg-secondary, #f5f5f5);\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-radius: var(--radius-lg, 12px);\n}\n.day-nav__btn[_ngcontent-%COMP%] {\n  min-width: 40px;\n  min-height: 40px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  border: none;\n  background: transparent;\n  border-radius: var(--radius-md, 8px);\n  cursor: pointer;\n  color: var(--text-primary);\n  transition: background 150ms ease;\n}\n.day-nav__btn[_ngcontent-%COMP%]:hover {\n  background: var(--bg-hover, #eee);\n}\n.day-nav__label[_ngcontent-%COMP%] {\n  flex: 1;\n  text-align: center;\n  font-weight: 600;\n  font-size: 0.95rem;\n  color: var(--text-primary);\n}\n.day-view__list[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3, 12px);\n}\n.day-view__empty[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  padding: var(--space-8, 32px) var(--space-4, 16px);\n  color: var(--text-muted, #999);\n  text-align: center;\n}\n.day-view__empty[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 2.5rem;\n  opacity: 0.5;\n}\n.day-view__empty[_ngcontent-%COMP%]   p[_ngcontent-%COMP%] {\n  font-size: 0.9rem;\n}\n/*# sourceMappingURL=student-homework-day-view.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentHomeworkDayViewComponent, [{
    type: Component,
    args: [{ selector: "app-student-homework-day-view", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule, HomeworkCardComponent], template: '<div class="day-view">\n\n  <!-- Navigation bar -->\n  <nav class="day-nav" aria-label="\u041D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F \u043F\u043E \u0434\u043D\u044F\u043C">\n    <button\n      type="button"\n      class="day-nav__btn"\n      (click)="previous()"\n      aria-label="\u041F\u0440\u0435\u0434\u044B\u0434\u0443\u0449\u0438\u0439 \u0434\u0435\u043D\u044C">\n      <i class="ph ph-caret-left" aria-hidden="true"></i>\n    </button>\n    <span class="day-nav__label">{{ dateLabel }}</span>\n    <button\n      type="button"\n      class="day-nav__btn"\n      (click)="next()"\n      aria-label="\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0438\u0439 \u0434\u0435\u043D\u044C">\n      <i class="ph ph-caret-right" aria-hidden="true"></i>\n    </button>\n  </nav>\n\n  <!-- Items list -->\n  @if (itemsForDay.length > 0) {\n    <div class="day-view__list" role="list" aria-label="\u0417\u0430\u0434\u0430\u043D\u0438\u044F \u043D\u0430 \u0434\u0435\u043D\u044C">\n      @for (item of itemsForDay; track item.id) {\n        <app-homework-card\n          [homework]="item"\n          [showCompleteCheckbox]="true"\n          (completeToggled)="onCompleteToggled($event)">\n        </app-homework-card>\n      }\n    </div>\n  } @else {\n    <div class="day-view__empty">\n      <i class="ph ph-check-circle" aria-hidden="true"></i>\n      <p>\u041D\u0430 \u044D\u0442\u043E\u0442 \u0434\u0435\u043D\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u0439 \u043D\u0435\u0442</p>\n    </div>\n  }\n\n</div>\n', styles: ["/* src/app/features/student/homework/day-view/student-homework-day-view.component.css */\n:host {\n  display: block;\n}\n.day-view {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3, 12px);\n}\n.day-nav {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  padding: var(--space-2, 8px) var(--space-3, 12px);\n  background: var(--bg-secondary, #f5f5f5);\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-radius: var(--radius-lg, 12px);\n}\n.day-nav__btn {\n  min-width: 40px;\n  min-height: 40px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  border: none;\n  background: transparent;\n  border-radius: var(--radius-md, 8px);\n  cursor: pointer;\n  color: var(--text-primary);\n  transition: background 150ms ease;\n}\n.day-nav__btn:hover {\n  background: var(--bg-hover, #eee);\n}\n.day-nav__label {\n  flex: 1;\n  text-align: center;\n  font-weight: 600;\n  font-size: 0.95rem;\n  color: var(--text-primary);\n}\n.day-view__list {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3, 12px);\n}\n.day-view__empty {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  padding: var(--space-8, 32px) var(--space-4, 16px);\n  color: var(--text-muted, #999);\n  text-align: center;\n}\n.day-view__empty i {\n  font-size: 2.5rem;\n  opacity: 0.5;\n}\n.day-view__empty p {\n  font-size: 0.9rem;\n}\n/*# sourceMappingURL=student-homework-day-view.component.css.map */\n"] }]
  }], null, { date: [{
    type: Input,
    args: [{ required: true }]
  }], items: [{
    type: Input,
    args: [{ required: true }]
  }], dateChanged: [{
    type: Output
  }], completeToggled: [{
    type: Output
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentHomeworkDayViewComponent, { className: "StudentHomeworkDayViewComponent", filePath: "src/app/features/student/homework/day-view/student-homework-day-view.component.ts", lineNumber: 33 });
})();

// src/app/features/student/homework/week-view/student-homework-week-view.component.ts
var _forTrack03 = ($index, $item) => $item.dateKey;
var _forTrack1 = ($index, $item) => $item.id;
function StudentHomeworkWeekViewComponent_Conditional_8_For_1_Conditional_0_For_5_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "app-homework-card", 12);
    \u0275\u0275listener("completeToggled", function StudentHomeworkWeekViewComponent_Conditional_8_For_1_Conditional_0_For_5_Template_app_homework_card_completeToggled_0_listener($event) {
      \u0275\u0275restoreView(_r1);
      const ctx_r1 = \u0275\u0275nextContext(4);
      return \u0275\u0275resetView(ctx_r1.onCompleteToggled($event));
    });
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const item_r3 = ctx.$implicit;
    \u0275\u0275property("homework", item_r3)("showCompleteCheckbox", true);
  }
}
function StudentHomeworkWeekViewComponent_Conditional_8_For_1_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "section", 8)(1, "h3", 9);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "div", 10);
    \u0275\u0275repeaterCreate(4, StudentHomeworkWeekViewComponent_Conditional_8_For_1_Conditional_0_For_5_Template, 1, 2, "app-homework-card", 11, _forTrack1);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const day_r4 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275attribute("aria-label", day_r4.label);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(day_r4.label);
    \u0275\u0275advance(2);
    \u0275\u0275repeater(day_r4.items);
  }
}
function StudentHomeworkWeekViewComponent_Conditional_8_For_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275template(0, StudentHomeworkWeekViewComponent_Conditional_8_For_1_Conditional_0_Template, 6, 2, "section", 8);
  }
  if (rf & 2) {
    const day_r4 = ctx.$implicit;
    \u0275\u0275conditional(day_r4.items.length > 0 ? 0 : -1);
  }
}
function StudentHomeworkWeekViewComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275repeaterCreate(0, StudentHomeworkWeekViewComponent_Conditional_8_For_1_Template, 1, 1, null, null, _forTrack03);
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275repeater(ctx_r1.days);
  }
}
function StudentHomeworkWeekViewComponent_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 7);
    \u0275\u0275element(1, "i", 13);
    \u0275\u0275elementStart(2, "p");
    \u0275\u0275text(3, "\u041D\u0430 \u044D\u0442\u043E\u0439 \u043D\u0435\u0434\u0435\u043B\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u0439 \u043D\u0435\u0442");
    \u0275\u0275elementEnd()();
  }
}
var RU_DAY_FULL = ["\u0412\u043E\u0441\u043A\u0440\u0435\u0441\u0435\u043D\u044C\u0435", "\u041F\u043E\u043D\u0435\u0434\u0435\u043B\u044C\u043D\u0438\u043A", "\u0412\u0442\u043E\u0440\u043D\u0438\u043A", "\u0421\u0440\u0435\u0434\u0430", "\u0427\u0435\u0442\u0432\u0435\u0440\u0433", "\u041F\u044F\u0442\u043D\u0438\u0446\u0430", "\u0421\u0443\u0431\u0431\u043E\u0442\u0430"];
var StudentHomeworkWeekViewComponent = class _StudentHomeworkWeekViewComponent {
  monday;
  items;
  mondayChanged = new EventEmitter();
  completeToggled = new EventEmitter();
  get weekLabel() {
    const saturday = addDays(this.monday, 5);
    const s = this.monday;
    const e = saturday;
    if (s.getMonth() === e.getMonth()) {
      return `${s.getDate()}\u2013${e.getDate()} ${MONTH_ABBREV[e.getMonth()]}`;
    }
    return `${s.getDate()} ${MONTH_ABBREV[s.getMonth()]} \u2013 ${e.getDate()} ${MONTH_ABBREV[e.getMonth()]}`;
  }
  get days() {
    const rows = [];
    for (let i = 0; i < 6; i++) {
      const d = addDays(this.monday, i);
      const dateKey = formatDate(d);
      const dayItems = this.items.filter((h) => h.lessonDate === dateKey);
      const dayIndex = d.getDay();
      const label = `${RU_DAY_FULL[dayIndex]}, ${d.getDate()} ${MONTH_ABBREV[d.getMonth()]}`;
      rows.push({ date: d, dateKey, label, items: dayItems });
    }
    return rows;
  }
  get hasAnyItems() {
    return this.days.some((d) => d.items.length > 0);
  }
  previous() {
    this.mondayChanged.emit(addDays(this.monday, -7));
  }
  next() {
    this.mondayChanged.emit(addDays(this.monday, 7));
  }
  onCompleteToggled(event) {
    this.completeToggled.emit(event);
  }
  static \u0275fac = function StudentHomeworkWeekViewComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentHomeworkWeekViewComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentHomeworkWeekViewComponent, selectors: [["app-student-homework-week-view"]], inputs: { monday: "monday", items: "items" }, outputs: { mondayChanged: "mondayChanged", completeToggled: "completeToggled" }, decls: 10, vars: 2, consts: [[1, "week-view"], ["aria-label", "\u041D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F \u043F\u043E \u043D\u0435\u0434\u0435\u043B\u044F\u043C", 1, "week-nav"], ["type", "button", "aria-label", "\u041F\u0440\u0435\u0434\u044B\u0434\u0443\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F", 1, "week-nav__btn", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-caret-left"], [1, "week-nav__label"], ["type", "button", "aria-label", "\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F", 1, "week-nav__btn", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-caret-right"], [1, "week-view__empty"], [1, "week-view__day"], [1, "week-view__day-heading"], ["role", "list", 1, "week-view__day-items"], [3, "homework", "showCompleteCheckbox"], [3, "completeToggled", "homework", "showCompleteCheckbox"], ["aria-hidden", "true", 1, "ph", "ph-check-circle"]], template: function StudentHomeworkWeekViewComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "nav", 1)(2, "button", 2);
      \u0275\u0275listener("click", function StudentHomeworkWeekViewComponent_Template_button_click_2_listener() {
        return ctx.previous();
      });
      \u0275\u0275element(3, "i", 3);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(4, "span", 4);
      \u0275\u0275text(5);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(6, "button", 5);
      \u0275\u0275listener("click", function StudentHomeworkWeekViewComponent_Template_button_click_6_listener() {
        return ctx.next();
      });
      \u0275\u0275element(7, "i", 6);
      \u0275\u0275elementEnd()();
      \u0275\u0275template(8, StudentHomeworkWeekViewComponent_Conditional_8_Template, 2, 0)(9, StudentHomeworkWeekViewComponent_Conditional_9_Template, 4, 0, "div", 7);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275advance(5);
      \u0275\u0275textInterpolate(ctx.weekLabel);
      \u0275\u0275advance(3);
      \u0275\u0275conditional(ctx.hasAnyItems ? 8 : 9);
    }
  }, dependencies: [CommonModule, HomeworkCardComponent], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.week-view[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-4, 16px);\n}\n.week-nav[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  padding: var(--space-2, 8px) var(--space-3, 12px);\n  background: var(--bg-secondary, #f5f5f5);\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-radius: var(--radius-lg, 12px);\n}\n.week-nav__btn[_ngcontent-%COMP%] {\n  min-width: 40px;\n  min-height: 40px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  border: none;\n  background: transparent;\n  border-radius: var(--radius-md, 8px);\n  cursor: pointer;\n  color: var(--text-primary);\n  transition: background 150ms ease;\n}\n.week-nav__btn[_ngcontent-%COMP%]:hover {\n  background: var(--bg-hover, #eee);\n}\n.week-nav__label[_ngcontent-%COMP%] {\n  flex: 1;\n  text-align: center;\n  font-weight: 600;\n  font-size: 0.95rem;\n}\n.week-view__day[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2, 8px);\n}\n.week-view__day-heading[_ngcontent-%COMP%] {\n  font-size: 0.8rem;\n  font-weight: 600;\n  text-transform: uppercase;\n  letter-spacing: 0.04em;\n  color: var(--text-muted, #999);\n  margin: 0;\n}\n.week-view__day-items[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2, 8px);\n}\n.week-view__empty[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  padding: var(--space-8, 32px) var(--space-4, 16px);\n  color: var(--text-muted, #999);\n  text-align: center;\n}\n.week-view__empty[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 2.5rem;\n  opacity: 0.5;\n}\n.week-view__empty[_ngcontent-%COMP%]   p[_ngcontent-%COMP%] {\n  font-size: 0.9rem;\n}\n/*# sourceMappingURL=student-homework-week-view.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentHomeworkWeekViewComponent, [{
    type: Component,
    args: [{ selector: "app-student-homework-week-view", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule, HomeworkCardComponent], template: '<div class="week-view">\n\n  <!-- Navigation bar -->\n  <nav class="week-nav" aria-label="\u041D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F \u043F\u043E \u043D\u0435\u0434\u0435\u043B\u044F\u043C">\n    <button\n      type="button"\n      class="week-nav__btn"\n      (click)="previous()"\n      aria-label="\u041F\u0440\u0435\u0434\u044B\u0434\u0443\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F">\n      <i class="ph ph-caret-left" aria-hidden="true"></i>\n    </button>\n    <span class="week-nav__label">{{ weekLabel }}</span>\n    <button\n      type="button"\n      class="week-nav__btn"\n      (click)="next()"\n      aria-label="\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F">\n      <i class="ph ph-caret-right" aria-hidden="true"></i>\n    </button>\n  </nav>\n\n  <!-- Days list -->\n  @if (hasAnyItems) {\n    @for (day of days; track day.dateKey) {\n      @if (day.items.length > 0) {\n        <section class="week-view__day" [attr.aria-label]="day.label">\n          <h3 class="week-view__day-heading">{{ day.label }}</h3>\n          <div class="week-view__day-items" role="list">\n            @for (item of day.items; track item.id) {\n              <app-homework-card\n                [homework]="item"\n                [showCompleteCheckbox]="true"\n                (completeToggled)="onCompleteToggled($event)">\n              </app-homework-card>\n            }\n          </div>\n        </section>\n      }\n    }\n  } @else {\n    <div class="week-view__empty">\n      <i class="ph ph-check-circle" aria-hidden="true"></i>\n      <p>\u041D\u0430 \u044D\u0442\u043E\u0439 \u043D\u0435\u0434\u0435\u043B\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u0439 \u043D\u0435\u0442</p>\n    </div>\n  }\n\n</div>\n', styles: ["/* src/app/features/student/homework/week-view/student-homework-week-view.component.css */\n:host {\n  display: block;\n}\n.week-view {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-4, 16px);\n}\n.week-nav {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  padding: var(--space-2, 8px) var(--space-3, 12px);\n  background: var(--bg-secondary, #f5f5f5);\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-radius: var(--radius-lg, 12px);\n}\n.week-nav__btn {\n  min-width: 40px;\n  min-height: 40px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  border: none;\n  background: transparent;\n  border-radius: var(--radius-md, 8px);\n  cursor: pointer;\n  color: var(--text-primary);\n  transition: background 150ms ease;\n}\n.week-nav__btn:hover {\n  background: var(--bg-hover, #eee);\n}\n.week-nav__label {\n  flex: 1;\n  text-align: center;\n  font-weight: 600;\n  font-size: 0.95rem;\n}\n.week-view__day {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2, 8px);\n}\n.week-view__day-heading {\n  font-size: 0.8rem;\n  font-weight: 600;\n  text-transform: uppercase;\n  letter-spacing: 0.04em;\n  color: var(--text-muted, #999);\n  margin: 0;\n}\n.week-view__day-items {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2, 8px);\n}\n.week-view__empty {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  padding: var(--space-8, 32px) var(--space-4, 16px);\n  color: var(--text-muted, #999);\n  text-align: center;\n}\n.week-view__empty i {\n  font-size: 2.5rem;\n  opacity: 0.5;\n}\n.week-view__empty p {\n  font-size: 0.9rem;\n}\n/*# sourceMappingURL=student-homework-week-view.component.css.map */\n"] }]
  }], null, { monday: [{
    type: Input,
    args: [{ required: true }]
  }], items: [{
    type: Input,
    args: [{ required: true }]
  }], mondayChanged: [{
    type: Output
  }], completeToggled: [{
    type: Output
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentHomeworkWeekViewComponent, { className: "StudentHomeworkWeekViewComponent", filePath: "src/app/features/student/homework/week-view/student-homework-week-view.component.ts", lineNumber: 39 });
})();

// src/app/features/student/homework/month-view/student-homework-month-view.component.ts
var _forTrack04 = ($index, $item) => $item.dateKey;
function StudentHomeworkMonthViewComponent_For_11_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 9);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const dayName_r1 = ctx.$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(dayName_r1);
  }
}
function StudentHomeworkMonthViewComponent_For_13_For_2_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 16);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const cell_r3 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275classProp("month-grid__badge--alert", cell_r3.hasUncompleted);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", cell_r3.count, " ");
  }
}
function StudentHomeworkMonthViewComponent_For_13_For_2_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "span", 15);
  }
}
function StudentHomeworkMonthViewComponent_For_13_For_2_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 12);
    \u0275\u0275listener("click", function StudentHomeworkMonthViewComponent_For_13_For_2_Template_button_click_0_listener() {
      const cell_r3 = \u0275\u0275restoreView(_r2).$implicit;
      const ctx_r3 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r3.onCellClick(cell_r3));
    });
    \u0275\u0275elementStart(1, "span", 13);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275template(3, StudentHomeworkMonthViewComponent_For_13_For_2_Conditional_3_Template, 2, 3, "span", 14)(4, StudentHomeworkMonthViewComponent_For_13_For_2_Conditional_4_Template, 1, 0, "span", 15);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const cell_r3 = ctx.$implicit;
    \u0275\u0275classProp("month-grid__cell--other-month", !cell_r3.isCurrentMonth)("month-grid__cell--today", cell_r3.isToday)("month-grid__cell--has-items", cell_r3.count > 0);
    \u0275\u0275attribute("aria-label", cell_r3.dayNumber + " \u2014 " + cell_r3.count + " \u0437\u0430\u0434\u0430\u043D\u0438\u0439")("aria-pressed", false);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(cell_r3.dayNumber);
    \u0275\u0275advance();
    \u0275\u0275conditional(cell_r3.count > 0 ? 3 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(cell_r3.hasUncompleted ? 4 : -1);
  }
}
function StudentHomeworkMonthViewComponent_For_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 10);
    \u0275\u0275repeaterCreate(1, StudentHomeworkMonthViewComponent_For_13_For_2_Template, 5, 11, "button", 11, _forTrack04);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const week_r5 = ctx.$implicit;
    \u0275\u0275advance();
    \u0275\u0275repeater(week_r5);
  }
}
var RU_MONTH_FULL = [
  "\u042F\u043D\u0432\u0430\u0440\u044C",
  "\u0424\u0435\u0432\u0440\u0430\u043B\u044C",
  "\u041C\u0430\u0440\u0442",
  "\u0410\u043F\u0440\u0435\u043B\u044C",
  "\u041C\u0430\u0439",
  "\u0418\u044E\u043D\u044C",
  "\u0418\u044E\u043B\u044C",
  "\u0410\u0432\u0433\u0443\u0441\u0442",
  "\u0421\u0435\u043D\u0442\u044F\u0431\u0440\u044C",
  "\u041E\u043A\u0442\u044F\u0431\u0440\u044C",
  "\u041D\u043E\u044F\u0431\u0440\u044C",
  "\u0414\u0435\u043A\u0430\u0431\u0440\u044C"
];
var StudentHomeworkMonthViewComponent = class _StudentHomeworkMonthViewComponent {
  month;
  items;
  dateSelected = new EventEmitter();
  monthChanged = new EventEmitter();
  WEEK_DAYS = ["\u041F\u043D", "\u0412\u0442", "\u0421\u0440", "\u0427\u0442", "\u041F\u0442", "\u0421\u0431", "\u0412\u0441"];
  get monthLabel() {
    return `${RU_MONTH_FULL[this.month.getMonth()]} ${this.month.getFullYear()}`;
  }
  get cells() {
    const year = this.month.getFullYear();
    const monthIndex = this.month.getMonth();
    const firstDay = new Date(year, monthIndex, 1);
    const startMonday = getMonday(firstDay);
    const todayKey = formatDate(/* @__PURE__ */ new Date());
    const cells = [];
    for (let i = 0; i < 42; i++) {
      const d = addDays(startMonday, i);
      const dateKey = formatDate(d);
      const isCurrentMonth = d.getMonth() === monthIndex;
      const dayItems = this.items.filter((h) => h.lessonDate === dateKey);
      cells.push({
        date: d,
        dateKey,
        dayNumber: d.getDate(),
        isCurrentMonth,
        isToday: dateKey === todayKey,
        count: dayItems.length,
        hasUncompleted: dayItems.some((h) => !h.completed)
      });
    }
    return cells;
  }
  get weeks() {
    const all = this.cells;
    const result = [];
    for (let i = 0; i < 6; i++) {
      result.push(all.slice(i * 7, i * 7 + 7));
    }
    return result;
  }
  previousMonth() {
    const d = new Date(this.month);
    d.setMonth(d.getMonth() - 1);
    d.setDate(1);
    this.monthChanged.emit(d);
  }
  nextMonth() {
    const d = new Date(this.month);
    d.setMonth(d.getMonth() + 1);
    d.setDate(1);
    this.monthChanged.emit(d);
  }
  onCellClick(cell) {
    this.dateSelected.emit(cell.date);
  }
  static \u0275fac = function StudentHomeworkMonthViewComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentHomeworkMonthViewComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentHomeworkMonthViewComponent, selectors: [["app-student-homework-month-view"]], inputs: { month: "month", items: "items" }, outputs: { dateSelected: "dateSelected", monthChanged: "monthChanged" }, decls: 14, vars: 1, consts: [[1, "month-view"], ["aria-label", "\u041D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F \u043F\u043E \u043C\u0435\u0441\u044F\u0446\u0430\u043C", 1, "month-nav"], ["type", "button", "aria-label", "\u041F\u0440\u0435\u0434\u044B\u0434\u0443\u0449\u0438\u0439 \u043C\u0435\u0441\u044F\u0446", 1, "month-nav__btn", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-caret-left"], [1, "month-nav__label"], ["type", "button", "aria-label", "\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0438\u0439 \u043C\u0435\u0441\u044F\u0446", 1, "month-nav__btn", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-caret-right"], ["role", "grid", "aria-label", "\u041A\u0430\u043B\u0435\u043D\u0434\u0430\u0440\u044C \u0434\u043E\u043C\u0430\u0448\u043D\u0438\u0445 \u0437\u0430\u0434\u0430\u043D\u0438\u0439", 1, "month-grid"], ["role", "row", 1, "month-grid__header"], ["role", "columnheader", 1, "month-grid__weekday"], ["role", "row", 1, "month-grid__week"], ["type", "button", "role", "gridcell", 1, "month-grid__cell", 3, "month-grid__cell--other-month", "month-grid__cell--today", "month-grid__cell--has-items"], ["type", "button", "role", "gridcell", 1, "month-grid__cell", 3, "click"], [1, "month-grid__day-num"], [1, "month-grid__badge", 3, "month-grid__badge--alert"], ["aria-hidden", "true", 1, "month-grid__dot"], [1, "month-grid__badge"]], template: function StudentHomeworkMonthViewComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "nav", 1)(2, "button", 2);
      \u0275\u0275listener("click", function StudentHomeworkMonthViewComponent_Template_button_click_2_listener() {
        return ctx.previousMonth();
      });
      \u0275\u0275element(3, "i", 3);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(4, "span", 4);
      \u0275\u0275text(5);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(6, "button", 5);
      \u0275\u0275listener("click", function StudentHomeworkMonthViewComponent_Template_button_click_6_listener() {
        return ctx.nextMonth();
      });
      \u0275\u0275element(7, "i", 6);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(8, "div", 7)(9, "div", 8);
      \u0275\u0275repeaterCreate(10, StudentHomeworkMonthViewComponent_For_11_Template, 2, 1, "div", 9, \u0275\u0275repeaterTrackByIdentity);
      \u0275\u0275elementEnd();
      \u0275\u0275repeaterCreate(12, StudentHomeworkMonthViewComponent_For_13_Template, 3, 0, "div", 10, \u0275\u0275repeaterTrackByIndex);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275advance(5);
      \u0275\u0275textInterpolate(ctx.monthLabel);
      \u0275\u0275advance(5);
      \u0275\u0275repeater(ctx.WEEK_DAYS);
      \u0275\u0275advance(2);
      \u0275\u0275repeater(ctx.weeks);
    }
  }, dependencies: [CommonModule], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.month-view[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3, 12px);\n}\n.month-nav[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  padding: var(--space-2, 8px) var(--space-3, 12px);\n  background: var(--bg-secondary, #f5f5f5);\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-radius: var(--radius-lg, 12px);\n}\n.month-nav__btn[_ngcontent-%COMP%] {\n  min-width: 40px;\n  min-height: 40px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  border: none;\n  background: transparent;\n  border-radius: var(--radius-md, 8px);\n  cursor: pointer;\n  color: var(--text-primary);\n  transition: background 150ms ease;\n}\n.month-nav__btn[_ngcontent-%COMP%]:hover {\n  background: var(--bg-hover, #eee);\n}\n.month-nav__label[_ngcontent-%COMP%] {\n  flex: 1;\n  text-align: center;\n  font-weight: 600;\n  font-size: 0.95rem;\n}\n.month-grid[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-radius: var(--radius-lg, 12px);\n  overflow: hidden;\n  background: var(--bg-elevated, #fff);\n}\n.month-grid__header[_ngcontent-%COMP%], \n.month-grid__week[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: repeat(7, 1fr);\n  gap: 1px;\n}\n.month-grid__weekday[_ngcontent-%COMP%] {\n  padding: 6px 0;\n  text-align: center;\n  font-size: 0.72rem;\n  font-weight: 600;\n  text-transform: uppercase;\n  letter-spacing: 0.04em;\n  color: var(--text-muted, #999);\n  background: var(--bg-secondary, #f5f5f5);\n}\n.month-grid__cell[_ngcontent-%COMP%] {\n  position: relative;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  justify-content: flex-start;\n  gap: 2px;\n  min-height: 52px;\n  padding: 6px 4px;\n  background: var(--bg-elevated, #fff);\n  border: none;\n  cursor: pointer;\n  transition: background 150ms ease;\n}\n.month-grid__cell[_ngcontent-%COMP%]:hover {\n  background: var(--bg-hover, #f0f0f0);\n}\n.month-grid__cell--other-month[_ngcontent-%COMP%]   .month-grid__day-num[_ngcontent-%COMP%] {\n  color: var(--text-muted, #ccc);\n}\n.month-grid__cell--today[_ngcontent-%COMP%] {\n  background: color-mix(in oklab, var(--accent-primary, #1565c0) 8%, transparent);\n}\n.month-grid__cell--today[_ngcontent-%COMP%]   .month-grid__day-num[_ngcontent-%COMP%] {\n  color: var(--accent-primary, #1565c0);\n  font-weight: 700;\n}\n.month-grid__day-num[_ngcontent-%COMP%] {\n  font-size: 0.875rem;\n  font-weight: 500;\n  color: var(--text-primary);\n  line-height: 1;\n}\n.month-grid__badge[_ngcontent-%COMP%] {\n  font-size: 0.65rem;\n  font-weight: 700;\n  line-height: 1;\n  padding: 1px 5px;\n  border-radius: var(--radius-full, 999px);\n  background: var(--accent-secondary, #1976d2);\n  color: #fff;\n}\n.month-grid__badge--alert[_ngcontent-%COMP%] {\n  background: var(--accent-danger, #c62828);\n}\n.month-grid__dot[_ngcontent-%COMP%] {\n  width: 5px;\n  height: 5px;\n  border-radius: 50%;\n  background: var(--accent-danger, #c62828);\n  position: absolute;\n  top: 4px;\n  right: 6px;\n}\n/*# sourceMappingURL=student-homework-month-view.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentHomeworkMonthViewComponent, [{
    type: Component,
    args: [{ selector: "app-student-homework-month-view", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule], template: `<div class="month-view">

  <!-- Navigation bar -->
  <nav class="month-nav" aria-label="\u041D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F \u043F\u043E \u043C\u0435\u0441\u044F\u0446\u0430\u043C">
    <button
      type="button"
      class="month-nav__btn"
      (click)="previousMonth()"
      aria-label="\u041F\u0440\u0435\u0434\u044B\u0434\u0443\u0449\u0438\u0439 \u043C\u0435\u0441\u044F\u0446">
      <i class="ph ph-caret-left" aria-hidden="true"></i>
    </button>
    <span class="month-nav__label">{{ monthLabel }}</span>
    <button
      type="button"
      class="month-nav__btn"
      (click)="nextMonth()"
      aria-label="\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0438\u0439 \u043C\u0435\u0441\u044F\u0446">
      <i class="ph ph-caret-right" aria-hidden="true"></i>
    </button>
  </nav>

  <!-- Calendar grid -->
  <div class="month-grid" role="grid" aria-label="\u041A\u0430\u043B\u0435\u043D\u0434\u0430\u0440\u044C \u0434\u043E\u043C\u0430\u0448\u043D\u0438\u0445 \u0437\u0430\u0434\u0430\u043D\u0438\u0439">

    <!-- Week day headers -->
    <div class="month-grid__header" role="row">
      @for (dayName of WEEK_DAYS; track dayName) {
        <div class="month-grid__weekday" role="columnheader">{{ dayName }}</div>
      }
    </div>

    <!-- Weeks -->
    @for (week of weeks; track $index) {
      <div class="month-grid__week" role="row">
        @for (cell of week; track cell.dateKey) {
          <button
            type="button"
            role="gridcell"
            class="month-grid__cell"
            [class.month-grid__cell--other-month]="!cell.isCurrentMonth"
            [class.month-grid__cell--today]="cell.isToday"
            [class.month-grid__cell--has-items]="cell.count > 0"
            (click)="onCellClick(cell)"
            [attr.aria-label]="cell.dayNumber + ' \u2014 ' + cell.count + ' \u0437\u0430\u0434\u0430\u043D\u0438\u0439'"
            [attr.aria-pressed]="false">
            <span class="month-grid__day-num">{{ cell.dayNumber }}</span>
            @if (cell.count > 0) {
              <span class="month-grid__badge" [class.month-grid__badge--alert]="cell.hasUncompleted">
                {{ cell.count }}
              </span>
            }
            @if (cell.hasUncompleted) {
              <span class="month-grid__dot" aria-hidden="true"></span>
            }
          </button>
        }
      </div>
    }

  </div>

</div>
`, styles: ["/* src/app/features/student/homework/month-view/student-homework-month-view.component.css */\n:host {\n  display: block;\n}\n.month-view {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3, 12px);\n}\n.month-nav {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  padding: var(--space-2, 8px) var(--space-3, 12px);\n  background: var(--bg-secondary, #f5f5f5);\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-radius: var(--radius-lg, 12px);\n}\n.month-nav__btn {\n  min-width: 40px;\n  min-height: 40px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  border: none;\n  background: transparent;\n  border-radius: var(--radius-md, 8px);\n  cursor: pointer;\n  color: var(--text-primary);\n  transition: background 150ms ease;\n}\n.month-nav__btn:hover {\n  background: var(--bg-hover, #eee);\n}\n.month-nav__label {\n  flex: 1;\n  text-align: center;\n  font-weight: 600;\n  font-size: 0.95rem;\n}\n.month-grid {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-radius: var(--radius-lg, 12px);\n  overflow: hidden;\n  background: var(--bg-elevated, #fff);\n}\n.month-grid__header,\n.month-grid__week {\n  display: grid;\n  grid-template-columns: repeat(7, 1fr);\n  gap: 1px;\n}\n.month-grid__weekday {\n  padding: 6px 0;\n  text-align: center;\n  font-size: 0.72rem;\n  font-weight: 600;\n  text-transform: uppercase;\n  letter-spacing: 0.04em;\n  color: var(--text-muted, #999);\n  background: var(--bg-secondary, #f5f5f5);\n}\n.month-grid__cell {\n  position: relative;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  justify-content: flex-start;\n  gap: 2px;\n  min-height: 52px;\n  padding: 6px 4px;\n  background: var(--bg-elevated, #fff);\n  border: none;\n  cursor: pointer;\n  transition: background 150ms ease;\n}\n.month-grid__cell:hover {\n  background: var(--bg-hover, #f0f0f0);\n}\n.month-grid__cell--other-month .month-grid__day-num {\n  color: var(--text-muted, #ccc);\n}\n.month-grid__cell--today {\n  background: color-mix(in oklab, var(--accent-primary, #1565c0) 8%, transparent);\n}\n.month-grid__cell--today .month-grid__day-num {\n  color: var(--accent-primary, #1565c0);\n  font-weight: 700;\n}\n.month-grid__day-num {\n  font-size: 0.875rem;\n  font-weight: 500;\n  color: var(--text-primary);\n  line-height: 1;\n}\n.month-grid__badge {\n  font-size: 0.65rem;\n  font-weight: 700;\n  line-height: 1;\n  padding: 1px 5px;\n  border-radius: var(--radius-full, 999px);\n  background: var(--accent-secondary, #1976d2);\n  color: #fff;\n}\n.month-grid__badge--alert {\n  background: var(--accent-danger, #c62828);\n}\n.month-grid__dot {\n  width: 5px;\n  height: 5px;\n  border-radius: 50%;\n  background: var(--accent-danger, #c62828);\n  position: absolute;\n  top: 4px;\n  right: 6px;\n}\n/*# sourceMappingURL=student-homework-month-view.component.css.map */\n"] }]
  }], null, { month: [{
    type: Input,
    args: [{ required: true }]
  }], items: [{
    type: Input,
    args: [{ required: true }]
  }], dateSelected: [{
    type: Output
  }], monthChanged: [{
    type: Output
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentHomeworkMonthViewComponent, { className: "StudentHomeworkMonthViewComponent", filePath: "src/app/features/student/homework/month-view/student-homework-month-view.component.ts", lineNumber: 44 });
})();

// src/app/features/student/homework/student-homework.component.ts
var _c0 = () => [1, 2, 3];
function StudentHomeworkComponent_Conditional_12_For_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 11);
  }
}
function StudentHomeworkComponent_Conditional_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 9);
    \u0275\u0275repeaterCreate(1, StudentHomeworkComponent_Conditional_12_For_2_Template, 1, 0, "div", 11, \u0275\u0275repeaterTrackByIndex);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    \u0275\u0275advance();
    \u0275\u0275repeater(\u0275\u0275pureFunction0(0, _c0));
  }
}
function StudentHomeworkComponent_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 10);
    \u0275\u0275element(1, "i", 12);
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
function StudentHomeworkComponent_Conditional_14_Case_0_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "app-student-homework-day-view", 16);
    \u0275\u0275listener("dateChanged", function StudentHomeworkComponent_Conditional_14_Case_0_Template_app_student_homework_day_view_dateChanged_0_listener($event) {
      \u0275\u0275restoreView(_r2);
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onDayDateChanged($event));
    })("completeToggled", function StudentHomeworkComponent_Conditional_14_Case_0_Template_app_student_homework_day_view_completeToggled_0_listener($event) {
      \u0275\u0275restoreView(_r2);
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onCompleteToggled($event));
    });
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275property("date", ctx_r0.selectedDate())("items", ctx_r0.filteredHomeworks());
  }
}
function StudentHomeworkComponent_Conditional_14_Case_1_Template(rf, ctx) {
  if (rf & 1) {
    const _r3 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "app-student-homework-week-view", 17);
    \u0275\u0275listener("mondayChanged", function StudentHomeworkComponent_Conditional_14_Case_1_Template_app_student_homework_week_view_mondayChanged_0_listener($event) {
      \u0275\u0275restoreView(_r3);
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onWeekMondayChanged($event));
    })("completeToggled", function StudentHomeworkComponent_Conditional_14_Case_1_Template_app_student_homework_week_view_completeToggled_0_listener($event) {
      \u0275\u0275restoreView(_r3);
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onCompleteToggled($event));
    });
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275property("monday", ctx_r0.weekMonday())("items", ctx_r0.filteredHomeworks());
  }
}
function StudentHomeworkComponent_Conditional_14_Case_2_Template(rf, ctx) {
  if (rf & 1) {
    const _r4 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "app-student-homework-month-view", 18);
    \u0275\u0275listener("dateSelected", function StudentHomeworkComponent_Conditional_14_Case_2_Template_app_student_homework_month_view_dateSelected_0_listener($event) {
      \u0275\u0275restoreView(_r4);
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onDateSelected($event));
    })("monthChanged", function StudentHomeworkComponent_Conditional_14_Case_2_Template_app_student_homework_month_view_monthChanged_0_listener($event) {
      \u0275\u0275restoreView(_r4);
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.onMonthChanged($event));
    });
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275property("month", ctx_r0.currentMonth())("items", ctx_r0.filteredHomeworks());
  }
}
function StudentHomeworkComponent_Conditional_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275template(0, StudentHomeworkComponent_Conditional_14_Case_0_Template, 1, 2, "app-student-homework-day-view", 13)(1, StudentHomeworkComponent_Conditional_14_Case_1_Template, 1, 2, "app-student-homework-week-view", 14)(2, StudentHomeworkComponent_Conditional_14_Case_2_Template, 1, 2, "app-student-homework-month-view", 15);
  }
  if (rf & 2) {
    let tmp_1_0;
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275conditional((tmp_1_0 = ctx_r0.mode()) === "day" ? 0 : tmp_1_0 === "week" ? 1 : tmp_1_0 === "month" ? 2 : -1);
  }
}
var StudentHomeworkComponent = class _StudentHomeworkComponent {
  authService = inject(AuthService);
  apiService = inject(StudentApiService);
  loading = signal(false);
  error = signal(null);
  homeworks = signal([]);
  /** Current display mode. Default: «День» per D-09. */
  mode = signal("day");
  /** Currently selected date for day view (default: tomorrow per D-09). */
  selectedDate = signal(addDays(/* @__PURE__ */ new Date(), 1));
  /** Monday of the current week for week view. */
  weekMonday = signal(getMonday(/* @__PURE__ */ new Date()));
  /** First day of the current month for month view. */
  currentMonth = signal((() => {
    const d = /* @__PURE__ */ new Date();
    d.setDate(1);
    d.setHours(0, 0, 0, 0);
    return d;
  })());
  /** Toggle: show only uncompleted homeworks. */
  filterUncompleted = signal(false);
  /** Filtered homework list — applied in all three modes. */
  filteredHomeworks = computed(() => {
    const all = this.homeworks();
    return this.filterUncompleted() ? all.filter((h) => !h.completed) : all;
  });
  modeOptions = [
    { value: "day", label: "\u0414\u0435\u043D\u044C" },
    { value: "week", label: "\u041D\u0435\u0434\u0435\u043B\u044F" },
    { value: "month", label: "\u041C\u0435\u0441\u044F\u0446" }
  ];
  ngOnInit() {
    this.load();
  }
  load() {
    const groupId = this.authService.currentUser()?.groupId;
    if (groupId == null) {
      this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u044F. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u043F\u043E\u0434\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u0435.");
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.apiService.getActiveSemesterId().pipe(switchMap((semesterId) => {
      if (semesterId == null)
        return of([]);
      return this.apiService.getHomeworks(groupId, semesterId);
    })).subscribe({
      next: (homeworks) => {
        this.homeworks.set(homeworks);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u044F. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u043F\u043E\u0434\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u0435.");
        this.loading.set(false);
      }
    });
  }
  onModeChange(mode) {
    this.mode.set(mode);
  }
  onFilterChange(value) {
    this.filterUncompleted.set(value);
  }
  /** MonthView cell click → switch to day mode with that date. */
  onDateSelected(date) {
    this.selectedDate.set(date);
    this.mode.set("day");
  }
  onDayDateChanged(date) {
    this.selectedDate.set(date);
  }
  onWeekMondayChanged(monday) {
    this.weekMonday.set(monday);
  }
  onMonthChanged(month) {
    this.currentMonth.set(month);
  }
  onCompleteToggled(event) {
    const req$ = event.completed ? this.apiService.markHomeworkComplete(event.homework.id) : this.apiService.unmarkHomeworkComplete(event.homework.id);
    req$.subscribe({
      next: () => this.load(),
      error: () => {
        this.load();
      }
    });
  }
  static \u0275fac = function StudentHomeworkComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentHomeworkComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentHomeworkComponent, selectors: [["app-student-homework"]], decls: 15, vars: 7, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-header__eyebrow"], [1, "page-header__title"], [1, "hw-toolbar"], [3, "valueChange", "options", "value"], [1, "hw-filter-toggle"], ["aria-label", "\u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u0442\u043E\u043B\u044C\u043A\u043E \u043D\u0435\u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043D\u044B\u0435", 3, "change", "checked"], [1, "hw-filter-toggle__label"], ["role", "list", "aria-busy", "true", 1, "homework-skeleton-list"], ["role", "alert", 1, "page-error"], ["role", "listitem", 1, "homework-skeleton"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"], [3, "date", "items"], [3, "monday", "items"], [3, "month", "items"], [3, "dateChanged", "completeToggled", "date", "items"], [3, "mondayChanged", "completeToggled", "monday", "items"], [3, "dateSelected", "monthChanged", "month", "items"]], template: function StudentHomeworkComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "header", 1)(2, "p", 2);
      \u0275\u0275text(3, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(4, "h1", 3);
      \u0275\u0275text(5, "\u0414\u043E\u043C\u0430\u0448\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(6, "div", 4)(7, "app-segmented-control", 5);
      \u0275\u0275listener("valueChange", function StudentHomeworkComponent_Template_app_segmented_control_valueChange_7_listener($event) {
        return ctx.onModeChange($event);
      });
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(8, "label", 6)(9, "mat-slide-toggle", 7);
      \u0275\u0275listener("change", function StudentHomeworkComponent_Template_mat_slide_toggle_change_9_listener($event) {
        return ctx.onFilterChange($event.checked);
      });
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(10, "span", 8);
      \u0275\u0275text(11, "\u0422\u043E\u043B\u044C\u043A\u043E \u043D\u0435\u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043D\u044B\u0435");
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(12, StudentHomeworkComponent_Conditional_12_Template, 3, 1, "div", 9)(13, StudentHomeworkComponent_Conditional_13_Template, 4, 1, "div", 10)(14, StudentHomeworkComponent_Conditional_14_Template, 3, 1);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(7);
      \u0275\u0275property("options", ctx.modeOptions)("value", ctx.mode());
      \u0275\u0275advance(2);
      \u0275\u0275property("checked", ctx.filterUncompleted());
      \u0275\u0275advance(3);
      \u0275\u0275conditional(ctx.loading() ? 12 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(!ctx.loading() && ctx.error() ? 13 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(!ctx.loading() && !ctx.error() ? 14 : -1);
    }
  }, dependencies: [
    CommonModule,
    FormsModule,
    MatSlideToggleModule,
    MatSlideToggle,
    SegmentedControlComponent,
    StudentHomeworkDayViewComponent,
    StudentHomeworkWeekViewComponent,
    StudentHomeworkMonthViewComponent
  ], styles: ["\n\n.hw-toolbar[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  flex-wrap: wrap;\n  gap: var(--space-3, 12px);\n  margin-bottom: var(--space-2, 8px);\n}\n.hw-filter-toggle[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  cursor: pointer;\n}\n.hw-filter-toggle__label[_ngcontent-%COMP%] {\n  font-size: 0.875rem;\n  color: var(--text-secondary, #555);\n  -webkit-user-select: none;\n  user-select: none;\n}\n.homework-skeleton-list[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3, 12px);\n}\n.homework-skeleton[_ngcontent-%COMP%] {\n  height: 80px;\n  border-radius: var(--radius-lg, 12px);\n  background:\n    linear-gradient(\n      90deg,\n      var(--bg-elevated) 25%,\n      color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,\n      var(--bg-elevated) 75%);\n  background-size: 200% 100%;\n  animation: dashboard-shimmer 1.5s linear infinite;\n}\n@media (prefers-reduced-motion: reduce) {\n  .homework-skeleton[_ngcontent-%COMP%] {\n    animation: none;\n  }\n}\n/*# sourceMappingURL=student-homework.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentHomeworkComponent, [{
    type: Component,
    args: [{ selector: "app-student-homework", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [
      CommonModule,
      FormsModule,
      MatSlideToggleModule,
      SegmentedControlComponent,
      StudentHomeworkDayViewComponent,
      StudentHomeworkWeekViewComponent,
      StudentHomeworkMonthViewComponent
    ], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: `<div class="page-stack" [@routeFade]>
  <!-- Page header -->
  <header class="page-header">
    <p class="page-header__eyebrow">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</p>
    <h1 class="page-header__title">\u0414\u043E\u043C\u0430\u0448\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F</h1>
  </header>

  <!-- Mode switcher -->
  <div class="hw-toolbar">
    <app-segmented-control
      [options]="modeOptions"
      [value]="mode()"
      (valueChange)="onModeChange($event)">
    </app-segmented-control>

    <label class="hw-filter-toggle">
      <mat-slide-toggle
        [checked]="filterUncompleted()"
        (change)="onFilterChange($event.checked)"
        aria-label="\u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u0442\u043E\u043B\u044C\u043A\u043E \u043D\u0435\u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043D\u044B\u0435">
      </mat-slide-toggle>
      <span class="hw-filter-toggle__label">\u0422\u043E\u043B\u044C\u043A\u043E \u043D\u0435\u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043D\u044B\u0435</span>
    </label>
  </div>

  <!-- Loading skeleton -->
  @if (loading()) {
    <div class="homework-skeleton-list" role="list" aria-busy="true">
      @for (_ of [1,2,3]; track $index) {
        <div class="homework-skeleton" role="listitem"></div>
      }
    </div>
  }

  <!-- Error state -->
  @if (!loading() && error()) {
    <div class="page-error" role="alert">
      <i class="ph ph-warning-circle" aria-hidden="true"></i>
      <span>{{ error() }}</span>
    </div>
  }

  <!-- Sub-views -->
  @if (!loading() && !error()) {
    @switch (mode()) {
      @case ('day') {
        <app-student-homework-day-view
          [date]="selectedDate()"
          [items]="filteredHomeworks()"
          (dateChanged)="onDayDateChanged($event)"
          (completeToggled)="onCompleteToggled($event)">
        </app-student-homework-day-view>
      }
      @case ('week') {
        <app-student-homework-week-view
          [monday]="weekMonday()"
          [items]="filteredHomeworks()"
          (mondayChanged)="onWeekMondayChanged($event)"
          (completeToggled)="onCompleteToggled($event)">
        </app-student-homework-week-view>
      }
      @case ('month') {
        <app-student-homework-month-view
          [month]="currentMonth()"
          [items]="filteredHomeworks()"
          (dateSelected)="onDateSelected($event)"
          (monthChanged)="onMonthChanged($event)">
        </app-student-homework-month-view>
      }
    }
  }
</div>
`, styles: ["/* src/app/features/student/homework/student-homework.component.css */\n.hw-toolbar {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  flex-wrap: wrap;\n  gap: var(--space-3, 12px);\n  margin-bottom: var(--space-2, 8px);\n}\n.hw-filter-toggle {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  cursor: pointer;\n}\n.hw-filter-toggle__label {\n  font-size: 0.875rem;\n  color: var(--text-secondary, #555);\n  -webkit-user-select: none;\n  user-select: none;\n}\n.homework-skeleton-list {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3, 12px);\n}\n.homework-skeleton {\n  height: 80px;\n  border-radius: var(--radius-lg, 12px);\n  background:\n    linear-gradient(\n      90deg,\n      var(--bg-elevated) 25%,\n      color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,\n      var(--bg-elevated) 75%);\n  background-size: 200% 100%;\n  animation: dashboard-shimmer 1.5s linear infinite;\n}\n@media (prefers-reduced-motion: reduce) {\n  .homework-skeleton {\n    animation: none;\n  }\n}\n/*# sourceMappingURL=student-homework.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentHomeworkComponent, { className: "StudentHomeworkComponent", filePath: "src/app/features/student/homework/student-homework.component.ts", lineNumber: 64 });
})();
export {
  StudentHomeworkComponent
};
//# sourceMappingURL=chunk-PBRFSSQX.js.map
