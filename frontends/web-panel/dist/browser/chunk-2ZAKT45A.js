import {
  MatIconModule
} from "./chunk-CZJGELVO.js";
import {
  MAT_DIALOG_DATA,
  MatCell,
  MatCellDef,
  MatColumnDef,
  MatDialog,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogModule,
  MatDialogRef,
  MatDialogTitle,
  MatHeaderCell,
  MatHeaderCellDef,
  MatHeaderRow,
  MatHeaderRowDef,
  MatRow,
  MatRowDef,
  MatSnackBar,
  MatTable,
  MatTableModule,
  MatTooltip,
  MatTooltipModule
} from "./chunk-A4C7LUEI.js";
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerModule,
  MatDatepickerToggle
} from "./chunk-IYM3FV6R.js";
import "./chunk-U7ADN4CW.js";
import "./chunk-LKDOKLLT.js";
import {
  MatInput,
  MatInputModule
} from "./chunk-XDHRGA6B.js";
import "./chunk-GDZ3HOTB.js";
import {
  MatProgressBar,
  MatProgressBarModule
} from "./chunk-WFRMJ3YJ.js";
import {
  MatButton,
  MatButtonModule,
  MatError,
  MatFormField,
  MatFormFieldModule,
  MatIconButton,
  MatLabel,
  MatSuffix
} from "./chunk-SDENFCBC.js";
import {
  DefaultValueAccessor,
  FormBuilder,
  FormControlName,
  FormGroupDirective,
  FormsModule,
  NgControlStatus,
  NgControlStatusGroup,
  NgModel,
  ReactiveFormsModule,
  Validators,
  ɵNgNoValidate
} from "./chunk-52TXXDGC.js";
import "./chunk-QNELF7JG.js";
import "./chunk-G7PFIUH5.js";
import "./chunk-NITJSJ4E.js";
import {
  AdminApiService
} from "./chunk-W5VNZ3AB.js";
import "./chunk-LGL42SQS.js";
import "./chunk-T4R75CF5.js";
import {
  CommonModule
} from "./chunk-L2FQVI4C.js";
import {
  Component,
  inject,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵclassMap,
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
  ɵɵreference,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1,
  ɵɵtwoWayBindingSet,
  ɵɵtwoWayListener,
  ɵɵtwoWayProperty
} from "./chunk-WGFJ2PWY.js";

// src/app/features/admin/shared/types.ts
function deriveSemesterStatus(s) {
  if (s.active)
    return "active";
  const today = (/* @__PURE__ */ new Date()).toISOString().slice(0, 10);
  return s.dateFrom > today ? "planned" : "finished";
}

// src/app/features/admin/semesters/semester-dialog/semester-dialog.component.ts
function SemesterDialogComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u041E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043F\u043E\u043B\u0435");
    \u0275\u0275elementEnd();
  }
}
function SemesterDialogComponent_Conditional_16_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u041E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043F\u043E\u043B\u0435");
    \u0275\u0275elementEnd();
  }
}
function SemesterDialogComponent_Conditional_24_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u041E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043F\u043E\u043B\u0435");
    \u0275\u0275elementEnd();
  }
}
function SemesterDialogComponent_Conditional_25_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u0414\u0430\u0442\u0430 \u043E\u043A\u043E\u043D\u0447\u0430\u043D\u0438\u044F \u0434\u043E\u043B\u0436\u043D\u0430 \u0431\u044B\u0442\u044C \u043F\u043E\u0437\u0436\u0435 \u0434\u0430\u0442\u044B \u043D\u0430\u0447\u0430\u043B\u0430");
    \u0275\u0275elementEnd();
  }
}
function formatDateToString(d) {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}
function parseDateString(s) {
  const [year, month, day] = s.split("-").map(Number);
  return new Date(year, month - 1, day);
}
var SemesterDialogComponent = class _SemesterDialogComponent {
  data = inject(MAT_DIALOG_DATA);
  dialogRef = inject(MatDialogRef);
  adminApi = inject(AdminApiService);
  fb = inject(FormBuilder);
  saving = false;
  form = this.fb.group({
    name: ["", Validators.required],
    dateFrom: [null, Validators.required],
    dateTo: [null, Validators.required]
  }, { validators: [this.dateRangeValidator] });
  get isEdit() {
    return this.data.mode === "edit";
  }
  ngOnInit() {
    if (this.isEdit && this.data.semester) {
      this.form.patchValue({
        name: this.data.semester.name,
        dateFrom: parseDateString(this.data.semester.dateFrom),
        dateTo: parseDateString(this.data.semester.dateTo)
      });
    }
  }
  dateRangeValidator(control) {
    const from = control.get("dateFrom")?.value;
    const to = control.get("dateTo")?.value;
    if (from && to && to <= from) {
      return { dateRange: true };
    }
    return null;
  }
  save() {
    if (this.form.invalid || this.saving)
      return;
    this.saving = true;
    const { name, dateFrom, dateTo } = this.form.value;
    const payload = {
      name,
      dateFrom: formatDateToString(dateFrom),
      dateTo: formatDateToString(dateTo)
    };
    if (this.isEdit && this.data.semester) {
      this.adminApi.updateSemester(this.data.semester.id, payload).subscribe({
        next: () => this.dialogRef.close(true),
        error: () => this.saving = false
      });
    } else {
      this.adminApi.createSemester(payload).subscribe({
        next: () => this.dialogRef.close(true),
        error: () => this.saving = false
      });
    }
  }
  static \u0275fac = function SemesterDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _SemesterDialogComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _SemesterDialogComponent, selectors: [["app-semester-dialog"]], decls: 31, vars: 12, consts: [["pickerFrom", ""], ["pickerTo", ""], ["mat-dialog-title", ""], [1, "flex", "flex-col", "gap-4", 3, "formGroup"], [1, "w-full"], ["matInput", "", "formControlName", "name"], ["matInput", "", "formControlName", "dateFrom", 3, "matDatepicker"], ["matIconSuffix", "", 3, "for"], ["matInput", "", "formControlName", "dateTo", 3, "matDatepicker"], ["align", "end"], ["mat-button", "", "mat-dialog-close", ""], ["mat-flat-button", "", "color", "primary", 3, "click", "disabled"]], template: function SemesterDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      const _r1 = \u0275\u0275getCurrentView();
      \u0275\u0275elementStart(0, "h2", 2);
      \u0275\u0275text(1);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(2, "mat-dialog-content")(3, "form", 3)(4, "mat-form-field", 4)(5, "mat-label");
      \u0275\u0275text(6, "\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435");
      \u0275\u0275elementEnd();
      \u0275\u0275element(7, "input", 5);
      \u0275\u0275template(8, SemesterDialogComponent_Conditional_8_Template, 2, 0, "mat-error");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(9, "mat-form-field", 4)(10, "mat-label");
      \u0275\u0275text(11, "\u0414\u0430\u0442\u0430 \u043D\u0430\u0447\u0430\u043B\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275element(12, "input", 6)(13, "mat-datepicker-toggle", 7)(14, "mat-datepicker", null, 0);
      \u0275\u0275template(16, SemesterDialogComponent_Conditional_16_Template, 2, 0, "mat-error");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(17, "mat-form-field", 4)(18, "mat-label");
      \u0275\u0275text(19, "\u0414\u0430\u0442\u0430 \u043E\u043A\u043E\u043D\u0447\u0430\u043D\u0438\u044F");
      \u0275\u0275elementEnd();
      \u0275\u0275element(20, "input", 8)(21, "mat-datepicker-toggle", 7)(22, "mat-datepicker", null, 1);
      \u0275\u0275template(24, SemesterDialogComponent_Conditional_24_Template, 2, 0, "mat-error")(25, SemesterDialogComponent_Conditional_25_Template, 2, 0, "mat-error");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(26, "mat-dialog-actions", 9)(27, "button", 10);
      \u0275\u0275text(28, "\u041E\u0442\u043C\u0435\u043D\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(29, "button", 11);
      \u0275\u0275listener("click", function SemesterDialogComponent_Template_button_click_29_listener() {
        \u0275\u0275restoreView(_r1);
        return \u0275\u0275resetView(ctx.save());
      });
      \u0275\u0275text(30);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      const pickerFrom_r2 = \u0275\u0275reference(15);
      const pickerTo_r3 = \u0275\u0275reference(23);
      \u0275\u0275advance();
      \u0275\u0275textInterpolate(ctx.isEdit ? "\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u0441\u0435\u043C\u0435\u0441\u0442\u0440" : "\u041D\u043E\u0432\u044B\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440");
      \u0275\u0275advance(2);
      \u0275\u0275property("formGroup", ctx.form);
      \u0275\u0275advance(5);
      \u0275\u0275conditional(ctx.form.controls.name.hasError("required") ? 8 : -1);
      \u0275\u0275advance(4);
      \u0275\u0275property("matDatepicker", pickerFrom_r2);
      \u0275\u0275advance();
      \u0275\u0275property("for", pickerFrom_r2);
      \u0275\u0275advance(3);
      \u0275\u0275conditional(ctx.form.controls.dateFrom.hasError("required") ? 16 : -1);
      \u0275\u0275advance(4);
      \u0275\u0275property("matDatepicker", pickerTo_r3);
      \u0275\u0275advance();
      \u0275\u0275property("for", pickerTo_r3);
      \u0275\u0275advance(3);
      \u0275\u0275conditional(ctx.form.controls.dateTo.hasError("required") ? 24 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.form.hasError("dateRange") ? 25 : -1);
      \u0275\u0275advance(4);
      \u0275\u0275property("disabled", ctx.form.invalid || ctx.saving);
      \u0275\u0275advance();
      \u0275\u0275textInterpolate1(" ", ctx.isEdit ? "\u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F" : "\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0441\u0435\u043C\u0435\u0441\u0442\u0440", " ");
    }
  }, dependencies: [
    CommonModule,
    ReactiveFormsModule,
    \u0275NgNoValidate,
    DefaultValueAccessor,
    NgControlStatus,
    NgControlStatusGroup,
    FormGroupDirective,
    FormControlName,
    MatDialogModule,
    MatDialogClose,
    MatDialogTitle,
    MatDialogActions,
    MatDialogContent,
    MatFormFieldModule,
    MatFormField,
    MatLabel,
    MatError,
    MatSuffix,
    MatInputModule,
    MatInput,
    MatButtonModule,
    MatButton,
    MatDatepickerModule,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle
  ], encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(SemesterDialogComponent, [{
    type: Component,
    args: [{ selector: "app-semester-dialog", standalone: true, imports: [
      CommonModule,
      ReactiveFormsModule,
      MatDialogModule,
      MatFormFieldModule,
      MatInputModule,
      MatButtonModule,
      MatDatepickerModule
    ], template: `<h2 mat-dialog-title>{{ isEdit ? '\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u0441\u0435\u043C\u0435\u0441\u0442\u0440' : '\u041D\u043E\u0432\u044B\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440' }}</h2>
<mat-dialog-content>
  <form [formGroup]="form" class="flex flex-col gap-4">
    <mat-form-field class="w-full">
      <mat-label>\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435</mat-label>
      <input matInput formControlName="name">
      @if (form.controls.name.hasError('required')) {
        <mat-error>\u041E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043F\u043E\u043B\u0435</mat-error>
      }
    </mat-form-field>

    <mat-form-field class="w-full">
      <mat-label>\u0414\u0430\u0442\u0430 \u043D\u0430\u0447\u0430\u043B\u0430</mat-label>
      <input matInput [matDatepicker]="pickerFrom" formControlName="dateFrom">
      <mat-datepicker-toggle matIconSuffix [for]="pickerFrom"></mat-datepicker-toggle>
      <mat-datepicker #pickerFrom></mat-datepicker>
      @if (form.controls.dateFrom.hasError('required')) {
        <mat-error>\u041E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043F\u043E\u043B\u0435</mat-error>
      }
    </mat-form-field>

    <mat-form-field class="w-full">
      <mat-label>\u0414\u0430\u0442\u0430 \u043E\u043A\u043E\u043D\u0447\u0430\u043D\u0438\u044F</mat-label>
      <input matInput [matDatepicker]="pickerTo" formControlName="dateTo">
      <mat-datepicker-toggle matIconSuffix [for]="pickerTo"></mat-datepicker-toggle>
      <mat-datepicker #pickerTo></mat-datepicker>
      @if (form.controls.dateTo.hasError('required')) {
        <mat-error>\u041E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043F\u043E\u043B\u0435</mat-error>
      }
      @if (form.hasError('dateRange')) {
        <mat-error>\u0414\u0430\u0442\u0430 \u043E\u043A\u043E\u043D\u0447\u0430\u043D\u0438\u044F \u0434\u043E\u043B\u0436\u043D\u0430 \u0431\u044B\u0442\u044C \u043F\u043E\u0437\u0436\u0435 \u0434\u0430\u0442\u044B \u043D\u0430\u0447\u0430\u043B\u0430</mat-error>
      }
    </mat-form-field>
  </form>
</mat-dialog-content>
<mat-dialog-actions align="end">
  <button mat-button mat-dialog-close>\u041E\u0442\u043C\u0435\u043D\u0430</button>
  <button mat-flat-button color="primary" [disabled]="form.invalid || saving" (click)="save()">
    {{ isEdit ? '\u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F' : '\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0441\u0435\u043C\u0435\u0441\u0442\u0440' }}
  </button>
</mat-dialog-actions>
` }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(SemesterDialogComponent, { className: "SemesterDialogComponent", filePath: "src/app/features/admin/semesters/semester-dialog/semester-dialog.component.ts", lineNumber: 44 });
})();

// src/app/features/admin/semesters/delete-semester-dialog/delete-semester-dialog.component.ts
var DeleteSemesterDialogComponent = class _DeleteSemesterDialogComponent {
  data = inject(MAT_DIALOG_DATA);
  dialogRef = inject(MatDialogRef);
  confirmInput = "";
  confirm() {
    this.dialogRef.close(this.confirmInput.trim());
  }
  static \u0275fac = function DeleteSemesterDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _DeleteSemesterDialogComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _DeleteSemesterDialogComponent, selectors: [["app-delete-semester-dialog"]], decls: 14, vars: 3, consts: [["mat-dialog-title", ""], [1, "mb-4"], [1, "w-full"], ["matInput", "", "autocomplete", "off", 3, "ngModelChange", "ngModel"], ["align", "end"], ["mat-button", "", "mat-dialog-close", ""], ["mat-flat-button", "", "color", "warn", 3, "click", "disabled"]], template: function DeleteSemesterDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "h2", 0);
      \u0275\u0275text(1, "\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u0441\u0435\u043C\u0435\u0441\u0442\u0440?");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(2, "mat-dialog-content")(3, "p", 1);
      \u0275\u0275text(4, "\u042D\u0442\u043E \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435 \u043D\u0435\u043E\u0431\u0440\u0430\u0442\u0438\u043C\u043E. \u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u043D\u0430\u0437\u0432\u0430\u043D\u0438\u0435 \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u0430 \u0434\u043B\u044F \u043F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043D\u0438\u044F:");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "mat-form-field", 2)(6, "mat-label");
      \u0275\u0275text(7);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(8, "input", 3);
      \u0275\u0275twoWayListener("ngModelChange", function DeleteSemesterDialogComponent_Template_input_ngModelChange_8_listener($event) {
        \u0275\u0275twoWayBindingSet(ctx.confirmInput, $event) || (ctx.confirmInput = $event);
        return $event;
      });
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(9, "mat-dialog-actions", 4)(10, "button", 5);
      \u0275\u0275text(11, "\u041E\u0442\u043C\u0435\u043D\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(12, "button", 6);
      \u0275\u0275listener("click", function DeleteSemesterDialogComponent_Template_button_click_12_listener() {
        return ctx.confirm();
      });
      \u0275\u0275text(13, "\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u043D\u0430\u0432\u0441\u0435\u0433\u0434\u0430");
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275advance(7);
      \u0275\u0275textInterpolate1("\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \xAB", ctx.data.semester.name, "\xBB");
      \u0275\u0275advance();
      \u0275\u0275twoWayProperty("ngModel", ctx.confirmInput);
      \u0275\u0275advance(4);
      \u0275\u0275property("disabled", ctx.confirmInput.trim() !== ctx.data.semester.name);
    }
  }, dependencies: [MatDialogModule, MatDialogClose, MatDialogTitle, MatDialogActions, MatDialogContent, MatButtonModule, MatButton, MatFormFieldModule, MatFormField, MatLabel, MatInputModule, MatInput, FormsModule, DefaultValueAccessor, NgControlStatus, NgModel], encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(DeleteSemesterDialogComponent, [{
    type: Component,
    args: [{
      selector: "app-delete-semester-dialog",
      standalone: true,
      imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, FormsModule],
      template: `
    <h2 mat-dialog-title>\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u0441\u0435\u043C\u0435\u0441\u0442\u0440?</h2>
    <mat-dialog-content>
      <p class="mb-4">\u042D\u0442\u043E \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435 \u043D\u0435\u043E\u0431\u0440\u0430\u0442\u0438\u043C\u043E. \u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u043D\u0430\u0437\u0432\u0430\u043D\u0438\u0435 \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u0430 \u0434\u043B\u044F \u043F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043D\u0438\u044F:</p>
      <mat-form-field class="w-full">
        <mat-label>\u0412\u0432\u0435\u0434\u0438\u0442\u0435 &laquo;{{ data.semester.name }}&raquo;</mat-label>
        <input matInput [(ngModel)]="confirmInput" autocomplete="off">
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>\u041E\u0442\u043C\u0435\u043D\u0430</button>
      <button mat-flat-button color="warn"
        [disabled]="confirmInput.trim() !== data.semester.name"
        (click)="confirm()">\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u043D\u0430\u0432\u0441\u0435\u0433\u0434\u0430</button>
    </mat-dialog-actions>
  `
    }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(DeleteSemesterDialogComponent, { className: "DeleteSemesterDialogComponent", filePath: "src/app/features/admin/semesters/delete-semester-dialog/delete-semester-dialog.component.ts", lineNumber: 31 });
})();

// src/app/features/admin/semesters/semesters-page.component.ts
function SemestersPageComponent_Conditional_11_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-progress-bar", 7);
  }
}
function SemestersPageComponent_Conditional_12_Template(rf, ctx) {
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
function SemestersPageComponent_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 9)(1, "div", 12)(2, "div", 13);
    \u0275\u0275element(3, "i", 14);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "h3", 15);
    \u0275\u0275text(5, "\u0421\u0435\u043C\u0435\u0441\u0442\u0440\u044B \u043D\u0435 \u0441\u043E\u0437\u0434\u0430\u043D\u044B");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "p", 16);
    \u0275\u0275text(7, "\u0421\u043E\u0437\u0434\u0430\u0439\u0442\u0435 \u043F\u0435\u0440\u0432\u044B\u0439 \u0443\u0447\u0435\u0431\u043D\u044B\u0439 \u043F\u0435\u0440\u0438\u043E\u0434 \u0447\u0435\u0440\u0435\u0437 \u043A\u043D\u043E\u043F\u043A\u0443 \u0432\u044B\u0448\u0435.");
    \u0275\u0275elementEnd()()();
  }
}
function SemestersPageComponent_Conditional_14_mat_header_cell_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-header-cell");
    \u0275\u0275text(1, "\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435");
    \u0275\u0275elementEnd();
  }
}
function SemestersPageComponent_Conditional_14_mat_cell_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-cell");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const sem_r2 = ctx.$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(sem_r2.name);
  }
}
function SemestersPageComponent_Conditional_14_mat_header_cell_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-header-cell");
    \u0275\u0275text(1, "\u041D\u0430\u0447\u0430\u043B\u043E");
    \u0275\u0275elementEnd();
  }
}
function SemestersPageComponent_Conditional_14_mat_cell_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-cell");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const sem_r3 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.formatDate(sem_r3.dateFrom));
  }
}
function SemestersPageComponent_Conditional_14_mat_header_cell_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-header-cell");
    \u0275\u0275text(1, "\u041A\u043E\u043D\u0435\u0446");
    \u0275\u0275elementEnd();
  }
}
function SemestersPageComponent_Conditional_14_mat_cell_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-cell");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const sem_r4 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.formatDate(sem_r4.dateTo));
  }
}
function SemestersPageComponent_Conditional_14_mat_header_cell_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-header-cell");
    \u0275\u0275text(1, "\u0421\u0442\u0430\u0442\u0443\u0441");
    \u0275\u0275elementEnd();
  }
}
function SemestersPageComponent_Conditional_14_mat_cell_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-cell")(1, "span", 27);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const sem_r5 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275classMap("semester-status semester-status--" + ctx_r0.getStatus(sem_r5));
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r0.getStatusLabel(sem_r5), " ");
  }
}
function SemestersPageComponent_Conditional_14_mat_header_cell_15_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-header-cell");
  }
}
function SemestersPageComponent_Conditional_14_mat_cell_16_Template(rf, ctx) {
  if (rf & 1) {
    const _r6 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "mat-cell")(1, "button", 28);
    \u0275\u0275listener("click", function SemestersPageComponent_Conditional_14_mat_cell_16_Template_button_click_1_listener() {
      const sem_r7 = \u0275\u0275restoreView(_r6).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.openEditDialog(sem_r7));
    });
    \u0275\u0275element(2, "i", 29);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "button", 30);
    \u0275\u0275listener("click", function SemestersPageComponent_Conditional_14_mat_cell_16_Template_button_click_3_listener() {
      const sem_r7 = \u0275\u0275restoreView(_r6).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.deleteSemester(sem_r7));
    });
    \u0275\u0275element(4, "i", 31);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const sem_r7 = ctx.$implicit;
    \u0275\u0275advance(3);
    \u0275\u0275property("disabled", sem_r7.active)("matTooltip", sem_r7.active ? "\u041D\u0435\u043B\u044C\u0437\u044F \u0443\u0434\u0430\u043B\u0438\u0442\u044C \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440" : "\u0423\u0434\u0430\u043B\u0438\u0442\u044C");
    \u0275\u0275attribute("aria-label", sem_r7.active ? "\u041D\u0435\u043B\u044C\u0437\u044F \u0443\u0434\u0430\u043B\u0438\u0442\u044C \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440" : "\u0423\u0434\u0430\u043B\u0438\u0442\u044C");
  }
}
function SemestersPageComponent_Conditional_14_mat_header_row_17_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-header-row");
  }
}
function SemestersPageComponent_Conditional_14_mat_row_18_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-row");
  }
}
function SemestersPageComponent_Conditional_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 10)(1, "mat-table", 17);
    \u0275\u0275elementContainerStart(2, 18);
    \u0275\u0275template(3, SemestersPageComponent_Conditional_14_mat_header_cell_3_Template, 2, 0, "mat-header-cell", 19)(4, SemestersPageComponent_Conditional_14_mat_cell_4_Template, 2, 1, "mat-cell", 20);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(5, 21);
    \u0275\u0275template(6, SemestersPageComponent_Conditional_14_mat_header_cell_6_Template, 2, 0, "mat-header-cell", 19)(7, SemestersPageComponent_Conditional_14_mat_cell_7_Template, 2, 1, "mat-cell", 20);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(8, 22);
    \u0275\u0275template(9, SemestersPageComponent_Conditional_14_mat_header_cell_9_Template, 2, 0, "mat-header-cell", 19)(10, SemestersPageComponent_Conditional_14_mat_cell_10_Template, 2, 1, "mat-cell", 20);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(11, 23);
    \u0275\u0275template(12, SemestersPageComponent_Conditional_14_mat_header_cell_12_Template, 2, 0, "mat-header-cell", 19)(13, SemestersPageComponent_Conditional_14_mat_cell_13_Template, 3, 3, "mat-cell", 20);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(14, 24);
    \u0275\u0275template(15, SemestersPageComponent_Conditional_14_mat_header_cell_15_Template, 1, 0, "mat-header-cell", 19)(16, SemestersPageComponent_Conditional_14_mat_cell_16_Template, 5, 3, "mat-cell", 20);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275template(17, SemestersPageComponent_Conditional_14_mat_header_row_17_Template, 1, 0, "mat-header-row", 25)(18, SemestersPageComponent_Conditional_14_mat_row_18_Template, 1, 0, "mat-row", 26);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275property("dataSource", ctx_r0.semesters());
    \u0275\u0275advance(16);
    \u0275\u0275property("matHeaderRowDef", ctx_r0.displayedColumns);
    \u0275\u0275advance();
    \u0275\u0275property("matRowDefColumns", ctx_r0.displayedColumns);
  }
}
var SemestersPageComponent = class _SemestersPageComponent {
  adminApi = inject(AdminApiService);
  dialog = inject(MatDialog);
  snackBar = inject(MatSnackBar);
  semesters = signal([]);
  loading = signal(false);
  error = signal(null);
  displayedColumns = ["name", "dateFrom", "dateTo", "status", "actions"];
  ngOnInit() {
    this.reload();
  }
  reload() {
    this.loading.set(true);
    this.error.set(null);
    this.adminApi.listSemesters().subscribe({
      next: (semesters) => {
        this.semesters.set(semesters);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0441\u0435\u043C\u0435\u0441\u0442\u0440\u044B.");
        this.loading.set(false);
      }
    });
  }
  getStatus(sem) {
    return deriveSemesterStatus(sem);
  }
  getStatusLabel(sem) {
    const status = this.getStatus(sem);
    switch (status) {
      case "active":
        return "\u0410\u043A\u0442\u0438\u0432\u043D\u044B\u0439";
      case "planned":
        return "\u0417\u0430\u043F\u043B\u0430\u043D\u0438\u0440\u043E\u0432\u0430\u043D";
      case "finished":
        return "\u0417\u0430\u0432\u0435\u0440\u0448\u0451\u043D";
    }
  }
  formatDate(iso) {
    if (!iso)
      return "";
    const parts = iso.slice(0, 10).split("-");
    if (parts.length !== 3)
      return iso;
    return `${parts[2]}.${parts[1]}.${parts[0]}`;
  }
  openCreateDialog() {
    const ref = this.dialog.open(SemesterDialogComponent, {
      maxWidth: "480px",
      width: "100%",
      data: { mode: "create" }
    });
    ref.afterClosed().subscribe((result) => {
      if (result) {
        this.snackBar.open("\u0421\u0435\u043C\u0435\u0441\u0442\u0440 \u0441\u043E\u0437\u0434\u0430\u043D.", void 0, { duration: 4e3 });
        this.reload();
      }
    });
  }
  openEditDialog(sem) {
    const ref = this.dialog.open(SemesterDialogComponent, {
      maxWidth: "480px",
      width: "100%",
      data: { mode: "edit", semester: sem }
    });
    ref.afterClosed().subscribe((result) => {
      if (result) {
        this.snackBar.open("\u0421\u0435\u043C\u0435\u0441\u0442\u0440 \u043E\u0431\u043D\u043E\u0432\u043B\u0451\u043D.", void 0, { duration: 4e3 });
        this.reload();
      }
    });
  }
  deleteSemester(sem) {
    if (sem.active)
      return;
    const ref = this.dialog.open(DeleteSemesterDialogComponent, {
      maxWidth: "480px",
      width: "100%",
      data: { semester: sem }
    });
    ref.afterClosed().subscribe((confirmation) => {
      if (confirmation) {
        this.adminApi.deleteSemester(sem.id, confirmation).subscribe({
          next: () => {
            this.snackBar.open("\u0421\u0435\u043C\u0435\u0441\u0442\u0440 \u0443\u0434\u0430\u043B\u0451\u043D.", void 0, { duration: 4e3 });
            this.reload();
          },
          error: () => {
            this.snackBar.open("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0443\u0434\u0430\u043B\u0438\u0442\u044C \u0441\u0435\u043C\u0435\u0441\u0442\u0440.", void 0, { duration: 6e3 });
          }
        });
      }
    });
  }
  static \u0275fac = function SemestersPageComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _SemestersPageComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _SemestersPageComponent, selectors: [["app-semesters-page"]], decls: 15, vars: 3, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-header__title"], [1, "page-header__subtitle"], [1, "page-header__actions"], ["type", "button", 1, "btn-brand", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-plus"], ["mode", "indeterminate"], ["role", "alert", 1, "page-error"], [1, "page-card"], [1, "page-card", "page-card--flush"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"], [1, "page-empty"], ["aria-hidden", "true", 1, "page-empty__icon"], [1, "ph-duotone", "ph-calendar-blank"], [1, "page-empty__title"], [1, "page-empty__text"], [3, "dataSource"], ["matColumnDef", "name"], [4, "matHeaderCellDef"], [4, "matCellDef"], ["matColumnDef", "dateFrom"], ["matColumnDef", "dateTo"], ["matColumnDef", "status"], ["matColumnDef", "actions"], [4, "matHeaderRowDef"], [4, "matRowDef", "matRowDefColumns"], [1, "semester-status"], ["mat-icon-button", "", "matTooltip", "\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C", "aria-label", "\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-pencil-simple"], ["mat-icon-button", "", 3, "click", "disabled", "matTooltip"], ["aria-hidden", "true", 1, "ph", "ph-trash"]], template: function SemestersPageComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "header", 1)(2, "div", 2)(3, "h1");
      \u0275\u0275text(4, "\u0421\u0435\u043C\u0435\u0441\u0442\u0440\u044B");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "p", 3);
      \u0275\u0275text(6, " \u0423\u0447\u0435\u0431\u043D\u044B\u0435 \u043F\u0435\u0440\u0438\u043E\u0434\u044B \u0441 \u0434\u0430\u0442\u0430\u043C\u0438 \u043D\u0430\u0447\u0430\u043B\u0430 \u0438 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043D\u0438\u044F. ");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(7, "div", 4)(8, "button", 5);
      \u0275\u0275listener("click", function SemestersPageComponent_Template_button_click_8_listener() {
        return ctx.openCreateDialog();
      });
      \u0275\u0275element(9, "i", 6);
      \u0275\u0275text(10, " \u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0441\u0435\u043C\u0435\u0441\u0442\u0440 ");
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(11, SemestersPageComponent_Conditional_11_Template, 1, 0, "mat-progress-bar", 7)(12, SemestersPageComponent_Conditional_12_Template, 3, 1, "div", 8)(13, SemestersPageComponent_Conditional_13_Template, 8, 0, "div", 9)(14, SemestersPageComponent_Conditional_14_Template, 19, 3, "div", 10);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275advance(11);
      \u0275\u0275conditional(ctx.loading() ? 11 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.error() ? 12 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.semesters().length === 0 && !ctx.loading() ? 13 : 14);
    }
  }, dependencies: [
    CommonModule,
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
    MatIconButton,
    MatIconModule,
    MatProgressBarModule,
    MatProgressBar,
    MatTooltipModule,
    MatTooltip
  ], encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(SemestersPageComponent, [{
    type: Component,
    args: [{ selector: "app-semesters-page", standalone: true, imports: [
      CommonModule,
      MatTableModule,
      MatButtonModule,
      MatIconModule,
      MatProgressBarModule,
      MatTooltipModule
    ], template: `<div class="page-stack">
  <header class="page-header">
    <div class="page-header__title">
      <h1>\u0421\u0435\u043C\u0435\u0441\u0442\u0440\u044B</h1>
      <p class="page-header__subtitle">
        \u0423\u0447\u0435\u0431\u043D\u044B\u0435 \u043F\u0435\u0440\u0438\u043E\u0434\u044B \u0441 \u0434\u0430\u0442\u0430\u043C\u0438 \u043D\u0430\u0447\u0430\u043B\u0430 \u0438 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043D\u0438\u044F.
      </p>
    </div>
    <div class="page-header__actions">
      <button type="button" class="btn-brand" (click)="openCreateDialog()">
        <i class="ph ph-plus" aria-hidden="true"></i>
        \u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0441\u0435\u043C\u0435\u0441\u0442\u0440
      </button>
    </div>
  </header>

  @if (loading()) {
    <mat-progress-bar mode="indeterminate"></mat-progress-bar>
  }

  @if (error()) {
    <div class="page-error" role="alert">
      <i class="ph ph-warning-circle" aria-hidden="true"></i>
      {{ error() }}
    </div>
  }

  @if (semesters().length === 0 && !loading()) {
    <div class="page-card">
      <div class="page-empty">
        <div class="page-empty__icon" aria-hidden="true">
          <i class="ph-duotone ph-calendar-blank"></i>
        </div>
        <h3 class="page-empty__title">\u0421\u0435\u043C\u0435\u0441\u0442\u0440\u044B \u043D\u0435 \u0441\u043E\u0437\u0434\u0430\u043D\u044B</h3>
        <p class="page-empty__text">\u0421\u043E\u0437\u0434\u0430\u0439\u0442\u0435 \u043F\u0435\u0440\u0432\u044B\u0439 \u0443\u0447\u0435\u0431\u043D\u044B\u0439 \u043F\u0435\u0440\u0438\u043E\u0434 \u0447\u0435\u0440\u0435\u0437 \u043A\u043D\u043E\u043F\u043A\u0443 \u0432\u044B\u0448\u0435.</p>
      </div>
    </div>
  } @else {
    <div class="page-card page-card--flush">
      <mat-table [dataSource]="semesters()">
        <ng-container matColumnDef="name">
          <mat-header-cell *matHeaderCellDef>\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435</mat-header-cell>
          <mat-cell *matCellDef="let sem">{{ sem.name }}</mat-cell>
        </ng-container>

        <ng-container matColumnDef="dateFrom">
          <mat-header-cell *matHeaderCellDef>\u041D\u0430\u0447\u0430\u043B\u043E</mat-header-cell>
          <mat-cell *matCellDef="let sem">{{ formatDate(sem.dateFrom) }}</mat-cell>
        </ng-container>

        <ng-container matColumnDef="dateTo">
          <mat-header-cell *matHeaderCellDef>\u041A\u043E\u043D\u0435\u0446</mat-header-cell>
          <mat-cell *matCellDef="let sem">{{ formatDate(sem.dateTo) }}</mat-cell>
        </ng-container>

        <ng-container matColumnDef="status">
          <mat-header-cell *matHeaderCellDef>\u0421\u0442\u0430\u0442\u0443\u0441</mat-header-cell>
          <mat-cell *matCellDef="let sem">
            <span class="semester-status" [class]="'semester-status semester-status--' + getStatus(sem)">
              {{ getStatusLabel(sem) }}
            </span>
          </mat-cell>
        </ng-container>

        <ng-container matColumnDef="actions">
          <mat-header-cell *matHeaderCellDef></mat-header-cell>
          <mat-cell *matCellDef="let sem">
            <button mat-icon-button matTooltip="\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C" aria-label="\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C" (click)="openEditDialog(sem)">
              <i class="ph ph-pencil-simple" aria-hidden="true"></i>
            </button>
            <button mat-icon-button
              [disabled]="sem.active"
              [matTooltip]="sem.active ? '\u041D\u0435\u043B\u044C\u0437\u044F \u0443\u0434\u0430\u043B\u0438\u0442\u044C \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440' : '\u0423\u0434\u0430\u043B\u0438\u0442\u044C'"
              [attr.aria-label]="sem.active ? '\u041D\u0435\u043B\u044C\u0437\u044F \u0443\u0434\u0430\u043B\u0438\u0442\u044C \u0430\u043A\u0442\u0438\u0432\u043D\u044B\u0439 \u0441\u0435\u043C\u0435\u0441\u0442\u0440' : '\u0423\u0434\u0430\u043B\u0438\u0442\u044C'"
              (click)="deleteSemester(sem)">
              <i class="ph ph-trash" aria-hidden="true"></i>
            </button>
          </mat-cell>
        </ng-container>

        <mat-header-row *matHeaderRowDef="displayedColumns"></mat-header-row>
        <mat-row *matRowDef="let row; columns: displayedColumns;"></mat-row>
      </mat-table>
    </div>
  }
</div>
` }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(SemestersPageComponent, { className: "SemestersPageComponent", filePath: "src/app/features/admin/semesters/semesters-page.component.ts", lineNumber: 30 });
})();
export {
  SemestersPageComponent
};
//# sourceMappingURL=chunk-2ZAKT45A.js.map
