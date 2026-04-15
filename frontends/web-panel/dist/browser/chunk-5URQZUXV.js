import {
  HeadmanApiService
} from "./chunk-ZC6IXG25.js";
import {
  MatProgressSpinner,
  MatProgressSpinnerModule
} from "./chunk-VOF6QK53.js";
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
} from "./chunk-HQRRCPDY.js";
import "./chunk-F5IUR55I.js";
import {
  MatIconModule
} from "./chunk-JUF5AB3M.js";
import {
  MatSnackBar
} from "./chunk-5Q6RVIG4.js";
import {
  MatCheckbox,
  MatCheckboxModule
} from "./chunk-ZIZW6QQV.js";
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogActions,
  MatDialogClose,
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
  AuthService
} from "./chunk-ID5GKTOO.js";
import "./chunk-JHAETIDC.js";
import "./chunk-LPDGA6NZ.js";
import "./chunk-OIBNGD5S.js";
import {
  MatError,
  MatFormField,
  MatFormFieldModule,
  MatLabel,
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
  FormControl,
  FormControlName,
  FormGroup,
  FormGroupDirective,
  FormGroupName,
  NgControlStatus,
  NgControlStatusGroup,
  ReactiveFormsModule,
  RequiredValidator,
  Validators,
  ɵNgNoValidate
} from "./chunk-HWHV5SCS.js";
import {
  RouterModule
} from "./chunk-BRINUWSL.js";
import "./chunk-RWV7HGF7.js";
import "./chunk-FLE4DLW4.js";
import {
  CommonModule,
  NgForOf,
  NgIf
} from "./chunk-CLRYRPPS.js";
import {
  Component,
  computed,
  forkJoin,
  inject,
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
  ɵɵrepeaterTrackByIdentity,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1,
  ɵɵtextInterpolate2
} from "./chunk-4M4FDLBS.js";

// src/app/features/headman/group/assign-assistant-dialog.component.ts
var _forTrack0 = ($index, $item) => $item.id;
var _forTrack1 = ($index, $item) => $item.value;
function AssignAssistantDialogComponent_For_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-option", 4);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const s_r1 = ctx.$implicit;
    \u0275\u0275property("value", s_r1.id);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate2("", s_r1.fullName, " (", s_r1.login, ")");
  }
}
function AssignAssistantDialogComponent_mat_error_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430");
    \u0275\u0275elementEnd();
  }
}
function AssignAssistantDialogComponent_For_15_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 8)(1, "mat-checkbox", 16);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const p_r2 = ctx.$implicit;
    \u0275\u0275advance();
    \u0275\u0275property("formControlName", p_r2.value);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(p_r2.label);
  }
}
function AssignAssistantDialogComponent_mat_error_16_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error", 17);
    \u0275\u0275text(1, " \u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0445\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u043E \u043F\u0440\u0430\u0432\u043E ");
    \u0275\u0275elementEnd();
  }
}
function AssignAssistantDialogComponent_Conditional_19_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 11);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r2.apiError);
  }
}
function AssignAssistantDialogComponent_Conditional_24_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-spinner", 15);
  }
}
var AssignAssistantDialogComponent = class _AssignAssistantDialogComponent {
  headmanApi = inject(HeadmanApiService);
  snackBar = inject(MatSnackBar);
  dialogRef = inject(MatDialogRef);
  auth = inject(AuthService);
  data = inject(MAT_DIALOG_DATA);
  permissionList = [
    { value: "excuse_approve", label: "\u041E\u0434\u043E\u0431\u0440\u044F\u0442\u044C \u0442\u0438\u043A\u0435\u0442\u044B \u043E \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u0430\u0445" },
    { value: "late_checkin_approve", label: "\u041E\u0434\u043E\u0431\u0440\u044F\u0442\u044C \u0437\u0430\u043F\u0440\u043E\u0441\u044B \u043F\u043E\u0437\u0434\u043D\u0435\u0439 \u043E\u0442\u043C\u0435\u0442\u043A\u0438" },
    { value: "subject_manage", label: "\u0423\u043F\u0440\u0430\u0432\u043B\u044F\u0442\u044C \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u0430\u043C\u0438 \u0433\u0440\u0443\u043F\u043F\u044B" },
    { value: "attendance_mark", label: "\u041F\u0440\u043E\u0441\u0442\u0430\u0432\u043B\u044F\u0442\u044C \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u044C" }
  ];
  eligibleStudents = [];
  submitting = false;
  showPermissionError = false;
  apiError = null;
  form = new FormGroup({
    studentId: new FormControl(null, Validators.required),
    permissions: new FormGroup(Object.fromEntries(this.permissionList.map((p) => [p.value, new FormControl(false)])))
  });
  ngOnInit() {
    const currentUserId = this.auth.currentUser()?.id;
    this.eligibleStudents = this.data.students.filter((s) => !this.data.assistantIds.has(s.id) && s.id !== currentUserId);
  }
  onSubmit() {
    this.showPermissionError = false;
    if (this.form.get("studentId")?.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const selectedPermissions = Object.entries(this.form.value.permissions ?? {}).filter(([, v]) => v).map(([k]) => k);
    if (selectedPermissions.length === 0) {
      this.showPermissionError = true;
      return;
    }
    this.submitting = true;
    this.apiError = null;
    this.headmanApi.assignAssistant({
      studentId: this.form.value.studentId,
      permissions: selectedPermissions
    }).subscribe({
      next: () => {
        this.submitting = false;
        this.snackBar.open("\u041F\u043E\u043C\u043E\u0449\u043D\u0438\u043A \u043D\u0430\u0437\u043D\u0430\u0447\u0435\u043D.", void 0, { duration: 4e3 });
        this.dialogRef.close(true);
      },
      error: () => {
        this.submitting = false;
        this.apiError = "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0435\u0449\u0451 \u0440\u0430\u0437.";
      }
    });
  }
  static \u0275fac = function AssignAssistantDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _AssignAssistantDialogComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _AssignAssistantDialogComponent, selectors: [["app-assign-assistant-dialog"]], decls: 26, vars: 7, consts: [["mat-dialog-title", ""], [3, "ngSubmit", "formGroup"], ["appearance", "outline", 2, "width", "100%"], ["formControlName", "studentId", "aria-label", "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430", "required", ""], [3, "value"], [4, "ngIf"], [1, "permissions-label"], ["formGroupName", "permissions"], [1, "permission-row", 2, "min-height", "44px"], ["class", "permission-error", 4, "ngIf"], [1, "permissions-hint"], [1, "page-error"], ["align", "end"], ["mat-stroked-button", "", "type", "button", 3, "mat-dialog-close"], ["type", "button", 1, "btn-brand", 3, "click", "disabled"], ["diameter", "16"], [3, "formControlName"], [1, "permission-error"]], template: function AssignAssistantDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "h2", 0);
      \u0275\u0275text(1, "\u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(2, "mat-dialog-content")(3, "form", 1);
      \u0275\u0275listener("ngSubmit", function AssignAssistantDialogComponent_Template_form_ngSubmit_3_listener() {
        return ctx.onSubmit();
      });
      \u0275\u0275elementStart(4, "mat-form-field", 2)(5, "mat-label");
      \u0275\u0275text(6, "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(7, "mat-select", 3);
      \u0275\u0275repeaterCreate(8, AssignAssistantDialogComponent_For_9_Template, 2, 3, "mat-option", 4, _forTrack0);
      \u0275\u0275elementEnd();
      \u0275\u0275template(10, AssignAssistantDialogComponent_mat_error_10_Template, 2, 0, "mat-error", 5);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(11, "p", 6);
      \u0275\u0275text(12, "\u041F\u0440\u0430\u0432\u0430 \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(13, "div", 7);
      \u0275\u0275repeaterCreate(14, AssignAssistantDialogComponent_For_15_Template, 3, 2, "div", 8, _forTrack1);
      \u0275\u0275elementEnd();
      \u0275\u0275template(16, AssignAssistantDialogComponent_mat_error_16_Template, 2, 0, "mat-error", 9);
      \u0275\u0275elementStart(17, "p", 10);
      \u0275\u0275text(18, " \u041C\u043E\u0436\u043D\u043E \u0432\u044B\u0431\u0440\u0430\u0442\u044C \u043D\u0435\u0441\u043A\u043E\u043B\u044C\u043A\u043E \u043F\u0440\u0430\u0432. \u041F\u0440\u0430\u0432\u0430 \u043C\u043E\u0436\u043D\u043E \u0438\u0437\u043C\u0435\u043D\u0438\u0442\u044C \u043F\u043E\u0437\u0436\u0435 \u0447\u0435\u0440\u0435\u0437 \u043F\u0435\u0440\u0435\u043D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435. ");
      \u0275\u0275elementEnd();
      \u0275\u0275template(19, AssignAssistantDialogComponent_Conditional_19_Template, 2, 1, "div", 11);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(20, "mat-dialog-actions", 12)(21, "button", 13);
      \u0275\u0275text(22, "\u041E\u0442\u043C\u0435\u043D\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(23, "button", 14);
      \u0275\u0275listener("click", function AssignAssistantDialogComponent_Template_button_click_23_listener() {
        return ctx.onSubmit();
      });
      \u0275\u0275template(24, AssignAssistantDialogComponent_Conditional_24_Template, 1, 0, "mat-spinner", 15);
      \u0275\u0275text(25, " \u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430 ");
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      let tmp_2_0;
      \u0275\u0275advance(3);
      \u0275\u0275property("formGroup", ctx.form);
      \u0275\u0275advance(5);
      \u0275\u0275repeater(ctx.eligibleStudents);
      \u0275\u0275advance(2);
      \u0275\u0275property("ngIf", (tmp_2_0 = ctx.form.get("studentId")) == null ? null : tmp_2_0.invalid);
      \u0275\u0275advance(4);
      \u0275\u0275repeater(ctx.permissionList);
      \u0275\u0275advance(2);
      \u0275\u0275property("ngIf", ctx.showPermissionError);
      \u0275\u0275advance(3);
      \u0275\u0275conditional(ctx.apiError ? 19 : -1);
      \u0275\u0275advance(2);
      \u0275\u0275property("mat-dialog-close", false);
      \u0275\u0275advance(2);
      \u0275\u0275property("disabled", ctx.submitting);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.submitting ? 24 : -1);
    }
  }, dependencies: [
    CommonModule,
    NgIf,
    ReactiveFormsModule,
    \u0275NgNoValidate,
    NgControlStatus,
    NgControlStatusGroup,
    RequiredValidator,
    FormGroupDirective,
    FormControlName,
    FormGroupName,
    MatDialogModule,
    MatDialogClose,
    MatDialogTitle,
    MatDialogActions,
    MatDialogContent,
    MatButtonModule,
    MatButton,
    MatSelectModule,
    MatFormField,
    MatLabel,
    MatError,
    MatSelect,
    MatOption,
    MatCheckboxModule,
    MatCheckbox,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    MatProgressSpinner
  ], styles: ["\n\n.permissions-label[_ngcontent-%COMP%] {\n  font-weight: 600;\n  font-size: var(--text-sm);\n  margin-bottom: var(--space-2);\n  color: var(--text-primary);\n}\n.permissions-hint[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  margin-top: var(--space-2);\n}\n.permission-row[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n}\n.permission-error[_ngcontent-%COMP%] {\n  display: block;\n  font-size: var(--text-xs);\n  color: var(--accent-danger);\n  margin-top: var(--space-1);\n}\n/*# sourceMappingURL=assign-assistant-dialog.component.css.map */"] });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(AssignAssistantDialogComponent, [{
    type: Component,
    args: [{ selector: "app-assign-assistant-dialog", standalone: true, imports: [
      CommonModule,
      ReactiveFormsModule,
      MatDialogModule,
      MatButtonModule,
      MatSelectModule,
      MatCheckboxModule,
      MatFormFieldModule,
      MatProgressSpinnerModule
    ], template: `
    <h2 mat-dialog-title>\u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B</h2>
    <mat-dialog-content>
      <form [formGroup]="form" (ngSubmit)="onSubmit()">
        <!-- Student select -->
        <mat-form-field appearance="outline" style="width:100%">
          <mat-label>\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430</mat-label>
          <mat-select formControlName="studentId" aria-label="\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430" required>
            @for (s of eligibleStudents; track s.id) {
              <mat-option [value]="s.id">{{ s.fullName }} ({{ s.login }})</mat-option>
            }
          </mat-select>
          <mat-error *ngIf="form.get('studentId')?.invalid">\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430</mat-error>
        </mat-form-field>

        <!-- Permission checkboxes -->
        <p class="permissions-label">\u041F\u0440\u0430\u0432\u0430 \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430</p>
        <div formGroupName="permissions">
          @for (p of permissionList; track p.value) {
            <div class="permission-row" style="min-height:44px">
              <mat-checkbox [formControlName]="p.value">{{ p.label }}</mat-checkbox>
            </div>
          }
        </div>
        <mat-error *ngIf="showPermissionError" class="permission-error">
          \u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0445\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u043E \u043F\u0440\u0430\u0432\u043E
        </mat-error>
        <p class="permissions-hint">
          \u041C\u043E\u0436\u043D\u043E \u0432\u044B\u0431\u0440\u0430\u0442\u044C \u043D\u0435\u0441\u043A\u043E\u043B\u044C\u043A\u043E \u043F\u0440\u0430\u0432. \u041F\u0440\u0430\u0432\u0430 \u043C\u043E\u0436\u043D\u043E \u0438\u0437\u043C\u0435\u043D\u0438\u0442\u044C \u043F\u043E\u0437\u0436\u0435 \u0447\u0435\u0440\u0435\u0437 \u043F\u0435\u0440\u0435\u043D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435.
        </p>

        @if (apiError) {
          <div class="page-error">{{ apiError }}</div>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" [mat-dialog-close]="false">\u041E\u0442\u043C\u0435\u043D\u0430</button>
      <button class="btn-brand" type="button" [disabled]="submitting" (click)="onSubmit()">
        @if (submitting) { <mat-spinner diameter="16"></mat-spinner> }
        \u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430
      </button>
    </mat-dialog-actions>
  `, styles: ["/* angular:styles/component:css;b689efd749bd3e686a7129699fc7fb85798717cdcf01d57dc8492dbd72beca4b;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/group/assign-assistant-dialog.component.ts */\n.permissions-label {\n  font-weight: 600;\n  font-size: var(--text-sm);\n  margin-bottom: var(--space-2);\n  color: var(--text-primary);\n}\n.permissions-hint {\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n  margin-top: var(--space-2);\n}\n.permission-row {\n  display: flex;\n  align-items: center;\n}\n.permission-error {\n  display: block;\n  font-size: var(--text-xs);\n  color: var(--accent-danger);\n  margin-top: var(--space-1);\n}\n/*# sourceMappingURL=assign-assistant-dialog.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(AssignAssistantDialogComponent, { className: "AssignAssistantDialogComponent", filePath: "src/app/features/headman/group/assign-assistant-dialog.component.ts", lineNumber: 104 });
})();

// src/app/features/headman/group/delete-assistant-dialog.component.ts
var DeleteAssistantDialogComponent = class _DeleteAssistantDialogComponent {
  data = inject(MAT_DIALOG_DATA);
  static \u0275fac = function DeleteAssistantDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _DeleteAssistantDialogComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _DeleteAssistantDialogComponent, selectors: [["app-delete-assistant-dialog"]], decls: 9, vars: 3, consts: [["mat-dialog-title", ""], ["align", "end"], ["mat-stroked-button", "", 3, "mat-dialog-close"], ["mat-flat-button", "", 1, "btn-danger", 3, "mat-dialog-close"]], template: function DeleteAssistantDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "h2", 0);
      \u0275\u0275text(1, "\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430?");
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
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate1(" \u0421\u0442\u0443\u0434\u0435\u043D\u0442 ", ctx.data.fullName, " \u043F\u043E\u0442\u0435\u0440\u044F\u0435\u0442 \u0432\u0441\u0435 \u043F\u0440\u0430\u0432\u0430 \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B. ");
      \u0275\u0275advance(2);
      \u0275\u0275property("mat-dialog-close", false);
      \u0275\u0275advance(2);
      \u0275\u0275property("mat-dialog-close", true);
    }
  }, dependencies: [MatDialogModule, MatDialogClose, MatDialogTitle, MatDialogActions, MatDialogContent, MatButtonModule, MatButton], styles: ["\n\n.btn-danger[_ngcontent-%COMP%] {\n  background: var(--accent-danger);\n  color: white;\n}\n/*# sourceMappingURL=delete-assistant-dialog.component.css.map */"] });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(DeleteAssistantDialogComponent, [{
    type: Component,
    args: [{ selector: "app-delete-assistant-dialog", standalone: true, imports: [MatDialogModule, MatButtonModule], template: `
    <h2 mat-dialog-title>\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430?</h2>
    <mat-dialog-content>
      \u0421\u0442\u0443\u0434\u0435\u043D\u0442 {{ data.fullName }} \u043F\u043E\u0442\u0435\u0440\u044F\u0435\u0442 \u0432\u0441\u0435 \u043F\u0440\u0430\u0432\u0430 \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B.
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button [mat-dialog-close]="false">\u041E\u0442\u043C\u0435\u043D\u0430</button>
      <button mat-flat-button class="btn-danger" [mat-dialog-close]="true">\u0423\u0434\u0430\u043B\u0438\u0442\u044C</button>
    </mat-dialog-actions>
  `, styles: ["/* angular:styles/component:css;198aaea02e1fa9d38442963dfa13f52956c4fb0c546b1e85cc691f1de51a6195;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/group/delete-assistant-dialog.component.ts */\n.btn-danger {\n  background: var(--accent-danger);\n  color: white;\n}\n/*# sourceMappingURL=delete-assistant-dialog.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(DeleteAssistantDialogComponent, { className: "DeleteAssistantDialogComponent", filePath: "src/app/features/headman/group/delete-assistant-dialog.component.ts", lineNumber: 30 });
})();

// src/app/features/headman/group/headman-group.component.ts
var _c0 = () => [1, 2, 3, 4, 5];
var _c1 = () => ["student", "status", "action"];
var _forTrack02 = ($index, $item) => $item.id;
function HeadmanGroupComponent_Conditional_15_div_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 17);
  }
}
function HeadmanGroupComponent_Conditional_15_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275template(0, HeadmanGroupComponent_Conditional_15_div_0_Template, 1, 0, "div", 15);
    \u0275\u0275elementStart(1, "span", 16);
    \u0275\u0275text(2, "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...");
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    \u0275\u0275property("ngForOf", \u0275\u0275pureFunction0(1, _c0));
  }
}
function HeadmanGroupComponent_Conditional_16_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 9);
    \u0275\u0275element(1, "i", 18);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(ctx_r0.error());
  }
}
function HeadmanGroupComponent_Conditional_17_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 10);
    \u0275\u0275element(1, "i", 19);
    \u0275\u0275elementStart(2, "h3");
    \u0275\u0275text(3, "\u0413\u0440\u0443\u043F\u043F\u0430 \u043F\u0443\u0441\u0442\u0430");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "p");
    \u0275\u0275text(5, "\u0412 \u0432\u0430\u0448\u0435\u0439 \u0433\u0440\u0443\u043F\u043F\u0435 \u043F\u043E\u043A\u0430 \u043D\u0435\u0442 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432.");
    \u0275\u0275elementEnd()();
  }
}
function HeadmanGroupComponent_Conditional_18_th_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "th", 27);
    \u0275\u0275text(1, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
    \u0275\u0275elementEnd();
  }
}
function HeadmanGroupComponent_Conditional_18_td_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "td", 28)(1, "span", 29);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "span", 30);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const s_r2 = ctx.$implicit;
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(s_r2.fullName);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(s_r2.login);
  }
}
function HeadmanGroupComponent_Conditional_18_th_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "th", 27);
    \u0275\u0275text(1, "\u0421\u0442\u0430\u0442\u0443\u0441");
    \u0275\u0275elementEnd();
  }
}
function HeadmanGroupComponent_Conditional_18_td_6_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 31);
    \u0275\u0275text(1, "\u041F\u043E\u043C\u043E\u0449\u043D\u0438\u043A \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B");
    \u0275\u0275elementEnd();
  }
}
function HeadmanGroupComponent_Conditional_18_td_6_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 32);
    \u0275\u0275text(1, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
    \u0275\u0275elementEnd();
  }
}
function HeadmanGroupComponent_Conditional_18_td_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "td", 28);
    \u0275\u0275template(1, HeadmanGroupComponent_Conditional_18_td_6_Conditional_1_Template, 2, 0, "span", 31)(2, HeadmanGroupComponent_Conditional_18_td_6_Conditional_2_Template, 2, 0, "span", 32);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const s_r3 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r0.assistantIds().has(s_r3.id) ? 1 : 2);
  }
}
function HeadmanGroupComponent_Conditional_18_th_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "th", 27);
  }
}
function HeadmanGroupComponent_Conditional_18_td_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "td", 28);
  }
}
function HeadmanGroupComponent_Conditional_18_tr_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "tr", 33);
  }
}
function HeadmanGroupComponent_Conditional_18_tr_11_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "tr", 34);
  }
}
function HeadmanGroupComponent_Conditional_18_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "table", 11);
    \u0275\u0275elementContainerStart(1, 20);
    \u0275\u0275template(2, HeadmanGroupComponent_Conditional_18_th_2_Template, 2, 0, "th", 21)(3, HeadmanGroupComponent_Conditional_18_td_3_Template, 5, 2, "td", 22);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(4, 23);
    \u0275\u0275template(5, HeadmanGroupComponent_Conditional_18_th_5_Template, 2, 0, "th", 21)(6, HeadmanGroupComponent_Conditional_18_td_6_Template, 3, 1, "td", 22);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(7, 24);
    \u0275\u0275template(8, HeadmanGroupComponent_Conditional_18_th_8_Template, 1, 0, "th", 21)(9, HeadmanGroupComponent_Conditional_18_td_9_Template, 1, 0, "td", 22);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275template(10, HeadmanGroupComponent_Conditional_18_tr_10_Template, 1, 0, "tr", 25)(11, HeadmanGroupComponent_Conditional_18_tr_11_Template, 1, 0, "tr", 26);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275property("dataSource", ctx_r0.students());
    \u0275\u0275advance(10);
    \u0275\u0275property("matHeaderRowDef", \u0275\u0275pureFunction0(3, _c1));
    \u0275\u0275advance();
    \u0275\u0275property("matRowDefColumns", \u0275\u0275pureFunction0(4, _c1));
  }
}
function HeadmanGroupComponent_Conditional_23_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 13);
    \u0275\u0275text(1, "\u041F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0438 \u043D\u0435 \u043D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u044B");
    \u0275\u0275elementEnd();
  }
}
function HeadmanGroupComponent_For_25_For_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 40);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const p_r5 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.permissionLabel(p_r5));
  }
}
function HeadmanGroupComponent_For_25_Template(rf, ctx) {
  if (rf & 1) {
    const _r4 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "div", 14);
    \u0275\u0275element(1, "i", 35);
    \u0275\u0275elementStart(2, "div", 36)(3, "span", 37);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "span", 38);
    \u0275\u0275text(6);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(7, "div", 39);
    \u0275\u0275repeaterCreate(8, HeadmanGroupComponent_For_25_For_9_Template, 2, 1, "span", 40, \u0275\u0275repeaterTrackByIdentity);
    \u0275\u0275elementEnd()();
    \u0275\u0275elementStart(10, "button", 41);
    \u0275\u0275listener("click", function HeadmanGroupComponent_For_25_Template_button_click_10_listener() {
      const a_r6 = \u0275\u0275restoreView(_r4).$implicit;
      const ctx_r0 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r0.openDeleteDialog(a_r6));
    });
    \u0275\u0275element(11, "i", 42);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const a_r6 = ctx.$implicit;
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate(a_r6.fullName);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate(a_r6.login);
    \u0275\u0275advance(2);
    \u0275\u0275repeater(a_r6.permissions);
    \u0275\u0275advance(2);
    \u0275\u0275attribute("aria-label", "\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430 " + a_r6.fullName);
  }
}
var PERMISSION_CHIP_LABELS = {
  excuse_approve: "\u041E\u0434\u043E\u0431\u0440\u0435\u043D\u0438\u0435 \u043F\u0440\u043E\u043F\u0443\u0441\u043A\u043E\u0432",
  late_checkin_approve: "\u041F\u043E\u0437\u0434\u043D\u0438\u0435 \u043E\u0442\u043C\u0435\u0442\u043A\u0438",
  subject_manage: "\u0423\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435 \u043F\u0440\u0435\u0434\u043C\u0435\u0442\u0430\u043C\u0438",
  attendance_mark: "\u041F\u0440\u043E\u0441\u0442\u0430\u0432\u043B\u0435\u043D\u0438\u0435 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438"
};
var HeadmanGroupComponent = class _HeadmanGroupComponent {
  headmanApi = inject(HeadmanApiService);
  dialog = inject(MatDialog);
  snackBar = inject(MatSnackBar);
  auth = inject(AuthService);
  loading = signal(false);
  error = signal(null);
  students = signal([]);
  assistants = signal([]);
  assistantIds = computed(() => new Set(this.assistants().map((a) => a.studentId)));
  ngOnInit() {
    this.loadData();
  }
  loadData() {
    this.loading.set(true);
    this.error.set(null);
    const groupId = this.auth.currentUser()?.groupId;
    if (!groupId) {
      this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0441\u043F\u0438\u0441\u043E\u043A \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043E\u0431\u043D\u043E\u0432\u0438\u0442\u044C \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0443.");
      this.loading.set(false);
      return;
    }
    forkJoin([
      this.headmanApi.getGroupMembers(),
      this.headmanApi.listAssistants(groupId)
    ]).subscribe({
      next: ([membersResp, assistantsResp]) => {
        this.students.set(Object.values(membersResp?._embedded ?? {})[0] ?? []);
        this.assistants.set(Object.values(assistantsResp?._embedded ?? {})[0] ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0441\u043F\u0438\u0441\u043E\u043A \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043E\u0431\u043D\u043E\u0432\u0438\u0442\u044C \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0443.");
        this.loading.set(false);
      }
    });
  }
  openAssignDialog() {
    const ref = this.dialog.open(AssignAssistantDialogComponent, {
      width: "520px",
      maxWidth: "95vw",
      ariaLabel: "\u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B",
      data: { students: this.students(), assistantIds: this.assistantIds() }
    });
    ref.afterClosed().subscribe((result) => {
      if (result) {
        this.loadData();
      }
    });
  }
  openDeleteDialog(assistant) {
    const ref = this.dialog.open(DeleteAssistantDialogComponent, {
      data: { fullName: assistant.fullName },
      ariaLabel: "\u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043D\u0438\u0435 \u0443\u0434\u0430\u043B\u0435\u043D\u0438\u044F"
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed)
        return;
      this.headmanApi.revokeAssistant(assistant.id).subscribe({
        next: () => {
          this.snackBar.open("\u041F\u043E\u043C\u043E\u0449\u043D\u0438\u043A \u0443\u0434\u0430\u043B\u0451\u043D.", void 0, { duration: 4e3 });
          this.loadData();
        },
        error: () => this.snackBar.open("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0443\u0434\u0430\u043B\u0438\u0442\u044C \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430.", void 0, { duration: 6e3 })
      });
    });
  }
  permissionLabel(p) {
    return PERMISSION_CHIP_LABELS[p] ?? p;
  }
  static \u0275fac = function HeadmanGroupComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeadmanGroupComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeadmanGroupComponent, selectors: [["app-headman-group"]], decls: 26, vars: 3, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-eyebrow"], [1, "page-title"], [1, "page-header__actions"], [1, "btn-brand", 3, "click"], [1, "ph", "ph-plus"], [1, "page-card", "page-card--flush"], [1, "page-card__header"], [1, "page-error"], [1, "page-empty"], ["mat-table", "", 3, "dataSource"], [1, "page-card"], [1, "assistant-empty"], [1, "assistant-item"], ["class", "skeleton-row", "aria-hidden", "true", 4, "ngFor", "ngForOf"], ["aria-live", "polite", 1, "sr-only"], ["aria-hidden", "true", 1, "skeleton-row"], [1, "ph", "ph-warning-circle"], [1, "ph-duotone", "ph-users"], ["matColumnDef", "student"], ["mat-header-cell", "", 4, "matHeaderCellDef"], ["mat-cell", "", 4, "matCellDef"], ["matColumnDef", "status"], ["matColumnDef", "action"], ["mat-header-row", "", 4, "matHeaderRowDef"], ["mat-row", "", 4, "matRowDef", "matRowDefColumns"], ["mat-header-cell", ""], ["mat-cell", ""], [1, "student-name"], [1, "student-login"], [1, "role-chip", "role-chip--headman-assistant"], [1, "role-chip", "role-chip--student"], ["mat-header-row", ""], ["mat-row", ""], [1, "ph", "ph-user-circle"], [1, "assistant-info"], [1, "assistant-name"], [1, "assistant-login"], [1, "permission-chips"], [1, "permission-chip"], [1, "icon-btn", "icon-btn--danger", 3, "click"], [1, "ph", "ph-trash"]], template: function HeadmanGroupComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "div")(3, "span", 2);
      \u0275\u0275text(4, "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "h1", 3);
      \u0275\u0275text(6, "\u041C\u043E\u044F \u0433\u0440\u0443\u043F\u043F\u0430");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(7, "div", 4)(8, "button", 5);
      \u0275\u0275listener("click", function HeadmanGroupComponent_Template_button_click_8_listener() {
        return ctx.openAssignDialog();
      });
      \u0275\u0275element(9, "i", 6);
      \u0275\u0275text(10, " \u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430 ");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(11, "div", 7)(12, "div", 8)(13, "h2");
      \u0275\u0275text(14, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u044B");
      \u0275\u0275elementEnd()();
      \u0275\u0275template(15, HeadmanGroupComponent_Conditional_15_Template, 3, 2)(16, HeadmanGroupComponent_Conditional_16_Template, 3, 1, "div", 9)(17, HeadmanGroupComponent_Conditional_17_Template, 6, 0, "div", 10)(18, HeadmanGroupComponent_Conditional_18_Template, 12, 5, "table", 11);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(19, "div", 12)(20, "div", 8)(21, "h2");
      \u0275\u0275text(22, "\u041F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0438 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B");
      \u0275\u0275elementEnd()();
      \u0275\u0275template(23, HeadmanGroupComponent_Conditional_23_Template, 2, 0, "p", 13);
      \u0275\u0275repeaterCreate(24, HeadmanGroupComponent_For_25_Template, 12, 3, "div", 14, _forTrack02);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(15);
      \u0275\u0275conditional(ctx.loading() ? 15 : ctx.error() ? 16 : ctx.students().length === 0 ? 17 : 18);
      \u0275\u0275advance(8);
      \u0275\u0275conditional(ctx.assistants().length === 0 && !ctx.loading() ? 23 : -1);
      \u0275\u0275advance();
      \u0275\u0275repeater(ctx.assistants());
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
    MatIconModule,
    MatProgressSpinnerModule
  ], styles: ["\n\n.student-login[_ngcontent-%COMP%] {\n  display: block;\n  font-family: var(--font-mono);\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n}\n.assistant-login[_ngcontent-%COMP%] {\n  display: block;\n  font-family: var(--font-mono);\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n}\n.permission-chip[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  background: var(--bg-elevated);\n  font-size: var(--text-xs);\n  padding: 2px 8px;\n  border-radius: var(--radius-full);\n  margin-right: 4px;\n  margin-top: 4px;\n}\n.icon-btn--danger[_ngcontent-%COMP%]:hover {\n  color: var(--accent-danger);\n}\n.skeleton-row[_ngcontent-%COMP%] {\n  height: 52px;\n  background: var(--bg-elevated);\n  border-radius: var(--radius-md);\n  margin-bottom: 8px;\n  animation: _ngcontent-%COMP%_dashboard-shimmer 1.4s ease-in-out infinite;\n}\n@keyframes _ngcontent-%COMP%_dashboard-shimmer {\n  0%, 100% {\n    opacity: 0.5;\n  }\n  50% {\n    opacity: 1;\n  }\n}\n.role-chip--headman-assistant[_ngcontent-%COMP%] {\n  background: var(--accent-info-subtle, rgba(139,92,246,0.12));\n  color: var(--accent-info);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  padding: 2px 8px;\n  border-radius: var(--radius-full);\n}\n.assistant-item[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: flex-start;\n  gap: var(--space-4);\n  padding: var(--space-4);\n  border-bottom: 1px solid var(--border-subtle);\n}\n.assistant-item[_ngcontent-%COMP%]:last-of-type {\n  border-bottom: none;\n}\n.assistant-info[_ngcontent-%COMP%] {\n  flex: 1;\n}\n.assistant-name[_ngcontent-%COMP%] {\n  display: block;\n  font-weight: 600;\n  font-size: var(--text-sm);\n}\n.permission-chips[_ngcontent-%COMP%] {\n  display: flex;\n  flex-wrap: wrap;\n  margin-top: var(--space-1);\n}\n.assistant-empty[_ngcontent-%COMP%] {\n  padding: var(--space-4);\n  color: var(--text-muted);\n  font-size: var(--text-sm);\n}\n.sr-only[_ngcontent-%COMP%] {\n  position: absolute;\n  width: 1px;\n  height: 1px;\n  padding: 0;\n  margin: -1px;\n  overflow: hidden;\n  clip: rect(0, 0, 0, 0);\n  white-space: nowrap;\n  border: 0;\n}\n/*# sourceMappingURL=headman-group.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] } });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeadmanGroupComponent, [{
    type: Component,
    args: [{ selector: "app-headman-group", standalone: true, imports: [
      CommonModule,
      RouterModule,
      MatTableModule,
      MatButtonModule,
      MatIconModule,
      MatProgressSpinnerModule,
      AssignAssistantDialogComponent,
      DeleteAssistantDialogComponent
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
          <h1 class="page-title">\u041C\u043E\u044F \u0433\u0440\u0443\u043F\u043F\u0430</h1>
        </div>
        <div class="page-header__actions">
          <button class="btn-brand" (click)="openAssignDialog()">
            <i class="ph ph-plus"></i> \u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430
          </button>
        </div>
      </div>

      <!-- Section 1: Student list -->
      <div class="page-card page-card--flush">
        <div class="page-card__header">
          <h2>\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u044B</h2>
        </div>

        @if (loading()) {
          <div class="skeleton-row" *ngFor="let i of [1,2,3,4,5]" aria-hidden="true"></div>
          <span aria-live="polite" class="sr-only">\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430...</span>
        }
        @else if (error()) {
          <div class="page-error"><i class="ph ph-warning-circle"></i>{{ error() }}</div>
        }
        @else if (students().length === 0) {
          <div class="page-empty">
            <i class="ph-duotone ph-users"></i>
            <h3>\u0413\u0440\u0443\u043F\u043F\u0430 \u043F\u0443\u0441\u0442\u0430</h3>
            <p>\u0412 \u0432\u0430\u0448\u0435\u0439 \u0433\u0440\u0443\u043F\u043F\u0435 \u043F\u043E\u043A\u0430 \u043D\u0435\u0442 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432.</p>
          </div>
        }
        @else {
          <table mat-table [dataSource]="students()">
            <ng-container matColumnDef="student">
              <th mat-header-cell *matHeaderCellDef>\u0421\u0442\u0443\u0434\u0435\u043D\u0442</th>
              <td mat-cell *matCellDef="let s">
                <span class="student-name">{{ s.fullName }}</span>
                <span class="student-login">{{ s.login }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>\u0421\u0442\u0430\u0442\u0443\u0441</th>
              <td mat-cell *matCellDef="let s">
                @if (assistantIds().has(s.id)) {
                  <span class="role-chip role-chip--headman-assistant">\u041F\u043E\u043C\u043E\u0449\u043D\u0438\u043A \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B</span>
                } @else {
                  <span class="role-chip role-chip--student">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</span>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="action">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let s"></td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="['student','status','action']"></tr>
            <tr mat-row *matRowDef="let row; columns: ['student','status','action']"></tr>
          </table>
        }
      </div>

      <!-- Section 2: Assistant list -->
      <div class="page-card">
        <div class="page-card__header">
          <h2>\u041F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0438 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B</h2>
        </div>
        @if (assistants().length === 0 && !loading()) {
          <p class="assistant-empty">\u041F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0438 \u043D\u0435 \u043D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u044B</p>
        }
        @for (a of assistants(); track a.id) {
          <div class="assistant-item">
            <i class="ph ph-user-circle"></i>
            <div class="assistant-info">
              <span class="assistant-name">{{ a.fullName }}</span>
              <span class="assistant-login">{{ a.login }}</span>
              <div class="permission-chips">
                @for (p of a.permissions; track p) {
                  <span class="permission-chip">{{ permissionLabel(p) }}</span>
                }
              </div>
            </div>
            <button class="icon-btn icon-btn--danger"
                    [attr.aria-label]="'\u0423\u0434\u0430\u043B\u0438\u0442\u044C \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A\u0430 ' + a.fullName"
                    (click)="openDeleteDialog(a)">
              <i class="ph ph-trash"></i>
            </button>
          </div>
        }
      </div>
    </div>
  `, styles: ["/* angular:styles/component:css;f7ef0aba80238c6e2d0656f03b9515346187964f0642d93cf0c60d17fb5b64b8;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/features/headman/group/headman-group.component.ts */\n.student-login {\n  display: block;\n  font-family: var(--font-mono);\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n}\n.assistant-login {\n  display: block;\n  font-family: var(--font-mono);\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n}\n.permission-chip {\n  display: inline-flex;\n  align-items: center;\n  background: var(--bg-elevated);\n  font-size: var(--text-xs);\n  padding: 2px 8px;\n  border-radius: var(--radius-full);\n  margin-right: 4px;\n  margin-top: 4px;\n}\n.icon-btn--danger:hover {\n  color: var(--accent-danger);\n}\n.skeleton-row {\n  height: 52px;\n  background: var(--bg-elevated);\n  border-radius: var(--radius-md);\n  margin-bottom: 8px;\n  animation: dashboard-shimmer 1.4s ease-in-out infinite;\n}\n@keyframes dashboard-shimmer {\n  0%, 100% {\n    opacity: 0.5;\n  }\n  50% {\n    opacity: 1;\n  }\n}\n.role-chip--headman-assistant {\n  background: var(--accent-info-subtle, rgba(139,92,246,0.12));\n  color: var(--accent-info);\n  font-size: var(--text-xs);\n  font-weight: 600;\n  padding: 2px 8px;\n  border-radius: var(--radius-full);\n}\n.assistant-item {\n  display: flex;\n  align-items: flex-start;\n  gap: var(--space-4);\n  padding: var(--space-4);\n  border-bottom: 1px solid var(--border-subtle);\n}\n.assistant-item:last-of-type {\n  border-bottom: none;\n}\n.assistant-info {\n  flex: 1;\n}\n.assistant-name {\n  display: block;\n  font-weight: 600;\n  font-size: var(--text-sm);\n}\n.permission-chips {\n  display: flex;\n  flex-wrap: wrap;\n  margin-top: var(--space-1);\n}\n.assistant-empty {\n  padding: var(--space-4);\n  color: var(--text-muted);\n  font-size: var(--text-sm);\n}\n.sr-only {\n  position: absolute;\n  width: 1px;\n  height: 1px;\n  padding: 0;\n  margin: -1px;\n  overflow: hidden;\n  clip: rect(0, 0, 0, 0);\n  white-space: nowrap;\n  border: 0;\n}\n/*# sourceMappingURL=headman-group.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeadmanGroupComponent, { className: "HeadmanGroupComponent", filePath: "src/app/features/headman/group/headman-group.component.ts", lineNumber: 239 });
})();
export {
  HeadmanGroupComponent
};
//# sourceMappingURL=chunk-5URQZUXV.js.map
