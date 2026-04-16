import {
  MatCheckbox,
  MatCheckboxModule
} from "./chunk-MQ7WFICG.js";
import {
  CommonModule
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
  setClassMetadata,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵattribute,
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
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵsanitizeUrl,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate
} from "./chunk-MFRIGFR2.js";

// src/app/shared/homework-card/homework-card.component.ts
function HomeworkCardComponent_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "mat-checkbox", 9);
    \u0275\u0275listener("change", function HomeworkCardComponent_Conditional_1_Template_mat_checkbox_change_0_listener() {
      \u0275\u0275restoreView(_r1);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1.onToggleComplete());
    });
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275property("checked", !!ctx_r1.homework.completed)("disabled", ctx_r1.pending);
    \u0275\u0275attribute("aria-label", ctx_r1.toggleLabel());
  }
}
function HomeworkCardComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "a", 5);
    \u0275\u0275element(1, "i", 10);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275property("href", ctx_r1.homework.link, \u0275\u0275sanitizeUrl);
  }
}
function HomeworkCardComponent_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 6);
    \u0275\u0275element(1, "i", 11);
    \u0275\u0275elementStart(2, "span");
    \u0275\u0275text(3);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate(ctx_r1.subjectName);
  }
}
function HomeworkCardComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 7);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r1.homework.description);
  }
}
function HomeworkCardComponent_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    const _r3 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "div", 8)(1, "button", 12);
    \u0275\u0275listener("click", function HomeworkCardComponent_Conditional_9_Template_button_click_1_listener() {
      \u0275\u0275restoreView(_r3);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1.edit.emit(ctx_r1.homework));
    });
    \u0275\u0275element(2, "i", 13);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "button", 14);
    \u0275\u0275listener("click", function HomeworkCardComponent_Conditional_9_Template_button_click_3_listener() {
      \u0275\u0275restoreView(_r3);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1.delete.emit(ctx_r1.homework));
    });
    \u0275\u0275element(4, "i", 15);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275attribute("aria-label", "\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C \xAB" + ctx_r1.homework.title + "\xBB");
    \u0275\u0275advance(2);
    \u0275\u0275attribute("aria-label", "\u0423\u0434\u0430\u043B\u0438\u0442\u044C \xAB" + ctx_r1.homework.title + "\xBB");
  }
}
var HomeworkCardComponent = class _HomeworkCardComponent {
  homework;
  subjectName = null;
  showEditActions = false;
  showCompleteCheckbox = false;
  pending = false;
  edit = new EventEmitter();
  delete = new EventEmitter();
  completeToggled = new EventEmitter();
  toggleLabel() {
    return this.homework.completed ? `\u0421\u043D\u044F\u0442\u044C \u043E\u0442\u043C\u0435\u0442\u043A\u0443 \xAB\u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E\xBB \u0441 \u0437\u0430\u0434\u0430\u043D\u0438\u044F \xAB${this.homework.title}\xBB` : `\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u0435 \xAB${this.homework.title}\xBB \u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043D\u044B\u043C`;
  }
  onToggleComplete() {
    this.completeToggled.emit({
      homework: this.homework,
      completed: !this.homework.completed
    });
  }
  static \u0275fac = function HomeworkCardComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HomeworkCardComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HomeworkCardComponent, selectors: [["app-homework-card"]], inputs: { homework: "homework", subjectName: "subjectName", showEditActions: "showEditActions", showCompleteCheckbox: "showCompleteCheckbox", pending: "pending" }, outputs: { edit: "edit", delete: "delete", completeToggled: "completeToggled" }, decls: 10, vars: 9, consts: [["role", "listitem", 1, "hw-card"], [1, "hw-card__checkbox", 3, "checked", "disabled"], [1, "hw-card__body"], [1, "hw-card__header"], [1, "hw-card__title"], ["target", "_blank", "rel", "noopener", "aria-label", "\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043C\u0430\u0442\u0435\u0440\u0438\u0430\u043B\u044B \u043A \u0437\u0430\u0434\u0430\u043D\u0438\u044E (\u043D\u043E\u0432\u0430\u044F \u0432\u043A\u043B\u0430\u0434\u043A\u0430)", 1, "hw-card__link", 3, "href"], [1, "hw-card__subject"], [1, "hw-card__description"], [1, "hw-card__actions"], [1, "hw-card__checkbox", 3, "change", "checked", "disabled"], ["aria-hidden", "true", 1, "ph", "ph-arrow-square-out"], ["aria-hidden", "true", 1, "ph", "ph-book-open"], ["type", "button", 1, "hw-card__icon-btn", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-pencil"], ["type", "button", 1, "hw-card__icon-btn", "hw-card__icon-btn--danger", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-trash"]], template: function HomeworkCardComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0);
      \u0275\u0275template(1, HomeworkCardComponent_Conditional_1_Template, 1, 3, "mat-checkbox", 1);
      \u0275\u0275elementStart(2, "div", 2)(3, "div", 3)(4, "span", 4);
      \u0275\u0275text(5);
      \u0275\u0275elementEnd();
      \u0275\u0275template(6, HomeworkCardComponent_Conditional_6_Template, 2, 1, "a", 5);
      \u0275\u0275elementEnd();
      \u0275\u0275template(7, HomeworkCardComponent_Conditional_7_Template, 4, 1, "div", 6)(8, HomeworkCardComponent_Conditional_8_Template, 2, 1, "p", 7);
      \u0275\u0275elementEnd();
      \u0275\u0275template(9, HomeworkCardComponent_Conditional_9_Template, 5, 2, "div", 8);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275classProp("hw-card--completed", ctx.homework.completed);
      \u0275\u0275attribute("aria-label", "\u0417\u0430\u0434\u0430\u043D\u0438\u0435 \xAB" + ctx.homework.title + "\xBB");
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.showCompleteCheckbox ? 1 : -1);
      \u0275\u0275advance(4);
      \u0275\u0275textInterpolate(ctx.homework.title);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.homework.link ? 6 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.subjectName ? 7 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.homework.description ? 8 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.showEditActions ? 9 : -1);
    }
  }, dependencies: [CommonModule, MatCheckboxModule, MatCheckbox], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.hw-card[_ngcontent-%COMP%] {\n  display: flex;\n  gap: var(--space-2, 8px);\n  background: var(--bg-secondary, #fafafa);\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-left: 3px solid var(--accent-secondary, #1976d2);\n  border-radius: var(--radius-lg, 12px);\n  padding: var(--space-3, 12px) var(--space-4, 16px);\n  align-items: flex-start;\n  transition: opacity 150ms ease;\n}\n.hw-card--completed[_ngcontent-%COMP%] {\n  opacity: 0.55;\n  border-left-color: var(--border-subtle, #d0d0d0);\n}\n.hw-card--completed[_ngcontent-%COMP%]   .hw-card__title[_ngcontent-%COMP%] {\n  text-decoration: line-through;\n}\n.hw-card__checkbox[_ngcontent-%COMP%] {\n  flex-shrink: 0;\n}\n.hw-card__body[_ngcontent-%COMP%] {\n  flex: 1;\n  min-width: 0;\n}\n.hw-card__header[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: 8px;\n}\n.hw-card__title[_ngcontent-%COMP%] {\n  font-weight: 600;\n  font-size: 0.95rem;\n  line-height: 1.35;\n  color: var(--text-primary);\n}\n.hw-card__link[_ngcontent-%COMP%] {\n  color: var(--accent-secondary, #1976d2);\n  min-width: 32px;\n  min-height: 32px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n}\n.hw-card__subject[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: 4px;\n  font-size: 0.78rem;\n  color: var(--accent-secondary, #1976d2);\n  margin-top: 4px;\n}\n.hw-card__description[_ngcontent-%COMP%] {\n  margin: 6px 0 0;\n  font-size: 0.85rem;\n  line-height: 1.4;\n  color: var(--text-secondary, #555);\n  white-space: pre-wrap;\n}\n.hw-card__actions[_ngcontent-%COMP%] {\n  display: flex;\n  gap: 4px;\n  flex-shrink: 0;\n}\n.hw-card__icon-btn[_ngcontent-%COMP%] {\n  width: 32px;\n  height: 32px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  background: transparent;\n  border: 1px solid var(--border-subtle, #d0d0d0);\n  border-radius: var(--radius-md, 8px);\n  color: var(--text-secondary, #555);\n  cursor: pointer;\n  transition:\n    background 150ms ease,\n    color 150ms ease,\n    border-color 150ms ease;\n}\n.hw-card__icon-btn[_ngcontent-%COMP%]:hover {\n  background: var(--bg-hover, #eee);\n  color: var(--text-primary);\n}\n.hw-card__icon-btn--danger[_ngcontent-%COMP%]:hover {\n  background: #ffebee;\n  color: #c62828;\n  border-color: #ef9a9a;\n}\n/*# sourceMappingURL=homework-card.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HomeworkCardComponent, [{
    type: Component,
    args: [{ selector: "app-homework-card", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule, MatCheckboxModule], template: `
    <div
      class="hw-card"
      [class.hw-card--completed]="homework.completed"
      role="listitem"
      [attr.aria-label]="'\u0417\u0430\u0434\u0430\u043D\u0438\u0435 \xAB' + homework.title + '\xBB'">

      @if (showCompleteCheckbox) {
        <mat-checkbox
          class="hw-card__checkbox"
          [checked]="!!homework.completed"
          [disabled]="pending"
          (change)="onToggleComplete()"
          [attr.aria-label]="toggleLabel()">
        </mat-checkbox>
      }

      <div class="hw-card__body">
        <div class="hw-card__header">
          <span class="hw-card__title">{{ homework.title }}</span>
          @if (homework.link) {
            <a
              class="hw-card__link"
              [href]="homework.link"
              target="_blank"
              rel="noopener"
              aria-label="\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043C\u0430\u0442\u0435\u0440\u0438\u0430\u043B\u044B \u043A \u0437\u0430\u0434\u0430\u043D\u0438\u044E (\u043D\u043E\u0432\u0430\u044F \u0432\u043A\u043B\u0430\u0434\u043A\u0430)">
              <i class="ph ph-arrow-square-out" aria-hidden="true"></i>
            </a>
          }
        </div>

        @if (subjectName) {
          <div class="hw-card__subject">
            <i class="ph ph-book-open" aria-hidden="true"></i>
            <span>{{ subjectName }}</span>
          </div>
        }

        @if (homework.description) {
          <p class="hw-card__description">{{ homework.description }}</p>
        }
      </div>

      @if (showEditActions) {
        <div class="hw-card__actions">
          <button
            type="button"
            class="hw-card__icon-btn"
            (click)="edit.emit(homework)"
            [attr.aria-label]="'\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C \xAB' + homework.title + '\xBB'">
            <i class="ph ph-pencil" aria-hidden="true"></i>
          </button>
          <button
            type="button"
            class="hw-card__icon-btn hw-card__icon-btn--danger"
            (click)="delete.emit(homework)"
            [attr.aria-label]="'\u0423\u0434\u0430\u043B\u0438\u0442\u044C \xAB' + homework.title + '\xBB'">
            <i class="ph ph-trash" aria-hidden="true"></i>
          </button>
        </div>
      }
    </div>
  `, styles: ["/* angular:styles/component:css;43c2cfd942666595a4f35c9be06a068b78704a6f9fda0920bbc7602e673f0a37;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/shared/homework-card/homework-card.component.ts */\n:host {\n  display: block;\n}\n.hw-card {\n  display: flex;\n  gap: var(--space-2, 8px);\n  background: var(--bg-secondary, #fafafa);\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-left: 3px solid var(--accent-secondary, #1976d2);\n  border-radius: var(--radius-lg, 12px);\n  padding: var(--space-3, 12px) var(--space-4, 16px);\n  align-items: flex-start;\n  transition: opacity 150ms ease;\n}\n.hw-card--completed {\n  opacity: 0.55;\n  border-left-color: var(--border-subtle, #d0d0d0);\n}\n.hw-card--completed .hw-card__title {\n  text-decoration: line-through;\n}\n.hw-card__checkbox {\n  flex-shrink: 0;\n}\n.hw-card__body {\n  flex: 1;\n  min-width: 0;\n}\n.hw-card__header {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: 8px;\n}\n.hw-card__title {\n  font-weight: 600;\n  font-size: 0.95rem;\n  line-height: 1.35;\n  color: var(--text-primary);\n}\n.hw-card__link {\n  color: var(--accent-secondary, #1976d2);\n  min-width: 32px;\n  min-height: 32px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n}\n.hw-card__subject {\n  display: inline-flex;\n  align-items: center;\n  gap: 4px;\n  font-size: 0.78rem;\n  color: var(--accent-secondary, #1976d2);\n  margin-top: 4px;\n}\n.hw-card__description {\n  margin: 6px 0 0;\n  font-size: 0.85rem;\n  line-height: 1.4;\n  color: var(--text-secondary, #555);\n  white-space: pre-wrap;\n}\n.hw-card__actions {\n  display: flex;\n  gap: 4px;\n  flex-shrink: 0;\n}\n.hw-card__icon-btn {\n  width: 32px;\n  height: 32px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  background: transparent;\n  border: 1px solid var(--border-subtle, #d0d0d0);\n  border-radius: var(--radius-md, 8px);\n  color: var(--text-secondary, #555);\n  cursor: pointer;\n  transition:\n    background 150ms ease,\n    color 150ms ease,\n    border-color 150ms ease;\n}\n.hw-card__icon-btn:hover {\n  background: var(--bg-hover, #eee);\n  color: var(--text-primary);\n}\n.hw-card__icon-btn--danger:hover {\n  background: #ffebee;\n  color: #c62828;\n  border-color: #ef9a9a;\n}\n/*# sourceMappingURL=homework-card.component.css.map */\n"] }]
  }], null, { homework: [{
    type: Input,
    args: [{ required: true }]
  }], subjectName: [{
    type: Input
  }], showEditActions: [{
    type: Input
  }], showCompleteCheckbox: [{
    type: Input
  }], pending: [{
    type: Input
  }], edit: [{
    type: Output
  }], delete: [{
    type: Output
  }], completeToggled: [{
    type: Output
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HomeworkCardComponent, { className: "HomeworkCardComponent", filePath: "src/app/shared/homework-card/homework-card.component.ts", lineNumber: 193 });
})();

export {
  HomeworkCardComponent
};
//# sourceMappingURL=chunk-EI5Q5OC5.js.map
