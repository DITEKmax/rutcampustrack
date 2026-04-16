import {
  ConfirmDialogComponent
} from "./chunk-KQCCUEDY.js";
import {
  HeadmanApiService
} from "./chunk-BU5FJGI6.js";
import {
  MatProgressSpinner,
  MatProgressSpinnerModule
} from "./chunk-BAW76DQ6.js";
import "./chunk-F5IUR55I.js";
import {
  SubjectCacheService
} from "./chunk-QY7VTECN.js";
import {
  HomeworkCardComponent
} from "./chunk-EI5Q5OC5.js";
import {
  addDays,
  formatDate,
  formatWeekRange,
  getMonday,
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
import {
  MatInput,
  MatInputModule
} from "./chunk-WQN3TC62.js";
import "./chunk-POR7KUSL.js";
import "./chunk-OIBNGD5S.js";
import {
  MatError,
  MatFormField,
  MatFormFieldModule,
  MatLabel
} from "./chunk-Z3VV4J2X.js";
import {
  MatButton,
  MatButtonModule
} from "./chunk-PAS4QIDL.js";
import "./chunk-4RJPSRCU.js";
import "./chunk-6JM2QIGP.js";
import "./chunk-75YPR2VK.js";
import "./chunk-2UA5NBNP.js";
import "./chunk-YIJEORIR.js";
import "./chunk-B3BDHDAM.js";
import {
  DefaultValueAccessor,
  FormControl,
  FormControlName,
  FormGroup,
  FormGroupDirective,
  MaxLengthValidator,
  NgControlStatus,
  NgControlStatusGroup,
  ReactiveFormsModule,
  RequiredValidator,
  Validators,
  ɵNgNoValidate
} from "./chunk-XAMIXVT7.js";
import {
  AsyncPipe,
  CommonModule,
  HttpClient,
  HttpParams
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  EventEmitter,
  Injectable,
  Input,
  Output,
  catchError,
  computed,
  forkJoin,
  inject,
  map,
  of,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵdefineInjectable,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵgetCurrentView,
  ɵɵlistener,
  ɵɵnextContext,
  ɵɵpipe,
  ɵɵpipeBind1,
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
} from "./chunk-MFRIGFR2.js";

// src/app/shared/week-navigator/week-navigator.component.ts
function WeekNavigatorComponent_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 9);
    \u0275\u0275listener("click", function WeekNavigatorComponent_Conditional_10_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r1);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1.today());
    });
    \u0275\u0275element(1, "i", 10);
    \u0275\u0275text(2, " \u0421\u0435\u0433\u043E\u0434\u043D\u044F ");
    \u0275\u0275elementEnd();
  }
}
var WeekNavigatorComponent = class _WeekNavigatorComponent {
  initialDate = /* @__PURE__ */ new Date();
  weekChanged = new EventEmitter();
  initialMonday = signal(getMonday(/* @__PURE__ */ new Date()));
  weekOffset = signal(0);
  currentMonday = computed(() => addDays(this.initialMonday(), this.weekOffset() * 7));
  weekLabel = computed(() => formatWeekRange(this.currentMonday()));
  isCurrentWeek = computed(() => isSameWeek(this.currentMonday(), /* @__PURE__ */ new Date()));
  ngOnInit() {
    this.initialMonday.set(getMonday(this.initialDate));
    this.weekOffset.set(0);
    this.emit();
  }
  previous() {
    this.weekOffset.update((v) => v - 1);
    this.emit();
  }
  next() {
    this.weekOffset.update((v) => v + 1);
    this.emit();
  }
  today() {
    this.initialMonday.set(getMonday(/* @__PURE__ */ new Date()));
    this.weekOffset.set(0);
    this.emit();
  }
  emit() {
    this.weekChanged.emit({
      monday: this.currentMonday(),
      weekOffset: this.weekOffset()
    });
  }
  static \u0275fac = function WeekNavigatorComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _WeekNavigatorComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _WeekNavigatorComponent, selectors: [["app-week-navigator"]], inputs: { initialDate: "initialDate" }, outputs: { weekChanged: "weekChanged" }, decls: 11, vars: 2, consts: [["aria-label", "\u041D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F \u043F\u043E \u043D\u0435\u0434\u0435\u043B\u044F\u043C", 1, "week-nav"], ["type", "button", "aria-label", "\u041F\u0440\u0435\u0434\u044B\u0434\u0443\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F", 1, "week-nav__btn", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-caret-left"], [1, "week-nav__label"], [1, "week-nav__eyebrow"], [1, "week-nav__range"], ["type", "button", "aria-label", "\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F", 1, "week-nav__btn", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-caret-right"], ["type", "button", "aria-label", "\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043D\u0430 \u0442\u0435\u043A\u0443\u0449\u0443\u044E \u043D\u0435\u0434\u0435\u043B\u044E", 1, "week-nav__today"], ["type", "button", "aria-label", "\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043D\u0430 \u0442\u0435\u043A\u0443\u0449\u0443\u044E \u043D\u0435\u0434\u0435\u043B\u044E", 1, "week-nav__today", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-calendar-blank"]], template: function WeekNavigatorComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "nav", 0)(1, "button", 1);
      \u0275\u0275listener("click", function WeekNavigatorComponent_Template_button_click_1_listener() {
        return ctx.previous();
      });
      \u0275\u0275element(2, "i", 2);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(3, "div", 3)(4, "span", 4);
      \u0275\u0275text(5, "\u041D\u0435\u0434\u0435\u043B\u044F");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(6, "span", 5);
      \u0275\u0275text(7);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(8, "button", 6);
      \u0275\u0275listener("click", function WeekNavigatorComponent_Template_button_click_8_listener() {
        return ctx.next();
      });
      \u0275\u0275element(9, "i", 7);
      \u0275\u0275elementEnd();
      \u0275\u0275template(10, WeekNavigatorComponent_Conditional_10_Template, 3, 0, "button", 8);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275advance(7);
      \u0275\u0275textInterpolate(ctx.weekLabel());
      \u0275\u0275advance(3);
      \u0275\u0275conditional(!ctx.isCurrentWeek() ? 10 : -1);
    }
  }, dependencies: [CommonModule], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.week-nav[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  padding: var(--space-2, 8px) var(--space-3, 12px);\n  background: var(--bg-secondary, #f5f5f5);\n  border-radius: var(--radius-lg, 12px);\n  border: 1px solid var(--border-subtle, #e0e0e0);\n}\n.week-nav__btn[_ngcontent-%COMP%] {\n  min-width: 40px;\n  min-height: 40px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  border: none;\n  background: transparent;\n  border-radius: var(--radius-md, 8px);\n  cursor: pointer;\n  color: var(--text-primary);\n  transition: background 150ms ease;\n}\n.week-nav__btn[_ngcontent-%COMP%]:hover {\n  background: var(--bg-hover, #eee);\n}\n.week-nav__btn[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 1.2rem;\n}\n.week-nav__label[_ngcontent-%COMP%] {\n  flex: 1;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: 2px;\n}\n.week-nav__eyebrow[_ngcontent-%COMP%] {\n  font-size: 0.7rem;\n  text-transform: uppercase;\n  color: var(--text-muted);\n  letter-spacing: 0.04em;\n}\n.week-nav__range[_ngcontent-%COMP%] {\n  font-weight: 600;\n  font-size: 0.95rem;\n}\n.week-nav__today[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: 4px;\n  padding: 6px 12px;\n  border-radius: var(--radius-full, 999px);\n  border: 1px solid var(--border-subtle, #d0d0d0);\n  background: var(--bg-elevated, #fff);\n  color: var(--text-primary);\n  font-size: 0.85rem;\n  cursor: pointer;\n  transition: background 150ms ease;\n}\n.week-nav__today[_ngcontent-%COMP%]:hover {\n  background: var(--bg-hover, #eee);\n}\n/*# sourceMappingURL=week-navigator.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(WeekNavigatorComponent, [{
    type: Component,
    args: [{ selector: "app-week-navigator", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule], template: `
    <nav class="week-nav" aria-label="\u041D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F \u043F\u043E \u043D\u0435\u0434\u0435\u043B\u044F\u043C">
      <button
        type="button"
        class="week-nav__btn"
        (click)="previous()"
        aria-label="\u041F\u0440\u0435\u0434\u044B\u0434\u0443\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F">
        <i class="ph ph-caret-left" aria-hidden="true"></i>
      </button>
      <div class="week-nav__label">
        <span class="week-nav__eyebrow">\u041D\u0435\u0434\u0435\u043B\u044F</span>
        <span class="week-nav__range">{{ weekLabel() }}</span>
      </div>
      <button
        type="button"
        class="week-nav__btn"
        (click)="next()"
        aria-label="\u0421\u043B\u0435\u0434\u0443\u044E\u0449\u0430\u044F \u043D\u0435\u0434\u0435\u043B\u044F">
        <i class="ph ph-caret-right" aria-hidden="true"></i>
      </button>
      @if (!isCurrentWeek()) {
        <button
          type="button"
          class="week-nav__today"
          (click)="today()"
          aria-label="\u041F\u0435\u0440\u0435\u0439\u0442\u0438 \u043D\u0430 \u0442\u0435\u043A\u0443\u0449\u0443\u044E \u043D\u0435\u0434\u0435\u043B\u044E">
          <i class="ph ph-calendar-blank" aria-hidden="true"></i>
          \u0421\u0435\u0433\u043E\u0434\u043D\u044F
        </button>
      }
    </nav>
  `, styles: ["/* angular:styles/component:css;36b3bacf2160a0cddd99e38b1479f11b663d8876bd131e82c77e23adc55728d8;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/shared/week-navigator/week-navigator.component.ts */\n:host {\n  display: block;\n}\n.week-nav {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  padding: var(--space-2, 8px) var(--space-3, 12px);\n  background: var(--bg-secondary, #f5f5f5);\n  border-radius: var(--radius-lg, 12px);\n  border: 1px solid var(--border-subtle, #e0e0e0);\n}\n.week-nav__btn {\n  min-width: 40px;\n  min-height: 40px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  border: none;\n  background: transparent;\n  border-radius: var(--radius-md, 8px);\n  cursor: pointer;\n  color: var(--text-primary);\n  transition: background 150ms ease;\n}\n.week-nav__btn:hover {\n  background: var(--bg-hover, #eee);\n}\n.week-nav__btn i {\n  font-size: 1.2rem;\n}\n.week-nav__label {\n  flex: 1;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: 2px;\n}\n.week-nav__eyebrow {\n  font-size: 0.7rem;\n  text-transform: uppercase;\n  color: var(--text-muted);\n  letter-spacing: 0.04em;\n}\n.week-nav__range {\n  font-weight: 600;\n  font-size: 0.95rem;\n}\n.week-nav__today {\n  display: inline-flex;\n  align-items: center;\n  gap: 4px;\n  padding: 6px 12px;\n  border-radius: var(--radius-full, 999px);\n  border: 1px solid var(--border-subtle, #d0d0d0);\n  background: var(--bg-elevated, #fff);\n  color: var(--text-primary);\n  font-size: 0.85rem;\n  cursor: pointer;\n  transition: background 150ms ease;\n}\n.week-nav__today:hover {\n  background: var(--bg-hover, #eee);\n}\n/*# sourceMappingURL=week-navigator.component.css.map */\n"] }]
  }], null, { initialDate: [{
    type: Input
  }], weekChanged: [{
    type: Output
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(WeekNavigatorComponent, { className: "WeekNavigatorComponent", filePath: "src/app/shared/week-navigator/week-navigator.component.ts", lineNumber: 136 });
})();

// src/app/features/headman/homework/headman-homework-api.service.ts
var HeadmanHomeworkApiService = class _HeadmanHomeworkApiService {
  http = inject(HttpClient);
  base = "/api/academic/homeworks";
  /** Fetch homeworks for a group + semester (paged, client-filters by date). */
  list(groupId, semesterId) {
    const params = new HttpParams().set("groupId", String(groupId)).set("semesterId", String(semesterId)).set("size", "200");
    return this.http.get(this.base, { params }).pipe(map((resp) => resp._embedded?.homeworkResponseList ?? []));
  }
  create(req) {
    return this.http.post(this.base, req);
  }
  update(id, req) {
    return this.http.put(`${this.base}/${id}`, req);
  }
  delete(id) {
    return this.http.delete(`${this.base}/${id}`);
  }
  static \u0275fac = function HeadmanHomeworkApiService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanHomeworkApiService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _HeadmanHomeworkApiService, factory: _HeadmanHomeworkApiService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanHomeworkApiService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], null, null);
})();

// src/app/features/headman/homework/homework-inline-form/homework-inline-form.component.ts
function HomeworkInlineFormComponent_Conditional_20_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435 \u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E");
    \u0275\u0275elementEnd();
  }
}
function HomeworkInlineFormComponent_Conditional_21_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u041D\u0435 \u0431\u043E\u043B\u0435\u0435 255 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432");
    \u0275\u0275elementEnd();
  }
}
function HomeworkInlineFormComponent_Conditional_26_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u041D\u0435 \u0431\u043E\u043B\u0435\u0435 4000 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432");
    \u0275\u0275elementEnd();
  }
}
function HomeworkInlineFormComponent_Conditional_31_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u041D\u0435 \u0431\u043E\u043B\u0435\u0435 2048 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432");
    \u0275\u0275elementEnd();
  }
}
function HomeworkInlineFormComponent_Conditional_32_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 11);
    \u0275\u0275element(1, "i", 16);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.apiError(), " ");
  }
}
function HomeworkInlineFormComponent_Conditional_37_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-spinner", 15);
  }
}
var HomeworkInlineFormComponent = class _HomeworkInlineFormComponent {
  api = inject(HeadmanHomeworkApiService);
  subjectId;
  subjectName;
  lessonDate;
  // YYYY-MM-DD
  lessonNumber;
  groupId;
  semesterId;
  initialHomework = null;
  saved = new EventEmitter();
  cancelled = new EventEmitter();
  form = new FormGroup({
    title: new FormControl("", {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(255)]
    }),
    description: new FormControl("", {
      nonNullable: true,
      validators: [Validators.maxLength(4e3)]
    }),
    link: new FormControl("", {
      nonNullable: true,
      validators: [Validators.maxLength(2048)]
    })
  });
  submitting = signal(false);
  apiError = signal(null);
  ngOnInit() {
    if (this.initialHomework) {
      this.form.patchValue({
        title: this.initialHomework.title ?? "",
        description: this.initialHomework.description ?? "",
        link: this.initialHomework.link ?? ""
      });
    }
  }
  isEdit() {
    return this.initialHomework !== null;
  }
  ariaLabel() {
    return this.isEdit() ? "\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u0434\u043E\u043C\u0430\u0448\u043D\u0435\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u0435" : "\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0434\u043E\u043C\u0430\u0448\u043D\u0435\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u0435";
  }
  formattedDate() {
    const [y, m, d] = this.lessonDate.split("-");
    const months = [
      "\u044F\u043D\u0432",
      "\u0444\u0435\u0432",
      "\u043C\u0430\u0440",
      "\u0430\u043F\u0440",
      "\u043C\u0430\u044F",
      "\u0438\u044E\u043D",
      "\u0438\u044E\u043B",
      "\u0430\u0432\u0433",
      "\u0441\u0435\u043D",
      "\u043E\u043A\u0442",
      "\u043D\u043E\u044F",
      "\u0434\u0435\u043A"
    ];
    const mi = Math.max(0, Math.min(11, parseInt(m, 10) - 1));
    return `${parseInt(d, 10)} ${months[mi]} ${y}`;
  }
  submit() {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const title = v.title.trim();
    const description = v.description.trim() || null;
    const link = v.link.trim() || null;
    this.submitting.set(true);
    this.apiError.set(null);
    const call$ = this.isEdit() ? this.api.update(this.initialHomework.id, {
      title,
      description,
      link
    }) : this.api.create({
      title,
      description,
      link,
      subjectId: this.subjectId,
      groupId: this.groupId,
      semesterId: this.semesterId,
      lessonDate: this.lessonDate,
      lessonNumber: this.lessonNumber
    });
    call$.subscribe({
      next: (hw) => {
        this.submitting.set(false);
        this.saved.emit(hw);
      },
      error: (err) => {
        this.submitting.set(false);
        this.apiError.set(this.extractError(err));
      }
    });
  }
  cancel() {
    if (this.submitting())
      return;
    this.cancelled.emit();
  }
  /**
   * Best-effort extraction of a human-readable error from an HttpErrorResponse.
   * Backend emits RFC 7807 `ErrorResponse` with `detail` (and optional `field`);
   * falls back to a generic message if the shape differs.
   */
  extractError(err) {
    const e = err;
    const detail = e?.error?.detail ?? e?.error?.message;
    if (detail)
      return detail;
    if (e?.status === 403)
      return "\u041D\u0435\u0434\u043E\u0441\u0442\u0430\u0442\u043E\u0447\u043D\u043E \u043F\u0440\u0430\u0432 \u0434\u043B\u044F \u044D\u0442\u043E\u0433\u043E \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044F.";
    if (e?.status === 400)
      return "\u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0432\u0432\u0435\u0434\u0451\u043D\u043D\u044B\u0435 \u0434\u0430\u043D\u043D\u044B\u0435.";
    return "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0441\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u0435. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437.";
  }
  static \u0275fac = function HomeworkInlineFormComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HomeworkInlineFormComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HomeworkInlineFormComponent, selectors: [["app-homework-inline-form"]], inputs: { subjectId: "subjectId", subjectName: "subjectName", lessonDate: "lessonDate", lessonNumber: "lessonNumber", groupId: "groupId", semesterId: "semesterId", initialHomework: "initialHomework" }, outputs: { saved: "saved", cancelled: "cancelled" }, decls: 39, vars: 15, consts: [["role", "form", 1, "hw-form"], [1, "hw-form__context"], ["aria-hidden", "true", 1, "ph", "ph-book-open"], [1, "hw-form__context-label"], [1, "hw-form__context-value"], [1, "hw-form__context-sep"], ["novalidate", "", 3, "ngSubmit", "formGroup"], ["appearance", "outline", 1, "hw-form__field"], ["matInput", "", "formControlName", "title", "maxlength", "255", "placeholder", "\u041D\u0430\u043F\u0440\u0438\u043C\u0435\u0440: \u0420\u0435\u0448\u0438\u0442\u044C \u211612-18 \u0438\u0437 \u0443\u0447\u0435\u0431\u043D\u0438\u043A\u0430", "required", ""], ["matInput", "", "formControlName", "description", "rows", "3", "maxlength", "4000", "placeholder", "\u0427\u0442\u043E \u0438\u043C\u0435\u043D\u043D\u043E \u043D\u0443\u0436\u043D\u043E \u0441\u0434\u0435\u043B\u0430\u0442\u044C, \u043A\u0430\u043A\u0438\u0435 \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u044B/\u0437\u0430\u0434\u0430\u0447\u0438 \u0438 \u0442.\u0434."], ["matInput", "", "formControlName", "link", "type", "url", "maxlength", "2048", "placeholder", "https://..."], ["role", "alert", 1, "hw-form__error"], [1, "hw-form__actions"], ["type", "button", "mat-stroked-button", "", 3, "click", "disabled"], ["type", "submit", "mat-flat-button", "", "color", "primary", 1, "btn-brand", 3, "disabled"], ["diameter", "16", "aria-hidden", "true"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"]], template: function HomeworkInlineFormComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1);
      \u0275\u0275element(2, "i", 2);
      \u0275\u0275elementStart(3, "span", 3);
      \u0275\u0275text(4, "\u041F\u0440\u0435\u0434\u043C\u0435\u0442:");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "span", 4);
      \u0275\u0275text(6);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(7, "span", 5);
      \u0275\u0275text(8, "\xB7");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(9, "span");
      \u0275\u0275text(10);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(11, "span", 5);
      \u0275\u0275text(12, "\xB7");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(13, "span");
      \u0275\u0275text(14);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(15, "form", 6);
      \u0275\u0275listener("ngSubmit", function HomeworkInlineFormComponent_Template_form_ngSubmit_15_listener() {
        return ctx.submit();
      });
      \u0275\u0275elementStart(16, "mat-form-field", 7)(17, "mat-label");
      \u0275\u0275text(18, "\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F");
      \u0275\u0275elementEnd();
      \u0275\u0275element(19, "input", 8);
      \u0275\u0275template(20, HomeworkInlineFormComponent_Conditional_20_Template, 2, 0, "mat-error")(21, HomeworkInlineFormComponent_Conditional_21_Template, 2, 0, "mat-error");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(22, "mat-form-field", 7)(23, "mat-label");
      \u0275\u0275text(24, "\u041E\u043F\u0438\u0441\u0430\u043D\u0438\u0435 (\u043D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E)");
      \u0275\u0275elementEnd();
      \u0275\u0275element(25, "textarea", 9);
      \u0275\u0275template(26, HomeworkInlineFormComponent_Conditional_26_Template, 2, 0, "mat-error");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(27, "mat-form-field", 7)(28, "mat-label");
      \u0275\u0275text(29, "\u0421\u0441\u044B\u043B\u043A\u0430 \u043D\u0430 \u043C\u0430\u0442\u0435\u0440\u0438\u0430\u043B\u044B (\u043D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E)");
      \u0275\u0275elementEnd();
      \u0275\u0275element(30, "input", 10);
      \u0275\u0275template(31, HomeworkInlineFormComponent_Conditional_31_Template, 2, 0, "mat-error");
      \u0275\u0275elementEnd();
      \u0275\u0275template(32, HomeworkInlineFormComponent_Conditional_32_Template, 3, 1, "div", 11);
      \u0275\u0275elementStart(33, "div", 12)(34, "button", 13);
      \u0275\u0275listener("click", function HomeworkInlineFormComponent_Template_button_click_34_listener() {
        return ctx.cancel();
      });
      \u0275\u0275text(35, " \u041E\u0442\u043C\u0435\u043D\u0430 ");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(36, "button", 14);
      \u0275\u0275template(37, HomeworkInlineFormComponent_Conditional_37_Template, 1, 0, "mat-spinner", 15);
      \u0275\u0275text(38);
      \u0275\u0275elementEnd()()()();
    }
    if (rf & 2) {
      let tmp_6_0;
      let tmp_7_0;
      let tmp_8_0;
      let tmp_9_0;
      \u0275\u0275property("@expand", void 0);
      \u0275\u0275attribute("aria-label", ctx.ariaLabel());
      \u0275\u0275advance(6);
      \u0275\u0275textInterpolate(ctx.subjectName);
      \u0275\u0275advance(4);
      \u0275\u0275textInterpolate(ctx.formattedDate());
      \u0275\u0275advance(4);
      \u0275\u0275textInterpolate1("\u041F\u0430\u0440\u0430 \u2116 ", ctx.lessonNumber, "");
      \u0275\u0275advance();
      \u0275\u0275property("formGroup", ctx.form);
      \u0275\u0275advance(5);
      \u0275\u0275conditional(((tmp_6_0 = ctx.form.get("title")) == null ? null : tmp_6_0.hasError("required")) && ((tmp_6_0 = ctx.form.get("title")) == null ? null : tmp_6_0.touched) ? 20 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(((tmp_7_0 = ctx.form.get("title")) == null ? null : tmp_7_0.hasError("maxlength")) ? 21 : -1);
      \u0275\u0275advance(5);
      \u0275\u0275conditional(((tmp_8_0 = ctx.form.get("description")) == null ? null : tmp_8_0.hasError("maxlength")) ? 26 : -1);
      \u0275\u0275advance(5);
      \u0275\u0275conditional(((tmp_9_0 = ctx.form.get("link")) == null ? null : tmp_9_0.hasError("maxlength")) ? 31 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.apiError() ? 32 : -1);
      \u0275\u0275advance(2);
      \u0275\u0275property("disabled", ctx.submitting());
      \u0275\u0275advance(2);
      \u0275\u0275property("disabled", ctx.form.invalid || ctx.submitting());
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.submitting() ? 37 : -1);
      \u0275\u0275advance();
      \u0275\u0275textInterpolate1(" ", ctx.isEdit() ? "\u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C" : "\u0421\u043E\u0437\u0434\u0430\u0442\u044C", " ");
    }
  }, dependencies: [
    CommonModule,
    ReactiveFormsModule,
    \u0275NgNoValidate,
    DefaultValueAccessor,
    NgControlStatus,
    NgControlStatusGroup,
    RequiredValidator,
    MaxLengthValidator,
    FormGroupDirective,
    FormControlName,
    MatButtonModule,
    MatButton,
    MatFormFieldModule,
    MatFormField,
    MatLabel,
    MatError,
    MatInputModule,
    MatInput,
    MatProgressSpinnerModule,
    MatProgressSpinner
  ], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n}\n.hw-form[_ngcontent-%COMP%] {\n  margin-top: 8px;\n  padding: var(--space-4, 16px);\n  background: var(--bg-elevated, #fff);\n  border: 1px solid var(--accent-secondary, #1976d2);\n  border-radius: var(--radius-lg, 12px);\n}\n.hw-form__context[_ngcontent-%COMP%] {\n  display: flex;\n  flex-wrap: wrap;\n  align-items: center;\n  gap: 6px;\n  font-size: 0.8rem;\n  color: var(--text-secondary, #555);\n  margin-bottom: 12px;\n  padding-bottom: 8px;\n  border-bottom: 1px dashed var(--border-subtle, #e0e0e0);\n}\n.hw-form__context-label[_ngcontent-%COMP%] {\n  font-weight: 500;\n}\n.hw-form__context-value[_ngcontent-%COMP%] {\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.hw-form__context-sep[_ngcontent-%COMP%] {\n  color: var(--text-muted, #999);\n}\n.hw-form__field[_ngcontent-%COMP%] {\n  width: 100%;\n  display: block;\n  margin-bottom: 4px;\n}\n.hw-form__error[_ngcontent-%COMP%] {\n  margin: var(--space-2) 0;\n  padding: var(--space-2) var(--space-3);\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 28%, transparent);\n  color: var(--accent-danger);\n  border-radius: var(--radius-md);\n  font-size: 0.8125rem;\n  display: inline-flex;\n  align-items: center;\n  gap: 6px;\n}\n.hw-form__actions[_ngcontent-%COMP%] {\n  display: flex;\n  gap: 8px;\n  justify-content: flex-end;\n  margin-top: 8px;\n}\n/*# sourceMappingURL=homework-inline-form.component.css.map */"], data: { animation: [
    trigger("expand", [
      transition(":enter", [
        style({ height: 0, opacity: 0, overflow: "hidden" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ height: "*", opacity: 1 }))
      ]),
      transition(":leave", [
        style({ overflow: "hidden" }),
        animate("150ms ease-in", style({ height: 0, opacity: 0 }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HomeworkInlineFormComponent, [{
    type: Component,
    args: [{ selector: "app-homework-inline-form", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [
      CommonModule,
      ReactiveFormsModule,
      MatButtonModule,
      MatFormFieldModule,
      MatInputModule,
      MatProgressSpinnerModule
    ], animations: [
      trigger("expand", [
        transition(":enter", [
          style({ height: 0, opacity: 0, overflow: "hidden" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ height: "*", opacity: 1 }))
        ]),
        transition(":leave", [
          style({ overflow: "hidden" }),
          animate("150ms ease-in", style({ height: 0, opacity: 0 }))
        ])
      ])
    ], template: `
    <div class="hw-form" [@expand] role="form" [attr.aria-label]="ariaLabel()">
      <div class="hw-form__context">
        <i class="ph ph-book-open" aria-hidden="true"></i>
        <span class="hw-form__context-label">\u041F\u0440\u0435\u0434\u043C\u0435\u0442:</span>
        <span class="hw-form__context-value">{{ subjectName }}</span>
        <span class="hw-form__context-sep">\xB7</span>
        <span>{{ formattedDate() }}</span>
        <span class="hw-form__context-sep">\xB7</span>
        <span>\u041F\u0430\u0440\u0430 \u2116 {{ lessonNumber }}</span>
      </div>

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <mat-form-field appearance="outline" class="hw-form__field">
          <mat-label>\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F</mat-label>
          <input
            matInput
            formControlName="title"
            maxlength="255"
            placeholder="\u041D\u0430\u043F\u0440\u0438\u043C\u0435\u0440: \u0420\u0435\u0448\u0438\u0442\u044C \u211612-18 \u0438\u0437 \u0443\u0447\u0435\u0431\u043D\u0438\u043A\u0430"
            required />
          @if (form.get('title')?.hasError('required') && form.get('title')?.touched) {
            <mat-error>\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435 \u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E</mat-error>
          }
          @if (form.get('title')?.hasError('maxlength')) {
            <mat-error>\u041D\u0435 \u0431\u043E\u043B\u0435\u0435 255 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="hw-form__field">
          <mat-label>\u041E\u043F\u0438\u0441\u0430\u043D\u0438\u0435 (\u043D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E)</mat-label>
          <textarea
            matInput
            formControlName="description"
            rows="3"
            maxlength="4000"
            placeholder="\u0427\u0442\u043E \u0438\u043C\u0435\u043D\u043D\u043E \u043D\u0443\u0436\u043D\u043E \u0441\u0434\u0435\u043B\u0430\u0442\u044C, \u043A\u0430\u043A\u0438\u0435 \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u044B/\u0437\u0430\u0434\u0430\u0447\u0438 \u0438 \u0442.\u0434."></textarea>
          @if (form.get('description')?.hasError('maxlength')) {
            <mat-error>\u041D\u0435 \u0431\u043E\u043B\u0435\u0435 4000 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="hw-form__field">
          <mat-label>\u0421\u0441\u044B\u043B\u043A\u0430 \u043D\u0430 \u043C\u0430\u0442\u0435\u0440\u0438\u0430\u043B\u044B (\u043D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E)</mat-label>
          <input
            matInput
            formControlName="link"
            type="url"
            maxlength="2048"
            placeholder="https://..." />
          @if (form.get('link')?.hasError('maxlength')) {
            <mat-error>\u041D\u0435 \u0431\u043E\u043B\u0435\u0435 2048 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432</mat-error>
          }
        </mat-form-field>

        @if (apiError()) {
          <div class="hw-form__error" role="alert">
            <i class="ph ph-warning-circle" aria-hidden="true"></i>
            {{ apiError() }}
          </div>
        }

        <div class="hw-form__actions">
          <button
            type="button"
            mat-stroked-button
            (click)="cancel()"
            [disabled]="submitting()">
            \u041E\u0442\u043C\u0435\u043D\u0430
          </button>
          <button
            type="submit"
            mat-flat-button
            color="primary"
            class="btn-brand"
            [disabled]="form.invalid || submitting()">
            @if (submitting()) {
              <mat-spinner diameter="16" aria-hidden="true"></mat-spinner>
            }
            {{ isEdit() ? '\u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C' : '\u0421\u043E\u0437\u0434\u0430\u0442\u044C' }}
          </button>
        </div>
      </form>
    </div>
  `, styles: ["/* angular:styles/component:css;8e405d8c1fe5635dbf71008704c867a428234546a341224dc1ac14eee043f2f0;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/homework/homework-inline-form/homework-inline-form.component.ts */\n:host {\n  display: block;\n}\n.hw-form {\n  margin-top: 8px;\n  padding: var(--space-4, 16px);\n  background: var(--bg-elevated, #fff);\n  border: 1px solid var(--accent-secondary, #1976d2);\n  border-radius: var(--radius-lg, 12px);\n}\n.hw-form__context {\n  display: flex;\n  flex-wrap: wrap;\n  align-items: center;\n  gap: 6px;\n  font-size: 0.8rem;\n  color: var(--text-secondary, #555);\n  margin-bottom: 12px;\n  padding-bottom: 8px;\n  border-bottom: 1px dashed var(--border-subtle, #e0e0e0);\n}\n.hw-form__context-label {\n  font-weight: 500;\n}\n.hw-form__context-value {\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.hw-form__context-sep {\n  color: var(--text-muted, #999);\n}\n.hw-form__field {\n  width: 100%;\n  display: block;\n  margin-bottom: 4px;\n}\n.hw-form__error {\n  margin: var(--space-2) 0;\n  padding: var(--space-2) var(--space-3);\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 28%, transparent);\n  color: var(--accent-danger);\n  border-radius: var(--radius-md);\n  font-size: 0.8125rem;\n  display: inline-flex;\n  align-items: center;\n  gap: 6px;\n}\n.hw-form__actions {\n  display: flex;\n  gap: 8px;\n  justify-content: flex-end;\n  margin-top: 8px;\n}\n/*# sourceMappingURL=homework-inline-form.component.css.map */\n"] }]
  }], null, { subjectId: [{
    type: Input,
    args: [{ required: true }]
  }], subjectName: [{
    type: Input,
    args: [{ required: true }]
  }], lessonDate: [{
    type: Input,
    args: [{ required: true }]
  }], lessonNumber: [{
    type: Input,
    args: [{ required: true }]
  }], groupId: [{
    type: Input,
    args: [{ required: true }]
  }], semesterId: [{
    type: Input,
    args: [{ required: true }]
  }], initialHomework: [{
    type: Input
  }], saved: [{
    type: Output
  }], cancelled: [{
    type: Output
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HomeworkInlineFormComponent, { className: "HomeworkInlineFormComponent", filePath: "src/app/features/headman/homework/homework-inline-form/homework-inline-form.component.ts", lineNumber: 208 });
})();

// src/app/features/headman/homework/headman-homework.component.ts
var _forTrack0 = ($index, $item) => $item.date;
var _forTrack1 = ($index, $item) => $item.key;
var _forTrack2 = ($index, $item) => $item.id;
function HeadmanHomeworkComponent_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 6);
    \u0275\u0275element(1, "i", 9);
    \u0275\u0275elementStart(2, "h3");
    \u0275\u0275text(3, "\u041D\u0435\u0442 \u0430\u043A\u0442\u0438\u0432\u043D\u043E\u0433\u043E \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p");
    \u0275\u0275text(5, "\u041F\u043E\u043F\u0440\u043E\u0441\u0438\u0442\u0435 \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440\u0430 \u0430\u043A\u0442\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u0442\u0435\u043A\u0443\u0449\u0438\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440.");
    \u0275\u0275elementEnd()();
  }
}
function HeadmanHomeworkComponent_Conditional_11_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 7);
    \u0275\u0275element(1, "mat-spinner", 10);
    \u0275\u0275elementEnd();
  }
}
function HeadmanHomeworkComponent_Conditional_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 8);
    \u0275\u0275element(1, "i", 11);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r0.error(), " ");
  }
}
function HeadmanHomeworkComponent_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 6);
    \u0275\u0275element(1, "i", 9);
    \u0275\u0275elementStart(2, "h3");
    \u0275\u0275text(3, "\u041D\u0430 \u044D\u0442\u043E\u0439 \u043D\u0435\u0434\u0435\u043B\u0435 \u043F\u0430\u0440 \u043D\u0435\u0442");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p");
    \u0275\u0275text(5, "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0434\u0440\u0443\u0433\u0443\u044E \u043D\u0435\u0434\u0435\u043B\u044E \u0447\u0435\u0440\u0435\u0437 \u0441\u0442\u0440\u0435\u043B\u043A\u0438 \u0441\u0432\u0435\u0440\u0445\u0443.");
    \u0275\u0275elementEnd()();
  }
}
function HeadmanHomeworkComponent_Conditional_14_For_1_For_8_Conditional_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 22);
    \u0275\u0275text(1, "\u043E\u0442\u043C\u0435\u043D\u0435\u043D\u0430");
    \u0275\u0275elementEnd();
  }
}
function HeadmanHomeworkComponent_Conditional_14_For_1_For_8_For_12_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "app-homework-card", 27);
    \u0275\u0275pipe(1, "async");
    \u0275\u0275listener("edit", function HeadmanHomeworkComponent_Conditional_14_For_1_For_8_For_12_Template_app_homework_card_edit_0_listener($event) {
      \u0275\u0275restoreView(_r2);
      const row_r3 = \u0275\u0275nextContext().$implicit;
      const ctx_r0 = \u0275\u0275nextContext(3);
      return \u0275\u0275resetView(ctx_r0.onEditHomework(row_r3, $event));
    })("delete", function HeadmanHomeworkComponent_Conditional_14_For_1_For_8_For_12_Template_app_homework_card_delete_0_listener($event) {
      \u0275\u0275restoreView(_r2);
      const ctx_r0 = \u0275\u0275nextContext(4);
      return \u0275\u0275resetView(ctx_r0.onDeleteHomework($event));
    });
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    let tmp_32_0;
    const hw_r4 = ctx.$implicit;
    const row_r3 = \u0275\u0275nextContext().$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275property("homework", hw_r4)("subjectName", (tmp_32_0 = \u0275\u0275pipeBind1(1, 3, row_r3.subjectName$)) !== null && tmp_32_0 !== void 0 ? tmp_32_0 : null)("showEditActions", ctx_r0.isOwn(hw_r4));
  }
}
function HeadmanHomeworkComponent_Conditional_14_For_1_For_8_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    const _r5 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "app-homework-inline-form", 28);
    \u0275\u0275pipe(1, "async");
    \u0275\u0275listener("saved", function HeadmanHomeworkComponent_Conditional_14_For_1_For_8_Conditional_13_Template_app_homework_inline_form_saved_0_listener() {
      \u0275\u0275restoreView(_r5);
      const ctx_r0 = \u0275\u0275nextContext(4);
      return \u0275\u0275resetView(ctx_r0.onSaved());
    })("cancelled", function HeadmanHomeworkComponent_Conditional_14_For_1_For_8_Conditional_13_Template_app_homework_inline_form_cancelled_0_listener() {
      \u0275\u0275restoreView(_r5);
      const ctx_r0 = \u0275\u0275nextContext(4);
      return \u0275\u0275resetView(ctx_r0.onCancelled());
    });
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    let tmp_23_0;
    const row_r3 = \u0275\u0275nextContext().$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275property("subjectId", row_r3.lesson.subjectId)("subjectName", (tmp_23_0 = \u0275\u0275pipeBind1(1, 7, row_r3.subjectName$)) !== null && tmp_23_0 !== void 0 ? tmp_23_0 : "\u041F\u0440\u0435\u0434\u043C\u0435\u0442")("lessonDate", row_r3.lesson.date)("lessonNumber", row_r3.lesson.lessonNumber)("groupId", ctx_r0.groupId())("semesterId", ctx_r0.semesterId())("initialHomework", ctx_r0.editingHomework());
  }
}
function HeadmanHomeworkComponent_Conditional_14_For_1_For_8_Conditional_14_Template(rf, ctx) {
  if (rf & 1) {
    const _r6 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 29);
    \u0275\u0275pipe(1, "async");
    \u0275\u0275listener("click", function HeadmanHomeworkComponent_Conditional_14_For_1_For_8_Conditional_14_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r6);
      const row_r3 = \u0275\u0275nextContext().$implicit;
      const ctx_r0 = \u0275\u0275nextContext(3);
      return \u0275\u0275resetView(ctx_r0.onAddHomework(row_r3));
    });
    \u0275\u0275element(2, "i", 30);
    \u0275\u0275text(3, " \u0414\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u0435 ");
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    let tmp_22_0;
    const row_r3 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275attribute("aria-label", "\u0414\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u0435 \u043D\u0430 \u043F\u0430\u0440\u0443 \xAB" + ((tmp_22_0 = \u0275\u0275pipeBind1(1, 1, row_r3.subjectName$)) !== null && tmp_22_0 !== void 0 ? tmp_22_0 : "\u041F\u0440\u0435\u0434\u043C\u0435\u0442") + "\xBB");
  }
}
function HeadmanHomeworkComponent_Conditional_14_For_1_For_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "li", 17)(1, "div", 18)(2, "span", 19);
    \u0275\u0275text(3);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "span", 20);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "span", 21);
    \u0275\u0275text(7);
    \u0275\u0275pipe(8, "async");
    \u0275\u0275elementEnd();
    \u0275\u0275template(9, HeadmanHomeworkComponent_Conditional_14_For_1_For_8_Conditional_9_Template, 2, 0, "span", 22);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(10, "div", 23);
    \u0275\u0275repeaterCreate(11, HeadmanHomeworkComponent_Conditional_14_For_1_For_8_For_12_Template, 2, 5, "app-homework-card", 24, _forTrack2);
    \u0275\u0275elementEnd();
    \u0275\u0275template(13, HeadmanHomeworkComponent_Conditional_14_For_1_For_8_Conditional_13_Template, 2, 9, "app-homework-inline-form", 25)(14, HeadmanHomeworkComponent_Conditional_14_For_1_For_8_Conditional_14_Template, 4, 3, "button", 26);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    let tmp_23_0;
    const row_r3 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(3);
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate1("\u041F\u0430\u0440\u0430 \u2116 ", row_r3.lesson.lessonNumber, "");
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate2(" ", ctx_r0.shortTime(row_r3.lesson.startTime), "\u2013", ctx_r0.shortTime(row_r3.lesson.endTime), " ");
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", (tmp_23_0 = \u0275\u0275pipeBind1(8, 6, row_r3.subjectName$)) !== null && tmp_23_0 !== void 0 ? tmp_23_0 : "\u041F\u0440\u0435\u0434\u043C\u0435\u0442", " ");
    \u0275\u0275advance(2);
    \u0275\u0275conditional(ctx_r0.isLessonCancelled(row_r3.lesson) ? 9 : -1);
    \u0275\u0275advance(2);
    \u0275\u0275repeater(ctx_r0.homeworksFor(row_r3.key));
    \u0275\u0275advance(2);
    \u0275\u0275conditional(ctx_r0.isEditing(row_r3.key) ? 13 : !ctx_r0.isLessonCancelled(row_r3.lesson) ? 14 : -1);
  }
}
function HeadmanHomeworkComponent_Conditional_14_For_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "section", 12)(1, "header", 13)(2, "span", 14);
    \u0275\u0275text(3);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "span", 15);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(6, "ul", 16);
    \u0275\u0275repeaterCreate(7, HeadmanHomeworkComponent_Conditional_14_For_1_For_8_Template, 15, 8, "li", 17, _forTrack1);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const day_r7 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate(day_r7.label);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.formatDayDate(day_r7.date));
    \u0275\u0275advance(2);
    \u0275\u0275repeater(day_r7.lessons);
  }
}
function HeadmanHomeworkComponent_Conditional_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275repeaterCreate(0, HeadmanHomeworkComponent_Conditional_14_For_1_Template, 9, 2, "section", 12, _forTrack0);
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275repeater(ctx_r0.days());
  }
}
var DAY_NAMES = [
  "",
  // placeholder — backend dayOfWeek is 1-indexed
  "\u041F\u043E\u043D\u0435\u0434\u0435\u043B\u044C\u043D\u0438\u043A",
  "\u0412\u0442\u043E\u0440\u043D\u0438\u043A",
  "\u0421\u0440\u0435\u0434\u0430",
  "\u0427\u0435\u0442\u0432\u0435\u0440\u0433",
  "\u041F\u044F\u0442\u043D\u0438\u0446\u0430",
  "\u0421\u0443\u0431\u0431\u043E\u0442\u0430"
];
var MONTH_ABBREV = [
  "\u044F\u043D\u0432",
  "\u0444\u0435\u0432",
  "\u043C\u0430\u0440",
  "\u0430\u043F\u0440",
  "\u043C\u0430\u044F",
  "\u0438\u044E\u043D",
  "\u0438\u044E\u043B",
  "\u0430\u0432\u0433",
  "\u0441\u0435\u043D",
  "\u043E\u043A\u0442",
  "\u043D\u043E\u044F",
  "\u0434\u0435\u043A"
];
var HeadmanHomeworkComponent = class _HeadmanHomeworkComponent {
  auth = inject(AuthService);
  headmanApi = inject(HeadmanApiService);
  studentApi = inject(StudentApiService);
  homeworkApi = inject(HeadmanHomeworkApiService);
  subjectCache = inject(SubjectCacheService);
  destroyRef = inject(DestroyRef);
  dialog = inject(MatDialog);
  groupId = signal(null);
  currentUserId = signal(null);
  semesterId = signal(null);
  monday = signal(getMonday(/* @__PURE__ */ new Date()));
  lessons = signal([]);
  homeworks = signal([]);
  loading = signal(true);
  error = signal(null);
  /** Single-shot: only one inline form open at a time. */
  editingState = signal(null);
  editingHomework = computed(() => this.editingState()?.homework ?? null);
  days = computed(() => {
    const monday = this.monday();
    const lessons = this.lessons();
    const byDow = /* @__PURE__ */ new Map();
    for (const l of lessons) {
      if (l.dayOfWeek < 1 || l.dayOfWeek > 6)
        continue;
      const row = {
        lesson: l,
        key: `${l.date}-${l.lessonNumber}`,
        subjectName$: this.subjectCache.getName(l.subjectId)
      };
      const arr = byDow.get(l.dayOfWeek) ?? [];
      arr.push(row);
      byDow.set(l.dayOfWeek, arr);
    }
    const result = [];
    for (let dow = 1; dow <= 6; dow++) {
      const arr = byDow.get(dow);
      if (!arr || arr.length === 0)
        continue;
      const date = formatDate(addDays(monday, dow - 1));
      arr.sort((a, b) => a.lesson.lessonNumber - b.lesson.lessonNumber);
      result.push({
        date,
        dow,
        label: DAY_NAMES[dow],
        lessons: arr
      });
    }
    return result;
  });
  ngOnInit() {
    const user = this.auth.currentUser();
    if (!user?.groupId) {
      this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u043F\u0440\u0435\u0434\u0435\u043B\u0438\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443. \u041E\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044C \u043A \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440\u0443.");
      this.loading.set(false);
      return;
    }
    this.groupId.set(user.groupId);
    this.currentUserId.set(user.id);
    this.loadActiveSemester();
  }
  loadActiveSemester() {
    this.headmanApi.listSemesters().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (resp) => {
        const embedded = resp?._embedded;
        const list = embedded ? Object.values(embedded)[0] ?? [] : Array.isArray(resp) ? resp : [];
        const active = list.find((s) => s?.active === true);
        if (!active) {
          this.semesterId.set(null);
          this.loading.set(false);
          return;
        }
        this.semesterId.set(active.id);
        this.reload();
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440.");
        this.loading.set(false);
      }
    });
  }
  onWeekChanged(e) {
    this.monday.set(e.monday);
    this.editingState.set(null);
    if (this.semesterId() !== null) {
      this.reload();
    }
  }
  reload() {
    const g = this.groupId();
    const s = this.semesterId();
    if (!g || !s)
      return;
    this.loading.set(true);
    this.error.set(null);
    const monday = this.monday();
    const saturday = addDays(monday, 5);
    forkJoin({
      lessons: this.studentApi.getWeekLessons(g, formatDate(monday), formatDate(saturday)).pipe(catchError(() => of([]))),
      homeworks: this.homeworkApi.list(g, s).pipe(catchError(() => of([])))
    }).pipe(takeUntilDestroyed(this.destroyRef), map(({ lessons, homeworks }) => ({ lessons, homeworks }))).subscribe({
      next: ({ lessons, homeworks }) => {
        this.lessons.set(lessons);
        this.homeworks.set(homeworks);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0440\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435 \u0438 \u0437\u0430\u0434\u0430\u043D\u0438\u044F.");
        this.loading.set(false);
      }
    });
  }
  homeworksFor(lessonKey) {
    const [date, numStr] = this.splitLessonKey(lessonKey);
    const num = Number(numStr);
    return this.homeworks().filter((hw) => hw.lessonDate === date && hw.lessonNumber === num);
  }
  splitLessonKey(key) {
    const i = key.lastIndexOf("-");
    return [key.slice(0, i), key.slice(i + 1)];
  }
  isEditing(lessonKey) {
    return this.editingState()?.lessonKey === lessonKey;
  }
  isOwn(hw) {
    const uid = this.currentUserId();
    return uid !== null && hw.publishedBy === uid;
  }
  isLessonCancelled(l) {
    return (l.status ?? "").toString().toUpperCase() === "CANCELLED";
  }
  shortTime(t) {
    if (!t)
      return "";
    return t.length >= 5 ? t.slice(0, 5) : t;
  }
  formatDayDate(date) {
    const [y, m, d] = date.split("-");
    const mi = Math.max(0, Math.min(11, parseInt(m, 10) - 1));
    return `${parseInt(d, 10)} ${MONTH_ABBREV[mi]} ${y}`;
  }
  onAddHomework(row) {
    this.editingState.set({ lessonKey: row.key, homework: null });
  }
  onEditHomework(row, hw) {
    this.editingState.set({ lessonKey: row.key, homework: hw });
  }
  onDeleteHomework(hw) {
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: "\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u0435?",
        message: `\u0417\u0430\u0434\u0430\u043D\u0438\u0435 \xAB${hw.title}\xBB \u0431\u0443\u0434\u0435\u0442 \u0443\u0434\u0430\u043B\u0435\u043D\u043E \u0431\u0435\u0437 \u0432\u043E\u0437\u043C\u043E\u0436\u043D\u043E\u0441\u0442\u0438 \u0432\u043E\u0441\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u044F.`,
        confirmLabel: "\u0423\u0434\u0430\u043B\u0438\u0442\u044C",
        destructive: true
      },
      autoFocus: "first-tabbable"
    }).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((ok) => {
      if (!ok)
        return;
      this.homeworkApi.delete(hw.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => this.reload(),
        error: () => {
          this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0443\u0434\u0430\u043B\u0438\u0442\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u0435. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437.");
        }
      });
    });
  }
  onSaved() {
    this.editingState.set(null);
    this.reload();
  }
  onCancelled() {
    this.editingState.set(null);
  }
  static \u0275fac = function HeadmanHomeworkComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanHomeworkComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanHomeworkComponent, selectors: [["app-headman-homework"]], decls: 15, vars: 2, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-eyebrow"], [1, "page-title"], [1, "page-subtitle"], [3, "weekChanged"], [1, "page-empty"], [1, "page-card"], ["role", "alert", 1, "page-error"], ["aria-hidden", "true", 1, "ph-duotone", "ph-calendar-blank"], ["diameter", "32"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"], [1, "day-card"], [1, "day-card__header"], [1, "day-card__dow"], [1, "day-card__date"], ["role", "list", 1, "lesson-list"], [1, "lesson-block"], [1, "lesson-block__header"], [1, "lesson-block__num"], [1, "lesson-block__time"], [1, "lesson-block__subject"], [1, "lesson-block__cancelled"], ["role", "list", 1, "lesson-block__homeworks"], [3, "homework", "subjectName", "showEditActions"], [3, "subjectId", "subjectName", "lessonDate", "lessonNumber", "groupId", "semesterId", "initialHomework"], ["type", "button", 1, "lesson-block__add"], [3, "edit", "delete", "homework", "subjectName", "showEditActions"], [3, "saved", "cancelled", "subjectId", "subjectName", "lessonDate", "lessonNumber", "groupId", "semesterId", "initialHomework"], ["type", "button", 1, "lesson-block__add", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-plus"]], template: function HeadmanHomeworkComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "div")(3, "span", 2);
      \u0275\u0275text(4, "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "h1", 3);
      \u0275\u0275text(6, "\u0414\u043E\u043C\u0430\u0448\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(7, "p", 4);
      \u0275\u0275text(8, "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0430\u0440\u0443 \u0438 \u0434\u043E\u0431\u0430\u0432\u044C\u0442\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u0435.");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(9, "app-week-navigator", 5);
      \u0275\u0275listener("weekChanged", function HeadmanHomeworkComponent_Template_app_week_navigator_weekChanged_9_listener($event) {
        return ctx.onWeekChanged($event);
      });
      \u0275\u0275elementEnd();
      \u0275\u0275template(10, HeadmanHomeworkComponent_Conditional_10_Template, 6, 0, "div", 6)(11, HeadmanHomeworkComponent_Conditional_11_Template, 2, 0, "div", 7)(12, HeadmanHomeworkComponent_Conditional_12_Template, 3, 1, "div", 8)(13, HeadmanHomeworkComponent_Conditional_13_Template, 6, 0, "div", 6)(14, HeadmanHomeworkComponent_Conditional_14_Template, 2, 0);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(10);
      \u0275\u0275conditional(!ctx.semesterId() && !ctx.loading() ? 10 : ctx.loading() ? 11 : ctx.error() ? 12 : ctx.days().length === 0 ? 13 : 14);
    }
  }, dependencies: [
    CommonModule,
    AsyncPipe,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatProgressSpinner,
    WeekNavigatorComponent,
    HomeworkCardComponent,
    HomeworkInlineFormComponent
  ], styles: ["\n\n.page-stack[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-4, 16px);\n}\n.page-header__subtitle[_ngcontent-%COMP%], \n.page-subtitle[_ngcontent-%COMP%] {\n  margin: 4px 0 0;\n  font-size: 0.9rem;\n  color: var(--text-secondary, #555);\n}\n.day-card[_ngcontent-%COMP%] {\n  background: var(--bg-secondary, #fafafa);\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-radius: var(--radius-lg, 12px);\n  padding: var(--space-4, 16px);\n}\n.day-card__header[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: baseline;\n  gap: 12px;\n  margin-bottom: 12px;\n  padding-bottom: 8px;\n  border-bottom: 1px solid var(--border-subtle, #e0e0e0);\n}\n.day-card__dow[_ngcontent-%COMP%] {\n  font-weight: 600;\n  font-size: 1rem;\n  color: var(--text-primary);\n}\n.day-card__date[_ngcontent-%COMP%] {\n  font-size: 0.85rem;\n  color: var(--text-secondary, #666);\n}\n.lesson-list[_ngcontent-%COMP%] {\n  list-style: none;\n  padding: 0;\n  margin: 0;\n  display: flex;\n  flex-direction: column;\n  gap: 16px;\n}\n.lesson-block[_ngcontent-%COMP%] {\n  padding: 12px;\n  background: var(--bg-elevated, #fff);\n  border: 1px solid var(--border-subtle, #e8e8e8);\n  border-radius: var(--radius-md, 8px);\n}\n.lesson-block__header[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: 8px;\n  flex-wrap: wrap;\n  margin-bottom: 8px;\n  font-size: 0.85rem;\n}\n.lesson-block__num[_ngcontent-%COMP%] {\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.lesson-block__time[_ngcontent-%COMP%] {\n  font-family: var(--font-mono, monospace);\n  font-variant-numeric: tabular-nums;\n  color: var(--text-secondary, #555);\n}\n.lesson-block__subject[_ngcontent-%COMP%] {\n  margin-left: auto;\n  font-weight: 500;\n  color: var(--accent-secondary, #1976d2);\n}\n.lesson-block__cancelled[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  padding: 4px 10px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-danger) 14%, transparent);\n  color: var(--accent-danger);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 30%, transparent);\n  font: 500 0.6875rem/1 var(--font-mono);\n  letter-spacing: 0.04em;\n  text-transform: uppercase;\n}\n.lesson-block__homeworks[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 6px;\n}\n.lesson-block__homeworks[_ngcontent-%COMP%]:empty {\n  display: none;\n}\n.lesson-block__add[_ngcontent-%COMP%] {\n  margin-top: 8px;\n  width: 100%;\n  padding: 8px 12px;\n  border: 1px dashed var(--accent-secondary, #1976d2);\n  border-radius: var(--radius-md, 8px);\n  background: transparent;\n  color: var(--accent-secondary, #1976d2);\n  cursor: pointer;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  gap: 6px;\n  font-size: 0.9rem;\n  font-weight: 500;\n  transition: background 150ms ease;\n}\n.lesson-block__add[_ngcontent-%COMP%]:hover {\n  background: color-mix(in oklab, var(--accent-secondary, #1976d2) 8%, transparent);\n}\n.page-empty[_ngcontent-%COMP%] {\n  padding: 32px;\n  text-align: center;\n  color: var(--text-secondary, #555);\n}\n.page-empty[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 2.5rem;\n  color: var(--text-muted, #999);\n}\n.page-empty[_ngcontent-%COMP%]   h3[_ngcontent-%COMP%] {\n  margin: 12px 0 4px;\n}\n.page-error[_ngcontent-%COMP%] {\n  padding: var(--space-3) var(--space-4);\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 28%, transparent);\n  color: var(--accent-danger);\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  font-size: 0.875rem;\n}\n/*# sourceMappingURL=headman-homework.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanHomeworkComponent, [{
    type: Component,
    args: [{ selector: "app-headman-homework", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [
      CommonModule,
      AsyncPipe,
      MatButtonModule,
      MatProgressSpinnerModule,
      WeekNavigatorComponent,
      HomeworkCardComponent,
      HomeworkInlineFormComponent
    ], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: `
    <div class="page-stack" [@routeFade]>
      <div class="page-header">
        <div>
          <span class="page-eyebrow">\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430</span>
          <h1 class="page-title">\u0414\u043E\u043C\u0430\u0448\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F</h1>
          <p class="page-subtitle">\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0430\u0440\u0443 \u0438 \u0434\u043E\u0431\u0430\u0432\u044C\u0442\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u0435.</p>
        </div>
      </div>

      <app-week-navigator (weekChanged)="onWeekChanged($event)" />

      @if (!semesterId() && !loading()) {
        <div class="page-empty">
          <i class="ph-duotone ph-calendar-blank" aria-hidden="true"></i>
          <h3>\u041D\u0435\u0442 \u0430\u043A\u0442\u0438\u0432\u043D\u043E\u0433\u043E \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u0430</h3>
          <p>\u041F\u043E\u043F\u0440\u043E\u0441\u0438\u0442\u0435 \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440\u0430 \u0430\u043A\u0442\u0438\u0432\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u0442\u0435\u043A\u0443\u0449\u0438\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440.</p>
        </div>
      } @else if (loading()) {
        <div class="page-card"><mat-spinner diameter="32" /></div>
      } @else if (error()) {
        <div class="page-error" role="alert">
          <i class="ph ph-warning-circle" aria-hidden="true"></i>
          {{ error() }}
        </div>
      } @else if (days().length === 0) {
        <div class="page-empty">
          <i class="ph-duotone ph-calendar-blank" aria-hidden="true"></i>
          <h3>\u041D\u0430 \u044D\u0442\u043E\u0439 \u043D\u0435\u0434\u0435\u043B\u0435 \u043F\u0430\u0440 \u043D\u0435\u0442</h3>
          <p>\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0434\u0440\u0443\u0433\u0443\u044E \u043D\u0435\u0434\u0435\u043B\u044E \u0447\u0435\u0440\u0435\u0437 \u0441\u0442\u0440\u0435\u043B\u043A\u0438 \u0441\u0432\u0435\u0440\u0445\u0443.</p>
        </div>
      } @else {
        @for (day of days(); track day.date) {
          <section class="day-card">
            <header class="day-card__header">
              <span class="day-card__dow">{{ day.label }}</span>
              <span class="day-card__date">{{ formatDayDate(day.date) }}</span>
            </header>

            <ul class="lesson-list" role="list">
              @for (row of day.lessons; track row.key) {
                <li class="lesson-block">
                  <div class="lesson-block__header">
                    <span class="lesson-block__num">\u041F\u0430\u0440\u0430 \u2116 {{ row.lesson.lessonNumber }}</span>
                    <span class="lesson-block__time">
                      {{ shortTime(row.lesson.startTime) }}\u2013{{ shortTime(row.lesson.endTime) }}
                    </span>
                    <span class="lesson-block__subject">
                      {{ (row.subjectName$ | async) ?? '\u041F\u0440\u0435\u0434\u043C\u0435\u0442' }}
                    </span>
                    @if (isLessonCancelled(row.lesson)) {
                      <span class="lesson-block__cancelled">\u043E\u0442\u043C\u0435\u043D\u0435\u043D\u0430</span>
                    }
                  </div>

                  <div class="lesson-block__homeworks" role="list">
                    @for (hw of homeworksFor(row.key); track hw.id) {
                      <app-homework-card
                        [homework]="hw"
                        [subjectName]="(row.subjectName$ | async) ?? null"
                        [showEditActions]="isOwn(hw)"
                        (edit)="onEditHomework(row, $any($event))"
                        (delete)="onDeleteHomework($any($event))" />
                    }
                  </div>

                  @if (isEditing(row.key)) {
                    <app-homework-inline-form
                      [subjectId]="row.lesson.subjectId"
                      [subjectName]="(row.subjectName$ | async) ?? '\u041F\u0440\u0435\u0434\u043C\u0435\u0442'"
                      [lessonDate]="row.lesson.date"
                      [lessonNumber]="row.lesson.lessonNumber"
                      [groupId]="groupId()!"
                      [semesterId]="semesterId()!"
                      [initialHomework]="editingHomework()"
                      (saved)="onSaved()"
                      (cancelled)="onCancelled()" />
                  } @else if (!isLessonCancelled(row.lesson)) {
                    <button
                      type="button"
                      class="lesson-block__add"
                      (click)="onAddHomework(row)"
                      [attr.aria-label]="'\u0414\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u0435 \u043D\u0430 \u043F\u0430\u0440\u0443 \xAB' + ((row.subjectName$ | async) ?? '\u041F\u0440\u0435\u0434\u043C\u0435\u0442') + '\xBB'">
                      <i class="ph ph-plus" aria-hidden="true"></i>
                      \u0414\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u0437\u0430\u0434\u0430\u043D\u0438\u0435
                    </button>
                  }
                </li>
              }
            </ul>
          </section>
        }
      }
    </div>
  `, styles: ["/* angular:styles/component:css;2645842612245ad5ad8258b31fa237dc4b7b0c4c33d22832ccfeec3bb5103dbb;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/homework/headman-homework.component.ts */\n.page-stack {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-4, 16px);\n}\n.page-header__subtitle,\n.page-subtitle {\n  margin: 4px 0 0;\n  font-size: 0.9rem;\n  color: var(--text-secondary, #555);\n}\n.day-card {\n  background: var(--bg-secondary, #fafafa);\n  border: 1px solid var(--border-subtle, #e0e0e0);\n  border-radius: var(--radius-lg, 12px);\n  padding: var(--space-4, 16px);\n}\n.day-card__header {\n  display: flex;\n  align-items: baseline;\n  gap: 12px;\n  margin-bottom: 12px;\n  padding-bottom: 8px;\n  border-bottom: 1px solid var(--border-subtle, #e0e0e0);\n}\n.day-card__dow {\n  font-weight: 600;\n  font-size: 1rem;\n  color: var(--text-primary);\n}\n.day-card__date {\n  font-size: 0.85rem;\n  color: var(--text-secondary, #666);\n}\n.lesson-list {\n  list-style: none;\n  padding: 0;\n  margin: 0;\n  display: flex;\n  flex-direction: column;\n  gap: 16px;\n}\n.lesson-block {\n  padding: 12px;\n  background: var(--bg-elevated, #fff);\n  border: 1px solid var(--border-subtle, #e8e8e8);\n  border-radius: var(--radius-md, 8px);\n}\n.lesson-block__header {\n  display: flex;\n  align-items: center;\n  gap: 8px;\n  flex-wrap: wrap;\n  margin-bottom: 8px;\n  font-size: 0.85rem;\n}\n.lesson-block__num {\n  font-weight: 600;\n  color: var(--text-primary);\n}\n.lesson-block__time {\n  font-family: var(--font-mono, monospace);\n  font-variant-numeric: tabular-nums;\n  color: var(--text-secondary, #555);\n}\n.lesson-block__subject {\n  margin-left: auto;\n  font-weight: 500;\n  color: var(--accent-secondary, #1976d2);\n}\n.lesson-block__cancelled {\n  display: inline-flex;\n  align-items: center;\n  padding: 4px 10px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-danger) 14%, transparent);\n  color: var(--accent-danger);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 30%, transparent);\n  font: 500 0.6875rem/1 var(--font-mono);\n  letter-spacing: 0.04em;\n  text-transform: uppercase;\n}\n.lesson-block__homeworks {\n  display: flex;\n  flex-direction: column;\n  gap: 6px;\n}\n.lesson-block__homeworks:empty {\n  display: none;\n}\n.lesson-block__add {\n  margin-top: 8px;\n  width: 100%;\n  padding: 8px 12px;\n  border: 1px dashed var(--accent-secondary, #1976d2);\n  border-radius: var(--radius-md, 8px);\n  background: transparent;\n  color: var(--accent-secondary, #1976d2);\n  cursor: pointer;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  gap: 6px;\n  font-size: 0.9rem;\n  font-weight: 500;\n  transition: background 150ms ease;\n}\n.lesson-block__add:hover {\n  background: color-mix(in oklab, var(--accent-secondary, #1976d2) 8%, transparent);\n}\n.page-empty {\n  padding: 32px;\n  text-align: center;\n  color: var(--text-secondary, #555);\n}\n.page-empty i {\n  font-size: 2.5rem;\n  color: var(--text-muted, #999);\n}\n.page-empty h3 {\n  margin: 12px 0 4px;\n}\n.page-error {\n  padding: var(--space-3) var(--space-4);\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 28%, transparent);\n  color: var(--accent-danger);\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  font-size: 0.875rem;\n}\n/*# sourceMappingURL=headman-homework.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanHomeworkComponent, { className: "HeadmanHomeworkComponent", filePath: "src/app/features/headman/homework/headman-homework.component.ts", lineNumber: 324 });
})();
export {
  HeadmanHomeworkComponent
};
//# sourceMappingURL=chunk-WVOXSGBA.js.map
