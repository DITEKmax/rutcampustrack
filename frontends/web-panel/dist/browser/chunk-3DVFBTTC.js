import {
  HeadmanApiService
} from "./chunk-BU5FJGI6.js";
import {
  MatProgressSpinner,
  MatProgressSpinnerModule
} from "./chunk-BAW76DQ6.js";
import {
  MatCell,
  MatCellDef,
  MatColumnDef,
  MatHeaderCell,
  MatHeaderCellDef,
  MatHeaderRow,
  MatHeaderRowDef,
  MatRow,
  MatRowDef,
  MatTable,
  MatTableModule
} from "./chunk-GRVREKGH.js";
import {
  MatIconModule
} from "./chunk-62VJMDFV.js";
import "./chunk-F5IUR55I.js";
import {
  MatSnackBar
} from "./chunk-53YESMZZ.js";
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogModule,
  MatDialogRef,
  MatDialogTitle
} from "./chunk-M24SPP55.js";
import "./chunk-33KX5TJL.js";
import {
  animate,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import "./chunk-SEQXD3QE.js";
import {
  MatInput,
  MatInputModule
} from "./chunk-WQN3TC62.js";
import "./chunk-OIBNGD5S.js";
import {
  MatError,
  MatFormField,
  MatFormFieldModule,
  MatHint,
  MatLabel,
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
  Validators,
  ɵNgNoValidate
} from "./chunk-XAMIXVT7.js";
import {
  RouterModule
} from "./chunk-643P6YYN.js";
import "./chunk-7WMFDRFR.js";
import {
  CommonModule,
  NgForOf,
  NgIf
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  catchError,
  forkJoin,
  inject,
  of,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵelement,
  ɵɵelementContainerEnd,
  ɵɵelementContainerStart,
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
} from "./chunk-MFRIGFR2.js";

// src/app/features/headman/subjects/subject-dialog.component.ts
var _forTrack0 = ($index, $item) => $item.id;
function SubjectDialogComponent_mat_error_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, " \u041E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043F\u043E\u043B\u0435 ");
    \u0275\u0275elementEnd();
  }
}
function SubjectDialogComponent_mat_error_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, " \u041D\u0435 \u0431\u043E\u043B\u0435\u0435 120 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432 ");
    \u0275\u0275elementEnd();
  }
}
function SubjectDialogComponent_mat_error_20_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, " \u041E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043F\u043E\u043B\u0435 ");
    \u0275\u0275elementEnd();
  }
}
function SubjectDialogComponent_For_26_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-option", 11);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const t_r1 = ctx.$implicit;
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275property("value", t_r1.id);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r1.teacherLabel(t_r1));
  }
}
function SubjectDialogComponent_Conditional_27_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-hint");
    \u0275\u0275text(1, " \u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0438 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u044B. \u041E\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044C \u043A \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440\u0443. ");
    \u0275\u0275elementEnd();
  }
}
function SubjectDialogComponent_Conditional_28_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-hint");
    \u0275\u0275text(1, " \u041C\u043E\u0436\u043D\u043E \u0432\u044B\u0431\u0440\u0430\u0442\u044C \u043D\u0435\u0441\u043A\u043E\u043B\u044C\u043A\u0438\u0445; \u0432\u0441\u0435 \u2014 \u0440\u0430\u0432\u043D\u043E\u043F\u0440\u0430\u0432\u043D\u044B\u0435 \u043D\u0430\u0431\u043B\u044E\u0434\u0430\u0442\u0435\u043B\u0438. ");
    \u0275\u0275elementEnd();
  }
}
function SubjectDialogComponent_Conditional_29_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 12);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r1.apiError);
  }
}
function SubjectDialogComponent_Conditional_34_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-spinner", 16);
  }
}
var SubjectDialogComponent = class _SubjectDialogComponent {
  headmanApi = inject(HeadmanApiService);
  snackBar = inject(MatSnackBar);
  dialogRef = inject(MatDialogRef);
  data = inject(MAT_DIALOG_DATA);
  isEdit;
  form = new FormGroup({
    name: new FormControl("", { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] }),
    type: new FormControl("", { nonNullable: true, validators: [Validators.required] }),
    teacherIds: new FormControl([], { nonNullable: true })
  });
  teachers = [];
  teachersLoading = true;
  teachersError = false;
  submitting = false;
  apiError = null;
  constructor() {
    this.isEdit = this.data.mode === "edit";
    if (this.isEdit && this.data.subject) {
      this.form.patchValue({
        name: this.data.subject.name,
        type: this.data.subject.type ?? "",
        teacherIds: this.data.subject.teacherIds ?? []
      });
    }
  }
  ngOnInit() {
    this.headmanApi.listTeachers().subscribe({
      next: (resp) => {
        const embedded = resp?._embedded;
        let list;
        if (embedded) {
          list = Object.values(embedded)[0] ?? [];
        } else if (Array.isArray(resp)) {
          list = resp;
        } else {
          list = [];
        }
        const collator = new Intl.Collator("ru", { sensitivity: "base" });
        this.teachers = list.slice().sort((a, b) => collator.compare(a?.lastName ?? a?.fullName ?? "", b?.lastName ?? b?.fullName ?? ""));
        this.teachersLoading = false;
      },
      error: () => {
        this.teachers = [];
        this.teachersLoading = false;
        this.teachersError = true;
      }
    });
  }
  teacherLabel(t) {
    const fio = t?.fullName ?? t?.lastName ?? "";
    const num = t?.employeeNumber;
    return num ? `${fio} (\u0442\u0430\u0431. \u2116 ${num})` : fio;
  }
  onSubmit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting = true;
    this.apiError = null;
    const body = {
      name: this.form.value.name,
      type: this.form.value.type,
      teacherIds: this.form.value.teacherIds ?? []
    };
    const call = this.isEdit ? this.headmanApi.updateSubject(this.data.subject.id, body) : this.headmanApi.createSubject(body);
    call.subscribe({
      next: () => {
        this.submitting = false;
        const msg = this.isEdit ? "\u041F\u0440\u0435\u0434\u043C\u0435\u0442 \u043E\u0431\u043D\u043E\u0432\u043B\u0451\u043D." : "\u041F\u0440\u0435\u0434\u043C\u0435\u0442 \u0441\u043E\u0437\u0434\u0430\u043D.";
        this.snackBar.open(msg, void 0, { duration: 4e3 });
        this.dialogRef.close(true);
      },
      error: () => {
        this.submitting = false;
        this.apiError = "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0441\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437.";
      }
    });
  }
  static \u0275fac = function SubjectDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _SubjectDialogComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _SubjectDialogComponent, selectors: [["app-subject-dialog"]], decls: 36, vars: 12, consts: [["mat-dialog-title", ""], [3, "formGroup"], ["appearance", "outline", 2, "width", "100%", "margin-bottom", "var(--space-4)"], ["matInput", "", "formControlName", "name", "maxlength", "120", "placeholder", "\u041D\u0430\u043F\u0440\u0438\u043C\u0435\u0440: \u041B\u0438\u043D\u0435\u0439\u043D\u0430\u044F \u0430\u043B\u0433\u0435\u0431\u0440\u0430"], [4, "ngIf"], ["formControlName", "type", "aria-label", "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0442\u0438\u043F \u0437\u0430\u043D\u044F\u0442\u0438\u044F"], ["value", "LECTURE"], ["value", "PRACTICE"], ["value", "LAB"], ["appearance", "outline", 2, "width", "100%"], ["formControlName", "teacherIds", "multiple", "", "aria-label", "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0435\u0439", 3, "disabled"], [3, "value"], [1, "page-error", 2, "margin-top", "var(--space-4)"], ["align", "end"], ["mat-stroked-button", "", "type", "button", 3, "mat-dialog-close"], ["type", "button", 1, "btn-brand", 3, "click", "disabled"], ["diameter", "16"]], template: function SubjectDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "h2", 0);
      \u0275\u0275text(1);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(2, "mat-dialog-content")(3, "form", 1)(4, "mat-form-field", 2)(5, "mat-label");
      \u0275\u0275text(6, "\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275element(7, "input", 3);
      \u0275\u0275template(8, SubjectDialogComponent_mat_error_8_Template, 2, 0, "mat-error", 4)(9, SubjectDialogComponent_mat_error_9_Template, 2, 0, "mat-error", 4);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(10, "mat-form-field", 2)(11, "mat-label");
      \u0275\u0275text(12, "\u0422\u0438\u043F \u0437\u0430\u043D\u044F\u0442\u0438\u044F");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(13, "mat-select", 5)(14, "mat-option", 6);
      \u0275\u0275text(15, "\u041B\u0435\u043A\u0446\u0438\u044F");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(16, "mat-option", 7);
      \u0275\u0275text(17, "\u041F\u0440\u0430\u043A\u0442\u0438\u043A\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(18, "mat-option", 8);
      \u0275\u0275text(19, "\u041B\u0430\u0431\u043E\u0440\u0430\u0442\u043E\u0440\u043D\u0430\u044F");
      \u0275\u0275elementEnd()();
      \u0275\u0275template(20, SubjectDialogComponent_mat_error_20_Template, 2, 0, "mat-error", 4);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(21, "mat-form-field", 9)(22, "mat-label");
      \u0275\u0275text(23, "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0438");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(24, "mat-select", 10);
      \u0275\u0275repeaterCreate(25, SubjectDialogComponent_For_26_Template, 2, 2, "mat-option", 11, _forTrack0);
      \u0275\u0275elementEnd();
      \u0275\u0275template(27, SubjectDialogComponent_Conditional_27_Template, 2, 0, "mat-hint")(28, SubjectDialogComponent_Conditional_28_Template, 2, 0, "mat-hint");
      \u0275\u0275elementEnd();
      \u0275\u0275template(29, SubjectDialogComponent_Conditional_29_Template, 2, 1, "div", 12);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(30, "mat-dialog-actions", 13)(31, "button", 14);
      \u0275\u0275text(32, "\u041E\u0442\u043C\u0435\u043D\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(33, "button", 15);
      \u0275\u0275listener("click", function SubjectDialogComponent_Template_button_click_33_listener() {
        return ctx.onSubmit();
      });
      \u0275\u0275template(34, SubjectDialogComponent_Conditional_34_Template, 1, 0, "mat-spinner", 16);
      \u0275\u0275text(35);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      let tmp_2_0;
      let tmp_3_0;
      let tmp_4_0;
      \u0275\u0275advance();
      \u0275\u0275textInterpolate(ctx.isEdit ? "\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442" : "\u041D\u043E\u0432\u044B\u0439 \u043F\u0440\u0435\u0434\u043C\u0435\u0442");
      \u0275\u0275advance(2);
      \u0275\u0275property("formGroup", ctx.form);
      \u0275\u0275advance(5);
      \u0275\u0275property("ngIf", (tmp_2_0 = ctx.form.get("name")) == null ? null : tmp_2_0.hasError("required"));
      \u0275\u0275advance();
      \u0275\u0275property("ngIf", (tmp_3_0 = ctx.form.get("name")) == null ? null : tmp_3_0.hasError("maxlength"));
      \u0275\u0275advance(11);
      \u0275\u0275property("ngIf", (tmp_4_0 = ctx.form.get("type")) == null ? null : tmp_4_0.hasError("required"));
      \u0275\u0275advance(4);
      \u0275\u0275property("disabled", ctx.teachersLoading || ctx.teachersError || ctx.teachers.length === 0);
      \u0275\u0275advance();
      \u0275\u0275repeater(ctx.teachers);
      \u0275\u0275advance(2);
      \u0275\u0275conditional(ctx.teachersError || ctx.teachers.length === 0 && !ctx.teachersLoading ? 27 : 28);
      \u0275\u0275advance(2);
      \u0275\u0275conditional(ctx.apiError ? 29 : -1);
      \u0275\u0275advance(2);
      \u0275\u0275property("mat-dialog-close", false);
      \u0275\u0275advance(2);
      \u0275\u0275property("disabled", ctx.submitting || ctx.form.invalid);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.submitting ? 34 : -1);
      \u0275\u0275advance();
      \u0275\u0275textInterpolate1(" ", ctx.isEdit ? "\u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442" : "\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442", " ");
    }
  }, dependencies: [
    CommonModule,
    NgIf,
    ReactiveFormsModule,
    \u0275NgNoValidate,
    DefaultValueAccessor,
    NgControlStatus,
    NgControlStatusGroup,
    MaxLengthValidator,
    FormGroupDirective,
    FormControlName,
    MatDialogModule,
    MatDialogClose,
    MatDialogTitle,
    MatDialogActions,
    MatDialogContent,
    MatButtonModule,
    MatButton,
    MatInputModule,
    MatInput,
    MatFormField,
    MatLabel,
    MatHint,
    MatError,
    MatSelectModule,
    MatSelect,
    MatOption,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    MatProgressSpinner
  ], styles: ["\n\nmat-dialog-content[_ngcontent-%COMP%] {\n  padding-top: var(--space-4);\n}\n/*# sourceMappingURL=subject-dialog.component.css.map */"] });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(SubjectDialogComponent, [{
    type: Component,
    args: [{ selector: "app-subject-dialog", standalone: true, imports: [
      CommonModule,
      ReactiveFormsModule,
      MatDialogModule,
      MatButtonModule,
      MatInputModule,
      MatSelectModule,
      MatFormFieldModule,
      MatProgressSpinnerModule
    ], template: `
    <h2 mat-dialog-title>{{ isEdit ? '\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442' : '\u041D\u043E\u0432\u044B\u0439 \u043F\u0440\u0435\u0434\u043C\u0435\u0442' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form">

        <!-- Subject name -->
        <mat-form-field appearance="outline" style="width:100%; margin-bottom: var(--space-4)">
          <mat-label>\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u0430</mat-label>
          <input matInput formControlName="name"
                 maxlength="120"
                 placeholder="\u041D\u0430\u043F\u0440\u0438\u043C\u0435\u0440: \u041B\u0438\u043D\u0435\u0439\u043D\u0430\u044F \u0430\u043B\u0433\u0435\u0431\u0440\u0430" />
          <mat-error *ngIf="form.get('name')?.hasError('required')">
            \u041E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043F\u043E\u043B\u0435
          </mat-error>
          <mat-error *ngIf="form.get('name')?.hasError('maxlength')">
            \u041D\u0435 \u0431\u043E\u043B\u0435\u0435 120 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432
          </mat-error>
        </mat-form-field>

        <!-- Subject type (D-02) -->
        <mat-form-field appearance="outline" style="width:100%; margin-bottom: var(--space-4)">
          <mat-label>\u0422\u0438\u043F \u0437\u0430\u043D\u044F\u0442\u0438\u044F</mat-label>
          <mat-select formControlName="type" aria-label="\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0442\u0438\u043F \u0437\u0430\u043D\u044F\u0442\u0438\u044F">
            <mat-option value="LECTURE">\u041B\u0435\u043A\u0446\u0438\u044F</mat-option>
            <mat-option value="PRACTICE">\u041F\u0440\u0430\u043A\u0442\u0438\u043A\u0430</mat-option>
            <mat-option value="LAB">\u041B\u0430\u0431\u043E\u0440\u0430\u0442\u043E\u0440\u043D\u0430\u044F</mat-option>
          </mat-select>
          <mat-error *ngIf="form.get('type')?.hasError('required')">
            \u041E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043F\u043E\u043B\u0435
          </mat-error>
        </mat-form-field>

        <!-- Teacher multi-select (D-19) -->
        <mat-form-field appearance="outline" style="width:100%">
          <mat-label>\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0438</mat-label>
          <mat-select formControlName="teacherIds"
                      multiple
                      aria-label="\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0435\u0439"
                      [disabled]="teachersLoading || teachersError || teachers.length === 0">
            @for (t of teachers; track t.id) {
              <mat-option [value]="t.id">{{ teacherLabel(t) }}</mat-option>
            }
          </mat-select>
          @if (teachersError || (teachers.length === 0 && !teachersLoading)) {
            <mat-hint>
              \u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0438 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u044B. \u041E\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044C \u043A \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440\u0443.
            </mat-hint>
          } @else {
            <mat-hint>
              \u041C\u043E\u0436\u043D\u043E \u0432\u044B\u0431\u0440\u0430\u0442\u044C \u043D\u0435\u0441\u043A\u043E\u043B\u044C\u043A\u0438\u0445; \u0432\u0441\u0435 \u2014 \u0440\u0430\u0432\u043D\u043E\u043F\u0440\u0430\u0432\u043D\u044B\u0435 \u043D\u0430\u0431\u043B\u044E\u0434\u0430\u0442\u0435\u043B\u0438.
            </mat-hint>
          }
        </mat-form-field>

        @if (apiError) {
          <div class="page-error" style="margin-top: var(--space-4)">{{ apiError }}</div>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" [mat-dialog-close]="false">\u041E\u0442\u043C\u0435\u043D\u0430</button>
      <button class="btn-brand" type="button"
              [disabled]="submitting || form.invalid"
              (click)="onSubmit()">
        @if (submitting) {
          <mat-spinner diameter="16"></mat-spinner>
        }
        {{ isEdit ? '\u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442' : '\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442' }}
      </button>
    </mat-dialog-actions>
  `, styles: ["/* angular:styles/component:css;16ceb578ec5b2d1db89f49bb90f39fa168b072d7555cb1182aa1c9315fad841e;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/subjects/subject-dialog.component.ts */\nmat-dialog-content {\n  padding-top: var(--space-4);\n}\n/*# sourceMappingURL=subject-dialog.component.css.map */\n"] }]
  }], () => [], null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(SubjectDialogComponent, { className: "SubjectDialogComponent", filePath: "src/app/features/headman/subjects/subject-dialog.component.ts", lineNumber: 129 });
})();

// src/app/features/headman/subjects/delete-subject-dialog.component.ts
function DeleteSubjectDialogComponent_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "h2", 0);
    \u0275\u0275text(1, "\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442?");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(2, "mat-dialog-content");
    \u0275\u0275text(3);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "mat-dialog-actions", 1)(5, "button", 2);
    \u0275\u0275text(6, "\u041E\u0442\u043C\u0435\u043D\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(7, "button", 3);
    \u0275\u0275text(8, "\u0423\u0434\u0430\u043B\u0438\u0442\u044C");
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate1(" \u041F\u0440\u0435\u0434\u043C\u0435\u0442 \xAB", ctx_r0.data.subjectName, "\xBB \u0431\u0443\u0434\u0435\u0442 \u0443\u0434\u0430\u043B\u0451\u043D. \u042D\u0442\u043E \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435 \u043D\u0435\u043B\u044C\u0437\u044F \u043E\u0442\u043C\u0435\u043D\u0438\u0442\u044C. ");
    \u0275\u0275advance(2);
    \u0275\u0275property("mat-dialog-close", false);
    \u0275\u0275advance(2);
    \u0275\u0275property("mat-dialog-close", true);
  }
}
function DeleteSubjectDialogComponent_Conditional_1_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "li");
    \u0275\u0275text(1, "\u0420\u0430\u0437\u043E\u0432\u044B\u0445 \u043F\u0430\u0440: ");
    \u0275\u0275elementStart(2, "strong");
    \u0275\u0275text(3);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate(ctx_r0.data.cascade.oneOffLessonsCount);
  }
}
function DeleteSubjectDialogComponent_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "h2", 0);
    \u0275\u0275text(1, "\u0412\u043D\u0438\u043C\u0430\u043D\u0438\u0435: \u0431\u0443\u0434\u0443\u0442 \u0443\u0434\u0430\u043B\u0435\u043D\u044B \u0434\u0430\u043D\u043D\u044B\u0435 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(2, "mat-dialog-content")(3, "p");
    \u0275\u0275text(4, " \u0423 \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u0430 ");
    \u0275\u0275elementStart(5, "strong");
    \u0275\u0275text(6);
    \u0275\u0275elementEnd();
    \u0275\u0275text(7, " \u0435\u0441\u0442\u044C \u0438\u0441\u0442\u043E\u0440\u0438\u044F: ");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(8, "ul", 4)(9, "li");
    \u0275\u0275text(10, "\u041F\u0430\u0440 \u0432 \u0440\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0438: ");
    \u0275\u0275elementStart(11, "strong");
    \u0275\u0275text(12);
    \u0275\u0275elementEnd()();
    \u0275\u0275template(13, DeleteSubjectDialogComponent_Conditional_1_Conditional_13_Template, 4, 1, "li");
    \u0275\u0275elementStart(14, "li");
    \u0275\u0275text(15, "\u0417\u0430\u043D\u044F\u0442\u0438\u0439 \u0432\u0441\u0435\u0433\u043E: ");
    \u0275\u0275elementStart(16, "strong");
    \u0275\u0275text(17);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(18, "li", 5);
    \u0275\u0275text(19, " \u0417\u0430\u043D\u044F\u0442\u0438\u0439 \u0441 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C\u044E: ");
    \u0275\u0275elementStart(20, "strong");
    \u0275\u0275text(21);
    \u0275\u0275elementEnd()()();
    \u0275\u0275elementStart(22, "div", 6);
    \u0275\u0275element(23, "i", 7);
    \u0275\u0275elementStart(24, "div")(25, "strong");
    \u0275\u0275text(26, "\u0412\u0441\u0435 \u043E\u0442\u043C\u0435\u0442\u043A\u0438 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432 \u043F\u043E \u044D\u0442\u043E\u043C\u0443 \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u0443 \u0431\u0443\u0434\u0443\u0442 \u0443\u0434\u0430\u043B\u0435\u043D\u044B \u0431\u0435\u0437\u0432\u043E\u0437\u0432\u0440\u0430\u0442\u043D\u043E.");
    \u0275\u0275elementEnd();
    \u0275\u0275text(27, " \u0415\u0441\u043B\u0438 \u044D\u0442\u0438 \u0434\u0430\u043D\u043D\u044B\u0435 \u0435\u0449\u0451 \u043D\u0443\u0436\u043D\u044B \u2014 \u0441\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u0435 \u0438\u0445 \u0441\u043A\u0440\u0438\u043D\u0448\u043E\u0442\u0430\u043C\u0438 \u043F\u0435\u0440\u0435\u0434 \u0443\u0434\u0430\u043B\u0435\u043D\u0438\u0435\u043C. ");
    \u0275\u0275elementEnd()()();
    \u0275\u0275elementStart(28, "mat-dialog-actions", 1)(29, "button", 2);
    \u0275\u0275text(30, "\u041E\u0442\u043C\u0435\u043D\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(31, "button", 3);
    \u0275\u0275text(32, " \u0412\u0441\u0451 \u0440\u0430\u0432\u043D\u043E \u0443\u0434\u0430\u043B\u0438\u0442\u044C ");
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(6);
    \u0275\u0275textInterpolate1("\xAB", ctx_r0.data.subjectName, "\xBB");
    \u0275\u0275advance(6);
    \u0275\u0275textInterpolate(ctx_r0.data.cascade.scheduleItemsCount);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.data.cascade.oneOffLessonsCount > 0 ? 13 : -1);
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate(ctx_r0.data.cascade.totalLessonsCount);
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate(ctx_r0.data.cascade.nonPlannedLessonsCount);
    \u0275\u0275advance(8);
    \u0275\u0275property("mat-dialog-close", false);
    \u0275\u0275advance(2);
    \u0275\u0275property("mat-dialog-close", true);
  }
}
var DeleteSubjectDialogComponent = class _DeleteSubjectDialogComponent {
  data = inject(MAT_DIALOG_DATA);
  static \u0275fac = function DeleteSubjectDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _DeleteSubjectDialogComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _DeleteSubjectDialogComponent, selectors: [["app-delete-subject-dialog"]], decls: 2, vars: 1, consts: [["mat-dialog-title", ""], ["align", "end"], ["mat-stroked-button", "", 3, "mat-dialog-close"], ["mat-flat-button", "", 1, "btn-danger", 3, "mat-dialog-close"], [1, "cascade-list"], [1, "warning"], [1, "warning-box"], [1, "ph-fill", "ph-warning"]], template: function DeleteSubjectDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275template(0, DeleteSubjectDialogComponent_Conditional_0_Template, 9, 3)(1, DeleteSubjectDialogComponent_Conditional_1_Template, 33, 7);
    }
    if (rf & 2) {
      \u0275\u0275conditional(!ctx.data.cascade ? 0 : 1);
    }
  }, dependencies: [CommonModule, MatDialogModule, MatDialogClose, MatDialogTitle, MatDialogActions, MatDialogContent, MatButtonModule, MatButton], styles: ["\n\n.btn-danger[_ngcontent-%COMP%] {\n  background: var(--accent-danger);\n  color: white;\n}\n.cascade-list[_ngcontent-%COMP%] {\n  margin: 8px 0 12px;\n  padding-left: 20px;\n  line-height: 1.8;\n}\n.cascade-list[_ngcontent-%COMP%]   .warning[_ngcontent-%COMP%] {\n  color: var(--accent-danger);\n}\n.warning-box[_ngcontent-%COMP%] {\n  display: flex;\n  gap: 12px;\n  padding: 12px;\n  margin-top: 8px;\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-danger) 10%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 30%, transparent);\n  color: var(--text-primary);\n  font-size: 0.875rem;\n}\n.warning-box[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  color: var(--accent-danger);\n  font-size: 1.5rem;\n  flex-shrink: 0;\n}\n/*# sourceMappingURL=delete-subject-dialog.component.css.map */"] });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(DeleteSubjectDialogComponent, [{
    type: Component,
    args: [{ selector: "app-delete-subject-dialog", standalone: true, imports: [CommonModule, MatDialogModule, MatButtonModule], template: `
    @if (!data.cascade) {
      <h2 mat-dialog-title>\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442?</h2>
      <mat-dialog-content>
        \u041F\u0440\u0435\u0434\u043C\u0435\u0442 \xAB{{ data.subjectName }}\xBB \u0431\u0443\u0434\u0435\u0442 \u0443\u0434\u0430\u043B\u0451\u043D. \u042D\u0442\u043E \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435 \u043D\u0435\u043B\u044C\u0437\u044F \u043E\u0442\u043C\u0435\u043D\u0438\u0442\u044C.
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-stroked-button [mat-dialog-close]="false">\u041E\u0442\u043C\u0435\u043D\u0430</button>
        <button mat-flat-button class="btn-danger" [mat-dialog-close]="true">\u0423\u0434\u0430\u043B\u0438\u0442\u044C</button>
      </mat-dialog-actions>
    } @else {
      <h2 mat-dialog-title>\u0412\u043D\u0438\u043C\u0430\u043D\u0438\u0435: \u0431\u0443\u0434\u0443\u0442 \u0443\u0434\u0430\u043B\u0435\u043D\u044B \u0434\u0430\u043D\u043D\u044B\u0435 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438</h2>
      <mat-dialog-content>
        <p>
          \u0423 \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u0430 <strong>\xAB{{ data.subjectName }}\xBB</strong> \u0435\u0441\u0442\u044C \u0438\u0441\u0442\u043E\u0440\u0438\u044F:
        </p>
        <ul class="cascade-list">
          <li>\u041F\u0430\u0440 \u0432 \u0440\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0438: <strong>{{ data.cascade.scheduleItemsCount }}</strong></li>
          @if (data.cascade.oneOffLessonsCount > 0) {
            <li>\u0420\u0430\u0437\u043E\u0432\u044B\u0445 \u043F\u0430\u0440: <strong>{{ data.cascade.oneOffLessonsCount }}</strong></li>
          }
          <li>\u0417\u0430\u043D\u044F\u0442\u0438\u0439 \u0432\u0441\u0435\u0433\u043E: <strong>{{ data.cascade.totalLessonsCount }}</strong></li>
          <li class="warning">
            \u0417\u0430\u043D\u044F\u0442\u0438\u0439 \u0441 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C\u044E: <strong>{{ data.cascade.nonPlannedLessonsCount }}</strong>
          </li>
        </ul>
        <div class="warning-box">
          <i class="ph-fill ph-warning"></i>
          <div>
            <strong>\u0412\u0441\u0435 \u043E\u0442\u043C\u0435\u0442\u043A\u0438 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432 \u043F\u043E \u044D\u0442\u043E\u043C\u0443 \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u0443 \u0431\u0443\u0434\u0443\u0442 \u0443\u0434\u0430\u043B\u0435\u043D\u044B \u0431\u0435\u0437\u0432\u043E\u0437\u0432\u0440\u0430\u0442\u043D\u043E.</strong>
            \u0415\u0441\u043B\u0438 \u044D\u0442\u0438 \u0434\u0430\u043D\u043D\u044B\u0435 \u0435\u0449\u0451 \u043D\u0443\u0436\u043D\u044B \u2014 \u0441\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u0435 \u0438\u0445 \u0441\u043A\u0440\u0438\u043D\u0448\u043E\u0442\u0430\u043C\u0438 \u043F\u0435\u0440\u0435\u0434 \u0443\u0434\u0430\u043B\u0435\u043D\u0438\u0435\u043C.
          </div>
        </div>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-stroked-button [mat-dialog-close]="false">\u041E\u0442\u043C\u0435\u043D\u0430</button>
        <button mat-flat-button class="btn-danger" [mat-dialog-close]="true">
          \u0412\u0441\u0451 \u0440\u0430\u0432\u043D\u043E \u0443\u0434\u0430\u043B\u0438\u0442\u044C
        </button>
      </mat-dialog-actions>
    }
  `, styles: ["/* angular:styles/component:css;9f7d257b90868c967f70eae287689047f9ada6aa75edd77486135d00308fe529;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/subjects/delete-subject-dialog.component.ts */\n.btn-danger {\n  background: var(--accent-danger);\n  color: white;\n}\n.cascade-list {\n  margin: 8px 0 12px;\n  padding-left: 20px;\n  line-height: 1.8;\n}\n.cascade-list .warning {\n  color: var(--accent-danger);\n}\n.warning-box {\n  display: flex;\n  gap: 12px;\n  padding: 12px;\n  margin-top: 8px;\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-danger) 10%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 30%, transparent);\n  color: var(--text-primary);\n  font-size: 0.875rem;\n}\n.warning-box i {\n  color: var(--accent-danger);\n  font-size: 1.5rem;\n  flex-shrink: 0;\n}\n/*# sourceMappingURL=delete-subject-dialog.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(DeleteSubjectDialogComponent, { className: "DeleteSubjectDialogComponent", filePath: "src/app/features/headman/subjects/delete-subject-dialog.component.ts", lineNumber: 94 });
})();

// src/app/features/headman/subjects/headman-subjects.component.ts
var _c0 = () => [1, 2, 3, 4];
var _c1 = () => ["name", "type", "teacher", "actions"];
function HeadmanSubjectsComponent_Conditional_15_div_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 14);
  }
}
function HeadmanSubjectsComponent_Conditional_15_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275template(0, HeadmanSubjectsComponent_Conditional_15_div_0_Template, 1, 0, "div", 12);
    \u0275\u0275elementStart(1, "span", 13);
    \u0275\u0275text(2, "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...");
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    \u0275\u0275property("ngForOf", \u0275\u0275pureFunction0(1, _c0));
  }
}
function HeadmanSubjectsComponent_Conditional_16_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 9);
    \u0275\u0275element(1, "i", 15);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.error());
  }
}
function HeadmanSubjectsComponent_Conditional_17_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "div", 10);
    \u0275\u0275element(1, "i", 16);
    \u0275\u0275elementStart(2, "h3");
    \u0275\u0275text(3, "\u041D\u0435\u0442 \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u043E\u0432");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p");
    \u0275\u0275text(5, "\u0414\u043E\u0431\u0430\u0432\u044C\u0442\u0435 \u043F\u0435\u0440\u0432\u044B\u0439 \u043F\u0440\u0435\u0434\u043C\u0435\u0442, \u0447\u0442\u043E\u0431\u044B \u043D\u0430\u0447\u0430\u0442\u044C \u0432\u0435\u0441\u0442\u0438 \u0436\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438.");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "button", 5);
    \u0275\u0275listener("click", function HeadmanSubjectsComponent_Conditional_17_Template_button_click_6_listener() {
      \u0275\u0275restoreView(_r2);
      const ctx_r0 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r0.openCreateDialog());
    });
    \u0275\u0275text(7, "\u0414\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442");
    \u0275\u0275elementEnd()();
  }
}
function HeadmanSubjectsComponent_Conditional_18_th_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "th", 25);
    \u0275\u0275text(1, "\u041F\u0440\u0435\u0434\u043C\u0435\u0442");
    \u0275\u0275elementEnd();
  }
}
function HeadmanSubjectsComponent_Conditional_18_td_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "td", 26)(1, "span", 27);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const s_r3 = ctx.$implicit;
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(s_r3.name);
  }
}
function HeadmanSubjectsComponent_Conditional_18_th_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "th", 25);
    \u0275\u0275text(1, "\u0422\u0438\u043F");
    \u0275\u0275elementEnd();
  }
}
function HeadmanSubjectsComponent_Conditional_18_td_6_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 28);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const s_r4 = \u0275\u0275nextContext().$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.subjectTypeLabel(s_r4.type));
  }
}
function HeadmanSubjectsComponent_Conditional_18_td_6_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 29);
    \u0275\u0275text(1, "\u2014");
    \u0275\u0275elementEnd();
  }
}
function HeadmanSubjectsComponent_Conditional_18_td_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "td", 26);
    \u0275\u0275template(1, HeadmanSubjectsComponent_Conditional_18_td_6_Conditional_1_Template, 2, 1, "span", 28)(2, HeadmanSubjectsComponent_Conditional_18_td_6_Conditional_2_Template, 2, 0, "span", 29);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const s_r4 = ctx.$implicit;
    \u0275\u0275advance();
    \u0275\u0275conditional(s_r4.type ? 1 : 2);
  }
}
function HeadmanSubjectsComponent_Conditional_18_th_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "th", 25);
    \u0275\u0275text(1, "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0438");
    \u0275\u0275elementEnd();
  }
}
function HeadmanSubjectsComponent_Conditional_18_td_9_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const s_r5 = \u0275\u0275nextContext().$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.teacherNames(s_r5).join(", "));
  }
}
function HeadmanSubjectsComponent_Conditional_18_td_9_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 29);
    \u0275\u0275text(1, "\u041D\u0435 \u043D\u0430\u0437\u043D\u0430\u0447\u0435\u043D");
    \u0275\u0275elementEnd();
  }
}
function HeadmanSubjectsComponent_Conditional_18_td_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "td", 26);
    \u0275\u0275template(1, HeadmanSubjectsComponent_Conditional_18_td_9_Conditional_1_Template, 2, 1, "span")(2, HeadmanSubjectsComponent_Conditional_18_td_9_Conditional_2_Template, 2, 0, "span", 29);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const s_r5 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.teacherNames(s_r5).length > 0 ? 1 : 2);
  }
}
function HeadmanSubjectsComponent_Conditional_18_th_11_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "th", 25);
  }
}
function HeadmanSubjectsComponent_Conditional_18_td_12_Template(rf, ctx) {
  if (rf & 1) {
    const _r6 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "td", 26)(1, "button", 30);
    \u0275\u0275listener("click", function HeadmanSubjectsComponent_Conditional_18_td_12_Template_button_click_1_listener() {
      const s_r7 = \u0275\u0275restoreView(_r6).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.openEditDialog(s_r7));
    });
    \u0275\u0275element(2, "i", 31);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "button", 32);
    \u0275\u0275listener("click", function HeadmanSubjectsComponent_Conditional_18_td_12_Template_button_click_3_listener() {
      const s_r7 = \u0275\u0275restoreView(_r6).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.openDeleteDialog(s_r7));
    });
    \u0275\u0275element(4, "i", 33);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const s_r7 = ctx.$implicit;
    \u0275\u0275advance();
    \u0275\u0275attribute("aria-label", "\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442 " + s_r7.name);
    \u0275\u0275advance(2);
    \u0275\u0275attribute("aria-label", "\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442 " + s_r7.name);
  }
}
function HeadmanSubjectsComponent_Conditional_18_tr_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "tr", 34);
  }
}
function HeadmanSubjectsComponent_Conditional_18_tr_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "tr", 35);
  }
}
function HeadmanSubjectsComponent_Conditional_18_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "table", 11);
    \u0275\u0275elementContainerStart(1, 17);
    \u0275\u0275template(2, HeadmanSubjectsComponent_Conditional_18_th_2_Template, 2, 0, "th", 18)(3, HeadmanSubjectsComponent_Conditional_18_td_3_Template, 3, 1, "td", 19);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(4, 20);
    \u0275\u0275template(5, HeadmanSubjectsComponent_Conditional_18_th_5_Template, 2, 0, "th", 18)(6, HeadmanSubjectsComponent_Conditional_18_td_6_Template, 3, 1, "td", 19);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(7, 21);
    \u0275\u0275template(8, HeadmanSubjectsComponent_Conditional_18_th_8_Template, 2, 0, "th", 18)(9, HeadmanSubjectsComponent_Conditional_18_td_9_Template, 3, 1, "td", 19);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(10, 22);
    \u0275\u0275template(11, HeadmanSubjectsComponent_Conditional_18_th_11_Template, 1, 0, "th", 18)(12, HeadmanSubjectsComponent_Conditional_18_td_12_Template, 5, 2, "td", 19);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275template(13, HeadmanSubjectsComponent_Conditional_18_tr_13_Template, 1, 0, "tr", 23)(14, HeadmanSubjectsComponent_Conditional_18_tr_14_Template, 1, 0, "tr", 24);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275property("dataSource", ctx_r0.subjects());
    \u0275\u0275advance(13);
    \u0275\u0275property("matHeaderRowDef", \u0275\u0275pureFunction0(3, _c1));
    \u0275\u0275advance();
    \u0275\u0275property("matRowDefColumns", \u0275\u0275pureFunction0(4, _c1));
  }
}
var SUBJECT_TYPE_LABELS = {
  LECTURE: "\u041B\u0435\u043A\u0446\u0438\u044F",
  PRACTICE: "\u041F\u0440\u0430\u043A\u0442\u0438\u043A\u0430",
  LAB: "\u041B\u0430\u0431\u043E\u0440\u0430\u0442\u043E\u0440\u043D\u0430\u044F"
};
var HeadmanSubjectsComponent = class _HeadmanSubjectsComponent {
  headmanApi = inject(HeadmanApiService);
  dialog = inject(MatDialog);
  snackBar = inject(MatSnackBar);
  loading = signal(false);
  error = signal(null);
  subjects = signal([]);
  teachersById = signal(/* @__PURE__ */ new Map());
  ngOnInit() {
    this.loadSubjects();
  }
  loadSubjects() {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      subjects: this.headmanApi.listSubjects(),
      teachers: this.headmanApi.listTeachers().pipe(catchError(() => of(null)))
    }).subscribe({
      next: ({ subjects, teachers }) => {
        const subjList = Object.values(subjects?._embedded ?? {})[0] ?? [];
        this.subjects.set(subjList);
        const tList = teachers ? Object.values(teachers?._embedded ?? {})[0] ?? [] : [];
        const map = /* @__PURE__ */ new Map();
        for (const t of tList) {
          if (t?.id != null)
            map.set(t.id, t);
        }
        this.teachersById.set(map);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u044B. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043E\u0431\u043D\u043E\u0432\u0438\u0442\u044C \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0443.");
        this.loading.set(false);
      }
    });
  }
  /** Map subject.teacherIds[] → отображаемые ФИО (фолбэк на lastName). */
  teacherNames(subject) {
    const ids = subject?.teacherIds ?? [];
    if (!ids.length)
      return [];
    const map = this.teachersById();
    return ids.map((id) => map.get(id)).filter(Boolean).map((t) => t.fullName ?? t.lastName ?? `#${t.id}`);
  }
  subjectTypeLabel(type) {
    return SUBJECT_TYPE_LABELS[type] ?? type;
  }
  openCreateDialog() {
    const ref = this.dialog.open(SubjectDialogComponent, {
      width: "480px",
      maxWidth: "95vw",
      ariaLabel: "\u041D\u043E\u0432\u044B\u0439 \u043F\u0440\u0435\u0434\u043C\u0435\u0442",
      data: { mode: "create" }
    });
    ref.afterClosed().subscribe((result) => {
      if (result)
        this.loadSubjects();
    });
  }
  openEditDialog(subject) {
    const ref = this.dialog.open(SubjectDialogComponent, {
      width: "480px",
      maxWidth: "95vw",
      ariaLabel: "\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442",
      data: { mode: "edit", subject }
    });
    ref.afterClosed().subscribe((result) => {
      if (result)
        this.loadSubjects();
    });
  }
  openDeleteDialog(subject) {
    const ref = this.dialog.open(DeleteSubjectDialogComponent, {
      data: { subjectName: subject.name, cascade: null },
      ariaLabel: "\u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043D\u0438\u0435 \u0443\u0434\u0430\u043B\u0435\u043D\u0438\u044F"
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed)
        return;
      this.attemptDelete(subject, false);
    });
  }
  /**
   * Пытается удалить предмет. На 409 с extras — открывает второй диалог с
   * предупреждением о потере данных посещаемости. Если староста подтверждает,
   * повторный вызов уходит с force=true.
   */
  attemptDelete(subject, force) {
    this.headmanApi.deleteSubject(subject.id, force).subscribe({
      next: () => {
        this.snackBar.open("\u041F\u0440\u0435\u0434\u043C\u0435\u0442 \u0443\u0434\u0430\u043B\u0451\u043D.", void 0, { duration: 4e3 });
        this.loadSubjects();
      },
      error: (err) => {
        const extras = err?.error?.extras;
        if (err.status === 409 && extras && typeof extras === "object") {
          const ref = this.dialog.open(DeleteSubjectDialogComponent, {
            data: {
              subjectName: subject.name,
              cascade: {
                scheduleItemsCount: Number(extras.scheduleItemsCount ?? 0),
                oneOffLessonsCount: Number(extras.oneOffLessonsCount ?? 0),
                nonPlannedLessonsCount: Number(extras.nonPlannedLessonsCount ?? 0),
                totalLessonsCount: Number(extras.totalLessonsCount ?? 0)
              }
            },
            width: "520px",
            ariaLabel: "\u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043D\u0438\u0435 \u0443\u0434\u0430\u043B\u0435\u043D\u0438\u044F \u0441 \u0434\u0430\u043D\u043D\u044B\u043C\u0438 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438"
          });
          ref.afterClosed().subscribe((confirmed) => {
            if (confirmed)
              this.attemptDelete(subject, true);
          });
          return;
        }
        if (err.status === 409) {
          this.snackBar.open("\u041D\u0435\u043B\u044C\u0437\u044F \u0443\u0434\u0430\u043B\u0438\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442 \u0441 \u0437\u0430\u043F\u0438\u0441\u044F\u043C\u0438 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438.", void 0, { duration: 6e3 });
          return;
        }
        this.snackBar.open("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0443\u0434\u0430\u043B\u0438\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442.", void 0, { duration: 6e3 });
      }
    });
  }
  static \u0275fac = function HeadmanSubjectsComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanSubjectsComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanSubjectsComponent, selectors: [["app-headman-subjects"]], decls: 19, vars: 2, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-eyebrow"], [1, "page-title"], [1, "page-header__actions"], [1, "btn-brand", 3, "click"], [1, "ph", "ph-plus"], [1, "page-card", "page-card--flush"], [1, "page-card__header"], [1, "page-error"], [1, "page-empty"], ["mat-table", "", 3, "dataSource"], ["class", "skeleton-row", "aria-hidden", "true", 4, "ngFor", "ngForOf"], ["aria-live", "polite", 1, "sr-only"], ["aria-hidden", "true", 1, "skeleton-row"], [1, "ph", "ph-warning-circle"], [1, "ph-duotone", "ph-books"], ["matColumnDef", "name"], ["mat-header-cell", "", 4, "matHeaderCellDef"], ["mat-cell", "", 4, "matCellDef"], ["matColumnDef", "type"], ["matColumnDef", "teacher"], ["matColumnDef", "actions"], ["mat-header-row", "", 4, "matHeaderRowDef"], ["mat-row", "", 4, "matRowDef", "matRowDefColumns"], ["mat-header-cell", ""], ["mat-cell", ""], [1, "subject-name"], [1, "type-chip"], [1, "no-teacher"], [1, "icon-btn", 3, "click"], [1, "ph", "ph-pencil-simple"], [1, "icon-btn", "icon-btn--danger", 3, "click"], [1, "ph", "ph-trash"], ["mat-header-row", ""], ["mat-row", ""]], template: function HeadmanSubjectsComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "div")(3, "span", 2);
      \u0275\u0275text(4, "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "h1", 3);
      \u0275\u0275text(6, "\u041F\u0440\u0435\u0434\u043C\u0435\u0442\u044B \u0433\u0440\u0443\u043F\u043F\u044B");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(7, "div", 4)(8, "button", 5);
      \u0275\u0275listener("click", function HeadmanSubjectsComponent_Template_button_click_8_listener() {
        return ctx.openCreateDialog();
      });
      \u0275\u0275element(9, "i", 6);
      \u0275\u0275text(10, " \u0414\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442 ");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(11, "div", 7)(12, "div", 8)(13, "h2");
      \u0275\u0275text(14, "\u041F\u0440\u0435\u0434\u043C\u0435\u0442\u044B");
      \u0275\u0275elementEnd()();
      \u0275\u0275template(15, HeadmanSubjectsComponent_Conditional_15_Template, 3, 2)(16, HeadmanSubjectsComponent_Conditional_16_Template, 3, 1, "div", 9)(17, HeadmanSubjectsComponent_Conditional_17_Template, 8, 0, "div", 10)(18, HeadmanSubjectsComponent_Conditional_18_Template, 15, 5, "table", 11);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(15);
      \u0275\u0275conditional(ctx.loading() ? 15 : ctx.error() ? 16 : ctx.subjects().length === 0 ? 17 : 18);
    }
  }, dependencies: [
    CommonModule,
    NgForOf,
    RouterModule,
    MatTableModule,
    MatTable,
    MatHeaderCellDef,
    MatHeaderRowDef,
    MatColumnDef,
    MatCellDef,
    MatRowDef,
    MatHeaderCell,
    MatCell,
    MatHeaderRow,
    MatRow,
    MatButtonModule,
    MatIconModule
  ], styles: ["\n\n.no-teacher[_ngcontent-%COMP%] {\n  font-style: italic;\n  color: var(--text-muted);\n}\n.type-chip[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  padding: 4px 10px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-secondary) 14%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-secondary) 28%, transparent);\n  color: var(--accent-secondary);\n  font: 500 0.6875rem/1 var(--font-mono);\n  letter-spacing: 0.04em;\n  text-transform: uppercase;\n  white-space: nowrap;\n}\n.skeleton-row[_ngcontent-%COMP%] {\n  height: 52px;\n  margin: 4px 0;\n  border-radius: 4px;\n  background:\n    linear-gradient(\n      90deg,\n      var(--bg-secondary) 25%,\n      var(--bg-elevated) 50%,\n      var(--bg-secondary) 75%);\n  background-size: 200% 100%;\n  animation: _ngcontent-%COMP%_dashboard-shimmer 1.5s infinite;\n}\n@keyframes _ngcontent-%COMP%_dashboard-shimmer {\n  0% {\n    background-position: 200% 0;\n  }\n  100% {\n    background-position: -200% 0;\n  }\n}\n/*# sourceMappingURL=headman-subjects.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanSubjectsComponent, [{
    type: Component,
    args: [{ selector: "app-headman-subjects", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [
      CommonModule,
      NgForOf,
      RouterModule,
      MatTableModule,
      MatButtonModule,
      MatIconModule
    ], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: `
    <div class="page-stack" [@routeFade]>
      <!-- Page header -->
      <div class="page-header">
        <div>
          <span class="page-eyebrow">\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442</span>
          <h1 class="page-title">\u041F\u0440\u0435\u0434\u043C\u0435\u0442\u044B \u0433\u0440\u0443\u043F\u043F\u044B</h1>
        </div>
        <div class="page-header__actions">
          <button class="btn-brand" (click)="openCreateDialog()">
            <i class="ph ph-plus"></i> \u0414\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442
          </button>
        </div>
      </div>

      <!-- Subject list card -->
      <div class="page-card page-card--flush">
        <div class="page-card__header">
          <h2>\u041F\u0440\u0435\u0434\u043C\u0435\u0442\u044B</h2>
        </div>

        @if (loading()) {
          <div class="skeleton-row" *ngFor="let i of [1,2,3,4]" aria-hidden="true"></div>
          <span aria-live="polite" class="sr-only">\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...</span>
        }
        @else if (error()) {
          <div class="page-error"><i class="ph ph-warning-circle"></i>{{ error() }}</div>
        }
        @else if (subjects().length === 0) {
          <div class="page-empty">
            <i class="ph-duotone ph-books"></i>
            <h3>\u041D\u0435\u0442 \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u043E\u0432</h3>
            <p>\u0414\u043E\u0431\u0430\u0432\u044C\u0442\u0435 \u043F\u0435\u0440\u0432\u044B\u0439 \u043F\u0440\u0435\u0434\u043C\u0435\u0442, \u0447\u0442\u043E\u0431\u044B \u043D\u0430\u0447\u0430\u0442\u044C \u0432\u0435\u0441\u0442\u0438 \u0436\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438.</p>
            <button class="btn-brand" (click)="openCreateDialog()">\u0414\u043E\u0431\u0430\u0432\u0438\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442</button>
          </div>
        }
        @else {
          <table mat-table [dataSource]="subjects()">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>\u041F\u0440\u0435\u0434\u043C\u0435\u0442</th>
              <td mat-cell *matCellDef="let s">
                <span class="subject-name">{{ s.name }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="type">
              <th mat-header-cell *matHeaderCellDef>\u0422\u0438\u043F</th>
              <td mat-cell *matCellDef="let s">
                @if (s.type) {
                  <span class="type-chip">{{ subjectTypeLabel(s.type) }}</span>
                } @else {
                  <span class="no-teacher">\u2014</span>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="teacher">
              <th mat-header-cell *matHeaderCellDef>\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u0438</th>
              <td mat-cell *matCellDef="let s">
                @if (teacherNames(s).length > 0) {
                  <span>{{ teacherNames(s).join(', ') }}</span>
                } @else {
                  <span class="no-teacher">\u041D\u0435 \u043D\u0430\u0437\u043D\u0430\u0447\u0435\u043D</span>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let s">
                <button class="icon-btn"
                        [attr.aria-label]="'\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442 ' + s.name"
                        (click)="openEditDialog(s)">
                  <i class="ph ph-pencil-simple"></i>
                </button>
                <button class="icon-btn icon-btn--danger"
                        [attr.aria-label]="'\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442 ' + s.name"
                        (click)="openDeleteDialog(s)">
                  <i class="ph ph-trash"></i>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="['name','type','teacher','actions']"></tr>
            <tr mat-row *matRowDef="let row; columns: ['name','type','teacher','actions']"></tr>
          </table>
        }
      </div>
    </div>
  `, styles: ["/* angular:styles/component:css;25e63cd2ac1b5c7563003c5d33f6c66ecc1b1aa0ccd6d5e9740f3ca3194d86de;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/subjects/headman-subjects.component.ts */\n.no-teacher {\n  font-style: italic;\n  color: var(--text-muted);\n}\n.type-chip {\n  display: inline-flex;\n  align-items: center;\n  padding: 4px 10px;\n  border-radius: var(--radius-full);\n  background: color-mix(in oklab, var(--accent-secondary) 14%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-secondary) 28%, transparent);\n  color: var(--accent-secondary);\n  font: 500 0.6875rem/1 var(--font-mono);\n  letter-spacing: 0.04em;\n  text-transform: uppercase;\n  white-space: nowrap;\n}\n.skeleton-row {\n  height: 52px;\n  margin: 4px 0;\n  border-radius: 4px;\n  background:\n    linear-gradient(\n      90deg,\n      var(--bg-secondary) 25%,\n      var(--bg-elevated) 50%,\n      var(--bg-secondary) 75%);\n  background-size: 200% 100%;\n  animation: dashboard-shimmer 1.5s infinite;\n}\n@keyframes dashboard-shimmer {\n  0% {\n    background-position: 200% 0;\n  }\n  100% {\n    background-position: -200% 0;\n  }\n}\n/*# sourceMappingURL=headman-subjects.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanSubjectsComponent, { className: "HeadmanSubjectsComponent", filePath: "src/app/features/headman/subjects/headman-subjects.component.ts", lineNumber: 180 });
})();
export {
  HeadmanSubjectsComponent
};
//# sourceMappingURL=chunk-3DVFBTTC.js.map
