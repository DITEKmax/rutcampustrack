import {
  MatSnackBar
} from "./chunk-53YESMZZ.js";
import {
  SubjectCacheService
} from "./chunk-QY7VTECN.js";
import {
  MatCheckbox,
  MatCheckboxModule
} from "./chunk-MQ7WFICG.js";
import {
  StudentApiService
} from "./chunk-ESVH3VU5.js";
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogContent,
  MatDialogModule,
  MatDialogRef,
  MatDialogTitle
} from "./chunk-M24SPP55.js";
import {
  MatInput,
  MatInputModule
} from "./chunk-WQN3TC62.js";
import {
  MatFormField,
  MatFormFieldModule,
  MatSelect,
  MatSelectModule
} from "./chunk-Z3VV4J2X.js";
import {
  MatButton,
  MatButtonModule
} from "./chunk-PAS4QIDL.js";
import {
  MatOption
} from "./chunk-4RJPSRCU.js";
import {
  DefaultValueAccessor,
  FormBuilder,
  FormControlDirective,
  MaxLengthValidator,
  NgControlStatus,
  ReactiveFormsModule,
  Validators
} from "./chunk-XAMIXVT7.js";
import {
  CommonModule
} from "./chunk-M5DKW26A.js";
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
  ɵɵproperty,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵrepeaterTrackByIdentity,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-MFRIGFR2.js";

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
function ExcuseFormDialogComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 4);
    \u0275\u0275text(1, " \u0422\u0438\u043A\u0435\u0442 \u0431\u0443\u0434\u0435\u0442 \u043F\u043E\u0434\u0430\u043D \u0442\u043E\u043B\u044C\u043A\u043E \u043F\u043E \u044D\u0442\u043E\u0439 \u043F\u0430\u0440\u0435. \u0414\u043B\u044F \u0434\u0440\u0443\u0433\u0438\u0445 \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u043E\u0432 \u043E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 \u0440\u0430\u0437\u0434\u0435\u043B \xAB\u0423\u0432\u0430\u0436\u0438\u0442\u0435\u043B\u044C\u043D\u044B\u0435 \u043F\u0440\u0438\u0447\u0438\u043D\u044B\xBB. ");
    \u0275\u0275elementEnd();
  }
}
function ExcuseFormDialogComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 4);
    \u0275\u0275text(1, "\u041D\u0435\u0442 \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u043E\u0432 \u0441 \u043D\u0430\u0447\u0430\u043B\u0430 \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u0430.");
    \u0275\u0275elementEnd();
  }
}
function ExcuseFormDialogComponent_For_10_For_4_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "label", 26)(1, "mat-checkbox", 27);
    \u0275\u0275listener("change", function ExcuseFormDialogComponent_For_10_For_4_Template_mat_checkbox_change_1_listener() {
      const lesson_r2 = \u0275\u0275restoreView(_r1).$implicit;
      const ctx_r2 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r2.toggleLesson(lesson_r2.lessonId));
    });
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(2, "span", 28);
    \u0275\u0275text(3);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "span", 29);
    \u0275\u0275text(5);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "span", 30);
    \u0275\u0275text(7);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const lesson_r2 = ctx.$implicit;
    const day_r4 = \u0275\u0275nextContext().$implicit;
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275classProp("lesson-row--disabled", ctx_r2.isDisabled(lesson_r2.lessonId));
    \u0275\u0275advance();
    \u0275\u0275property("checked", ctx_r2.isSelected(lesson_r2.lessonId))("disabled", ctx_r2.isDisabled(lesson_r2.lessonId))("aria-label", day_r4.label + ", \u2116" + lesson_r2.lessonNumber + " " + ctx_r2.subjectName(lesson_r2.subjectId));
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
function ExcuseFormDialogComponent_For_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 6)(1, "div", 24);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275repeaterCreate(3, ExcuseFormDialogComponent_For_10_For_4_Template, 8, 10, "label", 25, _forTrack1);
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
function ExcuseFormDialogComponent_For_17_Template(rf, ctx) {
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
function ExcuseFormDialogComponent_Conditional_38_Template(rf, ctx) {
  if (rf & 1) {
    const _r6 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "span", 17)(1, "span", 31);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "button", 32);
    \u0275\u0275listener("click", function ExcuseFormDialogComponent_Conditional_38_Template_button_click_3_listener() {
      \u0275\u0275restoreView(_r6);
      const ctx_r2 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r2.clearFile());
    });
    \u0275\u0275element(4, "i", 33);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx.name);
  }
}
function ExcuseFormDialogComponent_Conditional_39_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 18);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r2.fileError());
  }
}
function ExcuseFormDialogComponent_Conditional_40_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 18);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r2.validationError());
  }
}
function ExcuseFormDialogComponent_Conditional_41_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 19);
    \u0275\u0275element(1, "i", 34);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r2.submitError(), " ");
  }
}
function ExcuseFormDialogComponent_Conditional_46_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "span", 23);
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
  /**
   * When the dialog is opened from a specific lesson card, the submission is
   * locked to that single lesson — other checkboxes are disabled so students
   * cannot piggy-back unrelated absences onto a per-lesson request.
   */
  lockedToLessonIds;
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
    if (data.preselectedLessonIds?.length) {
      this.selectedLessonIds.set(new Set(data.preselectedLessonIds));
      this.lockedToLessonIds = new Set(data.preselectedLessonIds);
    } else {
      this.lockedToLessonIds = /* @__PURE__ */ new Set();
    }
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
    if (this.isLocked())
      return;
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
  /** True when the dialog was opened with a pre-selected lesson — selection is frozen. */
  isLocked() {
    return this.lockedToLessonIds.size > 0;
  }
  /** Disable a checkbox row if the selection is locked and this lesson isn't the locked one. */
  isDisabled(lessonId) {
    return this.isLocked() && !this.lockedToLessonIds.has(lessonId);
  }
  attachedFile = signal(null);
  fileError = signal(null);
  onFileSelected(event) {
    const input = event.target;
    const file = input.files && input.files.length > 0 ? input.files[0] : null;
    this.fileError.set(null);
    if (!file) {
      this.attachedFile.set(null);
      return;
    }
    const MAX_BYTES = 10 * 1024 * 1024;
    if (file.size > MAX_BYTES) {
      this.fileError.set("\u0424\u0430\u0439\u043B \u0434\u043E\u043B\u0436\u0435\u043D \u0431\u044B\u0442\u044C \u043D\u0435 \u0431\u043E\u043B\u044C\u0448\u0435 10 \u041C\u0411.");
      input.value = "";
      this.attachedFile.set(null);
      return;
    }
    this.attachedFile.set(file);
  }
  clearFile() {
    this.attachedFile.set(null);
    this.fileError.set(null);
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
    const file = this.attachedFile();
    const submit$ = file ? this.apiService.submitExcuseWithFile(ids, excuseType, comment, file) : this.apiService.submitExcuse(ids, excuseType, comment);
    submit$.subscribe({
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
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _ExcuseFormDialogComponent, selectors: [["app-excuse-form-dialog"]], decls: 48, vars: 14, consts: [["mat-dialog-title", ""], [1, "dialog-body"], [1, "form-section"], [1, "form-section__label"], [1, "form-hint"], ["role", "list", "aria-label", "\u0421\u043F\u0438\u0441\u043E\u043A \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u043E\u0432 \u0441 \u043D\u0430\u0447\u0430\u043B\u0430 \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u0430", 1, "lesson-list"], [1, "lesson-day"], ["appearance", "outline", 1, "full-width"], ["placeholder", "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0438\u0447\u0438\u043D\u0443", "aria-label", "\u041F\u0440\u0438\u0447\u0438\u043D\u0430 \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0430", 3, "formControl"], [3, "value"], [1, "form-hint-inline"], ["matInput", "", "rows", "3", "maxlength", "1000", "placeholder", "\u0423\u043A\u0430\u0436\u0438\u0442\u0435 \u0434\u043E\u043F\u043E\u043B\u043D\u0438\u0442\u0435\u043B\u044C\u043D\u044B\u0435 \u043E\u0431\u0441\u0442\u043E\u044F\u0442\u0435\u043B\u044C\u0441\u0442\u0432\u0430", 3, "formControl"], [1, "file-row"], [1, "file-row__picker"], ["type", "file", "aria-label", "\u041F\u0440\u0438\u043A\u0440\u0435\u043F\u0438\u0442\u044C \u0444\u0430\u0439\u043B", 3, "change"], [1, "file-row__picker-label"], ["aria-hidden", "true", 1, "ph", "ph-paperclip"], [1, "file-row__meta"], ["role", "alert", 1, "form-error"], ["role", "alert", 1, "page-error"], ["align", "end"], ["mat-stroked-button", "", "type", "button", 3, "click", "disabled"], ["type", "button", 1, "btn-brand", 3, "click", "disabled"], [1, "btn-spinner"], [1, "lesson-day__heading"], ["role", "listitem", 1, "lesson-row", 3, "lesson-row--disabled"], ["role", "listitem", 1, "lesson-row"], [3, "change", "checked", "disabled", "aria-label"], [1, "lesson-row__num"], [1, "lesson-row__subject"], [1, "lesson-row__mark"], [1, "file-row__name"], ["type", "button", "aria-label", "\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u0444\u0430\u0439\u043B", 1, "file-row__clear", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-x"], [1, "ph", "ph-warning-circle"]], template: function ExcuseFormDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "h2", 0);
      \u0275\u0275text(1, "\u041D\u043E\u0432\u044B\u0439 \u0442\u0438\u043A\u0435\u0442 \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0435");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(2, "mat-dialog-content", 1)(3, "section", 2)(4, "h3", 3);
      \u0275\u0275text(5);
      \u0275\u0275elementEnd();
      \u0275\u0275template(6, ExcuseFormDialogComponent_Conditional_6_Template, 2, 0, "p", 4);
      \u0275\u0275elementStart(7, "div", 5);
      \u0275\u0275template(8, ExcuseFormDialogComponent_Conditional_8_Template, 2, 0, "p", 4);
      \u0275\u0275repeaterCreate(9, ExcuseFormDialogComponent_For_10_Template, 5, 1, "div", 6, _forTrack0);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(11, "section", 2)(12, "h3", 3);
      \u0275\u0275text(13, "\u041F\u0440\u0438\u0447\u0438\u043D\u0430 \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(14, "mat-form-field", 7)(15, "mat-select", 8);
      \u0275\u0275repeaterCreate(16, ExcuseFormDialogComponent_For_17_Template, 2, 2, "mat-option", 9, \u0275\u0275repeaterTrackByIdentity);
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(18, "section", 2)(19, "h3", 3);
      \u0275\u0275text(20, "\u041A\u043E\u043C\u043C\u0435\u043D\u0442\u0430\u0440\u0438\u0439 ");
      \u0275\u0275elementStart(21, "span", 10);
      \u0275\u0275text(22, "(\u043D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E)");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(23, "mat-form-field", 7);
      \u0275\u0275element(24, "textarea", 11);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(25, "section", 2)(26, "h3", 3);
      \u0275\u0275text(27, "\u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0430\u044E\u0449\u0438\u0439 \u0434\u043E\u043A\u0443\u043C\u0435\u043D\u0442 ");
      \u0275\u0275elementStart(28, "span", 10);
      \u0275\u0275text(29, "(\u043D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E, \u0434\u043E 10 \u041C\u0411)");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(30, "p", 4);
      \u0275\u0275text(31, " \u0424\u0430\u0439\u043B \u0431\u0443\u0434\u0435\u0442 \u043F\u0435\u0440\u0435\u0441\u043B\u0430\u043D \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0435 \u0432 Telegram. \u041D\u0430 \u0441\u0435\u0440\u0432\u0435\u0440\u0435 \u043E\u043D \u043D\u0435 \u0445\u0440\u0430\u043D\u0438\u0442\u0441\u044F. ");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(32, "div", 12)(33, "label", 13)(34, "input", 14);
      \u0275\u0275listener("change", function ExcuseFormDialogComponent_Template_input_change_34_listener($event) {
        return ctx.onFileSelected($event);
      });
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(35, "span", 15);
      \u0275\u0275element(36, "i", 16);
      \u0275\u0275text(37);
      \u0275\u0275elementEnd()();
      \u0275\u0275template(38, ExcuseFormDialogComponent_Conditional_38_Template, 5, 1, "span", 17);
      \u0275\u0275elementEnd();
      \u0275\u0275template(39, ExcuseFormDialogComponent_Conditional_39_Template, 2, 1, "p", 18);
      \u0275\u0275elementEnd();
      \u0275\u0275template(40, ExcuseFormDialogComponent_Conditional_40_Template, 2, 1, "p", 18)(41, ExcuseFormDialogComponent_Conditional_41_Template, 3, 1, "div", 19);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(42, "mat-dialog-actions", 20)(43, "button", 21);
      \u0275\u0275listener("click", function ExcuseFormDialogComponent_Template_button_click_43_listener() {
        return ctx.cancel();
      });
      \u0275\u0275text(44, " \u041D\u0435 \u043F\u043E\u0434\u0430\u0432\u0430\u0442\u044C ");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(45, "button", 22);
      \u0275\u0275listener("click", function ExcuseFormDialogComponent_Template_button_click_45_listener() {
        return ctx.submit();
      });
      \u0275\u0275template(46, ExcuseFormDialogComponent_Conditional_46_Template, 1, 0, "span", 23);
      \u0275\u0275text(47, " \u041F\u043E\u0434\u0430\u0442\u044C \u0442\u0438\u043A\u0435\u0442 ");
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      let tmp_8_0;
      \u0275\u0275advance(5);
      \u0275\u0275textInterpolate1(" ", ctx.isLocked() ? "\u0417\u0430\u044F\u0432\u043A\u0430 \u043F\u043E \u0432\u044B\u0431\u0440\u0430\u043D\u043D\u043E\u0439 \u043F\u0430\u0440\u0435" : "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043D\u044B\u0435 \u0437\u0430\u043D\u044F\u0442\u0438\u044F", " ");
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.isLocked() ? 6 : -1);
      \u0275\u0275advance(2);
      \u0275\u0275conditional(!ctx.hasRecords() ? 8 : -1);
      \u0275\u0275advance();
      \u0275\u0275repeater(ctx.dayGroups());
      \u0275\u0275advance(6);
      \u0275\u0275property("formControl", ctx.excuseTypeControl);
      \u0275\u0275advance();
      \u0275\u0275repeater(ctx.excuseTypes);
      \u0275\u0275advance(8);
      \u0275\u0275property("formControl", ctx.commentControl);
      \u0275\u0275advance(13);
      \u0275\u0275textInterpolate1(" ", ctx.attachedFile() ? "\u0417\u0430\u043C\u0435\u043D\u0438\u0442\u044C \u0444\u0430\u0439\u043B" : "\u041F\u0440\u0438\u043A\u0440\u0435\u043F\u0438\u0442\u044C \u0444\u0430\u0439\u043B", " ");
      \u0275\u0275advance();
      \u0275\u0275conditional((tmp_8_0 = ctx.attachedFile()) ? 38 : -1, tmp_8_0);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.fileError() ? 39 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.validationError() ? 40 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.submitError() ? 41 : -1);
      \u0275\u0275advance(2);
      \u0275\u0275property("disabled", ctx.submitting());
      \u0275\u0275advance(2);
      \u0275\u0275property("disabled", ctx.submitting());
      \u0275\u0275attribute("aria-busy", ctx.submitting());
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.submitting() ? 46 : -1);
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
  ], styles: ["\n\n.dialog-body[_ngcontent-%COMP%] {\n  padding: var(--space-6) var(--space-6) 0;\n  overflow-y: auto;\n  max-height: calc(80vh - 120px);\n}\n.form-section[_ngcontent-%COMP%] {\n  margin-bottom: var(--space-5);\n}\n.form-section__label[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  font-weight: 600;\n  color: var(--text-primary);\n  margin-bottom: var(--space-2);\n}\n.form-hint-inline[_ngcontent-%COMP%] {\n  font-weight: 400;\n  color: var(--text-muted);\n}\n.form-hint[_ngcontent-%COMP%] {\n  color: var(--text-muted);\n  font-size: var(--text-sm);\n  padding: var(--space-4);\n  text-align: center;\n}\n.form-error[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  color: var(--accent-danger);\n  margin-top: var(--space-1);\n}\n.lesson-list[_ngcontent-%COMP%] {\n  background: var(--bg-surface);\n  border-radius: var(--radius-md);\n  max-height: 360px;\n  overflow-y: auto;\n  border: 1px solid var(--border-subtle);\n}\n.lesson-day[_ngcontent-%COMP%]    + .lesson-day[_ngcontent-%COMP%] {\n  border-top: 1px solid var(--border-subtle);\n}\n.lesson-day__heading[_ngcontent-%COMP%] {\n  padding: var(--space-2) var(--space-4);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  color: var(--text-muted);\n  background: var(--bg-elevated);\n  text-transform: none;\n  letter-spacing: 0;\n}\n.lesson-row[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-2) var(--space-4);\n  cursor: pointer;\n  border-bottom: 1px solid var(--border-subtle);\n}\n.lesson-row[_ngcontent-%COMP%]:last-child {\n  border-bottom: none;\n}\n.lesson-row__num[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  min-width: 28px;\n}\n.lesson-row__subject[_ngcontent-%COMP%] {\n  flex: 1;\n  font-size: var(--text-sm);\n  color: var(--text-primary);\n}\n.lesson-row__mark[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  font-weight: 600;\n  color: var(--accent-danger, #c62828);\n  min-width: 16px;\n  text-align: center;\n}\n.lesson-row__mark--excused[_ngcontent-%COMP%] {\n  color: var(--accent-warning, #c77700);\n}\n.lesson-row--disabled[_ngcontent-%COMP%] {\n  cursor: not-allowed;\n  opacity: 0.5;\n}\n.file-row[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  flex-wrap: wrap;\n  margin-top: var(--space-2);\n}\n.file-row__picker[_ngcontent-%COMP%] {\n  position: relative;\n  display: inline-flex;\n  cursor: pointer;\n}\n.file-row__picker[_ngcontent-%COMP%]   input[type=file][_ngcontent-%COMP%] {\n  position: absolute;\n  inset: 0;\n  opacity: 0;\n  cursor: pointer;\n}\n.file-row__picker-label[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  padding: var(--space-2) var(--space-4);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-pill, 9999px);\n  background: var(--bg-surface);\n  color: var(--text-primary);\n  font-size: var(--text-sm);\n  font-weight: 500;\n  transition: border-color 150ms ease, background 150ms ease;\n}\n.file-row__picker[_ngcontent-%COMP%]:hover   .file-row__picker-label[_ngcontent-%COMP%] {\n  border-color: var(--accent-primary);\n  background: color-mix(in srgb, var(--accent-primary) 8%, var(--bg-surface));\n}\n.file-row__meta[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n  min-width: 0;\n}\n.file-row__name[_ngcontent-%COMP%] {\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n  max-width: 240px;\n}\n.file-row__clear[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  width: 24px;\n  height: 24px;\n  border: none;\n  background: transparent;\n  color: var(--text-muted);\n  border-radius: var(--radius-full);\n  cursor: pointer;\n}\n.file-row__clear[_ngcontent-%COMP%]:hover {\n  color: var(--accent-danger);\n  background: color-mix(in srgb, var(--accent-danger) 10%, transparent);\n}\n.drop-zone[_ngcontent-%COMP%] {\n  border: 2px dashed var(--border-default);\n  border-radius: var(--radius-md);\n  background: var(--bg-elevated);\n  min-height: 80px;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  justify-content: center;\n  gap: var(--space-1);\n  cursor: pointer;\n  transition: border-color 150ms ease, background 150ms ease;\n  padding: var(--space-4);\n}\n.drop-zone[_ngcontent-%COMP%]:hover, \n.drop-zone--active[_ngcontent-%COMP%] {\n  border-color: var(--accent-primary);\n  background: color-mix(in srgb, var(--accent-primary) 5%, var(--bg-elevated));\n}\n.drop-zone[_ngcontent-%COMP%]:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.drop-zone__icon[_ngcontent-%COMP%] {\n  font-size: 28px;\n  color: var(--text-muted);\n}\n.drop-zone__text[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n}\n.file-list[_ngcontent-%COMP%] {\n  list-style: none;\n  padding: 0;\n  margin: var(--space-2) 0 0;\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-1);\n}\n.file-chip[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-1);\n  padding: var(--space-1) var(--space-2);\n  background: var(--bg-surface);\n  border-radius: var(--radius-sm);\n  border: 1px solid var(--border-subtle);\n}\n.file-chip__icon[_ngcontent-%COMP%] {\n  font-size: 14px;\n  color: var(--text-muted);\n  flex-shrink: 0;\n}\n.file-chip__name[_ngcontent-%COMP%] {\n  flex: 1;\n  font-size: var(--text-xs);\n  color: var(--text-primary);\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n}\n.file-chip__size[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  flex-shrink: 0;\n}\n.file-chip__remove[_ngcontent-%COMP%] {\n  background: none;\n  border: none;\n  cursor: pointer;\n  color: var(--text-muted);\n  padding: 2px;\n  display: flex;\n  align-items: center;\n  border-radius: var(--radius-sm);\n}\n.file-chip__remove[_ngcontent-%COMP%]:hover {\n  color: var(--accent-danger);\n}\n.full-width[_ngcontent-%COMP%] {\n  width: 100%;\n}\n.btn-spinner[_ngcontent-%COMP%] {\n  display: inline-block;\n  width: 14px;\n  height: 14px;\n  border: 2px solid rgba(255, 255, 255, 0.4);\n  border-top-color: white;\n  border-radius: 50%;\n  animation: _ngcontent-%COMP%_spin 0.6s linear infinite;\n  margin-right: var(--space-1);\n}\n@keyframes _ngcontent-%COMP%_spin {\n  to {\n    transform: rotate(360deg);\n  }\n}\n/*# sourceMappingURL=excuse-form-dialog.component.css.map */"], changeDetection: 0 });
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
    <h3 class="form-section__label">
      {{ isLocked() ? '\u0417\u0430\u044F\u0432\u043A\u0430 \u043F\u043E \u0432\u044B\u0431\u0440\u0430\u043D\u043D\u043E\u0439 \u043F\u0430\u0440\u0435' : '\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043D\u044B\u0435 \u0437\u0430\u043D\u044F\u0442\u0438\u044F' }}
    </h3>
    @if (isLocked()) {
      <p class="form-hint">
        \u0422\u0438\u043A\u0435\u0442 \u0431\u0443\u0434\u0435\u0442 \u043F\u043E\u0434\u0430\u043D \u0442\u043E\u043B\u044C\u043A\u043E \u043F\u043E \u044D\u0442\u043E\u0439 \u043F\u0430\u0440\u0435. \u0414\u043B\u044F \u0434\u0440\u0443\u0433\u0438\u0445 \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u043E\u0432 \u043E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 \u0440\u0430\u0437\u0434\u0435\u043B \xAB\u0423\u0432\u0430\u0436\u0438\u0442\u0435\u043B\u044C\u043D\u044B\u0435 \u043F\u0440\u0438\u0447\u0438\u043D\u044B\xBB.
      </p>
    }
    <div class="lesson-list" role="list" aria-label="\u0421\u043F\u0438\u0441\u043E\u043A \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u043E\u0432 \u0441 \u043D\u0430\u0447\u0430\u043B\u0430 \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u0430">
      @if (!hasRecords()) {
        <p class="form-hint">\u041D\u0435\u0442 \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u043E\u0432 \u0441 \u043D\u0430\u0447\u0430\u043B\u0430 \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u0430.</p>
      }
      @for (day of dayGroups(); track day.date) {
        <div class="lesson-day">
          <div class="lesson-day__heading">{{ day.label }}</div>
          @for (lesson of day.records; track lesson.lessonId) {
            <label
              class="lesson-row"
              role="listitem"
              [class.lesson-row--disabled]="isDisabled(lesson.lessonId)"
            >
              <mat-checkbox
                [checked]="isSelected(lesson.lessonId)"
                [disabled]="isDisabled(lesson.lessonId)"
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

  <!-- Attachment -->
  <section class="form-section">
    <h3 class="form-section__label">\u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0430\u044E\u0449\u0438\u0439 \u0434\u043E\u043A\u0443\u043C\u0435\u043D\u0442 <span class="form-hint-inline">(\u043D\u0435\u043E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E, \u0434\u043E 10 \u041C\u0411)</span></h3>
    <p class="form-hint">
      \u0424\u0430\u0439\u043B \u0431\u0443\u0434\u0435\u0442 \u043F\u0435\u0440\u0435\u0441\u043B\u0430\u043D \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0435 \u0432 Telegram. \u041D\u0430 \u0441\u0435\u0440\u0432\u0435\u0440\u0435 \u043E\u043D \u043D\u0435 \u0445\u0440\u0430\u043D\u0438\u0442\u0441\u044F.
    </p>
    <div class="file-row">
      <label class="file-row__picker">
        <input
          type="file"
          (change)="onFileSelected($event)"
          aria-label="\u041F\u0440\u0438\u043A\u0440\u0435\u043F\u0438\u0442\u044C \u0444\u0430\u0439\u043B"
        />
        <span class="file-row__picker-label">
          <i class="ph ph-paperclip" aria-hidden="true"></i>
          {{ attachedFile() ? '\u0417\u0430\u043C\u0435\u043D\u0438\u0442\u044C \u0444\u0430\u0439\u043B' : '\u041F\u0440\u0438\u043A\u0440\u0435\u043F\u0438\u0442\u044C \u0444\u0430\u0439\u043B' }}
        </span>
      </label>
      @if (attachedFile(); as file) {
        <span class="file-row__meta">
          <span class="file-row__name">{{ file.name }}</span>
          <button type="button" class="file-row__clear" (click)="clearFile()" aria-label="\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u0444\u0430\u0439\u043B">
            <i class="ph ph-x" aria-hidden="true"></i>
          </button>
        </span>
      }
    </div>
    @if (fileError()) {
      <p class="form-error" role="alert">{{ fileError() }}</p>
    }
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
`, styles: ["/* src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.css */\n.dialog-body {\n  padding: var(--space-6) var(--space-6) 0;\n  overflow-y: auto;\n  max-height: calc(80vh - 120px);\n}\n.form-section {\n  margin-bottom: var(--space-5);\n}\n.form-section__label {\n  font-size: var(--text-sm);\n  font-weight: 600;\n  color: var(--text-primary);\n  margin-bottom: var(--space-2);\n}\n.form-hint-inline {\n  font-weight: 400;\n  color: var(--text-muted);\n}\n.form-hint {\n  color: var(--text-muted);\n  font-size: var(--text-sm);\n  padding: var(--space-4);\n  text-align: center;\n}\n.form-error {\n  font-size: var(--text-xs);\n  color: var(--accent-danger);\n  margin-top: var(--space-1);\n}\n.lesson-list {\n  background: var(--bg-surface);\n  border-radius: var(--radius-md);\n  max-height: 360px;\n  overflow-y: auto;\n  border: 1px solid var(--border-subtle);\n}\n.lesson-day + .lesson-day {\n  border-top: 1px solid var(--border-subtle);\n}\n.lesson-day__heading {\n  padding: var(--space-2) var(--space-4);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  color: var(--text-muted);\n  background: var(--bg-elevated);\n  text-transform: none;\n  letter-spacing: 0;\n}\n.lesson-row {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-2) var(--space-4);\n  cursor: pointer;\n  border-bottom: 1px solid var(--border-subtle);\n}\n.lesson-row:last-child {\n  border-bottom: none;\n}\n.lesson-row__num {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  min-width: 28px;\n}\n.lesson-row__subject {\n  flex: 1;\n  font-size: var(--text-sm);\n  color: var(--text-primary);\n}\n.lesson-row__mark {\n  font-size: var(--text-sm);\n  font-weight: 600;\n  color: var(--accent-danger, #c62828);\n  min-width: 16px;\n  text-align: center;\n}\n.lesson-row__mark--excused {\n  color: var(--accent-warning, #c77700);\n}\n.lesson-row--disabled {\n  cursor: not-allowed;\n  opacity: 0.5;\n}\n.file-row {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  flex-wrap: wrap;\n  margin-top: var(--space-2);\n}\n.file-row__picker {\n  position: relative;\n  display: inline-flex;\n  cursor: pointer;\n}\n.file-row__picker input[type=file] {\n  position: absolute;\n  inset: 0;\n  opacity: 0;\n  cursor: pointer;\n}\n.file-row__picker-label {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-1);\n  padding: var(--space-2) var(--space-4);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-pill, 9999px);\n  background: var(--bg-surface);\n  color: var(--text-primary);\n  font-size: var(--text-sm);\n  font-weight: 500;\n  transition: border-color 150ms ease, background 150ms ease;\n}\n.file-row__picker:hover .file-row__picker-label {\n  border-color: var(--accent-primary);\n  background: color-mix(in srgb, var(--accent-primary) 8%, var(--bg-surface));\n}\n.file-row__meta {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n  min-width: 0;\n}\n.file-row__name {\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n  max-width: 240px;\n}\n.file-row__clear {\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  width: 24px;\n  height: 24px;\n  border: none;\n  background: transparent;\n  color: var(--text-muted);\n  border-radius: var(--radius-full);\n  cursor: pointer;\n}\n.file-row__clear:hover {\n  color: var(--accent-danger);\n  background: color-mix(in srgb, var(--accent-danger) 10%, transparent);\n}\n.drop-zone {\n  border: 2px dashed var(--border-default);\n  border-radius: var(--radius-md);\n  background: var(--bg-elevated);\n  min-height: 80px;\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  justify-content: center;\n  gap: var(--space-1);\n  cursor: pointer;\n  transition: border-color 150ms ease, background 150ms ease;\n  padding: var(--space-4);\n}\n.drop-zone:hover,\n.drop-zone--active {\n  border-color: var(--accent-primary);\n  background: color-mix(in srgb, var(--accent-primary) 5%, var(--bg-elevated));\n}\n.drop-zone:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n.drop-zone__icon {\n  font-size: 28px;\n  color: var(--text-muted);\n}\n.drop-zone__text {\n  font-size: var(--text-sm);\n  color: var(--text-secondary);\n}\n.file-list {\n  list-style: none;\n  padding: 0;\n  margin: var(--space-2) 0 0;\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-1);\n}\n.file-chip {\n  display: flex;\n  align-items: center;\n  gap: var(--space-1);\n  padding: var(--space-1) var(--space-2);\n  background: var(--bg-surface);\n  border-radius: var(--radius-sm);\n  border: 1px solid var(--border-subtle);\n}\n.file-chip__icon {\n  font-size: 14px;\n  color: var(--text-muted);\n  flex-shrink: 0;\n}\n.file-chip__name {\n  flex: 1;\n  font-size: var(--text-xs);\n  color: var(--text-primary);\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n}\n.file-chip__size {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n  flex-shrink: 0;\n}\n.file-chip__remove {\n  background: none;\n  border: none;\n  cursor: pointer;\n  color: var(--text-muted);\n  padding: 2px;\n  display: flex;\n  align-items: center;\n  border-radius: var(--radius-sm);\n}\n.file-chip__remove:hover {\n  color: var(--accent-danger);\n}\n.full-width {\n  width: 100%;\n}\n.btn-spinner {\n  display: inline-block;\n  width: 14px;\n  height: 14px;\n  border: 2px solid rgba(255, 255, 255, 0.4);\n  border-top-color: white;\n  border-radius: 50%;\n  animation: spin 0.6s linear infinite;\n  margin-right: var(--space-1);\n}\n@keyframes spin {\n  to {\n    transform: rotate(360deg);\n  }\n}\n/*# sourceMappingURL=excuse-form-dialog.component.css.map */\n"] }]
  }], () => [{ type: void 0, decorators: [{
    type: Inject,
    args: [MAT_DIALOG_DATA]
  }] }], null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(ExcuseFormDialogComponent, { className: "ExcuseFormDialogComponent", filePath: "src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.ts", lineNumber: 55 });
})();

export {
  EXCUSE_TYPE_LABELS,
  ExcuseFormDialogComponent
};
//# sourceMappingURL=chunk-WZUMQHNK.js.map
