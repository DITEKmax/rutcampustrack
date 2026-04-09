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
import "./chunk-U7ADN4CW.js";
import {
  _MatInternalFormField
} from "./chunk-JRVO2TNY.js";
import {
  MatInput,
  MatInputModule
} from "./chunk-XDHRGA6B.js";
import {
  MatSelect,
  MatSelectModule
} from "./chunk-C3QW7W42.js";
import "./chunk-GDZ3HOTB.js";
import {
  MatProgressBar,
  MatProgressBarModule
} from "./chunk-WFRMJ3YJ.js";
import {
  MatOption
} from "./chunk-4GAYHM76.js";
import {
  MatButton,
  MatButtonModule,
  MatError,
  MatFormField,
  MatFormFieldModule,
  MatIconButton,
  MatLabel
} from "./chunk-SDENFCBC.js";
import {
  CheckboxRequiredValidator,
  DefaultValueAccessor,
  FormBuilder,
  FormControlName,
  FormGroupDirective,
  MaxLengthValidator,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  NgControlStatus,
  NgControlStatusGroup,
  ReactiveFormsModule,
  Validators,
  ɵNgNoValidate
} from "./chunk-52TXXDGC.js";
import "./chunk-QNELF7JG.js";
import {
  MatRipple,
  _StructuralStylesLoader
} from "./chunk-G7PFIUH5.js";
import {
  FocusMonitor,
  MatCommonModule,
  _CdkPrivateStyleLoader,
  _IdGenerator
} from "./chunk-NITJSJ4E.js";
import {
  AdminApiService
} from "./chunk-W5VNZ3AB.js";
import "./chunk-LGL42SQS.js";
import "./chunk-T4R75CF5.js";
import {
  CommonModule
} from "./chunk-L2FQVI4C.js";
import {
  ANIMATION_MODULE_TYPE,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  Directive,
  ElementRef,
  EventEmitter,
  HostAttributeToken,
  InjectionToken,
  Input,
  NgModule,
  Output,
  ViewChild,
  ViewEncapsulation,
  booleanAttribute,
  forkJoin,
  forwardRef,
  inject,
  numberAttribute,
  setClassMetadata,
  signal,
  ɵsetClassDebugInfo,
  ɵɵInheritDefinitionFeature,
  ɵɵNgOnChangesFeature,
  ɵɵProvidersFeature,
  ɵɵadvance,
  ɵɵattribute,
  ɵɵclassMap,
  ɵɵclassProp,
  ɵɵconditional,
  ɵɵdefineComponent,
  ɵɵdefineDirective,
  ɵɵdefineInjector,
  ɵɵdefineNgModule,
  ɵɵelement,
  ɵɵelementContainerEnd,
  ɵɵelementContainerStart,
  ɵɵelementEnd,
  ɵɵelementStart,
  ɵɵgetCurrentView,
  ɵɵgetInheritedFactory,
  ɵɵhostProperty,
  ɵɵlistener,
  ɵɵloadQuery,
  ɵɵnamespaceSVG,
  ɵɵnextContext,
  ɵɵprojection,
  ɵɵprojectionDef,
  ɵɵproperty,
  ɵɵqueryRefresh,
  ɵɵreference,
  ɵɵrepeater,
  ɵɵrepeaterCreate,
  ɵɵresetView,
  ɵɵrestoreView,
  ɵɵtemplate,
  ɵɵtext,
  ɵɵtextInterpolate,
  ɵɵtextInterpolate1,
  ɵɵtextInterpolate2,
  ɵɵtwoWayBindingSet,
  ɵɵtwoWayListener,
  ɵɵtwoWayProperty,
  ɵɵviewQuery
} from "./chunk-WGFJ2PWY.js";

// node_modules/@angular/material/fesm2022/slide-toggle.mjs
var _c0 = ["switch"];
var _c1 = ["*"];
function MatSlideToggle_Conditional_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "span", 10);
    \u0275\u0275namespaceSVG();
    \u0275\u0275elementStart(1, "svg", 12);
    \u0275\u0275element(2, "path", 13);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(3, "svg", 14);
    \u0275\u0275element(4, "path", 15);
    \u0275\u0275elementEnd()();
  }
}
var MAT_SLIDE_TOGGLE_DEFAULT_OPTIONS = new InjectionToken("mat-slide-toggle-default-options", {
  providedIn: "root",
  factory: () => ({
    disableToggleValue: false,
    hideIcon: false,
    disabledInteractive: false
  })
});
var MAT_SLIDE_TOGGLE_VALUE_ACCESSOR = {
  provide: NG_VALUE_ACCESSOR,
  useExisting: forwardRef(() => MatSlideToggle),
  multi: true
};
var MatSlideToggleChange = class {
  source;
  checked;
  constructor(source, checked) {
    this.source = source;
    this.checked = checked;
  }
};
var MatSlideToggle = class _MatSlideToggle {
  _elementRef = inject(ElementRef);
  _focusMonitor = inject(FocusMonitor);
  _changeDetectorRef = inject(ChangeDetectorRef);
  defaults = inject(MAT_SLIDE_TOGGLE_DEFAULT_OPTIONS);
  _onChange = (_) => {
  };
  _onTouched = () => {
  };
  _validatorOnChange = () => {
  };
  _uniqueId;
  _checked = false;
  _createChangeEvent(isChecked) {
    return new MatSlideToggleChange(this, isChecked);
  }
  /** Unique ID for the label element. */
  _labelId;
  /** Returns the unique id for the visual hidden button. */
  get buttonId() {
    return `${this.id || this._uniqueId}-button`;
  }
  /** Reference to the MDC switch element. */
  _switchElement;
  /** Focuses the slide-toggle. */
  focus() {
    this._switchElement.nativeElement.focus();
  }
  /** Whether noop animations are enabled. */
  _noopAnimations;
  /** Whether the slide toggle is currently focused. */
  _focused;
  /** Name value will be applied to the input element if present. */
  name = null;
  /** A unique id for the slide-toggle input. If none is supplied, it will be auto-generated. */
  id;
  /** Whether the label should appear after or before the slide-toggle. Defaults to 'after'. */
  labelPosition = "after";
  /** Used to set the aria-label attribute on the underlying input element. */
  ariaLabel = null;
  /** Used to set the aria-labelledby attribute on the underlying input element. */
  ariaLabelledby = null;
  /** Used to set the aria-describedby attribute on the underlying input element. */
  ariaDescribedby;
  /** Whether the slide-toggle is required. */
  required;
  // TODO(crisbeto): this should be a ThemePalette, but some internal apps were abusing
  // the lack of type checking previously and assigning random strings.
  /**
   * Theme color of the slide toggle. This API is supported in M2 themes only,
   * it has no effect in M3 themes. For color customization in M3, see https://material.angular.dev/components/slide-toggle/styling.
   *
   * For information on applying color variants in M3, see
   * https://material.angular.dev/guide/material-2-theming#optional-add-backwards-compatibility-styles-for-color-variants
   */
  color;
  /** Whether the slide toggle is disabled. */
  disabled = false;
  /** Whether the slide toggle has a ripple. */
  disableRipple = false;
  /** Tabindex of slide toggle. */
  tabIndex = 0;
  /** Whether the slide-toggle element is checked or not. */
  get checked() {
    return this._checked;
  }
  set checked(value) {
    this._checked = value;
    this._changeDetectorRef.markForCheck();
  }
  /** Whether to hide the icon inside of the slide toggle. */
  hideIcon;
  /** Whether the slide toggle should remain interactive when it is disabled. */
  disabledInteractive;
  /** An event will be dispatched each time the slide-toggle changes its value. */
  change = new EventEmitter();
  /**
   * An event will be dispatched each time the slide-toggle input is toggled.
   * This event is always emitted when the user toggles the slide toggle, but this does not mean
   * the slide toggle's value has changed.
   */
  toggleChange = new EventEmitter();
  /** Returns the unique id for the visual hidden input. */
  get inputId() {
    return `${this.id || this._uniqueId}-input`;
  }
  constructor() {
    inject(_CdkPrivateStyleLoader).load(_StructuralStylesLoader);
    const tabIndex = inject(new HostAttributeToken("tabindex"), {
      optional: true
    });
    const defaults = this.defaults;
    const animationMode = inject(ANIMATION_MODULE_TYPE, {
      optional: true
    });
    this.tabIndex = tabIndex == null ? 0 : parseInt(tabIndex) || 0;
    this.color = defaults.color || "accent";
    this._noopAnimations = animationMode === "NoopAnimations";
    this.id = this._uniqueId = inject(_IdGenerator).getId("mat-mdc-slide-toggle-");
    this.hideIcon = defaults.hideIcon ?? false;
    this.disabledInteractive = defaults.disabledInteractive ?? false;
    this._labelId = this._uniqueId + "-label";
  }
  ngAfterContentInit() {
    this._focusMonitor.monitor(this._elementRef, true).subscribe((focusOrigin) => {
      if (focusOrigin === "keyboard" || focusOrigin === "program") {
        this._focused = true;
        this._changeDetectorRef.markForCheck();
      } else if (!focusOrigin) {
        Promise.resolve().then(() => {
          this._focused = false;
          this._onTouched();
          this._changeDetectorRef.markForCheck();
        });
      }
    });
  }
  ngOnChanges(changes) {
    if (changes["required"]) {
      this._validatorOnChange();
    }
  }
  ngOnDestroy() {
    this._focusMonitor.stopMonitoring(this._elementRef);
  }
  /** Implemented as part of ControlValueAccessor. */
  writeValue(value) {
    this.checked = !!value;
  }
  /** Implemented as part of ControlValueAccessor. */
  registerOnChange(fn) {
    this._onChange = fn;
  }
  /** Implemented as part of ControlValueAccessor. */
  registerOnTouched(fn) {
    this._onTouched = fn;
  }
  /** Implemented as a part of Validator. */
  validate(control) {
    return this.required && control.value !== true ? {
      "required": true
    } : null;
  }
  /** Implemented as a part of Validator. */
  registerOnValidatorChange(fn) {
    this._validatorOnChange = fn;
  }
  /** Implemented as a part of ControlValueAccessor. */
  setDisabledState(isDisabled) {
    this.disabled = isDisabled;
    this._changeDetectorRef.markForCheck();
  }
  /** Toggles the checked state of the slide-toggle. */
  toggle() {
    this.checked = !this.checked;
    this._onChange(this.checked);
  }
  /**
   * Emits a change event on the `change` output. Also notifies the FormControl about the change.
   */
  _emitChangeEvent() {
    this._onChange(this.checked);
    this.change.emit(this._createChangeEvent(this.checked));
  }
  /** Method being called whenever the underlying button is clicked. */
  _handleClick() {
    if (!this.disabled) {
      this.toggleChange.emit();
      if (!this.defaults.disableToggleValue) {
        this.checked = !this.checked;
        this._onChange(this.checked);
        this.change.emit(new MatSlideToggleChange(this, this.checked));
      }
    }
  }
  _getAriaLabelledBy() {
    if (this.ariaLabelledby) {
      return this.ariaLabelledby;
    }
    return this.ariaLabel ? null : this._labelId;
  }
  static \u0275fac = function MatSlideToggle_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _MatSlideToggle)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({
    type: _MatSlideToggle,
    selectors: [["mat-slide-toggle"]],
    viewQuery: function MatSlideToggle_Query(rf, ctx) {
      if (rf & 1) {
        \u0275\u0275viewQuery(_c0, 5);
      }
      if (rf & 2) {
        let _t;
        \u0275\u0275queryRefresh(_t = \u0275\u0275loadQuery()) && (ctx._switchElement = _t.first);
      }
    },
    hostAttrs: [1, "mat-mdc-slide-toggle"],
    hostVars: 13,
    hostBindings: function MatSlideToggle_HostBindings(rf, ctx) {
      if (rf & 2) {
        \u0275\u0275hostProperty("id", ctx.id);
        \u0275\u0275attribute("tabindex", null)("aria-label", null)("name", null)("aria-labelledby", null);
        \u0275\u0275classMap(ctx.color ? "mat-" + ctx.color : "");
        \u0275\u0275classProp("mat-mdc-slide-toggle-focused", ctx._focused)("mat-mdc-slide-toggle-checked", ctx.checked)("_mat-animation-noopable", ctx._noopAnimations);
      }
    },
    inputs: {
      name: "name",
      id: "id",
      labelPosition: "labelPosition",
      ariaLabel: [0, "aria-label", "ariaLabel"],
      ariaLabelledby: [0, "aria-labelledby", "ariaLabelledby"],
      ariaDescribedby: [0, "aria-describedby", "ariaDescribedby"],
      required: [2, "required", "required", booleanAttribute],
      color: "color",
      disabled: [2, "disabled", "disabled", booleanAttribute],
      disableRipple: [2, "disableRipple", "disableRipple", booleanAttribute],
      tabIndex: [2, "tabIndex", "tabIndex", (value) => value == null ? 0 : numberAttribute(value)],
      checked: [2, "checked", "checked", booleanAttribute],
      hideIcon: [2, "hideIcon", "hideIcon", booleanAttribute],
      disabledInteractive: [2, "disabledInteractive", "disabledInteractive", booleanAttribute]
    },
    outputs: {
      change: "change",
      toggleChange: "toggleChange"
    },
    exportAs: ["matSlideToggle"],
    features: [\u0275\u0275ProvidersFeature([MAT_SLIDE_TOGGLE_VALUE_ACCESSOR, {
      provide: NG_VALIDATORS,
      useExisting: _MatSlideToggle,
      multi: true
    }]), \u0275\u0275NgOnChangesFeature],
    ngContentSelectors: _c1,
    decls: 13,
    vars: 27,
    consts: [["switch", ""], ["mat-internal-form-field", "", 3, "labelPosition"], ["role", "switch", "type", "button", 1, "mdc-switch", 3, "click", "tabIndex", "disabled"], [1, "mdc-switch__track"], [1, "mdc-switch__handle-track"], [1, "mdc-switch__handle"], [1, "mdc-switch__shadow"], [1, "mdc-elevation-overlay"], [1, "mdc-switch__ripple"], ["mat-ripple", "", 1, "mat-mdc-slide-toggle-ripple", "mat-focus-indicator", 3, "matRippleTrigger", "matRippleDisabled", "matRippleCentered"], [1, "mdc-switch__icons"], [1, "mdc-label", 3, "click", "for"], ["viewBox", "0 0 24 24", "aria-hidden", "true", 1, "mdc-switch__icon", "mdc-switch__icon--on"], ["d", "M19.69,5.23L8.96,15.96l-4.23-4.23L2.96,13.5l6,6L21.46,7L19.69,5.23z"], ["viewBox", "0 0 24 24", "aria-hidden", "true", 1, "mdc-switch__icon", "mdc-switch__icon--off"], ["d", "M20 13H4v-2h16v2z"]],
    template: function MatSlideToggle_Template(rf, ctx) {
      if (rf & 1) {
        const _r1 = \u0275\u0275getCurrentView();
        \u0275\u0275projectionDef();
        \u0275\u0275elementStart(0, "div", 1)(1, "button", 2, 0);
        \u0275\u0275listener("click", function MatSlideToggle_Template_button_click_1_listener() {
          \u0275\u0275restoreView(_r1);
          return \u0275\u0275resetView(ctx._handleClick());
        });
        \u0275\u0275element(3, "span", 3);
        \u0275\u0275elementStart(4, "span", 4)(5, "span", 5)(6, "span", 6);
        \u0275\u0275element(7, "span", 7);
        \u0275\u0275elementEnd();
        \u0275\u0275elementStart(8, "span", 8);
        \u0275\u0275element(9, "span", 9);
        \u0275\u0275elementEnd();
        \u0275\u0275template(10, MatSlideToggle_Conditional_10_Template, 5, 0, "span", 10);
        \u0275\u0275elementEnd()()();
        \u0275\u0275elementStart(11, "label", 11);
        \u0275\u0275listener("click", function MatSlideToggle_Template_label_click_11_listener($event) {
          \u0275\u0275restoreView(_r1);
          return \u0275\u0275resetView($event.stopPropagation());
        });
        \u0275\u0275projection(12);
        \u0275\u0275elementEnd()();
      }
      if (rf & 2) {
        const switch_r2 = \u0275\u0275reference(2);
        \u0275\u0275property("labelPosition", ctx.labelPosition);
        \u0275\u0275advance();
        \u0275\u0275classProp("mdc-switch--selected", ctx.checked)("mdc-switch--unselected", !ctx.checked)("mdc-switch--checked", ctx.checked)("mdc-switch--disabled", ctx.disabled)("mat-mdc-slide-toggle-disabled-interactive", ctx.disabledInteractive);
        \u0275\u0275property("tabIndex", ctx.disabled && !ctx.disabledInteractive ? -1 : ctx.tabIndex)("disabled", ctx.disabled && !ctx.disabledInteractive);
        \u0275\u0275attribute("id", ctx.buttonId)("name", ctx.name)("aria-label", ctx.ariaLabel)("aria-labelledby", ctx._getAriaLabelledBy())("aria-describedby", ctx.ariaDescribedby)("aria-required", ctx.required || null)("aria-checked", ctx.checked)("aria-disabled", ctx.disabled && ctx.disabledInteractive ? "true" : null);
        \u0275\u0275advance(8);
        \u0275\u0275property("matRippleTrigger", switch_r2)("matRippleDisabled", ctx.disableRipple || ctx.disabled)("matRippleCentered", true);
        \u0275\u0275advance();
        \u0275\u0275conditional(!ctx.hideIcon ? 10 : -1);
        \u0275\u0275advance();
        \u0275\u0275property("for", ctx.buttonId);
        \u0275\u0275attribute("id", ctx._labelId);
      }
    },
    dependencies: [MatRipple, _MatInternalFormField],
    styles: ['.mdc-switch{align-items:center;background:none;border:none;cursor:pointer;display:inline-flex;flex-shrink:0;margin:0;outline:none;overflow:visible;padding:0;position:relative;width:var(--mdc-switch-track-width, 52px)}.mdc-switch.mdc-switch--disabled{cursor:default;pointer-events:none}.mdc-switch.mat-mdc-slide-toggle-disabled-interactive{pointer-events:auto}.mdc-switch__track{overflow:hidden;position:relative;width:100%;height:var(--mdc-switch-track-height, 32px);border-radius:var(--mdc-switch-track-shape, var(--mat-sys-corner-full))}.mdc-switch--disabled.mdc-switch .mdc-switch__track{opacity:var(--mdc-switch-disabled-track-opacity, 0.12)}.mdc-switch__track::before,.mdc-switch__track::after{border:1px solid rgba(0,0,0,0);border-radius:inherit;box-sizing:border-box;content:"";height:100%;left:0;position:absolute;width:100%;border-width:var(--mat-switch-track-outline-width, 2px);border-color:var(--mat-switch-track-outline-color, var(--mat-sys-outline))}.mdc-switch--selected .mdc-switch__track::before,.mdc-switch--selected .mdc-switch__track::after{border-width:var(--mat-switch-selected-track-outline-width, 2px);border-color:var(--mat-switch-selected-track-outline-color, transparent)}.mdc-switch--disabled .mdc-switch__track::before,.mdc-switch--disabled .mdc-switch__track::after{border-width:var(--mat-switch-disabled-unselected-track-outline-width, 2px);border-color:var(--mat-switch-disabled-unselected-track-outline-color, var(--mat-sys-on-surface))}@media(forced-colors: active){.mdc-switch__track{border-color:currentColor}}.mdc-switch__track::before{transition:transform 75ms 0ms cubic-bezier(0, 0, 0.2, 1);transform:translateX(0);background:var(--mdc-switch-unselected-track-color, var(--mat-sys-surface-variant))}.mdc-switch--selected .mdc-switch__track::before{transition:transform 75ms 0ms cubic-bezier(0.4, 0, 0.6, 1);transform:translateX(100%)}[dir=rtl] .mdc-switch--selected .mdc-switch--selected .mdc-switch__track::before{transform:translateX(-100%)}.mdc-switch--selected .mdc-switch__track::before{opacity:var(--mat-switch-hidden-track-opacity, 0);transition:var(--mat-switch-hidden-track-transition, opacity 75ms)}.mdc-switch--unselected .mdc-switch__track::before{opacity:var(--mat-switch-visible-track-opacity, 1);transition:var(--mat-switch-visible-track-transition, opacity 75ms)}.mdc-switch:enabled:hover:not(:focus):not(:active) .mdc-switch__track::before{background:var(--mdc-switch-unselected-hover-track-color, var(--mat-sys-surface-variant))}.mdc-switch:enabled:focus:not(:active) .mdc-switch__track::before{background:var(--mdc-switch-unselected-focus-track-color, var(--mat-sys-surface-variant))}.mdc-switch:enabled:active .mdc-switch__track::before{background:var(--mdc-switch-unselected-pressed-track-color, var(--mat-sys-surface-variant))}.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:hover:not(:focus):not(:active) .mdc-switch__track::before,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:focus:not(:active) .mdc-switch__track::before,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:active .mdc-switch__track::before,.mdc-switch.mdc-switch--disabled .mdc-switch__track::before{background:var(--mdc-switch-disabled-unselected-track-color, var(--mat-sys-surface-variant))}.mdc-switch__track::after{transform:translateX(-100%);background:var(--mdc-switch-selected-track-color, var(--mat-sys-primary))}[dir=rtl] .mdc-switch__track::after{transform:translateX(100%)}.mdc-switch--selected .mdc-switch__track::after{transform:translateX(0)}.mdc-switch--selected .mdc-switch__track::after{opacity:var(--mat-switch-visible-track-opacity, 1);transition:var(--mat-switch-visible-track-transition, opacity 75ms)}.mdc-switch--unselected .mdc-switch__track::after{opacity:var(--mat-switch-hidden-track-opacity, 0);transition:var(--mat-switch-hidden-track-transition, opacity 75ms)}.mdc-switch:enabled:hover:not(:focus):not(:active) .mdc-switch__track::after{background:var(--mdc-switch-selected-hover-track-color, var(--mat-sys-primary))}.mdc-switch:enabled:focus:not(:active) .mdc-switch__track::after{background:var(--mdc-switch-selected-focus-track-color, var(--mat-sys-primary))}.mdc-switch:enabled:active .mdc-switch__track::after{background:var(--mdc-switch-selected-pressed-track-color, var(--mat-sys-primary))}.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:hover:not(:focus):not(:active) .mdc-switch__track::after,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:focus:not(:active) .mdc-switch__track::after,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:active .mdc-switch__track::after,.mdc-switch.mdc-switch--disabled .mdc-switch__track::after{background:var(--mdc-switch-disabled-selected-track-color, var(--mat-sys-on-surface))}.mdc-switch__handle-track{height:100%;pointer-events:none;position:absolute;top:0;transition:transform 75ms 0ms cubic-bezier(0.4, 0, 0.2, 1);left:0;right:auto;transform:translateX(0);width:calc(100% - var(--mdc-switch-handle-width))}[dir=rtl] .mdc-switch__handle-track{left:auto;right:0}.mdc-switch--selected .mdc-switch__handle-track{transform:translateX(100%)}[dir=rtl] .mdc-switch--selected .mdc-switch__handle-track{transform:translateX(-100%)}.mdc-switch__handle{display:flex;pointer-events:auto;position:absolute;top:50%;transform:translateY(-50%);left:0;right:auto;transition:width 75ms cubic-bezier(0.4, 0, 0.2, 1),height 75ms cubic-bezier(0.4, 0, 0.2, 1),margin 75ms cubic-bezier(0.4, 0, 0.2, 1);width:var(--mdc-switch-handle-width);height:var(--mdc-switch-handle-height);border-radius:var(--mdc-switch-handle-shape, var(--mat-sys-corner-full))}[dir=rtl] .mdc-switch__handle{left:auto;right:0}.mat-mdc-slide-toggle .mdc-switch--unselected .mdc-switch__handle{width:var(--mat-switch-unselected-handle-size, 16px);height:var(--mat-switch-unselected-handle-size, 16px);margin:var(--mat-switch-unselected-handle-horizontal-margin, 0 8px)}.mat-mdc-slide-toggle .mdc-switch--unselected .mdc-switch__handle:has(.mdc-switch__icons){margin:var(--mat-switch-unselected-with-icon-handle-horizontal-margin, 0 4px)}.mat-mdc-slide-toggle .mdc-switch--selected .mdc-switch__handle{width:var(--mat-switch-selected-handle-size, 24px);height:var(--mat-switch-selected-handle-size, 24px);margin:var(--mat-switch-selected-handle-horizontal-margin, 0 24px)}.mat-mdc-slide-toggle .mdc-switch--selected .mdc-switch__handle:has(.mdc-switch__icons){margin:var(--mat-switch-selected-with-icon-handle-horizontal-margin, 0 24px)}.mat-mdc-slide-toggle .mdc-switch__handle:has(.mdc-switch__icons){width:var(--mat-switch-with-icon-handle-size, 24px);height:var(--mat-switch-with-icon-handle-size, 24px)}.mat-mdc-slide-toggle .mdc-switch:active:not(.mdc-switch--disabled) .mdc-switch__handle{width:var(--mat-switch-pressed-handle-size, 28px);height:var(--mat-switch-pressed-handle-size, 28px)}.mat-mdc-slide-toggle .mdc-switch--selected:active:not(.mdc-switch--disabled) .mdc-switch__handle{margin:var(--mat-switch-selected-pressed-handle-horizontal-margin, 0 22px)}.mat-mdc-slide-toggle .mdc-switch--unselected:active:not(.mdc-switch--disabled) .mdc-switch__handle{margin:var(--mat-switch-unselected-pressed-handle-horizontal-margin, 0 2px)}.mdc-switch--disabled.mdc-switch--selected .mdc-switch__handle::after{opacity:var(--mat-switch-disabled-selected-handle-opacity, 1)}.mdc-switch--disabled.mdc-switch--unselected .mdc-switch__handle::after{opacity:var(--mat-switch-disabled-unselected-handle-opacity, 0.38)}.mdc-switch__handle::before,.mdc-switch__handle::after{border:1px solid rgba(0,0,0,0);border-radius:inherit;box-sizing:border-box;content:"";width:100%;height:100%;left:0;position:absolute;top:0;transition:background-color 75ms 0ms cubic-bezier(0.4, 0, 0.2, 1),border-color 75ms 0ms cubic-bezier(0.4, 0, 0.2, 1);z-index:-1}@media(forced-colors: active){.mdc-switch__handle::before,.mdc-switch__handle::after{border-color:currentColor}}.mdc-switch--selected:enabled .mdc-switch__handle::after{background:var(--mdc-switch-selected-handle-color, var(--mat-sys-on-primary))}.mdc-switch--selected:enabled:hover:not(:focus):not(:active) .mdc-switch__handle::after{background:var(--mdc-switch-selected-hover-handle-color, var(--mat-sys-primary-container))}.mdc-switch--selected:enabled:focus:not(:active) .mdc-switch__handle::after{background:var(--mdc-switch-selected-focus-handle-color, var(--mat-sys-primary-container))}.mdc-switch--selected:enabled:active .mdc-switch__handle::after{background:var(--mdc-switch-selected-pressed-handle-color, var(--mat-sys-primary-container))}.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled.mdc-switch--selected:hover:not(:focus):not(:active) .mdc-switch__handle::after,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled.mdc-switch--selected:focus:not(:active) .mdc-switch__handle::after,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled.mdc-switch--selected:active .mdc-switch__handle::after,.mdc-switch--selected.mdc-switch--disabled .mdc-switch__handle::after{background:var(--mdc-switch-disabled-selected-handle-color, var(--mat-sys-surface))}.mdc-switch--unselected:enabled .mdc-switch__handle::after{background:var(--mdc-switch-unselected-handle-color, var(--mat-sys-outline))}.mdc-switch--unselected:enabled:hover:not(:focus):not(:active) .mdc-switch__handle::after{background:var(--mdc-switch-unselected-hover-handle-color, var(--mat-sys-on-surface-variant))}.mdc-switch--unselected:enabled:focus:not(:active) .mdc-switch__handle::after{background:var(--mdc-switch-unselected-focus-handle-color, var(--mat-sys-on-surface-variant))}.mdc-switch--unselected:enabled:active .mdc-switch__handle::after{background:var(--mdc-switch-unselected-pressed-handle-color, var(--mat-sys-on-surface-variant))}.mdc-switch--unselected.mdc-switch--disabled .mdc-switch__handle::after{background:var(--mdc-switch-disabled-unselected-handle-color, var(--mat-sys-on-surface))}.mdc-switch__handle::before{background:var(--mdc-switch-handle-surface-color)}.mdc-switch__shadow{border-radius:inherit;bottom:0;left:0;position:absolute;right:0;top:0}.mdc-switch:enabled .mdc-switch__shadow{box-shadow:var(--mdc-switch-handle-elevation-shadow)}.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:hover:not(:focus):not(:active) .mdc-switch__shadow,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:focus:not(:active) .mdc-switch__shadow,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:active .mdc-switch__shadow,.mdc-switch.mdc-switch--disabled .mdc-switch__shadow{box-shadow:var(--mdc-switch-disabled-handle-elevation-shadow)}.mdc-switch__ripple{left:50%;position:absolute;top:50%;transform:translate(-50%, -50%);z-index:-1;width:var(--mdc-switch-state-layer-size, 40px);height:var(--mdc-switch-state-layer-size, 40px)}.mdc-switch__ripple::after{content:"";opacity:0}.mdc-switch--disabled .mdc-switch__ripple::after{display:none}.mat-mdc-slide-toggle-disabled-interactive .mdc-switch__ripple::after{display:block}.mdc-switch:hover .mdc-switch__ripple::after{opacity:.04;transition:75ms opacity cubic-bezier(0, 0, 0.2, 1)}.mat-mdc-slide-toggle.mat-mdc-slide-toggle-focused .mdc-switch .mdc-switch__ripple::after{opacity:.12}.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:enabled:focus .mdc-switch__ripple::after,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:enabled:active .mdc-switch__ripple::after,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:enabled:hover:not(:focus) .mdc-switch__ripple::after,.mdc-switch--unselected:enabled:hover:not(:focus) .mdc-switch__ripple::after{background:var(--mdc-switch-unselected-hover-state-layer-color, var(--mat-sys-on-surface))}.mdc-switch--unselected:enabled:focus .mdc-switch__ripple::after{background:var(--mdc-switch-unselected-focus-state-layer-color, var(--mat-sys-on-surface))}.mdc-switch--unselected:enabled:active .mdc-switch__ripple::after{background:var(--mdc-switch-unselected-pressed-state-layer-color, var(--mat-sys-on-surface));opacity:var(--mdc-switch-unselected-pressed-state-layer-opacity, var(--mat-sys-pressed-state-layer-opacity));transition:opacity 75ms linear}.mdc-switch--selected:enabled:hover:not(:focus) .mdc-switch__ripple::after{background:var(--mdc-switch-selected-hover-state-layer-color, var(--mat-sys-primary))}.mdc-switch--selected:enabled:focus .mdc-switch__ripple::after{background:var(--mdc-switch-selected-focus-state-layer-color, var(--mat-sys-primary))}.mdc-switch--selected:enabled:active .mdc-switch__ripple::after{background:var(--mdc-switch-selected-pressed-state-layer-color, var(--mat-sys-primary));opacity:var(--mdc-switch-selected-pressed-state-layer-opacity, var(--mat-sys-pressed-state-layer-opacity));transition:opacity 75ms linear}.mdc-switch__icons{position:relative;height:100%;width:100%;z-index:1;transform:translateZ(0)}.mdc-switch--disabled.mdc-switch--unselected .mdc-switch__icons{opacity:var(--mdc-switch-disabled-unselected-icon-opacity, 0.38)}.mdc-switch--disabled.mdc-switch--selected .mdc-switch__icons{opacity:var(--mdc-switch-disabled-selected-icon-opacity, 0.38)}.mdc-switch__icon{bottom:0;left:0;margin:auto;position:absolute;right:0;top:0;opacity:0;transition:opacity 30ms 0ms cubic-bezier(0.4, 0, 1, 1)}.mdc-switch--unselected .mdc-switch__icon{width:var(--mdc-switch-unselected-icon-size, 16px);height:var(--mdc-switch-unselected-icon-size, 16px);fill:var(--mdc-switch-unselected-icon-color, var(--mat-sys-surface-variant))}.mdc-switch--unselected.mdc-switch--disabled .mdc-switch__icon{fill:var(--mdc-switch-disabled-unselected-icon-color, var(--mat-sys-surface-variant))}.mdc-switch--selected .mdc-switch__icon{width:var(--mdc-switch-selected-icon-size, 16px);height:var(--mdc-switch-selected-icon-size, 16px);fill:var(--mdc-switch-selected-icon-color, var(--mat-sys-on-primary-container))}.mdc-switch--selected.mdc-switch--disabled .mdc-switch__icon{fill:var(--mdc-switch-disabled-selected-icon-color, var(--mat-sys-on-surface))}.mdc-switch--selected .mdc-switch__icon--on,.mdc-switch--unselected .mdc-switch__icon--off{opacity:1;transition:opacity 45ms 30ms cubic-bezier(0, 0, 0.2, 1)}.mat-mdc-slide-toggle{-webkit-user-select:none;user-select:none;display:inline-block;-webkit-tap-highlight-color:rgba(0,0,0,0);outline:0}.mat-mdc-slide-toggle .mat-mdc-slide-toggle-ripple,.mat-mdc-slide-toggle .mdc-switch__ripple::after{top:0;left:0;right:0;bottom:0;position:absolute;border-radius:50%;pointer-events:none}.mat-mdc-slide-toggle .mat-mdc-slide-toggle-ripple:not(:empty),.mat-mdc-slide-toggle .mdc-switch__ripple::after:not(:empty){transform:translateZ(0)}.mat-mdc-slide-toggle.mat-mdc-slide-toggle-focused .mat-focus-indicator::before{content:""}.mat-mdc-slide-toggle .mat-internal-form-field{color:var(--mat-switch-label-text-color, var(--mat-sys-on-surface));font-family:var(--mat-switch-label-text-font, var(--mat-sys-body-medium-font));line-height:var(--mat-switch-label-text-line-height, var(--mat-sys-body-medium-line-height));font-size:var(--mat-switch-label-text-size, var(--mat-sys-body-medium-size));letter-spacing:var(--mat-switch-label-text-tracking, var(--mat-sys-body-medium-tracking));font-weight:var(--mat-switch-label-text-weight, var(--mat-sys-body-medium-weight))}.mat-mdc-slide-toggle .mat-ripple-element{opacity:.12}.mat-mdc-slide-toggle .mat-focus-indicator::before{border-radius:50%}.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__handle-track,.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__icon,.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__handle::before,.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__handle::after,.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__track::before,.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__track::after{transition:none}.mat-mdc-slide-toggle .mdc-switch:enabled+.mdc-label{cursor:pointer}.mat-mdc-slide-toggle .mdc-switch--disabled+label{color:var(--mdc-switch-disabled-label-text-color)}\n'],
    encapsulation: 2,
    changeDetection: 0
  });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(MatSlideToggle, [{
    type: Component,
    args: [{
      selector: "mat-slide-toggle",
      host: {
        "class": "mat-mdc-slide-toggle",
        "[id]": "id",
        // Needs to be removed since it causes some a11y issues (see #21266).
        "[attr.tabindex]": "null",
        "[attr.aria-label]": "null",
        "[attr.name]": "null",
        "[attr.aria-labelledby]": "null",
        "[class.mat-mdc-slide-toggle-focused]": "_focused",
        "[class.mat-mdc-slide-toggle-checked]": "checked",
        "[class._mat-animation-noopable]": "_noopAnimations",
        "[class]": 'color ? "mat-" + color : ""'
      },
      exportAs: "matSlideToggle",
      encapsulation: ViewEncapsulation.None,
      changeDetection: ChangeDetectionStrategy.OnPush,
      providers: [MAT_SLIDE_TOGGLE_VALUE_ACCESSOR, {
        provide: NG_VALIDATORS,
        useExisting: MatSlideToggle,
        multi: true
      }],
      imports: [MatRipple, _MatInternalFormField],
      template: `<div mat-internal-form-field [labelPosition]="labelPosition">
  <button
    class="mdc-switch"
    role="switch"
    type="button"
    [class.mdc-switch--selected]="checked"
    [class.mdc-switch--unselected]="!checked"
    [class.mdc-switch--checked]="checked"
    [class.mdc-switch--disabled]="disabled"
    [class.mat-mdc-slide-toggle-disabled-interactive]="disabledInteractive"
    [tabIndex]="disabled && !disabledInteractive ? -1 : tabIndex"
    [disabled]="disabled && !disabledInteractive"
    [attr.id]="buttonId"
    [attr.name]="name"
    [attr.aria-label]="ariaLabel"
    [attr.aria-labelledby]="_getAriaLabelledBy()"
    [attr.aria-describedby]="ariaDescribedby"
    [attr.aria-required]="required || null"
    [attr.aria-checked]="checked"
    [attr.aria-disabled]="disabled && disabledInteractive ? 'true' : null"
    (click)="_handleClick()"
    #switch>
    <span class="mdc-switch__track"></span>
    <span class="mdc-switch__handle-track">
      <span class="mdc-switch__handle">
        <span class="mdc-switch__shadow">
          <span class="mdc-elevation-overlay"></span>
        </span>
        <span class="mdc-switch__ripple">
          <span class="mat-mdc-slide-toggle-ripple mat-focus-indicator" mat-ripple
            [matRippleTrigger]="switch"
            [matRippleDisabled]="disableRipple || disabled"
            [matRippleCentered]="true"></span>
        </span>
        @if (!hideIcon) {
          <span class="mdc-switch__icons">
            <svg
              class="mdc-switch__icon mdc-switch__icon--on"
              viewBox="0 0 24 24"
              aria-hidden="true">
              <path d="M19.69,5.23L8.96,15.96l-4.23-4.23L2.96,13.5l6,6L21.46,7L19.69,5.23z" />
            </svg>
            <svg
              class="mdc-switch__icon mdc-switch__icon--off"
              viewBox="0 0 24 24"
              aria-hidden="true">
              <path d="M20 13H4v-2h16v2z" />
            </svg>
          </span>
        }
      </span>
    </span>
  </button>

  <!--
    Clicking on the label will trigger another click event from the button.
    Stop propagation here so other listeners further up in the DOM don't execute twice.
  -->
  <label class="mdc-label" [for]="buttonId" [attr.id]="_labelId" (click)="$event.stopPropagation()">
    <ng-content></ng-content>
  </label>
</div>
`,
      styles: ['.mdc-switch{align-items:center;background:none;border:none;cursor:pointer;display:inline-flex;flex-shrink:0;margin:0;outline:none;overflow:visible;padding:0;position:relative;width:var(--mdc-switch-track-width, 52px)}.mdc-switch.mdc-switch--disabled{cursor:default;pointer-events:none}.mdc-switch.mat-mdc-slide-toggle-disabled-interactive{pointer-events:auto}.mdc-switch__track{overflow:hidden;position:relative;width:100%;height:var(--mdc-switch-track-height, 32px);border-radius:var(--mdc-switch-track-shape, var(--mat-sys-corner-full))}.mdc-switch--disabled.mdc-switch .mdc-switch__track{opacity:var(--mdc-switch-disabled-track-opacity, 0.12)}.mdc-switch__track::before,.mdc-switch__track::after{border:1px solid rgba(0,0,0,0);border-radius:inherit;box-sizing:border-box;content:"";height:100%;left:0;position:absolute;width:100%;border-width:var(--mat-switch-track-outline-width, 2px);border-color:var(--mat-switch-track-outline-color, var(--mat-sys-outline))}.mdc-switch--selected .mdc-switch__track::before,.mdc-switch--selected .mdc-switch__track::after{border-width:var(--mat-switch-selected-track-outline-width, 2px);border-color:var(--mat-switch-selected-track-outline-color, transparent)}.mdc-switch--disabled .mdc-switch__track::before,.mdc-switch--disabled .mdc-switch__track::after{border-width:var(--mat-switch-disabled-unselected-track-outline-width, 2px);border-color:var(--mat-switch-disabled-unselected-track-outline-color, var(--mat-sys-on-surface))}@media(forced-colors: active){.mdc-switch__track{border-color:currentColor}}.mdc-switch__track::before{transition:transform 75ms 0ms cubic-bezier(0, 0, 0.2, 1);transform:translateX(0);background:var(--mdc-switch-unselected-track-color, var(--mat-sys-surface-variant))}.mdc-switch--selected .mdc-switch__track::before{transition:transform 75ms 0ms cubic-bezier(0.4, 0, 0.6, 1);transform:translateX(100%)}[dir=rtl] .mdc-switch--selected .mdc-switch--selected .mdc-switch__track::before{transform:translateX(-100%)}.mdc-switch--selected .mdc-switch__track::before{opacity:var(--mat-switch-hidden-track-opacity, 0);transition:var(--mat-switch-hidden-track-transition, opacity 75ms)}.mdc-switch--unselected .mdc-switch__track::before{opacity:var(--mat-switch-visible-track-opacity, 1);transition:var(--mat-switch-visible-track-transition, opacity 75ms)}.mdc-switch:enabled:hover:not(:focus):not(:active) .mdc-switch__track::before{background:var(--mdc-switch-unselected-hover-track-color, var(--mat-sys-surface-variant))}.mdc-switch:enabled:focus:not(:active) .mdc-switch__track::before{background:var(--mdc-switch-unselected-focus-track-color, var(--mat-sys-surface-variant))}.mdc-switch:enabled:active .mdc-switch__track::before{background:var(--mdc-switch-unselected-pressed-track-color, var(--mat-sys-surface-variant))}.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:hover:not(:focus):not(:active) .mdc-switch__track::before,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:focus:not(:active) .mdc-switch__track::before,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:active .mdc-switch__track::before,.mdc-switch.mdc-switch--disabled .mdc-switch__track::before{background:var(--mdc-switch-disabled-unselected-track-color, var(--mat-sys-surface-variant))}.mdc-switch__track::after{transform:translateX(-100%);background:var(--mdc-switch-selected-track-color, var(--mat-sys-primary))}[dir=rtl] .mdc-switch__track::after{transform:translateX(100%)}.mdc-switch--selected .mdc-switch__track::after{transform:translateX(0)}.mdc-switch--selected .mdc-switch__track::after{opacity:var(--mat-switch-visible-track-opacity, 1);transition:var(--mat-switch-visible-track-transition, opacity 75ms)}.mdc-switch--unselected .mdc-switch__track::after{opacity:var(--mat-switch-hidden-track-opacity, 0);transition:var(--mat-switch-hidden-track-transition, opacity 75ms)}.mdc-switch:enabled:hover:not(:focus):not(:active) .mdc-switch__track::after{background:var(--mdc-switch-selected-hover-track-color, var(--mat-sys-primary))}.mdc-switch:enabled:focus:not(:active) .mdc-switch__track::after{background:var(--mdc-switch-selected-focus-track-color, var(--mat-sys-primary))}.mdc-switch:enabled:active .mdc-switch__track::after{background:var(--mdc-switch-selected-pressed-track-color, var(--mat-sys-primary))}.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:hover:not(:focus):not(:active) .mdc-switch__track::after,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:focus:not(:active) .mdc-switch__track::after,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:active .mdc-switch__track::after,.mdc-switch.mdc-switch--disabled .mdc-switch__track::after{background:var(--mdc-switch-disabled-selected-track-color, var(--mat-sys-on-surface))}.mdc-switch__handle-track{height:100%;pointer-events:none;position:absolute;top:0;transition:transform 75ms 0ms cubic-bezier(0.4, 0, 0.2, 1);left:0;right:auto;transform:translateX(0);width:calc(100% - var(--mdc-switch-handle-width))}[dir=rtl] .mdc-switch__handle-track{left:auto;right:0}.mdc-switch--selected .mdc-switch__handle-track{transform:translateX(100%)}[dir=rtl] .mdc-switch--selected .mdc-switch__handle-track{transform:translateX(-100%)}.mdc-switch__handle{display:flex;pointer-events:auto;position:absolute;top:50%;transform:translateY(-50%);left:0;right:auto;transition:width 75ms cubic-bezier(0.4, 0, 0.2, 1),height 75ms cubic-bezier(0.4, 0, 0.2, 1),margin 75ms cubic-bezier(0.4, 0, 0.2, 1);width:var(--mdc-switch-handle-width);height:var(--mdc-switch-handle-height);border-radius:var(--mdc-switch-handle-shape, var(--mat-sys-corner-full))}[dir=rtl] .mdc-switch__handle{left:auto;right:0}.mat-mdc-slide-toggle .mdc-switch--unselected .mdc-switch__handle{width:var(--mat-switch-unselected-handle-size, 16px);height:var(--mat-switch-unselected-handle-size, 16px);margin:var(--mat-switch-unselected-handle-horizontal-margin, 0 8px)}.mat-mdc-slide-toggle .mdc-switch--unselected .mdc-switch__handle:has(.mdc-switch__icons){margin:var(--mat-switch-unselected-with-icon-handle-horizontal-margin, 0 4px)}.mat-mdc-slide-toggle .mdc-switch--selected .mdc-switch__handle{width:var(--mat-switch-selected-handle-size, 24px);height:var(--mat-switch-selected-handle-size, 24px);margin:var(--mat-switch-selected-handle-horizontal-margin, 0 24px)}.mat-mdc-slide-toggle .mdc-switch--selected .mdc-switch__handle:has(.mdc-switch__icons){margin:var(--mat-switch-selected-with-icon-handle-horizontal-margin, 0 24px)}.mat-mdc-slide-toggle .mdc-switch__handle:has(.mdc-switch__icons){width:var(--mat-switch-with-icon-handle-size, 24px);height:var(--mat-switch-with-icon-handle-size, 24px)}.mat-mdc-slide-toggle .mdc-switch:active:not(.mdc-switch--disabled) .mdc-switch__handle{width:var(--mat-switch-pressed-handle-size, 28px);height:var(--mat-switch-pressed-handle-size, 28px)}.mat-mdc-slide-toggle .mdc-switch--selected:active:not(.mdc-switch--disabled) .mdc-switch__handle{margin:var(--mat-switch-selected-pressed-handle-horizontal-margin, 0 22px)}.mat-mdc-slide-toggle .mdc-switch--unselected:active:not(.mdc-switch--disabled) .mdc-switch__handle{margin:var(--mat-switch-unselected-pressed-handle-horizontal-margin, 0 2px)}.mdc-switch--disabled.mdc-switch--selected .mdc-switch__handle::after{opacity:var(--mat-switch-disabled-selected-handle-opacity, 1)}.mdc-switch--disabled.mdc-switch--unselected .mdc-switch__handle::after{opacity:var(--mat-switch-disabled-unselected-handle-opacity, 0.38)}.mdc-switch__handle::before,.mdc-switch__handle::after{border:1px solid rgba(0,0,0,0);border-radius:inherit;box-sizing:border-box;content:"";width:100%;height:100%;left:0;position:absolute;top:0;transition:background-color 75ms 0ms cubic-bezier(0.4, 0, 0.2, 1),border-color 75ms 0ms cubic-bezier(0.4, 0, 0.2, 1);z-index:-1}@media(forced-colors: active){.mdc-switch__handle::before,.mdc-switch__handle::after{border-color:currentColor}}.mdc-switch--selected:enabled .mdc-switch__handle::after{background:var(--mdc-switch-selected-handle-color, var(--mat-sys-on-primary))}.mdc-switch--selected:enabled:hover:not(:focus):not(:active) .mdc-switch__handle::after{background:var(--mdc-switch-selected-hover-handle-color, var(--mat-sys-primary-container))}.mdc-switch--selected:enabled:focus:not(:active) .mdc-switch__handle::after{background:var(--mdc-switch-selected-focus-handle-color, var(--mat-sys-primary-container))}.mdc-switch--selected:enabled:active .mdc-switch__handle::after{background:var(--mdc-switch-selected-pressed-handle-color, var(--mat-sys-primary-container))}.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled.mdc-switch--selected:hover:not(:focus):not(:active) .mdc-switch__handle::after,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled.mdc-switch--selected:focus:not(:active) .mdc-switch__handle::after,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled.mdc-switch--selected:active .mdc-switch__handle::after,.mdc-switch--selected.mdc-switch--disabled .mdc-switch__handle::after{background:var(--mdc-switch-disabled-selected-handle-color, var(--mat-sys-surface))}.mdc-switch--unselected:enabled .mdc-switch__handle::after{background:var(--mdc-switch-unselected-handle-color, var(--mat-sys-outline))}.mdc-switch--unselected:enabled:hover:not(:focus):not(:active) .mdc-switch__handle::after{background:var(--mdc-switch-unselected-hover-handle-color, var(--mat-sys-on-surface-variant))}.mdc-switch--unselected:enabled:focus:not(:active) .mdc-switch__handle::after{background:var(--mdc-switch-unselected-focus-handle-color, var(--mat-sys-on-surface-variant))}.mdc-switch--unselected:enabled:active .mdc-switch__handle::after{background:var(--mdc-switch-unselected-pressed-handle-color, var(--mat-sys-on-surface-variant))}.mdc-switch--unselected.mdc-switch--disabled .mdc-switch__handle::after{background:var(--mdc-switch-disabled-unselected-handle-color, var(--mat-sys-on-surface))}.mdc-switch__handle::before{background:var(--mdc-switch-handle-surface-color)}.mdc-switch__shadow{border-radius:inherit;bottom:0;left:0;position:absolute;right:0;top:0}.mdc-switch:enabled .mdc-switch__shadow{box-shadow:var(--mdc-switch-handle-elevation-shadow)}.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:hover:not(:focus):not(:active) .mdc-switch__shadow,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:focus:not(:active) .mdc-switch__shadow,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:active .mdc-switch__shadow,.mdc-switch.mdc-switch--disabled .mdc-switch__shadow{box-shadow:var(--mdc-switch-disabled-handle-elevation-shadow)}.mdc-switch__ripple{left:50%;position:absolute;top:50%;transform:translate(-50%, -50%);z-index:-1;width:var(--mdc-switch-state-layer-size, 40px);height:var(--mdc-switch-state-layer-size, 40px)}.mdc-switch__ripple::after{content:"";opacity:0}.mdc-switch--disabled .mdc-switch__ripple::after{display:none}.mat-mdc-slide-toggle-disabled-interactive .mdc-switch__ripple::after{display:block}.mdc-switch:hover .mdc-switch__ripple::after{opacity:.04;transition:75ms opacity cubic-bezier(0, 0, 0.2, 1)}.mat-mdc-slide-toggle.mat-mdc-slide-toggle-focused .mdc-switch .mdc-switch__ripple::after{opacity:.12}.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:enabled:focus .mdc-switch__ripple::after,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:enabled:active .mdc-switch__ripple::after,.mat-mdc-slide-toggle-disabled-interactive.mdc-switch--disabled:enabled:hover:not(:focus) .mdc-switch__ripple::after,.mdc-switch--unselected:enabled:hover:not(:focus) .mdc-switch__ripple::after{background:var(--mdc-switch-unselected-hover-state-layer-color, var(--mat-sys-on-surface))}.mdc-switch--unselected:enabled:focus .mdc-switch__ripple::after{background:var(--mdc-switch-unselected-focus-state-layer-color, var(--mat-sys-on-surface))}.mdc-switch--unselected:enabled:active .mdc-switch__ripple::after{background:var(--mdc-switch-unselected-pressed-state-layer-color, var(--mat-sys-on-surface));opacity:var(--mdc-switch-unselected-pressed-state-layer-opacity, var(--mat-sys-pressed-state-layer-opacity));transition:opacity 75ms linear}.mdc-switch--selected:enabled:hover:not(:focus) .mdc-switch__ripple::after{background:var(--mdc-switch-selected-hover-state-layer-color, var(--mat-sys-primary))}.mdc-switch--selected:enabled:focus .mdc-switch__ripple::after{background:var(--mdc-switch-selected-focus-state-layer-color, var(--mat-sys-primary))}.mdc-switch--selected:enabled:active .mdc-switch__ripple::after{background:var(--mdc-switch-selected-pressed-state-layer-color, var(--mat-sys-primary));opacity:var(--mdc-switch-selected-pressed-state-layer-opacity, var(--mat-sys-pressed-state-layer-opacity));transition:opacity 75ms linear}.mdc-switch__icons{position:relative;height:100%;width:100%;z-index:1;transform:translateZ(0)}.mdc-switch--disabled.mdc-switch--unselected .mdc-switch__icons{opacity:var(--mdc-switch-disabled-unselected-icon-opacity, 0.38)}.mdc-switch--disabled.mdc-switch--selected .mdc-switch__icons{opacity:var(--mdc-switch-disabled-selected-icon-opacity, 0.38)}.mdc-switch__icon{bottom:0;left:0;margin:auto;position:absolute;right:0;top:0;opacity:0;transition:opacity 30ms 0ms cubic-bezier(0.4, 0, 1, 1)}.mdc-switch--unselected .mdc-switch__icon{width:var(--mdc-switch-unselected-icon-size, 16px);height:var(--mdc-switch-unselected-icon-size, 16px);fill:var(--mdc-switch-unselected-icon-color, var(--mat-sys-surface-variant))}.mdc-switch--unselected.mdc-switch--disabled .mdc-switch__icon{fill:var(--mdc-switch-disabled-unselected-icon-color, var(--mat-sys-surface-variant))}.mdc-switch--selected .mdc-switch__icon{width:var(--mdc-switch-selected-icon-size, 16px);height:var(--mdc-switch-selected-icon-size, 16px);fill:var(--mdc-switch-selected-icon-color, var(--mat-sys-on-primary-container))}.mdc-switch--selected.mdc-switch--disabled .mdc-switch__icon{fill:var(--mdc-switch-disabled-selected-icon-color, var(--mat-sys-on-surface))}.mdc-switch--selected .mdc-switch__icon--on,.mdc-switch--unselected .mdc-switch__icon--off{opacity:1;transition:opacity 45ms 30ms cubic-bezier(0, 0, 0.2, 1)}.mat-mdc-slide-toggle{-webkit-user-select:none;user-select:none;display:inline-block;-webkit-tap-highlight-color:rgba(0,0,0,0);outline:0}.mat-mdc-slide-toggle .mat-mdc-slide-toggle-ripple,.mat-mdc-slide-toggle .mdc-switch__ripple::after{top:0;left:0;right:0;bottom:0;position:absolute;border-radius:50%;pointer-events:none}.mat-mdc-slide-toggle .mat-mdc-slide-toggle-ripple:not(:empty),.mat-mdc-slide-toggle .mdc-switch__ripple::after:not(:empty){transform:translateZ(0)}.mat-mdc-slide-toggle.mat-mdc-slide-toggle-focused .mat-focus-indicator::before{content:""}.mat-mdc-slide-toggle .mat-internal-form-field{color:var(--mat-switch-label-text-color, var(--mat-sys-on-surface));font-family:var(--mat-switch-label-text-font, var(--mat-sys-body-medium-font));line-height:var(--mat-switch-label-text-line-height, var(--mat-sys-body-medium-line-height));font-size:var(--mat-switch-label-text-size, var(--mat-sys-body-medium-size));letter-spacing:var(--mat-switch-label-text-tracking, var(--mat-sys-body-medium-tracking));font-weight:var(--mat-switch-label-text-weight, var(--mat-sys-body-medium-weight))}.mat-mdc-slide-toggle .mat-ripple-element{opacity:.12}.mat-mdc-slide-toggle .mat-focus-indicator::before{border-radius:50%}.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__handle-track,.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__icon,.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__handle::before,.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__handle::after,.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__track::before,.mat-mdc-slide-toggle._mat-animation-noopable .mdc-switch__track::after{transition:none}.mat-mdc-slide-toggle .mdc-switch:enabled+.mdc-label{cursor:pointer}.mat-mdc-slide-toggle .mdc-switch--disabled+label{color:var(--mdc-switch-disabled-label-text-color)}\n']
    }]
  }], () => [], {
    _switchElement: [{
      type: ViewChild,
      args: ["switch"]
    }],
    name: [{
      type: Input
    }],
    id: [{
      type: Input
    }],
    labelPosition: [{
      type: Input
    }],
    ariaLabel: [{
      type: Input,
      args: ["aria-label"]
    }],
    ariaLabelledby: [{
      type: Input,
      args: ["aria-labelledby"]
    }],
    ariaDescribedby: [{
      type: Input,
      args: ["aria-describedby"]
    }],
    required: [{
      type: Input,
      args: [{
        transform: booleanAttribute
      }]
    }],
    color: [{
      type: Input
    }],
    disabled: [{
      type: Input,
      args: [{
        transform: booleanAttribute
      }]
    }],
    disableRipple: [{
      type: Input,
      args: [{
        transform: booleanAttribute
      }]
    }],
    tabIndex: [{
      type: Input,
      args: [{
        transform: (value) => value == null ? 0 : numberAttribute(value)
      }]
    }],
    checked: [{
      type: Input,
      args: [{
        transform: booleanAttribute
      }]
    }],
    hideIcon: [{
      type: Input,
      args: [{
        transform: booleanAttribute
      }]
    }],
    disabledInteractive: [{
      type: Input,
      args: [{
        transform: booleanAttribute
      }]
    }],
    change: [{
      type: Output
    }],
    toggleChange: [{
      type: Output
    }]
  });
})();
var MAT_SLIDE_TOGGLE_REQUIRED_VALIDATOR = {
  provide: NG_VALIDATORS,
  useExisting: forwardRef(() => MatSlideToggleRequiredValidator),
  multi: true
};
var MatSlideToggleRequiredValidator = class _MatSlideToggleRequiredValidator extends CheckboxRequiredValidator {
  static \u0275fac = /* @__PURE__ */ (() => {
    let \u0275MatSlideToggleRequiredValidator_BaseFactory;
    return function MatSlideToggleRequiredValidator_Factory(__ngFactoryType__) {
      return (\u0275MatSlideToggleRequiredValidator_BaseFactory || (\u0275MatSlideToggleRequiredValidator_BaseFactory = \u0275\u0275getInheritedFactory(_MatSlideToggleRequiredValidator)))(__ngFactoryType__ || _MatSlideToggleRequiredValidator);
    };
  })();
  static \u0275dir = /* @__PURE__ */ \u0275\u0275defineDirective({
    type: _MatSlideToggleRequiredValidator,
    selectors: [["mat-slide-toggle", "required", "", "formControlName", ""], ["mat-slide-toggle", "required", "", "formControl", ""], ["mat-slide-toggle", "required", "", "ngModel", ""]],
    features: [\u0275\u0275ProvidersFeature([MAT_SLIDE_TOGGLE_REQUIRED_VALIDATOR]), \u0275\u0275InheritDefinitionFeature]
  });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(MatSlideToggleRequiredValidator, [{
    type: Directive,
    args: [{
      selector: `mat-slide-toggle[required][formControlName],
             mat-slide-toggle[required][formControl], mat-slide-toggle[required][ngModel]`,
      providers: [MAT_SLIDE_TOGGLE_REQUIRED_VALIDATOR]
    }]
  }], null, null);
})();
var _MatSlideToggleRequiredValidatorModule = class __MatSlideToggleRequiredValidatorModule {
  static \u0275fac = function _MatSlideToggleRequiredValidatorModule_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || __MatSlideToggleRequiredValidatorModule)();
  };
  static \u0275mod = /* @__PURE__ */ \u0275\u0275defineNgModule({
    type: __MatSlideToggleRequiredValidatorModule,
    imports: [MatSlideToggleRequiredValidator],
    exports: [MatSlideToggleRequiredValidator]
  });
  static \u0275inj = /* @__PURE__ */ \u0275\u0275defineInjector({});
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(_MatSlideToggleRequiredValidatorModule, [{
    type: NgModule,
    args: [{
      imports: [MatSlideToggleRequiredValidator],
      exports: [MatSlideToggleRequiredValidator]
    }]
  }], null, null);
})();
var MatSlideToggleModule = class _MatSlideToggleModule {
  static \u0275fac = function MatSlideToggleModule_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _MatSlideToggleModule)();
  };
  static \u0275mod = /* @__PURE__ */ \u0275\u0275defineNgModule({
    type: _MatSlideToggleModule,
    imports: [MatSlideToggle, MatCommonModule],
    exports: [MatSlideToggle, MatCommonModule]
  });
  static \u0275inj = /* @__PURE__ */ \u0275\u0275defineInjector({
    imports: [MatSlideToggle, MatCommonModule, MatCommonModule]
  });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(MatSlideToggleModule, [{
    type: NgModule,
    args: [{
      imports: [MatSlideToggle, MatCommonModule],
      exports: [MatSlideToggle, MatCommonModule]
    }]
  }], null, null);
})();

// src/app/features/admin/groups/group-dialog/group-dialog.component.ts
function GroupDialogComponent_Conditional_8_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u041E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043F\u043E\u043B\u0435");
    \u0275\u0275elementEnd();
  }
}
function GroupDialogComponent_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-error");
    \u0275\u0275text(1, "\u041E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043F\u043E\u043B\u0435");
    \u0275\u0275elementEnd();
  }
}
function GroupDialogComponent_Conditional_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-slide-toggle", 5);
    \u0275\u0275text(1, "\u0410\u043A\u0442\u0438\u0432\u043D\u0430");
    \u0275\u0275elementEnd();
  }
}
var GroupDialogComponent = class _GroupDialogComponent {
  data = inject(MAT_DIALOG_DATA);
  dialogRef = inject(MatDialogRef);
  adminApi = inject(AdminApiService);
  fb = inject(FormBuilder);
  saving = false;
  form = this.fb.nonNullable.group({
    name: ["", [Validators.required, Validators.maxLength(50)]],
    code: ["", [Validators.required, Validators.maxLength(20)]],
    active: [true]
  });
  get isEdit() {
    return this.data.mode === "edit";
  }
  ngOnInit() {
    if (this.isEdit && this.data.group) {
      this.form.patchValue({
        name: this.data.group.name,
        code: this.data.group.code,
        active: this.data.group.active
      });
    }
  }
  save() {
    if (this.form.invalid || this.saving)
      return;
    this.saving = true;
    const { name, code, active } = this.form.getRawValue();
    if (this.isEdit && this.data.group) {
      this.adminApi.updateGroup(this.data.group.id, { name, code, active }).subscribe({
        next: () => this.dialogRef.close(true),
        error: () => this.saving = false
      });
    } else {
      this.adminApi.createGroup({ name, code }).subscribe({
        next: () => this.dialogRef.close(true),
        error: () => this.saving = false
      });
    }
  }
  static \u0275fac = function GroupDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _GroupDialogComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _GroupDialogComponent, selectors: [["app-group-dialog"]], decls: 20, vars: 7, consts: [["mat-dialog-title", ""], [1, "flex", "flex-col", "gap-4", 3, "formGroup"], [1, "w-full"], ["matInput", "", "formControlName", "name", "maxlength", "50"], ["matInput", "", "formControlName", "code", "maxlength", "20"], ["formControlName", "active"], ["align", "end"], ["mat-button", "", "mat-dialog-close", ""], ["mat-flat-button", "", "color", "primary", 3, "click", "disabled"]], template: function GroupDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "h2", 0);
      \u0275\u0275text(1);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(2, "mat-dialog-content")(3, "form", 1)(4, "mat-form-field", 2)(5, "mat-label");
      \u0275\u0275text(6, "\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435");
      \u0275\u0275elementEnd();
      \u0275\u0275element(7, "input", 3);
      \u0275\u0275template(8, GroupDialogComponent_Conditional_8_Template, 2, 0, "mat-error");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(9, "mat-form-field", 2)(10, "mat-label");
      \u0275\u0275text(11, "\u041A\u043E\u0434");
      \u0275\u0275elementEnd();
      \u0275\u0275element(12, "input", 4);
      \u0275\u0275template(13, GroupDialogComponent_Conditional_13_Template, 2, 0, "mat-error");
      \u0275\u0275elementEnd();
      \u0275\u0275template(14, GroupDialogComponent_Conditional_14_Template, 2, 0, "mat-slide-toggle", 5);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(15, "mat-dialog-actions", 6)(16, "button", 7);
      \u0275\u0275text(17, "\u041E\u0442\u043C\u0435\u043D\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(18, "button", 8);
      \u0275\u0275listener("click", function GroupDialogComponent_Template_button_click_18_listener() {
        return ctx.save();
      });
      \u0275\u0275text(19);
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275advance();
      \u0275\u0275textInterpolate(ctx.isEdit ? "\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443" : "\u041D\u043E\u0432\u0430\u044F \u0433\u0440\u0443\u043F\u043F\u0430");
      \u0275\u0275advance(2);
      \u0275\u0275property("formGroup", ctx.form);
      \u0275\u0275advance(5);
      \u0275\u0275conditional(ctx.form.controls.name.hasError("required") ? 8 : -1);
      \u0275\u0275advance(5);
      \u0275\u0275conditional(ctx.form.controls.code.hasError("required") ? 13 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.isEdit ? 14 : -1);
      \u0275\u0275advance(4);
      \u0275\u0275property("disabled", ctx.form.invalid || ctx.saving);
      \u0275\u0275advance();
      \u0275\u0275textInterpolate1(" ", ctx.isEdit ? "\u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F" : "\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443", " ");
    }
  }, dependencies: [
    CommonModule,
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
    MatFormFieldModule,
    MatFormField,
    MatLabel,
    MatError,
    MatInputModule,
    MatInput,
    MatButtonModule,
    MatButton,
    MatSlideToggleModule,
    MatSlideToggle
  ], encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(GroupDialogComponent, [{
    type: Component,
    args: [{ selector: "app-group-dialog", standalone: true, imports: [
      CommonModule,
      ReactiveFormsModule,
      MatDialogModule,
      MatFormFieldModule,
      MatInputModule,
      MatButtonModule,
      MatSlideToggleModule
    ], template: `<h2 mat-dialog-title>{{ isEdit ? '\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443' : '\u041D\u043E\u0432\u0430\u044F \u0433\u0440\u0443\u043F\u043F\u0430' }}</h2>
<mat-dialog-content>
  <form [formGroup]="form" class="flex flex-col gap-4">
    <mat-form-field class="w-full">
      <mat-label>\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435</mat-label>
      <input matInput formControlName="name" maxlength="50">
      @if (form.controls.name.hasError('required')) {
        <mat-error>\u041E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043F\u043E\u043B\u0435</mat-error>
      }
    </mat-form-field>

    <mat-form-field class="w-full">
      <mat-label>\u041A\u043E\u0434</mat-label>
      <input matInput formControlName="code" maxlength="20">
      @if (form.controls.code.hasError('required')) {
        <mat-error>\u041E\u0431\u044F\u0437\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043F\u043E\u043B\u0435</mat-error>
      }
    </mat-form-field>

    @if (isEdit) {
      <mat-slide-toggle formControlName="active">\u0410\u043A\u0442\u0438\u0432\u043D\u0430</mat-slide-toggle>
    }
  </form>
</mat-dialog-content>
<mat-dialog-actions align="end">
  <button mat-button mat-dialog-close>\u041E\u0442\u043C\u0435\u043D\u0430</button>
  <button mat-flat-button color="primary" [disabled]="form.invalid || saving" (click)="save()">
    {{ isEdit ? '\u0421\u043E\u0445\u0440\u0430\u043D\u0438\u0442\u044C \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F' : '\u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443' }}
  </button>
</mat-dialog-actions>
` }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(GroupDialogComponent, { className: "GroupDialogComponent", filePath: "src/app/features/admin/groups/group-dialog/group-dialog.component.ts", lineNumber: 32 });
})();

// src/app/features/admin/groups/assign-headman-dialog/assign-headman-dialog.component.ts
var _forTrack0 = ($index, $item) => $item.id;
function AssignHeadmanDialogComponent_For_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-option", 4);
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const s_r1 = ctx.$implicit;
    \u0275\u0275property("value", s_r1.id);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate2("", s_r1.displayName, " (", s_r1.login, ")");
  }
}
var AssignHeadmanDialogComponent = class _AssignHeadmanDialogComponent {
  data = inject(MAT_DIALOG_DATA);
  dialogRef = inject(MatDialogRef);
  selectedUserId = null;
  assign() {
    this.dialogRef.close(this.selectedUserId);
  }
  static \u0275fac = function AssignHeadmanDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _AssignHeadmanDialogComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _AssignHeadmanDialogComponent, selectors: [["app-assign-headman-dialog"]], decls: 16, vars: 3, consts: [["mat-dialog-title", ""], [1, "mb-4"], [1, "w-full"], [3, "valueChange", "value"], [3, "value"], ["align", "end"], ["mat-button", "", "mat-dialog-close", ""], ["mat-flat-button", "", "color", "primary", 3, "click", "disabled"]], template: function AssignHeadmanDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "h2", 0);
      \u0275\u0275text(1, "\u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(2, "mat-dialog-content")(3, "p", 1);
      \u0275\u0275text(4);
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "mat-form-field", 2)(6, "mat-label");
      \u0275\u0275text(7, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(8, "mat-select", 3);
      \u0275\u0275twoWayListener("valueChange", function AssignHeadmanDialogComponent_Template_mat_select_valueChange_8_listener($event) {
        \u0275\u0275twoWayBindingSet(ctx.selectedUserId, $event) || (ctx.selectedUserId = $event);
        return $event;
      });
      \u0275\u0275repeaterCreate(9, AssignHeadmanDialogComponent_For_10_Template, 2, 3, "mat-option", 4, _forTrack0);
      \u0275\u0275elementEnd()()();
      \u0275\u0275elementStart(11, "mat-dialog-actions", 5)(12, "button", 6);
      \u0275\u0275text(13, "\u041E\u0442\u043C\u0435\u043D\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(14, "button", 7);
      \u0275\u0275listener("click", function AssignHeadmanDialogComponent_Template_button_click_14_listener() {
        return ctx.assign();
      });
      \u0275\u0275text(15, "\u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C");
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275advance(4);
      \u0275\u0275textInterpolate1("\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430 \u0433\u0440\u0443\u043F\u043F\u044B ", ctx.data.group.name, ".");
      \u0275\u0275advance(4);
      \u0275\u0275twoWayProperty("value", ctx.selectedUserId);
      \u0275\u0275advance();
      \u0275\u0275repeater(ctx.data.students);
      \u0275\u0275advance(5);
      \u0275\u0275property("disabled", !ctx.selectedUserId);
    }
  }, dependencies: [MatDialogModule, MatDialogClose, MatDialogTitle, MatDialogActions, MatDialogContent, MatButtonModule, MatButton, MatSelectModule, MatFormField, MatLabel, MatSelect, MatOption, MatFormFieldModule], encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(AssignHeadmanDialogComponent, [{
    type: Component,
    args: [{
      selector: "app-assign-headman-dialog",
      standalone: true,
      imports: [MatDialogModule, MatButtonModule, MatSelectModule, MatFormFieldModule],
      template: `
    <h2 mat-dialog-title>\u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443</h2>
    <mat-dialog-content>
      <p class="mb-4">\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u0430 \u0433\u0440\u0443\u043F\u043F\u044B {{ data.group.name }}.</p>
      <mat-form-field class="w-full">
        <mat-label>\u0421\u0442\u0443\u0434\u0435\u043D\u0442</mat-label>
        <mat-select [(value)]="selectedUserId">
          @for (s of data.students; track s.id) {
            <mat-option [value]="s.id">{{ s.displayName }} ({{ s.login }})</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>\u041E\u0442\u043C\u0435\u043D\u0430</button>
      <button mat-flat-button color="primary" [disabled]="!selectedUserId" (click)="assign()">\u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C</button>
    </mat-dialog-actions>
  `
    }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(AssignHeadmanDialogComponent, { className: "AssignHeadmanDialogComponent", filePath: "src/app/features/admin/groups/assign-headman-dialog/assign-headman-dialog.component.ts", lineNumber: 32 });
})();

// src/app/features/admin/groups/revoke-headman-dialog/revoke-headman-dialog.component.ts
var RevokeHeadmanDialogComponent = class _RevokeHeadmanDialogComponent {
  data = inject(MAT_DIALOG_DATA);
  static \u0275fac = function RevokeHeadmanDialogComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _RevokeHeadmanDialogComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _RevokeHeadmanDialogComponent, selectors: [["app-revoke-headman-dialog"]], decls: 10, vars: 3, consts: [["mat-dialog-title", ""], ["align", "end"], ["mat-button", "", "mat-dialog-close", ""], ["mat-flat-button", "", "color", "warn", 3, "mat-dialog-close"]], template: function RevokeHeadmanDialogComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "h2", 0);
      \u0275\u0275text(1, "\u0421\u043D\u044F\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443?");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(2, "mat-dialog-content")(3, "p");
      \u0275\u0275text(4);
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(5, "mat-dialog-actions", 1)(6, "button", 2);
      \u0275\u0275text(7, "\u041E\u0442\u043C\u0435\u043D\u0430");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(8, "button", 3);
      \u0275\u0275text(9, "\u0421\u043D\u044F\u0442\u044C");
      \u0275\u0275elementEnd()();
    }
    if (rf & 2) {
      \u0275\u0275advance(4);
      \u0275\u0275textInterpolate2("", ctx.data.headman.displayName, " \u0431\u0443\u0434\u0435\u0442 \u0441\u043D\u044F\u0442 \u0441 \u0434\u043E\u043B\u0436\u043D\u043E\u0441\u0442\u0438 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B \u0433\u0440\u0443\u043F\u043F\u044B ", ctx.data.group.name, ".");
      \u0275\u0275advance(4);
      \u0275\u0275property("mat-dialog-close", true);
    }
  }, dependencies: [MatDialogModule, MatDialogClose, MatDialogTitle, MatDialogActions, MatDialogContent, MatButtonModule, MatButton], encapsulation: 2 });
};
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(RevokeHeadmanDialogComponent, [{
    type: Component,
    args: [{
      selector: "app-revoke-headman-dialog",
      standalone: true,
      imports: [MatDialogModule, MatButtonModule],
      template: `
    <h2 mat-dialog-title>\u0421\u043D\u044F\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443?</h2>
    <mat-dialog-content>
      <p>{{ data.headman.displayName }} \u0431\u0443\u0434\u0435\u0442 \u0441\u043D\u044F\u0442 \u0441 \u0434\u043E\u043B\u0436\u043D\u043E\u0441\u0442\u0438 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B \u0433\u0440\u0443\u043F\u043F\u044B {{ data.group.name }}.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>\u041E\u0442\u043C\u0435\u043D\u0430</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="true">\u0421\u043D\u044F\u0442\u044C</button>
    </mat-dialog-actions>
  `
    }]
  }], null, null);
})();
(() => {
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(RevokeHeadmanDialogComponent, { className: "RevokeHeadmanDialogComponent", filePath: "src/app/features/admin/groups/revoke-headman-dialog/revoke-headman-dialog.component.ts", lineNumber: 22 });
})();

// src/app/features/admin/groups/groups-page.component.ts
function GroupsPageComponent_Conditional_11_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-progress-bar", 7);
  }
}
function GroupsPageComponent_Conditional_12_Template(rf, ctx) {
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
function GroupsPageComponent_Conditional_13_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 9)(1, "div", 12)(2, "div", 13);
    \u0275\u0275element(3, "i", 14);
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(4, "h3", 15);
    \u0275\u0275text(5, "\u0413\u0440\u0443\u043F\u043F \u043D\u0435\u0442");
    \u0275\u0275elementEnd();
    \u0275\u0275elementStart(6, "p", 16);
    \u0275\u0275text(7, "\u0421\u043E\u0437\u0434\u0430\u0439\u0442\u0435 \u043F\u0435\u0440\u0432\u0443\u044E \u0443\u0447\u0435\u0431\u043D\u0443\u044E \u0433\u0440\u0443\u043F\u043F\u0443 \u0447\u0435\u0440\u0435\u0437 \u043A\u043D\u043E\u043F\u043A\u0443 \u0432\u044B\u0448\u0435.");
    \u0275\u0275elementEnd()()();
  }
}
function GroupsPageComponent_Conditional_14_mat_header_cell_3_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-header-cell");
    \u0275\u0275text(1, "\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435");
    \u0275\u0275elementEnd();
  }
}
function GroupsPageComponent_Conditional_14_mat_cell_4_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-cell");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const group_r2 = ctx.$implicit;
    \u0275\u0275advance();
    \u0275\u0275textInterpolate2("", group_r2.name, " (", group_r2.code, ")");
  }
}
function GroupsPageComponent_Conditional_14_mat_header_cell_6_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-header-cell");
    \u0275\u0275text(1, "\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430");
    \u0275\u0275elementEnd();
  }
}
function GroupsPageComponent_Conditional_14_mat_cell_7_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-cell");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    let tmp_3_0;
    const group_r3 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate((tmp_3_0 = (tmp_3_0 = ctx_r0.groupHeadman(group_r3.id)) == null ? null : tmp_3_0.displayName) !== null && tmp_3_0 !== void 0 ? tmp_3_0 : "\u041D\u0435 \u043D\u0430\u0437\u043D\u0430\u0447\u0435\u043D");
  }
}
function GroupsPageComponent_Conditional_14_mat_header_cell_9_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-header-cell");
    \u0275\u0275text(1, "\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432");
    \u0275\u0275elementEnd();
  }
}
function GroupsPageComponent_Conditional_14_mat_cell_10_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "mat-cell");
    \u0275\u0275text(1);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const group_r4 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance();
    \u0275\u0275textInterpolate(ctx_r0.groupStudentCount(group_r4.id));
  }
}
function GroupsPageComponent_Conditional_14_mat_header_cell_12_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-header-cell");
  }
}
function GroupsPageComponent_Conditional_14_mat_cell_13_Conditional_3_Template(rf, ctx) {
  if (rf & 1) {
    const _r7 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 30);
    \u0275\u0275listener("click", function GroupsPageComponent_Conditional_14_mat_cell_13_Conditional_3_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r7);
      const group_r6 = \u0275\u0275nextContext().$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.revokeHeadman(group_r6));
    });
    \u0275\u0275element(1, "i", 31);
    \u0275\u0275elementEnd();
  }
}
function GroupsPageComponent_Conditional_14_mat_cell_13_Conditional_4_Template(rf, ctx) {
  if (rf & 1) {
    const _r8 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "button", 32);
    \u0275\u0275listener("click", function GroupsPageComponent_Conditional_14_mat_cell_13_Conditional_4_Template_button_click_0_listener() {
      \u0275\u0275restoreView(_r8);
      const group_r6 = \u0275\u0275nextContext().$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.assignHeadman(group_r6));
    });
    \u0275\u0275element(1, "i", 33);
    \u0275\u0275elementEnd();
  }
}
function GroupsPageComponent_Conditional_14_mat_cell_13_Template(rf, ctx) {
  if (rf & 1) {
    const _r5 = \u0275\u0275getCurrentView();
    \u0275\u0275elementStart(0, "mat-cell")(1, "button", 26);
    \u0275\u0275listener("click", function GroupsPageComponent_Conditional_14_mat_cell_13_Template_button_click_1_listener() {
      const group_r6 = \u0275\u0275restoreView(_r5).$implicit;
      const ctx_r0 = \u0275\u0275nextContext(2);
      return \u0275\u0275resetView(ctx_r0.openEditDialog(group_r6));
    });
    \u0275\u0275element(2, "i", 27);
    \u0275\u0275elementEnd();
    \u0275\u0275template(3, GroupsPageComponent_Conditional_14_mat_cell_13_Conditional_3_Template, 2, 0, "button", 28)(4, GroupsPageComponent_Conditional_14_mat_cell_13_Conditional_4_Template, 2, 0, "button", 29);
    \u0275\u0275elementEnd();
  }
  if (rf & 2) {
    const group_r6 = ctx.$implicit;
    const ctx_r0 = \u0275\u0275nextContext(2);
    \u0275\u0275advance(3);
    \u0275\u0275conditional(ctx_r0.groupHeadman(group_r6.id) ? 3 : 4);
  }
}
function GroupsPageComponent_Conditional_14_mat_header_row_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-header-row");
  }
}
function GroupsPageComponent_Conditional_14_mat_row_15_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275element(0, "mat-row");
  }
}
function GroupsPageComponent_Conditional_14_Template(rf, ctx) {
  if (rf & 1) {
    \u0275\u0275elementStart(0, "div", 10)(1, "mat-table", 17);
    \u0275\u0275elementContainerStart(2, 18);
    \u0275\u0275template(3, GroupsPageComponent_Conditional_14_mat_header_cell_3_Template, 2, 0, "mat-header-cell", 19)(4, GroupsPageComponent_Conditional_14_mat_cell_4_Template, 2, 2, "mat-cell", 20);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(5, 21);
    \u0275\u0275template(6, GroupsPageComponent_Conditional_14_mat_header_cell_6_Template, 2, 0, "mat-header-cell", 19)(7, GroupsPageComponent_Conditional_14_mat_cell_7_Template, 2, 1, "mat-cell", 20);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(8, 22);
    \u0275\u0275template(9, GroupsPageComponent_Conditional_14_mat_header_cell_9_Template, 2, 0, "mat-header-cell", 19)(10, GroupsPageComponent_Conditional_14_mat_cell_10_Template, 2, 1, "mat-cell", 20);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275elementContainerStart(11, 23);
    \u0275\u0275template(12, GroupsPageComponent_Conditional_14_mat_header_cell_12_Template, 1, 0, "mat-header-cell", 19)(13, GroupsPageComponent_Conditional_14_mat_cell_13_Template, 5, 1, "mat-cell", 20);
    \u0275\u0275elementContainerEnd();
    \u0275\u0275template(14, GroupsPageComponent_Conditional_14_mat_header_row_14_Template, 1, 0, "mat-header-row", 24)(15, GroupsPageComponent_Conditional_14_mat_row_15_Template, 1, 0, "mat-row", 25);
    \u0275\u0275elementEnd()();
  }
  if (rf & 2) {
    const ctx_r0 = \u0275\u0275nextContext();
    \u0275\u0275advance();
    \u0275\u0275property("dataSource", ctx_r0.groups());
    \u0275\u0275advance(13);
    \u0275\u0275property("matHeaderRowDef", ctx_r0.displayedColumns);
    \u0275\u0275advance();
    \u0275\u0275property("matRowDefColumns", ctx_r0.displayedColumns);
  }
}
var GroupsPageComponent = class _GroupsPageComponent {
  adminApi = inject(AdminApiService);
  dialog = inject(MatDialog);
  snackBar = inject(MatSnackBar);
  groups = signal([]);
  allStudents = signal([]);
  loading = signal(false);
  error = signal(null);
  displayedColumns = ["name", "headman", "studentCount", "actions"];
  ngOnInit() {
    this.reload();
  }
  groupHeadman(groupId) {
    return this.allStudents().find((u) => u.groupId === groupId && u.headman === true);
  }
  groupStudentCount(groupId) {
    return this.allStudents().filter((u) => u.groupId === groupId).length;
  }
  reload() {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      groups: this.adminApi.listGroups(),
      students: this.adminApi.listUsers({ role: "student", size: 500 })
    }).subscribe({
      next: ({ groups, students }) => {
        this.groups.set(groups);
        this.allStudents.set(students.items);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0434\u0430\u043D\u043D\u044B\u0435.");
        this.loading.set(false);
      }
    });
  }
  openCreateDialog() {
    const ref = this.dialog.open(GroupDialogComponent, {
      maxWidth: "480px",
      width: "100%",
      data: { mode: "create" }
    });
    ref.afterClosed().subscribe((result) => {
      if (result) {
        this.snackBar.open("\u0413\u0440\u0443\u043F\u043F\u0430 \u0441\u043E\u0437\u0434\u0430\u043D\u0430.", void 0, { duration: 4e3 });
        this.reload();
      }
    });
  }
  openEditDialog(group) {
    const ref = this.dialog.open(GroupDialogComponent, {
      maxWidth: "480px",
      width: "100%",
      data: { mode: "edit", group }
    });
    ref.afterClosed().subscribe((result) => {
      if (result) {
        this.snackBar.open("\u0413\u0440\u0443\u043F\u043F\u0430 \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0430.", void 0, { duration: 4e3 });
        this.reload();
      }
    });
  }
  assignHeadman(group) {
    this.adminApi.listStudentsByGroup(group.id).subscribe((students) => {
      const ref = this.dialog.open(AssignHeadmanDialogComponent, {
        maxWidth: "480px",
        width: "100%",
        data: { group, students }
      });
      ref.afterClosed().subscribe((userId) => {
        if (userId) {
          this.adminApi.patchUser(userId, { isHeadman: true }).subscribe({
            next: () => {
              this.snackBar.open("\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430 \u043D\u0430\u0437\u043D\u0430\u0447\u0435\u043D.", void 0, { duration: 4e3 });
              this.reload();
            },
            error: () => {
              this.snackBar.open("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443.", void 0, { duration: 6e3 });
            }
          });
        }
      });
    });
  }
  revokeHeadman(group) {
    const headman = this.groupHeadman(group.id);
    if (!headman)
      return;
    const ref = this.dialog.open(RevokeHeadmanDialogComponent, {
      maxWidth: "480px",
      width: "100%",
      data: { group, headman }
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.adminApi.patchUser(headman.id, { isHeadman: false }).subscribe({
          next: () => {
            this.snackBar.open("\u0414\u043E\u0441\u0442\u0443\u043F \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B \u0441\u043D\u044F\u0442.", void 0, { duration: 4e3 });
            this.reload();
          },
          error: () => {
            this.snackBar.open("\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0441\u043D\u044F\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443.", void 0, { duration: 6e3 });
          }
        });
      }
    });
  }
  static \u0275fac = function GroupsPageComponent_Factory(__ngFactoryType__) {
    return new (__ngFactoryType__ || _GroupsPageComponent)();
  };
  static \u0275cmp = /* @__PURE__ */ \u0275\u0275defineComponent({ type: _GroupsPageComponent, selectors: [["app-groups-page"]], decls: 15, vars: 3, consts: [[1, "page-stack"], [1, "page-header"], [1, "page-header__title"], [1, "page-header__subtitle"], [1, "page-header__actions"], ["type", "button", 1, "btn-brand", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-plus"], ["mode", "indeterminate"], ["role", "alert", 1, "page-error"], [1, "page-card"], [1, "page-card", "page-card--flush"], ["aria-hidden", "true", 1, "ph", "ph-warning-circle"], [1, "page-empty"], ["aria-hidden", "true", 1, "page-empty__icon"], [1, "ph-duotone", "ph-users-three"], [1, "page-empty__title"], [1, "page-empty__text"], [3, "dataSource"], ["matColumnDef", "name"], [4, "matHeaderCellDef"], [4, "matCellDef"], ["matColumnDef", "headman"], ["matColumnDef", "studentCount"], ["matColumnDef", "actions"], [4, "matHeaderRowDef"], [4, "matRowDef", "matRowDefColumns"], ["mat-icon-button", "", "matTooltip", "\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C", "aria-label", "\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-pencil-simple"], ["mat-icon-button", "", "matTooltip", "\u0421\u043D\u044F\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443", "aria-label", "\u0421\u043D\u044F\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443"], ["mat-icon-button", "", "matTooltip", "\u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443", "aria-label", "\u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443"], ["mat-icon-button", "", "matTooltip", "\u0421\u043D\u044F\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443", "aria-label", "\u0421\u043D\u044F\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-crown-cross"], ["mat-icon-button", "", "matTooltip", "\u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443", "aria-label", "\u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443", 3, "click"], ["aria-hidden", "true", 1, "ph", "ph-crown"]], template: function GroupsPageComponent_Template(rf, ctx) {
    if (rf & 1) {
      \u0275\u0275elementStart(0, "div", 0)(1, "header", 1)(2, "div", 2)(3, "h1");
      \u0275\u0275text(4, "\u0413\u0440\u0443\u043F\u043F\u044B");
      \u0275\u0275elementEnd();
      \u0275\u0275elementStart(5, "p", 3);
      \u0275\u0275text(6, " \u0423\u0447\u0435\u0431\u043D\u044B\u0435 \u0433\u0440\u0443\u043F\u043F\u044B, \u0438\u0445 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B \u0438 \u0441\u043E\u0441\u0442\u0430\u0432 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432. ");
      \u0275\u0275elementEnd()();
      \u0275\u0275elementStart(7, "div", 4)(8, "button", 5);
      \u0275\u0275listener("click", function GroupsPageComponent_Template_button_click_8_listener() {
        return ctx.openCreateDialog();
      });
      \u0275\u0275element(9, "i", 6);
      \u0275\u0275text(10, " \u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443 ");
      \u0275\u0275elementEnd()()();
      \u0275\u0275template(11, GroupsPageComponent_Conditional_11_Template, 1, 0, "mat-progress-bar", 7)(12, GroupsPageComponent_Conditional_12_Template, 3, 1, "div", 8)(13, GroupsPageComponent_Conditional_13_Template, 8, 0, "div", 9)(14, GroupsPageComponent_Conditional_14_Template, 16, 3, "div", 10);
      \u0275\u0275elementEnd();
    }
    if (rf & 2) {
      \u0275\u0275advance(11);
      \u0275\u0275conditional(ctx.loading() ? 11 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.error() ? 12 : -1);
      \u0275\u0275advance();
      \u0275\u0275conditional(ctx.groups().length === 0 && !ctx.loading() ? 13 : 14);
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
  (typeof ngDevMode === "undefined" || ngDevMode) && setClassMetadata(GroupsPageComponent, [{
    type: Component,
    args: [{ selector: "app-groups-page", standalone: true, imports: [
      CommonModule,
      MatTableModule,
      MatButtonModule,
      MatIconModule,
      MatProgressBarModule,
      MatTooltipModule
    ], template: `<div class="page-stack">
  <header class="page-header">
    <div class="page-header__title">
      <h1>\u0413\u0440\u0443\u043F\u043F\u044B</h1>
      <p class="page-header__subtitle">
        \u0423\u0447\u0435\u0431\u043D\u044B\u0435 \u0433\u0440\u0443\u043F\u043F\u044B, \u0438\u0445 \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u044B \u0438 \u0441\u043E\u0441\u0442\u0430\u0432 \u0441\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432.
      </p>
    </div>
    <div class="page-header__actions">
      <button type="button" class="btn-brand" (click)="openCreateDialog()">
        <i class="ph ph-plus" aria-hidden="true"></i>
        \u0421\u043E\u0437\u0434\u0430\u0442\u044C \u0433\u0440\u0443\u043F\u043F\u0443
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

  @if (groups().length === 0 && !loading()) {
    <div class="page-card">
      <div class="page-empty">
        <div class="page-empty__icon" aria-hidden="true">
          <i class="ph-duotone ph-users-three"></i>
        </div>
        <h3 class="page-empty__title">\u0413\u0440\u0443\u043F\u043F \u043D\u0435\u0442</h3>
        <p class="page-empty__text">\u0421\u043E\u0437\u0434\u0430\u0439\u0442\u0435 \u043F\u0435\u0440\u0432\u0443\u044E \u0443\u0447\u0435\u0431\u043D\u0443\u044E \u0433\u0440\u0443\u043F\u043F\u0443 \u0447\u0435\u0440\u0435\u0437 \u043A\u043D\u043E\u043F\u043A\u0443 \u0432\u044B\u0448\u0435.</p>
      </div>
    </div>
  } @else {
    <div class="page-card page-card--flush">
      <mat-table [dataSource]="groups()">
        <ng-container matColumnDef="name">
          <mat-header-cell *matHeaderCellDef>\u041D\u0430\u0437\u0432\u0430\u043D\u0438\u0435</mat-header-cell>
          <mat-cell *matCellDef="let group">{{ group.name }} ({{ group.code }})</mat-cell>
        </ng-container>

        <ng-container matColumnDef="headman">
          <mat-header-cell *matHeaderCellDef>\u0421\u0442\u0430\u0440\u043E\u0441\u0442\u0430</mat-header-cell>
          <mat-cell *matCellDef="let group">{{ groupHeadman(group.id)?.displayName ?? '\u041D\u0435 \u043D\u0430\u0437\u043D\u0430\u0447\u0435\u043D' }}</mat-cell>
        </ng-container>

        <ng-container matColumnDef="studentCount">
          <mat-header-cell *matHeaderCellDef>\u0421\u0442\u0443\u0434\u0435\u043D\u0442\u043E\u0432</mat-header-cell>
          <mat-cell *matCellDef="let group">{{ groupStudentCount(group.id) }}</mat-cell>
        </ng-container>

        <ng-container matColumnDef="actions">
          <mat-header-cell *matHeaderCellDef></mat-header-cell>
          <mat-cell *matCellDef="let group">
            <button mat-icon-button matTooltip="\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C" aria-label="\u0420\u0435\u0434\u0430\u043A\u0442\u0438\u0440\u043E\u0432\u0430\u0442\u044C" (click)="openEditDialog(group)">
              <i class="ph ph-pencil-simple" aria-hidden="true"></i>
            </button>
            @if (groupHeadman(group.id)) {
              <button mat-icon-button matTooltip="\u0421\u043D\u044F\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443" aria-label="\u0421\u043D\u044F\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443" (click)="revokeHeadman(group)">
                <i class="ph ph-crown-cross" aria-hidden="true"></i>
              </button>
            } @else {
              <button mat-icon-button matTooltip="\u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443" aria-label="\u041D\u0430\u0437\u043D\u0430\u0447\u0438\u0442\u044C \u0441\u0442\u0430\u0440\u043E\u0441\u0442\u0443" (click)="assignHeadman(group)">
                <i class="ph ph-crown" aria-hidden="true"></i>
              </button>
            }
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
  (typeof ngDevMode === "undefined" || ngDevMode) && \u0275setClassDebugInfo(GroupsPageComponent, { className: "GroupsPageComponent", filePath: "src/app/features/admin/groups/groups-page.component.ts", lineNumber: 36 });
})();
export {
  GroupsPageComponent
};
//# sourceMappingURL=chunk-Q3REUOLA.js.map
