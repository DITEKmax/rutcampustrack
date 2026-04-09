import {
  SubjectCacheService
} from "./chunk-LRO2XU6A.js";
import {
  StudentApiService
} from "./chunk-NZEYOUMN.js";
import "./chunk-3OOB2NIX.js";
import {
  animate,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import {
  AuthService
} from "./chunk-YJRH5W7Z.js";
import {
  RouterLink
} from "./chunk-LV56B2AI.js";
import "./chunk-LGL42SQS.js";
import "./chunk-T4R75CF5.js";
import {
  AsyncPipe
} from "./chunk-L2FQVI4C.js";
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Input,
  computed,
  forkJoin,
  inject,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵclassProp,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵnextContext,
  ɵɵpipe,
  ɵɵpipeBind1,
  ɵɵproperty,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-WGFJ2PWY.js";

// src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.ts
function NextLessonCardComponent_Conditional_0_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "span", 4);
  }
}
function NextLessonCardComponent_Conditional_0_Conditional_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "a", 11);
    \u0275\u0275element(1, "i", 10);
    \u0275\u0275text(2, " \u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C\u0441\u044F ");
    \u0275\u0275element(3, "i", 12);
    \u0275\u0275elementEnd();
  }
}
function NextLessonCardComponent_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "article", 2)(1, "p", 3);
    \u0275\u0275template(2, NextLessonCardComponent_Conditional_0_Conditional_2_Template, 1, 0, "span", 4);
    \u0275\u0275text(3);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "h3", 5);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "p", 6);
    \u0275\u0275element(7, "i", 7);
    \u0275\u0275elementStart(8, "span", 8);
    \u0275\u0275text(9);
    \u0275\u0275elementEnd();
    \u0275\u0275element(10, "span", 9)(11, "i", 10);
    \u0275\u0275elementStart(12, "span");
    \u0275\u0275text(13);
    \u0275\u0275elementEnd()();
    \u0275\u0275template(14, NextLessonCardComponent_Conditional_0_Conditional_14_Template, 4, 0, "a", 11);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275classProp("next-lesson-card--active", ctx_r0.isActive);
    \u0275\u0275advance(2);
    \u0275\u0275conditional(ctx_r0.isActive ? 2 : -1);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r0.heading, " ");
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.subjectName);
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate(ctx_r0.timeLabel);
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate1("\u0410\u0443\u0434. ", ctx.room, "");
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.isActive ? 14 : -1);
  }
}
function NextLessonCardComponent_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "article", 1)(1, "div", 13);
    \u0275\u0275element(2, "i", 14);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "h3", 5);
    \u0275\u0275text(4, "\u0421\u0435\u0433\u043E\u0434\u043D\u044F \u043F\u0430\u0440 \u043D\u0435\u0442");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "p", 6);
    \u0275\u0275text(6, "\u0425\u043E\u0440\u043E\u0448\u0435\u0435 \u0432\u0440\u0435\u043C\u044F \u0447\u0442\u043E\u0431\u044B \u043E\u0442\u0434\u043E\u0445\u043D\u0443\u0442\u044C \u0438\u043B\u0438 \u043F\u043E\u0434\u0433\u043E\u0442\u043E\u0432\u0438\u0442\u044C\u0441\u044F.");
    \u0275\u0275elementEnd()();
  }
}
var NextLessonCardComponent = class _NextLessonCardComponent {
  lesson = null;
  subjectName = "\u041F\u0440\u0435\u0434\u043C\u0435\u0442";
  get isActive() {
    return this.lesson?.status === "ACTIVE";
  }
  get isPresent() {
    return this.lesson !== null;
  }
  get heading() {
    return this.isActive ? "\u0422\u0435\u043A\u0443\u0449\u0430\u044F \u043F\u0430\u0440\u0430" : "\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0430\u044F \u043F\u0430\u0440\u0430";
  }
  get startLabel() {
    return this.lesson ? this.lesson.startTime.slice(0, 5) : "";
  }
  get endLabel() {
    return this.lesson ? this.lesson.endTime.slice(0, 5) : "";
  }
  get timeLabel() {
    return this.isPresent ? `${this.startLabel}\u2013${this.endLabel}` : "";
  }
  static \u0275fac = function NextLessonCardComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _NextLessonCardComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _NextLessonCardComponent, selectors: [["app-next-lesson-card"]], inputs: { lesson: "lesson", subjectName: "subjectName" }, decls: 2, vars: 1, consts: [[1, "next-lesson-card", 3, "next-lesson-card--active"], [1, "next-lesson-card", "next-lesson-card--empty"], [1, "next-lesson-card"], [1, "next-lesson-card__eyebrow"], ["aria-hidden", "true", 1, "next-lesson-card__live-dot"], [1, "next-lesson-card__subject"], [1, "next-lesson-card__meta"], ["aria-hidden", "true", 1, "ph-fill", "ph-clock"], [1, "next-lesson-card__time"], ["aria-hidden", "true", 1, "next-lesson-card__sep"], ["aria-hidden", "true", 1, "ph-fill", "ph-map-pin"], ["routerLink", "/student/checkin", 1, "next-lesson-card__cta"], ["aria-hidden", "true", 1, "ph", "ph-arrow-right"], ["aria-hidden", "true", 1, "next-lesson-card__empty-icon"], [1, "ph-duotone", "ph-calendar-blank"]], template: function NextLessonCardComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275template(0, NextLessonCardComponent_Conditional_0_Template, 15, 8, "article", 0)(1, NextLessonCardComponent_Conditional_1_Template, 7, 0, "article", 1);
    }
    if (rf & 2) {
      let tmp_0_0;
      \u0275\u0275conditional((tmp_0_0 = ctx.lesson) ? 0 : 1, tmp_0_0);
    }
  }, dependencies: [RouterLink], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.next-lesson-card[_ngcontent-%COMP%] {\n  padding: var(--space-5);\n  border-radius: var(--radius-lg);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3);\n  transition: border-color var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);\n}\n.next-lesson-card--active[_ngcontent-%COMP%] {\n  border-color: var(--border-accent);\n  box-shadow: var(--glow-primary);\n}\n.next-lesson-card__eyebrow[_ngcontent-%COMP%] {\n  margin: 0;\n  font-size: var(--text-xs);\n  font-weight: 600;\n  text-transform: uppercase;\n  letter-spacing: var(--tracking-wide);\n  color: var(--text-muted);\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n}\n.next-lesson-card--active[_ngcontent-%COMP%]   .next-lesson-card__eyebrow[_ngcontent-%COMP%] {\n  color: var(--accent-primary);\n}\n.next-lesson-card__live-dot[_ngcontent-%COMP%] {\n  width: 8px;\n  height: 8px;\n  border-radius: 50%;\n  background: var(--accent-primary);\n  box-shadow: 0 0 6px var(--accent-primary);\n  animation: _ngcontent-%COMP%_next-lesson-blink 1.6s ease-in-out infinite;\n}\n@keyframes _ngcontent-%COMP%_next-lesson-blink {\n  0%, 100% {\n    opacity: 1;\n  }\n  50% {\n    opacity: 0.4;\n  }\n}\n.next-lesson-card__subject[_ngcontent-%COMP%] {\n  margin: 0;\n  font-family: var(--font-heading);\n  font-size: var(--text-2xl);\n  font-weight: 600;\n  line-height: var(--leading-heading);\n  color: var(--text-primary);\n}\n.next-lesson-card__meta[_ngcontent-%COMP%] {\n  margin: 0;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  color: var(--text-secondary);\n  font-size: var(--text-sm);\n}\n.next-lesson-card__time[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-weight: 600;\n  font-variant-numeric: tabular-nums;\n}\n.next-lesson-card__sep[_ngcontent-%COMP%] {\n  width: 4px;\n  height: 4px;\n  border-radius: 50%;\n  background: var(--text-muted);\n  display: inline-block;\n}\n.next-lesson-card__meta[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 14px;\n}\n.next-lesson-card__cta[_ngcontent-%COMP%] {\n  align-self: flex-start;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: 10px 18px;\n  min-height: 44px;\n  border-radius: var(--radius-full);\n  background: var(--accent-primary);\n  color: var(--accent-primary-contrast);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: var(--text-sm);\n  text-decoration: none;\n  transition: filter var(--duration-base) var(--ease-out);\n}\n.next-lesson-card__cta[_ngcontent-%COMP%]:hover {\n  filter: brightness(1.08);\n}\n.next-lesson-card__cta[_ngcontent-%COMP%]:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.next-lesson-card__cta[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 16px;\n}\n.next-lesson-card--empty[_ngcontent-%COMP%] {\n  align-items: center;\n  text-align: center;\n  padding: var(--space-6);\n}\n.next-lesson-card__empty-icon[_ngcontent-%COMP%] {\n  width: 56px;\n  height: 56px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-secondary) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-secondary) 28%, transparent);\n  color: var(--accent-secondary);\n  display: grid;\n  place-items: center;\n}\n.next-lesson-card__empty-icon[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 28px;\n}\n@media (prefers-reduced-motion: reduce) {\n  .next-lesson-card__live-dot[_ngcontent-%COMP%] {\n    animation: none;\n  }\n  .next-lesson-card[_ngcontent-%COMP%] {\n    transition: none;\n  }\n}\n/*# sourceMappingURL=next-lesson-card.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(NextLessonCardComponent, [{
    type: Component,
    args: [{ selector: "app-next-lesson-card", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [RouterLink], template: '@if (lesson; as l) {\r\n  <article class="next-lesson-card" [class.next-lesson-card--active]="isActive">\r\n    <p class="next-lesson-card__eyebrow">\r\n      @if (isActive) {\r\n        <span class="next-lesson-card__live-dot" aria-hidden="true"></span>\r\n      }\r\n      {{ heading }}\r\n    </p>\r\n    <h3 class="next-lesson-card__subject">{{ subjectName }}</h3>\r\n    <p class="next-lesson-card__meta">\r\n      <i class="ph-fill ph-clock" aria-hidden="true"></i>\r\n      <span class="next-lesson-card__time">{{ timeLabel }}</span>\r\n      <span class="next-lesson-card__sep" aria-hidden="true"></span>\r\n      <i class="ph-fill ph-map-pin" aria-hidden="true"></i>\r\n      <span>\u0410\u0443\u0434. {{ l.room }}</span>\r\n    </p>\r\n    @if (isActive) {\r\n      <a routerLink="/student/checkin" class="next-lesson-card__cta">\r\n        <i class="ph-fill ph-map-pin" aria-hidden="true"></i>\r\n        \u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C\u0441\u044F\r\n        <i class="ph ph-arrow-right" aria-hidden="true"></i>\r\n      </a>\r\n    }\r\n  </article>\r\n} @else {\r\n  <article class="next-lesson-card next-lesson-card--empty">\r\n    <div class="next-lesson-card__empty-icon" aria-hidden="true">\r\n      <i class="ph-duotone ph-calendar-blank"></i>\r\n    </div>\r\n    <h3 class="next-lesson-card__subject">\u0421\u0435\u0433\u043E\u0434\u043D\u044F \u043F\u0430\u0440 \u043D\u0435\u0442</h3>\r\n    <p class="next-lesson-card__meta">\u0425\u043E\u0440\u043E\u0448\u0435\u0435 \u0432\u0440\u0435\u043C\u044F \u0447\u0442\u043E\u0431\u044B \u043E\u0442\u0434\u043E\u0445\u043D\u0443\u0442\u044C \u0438\u043B\u0438 \u043F\u043E\u0434\u0433\u043E\u0442\u043E\u0432\u0438\u0442\u044C\u0441\u044F.</p>\r\n  </article>\r\n}\r\n', styles: ["/* src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.css */\n:host {\n  display: block;\n}\n.next-lesson-card {\n  padding: var(--space-5);\n  border-radius: var(--radius-lg);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3);\n  transition: border-color var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);\n}\n.next-lesson-card--active {\n  border-color: var(--border-accent);\n  box-shadow: var(--glow-primary);\n}\n.next-lesson-card__eyebrow {\n  margin: 0;\n  font-size: var(--text-xs);\n  font-weight: 600;\n  text-transform: uppercase;\n  letter-spacing: var(--tracking-wide);\n  color: var(--text-muted);\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n}\n.next-lesson-card--active .next-lesson-card__eyebrow {\n  color: var(--accent-primary);\n}\n.next-lesson-card__live-dot {\n  width: 8px;\n  height: 8px;\n  border-radius: 50%;\n  background: var(--accent-primary);\n  box-shadow: 0 0 6px var(--accent-primary);\n  animation: next-lesson-blink 1.6s ease-in-out infinite;\n}\n@keyframes next-lesson-blink {\n  0%, 100% {\n    opacity: 1;\n  }\n  50% {\n    opacity: 0.4;\n  }\n}\n.next-lesson-card__subject {\n  margin: 0;\n  font-family: var(--font-heading);\n  font-size: var(--text-2xl);\n  font-weight: 600;\n  line-height: var(--leading-heading);\n  color: var(--text-primary);\n}\n.next-lesson-card__meta {\n  margin: 0;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  color: var(--text-secondary);\n  font-size: var(--text-sm);\n}\n.next-lesson-card__time {\n  font-family: var(--font-mono);\n  font-weight: 600;\n  font-variant-numeric: tabular-nums;\n}\n.next-lesson-card__sep {\n  width: 4px;\n  height: 4px;\n  border-radius: 50%;\n  background: var(--text-muted);\n  display: inline-block;\n}\n.next-lesson-card__meta i {\n  font-size: 14px;\n}\n.next-lesson-card__cta {\n  align-self: flex-start;\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: 10px 18px;\n  min-height: 44px;\n  border-radius: var(--radius-full);\n  background: var(--accent-primary);\n  color: var(--accent-primary-contrast);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: var(--text-sm);\n  text-decoration: none;\n  transition: filter var(--duration-base) var(--ease-out);\n}\n.next-lesson-card__cta:hover {\n  filter: brightness(1.08);\n}\n.next-lesson-card__cta:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.next-lesson-card__cta i {\n  font-size: 16px;\n}\n.next-lesson-card--empty {\n  align-items: center;\n  text-align: center;\n  padding: var(--space-6);\n}\n.next-lesson-card__empty-icon {\n  width: 56px;\n  height: 56px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-secondary) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-secondary) 28%, transparent);\n  color: var(--accent-secondary);\n  display: grid;\n  place-items: center;\n}\n.next-lesson-card__empty-icon i {\n  font-size: 28px;\n}\n@media (prefers-reduced-motion: reduce) {\n  .next-lesson-card__live-dot {\n    animation: none;\n  }\n  .next-lesson-card {\n    transition: none;\n  }\n}\n/*# sourceMappingURL=next-lesson-card.component.css.map */\n"] }]
  }], null, { lesson: [{
    type: Input
  }], subjectName: [{
    type: Input
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(NextLessonCardComponent, { className: "NextLessonCardComponent", filePath: "src/app/features/student/dashboard/next-lesson-card/next-lesson-card.component.ts", lineNumber: 22 });
})();

// src/app/features/student/dashboard/redzone-warning/redzone-warning.component.ts
var RedzoneWarningComponent = class _RedzoneWarningComponent {
  subjectName;
  percentage;
  get percentLabel() {
    return `${Math.round(this.percentage)}%`;
  }
  static \u0275fac = function RedzoneWarningComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _RedzoneWarningComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _RedzoneWarningComponent, selectors: [["app-redzone-warning"]], inputs: { subjectName: "subjectName", percentage: "percentage" }, decls: 6, vars: 2, consts: [["role", "alert", 1, "redzone-warning"], ["aria-hidden", "true", 1, "ph-duotone", "ph-warning"], [1, "redzone-warning__text"]], template: function RedzoneWarningComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0);
      \u0275\u0275element(1, "i", 1);
      \u0275\u0275elementStart(2, "p", 2)(3, "strong");
      \u0275\u0275text(4);
      \u0275\u0275elementEnd();
      \u0275\u0275text(5);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275advance(4);
      \u0275\u0275textInterpolate(ctx.subjectName);
      \u0275\u0275advance();
      \u0275\u0275textInterpolate1(" \u2014 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C \u043D\u0438\u0436\u0435 \u043F\u043E\u0440\u043E\u0433\u0430 (", ctx.percentLabel, ") ");
    }
  }, styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.redzone-warning[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: var(--space-3) var(--space-4);\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-warning) 10%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-warning) 25%, transparent);\n  color: var(--accent-warning);\n}\n.redzone-warning[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 20px;\n  flex-shrink: 0;\n}\n.redzone-warning__text[_ngcontent-%COMP%] {\n  margin: 0;\n  font-size: var(--text-sm);\n  color: var(--text-primary);\n  line-height: var(--leading-body);\n}\n.redzone-warning__text[_ngcontent-%COMP%]   strong[_ngcontent-%COMP%] {\n  color: var(--accent-warning);\n}\n/*# sourceMappingURL=redzone-warning.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(RedzoneWarningComponent, [{
    type: Component,
    args: [{ selector: "app-redzone-warning", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, template: '<div class="redzone-warning" role="alert">\r\n  <i class="ph-duotone ph-warning" aria-hidden="true"></i>\r\n  <p class="redzone-warning__text">\r\n    <strong>{{ subjectName }}</strong> \u2014 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C \u043D\u0438\u0436\u0435 \u043F\u043E\u0440\u043E\u0433\u0430 ({{ percentLabel }})\r\n  </p>\r\n</div>\r\n', styles: ["/* src/app/features/student/dashboard/redzone-warning/redzone-warning.component.css */\n:host {\n  display: block;\n}\n.redzone-warning {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: var(--space-3) var(--space-4);\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-warning) 10%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-warning) 25%, transparent);\n  color: var(--accent-warning);\n}\n.redzone-warning i {\n  font-size: 20px;\n  flex-shrink: 0;\n}\n.redzone-warning__text {\n  margin: 0;\n  font-size: var(--text-sm);\n  color: var(--text-primary);\n  line-height: var(--leading-body);\n}\n.redzone-warning__text strong {\n  color: var(--accent-warning);\n}\n/*# sourceMappingURL=redzone-warning.component.css.map */\n"] }]
  }], null, { subjectName: [{
    type: Input,
    args: [{ required: true }]
  }], percentage: [{
    type: Input,
    args: [{ required: true }]
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(RedzoneWarningComponent, { className: "RedzoneWarningComponent", filePath: "src/app/features/student/dashboard/redzone-warning/redzone-warning.component.ts", lineNumber: 19 });
})();

// src/app/features/student/dashboard/student-dashboard.component.ts
var _forTrack0 = ($index, $item) => $item.id;
var _forTrack1 = ($index, $item) => $item.subjectId;
function StudentDashboardComponent_Conditional_15_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 10);
    \u0275\u0275element(1, "i", 12);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.error(), " ");
  }
}
function StudentDashboardComponent_Conditional_16_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 11);
    \u0275\u0275element(1, "div", 13)(2, "div", 13);
    \u0275\u0275elementEnd();
  }
}
function StudentDashboardComponent_Conditional_17_Conditional_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 17);
    \u0275\u0275text(1, "\u0421\u0435\u0433\u043E\u0434\u043D\u044F \u043F\u0430\u0440 \u043D\u0435\u0442");
    \u0275\u0275elementEnd();
  }
}
function StudentDashboardComponent_Conditional_17_Conditional_6_For_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 21)(1, "span", 22);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275element(3, "span", 23);
    \u0275\u0275elementStart(4, "span", 24);
    \u0275\u0275text(5);
    \u0275\u0275pipe(6, "async");
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    let tmp_16_0;
    const lesson_r2 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275classProp("dashboard-today-chip--active", lesson_r2.status === "ACTIVE")("dashboard-today-chip--cancelled", lesson_r2.status === "CANCELLED");
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.formatTime(lesson_r2.startTime));
    \u0275\u0275advance();
    \u0275\u0275attribute("data-status", lesson_r2.status);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", (tmp_16_0 = \u0275\u0275pipeBind1(6, 7, ctx_r0.getSubjectName$(lesson_r2.subjectId))) !== null && tmp_16_0 !== void 0 ? tmp_16_0 : "\u041F\u0440\u0435\u0434\u043C\u0435\u0442", " ");
  }
}
function StudentDashboardComponent_Conditional_17_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 18);
    \u0275\u0275repeaterCreate(1, StudentDashboardComponent_Conditional_17_Conditional_6_For_2_Template, 7, 9, "div", 20, _forTrack0);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275repeater(ctx_r0.todaySorted());
  }
}
function StudentDashboardComponent_Conditional_17_Conditional_7_For_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "app-redzone-warning", 27);
  }
  if (rf & 2) {
    const subject_r3 = ctx.$implicit;
    \u0275\u0275property("subjectName", subject_r3.subjectName)("percentage", subject_r3.percentage);
  }
}
function StudentDashboardComponent_Conditional_17_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "section", 19)(1, "h3", 25);
    \u0275\u0275text(2, "\u0412\u043D\u0438\u043C\u0430\u043D\u0438\u0435 \u043A \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "div", 26);
    \u0275\u0275repeaterCreate(4, StudentDashboardComponent_Conditional_17_Conditional_7_For_5_Template, 1, 2, "app-redzone-warning", 27, _forTrack1);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(4);
    \u0275\u0275repeater(ctx_r0.redZoneSubjects());
  }
}
function StudentDashboardComponent_Conditional_17_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "app-next-lesson-card", 14);
    \u0275\u0275pipe(1, "async");
    \u0275\u0275elementStart(2, "section", 15)(3, "h3", 16);
    \u0275\u0275text(4, "\u0420\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435 \u043D\u0430 \u0441\u0435\u0433\u043E\u0434\u043D\u044F");
    \u0275\u0275elementEnd();
    \u0275\u0275template(5, StudentDashboardComponent_Conditional_17_Conditional_5_Template, 2, 0, "p", 17)(6, StudentDashboardComponent_Conditional_17_Conditional_6_Template, 3, 0, "div", 18);
    \u0275\u0275elementEnd();
    \u0275\u0275template(7, StudentDashboardComponent_Conditional_17_Conditional_7_Template, 6, 0, "section", 19);
  }
  if (rf & 2) {
    let tmp_2_0;
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275property("lesson", ctx_r0.nextLesson())("subjectName", (tmp_2_0 = \u0275\u0275pipeBind1(1, 4, ctx_r0.getSubjectName$((tmp_2_0 = ctx_r0.nextLesson()) == null ? null : tmp_2_0.subjectId))) !== null && tmp_2_0 !== void 0 ? tmp_2_0 : "\u041F\u0440\u0435\u0434\u043C\u0435\u0442");
    \u0275\u0275advance(5);
    \u0275\u0275conditional(ctx_r0.todaySorted().length === 0 ? 5 : 6);
    \u0275\u0275advance(2);
    \u0275\u0275conditional(ctx_r0.redZoneSubjects().length > 0 ? 7 : -1);
  }
}
function todayDateString() {
  const d = /* @__PURE__ */ new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}
function formatHhMm(time) {
  return time.slice(0, 5);
}
var StudentDashboardComponent = class _StudentDashboardComponent {
  studentApi = inject(StudentApiService);
  subjectCache = inject(SubjectCacheService);
  auth = inject(AuthService);
  destroyRef = inject(DestroyRef);
  lessons = signal([]);
  stats = signal(null);
  threshold = signal(null);
  loading = signal(false);
  error = signal(null);
  /** Live clock ticker — refreshed once per minute. */
  _now = signal(/* @__PURE__ */ new Date());
  timeLabel = computed(() => this._now().toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" }));
  dateLabel = computed(() => this._now().toLocaleDateString("ru-RU", {
    weekday: "long",
    day: "numeric",
    month: "long"
  }));
  greeting = computed(() => {
    const hour = this._now().getHours();
    if (hour < 6)
      return "\u0414\u043E\u0431\u0440\u043E\u0439 \u043D\u043E\u0447\u0438";
    if (hour < 12)
      return "\u0414\u043E\u0431\u0440\u043E\u0435 \u0443\u0442\u0440\u043E";
    if (hour < 18)
      return "\u0414\u043E\u0431\u0440\u044B\u0439 \u0434\u0435\u043D\u044C";
    return "\u0414\u043E\u0431\u0440\u044B\u0439 \u0432\u0435\u0447\u0435\u0440";
  });
  /** Today's lessons sorted by start time (ascending). */
  todaySorted = computed(() => [...this.lessons()].sort((a, b) => a.startTime.localeCompare(b.startTime)));
  /**
   * Selected focal lesson for NextLessonCard: prefer ACTIVE, fall back to the
   * earliest PLANNED, null if nothing applicable.
   */
  nextLesson = computed(() => {
    const sorted = this.todaySorted();
    return sorted.find((l) => l.status === "ACTIVE") ?? sorted.find((l) => l.status === "PLANNED") ?? null;
  });
  /**
   * Subjects whose attendance is strictly below the resolved red-zone
   * threshold. Empty until both stats and threshold have resolved.
   */
  redZoneSubjects = computed(() => {
    const s = this.stats();
    const t = this.threshold();
    if (!s || !t)
      return [];
    return s.subjects.filter((sub) => sub.percentage < t.percentage);
  });
  formatTime(time) {
    return formatHhMm(time);
  }
  getSubjectName$(id) {
    return this.subjectCache.getName(id ?? null);
  }
  ngOnInit() {
    const user = this.auth.currentUser();
    const groupId = user?.groupId;
    if (!groupId) {
      this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u043F\u0440\u0435\u0434\u0435\u043B\u0438\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443 \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F.");
      return;
    }
    this.loading.set(true);
    const today = todayDateString();
    forkJoin({
      lessons: this.studentApi.getWeekLessons(groupId, today, today),
      stats: this.studentApi.getStudentStats(),
      threshold: this.studentApi.resolveGlobalThreshold()
    }).subscribe({
      next: ({ lessons, stats, threshold }) => {
        this.lessons.set(lessons);
        this.stats.set(stats);
        this.threshold.set(threshold);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0434\u0430\u043D\u043D\u044B\u0435. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u043F\u043E\u0434\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u0435 \u0438 \u043E\u0431\u043D\u043E\u0432\u0438\u0442\u0435 \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0443.");
        this.loading.set(false);
      }
    });
    const tick = setInterval(() => this._now.set(/* @__PURE__ */ new Date()), 6e4);
    this.destroyRef.onDestroy(() => clearInterval(tick));
  }
  static \u0275fac = function StudentDashboardComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentDashboardComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentDashboardComponent, selectors: [["app-student-dashboard"]], decls: 18, vars: 5, consts: [[1, "dashboard", "student-dashboard", "page-stack"], [1, "dashboard__hero"], [1, "dashboard__greeting"], [1, "dashboard__eyebrow"], ["aria-hidden", "true", 1, "dashboard__pulse"], [1, "dashboard__title"], [1, "dashboard__subtitle"], ["aria-hidden", "true", 1, "dashboard__clock"], [1, "dashboard__clock-value"], [1, "dashboard__clock-label"], ["role", "alert", 1, "dashboard__error"], ["aria-busy", "true", "aria-label", "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430\u2026", 1, "dashboard__skeleton-row"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"], [1, "dashboard-skeleton"], [3, "lesson", "subjectName"], [1, "dashboard-today"], [1, "dashboard-today__title"], [1, "dashboard-today__empty"], [1, "dashboard-today__row"], [1, "dashboard-redzone"], [1, "dashboard-today-chip", 3, "dashboard-today-chip--active", "dashboard-today-chip--cancelled"], [1, "dashboard-today-chip"], [1, "dashboard-today-chip__time"], ["aria-hidden", "true", 1, "dashboard-today-chip__dot"], [1, "dashboard-today-chip__name"], [1, "dashboard-redzone__title"], [1, "dashboard-redzone__list"], [3, "subjectName", "percentage"]], template: function StudentDashboardComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "section", 0)(1, "header", 1)(2, "div", 2)(3, "p", 3);
      \u0275\u0275element(4, "span", 4);
      \u0275\u0275text(5, " \u0421\u0442\u0443\u0434\u0435\u043D\u0442 ");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(6, "h2", 5);
      \u0275\u0275text(7);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(8, "p", 6);
      \u0275\u0275text(9);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(10, "div", 7)(11, "span", 8);
      \u0275\u0275text(12);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(13, "span", 9);
      \u0275\u0275text(14, "\u043F\u043E \u041C\u043E\u0441\u043A\u0432\u0435");
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(15, StudentDashboardComponent_Conditional_15_Template, 3, 1, "div", 10)(16, StudentDashboardComponent_Conditional_16_Template, 3, 0, "div", 11)(17, StudentDashboardComponent_Conditional_17_Template, 8, 6);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(7);
      \u0275\u0275textInterpolate(ctx.greeting());
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate(ctx.dateLabel());
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.timeLabel());
      \u0275\u0275advance(3);
      \u0275\u0275conditional(ctx.error() ? 15 : ctx.loading() ? 16 : 17);
    }
  }, dependencies: [AsyncPipe, NextLessonCardComponent, RedzoneWarningComponent], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.student-dashboard[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-6);\n}\n.dashboard__hero[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: flex-start;\n  justify-content: space-between;\n  gap: var(--space-5);\n  padding: var(--space-6);\n  border-radius: var(--radius-xl);\n  background:\n    radial-gradient(\n      ellipse at 15% 20%,\n      color-mix(in oklab, var(--accent-primary) 16%, transparent),\n      transparent 55%),\n    radial-gradient(\n      ellipse at 90% 80%,\n      color-mix(in oklab, var(--accent-secondary) 18%, transparent),\n      transparent 55%),\n    var(--bg-secondary);\n  border: 1px solid var(--border-default);\n}\n.dashboard__greeting[_ngcontent-%COMP%] {\n  min-width: 0;\n  flex: 1;\n}\n.dashboard__eyebrow[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: 4px 12px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-primary) 30%, transparent);\n  color: var(--accent-primary);\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  font-weight: 600;\n  letter-spacing: 0.06em;\n  text-transform: uppercase;\n  margin: 0 0 var(--space-4);\n}\n.dashboard__pulse[_ngcontent-%COMP%] {\n  width: 6px;\n  height: 6px;\n  border-radius: 50%;\n  background: var(--accent-primary);\n  box-shadow: 0 0 8px var(--accent-primary);\n  animation: _ngcontent-%COMP%_dashboard-pulse 2.2s ease-in-out infinite;\n}\n@keyframes _ngcontent-%COMP%_dashboard-pulse {\n  0%, 100% {\n    opacity: 1;\n    transform: scale(1);\n  }\n  50% {\n    opacity: 0.5;\n    transform: scale(1.4);\n  }\n}\n.dashboard__title[_ngcontent-%COMP%] {\n  margin: 0;\n  font-family: var(--font-display);\n  font-size: clamp(1.75rem, 2.4vw + 0.5rem, 2.5rem);\n  font-weight: 600;\n  line-height: var(--leading-display);\n  color: var(--text-primary);\n}\n.dashboard__subtitle[_ngcontent-%COMP%] {\n  margin: var(--space-2) 0 0;\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n}\n.dashboard__clock[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  align-items: flex-end;\n  gap: 2px;\n  flex-shrink: 0;\n}\n.dashboard__clock-value[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: var(--text-2xl);\n  font-weight: 600;\n  color: var(--text-primary);\n  font-variant-numeric: tabular-nums;\n}\n.dashboard__clock-label[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  text-transform: uppercase;\n  letter-spacing: var(--tracking-wide);\n}\n.dashboard__error[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-4);\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 28%, transparent);\n  color: var(--accent-danger);\n  font-size: var(--text-sm);\n}\n.dashboard__skeleton-row[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3);\n}\n.dashboard-skeleton[_ngcontent-%COMP%] {\n  height: 120px;\n  border-radius: var(--radius-lg);\n  background:\n    linear-gradient(\n      90deg,\n      var(--bg-elevated) 25%,\n      color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,\n      var(--bg-elevated) 75%);\n  background-size: 200% 100%;\n  animation: _ngcontent-%COMP%_dashboard-shimmer 1.5s linear infinite;\n}\n@keyframes _ngcontent-%COMP%_dashboard-shimmer {\n  0% {\n    background-position: 200% 0;\n  }\n  100% {\n    background-position: -200% 0;\n  }\n}\n.dashboard-today__title[_ngcontent-%COMP%] {\n  margin: 0 0 var(--space-3);\n  font-family: var(--font-heading);\n  font-size: var(--text-base);\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.dashboard-today__empty[_ngcontent-%COMP%] {\n  margin: 0;\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n}\n.dashboard-today__row[_ngcontent-%COMP%] {\n  display: flex;\n  gap: var(--space-3);\n  overflow-x: auto;\n  padding-bottom: var(--space-2);\n}\n.dashboard-today-chip[_ngcontent-%COMP%] {\n  flex-shrink: 0;\n  display: flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-2) var(--space-3);\n  border-radius: var(--radius-full);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  font-size: var(--text-xs);\n  color: var(--text-primary);\n}\n.dashboard-today-chip--active[_ngcontent-%COMP%] {\n  border-color: var(--border-accent);\n  box-shadow: var(--glow-primary);\n}\n.dashboard-today-chip--cancelled[_ngcontent-%COMP%] {\n  opacity: 0.55;\n}\n.dashboard-today-chip__time[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-weight: 600;\n  font-variant-numeric: tabular-nums;\n}\n.dashboard-today-chip__dot[_ngcontent-%COMP%] {\n  width: 8px;\n  height: 8px;\n  border-radius: 50%;\n  background: var(--text-muted);\n}\n.dashboard-today-chip__dot[data-status=ACTIVE][_ngcontent-%COMP%] {\n  background: var(--accent-primary);\n  box-shadow: 0 0 6px var(--accent-primary);\n}\n.dashboard-today-chip__dot[data-status=CLOSED][_ngcontent-%COMP%] {\n  background: var(--status-present);\n}\n.dashboard-today-chip__dot[data-status=CANCELLED][_ngcontent-%COMP%] {\n  background: var(--text-muted);\n}\n.dashboard-today-chip__name[_ngcontent-%COMP%] {\n  font-family: var(--font-sans);\n  font-weight: 400;\n}\n.dashboard-redzone__title[_ngcontent-%COMP%] {\n  margin: 0 0 var(--space-3);\n  font-family: var(--font-heading);\n  font-size: var(--text-base);\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.dashboard-redzone__list[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2);\n}\n@media (prefers-reduced-motion: reduce) {\n  .dashboard-skeleton[_ngcontent-%COMP%] {\n    animation: none;\n  }\n  .dashboard__pulse[_ngcontent-%COMP%] {\n    animation: none;\n  }\n}\n/*# sourceMappingURL=student-dashboard.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentDashboardComponent, [{
    type: Component,
    args: [{ selector: "app-student-dashboard", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [AsyncPipe, NextLessonCardComponent, RedzoneWarningComponent], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: `<section class="dashboard student-dashboard page-stack" [@routeFade]>\r
  <!-- Greeting hero -->\r
  <header class="dashboard__hero">\r
    <div class="dashboard__greeting">\r
      <p class="dashboard__eyebrow">\r
        <span class="dashboard__pulse" aria-hidden="true"></span>\r
        \u0421\u0442\u0443\u0434\u0435\u043D\u0442\r
      </p>\r
      <h2 class="dashboard__title">{{ greeting() }}</h2>\r
      <p class="dashboard__subtitle">{{ dateLabel() }}</p>\r
    </div>\r
    <div class="dashboard__clock" aria-hidden="true">\r
      <span class="dashboard__clock-value">{{ timeLabel() }}</span>\r
      <span class="dashboard__clock-label">\u043F\u043E \u041C\u043E\u0441\u043A\u0432\u0435</span>\r
    </div>\r
  </header>\r
\r
  @if (error()) {\r
    <div class="dashboard__error" role="alert">\r
      <i class="ph ph-warning-circle" aria-hidden="true"></i>\r
      {{ error() }}\r
    </div>\r
  } @else if (loading()) {\r
    <div class="dashboard__skeleton-row" aria-busy="true" aria-label="\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430\u2026">\r
      <div class="dashboard-skeleton"></div>\r
      <div class="dashboard-skeleton"></div>\r
    </div>\r
  } @else {\r
    <!-- Next lesson card -->\r
    <app-next-lesson-card\r
      [lesson]="nextLesson()"\r
      [subjectName]="(getSubjectName$(nextLesson()?.subjectId) | async) ?? '\u041F\u0440\u0435\u0434\u043C\u0435\u0442'"\r
    />\r
\r
    <!-- Today's schedule summary -->\r
    <section class="dashboard-today">\r
      <h3 class="dashboard-today__title">\u0420\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435 \u043D\u0430 \u0441\u0435\u0433\u043E\u0434\u043D\u044F</h3>\r
      @if (todaySorted().length === 0) {\r
        <p class="dashboard-today__empty">\u0421\u0435\u0433\u043E\u0434\u043D\u044F \u043F\u0430\u0440 \u043D\u0435\u0442</p>\r
      } @else {\r
        <div class="dashboard-today__row">\r
          @for (lesson of todaySorted(); track lesson.id) {\r
            <div\r
              class="dashboard-today-chip"\r
              [class.dashboard-today-chip--active]="lesson.status === 'ACTIVE'"\r
              [class.dashboard-today-chip--cancelled]="lesson.status === 'CANCELLED'"\r
            >\r
              <span class="dashboard-today-chip__time">{{ formatTime(lesson.startTime) }}</span>\r
              <span\r
                class="dashboard-today-chip__dot"\r
                [attr.data-status]="lesson.status"\r
                aria-hidden="true"\r
              ></span>\r
              <span class="dashboard-today-chip__name">\r
                {{ (getSubjectName$(lesson.subjectId) | async) ?? '\u041F\u0440\u0435\u0434\u043C\u0435\u0442' }}\r
              </span>\r
            </div>\r
          }\r
        </div>\r
      }\r
    </section>\r
\r
    <!-- Red-zone warnings -->\r
    @if (redZoneSubjects().length > 0) {\r
      <section class="dashboard-redzone">\r
        <h3 class="dashboard-redzone__title">\u0412\u043D\u0438\u043C\u0430\u043D\u0438\u0435 \u043A \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438</h3>\r
        <div class="dashboard-redzone__list">\r
          @for (subject of redZoneSubjects(); track subject.subjectId) {\r
            <app-redzone-warning\r
              [subjectName]="subject.subjectName"\r
              [percentage]="subject.percentage"\r
            />\r
          }\r
        </div>\r
      </section>\r
    }\r
  }\r
</section>\r
`, styles: ["/* src/app/features/student/dashboard/student-dashboard.component.css */\n:host {\n  display: block;\n}\n.student-dashboard {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-6);\n}\n.dashboard__hero {\n  display: flex;\n  align-items: flex-start;\n  justify-content: space-between;\n  gap: var(--space-5);\n  padding: var(--space-6);\n  border-radius: var(--radius-xl);\n  background:\n    radial-gradient(\n      ellipse at 15% 20%,\n      color-mix(in oklab, var(--accent-primary) 16%, transparent),\n      transparent 55%),\n    radial-gradient(\n      ellipse at 90% 80%,\n      color-mix(in oklab, var(--accent-secondary) 18%, transparent),\n      transparent 55%),\n    var(--bg-secondary);\n  border: 1px solid var(--border-default);\n}\n.dashboard__greeting {\n  min-width: 0;\n  flex: 1;\n}\n.dashboard__eyebrow {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: 4px 12px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-primary) 30%, transparent);\n  color: var(--accent-primary);\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  font-weight: 600;\n  letter-spacing: 0.06em;\n  text-transform: uppercase;\n  margin: 0 0 var(--space-4);\n}\n.dashboard__pulse {\n  width: 6px;\n  height: 6px;\n  border-radius: 50%;\n  background: var(--accent-primary);\n  box-shadow: 0 0 8px var(--accent-primary);\n  animation: dashboard-pulse 2.2s ease-in-out infinite;\n}\n@keyframes dashboard-pulse {\n  0%, 100% {\n    opacity: 1;\n    transform: scale(1);\n  }\n  50% {\n    opacity: 0.5;\n    transform: scale(1.4);\n  }\n}\n.dashboard__title {\n  margin: 0;\n  font-family: var(--font-display);\n  font-size: clamp(1.75rem, 2.4vw + 0.5rem, 2.5rem);\n  font-weight: 600;\n  line-height: var(--leading-display);\n  color: var(--text-primary);\n}\n.dashboard__subtitle {\n  margin: var(--space-2) 0 0;\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n}\n.dashboard__clock {\n  display: flex;\n  flex-direction: column;\n  align-items: flex-end;\n  gap: 2px;\n  flex-shrink: 0;\n}\n.dashboard__clock-value {\n  font-family: var(--font-mono);\n  font-size: var(--text-2xl);\n  font-weight: 600;\n  color: var(--text-primary);\n  font-variant-numeric: tabular-nums;\n}\n.dashboard__clock-label {\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  text-transform: uppercase;\n  letter-spacing: var(--tracking-wide);\n}\n.dashboard__error {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-4);\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 28%, transparent);\n  color: var(--accent-danger);\n  font-size: var(--text-sm);\n}\n.dashboard__skeleton-row {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3);\n}\n.dashboard-skeleton {\n  height: 120px;\n  border-radius: var(--radius-lg);\n  background:\n    linear-gradient(\n      90deg,\n      var(--bg-elevated) 25%,\n      color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,\n      var(--bg-elevated) 75%);\n  background-size: 200% 100%;\n  animation: dashboard-shimmer 1.5s linear infinite;\n}\n@keyframes dashboard-shimmer {\n  0% {\n    background-position: 200% 0;\n  }\n  100% {\n    background-position: -200% 0;\n  }\n}\n.dashboard-today__title {\n  margin: 0 0 var(--space-3);\n  font-family: var(--font-heading);\n  font-size: var(--text-base);\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.dashboard-today__empty {\n  margin: 0;\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n}\n.dashboard-today__row {\n  display: flex;\n  gap: var(--space-3);\n  overflow-x: auto;\n  padding-bottom: var(--space-2);\n}\n.dashboard-today-chip {\n  flex-shrink: 0;\n  display: flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-2) var(--space-3);\n  border-radius: var(--radius-full);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  font-size: var(--text-xs);\n  color: var(--text-primary);\n}\n.dashboard-today-chip--active {\n  border-color: var(--border-accent);\n  box-shadow: var(--glow-primary);\n}\n.dashboard-today-chip--cancelled {\n  opacity: 0.55;\n}\n.dashboard-today-chip__time {\n  font-family: var(--font-mono);\n  font-weight: 600;\n  font-variant-numeric: tabular-nums;\n}\n.dashboard-today-chip__dot {\n  width: 8px;\n  height: 8px;\n  border-radius: 50%;\n  background: var(--text-muted);\n}\n.dashboard-today-chip__dot[data-status=ACTIVE] {\n  background: var(--accent-primary);\n  box-shadow: 0 0 6px var(--accent-primary);\n}\n.dashboard-today-chip__dot[data-status=CLOSED] {\n  background: var(--status-present);\n}\n.dashboard-today-chip__dot[data-status=CANCELLED] {\n  background: var(--text-muted);\n}\n.dashboard-today-chip__name {\n  font-family: var(--font-sans);\n  font-weight: 400;\n}\n.dashboard-redzone__title {\n  margin: 0 0 var(--space-3);\n  font-family: var(--font-heading);\n  font-size: var(--text-base);\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.dashboard-redzone__list {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2);\n}\n@media (prefers-reduced-motion: reduce) {\n  .dashboard-skeleton {\n    animation: none;\n  }\n  .dashboard__pulse {\n    animation: none;\n  }\n}\n/*# sourceMappingURL=student-dashboard.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentDashboardComponent, { className: "StudentDashboardComponent", filePath: "src/app/features/student/dashboard/student-dashboard.component.ts", lineNumber: 78 });
})();
export {
  StudentDashboardComponent
};
//# sourceMappingURL=chunk-EMT2O7OV.js.map
