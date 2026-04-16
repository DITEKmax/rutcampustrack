import {
  AuthApi
} from "./chunk-HQN62MRG.js";
import {
  AuthService
} from "./chunk-URGWZVMC.js";
import {
  DefaultValueAccessor,
  FormBuilder,
  FormControlName,
  FormGroupDirective,
  MaxLengthValidator,
  NgControlStatus,
  NgControlStatusGroup,
  ReactiveFormsModule,
  Validators,
  ɵNgNoValidate
} from "./chunk-XAMIXVT7.js";
import {
  Router
} from "./chunk-643P6YYN.js";
import "./chunk-7WMFDRFR.js";
import "./chunk-M5DKW26A.js";
import {
  Component,
  inject,
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
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate1
} from "./chunk-MFRIGFR2.js";

// src/app/features/login/login.component.ts
function LoginComponent_Conditional_17_Conditional_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 21);
    \u0275\u0275element(1, "i", 23);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r1.errorMessage, " ");
  }
}
function LoginComponent_Conditional_17_Conditional_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "span", 24);
    \u0275\u0275text(1, " \u0412\u0445\u043E\u0434... ");
  }
}
function LoginComponent_Conditional_17_Conditional_15_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275text(0, " \u0412\u043E\u0439\u0442\u0438 ");
  }
}
function LoginComponent_Conditional_17_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "form", 12);
    \u0275\u0275listener("ngSubmit", function LoginComponent_Conditional_17_Template_form_ngSubmit_0_listener() {
      \u0275\u0275restoreView(_r1);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1.onSubmit());
    });
    \u0275\u0275elementStart(1, "div", 13)(2, "label", 14);
    \u0275\u0275text(3, "\u041B\u043E\u0433\u0438\u043D");
    \u0275\u0275elementEnd();
    \u0275\u0275element(4, "input", 15);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "div", 13)(6, "label", 16);
    \u0275\u0275text(7, "\u041F\u0430\u0440\u043E\u043B\u044C");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(8, "div", 17);
    \u0275\u0275element(9, "input", 18);
    \u0275\u0275elementStart(10, "button", 19);
    \u0275\u0275listener("click", function LoginComponent_Conditional_17_Template_button_click_10_listener() {
      \u0275\u0275restoreView(_r1);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1.togglePasswordVisibility());
    });
    \u0275\u0275element(11, "i", 20);
    \u0275\u0275elementEnd()()();
    \u0275\u0275template(12, LoginComponent_Conditional_17_Conditional_12_Template, 3, 1, "div", 21);
    \u0275\u0275elementStart(13, "button", 22);
    \u0275\u0275template(14, LoginComponent_Conditional_17_Conditional_14_Template, 2, 0)(15, LoginComponent_Conditional_17_Conditional_15_Template, 1, 0);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    let tmp_2_0;
    let tmp_3_0;
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275property("formGroup", ctx_r1.form);
    \u0275\u0275advance(4);
    \u0275\u0275classProp("login-input--error", ((tmp_2_0 = ctx_r1.form.get("login")) == null ? null : tmp_2_0.invalid) && ((tmp_2_0 = ctx_r1.form.get("login")) == null ? null : tmp_2_0.touched));
    \u0275\u0275advance(5);
    \u0275\u0275classProp("login-input--error", ((tmp_3_0 = ctx_r1.form.get("password")) == null ? null : tmp_3_0.invalid) && ((tmp_3_0 = ctx_r1.form.get("password")) == null ? null : tmp_3_0.touched));
    \u0275\u0275property("type", ctx_r1.passwordVisible ? "text" : "password");
    \u0275\u0275advance();
    \u0275\u0275attribute("aria-label", ctx_r1.passwordVisible ? "\u0421\u043A\u0440\u044B\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C" : "\u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C")("aria-pressed", ctx_r1.passwordVisible);
    \u0275\u0275advance();
    \u0275\u0275classProp("ph-eye", !ctx_r1.passwordVisible)("ph-eye-slash", ctx_r1.passwordVisible);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r1.errorMessage ? 12 : -1);
    \u0275\u0275advance();
    \u0275\u0275property("disabled", ctx_r1.form.invalid || ctx_r1.loading);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r1.loading ? 14 : 15);
  }
}
function LoginComponent_Conditional_18_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 21);
    \u0275\u0275element(1, "i", 23);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r1.errorMessage, " ");
  }
}
function LoginComponent_Conditional_18_Conditional_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "span", 24);
    \u0275\u0275text(1, " \u0412\u0445\u043E\u0434... ");
  }
}
function LoginComponent_Conditional_18_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275text(0, " \u0412\u043E\u0439\u0442\u0438 ");
  }
}
function LoginComponent_Conditional_18_Template(rf, ctx) {
  if (rf & 1) {
    const _r3 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "form", 12);
    \u0275\u0275listener("ngSubmit", function LoginComponent_Conditional_18_Template_form_ngSubmit_0_listener() {
      \u0275\u0275restoreView(_r3);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1.onSubmitOtp());
    });
    \u0275\u0275elementStart(1, "p", 25);
    \u0275\u0275text(2, " \u041E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 Telegram-\u0431\u043E\u0442\u0430 \u0438 \u043E\u0442\u043F\u0440\u0430\u0432\u044C\u0442\u0435 \u043A\u043E\u043C\u0430\u043D\u0434\u0443 ");
    \u0275\u0275elementStart(3, "strong");
    \u0275\u0275text(4, "/login");
    \u0275\u0275elementEnd();
    \u0275\u0275text(5, " \u2014 \u0431\u043E\u0442 \u043F\u0440\u0438\u0448\u043B\u0451\u0442 6-\u0437\u043D\u0430\u0447\u043D\u044B\u0439 \u043A\u043E\u0434. \u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0435\u0433\u043E \u043D\u0438\u0436\u0435. ");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "div", 13)(7, "label", 26);
    \u0275\u0275text(8, "\u041A\u043E\u0434 \u0438\u0437 Telegram");
    \u0275\u0275elementEnd();
    \u0275\u0275element(9, "input", 27);
    \u0275\u0275elementEnd();
    \u0275\u0275template(10, LoginComponent_Conditional_18_Conditional_10_Template, 3, 1, "div", 21);
    \u0275\u0275elementStart(11, "button", 22);
    \u0275\u0275template(12, LoginComponent_Conditional_18_Conditional_12_Template, 2, 0)(13, LoginComponent_Conditional_18_Conditional_13_Template, 1, 0);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    let tmp_2_0;
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275property("formGroup", ctx_r1.otpForm);
    \u0275\u0275advance(9);
    \u0275\u0275classProp("login-input--error", ((tmp_2_0 = ctx_r1.otpForm.get("code")) == null ? null : tmp_2_0.invalid) && ((tmp_2_0 = ctx_r1.otpForm.get("code")) == null ? null : tmp_2_0.touched));
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r1.errorMessage ? 10 : -1);
    \u0275\u0275advance();
    \u0275\u0275property("disabled", ctx_r1.otpForm.invalid || ctx_r1.loading);
    \u0275\u0275advance();
    \u0275\u0275conditional(ctx_r1.loading ? 12 : 13);
  }
}
var LoginComponent = class _LoginComponent {
  fb = inject(FormBuilder);
  authService = inject(AuthService);
  authApi = inject(AuthApi);
  router = inject(Router);
  mode = "password";
  form = this.fb.group({
    login: ["", Validators.required],
    password: ["", Validators.required]
  });
  otpForm = this.fb.group({
    code: ["", [Validators.required, Validators.pattern(/^\d{6}$/)]]
  });
  loading = false;
  errorMessage = "";
  passwordVisible = false;
  setMode(m) {
    this.mode = m;
    this.errorMessage = "";
  }
  togglePasswordVisibility() {
    this.passwordVisible = !this.passwordVisible;
  }
  onSubmit() {
    if (this.form.invalid)
      return;
    this.loading = true;
    this.errorMessage = "";
    this.form.disable();
    const { login, password } = this.form.getRawValue();
    this.authApi.login({ login, password }).subscribe({
      next: (tokens) => {
        this.authService.setTokens(tokens.accessToken, tokens.refreshToken);
        const target = this.authService.resolveDashboardFor(this.authService.currentUser());
        this.router.navigateByUrl(target);
      },
      error: (err) => {
        this.loading = false;
        this.form.enable();
        this.form.patchValue({ password: "" });
        this.errorMessage = err.status === 401 ? "\u041D\u0435\u0432\u0435\u0440\u043D\u044B\u0439 \u043B\u043E\u0433\u0438\u043D \u0438\u043B\u0438 \u043F\u0430\u0440\u043E\u043B\u044C. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0434\u0430\u043D\u043D\u044B\u0435 \u0438 \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0441\u043D\u043E\u0432\u0430." : "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043F\u043E\u0434\u043A\u043B\u044E\u0447\u0438\u0442\u044C\u0441\u044F \u043A \u0441\u0435\u0440\u0432\u0435\u0440\u0443. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0441\u043E\u0435\u0434\u0438\u043D\u0435\u043D\u0438\u0435.";
      }
    });
  }
  onSubmitOtp() {
    if (this.otpForm.invalid)
      return;
    this.loading = true;
    this.errorMessage = "";
    this.otpForm.disable();
    const code = this.otpForm.getRawValue().code;
    this.authApi.verifyOtpByCode(code).subscribe({
      next: (tokens) => {
        this.authService.setTokens(tokens.accessToken, tokens.refreshToken);
        const target = this.authService.resolveDashboardFor(this.authService.currentUser());
        this.router.navigateByUrl(target);
      },
      error: (err) => {
        this.loading = false;
        this.otpForm.enable();
        this.otpForm.patchValue({ code: "" });
        if (err.status === 401) {
          this.errorMessage = "\u041A\u043E\u0434 \u043D\u0435\u0432\u0435\u0440\u043D\u044B\u0439 \u0438\u043B\u0438 \u0438\u0441\u0442\u0451\u043A. \u0417\u0430\u043F\u0440\u043E\u0441\u0438\u0442\u0435 \u043D\u043E\u0432\u044B\u0439 \u0447\u0435\u0440\u0435\u0437 \u0431\u043E\u0442\u0430.";
        } else if (err.status === 429) {
          this.errorMessage = "\u0421\u043B\u0438\u0448\u043A\u043E\u043C \u043C\u043D\u043E\u0433\u043E \u043F\u043E\u043F\u044B\u0442\u043E\u043A. \u041F\u043E\u0434\u043E\u0436\u0434\u0438\u0442\u0435 \u043D\u0435\u043C\u043D\u043E\u0433\u043E \u0438 \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0441\u043D\u043E\u0432\u0430.";
        } else {
          this.errorMessage = "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043F\u043E\u0434\u043A\u043B\u044E\u0447\u0438\u0442\u044C\u0441\u044F \u043A \u0441\u0435\u0440\u0432\u0435\u0440\u0443. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0441\u043E\u0435\u0434\u0438\u043D\u0435\u043D\u0438\u0435.";
        }
      }
    });
  }
  static \u0275fac = function LoginComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _LoginComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _LoginComponent, selectors: [["app-login"]], decls: 19, vars: 6, consts: [[1, "login-page"], [1, "login-card"], [1, "login-brand"], ["aria-hidden", "true", 1, "login-brand__icon"], ["src", "logo.svg", "alt", "", 1, "login-brand__logo"], [1, "login-brand__title"], [1, "login-brand__subtitle"], [1, "login-tabs"], ["type", "button", 1, "login-tab", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-lock-simple"], ["aria-hidden", "true", 1, "ph", "ph-telegram-logo"], [1, "login-form", 3, "formGroup"], [1, "login-form", 3, "ngSubmit", "formGroup"], [1, "login-field"], ["for", "login-input", 1, "login-label"], ["id", "login-input", "formControlName", "login", "autocomplete", "username", "placeholder", "student1", 1, "login-input"], ["for", "password-input", 1, "login-label"], [1, "login-password"], ["id", "password-input", "formControlName", "password", "autocomplete", "current-password", "placeholder", "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022", 1, "login-input", "login-input--password", 3, "type"], ["type", "button", "tabindex", "0", 1, "login-password__toggle", 3, "click"], ["aria-hidden", "true", 1, "ph"], ["role", "alert", 1, "login-error"], ["type", "submit", 1, "login-submit", 3, "disabled"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"], ["aria-hidden", "true", 1, "login-spinner"], [1, "login-otp-info__text"], ["for", "otp-code-input", 1, "login-label"], ["id", "otp-code-input", "formControlName", "code", "inputmode", "numeric", "autocomplete", "one-time-code", "maxlength", "6", "placeholder", "123456", 1, "login-input", "login-input--mono"]], template: function LoginComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "div", 1)(2, "div", 2)(3, "div", 3);
      \u0275\u0275element(4, "img", 4);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "div")(6, "h1", 5);
      \u0275\u0275text(7, "RutTrack");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(8, "p", 6);
      \u0275\u0275text(9, "\u0421\u0438\u0441\u0442\u0435\u043C\u0430 \u0443\u0447\u0451\u0442\u0430 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438 \u0420\u0423\u0422 \u041C\u0418\u0418\u0422");
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(10, "div", 7)(11, "button", 8);
      \u0275\u0275listener("click", function LoginComponent_Template_button_click_11_listener() {
        return ctx.setMode("password");
      });
      \u0275\u0275element(12, "i", 9);
      \u0275\u0275text(13, " \u041F\u0430\u0440\u043E\u043B\u044C ");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(14, "button", 8);
      \u0275\u0275listener("click", function LoginComponent_Template_button_click_14_listener() {
        return ctx.setMode("otp");
      });
      \u0275\u0275element(15, "i", 10);
      \u0275\u0275text(16, " OTP \u0447\u0435\u0440\u0435\u0437 Telegram ");
      \u0275\u0275elementEnd()();
      \u0275\u0275template(17, LoginComponent_Conditional_17_Template, 16, 15, "form", 11)(18, LoginComponent_Conditional_18_Template, 14, 6, "form", 11);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275advance(11);
      \u0275\u0275classProp("login-tab--active", ctx.mode === "password");
      \u0275\u0275advance(3);
      \u0275\u0275classProp("login-tab--active", ctx.mode === "otp");
      \u0275\u0275advance(3);
      \u0275\u0275conditional(ctx.mode === "password" ? 17 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.mode === "otp" ? 18 : -1);
    }
  }, dependencies: [ReactiveFormsModule, \u0275NgNoValidate, DefaultValueAccessor, NgControlStatus, NgControlStatusGroup, MaxLengthValidator, FormGroupDirective, FormControlName], styles: ['\n\n.login-page[_ngcontent-%COMP%] {\n  min-height: 100vh;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  background: var(--bg-primary);\n  padding: var(--space-5);\n  position: relative;\n  overflow: hidden;\n  isolation: isolate;\n}\n.login-page[_ngcontent-%COMP%]::before {\n  content: "";\n  position: absolute;\n  inset: -25%;\n  z-index: -1;\n  background:\n    radial-gradient(\n      45% 45% at 20% 30%,\n      color-mix(in oklab, var(--accent-primary) 22%, transparent) 0%,\n      transparent 70%),\n    radial-gradient(\n      40% 40% at 80% 70%,\n      color-mix(in oklab, var(--accent-secondary) 22%, transparent) 0%,\n      transparent 70%),\n    radial-gradient(\n      35% 35% at 60% 20%,\n      color-mix(in oklab, var(--accent-info, var(--accent-secondary)) 18%, transparent) 0%,\n      transparent 70%);\n  filter: blur(40px);\n  animation: _ngcontent-%COMP%_login-bg 18s ease-in-out infinite alternate;\n}\n@keyframes _ngcontent-%COMP%_login-bg {\n  0% {\n    transform: translate3d(0, 0, 0) scale(1);\n  }\n  50% {\n    transform: translate3d(2%, -3%, 0) scale(1.05);\n  }\n  100% {\n    transform: translate3d(-2%, 3%, 0) scale(1.02);\n  }\n}\n@media (prefers-reduced-motion: reduce) {\n  .login-page[_ngcontent-%COMP%]::before {\n    animation: none;\n  }\n}\n.login-card[_ngcontent-%COMP%] {\n  width: 100%;\n  max-width: 420px;\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-xl);\n  padding: var(--space-8) var(--space-7);\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-6);\n  box-shadow: var(--shadow-lg);\n}\n.login-brand[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n}\n.login-brand__icon[_ngcontent-%COMP%] {\n  width: 48px;\n  height: 48px;\n  border-radius: var(--radius-md);\n  display: grid;\n  place-items: center;\n  flex-shrink: 0;\n  overflow: hidden;\n  box-shadow: var(--glow-primary, none);\n}\n.login-brand__logo[_ngcontent-%COMP%] {\n  width: 100%;\n  height: 100%;\n  display: block;\n}\n.login-brand__icon[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 26px;\n}\n.login-brand__title[_ngcontent-%COMP%] {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 1.25rem;\n  color: var(--text-primary);\n  margin: 0;\n  letter-spacing: -0.02em;\n  line-height: 1.2;\n}\n.login-brand__subtitle[_ngcontent-%COMP%] {\n  font-size: 0.8125rem;\n  color: var(--text-secondary);\n  margin: 2px 0 0;\n  line-height: 1.4;\n}\n.login-tabs[_ngcontent-%COMP%] {\n  display: flex;\n  gap: var(--space-2);\n  background: var(--bg-surface);\n  border-radius: var(--radius-lg);\n  padding: 4px;\n  border: 1px solid var(--border-subtle);\n}\n.login-tab[_ngcontent-%COMP%] {\n  flex: 1;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  gap: var(--space-2);\n  padding: 8px 12px;\n  border-radius: var(--radius-md);\n  border: 0;\n  background: transparent;\n  color: var(--text-secondary);\n  font-family: var(--font-sans);\n  font-size: 0.875rem;\n  font-weight: 500;\n  cursor: pointer;\n  transition: background var(--duration-fast) var(--ease-out), color var(--duration-fast) var(--ease-out);\n}\n.login-tab[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 16px;\n}\n.login-tab--active[_ngcontent-%COMP%] {\n  background: var(--bg-elevated);\n  color: var(--text-primary);\n  box-shadow: var(--shadow-sm);\n}\n.login-tab[_ngcontent-%COMP%]:not(.login-tab--active):hover {\n  color: var(--text-primary);\n  background: color-mix(in oklab, var(--bg-elevated) 50%, transparent);\n}\n.login-form[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-4);\n}\n.login-field[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2);\n}\n.login-label[_ngcontent-%COMP%] {\n  font-size: 0.8125rem;\n  font-weight: 600;\n  color: var(--text-secondary);\n  letter-spacing: 0.01em;\n}\n.login-input[_ngcontent-%COMP%] {\n  width: 100%;\n  padding: 10px 14px;\n  background: var(--bg-surface);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-md);\n  color: var(--text-primary);\n  font-family: var(--font-sans);\n  font-size: 0.9375rem;\n  outline: none;\n  transition: border-color var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out);\n  box-sizing: border-box;\n}\n.login-input[_ngcontent-%COMP%]::placeholder {\n  color: var(--text-muted);\n}\n.login-input[_ngcontent-%COMP%]:focus {\n  border-color: var(--accent-primary);\n  box-shadow: 0 0 0 3px color-mix(in oklab, var(--accent-primary) 18%, transparent);\n}\n.login-input--error[_ngcontent-%COMP%] {\n  border-color: var(--accent-danger);\n  box-shadow: 0 0 0 3px color-mix(in oklab, var(--accent-danger) 15%, transparent);\n}\n.login-input--mono[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  letter-spacing: 0.1em;\n  font-size: 1.25rem;\n  text-align: center;\n}\n.login-password[_ngcontent-%COMP%] {\n  position: relative;\n  display: block;\n}\n.login-input--password[_ngcontent-%COMP%] {\n  padding-right: 44px;\n}\n.login-password__toggle[_ngcontent-%COMP%] {\n  position: absolute;\n  top: 50%;\n  right: 6px;\n  transform: translateY(-50%);\n  width: 32px;\n  height: 32px;\n  display: grid;\n  place-items: center;\n  border: 0;\n  background: transparent;\n  color: var(--text-muted);\n  cursor: pointer;\n  border-radius: var(--radius-sm, 6px);\n  transition: color var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out);\n}\n.login-password__toggle[_ngcontent-%COMP%]:hover {\n  color: var(--text-primary);\n}\n.login-password__toggle[_ngcontent-%COMP%]:focus-visible {\n  outline: none;\n  color: var(--accent-primary);\n  box-shadow: 0 0 0 2px color-mix(in oklab, var(--accent-primary) 40%, transparent);\n}\n.login-password__toggle[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 18px;\n}\n.login-error[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: flex-start;\n  gap: var(--space-2);\n  padding: var(--space-3) var(--space-4);\n  background: color-mix(in srgb, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in srgb, var(--accent-danger) 28%, transparent);\n  border-radius: var(--radius-md);\n  color: var(--accent-danger);\n  font-size: 0.875rem;\n  line-height: 1.5;\n}\n.login-error[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 18px;\n  flex-shrink: 0;\n  margin-top: 1px;\n}\n.login-submit[_ngcontent-%COMP%] {\n  width: 100%;\n  padding: 12px 20px;\n  border-radius: var(--radius-full);\n  border: 0;\n  background: var(--accent-primary);\n  color: var(--accent-primary-contrast);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 0.9375rem;\n  cursor: pointer;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  gap: var(--space-2);\n  box-shadow: var(--glow-primary);\n  transition:\n    transform var(--duration-base) var(--ease-out),\n    box-shadow var(--duration-base) var(--ease-out),\n    opacity var(--duration-fast) var(--ease-out);\n  margin-top: var(--space-2);\n}\n.login-submit[_ngcontent-%COMP%]:hover:not(:disabled) {\n  transform: translateY(-1px);\n  box-shadow: 0 0 28px color-mix(in oklab, var(--accent-primary) 50%, transparent);\n}\n.login-submit[_ngcontent-%COMP%]:active:not(:disabled) {\n  transform: scale(0.98);\n}\n.login-submit[_ngcontent-%COMP%]:disabled {\n  opacity: 0.5;\n  cursor: not-allowed;\n  box-shadow: none;\n}\n.login-submit--secondary[_ngcontent-%COMP%] {\n  background: var(--bg-elevated);\n  color: var(--text-primary);\n  box-shadow: none;\n  border: 1px solid var(--border-default);\n  margin-top: 0;\n}\n.login-submit--secondary[_ngcontent-%COMP%]:hover:not(:disabled) {\n  box-shadow: none;\n  background: color-mix(in oklab, var(--bg-elevated) 80%, var(--accent-primary) 20%);\n  border-color: var(--border-accent);\n}\n.login-link[_ngcontent-%COMP%] {\n  background: none;\n  border: none;\n  color: var(--accent-secondary);\n  font-size: 0.875rem;\n  cursor: pointer;\n  text-align: center;\n  padding: var(--space-1);\n  text-decoration: underline;\n  text-underline-offset: 3px;\n}\n.login-link[_ngcontent-%COMP%]:hover {\n  color: var(--text-primary);\n}\n.login-spinner[_ngcontent-%COMP%] {\n  display: inline-block;\n  width: 16px;\n  height: 16px;\n  border: 2px solid color-mix(in oklab, currentColor 30%, transparent);\n  border-top-color: currentColor;\n  border-radius: 50%;\n  animation: _ngcontent-%COMP%_login-spin 0.7s linear infinite;\n}\n@keyframes _ngcontent-%COMP%_login-spin {\n  to {\n    transform: rotate(360deg);\n  }\n}\n.login-otp-info[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-4);\n  text-align: center;\n  padding: var(--space-4) 0;\n}\n.login-otp-info__icon[_ngcontent-%COMP%] {\n  width: 56px;\n  height: 56px;\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-secondary) 15%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-secondary) 35%, transparent);\n  display: grid;\n  place-items: center;\n  color: var(--accent-secondary);\n}\n.login-otp-info__icon[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 28px;\n}\n.login-otp-info__title[_ngcontent-%COMP%] {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 1rem;\n  color: var(--text-primary);\n  margin: 0;\n}\n.login-otp-info__text[_ngcontent-%COMP%] {\n  font-size: 0.875rem;\n  color: var(--text-secondary);\n  line-height: 1.6;\n  margin: 0;\n  max-width: 34ch;\n}\n/*# sourceMappingURL=login.component.css.map */'] });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(LoginComponent, [{
    type: Component,
    args: [{ selector: "app-login", standalone: true, imports: [ReactiveFormsModule], template: `<div class="login-page">
  <div class="login-card">
    <!-- \u041B\u043E\u0433\u043E\u0442\u0438\u043F / \u0431\u0440\u0435\u043D\u0434 -->
    <div class="login-brand">
      <div class="login-brand__icon" aria-hidden="true">
        <img src="logo.svg" alt="" class="login-brand__logo" />
      </div>
      <div>
        <h1 class="login-brand__title">RutTrack</h1>
        <p class="login-brand__subtitle">\u0421\u0438\u0441\u0442\u0435\u043C\u0430 \u0443\u0447\u0451\u0442\u0430 \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438 \u0420\u0423\u0422 \u041C\u0418\u0418\u0422</p>
      </div>
    </div>

    <!-- \u041F\u0435\u0440\u0435\u043A\u043B\u044E\u0447\u0430\u0442\u0435\u043B\u044C \u0440\u0435\u0436\u0438\u043C\u0430 \u0432\u0445\u043E\u0434\u0430 -->
    <div class="login-tabs">
      <button type="button" class="login-tab" [class.login-tab--active]="mode === 'password'" (click)="setMode('password')">
        <i class="ph ph-lock-simple" aria-hidden="true"></i>
        \u041F\u0430\u0440\u043E\u043B\u044C
      </button>
      <button type="button" class="login-tab" [class.login-tab--active]="mode === 'otp'" (click)="setMode('otp')">
        <i class="ph ph-telegram-logo" aria-hidden="true"></i>
        OTP \u0447\u0435\u0440\u0435\u0437 Telegram
      </button>
    </div>

    <!-- \u0424\u043E\u0440\u043C\u0430 \u0432\u0445\u043E\u0434\u0430 \u043F\u043E \u043F\u0430\u0440\u043E\u043B\u044E -->
    @if (mode === 'password') {
      <form [formGroup]="form" (ngSubmit)="onSubmit()" class="login-form">
        <div class="login-field">
          <label class="login-label" for="login-input">\u041B\u043E\u0433\u0438\u043D</label>
          <input
            id="login-input"
            class="login-input"
            formControlName="login"
            autocomplete="username"
            placeholder="student1"
            [class.login-input--error]="form.get('login')?.invalid && form.get('login')?.touched"
          />
        </div>

        <div class="login-field">
          <label class="login-label" for="password-input">\u041F\u0430\u0440\u043E\u043B\u044C</label>
          <div class="login-password">
            <input
              id="password-input"
              class="login-input login-input--password"
              [type]="passwordVisible ? 'text' : 'password'"
              formControlName="password"
              autocomplete="current-password"
              placeholder="\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"
              [class.login-input--error]="form.get('password')?.invalid && form.get('password')?.touched"
            />
            <button
              type="button"
              class="login-password__toggle"
              (click)="togglePasswordVisibility()"
              [attr.aria-label]="passwordVisible ? '\u0421\u043A\u0440\u044B\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C' : '\u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C'"
              [attr.aria-pressed]="passwordVisible"
              tabindex="0"
            >
              <i class="ph" [class.ph-eye]="!passwordVisible" [class.ph-eye-slash]="passwordVisible" aria-hidden="true"></i>
            </button>
          </div>
        </div>

        @if (errorMessage) {
          <div class="login-error" role="alert">
            <i class="ph ph-warning-circle" aria-hidden="true"></i>
            {{ errorMessage }}
          </div>
        }

        <button type="submit" class="login-submit" [disabled]="form.invalid || loading">
          @if (loading) {
            <span class="login-spinner" aria-hidden="true"></span>
            \u0412\u0445\u043E\u0434...
          } @else {
            \u0412\u043E\u0439\u0442\u0438
          }
        </button>
      </form>
    }

    <!-- OTP-\u0432\u0445\u043E\u0434 \u0447\u0435\u0440\u0435\u0437 Telegram -->
    @if (mode === 'otp') {
      <form [formGroup]="otpForm" (ngSubmit)="onSubmitOtp()" class="login-form">
        <p class="login-otp-info__text">
          \u041E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 Telegram-\u0431\u043E\u0442\u0430 \u0438 \u043E\u0442\u043F\u0440\u0430\u0432\u044C\u0442\u0435 \u043A\u043E\u043C\u0430\u043D\u0434\u0443 <strong>/login</strong> \u2014
          \u0431\u043E\u0442 \u043F\u0440\u0438\u0448\u043B\u0451\u0442 6-\u0437\u043D\u0430\u0447\u043D\u044B\u0439 \u043A\u043E\u0434. \u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0435\u0433\u043E \u043D\u0438\u0436\u0435.
        </p>

        <div class="login-field">
          <label class="login-label" for="otp-code-input">\u041A\u043E\u0434 \u0438\u0437 Telegram</label>
          <input
            id="otp-code-input"
            class="login-input login-input--mono"
            formControlName="code"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            placeholder="123456"
            [class.login-input--error]="otpForm.get('code')?.invalid && otpForm.get('code')?.touched"
          />
        </div>

        @if (errorMessage) {
          <div class="login-error" role="alert">
            <i class="ph ph-warning-circle" aria-hidden="true"></i>
            {{ errorMessage }}
          </div>
        }

        <button type="submit" class="login-submit" [disabled]="otpForm.invalid || loading">
          @if (loading) {
            <span class="login-spinner" aria-hidden="true"></span>
            \u0412\u0445\u043E\u0434...
          } @else {
            \u0412\u043E\u0439\u0442\u0438
          }
        </button>
      </form>
    }
  </div>
</div>
`, styles: ['/* src/app/features/login/login.component.css */\n.login-page {\n  min-height: 100vh;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  background: var(--bg-primary);\n  padding: var(--space-5);\n  position: relative;\n  overflow: hidden;\n  isolation: isolate;\n}\n.login-page::before {\n  content: "";\n  position: absolute;\n  inset: -25%;\n  z-index: -1;\n  background:\n    radial-gradient(\n      45% 45% at 20% 30%,\n      color-mix(in oklab, var(--accent-primary) 22%, transparent) 0%,\n      transparent 70%),\n    radial-gradient(\n      40% 40% at 80% 70%,\n      color-mix(in oklab, var(--accent-secondary) 22%, transparent) 0%,\n      transparent 70%),\n    radial-gradient(\n      35% 35% at 60% 20%,\n      color-mix(in oklab, var(--accent-info, var(--accent-secondary)) 18%, transparent) 0%,\n      transparent 70%);\n  filter: blur(40px);\n  animation: login-bg 18s ease-in-out infinite alternate;\n}\n@keyframes login-bg {\n  0% {\n    transform: translate3d(0, 0, 0) scale(1);\n  }\n  50% {\n    transform: translate3d(2%, -3%, 0) scale(1.05);\n  }\n  100% {\n    transform: translate3d(-2%, 3%, 0) scale(1.02);\n  }\n}\n@media (prefers-reduced-motion: reduce) {\n  .login-page::before {\n    animation: none;\n  }\n}\n.login-card {\n  width: 100%;\n  max-width: 420px;\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  border-radius: var(--radius-xl);\n  padding: var(--space-8) var(--space-7);\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-6);\n  box-shadow: var(--shadow-lg);\n}\n.login-brand {\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n}\n.login-brand__icon {\n  width: 48px;\n  height: 48px;\n  border-radius: var(--radius-md);\n  display: grid;\n  place-items: center;\n  flex-shrink: 0;\n  overflow: hidden;\n  box-shadow: var(--glow-primary, none);\n}\n.login-brand__logo {\n  width: 100%;\n  height: 100%;\n  display: block;\n}\n.login-brand__icon i {\n  font-size: 26px;\n}\n.login-brand__title {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 1.25rem;\n  color: var(--text-primary);\n  margin: 0;\n  letter-spacing: -0.02em;\n  line-height: 1.2;\n}\n.login-brand__subtitle {\n  font-size: 0.8125rem;\n  color: var(--text-secondary);\n  margin: 2px 0 0;\n  line-height: 1.4;\n}\n.login-tabs {\n  display: flex;\n  gap: var(--space-2);\n  background: var(--bg-surface);\n  border-radius: var(--radius-lg);\n  padding: 4px;\n  border: 1px solid var(--border-subtle);\n}\n.login-tab {\n  flex: 1;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  gap: var(--space-2);\n  padding: 8px 12px;\n  border-radius: var(--radius-md);\n  border: 0;\n  background: transparent;\n  color: var(--text-secondary);\n  font-family: var(--font-sans);\n  font-size: 0.875rem;\n  font-weight: 500;\n  cursor: pointer;\n  transition: background var(--duration-fast) var(--ease-out), color var(--duration-fast) var(--ease-out);\n}\n.login-tab i {\n  font-size: 16px;\n}\n.login-tab--active {\n  background: var(--bg-elevated);\n  color: var(--text-primary);\n  box-shadow: var(--shadow-sm);\n}\n.login-tab:not(.login-tab--active):hover {\n  color: var(--text-primary);\n  background: color-mix(in oklab, var(--bg-elevated) 50%, transparent);\n}\n.login-form {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-4);\n}\n.login-field {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2);\n}\n.login-label {\n  font-size: 0.8125rem;\n  font-weight: 600;\n  color: var(--text-secondary);\n  letter-spacing: 0.01em;\n}\n.login-input {\n  width: 100%;\n  padding: 10px 14px;\n  background: var(--bg-surface);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-md);\n  color: var(--text-primary);\n  font-family: var(--font-sans);\n  font-size: 0.9375rem;\n  outline: none;\n  transition: border-color var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out);\n  box-sizing: border-box;\n}\n.login-input::placeholder {\n  color: var(--text-muted);\n}\n.login-input:focus {\n  border-color: var(--accent-primary);\n  box-shadow: 0 0 0 3px color-mix(in oklab, var(--accent-primary) 18%, transparent);\n}\n.login-input--error {\n  border-color: var(--accent-danger);\n  box-shadow: 0 0 0 3px color-mix(in oklab, var(--accent-danger) 15%, transparent);\n}\n.login-input--mono {\n  font-family: var(--font-mono);\n  letter-spacing: 0.1em;\n  font-size: 1.25rem;\n  text-align: center;\n}\n.login-password {\n  position: relative;\n  display: block;\n}\n.login-input--password {\n  padding-right: 44px;\n}\n.login-password__toggle {\n  position: absolute;\n  top: 50%;\n  right: 6px;\n  transform: translateY(-50%);\n  width: 32px;\n  height: 32px;\n  display: grid;\n  place-items: center;\n  border: 0;\n  background: transparent;\n  color: var(--text-muted);\n  cursor: pointer;\n  border-radius: var(--radius-sm, 6px);\n  transition: color var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out);\n}\n.login-password__toggle:hover {\n  color: var(--text-primary);\n}\n.login-password__toggle:focus-visible {\n  outline: none;\n  color: var(--accent-primary);\n  box-shadow: 0 0 0 2px color-mix(in oklab, var(--accent-primary) 40%, transparent);\n}\n.login-password__toggle i {\n  font-size: 18px;\n}\n.login-error {\n  display: flex;\n  align-items: flex-start;\n  gap: var(--space-2);\n  padding: var(--space-3) var(--space-4);\n  background: color-mix(in srgb, var(--accent-danger) 12%, transparent);\n  border: 1px solid color-mix(in srgb, var(--accent-danger) 28%, transparent);\n  border-radius: var(--radius-md);\n  color: var(--accent-danger);\n  font-size: 0.875rem;\n  line-height: 1.5;\n}\n.login-error i {\n  font-size: 18px;\n  flex-shrink: 0;\n  margin-top: 1px;\n}\n.login-submit {\n  width: 100%;\n  padding: 12px 20px;\n  border-radius: var(--radius-full);\n  border: 0;\n  background: var(--accent-primary);\n  color: var(--accent-primary-contrast);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 0.9375rem;\n  cursor: pointer;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  gap: var(--space-2);\n  box-shadow: var(--glow-primary);\n  transition:\n    transform var(--duration-base) var(--ease-out),\n    box-shadow var(--duration-base) var(--ease-out),\n    opacity var(--duration-fast) var(--ease-out);\n  margin-top: var(--space-2);\n}\n.login-submit:hover:not(:disabled) {\n  transform: translateY(-1px);\n  box-shadow: 0 0 28px color-mix(in oklab, var(--accent-primary) 50%, transparent);\n}\n.login-submit:active:not(:disabled) {\n  transform: scale(0.98);\n}\n.login-submit:disabled {\n  opacity: 0.5;\n  cursor: not-allowed;\n  box-shadow: none;\n}\n.login-submit--secondary {\n  background: var(--bg-elevated);\n  color: var(--text-primary);\n  box-shadow: none;\n  border: 1px solid var(--border-default);\n  margin-top: 0;\n}\n.login-submit--secondary:hover:not(:disabled) {\n  box-shadow: none;\n  background: color-mix(in oklab, var(--bg-elevated) 80%, var(--accent-primary) 20%);\n  border-color: var(--border-accent);\n}\n.login-link {\n  background: none;\n  border: none;\n  color: var(--accent-secondary);\n  font-size: 0.875rem;\n  cursor: pointer;\n  text-align: center;\n  padding: var(--space-1);\n  text-decoration: underline;\n  text-underline-offset: 3px;\n}\n.login-link:hover {\n  color: var(--text-primary);\n}\n.login-spinner {\n  display: inline-block;\n  width: 16px;\n  height: 16px;\n  border: 2px solid color-mix(in oklab, currentColor 30%, transparent);\n  border-top-color: currentColor;\n  border-radius: 50%;\n  animation: login-spin 0.7s linear infinite;\n}\n@keyframes login-spin {\n  to {\n    transform: rotate(360deg);\n  }\n}\n.login-otp-info {\n  display: flex;\n  flex-direction: column;\n  align-items: center;\n  gap: var(--space-4);\n  text-align: center;\n  padding: var(--space-4) 0;\n}\n.login-otp-info__icon {\n  width: 56px;\n  height: 56px;\n  border-radius: var(--radius-md);\n  background: color-mix(in oklab, var(--accent-secondary) 15%, transparent);\n  border: 1px solid color-mix(in oklab, var(--accent-secondary) 35%, transparent);\n  display: grid;\n  place-items: center;\n  color: var(--accent-secondary);\n}\n.login-otp-info__icon i {\n  font-size: 28px;\n}\n.login-otp-info__title {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 1rem;\n  color: var(--text-primary);\n  margin: 0;\n}\n.login-otp-info__text {\n  font-size: 0.875rem;\n  color: var(--text-secondary);\n  line-height: 1.6;\n  margin: 0;\n  max-width: 34ch;\n}\n/*# sourceMappingURL=login.component.css.map */\n'] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(LoginComponent, { className: "LoginComponent", filePath: "src/app/features/login/login.component.ts", lineNumber: 14 });
})();
export {
  LoginComponent
};
//# sourceMappingURL=chunk-2O4DPRKE.js.map
