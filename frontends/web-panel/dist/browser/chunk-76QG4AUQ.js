import {
  MatProgressSpinner,
  MatProgressSpinnerModule
} from "./chunk-G7R5U6WL.js";
import {
  AuthApi
} from "./chunk-2QATIFWL.js";
import {
  AuthService
} from "./chunk-YJRH5W7Z.js";
import {
  MatInput,
  MatInputModule
} from "./chunk-XDHRGA6B.js";
import {
  MatCard,
  MatCardContent,
  MatCardHeader,
  MatCardModule,
  MatCardTitle
} from "./chunk-GPYV2N2Y.js";
import {
  MatButton,
  MatButtonModule,
  MatFormField,
  MatFormFieldModule,
  MatLabel
} from "./chunk-SDENFCBC.js";
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
} from "./chunk-52TXXDGC.js";
import "./chunk-QNELF7JG.js";
import "./chunk-G7PFIUH5.js";
import "./chunk-NITJSJ4E.js";
import {
  Router
} from "./chunk-LV56B2AI.js";
import "./chunk-LGL42SQS.js";
import "./chunk-T4R75CF5.js";
import "./chunk-L2FQVI4C.js";
import {
  Component,
  inject,
  setClassMetadata,
  ɵsetClassDebugInfo,
  ɵɵadvance,
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
  ɵɵtextInterpolate1
} from "./chunk-WGFJ2PWY.js";

// src/app/features/login/login.component.ts
function LoginComponent_Conditional_15_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 7);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r0.errorMessage, " ");
  }
}
function LoginComponent_Conditional_17_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-spinner", 9);
    \u0275\u0275text(1, " \u0412\u0445\u043E\u0434... ");
  }
}
function LoginComponent_Conditional_18_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275text(0, " \u0412\u043E\u0439\u0442\u0438 ");
  }
}
var LoginComponent = class _LoginComponent {
  fb = inject(FormBuilder);
  authService = inject(AuthService);
  authApi = inject(AuthApi);
  router = inject(Router);
  form = this.fb.group({
    login: ["", Validators.required],
    password: ["", Validators.required]
  });
  loading = false;
  errorMessage = "";
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
        if (err.status === 401) {
          this.errorMessage = "\u041D\u0435\u0432\u0435\u0440\u043D\u044B\u0439 \u043B\u043E\u0433\u0438\u043D \u0438\u043B\u0438 \u043F\u0430\u0440\u043E\u043B\u044C. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0434\u0430\u043D\u043D\u044B\u0435 \u0438 \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0441\u043D\u043E\u0432\u0430.";
        } else {
          this.errorMessage = "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043F\u043E\u0434\u043A\u043B\u044E\u0447\u0438\u0442\u044C\u0441\u044F \u043A \u0441\u0435\u0440\u0432\u0435\u0440\u0443. \u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0441\u043E\u0435\u0434\u0438\u043D\u0435\u043D\u0438\u0435.";
        }
      }
    });
  }
  static \u0275fac = function LoginComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _LoginComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _LoginComponent, selectors: [["app-login"]], decls: 19, vars: 4, consts: [[1, "flex", "items-center", "justify-center", "min-h-screen", "bg-[var(--mat-sys-surface)]"], [1, "w-full", "max-w-[400px]", "p-8"], [1, "text-[28px]", "font-normal", "leading-[1.2]", "mb-6"], [1, "flex", "flex-col", "gap-4", 3, "ngSubmit", "formGroup"], ["appearance", "outline", 1, "w-full"], ["matInput", "", "formControlName", "login", "autocomplete", "username"], ["matInput", "", "type", "password", "formControlName", "password", "autocomplete", "current-password"], [1, "text-sm", "text-[var(--mat-sys-error)]", "mt-1"], ["mat-flat-button", "", "color", "primary", "type", "submit", 1, "w-full", "h-12", "mt-2", 3, "disabled"], ["diameter", "20", 1, "inline-block", "mr-2"]], template: function LoginComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "mat-card", 1)(2, "mat-card-header")(3, "mat-card-title", 2);
      \u0275\u0275text(4, " \u0414\u043E\u0431\u0440\u043E \u043F\u043E\u0436\u0430\u043B\u043E\u0432\u0430\u0442\u044C ");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(5, "mat-card-content")(6, "form", 3);
      \u0275\u0275listener("ngSubmit", function LoginComponent_Template_form_ngSubmit_6_listener() {
        return ctx.onSubmit();
      });
      \u0275\u0275elementStart(7, "mat-form-field", 4)(8, "mat-label");
      \u0275\u0275text(9, "\u041B\u043E\u0433\u0438\u043D");
      \u0275\u0275elementEnd();
      \u0275\u0275element(10, "input", 5);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(11, "mat-form-field", 4)(12, "mat-label");
      \u0275\u0275text(13, "\u041F\u0430\u0440\u043E\u043B\u044C");
      \u0275\u0275elementEnd();
      \u0275\u0275element(14, "input", 6);
      \u0275\u0275elementEnd();
      \u0275\u0275template(15, LoginComponent_Conditional_15_Template, 2, 1, "div", 7);
      \u0275\u0275elementStart(16, "button", 8);
      \u0275\u0275template(17, LoginComponent_Conditional_17_Template, 2, 0)(18, LoginComponent_Conditional_18_Template, 1, 0);
      \u0275\u0275elementEnd()()()()();
    }
    if (rf & 2) {
      \u0275\u0275advance(6);
      \u0275\u0275property("formGroup", ctx.form);
      \u0275\u0275advance(9);
      \u0275\u0275conditional(ctx.errorMessage ? 15 : -1);
      \u0275\u0275advance();
      \u0275\u0275property("disabled", ctx.form.invalid || ctx.loading);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.loading ? 17 : 18);
    }
  }, dependencies: [ReactiveFormsModule, \u0275NgNoValidate, DefaultValueAccessor, NgControlStatus, NgControlStatusGroup, FormGroupDirective, FormControlName, MatCardModule, MatCard, MatCardContent, MatCardHeader, MatCardTitle, MatFormFieldModule, MatFormField, MatLabel, MatInputModule, MatInput, MatButtonModule, MatButton, MatProgressSpinnerModule, MatProgressSpinner], encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(LoginComponent, [{
    type: Component,
    args: [{ selector: "app-login", standalone: true, imports: [
      ReactiveFormsModule,
      MatCardModule,
      MatFormFieldModule,
      MatInputModule,
      MatButtonModule,
      MatProgressSpinnerModule
    ], template: '<div class="flex items-center justify-center min-h-screen bg-[var(--mat-sys-surface)]">\n  <mat-card class="w-full max-w-[400px] p-8">\n    <mat-card-header>\n      <mat-card-title class="text-[28px] font-normal leading-[1.2] mb-6">\n        \u0414\u043E\u0431\u0440\u043E \u043F\u043E\u0436\u0430\u043B\u043E\u0432\u0430\u0442\u044C\n      </mat-card-title>\n    </mat-card-header>\n    <mat-card-content>\n      <form [formGroup]="form" (ngSubmit)="onSubmit()" class="flex flex-col gap-4">\n        <mat-form-field appearance="outline" class="w-full">\n          <mat-label>\u041B\u043E\u0433\u0438\u043D</mat-label>\n          <input matInput formControlName="login" autocomplete="username" />\n        </mat-form-field>\n\n        <mat-form-field appearance="outline" class="w-full">\n          <mat-label>\u041F\u0430\u0440\u043E\u043B\u044C</mat-label>\n          <input matInput type="password" formControlName="password" autocomplete="current-password" />\n        </mat-form-field>\n\n        @if (errorMessage) {\n          <div class="text-sm text-[var(--mat-sys-error)] mt-1">\n            {{ errorMessage }}\n          </div>\n        }\n\n        <button mat-flat-button color="primary" type="submit"\n                [disabled]="form.invalid || loading"\n                class="w-full h-12 mt-2">\n          @if (loading) {\n            <mat-spinner diameter="20" class="inline-block mr-2"></mat-spinner>\n            \u0412\u0445\u043E\u0434...\n          } @else {\n            \u0412\u043E\u0439\u0442\u0438\n          }\n        </button>\n      </form>\n    </mat-card-content>\n  </mat-card>\n</div>\n' }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(LoginComponent, { className: "LoginComponent", filePath: "src/app/features/login/login.component.ts", lineNumber: 25 });
})();
export {
  LoginComponent
};
//# sourceMappingURL=chunk-76QG4AUQ.js.map
