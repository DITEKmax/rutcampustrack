import {
  MatProgressSpinner,
  MatProgressSpinnerModule
} from "./chunk-BAW76DQ6.js";
import "./chunk-F5IUR55I.js";
import "./chunk-33KX5TJL.js";
import {
  animate,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import {
  AuthApi
} from "./chunk-HQN62MRG.js";
import {
  AuthService
} from "./chunk-URGWZVMC.js";
import {
  MatInput,
  MatInputModule
} from "./chunk-WQN3TC62.js";
import {
  MatError,
  MatFormField,
  MatFormFieldModule,
  MatLabel,
  MatSuffix
} from "./chunk-Z3VV4J2X.js";
import {
  MatButton,
  MatButtonModule,
  MatIconButton
} from "./chunk-PAS4QIDL.js";
import "./chunk-4RJPSRCU.js";
import "./chunk-6JM2QIGP.js";
import "./chunk-75YPR2VK.js";
import "./chunk-2UA5NBNP.js";
import "./chunk-YIJEORIR.js";
import "./chunk-B3BDHDAM.js";
import {
  DefaultValueAccessor,
  FormBuilder,
  FormControlName,
  FormGroupDirective,
  NgControlStatus,
  NgControlStatusGroup,
  ReactiveFormsModule,
  Validators,
  ɵNgNoValidate
} from "./chunk-XAMIXVT7.js";
import {
  CommonModule
} from "./chunk-M5DKW26A.js";
import {
  ChangeDetectionStrategy,
  Component,
  computed,
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
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵlistener,
  ɵɵnextContext,
  ɵɵproperty,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-MFRIGFR2.js";

// src/app/features/student/profile/student-profile.component.ts
function StudentProfileComponent_Conditional_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 9)(1, "span", 24);
    \u0275\u0275text(2, "\u0413\u0420\u0423\u041F\u041F\u0410");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "span", 25);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(4);
    \u0275\u0275textInterpolate(ctx_r0.currentUser().groupId);
  }
}
function StudentProfileComponent_Conditional_27_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error", 17);
    \u0275\u0275text(1, "\u041D\u0435\u0432\u0435\u0440\u043D\u044B\u0439 \u0442\u0435\u043A\u0443\u0449\u0438\u0439 \u043F\u0430\u0440\u043E\u043B\u044C.");
    \u0275\u0275elementEnd();
  }
}
function StudentProfileComponent_Conditional_47_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 21);
    \u0275\u0275element(1, "i", 26);
    \u0275\u0275elementStart(2, "span");
    \u0275\u0275text(3);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate(ctx_r0.apiError());
  }
}
function StudentProfileComponent_Conditional_48_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 22);
    \u0275\u0275element(1, "i", 27);
    \u0275\u0275elementStart(2, "span");
    \u0275\u0275text(3, "\u041F\u0430\u0440\u043E\u043B\u044C \u0443\u0441\u043F\u0435\u0448\u043D\u043E \u0438\u0437\u043C\u0435\u043D\u0451\u043D");
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    \u0275\u0275property("@toastSlide", void 0);
  }
}
function StudentProfileComponent_Conditional_50_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-spinner", 28);
    \u0275\u0275elementStart(1, "span");
    \u0275\u0275text(2, "\u0421\u043E\u0445\u0440\u0430\u043D\u044F\u0435\u043C\u2026");
    \u0275\u0275elementEnd();
  }
}
function StudentProfileComponent_Conditional_51_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span");
    \u0275\u0275text(1, "\u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C");
    \u0275\u0275elementEnd();
  }
}
var StudentProfileComponent = class _StudentProfileComponent {
  authService = inject(AuthService);
  authApi = inject(AuthApi);
  fb = inject(FormBuilder);
  currentUser = this.authService.currentUser;
  avatarInitials = computed(() => {
    const id = this.currentUser()?.id;
    if (id == null)
      return "??";
    return String(id).slice(-2).padStart(2, "0");
  });
  form;
  submitting = signal(false);
  successVisible = signal(false);
  apiError = signal(null);
  showCurrentPassword = signal(false);
  showNewPassword = signal(false);
  // Live validation hint state
  newPasswordValue = signal("");
  hintMinLength = computed(() => this.newPasswordValue().length >= 8);
  hintUppercase = computed(() => /[A-Z]/.test(this.newPasswordValue()));
  hintDigit = computed(() => /\d/.test(this.newPasswordValue()));
  ngOnInit() {
    this.form = this.fb.group({
      currentPassword: ["", Validators.required],
      newPassword: [
        "",
        [
          Validators.required,
          Validators.minLength(8),
          (c) => /[A-Z]/.test(c.value) && /\d/.test(c.value) ? null : { pattern: true }
        ]
      ]
    });
    this.form.get("newPassword")?.valueChanges.subscribe((v) => {
      this.newPasswordValue.set(v ?? "");
    });
  }
  toggleCurrentPasswordVisibility() {
    this.showCurrentPassword.update((v) => !v);
  }
  toggleNewPasswordVisibility() {
    this.showNewPassword.update((v) => !v);
  }
  onSubmit() {
    if (this.form.invalid || this.submitting())
      return;
    this.submitting.set(true);
    this.apiError.set(null);
    this.form.get("currentPassword")?.setErrors(null);
    const { currentPassword, newPassword } = this.form.value;
    this.authApi.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.submitting.set(false);
        this.successVisible.set(true);
        this.form.reset();
        this.newPasswordValue.set("");
        setTimeout(() => this.successVisible.set(false), 3e3);
      },
      error: (err) => {
        this.submitting.set(false);
        if (err.status === 401) {
          this.form.get("currentPassword")?.setErrors({ wrongPassword: true });
        } else {
          this.apiError.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0438\u0437\u043C\u0435\u043D\u0438\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043F\u043E\u0437\u0436\u0435.");
        }
      }
    });
  }
  static \u0275fac = function StudentProfileComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentProfileComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentProfileComponent, selectors: [["app-student-profile"]], decls: 52, vars: 29, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-header__eyebrow"], [1, "page-header__title"], [1, "identity-card"], [1, "identity-card__avatar"], [1, "identity-card__info"], [1, "identity-card__role"], ["aria-label", "\u0418\u0434\u0435\u043D\u0442\u0438\u0444\u0438\u043A\u0430\u0442\u043E\u0440 \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F", 1, "identity-card__id"], [1, "identity-card__group"], [1, "identity-card__platform-badge"], [1, "password-card"], [1, "password-card__heading"], ["novalidate", "", 3, "ngSubmit", "formGroup"], ["appearance", "outline", 1, "full-width"], ["matInput", "", "formControlName", "currentPassword", "autocomplete", "current-password", 3, "type"], ["mat-icon-button", "", "matSuffix", "", "type", "button", 3, "click"], ["aria-live", "assertive"], ["matInput", "", "formControlName", "newPassword", "autocomplete", "new-password", 3, "type"], ["role", "list", 1, "password-hints"], ["role", "listitem"], ["role", "alert", "aria-live", "assertive", 1, "error-banner"], ["role", "status", "aria-live", "polite", 1, "success-banner"], ["mat-flat-button", "", "type", "submit", 1, "submit-btn", 3, "disabled"], [1, "identity-card__group-label"], [1, "identity-card__group-value"], [1, "ph-warning-circle", "ph-fill"], [1, "ph-check-circle", "ph-fill"], ["diameter", "16", "strokeWidth", "2"]], template: function StudentProfileComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "header", 1)(2, "p", 2);
      \u0275\u0275text(3, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(4, "h1", 3);
      \u0275\u0275text(5, "\u041F\u0440\u043E\u0444\u0438\u043B\u044C");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(6, "div", 4)(7, "div", 5);
      \u0275\u0275text(8);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(9, "div", 6)(10, "span", 7);
      \u0275\u0275text(11, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(12, "span", 8);
      \u0275\u0275text(13);
      \u0275\u0275elementEnd();
      \u0275\u0275template(14, StudentProfileComponent_Conditional_14_Template, 5, 1, "span", 9);
      \u0275\u0275elementStart(15, "span", 10);
      \u0275\u0275text(16, "Web Cabinet");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(17, "div", 11)(18, "h2", 12);
      \u0275\u0275text(19, "\u0418\u0437\u043C\u0435\u043D\u0438\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(20, "form", 13);
      \u0275\u0275listener("ngSubmit", function StudentProfileComponent_Template_form_ngSubmit_20_listener() {
        return ctx.onSubmit();
      });
      \u0275\u0275elementStart(21, "mat-form-field", 14)(22, "mat-label");
      \u0275\u0275text(23, "\u0422\u0435\u043A\u0443\u0449\u0438\u0439 \u043F\u0430\u0440\u043E\u043B\u044C");
      \u0275\u0275elementEnd();
      \u0275\u0275element(24, "input", 15);
      \u0275\u0275elementStart(25, "button", 16);
      \u0275\u0275listener("click", function StudentProfileComponent_Template_button_click_25_listener() {
        return ctx.toggleCurrentPasswordVisibility();
      });
      \u0275\u0275element(26, "i");
      \u0275\u0275elementEnd();
      \u0275\u0275template(27, StudentProfileComponent_Conditional_27_Template, 2, 0, "mat-error", 17);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(28, "mat-form-field", 14)(29, "mat-label");
      \u0275\u0275text(30, "\u041D\u043E\u0432\u044B\u0439 \u043F\u0430\u0440\u043E\u043B\u044C");
      \u0275\u0275elementEnd();
      \u0275\u0275element(31, "input", 18);
      \u0275\u0275elementStart(32, "button", 16);
      \u0275\u0275listener("click", function StudentProfileComponent_Template_button_click_32_listener() {
        return ctx.toggleNewPasswordVisibility();
      });
      \u0275\u0275element(33, "i");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(34, "ul", 19)(35, "li", 20);
      \u0275\u0275element(36, "i");
      \u0275\u0275elementStart(37, "span");
      \u0275\u0275text(38, "\u041C\u0438\u043D\u0438\u043C\u0443\u043C 8 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(39, "li", 20);
      \u0275\u0275element(40, "i");
      \u0275\u0275elementStart(41, "span");
      \u0275\u0275text(42, "\u0425\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u0430 \u0437\u0430\u0433\u043B\u0430\u0432\u043D\u0430\u044F \u0431\u0443\u043A\u0432\u0430");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(43, "li", 20);
      \u0275\u0275element(44, "i");
      \u0275\u0275elementStart(45, "span");
      \u0275\u0275text(46, "\u0425\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u0430 \u0446\u0438\u0444\u0440\u0430");
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(47, StudentProfileComponent_Conditional_47_Template, 4, 1, "div", 21)(48, StudentProfileComponent_Conditional_48_Template, 4, 1, "div", 22);
      \u0275\u0275elementStart(49, "button", 23);
      \u0275\u0275template(50, StudentProfileComponent_Conditional_50_Template, 3, 0)(51, StudentProfileComponent_Conditional_51_Template, 2, 0, "span");
      \u0275\u0275elementEnd()()()();
    }
    if (rf & 2) {
      let tmp_2_0;
      let tmp_3_0;
      let tmp_6_0;
      let tmp_9_0;
      \u0275\u0275property("@routeFade", void 0);
      \u0275\u0275advance(8);
      \u0275\u0275textInterpolate(ctx.avatarInitials());
      \u0275\u0275advance(5);
      \u0275\u0275textInterpolate1(" ID ", (tmp_2_0 = ctx.currentUser()) == null ? null : tmp_2_0.id, " ");
      \u0275\u0275advance();
      \u0275\u0275conditional(((tmp_3_0 = ctx.currentUser()) == null ? null : tmp_3_0.groupId) ? 14 : -1);
      \u0275\u0275advance(6);
      \u0275\u0275property("formGroup", ctx.form);
      \u0275\u0275advance(4);
      \u0275\u0275property("type", ctx.showCurrentPassword() ? "text" : "password");
      \u0275\u0275attribute("aria-invalid", ((tmp_6_0 = ctx.form.get("currentPassword")) == null ? null : tmp_6_0.hasError("wrongPassword")) || null);
      \u0275\u0275advance();
      \u0275\u0275attribute("aria-label", ctx.showCurrentPassword() ? "\u0421\u043A\u0440\u044B\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C" : "\u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C");
      \u0275\u0275advance();
      \u0275\u0275classMap(ctx.showCurrentPassword() ? "ph-eye-slash" : "ph-eye");
      \u0275\u0275advance();
      \u0275\u0275conditional(((tmp_9_0 = ctx.form.get("currentPassword")) == null ? null : tmp_9_0.hasError("wrongPassword")) ? 27 : -1);
      \u0275\u0275advance(4);
      \u0275\u0275property("type", ctx.showNewPassword() ? "text" : "password");
      \u0275\u0275advance();
      \u0275\u0275attribute("aria-label", ctx.showNewPassword() ? "\u0421\u043A\u0440\u044B\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C" : "\u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C");
      \u0275\u0275advance();
      \u0275\u0275classMap(ctx.showNewPassword() ? "ph-eye-slash" : "ph-eye");
      \u0275\u0275advance(2);
      \u0275\u0275attribute("aria-label", "\u041C\u0438\u043D\u0438\u043C\u0443\u043C 8 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432: " + (ctx.hintMinLength() ? "\u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E" : "\u043D\u0435 \u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E"));
      \u0275\u0275advance();
      \u0275\u0275classMap(ctx.hintMinLength() ? "ph-check ph-fill hint-met" : "ph-circle hint-unmet");
      \u0275\u0275advance(3);
      \u0275\u0275attribute("aria-label", "\u0425\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u0430 \u0437\u0430\u0433\u043B\u0430\u0432\u043D\u0430\u044F \u0431\u0443\u043A\u0432\u0430: " + (ctx.hintUppercase() ? "\u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E" : "\u043D\u0435 \u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E"));
      \u0275\u0275advance();
      \u0275\u0275classMap(ctx.hintUppercase() ? "ph-check ph-fill hint-met" : "ph-circle hint-unmet");
      \u0275\u0275advance(3);
      \u0275\u0275attribute("aria-label", "\u0425\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u0430 \u0446\u0438\u0444\u0440\u0430: " + (ctx.hintDigit() ? "\u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E" : "\u043D\u0435 \u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E"));
      \u0275\u0275advance();
      \u0275\u0275classMap(ctx.hintDigit() ? "ph-check ph-fill hint-met" : "ph-circle hint-unmet");
      \u0275\u0275advance(3);
      \u0275\u0275conditional(ctx.apiError() ? 47 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.successVisible() ? 48 : -1);
      \u0275\u0275advance();
      \u0275\u0275property("disabled", ctx.form.invalid || ctx.submitting());
      \u0275\u0275attribute("aria-disabled", ctx.form.invalid || ctx.submitting());
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.submitting() ? 50 : 51);
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
    MatFormFieldModule,
    MatFormField,
    MatLabel,
    MatError,
    MatSuffix,
    MatInputModule,
    MatInput,
    MatButtonModule,
    MatButton,
    MatIconButton,
    MatProgressSpinnerModule,
    MatProgressSpinner
  ], styles: ["\n\n.identity-card[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-4, 16px);\n  background:\n    radial-gradient(\n      ellipse at 15% 20%,\n      color-mix(in oklab, var(--accent-primary) 16%, transparent),\n      transparent 55%),\n    var(--bg-secondary);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-xl);\n  padding: var(--space-6, 32px);\n  margin-bottom: var(--space-5, 24px);\n}\n.identity-card__avatar[_ngcontent-%COMP%] {\n  width: 64px;\n  height: 64px;\n  border-radius: var(--radius-2xl, 16px);\n  background: var(--gradient-brand, linear-gradient(135deg, var(--accent-primary), var(--accent-secondary)));\n  box-shadow: var(--glow-primary, 0 0 20px color-mix(in oklab, var(--accent-primary) 40%, transparent));\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  font-family: var(--font-mono);\n  font-weight: 600;\n  font-size: var(--text-base);\n  color: var(--bg-primary);\n  flex-shrink: 0;\n}\n.identity-card__info[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-1, 4px);\n}\n.identity-card__role[_ngcontent-%COMP%] {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  line-height: var(--leading-heading);\n}\n.identity-card__id[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n}\n.identity-card__group[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-1, 4px);\n}\n.identity-card__group-label[_ngcontent-%COMP%] {\n  font-size: var(--text-xs);\n  font-weight: 600;\n  text-transform: uppercase;\n  color: var(--text-muted);\n}\n.identity-card__group-value[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  font-weight: 600;\n}\n.identity-card__platform-badge[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  font-size: var(--text-xs);\n  color: var(--accent-secondary);\n  background: color-mix(in oklab, var(--accent-secondary) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-secondary) 25%, transparent);\n  border-radius: var(--radius-full, 9999px);\n  padding: 2px 10px;\n  width: fit-content;\n}\n.password-card[_ngcontent-%COMP%] {\n  background: var(--bg-elevated);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-xl);\n  padding: var(--space-6, 32px);\n}\n.password-card__heading[_ngcontent-%COMP%] {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  margin-bottom: var(--space-4, 16px);\n}\n.full-width[_ngcontent-%COMP%] {\n  width: 100%;\n  margin-bottom: var(--space-4, 16px);\n}\n.password-hints[_ngcontent-%COMP%] {\n  list-style: none;\n  padding: 0;\n  margin: 0 0 var(--space-4, 16px) 0;\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-1, 4px);\n}\n.password-hints[_ngcontent-%COMP%]   li[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-1, 4px);\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n}\n.hint-met[_ngcontent-%COMP%] {\n  color: var(--status-present, var(--accent-primary));\n}\n.hint-unmet[_ngcontent-%COMP%] {\n  color: var(--text-muted);\n}\n.submit-btn[_ngcontent-%COMP%] {\n  width: 100%;\n  min-height: 48px;\n  background: var(--accent-primary) !important;\n  color: var(--accent-primary-contrast, #0A0E17) !important;\n  border-radius: var(--radius-full, 9999px) !important;\n  font-weight: 600;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  gap: var(--space-2, 8px);\n}\n.error-banner[_ngcontent-%COMP%], \n.success-banner[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  padding: var(--space-3, 12px) var(--space-4, 16px);\n  border-radius: var(--radius-md);\n  font-size: var(--text-sm);\n  margin-bottom: var(--space-3, 12px);\n}\n.error-banner[_ngcontent-%COMP%] {\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  color: var(--accent-danger);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 25%, transparent);\n}\n.success-banner[_ngcontent-%COMP%] {\n  background: color-mix(in oklab, var(--status-present, var(--accent-primary)) 12%, transparent);\n  color: var(--status-present, var(--accent-primary));\n  border: 1px solid color-mix(in oklab, var(--status-present, var(--accent-primary)) 25%, transparent);\n}\n/*# sourceMappingURL=student-profile.component.css.map */"], data: { animation: [
    trigger("routeFade", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ]),
    trigger("toastSlide", [
      transition(":enter", [
        style({ opacity: 0, transform: "translateY(8px)" }),
        animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentProfileComponent, [{
    type: Component,
    args: [{ selector: "app-student-profile", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [
      CommonModule,
      ReactiveFormsModule,
      MatFormFieldModule,
      MatInputModule,
      MatButtonModule,
      MatProgressSpinnerModule
    ], animations: [
      trigger("routeFade", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ]),
      trigger("toastSlide", [
        transition(":enter", [
          style({ opacity: 0, transform: "translateY(8px)" }),
          animate("200ms cubic-bezier(0.16, 1, 0.3, 1)", style({ opacity: 1, transform: "translateY(0)" }))
        ])
      ])
    ], template: `<div class="page-stack" [@routeFade]>
  <header class="page-header">
    <p class="page-header__eyebrow">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</p>
    <h1 class="page-header__title">\u041F\u0440\u043E\u0444\u0438\u043B\u044C</h1>
  </header>

  <!-- Identity card -->
  <div class="identity-card">
    <div class="identity-card__avatar">{{ avatarInitials() }}</div>
    <div class="identity-card__info">
      <span class="identity-card__role">\u0421\u0442\u0443\u0434\u0435\u043D\u0442</span>
      <span class="identity-card__id" aria-label="\u0418\u0434\u0435\u043D\u0442\u0438\u0444\u0438\u043A\u0430\u0442\u043E\u0440 \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F">
        ID {{ currentUser()?.id }}
      </span>
      @if (currentUser()?.groupId) {
        <span class="identity-card__group">
          <span class="identity-card__group-label">\u0413\u0420\u0423\u041F\u041F\u0410</span>
          <span class="identity-card__group-value">{{ currentUser()!.groupId }}</span>
        </span>
      }
      <span class="identity-card__platform-badge">Web Cabinet</span>
    </div>
  </div>

  <!-- Password change form -->
  <div class="password-card">
    <h2 class="password-card__heading">\u0418\u0437\u043C\u0435\u043D\u0438\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C</h2>

    <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
      <!-- Current password -->
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>\u0422\u0435\u043A\u0443\u0449\u0438\u0439 \u043F\u0430\u0440\u043E\u043B\u044C</mat-label>
        <input
          matInput
          [type]="showCurrentPassword() ? 'text' : 'password'"
          formControlName="currentPassword"
          autocomplete="current-password"
          [attr.aria-invalid]="form.get('currentPassword')?.hasError('wrongPassword') || null">
        <button
          mat-icon-button
          matSuffix
          type="button"
          [attr.aria-label]="showCurrentPassword() ? '\u0421\u043A\u0440\u044B\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C' : '\u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C'"
          (click)="toggleCurrentPasswordVisibility()">
          <i [class]="showCurrentPassword() ? 'ph-eye-slash' : 'ph-eye'"></i>
        </button>
        @if (form.get('currentPassword')?.hasError('wrongPassword')) {
          <mat-error aria-live="assertive">\u041D\u0435\u0432\u0435\u0440\u043D\u044B\u0439 \u0442\u0435\u043A\u0443\u0449\u0438\u0439 \u043F\u0430\u0440\u043E\u043B\u044C.</mat-error>
        }
      </mat-form-field>

      <!-- New password -->
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>\u041D\u043E\u0432\u044B\u0439 \u043F\u0430\u0440\u043E\u043B\u044C</mat-label>
        <input
          matInput
          [type]="showNewPassword() ? 'text' : 'password'"
          formControlName="newPassword"
          autocomplete="new-password">
        <button
          mat-icon-button
          matSuffix
          type="button"
          [attr.aria-label]="showNewPassword() ? '\u0421\u043A\u0440\u044B\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C' : '\u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C'"
          (click)="toggleNewPasswordVisibility()">
          <i [class]="showNewPassword() ? 'ph-eye-slash' : 'ph-eye'"></i>
        </button>
      </mat-form-field>

      <!-- Validation hints -->
      <ul class="password-hints" role="list">
        <li role="listitem" [attr.aria-label]="'\u041C\u0438\u043D\u0438\u043C\u0443\u043C 8 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432: ' + (hintMinLength() ? '\u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E' : '\u043D\u0435 \u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E')">
          <i [class]="hintMinLength() ? 'ph-check ph-fill hint-met' : 'ph-circle hint-unmet'"></i>
          <span>\u041C\u0438\u043D\u0438\u043C\u0443\u043C 8 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432</span>
        </li>
        <li role="listitem" [attr.aria-label]="'\u0425\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u0430 \u0437\u0430\u0433\u043B\u0430\u0432\u043D\u0430\u044F \u0431\u0443\u043A\u0432\u0430: ' + (hintUppercase() ? '\u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E' : '\u043D\u0435 \u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E')">
          <i [class]="hintUppercase() ? 'ph-check ph-fill hint-met' : 'ph-circle hint-unmet'"></i>
          <span>\u0425\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u0430 \u0437\u0430\u0433\u043B\u0430\u0432\u043D\u0430\u044F \u0431\u0443\u043A\u0432\u0430</span>
        </li>
        <li role="listitem" [attr.aria-label]="'\u0425\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u0430 \u0446\u0438\u0444\u0440\u0430: ' + (hintDigit() ? '\u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E' : '\u043D\u0435 \u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D\u043E')">
          <i [class]="hintDigit() ? 'ph-check ph-fill hint-met' : 'ph-circle hint-unmet'"></i>
          <span>\u0425\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u0430 \u0446\u0438\u0444\u0440\u0430</span>
        </li>
      </ul>

      <!-- API error banner -->
      @if (apiError()) {
        <div class="error-banner" role="alert" aria-live="assertive">
          <i class="ph-warning-circle ph-fill"></i>
          <span>{{ apiError() }}</span>
        </div>
      }

      <!-- Success banner -->
      @if (successVisible()) {
        <div class="success-banner" [@toastSlide] role="status" aria-live="polite">
          <i class="ph-check-circle ph-fill"></i>
          <span>\u041F\u0430\u0440\u043E\u043B\u044C \u0443\u0441\u043F\u0435\u0448\u043D\u043E \u0438\u0437\u043C\u0435\u043D\u0451\u043D</span>
        </div>
      }

      <!-- Submit -->
      <button
        mat-flat-button
        type="submit"
        class="submit-btn"
        [disabled]="form.invalid || submitting()"
        [attr.aria-disabled]="form.invalid || submitting()">
        @if (submitting()) {
          <mat-spinner diameter="16" strokeWidth="2"></mat-spinner>
          <span>\u0421\u043E\u0445\u0440\u0430\u043D\u044F\u0435\u043C\u2026</span>
        } @else {
          <span>\u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C</span>
        }
      </button>
    </form>
  </div>
</div>
`, styles: ["/* src/app/features/student/profile/student-profile.component.css */\n.identity-card {\n  display: flex;\n  align-items: center;\n  gap: var(--space-4, 16px);\n  background:\n    radial-gradient(\n      ellipse at 15% 20%,\n      color-mix(in oklab, var(--accent-primary) 16%, transparent),\n      transparent 55%),\n    var(--bg-secondary);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-xl);\n  padding: var(--space-6, 32px);\n  margin-bottom: var(--space-5, 24px);\n}\n.identity-card__avatar {\n  width: 64px;\n  height: 64px;\n  border-radius: var(--radius-2xl, 16px);\n  background: var(--gradient-brand, linear-gradient(135deg, var(--accent-primary), var(--accent-secondary)));\n  box-shadow: var(--glow-primary, 0 0 20px color-mix(in oklab, var(--accent-primary) 40%, transparent));\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  font-family: var(--font-mono);\n  font-weight: 600;\n  font-size: var(--text-base);\n  color: var(--bg-primary);\n  flex-shrink: 0;\n}\n.identity-card__info {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-1, 4px);\n}\n.identity-card__role {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  line-height: var(--leading-heading);\n}\n.identity-card__id {\n  font-size: var(--text-xs);\n  font-family: var(--font-mono);\n  color: var(--text-muted);\n}\n.identity-card__group {\n  display: flex;\n  align-items: center;\n  gap: var(--space-1, 4px);\n}\n.identity-card__group-label {\n  font-size: var(--text-xs);\n  font-weight: 600;\n  text-transform: uppercase;\n  color: var(--text-muted);\n}\n.identity-card__group-value {\n  font-size: var(--text-sm);\n  font-weight: 600;\n}\n.identity-card__platform-badge {\n  display: inline-flex;\n  align-items: center;\n  font-size: var(--text-xs);\n  color: var(--accent-secondary);\n  background: color-mix(in oklab, var(--accent-secondary) 12%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-secondary) 25%, transparent);\n  border-radius: var(--radius-full, 9999px);\n  padding: 2px 10px;\n  width: fit-content;\n}\n.password-card {\n  background: var(--bg-elevated);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-xl);\n  padding: var(--space-6, 32px);\n}\n.password-card__heading {\n  font-size: var(--text-base);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  margin-bottom: var(--space-4, 16px);\n}\n.full-width {\n  width: 100%;\n  margin-bottom: var(--space-4, 16px);\n}\n.password-hints {\n  list-style: none;\n  padding: 0;\n  margin: 0 0 var(--space-4, 16px) 0;\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-1, 4px);\n}\n.password-hints li {\n  display: flex;\n  align-items: center;\n  gap: var(--space-1, 4px);\n  font-size: var(--text-xs);\n  color: var(--text-muted);\n}\n.hint-met {\n  color: var(--status-present, var(--accent-primary));\n}\n.hint-unmet {\n  color: var(--text-muted);\n}\n.submit-btn {\n  width: 100%;\n  min-height: 48px;\n  background: var(--accent-primary) !important;\n  color: var(--accent-primary-contrast, #0A0E17) !important;\n  border-radius: var(--radius-full, 9999px) !important;\n  font-weight: 600;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  gap: var(--space-2, 8px);\n}\n.error-banner,\n.success-banner {\n  display: flex;\n  align-items: center;\n  gap: var(--space-2, 8px);\n  padding: var(--space-3, 12px) var(--space-4, 16px);\n  border-radius: var(--radius-md);\n  font-size: var(--text-sm);\n  margin-bottom: var(--space-3, 12px);\n}\n.error-banner {\n  background: color-mix(in oklab, var(--accent-danger) 12%, transparent);\n  color: var(--accent-danger);\n  border: 1px solid color-mix(in oklab, var(--accent-danger) 25%, transparent);\n}\n.success-banner {\n  background: color-mix(in oklab, var(--status-present, var(--accent-primary)) 12%, transparent);\n  color: var(--status-present, var(--accent-primary));\n  border: 1px solid color-mix(in oklab, var(--status-present, var(--accent-primary)) 25%, transparent);\n}\n/*# sourceMappingURL=student-profile.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentProfileComponent, { className: "StudentProfileComponent", filePath: "src/app/features/student/profile/student-profile.component.ts", lineNumber: 46 });
})();
export {
  StudentProfileComponent
};
//# sourceMappingURL=chunk-NBQHNNYY.js.map
