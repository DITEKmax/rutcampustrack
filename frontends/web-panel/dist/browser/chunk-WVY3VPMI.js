import "./chunk-F5IUR55I.js";
import {
  MatSnackBar
} from "./chunk-5Q6RVIG4.js";
import {
  SubjectCacheService
} from "./chunk-PN7WKSHU.js";
import {
  MatCheckbox,
  MatCheckboxModule
} from "./chunk-ZIZW6QQV.js";
import {
  StudentApiService
} from "./chunk-EB6UCOGR.js";
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
  MatInput,
  MatInputModule
} from "./chunk-3O5U4Y7F.js";
import "./chunk-LPDGA6NZ.js";
import "./chunk-OIBNGD5S.js";
import {
  MatFormField,
  MatFormFieldModule,
  MatSelect,
  MatSelectModule
} from "./chunk-JQW43QIT.js";
import {
  MatButton,
  MatButtonModule
} from "./chunk-GTDHNJL7.js";
import {
  MatOption
} from "./chunk-676FZPAG.js";
import "./chunk-2WN7CIGJ.js";
import "./chunk-ZEX6QU7O.js";
import "./chunk-G4JX6AWT.js";
import "./chunk-YIJEORIR.js";
import "./chunk-FW2NJ6PM.js";
import {
  DefaultValueAccessor,
  FormBuilder,
  FormControlDirective,
  MaxLengthValidator,
  NgControlStatus,
  ReactiveFormsModule,
  Validators
} from "./chunk-HWHV5SCS.js";
import "./chunk-FLE4DLW4.js";
import {
  CommonModule,
  DatePipe,
  NgForOf
} from "./chunk-CLRYRPPS.js";
import {
  ChangeDetectionStrategy,
  Component,
  Inject,
  __spreadProps,
  __spreadValues,
  computed,
  inject,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵclassMap,
  ɵɵclassProp,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵdirectiveInject,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵgetCurrentView,
  ɵɵlistener,
  ɵɵnextContext,
  ɵɵpipe,
  ɵɵpipeBind2,
  ɵɵproperty,
  ɵɵpureFunction0,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵrepeaterTrackByIdentity,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-4M4FDLBS.js";

// src/app/features/student/shared/student-schedule.types.ts
var EXCUSE_TYPE_LABELS = {
  illness: "\u0411\u043E\u043B\u0435\u0437\u043D\u044C",
  summons: "\u041F\u043E\u0432\u0435\u0441\u0442\u043A\u0430",
  university_order: "\u041F\u0440\u0438\u043A\u0430\u0437 \u0443\u043D\u0438\u0432\u0435\u0440\u0441\u0438\u0442\u0435\u0442\u0430",
  exemption: "\u041E\u0441\u0432\u043E\u0431\u043E\u0436\u0434\u0435\u043D\u0438\u0435",
  free_attendance: "\u0421\u0432\u043E\u0431\u043E\u0434\u043D\u043E\u0435 \u043F\u043E\u0441\u0435\u0449\u0435\u043D\u0438\u0435",
  other: "\u0414\u0440\u0443\u0433\u043E\u0435"
};

// src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.ts
var _forTrack0 = ($index, $item) => $item.date;
var _forTrack1 = ($index, $item) => $item.lessonId;
function ExcuseFormDialogComponent_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 5);
    \u0275\u0275text(1, "\u041D\u0435\u0442 \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u043E\u0432 \u0441 \u043D\u0430\u0447\u0430\u043B\u0430 \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u0430.");
    \u0275\u0275elementEnd();
  }
}
function ExcuseFormDialogComponent_For_9_For_4_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "label", 19)(1, "mat-checkbox", 20);
    \u0275\u0275listener("change", function ExcuseFormDialogComponent_For_9_For_4_Template_mat_checkbox_change_1_listener() {
      const lesson_r2 = \u0275\u0275restoreView(_r1).$implicit;
      const ctx_r2 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r2.toggleLesson(lesson_r2.lessonId));
    });
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(2, "span", 21);
    \u0275\u0275text(3);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "span", 22);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "span", 23);
    \u0275\u0275text(7);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const lesson_r2 = ctx.$implicit;
    const day_r4 = \u0275\u0275nextContext().$implicit;
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275property("checked", ctx_r2.isSelected(lesson_r2.lessonId))("aria-label", day_r4.label + ", \u2116" + lesson_r2.lessonNumber + " " + ctx_r2.subjectName(lesson_r2.subjectId));
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1("\u2116", lesson_r2.lessonNumber, "");
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r2.subjectName(lesson_r2.subjectId));
    \u0275\u0275advance();
    \u0275\u0275classProp("lesson-row__mark--excused", lesson_r2.status === "excused");
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r2.statusSymbol(lesson_r2.status), " ");
  }
}
function ExcuseFormDialogComponent_For_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 6)(1, "div", 18);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275repeaterCreate(3, ExcuseFormDialogComponent_For_9_For_4_Template, 8, 7, "label", 19, _forTrack1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const day_r4 = ctx.$implicit;
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(day_r4.label);
    \u0275\u0275advance();
    \u0275\u0275repeater(day_r4.records);
  }
}
function ExcuseFormDialogComponent_For_16_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-option", 9);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const type_r5 = ctx.$implicit;
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275property("value", type_r5);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r2.excuseTypeLabels[type_r5]);
  }
}
function ExcuseFormDialogComponent_Conditional_24_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 12);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r2.validationError());
  }
}
function ExcuseFormDialogComponent_Conditional_25_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 13);
    \u0275\u0275element(1, "i", 24);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r2.submitError(), " ");
  }
}
function ExcuseFormDialogComponent_Conditional_30_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "span", 17);
  }
}
var DAY_NAMES = ["\u0412\u043E\u0441\u043A\u0440\u0435\u0441\u0435\u043D\u044C\u0435", "\u041F\u043E\u043D\u0435\u0434\u0435\u043B\u044C\u043D\u0438\u043A", "\u0412\u0442\u043E\u0440\u043D\u0438\u043A", "\u0421\u0440\u0435\u0434\u0430", "\u0427\u0435\u0442\u0432\u0435\u0440\u0433", "\u041F\u044F\u0442\u043D\u0438\u0446\u0430", "\u0421\u0443\u0431\u0431\u043E\u0442\u0430"];
var MONTH_NAMES = [
  "\u044F\u043D\u0432\u0430\u0440\u044F",
  "\u0444\u0435\u0432\u0440\u0430\u043B\u044F",
  "\u043C\u0430\u0440\u0442\u0430",
  "\u0430\u043F\u0440\u0435\u043B\u044F",
  "\u043C\u0430\u044F",
  "\u0438\u044E\u043D\u044F",
  "\u0438\u044E\u043B\u044F",
  "\u0430\u0432\u0433\u0443\u0441\u0442\u0430",
  "\u0441\u0435\u043D\u0442\u044F\u0431\u0440\u044F",
  "\u043E\u043A\u0442\u044F\u0431\u0440\u044F",
  "\u043D\u043E\u044F\u0431\u0440\u044F",
  "\u0434\u0435\u043A\u0430\u0431\u0440\u044F"
];
var ExcuseFormDialogComponent = class _ExcuseFormDialogComponent {
  apiService = inject(StudentApiService);
  subjectCache = inject(SubjectCacheService);
  snackBar = inject(MatSnackBar);
  fb = inject(FormBuilder);
  dialogRef = inject(MatDialogRef);
  lessons;
  /** Записи «н» (absent) и «у» (excused), отсортированные по дате от начала семестра. */
  missedRecords;
  /** Записи, сгруппированные по дню, отсортированные по возрастанию даты. */
  dayGroups = signal([]);
  /** Словарь subjectId → subject name (заполняется асинхронно). */
  subjectNames = signal({});
  selectedLessonIds = signal(/* @__PURE__ */ new Set());
  submitting = signal(false);
  submitError = signal(null);
  validationError = signal(null);
  excuseTypeLabels = EXCUSE_TYPE_LABELS;
  excuseTypes = Object.keys(EXCUSE_TYPE_LABELS);
  hasRecords = computed(() => this.dayGroups().length > 0);
  form;
  get excuseTypeControl() {
    return this.form.get("excuseType");
  }
  get commentControl() {
    return this.form.get("comment");
  }
  constructor(data) {
    this.lessons = data.lessons ?? [];
    this.missedRecords = this.lessons.filter((l) => l.status === "absent" || l.status === "excused").slice().sort((a, b) => a.lessonDate.localeCompare(b.lessonDate) || a.lessonNumber - b.lessonNumber);
    this.dayGroups.set(this.groupByDay(this.missedRecords));
    this.form = this.fb.group({
      excuseType: [null, [Validators.required]],
      comment: ["", [Validators.maxLength(1e3)]]
    });
  }
  ngOnInit() {
    const uniqueSubjectIds = Array.from(new Set(this.missedRecords.map((r) => r.subjectId)));
    uniqueSubjectIds.forEach((id) => {
      this.subjectCache.getName(id).subscribe((name) => {
        this.subjectNames.update((prev) => __spreadProps(__spreadValues({}, prev), { [id]: name }));
      });
    });
  }
  subjectName(subjectId) {
    return this.subjectNames()[subjectId] ?? "\u041F\u0440\u0435\u0434\u043C\u0435\u0442";
  }
  toggleLesson(lessonId) {
    this.selectedLessonIds.update((set) => {
      const next = new Set(set);
      if (next.has(lessonId))
        next.delete(lessonId);
      else
        next.add(lessonId);
      return next;
    });
    this.validationError.set(null);
  }
  isSelected(lessonId) {
    return this.selectedLessonIds().has(lessonId);
  }
  statusSymbol(status) {
    if (status === "absent")
      return "\u043D";
    if (status === "excused")
      return "\u0443";
    return "";
  }
  submit() {
    const ids = Array.from(this.selectedLessonIds());
    if (ids.length === 0) {
      this.validationError.set("\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0445\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u043E \u0437\u0430\u043D\u044F\u0442\u0438\u0435");
      return;
    }
    const excuseType = this.excuseTypeControl.value;
    if (!excuseType) {
      this.validationError.set("\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0438\u0447\u0438\u043D\u0443 \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0430");
      return;
    }
    this.submitting.set(true);
    this.submitError.set(null);
    const comment = this.commentControl.value?.trim() || null;
    this.apiService.submitExcuse(ids, excuseType, comment).subscribe({
      next: () => {
        this.submitting.set(false);
        this.dialogRef.close(true);
        this.snackBar.open("\u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D. \u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043D\u0438\u0435 \u043F\u0440\u0438\u0434\u0451\u0442 \u0432 Telegram.", "\u0417\u0430\u043A\u0440\u044B\u0442\u044C", { duration: 5e3, panelClass: ["snack-success"] });
      },
      error: () => {
        this.submitting.set(false);
        this.submitError.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043F\u043E\u0434\u0430\u0442\u044C \u0442\u0438\u043A\u0435\u0442. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437.");
      }
    });
  }
  cancel() {
    this.dialogRef.close(false);
  }
  groupByDay(records) {
    const map = /* @__PURE__ */ new Map();
    for (const r of records) {
      const arr = map.get(r.lessonDate) ?? [];
      arr.push(r);
      map.set(r.lessonDate, arr);
    }
    return Array.from(map.entries()).sort((a, b) => a[0].localeCompare(b[0])).map(([date, recs]) => ({
      date,
      label: this.dayLabel(date),
      records: recs.slice().sort((a, b) => a.lessonNumber - b.lessonNumber)
    }));
  }
  dayLabel(date) {
    const d = /* @__PURE__ */ new Date(date + "T00:00:00");
    const dow = d.getDay();
    return `${DAY_NAMES[dow]}, ${d.getDate()} ${MONTH_NAMES[d.getMonth()]}`;
  }
  static \u0275fac = function ExcuseFormDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _ExcuseFormDialogComponent)(\u0275\u0275directiveInject(MAT_DIALOG_DATA));
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _ExcuseFormDialogComponent, selectors: [["app-excuse-form-dialog"]], decls: 32, vars: 9, consts: [["mat-dialog-title", ""], [1, "dialog-body"], [1, "form-section"], [1, "form-section__label"], ["role", "list", "aria-label", "\u0421\u043F\u0438\u0441\u043E\u043A \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u043E\u0432 \u0441 \u043D\u0430\u0447\u0430\u043B\u0430 \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u0430", 1, "lesson-list"], [1, "form-hint"], [1, "lesson-day"], ["appearance", "outline", 1, "full-width"], ["placeholder", "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0438\u0447\u0438\u043D\u0443", "aria-label", "\u041F\u0440\u0438\u0447\u0438\u043D\u0430 \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0430", 3, "formControl"], [3, "value"], [1, "form-hint-inline"], ["matInput", "", "rows", "3", "maxlength", "1000", "placeholder", "\u0423\u043A\u0430\u0436\u0438\u0442\u0435 \u0434\u043E\u043F\u043E\u043B\u043D\u0438\u0442\u0435\u043B\u044C\u043D\u044B\u0435 \u043E\u0431\u0441\u0442\u043E\u044F\u0442\u0435\u043B\u044C\u0441\u0442\u0432\u0430", 3, "formControl"], ["role", "alert", 1, "form-error"], ["role", "alert", 1, "page-error"], ["align", "end"], ["mat-stroked-button", "", "type", "button", 3, "click", "disabled"], ["type", "button", 1, "btn-brand", 3, "click", "disabled"], [1, "btn-spinner"], [1, "lesson-day__heading"], ["role", "listitem", 1, "lesson-row"], [3, "change", "checked", "aria-label"], [1, "lesson-row__num"], [1, "lesson-row__subject"], [1, "lesson-row__mark"], [1, "ph", "ph-warning-circle"]], template: function ExcuseFormDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "h2", 0);
      \u0275\u0275text(1, "\u041D\u043E\u0432\u044B\u0439 \u0442\u0438\u043A\u0435\u0442 \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0435");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(2, "mat-dialog-content", 1)(3, "section", 2)(4, "h3", 3);
      \u0275\u0275text(5, "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043D\u044B\u0435 \u0437\u0430\u043D\u044F\u0442\u0438\u044F");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(6, "div", 4);
      \u0275\u0275template(7, ExcuseFormDialogComponent_Conditional_7_Template, 2, 0, "p", 5);
      \u0275\u0275repeaterCreate(8, ExcuseFormDialogComponent_For_9_Template, 5, 1, "div", 6, _forTrack0);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(10, "section", 2)(11, "h3", 3);
      \u0275\u0275text(12, "\u041F\u0440\u0438\u0447\u0438\u043D\u0430 \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(13, "mat-form-field", 7)(14, "mat-select", 8);
      \u0275\u0275repeaterCreate(15, ExcuseFormDialogComponent_For_16_Template, 2, 2, "mat-option", 9, \u0275\u0275repeaterTrackByIdentity);
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(17, "section", 2)(18, "h3", 3);
      \u0275\u0275text(19, "\u041A\u043E\u043C\u043C\u0435\u043D\u0442\u0430\u0440\u0438\u0439 ");
      \u0275\u0275elementStart(20, "span", 10);
      \u0275\u0275text(21, "(\u043D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E)");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(22, "mat-form-field", 7);
      \u0275\u0275element(23, "textarea", 11);
      \u0275\u0275elementEnd()();
      \u0275\u0275template(24, ExcuseFormDialogComponent_Conditional_24_Template, 2, 1, "p", 12)(25, ExcuseFormDialogComponent_Conditional_25_Template, 3, 1, "div", 13);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(26, "mat-dialog-actions", 14)(27, "button", 15);
      \u0275\u0275listener("click", function ExcuseFormDialogComponent_Template_button_click_27_listener() {
        return ctx.cancel();
      });
      \u0275\u0275text(28, " \u041D\u0435 \u043F\u043E\u0434\u0430\u0432\u0430\u0442\u044C ");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(29, "button", 16);
      \u0275\u0275listener("click", function ExcuseFormDialogComponent_Template_button_click_29_listener() {
        return ctx.submit();
      });
      \u0275\u0275template(30, ExcuseFormDialogComponent_Conditional_30_Template, 1, 0, "span", 17);
      \u0275\u0275text(31, " \u041F\u043E\u0434\u0430\u0442\u044C \u0442\u0438\u043A\u0435\u0442 ");
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275advance(7);
      \u0275\u0275conditional(!ctx.hasRecords() ? 7 : -1);
      \u0275\u0275advance();
      \u0275\u0275repeater(ctx.dayGroups());
      \u0275\u0275advance(6);
      \u0275\u0275property("formControl", ctx.excuseTypeControl);
      \u0275\u0275advance();
      \u0275\u0275repeater(ctx.excuseTypes);
      \u0275\u0275advance(8);
      \u0275\u0275property("formControl", ctx.commentControl);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.validationError() ? 24 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.submitError() ? 25 : -1);
      \u0275\u0275advance(2);
      \u0275\u0275property("disabled", ctx.submitting());
      \u0275\u0275advance(2);
      \u0275\u0275property("disabled", ctx.submitting());
      \u0275\u0275attribute("aria-busy", ctx.submitting());
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.submitting() ? 30 : -1);
    }
  }, dependencies: [
    CommonModule,
    ReactiveFormsModule,
    DefaultValueAccessor,
    NgControlStatus,
    MaxLengthValidator,
    FormControlDirective,
    MatDialogModule,
    MatDialogTitle,
    MatDialogActions,
    MatDialogContent,
    MatCheckboxModule,
    MatCheckbox,
    MatFormFieldModule,
    MatFormField,
    MatInputModule,
    MatInput,
    MatSelectModule,
    MatSelect,
    MatOption,
    MatButtonModule,
    MatButton
  ], styles: ["\n\n.dialog-body[_ngcontent-%COMP%] {\n  padding: var(--space-6) var(--space-6) 0;\n  overflow-y: auto;\n  max-height: calc(80vh - 120px);\n}\n.form-section[_ngcontent-%COMP%] {\n  margin-bottom: var(--space-5);\n}\n.form-section__label[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  font-weight: 600;\n  color: var(--text-primary);\n  margin-bottom: var(--space-2);\n}\n.form-hint-inline[_ngcontent-%COMP%] {\n  font-weight: 400;\n  color: var(--text-muted);\n}\n.form-hint[_ngcontent-%COMP%] {\n  color: var(--text-muted);\n  font-size: var(--text-sm);\n  padding: var(--space-4);\n  text-align: center;\n}\n.form-error[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  color: var(--accent-danger);\n  margin-top: var(--space-1);\n}\n.lesson-list[_ngcontent-%COMP%] {\n  background: var(--bg-surface);\n  border-radius: var(--radius-md);\n  max-height: 360px;\n  overflow-y: auto;\n  border: 1px solid var(--border-subtle);\n}\n.lesson-day[_ngcontent-%COMP%]    + .lesson-day[_ngcontent-%COMP%] {\n  border-top: 1px solid var(--border-subtle);\n}\n.lesson-day__heading[_ngcontent-%COMP%] {\n  padding: var(--space-2) var(--space-4);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  color: var(--text-muted);\n  background: var(--bg-elevated);\n  text-transform: none;\n  letter-spacing: 0;\n}\n.lesson-row[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-2) var(--space-4);\n  cursor: pointer;\n  border-bottom: 1px solid var(--border-subtle);\n}\n.lesson-row[_ngcontent-%COMP%]:last-child {\n  border-bottom: none;\n}\n.lesson-row__num[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  min-width: 28px;\n}\n.lesson-row__subject[_ngcontent-%COMP%] {\n  flex: 1;\n  font-size: var(--text-sm);\n  color: var(--text-primary);\n}\n.lesson-row__mark[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  font-weight: 600;\n  color: var(--accent-danger, #c62828);\n  min-width: 16px;\n  text-align: center;\n}\n.lesson-row__mark--excused[_ngcontent-%COMP%] {\n  color: var(--accent-warning, #c77700);\n}\n.drop-zone[_ngcontent-%COMP%] {\n  border: 2px dashed var(--border-default);\n  border-radius: var(--radius-md);\n  background: var(--bg-elevated);\n  min-height: 80px;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  justify-content: center;\n  gap: var(--space-1);\n  cursor: pointer;\n  transition: border-color 150ms ease, background 150ms ease;\n  padding: var(--space-4);\n}\n.drop-zone[_ngcontent-%COMP%]:hover, \n.drop-zone--active[_ngcontent-%COMP%] {\n  border-color: var(--accent-primary);\n  background: color-mix(in srgb, var(--accent-primary) 5%, var(--bg-elevated));\n}\n.drop-zone[_ngcontent-%COMP%]:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.drop-zone__icon[_ngcontent-%COMP%] {\n  font-size: 28px;\n  color: var(--text-muted);\n}\n.drop-zone__text[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n}\n.file-list[_ngcontent-%COMP%] {\n  list-style: none;\n  padding: 0;\n  margin: var(--space-2) 0 0;\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-1);\n}\n.file-chip[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-1);\n  padding: var(--space-1) var(--space-2);\n  background: var(--bg-surface);\n  border-radius: var(--radius-sm);\n  border: 1px solid var(--border-subtle);\n}\n.file-chip__icon[_ngcontent-%COMP%] {\n  font-size: 14px;\n  color: var(--text-muted);\n  flex-shrink: 0;\n}\n.file-chip__name[_ngcontent-%COMP%] {\n  flex: 1;\n  font-size: var(--text-xs);\n  color: var(--text-primary);\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n}\n.file-chip__size[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  flex-shrink: 0;\n}\n.file-chip__remove[_ngcontent-%COMP%] {\n  background: none;\n  border: none;\n  cursor: pointer;\n  color: var(--text-muted);\n  padding: 2px;\n  display: flex;\n  align-items: center;\n  border-radius: var(--radius-sm);\n}\n.file-chip__remove[_ngcontent-%COMP%]:hover {\n  color: var(--accent-danger);\n}\n.full-width[_ngcontent-%COMP%] {\n  width: 100%;\n}\n.btn-spinner[_ngcontent-%COMP%] {\n  display: inline-block;\n  width: 14px;\n  height: 14px;\n  border: 2px solid rgba(255, 255, 255, 0.4);\n  border-top-color: white;\n  border-radius: 50%;\n  animation: _ngcontent-%COMP%_spin 0.6s linear infinite;\n  margin-right: var(--space-1);\n}\n@keyframes _ngcontent-%COMP%_spin {\n  to {\n    transform: rotate(360deg);\n  }\n}\n/*# sourceMappingURL=excuse-form-dialog.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(ExcuseFormDialogComponent, [{
    type: Component,
    args: [{ selector: "app-excuse-form-dialog", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [
      CommonModule,
      ReactiveFormsModule,
      MatDialogModule,
      MatCheckboxModule,
      MatFormFieldModule,
      MatInputModule,
      MatSelectModule,
      MatButtonModule
    ], template: `<h2 mat-dialog-title>\u041D\u043E\u0432\u044B\u0439 \u0442\u0438\u043A\u0435\u0442 \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0435</h2>

<mat-dialog-content class="dialog-body">

  <!-- Lesson selection -->
  <section class="form-section">
    <h3 class="form-section__label">\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043D\u044B\u0435 \u0437\u0430\u043D\u044F\u0442\u0438\u044F</h3>
    <div class="lesson-list" role="list" aria-label="\u0421\u043F\u0438\u0441\u043E\u043A \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u043E\u0432 \u0441 \u043D\u0430\u0447\u0430\u043B\u0430 \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u0430">
      @if (!hasRecords()) {
        <p class="form-hint">\u041D\u0435\u0442 \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u043E\u0432 \u0441 \u043D\u0430\u0447\u0430\u043B\u0430 \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u0430.</p>
      }
      @for (day of dayGroups(); track day.date) {
        <div class="lesson-day">
          <div class="lesson-day__heading">{{ day.label }}</div>
          @for (lesson of day.records; track lesson.lessonId) {
            <label class="lesson-row" role="listitem">
              <mat-checkbox
                [checked]="isSelected(lesson.lessonId)"
                (change)="toggleLesson(lesson.lessonId)"
                [aria-label]="day.label + ', \u2116' + lesson.lessonNumber + ' ' + subjectName(lesson.subjectId)"
              />
              <span class="lesson-row__num">\u2116{{ lesson.lessonNumber }}</span>
              <span class="lesson-row__subject">{{ subjectName(lesson.subjectId) }}</span>
              <span class="lesson-row__mark" [class.lesson-row__mark--excused]="lesson.status === 'excused'">
                {{ statusSymbol(lesson.status) }}
              </span>
            </label>
          }
        </div>
      }
    </div>
  </section>

  <!-- Excuse type dropdown (D-21) -->
  <section class="form-section">
    <h3 class="form-section__label">\u041F\u0440\u0438\u0447\u0438\u043D\u0430 \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0430</h3>
    <mat-form-field appearance="outline" class="full-width">
      <mat-select
        [formControl]="excuseTypeControl"
        placeholder="\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0438\u0447\u0438\u043D\u0443"
        aria-label="\u041F\u0440\u0438\u0447\u0438\u043D\u0430 \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0430"
      >
        @for (type of excuseTypes; track type) {
          <mat-option [value]="type">{{ excuseTypeLabels[type] }}</mat-option>
        }
      </mat-select>
    </mat-form-field>
  </section>

  <!-- Comment -->
  <section class="form-section">
    <h3 class="form-section__label">\u041A\u043E\u043C\u043C\u0435\u043D\u0442\u0430\u0440\u0438\u0439 <span class="form-hint-inline">(\u043D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E)</span></h3>
    <mat-form-field appearance="outline" class="full-width">
      <textarea
        matInput
        [formControl]="commentControl"
        rows="3"
        maxlength="1000"
        placeholder="\u0423\u043A\u0430\u0436\u0438\u0442\u0435 \u0434\u043E\u043F\u043E\u043B\u043D\u0438\u0442\u0435\u043B\u044C\u043D\u044B\u0435 \u043E\u0431\u0441\u0442\u043E\u044F\u0442\u0435\u043B\u044C\u0441\u0442\u0432\u0430"
      ></textarea>
    </mat-form-field>
  </section>

  @if (validationError()) {
    <p class="form-error" role="alert">{{ validationError() }}</p>
  }

  @if (submitError()) {
    <div class="page-error" role="alert">
      <i class="ph ph-warning-circle"></i>
      {{ submitError() }}
    </div>
  }

</mat-dialog-content>

<mat-dialog-actions align="end">
  <button mat-stroked-button type="button" (click)="cancel()" [disabled]="submitting()">
    \u041D\u0435 \u043F\u043E\u0434\u0430\u0432\u0430\u0442\u044C
  </button>
  <button
    class="btn-brand"
    type="button"
    (click)="submit()"
    [disabled]="submitting()"
    [attr.aria-busy]="submitting()"
  >
    @if (submitting()) {
      <span class="btn-spinner"></span>
    }
    \u041F\u043E\u0434\u0430\u0442\u044C \u0442\u0438\u043A\u0435\u0442
  </button>
</mat-dialog-actions>
`, styles: ["/* src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.css */\n.dialog-body {\n  padding: var(--space-6) var(--space-6) 0;\n  overflow-y: auto;\n  max-height: calc(80vh - 120px);\n}\n.form-section {\n  margin-bottom: var(--space-5);\n}\n.form-section__label {\n  font-size: var(--text-sm);\n  font-weight: 600;\n  color: var(--text-primary);\n  margin-bottom: var(--space-2);\n}\n.form-hint-inline {\n  font-weight: 400;\n  color: var(--text-muted);\n}\n.form-hint {\n  color: var(--text-muted);\n  font-size: var(--text-sm);\n  padding: var(--space-4);\n  text-align: center;\n}\n.form-error {\n  font-size: var(--text-xs);\n  color: var(--accent-danger);\n  margin-top: var(--space-1);\n}\n.lesson-list {\n  background: var(--bg-surface);\n  border-radius: var(--radius-md);\n  max-height: 360px;\n  overflow-y: auto;\n  border: 1px solid var(--border-subtle);\n}\n.lesson-day + .lesson-day {\n  border-top: 1px solid var(--border-subtle);\n}\n.lesson-day__heading {\n  padding: var(--space-2) var(--space-4);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  color: var(--text-muted);\n  background: var(--bg-elevated);\n  text-transform: none;\n  letter-spacing: 0;\n}\n.lesson-row {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-2) var(--space-4);\n  cursor: pointer;\n  border-bottom: 1px solid var(--border-subtle);\n}\n.lesson-row:last-child {\n  border-bottom: none;\n}\n.lesson-row__num {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  min-width: 28px;\n}\n.lesson-row__subject {\n  flex: 1;\n  font-size: var(--text-sm);\n  color: var(--text-primary);\n}\n.lesson-row__mark {\n  font-size: var(--text-sm);\n  font-weight: 600;\n  color: var(--accent-danger, #c62828);\n  min-width: 16px;\n  text-align: center;\n}\n.lesson-row__mark--excused {\n  color: var(--accent-warning, #c77700);\n}\n.drop-zone {\n  border: 2px dashed var(--border-default);\n  border-radius: var(--radius-md);\n  background: var(--bg-elevated);\n  min-height: 80px;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  justify-content: center;\n  gap: var(--space-1);\n  cursor: pointer;\n  transition: border-color 150ms ease, background 150ms ease;\n  padding: var(--space-4);\n}\n.drop-zone:hover,\n.drop-zone--active {\n  border-color: var(--accent-primary);\n  background: color-mix(in srgb, var(--accent-primary) 5%, var(--bg-elevated));\n}\n.drop-zone:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.drop-zone__icon {\n  font-size: 28px;\n  color: var(--text-muted);\n}\n.drop-zone__text {\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n}\n.file-list {\n  list-style: none;\n  padding: 0;\n  margin: var(--space-2) 0 0;\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-1);\n}\n.file-chip {\n  display: flex;\n  align-items: center;\n  gap: var(--space-1);\n  padding: var(--space-1) var(--space-2);\n  background: var(--bg-surface);\n  border-radius: var(--radius-sm);\n  border: 1px solid var(--border-subtle);\n}\n.file-chip__icon {\n  font-size: 14px;\n  color: var(--text-muted);\n  flex-shrink: 0;\n}\n.file-chip__name {\n  flex: 1;\n  font-size: var(--text-xs);\n  color: var(--text-primary);\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n}\n.file-chip__size {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  flex-shrink: 0;\n}\n.file-chip__remove {\n  background: none;\n  border: none;\n  cursor: pointer;\n  color: var(--text-muted);\n  padding: 2px;\n  display: flex;\n  align-items: center;\n  border-radius: var(--radius-sm);\n}\n.file-chip__remove:hover {\n  color: var(--accent-danger);\n}\n.full-width {\n  width: 100%;\n}\n.btn-spinner {\n  display: inline-block;\n  width: 14px;\n  height: 14px;\n  border: 2px solid rgba(255, 255, 255, 0.4);\n  border-top-color: white;\n  border-radius: 50%;\n  animation: spin 0.6s linear infinite;\n  margin-right: var(--space-1);\n}\n@keyframes spin {\n  to {\n    transform: rotate(360deg);\n  }\n}\n/*# sourceMappingURL=excuse-form-dialog.component.css.map */\n"] }]
  }], () => [{ type: void 0, decorators: [{
    type: Inject,
    args: [MAT_DIALOG_DATA]
  }] }], null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(ExcuseFormDialogComponent, { className: "ExcuseFormDialogComponent", filePath: "src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.ts", lineNumber: 55 });
})();

// src/app/features/student/excuses/student-excuses.component.ts
var _c0 = () => [1, 2, 3];
var _forTrack02 = ($index, $item) => $item.id;
function StudentExcusesComponent_Conditional_11_div_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 11);
  }
}
function StudentExcusesComponent_Conditional_11_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 7);
    \u0275\u0275template(1, StudentExcusesComponent_Conditional_11_div_1_Template, 1, 0, "div", 10);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    \u0275\u0275advance();
    \u0275\u0275property("ngForOf", \u0275\u0275pureFunction0(1, _c0));
  }
}
function StudentExcusesComponent_Conditional_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 8);
    \u0275\u0275element(1, "i", 12);
    \u0275\u0275elementStart(2, "h2", 13);
    \u0275\u0275text(3, "\u041D\u0435\u0442 \u0442\u0438\u043A\u0435\u0442\u043E\u0432 \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0435");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p", 14);
    \u0275\u0275text(5, "\u041F\u043E\u0434\u0430\u0439\u0442\u0435 \u043F\u0435\u0440\u0432\u044B\u0439 \u0442\u0438\u043A\u0435\u0442, \u0435\u0441\u043B\u0438 \u043F\u0440\u043E\u043F\u0443\u0441\u0442\u0438\u043B\u0438 \u0437\u0430\u043D\u044F\u0442\u0438\u0435 \u043F\u043E \u0443\u0432\u0430\u0436\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0439 \u043F\u0440\u0438\u0447\u0438\u043D\u0435");
    \u0275\u0275elementEnd()();
  }
}
function StudentExcusesComponent_Conditional_13_For_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 18)(1, "span", 19);
    \u0275\u0275text(2);
    \u0275\u0275pipe(3, "date");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "span", 20);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "span", 21);
    \u0275\u0275text(7);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(8, "span", 22);
    \u0275\u0275text(9);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ticket_r1 = ctx.$implicit;
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(\u0275\u0275pipeBind2(3, 7, ticket_r1.createdAt, "dd.MM.yyyy"));
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate(ctx_r1.labelForExcuseType(ticket_r1.excuseType));
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ticket_r1.lessonIds.length);
    \u0275\u0275advance();
    \u0275\u0275classMap("status-chip--" + ticket_r1.status);
    \u0275\u0275attribute("aria-label", ctx_r1.statusLabels[ticket_r1.status]);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r1.statusLabels[ticket_r1.status]);
  }
}
function StudentExcusesComponent_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 9)(1, "div", 15)(2, "div", 16)(3, "span", 17);
    \u0275\u0275text(4, "\u0414\u0430\u0442\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "span", 17);
    \u0275\u0275text(6, "\u041F\u0440\u0438\u0447\u0438\u043D\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(7, "span", 17);
    \u0275\u0275text(8, "\u0417\u0430\u043D\u044F\u0442\u0438\u0439");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(9, "span", 17);
    \u0275\u0275text(10, "\u0421\u0442\u0430\u0442\u0443\u0441");
    \u0275\u0275elementEnd()();
    \u0275\u0275repeaterCreate(11, StudentExcusesComponent_Conditional_13_For_12_Template, 10, 10, "div", 18, _forTrack02);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance(11);
    \u0275\u0275repeater(ctx_r1.tickets());
  }
}
var StudentExcusesComponent = class _StudentExcusesComponent {
  apiService = inject(StudentApiService);
  dialog = inject(MatDialog);
  snackBar = inject(MatSnackBar);
  loading = signal(false);
  error = signal(null);
  tickets = signal([]);
  statusLabels = {
    draft: "\u0427\u0435\u0440\u043D\u043E\u0432\u0438\u043A",
    submitted: "\u041D\u0430 \u0440\u0430\u0441\u0441\u043C\u043E\u0442\u0440\u0435\u043D\u0438\u0438",
    approved: "\u041E\u0434\u043E\u0431\u0440\u0435\u043D\u043E",
    rejected: "\u041E\u0442\u043A\u043B\u043E\u043D\u0435\u043D\u043E"
  };
  excuseTypeLabels = EXCUSE_TYPE_LABELS;
  labelForExcuseType(type) {
    return this.excuseTypeLabels[type] ?? type;
  }
  ngOnInit() {
    this.loadTickets();
  }
  loadTickets() {
    this.loading.set(true);
    this.error.set(null);
    this.apiService.getExcuseTickets().subscribe({
      next: (tickets) => {
        this.tickets.set(tickets);
        this.loading.set(false);
      },
      error: () => {
        this.tickets.set([]);
        this.loading.set(false);
      }
    });
  }
  openExcuseForm() {
    this.apiService.getStudentRecords().subscribe({
      next: (records) => {
        const ref = this.dialog.open(ExcuseFormDialogComponent, {
          width: "560px",
          maxWidth: "100vw",
          maxHeight: "80vh",
          ariaLabel: "\u041D\u043E\u0432\u044B\u0439 \u0442\u0438\u043A\u0435\u0442 \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0435",
          data: { lessons: records }
        });
        ref.afterClosed().subscribe((submitted) => {
          if (submitted)
            this.loadTickets();
        });
      },
      error: () => {
        const ref = this.dialog.open(ExcuseFormDialogComponent, {
          width: "560px",
          maxWidth: "100vw",
          maxHeight: "80vh",
          ariaLabel: "\u041D\u043E\u0432\u044B\u0439 \u0442\u0438\u043A\u0435\u0442 \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0435",
          data: { lessons: [] }
        });
        ref.afterClosed().subscribe((submitted) => {
          if (submitted)
            this.loadTickets();
        });
      }
    });
  }
  static \u0275fac = function StudentExcusesComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentExcusesComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentExcusesComponent, selectors: [["app-student-excuses"]], decls: 14, vars: 2, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-header__eyebrow"], [1, "page-header__title"], [1, "page-header__actions"], [1, "btn-brand", 3, "click"], [1, "ph", "ph-plus"], [1, "page-card"], [1, "page-empty"], [1, "page-card", "page-card--flush"], ["class", "skeleton-row", 4, "ngFor", "ngForOf"], [1, "skeleton-row"], [1, "ph-duotone", "ph-file-text", "page-empty__icon"], [1, "page-empty__heading"], [1, "page-empty__body"], ["role", "table", "aria-label", "\u0422\u0438\u043A\u0435\u0442\u044B \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0430\u0445", 1, "excuse-table"], ["role", "row", 1, "excuse-table__header"], ["role", "columnheader"], ["role", "row", 1, "excuse-table__row"], ["role", "cell", 1, "excuse-date"], ["role", "cell", 1, "excuse-subjects"], ["role", "cell", 1, "excuse-count"], ["role", "cell", 1, "status-chip"]], template: function StudentExcusesComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "div")(3, "p", 2);
      \u0275\u0275text(4, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "h1", 3);
      \u0275\u0275text(6, "\u041F\u0440\u043E\u043F\u0443\u0441\u043A\u0438");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(7, "div", 4)(8, "button", 5);
      \u0275\u0275listener("click", function StudentExcusesComponent_Template_button_click_8_listener() {
        return ctx.openExcuseForm();
      });
      \u0275\u0275element(9, "i", 6);
      \u0275\u0275text(10, " \u041F\u043E\u0434\u0430\u0442\u044C \u0442\u0438\u043A\u0435\u0442 ");
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(11, StudentExcusesComponent_Conditional_11_Template, 2, 2, "div", 7)(12, StudentExcusesComponent_Conditional_12_Template, 6, 0, "div", 8)(13, StudentExcusesComponent_Conditional_13_Template, 13, 0, "div", 9);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(11);
      \u0275\u0275conditional(ctx.loading() ? 11 : ctx.tickets().length === 0 ? 12 : 13);
    }
  }, dependencies: [CommonModule, NgForOf, DatePipe], styles: ["\n\n.excuse-table[_ngcontent-%COMP%] {\n  width: 100%;\n}\n.excuse-table__header[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: 120px 1fr 80px 140px;\n  padding: var(--space-2) var(--space-4);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  color: var(--text-muted);\n  border-bottom: 1px solid var(--border-subtle);\n  text-transform: uppercase;\n  letter-spacing: 0.05em;\n}\n.excuse-table__row[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: 120px 1fr 80px 140px;\n  padding: var(--space-4);\n  min-height: 52px;\n  align-items: center;\n  border-bottom: 1px solid var(--border-subtle);\n  gap: var(--space-4);\n}\n.excuse-table__row[_ngcontent-%COMP%]:last-child {\n  border-bottom: none;\n}\n.excuse-date[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n}\n.excuse-subjects[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  color: var(--text-primary);\n}\n.status-chip--submitted[_ngcontent-%COMP%], \n.status-chip--pending[_ngcontent-%COMP%] {\n  background-color: color-mix(in srgb, var(--accent-warning) 15%, transparent);\n  color: var(--accent-warning);\n  border: 1px solid var(--accent-warning);\n}\n.status-chip--draft[_ngcontent-%COMP%] {\n  background-color: color-mix(in srgb, var(--text-muted) 15%, transparent);\n  color: var(--text-muted);\n  border: 1px solid var(--border-default);\n}\n.excuse-count[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  font-family: var(--font-mono);\n  color: var(--text-secondary);\n  text-align: center;\n}\n.status-chip--approved[_ngcontent-%COMP%] {\n  background-color: color-mix(in srgb, var(--accent-info) 15%, transparent);\n  color: var(--accent-info);\n  border: 1px solid var(--accent-info);\n}\n.status-chip--rejected[_ngcontent-%COMP%] {\n  background-color: color-mix(in srgb, var(--accent-danger) 15%, transparent);\n  color: var(--accent-danger);\n  border: 1px solid var(--accent-danger);\n}\n.status-chip--cancelled[_ngcontent-%COMP%] {\n  background-color: color-mix(in srgb, var(--text-muted) 15%, transparent);\n  color: var(--text-muted);\n  border: 1px solid var(--border-default);\n}\n.skeleton-row[_ngcontent-%COMP%] {\n  height: 52px;\n  border-radius: var(--radius-md);\n  background: var(--bg-elevated);\n  margin-bottom: var(--space-2);\n  animation: dashboard-shimmer 1.5s infinite;\n}\n@media (max-width: 600px) {\n  .excuse-table__header[_ngcontent-%COMP%], \n   .excuse-table__row[_ngcontent-%COMP%] {\n    grid-template-columns: 1fr 60px 140px;\n  }\n  .excuse-date[_ngcontent-%COMP%] {\n    display: none;\n  }\n}\n/*# sourceMappingURL=student-excuses.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentExcusesComponent, [{
    type: Component,
    args: [{ selector: "app-student-excuses", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule], animations: [
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
      <h1 class="page-header__title">\u041F\u0440\u043E\u043F\u0443\u0441\u043A\u0438</h1>
    </div>
    <div class="page-header__actions">
      <button class="btn-brand" (click)="openExcuseForm()">
        <i class="ph ph-plus"></i>
        \u041F\u043E\u0434\u0430\u0442\u044C \u0442\u0438\u043A\u0435\u0442
      </button>
    </div>
  </div>

  @if (loading()) {
    <div class="page-card">
      <div class="skeleton-row" *ngFor="let i of [1,2,3]"></div>
    </div>
  } @else if (tickets().length === 0) {
    <div class="page-empty">
      <i class="ph-duotone ph-file-text page-empty__icon"></i>
      <h2 class="page-empty__heading">\u041D\u0435\u0442 \u0442\u0438\u043A\u0435\u0442\u043E\u0432 \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0435</h2>
      <p class="page-empty__body">\u041F\u043E\u0434\u0430\u0439\u0442\u0435 \u043F\u0435\u0440\u0432\u044B\u0439 \u0442\u0438\u043A\u0435\u0442, \u0435\u0441\u043B\u0438 \u043F\u0440\u043E\u043F\u0443\u0441\u0442\u0438\u043B\u0438 \u0437\u0430\u043D\u044F\u0442\u0438\u0435 \u043F\u043E \u0443\u0432\u0430\u0436\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0439 \u043F\u0440\u0438\u0447\u0438\u043D\u0435</p>
    </div>
  } @else {
    <div class="page-card page-card--flush">
      <div class="excuse-table" role="table" aria-label="\u0422\u0438\u043A\u0435\u0442\u044B \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0430\u0445">
        <div class="excuse-table__header" role="row">
          <span role="columnheader">\u0414\u0430\u0442\u0430</span>
          <span role="columnheader">\u041F\u0440\u0438\u0447\u0438\u043D\u0430</span>
          <span role="columnheader">\u0417\u0430\u043D\u044F\u0442\u0438\u0439</span>
          <span role="columnheader">\u0421\u0442\u0430\u0442\u0443\u0441</span>
        </div>
        @for (ticket of tickets(); track ticket.id) {
          <div class="excuse-table__row" role="row">
            <span class="excuse-date" role="cell">{{ ticket.createdAt | date:'dd.MM.yyyy' }}</span>
            <span class="excuse-subjects" role="cell">{{ labelForExcuseType(ticket.excuseType) }}</span>
            <span class="excuse-count" role="cell">{{ ticket.lessonIds.length }}</span>
            <span
              class="status-chip"
              [class]="'status-chip--' + ticket.status"
              [attr.aria-label]="statusLabels[ticket.status]"
              role="cell"
            >{{ statusLabels[ticket.status] }}</span>
          </div>
        }
      </div>
    </div>
  }
</div>
`, styles: ["/* src/app/features/student/excuses/student-excuses.component.css */\n.excuse-table {\n  width: 100%;\n}\n.excuse-table__header {\n  display: grid;\n  grid-template-columns: 120px 1fr 80px 140px;\n  padding: var(--space-2) var(--space-4);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  color: var(--text-muted);\n  border-bottom: 1px solid var(--border-subtle);\n  text-transform: uppercase;\n  letter-spacing: 0.05em;\n}\n.excuse-table__row {\n  display: grid;\n  grid-template-columns: 120px 1fr 80px 140px;\n  padding: var(--space-4);\n  min-height: 52px;\n  align-items: center;\n  border-bottom: 1px solid var(--border-subtle);\n  gap: var(--space-4);\n}\n.excuse-table__row:last-child {\n  border-bottom: none;\n}\n.excuse-date {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n}\n.excuse-subjects {\n  font-size: var(--text-sm);\n  color: var(--text-primary);\n}\n.status-chip--submitted,\n.status-chip--pending {\n  background-color: color-mix(in srgb, var(--accent-warning) 15%, transparent);\n  color: var(--accent-warning);\n  border: 1px solid var(--accent-warning);\n}\n.status-chip--draft {\n  background-color: color-mix(in srgb, var(--text-muted) 15%, transparent);\n  color: var(--text-muted);\n  border: 1px solid var(--border-default);\n}\n.excuse-count {\n  font-size: var(--text-sm);\n  font-family: var(--font-mono);\n  color: var(--text-secondary);\n  text-align: center;\n}\n.status-chip--approved {\n  background-color: color-mix(in srgb, var(--accent-info) 15%, transparent);\n  color: var(--accent-info);\n  border: 1px solid var(--accent-info);\n}\n.status-chip--rejected {\n  background-color: color-mix(in srgb, var(--accent-danger) 15%, transparent);\n  color: var(--accent-danger);\n  border: 1px solid var(--accent-danger);\n}\n.status-chip--cancelled {\n  background-color: color-mix(in srgb, var(--text-muted) 15%, transparent);\n  color: var(--text-muted);\n  border: 1px solid var(--border-default);\n}\n.skeleton-row {\n  height: 52px;\n  border-radius: var(--radius-md);\n  background: var(--bg-elevated);\n  margin-bottom: var(--space-2);\n  animation: dashboard-shimmer 1.5s infinite;\n}\n@media (max-width: 600px) {\n  .excuse-table__header,\n  .excuse-table__row {\n    grid-template-columns: 1fr 60px 140px;\n  }\n  .excuse-date {\n    display: none;\n  }\n}\n/*# sourceMappingURL=student-excuses.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentExcusesComponent, { className: "StudentExcusesComponent", filePath: "src/app/features/student/excuses/student-excuses.component.ts", lineNumber: 29 });
})();
export {
  StudentExcusesComponent
};
//# sourceMappingURL=chunk-WVY3VPMI.js.map
