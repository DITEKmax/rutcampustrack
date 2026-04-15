import {
  MatDialog,
  MatDialogModule,
  MatDialogRef
} from "./chunk-6NLIVYJ4.js";
import {
  StudentNotificationBadgeService
} from "./chunk-CMIRS6Q2.js";
import "./chunk-OYYF72SB.js";
import {
  animate,
  state,
  style,
  transition,
  trigger
} from "./chunk-JKCQIGK6.js";
import {
  AuthApi
} from "./chunk-E6QM7VLR.js";
import {
  AuthService,
  ProfileService
} from "./chunk-ID5GKTOO.js";
import "./chunk-OIBNGD5S.js";
import "./chunk-G4JX6AWT.js";
import "./chunk-YIJEORIR.js";
import "./chunk-FW2NJ6PM.js";
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
} from "./chunk-HWHV5SCS.js";
import {
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet
} from "./chunk-BRINUWSL.js";
import "./chunk-RWV7HGF7.js";
import "./chunk-FLE4DLW4.js";
import {
  CommonModule
} from "./chunk-CLRYRPPS.js";
import {
  ChangeDetectionStrategy,
  Component,
  Injectable,
  Input,
  __async,
  computed,
  filter,
  inject,
  input,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵclassMap,
  ɵɵclassProp,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵdefineInjectable,
  ɵɵelement,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵgetCurrentView,
  ɵɵlistener,
  ɵɵnamespaceSVG,
  ɵɵnextContext,
  ɵɵproperty,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵstyleProp,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1
} from "./chunk-4M4FDLBS.js";

// src/app/core/theme/theme.service.ts
var ThemeService = class _ThemeService {
  STORAGE_KEY = "ruttrack.theme";
  /** Legacy key from the pre-brandbook build — still read on first load. */
  LEGACY_STORAGE_KEY = "web-panel.theme";
  _theme = signal("dark");
  theme = this._theme.asReadonly();
  isDark = computed(() => this._theme() === "dark");
  userOverrode = false;
  constructor() {
    const stored = this.readStored();
    if (stored) {
      this._theme.set(stored);
      this.userOverrode = true;
    } else {
      this._theme.set(window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark");
    }
    this.applyTheme();
    this.watchSystemPreference();
  }
  toggle() {
    this.setTheme(this._theme() === "dark" ? "light" : "dark");
  }
  setTheme(next) {
    const root = document.documentElement;
    root.classList.add("theme-transitioning");
    window.setTimeout(() => root.classList.remove("theme-transitioning"), 320);
    this.userOverrode = true;
    try {
      localStorage.setItem(this.STORAGE_KEY, next);
    } catch {
    }
    this._theme.set(next);
    this.applyTheme();
  }
  readStored() {
    try {
      const raw = localStorage.getItem(this.STORAGE_KEY);
      if (raw === "dark" || raw === "light")
        return raw;
      const legacy = localStorage.getItem(this.LEGACY_STORAGE_KEY);
      if (legacy === "dark" || legacy === "light") {
        localStorage.setItem(this.STORAGE_KEY, legacy);
        localStorage.removeItem(this.LEGACY_STORAGE_KEY);
        return legacy;
      }
      return null;
    } catch {
      return null;
    }
  }
  watchSystemPreference() {
    if (!window.matchMedia)
      return;
    const mql = window.matchMedia("(prefers-color-scheme: light)");
    mql.addEventListener("change", (e) => {
      if (this.userOverrode)
        return;
      this._theme.set(e.matches ? "light" : "dark");
      this.applyTheme();
    });
  }
  applyTheme() {
    const root = document.documentElement;
    const current = this._theme();
    root.setAttribute("data-theme", current);
    root.classList.toggle("dark", current === "dark");
  }
  static \u0275fac = function ThemeService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _ThemeService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _ThemeService, factory: _ThemeService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(ThemeService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], () => [], null);
})();

// src/app/core/profile/preset-avatars.ts
var AVATAR_PRESETS = [
  { id: "avatar_01", icon: "ph-duotone ph-graduation-cap", gradient: "linear-gradient(135deg,#00E5A0,#3B82F6)", label: "\u0421\u0442\u0443\u0434\u0435\u043D\u0442" },
  { id: "avatar_02", icon: "ph-duotone ph-chalkboard-teacher", gradient: "linear-gradient(135deg,#3B82F6,#8B5CF6)", label: "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C" },
  { id: "avatar_03", icon: "ph-duotone ph-rocket-launch", gradient: "linear-gradient(135deg,#F43F5E,#F97316)", label: "\u0420\u0430\u043A\u0435\u0442\u0430" },
  { id: "avatar_04", icon: "ph-duotone ph-cat", gradient: "linear-gradient(135deg,#F97316,#FACC15)", label: "\u041A\u043E\u0442" },
  { id: "avatar_05", icon: "ph-duotone ph-dog", gradient: "linear-gradient(135deg,#84CC16,#22C55E)", label: "\u041F\u0451\u0441" },
  { id: "avatar_06", icon: "ph-duotone ph-smiley", gradient: "linear-gradient(135deg,#FACC15,#EAB308)", label: "\u0421\u043C\u0430\u0439\u043B" },
  { id: "avatar_07", icon: "ph-duotone ph-ghost", gradient: "linear-gradient(135deg,#6366F1,#EC4899)", label: "\u041F\u0440\u0438\u0437\u0440\u0430\u043A" },
  { id: "avatar_08", icon: "ph-duotone ph-game-controller", gradient: "linear-gradient(135deg,#22D3EE,#06B6D4)", label: "\u0413\u0435\u0439\u043C\u043F\u0430\u0434" },
  { id: "avatar_09", icon: "ph-duotone ph-music-notes", gradient: "linear-gradient(135deg,#A855F7,#D946EF)", label: "\u041C\u0443\u0437\u044B\u043A\u0430" },
  { id: "avatar_10", icon: "ph-duotone ph-basketball", gradient: "linear-gradient(135deg,#F97316,#DC2626)", label: "\u0411\u0430\u0441\u043A\u0435\u0442\u0431\u043E\u043B" },
  { id: "avatar_11", icon: "ph-duotone ph-leaf", gradient: "linear-gradient(135deg,#10B981,#34D399)", label: "\u041B\u0438\u0441\u0442" },
  { id: "avatar_12", icon: "ph-duotone ph-coffee", gradient: "linear-gradient(135deg,#A16207,#78350F)", label: "\u041A\u043E\u0444\u0435" }
];
function findPreset(id) {
  if (!id)
    return void 0;
  return AVATAR_PRESETS.find((p) => p.id === id);
}

// src/app/core/profile/avatar.component.ts
function AvatarComponent_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 2);
    \u0275\u0275element(1, "i", 3);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const p_r1 = ctx;
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275styleProp("background", p_r1.gradient)("width", ctx_r1.size(), "px")("height", ctx_r1.size(), "px");
    \u0275\u0275advance();
    \u0275\u0275classMap(p_r1.icon);
  }
}
function AvatarComponent_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 4);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275styleProp("width", ctx_r1.size(), "px")("height", ctx_r1.size(), "px");
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r1.initials(), " ");
  }
}
var AvatarComponent = class _AvatarComponent {
  avatarId = input(null);
  initials = input("?");
  size = input(40);
  preset = computed(() => findPreset(this.avatarId() ?? null));
  static \u0275fac = function AvatarComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _AvatarComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _AvatarComponent, selectors: [["app-avatar"]], inputs: { avatarId: [1, "avatarId"], initials: [1, "initials"], size: [1, "size"] }, decls: 2, vars: 1, consts: [[1, "avatar", 3, "background", "width", "height"], [1, "avatar", "avatar--initials", 3, "width", "height"], [1, "avatar"], ["aria-hidden", "true"], [1, "avatar", "avatar--initials"]], template: function AvatarComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275template(0, AvatarComponent_Conditional_0_Template, 2, 8, "span", 0)(1, AvatarComponent_Conditional_1_Template, 2, 5, "span", 1);
    }
    if (rf & 2) {
      let tmp_0_0;
      \u0275\u0275conditional((tmp_0_0 = ctx.preset()) ? 0 : 1, tmp_0_0);
    }
  }, styles: ['\n\n[_nghost-%COMP%] {\n  display: inline-block;\n  line-height: 0;\n}\n.avatar[_ngcontent-%COMP%] {\n  display: grid;\n  place-items: center;\n  border-radius: 50%;\n  color: #fff;\n  font-family: var(--font-display, "Inter", sans-serif);\n  font-weight: 700;\n  letter-spacing: -0.01em;\n  box-shadow: 0 0 0 1px color-mix(in oklab, var(--text-primary, #000) 8%, transparent) inset;\n  overflow: hidden;\n  flex-shrink: 0;\n}\n.avatar[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: calc(var(--avatar-size, 40px) * 0.55);\n}\n.avatar--initials[_ngcontent-%COMP%] {\n  background:\n    linear-gradient(\n      135deg,\n      #00E5A0,\n      #3B82F6);\n  font-size: calc(var(--avatar-size, 40px) * 0.40);\n  text-transform: uppercase;\n}\n/*# sourceMappingURL=avatar.component.css.map */'], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(AvatarComponent, [{
    type: Component,
    args: [{ selector: "app-avatar", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, template: `
    @if (preset(); as p) {
      <span class="avatar" [style.background]="p.gradient" [style.width.px]="size()" [style.height.px]="size()">
        <i [class]="p.icon" aria-hidden="true"></i>
      </span>
    } @else {
      <span class="avatar avatar--initials" [style.width.px]="size()" [style.height.px]="size()">
        {{ initials() }}
      </span>
    }
  `, styles: ['/* angular:styles/component:css;68d2eea9d14db15b47b58ac242e9e7be7c3c0ee567bd9ce65772085b1096b42e;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/core/profile/avatar.component.ts */\n:host {\n  display: inline-block;\n  line-height: 0;\n}\n.avatar {\n  display: grid;\n  place-items: center;\n  border-radius: 50%;\n  color: #fff;\n  font-family: var(--font-display, "Inter", sans-serif);\n  font-weight: 700;\n  letter-spacing: -0.01em;\n  box-shadow: 0 0 0 1px color-mix(in oklab, var(--text-primary, #000) 8%, transparent) inset;\n  overflow: hidden;\n  flex-shrink: 0;\n}\n.avatar i {\n  font-size: calc(var(--avatar-size, 40px) * 0.55);\n}\n.avatar--initials {\n  background:\n    linear-gradient(\n      135deg,\n      #00E5A0,\n      #3B82F6);\n  font-size: calc(var(--avatar-size, 40px) * 0.40);\n  text-transform: uppercase;\n}\n/*# sourceMappingURL=avatar.component.css.map */\n'] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(AvatarComponent, { className: "AvatarComponent", filePath: "src/app/core/profile/avatar.component.ts", lineNumber: 45 });
})();

// src/app/core/profile/profile-dialog.component.ts
var _forTrack0 = ($index, $item) => $item.id;
function ProfileDialogComponent_For_21_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 31);
    \u0275\u0275listener("click", function ProfileDialogComponent_For_21_Template_button_click_0_listener() {
      const p_r2 = \u0275\u0275restoreView(_r1).$implicit;
      const ctx_r2 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r2.selectPreset(p_r2.id));
    });
    \u0275\u0275element(1, "i", 32);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const p_r2 = ctx.$implicit;
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275styleProp("background", p_r2.gradient);
    \u0275\u0275classProp("preset-btn--active", ctx_r2.selectedAvatarId() === p_r2.id);
    \u0275\u0275property("title", p_r2.label);
    \u0275\u0275attribute("aria-label", p_r2.label);
    \u0275\u0275advance();
    \u0275\u0275classMap(p_r2.icon);
  }
}
function ProfileDialogComponent_Conditional_22_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 16);
    \u0275\u0275element(1, "i", 33);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r2.avatarError(), " ");
  }
}
function ProfileDialogComponent_Conditional_25_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "span", 34);
    \u0275\u0275text(1, " \u0421\u043E\u0445\u0440\u0430\u043D\u044F\u0435\u043C\u2026 ");
  }
}
function ProfileDialogComponent_Conditional_26_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275text(0, " \u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u0430\u0432\u0430\u0442\u0430\u0440 ");
  }
}
function ProfileDialogComponent_Conditional_27_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 19);
    \u0275\u0275element(1, "i", 35);
    \u0275\u0275text(2, " \u0410\u0432\u0430\u0442\u0430\u0440 \u0441\u043E\u0445\u0440\u0430\u043D\u0451\u043D ");
    \u0275\u0275elementEnd();
  }
}
function ProfileDialogComponent_Conditional_56_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 16);
    \u0275\u0275element(1, "i", 33);
    \u0275\u0275text(2);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275advance(2);
    \u0275\u0275textInterpolate1(" ", ctx_r2.passwordError(), " ");
  }
}
function ProfileDialogComponent_Conditional_57_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 19);
    \u0275\u0275element(1, "i", 35);
    \u0275\u0275text(2, " \u041F\u0430\u0440\u043E\u043B\u044C \u0443\u0441\u043F\u0435\u0448\u043D\u043E \u0438\u0437\u043C\u0435\u043D\u0451\u043D ");
    \u0275\u0275elementEnd();
  }
}
function ProfileDialogComponent_Conditional_59_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "span", 34);
    \u0275\u0275text(1, " \u0421\u043E\u0445\u0440\u0430\u043D\u044F\u0435\u043C\u2026 ");
  }
}
function ProfileDialogComponent_Conditional_60_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275text(0, " \u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C ");
  }
}
var ProfileDialogComponent = class _ProfileDialogComponent {
  profileService = inject(ProfileService);
  dialogRef = inject(MatDialogRef);
  fb = inject(FormBuilder);
  presets = AVATAR_PRESETS;
  profile = this.profileService.profile;
  initials = this.profileService.initials;
  displayName = this.profileService.displayName;
  selectedAvatarId = signal(this.profile()?.avatarId ?? null);
  avatarSaving = signal(false);
  avatarError = signal(null);
  avatarSaved = signal(false);
  showCurrent = signal(false);
  showNew = signal(false);
  passwordSaving = signal(false);
  passwordError = signal(null);
  passwordSaved = signal(false);
  form = this.fb.nonNullable.group({
    currentPassword: ["", [Validators.required]],
    newPassword: [
      "",
      [
        Validators.required,
        Validators.minLength(8),
        Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/)
      ]
    ]
  });
  passwordValue = signal("");
  hintMin = computed(() => this.passwordValue().length >= 8);
  hintUpper = computed(() => /[A-Z]/.test(this.passwordValue()));
  hintDigit = computed(() => /\d/.test(this.passwordValue()));
  constructor() {
    this.profileService.load();
    this.form.controls.newPassword.valueChanges.subscribe((v) => this.passwordValue.set(v ?? ""));
  }
  selectPreset(id) {
    if (this.avatarSaving())
      return;
    this.selectedAvatarId.set(id);
    this.avatarSaved.set(false);
    this.avatarError.set(null);
  }
  saveAvatar() {
    if (this.avatarSaving())
      return;
    this.avatarSaving.set(true);
    this.avatarError.set(null);
    this.profileService.updateAvatar(this.selectedAvatarId()).subscribe({
      next: () => {
        this.avatarSaving.set(false);
        this.avatarSaved.set(true);
      },
      error: () => {
        this.avatarSaving.set(false);
        this.avatarError.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0441\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u0430\u0432\u0430\u0442\u0430\u0440.");
      }
    });
  }
  toggleCurrent() {
    this.showCurrent.update((v) => !v);
  }
  toggleNew() {
    this.showNew.update((v) => !v);
  }
  changePassword() {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.passwordSaving())
      return;
    this.passwordSaving.set(true);
    this.passwordError.set(null);
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.profileService.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.passwordSaving.set(false);
        this.passwordSaved.set(true);
        this.form.reset();
        this.passwordValue.set("");
      },
      error: (err) => {
        this.passwordSaving.set(false);
        this.passwordError.set(err?.status === 400 || err?.status === 401 ? "\u041D\u0435\u0432\u0435\u0440\u043D\u044B\u0439 \u0442\u0435\u043A\u0443\u0449\u0438\u0439 \u043F\u0430\u0440\u043E\u043B\u044C." : "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0441\u043C\u0435\u043D\u0438\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C. \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043F\u043E\u0437\u0436\u0435.");
      }
    });
  }
  close() {
    this.dialogRef.close();
  }
  static \u0275fac = function ProfileDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _ProfileDialogComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _ProfileDialogComponent, selectors: [["app-profile-dialog"]], decls: 61, vars: 47, consts: [[1, "profile-dialog"], [1, "profile-dialog__header"], [1, "profile-dialog__hero"], [3, "avatarId", "initials", "size"], [1, "profile-dialog__hero-meta"], [1, "profile-dialog__name"], [1, "profile-dialog__login"], ["type", "button", "aria-label", "\u0417\u0430\u043A\u0440\u044B\u0442\u044C", 1, "profile-dialog__close", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-x"], [1, "profile-dialog__section"], [1, "profile-dialog__title"], [1, "profile-dialog__hint"], [1, "profile-dialog__grid"], ["type", "button", "aria-label", "\u0418\u043D\u0438\u0446\u0438\u0430\u043B\u044B", 1, "preset-btn", 3, "click"], [1, "preset-btn__initials"], ["type", "button", 1, "preset-btn", 3, "preset-btn--active", "title", "background"], ["role", "alert", 1, "profile-dialog__error"], [1, "profile-dialog__actions"], ["type", "button", 1, "btn-brand", 3, "click", "disabled"], ["role", "status", 1, "profile-dialog__success"], ["novalidate", "", 1, "password-form", 3, "ngSubmit", "formGroup"], [1, "profile-field"], [1, "profile-field__label"], [1, "profile-field__input"], ["formControlName", "currentPassword", "autocomplete", "current-password", 3, "type"], ["type", "button", 1, "profile-field__toggle", 3, "click"], [1, "ph"], ["formControlName", "newPassword", "autocomplete", "new-password", 3, "type"], ["role", "list", 1, "password-hints"], ["aria-hidden", "true", 1, "ph"], ["type", "submit", 1, "btn-brand", 3, "disabled"], ["type", "button", 1, "preset-btn", 3, "click", "title"], ["aria-hidden", "true"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"], ["aria-hidden", "true", 1, "btn-spinner"], ["aria-hidden", "true", 1, "ph-fill", "ph-check-circle"]], template: function ProfileDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "header", 1)(2, "div", 2);
      \u0275\u0275element(3, "app-avatar", 3);
      \u0275\u0275elementStart(4, "div", 4)(5, "h2", 5);
      \u0275\u0275text(6);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(7, "p", 6);
      \u0275\u0275text(8);
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(9, "button", 7);
      \u0275\u0275listener("click", function ProfileDialogComponent_Template_button_click_9_listener() {
        return ctx.close();
      });
      \u0275\u0275element(10, "i", 8);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(11, "section", 9)(12, "h3", 10);
      \u0275\u0275text(13, "\u0410\u0432\u0430\u0442\u0430\u0440");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(14, "p", 11);
      \u0275\u0275text(15, "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u0441\u0435\u0442 \u0438\u043B\u0438 \u043E\u0441\u0442\u0430\u0432\u044C\u0442\u0435 \u0438\u043D\u0438\u0446\u0438\u0430\u043B\u044B \u043F\u043E \u0443\u043C\u043E\u043B\u0447\u0430\u043D\u0438\u044E.");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(16, "div", 12)(17, "button", 13);
      \u0275\u0275listener("click", function ProfileDialogComponent_Template_button_click_17_listener() {
        return ctx.selectPreset(null);
      });
      \u0275\u0275elementStart(18, "span", 14);
      \u0275\u0275text(19);
      \u0275\u0275elementEnd()();
      \u0275\u0275repeaterCreate(20, ProfileDialogComponent_For_21_Template, 2, 8, "button", 15, _forTrack0);
      \u0275\u0275elementEnd();
      \u0275\u0275template(22, ProfileDialogComponent_Conditional_22_Template, 3, 1, "div", 16);
      \u0275\u0275elementStart(23, "div", 17)(24, "button", 18);
      \u0275\u0275listener("click", function ProfileDialogComponent_Template_button_click_24_listener() {
        return ctx.saveAvatar();
      });
      \u0275\u0275template(25, ProfileDialogComponent_Conditional_25_Template, 2, 0)(26, ProfileDialogComponent_Conditional_26_Template, 1, 0);
      \u0275\u0275elementEnd();
      \u0275\u0275template(27, ProfileDialogComponent_Conditional_27_Template, 3, 0, "span", 19);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(28, "section", 9)(29, "h3", 10);
      \u0275\u0275text(30, "\u0421\u043C\u0435\u043D\u0438\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(31, "form", 20);
      \u0275\u0275listener("ngSubmit", function ProfileDialogComponent_Template_form_ngSubmit_31_listener() {
        return ctx.changePassword();
      });
      \u0275\u0275elementStart(32, "label", 21)(33, "span", 22);
      \u0275\u0275text(34, "\u0422\u0435\u043A\u0443\u0449\u0438\u0439 \u043F\u0430\u0440\u043E\u043B\u044C");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(35, "span", 23);
      \u0275\u0275element(36, "input", 24);
      \u0275\u0275elementStart(37, "button", 25);
      \u0275\u0275listener("click", function ProfileDialogComponent_Template_button_click_37_listener() {
        return ctx.toggleCurrent();
      });
      \u0275\u0275element(38, "i", 26);
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(39, "label", 21)(40, "span", 22);
      \u0275\u0275text(41, "\u041D\u043E\u0432\u044B\u0439 \u043F\u0430\u0440\u043E\u043B\u044C");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(42, "span", 23);
      \u0275\u0275element(43, "input", 27);
      \u0275\u0275elementStart(44, "button", 25);
      \u0275\u0275listener("click", function ProfileDialogComponent_Template_button_click_44_listener() {
        return ctx.toggleNew();
      });
      \u0275\u0275element(45, "i", 26);
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(46, "ul", 28)(47, "li");
      \u0275\u0275element(48, "i", 29);
      \u0275\u0275text(49, " \u041C\u0438\u043D\u0438\u043C\u0443\u043C 8 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432 ");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(50, "li");
      \u0275\u0275element(51, "i", 29);
      \u0275\u0275text(52, " \u0425\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u0430 \u0437\u0430\u0433\u043B\u0430\u0432\u043D\u0430\u044F \u0431\u0443\u043A\u0432\u0430 ");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(53, "li");
      \u0275\u0275element(54, "i", 29);
      \u0275\u0275text(55, " \u0425\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u0430 \u0446\u0438\u0444\u0440\u0430 ");
      \u0275\u0275elementEnd()();
      \u0275\u0275template(56, ProfileDialogComponent_Conditional_56_Template, 3, 1, "div", 16)(57, ProfileDialogComponent_Conditional_57_Template, 3, 0, "div", 19);
      \u0275\u0275elementStart(58, "button", 30);
      \u0275\u0275template(59, ProfileDialogComponent_Conditional_59_Template, 2, 0)(60, ProfileDialogComponent_Conditional_60_Template, 1, 0);
      \u0275\u0275elementEnd()()()();
    }
    if (rf & 2) {
      let tmp_4_0;
      let tmp_9_0;
      \u0275\u0275advance(3);
      \u0275\u0275property("avatarId", ctx.selectedAvatarId())("initials", ctx.initials())("size", 72);
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.displayName() || "\u2014");
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate((tmp_4_0 = ctx.profile()) == null ? null : tmp_4_0.login);
      \u0275\u0275advance(9);
      \u0275\u0275classProp("preset-btn--active", ctx.selectedAvatarId() === null);
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate(ctx.initials());
      \u0275\u0275advance();
      \u0275\u0275repeater(ctx.presets);
      \u0275\u0275advance(2);
      \u0275\u0275conditional(ctx.avatarError() ? 22 : -1);
      \u0275\u0275advance(2);
      \u0275\u0275property("disabled", ctx.avatarSaving() || ctx.selectedAvatarId() === ((tmp_9_0 = (tmp_9_0 = ctx.profile()) == null ? null : tmp_9_0.avatarId) !== null && tmp_9_0 !== void 0 ? tmp_9_0 : null));
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.avatarSaving() ? 25 : 26);
      \u0275\u0275advance(2);
      \u0275\u0275conditional(ctx.avatarSaved() ? 27 : -1);
      \u0275\u0275advance(4);
      \u0275\u0275property("formGroup", ctx.form);
      \u0275\u0275advance(5);
      \u0275\u0275property("type", ctx.showCurrent() ? "text" : "password");
      \u0275\u0275advance();
      \u0275\u0275attribute("aria-label", ctx.showCurrent() ? "\u0421\u043A\u0440\u044B\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C" : "\u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C");
      \u0275\u0275advance();
      \u0275\u0275classProp("ph-eye", !ctx.showCurrent())("ph-eye-slash", ctx.showCurrent());
      \u0275\u0275advance(5);
      \u0275\u0275property("type", ctx.showNew() ? "text" : "password");
      \u0275\u0275advance();
      \u0275\u0275attribute("aria-label", ctx.showNew() ? "\u0421\u043A\u0440\u044B\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C" : "\u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C");
      \u0275\u0275advance();
      \u0275\u0275classProp("ph-eye", !ctx.showNew())("ph-eye-slash", ctx.showNew());
      \u0275\u0275advance(2);
      \u0275\u0275classProp("met", ctx.hintMin());
      \u0275\u0275advance();
      \u0275\u0275classProp("ph-check", ctx.hintMin())("ph-circle", !ctx.hintMin());
      \u0275\u0275advance(2);
      \u0275\u0275classProp("met", ctx.hintUpper());
      \u0275\u0275advance();
      \u0275\u0275classProp("ph-check", ctx.hintUpper())("ph-circle", !ctx.hintUpper());
      \u0275\u0275advance(2);
      \u0275\u0275classProp("met", ctx.hintDigit());
      \u0275\u0275advance();
      \u0275\u0275classProp("ph-check", ctx.hintDigit())("ph-circle", !ctx.hintDigit());
      \u0275\u0275advance(2);
      \u0275\u0275conditional(ctx.passwordError() ? 56 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.passwordSaved() ? 57 : -1);
      \u0275\u0275advance();
      \u0275\u0275property("disabled", ctx.form.invalid || ctx.passwordSaving());
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.passwordSaving() ? 59 : 60);
    }
  }, dependencies: [CommonModule, ReactiveFormsModule, \u0275NgNoValidate, DefaultValueAccessor, NgControlStatus, NgControlStatusGroup, FormGroupDirective, FormControlName, MatDialogModule, AvatarComponent], styles: ["\n\n.profile-dialog[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-6);\n  min-width: 360px;\n  max-width: 540px;\n  padding: var(--space-6);\n  background: var(--bg-secondary);\n  color: var(--text-primary);\n}\n.profile-dialog__header[_ngcontent-%COMP%] {\n  display: flex;\n  justify-content: space-between;\n  align-items: flex-start;\n  gap: var(--space-3);\n}\n.profile-dialog__hero[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n}\n.profile-dialog__name[_ngcontent-%COMP%] {\n  margin: 0;\n  font-family: var(--font-display);\n  font-size: 1.125rem;\n  font-weight: 700;\n}\n.profile-dialog__login[_ngcontent-%COMP%] {\n  margin: 2px 0 0;\n  font-family: var(--font-mono);\n  font-size: 0.8125rem;\n  color: var(--text-secondary);\n}\n.profile-dialog__close[_ngcontent-%COMP%] {\n  background: transparent;\n  border: 0;\n  color: var(--text-muted);\n  width: 32px;\n  height: 32px;\n  border-radius: var(--radius-sm);\n  display: grid;\n  place-items: center;\n  cursor: pointer;\n}\n.profile-dialog__close[_ngcontent-%COMP%]:hover {\n  color: var(--text-primary);\n  background: var(--bg-elevated);\n}\n.profile-dialog__close[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 18px;\n}\n.profile-dialog__section[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3);\n}\n.profile-dialog__title[_ngcontent-%COMP%] {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 0.9375rem;\n  margin: 0;\n  color: var(--text-primary);\n}\n.profile-dialog__hint[_ngcontent-%COMP%] {\n  margin: 0;\n  font-size: 0.8125rem;\n  color: var(--text-secondary);\n}\n.profile-dialog__grid[_ngcontent-%COMP%] {\n  display: grid;\n  grid-template-columns: repeat(auto-fill, minmax(54px, 1fr));\n  gap: var(--space-2);\n}\n.preset-btn[_ngcontent-%COMP%] {\n  width: 54px;\n  height: 54px;\n  border-radius: 50%;\n  border: 2px solid transparent;\n  background: var(--bg-surface);\n  display: grid;\n  place-items: center;\n  color: #fff;\n  cursor: pointer;\n  transition:\n    transform var(--duration-fast) var(--ease-out),\n    border-color var(--duration-fast) var(--ease-out),\n    box-shadow var(--duration-fast) var(--ease-out);\n  padding: 0;\n}\n.preset-btn[_ngcontent-%COMP%]:hover {\n  transform: scale(1.06);\n}\n.preset-btn--active[_ngcontent-%COMP%] {\n  border-color: var(--accent-primary);\n  box-shadow: 0 0 0 3px color-mix(in oklab, var(--accent-primary) 30%, transparent);\n}\n.preset-btn[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 26px;\n}\n.preset-btn__initials[_ngcontent-%COMP%] {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 1.125rem;\n  color: var(--text-primary);\n  background:\n    linear-gradient(\n      135deg,\n      #00E5A0,\n      #3B82F6);\n  width: 100%;\n  height: 100%;\n  border-radius: 50%;\n  display: grid;\n  place-items: center;\n  -webkit-background-clip: padding-box;\n  color: #fff;\n}\n.profile-dialog__actions[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  flex-wrap: wrap;\n}\n.password-form[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3);\n}\n.profile-field[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2);\n}\n.profile-field__label[_ngcontent-%COMP%] {\n  font-size: 0.8125rem;\n  font-weight: 600;\n  color: var(--text-secondary);\n}\n.profile-field__input[_ngcontent-%COMP%] {\n  position: relative;\n  display: block;\n}\n.profile-field__input[_ngcontent-%COMP%]   input[_ngcontent-%COMP%] {\n  width: 100%;\n  padding: 10px 44px 10px 14px;\n  background: var(--bg-surface);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-md);\n  color: var(--text-primary);\n  font-family: var(--font-sans);\n  font-size: 0.9375rem;\n  outline: none;\n  transition: border-color var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out);\n  box-sizing: border-box;\n}\n.profile-field__input[_ngcontent-%COMP%]   input[_ngcontent-%COMP%]:focus {\n  border-color: var(--accent-primary);\n  box-shadow: 0 0 0 3px color-mix(in oklab, var(--accent-primary) 18%, transparent);\n}\n.profile-field__toggle[_ngcontent-%COMP%] {\n  position: absolute;\n  top: 50%;\n  right: 6px;\n  transform: translateY(-50%);\n  width: 32px;\n  height: 32px;\n  display: grid;\n  place-items: center;\n  border: 0;\n  background: transparent;\n  color: var(--text-muted);\n  cursor: pointer;\n  border-radius: var(--radius-sm);\n}\n.profile-field__toggle[_ngcontent-%COMP%]:hover {\n  color: var(--text-primary);\n}\n.profile-field__toggle[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 18px;\n}\n.password-hints[_ngcontent-%COMP%] {\n  list-style: none;\n  padding: 0;\n  margin: 0;\n  display: flex;\n  flex-direction: column;\n  gap: 6px;\n  font-size: 0.875rem;\n  color: var(--text-secondary);\n}\n.password-hints[_ngcontent-%COMP%]   li[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: 8px;\n}\n.password-hints[_ngcontent-%COMP%]   li.met[_ngcontent-%COMP%] {\n  color: var(--accent-primary);\n}\n.password-hints[_ngcontent-%COMP%]   li[_ngcontent-%COMP%]   i[_ngcontent-%COMP%] {\n  font-size: 14px;\n}\n.profile-dialog__error[_ngcontent-%COMP%], \n.profile-dialog__success[_ngcontent-%COMP%] {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-2) var(--space-3);\n  border-radius: var(--radius-md);\n  font-size: 0.875rem;\n}\n.profile-dialog__error[_ngcontent-%COMP%] {\n  background: color-mix(in srgb, var(--accent-danger) 12%, transparent);\n  color: var(--accent-danger);\n  border: 1px solid color-mix(in srgb, var(--accent-danger) 28%, transparent);\n}\n.profile-dialog__success[_ngcontent-%COMP%] {\n  background: color-mix(in srgb, var(--accent-primary) 12%, transparent);\n  color: var(--accent-primary);\n  border: 1px solid color-mix(in srgb, var(--accent-primary) 28%, transparent);\n}\n.btn-spinner[_ngcontent-%COMP%] {\n  display: inline-block;\n  width: 14px;\n  height: 14px;\n  border: 2px solid color-mix(in oklab, currentColor 30%, transparent);\n  border-top-color: currentColor;\n  border-radius: 50%;\n  animation: _ngcontent-%COMP%_profile-spin 0.7s linear infinite;\n  margin-right: 6px;\n  vertical-align: -2px;\n}\n@keyframes _ngcontent-%COMP%_profile-spin {\n  to {\n    transform: rotate(360deg);\n  }\n}\n/*# sourceMappingURL=profile-dialog.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(ProfileDialogComponent, [{
    type: Component,
    args: [{ selector: "app-profile-dialog", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule, ReactiveFormsModule, MatDialogModule, AvatarComponent], template: `<div class="profile-dialog">
  <header class="profile-dialog__header">
    <div class="profile-dialog__hero">
      <app-avatar [avatarId]="selectedAvatarId()" [initials]="initials()" [size]="72" />
      <div class="profile-dialog__hero-meta">
        <h2 class="profile-dialog__name">{{ displayName() || '\u2014' }}</h2>
        <p class="profile-dialog__login">{{ profile()?.login }}</p>
      </div>
    </div>
    <button type="button" class="profile-dialog__close" (click)="close()" aria-label="\u0417\u0430\u043A\u0440\u044B\u0442\u044C">
      <i class="ph ph-x" aria-hidden="true"></i>
    </button>
  </header>

  <section class="profile-dialog__section">
    <h3 class="profile-dialog__title">\u0410\u0432\u0430\u0442\u0430\u0440</h3>
    <p class="profile-dialog__hint">\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u043F\u0440\u0435\u0441\u0435\u0442 \u0438\u043B\u0438 \u043E\u0441\u0442\u0430\u0432\u044C\u0442\u0435 \u0438\u043D\u0438\u0446\u0438\u0430\u043B\u044B \u043F\u043E \u0443\u043C\u043E\u043B\u0447\u0430\u043D\u0438\u044E.</p>

    <div class="profile-dialog__grid">
      <button type="button"
              class="preset-btn"
              [class.preset-btn--active]="selectedAvatarId() === null"
              (click)="selectPreset(null)"
              aria-label="\u0418\u043D\u0438\u0446\u0438\u0430\u043B\u044B">
        <span class="preset-btn__initials">{{ initials() }}</span>
      </button>
      @for (p of presets; track p.id) {
        <button type="button"
                class="preset-btn"
                [class.preset-btn--active]="selectedAvatarId() === p.id"
                (click)="selectPreset(p.id)"
                [attr.aria-label]="p.label"
                [title]="p.label"
                [style.background]="p.gradient">
          <i [class]="p.icon" aria-hidden="true"></i>
        </button>
      }
    </div>

    @if (avatarError()) {
      <div class="profile-dialog__error" role="alert">
        <i class="ph ph-warning-circle" aria-hidden="true"></i> {{ avatarError() }}
      </div>
    }

    <div class="profile-dialog__actions">
      <button type="button"
              class="btn-brand"
              [disabled]="avatarSaving() || selectedAvatarId() === (profile()?.avatarId ?? null)"
              (click)="saveAvatar()">
        @if (avatarSaving()) { <span class="btn-spinner" aria-hidden="true"></span> \u0421\u043E\u0445\u0440\u0430\u043D\u044F\u0435\u043C\u2026 }
        @else { \u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u0430\u0432\u0430\u0442\u0430\u0440 }
      </button>
      @if (avatarSaved()) {
        <span class="profile-dialog__success" role="status">
          <i class="ph-fill ph-check-circle" aria-hidden="true"></i> \u0410\u0432\u0430\u0442\u0430\u0440 \u0441\u043E\u0445\u0440\u0430\u043D\u0451\u043D
        </span>
      }
    </div>
  </section>

  <section class="profile-dialog__section">
    <h3 class="profile-dialog__title">\u0421\u043C\u0435\u043D\u0438\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C</h3>

    <form [formGroup]="form" (ngSubmit)="changePassword()" class="password-form" novalidate>
      <label class="profile-field">
        <span class="profile-field__label">\u0422\u0435\u043A\u0443\u0449\u0438\u0439 \u043F\u0430\u0440\u043E\u043B\u044C</span>
        <span class="profile-field__input">
          <input [type]="showCurrent() ? 'text' : 'password'"
                 formControlName="currentPassword"
                 autocomplete="current-password" />
          <button type="button" class="profile-field__toggle" (click)="toggleCurrent()"
                  [attr.aria-label]="showCurrent() ? '\u0421\u043A\u0440\u044B\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C' : '\u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C'">
            <i class="ph" [class.ph-eye]="!showCurrent()" [class.ph-eye-slash]="showCurrent()"></i>
          </button>
        </span>
      </label>

      <label class="profile-field">
        <span class="profile-field__label">\u041D\u043E\u0432\u044B\u0439 \u043F\u0430\u0440\u043E\u043B\u044C</span>
        <span class="profile-field__input">
          <input [type]="showNew() ? 'text' : 'password'"
                 formControlName="newPassword"
                 autocomplete="new-password" />
          <button type="button" class="profile-field__toggle" (click)="toggleNew()"
                  [attr.aria-label]="showNew() ? '\u0421\u043A\u0440\u044B\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C' : '\u041F\u043E\u043A\u0430\u0437\u0430\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C'">
            <i class="ph" [class.ph-eye]="!showNew()" [class.ph-eye-slash]="showNew()"></i>
          </button>
        </span>
      </label>

      <ul class="password-hints" role="list">
        <li [class.met]="hintMin()">
          <i class="ph" [class.ph-check]="hintMin()" [class.ph-circle]="!hintMin()" aria-hidden="true"></i>
          \u041C\u0438\u043D\u0438\u043C\u0443\u043C 8 \u0441\u0438\u043C\u0432\u043E\u043B\u043E\u0432
        </li>
        <li [class.met]="hintUpper()">
          <i class="ph" [class.ph-check]="hintUpper()" [class.ph-circle]="!hintUpper()" aria-hidden="true"></i>
          \u0425\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u0430 \u0437\u0430\u0433\u043B\u0430\u0432\u043D\u0430\u044F \u0431\u0443\u043A\u0432\u0430
        </li>
        <li [class.met]="hintDigit()">
          <i class="ph" [class.ph-check]="hintDigit()" [class.ph-circle]="!hintDigit()" aria-hidden="true"></i>
          \u0425\u043E\u0442\u044F \u0431\u044B \u043E\u0434\u043D\u0430 \u0446\u0438\u0444\u0440\u0430
        </li>
      </ul>

      @if (passwordError()) {
        <div class="profile-dialog__error" role="alert">
          <i class="ph ph-warning-circle" aria-hidden="true"></i> {{ passwordError() }}
        </div>
      }
      @if (passwordSaved()) {
        <div class="profile-dialog__success" role="status">
          <i class="ph-fill ph-check-circle" aria-hidden="true"></i> \u041F\u0430\u0440\u043E\u043B\u044C \u0443\u0441\u043F\u0435\u0448\u043D\u043E \u0438\u0437\u043C\u0435\u043D\u0451\u043D
        </div>
      }

      <button type="submit" class="btn-brand" [disabled]="form.invalid || passwordSaving()">
        @if (passwordSaving()) { <span class="btn-spinner" aria-hidden="true"></span> \u0421\u043E\u0445\u0440\u0430\u043D\u044F\u0435\u043C\u2026 }
        @else { \u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u044C }
      </button>
    </form>
  </section>
</div>
`, styles: ["/* src/app/core/profile/profile-dialog.component.css */\n.profile-dialog {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-6);\n  min-width: 360px;\n  max-width: 540px;\n  padding: var(--space-6);\n  background: var(--bg-secondary);\n  color: var(--text-primary);\n}\n.profile-dialog__header {\n  display: flex;\n  justify-content: space-between;\n  align-items: flex-start;\n  gap: var(--space-3);\n}\n.profile-dialog__hero {\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n}\n.profile-dialog__name {\n  margin: 0;\n  font-family: var(--font-display);\n  font-size: 1.125rem;\n  font-weight: 700;\n}\n.profile-dialog__login {\n  margin: 2px 0 0;\n  font-family: var(--font-mono);\n  font-size: 0.8125rem;\n  color: var(--text-secondary);\n}\n.profile-dialog__close {\n  background: transparent;\n  border: 0;\n  color: var(--text-muted);\n  width: 32px;\n  height: 32px;\n  border-radius: var(--radius-sm);\n  display: grid;\n  place-items: center;\n  cursor: pointer;\n}\n.profile-dialog__close:hover {\n  color: var(--text-primary);\n  background: var(--bg-elevated);\n}\n.profile-dialog__close i {\n  font-size: 18px;\n}\n.profile-dialog__section {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3);\n}\n.profile-dialog__title {\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: 0.9375rem;\n  margin: 0;\n  color: var(--text-primary);\n}\n.profile-dialog__hint {\n  margin: 0;\n  font-size: 0.8125rem;\n  color: var(--text-secondary);\n}\n.profile-dialog__grid {\n  display: grid;\n  grid-template-columns: repeat(auto-fill, minmax(54px, 1fr));\n  gap: var(--space-2);\n}\n.preset-btn {\n  width: 54px;\n  height: 54px;\n  border-radius: 50%;\n  border: 2px solid transparent;\n  background: var(--bg-surface);\n  display: grid;\n  place-items: center;\n  color: #fff;\n  cursor: pointer;\n  transition:\n    transform var(--duration-fast) var(--ease-out),\n    border-color var(--duration-fast) var(--ease-out),\n    box-shadow var(--duration-fast) var(--ease-out);\n  padding: 0;\n}\n.preset-btn:hover {\n  transform: scale(1.06);\n}\n.preset-btn--active {\n  border-color: var(--accent-primary);\n  box-shadow: 0 0 0 3px color-mix(in oklab, var(--accent-primary) 30%, transparent);\n}\n.preset-btn i {\n  font-size: 26px;\n}\n.preset-btn__initials {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 1.125rem;\n  color: var(--text-primary);\n  background:\n    linear-gradient(\n      135deg,\n      #00E5A0,\n      #3B82F6);\n  width: 100%;\n  height: 100%;\n  border-radius: 50%;\n  display: grid;\n  place-items: center;\n  -webkit-background-clip: padding-box;\n  color: #fff;\n}\n.profile-dialog__actions {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  flex-wrap: wrap;\n}\n.password-form {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-3);\n}\n.profile-field {\n  display: flex;\n  flex-direction: column;\n  gap: var(--space-2);\n}\n.profile-field__label {\n  font-size: 0.8125rem;\n  font-weight: 600;\n  color: var(--text-secondary);\n}\n.profile-field__input {\n  position: relative;\n  display: block;\n}\n.profile-field__input input {\n  width: 100%;\n  padding: 10px 44px 10px 14px;\n  background: var(--bg-surface);\n  border: 1px solid var(--border-default);\n  border-radius: var(--radius-md);\n  color: var(--text-primary);\n  font-family: var(--font-sans);\n  font-size: 0.9375rem;\n  outline: none;\n  transition: border-color var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out);\n  box-sizing: border-box;\n}\n.profile-field__input input:focus {\n  border-color: var(--accent-primary);\n  box-shadow: 0 0 0 3px color-mix(in oklab, var(--accent-primary) 18%, transparent);\n}\n.profile-field__toggle {\n  position: absolute;\n  top: 50%;\n  right: 6px;\n  transform: translateY(-50%);\n  width: 32px;\n  height: 32px;\n  display: grid;\n  place-items: center;\n  border: 0;\n  background: transparent;\n  color: var(--text-muted);\n  cursor: pointer;\n  border-radius: var(--radius-sm);\n}\n.profile-field__toggle:hover {\n  color: var(--text-primary);\n}\n.profile-field__toggle i {\n  font-size: 18px;\n}\n.password-hints {\n  list-style: none;\n  padding: 0;\n  margin: 0;\n  display: flex;\n  flex-direction: column;\n  gap: 6px;\n  font-size: 0.875rem;\n  color: var(--text-secondary);\n}\n.password-hints li {\n  display: inline-flex;\n  align-items: center;\n  gap: 8px;\n}\n.password-hints li.met {\n  color: var(--accent-primary);\n}\n.password-hints li i {\n  font-size: 14px;\n}\n.profile-dialog__error,\n.profile-dialog__success {\n  display: inline-flex;\n  align-items: center;\n  gap: var(--space-2);\n  padding: var(--space-2) var(--space-3);\n  border-radius: var(--radius-md);\n  font-size: 0.875rem;\n}\n.profile-dialog__error {\n  background: color-mix(in srgb, var(--accent-danger) 12%, transparent);\n  color: var(--accent-danger);\n  border: 1px solid color-mix(in srgb, var(--accent-danger) 28%, transparent);\n}\n.profile-dialog__success {\n  background: color-mix(in srgb, var(--accent-primary) 12%, transparent);\n  color: var(--accent-primary);\n  border: 1px solid color-mix(in srgb, var(--accent-primary) 28%, transparent);\n}\n.btn-spinner {\n  display: inline-block;\n  width: 14px;\n  height: 14px;\n  border: 2px solid color-mix(in oklab, currentColor 30%, transparent);\n  border-top-color: currentColor;\n  border-radius: 50%;\n  animation: profile-spin 0.7s linear infinite;\n  margin-right: 6px;\n  vertical-align: -2px;\n}\n@keyframes profile-spin {\n  to {\n    transform: rotate(360deg);\n  }\n}\n/*# sourceMappingURL=profile-dialog.component.css.map */\n"] }]
  }], () => [], null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(ProfileDialogComponent, { className: "ProfileDialogComponent", filePath: "src/app/core/profile/profile-dialog.component.ts", lineNumber: 23 });
})();

// src/app/layout/sidebar/sidebar.component.ts
var _forTrack02 = ($index, $item) => $item.route;
function SidebarComponent_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 3);
    \u0275\u0275text(1, "RutTrack");
    \u0275\u0275elementEnd();
  }
}
function SidebarComponent_Conditional_5_For_2_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 10);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const item_r1 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(item_r1.label);
  }
}
function SidebarComponent_Conditional_5_For_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "li")(1, "a", 13);
    \u0275\u0275element(2, "i", 14);
    \u0275\u0275template(3, SidebarComponent_Conditional_5_For_2_Conditional_3_Template, 2, 1, "span", 10);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const item_r1 = ctx.$implicit;
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275property("routerLink", item_r1.route)("title", item_r1.label);
    \u0275\u0275attribute("aria-label", ctx_r1.collapsed() ? item_r1.label : null);
    \u0275\u0275advance();
    \u0275\u0275classMap(item_r1.icon);
    \u0275\u0275advance();
    \u0275\u0275conditional(!ctx_r1.collapsed() ? 3 : -1);
  }
}
function SidebarComponent_Conditional_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "ul", 4);
    \u0275\u0275repeaterCreate(1, SidebarComponent_Conditional_5_For_2_Template, 4, 6, "li", null, _forTrack02);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275repeater(ctx_r1.filteredPrimaryItems());
  }
}
function SidebarComponent_Conditional_6_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 15);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r1.sectionLabel());
  }
}
function SidebarComponent_Conditional_6_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 16);
  }
}
function SidebarComponent_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275template(0, SidebarComponent_Conditional_6_Conditional_0_Template, 2, 1, "p", 15)(1, SidebarComponent_Conditional_6_Conditional_1_Template, 1, 0, "div", 16);
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275conditional(!ctx_r1.collapsed() ? 0 : 1);
  }
}
function SidebarComponent_For_9_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 18);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275attribute("aria-label", "\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F, " + ctx_r1.unreadCount() + " \u043D\u0435\u043F\u0440\u043E\u0447\u0438\u0442\u0430\u043D\u043D\u044B\u0445");
    \u0275\u0275advance();
    \u0275\u0275textInterpolate1(" ", ctx_r1.unreadCount() > 9 ? "9+" : ctx_r1.unreadCount(), " ");
  }
}
function SidebarComponent_For_9_Conditional_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 10);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const item_r3 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(item_r3.label);
  }
}
function SidebarComponent_For_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "li")(1, "a", 13)(2, "span", 17);
    \u0275\u0275element(3, "i", 14);
    \u0275\u0275template(4, SidebarComponent_For_9_Conditional_4_Template, 2, 2, "span", 18);
    \u0275\u0275elementEnd();
    \u0275\u0275template(5, SidebarComponent_For_9_Conditional_5_Template, 2, 1, "span", 10);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const item_r3 = ctx.$implicit;
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275property("routerLink", item_r3.route)("title", item_r3.label);
    \u0275\u0275advance(2);
    \u0275\u0275classMap(item_r3.icon);
    \u0275\u0275advance();
    \u0275\u0275conditional(item_r3.icon === "ph-bell" && ctx_r1.unreadCount() > 0 ? 4 : -1);
    \u0275\u0275advance();
    \u0275\u0275conditional(!ctx_r1.collapsed() ? 5 : -1);
  }
}
function SidebarComponent_Conditional_10_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "p", 15);
    \u0275\u0275text(1, "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442");
    \u0275\u0275elementEnd();
  }
}
function SidebarComponent_Conditional_10_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "div", 16);
  }
}
function SidebarComponent_Conditional_10_For_4_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 10);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const item_r4 = \u0275\u0275nextContext().$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(item_r4.label);
  }
}
function SidebarComponent_Conditional_10_For_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "li")(1, "a", 13);
    \u0275\u0275element(2, "i", 14);
    \u0275\u0275template(3, SidebarComponent_Conditional_10_For_4_Conditional_3_Template, 2, 1, "span", 10);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const item_r4 = ctx.$implicit;
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275property("routerLink", item_r4.route)("title", item_r4.label);
    \u0275\u0275advance();
    \u0275\u0275classMap(item_r4.icon);
    \u0275\u0275advance();
    \u0275\u0275conditional(!ctx_r1.collapsed() ? 3 : -1);
  }
}
function SidebarComponent_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275template(0, SidebarComponent_Conditional_10_Conditional_0_Template, 2, 0, "p", 15)(1, SidebarComponent_Conditional_10_Conditional_1_Template, 1, 0, "div", 16);
    \u0275\u0275elementStart(2, "ul", 5);
    \u0275\u0275repeaterCreate(3, SidebarComponent_Conditional_10_For_4_Template, 4, 5, "li", null, _forTrack02);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275conditional(!ctx_r1.collapsed() ? 0 : 1);
    \u0275\u0275advance(3);
    \u0275\u0275repeater(ctx_r1.filteredHeadmanNavItems());
  }
}
function SidebarComponent_Conditional_13_Conditional_0_Case_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275text(0, " \u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440 ");
  }
}
function SidebarComponent_Conditional_13_Conditional_0_Case_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275text(0, " \u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C ");
  }
}
function SidebarComponent_Conditional_13_Conditional_0_Case_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275text(0);
  }
  if (rf & 2) {
    const user_r6 = \u0275\u0275nextContext(2);
    \u0275\u0275textInterpolate1(" ", user_r6.isHeadman ? "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" : "\u0421\u0442\u0443\u0434\u0435\u043D\u0442", " ");
  }
}
function SidebarComponent_Conditional_13_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    const _r5 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 21);
    \u0275\u0275listener("click", function SidebarComponent_Conditional_13_Conditional_0_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r5);
      const ctx_r1 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r1.openProfile());
    });
    \u0275\u0275element(1, "app-avatar", 22);
    \u0275\u0275elementStart(2, "div", 23)(3, "span", 24);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "span", 25);
    \u0275\u0275template(6, SidebarComponent_Conditional_13_Conditional_0_Case_6_Template, 1, 0)(7, SidebarComponent_Conditional_13_Conditional_0_Case_7_Template, 1, 0)(8, SidebarComponent_Conditional_13_Conditional_0_Case_8_Template, 1, 1);
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    let tmp_3_0;
    let tmp_6_0;
    let tmp_7_0;
    const user_r6 = \u0275\u0275nextContext();
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275property("avatarId", (tmp_3_0 = ctx_r1.profile()) == null ? null : tmp_3_0.avatarId)("initials", ctx_r1.profileInitials() || "?")("size", 36);
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate(ctx_r1.profileDisplayName() || ((tmp_6_0 = ctx_r1.profile()) == null ? null : tmp_6_0.login) || "\u2014");
    \u0275\u0275advance(2);
    \u0275\u0275conditional((tmp_7_0 = user_r6.role) === "ADMIN" ? 6 : tmp_7_0 === "TEACHER" ? 7 : tmp_7_0 === "STUDENT" ? 8 : -1);
  }
}
function SidebarComponent_Conditional_13_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    const _r7 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 26);
    \u0275\u0275listener("click", function SidebarComponent_Conditional_13_Conditional_1_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r7);
      const ctx_r1 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r1.openProfile());
    });
    \u0275\u0275element(1, "app-avatar", 22);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    let tmp_4_0;
    const ctx_r1 = \u0275\u0275nextContext(2);
    \u0275\u0275property("title", ctx_r1.profileDisplayName() || "\u041F\u0440\u043E\u0444\u0438\u043B\u044C");
    \u0275\u0275advance();
    \u0275\u0275property("avatarId", (tmp_4_0 = ctx_r1.profile()) == null ? null : tmp_4_0.avatarId)("initials", ctx_r1.profileInitials() || "?")("size", 36);
  }
}
function SidebarComponent_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275template(0, SidebarComponent_Conditional_13_Conditional_0_Template, 9, 5, "button", 19)(1, SidebarComponent_Conditional_13_Conditional_1_Template, 2, 4, "button", 20);
  }
  if (rf & 2) {
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275conditional(!ctx_r1.collapsed() ? 0 : 1);
  }
}
function SidebarComponent_Conditional_16_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 10);
    \u0275\u0275text(1, "\u0412\u044B\u0439\u0442\u0438");
    \u0275\u0275elementEnd();
  }
}
function SidebarComponent_Conditional_19_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 10);
    \u0275\u0275text(1, "\u0421\u0432\u0435\u0440\u043D\u0443\u0442\u044C");
    \u0275\u0275elementEnd();
  }
}
var SidebarComponent = class _SidebarComponent {
  authService = inject(AuthService);
  authApi = inject(AuthApi);
  router = inject(Router);
  themeService = inject(ThemeService);
  SIDEBAR_KEY = "web-panel.sidebar.collapsed";
  collapsed = signal(false);
  currentUser = this.authService.currentUser;
  notificationBadge = inject(StudentNotificationBadgeService);
  unreadCount = this.notificationBadge.unreadCount;
  profileService = inject(ProfileService);
  dialog = inject(MatDialog);
  profile = this.profileService.profile;
  profileInitials = this.profileService.initials;
  profileDisplayName = this.profileService.displayName;
  /** Primary nav (dashboards) — always shown first when role matches. */
  primaryItems = [
    {
      label: "\u0414\u0430\u0448\u0431\u043E\u0440\u0434",
      icon: "ph-squares-four",
      route: "/teacher/dashboard",
      roles: ["TEACHER"]
    },
    {
      label: "\u0414\u0430\u0448\u0431\u043E\u0440\u0434",
      icon: "ph-squares-four",
      route: "/admin/dashboard",
      roles: ["ADMIN"]
    },
    {
      label: "\u0413\u043B\u0430\u0432\u043D\u0430\u044F",
      icon: "ph-squares-four",
      route: "/student/dashboard",
      roles: ["STUDENT"]
    }
  ];
  /** Secondary nav — work pages under each section. */
  allNavItems = [
    // Teacher items
    {
      label: "\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438",
      icon: "ph-book-open",
      route: "/teacher/journal",
      roles: ["TEACHER"]
    },
    {
      label: "\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430",
      icon: "ph-chart-bar",
      route: "/teacher/stats",
      roles: ["TEACHER"]
    },
    // Admin items
    {
      label: "\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u0438",
      icon: "ph-users",
      route: "/admin/users",
      roles: ["ADMIN"]
    },
    {
      label: "\u0413\u0440\u0443\u043F\u043F\u044B",
      icon: "ph-users-three",
      route: "/admin/groups",
      roles: ["ADMIN"]
    },
    {
      label: "\u0421\u0435\u043C\u0435\u0441\u0442\u0440\u044B",
      icon: "ph-calendar",
      route: "/admin/semesters",
      roles: ["ADMIN"]
    },
    // Student items
    {
      label: "\u0420\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435",
      icon: "ph-calendar-dots",
      route: "/student/schedule",
      roles: ["STUDENT"]
    },
    {
      label: "\u041E\u0442\u043C\u0435\u0442\u0438\u0442\u044C\u0441\u044F",
      icon: "ph-map-pin",
      route: "/student/checkin",
      roles: ["STUDENT"]
    },
    {
      label: "\u0414\u043E\u043C\u0430\u0448\u043D\u0438\u0435 \u0437\u0430\u0434\u0430\u043D\u0438\u044F",
      icon: "ph-notebook",
      route: "/student/homework",
      roles: ["STUDENT"]
    },
    {
      label: "\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430",
      icon: "ph-chart-bar",
      route: "/student/stats",
      roles: ["STUDENT"]
    },
    {
      label: "\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F",
      icon: "ph-bell",
      route: "/student/notifications",
      roles: ["STUDENT"]
    },
    {
      label: "\u041F\u0440\u043E\u0444\u0438\u043B\u044C",
      icon: "ph-user-circle",
      route: "/student/profile",
      roles: ["STUDENT"]
    },
    {
      label: "\u041F\u0440\u043E\u043F\u0443\u0441\u043A\u0438",
      icon: "ph-file-text",
      route: "/student/excuses",
      roles: ["STUDENT"]
    },
    {
      label: "\u0417\u0430\u043F\u0440\u043E\u0441 \u043E\u0442\u043C\u0435\u0442\u043A\u0438",
      icon: "ph-clock-countdown",
      route: "/student/late-checkin",
      roles: ["STUDENT"]
    },
    // Headman items (Старостат section) — shown only when isHeadman === true
    {
      label: "\u041A\u0430\u0431\u0438\u043D\u0435\u0442 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B",
      icon: "ph-crown-simple",
      route: "/headman/dashboard",
      roles: ["STUDENT"],
      isHeadman: true
    },
    {
      label: "\u0413\u0440\u0443\u043F\u043F\u0430",
      icon: "ph-users-three",
      route: "/headman/group",
      roles: ["STUDENT"],
      isHeadman: true
    },
    {
      label: "\u041F\u0440\u0435\u0434\u043C\u0435\u0442\u044B",
      icon: "ph-books",
      route: "/headman/subjects",
      roles: ["STUDENT"],
      isHeadman: true
    },
    {
      label: "\u0420\u0430\u0441\u043F\u0438\u0441\u0430\u043D\u0438\u0435",
      icon: "ph-calendar-blank",
      route: "/headman/schedule",
      roles: ["STUDENT"],
      isHeadman: true
    },
    {
      label: "\u041F\u0430\u0440\u044B \u043D\u0430 2 \u043D\u0435\u0434\u0435\u043B\u0438",
      icon: "ph-calendar-x",
      route: "/headman/lessons",
      roles: ["STUDENT"],
      isHeadman: true
    },
    {
      label: "\u0416\u0443\u0440\u043D\u0430\u043B",
      icon: "ph-table",
      route: "/headman/journal",
      roles: ["STUDENT"],
      isHeadman: true
    },
    {
      label: "\u0416\u0443\u0440\u043D\u0430\u043B \u043F\u043E\u0441\u0435\u0449\u0430\u0435\u043C\u043E\u0441\u0442\u0438",
      icon: "ph-clipboard-text",
      route: "/headman/weekly-journal",
      roles: ["STUDENT"],
      isHeadman: true
    },
    {
      label: "\u041F\u0440\u043E\u043F\u0443\u0441\u043A\u0438",
      icon: "ph-file-text",
      route: "/headman/excuses",
      roles: ["STUDENT"],
      isHeadman: true
    },
    {
      label: "\u0417\u0430\u043F\u0440\u043E\u0441\u044B \u043E\u0442\u043C\u0435\u0442\u043A\u0438",
      icon: "ph-clock-countdown",
      route: "/headman/late-checkin",
      roles: ["STUDENT"],
      isHeadman: true
    },
    {
      label: "\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043A\u0430",
      icon: "ph-chart-bar",
      route: "/headman/stats",
      roles: ["STUDENT"],
      isHeadman: true
    }
  ];
  filteredPrimaryItems = computed(() => {
    const user = this.currentUser();
    if (!user)
      return [];
    return this.primaryItems.filter((item) => item.roles.includes(user.role));
  });
  /** Non-headman secondary nav items — work pages for the user's role. */
  filteredNavItems = computed(() => {
    const user = this.currentUser();
    if (!user)
      return [];
    return this.allNavItems.filter((item) => item.roles.includes(user.role)).filter((item) => !item.isHeadman);
  });
  /** Headman-only nav items (Старостат section) — shown only when isHeadman === true. */
  filteredHeadmanNavItems = computed(() => {
    const user = this.currentUser();
    if (!user || !user.isHeadman)
      return [];
    return this.allNavItems.filter((item) => item.roles.includes(user.role) && item.isHeadman === true);
  });
  sectionLabel = computed(() => {
    const user = this.currentUser();
    if (!user)
      return "";
    if (user.role === "ADMIN")
      return "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0438\u0440\u043E\u0432\u0430\u043D\u0438\u0435";
    if (user.role === "STUDENT")
      return "\u0423\u0447\u0451\u0431\u0430";
    return "\u0420\u0430\u0431\u043E\u0442\u0430";
  });
  ngOnInit() {
    const stored = localStorage.getItem(this.SIDEBAR_KEY);
    if (stored === "true")
      this.collapsed.set(true);
    if (typeof window !== "undefined" && window.innerWidth < 1024) {
      this.collapsed.set(true);
    }
    if (this.authService.isAuthenticated())
      this.profileService.load();
  }
  openProfile() {
    this.profileService.load();
    this.dialog.open(ProfileDialogComponent, {
      width: "560px",
      panelClass: "profile-dialog-panel",
      autoFocus: false
    });
  }
  toggleCollapse() {
    this.collapsed.update((v) => !v);
    localStorage.setItem(this.SIDEBAR_KEY, String(this.collapsed()));
  }
  logout() {
    return __async(this, null, function* () {
      yield this.authService.logout(this.authApi, this.router);
    });
  }
  toggleTheme() {
    this.themeService.toggle();
  }
  static \u0275fac = function SidebarComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _SidebarComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _SidebarComponent, selectors: [["app-sidebar"]], decls: 20, vars: 14, consts: [["aria-label", "\u041E\u0441\u043D\u043E\u0432\u043D\u0430\u044F \u043D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F", 1, "sidebar"], ["routerLink", "/", "aria-label", "RutCampusTrack \u2014 \u0433\u043B\u0430\u0432\u043D\u0430\u044F", 1, "sidebar__brand"], ["aria-hidden", "true", 1, "sidebar__brand-mark"], [1, "sidebar__brand-name"], [1, "sidebar__nav", "sidebar__nav--primary"], [1, "sidebar__nav"], [1, "sidebar__spacer"], [1, "sidebar__footer"], ["type", "button", "title", "\u0412\u044B\u0439\u0442\u0438", 1, "sidebar__footer-btn", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-sign-out", "sidebar__icon"], [1, "sidebar__label"], ["type", "button", 1, "sidebar__footer-btn", "sidebar__collapse-btn", 3, "click", "title"], ["aria-hidden", "true", 1, "ph", "ph-caret-left", "sidebar__icon"], ["routerLinkActive", "sidebar__link--active", 1, "sidebar__link", 3, "routerLink", "title"], ["aria-hidden", "true", 1, "ph", "sidebar__icon"], [1, "sidebar__section"], ["aria-hidden", "true", 1, "sidebar__section-divider"], [1, "sidebar__icon-wrap", 2, "position", "relative", "display", "inline-flex"], [1, "notification-badge"], ["type", "button", "aria-label", "\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043F\u0440\u043E\u0444\u0438\u043B\u044C", 1, "sidebar__user", "sidebar__user--button"], ["type", "button", "aria-label", "\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043F\u0440\u043E\u0444\u0438\u043B\u044C", 1, "sidebar__user", "sidebar__user--collapsed", "sidebar__user--button", 3, "title"], ["type", "button", "aria-label", "\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043F\u0440\u043E\u0444\u0438\u043B\u044C", 1, "sidebar__user", "sidebar__user--button", 3, "click"], [3, "avatarId", "initials", "size"], [1, "sidebar__user-meta"], [1, "sidebar__user-name"], [1, "sidebar__user-role"], ["type", "button", "aria-label", "\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043F\u0440\u043E\u0444\u0438\u043B\u044C", 1, "sidebar__user", "sidebar__user--collapsed", "sidebar__user--button", 3, "click", "title"]], template: function SidebarComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "nav", 0)(1, "a", 1)(2, "span", 2);
      \u0275\u0275text(3, "R");
      \u0275\u0275elementEnd();
      \u0275\u0275template(4, SidebarComponent_Conditional_4_Template, 2, 0, "span", 3);
      \u0275\u0275elementEnd();
      \u0275\u0275template(5, SidebarComponent_Conditional_5_Template, 3, 0, "ul", 4)(6, SidebarComponent_Conditional_6_Template, 2, 1);
      \u0275\u0275elementStart(7, "ul", 5);
      \u0275\u0275repeaterCreate(8, SidebarComponent_For_9_Template, 6, 6, "li", null, _forTrack02);
      \u0275\u0275elementEnd();
      \u0275\u0275template(10, SidebarComponent_Conditional_10_Template, 5, 1);
      \u0275\u0275element(11, "div", 6);
      \u0275\u0275elementStart(12, "div", 7);
      \u0275\u0275template(13, SidebarComponent_Conditional_13_Template, 2, 1);
      \u0275\u0275elementStart(14, "button", 8);
      \u0275\u0275listener("click", function SidebarComponent_Template_button_click_14_listener() {
        return ctx.logout();
      });
      \u0275\u0275element(15, "i", 9);
      \u0275\u0275template(16, SidebarComponent_Conditional_16_Template, 2, 0, "span", 10);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(17, "button", 11);
      \u0275\u0275listener("click", function SidebarComponent_Template_button_click_17_listener() {
        return ctx.toggleCollapse();
      });
      \u0275\u0275element(18, "i", 12);
      \u0275\u0275template(19, SidebarComponent_Conditional_19_Template, 2, 0, "span", 10);
      \u0275\u0275elementEnd()()();
    }
    if (rf & 2) {
      let tmp_7_0;
      \u0275\u0275classProp("sidebar--collapsed", ctx.collapsed());
      \u0275\u0275property("@collapse", ctx.collapsed() ? "collapsed" : "expanded");
      \u0275\u0275advance(4);
      \u0275\u0275conditional(!ctx.collapsed() ? 4 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.filteredPrimaryItems().length > 0 ? 5 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.filteredNavItems().length > 0 ? 6 : -1);
      \u0275\u0275advance(2);
      \u0275\u0275repeater(ctx.filteredNavItems());
      \u0275\u0275advance(2);
      \u0275\u0275conditional(ctx.filteredHeadmanNavItems().length > 0 ? 10 : -1);
      \u0275\u0275advance(3);
      \u0275\u0275conditional((tmp_7_0 = ctx.currentUser()) ? 13 : -1, tmp_7_0);
      \u0275\u0275advance();
      \u0275\u0275attribute("aria-label", ctx.collapsed() ? "\u0412\u044B\u0439\u0442\u0438" : null);
      \u0275\u0275advance(2);
      \u0275\u0275conditional(!ctx.collapsed() ? 16 : -1);
      \u0275\u0275advance();
      \u0275\u0275property("title", ctx.collapsed() ? "\u0420\u0430\u0437\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E" : "\u0421\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E");
      \u0275\u0275attribute("aria-label", ctx.collapsed() ? "\u0420\u0430\u0437\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E" : "\u0421\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E");
      \u0275\u0275advance();
      \u0275\u0275property("@rotateChevron", ctx.collapsed() ? "collapsed" : "expanded");
      \u0275\u0275advance();
      \u0275\u0275conditional(!ctx.collapsed() ? 19 : -1);
    }
  }, dependencies: [RouterLink, RouterLinkActive, MatDialogModule, AvatarComponent], styles: ['\n\n[_nghost-%COMP%] {\n  display: block;\n  flex-shrink: 0;\n  height: 100vh;\n  position: sticky;\n  top: 0;\n}\n.sidebar[_ngcontent-%COMP%] {\n  height: 100%;\n  display: flex;\n  flex-direction: column;\n  background: var(--bg-primary);\n  border-right: 1px solid var(--border-subtle);\n  overflow-y: auto;\n  overflow-x: hidden;\n  scrollbar-width: thin;\n  scrollbar-color: color-mix(in oklab, var(--text-muted) 35%, transparent) transparent;\n  position: relative;\n  background-image:\n    linear-gradient(var(--border-subtle) 1px, transparent 1px),\n    linear-gradient(\n      90deg,\n      var(--border-subtle) 1px,\n      transparent 1px);\n  background-size: 64px 64px;\n  transition: background-color var(--duration-slow) var(--ease-out), border-color var(--duration-slow) var(--ease-out);\n}\n.sidebar[_ngcontent-%COMP%]::-webkit-scrollbar {\n  width: 6px;\n}\n.sidebar[_ngcontent-%COMP%]::-webkit-scrollbar-track {\n  background: transparent;\n}\n.sidebar[_ngcontent-%COMP%]::-webkit-scrollbar-thumb {\n  background: color-mix(in oklab, var(--text-muted) 35%, transparent);\n  border-radius: 3px;\n}\n.sidebar[_ngcontent-%COMP%]::-webkit-scrollbar-thumb:hover {\n  background: color-mix(in oklab, var(--text-muted) 60%, transparent);\n}\n.sidebar__brand[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 0 var(--space-5);\n  height: var(--header-height, 64px);\n  flex-shrink: 0;\n  border-bottom: 1px solid var(--border-subtle);\n  text-decoration: none;\n  color: var(--text-primary);\n}\n.sidebar--collapsed[_ngcontent-%COMP%]   .sidebar__brand[_ngcontent-%COMP%] {\n  padding: 0;\n  justify-content: center;\n}\n.sidebar__brand-mark[_ngcontent-%COMP%] {\n  width: 38px;\n  height: 38px;\n  border-radius: 10px;\n  background: var(--gradient-brand);\n  color: var(--accent-primary-contrast);\n  display: grid;\n  place-items: center;\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 1rem;\n  flex-shrink: 0;\n  box-shadow: var(--glow-primary);\n  position: relative;\n}\n.sidebar__brand-mark[_ngcontent-%COMP%]::after {\n  content: "";\n  position: absolute;\n  top: 6px;\n  right: 6px;\n  width: 6px;\n  height: 6px;\n  border-radius: 50%;\n  background: #fff;\n  box-shadow: 0 0 8px rgba(255, 255, 255, 0.8);\n}\n.sidebar__brand-name[_ngcontent-%COMP%] {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 1.125rem;\n  color: var(--text-primary);\n  letter-spacing: -0.01em;\n  white-space: nowrap;\n}\n.sidebar__nav[_ngcontent-%COMP%] {\n  list-style: none;\n  margin: 0;\n  padding: var(--space-3);\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n}\n.sidebar__nav--primary[_ngcontent-%COMP%] {\n  padding-bottom: var(--space-2);\n}\n.sidebar__section[_ngcontent-%COMP%] {\n  margin: var(--space-4) var(--space-5) var(--space-2);\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  font-weight: 500;\n  color: var(--text-muted);\n  letter-spacing: 0.05em;\n  text-transform: uppercase;\n}\n.sidebar__section-divider[_ngcontent-%COMP%] {\n  height: 1px;\n  margin: var(--space-4) var(--space-3);\n  background: var(--border-subtle);\n}\n.sidebar__link[_ngcontent-%COMP%] {\n  position: relative;\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 10px 12px 10px 13px;\n  border-radius: var(--radius-md);\n  font-size: 0.875rem;\n  color: var(--text-secondary);\n  text-decoration: none;\n  transition: background-color var(--duration-base) var(--ease-out), color var(--duration-base) var(--ease-out);\n}\n.sidebar--collapsed[_ngcontent-%COMP%]   .sidebar__link[_ngcontent-%COMP%] {\n  justify-content: center;\n  padding: 10px 0;\n}\n.sidebar__link[_ngcontent-%COMP%]:hover {\n  background: color-mix(in oklab, var(--text-primary) 5%, transparent);\n  color: var(--text-primary);\n}\n.sidebar__link--active[_ngcontent-%COMP%] {\n  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);\n  color: var(--accent-primary);\n  font-weight: 500;\n}\n.sidebar__link--active[_ngcontent-%COMP%]::before {\n  content: "";\n  position: absolute;\n  left: 0;\n  top: 8px;\n  bottom: 8px;\n  width: 3px;\n  border-radius: 0 3px 3px 0;\n  background: var(--accent-primary);\n  box-shadow: var(--glow-primary);\n}\n.sidebar--collapsed[_ngcontent-%COMP%]   .sidebar__link--active[_ngcontent-%COMP%]::before {\n  left: 6px;\n  top: 10px;\n  bottom: 10px;\n}\n.sidebar__icon[_ngcontent-%COMP%] {\n  font-size: 22px;\n  flex-shrink: 0;\n  line-height: 1;\n}\n.sidebar__label[_ngcontent-%COMP%] {\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.sidebar__spacer[_ngcontent-%COMP%] {\n  flex: 1;\n  min-height: var(--space-4);\n}\n.sidebar__footer[_ngcontent-%COMP%] {\n  flex-shrink: 0;\n  padding: var(--space-3);\n  border-top: 1px solid var(--border-subtle);\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n}\n.sidebar__user[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: var(--space-3);\n  margin-bottom: var(--space-2);\n  border-radius: var(--radius-md);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  transition: border-color var(--duration-base) var(--ease-out), background-color var(--duration-base) var(--ease-out);\n}\n.sidebar__user--collapsed[_ngcontent-%COMP%] {\n  justify-content: center;\n  padding: var(--space-2);\n}\n.sidebar__user--button[_ngcontent-%COMP%] {\n  cursor: pointer;\n  font: inherit;\n  color: inherit;\n  text-align: left;\n  width: 100%;\n}\n.sidebar__user--button[_ngcontent-%COMP%]:hover {\n  border-color: var(--border-accent);\n  background: var(--bg-elevated);\n}\n.sidebar__user--button[_ngcontent-%COMP%]:focus-visible {\n  outline: none;\n  box-shadow: 0 0 0 2px color-mix(in oklab, var(--accent-primary) 45%, transparent);\n}\n.sidebar__user-avatar[_ngcontent-%COMP%] {\n  width: 34px;\n  height: 34px;\n  border-radius: 50%;\n  background: var(--gradient-brand);\n  color: var(--accent-primary-contrast);\n  display: grid;\n  place-items: center;\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 0.75rem;\n  flex-shrink: 0;\n  box-shadow: var(--glow-primary);\n}\n.sidebar__user-meta[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  min-width: 0;\n  line-height: 1.2;\n}\n.sidebar__user-id[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  color: var(--text-muted);\n}\n.sidebar__user-name[_ngcontent-%COMP%] {\n  font-family: var(--font-heading);\n  font-size: 0.875rem;\n  font-weight: 600;\n  color: var(--text-primary);\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  max-width: 160px;\n}\n.sidebar__user-role[_ngcontent-%COMP%] {\n  font-family: var(--font-heading);\n  font-size: 0.8125rem;\n  font-weight: 500;\n  color: var(--text-primary);\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.sidebar__footer-btn[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 10px 12px;\n  border-radius: var(--radius-md);\n  background: transparent;\n  border: 0;\n  color: var(--text-secondary);\n  font-size: 0.875rem;\n  font-family: inherit;\n  cursor: pointer;\n  text-align: left;\n  transition: background-color var(--duration-base) var(--ease-out), color var(--duration-base) var(--ease-out);\n}\n.sidebar--collapsed[_ngcontent-%COMP%]   .sidebar__footer-btn[_ngcontent-%COMP%] {\n  justify-content: center;\n  padding: 10px 0;\n}\n.sidebar__footer-btn[_ngcontent-%COMP%]:hover {\n  background: color-mix(in oklab, var(--text-primary) 5%, transparent);\n  color: var(--text-primary);\n}\n.sidebar__collapse-btn[_ngcontent-%COMP%]:hover   .ph-caret-left[_ngcontent-%COMP%] {\n  color: var(--accent-primary);\n}\n.notification-badge[_ngcontent-%COMP%] {\n  position: absolute;\n  top: -4px;\n  right: -4px;\n  min-width: 16px;\n  height: 16px;\n  background: var(--accent-primary);\n  color: var(--accent-primary-contrast, #0A0E17);\n  border-radius: var(--radius-full, 9999px);\n  font-size: 10px;\n  font-family: var(--font-mono);\n  font-weight: 600;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  padding: 0 3px;\n  pointer-events: none;\n}\n/*# sourceMappingURL=sidebar.component.css.map */'], data: { animation: [
    trigger("collapse", [
      state("expanded", style({ width: "260px" })),
      state("collapsed", style({ width: "72px" })),
      transition("expanded <=> collapsed", animate("240ms cubic-bezier(0.16, 1, 0.3, 1)"))
    ]),
    trigger("rotateChevron", [
      state("expanded", style({ transform: "rotate(0deg)" })),
      state("collapsed", style({ transform: "rotate(180deg)" })),
      transition("expanded <=> collapsed", animate("200ms cubic-bezier(0.16, 1, 0.3, 1)"))
    ])
  ] } });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(SidebarComponent, [{
    type: Component,
    args: [{ selector: "app-sidebar", standalone: true, imports: [RouterLink, RouterLinkActive, MatDialogModule, AvatarComponent], animations: [
      trigger("collapse", [
        state("expanded", style({ width: "260px" })),
        state("collapsed", style({ width: "72px" })),
        transition("expanded <=> collapsed", animate("240ms cubic-bezier(0.16, 1, 0.3, 1)"))
      ]),
      trigger("rotateChevron", [
        state("expanded", style({ transform: "rotate(0deg)" })),
        state("collapsed", style({ transform: "rotate(180deg)" })),
        transition("expanded <=> collapsed", animate("200ms cubic-bezier(0.16, 1, 0.3, 1)"))
      ])
    ], template: `<nav\r
  [@collapse]="collapsed() ? 'collapsed' : 'expanded'"\r
  class="sidebar"\r
  [class.sidebar--collapsed]="collapsed()"\r
  aria-label="\u041E\u0441\u043D\u043E\u0432\u043D\u0430\u044F \u043D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044F"\r
>\r
  <!-- Brand -->\r
  <a routerLink="/" class="sidebar__brand" aria-label="RutCampusTrack \u2014 \u0433\u043B\u0430\u0432\u043D\u0430\u044F">\r
    <span class="sidebar__brand-mark" aria-hidden="true">R</span>\r
    @if (!collapsed()) {\r
      <span class="sidebar__brand-name">RutTrack</span>\r
    }\r
  </a>\r
\r
  <!-- Primary nav (dashboard) -->\r
  @if (filteredPrimaryItems().length > 0) {\r
    <ul class="sidebar__nav sidebar__nav--primary">\r
      @for (item of filteredPrimaryItems(); track item.route) {\r
        <li>\r
          <a\r
            [routerLink]="item.route"\r
            routerLinkActive="sidebar__link--active"\r
            class="sidebar__link"\r
            [title]="item.label"\r
            [attr.aria-label]="collapsed() ? item.label : null"\r
          >\r
            <i [class]="item.icon" class="ph sidebar__icon" aria-hidden="true"></i>\r
            @if (!collapsed()) {\r
              <span class="sidebar__label">{{ item.label }}</span>\r
            }\r
          </a>\r
        </li>\r
      }\r
    </ul>\r
  }\r
\r
  <!-- Section header -->\r
  @if (filteredNavItems().length > 0) {\r
    @if (!collapsed()) {\r
      <p class="sidebar__section">{{ sectionLabel() }}</p>\r
    } @else {\r
      <div class="sidebar__section-divider" aria-hidden="true"></div>\r
    }\r
  }\r
\r
  <!-- Secondary nav -->\r
  <ul class="sidebar__nav">\r
    @for (item of filteredNavItems(); track item.route) {\r
      <li>\r
        <a\r
          [routerLink]="item.route"\r
          routerLinkActive="sidebar__link--active"\r
          class="sidebar__link"\r
          [title]="item.label"\r
        >\r
          <span class="sidebar__icon-wrap" style="position: relative; display: inline-flex;">\r
            <i [class]="item.icon" class="ph sidebar__icon" aria-hidden="true"></i>\r
            @if (item.icon === 'ph-bell' && unreadCount() > 0) {\r
              <span class="notification-badge" [attr.aria-label]="'\u0423\u0432\u0435\u0434\u043E\u043C\u043B\u0435\u043D\u0438\u044F, ' + unreadCount() + ' \u043D\u0435\u043F\u0440\u043E\u0447\u0438\u0442\u0430\u043D\u043D\u044B\u0445'">\r
                {{ unreadCount() > 9 ? '9+' : unreadCount() }}\r
              </span>\r
            }\r
          </span>\r
          @if (!collapsed()) {\r
            <span class="sidebar__label">{{ item.label }}</span>\r
          }\r
        </a>\r
      </li>\r
    }\r
  </ul>\r
\r
  <!-- Headman section (\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442) \u2014 only visible when isHeadman === true -->\r
  @if (filteredHeadmanNavItems().length > 0) {\r
    @if (!collapsed()) {\r
      <p class="sidebar__section">\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430\u0442</p>\r
    } @else {\r
      <div class="sidebar__section-divider" aria-hidden="true"></div>\r
    }\r
    <ul class="sidebar__nav">\r
      @for (item of filteredHeadmanNavItems(); track item.route) {\r
        <li>\r
          <a\r
            [routerLink]="item.route"\r
            routerLinkActive="sidebar__link--active"\r
            class="sidebar__link"\r
            [title]="item.label"\r
          >\r
            <i [class]="item.icon" class="ph sidebar__icon" aria-hidden="true"></i>\r
            @if (!collapsed()) {\r
              <span class="sidebar__label">{{ item.label }}</span>\r
            }\r
          </a>\r
        </li>\r
      }\r
    </ul>\r
  }\r
\r
  <!-- Spacer -->\r
  <div class="sidebar__spacer"></div>\r
\r
  <!-- Footer: user block + logout + collapse -->\r
  <div class="sidebar__footer">\r
    @if (currentUser(); as user) {\r
      @if (!collapsed()) {\r
        <button type="button" class="sidebar__user sidebar__user--button"\r
                (click)="openProfile()" aria-label="\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043F\u0440\u043E\u0444\u0438\u043B\u044C">\r
          <app-avatar [avatarId]="profile()?.avatarId" [initials]="profileInitials() || '?'" [size]="36" />\r
          <div class="sidebar__user-meta">\r
            <span class="sidebar__user-name">{{ profileDisplayName() || profile()?.login || '\u2014' }}</span>\r
            <span class="sidebar__user-role">\r
              @switch (user.role) {\r
                @case ('ADMIN') { \u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440 }\r
                @case ('TEACHER') { \u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C }\r
                @case ('STUDENT') { {{ user.isHeadman ? '\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430' : '\u0421\u0442\u0443\u0434\u0435\u043D\u0442' }} }\r
              }\r
            </span>\r
          </div>\r
        </button>\r
      } @else {\r
        <button type="button" class="sidebar__user sidebar__user--collapsed sidebar__user--button"\r
                (click)="openProfile()"\r
                [title]="profileDisplayName() || '\u041F\u0440\u043E\u0444\u0438\u043B\u044C'"\r
                aria-label="\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043F\u0440\u043E\u0444\u0438\u043B\u044C">\r
          <app-avatar [avatarId]="profile()?.avatarId" [initials]="profileInitials() || '?'" [size]="36" />\r
        </button>\r
      }\r
    }\r
\r
    <button\r
      type="button"\r
      (click)="logout()"\r
      class="sidebar__footer-btn"\r
      title="\u0412\u044B\u0439\u0442\u0438"\r
      [attr.aria-label]="collapsed() ? '\u0412\u044B\u0439\u0442\u0438' : null"\r
    >\r
      <i class="ph ph-sign-out sidebar__icon" aria-hidden="true"></i>\r
      @if (!collapsed()) {\r
        <span class="sidebar__label">\u0412\u044B\u0439\u0442\u0438</span>\r
      }\r
    </button>\r
\r
    <button\r
      type="button"\r
      (click)="toggleCollapse()"\r
      class="sidebar__footer-btn sidebar__collapse-btn"\r
      [attr.aria-label]="collapsed() ? '\u0420\u0430\u0437\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E' : '\u0421\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E'"\r
      [title]="collapsed() ? '\u0420\u0430\u0437\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E' : '\u0421\u0432\u0435\u0440\u043D\u0443\u0442\u044C \u043C\u0435\u043D\u044E'"\r
    >\r
      <i\r
        [@rotateChevron]="collapsed() ? 'collapsed' : 'expanded'"\r
        class="ph ph-caret-left sidebar__icon"\r
        aria-hidden="true"\r
      ></i>\r
      @if (!collapsed()) {\r
        <span class="sidebar__label">\u0421\u0432\u0435\u0440\u043D\u0443\u0442\u044C</span>\r
      }\r
    </button>\r
  </div>\r
</nav>\r
`, styles: ['/* src/app/layout/sidebar/sidebar.component.css */\n:host {\n  display: block;\n  flex-shrink: 0;\n  height: 100vh;\n  position: sticky;\n  top: 0;\n}\n.sidebar {\n  height: 100%;\n  display: flex;\n  flex-direction: column;\n  background: var(--bg-primary);\n  border-right: 1px solid var(--border-subtle);\n  overflow-y: auto;\n  overflow-x: hidden;\n  scrollbar-width: thin;\n  scrollbar-color: color-mix(in oklab, var(--text-muted) 35%, transparent) transparent;\n  position: relative;\n  background-image:\n    linear-gradient(var(--border-subtle) 1px, transparent 1px),\n    linear-gradient(\n      90deg,\n      var(--border-subtle) 1px,\n      transparent 1px);\n  background-size: 64px 64px;\n  transition: background-color var(--duration-slow) var(--ease-out), border-color var(--duration-slow) var(--ease-out);\n}\n.sidebar::-webkit-scrollbar {\n  width: 6px;\n}\n.sidebar::-webkit-scrollbar-track {\n  background: transparent;\n}\n.sidebar::-webkit-scrollbar-thumb {\n  background: color-mix(in oklab, var(--text-muted) 35%, transparent);\n  border-radius: 3px;\n}\n.sidebar::-webkit-scrollbar-thumb:hover {\n  background: color-mix(in oklab, var(--text-muted) 60%, transparent);\n}\n.sidebar__brand {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 0 var(--space-5);\n  height: var(--header-height, 64px);\n  flex-shrink: 0;\n  border-bottom: 1px solid var(--border-subtle);\n  text-decoration: none;\n  color: var(--text-primary);\n}\n.sidebar--collapsed .sidebar__brand {\n  padding: 0;\n  justify-content: center;\n}\n.sidebar__brand-mark {\n  width: 38px;\n  height: 38px;\n  border-radius: 10px;\n  background: var(--gradient-brand);\n  color: var(--accent-primary-contrast);\n  display: grid;\n  place-items: center;\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 1rem;\n  flex-shrink: 0;\n  box-shadow: var(--glow-primary);\n  position: relative;\n}\n.sidebar__brand-mark::after {\n  content: "";\n  position: absolute;\n  top: 6px;\n  right: 6px;\n  width: 6px;\n  height: 6px;\n  border-radius: 50%;\n  background: #fff;\n  box-shadow: 0 0 8px rgba(255, 255, 255, 0.8);\n}\n.sidebar__brand-name {\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 1.125rem;\n  color: var(--text-primary);\n  letter-spacing: -0.01em;\n  white-space: nowrap;\n}\n.sidebar__nav {\n  list-style: none;\n  margin: 0;\n  padding: var(--space-3);\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n}\n.sidebar__nav--primary {\n  padding-bottom: var(--space-2);\n}\n.sidebar__section {\n  margin: var(--space-4) var(--space-5) var(--space-2);\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  font-weight: 500;\n  color: var(--text-muted);\n  letter-spacing: 0.05em;\n  text-transform: uppercase;\n}\n.sidebar__section-divider {\n  height: 1px;\n  margin: var(--space-4) var(--space-3);\n  background: var(--border-subtle);\n}\n.sidebar__link {\n  position: relative;\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 10px 12px 10px 13px;\n  border-radius: var(--radius-md);\n  font-size: 0.875rem;\n  color: var(--text-secondary);\n  text-decoration: none;\n  transition: background-color var(--duration-base) var(--ease-out), color var(--duration-base) var(--ease-out);\n}\n.sidebar--collapsed .sidebar__link {\n  justify-content: center;\n  padding: 10px 0;\n}\n.sidebar__link:hover {\n  background: color-mix(in oklab, var(--text-primary) 5%, transparent);\n  color: var(--text-primary);\n}\n.sidebar__link--active {\n  background: color-mix(in oklab, var(--accent-primary) 12%, transparent);\n  color: var(--accent-primary);\n  font-weight: 500;\n}\n.sidebar__link--active::before {\n  content: "";\n  position: absolute;\n  left: 0;\n  top: 8px;\n  bottom: 8px;\n  width: 3px;\n  border-radius: 0 3px 3px 0;\n  background: var(--accent-primary);\n  box-shadow: var(--glow-primary);\n}\n.sidebar--collapsed .sidebar__link--active::before {\n  left: 6px;\n  top: 10px;\n  bottom: 10px;\n}\n.sidebar__icon {\n  font-size: 22px;\n  flex-shrink: 0;\n  line-height: 1;\n}\n.sidebar__label {\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.sidebar__spacer {\n  flex: 1;\n  min-height: var(--space-4);\n}\n.sidebar__footer {\n  flex-shrink: 0;\n  padding: var(--space-3);\n  border-top: 1px solid var(--border-subtle);\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n}\n.sidebar__user {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: var(--space-3);\n  margin-bottom: var(--space-2);\n  border-radius: var(--radius-md);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  transition: border-color var(--duration-base) var(--ease-out), background-color var(--duration-base) var(--ease-out);\n}\n.sidebar__user--collapsed {\n  justify-content: center;\n  padding: var(--space-2);\n}\n.sidebar__user--button {\n  cursor: pointer;\n  font: inherit;\n  color: inherit;\n  text-align: left;\n  width: 100%;\n}\n.sidebar__user--button:hover {\n  border-color: var(--border-accent);\n  background: var(--bg-elevated);\n}\n.sidebar__user--button:focus-visible {\n  outline: none;\n  box-shadow: 0 0 0 2px color-mix(in oklab, var(--accent-primary) 45%, transparent);\n}\n.sidebar__user-avatar {\n  width: 34px;\n  height: 34px;\n  border-radius: 50%;\n  background: var(--gradient-brand);\n  color: var(--accent-primary-contrast);\n  display: grid;\n  place-items: center;\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 0.75rem;\n  flex-shrink: 0;\n  box-shadow: var(--glow-primary);\n}\n.sidebar__user-meta {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  min-width: 0;\n  line-height: 1.2;\n}\n.sidebar__user-id {\n  font-family: var(--font-mono);\n  font-size: 0.6875rem;\n  color: var(--text-muted);\n}\n.sidebar__user-name {\n  font-family: var(--font-heading);\n  font-size: 0.875rem;\n  font-weight: 600;\n  color: var(--text-primary);\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  max-width: 160px;\n}\n.sidebar__user-role {\n  font-family: var(--font-heading);\n  font-size: 0.8125rem;\n  font-weight: 500;\n  color: var(--text-primary);\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.sidebar__footer-btn {\n  display: flex;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 10px 12px;\n  border-radius: var(--radius-md);\n  background: transparent;\n  border: 0;\n  color: var(--text-secondary);\n  font-size: 0.875rem;\n  font-family: inherit;\n  cursor: pointer;\n  text-align: left;\n  transition: background-color var(--duration-base) var(--ease-out), color var(--duration-base) var(--ease-out);\n}\n.sidebar--collapsed .sidebar__footer-btn {\n  justify-content: center;\n  padding: 10px 0;\n}\n.sidebar__footer-btn:hover {\n  background: color-mix(in oklab, var(--text-primary) 5%, transparent);\n  color: var(--text-primary);\n}\n.sidebar__collapse-btn:hover .ph-caret-left {\n  color: var(--accent-primary);\n}\n.notification-badge {\n  position: absolute;\n  top: -4px;\n  right: -4px;\n  min-width: 16px;\n  height: 16px;\n  background: var(--accent-primary);\n  color: var(--accent-primary-contrast, #0A0E17);\n  border-radius: var(--radius-full, 9999px);\n  font-size: 10px;\n  font-family: var(--font-mono);\n  font-weight: 600;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  padding: 0 3px;\n  pointer-events: none;\n}\n/*# sourceMappingURL=sidebar.component.css.map */\n'] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(SidebarComponent, { className: "SidebarComponent", filePath: "src/app/layout/sidebar/sidebar.component.ts", lineNumber: 55 });
})();

// src/app/core/theme/theme-toggle.component.ts
function ThemeToggleComponent_Conditional_1_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275namespaceSVG();
    \u0275\u0275elementStart(0, "svg", 1);
    \u0275\u0275element(1, "path", 2);
    \u0275\u0275elementEnd();
  }
}
function ThemeToggleComponent_Conditional_2_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275namespaceSVG();
    \u0275\u0275elementStart(0, "svg", 1);
    \u0275\u0275element(1, "circle", 3)(2, "path", 4)(3, "path", 5)(4, "path", 6)(5, "path", 7)(6, "path", 8)(7, "path", 9)(8, "path", 10)(9, "path", 11);
    \u0275\u0275elementEnd();
  }
}
var ThemeToggleComponent = class _ThemeToggleComponent {
  themeService = inject(ThemeService);
  compact = false;
  static \u0275fac = function ThemeToggleComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _ThemeToggleComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _ThemeToggleComponent, selectors: [["app-theme-toggle"]], inputs: { compact: "compact" }, decls: 3, vars: 6, consts: [["type", "button", "role", "switch", 1, "theme-toggle", 3, "click", "title"], ["viewBox", "0 0 24 24", "fill", "none", "stroke", "currentColor", "stroke-width", "1.8", "stroke-linecap", "round", "stroke-linejoin", "round", "aria-hidden", "true"], ["d", "M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"], ["cx", "12", "cy", "12", "r", "4"], ["d", "M12 2v2"], ["d", "M12 20v2"], ["d", "m4.93 4.93 1.41 1.41"], ["d", "m17.66 17.66 1.41 1.41"], ["d", "M2 12h2"], ["d", "M20 12h2"], ["d", "m6.34 17.66-1.41 1.41"], ["d", "m19.07 4.93-1.41 1.41"]], template: function ThemeToggleComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "button", 0);
      \u0275\u0275listener("click", function ThemeToggleComponent_Template_button_click_0_listener() {
        return ctx.themeService.toggle();
      });
      \u0275\u0275template(1, ThemeToggleComponent_Conditional_1_Template, 2, 0, ":svg:svg", 1)(2, ThemeToggleComponent_Conditional_2_Template, 10, 0, ":svg:svg", 1);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275classProp("compact", ctx.compact);
      \u0275\u0275property("title", ctx.themeService.isDark() ? "\u0421\u0432\u0435\u0442\u043B\u0430\u044F \u0442\u0435\u043C\u0430" : "\u0422\u0451\u043C\u043D\u0430\u044F \u0442\u0435\u043C\u0430");
      \u0275\u0275attribute("aria-checked", ctx.themeService.isDark())("aria-label", ctx.themeService.isDark() ? "\u0412\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u0441\u0432\u0435\u0442\u043B\u0443\u044E \u0442\u0435\u043C\u0443" : "\u0412\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u0442\u0451\u043C\u043D\u0443\u044E \u0442\u0435\u043C\u0443");
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.themeService.isDark() ? 1 : 2);
    }
  }, styles: ["\n\n.theme-toggle[_ngcontent-%COMP%] {\n  width: 40px;\n  height: 40px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  border-radius: var(--radius-full);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  color: var(--text-primary);\n  cursor: pointer;\n  padding: 0;\n  transition:\n    background-color var(--duration-base) var(--ease-out),\n    border-color var(--duration-base) var(--ease-out),\n    transform var(--duration-fast) var(--ease-out),\n    box-shadow var(--duration-base) var(--ease-out);\n}\n.theme-toggle.compact[_ngcontent-%COMP%] {\n  width: 32px;\n  height: 32px;\n}\n.theme-toggle[_ngcontent-%COMP%]   svg[_ngcontent-%COMP%] {\n  width: 20px;\n  height: 20px;\n}\n.theme-toggle.compact[_ngcontent-%COMP%]   svg[_ngcontent-%COMP%] {\n  width: 16px;\n  height: 16px;\n}\n.theme-toggle[_ngcontent-%COMP%]:hover {\n  border-color: var(--border-accent);\n  box-shadow: var(--glow-primary);\n}\n.theme-toggle[_ngcontent-%COMP%]:active {\n  transform: scale(0.94);\n}\n.theme-toggle[_ngcontent-%COMP%]:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n/*# sourceMappingURL=theme-toggle.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(ThemeToggleComponent, [{
    type: Component,
    args: [{ selector: "app-theme-toggle", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, template: `
    <button
      type="button"
      role="switch"
      [attr.aria-checked]="themeService.isDark()"
      [attr.aria-label]="themeService.isDark() ? '\u0412\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u0441\u0432\u0435\u0442\u043B\u0443\u044E \u0442\u0435\u043C\u0443' : '\u0412\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u0442\u0451\u043C\u043D\u0443\u044E \u0442\u0435\u043C\u0443'"
      [title]="themeService.isDark() ? '\u0421\u0432\u0435\u0442\u043B\u0430\u044F \u0442\u0435\u043C\u0430' : '\u0422\u0451\u043C\u043D\u0430\u044F \u0442\u0435\u043C\u0430'"
      [class.compact]="compact"
      class="theme-toggle"
      (click)="themeService.toggle()"
    >
      @if (themeService.isDark()) {
        <!-- Moon -->
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
             stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
        </svg>
      } @else {
        <!-- Sun -->
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
             stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2" />
          <path d="M12 20v2" />
          <path d="m4.93 4.93 1.41 1.41" />
          <path d="m17.66 17.66 1.41 1.41" />
          <path d="M2 12h2" />
          <path d="M20 12h2" />
          <path d="m6.34 17.66-1.41 1.41" />
          <path d="m19.07 4.93-1.41 1.41" />
        </svg>
      }
    </button>
  `, styles: ["/* angular:styles/component:css;b34f7a7eadec0b2a1ae6a317841e571b642dc75cc96a951a3bff13ebba479c5b;C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/src/app/core/theme/theme-toggle.component.ts */\n.theme-toggle {\n  width: 40px;\n  height: 40px;\n  display: inline-flex;\n  align-items: center;\n  justify-content: center;\n  border-radius: var(--radius-full);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  color: var(--text-primary);\n  cursor: pointer;\n  padding: 0;\n  transition:\n    background-color var(--duration-base) var(--ease-out),\n    border-color var(--duration-base) var(--ease-out),\n    transform var(--duration-fast) var(--ease-out),\n    box-shadow var(--duration-base) var(--ease-out);\n}\n.theme-toggle.compact {\n  width: 32px;\n  height: 32px;\n}\n.theme-toggle svg {\n  width: 20px;\n  height: 20px;\n}\n.theme-toggle.compact svg {\n  width: 16px;\n  height: 16px;\n}\n.theme-toggle:hover {\n  border-color: var(--border-accent);\n  box-shadow: var(--glow-primary);\n}\n.theme-toggle:active {\n  transform: scale(0.94);\n}\n.theme-toggle:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n/*# sourceMappingURL=theme-toggle.component.css.map */\n"] }]
  }], null, { compact: [{
    type: Input
  }] });
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(ThemeToggleComponent, { className: "ThemeToggleComponent", filePath: "src/app/core/theme/theme-toggle.component.ts", lineNumber: 95 });
})();

// src/app/layout/header/header.component.ts
function HeaderComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 6);
    \u0275\u0275listener("click", function HeaderComponent_Conditional_8_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r1);
      const ctx_r1 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r1.openProfile());
    });
    \u0275\u0275element(1, "app-avatar", 7);
    \u0275\u0275elementStart(2, "div", 8)(3, "span", 9);
    \u0275\u0275text(4);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(5, "span");
    \u0275\u0275text(6);
    \u0275\u0275elementEnd()()();
  }
  if (rf & 2) {
    let tmp_1_0;
    const ctx_r1 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275property("avatarId", (tmp_1_0 = ctx_r1.profile()) == null ? null : tmp_1_0.avatarId)("initials", ctx_r1.userInitial())("size", 36);
    \u0275\u0275advance(3);
    \u0275\u0275textInterpolate(ctx_r1.profileDisplayName() || ctx_r1.roleLabel());
    \u0275\u0275advance();
    \u0275\u0275classMap(ctx_r1.roleChipClass());
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r1.roleLabel());
  }
}
var HeaderComponent = class _HeaderComponent {
  router = inject(Router);
  auth = inject(AuthService);
  profileService = inject(ProfileService);
  dialog = inject(MatDialog);
  currentUser = this.auth.currentUser;
  profile = this.profileService.profile;
  profileInitials = this.profileService.initials;
  profileDisplayName = this.profileService.displayName;
  constructor() {
    if (this.auth.isAuthenticated())
      this.profileService.load();
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.updateTitleFromRoute());
    this.updateTitleFromRoute();
  }
  openProfile() {
    this.profileService.load();
    this.dialog.open(ProfileDialogComponent, {
      width: "560px",
      panelClass: "profile-dialog-panel",
      autoFocus: false
    });
  }
  /** Page title + eyebrow derived from the currently activated route's `data`. */
  routeState = signal({
    title: "\u041F\u0430\u043D\u0435\u043B\u044C \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u044F",
    eyebrow: "RutCampusTrack"
  });
  pageTitle = computed(() => this.routeState().title);
  pageEyebrow = computed(() => this.routeState().eyebrow);
  roleLabel = computed(() => {
    const user = this.currentUser();
    if (!user)
      return "";
    if (user.role === "ADMIN")
      return "\u0410\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440";
    if (user.role === "TEACHER")
      return "\u041F\u0440\u0435\u043F\u043E\u0434\u0430\u0432\u0430\u0442\u0435\u043B\u044C";
    return user.isHeadman ? "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430" : "\u0421\u0442\u0443\u0434\u0435\u043D\u0442";
  });
  roleChipClass = computed(() => {
    const user = this.currentUser();
    if (!user)
      return "role-chip";
    const suffix = user.role === "ADMIN" ? "admin" : user.role === "TEACHER" ? "teacher" : "student";
    return `role-chip role-chip--${suffix}`;
  });
  userInitial = computed(() => {
    const fromProfile = this.profileInitials();
    if (fromProfile)
      return fromProfile;
    const user = this.currentUser();
    return user ? user.role[0] : "?";
  });
  updateTitleFromRoute() {
    let route = this.router.routerState.snapshot.root;
    while (route.firstChild)
      route = route.firstChild;
    const data = route.data;
    this.routeState.set({
      title: data.title ?? "\u041F\u0430\u043D\u0435\u043B\u044C \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u044F",
      eyebrow: data.eyebrow ?? "RutCampusTrack"
    });
  }
  static \u0275fac = function HeaderComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _HeaderComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _HeaderComponent, selectors: [["app-header"]], decls: 9, vars: 3, consts: [[1, "shell-header"], [1, "shell-header__title"], [1, "shell-header__eyebrow"], [1, "shell-header__heading"], [1, "shell-header__actions"], ["type", "button", "aria-label", "\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043F\u0440\u043E\u0444\u0438\u043B\u044C", 1, "user-chip", "user-chip--button"], ["type", "button", "aria-label", "\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043F\u0440\u043E\u0444\u0438\u043B\u044C", 1, "user-chip", "user-chip--button", 3, "click"], [3, "avatarId", "initials", "size"], [1, "user-chip__meta"], [1, "user-chip__name"]], template: function HeaderComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "header", 0)(1, "div", 1)(2, "span", 2);
      \u0275\u0275text(3);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(4, "p", 3);
      \u0275\u0275text(5);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(6, "div", 4);
      \u0275\u0275element(7, "app-theme-toggle");
      \u0275\u0275template(8, HeaderComponent_Conditional_8_Template, 7, 7, "button", 5);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275advance(3);
      \u0275\u0275textInterpolate(ctx.pageEyebrow());
      \u0275\u0275advance(2);
      \u0275\u0275textInterpolate(ctx.pageTitle());
      \u0275\u0275advance(3);
      \u0275\u0275conditional(ctx.currentUser() ? 8 : -1);
    }
  }, dependencies: [ThemeToggleComponent, MatDialogModule, AvatarComponent], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n  position: sticky;\n  top: 0;\n  z-index: var(--z-sticky, 200);\n}\n.shell-header[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: var(--space-5);\n  height: var(--header-height, 64px);\n  padding: 0 var(--space-6);\n  background: color-mix(in oklab, var(--bg-primary) 82%, transparent);\n  backdrop-filter: blur(14px) saturate(140%);\n  -webkit-backdrop-filter: blur(14px) saturate(140%);\n  border-bottom: 1px solid var(--border-subtle);\n  transition: background-color var(--duration-slow) var(--ease-out), border-color var(--duration-slow) var(--ease-out);\n}\n.shell-header__title[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  min-width: 0;\n}\n.shell-header__eyebrow[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: var(--text-xs);\n  font-weight: 500;\n  color: var(--text-muted);\n  letter-spacing: var(--tracking-wide);\n  text-transform: uppercase;\n}\n.shell-header__heading[_ngcontent-%COMP%] {\n  margin: 0;\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: var(--text-xl);\n  color: var(--text-primary);\n  line-height: 1.2;\n  letter-spacing: -0.01em;\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.shell-header__actions[_ngcontent-%COMP%] {\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n  flex-shrink: 0;\n}\n.user-chip[_ngcontent-%COMP%] {\n  display: none;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 6px 14px 6px 6px;\n  border-radius: var(--radius-full);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  transition:\n    border-color var(--duration-base) var(--ease-out),\n    background-color var(--duration-slow) var(--ease-out),\n    transform var(--duration-fast) var(--ease-out);\n}\n.user-chip--button[_ngcontent-%COMP%] {\n  cursor: pointer;\n  font: inherit;\n  color: inherit;\n  text-align: left;\n}\n.user-chip[_ngcontent-%COMP%]:hover {\n  border-color: var(--border-accent);\n}\n.user-chip--button[_ngcontent-%COMP%]:hover {\n  transform: translateY(-1px);\n}\n.user-chip--button[_ngcontent-%COMP%]:focus-visible {\n  outline: none;\n  box-shadow: 0 0 0 3px color-mix(in oklab, var(--accent-primary) 35%, transparent);\n}\n@media (min-width: 768px) {\n  .user-chip[_ngcontent-%COMP%] {\n    display: inline-flex;\n  }\n}\n.user-chip__avatar[_ngcontent-%COMP%] {\n  width: 32px;\n  height: 32px;\n  border-radius: 50%;\n  display: grid;\n  place-items: center;\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 0.75rem;\n  color: var(--accent-primary-contrast);\n  background: var(--gradient-brand);\n  box-shadow: var(--glow-primary);\n}\n.user-chip[_ngcontent-%COMP%]   app-avatar[_ngcontent-%COMP%] {\n  line-height: 0;\n}\n.user-chip__meta[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  line-height: 1;\n}\n.user-chip__name[_ngcontent-%COMP%] {\n  font-family: var(--font-mono);\n  font-size: var(--text-xs);\n  color: var(--text-secondary);\n  letter-spacing: 0.02em;\n}\n.user-chip[_ngcontent-%COMP%]   .role-chip[_ngcontent-%COMP%] {\n  padding: 1px 0;\n  background: transparent !important;\n}\n@media (max-width: 640px) {\n  .shell-header[_ngcontent-%COMP%] {\n    padding: 0 var(--space-4);\n    gap: var(--space-3);\n  }\n  .shell-header__heading[_ngcontent-%COMP%] {\n    font-size: var(--text-lg);\n  }\n}\n/*# sourceMappingURL=header.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(HeaderComponent, [{
    type: Component,
    args: [{ selector: "app-header", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [ThemeToggleComponent, MatDialogModule, AvatarComponent], template: '<header class="shell-header">\n  <div class="shell-header__title">\n    <span class="shell-header__eyebrow">{{ pageEyebrow() }}</span>\n    <p class="shell-header__heading">{{ pageTitle() }}</p>\n  </div>\n\n  <div class="shell-header__actions">\n    <app-theme-toggle />\n\n    @if (currentUser()) {\n      <button type="button"\n              class="user-chip user-chip--button"\n              aria-label="\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043F\u0440\u043E\u0444\u0438\u043B\u044C"\n              (click)="openProfile()">\n        <app-avatar [avatarId]="profile()?.avatarId" [initials]="userInitial()" [size]="36" />\n        <div class="user-chip__meta">\n          <span class="user-chip__name">{{ profileDisplayName() || roleLabel() }}</span>\n          <span [class]="roleChipClass()">{{ roleLabel() }}</span>\n        </div>\n      </button>\n    }\n  </div>\n</header>\n', styles: ["/* src/app/layout/header/header.component.css */\n:host {\n  display: block;\n  position: sticky;\n  top: 0;\n  z-index: var(--z-sticky, 200);\n}\n.shell-header {\n  display: flex;\n  align-items: center;\n  justify-content: space-between;\n  gap: var(--space-5);\n  height: var(--header-height, 64px);\n  padding: 0 var(--space-6);\n  background: color-mix(in oklab, var(--bg-primary) 82%, transparent);\n  backdrop-filter: blur(14px) saturate(140%);\n  -webkit-backdrop-filter: blur(14px) saturate(140%);\n  border-bottom: 1px solid var(--border-subtle);\n  transition: background-color var(--duration-slow) var(--ease-out), border-color var(--duration-slow) var(--ease-out);\n}\n.shell-header__title {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  min-width: 0;\n}\n.shell-header__eyebrow {\n  font-family: var(--font-mono);\n  font-size: var(--text-xs);\n  font-weight: 500;\n  color: var(--text-muted);\n  letter-spacing: var(--tracking-wide);\n  text-transform: uppercase;\n}\n.shell-header__heading {\n  margin: 0;\n  font-family: var(--font-heading);\n  font-weight: 600;\n  font-size: var(--text-xl);\n  color: var(--text-primary);\n  line-height: 1.2;\n  letter-spacing: -0.01em;\n  white-space: nowrap;\n  overflow: hidden;\n  text-overflow: ellipsis;\n}\n.shell-header__actions {\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n  flex-shrink: 0;\n}\n.user-chip {\n  display: none;\n  align-items: center;\n  gap: var(--space-3);\n  padding: 6px 14px 6px 6px;\n  border-radius: var(--radius-full);\n  background: var(--bg-secondary);\n  border: 1px solid var(--border-subtle);\n  transition:\n    border-color var(--duration-base) var(--ease-out),\n    background-color var(--duration-slow) var(--ease-out),\n    transform var(--duration-fast) var(--ease-out);\n}\n.user-chip--button {\n  cursor: pointer;\n  font: inherit;\n  color: inherit;\n  text-align: left;\n}\n.user-chip:hover {\n  border-color: var(--border-accent);\n}\n.user-chip--button:hover {\n  transform: translateY(-1px);\n}\n.user-chip--button:focus-visible {\n  outline: none;\n  box-shadow: 0 0 0 3px color-mix(in oklab, var(--accent-primary) 35%, transparent);\n}\n@media (min-width: 768px) {\n  .user-chip {\n    display: inline-flex;\n  }\n}\n.user-chip__avatar {\n  width: 32px;\n  height: 32px;\n  border-radius: 50%;\n  display: grid;\n  place-items: center;\n  font-family: var(--font-display);\n  font-weight: 700;\n  font-size: 0.75rem;\n  color: var(--accent-primary-contrast);\n  background: var(--gradient-brand);\n  box-shadow: var(--glow-primary);\n}\n.user-chip app-avatar {\n  line-height: 0;\n}\n.user-chip__meta {\n  display: flex;\n  flex-direction: column;\n  gap: 2px;\n  line-height: 1;\n}\n.user-chip__name {\n  font-family: var(--font-mono);\n  font-size: var(--text-xs);\n  color: var(--text-secondary);\n  letter-spacing: 0.02em;\n}\n.user-chip .role-chip {\n  padding: 1px 0;\n  background: transparent !important;\n}\n@media (max-width: 640px) {\n  .shell-header {\n    padding: 0 var(--space-4);\n    gap: var(--space-3);\n  }\n  .shell-header__heading {\n    font-size: var(--text-lg);\n  }\n}\n/*# sourceMappingURL=header.component.css.map */\n"] }]
  }], () => [], null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(HeaderComponent, { className: "HeaderComponent", filePath: "src/app/layout/header/header.component.ts", lineNumber: 27 });
})();

// src/app/features/student/shared/student-banner.service.ts
var DISMISSED_KEY = "pwa-banner-dismissed";
var SHOWN_KEY = "pwa-banner-shown";
var StudentBannerService = class _StudentBannerService {
  authService = inject(AuthService);
  shouldShow = signal(false);
  /**
   * Call once after auth resolves (e.g. from StudentPwaBannerComponent.ngOnInit).
   * Safe to call multiple times — idempotent.
   */
  init() {
    const user = this.authService.currentUser();
    if (user?.role !== "STUDENT") {
      this.shouldShow.set(false);
      return;
    }
    if (localStorage.getItem(DISMISSED_KEY) === "true") {
      this.shouldShow.set(false);
      return;
    }
    localStorage.setItem(SHOWN_KEY, "true");
    this.shouldShow.set(true);
  }
  /** Permanently suppress banner for this browser. */
  dismiss() {
    localStorage.setItem(DISMISSED_KEY, "true");
    this.shouldShow.set(false);
  }
  static \u0275fac = function StudentBannerService_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentBannerService)();
  };
  static \u0275prov = /* @__PURE__ */ \u0275\u0275defineInjectable({ token: _StudentBannerService, factory: _StudentBannerService.\u0275fac, providedIn: "root" });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentBannerService, [{
    type: Injectable,
    args: [{ providedIn: "root" }]
  }], null, null);
})();

// src/app/layout/shell/student-pwa-banner/student-pwa-banner.component.ts
function StudentPwaBannerComponent_Conditional_0_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 3);
    \u0275\u0275text(1, "\u0423\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u0435 RutTrack: \u043E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 \u0432 Safari \u2192 \u041F\u043E\u0434\u0435\u043B\u0438\u0442\u044C\u0441\u044F \u2192 \u041D\u0430 \u044D\u043A\u0440\u0430\u043D \xAB\u0414\u043E\u043C\u043E\u0439\xBB");
    \u0275\u0275elementEnd();
  }
}
function StudentPwaBannerComponent_Conditional_0_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 3);
    \u0275\u0275text(1, "\u0423\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u0435 RutTrack \u043D\u0430 \u0433\u043B\u0430\u0432\u043D\u044B\u0439 \u044D\u043A\u0440\u0430\u043D");
    \u0275\u0275elementEnd();
  }
}
function StudentPwaBannerComponent_Conditional_0_Conditional_5_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "a", 4);
    \u0275\u0275text(1, " \u041E\u0442\u043A\u0440\u044B\u0442\u044C \u0432 Safari ");
    \u0275\u0275elementEnd();
  }
}
function StudentPwaBannerComponent_Conditional_0_Conditional_6_Template(rf, ctx) {
  if (rf & 1) {
    const _r2 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 9);
    \u0275\u0275listener("click", function StudentPwaBannerComponent_Conditional_0_Conditional_6_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r2);
      const ctx_r2 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r2.install());
    });
    \u0275\u0275text(1, " \u0423\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C \u043F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u0435 ");
    \u0275\u0275elementEnd();
  }
}
function StudentPwaBannerComponent_Conditional_0_Conditional_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "a", 6);
    \u0275\u0275text(1, " \u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u0435 ");
    \u0275\u0275elementEnd();
  }
}
function StudentPwaBannerComponent_Conditional_0_Template(rf, ctx) {
  if (rf & 1) {
    const _r1 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "div", 0);
    \u0275\u0275element(1, "i", 1);
    \u0275\u0275elementStart(2, "div", 2);
    \u0275\u0275template(3, StudentPwaBannerComponent_Conditional_0_Conditional_3_Template, 2, 0, "span", 3)(4, StudentPwaBannerComponent_Conditional_0_Conditional_4_Template, 2, 0, "span", 3);
    \u0275\u0275elementEnd();
    \u0275\u0275template(5, StudentPwaBannerComponent_Conditional_0_Conditional_5_Template, 2, 0, "a", 4)(6, StudentPwaBannerComponent_Conditional_0_Conditional_6_Template, 2, 0, "button", 5)(7, StudentPwaBannerComponent_Conditional_0_Conditional_7_Template, 2, 0, "a", 6);
    \u0275\u0275elementStart(8, "button", 7);
    \u0275\u0275listener("click", function StudentPwaBannerComponent_Conditional_0_Template_button_click_8_listener() {
      \u0275\u0275restoreView(_r1);
      const ctx_r2 = \u0275\u0275nextContext();
      return \u0275\u0275resetView(ctx_r2.dismiss());
    });
    \u0275\u0275element(9, "i", 8);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r2 = \u0275\u0275nextContext();
    \u0275\u0275property("@bannerSlide", void 0);
    \u0275\u0275advance(3);
    \u0275\u0275conditional(ctx_r2.isIos() ? 3 : 4);
    \u0275\u0275advance(2);
    \u0275\u0275conditional(ctx_r2.isIos() ? 5 : ctx_r2.hasPrompt() ? 6 : 7);
  }
}
var StudentPwaBannerComponent = class _StudentPwaBannerComponent {
  bannerService = inject(StudentBannerService);
  isIos = signal(false);
  hasPrompt = signal(false);
  deferredPrompt = null;
  promptHandler = (e) => {
    e.preventDefault();
    this.deferredPrompt = e;
    this.hasPrompt.set(true);
  };
  ngOnInit() {
    this.bannerService.init();
    const ua = navigator.userAgent.toLowerCase();
    const isIosBrowser = /iphone|ipad|ipod/.test(ua) && !window["MSStream"];
    this.isIos.set(isIosBrowser);
    if (!isIosBrowser) {
      window.addEventListener("beforeinstallprompt", this.promptHandler);
    }
  }
  ngOnDestroy() {
    window.removeEventListener("beforeinstallprompt", this.promptHandler);
  }
  install() {
    return __async(this, null, function* () {
      if (this.deferredPrompt) {
        this.deferredPrompt.prompt();
        const { outcome } = yield this.deferredPrompt.userChoice;
        if (outcome === "accepted") {
          this.bannerService.dismiss();
        }
        this.deferredPrompt = null;
        this.hasPrompt.set(false);
      }
    });
  }
  dismiss() {
    this.bannerService.dismiss();
  }
  /** Used to skip animation when user prefers-reduced-motion. */
  prefersReducedMotion() {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }
  static \u0275fac = function StudentPwaBannerComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _StudentPwaBannerComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _StudentPwaBannerComponent, selectors: [["app-student-pwa-banner"]], decls: 1, vars: 1, consts: [["role", "banner", "aria-label", "\u0423\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u0435 \u043F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u0435 RutTrack", 1, "pwa-banner"], ["aria-hidden", "true", 1, "ph-duotone", "ph-device-mobile-camera", "pwa-banner__icon"], [1, "pwa-banner__text"], [1, "pwa-banner__title"], ["href", "/app/", "target", "_blank", "rel", "noopener", 1, "btn-brand", "btn-brand--sm"], ["type", "button", 1, "btn-brand", "btn-brand--sm"], ["href", "/app/", 1, "btn-brand", "btn-brand--sm"], ["type", "button", "aria-label", "\u0417\u0430\u043A\u0440\u044B\u0442\u044C \u0431\u0430\u043D\u043D\u0435\u0440", 1, "pwa-banner__dismiss", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-x"], ["type", "button", 1, "btn-brand", "btn-brand--sm", 3, "click"]], template: function StudentPwaBannerComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275template(0, StudentPwaBannerComponent_Conditional_0_Template, 10, 3, "div", 0);
    }
    if (rf & 2) {
      \u0275\u0275conditional(ctx.bannerService.shouldShow() ? 0 : -1);
    }
  }, dependencies: [CommonModule], styles: ["\n\n.pwa-banner[_ngcontent-%COMP%] {\n  background: var(--bg-secondary);\n  border-bottom: 1px solid var(--border-subtle);\n  border-left: 4px solid var(--accent-primary);\n  padding: var(--space-2) var(--space-5);\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n  flex-wrap: wrap;\n}\n.pwa-banner__icon[_ngcontent-%COMP%] {\n  font-size: 24px;\n  color: var(--accent-primary);\n  flex-shrink: 0;\n}\n.pwa-banner__text[_ngcontent-%COMP%] {\n  flex: 1;\n  min-width: 0;\n}\n.pwa-banner__title[_ngcontent-%COMP%] {\n  font-size: var(--text-sm);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  color: var(--text-primary);\n  display: block;\n}\n.btn-brand--sm[_ngcontent-%COMP%] {\n  padding: 8px 16px;\n  min-height: 36px;\n  font-size: var(--text-sm);\n  text-decoration: none;\n  display: inline-flex;\n  align-items: center;\n  white-space: nowrap;\n}\n.pwa-banner__dismiss[_ngcontent-%COMP%] {\n  width: 32px;\n  height: 32px;\n  border: none;\n  background: transparent;\n  color: var(--text-muted);\n  border-radius: var(--radius-sm);\n  cursor: pointer;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  transition: color 150ms ease;\n  flex-shrink: 0;\n}\n.pwa-banner__dismiss[_ngcontent-%COMP%]:hover {\n  color: var(--text-secondary);\n}\n.pwa-banner__dismiss[_ngcontent-%COMP%]:active {\n  transform: scale(0.92);\n}\n.pwa-banner__dismiss[_ngcontent-%COMP%]:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n/*# sourceMappingURL=student-pwa-banner.component.css.map */"], data: { animation: [
    trigger("bannerSlide", [
      transition(":enter", [
        style({ transform: "translateY(-100%)", opacity: 0 }),
        animate("200ms ease-out", style({ transform: "translateY(0)", opacity: 1 }))
      ]),
      transition(":leave", [
        animate("150ms ease-in", style({ opacity: 0, transform: "translateY(-8px)" }))
      ])
    ])
  ] }, changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(StudentPwaBannerComponent, [{
    type: Component,
    args: [{ selector: "app-student-pwa-banner", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [CommonModule], animations: [
      trigger("bannerSlide", [
        transition(":enter", [
          style({ transform: "translateY(-100%)", opacity: 0 }),
          animate("200ms ease-out", style({ transform: "translateY(0)", opacity: 1 }))
        ]),
        transition(":leave", [
          animate("150ms ease-in", style({ opacity: 0, transform: "translateY(-8px)" }))
        ])
      ])
    ], template: '@if (bannerService.shouldShow()) {\r\n  <div\r\n    class="pwa-banner"\r\n    [@bannerSlide]\r\n    role="banner"\r\n    aria-label="\u0423\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u0435 \u043F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u0435 RutTrack"\r\n  >\r\n    <i class="ph-duotone ph-device-mobile-camera pwa-banner__icon" aria-hidden="true"></i>\r\n\r\n    <div class="pwa-banner__text">\r\n      @if (isIos()) {\r\n        <span class="pwa-banner__title">\u0423\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u0435 RutTrack: \u043E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 \u0432 Safari \u2192 \u041F\u043E\u0434\u0435\u043B\u0438\u0442\u044C\u0441\u044F \u2192 \u041D\u0430 \u044D\u043A\u0440\u0430\u043D \xAB\u0414\u043E\u043C\u043E\u0439\xBB</span>\r\n      } @else {\r\n        <span class="pwa-banner__title">\u0423\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u0435 RutTrack \u043D\u0430 \u0433\u043B\u0430\u0432\u043D\u044B\u0439 \u044D\u043A\u0440\u0430\u043D</span>\r\n      }\r\n    </div>\r\n\r\n    @if (isIos()) {\r\n      <a href="/app/" class="btn-brand btn-brand--sm" target="_blank" rel="noopener">\r\n        \u041E\u0442\u043A\u0440\u044B\u0442\u044C \u0432 Safari\r\n      </a>\r\n    } @else if (hasPrompt()) {\r\n      <button class="btn-brand btn-brand--sm" type="button" (click)="install()">\r\n        \u0423\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C \u043F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u0435\r\n      </button>\r\n    } @else {\r\n      <a href="/app/" class="btn-brand btn-brand--sm">\r\n        \u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u0435\r\n      </a>\r\n    }\r\n\r\n    <button\r\n      class="pwa-banner__dismiss"\r\n      type="button"\r\n      aria-label="\u0417\u0430\u043A\u0440\u044B\u0442\u044C \u0431\u0430\u043D\u043D\u0435\u0440"\r\n      (click)="dismiss()"\r\n    >\r\n      <i class="ph ph-x" aria-hidden="true"></i>\r\n    </button>\r\n  </div>\r\n}\r\n', styles: ["/* src/app/layout/shell/student-pwa-banner/student-pwa-banner.component.css */\n.pwa-banner {\n  background: var(--bg-secondary);\n  border-bottom: 1px solid var(--border-subtle);\n  border-left: 4px solid var(--accent-primary);\n  padding: var(--space-2) var(--space-5);\n  display: flex;\n  align-items: center;\n  gap: var(--space-4);\n  flex-wrap: wrap;\n}\n.pwa-banner__icon {\n  font-size: 24px;\n  color: var(--accent-primary);\n  flex-shrink: 0;\n}\n.pwa-banner__text {\n  flex: 1;\n  min-width: 0;\n}\n.pwa-banner__title {\n  font-size: var(--text-sm);\n  font-family: var(--font-heading);\n  font-weight: 600;\n  color: var(--text-primary);\n  display: block;\n}\n.btn-brand--sm {\n  padding: 8px 16px;\n  min-height: 36px;\n  font-size: var(--text-sm);\n  text-decoration: none;\n  display: inline-flex;\n  align-items: center;\n  white-space: nowrap;\n}\n.pwa-banner__dismiss {\n  width: 32px;\n  height: 32px;\n  border: none;\n  background: transparent;\n  color: var(--text-muted);\n  border-radius: var(--radius-sm);\n  cursor: pointer;\n  display: flex;\n  align-items: center;\n  justify-content: center;\n  transition: color 150ms ease;\n  flex-shrink: 0;\n}\n.pwa-banner__dismiss:hover {\n  color: var(--text-secondary);\n}\n.pwa-banner__dismiss:active {\n  transform: scale(0.92);\n}\n.pwa-banner__dismiss:focus-visible {\n  outline: 2px solid var(--accent-primary);\n  outline-offset: 2px;\n}\n/*# sourceMappingURL=student-pwa-banner.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(StudentPwaBannerComponent, { className: "StudentPwaBannerComponent", filePath: "src/app/layout/shell/student-pwa-banner/student-pwa-banner.component.ts", lineNumber: 46 });
})();

// src/app/layout/shell/shell.component.ts
var ShellComponent = class _ShellComponent {
  static \u0275fac = function ShellComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _ShellComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _ShellComponent, selectors: [["app-shell"]], decls: 8, vars: 0, consts: [[1, "shell"], [1, "shell__sidebar"], [1, "shell__column"], ["tabindex", "-1", 1, "shell__main"], [1, "shell__content"]], template: function ShellComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0);
      \u0275\u0275element(1, "app-sidebar", 1);
      \u0275\u0275elementStart(2, "div", 2);
      \u0275\u0275element(3, "app-header")(4, "app-student-pwa-banner");
      \u0275\u0275elementStart(5, "main", 3)(6, "div", 4);
      \u0275\u0275element(7, "router-outlet");
      \u0275\u0275elementEnd()()()();
    }
  }, dependencies: [RouterOutlet, SidebarComponent, HeaderComponent, StudentPwaBannerComponent], styles: ["\n\n[_nghost-%COMP%] {\n  display: block;\n  height: 100vh;\n  background: var(--bg-primary);\n  color: var(--text-primary);\n}\n.shell[_ngcontent-%COMP%] {\n  display: flex;\n  height: 100%;\n  overflow: hidden;\n}\n.shell__sidebar[_ngcontent-%COMP%] {\n  flex-shrink: 0;\n}\n.shell__column[_ngcontent-%COMP%] {\n  display: flex;\n  flex-direction: column;\n  flex: 1;\n  min-width: 0;\n  min-height: 0;\n  overflow: hidden;\n  background:\n    radial-gradient(\n      ellipse 70% 50% at 50% 0%,\n      color-mix(in oklab, var(--accent-primary) 4%, transparent),\n      transparent 60%),\n    var(--bg-primary);\n}\n.shell__main[_ngcontent-%COMP%] {\n  flex: 1;\n  overflow-y: auto;\n  overflow-x: hidden;\n  outline: none;\n  scrollbar-gutter: stable;\n}\n.shell__content[_ngcontent-%COMP%] {\n  max-width: var(--content-max-width, 1280px);\n  margin: 0 auto;\n  padding: var(--space-6) var(--space-6) var(--space-9);\n}\n@media (max-width: 768px) {\n  .shell__content[_ngcontent-%COMP%] {\n    padding: var(--space-4) var(--space-4) var(--space-8);\n  }\n}\n.shell__main[_ngcontent-%COMP%]::-webkit-scrollbar {\n  width: 12px;\n  height: 12px;\n}\n.shell__main[_ngcontent-%COMP%]::-webkit-scrollbar-track {\n  background: transparent;\n}\n.shell__main[_ngcontent-%COMP%]::-webkit-scrollbar-thumb {\n  background: var(--border-default);\n  border-radius: var(--radius-full);\n  border: 3px solid var(--bg-primary);\n}\n.shell__main[_ngcontent-%COMP%]::-webkit-scrollbar-thumb:hover {\n  background: var(--border-accent);\n}\n/*# sourceMappingURL=shell.component.css.map */"], changeDetection: 0 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(ShellComponent, [{
    type: Component,
    args: [{ selector: "app-shell", standalone: true, changeDetection: ChangeDetectionStrategy.OnPush, imports: [RouterOutlet, SidebarComponent, HeaderComponent, StudentPwaBannerComponent], template: '<div class="shell">\r\n  <app-sidebar class="shell__sidebar" />\r\n\r\n  <div class="shell__column">\r\n    <app-header />\r\n    <app-student-pwa-banner />\r\n    <main class="shell__main" tabindex="-1">\r\n      <div class="shell__content">\r\n        <router-outlet />\r\n      </div>\r\n    </main>\r\n  </div>\r\n</div>\r\n', styles: ["/* src/app/layout/shell/shell.component.css */\n:host {\n  display: block;\n  height: 100vh;\n  background: var(--bg-primary);\n  color: var(--text-primary);\n}\n.shell {\n  display: flex;\n  height: 100%;\n  overflow: hidden;\n}\n.shell__sidebar {\n  flex-shrink: 0;\n}\n.shell__column {\n  display: flex;\n  flex-direction: column;\n  flex: 1;\n  min-width: 0;\n  min-height: 0;\n  overflow: hidden;\n  background:\n    radial-gradient(\n      ellipse 70% 50% at 50% 0%,\n      color-mix(in oklab, var(--accent-primary) 4%, transparent),\n      transparent 60%),\n    var(--bg-primary);\n}\n.shell__main {\n  flex: 1;\n  overflow-y: auto;\n  overflow-x: hidden;\n  outline: none;\n  scrollbar-gutter: stable;\n}\n.shell__content {\n  max-width: var(--content-max-width, 1280px);\n  margin: 0 auto;\n  padding: var(--space-6) var(--space-6) var(--space-9);\n}\n@media (max-width: 768px) {\n  .shell__content {\n    padding: var(--space-4) var(--space-4) var(--space-8);\n  }\n}\n.shell__main::-webkit-scrollbar {\n  width: 12px;\n  height: 12px;\n}\n.shell__main::-webkit-scrollbar-track {\n  background: transparent;\n}\n.shell__main::-webkit-scrollbar-thumb {\n  background: var(--border-default);\n  border-radius: var(--radius-full);\n  border: 3px solid var(--bg-primary);\n}\n.shell__main::-webkit-scrollbar-thumb:hover {\n  background: var(--border-accent);\n}\n/*# sourceMappingURL=shell.component.css.map */\n"] }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(ShellComponent, { className: "ShellComponent", filePath: "src/app/layout/shell/shell.component.ts", lineNumber: 23 });
})();
export {
  ShellComponent
};
//# sourceMappingURL=chunk-IQM2FRC2.js.map
