import {
  MatCheckbox,
  MatCheckboxModule
} from "./chunk-ZIZW6QQV.js";
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
import {
  AuthService
} from "./chunk-ID5GKTOO.js";
import "./chunk-LPDGA6NZ.js";
import "./chunk-ZEX6QU7O.js";
import "./chunk-YIJEORIR.js";
import "./chunk-FW2NJ6PM.js";
import "./chunk-HWHV5SCS.js";
import "./chunk-FLE4DLW4.js";
import {
  CommonModule
} from "./chunk-CLRYRPPS.js";
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  EventEmitter,
  Input,
  Output,
  __spreadProps,
  __spreadValues,
  computed,
  inject,
  of,
  setClassMetadata,
  signal,
  switchMap,
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
  ɵɵpureFunction0,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵrepeaterTrackByIndex,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵsanitizeUrl,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate
} from "./chunk-4M4FDLBS.js";

// src/app/features/student/homework/homework-item/homework-item.component.ts
function HomeworkItemComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "a", 5);
    \u0275\u0275element(1, "i", 9);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275property("href", ctx_r0.item.link, \u0275\u0275sanitizeUrl);
  }
}
function HomeworkItemComponent_Conditional_11_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 8);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.item.description);
  }
}
var HomeworkItemComponent = class _HomeworkItemComponent {
  item;
  pending = false;
  toggleComplete = new EventEmitter();
  get toggleAriaLabel() {
    return this.item.completed ? `\u0421\u043D\u044F\u0442\u044C \u043E\u0442\u043C\u0435\u0442\u043A\u0443 \u0441 \u0437\u0430\u0434\u0430\u043D\u0438\u044F "${this.item.title}"` : `\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u0435 "${this.item.title}" \u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043D\u044B\u043C`;
  }
  onToggle() {
    this.toggleComplete.emit(this.item.id);
  }
  static \u0275fac = function HomeworkItemComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HomeworkItemComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HomeworkItemComponent, selectors: [["app-homework-item"]], inputs: { item: "item", pending: "pending" }, outputs: { toggleComplete: "toggleComplete" }, decls: 12, vars: 12, consts: [["role", "listitem", 1, "homework-item"], [3, "change", "checked", "disabled"], [1, "homework-item__content"], [1, "homework-item__header"], [1, "homework-item__title"], ["target", "_blank", "rel", "noopener", "aria-label", "\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043C\u0430\u0442\u0435\u0440\u0438\u0430\u043B\u044B \u043A \u0437\u0430\u0434\u0430\u043D\u0438\u044E (\u043D\u043E\u0432\u0430\u044F \u0432\u043A\u043B\u0430\u0434\u043A\u0430)", 1, "homework-item__link", 3, "href"], [1, "homework-item__subject"], [1, "ph-book-open", "ph-fill"], [1, "homework-item__description"], [1, "ph-arrow-square-out", "ph-fill"]], template: function HomeworkItemComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "mat-checkbox", 1);
      \u0275\u0275listener("change", function HomeworkItemComponent_Template_mat_checkbox_change_1_listener() {
        return ctx.onToggle();
      });
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(2, "div", 2)(3, "div", 3)(4, "span", 4);
      \u0275\u0275text(5);
      \u0275\u0275elementEnd();
      \u0275\u0275template(6, HomeworkItemComponent_Conditional_6_Template, 2, 1, "a", 5);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(7, "div", 6);
      \u0275\u0275element(8, "i", 7);
      \u0275\u0275elementStart(9, "span");
      \u0275\u0275text(10);
      \u0275\u0275elementEnd()();
      \u0275\u0275template(11, HomeworkItemComponent_Conditional_11_Template, 2, 1, "p", 8);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275classProp("is-completed", ctx.item.completed)("is-incomplete", !ctx.item.completed);
      \u0275\u0275attribute("aria-busy", ctx.pending || null);
      \u0275\u0275advance();
      \u0275\u0275property("checked", ctx.item.completed)("disabled", ctx.pending);
      \u0275\u0275attribute("aria-label", ctx.toggleAriaLabel);
      \u0275\u0275advance(4);
      \u0275\u0275textInterpolate(ctx.item.title);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.item.link ? 6 : -1);
      \u0275\u0275advance(4);
      \u0275\u0275textInterpolate(ctx.item.subjectId);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.item.description ? 11 : -1);
    }
  }, dependencies: [CommonModule, MatCheckboxModule, MatCheckbox], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.homework-item[_ngcontent-%COMP%] {\n  display: flex;\n  gap: var(--space-2);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-lg);\n  padding: var(--space-4);\n  border-left: 3px solid var(--border-default);\n  transition: opacity 150ms var(--ease-out);\n}\n.homework-item.is-completed[_ngcontent-%COMP%] {\n  opacity: 0.55;\n  border-left-color: var(--border-subtle);\n}\n.homework-item.is-completed[_ngcontent-%COMP%]   .homework-item__title[_ngcontent-%COMP%] {\n  text-decoration: line-through;\n}\n.homework-item__content[_ngcontent-%COMP%] {\n  flex: 1;\n  min-width: 0;\n}\n.homework-item__header[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: var(--space-2);\n}\n.homework-item__title[_ngcontent-%COMP%] {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  line-height: var(--leading-heading);\n}\n.homework-item__link[_ngcontent-%COMP%] {\n  color: var(--accent-secondary);\n  flex-shrink: 0;\n  min-width: 44px;\n  min-height: 44px;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n}\n.homework-item__subject[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-1);\n  margin-top: var(--space-1);\n  font-size: var(--text-xs);\n  color: var(--accent-secondary);\n  background: color-mix(in oklab, var(--accent-secondary) 10%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-secondary) 20%, transparent);\n  border-radius: var(--radius-full);\n  padding: 2px 8px;\n  width: fit-content;\n}\n.homework-item__description[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  line-height: var(--leading-body);\n  color: var(--text-secondary);\n  margin-top: var(--space-1);\n  display: -webkit-box;\n  -webkit-line-clamp: 2;\n  -webkit-box-orient: vertical;\n  overflow: hidden;\n}\n/*# sourceMappingURL=homework-item.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HomeworkItemComponent, [{
    type: Component,
    args: [{ selector: "app-homework-item", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule, MatCheckboxModule], template: `
    <div
      class="homework-item"
      [class.is-completed]="item.completed"
      [class.is-incomplete]="!item.completed"
      role="listitem"
      [attr.aria-busy]="pending || null">

      <!-- Checkbox -->
      <mat-checkbox
        [checked]="item.completed"
        [disabled]="pending"
        (change)="onToggle()"
        [attr.aria-label]="toggleAriaLabel">
      </mat-checkbox>

      <!-- Content -->
      <div class="homework-item__content">
        <div class="homework-item__header">
          <span class="homework-item__title">{{ item.title }}</span>
          @if (item.link) {
            <a
              [href]="item.link"
              target="_blank"
              rel="noopener"
              class="homework-item__link"
              aria-label="\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043C\u0430\u0442\u0435\u0440\u0438\u0430\u043B\u044B \u043A \u0437\u0430\u0434\u0430\u043D\u0438\u044E (\u043D\u043E\u0432\u0430\u044F \u0432\u043A\u043B\u0430\u0434\u043A\u0430)">
              <i class="ph-arrow-square-out ph-fill"></i>
            </a>
          }
        </div>
        <div class="homework-item__subject">
          <i class="ph-book-open ph-fill"></i>
          <span>{{ item.subjectId }}</span>
        </div>
        @if (item.description) {
          <p class="homework-item__description">{{ item.description }}</p>
        }
      </div>
    </div>
  `, styles: ["/* angular:styles/component:css;bb2726d8f54354b5d7b9ba90eddd67d9f5852ac18351d88370d94cd34a75901b;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/student/homework/homework-item/homework-item.component.ts */\n:host {\n  display: block;\n}\n.homework-item {\n  display: flex;\n  gap: var(--space-2);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-lg);\n  padding: var(--space-4);\n  border-left: 3px solid var(--border-default);\n  transition: opacity 150ms var(--ease-out);\n}\n.homework-item.is-completed {\n  opacity: 0.55;\n  border-left-color: var(--border-subtle);\n}\n.homework-item.is-completed .homework-item__title {\n  text-decoration: line-through;\n}\n.homework-item__content {\n  flex: 1;\n  min-width: 0;\n}\n.homework-item__header {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: var(--space-2);\n}\n.homework-item__title {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  line-height: var(--leading-heading);\n}\n.homework-item__link {\n  color: var(--accent-secondary);\n  flex-shrink: 0;\n  min-width: 44px;\n  min-height: 44px;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n}\n.homework-item__subject {\n  display: flex;\n  align-items: center;\n  gap: var(--space-1);\n  margin-top: var(--space-1);\n  font-size: var(--text-xs);\n  color: var(--accent-secondary);\n  background: color-mix(in oklab, var(--accent-secondary) 10%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-secondary) 20%, transparent);\n  border-radius: var(--radius-full);\n  padding: 2px 8px;\n  width: fit-content;\n}\n.homework-item__description {\n  font-size: var(--text-sm);\n  line-height: var(--leading-body);\n  color: var(--text-secondary);\n  margin-top: var(--space-1);\n  display: -webkit-box;\n  -webkit-line-clamp: 2;\n  -webkit-box-orient: vertical;\n  overflow: hidden;\n}\n/*# sourceMappingURL=homework-item.component.css.map */\n"] }]
  }], null, { item: [{
    type: Input,
    args: [{ required: true }]
  }], pending: [{
    type: Input
  }], toggleComplete: [{
    type: Output
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HomeworkItemComponent, { className: "HomeworkItemComponent", filePath: "src/app/features/student/homework/homework-item/homework-item.component.ts", lineNumber: 140 });
})();

// src/app/features/student/homework/student-homework.component.ts
var _c0 = () => [1, 2, 3, 4];
var _forTrack0 = ($index, $item) => $item.id;
function StudentHomeworkComponent_Conditional_6_For_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 7);
  }
}
function StudentHomeworkComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 4);
    \u0275\u0275repeaterCreate(1, StudentHomeworkComponent_Conditional_6_For_2_Template, 1, 0, "div", 7, \u0275\u0275repeaterTrackByIndex);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    \u0275\u0275advance();
    \u0275\u0275repeater(\u0275\u0275pureFunction0(0, _c0));
  }
}
function StudentHomeworkComponent_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 5);
    \u0275\u0275element(1, "i", 8);
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
function StudentHomeworkComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 6);
    \u0275\u0275element(1, "i", 9);
    \u0275\u0275elementStart(2, "h2");
    \u0275\u0275text(3, "\u0417\u0430\u0434\u0430\u043D\u0438\u0439 \u043D\u0435\u0442");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p");
    \u0275\u0275text(5, "\u0414\u043E\u043C\u0430\u0448\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F \u0434\u043B\u044F \u0433\u0440\u0443\u043F\u043F\u044B \u043F\u043E\u043A\u0430 \u043D\u0435 \u0434\u043E\u0431\u0430\u0432\u043B\u0435\u043D\u044B.");
    \u0275\u0275elementEnd()();
  }
}
function StudentHomeworkComponent_Conditional_9_Conditional_0_For_4_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 13);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const item_r3 = \u0275\u0275nextContext().$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.itemErrors()[item_r3.id]);
  }
}
function StudentHomeworkComponent_Conditional_9_Conditional_0_For_4_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "app-homework-item", 12);
    \u0275\u0275listener("toggleComplete", function StudentHomeworkComponent_Conditional_9_Conditional_0_For_4_Template_app_homework_item_toggleComplete_0_listener($event) {
      \u0275\u0275restoreView(_r2);
      const ctx_r0 = \u0275\u0275nextContext(3);
      return \u0275\u0275resetView(ctx_r0.onToggleComplete($event));
    });
    \u0275\u0275elementEnd();
    \u0275\u0275template(1, StudentHomeworkComponent_Conditional_9_Conditional_0_For_4_Conditional_1_Template, 2, 1, "p", 13);
  }
  if (rf & 2) {
    let tmp_13_0;
    const item_r3 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275property("item", item_r3)("pending", (tmp_13_0 = ctx_r0.pendingItems()[item_r3.id]) !== null && tmp_13_0 !== void 0 ? tmp_13_0 : false);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.itemErrors()[item_r3.id] ? 1 : -1);
  }
}
function StudentHomeworkComponent_Conditional_9_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 10);
    \u0275\u0275text(1, "\u0412\u044B\u043F\u043E\u043B\u043D\u0438\u0442\u044C");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(2, "div", 11);
    \u0275\u0275repeaterCreate(3, StudentHomeworkComponent_Conditional_9_Conditional_0_For_4_Template, 2, 3, null, null, _forTrack0);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(3);
    \u0275\u0275repeater(ctx_r0.incompleteItems());
  }
}
function StudentHomeworkComponent_Conditional_9_Conditional_1_For_4_Template(rf, ctx) {
  if (rf & 1) {
    const _r4 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "app-homework-item", 12);
    \u0275\u0275listener("toggleComplete", function StudentHomeworkComponent_Conditional_9_Conditional_1_For_4_Template_app_homework_item_toggleComplete_0_listener($event) {
      \u0275\u0275restoreView(_r4);
      const ctx_r0 = \u0275\u0275nextContext(3);
      return \u0275\u0275resetView(ctx_r0.onToggleComplete($event));
    });
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    let tmp_13_0;
    const item_r5 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275property("item", item_r5)("pending", (tmp_13_0 = ctx_r0.pendingItems()[item_r5.id]) !== null && tmp_13_0 !== void 0 ? tmp_13_0 : false);
  }
}
function StudentHomeworkComponent_Conditional_9_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 10);
    \u0275\u0275text(1, "\u0412\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(2, "div", 11);
    \u0275\u0275repeaterCreate(3, StudentHomeworkComponent_Conditional_9_Conditional_1_For_4_Template, 1, 2, "app-homework-item", 14, _forTrack0);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(3);
    \u0275\u0275repeater(ctx_r0.completeItems());
  }
}
function StudentHomeworkComponent_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275template(0, StudentHomeworkComponent_Conditional_9_Conditional_0_Template, 5, 0)(1, StudentHomeworkComponent_Conditional_9_Conditional_1_Template, 5, 0);
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275conditional(ctx_r0.incompleteItems().length > 0 ? 0 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.completeItems().length > 0 ? 1 : -1);
  }
}
var StudentHomeworkComponent = class _StudentHomeworkComponent {
  authService = inject(AuthService);
  apiService = inject(StudentApiService);
  destroyRef = inject(DestroyRef);
  loading = signal(false);
  error = signal(null);
  items = signal([]);
  /** Map of itemId → boolean: true while API call in flight for that item */
  pendingItems = signal({});
  /** Map of itemId → error message when optimistic toggle failed */
  itemErrors = signal({});
  incompleteItems = computed(() => this.items().filter((i) => !i.completed).sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
  completeItems = computed(() => this.items().filter((i) => i.completed).sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
  ngOnInit() {
    const groupId = this.authService.currentUser()?.groupId;
    if (groupId == null) {
      this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u044F. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u043F\u043E\u0434\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u0435 \u0438 \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437.");
      return;
    }
    this.loading.set(true);
    this.apiService.getActiveSemesterId().pipe(switchMap((semesterId) => {
      if (semesterId == null)
        return of([]);
      return this.apiService.getHomeworks(groupId, semesterId);
    })).subscribe({
      next: (homeworks) => {
        this.items.set(homeworks);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u044F. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u043F\u043E\u0434\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u0435 \u0438 \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437.");
        this.loading.set(false);
      }
    });
  }
  onToggleComplete(itemId) {
    const current = this.items().find((i) => i.id === itemId);
    if (!current)
      return;
    this.itemErrors.update((e) => __spreadProps(__spreadValues({}, e), { [itemId]: "" }));
    this.pendingItems.update((p) => __spreadProps(__spreadValues({}, p), { [itemId]: true }));
    this.items.update((list) => list.map((i) => i.id === itemId ? __spreadProps(__spreadValues({}, i), { completed: !i.completed }) : i));
    const call$ = current.completed ? this.apiService.unmarkHomeworkComplete(itemId) : this.apiService.markHomeworkComplete(itemId);
    call$.subscribe({
      next: () => {
        this.pendingItems.update((p) => __spreadProps(__spreadValues({}, p), { [itemId]: false }));
      },
      error: () => {
        this.items.update((list) => list.map((i) => i.id === itemId ? __spreadProps(__spreadValues({}, i), { completed: current.completed }) : i));
        this.pendingItems.update((p) => __spreadProps(__spreadValues({}, p), { [itemId]: false }));
        this.itemErrors.update((e) => __spreadProps(__spreadValues({}, e), {
          [itemId]: "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u0431\u043D\u043E\u0432\u0438\u0442\u044C \u0441\u0442\u0430\u0442\u0443\u0441. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437."
        }));
      }
    });
  }
  static \u0275fac = function StudentHomeworkComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentHomeworkComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentHomeworkComponent, selectors: [["app-student-homework"]], decls: 10, vars: 5, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-header__eyebrow"], [1, "page-header__title"], ["role", "list", "aria-busy", "true", 1, "homework-list"], ["role", "alert", 1, "page-error"], [1, "page-empty"], ["role", "listitem", 1, "homework-skeleton"], [1, "ph-warning-circle", "ph-fill"], [1, "ph-notebook-text", "ph-duotone"], [1, "homework-section-label"], ["role", "list", 1, "homework-list"], [3, "toggleComplete", "item", "pending"], ["role", "alert", 1, "homework-item-error"], [3, "item", "pending"]], template: function StudentHomeworkComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "header", 1)(2, "p", 2);
      \u0275\u0275text(3, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(4, "h1", 3);
      \u0275\u0275text(5, "\u0414\u043E\u043C\u0430\u0448\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F");
      \u0275\u0275elementEnd()();
      \u0275\u0275template(6, StudentHomeworkComponent_Conditional_6_Template, 3, 1, "div", 4)(7, StudentHomeworkComponent_Conditional_7_Template, 4, 1, "div", 5)(8, StudentHomeworkComponent_Conditional_8_Template, 6, 0, "div", 6)(9, StudentHomeworkComponent_Conditional_9_Template, 2, 2);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(6);
      \u0275\u0275conditional(ctx.loading() ? 6 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(!ctx.loading() && ctx.error() ? 7 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(!ctx.loading() && !ctx.error() && ctx.items().length === 0 ? 8 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(!ctx.loading() && !ctx.error() && ctx.items().length > 0 ? 9 : -1);
    }
  }, dependencies: [CommonModule, HomeworkItemComponent], styles: ["\n\n.homework-list[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3, 12px);\n}\n.homework-skeleton[_ngcontent-%COMP%] {\n  height: 80px;\n  border-radius: var(--radius-lg, 12px);\n  background:\n    linear-gradient(\n      90deg,\n      var(--bg-elevated) 25%,\n      color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,\n      var(--bg-elevated) 75%);\n  background-size: 200% 100%;\n  animation: dashboard-shimmer 1.5s linear infinite;\n}\n.homework-section-label[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  font-weight: 600;\n  text-transform: uppercase;\n  letter-spacing: 0.05em;\n  color: var(--text-muted);\n  margin-bottom: var(--space-1);\n}\n.homework-item-error[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  color: var(--accent-danger);\n  margin-top: calc(-1 * var(--space-2));\n}\n@media (prefers-reduced-motion: reduce) {\n  .homework-skeleton[_ngcontent-%COMP%] {\n    animation: none;\n  }\n}\n/*# sourceMappingURL=student-homework.component.css.map */"], data: { animation: [
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
    args: [{ selector: "app-student-homework", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule, HomeworkItemComponent], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: '<div class="page-stack" [@routeFade]>\r\n  <!-- Page header -->\r\n  <header class="page-header">\r\n    <p class="page-header__eyebrow">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</p>\r\n    <h1 class="page-header__title">\u0414\u043E\u043C\u0430\u0448\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F</h1>\r\n  </header>\r\n\r\n  <!-- Loading skeleton -->\r\n  @if (loading()) {\r\n    <div class="homework-list" role="list" aria-busy="true">\r\n      @for (_ of [1,2,3,4]; track $index) {\r\n        <div class="homework-skeleton" role="listitem"></div>\r\n      }\r\n    </div>\r\n  }\r\n\r\n  <!-- Error state -->\r\n  @if (!loading() && error()) {\r\n    <div class="page-error" role="alert">\r\n      <i class="ph-warning-circle ph-fill"></i>\r\n      <span>{{ error() }}</span>\r\n    </div>\r\n  }\r\n\r\n  <!-- Empty state -->\r\n  @if (!loading() && !error() && items().length === 0) {\r\n    <div class="page-empty">\r\n      <i class="ph-notebook-text ph-duotone"></i>\r\n      <h2>\u0417\u0430\u0434\u0430\u043D\u0438\u0439 \u043D\u0435\u0442</h2>\r\n      <p>\u0414\u043E\u043C\u0430\u0448\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F \u0434\u043B\u044F \u0433\u0440\u0443\u043F\u043F\u044B \u043F\u043E\u043A\u0430 \u043D\u0435 \u0434\u043E\u0431\u0430\u0432\u043B\u0435\u043D\u044B.</p>\r\n    </div>\r\n  }\r\n\r\n  <!-- Homework list -->\r\n  @if (!loading() && !error() && items().length > 0) {\r\n    @if (incompleteItems().length > 0) {\r\n      <p class="homework-section-label">\u0412\u044B\u043F\u043E\u043B\u043D\u0438\u0442\u044C</p>\r\n      <div class="homework-list" role="list">\r\n        @for (item of incompleteItems(); track item.id) {\r\n          <app-homework-item\r\n            [item]="item"\r\n            [pending]="pendingItems()[item.id] ?? false"\r\n            (toggleComplete)="onToggleComplete($event)">\r\n          </app-homework-item>\r\n          @if (itemErrors()[item.id]) {\r\n            <p class="homework-item-error" role="alert">{{ itemErrors()[item.id] }}</p>\r\n          }\r\n        }\r\n      </div>\r\n    }\r\n    @if (completeItems().length > 0) {\r\n      <p class="homework-section-label">\u0412\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E</p>\r\n      <div class="homework-list" role="list">\r\n        @for (item of completeItems(); track item.id) {\r\n          <app-homework-item\r\n            [item]="item"\r\n            [pending]="pendingItems()[item.id] ?? false"\r\n            (toggleComplete)="onToggleComplete($event)">\r\n          </app-homework-item>\r\n        }\r\n      </div>\r\n    }\r\n  }\r\n</div>\r\n', styles: ["/* src/app/features/student/homework/student-homework.component.css */\n.homework-list {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3, 12px);\n}\n.homework-skeleton {\n  height: 80px;\n  border-radius: var(--radius-lg, 12px);\n  background:\n    linear-gradient(\n      90deg,\n      var(--bg-elevated) 25%,\n      color-mix(in oklab, var(--text-primary) 8%, transparent) 50%,\n      var(--bg-elevated) 75%);\n  background-size: 200% 100%;\n  animation: dashboard-shimmer 1.5s linear infinite;\n}\n.homework-section-label {\n  font-size: var(--text-xs);\n  font-weight: 600;\n  text-transform: uppercase;\n  letter-spacing: 0.05em;\n  color: var(--text-muted);\n  margin-bottom: var(--space-1);\n}\n.homework-item-error {\n  font-size: var(--text-xs);\n  color: var(--accent-danger);\n  margin-top: calc(-1 * var(--space-2));\n}\n@media (prefers-reduced-motion: reduce) {\n  .homework-skeleton {\n    animation: none;\n  }\n}\n/*# sourceMappingURL=student-homework.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentHomeworkComponent, { className: "StudentHomeworkComponent", filePath: "src/app/features/student/homework/student-homework.component.ts", lineNumber: 51 });
})();
export {
  StudentHomeworkComponent
};
//# sourceMappingURL=chunk-YV4SUYVU.js.map
